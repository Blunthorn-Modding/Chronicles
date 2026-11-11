package io.github.chronicle.core.campaign;

import java.util.UUID;

/** Identifies the person or non-player system responsible for a state change. */
public record Actor(String name, String uuid) {
    public Actor {
        CampaignValidation.text(name, "actor name");
        if (name.length() > 64) {
            throw new IllegalArgumentException("actor name must be at most 64 characters");
        }
        if (uuid != null) {
            try {
                UUID.fromString(uuid);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("actor uuid must be a UUID when present", exception);
            }
        }
    }
}
