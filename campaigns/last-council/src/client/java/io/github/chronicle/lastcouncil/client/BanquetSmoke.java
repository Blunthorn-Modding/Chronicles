package io.github.chronicle.lastcouncil.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.lastcouncil.banquet.BanquetConfig;
import io.github.chronicle.lastcouncil.network.BanquetPayloads;
import io.github.chronicle.lastcouncil.network.BanquetPayloads.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;

/** Opt-in two-client native setup and permissions smoke. */
public final class BanquetSmoke {
    private static BanquetSmoke instance;
    private final Path output, shared; private final String role, phase;
    private final List<String> checks = new ArrayList<>(); private final long started = System.nanoTime();
    private boolean connecting, done, giftTrial; private int step, wait, views; private UUID forged; private String offlineIllnesses; private long offlineTime;
    private BanquetSmoke(String path) {
        Path root=Path.of(System.getenv("CHRONICLE_BANQUET_ROOT")).toAbsolutePath().normalize();
        output=Path.of(path).toAbsolutePath().normalize(); shared=output.getParent();
        role=System.getenv("CHRONICLE_BANQUET_ROLE"); phase=System.getenv("CHRONICLE_BANQUET_PHASE");
        if(root.getParent()==null || !root.getParent().getFileName().toString().equals("test-runs")
                || !Files.isRegularFile(root.resolve(".council-banquet-fixture")) || output.startsWith(root)
                || !Set.of("A","B").contains(role) || !Set.of("initial","restart").contains(phase))
            throw new IllegalArgumentException("Unsafe banquet smoke fixture");
    }
    public static void register() { String path=System.getenv("CHRONICLE_BANQUET_SMOKE"); if(path!=null){instance=new BanquetSmoke(path);ClientTickEvents.END_CLIENT_TICK.register(instance::tick);} }
    public static void view(View view) { if(instance!=null && view.request().equals(instance.forged)) instance.views++; }
    private void mark(String name)throws Exception { Files.writeString(shared.resolve(phase+"-"+name),"ready",StandardCharsets.UTF_8); }
    private boolean has(String name){return Files.isRegularFile(shared.resolve(phase+"-"+name));}
    private void tick(MinecraftClient c) {
        if(done)return;
        try {
            if(System.nanoTime()-started>240_000_000_000L)throw new IllegalStateException("timeout "+role+" "+phase+" step "+step);
            if(giftTrial){if(BanquetGiftTrial.tick(c)){giftTrial=false;checks.add("actual gift response loss, disconnect/reconnect and same-nonce retry did not duplicate supplies");c.player.networkHandler.sendChatCommand("chronicle banquet trial clock_start");wait=60;step=15;}return;}
            if(c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof AccessibilityOnboardingScreen)c.setScreen(new TitleScreen());
            if(!connecting&&c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof TitleScreen){connecting=true;ConnectScreen.connect(c.currentScreen,c,ServerAddress.parse("127.0.0.1:25575"),new ServerInfo("Banquet smoke","127.0.0.1:25575",ServerInfo.ServerType.OTHER),false,null);return;}
            if(c.player==null)return; if(!c.player.getName().getString().equals("ChronicleFabric"+role))throw new IllegalStateException("Unexpected client");
            if(wait>0){wait--;return;}
            if(role.equals("B")){ second(c); return; } host(c);
        }catch(Throwable failure){finish(c,failure.toString());}
    }
    private void second(MinecraftClient c)throws Exception {
        switch(step){
            case 0->{mark("ready-B");c.player.networkHandler.sendChatCommand("chronicle players join");forged=UUID.randomUUID();ClientPlayNetworking.send(new Request(forged,Action.SAVE,0,new BanquetConfig(true,1,1),BanquetPayloads.NONE,0,0,0));wait=80;step=1;}
            case 1->{require(views==0,"non-operator received forged SAVE response");checks.add("non-operator forged SAVE received no response");mark("denied-B");step=2;}
            case 2->{if(!has("trial-complete"))return;c.player.networkHandler.sendChatCommand("chronicle banquet log");step=3;}
            case 3->{if(!(c.currentScreen instanceof BanquetScreen screen))return;View view=viewOf(screen);require(view.canLog()&&view.logView()&&!view.log().isEmpty(),"Chancellor provenance unavailable");checks.add("assigned Chancellor saw private provenance");mark("log-B");step=4;}
            case 4->{if(has("done-A")){if(phase.equals("initial")){wait=30;step=5;}else finish(c,null);}}
            case 5->{offlineIllnesses=illnesses();offlineTime=c.world.getTime();wait=60;step=6;}
            case 6->{require(offlineIllnesses.equals(illnesses())&&c.world.getTime()>offlineTime,"offline illness advanced or occupied active world was frozen");checks.add("affected player's clock stayed held offline while another participant kept active play running");finish(c,null);}
        }
    }
    private void host(MinecraftClient c)throws Exception {
        switch(step){
            case 0->{if(!has("ready-B")||!has("denied-B"))return;c.player.networkHandler.sendChatCommand("chronicle editing on");wait=40;step=1;}
            case 1->{c.player.networkHandler.sendChatCommand("chronicle setup");step=2;}
            case 2->{if(!(c.currentScreen instanceof CoreMenuScreen menu)||menu.pending())return;if(!menu.state().selected().equals("tool:last_council:banquet")){menu.selectForTest("tool:last_council:banquet");wait=10;return;}require(menu.triggerForTest("Open banquet"),"banquet setup action missing");step=3;}
            case 3->{if(!(c.currentScreen instanceof BanquetScreen screen))return;View view=viewOf(screen);require(view.editable()&&!view.canLog()&&view.log().isEmpty(),"unexpected setup permissions or private log");
                if(phase.equals("restart")){require(view.config().equals(new BanquetConfig(true,2,8)),"settings did not survive restart");checks.add("settings survived restart");c.setScreen(null);c.player.networkHandler.sendChatCommand("chronicle banquet trial exercise");step=10;return;}
                require(view.config().equals(BanquetConfig.defaults()),"unexpected initial defaults"); c.getWindow().setWindowedSize(1280,720);wait=20;step=4;}
            case 4->{ScreenshotRecorder.saveScreenshot(shared.toFile(),"banquet-normal.png",c.getFramebuffer(),1,ignored->{});press(c.currentScreen,"Settings...");press(c.currentScreen,"Banquet: Disabled");press(c.currentScreen,"Save");require(enabled(c.currentScreen,"Settings...")&&enabled(c.currentScreen,"Give supplies..."),"pending save disabled existing buttons");checks.add("pending save leaves existing menu buttons enabled without flicker");step=5;}
            case 5->{if(!(c.currentScreen instanceof BanquetScreen screen)||!viewOf(screen).config().enabled())return;require(viewOf(screen).config().equals(new BanquetConfig(true,2,8)),"unexpected saved settings");c.player.networkHandler.sendChatCommand("chronicle banquet trial change_settings");wait=15;step=40;}
            case 40->{clock("change_settings");press(c.currentScreen,"Settings...");press(c.currentScreen,"Save");step=41;}
            case 41->{if(!(c.currentScreen instanceof BanquetScreen screen)||!viewOf(screen).config().equals(new BanquetConfig(true,3,9)))return;press(screen,"Settings...");var fields=c.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.TextFieldWidget.class::isInstance).map(net.minecraft.client.gui.widget.TextFieldWidget.class::cast).toList();require(fields.size()==2&&fields.get(0).getText().equals("3")&&fields.get(1).getText().equals("9"),"stale settings draft was reused with a fresh revision");fields.get(0).setText("2");fields.get(1).setText("8");wait=5;step=43;}
            case 43->{press(c.currentScreen,"Save");step=42;}
            case 42->{if(!(c.currentScreen instanceof BanquetScreen screen)||!viewOf(screen).config().equals(new BanquetConfig(true,2,8)))return;checks.add("rejected stale save reloads current server settings before another edit");c.getWindow().setWindowedSize(640,360);wait=20;step=6;}
            case 6->{c.currentScreen.resize(c,320,180);wait=10;step=7;}
            case 7->{ScreenshotRecorder.saveScreenshot(shared.toFile(),"banquet-compact.png",c.getFramebuffer(),1,ignored->{});checks.add("operator saved enabled settings through native controls; no Chancellor log exposed");c.setScreen(null);c.player.networkHandler.sendChatCommand("chronicle editing off");wait=30;step=8;}
            case 8->{c.player.networkHandler.sendChatCommand("chronicle players join");wait=30;step=9;}
            case 9->{c.player.networkHandler.sendChatCommand("chronicle session start");wait=80;step=11;}
            case 11->{c.player.networkHandler.sendChatCommand("chronicle banquet trial exercise");step=10;}
            case 10->{Path report=shared.resolve(phase+"-trial.json");if(!Files.isRegularFile(report))return;var result=com.google.gson.JsonParser.parseString(Files.readString(report)).getAsJsonObject();require(result.get("result").getAsString().equals("PASS"),"server item trial failed: "+result);checks.add(phase.equals("initial")?"actual gift, offhand poison, full-hunger use, refresh and cure; onset accelerated for symptoms":"illness and receipts survived restart");mark("trial-complete");step=12;}
            case 12->{if(!has("log-B"))return;if(phase.equals("initial")){c.options.useKey.setPressed(true);wait=50;step=14;}else step=13;}
            case 14->{c.options.useKey.setPressed(false);require(c.player.getMainHandStack().isOf(net.minecraft.item.Items.GLASS_BOTTLE),"real drinking animation did not consume the issued drink");checks.add("32-tick drink use completed through real client input at full hunger");BanquetGiftTrial.start(c);giftTrial=true;}
            case 15->{c.player.networkHandler.sendChatCommand("chronicle banquet trial clock_advance");wait=15;step=16;}
            case 16->{clock("clock_advance");c.player.networkHandler.sendChatCommand("chronicle session pause");wait=20;step=17;}
            case 17->{c.player.networkHandler.sendChatCommand("chronicle banquet trial clock_start");wait=60;step=18;}
            case 18->{c.player.networkHandler.sendChatCommand("chronicle banquet trial clock_held");wait=15;step=19;}
            case 19->{clock("clock_held");Files.delete(shared.resolve(phase+"-trial.json.clock_held"));c.player.networkHandler.sendChatCommand("chronicle banquet trial build_on");wait=60;step=20;}
            case 20->{c.player.networkHandler.sendChatCommand("chronicle banquet trial clock_held");wait=15;step=21;}
            case 21->{clock("clock_held");checks.add("real active ticks advance illness; technical pause and Building Mode hold it");c.player.networkHandler.sendChatCommand("chronicle banquet trial build_off");wait=15;step=22;}
            case 22->{c.player.networkHandler.sendChatCommand("chronicle session resume");wait=30;step=23;}
            case 23->{c.player.networkHandler.sendChatCommand("chronicle banquet trial death_prepare");wait=30;step=24;}
            case 24->{clock("death_prepare");require(!c.player.isAlive(),"fixture death did not occur");c.player.requestRespawn();wait=40;step=25;}
            case 25->{require(c.player.isAlive(),"fixture respawn failed");c.player.networkHandler.sendChatCommand("chronicle banquet trial death_check");wait=15;step=26;}
            case 26->{clock("death_check");checks.add("illness survived real death and respawn without refresh");step=13;}
            case 13->{mark("done-A");finish(c,null);}
        }
    }
    private void clock(String mode)throws Exception{Path path=shared.resolve(phase+"-trial.json."+mode);require(Files.isRegularFile(path)&&Files.readString(path).equals("PASS"),"clock test failed: "+mode+(Files.exists(path)?Files.readString(path):" missing"));}
    private String illnesses()throws Exception{return com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getenv("CHRONICLE_BANQUET_ROOT")).resolve("world/chronicle/last-council/banquet.json"))).getAsJsonObject().get("illnesses").toString();}
    private static View viewOf(BanquetScreen screen)throws Exception{var f=BanquetScreen.class.getDeclaredField("view");f.setAccessible(true);return (View)f.get(screen);}
    private static void press(Screen screen,String text){var button=screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(b->b.active&&b.getMessage().getString().equals(text)).findFirst().orElseThrow();button.onPress(new MouseInput(0,0));}
    private static boolean enabled(Screen screen,String text){return screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).anyMatch(b->b.active&&b.getMessage().getString().equals(text));}
    private void finish(MinecraftClient c,String error){if(done)return;done=true;try{var result=new JsonObject();result.addProperty("result",error==null?"PASS":"FAIL");result.addProperty("detail",error==null?"Completed":error);result.add("checks",new Gson().toJsonTree(checks));Files.writeString(output,new Gson().toJson(result),StandardCharsets.UTF_8);}catch(Exception ignored){}c.scheduleStop();}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
