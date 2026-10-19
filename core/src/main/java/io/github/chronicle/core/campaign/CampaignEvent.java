package io.github.chronicle.core.campaign;

import java.util.LinkedHashMap;
import java.util.Map;

/** Metadata and pure handler for a campaign action. */
public record CampaignEvent(String id, String description, boolean onceOnly, Map<String, Boolean> requiredFlags,
                            EventHandler handler) {
    public CampaignEvent {
        CampaignValidation.identifier(id, "event ID");
        CampaignValidation.text(description, "event description");
        CampaignValidation.flagMap(requiredFlags, "event required flags");
        requiredFlags = Map.copyOf(new LinkedHashMap<>(requiredFlags));
        if (handler == null) {
            throw new IllegalArgumentException("event handler is required");
        }
    }
}
