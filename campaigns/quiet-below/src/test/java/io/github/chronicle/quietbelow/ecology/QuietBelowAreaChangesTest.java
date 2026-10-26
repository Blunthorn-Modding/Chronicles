package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.areas.AreaCell;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuietBelowAreaChangesTest {
    private static final AreaCell HEART = new AreaCell("{\"Name\":\"quiet_below:relic_heart\"}", null);
    private static final AreaCell STONE = new AreaCell("{\"Name\":\"minecraft:stone\"}", null);

    @Test void unavailableEcologyRefusesAreaPreparationAndCompletion(){assertThrows(IllegalStateException.class,()->QuietBelowEcology.requireAreaOperation(null));assertThrows(IllegalStateException.class,()->QuietBelowEcology.flushAreaOperation(null));}

    @Test void heartEvidenceIsRecognizedAndCannotBeChanged() {
        assertTrue(QuietBelowAreaChanges.isHeart(HEART));
        assertFalse(QuietBelowAreaChanges.isHeart(STONE));
        assertThrows(IllegalStateException.class, () -> QuietBelowAreaChanges.validateCell(HEART, STONE));
        assertThrows(IllegalStateException.class, () -> QuietBelowAreaChanges.validateCell(STONE, HEART));
        assertDoesNotThrow(() -> QuietBelowAreaChanges.validateCell(HEART, HEART));
    }
    @Test void endingPreventsNetworkRestorationWithoutBlockingOrdinaryDepartureTerrain(){AreaCell air=new AreaCell("{\"Name\":\"minecraft:air\"}",null),sculk=new AreaCell("{\"Name\":\"minecraft:sculk\"}",null);assertDoesNotThrow(()->QuietBelowAreaChanges.validateCell(STONE,air,true));assertThrows(IllegalStateException.class,()->QuietBelowAreaChanges.validateCell(STONE,sculk,true));assertThrows(IllegalStateException.class,()->QuietBelowAreaChanges.validateCell(sculk,sculk,true));assertDoesNotThrow(()->QuietBelowAreaChanges.validateCell(STONE,sculk,false));}
}
