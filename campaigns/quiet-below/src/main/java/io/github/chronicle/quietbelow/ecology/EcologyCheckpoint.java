package io.github.chronicle.quietbelow.ecology;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static io.github.chronicle.quietbelow.ecology.EcologyEngine.*;

/** Strict, bounded checkpoint pages. The original source index remains independently replayable. */
final class EcologyCheckpoint {
    // Four worst-case 32-property before/after states fit both the 64 KiB line and JSON-node caps.
    private static final int CELL_PAGE=4, AIR_PAGE=256;
    record Decoded(JsonObject header,List<Cell> cells,List<JsonElement> health,List<Pos> air,Claim claim,int end) { }

    static int prefixEnd(List<EcologyJournal.Entry> entries){
        if(entries.isEmpty()||!entries.getFirst().type().equals("CONFIGURE"))throw bad("Missing checkpoint configuration");
        for(int n=1;n<entries.size();n++){
            String type=entries.get(n).type();if(type.equals("INDEX_COMPLETE"))return n;
            if(!type.equals("INDEX"))throw bad("Checkpoint must follow the original source index");
        }
        throw bad("Incomplete source index");
    }
    static void write(EcologyJournal target,EcologyEngine engine,List<Pos> air,String hash,long records)throws IOException {
        List<Cell> cells=engine.cells();JsonObject h=new JsonObject();h.addProperty("format",2);
        h.addProperty("sourceHash",hash);h.addProperty("sourceRecords",records);h.addProperty("cells",cells.size());h.addProperty("air",air.size());
        h.addProperty("enabled",engine.enabled());h.addProperty("cleanup",engine.cleanup());h.addProperty("speed",engine.speedPercent());
        JsonObject crops=new JsonObject();crops.addProperty("enabled",engine.cropRules().enabled());crops.addProperty("seconds",engine.cropRules().seconds());h.add("crops",crops);
        JsonObject recovery=new JsonObject();recovery.addProperty("enabled",engine.recoveryRules().enabled());recovery.addProperty("seconds",engine.recoveryRules().seconds());h.add("recovery",recovery);
        h.add("vitality",engine.vitality().checkpointHeader());h.add("claim",claim(engine.claim()));target.append("CHECKPOINT_BEGIN",h);
        for(int n=0;n<cells.size();n+=CELL_PAGE){
            JsonArray page=new JsonArray();for(int k=n;k<Math.min(cells.size(),n+CELL_PAGE);k++){
                Cell cell=cells.get(k);JsonObject c=new JsonObject();c.add("p",pos(cell.position()));c.add("before",state(cell.before()));c.add("after",state(cell.after()));
                c.addProperty("adopted",cell.adopted());c.add("vitality",engine.vitality().checkpointCell(cell.position()));page.add(c);
            }
            target.append("CHECKPOINT_CELLS",page(n,page));
        }
        for(int n=0;n<air.size();n+=AIR_PAGE){
            JsonArray page=new JsonArray();for(int k=n;k<Math.min(air.size(),n+AIR_PAGE);k++)page.add(pos(air.get(k)));
            target.append("CHECKPOINT_AIR",page(n,page));
        }
        target.append("CHECKPOINT_END",new JsonObject());
    }
    private static JsonObject page(int offset,JsonArray values){JsonObject p=new JsonObject();p.addProperty("offset",offset);p.add("values",values);return p;}
    static Decoded read(List<EcologyJournal.Entry> entries,int begin){
        if(prefixEnd(entries)!=begin-1)throw bad("Checkpoint is not immediately after the source index");
        JsonObject h=entries.get(begin).payload();int format=integer(h,"format");
        if(format==1)keys(h,"format","sourceHash","sourceRecords","cells","air","enabled","cleanup","speed","crops","vitality","claim");
        else if(format==2)keys(h,"format","sourceHash","sourceRecords","cells","air","enabled","cleanup","speed","crops","recovery","vitality","claim");
        else throw bad("Unsupported checkpoint format");
        if(!text(h,"sourceHash").matches("[0-9a-f]{64}")||natural(h,"sourceRecords")<begin)throw bad("Invalid checkpoint provenance");
        int cellCount=integer(h,"cells"),airCount=integer(h,"air");
        if(cellCount<0||cellCount>MAX_CELLS||airCount<0||airCount>MAX_AIR)throw bad("Checkpoint exceeds ecology capacity");
        List<Cell> cells=new ArrayList<>();List<JsonElement> health=new ArrayList<>();List<Pos> air=new ArrayList<>();
        Set<Pos> seenCells=new HashSet<>(),seenAir=new HashSet<>();boolean airStarted=false;
        for(int n=begin+1;n<entries.size();n++){
            var entry=entries.get(n);JsonObject p=entry.payload();String type=entry.type();
            if(type.equals("CHECKPOINT_END")){
                keys(p);if(cells.size()!=cellCount||air.size()!=airCount)throw bad("Incomplete checkpoint pages");
                return new Decoded(h,List.copyOf(cells),List.copyOf(health),List.copyOf(air),claim(h.get("claim")),n);
            }
            keys(p,"offset","values");JsonElement values=p.get("values");if(values==null||!values.isJsonArray())throw bad("Checkpoint page requires an array");
            JsonArray a=values.getAsJsonArray();if(a.isEmpty())throw bad("Empty checkpoint page");
            if(type.equals("CHECKPOINT_CELLS")){
                if(airStarted||integer(p,"offset")!=cells.size()||a.size()>CELL_PAGE||cells.size()+a.size()>cellCount)throw bad("Invalid checkpoint cell page order");
                for(JsonElement item:a){
                    JsonObject c=object(item);keys(c,"p","before","after","adopted","vitality");Pos at=position(c.get("p"));
                    State before=block(c.get("before")),after=block(c.get("after"));boolean adopted=bool(c,"adopted");
                    if(!seenCells.add(at)||!after.network()||before.network()||adopted&&!before.equals(AIR))throw bad("Invalid checkpoint ownership");
                    cells.add(new Cell(at,before,after,adopted));health.add(c.get("vitality"));
                }
            }else if(type.equals("CHECKPOINT_AIR")){
                airStarted=true;if(cells.size()!=cellCount||integer(p,"offset")!=air.size()||a.size()>AIR_PAGE||air.size()+a.size()>airCount)throw bad("Invalid checkpoint frontier page order");
                for(JsonElement item:a){Pos at=position(item);if(!seenAir.add(at))throw bad("Duplicate checkpoint frontier");air.add(at);}
            }else throw bad("Unexpected record inside checkpoint");
        }
        throw bad("Missing checkpoint completion");
    }
    private static JsonElement claim(Claim c){
        if(c==null)return JsonNull.INSTANCE;JsonObject p=new JsonObject();p.addProperty("site",c.site());p.addProperty("revision",c.revision());
        p.add("min",pos(c.area().min()));p.add("max",pos(c.area().max()));p.addProperty("complete",c.complete());JsonArray cursors=new JsonArray();
        for(var e:new TreeMap<>(c.cursors()).entrySet()){JsonArray cursor=new JsonArray();cursor.add((int)(e.getKey()>>32));cursor.add((int)(long)e.getKey());cursor.add(e.getValue());cursors.add(cursor);}
        p.add("cursors",cursors);return p;
    }
    private static Claim claim(JsonElement item){
        if(item.isJsonNull())return null;JsonObject p=object(item);keys(p,"site","revision","min","max","complete","cursors");
        String site=text(p,"site");long revision=natural(p,"revision");if(!site.matches("[a-z][a-z0-9_]{0,63}(?::[a-z][a-z0-9_]{0,63})?")||revision<1)throw bad("Invalid checkpoint takeover pin");
        Area area=new Area(position(p.get("min")),position(p.get("max")));JsonElement e=p.get("cursors");
        if(!e.isJsonArray()||e.getAsJsonArray().size()>256)throw bad("Invalid takeover cursors");Map<Long,Integer> cursors=new LinkedHashMap<>();
        for(JsonElement entry:e.getAsJsonArray()){
            if(!entry.isJsonArray()||entry.getAsJsonArray().size()!=3)throw bad("Invalid takeover cursor tuple");
            JsonArray tuple=entry.getAsJsonArray();int x=signed(tuple.get(0)),z=signed(tuple.get(1)),cursor=signed(tuple.get(2));
            if(x<(area.min().x()>>4)||x>(area.max().x()>>4)||z<(area.min().z()>>4)||z>(area.max().z()>>4))throw bad("Takeover cursor outside region");
            int size=(Math.min(area.max().x(),(x<<4)+15)-Math.max(area.min().x(),x<<4)+1)*(Math.min(area.max().z(),(z<<4)+15)-Math.max(area.min().z(),z<<4)+1)*(area.max().y()-area.min().y()+1);
            if(cursor<1||cursor>size||cursors.put(((long)x<<32)|(z&0xffffffffL),cursor)!=null)throw bad("Invalid takeover cursor");
        }
        return new Claim(site,revision,area,bool(p,"complete"),cursors);
    }
    static JsonObject object(JsonElement e){if(e==null||!e.isJsonObject())throw bad("Checkpoint object required");return e.getAsJsonObject();}
    static void keys(JsonObject p,String... fields){if(!p.keySet().equals(Set.of(fields)))throw bad("Unexpected checkpoint fields");}
    static String text(JsonObject p,String k){JsonElement e=p.get(k);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw bad("Checkpoint string required");return e.getAsString();}
    static long natural(JsonObject p,String k){JsonElement e=p.get(k);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("0|[1-9][0-9]*"))throw bad("Checkpoint integer required");return Long.parseLong(e.getAsString());}
    static int integer(JsonObject p,String k){return Math.toIntExact(natural(p,k));}
    private static int signed(JsonElement e){if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("-?(0|[1-9][0-9]*)"))throw bad("Checkpoint signed integer required");return Integer.parseInt(e.getAsString());}
    static boolean bool(JsonObject p,String k){JsonElement e=p.get(k);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean())throw bad("Checkpoint boolean required");return e.getAsBoolean();}
    private static IllegalArgumentException bad(String message){return new IllegalArgumentException(message);}
}
