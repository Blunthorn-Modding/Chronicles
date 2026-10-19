package io.github.chronicle.core.authoring;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoringDomainTest {
    @Test
    void regionsNormalizeCornersAndUseConstantTimeInclusiveMembership() {
        AuthoringSite.Region region = new AuthoringSite.Region("cave", "minecraft:overworld", 1,
                new BlockPosition(10, 80, 20), new BlockPosition(-10, -20, -20));

        assertEquals(new BlockPosition(-10, -20, -20), region.min());
        assertEquals(new BlockPosition(10, 80, 20), region.max());
        assertTrue(region.contains(new BlockPosition(-10, -20, -20)));
        assertTrue(region.contains(new BlockPosition(10, 80, 20)));
        assertFalse(region.contains(new BlockPosition(11, 80, 20)));
        assertEquals(21L * 101 * 41, region.volume());
    }

    @Test
    void largeRegionsDoNotEnumerateAndSaturateTheirVolume() {
        AuthoringSite.Region region = new AuthoringSite.Region("large", "minecraft:overworld", 1,
                new BlockPosition(-30_000_000, -2_000_000, -30_000_000), new BlockPosition(30_000_000, 2_000_000, 30_000_000));

        assertEquals(Long.MAX_VALUE, region.volume());
        assertTrue(region.contains(new BlockPosition(0, 0, 0)));
    }

    @Test
    void validatesIdentifiersFiniteCoordinatesAndBounds() {
        assertThrows(IllegalArgumentException.class, () -> new AuthoringSite.Region("Bad-Name", "minecraft:overworld", 1,
                new BlockPosition(0, 0, 0), new BlockPosition(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new AuthoringSite.Region("good", "overworld", 1,
                new BlockPosition(0, 0, 0), new BlockPosition(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new BlockPosition(30_000_001, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlockPosition(0, 2_000_001, 0));
        assertThrows(IllegalArgumentException.class, () -> new AuthoringSite.Location("point", "minecraft:overworld", 1,
                Double.NaN, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AuthoringSite.Anchor("anchor", "minecraft:overworld", 1,
                new BlockPosition(0, 0, 0), "stone"));
    }

    @Test
    void registryIsOrderedImmutableAndChecksRevisions() {
        Map<String, AuthoringSite> supplied = new LinkedHashMap<>();
        supplied.put("zeta", region("zeta", 1));
        supplied.put("alpha", region("alpha", 1));
        AuthoringRegistry registry = new AuthoringRegistry(1, supplied);

        assertEquals(java.util.List.of("alpha", "zeta"), registry.sites().keySet().stream().toList());
        assertThrows(UnsupportedOperationException.class, () -> registry.sites().clear());
        assertThrows(IllegalArgumentException.class, () -> new AuthoringRegistry(0, Map.of("alpha", region("alpha", 1))));
        assertThrows(IllegalArgumentException.class, () -> new AuthoringRegistry(1, Map.of("wrong", region("alpha", 1))));
    }

    private static AuthoringSite.Region region(String id, long revision) {
        return new AuthoringSite.Region(id, "minecraft:overworld", revision, new BlockPosition(0, 0, 0), new BlockPosition(1, 1, 1));
    }
}
