package io.github.chronicle.quietbelow.ecology;

import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.Pos;

/** Derived, bounded crop discovery and per-position bonus cooldowns; no world entities. */
public final class EcologyCrops {
    public static final int RADIUS=4, DISCOVERY_READS=128, SECTION_CHECKS=16, CROP_CHECKS=16,
            SOURCE_CHECKS=32, WRITES=2, MAX_CROPS=8192, MAX_SECTIONS=EcologyEngine.MAX_CELLS*8;
    public static final List<Integer> SECONDS=List.of(2,5,10,20,40);
    private static final Set<String> CROP_IDS=Set.of("minecraft:wheat","minecraft:carrots","minecraft:potatoes","minecraft:beetroots");
    public record Rules(boolean enabled,int seconds) {
        public Rules { if(!SECONDS.contains(seconds))throw new IllegalArgumentException("Unsupported crop bonus interval"); }
    }
    public static final Rules DEFAULT=new Rules(true,10);
    public record Section(int x,int y,int z) {
        public static Section at(Pos p){return new Section(p.x()>>4,p.y()>>4,p.z()>>4);}
        public Pos at(int cursor){return safePosition(x*16+cursor%16,y*16+cursor/256,z*16+cursor/16%16);}
    }
    public record Crop(String id,int age,int maximum,boolean eligible) {
        public Crop {
            if(!CROP_IDS.contains(id)
                    ||maximum!=(id.equals("minecraft:beetroots")?3:7)||age<0||age>maximum)
                throw new IllegalArgumentException("Unsupported crop state");
        }
    }
    public interface Terrain {
        boolean loaded(Pos p);
        /** Loaded section palette only; must not load chunks or require the section origin inside a custom border. */
        boolean mayContainCrops(Section section);
        Crop crop(Pos p);
        boolean grow(Pos p,Crop expected);
    }
    public interface Network {
        List<Pos> cropSources();
        boolean owns(Pos p);
        boolean living(Pos p);
        boolean cropsAvailable();
    }
    private static final class Entry { long next;Entry(long next){this.next=next;} }
    private Rules rules;
    private long ticks,bonuses,nextSources;
    private String failure;
    private final LinkedHashSet<Section> sections=new LinkedHashSet<>();
    private Set<Section> rebuilding;
    private Iterator<Pos> sources;
    private Iterator<Section> scan;
    private Section current;
    private int cursor,reads,writes,checks;
    private final Map<Pos,Entry> crops=new HashMap<>();
    private final ArrayDeque<Pos> rotation=new ArrayDeque<>();
    private final LinkedHashSet<Pos> notices=new LinkedHashSet<>();
    public EcologyCrops(Rules rules){this.rules=Objects.requireNonNull(rules);}
    public Rules rules(){return rules;}
    public long ticks(){return ticks;}
    public long bonuses(){return bonuses;}
    public int cached(){return crops.size();}
    public int sections(){return sections.size();}
    public int reads(){return reads;}
    public int writes(){return writes;}
    public String failure(){return failure;}
    public void fail(String detail){if(failure==null)failure=Objects.requireNonNull(detail);}
    public void notice(Pos p){if(sections.contains(Section.at(p))&&notices.size()<MAX_CROPS)notices.add(p);}
    public void planted(Pos p){Entry old=crops.get(p);if(old!=null)old.next=ticks+interval();notice(p);}
    /** A successful owned geometry write can expose a new footprint before the next rebuild. */
    public void source(Pos p){
        if(failure!=null)return;
        for(int x=(p.x()-RADIUS)>>4;x<=((p.x()+RADIUS)>>4);x++)
            for(int y=(p.y()-RADIUS)>>4;y<=((p.y()+RADIUS)>>4);y++)
                for(int z=(p.z()-RADIUS)>>4;z<=((p.z()+RADIUS)>>4);z++){
                    Section section=new Section(x,y,z);
                    if(!sections.contains(section)&&sections.size()>=MAX_SECTIONS){fail("Crop discovery section capacity reached; reduce the network and use Reset crop tuning.");return;}
                    sections.add(section);if(rebuilding!=null)rebuilding.add(section);
                }
    }
    public void tick(Terrain world,Network network,boolean playing){
        reads=writes=checks=0;
        if(failure!=null||!rules.enabled||!playing||!network.cropsAvailable())return;
        ticks=Math.addExact(ticks,1);
        if(sources==null&&ticks>=nextSources){sources=network.cropSources().iterator();rebuilding=new LinkedHashSet<>();}
        if(sources!=null){
            for(int n=0;n<SOURCE_CHECKS&&sources.hasNext();n++){Pos p=sources.next();if(network.owns(p))source(p);}
            if(!sources.hasNext()){
                sections.retainAll(rebuilding);rebuilding=null;sources=null;nextSources=ticks+200;
                if(current!=null&&!sections.contains(current))current=null;
            }
        }
        if(failure!=null)return;
        for(int n=0;n<CROP_CHECKS&&!notices.isEmpty();n++){
            Pos p=notices.iterator().next();notices.remove(p);if(!crops.containsKey(p)&&world.loaded(p))discover(world,network,p);
        }
        if(failure!=null)return;
        int discovery=0;
        while(discovery<DISCOVERY_READS&&checks<SECTION_CHECKS){
            if(current==null){
                if(scan==null||!scan.hasNext())scan=new ArrayList<>(sections).iterator();
                if(!scan.hasNext())break;
                Section section=scan.next();checks++;
                if(!sections.contains(section)||section.at(0)==null||!world.mayContainCrops(section))continue;
                current=section;cursor=0;
            }
            Pos p=current.at(cursor++);discovery++;
            if(p!=null&&!crops.containsKey(p)&&world.loaded(p))discover(world,network,p);
            if(cursor==4096)current=null;
            if(failure!=null)return;
        }
        int candidates=Math.min(CROP_CHECKS,rotation.size());
        for(int n=0;n<candidates;n++){
            Pos p=rotation.removeFirst();Entry entry=crops.get(p);if(entry==null)continue;
            if(!world.loaded(p)){crops.remove(p);continue;}
            reads++;Crop crop=world.crop(p);
            if(crop==null||crop.age==crop.maximum){crops.remove(p);continue;}
            boolean living=nearLiving(network,p);
            if(!living&&!near(network,p,false)){crops.remove(p);continue;}
            rotation.addLast(p);
            if(!crop.eligible||!living){entry.next=ticks+interval();continue;}
            if(ticks>=entry.next&&writes<WRITES){
                // Native adapter compares the sampled state and its growth conditions again.
                writes++;if(world.grow(p,crop))bonuses++;
                entry.next=ticks+interval();
            }
        }
    }
    private int interval(){return rules.seconds*20;}
    private void discover(Terrain world,Network network,Pos p){
        reads++;Crop crop=world.crop(p);if(crop==null||crop.age==crop.maximum)return;
        if(!near(network,p,false))return;
        if(crops.size()>=MAX_CROPS){fail("Crop cache capacity reached; reduce nearby crops and use Reset crop tuning.");return;}
        crops.put(p,new Entry(ticks+interval()));rotation.addLast(p);
    }
    public static boolean nearLiving(Network network,Pos crop){
        return near(network,crop,true);
    }
    private static boolean near(Network network,Pos crop,boolean living){
        for(int dx=-RADIUS;dx<=RADIUS;dx++)for(int dy=-RADIUS;dy<=RADIUS;dy++)for(int dz=-RADIUS;dz<=RADIUS;dz++)
            if(dx*dx+dy*dy+dz*dz<=RADIUS*RADIUS){
                Pos p=safePosition(crop.x()+dx,crop.y()+dy,crop.z()+dz);
                if(p!=null&&(living?network.living(p):network.owns(p)))return true;
            }
        return false;
    }
    private static Pos safePosition(int x,int y,int z){
        return Math.abs((long)x)>30_000_000||Math.abs((long)z)>30_000_000||Math.abs((long)y)>4096?null:new Pos(x,y,z);
    }
}
