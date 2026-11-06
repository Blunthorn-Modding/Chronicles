package io.github.chronicle.lastcouncil.council;

import io.github.chronicle.core.ballots.BallotStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static io.github.chronicle.lastcouncil.council.SuccessionRules.*;
import static io.github.chronicle.lastcouncil.council.SuccessionState.*;
import static org.junit.jupiter.api.Assertions.*;

class SuccessionHistoryValidationTest {
    private static final String A="c:a", B="c:b", C="c:c", D="c:d";
    private static final Set<String> INITIAL=Set.of(A,B,C,D);
    private static final UUID ONE=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TWO=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ACTOR=UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Result ELIMINATE_D=new Result(Decision.ELIMINATED,D,List.of());

    private static Round round(int order,int meeting,Set<String> slate,BallotStatus status,Result result) {
        return new Round(order,meeting,slate,status,result);
    }
    private static SuccessionState state(Stage stage,Set<String> remaining,String crown,Map<UUID,Round> rounds,List<Withdrawal> withdrawals) {
        return new SuccessionState(stage,INITIAL,remaining,crown,rounds,withdrawals);
    }

    @Test void laterSlateCannotReintroduceOrOmitCandidates() {
        Round first=round(1,1,INITIAL,BallotStatus.CLOSED,ELIMINATE_D);
        for (Set<String> badSlate:List.of(INITIAL,Set.of(A,B))) {
            Round later=round(2,2,badSlate,BallotStatus.OPEN,null);
            assertThrows(IllegalArgumentException.class,()->state(Stage.CONTESTED,Set.of(A,B,C),"",Map.of(ONE,first,TWO,later),List.of()));
        }
    }

    @Test void noRoundMayFollowCrownOrVacancy() {
        Round crown=round(1,1,INITIAL,BallotStatus.CLOSED,new Result(Decision.CROWNED,A,List.of()));
        Round after=round(2,2,INITIAL,BallotStatus.OPEN,null);
        assertThrows(IllegalArgumentException.class,()->state(Stage.CROWNED,INITIAL,A,Map.of(ONE,crown,TWO,after),List.of()));
        Set<String> pair=Set.of(A,B);
        Round vacancy=round(1,1,pair,BallotStatus.CLOSED,new Result(Decision.VACANT,"",List.of()));
        Round afterVacancy=round(2,2,pair,BallotStatus.OPEN,null);
        assertThrows(IllegalArgumentException.class,()->new SuccessionState(Stage.VACANT,pair,pair,"",Map.of(ONE,vacancy,TWO,afterVacancy),List.of()));
    }

    @Test void sequenceMustBeContiguousAndUniqueAcrossEventTypes() {
        Round first=round(1,1,INITIAL,BallotStatus.VOID,null);
        Round skipped=round(3,2,INITIAL,BallotStatus.OPEN,null);
        assertThrows(IllegalArgumentException.class,()->state(Stage.CONTESTED,INITIAL,"",Map.of(ONE,first,TWO,skipped),List.of()));
        Withdrawal duplicate=new Withdrawal(1,D,ACTOR,"Withdrawn",1);
        assertThrows(IllegalArgumentException.class,()->state(Stage.CONTESTED,Set.of(A,B,C),"",Map.of(ONE,first),List.of(duplicate)));
    }

    @Test void meetingsCannotMoveBackwardOrRepeatAfterCompletedRound() {
        Round first=round(1,2,INITIAL,BallotStatus.VOID,null);
        Round earlier=round(2,1,INITIAL,BallotStatus.OPEN,null);
        assertThrows(IllegalArgumentException.class,()->state(Stage.CONTESTED,INITIAL,"",Map.of(ONE,first,TWO,earlier),List.of()));
        Round completed=round(1,2,INITIAL,BallotStatus.CLOSED,ELIMINATE_D);
        Round repeated=round(2,2,Set.of(A,B,C),BallotStatus.OPEN,null);
        assertThrows(IllegalArgumentException.class,()->state(Stage.CONTESTED,Set.of(A,B,C),"",Map.of(ONE,completed,TWO,repeated),List.of()));
    }

    @Test void voidReplacementCanCompleteInSameMeeting() {
        Round first=round(1,1,INITIAL,BallotStatus.VOID,null);
        Round replacement=round(2,1,INITIAL,BallotStatus.CLOSED,ELIMINATE_D);
        assertDoesNotThrow(()->state(Stage.CONTESTED,Set.of(A,B,C),"",Map.of(ONE,first,TWO,replacement),List.of()));
    }

    @Test void withdrawalChangesOnlySubsequentSlateAndMustFollowAnAttempt() {
        Round first=round(1,1,INITIAL,BallotStatus.VOID,null);
        Withdrawal withdrawal=new Withdrawal(2,D,ACTOR,"Withdrawn",2);
        Round later=round(3,2,Set.of(A,B,C),BallotStatus.OPEN,null);
        assertDoesNotThrow(()->state(Stage.CONTESTED,Set.of(A,B,C),"",Map.of(ONE,first,TWO,later),List.of(withdrawal)));
        Withdrawal premature=new Withdrawal(1,D,ACTOR,"Withdrawn",1);
        Round after=round(2,1,Set.of(A,B,C),BallotStatus.OPEN,null);
        assertThrows(IllegalArgumentException.class,()->state(Stage.CONTESTED,Set.of(A,B,C),"",Map.of(ONE,after),List.of(premature)));
        Withdrawal tooLate=new Withdrawal(3,D,ACTOR,"Withdrawn",1);
        Round openBeforeWithdrawal=round(2,1,INITIAL,BallotStatus.OPEN,null);
        assertThrows(IllegalArgumentException.class,()->state(Stage.CONTESTED,Set.of(A,B,C),"",Map.of(ONE,first,TWO,openBeforeWithdrawal),List.of(tooLate)));
    }
}
