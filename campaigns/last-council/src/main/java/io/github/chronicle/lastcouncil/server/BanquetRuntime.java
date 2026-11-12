package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.content.InventorySnapshot;
import io.github.chronicle.core.server.*;
import io.github.chronicle.lastcouncil.LastCouncil;
import io.github.chronicle.lastcouncil.banquet.*;
import io.github.chronicle.lastcouncil.network.BanquetPayloads.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.WorldSavePath;
import static io.github.chronicle.lastcouncil.banquet.BanquetState.*;
import static net.minecraft.server.command.CommandManager.literal;

/** Private item ledger + exact inventory recovery. No banquet record changes political eligibility. */
public final class BanquetRuntime {
    public static final String HOLD="last_council:banquet";
    private static final UUID NONE=new UUID(0,0);
    private static final Map<MinecraftServer,Runtime> WORLDS=new IdentityHashMap<>();
    private static final Map<ServerPlayerEntity,Long> REQUESTS=new WeakHashMap<>();
    private static final ThreadLocal<Boolean> APPLYING=ThreadLocal.withInitial(()->false);
    private BanquetRuntime() { }
    private static final class Runtime {
        final BanquetSettings settings; final BanquetStore store; final BanquetJournal journal; final UUID worldId;
        BanquetState state; long revision=new java.security.SecureRandom().nextLong(Long.MAX_VALUE); int ticks;
        String inspected="";
        boolean failed;
        final Map<UUID,Long> elapsed=new HashMap<>(); final Map<UUID,List<StatusEffectInstance>> symptoms=new HashMap<>();
        Runtime(BanquetSettings settings,BanquetStore store,BanquetState state,BanquetJournal journal,UUID worldId){this.settings=settings;this.store=store;this.state=state;this.journal=journal;this.worldId=worldId;}
    }
    public static void register() {
        ScenarioContentRuntime.registerExtension(HOLD,new ScenarioContentRuntime.ExtensionAdapter(){
            public void validate(String json)throws IOException{BanquetSettings.decode(json.getBytes(StandardCharsets.UTF_8));}
            public String capture(MinecraftServer server)throws IOException{if(!CouncilRuntime.active())return null;var r=runtime(server);r.settings.verify();return new String(BanquetSettings.encode(r.settings.config()),StandardCharsets.UTF_8);}
            public void initialize(Path directory,String initial,boolean legacy)throws IOException{BanquetProfiles.initialize(directory,initial,legacy);}
            public void verify(Path directory)throws IOException{BanquetProfiles.verify(directory);}
        });
        ServerLifecycleEvents.SERVER_STARTED.register(BanquetRuntime::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{var r=WORLDS.get(server);if(r!=null&&r.journal.pending()==null)try{flush(r);}catch(Exception e){hold(server,"Banquet clock checkpoint failed; preserve its records.");}});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{WORLDS.remove(server);REQUESTS.keySet().removeIf(p->p.getEntityWorld().getServer()==server);});
        ServerTickEvents.END_SERVER_TICK.register(BanquetRuntime::tick);
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(q,c)->c.server().execute(()->request(c.player(),q)));
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("chronicle").then(literal("banquet")
            .then(literal("log").executes(c->{var p=c.getSource().getPlayerOrThrow();if(!CouncilRuntime.isChancellor(c.getSource().getServer(),p.getUuid()))throw new IllegalStateException("Only the assigned Chancellor can inspect banquet records.");send(p,NONE,true,true,"");return 1;}))
            .then(literal("recover").requires(s->s.hasPermissionLevel(2)).executes(c->{try{var s=c.getSource().getServer();var existing=WORLDS.get(s);if(existing==null||existing.journal.poisoned())start(s);var r=runtime(s);recover(s,r);if(r.journal.pending()!=null)throw new IllegalStateException("Recovery remains held. Restore the exact before or after inventory and retry.");c.getSource().sendFeedback(()->Text.literal("Banquet records recovered."),false);return 1;}catch(IOException|IllegalStateException e){c.getSource().sendError(Text.literal("Banquet recovery remains held; preserve the recorded inventory and files."));return 0;}})))));
        if(CouncilRuntime.active())GameSetupTools.register(new GameSetupTools.Tool(HOLD,"Banquet","Optional drinks and delayed illness.",List.of(new GameSetupTools.Verb("open","Open banquet",GameSetupTools.Guard.READ_ONLY,false)),source->new GameSetupTools.Status(runtime(source.getServer()).revision,List.of(),Map.of()),(source,verb)->{send(source.getPlayerOrThrow(),NONE,true,false,"");return "";}));
    }
    private static void start(MinecraftServer server){
        if(!CouncilRuntime.active())return;
        try{
            Path root=server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/last-council");
            Path ledger=root.resolve("banquet.json"),journalPath=root.resolve("banquet-inventory.json");
            if(Files.exists(journalPath,LinkOption.NOFOLLOW_LINKS)!=Files.exists(ledger,LinkOption.NOFOLLOW_LINKS))throw new IOException("One of the initialized banquet records is missing.");
            var identity=new io.github.chronicle.core.persistence.AtomicDataFile(root.resolve("banquet-world-id"),128);byte[] identityBytes=identity.load();
            if(identityBytes==null){if(Files.exists(ledger,LinkOption.NOFOLLOW_LINKS))throw new IOException("Initialized banquet identity is missing.");identityBytes=(UUID.randomUUID()+"\n").getBytes(StandardCharsets.US_ASCII);identity.save(identityBytes);}
            String identityText=new String(identityBytes,StandardCharsets.US_ASCII);UUID worldId=UUID.fromString(identityText.strip());if(!identityText.equals(worldId+"\n"))throw new IOException("Invalid banquet identity.");
            var store=new BanquetStore(ledger);var state=store.load();
            if(!Files.exists(ledger,LinkOption.NOFOLLOW_LINKS))store.save(state);
            var journal=new BanquetJournal(journalPath);
            Path directory=ScenarioAuthoringProfiles.chronicleConfig().resolve(ScenarioAuthoringProfiles.active().isPresent()?"profile-content":"");
            var settings=new BanquetSettings(directory.resolve("last-council-banquet.json"),BanquetConfig.defaults());
            Runtime r=new Runtime(settings,store,state,journal,worldId);WORLDS.put(server,r);
            if(journal.pending()!=null)hold(server,"A banquet operation needs exact inventory recovery; preserve the inventory.");else RuntimeHolds.release(server,HOLD);
        }catch(Exception e){WORLDS.remove(server);io.github.chronicle.core.ChronicleCore.LOGGER.error("Banquet records could not be loaded; retained files were preserved",e);hold(server,"Banquet records are unavailable; restore their files before recovery.");}
    }
    private static Runtime runtime(MinecraftServer server){var r=WORLDS.get(server);if(r==null)throw new IllegalStateException("Banquet records need operator recovery.");return r;}
    public static BanquetState state(MinecraftServer server){return runtime(server).state;}
    private static void hold(MinecraftServer server,String message){RuntimeHolds.hold(server,HOLD,message);}
    private static void ready(Runtime r)throws IOException{r.settings.verify();r.store.verifyUnchanged();r.journal.verify();if(r.journal.poisoned())throw new IOException("Inventory journal outcome is uncertain.");}
    private static boolean participant(ServerPlayerEntity player){return PlayRuntime.eligiblePlayers(player.getEntityWorld().getServer()).contains(player);}
    private static String useReason(ServerPlayerEntity player){
        MinecraftServer server=player.getEntityWorld().getServer();
        if(!CouncilRuntime.active()||!EventSources.activeOperation(server)||!participant(player)||RuntimeHolds.reason(server)!=null)return "Banquet supplies require active participation without a hold.";
        return "";
    }
    public static boolean canUse(ServerPlayerEntity player){String reason=useReason(player);if(!reason.isBlank()){player.sendMessage(Text.literal(reason),true);return false;}return true;}
    private static void requireUse(ServerPlayerEntity player){String reason=useReason(player);if(!reason.isBlank())throw new IllegalStateException(reason);}
    private static InventorySnapshot inventory(MinecraftServer server,ServerPlayerEntity player){
        if(player.getInventory().size()!=43)throw new IllegalStateException("Unsupported player inventory layout.");
        SimpleInventory copy=new SimpleInventory(43);for(int i=0;i<43;i++)copy.setStack(i,player.getInventory().getStack(i).copy());return InventorySnapshots.capture(server,copy);
    }
    private static InventorySnapshot snapshot(MinecraftServer server,List<ItemStack> stacks){return InventorySnapshots.capture(server,new SimpleInventory(stacks.toArray(ItemStack[]::new)));}
    private static void apply(ServerPlayerEntity player,List<ItemStack> stacks){for(int i=0;i<43;i++)player.getInventory().setStack(i,stacks.get(i).copy());player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();}
    private static void savedInventory(MinecraftServer server,ServerPlayerEntity player)throws IOException{
        Path path=server.getSavePath(WorldSavePath.PLAYERDATA).resolve(player.getUuid()+".dat");
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>8L*1024*1024)throw new IOException("Saved player inventory is unavailable.");
        var saved=net.minecraft.nbt.NbtIo.readCompressed(path,net.minecraft.nbt.NbtSizeTracker.of(32L*1024*1024));
        var view=net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY,server.getRegistryManager());player.writeData(view);
        var expected=view.getNbt();
        if(!Objects.equals(saved.get("Inventory"),expected.get("Inventory"))||!Objects.equals(saved.get("equipment"),expected.get("equipment")))throw new IOException("Player inventory save could not be confirmed from disk.");
    }
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String ledgerHash(BanquetState state)throws IOException{return hash(BanquetStore.encode(state));}
    private static UUID derived(UUID request,String suffix){return UUID.nameUUIDFromBytes((request+"/"+suffix).getBytes(StandardCharsets.UTF_8));}
    private static String name(ServerPlayerEntity player){String s=player.getName().getString();return s.substring(0,Math.min(80,s.length()));}
    private static void flush(Runtime r)throws IOException{
        if(r.elapsed.isEmpty())return;ready(r);if(r.journal.pending()!=null)throw new IOException("Inventory recovery is pending.");
        BanquetState next=r.state;for(var entry:r.elapsed.entrySet())next=BanquetTransitions.advance(next,entry.getValue(),Set.of(entry.getKey()));
        if(next!=r.state){r.store.save(next);r.state=next;}r.elapsed.clear();
    }
    private static String transact(MinecraftServer server,Runtime r,BanquetJournal.Intent intent,ServerPlayerEntity owner)throws IOException{
        ready(r);
        var prior=r.journal.get(intent.request());
        if(prior!=null){if(!prior.actor().equals(intent.actor())||!prior.owner().equals(intent.owner())||!prior.fingerprint().equals(intent.fingerprint()))throw new IllegalStateException("This request identifier belongs to different supplies.");return prior.stage()==BanquetJournal.Stage.COMPLETE?"This supply request already completed.":"This request was cancelled; make a new request.";}
        if(r.journal.pending()!=null)throw new IllegalStateException("A banquet operation needs recovery first.");
        if(!ledgerHash(r.state).equals(intent.beforeLedgerHash())||!inventory(server,owner).equals(intent.before()))throw new IllegalStateException("Inventory or banquet state changed; nothing was applied.");
        r.journal.claim(intent);hold(server,"A banquet operation needs exact inventory recovery; preserve the inventory.");
        apply(owner,InventorySnapshots.decode(server,intent.after()));
        server.getPlayerManager().saveAllPlayerData();
        savedInventory(server,owner);
        if(server.getPlayerManager().getPlayer(owner.getUuid())!=owner||!inventory(server,owner).equals(intent.after()))throw new IOException("Inventory save needs exact recovery.");
        r.store.save(intent.afterLedger());r.state=intent.afterLedger();
        r.journal.finish(intent.request(),BanquetJournal.Stage.COMPLETE);RuntimeHolds.release(server,HOLD);
        return "Supplies delivered.";
    }
    private static void recover(MinecraftServer server,Runtime r)throws IOException{
        ready(r);var op=r.journal.pending();if(op==null){flush(r);r.failed=false;RuntimeHolds.release(server,HOLD);return;}
        var player=server.getPlayerManager().getPlayer(op.owner());if(player==null)return;
        String current=ledgerHash(r.state),after=ledgerHash(op.afterLedger());
        var live=inventory(server,player);
        if(live.equals(op.before())&&current.equals(op.beforeLedgerHash())){
            server.getPlayerManager().saveAllPlayerData();if(!inventory(server,player).equals(live))throw new IOException("Recovery inventory changed.");
            savedInventory(server,player);
            r.journal.finish(op.request(),BanquetJournal.Stage.CANCELLED);r.failed=false;RuntimeHolds.release(server,HOLD);
        }else if(live.equals(op.after())&&(current.equals(op.beforeLedgerHash())||current.equals(after))){
            server.getPlayerManager().saveAllPlayerData();if(!inventory(server,player).equals(live))throw new IOException("Recovery inventory changed.");
            savedInventory(server,player);
            if(!current.equals(after)){r.store.save(op.afterLedger());r.state=op.afterLedger();}
            r.journal.finish(op.request(),BanquetJournal.Stage.COMPLETE);r.failed=false;RuntimeHolds.release(server,HOLD);
        }
    }
    public static boolean contaminate(ServerPlayerEntity player){
        MinecraftServer server=player.getEntityWorld().getServer();
        try{
            requireUse(player);Runtime r=runtime(server);ready(r);flush(r);
            if(!r.settings.config().enabled())throw new IllegalStateException("Poisoning is disabled.");
            UUID poison=BanquetItems.token(player.getMainHandStack(),SupplyKind.POISON),drink=BanquetItems.token(player.getOffHandStack(),SupplyKind.DRINK);
            UUID request=derived(poison,"contaminate");var before=inventory(server,player);var stacks=new ArrayList<>(InventorySnapshots.decode(server,before));
            int slot=player.getInventory().getSelectedSlot();stacks.set(slot,ItemStack.EMPTY);
            BanquetState after=BanquetTransitions.contaminate(r.state,r.state.revision(),request,player.getUuid(),poison,drink);
            String fingerprint=hash(("contaminate/"+player.getUuid()+"/"+poison+"/"+drink).getBytes(StandardCharsets.UTF_8));
            transact(server,r,new BanquetJournal.Intent(request,player.getUuid(),player.getUuid(),fingerprint,name(player)+" contaminated a drink.",ledgerHash(r.state),after,before,snapshot(server,stacks)),player);
            player.sendMessage(Text.literal("You empty the vial into the drink."),true);return true;
        }catch(Exception e){itemFailure(player,e);return false;}
    }
    public static void consume(ServerPlayerEntity player,SupplyKind kind,Hand hand){
        MinecraftServer server=player.getEntityWorld().getServer();
        try{
            requireUse(player);Runtime r=runtime(server);ready(r);flush(r);
            UUID token=BanquetItems.token(player.getStackInHand(hand),kind),request=derived(token,"consume");
            if(kind==SupplyKind.POISON)throw new IllegalStateException("Use the dose with a drink in your other hand.");
            var before=inventory(server,player);var stacks=new ArrayList<>(InventorySnapshots.decode(server,before));int slot=hand==Hand.OFF_HAND?40:player.getInventory().getSelectedSlot();stacks.set(slot,new ItemStack(Items.GLASS_BOTTLE));
            BanquetState after;
            if(kind==SupplyKind.ANTIDOTE)after=BanquetTransitions.antidote(r.state,r.state.revision(),request,player.getUuid(),token);
            else {
                after=BanquetTransitions.drink(r.state,r.state.revision(),request,player.getUuid(),token,r.settings.config().timing(),RepeatPolicy.REFRESH);
                if(!r.settings.config().enabled())after=new BanquetState(after.revision(),after.supplies(),r.state.illnesses(),after.operations());
                // Disabled poisoning cannot create/refresh illness; existing clocks remain suspended.
            }
            String fingerprint=hash((kind+"/"+player.getUuid()+"/"+token).getBytes(StandardCharsets.UTF_8));
            transact(server,r,new BanquetJournal.Intent(request,player.getUuid(),player.getUuid(),fingerprint,name(player)+" consumed "+(kind==SupplyKind.DRINK?"a banquet drink.":"an antidote."),ledgerHash(r.state),after,before,snapshot(server,stacks)),player);
            if(kind==SupplyKind.ANTIDOTE)clearSymptoms(r,player);
        }catch(Exception e){itemFailure(player,e);}
    }
    private static void itemFailure(ServerPlayerEntity player,Exception e){
        var r=WORLDS.get(player.getEntityWorld().getServer());if(e instanceof IOException||r!=null&&(r.journal.pending()!=null||r.journal.poisoned()))hold(player.getEntityWorld().getServer(),"Banquet inventory evidence needs operator recovery; preserve the inventory.");
        player.sendMessage(Text.literal(e instanceof IOException?"The use could not be confirmed. Preserve your inventory and ask an operator to recover banquet records.":Objects.toString(e.getMessage(),"Supply unavailable.")),true);player.playerScreenHandler.syncState();
    }
    private static boolean editable(ServerPlayerEntity player){var server=player.getEntityWorld().getServer();return player.hasPermissionLevel(2)&&AuthoringRuntime.isAuthor(player)&&AuthoringRuntime.editable(server)&&RuntimeHolds.reason(server)==null;}
    private static List<PlayerChoice> players(MinecraftServer server){var play=PlayRuntime.available(server);if(play==null||!Departures.ready(server))return List.of();return server.getPlayerManager().getPlayerList().stream().filter(p->play.participants().contains(p.getUuid())&&p.isAlive()&&!p.isSpectator()&&!Departures.departed(server,p.getUuid())).limit(256).map(p->new PlayerChoice(p.getUuid(),name(p))).toList();}
    private static String giftFingerprint(Request q,UUID actor){return hash((actor+"/"+q.recipient()+"/"+q.drinks()+"/"+q.poison()+"/"+q.antidotes()).getBytes(StandardCharsets.UTF_8));}
    private static String gift(ServerPlayerEntity actor,Request q)throws IOException{
        MinecraftServer server=actor.getEntityWorld().getServer();Runtime r=runtime(server);ready(r);String fingerprint=giftFingerprint(q,actor.getUuid());
        var prior=r.journal.get(q.request());if(prior!=null){if(!prior.actor().equals(actor.getUuid())||!prior.owner().equals(q.recipient())||!prior.fingerprint().equals(fingerprint))throw new IllegalStateException("This request identifier belongs to different supplies.");return prior.stage()==BanquetJournal.Stage.COMPLETE?"This gift already completed.":"This gift was cancelled; make a new request.";}
        var pending=r.journal.pending();if(pending!=null){if(!pending.request().equals(q.request())||!pending.actor().equals(actor.getUuid())||!pending.fingerprint().equals(fingerprint))throw new IllegalStateException("A banquet operation needs recovery first.");recover(server,r);return r.journal.pending()==null?"The gift outcome was recovered.":"Recovery is waiting for the recipient's exact inventory.";}
        if(RuntimeHolds.reason(server)!=null)throw new IllegalStateException("Resolve operation holds first.");
        if(q.drinks()+q.poison()+q.antidotes()<1)throw new IllegalArgumentException("Choose at least one supply.");
        ServerPlayerEntity target=server.getPlayerManager().getPlayer(q.recipient());if(target==null||players(server).stream().noneMatch(p->p.id().equals(q.recipient())))throw new IllegalStateException("Choose an online participant.");
        flush(r);var before=inventory(server,target);var stacks=new ArrayList<>(InventorySnapshots.decode(server,before));var slots=new ArrayList<Integer>();for(int i=0;i<36;i++)if(stacks.get(i).isEmpty())slots.add(i);
        int total=q.drinks()+q.poison()+q.antidotes();if(slots.size()<total)throw new IllegalStateException("The recipient needs "+total+" empty inventory slots; nothing was given.");
        BanquetState after=r.state;int index=0;for(var kind:SupplyKind.values()){int count=kind==SupplyKind.DRINK?q.drinks():kind==SupplyKind.POISON?q.poison():q.antidotes();for(int i=0;i<count;i++){UUID token=derived(q.request(),"token/"+index);after=BanquetTransitions.issue(after,after.revision(),derived(q.request(),"issue/"+index),actor.getUuid(),token,kind);stacks.set(slots.get(index),BanquetItems.stack(kind,token));index++;}}
        return transact(server,r,new BanquetJournal.Intent(q.request(),actor.getUuid(),target.getUuid(),fingerprint,name(actor)+" gave "+name(target)+" "+q.drinks()+" drinks, "+q.poison()+" doses and "+q.antidotes()+" antidotes.",ledgerHash(r.state),after,before,snapshot(server,stacks)),target);
    }
    private static void request(ServerPlayerEntity player,Request q){
        var server=player.getEntityWorld().getServer();if(server.getPlayerManager().getPlayer(player.getUuid())!=player||!CouncilRuntime.active())return;
        if(!player.hasPermissionLevel(2)&&q.action()!=Action.LOG)return;
        long now=System.nanoTime();Long last=REQUESTS.get(player);
        var known=WORLDS.get(server);boolean receipt=q.action()==Action.GIFT&&known!=null&&known.journal.get(q.request())!=null;
        if(last!=null&&now-last<100_000_000L&&!receipt){send(player,q.request(),q.action()==Action.OPEN,q.action()==Action.LOG,"Please wait briefly and retry.");return;}
        REQUESTS.put(player,now);
        String message="";boolean log=q.action()==Action.LOG;
        try{
            Runtime r=runtime(server);ready(r);
            switch(q.action()){
                case OPEN -> { }
                case LOG -> {if(!CouncilRuntime.isChancellor(server,player.getUuid()))return;}
                case SAVE -> {if(!editable(player)||q.revision()!=r.revision||q.config()==null)throw new IllegalStateException("Enable Editing outside active play and reopen current settings.");flush(r);r.settings.save(q.config());r.revision=Math.incrementExact(r.revision);message="Settings saved.";}
                case GIFT -> message=gift(player,q);
                case RECOVER -> {recover(server,r);message=r.journal.pending()==null?"Banquet records recovered.":"Exact inventory recovery is still required.";}
            }
        }catch(Exception e){message=e instanceof IOException?"Banquet storage needs operator recovery; preserve its files.":Objects.toString(e.getMessage(),"Banquet action unavailable.");if(e instanceof IOException)hold(server,message);}
        if(!BanquetTrial.suppressGiftReply(player,q))send(player,q.request(),q.action()==Action.OPEN,log,message);
    }
    private static void send(ServerPlayerEntity player,UUID request,boolean opening,boolean logView,String message){
        if(!ServerPlayNetworking.canSend(player,View.ID))return;var server=player.getEntityWorld().getServer();var r=WORLDS.get(server);boolean canLog=CouncilRuntime.isChancellor(server,player.getUuid());
        if(!player.hasPermissionLevel(2)&&!canLog)return;
        List<LogEntry> log=List.of();if(logView&&canLog&&r!=null){var records=r.journal.receipts();log=records.subList(Math.max(0,records.size()-64),records.size()).stream().map(o->new LogEntry(o.description()+" ["+o.stage().name().toLowerCase(Locale.ROOT)+"]")).toList();}
        boolean held=r==null||r.failed||r.journal.pending()!=null||r.journal.poisoned();
        ServerPlayNetworking.send(player,new View(request,opening,r==null?0:r.revision,r==null?BanquetConfig.defaults():r.settings.config(),r!=null&&editable(player),player.hasPermissionLevel(2)&&!held,canLog,player.hasPermissionLevel(2)?players(server):List.of(),log,message.substring(0,Math.min(512,message.length())),logView&&canLog,held,r==null?NONE:r.worldId));
    }
    private static void tick(MinecraftServer server){
        Runtime r=WORLDS.get(server);if(r==null||r.failed)return;
        try{
            if(r.journal.pending()!=null){
                if(++r.ticks%20==0){var op=r.journal.pending();var owner=server.getPlayerManager().getPlayer(op.owner());
                    if(owner==null)r.inspected="";
                    else{String evidence=op.request()+"/"+System.identityHashCode(owner)+"/"+hash(inventory(server,owner).toString().getBytes(StandardCharsets.UTF_8));if(!evidence.equals(r.inspected)){r.inspected=evidence;recover(server,r);}}}
                return;
            }
            boolean advancing=r.settings.config().enabled()&&EventSources.activeOperation(server)&&RuntimeHolds.reason(server)==null;
            Set<UUID> eligible=new HashSet<>();if(advancing)for(var player:PlayRuntime.eligiblePlayers(server)){eligible.add(player.getUuid());if(r.state.illnesses().containsKey(player.getUuid()))r.elapsed.merge(player.getUuid(),1L,Math::addExact);}
            if(++r.ticks%20!=0)return;
            flush(r);
            for(var player:server.getPlayerManager().getPlayerList()){
                var illness=r.state.illnesses().get(player.getUuid());
                if(eligible.contains(player.getUuid())&&illness!=null&&illness.active())symptoms(r,player);else clearSymptoms(r,player);
            }
        }catch(Exception e){r.failed=true;io.github.chronicle.core.ChronicleCore.LOGGER.error("Banquet progression held for exact recovery",e);hold(server,"Banquet records need recovery; preserve their retained files.");}
    }
    private static void symptoms(Runtime r,ServerPlayerEntity player){
        List<StatusEffectInstance> ours=r.symptoms.computeIfAbsent(player.getUuid(),ignored->new ArrayList<>());
        ours.removeIf(instance->player.getStatusEffect(instance.getEffectType())!=instance);
        for(var effect:List.of(StatusEffects.NAUSEA,StatusEffects.SLOWNESS)){
            var old=player.getStatusEffect(effect);boolean owned=old==null||ours.contains(old);
            if(!owned)continue;
            APPLYING.set(true);try{player.addStatusEffect(new StatusEffectInstance(effect,40,0,false,false,true));}finally{APPLYING.remove();}
            if(owned){var applied=player.getStatusEffect(effect);if(applied!=null&&!ours.contains(applied))ours.add(applied);}
        }
    }
    public static void effectUpdated(ServerPlayerEntity player,StatusEffectInstance effect){
        if(APPLYING.get())return;var r=WORLDS.get(player.getEntityWorld().getServer());if(r==null)return;var ours=r.symptoms.get(player.getUuid());if(ours==null)return;
        var old=player.getStatusEffect(effect.getEffectType());if(old!=null)ours.removeIf(instance->instance==old);
    }
    private static void clearSymptoms(Runtime r,ServerPlayerEntity player){
        var ours=r.symptoms.remove(player.getUuid());if(ours==null)return;
        for(var effect:List.of(StatusEffects.NAUSEA,StatusEffects.SLOWNESS)){var current=player.getStatusEffect(effect);if(current!=null&&ours.contains(current)&&current.getAmplifier()==0&&current.getDuration()<=40)player.removeStatusEffect(effect);}
    }
}
