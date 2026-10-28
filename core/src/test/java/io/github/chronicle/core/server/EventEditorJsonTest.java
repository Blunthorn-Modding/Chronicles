package io.github.chronicle.core.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.menu.EventEditorForm;
import io.github.chronicle.core.play.EventSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventEditorJsonTest {
    @Test void readsExactLegacyShapeAndWritesOnlyCurrentShape() {
        EventEditorForm form = new EventEditorForm("test:event", 1, "Event", "", "", "", "PARTICIPANTS", "", 0,
                "MANUAL", "", 0, 0, "test:ready", List.of(), true);
        JsonObject legacy = BookConfig.strictObject(EventEditorJson.encode(form));
        legacy.remove("requiredFlags"); legacy.remove("flagChanges"); legacy.remove("source");
        legacy.addProperty("flag", "test:ready");
        assertEquals(form, EventEditorJson.decode(legacy.toString()));
        JsonObject mixed = legacy.deepCopy(); mixed.add("flagChanges", new JsonObject());
        assertThrows(IllegalArgumentException.class, () -> EventEditorJson.decode(mixed.toString()));
        EventEditorForm current = new EventEditorForm(form.id(), form.revision(), form.name(), "", "", "", "PARTICIPANTS", "", 0,
                "SYSTEM", "", 0, 10, List.of("test:a", "test:b"), List.of(), true,
                Map.of("test:done", true, "test:reset", false), new EventSource("chronicle:book_opened", "test:book", "read"));
        assertEquals(current, EventEditorJson.decode(EventEditorJson.encode(current)));
        JsonObject encoded = BookConfig.strictObject(EventEditorJson.encode(current));
        encoded.getAsJsonObject("flagChanges").addProperty("test:done", "true");
        assertThrows(IllegalArgumentException.class, () -> EventEditorJson.decode(encoded.toString()));
    }
    @Test
    void roundTripsCompleteAndIncompleteDraftsWithoutTrustingPresentationFields() {
        EventEditorForm complete = new EventEditorForm("quiet_below:sinkhole", 7, "Sinkhole", "Organizer note",
                "The earth opens.\nRun!", "minecraft:ambient.cave", "REGION", "village", 4,
                "ENTRY", "mine", 3, 0, "quiet_below:ready",
                List.of(new EventEditorForm.Change("sinkhole", "Sinkhole - END", "END", 9,
                        "a".repeat(64), "minecraft:overworld", true, "")), false);
        assertEquals(complete, EventEditorJson.decode(EventEditorJson.encode(complete)));

        EventEditorForm incomplete = CoreEventMenu.emptyForm();
        assertEquals(incomplete, EventEditorJson.decode(EventEditorJson.encode(incomplete)));
    }

    @Test
    void rejectsUnknownMissingDuplicateAndWronglyTypedMembers() {
        String encoded = EventEditorJson.encode(CoreEventMenu.emptyForm());
        JsonObject unknown = BookConfig.strictObject(encoded);
        unknown.addProperty("unknown", true);
        assertThrows(IllegalArgumentException.class, () -> EventEditorJson.decode(unknown.toString()));

        JsonObject missing = BookConfig.strictObject(encoded);
        missing.remove("name");
        assertThrows(IllegalArgumentException.class, () -> EventEditorJson.decode(missing.toString()));

        String duplicate = encoded.substring(0, encoded.length() - 1) + ",\"id\":\"quiet_below:other\"}";
        assertThrows(IllegalArgumentException.class, () -> EventEditorJson.decode(duplicate));

        JsonObject wrongType = BookConfig.strictObject(encoded);
        wrongType.addProperty("creating", "true");
        assertThrows(IllegalArgumentException.class, () -> EventEditorJson.decode(wrongType.toString()));

        JsonObject fractional = BookConfig.strictObject(encoded);
        fractional.addProperty("seconds", 1.5);
        assertThrows(IllegalArgumentException.class, () -> EventEditorJson.decode(fractional.toString()));
    }

    @Test
    void enforcesChangeShapeCountAndAggregateEncodedBudget() {
        EventEditorForm.Change change = new EventEditorForm.Change("region", "Region - START", "START", 2,
                "b".repeat(64), "minecraft:overworld", true, "");
        ArrayList<EventEditorForm.Change> tooMany = new ArrayList<>();
        for (int index = 0; index <= EventEditorForm.MAX_CHANGES; index++) tooMany.add(change);
        assertThrows(IllegalArgumentException.class, () -> new EventEditorForm("quiet_below:test", 1, "Test", "", "", "",
                "PARTICIPANTS", "", 0, "MANUAL", "", 0, 0, "", tooMany, false));

        JsonObject malformed = BookConfig.strictObject(EventEditorJson.encode(CoreEventMenu.emptyForm()));
        JsonObject badChange = new JsonObject();
        badChange.addProperty("regionId", "region");
        JsonArray changes = new JsonArray();
        changes.add(badChange);
        malformed.add("changes", changes);
        assertThrows(IllegalArgumentException.class, () -> EventEditorJson.decode(malformed.toString()));

        assertThrows(IllegalArgumentException.class, () -> EventEditorJson.decode("{\"padding\":\""
                + "x".repeat(EventEditorJson.MAX_ENCODED_BYTES) + "\"}"));
    }
}
