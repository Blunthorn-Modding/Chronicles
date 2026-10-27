package io.github.chronicle.core.client.testing;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.menu.CoreMenuModel.Page;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** One-client disposable proof for the actual Area and Departure popups. */
public final class CatalogsSmoke {
    private static final String OUTPUT = System.getenv("CHRONICLE_CATALOGS_SMOKE"), ROOT = System.getenv("CHRONICLE_CATALOGS_SERVER_DIR"), ADDRESS = "127.0.0.1:25575";
    private static CatalogsSmoke instance;
    private final Path output; private final List<String> checks = new ArrayList<>(), messages = new ArrayList<>();
    private final long began = System.nanoTime(); private boolean connecting, done; private int step, wait,stableTicks;private Object captureScreen;
    private CatalogsSmoke() {
        output = Path.of(OUTPUT).toAbsolutePath().normalize(); Path root = Path.of(ROOT).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve(".catalogs-fixture"))) throw new IllegalArgumentException("refusing catalog smoke outside disposable fixture");
        if (output.startsWith(root)) throw new IllegalArgumentException("catalog evidence must be outside fixture");
    }
    public static void register() { if (OUTPUT == null) return; try {
        if (ROOT == null) throw new IllegalArgumentException("CHRONICLE_CATALOGS_SERVER_DIR is required"); instance = new CatalogsSmoke();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay) instance.messages.add(message.getString()); });
        ClientTickEvents.END_CLIENT_TICK.register(instance::tick);
    } catch (Exception error) { ChronicleCore.LOGGER.error("Catalogs smoke setup failed", error); } }
    private void tick(MinecraftClient client) { if (done) return; try {
        if (System.nanoTime() - began > 240_000_000_000L) throw new IllegalStateException("catalog smoke timeout step " + step);
        if (client.player == null && client.getOverlay() == null && client.currentScreen instanceof AccessibilityOnboardingScreen) client.setScreen(new TitleScreen());
        if (!connecting && client.player == null && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) { connecting = true; ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS), new ServerInfo("Catalog smoke", ADDRESS, ServerInfo.ServerType.OTHER), false, null); return; }
        if (client.player == null) return; client.getToastManager().clear(); drive(client);
    } catch (Throwable error) { finish(client, false, error.getClass().getSimpleName() + ": " + error.getMessage()); } }
    private void drive(MinecraftClient client) {
        if (step == 0) { command(client, "chronicle_catalogs_trial prepare"); step++; return; }
        if (step == 1) { if (!passed("prepare")) return; command(client, "chronicles"); step++; return; }
        CoreMenuScreen menu = client.currentScreen instanceof CoreMenuScreen value ? value : null;
        if (step == 2) { if (menu == null || menu.pending()) return; require(menu.triggerForTest("Area changes"), "Area changes navigation missing"); step++; return; }
        if (step == 3) { if (menu == null || menu.pending() || menu.state().page() != Page.AREA_CHANGES) return; require(menu.triggerForTest("Create new area"), "empty-list Create new area missing"); wait = 4; step++; return; }
        if (step == 4) { if (wait-- > 0) return; require(simple(client, "AreaCreateScreen"), "native Area create popup did not open"); require(client.getWindow().getScaledWidth()==Integer.parseInt(System.getenv("CHRONICLE_MENU_WIDTH"))/2&&client.getWindow().getScaledHeight()==Integer.parseInt(System.getenv("CHRONICLE_MENU_HEIGHT"))/2,"unexpected logical viewport"); text(client, 0, "Menu sample"); capture(client, "area-create"); click(client, "Begin with End"); step++; return; }
        if (step == 5) { if (client.currentScreen!=null||!seen("Area change 'Menu sample' started with END.")) return; checks.add("actual Area create popup submitted End-first Menu sample and closed to the world after server acknowledgement"); client.interactionManager.attackBlock(new BlockPos(4,64,4), Direction.UP); wait = 4; step++; return; }
        if (step == 6) { if (wait-- > 0) return; var second = new BlockPos(5,64,4); client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(second), Direction.UP, second, false)); wait = 4; step++; return; }
        if (step == 7) { if (wait-- > 0) return; command(client, "chronicles area change save"); step++; return; }
        if (step == 8) { if (!seen("Saved Area change 'Menu sample' with its END state.")) return; command(client, "chronicles"); step++; return; }
        if (step == 9) { if (menu == null || menu.pending()) return; require(menu.triggerForTest("Area changes"), "Area navigation was not restored"); step++; return; }
        if (step == 10) { if (menu == null || menu.pending() || menu.state().page() != Page.AREA_CHANGES) return; menu.selectForTest(menu.state().entries().stream().filter(e -> e.label().equals("Menu sample")).findFirst().orElseThrow().id()); step++; return; }
        if (step == 11) { if (menu == null || menu.pending() || menu.state().detail() == null) return; require(menu.triggerForTest("Preview saved state"), "Area preview control missing"); wait=4; step++; return; }
        if (step == 12) { if (wait-- > 0) return; require(simple(client,"AreaStateScreen"), "native Area state picker did not open"); ButtonWidget start=button(client,"Start"), end=button(client,"End"); require(!start.active && end.active, "missing Start was not disabled while End was enabled"); capture(client,"area-state-picker"); click(client,"Cancel"); checks.add("native state picker disabled missing Start and enabled saved End"); step++; return; }
        if (step == 13) { if (!(client.currentScreen instanceof CoreMenuScreen)) return; require(((CoreMenuScreen)client.currentScreen).triggerForTest("Save current area state"), "Area capture control missing"); step++; return; }
        if (step == 14) { if (!simple(client,"AreaStateScreen")) return; click(client,"Start"); step++; return; }
        if (step == 15) { if (!(client.currentScreen instanceof CoreMenuScreen) || ((CoreMenuScreen)client.currentScreen).pending() || !seen("Saved current area as START for 'Menu sample'.")) return; checks.add("actual state picker saved the previously missing Start with its private acknowledgement"); require(((CoreMenuScreen)client.currentScreen).triggerForTest("Departures"), "Departures navigation missing"); step++; return; }
        if (step == 16) { if (menu == null || menu.pending() || menu.state().page()!=Page.DEPARTURES) return; require(menu.triggerForTest("Create new departure"), "empty-list Create new departure missing"); step++; return; }
        if (step == 17) { if (!simple(client,"DepartureEditorScreen")) return; text(client,0,"Menu departure");if(!settled(client))return; capture(client,"departure-editor"); click(client,"Block site:"); step++; return; }
        if (step == 18) { if (!simple(client,"DepartureChoiceScreen")) return; if(!settled(client))return;capture(client,"block-site-selector"); click(client,"catalog_helm"); step++; return; }
        if (step == 19) { if (!simple(client,"DepartureEditorScreen")) return; click(client,"Region:"); step++; return; }
        if (step == 20) { if (!simple(client,"DepartureChoiceScreen")) return; if(!settled(client))return;capture(client,"region-selector"); click(client,"catalog_boarding"); step++; return; }
        if (step == 21) { if (!simple(client,"DepartureEditorScreen")) return; click(client,"Area:"); step++; return; }
        if (step == 22) { if (!simple(client,"DepartureChoiceScreen")) return; if(!settled(client))return;capture(client,"area-selector"); click(client,"Menu sample"); step++; return; }
        if (step == 23) { if (!simple(client,"DepartureEditorScreen")) return; require(textValue(client,0).equals("Menu departure"), "departure draft name did not survive paged selectors"); click(client,"State: start"); require(textValue(client,0).equals("Menu departure"), "departure draft name did not survive state switch"); click(client,"Cancel"); step++; return; }
        if (step == 24) { if (!(client.currentScreen instanceof CoreMenuScreen)) return; require(((CoreMenuScreen)client.currentScreen).triggerForTest("Create new departure"), "departure editor cancel did not return to menu"); step++; return; }
        if (step == 25) { if (!simple(client,"DepartureEditorScreen")) return; text(client,0,"Menu departure"); click(client,"Block site:"); step++; return; }
        if (step == 26) { if (!simple(client,"DepartureChoiceScreen")) return; click(client,"catalog_helm"); step++; return; }
        if (step == 27) { if (!simple(client,"DepartureEditorScreen")) return; click(client,"Region:"); step++; return; }
        if (step == 28) { if (!simple(client,"DepartureChoiceScreen")) return; click(client,"catalog_boarding"); step++; return; }
        if (step == 29) { if (!simple(client,"DepartureEditorScreen")) return; click(client,"Area:"); step++; return; }
        if (step == 30) { if (!simple(client,"DepartureChoiceScreen")) return; click(client,"Menu sample"); step++; return; }
        if (step == 31) { if (!simple(client,"DepartureEditorScreen")) return; click(client,"State: start"); require(textValue(client,0).equals("Menu departure"), "draft name did not survive final End selection");wait=4;step=311;return; }
        if(step==311){if(wait-->0)return;capture(client,"departure-editor-complete");click(client,"Save");step=32;return;}
        if (step == 32) { if (!(client.currentScreen instanceof CoreMenuScreen) || ((CoreMenuScreen)client.currentScreen).pending()) return; command(client,"chronicle_catalogs_trial verify"); step++; return; }
        if (step == 33) { if (!passed("verify")) return; checks.add("actual Departure editor persisted name, catalog_helm, catalog_boarding, Menu sample and End through real Save with Building Mode retained");menu.selectForTest(menu.state().selected());step=331;return; }
        if(step==331){if(menu==null||menu.pending())return;require(menu.triggerForTest("Edit departure"),"existing Departure edit unavailable");step=34;return;}
        if(step==34){if(!simple(client,"DepartureEditorScreen"))return;text(client,0,"Edited departure");click(client,"Save");step++;return;}
        if(step==35){if(menu==null||menu.pending())return;command(client,"chronicle_catalogs_trial verify_edit");step++;return;}
        if(step==36){if(!passed("verify_edit"))return;checks.add("actual existing Departure editor Save advanced one revision without losing settings or Building Mode");finish(client,true,"catalog popup forms and selectors passed"); }
    }
    private boolean settled(MinecraftClient client){if(captureScreen!=client.currentScreen){captureScreen=client.currentScreen;stableTicks=0;return false;}return ++stableTicks>=4;}
    private static boolean simple(MinecraftClient client,String name){return client.currentScreen!=null&&client.currentScreen.getClass().getSimpleName().equals(name);}
    private static List<TextFieldWidget> fields(MinecraftClient client){return client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).toList();}
    private static void text(MinecraftClient client,int index,String value){fields(client).get(index).setText(value);} private static String textValue(MinecraftClient client,int index){return fields(client).get(index).getText();}
    private static ButtonWidget button(MinecraftClient client,String prefix){return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(value->value.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow(()->new IllegalStateException("button missing: "+prefix));}
    private static void click(MinecraftClient client,String prefix){ButtonWidget value=button(client,prefix); require(value.active,"button is disabled: "+prefix); value.onPress(new net.minecraft.client.input.MouseInput(0,0));}
    private boolean passed(String action){for(String value:messages){if(value.contains("Catalogs trial FAIL "+action))throw new IllegalStateException(value);if(value.contains("Catalogs trial PASS "+action))return true;}return false;}
    private boolean seen(String text){return messages.stream().anyMatch(value->value.contains(text));} private static void command(MinecraftClient client,String command){client.player.networkHandler.sendChatCommand(command);}
    private void capture(MinecraftClient client,String kind){ScreenshotRecorder.saveScreenshot(output.getParent().toFile(),"catalogs-"+kind+"-"+client.getWindow().getScaledWidth()+"x"+client.getWindow().getScaledHeight()+".png",client.getFramebuffer(),1,text->{});}
    private void finish(MinecraftClient client,boolean pass,String detail){if(done)return;done=true;try{Files.createDirectories(output.getParent());JsonObject value=new JsonObject();value.addProperty("result",pass?"PASS":"FAIL");value.addProperty("detail",detail);JsonArray array=new JsonArray();checks.forEach(array::add);value.add("checks",array);Files.writeString(output,new Gson().toJson(value));}catch(Exception ignored){}client.scheduleStop();}
    private static void require(boolean condition,String text){if(!condition)throw new IllegalStateException(text);}
}
