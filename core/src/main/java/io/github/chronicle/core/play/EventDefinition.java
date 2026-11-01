package io.github.chronicle.core.play;

import io.github.chronicle.core.areas.AreaBounds;
import io.github.chronicle.core.areas.SnapshotRef;
import io.github.chronicle.core.authoring.BlockPosition;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.Collections;
import java.util.TreeMap;

/** Immutable, revisioned authoring definition for a unified Chronicle Event. */
public record EventDefinition(String id, long revision, String label, String description,
                              String text, String sound, ObservationCatalog.Audience audience,
                              Optional<EventRegionReference> audienceRegion,
                              List<EventRegionChange> changes, Map<String, Boolean> flagChanges,
                              List<EventEncounterAction> encounterActions, int formatVersion) {
    public static final int MAX_CHANGES = 8;
    public static final int MAX_TOTAL_CELLS = 262_144;
    public static final int MAX_UNIQUE_CHUNKS = 64;
    public static final int MAX_FLAGS = 32;
    public static final int MAX_ENCOUNTER_ACTIONS = 4;

    public EventDefinition(String id, long revision, String label, String description, String text, String sound,
                           ObservationCatalog.Audience audience, Optional<EventRegionReference> audienceRegion,
                           List<EventRegionChange> changes) {
        this(id, revision, label, description, text, sound, audience, audienceRegion, changes, Map.of(), List.of(), 1);
    }

    public EventDefinition(String id, long revision, String label, String description, String text, String sound,
                           ObservationCatalog.Audience audience, Optional<EventRegionReference> audienceRegion,
                           List<EventRegionChange> changes, Map<String, Boolean> flagChanges) {
        this(id, revision, label, description, text, sound, audience, audienceRegion, changes, flagChanges, List.of(), 2);
    }

    public EventDefinition(String id, long revision, String label, String description, String text, String sound,
                           ObservationCatalog.Audience audience, Optional<EventRegionReference> audienceRegion,
                           List<EventRegionChange> changes, Map<String, Boolean> flagChanges,
                           List<EventEncounterAction> encounterActions) {
        this(id, revision, label, description, text, sound, audience, audienceRegion, changes, flagChanges, encounterActions, 3);
    }

    /** Compatibility constructor used by exact v1/v2 revisions. */
    public EventDefinition(String id, long revision, String label, String description, String text, String sound,
                           ObservationCatalog.Audience audience, Optional<EventRegionReference> audienceRegion,
                           List<EventRegionChange> changes, Map<String, Boolean> flagChanges, int formatVersion) {
        this(id, revision, label, description, text, sound, audience, audienceRegion, changes, flagChanges, List.of(), formatVersion);
    }

    public EventDefinition {
        ObservationCatalog.requireIdentifier(id, "event ID");
        if (revision < 1) throw new IllegalArgumentException("event revision must be positive");
        requireText(label, 80, false, false, "event label");
        requireText(description, 512, true, false, "event description");
        requireText(text, 512, true, true, "event text");
        if (sound == null) throw new IllegalArgumentException("event sound is required; use an empty string for no sound");
        if (!sound.isEmpty()) ObservationCatalog.requireIdentifier(sound, "event sound");
        Objects.requireNonNull(audience, "event audience is required");
        Objects.requireNonNull(audienceRegion, "event audience Region reference is required");
        if (audience != ObservationCatalog.Audience.REGION && audienceRegion.isPresent()) {
            throw new IllegalArgumentException("only a Region audience may have an audience Region reference");
        }
        Objects.requireNonNull(changes, "event Region changes are required");
        changes = List.copyOf(changes);
        if (formatVersion < 1 || formatVersion > 3) throw new IllegalArgumentException("unsupported event definition format");
        Objects.requireNonNull(flagChanges, "event flag changes are required");
        if (flagChanges.size() > MAX_FLAGS) throw new IllegalArgumentException("too many event flag changes");
        TreeMap<String, Boolean> orderedFlags = new TreeMap<>();
        for (var entry : flagChanges.entrySet()) {
            ObservationCatalog.requireIdentifier(entry.getKey(), "event outcome flag");
            orderedFlags.put(entry.getKey(), Objects.requireNonNull(entry.getValue(), "event flag value is required"));
        }
        flagChanges = Collections.unmodifiableMap(orderedFlags);
        if (formatVersion == 1 && !flagChanges.isEmpty()) throw new IllegalArgumentException("legacy event definitions cannot change flags");
        encounterActions = List.copyOf(Objects.requireNonNull(encounterActions, "event encounter actions are required"));
        if (encounterActions.size() > MAX_ENCOUNTER_ACTIONS) throw new IllegalArgumentException("an event may spawn at most 4 encounter groups");
        if (formatVersion < 3 && !encounterActions.isEmpty()) throw new IllegalArgumentException("legacy event definitions cannot spawn encounter groups");
        if (changes.size() > MAX_CHANGES) {
            throw new IllegalArgumentException("an event may change at most " + MAX_CHANGES + " Regions");
        }
        if (text.isBlank() && sound.isEmpty() && changes.isEmpty() && flagChanges.isEmpty() && encounterActions.isEmpty()) {
            throw new IllegalArgumentException("an event requires text, sound, a Region change or a flag change");
        }
        validateChanges(changes);
    }

    public boolean hasMedia() { return !text.isBlank() || !sound.isEmpty(); }

    public boolean progressOnly() { return !hasMedia() && changes.isEmpty() && !flagChanges.isEmpty(); }

    /** Stable digest of every field and every ordered target pin in this definition. */
    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "chronicle:event-definition:v" + formatVersion);
            update(digest, id);
            update(digest, revision);
            update(digest, label);
            update(digest, description);
            update(digest, text);
            update(digest, sound);
            update(digest, audience.name());
            update(digest, audienceRegion.isPresent());
            audienceRegion.ifPresent(reference -> update(digest, reference));
            update(digest, changes.size());
            for (EventRegionChange change : changes) update(digest, change);
            if (formatVersion >= 2) {
                update(digest, flagChanges.size());
                flagChanges.forEach((flag, value) -> { update(digest, flag); update(digest, value); });
            }
            if (formatVersion >= 3) {
                update(digest, encounterActions.size());
                for (EventEncounterAction action : encounterActions) {
                    update(digest, action.groupId());
                    update(digest, action.targetOverride().isPresent());
                    action.targetOverride().ifPresent(target -> {
                        update(digest, target.kind().name()); update(digest, target.siteId()); update(digest, target.siteRevision());
                    });
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void validateChanges(List<EventRegionChange> changes) {
        Set<String> areaIds = new HashSet<>();
        Set<String> regionIds = new HashSet<>();
        Set<Long> chunks = new HashSet<>();
        String dimension = null;
        long cells = 0;
        for (int index = 0; index < changes.size(); index++) {
            EventRegionChange change = Objects.requireNonNull(changes.get(index), "event Region change is required");
            AreaBounds bounds = change.snapshot().bounds();
            if (dimension == null) dimension = bounds.dimension();
            else if (!dimension.equals(bounds.dimension())) {
                throw new IllegalArgumentException("all event Region changes must be in one dimension");
            }
            if (!areaIds.add(change.areaId())) throw new IllegalArgumentException("event Area targets must be distinct");
            if (!regionIds.add(change.region().id())) throw new IllegalArgumentException("event Region targets must be distinct");
            for (int prior = 0; prior < index; prior++) {
                if (overlaps(bounds, changes.get(prior).snapshot().bounds())) {
                    throw new IllegalArgumentException("event Region changes must not overlap");
                }
            }
            cells += bounds.cells();
            if (cells > MAX_TOTAL_CELLS) {
                throw new IllegalArgumentException("event Region changes exceed " + MAX_TOTAL_CELLS + " total cells");
            }
            for (int chunkX = Math.floorDiv(bounds.min().x(), 16); chunkX <= Math.floorDiv(bounds.max().x(), 16); chunkX++) {
                for (int chunkZ = Math.floorDiv(bounds.min().z(), 16); chunkZ <= Math.floorDiv(bounds.max().z(), 16); chunkZ++) {
                    chunks.add((((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL));
                    if (chunks.size() > MAX_UNIQUE_CHUNKS) {
                        throw new IllegalArgumentException("event Region changes intersect more than " + MAX_UNIQUE_CHUNKS + " unique chunks");
                    }
                }
            }
        }
    }

    private static boolean overlaps(AreaBounds first, AreaBounds second) {
        if (!first.dimension().equals(second.dimension())) return false;
        return intersects(first.min().x(), first.max().x(), second.min().x(), second.max().x())
                && intersects(first.min().y(), first.max().y(), second.min().y(), second.max().y())
                && intersects(first.min().z(), first.max().z(), second.min().z(), second.max().z());
    }

    private static boolean intersects(int firstMin, int firstMax, int secondMin, int secondMax) {
        return firstMin <= secondMax && secondMin <= firstMax;
    }

    private static void requireText(String value, int maximum, boolean emptyAllowed, boolean newlineAllowed, String name) {
        if (value == null || value.length() > maximum || (!emptyAllowed && value.isBlank())) {
            throw new IllegalArgumentException(name + " must be " + (emptyAllowed ? "at most " : "1-") + maximum + " characters");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) && !(newlineAllowed && character == '\n')) {
                throw new IllegalArgumentException(name + " contains an unsupported control character");
            }
        }
    }

    private static void update(MessageDigest digest, EventRegionChange change) {
        update(digest, change.region());
        update(digest, change.areaId());
        update(digest, change.areaRevision());
        update(digest, change.state().name());
        update(digest, change.snapshot());
    }

    private static void update(MessageDigest digest, EventRegionReference reference) {
        update(digest, reference.id());
        update(digest, reference.revision());
    }

    private static void update(MessageDigest digest, SnapshotRef snapshot) {
        update(digest, snapshot.sha256());
        AreaBounds bounds = snapshot.bounds();
        update(digest, bounds.regionId());
        update(digest, bounds.dimension());
        update(digest, bounds.regionRevision());
        update(digest, bounds.min());
        update(digest, bounds.max());
        update(digest, snapshot.cells());
        update(digest, snapshot.encodedBytes());
    }

    private static void update(MessageDigest digest, BlockPosition position) {
        update(digest, position.x());
        update(digest, position.y());
        update(digest, position.z());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        update(digest, bytes.length);
        digest.update(bytes);
    }

    private static void update(MessageDigest digest, boolean value) {
        digest.update((byte) (value ? 1 : 0));
    }

    private static void update(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void update(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }
}
