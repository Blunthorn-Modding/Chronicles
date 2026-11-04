package io.github.chronicle.quietbelow.content;

import io.github.chronicle.core.scenario.ScenarioContentJson;
import io.github.chronicle.core.play.EventOccurrencePolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuietBelowFinalDefaultsTest {
    @Test void finalDocumentContentIsPortableCompleteAndStrictlyRoundTrips() {
        var content = QuietBelowScenarioDefaults.loadOptional().orElseThrow();
        assertEquals(6, content.roles().size()); assertEquals(10, content.roles().values().stream().mapToInt(r -> r.capacity()).sum());
        assertEquals(9, content.books().size()); assertEquals(4, content.lootTables().size()); assertEquals(9, content.events().size());
        assertEquals(content, ScenarioContentJson.decode(ScenarioContentJson.encode(content)));
        content.roles().values().forEach(role -> { assertNull(role.starting()); assertNull(role.respawn()); role.books().forEach(id -> assertTrue(content.books().containsKey(id))); });
        content.events().values().forEach(event -> assertFalse(event.enabled()));
        assertEquals(EventOccurrencePolicy.ONCE_PER_ACTOR, content.events().get("quiet_below:altar_offering").occurrencePolicy());
        assertEquals(EventOccurrencePolicy.PER_SOURCE_OCCURRENCE, content.events().get("quiet_below:sculk_growth").occurrencePolicy());
        var city = content.lootTables().get("quiet_below:ancient_city"); assertEquals(28, city.entries().size());
        assertTrue(city.entries().stream().noneMatch(entry -> entry.item().equals("minecraft:enchanted_book")));
        assertTrue(city.entries().stream().anyMatch(entry -> entry.item().equals("minecraft:book")));
        assertEquals(36000, content.events().get("quiet_below:sinkhole_opening").delayTicks());
        assertEquals(18000, content.events().get("quiet_below:sculk_awakening").delayTicks());
        String journal = String.join(" ", content.books().get("quiet_below:research_journal_ii").pages()).replace('\n', ' ');
        assertTrue(journal.contains("planks seems to halt") || journal.contains("planks seem to halt"));
    }
}
