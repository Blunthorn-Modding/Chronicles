package io.github.chronicle.quietbelow.content;

/** Transient provenance for Night Vision supplied by an active Altar claim. */
public interface AltarSightEffect {
    boolean quietBelow$isAltarSight();
    void quietBelow$markAltarSight();
    void quietBelow$yieldAltarSight();
}
