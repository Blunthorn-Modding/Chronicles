package io.github.chronicle.core.client;

import io.github.chronicle.core.network.ItemEnchantingPayloads;
import io.github.chronicle.core.screen.ItemEnchantingScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public final class ItemEnchantingClient {
    private ItemEnchantingClient() { }
    public static void register() {
        HandledScreens.register(ItemEnchantingScreenHandler.TYPE, ItemEnchantingScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(ItemEnchantingPayloads.View.ID,
                (view, context) -> context.client().execute(() -> receive(context.client(), view)));
    }
    private static void receive(MinecraftClient client, ItemEnchantingPayloads.View view) {
        if (client.player == null || !(client.currentScreen instanceof ItemEnchantingScreen screen)
                || client.player.currentScreenHandler != screen.getScreenHandler()) return;
        screen.accept(view);
    }
}
