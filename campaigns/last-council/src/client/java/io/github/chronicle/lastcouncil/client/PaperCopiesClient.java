package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.lastcouncil.network.CouncilPayloads.*;
import io.github.chronicle.lastcouncil.network.PaperCopyPayloads;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Copy acknowledgements never replace a newer screen or select another recipient. */
final class PaperCopiesClient {
    private record Pending(PersonalScreen owner,UUID actor,long created) { }
    private static final Map<UUID,Pending> REQUESTS=new LinkedHashMap<>();
    private PaperCopiesClient() { }
    static void register(){ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->REQUESTS.clear());}
    static void request(PersonalScreen owner,Section section,int index){
        var client=MinecraftClient.getInstance();
        if(client.player==null||!ClientPlayNetworking.canSend(PaperCopyPayloads.Request.ID)){owner.feedback="Install the matching Last Council client/server.";return;}
        long now=System.nanoTime();REQUESTS.entrySet().removeIf(e->now-e.getValue().created()>600_000_000_000L);
        if(REQUESTS.size()>=16)REQUESTS.remove(REQUESTS.keySet().iterator().next());
        UUID id=UUID.randomUUID();REQUESTS.put(id,new Pending(owner,client.player.getUuid(),now));owner.copyStarted(id);
        ClientPlayNetworking.send(new PaperCopyPayloads.Request(id,section,index));
    }
    static boolean notice(Notice notice,MinecraftClient client){
        Pending p=REQUESTS.remove(notice.request());if(p==null)return false;
        if(client.player==null||!client.player.getUuid().equals(p.actor()))return true;
        p.owner().copyNotice(notice);if(client.currentScreen!=p.owner())client.player.sendMessage(Text.literal(notice.message()),false);
        return true;
    }
}
