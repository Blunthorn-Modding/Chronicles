package io.github.chronicle.core.client;

import io.github.chronicle.core.network.ItemEnchantingPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/** Client transport for the server-authoritative item enchanting workshop. */
public final class ItemEnchantingClient {
    private static ItemEnchantingScreen openScreen;

    private ItemEnchantingClient() { }

    /** Called by the core client initializer; the server owns every workshop offer. */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ItemEnchantingPayloads.View.ID,
                (payload, context) -> context.client().execute(() -> receive(context.client(), payload)));
    }

    private static void receive(MinecraftClient client, ItemEnchantingPayloads.View view) {
        if (client.currentScreen instanceof ItemEnchantingScreen screen) {
            if(view.opening()&&!view.token().equals(screen.state().token())){
                openScreen=new ItemEnchantingScreen(view);client.setScreen(openScreen);return;
            }
            openScreen = screen;
            screen.accept(view);
            return;
        }
        // A completed response can arrive after the player deliberately closed
        // the workshop. Never reopen a completed transaction from that response.
        if (view.completed() || !view.opening()) return;
        openScreen = new ItemEnchantingScreen(view);
        client.setScreen(openScreen);
    }

    static void closed(ItemEnchantingScreen screen) {
        if (openScreen == screen) openScreen = null;
    }
}
