package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.lastcouncil.banquet.BanquetConfig;
import io.github.chronicle.lastcouncil.banquet.BanquetState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BanquetSettingsTest {
    @TempDir Path directory;

    @Test void defaultsRoundTripAndTimingBounds() throws Exception {
        Path path = directory.resolve("settings.json");
        var settings = new BanquetSettings(path, BanquetConfig.defaults());
        assertEquals(new BanquetConfig(false, 2, 8), settings.config());
        assertEquals(new BanquetState.Timing(2400, 9600), settings.config().timing());
        var next = new BanquetConfig(true, 1, 60);
        settings.save(next);
        assertEquals(next, new BanquetSettings(path, BanquetConfig.defaults()).config());
        assertEquals(new BanquetState.Timing(1200, 72000), next.timing());
        assertThrows(IllegalArgumentException.class, () -> new BanquetConfig(false, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new BanquetConfig(false, 1, 61));
    }

    @Test void invalidFilesRemainUnchanged() throws Exception {
        Path path = directory.resolve("settings.json");
        for (String text : new String[] {
                "{\"schema\":1,\"enabled\":false,\"onsetMinutes\":2,\"durationMinutes\":8,\"extra\":0}",
                "{\"schema\":1,\"enabled\":false,\"enabled\":true,\"onsetMinutes\":2,\"durationMinutes\":8}",
                "{\"schema\":1,\"enabled\":\"false\",\"onsetMinutes\":2,\"durationMinutes\":8}",
                "{\"schema\":1,\"enabled\":false,\"onsetMinutes\":2.0,\"durationMinutes\":8}",
                "{\"schema\":1,\"enabled\":false,\"onsetMinutes\":2e0,\"durationMinutes\":8}",
                "{\"schema\":1,\"enabled\":false,\"onsetMinutes\":\"2\",\"durationMinutes\":8}",
                "{\"schema\":1,\"enabled\":false,\"onsetMinutes\":0,\"durationMinutes\":8}" }) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            Files.write(path, bytes);
            assertThrows(IOException.class, () -> new BanquetSettings(path, BanquetConfig.defaults()));
            assertArrayEquals(bytes, Files.readAllBytes(path));
        }
        byte[] invalidUtf8 = {(byte) 0xc3, (byte) 0x28};
        Files.write(path, invalidUtf8);
        assertThrows(IOException.class, () -> new BanquetSettings(path, BanquetConfig.defaults()));
        assertArrayEquals(invalidUtf8, Files.readAllBytes(path));
    }

    @Test void staleSavePreservesFileAndMemory() throws Exception {
        Path path = directory.resolve("settings.json");
        var settings = new BanquetSettings(path, BanquetConfig.defaults());
        byte[] external = BanquetSettings.encode(new BanquetConfig(true, 3, 4));
        Files.write(path, external);
        assertThrows(IOException.class, settings::verify);
        assertThrows(IOException.class, () -> settings.save(new BanquetConfig(true, 5, 6)));
        assertArrayEquals(external, Files.readAllBytes(path));
        assertEquals(BanquetConfig.defaults(), settings.config());
    }
}
