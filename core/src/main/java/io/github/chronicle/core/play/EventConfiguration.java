package io.github.chronicle.core.play;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/** Trigger and readiness configuration for one immutable Event definition revision. */
public record EventConfiguration(EventDefinition definition, PlayState.Mode mode,
                                 Optional<EventRegionReference> triggerRegion,
                                 long armedAtTicks, long delayTicks,
                                 List<String> requiredFlags, boolean enabled, EventSource source, int formatVersion,
                                 EventOccurrencePolicy occurrencePolicy) {
    public static final long MAX_DELAY_TICKS = Math.multiplyExact(ObservationConfiguration.MAX_DELAY_SECONDS, 20L);
    public static final int MAX_FLAGS = 32;

    public EventConfiguration(EventDefinition definition, PlayState.Mode mode, Optional<EventRegionReference> triggerRegion,
                              long armedAtTicks, long delayTicks, String requiredFlag, boolean enabled) {
        this(definition, mode, triggerRegion, armedAtTicks, delayTicks, legacyFlags(requiredFlag), enabled, EventSource.empty(), 1);
    }

    public EventConfiguration(EventDefinition definition, PlayState.Mode mode, Optional<EventRegionReference> triggerRegion,
                              long armedAtTicks, long delayTicks, List<String> requiredFlags, boolean enabled, EventSource source) {
        this(definition, mode, triggerRegion, armedAtTicks, delayTicks, requiredFlags, enabled, source,
                definition != null && definition.formatVersion() >= 3 ? 3 : 2);
    }

    /** Compatibility constructor for exact v1-v3 configurations. */
    public EventConfiguration(EventDefinition definition, PlayState.Mode mode, Optional<EventRegionReference> triggerRegion,
                              long armedAtTicks, long delayTicks, List<String> requiredFlags, boolean enabled,
                              EventSource source, int formatVersion) {
        this(definition, mode, triggerRegion, armedAtTicks, delayTicks, requiredFlags, enabled, source, formatVersion,
                EventOccurrencePolicy.ONCE);
    }

    /** Current constructor for explicitly recurring source Events. */
    public EventConfiguration(EventDefinition definition, PlayState.Mode mode, Optional<EventRegionReference> triggerRegion,
                              long armedAtTicks, long delayTicks, List<String> requiredFlags, boolean enabled,
                              EventSource source, EventOccurrencePolicy occurrencePolicy) {
        this(definition, mode, triggerRegion, armedAtTicks, delayTicks, requiredFlags, enabled, source, 4, occurrencePolicy);
    }

    public EventConfiguration {
        Objects.requireNonNull(definition, "event definition is required");
        Objects.requireNonNull(mode, "event trigger mode is required");
        Objects.requireNonNull(triggerRegion, "event trigger Region reference is required");
        Objects.requireNonNull(source, "event source is required");
        Objects.requireNonNull(occurrencePolicy, "event occurrence policy is required");
        Objects.requireNonNull(requiredFlags, "required flags are required");
        if (formatVersion < 1 || formatVersion > 4) throw new IllegalArgumentException("unsupported event configuration format");
        if (definition.formatVersion() != formatVersion) throw new IllegalArgumentException("event definition and configuration formats must match");
        if (requiredFlags.size() > MAX_FLAGS) throw new IllegalArgumentException("too many required flags");
        TreeSet<String> flags = new TreeSet<>();
        for (String flag : requiredFlags) {
            ObservationCatalog.requireIdentifier(flag, "required flag");
            if (!flags.add(flag)) throw new IllegalArgumentException("required flags must be unique");
        }
        requiredFlags = List.copyOf(flags);
        if (formatVersion == 1 && (requiredFlags.size() > 1 || !source.isEmpty() || mode == PlayState.Mode.SYSTEM)) {
            throw new IllegalArgumentException("legacy event configuration cannot contain progression sources or multiple requirements");
        }
        if (formatVersion < 4 && occurrencePolicy != EventOccurrencePolicy.ONCE) {
            throw new IllegalArgumentException("legacy Event formats are always once per campaign");
        }
        if (armedAtTicks < 0) throw new IllegalArgumentException("event armed time must not be negative");
        if (delayTicks < 0) throw new IllegalArgumentException("event delay must not be negative");
        if (mode == PlayState.Mode.TIMER) {
            if (delayTicks < 1 || delayTicks > MAX_DELAY_TICKS) {
                throw new IllegalArgumentException("event timer delay is outside the supported range");
            }
        } else if (formatVersion == 1) {
            delayTicks = 0;
        } else if (delayTicks > MAX_DELAY_TICKS) {
            throw new IllegalArgumentException("event delay is outside the supported range");
        }
        if (mode == PlayState.Mode.SYSTEM && source.isEmpty()) throw new IllegalArgumentException("a system event requires a source");
        if (occurrencePolicy != EventOccurrencePolicy.ONCE) {
            if(source.type().equals("chronicle_core:event_completed"))throw new IllegalArgumentException("Event-completion triggers are once per campaign; repeating completion chains could loop indefinitely.");
            if(delayTicks!=0)throw new IllegalArgumentException("Repeating source cues currently require no delay. Set delay to zero or choose Once per campaign.");
            if (formatVersion != 4 || mode != PlayState.Mode.SYSTEM || source.isEmpty()) {
                throw new IllegalArgumentException("repeating Events require a current registered source trigger");
            }
            if (!definition.hasMedia() || !definition.changes().isEmpty() || !definition.flagChanges().isEmpty()
                    || !definition.encounterActions().isEmpty()) {
                throw new IllegalArgumentException("repeating Events may contain only text and sound actions");
            }
            if (occurrencePolicy == EventOccurrencePolicy.ONCE_PER_ACTOR
                    && definition.audience() != ObservationCatalog.Audience.SOURCE_ACTOR) {
                throw new IllegalArgumentException("once-per-player Events must target the triggering player");
            }
        }
        if (definition.audience() == ObservationCatalog.Audience.SOURCE_ACTOR) {
            if (mode != PlayState.Mode.SYSTEM || occurrencePolicy == EventOccurrencePolicy.ONCE) {
                throw new IllegalArgumentException("triggering-player audience requires a repeating source Event");
            }
        }
        if (enabled && mode == PlayState.Mode.ENTRY && triggerRegion.isEmpty()) {
            throw new IllegalArgumentException("an enabled entry event requires a trigger Region");
        }
        if (enabled && mode == PlayState.Mode.CONDITION && requiredFlags.isEmpty()) {
            throw new IllegalArgumentException("an enabled condition event requires a flag");
        }
        if (enabled && definition.audience() == ObservationCatalog.Audience.REGION
                && definition.audienceRegion().isEmpty()) {
            throw new IllegalArgumentException("an enabled Region-audience event requires an audience Region");
        }
    }

    public String requiredFlag() { return requiredFlags.isEmpty() ? "" : requiredFlags.getFirst(); }

    /** Preserves the exact v1 digest for immutable archives while pinning every current requirement and source. */
    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "chronicle:event-configuration:v" + formatVersion);
            update(digest, definition.fingerprint());
            update(digest, mode.name());
            update(digest, triggerRegion.isPresent());
            triggerRegion.ifPresent(reference -> { update(digest, reference.id()); update(digest, reference.revision()); });
            update(digest, armedAtTicks);
            update(digest, delayTicks);
            if (formatVersion == 1) update(digest, requiredFlag());
            else {
                update(digest, requiredFlags.size());
                requiredFlags.forEach(flag -> update(digest, flag));
                update(digest, source.type()); update(digest, source.target()); update(digest, source.activity());
            }
            if (formatVersion >= 4) update(digest, occurrencePolicy.name());
            update(digest, enabled);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static List<String> legacyFlags(String flag) {
        if (flag == null) throw new IllegalArgumentException("required flag is required; use an empty string for no flag");
        return flag.isEmpty() ? List.of() : List.of(flag);
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
    private static void update(MessageDigest digest, boolean value) { digest.update((byte) (value ? 1 : 0)); }
    private static void update(MessageDigest digest, int value) { digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array()); }
    private static void update(MessageDigest digest, long value) { digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array()); }
}
