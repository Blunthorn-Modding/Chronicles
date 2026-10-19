package io.github.chronicle.core.loot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LootCatalogTest {
    private static LootType type(String id) {
        return new LootType(id, "Supplies", "A small useful cache.", 2, 15, List.of(new LootEntry("minecraft:bread", 50, 1, 3, null)));
    }

    @Test void validatesBoundsAndExposesAnImmutableFrozenCatalog() {
        assertEquals("{}", type("example:camp").entries().getFirst().componentsJson());
        assertEquals(1, new LootType("example:short", "Short", "A profile.", 0, .0001, List.of()).refillTicks());
        assertEquals(1800, new LootType("example:hour", "Hour", "A profile.", 0, 1.5, List.of()).refillTicks());
        assertEquals(0, new LootType("example:never", "Never", "A profile.", 0, 0, List.of()).refillTicks());
        assertThrows(IllegalArgumentException.class, () -> new LootEntry("bread", 1, 1, 1, "{}"));
        assertThrows(IllegalArgumentException.class, () -> new LootEntry("minecraft:bread", 101, 1, 1, "{}"));
        assertThrows(IllegalArgumentException.class, () -> new LootEntry("minecraft:bread", 1, 2, 1, "{}"));
        assertThrows(IllegalArgumentException.class, () -> new LootEntry("minecraft:bread", 1, 1, 1, "[]"));
        assertThrows(IllegalArgumentException.class, () -> new LootEntry("minecraft:bread", 1, 1, 1, "{\"x\":1,\"x\":2}"));
        assertThrows(IllegalArgumentException.class, () -> new LootType("example:many", "Many", "A profile.", 0, 0, java.util.Collections.nCopies(28, type("example:entry").entries().getFirst())));

        LootCatalog catalog = new LootCatalog();
        LootType value = type("example:camp");
        catalog.register(value);
        assertEquals(value, catalog.types().get(value.id()));
        assertThrows(UnsupportedOperationException.class, () -> catalog.types().clear());
        assertThrows(IllegalArgumentException.class, () -> catalog.register(value));
        catalog.freeze();
        assertThrows(IllegalStateException.class, () -> catalog.register(type("example:after")));
    }

    @Test void boundsTheNumberOfRegisteredTypes() {
        LootCatalog catalog = new LootCatalog();
        for (int i = 0; i < LootCatalog.MAX_TYPES; i++) catalog.register(type("example:type" + i));
        assertEquals(LootCatalog.MAX_TYPES, catalog.types().size());
        assertThrows(IllegalArgumentException.class, () -> catalog.register(type("example:one_more")));
    }
}
