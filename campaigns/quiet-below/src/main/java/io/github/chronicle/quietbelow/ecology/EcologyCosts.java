package io.github.chronicle.quietbelow.ecology;

import java.util.*;

import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.Pos;

/** Bounded continuous local costs; native code owns application and removal of its effects. */
public final class EcologyCosts {
    public static final int MAX_PLAYERS=256, MAX_METADATA_CHECKS=MAX_PLAYERS, MAX_APPLICATIONS=MAX_PLAYERS*2;
    public static final List<Integer> SECONDS=List.of(10,20,40,60,120);
    public enum Kind { HUNGER, DARKNESS }
    public record Rule(boolean enabled,int seconds) { public Rule {if(!SECONDS.contains(seconds))throw new IllegalArgumentException("Unsupported local-cost interval");} }
    public record Rules(Rule hunger,Rule darkness) { public Rules {Objects.requireNonNull(hunger);Objects.requireNonNull(darkness);} }
    public static final Rules DEFAULT=new Rules(new Rule(true,40),new Rule(true,40));
    /** A null position is permitted only for an explicit airborne sample. */
    public record Player(UUID id,Pos position,boolean airborne) {
        public Player {Objects.requireNonNull(id);if(position==null&&!airborne)throw new IllegalArgumentException("Grounded player needs a footing sample");}
        public Player(UUID id,Pos position){this(id,Objects.requireNonNull(position),false);}
        public static Player airborne(UUID id){return new Player(id,null,true);}
    }
    public interface World {
        List<Player> eligiblePlayers();
        /** Immutable desired effects, including an empty map to promptly withdraw campaign effects. */
        void synchronize(Map<UUID,Set<Kind>> active);
    }
    public interface Network {
        boolean costsAvailable(); boolean extensive(); boolean owns(Pos position); boolean living(Pos position);
    }
    private static final class Entry {long hungerDeadline,darknessDeadline;boolean hungerActive,darknessActive;Pos lastFooting;}
    private Rules rules;
    private final Map<UUID,Entry> players=new HashMap<>();
    private long ticks,applications;
    private int checks,attempts;
    private String failure;

    public EcologyCosts(Rules rules){this.rules=Objects.requireNonNull(rules);}
    public Rules rules(){return rules;} public long ticks(){return ticks;} public long applications(){return applications;}
    public int tracked(){return players.size();} public int checks(){return checks;} public int attempts(){return attempts;} public String failure(){return failure;}
    /** Last valid living footing for an airborne participant; used by the native recheck only. */
    public Pos footing(UUID player){Entry entry=players.get(Objects.requireNonNull(player));return entry==null?null:entry.lastFooting;}
    public void rules(Rules rules){this.rules=Objects.requireNonNull(rules);clear();}
    public void fail(String detail){if(failure==null)failure=Objects.requireNonNull(detail);clear();}
    /** Entity replacement or reconnect cannot retain the previous body's exposure credit. */
    public void resetPlayer(UUID player){players.remove(Objects.requireNonNull(player));}

    public void tick(World world,Network network,boolean playing){
        checks=attempts=0;
        if(failure!=null||!playing||!network.costsAvailable()||(!rules.hunger.enabled()&&!rules.darkness.enabled())){clear();synchronize(world,Map.of());return;}
        Map<UUID,Player> present=new LinkedHashMap<>();for(Player player:world.eligiblePlayers())present.put(player.id(),player);
        if(present.size()>MAX_PLAYERS){fail("Local-cost participant capacity reached; reduce eligible participants.");synchronize(world,Map.of());return;}
        if(present.isEmpty()){clear();synchronize(world,Map.of());return;}
        ticks=Math.addExact(ticks,1);players.keySet().retainAll(present.keySet());Map<UUID,Set<Kind>> active=new LinkedHashMap<>();
        for(Player player:present.values()){
            checks++;Entry entry=players.get(player.id());
            if(player.airborne()){
                if(entry==null||entry.lastFooting==null||!network.owns(entry.lastFooting)||!network.living(entry.lastFooting)){players.remove(player.id());continue;}
            }else{
                boolean contact=network.owns(player.position())&&network.living(player.position());
                if(!contact){players.remove(player.id());continue;}
                entry=players.computeIfAbsent(player.id(),ignored->new Entry());entry.lastFooting=player.position();
            }
            EnumSet<Kind> kinds=EnumSet.noneOf(Kind.class);
            update(entry,Kind.HUNGER,rules.hunger(),true,kinds);update(entry,Kind.DARKNESS,rules.darkness(),network.extensive(),kinds);
            if(!kinds.isEmpty())active.put(player.id(),Set.copyOf(kinds));
        }
        synchronize(world,active);
    }
    private void update(Entry entry,Kind kind,Rule rule,boolean stageEligible,EnumSet<Kind> active){
        if(!rule.enabled()||!stageEligible){setDeadline(entry,kind,0);setActive(entry,kind,false);return;}
        long deadline=deadline(entry,kind);if(deadline==0){setDeadline(entry,kind,ticks+(long)rule.seconds()*20);setActive(entry,kind,false);return;}
        if(ticks<deadline)return;if(!isActive(entry,kind)){setActive(entry,kind,true);applications++;}active.add(kind);attempts++;
    }
    private static long deadline(Entry entry,Kind kind){return kind==Kind.HUNGER?entry.hungerDeadline:entry.darknessDeadline;}
    private static void setDeadline(Entry entry,Kind kind,long value){if(kind==Kind.HUNGER)entry.hungerDeadline=value;else entry.darknessDeadline=value;}
    private static boolean isActive(Entry entry,Kind kind){return kind==Kind.HUNGER?entry.hungerActive:entry.darknessActive;}
    private static void setActive(Entry entry,Kind kind,boolean value){if(kind==Kind.HUNGER)entry.hungerActive=value;else entry.darknessActive=value;}
    private void clear(){players.clear();}
    private static void synchronize(World world,Map<UUID,Set<Kind>> active){Map<UUID,Set<Kind>> copy=new LinkedHashMap<>();active.forEach((id,kinds)->copy.put(id,Set.copyOf(kinds)));world.synchronize(Map.copyOf(copy));}
}
