package io.github.chronicle.core.play;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.encounters.EncounterTarget;

import java.util.Objects;
import java.util.Optional;

/** One bounded spawn action. An empty override deliberately uses the group's map binding. */
public record EventEncounterAction(String groupId, Optional<EncounterTarget> targetOverride) {
    public EventEncounterAction {
        if (groupId == null || !AuthoringSite.NAMESPACED_ID.matcher(groupId).matches())
            throw new IllegalArgumentException("encounter action group ID must be namespaced");
        targetOverride = Objects.requireNonNull(targetOverride, "encounter action target override is required");
    }

    public static EventEncounterAction groupTarget(String groupId) {
        return new EventEncounterAction(groupId, Optional.empty());
    }
}
