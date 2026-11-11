package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
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

/** Opt-in client driver for the disposable native local-healing regression fixture. */
final class QuietBelowRecoverySmoke {
    private static final String OUTPUT=System.getenv("CHRONICLE_ECOLOGY_SMOKE"), PHASE=System.getenv("CHRONICLE_ECOLOGY_PHASE"), ADDRESS="127.0.0.1:25575";
    private final List<String> messages=new ArrayList<>(),checks=new ArrayList<>();
    private final List<Step> steps=new ArrayList<>();
    private final long overallDeadline=System.nanoTime()+240_000_000_000L;
    private int index,baseline; private long deadline,until; private boolean started,connected,built,finished;

    static void register(){
        if(OUTPUT==null||!List.of("recovery","recovery_restart").contains(PHASE))return;
        var smoke=new QuietBelowRecoverySmoke();
        ClientReceiveMessageEvents.GAME.register((text,overlay)->smoke.messages.add(text.getString()));
        ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
    }
    private void tick(MinecraftClient client){
        if(finished)return;
        try{
            require(System.nanoTime()<overallDeadline,"recovery smoke overall timeout");
            if(!connected&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen){
                connected=true;ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(ADDRESS),new ServerInfo("Chronicle recovery fixture",ADDRESS,ServerInfo.ServerType.OTHER),false,null);
            }
            if(client.player==null||client.world==null||(client.currentScreen!=null&&!(client.currentScreen instanceof CoreMenuScreen)))return;
            require(client.getCurrentServerEntry()!=null&&ADDRESS.equals(client.getCurrentServerEntry().address),"recovery fixture address mismatch");
            if(!built){build(client);built=true;}
            if(index>=steps.size()){finish(client,true,"recovery rehearsal complete");return;}
            Step step=steps.get(index);
            if(!started){baseline=messages.size();deadline=System.nanoTime()+step.timeoutMillis*1_000_000L;step.start.run();started=true;}
            if(step.done.getAsBoolean()){checks.add(step.name);index++;started=false;}else require(System.nanoTime()<deadline,"timed out: "+step.name);
        }catch(Exception error){finish(client,false,error.toString());}
    }
    private void build(MinecraftClient client){if("recovery".equals(PHASE))recovery(client);else restart(client);}
    private void recovery(MinecraftClient client){
        command(client,"prepare isolated natural-healing fixture","quiet_below_recovery_trial prepare","PASS recovery prepare:.*");
        command(client,"enable recovery Editing","chronicle editing on","Editing enabled.*");
        command(client,"create recovery source","chronicle regions create recovery_source","Region selection recovery_source opened.*");
        command(client,"recovery source first corner","chronicle regions pos1 -4 64 0","Set Region draft recovery_source position 1.*");
        command(client,"recovery source second corner","chronicle regions pos2 7 64 7","Set Region draft recovery_source position 2.*");
        command(client,"save recovery source","chronicle regions save","Saved Region 'recovery_source'\\.");
        command(client,"look at recovery Heart","tp @s -3.5 65 0.5 0 90",".*Teleported.*");
        command(client,"bind recovery Heart","chronicle locations blocks bind recovery_heart target","Saved Block site recovery_heart.*");
        command(client,"assign recovery source","quiet_below_authoring_trial assign-source recovery_source","PASS canonical authoring assign-source:.*");
        command(client,"assign recovery Heart","quiet_below_authoring_trial assign-heart recovery_heart","PASS canonical authoring assign-heart:.*");
        command(client,"seal alternate Heart contacts for cut test","quiet_below_recovery_trial seal","PASS recovery seal:.*");
        command(client,"index recovery source","chronicle setup tool \"quiet_below:ecology\" index","Source indexing started.*");
        delay("finish recovery source index",1_200);
        command(client,"enable recovery ecology","chronicle setup tool \"quiet_below:ecology\" enable","Growth enabled.*");
        openVitality(client);vitality(client,"Lower extent thresholds");vitality(client,"Lower extent thresholds");check("close vitality controls",()->client.currentScreen.close());
        openRecovery(client);healing(client,"Faster healing");healing(client,"Faster healing");
        check("minimum healing interval is disabled",()->require(((CoreMenuScreen)client.currentScreen).state().detail().controls().stream().filter(c->c.label().equals("Faster healing")).noneMatch(c->c.enabled()),"healing interval underflow"));
        check("resize to a readable summary after compact-list selection",()->client.getWindow().setWindowedSize(1280,720));delay("render larger healing summary",600);
        check("show local-healing summary",()->{for(int n=0;n<100;n++)((CoreMenuScreen)client.currentScreen).scrollDetailForTest(1);});
        screenshot(client,"local-healing-settings.png");check("close local-healing controls",()->client.currentScreen.close());
        command(client,"start recovery play and clear author mode","chronicle session start","Started Chronicle session.*");
        command(client,"join recovery play","chronicle players join",".*Participation enabled.*");
        command(client,"remember injured nearby participant","quiet_below_recovery_trial remember","PASS recovery remember:.*");
        delay("one full two-second healing interval",2_500);command(client,"near living sculk heals one half-heart","quiet_below_recovery_trial healed","PASS recovery healed:.*");
        command(client,"move to distant control","tp @s 14.5 64 2.5 0 0",".*Teleported.*");command(client,"remember distant health","quiet_below_recovery_trial far","PASS recovery far:.*");
        delay("distant control remains unhealed",2_500);command(client,"distant participant receives no local healing","quiet_below_recovery_trial unchanged","PASS recovery unchanged:.*");
        command(client,"return to living sculk","tp @s 2.5 65 2.5 0 0",".*Teleported.*");command(client,"remember fresh re-entry health","quiet_below_recovery_trial remember","PASS recovery remember:.*");
        delay("re-entry does not carry healing credit",800);command(client,"re-entry needs a full interval","quiet_below_recovery_trial unchanged","PASS recovery unchanged:.*");
        delay("re-entry full interval",2_000);command(client,"re-entered participant heals","quiet_below_recovery_trial healed","PASS recovery healed:.*");
        command(client,"pause recovery play","chronicle session pause","Paused Chronicle session.*");command(client,"remember paused health","quiet_below_recovery_trial remember","PASS recovery remember:.*");
        delay("paused recovery remains frozen",2_500);command(client,"paused participant receives no healing","quiet_below_recovery_trial unchanged","PASS recovery unchanged:.*");
        command(client,"resume recovery play","chronicle session resume","Resumed Chronicle session.*");
        command(client,"cut the only Heart contact","quiet_below_recovery_trial cut","PASS recovery cut:.*");delay("cut network remains ineligible",2_500);command(client,"cut roots stop local healing","quiet_below_recovery_trial unchanged","PASS recovery unchanged:.*");
        command(client,"restore physical Heart contact","quiet_below_recovery_trial reconnect","PASS recovery reconnect:.*");delay("reconnected roots need a fresh interval",800);command(client,"reconnection has no stored credit","quiet_below_recovery_trial unchanged","PASS recovery unchanged:.*");delay("reconnected full interval",2_000);command(client,"reconnected network heals","quiet_below_recovery_trial healed","PASS recovery healed:.*");
        command(client,"make participant creative","quiet_below_recovery_trial creative","PASS recovery creative:.*");delay("creative health remains unchanged",2_500);command(client,"creative participants receive no healing","quiet_below_recovery_trial unchanged","PASS recovery unchanged:.*");
        command(client,"make participant adventure","gamemode adventure @s",".*[Aa]dventure.*");command(client,"restore nearby location","tp @s 2.5 65 2.5 0 0",".*Teleported.*");command(client,"remember adventure health","quiet_below_recovery_trial remember","PASS recovery remember:.*");delay("adventure full interval",2_500);command(client,"adventure participant heals","quiet_below_recovery_trial healed","PASS recovery healed:.*");
        command(client,"restore survival participant","gamemode survival @s",".*[Ss]urvival.*");
        command(client,"leave Chronicle participation","chronicle players leave","You left participation.*");command(client,"remember nonparticipant health","quiet_below_recovery_trial remember","PASS recovery remember:.*");delay("nonparticipant remains unhealed",2_500);command(client,"nonparticipant receives no healing","quiet_below_recovery_trial unchanged","PASS recovery unchanged:.*");
        command(client,"rejoin Chronicle participation","chronicle players join",".*Participation enabled.*");command(client,"remember rejoined health","quiet_below_recovery_trial remember","PASS recovery remember:.*");delay("rejoined player gets fresh interval",800);command(client,"rejoined player has no offline credit","quiet_below_recovery_trial unchanged","PASS recovery unchanged:.*");delay("rejoined full interval",2_000);command(client,"rejoined player heals","quiet_below_recovery_trial healed","PASS recovery healed:.*");
        command(client,"pause before recovery restart","chronicle session pause","Paused Chronicle session.*");command(client,"enable Editing for persisted healing","chronicle editing on","Editing enabled.*");command(client,"save fast healing state","quiet_below_recovery_trial save","PASS recovery save:.*");
    }
    private void restart(MinecraftClient client){
        command(client,"verify saved recovery health and tuning","quiet_below_recovery_trial restart","PASS recovery restart:.*");
        command(client,"resume recovery after restart","chronicle session resume","Resumed Chronicle session.*");command(client,"join restarted recovery play","chronicle players join",".*Participation enabled.*");
        delay("restart has no offline healing credit",800);command(client,"restart waits a fresh interval","quiet_below_recovery_trial unchanged","PASS recovery unchanged:.*");delay("restart full interval",2_000);command(client,"reconnected participant heals after restart","quiet_below_recovery_trial healed","PASS recovery healed:.*");
        command(client,"pause review fixture","chronicle session pause","Paused Chronicle session.*");command(client,"enable review Editing","chronicle editing on","Editing enabled.*");
        command(client,"reset local healing for review","chronicle setup tool \"quiet_below:recovery\" reset","Local healing enabled; interval 10 eligible seconds.*");
        command(client,"prepare authentic recovery review","quiet_below_recovery_trial review","PASS recovery review:.*");command(client,"set recovery review spawn","setworldspawn 8 64 8 180 0",".*spawn.*");
        command(client,"keep review spawn on the signed bench","gamerule spawnRadius 0",".*spawnRadius.*");
        command(client,"take a safe overview camera","gamemode creative @s",".*[Cc]reative.*");command(client,"show prepared healing bench","tp @s 8.5 72 13.5 180 28",".*Teleported.*");screenshot(client,"local-healing-review-ready.png");
    }
    private void openVitality(MinecraftClient client){
        step("open vitality menu",()->send(client,"chronicle"),()->client.currentScreen instanceof CoreMenuScreen,10_000);
        step("open Game setup for vitality",()->require(press((CoreMenuScreen)client.currentScreen,"Game setup"),"Game setup unavailable"),()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().page()==Page.SETUP,10_000);
        step("select Sculk vitality",()->((CoreMenuScreen)client.currentScreen).selectForTest("tool:quiet_below:vitality"),()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().detail()!=null&&m.state().detail().id().equals("tool:quiet_below:vitality"),10_000);
    }
    private void openRecovery(MinecraftClient client){
        step("open local-healing menu",()->send(client,"chronicle"),()->client.currentScreen instanceof CoreMenuScreen,10_000);
        step("open Game setup for local healing",()->require(press((CoreMenuScreen)client.currentScreen,"Game setup"),"Game setup unavailable"),()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().page()==Page.SETUP,10_000);
        step("reach Local healing through visible compact-list controls",()->{
            var menu=(CoreMenuScreen)client.currentScreen;boolean selected=false;
            for(int n=0;n<12;n++){if(menu.triggerForTest("Local healing")){selected=true;break;}require(menu.triggerForTest("↓"),"Local healing is hidden without a usable list scroll control");}
            require(selected,"Local healing could not be selected");
        },()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().detail()!=null&&m.state().detail().id().equals("tool:quiet_below:recovery"),10_000);
    }
    private void vitality(MinecraftClient client,String label){step("native vitality "+label,()->require(press((CoreMenuScreen)client.currentScreen,label),label+" unavailable"),()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().message().contains("Vitality tuning saved"),10_000);}
    private void healing(MinecraftClient client,String label){step("native recovery "+label,()->require(press((CoreMenuScreen)client.currentScreen,label),label+" unavailable"),()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().message().contains("Local healing enabled"),10_000);}
    private boolean press(CoreMenuScreen menu,String label){for(int n=0;n<60;n++)menu.scrollDetailForTest(1);for(int n=0;n<120;n++){if(menu.triggerForTest(label))return true;menu.scrollDetailForTest(-1);}return false;}
    private void screenshot(MinecraftClient client,String name){check("clear overlays for "+name,()->{client.inGameHud.getChatHud().clear(false);client.getToastManager().clear();});delay("render "+name,400);step("capture "+name,()->ScreenshotRecorder.saveScreenshot(Path.of(OUTPUT).toFile(),name,client.getFramebuffer(),1,ignored->{}),()->Files.isRegularFile(Path.of(OUTPUT,"screenshots",name)),20_000);}
    private void command(MinecraftClient client,String name,String command,String response){step(name,()->send(client,command),()->received(response),20_000);}
    private void delay(String name,long millis){step(name,()->until=System.nanoTime()+millis*1_000_000L,()->System.nanoTime()>=until,millis+5_000);}
    private void check(String name,Runnable assertion){step(name,assertion,()->true,1_000);}
    private void step(String name,Runnable start,BooleanSupplier done,long timeout){steps.add(new Step(name,start,done,timeout));}
    private void send(MinecraftClient client,String command){client.player.networkHandler.sendChatCommand(command);}
    private boolean received(String expression){var fresh=messages.subList(baseline,messages.size());for(String message:fresh)if(message.startsWith("FAIL recovery "))throw new IllegalStateException(message);Pattern pattern=Pattern.compile(expression,Pattern.CASE_INSENSITIVE);return fresh.stream().anyMatch(message->pattern.matcher(message).matches());}
    private void finish(MinecraftClient client,boolean pass,String detail){if(finished)return;finished=true;try{Files.createDirectories(Path.of(OUTPUT));Files.writeString(Path.of(OUTPUT).resolve("ecology-"+PHASE+".json"),new Gson().toJson(Map.of("result",pass?"PASS":"FAIL","detail",detail,"checks",checks,"step",index<steps.size()?steps.get(index).name:"complete","messages",messages)));}catch(Exception error){error.printStackTrace();}client.scheduleStop();}
    private static void require(boolean condition,String detail){if(!condition)throw new IllegalStateException(detail);}
    private record Step(String name,Runnable start,BooleanSupplier done,long timeoutMillis) { }
}
