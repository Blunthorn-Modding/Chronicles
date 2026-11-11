package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.MapSiteAssignments;
import io.github.chronicle.core.server.PlayRuntime;
import io.github.chronicle.quietbelow.QuietBelow;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import static net.minecraft.server.command.CommandManager.literal;

/** Private native proof for real-time Heart last stands; never registered outside its isolated fixture. */
public final class EcologyHeartDefianceTrial {
    private static final Pattern RUN=Pattern.compile("[0-9]{8}T[0-9]{12,}Z");
    private static final BlockPos HEART=new BlockPos(0,65,0);
    private static final String CITY="defiance_city",ROOT="defiance_heart";
    private static final Map<MinecraftServer,Stage> STAGES=new IdentityHashMap<>();
    private EcologyHeartDefianceTrial(){}
    public static void register(){
        if(!"true".equals(System.getenv("CHRONICLE_HEART_DEFIANCE_TRIAL")))return;
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->commands(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            Stage stage=STAGES.get(server);
            if(stage==null||stage.second||stage.claimSignals!=null)return;
            var signals=QuietBelowEcology.signalsForTest(server);
            if(signals.phase().equals("AUTHORIZED")&&signals.wardenAttempted())stage.claimSignals=signals;
        });
    }
    private static void commands(CommandDispatcher<ServerCommandSource> dispatcher){
        var root=literal("quiet_below_heart_defiance_trial").requires(source->source.hasPermissionLevel(2));
        for(String verb:List.of("prepare","observe","cut","warning","reconnect","recut_after_warning","settle","status","first","second_cut","second_settle","second","restart"))root.then(literal(verb).executes(context->run(context.getSource(),verb)));
        dispatcher.register(root);
    }
    private static int run(ServerCommandSource source,String verb){
        try{guard(source);var server=source.getServer();var world=source.getWorld();var player=source.getPlayerOrThrow();
            switch(verb){
                case "prepare"->prepare(source,world,player);
                case "observe"->observe(server);
                case "cut"->cut(server,world,false);
                case "warning"->warning(server,world);
                case "settle"->settle(server,false);
                case "status"->status(source,server,world);
                case "first"->first(server,world);
                case "reconnect"->reconnect(server,world);
                case "recut_after_warning"->{require(stage(server).warningAborted,"warning cycle was not verified before recut");cut(server,world,false);}
                case "second_cut"->cut(server,world,true);
                case "second_settle"->settle(server,true);
                case "second"->second(server,world);
                case "restart"->restart(server,world);
                default->throw new IllegalArgumentException("Unknown defiance trial action");
            }
            source.sendFeedback(()->Text.literal("PASS Defiance "+verb),false);return 1;
        }catch(Exception error){source.sendError(Text.literal("FAIL Defiance "+verb+": "+detail(error)));return 0;}
    }
    private static void guard(ServerCommandSource source){
        MinecraftServer server=source.getServer();Path root;
        try{root=server.getRunDirectory().toRealPath();}catch(IOException error){throw new IllegalStateException("Defiance trial cannot resolve its server root",error);}
        if(!source.hasPermissionLevel(2)||!server.isDedicated()||server.isOnlineMode()||!"127.0.0.1".equals(server.getServerIp())||server.getServerPort()!=25575||root.getParent()==null||!"test-runs".equals(root.getParent().getFileName().toString())||!RUN.matcher(root.getFileName().toString()).matches())throw new IllegalStateException("Defiance trial is limited to an offline test-runs server");
        if(source.getWorld().getRegistryKey()!=net.minecraft.world.World.OVERWORLD)throw new IllegalStateException("Defiance trial requires the Overworld");
    }
    private static void prepare(ServerCommandSource source,ServerWorld world,ServerPlayerEntity player)throws Exception{
        EcologyEngine e=QuietBelowEcology.engineForTest(source.getServer());require(e.setup()==null,"fixture already configured");
        for(int x=-4;x<=20;x++)for(int z=-4;z<=20;z++){put(world,new BlockPos(x,63,z),Blocks.STONE.getDefaultState());if(x<0||x>15||z<0||z>15)put(world,new BlockPos(x,64,z),Blocks.STONE.getDefaultState());}
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)put(world,new BlockPos(x,64,z),Blocks.SCULK.getDefaultState());put(world,HEART,QuietBelow.RELIC_HEART.getDefaultState());
        AuthoringRuntime author=AuthoringRuntime.get(source.getServer());author.enable(player.getUuid());String dimension=world.getRegistryKey().getValue().toString();
        var city=(AuthoringSite.Region)author.service().put(new AuthoringSite.Region(CITY,dimension,1,new BlockPosition(-4,63,-4),new BlockPosition(20,66,20)),false);
        var heart=(AuthoringSite.Anchor)author.service().put(new AuthoringSite.Anchor(ROOT,dimension,1,new BlockPosition(0,65,0),"quiet_below:relic_heart"),false);
        MapSiteAssignments.assign(source,"quiet_below:source_city",city.id());MapSiteAssignments.assign(source,"quiet_below:relic_heart",heart.id());
        e.configure(new EcologyEngine.Setup(city.id(),city.revision(),heart.id(),heart.revision(),dimension),new EcologyEngine.Area(new EcologyTerrain.Pos(-4,63,-4),new EcologyTerrain.Pos(20,66,20)),QuietBelowEcology.pos(HEART));
        var terrain=new QuietBelowEcology.Adapter(world);for(int i=0;i<800&&!e.view().indexed();i++)e.index(terrain);require(e.view().indexed(),"source did not finish bounded paused indexing");e.mode(true);
        ChronicleRuntime.service(source.getServer()).start(new Actor(player.getName().getString(),player.getUuidAsString()),null);PlayRuntime.setSelfParticipant(player,true);author.disable(player.getUuid());player.interactionManager.changeGameMode(GameMode.CREATIVE);player.teleport(world,0.5,66,4.5,Set.of(),180,15,false);
        STAGES.put(source.getServer(),new Stage());
    }
    private static void observe(MinecraftServer server){EcologyEngine e=QuietBelowEcology.engineForTest(server);require(e.heartProof(true).complete()&&e.heartProof(true).living()>=256&&!e.heartVulnerable(true),"Heart is not protected by the 256-cell source");}
    private static void status(ServerCommandSource source,MinecraftServer server,ServerWorld world)throws Exception{EcologyEngine e=QuietBelowEcology.engineForTest(server);HeartDefianceStore.State state=store(server).load();int wardens=wardens(world).size();long receipt=state.receipt()==null?0:state.receipt().revision();long claimed=state.receipt()==null?-1:state.receipt().claimedAtActiveTicks();long eligible=state.receipt()==null?-1:state.receipt().eligibleAfterActiveTicks();String phase=QuietBelowEcology.signalsForTest(server).phase();source.sendFeedback(()->Text.literal("Defiance status elapsed="+e.heartElapsed()+" living="+e.heartProof(true).living()+" complete="+e.heartProof(true).complete()+" wardens="+wardens+" receipt="+receipt+" active="+PlayRuntime.elapsedTicks(server)+" phase="+phase+" claimed="+claimed+" eligible="+eligible),false);}
    private static void cut(MinecraftServer server,ServerWorld world,boolean second){Stage stage=stage(server);EcologyEngine e=QuietBelowEcology.engineForTest(server);
        for(int dx=-3;dx<=3;dx++)for(int dy=-3;dy<=3;dy++)for(int dz=-3;dz<=3;dz++){BlockPos at=HEART.add(dx,dy,dz);if(at.equals(HEART))continue;BlockState state=world.getBlockState(at);if(isNetwork(state)){if(!second)stage.original.put(at,state);put(world,at,Blocks.STONE.getDefaultState());e.noticeContact(QuietBelowEcology.pos(at));}}
        stage.cutAt=PlayRuntime.elapsedTicks(server);stage.second=second;require(e.heartElapsed()==0,"cut started with an existing weakness clock");
    }
    private static void settle(MinecraftServer server,boolean second){Stage stage=stage(server);EcologyEngine e=QuietBelowEcology.engineForTest(server);EcologyHeart.Proof proof=e.heartProof(true);require(proof.complete()&&proof.living()==0,"cut topology has not settled to a complete zero-living proof");Set<EcologyTerrain.Pos> positions=e.cells().stream().map(EcologyEngine.Cell::position).collect(java.util.stream.Collectors.toSet());if(second)stage.secondBaseline=positions;else stage.firstBaseline=positions;}
    private static void warning(MinecraftServer server,ServerWorld world)throws IOException{Stage stage=stage(server);EcologyEngine e=QuietBelowEcology.engineForTest(server);require(!stage.second&&e.heartElapsed()>=220&&e.heartElapsed()<1000,"warning was not observed before final effort");HeartDefianceStore.State state=store(server).load();var signals=QuietBelowEcology.signalsForTest(server);require("WARNING".equals(signals.phase())&&signals.heartbeatTicks().equals(List.of(200)),"first warning did not emit only its initial heartbeat: "+signals);require(state.receipt()==null,"warning claimed the durable cooldown before final effort");require(wardens(world).isEmpty()&&!signals.wardenAttempted()&&!signals.wardenSpawned(),"warning started a Warden or last-stand effect");require(e.heartBurstOutcome().written()==0,"warning started a final growth burst");stage.warningAborted=true;}
    private static void first(MinecraftServer server,ServerWorld world)throws Exception{Stage stage=stage(server);EcologyEngine e=QuietBelowEcology.engineForTest(server);require(!stage.second&&PlayRuntime.elapsedTicks(server)-stage.cutAt>=1040,"first burst has not had its bounded completion window");
        HeartDefianceStore.State state=store(server).load();require(stage.warningAborted&&state.receipt()!=null,"first last-stand has no durable receipt after the aborted warning");var receipt=state.receipt();require(receipt.claimedAtActiveTicks()>=stage.cutAt+990&&receipt.claimedAtActiveTicks()<=stage.cutAt+1020,"receipt was not claimed near the actual final effort: "+receipt.claimedAtActiveTicks()+" versus "+stage.cutAt);require(receipt.eligibleAfterActiveTicks()==receipt.claimedAtActiveTicks()+12_000,"cooldown expiry is not based on the final-effort claim");require(stage.claimSignals!=null&&stage.claimSignals.heartbeatTicks().equals(List.of(200,380,540,680,800,900,960,980)),"captured first last stand did not emit the exact accelerating heartbeat schedule: "+stage.claimSignals);var wardens=wardens(world);require(wardens.size()==1,"expected one direct Warden, found "+wardens.size());require(stage.firstBaseline!=null&&e.cells().stream().anyMatch(cell->nearHeart(cell.position())&&!stage.firstBaseline.contains(cell.position())&&cell.after().network()),"last stand did not journal a new root-near growth attempt");Set<String> footprint=new HashSet<>();
        for(var cell:e.cells())if(!stage.firstBaseline.contains(cell.position())&&cell.after().network()&&Math.abs(cell.position().x())<=2&&Math.abs(cell.position().z())<=2&&Math.abs(cell.position().y()-65)<=3){
            require(cell.after().sameOwnedShape(QuietBelowEcology.encode(world.getBlockState(new BlockPos(cell.position().x(),cell.position().y(),cell.position().z())))),"burst ledger/world mismatch");
            footprint.add(cell.position().x()+":"+cell.position().z());
        }
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)if(x!=0||z!=0)require(footprint.contains(x+":"+z),"burst missed eligible5x5 surface column "+x+","+z+"; footprint="+footprint);
        stage.warden=wardens.getFirst().getUuid();stage.receiptRevision=state.receipt().revision();write(server,stage);
    }
    private static void reconnect(MinecraftServer server,ServerWorld world){Stage stage=stage(server);EcologyEngine e=QuietBelowEcology.engineForTest(server);require(!stage.original.isEmpty(),"no cut network states were saved");stage.original.forEach((at,state)->{put(world,at,state);e.noticeContact(QuietBelowEcology.pos(at));});}
    private static void second(MinecraftServer server,ServerWorld world)throws Exception{Stage stage=stage(server);EcologyEngine e=QuietBelowEcology.engineForTest(server);require(stage.second&&e.heartElapsed()>=1000,"second weakness phase has not reached 50 active seconds");HeartDefianceStore.State state=store(server).load();require(state.receipt()!=null&&state.receipt().revision()==stage.receiptRevision,"cooldown wrote a second receipt");require(wardens(world).stream().allMatch(warden->warden.getUuid().equals(stage.warden)),"cooldown spawned another Warden");require(stage.secondBaseline!=null&&e.cells().stream().map(EcologyEngine.Cell::position).collect(java.util.stream.Collectors.toSet()).equals(stage.secondBaseline),"cooldown permitted a second ecology growth change");require(!networkInShell(world),"cooldown restored network blocks in the Heart shell");require(QuietBelowEcology.signalsForTest(server).heartbeatTicks().isEmpty()&&!QuietBelowEcology.signalsForTest(server).wardenAttempted(),"cooldown emitted a second heartbeat or Warden attempt");require(e.heartProof(true).complete()&&e.heartProof(true).living()==0,"second cut is not a complete zero-living proof");write(server,stage);}
    private static void restart(MinecraftServer server,ServerWorld world)throws Exception{Stage saved=read(server);EcologyEngine e=QuietBelowEcology.engineForTest(server);HeartDefianceStore.State state=store(server).load();require(e.heartElapsed()>=100&&e.heartElapsed()<=400,"restart did not begin a fresh bounded weakness wait");require(state.receipt()!=null&&state.receipt().revision()==saved.receiptRevision,"restart changed the durable defiance receipt");require(QuietBelowEcology.signalsForTest(server).heartbeatTicks().isEmpty()&&!QuietBelowEcology.signalsForTest(server).wardenAttempted(),"restart replayed last-stand effects");require(wardens(world).stream().allMatch(warden->warden.getUuid().equals(saved.warden)),"restart spawned another Warden");}
    private static HeartDefianceStore store(MinecraftServer server){return new HeartDefianceStore(server.getSavePath(WorldSavePath.ROOT).resolve("quiet-below").resolve("heart-defiance.json"));}
    private static List<WardenEntity> wardens(ServerWorld world){return world.getEntitiesByClass(WardenEntity.class,new Box(-6,60,-6,7,72,7),warden->true);}
    private static Stage stage(MinecraftServer server){Stage stage=STAGES.get(server);if(stage==null)throw new IllegalStateException("Run prepare first");return stage;}
    private static Path evidence(MinecraftServer server){return server.getSavePath(WorldSavePath.ROOT).resolve("defiance-first.json");}
    private static void write(MinecraftServer server,Stage stage)throws IOException{JsonObject json=new JsonObject();json.addProperty("receiptRevision",stage.receiptRevision);json.addProperty("warden",stage.warden.toString());Files.writeString(evidence(server),json.toString(),StandardCharsets.UTF_8);}
    private static Stage read(MinecraftServer server)throws IOException{JsonObject json=JsonParser.parseString(Files.readString(evidence(server))).getAsJsonObject();Stage stage=new Stage();stage.receiptRevision=json.get("receiptRevision").getAsLong();stage.warden=UUID.fromString(json.get("warden").getAsString());return stage;}
    private static void put(ServerWorld world,BlockPos at,BlockState state){try(var ignored=EcologyHooks.ownWrite()){world.setBlockState(at,state,net.minecraft.block.Block.NOTIFY_ALL);}}
    private static boolean isNetwork(BlockState state){return QuietBelowEcology.encode(state).network();}
    private static boolean nearHeart(EcologyTerrain.Pos at){return Math.abs(at.x()-HEART.getX())+Math.abs(at.y()-HEART.getY())+Math.abs(at.z()-HEART.getZ())<=1;}
    private static boolean networkInShell(ServerWorld world){for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++)if(Math.abs(dx)+Math.abs(dy)+Math.abs(dz)<=2&&!HEART.equals(HEART.add(dx,dy,dz))&&isNetwork(world.getBlockState(HEART.add(dx,dy,dz))))return true;return false;}
    private static void require(boolean value,String detail){if(!value)throw new IllegalStateException(detail);}
    private static String detail(Exception error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
    private static final class Stage {final Map<BlockPos,BlockState> original=new HashMap<>();long cutAt,receiptRevision;Set<EcologyTerrain.Pos> firstBaseline,secondBaseline;UUID warden;QuietBelowEcology.DefianceSignals claimSignals;boolean second,warningAborted;}
}
