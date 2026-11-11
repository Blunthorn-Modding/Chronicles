package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.quietbelow.content.RelicHeartBlock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcologyHeartTest {
    private static final EcologyHeart.Proof WEAK = new EcologyHeart.Proof(true, true, 31, 64);

    @Test void defaultBecomesVulnerableOnTheExactSixtiethSecond() {
        EcologyHeart.Rules rules = EcologyHeart.DEFAULT;
        assertEquals(1200, rules.requiredTicks());
        assertFalse(EcologyHeart.vulnerable(rules, 1199, WEAK));
        int complete = EcologyHeart.nextElapsed(rules, 1199, WEAK, true, 1);
        assertEquals(1200, complete);
        assertTrue(EcologyHeart.vulnerable(rules, complete, WEAK));
    }

    @Test void knownWeakPausePreservesProgressWithoutOfflineCatchup() {
        EcologyHeart.Rules rules = EcologyHeart.DEFAULT;
        assertEquals(777, EcologyHeart.nextElapsed(rules, 777, WEAK, false, 20));
        assertEquals(797, EcologyHeart.nextElapsed(rules, 777, WEAK, true, 20));
        assertThrows(IllegalArgumentException.class, () -> EcologyHeart.nextElapsed(rules, 777, WEAK, true, 21));
    }

    @Test void incompleteOrInvalidRootResetsAndBlocksEvenCompletedProgress() {
        EcologyHeart.Rules rules = EcologyHeart.DEFAULT;
        int completed = rules.requiredTicks();
        EcologyHeart.Proof unknown = new EcologyHeart.Proof(false, true, 0, 64);
        EcologyHeart.Proof invalidRoot = new EcologyHeart.Proof(true, false, 0, 64);
        for (EcologyHeart.Proof proof : new EcologyHeart.Proof[]{unknown, invalidRoot}) {
            assertEquals(0, EcologyHeart.nextElapsed(rules, completed, proof, false, 0));
            assertFalse(EcologyHeart.vulnerable(rules, completed, proof));
        }
    }

    @Test void exactThresholdOrStrongerNetworkResetsWhileReadyCountControlsEligibility() {
        EcologyHeart.Rules fixed = new EcologyHeart.Rules(64, 60);
        EcologyHeart.Proof exact = new EcologyHeart.Proof(true, true, 64, 512);
        EcologyHeart.Proof stronger = new EcologyHeart.Proof(true, true, 65, 32);
        assertEquals(0, EcologyHeart.nextElapsed(fixed, 500, exact, false, 0));
        assertEquals(0, EcologyHeart.nextElapsed(fixed, 500, stronger, true, 20));

        // The Established/hysteresis value does not replace the actual living-ready count for a fixed rule.
        EcologyHeart.Proof weakReadyLargeEstablished = new EcologyHeart.Proof(true, true, 63, 4096);
        assertEquals(520, EcologyHeart.nextElapsed(fixed, 500, weakReadyLargeEstablished, true, 20));
    }

    @Test void followEstablishedAndFixedThresholdsResolveIndependently() {
        EcologyHeart.Rules following = new EcologyHeart.Rules(0, 30);
        EcologyHeart.Rules fixed = new EcologyHeart.Rules(128, 30);
        assertEquals(32, following.threshold(32));
        assertEquals(4096, following.threshold(4096));
        assertEquals(128, fixed.threshold(32));
        assertEquals(128, fixed.threshold(4096));

        assertEquals(0, EcologyHeart.nextElapsed(following, 200, new EcologyHeart.Proof(true, true, 32, 32), true, 20));
        assertEquals(220, EcologyHeart.nextElapsed(fixed, 200, new EcologyHeart.Proof(true, true, 32, 32), true, 20));
    }

    @Test void advancementClampsAtRequiredTicks() {
        EcologyHeart.Rules rules = new EcologyHeart.Rules(64, 15);
        assertEquals(300, EcologyHeart.nextElapsed(rules, 295, WEAK, true, 20));
        assertEquals(300, EcologyHeart.nextElapsed(rules, 300, WEAK, true, 20));
    }

    @Test void lensDistinguishesUnknownProtectedWeakeningAndVulnerableProofs() {
        EcologyHeart.Rules rules = EcologyHeart.DEFAULT;
        assertEquals(RelicHeartBlock.Lens.RESTING,
                QuietBelowEcology.heartLens(new EcologyHeart.Proof(false, true, 31, 64), rules, false));
        assertEquals(RelicHeartBlock.Lens.RESTING,
                QuietBelowEcology.heartLens(new EcologyHeart.Proof(true, false, 31, 64), rules, true));
        assertEquals(RelicHeartBlock.Lens.PROTECTED,
                QuietBelowEcology.heartLens(new EcologyHeart.Proof(true, true, 64, 64), rules, true));
        assertEquals(RelicHeartBlock.Lens.WEAKENING, QuietBelowEcology.heartLens(WEAK, rules, false));
        assertEquals(RelicHeartBlock.Lens.VULNERABLE, QuietBelowEcology.heartLens(WEAK, rules, true));
    }

    @Test void fallbackProtectionRequiresTheExactCurrentAnchorDimensionPositionAndBlock() {
        AuthoringSite.Anchor anchor = new AuthoringSite.Anchor("heart", "minecraft:overworld", 1,
                new BlockPosition(3, 64, -2), "quiet_below:relic_heart");
        EcologyTerrain.Pos position = new EcologyTerrain.Pos(3, 64, -2);
        assertTrue(QuietBelowEcology.matchesAssignedHeart(anchor, "minecraft:overworld", position, "quiet_below:relic_heart"));
        assertFalse(QuietBelowEcology.matchesAssignedHeart(anchor, "minecraft:the_nether", position, "quiet_below:relic_heart"));
        assertFalse(QuietBelowEcology.matchesAssignedHeart(anchor, "minecraft:overworld", new EcologyTerrain.Pos(4, 64, -2), "quiet_below:relic_heart"));
        assertFalse(QuietBelowEcology.matchesAssignedHeart(anchor, "minecraft:overworld", position, "minecraft:stone"));
        assertFalse(QuietBelowEcology.matchesAssignedHeart(
                new AuthoringSite.Location("heart", "minecraft:overworld", 1, 3, 64, -2, 0, 0),
                "minecraft:overworld", position, "quiet_below:relic_heart"));
    }

    @Test void validatesRulesProofAndClockBoundsWhileOfferingRecommendedChoices() {
        assertEquals(1,EcologyHeart.THRESHOLD_CHOICES.getFirst());assertEquals(EcologyEngine.MAX_CELLS,EcologyHeart.THRESHOLD_CHOICES.getLast());
        assertEquals(1,EcologyHeart.DELAY_CHOICES.getFirst());assertEquals(3600,EcologyHeart.DELAY_CHOICES.getLast());assertTrue(EcologyHeart.DELAY_CHOICES.contains(60));
        assertEquals(340, new EcologyHeart.Rules(17, 17).requiredTicks());
        assertThrows(IllegalArgumentException.class, () -> new EcologyHeart.Rules(-1, 60));
        assertThrows(IllegalArgumentException.class, () -> new EcologyHeart.Rules(EcologyEngine.MAX_CELLS + 1, 60));
        assertThrows(IllegalArgumentException.class, () -> new EcologyHeart.Rules(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new EcologyHeart.Rules(0, 3601));
        assertThrows(IllegalArgumentException.class, () -> EcologyHeart.DEFAULT.threshold(0));
        assertThrows(IllegalArgumentException.class, () -> EcologyHeart.DEFAULT.threshold(EcologyEngine.MAX_CELLS + 1));
        assertThrows(IllegalArgumentException.class, () -> new EcologyHeart.Proof(true, true, -1, 64));
        assertThrows(IllegalArgumentException.class, () -> new EcologyHeart.Proof(true, true, EcologyEngine.MAX_CELLS + 1, 64));
        assertThrows(IllegalArgumentException.class, () -> new EcologyHeart.Proof(true, true, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> EcologyHeart.nextElapsed(EcologyHeart.DEFAULT, -1, WEAK, true, 1));
        assertThrows(IllegalArgumentException.class, () -> EcologyHeart.nextElapsed(EcologyHeart.DEFAULT, 0, WEAK, true, -1));
        assertThrows(IllegalArgumentException.class, () -> EcologyHeart.vulnerable(EcologyHeart.DEFAULT, 1201, WEAK));
    }
}
