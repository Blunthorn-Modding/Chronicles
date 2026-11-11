package io.github.chronicle.core.roles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict, bounded assignment persistence; no malformed input is repaired or truncated. */
public final class PlayerRoleAssignmentStore implements PlayerRoleAssignmentRepository {
    private static final long MAX_BYTES = 512 * 1024;
    private final Path file;
    public PlayerRoleAssignmentStore(Path file) { this.file=java.util.Objects.requireNonNull(file).toAbsolutePath().normalize(); }
    @Override public PlayerRoleAssignments load() throws IOException {
        if(!Files.exists(file)) return PlayerRoleAssignments.empty();
        if(!Files.isRegularFile(file)||Files.size(file)>MAX_BYTES)throw new IOException("Player Role assignments are missing, not regular, or exceed 512 KiB: "+file);
        try { JsonObject root=BookConfig.strictObject(Files.readString(file,StandardCharsets.UTF_8)); if(!root.keySet().equals(Set.of("revision","assigned","started","migratedExisting"))&&!root.keySet().equals(Set.of("revision","assigned","started","migratedExisting","startingDisabled","respawnDisabled")))throw new IllegalArgumentException("root fields do not match schema");
            long revision=integer(root.get("revision"),"revision"); if(revision<0)throw new IllegalArgumentException("revision must not be negative");
            Map<UUID,String> assigned=new LinkedHashMap<>();for(JsonElement e:array(root.get("assigned"),"assigned")){JsonObject o=object(e,"assigned entry");exact(o,Set.of("player","role"),"assigned entry");UUID id=uuid(o.get("player"),"assigned player");if(assigned.putIfAbsent(id,string(o.get("role"),"assigned role"))!=null)throw new IllegalArgumentException("duplicate assigned UUID");}
            Set<UUID> started=new LinkedHashSet<>();for(JsonElement e:array(root.get("started"),"started")){UUID id=uuid(e,"started player");if(!started.add(id))throw new IllegalArgumentException("duplicate started UUID");}
            Set<UUID> startingDisabled=root.has("startingDisabled")?uuids(array(root.get("startingDisabled"),"startingDisabled"),"startingDisabled"):Set.of();
            Set<UUID> respawnDisabled=root.has("respawnDisabled")?uuids(array(root.get("respawnDisabled"),"respawnDisabled"),"respawnDisabled"):Set.of();
            return new PlayerRoleAssignments(revision,assigned,started,bool(root.get("migratedExisting"),"migratedExisting"),startingDisabled,respawnDisabled);
        } catch(RuntimeException e){throw new IOException("Player Role assignments are invalid and were not changed: "+file,e);}
    }
    @Override public void save(PlayerRoleAssignments state) throws IOException {
        JsonObject root=new JsonObject();root.addProperty("revision",state.revision());JsonArray a=new JsonArray();state.assigned().forEach((id,role)->{JsonObject x=new JsonObject();x.addProperty("player",id.toString());x.addProperty("role",role);a.add(x);});root.add("assigned",a);JsonArray marks=new JsonArray();state.started().forEach(id->marks.add(id.toString()));root.add("started",marks);root.addProperty("migratedExisting",state.migratedExisting());root.add("startingDisabled",uuidArray(state.startingDisabled()));root.add("respawnDisabled",uuidArray(state.respawnDisabled()));
        byte[] bytes=root.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("Player Role assignments exceed 512 KiB");Path parent=file.getParent();if(parent==null)throw new IOException("Player Role assignment path has no parent");Files.createDirectories(parent);Path tmp=Files.createTempFile(parent,file.getFileName().toString(),".tmp");try{force(tmp,bytes);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){throw new IOException("atomic replacement is not supported for Player Role assignments",e);}}finally{Files.deleteIfExists(tmp);}
    }
    private static void force(Path target,byte[] bytes)throws IOException{try(FileChannel c=FileChannel.open(target,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())c.write(b);c.force(true);}}
    private static JsonObject object(JsonElement e,String f){if(e==null||!e.isJsonObject())throw new IllegalArgumentException(f+" must be an object");return e.getAsJsonObject();}
    private static JsonArray array(JsonElement e,String f){if(e==null||!e.isJsonArray())throw new IllegalArgumentException(f+" must be an array");return e.getAsJsonArray();}
    private static JsonArray uuidArray(Set<UUID> values){JsonArray result=new JsonArray();values.forEach(id->result.add(id.toString()));return result;}
    private static Set<UUID> uuids(JsonArray values,String field){Set<UUID> result=new LinkedHashSet<>();for(JsonElement value:values){UUID id=uuid(value,field);if(!result.add(id))throw new IllegalArgumentException("duplicate "+field+" UUID");}return result;}
    private static void exact(JsonObject o,Set<String> names,String f){if(!o.keySet().equals(names))throw new IllegalArgumentException(f+" fields do not match schema");}
    private static String string(JsonElement e,String f){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException(f+" must be string");return e.getAsString();}
    private static UUID uuid(JsonElement e,String f){try{return UUID.fromString(string(e,f));}catch(IllegalArgumentException x){throw new IllegalArgumentException(f+" must be UUID",x);}}
    private static long integer(JsonElement e,String f){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("-?(0|[1-9][0-9]*)"))throw new IllegalArgumentException(f+" must be integer");try{return Long.parseLong(e.getAsString());}catch(NumberFormatException x){throw new IllegalArgumentException(f+" outside range",x);}}
    private static boolean bool(JsonElement e,String f){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException(f+" must be boolean");return e.getAsBoolean();}
}
