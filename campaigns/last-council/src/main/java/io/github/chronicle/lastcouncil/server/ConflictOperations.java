package io.github.chronicle.lastcouncil.server;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.lastcouncil.conflicts.ConflictService;
import io.github.chronicle.lastcouncil.conflicts.ConflictService.House;
import io.github.chronicle.lastcouncil.treaties.TreatyService;
import java.io.IOException;
import java.nio.charset.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;

/** Pinned private intent coordinates two durable journals. Recovery never chooses new terms or parties. */
public final class ConflictOperations {
    public enum Kind { DECLARATION, AGREEMENT }
    public record Breach(UUID treaty,long revision){public Breach{Objects.requireNonNull(treaty);if(revision<1)throw new IllegalArgumentException();}}
    public record Operation(Kind kind,UUID id,UUID actor,House initiator,House target,String reason,List<Breach> breaches,TreatyService.Document document,Set<UUID> present,long conflictRevision){
        public Operation{Objects.requireNonNull(kind);Objects.requireNonNull(id);Objects.requireNonNull(actor);breaches=List.copyOf(breaches);present=Set.copyOf(present);if(breaches.size()>64||present.size()>33||breaches.stream().map(Breach::treaty).distinct().count()!=breaches.size())throw new IllegalArgumentException();if(kind==Kind.DECLARATION){Objects.requireNonNull(initiator);Objects.requireNonNull(target);Objects.requireNonNull(reason);if(document!=null||!present.isEmpty()||conflictRevision!=0||!actor.equals(initiator.owner()))throw new IllegalArgumentException();}else{Objects.requireNonNull(document);if(document.conflictLink()==null||initiator!=null||target!=null||!reason.isEmpty()||!breaches.isEmpty()||conflictRevision<1)throw new IllegalArgumentException();}}
    }
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private final AtomicDataFile file;
    private Operation pending;
    private boolean poisoned;
    public ConflictOperations(Path path)throws IOException{file=new AtomicDataFile(path,256*1024);byte[] bytes=file.load();if(bytes==null){write(null);return;}try{var root=BookConfig.strictObject(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());if(!root.keySet().equals(Set.of("schema","pending"))||!root.get("schema").toString().equals("1"))throw new IllegalArgumentException();pending=root.get("pending").isJsonNull()?null:decode(root.getAsJsonObject("pending"));}catch(RuntimeException|CharacterCodingException e){throw new IOException("Invalid conflict operation; preserved",e);}}
    public Operation pending(){return pending;}
    public boolean uncertain(){return poisoned;}
    public void begin(Operation operation,ConflictService conflicts,TreatyService treaties)throws IOException{
        if(poisoned||pending!=null)throw new IOException("Conflict operation needs recovery");file.verifyUnchanged();
        validate(operation,conflicts,treaties);write(operation);apply(conflicts,treaties);
    }
    public void recover(ConflictService conflicts,TreatyService treaties)throws IOException{if(poisoned)throw new IOException("Reload conflict operation before recovery");file.verifyUnchanged();if(pending!=null)apply(conflicts,treaties);}
    private void apply(ConflictService conflicts,TreatyService treaties)throws IOException{
        conflicts.verify();treaties.verify();Operation op=pending;if(op.kind()==Kind.DECLARATION){
            for(Breach b:op.breaches()){var t=treaties.get(b.treaty());if(t==null||t.document().contentRevision()!=b.revision())throw new IOException("Pinned agreement changed; preserve records");treaties.breach(b.treaty(),b.revision(),op.actor());}
            conflicts.declare(op.id(),op.id(),op.initiator(),op.target(),op.reason());
        }else{
            var d=op.document();var current=treaties.get(d.id());if(current==null||!current.document().equals(d))throw new IOException("Pinned agreement changed; preserve records");
            treaties.sanctify(d.id(),d.contentRevision(),op.actor(),op.present());
            var link=d.conflictLink();conflicts.recordAgreement(link.conflictId(),op.conflictRevision(),op.actor(),d.id(),link.membershipRevision(),agreementKind(d.purpose()),houseIds(d));
        }
        write(null);
    }
    public static void validate(Operation op,ConflictService conflicts,TreatyService treaties)throws IOException{
        conflicts.verify();treaties.verify();
        if(op.kind()==Kind.DECLARATION){conflicts.preview(c->c.declare(op.id(),op.id(),op.initiator(),op.target(),op.reason()));
            treaties.preview(t->{for(Breach b:op.breaches())t.breach(b.treaty(),b.revision(),op.actor());});
        }else{var d=op.document();var current=treaties.get(d.id());if(current==null||!current.document().equals(d))throw new IllegalStateException("Agreement changed");
            treaties.preview(t->t.sanctify(d.id(),d.contentRevision(),op.actor(),op.present()));var link=d.conflictLink();
            conflicts.preview(c->c.recordAgreement(link.conflictId(),op.conflictRevision(),op.actor(),d.id(),link.membershipRevision(),agreementKind(d.purpose()),houseIds(d)));}
    }
    public static ConflictService.AgreementKind agreementKind(TreatyService.Purpose purpose){return switch(purpose){case PEACE->ConflictService.AgreementKind.PEACE;case CEASEFIRE->ConflictService.AgreementKind.CEASEFIRE;default->throw new IllegalArgumentException("Not a peace or ceasefire agreement");};}
    public static Set<String> houseIds(TreatyService.Document d){Set<String> ids=new HashSet<>();d.parties().forEach(p->ids.add(p.houseId()));return Set.copyOf(ids);}
    private static Operation decode(JsonObject o){
        exact(o,"kind","id","actor","initiator","target","reason","breaches","document","present","conflictRevision");
        var breaches=new ArrayList<Breach>();for(var e:o.getAsJsonArray("breaches")){var b=e.getAsJsonObject();exact(b,"treaty","revision");breaches.add(new Breach(uuid(b.get("treaty")),number(b.get("revision"))));}
        Set<UUID> present=new HashSet<>();for(var e:o.getAsJsonArray("present"))if(!present.add(uuid(e)))throw new IllegalArgumentException("Duplicate attendance");
        return new Operation(Kind.valueOf(text(o.get("kind"))),uuid(o.get("id")),uuid(o.get("actor")),o.get("initiator").isJsonNull()?null:house(o.getAsJsonObject("initiator")),o.get("target").isJsonNull()?null:house(o.getAsJsonObject("target")),text(o.get("reason")),breaches,o.get("document").isJsonNull()?null:TreatyService.decodeStoredDocument(o.getAsJsonObject("document")),present,number(o.get("conflictRevision")));
    }
    private static House house(JsonObject o){exact(o,"houseId","owner","publicName");return new House(text(o.get("houseId")),uuid(o.get("owner")),text(o.get("publicName")));}
    private static void exact(JsonObject o,String...keys){if(o==null||!o.keySet().equals(Set.of(keys)))throw new IllegalArgumentException("Unexpected intent fields");}
    private static String text(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected text");return e.getAsString();}
    private static UUID uuid(JsonElement e){String s=text(e);UUID id=UUID.fromString(s);if(!id.toString().equals(s))throw new IllegalArgumentException("Noncanonical identity");return id;}
    private static long number(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("Expected integer");return Long.parseLong(e.getAsString());}
    private void write(Operation op)throws IOException{var root=new JsonObject();root.addProperty("schema",1);root.add("pending",JSON.toJsonTree(op));try{file.save((root+"\n").getBytes(StandardCharsets.UTF_8));}catch(IOException|RuntimeException e){poisoned=true;throw e;}pending=op;}
}
