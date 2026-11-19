package io.github.chronicle.lastcouncil.treaties;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.lastcouncil.progression.PoliticalOccurrence;
import io.github.chronicle.lastcouncil.progression.PoliticalObserver;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;

/** World-owned agreements. Callers must independently authorize current office and lectern sessions. */
public final class TreatyService {
    public enum Visibility { PUBLIC, PRIVATE }
    public enum Status { DRAFT, ACTIVE, BROKEN }
    public enum Action { CREATED, EDITED, SIGNED, SANCTIFIED, BROKEN }
    /** Mechanical category is explicit; promise text is never interpreted. */
    public enum Purpose { GENERAL, ALLIANCE, NON_AGGRESSION, MUTUAL_DEFENSE, TRADE, PEACE, CEASEFIRE }
    /** Pins a Peace/Ceasefire draft to the current conflict membership revision. */
    public record ConflictLink(UUID conflictId, long membershipRevision) {
        public ConflictLink { Objects.requireNonNull(conflictId); if(membershipRevision<1) throw new IllegalArgumentException("Invalid conflict membership revision"); }
    }
    public record Party(String houseId, UUID owner, String publicName) {
        public Party { if(houseId==null||houseId.length()>128||!houseId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid namespaced house ID"); Objects.requireNonNull(owner); plain(publicName, 80, false); }
    }
    public record Document(UUID id, long contentRevision, String title, String terms, Visibility visibility, List<Party> parties, Purpose purpose, ConflictLink conflictLink) {
        public Document {
            Objects.requireNonNull(id); if(contentRevision<1) throw new IllegalArgumentException("Invalid revision");
            plain(title,80,false); plain(terms,2048,true); Objects.requireNonNull(visibility); Objects.requireNonNull(purpose);
            parties=List.copyOf(parties); if(parties.size()<2||parties.size()>16) throw new IllegalArgumentException("Two to sixteen parties required");
            if(parties.stream().map(Party::owner).distinct().count()!=parties.size()||parties.stream().map(Party::houseId).distinct().count()!=parties.size()) throw new IllegalArgumentException("Duplicate party");
            if((purpose==Purpose.PEACE||purpose==Purpose.CEASEFIRE)!=(conflictLink!=null)) throw new IllegalArgumentException("Conflict link is required only for peace or ceasefire");
        }
        /** Compatibility constructor for pre-conflict callers and schema-one documents. */
        public Document(UUID id, long contentRevision, String title, String terms, Visibility visibility, List<Party> parties) {
            this(id,contentRevision,title,terms,visibility,parties,Purpose.GENERAL,null);
        }
    }
    public record Event(Action action, UUID actor, Document document) { public Event { Objects.requireNonNull(action); Objects.requireNonNull(actor); Objects.requireNonNull(document); } }
    public record View(Document document, Status status, Map<UUID,Long> signatures, List<Event> history) {
        public View { signatures=Map.copyOf(signatures); history=List.copyOf(history); }
    }
    private record Treaty(Document document, Status status, Map<UUID,Long> signatures, List<Event> history) {
        Treaty { signatures=Map.copyOf(signatures); history=List.copyOf(history); }
    }
    public interface Storage { byte[] load() throws IOException; void save(byte[] bytes) throws IOException; default void verify()throws IOException{} }
    private static final int MAX_BYTES=8*1024*1024;
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private final Storage storage;
    private Map<UUID,Treaty> treaties=Map.of();
    private long revision;
    private boolean poisoned;
    private PoliticalObserver observer=PoliticalObserver.NONE;
    public synchronized void observer(PoliticalObserver value){observer=Objects.requireNonNull(value);}
    public TreatyService(Path path) throws IOException { this(storage(path)); }
    public TreatyService(Storage storage) throws IOException { this.storage=Objects.requireNonNull(storage); reload(); }
    public synchronized void reload() throws IOException {
        try {
            byte[] bytes=storage.load();
            if(bytes==null){treaties=Map.of(); revision=0; poisoned=false; return;}
            Decoded decoded=decode(bytes); treaties=decoded.treaties(); revision=decoded.revision(); poisoned=false;
        } catch (IOException | RuntimeException failure) { poisoned=true; throw failure; }
    }
    public synchronized long revision(){return revision;}
    public synchronized void verify()throws IOException{requireWritable();storage.verify();}
    @FunctionalInterface public interface Mutation {void apply(TreatyService service)throws IOException;}
    /** Authoritative preflight for a pinned cross-store operation. No real storage is changed. */
    public synchronized void preview(Mutation mutation)throws IOException {requireWritable();byte[] snapshot=encode(revision,treaties);TreatyService copy=new TreatyService(new Storage(){public byte[] load(){return snapshot;}public void save(byte[] ignored){}});mutation.apply(copy);}
    /** Validate and plan child source facts on an isolated replay without publishing. */
    public synchronized List<PoliticalOccurrence> politicalPlan(Mutation mutation)throws IOException {
        requireWritable(); storage.verify(); byte[] snapshot=encode(revision,treaties);
        TreatyService copy=new TreatyService(new Storage(){public byte[] load(){return snapshot;}public void save(byte[] ignored){}});
        var result=new ArrayList<PoliticalOccurrence>(); copy.observer(result::addAll); mutation.apply(copy); return List.copyOf(result);
    }
    public synchronized void checkpoint()throws IOException {requireWritable();storage.save(encode(revision,treaties));}

    public synchronized List<View> view(UUID viewer, boolean chancellor) {
        Objects.requireNonNull(viewer); List<View> result=new ArrayList<>();
        for(Treaty treaty:treaties.values()) if(visible(treaty.document(),viewer,chancellor)) {
            List<Event> events=treaty.history().stream().filter(e->visible(e.document(),viewer,chancellor)).toList();
            result.add(new View(treaty.document(),treaty.status(),treaty.signatures(),events));
        }
        return List.copyOf(result);
    }
    /** Trusted server-only lookup. Never expose this result directly to a player. */
    public synchronized View get(UUID id) { Treaty treaty=treaties.get(Objects.requireNonNull(id)); return treaty==null?null:view(treaty); }
    /** Trusted server-only complete ledger, including private documents and history. */
    public synchronized List<View> allAuthority() { return treaties.values().stream().map(TreatyService::view).toList(); }
    public synchronized void create(UUID id, UUID actor, Document doc) throws IOException {
        requireWritable(); Objects.requireNonNull(id); Objects.requireNonNull(actor); Objects.requireNonNull(doc);
        if(!id.equals(doc.id())||doc.contentRevision()!=1||!party(doc,actor)) throw new IllegalArgumentException("Invalid creator or document");
        Treaty old=treaties.get(id); if(old!=null){if(old.history().get(0).actor().equals(actor)&&old.history().get(0).document().equals(doc))return; throw new IllegalArgumentException("Treaty ID already used");}
        if(treaties.size()>=64)throw new IllegalStateException("Treaty limit reached");
        put(id,new Treaty(doc,Status.DRAFT,Map.of(),List.of(new Event(Action.CREATED,actor,doc))));
    }
    public synchronized void edit(UUID id,long expected,UUID actor,Document replacement)throws IOException {
        requireWritable(); Treaty t=check(id,expected,actor,Status.DRAFT); Objects.requireNonNull(replacement);
        if(!id.equals(replacement.id())||replacement.contentRevision()!=expected+1||!party(replacement,actor)) throw new IllegalArgumentException("Invalid replacement");
        Document d=t.document(); if(d.title().equals(replacement.title())&&d.terms().equals(replacement.terms())&&d.visibility()==replacement.visibility()&&d.parties().equals(replacement.parties())&&d.purpose()==replacement.purpose()&&Objects.equals(d.conflictLink(),replacement.conflictLink()))return;
        put(id,append(t,replacement,Status.DRAFT,Map.of(),Action.EDITED,actor));
    }
    public synchronized void sign(UUID id,long expected,UUID actor)throws IOException {
        requireWritable(); Treaty t=check(id,expected,actor,Status.DRAFT); if(t.signatures().containsKey(actor))return;
        Map<UUID,Long> signatures=new HashMap<>(t.signatures());signatures.put(actor,expected);
        put(id,append(t,t.document(),t.status(),signatures,Action.SIGNED,actor));
    }
    public synchronized void sanctify(UUID id,long expected,UUID chancellor,Set<UUID> present)throws IOException {
        requireWritable(); Objects.requireNonNull(chancellor); Objects.requireNonNull(present); Treaty t=require(id,expected);
        if(party(t.document(),chancellor))throw new IllegalArgumentException("Chancellor is a party");
        if(t.status()==Status.ACTIVE && t.history().stream().anyMatch(e->e.action()==Action.SANCTIFIED&&e.actor().equals(chancellor)))return;
        if(t.status()!=Status.DRAFT||!present.contains(chancellor))throw new IllegalStateException("Chancellor must be present at a draft");
        for(Party p:t.document().parties())if(!present.contains(p.owner())||!Objects.equals(t.signatures().get(p.owner()),expected))throw new IllegalStateException("All parties must be present and have signed");
        put(id,append(t,t.document(),Status.ACTIVE,t.signatures(),Action.SANCTIFIED,chancellor));
    }
    public synchronized void breach(UUID id,long expected,UUID actor)throws IOException {
        requireWritable(); Treaty t=require(id,expected); if(!party(t.document(),actor))throw new IllegalArgumentException("Not a party");
        if(t.status()==Status.BROKEN)return; if(t.status()!=Status.ACTIVE)throw new IllegalStateException("Treaty is not active");
        put(id,append(t,t.document(),Status.BROKEN,t.signatures(),Action.BROKEN,actor));
    }
    private void requireWritable() throws IOException { if(poisoned)throw new IOException("Reload required after uncertain write"); }
    private Treaty check(UUID id,long expected,UUID actor,Status status){Treaty t=require(id,expected);if(!party(t.document(),actor))throw new IllegalArgumentException("Not a party");if(t.status()!=status)throw new IllegalStateException("Wrong status");return t;}
    private Treaty require(UUID id,long expected){Treaty t=treaties.get(Objects.requireNonNull(id));if(t==null)throw new IllegalArgumentException("Unknown treaty");if(t.document().contentRevision()!=expected)throw new IllegalStateException("Stale revision");return t;}
    private static boolean party(Document d,UUID actor){return d.parties().stream().anyMatch(p->p.owner().equals(actor));}
    private static boolean visible(Document d,UUID actor,boolean chancellor){return d.visibility()==Visibility.PUBLIC||chancellor||party(d,actor);}
    private static View view(Treaty treaty) { return new View(treaty.document(),treaty.status(),treaty.signatures(),treaty.history()); }
    private static Treaty append(Treaty t,Document d,Status s,Map<UUID,Long> signatures,Action action,UUID actor){if(t.history().size()>=128)throw new IllegalStateException("History limit reached");List<Event> history=new ArrayList<>(t.history());history.add(new Event(action,actor,d));return new Treaty(d,s,signatures,history);}
    private void put(UUID id,Treaty treaty)throws IOException {
        if(poisoned)throw new IOException("Reload required after uncertain write");
        Map<UUID,Treaty> next=new LinkedHashMap<>(treaties);next.put(id,treaty);long rev=Math.addExact(revision,1);
        byte[] bytes=storage instanceof Memory ? new byte[0] : encode(rev,next); if(bytes.length>MAX_BYTES)throw new IOException("Treaty data too large");
        Event latest=treaty.history().get(treaty.history().size()-1);
        PoliticalOccurrence occurrence=occurrence(id,treaty.history().size()-1,latest,treaty.history().subList(0,treaty.history().size()-1));
        if(occurrence!=null)observer.before(List.of(occurrence));
        try {storage.save(bytes);}catch(IOException|RuntimeException failure){poisoned=true;throw failure;}
        treaties=Collections.unmodifiableMap(next);revision=rev;
    }
    /** Evidence is tied to the exact canonical prefix of this treaty's history. */
    public synchronized List<PoliticalOccurrence> politicalEvidence(String authority)throws IOException {
        verify();
        if(authority==null)throw new IOException("Invalid treaty authority");
        String[] parts=authority.split(":",-1);
        if(parts.length!=4||!parts[0].equals("council-treaty")||!parts[2].matches("0|[1-9][0-9]*")||!parts[3].matches("[0-9a-f]{64}"))
            throw new IOException("Invalid treaty authority");
        final UUID id; final int index;
        try {id=UUID.fromString(parts[1]);index=Integer.parseInt(parts[2]);}
        catch(IllegalArgumentException failure){throw new IOException("Invalid treaty authority",failure);}
        if(!id.toString().equals(parts[1]))throw new IOException("Noncanonical treaty identity");
        Treaty treaty=treaties.get(id);
        if(treaty==null||treaty.history().size()<index)throw new IOException("Treaty evidence baseline missing or older");
        List<Event> prefix=treaty.history().subList(0,index);
        if(!prefixHash(prefix).equals(parts[3]))throw new IOException("Treaty evidence baseline changed");
        if(treaty.history().size()==index)return List.of();
        PoliticalOccurrence result=occurrence(id,index,treaty.history().get(index),prefix);
        if(result==null)throw new IOException("Treaty event is not a public political occurrence");
        return List.of(result);
    }
    private static PoliticalOccurrence occurrence(UUID id,int index,Event event,List<Event> prefix) {
        if(event.document().visibility()!=Visibility.PUBLIC || !(event.action()==Action.SIGNED||event.action()==Action.SANCTIFIED||event.action()==Action.BROKEN))return null;
        return new PoliticalOccurrence("council-treaty:"+id+":"+index+":"+prefixHash(prefix),"last_council:treaty_changed","*",event.action().name().toLowerCase(Locale.ROOT));
    }
    private static String prefixHash(List<Event> prefix) {
        try {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(JSON.toJson(prefix).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static void plain(String s,int max,boolean newline){if(s==null||s.isBlank()||s.codePointCount(0,s.length())>max||s.codePoints().anyMatch(c->(Character.isISOControl(c)&&!(newline&&c=='\n'))||(c>=0xd800&&c<=0xdfff))||s.indexOf('\u00a7')>=0)throw new IllegalArgumentException("Invalid plain text");}
    private record Decoded(long revision,Map<UUID,Treaty> treaties){}
    private static byte[] encode(long revision,Map<UUID,Treaty> values){JsonObject root=new JsonObject();root.addProperty("schema",2);root.addProperty("revision",revision);JsonArray all=new JsonArray();for(Treaty t:values.values())all.add(JSON.toJsonTree(t.history()));root.add("treaties",all);return (root+"\n").getBytes(StandardCharsets.UTF_8);}
    private static Decoded decode(byte[] bytes)throws IOException {
        try {
            if(bytes.length<1||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Bad size");
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root=BookConfig.strictObject(text);exact(root,"schema","revision","treaties");long schema=number(root.get("schema"));if(schema!=1&&schema!=2)throw new IllegalArgumentException("Bad schema");long revision=number(root.get("revision"));if(revision<0)throw new IllegalArgumentException("Bad revision");
            JsonArray all=root.getAsJsonArray("treaties");if(all.size()>64)throw new IllegalArgumentException("Too many treaties");
            Memory memory=new Memory();TreatyService replay=new TreatyService(memory);
            for(JsonElement entry:all){JsonArray history=entry.getAsJsonArray();if(history.size()==0||history.size()>128)throw new IllegalArgumentException("Bad history"); UUID historyId=null;
                for(JsonElement element:history){JsonObject e=element.getAsJsonObject();exact(e,"action","actor","document");Action action=Action.valueOf(string(e.get("action")));UUID actor=uuid(e.get("actor"));Document d=document(e.getAsJsonObject("document"),schema);
                    if(historyId==null){historyId=d.id();if(action!=Action.CREATED||replay.treaties.containsKey(historyId))throw new IllegalArgumentException("Invalid treaty history");}else if(!historyId.equals(d.id()))throw new IllegalArgumentException("Mixed treaty history"); long before=replay.revision;
                    switch(action){case CREATED->replay.create(d.id(),actor,d);case EDITED->replay.edit(d.id(),d.contentRevision()-1,actor,d);case SIGNED->replay.sign(d.id(),d.contentRevision(),actor);case SANCTIFIED->{Set<UUID> present=new HashSet<>();present.add(actor);d.parties().forEach(p->present.add(p.owner()));replay.sanctify(d.id(),d.contentRevision(),actor,present);}case BROKEN->replay.breach(d.id(),d.contentRevision(),actor);}
                    Treaty t=replay.treaties.get(d.id());if(replay.revision!=before+1||t==null||!t.document().equals(d)||!t.history().get(t.history().size()-1).equals(new Event(action,actor,d)))throw new IllegalArgumentException("Invalid history transition");
                }
            }
            if(replay.revision!=revision)throw new IllegalArgumentException("Revision mismatch");return new Decoded(revision,replay.treaties);
        }catch(Exception invalid){throw new IOException("Invalid treaty data; preserved",invalid);}
    }
    public static Document decodeStoredDocument(JsonObject o){return document(o,2);}
    private static Document document(JsonObject o,long schema){
        if(schema==1) exact(o,"id","contentRevision","title","terms","visibility","parties");
        else exact(o,"id","contentRevision","title","terms","visibility","parties","purpose","conflictLink");
        List<Party> parties=new ArrayList<>();
        for(JsonElement e:o.getAsJsonArray("parties")){JsonObject p=e.getAsJsonObject();exact(p,"houseId","owner","publicName");parties.add(new Party(string(p.get("houseId")),uuid(p.get("owner")),string(p.get("publicName"))));}
        Purpose purpose=schema==1?Purpose.GENERAL:Purpose.valueOf(string(o.get("purpose")));
        ConflictLink link=null;
        if(schema==2&&!o.get("conflictLink").isJsonNull()){JsonObject c=o.getAsJsonObject("conflictLink");exact(c,"conflictId","membershipRevision");link=new ConflictLink(uuid(c.get("conflictId")),number(c.get("membershipRevision")));}
        return new Document(uuid(o.get("id")),number(o.get("contentRevision")),string(o.get("title")),string(o.get("terms")),Visibility.valueOf(string(o.get("visibility"))),parties,purpose,link);
    }
    private static void exact(JsonObject o,String...fields){if(o==null||!o.keySet().equals(Set.of(fields)))throw new IllegalArgumentException("Unexpected fields");}
    private static String string(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected string");return e.getAsString();}
    private static UUID uuid(JsonElement e){String s=string(e);UUID u=UUID.fromString(s);if(!u.toString().equals(s))throw new IllegalArgumentException("Noncanonical UUID");return u;}
    private static long number(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("Expected canonical integer");return Long.parseLong(e.getAsString());}
    private static Storage storage(Path path){AtomicDataFile f=new AtomicDataFile(path,MAX_BYTES);return new Storage(){public byte[] load()throws IOException{return f.load();}public void save(byte[] bytes)throws IOException{f.save(bytes);}public void verify()throws IOException{f.verifyUnchanged();}};}
    private static final class Memory implements Storage {public byte[] load(){return null;}public void save(byte[] bytes){}}
}
