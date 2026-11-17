package io.github.chronicle.lastcouncil.treaties;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.chronicle.lastcouncil.treaties.TreatyService.*;

class TypedTreatyTest {
    static class Store implements Storage{byte[] bytes;public byte[] load(){return bytes;}public void save(byte[] bytes){this.bytes=bytes.clone();}}
    final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),gm=UUID.randomUUID(),id=UUID.randomUUID();
    List<Party> parties(){return List.of(new Party("house:a",a,"A"),new Party("house:b",b,"B"));}
    @Test void legacyReaderPreservesPrivateSignaturesAndWritesStrictSchemaTwo()throws Exception{
        Store store=new Store();var s=new TreatyService(store);var d=new Document(id,1,"Agreement","Exact terms",Visibility.PRIVATE,parties());s.create(id,a,d);s.sign(id,1,a);
        JsonObject root=JsonParser.parseString(new String(store.bytes,StandardCharsets.UTF_8)).getAsJsonObject();root.addProperty("schema",1);for(var history:root.getAsJsonArray("treaties"))for(var event:history.getAsJsonArray()){var doc=event.getAsJsonObject().getAsJsonObject("document");doc.remove("purpose");doc.remove("conflictLink");}store.bytes=root.toString().getBytes(StandardCharsets.UTF_8);
        var legacy=new TreatyService(store);assertEquals(Purpose.GENERAL,legacy.get(id).document().purpose());assertTrue(legacy.get(id).signatures().containsKey(a));assertTrue(legacy.view(gm,false).isEmpty());legacy.sign(id,1,b);assertEquals(legacy.get(id),new TreatyService(store).get(id));assertEquals(2,JsonParser.parseString(new String(store.bytes,StandardCharsets.UTF_8)).getAsJsonObject().get("schema").getAsInt());
    }
    @Test void typeOrConflictChangesInvalidateExactConsent()throws Exception{
        Store store=new Store();var s=new TreatyService(store);s.create(id,a,new Document(id,1,"Pact","Terms",Visibility.PRIVATE,parties()));s.sign(id,1,a);
        s.edit(id,1,a,new Document(id,2,"Pact","Terms",Visibility.PRIVATE,parties(),Purpose.NON_AGGRESSION,null));assertTrue(s.get(id).signatures().isEmpty());
        UUID conflict=UUID.randomUUID();s.edit(id,2,a,new Document(id,3,"Pact","Terms",Visibility.PRIVATE,parties(),Purpose.PEACE,new ConflictLink(conflict,1)));s.sign(id,3,a);s.sign(id,3,b);
        s.edit(id,3,a,new Document(id,4,"Pact","Terms",Visibility.PRIVATE,parties(),Purpose.PEACE,new ConflictLink(conflict,2)));assertTrue(s.get(id).signatures().isEmpty());assertThrows(IllegalStateException.class,()->s.sign(id,3,a));
        assertEquals(s.get(id),new TreatyService(store).get(id));
        assertThrows(IllegalArgumentException.class,()->new Document(id,1,"Pact","Terms",Visibility.PUBLIC,parties(),Purpose.PEACE,null));assertThrows(IllegalArgumentException.class,()->new ConflictLink(conflict,0));
    }
}
