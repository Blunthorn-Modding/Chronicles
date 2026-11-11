package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicitly guarded native shutdown regression; the original captured world is never opened. */
public final class CapturedShutdownTrial {
    private static final Map<MinecraftServer,Probe> PROBES=new IdentityHashMap<>();
    private static final int CAPTURED_CELLS=95927;
    private CapturedShutdownTrial(){}
    public static void register(){
        if(System.getenv("CHRONICLE_CAPTURED_SHUTDOWN_REPORTS")==null)return;
        ServerLifecycleEvents.SERVER_STARTED.register(CapturedShutdownTrial::started);
        ServerTickEvents.END_SERVER_TICK.register(CapturedShutdownTrial::tick);
        // Registered after the production stop callback: this proof requires its drain to finish.
        ServerLifecycleEvents.SERVER_STOPPING.register(CapturedShutdownTrial::stopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(PROBES::remove);
    }
    private static void started(MinecraftServer server){
        try{
            EcologyAutoMaintenanceTrial.requireFixture(server);
            Path run=server.getRunDirectory().toRealPath();Path reports=Path.of(System.getenv("CHRONICLE_CAPTURED_SHUTDOWN_REPORTS")).toRealPath();
            if(!Files.isRegularFile(run.resolve(".captured-shutdown-fixture"))
                    ||!reports.getParent().equals(run.getParent().getParent().resolve("reports"))
                    ||!reports.getFileName().toString().startsWith("maintenance-shutdown-"))
                throw new IllegalStateException("Captured shutdown fixture/report guard failed");
            var engine=QuietBelowEcology.engineForTest(server);
            require(engine.view().indexed()&&engine.view().owned()==CAPTURED_CELLS&&engine.failure()==null,"Captured journal did not replay completely");
            boolean restart="restart".equals(System.getenv("CHRONICLE_CAPTURED_SHUTDOWN_PHASE"));
            Probe probe=new Probe(reports,engine,restart);PROBES.put(server,probe);
            if(restart){
                require(!QuietBelowEcology.maintenanceForTest(server).busy(),"Restart inherited a pending worker");
                write(reports.resolve("restart.json"),result("PASS",engine.view().owned(),"Fresh runtime replayed the captured state"));
                return;
            }
            QuietBelowEcology.maintenancePolicyForTest(server,EcologyMaintenancePolicy.DEFAULT,()->{
                probe.entered.countDown();
                try{if(!probe.release.await(120,TimeUnit.SECONDS))throw new IllegalStateException("Captured shutdown release timed out");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            });
            // Worker probe is latch-only; this independent watcher performs no Minecraft calls.
            Thread watcher=new Thread(()->{
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
                while(System.nanoTime()<end){
                    if(Files.isRegularFile(reports.resolve("release.request"))){probe.released.set(true);probe.release.countDown();return;}
                    try{Thread.sleep(25);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
                }
            },"captured-shutdown-release-watcher");
            watcher.setDaemon(true);watcher.start();
        }catch(Exception e){throw new IllegalStateException("Captured shutdown setup failed",e);}
    }
    private static void tick(MinecraftServer server){
        var probe=PROBES.get(server);if(probe==null||probe.restart||probe.ready)return;
        if(probe.entered.getCount()==0&&QuietBelowEcology.maintenanceForTest(server).busy()){
            probe.ready=true;
            try{write(probe.reports.resolve("ready.json"),result("PASS",probe.engine.view().owned(),"Real maintenance entered and held before stop"));}
            catch(Exception e){throw new IllegalStateException(e);}
        }
    }
    private static void stopping(MinecraftServer server){
        var probe=PROBES.get(server);if(probe==null)return;
        try{
            var status=QuietBelowEcology.maintenanceForTest(server);
            require(!status.busy()&&!status.failed(),"Production stop returned before maintenance was safely drained");
            var engine=QuietBelowEcology.engineForTest(server);
            require(engine==probe.engine&&engine.view().owned()==CAPTURED_CELLS,"Drain changed engine identity or recorded cells");
            if(!probe.restart)require(probe.ready&&probe.released.get()&&status.completed()>=1,"Captured drain proof was not exercised");
            var result=result("PASS",engine.view().owned(),"Production maintenance drained before final vanilla save");
            result.addProperty("completedMaintenance",status.completed());
            result.addProperty("beforeBytes",status.beforeBytes());result.addProperty("afterBytes",status.afterBytes());
            write(probe.reports.resolve(probe.restart?"restart-drained.json":"drained.json"),result);
        }catch(Exception e){
            try{write(probe.reports.resolve("trial-failure.json"),result("FAIL",0,e.toString()));}catch(Exception ignored){}
            throw new IllegalStateException("Captured shutdown drain verification failed",e);
        }
    }
    private static JsonObject result(String outcome,int cells,String detail){var v=new JsonObject();v.addProperty("result",outcome);v.addProperty("cells",cells);v.addProperty("detail",detail);return v;}
    private static void write(Path path,JsonObject value)throws Exception{Path tmp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(tmp,value.toString()+"\n",StandardCharsets.UTF_8);Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static final class Probe{
        final Path reports;final EcologyEngine engine;final boolean restart;
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        final AtomicBoolean released=new AtomicBoolean();boolean ready;
        Probe(Path p,EcologyEngine e,boolean r){reports=p;engine=e;restart=r;}
    }
}
