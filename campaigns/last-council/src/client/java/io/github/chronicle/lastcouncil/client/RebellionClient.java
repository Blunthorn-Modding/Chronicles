package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.lastcouncil.network.RebellionPayloads.*;
import io.github.chronicle.lastcouncil.network.RebellionPayloads;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Correlates a public registry session with its originating screen. */
final class RebellionClient {
    private static UUID opening,pending; private static Action pendingAction; private static RebellionScreen.Declare draft; private static CouncilScreen owner; private static Screen child; private static View view; private static boolean visit,invalid; private static int age; private static final Set<UUID> abandoned=new HashSet<>();
    static void register(){
        ClientPlayNetworking.registerGlobalReceiver(View.ID,(v,c)->c.client().execute(()->receive(v)));
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{opening=null;pending=null;owner=null;child=null;view=null;abandoned.clear();});
        ClientTickEvents.END_CLIENT_TICK.register(c->{if(owner!=null&&c.currentScreen!=owner&&c.currentScreen!=child){close();return;}if((opening!=null||pending!=null)&&++age>200){if(opening!=null)abandoned.add(opening);opening=null;pending=null;invalid=true;if(draft!=null){draft.message("Response delayed. Refresh before retrying.");child(draft);}else if(owner!=null){owner.message("Server response delayed. Close and reopen before retrying.");if(c.currentScreen==owner)owner.rebuildPreservingFocus();}}});
    }
    static void open(CouncilScreen parent){start(parent,false);}
    static void visits(CouncilBusinessScreen parent){start(parent,true);}
    private static void start(CouncilScreen parent,boolean picker){
        if(opening!=null||!ClientPlayNetworking.canSend(Request.ID))return;
        close();opening=UUID.randomUUID();age=0;invalid=false;owner=parent;visit=picker;view=null;pending=null;
        ClientPlayNetworking.send(new Request(opening,Action.OPEN,0,0,RebellionPayloads.NONE,"",""));
    }
    private static void receive(View next){
        MinecraftClient client=MinecraftClient.getInstance();
        if(next.opening()){
            if(abandoned.remove(next.request())){if(ClientPlayNetworking.canSend(Request.ID))ClientPlayNetworking.send(new Request(UUID.randomUUID(),Action.CLOSE,next.token(),next.revision(),RebellionPayloads.NONE,"",""));return;}
            if(opening!=null&&opening.equals(next.request())){
                opening=null;view=next;
                if(client.currentScreen!=owner){close();return;}
                if(visit){visit=false;pickVisit();}else owner.rebuildPreservingFocus();
            }else if(opening==null){view=next;owner=new RebellionScreen();child=null;pending=null;client.setScreen(owner);}
            return;
        }
        if(view==null||next.token()!=view.token())return;
        if(!next.request().equals(RebellionPayloads.NONE)&&!next.request().equals(pending))return;
        boolean reply=next.request().equals(pending); Action completed=reply?pendingAction:null;
        if(reply){pending=null;pendingAction=null;}
        if(next.revision()<view.revision()){if(reply&&completed==Action.DECLARE&&draft!=null){if(next.saved())draft=null;else{draft.message(next.refusal().isBlank()?next.message():next.refusal());child(draft);}}return;}
        if(reply&&completed==Action.STATE){invalid=false;if(draft!=null){draft.message("State refreshed. Review availability before retrying.");if(client.currentScreen==draft)draft.rebuildPreservingFocus();}}
        if(reply&&completed==Action.DECLARE&&draft!=null){
            if(next.saved())draft=null;
            else {draft.message(next.refusal().isBlank()?next.message():next.refusal());child(draft);}
        }
        view=next;
        if(draft!=null&&client.currentScreen==draft)draft.rebuildPreservingFocus();
        if(owner!=null){owner.message(next.refusal().isBlank()?next.message():next.refusal());if(client.currentScreen==owner)owner.rebuildPreservingFocus();}
    }
    static View view(){return view;}
    static boolean busy(){return invalid||pending!=null||opening!=null;}
    static void send(Action action,UUID faction,String statement,String house){
        if(view==null||busy()||!ClientPlayNetworking.canSend(Request.ID))return;
        pending=UUID.randomUUID();pendingAction=action;age=0;ClientPlayNetworking.send(new Request(pending,action,view.token(),view.revision(),faction,statement,house));
        if(owner!=null)owner.rebuildPreservingFocus();
    }
    static boolean needsRefresh(){return invalid;}
    static void retainDraft(RebellionScreen.Declare value){draft=value;}
    static void abandonDraft(){draft=null;}
    static void refresh(){if(view==null||pending!=null||!ClientPlayNetworking.canSend(Request.ID))return;pending=UUID.randomUUID();pendingAction=Action.STATE;age=0;ClientPlayNetworking.send(new Request(pending,Action.STATE,view.token(),view.revision(),view.detail()==null?RebellionPayloads.NONE:view.detail().id(),"",""));}
    static void child(Screen screen){child=screen;MinecraftClient.getInstance().setScreen(screen);}
    static void returnToOwner(){child=null;MinecraftClient.getInstance().setScreen(owner);}
    static void close(){if(opening!=null)abandoned.add(opening);if(view!=null&&ClientPlayNetworking.canSend(Request.ID))ClientPlayNetworking.send(new Request(UUID.randomUUID(),Action.CLOSE,view.token(),view.revision(),RebellionPayloads.NONE,"",""));view=null;owner=null;child=null;opening=null;pending=null;pendingAction=null;draft=null;invalid=false;}
    private static void pickVisit(){
        if(view==null||owner==null)return;
        if(view.visits().isEmpty()){owner.message("No rebel Houses are available for a visit.");owner.rebuildPreservingFocus();return;}
        child(new RebellionVisitPicker(owner));
    }
}
