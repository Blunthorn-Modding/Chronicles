package io.github.chronicle.core.roles;

import io.github.chronicle.core.spawn.SpawnBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PlayerRoleWorldConfigTest {
    @TempDir Path directory;
    private static PlayerRole role(String name) { return new PlayerRole("example:guide", name, "A role.", true, 2, new SpawnBinding("chronicle_core:role_start", "camp", 3), new SpawnBinding("chronicle_core:role_respawn", "camp", 3), List.of(), List.of()); }
    private Path legacy() { return directory.resolve("config/chronicle/player-roles.yaml"); }
    private Path world(String name) { return directory.resolve(name); }
    @Test void bootstrapsOneWorldFromLegacyWithoutTouchingLegacyBytes() throws Exception {
        PlayerRole legacyRole = role("Legacy"); Path legacy = legacy(); Files.createDirectories(legacy.getParent()); String bytes = PlayerRoleConfig.encode(Map.of(legacyRole.id(), legacyRole)); Files.writeString(legacy, bytes);
        var loaded = PlayerRoleWorldConfig.load(world("one"), legacy, Map.of("example:guide", role("Default")));
        assertEquals(Map.of(legacyRole.id(), legacyRole), loaded.roles()); assertEquals(bytes, Files.readString(legacy)); assertFalse(Files.exists(legacy.resolveSibling("player-roles.yaml.initialized"))); assertTrue(Files.exists(PlayerRoleWorldConfig.worldFile(world("one"))));
    }
    @Test void existingWorldAlwaysWinsOverLaterLegacyChanges() throws Exception {
        Path legacy = legacy(); Files.createDirectories(legacy.getParent()); Files.writeString(legacy, PlayerRoleConfig.encode(Map.of("example:guide", role("Legacy"))));
        assertEquals("Legacy", PlayerRoleWorldConfig.load(world("one"), legacy, Map.of()).roles().get("example:guide").name()); Files.writeString(legacy, PlayerRoleConfig.encode(Map.of("example:guide", role("Changed legacy"))));
        assertEquals("Legacy", PlayerRoleWorldConfig.load(world("one"), legacy, Map.of()).roles().get("example:guide").name());
    }
    @Test void missingMarkedOrCorruptLegacyFailsWithoutWorldResurrection() throws Exception {
        Path legacy = legacy(); Files.createDirectories(legacy.getParent()); Files.writeString(legacy.resolveSibling("player-roles.yaml.initialized"), "old");
        assertThrows(IOException.class, () -> PlayerRoleWorldConfig.load(world("missing"), legacy, Map.of())); assertFalse(Files.exists(PlayerRoleWorldConfig.worldFile(world("missing"))));
        Files.delete(legacy.resolveSibling("player-roles.yaml.initialized")); Files.writeString(legacy, "not: [valid");
        assertThrows(IOException.class, () -> PlayerRoleWorldConfig.load(world("corrupt"), legacy, Map.of())); assertFalse(Files.exists(PlayerRoleWorldConfig.worldFile(world("corrupt"))));
        Files.delete(legacy); Files.createDirectory(legacy);
        assertThrows(IOException.class, () -> PlayerRoleWorldConfig.load(world("special"), legacy, Map.of())); assertFalse(Files.exists(PlayerRoleWorldConfig.worldFile(world("special"))));
    }
    @Test void deletedInitializedWorldFailsAndAbsentSourcesUseRegisteredDefaults() throws Exception {
        PlayerRole defaultRole = role("Default"); Path legacy = legacy();
        assertEquals("Default", PlayerRoleWorldConfig.load(world("defaults"), legacy, Map.of(defaultRole.id(), defaultRole)).roles().get(defaultRole.id()).name());
        Path file = PlayerRoleWorldConfig.worldFile(world("defaults")); Files.delete(file);
        assertThrows(IOException.class, () -> PlayerRoleWorldConfig.load(world("defaults"), legacy, Map.of(defaultRole.id(), defaultRole)));
    }
    @Test void copiedWorldRetainsDefinitionsAndBindingsWithoutLegacyConfig() throws Exception {
        PlayerRole source = role("Portable"); Path legacy = legacy(); var first = PlayerRoleWorldConfig.load(world("source"), legacy, Map.of(source.id(), source)); Path original = PlayerRoleWorldConfig.worldFile(world("source"));
        Path copied = PlayerRoleWorldConfig.worldFile(world("copy")); Files.createDirectories(copied.getParent()); Files.copy(original, copied); Files.copy(original.resolveSibling("player-roles.yaml.initialized"), copied.resolveSibling("player-roles.yaml.initialized"));
        assertEquals(first.roles(), PlayerRoleWorldConfig.load(world("copy"), legacy, Map.of()).roles()); assertEquals(source.starting(), PlayerRoleWorldConfig.load(world("copy"), legacy, Map.of()).roles().get(source.id()).starting());
    }
}
