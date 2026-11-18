package io.github.chronicle.lastcouncil.foreign;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;

/** Reusable foreign realm definitions; diplomatic state belongs to the world. */
public final class ForeignRealmService {
    public record Realm(String id,String name,String description) {
        public Realm { ForeignAffairsService.id(id); ForeignAffairsService.text(name,80,false); if(name.indexOf('\n')>=0)throw new IllegalArgumentException("Invalid name"); ForeignAffairsService.text(description,4096,true); }
    }
    public record Catalog(long revision,List<Realm> realms) {
        public Catalog { if(revision<0)throw new IllegalArgumentException("Invalid revision"); realms=List.copyOf(realms); if(realms.size()>64||realms.stream().map(Realm::id).distinct().count()!=realms.size())throw new IllegalArgumentException("Invalid realms"); }
    }
    public interface Storage { byte[] load() throws IOException; void save(byte[] bytes) throws IOException; default void verify() throws IOException {} }
    private static final int MAX_BYTES=512*1024;
    private final Storage storage; private Catalog catalog; private boolean poisoned;
    public ForeignRealmService(Path path)throws IOException { this(storage(path)); }
    public ForeignRealmService(Storage storage)throws IOException { this.storage=Objects.requireNonNull(storage); reload(); }
    public synchronized void reload()throws IOException { try { byte[] bytes=storage.load(); catalog=bytes==null?new Catalog(0,List.of()):decode(bytes); if(bytes==null)storage.save(encode(catalog.revision(),catalog.realms())); poisoned=false; } catch(IOException|RuntimeException e) { poisoned=true; throw e; } }
    public synchronized void verify()throws IOException { writable(); }
    public synchronized long revision(){return catalog.revision();}
    public synchronized List<Realm> realms(){return catalog.realms();}
    public synchronized Realm get(String id){ForeignAffairsService.id(id); return catalog.realms().stream().filter(r->r.id().equals(id)).findFirst().orElse(null);}
    public synchronized Realm create(long expectedRevision,String name,String description)throws IOException { writable(); check(expectedRevision); if(catalog.realms().size()>=64)throw new IllegalStateException("Realm limit reached"); String id; do { id="last_council:realm_"+UUID.randomUUID(); } while(get(id)!=null); Realm realm=new Realm(id,name,description); List<Realm> next=new ArrayList<>(catalog.realms()); next.add(realm); save(next); return realm; }
    public synchronized Realm update(long expectedRevision,String id,String name,String description)throws IOException { writable(); check(expectedRevision); Realm old=require(id); Realm updated=new Realm(id,name,description); if(old.equals(updated))return old; List<Realm> next=new ArrayList<>(catalog.realms()); next.set(next.indexOf(old),updated); save(next); return updated; }
    public synchronized void delete(long expectedRevision,String id,Set<String> referencedIds)throws IOException { writable(); check(expectedRevision); Realm old=require(id); if(Objects.requireNonNull(referencedIds).contains(id))throw new IllegalStateException("Realm is referenced"); List<Realm> next=new ArrayList<>(catalog.realms()); next.remove(old); save(next); }
    public synchronized String exportJson(){return new String(encode(catalog.revision(),catalog.realms()),StandardCharsets.UTF_8);}
    private Realm require(String id) { Realm r=get(id); if(r==null)throw new IllegalArgumentException("Unknown realm"); return r; }
    private void check(long expected) { if(catalog.revision()!=expected)throw new IllegalStateException("Stale realm catalog revision"); }
    private void writable()throws IOException { if(poisoned)throw new IOException("Reload required after uncertain write"); storage.verify(); }
    private void save(List<Realm> realms)throws IOException { Catalog next=new Catalog(Math.addExact(catalog.revision(),1),realms); byte[] bytes=encode(next.revision(),next.realms()); if(bytes.length>MAX_BYTES)throw new IOException("Realm catalog too large"); if(!decode(bytes).equals(next))throw new IOException("Realm catalog candidate failed validation"); try {storage.save(bytes);}catch(IOException|RuntimeException e){poisoned=true;throw e;} catalog=next; }
    public static byte[] encode(long revision,List<Realm> realms) { Catalog c=new Catalog(revision,realms); JsonObject o=new JsonObject(); o.addProperty("schema",1); o.addProperty("revision",c.revision()); JsonArray a=new JsonArray(); for(Realm realm:c.realms()) { JsonObject r=new JsonObject(); r.addProperty("id",realm.id()); r.addProperty("name",realm.name()); r.addProperty("description",realm.description()); a.add(r); } o.add("realms",a); return (o+"\n").getBytes(StandardCharsets.UTF_8); }
    public static Catalog decode(byte[] bytes)throws IOException { try { if(bytes.length<1||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Invalid size"); String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); JsonObject o=BookConfig.strictObject(text); ForeignAffairsService.exact(o,"schema","revision","realms"); if(ForeignAffairsService.number(o.get("schema"))!=1)throw new IllegalArgumentException("Invalid schema"); long revision=ForeignAffairsService.number(o.get("revision")); JsonArray a=o.getAsJsonArray("realms"); if(a.size()>64)throw new IllegalArgumentException("Too many realms"); List<Realm> realms=new ArrayList<>(); for(JsonElement v:a){JsonObject r=v.getAsJsonObject(); ForeignAffairsService.exact(r,"id","name","description"); realms.add(new Realm(ForeignAffairsService.string(r.get("id")),ForeignAffairsService.string(r.get("name")),ForeignAffairsService.string(r.get("description"))));} return new Catalog(revision,realms); }catch(Exception e){throw new IOException("Invalid realm catalog; preserved",e);} }
    private static Storage storage(Path path){AtomicDataFile f=new AtomicDataFile(path,MAX_BYTES);return new Storage(){public byte[] load()throws IOException{return f.load();} public void save(byte[] b)throws IOException{f.save(b);}public void verify()throws IOException{f.verifyUnchanged();}};}
}
