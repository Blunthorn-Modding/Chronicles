package io.github.chronicle.core.server;

import io.github.chronicle.core.network.ItemEnchantingPayloads.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ItemEnchantingWorkstationPayloadTest {
    private static final UUID SESSION = UUID.randomUUID(), TOKEN = UUID.randomUUID();
    private static RecipeHint hint() { return new RecipeHint("minecraft:echo_shard", "minecraft:sharpness", "Sharpness", 1, 1); }
    private static View view(List<RecipeHint> recipes, boolean opening) { return new View(7, SESSION, TOKEN, 0, "Ancient Forge", State.EMPTY_TARGET, recipes, opening); }

    @Test void openingRequiresCatalogButLaterUpdatesMayOmitIt() {
        assertThrows(IllegalArgumentException.class, () -> view(null, true));
        assertNull(view(null, false).recipes());
        assertEquals(List.of(hint()), view(null, false).withRecipes(List.of(hint())).recipes());
    }
    @Test void catalogIsImmutableAndBounded() {
        var rows = new ArrayList<>(List.of(hint()));
        var view = view(rows, true);
        rows.clear();
        assertEquals(1, view.recipes().size());
        assertThrows(UnsupportedOperationException.class, () -> view.recipes().clear());
        assertDoesNotThrow(() -> view(Collections.nCopies(256, hint()), true));
        assertThrows(IllegalArgumentException.class, () -> view(Collections.nCopies(257, hint()), true));
    }
    @Test void viewRejectsMissingAuthorityAndInvalidRevision() {
        assertThrows(IllegalArgumentException.class, () -> new View(-1, SESSION, TOKEN, 0, "Forge", State.READY, List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new View(1, null, TOKEN, 0, "Forge", State.READY, List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new View(1, SESSION, null, 0, "Forge", State.READY, List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new View(1, SESSION, TOKEN, -1, "Forge", State.READY, List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new View(1, SESSION, TOKEN, 0, " ", State.READY, List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new View(1, SESSION, TOKEN, 0, "x".repeat(81), State.READY, List.of(), true));
    }
    @Test void hintsRejectInvalidIdentifiersLevelsAndAmounts() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeHint("Minecraft:echo_shard", "minecraft:sharpness", "Sharpness", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RecipeHint("echo_shard", "minecraft:sharpness", "Sharpness", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RecipeHint("minecraft:echo_shard", "minecraft:sharpness", " ", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RecipeHint("minecraft:echo_shard", "minecraft:sharpness", "Sharpness", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RecipeHint("minecraft:echo_shard", "minecraft:sharpness", "Sharpness", 256, 1));
        assertThrows(IllegalArgumentException.class, () -> new RecipeHint("minecraft:echo_shard", "minecraft:sharpness", "Sharpness", 1, 65));
    }
    @Test void supportedDestinationsAreFiniteAndButtonSpecific() {
        for (int mode : new int[]{0, 3}) for (int button : new int[]{0, 1}) assertDoesNotThrow(() -> new Take(1, SESSION, TOKEN, mode, button));
        assertDoesNotThrow(() -> new Take(1, SESSION, TOKEN, 1, 0));
        for (int button : new int[]{0, 8, 40}) assertDoesNotThrow(() -> new Take(1, SESSION, TOKEN, 2, button));
        for (int[] invalid : new int[][]{{-1,0},{4,0},{0,2},{1,1},{2,9},{2,39},{3,2}})
            assertThrows(IllegalArgumentException.class, () -> new Take(1, SESSION, TOKEN, invalid[0], invalid[1]));
        assertThrows(IllegalArgumentException.class, () -> new Take(1, null, TOKEN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Take(1, SESSION, null, 0, 0));
    }
}
