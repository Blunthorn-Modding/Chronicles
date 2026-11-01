package io.github.chronicle.core.roles;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;

/** Selects a map-owned role catalog, using the old server catalog only as first-world bootstrap input. */
public final class PlayerRoleWorldConfig {
    public record Loaded(PlayerRoleConfig config, Map<String, PlayerRole> roles) { }
    private PlayerRoleWorldConfig() { }
    public static Loaded load(Path worldRoot, Path legacyFile, Map<String, PlayerRole> registeredDefaults) throws IOException {
        if (worldRoot == null || legacyFile == null) throw new IllegalArgumentException("world root and legacy role file are required");
        Path world = worldRoot.toAbsolutePath().normalize().resolve("chronicle/player-roles.yaml"); Path worldMarker = marker(world);
        PlayerRoleConfig config = new PlayerRoleConfig(world);
        if (Files.exists(world, LinkOption.NOFOLLOW_LINKS) || Files.exists(worldMarker, LinkOption.NOFOLLOW_LINKS)) return new Loaded(config, config.loadOrCreate(registeredDefaults));
        Path legacy = legacyFile.toAbsolutePath().normalize(); Path legacyMarker = marker(legacy);
        Map<String, PlayerRole> seed;
        if (Files.exists(legacy, LinkOption.NOFOLLOW_LINKS)) seed = legacyRoles(legacy);
        else if (Files.exists(legacyMarker, LinkOption.NOFOLLOW_LINKS)) throw new IOException("legacy Player Role config is missing after initialization; restore it instead of recreating it: " + legacy);
        else seed = registeredDefaults;
        return new Loaded(config, config.loadOrCreate(seed));
    }
    public static Path worldFile(Path worldRoot) { if (worldRoot == null) throw new IllegalArgumentException("world root is required"); return worldRoot.toAbsolutePath().normalize().resolve("chronicle/player-roles.yaml"); }
    private static Map<String, PlayerRole> legacyRoles(Path legacy) throws IOException {
        if (Files.isSymbolicLink(legacy) || !Files.isRegularFile(legacy, LinkOption.NOFOLLOW_LINKS) || Files.size(legacy) > PlayerRoleConfig.MAX_FILE_BYTES) throw new IOException("legacy Player Role config must be a bounded regular non-link file: " + legacy);
        try { return PlayerRoleConfig.decode(strictUtf8(Files.readAllBytes(legacy))); }
        catch (RuntimeException | CharacterCodingException failure) { throw new IOException("legacy Player Role config is invalid and was not replaced: " + legacy, failure); }
    }
    private static Path marker(Path file) { return file.resolveSibling(file.getFileName() + ".initialized"); }
    private static String strictUtf8(byte[] bytes) throws CharacterCodingException { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
}
