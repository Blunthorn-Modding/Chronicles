package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.network.OfferingPayloads;
import io.github.chronicle.core.offering.OfferingDefinition;
import io.github.chronicle.core.offering.OfferingCooldown;
import io.github.chronicle.core.offering.OfferingRefusal;
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
    private static final Map<String,OfferingPayloads.Presentation> PRESENTATIONS=new TreeMap<>();
    private static final Map<String,OfferingPayloads.Layout> LAYOUTS=new TreeMap<>();
    private static final Map<String,java.util.function.LongSupplier> COOLDOWNS=new TreeMap<>();
    private static final Map<MinecraftServer,Map<UUID,Offer>> OFFERS=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Integer> CURSORS=new IdentityHashMap<>();
    private static final long MAX_AGE=60_000_000_000L;
    private static boolean frozen;
    private LastingBargains() { }
    public record Choice(OfferingDefinition definition,TemporaryOfferings.Definition temporary,String label,String description,String cost,String bonus) {
        public Choice {
            if((definition==null)==(temporary==null)||label==null||label.isBlank()||label.length()>64||description==null||description.isBlank()
                    ||description.length()>512)
                throw new IllegalArgumentException("Invalid bargain choice");
            OfferingPayloads.term(cost);OfferingPayloads.term(bonus);
        }
        public Choice(OfferingDefinition definition,String label,String description) {
            this(definition,null,label,description,"See complete trade details","See complete trade details");
        }
        public Choice(OfferingDefinition definition,String label,String description,String cost,String bonus) {
            this(definition,null,label,description,cost,bonus);
        }
        public Choice(TemporaryOfferings.Definition definition,String label,String description) { this(null,definition,label,description,"Hidden temporary terms","Hidden temporary terms"); }
        String id(){return definition!=null?definition.id():temporary.id();}
    }
    @FunctionalInterface public interface Gate {
        String unavailable(ServerPlayerEntity player);
        /** Temporary lack of power/eligibility leaves a readable workstation open. */
        default boolean stillPresent(ServerPlayerEntity player) { return player.isAlive()&&!player.isSpectator(); }
        default TemporaryOfferings.Station station(ServerPlayerEntity player) { throw new IllegalStateException("This station has no temporary-offering context"); }
        /** Called only for a NEW durable receipt, after projection. Never retried on refresh/replay/login.
         * Implement gameplay aftermath before private flavor; a hard crash can omit this transient callback. */
        default void committed(ServerPlayerEntity player,OfferingDefinition definition,UUID transaction) { }
        /** Opt-in campaign refusal behavior; technical/ineligible attempts never call refused. */
        default boolean gameplayRefusals(){return false;}
        default String invalidAttempt(ServerPlayerEntity player){return unavailable(player);}
        default long configurationRevision(){return 0;}
        default void refused(ServerPlayerEntity player,String reason){ }
        default String refusalFeedback(){return "Offering refused.";}
    }
    public static void registerCooldown(String source,java.util.function.LongSupplier duration){
        OfferingDefinition.validId(source);Objects.requireNonNull(duration);
        if(frozen||COOLDOWNS.size()>=32||COOLDOWNS.putIfAbsent(source,duration)!=null)throw new IllegalStateException("Invalid or frozen offering cooldown policy");
    }
    static long cooldownTicks(String source){var policy=COOLDOWNS.get(source);long ticks=policy==null?0:policy.getAsLong();if(ticks<0||ticks>20L*86400*30)throw new IllegalStateException("Invalid offering cooldown policy");return ticks;}
    static void validateCooldown(MinecraftServer server,UUID player,String source){
        if(!COOLDOWNS.containsKey(source))return;
        var lasting=OfferingStats.ledger(server);var temporary=TemporaryOfferings.ledger(server);
        if(lasting.closedSources().contains(source)||temporary.closedSources().contains(source))throw new IllegalStateException("Offering source has ended");
        PlayRuntime.service(server);cooldownTicks(source);
        if(temporary.unresolved().stream().anyMatch(c->c.player().equals(player)))throw new IllegalStateException("An offering awaits manual review");
        if(OfferingCooldown.until(lasting,temporary,player,source)>PlayRuntime.elapsedTicks(server))throw new OfferingRefusal("The previous prayer has not faded.");
    }
    static void validateSource(MinecraftServer server,String source){
        if(COOLDOWNS.containsKey(source)&&(OfferingStats.ledger(server).closedSources().contains(source)||TemporaryOfferings.ledger(server).closedSources().contains(source)))throw new IllegalStateException("Offering source has ended");
    }
    public static void registerGroup(String id,List<Choice> choices){registerGroup(id,choices,OfferingPayloads.Presentation.neutral());}
    public static List<EventSources.Choice> eventSourceChoices(MinecraftServer server) {
        List<EventSources.Choice> result=new ArrayList<>();result.add(new EventSources.Choice("*","Any completed prayer","Any registered prayer"));
        GROUPS.values().forEach(choices->choices.forEach(choice->result.add(new EventSources.Choice(choice.id(),choice.label,choice.description))));
        return List.copyOf(result);
    }
    public static void registerGroup(String id,List<Choice> choices,OfferingPayloads.Presentation presentation) { registerGroup(id,choices,presentation,OfferingPayloads.Layout.GENERIC); }
    public static void registerGroup(String id,List<Choice> choices,OfferingPayloads.Presentation presentation,OfferingPayloads.Layout layout) {
        Objects.requireNonNull(presentation);
        Objects.requireNonNull(layout);
        OfferingDefinition.validId(id);
        if(frozen||GROUPS.size()>=32||GROUPS.containsKey(id)||choices==null||choices.isEmpty()||choices.size()>16)throw new IllegalStateException("Invalid or frozen bargain group");
        Set<String> ids=new HashSet<>();for(Choice choice:choices)if(!ids.add(choice.id()))throw new IllegalArgumentException("Duplicate offering choice");
        if(layout==OfferingPayloads.Layout.ALTAR_FIVE&&choices.size()!=5)throw new IllegalArgumentException("Altar layout requires five choices");
        GROUPS.put(id,List.copyOf(choices));PRESENTATIONS.put(id,presentation);LAYOUTS.put(id,layout);
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
        if(!ServerPlayNetworking.canSend(player,OfferingPayloads.View.ID)){player.sendMessage(Text.literal("Install the matching Chronicle client to use this workstation."),false);return;}
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
                        false,true);
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
    private static OfferingStats.Totals current(ServerPlayerEntity p) {
        return new OfferingStats.Totals(p.getMaxHealth(),io.github.chronicle.core.stats.FoodCapacity.capacity(p),
                p.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED),
                p.getAttributeValue(io.github.chronicle.core.stats.OfferingAttributes.EXHAUSTION_MULTIPLIER));
    }
    private static Offer make(ServerPlayerEntity p,String source,String group,String title,Gate gate,int selected,int syncId,
                              UUID parent,OfferingScreenHandler handler,boolean answered,boolean opening) {
        boolean refusals=gate.gameplayRefusals();
        String station;try{station=refusals?gate.invalidAttempt(p):gate.unavailable(p);}catch(RuntimeException error){station="Bargain authority unavailable";}long revision=-1,temporaryRevision=-1;
        io.github.chronicle.core.offering.OfferingLedger ledger=null;
        try{ledger=OfferingStats.ledger(p.getEntityWorld().getServer());revision=ledger.revision();
            if(ledger.closedSources().contains(source))station="Campaign source ended";
        }catch(RuntimeException error){station="Bargain authority unavailable";}
        try{var temporary=TemporaryOfferings.ledger(p.getEntityWorld().getServer());temporaryRevision=temporary.revision();if(temporary.closedSources().contains(source))station="Campaign source ended";}
        catch(RuntimeException error){if(refusals)station="Bargain authority unavailable";}
        var observed=current(p);List<OfferingPayloads.Choice> views=new ArrayList<>();List<Object> previews=new ArrayList<>();
        for(Choice choice:GROUPS.get(group)){
            Object preview=null;boolean complete=false;
            try{
                if(refusals&&station==null)preview=evaluate(p,source,gate,choice);
                else if(!refusals){
                    if(choice.definition!=null){int count=ledger==null?0:ledger.count(p.getUuid(),source,choice.definition.id());complete=count>=choice.definition.maxPurchases();if(!complete)preview=OfferingStats.preview(p,source,choice.definition);}
                    else preview=TemporaryOfferings.preview(p,source,choice.temporary,gate.station(p));
                }
            }catch(RuntimeException error){if(refusals)station="Bargain authority unavailable";}
            previews.add(preview);
            views.add(new OfferingPayloads.Choice(choice.label,station==null&&preview!=null&&!complete,complete));
        }
        if(station!=null&&refusals)views.replaceAll(choice->new OfferingPayloads.Choice(choice.label(),false,false));
        var state=station!=null?OfferingPayloads.State.UNAVAILABLE:
                views.stream().allMatch(OfferingPayloads.Choice::complete)?OfferingPayloads.State.EXHAUSTED:
                views.stream().anyMatch(OfferingPayloads.Choice::available)?OfferingPayloads.State.READY:OfferingPayloads.State.UNAVAILABLE;
        if(answered)state=OfferingPayloads.State.ANSWERED;
        UUID token=UUID.randomUUID();
        var view=new OfferingPayloads.View(syncId,token,parent,title,state,views,opening,PRESENTATIONS.get(group),LAYOUTS.get(group));
        return new Offer(token,System.nanoTime(),p.networkHandler,parent,source,group,title,gate,selected,
                Collections.unmodifiableList(previews),handler,view,revision,temporaryRevision,observed,station,gate.configurationRevision());
    }
    private static Object evaluate(ServerPlayerEntity player,String source,Gate gate,Choice choice){
        String problem=gate.unavailable(player);if(problem!=null)return new Refused(problem);
        try{return choice.definition!=null?OfferingStats.preview(player,source,choice.definition):TemporaryOfferings.preview(player,source,choice.temporary,gate.station(player));}
        catch(OfferingRefusal refusal){return new Refused(refusal.getMessage());}
    }
    private static void replace(ServerPlayerEntity player,Offer previous,int selected,boolean answered) {replace(player,previous,selected,answered,"");}
    private static void replace(ServerPlayerEntity player,Offer previous,int selected,boolean answered,String feedback) {
        var offer=make(player,previous.source,previous.group,previous.title,previous.gate,selected,previous.handler.syncId,
                previous.token,previous.handler,answered,false);
        if(!feedback.isEmpty()){var view=offer.view;offer=offer.withView(new OfferingPayloads.View(view.syncId(),view.token(),view.parent(),view.title(),view.state(),view.choices(),view.opening(),view.presentation(),view.layout(),feedback));}
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
        if(action.action()==2){replace(player,offer,offer.selected,false);return;}
        if(System.nanoTime()-offer.created>=MAX_AGE||offer.view.state()==OfferingPayloads.State.EXPIRED){
            replace(player,offer,offer.selected,false);return;
        }
        if(action.action()==0){replace(player,offer,offer.selected,false);return;}
        int selected=action.selected();
        if(selected>=GROUPS.get(offer.group).size()){replace(player,offer,offer.selected,false);return;}
        Object expected=offer.previews.get(selected);
        Choice choice=GROUPS.get(offer.group).get(selected);OfferingDefinition definition=choice.definition;
        if(offer.gate.gameplayRefusals()){
            try{
                if(offer.view.state()!=OfferingPayloads.State.READY||!offer.view.choices().get(selected).available()
                        ||offer.gate.invalidAttempt(player)!=null||offer.configurationRevision!=offer.gate.configurationRevision()
                        ||OfferingStats.ledger(server).revision()!=offer.revision||TemporaryOfferings.ledger(server).revision()!=offer.temporaryRevision
                        ||!current(player).equals(offer.observed)){
                    replace(player,offer,selected,false);return;
                }
                OfferingStats.verifyAuthority(server);TemporaryOfferings.verifyAuthority(server);
                Object fresh=evaluate(player,offer.source,offer.gate,choice);
                if(fresh instanceof Refused refusal){
                    offer.gate.refused(player,refusal.reason);replace(player,offer,selected,false,offer.gate.refusalFeedback());return;
                }
                if(!Objects.equals(expected,fresh)){replace(player,offer,selected,false,"The altar's answer has shifted. Pray again.");return;}
            }catch(RuntimeException technical){terminal(player,offer);return;}
        }else if(offer.gate.unavailable(player)!=null||expected==null||!offer.view.choices().get(selected).available()){
            replace(player,offer,selected,false);return;
        }
        if(expected==null){replace(player,offer,selected,false);return;}
        if(choice.temporary!=null){
            try{TemporaryOfferings.purchase(player,offer.source,choice.temporary,offer.gate.station(player),offer.token,(TemporaryOfferings.Prepared)expected);replace(player,offer,selected,true);}
            catch(Exception error){ChronicleCore.LOGGER.warn("Temporary offering commit did not finish for {}: {}",player.getUuid(),error.toString());terminal(player,offer);}
            return;
        }
        OfferingStats.PurchaseResult result;
        String sourceReceipt=null;
        try{
            var source=new io.github.chronicle.core.play.EventSource("chronicle_core:prayer_completed",definition.id(),"");
            String authority="offering:"+offer.token;
            sourceReceipt=EventSources.prepare(player,EventSources.id(authority,source.type(),source.target(),""),source,authority);
            result=OfferingStats.purchase(player,offer.source,definition,offer.token,(OfferingStats.Preview)expected);
            EventSources.complete(server,sourceReceipt,true);
        }
        catch(Exception error){
            // A fresh-preview refusal happened before purchase. IO or a failed authority stays uncertain.
            if(!(error instanceof java.io.IOException)&&!(error instanceof EventSources.Unavailable))try {
                if(OfferingStats.ledger(server).receipts().stream().noneMatch(receipt->receipt.transaction().equals(offer.token)))EventSources.cancelAuthority(server,"offering:"+offer.token);
            }catch(RuntimeException uncertain){error.addSuppressed(uncertain);}
            ChronicleCore.LOGGER.warn("Offering commit did not finish for {}: {}",player.getUuid(),error.toString());
            terminal(player,offer);return;
        }
        if(result.newlyCommitted())try{offer.gate.committed(player,definition,result.transaction());}
        catch(Exception error){ChronicleCore.LOGGER.error("Offering is durably committed; transient aftermath did not finish and will not be retried",error);}
        replace(player,offer,selected,true);
    }
    private static void terminal(ServerPlayerEntity player,Offer offer) {
        var old=offer.view;
        var view=new OfferingPayloads.View(old.syncId(),old.token(),old.parent(),old.title(),OfferingPayloads.State.EXPIRED,old.choices(),false,old.presentation(),old.layout());
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
            if(System.nanoTime()-offer.created>=MAX_AGE){if(offer.view.state()!=OfferingPayloads.State.EXPIRED)terminal(player,offer);continue;}
            try{
                String station=offer.gate.gameplayRefusals()?offer.gate.invalidAttempt(player):offer.gate.unavailable(player);var ledger=OfferingStats.ledger(server);var temporary=TemporaryOfferings.ledger(server);
                if(ledger.closedSources().contains(offer.source)||temporary.closedSources().contains(offer.source))station="Campaign source ended";
                String oldProblem=offer.gateProblem;
                if(offer.configurationRevision!=offer.gate.configurationRevision()||ledger.revision()!=offer.revision||temporary.revision()!=offer.temporaryRevision||!current(player).equals(offer.observed)||!Objects.equals(station,oldProblem))
                    replace(player,offer,offer.selected,false);
            }catch(RuntimeException error){if(offer.view.state()!=OfferingPayloads.State.EXPIRED)terminal(player,offer);}
        }
        CURSORS.put(server,(cursor+Math.min(4,sample.size()))%sample.size());
    }
    private static void thread(MinecraftServer server){if(!server.isOnThread())throw new IllegalStateException("Bargains require the server thread");}
    private record Refused(String reason) { }
    private record Offer(UUID token,long created,net.minecraft.server.network.ServerPlayNetworkHandler connection,UUID parent,
                         String source,String group,String title,Gate gate,int selected,List<Object> previews,
                         OfferingScreenHandler handler,OfferingPayloads.View view,long revision,long temporaryRevision,OfferingStats.Totals observed,String gateProblem,long configurationRevision) {
        Offer bound(OfferingScreenHandler value){return new Offer(token,created,connection,parent,source,group,title,gate,selected,previews,value,view,revision,temporaryRevision,observed,gateProblem,configurationRevision);}
        Offer withView(OfferingPayloads.View value){return new Offer(token,created,connection,parent,source,group,title,gate,selected,previews,handler,value,revision,temporaryRevision,observed,gateProblem,configurationRevision);}
    }
}
