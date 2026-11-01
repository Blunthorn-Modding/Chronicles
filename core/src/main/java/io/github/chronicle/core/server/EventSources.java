package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import io.github.chronicle.core.play.EventSource;
import io.github.chronicle.core.progression.SourceLedger;
import io.github.chronicle.core.progression.SourceLedgerStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.BiPredicate;

/** Trusted gameplay source catalogue and durable future-occurrence evidence. No source performs Event effects. */
public final class EventSources {
    public static final String HOLD="chronicle_core:event-sources";
    private static final String PREFIX="chronicle_core:";
    private static final Map<String,Provider> PROVIDERS=new LinkedHashMap<>();
    private static final Set<String> WORKSHOP_TYPES=new LinkedHashSet<>();
    private static final Map<String,BiPredicate<MinecraftServer,SourceLedger.Receipt>> AUTHORITIES=new LinkedHashMap<>();
    private static final Set<String> UNCERTAIN_AUTHORITIES=new LinkedHashSet<>();
    private static final Map<MinecraftServer,Runtime> WORLDS=new IdentityHashMap<>();
    private static boolean registered,frozen;
    private EventSources() { }
    /** A source-owned hold already explains this failure; coordinators must not add a derivative hold. */
    public static final class Unavailable extends IllegalStateException {public Unavailable(String message,Throwable cause){super(message,cause);}}
    public static void registerWorkshopType(String managedType){new EventSource(managedType,"","");if(frozen||WORKSHOP_TYPES.size()>=32)throw new IllegalStateException("source catalogue is frozen or full");WORKSHOP_TYPES.add(managedType);}
    public static void registerAuthority(String prefix,BiPredicate<MinecraftServer,SourceLedger.Receipt> verifier){registerAuthority(prefix,verifier,false);}
    public static void registerAuthority(String prefix,BiPredicate<MinecraftServer,SourceLedger.Receipt> verifier,boolean uncertainOnRestart){
        if(frozen||prefix==null||!prefix.matches("[a-z0-9_-]+:")||AUTHORITIES.putIfAbsent(prefix,Objects.requireNonNull(verifier))!=null)throw new IllegalStateException("invalid or duplicate Event source authority");
        if(uncertainOnRestart)UNCERTAIN_AUTHORITIES.add(prefix);
    }
    public record Occurrence(String id,UUID actor,boolean activeOrigin,Map<String,Boolean> flags) {
        public Occurrence { if(id==null||id.isEmpty()||id.length()>256||flags==null||flags.size()>256)throw new IllegalArgumentException("invalid source occurrence");flags=Map.copyOf(flags); }
    }
    public record Choice(String id,String label,String description) { }
    private record Provider(String label,String description,Function<MinecraftServer,List<Choice>> targets,List<Choice> activities) { }
    /** Mods register meaning/selectors. Accepted evidence is still published through prepare/complete. */
    public static synchronized void registerProvider(String id,String label,String description,
            Function<MinecraftServer,List<Choice>> targets,List<Choice> activities) {
        new EventSource(id,"","");
        if(frozen||PROVIDERS.size()>=32||PROVIDERS.containsKey(id))throw new IllegalStateException("Event source providers are frozen, full or duplicated");
        PROVIDERS.put(id,new Provider(Objects.requireNonNull(label),Objects.requireNonNull(description),Objects.requireNonNull(targets),List.copyOf(activities)));
    }
    public static synchronized void register() {
        if(registered)return;registered=true;
        for(String kind:List.of("block_interacted","block_opened","block_placed","block_broken"))
            registerProvider(PREFIX+kind,switch(kind){case "block_interacted"->"Block interacted with";case "block_opened"->"Block container or workstation opened";case "block_placed"->"Block placed";default->"Block broken";},
                    "Successful player activity at the selected managed block, block type or registered Block location.",EventSources::blockChoices,List.of());
        for(String kind:List.of("book_found","book_opened"))registerProvider(PREFIX+kind,kind.equals("book_found")?"Book found":"Book opened",
                kind.equals("book_found")?"First managed inventory discovery; gifts and role supplies count.":"The real managed book opened during gameplay; editor previews do not count.",
                server->BookRuntime.books(server).values().stream().map(b->new Choice(b.id(),b.title(),b.author())).toList(),List.of());
        registerProvider(PREFIX+"departure_completed","Departure completed","Durable player and optional Region work completed. Silent flag outcomes may finalize after play ends.",
                server->Departures.state(server).definitions().values().stream().map(d->new Choice(d.id(),d.name(),"Completed departure")).toList(),List.of());
        registerProvider(PREFIX+"region_applied","Saved Region state applied","A direct apply or whole Event Region batch durably committed; capturing does not count.",
                server->AreaChanges.catalog(server).areas().values().stream().map(a->new Choice(a.bounds().regionId(),a.name(),"Saved Region application")).distinct().toList(),
                List.of(new Choice("START","Start state","Saved Start state"),new Choice("END","End state","Saved End state")));
        registerProvider(PREFIX+"prayer_completed","Prayer completed","A lasting bargain was durably purchased; opening or refusing a station does not count.",LastingBargains::eventSourceChoices,List.of());
        registerProvider(PREFIX+"forge_completed","Forge enchantment completed","A valid recipe actually changed the enchantments; scraps and unchanged capped equipment do not count.",
                EventSources::forgeChoices,List.of());
        ServerLifecycleEvents.SERVER_STARTING.register(server->frozen=true);
        ServerLifecycleEvents.SERVER_STARTED.register(EventSources::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        ServerTickEvents.END_SERVER_TICK.register(EventSources::tick);
    }
    private static void start(MinecraftServer server) {
        Runtime runtime=new Runtime(new SourceLedgerStore(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/event-sources.bin")));WORLDS.put(server,runtime);
        try{runtime.ledger=runtime.store.load();runtime.ready=true;
            if(runtime.ledger.receipts().values().stream().anyMatch(r->r.stage()==SourceLedger.Stage.PREPARED&&uncertain(r))) {
                runtime.held=true;RuntimeHolds.hold(server,HOLD,"A gameplay source outcome is uncertain after restart; review its exact prepared evidence before resuming.");
            }
        }catch(Exception failure){fail(server,failure);}
    }
    public static List<Entry> catalog(MinecraftServer server,String kind,String query) {
        String text=Objects.requireNonNullElse(query,"");List<Entry> result=new ArrayList<>();
        if(kind.equals("EVENT_TRIGGER")) {
            for(String id:List.of("MANUAL","ENTRY","TIMER","CONDITION"))result.add(entry(id,switch(id){case "MANUAL"->"Manual";case "ENTRY"->"Region entered";case "TIMER"->"Active play timer";default->"Flags become ready";},"Event trigger"));
            PROVIDERS.forEach((id,p)->result.add(entry(id,p.label,p.description)));
        } else {
            int split=text.indexOf('|');String type=split<0?text:text.substring(0,split);text=split<0?"":text.substring(split+1);
            Provider provider=PROVIDERS.get(type);if(provider==null)return List.of();
            List<Choice> choices;
            if(kind.equals("EVENT_TARGET"))choices=provider.targets.apply(server);
            else if(kind.equals("EVENT_ACTIVITY")){
                var activities=new ArrayList<Choice>();
                activities.add(new Choice("","Any activity",provider.activities.isEmpty()?"The selected trigger already specifies its activity.":"Use this trigger without an additional activity filter."));
                activities.addAll(provider.activities);choices=activities;
            }else choices=List.of();
            for(Choice choice:choices)result.add(entry(type+"|"+choice.id,choice.label,choice.description));
        }
        String filter=text.toLowerCase(Locale.ROOT);return result.stream().filter(e->(e.label()+" "+e.id()+" "+e.description()).toLowerCase(Locale.ROOT).contains(filter)).limit(512).toList();
    }
    private static Entry entry(String id,String label,String description){return new Entry(id,label,description,"EVENT_SOURCE","",0);}
    public static void validate(MinecraftServer server,EventSource source) {
        if(source==null||source.isEmpty())throw new IllegalArgumentException("Choose a registered gameplay trigger");
        Provider p=PROVIDERS.get(source.type());if(p==null)throw new IllegalArgumentException("Gameplay trigger provider unavailable: "+source.type());
        if(p.targets.apply(server).stream().noneMatch(c->c.id.equals(source.target())))throw new IllegalArgumentException("Choose an available trigger target");
        if(!source.activity().isEmpty()&&p.activities.stream().noneMatch(c->c.id.equals(source.activity())))throw new IllegalArgumentException("Choose an available trigger activity");
    }
    public static List<Occurrence> facts(MinecraftServer server,EventSource source) {
        Runtime r=runtime(server);try{reconcile(server,r);}catch(RuntimeException failure){fail(server,failure);throw new Unavailable("Event source authority could not be reconciled",failure);}
        return r.ledger.receipts().values().stream().filter(e->e.stage()==SourceLedger.Stage.COMMITTED&&matches(source,e))
                .map(e->new Occurrence(e.id(),e.actor(),e.activeOrigin(),e.flags())).toList();
    }
    private static boolean matches(EventSource source,SourceLedger.Receipt receipt) {
        return source.type().equals(receipt.type())&&(source.target().equals("*")||source.target().equals(receipt.target()))
                &&(source.activity().isEmpty()||source.activity().equals(receipt.activity()));
    }
    public static boolean active(ServerPlayerEntity player) {
        MinecraftServer server=player.getEntityWorld().getServer();var campaign=ChronicleRuntime.available(server);
        return campaign!=null&&campaign.state().phase()==SessionPhase.ACTIVE&&!SessionBuilding.enabled(server)&&RuntimeHolds.reason(server)==null
                &&!player.isSpectator()&&!AuthoringRuntime.isAuthor(player)&&PlayRuntime.eligiblePlayers(server).contains(player);
    }
    /** Called at the accepted origin, before the durable source authority or gameplay mutation. */
    public static String prepare(MinecraftServer server,String id,EventSource source,UUID actor,boolean activeOrigin,String authority) {
        if(!activeOrigin||!EventProgressRuntime.interested(server,source))return null;
        Runtime r=runtime(server);
        SourceLedger.Receipt receipt=new SourceLedger.Receipt(id,source.type(),source.target(),source.activity(),actor,true,
                ChronicleRuntime.service(server).state().flags(),authority,SourceLedger.Stage.PREPARED);
        try{
            var archived=r.store.archived(id);
            if(archived.isPresent()){
                SourceLedger.Receipt old=archived.get();
                if(old.stage()!=SourceLedger.Stage.COMMITTED||!old.at(SourceLedger.Stage.PREPARED).equals(receipt))
                    throw new IllegalStateException("archived Event source operation ID conflicts or was cancelled");
                return null;
            }
            publish(r,r.ledger.prepare(receipt));return id;
        }catch(IOException|RuntimeException failure){fail(server,failure);throw new Unavailable("Event source preparation could not be saved",failure);}
    }
    public static String prepare(ServerPlayerEntity actor,String id,EventSource source,String authority) {
        return prepare(actor.getEntityWorld().getServer(),id,source,actor.getUuid(),active(actor),authority);
    }
    public static void complete(MinecraftServer server,String id,boolean success) {
        if(id==null)return;Runtime r=runtime(server);
        try{
            if(!r.ledger.receipts().containsKey(id)){
                SourceLedger.Receipt old=r.store.archived(id).orElseThrow(()->new IllegalStateException("source preparation is missing"));
                SourceLedger.Stage expected=success?SourceLedger.Stage.COMMITTED:SourceLedger.Stage.CANCELLED;
                if(old.stage()!=expected)throw new IllegalStateException("source outcome conflicts with archived evidence");
                return;
            }
            publish(r,r.ledger.finish(id,success));
        }catch(IOException|RuntimeException failure){fail(server,failure);throw new Unavailable("Event source outcome could not be saved; exact preparation retained",failure);}
    }
    /** Use only when the owning producer has proved a pre-authority refusal. */
    public static void cancelAuthority(MinecraftServer server,String authority) {
        Runtime r=runtime(server);try{publish(r,r.ledger.cancelAuthority(authority));}
        catch(IOException|RuntimeException failure){fail(server,failure);throw new Unavailable("Refused source evidence could not be cancelled",failure);}
    }
    public static String id(String authority,String type,String target,String activity) {
        return "source:"+UUID.nameUUIDFromBytes((authority+"|"+type+"|"+target+"|"+activity).getBytes(StandardCharsets.UTF_8));
    }
    public static void prepareRegion(MinecraftServer server,UUID operation,UUID actor,String region,String state,boolean activeOrigin,String authorityKind) {
        String authority=authorityKind+":"+operation;EventSource source=new EventSource(PREFIX+"region_applied",region,state);
        prepare(server,id(authority,source.type(),region,state),source,actor,activeOrigin,authority);
    }
    public static boolean activeOperation(MinecraftServer server) {
        var c=ChronicleRuntime.available(server);return c!=null&&c.state().phase()==SessionPhase.ACTIVE&&!SessionBuilding.enabled(server)&&!PlayRuntime.eligiblePlayers(server).isEmpty();
    }
    public static List<SourceLedger.Receipt> pending(MinecraftServer server) {
        Runtime r=WORLDS.get(server);if(r==null)throw new IllegalStateException("Event source evidence unavailable");
        return r.ledger.receipts().values().stream().filter(receipt->receipt.stage()==SourceLedger.Stage.PREPARED).toList();
    }
    static List<SourceLedger.Receipt> committed(MinecraftServer server){return runtime(server).ledger.receipts().values().stream().filter(receipt->receipt.stage()==SourceLedger.Stage.COMMITTED).toList();}
    static Set<String> liveIds(MinecraftServer server){return Set.copyOf(runtime(server).ledger.receipts().keySet());}
    static boolean liveContains(MinecraftServer server,String id){return runtime(server).ledger.receipts().containsKey(id);}
    static boolean archived(MinecraftServer server,String id)throws IOException{return runtime(server).store.archived(id).isPresent();}
    static void retireCommitted(MinecraftServer server,Set<String> ids)throws IOException{
        Runtime r=runtime(server);
        for(String id:ids){SourceLedger.Receipt receipt=r.ledger.receipts().get(id);if(receipt==null||receipt.stage()!=SourceLedger.Stage.COMMITTED)throw new IllegalStateException("only committed Event source evidence can be retired");}
        publish(r,r.ledger.retire(ids));
    }
    /** Explicitly reload repaired evidence; loading never itself acknowledges an uncertain gameplay outcome. */
    public static void reload(net.minecraft.server.command.ServerCommandSource source)throws IOException {
        MinecraftServer server=source.getServer();requireRecoveryAuthority(source);Runtime r=WORLDS.get(server);if(r==null)throw new IllegalStateException("Event source evidence unavailable");
        try {
            SourceLedger loaded=r.store.load();
            ChronicleRuntime.service(server).recordOperation(new io.github.chronicle.core.campaign.Actor(source.getName(),source.getPlayer().getUuidAsString()),"event.source.reload","event-sources",Map.of("revision",Long.toString(loaded.revision())));
            r.ledger=loaded;r.ready=true;r.reconciledTick=Integer.MIN_VALUE;
            if(loaded.receipts().values().stream().anyMatch(receipt->receipt.stage()==SourceLedger.Stage.PREPARED&&uncertain(receipt))){r.held=true;RuntimeHolds.hold(server,HOLD,"Review the exact uncertain gameplay source before resuming.");}
            else releaseIfResolved(server,r);
        }catch(IOException|RuntimeException failure){fail(server,failure);throw failure;}
    }
    /** Explicit review of an exact uncertain source. Automatic reconciliation never manufactures this decision. */
    public static void recover(net.minecraft.server.command.ServerCommandSource source,SourceLedger.Receipt expected,boolean acknowledgeSuccess)throws IOException {
        MinecraftServer server=source.getServer();
        requireRecoveryAuthority(source);
        Runtime r=WORLDS.get(server);if(r==null||expected==null)throw new IllegalStateException("Review the exact source evidence first");
        SourceLedger loaded=r.store.load();SourceLedger.Receipt actual=loaded.receipts().get(expected.id());
        if(!expected.equals(actual)||actual.stage()!=SourceLedger.Stage.PREPARED||!uncertain(actual))throw new IllegalStateException("Source evidence changed or has an authoritative system outcome; review it again");
        if(actual.authority().startsWith("forge:"))forgeEvidence(server,actual);
        if(acknowledgeSuccess&&actual.authority().startsWith("book:"))BookDeliveryRuntime.inspectRecovery(server,actual);
        ChronicleRuntime.service(server).recordOperation(new io.github.chronicle.core.campaign.Actor(source.getName(),source.getPlayer().getUuidAsString()),
                "event.source.recovery",actual.id(),Map.of("authority",actual.authority(),"outcome",acknowledgeSuccess?"operator-confirmed-complete":"operator-confirmed-not-complete"));
        if(acknowledgeSuccess&&actual.authority().startsWith("book:"))BookDeliveryRuntime.recoverDiscovery(server,actual);
        SourceLedger next=loaded.finish(actual.id(),acknowledgeSuccess);r.store.save(next);r.ledger=next;r.ready=true;
        releaseIfResolved(server,r);
    }
    private static void requireRecoveryAuthority(net.minecraft.server.command.ServerCommandSource source){if(!source.hasPermissionLevel(2)||source.getPlayer()==null||ChronicleRuntime.service(source.getServer()).state().phase()==SessionPhase.ACTIVE||RuntimeHolds.heldByOther(source.getServer(),HOLD))throw new IllegalStateException("Source recovery requires an operator, non-active play and no other operation hold");}
    private static boolean uncertain(SourceLedger.Receipt receipt){return receipt.authority().startsWith("transient:")||receipt.authority().startsWith("forge:")||receipt.authority().startsWith("extent:")||receipt.authority().startsWith("book:")||UNCERTAIN_AUTHORITIES.stream().anyMatch(receipt.authority()::startsWith);}
    public static boolean recoverable(SourceLedger.Receipt receipt){return receipt!=null&&receipt.stage()==SourceLedger.Stage.PREPARED&&uncertain(receipt);}
    public static io.github.chronicle.core.progression.ForgeResultEvidence forgeEvidence(MinecraftServer server,SourceLedger.Receipt receipt)throws IOException {
        String[] parts=receipt.authority().split(":",3);if(parts.length!=3||!parts[0].equals("forge"))throw new IllegalArgumentException("Choose a Forge source receipt");
        var evidence=io.github.chronicle.core.progression.ForgeResultEvidence.load(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/forge-results"),UUID.fromString(parts[1]),parts[2]);
        if(!evidence.actor().equals(receipt.actor())||!evidence.target().equals(receipt.target()))throw new IOException("Forge source/result identity mismatch");return evidence;
    }
    private static void publish(Runtime runtime,SourceLedger next)throws IOException {if(next==runtime.ledger)return;runtime.store.save(next);runtime.ledger=next;}
    private static Runtime runtime(MinecraftServer server) {if(!server.isOnThread())throw new IllegalStateException("Event sources require the server thread");Runtime r=WORLDS.get(server);if(r==null||!r.ready)throw new Unavailable("Event source evidence unavailable; inspect the server log",null);return r;}
    private static void tick(MinecraftServer server) {
        Runtime r=WORLDS.get(server);if(r==null||!r.ready)return;
        try{if(server.getTicks()%100==0)r.store.verifyUnchanged();reconcile(server,r);}catch(RuntimeException|IOException failure){fail(server,failure);}
    }
    private static void reconcile(MinecraftServer server,Runtime r) {
        if(r.reconciledTick==server.getTicks())return;r.reconciledTick=server.getTicks();
        for(var receipt:List.copyOf(r.ledger.receipts().values()))if(receipt.stage()==SourceLedger.Stage.PREPARED) {
            String authority=receipt.authority();boolean committed=false;
            if(authority.startsWith("departure:")){UUID id=UUID.fromString(authority.substring(10));var op=Departures.state(server).operations().get(id);if(op==null&&Departures.sourceDefinitelyAbsent(server,id)){cancelAuthority(server,authority);continue;}committed=op!=null&&op.stage()==io.github.chronicle.core.departures.DepartureOperation.Stage.COMPLETE&&receipt.type().equals(PREFIX+"departure_completed")&&receipt.target().equals(op.definition().id())&&Objects.equals(receipt.actor(),op.actor());}
            else if(authority.startsWith("offering:")){UUID operation=UUID.fromString(authority.substring(9));committed=OfferingStats.ledger(server).receipts().stream().anyMatch(e->e.transaction().equals(operation)&&Objects.equals(e.player(),receipt.actor())&&e.offering().equals(receipt.target()));if(!committed&&OfferingStats.sourceDefinitelyAbsent(server,operation)){cancelAuthority(server,authority);continue;}}
            else if(authority.startsWith("area:")){UUID id=UUID.fromString(authority.substring(5));var op=AreaChanges.operations(server).state().operations().get(id);if(op==null&&WorldAreaOperations.sourceDefinitelyAbsent(server,id)){cancelAuthority(server,authority);continue;}committed=op!=null&&op.stage()==io.github.chronicle.core.areas.AreaOperation.Stage.COMPLETE&&receipt.type().equals(PREFIX+"region_applied")&&receipt.target().equals(op.bounds().regionId())&&op.requestedState().map(s->s.name().equals(receipt.activity())).orElse(false)&&Objects.equals(receipt.actor(),op.actor());}
            else if(authority.startsWith("batch:")){UUID id=UUID.fromString(authority.substring(6));if(EventWorldBatches.sourceDefinitelyAbsent(server,id)){cancelAuthority(server,authority);continue;}committed=EventWorldBatches.sourceCommitted(server,id,receipt.actor(),receipt.target(),receipt.activity());}
            else if(authority.startsWith("book:")){var delivery=ChestAuthoring.service(server).state().bookDelivery().deliveries().get(receipt.target());committed=delivery!=null&&delivery.firstDiscovery()!=null&&delivery.deliveryToken().equals(authority.substring(5))&&delivery.firstDiscovery().playerUuid().equals(receipt.actor().toString());}
            else if(authority.startsWith("rolebook:")) {
                String[] parts=authority.split(":",3);if(parts.length!=3)throw new IllegalStateException("Role book source authority is invalid");var role=RoleFirstEntry.sourceOperation(server,UUID.fromString(parts[1]));
                if(role!=null&&role.stage()==io.github.chronicle.core.entry.RoleEntryOperation.Stage.ABORTED){cancelAuthority(server,authority);continue;}
                var delivery=ChestAuthoring.service(server).state().bookDelivery().deliveries().get(receipt.target());
                committed=role!=null&&role.stage()==io.github.chronicle.core.entry.RoleEntryOperation.Stage.COMPLETE&&role.playerId().equals(receipt.actor())&&role.books().stream().anyMatch(book->book.bookId().equals(receipt.target())&&book.deliveryToken().equals(parts[2]))&&delivery!=null&&delivery.firstDiscovery()!=null&&delivery.deliveryToken().equals(parts[2])&&delivery.firstDiscovery().playerUuid().equals(receipt.actor().toString());
            }
            else for(var verifier:AUTHORITIES.entrySet())if(authority.startsWith(verifier.getKey())){committed=verifier.getValue().test(server,receipt);break;}
            if(committed)complete(server,receipt.id(),true);
        }
        releaseIfResolved(server,r);
    }
    private static void releaseIfResolved(MinecraftServer server,Runtime r){if(r.held&&r.ready&&r.ledger.receipts().values().stream().noneMatch(receipt->receipt.stage()==SourceLedger.Stage.PREPARED&&uncertain(receipt))){RuntimeHolds.release(server,HOLD);r.held=false;}}
    public static void fail(MinecraftServer server,Exception failure) {Runtime r=WORLDS.get(server);if(r!=null){r.ready=false;r.held=true;}RuntimeHolds.hold(server,HOLD,"Event source evidence is unavailable or uncertain; preserve the world and inspect the server log.");ChronicleCore.LOGGER.error("Event source evidence retained for recovery",failure);}
    private static List<Choice> blockChoices(MinecraftServer server) {
        List<Choice> choices=new ArrayList<>();
        for(var site:AuthoringRuntime.get(server).service().state().sites().values())if(site instanceof AuthoringSite.Anchor a)choices.add(new Choice("site:"+a.id(),a.id(),a.expectedBlock()+" at "+a.dimension()));
        var managed=ManagedBlocks.snapshot(server);for(var type:managed.types())choices.add(new Choice("type:"+type.typeId(),type.label(),"Any registered managed instance of this type"));
        for(var instance:managed.instances())choices.add(new Choice("managed:"+instance.id(),instance.label()+" "+instance.position(),"This managed block instance"));
        return List.copyOf(choices);
    }
    private static List<Choice> forgeChoices(MinecraftServer server) {
        List<Choice> result=new ArrayList<>();result.add(new Choice("*","Any registered Forge","Any successful registered ingredient workshop"));
        for(var instance:ManagedBlocks.snapshot(server).instances())if(WORKSHOP_TYPES.contains(instance.typeId()))result.add(new Choice("managed:"+instance.id(),instance.label()+" "+instance.position(),"This Forge instance"));
        return List.copyOf(result);
    }
    public static String managedTarget(ServerWorld world,BlockPos pos) {
        return blockTargets(world,pos).stream().filter(s->s.startsWith("managed:")).findFirst().orElse("*");
    }
    public static boolean interested(MinecraftServer server,EventSource source){return EventProgressRuntime.interested(server,source);}
    public static List<String> blockTargets(ServerWorld world,BlockPos pos) {
        String dimension=world.getRegistryKey().getValue().toString();BlockPosition position=new BlockPosition(pos.getX(),pos.getY(),pos.getZ());List<String> result=new ArrayList<>();
        for(var site:AuthoringRuntime.get(world.getServer()).service().state().sites().values())if(site instanceof AuthoringSite.Anchor a&&a.dimension().equals(dimension)&&a.position().equals(position))result.add("site:"+a.id());
        for(var instance:ManagedBlocks.snapshot(world.getServer()).instances())if(instance.dimension().equals(dimension)&&instance.position().equals(position)){result.add("managed:"+instance.id());result.add("type:"+instance.typeId());}
        return List.copyOf(new LinkedHashSet<>(result));
    }
    public record BlockAttempt(List<String> interactions,List<String> openings,Map<String,Set<BlockPos>> placements,String placedBlock,Map<BlockPos,BlockState> before,net.minecraft.screen.ScreenHandler screen) { }
    public static BlockAttempt beginBlock(ServerPlayerEntity player,ServerWorld world,ItemStack stack,BlockHitResult hit) {
        if(!active(player))return null;List<String> targets=blockTargets(world,hit.getBlockPos());
        List<String> interactions=prepareTargets(player,"block_interacted",targets),openings=prepareTargets(player,"block_opened",targets);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();before.put(hit.getBlockPos().toImmutable(),world.getBlockState(hit.getBlockPos()));BlockPos adjacent=hit.getBlockPos().offset(hit.getSide());before.put(adjacent,world.getBlockState(adjacent));
        Map<String,Set<BlockPos>> placements=new LinkedHashMap<>();String placedBlock="";
        if(stack.getItem() instanceof BlockItem block) {
            placedBlock=Registries.BLOCK.getId(block.getBlock()).toString();Map<String,Set<BlockPos>> targetsByPosition=new LinkedHashMap<>();
            for(BlockPos pos:before.keySet())for(String target:blockTargets(world,pos))targetsByPosition.computeIfAbsent(target,k->new LinkedHashSet<>()).add(pos);
            ChronicleCore.managedBlocks().byBlock(placedBlock).ifPresent(type->targetsByPosition.put("type:"+type.typeId(),Set.copyOf(before.keySet())));
            for(var target:targetsByPosition.entrySet())for(String id:prepareTargets(player,"block_placed",List.of(target.getKey())))placements.put(id,Set.copyOf(target.getValue()));
        }
        return new BlockAttempt(interactions,openings,Map.copyOf(placements),placedBlock,Map.copyOf(before),player.currentScreenHandler);
    }
    public static void finishBlock(ServerPlayerEntity player,ServerWorld world,BlockAttempt attempt,ActionResult result) {
        if(attempt==null)return;boolean accepted=result.isAccepted();MinecraftServer server=world.getServer();
        for(String id:attempt.interactions)complete(server,id,accepted);
        for(String id:attempt.openings)complete(server,id,accepted&&player.currentScreenHandler!=attempt.screen&&player.currentScreenHandler!=player.playerScreenHandler);
        for(var placement:attempt.placements.entrySet()) {
            boolean placed=accepted&&placement.getValue().stream().anyMatch(pos->!world.getBlockState(pos).equals(attempt.before.get(pos))&&Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString().equals(attempt.placedBlock));
            complete(server,placement.getKey(),placed);
        }
    }
    public static List<String> prepareTargets(ServerPlayerEntity player,String type,Collection<String> targets) {
        List<String> result=new ArrayList<>();String operation=UUID.randomUUID().toString();
        for(String target:new LinkedHashSet<>(targets)){EventSource source=new EventSource(PREFIX+type,target,"");String id=prepare(player,id(operation,source.type(),target,""),source,"transient:"+operation);if(id!=null)result.add(id);}
        return List.copyOf(result);
    }
    public static String prepareBookOpen(ServerPlayerEntity player,ItemStack stack) {
        String book=BookDeliveryRuntime.authenticBook(player.getEntityWorld().getServer(),stack);if(book==null)return null;
        EventSource source=new EventSource(PREFIX+"book_opened",book,"");String operation=UUID.randomUUID().toString();return prepare(player,id(operation,source.type(),book,""),source,"transient:"+operation);
    }
    private static final class Runtime {final SourceLedgerStore store;SourceLedger ledger=SourceLedger.empty();boolean ready,held;int reconciledTick=Integer.MIN_VALUE;Runtime(SourceLedgerStore store){this.store=store;}}
}
