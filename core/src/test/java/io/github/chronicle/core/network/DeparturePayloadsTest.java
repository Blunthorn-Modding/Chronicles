package io.github.chronicle.core.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeparturePayloadsTest {
    private static final UUID FIRST=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FINAL=UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test void stagesRequireExactParentShape(){
        assertDoesNotThrow(()->new DeparturePayloads.View(FIRST,DeparturePayloads.NO_PARENT,1,"Leave?","First warning","Continue","",true,true,false));
        assertDoesNotThrow(()->new DeparturePayloads.View(FINAL,FIRST,2,"Point of no return","Final warning","Depart","Cancel is focused.",true,true,false));
        assertThrows(IllegalArgumentException.class,()->new DeparturePayloads.View(FIRST,FINAL,1,"Leave?","Warning","Continue","",true,true,false));
        assertThrows(IllegalArgumentException.class,()->new DeparturePayloads.View(FINAL,DeparturePayloads.NO_PARENT,2,"Final","Warning","Depart","",true,true,false));
    }

    @Test void actionsAndUtf8PayloadsAreStrictlyBounded(){
        assertThrows(IllegalArgumentException.class,()->new DeparturePayloads.Action(FIRST,-1));
        assertThrows(IllegalArgumentException.class,()->new DeparturePayloads.Action(FIRST,3));
        assertThrows(IllegalArgumentException.class,()->new DeparturePayloads.View(FIRST,DeparturePayloads.NO_PARENT,1,"Leave?","bad\u0000text","Continue","",true,true,false));
        String oversized="€".repeat(DeparturePayloads.View.MAX_DETAILS_BYTES/3+1);
        assertThrows(IllegalArgumentException.class,()->new DeparturePayloads.View(FIRST,DeparturePayloads.NO_PARENT,1,"Leave?",oversized,"Continue","",true,true,false));
    }
}
