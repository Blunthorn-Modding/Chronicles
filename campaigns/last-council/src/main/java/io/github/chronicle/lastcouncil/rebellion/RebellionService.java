package io.github.chronicle.lastcouncil.rebellion;

import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.github.chronicle.lastcouncil.rebellion.RebellionJournal.*;

/** World-owned rebellion membership, palace visits, and exact reinstatement receipts. */
public final class RebellionService {
    public static final int MAX_STATEMENT = 512;
    public static final int MAX_TARGETS = 32;

    public enum Action { DECLARED, JOINED, REINSTATED }
    public enum VisitStatus { WAITING, INSIDE, ENDED }

    /** Public House identity captured at the time of a declaration or join. */
    public record House(String houseId, String publicName) {
        public House {
            validateHouseId(houseId);
            validateText(publicName, 80, false);
        }
    }

    public record PublicEvent(Action action, House house, long revision) {}

    public record Faction(UUID id, House founder, String statement, long revision,
                          List<House> members, List<PublicEvent> history) {
        public Faction {
            members = List.copyOf(members);
            history = List.copyOf(history);
        }
    }

    public record Membership(UUID faction, long revision, House house) {}

    public record Visit(UUID grant, String houseId, VisitStatus status, long revision) {}

    public record Snapshot(long revision, Map<UUID, Faction> factions,
                           Map<String, Membership> memberships, Map<String, Visit> visits) {
        public Snapshot {
            factions = immutableMap(factions);
            memberships = immutableMap(memberships);
            visits = immutableMap(visits);
        }
    }

    /** The selected House and exact membership and faction versions pinned in a motion. */
    public record Target(String houseId, UUID faction, long membershipRevision, long factionRevision) {
        public Target {
            validateHouseId(houseId);
            Objects.requireNonNull(faction);
            if (membershipRevision < 1 || factionRevision < membershipRevision || factionRevision > MAX_ENTRIES)
                throw new IllegalArgumentException("Invalid rebellion target revision.");
        }
    }

    /** Authority-only receipt. The Council CLOSE transaction validates the source ballot. */
    public record Effect(UUID ballot, UUID motion, List<Target> targets, String title) {
        public Effect {
            Objects.requireNonNull(ballot);
            Objects.requireNonNull(motion);
            targets = exactTargets(targets);
            validateText(title, 160, false);
        }
    }

    public interface Storage {
        byte[] load() throws IOException;
        void save(byte[] bytes) throws IOException;
        default void verify() throws IOException {}
    }

    private final Storage storage;
    private State state = replay(List.of());
    private boolean initialized;
    private boolean needsReload;

    public RebellionService(Path file) throws IOException {
        this(storage(file));
    }

    public RebellionService(Storage storage) throws IOException {
        this.storage = Objects.requireNonNull(storage);
        reload();
    }

    public synchronized void reload() throws IOException {
        try {
            byte[] bytes = storage.load();
            if (bytes == null) {
                if (initialized) throw new IOException("The rebellion journal is missing. Restore it before recovery.");
                storage.save(encode(List.of()));
                state = replay(List.of());
            } else {
                state = decode(bytes);
            }
            initialized = true;
            needsReload = false;
        } catch (IOException | RuntimeException failure) {
            needsReload = true;
            throw failure;
        }
    }

    public synchronized void verify() throws IOException {
        if (needsReload) throw new IOException("Reload the rebellion journal before continuing.");
        try {
            storage.verify();
        } catch (IOException | RuntimeException failure) {
            needsReload = true;
            throw failure;
        }
    }

    public synchronized boolean needsReload() {
        return needsReload;
    }

    /** Last verified snapshot. Callers making authority decisions must account for storage holds. */
    public synchronized Snapshot snapshot() {
        return state.snapshot();
    }

    public synchronized Membership membership(String houseId) {
        return state.snapshot().memberships().get(houseId);
    }

    public synchronized List<Effect> effects() {
        return state.effects();
    }

    /** The declaration operation ID is also the new faction's stable ID. */
    public synchronized Faction declare(UUID operation, long expected, House actor, String statement) throws IOException {
        append(new Declare(operation, expected, actor, statement));
        return state.snapshot().factions().get(operation);
    }

    public synchronized Faction join(UUID operation, long expected, House actor, UUID faction) throws IOException {
        append(new Join(operation, expected, actor, faction));
        return state.snapshot().factions().get(faction);
    }

    public synchronized Visit grantVisit(UUID operation, long expected, String houseId) throws IOException {
        append(new Grant(operation, expected, houseId));
        return new Visit(operation, houseId, VisitStatus.WAITING, expected + 1);
    }

    public synchronized Visit markEntered(UUID operation, long expected, String houseId, UUID grant) throws IOException {
        append(new Enter(operation, expected, houseId, grant));
        return new Visit(grant, houseId, VisitStatus.INSIDE, expected + 1);
    }

    public synchronized Visit markExited(UUID operation, long expected, String houseId, UUID grant) throws IOException {
        append(new Exit(operation, expected, houseId, grant));
        return new Visit(grant, houseId, VisitStatus.ENDED, expected + 1);
    }

    public synchronized List<Target> targets(List<String> houses) throws IOException {
        verify();
        if (houses.isEmpty() || houses.size() > MAX_TARGETS)
            throw new IllegalArgumentException("Choose one or more rebel Houses.");
        List<Target> targets = new ArrayList<>(houses.size());
        for (String house : houses) {
            validateHouseId(house);
            Membership member = membership(house);
            if (member == null) throw staleTargets();
            Faction faction = state.snapshot().factions().get(member.faction());
            targets.add(new Target(house, faction.id(), member.revision(), faction.revision()));
        }
        return exactTargets(targets);
    }

    public synchronized void validateTargets(List<Target> targets) throws IOException {
        verify();
        validateTargets(state.snapshot(), exactTargets(targets));
    }

    public synchronized void apply(Effect effect) throws IOException {
        append(new Restore(effect));
    }

    private void append(Entry entry) throws IOException {
        verify();
        Entry prior = state.entriesById().get(entry.id());
        if (prior != null) {
            if (prior.equals(entry)) return;
            throw new IllegalArgumentException("This rebellion operation ID was already used.");
        }
        if (state.entries().size() >= MAX_ENTRIES)
            throw new IllegalStateException("The rebellion journal is full.");
        List<Entry> entries = new ArrayList<>(state.entries());
        entries.add(entry);
        State candidate = replay(entries);
        byte[] bytes = encode(entries);
        if (!decode(bytes).equals(candidate)) throw new IOException("Rebellion candidate failed validation.");
        try {
            storage.save(bytes);
        } catch (IOException | RuntimeException failure) {
            needsReload = true;
            throw failure;
        }
        state = candidate;
    }

    static List<Target> exactTargets(List<Target> targets) {
        if (targets == null || targets.isEmpty() || targets.size() > MAX_TARGETS)
            throw new IllegalArgumentException("Choose one or more rebel Houses.");
        List<Target> copy = List.copyOf(targets);
        var houses = new HashSet<String>();
        for (Target target : copy) {
            if (!houses.add(target.houseId())) throw new IllegalArgumentException("A House was selected twice.");
        }
        return copy;
    }

    static void validateTargets(Snapshot state, List<Target> targets) {
        for (Target target : targets) {
            Membership member = state.memberships().get(target.houseId());
            Faction faction = state.factions().get(target.faction());
            validateTarget(target, member, faction == null ? 0 : faction.revision());
        }
    }

    static void validateTarget(Target target, Membership membership, long factionRevision) {
        if (membership == null || !membership.faction().equals(target.faction())
                || membership.revision() != target.membershipRevision() || factionRevision != target.factionRevision())
            throw staleTargets();
    }

    private static IllegalStateException staleTargets() {
        return new IllegalStateException("The selected rebellion membership changed. Review it and propose a new motion.");
    }

    static void validateHouseId(String id) {
        if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid namespaced House ID.");
    }

    static void validateText(String text, int maxCodePoints, boolean multiline) {
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > maxCodePoints
                || text.codePoints().anyMatch(c -> c == '\u00a7' || (c >= 0xd800 && c <= 0xdfff)
                        || (Character.isISOControl(c) && !(multiline && c == '\n'))))
            throw new IllegalArgumentException("Invalid rebellion text.");
    }

    static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static Storage storage(Path path) {
        var file = new AtomicDataFile(path, MAX_BYTES);
        return new Storage() {
            @Override public byte[] load() throws IOException { return file.load(); }
            @Override public void save(byte[] bytes) throws IOException { file.save(bytes); }
            @Override public void verify() throws IOException { file.verifyUnchanged(); }
        };
    }
}
