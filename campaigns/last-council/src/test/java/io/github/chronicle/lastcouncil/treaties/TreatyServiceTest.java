package io.github.chronicle.lastcouncil.treaties;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.chronicle.lastcouncil.treaties.TreatyService.*;

class TreatyServiceTest {
    private final UUID a=UUID.randomUUID(), b=UUID.randomUUID(), c=UUID.randomUUID(), gm=UUID.randomUUID(), id=UUID.randomUUID();
    private Party p(UUID u){return new Party("house:" + u.toString(),u,"House " + u);}
    private Document doc(long rev,Visibility visibility,UUID... owners){return new Document(id,rev,"Agreement","Terms\nSecond line",visibility,Arrays.stream(owners).map(this::p).toList());}
    private static class Store implements Storage {
        byte[] bytes; boolean failBefore, failAfter, failLoad;
        public byte[] load() throws IOException {if(failLoad)throw new IOException();return bytes;}
        public void save(byte[] b)throws IOException {if(failBefore)throw new IOException();bytes=b.clone();if(failAfter)throw new IOException();}
    }
    @Test void consentAttendanceAndHistory() throws Exception {
        Store store=new Store();TreatyService s=new TreatyService(store);Document d=doc(1,Visibility.PUBLIC,a,b,c);
        s.create(id,a,d);s.create(id,a,d);s.sign(id,1,a);s.sign(id,1,a);s.sign(id,1,b);s.sign(id,1,c);
        assertEquals(4,s.revision());assertThrows(IllegalStateException.class,()->s.sanctify(id,1,gm,Set.of(a,b,gm)));
        TreatyService loaded=new TreatyService(store);
        assertThrows(IllegalArgumentException.class,()->loaded.sign(id,1,gm));
        assertThrows(IllegalArgumentException.class,()->loaded.sanctify(id,1,a,Set.of(a,b,c)));
        loaded.sanctify(id,1,gm,Set.of(a,b,c,gm));loaded.sanctify(id,1,gm,Set.of());
        loaded.breach(id,1,b);loaded.breach(id,1,b);
        assertEquals(Status.BROKEN,loaded.view(a,false).get(0).status());assertEquals(6,loaded.view(a,false).get(0).history().size());
        assertEquals(loaded.view(a,false),new TreatyService(store).view(a,false));
    }
    @Test void editsInvalidateAndProtectOldPrivateHistory() throws Exception {
        TreatyService s=new TreatyService(new Store());s.create(id,a,doc(1,Visibility.PRIVATE,a,b));s.sign(id,1,b);
        s.edit(id,1,a,doc(2,Visibility.PUBLIC,a,c));assertTrue(s.view(c,false).get(0).history().stream().allMatch(e->e.document().contentRevision()==2));
        assertEquals(3,s.view(gm,true).get(0).history().size());assertTrue(s.view(c,false).get(0).signatures().isEmpty());
        assertThrows(IllegalStateException.class,()->s.sign(id,1,a));assertThrows(IllegalArgumentException.class,()->s.sign(id,2,b));
        long before=s.revision();s.edit(id,2,a,doc(3,Visibility.PUBLIC,a,c));assertEquals(before,s.revision());
        s.sign(id,2,a);s.sign(id,2,c);s.sanctify(id,2,gm,Set.of(a,c,gm));assertThrows(IllegalStateException.class,()->s.edit(id,2,a,doc(3,Visibility.PUBLIC,a,c)));
    }
    @Test void currentPartyCanReplaceCreatorWithoutExposingPriorPrivateRevision() throws Exception {
        Store store=new Store(); TreatyService s=new TreatyService(store);
        s.create(id,a,doc(1,Visibility.PRIVATE,a,b)); s.sign(id,1,a); s.sign(id,1,b);
        s.edit(id,1,b,doc(2,Visibility.PUBLIC,b,c));
        View replacement=s.view(c,false).get(0);
        assertTrue(replacement.signatures().isEmpty());
        assertEquals(1,replacement.history().size());
        assertEquals(2,replacement.history().get(0).document().contentRevision());
        assertEquals(4,s.view(a,false).get(0).history().size());
        assertEquals(replacement,new TreatyService(store).view(c,false).get(0));
    }
    @Test void privateExistenceAndFailedWritesRequireReload() throws Exception {
        Store store=new Store();TreatyService s=new TreatyService(store);s.create(id,a,doc(1,Visibility.PRIVATE,a,b));assertTrue(s.view(c,false).isEmpty());
        store.failBefore=true;assertThrows(IOException.class,()->s.sign(id,1,a));store.failBefore=false;assertThrows(IOException.class,()->s.sign(id,1,b));
        s.reload();s.sign(id,1,a);store.failAfter=true;assertThrows(IOException.class,()->s.sign(id,1,b));assertFalse(s.view(a,false).get(0).signatures().containsKey(b));
        store.failAfter=false;s.reload();assertTrue(s.view(a,false).get(0).signatures().containsKey(b));
    }
    @Test void poisonedMutationsNeverAcknowledgeDuplicatesAndFailedReloadStaysPoisoned() throws Exception {
        Store store=new Store(); TreatyService s=new TreatyService(store); Document d=doc(1,Visibility.PUBLIC,a,b);
        s.create(id,a,d); s.sign(id,1,a);
        store.failBefore=true; assertThrows(IOException.class,()->s.sign(id,1,b)); store.failBefore=false;
        assertThrows(IOException.class,()->s.create(id,a,d));
        assertThrows(IOException.class,()->s.sign(id,1,a));
        assertThrows(IOException.class,()->s.edit(id,1,a,doc(2,Visibility.PUBLIC,a,b)));
        store.failLoad=true; assertThrows(IOException.class,s::reload); store.failLoad=false;
        assertThrows(IOException.class,()->s.sign(id,1,a));
        s.reload(); s.sign(id,1,b); s.sanctify(id,1,gm,Set.of(a,b,gm));
        store.failBefore=true; assertThrows(IOException.class,()->s.breach(id,1,a)); store.failBefore=false;
        assertThrows(IOException.class,()->s.sanctify(id,1,gm,Set.of()));
        s.reload(); s.breach(id,1,a);
        UUID other=UUID.randomUUID(); Document otherDoc=new Document(other,1,"Other","Terms",Visibility.PUBLIC,List.of(p(a),p(b)));
        store.failAfter=true; assertThrows(IOException.class,()->s.create(other,a,otherDoc)); store.failAfter=false;
        assertThrows(IOException.class,()->s.breach(id,1,a));
        assertThrows(IOException.class,()->s.create(id,a,d));
        s.reload(); s.create(other,a,otherDoc); s.breach(id,1,a);
        assertEquals(2,s.view(a,false).size());
    }
    @Test void unicodeLimitsAndNamespacedHouseIdsRoundTrip() throws Exception {
        String emoji="\uD83D\uDE00"; String name=emoji.repeat(80); String terms=emoji.repeat(2048);
        Party first=new Party("example:"+"a".repeat(120),a,name); Party second=p(b);
        Document d=new Document(id,1,name,terms,Visibility.PRIVATE,List.of(first,second));
        Store store=new Store(); TreatyService s=new TreatyService(store); s.create(id,a,d);
        assertEquals(d,new TreatyService(store).view(a,false).get(0).document());
        assertThrows(IllegalArgumentException.class,()->new Party("bad",a,"Name"));
        assertThrows(IllegalArgumentException.class,()->new Party("example:"+"a".repeat(121),a,"Name"));
        assertThrows(IllegalArgumentException.class,()->new Party("house:a",a,emoji.repeat(81)));
        assertThrows(IllegalArgumentException.class,()->new Document(id,1,emoji.repeat(81),"Terms",Visibility.PUBLIC,List.of(first,second)));
        assertThrows(IllegalArgumentException.class,()->new Document(id,1,"Title",emoji.repeat(2049),Visibility.PUBLIC,List.of(first,second)));
        assertThrows(IllegalArgumentException.class,()->new Party("house:a",a,"\uD800"));
        assertThrows(IllegalArgumentException.class,()->new Document(id,1,"Title","\uDC00",Visibility.PUBLIC,List.of(first,second)));
    }
    @Test void malformedStateIsRejected() throws Exception {
        Store store=new Store();TreatyService s=new TreatyService(store);s.create(id,a,doc(1,Visibility.PUBLIC,a,b));String json=new String(store.bytes,StandardCharsets.UTF_8);
        store.bytes=json.replace("\"schema\":1","\"schema\":1,\"schema\":1").getBytes(StandardCharsets.UTF_8);assertThrows(IOException.class,()->new TreatyService(store));
        store.bytes=json.replace("\"revision\":1","\"revision\":1.0").getBytes(StandardCharsets.UTF_8);assertThrows(IOException.class,()->new TreatyService(store));
        store.bytes=new byte[]{(byte)0xc3,(byte)0x28};assertThrows(IOException.class,()->new TreatyService(store));
    }
}
