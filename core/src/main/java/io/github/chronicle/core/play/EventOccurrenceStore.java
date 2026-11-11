package io.github.chronicle.core.play;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.history.ImmutableHistoryArchive;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Strict atomic live occurrence journal with create-only terminal source and actor histories. */
public final class EventOccurrenceStore {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "revision", "claims", "actors");
    private static final Set<String> CLAIM_FIELDS = Set.of("eventId", "operationId", "configurationFingerprint", "source",
            "claimedSession", "claimedAtTicks", "recipients", "stage", "detail");
    private static final Set<String> SOURCE_FIELDS = Set.of("id", "type", "target", "activity", "actor", "activeOrigin", "flags", "authority");
    private static final Set<String> ACTOR_FIELDS = Set.of("eventId", "actor", "operationId", "sourceId", "configurationFingerprint");
    private static final Set<String> DECISION_FIELDS = Set.of("kind", "eventId", "configurationFingerprint", "source", "operationId", "recipients");

    private final Path file;
    private final Path marker;
    private final ImmutableHistoryArchive decisions;
    private final ImmutableHistoryArchive actors;
    private byte[] observed;
    private boolean loaded;

    public EventOccurrenceStore(Path file) {
        this.file = Objects.requireNonNull(file, "Event occurrence journal is required").toAbsolutePath().normalize();
        this.marker = this.file.resolveSibling("event-occurrences.initialized");
        this.decisions = new ImmutableHistoryArchive(this.file.resolveSibling("event-occurrence-history"), "event-occurrence", MAX_BYTES);
        this.actors = new ImmutableHistoryArchive(this.file.resolveSibling("event-actor-history"), "event-actor", MAX_BYTES);
    }

    public synchronized EventOccurrenceState load() throws IOException {
        byte[] bytes = current();
        boolean initialized = Files.exists(marker, LinkOption.NOFOLLOW_LINKS);
        if (initialized && (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                || Files.size(marker) != 1 || Files.readAllBytes(marker)[0] != '1'))
            throw new IOException("Invalid Event occurrence initialization marker; preserve and repair the evidence");
        if (initialized && bytes == null) throw new IOException("Initialized Event occurrence journal is missing; restore its exact evidence");
        EventOccurrenceState state;
        try { state = bytes == null ? EventOccurrenceState.empty() : decodeState(utf8(bytes)); }
        catch (RuntimeException failure) { throw new IOException("Event occurrence journal is invalid and was preserved", failure); }
        observed = digest(bytes); loaded = true;
        if (bytes == null) save(state);
        // These create-only anchors distinguish a fresh world from a lost history directory.
        if (initialized) {
            if (decisions.read("initialization").isEmpty() || actors.read("initialization").isEmpty())
                throw new IOException("Initialized Event occurrence history is missing; restore its exact evidence");
        } else {
            decisions.publish("initialization", new byte[]{'1'});
            actors.publish("initialization", new byte[]{'1'});
            Path temporary = Files.createTempFile(file.getParent(), ".event-occurrences-init-", ".tmp");
            try { force(temporary, new byte[]{'1'}); Files.createLink(marker, temporary); }
            finally { Files.deleteIfExists(temporary); }
        }
        return state;
    }

    public synchronized void save(EventOccurrenceState state) throws IOException {
        if (!loaded) throw new IOException("Load Event occurrence evidence before saving");
        byte[] before = current();
        if (!Arrays.equals(observed, digest(before))) throw new IOException("Event occurrence evidence changed externally");
        byte[] bytes = encodeState(state).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Event occurrence journal capacity reached");
        validatePath(file.getParent()); Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".event-occurrences-", ".tmp");
        try {
            force(temporary, bytes);
            if (!Arrays.equals(observed, digest(current()))) throw new IOException("Event occurrence evidence changed before replacement");
            if (before == null) Files.createLink(file, temporary);
            else move(temporary, file);
            observed = digest(bytes);
        } finally { Files.deleteIfExists(temporary); }
    }

    public synchronized void publishDecision(EventOccurrenceState.Decision decision) throws IOException {
        decisions.publish(EventOccurrenceState.sourceKey(decision.eventId(), decision.source().id()),
                encodeDecision(decision).getBytes(StandardCharsets.UTF_8));
    }

    public synchronized Optional<EventOccurrenceState.Decision> decision(String eventId, String sourceId) throws IOException {
        Optional<byte[]> payload = decisions.read(EventOccurrenceState.sourceKey(eventId, sourceId));
        if (payload.isEmpty()) return Optional.empty();
        try {
            EventOccurrenceState.Decision decision = decodeDecision(utf8(payload.get()));
            if (!decision.eventId().equals(eventId) || !decision.source().id().equals(sourceId)) {
                throw new IOException("archived Event occurrence identity mismatch");
            }
            return Optional.of(decision);
        } catch (RuntimeException failure) { throw new IOException("archived Event occurrence is invalid", failure); }
    }

    public synchronized void publishActor(EventOccurrenceState.ActorConsumption actor) throws IOException {
        actors.publish(actor.key(), encodeActor(actor).toString().getBytes(StandardCharsets.UTF_8));
    }

    public synchronized Optional<EventOccurrenceState.ActorConsumption> actor(String eventId, UUID actor) throws IOException {
        String key = EventOccurrenceState.actorKey(eventId, actor);
        Optional<byte[]> payload = actors.read(key);
        if (payload.isEmpty()) return Optional.empty();
        try {
            EventOccurrenceState.ActorConsumption value = decodeActor(BookConfig.strictObject(utf8(payload.get())));
            if (!value.key().equals(key)) throw new IOException("archived Event actor identity mismatch");
            return Optional.of(value);
        } catch (RuntimeException failure) { throw new IOException("archived Event actor history is invalid", failure); }
    }

    public static String encodeState(EventOccurrenceState state) {
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", 1); root.addProperty("revision", state.revision());
        JsonArray claims = new JsonArray(); state.claims().values().forEach(claim -> claims.add(encodeClaim(claim))); root.add("claims", claims);
        JsonArray actors = new JsonArray(); state.actors().values().forEach(actor -> actors.add(encodeActor(actor))); root.add("actors", actors);
        return root + "\n";
    }

    public static EventOccurrenceState decodeState(String source) {
        JsonObject root = BookConfig.strictObject(source); PlayJson.fields(root, ROOT_FIELDS, "Event occurrence journal");
        if (PlayJson.integer(root.get("schemaVersion"), "schemaVersion") != 1) throw new IllegalArgumentException("Unsupported Event occurrence schema");
        Map<UUID, EventOccurrenceState.Claim> claims = new LinkedHashMap<>();
        JsonArray claimValues = PlayJson.array(root.get("claims"), "Event occurrence claims");
        if (claimValues.size() > EventOccurrenceState.MAX_LIVE_CLAIMS) throw new IllegalArgumentException("Too many live Event occurrence claims");
        for (JsonElement value : claimValues) {
            EventOccurrenceState.Claim claim = decodeClaim(PlayJson.object(value, "Event occurrence claim"));
            if (claims.putIfAbsent(claim.operationId(), claim) != null) throw new IllegalArgumentException("Duplicate Event occurrence operation");
        }
        Map<String, EventOccurrenceState.ActorConsumption> actors = new LinkedHashMap<>();
        JsonArray actorValues = PlayJson.array(root.get("actors"), "Event actor histories");
        if (actorValues.size() > EventOccurrenceState.MAX_LIVE_ACTORS) throw new IllegalArgumentException("Too many live Event actor histories");
        for (JsonElement value : actorValues) {
            EventOccurrenceState.ActorConsumption actor = decodeActor(PlayJson.object(value, "Event actor history"));
            if (actors.putIfAbsent(actor.key(), actor) != null) throw new IllegalArgumentException("Duplicate Event actor history");
        }
        return new EventOccurrenceState(PlayJson.number(root.get("revision"), "revision"), claims, actors);
    }

    private static JsonObject encodeClaim(EventOccurrenceState.Claim claim) {
        JsonObject value = new JsonObject(); value.addProperty("eventId", claim.eventId()); value.addProperty("operationId", claim.operationId().toString());
        value.addProperty("configurationFingerprint", claim.configurationFingerprint()); value.add("source", encodeSource(claim.source()));
        value.addProperty("claimedSession", claim.claimedSession()); value.addProperty("claimedAtTicks", claim.claimedAtTicks());
        value.add("recipients", uuids(claim.recipients())); value.addProperty("stage", claim.stage().name()); value.addProperty("detail", claim.detail()); return value;
    }

    private static EventOccurrenceState.Claim decodeClaim(JsonObject value) {
        PlayJson.fields(value, CLAIM_FIELDS, "Event occurrence claim");
        return new EventOccurrenceState.Claim(text(value, "eventId"), PlayJson.uuid(value.get("operationId"), "operationId"), text(value, "configurationFingerprint"),
                decodeSource(PlayJson.object(value.get("source"), "source")), PlayJson.integer(value.get("claimedSession"), "claimedSession"),
                PlayJson.number(value.get("claimedAtTicks"), "claimedAtTicks"), decodeUuids(value.get("recipients")),
                PlayJson.enumeration(value.get("stage"), EventOccurrenceState.Stage.class, "stage"), text(value, "detail"));
    }

    private static JsonObject encodeSource(EventOccurrenceState.SourceSnapshot source) {
        JsonObject value = new JsonObject(); value.addProperty("id", source.id()); value.addProperty("type", source.type());
        value.addProperty("target", source.target()); value.addProperty("activity", source.activity());
        value.add("actor", source.actor() == null ? JsonNull.INSTANCE : new JsonPrimitive(source.actor().toString()));
        value.addProperty("activeOrigin", source.activeOrigin()); JsonObject flags = new JsonObject(); source.flags().forEach(flags::addProperty);
        value.add("flags", flags); value.addProperty("authority", source.authority()); return value;
    }

    private static EventOccurrenceState.SourceSnapshot decodeSource(JsonObject value) {
        PlayJson.fields(value, SOURCE_FIELDS, "Event occurrence source"); Map<String, Boolean> flags = new TreeMap<>();
        JsonObject encodedFlags = PlayJson.object(value.get("flags"), "source flags");
        if (encodedFlags.size() > 256) throw new IllegalArgumentException("Too many source flags");
        encodedFlags.entrySet().forEach(entry -> flags.put(entry.getKey(), bool(entry.getValue(), "source flag")));
        JsonElement actor = value.get("actor");
        return new EventOccurrenceState.SourceSnapshot(text(value, "id"), text(value, "type"), text(value, "target"), text(value, "activity"),
                actor.isJsonNull() ? null : PlayJson.uuid(actor, "source actor"), bool(value.get("activeOrigin"), "activeOrigin"), flags, text(value, "authority"));
    }

    private static JsonObject encodeActor(EventOccurrenceState.ActorConsumption actor) {
        JsonObject value = new JsonObject(); value.addProperty("eventId", actor.eventId()); value.addProperty("actor", actor.actor().toString());
        value.addProperty("operationId", actor.operationId().toString()); value.addProperty("sourceId", actor.sourceId());
        value.addProperty("configurationFingerprint", actor.configurationFingerprint()); return value;
    }

    private static EventOccurrenceState.ActorConsumption decodeActor(JsonObject value) {
        PlayJson.fields(value, ACTOR_FIELDS, "Event actor history");
        return new EventOccurrenceState.ActorConsumption(text(value, "eventId"), PlayJson.uuid(value.get("actor"), "actor"),
                PlayJson.uuid(value.get("operationId"), "operationId"), text(value, "sourceId"), text(value, "configurationFingerprint"));
    }

    private static String encodeDecision(EventOccurrenceState.Decision decision) {
        JsonObject value = new JsonObject(); value.addProperty("kind", decision.kind().name()); value.addProperty("eventId", decision.eventId());
        value.addProperty("configurationFingerprint", decision.configurationFingerprint()); value.add("source", encodeSource(decision.source()));
        value.add("operationId", decision.operationId() == null ? JsonNull.INSTANCE : new JsonPrimitive(decision.operationId().toString()));
        value.add("recipients", uuids(decision.recipients())); return value + "\n";
    }

    private static EventOccurrenceState.Decision decodeDecision(String source) {
        JsonObject value = BookConfig.strictObject(source); PlayJson.fields(value, DECISION_FIELDS, "Event occurrence decision");
        JsonElement operation = value.get("operationId");
        return new EventOccurrenceState.Decision(PlayJson.enumeration(value.get("kind"), EventOccurrenceState.Decision.Kind.class, "kind"),
                text(value, "eventId"), text(value, "configurationFingerprint"), decodeSource(PlayJson.object(value.get("source"), "source")),
                operation.isJsonNull() ? null : PlayJson.uuid(operation, "operationId"), decodeUuids(value.get("recipients")));
    }

    private static JsonArray uuids(List<UUID> values) { JsonArray result = new JsonArray(); values.forEach(value -> result.add(value.toString())); return result; }
    private static List<UUID> decodeUuids(JsonElement value) { JsonArray array = PlayJson.array(value, "recipients"); if (array.size() > PlayState.MAX_PARTICIPANTS) throw new IllegalArgumentException("Too many recipients"); List<UUID> result = new ArrayList<>(); array.forEach(item -> result.add(PlayJson.uuid(item, "recipient"))); return result; }
    private static String text(JsonObject value, String field) { return PlayJson.string(PlayJson.required(value, field), field); }
    private static boolean bool(JsonElement value, String field) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(field + " must be boolean"); return value.getAsBoolean(); }

    private byte[] current() throws IOException {
        validatePath(file.getParent());
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES) throw new IOException("Invalid Event occurrence journal file");
        try (FileChannel input = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = input.size();
            if (size < 1 || size > MAX_BYTES) throw new IOException("Invalid Event occurrence journal size");
            ByteBuffer buffer = ByteBuffer.allocate(Math.toIntExact(size));
            while (buffer.hasRemaining()) if (input.read(buffer) < 0) throw new IOException("Truncated Event occurrence journal");
            if (input.read(ByteBuffer.allocate(1)) != -1) throw new IOException("Event occurrence journal grew during reading");
            return buffer.array();
        }
    }
    private static String utf8(byte[] bytes) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static void validatePath(Path path) throws IOException { for (Path value = path; value != null; value = value.getParent()) if (Files.isSymbolicLink(value)) throw new IOException("Event occurrence journal path cannot contain symbolic links"); }
    private static byte[] digest(byte[] value) throws IOException { if (value == null) return null; try { return MessageDigest.getInstance("SHA-256").digest(value); } catch (NoSuchAlgorithmException impossible) { throw new IOException(impossible); } }
    private static void force(Path path, byte[] bytes) throws IOException { try (FileChannel output = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) { ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) output.write(buffer); output.force(true); } }
    private static void move(Path from, Path to) throws IOException { try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException failure) { throw new IOException("Atomic Event occurrence replacement is unavailable", failure); } }
}
