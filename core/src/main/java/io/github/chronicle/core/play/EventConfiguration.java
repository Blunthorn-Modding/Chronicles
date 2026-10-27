package io.github.chronicle.core.play;

import java.util.Objects;
import java.util.Optional;

/** Trigger and readiness configuration for one immutable Event definition revision. */
public record EventConfiguration(EventDefinition definition, PlayState.Mode mode,
                                 Optional<EventRegionReference> triggerRegion,
                                 long armedAtTicks, long delayTicks,
                                 String requiredFlag, boolean enabled) {
    public static final long MAX_DELAY_TICKS = Math.multiplyExact(ObservationConfiguration.MAX_DELAY_SECONDS, 20L);

    public EventConfiguration {
        Objects.requireNonNull(definition, "event definition is required");
        Objects.requireNonNull(mode, "event trigger mode is required");
        Objects.requireNonNull(triggerRegion, "event trigger Region reference is required");
        if (armedAtTicks < 0) throw new IllegalArgumentException("event armed time must not be negative");
        if (delayTicks < 0) throw new IllegalArgumentException("event delay must not be negative");
        if (mode == PlayState.Mode.TIMER) {
            if (delayTicks < 1 || delayTicks > MAX_DELAY_TICKS) {
                throw new IllegalArgumentException("event timer delay is outside the supported range");
            }
        } else {
            delayTicks = 0;
        }
        if (requiredFlag == null) throw new IllegalArgumentException("required flag is required; use an empty string for no flag");
        if (!requiredFlag.isEmpty()) ObservationCatalog.requireIdentifier(requiredFlag, "required flag");
        if (enabled && mode == PlayState.Mode.ENTRY && triggerRegion.isEmpty()) {
            throw new IllegalArgumentException("an enabled entry event requires a trigger Region");
        }
        if (enabled && mode == PlayState.Mode.CONDITION && requiredFlag.isEmpty()) {
            throw new IllegalArgumentException("an enabled condition event requires a flag");
        }
        if (enabled && definition.audience() == ObservationCatalog.Audience.REGION
                && definition.audienceRegion().isEmpty()) {
            throw new IllegalArgumentException("an enabled Region-audience event requires an audience Region");
        }
    }
}
