package io.github.chronicle.quietbelow.content;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.scenario.ScenarioContent;
import io.github.chronicle.core.scenario.ScenarioContentJson;
import io.github.chronicle.core.scenario.ScenarioEventTemplates;
import io.github.chronicle.core.server.Encounters;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Optional packaged defaults. Their presence replaces only reusable campaign content, never map setup. */
public final class QuietBelowScenarioDefaults {
    public static final String SCENARIO_ID = "quiet_below:scenario";
    private static final String RESOURCE = "/data/quiet_below/chronicle/scenario-content.json";
    private QuietBelowScenarioDefaults() { }
    public static Optional<ScenarioContent> loadOptional() {
        try (InputStream stream = QuietBelowScenarioDefaults.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) return Optional.empty();
            byte[] bytes = stream.readNBytes(ScenarioContentJson.MAX_BYTES + 1);
            if (bytes.length > ScenarioContentJson.MAX_BYTES) throw new IllegalStateException("Quiet Below scenario content exceeds 16 MiB");
            ScenarioContent content = ScenarioContentJson.decode(strictUtf8(bytes));
            if (!SCENARIO_ID.equals(content.scenarioId())) throw new IllegalStateException("Quiet Below scenario content has unexpected scenario ID");
            return Optional.of(content);
        } catch (IOException exception) { throw new IllegalStateException("Unable to load packaged Quiet Below scenario content", exception); }
    }
    /** Startup-only registration; it never enables Events or applies content to a map/world. */
    public static void register(ScenarioContent content) {
        if (content == null || !SCENARIO_ID.equals(content.scenarioId())) throw new IllegalArgumentException("Quiet Below scenario content identity is required");
        if (content.roles().values().stream().anyMatch(role -> role.starting() != null || role.respawn() != null)) throw new IllegalArgumentException("Quiet Below scenario roles must not contain map bindings");
        if (content.roles().keySet().stream().anyMatch(ChronicleCore.playerRoles().roles()::containsKey)
                || content.books().keySet().stream().anyMatch(ChronicleCore.books().books()::containsKey)
                || content.lootTables().keySet().stream().anyMatch(ChronicleCore.chestTypes().types()::containsKey)
                || content.events().keySet().stream().anyMatch(ScenarioEventTemplates.templates()::containsKey)) throw new IllegalArgumentException("Quiet Below scenario content duplicates registered defaults");
        content.roles().values().forEach(ChronicleCore.playerRoles()::register);
        content.books().values().forEach(ChronicleCore.books()::register);
        content.lootTables().values().forEach(ChronicleCore.chestTypes()::register);
        Encounters.registerDefaults(content.encounterGroups().values());
        content.events().values().forEach(ScenarioEventTemplates::register);
    }
    private static String strictUtf8(byte[] bytes) throws CharacterCodingException { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
}
