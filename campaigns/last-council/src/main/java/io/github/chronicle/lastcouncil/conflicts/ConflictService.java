package io.github.chronicle.lastcouncil.conflicts;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;

/**
 * World-owned journal of house conflicts. This service intentionally knows no
 * treaty terms: an agreement link is authority-only and public views omit it.
 * Runtime code must authenticate interactions and treaty signatures separately.
 */
public final class ConflictService {
    public enum Status { ACTIVE, PAUSED, ENDED }
    public enum AgreementKind { PEACE, CEASEFIRE }
    public enum Action { DECLARED, JOINED, CHANGED, WITHDREW, AGREEMENT }
    public record House(String houseId, UUID owner, String publicName) {
        public House { namespaced(houseId); Objects.requireNonNull(owner); plain(publicName,80,false); }
    }
    /** Authority-only link. Never copy this into a client/public projection. */
    public record Agreement(UUID treatyId, long membershipRevision, AgreementKind kind) {
        public Agreement { Objects.requireNonNull(treatyId); if(membershipRevision<1)throw new IllegalArgumentException("Invalid membership revision"); Objects.requireNonNull(kind); }
    }
    public record Record(UUID id, UUID requestId, long revision, long membershipRevision, House initiator, House target,
                         List<House> firstSide, List<House> secondSide, Status status, Agreement agreement) {
        public Record {
            Objects.requireNonNull(id); Objects.requireNonNull(requestId); if(revision<1||membershipRevision<1)throw new IllegalArgumentException("Invalid revision");
            Objects.requireNonNull(initiator);Objects.requireNonNull(target);if(initiator.houseId().equals(target.houseId()))throw new IllegalArgumentException("A house cannot conflict with itself");
            firstSide=List.copyOf(firstSide);secondSide=List.copyOf(secondSide);Objects.requireNonNull(status);
            if(firstSide.isEmpty()||secondSide.isEmpty()||!firstSide.get(0).equals(initiator)||!secondSide.get(0).equals(target))throw new IllegalArgumentException("Invalid founding sides");
            List<House> all=new ArrayList<>(firstSide);all.addAll(secondSide);if(all.size()>16||all.stream().map(House::houseId).distinct().count()!=all.size()||all.stream().map(House::owner).distinct().count()!=all.size())throw new IllegalArgumentException("Invalid conflict members");
            if(status==Status.ACTIVE&&agreement!=null)throw new IllegalArgumentException("Active conflict cannot have agreement");
            if(status!=Status.ACTIVE&&agreement==null)throw new IllegalArgumentException("Finished conflict requires agreement");
            if(agreement!=null&&((status==Status.ENDED)!=(agreement.kind()==AgreementKind.PEACE)))throw new IllegalArgumentException("Agreement status mismatch");
            if(agreement!=null&&agreement.membershipRevision()!=membershipRevision)throw new IllegalArgumentException("Agreement membership mismatch");
        }
    }
    public record PublicEvent(Action action, House actor, String detail, long recordRevision) {
        public PublicEvent {Objects.requireNonNull(action);Objects.requireNonNull(actor);plain(detail,256,true);if(recordRevision<1)throw new IllegalArgumentException("Invalid revision");}
    }
    public record View(Record record, List<PublicEvent> history) { public View {Objects.requireNonNull(record);history=List.copyOf(history);} }
    /** Safe for clients/list/detail views: excludes request IDs and agreement links. */
    public record PublicRecord(UUID id,long revision,long membershipRevision,House initiator,House target,List<House> firstSide,List<House> secondSide,Status status,List<PublicEvent> history){
        public PublicRecord {Objects.requireNonNull(id);if(revision<1||membershipRevision<1)throw new IllegalArgumentException("Invalid revision");Objects.requireNonNull(initiator);Objects.requireNonNull(target);firstSide=List.copyOf(firstSide);secondSide=List.copyOf(secondSide);Objects.requireNonNull(status);history=List.copyOf(history);}
    }
    public record PublicView(PublicRecord record) { public PublicView {Objects.requireNonNull(record);} }
    public interface Storage { byte[] load() throws IOException; void save(byte[] bytes) throws IOException; default void verify()throws IOException{} }
    @FunctionalInterface public interface Mutation { void apply(ConflictService service) throws IOException; }

    private record Conflict(Record record,List<PublicEvent> history,List<Record> snapshots) { Conflict {history=List.copyOf(history);snapshots=List.copyOf(snapshots);} }
    private record Decoded(long revision,Map<UUID,Conflict> conflicts) {}
    private static final int MAX_BYTES=8*1024*1024, MAX_CONFLICTS=64, MAX_HISTORY=128;
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private final Storage storage;
    private Map<UUID,Conflict> conflicts=Map.of(); private long revision; private boolean poisoned;
    public ConflictService(Path path)throws IOException {this(storage(path));}
    public ConflictService(Storage storage)throws IOException {this.storage=Objects.requireNonNull(storage);reload();}
    public synchronized void reload()throws IOException {try{byte[] b=storage.load();if(b==null){conflicts=Map.of();revision=0;poisoned=false;return;}Decoded d=decode(b);conflicts=d.conflicts;revision=d.revision;poisoned=false;}catch(IOException|RuntimeException e){poisoned=true;throw e;}}
    public synchronized long revision(){return revision;}
    public synchronized void verify()throws IOException{requireWritable();storage.verify();}
    public synchronized void checkpoint()throws IOException {requireWritable();storage.save(encode(revision,conflicts));}
    public synchronized List<View> records(){return conflicts.values().stream().map(c->new View(c.record,c.history)).toList();}
    public synchronized List<PublicView> publicViews(){return conflicts.values().stream().map(c->new PublicView(publicRecord(c))).toList();}
    public synchronized String sideAt(UUID conflictId,long recordRevision,String house){var c=require(conflictId);if(recordRevision<1||recordRevision>c.snapshots.size())throw new IllegalArgumentException("Unknown conflict revision");var r=c.snapshots.get(Math.toIntExact(recordRevision-1));return contains(r.firstSide,house)?r.initiator.houseId:contains(r.secondSide,house)?r.target.houseId:"";}
    public synchronized Agreement agreement(UUID conflictId){return require(conflictId).record.agreement;}
    public synchronized void preview(Mutation mutation)throws IOException {requireWritable(); Memory m=new Memory(encode(revision,conflicts)); ConflictService copy=new ConflictService(m);mutation.apply(copy);if(encode(copy.revision,copy.conflicts).length>MAX_BYTES)throw new IOException("Conflict data too large");}
    public synchronized void declare(UUID id,UUID requestId,House initiator,House target,String reason)throws IOException {
        requireWritable();Objects.requireNonNull(id);Objects.requireNonNull(requestId);Objects.requireNonNull(initiator);Objects.requireNonNull(target);plain(reason,256,true);
        Conflict old=conflicts.get(id);if(old!=null){Record r=old.record;if(r.requestId.equals(requestId)&&r.initiator.equals(initiator)&&r.target.equals(target)&&old.history.get(0).detail.equals(reason))return;throw new IllegalArgumentException("Conflict ID already used");}
        if(conflicts.size()>=MAX_CONFLICTS)throw new IllegalStateException("Conflict limit reached");
        for(Conflict c:conflicts.values())if(c.record.status==Status.ACTIVE&&sameFounders(c.record,initiator,target))throw new IllegalStateException("Active conflict already exists for these houses");
        Record r=new Record(id,requestId,1,1,initiator,target,List.of(initiator),List.of(target),Status.ACTIVE,null);put(id,new Conflict(r,List.of(event(Action.DECLARED,initiator,reason,r)),List.of(r)));
    }
    public synchronized void join(UUID id,long expected,House actor,String sideFounderHouseId)throws IOException {mutateMember(id,expected,actor,sideFounderHouseId,Action.JOINED);}
    public synchronized void change(UUID id,long expected,House actor,String sideFounderHouseId)throws IOException {mutateMember(id,expected,actor,sideFounderHouseId,Action.CHANGED);}
    public synchronized void withdraw(UUID id,long expected,House actor)throws IOException {mutateMember(id,expected,actor,null,Action.WITHDREW);}
    public synchronized void recordAgreement(UUID id,long expected,UUID chancellor,UUID treatyId,long expectedMembershipRevision,AgreementKind kind,Set<String> signerHouseIds)throws IOException {
        requireWritable();Objects.requireNonNull(chancellor);Objects.requireNonNull(treatyId);Objects.requireNonNull(kind);Objects.requireNonNull(signerHouseIds);Conflict c=require(id);Record r=c.record;
        if(r.agreement!=null&&r.agreement.equals(new Agreement(treatyId,expectedMembershipRevision,kind)))return;
        if(r.revision!=expected)throw new IllegalStateException("Stale conflict revision");
        if(r.status!=Status.ACTIVE&&(kind!=AgreementKind.PEACE||r.status!=Status.PAUSED))throw new IllegalStateException("Conflict cannot accept this agreement");
        if(r.membershipRevision!=expectedMembershipRevision)throw new IllegalStateException("Stale conflict membership");if(!memberIds(r).equals(Set.copyOf(signerHouseIds)))throw new IllegalStateException("Every current house must sign");
        Agreement a=new Agreement(treatyId,expectedMembershipRevision,kind);Status next=kind==AgreementKind.PEACE?Status.ENDED:Status.PAUSED;
        if(r.status==next&&a.equals(r.agreement))return;
        Record n=new Record(r.id,r.requestId,r.revision+1,r.membershipRevision,r.initiator,r.target,r.firstSide,r.secondSide,next,a);put(id,append(c,event(Action.AGREEMENT,new House("chronicle:chancellor",chancellor,"Chancellor"),kind==AgreementKind.PEACE?"Conflict ended.":"Conflict entered a ceasefire.",n),n));
    }
    public static boolean classify(Record record,int threshold){Objects.requireNonNull(record);if(threshold<2||threshold>16)throw new IllegalArgumentException("Threshold must be 2 to 16");return record.status!=Status.ENDED&&memberIds(record).size()>=threshold;}
    private void mutateMember(UUID id,long expected,House actor,String side,Action action)throws IOException {requireWritable();Objects.requireNonNull(actor);Conflict c=require(id,expected);Record r=c.record;if(r.status!=Status.ACTIVE)throw new IllegalStateException("Conflict is not active");boolean first=contains(r.firstSide,actor.houseId()),second=contains(r.secondSide,actor.houseId()),founder=actor.houseId().equals(r.initiator.houseId())||actor.houseId().equals(r.target.houseId());if(founder)throw new IllegalStateException("Founders require a peace agreement");
        List<House>a=new ArrayList<>(r.firstSide),b=new ArrayList<>(r.secondSide);if(action==Action.JOINED){if(first||second)throw new IllegalStateException("House already participates");addTo(a,b,actor,side,r);}else if(action==Action.CHANGED){if(!(first||second))throw new IllegalStateException("House does not participate");if(side==null||side.equals(first?r.initiator.houseId():r.target.houseId()))throw new IllegalStateException("House already supports this side");remove(a,b,actor.houseId());addTo(a,b,actor,side,r);}else {if(!(first||second))throw new IllegalStateException("House does not participate");remove(a,b,actor.houseId());}
        Record n=new Record(r.id,r.requestId,r.revision+1,r.membershipRevision+1,r.initiator,r.target,a,b,Status.ACTIVE,null);String d=switch(action){case JOINED->"House "+actor.publicName()+" joined a side.";case CHANGED->"House "+actor.publicName()+" changed sides.";case WITHDREW->"House "+actor.publicName()+" withdrew.";default->throw new AssertionError();};put(id,append(c,event(action,actor,d,n),n));}
    private static void addTo(List<House>a,List<House>b,House actor,String side,Record r){if(side==null)throw new IllegalArgumentException("Choose a side");if(side.equals(r.initiator.houseId()))a.add(actor);else if(side.equals(r.target.houseId()))b.add(actor);else throw new IllegalArgumentException("Unknown side");}
    private static void remove(List<House>a,List<House>b,String house){a.removeIf(x->x.houseId.equals(house));b.removeIf(x->x.houseId.equals(house));}
    private static boolean contains(List<House> list,String house){return list.stream().anyMatch(x->x.houseId.equals(house));}
    private static Set<String> memberIds(Record r){Set<String>s=new LinkedHashSet<>();r.firstSide.forEach(x->s.add(x.houseId));r.secondSide.forEach(x->s.add(x.houseId));return Set.copyOf(s);}
    private static boolean sameFounders(Record r,House a,House b){return Set.of(r.initiator.houseId,r.target.houseId).equals(Set.of(a.houseId,b.houseId));}
    private static PublicEvent event(Action a,House actor,String detail,Record r){return new PublicEvent(a,actor,detail,r.revision);}
    private static Conflict append(Conflict c,PublicEvent e,Record r){if(c.history.size()>=MAX_HISTORY)throw new IllegalStateException("Conflict history limit reached");List<PublicEvent> h=new ArrayList<>(c.history);h.add(e);List<Record> snapshots=new ArrayList<>(c.snapshots);snapshots.add(r);return new Conflict(r,h,snapshots);}
    private Conflict require(UUID id,long expected){Conflict c=require(id);if(c.record.revision!=expected)throw new IllegalStateException("Stale conflict revision");return c;}
    private Conflict require(UUID id){Conflict c=conflicts.get(Objects.requireNonNull(id));if(c==null)throw new IllegalArgumentException("Unknown conflict");return c;}
    private void put(UUID id,Conflict next)throws IOException {Map<UUID,Conflict> n=new LinkedHashMap<>(conflicts);n.put(id,next);long rev=Math.addExact(revision,1);byte[] bytes=storage instanceof Memory?new byte[0]:encode(rev,n);if(bytes.length>MAX_BYTES)throw new IOException("Conflict data too large");try{storage.save(bytes);}catch(IOException|RuntimeException e){poisoned=true;throw e;}conflicts=Collections.unmodifiableMap(n);revision=rev;}
    private void requireWritable()throws IOException {if(poisoned)throw new IOException("Reload required after uncertain write");}
    private static PublicRecord publicRecord(Conflict c){Record r=c.record;return new PublicRecord(r.id,r.revision,r.membershipRevision,r.initiator,r.target,r.firstSide,r.secondSide,r.status,c.history);}
    private static void namespaced(String s){if(s==null||s.length()>128||!s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid namespaced house ID");}
    private static void plain(String s,int max,boolean newline){if(s==null||s.isBlank()||s.codePointCount(0,s.length())>max||s.codePoints().anyMatch(c->(Character.isISOControl(c)&&!(newline&&c=='\n'))||(c>=0xd800&&c<=0xdfff))||s.indexOf('\u00a7')>=0)throw new IllegalArgumentException("Invalid plain text");}
    private record Stored(Record record,List<PublicEvent> history,List<Record> snapshots){}
    private static byte[] encode(long revision,Map<UUID,Conflict> values){JsonObject root=new JsonObject();root.addProperty("schema",1);root.addProperty("revision",revision);JsonArray a=new JsonArray();for(Conflict c:values.values())a.add(JSON.toJsonTree(new Stored(c.record,c.history,c.snapshots)));root.add("conflicts",a);return (root+"\n").getBytes(StandardCharsets.UTF_8);}
    private static Decoded decode(byte[] bytes)throws IOException {
        try{
            if(bytes.length<1||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Bad size");
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root=BookConfig.strictObject(text);exact(root,"schema","revision","conflicts");if(number(root.get("schema"))!=1)throw new IllegalArgumentException("Bad schema");long revision=number(root.get("revision"));JsonArray all=root.getAsJsonArray("conflicts");if(all.size()>MAX_CONFLICTS)throw new IllegalArgumentException("Too many conflicts");
            ConflictService replay=new ConflictService(new Memory(null));
            for(JsonElement element:all){JsonObject entry=element.getAsJsonObject();exact(entry,"record","history","snapshots");Record last=record(entry.getAsJsonObject("record"));JsonArray events=entry.getAsJsonArray("history"),states=entry.getAsJsonArray("snapshots");if(events.size()<1||events.size()>MAX_HISTORY||states.size()!=events.size()||replay.conflicts.containsKey(last.id))throw new IllegalArgumentException("Bad history");
                for(int i=0;i<events.size();i++){PublicEvent e=event(events.get(i).getAsJsonObject());Record after=record(states.get(i).getAsJsonObject());if(!after.id.equals(last.id)||after.revision!=i+1||e.recordRevision!=i+1)throw new IllegalArgumentException("Invalid conflict history");
                    long before=replay.revision;
                    if(i==0){if(e.action!=Action.DECLARED)throw new IllegalArgumentException("Invalid first action");replay.declare(after.id,after.requestId,after.initiator,after.target,e.detail);}
                    else{Record prior=replay.require(last.id).record;switch(e.action){
                        case JOINED,CHANGED->{String side=contains(after.firstSide,e.actor.houseId)?after.initiator.houseId:contains(after.secondSide,e.actor.houseId)?after.target.houseId:null;if(e.action==Action.JOINED)replay.join(last.id,prior.revision,e.actor,side);else replay.change(last.id,prior.revision,e.actor,side);}
                        case WITHDREW->replay.withdraw(last.id,prior.revision,e.actor);
                        case AGREEMENT->{if(after.agreement==null||!e.actor.houseId.equals("chronicle:chancellor"))throw new IllegalArgumentException("Invalid agreement history");Agreement a=after.agreement;replay.recordAgreement(last.id,prior.revision,e.actor.owner,a.treatyId,a.membershipRevision,a.kind,memberIds(prior));}
                        default->throw new IllegalArgumentException("Unexpected declaration");}}
                    Conflict actual=replay.require(last.id);if(replay.revision!=before+1||!actual.record.equals(after)||!actual.history.getLast().equals(e))throw new IllegalArgumentException("Invalid conflict transition");
                }
                if(!replay.require(last.id).record.equals(last))throw new IllegalArgumentException("Record does not match history");
            }
            if(replay.revision!=revision)throw new IllegalArgumentException("Revision mismatch");return new Decoded(revision,replay.conflicts);
        }catch(Exception e){throw new IOException("Invalid conflict data; preserved",e);}
    }
    private static Record record(JsonObject o){exact(o,"id","requestId","revision","membershipRevision","initiator","target","firstSide","secondSide","status","agreement");Agreement a=o.get("agreement").isJsonNull()?null:agreement(o.getAsJsonObject("agreement"));return new Record(uuid(o.get("id")),uuid(o.get("requestId")),number(o.get("revision")),number(o.get("membershipRevision")),house(o.getAsJsonObject("initiator")),house(o.getAsJsonObject("target")),houses(o.getAsJsonArray("firstSide")),houses(o.getAsJsonArray("secondSide")),Status.valueOf(string(o.get("status"))),a);}
    private static Agreement agreement(JsonObject o){exact(o,"treatyId","membershipRevision","kind");return new Agreement(uuid(o.get("treatyId")),number(o.get("membershipRevision")),AgreementKind.valueOf(string(o.get("kind"))));}
    private static House house(JsonObject o){exact(o,"houseId","owner","publicName");return new House(string(o.get("houseId")),uuid(o.get("owner")),string(o.get("publicName")));}
    private static List<House> houses(JsonArray a){List<House>r=new ArrayList<>();for(JsonElement e:a)r.add(house(e.getAsJsonObject()));return r;}
    private static PublicEvent event(JsonObject o){exact(o,"action","actor","detail","recordRevision");return new PublicEvent(Action.valueOf(string(o.get("action"))),house(o.getAsJsonObject("actor")),string(o.get("detail")),number(o.get("recordRevision")));}
    private static void exact(JsonObject o,String...fields){if(o==null||!o.keySet().equals(Set.of(fields)))throw new IllegalArgumentException("Unexpected fields");}
    private static String string(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected string");return e.getAsString();}
    private static UUID uuid(JsonElement e){String s=string(e);UUID u=UUID.fromString(s);if(!u.toString().equals(s))throw new IllegalArgumentException("Noncanonical UUID");return u;}
    private static long number(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("Expected canonical integer");return Long.parseLong(e.getAsString());}
    private static Storage storage(Path path){AtomicDataFile f=new AtomicDataFile(path,MAX_BYTES);return new Storage(){public byte[] load()throws IOException{return f.load();}public void save(byte[] b)throws IOException{f.save(b);}public void verify()throws IOException{f.verifyUnchanged();}};}
    private static final class Memory implements Storage {private byte[] bytes;Memory(byte[] bytes){this.bytes=bytes;}public byte[] load(){return bytes;}public void save(byte[] bytes){this.bytes=bytes;}}
}
