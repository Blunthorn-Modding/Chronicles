package io.github.chronicle.lastcouncil.treaties;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
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
    public record Party(String houseId, UUID owner, String publicName) {
        public Party { if(houseId==null||houseId.length()>128||!houseId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid namespaced house ID"); Objects.requireNonNull(owner); plain(publicName, 80, false); }
    }
    public record Document(UUID id, long contentRevision, String title, String terms, Visibility visibility, List<Party> parties) {
        public Document {
            Objects.requireNonNull(id); if(contentRevision<1) throw new IllegalArgumentException("Invalid revision");
            plain(title,80,false); plain(terms,2048,true); Objects.requireNonNull(visibility);
            parties=List.copyOf(parties); if(parties.size()<2||parties.size()>16) throw new IllegalArgumentException("Two to sixteen parties required");
            if(parties.stream().map(Party::owner).distinct().count()!=parties.size()||parties.stream().map(Party::houseId).distinct().count()!=parties.size()) throw new IllegalArgumentException("Duplicate party");
        }
    }
    public record Event(Action action, UUID actor, Document document) { public Event { Objects.requireNonNull(action); Objects.requireNonNull(actor); Objects.requireNonNull(document); } }
    public record View(Document document, Status status, Map<UUID,Long> signatures, List<Event> history) {
        public View { signatures=Map.copyOf(signatures); history=List.copyOf(history); }
    }
    private record Treaty(Document document, Status status, Map<UUID,Long> signatures, List<Event> history) {
        Treaty { signatures=Map.copyOf(signatures); history=List.copyOf(history); }
    }
    public interface Storage { byte[] load() throws IOException; void save(byte[] bytes) throws IOException; }
    private static final int MAX_BYTES=8*1024*1024;
    private static final Gson JSON=new Gson();
    private final Storage storage;
    private Map<UUID,Treaty> treaties=Map.of();
    private long revision;
    private boolean poisoned;
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
    public synchronized List<View> view(UUID viewer, boolean chancellor) {
        Objects.requireNonNull(viewer); List<View> result=new ArrayList<>();
        for(Treaty treaty:treaties.values()) if(visible(treaty.document(),viewer,chancellor)) {
            List<Event> events=treaty.history().stream().filter(e->visible(e.document(),viewer,chancellor)).toList();
            result.add(new View(treaty.document(),treaty.status(),treaty.signatures(),events));
        }
        return List.copyOf(result);
    }
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
        Document d=t.document(); if(d.title().equals(replacement.title())&&d.terms().equals(replacement.terms())&&d.visibility()==replacement.visibility()&&d.parties().equals(replacement.parties()))return;
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
    private static Treaty append(Treaty t,Document d,Status s,Map<UUID,Long> signatures,Action action,UUID actor){if(t.history().size()>=128)throw new IllegalStateException("History limit reached");List<Event> history=new ArrayList<>(t.history());history.add(new Event(action,actor,d));return new Treaty(d,s,signatures,history);}
    private void put(UUID id,Treaty treaty)throws IOException {
        if(poisoned)throw new IOException("Reload required after uncertain write");
        Map<UUID,Treaty> next=new LinkedHashMap<>(treaties);next.put(id,treaty);long rev=Math.addExact(revision,1);
        byte[] bytes=storage instanceof Memory ? new byte[0] : encode(rev,next); if(bytes.length>MAX_BYTES)throw new IOException("Treaty data too large");
        try {storage.save(bytes);}catch(IOException|RuntimeException failure){poisoned=true;throw failure;}
        treaties=Collections.unmodifiableMap(next);revision=rev;
    }
    private static void plain(String s,int max,boolean newline){if(s==null||s.isBlank()||s.codePointCount(0,s.length())>max||s.codePoints().anyMatch(c->(Character.isISOControl(c)&&!(newline&&c=='\n'))||(c>=0xd800&&c<=0xdfff))||s.indexOf('\u00a7')>=0)throw new IllegalArgumentException("Invalid plain text");}
    private record Decoded(long revision,Map<UUID,Treaty> treaties){}
    private static byte[] encode(long revision,Map<UUID,Treaty> values){JsonObject root=new JsonObject();root.addProperty("schema",1);root.addProperty("revision",revision);JsonArray all=new JsonArray();for(Treaty t:values.values())all.add(JSON.toJsonTree(t.history()));root.add("treaties",all);return (root+"\n").getBytes(StandardCharsets.UTF_8);}
    private static Decoded decode(byte[] bytes)throws IOException {
        try {
            if(bytes.length<1||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Bad size");
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root=BookConfig.strictObject(text);exact(root,"schema","revision","treaties");if(number(root.get("schema"))!=1)throw new IllegalArgumentException("Bad schema");long revision=number(root.get("revision"));if(revision<0)throw new IllegalArgumentException("Bad revision");
            JsonArray all=root.getAsJsonArray("treaties");if(all.size()>64)throw new IllegalArgumentException("Too many treaties");
            Memory memory=new Memory();TreatyService replay=new TreatyService(memory);
            for(JsonElement entry:all){JsonArray history=entry.getAsJsonArray();if(history.size()==0||history.size()>128)throw new IllegalArgumentException("Bad history"); UUID historyId=null;
                for(JsonElement element:history){JsonObject e=element.getAsJsonObject();exact(e,"action","actor","document");Action action=Action.valueOf(string(e.get("action")));UUID actor=uuid(e.get("actor"));Document d=document(e.getAsJsonObject("document"));
                    if(historyId==null){historyId=d.id();if(action!=Action.CREATED||replay.treaties.containsKey(historyId))throw new IllegalArgumentException("Invalid treaty history");}else if(!historyId.equals(d.id()))throw new IllegalArgumentException("Mixed treaty history"); long before=replay.revision;
                    switch(action){case CREATED->replay.create(d.id(),actor,d);case EDITED->replay.edit(d.id(),d.contentRevision()-1,actor,d);case SIGNED->replay.sign(d.id(),d.contentRevision(),actor);case SANCTIFIED->{Set<UUID> present=new HashSet<>();present.add(actor);d.parties().forEach(p->present.add(p.owner()));replay.sanctify(d.id(),d.contentRevision(),actor,present);}case BROKEN->replay.breach(d.id(),d.contentRevision(),actor);}
                    Treaty t=replay.treaties.get(d.id());if(replay.revision!=before+1||t==null||!t.document().equals(d)||!t.history().get(t.history().size()-1).equals(new Event(action,actor,d)))throw new IllegalArgumentException("Invalid history transition");
                }
            }
            if(replay.revision!=revision)throw new IllegalArgumentException("Revision mismatch");return new Decoded(revision,replay.treaties);
        }catch(Exception invalid){throw new IOException("Invalid treaty data; preserved",invalid);}
    }
    private static Document document(JsonObject o){exact(o,"id","contentRevision","title","terms","visibility","parties");List<Party> parties=new ArrayList<>();for(JsonElement e:o.getAsJsonArray("parties")){JsonObject p=e.getAsJsonObject();exact(p,"houseId","owner","publicName");parties.add(new Party(string(p.get("houseId")),uuid(p.get("owner")),string(p.get("publicName"))));}return new Document(uuid(o.get("id")),number(o.get("contentRevision")),string(o.get("title")),string(o.get("terms")),Visibility.valueOf(string(o.get("visibility"))),parties);}
    private static void exact(JsonObject o,String...fields){if(o==null||!o.keySet().equals(Set.of(fields)))throw new IllegalArgumentException("Unexpected fields");}
    private static String string(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected string");return e.getAsString();}
    private static UUID uuid(JsonElement e){String s=string(e);UUID u=UUID.fromString(s);if(!u.toString().equals(s))throw new IllegalArgumentException("Noncanonical UUID");return u;}
    private static long number(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("Expected canonical integer");return Long.parseLong(e.getAsString());}
    private static Storage storage(Path path){AtomicDataFile f=new AtomicDataFile(path,MAX_BYTES);return new Storage(){public byte[] load()throws IOException{return f.load();}public void save(byte[] bytes)throws IOException{f.save(bytes);}};}
    private static final class Memory implements Storage {public byte[] load(){return null;}public void save(byte[] bytes){}}
}
