package io.github.chronicle.core.client;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.ui.AuthoringUiModel.Tab;
import io.github.chronicle.core.network.AuthoringUiPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Client transport and rebindable entry point for the server-authoritative authoring screen. */
public final class AuthoringUiClient {
    private static final KeyBinding OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chronicle.authoring.open", InputUtil.Type.KEYSYM, InputUtil.GLFW_KEY_O,
            ChronicleCoreClient.KEY_CATEGORY));

    private AuthoringUiClient() { }

    /** Called by the core client initializer; payload registration remains on the common side. */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(AuthoringUiPayloads.View.ID,
                (payload, context) -> context.client().execute(() -> receiveView(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(AuthoringUiPayloads.Notice.ID,
                (payload, context) -> context.client().execute(() -> receiveNotice(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(AuthoringUiClient::tick);
    }

    private static void tick(MinecraftClient client) {
        while (OPEN_KEY.wasPressed()) {
            if (client.player == null) return;
            if (ClientPlayNetworking.canSend(io.github.chronicle.core.network.CoreMenuPayloads.Request.ID)) {
                ClientPlayNetworking.send(new io.github.chronicle.core.network.CoreMenuPayloads.Request(
                        io.github.chronicle.core.menu.CoreMenuModel.Action.OPEN,
                        io.github.chronicle.core.menu.CoreMenuModel.Page.SESSION, "", "", 0, 0, "", ""));
                return;
            }
            if (!ClientPlayNetworking.canSend(AuthoringUiPayloads.Request.ID)) {
                chat(client, "Authoring UI is unavailable on this server.");
                return;
            }
            if (client.currentScreen instanceof AuthoringScreen screen) {
                screen.openCurrentTab();
            } else {
                ClientPlayNetworking.send(new AuthoringUiPayloads.Request(
                        io.github.chronicle.core.authoring.ui.AuthoringUiModel.Action.OPEN, "", "", 0, Tab.CHESTS, 0));
            }
        }
    }

    static void receiveView(MinecraftClient client, AuthoringUiPayloads.View view) {
        io.github.chronicle.core.client.testing.FabricRegressionSmoke.onAuthoringView(view.state());
        if (client.currentScreen instanceof AuthoringScreen screen) {
            screen.accept(view.state());
        } else if (view.open()) {
            client.setScreen(new AuthoringScreen(view.state()));
        }
    }

    static void receiveNotice(MinecraftClient client, AuthoringUiPayloads.Notice notice) {
        io.github.chronicle.core.client.testing.FabricRegressionSmoke.onAuthoringNotice(notice.message());
        if (client.currentScreen instanceof AuthoringScreen screen) {
            screen.notice(notice.message());
            if (looksLikePermissionFailure(notice.message())) client.setScreen(null);
        } else {
            chat(client, notice.message());
        }
    }

    static void chat(MinecraftClient client, String message) {
        if (client.player != null) client.inGameHud.getChatHud().addMessage(Text.literal(message));
    }

    private static boolean looksLikePermissionFailure(String message) {
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("permission") || normalized.contains("operator") || normalized.contains("not authorized")
                || normalized.contains("access denied") || normalized.contains("denied");
    }
}
