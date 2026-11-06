package io.github.chronicle.lastcouncil.council;

import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.ballots.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.github.chronicle.lastcouncil.council.ProceedingsState.*;
import static org.junit.jupiter.api.Assertions.*;

class ProceedingsServiceTest {
    static final UUID HOST=new UUID(0,1), A=new UUID(0,2), B=new UUID(0,3);
    static final AuthoringSite.Region REGION=new AuthoringSite.Region("council","minecraft:overworld",1,new BlockPosition(-5,64,-5),new BlockPosition(5,80,5));
    static final Map<String,BallotElector> ELECTORS=Map.of("c:a",new BallotElector(A,"A"),"c:b",new BallotElector(B,"B"));
    static final class Repo implements ProceedingsRepository {
        ProceedingsState saved=ProceedingsState.empty();int saves,failAt=-1;
        public ProceedingsState load(){return saved;}
        public void save(ProceedingsState next)throws IOException{if(++saves==failAt)throw new IOException("write refused");saved=next;}
    }
    static final class Ballots implements BallotRepository {
        BallotState saved=BallotState.empty();boolean fail;
        public BallotState load(){return saved;}
        public void save(BallotState next)throws IOException{if(fail)throw new IOException("write refused");saved=next;}
    }
    record Fixture(Repo repo,Ballots ledger,BallotService ballots,ProceedingsService service,UUID motion){ }
    static Fixture fixture()throws Exception{
        Repo repo=new Repo();Ballots ledger=new Ballots();BallotService ballots=new BallotService(ledger,Clock.systemUTC());
        ProceedingsService service=new ProceedingsService(repo,ballots,Clock.systemUTC());service.region(REGION.id());service.openMeeting();
        UUID id=UUID.randomUUID();service.propose(motion(id,HOST,"",true));return new Fixture(repo,ledger,ballots,service,id);
    }
    static Motion motion(UUID id,UUID actor,String house,boolean host){return new Motion(id,"A public question","Public motion body",actor,house,BallotVisibility.SECRET,Threshold.MAJORITY,host,Set.of(),MotionStatus.SUBMITTED,List.of(),List.of());}
    @Test void sponsorshipAndMeetingPhasesDoNotFabricateBallots()throws Exception{
        var f=fixture();var s=f.service();UUID proposal=UUID.randomUUID();s.propose(motion(proposal,A,"c:a",false));
        assertThrows(IllegalStateException.class,()->s.admit(proposal));
        assertThrows(IllegalStateException.class,()->s.sponsor(proposal,"c:a"));
        s.sponsor(proposal,"c:b");s.sponsor(proposal,"c:b");s.admit(proposal);
        s.defer(proposal,"More discussion needed");assertEquals(1,s.state().motions().get(proposal).notes().size());
        assertEquals(MotionStatus.SUBMITTED,s.state().motions().get(proposal).status());
        s.recess();assertNull(s.state().activeBallot());s.resumeMeeting();s.withdraw(proposal,"c:a");
        s.closeMeeting();assertEquals(Phase.BETWEEN,s.state().phase());
        UUID call=UUID.randomUUID();assertTrue(s.call(call,HOST));assertFalse(s.call(call,HOST));assertEquals(1,s.state().calls().size());
        s.openMeeting();assertEquals(2,s.state().meeting());assertTrue(f.ballots().state().ballots().isEmpty());
    }
    @Test void failedOpeningIntentDoesNotOpenCoreBallot()throws Exception{
        var f=fixture();f.repo().failAt=f.repo().saves+1;
        assertThrows(IOException.class,()->f.service().open(f.motion(),HOST,REGION,ELECTORS));
        assertNull(f.service().state().pending());assertTrue(f.ballots().state().ballots().isEmpty());
    }
    @Test void failedCoreOpenRecoversSameElectorateAndIdentity()throws Exception{
        var f=fixture();f.ledger().fail=true;
        assertThrows(IOException.class,()->f.service().open(f.motion(),HOST,REGION,ELECTORS));
        UUID original=f.service().state().activeBallot();assertNotNull(f.service().state().pending());
        assertThrows(IllegalStateException.class,()->f.service().closeMeeting());
        f.ledger().fail=false;var restarted=new ProceedingsService(f.repo(),new BallotService(f.ledger(),Clock.systemUTC()),Clock.systemUTC());
        restarted.recover();assertEquals(original,restarted.state().activeBallot());assertNull(restarted.state().pending());
        assertEquals(ELECTORS,restarted.state().attempts().get(original).definition().electorate());
        assertThrows(IllegalArgumentException.class,()->restarted.cast(original,HOST,"yes"));
    }
    @Test void coreOpenSurvivesModuleFinalizeFailure()throws Exception{
        var f=fixture();f.repo().failAt=f.repo().saves+2;
        assertThrows(IOException.class,()->f.service().open(f.motion(),HOST,REGION,ELECTORS));
        UUID original=f.service().state().activeBallot();assertEquals(1,f.ledger().saved.ballots().size());
        var restarted=new ProceedingsService(f.repo(),new BallotService(f.ledger(),Clock.systemUTC()),Clock.systemUTC());
        restarted.recover();assertEquals(original,restarted.state().activeBallot());
        assertEquals(1,f.ledger().saved.ballots().size());
    }
    @Test void closeRecoveryRetainsFirstConfirmedVoteAndStableCoreReceipt()throws Exception{
        var f=fixture();var s=f.service();Ballot b=s.open(f.motion(),HOST,REGION,ELECTORS);
        s.cast(b.definition().id(),A,"yes");assertThrows(IllegalStateException.class,()->s.cast(b.definition().id(),A,"no"));
        f.repo().failAt=f.repo().saves+2;
        assertThrows(IOException.class,()->s.close(b.definition().id(),HOST));
        Ballot terminal=f.ledger().saved.ballots().get(b.definition().id());assertEquals(BallotStatus.CLOSED,terminal.status());
        var restarted=new ProceedingsService(f.repo(),new BallotService(f.ledger(),Clock.systemUTC()),Clock.systemUTC());restarted.recover();
        assertEquals(terminal,f.ledger().saved.ballots().get(b.definition().id()));assertNull(restarted.state().activeBallot());
        assertEquals(MotionStatus.RECORDED,restarted.state().motions().get(f.motion()).status());
        assertTrue(restarted.view(b.definition().id(),HOST).passedOptions().isEmpty());assertEquals(2,restarted.view(b.definition().id(),HOST).threshold());
    }
    @Test void voidReplacementKeepsOriginalVotesReasonAndUsesNewSnapshot()throws Exception{
        var f=fixture();var s=f.service();Ballot original=s.open(f.motion(),HOST,REGION,ELECTORS);s.cast(original.definition().id(),A,"yes");
        assertThrows(IllegalArgumentException.class,()->s.voidBallot(original.definition().id(),HOST," "));
        f.repo().failAt=f.repo().saves+2;assertThrows(IOException.class,()->s.voidBallot(original.definition().id(),HOST,"Wrong electorate"));
        var restarted=new ProceedingsService(f.repo(),new BallotService(f.ledger(),Clock.systemUTC()),Clock.systemUTC());restarted.recover();
        var voided=f.ledger().saved.ballots().get(original.definition().id());assertEquals(Map.of("c:a","yes"),voided.votes());assertEquals("Wrong electorate",voided.reason());
        assertTrue(restarted.view(original.definition().id(),HOST).totals().isEmpty());
        Ballot replacement=restarted.open(f.motion(),HOST,REGION,Map.of("c:b",ELECTORS.get("c:b")));
        assertNotEquals(original.definition().id(),replacement.definition().id());assertEquals(1,replacement.definition().threshold());
        assertEquals(2,restarted.state().motions().get(f.motion()).ballots().size());
        assertThrows(IllegalStateException.class,()->restarted.region("other"));
    }
    @Test void realJournalRestartRetainsPinnedRegionAndCast(@TempDir Path root)throws Exception{
        var ballots=new BallotService(new BallotStore(root.resolve("ballots.json")),Clock.systemUTC());
        var s=new ProceedingsService(new ProceedingsStore(root.resolve("proceedings.json")),ballots,Clock.systemUTC());
        s.region(REGION.id());s.openMeeting();UUID id=UUID.randomUUID();s.propose(motion(id,HOST,"",true));
        Ballot b=s.open(id,HOST,REGION,ELECTORS);s.cast(b.definition().id(),B,"abstain");
        var reopened=new ProceedingsService(new ProceedingsStore(root.resolve("proceedings.json")),new BallotService(new BallotStore(root.resolve("ballots.json")),Clock.systemUTC()),Clock.systemUTC());
        assertEquals(s.state(),reopened.state());assertEquals("abstain",reopened.view(b.definition().id(),B).ownChoice());
        assertEquals(REGION,reopened.state().attempts().get(b.definition().id()).region());
    }
    @Test void meetingCallUsesRecurringMediaWithoutResettingOtherEvents() {
        var template=io.github.chronicle.lastcouncil.content.CouncilDefaults.meetingCall();
        assertEquals(io.github.chronicle.core.play.EventOccurrencePolicy.PER_SOURCE_OCCURRENCE,template.occurrencePolicy());
        assertEquals("last_council:council_called",template.source().type());
        assertTrue(template.regionOutcomes().isEmpty());assertTrue(template.flagOutcomes().isEmpty());
        assertTrue(template.encounterOutcomes().isEmpty());assertEquals(0,template.delayTicks());
    }
    @Test void recoveryCannotClearAnExternalEditWithoutRepair(@TempDir Path root)throws Exception {
        var ballots=new BallotService(new BallotStore(root.resolve("ballots.json")),Clock.systemUTC());
        var service=new ProceedingsService(new ProceedingsStore(root.resolve("proceedings.json")),ballots,Clock.systemUTC());
        service.region(REGION.id());String original=java.nio.file.Files.readString(root.resolve("proceedings.json"));
        java.nio.file.Files.writeString(root.resolve("proceedings.json"),"external");
        assertThrows(IOException.class,service::recover);
        assertEquals("external",java.nio.file.Files.readString(root.resolve("proceedings.json")));
        java.nio.file.Files.writeString(root.resolve("proceedings.json"),original);assertDoesNotThrow(service::recover);
    }
    @Test void preparedCallRequiresTheLoadedProducerRecord() {
        UUID id=UUID.randomUUID();String authority="council-call:"+id;
        var receipt=new io.github.chronicle.core.progression.SourceLedger.Receipt("source:test","last_council:council_called","council","",HOST,true,Map.of(),authority,io.github.chronicle.core.progression.SourceLedger.Stage.PREPARED);
        assertFalse(CouncilCallEvidence.accepted(receipt,Map.of(),"source:test"));
        var calls=Map.of(id,new Call(id,HOST,"2026-01-01T00:00:00Z",1));
        assertTrue(CouncilCallEvidence.accepted(receipt,calls,"source:test"));
        assertThrows(IllegalStateException.class,()->CouncilCallEvidence.accepted(receipt,calls,"source:other"));
        assertThrows(IllegalStateException.class,()->CouncilCallEvidence.accepted(receipt,Map.of(id,new Call(id,A,"2026-01-01T00:00:00Z",1)),"source:test"));
    }
}
