package io.github.chronicle.core.eventpool;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.core.play.EventJson;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;

/** Strict bounded journal, atomically replaced through Core's observed-byte store. */
public final class EventPoolStore implements EventPoolService.Storage {
    public static final int MAX_BYTES=8*1024*1024;
    private final AtomicDataFile file;
    public EventPoolStore(Path path){file=new AtomicDataFile(path,MAX_BYTES);}
    public byte[] load()throws IOException{return file.load();}
    public void save(byte[] bytes)throws IOException{decode(bytes);file.save(bytes);}
    public void verifyUnchanged()throws IOException{file.verifyUnchanged();}
    public static byte[] encode(Map<String,EventPoolDraw> draws)throws IOException {
        JsonObject root=new JsonObject();root.addProperty("schema",1);JsonArray all=new JsonArray();
        draws.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
            EventPoolDraw d=e.getValue();if(!e.getKey().equals(d.key()))throw new IllegalArgumentException("Draw key mismatch");
            JsonObject item=new JsonObject();item.addProperty("key",d.key());item.addProperty("poolId",d.poolId());item.addProperty("poolFingerprint",d.poolFingerprint());item.addProperty("operationId",d.operationId().toString());
            item.add("configuration",EventJson.encodeConfiguration(d.configuration()));item.addProperty("session",d.session());item.add("actor",d.actor()==null?JsonNull.INSTANCE:new JsonPrimitive(d.actor().toString()));
            JsonArray recipients=new JsonArray();d.recipients().forEach(id->recipients.add(id.toString()));item.add("recipients",recipients);all.add(item);
        });root.add("draws",all);byte[] bytes=(root+"\n").getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new IOException("Pool journal exceeds bounds");
        if(!decode(bytes).equals(draws))throw new IOException("Pool journal serialization mismatch");return bytes;
    }
    public static Map<String,EventPoolDraw> decode(byte[] bytes)throws IOException {
        if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw new IOException("Pool journal outside bounds");
        try {
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root=BookConfig.strictObject(text);exact(root,"schema","draws");if(integer(root.get("schema"))!=1)throw new IllegalArgumentException("Unsupported schema");
            JsonArray all=array(root.get("draws"));if(all.size()>256)throw new IllegalArgumentException("Too many draws");
            Map<String,EventPoolDraw> result=new LinkedHashMap<>();Set<String> events=new HashSet<>();Set<UUID> operations=new HashSet<>();
            for(JsonElement value:all){JsonObject item=object(value);exact(item,"key","poolId","poolFingerprint","operationId","configuration","session","actor","recipients");
                var recipients=new ArrayList<UUID>();for(JsonElement entry:array(item.get("recipients")))recipients.add(uuid(entry));
                EventPoolDraw draw=new EventPoolDraw(string(item.get("key")),string(item.get("poolId")),string(item.get("poolFingerprint")),uuid(item.get("operationId")),EventJson.decodeConfiguration(object(item.get("configuration"))),integer(item.get("session")),item.get("actor").isJsonNull()?null:uuid(item.get("actor")),recipients);
                if(result.putIfAbsent(draw.key(),draw)!=null||!events.add(draw.configuration().definition().id())||!operations.add(draw.operationId()))throw new IllegalArgumentException("Duplicate pool draw authority");
            }return Map.copyOf(result);
        }catch(CharacterCodingException|RuntimeException invalid){throw new IOException("Invalid pool journal; preserved",invalid);}
    }
    private static void exact(JsonObject object,String... fields){if(!object.keySet().equals(Set.of(fields)))throw new IllegalArgumentException("Unexpected fields");}
    private static JsonObject object(JsonElement e){if(e==null||!e.isJsonObject())throw new IllegalArgumentException("Expected object");return e.getAsJsonObject();}
    private static JsonArray array(JsonElement e){if(e==null||!e.isJsonArray())throw new IllegalArgumentException("Expected array");return e.getAsJsonArray();}
    private static String string(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected string");return e.getAsString();}
    private static int integer(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("Expected integer");return Integer.parseInt(e.getAsString());}
    private static UUID uuid(JsonElement e){String s=string(e);UUID result=UUID.fromString(s);if(!result.toString().equals(s))throw new IllegalArgumentException("Invalid UUID");return result;}
}
