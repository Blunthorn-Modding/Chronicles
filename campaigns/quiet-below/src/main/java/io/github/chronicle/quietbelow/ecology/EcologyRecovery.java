package io.github.chronicle.quietbelow.ecology;

import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.Pos;

/**
 * Derived, bounded local healing. The native adapter supplies only alive survival/adventure,
 * non-author participants, and must revalidate health, position, and power in {@link World#heal}.
 */
public final class EcologyRecovery {
    public static final int RADIUS=4, CUBE_CHECKS=729, SPHERE_CHECKS=257, GUARDED_CHECKS=513,
            HEALS=1, MAX_PLAYERS=256;
    public static final List<Integer> SECONDS=List.of(2,5,10,20,40);
    public record Rules(boolean enabled,int seconds) {
        public Rules { if(!SECONDS.contains(seconds))throw new IllegalArgumentException("Unsupported recovery interval"); }
    }
    public static final Rules DEFAULT=new Rules(true,10);
    public record Player(UUID id,Pos position) { public Player { Objects.requireNonNull(id);Objects.requireNonNull(position); } }
    public interface World {
        /** Native adapter supplies currently eligible, online PLAY participants only. */
        List<Player> eligiblePlayers();
        boolean needsHealing(UUID player);
        /** Recheck actual player health, position, and recovery availability before changing health. */
        boolean heal(UUID player,float points);
    }
    public interface Network {
        /** True only while the global extent is Established or Extensive and topology is resolved. */
        boolean recoveryAvailable();
        boolean owns(Pos position);
        /** True only for owned, rooted sculk with at least 25% vitality. */
        boolean living(Pos position);
    }
    private record Offset(int x,int y,int z,int distance) { }
    private static final List<Offset> OFFSETS=offsets();
    private static final class Entry { long next;Pos source;Entry(long next,Pos source){this.next=next;this.source=source;} }
    private Rules rules;
    private final Map<UUID,Entry> players=new HashMap<>();
    private final ArrayDeque<UUID> rotation=new ArrayDeque<>();
    private long ticks,healed;
    private int checks,heals;
    private String failure;

    public EcologyRecovery(Rules rules){this.rules=Objects.requireNonNull(rules);}
    public Rules rules(){return rules;}
    public long ticks(){return ticks;}
    public long healed(){return healed;}
    public int tracked(){return players.size();}
    public int checks(){return checks;}
    public int heals(){return heals;}
    public String failure(){return failure;}
    public void rules(Rules rules){this.rules=Objects.requireNonNull(rules);clear();}
    public void fail(String detail){if(failure==null)failure=Objects.requireNonNull(detail);clear();}

    public void tick(World world,Network network,boolean playing){
        checks=heals=0;
        if(failure!=null)return;
        if(!rules.enabled||!playing||!network.recoveryAvailable()){clear();return;}
        List<Player> eligible=world.eligiblePlayers();
        if(eligible.size()>MAX_PLAYERS){fail("Recovery participant capacity reached; reduce eligible participants.");return;}
        if(eligible.isEmpty()){clear();return;}
        ticks=Math.addExact(ticks,1);
        Map<UUID,Player> present=new LinkedHashMap<>();for(Player player:eligible)present.put(player.id(),player);
        players.keySet().retainAll(present.keySet());
        rotation.removeIf(id->!present.containsKey(id));
        for(UUID id:present.keySet())if(!rotation.contains(id))rotation.addLast(id);
        players.entrySet().removeIf(entry->{
            Player candidate=present.get(entry.getKey());
            return candidate==null||!world.needsHealing(entry.getKey())||!witness(network,candidate.position(),entry.getValue().source);
        });
        UUID id=rotation.removeFirst();rotation.addLast(id);
        Player player=present.get(id);
        if(!world.needsHealing(player.id())){players.remove(player.id());return;}
        Entry entry=players.get(player.id());
        if(entry==null){
            Pos source=findLiving(network,player.position());
            if(source!=null)players.put(player.id(),new Entry(ticks+interval(),source));
            return;
        }
        if(ticks<entry.next){
            Pos source=findLiving(network,player.position());
            if(source==null)players.remove(player.id());else entry.source=source;
            return;
        }
        heals++;
        if(world.heal(player.id(),1.0F))healed++;
        entry.next=ticks+interval();
    }

    /**
     * At most 257 Euclidean positions (within the 9x9x9, 729-position cube), without loading
     * chunks or scanning the world. All cached cooldowns use at most 256 one-position witnesses;
     * a selected-player search or native guarded recheck adds at most 257, for 513 metadata-only
     * positions per tick.
     */
    public boolean nearLiving(Network network,Pos player){
        return findLiving(network,player)!=null;
    }
    private Pos findLiving(Network network,Pos player){
        for(Offset offset:OFFSETS){
            checks++;
            Pos position=safePosition(player,offset);
            if(position!=null&&network.owns(position)&&network.living(position))return position;
        }
        return null;
    }
    private boolean witness(Network network,Pos player,Pos source){
        checks++;
        return source!=null&&distance(player,source)<=RADIUS*RADIUS&&network.owns(source)&&network.living(source);
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
    private int interval(){return rules.seconds*20;}
    private void clear(){players.clear();rotation.clear();}
    private static Pos safePosition(Pos center,Offset offset){
        long x=(long)center.x()+offset.x(),y=(long)center.y()+offset.y(),z=(long)center.z()+offset.z();
        return Math.abs(x)>30_000_000||Math.abs(z)>30_000_000||Math.abs(y)>4096?null:new Pos((int)x,(int)y,(int)z);
    }
}
