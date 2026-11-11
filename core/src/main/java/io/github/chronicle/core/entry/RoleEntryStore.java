package io.github.chronicle.core.entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.content.InventorySnapshot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict, bounded, atomic role-entry journal. Invalid evidence is never replaced. */
public final class RoleEntryStore implements RoleEntryRepository {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private final Path file;
    private Boolean observedPresent;
    private byte[] observedFingerprint;

    public RoleEntryStore(Path file) {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("role-entry file is required");
        this.file = file.toAbsolutePath().normalize();
    }

    @Override public synchronized RoleEntryState load() throws IOException {
        if (!Files.exists(file)) {
            observedPresent = false; observedFingerprint = null; return RoleEntryState.empty();
        }
        byte[] bytes = currentBytes();
        try {
            RoleEntryState state = decode(new String(bytes, StandardCharsets.UTF_8));
            observedPresent = true; observedFingerprint = fingerprint(bytes); return state;
        } catch (RuntimeException exception) {
            throw new IOException("role-entry data is invalid and was not replaced: " + file, exception);
        }
    }

    @Override public synchronized void save(RoleEntryState state) throws IOException {
        if (state == null) throw new IllegalArgumentException("role-entry state is required");
        Path parent = file.getParent();
        if (parent == null) throw new IOException("role-entry path has no parent");
        Files.createDirectories(parent);
        boolean present = Files.exists(file);
        if (observedPresent != null && observedPresent != present) throw new IOException("role-entry file presence changed outside this store; refusing overwrite");
        byte[] old = null;
        if (present) {
            old = currentBytes();
            if (observedFingerprint != null && !MessageDigest.isEqual(observedFingerprint, fingerprint(old))) {
                throw new IOException("role-entry file changed outside this store; refusing overwrite");
            }
            try { decode(new String(old, StandardCharsets.UTF_8)); }
            catch (RuntimeException exception) { throw new IOException("role-entry data became invalid; refusing overwrite", exception); }
        }
        byte[] next = encode(state).getBytes(StandardCharsets.UTF_8);
        if (next.length > MAX_FILE_BYTES) throw new IOException("role-entry data exceeds 64 MiB");
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        Path backupTemporary = null;
        try {
            forceWrite(temporary, next);
            if (old != null) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                backupTemporary = Files.createTempFile(parent, backup.getFileName().toString(), ".tmp");
                forceWrite(backupTemporary, old); move(backupTemporary, backup); backupTemporary = null;
            }
            move(temporary, file);
            observedPresent = true; observedFingerprint = fingerprint(next);
        } finally {
            delete(temporary); delete(backupTemporary);
        }
    }

    static String encode(RoleEntryState state) {
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", SCHEMA_VERSION); root.addProperty("revision", state.revision());
        JsonArray operations = new JsonArray();
        state.operations().values().forEach(operation -> operations.add(encode(operation)));
        root.add("operations", operations); return root.toString();
    }

    static RoleEntryState decode(String source) {
        JsonObject root = BookConfig.strictObject(source); exact(root, Set.of("schemaVersion", "revision", "operations"), "root");
        if (integer(required(root, "schemaVersion"), "schemaVersion") != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported role-entry schema version");
        long revision = number(required(root, "revision"), "revision");
        JsonArray values = array(required(root, "operations"), "operations");
        if (values.size() > RoleEntryState.MAX_OPERATIONS) throw new IllegalArgumentException("too many role-entry operations");
        Map<UUID, RoleEntryOperation> operations = new LinkedHashMap<>();
        for (JsonElement value : values) {
            RoleEntryOperation operation = operation(object(value, "operation"));
            if (operations.putIfAbsent(operation.playerId(), operation) != null) throw new IllegalArgumentException("duplicate role-entry player");
        }
        return new RoleEntryState(revision, operations);
    }

    private static JsonObject encode(RoleEntryOperation value) {
        JsonObject item = new JsonObject();
        item.addProperty("operationId", value.operationId().toString()); item.addProperty("playerId", value.playerId().toString());
        item.addProperty("roleId", value.roleId()); item.addProperty("roleFingerprint", value.roleFingerprint()); item.addProperty("roleContent", value.roleContent());
        item.add("beforeInventory", snapshot(value.beforeInventory())); item.add("afterInventory", snapshot(value.afterInventory()));
        item.add("beforePosition", position(value.beforePosition())); item.add("afterPosition", position(value.afterPosition()));
        item.addProperty("movementPurpose", value.movementPurpose());
        JsonArray books = new JsonArray(); value.books().forEach(book -> { JsonObject grant = new JsonObject(); grant.addProperty("bookId", book.bookId()); grant.addProperty("deliveryToken", book.deliveryToken()); books.add(grant); });
        item.add("books", books); item.addProperty("stage", value.stage().name()); item.addProperty("detail", value.detail()); item.addProperty("createdAt", value.createdAt()); return item;
    }

    private static RoleEntryOperation operation(JsonObject item) {
        exact(item, Set.of("operationId", "playerId", "roleId", "roleFingerprint", "roleContent", "beforeInventory", "afterInventory",
                "beforePosition", "afterPosition", "movementPurpose", "books", "stage", "detail", "createdAt"), "operation");
        JsonArray books = array(required(item, "books"), "books");
        if (books.size() > RoleEntryOperation.MAX_BOOKS) throw new IllegalArgumentException("too many role-entry books");
        List<RoleBookGrant> grants = new ArrayList<>();
        for (JsonElement value : books) { JsonObject grant = object(value, "book grant"); exact(grant, Set.of("bookId", "deliveryToken"), "book grant"); grants.add(new RoleBookGrant(string(required(grant,"bookId"),"bookId"), string(required(grant,"deliveryToken"),"deliveryToken"))); }
        RoleEntryOperation.Stage stage;
        try { stage = RoleEntryOperation.Stage.valueOf(string(required(item, "stage"), "stage")); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("unknown role-entry stage", exception); }
        return new RoleEntryOperation(uuid(required(item,"operationId"),"operationId"), uuid(required(item,"playerId"),"playerId"),
                string(required(item,"roleId"),"roleId"), string(required(item,"roleFingerprint"),"roleFingerprint"), string(required(item,"roleContent"),"roleContent"),
                snapshot(required(item,"beforeInventory")), snapshot(required(item,"afterInventory")),
                position(required(item,"beforePosition")), position(required(item,"afterPosition")),
                string(required(item,"movementPurpose"),"movementPurpose"), grants, stage,
                string(required(item,"detail"),"detail"), string(required(item,"createdAt"),"createdAt"));
    }

    private static JsonObject snapshot(InventorySnapshot value) { JsonObject item = new JsonObject(); item.addProperty("size", value.size()); JsonArray stacks = new JsonArray(); value.stacks().forEach(stacks::add); item.add("stacks", stacks); return item; }
    private static InventorySnapshot snapshot(JsonElement value) { JsonObject item=object(value,"inventory snapshot"); exact(item,Set.of("size","stacks"),"inventory snapshot"); JsonArray values=array(required(item,"stacks"),"stacks"); List<String> stacks=new ArrayList<>(values.size()); values.forEach(stack->stacks.add(string(stack,"stack"))); return new InventorySnapshot(integer(required(item,"size"),"size"),stacks); }
    private static JsonObject position(PlayerPosition value) { JsonObject item=new JsonObject(); item.addProperty("dimension",value.dimension()); item.addProperty("x",Double.toHexString(value.x())); item.addProperty("y",Double.toHexString(value.y())); item.addProperty("z",Double.toHexString(value.z())); item.addProperty("yaw",Float.toHexString(value.yaw())); item.addProperty("pitch",Float.toHexString(value.pitch())); return item; }
    private static PlayerPosition position(JsonElement value) { JsonObject item=object(value,"position"); exact(item,Set.of("dimension","x","y","z","yaw","pitch"),"position"); try{return new PlayerPosition(string(required(item,"dimension"),"dimension"),Double.valueOf(string(required(item,"x"),"x")),Double.valueOf(string(required(item,"y"),"y")),Double.valueOf(string(required(item,"z"),"z")),Float.valueOf(string(required(item,"yaw"),"yaw")),Float.valueOf(string(required(item,"pitch"),"pitch")));}catch(NumberFormatException exception){throw new IllegalArgumentException("position values must be hexadecimal floating-point numbers",exception);} }

    private byte[] currentBytes() throws IOException { if(!Files.isRegularFile(file))throw new IOException("role-entry path is not a regular file: "+file);if(Files.size(file)>MAX_FILE_BYTES)throw new IOException("role-entry file exceeds 64 MiB: "+file);return Files.readAllBytes(file); }
    private static void forceWrite(Path target,byte[] bytes)throws IOException{try(FileChannel channel=FileChannel.open(target,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);} }
    private static void move(Path source,Path target)throws IOException{try{Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException exception){throw new IOException("atomic replacement is not supported for role-entry data: "+target,exception);} }
    private static void delete(Path target){if(target!=null)try{Files.deleteIfExists(target);}catch(IOException ignored){}}
    private static byte[] fingerprint(byte[] bytes)throws IOException{try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException exception){throw new IOException("SHA-256 unavailable",exception);} }
    private static JsonElement required(JsonObject object,String field){if(!object.has(field)||object.get(field).isJsonNull())throw new IllegalArgumentException("missing "+field);return object.get(field);}
    private static JsonObject object(JsonElement value,String field){if(value==null||!value.isJsonObject())throw new IllegalArgumentException(field+" must be an object");return value.getAsJsonObject();}
    private static JsonArray array(JsonElement value,String field){if(value==null||!value.isJsonArray())throw new IllegalArgumentException(field+" must be an array");return value.getAsJsonArray();}
    private static String string(JsonElement value,String field){if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException(field+" must be a string");return value.getAsString();}
    private static long number(JsonElement value,String field){if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!value.getAsString().matches("0|[1-9][0-9]*"))throw new IllegalArgumentException(field+" must be a non-negative integer");try{return Long.parseLong(value.getAsString());}catch(NumberFormatException exception){throw new IllegalArgumentException(field+" is outside long range",exception);} }
    private static int integer(JsonElement value,String field){long result=number(value,field);if(result>Integer.MAX_VALUE)throw new IllegalArgumentException(field+" is outside integer range");return(int)result;}
    private static UUID uuid(JsonElement value,String field){String text=string(value,field);try{UUID id=UUID.fromString(text);if(!id.toString().equals(text))throw new IllegalArgumentException("non-canonical UUID");return id;}catch(RuntimeException exception){throw new IllegalArgumentException(field+" must be a canonical UUID",exception);} }
    private static void exact(JsonObject object,Set<String> fields,String label){if(!object.keySet().equals(fields))throw new IllegalArgumentException(label+" fields do not match schema");}
}
