package io.github.chronicle.quietbelow.ecology;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;

/** Bounded, durable campaign ecology. No clocks, players, chunk loads or Minecraft classes. */
public final class EcologyEngine implements EcologyCrops.Network,EcologyRecovery.Network,EcologyCosts.Network {
    public static final int MAX_CELLS=131072, MAX_AIR=524288, INDEX_READS=256, GRAPH_WORK=128, MAX_WRITES=4;
    public static final long MAX_SOURCE_VOLUME=16_777_216;
    public static final List<Integer> SPEEDS=List.of(25,50,100,200,400,800,1600);
    public record Setup(String city, long cityRevision, String heart, long heartRevision, String dimension) {
        public Setup {
            if(city==null || !city.matches("[a-z][a-z0-9_]{0,63}(?::[a-z][a-z0-9_]{0,63})?") ||
                    heart==null || !heart.matches("[a-z][a-z0-9_]{0,63}(?::[a-z][a-z0-9_]{0,63})?") ||
                    cityRevision<1 || heartRevision<1 || dimension==null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid ecology setup pin");
        }
    }
    public record Area(Pos min, Pos max) {
        public Area {
            if(min.x()>max.x() || min.y()>max.y() || min.z()>max.z() || volume(min,max)>MAX_SOURCE_VOLUME)
                throw new IllegalArgumentException("Sculk region exceeds ecology indexing bounds");
            long chunks=((long)(max.x()>>4)-(min.x()>>4)+1)*((long)(max.z()>>4)-(min.z()>>4)+1);
            if(chunks>256) throw new IllegalArgumentException("Sculk region may span at most 256 chunks in this review build");
        }
        private static long volume(Pos a,Pos b) {
            try { return Math.multiplyExact(Math.multiplyExact((long)b.x()-a.x()+1,(long)b.y()-a.y()+1),(long)b.z()-a.z()+1); }
            catch(ArithmeticException e) { return Long.MAX_VALUE; }
        }
        boolean contains(Pos p) { return p.x()>=min.x()&&p.x()<=max.x()&&p.y()>=min.y()&&p.y()<=max.y()&&p.z()>=min.z()&&p.z()<=max.z(); }
    }
    public record Cell(Pos position, State before, State after, boolean adopted) { }
    public record Claim(String site,long revision,Area area,boolean complete,Map<Long,Integer> cursors) {
        public Claim { cursors=Map.copyOf(cursors); }
    }
    public record View(boolean configured, boolean indexed, boolean enabled, String hold, int owned,
                       int connected, int frontier, int completedChunks, int totalChunks, boolean unresolved,
                       String extent, long revision, long journalBytes) { }

    private final EcologyJournal journal;
    private final LinkedHashMap<Pos,Cell> cells=new LinkedHashMap<>();
    private final EcologyPositions positions=new EcologyPositions(MAX_CELLS);
    private final Map<Long,Integer> ownedChunks=new HashMap<>();
    private final ArrayList<Pos> air=new ArrayList<>();
    private final Set<Pos> airSet=new HashSet<>();
    private final Map<Long,Integer> cursors=new LinkedHashMap<>();
    private Setup setup;
    private Area area;
    private Pos heart;
    private boolean indexed,enabled;
    private String failure;
    private long controlRevision;
    private int chunkTurn,clock,writes,growthInterval=20;
    private long growthTurn;
    private final EcologyVitality vitality=new EcologyVitality();
    private EcologyCrops.Rules cropRules=EcologyCrops.DEFAULT;
    private EcologyRecovery.Rules recoveryRules=EcologyRecovery.DEFAULT;
    private EcologyCosts.Rules costRules=EcologyCosts.DEFAULT;
    private int vitalityTicks;
    private Iterator<Pos> vitalityScan;
    private boolean vitalityPending=true;
    private EcologyVitality.Summary vitalitySummary=new EcologyVitality.Summary(0,0,0,0,0,0,0,0);
    private int vRooted,vReady,vDetached,vFeeding,vSealed,vUnknown;
    private long vRootedHealth,vDetachedHealth;
    private int speedPercent=100;
    private long generation;
    private long graphGeneration=-1;
    private Iterator<Pos> validation;
    private Iterator<Pos> exposureScan;
    private Iterator<Pos> appearanceScan;
    private Iterator<Pos> contactScan;
    private final Set<Pos> pendingContacts=new LinkedHashSet<>();
    private Claim claim;
    private boolean cleanup;
    private ArrayDeque<Pos> graph;
    private Set<Pos> visiting=new HashSet<>(), connected=new HashSet<>();
    private boolean graphUnknown,unresolved=true,validationUnknown,validated,extending;

    public EcologyEngine(EcologyJournal journal) {
        this.journal=Objects.requireNonNull(journal);
        List<EcologyJournal.Entry> entries=journal.entries();
        for(int n=0;n<entries.size();n++) {
            EcologyJournal.Entry e=entries.get(n);
            if(e.type().equals("CHECKPOINT_BEGIN")) {
                EcologyCheckpoint.Decoded checkpoint=EcologyCheckpoint.read(entries,n);
                restoreCheckpoint(checkpoint);n=checkpoint.end();
            } else replay(e.type(),e.payload());
        }
        controlRevision=journal.revision();
    }
    /** Called only on a new, unexposed engine during strict startup replay. */
    private void restoreCheckpoint(EcologyCheckpoint.Decoded checkpoint){
        if(!indexed)throw bad("Checkpoint before source index completion");
        Set<Pos> adopted=new HashSet<>();for(Cell c:cells.values())if(c.adopted)adopted.add(c.position);
        for(Cell c:checkpoint.cells())if(c.adopted&&(!adopted.contains(c.position)||!c.before.equals(AIR)))throw bad("Checkpoint invented a source cell");
        JsonObject h=checkpoint.header();enabled=EcologyCheckpoint.bool(h,"enabled");cleanup=EcologyCheckpoint.bool(h,"cleanup");
        speedPercent=EcologyCheckpoint.integer(h,"speed");if(!SPEEDS.contains(speedPercent))throw bad("Unsupported checkpoint speed");
        JsonObject crop=EcologyCheckpoint.object(h.get("crops"));EcologyCheckpoint.keys(crop,"enabled","seconds");
        cropRules=new EcologyCrops.Rules(EcologyCheckpoint.bool(crop,"enabled"),EcologyCheckpoint.integer(crop,"seconds"));
        if(h.has("recovery")){
            JsonObject recovery=EcologyCheckpoint.object(h.get("recovery"));EcologyCheckpoint.keys(recovery,"enabled","seconds");
            recoveryRules=new EcologyRecovery.Rules(EcologyCheckpoint.bool(recovery,"enabled"),EcologyCheckpoint.integer(recovery,"seconds"));
        }
        EcologyCosts.Rules restoredCosts=h.has("costs")?costRules(EcologyCheckpoint.object(h.get("costs"))):EcologyCosts.DEFAULT;
        EcologyVitality restored=new EcologyVitality();restored.restoreHeader(EcologyCheckpoint.object(h.get("vitality")));
        for(int n=0;n<checkpoint.cells().size();n++)restored.restoreCell(checkpoint.cells().get(n).position,checkpoint.health().get(n));
        // Copy only after every page and every value has passed validation.
        costRules=restoredCosts;
        vitality.restoreHeader(restored.checkpointHeader());
        for(Cell c:checkpoint.cells())vitality.restoreCell(c.position,restored.checkpointCell(c.position));
        cells.clear();positions.clear();ownedChunks.clear();for(Cell c:checkpoint.cells())putCell(c.position,c);
        air.clear();airSet.clear();for(Pos p:checkpoint.air()){air.add(p);airSet.add(p);}
        claim=checkpoint.claim();generation++;
    }
    void writeCheckpoint(EcologyJournal target,String sourceHash)throws IOException {
        healthy();requireSetup();if(!indexed||vitalityTicks!=0)throw new IllegalStateException("Checkpoint requires a completed, flushed source index");
        List<EcologyJournal.Entry> original=journal.entries();int end=EcologyCheckpoint.prefixEnd(original);
        for(int n=0;n<=end;n++){var entry=original.get(n);target.append(entry.type(),entry.payload());}
        EcologyCheckpoint.write(target,this,List.copyOf(air),sourceHash,journal.revision());
    }
    boolean sameCheckpointState(EcologyEngine other){
        if(!Objects.equals(setup,other.setup)||indexed!=other.indexed||enabled!=other.enabled||cleanup!=other.cleanup||speedPercent!=other.speedPercent
                ||!cropRules.equals(other.cropRules)||!recoveryRules.equals(other.recoveryRules)||!costRules.equals(other.costRules)||!Objects.equals(claim,other.claim)||!cursors.equals(other.cursors)
                ||!cells().equals(other.cells())||!air.equals(other.air)||!vitality.checkpointHeader().equals(other.vitality.checkpointHeader()))return false;
        for(Pos p:cells.keySet())if(!vitality.checkpointCell(p).equals(other.vitality.checkpointCell(p)))return false;
        return true;
    }
    public Setup setup() { return setup; }
    public List<Cell> cells() { return List.copyOf(cells.values()); }
    public boolean owns(Pos p) { return cells.containsKey(p); }
    public boolean rooted(Pos p) { return connected.contains(p) && !unresolved && (graph==null||extending) && graphGeneration==generation; }
    public boolean enabled() { return enabled && failure==null; }
    public boolean living(Pos p) { return rooted(p)&&vitality.ready(p); }
    public EcologyVitality vitality(){return vitality;}
    public EcologyVitality.Summary vitalitySummary(){return vitalitySummary;}
    public boolean vitalityPending(){return vitalityPending;}
    public EcologyCrops.Rules cropRules(){return cropRules;}
    public EcologyRecovery.Rules recoveryRules(){return recoveryRules;}
    public EcologyCosts.Rules costRules(){return costRules;}
    // Extent retains its last completed bounded assessment during harmless connected additions.
    // Local power and certified topology are still checked; a cut/unload invalidates immediately.
    @Override public boolean recoveryAvailable(){return cropsAvailable()&&!vitality.extent().equals("Rooted");}
    @Override public boolean costsAvailable(){return recoveryAvailable();}
    @Override public boolean extensive(){return vitality.extent().equals("Extensive");}
    public void tuneRecovery(EcologyRecovery.Rules rules)throws IOException {
        healthy();requireSetup();if(!indexed)throw new IllegalStateException("Complete source indexing before changing local healing");
        Objects.requireNonNull(rules);if(rules.equals(recoveryRules))return;
        JsonObject p=new JsonObject();p.addProperty("enabled",rules.enabled());p.addProperty("seconds",rules.seconds());record("RECOVERY_RULES",p);
    }
    public void tuneCosts(EcologyCosts.Rules rules)throws IOException {
        healthy();requireSetup();if(!indexed)throw new IllegalStateException("Complete source indexing before changing local costs");
        Objects.requireNonNull(rules);if(rules.equals(costRules))return;
        JsonObject p=new JsonObject();p.add("hunger",costRule(rules.hunger()));p.add("darkness",costRule(rules.darkness()));record("COST_RULES",p);
    }
    @Override public Iterable<Pos> cropSources(){return positions::scan;}
    @Override public boolean cropsAvailable(){return failure==null&&enabled&&indexed&&!unresolved
            &&(graph==null||extending)&&graphGeneration==generation;}
    public void tuneCrops(EcologyCrops.Rules rules)throws IOException {
        healthy();requireSetup();if(!indexed)throw new IllegalStateException("Complete source indexing before changing crop growth");
        Objects.requireNonNull(rules);if(rules.equals(cropRules))return;
        JsonObject p=new JsonObject();p.addProperty("enabled",rules.enabled());p.addProperty("seconds",rules.seconds());record("CROP_RULES",p);
    }
    public void tuneVitality(EcologyVitality.Rules rules)throws IOException {
        healthy();requireSetup();if(!indexed)throw new IllegalStateException("Complete source indexing before changing vitality");
        Objects.requireNonNull(rules);if(rules.equals(vitality.rules()))return;
        flushVitality();record("VITALITY_RULES",EcologyVitality.rulesPayload(rules));resetVitalityScan();
    }
    public void flushVitality()throws IOException {
        if(vitalityTicks>0&&failure==null&&indexed){record("VITALITY",EcologyVitality.payload(vitalityTicks,new JsonArray(),-1));vitalityTicks=0;}
    }
    private void resetVitalityScan(){vitalityScan=null;vitalityPending=true;}
    public void chunkUnloaded(int x,int z)throws IOException {if(ownedChunks.containsKey(chunk(x,z))){flushVitality();invalidateConnections();}}
    private void putCell(Pos p,Cell cell){if(cells.put(p,cell)==null){positions.add(p);ownedChunks.merge(chunk(p.x()>>4,p.z()>>4),1,Integer::sum);}}
    public String failure() { return failure; }
    public Claim claim() { return claim; }
    public boolean cleanup() { return cleanup; }
    public int writeBudgetRemaining() { return MAX_WRITES-writes; }
    public int speedPercent() { return speedPercent; }
    public int attemptTicks(boolean bright) { return Math.max(1,Math.round((bright?10:20)*100f/speedPercent)); }
    public void speed(int percent)throws IOException {
        healthy();requireSetup();if(!indexed)throw new IllegalStateException("Complete source indexing before changing spread speed");
        if(!SPEEDS.contains(percent))throw new IllegalArgumentException("Choose a supported spread-speed multiplier");
        if(percent==speedPercent)return;
        JsonObject p=new JsonObject();p.addProperty("percent",percent);record("SPEED",p);clock=0;growthInterval=20;
    }
    public void noticeContact(Pos p) { if(pendingContacts.size()<MAX_CELLS)pendingContacts.add(p); }
    public void cleanup(boolean on) throws IOException {
        healthy();requireSetup();if(!indexed)throw new IllegalStateException("Complete source indexing first");
        JsonObject p=new JsonObject();p.addProperty("enabled",on);record("CLEANUP_MODE",p);
    }
    public void claim(String site,long revision,Area area) throws IOException {
        healthy();requireSetup();if(!indexed)throw new IllegalStateException("Complete source indexing first");
        if(claim!=null&&!claim.complete)throw new IllegalStateException("Finish or cancel the current region takeover first");
        JsonObject p=new JsonObject();p.addProperty("site",site);p.addProperty("revision",revision);p.add("min",pos(area.min));p.add("max",pos(area.max));record("CLAIM_START",p);
    }
    public void cancelClaim() throws IOException {healthy();if(claim!=null&&!claim.complete)record("CLAIM_DONE",new JsonObject());}
    /** Explicit metadata preparation. Preserve already-owned cells and their original history. */
    public void claimTick(EcologyTerrain w) throws IOException {
        healthy();if(claim==null||claim.complete)return;
        Area a=claim.area;
        for(int cx=a.min.x()>>4;cx<=a.max.x()>>4;cx++)for(int cz=a.min.z()>>4;cz<=a.max.z()>>4;cz++) {
            int size=chunkSize(a,cx,cz),cursor=claim.cursors.getOrDefault(chunk(cx,cz),0);
            if(cursor==size||!w.loaded(at(a,cx,cz,cursor)))continue;
            JsonArray adopted=new JsonArray(),fronts=new JsonArray();Set<Pos> addedAir=new HashSet<>();int reads=0;
            while(cursor<size&&reads++<INDEX_READS&&adopted.size()<64) {
                Pos p=at(a,cx,cz,cursor++);State s=w.get(p);if(!s.network()||owns(p))continue;
                if(cells.size()+adopted.size()>=MAX_CELLS)throw capacity("Region takeover exceeds owned-cell capacity");
                JsonObject c=new JsonObject();c.add("p",pos(p));c.add("after",state(s));c.add("cleanup",state(AIR));adopted.add(c);
                for(int f=0;f<6;f++){Pos air=p.offset(f);if(w.loaded(air)&&w.get(air).air()&&!airSet.contains(air)&&addedAir.add(air))fronts.add(pos(air));}
            }
            JsonObject p=new JsonObject();p.addProperty("chunkX",cx);p.addProperty("chunkZ",cz);p.addProperty("cursor",cursor);p.add("cells",adopted);p.add("air",fronts);record("CLAIM_SCAN",p);return;
        }
        boolean done=true;
        for(int cx=a.min.x()>>4;cx<=a.max.x()>>4;cx++)for(int cz=a.min.z()>>4;cz<=a.max.z()>>4;cz++)
            done&=claim.cursors.getOrDefault(chunk(cx,cz),0)==chunkSize(a,cx,cz);
        if(done)record("CLAIM_DONE",new JsonObject());
    }
    /** Never delete a contact waiting to join the living network. No distant patch is protected. */
    public boolean purge(EcologyTerrain w,Pos p,boolean playing) throws IOException {
        healthy();if(!cleanup||!playing||owns(p)||!w.loaded(p))return false;
        State before=w.get(p);if(!before.network())return false;
        if((heart!=null&&EcologyTerrain.connected(heart,SCULK,p,before))||touchesRooted(p,before)){noticeContact(p);return false;}
        if(writes>=MAX_WRITES)return false;
        JsonObject payload=new JsonObject();payload.add("p",pos(p));payload.add("before",state(before));record("PURGE",payload);writes++;
        if(!w.set(p,AIR)) {cleanup(false);if(enabled)mode(false);fail("A journaled sculk cleanup write failed; cleanup disabled. Inspect the world before continuing.");throw new IOException(failure);}
        return true;
    }
    public void fail(String reason) { if(failure==null) { failure=Objects.requireNonNull(reason); controlRevision++; } }
    /** A reversible missing/unloaded Heart is not a journal failure. Recertify after it returns. */
    public void invalidateConnections() { unresolved=true;generation++;controlRevision++;contactScan=null;resetVitalityScan(); }

    /** Geometry comes from Core's exact saved-site revisions, never a second editable geometry store. */
    public void attach(Setup pins, Area source, Pos heart) {
        if(!Objects.equals(setup,pins)) throw new IllegalStateException("Map assignments changed; ecology setup pins no longer match");
        if(!source.contains(heart)) throw new IllegalArgumentException("Relic Heart must be within the source city");
        if(source.equals(area)&&heart.equals(this.heart))return;
        this.area=source; this.heart=heart;
        for(var e:cursors.entrySet()) {
            int cx=(int)(e.getKey()>>32),cz=(int)(long)e.getKey();
            if(cx<(area.min.x()>>4)||cx>(area.max.x()>>4)||cz<(area.min.z()>>4)||cz>(area.max.z()>>4)||e.getValue()>chunkSize(cx,cz))
                throw new IllegalArgumentException("Index cursor does not match pinned source geometry");
        }
        if(indexed) {
            for(int cx=area.min.x()>>4;cx<=area.max.x()>>4;cx++) for(int cz=area.min.z()>>4;cz<=area.max.z()>>4;cz++)
                if(cursors.getOrDefault(chunk(cx,cz),0)!=chunkSize(cx,cz))throw bad("Completed source index has missing or partial chunks");
        }
        for(Cell c:cells.values())if(c.adopted&&!area.contains(c.position))throw bad("Adopted source cell outside pinned region");
        Map<Long,Integer> checked=new HashMap<>();
        for(var entry:journal.entries())if(entry.type().equals("INDEX")) {
            JsonObject p=entry.payload();int cx=integer(p,"chunkX"),cz=integer(p,"chunkZ"),end=integer(p,"cursor");
            int start=checked.getOrDefault(chunk(cx,cz),0);
            if(cx<(area.min.x()>>4)||cx>(area.max.x()>>4)||cz<(area.min.z()>>4)||cz>(area.max.z()>>4)||end>chunkSize(cx,cz))throw bad("Index history outside pinned region");
            for(JsonElement cell:array(p,"cells",64)){
                Pos at=position(object(cell).get("p"));
                int offset=sourceOffset(cx,cz,at);
                if(offset<start||offset>=end)throw bad("Adopted cell outside scanned interval");
            }
            checked.put(chunk(cx,cz),end);
        }
    }
    public void configure(Setup pins, Area source, Pos heart) throws IOException {
        healthy();
        if(setup!=null) throw new IllegalStateException("Ecology is already configured; existing ownership cannot be reset here");
        if(!source.contains(heart)) throw new IllegalArgumentException("Relic Heart must be inside the source city");
        JsonObject p=new JsonObject(); p.addProperty("city",pins.city); p.addProperty("cityRevision",pins.cityRevision);
        p.addProperty("heart",pins.heart); p.addProperty("heartRevision",pins.heartRevision); p.addProperty("dimension",pins.dimension);
        record("CONFIGURE",p); attach(pins,source,heart);
    }
    public void mode(boolean on) throws IOException {
        healthy();requireSetup();
        if(on && (!indexed||area==null||cells.isEmpty())) throw new IllegalStateException("Finish indexing a non-empty source city first");
        flushVitality();
        JsonObject p=new JsonObject(); p.addProperty("enabled",on); record("MODE",p); clock=0;
    }
    public View view() {
        int done=0,total=0;
        if(area!=null) for(int cx=area.min.x()>>4;cx<=area.max.x()>>4;cx++) for(int cz=area.min.z()>>4;cz<=area.max.z()>>4;cz++) {
            total++; if(cursors.getOrDefault(chunk(cx,cz),0)==chunkSize(cx,cz)) done++;
        }
        boolean uncertain=unresolved||graph!=null||graphGeneration!=generation;
        String extent=uncertain?"Unresolved":connected.isEmpty()?"Disconnected":vitality.extent();
        return new View(setup!=null,indexed,enabled,failure,cells.size(),connected.size(),air.size(),done,total,
                uncertain,extent,controlRevision,journal.bytes());
    }
    /** Metadata only. Explicitly initiated by configure; no world writes or force loading. */
    public void index(EcologyTerrain w) throws IOException {
        healthy(); if(area==null||indexed) return;
        int total=((area.max.x()>>4)-(area.min.x()>>4)+1)*((area.max.z()>>4)-(area.min.z()>>4)+1);
        for(int turn=0;turn<total;turn++) {
            int n=Math.floorMod(chunkTurn++,total), width=(area.max.z()>>4)-(area.min.z()>>4)+1;
            int cx=(area.min.x()>>4)+n/width,cz=(area.min.z()>>4)+n%width;
            int cursor=cursors.getOrDefault(chunk(cx,cz),0),size=chunkSize(cx,cz);
            if(cursor==size) continue;
            Pos first=at(cx,cz,cursor); if(!w.loaded(first)) continue;
            JsonArray adopted=new JsonArray(), fronts=new JsonArray();
            Set<Pos> newAir=new HashSet<>();
            int read=0;
            while(cursor<size && read++<INDEX_READS && adopted.size()<64) {
                Pos p=at(cx,cz,cursor++); State s=w.get(p);
                if(!s.network()||cells.containsKey(p)) continue;
                if(cells.size()+adopted.size()>=MAX_CELLS) throw capacity("Source city exceeds owned-cell capacity");
                JsonObject cell=new JsonObject(); cell.add("p",pos(p)); cell.add("after",state(s)); cell.add("cleanup",state(AIR)); adopted.add(cell);
                for(int f=0;f<6;f++) {
                    Pos a=p.offset(f);
                    if(w.loaded(a) && w.get(a).air() && airSet.size()+newAir.size()<MAX_AIR && !airSet.contains(a) && newAir.add(a)) fronts.add(pos(a));
                }
            }
            JsonObject payload=new JsonObject(); payload.addProperty("chunkX",cx);payload.addProperty("chunkZ",cz);payload.addProperty("cursor",cursor);
            payload.add("cells",adopted);payload.add("air",fronts); record("INDEX",payload);
            return;
        }
        if(view().completedChunks==total) record("INDEX_COMPLETE",new JsonObject());
    }

    /** One occupied-play tick. Call beginTick before native world ticks for shared mutation limits. */
    public void beginTick() { writes=0; }
    public void tick(EcologyTerrain w,boolean playing) throws IOException {
        if(!playing||!enabled()||!indexed||area==null) { flushVitality();clock=0;growthInterval=20;return; }
        reconcile(w);
        topology(w);
        if(unresolved || graph!=null) {flushVitality();resetVitalityScan();return;}
        vitalityTick(w);
        projectAppearance(w);
        discoverExposure(w);
        connectPlacedSculk(w);
        if(graph!=null || connected.isEmpty()) return;
        if(writes>=MAX_WRITES)return; // A busy native tick waits; it is not a persistence/capacity fault.
        if(++clock<attemptTicks(growthInterval==10)) return;
        clock=0;
        grow(w);
    }
    private void projectAppearance(EcologyTerrain w) {
        if(appearanceScan==null||!appearanceScan.hasNext())appearanceScan=positions.scan();
        int changed=0;
        for(int n=0;n<EcologyAppearance.READS&&appearanceScan.hasNext()&&changed<EcologyAppearance.WRITES;n++) {
            Pos p=appearanceScan.next();Cell c=cells.get(p);if(c==null||!w.loaded(p))continue;
            int stage=EcologyAppearance.stage(vitality.value(p),rooted(p),vitality.mode(p));
            if(stage>=0&&w.appearance(p,c.after,stage))changed++;
        }
    }
    private void vitalityTick(EcologyTerrain w)throws IOException {
        vitalityTicks++;
        if(vitalityScan==null){vitalityScan=positions.scan();vRooted=vReady=vDetached=vFeeding=vSealed=vUnknown=0;vRootedHealth=vDetachedHealth=0;}
        JsonArray changes=new JsonArray();List<Pos> checked=new ArrayList<>();
        for(int n=0;n<EcologyVitality.WORK&&vitalityScan.hasNext();n++){
            Pos p=vitalityScan.next();if(!owns(p))continue;EcologyVitality.Mode next=feedingMode(w,p);checked.add(p);
            if(vitality.mode(p)!=next)changes.add(EcologyVitality.change(p,next));
        }
        if(!changes.isEmpty()||vitalityTicks>=EcologyVitality.CHECKPOINT){record("VITALITY",EcologyVitality.payload(vitalityTicks,changes,-1));vitalityTicks=0;}
        for(Pos p:checked){var mode=vitality.mode(p);int value=vitality.value(p);
            if(rooted(p)){vRooted++;vRootedHealth+=value;if(vitality.ready(p))vReady++;}else{vDetached++;vDetachedHealth+=value;}
            if(mode==EcologyVitality.Mode.SHADED||mode==EcologyVitality.Mode.LIT)vFeeding++;
            if(mode==EcologyVitality.Mode.SEALED)vSealed++;if(mode==EcologyVitality.Mode.UNKNOWN)vUnknown++;
        }
        if(!vitalityScan.hasNext()){
            vitalitySummary=new EcologyVitality.Summary(vRooted,vReady,vDetached,vFeeding,vSealed,vUnknown,vRootedHealth,vDetachedHealth);
            vitalityPending=vUnknown>0;vitalityScan=null;
            if(!vitalityPending&&!EcologyVitality.nextExtent(vitality.extent(),vReady,vitality.rules()).equals(vitality.extent())){
                record("VITALITY",EcologyVitality.payload(vitalityTicks,new JsonArray(),vReady));vitalityTicks=0;
            }
        }
    }
    private EcologyVitality.Mode feedingMode(EcologyTerrain w,Pos p){
        if(!rooted(p))return EcologyVitality.Mode.DORMANT;
        boolean exposed=false,unknown=false;
        for(int f=0;f<6;f++){
            Pos a=localPosition(p,DIRECTIONS[f][0],DIRECTIONS[f][1],DIRECTIONS[f][2]);if(a==null){unknown=true;continue;}
            if(!w.loaded(a)){unknown=true;continue;}State s=w.get(a);
            if(s.air()||(owns(a)&&s.vein()&&!"true".equals(s.properties().get("waterlogged")))){exposed=true;if(w.light(a)>7)return EcologyVitality.Mode.LIT;}
        }
        return exposed?EcologyVitality.Mode.SHADED:unknown?EcologyVitality.Mode.UNKNOWN:EcologyVitality.Mode.SEALED;
    }
    /** Existing sculk joins only through real contact with the Heart/certified living network.
     * Read at most 33 x 18 neighbour positions and append at most MAX_WRITES per tick.
     * No terrain is changed; the explicit cleanup baseline does not invent prior terrain. */
    private void connectPlacedSculk(EcologyTerrain w) throws IOException {
        if(heart==null||!w.loaded(heart))return;
        connectNeighbours(w,heart,SCULK);
        for(int n=0;n<32&&!pendingContacts.isEmpty()&&writes<MAX_WRITES;n++) {
            Pos p=pendingContacts.iterator().next();pendingContacts.remove(p);
            if(owns(p)||!w.loaded(p))continue;State actual=w.get(p);
            if(actual.network()&&((EcologyTerrain.connected(heart,SCULK,p,actual))||touchesRooted(p,actual)))connect(w,p,actual);
        }
        if(contactScan==null||!contactScan.hasNext())contactScan=positions.scan();
        for(int n=0;n<32&&contactScan.hasNext()&&writes<MAX_WRITES;n++) {
            Pos p=contactScan.next();Cell c=cells.get(p);
            if(c!=null&&rooted(p)&&w.loaded(p))connectNeighbours(w,p,c.after);
        }
    }
    private void connectNeighbours(EcologyTerrain w,Pos origin,State originState) throws IOException {
        for(int dx=-1;dx<=1&&writes<MAX_WRITES;dx++)for(int dy=-1;dy<=1&&writes<MAX_WRITES;dy++)for(int dz=-1;dz<=1&&writes<MAX_WRITES;dz++) {
            int distance=Math.abs(dx)+Math.abs(dy)+Math.abs(dz);if(distance==0||distance>2)continue;
            Pos p=origin.add(dx,dy,dz);
            if(p.equals(heart)||cells.containsKey(p)||!w.loaded(p))continue;
            State actual=w.get(p);
            if(!actual.network()||!EcologyTerrain.connected(origin,originState,p,actual))continue;
            connect(w,p,actual);
        }
    }
    private void connect(EcologyTerrain w,Pos p,State actual) throws IOException {
            if(cells.size()>=MAX_CELLS)throw capacity("Owned-cell capacity reached");
            JsonArray fronts=new JsonArray();
            for(int f=0;f<6;f++) {Pos a=p.offset(f);if(w.loaded(a)&&w.get(a).air())fronts.add(pos(a));}
            if(air.size()+fronts.asList().stream().map(EcologyEngine::position).filter(a->!airSet.contains(a)).distinct().count()>MAX_AIR)
                throw capacity("Surface frontier capacity reached");
            JsonObject payload=new JsonObject();payload.add("p",pos(p));payload.add("after",state(actual));payload.add("cleanup",state(AIR));payload.add("air",fronts);
            record("CONNECT",payload);writes++;
    }
    private void reconcile(EcologyTerrain w) throws IOException {
        if(validation==null) {
            validation=positions.scan(); validationUnknown=false;
        }
        for(int n=0;n<GRAPH_WORK&&validation.hasNext();n++) {
            Pos p=validation.next(); Cell c=cells.get(p); if(c==null) continue;
            if(!w.loaded(p)) {
                validationUnknown=true;
                if(!unresolved){unresolved=true;generation++;controlRevision++;}
                continue;
            }
            State actual=w.get(p);
            observed(p,actual);
        }
        if(!validation.hasNext()) {
            validation=null;
            if(!validated||unresolved!=validationUnknown){generation++;controlRevision++;}
            validated=true;unresolved=validationUnknown;
        }
    }
    private void discoverExposure(EcologyTerrain w)throws IOException {
        if(exposureScan==null||!exposureScan.hasNext())exposureScan=positions.scan();
        Set<Pos> fresh=new LinkedHashSet<>();
        for(int n=0;n<32&&exposureScan.hasNext()&&fresh.size()<64;n++) {
            Pos p=exposureScan.next();if(!rooted(p)||!w.loaded(p))continue;
            for(Pos a:surfaceNeighbours(w,p,List.of()))if(fresh.size()<64&&!airSet.contains(a))fresh.add(a);
        }
        if(fresh.isEmpty())return;
        if(air.size()+fresh.size()>MAX_AIR)throw capacity("Surface frontier capacity reached");
        JsonObject payload=new JsonObject();JsonArray discovered=new JsonArray();fresh.forEach(p->discovered.add(pos(p)));payload.add("air",discovered);record("FRONTIER",payload);
    }
    private void topology(EcologyTerrain w) {
        if(graph==null && graphGeneration==generation) return;
        if(graph==null || graphGeneration!=generation) {
            graphGeneration=generation; graph=new ArrayDeque<>(); visiting=new HashSet<>(); graphUnknown=false;extending=false;
            if(heart==null||!w.loaded(heart)) { graphUnknown=true; }
            else for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
                int distance=Math.abs(dx)+Math.abs(dy)+Math.abs(dz);if(distance==0||distance>2)continue;
                Pos p=heart.add(dx,dy,dz); Cell c=cells.get(p);
                if(c!=null && EcologyTerrain.connected(heart,SCULK,p,c.after) && visiting.add(p)) graph.add(p);
            }
        }
        for(int n=0;n<GRAPH_WORK&&!graph.isEmpty();n++) {
            Pos p=graph.removeFirst(); Cell c=cells.get(p); if(c==null) continue;
            if(!w.loaded(p)) { graphUnknown=true;if(!unresolved){unresolved=true;controlRevision++;}continue; }
            for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) {
                int distance=Math.abs(dx)+Math.abs(dy)+Math.abs(dz); if(distance==0||distance>2) continue;
                Pos q=p.add(dx,dy,dz); Cell other=cells.get(q);
                if(other!=null&&!visiting.contains(q)&&EcologyTerrain.connected(p,c.after,q,other.after)) { visiting.add(q);graph.add(q); }
            }
        }
        if(graph.isEmpty()) {
            boolean unknown=graphUnknown||validationUnknown||!validated;
            if(!unknown)connected=visiting;
            graph=null;extending=false;
            if(unresolved!=unknown) { unresolved=unknown;controlRevision++; }
        }
    }
    private void grow(EcologyTerrain w) throws IOException {
        if(air.isEmpty()) return;
        // Fixed work: at most 8 air seeds, 7 target cells, 6 backing faces.
        // Independent choices break the insertion-order/direction feedback that painted long rows.
        // Persisted outcomes remain authoritative; future attempts need no new journal schema.
        SplittableRandom random=new SplittableRandom(setup.hashCode()*0x9E3779B97F4A7C15L
                ^journal.revision()^++growthTurn*0xD1B54A32D192ED03L);
        int[] steps={-1,0,1,2,3,4,5},faces={0,1,2,3,4,5};
        int reads=0;
        for(int tries=0;tries<8;tries++) {
            Pos from=air.get(random.nextInt(air.size()));
            if(!passable(w,from)||!rootedExposure(from)) continue;
            shuffle(random,steps);
            for(int step:steps) {
                Pos a=step<0?from:from.offset(step);
                if(!passable(w,a)) continue;
                State airState=w.get(a);
                if(airState.air()&&cells.size()>=64&&cells.size()%32==0&&feature(w,a,airState))return;
                // Both darkness and light permit growth; lit exposure also shortens the next interval.
                shuffle(random,faces);
                for(int face:faces) {
                    if(++reads>256) return;
                    Pos backing=a.offset(face);
                    if(!w.loaded(backing)||cells.containsKey(backing)||!w.solidFace(backing,face^1)) continue;
                    State original=w.get(backing);
                    if(original.network()) continue; // unrelated sculk is never silently adopted
                    if(w.substrate(backing)) {
                        if(touchesLiving(backing,SCULK)) { change(w,backing,original,SCULK,List.of(a));growthInterval=w.light(a)>7?10:20;return; }
                    } else if(airState.air() || (owns(a)&&airState.vein())) {
                        State next=airState.vein()?airState.with(FACES[face],"true"):EcologyTerrain.vein(face);
                        if(!next.equals(airState)&&touchesLiving(a,next)) { change(w,a,airState,next,List.of(a));growthInterval=w.light(a)>7?10:20;return; }
                    }
                }
            }
        }
    }
    private static void shuffle(SplittableRandom random,int[] values) {
        for(int i=values.length-1;i>0;i--){int j=random.nextInt(i+1),v=values[i];values[i]=values[j];values[j]=v;}
    }
    private boolean feature(EcologyTerrain w,Pos a,State before) throws IOException {
        Pos below=a.offset(0);Cell support=cells.get(below);
        if(vitalityPending||support==null||!support.after.id().equals("minecraft:sculk")||!living(below))return false;
        // Finite spacing query; unrelated nearby features also occupy their physical space.
        for(int dx=-6;dx<=6;dx++)for(int dy=-3;dy<=3;dy++)for(int dz=-6;dz<=6;dz++) {
            Cell c=cells.get(a.add(dx,dy,dz));
            if(c!=null&&!c.after.vein()&&!c.after.id().equals("minecraft:sculk"))return false;
        }
        String id=vitality.extent().equals("Extensive")&&cells.size()%128==0?"minecraft:sculk_shrieker":
                !vitality.extent().equals("Rooted")&&cells.size()%96==0?"minecraft:sculk_catalyst":"minecraft:sculk_sensor";
        Map<String,String> properties=switch(id) {
            case "minecraft:sculk_shrieker" -> Map.of("can_summon","true","shrieking","false","waterlogged","false");
            case "minecraft:sculk_catalyst" -> Map.of("bloom","false");
            default -> Map.of("sculk_sensor_phase","inactive","power","0","waterlogged","false");
        };
        State next=new State(id,properties);
        change(w,a,before,next,List.of(a));return true;
    }
    private boolean rootedExposure(Pos a) {
        if(living(a)) return true;
        for(int f=0;f<6;f++) if(living(a.offset(f))) return true;
        return false;
    }
    private boolean touchesRooted(Pos p,State s) {
        return touches(p,s,false);
    }
    private boolean touchesLiving(Pos p,State s) {return touches(p,s,true);}
    private boolean touches(Pos p,State s,boolean requireVitality) {
        for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) {
            if(Math.abs(dx)+Math.abs(dy)+Math.abs(dz)>2) continue;
            Pos q=p.add(dx,dy,dz);Cell c=cells.get(q);
            if(c!=null&&connected.contains(q)&&(!requireVitality||vitality.ready(q))&&EcologyTerrain.connected(p,s,q,c.after)) return true;
        }
        return false;
    }
    private boolean passable(EcologyTerrain w,Pos p) {
        if(!w.loaded(p)) return false;
        State s=w.get(p);return s.air() || (s.vein()&&owns(p)&&!"true".equals(s.properties().get("waterlogged")));
    }
    private void change(EcologyTerrain w,Pos p,State before,State after,List<Pos> frontier) throws IOException {
        Set<Pos> exposed=new LinkedHashSet<>(frontier);exposed.addAll(surfaceNeighbours(w,p,frontier));
        intend(p,before,after,List.copyOf(exposed));
        if(!w.set(p,after)) {
            mode(false);fail("A journaled ecology world write failed; growth was disabled, inspect before continuing");throw new IOException(failure);
        }
    }
    /** A local air path may turn a convex corner, never pass through a sealed wall.
     * At most 27 cached loaded-only reads; discovery checks at most 32 owned cells/tick. */
    private List<Pos> surfaceNeighbours(EcologyTerrain w,Pos p,List<Pos> seeds) {
        Map<Pos,Boolean> open=new HashMap<>();Set<Pos> candidates=new HashSet<>();
        for(int face=-1;face<6;face++) {
            int[] d=face<0?new int[]{0,0,0}:DIRECTIONS[face];Pos a=localPosition(p,d[0],d[1],d[2]);
            if(a!=null&&!airSet.contains(a)&&localOpen(w,a,open))candidates.add(a);
        }
        if(candidates.isEmpty())return List.of();
        ArrayDeque<Pos> queue=new ArrayDeque<>();Set<Pos> visited=new HashSet<>();
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
            Pos a=localPosition(p,dx,dy,dz);if(a==null)continue;
            if((seeds.contains(a)||(airSet.contains(a)&&rootedExposure(a)))&&localOpen(w,a,open)&&visited.add(a))queue.add(a);
        }
        while(!queue.isEmpty()) {
            Pos a=queue.removeFirst();
            for(int[] d:DIRECTIONS) {
                int dx=a.x()-p.x()+d[0],dy=a.y()-p.y()+d[1],dz=a.z()-p.z()+d[2];
                if(Math.abs(dx)>1||Math.abs(dy)>1||Math.abs(dz)>1)continue;
                Pos b=localPosition(p,dx,dy,dz);if(b!=null&&!visited.contains(b)&&localOpen(w,b,open)){visited.add(b);queue.add(b);}
            }
        }
        return candidates.stream().filter(visited::contains).sorted(Comparator.comparingInt(Pos::x).thenComparingInt(Pos::y).thenComparingInt(Pos::z)).toList();
    }
    private static Pos localPosition(Pos p,int dx,int dy,int dz) {
        long x=(long)p.x()+dx,y=(long)p.y()+dy,z=(long)p.z()+dz;
        return Math.abs(x)>30_000_000||Math.abs(z)>30_000_000||Math.abs(y)>4096?null:new Pos((int)x,(int)y,(int)z);
    }
    private boolean localOpen(EcologyTerrain w,Pos p,Map<Pos,Boolean> cache) {
        return cache.computeIfAbsent(p,a->{if(!w.loaded(a))return false;State s=w.get(a);return s.air()||(owns(a)&&s.vein()&&!"true".equals(s.properties().get("waterlogged")));});
    }
    private void intend(Pos p,State before,State after,List<Pos> frontier) throws IOException {
        healthy();
        if(writes>=MAX_WRITES) throw new IllegalStateException("Ecology tick write budget exhausted");
        if(!cells.containsKey(p)&&cells.size()>=MAX_CELLS) throw capacity("Owned-cell capacity reached");
        if(air.size()+frontier.stream().filter(a->!airSet.contains(a)).distinct().count()>MAX_AIR) throw capacity("Surface frontier capacity reached");
        JsonObject payload=new JsonObject();payload.add("p",pos(p));payload.add("before",state(before));payload.add("after",state(after));
        JsonArray fronts=new JsonArray();frontier.forEach(a->fronts.add(pos(a)));payload.add("air",fronts);
        record("GROW",payload);writes++;
    }
    /** Catalyst hook: authorize and journal a descendant before the native mutation. */
    public boolean nativeChange(EcologyTerrain w,Pos origin,Pos p,State before,State after) throws IOException {
        if(!enabled()||!living(origin)||writes>=MAX_WRITES||!after.network()||!w.loaded(origin)||!w.loaded(p)) return false;
        Cell current=cells.get(p);
        if(current!=null && before.sameOwnedShape(after)) return true;
        if(after.id().equals("minecraft:sculk_catalyst")&&(vitalityPending||vitality.extent().equals("Rooted")))return false;
        if(current==null&&!after.id().equals("minecraft:sculk")&&!before.air())return false;
        if(after.id().equals("minecraft:sculk_shrieker")&&(vitalityPending||!vitality.extent().equals("Extensive"))) return false;
        // A descendant must occupy a discovered air surface, or its immediately exposed backing.
        boolean exposed=airSet.contains(p)&&passable(w,p)&&rootedExposure(p);
        List<Pos> fronts=new ArrayList<>();
        if(exposed) fronts.add(p);
        for(int f=0;f<6;f++) {
            Pos a=p.offset(f);
            if(airSet.contains(a)&&passable(w,a)&&rootedExposure(a)) { exposed=true;fronts.add(a); }
        }
        if(!exposed||(!cells.containsKey(p)&&before.network())||!touchesLiving(p,after)) return false;
        if(after.id().equals("minecraft:sculk")&&!w.substrate(p)&&current==null) return false;
        intend(p,before,after,fronts);return true;
    }
    /** Native loss is an observation of already-journaled terrain, sharing the mutation budget. */
    public boolean reserveNativeLoss(){if(!enabled()||writes>=MAX_WRITES)return false;writes++;return true;}
    /** Outside writes prune ownership immediately. Physical contact scans reconnect placed sculk. */
    public void observed(Pos p,State actual) throws IOException {
        Cell c=cells.get(p);
        if(c==null||c.after.sameOwnedShape(actual))return;
        if(c.after.vein()&&actual.vein()) {
            boolean any=false,added=false;
            for(int f=0;f<6;f++){any|=actual.face(f);added|=actual.face(f)&&!c.after.face(f);}
            if(any&&!added) {
                JsonObject payload=new JsonObject();payload.add("p",pos(p));payload.add("after",state(actual));record("OBSERVE",payload);return;
            }
        }
        remove(p);
    }
    public void remove(Pos p) throws IOException {
        if(!cells.containsKey(p)) return;
        JsonObject payload=new JsonObject();payload.add("p",pos(p));record("REMOVE",payload);
    }
    private IllegalStateException capacity(String message) { fail(message+"; expansion held");return new IllegalStateException(failure); }
    private void healthy() { if(failure!=null) throw new IllegalStateException(failure); }
    private void record(String type,JsonObject payload) throws IOException {
        try { validate(type,payload);journal.append(type,payload); apply(type,payload); }
        catch(IOException|RuntimeException e) { fail("Ecology persistence/validation fault: "+e.getMessage()); throw e; }
    }

    private void replay(String type,JsonObject p) {
        validate(type,p);apply(type,p);
    }
    /** Validate the entire transition before any durable publication or in-memory mutation. */
    private void validate(String type,JsonObject p) {
        switch(type) {
            case "CONFIGURE" -> {
                keys(p,"city","cityRevision","heart","heartRevision","dimension");if(setup!=null)throw bad("Duplicate configuration");
                new Setup(string(p,"city"),number(p,"cityRevision"),string(p,"heart"),number(p,"heartRevision"),string(p,"dimension"));
            }
            case "INDEX" -> {
                requireSetup();if(indexed)throw bad("Index after completion");keys(p,"chunkX","chunkZ","cursor","cells","air");
                int cx=integer(p,"chunkX"),cz=integer(p,"chunkZ"),cursor=integer(p,"cursor"),prior=cursors.getOrDefault(chunk(cx,cz),0);
                if(Math.abs((long)cx)>1_875_000||Math.abs((long)cz)>1_875_000||cursor<=prior||cursor-prior>INDEX_READS||cursor>16_777_216)throw bad("Invalid index cursor");
                Set<Pos> seen=new HashSet<>();JsonArray entries=array(p,"cells",64);
                if(cells.size()+entries.size()>MAX_CELLS)throw bad("Too many owned cells");
                for(JsonElement entry:entries) {
                    JsonObject c=object(entry);keys(c,"p","after","cleanup");Pos at=position(c.get("p"));
                    State after=block(c.get("after")),cleanup=block(c.get("cleanup"));
                    if(!after.network()||!cleanup.equals(AIR)||cells.containsKey(at)||!seen.add(at)||(at.x()>>4)!=cx||(at.z()>>4)!=cz)throw bad("Invalid adopted cell");
                }
                validateFronts(array(p,"air",384));
            }
            case "INDEX_COMPLETE" -> {requireSetup();keys(p);if(indexed)throw bad("Duplicate index completion");}
            case "MODE" -> {requireSetup();keys(p,"enabled");if(bool(p,"enabled")&&(!indexed||cells.isEmpty()))throw bad("Enabled incomplete network");}
            case "GROW" -> {
                requireSetup();if(!indexed||!enabled)throw bad("Growth before enable");keys(p,"p","before","after","air");
                Pos at=position(p.get("p"));State before=block(p.get("before")),after=block(p.get("after"));Cell old=cells.get(at);
                if(!after.network()||(old==null&&(before.network()||cells.size()>=MAX_CELLS)))throw bad("Invalid grown cell");
                if(old!=null&&!old.after.sameOwnedShape(before))throw bad("Discontinuous owned-cell transition");
                validateFronts(array(p,"air",12));
            }
            case "CONNECT" -> {
                requireSetup();if(!indexed||!enabled)throw bad("Connection before enable");keys(p,"p","after","cleanup","air");
                Pos at=position(p.get("p"));
                if(cells.containsKey(at)||cells.size()>=MAX_CELLS||!block(p.get("after")).network()||!block(p.get("cleanup")).equals(AIR))throw bad("Invalid connected cell");
                validateFronts(array(p,"air",6));
            }
            case "SPEED" -> {requireSetup();keys(p,"percent");if(!indexed||!SPEEDS.contains(integer(p,"percent")))throw bad("Invalid spread speed");}
            case "VITALITY_RULES" -> {requireSetup();if(!indexed)throw bad("Vitality tuning before indexing");EcologyVitality.parseRules(p);}
            case "CROP_RULES" -> {requireSetup();if(!indexed)throw bad("Crop tuning before indexing");keys(p,"enabled","seconds");new EcologyCrops.Rules(bool(p,"enabled"),integer(p,"seconds"));}
            case "RECOVERY_RULES" -> {requireSetup();if(!indexed)throw bad("Recovery tuning before indexing");keys(p,"enabled","seconds");new EcologyRecovery.Rules(bool(p,"enabled"),integer(p,"seconds"));}
            case "COST_RULES" -> {requireSetup();if(!indexed)throw bad("Cost tuning before indexing");costRules(p);}
            case "VITALITY" -> {requireSetup();if(!indexed||!enabled)throw bad("Vitality before enable");vitality.validate(p,cells::containsKey);}
            case "FRONTIER" -> {requireSetup();keys(p,"air");if(!indexed||!enabled)throw bad("Exposure discovery before enable");validateFronts(array(p,"air",64));}
            case "CLEANUP_MODE" -> {requireSetup();keys(p,"enabled");bool(p,"enabled");if(!indexed)throw bad("Cleanup before indexing");}
            case "PURGE" -> {requireSetup();keys(p,"p","before");if(!cleanup||owns(position(p.get("p")))||!block(p.get("before")).network())throw bad("Invalid cleanup intent");}
            case "CLAIM_START" -> {
                requireSetup();keys(p,"site","revision","min","max");
                if(!indexed||(claim!=null&&!claim.complete)||!string(p,"site").matches("[a-z][a-z0-9_]{0,63}(?::[a-z][a-z0-9_]{0,63})?")||number(p,"revision")<1)throw bad("Invalid takeover start");
                new Area(position(p.get("min")),position(p.get("max")));
            }
            case "CLAIM_SCAN" -> {
                keys(p,"chunkX","chunkZ","cursor","cells","air");if(claim==null||claim.complete)throw bad("No running takeover");
                int cx=integer(p,"chunkX"),cz=integer(p,"chunkZ"),end=integer(p,"cursor"),start=claim.cursors.getOrDefault(chunk(cx,cz),0);
                Area a=claim.area;
                if(cx<(a.min.x()>>4)||cx>(a.max.x()>>4)||cz<(a.min.z()>>4)||cz>(a.max.z()>>4)||end<=start||end-start>INDEX_READS||end>chunkSize(a,cx,cz))throw bad("Invalid takeover cursor");
                Set<Pos> scanned=new HashSet<>(),seen=new HashSet<>();for(int n=start;n<end;n++)scanned.add(at(a,cx,cz,n));
                JsonArray entries=array(p,"cells",64);if(cells.size()+entries.size()>MAX_CELLS)throw bad("Too many takeover cells");
                for(JsonElement item:entries){JsonObject c=object(item);keys(c,"p","after","cleanup");Pos at=position(c.get("p"));if(!scanned.contains(at)||owns(at)||!seen.add(at)||!block(c.get("after")).network()||!block(c.get("cleanup")).equals(AIR))throw bad("Invalid takeover cell");}
                validateFronts(array(p,"air",384));
            }
            case "CLAIM_DONE" -> {keys(p);if(claim==null||claim.complete)throw bad("No running takeover");}
            case "REMOVE" -> {requireSetup();keys(p,"p");if(!cells.containsKey(position(p.get("p"))))throw bad("Unknown removed cell");}
            case "OBSERVE" -> {
                requireSetup();keys(p,"p","after");Cell c=cells.get(position(p.get("p")));State after=block(p.get("after"));
                if(c==null||!c.after.vein()||!after.vein())throw bad("Invalid vein observation");
                boolean any=false;for(int f=0;f<6;f++){any|=after.face(f);if(after.face(f)&&!c.after.face(f))throw bad("Observation cannot adopt new faces");}
                if(!any)throw bad("Observation has no owned faces");
            }
            default -> throw bad("Unsupported ecology payload type: "+type);
        }
    }
    private void validateFronts(JsonArray fronts) {
        Set<Pos> additional=new HashSet<>();for(JsonElement e:fronts){Pos a=position(e);if(!airSet.contains(a))additional.add(a);}
        if(air.size()+additional.size()>MAX_AIR)throw bad("Too many surface cells");
    }
    private void apply(String type,JsonObject p) {
        switch(type) {
            case "CONFIGURE" -> {
                keys(p,"city","cityRevision","heart","heartRevision","dimension");
                if(setup!=null) throw bad("Duplicate configuration");
                setup=new Setup(string(p,"city"),number(p,"cityRevision"),string(p,"heart"),number(p,"heartRevision"),string(p,"dimension"));
            }
            case "INDEX" -> {
                requireSetup(); if(indexed) throw bad("Index after completion");keys(p,"chunkX","chunkZ","cursor","cells","air");
                int cx=integer(p,"chunkX"),cz=integer(p,"chunkZ"),cursor=integer(p,"cursor");
                if(Math.abs((long)cx)>1_875_000||Math.abs((long)cz)>1_875_000||cursor<1||cursor>16_777_216||cursor<=cursors.getOrDefault(chunk(cx,cz),0)) throw bad("Invalid index cursor");
                JsonArray entries=array(p,"cells",64);
                for(JsonElement entry:entries) { JsonObject c=object(entry);keys(c,"p","after","cleanup");Pos at=position(c.get("p"));State after=block(c.get("after"));
                    if(!after.network()||cells.containsKey(at)||cells.size()>=MAX_CELLS||(at.x()>>4)!=cx||(at.z()>>4)!=cz) throw bad("Invalid adopted cell");
                    putCell(at,new Cell(at,block(c.get("cleanup")),after,true));
                }
                fronts(array(p,"air",384));cursors.put(chunk(cx,cz),cursor);generation++;
            }
            case "INDEX_COMPLETE" -> { requireSetup();keys(p);if(indexed) throw bad("Duplicate index completion");indexed=true;controlRevision++; }
            case "MODE" -> { requireSetup();keys(p,"enabled");boolean on=bool(p,"enabled");if(on&&(!indexed||cells.isEmpty())) throw bad("Enabled incomplete network");enabled=on;controlRevision++; }
            case "CLEANUP_MODE" -> {cleanup=bool(p,"enabled");controlRevision++;}
            case "SPEED" -> {speedPercent=integer(p,"percent");controlRevision++;}
            case "VITALITY_RULES" -> {vitality.applyRules(p);controlRevision++;}
            case "CROP_RULES" -> {cropRules=new EcologyCrops.Rules(bool(p,"enabled"),integer(p,"seconds"));controlRevision++;}
            case "RECOVERY_RULES" -> {recoveryRules=new EcologyRecovery.Rules(bool(p,"enabled"),integer(p,"seconds"));controlRevision++;}
            case "COST_RULES" -> {costRules=costRules(p);controlRevision++;}
            case "VITALITY" -> {String prior=vitality.extent();vitality.apply(p);if(!prior.equals(vitality.extent()))controlRevision++;}
            case "FRONTIER" -> fronts(array(p,"air",64));
            case "PURGE" -> { }
            case "CLAIM_START" -> {claim=new Claim(string(p,"site"),number(p,"revision"),new Area(position(p.get("min")),position(p.get("max"))),false,Map.of());controlRevision++;}
            case "CLAIM_SCAN" -> {
                for(JsonElement item:array(p,"cells",64)){JsonObject c=object(item);Pos at=position(c.get("p"));putCell(at,new Cell(at,AIR,block(c.get("after")),false));}
                fronts(array(p,"air",384));Map<Long,Integer> next=new LinkedHashMap<>(claim.cursors);next.put(chunk(integer(p,"chunkX"),integer(p,"chunkZ")),integer(p,"cursor"));
                claim=new Claim(claim.site,claim.revision,claim.area,false,next);generation++;
            }
            case "CLAIM_DONE" -> {claim=new Claim(claim.site,claim.revision,claim.area,true,claim.cursors);controlRevision++;}
            case "GROW", "CONNECT" -> {
                requireSetup();if(!indexed||!enabled) throw bad("Growth before enable");
                Pos at=position(p.get("p"));State before=block(p.get(type.equals("CONNECT")?"cleanup":"before")),after=block(p.get("after"));
                if(!after.network()||(!cells.containsKey(at)&&cells.size()>=MAX_CELLS)) throw bad("Invalid grown cell");
                Cell old=cells.get(at);
                boolean touching=heart!=null&&EcologyTerrain.connected(heart,SCULK,at,after)||touchesRooted(at,after);
                boolean extend=(graph==null||extending)&&graphGeneration==generation&&!unresolved&&touching&&addsOnly(old,after);
                putCell(at,new Cell(at,old==null?before:old.before,after,old!=null&&old.adopted));
                fronts(array(p,"air",type.equals("CONNECT")?6:12));generation++;
                // An added connected surface cannot disconnect existing nodes. Extend from it;
                // cuts/observed losses still request a complete bounded rebuild. Replaying a
                // journal never takes this shortcut because no graph is certified at startup.
                if(extend){graphGeneration=generation;connected.add(at);visiting=connected;if(graph==null)graph=new ArrayDeque<>();graph.add(at);graphUnknown=false;extending=true;}
            }
            case "REMOVE" -> { requireSetup();keys(p,"p");Pos at=position(p.get("p"));if(cells.remove(at)==null) throw bad("Unknown removed cell");positions.remove(at);ownedChunks.compute(chunk(at.x()>>4,at.z()>>4),(k,n)->n==1?null:n-1);vitality.forget(at);resetVitalityScan();generation++; }
            case "OBSERVE" -> {Pos at=position(p.get("p"));Cell c=cells.get(at);cells.put(at,new Cell(at,c.before,block(p.get("after")),c.adopted));resetVitalityScan();generation++;}
            default -> throw bad("Unsupported ecology payload type: "+type);
        }
    }
    private void fronts(JsonArray fronts) {
        for(JsonElement e:fronts) { Pos a=position(e);if(airSet.add(a)) { if(air.size()>=MAX_AIR) throw bad("Too many surface cells");air.add(a); } }
    }
    private static boolean addsOnly(Cell old,State after){
        if(old==null)return true;
        if(!old.after.id().equals(after.id()))return false;
        if(old.after.vein())for(int f=0;f<6;f++)if(old.after.face(f)&&!after.face(f))return false;
        return true;
    }
    private void requireSetup() { if(setup==null) throw bad("Ecology record before configuration"); }
    private int chunkSize(int cx,int cz) {return chunkSize(area,cx,cz);}
    private int sourceOffset(int cx,int cz,Pos p){
        if(!area.contains(p)||(p.x()>>4)!=cx||(p.z()>>4)!=cz)return -1;
        int minX=Math.max(area.min.x(),cx<<4),minZ=Math.max(area.min.z(),cz<<4);
        int width=Math.min(area.max.x(),(cx<<4)+15)-minX+1,depth=Math.min(area.max.z(),(cz<<4)+15)-minZ+1;
        return (p.y()-area.min.y())*width*depth+(p.z()-minZ)*width+p.x()-minX;
    }
    private static int chunkSize(Area area,int cx,int cz) {return (Math.min(area.max.x(),(cx<<4)+15)-Math.max(area.min.x(),cx<<4)+1)*
            (Math.min(area.max.z(),(cz<<4)+15)-Math.max(area.min.z(),cz<<4)+1)*(area.max.y()-area.min.y()+1);}
    private Pos at(int cx,int cz,int cursor) {
        return at(area,cx,cz,cursor);
    }
    private static Pos at(Area area,int cx,int cz,int cursor) {
        int minX=Math.max(area.min.x(),cx<<4),minZ=Math.max(area.min.z(),cz<<4),width=Math.min(area.max.x(),(cx<<4)+15)-minX+1;
        int depth=Math.min(area.max.z(),(cz<<4)+15)-minZ+1;
        return new Pos(minX+cursor%width,area.min.y()+cursor/(width*depth),minZ+(cursor/width)%depth);
    }
    private static long chunk(int x,int z) { return ((long)x<<32)|(z&0xffffffffL); }
    static JsonArray pos(Pos p) {JsonArray a=new JsonArray();a.add(p.x());a.add(p.y());a.add(p.z());return a;}
    static JsonObject state(State s) {JsonObject p=new JsonObject();p.addProperty("id",s.id());JsonObject props=new JsonObject();s.properties().forEach(props::addProperty);p.add("properties",props);return p;}
    static Pos position(JsonElement e) {
        if(e==null||!e.isJsonArray()||e.getAsJsonArray().size()!=3) throw bad("Invalid position");JsonArray a=e.getAsJsonArray();return new Pos(exact(a.get(0)),exact(a.get(1)),exact(a.get(2)));
    }
    static State block(JsonElement e) {JsonObject p=object(e);keys(p,"id","properties");JsonObject props=object(p.get("properties"));Map<String,String> map=new TreeMap<>();
        props.entrySet().forEach(v->{if(!v.getValue().isJsonPrimitive()||!v.getValue().getAsJsonPrimitive().isString())throw bad("Non-string property");map.put(v.getKey(),v.getValue().getAsString());});return new State(string(p,"id"),map);}
    private static EcologyCosts.Rules costRules(JsonObject payload){
        keys(payload,"hunger","darkness");return new EcologyCosts.Rules(costRule(object(payload.get("hunger"))),costRule(object(payload.get("darkness"))));
    }
    private static JsonObject costRule(EcologyCosts.Rule rule){
        JsonObject p=new JsonObject();p.addProperty("enabled",rule.enabled());p.addProperty("seconds",rule.seconds());return p;
    }
    private static EcologyCosts.Rule costRule(JsonObject payload){
        keys(payload,"enabled","seconds");return new EcologyCosts.Rule(bool(payload,"enabled"),integer(payload,"seconds"));
    }
    private static void keys(JsonObject p,String... expected) {if(!p.keySet().equals(Set.of(expected)))throw bad("Unexpected ecology payload fields");}
    private static JsonObject object(JsonElement p) {if(p==null||!p.isJsonObject())throw bad("Object required");return p.getAsJsonObject();}
    private static JsonArray array(JsonObject p,String key,int max) {JsonElement e=p.get(key);if(e==null||!e.isJsonArray()||e.getAsJsonArray().size()>max)throw bad("Invalid array: "+key);return e.getAsJsonArray();}
    private static String string(JsonObject p,String key) {JsonElement e=p.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw bad("String required: "+key);return e.getAsString();}
    private static long number(JsonObject p,String key) {try {JsonPrimitive v=p.getAsJsonPrimitive(key);if(v==null||!v.isNumber()||!v.getAsString().matches("0|[1-9][0-9]*"))throw bad("Integer required");return Long.parseLong(v.getAsString());}catch(Exception e){throw bad("Invalid integer: "+key);}}
    private static int integer(JsonObject p,String key) {return exact(p.get(key));}
    private static int exact(JsonElement e) {try {if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("-?(0|[1-9][0-9]*)"))throw bad("Integer required");return Integer.parseInt(e.getAsString());}catch(Exception x){throw bad("Invalid coordinate/integer");}}
    private static boolean bool(JsonObject p,String key) {JsonElement e=p.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean())throw bad("Boolean required");return e.getAsBoolean();}
    private static IllegalArgumentException bad(String message) {return new IllegalArgumentException(message);}
}
