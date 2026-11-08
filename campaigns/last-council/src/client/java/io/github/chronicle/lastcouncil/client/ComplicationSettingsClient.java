package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.client.GameSetupClientActions;
import io.github.chronicle.lastcouncil.network.ComplicationPayloads.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import java.util.*;

public final class ComplicationSettingsClient {
    private static CoreMenuScreen waiting;
    private static UUID request;
    private ComplicationSettingsClient() { }
    public static void register(){
        GameSetupClientActions.register("tool:last_council:complications", "open", new GameSetupClientActions.Handler() {
            public void open(CoreMenuScreen parent) { ComplicationSettingsClient.open(parent); }
            public void cancel(CoreMenuScreen parent) { if (waiting == parent) { waiting = null; request = null; } }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{waiting=null;request=null;});
        ClientPlayNetworking.registerGlobalReceiver(View.ID,(view,context)->context.client().execute(()->{
            var client=context.client(); CouncilBallotsSmoke.complicationSettings(view);
            if(view.opening()&&waiting!=null&&client.currentScreen==waiting&&view.request().equals(request)) {
                var parent=waiting;waiting=null;request=null;client.setScreen(new ComplicationSettingsScreen(view,parent));
            } else if(!view.opening()&&client.currentScreen instanceof ComplicationSettingsScreen screen)screen.accept(view);
        }));
    }
    public static UUID open(CoreMenuScreen parent) {
        if(!ClientPlayNetworking.canSend(Request.ID))return null;
        waiting=parent;request=UUID.randomUUID();ClientPlayNetworking.send(new Request(request,Action.OPEN,0,null,List.of(),""));return request;
    }
}
