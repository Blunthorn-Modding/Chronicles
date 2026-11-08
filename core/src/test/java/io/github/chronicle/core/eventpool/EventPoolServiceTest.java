package io.github.chronicle.core.eventpool;

import io.github.chronicle.core.play.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EventPoolServiceTest {
    static final UUID ACTOR=UUID.randomUUID(),PLAYER=UUID.randomUUID(),OP=UUID.randomUUID();
    static EventPoolDefinition pool(int a,int b){return new EventPoolDefinition("test:pool","Pool",List.of(new EventPoolDefinition.Entry("test:a",a),new EventPoolDefinition.Entry("test:b",b)));}
    static EventPoolService.Candidate candidate(String id){
        var definition=new EventDefinition(id,1,"Event","","Message","",ObservationCatalog.Audience.PARTICIPANTS,Optional.empty(),List.of());
        return new EventPoolService.Candidate(new EventConfiguration(definition,PlayState.Mode.MANUAL,Optional.empty(),0,0,"",true),1,ACTOR,List.of(PLAYER));
    }
    static Map<String,EventPoolService.Candidate> candidates(){return Map.of("test:a",candidate("test:a"),"test:b",candidate("test:b"));}
    static final class Memory implements EventPoolService.Storage {
        byte[] bytes;boolean failBefore,failAfter;
        public byte[] load(){return bytes;}
        public void verifyUnchanged(){}
        public void save(byte[] value)throws IOException{if(failBefore)throw new IOException("before");bytes=value.clone();if(failAfter)throw new IOException("after");}
    }
    @Test void weightedEdgesRetryAndReservations()throws Exception {
        var store=new Memory();var service=new EventPoolService(store);var a=service.claim(pool(2,3),"one",candidates(),n->{assertEquals(5,n);return 1;},()->OP);
        assertEquals("test:a",a.configuration().definition().id());
        assertEquals(a,service.claim(pool(8,1),"one",Map.of(),n->{throw new AssertionError();},()->{throw new AssertionError();}));
        var b=service.claim(pool(2,3),"two",candidates(),n->{assertEquals(3,n);return n-1;},UUID::randomUUID);
        assertEquals("test:b",b.configuration().definition().id());
        assertThrows(IllegalStateException.class,()->service.claim(pool(2,3),"three",candidates(),n->0,UUID::randomUUID));
        assertThrows(IllegalStateException.class,()->service.claim(new EventPoolDefinition("test:other","Other",List.of()),"one",Map.of(),n->0,UUID::randomUUID));
        var last=new EventPoolService(new Memory()).claim(pool(2,3),"last",candidates(),n->n-1,UUID::randomUUID);assertEquals("test:b",last.configuration().definition().id());
        assertThrows(IllegalArgumentException.class,()->new EventPoolDefinition("test:p","Pool",List.of(new EventPoolDefinition.Entry("test:a",1),new EventPoolDefinition.Entry("test:a",2))));
    }
    @Test void uncertainSavePoisonsUntilReloadAndDoesNotRedrawPublishedSelection()throws Exception {
        var before=new Memory();before.failBefore=true;var first=new EventPoolService(before);
        assertThrows(IOException.class,()->first.claim(pool(1,1),"one",candidates(),n->0,()->OP));
        assertThrows(IllegalStateException.class,()->first.claim(pool(1,1),"one",candidates(),n->1,UUID::randomUUID));
        before.failBefore=false;assertEquals(OP,new EventPoolService(before).claim(pool(1,1),"one",candidates(),n->0,()->OP).operationId());
        var after=new Memory();after.failAfter=true;var second=new EventPoolService(after);
        assertThrows(IOException.class,()->second.claim(pool(1,1),"one",candidates(),n->1,()->OP));
        assertThrows(IllegalStateException.class,second::draws);
        after.failAfter=false;var recovered=new EventPoolService(after).claim(pool(9,1),"one",Map.of(),n->{throw new AssertionError();},()->{throw new AssertionError();});
        assertEquals(OP,recovered.operationId());assertEquals(candidate("test:b").configuration(),recovered.configuration());assertEquals(List.of(PLAYER),recovered.recipients());
    }
    @Test void externalChangeAndMalformedJournalArePreserved(@TempDir Path dir)throws Exception {
        Path path=dir.resolve("pools.json");var service=new EventPoolService(new EventPoolStore(path));Files.writeString(path,"{}");
        assertThrows(IOException.class,()->service.claim(pool(1,1),"one",candidates(),n->0,UUID::randomUUID));assertEquals("{}",Files.readString(path));
        assertThrows(IOException.class,()->new EventPoolService(new EventPoolStore(path)));
        assertThrows(IOException.class,()->EventPoolStore.decode(new byte[]{(byte)0xc3,0x28}));
        assertThrows(IOException.class,()->EventPoolStore.decode("{\"schema\":1,\"schema\":1,\"draws\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test void systemActorRoundTripsAndDisabledManualCueRefuses()throws Exception {
        var c=candidate("test:a");
        var disabled=new EventConfiguration(c.configuration().definition(),PlayState.Mode.MANUAL,Optional.empty(),0,0,"",false);
        var service=new EventPoolService(new Memory());
        var saved=service.claim(pool(1,1),"system",Map.of("test:a",new EventPoolService.Candidate(c.configuration(),1,null,List.of(PLAYER))),n->0,()->OP);
        assertNull(saved.actor());
        assertThrows(IllegalArgumentException.class,()->new EventPoolService.Candidate(disabled,1,null,List.of(PLAYER)));
        assertEquals(saved,EventPoolStore.decode(EventPoolStore.encode(service.draws())).get("system"));
    }
    @Test void existingSelectionCannotBeDispatchedFromExternallyChangedBytes(@TempDir Path dir)throws Exception {
        Path path=dir.resolve("pools.json");var service=new EventPoolService(new EventPoolStore(path));
        service.claim(pool(1,1),"one",candidates(),n->0,()->OP);
        Files.writeString(path,"{}");
        assertThrows(IOException.class,service::verifyUnchanged);
        assertEquals("{}",Files.readString(path));
    }

}
