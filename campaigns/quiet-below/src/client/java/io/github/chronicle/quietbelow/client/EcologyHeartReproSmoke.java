package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.chronicle.quietbelow.QuietBelow;
import io.github.chronicle.quietbelow.content.RelicHeartBlock;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

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

/** Physical mining reproduction in a disposable clone of the accepted review world. */
final class EcologyHeartReproSmoke {
    private static final String OUTPUT=System.getenv("CHRONICLE_HEART_REPRO_SMOKE"),ROOT=System.getenv("CHRONICLE_HEART_REPRO_SERVER_DIR"),RESUME=System.getenv("CHRONICLE_HEART_REPRO_RESUME"),ADDRESS="127.0.0.1:25575";
    private static final BlockPos HEART=new BlockPos(-4,64,0);
    private static final Pattern ELAPSED=Pattern.compile("Heart weakening: (\\d+) / (\\d+) active seconds below threshold\\.");
    private final Path output; private final List<String> messages=new ArrayList<>(),checks=new ArrayList<>(),samples=new ArrayList<>();private final List<Step> steps=new ArrayList<>();
    private final long overallDeadline=System.nanoTime()+210_000_000_000L;
    private int index,baseline,initialElapsed=-1,maxElapsed=-1,refreshes;private long deadline,nextRefresh,observationStarted;private boolean connected,built,started,finished,mining;

    private EcologyHeartReproSmoke(Path output,Path root)throws IOException{
        if(!Files.isDirectory(root)||output.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize()))throw new IllegalArgumentException("Heart reproduction needs an external report directory and isolated server clone");
        this.output=output;Files.createDirectories(output);
    }
    static void register(){
        if(OUTPUT==null)return;
        try{
            if(ROOT==null)throw new IllegalArgumentException("missing cloned server root");
            var smoke=new EcologyHeartReproSmoke(Path.of(OUTPUT),Path.of(ROOT));
            ClientReceiveMessageEvents.GAME.register((text,overlay)->{if(!overlay&&!smoke.finished)smoke.messages.add(text.getString());});
            ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
        }catch(Exception error){setupFail(error);MinecraftClient.getInstance().scheduleStop();}
    }
    private void tick(MinecraftClient client){
        if(finished)return;
        try{
            require(System.nanoTime()<overallDeadline,"Heart reproduction overall timeout");
            if(!connected&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen){connected=true;ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(ADDRESS),new ServerInfo("Isolated Heart reproduction",ADDRESS,ServerInfo.ServerType.OTHER),false,null);return;}
            if(client.player==null||client.world==null)return;
            require(client.getCurrentServerEntry()!=null&&ADDRESS.equals(client.getCurrentServerEntry().address),"Heart reproduction address mismatch");
            if(mining&&client.world.getBlockState(HEART).isOf(QuietBelow.RELIC_HEART))client.interactionManager.updateBlockBreakingProgress(HEART,Direction.UP);
            if(!built){build(client);built=true;}
            if(index>=steps.size()){finish(client,true,"saved-world Heart delay reproduction completed");return;}
            Step step=steps.get(index);if(!started){baseline=messages.size();deadline=System.nanoTime()+step.timeout*1_000_000L;step.start.run();started=true;}
            if(step.done.getAsBoolean()){checks.add(step.name);index++;started=false;}else require(System.nanoTime()<deadline,"timed out: "+step.name);
        }catch(Throwable error){finish(client,false,error.getClass().getSimpleName()+": "+error.getMessage());}
    }
    private void build(MinecraftClient client){
        command(client,"move to saved Heart observation point","tp @s -3.5 65 3.5 180 25", "teleported");
        command(client,"use Survival for physical mining","gamemode survival @s", "survival");
        step("hold a real iron pickaxe",()->send(client,"give @s minecraft:iron_pickaxe 1"),()->java.util.stream.IntStream.range(0,9).anyMatch(slot->client.player.getInventory().getStack(slot).isIn(net.minecraft.registry.tag.ItemTags.PICKAXES)),20_000);
        if("true".equals(RESUME))command(client,"resume copied paused session","chronicle session resume", "resumed");
        refresh(client,"read initial Heart status");
        waitFor("observe the actual 60-second weakness clock",()->observeClock(client),90_000);
        waitFor("confirm vulnerable brown Heart lens",()->client.world.getBlockState(HEART).isOf(QuietBelow.RELIC_HEART)&&client.world.getBlockState(HEART).get(RelicHeartBlock.LENS)==RelicHeartBlock.Lens.VULNERABLE,15_000);
        screenshot(client,"heart-repro-vulnerable.png");
        step("mine the vulnerable Heart with the held pickaxe",()->{client.player.getInventory().setSelectedSlot(findPickaxe(client));mining=true;client.interactionManager.attackBlock(HEART,Direction.UP);},()->messages.stream().anyMatch(m->m.equals("The Heart gives way. Something vast falls silent.")),30_000);
        step("confirm Heart was removed after its local cue",()->mining=false,()->!client.world.getBlockState(HEART).isOf(QuietBelow.RELIC_HEART),20_000);
    }
    private boolean observeClock(MinecraftClient client){
        inspectStatuses();
        if(latestStatus()!=null&&(latestStatus().startsWith("Heart protected:")||(initialElapsed>=0&&latestStatus().startsWith("Weakness cannot be verified"))))throw new IllegalStateException("saved network was not a continuously verifiable weakening network: "+latestStatus());
        if(messages.stream().anyMatch(m->m.startsWith("Heart vulnerable."))){
            require(System.nanoTime()-observationStarted>=60_000_000_000L,"Heart became vulnerable before the required real-time 60-second observation completed");
            require(refreshes>=6,"Heart became vulnerable before enough independent status observations");
            require(initialElapsed>=0&&maxElapsed>initialElapsed,"Heart clock never advanced before vulnerability");
            return true;
        }
        if(System.nanoTime()>=nextRefresh){send(client,"chronicles setup tool \"quiet_below:heart\" refresh");nextRefresh=System.nanoTime()+10_000_000_000L;}
        return false;
    }
    private void refresh(MinecraftClient client,String name){step(name,()->{if(observationStarted==0)observationStarted=System.nanoTime();send(client,"chronicles setup tool \"quiet_below:heart\" refresh");nextRefresh=System.nanoTime()+10_000_000_000L;},()->{inspectStatuses();return latestStatus()!=null;},15_000);}
    private void inspectStatuses(){
        for(String message:messages){Matcher match=ELAPSED.matcher(message);if(match.matches()){
            int elapsed=Integer.parseInt(match.group(1));if(samples.stream().noneMatch(s->s.equals(message))){samples.add(message);if(initialElapsed<0)initialElapsed=elapsed;maxElapsed=Math.max(maxElapsed,elapsed);refreshes++;}
        }else if(message.startsWith("Heart vulnerable.")&&!samples.contains(message)){samples.add(message);refreshes++;}}
    }
    private String latestStatus(){for(int i=messages.size()-1;i>=0;i--){String message=messages.get(i);if(message.startsWith("Heart ")||message.startsWith("Weakness cannot"))return message;}return null;}
    private int findPickaxe(MinecraftClient client){for(int slot=0;slot<9;slot++)if(client.player.getInventory().getStack(slot).isIn(net.minecraft.registry.tag.ItemTags.PICKAXES))return slot;throw new IllegalStateException("iron pickaxe was not available for physical Heart mining");}
    private void command(MinecraftClient client,String name,String command,String expected){step(name,()->send(client,command),()->received(expected),20_000);}
    private void waitFor(String name,BooleanSupplier done,long timeout){step(name,()->{},done,timeout);}
    private void screenshot(MinecraftClient client,String file){step("capture "+file,()->ScreenshotRecorder.saveScreenshot(output.toFile(),file,client.getFramebuffer(),1,ignored->{}),()->pngReady(output.resolve("screenshots").resolve(file)),20_000);}
    private void step(String name,Runnable start,BooleanSupplier done,long timeout){steps.add(new Step(name,start,done,timeout));}
    private void send(MinecraftClient client,String command){client.player.networkHandler.sendChatCommand(command);}
    private boolean received(String expected){for(String message:messages.subList(baseline,messages.size()))if(message.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT)))return true;return false;}
    private static boolean pngReady(Path path){try{if(!Files.isRegularFile(path))return false;byte[] bytes=Files.readAllBytes(path);int n=bytes.length;return n>24&&bytes[n-8]==73&&bytes[n-7]==69&&bytes[n-6]==78&&bytes[n-5]==68;}catch(IOException ignored){return false;}}
    private void finish(MinecraftClient client,boolean pass,String detail){if(finished)return;finished=true;try{JsonObject result=new JsonObject();result.addProperty("result",pass?"PASS":"FAIL");result.addProperty("detail",detail);result.addProperty("initialElapsed",initialElapsed);result.addProperty("maxElapsed",maxElapsed);result.add("checks",new Gson().toJsonTree(checks));result.add("statuses",new Gson().toJsonTree(samples));result.add("messages",new Gson().toJsonTree(messages));Files.writeString(output.resolve("heart-repro.json"),new Gson().toJson(result));}catch(IOException ignored){}client.scheduleStop();}
    private static void require(boolean condition,String detail){if(!condition)throw new IllegalStateException(detail);}
    private record Step(String name,Runnable start,BooleanSupplier done,long timeout){}
    private static void setupFail(Exception error){try{if(OUTPUT!=null){Path output=Path.of(OUTPUT);Files.createDirectories(output);Files.writeString(output.resolve("heart-repro.json"),new Gson().toJson(Map.of("result","FAIL","detail","setup: "+error.getMessage())));}}catch(Exception ignored){}}
}
