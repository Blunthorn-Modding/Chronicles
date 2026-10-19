package io.github.chronicle.core.authoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AuthoringCatalogTest {
    @TempDir Path directory;
    @Test void campaignRolesAreTypedUniqueAndFrozenBeforeRuntime() {
        var catalog = new AuthoringCatalog();
        var role = new AuthoringCatalog.Role("example:source", "Source city", "Select the source region.", AuthoringCatalog.Kind.REGION);
        catalog.register(role);
        assertEquals(role, catalog.get("example:source").orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> catalog.register(role));
        assertThrows(IllegalArgumentException.class, () -> catalog.validate(new AuthoringSite.Location("example:source", "minecraft:overworld", 1, 0, 64, 0, 0, 0)));
        catalog.validate(new AuthoringSite.Region("example:source", "minecraft:overworld", 1, new BlockPosition(0,0,0), new BlockPosition(1,1,1)));
        catalog.freeze();
        assertThrows(IllegalStateException.class, () -> catalog.register(new AuthoringCatalog.Role("other:source", "Other", "Other region.", AuthoringCatalog.Kind.REGION)));
    }
    @Test void namespacedAndLegacySitesRoundTripWithoutRenaming() throws Exception {
        var file = directory.resolve("authoring.json");
        var service = new AuthoringService(new AuthoringStore(file));
        service.put(new AuthoringSite.Location("old_arrival", "minecraft:overworld", 1, .5, 64, .5, 90, 0), false);
        service.put(new AuthoringSite.Location("example:arrival", "minecraft:overworld", 1, .5, 64, .5, 0, 0), false);
        assertEquals(service.state(), new AuthoringStore(file).load());
        assertEquals(2, service.state().sites().size());
        assertThrows(IllegalArgumentException.class, () -> new AuthoringCatalog.Role("unqualified", "Missing owner", "A region.", AuthoringCatalog.Kind.REGION));
        assertThrows(IllegalArgumentException.class, () -> new AuthoringCatalog.Role("example:bad", "Line\nbreak", "A region.", AuthoringCatalog.Kind.REGION));
    }
}
