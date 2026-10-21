package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.menu.CoreMenuModel.Page;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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

/** Opt-in rendered/native regression for the two short local ecological costs. */
final class QuietBelowCostsSmoke {
    private static final String OUTPUT=System.getenv("CHRONICLE_ECOLOGY_SMOKE"),PHASE=System.getenv("CHRONICLE_ECOLOGY_PHASE"),ADDRESS="127.0.0.1:25575";
    private final List<String> messages=new ArrayList<>(),checks=new ArrayList<>();private final List<Step> steps=new ArrayList<>();
    private final long overallDeadline=System.nanoTime()+300_000_000_000L;
    private int index,baseline;private long deadline,until;private boolean started,connected,built,finished;
    static void register(){if(OUTPUT==null||!List.of("costs","costs_restart").contains(PHASE))return;var smoke=new QuietBelowCostsSmoke();ClientReceiveMessageEvents.GAME.register((text,overlay)->smoke.messages.add(text.getString()));ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);}
    private void tick(MinecraftClient client){
        if(finished)return;try{
            require(System.nanoTime()<overallDeadline,"cost smoke overall timeout");
            if(!connected&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen){connected=true;ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(ADDRESS),new ServerInfo("Chronicle costs fixture",ADDRESS,ServerInfo.ServerType.OTHER),false,null);}
            if(client.player==null||client.world==null||(client.currentScreen!=null&&!(client.currentScreen instanceof CoreMenuScreen)))return;
            require(client.getCurrentServerEntry()!=null&&ADDRESS.equals(client.getCurrentServerEntry().address),"cost fixture address mismatch");
            if(!built){build(client);built=true;}if(index>=steps.size()){finish(client,true,"local cost rehearsal complete");return;}
            Step step=steps.get(index);if(!started){baseline=messages.size();deadline=System.nanoTime()+step.timeoutMillis*1_000_000L;step.start.run();started=true;}
            if(step.done.getAsBoolean()){checks.add(step.name);index++;started=false;}else require(System.nanoTime()<deadline,"timed out: "+step.name);
        }catch(Exception error){finish(client,false,error.toString());}
    }
    private void build(MinecraftClient client){if("costs".equals(PHASE))costs(client);else restart(client);}
    private void costs(MinecraftClient client){
        command(client,"prepare dense local-cost fixture","quiet_below_costs_trial prepare","PASS costs prepare:.*");
        command(client,"enable costs authoring","chronicle author mode on","Author mode enabled.*");
        command(client,"create cost source","chronicle author region create costs_source","Region draft costs_source opened.*");
        command(client,"cost source first corner","chronicle author region pos1 -4 64 0","Set region draft costs_source position 1.*");
        command(client,"cost source second corner","chronicle author region pos2 15 64 39","Set region draft costs_source position 2.*");
        command(client,"save cost source","chronicle author region save","Saved region costs_source.*");
        command(client,"look at costs Heart","tp @s -3.5 65 0.5 0 90",".*Teleported.*");
        command(client,"bind costs Heart","chronicle author block bind costs_heart target","Saved block costs_heart.*");
        command(client,"assign costs source","chronicles setup assign \"quiet_below:source_city\" costs_source","Requirement assigned.*");
        command(client,"assign costs Heart","chronicles setup assign \"quiet_below:relic_heart\" costs_heart","Requirement assigned.*");
        command(client,"seal alternate Heart contacts","quiet_below_costs_trial seal","PASS costs seal:.*");
        command(client,"index costs source","chronicles setup tool \"quiet_below:ecology\" index","Source indexing started.*");delay("finish costs source index",2_500);
        command(client,"enable costs ecology","chronicles setup tool \"quiet_below:ecology\" enable","Growth enabled.*");
        openTool(client,"Sculk vitality","tool:quiet_below:vitality");vitality(client,"Lower extent thresholds");vitality(client,"Lower extent thresholds");check("close vitality controls",()->client.currentScreen.close());
        openTool(client,"Local Hunger","tool:quiet_below:hunger");click(client,"More frequent pulses");click(client,"More frequent pulses");check("minimum hunger interval disables faster",()->require(((CoreMenuScreen)client.currentScreen).state().detail().controls().stream().filter(c->c.label().equals("More frequent pulses")).noneMatch(c->c.enabled()),"Hunger interval underflow"));screenshot(client,"local-hunger-settings.png");check("close hunger controls",()->client.currentScreen.close());
        check("resize for darkness menu",()->client.getWindow().setWindowedSize(1280,720));delay("render wide costs menu",400);
        openTool(client,"Local Darkness","tool:quiet_below:darkness");click(client,"More frequent pulses");click(client,"More frequent pulses");check("minimum darkness interval disables faster",()->require(((CoreMenuScreen)client.currentScreen).state().detail().controls().stream().filter(c->c.label().equals("More frequent pulses")).noneMatch(c->c.enabled()),"Darkness interval underflow"));screenshot(client,"local-darkness-settings.png");check("close darkness controls",()->client.currentScreen.close());
        command(client,"start cost play","chronicle session start","Started Chronicle session.*");command(client,"join cost play","chronicles join",".*(joined|already).*Chronicle participation.*");
        delay("allow dense field to reach Extensive",4_000);
        delay("complete the first ten second cost interval",6_800);command(client,"near dense sculk pulses both costs","quiet_below_costs_trial pulse","PASS costs pulse:.*");
        command(client,"clear effects for distance control","effect clear @s",".*([Rr]emoved every effect|[Cc]leared|No effects).*");command(client,"move to distant control","tp @s 24.5 64 2.5 0 0",".*Teleported.*");command(client,"remember distant effects","quiet_below_costs_trial far","PASS costs far:.*");delay("distant participant remains clear",10_800);command(client,"distant participant receives no local costs","quiet_below_costs_trial unchanged","PASS costs unchanged:.*");
        command(client,"return to dense living sculk","tp @s 2.5 65 2.5 0 0",".*Teleported.*");command(client,"remember fresh re-entry","quiet_below_costs_trial remember","PASS costs remember:.*");delay("re-entry has no stored credit",900);command(client,"re-entry waits full interval","quiet_below_costs_trial unchanged","PASS costs unchanged:.*");
        command(client,"pause cost play","chronicle session pause","Paused Chronicle session.*");delay("paused ecology stays frozen",10_800);command(client,"paused participant receives no costs","quiet_below_costs_trial unchanged","PASS costs unchanged:.*");command(client,"resume cost play","chronicle session resume","Resumed Chronicle session.*");
        command(client,"cut only Heart contact","quiet_below_costs_trial cut","PASS costs cut:.*");delay("cut network remains ineligible",10_800);command(client,"cut roots stop local costs","quiet_below_costs_trial unchanged","PASS costs unchanged:.*");command(client,"restore physical Heart contact","quiet_below_costs_trial reconnect","PASS costs reconnect:.*");
        command(client,"make participant creative","quiet_below_costs_trial creative","PASS costs creative:.*");delay("creative participant stays clear",10_800);command(client,"creative participant receives no costs","quiet_below_costs_trial unchanged","PASS costs unchanged:.*");command(client,"restore survival near sculk","gamemode survival @s",".*[Ss]urvival.*");command(client,"return near field","tp @s 2.5 65 2.5 0 0",".*Teleported.*");
        command(client,"leave Chronicle participation","chronicles leave","You left participation.*");command(client,"remember nonparticipant effects","quiet_below_costs_trial remember","PASS costs remember:.*");delay("nonparticipant stays clear",10_800);command(client,"nonparticipant receives no local costs","quiet_below_costs_trial unchanged","PASS costs unchanged:.*");command(client,"rejoin Chronicle participation","chronicles join",".*(joined|already).*Chronicle participation.*");command(client,"remember rejoined effects","quiet_below_costs_trial remember","PASS costs remember:.*");delay("rejoined player has fresh wait",900);command(client,"rejoined player has no stored credit","quiet_below_costs_trial unchanged","PASS costs unchanged:.*");
        delay("approach the fresh cost deadline",8_800);command(client,"install shorter and stronger foreign effects","quiet_below_costs_trial foreign","PASS costs foreign:.*");delay("foreign effects cover guarded pulse",1_500);command(client,"preserve foreign effect strength and duration","quiet_below_costs_trial unchanged","PASS costs unchanged:.*");
        command(client,"pause before cost restart","chronicle session pause","Paused Chronicle session.*");command(client,"enable authoring for persisted costs","chronicle author mode on","Author mode enabled.*");command(client,"save fast cost state","quiet_below_costs_trial save","PASS costs save:.*");
    }
    private void restart(MinecraftClient client){
        command(client,"verify saved cost tuning","quiet_below_costs_trial restart","PASS costs restart:.*");command(client,"resume cost play after restart","chronicle session resume","Resumed Chronicle session.*");command(client,"join restarted cost play","chronicles join",".*(joined.*Chronicle participation|already a Chronicle participant).*");
        command(client,"remember restart effects","quiet_below_costs_trial remember","PASS costs remember:.*");delay("restart requires fresh interval",900);command(client,"restart has no offline cost credit","quiet_below_costs_trial unchanged","PASS costs unchanged:.*");delay("restart full cost interval",10_000);command(client,"restart pulses both costs","quiet_below_costs_trial pulse","PASS costs pulse:.*");
        command(client,"pause review fixture","chronicle session pause","Paused Chronicle session.*");command(client,"enable review authoring","chronicle author mode on","Author mode enabled.*");command(client,"reset hunger for review","chronicles setup tool \"quiet_below:hunger\" reset","Local Hunger enabled; interval 40 eligible seconds.*");command(client,"reset darkness for review","chronicles setup tool \"quiet_below:darkness\" reset","Local Darkness enabled; interval 40 eligible seconds.*");command(client,"prepare local-cost review","quiet_below_costs_trial review","PASS costs review:.*");command(client,"set costs review spawn","setworldspawn 8 65 22 180 0",".*spawn.*");command(client,"keep review spawn on signed bench","gamerule spawnRadius 0",".*spawnRadius.*");command(client,"take overview camera","gamemode creative @s",".*[Cc]reative.*");command(client,"show prepared cost bench","tp @s 8.5 73 45 180 30",".*Teleported.*");screenshot(client,"local-costs-review-ready.png");
    }
    private void openTool(MinecraftClient client,String label,String id){
        step("open "+label+" menu",()->send(client,"chronicles"),()->client.currentScreen instanceof CoreMenuScreen,10_000);
        step("open Game setup for "+label,()->require(press((CoreMenuScreen)client.currentScreen,"Game setup"),"Game setup unavailable"),()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().page()==Page.SETUP,10_000);
        step("select visible "+label+" tool",()->{var menu=(CoreMenuScreen)client.currentScreen;boolean selected=false;for(int n=0;n<16;n++){if(menu.triggerForTest(label)){selected=true;break;}require(menu.triggerForTest("↓"),label+" is hidden without usable list scrolling");}require(selected,label+" could not be selected");},()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().detail()!=null&&m.state().detail().id().equals(id),10_000);
    }
    private void click(MinecraftClient client,String label){step("native "+label,()->require(press((CoreMenuScreen)client.currentScreen,label),label+" unavailable"),()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().message().contains("enabled; interval"),10_000);}
    private void vitality(MinecraftClient client,String label){step("native vitality "+label,()->require(press((CoreMenuScreen)client.currentScreen,label),label+" unavailable"),()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().message().contains("Vitality tuning saved"),10_000);}
    private boolean press(CoreMenuScreen menu,String label){for(int n=0;n<60;n++)menu.scrollDetailForTest(1);for(int n=0;n<120;n++){if(menu.triggerForTest(label))return true;menu.scrollDetailForTest(-1);}return false;}
    private void screenshot(MinecraftClient c,String n){check("clear overlays for "+n,()->{c.inGameHud.getChatHud().clear(false);c.getToastManager().clear();});delay("render "+n,400);step("capture "+n,()->ScreenshotRecorder.saveScreenshot(Path.of(OUTPUT).toFile(),n,c.getFramebuffer(),1,ignored->{}),()->Files.isRegularFile(Path.of(OUTPUT,"screenshots",n)),20_000);}
    private void command(MinecraftClient c,String n,String command,String response){step(n,()->send(c,command),()->received(response),20_000);}private void delay(String n,long ms){step(n,()->until=System.nanoTime()+ms*1_000_000L,()->System.nanoTime()>=until,ms+5_000);}private void check(String n,Runnable r){step(n,r,()->true,1_000);}private void step(String n,Runnable s,BooleanSupplier d,long t){steps.add(new Step(n,s,d,t));}private void send(MinecraftClient c,String command){c.player.networkHandler.sendChatCommand(command);}
    private boolean received(String expression){var fresh=messages.subList(baseline,messages.size());for(String message:fresh)if(message.startsWith("FAIL costs "))throw new IllegalStateException(message);Pattern p=Pattern.compile(expression,Pattern.CASE_INSENSITIVE);return fresh.stream().anyMatch(message->p.matcher(message).matches());}
    private void finish(MinecraftClient c,boolean pass,String detail){if(finished)return;finished=true;try{Files.createDirectories(Path.of(OUTPUT));Files.writeString(Path.of(OUTPUT).resolve("ecology-"+PHASE+".json"),new Gson().toJson(Map.of("result",pass?"PASS":"FAIL","detail",detail,"checks",checks,"step",index<steps.size()?steps.get(index).name:"complete","messages",messages)));}catch(Exception error){error.printStackTrace();}c.scheduleStop();}
    private static void require(boolean value,String detail){if(!value)throw new IllegalStateException(detail);}private record Step(String name,Runnable start,BooleanSupplier done,long timeoutMillis) { }
}
