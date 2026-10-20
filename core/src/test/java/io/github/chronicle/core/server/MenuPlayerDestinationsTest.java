package io.github.chronicle.core.server;

import io.github.chronicle.core.spawn.SpawnBinding;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuPlayerDestinationsTest {
    @Test void privatePolicyPurposesStayBoundedAndSeparatePlayersKindsAndPointRevisions() {
        UUID player = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        String starting = MenuPlayerDestinations.privatePurpose(player, "quiet_below:arrival", Long.MAX_VALUE, true);
        String death = MenuPlayerDestinations.privatePurpose(player, "quiet_below:arrival", Long.MAX_VALUE, false);
        String otherPlayer = MenuPlayerDestinations.privatePurpose(UUID.fromString("abcdefab-cdef-cdef-cdef-abcdefabcdef"), "quiet_below:arrival", Long.MAX_VALUE, true);
        String priorPoint = MenuPlayerDestinations.privatePurpose(player, "quiet_below:arrival", 7, true);
        String importedPoint = MenuPlayerDestinations.privatePurpose(player, "quiet_below:alternate_arrival", Long.MAX_VALUE, true);
        assertTrue(SpawnBinding.ID.matcher(starting).matches());
        assertTrue(SpawnBinding.ID.matcher(death).matches());
        assertNotEquals(starting, death);
        assertNotEquals(starting, otherPlayer);
        assertNotEquals(starting, priorPoint);
        assertNotEquals(starting, importedPoint);
    }
}
