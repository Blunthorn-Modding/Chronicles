package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import io.github.chronicle.quietbelow.QuietBelow;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Opt-in real-time driver for the disposable Heart last-stand proof. */
final class EcologyHeartDefianceSmoke {
    private static final String OUTPUT=System.getenv("CHRONICLE_HEART_DEFIANCE_SMOKE"),PHASE=System.getenv("CHRONICLE_HEART_DEFIANCE_PHASE"),ROOT=System.getenv("CHRONICLE_HEART_DEFIANCE_SERVER_DIR"),ADDRESS="127.0.0.1:25575";
    private static final Pattern STATUS=Pattern.compile("Defiance status elapsed=(\\d+) living=(\\d+) complete=(true|false) wardens=(\\d+) receipt=(\\d+) active=(\\d+)");
    private final Path output;private final String phase;private final List<String> messages=new ArrayList<>(),checks=new ArrayList<>(),statuses=new ArrayList<>();private final List<Step> steps=new ArrayList<>();
    private final long overallDeadline=System.nanoTime()+260_000_000_000L;
    private int index,baseline,lastElapsed,lastLiving,lastWardens,lastReceipt;private long deadline,nextStatus;private boolean lastComplete,connected,built,started,finished;

    private EcologyHeartDefianceSmoke(Path output,Path root,String phase)throws IOException{
        if(!List.of("initial","restart").contains(phase))throw new IllegalArgumentException("invalid Heart defiance phase");
        if(!Files.isDirectory(root)||output.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize()))throw new IllegalArgumentException("Heart defiance needs an external report directory and isolated server root");
        this.output=output;this.phase=phase;Files.createDirectories(output);
    }
    static void register(){
        if(OUTPUT==null||PHASE==null)return;
        try{if(ROOT==null)throw new IllegalArgumentException("missing staged server root");var smoke=new EcologyHeartDefianceSmoke(Path.of(OUTPUT),Path.of(ROOT),PHASE);
            ClientReceiveMessageEvents.GAME.register((text,overlay)->{if(!overlay&&!smoke.finished)smoke.messages.add(text.getString());});
            ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
        }catch(Exception error){setupFail(error);MinecraftClient.getInstance().scheduleStop();}
    }
    private void tick(MinecraftClient client){
        if(finished)return;
        try{
            require(System.nanoTime()<overallDeadline,"Heart defiance overall timeout");
            if(!connected&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen){connected=true;ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(ADDRESS),new ServerInfo("Disposable Heart defiance fixture",ADDRESS,ServerInfo.ServerType.OTHER),false,null);return;}
            if(client.player==null||client.world==null)return;
            require(client.getCurrentServerEntry()!=null&&ADDRESS.equals(client.getCurrentServerEntry().address),"Heart defiance address mismatch");
            if(!built){build(client);built=true;}
            if(index>=steps.size()){finish(client,true,"real-time Heart last-stand proof completed");return;}
            Step step=steps.get(index);if(!started){baseline=messages.size();deadline=System.nanoTime()+step.timeoutMillis*1_000_000L;step.start.run();started=true;}
            if(step.done.getAsBoolean()){checks.add(step.name);index++;started=false;}else require(System.nanoTime()<deadline,"timed out: "+step.name);
        }catch(Throwable error){finish(client,false,error.getClass().getSimpleName()+": "+error.getMessage());}
    }
    private void build(MinecraftClient client){
        if("initial".equals(phase)){
            command(client,"prepare 256-cell Heart source","quiet_below_heart_defiance_trial prepare","prepare",40_000);
            waitFor("wait for protected 256-cell topology proof",()->poll(client,0,256,0,true),45_000);
            command(client,"confirm protected Heart source","quiet_below_heart_defiance_trial observe","observe",20_000);
            command(client,"cut only the Heart-neighbour network shell","quiet_below_heart_defiance_trial cut","cut",20_000);
            waitFor("wait for the first cut to settle",()->poll(client,0,0,0,true),45_000);
            command(client,"record first post-cut topology baseline","quiet_below_heart_defiance_trial settle","settle",20_000);
            waitFor("wait for actual last-stand pulse and direct Warden",()->poll(client,1000,1,1,false),90_000);
            command(client,"verify first durable last stand","quiet_below_heart_defiance_trial first","first",20_000);
            screenshot(client,"heart-defiance-first-warden.png");
            command(client,"physically reconnect the original network","quiet_below_heart_defiance_trial reconnect","reconnect",20_000);
            waitFor("wait for fully protected reconnection",()->poll(client,0,256,0,true),45_000);
            command(client,"recut the Heart-neighbour network inside cooldown","quiet_below_heart_defiance_trial second_cut","second_cut",20_000);
            waitFor("wait for the second cut to settle",()->poll(client,0,0,0,true),45_000);
            command(client,"record second post-cut topology baseline","quiet_below_heart_defiance_trial second_settle","second_settle",20_000);
            waitFor("wait through the suppressed second weakness phase",()->poll(client,1000,0,0,false),90_000);
            command(client,"verify cooldown suppressed all second effects","quiet_below_heart_defiance_trial second","second",20_000);
        }else{
            delay("allow twelve actual active seconds after restart",12_000);
            command(client,"verify persisted defiance receipt after restart","quiet_below_heart_defiance_trial restart","restart",25_000);
        }
    }
    private boolean poll(MinecraftClient client,int elapsed,int living,int wardens,boolean complete){
        inspectStatuses();
        if(System.nanoTime()>=nextStatus){send(client,"quiet_below_heart_defiance_trial status");nextStatus=System.nanoTime()+5_000_000_000L;}
        return lastElapsed>=elapsed&&(living==0?lastLiving==0:lastLiving>=living)&&lastWardens>=wardens&&(!complete||lastComplete);
    }
    private void inspectStatuses(){for(String message:messages.subList(baseline,messages.size())){Matcher match=STATUS.matcher(message);if(match.matches()&&!statuses.contains(message)){statuses.add(message);lastElapsed=Integer.parseInt(match.group(1));lastLiving=Integer.parseInt(match.group(2));lastComplete=Boolean.parseBoolean(match.group(3));lastWardens=Integer.parseInt(match.group(4));lastReceipt=Integer.parseInt(match.group(5));}}}
    private void command(MinecraftClient client,String name,String command,String verb,long timeout){step(name,()->send(client,command),()->received(verb),timeout);}
    private void waitFor(String name,BooleanSupplier done,long timeout){step(name,()->{nextStatus=0;lastElapsed=-1;lastLiving=-1;lastWardens=-1;lastReceipt=-1;lastComplete=false;statuses.clear();},done,timeout);}
    private void delay(String name,long millis){step(name,()->deadline=System.nanoTime()+millis*1_000_000L,()->System.nanoTime()>=deadline,millis+5_000);}
    private void screenshot(MinecraftClient client,String file){step("capture "+file,()->ScreenshotRecorder.saveScreenshot(output.toFile(),file,client.getFramebuffer(),1,ignored->{}),()->pngReady(output.resolve("screenshots").resolve(file)),20_000);}
    private void step(String name,Runnable start,BooleanSupplier done,long timeout){steps.add(new Step(name,start,done,timeout));}
    private void send(MinecraftClient client,String command){client.player.networkHandler.sendChatCommand(command);}
    private boolean received(String verb){for(String message:messages.subList(baseline,messages.size())){if(message.regionMatches(true,0,"FAIL Defiance ",0,"FAIL Defiance ".length()))throw new IllegalStateException(message);if(message.toLowerCase(Locale.ROOT).equals("pass defiance "+verb.toLowerCase(Locale.ROOT)))return true;}return false;}
    private static boolean pngReady(Path file){try{if(!Files.isRegularFile(file))return false;byte[] b=Files.readAllBytes(file);int n=b.length;return n>24&&b[n-8]==73&&b[n-7]==69&&b[n-6]==78&&b[n-5]==68;}catch(IOException ignored){return false;}}
    private void finish(MinecraftClient client,boolean pass,String detail){if(finished)return;finished=true;try{Files.writeString(output.resolve("heart-defiance-"+phase+".json"),new Gson().toJson(Map.of("result",pass?"PASS":"FAIL","phase",phase,"detail",detail,"checks",checks,"statuses",statuses,"messages",messages,"lastElapsed",lastElapsed,"lastLiving",lastLiving,"lastWardens",lastWardens,"lastReceipt",lastReceipt)));}catch(IOException ignored){}client.scheduleStop();}
    private static void require(boolean yes,String detail){if(!yes)throw new IllegalStateException(detail);}
    private record Step(String name,Runnable start,BooleanSupplier done,long timeoutMillis){}
    private static void setupFail(Exception error){try{if(OUTPUT!=null){Files.createDirectories(Path.of(OUTPUT));Files.writeString(Path.of(OUTPUT).resolve("heart-defiance-"+String.valueOf(PHASE)+".json"),new Gson().toJson(Map.of("result","FAIL","detail","setup: "+error.getMessage())));}}catch(Exception ignored){}}
}
