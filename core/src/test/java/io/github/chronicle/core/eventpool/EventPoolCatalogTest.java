package io.github.chronicle.core.eventpool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventPoolCatalogTest {
    @TempDir Path root;
    private static EventPoolDefinition pool(String label) { return new EventPoolDefinition("test:pool", label, List.of(new EventPoolDefinition.Entry("test:event", 2))); }
    @Test void revisionAndRestart() throws Exception {
        Path file = root.resolve("catalog.json"); var catalog = new EventPoolCatalog(new EventPoolCatalogStore(file), List.of(pool("Initial")));
        catalog.put(0, pool("Changed")); assertEquals(1, catalog.revision());
        assertThrows(IllegalStateException.class, () -> catalog.delete(0, "test:pool"));
        assertEquals("Changed", new EventPoolCatalog(new EventPoolCatalogStore(file), List.of(pool("Default"))).definitions().get("test:pool").label());
        catalog.delete(1, "test:pool"); assertTrue(catalog.definitions().isEmpty());
    }
    @Test void externalChangesPreserveBothFileAndMemory() throws Exception {
        Path file = root.resolve("catalog.json"); var catalog = new EventPoolCatalog(new EventPoolCatalogStore(file), List.of(pool("Initial")));
        byte[] changed = EventPoolCatalogStore.encode(Map.of("test:pool", pool("External"))); Files.write(file, changed);
        assertThrows(IOException.class, () -> catalog.put(0, pool("Other")));
        assertArrayEquals(changed, Files.readAllBytes(file)); assertEquals("Initial", catalog.definitions().get("test:pool").label()); assertEquals(0, catalog.revision());
    }
    @Test void rejectsMalformedAndAmbiguousData() throws Exception {
        for (String text : List.of("{\"schema\":1,\"pools\":[],\"extra\":0}", "{\"schema\":1,\"schema\":1,\"pools\":[]}", "{\"schema\":1,\"pools\":[{\"id\":\"test:p\",\"label\":\"P\",\"entries\":[{\"eventId\":\"test:e\",\"weight\":0}]}]}"))
            assertThrows(IOException.class, () -> EventPoolCatalogStore.decode(text.getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> EventPoolCatalogStore.decode(new byte[]{(byte)0xc3, (byte)0x28}));
        assertThrows(IllegalArgumentException.class, () -> new EventPoolDefinition("test:p", "P", List.of(new EventPoolDefinition.Entry("test:e", 1), new EventPoolDefinition.Entry("test:e", 2))));
    }
}
