package io.github.chronicle.core.menu;

import java.util.List;
import java.util.Objects;

/**
 * Complete, bounded local draft for the Events editor.
 *
 * <p>This is deliberately a transport value, rather than an event domain model.  In
 * particular a new editor is allowed to carry an incomplete draft while an organizer
 * moves between pages.  The server validates identifiers, catalog pins, effects and
 * authority before it persists or executes anything.</p>
 */
public record EventEditorForm(
        String id, long revision, String name, String description, String message, String sound,
        String audience, String audienceRegion, long audienceRegionRevision,
        String mode, String triggerRegion, long triggerRegionRevision, long seconds, String flag,
        List<Change> changes, boolean creating) {
    public static final long MAX_SECONDS = 604_800L;
    public static final int MAX_CHANGES = 8;

    public EventEditorForm {
        text(id, 128); text(name, 80); text(description, 512); text(message, 512); text(sound, 128);
        text(audience, 32); text(audienceRegion, 128); text(mode, 32); text(triggerRegion, 128); text(flag, 128);
        if (revision < 0 || audienceRegionRevision < 0 || triggerRegionRevision < 0 || seconds < 0 || seconds > MAX_SECONDS)
            throw new IllegalArgumentException("Invalid event editor number");
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        if (changes.size() > MAX_CHANGES) throw new IllegalArgumentException("Too many event changes");
    }

    /** A pinned Region Saved-state target, including stale/missing references for repair. */
    public record Change(String regionId, String label, String state, long areaRevision, String snapshotHash,
                         String dimension, boolean current, String problem) {
        public Change {
            text(regionId, 128); text(label, 160); text(state, 32); text(snapshotHash, 64);
            text(dimension, 128); text(problem, 512);
            if (areaRevision < 0) throw new IllegalArgumentException("Invalid saved-state revision");
            if (!snapshotHash.isEmpty() && !snapshotHash.matches("[0-9a-fA-F]{64}"))
                throw new IllegalArgumentException("Invalid saved-state snapshot hash");
        }
    }

    private static void text(String value, int maximum) {
        if (value == null || value.length() > maximum || value.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Event editor text exceeds bounds");
    }
}
