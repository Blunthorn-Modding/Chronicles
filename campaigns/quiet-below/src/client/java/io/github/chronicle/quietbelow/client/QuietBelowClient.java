package io.github.chronicle.quietbelow.client;

import io.github.chronicle.quietbelow.QuietBelow;
import net.fabricmc.api.ClientModInitializer;

/** Client-only bootstrap reserved for campaign visuals and interactions. */
public final class QuietBelowClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        QuietBelowContentSmoke.registerIfRequested();
        QuietBelowEcologySmoke.register();
        QuietBelowWardenSmoke.register();
        QuietBelowStageSmoke.register();
        QuietBelow.LOGGER.info("The Quiet Below client initialized");
    }
}
