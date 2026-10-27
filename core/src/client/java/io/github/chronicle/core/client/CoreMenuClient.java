package io.github.chronicle.core.client;

import io.github.chronicle.core.network.CoreMenuPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/** Transport for the server-authoritative Chronicle control menu.  The server decides when it opens. */
public final class CoreMenuClient {
    private static long closedToken;
    private static CoreMenuScreen.NavigationSnapshot navigation;
    private CoreMenuClient() { }

    public static void register() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{closedToken=0;navigation=null;});
        ClientPlayNetworking.registerGlobalReceiver(CoreMenuPayloads.Response.ID,
                (payload, context) -> context.client().execute(() -> receive(context.client(), payload)));
    }

    static void receive(MinecraftClient client, CoreMenuPayloads.Response response) {
        io.github.chronicle.core.client.testing.CoreMenuSmoke.onView(response.view());
        io.github.chronicle.core.client.testing.BooksLoreSmoke.onView(response.view());
        io.github.chronicle.core.client.testing.PlayerRolesSmoke.onView(response.view());
        io.github.chronicle.core.client.testing.EventEditorSmoke.onView(response.view());
        if (client.currentScreen instanceof CoreMenuScreen screen) {
            screen.accept(response.view());
        } else if (client.currentScreen instanceof CatalogAddPopup popup) {
            if (popup.parentScreen() instanceof EventEditorScreen editor) {
                editor.parentMenu().acceptWhileEditorOpen(response.view());
                popup.acceptServerView(response.view());
            } else {
                if(popup.parentScreen() instanceof PlayerRoleEditorScreen editor&&editor.parentMenu()!=null){editor.parentMenu().acceptWhileEditorOpen(response.view());editor.serverUpdated(response.view());}
                popup.acceptServerView(response.view());
            }
        } else if (client.currentScreen instanceof EventEditorScreen editor) {
            editor.parentMenu().acceptWhileEventEditorOpen(editor, response.view());
        } else if (client.currentScreen instanceof PlayerRoleEditorScreen editor && editor.parentMenu() != null) {
            editor.parentMenu().acceptWhileEditorOpen(response.view()); editor.serverUpdated(response.view());
        } else if (client.currentScreen instanceof PlayerRoleChoiceScreen choice && choice.parentMenu() instanceof CoreMenuScreen parent) {
            boolean same = parent.state().token() == response.view().token();
            parent.acceptWhileEditorOpen(response.view()); choice.serverUpdated(same);
        } else if (client.currentScreen instanceof BookGiveScreen give && give.parentMenu() instanceof CoreMenuScreen parent) {
            // A background refresh must keep the menu current, while the popup becomes read-only if its frame changed.
            parent.acceptWhileEditorOpen(response.view());
            give.serverUpdated(response.view());
        } else if (client.currentScreen instanceof LootTableCreateScreen creator) {
            creator.parentMenu().acceptWhileEditorOpen(response.view());
            creator.serverUpdated(response.view());
        } else if (client.currentScreen instanceof BookEditorScreen editor && editor.parentMenu() != null) {
            // A refresh arriving behind a local editor must advance the menu frame, but never close the editor.
            editor.parentMenu().acceptWhileEditorOpen(response.view());
        } else if (response.open() && response.view().token() != closedToken) {
            client.setScreen(new CoreMenuScreen(response.view()));
        }
    }

    static void remember(CoreMenuScreen screen){navigation=screen.navigationSnapshot();}
    static void restore(CoreMenuScreen screen){if(navigation!=null)screen.restoreNavigation(navigation);}

    /** A late mutation response must not re-open an operator's deliberately closed menu. */
    static void closed(long token) {
        closedToken = token;
        if (ClientPlayNetworking.canSend(CoreMenuPayloads.Request.ID)) {
            ClientPlayNetworking.send(new CoreMenuPayloads.Request(io.github.chronicle.core.menu.CoreMenuModel.Action.CLOSE,
                    io.github.chronicle.core.menu.CoreMenuModel.Page.SESSION, "", "", token, 0, "", ""));
        }
    }
}
