package io.github.chronicle.quietbelow.ecology;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EcologyMaintenancePolicyTest {
    @Test void quietPreferenceAndHardHeadroom(){var p=EcologyMaintenancePolicy.DEFAULT;
        assertTrue(p.due(true,16L<<20,0,1,0,0,-1));assertFalse(p.due(false,16L<<20,0,1,0,0,-1));
        assertTrue(p.due(false,32L<<20,0,1,0,0,-1));assertTrue(p.due(false,0,75_000,1,0,0,-1));
        assertFalse(p.due(true,0,37_499,1,0,0,-1));assertTrue(p.due(true,0,37_500,1,0,0,-1));
    }
    @Test void noGrowthNeverRecompactsAndUrgentProgressDoesNotWaitOutCooldown(){var p=EcologyMaintenancePolicy.DEFAULT;
        assertFalse(p.due(true,32L<<20,80_000,100_000,32L<<20,80_000,1));
        assertFalse(p.due(true,24L<<20,30_000,50,16L<<20,30_000,1));
        assertTrue(p.due(true,24L<<20,30_000,6001,16L<<20,30_000,1));
        assertTrue(p.due(false,40L<<20,80_000,50,32L<<20,80_000,1));
    }
    @Test void invalidPolicyCannotDisableStorageHeadroom(){assertThrows(IllegalArgumentException.class,()->new EcologyMaintenancePolicy(1,1,EcologyJournal.MAX_FILE_BYTES,2,0,1,1));}
}
