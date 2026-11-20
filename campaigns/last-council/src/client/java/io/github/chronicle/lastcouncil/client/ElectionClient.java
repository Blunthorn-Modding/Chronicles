package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.lastcouncil.network.ElectionPayloads;
import io.github.chronicle.lastcouncil.network.ElectionPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;

/** One correlated election session, owned by the screen that opened it. */
final class ElectionClient {
    private static UUID opening,pending; private static Action pendingAction; private static CouncilScreen owner;
    private static ElectionReasonScreen child; private static View view; private static int age; private static boolean invalid;
    private static final Set<UUID> abandoned=new HashSet<>();
    static void register(){
        ClientPlayNetworking.registerGlobalReceiver(View.ID,(v,c)->c.client().execute(()->receive(v)));
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{opening=null;pending=null;owner=null;child=null;view=null;abandoned.clear();invalid=false;});
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(owner!=null&&c.currentScreen!=owner&&c.currentScreen!=child){close();return;}
            if((opening!=null||pending!=null)&&++age>200){
                if(opening!=null)abandoned.add(opening);opening=null;pending=null;pendingAction=null;invalid=true;
                String message="Response delayed. Close and reopen before retrying.";
                if(child!=null){child.message(message);if(c.currentScreen==child)child.rebuildPreservingFocus();}
                else if(owner!=null){owner.message(message);if(c.currentScreen==owner)owner.rebuildPreservingFocus();}
            }
        });
    }
    static void open(CouncilScreen screen){close();if(!ClientPlayNetworking.canSend(Request.ID)){screen.message("Election is unavailable.");return;}owner=screen;opening=UUID.randomUUID();age=0;ClientPlayNetworking.send(new Request(opening,Action.OPEN,0,0,"",0));}
    private static void receive(View next){
        var client=MinecraftClient.getInstance();
        if(next.opening()){
            if(abandoned.remove(next.request())){closeToken(next);return;}
            if(opening==null||!opening.equals(next.request())){closeToken(next);return;}
            opening=null;if(client.currentScreen!=owner){closeToken(next);close();return;}view=next;owner.rebuildPreservingFocus();return;
        }
        if(view==null||owner==null||next.token()!=view.token())return;
        if(!next.request().equals(ElectionPayloads.NONE)&&!next.request().equals(pending))return;
        boolean reply=pending!=null&&next.request().equals(pending);Action completed=reply?pendingAction:null;
        if(reply){pending=null;pendingAction=null;}
        if(next.revision()>=view.revision())view=next;
        if(reply&&child!=null&&(completed==Action.WITHDRAW||completed==Action.END)){
            if(next.saved()){child=null;if(client.currentScreen instanceof ElectionReasonScreen)client.setScreen(owner);}
            else {child.message(next.message().isBlank()?"The election changed. Review and try again.":next.message());if(client.currentScreen==child)child.rebuildPreservingFocus();}
        }
        if(owner!=null&&client.currentScreen==owner){if(reply&&!next.message().isBlank())owner.message(next.message());owner.rebuildPreservingFocus();}
    }
    static View view(){return view;}
    static boolean owns(CouncilScreen screen){return owner==screen;}
    static boolean busy(){return invalid||opening!=null||pending!=null;}
    static void send(Action action,String reason,int page){
        if(view==null||busy()||!ClientPlayNetworking.canSend(Request.ID))return;
        pending=UUID.randomUUID();pendingAction=action;age=0;ClientPlayNetworking.send(new Request(pending,action,view.token(),view.revision(),reason,page));
        var c=MinecraftClient.getInstance();if(child!=null&&c.currentScreen==child)child.rebuildPreservingFocus();else if(owner!=null&&c.currentScreen==owner)owner.rebuildPreservingFocus();
    }
    static void reason(Action action){if(owner==null||view==null)return;child=new ElectionReasonScreen(owner,action,view.ownCandidate());MinecraftClient.getInstance().setScreen(child);}
    static void returnToOwner(){child=null;MinecraftClient.getInstance().setScreen(owner);}
    private static void closeToken(View v){if(ClientPlayNetworking.canSend(Request.ID))ClientPlayNetworking.send(new Request(UUID.randomUUID(),Action.CLOSE,v.token(),v.revision(),"",v.page()));}
    static void close(){if(opening!=null)abandoned.add(opening);if(view!=null)closeToken(view);opening=null;pending=null;pendingAction=null;owner=null;child=null;view=null;invalid=false;}
}
