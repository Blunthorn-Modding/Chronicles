package io.github.chronicle.core.enchanting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnchantmentCapConfigTest {
    @TempDir Path directory;

    @Test void createsRoundTripsAndLoadsConfiguredPolicy() throws Exception {
        Path file = directory.resolve("config/enchantment-caps.json");
        EnchantmentCaps defaults = EnchantmentCaps.DEFAULT;
        EnchantmentCapConfig config = new EnchantmentCapConfig(file);
        assertEquals(defaults, config.loadOrCreate(defaults));
        assertTrue(Files.readString(file).contains("\n  \"overrides\": {}"));

        String source = "{\"schemaVersion\":1,\"enabled\":true,\"defaultMaxLevel\":2,\"overrides\":{\"minecraft:sharpness\":1}}";
        Files.writeString(file, source);
        EnchantmentCaps expected = new EnchantmentCaps(true, 2, Map.of("minecraft:sharpness", 1));
        assertEquals(expected, config.loadOrCreate(defaults));
        assertEquals(expected, EnchantmentCapConfig.decode(EnchantmentCapConfig.encode(expected)));
    }

    @Test void rejectsStrictCorruptionWithoutReplacingIt() throws Exception {
        Path file = directory.resolve("enchantment-caps.json");
        String[] invalid = {
                "{\"schemaVersion\":1,\"enabled\":true,\"defaultMaxLevel\":1,\"overrides\":{},\"unexpected\":true}",
                "{\"schemaVersion\":1,\"enabled\":true,\"defaultMaxLevel\":1,\"overrides\":{},\"enabled\":true}",
                "{\"schemaVersion\":1,\"enabled\":true,\"defaultMaxLevel\":1.5,\"overrides\":{}}",
                "{\"schemaVersion\":1,\"enabled\":true,\"defaultMaxLevel\":1,\"overrides\":{\"minecraft:sharpness\":{\"level\":1}}}",
                "{\"schemaVersion\":2,\"enabled\":true,\"defaultMaxLevel\":1,\"overrides\":{}}"
        };
        for (String source : invalid) {
            Files.writeString(file, source);
            assertThrows(IOException.class, () -> new EnchantmentCapConfig(file).loadOrCreate(EnchantmentCaps.DEFAULT));
            assertEquals(source, Files.readString(file));
        }
    }

    @Test void saveUsesExactObservedBytesAndPublishesValidBackup() throws Exception {
        Path file = directory.resolve("enchantment-caps.json");
        EnchantmentCapConfig config = new EnchantmentCapConfig(file);
        EnchantmentCaps initial = config.loadOrCreate(EnchantmentCaps.DEFAULT);
        String before = Files.readString(file);
        EnchantmentCaps replacement = new EnchantmentCaps(true, 2, Map.of("minecraft:sharpness", 1));
        assertEquals(replacement, config.save(replacement, initial));
        assertEquals(before, Files.readString(file.resolveSibling("enchantment-caps.json.bak")));
        assertEquals(initial, new EnchantmentCapConfig(file.resolveSibling("enchantment-caps.json.bak")).loadOrCreate(EnchantmentCaps.DEFAULT));

        String saved = Files.readString(file);
        Files.writeString(file, saved + "\n");
        IOException failure = assertThrows(IOException.class, () -> config.save(EnchantmentCaps.DEFAULT, replacement));
        assertTrue(failure.getMessage().contains("changed outside Chronicle"));
        assertEquals(saved + "\n", Files.readString(file));
        assertThrows(IllegalStateException.class, () -> config.save(EnchantmentCaps.DEFAULT, initial));
    }

    @Test void rejectsOversizedExistingFileWithoutChangingIt() throws Exception {
        Path file = directory.resolve("enchantment-caps.json");
        String oversized = " ".repeat((int) EnchantmentCapConfig.MAX_FILE_BYTES + 1);
        Files.writeString(file, oversized);
        assertThrows(IOException.class, () -> new EnchantmentCapConfig(file).loadOrCreate(EnchantmentCaps.DEFAULT));
        assertEquals(oversized.length(), Files.size(file));
    }

    @Test void preservesSymlinkInsteadOfFollowingIt() throws Exception {
        Path target = directory.resolve("target.json");
        Path link = directory.resolve("enchantment-caps.json");
        Files.writeString(target, "do not follow");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | IOException exception) {
            Assumptions.assumeTrue(false, "symbolic links are unavailable in this test environment");
        }
        assertThrows(IOException.class, () -> new EnchantmentCapConfig(link).loadOrCreate(EnchantmentCaps.DEFAULT));
        assertEquals("do not follow", Files.readString(target));
        assertTrue(Files.isSymbolicLink(link));
    }
}
