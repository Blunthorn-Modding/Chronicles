package io.github.chronicle.lastcouncil.content;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.lastcouncil.house.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.security.*;
import java.util.*;

/** One atomic append publishes late membership and its already-drawn papers together.
 * The original roster, cast and automatic issuance receipt are never rewritten. */
public final class HouseEnrollmentStore {
    public interface Storage {
        byte[] load() throws IOException;
        void save(byte[] bytes) throws IOException;
        void verifyUnchanged() throws IOException;
    }
    public record Snapshot(HouseRoster roster, HouseCast cast) { }
    private static final int MAX_BYTES=4*1024*1024;
    private final Storage storage;
    private final Snapshot baseline;
    private final String fingerprint;
    private Snapshot current;
    private JsonArray entries=new JsonArray();

    public HouseEnrollmentStore(Path root,HouseRoster roster,HouseCast cast)throws IOException {
        this(storage(root.resolve("house-enrollments.json")),roster,cast);
    }
    public HouseEnrollmentStore(Storage storage,HouseRoster roster,HouseCast cast)throws IOException {
        this.storage=Objects.requireNonNull(storage);baseline=new Snapshot(roster,cast);
        validateSnapshot(baseline);fingerprint=fingerprint(roster,cast);
        byte[] bytes=storage.load();
        if(bytes==null)current=baseline;
        else {current=decode(bytes);entries=object(bytes).getAsJsonArray("entries").deepCopy();}
    }
    public Snapshot snapshot(){return current;}
    public void verifyUnchanged()throws IOException {storage.verifyUnchanged();}
    public void verifyBaseline(HouseRoster roster,HouseCast cast)throws IOException {if(!baseline.equals(new Snapshot(roster,cast)))throw new IOException("Original House records changed; enrollment preserved");}

    public void append(HouseRoster roster,HouseCast cast,long drawSeed)throws IOException {
        Snapshot next=new Snapshot(roster,cast);validateAddition(current,next);
        UUID owner=roster.assignments().keySet().stream().filter(id->!current.roster().assignments().containsKey(id)).findFirst().orElseThrow();
        JsonObject item=new JsonObject();item.addProperty("owner",owner.toString());
        item.addProperty("candidate",roster.assignments().get(owner).candidate());item.addProperty("drawSeed",drawSeed);
        JsonObject castJson=BookConfig.strictObject(new String(HouseCastStore.encode(cast),StandardCharsets.UTF_8));
        item.add("papers",castJson.getAsJsonObject("papers").get(owner.toString()).deepCopy());
        JsonArray updated=entries.deepCopy();updated.add(item);
        JsonObject root=new JsonObject();root.addProperty("schema",1);root.addProperty("baseFingerprint",fingerprint);root.add("entries",updated);
        byte[] bytes=(root+"\n").getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES||!decode(bytes).equals(next))throw new IOException("Late House enrollment exceeds its validated bounds");
        storage.save(bytes); // No new membership/papers become visible before this succeeds.
        entries=updated;current=next;
    }
    private Snapshot decode(byte[] bytes)throws IOException {
        try {
            JsonObject root=object(bytes);exact(root,"schema","baseFingerprint","entries");
            if(number(root.get("schema"))!=1||!fingerprint.equals(string(root.get("baseFingerprint"))))throw new IllegalArgumentException("Enrollment baseline differs");
            if(baseline.cast()==null)throw new IllegalArgumentException("Enrollment requires an issued cast");
            JsonArray all=root.getAsJsonArray("entries");if(all.size()>HouseRoster.MAX_HOUSES)throw new IllegalArgumentException("Too many enrollments");
            Snapshot value=baseline;
            for(JsonElement element:all){
                JsonObject item=element.getAsJsonObject();exact(item,"owner","candidate","drawSeed","papers");
                UUID owner=UUID.fromString(string(item.get("owner")));if(!owner.toString().equals(string(item.get("owner"))))throw new IllegalArgumentException("Noncanonical owner");
                number(item.get("drawSeed"));
                if(!item.get("candidate").isJsonPrimitive()||!item.get("candidate").getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected candidacy boolean");
                JsonObject paper=item.getAsJsonObject("papers");String house=string(paper.get("houseId"));
                var assignments=new HashMap<>(value.roster().assignments());
                if(assignments.putIfAbsent(owner,new HouseAssignment(owner,house,true,item.get("candidate").getAsBoolean()))!=null)throw new IllegalArgumentException("Owner already assigned");
                HouseRoster roster=new HouseRoster(Math.addExact(value.roster().revision(),1),assignments);
                JsonObject cast=BookConfig.strictObject(new String(HouseCastStore.encode(value.cast()),StandardCharsets.UTF_8));
                cast.addProperty("rosterRevision",roster.revision());cast.getAsJsonObject("papers").add(owner.toString(),paper.deepCopy());
                Snapshot next=new Snapshot(roster,HouseCastStore.decode(cast.toString().getBytes(StandardCharsets.UTF_8)));
                validateAddition(value,next);value=next;
            }
            return value;
        }catch(RuntimeException invalid){throw new IOException("Invalid late House enrollment; records preserved",invalid);}
    }
    private static void validateSnapshot(Snapshot value){
        HouseCast cast=value.cast();HouseRoster roster=value.roster();
        if(cast==null)return;
        if(cast.rosterRevision()!=roster.revision()||!cast.papers().keySet().equals(roster.assignments().keySet()))throw new IllegalArgumentException("Enrollment cast/roster differ");
        roster.assignments().forEach((owner,a)->{if(!a.houseId().equals(cast.papers().get(owner).houseId()))throw new IllegalArgumentException("House papers differ");});
    }
    private static void validateAddition(Snapshot before,Snapshot after){
        validateSnapshot(before);validateSnapshot(after);
        if(before.cast()==null||after.cast()==null||after.roster().revision()!=Math.addExact(before.roster().revision(),1)
            ||after.roster().assignments().size()!=before.roster().assignments().size()+1
            ||!after.cast().castId().equals(before.cast().castId())||after.cast().seed()!=before.cast().seed())throw new IllegalArgumentException("Expected one late House addition");
        before.roster().assignments().forEach((owner,a)->{
            if(!a.equals(after.roster().assignments().get(owner))||!before.cast().papers().get(owner).equals(after.cast().papers().get(owner)))throw new IllegalArgumentException("Existing assignment or papers changed");
        });
        for(var entry:after.roster().assignments().entrySet())if(!before.roster().assignments().containsKey(entry.getKey())&&!entry.getValue().councilMember())throw new IllegalArgumentException("New House requires its Council seat");
    }
    private static String fingerprint(HouseRoster roster,HouseCast cast){
        StringBuilder canonical=new StringBuilder().append(roster.revision()).append('\n');
        roster.assignments().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->canonical.append(e.getKey()).append('|').append(e.getValue().houseId()).append('|').append(e.getValue().councilMember()).append('|').append(e.getValue().candidate()).append('\n'));
        try {MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(canonical.toString().getBytes(StandardCharsets.UTF_8));if(cast!=null)digest.update(HouseCastStore.encode(cast));return HexFormat.of().formatHex(digest.digest());}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static JsonObject object(byte[] bytes)throws IOException {
        if(bytes.length>MAX_BYTES)throw new IOException("Enrollment file too large");
        try{return BookConfig.strictObject(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());}
        catch(CharacterCodingException|RuntimeException invalid){throw new IOException("Invalid enrollment JSON; preserved",invalid);}
    }
    private static void exact(JsonObject object,String...fields){if(!object.keySet().equals(Set.of(fields)))throw new IllegalArgumentException("Unexpected enrollment fields");}
    private static String string(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected string");return e.getAsString();}
    private static long number(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("-?(0|[1-9][0-9]*)"))throw new IllegalArgumentException("Expected integer");return Long.parseLong(e.getAsString());}
    private static Storage storage(Path path){AtomicDataFile file=new AtomicDataFile(path,MAX_BYTES);return new Storage(){public byte[] load()throws IOException{return file.load();}public void save(byte[] b)throws IOException{file.save(b);}public void verifyUnchanged()throws IOException{file.verifyUnchanged();}};}
}
