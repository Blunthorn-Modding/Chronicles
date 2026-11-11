package io.github.chronicle.core.client.testing;

import com.google.gson.*;
import io.github.chronicle.core.client.CoreMenuScreen;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.network.*;
import net.minecraft.client.util.ScreenshotRecorder;

/** Actual native popup/create/export transport, only in a disposable marked fixture. */
public final class ScenarioProfileSmoke {
    private final Path output;private final String mode;private final List<String> messages=new ArrayList<>(),checks=new ArrayList<>();
    private CoreMenuScreen parent;private int step,wait;private boolean connecting,done;private final long began=System.nanoTime();
    private ScenarioProfileSmoke(String file,String mode){output=Path.of(file);this.mode=mode;Path root=Path.of(Objects.requireNonNull(System.getenv("CHRONICLE_ROLES_SERVER_DIR"))).toAbsolutePath().normalize();if(!Files.isRegularFile(root.resolve(".player-roles-fixture"))||!root.getParent().getFileName().toString().equals("test-runs")||output.toAbsolutePath().startsWith(root))throw new IllegalArgumentException("unsafe profile smoke");}
    public static void register(){String output=System.getenv("CHRONICLE_SCENARIO_SMOKE");if(output==null)return;var probe=new ScenarioProfileSmoke(output,System.getenv("CHRONICLE_SCENARIO_MODE"));ClientReceiveMessageEvents.GAME.register((text,overlay)->{if(!overlay)probe.messages.add(text.getString());});ClientTickEvents.END_CLIENT_TICK.register(probe::tick);}
    private void tick(MinecraftClient c){if(done)return;try{
        if(System.nanoTime()-began>120_000_000_000L)throw new IllegalStateException("timeout step "+step+" "+messages);
        if(c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof AccessibilityOnboardingScreen)c.setScreen(new TitleScreen());
        if(!connecting&&c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof TitleScreen){connecting=true;ConnectScreen.connect(c.currentScreen,c,ServerAddress.parse("127.0.0.1:25575"),new ServerInfo("Profile fixture","127.0.0.1:25575",ServerInfo.ServerType.OTHER),false,null);return;}
        if(c.player==null)return;c.getToastManager().clear();
        for(String message:messages)if(message.contains("Profile trial FAIL"))throw new IllegalStateException(message);
        CoreMenuScreen menu=c.currentScreen instanceof CoreMenuScreen m?m:null;
        if(step==0){c.player.networkHandler.sendChatCommand("chronicle");step=1;return;}
        if(step==1){if(menu==null||menu.pending())return;parent=menu;if(!mode.equals("create")){command(c,mode.equals("profile")?"empty":mode);step=2;}else step=3;return;}
        if(step==2){if(!passed(mode.equals("profile")?"empty":mode))return;checks.add("profile runtime assertions: "+mode);if(!mode.equals("profile")){finish(c,null);return;}step=3;}
        if(step==3){if(menu==null||menu.pending())return;if(!menu.state().editing()){require(menu.triggerForTest("Enable editing"),"Editing missing");step=4;return;}step=4;}
        if(step==4){if(menu==null||menu.pending()||!menu.state().editing())return;if(mode.equals("profile")){command(c,"seed");step=5;return;}step=6;}
        if(step==5){if(!passed("seed"))return;checks.add("normal book/role/loot/event authoring services saved empty-profile content");c.setScreen(null);c.player.networkHandler.sendChatCommand("chronicle");step=6;return;}
        if(step==6){if(menu==null||menu.pending())return;parent=menu;require(parent.triggerForTest("Scenario content...")||parent.triggerForTest("..."),"scenario header missing");step=7;return;}
        if(step==7){if(!screen(c))return;capture(c,"landing");if(mode.equals("create")){press(c,"Create custom scenario...");step=8;}else{press(c,"Native scenario");step=12;}return;}
        if(step==8){var fields=fields(c);require(fields.size()==2,"create name/ID fields missing");fields.get(0).setText(" Native scenario ");fields.get(1).setText("custom:native_scenario ");capture(c,"create");press(c,"Create setup");step=9;return;}
        if(step==9){if(!has(c,"Copy path"))return;var result=parent.state().scenarioResult();require(result!=null&&result.kind().equals("KIT")&&result.scenarioId().equals("custom:native_scenario")&&Files.isRegularFile(Path.of(result.path())),"create result not verified");checks.add("native Create acknowledged canonical file; whitespace normalized");wait=4;step=10;return;}
        if(step==10){if(wait-->0)return;capture(c,"created");finish(c,null);return;}
        if(step==12){if(!has(c,"Export content")||!button(c,"Export content").active)return;capture(c,"preview");press(c,"Export content");step=13;return;}
        if(step==13){if(!has(c,"Copy path"))return;var result=parent.state().scenarioResult();require(result!=null&&result.kind().equals("PROFILE")&&Files.isRegularFile(Path.of(result.path())),"export result missing");command(c,"export");step=14;return;}
        if(step==14){if(!passed("export"))return;wait=4;step=15;return;}
        if(step==15){if(wait-->0)return;capture(c,"exported");checks.add("native Export displays server file, preserves identity and exports effective reusable content");press(c,"Close");command(c,"edit");step=16;return;}
        if(step==16){if(!passed("edit"))return;checks.add("normal authoring services changed book and deleted role after export");finish(c,null);}
    }catch(Throwable error){finish(c,error.toString());}}
    private void command(MinecraftClient c,String verb){c.player.networkHandler.sendChatCommand("chronicle_profile_trial "+verb);}
    private boolean passed(String verb){return messages.stream().anyMatch(v->v.contains("Profile trial PASS "+verb));}
    private static boolean screen(MinecraftClient c){return c.currentScreen!=null&&c.currentScreen.getClass().getSimpleName().equals("ScenarioContentScreen");}
    private static List<TextFieldWidget> fields(MinecraftClient c){return c.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).toList();}
    private static boolean has(MinecraftClient c,String label){return c.currentScreen.children().stream().anyMatch(v->v instanceof ButtonWidget b&&b.getMessage().getString().equals(label));}
    private static ButtonWidget button(MinecraftClient c,String label){return c.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new IllegalStateException("button missing "+label));}
    private static void press(MinecraftClient c,String label){var b=button(c,label);require(b.active,"button disabled "+label);b.onPress(new net.minecraft.client.input.MouseInput(0,0));}
    private void capture(MinecraftClient c,String label){ScreenshotRecorder.saveScreenshot(output.getParent().toFile(),"profile-"+mode+"-"+label+".png",c.getFramebuffer(),1,t->{});}
    private void finish(MinecraftClient c,String error){if(done)return;done=true;try{var result=new JsonObject();result.addProperty("result",error==null?"PASS":"FAIL");result.addProperty("detail",error==null?"Native profile flow complete":error);result.add("checks",new Gson().toJsonTree(checks));Files.writeString(output,new Gson().toJson(result));}catch(Exception ignored){}c.scheduleStop();}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
