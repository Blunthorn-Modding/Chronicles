package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.network.OfferingPayloads;
import io.github.chronicle.core.offering.OfferingDefinition;
import io.github.chronicle.core.screen.OfferingScreenHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.*;

/** Reusable handled workstation; durable trades and one-use current-state authority stay server-side. */
public final class LastingBargains {
    private static final Map<String,List<Choice>> GROUPS=new TreeMap<>();
    private static final Map<MinecraftServer,Map<UUID,Offer>> OFFERS=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Integer> CURSORS=new IdentityHashMap<>();
    private static final long MAX_AGE=60_000_000_000L;
    private static boolean frozen;
    private LastingBargains() { }
    public record Choice(OfferingDefinition definition,String label,String description,String cost,String bonus) {
        public Choice {
            if(definition==null||label==null||label.isBlank()||label.length()>64||description==null||description.isBlank()
                    ||description.length()>512)
                throw new IllegalArgumentException("Invalid bargain choice");
            OfferingPayloads.term(cost);OfferingPayloads.term(bonus);
        }
        public Choice(OfferingDefinition definition,String label,String description) {
            this(definition,label,description,"See complete trade details","See complete trade details");
        }
    }
    @FunctionalInterface public interface Gate {
        String unavailable(ServerPlayerEntity player);
        /** Temporary lack of power/eligibility leaves a readable workstation open. */
        default boolean stillPresent(ServerPlayerEntity player) { return player.isAlive()&&!player.isSpectator(); }
        default void completed(ServerPlayerEntity player) { }
    }
    public static void registerGroup(String id,List<Choice> choices) {
        OfferingDefinition.validId(id);
        if(frozen||GROUPS.size()>=32||GROUPS.containsKey(id)||choices==null||choices.isEmpty()||choices.size()>16)throw new IllegalStateException("Invalid or frozen bargain group");
        Set<String> ids=new HashSet<>();for(Choice choice:choices)if(!ids.add(choice.definition.id()))throw new IllegalArgumentException("Duplicate offering choice");
        GROUPS.put(id,List.copyOf(choices));
    }
    public static void register() {
        OfferingPayloads.register();OfferingScreenHandler.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server->frozen=true);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{OFFERS.remove(server);CURSORS.remove(server);});
        ServerDisconnects.register((handler,server)->{
            var offers=OFFERS.get(server);if(offers!=null){Offer offer=offers.get(handler.player.getUuid());
                if(offer!=null&&OfferIdentity.sameConnection(offer.connection,handler))offers.remove(handler.player.getUuid(),offer);}
        });
        ServerTickEvents.END_SERVER_TICK.register(LastingBargains::tick);
        ServerPlayNetworking.registerGlobalReceiver(OfferingPayloads.Action.ID,(payload,context)->context.server().execute(()->act(context.player(),payload)));
    }
    public static void open(ServerPlayerEntity player,String source,String group,String title,Gate gate) {
        MinecraftServer server=player.getEntityWorld().getServer();thread(server);OfferingDefinition.validId(source);
        if(!ServerPlayNetworking.canSend(player,OfferingPayloads.View.ID)){player.sendMessage(Text.literal("Install the matching Chronicle client to use this Altar."),false);return;}
        if(title==null||title.isBlank()||title.length()>80||gate==null||!GROUPS.containsKey(group))throw new IllegalArgumentException("Invalid bargain station/group");
        var offers=OFFERS.computeIfAbsent(server,ignored->new HashMap<>());
        if(offers.size()>=256&&!offers.containsKey(player.getUuid())){player.sendMessage(Text.literal("Too many open bargains; try again later."),false);return;}
        // Vanilla closes the old handler before creating this one. Publish only from createMenu,
        // so an old handler's onClosed cannot revoke a newly opened station.
        Offer[] opened=new Offer[1];
        player.openHandledScreen(new ExtendedScreenHandlerFactory<OfferingPayloads.View>() {
            @Override public Text getDisplayName(){return Text.literal(title);}
            @Override public ScreenHandler createMenu(int syncId,PlayerInventory inventory,PlayerEntity owner){
                Offer offer=make(player,source,group,title,gate,0,syncId,null,null,
                        "Choose a bargain. Purchases survive death; no player reversal.",true);
                var handler=new OfferingScreenHandler(syncId,inventory,offer.view);
                offer=offer.bound(handler);opened[0]=offer;offers.put(player.getUuid(),offer);return handler;
            }
            @Override public OfferingPayloads.View getScreenOpeningData(ServerPlayerEntity actor){
                if(opened[0]==null||actor!=player)throw new IllegalStateException("Offering opening identity changed");
                return opened[0].view;
            }
        });
    }
    public static boolean canUse(ServerPlayerEntity player,OfferingScreenHandler handler) {
        var offers=OFFERS.get(player.getEntityWorld().getServer());Offer offer=offers==null?null:offers.get(player.getUuid());
        return offer!=null&&offer.handler==handler&&OfferIdentity.sameConnection(offer.connection,player.networkHandler)&&offer.gate.stillPresent(player);
    }
    public static void closed(ServerPlayerEntity player,OfferingScreenHandler handler) {
        var offers=OFFERS.get(player.getEntityWorld().getServer());if(offers==null)return;
        Offer offer=offers.get(player.getUuid());if(offer!=null&&offer.handler==handler&&OfferIdentity.sameConnection(offer.connection,player.networkHandler))offers.remove(player.getUuid(),offer);
    }
    private static OfferingPayloads.Stats current(ServerPlayerEntity p) {
        return new OfferingPayloads.Stats(p.getMaxHealth(),io.github.chronicle.core.stats.FoodCapacity.capacity(p),
                p.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED));
    }
    private static OfferingPayloads.Stats stats(OfferingStats.Totals t){return new OfferingPayloads.Stats(t.health(),t.food(),t.speed());}
    private static Offer make(ServerPlayerEntity p,String source,String group,String title,Gate gate,int selected,int syncId,
                              UUID parent,OfferingScreenHandler handler,String status,boolean opening) {
        String station=gate.unavailable(p);long revision=-1;
        io.github.chronicle.core.offering.OfferingLedger ledger=null;
        try{ledger=OfferingStats.ledger(p.getEntityWorld().getServer());revision=ledger.revision();
            if(ledger.closedSources().contains(source))station="This campaign source has ended.";
        }catch(RuntimeException error){station="Bargain authority unavailable; ask an operator to check it.";}
        var observed=current(p);List<OfferingPayloads.Choice> views=new ArrayList<>();List<OfferingStats.Preview> previews=new ArrayList<>();
        for(Choice choice:GROUPS.get(group)){
            OfferingStats.Preview preview=null;int count=ledger==null?0:ledger.count(p.getUuid(),source,choice.definition.id());
            String problem=station;
            try{preview=OfferingStats.preview(p,source,choice.definition);}catch(RuntimeException error){if(problem==null)problem=error.getMessage();}
            previews.add(preview);
            views.add(new OfferingPayloads.Choice(choice.label,count,choice.definition.maxPurchases(),choice.description,
                    choice.cost,choice.bonus,preview==null?observed:stats(preview.before()),preview==null?null:stats(preview.after()),problem==null?"":clip(problem,256)));
        }
        UUID token=UUID.randomUUID();
        var view=new OfferingPayloads.View(syncId,token,parent,title,clip(status,384),station==null,station==null?"":clip(station,384),
                views,selected,opening,false);
        return new Offer(token,System.nanoTime(),p.networkHandler,parent,source,group,title,gate,selected,
                Collections.unmodifiableList(previews),handler,view,revision,observed);
    }
    private static void replace(ServerPlayerEntity player,Offer previous,int selected,String status) {
        var offer=make(player,previous.source,previous.group,previous.title,previous.gate,selected,previous.handler.syncId,
                previous.token,previous.handler,status,false);
        OFFERS.get(player.getEntityWorld().getServer()).put(player.getUuid(),offer);offer.handler.view(offer.view);
        ServerPlayNetworking.send(player,offer.view);
    }
    public static void act(ServerPlayerEntity player,OfferingPayloads.Action action) {
        MinecraftServer server=player.getEntityWorld().getServer();thread(server);
        var offers=OFFERS.get(server);Offer offer=offers==null?null:offers.get(player.getUuid());
        if(offer==null||player.currentScreenHandler!=offer.handler||!OfferIdentity.accepts(offer.connection,offer.token,offer.parent,
                player.networkHandler,action.token(),action.action()==2||action.action()==3))return;
        if(action.action()==3){offers.remove(player.getUuid(),offer);player.closeHandledScreen();return;}
        offers.remove(player.getUuid(),offer); // A packet never purchases twice, even on IO/gate failure.
        if(action.action()==2){replace(player,offer,offer.selected,"Offer refreshed. Review the complete permanent trade.");return;}
        if(System.nanoTime()-offer.created>=MAX_AGE||offer.view.completed()){
            replace(player,offer,offer.selected,"Offer expired; review the refreshed trade before accepting.");return;
        }
        if(action.action()==0){
            if(action.selected()<GROUPS.get(offer.group).size())replace(player,offer,action.selected(),"Choose carefully: purchases survive death; no reversal.");
            else replace(player,offer,offer.selected,"That bargain is unavailable.");
            return;
        }
        if(action.selected()!=offer.selected){replace(player,offer,offer.selected,"Selection changed; review the current bargain.");return;}
        String problem=offer.gate.unavailable(player);OfferingStats.Preview expected=offer.previews.get(offer.selected);
        if(problem==null&&expected==null)problem=offer.view.choices().get(offer.selected).problem();
        if(problem!=null){replace(player,offer,offer.selected,"No bargain accepted. "+problem);return;}
        try{OfferingStats.purchase(player,offer.source,GROUPS.get(offer.group).get(offer.selected).definition,offer.token,expected);}
        catch(Exception error){
            ChronicleCore.LOGGER.warn("Bargain confirmation did not complete for {}: {}",player.getUuid(),error.toString());
            terminal(player,offer,"Could not finish confirmation. Refresh to check the authoritative purchase count.");return;
        }
        try{offer.gate.completed(player);}catch(Exception error){ChronicleCore.LOGGER.error("Bargain saved; cosmetic Altar feedback failed",error);}
        replace(player,offer,offer.selected,"Bargain accepted. Survives death; no player reversal.");
    }
    private static void terminal(ServerPlayerEntity player,Offer offer,String message) {
        var old=offer.view;
        var view=new OfferingPayloads.View(old.syncId(),old.token(),old.parent(),old.title(),message,old.stationReady(),old.stationProblem(),
                old.choices(),old.selected(),false,true);
        Offer next=offer.withView(view);OFFERS.get(player.getEntityWorld().getServer()).put(player.getUuid(),next);
        next.handler.view(view);ServerPlayNetworking.send(player,view);
    }
    /** Four bounded live checks per tick; unchanged screens do not rebuild or rotate their nonce. */
    private static void tick(MinecraftServer server) {
        var offers=OFFERS.get(server);if(offers==null||offers.isEmpty())return;
        List<Offer> sample=new ArrayList<>(offers.values());int cursor=CURSORS.getOrDefault(server,0)%sample.size();
        for(int n=0;n<Math.min(4,sample.size());n++){
            Offer offer=sample.get((cursor+n)%sample.size());ServerPlayerEntity player=server.getPlayerManager().getPlayer(offer.connection.player.getUuid());
            if(player==null||player.networkHandler!=offer.connection||player.currentScreenHandler!=offer.handler){offers.remove(offer.connection.player.getUuid(),offer);continue;}
            if(!offer.gate.stillPresent(player)){player.closeHandledScreen();continue;}
            if(System.nanoTime()-offer.created>=MAX_AGE){if(!offer.view.completed())terminal(player,offer,"Offer expired. Refresh to review it again.");continue;}
            try{
                String station=offer.gate.unavailable(player);var ledger=OfferingStats.ledger(server);
                if(ledger.closedSources().contains(offer.source))station="This campaign source has ended.";
                String oldProblem=offer.view.stationReady()?null:offer.view.stationProblem();
                if(ledger.revision()!=offer.revision||!current(player).equals(offer.observed)||!Objects.equals(station,oldProblem))
                    replace(player,offer,offer.selected,"Station or character changed; review the current trade.");
            }catch(RuntimeException error){if(!offer.view.completed())terminal(player,offer,"Bargain authority unavailable. Refresh after an operator repairs it.");}
        }
        CURSORS.put(server,(cursor+Math.min(4,sample.size()))%sample.size());
    }
    private static void thread(MinecraftServer server){if(!server.isOnThread())throw new IllegalStateException("Bargains require the server thread");}
    private static String clip(String value,int limit){if(value==null)return "Unavailable";return value.length()<=limit?value:value.substring(0,limit-3)+"...";}
    private record Offer(UUID token,long created,net.minecraft.server.network.ServerPlayNetworkHandler connection,UUID parent,
                         String source,String group,String title,Gate gate,int selected,List<OfferingStats.Preview> previews,
                         OfferingScreenHandler handler,OfferingPayloads.View view,long revision,OfferingPayloads.Stats observed) {
        Offer bound(OfferingScreenHandler value){return new Offer(token,created,connection,parent,source,group,title,gate,selected,previews,value,view,revision,observed);}
        Offer withView(OfferingPayloads.View value){return new Offer(token,created,connection,parent,source,group,title,gate,selected,previews,handler,value,revision,observed);}
    }
}
