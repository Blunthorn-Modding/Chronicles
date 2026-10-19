package io.github.chronicle.core.campaign;

import java.util.LinkedHashMap;
import java.util.Map;

/** An immutable record of one durable campaign-state mutation. */
public record AuditEntry(long sequence, String timestamp, Actor actor, String action, int sessionNumber,
                         String target, Map<String, String> parameters) {
    public AuditEntry {
        if (sequence < 0) {
            throw new IllegalArgumentException("audit sequence must be non-negative");
        }
        CampaignValidation.utcTimestamp(timestamp);
        if (actor == null) {
            throw new IllegalArgumentException("audit actor is required");
        }
        CampaignValidation.text(action, "audit action");
        CampaignValidation.sessionNumber(sessionNumber);
        CampaignValidation.text(target, "audit target");
        if (parameters == null || parameters.size() > 64) {
            throw new IllegalArgumentException("audit parameters may contain at most 64 entries");
        }
        LinkedHashMap<String, String> copied = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            CampaignValidation.text(entry.getKey(), "audit parameter key");
            CampaignValidation.text(entry.getValue(), "audit parameter value");
            copied.put(entry.getKey(), entry.getValue());
        }
        parameters = Map.copyOf(copied);
    }
}
