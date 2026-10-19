package io.github.chronicle.core.authoring.ui;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.chronicle.core.authoring.ui.AuthoringUiModel.Action.CHEST_BIND;
import static io.github.chronicle.core.authoring.ui.AuthoringUiModel.Tab.CHESTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoringUiModelTest {
    private static final Gson JSON = new Gson();

    @Test
    void entryRejectsMissingOrUnboundedTextAndNegativeRevision() {
        assertThrows(IllegalArgumentException.class, () -> new AuthoringUiModel.Entry(null, "label", "detail", "kind", "reference", 0));
        assertThrows(IllegalArgumentException.class, () -> new AuthoringUiModel.Entry("id\0", "label", "detail", "kind", "reference", 0));
        assertThrows(IllegalArgumentException.class, () -> new AuthoringUiModel.Entry("x".repeat(161), "label", "detail", "kind", "reference", 0));
        assertThrows(IllegalArgumentException.class, () -> new AuthoringUiModel.Entry("id", "label", "detail", "kind", "reference", -1));
    }

    @Test
    void snapshotEnforcesTokenPaginationSessionAndTextBounds() {
        assertThrows(NullPointerException.class, () -> new AuthoringUiModel.Snapshot(
                1, null, 0, 1, "phase", 0, false, false,
                "hold", "selection", "target", "message", false, List.of(), List.of(), "player"));
        assertThrows(IllegalArgumentException.class, () -> new AuthoringUiModel.Snapshot(
                1, CHESTS, 0, 1, null, 0, false, false,
                "hold", "selection", "target", "message", false, List.of(), List.of(), "player"));
        assertThrows(IllegalArgumentException.class, () -> snapshot(0, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 0, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new AuthoringUiModel.Snapshot(
                1, CHESTS, 0, 1, "phase", 0, false, false,
                "x\0", "selection", "target", "message", false, List.of(), List.of(), "player"));
        assertThrows(NullPointerException.class, () -> new AuthoringUiModel.Snapshot(
                1, CHESTS, 0, 1, "phase", 0, false, false,
                "hold", "selection", "target", "message", false, null, List.of(), "player"));
    }

    @Test
    void snapshotCopiesAndBoundsEntryAndChoiceLists() {
        List<AuthoringUiModel.Entry> entries = new ArrayList<>(List.of(entry("one")));
        List<AuthoringUiModel.Entry> choices = new ArrayList<>(List.of(entry("choice")));
        AuthoringUiModel.Snapshot snapshot = new AuthoringUiModel.Snapshot(
                1, CHESTS, 0, 1, "phase", 0, true, true,
                "hold", "selection", "target", "message", true, entries, choices, "player");

        entries.clear();
        choices.clear();
        assertEquals(1, snapshot.entries().size());
        assertEquals(1, snapshot.choices().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.choices().clear());
        assertThrows(IllegalArgumentException.class, () -> new AuthoringUiModel.Snapshot(
                1, CHESTS, 0, 1, "phase", 0, false, false,
                "hold", "selection", "target", "message", false,
                java.util.stream.IntStream.range(0, 13).mapToObj(i -> entry("entry" + i)).toList(), List.of(), "player"));
        assertThrows(IllegalArgumentException.class, () -> new AuthoringUiModel.Snapshot(
                1, CHESTS, 0, 1, "phase", 0, false, false,
                "hold", "selection", "target", "message", false,
                List.of(), java.util.stream.IntStream.range(0, 129).mapToObj(i -> entry("choice" + i)).toList(), "player"));
    }

    @Test
    void gsonRoundTripPreservesNamespacedUnicodeAndPrivatePreviewMetadata() {
        AuthoringUiModel.Snapshot original = new AuthoringUiModel.Snapshot(
                42, CHESTS, 1, 3, "authoring", 2, true, false,
                "private hold: 玩家 🕯️", "chronicle:chest/β", "minecraft:overworld@12,64,-8",
                "Café — aperçu privé", true,
                List.of(new AuthoringUiModel.Entry("chronicle:cache_ä", "Coffre privé 🔒", "owner-only metadata", "loot", "minecraft:chest", 7)),
                List.of(new AuthoringUiModel.Entry("minecraft:player/玩家", "Choix", "秘密", "player", "chronicle:role/éclat", 8)),
                "玩家_é");

        AuthoringUiModel.Snapshot restored = JSON.fromJson(JSON.toJson(original), AuthoringUiModel.Snapshot.class);

        assertEquals(original, restored);
        assertEquals("chronicle:chest/β", restored.selection());
        assertEquals("private hold: 玩家 🕯️", restored.hold());
    }

    @Test
    void actionsAreFiniteAndDoNotAcceptArbitraryCommandOperations() {
        assertEquals(CHEST_BIND, AuthoringUiModel.Action.valueOf("CHEST_BIND"));
        assertThrows(IllegalArgumentException.class, () -> AuthoringUiModel.Action.valueOf("RUN_SERVER_COMMAND"));
        assertFalse(List.of(AuthoringUiModel.Action.values()).stream().anyMatch(action -> action.name().contains("COMMAND")));
    }

    @Test
    void renderOnlyClockAndFeedbackChangesKeepControlsButAuthorityChangesDoNot() {
        var before = snapshot(12, 0, 1, 3);
        var clock = new AuthoringUiModel.Snapshot(12, CHESTS, 0, 1, "phase", 3, false, false,
                "hold", "Active play: 13s", "target", "new feedback", true, List.of(), List.of(), "player");
        assertTrue(before.sameControls(clock));
        assertFalse(before.sameControls(snapshot(13, 0, 1, 3)));
        assertFalse(before.sameControls(snapshot(12, 0, 1, 4)));
        var changedEntry = new AuthoringUiModel.Snapshot(12, CHESTS, 0, 1, "phase", 3, false, false,
                "hold", "selection", "target", "message", false, List.of(entry("new")), List.of(), "player");
        assertFalse(before.sameControls(changedEntry));
    }

    private static AuthoringUiModel.Entry entry(String id) {
        return new AuthoringUiModel.Entry(id, "label", "detail", "kind", "reference", 0);
    }

    private static AuthoringUiModel.Snapshot snapshot(long token, int page, int pages, int session) {
        return new AuthoringUiModel.Snapshot(token, CHESTS, page, pages, "phase", session, false, false,
                "hold", "selection", "target", "message", false, List.of(), List.of(), "player");
    }
}
