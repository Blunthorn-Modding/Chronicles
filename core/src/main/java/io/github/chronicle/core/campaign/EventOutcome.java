package io.github.chronicle.core.campaign;

import java.util.LinkedHashMap;
import java.util.Map;

/** The flag changes proposed by a campaign event handler. */
public record EventOutcome(Map<String, Boolean> flagChanges, String message) {
    public EventOutcome {
        CampaignValidation.flagMap(flagChanges, "event flag changes");
        flagChanges = Map.copyOf(new LinkedHashMap<>(flagChanges));
        CampaignValidation.text(message, "event message");
    }
}
