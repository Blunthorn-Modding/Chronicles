package io.github.chronicle.core.client.testing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.client.CatalogAddPopup;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.client.EventEditorScreen;
import io.github.chronicle.core.menu.CoreMenuModel.Page;
import io.github.chronicle.core.menu.CoreMenuModel.View;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Disposable, real-client proof for the CF22 Event editor.  It deliberately drives the
 * normal menu protocol and catalog popup instead of constructing DTOs or screens itself.
 */
public final class EventEditorSmoke {
    private static final String OUTPUT_ENV = System.getenv("CHRONICLE_EVENT_EDITOR_SMOKE");
    private static final String SERVER_ENV = System.getenv("CHRONICLE_EVENT_EDITOR_SERVER_DIR");
    private static final long DEADLINE = 240_000_000_000L;
    private static EventEditorSmoke instance;

    private final Path output, serverRoot;
    private final List<String> checks = new ArrayList<>();
    private final List<String> chat = new ArrayList<>();
    private final long deadline = System.nanoTime() + DEADLINE;
    private long views, waitViews;
    private int step, ticks;
    private boolean complete;
    private boolean cancellationIssued, cancellationVerified;

    private EventEditorSmoke(Path output, Path serverRoot) throws IOException {
        this.output = output.toAbsolutePath().normalize(); this.serverRoot = serverRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.serverRoot) || this.output.startsWith(this.serverRoot)) throw new IllegalArgumentException("Event editor smoke requires isolated paths");
        Files.createDirectories(this.output);
    }

    public static void register() {
        if (OUTPUT_ENV == null) return;
        try {
            if (SERVER_ENV == null) throw new IllegalArgumentException("CHRONICLE_EVENT_EDITOR_SERVER_DIR is required");
            instance = new EventEditorSmoke(Path.of(OUTPUT_ENV), Path.of(SERVER_ENV));
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay && !instance.complete) instance.chat.add(message.getString()); });
            ClientTickEvents.END_CLIENT_TICK.register(instance::tick);
        } catch (IOException | RuntimeException error) { setupFailure(error); MinecraftClient.getInstance().scheduleStop(); }
    }

    /** Called before normal client menu dispatch. */
    public static void onView(View value) { if (instance != null && !instance.complete) instance.views++; }

    private void tick(MinecraftClient client) {
        if (complete) return;
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("event editor smoke timed out at step " + step);
            if (client.player == null && client.getOverlay() == null && client.currentScreen instanceof AccessibilityOnboardingScreen) client.setScreen(new TitleScreen());
            if (client.player == null && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                String address = "127.0.0.1:25575";
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(address), new ServerInfo("Event editor smoke", address, ServerInfo.ServerType.OTHER), false, null);
                return;
            }
            if (client.player == null || ++ticks % 3 != 0) return;
            requireFixture(client);
            if (client.currentScreen instanceof CoreMenuScreen menu && menu.pending()) return;
            drive(client);
        } catch (Throwable error) { finish(client, false, error.getClass().getSimpleName() + ": " + error.getMessage()); }
    }

    private void drive(MinecraftClient client) {
        switch (step) {
            case 0 -> { client.player.networkHandler.sendChatCommand("chronicle_events_trial prepare_ui"); step++; }
            case 1 -> { if (chat.stream().noneMatch(message -> message.contains("Events trial PASS prepare_ui"))) return; client.player.networkHandler.sendChatCommand("chronicle"); step++; }
            case 2 -> {
                if (!(client.currentScreen instanceof CoreMenuScreen menu) || menu.pending() || menu.state().page()!=Page.SESSION) return;
                require(!menu.debugForTest(), "fresh Event editor client did not begin in Clean mode");
                menu.toggleDebugForTest(); require(menu.debugForTest(), "Debug toggle did not enable");
                menu.toggleDebugForTest(); require(!menu.debugForTest(), "Debug toggle did not return to Clean");
                checks.add("opened native Chronicle menu"); checks.add("Clean and Debug remain separate from Event editor authority"); step++;
            }
            case 3 -> { if (menu(client).state().editing()) { checks.add("fixture retained authoritative Editing"); step++; } else { require(menu(client).triggerForTest("Enable editing"), "editing control is unavailable"); waitViews = views; step++; } }
            case 4 -> await(() -> menu(client).state().editing(), "server acknowledged Editing");
            case 5 -> { require(menu(client).triggerForTest("Events"), "Events tab unavailable"); waitViews = views; step++; }
            case 6 -> await(() -> menu(client).state().page() == Page.EVENTS, "opened Events");
            case 7 -> { require(press(menu(client), "Create new event"), "Create new event unavailable"); waitViews = views; step++; }
            case 8 -> {
                if (!cancellationIssued) {
                    if (!(client.currentScreen instanceof EventEditorScreen editor)) return;
                    editor.setDraftForTest("id", "chronicle_core:cancelled_event");
                    require(editor.pressForTest("Cancel"), "local Event editor Cancel is unavailable"); cancellationIssued = true; return;
                }
                if (!cancellationVerified) {
                    if (!(client.currentScreen instanceof CoreMenuScreen menu)) return;
                    require(menu.state().entries().stream().noneMatch(entry -> entry.id().contains("cancelled_event")), "Cancel wrote an Event definition");
                    require(press(menu, "Create new event"), "Create new event unavailable after local Cancel"); cancellationVerified = true; return;
                }
                if (!(client.currentScreen instanceof EventEditorScreen editor)) return;
                require("BASICS".equals(editor.pageForTest()), "new Event editor did not open on Basics");
                frame(client, editor); editor.setDraftForTest("name", "Smoke Event");
                require(editor.pressForTest("Message & sound"), "Message & sound page button unavailable");
                checks.add("Create opens the native four-page Event editor and Cancel remains local without a write"); step++;
            }
            case 9 -> {
                if (!(client.currentScreen instanceof EventEditorScreen editor) || !"MEDIA".equals(editor.pageForTest())) return;
                editor.setDraftForTest("message", "A private smoke preview."); require(editor.pressForTest("Preview privately"), "private preview button unavailable before trigger configuration"); waitViews = views; step++;
            }
            case 10 -> awaitEditor(client, "MEDIA", "media preview works before audience and trigger are configured");
            case 11 -> { EventEditorScreen editor=editor(client); require(editor.pressForTest("Trigger"), "Trigger page unavailable"); step++; }
            case 12 -> {
                EventEditorScreen editor=editor(client); if (!"TRIGGER".equals(editor.pageForTest())) return;
                require(pressPrefix(editor, "Optional flag"), "optional flag picker is unavailable for Manual trigger"); step++;
            }
            case 13 -> selectPopup(client, "quiet_below:m8_gate", "selected a registered optional prerequisite flag");
            case 14 -> { EventEditorScreen editor=editor(client); require(editor.pressForTest("Region changes"), "Region changes page unavailable"); step++; }
            case 15 -> { EventEditorScreen editor=editor(client); if (!"CHANGES".equals(editor.pageForTest())) return; require(pressPrefix(editor, "Add Saved state"), "Saved-state picker unavailable"); step++; }
            case 16 -> selectPopup(client, "event_region_a|START", "added first Region Saved state through shared picker");
            case 17 -> { EventEditorScreen editor=editor(client); require(pressPrefix(editor, "Add Saved state"), "second Saved-state picker unavailable"); step++; }
            case 18 -> selectPopup(client, "event_region_b|START", "added second Region Saved state");
            case 19 -> { EventEditorScreen editor=editor(client); require(pressPrefix(editor, "Add Saved state"), "duplicate Saved-state picker unavailable"); step++; }
            case 20 -> duplicatePopupRefused(client);
            case 21 -> { EventEditorScreen editor=editor(client); require(editor.pressForTest("Remove"), "Saved-state removal unavailable"); checks.add("duplicate Region target is refused and a target can be removed"); step++; }
            case 22 -> { EventEditorScreen editor=editor(client); require(editor.pressForTest("Basics"), "Basics page unavailable"); step++; }
            case 23 -> { EventEditorScreen editor=editor(client); editor.setDraftForTest("id", "chronicle_core:smoke_event"); require(editor.pressForTest("Create event"), "Create event action unavailable"); waitViews=views; step++; }
            case 24 -> {
                if (!(client.currentScreen instanceof CoreMenuScreen menu) || views <= waitViews) return;
                require(menu.state().page() == Page.EVENTS && menu.state().selected().contains("smoke_event"), "successful save did not return to selected Event detail");
                checks.add("native create/save returns to the updated selected Event without reopening a stale editor");
                capture(client, "event-editor-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png"); finish(client, true, "completed " + checks.size() + " native Event editor checks");
            }
            default -> throw new IllegalStateException("unexpected Event editor step " + step);
        }
    }

    private void selectPopup(MinecraftClient client, String id, String check) {
        if (!(client.currentScreen instanceof CatalogAddPopup popup)) return;
        var search = popup.children().stream().filter(net.minecraft.client.gui.widget.TextFieldWidget.class::isInstance).map(net.minecraft.client.gui.widget.TextFieldWidget.class::cast).findFirst().orElseThrow();
        if (!search.getText().equals(id)) { search.setText(id); pressPopup(popup, "Find"); return; }
        String label = pickerLabel(popup, id);
        var button = popup.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast)
                .filter(value -> value.getMessage().getString().contains(label)).findFirst();
        if (button.isEmpty()) { // The catalog is paged: search first, then wait for its reply.
            return;
        }
        button.get().onPress(new net.minecraft.client.input.MouseInput(0, 0)); pressPopup(popup, "Add"); checks.add(check); step++;
    }

    private void duplicatePopupRefused(MinecraftClient client) {
        if (!(client.currentScreen instanceof CatalogAddPopup popup)) return;
        String id = "event_region_a|END";
        var search = popup.children().stream().filter(net.minecraft.client.gui.widget.TextFieldWidget.class::isInstance).map(net.minecraft.client.gui.widget.TextFieldWidget.class::cast).findFirst().orElseThrow();
        if (!search.getText().equals(id)) { search.setText(id); pressPopup(popup, "Find"); return; }
        String label = pickerLabel(popup, id);
        var button = popup.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast)
                .filter(value -> value.getMessage().getString().contains(label)).findFirst();
        if (button.isEmpty()) {
            return;
        }
        button.get().onPress(new net.minecraft.client.input.MouseInput(0, 0));
        var add = popup.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast)
                .filter(value -> value.getMessage().getString().equals("Add")).findFirst().orElseThrow();
        require(!add.active, "duplicate Region target unexpectedly enables Add");
        if (popup.parentScreen() instanceof EventEditorScreen editor) require(editor.valuesForTest().changes().size() == 2, "duplicate selection changed saved-state target count");
        pressPopup(popup, "Cancel"); checks.add("duplicate Region target was rejected"); step++;
    }

    private static void pressPopup(CatalogAddPopup popup, String label) {
        popup.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast)
                .filter(value -> value.active && value.getMessage().getString().equals(label)).findFirst().orElseThrow(() -> new IllegalStateException("popup button unavailable: " + label)).onPress(new net.minecraft.client.input.MouseInput(0,0));
    }
    private static String pickerLabel(CatalogAddPopup popup, String id) {
        if (!(popup.parentScreen() instanceof EventEditorScreen editor) || editor.parentMenu().state().picker() == null)
            throw new IllegalStateException("Event catalog response is not present on the parent menu");
        return editor.parentMenu().state().picker().entries().stream().filter(entry -> entry.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Catalog id is not on this picker page: " + id)).label();
    }
    private static boolean pressPrefix(EventEditorScreen editor, String prefix) {
        return editor.visibleWidgetBoundsForTest().stream().map(CoreMenuScreen.WidgetBounds::label).filter(label -> label.startsWith(prefix)).findFirst().map(editor::pressForTest).orElse(false);
    }
    private void await(java.util.function.BooleanSupplier condition, String check) { if (views > waitViews && condition.getAsBoolean()) { checks.add(check); step++; } }
    private void awaitMenu(MinecraftClient client, Page page, String check) { if (client.currentScreen instanceof CoreMenuScreen menu && !menu.pending() && menu.state().page()==page) { checks.add(check); step++; } }
    private void awaitEditor(MinecraftClient client, String page, String check) { if (client.currentScreen instanceof EventEditorScreen editor && page.equals(editor.pageForTest()) && views > waitViews) { checks.add(check); step++; } }
    private static CoreMenuScreen menu(MinecraftClient client) { if (!(client.currentScreen instanceof CoreMenuScreen screen)) throw new IllegalStateException("Core menu is not open"); return screen; }
    private static EventEditorScreen editor(MinecraftClient client) { if (!(client.currentScreen instanceof EventEditorScreen screen)) throw new IllegalStateException("Event editor is not open"); return screen; }
    private static boolean press(CoreMenuScreen screen,String label) { for(int i=0;i<42;i++){if(screen.triggerForTest(label))return true;screen.scrollDetailForTest(i%2==0?1:-1);}return false; }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private void frame(MinecraftClient client, EventEditorScreen editor) { for(var widget:editor.visibleWidgetBoundsForTest()) require(widget.x()>=0&&widget.y()>=0&&widget.x()+widget.width()<=client.getWindow().getScaledWidth()&&widget.y()+widget.height()<=client.getWindow().getScaledHeight(),"editor widget escapes viewport: "+widget); }
    private void requireFixture(MinecraftClient client) { ServerInfo server=client.getCurrentServerEntry(); require(server!=null&&server.address.equals("127.0.0.1:25575"),"refusing outside disposable loopback fixture"); require(client.player.getUuid().equals(UUID.nameUUIDFromBytes("OfflinePlayer:ChronicleEventUI".getBytes(StandardCharsets.UTF_8))),"unexpected smoke account"); }
    private void capture(MinecraftClient client,String name){ScreenshotRecorder.saveScreenshot(output.toFile(),name,client.getFramebuffer(),1,ignored->{});}
    private void finish(MinecraftClient client,boolean pass,String detail){if(complete)return;complete=true;JsonObject result=new JsonObject();result.addProperty("result",pass?"PASS":"FAIL");result.addProperty("detail",detail);result.addProperty("step",step);result.addProperty("views",views);JsonArray rows=new JsonArray();checks.forEach(rows::add);result.add("checks",rows);try{Files.writeString(output.resolve("event-editor.json"),result+"\n",StandardCharsets.UTF_8);}catch(IOException ignored){}client.scheduleStop();}
    private static void setupFailure(Throwable error){try{if(OUTPUT_ENV==null)return;Path out=Path.of(OUTPUT_ENV);Files.createDirectories(out);Files.writeString(out.resolve("event-editor.json"),"{\"result\":\"FAIL\",\"detail\":"+new com.google.gson.Gson().toJson(error.getMessage())+"}\n",StandardCharsets.UTF_8);}catch(IOException ignored){}}
}
