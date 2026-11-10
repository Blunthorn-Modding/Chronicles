package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.client.GameSetupClientActions;
import io.github.chronicle.lastcouncil.network.CatalogueEditorPayloads.*;
import io.github.chronicle.lastcouncil.network.CataloguePayloads.Category;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

final class CouncilCatalogueClient {
    private static CoreMenuScreen waiting;
    private static UUID opening;
    private static final java.util.Map<UUID,CouncilContentEditor> saving=new java.util.HashMap<>();
    private static CouncilContentEditor retained;
    static boolean saving(UUID request,CouncilContentEditor editor){if(!saving.isEmpty()||retained!=null)return false;saving.put(request,editor);return true;}
    static void saved(UUID request){saving.remove(request);}
    static void retain(CouncilContentEditor editor){retained=editor;}
    static void clearRetained(CouncilContentEditor editor){if(retained==editor)retained=null;}
    static boolean hasRetained(){return retained!=null;}
    static CouncilContentEditor retained(){return retained;}
    static void clearRetained(){retained=null;}
    private CouncilCatalogueClient() { }
    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client->{for(var editor:java.util.List.copyOf(saving.values()))editor.waitTick();});
        GameSetupClientActions.register("tool:last_council:catalogue", "open", new GameSetupClientActions.Handler() {
            public void open(CoreMenuScreen parent) {
                if (!ClientPlayNetworking.canSend(Request.ID)) return;
                waiting=parent; opening=UUID.randomUUID();
                ClientPlayNetworking.send(new Request(opening,Action.OPEN,Category.HOUSES,"",0,0,0,""));
            }
            public void cancel(CoreMenuScreen parent) { if(waiting==parent){waiting=null;opening=null;} }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{waiting=null;opening=null;saving.clear();retained=null;});
        ClientPlayNetworking.registerGlobalReceiver(View.ID,(view,context)->context.client().execute(()->{
            CouncilBallotsSmoke.catalogue(view);CouncilEditorSmoke.view(view);
            var client=context.client();
            var editor=saving.get(view.request());
            if(editor!=null && editor.acceptPending(view)) return;
            if(view.opening() && waiting!=null && client.currentScreen==waiting && view.request().equals(opening)) {
                for(var entry:java.util.List.copyOf(saving.entrySet()))if(entry.getValue().outcomeUncertain()){entry.getValue().reconciled();saving.remove(entry.getKey());}
                var parent=waiting;waiting=null;opening=null;client.setScreen(new CouncilCatalogueScreen(view,parent));
            } else if(client.currentScreen instanceof CouncilCatalogueScreen screen) screen.accept(view);
            else if(client.currentScreen instanceof CouncilContentEditor screen) screen.accept(view);
        }));
        ClientPlayNetworking.registerGlobalReceiver(Chunk.ID,(chunk,context)->context.client().execute(()->{
            CouncilEditorSmoke.chunk(chunk);
            if(context.client().currentScreen instanceof CouncilCatalogueScreen screen)screen.accept(chunk);
        }));
    }
}
