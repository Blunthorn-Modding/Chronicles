package io.github.chronicle.core.client;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.network.ProfilePayloads;
import io.github.chronicle.core.client.testing.FabricRegressionSmoke;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;

/** Client-only dossier key binding and profile packet handlers. */
public final class ChronicleCoreClient implements ClientModInitializer {
    public static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.create(Identifier.of(ChronicleCore.MOD_ID, "chronicle"));
    private static final KeyBinding DOSSIER_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chronicle.profile.open",
            InputUtil.Type.KEYSYM,
            InputUtil.GLFW_KEY_K,
            KEY_CATEGORY));

    @Override
    public void onInitializeClient() {
        AuthoringPreviewRenderer.register();
        AuthoringUiClient.register();
        CoreMenuClient.register();
        ItemEnchantingClient.register();
        OfferingScreen.register();
        ActionConfirmationScreen.register();
        io.github.chronicle.core.client.hud.FoodHud.register();
        ClientPlayNetworking.registerGlobalReceiver(ProfilePayloads.View.ID,
                (payload, context) -> context.client().execute(() -> receiveView(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(ProfilePayloads.Notice.ID,
                (payload, context) -> context.client().execute(() -> receiveNotice(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ChronicleCoreClient::openOwnDossierWhenRequested);
        DossierVisualSmoke.registerIfRequested();
        FabricRegressionSmoke.register();
        io.github.chronicle.core.client.testing.CoreMenuSmoke.register();
        io.github.chronicle.core.client.testing.EnchantmentCapsSmoke.register();
        ChronicleCore.LOGGER.info("Chronicle Core client initialized");
    }

    private static void openOwnDossierWhenRequested(MinecraftClient client) {
        while (DOSSIER_KEY.wasPressed()) {
            if (client.player == null) {
                return;
            }
            if (!ClientPlayNetworking.canSend(ProfilePayloads.Request.ID)) {
                DossierScreen.showNotice(client, "chronicle.profile.error.unsupported");
                return;
            }
            ClientPlayNetworking.send(new ProfilePayloads.Request(client.player.getUuid()));
        }
    }

    /** Called on the client thread after the server has authorized a public profile view. */
    public static void receiveView(MinecraftClient client, ProfilePayloads.View profile) {
        FabricRegressionSmoke.onView(profile);
        if (client.currentScreen instanceof DossierScreen screen && screen.owner().equals(profile.owner())) {
            screen.accept(profile);
            return;
        }
        if (profile.saved()) {
            return;
        }
        client.setScreen(new DossierScreen(profile));
    }

    /** Called on the client thread for bounded, server-selected profile feedback. */
    public static void receiveNotice(MinecraftClient client, ProfilePayloads.Notice notice) {
        FabricRegressionSmoke.onNotice(notice);
        DossierScreen.showNotice(client, notice.translationKey());
    }
}
