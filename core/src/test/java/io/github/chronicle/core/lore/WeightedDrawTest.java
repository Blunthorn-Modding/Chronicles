package io.github.chronicle.core.lore;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WeightedDrawTest {
    @Test void seedIsStableAcrossInputOrderAndDrawHasNoReplacement() {
        var a = new WeightedDraw.Entry<>("a", 1, "A");
        var b = new WeightedDraw.Entry<>("b", 3, "B");
        var c = new WeightedDraw.Entry<>("c", 5, "C");
        var draw = WeightedDraw.draw(List.of(c, a, b), 3, new Random(42));
        assertEquals(draw, WeightedDraw.draw(List.of(b, c, a), 3, new Random(42)));
        assertEquals(3, draw.stream().map(WeightedDraw.Entry::id).distinct().count());
    }
    @Test void respectsWeightsAndRejectsInvalidDraws() {
        var a = new WeightedDraw.Entry<>("a", 1, "A");
        var b = new WeightedDraw.Entry<>("b", 1_000_000, "B");
        assertEquals("b", WeightedDraw.draw(List.of(a, b), 1, new Random(42)).get(0).id());
        assertEquals(List.of(), WeightedDraw.draw(List.of(), 0, new Random(0)));
        assertThrows(IllegalArgumentException.class, () -> WeightedDraw.draw(List.of(a, a), 1, new Random(0)));
        assertThrows(IllegalArgumentException.class, () -> WeightedDraw.draw(List.of(a), 2, new Random(0)));
        assertThrows(IllegalArgumentException.class, () -> new WeightedDraw.Entry<>("a", 0, "A"));
        assertThrows(IllegalArgumentException.class, () -> new WeightedDraw.Entry<>("a", 1_000_001, "A"));
        assertThrows(NullPointerException.class, () -> WeightedDraw.draw(List.of(a), 0, null));
    }
}
