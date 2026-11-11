package io.github.chronicle.core.offering;

import java.util.UUID;

/** Durable immutable accepted terms, independent of later catalogue edits. */
public record OfferingReceipt(UUID transaction, UUID player, String source, String offering, OfferingTerms terms,
                              long acceptedAtTicks,long cooldownUntilTicks) {
    public OfferingReceipt(UUID transaction,UUID player,String source,String offering,OfferingTerms terms) {
        this(transaction,player,source,offering,terms,0,0);
    }
    public OfferingReceipt {
        if (transaction == null || player == null || terms == null) throw new IllegalArgumentException("complete offering receipt required");
        OfferingDefinition.validId(source);
        OfferingDefinition.validId(offering);
        terms.validatePurchase();
        if(acceptedAtTicks<0||cooldownUntilTicks<acceptedAtTicks)throw new IllegalArgumentException("invalid captured offering cooldown");
    }
}
