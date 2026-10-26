package io.github.chronicle.core.departures;

import java.util.Objects;
import java.util.UUID;

/** Permanent campaign-terminal outcome caused by one committed departure. */
public record CampaignConclusion(UUID operationId, DepartureReceipt receipt, String concludedAt) {
    public CampaignConclusion {
        Objects.requireNonNull(operationId, "conclusion operation ID is required");
        Objects.requireNonNull(receipt, "conclusion receipt is required");
        if (receipt.disposition() != DepartureReceipt.Disposition.SAVED) throw new IllegalArgumentException("campaign conclusion requires saved native evidence");
        concludedAt = DepartureValidation.instant(concludedAt, "campaign conclusion time");
    }
}
