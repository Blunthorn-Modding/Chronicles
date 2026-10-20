package io.github.chronicle.quietbelow.ecology;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;

/** Bounded, durable campaign ecology. No clocks, players, chunk loads or Minecraft classes. */
public final class EcologyEngine {
    public static final int MAX_CELLS=32768, MAX_AIR=131072, INDEX_READS=256, GRAPH_WORK=128, MAX_WRITES=4;
    public static final long MAX_SOURCE_VOLUME=16_777_216;
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
                throw new IllegalArgumentException("Source region exceeds ecology indexing bounds");
            long chunks=((long)(max.x()>>4)-(min.x()>>4)+1)*((long)(max.z()>>4)-(min.z()>>4)+1);
            if(chunks>256) throw new IllegalArgumentException("Source city may span at most 256 chunks in this review build");
        }
        private static long volume(Pos a,Pos b) {
            try { return Math.multiplyExact(Math.multiplyExact((long)b.x()-a.x()+1,(long)b.y()-a.y()+1),(long)b.z()-a.z()+1); }
            catch(ArithmeticException e) { return Long.MAX_VALUE; }
        }
        boolean contains(Pos p) { return p.x()>=min.x()&&p.x()<=max.x()&&p.y()>=min.y()&&p.y()<=max.y()&&p.z()>=min.z()&&p.z()<=max.z(); }
    }
    public record Cell(Pos position, State before, State after, boolean adopted) { }
    public record View(boolean configured, boolean indexed, boolean enabled, String hold, int owned,
                       int connected, int frontier, int completedChunks, int totalChunks, boolean unresolved,
                       String extent, long revision, long journalBytes) { }

    private final EcologyJournal journal;
    private final LinkedHashMap<Pos,Cell> cells=new LinkedHashMap<>();
    private final ArrayList<Pos> air=new ArrayList<>();
    private final Set<Pos> airSet=new HashSet<>();
    private final Map<Long,Integer> cursors=new LinkedHashMap<>();
    private Setup setup;
    private Area area;
    private Pos heart;
    private boolean indexed,enabled;
    private String failure;
    private long controlRevision;
    private int chunkTurn,airTurn,clock,writes,growthInterval=20;
    private long generation;
    private long graphGeneration=-1;
    private Iterator<Pos> validation;
    private ArrayDeque<Pos> graph;
    private Set<Pos> visiting=new HashSet<>(), connected=new HashSet<>();
    private boolean graphUnknown,unresolved=true,validationUnknown,validated,extending;

    public EcologyEngine(EcologyJournal journal) {
        this.journal=Objects.requireNonNull(journal);
        for(EcologyJournal.Entry e:journal.entries()) replay(e.type(),e.payload());
        controlRevision=journal.revision();
    }
    public Setup setup() { return setup; }
    public List<Cell> cells() { return List.copyOf(cells.values()); }
    public boolean owns(Pos p) { return cells.containsKey(p); }
    public boolean rooted(Pos p) { return connected.contains(p) && !unresolved && (graph==null||extending) && graphGeneration==generation; }
    public boolean enabled() { return enabled && failure==null; }
    public String failure() { return failure; }
    public void fail(String reason) { if(failure==null) { failure=Objects.requireNonNull(reason); controlRevision++; } }

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
            Set<Pos> scanned=new HashSet<>();for(int n=start;n<end;n++)scanned.add(at(cx,cz,n));
            for(JsonElement cell:array(p,"cells",64))if(!scanned.contains(position(object(cell).get("p"))))throw bad("Adopted cell outside scanned interval");
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
        JsonObject p=new JsonObject(); p.addProperty("enabled",on); record("MODE",p); clock=0;
    }
    public View view() {
        int done=0,total=0;
        if(area!=null) for(int cx=area.min.x()>>4;cx<=area.max.x()>>4;cx++) for(int cz=area.min.z()>>4;cz<=area.max.z()>>4;cz++) {
            total++; if(cursors.getOrDefault(chunk(cx,cz),0)==chunkSize(cx,cz)) done++;
        }
        boolean uncertain=unresolved||graph!=null||graphGeneration!=generation;
        String extent=uncertain?"Unresolved":connected.size()>=2048?"Extensive":connected.size()>=256?"Established":"Rooted";
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
        if(!playing||!enabled()||!indexed||area==null) { clock=0;growthInterval=20;return; }
        reconcile(w);
        topology(w);
        if(unresolved || graph!=null || connected.isEmpty()) return;
        if(writes>=MAX_WRITES)return; // A busy native tick waits; it is not a persistence/capacity fault.
        if(++clock<growthInterval) return;
        clock=0;
        grow(w);
    }
    private void reconcile(EcologyTerrain w) throws IOException {
        if(validation==null) {
            validation=new ArrayList<>(cells.keySet()).iterator(); validationUnknown=false;
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
    private void topology(EcologyTerrain w) {
        if(graph==null && graphGeneration==generation) return;
        if(graph==null || graphGeneration!=generation) {
            graphGeneration=generation; graph=new ArrayDeque<>(); visiting=new HashSet<>(); graphUnknown=false;extending=false;
            if(heart==null||!w.loaded(heart)) { graphUnknown=true; }
            else for(int f=0;f<6;f++) {
                Pos p=heart.offset(f); Cell c=cells.get(p);
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
        int reads=0;
        for(int tries=0;tries<8;tries++) {
            Pos from=air.get(Math.floorMod(airTurn++,air.size()));
            if(!passable(w,from)||!rootedExposure(from)) continue;
            for(int step=-1;step<6;step++) {
                Pos a=step<0?from:from.offset(step);
                if(!passable(w,a)) continue;
                State airState=w.get(a);
                if(airState.air()&&cells.size()>=64&&cells.size()%32==0&&feature(w,a,airState))return;
                // Both darkness and light permit growth; lit exposure also shortens the next interval.
                int start=Math.floorMod(airTurn+(w.light(a)>7?1:0),6);
                for(int i=0;i<6;i++) {
                    if(++reads>256) return;
                    int face=(i+start)%6; Pos backing=a.offset(face);
                    if(!w.loaded(backing)||cells.containsKey(backing)||!w.solidFace(backing,face^1)) continue;
                    State original=w.get(backing);
                    if(original.network()) continue; // unrelated sculk is never silently adopted
                    if(w.substrate(backing)) {
                        if(touchesRooted(backing,SCULK)) { change(w,backing,original,SCULK,List.of(a));growthInterval=w.light(a)>7?10:20;return; }
                    } else if(airState.air() || (owns(a)&&airState.vein())) {
                        State next=airState.vein()?airState.with(FACES[face],"true"):EcologyTerrain.vein(face);
                        if(!next.equals(airState)&&touchesRooted(a,next)) { change(w,a,airState,next,List.of(a));growthInterval=w.light(a)>7?10:20;return; }
                    }
                }
            }
        }
    }
    private boolean feature(EcologyTerrain w,Pos a,State before) throws IOException {
        Pos below=a.offset(0);Cell support=cells.get(below);
        if(support==null||!support.after.id().equals("minecraft:sculk")||!rooted(below))return false;
        // Finite spacing query; unrelated nearby features also occupy their physical space.
        for(int dx=-6;dx<=6;dx++)for(int dy=-3;dy<=3;dy++)for(int dz=-6;dz<=6;dz++) {
            Cell c=cells.get(a.add(dx,dy,dz));
            if(c!=null&&!c.after.vein()&&!c.after.id().equals("minecraft:sculk"))return false;
        }
        String id=connected.size()>=2048&&cells.size()%128==0?"minecraft:sculk_shrieker":
                connected.size()>=256&&cells.size()%96==0?"minecraft:sculk_catalyst":"minecraft:sculk_sensor";
        Map<String,String> properties=switch(id) {
            case "minecraft:sculk_shrieker" -> Map.of("can_summon","true","shrieking","false","waterlogged","false");
            case "minecraft:sculk_catalyst" -> Map.of("bloom","false");
            default -> Map.of("sculk_sensor_phase","inactive","power","0","waterlogged","false");
        };
        State next=new State(id,properties);
        change(w,a,before,next,List.of(a));return true;
    }
    private boolean rootedExposure(Pos a) {
        if(connected.contains(a)) return true;
        for(int f=0;f<6;f++) if(connected.contains(a.offset(f))) return true;
        return false;
    }
    private boolean touchesRooted(Pos p,State s) {
        for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) {
            if(Math.abs(dx)+Math.abs(dy)+Math.abs(dz)>2) continue;
            Pos q=p.add(dx,dy,dz);Cell c=cells.get(q);
            if(c!=null&&connected.contains(q)&&EcologyTerrain.connected(p,s,q,c.after)) return true;
        }
        return false;
    }
    private boolean passable(EcologyTerrain w,Pos p) {
        if(!w.loaded(p)) return false;
        State s=w.get(p);return s.air() || (s.vein()&&owns(p)&&!"true".equals(s.properties().get("waterlogged")));
    }
    private void change(EcologyTerrain w,Pos p,State before,State after,List<Pos> frontier) throws IOException {
        intend(p,before,after,frontier);
        if(!w.set(p,after)) {
            mode(false);fail("A journaled ecology world write failed; growth was disabled, inspect before continuing");throw new IOException(failure);
        }
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
        if(!enabled()||!rooted(origin)||writes>=MAX_WRITES||!after.network()||!w.loaded(origin)||!w.loaded(p)) return false;
        Cell current=cells.get(p);
        if(current!=null && before.sameOwnedShape(after)) return true;
        if(current==null&&!after.id().equals("minecraft:sculk")&&!before.air())return false;
        if(after.id().equals("minecraft:sculk_shrieker")&&connected.size()<2048) return false;
        // A descendant must occupy a discovered air surface, or its immediately exposed backing.
        boolean exposed=airSet.contains(p)&&passable(w,p)&&rootedExposure(p);
        List<Pos> fronts=new ArrayList<>();
        if(exposed) fronts.add(p);
        for(int f=0;f<6;f++) {
            Pos a=p.offset(f);
            if(airSet.contains(a)&&passable(w,a)&&rootedExposure(a)) { exposed=true;fronts.add(a); }
        }
        if(!exposed||(!cells.containsKey(p)&&before.network())||!touchesRooted(p,after)) return false;
        if(after.id().equals("minecraft:sculk")&&!w.substrate(p)&&current==null) return false;
        intend(p,before,after,fronts);return true;
    }
    /** Native loss is an observation of already-journaled terrain, sharing the mutation budget. */
    public boolean reserveNativeLoss(){if(!enabled()||writes>=MAX_WRITES)return false;writes++;return true;}
    /** Outside writes can prune ownership, but can never adopt placed sculk. */
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
                    cells.put(at,new Cell(at,block(c.get("cleanup")),after,true));
                }
                fronts(array(p,"air",384));cursors.put(chunk(cx,cz),cursor);generation++;
            }
            case "INDEX_COMPLETE" -> { requireSetup();keys(p);if(indexed) throw bad("Duplicate index completion");indexed=true;controlRevision++; }
            case "MODE" -> { requireSetup();keys(p,"enabled");boolean on=bool(p,"enabled");if(on&&(!indexed||cells.isEmpty())) throw bad("Enabled incomplete network");enabled=on;controlRevision++; }
            case "GROW" -> {
                requireSetup();if(!indexed||!enabled) throw bad("Growth before enable");keys(p,"p","before","after","air");
                Pos at=position(p.get("p"));State before=block(p.get("before")),after=block(p.get("after"));
                if(!after.network()||(!cells.containsKey(at)&&cells.size()>=MAX_CELLS)) throw bad("Invalid grown cell");
                Cell old=cells.get(at);
                boolean extend=(graph==null||extending)&&graphGeneration==generation&&!unresolved&&touchesRooted(at,after)&&addsOnly(old,after);
                cells.put(at,new Cell(at,old==null?before:old.before,after,old!=null&&old.adopted));
                fronts(array(p,"air",12));generation++;
                // An added connected surface cannot disconnect existing nodes. Extend from it;
                // cuts/observed losses still request a complete bounded rebuild. Replaying a
                // journal never takes this shortcut because no graph is certified at startup.
                if(extend){graphGeneration=generation;connected.add(at);visiting=connected;if(graph==null)graph=new ArrayDeque<>();graph.add(at);graphUnknown=false;extending=true;}
            }
            case "REMOVE" -> { requireSetup();keys(p,"p");Pos at=position(p.get("p"));if(cells.remove(at)==null) throw bad("Unknown removed cell");generation++; }
            case "OBSERVE" -> {Pos at=position(p.get("p"));Cell c=cells.get(at);cells.put(at,new Cell(at,c.before,block(p.get("after")),c.adopted));generation++;}
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
    private int chunkSize(int cx,int cz) {return (Math.min(area.max.x(),(cx<<4)+15)-Math.max(area.min.x(),cx<<4)+1)*
            (Math.min(area.max.z(),(cz<<4)+15)-Math.max(area.min.z(),cz<<4)+1)*(area.max.y()-area.min.y()+1);}
    private Pos at(int cx,int cz,int cursor) {
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
