package io.github.chronicle.core.scenario;

import io.github.chronicle.core.books.BookDefinition;
import io.github.chronicle.core.loot.LootEntry;
import io.github.chronicle.core.loot.LootType;
import io.github.chronicle.core.play.EventConfiguration;
import io.github.chronicle.core.play.EventDefinition;
import io.github.chronicle.core.play.EventRegionReference;
import io.github.chronicle.core.play.EventSource;
import io.github.chronicle.core.play.EventEncounterAction;
import io.github.chronicle.core.play.ObservationCatalog;
import io.github.chronicle.core.play.PlayState;
import io.github.chronicle.core.encounters.EncounterGroup;
import io.github.chronicle.core.encounters.EncounterMember;
import io.github.chronicle.core.encounters.EncounterTarget;
import io.github.chronicle.core.roles.PlayerRole;
import io.github.chronicle.core.roles.PlayerRoleConfig;
import io.github.chronicle.core.roles.StartingItem;
import com.google.gson.JsonParser;
import io.github.chronicle.core.spawn.SpawnBinding;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioContentTest {
    private static PlayerRole role() { return new PlayerRole("example:guide", "Guide", "Knows the way.", true, 2, new SpawnBinding("example:start", "example:camp", 3), new SpawnBinding("example:return", "example:camp", 4), List.of(new StartingItem("{\"id\":\"minecraft:torch\",\"count\":3}")), List.of("example:journal")); }
    private static EventConfiguration event() { EventDefinition definition = new EventDefinition("example:cue", 7, "Cue", "A cue.", "Listen.", "minecraft:block.note_block.bell", ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of(), Map.of("example:opened", true)); return new EventConfiguration(definition, PlayState.Mode.SYSTEM, Optional.empty(), 9324, 20, List.of("example:key"), true, new EventSource("example:book_opened", "example:journal", "open")); }
    private static ScenarioContent content() { BookDefinition book = new BookDefinition("example:journal", "Forge journal", "Forge", "A journal.", List.of("The first page."), BookDefinition.Rule.UNTIL_FOUND); LootType loot = new LootType("example:cache", "Cache", "A cache.", 1, 0, List.of(new LootEntry("minecraft:torch", 100, 1, 3, "{}"))); return ScenarioContentCapture.capture("example:quiet_below", Map.of("example:guide", role()), Map.of(book.id(), book), Map.of(loot.id(), loot), Map.of("example:cue", event())).content(); }
    @Test void roundTripsPortableCustomDefinitionsAndStripsLiveBindings() {
        PlayerRole live = role(); ScenarioContentCapture.Result captured = ScenarioContentCapture.capture("example:quiet_below", Map.of(live.id(), live), Map.of(), Map.of(), Map.of("example:cue", event()));
        assertEquals(List.of("example:guide:starting", "example:guide:respawn"), captured.strippedRoleBindings()); assertNotNull(live.starting()); assertNull(captured.content().roles().get(live.id()).starting());
        assertEquals(content(), ScenarioContentJson.decode(ScenarioContentJson.encode(content()))); assertFalse(captured.content().events().get("example:cue").enabled());
        String json = ScenarioContentJson.encode(content()); assertFalse(json.contains("9324")); assertFalse(json.contains("revision")); assertFalse(json.contains("snapshot")); assertFalse(json.contains("receipt")); assertFalse(json.contains("playerUUID")); assertTrue(json.contains("example:journal")); assertTrue(json.contains("example:key"));
    }
    @Test void rejectsUnknownDuplicateAndOversizeJson() {
        assertThrows(IllegalArgumentException.class, () -> ScenarioContentJson.decode("{\"schemaVersion\":1,\"schemaVersion\":1}"));
        assertThrows(IllegalArgumentException.class, () -> ScenarioContentJson.decode("{\"schemaVersion\":1,\"future\":true}"));
        assertThrows(IllegalArgumentException.class, () -> ScenarioContentJson.decode("x".repeat(ScenarioContentJson.MAX_BYTES + 1)));
    }
    @Test void preservesOrdinaryMapIdsButNeverAllowsRoleBindingsBackIntoContent() {
        EventDefinition definition = new EventDefinition("example:map_cue", 3, "Cue", "", "", "", ObservationCatalog.Audience.REGION, Optional.of(new EventRegionReference("courtyard", 9)), List.of(), Map.of("example:ready", true));
        EventTemplate template = EventTemplate.from(new EventConfiguration(definition, PlayState.Mode.ENTRY, Optional.of(new EventRegionReference("ship", 2)), 88, 0, List.of(), false, EventSource.empty()));
        assertEquals("courtyard", template.audienceReference()); assertEquals("ship", template.triggerReference());
        assertThrows(IllegalArgumentException.class, () -> new ScenarioContent("example:quiet_below", Map.of("example:guide", role()), Map.of(), Map.of(), Map.of()));
        var encoded = JsonParser.parseString(ScenarioContentJson.encode(ScenarioContentCapture.capture("example:quiet_below", Map.of("example:guide", role()), Map.of(), Map.of(), Map.of()).content())).getAsJsonObject(); encoded.addProperty("rolesYaml", PlayerRoleConfig.encode(Map.of("example:guide", role())));
        assertThrows(IllegalArgumentException.class, () -> ScenarioContentJson.decode(encoded.toString()));
        assertThrows(IllegalArgumentException.class, () -> new EventTemplate.RegionOutcome("courtyard", "area", "OTHER"));
        assertThrows(IllegalArgumentException.class, () -> new EventTemplate("example:empty", "Empty", "", "", "", ObservationCatalog.Audience.PARTICIPANTS, "", PlayState.Mode.MANUAL, "", 0, List.of(), Map.of(), List.of(), EventSource.empty()));
        assertThrows(IllegalArgumentException.class, () -> new EventTemplate("example:format", "Format", "", "Bad \u00a7code", "", ObservationCatalog.Audience.PARTICIPANTS, "", PlayState.Mode.MANUAL, "", 0, List.of(), Map.of(), List.of(), EventSource.empty()));
    }
    @Test void capturesPortableEncounterGroupsAndStripsRuntimePinsAndManagedSources() {
        EncounterGroup group = new EncounterGroup("example:spiders", "Spiders", "A group.", List.of(new EncounterMember("minecraft:spider", 2)));
        EventDefinition definition = new EventDefinition("example:spawn", 11, "Spawn", "", "", "", ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of(), Map.of(), List.of(new EventEncounterAction(group.id(), Optional.of(new EncounterTarget(EncounterTarget.Kind.REGION, "courtyard", 71)))));
        EventConfiguration configuration = new EventConfiguration(definition, PlayState.Mode.SYSTEM, Optional.empty(), 812, 0, List.of(), false, new EventSource("example:forge", "managed:123e4567-e89b-12d3-a456-426614174000", "use"));
        ScenarioContent captured = ScenarioContentCapture.capture("example:quiet_below", Map.of(), Map.of(), Map.of(), Map.of(definition.id(), configuration), Map.of(group.id(), group)).content();
        EventTemplate template = captured.events().get(definition.id()); assertEquals(List.of(new EventTemplate.EncounterOutcome(group.id(), "REGION", "courtyard")), template.encounterOutcomes()); assertEquals("", template.source().target());
        String encoded = ScenarioContentJson.encode(captured); assertEquals(captured, ScenarioContentJson.decode(encoded)); assertFalse(encoded.contains("71")); assertFalse(encoded.contains("managed:"));
        assertThrows(IllegalArgumentException.class, () -> new ScenarioContent("example:quiet_below", Map.of(), Map.of(), Map.of(), Map.of(definition.id(), template), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EventTemplate("example:bad", "Bad", "", "text", "", ObservationCatalog.Audience.PARTICIPANTS, "", PlayState.Mode.MANUAL, "", 0, List.of(), Map.of(), List.of(), new EventSource("example:forge", "managed:123e4567-e89b-12d3-a456-426614174000", "use")));
        var duplicate = JsonParser.parseString(encoded).getAsJsonObject(); duplicate.getAsJsonArray("encounterGroups").add(duplicate.getAsJsonArray("encounterGroups").get(0));
        assertThrows(IllegalArgumentException.class, () -> ScenarioContentJson.decode(duplicate.toString()));
        var unknown = JsonParser.parseString(encoded).getAsJsonObject(); unknown.addProperty("futureEncounterChange", true);
        assertThrows(IllegalArgumentException.class, () -> ScenarioContentJson.decode(unknown.toString()));
    }
    @Test void readsLegacySchemaOneAndWritesCurrentSchema() {
        ScenarioContent original = new ScenarioContent("example:legacy", Map.of(), Map.of(), Map.of(), Map.of());
        var root = JsonParser.parseString(ScenarioContentJson.encode(original)).getAsJsonObject(); root.addProperty("schemaVersion", 1); root.remove("encounterGroups");
        assertEquals(original, ScenarioContentJson.decode(root.toString())); assertTrue(ScenarioContentJson.encode(original).contains("\"schemaVersion\":" + ScenarioContentJson.SCHEMA_VERSION));
    }
}
