package io.github.chronicle.quietbelow.ecology;

import com.mojang.brigadier.CommandDispatcher;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.PlayRuntime;
import io.github.chronicle.quietbelow.QuietBelow;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in native reproduction against an archived world clone; never available to normal servers. */
public final class EcologyHeartAirCutTrial {
    private static final Pattern RUN=Pattern.compile("[0-9]{8}T[0-9]{12,}Z");
    private static final Map<MinecraftServer,Stage> STAGES=new IdentityHashMap<>();
    private EcologyHeartAirCutTrial(){}
    public static void register(){
        if(!"true".equals(System.getenv("CHRONICLE_HEART_AIRCUT_TRIAL")))return;
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->commands(dispatcher));
    }
    private static void commands(CommandDispatcher<ServerCommandSource> dispatcher){
        var root=literal("quiet_below_heart_aircut_trial").requires(source->source.hasPermissionLevel(2));
        for(String verb:Set.of("prepare","cut","settle","status","verify"))root.then(literal(verb).executes(context->run(context.getSource(),verb)));
        dispatcher.register(root);
    }
    private static int run(ServerCommandSource source,String verb){
        try{guard(source);switch(verb){
            case "prepare"->prepare(source);
            case "cut"->cut(source);
            case "settle"->settle(source);
            case "status"->status(source);
            case "verify"->verify(source);
            default->throw new IllegalArgumentException("Unknown air-cut action");
        }source.sendFeedback(()->Text.literal("PASS AirCut "+verb),false);return 1;
        }catch(Exception error){source.sendError(Text.literal("FAIL AirCut "+verb+": "+detail(error)));return 0;}
    }
    private static void guard(ServerCommandSource source){
        MinecraftServer server=source.getServer();Path root;
        try{root=server.getRunDirectory().toRealPath();}catch(IOException error){throw new IllegalStateException("Air-cut trial cannot resolve its server root",error);}
        if(!source.hasPermissionLevel(2)||!server.isDedicated()||server.isOnlineMode()||!"127.0.0.1".equals(server.getServerIp())||server.getServerPort()!=25575||root.getParent()==null||!"test-runs".equals(root.getParent().getFileName().toString())||!RUN.matcher(root.getFileName().toString()).matches())throw new IllegalStateException("Air-cut trial is limited to an offline test-runs server");
        if(source.getWorld().getRegistryKey()!=net.minecraft.world.World.OVERWORLD)throw new IllegalStateException("Air-cut trial requires the Overworld");
    }
    private static void prepare(ServerCommandSource source)throws Exception{
        MinecraftServer server=source.getServer();EcologyEngine engine=QuietBelowEcology.engineForTest(server);require(engine.setup()!=null&&engine.enabled()&&engine.view().indexed(),"archived fixture ecology is not ready");
        BlockPos heart=block(engine.rootPosition());ServerWorld world=source.getWorld();
        ServerPlayerEntity player=source.getPlayerOrThrow();AuthoringRuntime.get(server).disable(player.getUuid());PlayRuntime.setSelfParticipant(player,true);
        if(ChronicleRuntime.service(server).state().phase()==io.github.chronicle.core.campaign.SessionPhase.PAUSED)ChronicleRuntime.service(server).resume(new io.github.chronicle.core.campaign.Actor(player.getName().getString(),player.getUuidAsString()));
        require(ChronicleRuntime.service(server).state().phase()==io.github.chronicle.core.campaign.SessionPhase.ACTIVE,"archived fixture is not active");
        player.interactionManager.changeGameMode(GameMode.CREATIVE);
        player.teleport(world,heart.getX()+.5,heart.getY()+2,heart.getZ()+.5,Set.of(),180,30,false);
        STAGES.put(server,new Stage(heart));
    }
    private static void cut(ServerCommandSource source)throws IOException{
        MinecraftServer server=source.getServer();Stage stage=stage(server);EcologyEngine engine=QuietBelowEcology.engineForTest(server);ServerWorld world=source.getWorld();requireLoaded(world,stage.heart);
        require(world.getBlockState(stage.heart).isOf(QuietBelow.RELIC_HEART),"archived fixture Heart is missing at "+stage.heart.toShortString());
        for(EcologyEngine.Cell cell:engine.cells()){
            BlockPos at=block(cell.position());if(!inWindow(stage.heart,at)){stage.remoteOwned++;continue;}if(at.equals(stage.heart)||!cell.after().network())continue;
            if(engine.owns(cell.position())){put(world,at,Blocks.AIR.getDefaultState());engine.observed(cell.position(),EcologyTerrain.AIR);stage.removed++;}
        }
        require(stage.removed>0,"no owned network cells were present in the five by seven by five Heart window");
        require(stage.remoteOwned>0,"archived fixture has no remote owned ledger cells to retain during the local air cut");
        stage.cutAt=PlayRuntime.elapsedTicks(server);
    }
    private static void settle(ServerCommandSource source){
        MinecraftServer server=source.getServer();Stage stage=stage(server);EcologyEngine engine=QuietBelowEcology.engineForTest(server);EcologyHeart.Proof proof=engine.heartProof(true);
        require(proof.complete()&&proof.living()==0,"air cut has not reached complete zero living proof");
        require(engine.cells().stream().noneMatch(cell->inWindow(stage.heart,block(cell.position()))),"air cut retained a local owned seed");
        stage.baseline.clear();for(EcologyEngine.Cell cell:engine.cells())stage.baseline.add(cell.position());
    }
    private static void status(ServerCommandSource source){
        MinecraftServer server=source.getServer();Stage stage=stage(server);EcologyEngine engine=QuietBelowEcology.engineForTest(server);EcologyHeart.Proof proof=engine.heartProof(true);EcologyEngine.HeartBurstOutcome outcome=engine.heartBurstOutcome();QuietBelowEcology.DefianceSignals signals=QuietBelowEcology.signalsForTest(server);
        int newLocal=0;for(EcologyEngine.Cell cell:engine.cells())if(!stage.baseline.contains(cell.position())&&inWindow(stage.heart,block(cell.position()))&&cell.after().network())newLocal++;
        int wardens=source.getWorld().getEntitiesByClass(WardenEntity.class,new Box(stage.heart).expand(8,6,8),warden->true).size();
        String beats=signals.heartbeatTicks().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        int finalNewLocal=newLocal;
        source.sendFeedback(()->Text.literal("AirCut status elapsed="+engine.heartElapsed()+" living="+proof.living()+" complete="+proof.complete()+" removed="+stage.removed+" remoteOwned="+stage.remoteOwned+" newLocal="+finalNewLocal+" wardens="+wardens+" planned="+outcome.planned()+" checked="+outcome.checked()+" written="+outcome.written()+" reason="+outcome.reason()+" active="+outcome.active()+" beats="+beats+" wardenAttempted="+signals.wardenAttempted()+" wardenSpawned="+signals.wardenSpawned()),false);
    }
    private static void verify(ServerCommandSource source){
        MinecraftServer server=source.getServer();Stage stage=stage(server);EcologyEngine engine=QuietBelowEcology.engineForTest(server);EcologyEngine.HeartBurstOutcome outcome=engine.heartBurstOutcome();QuietBelowEcology.DefianceSignals signals=QuietBelowEcology.signalsForTest(server);
        require(engine.heartElapsed()>=1040,"final effort has not had its real-time completion window");
        require(!outcome.active()&&outcome.reason().equals("complete"),"final effort did not finish: "+outcome);
        require(outcome.written()>=24,"final effort did not write a complete footprint; outcome="+outcome.reason()+" planned="+outcome.planned()+" checked="+outcome.checked());
        int wardens=source.getWorld().getEntitiesByClass(WardenEntity.class,new Box(stage.heart).expand(8,6,8),warden->true).size();require(wardens==1,"expected one live Warden, found "+wardens);
        require(signals.heartbeatTicks().equals(List.of(200,380,540,680,800,900,960,980)),"unexpected final-effort heartbeat schedule: "+signals.heartbeatTicks());
        require(signals.wardenAttempted()&&signals.wardenSpawned(),"the final effort did not record a successful direct Warden attempt");
        int newLocal=0;for(EcologyEngine.Cell cell:engine.cells())if(!stage.baseline.contains(cell.position())&&inWindow(stage.heart,block(cell.position()))&&cell.after().network())newLocal++;
        Set<String> footprint=new HashSet<>();
        for(EcologyEngine.Cell cell:engine.cells())if(!stage.baseline.contains(cell.position())&&inWindow(stage.heart,block(cell.position()))&&cell.after().network()) {
            require(cell.after().sameOwnedShape(QuietBelowEcology.encode(source.getWorld().getBlockState(block(cell.position())))),"local burst cell does not match the real world");
            footprint.add((cell.position().x()-stage.heart.getX())+":"+(cell.position().z()-stage.heart.getZ()));
        }
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(dx!=0||dz!=0)
            require(footprint.contains(dx+":"+dz),"final effort missed eligible5x5 column "+dx+","+dz+"; footprint="+footprint);
        source.sendFeedback(()->Text.literal("AirCut verified24columns outcome="+outcome),false);
    }
    private static Stage stage(MinecraftServer server){Stage stage=STAGES.get(server);if(stage==null)throw new IllegalStateException("Run prepare first");return stage;}
    private static boolean inWindow(BlockPos heart,BlockPos at){return Math.abs(at.getX()-heart.getX())<=2&&Math.abs(at.getY()-heart.getY())<=3&&Math.abs(at.getZ()-heart.getZ())<=2;}
    private static void requireLoaded(ServerWorld world,BlockPos heart){for(int x=heart.getX()-2;x<=heart.getX()+2;x+=4)for(int z=heart.getZ()-2;z<=heart.getZ()+2;z+=4)require(world.getChunkManager().isChunkLoaded(x>>4,z>>4),"Heart-window chunk is not player-loaded: "+(x>>4)+","+(z>>4));}
    private static BlockPos block(EcologyTerrain.Pos position){return new BlockPos(position.x(),position.y(),position.z());}
    private static void put(ServerWorld world,BlockPos at,BlockState state){try(var ignored=EcologyHooks.ownWrite()){world.setBlockState(at,state,Block.NOTIFY_ALL);}}
    private static void require(boolean value,String detail){if(!value)throw new IllegalStateException(detail);}
    private static String detail(Exception error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
    private static final class Stage {final BlockPos heart;final Set<EcologyTerrain.Pos> baseline=new HashSet<>();long cutAt;int removed,remoteOwned;Stage(BlockPos heart){this.heart=heart;}}
}
