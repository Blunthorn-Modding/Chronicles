package io.github.chronicle.core.managed;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static io.github.chronicle.core.managed.ManagedJsonValues.*;

public final class ManagedBlockLimitStore implements ManagedBlockLimitRepository {
    private static final int SCHEMA = 1;
    private final StrictJsonFile file;
    public ManagedBlockLimitStore(Path path) { file = new StrictJsonFile(path); }
    @Override public synchronized ManagedBlockLimits load() throws IOException { return file.load(ManagedBlockLimits.empty(), ManagedBlockLimitStore::decode); }
    @Override public synchronized void save(ManagedBlockLimits limits) throws IOException {
        if (limits == null) throw new IllegalArgumentException("managed limits are required");
        file.save(encode(limits).toString(), ManagedBlockLimitStore::decode);
    }
    private static ManagedBlockLimits decode(JsonObject root) {
        exact(root, Set.of("schemaVersion", "revision", "overrides"), "managed limits");
        if (integer(required(root, "schemaVersion"), "schemaVersion") != SCHEMA) throw new IllegalArgumentException("unsupported managed limit schema");
        JsonObject values = object(required(root, "overrides"), "overrides");
        if (values.size() > ManagedBlockCatalog.MAX_TYPES) throw new IllegalArgumentException("too many managed limit overrides");
        Map<String, Integer> overrides = new TreeMap<>();
        for (var entry : values.entrySet()) overrides.put(entry.getKey(), integer(entry.getValue(), "limit " + entry.getKey()));
        return new ManagedBlockLimits(longNumber(required(root, "revision"), "revision"), overrides);
    }
    private static JsonObject encode(ManagedBlockLimits limits) {
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", SCHEMA); root.addProperty("revision", limits.revision());
        JsonObject overrides = new JsonObject(); for (var entry : limits.overrides().entrySet()) overrides.addProperty(entry.getKey(), entry.getValue());
        root.add("overrides", overrides); return root;
    }
}
