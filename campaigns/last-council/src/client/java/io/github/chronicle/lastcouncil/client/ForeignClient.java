package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.lastcouncil.network.ForeignPayloads.*;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

/** Token-bound foreign registry and setup editor. */
public final class ForeignClient {
    private static ForeignScreen root; private static Screen child;
    private ForeignClient(){}
    public static void register(){
        ClientPlayNetworking.registerGlobalReceiver(View.ID,(v,c)->c.client().execute(()->{
            var client=c.client();
            if(v.opening()){
                if(client.currentScreen instanceof HandledScreen<?>){send(new Request(UUID.randomUUID(),Action.CLOSE,v.token(),0,"","",""));return;}
                root=new ForeignScreen(v);child=null;client.setScreen(root);
            } else if(root!=null&&root.view().token()==v.token()&&(client.currentScreen==root||client.currentScreen==child)){
                if(v.closed()){root=null;child=null;client.setScreen(null);}else root.accept(v);
            }
        }));
        ClientTickEvents.END_CLIENT_TICK.register(c->{if(root!=null&&c.currentScreen!=root&&c.currentScreen!=child)closed(root);});
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{root=null;child=null;});
    }
    public static View currentView(){return root==null?null:root.view();}
    public static void open(){send(new Request(UUID.randomUUID(),Action.OPEN,0,0,"","",""));}
    static void send(Request request){if(ClientPlayNetworking.canSend(Request.ID))ClientPlayNetworking.send(request);}
    static void child(Screen screen){child=screen;}
    static void closed(ForeignScreen screen){if(root==screen){send(new Request(UUID.randomUUID(),Action.CLOSE,screen.view().token(),0,"","",""));root=null;child=null;}}
}
