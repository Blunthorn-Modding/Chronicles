package io.github.chronicle.lastcouncil.council;

import com.google.gson.*;
import io.github.chronicle.core.ballots.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.github.chronicle.lastcouncil.council.ProceedingsState.*;
import static io.github.chronicle.lastcouncil.council.ProceedingsServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ForeignMigrationTest {
    @TempDir Path directory;
    @Test void allLegacySchemasPreservePendingOpeningAndElectorate()throws Exception {
        for(int version:List.of(1,2,3)){
            var f=fixture();f.ledger().fail=true;assertThrows(IOException.class,()->f.service().open(f.motion(),HOST,REGION,ELECTORS));
            var original=f.service().state();Path path=directory.resolve("v"+version+".json");var store=new ProceedingsStore(path);store.load();store.save(original);
            var root=JsonParser.parseString(Files.readString(path)).getAsJsonObject();root.addProperty("schemaVersion",version);var state=root.getAsJsonObject("state");
            state.getAsJsonObject("motions").asMap().values().forEach(m->m.getAsJsonObject().remove("foreign"));
            if(version<3)state.remove("deferredAt");
            if(version==1){state.remove("succession");state.getAsJsonObject("pending").remove("result");}
            Files.writeString(path,root.toString());var loaded=new ProceedingsStore(path);assertEquals(original,loaded.load());
            f.ledger().fail=false;var recovered=new ProceedingsService(loaded,new BallotService(f.ledger(),Clock.systemUTC()),Clock.systemUTC());recovered.recover();
            assertEquals(original.activeBallot(),recovered.state().activeBallot());assertEquals(ELECTORS,recovered.state().attempts().get(original.activeBallot()).definition().electorate());
            assertNull(recovered.state().motions().get(f.motion()).foreign());assertEquals(4,JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("schemaVersion").getAsInt());
        }
    }
    @Test void legacySchemaCannotSmuggleANewForeignSubject()throws Exception {
        Path path=directory.resolve("legacy.json");var f=fixture();var store=new ProceedingsStore(path);store.load();store.save(f.service().state());
        var root=JsonParser.parseString(Files.readString(path)).getAsJsonObject();root.addProperty("schemaVersion",3);
        // Even null is a schema-4 field and must not be silently accepted as legacy.
        String retained=root.toString();Files.writeString(path,retained);assertThrows(IOException.class,()->new ProceedingsStore(path).load());assertEquals(retained,Files.readString(path));
    }
    @Test void foreignMetadataSurvivesCopiesAndStrictCurrentRoundTrip()throws Exception {
        var subject=new ForeignSubject(ForeignKind.DECLARE_WAR,"last_council:north",0);
        var motion=new Motion(UUID.randomUUID(),"Northern border","A public proposal",HOST,"",BallotVisibility.PUBLIC,Threshold.TWO_THIRDS,true,Set.of(),MotionStatus.SUBMITTED,List.of(),List.of(),subject);
        var copied=motion.with(MotionStatus.AGENDA,Set.of(),List.of(),List.of("For discussion"));assertEquals(subject,copied.foreign());
        ProceedingsState state=new ProceedingsState(1,0,Phase.BETWEEN,"",Set.of(),Map.of(copied.id(),copied),Map.of(),null,null,Map.of());
        Path path=directory.resolve("foreign.json");var store=new ProceedingsStore(path);store.load();store.save(state);assertEquals(state,new ProceedingsStore(path).load());
        String valid=Files.readString(path);String malformed=valid.replace("\"threshold\":\"TWO_THIRDS\"","\"threshold\":\"MAJORITY\"");assertNotEquals(valid,malformed);Files.writeString(path,malformed);assertThrows(IOException.class,()->new ProceedingsStore(path).load());
    }
}
