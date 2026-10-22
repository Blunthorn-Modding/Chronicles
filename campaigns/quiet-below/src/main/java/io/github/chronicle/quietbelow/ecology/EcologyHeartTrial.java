package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.network.ActionConfirmationPayloads;
import io.github.chronicle.core.server.ActionConfirmations;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.MapSiteAssignments;
import io.github.chronicle.core.server.OfferingStats;
import io.github.chronicle.core.server.PlayRuntime;
import io.github.chronicle.core.stats.FoodCapacity;
import io.github.chronicle.quietbelow.QuietBelow;
import io.github.chronicle.quietbelow.content.AncientAltars;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import static net.minecraft.server.command.CommandManager.literal;

/** Offline-only, fresh-world evidence seam for the Relic Heart ending. */
public final class EcologyHeartTrial {
    private static final String SOURCE="heart_trial_source", ROOT="heart_trial_root", PLAN="heart-trial.json";
    private static final Pattern RUN=Pattern.compile("[0-9]{8}T[0-9]{12,}Z");
    private static final BlockPos HEART=new BlockPos(0,65,0), ORIGIN=new BlockPos(0,64,0), GROWN=new BlockPos(-1,64,0), DETACHED=new BlockPos(-8,64,-8), REPLACED=new BlockPos(2,64,0), PERSIST=new BlockPos(3,64,0);
    private static int checks;
    private static final Map<MinecraftServer,Measure> MEASURE=new java.util.IdentityHashMap<>();
    private static final Map<MinecraftServer,UUID> WAIT_OFFLINE=new java.util.IdentityHashMap<>();
    private EcologyHeartTrial() { }
    public static void register(){if(!"true".equals(System.getenv("CHRONICLE_HEART_NATIVE_TRIAL")))return;CommandRegistrationCallback.EVENT.register((d,a,e)->commands(d));
        ServerTickEvents.START_SERVER_TICK.register(server->{Measure m=MEASURE.get(server);if(m!=null)m.tickStart=System.nanoTime();});
        ServerTickEvents.END_SERVER_TICK.register(EcologyHeartTrial::measure);
        ServerTickEvents.END_SERVER_TICK.register(server->{
            UUID expected=WAIT_OFFLINE.get(server);if(expected==null||server.getPlayerManager().getPlayer(expected)!=null)return;
            try{Files.writeString(server.getSavePath(WorldSavePath.ROOT).resolve("heart-B-offline"),"server observed offline",StandardCharsets.UTF_8);WAIT_OFFLINE.remove(server);}catch(IOException error){throw new IllegalStateException(error);}
        });
        io.github.chronicle.core.server.ServerDisconnects.register((handler,server)->{
            QuietBelow.LOGGER.info("Heart trial disconnect callback {}",handler.player.getUuid());
            try{
                if(!Files.isRegularFile(plan(server)))return;
                JsonObject p=readPlan(server);if(!p.has("b")||!handler.player.getUuidAsString().equals(p.get("b").getAsString()))return;
                Files.writeString(server.getSavePath(WorldSavePath.ROOT).resolve("heart-B-offline"),"server disconnect callback",StandardCharsets.UTF_8);
                QuietBelow.LOGGER.info("Heart trial B disconnect recorded");
            }catch(IOException error){throw new IllegalStateException(error);}
        });}
    private static void commands(CommandDispatcher<ServerCommandSource> d){var root=literal("quiet_below_heart_trial").requires(s->s.hasPermissionLevel(2));for(String v:List.of("prepare","itemrender","safety","offline","open","ending","check","restart","restart_b"))root.then(literal(v).executes(c->run(c.getSource(),v)));d.register(root);}
    private static int run(ServerCommandSource source,String verb){checks=0;try{fixture(source);ServerWorld world=source.getWorld();ServerPlayerEntity player=source.getPlayerOrThrow();EcologyEngine e=QuietBelowEcology.engineForTest(source.getServer());switch(verb){
        case "prepare"->prepare(source,world,player,e);case "itemrender"->{player.getInventory().setStack(8,new net.minecraft.item.ItemStack(QuietBelow.RELIC_HEART_ITEM));player.getInventory().setSelectedSlot(8);player.playerScreenHandler.syncState();} case "safety"->safety(player,e); case "offline"->offline(source); case "open"->open(player,world,e.rootPosition()); case "ending"->ending(source,e);
        case "check"->{require(e.ending()!=null,"Heart ending was not durable");require(QuietBelowEcology.endingForTest(source.getServer()).failure()==null,"cleanup failure");source.getServer().save(false,true,true);require(Files.isRegularFile(plan(source.getServer())),"missing bounded fixture plan");}
        case "restart"->restart(source,e,false); case "restart_b"->restart(source,e,true); default->throw new IllegalArgumentException("unknown Heart trial verb");}
        int count=checks;source.sendFeedback(()->Text.literal("PASS Heart "+verb+" ("+count+" checks)"),false);return 1;
    }catch(Exception x){source.sendError(Text.literal("FAIL Heart "+verb+": "+detail(x)));return 0;}}
    private static void prepare(ServerCommandSource source,ServerWorld w,ServerPlayerEntity p,EcologyEngine e)throws Exception{
        require(e.setup()==null,"fresh Heart trial world already configured");
        for(int x=-9;x<=64;x++)for(int z=-9;z<=64;z++)put(w,new BlockPos(x,63,z),Blocks.STONE.getDefaultState());
        put(w,HEART,QuietBelow.RELIC_HEART.getDefaultState());put(w,GROWN,Blocks.STONE.getDefaultState()); for(int x=0;x<64;x++)for(int z=0;z<64;z++)put(w,new BlockPos(x,64,z),Blocks.SCULK.getDefaultState()); put(w,DETACHED,Blocks.SCULK.getDefaultState());
        AuthoringRuntime a=AuthoringRuntime.get(source.getServer());a.enable(p.getUuid());String dim=w.getRegistryKey().getValue().toString();
        AuthoringSite.Region region=(AuthoringSite.Region)a.service().put(new AuthoringSite.Region(SOURCE,dim,1,new BlockPosition(-9,63,-9),new BlockPosition(64,65,64)),false);
        AuthoringSite.Anchor anchor=(AuthoringSite.Anchor)a.service().put(new AuthoringSite.Anchor(ROOT,dim,1,new BlockPosition(0,65,0),"quiet_below:relic_heart"),false);
        MapSiteAssignments.assign(source,"quiet_below:source_city",region.id());MapSiteAssignments.assign(source,"quiet_below:relic_heart",anchor.id());e.configure(new EcologyEngine.Setup(region.id(),region.revision(),anchor.id(),anchor.revision(),dim),new EcologyEngine.Area(new EcologyTerrain.Pos(-9,63,-9),new EcologyTerrain.Pos(64,65,64)),QuietBelowEcology.pos(HEART));
        QuietBelowEcology.Adapter terrain=new QuietBelowEcology.Adapter(w);for(int i=0;i<5000&&!e.view().indexed();i++)e.index(terrain);require(e.view().indexed(),"bounded Heart source did not index"); e.mode(true);ChronicleRuntime.service(source.getServer()).start(actor(p),null);PlayRuntime.setSelfParticipant(p,true);a.disable(p.getUuid());p.interactionManager.changeGameMode(GameMode.SURVIVAL);p.teleport(w,0.5,65,3.5,java.util.Set.of(),180,20,false);
        require(ChronicleRuntime.service(source.getServer()).state().phase().name().equals("ACTIVE"),"session is not active");require(e.rootPosition()!=null,"bound Heart missing");e.tuneHeart(new EcologyHeart.Rules(8192,1));for(int tick=0;tick<400&&!e.heartVulnerable(true);tick++){e.beginTick();e.tick(terrain,true);e.heartTick(true,true);}require(e.heartVulnerable(true),"short native Heart weakness did not mature");
        boolean accepted=e.owns(QuietBelowEcology.pos(GROWN))&&e.cells().stream().anyMatch(c->c.position().equals(QuietBelowEcology.pos(GROWN))&&c.before().id().equals("minecraft:stone"));if(!accepted)accepted=e.nativeChange(terrain,QuietBelowEcology.pos(ORIGIN),QuietBelowEcology.pos(GROWN),QuietBelowEcology.encode(Blocks.STONE.getDefaultState()),QuietBelowEcology.encode(Blocks.SCULK.getDefaultState()));require(accepted,"native grown baseline was not authorized");put(w,GROWN,Blocks.SCULK.getDefaultState());MEASURE.put(source.getServer(),new Measure());writePlan(source.getServer());require(!e.rooted(QuietBelowEcology.pos(DETACHED)),"detached fixture unexpectedly connected");require(e.cells().stream().anyMatch(c->c.position().equals(QuietBelowEcology.pos(DETACHED))&&c.adopted()),"detached adopted cleanup target missing");
    }
    private static void safety(ServerPlayerEntity p,EcologyEngine e)throws Exception{MinecraftServer s=p.getEntityWorld().getServer();
        var w=p.getEntityWorld();
        require(!w.setBlockState(HEART,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL)&&w.getBlockState(HEART).isOf(QuietBelow.RELIC_HEART),"ordinary world write destroyed the Heart");
        require(!PistonBlock.isMovable(w.getBlockState(HEART),w,HEART,Direction.EAST,true,Direction.EAST),"piston can move bound Heart");
        require(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(w,p,HEART,w.getBlockState(HEART),null),"player break bypassed confirmation");
        BlockPos copy=new BlockPos(-2,65,1);put(w,copy,QuietBelow.RELIC_HEART.getDefaultState());require(!QuietBelowEcology.protectsRoot(w,copy),"unbound copy became authoritative");
        require(UseBlockCallback.EVENT.invoker().interact(p,w,Hand.MAIN_HAND,new BlockHitResult(copy.toCenterPos(),Direction.UP,copy,false))==ActionResult.PASS,"copy opened ending");
        e.tuneHeart(new EcologyHeart.Rules(1,1));for(int i=0;i<20;i++)e.heartTick(true,true);
        require(!e.heartVulnerable(true),"healthy network below impossible one-cell threshold");
        UseBlockCallback.EVENT.invoker().interact(p,w,Hand.MAIN_HAND,new BlockHitResult(HEART.toCenterPos(),Direction.UP,HEART,false));act(p,token(p),0);require(e.ending()==null,"strong Heart acceptance ended network");
        e.tuneHeart(new EcologyHeart.Rules(8192,1));for(int i=0;i<20;i++)e.heartTick(true,true);require(e.heartVulnerable(true),"weakness did not recover after tuning");
        p.interactionManager.changeGameMode(GameMode.CREATIVE);open(p,w,e.rootPosition(),false);act(p,token(p),0);require(e.ending()==null&&token(p)==null,"Creative acceptance ended network");p.interactionManager.changeGameMode(GameMode.SURVIVAL);
        p.teleport(w,.5,65,12.5,java.util.Set.of(),180,20,false);open(p,w,e.rootPosition(),false);act(p,token(p),0);require(e.ending()==null&&token(p)==null,"out-of-range acceptance ended network");p.teleport(w,.5,65,3.5,java.util.Set.of(),180,20,false);
        ChronicleRuntime.service(s).pause(actor(p));open(p,w,e.rootPosition(),false);act(p,token(p),0);require(e.ending()==null&&token(p)==null,"paused acceptance ended network");ChronicleRuntime.service(s).resume(actor(p));
ServerPlayerEntity b=s.getPlayerManager().getPlayerList().stream().filter(x->!x.equals(p)).findFirst().orElseThrow(()->new IllegalStateException("second client required"));require(e.ending()==null,"Heart already ended");require(!e.heartVulnerable(false),"invalid root became vulnerable");open(p,p.getEntityWorld(),e.rootPosition(),false);UUID a=token(p);open(b,b.getEntityWorld(),e.rootPosition(),false);UUID other=token(b);require(a!=null&&other!=null&&!a.equals(other),"offers not private");act(b,a,0);act(p,other,0);require(a.equals(token(p))&&other.equals(token(b)),"cross actor token accepted");act(p,a,1);UUID successor=token(p);require(successor!=null&&!successor.equals(a),"refresh did not issue successor");act(p,a,2);require(token(p)==null,"old cancel did not revoke successor");act(p,a,1);require(token(p)==null,"replayed token reopened offer");var preview=OfferingStats.preview(b,AncientAltars.SOURCE,AncientAltars.MOVEMENT);OfferingStats.purchase(b,AncientAltars.SOURCE,AncientAltars.MOVEMENT,UUID.randomUUID(),preview);require(b.getMaxHealth()<20,"B sacrifice absent");OfferingStats.purchase(p,AncientAltars.SOURCE,AncientAltars.EXTRA_HEART,UUID.randomUUID(),OfferingStats.preview(p,AncientAltars.SOURCE,AncientAltars.EXTRA_HEART));
        OfferingStats.purchase(p,AncientAltars.SOURCE,AncientAltars.MOVEMENT,UUID.randomUUID(),OfferingStats.preview(p,AncientAltars.SOURCE,AncientAltars.MOVEMENT));
        require(FoodCapacity.capacity(p)==22&&p.getAttributeValue(EntityAttributes.MOVEMENT_SPEED)>.1001,"online mixed bonuses and sacrifices missing");
        writeActor(s,b.getUuid());WAIT_OFFLINE.put(s,b.getUuid());}
    private static void offline(ServerCommandSource source)throws Exception{require(source.getServer().getPlayerManager().getPlayer("ChronicleFabricB")==null,"B must be disconnected before terminal cleanup");UUID b=UUID.fromString(readPlan(source.getServer()).get("b").getAsString());require(OfferingStats.ledger(source.getServer()).totals(b).containsKey(AncientAltars.SOURCE),"offline B receipt missing");Field field=ActionConfirmations.class.getDeclaredField("OFFERS");field.setAccessible(true);Map<?,?> all=(Map<?,?>)field.get(null);Map<?,?> offers=(Map<?,?>)all.get(source.getServer());require(offers==null||!offers.containsKey(b),"disconnect retained B confirmation");}
    private static void open(ServerPlayerEntity p,World w,EcologyTerrain.Pos root){open(p,w,root,true);}
    private static void open(ServerPlayerEntity p,World w,EcologyTerrain.Pos root,boolean replacement){BlockPos at=QuietBelowEcology.blockPos(root);require(w.getBlockState(at).isOf(QuietBelow.RELIC_HEART),"bound Relic Heart missing");require(QuietBelowEcology.engineForTest(p.getEntityWorld().getServer()).heartVulnerable(true),"fixture Heart has not become weak");if(replacement){try(var ignored=EcologyHooks.ownWrite()){((ServerWorld)w).setBlockState(REPLACED,Blocks.GOLD_BLOCK.getDefaultState(),Block.NOTIFY_ALL);}require(w.getBlockState(REPLACED).isOf(Blocks.GOLD_BLOCK),"replacement fixture write failed");}ActionResult r=UseBlockCallback.EVENT.invoker().interact(p,w,Hand.MAIN_HAND,new BlockHitResult(at.toCenterPos(),Direction.UP,at,false));require(r!=ActionResult.PASS,"registered Heart callback did not handle root");}
    private static void ending(ServerCommandSource source,EcologyEngine e)throws Exception{require(e.ending()!=null,"terminal transition missing");require(e.view().extent().equals("Eradicated")&&e.view().connected()==0&&!e.view().unresolved(),"ending view still claims live topology");require(io.github.chronicle.core.server.GameSetupTools.status(source,QuietBelowEcology.TOOL).disabledReasons().get("enable").contains("ended"),"ended configuration permits restart");require(!e.enabled()&&!e.cropsAvailable()&&!e.recoveryAvailable()&&!e.costsAvailable(),"ended network services remained active");
        require(!e.rooted(QuietBelowEcology.pos(ORIGIN))&&!e.living(QuietBelowEcology.pos(ORIGIN)),"ended source retained living authority");require(QuietBelowEcology.stationPower(source.getWorld(),ORIGIN.up()).contains("destroyed"),"ended network still powers stations");
        require(FoodCapacity.capacity(source.getPlayerOrThrow())==20&&source.getPlayerOrThrow().getMaxHealth()==20&&source.getPlayerOrThrow().getAttributeValue(EntityAttributes.MOVEMENT_SPEED)<.1001,"online ending retained bonus or sacrifice");EcologyEradication cleanup=QuietBelowEcology.endingForTest(source.getServer());require(cleanup!=null&&cleanup.failure()==null,"cleanup absent/failing");require(cleanup.completed()==cleanup.total(),"loaded cleanup incomplete");JsonObject measured=JsonParser.parseString(Files.readString(source.getServer().getSavePath(WorldSavePath.ROOT).resolve("heart-cleanup-measured.json"))).getAsJsonObject();require(measured.get("ticks").getAsInt()>900&&measured.get("maxCompletionsPerTick").getAsInt()<=4,"native cleanup bounds not measured");require(!QuietBelowEcology.protectsRoot(source.getWorld(),HEART),"root remains protected");verify(source);source.getWorld().setBlockState(PERSIST,Blocks.GOLD_BLOCK.getDefaultState(),Block.NOTIFY_ALL);require(cleanup.conflicts()>=1,"completed-target foreign edit was not preserved as conflict");try(var ignored=EcologyHooks.ownWrite()){source.getWorld().setBlockState(PERSIST,Blocks.SCULK.getDefaultState(),Block.NOTIFY_ALL);}require(source.getWorld().getBlockState(PERSIST).isOf(Blocks.SCULK),"post-cleanup foreign sculk was not placed");writePersist(source.getServer());require(OfferingStats.ledger(source.getServer()).closedSources().contains(AncientAltars.SOURCE),"source authority not closed");}
    private static void restart(ServerCommandSource source,EcologyEngine e,boolean b)throws Exception{require(e.ending()!=null,"terminal record missing after restart");EcologyEradication cleanup=QuietBelowEcology.endingForTest(source.getServer());require(cleanup!=null&&cleanup.failure()==null,"cleanup did not restore");verify(source);if(b){ServerPlayerEntity p=source.getPlayerOrThrow();require(p.getMaxHealth()>=19.99&&p.getAttributeValue(EntityAttributes.MOVEMENT_SPEED)<=0.1001,"offline B bargain survived closure");require(OfferingStats.ledger(source.getServer()).totals(p.getUuid()).isEmpty(),"offline B receipt survived closure");}}
    private static final class Measure {
        long tickStart,started,peakHeap;int last,ticks,maxDelta;final java.util.ArrayList<Long> cpu=new java.util.ArrayList<>(),ecology=new java.util.ArrayList<>();boolean done;
    }
    private static void measure(MinecraftServer server){
        Measure m=MEASURE.get(server);if(m==null||m.done)return;
        var e=QuietBelowEcology.engineForTest(server);var cleanup=QuietBelowEcology.endingForTest(server);if(e.ending()==null||cleanup==null)return;
        if(m.started==0)m.started=System.nanoTime();
        int now=cleanup.completed(),delta=now-m.last;m.maxDelta=Math.max(m.maxDelta,delta);m.last=now;m.ticks++;
        m.peakHeap=Math.max(m.peakHeap,java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        if(m.tickStart!=0)m.cpu.add(System.nanoTime()-m.tickStart);m.ecology.add(QuietBelowEcology.tickNanosForTest(server));
        if(cleanup.completed()!=cleanup.total()||cleanup.failure()!=null)return;
        m.done=true;JsonObject out=new JsonObject();out.addProperty("kind","native bounded terminal cleanup in a synthetic 4096-cell surface fixture; no city-map claim");
        out.addProperty("targets",cleanup.total());out.addProperty("ticks",m.ticks);out.addProperty("maxCompletionsPerTick",m.maxDelta);out.addProperty("wallSeconds",(System.nanoTime()-m.started)/1e9);
        out.addProperty("observedTicksPerSecond",Math.min(20,(m.ticks-1)*1e9/(System.nanoTime()-m.started)));out.addProperty("peakHeapBytes",m.peakHeap);
        out.addProperty("callbackWindowP99Millis",percentile(m.cpu,.99));out.addProperty("ecologyP99Millis",percentile(m.ecology,.99));
        try{Files.writeString(server.getSavePath(WorldSavePath.ROOT).resolve("heart-cleanup-measured.json"),out.toString(),StandardCharsets.UTF_8);}catch(IOException error){throw new IllegalStateException(error);}
    }
    private static double percentile(java.util.List<Long> samples,double quantile){long[] v=samples.stream().mapToLong(Long::longValue).sorted().toArray();return v.length==0?0:v[Math.min(v.length-1,(int)(v.length*quantile))]/1e6;}
    private static void verify(ServerCommandSource source)throws Exception{JsonObject p=readPlan(source.getServer());ServerWorld w=source.getWorld();expect(w,HEART,p,"root");expect(w,GROWN,p,"grown");expect(w,DETACHED,p,"detached");expect(w,REPLACED,p,"replacement");if(p.has("persist"))expect(w,PERSIST,p,"persist");}
    private static void expect(ServerWorld w,BlockPos at,JsonObject p,String key){require(net.minecraft.registry.Registries.BLOCK.getId(w.getBlockState(at).getBlock()).toString().equals(p.getAsJsonObject(key).get("id").getAsString()),"unexpected cleanup state at "+at);}
    private static void writePlan(MinecraftServer s)throws IOException{JsonObject p=new JsonObject();p.addProperty("version",1);state(p,"root",Blocks.AIR);state(p,"grown",Blocks.STONE);state(p,"detached",Blocks.AIR);state(p,"replacement",Blocks.GOLD_BLOCK);Files.writeString(plan(s),p.toString(),StandardCharsets.UTF_8);}
    private static void state(JsonObject p,String k,net.minecraft.block.Block b){JsonObject x=new JsonObject();x.addProperty("id",net.minecraft.registry.Registries.BLOCK.getId(b).toString());p.add(k,x);}
    private static void writeActor(MinecraftServer s,UUID b)throws IOException{JsonObject p=JsonParser.parseString(Files.readString(plan(s),StandardCharsets.UTF_8)).getAsJsonObject();p.addProperty("b",b.toString());Files.writeString(plan(s),p.toString(),StandardCharsets.UTF_8);} private static void writePersist(MinecraftServer s)throws IOException{JsonObject p=JsonParser.parseString(Files.readString(plan(s),StandardCharsets.UTF_8)).getAsJsonObject();state(p,"persist",Blocks.SCULK);Files.writeString(plan(s),p.toString(),StandardCharsets.UTF_8);}
    private static JsonObject readPlan(MinecraftServer s)throws IOException{JsonObject p=JsonParser.parseString(Files.readString(plan(s),StandardCharsets.UTF_8)).getAsJsonObject();require((p.size()>=5&&p.size()<=7)&&p.has("version")&&p.get("version").getAsInt()==1,"invalid Heart fixture plan");for(String k:List.of("root","grown","detached","replacement"))require(p.has(k)&&p.get(k).isJsonObject()&&p.getAsJsonObject(k).size()==1&&p.getAsJsonObject(k).has("id"),"invalid Heart fixture entry: "+k);return p;}
    private static Path plan(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve(PLAN);} private static void fixture(ServerCommandSource source)throws Exception{MinecraftServer s=source.getServer();Path run=s.getRunDirectory().toRealPath();require(s.isDedicated()&&!s.isOnlineMode()&&s.getServerPort()==25575&&"127.0.0.1".equals(s.getServerIp())&&run.getParent()!=null&&run.getParent().getFileName().toString().equals("test-runs")&&RUN.matcher(run.getFileName().toString()).matches()&&source.getPlayerOrThrow().hasPermissionLevel(2),"unsafe Heart fixture");}
    private static Actor actor(ServerPlayerEntity p){return new Actor(p.getName().getString(),p.getUuidAsString());} private static void put(ServerWorld w,BlockPos p,net.minecraft.block.BlockState s){try(var ignored=EcologyHooks.ownWrite()){w.setBlockState(p,s,Block.NOTIFY_ALL);}}
    @SuppressWarnings("unchecked") private static UUID token(ServerPlayerEntity p){try{Field f=ActionConfirmations.class.getDeclaredField("OFFERS");f.setAccessible(true);Map<MinecraftServer,Map<UUID,?>> all=(Map<MinecraftServer,Map<UUID,?>>)f.get(null);Object offer=all.getOrDefault(p.getEntityWorld().getServer(),Map.of()).get(p.getUuid());if(offer==null)return null;Method m=offer.getClass().getDeclaredMethod("token");m.setAccessible(true);return(UUID)m.invoke(offer);}catch(ReflectiveOperationException x){throw new IllegalStateException("cannot inspect guarded trial token",x);}}
    private static void act(ServerPlayerEntity p,UUID t,int a){try{Method m=ActionConfirmations.class.getDeclaredMethod("act",ServerPlayerEntity.class,ActionConfirmationPayloads.Action.class);m.setAccessible(true);m.invoke(null,p,new ActionConfirmationPayloads.Action(t,a));}catch(ReflectiveOperationException x){throw new IllegalStateException("cannot invoke guarded trial token",x);}}
    private static void require(boolean yes,String text){if(!yes)throw new IllegalStateException(text);checks++;} private static String detail(Exception x){return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
}
