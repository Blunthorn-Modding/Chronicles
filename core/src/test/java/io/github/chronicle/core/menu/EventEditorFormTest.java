package io.github.chronicle.core.menu;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventEditorFormTest {
    @Test void permitsIncompleteCreateDraftButDefensivelyCopiesTargets() {
        List<EventEditorForm.Change> targets = new ArrayList<>();
        targets.add(new EventEditorForm.Change("", "", "", 0, "", "", false, ""));
        EventEditorForm form = assertDoesNotThrow(() -> form(targets));
        targets.clear();
        assertEquals(1, form.changes().size());
        assertThrows(UnsupportedOperationException.class, () -> form.changes().clear());
    }

    @Test void boundsNumbersHashesAndTargetCount() {
        assertThrows(IllegalArgumentException.class, () -> new EventEditorForm.Change("region", "label", "START", -1, "", "", true, ""));
        assertThrows(IllegalArgumentException.class, () -> new EventEditorForm.Change("region", "label", "START", 1, "not-a-hash", "", true, ""));
        assertThrows(IllegalArgumentException.class, () -> new EventEditorForm("id", 0, "", "", "", "", "", "", 0, "", "", 0,
                EventEditorForm.MAX_SECONDS + 1, "", List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> form(java.util.Collections.nCopies(EventEditorForm.MAX_CHANGES + 1,
                new EventEditorForm.Change("region", "label", "START", 0, "", "", true, ""))));
    }

    private static EventEditorForm form(List<EventEditorForm.Change> targets) {
        return new EventEditorForm("", 0, "", "", "", "", "PARTICIPANTS", "", 0,
                "MANUAL", "", 0, 0, "", targets, true);
    }
}
