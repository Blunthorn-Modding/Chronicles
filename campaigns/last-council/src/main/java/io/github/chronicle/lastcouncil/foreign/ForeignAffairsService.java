package io.github.chronicle.lastcouncil.foreign;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;

/** World-owned, replay-validated diplomatic decisions. */
public final class ForeignAffairsService {
    public enum Kind { DECLARE_WAR, MAKE_PEACE }
    public record Effect(UUID ballot, UUID motion, String realmId, long expectedRevision, Kind kind, String motionTitle) {
        public Effect { Objects.requireNonNull(ballot); Objects.requireNonNull(motion); id(realmId); if(expectedRevision<0)throw new IllegalArgumentException("Invalid revision"); Objects.requireNonNull(kind); text(motionTitle, 256, false); }
    }
    public record View(String realmId, long revision, boolean atWar, List<Effect> history) {
        public View { id(realmId); if(revision<0)throw new IllegalArgumentException("Invalid revision"); history=List.copyOf(history); }
    }
    public interface Storage { byte[] load() throws IOException; void save(byte[] bytes) throws IOException; default void verify() throws IOException {} }
    private static final int MAX_BYTES=2*1024*1024, MAX_EFFECTS=256;
    private final Storage storage;
    private List<Effect> effects=List.of();
    private boolean poisoned;
    public ForeignAffairsService(Path path) throws IOException { this(storage(path)); }
    public ForeignAffairsService(Storage storage) throws IOException { this.storage=Objects.requireNonNull(storage); reload(); }
    public synchronized void reload() throws IOException { try { byte[] b=storage.load(); effects=b==null?List.of():decode(b); if(b==null)storage.save(encode(effects)); poisoned=false; } catch(IOException|RuntimeException e) { poisoned=true; throw e; } }
    public synchronized void verify() throws IOException { writable(); }
    public synchronized List<Effect> effects() { return effects; }
    public synchronized Set<String> referencedRealms() { Set<String> ids=new LinkedHashSet<>(); effects.forEach(e->ids.add(e.realmId())); return Collections.unmodifiableSet(ids); }
    public synchronized View view(String realmId) { id(realmId); List<Effect> history=effects.stream().filter(e->e.realmId().equals(realmId)).toList(); return new View(realmId,history.size(),!history.isEmpty()&&history.getLast().kind()==Kind.DECLARE_WAR,history); }
    public synchronized void apply(Effect effect) throws IOException {
        writable(); Objects.requireNonNull(effect);
        for(Effect old:effects) if(old.ballot().equals(effect.ballot())) { if(old.equals(effect))return; throw new IllegalArgumentException("Ballot already used"); }
        View before=view(effect.realmId());
        if(before.revision()!=effect.expectedRevision())throw new IllegalStateException("Stale realm revision");
        if(before.atWar()==(effect.kind()==Kind.DECLARE_WAR))throw new IllegalStateException("Invalid diplomatic transition");
        if(effects.size()>=MAX_EFFECTS)throw new IllegalStateException("Foreign affairs history limit reached");
        List<Effect> next=new ArrayList<>(effects); next.add(effect); byte[] bytes=encode(next);
        if(bytes.length>MAX_BYTES)throw new IOException("Foreign affairs data too large");
        // Validate the exact candidate that will be published.
        if(!decode(bytes).equals(next))throw new IOException("Foreign affairs candidate failed validation");
        try { storage.save(bytes); } catch(IOException|RuntimeException e) { poisoned=true; throw e; }
        effects=List.copyOf(next);
    }
    private void writable() throws IOException { if(poisoned)throw new IOException("Reload required after uncertain write"); storage.verify(); }
    private static byte[] encode(List<Effect> effects) { JsonObject o=new JsonObject(); o.addProperty("schema",1); JsonArray a=new JsonArray(); for(Effect e:effects) { JsonObject x=new JsonObject(); x.addProperty("ballot",e.ballot().toString()); x.addProperty("motion",e.motion().toString()); x.addProperty("realmId",e.realmId()); x.addProperty("expectedRevision",e.expectedRevision()); x.addProperty("kind",e.kind().name()); x.addProperty("motionTitle",e.motionTitle()); a.add(x); } o.add("effects",a); return (o+"\n").getBytes(StandardCharsets.UTF_8); }
    private static List<Effect> decode(byte[] b) throws IOException { try {
        if(b.length<1||b.length>MAX_BYTES)throw new IllegalArgumentException("Invalid size");
        JsonObject o=BookConfig.strictObject(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString()); exact(o,"schema","effects"); if(number(o.get("schema"))!=1)throw new IllegalArgumentException("Invalid schema"); JsonArray a=o.getAsJsonArray("effects"); if(a.size()>MAX_EFFECTS)throw new IllegalArgumentException("Too many effects");
        List<Effect> result=new ArrayList<>(); Set<UUID> ballots=new HashSet<>(); Map<String,Long> revisions=new HashMap<>(); Map<String,Boolean> war=new HashMap<>();
        for(JsonElement v:a) { JsonObject x=v.getAsJsonObject(); exact(x,"ballot","motion","realmId","expectedRevision","kind","motionTitle"); Effect e=new Effect(uuid(x.get("ballot")),uuid(x.get("motion")),string(x.get("realmId")),number(x.get("expectedRevision")),Kind.valueOf(string(x.get("kind"))),string(x.get("motionTitle"))); long revision=revisions.getOrDefault(e.realmId(),0L); boolean atWar=war.getOrDefault(e.realmId(),false); if(!ballots.add(e.ballot())||e.expectedRevision()!=revision||atWar==(e.kind()==Kind.DECLARE_WAR))throw new IllegalArgumentException("Invalid history"); revisions.put(e.realmId(),revision+1); war.put(e.realmId(),!atWar); result.add(e); }
        return List.copyOf(result);
    } catch(Exception e) { throw new IOException("Invalid foreign affairs data; preserved",e); } }
    static void id(String s) { if(s==null||s.length()>128||!s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid realm ID"); }
    static void text(String s,int max,boolean empty) { if(s==null||(!empty&&s.isBlank())||s.codePointCount(0,s.length())>max||s.codePoints().anyMatch(c->(Character.isISOControl(c)&&c!='\n')||(c>=0xd800&&c<=0xdfff))||s.indexOf('\u00a7')>=0)throw new IllegalArgumentException("Invalid text"); }
    static void exact(JsonObject o,String... fields) { if(o==null||!o.keySet().equals(Set.of(fields)))throw new IllegalArgumentException("Unexpected fields"); }
    static String string(JsonElement e) { if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected string"); return e.getAsString(); }
    static long number(JsonElement e) { if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("Expected integer"); return Long.parseLong(e.getAsString()); }
    static UUID uuid(JsonElement e) { String s=string(e); UUID u=UUID.fromString(s); if(!u.toString().equals(s))throw new IllegalArgumentException("Noncanonical UUID"); return u; }
    private static Storage storage(Path path) { AtomicDataFile f=new AtomicDataFile(path,MAX_BYTES); return new Storage() { public byte[] load() throws IOException {return f.load();} public void save(byte[] b)throws IOException {f.save(b);} public void verify()throws IOException {f.verifyUnchanged();} }; }
}
