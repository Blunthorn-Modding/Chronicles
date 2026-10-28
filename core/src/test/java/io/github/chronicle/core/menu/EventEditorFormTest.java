package io.github.chronicle.core.menu;

import io.github.chronicle.core.play.EventSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventEditorFormTest {
    @Test void copiesAndBoundsProgressionFields() {
        List<String> required = new ArrayList<>(List.of("test:a", "test:b"));
        Map<String, Boolean> flags = new HashMap<>(Map.of("test:done", true));
        EventEditorForm form = progression(required, flags);
        required.clear(); flags.clear();
        assertEquals(List.of("test:a", "test:b"), form.requiredFlags());
        assertEquals("test:a", form.flag());
        assertEquals(Map.of("test:done", true), form.flagChanges());
        assertThrows(UnsupportedOperationException.class, () -> form.requiredFlags().clear());
        assertThrows(UnsupportedOperationException.class, () -> form.flagChanges().clear());
        assertThrows(IllegalArgumentException.class, () -> progression(List.of("test:a", "test:a"), Map.of()));
        List<String> tooMany = IntStream.range(0, 33).mapToObj(i -> "test:f" + i).toList();
        assertThrows(IllegalArgumentException.class, () -> progression(tooMany, Map.of()));
        Map<String, Boolean> tooManyChanges = new HashMap<>();
        tooMany.forEach(flag -> tooManyChanges.put(flag, true));
        assertThrows(IllegalArgumentException.class, () -> progression(List.of(), tooManyChanges));
    }

    private static EventEditorForm progression(List<String> required, Map<String, Boolean> flags) {
        return new EventEditorForm("", 0, "", "", "", "", "PARTICIPANTS", "", 0,
                "MANUAL", "", 0, 0, required, List.of(), true, flags, EventSource.empty());
    }
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
