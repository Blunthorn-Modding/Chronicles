package io.github.chronicle.lastcouncil.council;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;
import static io.github.chronicle.lastcouncil.council.SuccessionRules.*;
import static org.junit.jupiter.api.Assertions.*;

class SuccessionRulesTest {
    private static final String A = "c:a", B = "c:b", C = "c:c", D = "c:d";
    private static final IntUnaryOperator NO_DRAW = n -> { throw new AssertionError("unexpected draw"); };

    @Test void briefExamples() {
        assertEquals(new Result(Decision.ELIMINATED, C, List.of()), decide(Set.of(A,B,C), Map.of(A,4,B,3,C,1,"abstain",0), 8, NO_DRAW));
        assertEquals(new Result(Decision.VACANT, "", List.of()), decide(Set.of(A,B), Map.of(A,4,B,4,"abstain",0), 8, NO_DRAW));
        assertEquals(new Result(Decision.CROWNED, A, List.of()), decide(Set.of(A,B), Map.of(A,5,B,3,"abstain",0), 8, NO_DRAW));
        assertEquals(new Result(Decision.CROWNED, A, List.of()), decide(Set.of(A,B,C), Map.of(A,5,B,2,C,2,"abstain",0), 9, NO_DRAW));
        assertEquals(new Result(Decision.ELIMINATED, D, List.of(C,D)), decide(Set.of(A,B,C,D), Map.of(A,4,B,2,C,1,D,1,"abstain",0), 8, n -> { assertEquals(2,n); return 1; }));
        assertEquals(new Result(Decision.CROWNED, B, List.of()), decide(Set.of(A,B), Map.of(A,2,B,3,"abstain",0), 5, NO_DRAW));
    }

    @Test void abstentionsAndZeroVotesParticipateInLastPlace() {
        assertEquals(new Result(Decision.ELIMINATED, B, List.of(A,B,C)), decide(Set.of(C,A,B), Map.of(A,0,B,0,C,0,"abstain",8), 8, n -> 1));
        assertEquals(new Result(Decision.ELIMINATED, C, List.of(B,C)), decide(Set.of(A,B,C), Map.of(A,2,B,0,C,0,"abstain",1), 8, n -> 1));
    }

    @Test void confirmationAndEmptyCases() {
        assertEquals(new Result(Decision.CROWNED, A, List.of()), decide(Set.of(A), Map.of("yes",3,"no",1,"abstain",1), 5, NO_DRAW));
        assertEquals(new Result(Decision.VACANT, "", List.of()), decide(Set.of(A), Map.of("yes",2,"no",2,"abstain",1), 5, NO_DRAW));
        assertEquals(new Result(Decision.VACANT, "", List.of()), decide(Set.of(), Map.of("abstain",0), 0, NO_DRAW));
        assertEquals(new Result(Decision.VACANT, "", List.of()), decide(Set.of(A,B,C), Map.of(A,0,B,0,C,0,"abstain",0), 0, NO_DRAW));
    }

    @Test void rejectsInvalidInputsAndDraws() {
        Set<String> slate = Set.of(A,B,C);
        assertThrows(IllegalArgumentException.class, () -> decide(slate, Map.of(A,-1,B,0,C,0,"abstain",0), 3, NO_DRAW));
        assertThrows(IllegalArgumentException.class, () -> decide(slate, Map.of(A,3,B,1,C,0,"abstain",0), 3, NO_DRAW));
        assertThrows(IllegalArgumentException.class, () -> decide(slate, Map.of(A,0,B,0,C,0,"other",0), 3, NO_DRAW));
        assertThrows(IllegalArgumentException.class, () -> decide(Set.of("bad id"), Map.of("yes",0,"no",0,"abstain",0), 3, NO_DRAW));
        assertThrows(IllegalArgumentException.class, () -> decide(slate, Map.of(A,0,B,0,C,0,"abstain",0), -1, NO_DRAW));
        assertThrows(IllegalArgumentException.class, () -> decide(slate, Map.of(A,0,B,0,C,0,"abstain",0), 3, n -> n));
        assertThrows(IllegalArgumentException.class, () -> decide(slate, Map.of(A,0,B,0,C,0,"abstain",0), 3, n -> -1));
    }

    @Test void resultCopiesAndValidatesTieEvidence() {
        var tie = new ArrayList<>(List.of(A,B));
        var result = new Result(Decision.ELIMINATED, A, tie);
        tie.clear();
        assertEquals(List.of(A,B), result.tiedLast());
        assertThrows(UnsupportedOperationException.class, () -> result.tiedLast().clear());
        assertThrows(IllegalArgumentException.class, () -> new Result(Decision.ELIMINATED, A, List.of(B,A)));
        assertThrows(IllegalArgumentException.class, () -> new Result(Decision.VACANT, A, List.of()));
    }
}
