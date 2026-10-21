package io.github.chronicle.core.enchanting;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnchantmentCapsTest {
    @Test void defaultsAreDisabledAndPoliciesOnlyReduceLevels() {
        assertEquals(new EnchantmentCaps(false, 255, Map.of()), EnchantmentCaps.DEFAULT);
        assertEquals(7, EnchantmentCaps.DEFAULT.cap("minecraft:sharpness", 7));
        assertEquals(99, EnchantmentCaps.DEFAULT.storedLevel("minecraft:sharpness", 99));

        EnchantmentCaps caps = new EnchantmentCaps(true, 3, Map.of("minecraft:sharpness", 1));
        assertEquals(1, caps.cap("minecraft:sharpness", 5));
        assertEquals(3, caps.cap("minecraft:efficiency", 5));
        assertEquals(1, caps.cap("minecraft:sharpness", 2));
        assertEquals(2, caps.cap("minecraft:efficiency", 2));
        assertEquals(1, caps.storedLevel("minecraft:sharpness", 8));
        assertEquals(3, caps.storedLevel("minecraft:efficiency", 8));
        assertEquals(0, caps.storedLevel("minecraft:sharpness", 0));
    }

    @Test void validatesIdsBoundsAndDefensivelyCopiesOverrides() {
        Map<String, Integer> mutable = new HashMap<>();
        mutable.put("minecraft:sharpness", 2);
        EnchantmentCaps caps = new EnchantmentCaps(true, 3, mutable);
        mutable.put("minecraft:efficiency", 1);
        assertEquals(Map.of("minecraft:sharpness", 2), caps.overrides());
        assertThrows(UnsupportedOperationException.class, () -> caps.overrides().clear());
        assertThrows(IllegalArgumentException.class, () -> new EnchantmentCaps(true, 0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EnchantmentCaps(true, 256, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EnchantmentCaps(true, 1, Map.of("Minecraft:Sharpness", 1)));
        assertThrows(IllegalArgumentException.class, () -> new EnchantmentCaps(true, 1, Map.of("minecraft:sharpness", 256)));
        Map<String, Integer> tooMany = new HashMap<>();
        for (int index = 0; index <= EnchantmentCaps.MAX_OVERRIDES; index++) tooMany.put("test:value" + index, 1);
        assertThrows(IllegalArgumentException.class, () -> new EnchantmentCaps(true, 1, tooMany));
    }
}
