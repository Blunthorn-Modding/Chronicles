package io.github.chronicle.core.play;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Strict compare-before-replace journal. Loading never creates or rewrites a file. */
public final class EventProgressStore {
    public static final long MAX_BYTES = 16L * 1024 * 1024;
    private final Path file;
    private byte[] observed;
    private boolean loaded;

    public EventProgressStore(Path file) { this.file = Objects.requireNonNull(file).toAbsolutePath().normalize(); }

    public synchronized EventProgressState load() throws IOException {
        byte[] bytes = current();
        EventProgressState state;
        try { state = bytes == null ? EventProgressState.empty() : decode(new String(bytes, StandardCharsets.UTF_8)); }
        catch (RuntimeException failure) { throw new IOException("Event progression journal is invalid", failure); }
        observed = digest(bytes); loaded = true;
        return state;
    }

    public synchronized void save(EventProgressState state) throws IOException {
        if (!loaded) throw new IOException("Load Event progression before saving");
        byte[] before = current();
        if (!Arrays.equals(observed, digest(before))) throw new IOException("Event progression changed outside this process; refusing replacement");
        byte[] bytes = encode(state).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Event progression journal capacity reached");
        Path parent = file.getParent();
        for (Path p = parent; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IOException("Event progression directory cannot be a symbolic link");
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, ".event-progress-", ".tmp");
        Path backup = null;
        try {
            force(temp, bytes);
            if (before != null) {
                Path target = file.resolveSibling(file.getFileName() + ".bak");
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Event progression backup is not a regular file");
                backup = Files.createTempFile(parent, ".event-progress-backup-", ".tmp");
                force(backup, before); move(backup, target); backup = null;
            }
            if (!Arrays.equals(observed, digest(current()))) throw new IOException("Event progression changed before replacement");
            move(temp, file); observed = digest(bytes);
        } finally { Files.deleteIfExists(temp); if (backup != null) Files.deleteIfExists(backup); }
    }

    private byte[] current() throws IOException {
        for (Path p = file.getParent(); p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IOException("Event progression directory cannot be a symbolic link");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES)
            throw new IOException("Invalid Event progression journal file");
        return Files.readAllBytes(file);
    }
    private static byte[] digest(byte[] value) throws IOException {
        if (value == null) return null;
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException failure) { throw new IOException(failure); }
    }
    private static void force(Path path, byte[] bytes) throws IOException {
        try (FileChannel out = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) out.write(buffer); out.force(true);
        }
    }
    private static void move(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException failure) { throw new IOException("Atomic Event progression replacement is unavailable", failure); }
    }

    public static String encode(EventProgressState state) {
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", 1); root.addProperty("revision", state.revision());
        JsonArray entries = new JsonArray();
        for (var e : state.entries().values()) {
            JsonObject o = new JsonObject(); o.addProperty("id", e.eventId()); o.addProperty("fingerprint", e.fingerprint());
            o.addProperty("startedAt", e.startedAt()); o.addProperty("triggered", e.triggered());
            o.add("actor", e.actor() == null ? JsonNull.INSTANCE : new JsonPrimitive(e.actor().toString()));
            o.addProperty("source", e.source().name()); o.addProperty("occurrence", e.occurrence());
            o.addProperty("activeOrigin", e.activeOrigin()); o.addProperty("skipped", e.skipped());
            JsonArray facts = new JsonArray(); e.seenFacts().forEach(facts::add); o.add("seenFacts", facts);
            JsonObject versions = new JsonObject(); e.offVersions().forEach(versions::addProperty); o.add("offVersions", versions);
            entries.add(o);
        }
        root.add("entries", entries); return root + "\n";
    }

    public static EventProgressState decode(String source) {
        JsonObject root = BookConfig.strictObject(source);
        PlayJson.fields(root, Set.of("schemaVersion", "revision", "entries"), "Event progression");
        if (PlayJson.integer(root.get("schemaVersion"), "schemaVersion") != 1) throw new IllegalArgumentException("Unsupported Event progression version");
        var entries = new LinkedHashMap<String, EventProgressState.Entry>();
        var array = PlayJson.array(root.get("entries"), "entries");
        if (array.size() > PlayState.MAX_EVENTS) throw new IllegalArgumentException("Too many Event activations");
        for (var value : array) {
            var o = PlayJson.object(value, "activation");
            PlayJson.fields(o, Set.of("id", "fingerprint", "startedAt", "triggered", "actor", "source", "occurrence", "activeOrigin", "seenFacts", "offVersions", "skipped"), "activation");
            var facts = new LinkedHashSet<String>();
            for (var fact : PlayJson.array(o.get("seenFacts"), "source receipts"))
                if (!facts.add(PlayJson.string(fact, "receipt"))) throw new IllegalArgumentException("Duplicate source receipt");
            var versions = new LinkedHashMap<String, Long>();
            PlayJson.object(o.get("offVersions"), "reset versions").entrySet().forEach(e -> versions.put(e.getKey(), PlayJson.number(e.getValue(), "reset version")));
            var e = new EventProgressState.Entry(text(o,"id"),text(o,"fingerprint"),signed(o.get("startedAt")),bool(o,"triggered"),
                    o.get("actor").isJsonNull()?null:PlayJson.uuid(o.get("actor"),"actor"),PlayJson.enumeration(o.get("source"),PlayState.DeliverySource.class,"source"),
                    text(o,"occurrence"),bool(o,"activeOrigin"),facts,versions,bool(o,"skipped"));
            if (entries.put(e.eventId(),e) != null) throw new IllegalArgumentException("Duplicate Event activation");
        }
        return new EventProgressState(PlayJson.number(root.get("revision"), "revision"), entries);
    }
    private static String text(JsonObject o,String key){return PlayJson.string(PlayJson.required(o,key),key);}
    private static boolean bool(JsonObject o,String key){var v=PlayJson.required(o,key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected boolean "+key);return v.getAsBoolean();}
    private static long signed(JsonElement v){if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!v.getAsString().matches("-?(0|[1-9][0-9]*)"))throw new IllegalArgumentException("Expected integer countdown time");try{return Long.parseLong(v.getAsString());}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid countdown time",e);}}
}
