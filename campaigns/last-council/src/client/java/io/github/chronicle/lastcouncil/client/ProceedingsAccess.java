package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.core.client.ChronicleCoreClient;
import io.github.chronicle.core.client.TextInputGuard;
import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import io.github.chronicle.lastcouncil.network.ProceedingsPayloads.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.screen.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Explicit Chancellor access, independent of the motion lectern and operator privilege. */
final class ProceedingsAccess {
    private static final KeyBinding OPEN=KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.last_council.proceedings.open",InputUtil.Type.KEYSYM,InputUtil.GLFW_KEY_I,ChronicleCoreClient.KEY_CATEGORY));
    private record Opening(Screen expected,int expires) { }
    private static final Map<UUID,Opening> pending=new HashMap<>();
    private static BooleanSupplier enabled;
    private static int tick,cooldown;
    private ProceedingsAccess(){ }
    static KeyBinding binding(){return OPEN;}
    static void register(BooleanSupplier allowed) {
        enabled=allowed;
        ClientTickEvents.END_CLIENT_TICK.register(client->{tick++;if(cooldown>0)cooldown--;pending.values().removeIf(o->o.expires()<tick);while(OPEN.wasPressed())open(client);});
        ScreenEvents.AFTER_INIT.register((client,screen,w,h)->ScreenKeyboardEvents.allowKeyPress(screen).register((current,input)->!OPEN.matchesKey(input)||!open(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{pending.clear();tick=0;cooldown=0;});
    }
    private static boolean open(MinecraftClient client) {
        if(enabled==null||!enabled.getAsBoolean()||client.player==null||cooldown>0||!ClientPlayNetworking.canSend(Request.ID))return false;
        if(client.currentScreen instanceof ConfirmScreen||TextInputGuard.editingText(client.currentScreen))return false;
        if(client.currentScreen instanceof HandledScreen<?> container){container.close();if(client.currentScreen!=null)return true;}
        UUID id=UUID.randomUUID();expectOpening(id,client.currentScreen);cooldown=10;
        ClientPlayNetworking.send(new Request(id,Action.STATE,0,CouncilPayloads.NONE,"open","","",false,false));return true;
    }
    static void expectOpening(UUID id,Screen expected){if(pending.size()>=32)pending.clear();pending.put(id,new Opening(expected,tick+200));}
    static boolean openResponse(View view,MinecraftClient client) {
        if(enabled==null||!enabled.getAsBoolean()||!view.chancellor())return false;
        Opening opening=pending.remove(view.request());
        if(opening==null){if(!view.request().equals(CouncilPayloads.NONE)||client.currentScreen!=null)return false;}
        else if(opening.expires()<tick||opening.expected()!=client.currentScreen)return false;
        client.setScreen(new CouncilBusinessScreen(view,client.currentScreen));return true;
    }
}
