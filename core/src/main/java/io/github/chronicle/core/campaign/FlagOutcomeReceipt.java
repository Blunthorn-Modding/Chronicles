package io.github.chronicle.core.campaign;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Durable identity of an authorized Event flag transaction, independent of bounded audit history. */
public record FlagOutcomeReceipt(UUID operationId, String eventId, String definitionFingerprint,
                                 Map<String, Boolean> changes) {
    public static final int MAX_FLAG_CHANGES = 32;

    public FlagOutcomeReceipt {
        if (operationId == null) {
            throw new IllegalArgumentException("operation ID is required");
        }
        CampaignValidation.identifier(eventId, "event ID");
        if (definitionFingerprint == null || !definitionFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("definition fingerprint must be a lowercase SHA-256 hash");
        }
        CampaignValidation.flagMap(changes, "event flag changes");
        if (changes.isEmpty() || changes.size() > MAX_FLAG_CHANGES) {
            throw new IllegalArgumentException("event flag changes must contain between 1 and " + MAX_FLAG_CHANGES + " entries");
        }
        changes = Map.copyOf(new LinkedHashMap<>(changes));
    }
}
