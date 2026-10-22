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

/** Opt-in rendered/native regression for the continuous contact costs and selectable growth modes. */
final class QuietBelowCostsSmoke {
    private static final String OUTPUT=System.getenv("CHRONICLE_ECOLOGY_SMOKE"),PHASE=System.getenv("CHRONICLE_ECOLOGY_PHASE"),ADDRESS="127.0.0.1:25575";
    private final List<String> messages=new ArrayList<>(),checks=new ArrayList<>();private final List<Step> steps=new ArrayList<>();
    private final long overallDeadline=System.nanoTime()+300_000_000_000L;
    private int index,baseline;private long deadline,until;private boolean started,connected,built,finished,jumpSeen;
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
        command(client,"cost source second corner","chronicle author region pos2 15 66 39","Set region draft costs_source position 2.*");
        command(client,"save cost source","chronicle author region save","Saved region costs_source.*");
        command(client,"look at costs Heart","tp @s -3.5 65 0.5 0 90",".*Teleported.*");
        command(client,"bind costs Heart","chronicle author block bind costs_heart target","Saved block costs_heart.*");
        command(client,"assign costs source","chronicles setup assign \"quiet_below:source_city\" costs_source","Requirement assigned.*");
        command(client,"assign costs Heart","chronicles setup assign \"quiet_below:relic_heart\" costs_heart","Requirement assigned.*");
        command(client,"seal alternate Heart contacts","quiet_below_costs_trial seal","PASS costs seal:.*");
        command(client,"index costs source","chronicles setup tool \"quiet_below:ecology\" index","Source indexing started.*");delay("finish costs source index",2_500);
        command(client,"enable costs ecology","chronicles setup tool \"quiet_below:ecology\" enable","Growth enabled.*");
        openTool(client,"Spread mode","tool:quiet_below:spread");action(client,"Use sculk-count rate");action(client,"Raise count amplification");check("growth mode shows count rate",()->require(((CoreMenuScreen)client.currentScreen).state().detail().facts().stream().anyMatch(f->f.value().contains("Sculk-count rate")),"count growth mode not selected"));screenshot(client,"spread-mode-settings.png");action(client,"Use fixed rate");check("growth mode returns to fixed",()->require(((CoreMenuScreen)client.currentScreen).state().detail().facts().stream().anyMatch(f->f.value().contains("Fixed rate")),"fixed growth mode not selected"));check("close ecology controls",()->client.currentScreen.close());
        openTool(client,"Sculk vitality","tool:quiet_below:vitality");vitality(client,"Lower extent thresholds");vitality(client,"Lower extent thresholds");check("close vitality controls",()->client.currentScreen.close());
        openTool(client,"Local Hunger","tool:quiet_below:hunger");click(client,"Shorter exposure delay");click(client,"Shorter exposure delay");check("minimum hunger delay disables faster",()->require(((CoreMenuScreen)client.currentScreen).state().detail().controls().stream().filter(c->c.label().equals("Shorter exposure delay")).noneMatch(c->c.enabled()),"Hunger delay underflow"));screenshot(client,"local-hunger-settings.png");check("close hunger controls",()->client.currentScreen.close());
        check("resize for darkness menu",()->client.getWindow().setWindowedSize(1280,720));delay("render wide costs menu",400);
        openTool(client,"Local Darkness","tool:quiet_below:darkness");click(client,"Shorter exposure delay");click(client,"Shorter exposure delay");check("minimum darkness delay disables faster",()->require(((CoreMenuScreen)client.currentScreen).state().detail().controls().stream().filter(c->c.label().equals("Shorter exposure delay")).noneMatch(c->c.enabled()),"Darkness delay underflow"));screenshot(client,"local-darkness-settings.png");check("close darkness controls",()->client.currentScreen.close());
        command(client,"start cost play","chronicle session start","Started Chronicle session.*");command(client,"join cost play","chronicles join",".*(joined|already).*Chronicle participation.*");
        delay("allow dense field to reach Extensive",4_000);delay("complete first ten second exposure delay",10_300);command(client,"contact applies marked infinite effects","quiet_below_costs_trial active","PASS costs active:.*");command(client,"record continuous instances","quiet_below_costs_trial remember","PASS costs remember:.*");command(client,"contact effects stay continuously applied","quiet_below_costs_trial serialize_owned","PASS costs serialize_owned:.*");command(client,"verify copied ownership marker","quiet_below_costs_trial copy","PASS costs copy:.*");command(client,"landing tolerance retains sculk but rejects carpet height","quiet_below_costs_trial precision","PASS costs precision:.*");delay("pass the old pulse period and cadence",12_000);command(client,"same effects persist beyond old pulse period","quiet_below_costs_trial sustained","PASS costs sustained:.*");command(client,"leave the ground over living sculk","quiet_below_costs_trial air","PASS costs air:.*");delay("sample while still airborne",200);command(client,"jumping retains contact exposure","quiet_below_costs_trial airborne","PASS costs airborne:.*");delay("land on the same living sculk",1_800);command(client,"living landing retains the same effects","quiet_below_costs_trial landed","PASS costs landed:.*");
        step("normal client jump input",()->{jumpSeen=false;until=System.nanoTime()+800_000_000L;},()->{
            client.options.jumpKey.setPressed(System.nanoTime()<until);
            if(!client.player.isOnGround())jumpSeen=true;
            if(System.nanoTime()<until+1_500_000_000L)return false;
            client.options.jumpKey.setPressed(false);require(jumpSeen&&client.player.isOnGround(),"ordinary client jump did not leave and return to the ground");return true;
        },8_000);
        command(client,"normal jump and landing preserve exact cost instances","quiet_below_costs_trial sustained","PASS costs sustained:.*");
        command(client,"start real floor-vein walk","quiet_below_costs_trial lane","PASS costs lane:.*");
        movement(client,"walk across distinct floor-vein supports",false);
        command(client,"floor-vein walk preserves exact cost instances","quiet_below_costs_trial lane_end","PASS costs lane_end:.*");
        command(client,"return for real lateral jump","quiet_below_costs_trial lane","PASS costs lane:.*");
        movement(client,"jump across distinct floor-vein supports",true);
        command(client,"lateral jump preserves exact cost instances","quiet_below_costs_trial lane_end","PASS costs lane_end:.*");
        command(client,"return from vein lane","quiet_below_costs_trial lane","PASS costs lane:.*");
        command(client,"prepare immediate native respawn","gamerule doImmediateRespawn true",".*doImmediateRespawn.*");command(client,"respawn on uncoated living footing","spawnpoint @s 1 65 2",".*spawn point.*");command(client,"die with active contact effects","kill @s",".*Killed.*");delay("native death and immediate respawn",1_200);command(client,"respawn starts without old exposure credit","quiet_below_costs_trial clear","PASS costs clear:.*");delay("respawn completes fresh contact wait",10_300);command(client,"respawn gains effects only after waiting","quiet_below_costs_trial active","PASS costs active:.*");
        command(client,"raise glass pillar under feet","quiet_below_costs_trial pillar","PASS costs pillar:.*");delay("pillar clears contact effects",800);command(client,"glass pillar is safe despite nearby sculk","quiet_below_costs_trial clear","PASS costs clear:.*");command(client,"step down onto living sculk","quiet_below_costs_trial down","PASS costs down:.*");delay("fresh footing has no saved exposure",800);command(client,"fresh footing waits again","quiet_below_costs_trial clear","PASS costs clear:.*");delay("fresh footing completes full delay",10_300);command(client,"footing reactivates after full delay","quiet_below_costs_trial active","PASS costs active:.*");
        command(client,"install independent foreign effects","quiet_below_costs_trial foreign","PASS costs foreign:.*");command(client,"leave sculk with foreign effects","quiet_below_costs_trial pillar","PASS costs pillar:.*");delay("foreign effects survive eligibility change",800);command(client,"foreign effects remain after leaving sculk","quiet_below_costs_trial foreign_off","PASS costs foreign_off:.*");command(client,"foreign effects serialize normally","quiet_below_costs_trial serialize_foreign","PASS costs serialize_foreign:.*");command(client,"clear foreign effects","effect clear @s",".*([Rr]emoved every effect|[Cc]leared|No effects).*");command(client,"return to living footing","quiet_below_costs_trial down","PASS costs down:.*");delay("wait for native replacement test",10_300);command(client,"directly replace marked Hunger","quiet_below_costs_trial replace","PASS costs replace:.*");command(client,"preserve direct replacement off sculk","quiet_below_costs_trial pillar","PASS costs pillar:.*");delay("direct replacement exit",400);command(client,"clear direct replacement for next checks","effect clear @s",".*([Rr]emoved every effect|[Cc]leared|No effects).*");command(client,"restore footing for lifecycle checks","quiet_below_costs_trial down","PASS costs down:.*");
        command(client,"pause cost play","chronicle session pause","Paused Chronicle session.*");delay("pause clears contact effects",800);command(client,"paused player has no campaign effects","quiet_below_costs_trial clear","PASS costs clear:.*");command(client,"resume cost play","chronicle session resume","Resumed Chronicle session.*");delay("resumed player starts fresh delay",800);command(client,"resume did not bank contact time","quiet_below_costs_trial clear","PASS costs clear:.*");
        command(client,"cut only Heart contact","quiet_below_costs_trial cut","PASS costs cut:.*");delay("cut clears contact effects",800);command(client,"cut roots stop contact effects","quiet_below_costs_trial clear","PASS costs clear:.*");command(client,"restore physical Heart contact","quiet_below_costs_trial reconnect","PASS costs reconnect:.*");
        command(client,"make participant creative","quiet_below_costs_trial creative","PASS costs creative:.*");delay("creative clears contact effects",800);command(client,"creative player has no campaign effects","quiet_below_costs_trial clear","PASS costs clear:.*");command(client,"restore survival near sculk","gamemode survival @s",".*[Ss]urvival.*");command(client,"return to living footing after creative","quiet_below_costs_trial down","PASS costs down:.*");
        command(client,"leave Chronicle participation","chronicles leave","You left participation.*");delay("leaving clears contact effects",800);command(client,"nonparticipant has no campaign effects","quiet_below_costs_trial clear","PASS costs clear:.*");command(client,"rejoin Chronicle participation","chronicles join",".*(joined|already).*Chronicle participation.*");delay("rejoin has fresh contact delay",800);command(client,"rejoined player did not bank contact time","quiet_below_costs_trial clear","PASS costs clear:.*");
        command(client,"pause before cost restart","chronicle session pause","Paused Chronicle session.*");command(client,"enable authoring for persisted costs","chronicle author mode on","Author mode enabled.*");command(client,"persist non-default growth mode","chronicles setup tool \"quiet_below:spread\" use_count","Spread tuning saved:.*");command(client,"save fast cost state","quiet_below_costs_trial save","PASS costs save:.*");
    }
    private void restart(MinecraftClient client){
        command(client,"verify saved tuning and cleared restart effects","quiet_below_costs_trial restart","PASS costs restart:.*");command(client,"resume cost play after restart","chronicle session resume","Resumed Chronicle session.*");command(client,"join restarted cost play","chronicles join",".*(joined.*Chronicle participation|already a Chronicle participant).*");
        delay("restart begins a fresh exposure delay",800);command(client,"restart has no offline contact credit","quiet_below_costs_trial clear","PASS costs clear:.*");delay("restart full contact delay",10_300);command(client,"restart applies fresh marked effects","quiet_below_costs_trial active","PASS costs active:.*");
        command(client,"pause review fixture","chronicle session pause","Paused Chronicle session.*");command(client,"enable review authoring","chronicle author mode on","Author mode enabled.*");command(client,"reset hunger for review","chronicles setup tool \"quiet_below:hunger\" reset","Local Hunger enabled; initial delay 40 eligible seconds.*");command(client,"reset darkness for review","chronicles setup tool \"quiet_below:darkness\" reset","Local Darkness enabled; initial delay 40 eligible seconds.*");command(client,"reset growth mode for review","chronicles setup tool \"quiet_below:spread\" spread_reset","Spread tuning saved:.*");command(client,"prepare local-cost review","quiet_below_costs_trial review","PASS costs review:.*");command(client,"set costs review spawn","setworldspawn 8 65 22 180 0",".*spawn.*");command(client,"keep review spawn on signed bench","gamerule spawnRadius 0",".*spawnRadius.*");command(client,"take overview camera","gamemode creative @s",".*[Cc]reative.*");command(client,"show prepared cost bench","tp @s 8.5 73 45 180 30",".*Teleported.*");screenshot(client,"local-costs-review-ready.png");
    }
    private void openTool(MinecraftClient client,String label,String id){
        step("open "+label+" menu",()->send(client,"chronicles"),()->client.currentScreen instanceof CoreMenuScreen,10_000);
        step("open Game setup for "+label,()->require(press((CoreMenuScreen)client.currentScreen,"Game setup"),"Game setup unavailable"),()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().page()==Page.SETUP,10_000);
        step("select visible "+label+" tool",()->{},()->{
            if(!(client.currentScreen instanceof CoreMenuScreen menu)||menu.pending())return false;
            if(menu.state().detail()!=null&&menu.state().detail().id().equals(id))return true;
            if(menu.triggerForTest(label))return false;
            if(menu.triggerForTest("\u2193"))return false;
            require(menu.triggerForTest(">"),label+" is hidden without usable list/page navigation");return false;
        },20_000);
    }
    private void movement(MinecraftClient client,String label,boolean jumping){
        step(label,()->{jumpSeen=false;until=System.nanoTime()+2_000_000_000L;},()->{
            boolean moving=System.nanoTime()<until;
            client.options.forwardKey.setPressed(moving);client.options.jumpKey.setPressed(moving&&jumping);
            if(!client.player.isOnGround())jumpSeen=true;
            if(System.nanoTime()<until+1_200_000_000L)return false;
            client.options.forwardKey.setPressed(false);client.options.jumpKey.setPressed(false);
            require(!jumping||jumpSeen,"lateral jump test never left the ground");return true;
        },8_000);
    }
    private void click(MinecraftClient client,String label){step("native "+label,()->require(press((CoreMenuScreen)client.currentScreen,label),label+" unavailable"),()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().message().contains("initial delay"),10_000);}
    private void action(MinecraftClient client,String label){step("native "+label,()->require(press((CoreMenuScreen)client.currentScreen,label),label+" unavailable"),()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&!m.state().message().isBlank(),10_000);}
    private void vitality(MinecraftClient client,String label){step("native vitality "+label,()->require(press((CoreMenuScreen)client.currentScreen,label),label+" unavailable"),()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().message().contains("Vitality tuning saved"),10_000);}
    private boolean press(CoreMenuScreen menu,String label){for(int n=0;n<60;n++)menu.scrollDetailForTest(1);for(int n=0;n<120;n++){if(menu.triggerForTest(label))return true;menu.scrollDetailForTest(-1);}return false;}
    private void screenshot(MinecraftClient c,String n){check("clear overlays for "+n,()->{c.inGameHud.getChatHud().clear(false);c.getToastManager().clear();});delay("render "+n,400);step("capture "+n,()->ScreenshotRecorder.saveScreenshot(Path.of(OUTPUT).toFile(),n,c.getFramebuffer(),1,ignored->{}),()->Files.isRegularFile(Path.of(OUTPUT,"screenshots",n)),20_000);}
    private void command(MinecraftClient c,String n,String command,String response){step(n,()->send(c,command),()->received(response),20_000);}private void delay(String n,long ms){step(n,()->until=System.nanoTime()+ms*1_000_000L,()->System.nanoTime()>=until,ms+5_000);}private void check(String n,Runnable r){step(n,r,()->true,1_000);}private void step(String n,Runnable s,BooleanSupplier d,long t){steps.add(new Step(n,s,d,t));}private void send(MinecraftClient c,String command){c.player.networkHandler.sendChatCommand(command);}
    private boolean received(String expression){var fresh=messages.subList(baseline,messages.size());for(String message:fresh)if(message.startsWith("FAIL costs "))throw new IllegalStateException(message);Pattern p=Pattern.compile(expression,Pattern.CASE_INSENSITIVE);return fresh.stream().anyMatch(message->p.matcher(message).matches());}
    private void finish(MinecraftClient c,boolean pass,String detail){if(finished)return;finished=true;c.options.jumpKey.setPressed(false);c.options.forwardKey.setPressed(false);try{Files.createDirectories(Path.of(OUTPUT));Files.writeString(Path.of(OUTPUT).resolve("ecology-"+PHASE+".json"),new Gson().toJson(Map.of("result",pass?"PASS":"FAIL","detail",detail,"checks",checks,"step",index<steps.size()?steps.get(index).name:"complete","messages",messages)));}catch(Exception error){error.printStackTrace();}c.scheduleStop();}
    private static void require(boolean value,String detail){if(!value)throw new IllegalStateException(detail);}private record Step(String name,Runnable start,BooleanSupplier done,long timeoutMillis) { }
}
