package io.github.chronicle.lastcouncil.content;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;

/** Trusted world state; missing file is null. Invalid files remain untouched. */
public final class HouseCastStore {
    private final AtomicDataFile file;
    private boolean initialized;
    public HouseCastStore(Path path) { file = new AtomicDataFile(path, 2 * 1024 * 1024); }
    public HouseCast load() throws IOException {
        initialized = false;
        byte[] bytes = file.load();
        HouseCast cast = bytes == null ? null : decode(bytes);
        initialized = true;
        return cast;
    }
    public void save(HouseCast cast) throws IOException {
        if (!initialized) throw new IOException("cast must be successfully loaded before save");
        byte[] bytes = encode(Objects.requireNonNull(cast));
        if (!decode(bytes).equals(cast)) throw new IOException("cast serialization failed validation");
        file.save(bytes);
    }
    static byte[] encode(HouseCast cast) {
        JsonObject root = new JsonObject(); root.addProperty("schema",cast.usedSecretIds()==null?1:2); root.addProperty("castId", cast.castId().toString()); root.addProperty("rosterRevision",cast.rosterRevision()); root.addProperty("seed",cast.seed());
        if(cast.usedSecretIds()!=null)root.add("usedSecretIds",strings(cast.usedSecretIds().stream().sorted().toList()));
        JsonObject papers = new JsonObject();
        cast.papers().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            var p = e.getValue(); JsonObject o = new JsonObject(); o.addProperty("houseId",p.houseId()); o.add("secrets", strings(p.secrets())); o.add("goals", strings(p.goals()));
            JsonArray known = new JsonArray(); for (var k : p.knownSecrets()) { JsonObject item = new JsonObject(); item.addProperty("targetHouseId",k.targetHouseId()); item.addProperty("body",k.body()); known.add(item); }
            o.add("knownSecrets",known); papers.add(e.getKey().toString(),o);
        }); root.add("papers",papers); return root.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static JsonArray strings(List<String> list) { JsonArray a = new JsonArray(); list.forEach(a::add); return a; }
    static HouseCast decode(byte[] bytes) throws IOException {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root = BookConfig.strictObject(text);long schema=number(root.get("schema"));
            if(schema==1)exact(root,"schema","castId","rosterRevision","seed","papers");
            else if(schema==2)exact(root,"schema","castId","rosterRevision","seed","papers","usedSecretIds");
            else throw new IllegalArgumentException("unsupported schema");
            Set<String> used=null;if(schema==2){var ids=strings(root.get("usedSecretIds"),256);used=new HashSet<>(ids);if(used.size()!=ids.size())throw new IllegalArgumentException("duplicate used secret ID");}
            JsonObject all = object(root.get("papers")); if (all.size() > 32) throw new IllegalArgumentException("too many papers");
            Map<UUID,HouseCast.HousePapers> papers = new LinkedHashMap<>();
            for (var e : all.entrySet()) {
                UUID owner = uuid(e.getKey()); JsonObject p = object(e.getValue()); exact(p,"houseId","secrets","goals","knownSecrets");
                List<String> secrets = strings(p.get("secrets"),4), goals = strings(p.get("goals"),4);
                JsonArray known = array(p.get("knownSecrets")); if (known.size() > 124) throw new IllegalArgumentException("too much knowledge");
                List<HouseCast.KnownSecret> knowledge = new ArrayList<>();
                for (JsonElement v : known) { JsonObject k = object(v); exact(k,"targetHouseId","body"); knowledge.add(new HouseCast.KnownSecret(string(k.get("targetHouseId")),string(k.get("body")))); }
                if (papers.putIfAbsent(owner,new HouseCast.HousePapers(string(p.get("houseId")),secrets,goals,knowledge)) != null) throw new IllegalArgumentException("duplicate owner");
            }
            return new HouseCast(uuid(string(root.get("castId"))),number(root.get("rosterRevision")),number(root.get("seed")),papers,used);
        } catch (CharacterCodingException | RuntimeException e) { throw new IOException("invalid committed cast; file preserved",e); }
    }
    private static List<String> strings(JsonElement e,int max) { JsonArray a=array(e); if(a.size()>max)throw new IllegalArgumentException("too many strings"); List<String> result=new ArrayList<>(); for(JsonElement item:a)result.add(string(item)); return result; }
    private static JsonObject object(JsonElement e) { if(e==null||!e.isJsonObject())throw new IllegalArgumentException("expected object"); return e.getAsJsonObject(); }
    private static JsonArray array(JsonElement e) { if(e==null||!e.isJsonArray())throw new IllegalArgumentException("expected array"); return e.getAsJsonArray(); }
    private static String string(JsonElement e) { if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("expected string"); return e.getAsString(); }
    private static long number(JsonElement e) { if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("-?(0|[1-9][0-9]*)"))throw new IllegalArgumentException("expected integer"); return Long.parseLong(e.getAsString()); }
    private static UUID uuid(String s) { UUID id=UUID.fromString(s); if(!id.toString().equals(s))throw new IllegalArgumentException("noncanonical UUID"); return id; }
    private static void exact(JsonObject o,String... fields) { if(!o.keySet().equals(Set.of(fields)))throw new IllegalArgumentException("incorrect fields"); }
}
