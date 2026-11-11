package io.github.chronicle.core.client;

import io.github.chronicle.core.network.DeparturePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/** Receives only server-authored departure reviews; late replies never reopen a closed screen. */
public final class DepartureClient {
    private DepartureClient() { }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DeparturePayloads.View.ID,
                (view, context) -> context.client().execute(() -> receive(context.client(), view)));
    }

    static void receive(MinecraftClient client, DeparturePayloads.View view) {
        if (client.currentScreen instanceof DepartureConfirmScreen current) {
            if (!view.opening()) {
                current.accept(view);
                return;
            }
            if (view.stage() == 2 && current.state().stage() == 1
                    && view.parentToken().equals(current.state().token())) {
                client.setScreen(DepartureConfirmScreen.finalReview(view, current));
                return;
            }
            if (view.stage() == 1) {
                client.setScreen(DepartureConfirmScreen.first(view));
            }
            return;
        }
        if (view.opening() && !view.completed() && view.stage() == 1)
            client.setScreen(DepartureConfirmScreen.first(view));
    }
}
