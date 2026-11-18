package io.github.chronicle.lastcouncil.server;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import io.github.chronicle.lastcouncil.conflicts.ConflictService;
import io.github.chronicle.lastcouncil.conflicts.ConflictService.House;
import io.github.chronicle.lastcouncil.treaties.TreatyService;
import static io.github.chronicle.lastcouncil.treaties.TreatyService.*;

class ConflictOperationsTest {
    @TempDir Path dir;
    final House a=new House("house:a",UUID.randomUUID(),"A"),b=new House("house:b",UUID.randomUUID(),"B");final UUID gm=UUID.randomUUID();
    static class Store implements ConflictService.Storage{byte[] bytes;boolean fail,unchecked;public byte[] load(){return bytes;}public void save(byte[] b)throws IOException{bytes=b.clone();if(fail){if(unchecked)throw new IllegalStateException("Injected failure after write");throw new IOException("Injected failure after write");}}}
    List<Party> parties(){return List.of(new Party(a.houseId(),a.owner(),a.publicName()),new Party(b.houseId(),b.owner(),b.publicName()));}
    @Test void declarationRecoveryRetainsExactPrivateBreachAndOnePublicWar()throws Exception{
        var treaties=new TreatyService(dir.resolve("treaties.json"));UUID tid=UUID.randomUUID();var d=new Document(tid,1,"Private pact","Secret terms",Visibility.PRIVATE,parties(),Purpose.NON_AGGRESSION,null);treaties.create(tid,a.owner(),d);treaties.sign(tid,1,a.owner());treaties.sign(tid,1,b.owner());treaties.sanctify(tid,1,gm,Set.of(a.owner(),b.owner(),gm));
        Store store=new Store();var conflicts=new ConflictService(store);var journal=new ConflictOperations(dir.resolve("operations.json"));UUID id=UUID.randomUUID();var op=new ConflictOperations.Operation(ConflictOperations.Kind.DECLARATION,id,a.owner(),a,b,"Road closed",List.of(new ConflictOperations.Breach(tid,1)),null,Set.of(),0);
        store.fail=true;store.unchecked=true;assertThrows(IllegalStateException.class,()->journal.begin(op,conflicts,treaties));assertNotNull(journal.pending());assertEquals(Status.BROKEN,treaties.get(tid).status());store.fail=false;
        var reloaded=new ConflictService(store);var recovered=new ConflictOperations(dir.resolve("operations.json"));recovered.recover(reloaded,new TreatyService(dir.resolve("treaties.json")));assertNull(recovered.pending());assertEquals(1,reloaded.records().size());assertEquals("Road closed",reloaded.publicViews().getFirst().record().history().getFirst().detail());assertTrue(treaties.view(gm,false).isEmpty());
    }
    @Test void agreementRecoveryDoesNotRepeatSanctificationOrLoseHistory()throws Exception{
        Store store=new Store();var conflicts=new ConflictService(store);UUID id=UUID.randomUUID();conflicts.declare(id,id,a,b,"Road");var treaties=new TreatyService(dir.resolve("treaties.json"));UUID tid=UUID.randomUUID();var d=new Document(tid,1,"Peace","Promises",Visibility.PRIVATE,parties(),Purpose.PEACE,new ConflictLink(id,1));treaties.create(tid,a.owner(),d);treaties.sign(tid,1,a.owner());treaties.sign(tid,1,b.owner());
        var journal=new ConflictOperations(dir.resolve("operations.json"));var op=new ConflictOperations.Operation(ConflictOperations.Kind.AGREEMENT,UUID.randomUUID(),gm,null,null,"",List.of(),d,Set.of(gm,a.owner(),b.owner()),1);
        store.fail=true;assertThrows(IOException.class,()->journal.begin(op,conflicts,treaties));assertEquals(Status.ACTIVE,treaties.get(tid).status());store.fail=false;
        var loaded=new ConflictService(store);new ConflictOperations(dir.resolve("operations.json")).recover(loaded,new TreatyService(dir.resolve("treaties.json")));assertEquals(ConflictService.Status.ENDED,loaded.records().getFirst().record().status());assertEquals(4,new TreatyService(dir.resolve("treaties.json")).get(tid).history().size());assertEquals(2,loaded.records().getFirst().history().size());
    }
    @Test void publicPactsBetweenOtherHousesDoNotWarnOrBreakForTheActor()throws Exception{
        var treaties=new TreatyService(dir.resolve("treaties.json"));UUID id=UUID.randomUUID();var doc=new Document(id,1,"Public pact","Terms",Visibility.PUBLIC,parties(),Purpose.NON_AGGRESSION,null);treaties.create(id,a.owner(),doc);treaties.sign(id,1,a.owner());treaties.sign(id,1,b.owner());treaties.sanctify(id,1,gm,Set.of(gm,a.owner(),b.owner()));
        assertTrue(ConflictOperations.applicableNonAggression(treaties.get(id),a,b));var other=new House("house:other",UUID.randomUUID(),"Other");assertFalse(ConflictOperations.applicableNonAggression(treaties.get(id),other,b));treaties.breach(id,1,a.owner());assertFalse(ConflictOperations.applicableNonAggression(treaties.get(id),a,b));
    }
    @Test void malformedIntentIsRejectedAndPreserved()throws Exception{
        var conflicts=new ConflictService(new Store());var treaties=new TreatyService(dir.resolve("treaties.json"));var journal=new ConflictOperations(dir.resolve("operations.json"));UUID id=UUID.randomUUID();var op=new ConflictOperations.Operation(ConflictOperations.Kind.DECLARATION,id,a.owner(),a,b,"Road",List.of(),null,Set.of(),0);
        Store failing=new Store();failing.fail=true;var c=new ConflictService(failing);assertThrows(IOException.class,()->journal.begin(op,c,treaties));byte[] original=Files.readAllBytes(dir.resolve("operations.json"));
        var root=com.google.gson.JsonParser.parseString(new String(original,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();root.getAsJsonObject("pending").addProperty("extra",true);byte[] bad=root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(dir.resolve("operations.json"),bad);assertThrows(IOException.class,()->new ConflictOperations(dir.resolve("operations.json")));assertArrayEquals(bad,Files.readAllBytes(dir.resolve("operations.json")));
        Files.write(dir.resolve("operations.json"),new byte[]{(byte)0xc3,0x28});assertThrows(IOException.class,()->new ConflictOperations(dir.resolve("operations.json")));
    }
    @Test void missingConsentNeverWritesIntentOrSanctifies()throws Exception{
        var conflicts=new ConflictService(new Store());UUID id=UUID.randomUUID();conflicts.declare(id,id,a,b,"Road");var treaties=new TreatyService(dir.resolve("treaties.json"));UUID tid=UUID.randomUUID();var d=new Document(tid,1,"Peace","Promises",Visibility.PUBLIC,parties(),Purpose.PEACE,new ConflictLink(id,1));treaties.create(tid,a.owner(),d);treaties.sign(tid,1,a.owner());var ops=new ConflictOperations(dir.resolve("operations.json"));
        assertThrows(IllegalStateException.class,()->ops.begin(new ConflictOperations.Operation(ConflictOperations.Kind.AGREEMENT,UUID.randomUUID(),gm,null,null,"",List.of(),d,Set.of(gm,a.owner(),b.owner()),1),conflicts,treaties));assertNull(ops.pending());assertEquals(Status.DRAFT,treaties.get(tid).status());assertEquals(ConflictService.Status.ACTIVE,conflicts.records().getFirst().record().status());
    }
}
