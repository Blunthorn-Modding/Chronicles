package io.github.chronicle.core.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

/** Client transport and rebindable entry point for the server-authoritative authoring screen. */
public final class AuthoringUiClient {
    private static final KeyBinding OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chronicle.authoring.open", InputUtil.Type.KEYSYM, InputUtil.GLFW_KEY_O,
            ChronicleCoreClient.KEY_CATEGORY));

    private AuthoringUiClient() { }

    /** Called by the core client initializer; payload registration remains on the common side. */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AuthoringUiClient::tick);
    }

    private static void tick(MinecraftClient client) {
        while (OPEN_KEY.wasPressed()) {
            if (client.player == null) return;
            if (ClientPlayNetworking.canSend(io.github.chronicle.core.network.CoreMenuPayloads.Request.ID)) {
                ClientPlayNetworking.send(new io.github.chronicle.core.network.CoreMenuPayloads.Request(
                        io.github.chronicle.core.menu.CoreMenuModel.Action.REOPEN,
                        io.github.chronicle.core.menu.CoreMenuModel.Page.SESSION, "", "", 0, 0, "", ""));
                return;
            }
            chat(client,"Install the matching Chronicle client and server mods to open the menu.");
        }
    }

    static void chat(MinecraftClient client, String message) {
        if (client.player != null) client.inGameHud.getChatHud().addMessage(Text.literal(message));
    }

}
