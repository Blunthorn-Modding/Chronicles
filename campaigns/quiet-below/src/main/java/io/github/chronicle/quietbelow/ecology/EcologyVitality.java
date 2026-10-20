package io.github.chronicle.quietbelow.ecology;

import com.google.gson.*;
import java.util.*;
import java.util.function.Predicate;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;

/** Lazy vitality: append shared rate budgets, and rebase a cell only when its feeding mode changes. */
public final class EcologyVitality {
    public static final int FULL=240_000, READY=FULL/4, WORK=128, CHECKPOINT=200;
    public static final List<Integer> MINUTES=List.of(1,2,5,10,20,40);
    private static final int[][] EXTENTS={{64,512},{128,1024},{256,2048},{512,4096},{1024,8192}};
    public enum Mode { DORMANT, SEALED, SHADED, LIT, UNKNOWN }
    public record Rules(int lossMinutes,int recoveryMinutes,int extentPreset) {
        public Rules {if(!MINUTES.contains(lossMinutes)||!MINUTES.contains(recoveryMinutes)||extentPreset<0||extentPreset>=EXTENTS.length)throw new IllegalArgumentException("Unsupported vitality tuning");}
        public int established(){return EXTENTS[extentPreset][0];}
        public int extensive(){return EXTENTS[extentPreset][1];}
    }
    public static final Rules DEFAULT=new Rules(10,5,2);
    private record Cell(int value,Mode mode,long budget) { }
    public record Summary(int rooted,int ready,int detached,int feeding,int sealed,int unknown,long rootedHealth,long detachedHealth) {
        public int rootedPercent(){return rooted==0?0:(int)(rootedHealth*100/FULL/rooted);}
        public int detachedPercent(){return detached==0?0:(int)(detachedHealth*100/FULL/detached);}
    }
    private final Map<Pos,Cell> cells=new HashMap<>();
    private Rules rules=DEFAULT;
    private long ticks,loss,recovery;
    private String extent="Rooted";
    public Rules rules(){return rules;}
    public long ticks(){return ticks;}
    public String extent(){return extent;}
    public Mode mode(Pos p){Cell c=cells.get(p);return c==null?null:c.mode;}
    public int value(Pos p){Cell c=cells.get(p);if(c==null)return FULL;long delta=Math.min(FULL,budget(c.mode)-c.budget);return (int)Math.clamp(c.value+(c.mode==Mode.DORMANT||c.mode==Mode.SEALED?-delta:delta),0L,FULL);}
    public boolean ready(Pos p){return value(p)>=READY;}
    public int percent(Pos p){return value(p)*100/FULL;}
    public void forget(Pos p){cells.remove(p);}
    private long budget(Mode m){return switch(m){case DORMANT,SEALED->loss;case SHADED->recovery;case LIT->Math.multiplyExact(recovery,2);case UNKNOWN->0;};}
    public static JsonObject rulesPayload(Rules r){JsonObject p=new JsonObject();p.addProperty("loss",r.lossMinutes);p.addProperty("recovery",r.recoveryMinutes);p.addProperty("extent",r.extentPreset);return p;}
    public static Rules parseRules(JsonObject p){keys(p,"loss","recovery","extent");return new Rules(integer(p,"loss"),integer(p,"recovery"),integer(p,"extent"));}
    public void applyRules(JsonObject p){rules=parseRules(p);}
    public static JsonObject change(Pos at,Mode mode){JsonObject c=new JsonObject();c.add("p",EcologyEngine.pos(at));c.addProperty("mode",mode.name());return c;}
    public static JsonObject payload(int elapsed,JsonArray states,int ready){JsonObject p=new JsonObject();p.addProperty("ticks",elapsed);p.add("states",states);p.addProperty("ready",ready);return p;}
    public void validate(JsonObject p,Predicate<Pos> owned){
        keys(p,"ticks","states","ready");int elapsed=integer(p,"ticks"),ready=integer(p,"ready");JsonArray states=states(p);
        if(elapsed<0||elapsed>CHECKPOINT||ready< -1||ready>EcologyEngine.MAX_CELLS||(elapsed==0&&states.isEmpty()&&ready<0))throw new IllegalArgumentException("Invalid vitality checkpoint");
        Math.addExact(ticks,elapsed);long nextLoss=Math.addExact(loss,(long)elapsed*FULL/(rules.lossMinutes*1200));
        long nextRecovery=Math.addExact(recovery,(long)elapsed*FULL/(rules.recoveryMinutes*1200));Math.multiplyExact(nextRecovery,2);
        if(nextLoss<0)throw new IllegalArgumentException("Vitality budget overflow");
        Set<Pos> seen=new HashSet<>();for(JsonElement item:states){JsonObject c=item.getAsJsonObject();keys(c,"p","mode");Pos at=EcologyEngine.position(c.get("p"));
            if(!owned.test(at)||!seen.add(at))throw new IllegalArgumentException("Unknown or duplicate vitality cell");parseMode(c);}
    }
    public void apply(JsonObject p){
        int elapsed=integer(p,"ticks");ticks+=elapsed;loss+=(long)elapsed*FULL/(rules.lossMinutes*1200);recovery+=(long)elapsed*FULL/(rules.recoveryMinutes*1200);
        for(JsonElement item:states(p)){JsonObject c=item.getAsJsonObject();Pos at=EcologyEngine.position(c.get("p"));Mode mode=parseMode(c);cells.put(at,new Cell(value(at),mode,budget(mode)));}
        int ready=integer(p,"ready");if(ready>=0)extent=nextExtent(extent,ready,rules);
    }
    public static String nextExtent(String old,int ready,Rules r){
        if(ready>=r.extensive())return "Extensive";
        if(old.equals("Extensive")&&ready>=r.extensive()-r.extensive()/8)return old;
        if(ready>=r.established())return "Established";
        if(!old.equals("Rooted")&&ready>=r.established()-r.established()/8)return "Established";
        return "Rooted";
    }
    private static JsonArray states(JsonObject p){JsonElement e=p.get("states");if(e==null||!e.isJsonArray()||e.getAsJsonArray().size()>WORK)throw new IllegalArgumentException("Invalid vitality states");return e.getAsJsonArray();}
    private static Mode parseMode(JsonObject c){JsonElement e=c.get("mode");if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Invalid feeding mode");return Mode.valueOf(e.getAsString());}
    private static void keys(JsonObject p,String... keys){if(!p.keySet().equals(Set.of(keys)))throw new IllegalArgumentException("Unexpected vitality fields");}
    private static int integer(JsonObject p,String key){JsonElement e=p.get(key);try{if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("-?(0|[1-9][0-9]*)"))throw new IllegalArgumentException();return Integer.parseInt(e.getAsString());}catch(RuntimeException ex){throw new IllegalArgumentException("Invalid vitality integer: "+key);}}
}
