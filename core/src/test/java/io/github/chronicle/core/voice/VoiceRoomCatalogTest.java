package io.github.chronicle.core.voice;

import io.github.chronicle.core.authoring.AuthoringRegistry;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VoiceRoomCatalogTest {
    private static AuthoringSite.Region region(String id, String dimension, int minX, int maxX) {
        return new AuthoringSite.Region(id, dimension, 1, new BlockPosition(minX, 0, 0), new BlockPosition(maxX, 2, 2));
    }
    private static VoiceRoomStore.Binding binding(String id) { return new VoiceRoomStore.Binding(id, 1); }

    @Test void rejectsOverlapButAcceptsAdjacencyAndSeparateDimensions() {
        var a = region("a", "minecraft:overworld", 0, 2);
        var overlap = region("b", "minecraft:overworld", 2, 4);
        var adjacent = region("b", "minecraft:overworld", 3, 4);
        var other = region("c", "minecraft:the_nether", 0, 2);
        assertTrue(VoiceRoomCatalog.overlaps(a, overlap));
        assertFalse(VoiceRoomCatalog.overlaps(a, adjacent));
        assertFalse(VoiceRoomCatalog.overlaps(a, other));
        assertThrows(IllegalArgumentException.class, () -> new VoiceRoomCatalog(Map.of("a", binding("a"), "b", binding("b")), new AuthoringRegistry(1, Map.of("a", a, "b", overlap))));
        var catalog = new VoiceRoomCatalog(Map.of("a", binding("a"), "b", binding("b"), "c", binding("c")), new AuthoringRegistry(1, Map.of("a", a, "b", adjacent, "c", other)));
        assertEquals(3, catalog.regions().size());
        assertThrows(UnsupportedOperationException.class, () -> catalog.regions().clear());
        assertEquals(adjacent, catalog.at("minecraft:overworld", 3, 1, 1).orElseThrow());
        assertEquals(other, catalog.at("minecraft:the_nether", 1, 1, 1).orElseThrow());
    }

    @Test void requiresExactRegionIdentityAndRevision() {
        var a = region("a", "minecraft:overworld", 0, 2);
        var point = new AuthoringSite.Location("point", "minecraft:overworld", 1, 0, 0, 0, 0, 0);
        var registry = new AuthoringRegistry(1, Map.of("a", a, "point", point));
        assertThrows(IllegalArgumentException.class, () -> new VoiceRoomCatalog(Map.of("a", new VoiceRoomStore.Binding("a", 2)), registry));
        assertThrows(IllegalArgumentException.class, () -> new VoiceRoomCatalog(Map.of("point", binding("point")), registry));
        assertThrows(IllegalArgumentException.class, () -> new VoiceRoomCatalog(Map.of("missing", binding("missing")), registry));
        assertThrows(IllegalArgumentException.class, () -> new VoiceRoomCatalog(Map.of("wrong", binding("a")), registry));
    }

    @Test void feetPositionsUseHalfOpenBlockBoundsAndRejectNonfiniteValues() {
        var edge = region("edge", "minecraft:overworld", 29_999_999, 30_000_000);
        var catalog = new VoiceRoomCatalog(Map.of("edge", binding("edge")), new AuthoringRegistry(1, Map.of("edge", edge)));
        assertTrue(catalog.at("minecraft:overworld", 29_999_999, 0, 0).isPresent());
        assertTrue(catalog.at("minecraft:overworld", 30_000_000.999, 2.999, 2.999).isPresent());
        assertTrue(catalog.at("minecraft:overworld", 30_000_001, 1, 1).isEmpty());
        assertTrue(catalog.at("minecraft:overworld", 30_000_000, -0.001, 1).isEmpty());
        assertTrue(catalog.at("minecraft:overworld", Double.NaN, 1, 1).isEmpty());
        assertTrue(catalog.at("minecraft:overworld", Double.POSITIVE_INFINITY, 1, 1).isEmpty());
        assertTrue(catalog.at(null, 1, 1, 1).isEmpty());
    }
}
