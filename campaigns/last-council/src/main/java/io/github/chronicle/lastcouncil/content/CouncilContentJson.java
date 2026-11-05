package io.github.chronicle.lastcouncil.content;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.lastcouncil.house.HouseDefinition;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Strict bounded codec for reusable sample or operator-supplied content. */
public final class CouncilContentJson {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private CouncilContentJson() {}
    public static CouncilContent decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("content size outside bounds");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root = BookConfig.strictObject(text); exact(root,"schema","note","houses","secrets","goals","secretsPerHouse","goalsPerHouse","knowledgeChancePercent");
            if (integer(root.get("schema")) != 1) throw new IllegalArgumentException("unsupported content schema");
            String note = string(root.get("note")); CouncilContent.text(note);
            JsonObject houseObject=object(root.get("houses")); if(houseObject.size()>32)throw new IllegalArgumentException("too many houses");
            Map<String,HouseDefinition> houses=new LinkedHashMap<>();
            for(var entry:houseObject.entrySet()) {
                JsonObject h=object(entry.getValue()); exact(h,"id","title","reputation","description","interests","defaultCandidate");
                var house=new HouseDefinition(string(h.get("id")),string(h.get("title")),string(h.get("reputation")),string(h.get("description")),string(h.get("interests")),bool(h.get("defaultCandidate")));
                houses.put(entry.getKey(),house);
            }
            JsonArray secretArray=array(root.get("secrets")); if(secretArray.size()>256)throw new IllegalArgumentException("too many secrets");
            List<CouncilContent.SecretTemplate> secrets=new ArrayList<>();
            for(JsonElement e:secretArray) {
                JsonObject s=object(e); exact(s,"id","weight","text","facts","applicableHouseIds");
                JsonObject f=object(s.get("facts")); if(f.size()>128)throw new IllegalArgumentException("too many facts");
                Map<String,String> facts=new LinkedHashMap<>(); f.entrySet().forEach(x->facts.put(x.getKey(),string(x.getValue())));
                secrets.add(new CouncilContent.SecretTemplate(string(s.get("id")),integer(s.get("weight")),string(s.get("text")),facts,scope(s.get("applicableHouseIds"))));
            }
            JsonArray goalArray=array(root.get("goals")); if(goalArray.size()>256)throw new IllegalArgumentException("too many goals");
            List<CouncilContent.GoalTemplate> goals=new ArrayList<>();
            for(JsonElement e:goalArray) { JsonObject g=object(e); exact(g,"id","weight","text","applicableHouseIds"); goals.add(new CouncilContent.GoalTemplate(string(g.get("id")),integer(g.get("weight")),string(g.get("text")),scope(g.get("applicableHouseIds")))); }
            return new CouncilContent(houses,secrets,goals,integer(root.get("secretsPerHouse")),integer(root.get("goalsPerHouse")),integer(root.get("knowledgeChancePercent")));
        } catch(CharacterCodingException | RuntimeException e) { throw new IOException("invalid Council content",e); }
    }
    private static Set<String> scope(JsonElement e) { JsonArray a=array(e); if(a.size()>32)throw new IllegalArgumentException("scope too large"); Set<String> result=new LinkedHashSet<>(); for(JsonElement x:a)if(!result.add(string(x)))throw new IllegalArgumentException("duplicate scope"); return result; }
    private static JsonObject object(JsonElement e) { if(e==null||!e.isJsonObject())throw new IllegalArgumentException("expected object"); return e.getAsJsonObject(); }
    private static JsonArray array(JsonElement e) { if(e==null||!e.isJsonArray())throw new IllegalArgumentException("expected array"); return e.getAsJsonArray(); }
    private static String string(JsonElement e) { if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("expected string"); return e.getAsString(); }
    private static boolean bool(JsonElement e) { if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("expected boolean"); return e.getAsBoolean(); }
    private static int integer(JsonElement e) { if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("expected integer"); return Integer.parseInt(e.getAsString()); }
    private static void exact(JsonObject o,String... fields) { if(!o.keySet().equals(Set.of(fields)))throw new IllegalArgumentException("incorrect fields"); }
}
