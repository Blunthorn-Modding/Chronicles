package io.github.chronicle.lastcouncil.council;

import io.github.chronicle.core.ballots.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.chronicle.lastcouncil.council.ProceedingsServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ProceedingsPresenceTest {
    @Test void absentLockedCandidateDoesNotInvokeGateOrChangeState() throws Exception {
        var repo=new Repo();var ledger=new Ballots();int[] calls={0};
        var gate=new ProceedingsService.ProgressionGate() {
            public void beforeRegency(int meeting){calls[0]++;}
            public void beforeMeetingClose(int meeting){}
        };
        var service=new ProceedingsService(repo,new BallotService(ledger,Clock.systemUTC()),Clock.systemUTC(),n->0,gate);
        service.region(REGION.id());service.openMeeting();
        var first=service.openRegency(HOST,REGION,ELECTORS,Map.of("c:a","Alice (A)","c:b","Bob (B)"));
        service.voidBallot(first.definition().id(),HOST,"Retry");
        var before=service.state();int saves=repo.saves;
        var failure=assertThrows(IllegalStateException.class,()->service.openRegency(HOST,REGION,ELECTORS,Map.of("c:a","Alice (A)")));
        assertTrue(failure.getMessage().contains("c:b"));assertEquals(1,calls[0]);
        assertEquals(before,service.state());assertEquals(saves,repo.saves);assertNull(service.state().activeBallot());
        var replacement=service.openRegency(HOST,REGION,ELECTORS,Map.of("c:a","Alice (A)","c:b","Bob (B)"));
        assertNotEquals(first.definition().id(),replacement.definition().id());
        assertEquals(first.definition().options(),replacement.definition().options());
        assertEquals(2,replacement.definition().threshold());
    }
    @Test void aggregateDoesNotChangeSecretProjection() throws Exception {
        var f=fixture();var ballot=f.service().open(f.motion(),HOST,REGION,ELECTORS);var id=ballot.definition().id();
        f.service().cast(id,A,"yes");
        assertEquals(1,f.service().submissionCount(id));
        var view=f.service().view(id,null);
        assertEquals("",view.ownChoice());assertTrue(view.namedVotes().isEmpty());assertTrue(view.totals().isEmpty());
    }
}
