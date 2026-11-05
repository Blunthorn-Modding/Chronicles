package io.github.chronicle.core.lore;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TextTemplateTest {
    @Test void expandsOnlyOnceAndTreatsValuesLiterally() {
        assertEquals("Vale: ${other} $1 \\path\n", TextTemplate.render("${house.title}: ${fact.capacity}\n", Map.of("house.title", "Vale", "fact.capacity", "${other} $1 \\path")));
        assertEquals("", TextTemplate.render("", Map.of()));
    }
    @Test void rejectsMissingAndMalformedPlaceholders() {
        assertThrows(IllegalArgumentException.class, () -> TextTemplate.render("${missing}", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> TextTemplate.render("${house.}", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> TextTemplate.render("${house", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> TextTemplate.render("${${x}}", Map.of()));
    }
    @Test void enforcesCodePointAndBindingBounds() {
        String emoji = "😀";
        assertEquals(emoji.repeat(16_384), TextTemplate.render("${x}", Map.of("x", emoji.repeat(16_384))));
        assertThrows(IllegalArgumentException.class, () -> TextTemplate.render("${x}x", Map.of("x", emoji.repeat(16_384))));
        assertThrows(IllegalArgumentException.class, () -> TextTemplate.render("x".repeat(16_385), Map.of()));
        var bindings = new HashMap<String, String>();
        for (int i = 0; i < 129; i++) bindings.put("k" + i, "x");
        assertThrows(IllegalArgumentException.class, () -> TextTemplate.render("", bindings));
        assertThrows(IllegalArgumentException.class, () -> TextTemplate.render("bad\u0000", Map.of()));
    }
}
