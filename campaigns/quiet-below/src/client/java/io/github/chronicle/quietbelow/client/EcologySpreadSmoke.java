package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.menu.CoreMenuModel.Page;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;

/** Native driver for the deliberately amplified, disposable count-rate comparison. */
final class EcologySpreadSmoke {
    private static final String OUTPUT=System.getenv("CHRONICLE_SPREAD_SMOKE"),PHASE=System.getenv("CHRONICLE_SPREAD_PHASE"),ROOT=System.getenv("CHRONICLE_SPREAD_SERVER_DIR"),ADDRESS="127.0.0.1:25575";
    private static final Pattern PASS=Pattern.compile("^PASS Spread [a-z_]+ \\(.+\\)$",Pattern.CASE_INSENSITIVE);
    private final Path output,measurement; private final List<String> messages=new ArrayList<>(),checks=new ArrayList<>(); private final List<Step> steps=new ArrayList<>();
    private final long overallDeadline=System.nanoTime()+420_000_000_000L;
    private int index,baseline; private long deadline,until; private boolean started,connected,built,finished;

    private EcologySpreadSmoke(Path output,Path root,String phase){
        if(!List.of("initial","restart").contains(phase))throw new IllegalArgumentException("invalid spread smoke phase");
        if(!Files.isDirectory(root)||output.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize()))throw new IllegalArgumentException("spread smoke needs an external report directory and staged server");
        this.output=output;measurement=root.resolve("world").resolve("quiet-below").resolve("spread-measurement.json");
    }
    static void register(){
        if(OUTPUT==null||PHASE==null)return;
        try{
            if(ROOT==null)throw new IllegalArgumentException("missing staged server");
            var smoke=new EcologySpreadSmoke(Path.of(OUTPUT),Path.of(ROOT),PHASE);
            ClientReceiveMessageEvents.GAME.register((text,overlay)->{if(!overlay&&!smoke.finished)smoke.messages.add(text.getString());});
            ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
        }catch(Exception error){setupFail(error);MinecraftClient.getInstance().scheduleStop();}
    }
    private void tick(MinecraftClient client){
        if(finished)return;
        try{
            require(System.nanoTime()<overallDeadline,"spread smoke overall timeout");
            if(!connected&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen){
                connected=true;ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(ADDRESS),new ServerInfo("Disposable spread fixture",ADDRESS,ServerInfo.ServerType.OTHER),false,null);return;
            }
            if(client.player==null||client.world==null||(client.currentScreen!=null&&!(client.currentScreen instanceof CoreMenuScreen)))return;
            require(client.getCurrentServerEntry()!=null&&ADDRESS.equals(client.getCurrentServerEntry().address),"spread fixture address mismatch");
            if(!built){build(client);built=true;}
            if(index>=steps.size()){finish(client,true,"spread rehearsal complete");return;}
            Step step=steps.get(index);
            if(!started){baseline=messages.size();deadline=System.nanoTime()+step.timeoutMillis*1_000_000L;step.start.run();started=true;}
            if(step.done.getAsBoolean()){checks.add(step.name);index++;started=false;}else require(System.nanoTime()<deadline,"timed out: "+step.name);
        }catch(Throwable error){finish(client,false,error.getClass().getSimpleName()+": "+error.getMessage());}
    }
    private void build(MinecraftClient client){
        if("initial".equals(PHASE)){
            command(client,"prepare small and large physical sources","quiet_below_spread_trial prepare","prepare");
            command(client,"start four measured actual-tick phases","quiet_below_spread_trial measure","measure");
            waitFor("wait for recorded measurement matrix",this::measurementPassed,220_000);
            command(client,"verify rate comparison criteria","quiet_below_spread_trial result","result");
            command(client,"label and pause review fixture","quiet_below_spread_trial review","review");
            command(client,"take paused overview position","quiet_below_spread_trial overview","overview");
            delay("settle paused overview camera",1_500);
            screenshot(client,"spread-small-large-bridge.png");
            command(client,"remove temporary overview perch","quiet_below_spread_trial return","return");
            showSpreadFacts(client);
        }else{
            command(client,"verify persisted spread setup after restart","quiet_below_spread_trial restart","restart");
            command(client,"take persisted paused overview position","quiet_below_spread_trial overview","overview");
            delay("settle persisted overview camera",1_500);
            screenshot(client,"spread-small-large-bridge-restart.png");
            command(client,"remove persisted temporary overview perch","quiet_below_spread_trial return","return");
        }
    }
    private void showSpreadFacts(MinecraftClient client){
        step("open generic Chronicle menu",()->send(client,"chronicle"),()->client.currentScreen instanceof CoreMenuScreen,10_000);
        step("open Game setup spread controls",()->require(((CoreMenuScreen)client.currentScreen).triggerForTest("Game setup"),"Game setup unavailable"),()->client.currentScreen instanceof CoreMenuScreen menu&&!menu.pending()&&menu.state().page()==Page.SETUP,10_000);
        step("select Spread mode and read diagnostics",()->((CoreMenuScreen)client.currentScreen).selectForTest("tool:quiet_below:spread"),()->{
            if(!(client.currentScreen instanceof CoreMenuScreen menu)||menu.pending()||menu.state().detail()==null)return false;
            require(menu.state().detail().id().equals("tool:quiet_below:spread"),"Spread mode detail was not selected");
            var facts=menu.state().detail().facts();
            require(facts.stream().anyMatch(f->f.label().equals("Count")&&f.value().contains("Heart-connected")),"Spread count fact missing");
            require(facts.stream().anyMatch(f->f.label().equals("Attempts")&&f.value().contains("since engine load")),"Spread attempts fact missing");
            require(facts.stream().anyMatch(f->f.label().equals("Growth mode")),"Spread growth mode fact missing");
            return true;
        },20_000);
        screenshot(client,"spread-mode-diagnostics.png");
        check("close spread diagnostics",()->client.setScreen(null));
    }
    private boolean measurementPassed(){
        try{
            if(!Files.isRegularFile(measurement))return false;
            JsonObject value=JsonParser.parseString(Files.readString(measurement)).getAsJsonObject();
            require(value.has("result"),"measurement JSON has no result");
            require("PASS".equals(value.get("result").getAsString()),"measurement failed: "+value);
            return true;
        }catch(IllegalStateException error){throw error;}catch(Exception error){throw new IllegalStateException("invalid measurement JSON: "+error.getMessage(),error);}
    }
    private void command(MinecraftClient client,String name,String command,String verb){step(name,()->send(client,command),()->received(verb),40_000);}
    private void waitFor(String name,BooleanSupplier done,long timeout){step(name,()->{},done,timeout);}
    private void delay(String name,long millis){step(name,()->until=System.nanoTime()+millis*1_000_000L,()->System.nanoTime()>=until,millis+5_000);}
    private void screenshot(MinecraftClient client,String name){
        check("clear overlays for "+name,()->{client.inGameHud.getChatHud().clear(false);client.getToastManager().clear();});
        step("render "+name,()->until=System.nanoTime()+400_000_000L,()->System.nanoTime()>=until,5_000);
        step("capture "+name,()->ScreenshotRecorder.saveScreenshot(output.toFile(),name,client.getFramebuffer(),1,ignored->{}),()->pngReady(output.resolve("screenshots").resolve(name)),20_000);
    }
    private void check(String name,Runnable assertion){step(name,assertion,()->true,1_000);}
    private void step(String name,Runnable start,BooleanSupplier done,long timeout){steps.add(new Step(name,start,done,timeout));}
    private void send(MinecraftClient client,String command){client.player.networkHandler.sendChatCommand(command);}
    private boolean received(String verb){
        for(String message:messages.subList(baseline,messages.size())){
            if(message.regionMatches(true,0,"FAIL Spread ",0,"FAIL Spread ".length()))throw new IllegalStateException(message);
            if(PASS.matcher(message).matches()&&message.toLowerCase(java.util.Locale.ROOT).contains(" "+verb.toLowerCase(java.util.Locale.ROOT)+" "))return true;
        }
        return false;
    }
    private static boolean pngReady(Path path){try{if(!Files.isRegularFile(path))return false;byte[] bytes=Files.readAllBytes(path);int length=bytes.length;return length>24&&bytes[length-8]==73&&bytes[length-7]==69&&bytes[length-6]==78&&bytes[length-5]==68;}catch(Exception ignored){return false;}}
    private void finish(MinecraftClient client,boolean pass,String detail){
        if(finished)return;finished=true;
        try{Files.createDirectories(output);Files.writeString(output.resolve("spread-"+PHASE+"-A.json"),new Gson().toJson(Map.of("result",pass?"PASS":"FAIL","phase",PHASE,"detail",detail,"checks",checks,"step",index<steps.size()?steps.get(index).name:"complete","messages",messages)));}catch(Exception ignored){}
        client.scheduleStop();
    }
    private static void require(boolean condition,String detail){if(!condition)throw new IllegalStateException(detail);}
    private record Step(String name,Runnable start,BooleanSupplier done,long timeoutMillis){}
    private static void setupFail(Exception error){try{if(OUTPUT!=null){Path output=Path.of(OUTPUT);Files.createDirectories(output);Files.writeString(output.resolve("spread-"+PHASE+"-A.json"),"{\"result\":\"FAIL\",\"detail\":\"setup: "+error.getMessage().replace("\"","'")+"\"}");}}catch(Exception ignored){}}
}
