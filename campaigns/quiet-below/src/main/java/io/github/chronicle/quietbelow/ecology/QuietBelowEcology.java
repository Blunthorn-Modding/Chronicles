package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import io.github.chronicle.core.server.*;
import io.github.chronicle.quietbelow.QuietBelow;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import io.github.chronicle.quietbelow.content.AncientAltars;
import io.github.chronicle.quietbelow.content.RelicHeartBlock;
import net.minecraft.block.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import io.github.chronicle.quietbelow.content.SculkAppearance;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import java.io.IOException;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;

/** Campaign lifecycle and the sole Minecraft adapter for ecology. */
public final class QuietBelowEcology {
    private static final int MAX_WARDEN_SPAWN_CANDIDATES=128;
    public static final String TOOL="quiet_below:ecology";
    private static final Map<MinecraftServer,Runtime> WORLDS=new IdentityHashMap<>();
    private static final Map<ServerWorld,Set<ChunkPos>> LOADED=new IdentityHashMap<>();
    private static final Set<ServerWorld> LOAD_OVERFLOW=Collections.newSetFromMap(new IdentityHashMap<>());
    private QuietBelowEcology() { }

    /** Read-only structural root authority used by Core's managed-placement policy. */
    public static RootAuthority rootAuthority(MinecraftServer server) {
        Runtime runtime = WORLDS.get(server);
        if (runtime == null) return new RootAuthority(RootPhase.UNKNOWN, null, null, 0, null);
        EcologyEngine engine = runtime.engine;
        EcologyEngine.Setup setup = engine.setup();
        if (setup == null) return new RootAuthority(RootPhase.UNBOUND, null, null, 0, null);
        Pos root = engine.rootPosition();
        io.github.chronicle.core.authoring.BlockPosition position = root == null ? null
                : new io.github.chronicle.core.authoring.BlockPosition(root.x(), root.y(), root.z());
        RootPhase phase = engine.ending() == null ? RootPhase.BOUND : RootPhase.ENDED;
        return new RootAuthority(phase, setup.heart(), setup.dimension(), setup.heartRevision(), position);
    }

    public enum RootPhase { UNKNOWN, UNBOUND, BOUND, ENDED }
    public record RootAuthority(RootPhase phase, String siteId, String dimension, long siteRevision,
                                io.github.chronicle.core.authoring.BlockPosition position) { }

    public static void register() {
        EcologyEradication.register();
        PlayerBlockBreakEvents.BEFORE.register((world,player,p,state,be)->{
            if(!(world instanceof ServerWorld w)||!protectsRoot(w,p))return true;
            if(player instanceof ServerPlayerEntity actor)destroyHeart(actor,w,p);return false;
        });
        registerTool(new GameSetupTools.Tool("quiet_below:heart","Heart weakness",
                "Below the living sculk threshold, the Heart weakens during active play. A participating Survival or Adventure player can mine the vulnerable bound Heart with a pickaxe. Unloaded branches prevent vulnerability. Cleanup restores recorded terrain as chunks naturally load.",
                List.of(new GameSetupTools.Verb("follow_extent","Follow Established threshold",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("threshold_lower","Lower weakness threshold",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("threshold_higher","Raise weakness threshold",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("delay_shorter","Shorter weakness delay",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("delay_longer","Longer weakness delay",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("cooldown_shorter","Shorter last stand cooldown",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("cooldown_longer","Longer last stand cooldown",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("cooldown_reset","Default last stand cooldown",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("reset","Use default weakness",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("show_root","Show root",GameSetupTools.Guard.READ_ONLY,false),
                        new GameSetupTools.Verb("refresh","Refresh status",GameSetupTools.Guard.READ_ONLY,false)),
                QuietBelowEcology::heartStatus,QuietBelowEcology::heartAction));
        registerTool(new GameSetupTools.Tool(TOOL,"Sculk ecology",
                "Quiet Below growth. Assign Source city and Relic Heart first, then index existing city sculk. " +
                "Growth follows exposed surfaces during occupied active play. Continuous sculk links to the Heart; a complete cut makes the detached branch dormant. " +
                "Placed sculk reconnects through physical contact. Catalysts have no expansion range. Buildings have no protection.",
                List.of(new GameSetupTools.Verb("index","Index source city",GameSetupTools.Guard.EDIT,true),
                        new GameSetupTools.Verb("enable","Enable growth",GameSetupTools.Guard.EDIT,true),
                        new GameSetupTools.Verb("disable","Disable growth",GameSetupTools.Guard.OPERATOR,true),
                        new GameSetupTools.Verb("show_root","Show root",GameSetupTools.Guard.READ_ONLY,false),
                        new GameSetupTools.Verb("hide_root","Hide root",GameSetupTools.Guard.READ_ONLY,false),
                        new GameSetupTools.Verb("slower","Slower",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("faster","Faster",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("speed_reset","Reset speed",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("refresh","Refresh status",GameSetupTools.Guard.READ_ONLY,false)),
                QuietBelowEcology::status,QuietBelowEcology::execute));
        registerTool(new GameSetupTools.Tool("quiet_below:spread","Spread mode",
                "Choose fixed spread or amplification from living, Heart-connected sculk. Sculk ecology holds the base speed controls. Both modes retain existing growth work limits.",
                List.of(
                        new GameSetupTools.Verb("use_fixed","Use fixed rate",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("use_count","Use sculk-count rate",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("amplification_lower","Lower count amplification",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("amplification_higher","Raise count amplification",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("spread_reset","Reset growth mode",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("refresh","Refresh status",GameSetupTools.Guard.READ_ONLY,false)),
                QuietBelowEcology::spreadStatus,QuietBelowEcology::execute));
        registerTool(new GameSetupTools.Tool("quiet_below:vitality","Sculk vitality",
                "Vitality and extent tuning. Detached or isolated sealed sculk loses vitality but keeps its blocks. Directly joined Heart-connected sculk shares nourishment; air and sunlight still govern growth exposure and bright recovery. No XP upkeep or oxygen simulation.",
                List.of(new GameSetupTools.Verb("loss_slower","Slower vitality loss",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("loss_faster","Faster vitality loss",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("recovery_slower","Slower recovery",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("recovery_faster","Faster recovery",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("extent_lower","Lower extent thresholds",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("extent_higher","Higher extent thresholds",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("reset","Reset vitality tuning",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("refresh","Refresh status",GameSetupTools.Guard.READ_ONLY,false)),
                QuietBelowEcology::vitalityStatus,QuietBelowEcology::vitalityAction));
        registerTool(new GameSetupTools.Tool("quiet_below:ownership","Sculk ownership",
                "Adopt existing sculk in a saved region before enabling optional world-wide cleanup. Cleanup permanently deletes non-campaign sculk in the Heart's dimension as chunks normally load; no terrain is force-loaded.",
                List.of(new GameSetupTools.Verb("claim","Take over region",GameSetupTools.Guard.EDIT,true),
                        new GameSetupTools.Verb("cancel","Stop region takeover",GameSetupTools.Guard.EDIT,true),
                        new GameSetupTools.Verb("cleanup_on","Delete non-campaign sculk",GameSetupTools.Guard.EDIT,true),
                        new GameSetupTools.Verb("cleanup_off","Stop sculk cleanup",GameSetupTools.Guard.OPERATOR,false),
                        new GameSetupTools.Verb("refresh","Refresh status",GameSetupTools.Guard.READ_ONLY,false)),
                QuietBelowEcology::ownershipStatus,QuietBelowEcology::ownershipAction));
        registerTool(new GameSetupTools.Tool("quiet_below:crops","Crop growth",
                "Living sculk helps nearby wheat, carrots, potatoes and beetroot grow. No farm binding or discovery event is required. Normal farmland/light conditions apply; crops remain after the network is cut.",
                List.of(new GameSetupTools.Verb("enable","Enable crop boost",GameSetupTools.Guard.EDIT,true),
                        new GameSetupTools.Verb("disable","Disable crop boost",GameSetupTools.Guard.OPERATOR,true),
                        new GameSetupTools.Verb("faster","Faster crop growth",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("slower","Slower crop growth",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("reset","Reset crop tuning",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("refresh","Refresh status",GameSetupTools.Guard.READ_ONLY,false)),
                QuietBelowEcology::cropStatus,QuietBelowEcology::cropAction));
        registerTool(new GameSetupTools.Tool("quiet_below:recovery","Local healing",
                "Established living sculk slowly heals nearby injured participants. Leaving its reach or cutting its roots stops the benefit.",
                List.of(new GameSetupTools.Verb("enable","Enable local healing",GameSetupTools.Guard.EDIT,true),
                        new GameSetupTools.Verb("disable","Disable local healing",GameSetupTools.Guard.OPERATOR,true),
                        new GameSetupTools.Verb("faster","Faster healing",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("slower","Slower healing",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("reset","Reset healing tuning",GameSetupTools.Guard.EDIT,false),
                        new GameSetupTools.Verb("refresh","Refresh status",GameSetupTools.Guard.READ_ONLY,false)),
                QuietBelowEcology::recoveryStatus,QuietBelowEcology::recoveryAction));
        for(var kind:EcologyCosts.Kind.values()){
            String name=kind==EcologyCosts.Kind.HUNGER?"Local Hunger":"Local Darkness";
            registerTool(new GameSetupTools.Tool(kind==EcologyCosts.Kind.HUNGER?"quiet_below:hunger":"quiet_below:darkness",name,
                    "Standing on living sculk starts an exposure wait. The cost then persists until you land off living sculk; jumping keeps exposure and a pillar provides shelter. Existing potion and Warden effects are preserved.",
                    List.of(new GameSetupTools.Verb("enable","Enable "+name,GameSetupTools.Guard.EDIT,true),
                            new GameSetupTools.Verb("disable","Disable "+name,GameSetupTools.Guard.OPERATOR,true),
                            new GameSetupTools.Verb("faster","Shorter exposure delay",GameSetupTools.Guard.EDIT,false),
                            new GameSetupTools.Verb("slower","Longer exposure delay",GameSetupTools.Guard.EDIT,false),
                            new GameSetupTools.Verb("reset","Reset exposure tuning",GameSetupTools.Guard.EDIT,false),
                            new GameSetupTools.Verb("refresh","Refresh status",GameSetupTools.Guard.READ_ONLY,false)),
                    source->costStatus(source,kind),(source,verb)->costAction(source,verb,kind)));
        }
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->{
            Set<ChunkPos> loaded=LOADED.computeIfAbsent(world,k->new LinkedHashSet<>());
            Runtime r=WORLDS.get(world.getServer());
            if(loaded.size()>=EcologyCleanup.MAX_CHUNKS&&!loaded.contains(chunk.getPos())){
                LOAD_OVERFLOW.add(world);
                if(r!=null&&r.engine.cleanup())r.engine.fail("Cleanup loaded-chunk tracking capacity reached; no chunk was force-loaded.");return;
            }
            loaded.add(chunk.getPos());
            if(cleanupWorld(world,r))try{r.cleanup.load(chunk.getPos().x,chunk.getPos().z);}catch(Exception e){hold(r,e);}
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((world,chunk)->{
            Set<ChunkPos> loaded=LOADED.get(world);if(loaded!=null)loaded.remove(chunk.getPos());
            Runtime r=WORLDS.get(world.getServer());if(r!=null&&r.cleanup!=null&&r.engine.setup().dimension().equals(world.getRegistryKey().getValue().toString()))r.cleanup.unload(chunk.getPos().x,chunk.getPos().z);
            if(r!=null&&r.engine.setup()!=null&&r.engine.setup().dimension().equals(world.getRegistryKey().getValue().toString()))
                try{r.engine.chunkUnloaded(chunk.getPos().x,chunk.getPos().z);}catch(Exception e){hold(r,e);}
        });
        ServerLifecycleEvents.SERVER_STARTED.register(QuietBelowEcology::start);
        ServerTickEvents.START_SERVER_TICK.register(server->{Runtime r=WORLDS.get(server);if(r!=null){r.engine.beginTick();r.settlingWrites=0;}});
        ServerTickEvents.END_SERVER_TICK.register(QuietBelowEcology::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(EcologyCostEffects::clear);
        io.github.chronicle.core.server.ServerDisconnects.register((handler,server)->{
            EcologyCostEffects.disconnect(handler.player);
            Runtime r=WORLDS.get(server);if(r!=null)r.costs.resetPlayer(handler.player.getUuid());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,player,alive)->{
            EcologyCostEffects.disconnect(oldPlayer);
            EcologyCostEffects.withdrawMarked(player);
            Runtime r=WORLDS.get(player.getEntityWorld().getServer());if(r!=null)r.costs.resetPlayer(player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{
            EcologyCostEffects.forget(server);
            Runtime r=WORLDS.remove(server);if(r!=null){
                try{r.engine.flushHeart();r.engine.flushVitality();}catch(Exception e){QuietBelow.LOGGER.error("Ecology vitality shutdown checkpoint failed",e);}
                try{r.journal.close();}catch(IOException e){QuietBelow.LOGGER.error("Ecology journal close failed",e);}
            }
            LOADED.keySet().removeIf(world->world.getServer()==server);
            LOAD_OVERFLOW.removeIf(world->world.getServer()==server);
        });
        EcologyHooks.register(new NativeHooks());
        EcologyNativeTrial.register();
    }
    private static void registerTool(GameSetupTools.Tool tool){
        GameSetupTools.register(new GameSetupTools.Tool(tool.id(),tool.label(),tool.description(),tool.verbs(),source->{
            var original=tool.status().apply(source);Runtime r=runtime(source.getServer());if(r.engine.ending()==null)return original;
            Map<String,String> disabled=new HashMap<>(original.disabledReasons());
            for(var verb:tool.verbs())if(verb.guard()!=GameSetupTools.Guard.READ_ONLY)disabled.put(verb.id(),"The network has ended permanently; these settings cannot restart it.");
            if(tool.id().equals("quiet_below:heart"))return new GameSetupTools.Status(original.controlRevision(),original.facts(),disabled);
            List<Fact> facts=new ArrayList<>();facts.add(new Fact("Network ended",heartDescription(r)));
            facts.add(new Fact("Historical settings","The values below are retained evidence. See Relic Heart for current cleanup progress; nothing here can restart this network."));
            for(Fact fact:original.facts())facts.add(new Fact("Saved "+fact.label(),fact.value()));
            return new GameSetupTools.Status(original.controlRevision(),facts,disabled);
        },tool.executor()));
    }
    private static void start(MinecraftServer server) {
        EcologyJournal journal=null;
        try {
            journal=new EcologyJournal(server.getSavePath(WorldSavePath.ROOT).resolve("quiet-below/ecology.jsonl"));
            EcologyEngine engine=new EcologyEngine(journal);
            HeartDefianceStore defianceStore=new HeartDefianceStore(server.getSavePath(WorldSavePath.ROOT).resolve("quiet-below/heart-defiance.json"));
            Runtime runtime=new Runtime(engine,journal,defianceStore);WORLDS.put(server,runtime);
            try{runtime.defiance=defianceStore.load();}
            catch(Exception failure){defianceFailure(runtime,"Saved Heart last-stand state is unavailable; last stands are suppressed but normal weakness remains active",failure);}
            if(engine.setup()!=null) attach(server,runtime);
            QuietBelow.LOGGER.info("Quiet Below ecology ready; configured={}, enabled={}",engine.setup()!=null,engine.enabled());
        } catch(Exception e) {
            Runtime r=WORLDS.get(server);
            if(r!=null)r.engine.fail("Ecology setup cannot be resolved: "+e.getMessage());
            else if(journal!=null)try{journal.close();}catch(IOException close){e.addSuppressed(close);}
            QuietBelow.LOGGER.error("Quiet Below ecology unavailable; existing journal preserved",e);
        }
    }
    private static void tick(MinecraftServer server) {
        Runtime r=WORLDS.get(server);if(r==null){EcologyCostEffects.clear(server);return;}
        if(r.engine.failure()!=null){heartRestingAppearance(server,r);EcologyCostEffects.clear(server);return;}
        if(r.engine.setup()==null){EcologyCostEffects.clear(server);return;}
        long started=System.nanoTime();
        try {
            if(r.engine.ending()!=null){endingTick(server,r);EcologyCostEffects.clear(server);return;}
            attach(server,r);
            Adapter world=adapter(server,r.engine.setup().dimension());
            // Indexing is explicit preparation, never starts from simply registering a role.
            if(!r.engine.view().indexed()) {
                EcologyCostEffects.clear(server);projectHeartAppearance(world.world,r,RelicHeartBlock.Lens.RESTING,false);
                // Explicit metadata preparation needs the exact pins and loaded source chunks,
                // not the Heart chunk. Otherwise walking a large city can never finish its edges.
                if(ChronicleRuntime.service(server).state().phase()!=SessionPhase.ACTIVE)r.engine.index(world);
                return;
            }
            if(r.engine.claim()!=null&&!r.engine.claim().complete()) {
                r.claimProblem=claimProblem(server,r.engine.claim());
                if(r.claimProblem==null&&ChronicleRuntime.service(server).state().phase()!=SessionPhase.ACTIVE)r.engine.claimTick(world);
            } else r.claimProblem=null;
            if(r.rootProblem==null)r.engine.tick(world,playing(server));else r.engine.flushVitality();
            int priorHeartElapsed=r.engine.heartElapsed();
            if(priorHeartElapsed<r.lastHeartElapsed)resetDefianceSignals(r);
            EcologyHeart.Transition heart=r.engine.heartTick(r.rootProblem==null,playing(server));
            if(heart.current()==0&&priorHeartElapsed>0)resetDefianceSignals(r);
            if((heart.pulse()||heart.lastEffort())&&authorizeDefiance(server,r,heart)){
                boolean lastGrowth=heart.lastEffort()&&r.engine.heartLastEffort(world,r.rootProblem==null,heart);
                if(heart.lastEffort()) {
                    r.wardenAttempted=true;
                    r.wardenSpawned=applyDefianceEffect(r,"The Heart's final creature could not emerge",
                            ()->spawnLastStandWarden(world.world,r.engine.rootPosition()));
                    QuietBelow.LOGGER.info("Heart last stand attempted: root={}, growthQueued={}, Warden={}",
                            r.engine.rootPosition(),lastGrowth,r.wardenSpawned?"spawned":"no safe loaded spawn");
                }
                if(heart.pulse()) {
                    boolean emitted=applyDefianceEffect(r,"The Heart's heartbeat could not be emitted",
                            ()->heartHeartbeat(world.world,r));
                    if(emitted)r.heartbeatTicks.add(heart.current());
                }
                boolean visible=lastGrowth||r.wardenSpawned;
                applyDefianceEffect(r,"The Heart's last-stand signal could not be emitted",
                        ()->{heartPulse(world.world,r,heart.lastEffort(),visible);return true;});
            }
            var burst=r.engine.heartBurstOutcome();
            if(!burst.active()&&!burst.reason().equals("idle")&&!burst.equals(r.loggedBurst)) {
                r.loggedBurst=burst;
                QuietBelow.LOGGER.info("Heart final growth finished: root={}, planned={}, checked={}, written={}, result={}",
                        r.engine.rootPosition(),burst.planned(),burst.checked(),burst.written(),burst.reason());
            }
            r.lastHeartElapsed=heart.current();
            if(server.getTicks()%20==0)heartAppearance(world.world,r);
            if(!r.crops.rules().equals(r.engine.cropRules()))r.crops=new EcologyCrops(r.engine.cropRules());
            try{r.crops.tick(new EcologyCropWorld(world.world),r.engine,r.rootProblem==null&&playing(server));}
            catch(Exception e){r.crops.fail("Crop boost held: "+e.getMessage());QuietBelow.LOGGER.error("Quiet Below crop boost held; ecology retained",e);}
            if(!r.recovery.rules().equals(r.engine.recoveryRules()))r.recovery=new EcologyRecovery(r.engine.recoveryRules());
            try{r.recovery.tick(new EcologyRecoveryWorld(server,world.world,r.engine,r.recovery,()->r.rootProblem==null&&playing(server)),r.engine,r.rootProblem==null&&playing(server));}
            catch(Exception e){r.recovery.fail("Local healing held: "+e.getMessage());QuietBelow.LOGGER.error("Quiet Below local healing held; ecology retained",e);}
            if(!r.costs.rules().equals(r.engine.costRules())){EcologyCostEffects.clear(server);r.costs=new EcologyCosts(r.engine.costRules());}
            try{r.costs.tick(new EcologyCostsWorld(server,world.world,r.engine,r.costs,()->r.rootProblem==null
                    &&ChronicleRuntime.service(server).state().phase()==SessionPhase.ACTIVE&&RuntimeHolds.reason(server)==null),r.engine,r.rootProblem==null&&playing(server));}
            catch(Exception e){EcologyCostEffects.clear(server);r.costs.fail("Local costs held: "+e.getMessage());QuietBelow.LOGGER.error("Quiet Below local costs held; ecology retained",e);}
            if(r.engine.cleanup())r.cleanup.tick(world,r.engine,playing(server));
        } catch(Exception e) { EcologyCostEffects.clear(server);hold(r,e); }
        finally {r.lastTickNanos=System.nanoTime()-started;}
    }
    public static boolean playing(MinecraftServer server) {
        try {
            return ChronicleRuntime.service(server).state().phase()==SessionPhase.ACTIVE && RuntimeHolds.reason(server)==null
                    && !PlayRuntime.eligiblePlayers(server).isEmpty();
        } catch(IllegalStateException e) {return false;}
    }
    private record StationReadiness(String unavailable, int heat) {
        static StationReadiness off(String reason) { return new StationReadiness(reason, 0); }
    }
    /** Loaded-only, six-neighbor physical station power, independent of authored station bindings. */
    public static String stationPower(ServerWorld world, BlockPos station) {
        return stationReadiness(world, station).unavailable();
    }
    /** The Forge uses the same verified power proof as crafting, plus the halfway kindling stage. */
    public static int forgeHeat(ServerWorld world, BlockPos station) {
        return stationReadiness(world, station).heat();
    }
    private static StationReadiness stationReadiness(ServerWorld world, BlockPos station) {
        try {
            var server = world.getServer(); var r = runtime(server); var e = r.engine;
            if (e.ending() != null) return StationReadiness.off("The Relic Heart was destroyed; this network has ended permanently.");
            if (!playing(server)) return StationReadiness.off("The network rests while play is paused, empty or held.");
            if (e.setup() == null || !e.setup().dimension().equals(world.getRegistryKey().getValue().toString()))
                return StationReadiness.off("No campaign network is configured in this dimension.");
            Pins root = pins(server);
            if (!root.setup.equals(e.setup())) return StationReadiness.off("Source or Heart bindings changed; resolve ecology setup first.");
            var rootChunk = EcologyLoadedBlocks.chunk(world, blockPos(root.heart));
            if (rootChunk == null || !Registries.BLOCK.getId(rootChunk.getBlockState(blockPos(root.heart)).getBlock()).toString().equals(root.heartBlock))
                return StationReadiness.off("The living Relic Heart is missing or unloaded.");
            String small = "The network needs connected Established or Extensive living sculk.";
            int heat = e.forgeHeat();
            if (r.rootProblem != null || heat == 0) return StationReadiness.off(small);
            for (Direction direction : Direction.values()) {
                BlockPos at = station.offset(direction); var chunk = EcologyLoadedBlocks.chunk(world, at);
                if (chunk != null && e.living(pos(at), encode(chunk.getBlockState(at))))
                    return new StationReadiness(heat == 2 ? null : small, heat);
            }
            return StationReadiness.off(heat == 1 ? small : "Place the station directly beside connected living sculk (at least25% vitality).");
        } catch (IllegalStateException error) {
            return StationReadiness.off("The network is unavailable: " + error.getMessage());
        }
    }
    private static GameSetupTools.Status status(ServerCommandSource source) {
        Runtime r=runtime(source.getServer());EcologyEngine.View v=r.engine.view();
        Pins root=null;String rootIssue=null;
        try{root=visibleRoot(source.getServer(),r);}catch(Exception e){rootIssue=e.getMessage();}
        List<Fact> facts=List.of(new Fact("Root (Relic Heart)",root==null?rootIssue:coordinates(root)),
                new Fact("Growth",growthStatus(source.getServer(),r,v)),
                new Fact("Spread speed",speedLabel(r.engine)),
                new Fact("Growth mode",spreadLabel(r.engine)),
                new Fact("Vitality",r.engine.vitalitySummary().rootedPercent()+"% rooted average; "+r.engine.vitalitySummary().ready()+" ready rooted cells. See Sculk vitality for recovery/tuning."),
                new Fact("Speed controls","Set the base rate from 0.25x to 16x here. See Spread mode for fixed/sculk-count mode and amplification. Pause play and enable editing to change; these are attempt intervals."),
                new Fact("Living connection",!v.configured()?"No network indexed yet.":v.unresolved()?"Checking loaded sculk connections; expansion waits.":v.connected()==0?
                        "None. Detached sculk is dormant. Restore continuous sculk contact with the Heart to reconnect it.":
                        "Continuous sculk contact with the Heart; cutting every contact makes a branch dormant."),
                new Fact("Source index",!v.configured()?"Not prepared":v.indexed()?"Complete":v.completedChunks()+" / "+v.totalChunks()+" chunks; visit remaining source chunks"),
                new Fact("Network",v.owned()+" tracked; "+v.connected()+" connected; "+Math.max(0,v.owned()-v.connected())+" detached; "+v.extent()+(v.unresolved()?" (counts pending check)":"")),
                new Fact("How to test","Show root, then close this menu to see its private outline. Break every sculk connection to a branch; replace sculk to reconnect. Sensors are audible evidence, not a growth meter."),
                new Fact("Surface frontier",v.frontier()+" discovered air cells"),
                new Fact("Persistence",v.journalBytes()+" bytes; append-only history"),
                new Fact("Review scope","Surface growth, vitality, benefits, costs, stations and permanent Heart destruction. See Relic Heart for weakness and cleanup."));
        Map<String,String> disabled=new HashMap<>();
        if(!v.indexed())for(String verb:List.of("slower","faster","speed_reset","use_fixed","use_count","amplification_lower","amplification_higher","spread_reset"))disabled.put(verb,"Complete source indexing before changing growth mode.");
        if(r.engine.speedPercent()==EcologyEngine.SPEEDS.getFirst())disabled.put("slower","Minimum speed: 0.25x.");
        if(r.engine.speedPercent()==EcologyEngine.SPEEDS.getLast())disabled.put("faster","Maximum speed: 16x; at most one attempt per game tick.");
        if(r.engine.speedPercent()==100)disabled.put("speed_reset","Spread speed is already 1x.");
        var spread=r.engine.spreadRules();
        if(spread.mode()==EcologyEngine.GrowthMode.FIXED)disabled.put("use_fixed","Fixed rate is already active.");else disabled.put("use_count","Sculk-count rate is already active.");
        if(spread.amplificationPercent()==EcologyEngine.AMPLIFICATIONS.getFirst())disabled.put("amplification_lower","Minimum amplification: 0% per 1,000 sculk.");
        if(spread.amplificationPercent()==EcologyEngine.AMPLIFICATIONS.getLast())disabled.put("amplification_higher","Maximum amplification: 100% per 1,000 sculk.");
        if(spread.equals(EcologyEngine.DEFAULT_SPREAD))disabled.put("spread_reset","Growth mode is already at its defaults.");
        if(v.hold()!=null)for(String verb:List.of("slower","faster","speed_reset","use_fixed","use_count","amplification_lower","amplification_higher","spread_reset"))disabled.put(verb,v.hold());
        if(root==null)disabled.put("show_root",rootIssue);
        else if(source.getPlayer()==null||!source.getPlayer().getEntityWorld().getRegistryKey().getValue().toString().equals(root.setup.dimension()))
            disabled.put("show_root","Visit the root dimension: "+root.setup.dimension());
        if(v.configured())disabled.put("index","Already indexed or indexing; source ownership is not reset by this menu.");
        else try{pins(source.getServer());}catch(Exception e){disabled.put("index",e.getMessage());}
        if(!v.indexed())disabled.put("enable","Complete the loaded source index first.");
        if(v.enabled())disabled.put("enable","Growth is already enabled.");
        if(!v.configured()||!v.enabled())disabled.put("disable","Growth is already disabled.");
        if(v.hold()!=null){disabled.put("enable",v.hold());disabled.put("index",v.hold());}
        if(r.rootProblem!=null)disabled.put("enable",r.rootProblem);
        if(r.engine.ending()!=null)disabled.put("enable","The Heart was destroyed; the network cannot be restarted.");
        return new GameSetupTools.Status(v.revision()+r.menuRevision,facts,disabled);
    }
    private static GameSetupTools.Status spreadStatus(ServerCommandSource source){
        Runtime r=runtime(source.getServer());var all=status(source);var rules=r.engine.spreadRules();
        return new GameSetupTools.Status(all.controlRevision(),List.of(
                new Fact("Growth mode",spreadLabel(r.engine)),new Fact("Spread speed",speedLabel(r.engine)),
                new Fact("Amplification","+"+rules.amplificationPercent()+"% per 1,000 living, Heart-connected sculk. Dormant/disconnected sculk does not count."),
                new Fact("Count",r.engine.countedSculk()+" living, Heart-connected cells counted; confirmed losses are discounted from the last bounded summary."),
                new Fact("Attempts",r.engine.spreadAttempts()+" since engine load. Each attempt may find no valid surface; this is not a count of new blocks."),
                new Fact("Formula","Effective rate = base x (1 + count x amplification / 100000), capped at 16x. Set the base speed under Sculk ecology."),
                new Fact("Growth",growthStatus(source.getServer(),r,r.engine.view())),
                new Fact("Limits","Attempt speed only; existing loaded-only work, terrain-write limits and physical barriers remain.")),all.disabledReasons());
    }
    private static GameSetupTools.Status cropStatus(ServerCommandSource source){
        Runtime r=runtime(source.getServer());var e=r.engine;var v=e.view();var rules=e.cropRules();
        List<Fact> facts=List.of(new Fact("Crop boost",r.crops.failure()!=null?"HELD: "+r.crops.failure():
                        !rules.enabled()?"Disabled":!e.cropsAvailable()?"Waiting for an enabled, resolved network":
                        !playing(source.getServer())?"Paused; requires occupied active play":"Running near living sculk"),
                new Fact("Crops and reach","Wheat, carrots, potatoes and beetroot within 4 blocks of rooted sculk with at least 25% vitality. No farm binding."),
                new Fact("Bonus interval","At least "+rules.seconds()+" eligible seconds per crop; one extra normal age, independent of nearby sculk count."),
                new Fact("Conditions","Valid farmland and native growth light (9+). No planting, harvesting or random-tick changes. Mature/unsupported crops are untouched."),
                new Fact("Discovery",r.crops.cached()+" cached crops; "+r.crops.sections()+" nearby sections. Existing farms are found in bounded passes; new plantings are noticed."),
                new Fact("Since last reset",r.crops.bonuses()+" bonus age increments; "+r.crops.ticks()+" eligible crop ticks. Discovery/cooldowns restart with a full interval."),
                new Fact("Work bounds","128 discovery reads + 16 planting notices + 16 crop checks, at most 2 bonus writes per eligible tick; no forced chunks."),
                new Fact("Other benefits","Established living sculk can also heal nearby participants; see Local healing. Hunger, Darkness and stations follow later."));
        Map<String,String> disabled=new HashMap<>();
        if(rules.enabled())disabled.put("enable","Crop boost is already enabled.");else disabled.put("disable","Crop boost is already disabled.");
        if(rules.seconds()==EcologyCrops.SECONDS.getFirst())disabled.put("faster","Minimum interval: 2 eligible seconds.");
        if(rules.seconds()==EcologyCrops.SECONDS.getLast())disabled.put("slower","Maximum interval: 40 eligible seconds.");
        if(rules.equals(EcologyCrops.DEFAULT)&&r.crops.failure()==null)disabled.put("reset","Crop tuning is already at its defaults.");
        String issue=!v.indexed()?"Complete source indexing before tuning crop growth.":e.failure();
        if(issue!=null)for(String id:List.of("enable","disable","faster","slower","reset"))disabled.put(id,issue);
        return new GameSetupTools.Status(v.revision()+r.menuRevision,facts,disabled);
    }
    private static String cropAction(ServerCommandSource source,String verb)throws Exception {
        Runtime r=runtime(source.getServer());var old=r.engine.cropRules();var next=old;
        switch(verb){
            case "enable" -> next=new EcologyCrops.Rules(true,old.seconds());
            case "disable" -> next=new EcologyCrops.Rules(false,old.seconds());
            case "faster","slower" -> next=new EcologyCrops.Rules(old.enabled(),EcologyCrops.SECONDS.get(EcologyCrops.SECONDS.indexOf(old.seconds())+(verb.equals("faster")?-1:1)));
            case "reset" -> next=EcologyCrops.DEFAULT;
            case "refresh" -> {r.menuRevision++;return "Crop growth status refreshed.";}
            default -> throw new IllegalArgumentException("Unknown crop growth action");
        }
        r.engine.tuneCrops(next);r.crops=new EcologyCrops(next);r.menuRevision++;
        QuietBelow.LOGGER.info("Quiet Below crop tuning {} by {}",verb,source.getName());
        return "Crop boost "+(next.enabled()?"enabled":"disabled")+"; minimum bonus interval "+next.seconds()+" eligible seconds. Discovery/cooldowns restarted; existing crops retained.";
    }
    private static GameSetupTools.Status recoveryStatus(ServerCommandSource source){
        Runtime r=runtime(source.getServer());var e=r.engine;var rules=e.recoveryRules();Map<String,String> disabled=new HashMap<>();
        if(rules.enabled())disabled.put("enable","Local healing is already enabled.");else disabled.put("disable","Local healing is already disabled.");
        if(rules.seconds()==EcologyRecovery.SECONDS.getFirst())disabled.put("faster","Minimum interval: 2 eligible seconds.");
        if(rules.seconds()==EcologyRecovery.SECONDS.getLast())disabled.put("slower","Maximum interval: 40 eligible seconds.");
        if(rules.equals(EcologyRecovery.DEFAULT)&&r.recovery.failure()==null)disabled.put("reset","Healing tuning is already at its defaults.");
        String issue=!e.view().indexed()?"Complete source indexing before tuning local healing.":e.failure();
        if(issue!=null)for(String verb:List.of("enable","disable","faster","slower","reset"))disabled.put(verb,issue);
        String state=r.recovery.failure()!=null?"HELD: "+r.recovery.failure():!rules.enabled()?"Disabled":r.rootProblem!=null?r.rootProblem:
                !playing(source.getServer())?"Paused; requires occupied active play":!e.recoveryAvailable()?"Waiting for resolved Established or Extensive living sculk":"Running near living sculk";
        return new GameSetupTools.Status(e.view().revision()+r.menuRevision,List.of(
                new Fact("Local healing",state),
                new Fact("Reach","4 blocks from owned, rooted sculk at 25%+ vitality. Established or Extensive network; no Altar needed."),
                new Fact("Pace","Up to half a heart, at least "+rules.seconds()+" eligible seconds apart. Nearby sources do not stack."),
                new Fact("Players","Injured survival/adventure participants in the Heart's dimension. Editing, Creative and Spectator modes do not qualify."),
                new Fact("Fresh wait","Leaving reach, full health, lost power/eligibility, pause and restart reset the wait."),
                new Fact("Normal healing","Minecraft's natural regeneration, food and potion effects keep their normal behavior."),
                new Fact("Since last reset",r.recovery.healed()+" successful healing pulses; "+r.recovery.tracked()+" tracked participants."),
                new Fact("Groups","Players are checked in turn; large groups can wait longer than the selected minimum interval.")),disabled);
    }
    private static String recoveryAction(ServerCommandSource source,String verb)throws Exception {
        Runtime r=runtime(source.getServer());var old=r.engine.recoveryRules();var next=old;
        switch(verb){
            case "enable" -> next=new EcologyRecovery.Rules(true,old.seconds());
            case "disable" -> next=new EcologyRecovery.Rules(false,old.seconds());
            case "faster","slower" -> next=new EcologyRecovery.Rules(old.enabled(),EcologyRecovery.SECONDS.get(EcologyRecovery.SECONDS.indexOf(old.seconds())+(verb.equals("faster")?-1:1)));
            case "reset" -> next=EcologyRecovery.DEFAULT;
            case "refresh" -> {r.menuRevision++;return "Local healing status refreshed.";}
            default -> throw new IllegalArgumentException("Unknown local healing action");
        }
        r.engine.tuneRecovery(next);r.recovery=new EcologyRecovery(next);r.menuRevision++;
        QuietBelow.LOGGER.info("Quiet Below healing tuning {} by {}",verb,source.getName());
        return "Local healing "+(next.enabled()?"enabled":"disabled")+"; interval "+next.seconds()+" eligible seconds. Cooldowns restarted.";
    }
    private static GameSetupTools.Status costStatus(ServerCommandSource source,EcologyCosts.Kind kind){
        Runtime r=runtime(source.getServer());var e=r.engine;var rule=kind==EcologyCosts.Kind.HUNGER?e.costRules().hunger():e.costRules().darkness();
        Map<String,String> disabled=new HashMap<>();
        if(rule.enabled())disabled.put("enable","Already enabled.");else disabled.put("disable","Already disabled.");
        if(rule.seconds()==EcologyCosts.SECONDS.getFirst())disabled.put("faster","Minimum initial delay: 10 eligible seconds.");
        if(rule.seconds()==EcologyCosts.SECONDS.getLast())disabled.put("slower","Maximum initial delay: 120 eligible seconds.");
        if(rule.equals(EcologyCosts.DEFAULT.hunger())&&r.costs.failure()==null)disabled.put("reset","Exposure tuning is already at its defaults.");
        String issue=!e.view().indexed()?"Complete source indexing before tuning local costs.":e.failure();
        if(issue!=null)for(String verb:List.of("enable","disable","faster","slower","reset"))disabled.put(verb,issue);
        String state=r.costs.failure()!=null?"HELD: "+r.costs.failure():!rule.enabled()?"Disabled":r.rootProblem!=null?r.rootProblem:
                !playing(source.getServer())?"Paused; requires occupied active play":!e.costsAvailable()?"Waiting for resolved Established or Extensive living sculk":
                kind==EcologyCosts.Kind.DARKNESS&&!e.extensive()?"Waiting for Extensive growth":"Running on living sculk";
        return new GameSetupTools.Status(e.view().revision()+r.menuRevision,List.of(
                new Fact(kind==EcologyCosts.Kind.HUNGER?"Local Hunger":"Local Darkness",state),
                new Fact("Footing","Physically standing on owned, Heart-connected sculk at 25%+ vitality, including a floor-facing vein. Nearby sculk alone cannot start exposure. Jumping retains the last living footing; landing on a non-sculk pillar is safe."),
                new Fact("Effect",kind==EcologyCosts.Kind.HUNGER?"Sustained Hunger I after the initial wait; Established or Extensive growth.":"Sustained Darkness after the initial wait; Extensive growth only."),
                new Fact("Initial delay",rule.seconds()+" continuous eligible seconds after living footing, then the effect stays until a non-sculk landing or loss of power/eligibility."),
                new Fact("Players","Survival/Adventure participants in the Heart's dimension; Creative, Spectator and editing excluded."),
                new Fact("Reset","Landing off living sculk, lost power/eligibility, pause and restart remove campaign effects and discard waiting credit."),
                new Fact("Existing effects","Independent potion/Warden effects take precedence. Leaving removes only our marked effects; our effects are excluded from player saves."),
                new Fact("Since reset",r.costs.applications()+" initial waits completed across both effects; "+r.costs.tracked()+" tracked participants.")),disabled);
    }
    private static String costAction(ServerCommandSource source,String verb,EcologyCosts.Kind kind)throws Exception {
        Runtime r=runtime(source.getServer());var all=r.engine.costRules();var old=kind==EcologyCosts.Kind.HUNGER?all.hunger():all.darkness();var next=old;
        switch(verb){
            case "enable" -> next=new EcologyCosts.Rule(true,old.seconds());
            case "disable" -> next=new EcologyCosts.Rule(false,old.seconds());
            case "faster","slower" -> next=new EcologyCosts.Rule(old.enabled(),EcologyCosts.SECONDS.get(EcologyCosts.SECONDS.indexOf(old.seconds())+(verb.equals("faster")?-1:1)));
            case "reset" -> next=EcologyCosts.DEFAULT.hunger();
            case "refresh" -> {r.menuRevision++;return "Local cost status refreshed.";}
            default -> throw new IllegalArgumentException("Unknown local cost action");
        }
        r.engine.tuneCosts(kind==EcologyCosts.Kind.HUNGER?new EcologyCosts.Rules(next,all.darkness()):new EcologyCosts.Rules(all.hunger(),next));
        EcologyCostEffects.clear(source.getServer());r.costs=new EcologyCosts(r.engine.costRules());r.menuRevision++;
        QuietBelow.LOGGER.info("Quiet Below {} tuning {} by {}",kind,verb,source.getName());
        return (kind==EcologyCosts.Kind.HUNGER?"Local Hunger":"Local Darkness")+" "+(next.enabled()?"enabled":"disabled")+"; initial delay "+next.seconds()+" eligible seconds. Exposure waits restarted; campaign effects removed.";
    }
    private static String growthStatus(MinecraftServer server,Runtime r,EcologyEngine.View v) {
        if(r.engine.ending()!=null)return heartDescription(r);
        if(v.hold()!=null)return "HELD: "+v.hold();
        if(!v.configured())return "Not configured; assign the source city and Heart, then index.";
        if(r.rootProblem!=null)return "Waiting: "+r.rootProblem;
        if(!v.enabled())return "Disabled; existing sculk is retained.";
        if(ChronicleRuntime.service(server).state().phase()!=SessionPhase.ACTIVE)return "Paused; resume Chronicle play to grow or reconnect sculk.";
        String hold=RuntimeHolds.reason(server);if(hold!=null)return "Waiting for Chronicle operation: "+hold;
        if(PlayRuntime.eligiblePlayers(server).isEmpty())return "Waiting for an online, alive, non-spectator participant.";
        if(v.unresolved())return "Checking connections; keep the network chunks loaded.";
        if(v.connected()==0)return "Dormant: no living sculk connection to the Heart.";
        if(!r.engine.vitalityPending()&&r.engine.vitalitySummary().ready()==0)return "Recovering: connected sculk needs 25% vitality for growth. Restore direct contact with a nourished owned root or expose it to air; bright exposure recovers faster.";
        return "Active; spreading along reachable exposed surfaces.";
    }
    private static String coordinates(Pins p) {return p.heart.x()+", "+p.heart.y()+", "+p.heart.z()+" in "+p.setup.dimension();}
    private static String speedLabel(EcologyEngine e) {
        return String.format(Locale.ROOT,"%.2fx base, effective %.2fx; attempts every %.2fs dark / %.2fs bright (at 20 TPS)",e.speedPercent()/100.0,e.effectiveSpeedPercent()/100.0,e.attemptTicks(false)/20.0,e.attemptTicks(true)/20.0);
    }
    private static String spreadLabel(EcologyEngine e){
        var rules=e.spreadRules();return rules.mode()==EcologyEngine.GrowthMode.FIXED?"Fixed rate: the saved base rate is used.":
                "Sculk-count rate: +"+rules.amplificationPercent()+"% per 1,000 ready rooted sculk; "+e.countedSculk()+" counted; capped at 16x.";
    }
    private static GameSetupTools.Status vitalityStatus(ServerCommandSource source){
        Runtime r=runtime(source.getServer());var e=r.engine;var rules=e.vitality().rules();var s=e.vitalitySummary();Map<String,String> disabled=new HashMap<>();
        for(String verb:List.of("loss_slower","loss_faster","recovery_slower","recovery_faster","extent_lower","extent_higher","reset")){
            if(!e.view().indexed())disabled.put(verb,"Complete source indexing first.");if(e.failure()!=null)disabled.put(verb,e.failure());
        }
        if(rules.lossMinutes()==40)disabled.put("loss_slower","Maximum full-loss time: 40 eligible minutes.");
        if(rules.lossMinutes()==1)disabled.put("loss_faster","Minimum full-loss time: 1 eligible minute.");
        if(rules.recoveryMinutes()==40)disabled.put("recovery_slower","Maximum shaded recovery time: 40 eligible minutes.");
        if(rules.recoveryMinutes()==1)disabled.put("recovery_faster","Minimum shaded recovery time: 1 eligible minute.");
        if(rules.extentPreset()==0)disabled.put("extent_lower","Minimum thresholds: 64 / 512.");
        if(rules.extentPreset()==4)disabled.put("extent_higher","Maximum thresholds: 1024 / 8192.");
        if(rules.equals(EcologyVitality.DEFAULT))disabled.put("reset","Vitality tuning already uses defaults.");
        return new GameSetupTools.Status(e.view().revision()+r.menuRevision,List.of(
                new Fact("Assessment",e.vitalityPending()?"Checking connections/exposures; extent retains its last known value.":"Latest bounded assessment; "+e.vitality().extent()+" extent."),
                new Fact("Looked sculk",inspectedCell(source,r)),
                new Fact("Rooted vitality",s.rootedPercent()+"% average; "+s.ready()+" ready / "+s.rooted()+" rooted cells"),
                new Fact("Detached vitality",s.detachedPercent()+"% average across "+s.detached()+" detached cells; blocks stay in place"),
                new Fact("Nourishment",s.feeding()+" air-fed or directly joined roots; "+s.sealed()+" isolated sealed; "+s.unknown()+" unresolved"),
                new Fact("Full vitality loss",rules.lossMinutes()+" eligible minutes from 100% to 0% while detached or sealed without an adjacent certified root"),
                new Fact("Full recovery",rules.recoveryMinutes()+" eligible minutes with shaded nourishment; "+String.format(Locale.ROOT,"%.1f",rules.recoveryMinutes()/2.0)+" in bright exposure"),
                new Fact("Extent thresholds","Established "+rules.established()+" / Extensive "+rules.extensive()+" ready rooted cells; 12.5% exit buffer prevents rapid switching"),
                new Fact("Living growth","Needs physical Heart connection and at least 25% vitality. Directly touching owned rooted sculk nourishes covered cells without relaying light or creating a growth route. A cut stops growth/native responses immediately; reconnection can recover from zero."),
                new Fact("Visible vitality","Glistening blue: healthy, feeding roots (75%+). Dull blue: reduced vitality or dormant connection (25%+). Faded brown/blue: below 25%. Fully brown: zero. Colors recover too; large patches update gradually."),
                new Fact("Clock",e.vitality().ticks()/20+" saved eligible seconds; pause, empty play, holds and unresolved networks do not advance it. Bright means light above 7; no air depletion."),
                new Fact("Tuning","Pause play and enable editing. Full-loss/recovery choices: 1 / 2 / 5 / 10 / 20 / 40 minutes. Speed controls remain in Sculk ecology.")),disabled);
    }
    private static String inspectedCell(ServerCommandSource source,Runtime r) {
        var player=source.getPlayer();if(player==null)return "Look at sculk within six blocks, then refresh this menu.";
        var world=source.getWorld();var start=player.getEyePos();var end=start.add(player.getRotationVec(1).multiply(6));
        for(int n=0;n<=24;n++){
            BlockPos p=BlockPos.ofFloored(start.lerp(end,n/24.0));
            if(!world.getChunkManager().isChunkLoaded(p.getX()>>4,p.getZ()>>4))return "Target crosses an unloaded chunk; no chunks loaded.";
        }
        var hit=world.raycast(new net.minecraft.world.RaycastContext(start,end,net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,player));
        if(hit.getType()!=net.minecraft.util.hit.HitResult.Type.BLOCK)return "Look at sculk within six blocks, then refresh this menu.";
        BlockPos p=hit.getBlockPos();var actual=world.getBlockState(p);Pos cell=pos(p);
        if(!actual.contains(SculkAppearance.VITALITY))return "Target is not campaign-supported sculk.";
        String location=p.getX()+", "+p.getY()+", "+p.getZ();
        if(r.engine.setup()==null||!r.engine.setup().dimension().equals(world.getRegistryKey().getValue().toString())||!r.engine.owns(cell))
            return location+": not campaign-owned; no campaign vitality. Adopt a region or physically connect it during play.";
        int value=r.engine.vitality().value(cell);int expected=EcologyAppearance.stage(value,r.engine.rooted(cell),r.engine.vitality().mode(cell));
        String exact=value==0?"zero":"positive";
        String percent=value>0&&value*10000L<EcologyVitality.FULL?"<0.01%":String.format(Locale.ROOT,"%.2f%%",value*100.0/EcologyVitality.FULL);
        return location+": "+percent+" ("+exact+"); "
                +(r.engine.rooted(cell)?"rooted":"detached")+"; displayed "+EcologyAppearance.label(actual.get(SculkAppearance.VITALITY))
                +"; expected "+(expected<0?"unknown, retain last appearance":EcologyAppearance.label(expected))+". Refresh after a checkpoint/scan.";
    }
    private static String vitalityAction(ServerCommandSource source,String verb)throws Exception{
        Runtime r=runtime(source.getServer());var old=r.engine.vitality().rules();int loss=old.lossMinutes(),recovery=old.recoveryMinutes(),extent=old.extentPreset();
        switch(verb){
            case "loss_slower","loss_faster"->loss=EcologyVitality.MINUTES.get(EcologyVitality.MINUTES.indexOf(loss)+(verb.equals("loss_slower")?1:-1));
            case "recovery_slower","recovery_faster"->recovery=EcologyVitality.MINUTES.get(EcologyVitality.MINUTES.indexOf(recovery)+(verb.equals("recovery_slower")?1:-1));
            case "extent_lower"->extent--;case "extent_higher"->extent++;
            case "reset"->{loss=10;recovery=5;extent=2;}
            case "refresh"->{r.menuRevision++;return "Vitality status refreshed.";}
            default->throw new IllegalArgumentException("Unknown vitality action");
        }
        r.engine.tuneVitality(new EcologyVitality.Rules(loss,recovery,extent));return "Vitality tuning saved: loss "+loss+" min; shaded recovery "+recovery+" min; extent preset "+(extent+1)+" / 5.";
    }
    private static GameSetupTools.Status ownershipStatus(ServerCommandSource source) {
        Runtime r=runtime(source.getServer());var e=r.engine;var c=e.claim();
        String selected="Not assigned; choose Sculk takeover region in Game setup.";
        try{var site=takeoverRegion(source.getServer(),r);selected=site.id()+" (revision "+site.revision()+")";}catch(Exception ignored){ }
        String job="No takeover requested.";
        if(c!=null){
            long done=0,total=0;var a=c.area();
            for(int x=a.min().x()>>4;x<=a.max().x()>>4;x++)for(int z=a.min().z()>>4;z<=a.max().z()>>4;z++){
                total++;long key=((long)x<<32)|(z&0xffffffffL);
                int size=(Math.min(a.max().x(),(x<<4)+15)-Math.max(a.min().x(),x<<4)+1)*(Math.min(a.max().z(),(z<<4)+15)-Math.max(a.min().z(),z<<4)+1)*(a.max().y()-a.min().y()+1);
                if(c.cursors().getOrDefault(key,0)==size)done++;
            }
            job=c.site()+": "+done+" / "+total+" chunks; "+(c.complete()?done==total?"complete":"stopped; adopted cells retained":r.claimProblem!=null?r.claimProblem:AuthoringRuntime.editable(source.getServer())?"indexing normally loaded chunks; visit the region":"paused during active play");
        }
        Map<String,String> disabled=new HashMap<>();
        if(e.failure()!=null)for(String verb:List.of("claim","cancel","cleanup_on","cleanup_off"))disabled.put(verb,e.failure());
        if(!e.view().indexed()){disabled.put("claim","Complete source indexing first.");disabled.put("cleanup_on","Complete source indexing first.");}
        if(c!=null&&!c.complete())disabled.put("claim","Finish or stop the running takeover first.");else disabled.put("cancel","No region takeover is running.");
        try{takeoverRegion(source.getServer(),r);}catch(Exception ex){disabled.put("claim",ex.getMessage());}
        if(e.cleanup())disabled.put("cleanup_on","Cleanup is already enabled.");else disabled.put("cleanup_off","Cleanup is already stopped.");
        String dimension=e.setup()==null?"Not configured":e.setup().dimension();
        return new GameSetupTools.Status(e.view().revision()+r.menuRevision,List.of(
                new Fact("Takeover region",selected),new Fact("Region takeover",job),
                new Fact("Ownership","Adoption claims existing sculk/veins/catalysts/sensors/shriekers, including detached patches. Only a physical Heart connection wakes them. Already-owned history is preserved."),
                new Fact("Cleanup",e.cleanup()?"ENABLED; permanent deletion during occupied active play. "+(r.cleanup==null?"":r.cleanup.pending()+" loaded chunks pending; "+r.cleanup.removed()+" blocks deleted this run."):"Off; no non-campaign sculk is deleted."),
                new Fact("Cleanup scope","All normally loaded chunks in "+dimension+", continuing as others load. Other dimensions are unchanged. Disabled/empty/paused play waits; a running takeover takes priority."),
                new Fact("Before deleting","Take over every region whose sculk you want to keep first. This deletion includes non-campaign catalysts, sensors and shriekers. Stop sculk cleanup prevents future deletions; it does not restore deleted blocks."),
                new Fact("Catalysts","Campaign-owned catalysts ignore deaths/XP and legacy queued charge. The connected ecology is the only campaign growth system.")),disabled);
    }
    private static AuthoringSite.Region takeoverRegion(MinecraftServer server,Runtime r) {
        AuthoringSite site=MapSiteAssignments.resolve(server,"quiet_below:sculk_takeover").orElseThrow(()->new IllegalStateException("Assign a saved Sculk takeover region in Game setup first."));
        if(!(site instanceof AuthoringSite.Region region))throw new IllegalStateException("Takeover requires a region.");
        if(r.engine.setup()==null||!site.dimension().equals(r.engine.setup().dimension()))throw new IllegalStateException("Takeover region must be in the Heart's dimension.");
        new EcologyEngine.Area(new Pos(region.min().x(),region.min().y(),region.min().z()),new Pos(region.max().x(),region.max().y(),region.max().z()));return region;
    }
    private static String claimProblem(MinecraftServer server,EcologyEngine.Claim c) {
        AuthoringSite site=AuthoringRuntime.get(server).service().state().sites().get(c.site());
        return !(site instanceof AuthoringSite.Region region)||site.revision()!=c.revision()
                ||!new Pos(region.min().x(),region.min().y(),region.min().z()).equals(c.area().min())
                ||!new Pos(region.max().x(),region.max().y(),region.max().z()).equals(c.area().max())
                ||!site.dimension().equals(runtime(server).engine.setup().dimension())?
                "saved region changed; restore its pinned revision or stop this takeover and select again":null;
    }
    private static String ownershipAction(ServerCommandSource source,String verb)throws Exception {
        Runtime r=runtime(source.getServer());
        switch(verb){
            case "claim"->{var site=takeoverRegion(source.getServer(),r);r.engine.claim(site.id(),site.revision(),new EcologyEngine.Area(new Pos(site.min().x(),site.min().y(),site.min().z()),new Pos(site.max().x(),site.max().y(),site.max().z())));}
            case "cancel"->r.engine.cancelClaim();
            case "cleanup_on"->{attach(source.getServer(),r);seedCleanup(source.getServer(),r);r.engine.cleanup(true);}
            case "cleanup_off"->r.engine.cleanup(false);
            case "refresh"->r.menuRevision++;
            default->throw new IllegalArgumentException("Unknown ownership action");
        }
        return switch(verb){case "claim"->"Region takeover started. It adopts existing sculk while play is non-active; keep its chunks loaded.";
            case "cancel"->"Region takeover stopped; already adopted sculk remains owned.";
            case "cleanup_on"->"Non-campaign sculk cleanup enabled for the Heart's dimension. Deletion runs only during occupied active play and continues as chunks load.";
            case "cleanup_off"->"Non-campaign sculk cleanup stopped; future deletions prevented.";
            default->"Sculk ownership status refreshed.";};
    }
    private static boolean cleanupWorld(ServerWorld world,Runtime r) {return r!=null&&r.cleanup!=null&&r.engine.cleanup()&&r.engine.setup().dimension().equals(world.getRegistryKey().getValue().toString());}
    private static void seedCleanup(MinecraftServer server,Runtime r) {
        ServerWorld world=adapter(server,r.engine.setup().dimension()).world;
        if(LOAD_OVERFLOW.contains(world))throw new IllegalStateException("Loaded-chunk tracking capacity was exceeded; reduce loaded chunks and restart before enabling cleanup.");
        r.cleanup=new EcologyCleanup(world.getBottomY(),world.getHeight());
        for(ChunkPos p:LOADED.getOrDefault(world,Set.of()))r.cleanup.load(p.x,p.z);
    }
    private static Pins visibleRoot(MinecraftServer server,Runtime r) {
        Pins p=pins(server);
        if(r.engine.setup()!=null&&!r.engine.setup().equals(p.setup))throw new IllegalStateException("Root assignment differs from the pinned ecology setup; inspect the setup hold.");
        return p;
    }
    private static String execute(ServerCommandSource source,String verb) throws Exception {
        Runtime r=runtime(source.getServer());
        switch(verb) {
            case "index" -> { Pins p=pins(source.getServer());r.engine.configure(p.setup,p.area,p.heart); }
            case "enable" -> { attach(source.getServer(),r);r.engine.mode(true); }
            case "disable" -> r.engine.mode(false);
            case "show_root" -> {
                Pins p=visibleRoot(source.getServer(),r);
                AuthoringSite site=MapSiteAssignments.resolve(source.getServer(),"quiet_below:relic_heart").orElseThrow();
                AuthoringRuntime.get(source.getServer()).preview(source.getPlayerOrThrow(),site);
                source.sendFeedback(()->Text.literal("Sculk root: "+coordinates(p)+". Close the menu to see its private outline; Hide root removes it."),false);
            }
            case "hide_root" -> AuthoringRuntime.clearPreview(source.getPlayerOrThrow());
            case "slower", "faster" -> r.engine.speed(EcologyEngine.SPEEDS.get(EcologyEngine.SPEEDS.indexOf(r.engine.speedPercent())+(verb.equals("faster")?1:-1)));
            case "speed_reset" -> r.engine.speed(100);
            case "use_fixed" -> r.engine.tuneSpread(new EcologyEngine.SpreadRules(EcologyEngine.GrowthMode.FIXED,r.engine.spreadRules().amplificationPercent()));
            case "use_count" -> r.engine.tuneSpread(new EcologyEngine.SpreadRules(EcologyEngine.GrowthMode.SCULK_COUNT,r.engine.spreadRules().amplificationPercent()));
            case "amplification_lower", "amplification_higher" -> {var old=r.engine.spreadRules();int index=EcologyEngine.AMPLIFICATIONS.indexOf(old.amplificationPercent());r.engine.tuneSpread(new EcologyEngine.SpreadRules(old.mode(),EcologyEngine.AMPLIFICATIONS.get(index+(verb.equals("amplification_higher")?1:-1))));}
            case "spread_reset" -> r.engine.tuneSpread(EcologyEngine.DEFAULT_SPREAD);
            case "refresh" -> r.menuRevision++;
            default -> throw new IllegalArgumentException("Unknown ecology action");
        }
        QuietBelow.LOGGER.info("Ecology {} requested by {}",verb,source.getName());
        return switch(verb){case "index"->"Source indexing started. Keep source chunks loaded; it changes no terrain.";
            case "enable"->"Growth enabled. It advances only during occupied active play.";
            case "disable"->"Growth disabled; owned terrain and history retained.";
            case "show_root"->"Root outlined privately. Close the menu to view it.";
            case "hide_root"->"Root outline hidden.";
            case "slower", "faster", "speed_reset"->"Spread speed saved: "+speedLabel(r.engine)+". "+spreadLabel(r.engine);
            case "use_fixed","use_count","amplification_lower","amplification_higher","spread_reset"->"Spread tuning saved: "+speedLabel(r.engine)+". "+spreadLabel(r.engine);
            default->"Ecology status refreshed. "+growthStatus(source.getServer(),r,r.engine.view());};
    }
    private record Pins(EcologyEngine.Setup setup,EcologyEngine.Area area,Pos heart,String heartBlock) { }
    private static Pins pins(MinecraftServer server) {
        AuthoringSite city=MapSiteAssignments.resolve(server,"quiet_below:source_city").orElseThrow(()->new IllegalStateException("Assign Source city in Game setup first."));
        AuthoringSite heart=MapSiteAssignments.resolve(server,"quiet_below:relic_heart").orElseThrow(()->new IllegalStateException("Assign a Relic Heart block site first; use a clearly labelled temporary anchor in review maps."));
        if(!(city instanceof AuthoringSite.Region region)||!(heart instanceof AuthoringSite.Anchor anchor)||!city.dimension().equals(heart.dimension()))
            throw new IllegalStateException("Source city must be a region and Heart a block site in the same dimension.");
        var area=new EcologyEngine.Area(new Pos(region.min().x(),region.min().y(),region.min().z()),new Pos(region.max().x(),region.max().y(),region.max().z()));
        Pos hp=new Pos(anchor.position().x(),anchor.position().y(),anchor.position().z());
        return new Pins(new EcologyEngine.Setup(city.id(),city.revision(),heart.id(),heart.revision(),city.dimension()),area,hp,anchor.expectedBlock());
    }
    private static void attach(MinecraftServer server,Runtime r) {
        Pins p=pins(server);r.engine.attach(p.setup,p.area,p.heart);
        if(r.engine.ending()!=null){
            if(!r.engine.endingRootState().id().equals(p.heartBlock))throw new IllegalStateException("Terminal Heart type differs from pinned authoring root");
            return;
        }
        Adapter w=adapter(server,p.setup.dimension());
        if(r.cleanup==null){r.cleanup=new EcologyCleanup(w.world.getBottomY(),w.world.getHeight());if(r.engine.cleanup())seedCleanup(server,r);}
        String problem=!w.loaded(p.heart)?"Heart chunk is unloaded; visit "+coordinates(p):
                !w.get(p.heart).id().equals(p.heartBlock)?"Heart block is missing or replaced at "+coordinates(p)+". Restore "+p.heartBlock+"; weakness requires the original bound block.":null;
        if(!Objects.equals(r.rootProblem,problem)){r.rootProblem=problem;r.engine.invalidateConnections();}
    }
    private static Runtime runtime(MinecraftServer server) {
        if(!server.isOnThread())throw new IllegalStateException("Ecology requires the server thread");
        Runtime r=WORLDS.get(server);if(r==null)throw new IllegalStateException("Ecology unavailable; inspect quiet-below/ecology.jsonl and server log.");return r;
    }
    static EcologyEngine engineForTest(MinecraftServer server){return runtime(server).engine;}
    static EcologyEradication endingForTest(MinecraftServer server){return runtime(server).eradication;}
    static long tickNanosForTest(MinecraftServer server){return runtime(server).lastTickNanos;}
    public static boolean protectsRoot(ServerWorld world,BlockPos at){
        Runtime r=WORLDS.get(world.getServer());
        if(r==null)return assignedRoot(world,at);
        if(r.engine.setup()==null||!r.engine.setup().dimension().equals(world.getRegistryKey().getValue().toString())||!pos(at).equals(r.engine.rootPosition()))return false;
        return r.engine.ending()==null||r.eradication==null||!r.eradication.rootComplete();
    }
    private static boolean assignedRoot(ServerWorld world,BlockPos at){
        try{
            AuthoringSite site=MapSiteAssignments.resolve(world.getServer(),"quiet_below:relic_heart").orElse(null);
            var chunk=EcologyLoadedBlocks.chunk(world,at);if(chunk==null)return false;
            String actual=Registries.BLOCK.getId(chunk.getBlockState(at).getBlock()).toString();
            return matchesAssignedHeart(site,world.getRegistryKey().getValue().toString(),pos(at),actual);
        }catch(RuntimeException unavailable){return false;}
    }
    static boolean matchesAssignedHeart(AuthoringSite site,String dimension,Pos at,String actual){
        return site instanceof AuthoringSite.Anchor anchor&&anchor.dimension().equals(dimension)
                &&new Pos(anchor.position().x(),anchor.position().y(),anchor.position().z()).equals(at)
                &&anchor.expectedBlock().equals(actual);
    }
    private static void heartRestingAppearance(MinecraftServer server,Runtime r){
        try{
            if(r.engine.setup()==null||r.engine.rootPosition()==null)return;
            ServerWorld world=server.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(r.engine.setup().dimension())));
            if(world!=null)projectHeartAppearance(world,r,RelicHeartBlock.Lens.RESTING,false);
        }catch(RuntimeException failure){
            if(!r.appearanceFailureLogged){r.appearanceFailureLogged=true;QuietBelow.LOGGER.warn("Could not refresh resting Heart appearance; ecology remains held",failure);}
        }
    }
    private static void heartAppearance(ServerWorld world,Runtime r){
        if(r.engine.rootPosition()==null)return;
        try {
            var proof=r.engine.heartProof(r.rootProblem==null);boolean weak=r.engine.heartVulnerable(r.rootProblem==null);
            projectHeartAppearance(world,r,heartLens(proof,r.engine.heartRules(),weak),weak);
        } catch(RuntimeException failure) {
            if(!r.heartAppearanceFailureLogged) {
                r.heartAppearanceFailureLogged=true;
                QuietBelow.LOGGER.warn("Could not refresh Heart appearance; growth and weakness remain active",failure);
            }
        }
    }
    private static boolean authorizeDefiance(MinecraftServer server,Runtime r,EcologyHeart.Transition heart){
        if(r.defiancePhase==DefiancePhase.AUTHORIZED)return true;
        if(r.defiancePhase==DefiancePhase.DENIED||r.defiance==null||r.defianceProblem!=null)return false;
        HeartDefianceStore.Pin pin=HeartDefianceStore.Pin.of(r.engine.setup(),r.engine.rootPosition());
        long activeTicks=PlayRuntime.elapsedTicks(server),cycleStart=activeTicks-heart.current();
        if(r.defiance.receipt()!=null&&!r.defiance.receipt().pin().equals(pin)){
            defianceFailure(r,"Saved Heart last-stand state belongs to different ecology pins; last stands are suppressed",null);
            r.defiancePhase=DefiancePhase.DENIED;return false;
        }
        if(!HeartDefianceStore.cycleEligible(r.defiance.receipt(),pin,cycleStart)){
            r.defiancePhase=DefiancePhase.DENIED;return false;
        }
        try{
            // Durable claim precedes the first sound, particles, terrain change and Warden spawn.
            r.defiance=r.defianceStore.claim(pin,activeTicks,false);r.defiancePhase=DefiancePhase.AUTHORIZED;
            QuietBelow.LOGGER.info("Heart last stand claimed: root={}, activeTicks={}, cooldownSeconds={}",r.engine.rootPosition(),activeTicks,r.defiance.cooldownSeconds());return true;
        }catch(Exception failure){
            defianceFailure(r,"Heart last-stand claim could not be saved; this phase is suppressed while normal weakness continues",failure);
            r.defiancePhase=DefiancePhase.DENIED;return false;
        }
    }
    private static void defianceFailure(Runtime r,String message,Exception failure){
        r.defianceProblem=message;
        if(!r.defianceFailureLogged){r.defianceFailureLogged=true;if(failure==null)QuietBelow.LOGGER.error(message);else QuietBelow.LOGGER.error(message,failure);}
    }
    private static boolean applyDefianceEffect(Runtime r,String message,DefianceEffect effect){
        DefianceEffectResult result=optionalDefianceEffect(r.defiancePhase,effect);r.defiancePhase=result.phase();
        if(result.failure()!=null&&!r.defianceEffectFailureLogged){r.defianceEffectFailureLogged=true;QuietBelow.LOGGER.warn(message+"; normal Heart weakness continues",result.failure());}
        return result.value();
    }
    static DefianceEffectResult optionalDefianceEffect(DefiancePhase phase,DefianceEffect effect){
        Objects.requireNonNull(phase);Objects.requireNonNull(effect);
        if(phase!=DefiancePhase.AUTHORIZED)return new DefianceEffectResult(phase,false,null);
        try{return new DefianceEffectResult(phase,effect.run(),null);}
        catch(RuntimeException failure){return new DefianceEffectResult(phase,false,failure);}
    }
    private static void resetDefianceSignals(Runtime r) {
        r.defiancePhase=DefiancePhase.UNDECIDED;r.heartbeatTicks.clear();r.wardenAttempted=false;r.wardenSpawned=false;
    }
    record DefianceSignals(String phase,List<Integer> heartbeatTicks,boolean wardenAttempted,boolean wardenSpawned) { }
    static DefianceSignals signalsForTest(MinecraftServer server) {
        Runtime r=runtime(server);
        return new DefianceSignals(r.defiancePhase.name(),List.copyOf(r.heartbeatTicks),r.wardenAttempted,r.wardenSpawned);
    }
    private static boolean heartHeartbeat(ServerWorld world,Runtime r) {
        Pos root=r.engine.rootPosition();if(root==null)return false;
        BlockPos at=blockPos(root);if(EcologyLoadedBlocks.chunk(world,at)==null)return false;
        world.playSound(null,at,SoundEvents.ENTITY_WARDEN_HEARTBEAT,SoundCategory.BLOCKS,1.6f,.85f);
        return true;
    }

    /** One direct triggered Warden, without touching vanilla player warning managers. */
    private static boolean spawnLastStandWarden(ServerWorld world,Pos root){
        if(root==null||world.getDifficulty()==Difficulty.PEACEFUL)return false;
        BlockPos center=blockPos(root);int[] vertical={0,1,-1,2,-2,3,-3};int checked=0;
        for(int radius=1;radius<=4;radius++)for(int dy:vertical)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            if(++checked>MAX_WARDEN_SPAWN_CANDIDATES)return false;
            BlockPos feet=center.add(dx,dy,dz),floor=feet.down();
            if(!spawnColumnLoaded(world,floor)||!world.getWorldBorder().contains(floor)||!world.getWorldBorder().contains(feet))continue;
            BlockState support=EcologyLoadedBlocks.chunk(world,floor).getBlockState(floor);
            if(!support.isSideSolidFullSquare(world,floor,Direction.UP))continue;
            var box=EntityType.WARDEN.getDimensions().getBoxAt(feet.getX()+.5,feet.getY(),feet.getZ()+.5);
            if(!world.isSpaceEmpty(box))continue;
            return EntityType.WARDEN.spawn(world,feet,SpawnReason.TRIGGERED)!=null;
        }
        return false;
    }
    private static boolean spawnColumnLoaded(ServerWorld world,BlockPos floor){
        for(int dy=0;dy<=3;dy++){
            BlockPos at=floor.up(dy);if(!world.isInBuildLimit(at)||!world.getWorldBorder().contains(at)||EcologyLoadedBlocks.chunk(world,at)==null)return false;
            if(dy>0&&!EcologyLoadedBlocks.chunk(world,at).getBlockState(at).getCollisionShape(world,at).isEmpty())return false;
        }
        return true;
    }
    private static void heartPulse(ServerWorld world,Runtime r,boolean lastEffort,boolean grew){
        if(r.engine.rootPosition()==null)return;
        BlockPos at=blockPos(r.engine.rootPosition());
        if(EcologyLoadedBlocks.chunk(world,at)==null)return;
        if(lastEffort)world.playSound(null,at,SoundEvents.BLOCK_SCULK_CATALYST_BLOOM,SoundCategory.BLOCKS,1.35f,.62f);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.SCULK_SOUL,
                at.getX()+.5,at.getY()+.8,at.getZ()+.5,lastEffort?(grew?18:12):5,.32,.28,.32,.02);
    }
    private static void projectHeartAppearance(ServerWorld world,Runtime r,RelicHeartBlock.Lens lens,boolean weak){
        var at=blockPos(r.engine.rootPosition());var chunk=EcologyLoadedBlocks.chunk(world,at);if(chunk==null)return;var state=chunk.getBlockState(at);
        if(state.contains(RelicHeartBlock.VULNERABLE)&&state.contains(RelicHeartBlock.LENS)
                &&(state.get(RelicHeartBlock.VULNERABLE)!=weak||state.get(RelicHeartBlock.LENS)!=lens)){
            try(var own=EcologyHooks.ownWrite()){world.setBlockState(at,state.with(RelicHeartBlock.VULNERABLE,weak).with(RelicHeartBlock.LENS,lens),Block.NOTIFY_LISTENERS);}
        }
        if(weak&&playing(world.getServer()))world.spawnParticles(net.minecraft.particle.ParticleTypes.SCULK_SOUL,
                at.getX()+.5,at.getY()+1.05,at.getZ()+.5,3,.25,.12,.25,.015);
    }
    static RelicHeartBlock.Lens heartLens(EcologyHeart.Proof proof,EcologyHeart.Rules rules,boolean vulnerable){
        if(!proof.complete()||!proof.rootValid())return RelicHeartBlock.Lens.RESTING;
        if(proof.living()>=rules.threshold(proof.established()))return RelicHeartBlock.Lens.PROTECTED;
        return vulnerable?RelicHeartBlock.Lens.VULNERABLE:RelicHeartBlock.Lens.WEAKENING;
    }
    private static void destroyHeart(ServerPlayerEntity actor,ServerWorld world,BlockPos position){
        Runtime r=WORLDS.get(world.getServer());if(r==null||r.engine.ending()!=null)return;
        try{
            GameMode mode=actor.interactionManager.getGameMode();
            if(actor.getEntityWorld()!=world||!actor.isAlive()||actor.isSpectator()||(mode!=GameMode.SURVIVAL&&mode!=GameMode.ADVENTURE)
                    ||AuthoringRuntime.isAuthor(actor)||!PlayRuntime.eligiblePlayers(world.getServer()).contains(actor)||!playing(world.getServer())
                    ||actor.squaredDistanceTo(position.toCenterPos())>36||!actor.getMainHandStack().isIn(ItemTags.PICKAXES))return;
            attach(world.getServer(),r);Pins pins=visibleRoot(world.getServer(),r);var e=r.engine;
            if(r.rootProblem!=null||!pos(position).equals(pins.heart)||!pos(position).equals(e.rootPosition())||!e.heartVulnerable(true))return;
            var chunk=EcologyLoadedBlocks.chunk(world,position);if(chunk==null)return;BlockState actual=chunk.getBlockState(position);
            if(!Registries.BLOCK.getId(actual.getBlock()).toString().equals(pins.heartBlock))return;
            e.eradicate(UUID.randomUUID(),pos(position),encode(actual),true);
            try{OfferingStats.closeSource(world.getServer(),AncientAltars.SOURCE);r.sourceClosed=true;}
            catch(IOException|RuntimeException failure){QuietBelow.LOGGER.error("Heart ended durably; Altar source closure will retry during cleanup",failure);}
            EcologyCostEffects.clear(world.getServer());
            for(var witness:world.getServer().getPlayerManager().getPlayerList())if(witness.getEntityWorld()==world&&witness.squaredDistanceTo(position.toCenterPos())<=25)
                witness.sendMessage(Text.literal("The Heart gives way. Something vast falls silent."),false);
            // The durable END_SERVER_TICK receipt removes the root without vanilla drops.
        }catch(Exception failure){
            if(r.engine.ending()==null)hold(r,failure);
            else QuietBelow.LOGGER.error("Heart ended durably; follow-up feedback failed and cleanup will continue",failure);
        }
    }
    private static void endingTick(MinecraftServer server,Runtime r){
        try{
            attach(server,r);
            if(!r.sourceClosed){OfferingStats.closeSource(server,AncientAltars.SOURCE);r.sourceClosed=true;}
            if(r.eradication==null)r.eradication=new EcologyEradication(r.engine.ending(),r.engine.endingRoot(),r.engine.endingRootState(),r.engine.cells());
            if(r.endingWorkTick!=server.getTicks()){r.endingWorkTick=server.getTicks();r.eradication.tick(adapter(server,r.engine.setup().dimension()).world,playing(server));}
            r.endingProblem=r.eradication.failure();
            if(r.endingProblem!=null){endingHold(server,r,r.endingProblem);}
            else if(r.endingHeld){RuntimeHolds.release(server,"quiet_below:heart_ending");r.endingHeld=false;}
        }catch(Exception error){r.endingProblem="Ending held: "+(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());endingHold(server,r,r.endingProblem);}
    }
    private static void endingHold(MinecraftServer server,Runtime r,String reason){
        if(!r.endingHeld||!Objects.equals(reason,r.lastEndingHold)){RuntimeHolds.hold(server,"quiet_below:heart_ending",reason);r.lastEndingHold=reason;}r.endingHeld=true;
    }
    private static String heartDescription(Runtime r){
        var e=r.engine;
        if(e.ending()!=null)return r.endingProblem!=null?r.endingProblem:r.eradication==null?"Network ended; cleanup is waiting to start.":
                "Network ended; "+r.eradication.completed()+" / "+r.eradication.total()+" targets verified, "+r.eradication.conflicts()+" later edits preserved; "+r.eradication.pendingChunks()+" chunks pending.";
        if(e.failure()!=null)return e.failure();
        if(r.rootProblem!=null)return r.rootProblem;
        var proof=e.heartProof(true);if(!proof.complete())return "Weakness cannot be verified: finish indexing, enable ecology and load all tracked branches.";
        if(proof.living()>=e.heartRules().threshold(proof.established()))return "Heart protected: living network is at or above the weakness threshold.";
        if(e.heartVulnerable(true))return "Heart vulnerable. Mine the bound block with a pickaxe during active play.";
        return "Heart weakening: "+(e.heartElapsed()/20)+" / "+e.heartRules().seconds()+" active seconds below threshold.";
    }
    private static long remainingDefianceSeconds(Runtime r,MinecraftServer server) {
        if(r.defiance==null||r.defiance.receipt()==null)return 0;
        long ticks=Math.max(0,r.defiance.receipt().eligibleAfterActiveTicks()-PlayRuntime.elapsedTicks(server));
        return ticks/20+(ticks%20==0?0:1);
    }
    private static GameSetupTools.Status heartStatus(ServerCommandSource source){
        Runtime r=runtime(source.getServer());var e=r.engine;var rules=e.heartRules();Map<String,String> disabled=new HashMap<>();
        List<String> verbs=List.of("follow_extent","threshold_lower","threshold_higher","delay_shorter","delay_longer","reset");
        List<String> cooldownVerbs=List.of("cooldown_shorter","cooldown_longer","cooldown_reset");
        String unavailable=e.ending()!=null?"The network has ended permanently.":e.failure()!=null?e.failure():!e.view().indexed()?"Complete source indexing first.":null;
        if(unavailable!=null)for(String verb:verbs)disabled.put(verb,unavailable);
        if(unavailable!=null)for(String verb:cooldownVerbs)disabled.put(verb,unavailable);
        if(r.defiance==null||r.defianceProblem!=null)for(String verb:cooldownVerbs)disabled.put(verb,Objects.requireNonNullElse(r.defianceProblem,"Last-stand state is unavailable."));
        int resolved=rules.threshold(e.vitality().rules().established());
        if(rules.threshold()==0)disabled.put("follow_extent","Already follows Established.");
        if(resolved<=EcologyHeart.THRESHOLD_CHOICES.getFirst())disabled.put("threshold_lower","Minimum threshold: fewer than one living connected cell.");
        if(resolved>=EcologyHeart.THRESHOLD_CHOICES.getLast())disabled.put("threshold_higher","Maximum menu threshold reached.");
        if(rules.seconds()<=EcologyHeart.DELAY_CHOICES.getFirst())disabled.put("delay_shorter","Minimum menu delay reached.");
        if(rules.seconds()>=EcologyHeart.DELAY_CHOICES.getLast())disabled.put("delay_longer","Maximum menu delay reached.");
        if(rules.equals(EcologyHeart.DEFAULT))disabled.put("reset","Already uses default weakness rules.");
        int cooldown=r.defiance==null?HeartDefianceStore.DEFAULT_COOLDOWN_SECONDS:r.defiance.cooldownSeconds();
        if(cooldown<=HeartDefianceStore.COOLDOWN_CHOICES.getFirst())disabled.put("cooldown_shorter","Minimum last-stand cooldown reached.");
        if(cooldown>=HeartDefianceStore.COOLDOWN_CHOICES.getLast())disabled.put("cooldown_longer","Maximum last-stand cooldown reached.");
        if(cooldown==HeartDefianceStore.DEFAULT_COOLDOWN_SECONDS)disabled.put("cooldown_reset","Already uses the default last-stand cooldown.");
        long defianceRevision=r.defiance==null?0:r.defiance.revision();
        return new GameSetupTools.Status(e.view().revision()+r.menuRevision+defianceRevision,List.of(
                new Fact("Heart status",heartDescription(r)),new Fact("Weakness threshold",rules.threshold()==0?"Follows Established: fewer than "+rules.threshold(e.vitality().rules().established())+" living connected cells.":"Fewer than "+rules.threshold()+" living connected cells."),
                new Fact("Weakness delay",rules.seconds()+" active seconds. Unknown or unloaded branches reset the wait; known pauses preserve it. Restart requires a new wait."),
                new Fact("Last stand",r.defianceProblem==null?"The Heart pulses, makes one final growth attempt and directly summons one Warden. At most one phase begins per cooldown.":r.defianceProblem),
                new Fact("Last stand phase",r.defiancePhase.name()+"; remaining cooldown "+remainingDefianceSeconds(r,source.getServer())+" active seconds; final growth "+e.heartBurstOutcome()),
                new Fact("Last stand cooldown",cooldown+" active seconds, measured from the first pulse. A cut beginning during cooldown stays quiet for its full weakness cycle."),
                new Fact("Ending","Mine the vulnerable bound Heart with a pickaxe during occupied active play. Bonuses and sacrifices end together; disconnected sculk is included."),
                new Fact("Cleanup","Loaded chunks only, with saved chunk receipts. Later replacements are preserved; pending unloaded areas remain dormant.")),disabled);
    }
    private static String heartAction(ServerCommandSource source,String verb)throws Exception {
        Runtime r=runtime(source.getServer());var old=r.engine.heartRules();int threshold=old.threshold(),delay=old.seconds();
        if(verb.startsWith("cooldown_")){
            if(r.defiance==null||r.defianceProblem!=null)throw new IllegalStateException(Objects.requireNonNullElse(r.defianceProblem,"Last-stand state is unavailable."));
            int seconds=switch(verb){
                case "cooldown_shorter","cooldown_longer"->choice(HeartDefianceStore.COOLDOWN_CHOICES,r.defiance.cooldownSeconds(),verb.equals("cooldown_longer"));
                case "cooldown_reset"->HeartDefianceStore.DEFAULT_COOLDOWN_SECONDS;
                default->throw new IllegalArgumentException("Unknown Heart cooldown action");
            };
            r.defiance=r.defianceStore.tuneCooldown(seconds);return "Heart last-stand cooldown saved.";
        }
        switch(verb){
            case "follow_extent"->threshold=0;
            case "threshold_lower","threshold_higher"->threshold=choice(EcologyHeart.THRESHOLD_CHOICES,old.threshold(r.engine.vitality().rules().established()),verb.equals("threshold_higher"));
            case "delay_shorter","delay_longer"->delay=choice(EcologyHeart.DELAY_CHOICES,delay,verb.equals("delay_longer"));
            case "reset"->{threshold=0;delay=60;}
            case "show_root"->{return execute(source,"show_root");}
            case "refresh"->{r.menuRevision++;return heartDescription(r);}
            default->throw new IllegalArgumentException("Unknown Heart action");
        }
        r.engine.tuneHeart(new EcologyHeart.Rules(threshold,delay));resetDefianceSignals(r);r.lastHeartElapsed=0;return "Heart weakness rules saved; the weakness wait restarts.";
    }
    private static int choice(List<Integer> choices,int current,boolean higher){
        if(higher)return choices.stream().filter(n->n>current).findFirst().orElse(current);
        for(int n=choices.size()-1;n>=0;n--)if(choices.get(n)<current)return choices.get(n);return current;
    }
    private static Adapter adapter(MinecraftServer server,String dimension) {
        ServerWorld w=server.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(dimension)));
        if(w==null)throw new IllegalStateException("Ecology dimension unavailable");return new Adapter(w);
    }
    private static void hold(Runtime r,Exception e) {
        if(r.engine.failure()==null)r.engine.fail(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
        if(!r.failureLogged){r.failureLogged=true;QuietBelow.LOGGER.error("Quiet Below ecology held; no automatic retry: "+r.engine.failure(),e);}
    }
    enum DefiancePhase { UNDECIDED,AUTHORIZED,DENIED }
    @FunctionalInterface interface DefianceEffect { boolean run(); }
    record DefianceEffectResult(DefiancePhase phase,boolean value,RuntimeException failure) { }
    private static final class Runtime {
        final EcologyEngine engine;final EcologyJournal journal;final HeartDefianceStore defianceStore;int settlingWrites,endingWorkTick=Integer.MIN_VALUE,lastHeartElapsed;long menuRevision,lastTickNanos;boolean failureLogged,appearanceFailureLogged,defianceFailureLogged,defianceEffectFailureLogged;String rootProblem,claimProblem,defianceProblem;HeartDefianceStore.State defiance;DefiancePhase defiancePhase=DefiancePhase.UNDECIDED;EcologyCleanup cleanup;EcologyCrops crops;EcologyRecovery recovery;EcologyCosts costs;EcologyEradication eradication;boolean sourceClosed,endingHeld;String endingProblem,lastEndingHold;
        final List<Integer> heartbeatTicks=new ArrayList<>();boolean wardenAttempted,wardenSpawned,heartAppearanceFailureLogged;EcologyEngine.HeartBurstOutcome loggedBurst;
        Runtime(EcologyEngine e,EcologyJournal j,HeartDefianceStore d){engine=e;journal=j;defianceStore=d;crops=new EcologyCrops(e.cropRules());recovery=new EcologyRecovery(e.recoveryRules());costs=new EcologyCosts(e.costRules());}
    }
    private static final class NativeHooks implements EcologyHooks.Handler {
        private Runtime find(ServerWorld w) {
            Runtime r=WORLDS.get(w.getServer());
            return r!=null&&r.engine.setup()!=null&&r.engine.setup().dimension().equals(w.getRegistryKey().getValue().toString())?r:null;
        }
        @Override public boolean owns(ServerWorld w,BlockPos p){Runtime r=find(w);return (r!=null&&r.engine.owns(pos(p)))||protectsRoot(w,p);}
        @Override public void cropWrite(ServerWorld w,BlockPos p,BlockState state){
            Runtime r=find(w);if(r!=null&&EcologyCropWorld.supported(state)){
                if(((CropBlock)state.getBlock()).getAge(state)==0)r.crops.planted(pos(p));else r.crops.notice(pos(p));
            }
        }
        @Override public boolean managesCatalyst(ServerWorld w,BlockPos p){return find(w)!=null;}
        @Override public boolean managesFeature(ServerWorld w,BlockPos p){return find(w)!=null;}
        @Override public void settleInactive(ServerWorld w,BlockPos p,BlockState state){
            Runtime r=find(w);if(r==null||r.engine.failure()!=null||r.settlingWrites>=EcologyFeatureActivity.SETTLING_WRITES)return;
            var chunk=EcologyLoadedBlocks.chunk(w,p);if(chunk==null)return;
            BlockState actual=chunk.getBlockState(p);
            // Marking a native receiver dirty can update comparators. Preserve any resulting edit.
            if(actual.getBlock()!=state.getBlock()||active(w,p))return;
            state=EcologyFeatureActivity.quietState(actual);if(state==actual)return;
            r.settlingWrites++;
            try{
                if(!w.setBlockState(p,state,Block.NOTIFY_ALL))throw new IllegalStateException("Inactive sculk phase settling failed");
                // A reconnected feature must not inherit the old activation's delayed tick.
                w.getBlockTickScheduler().clearNextTicks(new BlockBox(p));
                if(state.isOf(Blocks.SCULK_SENSOR)){w.updateNeighbors(p,state.getBlock());w.updateNeighbors(p.down(),state.getBlock());}
            }catch(Exception failure){hold(r,failure);}
        }
        @Override public void unownedNetworkWrite(ServerWorld w,BlockPos p){Runtime r=find(w);if(r==null)return;r.engine.noticeContact(pos(p));if(cleanupWorld(w,r))try{r.cleanup.changed(pos(p));}catch(Exception e){hold(r,e);}}
        @Override public boolean active(ServerWorld w,BlockPos p){Runtime r=find(w);return r!=null&&r.rootProblem==null&&r.engine.enabled()&&r.engine.living(pos(p))&&playing(w.getServer());}
        @Override public BlockState beforeWrite(ServerWorld w,BlockPos p,BlockState before,BlockState proposed,int flags) {
            if(protectsRoot(w,p)&&!before.equals(proposed))return null;
            Runtime r=find(w);if(r==null)return proposed;
            if(r.engine.ending()!=null)return proposed;
            if(r.engine.owns(pos(p))&&before.getBlock()==proposed.getBlock()&&before.contains(SculkAppearance.VITALITY))
                proposed=proposed.with(SculkAppearance.VITALITY,before.get(SculkAppearance.VITALITY));
            var origin=EcologyHooks.catalystOrigin();
            if(origin==null||origin.world()!=w||!r.engine.owns(pos(origin.pos())))return proposed;
            // Native transient changes remain native; only descendants/static shape changes need ownership intents.
            State previous=encode(before),next=encode(proposed);
            if(r.engine.owns(pos(p))&&previous.sameOwnedShape(next))return proposed;
            if(!next.network()) {
                if(r.engine.owns(pos(p)))return active(w,origin.pos())&&r.engine.reserveNativeLoss()?proposed:null;
                return previous.network()?null:proposed; // Never prune a neighbouring unrelated network.
            }
            if(!active(w,origin.pos()))return null;
            if(next.id().equals("minecraft:sculk_shrieker")){proposed=proposed.with(SculkShriekerBlock.CAN_SUMMON,true);next=encode(proposed);}
            try{return r.engine.nativeChange(new Adapter(w),pos(origin.pos()),pos(p),previous,next)?proposed:null;}
            catch(Exception e){hold(r,e);return null;}
        }
        @Override public void afterWrite(ServerWorld w,BlockPos p,BlockState before,BlockState proposed,boolean success) {
            Runtime r=find(w);if(r==null)return;
            if(r.engine.ending()!=null){if(success&&r.eradication!=null)r.eradication.changed(w,p);return;}
            if(!r.engine.owns(pos(p)))return;
            try {
                // Neighbor updates can change the same position before the outer write returns.
                var chunk=EcologyLoadedBlocks.chunk(w,p);
                if(chunk!=null)r.engine.observed(pos(p),encode(chunk.getBlockState(p)));
            }catch(Exception e){hold(r,e);}
        }
    }
    public static Pos pos(BlockPos p){return new Pos(p.getX(),p.getY(),p.getZ());}
    public static BlockPos blockPos(Pos p){return new BlockPos(p.x(),p.y(),p.z());}
    public static State encode(BlockState state){Map<String,String> properties=new TreeMap<>();state.getEntries().forEach((p,v)->{if(!p.equals(SculkAppearance.VITALITY)&&!p.equals(RelicHeartBlock.LENS))properties.put(p.getName(),propertyName(p,v));});return new State(Registries.BLOCK.getId(state.getBlock()).toString(),properties);}
    @SuppressWarnings({"rawtypes","unchecked"}) private static String propertyName(Property p,Comparable v){return p.name(v);}
    public static BlockState decode(State encoded){
        Block block=Registries.BLOCK.getOptionalValue(Identifier.of(encoded.id())).orElseThrow(()->new IllegalStateException("Unknown recorded block: "+encoded.id()));
        BlockState state=block.getDefaultState();
        for(var e:encoded.properties().entrySet()){
            Property<?> p=block.getStateManager().getProperty(e.getKey());if(p==null)throw new IllegalStateException("Unknown recorded property");state=with(state,p,e.getValue());
        }
        int cosmetic=state.contains(SculkAppearance.VITALITY)&&!encoded.properties().containsKey(EcologyAppearance.PROPERTY)?1:0;
        if(state.contains(RelicHeartBlock.LENS)&&!encoded.properties().containsKey(RelicHeartBlock.LENS.getName()))cosmetic++;
        if(state.getProperties().size()-cosmetic!=encoded.properties().size())throw new IllegalStateException("Recorded state omits block properties: "+encoded.id());
        return state;
    }
    private static <T extends Comparable<T>> BlockState with(BlockState s,Property<T> p,String v){return s.with(p,p.parse(v).orElseThrow(()->new IllegalStateException("Invalid recorded property")));}
    public static final class Adapter implements EcologyTerrain {
        private final ServerWorld world;
        public Adapter(ServerWorld w){world=w;}
        @Override public boolean loaded(Pos p){return EcologyLoadedBlocks.chunk(world,blockPos(p))!=null;}
        private BlockState checked(Pos p){return EcologyLoadedBlocks.state(world,blockPos(p));}
        @Override public State get(Pos p){return encode(checked(p));}
        @Override public boolean solidFace(Pos p,int face){return checked(p).isSideSolidFullSquare(world,blockPos(p),Direction.byId(FACES[face]));}
        @Override public boolean substrate(Pos p){var state=checked(p);return state.isIn(BlockTags.SCULK_REPLACEABLE)&&!state.hasBlockEntity();}
        @Override public boolean growthExcluded(Pos p){return checked(p).isIn(EcologyGrowthExclusions.BLOCKS);}
        @Override public boolean feedingPassage(Pos p){var state=checked(p);return state.isOf(QuietBelow.ANCIENT_FORGE_BLOCK)||state.isOf(QuietBelow.ANCIENT_ALTAR_BLOCK);}
        @Override public int light(Pos p){checked(p);return world.getLightLevel(blockPos(p));}
        @Override public boolean set(Pos p,State state){
            checked(p);
            boolean changed;
            try(var ignored=EcologyHooks.ownWrite()){changed=world.setBlockState(blockPos(p),decode(state),Block.NOTIFY_ALL);}
            Runtime runtime=WORLDS.get(world.getServer());
            if(changed&&state.network()&&runtime!=null&&runtime.engine.setup()!=null
                    &&runtime.engine.setup().dimension().equals(world.getRegistryKey().getValue().toString())&&runtime.engine.owns(p))runtime.crops.source(p);
            return changed;
        }
        @Override public boolean appearance(Pos p,State expected,int stage){
            if(stage<0||stage>3)throw new IllegalArgumentException("Invalid sculk appearance");
            BlockState actual=checked(p);
            if(!actual.contains(SculkAppearance.VITALITY)||actual.get(SculkAppearance.VITALITY)==stage
                    ||!encode(actual).sameOwnedShape(expected))return false;
            try(var ignored=EcologyHooks.ownWrite()){
                if(!world.setBlockState(blockPos(p),actual.with(SculkAppearance.VITALITY,stage),Block.NOTIFY_LISTENERS))
                    throw new IllegalStateException("Sculk appearance update failed; existing vitality retained");
            }
            return true;
        }
    }
}
