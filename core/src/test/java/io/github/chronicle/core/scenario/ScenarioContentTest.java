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
        var root = JsonParser.parseString(ScenarioContentJson.encode(original)).getAsJsonObject(); root.addProperty("schemaVersion", 1); root.remove("encounterGroups"); root.remove("flags"); root.remove("eventPools"); root.remove("extensions");
        assertEquals(original, ScenarioContentJson.decode(root.toString())); assertTrue(ScenarioContentJson.encode(original).contains("\"schemaVersion\":" + ScenarioContentJson.SCHEMA_VERSION));
    }
    @Test void flagDefaultsRoundTripAndLegacySchemasRemainReadable() {
        var content = new ScenarioContent("example:scenario", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of("example:ready", true));
        var root = JsonParser.parseString(ScenarioContentJson.encode(content)).getAsJsonObject();
        assertEquals(content, ScenarioContentJson.decode(root.toString()));
        for (int version = 1; version <= 3; version++) {
            var legacy = root.deepCopy(); legacy.addProperty("schemaVersion", version); legacy.remove("flags"); legacy.remove("eventPools"); legacy.remove("extensions"); if (version == 1) legacy.remove("encounterGroups");
            assertTrue(ScenarioContentJson.decode(legacy.toString()).flags().isEmpty());
        }
        assertThrows(IllegalArgumentException.class, () -> new ScenarioContent("example:scenario", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of("invalid", false)));
        var tooMany = new java.util.HashMap<String, Boolean>(); for (int i = 0; i < 257; i++) tooMany.put("example:flag_" + i, false);
        assertThrows(IllegalArgumentException.class, () -> new ScenarioContent("example:scenario", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), tooMany));
        var nullValue = new java.util.HashMap<String, Boolean>(); nullValue.put("example:flag", null);
        assertThrows(IllegalArgumentException.class, () -> new ScenarioContent("example:scenario", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), nullValue));
        nullValue.clear(); nullValue.put(null, false);
        assertThrows(IllegalArgumentException.class, () -> new ScenarioContent("example:scenario", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), nullValue));
        assertThrows(IllegalArgumentException.class, () -> new ScenarioContent("example:scenario", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of("example:" + "a".repeat(ObservationCatalog.MAX_ID_LENGTH), false)));
        var invalidJson = root.deepCopy(); invalidJson.getAsJsonObject("flags").addProperty("example:ready", "true");
        assertThrows(IllegalArgumentException.class, () -> ScenarioContentJson.decode(invalidJson.toString()));
        var captured = ScenarioContentCapture.capture("example:scenario", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of("example:ready", true));
        assertEquals(Map.of("example:ready", false), captured.content().flags());
    }

    @Test void portablePoolsAndExtensionsRoundTripAndValidateReferences() {
        var event = new EventTemplate("example:cue", "Cue", "", "Hello", "", ObservationCatalog.Audience.PARTICIPANTS, "", PlayState.Mode.MANUAL, "", 0, List.of(), Map.of(), List.of(), EventSource.empty());
        var pool = new io.github.chronicle.core.eventpool.EventPoolDefinition("example:pool", "Pool", List.of(new io.github.chronicle.core.eventpool.EventPoolDefinition.Entry(event.id(), 2)));
        var extensions = Map.of("example:settings", ScenarioContentJson.canonicalExtension("{\"z\":1,\"a\":{\"b\":true}}"));
        var content = new ScenarioContent("example:scenario", Map.of(), Map.of(), Map.of(), Map.of(event.id(),event), Map.of(), Map.of(), Map.of(pool.id(),pool), extensions);
        String json = ScenarioContentJson.encode(content);
        assertEquals(content,ScenarioContentJson.decode(json)); assertEquals(json,ScenarioContentJson.encode(ScenarioContentJson.decode(json)));
        assertTrue(json.contains("\"extensions\":{\"example:settings\":{\"a\":{"));
        assertThrows(IllegalArgumentException.class,()->new ScenarioContent("example:scenario",Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(pool.id(),pool),extensions));
        assertThrows(IllegalArgumentException.class,()->ScenarioContentJson.canonicalExtension("{\"x\":1,\"x\":2}"));
        assertThrows(IllegalArgumentException.class,()->ScenarioContentJson.canonicalExtension("{\"x\":\""+"a".repeat(ScenarioContentJson.MAX_EXTENSION_BYTES)+"\"}"));
        var legacy=JsonParser.parseString(json).getAsJsonObject(); legacy.addProperty("schemaVersion",4); legacy.remove("eventPools"); legacy.remove("extensions");
        assertTrue(ScenarioContentJson.decode(legacy.toString()).eventPools().isEmpty());
    }

    @Test void disabledConfiguredPoolEventsAreNotSilentlyPromoted() {
        var definition = new EventDefinition("example:cue", 1, "Cue", "", "Text", "", ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of(), Map.of());
        var disabled = new EventConfiguration(definition, PlayState.Mode.MANUAL, Optional.empty(), 0, 0, List.of(), false, EventSource.empty());
        var pool = new io.github.chronicle.core.eventpool.EventPoolDefinition("example:pool", "Pool", List.of(new io.github.chronicle.core.eventpool.EventPoolDefinition.Entry(definition.id(), 1)));
        assertThrows(IllegalArgumentException.class, () -> ScenarioContentCapture.validatePoolConfigurations(Map.of(pool.id(),pool), Map.of(definition.id(),disabled)));
        assertDoesNotThrow(() -> ScenarioContentCapture.validatePoolConfigurations(Map.of(pool.id(),pool), Map.of()));
    }

    @Test void reusableCatalogExtensionsCanExceedSixtyFourKiBButRemainBounded() {
        String large=ScenarioContentJson.canonicalExtension("{\"body\":\""+"a".repeat(70_000)+"\"}");
        var content=new ScenarioContent("example:scenario",Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of("example:catalog",large));
        assertEquals(content,ScenarioContentJson.decode(ScenarioContentJson.encode(content)));
        String multibyte="{\"body\":\""+"\uD83D\uDE00".repeat(ScenarioContentJson.MAX_EXTENSION_BYTES/4)+"\"}";
        assertThrows(IllegalArgumentException.class,()->ScenarioContentJson.canonicalExtension(multibyte));
        String nested="{\"x\":".repeat(40)+"{}"+"}".repeat(40);
        assertThrows(IllegalArgumentException.class,()->ScenarioContentJson.canonicalExtension(nested));
    }

    @Test void multipleLegalExtensionsCannotExceedTheWholeContentBudget() {
        String large=ScenarioContentJson.canonicalExtension("{\"body\":\""+"a".repeat(ScenarioContentJson.MAX_EXTENSION_BYTES-128)+"\"}");
        var sections=new java.util.TreeMap<String,String>();
        for(int i=0;i<9;i++)sections.put("example:catalog_"+i,large);
        var content=new ScenarioContent("example:scenario",Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),sections);
        assertThrows(IllegalArgumentException.class,()->ScenarioContentJson.encode(content));
    }

}
