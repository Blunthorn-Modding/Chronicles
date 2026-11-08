package io.github.chronicle.core.server.testing;

import io.github.chronicle.core.campaign.*;
import io.github.chronicle.core.eventpool.*;
import io.github.chronicle.core.menu.EventEditorForm;
import io.github.chronicle.core.play.*;
import io.github.chronicle.core.server.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;

import java.nio.file.*;
import java.util.*;
import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in native pool trial, restricted to the disposable Council fixture. */
public final class EventPoolTrial {
    public static final String MESSAGE="A trial bell rings across the council.";
    private static final String FIRST="chronicle_core:pool_trial_first", SECOND="chronicle_core:pool_trial_second";
    private static final String FLAG="chronicle_core:pool_trial_flag", FLAG2="chronicle_core:pool_trial_flag_second";
    private static final String KEY="pool-trial-first", PENDING="pool-trial-pending";
    private static Job job;
    private EventPoolTrial() {}

    public static void register() {
        if (!"1".equals(System.getenv("CHRONICLE_POOL_TRIAL"))) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                literal("chronicle_pool_trial").then(literal("prepare").executes(c -> run(c.getSource(), "prepare")))
                        .then(literal("initial").executes(c -> run(c.getSource(), "initial")))
                        .then(literal("restart").executes(c -> run(c.getSource(), "restart")))));
        ServerTickEvents.END_SERVER_TICK.register(EventPoolTrial::tick);
    }

    private static int run(ServerCommandSource source, String phase) {
        Path report=null;
        try {
            fixture(source); report=reportPath(source.getServer());
            require(source.hasPermissionLevel(2), "operator permission required");
            require(job==null, "trial already running");
            ServerPlayerEntity player=source.getPlayerOrThrow();
            if (phase.equals("prepare")) {
                var campaign=ChronicleRuntime.service(source.getServer());
                require(campaign.state().phase()==SessionPhase.PAUSED,"prepare requires a paused campaign");
                if(!campaign.state().flags().containsKey(FLAG)) campaign.defineFlag(actor(player),FLAG);
                if(!campaign.state().flags().containsKey(FLAG2)) campaign.defineFlag(actor(player),FLAG2);
                require(!campaign.state().flags().get(FLAG)&&!campaign.state().flags().get(FLAG2),"trial flags must be false");
                save(player,FIRST,MESSAGE,"minecraft:block.note_block.bell",FLAG);
                save(player,SECOND,"","",FLAG2);
                writeReport(report,"CHRONICLE_POOL_TRIAL_OK","prepare");
                source.sendFeedback(()->Text.literal("CHRONICLE_POOL_TRIAL_OK prepare"),false);
                return 1;
            }
            if (phase.equals("restart")) {
                EventPoolDraw first=EventPools.draw(source.getServer(),pool(FIRST,9),KEY);
                require(EventPools.status(source.getServer(),KEY)==EventPools.Status.COMPLETE,"archived status missing");
                require(EventPools.dispatch(source.getServer(),KEY)==EventPools.Status.COMPLETE,"archived dispatch changed status");
                require(!ChronicleRuntime.service(source.getServer()).state().flags().getOrDefault(FLAG,false),"archived dispatch repeated outcome");
                EventPoolDraw pending=EventPools.draw(source.getServer(),pool(SECOND,9),PENDING);
                require(EventPools.status(source.getServer(),PENDING)==EventPools.Status.RESERVED,"pending draw was not retained");
                require(!first.operationId().equals(pending.operationId()),"occurrence IDs collided");
                EventPools.dispatch(source.getServer(),PENDING);
                job=new Job(source,report,pending,true,0);
            } else {
                require(ChronicleRuntime.service(source.getServer()).state().phase()==SessionPhase.ACTIVE,"initial requires an active campaign");
                EventPoolDraw first=EventPools.draw(source.getServer(),pool(FIRST,1),KEY);
                require(EventPools.status(source.getServer(),KEY)==EventPools.Status.RESERVED,"draw not reserved before dispatch");
                require(first.equals(EventPools.draw(source.getServer(),pool(FIRST,9),KEY)),"retry changed saved draw");
                EventPoolDraw pending=EventPools.draw(source.getServer(),pool(SECOND,1),PENDING);
                require(EventPools.status(source.getServer(),PENDING)==EventPools.Status.RESERVED,"pending draw not reserved");
                require(!first.operationId().equals(pending.operationId()),"occurrence IDs collided");
                EventPools.dispatch(source.getServer(),KEY);
                job=new Job(source,report,first,false,0);
            }
            return 1;
        } catch (Exception failure) { failReport(report,phase,failure); source.sendError(Text.literal("Pool trial FAIL "+phase)); return 0; }
    }

    private static void tick(MinecraftServer server) {
        Job current=job; if(current==null||current.source().getServer()!=server)return;
        try {
            require(current.ticks()<1200,"completion timed out");
            EventPools.Status status=EventPools.status(server,current.restart()?PENDING:KEY);
            require(status!=EventPools.Status.CONFLICT&&status!=EventPools.Status.MEDIA_UNCERTAIN,"execution did not complete safely");
            if(status!=EventPools.Status.COMPLETE){job=new Job(current.source(),current.report(),current.draw(),current.restart(),current.ticks()+1);return;}
            var draw=current.draw(); var play=PlayRuntime.service(server);
            EventExecution execution=play.state().executions().get(draw.configuration().definition().id());
            require(execution!=null&&execution.phase()==EventExecution.Phase.COMPLETE&&execution.operationId().equals(draw.operationId())
                    &&execution.configuration().equals(draw.configuration())&&execution.session()==draw.session()
                    &&Objects.equals(execution.actor(),draw.actor())&&new HashSet<>(execution.recipients()).equals(new HashSet<>(draw.recipients())),"execution differs from saved draw");
            require(ChronicleRuntime.service(server).state().flags().getOrDefault(current.restart()?FLAG2:FLAG,false),"flag outcome missing");
            if(!current.restart()) {
                play.reset(draw.configuration().definition().id());
                ChronicleRuntime.service(server).setFlag(actor(current.source().getPlayerOrThrow()),FLAG,false);
                require(EventPools.status(server,KEY)==EventPools.Status.COMPLETE,"reset lost archived completion");
                require(EventPools.dispatch(server,KEY)==EventPools.Status.COMPLETE,"reset allowed redispatch");
                require(!ChronicleRuntime.service(server).state().flags().getOrDefault(FLAG,false),"reset dispatch repeated outcome");
            }
            writeReport(current.report(),"CHRONICLE_POOL_TRIAL_OK",current.restart()?"restart":"initial");
            current.source().sendFeedback(()->Text.literal("CHRONICLE_POOL_TRIAL_OK "+(current.restart()?"restart":"initial")),false); job=null;
        } catch(Exception failure){failReport(current.report(),current.restart()?"restart":"initial",failure);current.source().sendError(Text.literal("Pool trial FAIL "+(current.restart()?"restart":"initial")));job=null;}
    }

    private static void writeReport(Path path,String result,String phase)throws Exception {
        Files.writeString(path,"{\"result\":\""+result+"\",\"phase\":\""+phase+"\"}\n");
    }
    private static void failReport(Path path,String phase,Exception failure) {
        if(path==null)return;
        try { var json=new com.google.gson.JsonObject();json.addProperty("result","CHRONICLE_POOL_TRIAL_FAIL");json.addProperty("phase",phase);json.addProperty("detail",failure.getClass().getSimpleName()+": "+failure.getMessage());Files.writeString(path,json+"\n"); } catch(Exception ignored) { }
    }

    private static EventPoolDefinition pool(String event,int weight){return new EventPoolDefinition("chronicle_core:pool_trial","Pool trial",List.of(new EventPoolDefinition.Entry(event,weight)));}
    private static void save(ServerPlayerEntity player,String id,String message,String sound,String flag)throws Exception {
        EventEditorForm form=new EventEditorForm(id,0,"Pool trial Event","Fixture",message,sound,ObservationCatalog.Audience.PARTICIPANTS.name(),"",0,PlayState.Mode.MANUAL.name(),"",0,0,List.of(),List.of(),true,Map.of(flag,true),EventSource.empty());
        EventRuntime.save(player,form,null,true);
    }
    private static Actor actor(ServerPlayerEntity player){return new Actor("pool-trial",player.getUuidAsString());}
    private static void fixture(ServerCommandSource source){
        var server=source.getServer(); Path root=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
        require(root!=null&&root.getParent()!=null&&root.getParent().getFileName().toString().equals("test-runs")&&Files.isRegularFile(root.resolve(".council-ballots-fixture"))&&server.isDedicated()&&!server.isOnlineMode()&&server.getServerPort()==25575&&"127.0.0.1".equals(server.getServerIp())&&source.getWorld().getRegistryKey()==World.OVERWORLD,"unsafe Council fixture");
    }
    private static Path reportPath(MinecraftServer server)throws Exception {
        Path root=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
        Path reports=root.getParent().getParent().resolve("reports").toRealPath();
        String configured=System.getenv("CHRONICLE_POOL_TRIAL_REPORT"); require(configured!=null&&!configured.isBlank(),"report path required");
        Path target=Path.of(configured).toAbsolutePath().normalize();
        require(target.getParent()!=null&&target.getParent().toRealPath().startsWith(reports)&&!Files.isSymbolicLink(target),"report must be inside workspace reports");
        return target;
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private record Job(ServerCommandSource source,Path report,EventPoolDraw draw,boolean restart,int ticks) {}
}
