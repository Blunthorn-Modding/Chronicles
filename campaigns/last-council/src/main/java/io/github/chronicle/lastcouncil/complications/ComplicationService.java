package io.github.chronicle.lastcouncil.complications;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.function.IntUnaryOperator;

/** Server-private scheduler. Never send its snapshot to players or ordinary operators. */
public final class ComplicationService {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    public enum Phase { WAITING, CLAIMED, DELIVERED, CANCELLED }
    public record Slot(int ordinal, int meeting, long target, Phase phase) {
        @Override public String toString() { return "Slot[private]"; }
    }
    public record Snapshot(UUID experience, ComplicationConfig config, List<Slot> slots,
                           List<Long> meetingTicks, long councilTicks, long lastDelivery,
                           long lastSharedTick, int currentMeeting, boolean advancing, String endReason) {
        public Snapshot { slots = List.copyOf(slots); meetingTicks = List.copyOf(meetingTicks); }
        @Override public String toString() { return "Snapshot[private]"; }
    }
    public record Claim(int ordinal, String key) {}
    private final ComplicationStorage storage;
    private final IntUnaryOperator random;
    private Snapshot state;
    private boolean poisoned;

    private ComplicationService(ComplicationStorage storage, IntUnaryOperator random, Snapshot state) {
        this.storage = Objects.requireNonNull(storage); this.random = Objects.requireNonNull(random); this.state = state;
    }
    public static ComplicationService load(ComplicationStorage storage, IntUnaryOperator random) throws IOException {
        byte[] bytes = storage.load();
        if (bytes == null) return new ComplicationService(storage, random, null);
        try {
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException();
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root = BookConfig.strictObject(text);
            if (!root.keySet().equals(Set.of("schemaVersion", "state")) || !root.get("schemaVersion").toString().equals("1")) throw new IllegalArgumentException();
            validateNumbers(root);
            Snapshot snapshot = JSON.fromJson(root.get("state"), Snapshot.class);
            if (!JSON.toJsonTree(snapshot).equals(root.get("state"))) throw new IllegalArgumentException();
            validate(snapshot);
            return new ComplicationService(storage, random, snapshot);
        } catch (RuntimeException | CharacterCodingException ex) { throw new IOException("Invalid complication state", ex); }
    }
    public synchronized void initialize(ComplicationConfig config, UUID experience) throws IOException {
        ready(); if (state != null) throw new IllegalStateException("Already initialized");
        Objects.requireNonNull(config); Objects.requireNonNull(experience);
        List<Slot> slots = allocate(config, config.meetings(), java.util.stream.IntStream.range(0, config.total()).boxed().toList());
        save(new Snapshot(experience, config, slots, Collections.nCopies(64, 0L), 0, -1, -1, 0, false, ""));
    }
    public synchronized boolean isInitialized() { ready(); return state != null; }
    public synchronized void verifyUnchanged() throws IOException { ready(); storage.verifyUnchanged(); }
    public synchronized Snapshot snapshot() { initialized(); return state; }
    public synchronized ComplicationConfig config() { initialized(); return state.config(); }
    public synchronized long elapsed(int meeting) { initialized(); checkMeeting(meeting); return state.meetingTicks().get(meeting - 1); }
    public synchronized int currentMeeting() { initialized(); return state.currentMeeting(); }
    /** Re-anchor with advance=false after restart and at every eligibility transition. */
    public synchronized void observeClock(int meeting, long sharedTick, boolean advance) throws IOException {
        initialized(); if (meeting != 0) checkMeeting(meeting);
        if (sharedTick < 0 || sharedTick < state.lastSharedTick() || advance && meeting == 0 || meeting != 0 && meeting < state.currentMeeting() || meeting > state.currentMeeting() && state.slots().stream().anyMatch(s -> s.phase() == Phase.CLAIMED)) throw new IllegalArgumentException("Invalid shared clock");
        long delta = advance && state.advancing() && meeting == state.currentMeeting() ? sharedTick - state.lastSharedTick() : 0;
        List<Long> clocks = new ArrayList<>(state.meetingTicks());
        long council = Math.addExact(state.councilTicks(), delta);
        if (delta > 0) clocks.set(meeting - 1, Math.addExact(clocks.get(meeting - 1), delta));
        save(new Snapshot(state.experience(), state.config(), state.slots(), clocks, council, state.lastDelivery(), sharedTick, meeting == 0 ? state.currentMeeting() : meeting, advance, state.endReason()));
    }
    /** Claimed slots are always returned for recovery; force bypasses target, never spacing. */
    public synchronized Optional<Claim> due(int meeting, boolean force) {
        initialized(); checkMeeting(meeting);
        if (!state.endReason().isEmpty()) return Optional.empty();
        for (Slot s : state.slots()) if (s.meeting() == meeting && s.phase() == Phase.CLAIMED) return Optional.of(claimOf(s));
        if (state.lastDelivery() >= 0 && state.councilTicks() - state.lastDelivery() < state.config().spacingTicks()) return Optional.empty();
        return state.slots().stream().filter(s -> s.meeting() == meeting && s.phase() == Phase.WAITING && (force || s.target() <= elapsed(meeting)))
                .min(Comparator.comparingLong(Slot::target).thenComparingInt(Slot::ordinal)).map(this::claimOf);
    }
    public synchronized boolean pendingCurrentMeeting(int meeting) {
        initialized(); checkMeeting(meeting);
        return state.slots().stream().anyMatch(s -> s.meeting() == meeting && (s.phase() == Phase.WAITING || s.phase() == Phase.CLAIMED));
    }
    public synchronized Claim claim(int ordinal) throws IOException {
        initialized(); Slot slot = slot(ordinal);
        if (slot.phase() == Phase.CLAIMED) return claimOf(slot);
        if (slot.phase() != Phase.WAITING || state.currentMeeting() != slot.meeting() || due(slot.meeting(), true).map(Claim::ordinal).orElse(-1) != ordinal)
            throw new IllegalStateException("Slot cannot be claimed");
        replace(slot, Phase.CLAIMED); return claimOf(slot);
    }
    public synchronized void deliver(int ordinal) throws IOException {
        initialized(); Slot slot = slot(ordinal);
        if (slot.phase() == Phase.DELIVERED) return;
        if (slot.phase() != Phase.CLAIMED) throw new IllegalStateException("Slot is not claimed");
        List<Slot> slots = new ArrayList<>(state.slots()); slots.set(ordinal, new Slot(ordinal, slot.meeting(), slot.target(), Phase.DELIVERED));
        save(new Snapshot(state.experience(), state.config(), slots, state.meetingTicks(), state.councilTicks(), state.councilTicks(), state.lastSharedTick(), state.currentMeeting(), state.advancing(), state.endReason()));
    }
    /** Confirmation belongs to the caller. Current and claimed slots remain fixed. */
    public synchronized void cancelFuture(ComplicationConfig expected, int currentMeeting, List<Integer> remaining) throws IOException {
        initialized(); if (!state.config().equals(expected)) throw new IllegalStateException("Stale configuration");
        checkMeeting(currentMeeting);
        if (currentMeeting != state.currentMeeting()) throw new IllegalStateException("Stale meeting");
        List<Integer> eligible = List.copyOf(remaining); int previous = currentMeeting;
        for (int m : eligible) { if (m <= previous || !expected.meetings().contains(m)) throw new IllegalArgumentException("Invalid remaining meetings"); previous = m; }
        List<Integer> ordinals = state.slots().stream().filter(s -> s.meeting() > currentMeeting && s.phase() == Phase.WAITING).map(Slot::ordinal).toList();
        Set<Integer> pinned = new HashSet<>();
        state.slots().stream().filter(s -> s.meeting() > currentMeeting && s.phase() == Phase.CLAIMED).forEach(s -> pinned.add(s.meeting()));
        eligible = eligible.stream().filter(m -> !pinned.contains(m)).toList();
        List<Slot> replacement = allocate(expected, eligible, ordinals);
        List<Slot> slots = new ArrayList<>(state.slots()); for (Slot s : replacement) slots.set(s.ordinal(), s);
        List<Integer> retained = new ArrayList<>();
        for (int m : expected.meetings()) if (m <= currentMeeting) retained.add(m);
        retained.addAll(remaining);
        ComplicationConfig effective = new ComplicationConfig(expected.poolId(), expected.total(), retained, expected.earliestTicks(), expected.latestTicks(), expected.spacingTicks());
        save(new Snapshot(state.experience(), effective, slots, state.meetingTicks(), state.councilTicks(), state.lastDelivery(), state.lastSharedTick(), state.currentMeeting(), state.advancing(), state.endReason()));
    }
    public synchronized void exceptionalEnd(String reason) throws IOException {
        initialized(); if (reason == null || reason.isBlank() || reason.length() > 512) throw new IllegalArgumentException("Reason required");
        if (state.slots().stream().anyMatch(s -> s.phase() == Phase.CLAIMED)) throw new IllegalStateException("Reconcile claimed delivery first");
        List<Slot> slots = new ArrayList<>();
        for (Slot s : state.slots()) slots.add(s.phase() == Phase.WAITING ? new Slot(s.ordinal(), s.meeting(), s.target(), Phase.CANCELLED) : s);
        save(copy(slots, reason));
    }
    private Snapshot copy(List<Slot> slots, String reason) { return new Snapshot(state.experience(), state.config(), slots, state.meetingTicks(), state.councilTicks(), state.lastDelivery(), state.lastSharedTick(), state.currentMeeting(), state.advancing(), reason); }
    private void replace(Slot s, Phase phase) throws IOException { List<Slot> slots = new ArrayList<>(state.slots()); slots.set(s.ordinal(), new Slot(s.ordinal(), s.meeting(), s.target(), phase)); save(copy(slots, state.endReason())); }
    private Slot slot(int ordinal) { if (ordinal < 0 || ordinal >= state.slots().size()) throw new IllegalArgumentException("Invalid ordinal"); return state.slots().get(ordinal); }
    private Claim claimOf(Slot s) { return new Claim(s.ordinal(), state.experience() + ":" + s.ordinal()); }
    private List<Slot> allocate(ComplicationConfig config, List<Integer> meetings, List<Integer> ordinals) {
        if ((long) meetings.size() * config.capacity() < ordinals.size()) throw new IllegalStateException("Insufficient remaining capacity; exceptional end requires a reason");
        Map<Integer,List<Integer>> groups = new LinkedHashMap<>(); for (int m : meetings) groups.put(m, new ArrayList<>());
        for (int ordinal : ordinals) {
            List<Integer> available = meetings.stream().filter(m -> groups.get(m).size() < config.capacity()).toList();
            groups.get(available.get(next(available.size()))).add(ordinal);
        }
        List<Slot> result = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            int n = entry.getValue().size(); if (n == 0) continue;
            long span = config.latestTicks() - config.earliestTicks() - (n - 1L) * config.spacingTicks();
            long[] offsets = new long[n]; for (int i = 0; i < n; i++) offsets[i] = next((int)span + 1);
            Arrays.sort(offsets);
            for (int i = 0; i < n; i++) result.add(new Slot(entry.getValue().get(i), entry.getKey(), config.earliestTicks() + offsets[i] + i * config.spacingTicks(), Phase.WAITING));
        }
        result.sort(Comparator.comparingInt(Slot::ordinal)); return result;
    }
    private int next(int bound) { int value = random.applyAsInt(bound); if (value < 0 || value >= bound) throw new IllegalStateException("Random source out of bounds"); return value; }
    private void save(Snapshot next) throws IOException {
        ready(); validate(next);
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", 1); root.add("state", JSON.toJsonTree(next));
        byte[] bytes;
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(root + "\n"));
            bytes = new byte[encoded.remaining()]; encoded.get(bytes);
        } catch (CharacterCodingException invalid) { throw new IOException("Invalid complication text", invalid); }
        if (bytes.length > MAX_BYTES) throw new IOException("Complication state too large");
        try { storage.save(bytes); } catch (IOException | RuntimeException failure) { poisoned = true; throw failure; }
        state = next;
    }
    private void ready() { if (poisoned) throw new IllegalStateException("Reload complication state after uncertain write"); }
    private void initialized() { ready(); if (state == null) throw new IllegalStateException("Not initialized"); }
    private static void checkMeeting(int meeting) { if (meeting < 1 || meeting > 64) throw new IllegalArgumentException("Invalid meeting"); }
    private static void validateNumbers(JsonElement value) {
        if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) validateNumbers(entry.getValue());
        else if (value.isJsonArray()) for (JsonElement element : value.getAsJsonArray()) validateNumbers(element);
        else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && !value.getAsString().matches("(0|-?[1-9][0-9]*)")) throw new IllegalArgumentException("Noncanonical integer");
    }
    private static void validate(Snapshot s) {
        Objects.requireNonNull(s); Objects.requireNonNull(s.experience()); Objects.requireNonNull(s.config());
        if (s.slots().size() != s.config().total() || s.meetingTicks().size() != 64 || s.councilTicks() < 0 || s.councilTicks() > 64 * ComplicationConfig.DAY || s.lastDelivery() < -1 || s.lastDelivery() > s.councilTicks() || s.lastSharedTick() < -1 || s.currentMeeting() < 0 || s.currentMeeting() > 64 || s.advancing() && s.currentMeeting() == 0 || s.endReason() == null || s.endReason().length() > 512) throw new IllegalArgumentException("Invalid state");
        long sum = 0; for (Long tick : s.meetingTicks()) { if (tick == null || tick < 0 || tick > ComplicationConfig.DAY) throw new IllegalArgumentException("Invalid clock"); sum += tick; }
        if (sum != s.councilTicks()) throw new IllegalArgumentException("Invalid clock total");
        int claimed = 0, delivered = 0;
        for (int i = 0; i < s.slots().size(); i++) {
            Slot slot = s.slots().get(i);
            if (slot.ordinal() != i || !s.config().meetings().contains(slot.meeting()) || slot.target() < s.config().earliestTicks() || slot.target() > s.config().latestTicks() || slot.phase() == null || !s.endReason().isEmpty() && (slot.phase() == Phase.WAITING || slot.phase() == Phase.CLAIMED) || slot.phase() == Phase.CANCELLED && s.endReason().isBlank()) throw new IllegalArgumentException("Invalid slot");
            if (slot.phase() == Phase.CLAIMED) { claimed++; if (slot.meeting() != s.currentMeeting()) throw new IllegalArgumentException("Invalid claim"); }
            if (slot.phase() == Phase.DELIVERED) { delivered++; if (slot.meeting() > s.currentMeeting()) throw new IllegalArgumentException("Invalid delivery"); }
            for (int j = 0; j < i; j++) {
                Slot other = s.slots().get(j);
                if (slot.meeting() == other.meeting() && Math.abs(slot.target() - other.target()) < s.config().spacingTicks()) throw new IllegalArgumentException("Invalid target spacing");
            }
        }
        if (claimed > 1 || (delivered > 0) != (s.lastDelivery() >= 0)) throw new IllegalArgumentException("Invalid delivery history");
    }
}
