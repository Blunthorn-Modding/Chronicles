package io.github.chronicle.core.departures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class DepartureStoreTest {
    @TempDir Path temporary;
    private int nextCase;

    @Test void defaultsMaterializeOnlyOnceAndDeletedDefaultsNeverResurrect()throws Exception{
        Path file=temporary.resolve("departures.json");DepartureDefinition supplied=definition();
        try(DepartureStore store=new DepartureStore(file,List.of(supplied))){DepartureService service=new DepartureService(store);assertEquals(supplied,service.state().definitions().get(supplied.id()));service.retire(supplied.id(),1,0);}
        try(DepartureStore store=new DepartureStore(file,List.of(supplied))){DepartureState reloaded=store.load();assertFalse(reloaded.definitions().containsKey(supplied.id()));assertTrue(reloaded.tombstones().contains(supplied.id()));}
    }

    @Test void roundTripWritesExactBackupBeforeReplacement()throws Exception{
        Path file=temporary.resolve("departures.json");String original;
        try(DepartureStore store=new DepartureStore(file,List.of(definition()))){DepartureState state=store.load();original=Files.readString(file);DepartureService service=new DepartureService(new AlreadyLoaded(store,state));service.replace(definition().withRevision(2),1,0);assertEquals(original,Files.readString(file.resolveSibling("departures.json.bak")));}
        try(DepartureStore store=new DepartureStore(file)){DepartureState state=store.load();assertEquals(1,state.revision());assertEquals(2,state.definitions().get("test:departure").revision());}
    }

    @Test void malformedTruncatedUnknownAndDuplicateEvidenceAreRejectedAndPreserved()throws Exception{
        assertInvalidPreserved("{\"schemaVersion\":1");
        String valid=DepartureStore.encode(new DepartureState(0,java.util.Map.of("test:departure",definition()),java.util.Set.of(),java.util.Map.of(),java.util.Map.of(),java.util.Map.of(),Optional.empty()));
        assertInvalidPreserved(valid.replaceFirst("\\{", "{\\\"unknown\\\":1,"));
        assertInvalidPreserved(valid.replace("\"revision\":0", "\"revision\":0,\"revision\":0"));
    }

    @Test void externalEditAfterLoadIsNeverOverwritten()throws Exception{
        Path file=temporary.resolve("departures.json");try(DepartureStore store=new DepartureStore(file,List.of(definition()))){DepartureService service=new DepartureService(store);String changed=Files.readString(file)+" ";Files.writeString(file,changed,StandardCharsets.UTF_8);assertThrows(IOException.class,()->service.replace(definition().withRevision(2),1,0));assertEquals(changed,Files.readString(file));assertEquals(1,service.state().definitions().get("test:departure").revision());}
    }

    @Test void missingInitializedAuthorityFailsInsteadOfRecreatingDefaults()throws Exception{
        Path file=temporary.resolve("departures.json");try(DepartureStore store=new DepartureStore(file,List.of(definition()))){store.load();}Files.delete(file);
        try(DepartureStore store=new DepartureStore(file,List.of(definition()))){assertThrows(IOException.class,store::load);assertFalse(Files.exists(file));}
    }

    @Test void decodeRejectsOversizedCollectionsBeforeBuildingState(){
        StringBuilder values=new StringBuilder();for(int i=0;i<=DepartureState.MAX_HISTORY;i++){if(i>0)values.append(',');values.append("\"test:t").append(i).append("\"");}
        String source="{\"schemaVersion\":1,\"revision\":0,\"definitions\":[],\"tombstones\":["+values+"],\"used\":[],\"operations\":[],\"departed\":[],\"concluded\":null}";
        assertThrows(IllegalArgumentException.class,()->DepartureStore.decode(source));
    }

    private void assertInvalidPreserved(String content)throws Exception{
        Path directory=Files.createDirectory(temporary.resolve("case-"+(nextCase++)));Path file=directory.resolve("departures.json");Files.writeString(file.resolveSibling("departures.json.initialized"),"chronicle-departures-schema-1\n");Files.writeString(file,content);byte[] before=Files.readAllBytes(file);
        try(DepartureStore store=new DepartureStore(file)){assertThrows(IOException.class,store::load);}assertArrayEquals(before,Files.readAllBytes(file));
    }

    private static DepartureDefinition definition(){return new DepartureDefinition("test:departure","Departure",1,Optional.of(new SiteRef("test:anchor",2)),Optional.of(new SiteRef("test:region",3)),"First","Final",Optional.empty(),true);}
    private record AlreadyLoaded(DepartureStore store,DepartureState loaded) implements DepartureRepository{public DepartureState load(){return loaded;}public void save(DepartureState replacement)throws IOException{store.save(replacement);}}
}
