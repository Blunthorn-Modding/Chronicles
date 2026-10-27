package io.github.chronicle.core.client.testing;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.server.testing.EventActionsTrial;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Real-player driver for the disposable CF22 Event action/recovery fixture. */
public final class EventActionsSmoke {
    private static final String OUTPUT=System.getenv("CHRONICLE_EVENTS_SMOKE"),ROOT=System.getenv("CHRONICLE_EVENTS_SERVER_DIR"),PHASE=System.getenv("CHRONICLE_EVENTS_PHASE"),ROLE=System.getenv("CHRONICLE_EVENTS_ROLE"),ADDRESS="127.0.0.1:25575";
    private static EventActionsSmoke instance;
    private final Path output,root;private final List<String> messages=new ArrayList<>(),checks=new ArrayList<>();private final long began=System.nanoTime();private boolean connecting,done,armed;private int step,wait;
    private EventActionsSmoke(){output=Path.of(OUTPUT).toAbsolutePath().normalize();root=Path.of(ROOT).toAbsolutePath().normalize();if(!Files.isRegularFile(root.resolve(".event-actions-fixture"))||output.startsWith(root))throw new IllegalArgumentException("refusing Events smoke outside disposable fixture");if(!List.of("basic","crash","recovery","final","media_seed","uncertain","max").contains(PHASE))throw new IllegalArgumentException("unknown Events smoke phase");}
    public static void register(){if(OUTPUT==null)return;try{instance=new EventActionsSmoke();ClientReceiveMessageEvents.GAME.register((message,overlay)->{if(!overlay)instance.messages.add(message.getString());});ClientTickEvents.END_CLIENT_TICK.register(instance::tick);}catch(Exception failure){ChronicleCore.LOGGER.error("Events smoke setup failed",failure);}}
    private void tick(MinecraftClient client){if(done)return;try{
        long timeout=PHASE.equals("max")?900_000_000_000L:300_000_000_000L;if(System.nanoTime()-began>timeout)throw new IllegalStateException("Events smoke timeout at step "+step);
        if(client.player==null&&client.getOverlay()==null&&client.currentScreen instanceof AccessibilityOnboardingScreen)client.setScreen(new TitleScreen());
        if(!connecting&&client.player==null&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen){connecting=true;ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(ADDRESS),new ServerInfo("Events smoke",ADDRESS,ServerInfo.ServerType.OTHER),false,null);return;}
        if(client.player==null)return;client.getToastManager().clear();switch(PHASE){case "basic"->basic(client);case "crash"->crash(client);case "recovery"->recovery(client);case "final"->finalRestart(client);case "media_seed"->mediaSeed(client);case "uncertain"->uncertain(client);case "max"->maximum(client);default->throw new IllegalStateException();}
    }catch(Throwable failure){finish(client,false,failure.getClass().getSimpleName()+": "+failure.getMessage());}}

    private void basic(MinecraftClient client){
        if("B".equals(ROLE)){if(step==0){wait=650;step++;return;}if(step==1){if(wait-->0)return;command(client,"nonop_guard");step++;return;}if(step==2){if(!passed("nonop_guard"))return;checks.add("ordinary player could not preview or run operator Event media");finish(client,true,"ordinary Event authority guard passed");}return;}
        if(step==0){command(client,"prepare_ui");step++;return;}
        if(step==1){if(!passed("prepare_ui"))return;checks.add("three loaded-only saved Region fixtures prepared, including paired Start/End states");command(client,"configure");step++;return;}
        if(step==2){if(!passed("configure"))return;command(client,"phase_guard");step++;return;}
        if(step==3){if(!passed("phase_guard"))return;checks.add("paused session refused live Event review");command(client,"activate");step++;return;}
        if(step==4){if(!passed("activate"))return;command(client,"flag_guard");step++;return;}
        if(step==5){if(!passed("flag_guard"))return;checks.add("false prerequisite refused Event review");command(client,"enable_flag");step++;return;}
        if(step==6){if(!passed("enable_flag"))return;command(client,"run_media");step++;return;}
        if(step==7){if(!passed("run_media")||!seen(EventActionsTrial.MEDIA_MESSAGE))return;checks.add("manual message and registered sound Event completed through the unified run gate");command(client,"target_world");wait=100;step++;return;}
        if(step==8){if(!passed("target_world")||wait-->0)return;command(client,"invalid_second");step++;return;}
        if(step==9){if(!passed("invalid_second"))return;checks.add("stale second target refused with zero first-Region writes and no batch authority");command(client,"missing_target");step++;return;}
        if(step==10){if(!passed("missing_target"))return;require(count(EventActionsTrial.WORLD_MESSAGE)==0,"a refused invalid Event emitted media");checks.add("missing later target refused before preparation with zero Event media");command(client,"player_refusal");step++;return;}
        if(step==11){if(!passed("player_refusal"))return;checks.add("player inside a target refused the complete batch");command(client,"hold_refusal");step++;return;}
        if(step==12){if(!passed("hold_refusal"))return;checks.add("unrelated runtime hold refused Event preflight");wait=450;step++;return;}
        if(step==13){if(wait-->0)return;command(client,"unloaded_refusal");step++;return;}
        if(step==14){if(!passed("unloaded_refusal"))return;checks.add("unloaded target refused without forcing its chunk");command(client,"run_world");step++;return;}
        if(step==15){if(!passed("run_world")||!seen(EventActionsTrial.WORLD_MESSAGE))return;command(client,"verify_world");step++;return;}
        if(step==16){if(!passed("verify_world"))return;checks.add("two non-overlapping Regions committed exact terrain, NBT, chest binding, managed block and lore effects before media");step++;return;}
        if(step==17){if(!seen(EventActionsTrial.AUTO_MESSAGE))return;command(client,"verify_auto");step++;return;}
        if(step==18){if(!passed("verify_auto"))return;checks.add("automatic timer Event completed through the same durable execution/media gate");finish(client,true,"Event action and refusal checks passed");}
    }

    private void crash(MinecraftClient client){if(step==0){command(client,"run_crash");step++;return;}if(step==1){if(!passed("run_crash"))return;writeArmed();armed=true;step++;return;}if(armed)return;}
    private void recovery(MinecraftClient client){if(step==0){if(!seen(EventActionsTrial.CRASH_MESSAGE))return;wait=20;step++;return;}if(step==1){if(wait-->0)return;command(client,"verify_recovery");step++;return;}if(step==2){if(!passed("verify_recovery"))return;require(count(EventActionsTrial.CRASH_MESSAGE)==1,"recovered world Event media was not delivered exactly once to this client");checks.add("abrupt first-child checkpoint restart recovered exact second target and emitted one post-world media delivery");finish(client,true,"Event recovery passed");}}
    private void finalRestart(MinecraftClient client){if(step==0){wait=120;step++;return;}if(step==1){if(wait-->0)return;require(count(EventActionsTrial.CRASH_MESSAGE)==0,"completed Event media replayed after a second restart");command(client,"verify_final");step++;return;}if(step==2){if(!passed("verify_final"))return;checks.add("completed Event world and media receipts survived another restart without replay");finish(client,true,"final Event restart passed");}}
    private void mediaSeed(MinecraftClient client){if(step==0){wait=40;step++;return;}if(step==1){if(wait-->0)return;command(client,"seed_media_claimed_and_halt");step++;}}
    private void uncertain(MinecraftClient client){if(step==0){wait=80;step++;return;}if(step==1){if(wait-->0)return;require(count(EventActionsTrial.UNCERTAIN_MESSAGE)==0,"uncertain media replayed automatically");command(client,"verify_uncertain");step++;return;}if(step==2){if(!passed("verify_uncertain"))return;checks.add("restart converted durable media claim to explicit uncertainty; GM acknowledgement completed it without replay");finish(client,true,"media uncertainty passed");}}
    private void maximum(MinecraftClient client){if(step==0){command(client,"prepare_max");step++;return;}if(step==1){if(!passed("prepare_max"))return;checks.add("two disjoint 131072-cell saved Regions covered exactly 64 loaded chunks");command(client,"max_preflight");step++;return;}if(step==2){if(!passed("max_preflight"))return;checks.add("maximum 262144-cell/64-chunk combined before-plus-after preflight completed without forcing chunks or writing terrain");finish(client,true,"maximum Event preflight passed");}}

    private void command(MinecraftClient client,String action){client.player.networkHandler.sendChatCommand("chronicle_events_trial "+action);}
    private boolean passed(String action){for(String message:messages){if(message.contains("Events trial FAIL "+action))throw new IllegalStateException(message);if(message.contains("Events trial PASS "+action))return true;}return false;}
    private boolean seen(String value){return messages.stream().anyMatch(message->message.contains(value));}
    private int count(String value){return(int)messages.stream().filter(message->message.contains(value)).count();}
    private void writeArmed(){try{Files.createDirectories(output.getParent());Files.writeString(output,"{\"result\":\"ARMED\"}\n");}catch(Exception failure){throw new IllegalStateException(failure);}}
    private void finish(MinecraftClient client,boolean pass,String detail){if(done)return;done=true;try{Files.createDirectories(output.getParent());JsonObject value=new JsonObject();value.addProperty("result",pass?"PASS":"FAIL");value.addProperty("phase",PHASE);value.addProperty("detail",detail);JsonArray array=new JsonArray();checks.forEach(array::add);value.add("checks",array);Files.writeString(output,new Gson().toJson(value));}catch(Exception ignored){}client.scheduleStop();}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
