package io.github.chronicle.core.authoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegionDraftTest {
    @Test
    void editingSeedsBothSavedCornersAndChangingOneLeavesTheOtherUntouched() {
        var saved = new AuthoringSite.Region("city", "minecraft:overworld", 7,
                new BlockPosition(-4, 10, 2), new BlockPosition(8, 30, 12));
        var draft = RegionDraft.edit(saved).withCorner(2, new BlockPosition(9, 32, 13));

        assertTrue(draft.existing());
        assertEquals(7, draft.baseRevision());
        assertEquals(saved.min(), draft.first());
        assertEquals(new BlockPosition(9, 32, 13), draft.second());
        assertEquals(new AuthoringSite.Region("city", "minecraft:overworld", 1,
                saved.min(), new BlockPosition(9, 32, 13)), draft.region());
    }

    @Test
    void newDraftRequiresBothCornersAndRejectsBadCornerNumbers() {
        var draft = RegionDraft.create("new_area", "minecraft:overworld");
        assertFalse(draft.existing());
        assertThrows(IllegalStateException.class, draft::region);
        assertThrows(IllegalArgumentException.class, () -> draft.withCorner(3, new BlockPosition(0, 0, 0)));
    }
}
