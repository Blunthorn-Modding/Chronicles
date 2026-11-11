package io.github.chronicle.core.menu;

import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import io.github.chronicle.core.menu.CoreMenuModel.Page;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreMenuFiltersTest {
    @Test void normalizesWithinEachPage() {
        assertEquals("GAME_EVENTS", CoreMenuFilters.normalize(Page.EVENTS, ""));
        assertEquals("REGION_CHANGES", CoreMenuFilters.normalize(Page.EVENTS, "REGION_CHANGES"));
        assertEquals("GAME_EVENTS", CoreMenuFilters.normalize(Page.EVENTS, "BLOCK"));
        assertEquals("BLOCK", CoreMenuFilters.normalize(Page.LOCATIONS, "BLOCK"));
        assertEquals("POINT", CoreMenuFilters.normalize(Page.LOCATIONS, "REGION_CHANGES"));
        assertEquals("POINT", CoreMenuFilters.normalize(Page.FLAGS, "REGION_CHANGES"));
    }

    @Test void separatesDirectStateActionsAndKeepsComposedEventsTogether() {
        var composed = entry("cue:test:with_region_changes", "CUE");
        var action = entry("action:test:reveal", "ACTION");
        var direct = entry("world:test:ship:end", "WORLD_ACTION");
        var flag = entry("flag:test:ready", "FLAG");
        assertTrue(CoreMenuFilters.acceptsEvent("GAME_EVENTS", composed));
        assertTrue(CoreMenuFilters.acceptsEvent("GAME_EVENTS", action));
        assertFalse(CoreMenuFilters.acceptsEvent("GAME_EVENTS", direct));
        assertTrue(CoreMenuFilters.acceptsEvent("REGION_CHANGES", direct));
        assertFalse(CoreMenuFilters.acceptsEvent("REGION_CHANGES", composed));
        assertFalse(CoreMenuFilters.acceptsEvent("REGION_CHANGES", action));
        for (String filter : new String[]{"GAME_EVENTS", "REGION_CHANGES"})
            assertFalse(CoreMenuFilters.acceptsEvent(filter, flag));
    }

    private static Entry entry(String id, String kind) { return new Entry(id, id, "", kind, "", 0); }
}
