package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Native player loads/unloads the fixture; cleanup itself uses the real server tick path. */
final class EradicationRecoverySmoke {
    private static final String OUTPUT=System.getenv("CHRONICLE_ERADICATION_RECOVERY_SMOKE");
    private static final String ROOT=System.getenv("CHRONICLE_ERADICATION_RECOVERY_ROOT");
    private static final String PHASE=System.getenv("CHRONICLE_ERADICATION_RECOVERY_PHASE");
    private static final String ADDRESS="127.0.0.1:25575";
    private final Path output;
    private final String phase;
    private final List<String> messages=new ArrayList<>(),checks=new ArrayList<>();
    private final List<Step> steps=new ArrayList<>();
    private JsonObject status=new JsonObject();
    private int index,baseline;
    private long until,nextPoll,deadline;
    private final long overall=System.nanoTime()+300_000_000_000L;
    private boolean connected,built,started,finished;

    private EradicationRecoverySmoke(Path output,Path root,String phase)throws IOException {
        this.output=output.toAbsolutePath().normalize();this.phase=phase;
        require(List.of("initial","recover","final").contains(phase),"Unknown recovery phase");
        root=root.toRealPath();
        require(root.getParent().getFileName().toString().equals("test-runs")&&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z"),"Not a timestamped test runtime");
        require(this.output.equals(root.getParent().getParent().resolve("reports/eradication-recovery-"+root.getFileName())),"Unexpected recovery reports path");
        require(Files.isRegularFile(root.resolve(".eradication-recovery-fixture")),"Missing owned fixture marker");
    }
    static void register() {
        if(OUTPUT==null)return;
        try {
            var smoke=new EradicationRecoverySmoke(Path.of(OUTPUT),Path.of(ROOT),PHASE);
            ClientReceiveMessageEvents.GAME.register((text,overlay)->{
                if(overlay||smoke.finished)return;
                String value=text.getString();smoke.messages.add(value);
                if(value.startsWith("RECOVERY "))smoke.status=JsonParser.parseString(value.substring(9)).getAsJsonObject();
            });
            ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
        } catch(Exception error) {setupFailure(error);MinecraftClient.getInstance().scheduleStop();}
    }
    private void tick(MinecraftClient client) {
        if(finished)return;
        try {
            require(System.nanoTime()<overall,"Overall recovery probe timeout");
            if(!connected&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen) {
                connected=true;ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(ADDRESS),
                        new ServerInfo("Disposable eradication recovery",ADDRESS,ServerInfo.ServerType.OTHER),false,null);return;
            }
            if(client.player==null||client.world==null||client.player.age<40)return;
            require(client.getCurrentServerEntry()!=null&&ADDRESS.equals(client.getCurrentServerEntry().address),"Wrong recovery endpoint");
            require(!Files.exists(output.resolve("trial-failure.json")),"Server recovery trial failed: "+failure());
            if(!built){build(client);built=true;}
            if(index==steps.size()){finish(client,true,"Native recovery phase completed");return;}
            Step step=steps.get(index);
            if(!started){baseline=messages.size();deadline=System.nanoTime()+step.timeout*1_000_000L;step.start.run();started=true;}
            if(step.done.getAsBoolean()){checks.add(step.name);index++;started=false;}
            else require(System.nanoTime()<deadline,"Timed out: "+step.name+"; status="+status);
        } catch(Throwable error) {finish(client,false,error.getClass().getSimpleName()+": "+error.getMessage());}
    }
    private void build(MinecraftClient client) {
        if(phase.equals("initial")) {
            step("move native player over the source chunks",()->send(client,"tp @s 1032.5 82 1032.5"),
                    ()->Math.abs(client.player.getY()-82)<1,15_000);
            delay("allow nearby chunks to load through the player",3000);
            command(client,"prepare dense source and durable terminal intent","prepare");
            waitFor("observe partial cleanup before saving",()->poll(client)&&mode("CHECKPOINT"),30_000);
            command(client,"save partial receipts with completed and pending foreign edits","checkpoint");
            command(client,"resume cleanup beyond its saved checkpoint","resume_to_crash");
            waitFor("reach partial unsaved crash checkpoint",()->poll(client)&&mode("CRASH_READY")&&Files.exists(output.resolve("crash-ready.json")),30_000);
        } else if(phase.equals("recover")) {
            waitFor("reload saved partial chunk receipts after forced termination",()->poll(client)&&number("completed")>0&&number("completed")<number("total"),30_000);
            command(client,"verify terminal state and receipt terrain agreement after crash","recovered");
            command(client,"leave the cleanup area during active play","unload");
            waitFor("prove natural unload holds unfinished cleanup",()->poll(client)&&mode("UNLOADED")&&number("loadedPlans")==0,90_000);
            command(client,"return to naturally reload the unfinished area","return");
            waitFor("finish cleanup and preserve a later completed target edit",()->poll(client)&&mode("COMPLETE")&&Files.exists(output.resolve("completed.json")),150_000);
        } else {
            waitFor("reload all completed receipts on the final restart",()->poll(client)&&number("total")>=4098&&number("completed")==number("total"),30_000);
            command(client,"verify every restored target and later edit after final restart","final");
        }
    }
    private boolean poll(MinecraftClient client) {
        if(System.nanoTime()>=nextPoll){send(client,"quiet_below_recovery_trial status");nextPoll=System.nanoTime()+500_000_000L;}
        return status.has("mode");
    }
    private boolean mode(String value){return status.has("mode")&&status.get("mode").getAsString().equals(value);}
    private int number(String name){return status.has(name)?status.get(name).getAsInt():0;}
    private void command(MinecraftClient client,String name,String verb) {
        step(name,()->send(client,"quiet_below_recovery_trial "+verb),()->{
            for(String value:messages.subList(baseline,messages.size())) {
                require(!value.startsWith("FAIL Recovery"),value);
                if(value.equals("PASS Recovery "+verb))return true;
            }
            return false;
        },30_000);
    }
    private void waitFor(String name,BooleanSupplier condition,long timeout){step(name,()->{status=new JsonObject();nextPoll=0;},condition,timeout);}
    private void delay(String name,long millis){step(name,()->until=System.nanoTime()+millis*1_000_000L,()->System.nanoTime()>=until,millis+5000);}
    private void step(String name,Runnable start,BooleanSupplier condition,long timeout){steps.add(new Step(name,start,condition,timeout));}
    private void send(MinecraftClient client,String command){client.player.networkHandler.sendChatCommand(command);}
    private String failure(){try{return Files.readString(output.resolve("trial-failure.json"));}catch(IOException ignored){return "No additional detail";}}
    private void finish(MinecraftClient client,boolean pass,String detail) {
        if(finished)return;finished=true;
        try{Files.writeString(output.resolve("client-"+phase+".json"),new Gson().toJson(Map.of("result",pass?"PASS":"FAIL","phase",phase,"detail",detail,"checks",checks,"messages",messages)));}
        catch(IOException ignored){ }
        client.scheduleStop();
    }
    private static void setupFailure(Exception error) {
        try{if(OUTPUT!=null)Files.writeString(Path.of(OUTPUT).resolve("client-"+PHASE+".json"),new Gson().toJson(Map.of("result","FAIL","detail","setup: "+error.getMessage())));}
        catch(IOException ignored){ }
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private record Step(String name,Runnable start,BooleanSupplier done,long timeout) { }
}
