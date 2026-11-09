package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.lastcouncil.network.TreatyPayloads.*;
import io.github.chronicle.lastcouncil.network.TreatyPayloads;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

/** Tracks the opening request and one uninterrupted lectern screen flow. */
public final class TreatyClient {
    private TreatyClient() { }
    private record Opening(UUID id,Screen origin,int expires) { }
    private static Opening pending;
    private static int tick;
    private static TreatyScreen root;
    private static Screen child;
    public static void register(java.util.function.BooleanSupplier active){
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(active.getAsBoolean()&&world.isClient()&&hand==Hand.MAIN_HAND&&world.getBlockState(hit.getBlockPos()).isOf(Blocks.LECTERN)&&ClientPlayNetworking.canSend(Request.ID)){
                MinecraftClient client=MinecraftClient.getInstance();
                if(client.currentScreen==null){UUID id=UUID.randomUUID();pending=new Opening(id,client.currentScreen,tick+200);send(new Request(id,Action.OPEN,0,TreatyPayloads.NONE,0,hit.getBlockPos(),"","",false,List.of(),0));}
            }
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{tick++;if(pending!=null&&pending.expires()<tick)pending=null;if(root!=null&&client.currentScreen!=root&&client.currentScreen!=child){send(closeRequest(root.view().token()));root=null;child=null;}});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{pending=null;root=null;child=null;tick=0;});
        ClientPlayNetworking.registerGlobalReceiver(View.ID,(view,context)->context.client().execute(()->receive(view,context.client())));
    }
    private static void receive(View view,MinecraftClient client){
        if(view.opening()){
            Opening opening=pending;
            if(opening!=null&&opening.id().equals(view.request()))pending=null;
            if(opening==null||!opening.id().equals(view.request())||opening.expires()<tick||opening.origin()!=client.currentScreen){if(view.token()!=0)send(closeRequest(view.token()));return;}
            if(!view.active())return;
            root=new TreatyScreen(view,opening.origin());child=null;client.setScreen(root);
            send(new Request(UUID.randomUUID(),Action.READY,view.token(),TreatyPayloads.NONE,0,BlockPos.ORIGIN,"","",false,List.of(),0));
        }else if(root!=null&&view.token()==root.view().token()){
            if(view.closed()){
                TreatyScreen revoked=root;boolean inFlow=client.currentScreen==revoked||client.currentScreen==child;
                root=null;child=null;
                if(inFlow)client.setScreen(client.player!=null&&client.world!=null?revoked.parent:null);
            }else if(client.currentScreen==root||client.currentScreen==child)root.accept(view);
        }
    }
    static void child(Screen screen){child=screen;}
    static void clearChild(){child=null;}
    public static TreatyScreen currentRoot(){return root;}
    public static View currentView(){return root==null?null:root.view();}
    static void closed(TreatyScreen screen){if(root==screen){send(closeRequest(root.view().token()));root=null;child=null;}}
    static void send(Request request){if(ClientPlayNetworking.canSend(Request.ID))ClientPlayNetworking.send(request);}
    private static Request closeRequest(long token){return new Request(UUID.randomUUID(),Action.CLOSE,token,TreatyPayloads.NONE,0,BlockPos.ORIGIN,"","",false,List.of(),0);}
}
