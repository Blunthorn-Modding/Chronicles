package io.github.chronicle.core.access;

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

/** World-owned Region access assignments and exact authoring-site references. */
public final class RegionAccessStore {
    private static final byte[] INITIALIZED = "chronicle-region-access-v1\n".getBytes(StandardCharsets.UTF_8);
    private static final int MAX_BINDINGS = 1024;
    private final AtomicDataFile data;
    private final AtomicDataFile marker;
    private boolean initialized;

    public record Binding(String regionId, long regionRevision, String policyId,
                          String outsidePointId, long outsidePointRevision) {
        public Binding {
            if (regionId == null || !AuthoringSite.ID.matcher(regionId).matches() || regionRevision < 1)
                throw new IllegalArgumentException("Invalid Region reference");
            if (policyId == null || policyId.length() > 160 || !AuthoringSite.NAMESPACED_ID.matcher(policyId).matches())
                throw new IllegalArgumentException("Invalid Region access policy ID");
            if (outsidePointId == null || (outsidePointId.isEmpty() ? outsidePointRevision != 0
                    : !AuthoringSite.ID.matcher(outsidePointId).matches() || outsidePointRevision < 1))
                throw new IllegalArgumentException("Invalid outside Point reference");
        }
    }

    public RegionAccessStore(Path directory) {
        data = new AtomicDataFile(directory.resolve("region-access.json"), 512 * 1024);
        marker = new AtomicDataFile(directory.resolve("region-access.initialized"), 128);
    }

    public Map<String, Binding> load() throws IOException {
        byte[] observedMarker = marker.load();
        if (observedMarker != null && !Arrays.equals(observedMarker, INITIALIZED))
            throw new IOException("Region access initialized marker is invalid");
        byte[] bytes = data.load();
        if (bytes == null) {
            if (observedMarker != null) throw new IOException("Initialized Region access catalog is missing");
            initialized = false;
            return Map.of();
        }
        var bindings = decode(bytes);
        if (observedMarker == null) marker.save(INITIALIZED);
        initialized = true;
        return bindings;
    }

    public void save(Map<String, Binding> bindings) throws IOException {
        byte[] bytes = encode(bindings);
        verifyUnchanged();
        // Publish the witness first. Interruption during first assignment can never look like a fresh empty world.
        if (!initialized) {
            marker.save(INITIALIZED);
            initialized = true;
        }
        data.save(bytes);
    }

    public void verifyUnchanged() throws IOException {
        marker.verifyUnchanged();
        data.verifyUnchanged();
    }

    private static byte[] encode(Map<String, Binding> bindings) throws IOException {
        if (bindings.size() > MAX_BINDINGS) throw new IOException("Too many guarded Regions");
        var output = new StringWriter();
        try (var writer = new JsonWriter(output)) {
            writer.setIndent("  ");
            writer.beginObject().name("schemaVersion").value(1).name("bindings").beginArray();
            for (var entry : new TreeMap<>(bindings).entrySet()) {
                Binding binding = entry.getValue();
                if (!entry.getKey().equals(binding.regionId())) throw new IOException("Region access key mismatch");
                writer.beginObject().name("regionId").value(binding.regionId())
                        .name("regionRevision").value(binding.regionRevision())
                        .name("policyId").value(binding.policyId())
                        .name("outsidePointId").value(binding.outsidePointId())
                        .name("outsidePointRevision").value(binding.outsidePointRevision()).endObject();
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
                    case "schemaVersion" -> {
                        if (number(reader) != 1) throw new IOException("Unsupported Region access schema");
                    }
                    case "bindings" -> {
                        reader.beginArray();
                        while (reader.hasNext()) {
                            Binding binding = readBinding(reader);
                            if (bindings.size() == MAX_BINDINGS || bindings.putIfAbsent(binding.regionId(), binding) != null)
                                throw new IOException("Duplicate Region or too many guarded Regions");
                        }
                        reader.endArray();
                    }
                    default -> throw new IOException("Unknown Region access field: " + field);
                }
            }
            reader.endObject();
            if (fields.size() != 2 || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IOException("Incomplete Region access catalog or trailing content");
            return Map.copyOf(bindings);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new IOException("Invalid Region access catalog", failure);
        }
    }

    private static Binding readBinding(JsonReader reader) throws IOException {
        String region = null, policy = null, point = null;
        long regionRevision = -1, pointRevision = -1;
        var fields = new HashSet<String>();
        reader.beginObject();
        while (reader.hasNext()) {
            String field = unique(reader, fields);
            switch (field) {
                case "regionId" -> region = string(reader);
                case "regionRevision" -> regionRevision = number(reader);
                case "policyId" -> policy = string(reader);
                case "outsidePointId" -> point = string(reader);
                case "outsidePointRevision" -> pointRevision = number(reader);
                default -> throw new IOException("Unknown Region access binding field: " + field);
            }
        }
        reader.endObject();
        return new Binding(region, regionRevision, policy, point, pointRevision);
    }

    private static String unique(JsonReader reader, Set<String> fields) throws IOException {
        String name = reader.nextName();
        if (!fields.add(name)) throw new IOException("Duplicate Region access field: " + name);
        return name;
    }

    private static String string(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.STRING) throw new IOException("Expected Region access string");
        return reader.nextString();
    }

    private static long number(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) throw new IOException("Expected Region access integer");
        return Long.parseLong(reader.nextString());
    }
}
