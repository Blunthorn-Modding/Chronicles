package io.github.chronicle.quietbelow.client;

import io.github.chronicle.quietbelow.QuietBelow;
import net.fabricmc.api.ClientModInitializer;

/** Client-only bootstrap reserved for campaign visuals and interactions. */
public final class QuietBelowClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlock(QuietBelow.ANCIENT_FORGE_BLOCK,
                net.minecraft.client.render.BlockRenderLayer.CUTOUT);
        net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlock(QuietBelow.ANCIENT_ALTAR_BLOCK,
                net.minecraft.client.render.BlockRenderLayer.CUTOUT);
        net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlock(QuietBelow.RELIC_HEART,
                net.minecraft.client.render.BlockRenderLayer.CUTOUT);
        SculkAppearanceHandshakeClient.register();
        QuietBelowCompatibilitySmoke.register();
        QuietBelowContentSmoke.registerIfRequested();
        QuietBelowEcologySmoke.register();
        AncientForgeSmoke.register();
        AncientAltarSmoke.register();
        EcologyHeartSmoke.register();
        EcologyHeartReproSmoke.register();
        EcologyHeartDefianceSmoke.register();
        HeartAirCutSmoke.register();
        EradicationRecoverySmoke.register();
        ManagedBlocksSmoke.register();
        QuietBelowRecoverySmoke.register();
        QuietBelowCostsSmoke.register();
        QuietBelowCitySmoke.register();
        QuietBelowWardenSmoke.register();
        QuietBelowStageSmoke.register();
        EcologySpreadSmoke.register();
        QuietBelow.LOGGER.info("The Quiet Below client initialized");
    }
}
