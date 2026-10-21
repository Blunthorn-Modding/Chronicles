package io.github.chronicle.quietbelow.ecology;

import java.util.*;

import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.Pos;

/**
 * Derived, bounded local ecological costs. Native code supplies only current eligible
 * participants and performs the final effect guard without loading world metadata.
 */
public final class EcologyCosts {
    public static final int RADIUS=4, DENSITY=8, SPHERE_CHECKS=257, MAX_PLAYERS=256,
            MAX_METADATA_CHECKS=MAX_PLAYERS*DENSITY+SPHERE_CHECKS, MAX_APPLICATIONS=2;
    public static final List<Integer> SECONDS=List.of(10,20,40,60,120);

    public enum Kind { HUNGER, DARKNESS }
    public record Rule(boolean enabled,int seconds) {
        public Rule { if(!SECONDS.contains(seconds))throw new IllegalArgumentException("Unsupported local-cost interval"); }
    }
    public record Rules(Rule hunger,Rule darkness) {
        public Rules { Objects.requireNonNull(hunger);Objects.requireNonNull(darkness); }
    }
    public static final Rules DEFAULT=new Rules(new Rule(true,40),new Rule(true,40));
    public record Player(UUID id,Pos position) {
        public Player { Objects.requireNonNull(id);Objects.requireNonNull(position); }
    }
    public interface World {
        /** Native adapter supplies currently eligible, online PLAY participants only. */
        List<Player> eligiblePlayers();
        /** Rechecks identity, eligibility, actual density, extent, play and saved rules. */
        boolean apply(UUID player,Kind kind);
    }
    public interface Network {
        /** True only while the topology is resolved and global extent is Established or Extensive. */
        boolean costsAvailable();
        boolean extensive();
        boolean owns(Pos position);
        /** True only for owned, physically rooted sculk with at least 25% vitality. */
        boolean living(Pos position);
    }

    private record Offset(int x,int y,int z,int distance) { }
    private static final List<Offset> OFFSETS=offsets();
    private static final class Entry {
        final List<Pos> witnesses;
        long hungerNext,darknessNext;
        Entry(List<Pos> witnesses,long hungerNext,long darknessNext){
            this.witnesses=witnesses;this.hungerNext=hungerNext;this.darknessNext=darknessNext;
        }
    }

    private Rules rules;
    private final Map<UUID,Entry> players=new HashMap<>();
    private final ArrayDeque<UUID> rotation=new ArrayDeque<>();
    private long ticks,applications;
    private int checks,attempts;
    private String failure;

    public EcologyCosts(Rules rules){this.rules=Objects.requireNonNull(rules);}
    public Rules rules(){return rules;}
    public long ticks(){return ticks;}
    /** Number of native guarded application attempts, including native skips. */
    public long applications(){return applications;}
    public int tracked(){return players.size();}
    /** Metadata positions read by this scheduler on the most recent tick. */
    public int checks(){return checks;}
    /** Native application attempts on the most recent tick. */
    public int attempts(){return attempts;}
    public String failure(){return failure;}
    public void rules(Rules rules){this.rules=Objects.requireNonNull(rules);clear();}
    public void fail(String detail){if(failure==null)failure=Objects.requireNonNull(detail);clear();}

    public void tick(World world,Network network,boolean playing){
        checks=attempts=0;
        if(failure!=null)return;
        if(!rules.hunger.enabled&&!rules.darkness.enabled||!playing||!network.costsAvailable()){clear();return;}
        Map<UUID,Player> present=new LinkedHashMap<>();
        for(Player player:world.eligiblePlayers())present.put(player.id(),player);
        if(present.size()>MAX_PLAYERS){fail("Local-cost participant capacity reached; reduce eligible participants.");return;}
        if(present.isEmpty()){clear();return;}
        ticks=Math.addExact(ticks,1);
        players.keySet().retainAll(present.keySet());
        rotation.removeIf(id->!present.containsKey(id));
        for(UUID id:present.keySet())if(!rotation.contains(id))rotation.addLast(id);
        players.entrySet().removeIf(entry->{
            Player player=present.get(entry.getKey());
            return player==null||!witnesses(network,player.position(),entry.getValue().witnesses);
        });
        resetIneligibleDeadlines(network);
        if(rotation.isEmpty())return;
        UUID id=rotation.removeFirst();rotation.addLast(id);
        Player player=present.get(id);
        Entry entry=players.get(id);
        if(entry==null){
            List<Pos> sources=findDense(network,player.position());
            if(sources!=null)players.put(id,new Entry(sources,deadline(rules.hunger),darknessDeadline(network)));
            return;
        }
        apply(world,network,id,Kind.HUNGER,entry);
        apply(world,network,id,Kind.DARKNESS,entry);
    }

    /** Bounded final density proof for the native adapter: exactly eight distinct local witnesses. */
    public static boolean dense(Network network,Pos player){return denseSources(network,player)!=null;}

    private void apply(World world,Network network,UUID id,Kind kind,Entry entry){
        Rule rule=kind==Kind.HUNGER?rules.hunger:rules.darkness;
        boolean stageEligible=kind==Kind.HUNGER||network.extensive();
        long next=kind==Kind.HUNGER?entry.hungerNext:entry.darknessNext;
        if(!rule.enabled||!stageEligible){setNext(entry,kind,0);return;}
        if(next==0){setNext(entry,kind,deadline(rule));return;}
        if(ticks<next)return;
        attempts++;applications++;
        world.apply(id,kind); // A false result is a guarded native skip and still consumes this interval.
        setNext(entry,kind,deadline(rule));
    }
    private long deadline(Rule rule){return ticks+(long)rule.seconds*20;}
    private long darknessDeadline(Network network){return rules.darkness.enabled&&network.extensive()?deadline(rules.darkness):0;}
    private static void setNext(Entry entry,Kind kind,long value){if(kind==Kind.HUNGER)entry.hungerNext=value;else entry.darknessNext=value;}
    /** A stage/rule edge resets every participant, even when that participant is not selected this tick. */
    private void resetIneligibleDeadlines(Network network){
        for(Entry entry:players.values()){
            if(!rules.hunger.enabled)entry.hungerNext=0;
            if(!rules.darkness.enabled||!network.extensive())entry.darknessNext=0;
        }
    }

    private List<Pos> findDense(Network network,Pos player){
        List<Pos> witnesses=new ArrayList<>(DENSITY);
        for(Offset offset:OFFSETS){
            checks++;
            Pos position=safePosition(player,offset);
            if(position!=null&&network.owns(position)&&network.living(position)){
                witnesses.add(position);
                if(witnesses.size()==DENSITY)return List.copyOf(witnesses);
            }
        }
        return null;
    }
    private static List<Pos> denseSources(Network network,Pos player){
        List<Pos> witnesses=new ArrayList<>(DENSITY);
        for(Offset offset:OFFSETS){
            Pos position=safePosition(player,offset);
            if(position!=null&&network.owns(position)&&network.living(position)){
                witnesses.add(position);
                if(witnesses.size()==DENSITY)return List.copyOf(witnesses);
            }
        }
        return null;
    }
    private boolean witnesses(Network network,Pos player,List<Pos> sources){
        if(sources.size()!=DENSITY)return false;
        for(Pos source:sources){
            checks++;
            if(distance(player,source)>RADIUS*RADIUS||!network.owns(source)||!network.living(source))return false;
        }
        return true;
    }
    private static int distance(Pos a,Pos b){
        long x=(long)a.x()-b.x(),y=(long)a.y()-b.y(),z=(long)a.z()-b.z();
        return x*x+y*y+z*z>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)(x*x+y*y+z*z);
    }
    private static List<Offset> offsets(){
        List<Offset> offsets=new ArrayList<>();
        for(int dx=-RADIUS;dx<=RADIUS;dx++)for(int dy=-RADIUS;dy<=RADIUS;dy++)for(int dz=-RADIUS;dz<=RADIUS;dz++)
            if(dx*dx+dy*dy+dz*dz<=RADIUS*RADIUS)offsets.add(new Offset(dx,dy,dz,dx*dx+dy*dy+dz*dz));
        offsets.sort(Comparator.comparingInt(Offset::distance).thenComparingInt(Offset::x).thenComparingInt(Offset::y).thenComparingInt(Offset::z));
        return List.copyOf(offsets);
    }
    private void clear(){players.clear();rotation.clear();}
    private static Pos safePosition(Pos center,Offset offset){
        long x=(long)center.x()+offset.x(),y=(long)center.y()+offset.y(),z=(long)center.z()+offset.z();
        return Math.abs(x)>30_000_000||Math.abs(z)>30_000_000||Math.abs(y)>4096?null:new Pos((int)x,(int)y,(int)z);
    }
}
