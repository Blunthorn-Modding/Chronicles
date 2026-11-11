package io.github.chronicle.core.menu;

import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import io.github.chronicle.core.menu.CoreMenuModel.Page;

/** Page-specific list categories shared by authoritative selection and native navigation. */
public final class CoreMenuFilters {
    public static final String GAME_EVENTS = "GAME_EVENTS";
    public static final String REGION_CHANGES = "REGION_CHANGES";

    private CoreMenuFilters() { }

    public static String normalize(Page page, String requested) {
        if (page == Page.EVENTS) return REGION_CHANGES.equals(requested) ? REGION_CHANGES : GAME_EVENTS;
        return page == Page.LOCATIONS && "BLOCK".equals(requested) ? "BLOCK" : "POINT";
    }

    public static boolean acceptsEvent(String filter, Entry entry) {
        return REGION_CHANGES.equals(normalize(Page.EVENTS, filter))
                ? entry.id().startsWith("world:")
                : entry.id().startsWith("cue:") || entry.id().startsWith("action:");
    }
}
