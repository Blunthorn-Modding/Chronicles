package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.client.GameSetupClientActions;
import io.github.chronicle.lastcouncil.network.BanquetPayloads;
import io.github.chronicle.lastcouncil.network.BanquetPayloads.*;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class BanquetClient {
    private static CoreMenuScreen waiting;
    private static UUID request;
    private static Request pendingGift;
    private static UUID pendingGiftWorld;
    private static String giftNotice="";
    static Request compatiblePendingGift(UUID worldId){return pendingGift!=null && !BanquetPayloads.NONE.equals(worldId) && worldId.equals(pendingGiftWorld)?pendingGift:null;}
    static boolean giftBlocked(UUID worldId){return BanquetPayloads.NONE.equals(worldId) || pendingGift!=null && compatiblePendingGift(worldId)==null;}
    static String takeGiftNotice(){String notice=giftNotice;giftNotice="";return notice;}
    static void trackGift(Request value,UUID worldId){if(giftBlocked(worldId) || pendingGift!=null)throw new IllegalStateException("Unresolved banquet gift");pendingGift=value;pendingGiftWorld=worldId;}
    private BanquetClient() { }
    public static void register() {
        GameSetupClientActions.register("tool:last_council:banquet", "open", new GameSetupClientActions.Handler() {
            public void open(CoreMenuScreen parent) { BanquetClient.open(parent); }
            public void cancel(CoreMenuScreen parent) { if(waiting==parent){waiting=null;request=null;} }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{waiting=null;request=null;if(pendingGift!=null){giftNotice="A banquet supply request is uncertain. Reconnect to the original world to retry it.";if(client.player!=null)client.player.sendMessage(net.minecraft.text.Text.literal(giftNotice),false);}});
        ClientPlayNetworking.registerGlobalReceiver(View.ID,(view,context)->context.client().execute(()->{
            BanquetSmoke.view(view);
            var client=context.client();
            if(!view.opening() && pendingGift!=null && pendingGift.request().equals(view.request()) && view.worldId().equals(pendingGiftWorld) && !view.held()){
                pendingGift=null;pendingGiftWorld=null;giftNotice=view.message().isBlank()?"Banquet supply request completed.":view.message();
                if(!(client.currentScreen instanceof BanquetScreen) && client.player!=null)client.player.sendMessage(net.minecraft.text.Text.literal(giftNotice),false);
            }
            if(view.opening() && waiting!=null && client.currentScreen==waiting && view.request().equals(request)) {
                var parent=waiting;waiting=null;request=null;client.setScreen(new BanquetScreen(view,parent));
            } else if(view.opening() && view.logView() && view.canLog() && client.currentScreen==null) {
                client.setScreen(new BanquetScreen(view,null));
            } else if(!view.opening() && client.currentScreen instanceof BanquetScreen screen) screen.accept(view);
        }));
    }
    public static UUID open(CoreMenuScreen parent) {
        if(!ClientPlayNetworking.canSend(Request.ID))return null;
        waiting=parent;request=UUID.randomUUID();ClientPlayNetworking.send(new Request(request,Action.OPEN,0,null,BanquetPayloads.NONE,0,0,0));return request;
    }
}
