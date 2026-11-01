package io.github.chronicle.core.menu;

import io.github.chronicle.core.play.EventSource;

import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;
import java.util.HashSet;

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
        String mode, String triggerRegion, long triggerRegionRevision, long seconds, List<String> requiredFlags,
        List<Change> changes, boolean creating, Map<String, Boolean> flagChanges, EventSource source,
        List<EncounterAction> encounterActions) {
    public static final long MAX_SECONDS = 604_800L;
    public static final int MAX_CHANGES = 8;
    public static final int MAX_FLAGS = 32;
    public static final int MAX_ENCOUNTER_ACTIONS = 4;

    public EventEditorForm(String id, long revision, String name, String description, String message, String sound,
                           String audience, String audienceRegion, long audienceRegionRevision,
                           String mode, String triggerRegion, long triggerRegionRevision, long seconds, String flag,
                           List<Change> changes, boolean creating) {
        this(id, revision, name, description, message, sound, audience, audienceRegion, audienceRegionRevision,
                mode, triggerRegion, triggerRegionRevision, seconds, legacyFlags(flag), changes, creating, Map.of(), EventSource.empty(), List.of());
    }

    public EventEditorForm(String id, long revision, String name, String description, String message, String sound,
                           String audience, String audienceRegion, long audienceRegionRevision,
                           String mode, String triggerRegion, long triggerRegionRevision, long seconds, List<String> requiredFlags,
                           List<Change> changes, boolean creating, Map<String, Boolean> flagChanges, EventSource source) {
        this(id,revision,name,description,message,sound,audience,audienceRegion,audienceRegionRevision,mode,triggerRegion,
                triggerRegionRevision,seconds,requiredFlags,changes,creating,flagChanges,source,List.of());
    }

    public EventEditorForm {
        text(id, 128); text(name, 80); text(description, 512); text(message, 512); text(sound, 128);
        text(audience, 32); text(audienceRegion, 128); text(mode, 32); text(triggerRegion, 128);
        requiredFlags = List.copyOf(Objects.requireNonNull(requiredFlags, "requiredFlags"));
        if (requiredFlags.size() > MAX_FLAGS) throw new IllegalArgumentException("Too many event requirements");
        HashSet<String> distinct = new HashSet<>();
        for (String flag : requiredFlags) {
            text(flag, 128);
            if (!distinct.add(flag)) throw new IllegalArgumentException("Duplicate event requirement");
        }
        Objects.requireNonNull(flagChanges, "flagChanges");
        if (flagChanges.size() > MAX_FLAGS) throw new IllegalArgumentException("Too many event flag changes");
        TreeMap<String, Boolean> orderedFlags = new TreeMap<>();
        for (var entry : flagChanges.entrySet()) {
            text(entry.getKey(), 128);
            orderedFlags.put(entry.getKey(), Objects.requireNonNull(entry.getValue(), "flag value"));
        }
        flagChanges = Collections.unmodifiableMap(orderedFlags);
        Objects.requireNonNull(source, "source");
        if (revision < 0 || audienceRegionRevision < 0 || triggerRegionRevision < 0 || seconds < 0 || seconds > MAX_SECONDS)
            throw new IllegalArgumentException("Invalid event editor number");
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        if (changes.size() > MAX_CHANGES) throw new IllegalArgumentException("Too many event changes");
        encounterActions = List.copyOf(Objects.requireNonNull(encounterActions, "encounterActions"));
        if(encounterActions.size()>MAX_ENCOUNTER_ACTIONS)throw new IllegalArgumentException("Too many encounter actions");
    }

    public String flag() { return requiredFlags.isEmpty() ? "" : requiredFlags.getFirst(); }

    private static List<String> legacyFlags(String flag) {
        text(flag, 128);
        return flag.isEmpty() ? List.of() : List.of(flag);
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

    /** A local spawn-action draft. GROUP uses the group's binding; POINT/REGION pin an override. */
    public record EncounterAction(String groupId,String groupLabel,String targetKind,String siteId,String siteLabel,
                                  long siteRevision,boolean current,String problem) {
        public EncounterAction {text(groupId,128);text(groupLabel,160);text(targetKind,16);text(siteId,128);text(siteLabel,160);text(problem,512);if(siteRevision<0)throw new IllegalArgumentException("Invalid encounter target revision");if(!targetKind.equals("GROUP")&&!targetKind.equals("POINT")&&!targetKind.equals("REGION"))throw new IllegalArgumentException("Invalid encounter target kind");if(targetKind.equals("GROUP")&&(!siteId.isEmpty()||siteRevision!=0))throw new IllegalArgumentException("Group-target action cannot pin a site");}
    }

    private static void text(String value, int maximum) {
        if (value == null || value.length() > maximum || value.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Event editor text exceeds bounds");
    }
}
