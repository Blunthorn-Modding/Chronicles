package io.github.chronicle.core.voice;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.persistence.AtomicDataFile;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** World-owned references to exact authored Region revisions. */
public final class VoiceRoomStore {
    private static final byte[] INITIALIZED = "chronicle-voice-rooms-v1\n".getBytes(StandardCharsets.UTF_8);
    private static final int MAX_BINDINGS = 1024;
    private final AtomicDataFile data;
    private final AtomicDataFile marker;
    private boolean initialized;
    private boolean ready;

    public record Binding(String regionId, long regionRevision) {
        public Binding {
            if (regionId == null || !AuthoringSite.ID.matcher(regionId).matches() || regionRevision < 1)
                throw new IllegalArgumentException("Invalid Region reference");
        }
    }

    public VoiceRoomStore(Path worldChronicleDirectory) {
        data = new AtomicDataFile(worldChronicleDirectory.resolve("voice-rooms.json"), 512 * 1024);
        marker = new AtomicDataFile(worldChronicleDirectory.resolve("voice-rooms.initialized"), 128);
    }

    public Map<String, Binding> load() throws IOException {
        ready = false;
        byte[] observedMarker = marker.load();
        if (observedMarker != null && !Arrays.equals(observedMarker, INITIALIZED))
            throw new IOException("Voice room initialized marker is invalid");
        byte[] bytes = data.load();
        if (bytes == null) {
            if (observedMarker != null) throw new IOException("Initialized voice room catalog is missing");
            initialized = false;
            ready = true;
            return Map.of();
        }
        if (observedMarker == null) throw new IOException("Voice room initialized marker is missing");
        Map<String, Binding> bindings = decode(bytes);
        initialized = true;
        ready = true;
        return bindings;
    }

    public void save(Map<String, Binding> bindings) throws IOException {
        byte[] bytes = encode(bindings);
        verifyUnchanged();
        // Fail closed after any uncertain publication until the files are reloaded.
        ready = false;
        if (!initialized) {
            marker.save(INITIALIZED);
            initialized = true;
        }
        data.save(bytes);
        ready = true;
    }

    public void verifyUnchanged() throws IOException {
        if (!ready) throw new IOException("Load the current voice rooms before saving or verifying");
        marker.verifyUnchanged();
        data.verifyUnchanged();
    }

    private static byte[] encode(Map<String, Binding> bindings) throws IOException {
        if (bindings == null || bindings.size() > MAX_BINDINGS) throw new IOException("Invalid or too many voice rooms");
        var output = new StringWriter();
        try (var writer = new JsonWriter(output)) {
            writer.setIndent("  ");
            writer.beginObject().name("schemaVersion").value(1).name("bindings").beginArray();
            try {
                for (var entry : new TreeMap<>(bindings).entrySet()) {
                    Binding binding = entry.getValue();
                    if (binding == null || !entry.getKey().equals(binding.regionId())) throw new IOException("Voice room key mismatch");
                    writer.beginObject().name("regionId").value(binding.regionId())
                            .name("regionRevision").value(binding.regionRevision()).endObject();
                }
            } catch (NullPointerException failure) {
                throw new IOException("Invalid voice room binding", failure);
            }
            writer.endArray().endObject();
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, Binding> decode(byte[] bytes) throws IOException {
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            var fields = new HashSet<String>();
            var bindings = new LinkedHashMap<String, Binding>();
            reader.beginObject();
            while (reader.hasNext()) {
                String field = unique(reader, fields);
                switch (field) {
                    case "schemaVersion" -> { if (number(reader) != 1) throw new IOException("Unsupported voice room schema"); }
                    case "bindings" -> {
                        reader.beginArray();
                        while (reader.hasNext()) {
                            Binding binding = readBinding(reader);
                            if (bindings.size() == MAX_BINDINGS || bindings.putIfAbsent(binding.regionId(), binding) != null)
                                throw new IOException("Duplicate Region or too many voice rooms");
                        }
                        reader.endArray();
                    }
                    default -> throw new IOException("Unknown voice room field: " + field);
                }
            }
            reader.endObject();
            if (fields.size() != 2 || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IOException("Incomplete voice room catalog or trailing content");
            return Map.copyOf(bindings);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new IOException("Invalid voice room catalog", failure);
        }
    }

    private static Binding readBinding(JsonReader reader) throws IOException {
        String region = null;
        long revision = -1;
        var fields = new HashSet<String>();
        reader.beginObject();
        while (reader.hasNext()) {
            String field = unique(reader, fields);
            switch (field) {
                case "regionId" -> {
                    if (reader.peek() != JsonToken.STRING) throw new IOException("Expected Region ID string");
                    region = reader.nextString();
                }
                case "regionRevision" -> revision = number(reader);
                default -> throw new IOException("Unknown voice room binding field: " + field);
            }
        }
        reader.endObject();
        return new Binding(region, revision);
    }

    private static String unique(JsonReader reader, Set<String> fields) throws IOException {
        String name = reader.nextName();
        if (!fields.add(name)) throw new IOException("Duplicate voice room field: " + name);
        return name;
    }

    private static long number(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) throw new IOException("Expected voice room integer");
        return Long.parseLong(reader.nextString());
    }
}
