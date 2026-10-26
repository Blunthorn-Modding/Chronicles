package io.github.chronicle.core.client.testing;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.menu.CoreMenuModel.Page;
import io.github.chronicle.core.menu.CoreMenuModel.View;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Small real-client CF19 navigation/creation probe; server-side trial proves runtime receipts. */
public final class PlayerRolesSmoke {
    private static final String OUTPUT = System.getenv("CHRONICLE_ROLES_SMOKE"), ROOT = System.getenv("CHRONICLE_ROLES_SERVER_DIR"), ADDRESS = "127.0.0.1:25575";
    private static final boolean RESTART="true".equals(System.getenv("CHRONICLE_ROLES_RESTART"));
    private static PlayerRolesSmoke instance;
    private final Path output, root; private final List<String> checks = new ArrayList<>(), messages = new ArrayList<>();
    private boolean connecting, done; private long began = System.nanoTime(); private int step, wait;
    private PlayerRolesSmoke() {
        output=Path.of(OUTPUT).toAbsolutePath().normalize();root=Path.of(ROOT).toAbsolutePath().normalize();
        if(!Files.isRegularFile(root.resolve(".player-roles-fixture")))throw new IllegalArgumentException("refusing role smoke outside its disposable fixture");
        if(output.startsWith(root))throw new IllegalArgumentException("role evidence must be outside fixture");
    }
    public static void register() { if(OUTPUT==null)return;try { if(ROOT==null)throw new IllegalArgumentException("CHRONICLE_ROLES_SERVER_DIR is required"); instance=new PlayerRolesSmoke();ClientReceiveMessageEvents.GAME.register((m,o)->{if(!o)instance.messages.add(m.getString());});ClientTickEvents.END_CLIENT_TICK.register(instance::tick); }catch(Exception e){ChronicleCore.LOGGER.error("Roles smoke setup failed",e);} }
    public static void onView(View view) { }
    private void tick(MinecraftClient client) { if(done)return;try {
        if(System.nanoTime()-began>120_000_000_000L)throw new IllegalStateException("roles smoke timeout step "+step);
        if(client.player==null&&client.getOverlay()==null&&client.currentScreen instanceof AccessibilityOnboardingScreen)client.setScreen(new TitleScreen());
        if(!connecting&&client.player==null&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen){connecting=true;ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(ADDRESS),new ServerInfo("Roles smoke",ADDRESS,ServerInfo.ServerType.OTHER),false,null);return;}
        if(client.player==null)return;client.getToastManager().clear();if(client.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen){client.player.requestRespawn();client.setScreen(null);return;}if(step==0){client.player.networkHandler.sendChatCommand("chronicles");step=RESTART?200:1;return;}
        CoreMenuScreen menu=client.currentScreen instanceof CoreMenuScreen m?m:null;if(java.util.Set.of(1,2,3,5,6,9,12,13,14,15,16,30,200,201,203).contains(step)&&(menu==null||menu.pending()))return;
        if(step==1){require(menu.triggerForTest("Player Roles"),"missing direct Player Roles navigation");step++;return;}
        if(step==2){require(menu.state().page()==Page.PLAYER_ROLES,"Player Roles did not open");require(menu.triggerForTest("Enable editing"),"cannot enable authoring");step++;return;}
        if(step==3){if(!menu.state().editing())return;var widgets=menu.visibleWidgetBoundsForTest();require(widgets.stream().filter(v->v.label().equals("Create new role")).count()==1,"Create new role appears more than once");var create=widgets.stream().filter(v->v.label().equals("Create new role")).findFirst().orElseThrow();var first=widgets.stream().filter(v->v.label().equals(menu.state().entries().getFirst().label())).findFirst().orElseThrow();require(create.y()<first.y(),"Create is not above the role list");wait=4;step=30;return;}
        if(step==30){if(wait-->0)return;capture(client,"menu");require(menu.triggerForTest("Create new role"),"Create role unavailable");step=4;return;}
        if(step==4){if(client.currentScreen==null||!client.currentScreen.getClass().getSimpleName().equals("PlayerRoleEditorScreen"))return;require(!hasButton(client,"Create new role"),"create action leaked into editor detail");var fields=client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).toList();require(!fields.isEmpty(),"role editor has no name field");fields.getFirst().setText("Smoke role");wait=4;step=40;return;}
        if(step==40){if(wait-->0)return;capture(client,"editor");press(client,"Create role");step=5;return;}
        if(step==5){if(menu.state().page()!=Page.PLAYER_ROLES||menu.pending())return;require(menu.state().detail()!=null&&menu.state().detail().title().equals("Smoke role"),"created role did not round-trip");checks.add("native Create role saved through normal menu transport");require(menu.triggerForTest("Players"),"Players navigation missing");step++;return;}
        if(step==6){if(menu.state().page()!=Page.PLAYERS||menu.pending())return;require(menu.triggerForTest("Assign player role…"),"GM assignment action missing");step++;return;}
        if(step==7){if(client.currentScreen==null||!client.currentScreen.getClass().getSimpleName().equals("PlayerRoleChoiceScreen"))return;press(client,"Smoke role (0 / 1)");step++;return;}
        if(step==8){if(client.currentScreen==null||!client.currentScreen.getClass().getSimpleName().equals("PlayerRoleChoiceScreen"))return;press(client,"Assign role");step++;return;}
        if(step==9){if(menu.pending()||menu.state().detail()==null||!menu.state().detail().facts().stream().anyMatch(f->f.value().equals("Smoke role")))return;checks.add("GM role assignment used reviewed confirmation");require(menu.triggerForTest("Assign player role…"),"GM clear assignment reopen missing");step++;return;}
        if(step==10){if(client.currentScreen==null||!client.currentScreen.getClass().getSimpleName().equals("PlayerRoleChoiceScreen"))return;press(client,"No role (0 / 512)");step++;return;}
        if(step==11){if(client.currentScreen==null||!client.currentScreen.getClass().getSimpleName().equals("PlayerRoleChoiceScreen"))return;press(client,"Clear role");step++;return;}
        if(step==12){if(menu.pending()||menu.state().detail()==null)return;require(menu.triggerForTest("Player Roles"),"role list return missing");step++;return;}
        if(step==13){if(menu.state().page()!=Page.PLAYER_ROLES||menu.pending())return;menu.selectForTest(menu.state().entries().stream().filter(e->e.label().equals("Smoke role")).findFirst().orElseThrow().id());step++;return;}
        if(step==14){if(menu.pending()||menu.state().detail()==null||!menu.state().detail().title().equals("Smoke role"))return;require(menu.triggerForTest("Delete role"),"clear role was not deletable");step++;return;}
        if(step==15){require(menu.triggerForTest("Confirm: Delete role"),"delete confirmation missing");step++;return;}
        if(step==16){if(menu.pending())return;checks.add("assigned role was cleared before confirmed deletion");client.player.networkHandler.sendChatCommand("chronicle_roles_trial prepare");step++;return;}
        if(step==17){if(!passed("prepare"))return;client.player.networkHandler.sendChatCommand("chronicle_roles_trial full");step++;return;}
        if(step==18){if(!passed("full"))return;wait=10;step++;return;}
        if(step==19){if(wait-->0)return;client.player.networkHandler.sendChatCommand("chronicle_roles_trial uncertain");step=24;return;}
        if(step==24){if(!passed("uncertain"))return;wait=20;step++;return;}
        if(step==25){if(wait-->0)return;client.player.networkHandler.sendChatCommand("chronicle_roles_trial verify_uncertain");step++;return;}
        if(step==26){if(!passed("verify_uncertain"))return;checks.add("unresolved prior movement claim deferred kit and book discovery despite free inventory");client.player.networkHandler.sendChatCommand("chronicle_roles_trial release");step=20;return;}
        if(step==20){if(!passed("release"))return;wait=30;step++;return;}
        if(step==21){if(wait-->0)return;client.player.networkHandler.sendChatCommand("chronicle_roles_trial verify");step++;return;}
        if(step==22){if(!passed("verify"))return;client.player.networkHandler.sendChatCommand("chronicle_roles_trial repeat");step++;return;}
        if(step==23){if(!passed("repeat"))return;checks.add("full-fit deferral, direct role Point movement, named-book discovery and once-only supplies verified");client.player.networkHandler.sendChatCommand("chronicle_roles_trial death");step=41;return;}
        if(step==41){if(!passed("death"))return;wait=30;step++;return;}
        if(step==42){if(wait-->0)return;client.player.networkHandler.sendChatCommand("chronicle_roles_trial verify_death");step++;return;}
        if(step==43){if(!passed("verify_death"))return;checks.add("real death applied role respawn Point without another kit or personal book");client.player.networkHandler.sendChatCommand("chronicle_roles_trial manual_respawn");step++;return;}
        if(step==44){if(!passed("manual_respawn"))return;wait=30;step++;return;}
        if(step==45){if(wait-->0)return;client.player.networkHandler.sendChatCommand("chronicle_roles_trial verify_manual");step++;return;}
        if(step==46){if(!passed("verify_manual"))return;checks.add("manual spawn policy took priority over role respawn Point");client.player.networkHandler.sendChatCommand("chronicle_roles_trial off_respawn");step++;return;}
        if(step==47){if(!passed("off_respawn"))return;wait=30;step++;return;}
        if(step==48){if(wait-->0)return;client.player.networkHandler.sendChatCommand("chronicle_roles_trial verify_off");step++;return;}
        if(step==49){if(!passed("verify_off"))return;checks.add("explicit Off retained vanilla spawn instead of falling back to the role");finish(client,true,"completed roles and real death/override checks");}
        if(step==200){require(menu.triggerForTest("Player Roles"),"restart roles navigation missing");step++;return;}
        if(step==201){if(menu.state().page()!=Page.PLAYER_ROLES)return;wait=4;step=203;return;}
        if(step==203){if(wait-->0)return;capture(client,"menu");client.player.networkHandler.sendChatCommand("chronicle_roles_trial restart");step=202;return;}
        if(step==202){if(!passed("restart"))return;checks.add("real restart retained explicit Off, vanilla position, original book discovery and start receipt without replay");finish(client,true,"restart and narrow role menu passed");}
    }catch(Throwable e){finish(client,false,e.getClass().getSimpleName()+": "+e.getMessage());}}
    private boolean passed(String verb){for(String value:messages){if(value.contains("Roles trial FAIL "+verb))throw new IllegalStateException(value);if(value.contains("Roles trial PASS "+verb))return true;}return false;}
    private static boolean hasButton(MinecraftClient c,String label){return c.currentScreen.children().stream().anyMatch(x->x instanceof ButtonWidget b&&b.getMessage().getString().equals(label));}
    private static void press(MinecraftClient c,String label){for(var child:c.currentScreen.children())if(child instanceof ButtonWidget b&&b.getMessage().getString().equals(label)){b.onPress(new net.minecraft.client.input.MouseInput(0,0));return;}throw new IllegalStateException("button missing: "+label);}
    private void capture(MinecraftClient c,String kind){ScreenshotRecorder.saveScreenshot(output.getParent().toFile(),"roles-"+kind+"-"+c.getWindow().getScaledWidth()+"x"+c.getWindow().getScaledHeight()+".png",c.getFramebuffer(),1,x->{});}
    private void finish(MinecraftClient c,boolean pass,String detail){if(done)return;done=true;try{Files.createDirectories(output.getParent());JsonObject v=new JsonObject();v.addProperty("result",pass?"PASS":"FAIL");v.addProperty("detail",detail);JsonArray a=new JsonArray();checks.forEach(a::add);v.add("checks",a);Files.writeString(output,new Gson().toJson(v));}catch(Exception ignored){}c.scheduleStop();}
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException(text);}
}
