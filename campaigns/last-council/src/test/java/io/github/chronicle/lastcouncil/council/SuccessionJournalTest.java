package io.github.chronicle.lastcouncil.council;

import com.google.gson.JsonParser;
import io.github.chronicle.core.ballots.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.github.chronicle.lastcouncil.council.ProceedingsServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;

class SuccessionJournalTest {
    private static final String HA="c:a", HB="c:b", HC="c:c", HD="c:d";
    private static final Map<String,String> THREE=Map.of(HA,"A",HB,"B",HC,"C");
    private static final ProceedingsService.ProgressionGate ALLOW=new ProceedingsService.ProgressionGate() {
        public void beforeRegency(int meeting) { }
        public void beforeMeetingClose(int meeting) { }
    };
    private static final IntUnaryOperator NO_DRAW=n->{throw new AssertionError("draw must not run");};
    private record Fixture(Repo repo, Ballots ledger, ProceedingsService service) { }
    private static Fixture fixture(IntUnaryOperator draw, ProceedingsService.ProgressionGate gate) throws Exception {
        Repo repo=new Repo(); Ballots ledger=new Ballots();
        ProceedingsService s=service(repo,ledger,draw,gate); s.region(REGION.id()); s.openMeeting();
        return new Fixture(repo,ledger,s);
    }
    private static ProceedingsService service(Repo repo,Ballots ledger,IntUnaryOperator draw,ProceedingsService.ProgressionGate gate) throws Exception {
        return new ProceedingsService(repo,new BallotService(ledger,Clock.systemUTC()),Clock.systemUTC(),draw,gate);
    }
    private static Set<String> options(Ballot ballot) {
        Set<String> ids=new HashSet<>(); ballot.definition().options().forEach(o->ids.add(o.id())); return ids;
    }
    @Test void openingVoidReplacementAndLaterMeetingKeepLockedSlate() throws Exception {
        var f=fixture(n->0,ALLOW);var s=f.service();
        assertThrows(IllegalStateException.class,()->s.openRegency(HOST,REGION,ELECTORS,Map.of(HA,"A")));
        Ballot first=s.openRegency(HOST,REGION,ELECTORS,THREE);
        assertEquals(BallotVisibility.SECRET,first.definition().visibility());
        assertEquals(Set.of(HA,HB,HC,"abstain"),options(first));assertEquals(2,first.definition().threshold());
        s.voidBallot(first.definition().id(),HOST,"Wrong electorate");
        assertThrows(IllegalStateException.class,()->s.withdrawCandidate(HA,HOST,"Cannot change replacement slate"));
        Ballot second=s.openRegency(HOST,REGION,Map.of(HA,ELECTORS.get(HA)),Map.of(HA,"A",HB,"B",HC,"C",HD,"D"));
        assertNotEquals(first.definition().id(),second.definition().id());assertEquals(1,second.definition().threshold());
        assertEquals(Set.of(HA,HB,HC,"abstain"),options(second));
        s.close(second.definition().id(),HOST);
        assertEquals(Set.of(HB,HC),s.state().succession().remaining());
        UUID motion=s.state().attempts().get(second.definition().id()).motion();
        assertThrows(IllegalStateException.class,()->s.openRegency(HOST,REGION,ELECTORS,THREE));
        assertThrows(IllegalStateException.class,()->s.open(motion,HOST,REGION,ELECTORS));
        s.closeMeeting();s.openMeeting();
        Ballot next=s.openRegency(HOST,REGION,ELECTORS,THREE);
        assertEquals(Set.of(HB,HC,"abstain"),options(next));
    }
    @Test void closeRecoveryUsesRecordedDrawBeforeCoreClose() throws Exception {
        var f=fixture(n->{assertEquals(3,n);return 1;},ALLOW);var s=f.service();
        Ballot b=s.openRegency(HOST,REGION,ELECTORS,THREE);f.ledger().fail=true;
        assertThrows(IOException.class,()->s.close(b.definition().id(),HOST));
        var result=s.state().pending().result();
        assertEquals(new SuccessionRules.Result(SuccessionRules.Decision.ELIMINATED,HB,List.of(HA,HB,HC)),result);
        assertEquals(BallotStatus.OPEN,f.ledger().saved.ballots().get(b.definition().id()).status());
        f.ledger().fail=false;var restarted=service(f.repo(),f.ledger(),NO_DRAW,ALLOW);restarted.recover();
        assertNull(restarted.state().pending());assertEquals(result,restarted.state().succession().rounds().get(b.definition().id()).result());
        assertEquals(Set.of(HA,HC),restarted.state().succession().remaining());
        restarted.recover();assertEquals(Set.of(HA,HC),restarted.state().succession().remaining());
    }
    @Test void closeRecoveryAfterCoreCommitRetainsResult() throws Exception {
        var f=fixture(n->2,ALLOW);var s=f.service();Ballot b=s.openRegency(HOST,REGION,ELECTORS,THREE);
        f.repo().failAt=f.repo().saves+2;
        assertThrows(IOException.class,()->s.close(b.definition().id(),HOST));
        var result=s.state().pending().result();assertEquals(HC,result.house());
        assertEquals(BallotStatus.CLOSED,f.ledger().saved.ballots().get(b.definition().id()).status());
        var restarted=service(f.repo(),f.ledger(),NO_DRAW,ALLOW);restarted.recover();
        assertEquals(result,restarted.state().succession().rounds().get(b.definition().id()).result());
        assertEquals(Set.of(HA,HB),restarted.state().succession().remaining());
    }
    @Test void withdrawalConfirmationAndUnresolvedHaveNoInventedWinner() throws Exception {
        var f=fixture(n->0,ALLOW);var s=f.service();Ballot b=s.openRegency(HOST,REGION,ELECTORS,THREE);
        s.close(b.definition().id(),HOST);s.withdrawCandidate(HB,HOST,"Withdrawn");s.closeMeeting();s.openMeeting();
        Ballot confirmation=s.openRegency(HOST,REGION,ELECTORS,THREE);
        assertEquals(Set.of("yes","no","abstain"),options(confirmation));
        s.cast(confirmation.definition().id(),A,"yes");s.cast(confirmation.definition().id(),B,"yes");s.close(confirmation.definition().id(),HOST);
        assertEquals(SuccessionState.Stage.CROWNED,s.state().succession().stage());assertEquals(HC,s.state().succession().crowned());
        var other=fixture(n->0,ALLOW);Ballot open=other.service().openRegency(HOST,REGION,ELECTORS,THREE);
        other.service().close(open.definition().id(),HOST);other.service().endSuccessionUnresolved();
        assertEquals(SuccessionState.Stage.UNRESOLVED,other.service().state().succession().stage());assertEquals("",other.service().state().succession().crowned());
    }
    @Test void gateDenialWritesNothingAndRecoveryDoesNotRepeatGate() throws Exception {
        class Gate implements ProceedingsService.ProgressionGate {
            boolean deny;int regency,meeting;
            public void beforeRegency(int m) { regency++;if(deny)throw new IllegalStateException("due"); }
            public void beforeMeetingClose(int m) { meeting++;if(deny)throw new IllegalStateException("due"); }
        }
        Gate gate=new Gate();var f=fixture(n->0,gate);var s=f.service();gate.deny=true;
        int saves=f.repo().saves;var before=s.state();
        assertThrows(IllegalStateException.class,()->s.openRegency(HOST,REGION,ELECTORS,THREE));
        assertThrows(IllegalStateException.class,s::closeMeeting);assertEquals(saves,f.repo().saves);assertEquals(before,s.state());
        gate.deny=false;f.ledger().fail=true;assertThrows(IOException.class,()->s.openRegency(HOST,REGION,ELECTORS,THREE));
        int calls=gate.regency;gate.deny=true;f.ledger().fail=false;
        var restarted=service(f.repo(),f.ledger(),NO_DRAW,gate);restarted.recover();assertEquals(calls,gate.regency);
    }
    @Test void legacyMigrationAndCurrentRestartPreserveHistory(@TempDir Path root) throws Exception {
        Path journal=root.resolve("proceedings.json"),ledger=root.resolve("ballots.json");
        var ballotService=new BallotService(new BallotStore(ledger),Clock.systemUTC());
        var s=new ProceedingsService(new ProceedingsStore(journal),ballotService,Clock.systemUTC());
        s.region(REGION.id());s.openMeeting();UUID id=UUID.randomUUID();s.propose(motion(id,HOST,"",true));
        Ballot b=s.open(id,HOST,REGION,ELECTORS);s.cast(b.definition().id(),A,"yes");
        // Produce a genuine schema-1-shaped pending operation with existing history.
        var tree=JsonParser.parseString(Files.readString(journal)).getAsJsonObject();
        tree.addProperty("schemaVersion",1);var state=tree.getAsJsonObject("state");state.remove("succession");state.remove("deferredAt");
        var pending=new com.google.gson.JsonObject();pending.addProperty("operation","CLOSE");pending.addProperty("ballot",b.definition().id().toString());pending.addProperty("actor",HOST.toString());pending.addProperty("reason","");state.add("pending",pending);
        Files.writeString(journal,tree.toString());
        var migrated=new ProceedingsService(new ProceedingsStore(journal),new BallotService(new BallotStore(ledger),Clock.systemUTC()),Clock.systemUTC());
        assertEquals(SuccessionState.empty(),migrated.state().succession());assertEquals(1,migrated.state().motions().size());
        assertNull(migrated.state().pending().result());migrated.recover();
        assertEquals(3,JsonParser.parseString(Files.readString(journal)).getAsJsonObject().get("schemaVersion").getAsInt());
        var reloaded=new ProceedingsService(new ProceedingsStore(journal),new BallotService(new BallotStore(ledger),Clock.systemUTC()),Clock.systemUTC());
        assertEquals(migrated.state(),reloaded.state());assertEquals(1,reloaded.state().attempts().size());
        assertEquals("yes",reloaded.view(b.definition().id(),A).ownChoice());
    }
    @Test void currentJournalRoundTripAndTamperedPendingResultFailsClosed(@TempDir Path root) throws Exception {
        Path journal=root.resolve("proceedings.json"),ledger=root.resolve("ballots.json");
        var repo=new ProceedingsStore(journal);var core=new BallotStore(ledger);
        var s=new ProceedingsService(repo,new BallotService(core,Clock.systemUTC()),Clock.systemUTC(),n->0,ALLOW);
        s.region(REGION.id());s.openMeeting();Ballot b=s.openRegency(HOST,REGION,ELECTORS,THREE);
        var reloaded=new ProceedingsService(new ProceedingsStore(journal),new BallotService(new BallotStore(ledger),Clock.systemUTC()),Clock.systemUTC(),NO_DRAW,ALLOW);
        assertEquals(s.state(),reloaded.state());
        var tree=JsonParser.parseString(Files.readString(journal)).getAsJsonObject();
        var pending=new com.google.gson.JsonObject();pending.addProperty("operation","CLOSE");pending.addProperty("ballot",b.definition().id().toString());pending.addProperty("actor",HOST.toString());pending.addProperty("reason","");
        var result=new com.google.gson.JsonObject();result.addProperty("decision","CROWNED");result.addProperty("house",HA);result.add("tiedLast",new com.google.gson.JsonArray());pending.add("result",result);tree.getAsJsonObject("state").add("pending",pending);
        Files.writeString(journal,tree.toString());String tampered=Files.readString(journal);
        var corrupt=new ProceedingsService(new ProceedingsStore(journal),new BallotService(new BallotStore(ledger),Clock.systemUTC()),Clock.systemUTC(),NO_DRAW,ALLOW);
        assertThrows(IllegalStateException.class,corrupt::recover);assertEquals(tampered,Files.readString(journal));
        assertEquals(BallotStatus.OPEN,new BallotStore(ledger).load().ballots().get(b.definition().id()).status());
    }
    @Test void enclosingMeetingCannotPrecedeSuccessionHistory() throws Exception {
        var f=fixture(n->0,ALLOW);var s=f.service();
        var first=s.openRegency(HOST,REGION,ELECTORS,THREE);s.voidBallot(first.definition().id(),HOST,"Technical mistake");
        s.closeMeeting();s.openMeeting();s.withdrawCandidate(HA,HOST,"Withdrawn at the next meeting");
        var state=s.state();
        assertThrows(IllegalArgumentException.class,()->new ProceedingsState(state.revision(),1,state.phase(),state.votingRegion(),state.lecterns(),state.motions(),state.attempts(),state.activeBallot(),state.pending(),state.calls(),state.succession()));
    }

}
