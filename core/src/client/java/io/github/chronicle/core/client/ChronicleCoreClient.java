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
        ClientPlayNetworking.registerGlobalReceiver(io.github.chronicle.core.network.SessionStatePayload.ID,
                (payload, context) -> context.client().execute(() ->
                        io.github.chronicle.core.server.SessionGameplay.clientSurvivalState(payload.survivalFrozen())));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                io.github.chronicle.core.server.SessionGameplay.clientSurvivalState(null));
        ClientServerPresets.register();
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
        io.github.chronicle.core.client.testing.BooksLoreSmoke.register();
        io.github.chronicle.core.client.testing.PlayerRolesSmoke.register();
        io.github.chronicle.core.client.testing.ScenarioProfileSmoke.register();
        BundledSoundHandshakeClient.register();
        io.github.chronicle.core.client.testing.FoundationVerificationSmoke.register();
        io.github.chronicle.core.client.testing.AreaChangesSmoke.register();
        io.github.chronicle.core.client.testing.EventEditorSmoke.register();
        io.github.chronicle.core.client.testing.EventActionsSmoke.register();
        io.github.chronicle.core.client.testing.EncounterActionsSmoke.register();
        io.github.chronicle.core.client.testing.EventRecoverySmoke.register();
        io.github.chronicle.core.client.testing.EventProgressionSmoke.register();
        io.github.chronicle.core.client.testing.EventSourcesSmoke.register();
        io.github.chronicle.core.client.testing.DeparturesSmoke.register();
        io.github.chronicle.core.client.testing.SubtypesSmoke.register();
        DepartureClient.register();
        io.github.chronicle.core.client.testing.EnchantmentCapsSmoke.register();
        io.github.chronicle.core.client.testing.PauseGameplaySmoke.register();
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
