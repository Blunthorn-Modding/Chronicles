package io.github.chronicle.core.ballots;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;

/** Bounded, strict journal; failed reads never grant write authority. */
public final class BallotStore implements BallotRepository {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private final AtomicDataFile file;
    private boolean loaded;
    public BallotStore(Path path) { file = new AtomicDataFile(path, 2 * 1024 * 1024); }
    public synchronized BallotState load() throws IOException {
        loaded = false; byte[] bytes = file.load();
        try { BallotState result = bytes == null ? BallotState.empty() : decode(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()); loaded = true; return result; }
        catch (RuntimeException | CharacterCodingException e) { throw new IOException("Invalid ballot ledger", e); }
    }
    public synchronized void verifyUnchanged() throws IOException { if (!loaded) throw new IOException("Load ballot ledger before use"); file.verifyUnchanged(); }
    public synchronized void save(BallotState state) throws IOException {
        if (!loaded) throw new IOException("Load ballot ledger before saving");
        try { String encoded = encode(state); if (!decode(encoded).equals(state)) throw new IllegalArgumentException("Ballot round trip failed"); file.save(encoded.getBytes(StandardCharsets.UTF_8)); }
        catch (RuntimeException e) { throw new IOException("Invalid ballot ledger state", e); }
    }
    public static String encode(BallotState state) {
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", 1); root.addProperty("revision", state.revision());
        JsonArray entries = new JsonArray(); state.ballots().values().forEach(b -> entries.add(JSON.toJsonTree(b))); root.add("ballots", entries); return root + "\n";
    }
    public static BallotState decode(String source) {
        JsonObject root = BookConfig.strictObject(source); fields(root, "schemaVersion", "revision", "ballots");
        if (number(root, "schemaVersion") != 1) throw new IllegalArgumentException("Unsupported schema"); long revision = number(root, "revision");
        Map<UUID,Ballot> ballots = new LinkedHashMap<>();
        for (JsonElement entry : array(root, "ballots")) {
            JsonObject b = object(entry); fields(b, "definition", "openedBy", "openedAt", "status", "votes", "finishedBy", "finishedAt", "reason");
            JsonObject d = object(b.get("definition")); fields(d, "id", "purpose", "title", "options", "electorate", "threshold", "visibility");
            List<BallotOption> options = new ArrayList<>(); for (JsonElement item : array(d, "options")) { JsonObject o = object(item); fields(o, "id", "label", "canPass"); options.add(new BallotOption(string(o, "id"), string(o, "label"), bool(o, "canPass"))); }
            Map<String,BallotElector> electorate = new LinkedHashMap<>(); object(d.get("electorate")).entrySet().forEach(e -> { JsonObject v = object(e.getValue()); fields(v, "actor", "label"); electorate.put(e.getKey(), new BallotElector(uuid(v, "actor"), string(v, "label"))); });
            BallotDefinition def = new BallotDefinition(uuid(d, "id"), string(d, "purpose"), string(d, "title"), options, electorate, Math.toIntExact(number(d, "threshold")), BallotVisibility.valueOf(string(d, "visibility")));
            Map<String,String> votes = new LinkedHashMap<>(); object(b.get("votes")).entrySet().forEach(e -> votes.put(e.getKey(), primitiveString(e.getValue())));
            JsonElement finished = b.get("finishedBy"); Ballot ballot = new Ballot(def, uuid(b, "openedBy"), string(b, "openedAt"), BallotStatus.valueOf(string(b, "status")), votes, finished.isJsonNull() ? null : uuid(b, "finishedBy"), string(b, "finishedAt"), string(b, "reason"));
            if (ballots.putIfAbsent(def.id(), ballot) != null) throw new IllegalArgumentException("Duplicate ballot ID");
        }
        return new BallotState(revision, ballots);
    }
    private static JsonObject object(JsonElement e) { if (e == null || !e.isJsonObject()) throw new IllegalArgumentException("Expected object"); return e.getAsJsonObject(); }
    private static JsonArray array(JsonObject o, String k) { JsonElement e = o.get(k); if (e == null || !e.isJsonArray()) throw new IllegalArgumentException("Expected array"); return e.getAsJsonArray(); }
    private static void fields(JsonObject o, String... fields) { if (!o.keySet().equals(Set.of(fields))) throw new IllegalArgumentException("Unexpected fields"); }
    private static String primitiveString(JsonElement e) { if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string"); return e.getAsString(); }
    private static String string(JsonObject o, String k) { return primitiveString(o.get(k)); }
    private static UUID uuid(JsonObject o, String k) { String s = string(o, k); UUID id = UUID.fromString(s); if (!id.toString().equals(s)) throw new IllegalArgumentException("Noncanonical UUID"); return id; }
    private static long number(JsonObject o, String k) { JsonElement e = o.get(k); if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || !e.getAsString().matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("Expected integer"); return Long.parseLong(e.getAsString()); }
    private static boolean bool(JsonObject o, String k) { JsonElement e = o.get(k); if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Expected boolean"); return e.getAsBoolean(); }
}
