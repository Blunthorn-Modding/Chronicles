package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.lastcouncil.network.ConflictPayloads;
import io.github.chronicle.lastcouncil.network.ConflictPayloads.*;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

/** One server-token-bound public register or in-person offer; never a remote target selector. */
public final class ConflictClient {
    private static ConflictScreen root;private static Screen child;
    private ConflictClient(){}
    public static void register(){
        ClientPlayNetworking.registerGlobalReceiver(View.ID,(v,c)->c.client().execute(()->{
            var client=c.client();if(v.opening()){if(client.currentScreen instanceof HandledScreen<?>){send(new Request(UUID.randomUUID(),Action.CLOSE,v.token(),ConflictPayloads.NONE,0,"","",0));return;}root=new ConflictScreen(v);child=null;client.setScreen(root);}
            else if(root!=null&&root.view().token()==v.token()&&(client.currentScreen==root||client.currentScreen==child)){if(v.closed()){root=null;child=null;client.setScreen(null);}else if(root.view().challenge()!=null&&v.challenge()==null){root=new ConflictScreen(v);child=null;client.setScreen(root);}else root.accept(v);}
        }));
        ClientTickEvents.END_CLIENT_TICK.register(c->{if(root!=null&&c.currentScreen!=root&&c.currentScreen!=child){closed(root);}});
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{root=null;child=null;});
    }
    static void send(Request q){if(ClientPlayNetworking.canSend(Request.ID))ClientPlayNetworking.send(q);}
    static void child(Screen s){child=s;}
    static void closed(ConflictScreen s){if(root==s){send(new Request(UUID.randomUUID(),Action.CLOSE,s.view().token(),ConflictPayloads.NONE,0,"","",0));root=null;child=null;}}
    public static ConflictScreen currentRoot(){return root;}
    public static View currentView(){return root==null?null:root.view();}
}
