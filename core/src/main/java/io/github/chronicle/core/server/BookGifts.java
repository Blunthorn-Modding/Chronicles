package io.github.chronicle.core.server;

import io.github.chronicle.core.books.BookDefinition;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.content.InventorySnapshot;
import io.github.chronicle.core.menu.CoreMenuModel.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Explicit GM copies bypass automatic spawning rules and retain the first discovery forever. */
public final class BookGifts {
    private BookGifts() { }
    public static BookStatus status(MinecraftServer server,String bookId) {
        var d=ChestAuthoring.service(server).state().bookDelivery().deliveries().get(bookId);
        if(d==null||d.firstDiscovery()==null)return new BookStatus(false,"","","",false);
        var found=d.firstDiscovery();var id=UUID.fromString(found.playerUuid());var player=server.getPlayerManager().getPlayer(id);
        String name=player==null?server.getApiServices().nameToIdCache().getByUuid(id).map(p->p.name()).orElse(""):player.getName().getString();
        return new BookStatus(true,name,found.playerUuid(),found.discoveredAt(),player!=null);
    }
    public static List<Entry> recipients(MinecraftServer server) {
        return PlayRuntime.eligiblePlayers(server).stream().map(p->new Entry(p.getUuidAsString(),p.getName().getString(),"Active participant","PLAYER","",0)).toList();
    }
    public static void requireActive(ServerCommandSource source) {
        var server=source.getServer();
        if(!source.hasPermissionLevel(2)||source.getPlayer()==null)throw new IllegalStateException("Giving lore books requires an operator.");
        if(ChronicleRuntime.service(server).state().phase()!=SessionPhase.ACTIVE||RuntimeHolds.reason(server)!=null)
            throw new IllegalStateException("Giving lore books requires active play without an operation hold.");
    }
    public static ServerPlayerEntity recipient(MinecraftServer server,String id) {
        UUID uuid;try{uuid=UUID.fromString(id);}catch(IllegalArgumentException e){throw new IllegalArgumentException("Choose an online active participant.");}
        return PlayRuntime.eligiblePlayers(server).stream().filter(p->p.getUuid().equals(uuid)).findFirst()
                .orElseThrow(()->new IllegalStateException("That player is no longer an online active participant. Reopen the player list."));
    }
    public static BookGiveForm form(ServerCommandSource source,BookDefinition book,String targetId) {
        requireActive(source);var players=recipients(source.getServer());String name="",reason="";boolean can=false;
        if(!targetId.isBlank()) {var target=recipient(source.getServer(),targetId);name=target.getName().getString();can=slot(target)>=0;if(!can)reason=name+" has no empty inventory slot.";}
        else if(players.isEmpty())reason="No online active participants. Add participants under Players first.";
        return new BookGiveForm(book.id(),book.title(),players,targetId,name,can,reason);
    }
    private static int slot(ServerPlayerEntity player) {for(int i=0;i<36;i++)if(player.getInventory().getStack(i).isEmpty())return i;return -1;}
    public static String give(ServerCommandSource source,BookDefinition expected,String targetId) throws IOException {
        requireActive(source);var server=source.getServer();
        if(expected==null||!expected.equals(BookRuntime.books(server).get(expected.id())))throw new IllegalStateException("Book changed. Review the current book before giving it.");
        var target=recipient(server,targetId);int slot=slot(target);if(slot<0)throw new IllegalStateException(target.getName().getString()+" has no empty inventory slot. Nothing was given.");
        var stack=ChestAuthoring.decode(server,new InventorySnapshot(1,List.of(BookDeliveryRuntime.stackJson(server,expected)))).getFirst();
        var actor=source.getPlayer();
        // Intent and conservative issuance precede inventory mutation. No automatic retry or offline queue.
        ChronicleRuntime.service(server).recordOperation(new Actor(source.getName(),actor.getUuidAsString()),"book.give.requested",expected.id(),Map.of("player",target.getUuidAsString(),"operation",UUID.randomUUID().toString()));
        var service=ChestAuthoring.service(server);service.claimOperatorIssuance(expected.id());
        String discovery=BookDeliveryRuntime.prepareDiscovery(server,expected.id(),service.deliveryToken(expected.id()),target.getUuid(),EventSources.active(target));
        try {
            target.getInventory().setStack(slot,stack);target.getInventory().markDirty();target.currentScreenHandler.sendContentUpdates();
            server.getPlayerManager().saveAllPlayerData();
            service.recordDiscovery(expected.id(),service.deliveryToken(expected.id()),target.getUuidAsString(),Instant.now().toString());
            EventSources.complete(server,discovery,true);
        } catch(IOException|RuntimeException e){
            if(!(e instanceof EventSources.Unavailable))RuntimeHolds.hold(server,"chronicle_core:book_delivery","A GM book delivery outcome is uncertain. Inspect the recipient inventory and server log before issuing another copy.");throw e;
        }
        return "Gave one copy of "+expected.title()+" to "+target.getName().getString()+". Original discovery history retained.";
    }
}
