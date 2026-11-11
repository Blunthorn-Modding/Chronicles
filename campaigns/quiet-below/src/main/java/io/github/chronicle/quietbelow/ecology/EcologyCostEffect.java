package io.github.chronicle.quietbelow.ecology;

/** Transient provenance on our own native effects; never serialized or sent as a custom packet. */
public interface EcologyCostEffect {
    boolean quietBelow$isContactCost();
    void quietBelow$markContactCost();
    void quietBelow$yieldContactCost();
}
