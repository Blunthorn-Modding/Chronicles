package io.github.chronicle.core.lore;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NumberRangeTest {
    @Test void inclusiveEndpointsAndFullIntegerDomain() {
        assertThrows(IllegalArgumentException.class, () -> new NumberRange(2, 1));
        var range = new NumberRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
        Random first = new Random() { @Override public long nextLong(long bound) { assertEquals(4294967296L, bound); return 0; } };
        Random last = new Random() { @Override public long nextLong(long bound) { return bound - 1; } };
        assertEquals(Integer.MIN_VALUE, range.draw(first));
        assertEquals(Integer.MAX_VALUE, range.draw(last));
        assertEquals(-7, new NumberRange(-7, -7).draw(last));
    }
}
