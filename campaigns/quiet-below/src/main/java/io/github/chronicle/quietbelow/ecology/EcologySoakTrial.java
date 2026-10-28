package io.github.chronicle.quietbelow.ecology;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.chronicle.quietbelow.QuietBelow;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.MapSiteAssignments;
import io.github.chronicle.core.server.PlayRuntime;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.literal;

/** Deliberately gated telemetry-only harness for the separately authorized disposable long soak. */
public final class EcologySoakTrial {
    private static final String A="ChronicleSoakA", B="ChronicleSoakB";
    private static final int MAX_TICKS=10800*20+12000;
    private static final Map<MinecraftServer,Job> JOBS=new IdentityHashMap<>();
    private static final Config CONFIG=Config.read();
    private static final MemoryMXBean MEMORY=ManagementFactory.getMemoryMXBean();
    private static final List<GarbageCollectorMXBean> GC=ManagementFactory.getGarbageCollectorMXBeans();
    private EcologySoakTrial(){}

    public static void register(){
        if(CONFIG==null)return;
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(literal("quiet_below_long_soak").then(literal("start").executes(c->start(c.getSource())))));
        ServerTickEvents.START_SERVER_TICK.register(s->{Job j=JOBS.get(s);if(j!=null)j.callbackStart=System.nanoTime();});
        // QuietBelowEcology registers first, so this END observer sees its completed work.
        ServerTickEvents.END_SERVER_TICK.register(EcologySoakTrial::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{Job j=JOBS.get(s);if(j!=null&&j.running())finish(j,"INTERRUPTED",null);});
        ServerLifecycleEvents.SERVER_STOPPED.register(JOBS::remove);
    }

    private static int start(ServerCommandSource source){
        try{
            if(JOBS.containsKey(source.getServer()))throw new IllegalStateException("long soak is already running");
            if(!"1".equals(System.getenv("CHRONICLE_ECOLOGY_NATIVE_TRIAL")))throw new IllegalStateException("CHRONICLE_ECOLOGY_NATIVE_TRIAL=1 is required");
            if(QuietBelowEcology.maintaining(source.getServer())){source.sendFeedback(()->Text.literal("SOAK_WAITING: ecology journal maintenance is in progress"),false);return 0;}
            ServerPlayerEntity actor=source.getPlayerOrThrow();
            if(!source.hasPermissionLevel(2)||!actor.getName().getString().equals(A))throw new IllegalStateException("only ChronicleSoakA (operator level 2) may start this soak");
            fixture(source.getServer());
            AuthoringSite site=MapSiteAssignments.resolve(source.getServer(),"quiet_below:source_city").orElseThrow(()->new IllegalStateException("Source city assignment is missing"));
            if(!(site instanceof AuthoringSite.Region city))throw new IllegalStateException("Source city must be a region");
            EcologyEngine engine=QuietBelowEcology.engineForTest(source.getServer()); EcologyEngine.View view=engine.view();
            if(!view.configured()||!view.indexed()||view.connected()<=0||!view.enabled()||view.unresolved())throw new IllegalStateException("ecology must be configured, indexed, resolved, connected and enabled");
            List<ServerPlayerEntity> players=eligible(source.getServer());
            if(players.size()!=2){source.sendFeedback(()->Text.literal("SOAK_WAITING: both active eligible soak clients must be joined"),false);return 0;}
            ServerPlayerEntity anchor=byName(players,A),roamer=byName(players,B);
            if(anchor==null||roamer==null){source.sendFeedback(()->Text.literal("SOAK_WAITING: ChronicleSoakA and ChronicleSoakB must both be active eligible participants"),false);return 0;}
            source.getServer().getTickManager().setTickRate(20F);
            source.getServer().getPlayerManager().setSimulationDistance(8);
            source.getServer().getPlayerManager().setViewDistance(12);
            BlockPos center=new BlockPos((city.min().x()+city.max().x())/2,city.max().y()+3,(city.min().z()+city.max().z())/2);
            ServerWorld anchorWorld=source.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(city.dimension())));
            if(anchorWorld==null)throw new IllegalStateException("Source city dimension is unavailable");
            BlockPos cave=cave(anchorWorld,city,center);
            Job job=new Job(source.getServer(),engine,city,anchor.getUuid(),roamer.getUuid(),center,cave,CONFIG,view.owned(),engine.spreadAttempts());
            protect(anchor);protect(roamer); move(anchor,anchorWorld,cave); move(roamer,anchorWorld,surface(anchorWorld,center));
            job.open(); JOBS.put(source.getServer(),job); write(job,"RUNNING",null);
            source.sendFeedback(()->Text.literal("SOAK_STARTED seconds="+CONFIG.seconds),false);return 1;
        }catch(Exception failure){source.sendError(Text.literal("SOAK_FAIL: "+failure.getMessage()));QuietBelow.LOGGER.error("Long soak start refused",failure);return 0;}
    }

    private static void tick(MinecraftServer server){
        Job j=JOBS.get(server);if(j==null||!j.running())return;
        try{
            long now=System.nanoTime(); long span=j.callbackStart==0?0:Math.max(0,now-j.callbackStart);
            if(j.count>=MAX_TICKS)throw new IllegalStateException("impossible extra tick count exceeds bounded telemetry capacity");
            boolean maintaining=QuietBelowEcology.maintaining(server);j.callback[j.count]=span;j.ecology[j.count]=maintaining?0:Math.max(0,QuietBelowEcology.tickNanosForTest(server));j.count++;
            List<ServerPlayerEntity> people=eligible(server);ServerPlayerEntity a=server.getPlayerManager().getPlayer(j.anchor),b=server.getPlayerManager().getPlayer(j.roamer);
            boolean present=people.size()==2&&a!=null&&b!=null&&people.contains(a)&&people.contains(b);
            if(!present){j.missingTicks++;j.missedTotal++;if(j.missingSince==0)j.missingSince=now;}else {j.missingTicks=0;j.missingSince=0;}
            EcologyEngine engine=j.engine;EcologyEngine.View view=engine.view();
            j.finalOwned=view.owned();j.maxOwned=Math.max(j.maxOwned,view.owned());
            if(view.unresolved()){j.unresolvedTicks++;j.unresolvedTotal++;}else j.unresolvedTicks=0;
            if(engine.failure()!=null){j.failureTicks++;if(j.failureSince==0)j.failureSince=now;}else {j.failureTicks=0;j.failureSince=0;}
            if(j.missingSince!=0&&now-j.missingSince>60_000_000_000L)throw new IllegalStateException("both eligible soak clients were not present for more than 60 seconds");
            if(j.failureSince!=0&&now-j.failureSince>60_000_000_000L)throw new IllegalStateException("ecology engine failure persisted for more than 60 seconds: "+engine.failure());
            if(j.count%100==0){if(a!=null)protect(a);if(b!=null)protect(b);}
            long elapsed=(now-j.started)/1_000_000_000L;
            boolean activeEcology=!maintaining&&QuietBelowEcology.playing(server)&&view.enabled()&&view.connected()>0&&!view.unresolved()&&java.util.Objects.toString(view.hold(),"").isEmpty();
            if(activeEcology)j.activeEcologyTicks++;
            if(!activeEcology&&!j.expectedUnload&&elapsed>j.recoveryUntil)j.unexpectedHeldTicks++;
            if(elapsed/1200>j.phase){j.phase++;j.awayUntil=elapsed+90;j.expectedUnload=true;
                if(a!=null)move(a,world(a),surface(world(a),j.center.add(1024,0,1024)));
                if(b!=null)move(b,world(b),surface(world(b),j.center.add(-1024,0,-1024)));}
            if(j.expectedUnload&&elapsed>=j.awayUntil){j.expectedUnload=false;j.recoveryUntil=elapsed+90;if(a!=null)move(a,world(a),j.cave);if(b!=null)move(b,world(b),surface(world(b),j.center));}
            if(b!=null&&elapsed%30==0&&elapsed!=j.lastRoute){j.lastRoute=elapsed;route(j,b,elapsed);}
            if(elapsed>=j.config.seconds){finish(j,"FINISHED",null);return;}
            if(now-j.lastReport>=10_000_000_000L){report(j,now,view,engine,maintaining);j.lastReport=now;}
        }catch(Exception failure){finish(j,"FAILED",failure);QuietBelow.LOGGER.error("Long soak failed; server remains running",failure);}
    }

    private static void route(Job j,ServerPlayerEntity p,long elapsed){
        ServerWorld w=world(p);BlockPos target;
        if(elapsed<j.awayUntil){target=new BlockPos(j.center.getX()+1024,j.center.getY(),j.center.getZ()+1024);}
        else {int n=(int)((elapsed/30)%4);int r=384;target=switch(n){case 0->j.center;case 1->j.center.add(r,0,0);case 2->j.center.add(0,0,r);default->j.center.add(-r,0,-r);};}
        move(p,w,surface(w,target));
    }
    private static ServerWorld world(ServerPlayerEntity p){return (ServerWorld)p.getEntityWorld();}
    private static BlockPos surface(ServerWorld w,BlockPos p){return new BlockPos(p.getX(),w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ())+4,p.getZ());}
    /** Bounded, loaded-only search for a natural two-block-high cave space; never edits or loads terrain. */
    private static BlockPos cave(ServerWorld w,AuthoringSite.Region city,BlockPos center){int checked=0;for(int radius=0;radius<=32;radius+=4)for(int dx=-radius;dx<=radius;dx+=4)for(int dz=-radius;dz<=radius;dz+=4){int x=center.getX()+dx,z=center.getZ()+dz;if(x<city.min().x()||x>city.max().x()||z<city.min().z()||z>city.max().z()||!w.isChunkLoaded(x>>4,z>>4))continue;for(int y=Math.min(city.max().y()-1,w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z)-1);y>=city.min().y()+1&&checked++<32768;y--){BlockPos p=new BlockPos(x,y,z);if(w.getBlockState(p).isAir()&&w.getBlockState(p.up()).isAir()&&!w.getBlockState(p.down()).isAir())return p;}}throw new IllegalStateException("no loaded natural two-block cave air near Source city center");}
    private static void move(ServerPlayerEntity p,ServerWorld w,BlockPos at){p.teleport(w,at.getX()+.5,at.getY(),at.getZ()+.5,Set.of(),0,0,false);}
    private static void protect(ServerPlayerEntity p){if(p.interactionManager.getGameMode()!=GameMode.SURVIVAL)p.interactionManager.changeGameMode(GameMode.SURVIVAL);if(!p.isInvulnerable())p.setInvulnerable(true);boolean abilities=!p.getAbilities().allowFlying||!p.getAbilities().flying;p.getAbilities().allowFlying=true;p.getAbilities().flying=true;if(abilities)p.sendAbilitiesUpdate();p.getHungerManager().setFoodLevel(20);p.setHealth(p.getMaxHealth());}
    private static List<ServerPlayerEntity> eligible(MinecraftServer s){return PlayRuntime.eligiblePlayers(s).stream().filter(p->p.getName().getString().equals(A)||p.getName().getString().equals(B)).toList();}
    private static ServerPlayerEntity byName(List<ServerPlayerEntity> p,String name){return p.stream().filter(x->x.getName().getString().equals(name)).findFirst().orElse(null);}

    private static void report(Job j,long now,EcologyEngine.View v,EcologyEngine e,boolean maintaining)throws IOException{String row=metrics(j,now,v,e,maintaining);j.csv.write(row);j.csv.newLine();j.csv.flush();j.windowStart=j.count;write(j,"RUNNING",null);}
    private static String metrics(Job j,long now,EcologyEngine.View v,EcologyEngine e,boolean maintaining){int n=j.count,from=j.windowStart;long elapsed=(now-j.started)/1_000_000_000L;long heap=MEMORY.getHeapMemoryUsage().getUsed();j.maxHeap=Math.max(j.maxHeap,heap);long gcCount=0,gcTime=0;for(var g:GC){gcCount+=Math.max(0,g.getCollectionCount());gcTime+=Math.max(0,g.getCollectionTime());}long cpu=ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os?os.getProcessCpuTime():-1;long callbackMean=mean(j.callback,from,n),ecoMean=mean(j.ecology,from,n);return Instant.now()+","+elapsed+","+n+","+fmt((n-from)*1_000_000_000D/Math.max(1,now-j.lastReport))+","+callbackMean+","+quantile(j.callback,from,n,.95)+","+quantile(j.callback,from,n,.99)+","+max(j.callback,from,n)+","+ecoMean+","+quantile(j.ecology,from,n,.99)+","+heap+","+j.maxHeap+","+gcCount+","+gcTime+","+cpu+","+loaded(j.server)+","+entities(j.server)+","+v.owned()+","+v.connected()+","+v.frontier()+","+e.spreadAttempts()+","+v.journalBytes()+","+v.unresolved()+","+java.util.Objects.toString(v.hold(),"").replace(',',';')+","+QuietBelowEcology.playing(j.server)+","+j.server.getPlayerManager().getCurrentPlayerCount()+","+eligible(j.server).size()+","+j.phase+","+j.expectedUnload+","+maintaining;}
    private static int loaded(MinecraftServer s){int x=0;for(ServerWorld w:s.getWorlds())x+=w.getChunkManager().getLoadedChunkCount();return x;}
    private static long entities(MinecraftServer s){long x=0;for(ServerWorld w:s.getWorlds())for(var ignored:w.iterateEntities())x++;return x;}
    private static long mean(long[] a,int from,int n){if(n<=from)return 0;long sum=0;for(int i=from;i<n;i++)sum+=a[i];return sum/(n-from);}
    private static long max(long[] a,int from,int n){long m=0;for(int i=from;i<n;i++)m=Math.max(m,a[i]);return m;}
    private static long quantile(long[] a,int from,int n,double q){if(n<=from)return 0;long[] copy=java.util.Arrays.copyOfRange(a,from,n);java.util.Arrays.sort(copy);return copy[Math.min(copy.length-1,(int)Math.ceil(copy.length*q)-1)];}
    private static String fmt(double d){return String.format(java.util.Locale.ROOT,"%.3f",d);}
    private static void write(Job j,String state,Exception failure)throws IOException{JsonObject x=new JsonObject();EcologyEngine engine=j.engine;EcologyEngine.View v=engine.view();x.addProperty("state",state);x.addProperty("startUTC",j.startedUtc);x.addProperty("endUTCDue",j.endDueUtc);x.addProperty("durationSeconds",j.config.seconds);x.addProperty("elapsedSeconds",(System.nanoTime()-j.started)/1_000_000_000L);x.addProperty("ticks",j.count);x.addProperty("owned",v.owned());x.addProperty("connected",v.connected());x.addProperty("frontier",v.frontier());x.addProperty("spreadAttempts",engine.spreadAttempts());x.addProperty("maintaining",QuietBelowEcology.maintaining(j.server));x.addProperty("activeEcologyTicks",j.activeEcologyTicks);x.addProperty("unexpectedHeldTicks",j.unexpectedHeldTicks);x.addProperty("initialOwned",j.initialOwned);x.addProperty("maxOwned",j.maxOwned);x.addProperty("finalOwned",j.finalOwned);x.addProperty("spreadAttemptsGrowth",engine.spreadAttempts()-j.initialSpreadAttempts);x.addProperty("eligible",eligible(j.server).size());x.addProperty("playing",QuietBelowEcology.playing(j.server));x.addProperty("unresolved",v.unresolved());x.addProperty("hold",java.util.Objects.toString(v.hold(),""));x.addProperty("missedEligibleTicks",j.missedTotal);x.addProperty("unresolvedTicks",j.unresolvedTotal);x.addProperty("expectedPhase",j.expectedUnload?"AWAY_UNLOAD":"NORMAL");x.addProperty("expectedUnload",j.expectedUnload);if(failure!=null)x.addProperty("failure",failure.toString());atomic(j.status,new GsonBuilder().setPrettyPrinting().create().toJson(x));}
    private static void finish(Job j,String state,Exception failure){if(!j.running())return;j.state=state;try{if(j.csv!=null){j.csv.flush();j.csv.close();}JsonObject sum=new JsonObject();sum.addProperty("state",state);sum.addProperty("startUTC",j.startedUtc);sum.addProperty("endUTC",Instant.now().toString());sum.addProperty("elapsedSeconds",(System.nanoTime()-j.started)/1_000_000_000L);sum.addProperty("ticks",j.count);sum.addProperty("activeEcologyTicks",j.activeEcologyTicks);sum.addProperty("unexpectedHeldTicks",j.unexpectedHeldTicks);sum.addProperty("initialOwned",j.initialOwned);sum.addProperty("maxOwned",j.maxOwned);sum.addProperty("finalOwned",j.finalOwned);sum.addProperty("spreadAttemptsGrowth",j.engine.spreadAttempts()-j.initialSpreadAttempts);sum.addProperty("callbackP99Nanos",quantile(j.callback,0,j.count,.99));sum.addProperty("ecologyP99Nanos",quantile(j.ecology,0,j.count,.99));sum.addProperty("heapStartBytes",j.initialHeap);sum.addProperty("maxHeapBytes",j.maxHeap);sum.addProperty("gcCountGrowth",gcCount()-j.initialGcCount);sum.addProperty("gcTimeMillisGrowth",gcTime()-j.initialGcTime);sum.addProperty("missedEligibleTicks",j.missedTotal);sum.addProperty("missedEligibleFraction",j.count==0?0D:(double)j.missedTotal/j.count);sum.addProperty("unresolvedTicks",j.unresolvedTotal);sum.addProperty("unresolvedFraction",j.count==0?0D:(double)j.unresolvedTotal/j.count);if(failure!=null)sum.addProperty("failure",failure.toString());atomic(j.summary,new GsonBuilder().setPrettyPrinting().create().toJson(sum));}catch(Exception e){QuietBelow.LOGGER.error("Cannot finalize long soak telemetry",e);}try{write(j,state,failure);}catch(Exception e){QuietBelow.LOGGER.error("Cannot write final long soak status",e);}}
    private static long gcCount(){long x=0;for(var g:GC)x+=Math.max(0,g.getCollectionCount());return x;}
    private static long gcTime(){long x=0;for(var g:GC)x+=Math.max(0,g.getCollectionTime());return x;}
    private static void atomic(Path target,String text)throws IOException{Path tmp=target.resolveSibling(target.getFileName()+".tmp");Files.writeString(tmp,text,StandardCharsets.UTF_8);try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}}
    private static void fixture(MinecraftServer s)throws IOException{Path run=s.getRunDirectory().toRealPath();Path report=CONFIG.reports.toRealPath();Path workspace=run.getParent()==null?null:run.getParent().getParent();if(!s.isDedicated()||s.isOnlineMode()||s.getServerPort()!=25575||!"127.0.0.1".equals(s.getServerIp())||workspace==null||!run.getParent().getFileName().toString().equals("test-runs")||!run.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")||!Files.isRegularFile(run.resolve(".long-soak-fixture"))||!report.getFileName().toString().startsWith("long-soak-")||!report.getParent().equals(workspace.resolve("reports")))throw new IllegalStateException("unsafe long-soak fixture");}
    private record Config(int seconds,Path reports){static Config read(){try{String value=System.getenv("CHRONICLE_LONG_SOAK_SECONDS"),out=System.getenv("CHRONICLE_LONG_SOAK_REPORTS");if(value==null||out==null)return null;int seconds=Integer.parseInt(value);Path p=Path.of(out);if(seconds<30||seconds>10800||!p.isAbsolute()||!Files.isDirectory(p))return null;return new Config(seconds,p);}catch(Exception e){return null;}}}
    private static final class Job {final MinecraftServer server;final EcologyEngine engine;final java.util.UUID anchor,roamer;final BlockPos center,cave;final Config config;final int initialOwned;final long initialSpreadAttempts;final long[] callback=new long[MAX_TICKS],ecology=new long[MAX_TICKS];final Path status,summary,telemetry;BufferedWriter csv;long started,callbackStart,lastReport,lastRoute,awayUntil,recoveryUntil,missingSince,failureSince,maxHeap,initialHeap,initialGcCount,initialGcTime,missedTotal,unresolvedTotal,activeEcologyTicks,unexpectedHeldTicks;int count,windowStart,missingTicks,unresolvedTicks,failureTicks,phase,maxOwned,finalOwned;boolean expectedUnload;String startedUtc,endDueUtc,state="NEW";Job(MinecraftServer s,EcologyEngine engine,AuthoringSite.Region city,java.util.UUID a,java.util.UUID b,BlockPos c,BlockPos cave,Config cfg,int initialOwned,long initialSpreadAttempts){server=s;this.engine=engine;anchor=a;roamer=b;center=c;this.cave=cave;config=cfg;this.initialOwned=initialOwned;this.initialSpreadAttempts=initialSpreadAttempts;maxOwned=finalOwned=initialOwned;status=cfg.reports.resolve("status.json");summary=cfg.reports.resolve("summary.json");telemetry=cfg.reports.resolve("telemetry.csv");}void open()throws IOException{csv=Files.newBufferedWriter(telemetry,StandardCharsets.UTF_8);csv.write("utc,elapsedSeconds,ticks,observedTPS,workMeanNanos,workP95Nanos,workP99Nanos,workMaxNanos,ecologyMeanNanos,ecologyP99Nanos,heapUsed,heapMax,gcCount,gcTimeMillis,processCpuNanos,loadedChunks,entityCount,owned,connected,frontier,spreadAttempts,journalBytes,unresolved,hold,playing,online,eligible,phase,expectedUnload,maintaining");csv.newLine();started=lastReport=System.nanoTime();startedUtc=Instant.now().toString();endDueUtc=Instant.parse(startedUtc).plusSeconds(config.seconds).toString();initialHeap=maxHeap=MEMORY.getHeapMemoryUsage().getUsed();initialGcCount=gcCount();initialGcTime=gcTime();state="RUNNING";}boolean running(){return state.equals("RUNNING");}}
}
