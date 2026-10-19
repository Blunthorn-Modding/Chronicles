package io.github.chronicle.core.play;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservationCatalogTest {
    @Test
    void definitionsAreOrderedFrozenAndFingerprintEveryField() {
        ObservationCatalog catalog = new ObservationCatalog();
        var second = definition("quiet_below:second", "Second", "A sound passes through the stone.", "minecraft:ambient.cave", ObservationCatalog.Audience.PARTICIPANTS);
        var first = definition("quiet_below:first", "First", "The stone carries a vibration.", "", ObservationCatalog.Audience.REGION);
        catalog.register(second);
        catalog.register(first);
        assertEquals(java.util.List.of(first, second), catalog.definitions());
        assertEquals(first, catalog.get(first.id()).orElseThrow());
        assertNotEquals(first.fingerprint(), new ObservationCatalog.Definition(first.id(), first.label(), first.text() + " ", first.sound(), first.audience()).fingerprint());
        catalog.freeze();
        assertThrows(IllegalStateException.class, () -> catalog.register(definition("quiet_below:third", "Third", "Text", "", ObservationCatalog.Audience.PARTICIPANTS)));
    }

    @Test
    void rejectsUnboundedOrNonPlainDefinitionsAndDuplicateIds() {
        assertThrows(IllegalArgumentException.class, () -> definition("plain", "Label", "Text", "", ObservationCatalog.Audience.PARTICIPANTS));
        assertThrows(IllegalArgumentException.class, () -> definition("quiet_below:test", "", "Text", "", ObservationCatalog.Audience.PARTICIPANTS));
        assertThrows(IllegalArgumentException.class, () -> definition("quiet_below:test", "Label", "line\nbreak", "", ObservationCatalog.Audience.PARTICIPANTS));
        assertThrows(IllegalArgumentException.class, () -> definition("quiet_below:test", "Label", "Text", "not-a-sound", ObservationCatalog.Audience.PARTICIPANTS));
        ObservationCatalog catalog = new ObservationCatalog();
        var definition = definition("quiet_below:test", "Label", "Text", "", ObservationCatalog.Audience.PARTICIPANTS);
        catalog.register(definition);
        assertThrows(IllegalArgumentException.class, () -> catalog.register(definition));
    }

    private static ObservationCatalog.Definition definition(String id, String label, String text, String sound, ObservationCatalog.Audience audience) {
        return new ObservationCatalog.Definition(id, label, text, sound, audience);
    }
}
