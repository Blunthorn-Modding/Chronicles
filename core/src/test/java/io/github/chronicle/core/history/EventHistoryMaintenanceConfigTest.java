package io.github.chronicle.core.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EventHistoryMaintenanceConfigTest {
    @TempDir Path temporary;

    @Test void createsOnlyDefaultPolicyAndPreservesExistingPolicy() throws Exception {
        Path file = temporary.resolve("chronicle/event-history-maintenance.json");
        assertEquals(EventHistoryMaintenanceConfig.DEFAULT, EventHistoryMaintenanceConfig.loadOrCreate(file));
        var custom = new EventHistoryMaintenanceConfig(false, 12, 34);
        Files.writeString(file, EventHistoryMaintenanceConfig.encode(custom), StandardCharsets.UTF_8);
        assertEquals(custom, EventHistoryMaintenanceConfig.loadOrCreate(file));
    }

    @Test void strictSchemaRejectsUnknownDuplicateAndInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> EventHistoryMaintenanceConfig.decode("{\"schemaVersion\":1,\"enabled\":true,\"quietSeconds\":60,\"intervalSeconds\":300,\"extra\":false}"));
        assertThrows(IllegalArgumentException.class, () -> EventHistoryMaintenanceConfig.decode("{\"schemaVersion\":1,\"enabled\":true,\"quietSeconds\":60,\"quietSeconds\":61,\"intervalSeconds\":300}"));
        assertThrows(IllegalArgumentException.class, () -> EventHistoryMaintenanceConfig.decode("{\"schemaVersion\":1,\"enabled\":true,\"quietSeconds\":61,\"intervalSeconds\":60}"));
    }

    @Test void refusesOversizeAndLeavesInvalidExistingFileUntouched() throws Exception {
        Path file = temporary.resolve("event-history-maintenance.json");
        String invalid = "{\"schemaVersion\":1,\"enabled\":true,\"quietSeconds\":0,\"intervalSeconds\":1}";
        Files.writeString(file, invalid, StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> EventHistoryMaintenanceConfig.loadOrCreate(file));
        assertEquals(invalid, Files.readString(file, StandardCharsets.UTF_8));
        Files.writeString(file, "x".repeat((int) EventHistoryMaintenanceConfig.MAX_FILE_BYTES + 1), StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> EventHistoryMaintenanceConfig.loadOrCreate(file));
    }
}
