package io.github.chronicle.core.authoring.ui;

import java.util.List;
import java.util.Objects;

/** Bounded presentation data. No client-provided coordinates or command strings are accepted. */
public final class AuthoringUiModel {
    private AuthoringUiModel() { }
    public enum Tab { CHESTS, PLACES, ROLES, SPAWNS, SPAWN_LOCATIONS, SPAWN_PLAYERS, SESSION }
    public enum Action {
        OPEN, REFRESH, MODE_ON, MODE_OFF, CORNER_1, CORNER_2, TARGET_1, TARGET_2, CLEAR_SELECTION, HIDE_PREVIEW,
        CHEST_BIND, CHEST_RETYPE, CHEST_UNBIND, CHEST_SHOW, LOOT_RELOAD,
        ROLE_SAVE, ROLE_REPLACE, SITE_REPLACE, SITE_REMOVE, SITE_SHOW,
        SPAWN_BIND, SPAWN_REBIND, SPAWN_REMOVE, SPAWN_PREVIEW, SPAWN_CONFIRM,
        SESSION_START, SESSION_PAUSE, SESSION_RESUME, SESSION_END
    }
    public record Entry(String id, String label, String detail, String kind, String reference, long revision) {
        public Entry {
            text(id, 160); text(label, 128); text(detail, 768); text(kind, 32); text(reference, 160);
            if (revision < 0) throw new IllegalArgumentException("Invalid entry revision");
        }
    }
    public record Snapshot(long token, Tab tab, int page, int pages, String phase, int session,
                           boolean author, boolean editable, String hold, String selection, String target,
                           String message, boolean success, List<Entry> entries, List<Entry> choices,
                           String previewPlayer) {
        public Snapshot {
            Objects.requireNonNull(tab); text(phase, 32); text(hold, 512); text(selection, 512);
            text(target, 512); text(message, 1024); text(previewPlayer, 64);
            if (token < 1 || page < 0 || pages < 1 || page >= pages || session < 0) throw new IllegalArgumentException("Invalid UI state");
            entries = List.copyOf(entries); choices = List.copyOf(choices);
            if (entries.size() > 12 || choices.size() > 128) throw new IllegalArgumentException("UI page exceeds bounds");
        }
    }
    private static void text(String value, int max) {
        if (value == null || value.length() > max || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid UI text");
    }
}
