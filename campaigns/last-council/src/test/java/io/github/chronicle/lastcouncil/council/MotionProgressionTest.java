package io.github.chronicle.lastcouncil.council;

import com.google.gson.JsonParser;
import io.github.chronicle.core.ballots.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.github.chronicle.lastcouncil.council.ProceedingsServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;

class MotionProgressionTest {
    private static final Map<String,String> CANDIDATES=Map.of("c:a","A","c:b","B");
    @Test void everyOpenMotionNeedsVoteOrMeetingDeferral() throws Exception {
        var fixture=fixture();var s=fixture.service();
        assertEquals(List.of(fixture.motion()),s.pendingMotions());
        assertThrows(IllegalStateException.class,()->s.openRegency(HOST,REGION,ELECTORS,CANDIDATES));
        s.defer(fixture.motion(),"Discuss next time");assertTrue(s.pendingMotions().isEmpty());
        long revision=s.state().revision();s.defer(fixture.motion(),"Retry");assertEquals(revision,s.state().revision());
        assertThrows(IllegalStateException.class,()->s.admit(fixture.motion()));
        var first=s.openRegency(HOST,REGION,ELECTORS,CANDIDATES);s.voidBallot(first.definition().id(),HOST,"Technical correction");
        assertTrue(s.pendingMotions().isEmpty());
        var replacement=s.openRegency(HOST,REGION,ELECTORS,CANDIDATES);s.voidBallot(replacement.definition().id(),HOST,"Not ready");
        s.closeMeeting();s.openMeeting();assertEquals(List.of(fixture.motion()),s.pendingMotions());
        assertThrows(IllegalStateException.class,()->s.openRegency(HOST,REGION,ELECTORS,CANDIDATES));
        s.admit(fixture.motion());var policy=s.open(fixture.motion(),HOST,REGION,ELECTORS);s.close(policy.definition().id(),HOST);
        assertTrue(s.pendingMotions().isEmpty());assertDoesNotThrow(()->s.openRegency(HOST,REGION,ELECTORS,CANDIDATES));
    }
    @Test void voidedPolicyIsUnresolvedAndNewProposalImmediatelyBlocksRegency() throws Exception {
        var fixture=fixture();var s=fixture.service();var policy=s.open(fixture.motion(),HOST,REGION,ELECTORS);
        s.voidBallot(policy.definition().id(),HOST,"Wrong wording");assertFalse(s.pendingMotions().isEmpty());
        s.defer(fixture.motion(),"Later");UUID id=UUID.randomUUID();s.propose(motion(id,A,"c:a",false));
        assertEquals(List.of(id),s.pendingMotions());assertThrows(IllegalStateException.class,()->s.openRegency(HOST,REGION,ELECTORS,CANDIDATES));
        s.withdraw(id,"c:a");assertTrue(s.pendingMotions().isEmpty());
    }
    @Test void deferralSurvivesActualStoreReloadAndLegacyNotesAreNotGuessed(@TempDir Path root) throws Exception {
        Path journal=root.resolve("proceedings.json"),ledger=root.resolve("ballots.json");
        var s=new ProceedingsService(new ProceedingsStore(journal),new BallotService(new BallotStore(ledger),Clock.systemUTC()),Clock.systemUTC());
        s.region(REGION.id());s.openMeeting();UUID id=UUID.randomUUID();s.propose(motion(id,HOST,"",true));s.defer(id,"Until later");
        var reloaded=new ProceedingsService(new ProceedingsStore(journal),new BallotService(new BallotStore(ledger),Clock.systemUTC()),Clock.systemUTC());
        assertTrue(reloaded.pendingMotions().isEmpty());assertEquals(1,reloaded.state().deferredAt().get(id));
        var tree=JsonParser.parseString(Files.readString(journal)).getAsJsonObject();tree.addProperty("schemaVersion",2);tree.getAsJsonObject("state").remove("deferredAt");Files.writeString(journal,tree.toString());
        var legacy=new ProceedingsService(new ProceedingsStore(journal),new BallotService(new BallotStore(ledger),Clock.systemUTC()),Clock.systemUTC());
        assertEquals(List.of(id),legacy.pendingMotions());assertEquals(reloaded.state().motions().get(id).notes(),legacy.state().motions().get(id).notes());
    }
}
