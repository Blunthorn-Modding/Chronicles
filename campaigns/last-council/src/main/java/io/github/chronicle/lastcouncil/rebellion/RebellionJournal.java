package io.github.chronicle.lastcouncil.rebellion;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static io.github.chronicle.lastcouncil.rebellion.RebellionService.*;

/** Strict codec and single transition engine for both live writes and journal replay. */
final class RebellionJournal {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_ENTRIES = 1024;
    static final int MAX_FACTIONS = 128;
    private static final UUID NONE = new UUID(0, 0);
    private static final Gson JSON = new Gson();

    private RebellionJournal() {}

    sealed interface Entry permits Declare, Join, Grant, Enter, Exit, Restore {
        UUID id();
    }

    record Declare(UUID id, long expected, House house, String statement) implements Entry {
        Declare {
            checkOperation(id, expected);
            Objects.requireNonNull(house);
            validateText(statement, MAX_STATEMENT, true);
        }
    }

    record Join(UUID id, long expected, House house, UUID faction) implements Entry {
        Join {
            checkOperation(id, expected);
            Objects.requireNonNull(house);
            Objects.requireNonNull(faction);
        }
    }

    record Grant(UUID id, long expected, String houseId) implements Entry {
        Grant {
            checkOperation(id, expected);
            validateHouseId(houseId);
        }
    }

    record Enter(UUID id, long expected, String houseId, UUID grant) implements Entry {
        Enter {
            checkOperation(id, expected);
            validateHouseId(houseId);
            Objects.requireNonNull(grant);
        }
    }

    record Exit(UUID id, long expected, String houseId, UUID grant) implements Entry {
        Exit {
            checkOperation(id, expected);
            validateHouseId(houseId);
            Objects.requireNonNull(grant);
        }
    }

    record Restore(Effect effect) implements Entry {
        Restore {
            Objects.requireNonNull(effect);
        }

        @Override public UUID id() { return effect.ballot(); }
    }

    record State(Snapshot snapshot, List<Entry> entries, Map<UUID, Entry> entriesById, List<Effect> effects) {}

    private static final class FactionReplay {
        private final UUID id;
        private final House founder;
        private final String statement;
        private final Map<String, House> members = new LinkedHashMap<>();
        private final List<PublicEvent> history = new ArrayList<>();
        private long revision;

        private FactionReplay(Declare declaration) {
            id = declaration.id();
            founder = declaration.house();
            statement = declaration.statement();
        }

        private void record(Action action, House house, long revision) {
            if (action == Action.REINSTATED) members.remove(house.houseId());
            else members.put(house.houseId(), house);
            this.revision = revision;
            history.add(new PublicEvent(action, house, revision));
        }

        private Faction snapshot() {
            return new Faction(id, founder, statement, revision, List.copyOf(members.values()), history);
        }
    }

    private static final class Replay {
        private final Map<UUID, FactionReplay> factions = new LinkedHashMap<>();
        private final Map<String, Membership> memberships = new LinkedHashMap<>();
        private final Map<String, Visit> visits = new LinkedHashMap<>();
        private final Map<UUID, Entry> entriesById = new LinkedHashMap<>();
        private final Set<UUID> restoredMotions = new HashSet<>();
        private final List<Effect> effects = new ArrayList<>();
        private int pendingVisitSteps;
        private long revision;

        private void accept(Entry entry) {
            if (entriesById.putIfAbsent(entry.id(), entry) != null)
                throw new IllegalArgumentException("Duplicate rebellion operation ID.");
            switch (entry) {
                case Declare declaration -> declare(declaration);
                case Join join -> join(join);
                case Grant grant -> grant(grant);
                case Enter enter -> moveVisit(enter.expected(), enter.houseId(), enter.grant(), VisitStatus.WAITING, VisitStatus.INSIDE);
                case Exit exit -> moveVisit(exit.expected(), exit.houseId(), exit.grant(), VisitStatus.INSIDE, VisitStatus.ENDED);
                case Restore restore -> restore(restore.effect());
            }
            revision++;
            // Keep enough room for every outstanding visit and for the Council to restore any rebel House.
            if (revision + pendingVisitSteps + memberships.size() > MAX_ENTRIES)
                throw new IllegalStateException("The rebellion journal has reached its reserved capacity.");
        }

        private void declare(Declare declaration) {
            checkRevision(declaration.expected());
            if (factions.size() >= MAX_FACTIONS) throw new IllegalStateException("The rebellion faction limit has been reached.");
            var faction = new FactionReplay(declaration);
            addMember(faction, declaration.house(), Action.DECLARED);
            factions.put(faction.id, faction);
        }

        private void join(Join join) {
            checkRevision(join.expected());
            FactionReplay faction = factions.get(join.faction());
            if (faction == null) throw new IllegalArgumentException("Choose a known rebellion faction.");
            addMember(faction, join.house(), Action.JOINED);
        }

        private void addMember(FactionReplay faction, House house, Action action) {
            if (memberships.containsKey(house.houseId())) throw new IllegalStateException("This House already belongs to a rebellion.");
            if (memberships.size() >= MAX_TARGETS) throw new IllegalStateException("The active rebellion House limit has been reached.");
            memberships.put(house.houseId(), new Membership(faction.id, revision + 1, house));
            faction.record(action, house, revision + 1);
        }

        private void grant(Grant grant) {
            checkRevision(grant.expected());
            requireRebel(grant.houseId());
            Visit previous = visits.get(grant.houseId());
            if (previous != null && previous.status() != VisitStatus.ENDED)
                throw new IllegalStateException("This House already has an outstanding palace visit.");
            visits.put(grant.houseId(), new Visit(grant.id(), grant.houseId(), VisitStatus.WAITING, revision + 1));
            pendingVisitSteps += 2;
        }

        private void moveVisit(long expected, String houseId, UUID grant, VisitStatus before, VisitStatus after) {
            checkRevision(expected);
            requireRebel(houseId);
            Visit visit = visits.get(houseId);
            if (visit == null || !visit.grant().equals(grant) || visit.status() != before)
                throw new IllegalStateException("This palace visit changed or has ended.");
            visits.put(houseId, new Visit(grant, houseId, after, revision + 1));
            pendingVisitSteps--;
        }

        private void restore(Effect effect) {
            if (!restoredMotions.add(effect.motion())) throw new IllegalArgumentException("This motion already reinstated Houses.");
            for (Target target : effect.targets()) {
                FactionReplay faction = factions.get(target.faction());
                validateTarget(target, memberships.get(target.houseId()), faction == null ? 0 : faction.revision);
            }
            for (Target target : effect.targets()) {
                Membership membership = memberships.remove(target.houseId());
                factions.get(target.faction()).record(Action.REINSTATED, membership.house(), revision + 1);
                Visit visit = visits.get(target.houseId());
                if (visit != null && visit.status() != VisitStatus.ENDED) {
                    pendingVisitSteps -= visit.status() == VisitStatus.WAITING ? 2 : 1;
                    visits.put(target.houseId(), new Visit(visit.grant(), target.houseId(), VisitStatus.ENDED, revision + 1));
                }
            }
            effects.add(effect);
        }

        private void checkRevision(long expected) {
            if (revision != expected) throw new IllegalStateException("The rebellion registry changed. Reopen it and try again.");
        }

        private void requireRebel(String house) {
            if (!memberships.containsKey(house)) throw new IllegalStateException("Choose a House currently in rebellion.");
        }

        private State snapshot(List<Entry> entries) {
            var records = new LinkedHashMap<UUID, Faction>();
            factions.forEach((id, faction) -> records.put(id, faction.snapshot()));
            return new State(new Snapshot(revision, records, memberships, visits), List.copyOf(entries),
                    immutableMap(entriesById), List.copyOf(effects));
        }
    }

    static State replay(List<Entry> entries) {
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many rebellion operations.");
        var replay = new Replay();
        entries.forEach(replay::accept);
        return replay.snapshot(entries);
    }

    static byte[] encode(List<Entry> entries) {
        var root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("revision", entries.size());
        var history = new JsonArray();
        for (Entry entry : entries) {
            JsonObject object = JSON.toJsonTree(entry).getAsJsonObject();
            object.addProperty("kind", switch (entry) {
                case Declare ignored -> "DECLARE";
                case Join ignored -> "JOIN";
                case Grant ignored -> "GRANT";
                case Enter ignored -> "ENTER";
                case Exit ignored -> "EXIT";
                case Restore ignored -> "RESTORE";
            });
            history.add(object);
        }
        root.add("history", history);
        return (root + "\n").getBytes(StandardCharsets.UTF_8);
    }

    static State decode(byte[] bytes) throws IOException {
        try {
            if (bytes.length < 1 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid rebellion journal size.");
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root = BookConfig.strictObject(text);
            exact(root, "schema", "revision", "history");
            if (number(root, "schema") != 1) throw new IllegalArgumentException("Unknown rebellion journal schema.");
            JsonArray history = root.getAsJsonArray("history");
            if (history.size() > MAX_ENTRIES || number(root, "revision") != history.size())
                throw new IllegalArgumentException("Invalid rebellion journal revision.");
            List<Entry> entries = new ArrayList<>(history.size());
            for (JsonElement element : history) entries.add(read(element.getAsJsonObject()));
            return replay(entries);
        } catch (RuntimeException | IOException failure) {
            throw new IOException("Invalid rebellion journal. Existing data was preserved.", failure);
        }
    }

    private static Entry read(JsonObject object) {
        return switch (string(object, "kind")) {
            case "DECLARE" -> {
                exact(object, "kind", "id", "expected", "house", "statement");
                yield new Declare(uuid(object, "id"), number(object, "expected"), house(object), string(object, "statement"));
            }
            case "JOIN" -> {
                exact(object, "kind", "id", "expected", "house", "faction");
                yield new Join(uuid(object, "id"), number(object, "expected"), house(object), uuid(object, "faction"));
            }
            case "GRANT" -> {
                exact(object, "kind", "id", "expected", "houseId");
                yield new Grant(uuid(object, "id"), number(object, "expected"), string(object, "houseId"));
            }
            case "ENTER" -> {
                exact(object, "kind", "id", "expected", "houseId", "grant");
                yield new Enter(uuid(object, "id"), number(object, "expected"), string(object, "houseId"), uuid(object, "grant"));
            }
            case "EXIT" -> {
                exact(object, "kind", "id", "expected", "houseId", "grant");
                yield new Exit(uuid(object, "id"), number(object, "expected"), string(object, "houseId"), uuid(object, "grant"));
            }
            case "RESTORE" -> {
                exact(object, "kind", "effect");
                yield new Restore(effect(object.getAsJsonObject("effect")));
            }
            default -> throw new IllegalArgumentException("Unknown rebellion journal operation.");
        };
    }

    private static House house(JsonObject operation) {
        JsonObject object = operation.getAsJsonObject("house");
        exact(object, "houseId", "publicName");
        return new House(string(object, "houseId"), string(object, "publicName"));
    }

    private static Effect effect(JsonObject object) {
        exact(object, "ballot", "motion", "targets", "title");
        JsonArray array = object.getAsJsonArray("targets");
        if (array.isEmpty() || array.size() > MAX_TARGETS) throw new IllegalArgumentException("Invalid reinstatement targets.");
        List<Target> targets = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            JsonObject target = element.getAsJsonObject();
            exact(target, "houseId", "faction", "membershipRevision", "factionRevision");
            targets.add(new Target(string(target, "houseId"), uuid(target, "faction"),
                    number(target, "membershipRevision"), number(target, "factionRevision")));
        }
        return new Effect(uuid(object, "ballot"), uuid(object, "motion"), targets, string(object, "title"));
    }

    private static void checkOperation(UUID id, long revision) {
        Objects.requireNonNull(id);
        if (id.equals(NONE)) throw new IllegalArgumentException("A rebellion operation needs an ID.");
        if (revision < 0 || revision > MAX_ENTRIES) throw new IllegalArgumentException("Invalid rebellion operation revision.");
    }

    private static void exact(JsonObject object, String... fields) {
        if (object == null || !object.keySet().equals(Set.of(fields)))
            throw new IllegalArgumentException("Unexpected rebellion journal fields.");
    }

    private static String string(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected a rebellion string.");
        return element.getAsString();
    }

    private static long number(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()
                || !element.getAsString().matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("Expected a rebellion revision.");
        return Long.parseLong(element.getAsString());
    }

    private static UUID uuid(JsonObject object, String field) {
        String source = string(object, field);
        UUID id = UUID.fromString(source);
        if (!id.toString().equals(source)) throw new IllegalArgumentException("Noncanonical rebellion identifier.");
        return id;
    }
}
