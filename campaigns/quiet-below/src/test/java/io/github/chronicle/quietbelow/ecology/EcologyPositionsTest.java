package io.github.chronicle.quietbelow.ecology;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.Pos;
import static org.junit.jupiter.api.Assertions.*;

class EcologyPositionsTest {
    private static final Pos A=new Pos(0,0,0), B=new Pos(1,0,0), C=new Pos(2,0,0), D=new Pos(3,0,0);

    @Test void stableSlotsStayBoundedAndAreReusedAcrossChurn() {
        var positions=new EcologyPositions(3);
        positions.add(A);positions.add(B);positions.add(C);
        assertThrows(IllegalStateException.class,()->positions.add(D));
        for(int n=0;n<100;n++) {
            positions.remove(B);positions.add(B);
            positions.remove(C);positions.add(C);
        }
        assertEquals(List.of(A,B,C),scan(positions.scan()));
    }

    @Test void removalDuringScanReturnsAHoleThatConsumesOneBudgetSlot() {
        var positions=new EcologyPositions(3);
        positions.add(A);positions.add(B);positions.add(C);
        Iterator<Pos> scan=positions.scan();
        assertEquals(A,scan.next());
        positions.remove(B);
        assertNull(scan.next());
        assertEquals(C,scan.next());
        assertFalse(scan.hasNext());
        assertThrows(java.util.NoSuchElementException.class,scan::next);
    }

    @Test void newSlotsDoNotExtendAnActivePassAndReusedSlotsRemainVisible() {
        var positions=new EcologyPositions(3);
        positions.add(A);positions.add(B);
        Iterator<Pos> scan=positions.scan();
        positions.add(C);
        assertEquals(List.of(A,B),scan(scan));

        positions.remove(B);
        Iterator<Pos> reused=positions.scan();
        positions.add(D);
        assertEquals(List.of(A,D,C),scan(reused));
    }

    @Test void simultaneousScansRemainIndependentAndClearInvalidatesPriorSlotsSafely() {
        var positions=new EcologyPositions(3);
        positions.add(A);positions.add(B);
        Iterator<Pos> first=positions.scan(),second=positions.scan();
        assertEquals(A,first.next());
        assertEquals(List.of(A,B),scan(second));
        assertEquals(B,first.next());

        Iterator<Pos> beforeClear=positions.scan();
        positions.clear();
        assertEquals(java.util.Arrays.asList(null,null),scan(beforeClear));
        assertFalse(positions.scan().hasNext());
        positions.add(C);assertEquals(List.of(C),scan(positions.scan()));
    }

    @Test void nullPositionsAndNegativeCapacityAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new EcologyPositions(-1));
        var positions=new EcologyPositions(1);
        assertThrows(NullPointerException.class,()->positions.add(null));
        assertThrows(NullPointerException.class,()->positions.remove(null));
    }

    private static List<Pos> scan(Iterator<Pos> scan) {
        var result=new ArrayList<Pos>();
        scan.forEachRemaining(result::add);
        return result;
    }
}
