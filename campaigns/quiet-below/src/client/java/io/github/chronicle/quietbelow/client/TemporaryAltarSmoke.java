package io.github.chronicle.quietbelow.client;

import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/** Guarded multi-process client for the disposable temporary-Altar native fixture. */
public final class TemporaryAltarSmoke {
    private static final String OUTPUT=System.getenv("CHRONICLE_TEMPORARY_ALTAR_SMOKE"),PHASE=System.getenv("CHRONICLE_TEMPORARY_ALTAR_PHASE"),ROLE=System.getenv("CHRONICLE_TEMPORARY_ALTAR_ROLE");
    private static final Pattern PASS=Pattern.compile("^Temporary Altar trial PASS ([a-z_]+) \\(\\d+ checks\\)$",Pattern.CASE_INSENSITIVE);
    private final Path output,result,ready,reconnect;private final String phase,role;private final List<String> messages=new ArrayList<>(),checks=new ArrayList<>();private final List<Step> steps=new ArrayList<>();private final long deadline=System.nanoTime()+300_000_000_000L;private int index,baseline;private long stepDeadline,until;private boolean connected,built,started,finished;private UUID uiToken;
    private TemporaryAltarSmoke(Path output,String phase,String role)throws Exception{this.output=output.toAbsolutePath().normalize();this.phase=phase;this.role=role;if(!List.of("initial","reconnect","restart","ui").contains(phase)||!List.of("A","B").contains(role))throw new IllegalArgumentException("invalid temporary Altar smoke phase/role");Files.createDirectories(this.output);result=this.output.resolve("temporary-altar-"+phase+"-"+role+".json");ready=this.output.resolve("temporary-altar-B-ready");reconnect=this.output.resolve("temporary-altar-reconnect-done");}
    public static void register(){if(OUTPUT==null||PHASE==null||ROLE==null)return;try{var smoke=new TemporaryAltarSmoke(Path.of(OUTPUT),PHASE,ROLE);ClientReceiveMessageEvents.GAME.register((text,overlay)->{if(!overlay&&!smoke.finished)smoke.messages.add(text.getString());});ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);}catch(Exception error){throw new IllegalStateException("temporary Altar smoke setup failed",error);}}
    private void tick(MinecraftClient client){if(finished)return;try{require(System.nanoTime()<deadline,"overall timeout");if(client.player==null&&client.currentScreen instanceof AccessibilityOnboardingScreen)throw new IllegalStateException("unexpected onboarding");if(!connected&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen){connected=true;ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse("127.0.0.1:25575"),new ServerInfo("Temporary Altar fixture","127.0.0.1:25575",ServerInfo.ServerType.OTHER),false,null);return;}if(client.player==null)return;if(!built){build(client);built=true;}if(index>=steps.size()){finish(client,true,"temporary Altar native smoke complete");return;}Step step=steps.get(index);if(!started){baseline=messages.size();stepDeadline=System.nanoTime()+step.timeoutMs*1_000_000L;step.start.run();started=true;}if(step.done.getAsBoolean()){checks.add(step.name);index++;started=false;}else require(System.nanoTime()<stepDeadline,"timed out: "+step.name);}catch(Throwable error){finish(client,false,error.getClass().getSimpleName()+": "+error.getMessage());}}
    private void build(MinecraftClient client){if(role.equals("B")){if(phase.equals("initial")){step("announce passive participant",()->marker(ready),()->true,2_000);step("remain online while owner reconnects",()->{},()->Files.isRegularFile(reconnect),180_000);}return;}if(phase.equals("ui")){buildUi(client);return;}if(phase.equals("initial")){step("wait for passive participant",()->{},()->Files.isRegularFile(ready),60_000);command("prepare exact temporary costs","prepare");command("purchase all four temporary prayers","purchase");command("foreign Night Vision remains authoritative","foreign_check");delay("foreign Night Vision duration",100);command("owned Night Vision resumes","owned_check");command("real death drops paid/repaired state once","death");step("respawn after native death",()->{},()->{if(client.currentScreen instanceof DeathScreen){client.player.requestRespawn();client.setScreen(null);return false;}return client.currentScreen==null&&client.player.isAlive();},30_000);command("death preserves ongoing prices without duplicating drops","respawn_check");}
        else if(phase.equals("reconnect")){command("same-server reconnect preserves authority","reconnect_check");step("release passive participant",()->marker(reconnect),()->true,2_000);}
        else{command("server downtime preserves remaining active-clock duration","restart_check");command("pause shared active clock","pause");delay("paused wall time",80);command("paused time spends no duration","paused_check");command("resume shared active clock","resume");delay("spend configured sixty active seconds",1300);command("active-clock expiry removes only ongoing effects","expired_check");command("review exact after-claim uncertainty without reward","uncertainty");command("source ending retains one-shot prices","ending");}}
    private void buildUi(MinecraftClient client){
        commandForge("configure real living network","prepare");delay("allow bounded living-network reconciliation",120);commandForge("verify powered station before Altar replacement","ready");
        command("prepare fresh character with no experience","ui_prepare");
        step("real Altar right click opens five prayers",()->client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(2.5,66,3.5),Direction.UP,new BlockPos(2,65,3),false)),()->client.currentScreen instanceof io.github.chronicle.core.client.OfferingScreen,15_000);
        step("compact native popup",()->{client.options.getGuiScale().setValue(2);client.getWindow().setWindowedSize(640,480);},()->client.getWindow().getFramebufferWidth()==640&&client.getWindow().getFramebufferHeight()==480,10_000);
        step("all five prayer buttons are visible and clickable on the same page",()->{},()->{
            var screen=(io.github.chronicle.core.client.OfferingScreen)client.currentScreen;
            return screen.state().choices().size()==5&&screen.children().stream().filter(v->v instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.visible&&b.active&&b.getMessage().getString().startsWith("Pray for ")).count()==5;
        },5_000);
        delay("render compact five-prayer popup",4);screenshot("capture compact five-prayer popup","temporary-altar-five-compact.png");
        step("insufficient-XP prayer sends authoritative C2S",()->{var screen=(io.github.chronicle.core.client.OfferingScreen)client.currentScreen;uiToken=screen.state().token();require(screen.triggerForTest("choice3"),"Clear Sight refusal button unavailable");},()->client.currentScreen instanceof io.github.chronicle.core.client.OfferingScreen screen&&!screen.pending()&&!screen.state().feedback().isEmpty(),15_000);
        screenshot("capture compact cryptic refusal","temporary-altar-refused-compact.png");
        step("stale failed token cannot repeat damage",()->net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new io.github.chronicle.core.network.OfferingPayloads.Action(uiToken,1,3)),()->true,1_000);
        delay("allow duplicate refusal packet",5);command("server verifies ordinary damage and no trade then supplies XP","ui_refused_check");
        command("reopen five-prayer popup for funded Clear Sight","ui_open");
        step("wide native popup",()->client.getWindow().setWindowedSize(1280,720),()->client.getWindow().getFramebufferWidth()==1280&&client.getWindow().getFramebufferHeight()==720,10_000);
        delay("render wide five-prayer popup",4);screenshot("capture wide five-prayer popup","temporary-altar-five-wide.png");
        step("normal Clear Sight button succeeds",()->{var screen=(io.github.chronicle.core.client.OfferingScreen)client.currentScreen;uiToken=screen.state().token();require(screen.triggerForTest("choice3"),"Clear Sight button unavailable");},()->client.currentScreen==null,15_000);
        step("stale successful token cannot purchase twice",()->net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new io.github.chronicle.core.network.OfferingPayloads.Action(uiToken,1,3)),()->true,1_000);
        delay("allow stale successful packet",5);command("server verifies exact choice route and one-use token","ui_check");
        command("open another physical Altar during shared cooldown","ui_cooldown_open");
        step("temporary success leaves lasting refusal clickable",()->{},()->client.currentScreen instanceof io.github.chronicle.core.client.OfferingScreen screen&&screen.state().choices().getFirst().available(),10_000);
        step("shared cooldown prayer sends ordinary refusal",()->{var screen=(io.github.chronicle.core.client.OfferingScreen)client.currentScreen;uiToken=screen.state().token();require(screen.triggerForTest("choice0"),"cooldown prayer button unavailable");},()->client.currentScreen instanceof io.github.chronicle.core.client.OfferingScreen screen&&!screen.pending()&&!screen.state().feedback().isEmpty(),15_000);
        step("duplicate cooldown packet cannot repeat damage",()->net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new io.github.chronicle.core.network.OfferingPayloads.Action(uiToken,1,0)),()->true,1_000);
        delay("allow cooldown replay",5);command("server verifies shared cross-ledger cooldown and retained evidence","ui_cooldown_check");
        delay("occupied active cooldown expires before another successful prayer",400);
        command("reopen after the shared cooldown","ui_expiry_open");
        step("lasting prayer succeeds after a temporary prayer cooldown",()->{var screen=(io.github.chronicle.core.client.OfferingScreen)client.currentScreen;require(screen.triggerForTest("choice1"),"post-cooldown prayer unavailable");},()->client.currentScreen==null,15_000);
        command("server verifies repeated use and a fresh atomic cooldown","ui_expiry_check");
        command("technical paused Creative absent stale and missing Root attempts never damage","ui_safety");
    }
    private void command(String name,String verb){step(name,()->client().player.networkHandler.sendChatCommand("chronicle_temporary_altar_trial "+verb),()->received(verb),30_000);}
    private void commandForge(String name,String verb){step(name,()->client().player.networkHandler.sendChatCommand("quiet_below_forge_trial "+verb),()->messages.subList(baseline,messages.size()).stream().anyMatch(message->message.startsWith("PASS Forge "+verb+" ")),45_000);}
    private boolean received(String verb){for(int i=baseline;i<messages.size();i++){var match=PASS.matcher(messages.get(i));if(match.matches()&&match.group(1).equalsIgnoreCase(verb))return true;if(messages.get(i).startsWith("Temporary Altar trial FAIL "))throw new IllegalStateException(messages.get(i));}return false;}
    private void delay(String name,int ticks){step(name,()->until=System.nanoTime()+ticks*50_000_000L,()->System.nanoTime()>=until,ticks*60L+5_000);}
    private void screenshot(String name,String filename){boolean[] requested={false};step(name,()->{client().inGameHud.getChatHud().clear(false);client().getToastManager().clear();until=System.nanoTime()+200_000_000L;},()->{if(System.nanoTime()<until)return false;if(!requested[0]){ScreenshotRecorder.saveScreenshot(output.toFile(),filename,client().getFramebuffer(),1,ignored->{});requested[0]=true;}return validPng(output.resolve("screenshots").resolve(filename));},20_000);}
    private static boolean validPng(Path path){try(FileChannel channel=FileChannel.open(path,StandardOpenOption.READ)){if(channel.size()<32)return false;ByteBuffer header=ByteBuffer.allocate(8),trailer=ByteBuffer.allocate(12);while(header.hasRemaining())if(channel.read(header)<0)return false;channel.position(channel.size()-12);while(trailer.hasRemaining())if(channel.read(trailer)<0)return false;byte[] h=header.array(),t=trailer.array();return(h[0]&255)==0x89&&h[1]==0x50&&h[2]==0x4e&&h[3]==0x47&&t[4]==0x49&&t[5]==0x45&&t[6]==0x4e&&t[7]==0x44;}catch(IOException error){return false;}}
    private void step(String name,Runnable start,BooleanSupplier done,long timeout){steps.add(new Step(name,start,done,timeout));}
    private void marker(Path path){try{Files.writeString(path,"ready\n",StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);}catch(Exception error){throw new IllegalStateException(error);}}
    private void finish(MinecraftClient client,boolean pass,String detail){if(finished)return;finished=true;try{Map<String,Object> report=new LinkedHashMap<>();report.put("result",pass?"PASS":"FAIL");report.put("phase",phase);report.put("role",role);report.put("detail",detail);report.put("checks",checks);Files.writeString(result,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");}catch(Exception ignored){}client.scheduleStop();}
    private static MinecraftClient client(){return MinecraftClient.getInstance();}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private record Step(String name,Runnable start,BooleanSupplier done,long timeoutMs){}
}
