package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuietBelowManagedBlocksTest {
    private static final BlockPosition ROOT = new BlockPosition(1, 2, 3);

    @Test void unboundCanMoveBoundCanOnlyRepairExactAndEndedOrUnknownDeny() {
        assertNull(QuietBelowManagedBlocks.structuralProblem(authority(QuietBelowEcology.RootPhase.UNBOUND), "minecraft:the_nether", new BlockPosition(9, 9, 9)));
        assertNull(QuietBelowManagedBlocks.structuralProblem(authority(QuietBelowEcology.RootPhase.BOUND), "minecraft:overworld", ROOT));
        assertNotNull(QuietBelowManagedBlocks.structuralProblem(authority(QuietBelowEcology.RootPhase.BOUND), "minecraft:overworld", new BlockPosition(2, 2, 3)));
        assertNotNull(QuietBelowManagedBlocks.structuralProblem(authority(QuietBelowEcology.RootPhase.BOUND), "minecraft:the_nether", ROOT));
        assertNotNull(QuietBelowManagedBlocks.structuralProblem(authority(QuietBelowEcology.RootPhase.ENDED), "minecraft:overworld", ROOT));
        assertNotNull(QuietBelowManagedBlocks.structuralProblem(authority(QuietBelowEcology.RootPhase.UNKNOWN), "minecraft:overworld", ROOT));
    }

    private static QuietBelowEcology.RootAuthority authority(QuietBelowEcology.RootPhase phase) {
        return new QuietBelowEcology.RootAuthority(phase, phase == QuietBelowEcology.RootPhase.UNBOUND ? null : "quiet_below:relic_heart",
                phase == QuietBelowEcology.RootPhase.UNBOUND ? null : "minecraft:overworld", phase == QuietBelowEcology.RootPhase.UNBOUND ? 0 : 4,
                phase == QuietBelowEcology.RootPhase.UNBOUND ? null : ROOT);
    }
}
