package io.github.chronicle.core.campaign;

/** Produces a proposed state change without touching Minecraft or other external systems. */
@FunctionalInterface
public interface EventHandler {
    EventOutcome handle(CampaignState snapshot);
}
