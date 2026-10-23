package io.github.chronicle.core.managed;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.authoring.BlockPosition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static io.github.chronicle.core.managed.ManagedJsonValues.*;

public final class ManagedBlockStore implements ManagedBlockLedgerRepository {
    private static final int SCHEMA = 1;
    private final StrictJsonFile file;
    private final Path path;
    private boolean absent;

    public ManagedBlockStore(Path path) { this.path = path.toAbsolutePath().normalize(); this.file = new StrictJsonFile(path); }
    public boolean wasAbsent() { return absent; }

    @Override public synchronized ManagedBlockLedger load() throws IOException {
        absent = !Files.exists(path);
        return file.load(ManagedBlockLedger.unmigrated(), ManagedBlockStore::decode);
    }

    @Override public synchronized void save(ManagedBlockLedger ledger) throws IOException {
        if (ledger == null) throw new IllegalArgumentException("managed ledger is required");
        file.save(encode(ledger).toString(), ManagedBlockStore::decode);
        absent = false;
    }

    private static ManagedBlockLedger decode(JsonObject root) {
        exact(root, Set.of("schemaVersion", "revision", "migrationVersion", "instances"), "managed ledger");
        if (integer(required(root, "schemaVersion"), "schemaVersion") != SCHEMA) throw new IllegalArgumentException("unsupported managed ledger schema");
        long revision = longNumber(required(root, "revision"), "revision");
        int migration = integer(required(root, "migrationVersion"), "migrationVersion");
        JsonArray array = array(required(root, "instances"), "instances");
        if (array.size() > ManagedBlockLedger.MAX_INSTANCES) throw new IllegalArgumentException("too many managed instances");
        List<ManagedBlockInstance> instances = new ArrayList<>(array.size());
        for (var element : array) {
            JsonObject entry = object(element, "instance");
            exact(entry, Set.of("id", "type", "block", "dimension", "position", "registration", "origin", "revision"), "instance");
            BlockPosition position = position(required(entry, "position"), "position");
            instances.add(new ManagedBlockInstance(
                    UUID.fromString(string(required(entry, "id"), "instance id")),
                    string(required(entry, "type"), "instance type"),
                    string(required(entry, "block"), "instance block"),
                    string(required(entry, "dimension"), "instance dimension"), position,
                    ManagedBlockInstance.Registration.valueOf(string(required(entry, "registration"), "registration")),
                    ManagedBlockInstance.Origin.valueOf(string(required(entry, "origin"), "origin")),
                    longNumber(required(entry, "revision"), "instance revision")));
        }
        return new ManagedBlockLedger(revision, migration, instances);
    }

    private static JsonObject encode(ManagedBlockLedger ledger) {
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", SCHEMA); root.addProperty("revision", ledger.revision());
        root.addProperty("migrationVersion", ledger.migrationVersion());
        JsonArray instances = new JsonArray();
        for (ManagedBlockInstance instance : ledger.instances()) {
            JsonObject entry = new JsonObject(); entry.addProperty("id", instance.id().toString()); entry.addProperty("type", instance.typeId());
            entry.addProperty("block", instance.blockId()); entry.addProperty("dimension", instance.dimension()); entry.add("position", position(instance.position()));
            entry.addProperty("registration", instance.registration().name()); entry.addProperty("origin", instance.origin().name()); entry.addProperty("revision", instance.revision());
            instances.add(entry);
        }
        root.add("instances", instances); return root;
    }
}
