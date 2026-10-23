package io.github.chronicle.quietbelow.ecology;

import com.google.gson.*;
import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.server.*;
import io.github.chronicle.quietbelow.QuietBelow;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static net.minecraft.server.command.CommandManager.literal;

/** Actual-tick, physical-bridge rate comparison. Never available in a normal/review server. */
public final class EcologySpreadTrial {
    private static final BlockPos HEART=new BlockPos(-4,64,0);
    private static final int SAMPLE_TICKS=400;
    private static final Map<MinecraftServer,Measurement> MEASUREMENTS=new IdentityHashMap<>();
    private EcologySpreadTrial(){ }

    public static void register(){
        if(!"true".equals(System.getenv("CHRONICLE_SPREAD_NATIVE_TRIAL")))return;
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            var root=literal("quiet_below_spread_trial").requires(s->s.hasPermissionLevel(2));
            for(String verb:List.of("prepare","measure","result","review","overview","return","restart"))root.then(literal(verb).executes(c->run(c.getSource(),verb)));
            dispatcher.register(root);
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            Measurement m=MEASUREMENTS.get(server);if(m==null||m.done)return;
            try{advance(m);}catch(Exception error){m.failure=error.toString();m.done=true;save(m);QuietBelow.LOGGER.error("Spread measurement failed",error);}
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(MEASUREMENTS::remove);
    }

    private static int run(ServerCommandSource source,String verb){
        try{
            requireFixture(source);var world=source.getWorld();var player=source.getPlayerOrThrow();
            var engine=QuietBelowEcology.engineForTest(source.getServer());
            switch(verb){
                case "prepare"->prepare(source,world,player,engine);
                case "measure"->{require(!MEASUREMENTS.containsKey(source.getServer()),"measurement already started");var m=new Measurement(source.getServer(),world,engine);MEASUREMENTS.put(source.getServer(),m);select(m);}
                case "result"->{var m=MEASUREMENTS.get(source.getServer());require(m!=null&&m.done&&m.failure==null,"measurement not successful");validate(m.rows);var facts=GameSetupTools.status(source,"quiet_below:spread").facts();require(facts.stream().anyMatch(f->f.label().equals("Count")&&f.value().startsWith(engine.countedSculk()+" ")),"operator diagnostic count differs from rate input");}
                case "review"->{require(MEASUREMENTS.containsKey(source.getServer())&&MEASUREMENTS.get(source.getServer()).done&&MEASUREMENTS.get(source.getServer()).failure==null,"finish measurement first");review(world,player);ChronicleRuntime.service(source.getServer()).pause(actor(player));}
                case "overview"->{require(ChronicleRuntime.service(source.getServer()).state().phase().name().equals("PAUSED"),"pause before overview");put(world,new BlockPos(9,89,35),Blocks.GLASS.getDefaultState());player.teleport(world,9.5,90,35.5,Set.of(),180,35,false);}
                case "return"->{player.teleport(world,3.5,65,8.5,Set.of(),-90,-12,false);put(world,new BlockPos(9,89,35),Blocks.AIR.getDefaultState());}
                case "restart"->{JsonObject saved=JsonParser.parseString(Files.readString(resultFile(source.getServer()),StandardCharsets.UTF_8)).getAsJsonObject();require(saved.get("result").getAsString().equals("PASS"),"missing passing measurement");validate(saved.getAsJsonArray("samples"));require(engine.speedPercent()==100&&engine.spreadRules().equals(new EcologyEngine.SpreadRules(EcologyEngine.GrowthMode.SCULK_COUNT,100)),"spread tuning did not persist");require(engine.spreadAttempts()==0,"diagnostic attempts persisted or advanced while paused");for(int x=8;x<=10;x++)require(world.getBlockState(new BlockPos(x,64,3)).isOf(Blocks.SCULK),"review bridge missing");require(ChronicleRuntime.service(source.getServer()).state().phase().name().equals("PAUSED"),"review did not stay paused");}
                default->throw new IllegalArgumentException("Unknown spread trial");
            }
            source.sendFeedback(()->Text.literal("PASS Spread "+verb+" (physical network and rate checks)"),false);return 1;
        }catch(Exception error){QuietBelow.LOGGER.error("Spread trial {} failed",verb,error);source.sendError(Text.literal("FAIL Spread "+verb+": "+error.getMessage()));return 0;}
    }

    private static void prepare(ServerCommandSource source,ServerWorld world,ServerPlayerEntity player,EcologyEngine engine)throws Exception{
        for(int x=-6;x<=7;x++)for(int z=-2;z<=10;z++)put(world,new BlockPos(x,63,z),Blocks.POLISHED_ANDESITE.getDefaultState());
        for(int x=16;x<=31;x++)for(int z=0;z<16;z++)put(world,new BlockPos(x,63,z),Blocks.POLISHED_ANDESITE.getDefaultState());
        put(world,HEART,QuietBelow.RELIC_HEART.getDefaultState());
        for(int x=-3;x<=-1;x++)put(world,new BlockPos(x,64,0),Blocks.SCULK.getDefaultState());
        for(int x=0;x<8;x++)for(int z=0;z<4;z++)put(world,new BlockPos(x,64,z),Blocks.SCULK.getDefaultState());
        for(int x=11;x<=15;x++)put(world,new BlockPos(x,64,3),Blocks.SCULK.getDefaultState());
        for(int x=16;x<=31;x++)for(int y=64;y<80;y++)for(int z=0;z<16;z++)put(world,new BlockPos(x,y,z),Blocks.SCULK.getDefaultState());
        put(world,new BlockPos(3,64,8),Blocks.STONE.getDefaultState());
        var author=AuthoringRuntime.get(source.getServer());author.enable(player.getUuid());String dimension=world.getRegistryKey().getValue().toString();
        var region=(AuthoringSite.Region)author.service().put(new AuthoringSite.Region("spread_source",dimension,1,new BlockPosition(-4,64,0),new BlockPosition(31,79,15)),false);
        var heart=(AuthoringSite.Anchor)author.service().put(new AuthoringSite.Anchor("spread_heart",dimension,1,new BlockPosition(-4,64,0),"quiet_below:relic_heart"),false);
        MapSiteAssignments.assign(source,"quiet_below:source_city",region.id());MapSiteAssignments.assign(source,"quiet_below:relic_heart",heart.id());
        engine.configure(new EcologyEngine.Setup(region.id(),region.revision(),heart.id(),heart.revision(),dimension),new EcologyEngine.Area(new EcologyTerrain.Pos(-4,64,0),new EcologyTerrain.Pos(31,79,15)),QuietBelowEcology.pos(HEART));
        var terrain=new QuietBelowEcology.Adapter(world);for(int n=0;n<100&&!engine.view().indexed();n++)engine.index(terrain);
        require(engine.view().indexed()&&engine.view().owned()>=4096,"physical reservoir index incomplete");engine.speed(100);engine.mode(true);
        world.setTimeOfDay(18000);player.interactionManager.changeGameMode(GameMode.SURVIVAL);player.teleport(world,3.5,65,8.5,Set.of(),-90,-12,false);player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION,12000,0,false,false));
        ChronicleRuntime.service(source.getServer()).start(actor(player),null);PlayRuntime.setSelfParticipant(player,true);author.disable(player.getUuid());
    }

    private static void select(Measurement m)throws Exception{
        boolean large=m.phase>=2;for(int x=8;x<=10;x++){BlockPos p=new BlockPos(x,64,3);put(m.world,p,large?Blocks.SCULK.getDefaultState():Blocks.AIR.getDefaultState());if(large)m.engine.noticeContact(QuietBelowEcology.pos(p));}
        m.engine.tuneSpread(new EcologyEngine.SpreadRules(m.phase%2==0?EcologyEngine.GrowthMode.FIXED:EcologyEngine.GrowthMode.SCULK_COUNT,100));
        m.waitStarted=m.server.getTicks();m.started=-1;
    }
    private static void advance(Measurement m)throws Exception{
        long now=m.server.getTicks();boolean large=m.phase>=2;
        if(m.started<0){
            require(now-m.waitStarted<600,"physical graph/census did not settle");
            if(m.engine.view().unresolved()||m.engine.vitalityPending()||m.engine.countedSculk()<(large?4000:1)||!large&&m.engine.countedSculk()>512)return;
            m.started=now;m.attempts=m.engine.spreadAttempts();m.owned=m.engine.view().owned();m.count=m.engine.countedSculk();m.effective=m.engine.effectiveSpeedPercent();m.interval=m.engine.attemptTicks(false);return;
        }
        if(now-m.started<SAMPLE_TICKS)return;
        JsonObject row=new JsonObject();row.addProperty("label",(large?"large":"small")+(m.phase%2==0?"_fixed":"_count"));row.addProperty("serverTicks",now-m.started);row.addProperty("count",m.count);row.addProperty("effectivePercent",m.effective);row.addProperty("darkAttemptTicks",m.interval);row.addProperty("attempts",m.engine.spreadAttempts()-m.attempts);row.addProperty("newOwnedCells",m.engine.view().owned()-m.owned);m.rows.add(row);
        if(++m.phase<4){select(m);return;}validate(m.rows);m.done=true;save(m);
    }
    private static void validate(JsonArray rows){
        require(rows!=null&&rows.size()==4,"four samples required");
        for(var element:rows){var row=element.getAsJsonObject();require(row.get("serverTicks").getAsInt()==SAMPLE_TICKS,"sample duration changed");require(row.get("attempts").getAsLong()>0&&row.get("newOwnedCells").getAsInt()>0,"no actual attempts/growth");}
        var small=rows.get(0).getAsJsonObject();var smallCount=rows.get(1).getAsJsonObject();var big=rows.get(2).getAsJsonObject();var bigCount=rows.get(3).getAsJsonObject();
        require(small.get("count").getAsInt()<512&&smallCount.get("count").getAsInt()<512&&big.get("count").getAsInt()>=4000&&bigCount.get("count").getAsInt()>=4000,"physical size distinction missing");
        require(small.get("effectivePercent").getAsInt()==100&&big.get("effectivePercent").getAsInt()==100,"fixed rate changed with size");
        long fixed=small.get("attempts").getAsLong(),largeFixed=big.get("attempts").getAsLong(),largeCount=bigCount.get("attempts").getAsLong();
        require(Math.abs(fixed-largeFixed)<=Math.max(4,fixed/4),"fixed attempt cadence differs materially by size");
        require(largeCount>=largeFixed*2&&largeCount>=smallCount.get("attempts").getAsLong()*2,"large counted network did not accelerate actual attempts");
    }
    private static void review(ServerWorld world,ServerPlayerEntity player){
        sign(world,new BlockPos(5,64,8),"SPREAD COMPARISON","Small field / bank","Cut bridge 8..10","y64, z3");
        sign(world,new BlockPos(6,64,8),"TEST SETTINGS","Base 1x, count mode","+100% per 1000","Default is fixed");
        player.teleport(world,3.5,65,8.5,Set.of(),-90,-12,false);
    }
    private static void sign(ServerWorld world,BlockPos pos,String... lines){put(world,pos,Blocks.OAK_SIGN.getDefaultState());var sign=(SignBlockEntity)world.getBlockEntity(pos);require(sign!=null,"sign missing");var text=sign.getFrontText();for(int i=0;i<4;i++)text=text.withMessage(i,Text.literal(lines[i]));sign.setText(text,true);}
    private static void save(Measurement m){try{JsonObject data=new JsonObject();data.addProperty("result",m.failure==null?"PASS":"FAIL");if(m.failure!=null)data.addProperty("error",m.failure);data.add("samples",m.rows);Path file=resultFile(m.server);Files.createDirectories(file.getParent());Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(data),StandardCharsets.UTF_8);}catch(Exception error){QuietBelow.LOGGER.error("Cannot save spread measurement",error);}}
    private static Path resultFile(MinecraftServer server){return server.getSavePath(WorldSavePath.ROOT).resolve("quiet-below/spread-measurement.json");}
    private static Actor actor(ServerPlayerEntity p){return new Actor(p.getName().getString(),p.getUuidAsString());}
    private static void put(ServerWorld world,BlockPos p,net.minecraft.block.BlockState state){require(world.getChunkManager().isChunkLoaded(p.getX()>>4,p.getZ()>>4),"fixture chunk not loaded");world.setBlockState(p,state,Block.NOTIFY_LISTENERS);}
    private static void requireFixture(ServerCommandSource source)throws Exception{var server=source.getServer();Path root=server.getRunDirectory().toRealPath();require(server.isDedicated()&&!server.isOnlineMode()&&server.getServerPort()==25575&&"127.0.0.1".equals(server.getServerIp()),"offline loopback fixture required");require(root.getParent()!=null&&root.getParent().getFileName().toString().equals("test-runs")&&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z"),"disposable test-runs required");require(source.getWorld()==server.getOverworld(),"overworld required");}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static final class Measurement {
        final MinecraftServer server;final ServerWorld world;final EcologyEngine engine;final JsonArray rows=new JsonArray();
        int phase,count,effective,interval,owned;long waitStarted,started=-1,attempts;boolean done;String failure;
        Measurement(MinecraftServer server,ServerWorld world,EcologyEngine engine){this.server=server;this.world=world;this.engine=engine;}
    }
}
