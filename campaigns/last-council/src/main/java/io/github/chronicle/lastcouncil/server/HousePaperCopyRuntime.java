package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.server.*;
import io.github.chronicle.lastcouncil.content.HousePaperBooks;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.Notice;
import io.github.chronicle.lastcouncil.network.PaperCopyPayloads.Request;
import java.io.IOException;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;

/** No recipient selector: every paper is resolved from the authenticated connection's cast. */
public final class HousePaperCopyRuntime {
    private static final Map<ServerPlayerEntity,Long> LAST=new WeakHashMap<>();
    private HousePaperCopyRuntime() { }
    public static void register(){
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(q,c)->c.server().execute(()->copy(c.player(),q)));
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->LAST.remove(h.player));
    }
    static String refusal(ServerPlayerEntity player){
        var server=player.getEntityWorld().getServer();
        if(!CouncilRuntime.active()||server.getPlayerManager().getPlayer(player.getUuid())!=player)return "House papers are unavailable.";
        if(ChronicleRuntime.service(server).state().phase()!=SessionPhase.ACTIVE||SessionBuilding.enabled(server)||RuntimeHolds.reason(server)!=null||!PlayRuntime.eligibleIds(server).contains(player.getUuid()))return "Physical copies require active participation outside Editing and Building Mode.";
        var cast=CouncilRuntime.committedCast(server);
        if(cast==null||!cast.papers().containsKey(player.getUuid()))return "Your House papers have not been issued yet.";
        return "";
    }
    private static void copy(ServerPlayerEntity player,Request q){
        var server=player.getEntityWorld().getServer();if(!server.isOnThread()||server.getPlayerManager().getPlayer(player.getUuid())!=player||!CouncilRuntime.active())return;
        long now=System.nanoTime();Long prior=LAST.get(player);if(prior!=null&&now-prior<250_000_000L){notice(player,q,"Wait for the previous copy request.");return;}LAST.put(player,now);
        try{
            String reason=refusal(player);if(!reason.isBlank())throw new IllegalStateException(reason);
            var cast=CouncilRuntime.committedCast(server);
            var books=HousePaperBooks.build(CouncilRuntime.content(server),cast,player.getUuid(),q.section(),q.index());
            notice(player,q,PersonalCopies.give(player,q.request(),"last_council:papers",cast.castId()+"/"+q.section()+"/"+q.index(),books));
        }catch(IOException failure){notice(player,q,"The copy could not be confirmed. Preserve your inventory and ask an operator to check copy recovery.");}
        catch(RuntimeException failure){notice(player,q,Objects.toString(failure.getMessage(),"The copy is unavailable."));}
    }
    private static void notice(ServerPlayerEntity player,Request q,String text){if(ServerPlayNetworking.canSend(player,Notice.ID))ServerPlayNetworking.send(player,new Notice(q.request(),text.substring(0,Math.min(512,text.length()))));}
}
