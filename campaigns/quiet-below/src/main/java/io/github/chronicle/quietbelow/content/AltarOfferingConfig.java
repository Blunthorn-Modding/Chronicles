package io.github.chronicle.quietbelow.content;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.Strictness;
import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import io.github.chronicle.core.server.GameSetupTools;
import io.github.chronicle.core.server.TemporaryOfferings;
import net.minecraft.server.command.ServerCommandSource;
import net.fabricmc.loader.api.FabricLoader;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Strict operator-owned initial balance. Invalid files disable temporary prayers and are never rewritten. */
public record AltarOfferingConfig(Emergency emergency,Repair repair,Sight sight,Harvest harvest,int cooldownSeconds) {
    public AltarOfferingConfig(Emergency emergency,Repair repair,Sight sight,Harvest harvest){this(emergency,repair,sight,harvest,900);}
    public static final String TOOL="quiet_below:altar_offerings";
    public static final AltarOfferingConfig DEFAULT=new AltarOfferingConfig(new Emergency(true,4,4,8),new Repair(true,25,2,1200),new Sight(true,3,25,300),new Harvest(true,16,300,8,200,150));
    private static AltarOfferingConfig current;private static String failure;private static long revision;
    public AltarOfferingConfig{Objects.requireNonNull(emergency);Objects.requireNonNull(repair);Objects.requireNonNull(sight);Objects.requireNonNull(harvest);range(cooldownSeconds,0,86400,"Altar cooldown seconds");}
    public record Emergency(boolean enabled,int donorHealth,int healing,int radius){public Emergency{range(donorHealth,1,40,"emergency health");range(healing,1,40,"emergency healing");range(radius,1,32,"emergency radius");}}
    public record Repair(boolean enabled,int percent,int health,int seconds){public Repair{range(percent,1,100,"repair percent");range(health,1,40,"repair health");range(seconds,1,86_400,"repair seconds");}}
    public record Sight(boolean enabled,int levels,int exhaustionPercent,int seconds){public Sight{range(levels,1,100,"sight levels");range(exhaustionPercent,0,25_500,"sight exhaustion");range(seconds,1,86_400,"sight seconds");}}
    public record Harvest(boolean enabled,int bread,int seconds,int radius,int cropPercent,int growthPercent){public Harvest{range(bread,1,2304,"harvest bread");range(seconds,1,86_400,"harvest seconds");range(radius,1,32,"harvest radius");range(cropPercent,100,400,"crop percent");range(growthPercent,100,200,"growth percent");}}
    public static void register(){
        try{reload();}catch(Exception error){failure=error.getMessage();}
        GameSetupTools.afterRegistrations(AltarOfferingConfig::registerTool);
    }
    private static void registerTool(){
        List<GameSetupTools.Verb> verbs=new ArrayList<>();
        verbs.add(new GameSetupTools.Verb("reload","Reload Altar terms",GameSetupTools.Guard.EDIT,true));
        verbs.add(new GameSetupTools.Verb("refresh","Refresh Altar terms",GameSetupTools.Guard.READ_ONLY,false));
        for(String child:List.of("chronicle_core:offerings",TemporaryOfferings.OWNER)){
            String prefix=child.equals(TemporaryOfferings.OWNER)?"temp_":"lasting_";
            for(var verb:GameSetupTools.get(child).orElseThrow().verbs())verbs.add(new GameSetupTools.Verb(prefix+verb.id(),verb.label(),verb.guard(),verb.confirm()));
        }
        GameSetupTools.register(new GameSetupTools.Tool(TOOL,"Altar offerings","Five Quiet Below prayers. Successful prayers share one per-character cooldown across all Altars (default 900 occupied active seconds), retained through death, reconnecting and restart. Permanent prayers have three uses each. Recovery never replays, refunds or manufactures a result. Player screens remain cryptic.",verbs,AltarOfferingConfig::combinedStatus,(source,verb)->{
            if(verb.startsWith("lasting_"))return GameSetupTools.execute(source,"chronicle_core:offerings",verb.substring(8));
            if(verb.startsWith("temp_"))return GameSetupTools.execute(source,TemporaryOfferings.OWNER,verb.substring(5));
            if(verb.equals("reload")){reload();return "Altar offering terms reloaded; earlier prayers retain their captured terms.";}
            return "Altar offering status refreshed.";
        }));
        GameSetupTools.presentWithin(TOOL,List.of("chronicle_core:offerings",TemporaryOfferings.OWNER));
    }
    public static long revision(){return revision;}
    private static GameSetupTools.Status combinedStatus(ServerCommandSource source){
        var own=status();var facts=new ArrayList<>(own.facts());var disabled=new HashMap<String,String>();long control=own.controlRevision();
        for(String child:List.of("chronicle_core:offerings",TemporaryOfferings.OWNER)){
            String prefix=child.equals(TemporaryOfferings.OWNER)?"temp_":"lasting_";
            var status=GameSetupTools.status(source,child);control=Math.addExact(control,status.controlRevision());
            for(var fact:status.facts())facts.add(new Fact((prefix.equals("temp_")?"Temporary ":"Lasting ")+fact.label(),fact.value()));
            status.disabledReasons().forEach((verb,reason)->disabled.put(prefix+verb,reason));
        }
        return new GameSetupTools.Status(control,facts,disabled);
    }
    public static AltarOfferingConfig get(){if(current==null)throw new IllegalStateException(failure==null?"Temporary Altar configuration is unavailable.":failure);return current;}
    private static GameSetupTools.Status status(){List<Fact> facts=new ArrayList<>();facts.add(new Fact("Status",failure==null?"Ready":failure));if(current!=null){facts.add(new Fact("Sheltering grace",on(current.emergency.enabled)+"; donor "+current.emergency.donorHealth+" HP; shared healing "+current.emergency.healing+" HP; radius "+current.emergency.radius+"."));facts.add(new Fact("Swift mending",on(current.repair.enabled)+"; repair "+current.repair.percent+"%; -"+current.repair.health+" HP for "+current.repair.seconds+" active seconds."));facts.add(new Fact("Clear sight",on(current.sight.enabled)+"; "+current.sight.levels+" XP levels; +"+current.sight.exhaustionPercent+"% exhaustion for "+current.sight.seconds+" active seconds."));facts.add(new Fact("Shared cooldown",current.cooldownSeconds+" occupied active seconds per character across all Altars; only successful prayers start it. Earlier prayers retain captured values."));facts.add(new Fact("Lasting prayers","More life and quicker feet: three uses each. Gameplay refusals deal ordinary 2 HP damage; no trade is paid or granted."));}facts.add(new Fact("File","config/chronicle/quiet-below-altar-offerings.json; existing values win and invalid files are not rewritten."));return new GameSetupTools.Status(revision,facts,Map.of());}
    private static String on(boolean value){return value?"Enabled":"Disabled";}
    private static Path file(){return FabricLoader.getInstance().getConfigDir().resolve("chronicle/quiet-below-altar-offerings.json");}
    private static void reload()throws Exception{try{Path file=file();if(!Files.exists(file)){Files.createDirectories(file.getParent());Path temporary=Files.createTempFile(file.getParent(),file.getFileName().toString(),".tmp");try{Files.writeString(temporary,encode(DEFAULT),StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException error){throw new IllegalStateException("Atomic Altar config creation is unsupported",error);}}finally{Files.deleteIfExists(temporary);}}if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>64*1024)throw new IllegalStateException("Temporary Altar config is not a bounded regular file");install(Files.readString(file));}catch(Exception error){disable(error);throw error;}}
    static void replaceForTest(String source){try{install(source);}catch(RuntimeException error){disable(error);throw error;}}
    private static void install(String source){current=decode(source);failure=null;revision=Math.addExact(revision,1);}
    private static void disable(Exception error){current=null;failure="Temporary Altar configuration is invalid: "+Objects.requireNonNullElse(error.getMessage(),error.getClass().getSimpleName());revision=Math.addExact(revision,1);}
    static String encode(AltarOfferingConfig value){JsonObject root=new JsonObject();root.addProperty("schema",2);root.addProperty("cooldownSeconds",value.cooldownSeconds);root.add("emergency",object(value.emergency.enabled,"donorHealth",value.emergency.donorHealth,"healing",value.emergency.healing,"radius",value.emergency.radius));root.add("repair",object(value.repair.enabled,"percent",value.repair.percent,"health",value.repair.health,"seconds",value.repair.seconds));root.add("sight",object(value.sight.enabled,"levels",value.sight.levels,"exhaustionPercent",value.sight.exhaustionPercent,"seconds",value.sight.seconds));root.add("harvest",object(value.harvest.enabled,"bread",value.harvest.bread,"seconds",value.harvest.seconds,"radius",value.harvest.radius,"cropPercent",value.harvest.cropPercent,"growthPercent",value.harvest.growthPercent));return new GsonBuilder().setPrettyPrinting().create().toJson(root)+"\n";}
    static AltarOfferingConfig decode(String source){JsonObject root=parse(source);int schema=integer(root,"schema");exact(root,schema==1?Set.of("schema","emergency","repair","sight","harvest"):Set.of("schema","cooldownSeconds","emergency","repair","sight","harvest"));if(schema!=1&&schema!=2)throw new IllegalArgumentException("Unsupported temporary Altar config schema");JsonObject e=child(root,"emergency",Set.of("enabled","donorHealth","healing","radius")),r=child(root,"repair",Set.of("enabled","percent","health","seconds")),s=child(root,"sight",Set.of("enabled","levels","exhaustionPercent","seconds")),h=child(root,"harvest",Set.of("enabled","bread","seconds","radius","cropPercent","growthPercent"));return new AltarOfferingConfig(new Emergency(bool(e,"enabled"),integer(e,"donorHealth"),integer(e,"healing"),integer(e,"radius")),new Repair(bool(r,"enabled"),integer(r,"percent"),integer(r,"health"),integer(r,"seconds")),new Sight(bool(s,"enabled"),integer(s,"levels"),integer(s,"exhaustionPercent"),integer(s,"seconds")),new Harvest(bool(h,"enabled"),integer(h,"bread"),integer(h,"seconds"),integer(h,"radius"),integer(h,"cropPercent"),integer(h,"growthPercent")),schema==1?900:integer(root,"cooldownSeconds"));}
    private static JsonObject object(boolean enabled,Object... values){JsonObject object=new JsonObject();object.addProperty("enabled",enabled);for(int i=0;i<values.length;i+=2)object.addProperty((String)values[i],(Integer)values[i+1]);return object;}
    private static JsonObject parse(String source){validate(source);try(JsonReader reader=new JsonReader(new StringReader(source))){reader.setStrictness(Strictness.STRICT);JsonElement value=JsonParser.parseReader(reader);if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("trailing Altar config JSON");if(!value.isJsonObject())throw new IllegalArgumentException("Altar config must be an object");return value.getAsJsonObject();}catch(Exception error){throw new IllegalArgumentException("Invalid temporary Altar config: "+error.getMessage(),error);}}
    private static void validate(String source){try(JsonReader reader=new JsonReader(new StringReader(source))){reader.setStrictness(Strictness.STRICT);validate(reader,0);if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("trailing Altar config JSON");}catch(Exception error){if(error instanceof IllegalArgumentException invalid)throw invalid;throw new IllegalArgumentException("Invalid temporary Altar config: "+error.getMessage(),error);}}
    private static void validate(JsonReader reader,int depth)throws java.io.IOException{if(depth>8)throw new IllegalArgumentException("Altar config JSON nesting exceeds 8");switch(reader.peek()){case BEGIN_OBJECT->{reader.beginObject();Set<String> names=new HashSet<>();while(reader.hasNext()){String name=reader.nextName();if(!names.add(name))throw new IllegalArgumentException("duplicate Altar config member: "+name);validate(reader,depth+1);}reader.endObject();}case BEGIN_ARRAY->{reader.beginArray();while(reader.hasNext())validate(reader,depth+1);reader.endArray();}case STRING,NUMBER->reader.nextString();case BOOLEAN->reader.nextBoolean();case NULL->reader.nextNull();default->throw new IllegalArgumentException("unexpected Altar config JSON token");}}
    private static JsonObject child(JsonObject parent,String key,Set<String> fields){JsonElement value=parent.get(key);if(value==null||!value.isJsonObject())throw new IllegalArgumentException(key+" must be an object");JsonObject child=value.getAsJsonObject();exact(child,fields);return child;}
    private static void exact(JsonObject object,Set<String> fields){if(!object.keySet().equals(fields))throw new IllegalArgumentException("Temporary Altar config fields do not match schema");}
    private static boolean bool(JsonObject object,String key){JsonElement value=object.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException(key+" must be boolean");return value.getAsBoolean();}
    private static int integer(JsonObject object,String key){JsonElement value=object.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!value.getAsString().matches("0|[1-9][0-9]*"))throw new IllegalArgumentException(key+" must be a canonical integer");try{return Integer.parseInt(value.getAsString());}catch(RuntimeException error){throw new IllegalArgumentException(key+" is outside integer range",error);}}
    private static void range(int value,int minimum,int maximum,String name){if(value<minimum||value>maximum)throw new IllegalArgumentException(name+" is outside "+minimum+".."+maximum);}
}
