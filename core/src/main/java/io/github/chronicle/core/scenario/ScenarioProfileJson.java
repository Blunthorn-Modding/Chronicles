package io.github.chronicle.core.scenario;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Typed envelope: a server profile is deliberately distinct from the old mod-build content input. */
public final class ScenarioProfileJson {
    public static final int MAX_BYTES=ScenarioContentJson.MAX_BYTES+4096;
    private static final String TYPE="chronicle:scenario_profile";
    private ScenarioProfileJson(){}
    public static String encode(ScenarioProfile profile){
        JsonObject root=new JsonObject();root.addProperty("type",TYPE);root.addProperty("schema",1);root.addProperty("identity",profile.identity().toString());root.addProperty("title",profile.title());
        JsonArray providers=new JsonArray();profile.mechanics().forEach(providers::add);root.add("mechanics",providers);root.add("content",JsonParser.parseString(ScenarioContentJson.encode(profile.content())));
        String result=new GsonBuilder().setPrettyPrinting().create().toJson(root)+"\n";bounded(result);return result;
    }
    public static ScenarioProfile decode(String source){
        bounded(source);validate(source);JsonObject root=JsonParser.parseString(source).getAsJsonObject();
        if(!root.keySet().equals(Set.of("type","schema","identity","title","mechanics","content"))||!TYPE.equals(string(root,"type"))||!root.get("schema").isJsonPrimitive()||!root.get("schema").getAsJsonPrimitive().isNumber()||!root.get("schema").toString().equals("1"))throw new IllegalArgumentException("Unsupported scenario profile. Use a profile exported by Chronicle.");
        String identity=string(root,"identity");UUID uuid=UUID.fromString(identity);if(!uuid.toString().equals(identity))throw new IllegalArgumentException("Invalid scenario profile identity.");
        if(!root.get("mechanics").isJsonArray()||root.getAsJsonArray("mechanics").size()>16||!root.get("content").isJsonObject())throw new IllegalArgumentException("Invalid scenario profile content.");
        Set<String> providers=new TreeSet<>();for(JsonElement value:root.getAsJsonArray("mechanics")){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||!providers.add(value.getAsString()))throw new IllegalArgumentException("Duplicate or invalid mechanics provider.");}
        return new ScenarioProfile(uuid,string(root,"title"),providers,ScenarioContentJson.decode(root.get("content").toString()));
    }
    private static String string(JsonObject root,String key){var value=root.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Invalid profile "+key);return value.getAsString();}
    private static void bounded(String source){if(source==null||source.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Scenario profile exceeds its bounded size.");}
    private static void validate(String source){try(JsonReader reader=new JsonReader(new StringReader(source))){reader.setStrictness(Strictness.STRICT);value(reader,0);if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing profile data.");}catch(IOException error){throw new IllegalArgumentException("Invalid profile JSON.",error);}}
    private static void value(JsonReader reader,int depth)throws IOException{
        if(depth>40)throw new IllegalArgumentException("Profile nesting exceeds 40.");
        switch(reader.peek()){
            case BEGIN_OBJECT->{reader.beginObject();Set<String> keys=new HashSet<>();while(reader.hasNext()){if(!keys.add(reader.nextName()))throw new IllegalArgumentException("Duplicate profile field.");value(reader,depth+1);}reader.endObject();}
            case BEGIN_ARRAY->{reader.beginArray();while(reader.hasNext())value(reader,depth+1);reader.endArray();}
            case STRING,NUMBER->reader.nextString();case BOOLEAN->reader.nextBoolean();case NULL->reader.nextNull();default->throw new IllegalArgumentException("Invalid profile JSON token.");
        }
    }
}
