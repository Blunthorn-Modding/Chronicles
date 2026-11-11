package io.github.chronicle.lastcouncil.content;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.lastcouncil.network.CataloguePayloads.Category;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure source-document manipulation; the strict content codec is the validation boundary. */
public final class CouncilContentEditor {
    private CouncilContentEditor() {}
    private static JsonObject root(CouncilContent c)throws IOException{return JsonParser.parseString(new String(CouncilContentJson.encode(c),StandardCharsets.UTF_8)).getAsJsonObject();}
    private static String field(Category c){return switch(c){case HOUSES->"houses";case SECRETS->"secrets";case GOALS->"goals";case KNOWLEDGE->"";};}
    public static List<String> ids(CouncilContent c,Category cat){return switch(cat){case HOUSES->c.houses().keySet().stream().sorted().toList();case SECRETS->c.secrets().stream().map(CouncilContent.SecretTemplate::id).sorted().toList();case GOALS->c.goals().stream().map(CouncilContent.GoalTemplate::id).sorted().toList();case KNOWLEDGE->List.of("rules");};}
    /** Serialize the catalogue once for a search, rather than once per record. */
    public static Map<String,JsonObject> documents(CouncilContent c,Category cat)throws IOException{
        JsonObject r=root(c);Map<String,JsonObject> result=new HashMap<>();
        if(cat==Category.KNOWLEDGE){JsonObject o=new JsonObject();for(String k:List.of("secretsPerHouse","goalsPerHouse","knowledgeChancePercent"))o.add(k,r.get(k));result.put("rules",o);}
        else if(cat==Category.HOUSES)r.getAsJsonObject(field(cat)).entrySet().forEach(e->result.put(e.getKey(),e.getValue().getAsJsonObject()));
        else for(var e:r.getAsJsonArray(field(cat))){var o=e.getAsJsonObject();if(cat==Category.SECRETS&&!o.has("numbers"))o.add("numbers",new JsonObject());result.put(o.get("id").getAsString(),o);}
        return result;
    }
    public static JsonObject document(CouncilContent c,Category cat,String id)throws IOException{
        var r=root(c);if(cat==Category.KNOWLEDGE){var o=new JsonObject();for(String k:List.of("secretsPerHouse","goalsPerHouse","knowledgeChancePercent"))o.add(k,r.get(k));return o;}
        if(cat==Category.HOUSES){var x=r.getAsJsonObject(field(cat)).get(id);if(x==null)throw new IllegalArgumentException("Record no longer exists.");return x.getAsJsonObject();}
        for(var e:r.getAsJsonArray(field(cat)))if(e.getAsJsonObject().get("id").getAsString().equals(id)){var o=e.getAsJsonObject();if(cat==Category.SECRETS&&!o.has("numbers"))o.add("numbers",new JsonObject());return o;}throw new IllegalArgumentException("Record no longer exists.");
    }
    private static String newHouseId(){return "last_council:house/"+UUID.randomUUID();}
    public static String nextFactKey(Set<String> existing){for(int i=1;i<=129;i++){String key="fact.value"+i;if(!existing.contains(key))return key;}throw new IllegalArgumentException("Too many template values.");}
    public static String nextNumberName(Set<String> existing){for(int i=1;i<=129;i++){String key="value"+i;if(!existing.contains(key))return key;}throw new IllegalArgumentException("Too many template values.");}
    public static JsonObject blank(Category cat){var o=new JsonObject();switch(cat){case HOUSES->{o.addProperty("id",newHouseId());o.addProperty("title","");o.addProperty("reputation","");o.addProperty("description","");o.addProperty("interests","");o.addProperty("defaultCandidate",false);}case SECRETS,GOALS->{o.addProperty("id","");o.addProperty("weight",1);o.addProperty("text","");if(cat==Category.SECRETS){o.add("facts",new JsonObject());o.add("numbers",new JsonObject());}o.add("applicableHouseIds",new JsonArray());}case KNOWLEDGE->throw new IllegalArgumentException("Rules already exist.");}return o;}
    public static CouncilContent save(CouncilContent c,Category cat,String oldId,byte[] bytes)throws IOException{
        String text;try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}catch(java.nio.charset.CharacterCodingException e){throw new IOException("Invalid UTF-8.",e);}JsonObject item=BookConfig.strictObject(text),r=root(c);
        if(cat==Category.HOUSES&&oldId==null&&(!item.has("id")||(item.get("id").isJsonPrimitive()&&item.get("id").getAsJsonPrimitive().isString()&&item.get("id").getAsString().isBlank())))item.addProperty("id",newHouseId());
        if(cat==Category.KNOWLEDGE){if(!item.keySet().equals(Set.of("secretsPerHouse","goalsPerHouse","knowledgeChancePercent")))throw new IllegalArgumentException("Invalid rules.");item.entrySet().forEach(e->r.add(e.getKey(),e.getValue()));}
        else {if(cat==Category.SECRETS||cat==Category.GOALS)validateTemplate(item,cat);if(!item.has("id")||!item.get("id").isJsonPrimitive()||!item.get("id").getAsJsonPrimitive().isString())throw new IllegalArgumentException("An ID is required.");String id=item.get("id").getAsString();if(oldId!=null&&!oldId.equals(id))throw new IllegalArgumentException("IDs cannot change.");if(oldId==null&&ids(c,cat).contains(id))throw new IllegalArgumentException("That ID already exists.");if(oldId!=null&&!ids(c,cat).contains(oldId))throw new IllegalArgumentException("Record no longer exists.");
            if(cat==Category.HOUSES)r.getAsJsonObject(field(cat)).add(id,item);else {JsonArray a=r.getAsJsonArray(field(cat)),out=new JsonArray();for(var e:a)if(oldId==null||!e.getAsJsonObject().get("id").getAsString().equals(oldId))out.add(e);out.add(item);r.add(field(cat),out);}}
        return CouncilContentJson.decode(new GsonBuilder().disableHtmlEscaping().create().toJson(r).getBytes(StandardCharsets.UTF_8));
    }
    private static void validateTemplate(JsonObject item,Category cat){
        if(!item.has("text")||!item.get("text").isJsonPrimitive()||!item.get("text").getAsJsonPrimitive().isString())throw new IllegalArgumentException("Template text is required.");
        String text=item.get("text").getAsString();int pos=0;while((pos=text.indexOf("${",pos))>=0){int end=text.indexOf('}',pos+2);if(end<0)throw new IllegalArgumentException("Unclosed template placeholder.");String key=text.substring(pos+2,end);if(key.matches("number\\.[A-Za-z_][A-Za-z0-9_]*")){if(cat!=Category.SECRETS||!item.has("numbers")||!item.get("numbers").isJsonObject()||!item.getAsJsonObject("numbers").has(key.substring(7)))throw new IllegalArgumentException("Missing number binding: "+key);}
            else if(!key.equals("house.own")&&!key.equals("house.other")&&!key.equals("house.title")&&!key.equals("other.title")&&!key.matches("fact\\.[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*"))throw new IllegalArgumentException("Unsupported template placeholder: "+key);pos=end+1;}
    }
    public static CouncilContent delete(CouncilContent c,Category cat,String id)throws IOException {if(cat==Category.KNOWLEDGE)throw new IllegalArgumentException("Rules cannot be deleted.");if(!ids(c,cat).contains(id))throw new IllegalArgumentException("Record no longer exists.");if(cat==Category.HOUSES&&(c.secrets().stream().anyMatch(s->s.applicableHouseIds().contains(id))||c.goals().stream().anyMatch(g->g.applicableHouseIds().contains(id))))throw new IllegalArgumentException("This House is referenced by a secret or goal.");var r=root(c);if(cat==Category.HOUSES)r.getAsJsonObject(field(cat)).remove(id);else{JsonArray a=new JsonArray();for(var e:r.getAsJsonArray(field(cat)))if(!e.getAsJsonObject().get("id").getAsString().equals(id))a.add(e);r.add(field(cat),a);}return CouncilContentJson.decode(r.toString().getBytes(StandardCharsets.UTF_8));}
}
