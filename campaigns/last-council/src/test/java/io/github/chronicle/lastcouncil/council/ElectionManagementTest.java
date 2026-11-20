package io.github.chronicle.lastcouncil.council;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.chronicle.core.ballots.*;
import io.github.chronicle.lastcouncil.progression.PoliticalOccurrence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.github.chronicle.lastcouncil.council.ProceedingsServiceTest.*;
import static io.github.chronicle.lastcouncil.council.ProceedingsState.Phase.*;
import static org.junit.jupiter.api.Assertions.*;

class ElectionManagementTest {
    private static final String HA="c:a", HB="c:b", HC="c:c";
    private static final Map<String,String> THREE=Map.of(HA,"A",HB,"B",HC,"C");
    private static final ProceedingsService.ProgressionGate ALLOW=new ProceedingsService.ProgressionGate() {
        public void beforeRegency(int meeting) { }
        public void beforeMeetingClose(int meeting) { }
    };
    @TempDir Path directory;

    @Test void withdrawalAndEndRetainExactAuditAndSourcesAcrossRestart() throws Exception {
        var service=started(new ProceedingsStore(journal()));
        UUID withdrawalId=UUID.randomUUID(),endId=UUID.randomUUID();
        List<PoliticalOccurrence> facts=new ArrayList<>();
        String reason="Considering the realm.\nI support another candidate.";
        service.observer(occurrences->{
            for(var fact:occurrences) {
                assertTrue(service.politicalElectionEvidence(fact.authority()).isEmpty());
                assertEquals("last_council:succession_result",fact.type());
                assertEquals("election",fact.target());
            }
            facts.addAll(occurrences);
        });
        service.withdrawCandidate(withdrawalId,HB,B,reason);
        assertEquals(Set.of(HC),service.state().succession().remaining());
        service.closeMeeting();service.openMeeting();
        service.endSuccessionUnresolved(endId,HOST,"Consider this election closed.");
        assertEquals(List.of("withdrawn","unresolved"),facts.stream().map(PoliticalOccurrence::activity).toList());

        var restarted=service(new ProceedingsStore(journal()));
        assertEquals(6,json().get("schemaVersion").getAsInt());
        assertEquals(service.state(),restarted.state());
        assertEquals(new SuccessionState.Withdrawal(2,HB,B,reason,1,withdrawalId,true),restarted.state().succession().withdrawals().getFirst());
        assertEquals(new SuccessionState.End(endId,HOST,"Consider this election closed.",2,false),restarted.state().succession().end());
        assertEquals("",restarted.state().succession().crowned());
        for(var fact:facts)assertEquals(List.of(fact),restarted.politicalElectionEvidence(fact.authority()));

        restarted.observer(ignored->{throw new AssertionError("a retry cannot create another source");});
        var before=restarted.state();
        restarted.withdrawCandidate(withdrawalId,HB,B,reason);
        restarted.endSuccessionUnresolved(endId,HOST,"Consider this election closed.");
        assertEquals(before,restarted.state());
        assertThrows(IllegalStateException.class,()->restarted.withdrawCandidate(withdrawalId,HB,A,reason));
        assertThrows(IllegalStateException.class,()->restarted.endSuccessionUnresolved(withdrawalId,HOST,reason));
        assertThrows(IllegalStateException.class,()->restarted.endSuccessionUnresolved(endId,HOST,"Changed reason"));
        assertThrows(IllegalStateException.class,()->restarted.endSuccessionUnresolved(UUID.randomUUID(),HOST,reason));
        assertThrows(IllegalStateException.class,()->restarted.openRegency(HOST,REGION,ELECTORS,THREE));
        restarted.propose(motion(UUID.randomUUID(),HOST,"",true));
        assertEquals(1,restarted.pendingMotions().size());
    }

    @Test void endAfterVoidIsAllowedWhileWithdrawalKeepsReplacementSlate() throws Exception {
        var service=service(new ProceedingsStore(journal()));
        service.region(REGION.id());service.openMeeting();
        var ballot=service.openRegency(HOST,REGION,ELECTORS,THREE);
        UUID operation=UUID.randomUUID();
        assertThrows(IllegalStateException.class,()->service.withdrawCandidate(operation,HA,A,"Withdraw"));
        assertThrows(IllegalStateException.class,()->service.endSuccessionUnresolved(operation,HOST,"End"));
        service.voidBallot(ballot.definition().id(),HOST,"Wrong electorate");
        assertThrows(IllegalStateException.class,()->service.withdrawCandidate(operation,HA,A,"Withdraw"));
        assertThrows(IllegalArgumentException.class,()->service.endSuccessionUnresolved(operation,HOST," "));
        assertThrows(IllegalArgumentException.class,()->service.endSuccessionUnresolved(operation,HOST,"x".repeat(401)));
        service.endSuccessionUnresolved(operation,HOST,"x".repeat(400));
        assertEquals(SuccessionState.Stage.UNRESOLVED,service.state().succession().stage());
        assertEquals(Set.of(HA,HB,HC),service.state().succession().remaining());
    }

    @Test void lastVoluntaryWithdrawalProducesVacancyAndSystemWithdrawalHasNoSource() throws Exception {
        var service=started(new ProceedingsStore(journal()));
        List<PoliticalOccurrence> occurrences=new ArrayList<>();
        service.observer(occurrences::addAll);
        service.withdrawCandidate(HB,B,"House entered rebellion.");
        assertFalse(service.state().succession().withdrawals().getFirst().voluntary());
        assertTrue(occurrences.isEmpty());
        service.withdrawCandidate(UUID.randomUUID(),HC,new UUID(0,4),"I withdraw.");
        assertEquals(SuccessionState.Stage.VACANT,service.state().succession().stage());
        assertEquals(List.of("withdrawn","vacant"),occurrences.stream().map(PoliticalOccurrence::activity).toList());
        assertEquals(occurrences,service.politicalElectionEvidence(occurrences.getFirst().authority()));
        var restarted=service(new ProceedingsStore(journal()));
        assertEquals(occurrences,restarted.politicalElectionEvidence(occurrences.getFirst().authority()));
        assertThrows(IllegalStateException.class,()->service.endSuccessionUnresolved(UUID.randomUUID(),HOST,"End"));
    }

    @Test void observerRefusalAndUncertainSaveLeaveOnlyVerifiedEvidence() throws Exception {
        class UncertainSave implements ProceedingsRepository {
            private final ProceedingsStore actual=new ProceedingsStore(journal());
            private boolean fail,uncertain;
            public ProceedingsState load() throws IOException { return actual.load(); }
            public void save(ProceedingsState state) throws IOException {
                actual.save(state);
                if(fail) { uncertain=true;throw new IOException("Post-publication failure"); }
            }
            public void verifyUnchanged() throws IOException {
                if(uncertain)throw new IOException("Reload proceedings");
                actual.verifyUnchanged();
            }
        }
        var repo=new UncertainSave();var service=started(repo);
        UUID id=UUID.randomUUID();String reason="End without a ruler.";
        List<PoliticalOccurrence> attempted=new ArrayList<>();
        service.observer(facts->{attempted.addAll(facts);throw new IOException("Preparation refused");});
        assertThrows(IOException.class,()->service.endSuccessionUnresolved(id,HOST,reason));
        String authority=attempted.getFirst().authority();
        assertTrue(service.politicalElectionEvidence(authority).isEmpty());
        assertEquals(SuccessionState.Stage.CONTESTED,new ProceedingsStore(journal()).load().succession().stage());

        repo.fail=true;service.observer(facts->assertEquals(attempted,facts));
        assertThrows(IOException.class,()->service.endSuccessionUnresolved(id,HOST,reason));
        assertThrows(IOException.class,()->service.politicalElectionEvidence(authority));
        var restarted=service(new ProceedingsStore(journal()));
        assertEquals(attempted,restarted.politicalElectionEvidence(authority));
        assertThrows(IOException.class,()->restarted.politicalElectionEvidence("council-election:"+id));

        var changed=json();election(changed).getAsJsonObject("end").addProperty("reason","Different valid reason");
        Files.writeString(journal(),changed.toString());
        var replacement=service(new ProceedingsStore(journal()));
        assertThrows(IOException.class,()->replacement.politicalElectionEvidence(authority));
    }

    @Test void legacyElectionMigratesWithoutInventingEndDetailsAndRejectsNewFields() throws Exception {
        var service=started(new ProceedingsStore(journal()));
        service.withdrawCandidate(HB,B,"House entered rebellion.");
        service.endSuccessionUnresolved();
        var original=service.state();var current=json();
        for(int schema=2;schema<=5;schema++) {
            var legacy=current.deepCopy();legacy.addProperty("schemaVersion",schema);
            var election=election(legacy);var withdrawal=withdrawal(legacy);
            election.remove("end");withdrawal.remove("operation");withdrawal.remove("voluntary");
            var state=legacy.getAsJsonObject("state");
            if(schema<3)state.remove("deferredAt");
            for(var motion:state.getAsJsonObject("motions").asMap().values()) {
                if(schema<4)motion.getAsJsonObject().remove("foreign");
                if(schema<5)motion.getAsJsonObject().remove("reinstatement");
            }
            Files.writeString(journal(),legacy.toString());
            var store=new ProceedingsStore(journal());var migrated=store.load();
            assertEquals(original,migrated);
            assertEquals(new SuccessionState.End(null,null,null,0,true),migrated.succession().end());
            store.save(migrated);assertEquals(6,json().get("schemaVersion").getAsInt());
            assertEquals(migrated,new ProceedingsStore(journal()).load());

            assertRejected(legacy,root->election(root).add("end",JsonNull.INSTANCE));
            assertRejected(legacy,root->withdrawal(root).addProperty("operation",UUID.randomUUID().toString()));
            assertRejected(legacy,root->withdrawal(root).addProperty("order","2"));
        }
    }

    @Test void currentJournalRequiresCompleteAuditAndConsistentChronology() throws Exception {
        var service=started(new ProceedingsStore(journal()));
        UUID withdrawal=UUID.randomUUID();
        service.withdrawCandidate(withdrawal,HB,B,"I withdraw.");
        service.closeMeeting();service.openMeeting();
        service.endSuccessionUnresolved(UUID.randomUUID(),HOST,"End without a ruler.");
        var valid=json();
        assertRejected(valid,root->election(root).remove("end"));
        assertRejected(valid,root->election(root).add("end",JsonNull.INSTANCE));
        assertRejected(valid,root->withdrawal(root).remove("operation"));
        assertRejected(valid,root->withdrawal(root).remove("voluntary"));
        assertRejected(valid,root->election(root).addProperty("stage","CONTESTED"));
        assertRejected(valid,root->election(root).getAsJsonObject("end").addProperty("meeting",3));
        assertRejected(valid,root->{
            withdrawal(root).addProperty("meeting",2);
            election(root).getAsJsonObject("end").addProperty("meeting",1);
        });
        assertRejected(valid,root->election(root).getAsJsonObject("end").addProperty("legacy",true));
        assertRejected(valid,root->election(root).getAsJsonObject("end").addProperty("operation",withdrawal.toString()));
    }

    @Test void warningCountsOnlyDistinctFutureMeetingsOrAnUsableCurrentMeeting() {
        UUID first=UUID.randomUUID(),replacement=UUID.randomUUID();
        var open=SuccessionState.empty().open(first,1,THREE.keySet());
        var voided=open.finish(first,BallotStatus.VOID,null);
        assertEquals("",ElectionPlanning.warning(voided,1,DISCUSSION,List.of(1,2,2)));
        assertEquals("",ElectionPlanning.warning(voided,1,RECESS,List.of(1,2)));
        assertTrue(ElectionPlanning.warning(voided,1,BETWEEN,List.of(1,2,2)).contains("1 declared meeting"));
        assertTrue(ElectionPlanning.warning(voided,1,GATHERING,List.of(1,2)).contains("1 declared meeting"));
        assertTrue(ElectionPlanning.warning(voided,1,DISCUSSION,List.of(2,2)).contains("2 more Regency ballots"));
        var result=new SuccessionRules.Result(SuccessionRules.Decision.ELIMINATED,HA,List.of());
        var contested=voided.open(replacement,1,THREE.keySet()).finish(replacement,BallotStatus.CLOSED,result);
        assertTrue(ElectionPlanning.warning(contested,1,DISCUSSION,List.of(1)).contains("0 declared meetings"));
        var one=contested.withdraw(UUID.randomUUID(),HB,B,"I withdraw.",1);
        assertTrue(ElectionPlanning.warning(one,1,DISCUSSION,List.of(1)).contains("1 more Regency ballot"));
        assertEquals("",ElectionPlanning.warning(one,2,DISCUSSION,List.of(1,2)));
        assertEquals("",ElectionPlanning.warning(one.endUnresolved(UUID.randomUUID(),HOST,"End.",2),2,BETWEEN,List.of()));
        assertEquals("",ElectionPlanning.warning(SuccessionState.empty(),0,BETWEEN,List.of()));
    }

    private Path journal() { return directory.resolve("proceedings.json"); }
    private JsonObject json() throws IOException { return JsonParser.parseString(Files.readString(journal())).getAsJsonObject(); }
    private static JsonObject election(JsonObject root) { return root.getAsJsonObject("state").getAsJsonObject("succession"); }
    private static JsonObject withdrawal(JsonObject root) { return election(root).getAsJsonArray("withdrawals").get(0).getAsJsonObject(); }
    private void assertRejected(JsonObject source,Consumer<JsonObject> change) throws Exception {
        var corrupt=source.deepCopy();change.accept(corrupt);String retained=corrupt.toString();
        Files.writeString(journal(),retained);var store=new ProceedingsStore(journal());
        assertThrows(IOException.class,store::load);
        assertThrows(IOException.class,()->store.save(ProceedingsState.empty()));
        assertEquals(retained,Files.readString(journal()));
    }
    private ProceedingsService service(ProceedingsRepository store) throws Exception {
        var ballots=new BallotService(new BallotStore(directory.resolve("ballots.json")),Clock.systemUTC());
        return new ProceedingsService(store,ballots,Clock.systemUTC(),size->0,ALLOW);
    }
    private ProceedingsService started(ProceedingsRepository store) throws Exception {
        var service=service(store);service.region(REGION.id());service.openMeeting();
        var ballot=service.openRegency(HOST,REGION,ELECTORS,THREE);
        service.close(ballot.definition().id(),HOST);
        return service;
    }
}
