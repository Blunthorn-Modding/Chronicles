package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.MapSiteAssignments;
import io.github.chronicle.core.server.OfferingStats;
import io.github.chronicle.core.server.PlayRuntime;
import io.github.chronicle.quietbelow.QuietBelow;
import io.github.chronicle.quietbelow.content.AncientAltars;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in operations rehearsal. No command or callback exists in normal servers. */
public final class EradicationRecoveryTrial {
    private static final int BASE = 1024;
    private static final BlockPos HEART = new BlockPos(BASE,80,BASE);
    private static final BlockPos GROWN = new BlockPos(BASE-1,79,BASE);
    private static final BlockPos COMPLETED_EDIT = new BlockPos(BASE,64,BASE);
    private static final BlockPos PENDING_EDIT = new BlockPos(BASE+15,79,BASE+15);
    private static final Map<MinecraftServer,Stage> STAGES = new IdentityHashMap<>();
    private enum Mode { PREPARED, CHECKPOINT, SAVED, CRASHING, CRASH_READY, RECOVERED, UNLOADING, UNLOADED, RETURNING, COMPLETE }
    private static final class Stage {
        Mode mode;
        int saved, last, maxDelta, unloadedProgress;
        long unloadedAt = -1;
        Stage(Mode mode,int completed) { this.mode=mode;last=completed; }
    }
    private EradicationRecoveryTrial() { }

    public static void register() {
        if(!"true".equals(System.getenv("CHRONICLE_ERADICATION_RECOVERY_TRIAL")))return;
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            var root=literal("quiet_below_recovery_trial").requires(source->source.hasPermissionLevel(2));
            for(String verb:Set.of("prepare","status","checkpoint","resume_to_crash","recovered","unload","return","final"))
                root.then(literal(verb).executes(context->run(context.getSource(),verb)));
            dispatcher.register(root);
        });
        ServerTickEvents.END_SERVER_TICK.register(EradicationRecoveryTrial::tick);
    }

    private static int run(ServerCommandSource source,String verb) {
        try {
            guard(source);
            switch(verb) {
                case "prepare" -> prepare(source);
                case "status" -> { status(source);return 1; }
                case "checkpoint" -> checkpoint(source);
                case "resume_to_crash" -> {
                    var stage=stage(source.getServer());require(stage.mode==Mode.SAVED,"Checkpoint is not saved");
                    stage.mode=Mode.CRASHING;resume(source.getServer());
                }
                case "recovered" -> recovered(source);
                case "unload" -> {
                    var stage=stage(source.getServer());require(stage.mode==Mode.RECOVERED,"Recovery was not verified");
                    stage.mode=Mode.UNLOADING;resume(source.getServer());
                    source.getPlayerOrThrow().teleport(source.getWorld(),512.5,82,512.5,Set.of(),0,0,false);
                }
                case "return" -> {
                    var stage=stage(source.getServer());require(stage.mode==Mode.UNLOADED,"Natural unload was not proved");
                    stage.mode=Mode.RETURNING;
                    source.getPlayerOrThrow().teleport(source.getWorld(),BASE+8.5,82,BASE+8.5,Set.of(),180,30,false);
                }
                case "final" -> finalCheck(source);
                default -> throw new IllegalArgumentException("Unknown recovery trial action");
            }
            source.sendFeedback(()->Text.literal("PASS Recovery "+verb),false);return 1;
        } catch(Exception error) {
            source.sendError(Text.literal("FAIL Recovery "+verb+": "+message(error)));return 0;
        }
    }

    private static void guard(ServerCommandSource source)throws IOException {
        var server=source.getServer();Path root=server.getRunDirectory().toRealPath();
        require(source.hasPermissionLevel(2)&&server.isDedicated()&&!server.isOnlineMode()
                &&"127.0.0.1".equals(server.getServerIp())&&server.getServerPort()==25575
                &&root.getParent()!=null&&root.getParent().getFileName().toString().equals("test-runs")
                &&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z"),"Not an owned offline recovery fixture");
        require(source.getWorld().getRegistryKey()==World.OVERWORLD,"Overworld fixture required");
        require(Files.isRegularFile(root.resolve(".eradication-recovery-fixture")),"Missing recovery fixture marker");
        reports(server); // Reject a linked or missing external report directory before any fixture mutation.
    }

    private static Path reports(MinecraftServer server)throws IOException {
        Path root=server.getRunDirectory().toRealPath();
        Path report=root.getParent().getParent().resolve("reports").resolve("eradication-recovery-"+root.getFileName());
        require(report.toRealPath().equals(report),"Recovery reports must be an existing real directory");
        return report;
    }

    private static void prepare(ServerCommandSource source)throws Exception {
        var server=source.getServer();var world=source.getWorld();var player=source.getPlayerOrThrow();
        var engine=QuietBelowEcology.engineForTest(server);require(engine.setup()==null,"Fixture is not fresh");
        require(world.getChunkManager().getWorldChunk(BASE>>4,BASE>>4)!=null&&world.getChunkManager().getWorldChunk((BASE-1)>>4,BASE>>4)!=null,"Wait for player-loaded fixture chunks");
        player.interactionManager.changeGameMode(GameMode.CREATIVE);player.getAbilities().flying=true;player.sendAbilitiesUpdate();
        player.teleport(world,BASE+8.5,82,BASE+8.5,Set.of(),180,30,false);
        for(int x=0;x<16;x++)for(int y=64;y<80;y++)for(int z=0;z<16;z++)put(world,new BlockPos(BASE+x,y,BASE+z),Blocks.SCULK.getDefaultState());
        put(world,GROWN,Blocks.STONE.getDefaultState());put(world,HEART,QuietBelow.RELIC_HEART.getDefaultState());
        var author=AuthoringRuntime.get(server);author.enable(player.getUuid());String dimension=world.getRegistryKey().getValue().toString();
        var region=(AuthoringSite.Region)author.service().put(new AuthoringSite.Region("recovery_source",dimension,1,new BlockPosition(BASE,64,BASE),new BlockPosition(BASE+15,80,BASE+15)),false);
        var anchor=(AuthoringSite.Anchor)author.service().put(new AuthoringSite.Anchor("recovery_heart",dimension,1,new BlockPosition(BASE,80,BASE),"quiet_below:relic_heart"),false);
        MapSiteAssignments.assign(source,"quiet_below:source_city",region.id());MapSiteAssignments.assign(source,"quiet_below:relic_heart",anchor.id());
        engine.configure(new EcologyEngine.Setup(region.id(),region.revision(),anchor.id(),anchor.revision(),dimension),
                new EcologyEngine.Area(new EcologyTerrain.Pos(BASE,64,BASE),new EcologyTerrain.Pos(BASE+15,80,BASE+15)),QuietBelowEcology.pos(HEART));
        var terrain=new QuietBelowEcology.Adapter(world);
        for(int n=0;n<1000&&!engine.view().indexed();n++)engine.index(terrain);
        require(engine.view().indexed()&&engine.cells().size()==4096,"Dense fixture did not index exactly4096 source cells");
        engine.mode(true);
        for(int n=0;n<1000&&!engine.heartProof(true).complete();n++){engine.beginTick();engine.tick(terrain,true);}
        require(engine.heartProof(true).complete(),"Fixture topology did not settle");
        require(engine.nativeChange(terrain,QuietBelowEcology.pos(new BlockPos(BASE,79,BASE)),QuietBelowEcology.pos(GROWN),
                QuietBelowEcology.encode(Blocks.STONE.getDefaultState()),EcologyTerrain.SCULK),"Grown stone-baseline cell was not authorized");
        put(world,GROWN,Blocks.SCULK.getDefaultState());
        for(int n=0;n<1000&&!engine.heartProof(true).complete();n++){engine.beginTick();engine.tick(terrain,true);}
        engine.tuneHeart(new EcologyHeart.Rules(8192,1));
        for(int n=0;n<20;n++)engine.heartTick(true,true);
        require(engine.heartVulnerable(true),"Fixture weakness did not mature");
        ChronicleRuntime.service(server).start(actor(),null);PlayRuntime.setSelfParticipant(player,true);author.disable(player.getUuid());
        engine.eradicate(UUID.randomUUID(),QuietBelowEcology.pos(HEART),QuietBelowEcology.encode(world.getBlockState(HEART)),true);
        STAGES.put(server,new Stage(Mode.PREPARED,0));
    }

    private static void checkpoint(ServerCommandSource source)throws Exception {
        var server=source.getServer();var stage=stage(server);var cleanup=cleanup(server);
        require(stage.mode==Mode.CHECKPOINT,"Not at the first partial checkpoint");
        require(cleanup.completed()>=64&&cleanup.completed()<cleanup.total(),"Checkpoint must be partial");
        put(source.getWorld(),COMPLETED_EDIT,Blocks.GOLD_BLOCK.getDefaultState());cleanup.changed(source.getWorld(),COMPLETED_EDIT);
        put(source.getWorld(),PENDING_EDIT,Blocks.GOLD_BLOCK.getDefaultState());cleanup.changed(source.getWorld(),PENDING_EDIT);
        stage.saved=cleanup.completed();stage.last=cleanup.completed();
        verifyReceipts(source.getWorld(),cleanup);verifyForeign(source.getWorld(),false);
        require(server.save(false,true,true),"Partial chunk flush failed");
        var proof=proof(server);proof.addProperty("savedCompleted",stage.saved);proof.addProperty("maxDelta",stage.maxDelta);
        proof.addProperty("chunksFlushed",true);
        var receipts=new JsonArray();
        for(var plan:cleanup.plansForTest()) {
            var marker=cleanup.markerForTest(source.getWorld(),plan);if(marker==null)continue;
            var receipt=new JsonObject();receipt.addProperty("x",plan.x());receipt.addProperty("z",plan.z());
            receipt.add("marker",EcologyEradication.MARKER_CODEC.encodeStart(JsonOps.INSTANCE,marker).getOrThrow());receipts.add(receipt);
        }
        proof.add("receipts",receipts);
        write(server,"save-point.json",proof);stage.mode=Mode.SAVED;
    }

    private static void recovered(ServerCommandSource source)throws Exception {
        var server=source.getServer();var cleanup=cleanup(server);var saved=read(server,"save-point.json");
        require(ChronicleRuntime.service(server).state().phase()==SessionPhase.PAUSED,"Crash restart must retain pause");
        var crashed=read(server,"crash-ready.json");
        require(cleanup.completed()>=saved.get("savedCompleted").getAsInt()&&cleanup.completed()<=crashed.get("unsavedCompleted").getAsInt()
                &&cleanup.completed()<cleanup.total(),"Saved partial progress did not recover within the crash checkpoint bounds");
        require(QuietBelowEcology.engineForTest(server).ending().toString().equals(saved.get("job").getAsString()),"Terminal job changed on crash restart");
        for(var entry:saved.getAsJsonArray("receipts")) {
            var receipt=entry.getAsJsonObject();
            var plan=cleanup.plansForTest().stream().filter(p->p.x()==receipt.get("x").getAsInt()&&p.z()==receipt.get("z").getAsInt()).findFirst().orElseThrow();
            var persisted=EcologyEradication.MARKER_CODEC.parse(JsonOps.INSTANCE,receipt.get("marker")).getOrThrow();
            var current=cleanup.markerForTest(source.getWorld(),plan);
            require(current!=null&&current.covers(persisted),"Saved per-chunk completion/conflict bits were lost");
        }
        require(cleanup.rootComplete(),"Root receipt did not recover");verifyTerminal(server);verifyReceipts(source.getWorld(),cleanup);verifyForeign(source.getWorld(),false);
        var stage=new Stage(Mode.RECOVERED,cleanup.completed());stage.saved=saved.get("savedCompleted").getAsInt();STAGES.put(server,stage);
        var proof=proof(server);proof.addProperty("recoveredCompleted",cleanup.completed());write(server,"recovered.json",proof);
    }

    private static void finalCheck(ServerCommandSource source)throws Exception {
        var server=source.getServer();var cleanup=cleanup(server);
        require(ChronicleRuntime.service(server).state().phase()==SessionPhase.PAUSED,"Final restart should remain paused");
        require(cleanup.completed()==cleanup.total()&&cleanup.pendingChunks()==0,"Completed receipts did not survive restart");
        verifyTerminal(server);verifyReceipts(source.getWorld(),cleanup);verifyAll(source.getWorld(),cleanup,true);
        var proof=proof(server);proof.addProperty("allTargetsVerified",cleanup.total());write(server,"final-restart.json",proof);
    }

    private static void tick(MinecraftServer server) {
        Stage stage=STAGES.get(server);if(stage==null)return;
        try {
            var cleanup=QuietBelowEcology.endingForTest(server);if(cleanup==null)return;
            require(cleanup.failure()==null,"Cleanup held: "+cleanup.failure());
            int delta=cleanup.completed()-stage.last;stage.last=cleanup.completed();
            require(delta>=0,"Progress went backwards");stage.maxDelta=Math.max(stage.maxDelta,delta);
            require(delta<=4,"Cleanup completed more than its four-target budget");
            if(stage.mode==Mode.PREPARED&&cleanup.completed()>=64){pause(server);stage.mode=Mode.CHECKPOINT;}
            if(stage.mode==Mode.CRASHING&&cleanup.completed()>=stage.saved+64) {
                require(cleanup.completed()<cleanup.total(),"Crash point is not partial");pause(server);stage.mode=Mode.CRASH_READY;
                var proof=proof(server);proof.addProperty("savedCompleted",stage.saved);proof.addProperty("unsavedCompleted",cleanup.completed());
                proof.addProperty("maxDelta",stage.maxDelta);write(server,"crash-ready.json",proof);
            }
            ServerWorld world=server.getOverworld();
            if(stage.mode==Mode.UNLOADING||stage.mode==Mode.UNLOADED) {
                require(cleanup.completed()<cleanup.total(),"Fixture finished before natural unload");
                if(loadedPlans(world,cleanup)==0) {
                    long now=PlayRuntime.elapsedTicks(server);
                    if(stage.unloadedAt<0){stage.unloadedAt=now;stage.unloadedProgress=cleanup.completed();}
                    require(cleanup.completed()==stage.unloadedProgress,"Unloaded chunks advanced cleanup");
                    if(now-stage.unloadedAt>=100&&stage.mode==Mode.UNLOADING) {
                        stage.mode=Mode.UNLOADED;var proof=proof(server);proof.addProperty("occupiedUnloadedTicks",now-stage.unloadedAt);
                        proof.addProperty("loadedPlans",0);write(server,"unloaded.json",proof);
                    }
                } else require(stage.unloadedAt<0,"Affected chunks reloaded while the player remained distant");
            }
            if(stage.mode==Mode.RETURNING&&cleanup.completed()==cleanup.total()) {
                pause(server);verifyTerminal(server);verifyReceipts(world,cleanup);verifyAll(world,cleanup,false);
                put(world,GROWN,Blocks.DIAMOND_BLOCK.getDefaultState());cleanup.changed(world,GROWN);
                verifyAll(world,cleanup,true);require(server.save(false,true,true),"Completed chunk flush failed");stage.mode=Mode.COMPLETE;
                var proof=proof(server);proof.addProperty("allTargetsVerified",cleanup.total());proof.addProperty("maxDelta",stage.maxDelta);
                write(server,"completed.json",proof);
            }
        } catch(Exception error) {
            STAGES.remove(server);
            try{pause(server);var failure=new JsonObject();failure.addProperty("error",message(error));write(server,"trial-failure.json",failure);}
            catch(Exception nested){QuietBelow.LOGGER.error("Recovery fixture failure could not be recorded",nested);}
        }
    }

    private static void verifyTerminal(MinecraftServer server) {
        var engine=QuietBelowEcology.engineForTest(server);
        require(engine.ending()!=null&&!engine.enabled()&&!engine.cropsAvailable()&&!engine.recoveryAvailable()&&!engine.costsAvailable(),"Ended ecology services became active");
        require(OfferingStats.ledger(server).closedSources().contains(AncientAltars.SOURCE),"Altar source closure did not persist");
    }

    private static void verifyReceipts(ServerWorld world,EcologyEradication cleanup) {
        for(var plan:cleanup.plansForTest()) {
            require(world.getChunkManager().getWorldChunk(plan.x(),plan.z())!=null,"Receipt chunk is not player-loaded");
            var marker=cleanup.markerForTest(world,plan);
            require(marker!=null||plan!=cleanup.plansForTest().getFirst(),"Root completion receipt is absent");
            if(marker==null)continue; // An untouched non-root plan legitimately has no receipt yet.
            var done=marker.doneBits();var conflicts=marker.conflictBits();
            for(int i=done.nextSetBit(0);i>=0;i=done.nextSetBit(i+1)) {
                var target=plan.targets().get(i);
                var actual=QuietBelowEcology.encode(world.getChunkManager().getWorldChunk(plan.x(),plan.z()).getBlockState(QuietBelowEcology.blockPos(target.position())));
                var expected=conflicts.get(i)?EcologyEradication.Status.CONFLICT:EcologyEradication.Status.DONE;
                require(target.decide(actual).status()==expected,"Receipt disagrees with restored/foreign terrain: "+target.position());
            }
        }
    }

    private static void verifyForeign(ServerWorld world,boolean finalEdit) {
        require(world.getBlockState(COMPLETED_EDIT).isOf(Blocks.GOLD_BLOCK)&&world.getBlockState(PENDING_EDIT).isOf(Blocks.GOLD_BLOCK),"A foreign replacement was overwritten");
        if(finalEdit)require(world.getBlockState(GROWN).isOf(Blocks.DIAMOND_BLOCK),"A post-completion edit was overwritten");
    }

    private static void verifyAll(ServerWorld world,EcologyEradication cleanup,boolean finalEdit) {
        require(loadedPlans(world,cleanup)==cleanup.plansForTest().size(),"Final plan chunks are not loaded");
        int checked=0;
        for(var plan:cleanup.plansForTest())for(var target:plan.targets()) {
            BlockPos at=QuietBelowEcology.blockPos(target.position());var expected=target.restore();
            if(at.equals(COMPLETED_EDIT)||at.equals(PENDING_EDIT))expected=QuietBelowEcology.encode(Blocks.GOLD_BLOCK.getDefaultState());
            if(finalEdit&&at.equals(GROWN))expected=QuietBelowEcology.encode(Blocks.DIAMOND_BLOCK.getDefaultState());
            require(expected.sameOwnedShape(QuietBelowEcology.encode(world.getChunkManager().getWorldChunk(plan.x(),plan.z()).getBlockState(at))),"Wrong restored/foreign state at "+at.toShortString());checked++;
        }
        require(checked==cleanup.total()&&checked>=4098,"Incomplete final target verification");verifyForeign(world,finalEdit);
    }

    private static JsonObject proof(MinecraftServer server) {
        var cleanup=cleanup(server);var result=new JsonObject();result.addProperty("result","PASS");
        result.addProperty("job",QuietBelowEcology.engineForTest(server).ending().toString());result.addProperty("total",cleanup.total());
        result.addProperty("phase",ChronicleRuntime.service(server).state().phase().name());
        result.addProperty("completed",cleanup.completed());result.addProperty("pendingChunks",cleanup.pendingChunks());result.addProperty("conflicts",cleanup.conflicts());
        return result;
    }
    private static void status(ServerCommandSource source) {
        var server=source.getServer();var cleanup=QuietBelowEcology.endingForTest(server);var result=new JsonObject();
        result.addProperty("mode",STAGES.containsKey(server)?stage(server).mode.name():"RESTORED");
        result.addProperty("completed",cleanup==null?0:cleanup.completed());result.addProperty("total",cleanup==null?0:cleanup.total());
        result.addProperty("loadedPlans",cleanup==null?0:loadedPlans(source.getWorld(),cleanup));
        result.addProperty("phase",ChronicleRuntime.service(server).state().phase().name());
        source.sendFeedback(()->Text.literal("RECOVERY "+result),false);
    }
    private static int loadedPlans(ServerWorld world,EcologyEradication cleanup) {
        return (int)cleanup.plansForTest().stream().filter(plan->world.getChunkManager().getWorldChunk(plan.x(),plan.z())!=null).count();
    }
    private static EcologyEradication cleanup(MinecraftServer server) {
        var value=QuietBelowEcology.endingForTest(server);require(value!=null&&value.failure()==null,"Terminal cleanup is unavailable");return value;
    }
    private static Stage stage(MinecraftServer server) {var value=STAGES.get(server);require(value!=null,"Fixture stage is unavailable");return value;}
    private static Actor actor() {return new Actor("EradicationRecoveryTrial",null);}
    private static void pause(MinecraftServer server)throws IOException {if(ChronicleRuntime.service(server).state().phase()==SessionPhase.ACTIVE)ChronicleRuntime.service(server).pause(actor());}
    private static void resume(MinecraftServer server)throws IOException {ChronicleRuntime.service(server).resume(actor());}
    private static void put(ServerWorld world,BlockPos at,net.minecraft.block.BlockState state) {try(var ignored=EcologyHooks.ownWrite()){world.setBlockState(at,state,Block.NOTIFY_ALL);}}
    private static JsonObject read(MinecraftServer server,String file)throws IOException {return JsonParser.parseString(Files.readString(reports(server).resolve(file))).getAsJsonObject();}
    private static void write(MinecraftServer server,String file,JsonObject value)throws IOException {
        Path target=reports(server).resolve(file),temporary=target.resolveSibling(file+".tmp");
        Files.writeString(temporary,value.toString(),StandardCharsets.UTF_8);
        Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    private static void require(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
    private static String message(Exception error) {return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
}
