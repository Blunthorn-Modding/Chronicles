package io.github.chronicle.lastcouncil.conflicts;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.chronicle.lastcouncil.conflicts.ConflictService.*;

class ConflictServiceTest {
    static final House A=new House("house:ash",UUID.randomUUID(),"Elian"),B=new House("house:rowan",UUID.randomUUID(),"Mara"),C=new House("house:vale",UUID.randomUUID(),"Oren"),D=new House("house:reed",UUID.randomUUID(),"Sera");
    static final UUID GM=UUID.randomUUID();
    static class Store implements Storage{byte[] bytes;boolean before,after;public byte[] load(){return bytes;}public void save(byte[] b)throws IOException{if(before)throw new IOException();bytes=b.clone();if(after)throw new IOException();}}
    @Test void sidesFoundersClassificationAndIndependentWars()throws Exception{
        Store store=new Store();var s=new ConflictService(store);UUID id=UUID.randomUUID();s.declare(id,id,A,B,"The road is closed.");s.declare(id,id,A,B,"The road is closed.");
        assertThrows(IllegalStateException.class,()->s.declare(UUID.randomUUID(),UUID.randomUUID(),B,A,"Again"));
        assertThrows(IllegalStateException.class,()->s.withdraw(id,1,A));assertThrows(IllegalStateException.class,()->s.change(id,1,B,A.houseId()));
        s.join(id,1,C,A.houseId());s.join(id,2,D,B.houseId());assertTrue(ConflictService.classify(s.records().getFirst().record(),4));
        assertThrows(IllegalStateException.class,()->s.join(id,3,C,B.houseId()));s.change(id,3,C,B.houseId());s.withdraw(id,4,D);assertFalse(ConflictService.classify(s.records().getFirst().record(),4));
        UUID other=UUID.randomUUID();s.declare(other,other,A,C,"Another conflict");
        assertEquals(s.records(),new ConflictService(store).records());assertEquals(s.publicViews(),new ConflictService(store).publicViews());
    }
    @Test void allCurrentConsentIsPinnedAndNewWarsFollowEitherAgreement()throws Exception{
        var s=new ConflictService(new Store());UUID id=UUID.randomUUID(),treaty=UUID.randomUUID();s.declare(id,id,A,B,"Road");s.join(id,1,C,A.houseId());
        assertThrows(IllegalStateException.class,()->s.recordAgreement(id,2,GM,treaty,1,AgreementKind.PEACE,Set.of(A.houseId(),B.houseId())));
        assertThrows(IllegalStateException.class,()->s.recordAgreement(id,2,GM,treaty,2,AgreementKind.PEACE,Set.of(A.houseId(),B.houseId())));
        s.recordAgreement(id,2,GM,treaty,2,AgreementKind.CEASEFIRE,Set.of(A.houseId(),B.houseId(),C.houseId()));
        long revision=s.revision();s.recordAgreement(id,2,GM,treaty,2,AgreementKind.CEASEFIRE,Set.of(A.houseId(),B.houseId(),C.houseId()));assertEquals(revision,s.revision());
        UUID second=UUID.randomUUID();s.declare(second,second,A,B,"New hostilities");assertEquals(2,s.records().size());assertEquals(Status.PAUSED,s.records().getFirst().record().status());
        s.recordAgreement(second,1,GM,UUID.randomUUID(),1,AgreementKind.PEACE,Set.of(A.houseId(),B.houseId()));UUID third=UUID.randomUUID();s.declare(third,third,A,B,"New war");assertEquals(3,s.records().size());
        String json=new Gson().toJson(s.publicViews());assertFalse(json.contains(treaty.toString()));assertFalse(json.contains("\"agreement\":"));
    }
    @Test void failurePoisonsAndReloadRecognizesAfterWriteOutcome()throws Exception{
        Store store=new Store();var s=new ConflictService(store);UUID id=UUID.randomUUID();s.declare(id,id,A,B,"Road");store.after=true;assertThrows(IOException.class,()->s.join(id,1,C,A.houseId()));store.after=false;
        assertThrows(IOException.class,()->s.withdraw(id,1,A));s.reload();assertEquals(2,s.records().getFirst().record().revision());
        s.preview(copy->copy.withdraw(id,2,C));assertEquals(2,s.records().getFirst().record().revision());s.withdraw(id,2,C);assertEquals(3,new ConflictService(store).records().getFirst().record().revision());
    }
    @Test void strictHistoryReplayRejectsForgedMembershipAndUnknownFields()throws Exception{
        Store store=new Store();var s=new ConflictService(store);UUID id=UUID.randomUUID();s.declare(id,id,A,B,"Road");s.join(id,1,C,A.houseId());byte[] original=store.bytes.clone();
        JsonObject root=JsonParser.parseString(new String(original,StandardCharsets.UTF_8)).getAsJsonObject();var entry=root.getAsJsonArray("conflicts").get(0).getAsJsonObject();entry.getAsJsonObject("record").addProperty("membershipRevision",99);store.bytes=root.toString().getBytes(StandardCharsets.UTF_8);assertThrows(IOException.class,()->new ConflictService(store));
        store.bytes=original;root=JsonParser.parseString(new String(original,StandardCharsets.UTF_8)).getAsJsonObject();root.addProperty("extra",true);store.bytes=root.toString().getBytes(StandardCharsets.UTF_8);assertThrows(IOException.class,()->new ConflictService(store));
        store.bytes=new byte[]{(byte)0xc3,0x28};assertThrows(IOException.class,()->new ConflictService(store));
    }
}
