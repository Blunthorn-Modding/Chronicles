package io.github.chronicle.core.client.testing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.client.CatalogAddPopup;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.client.EventEditorScreen;
import io.github.chronicle.core.client.FlagCreateScreen;
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
    private String campaignBeforeCancel, campaignAfterCreate, selectedGame, selectedWorld;

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
            client.getToastManager().clear();
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
            case 4 -> { if (views > waitViews && menu(client).state().editing()) { checks.add("server acknowledged Editing"); step = 100; } }
            case 100 -> { CoreMenuScreen m=menu(client); if (!m.triggerForTest("Flags")) { require(m.triggerForTest("v"), "Flags navigation unavailable"); return; } waitViews=views; step++; }
            case 101 -> { if (views<=waitViews || menu(client).state().page()!=Page.FLAGS) return; campaignBeforeCancel=campaignText(); capture(client,"flags-list.png"); require(press(menu(client),"Create flag"),"Create flag unavailable"); step++; }
            case 102 -> { if (!(client.currentScreen instanceof FlagCreateScreen popup)) return; capture(client,"flag-create.png"); popup.setIdForTest("chronicle_core:cancelled_flag"); require(popup.pressForTest("Cancel"),"flag Cancel unavailable"); step++; }
            case 103 -> { if (!(client.currentScreen instanceof CoreMenuScreen m)) return; require(campaignBeforeCancel.equals(campaignText()),"Cancel changed campaign state"); require(press(m,"Create flag"),"Create flag unavailable after Cancel"); step++; }
            case 104 -> { if (!(client.currentScreen instanceof FlagCreateScreen popup)) return; popup.setIdForTest("chronicle_core:ui_gate"); require(popup.pressForTest("Create"),"valid flag Create unavailable"); waitViews=views; step++; }
            case 105 -> { if (!(client.currentScreen instanceof CoreMenuScreen m) || views<=waitViews) return; require(m.state().success(),"flag creation failed: "+m.state().message()); campaignAfterCreate=campaignText(); require(campaignAfterCreate.contains("chronicle_core:ui_gate") && campaignAfterCreate.contains("flag.define"),"created flag or audit missing from persisted campaign"); var json=com.google.gson.JsonParser.parseString(campaignAfterCreate).getAsJsonObject(); require(!json.getAsJsonObject("flags").get("chronicle_core:ui_gate").getAsBoolean(),"new flag did not start Off"); m.triggerForTest("Back to list"); capture(client,"flags-created.png"); require(press(m,"Create flag"),"duplicate test Create unavailable"); step++; }
            case 106 -> { if (!(client.currentScreen instanceof FlagCreateScreen popup)) return; popup.setIdForTest("chronicle_core:ui_gate"); require(popup.pressForTest("Create"),"duplicate request unavailable"); waitViews=views; step++; }
            case 107 -> { if (!(client.currentScreen instanceof FlagCreateScreen popup) || views<=waitViews) return; require(popup.idForTest().equals("chronicle_core:ui_gate"),"duplicate rejection lost draft"); require(campaignAfterCreate.equals(campaignText()),"duplicate flag changed campaign state"); require(popup.pressForTest("Cancel"),"duplicate popup Cancel unavailable"); checks.add("flag Cancel and duplicate preserve state; creation persists Off with define audit"); step=112; }
            case 112 -> { menu(client).triggerForTest("Back to list"); require(press(menu(client),"Create flag"),"stale popup Create unavailable"); step++; }
            case 113 -> { if (!(client.currentScreen instanceof FlagCreateScreen popup)) return; popup.setIdForTest("chronicle_core:stale_flag"); client.player.networkHandler.sendChatCommand("chronicle editing off"); waitViews=views; step++; }
            case 114 -> { if (!(client.currentScreen instanceof FlagCreateScreen popup) || views<=waitViews) return; require(popup.idForTest().equals("chronicle_core:stale_flag") && !popup.pressForTest("Create"),"stale flag popup did not preserve and lock draft"); require(campaignAfterCreate.equals(campaignText()),"stale flag draft changed campaign state"); require(popup.pressForTest("Cancel"),"stale flag popup Cancel unavailable"); step++; }
            case 115 -> { CoreMenuScreen m=menu(client); require(!m.state().editing(),"Editing remained enabled"); require(m.triggerForTest("Enable editing"),"re-enable Editing unavailable"); waitViews=views; step++; }
            case 116 -> { if (views<=waitViews || !menu(client).state().editing()) return; menu(client).selectForTest("flag:chronicle_core:ui_gate"); waitViews=views; step++; }
            case 117 -> { if (views<=waitViews) return; require(!press(menu(client),"Turn On"),"flag could be changed while paused"); client.player.networkHandler.sendChatCommand("chronicle session resume"); waitViews=views; step++; }
            case 118 -> { if (views<=waitViews || !menu(client).state().phase().equals("ACTIVE")) return; CoreMenuScreen m=menu(client); require(m.state().detail().controls().stream().filter(c->c.action()==io.github.chronicle.core.menu.CoreMenuModel.Action.FLAG_CREATE).noneMatch(c->c.enabled()),"flag creation enabled during active play"); require(press(m,"Turn On"),"live Turn On unavailable"); require(m.triggerForTest("Confirm: Turn On"),"Turn On confirmation unavailable"); waitViews=views; step++; }
            case 119 -> { if (views<=waitViews) return; require(flagValue(),"confirmed Turn On was not persisted"); require(press(menu(client),"Turn Off"),"live Turn Off unavailable"); require(menu(client).triggerForTest("Confirm: Turn Off"),"Turn Off confirmation unavailable"); waitViews=views; step++; }
            case 120 -> { if (views<=waitViews) return; require(!flagValue(),"confirmed Turn Off was not persisted"); client.player.networkHandler.sendChatCommand("chronicle session pause"); waitViews=views; step++; }
            case 121 -> { if (views<=waitViews || !menu(client).state().phase().equals("PAUSED")) return; require(menu(client).triggerForTest("Enable editing"),"Editing unavailable after pause"); waitViews=views; step++; }
            case 122 -> { if (views<=waitViews || !menu(client).state().editing()) return; checks.add("stale flag draft locks after Editing revoked; confirmed live On and Off persist while active creation is disabled"); step=108; }
            case 108 -> { CoreMenuScreen m=menu(client); require(m.triggerForTest("Events"),"Events tab unavailable after Flags"); waitViews=views; step++; }
            case 109 -> { if (views<=waitViews || menu(client).state().page()!=Page.EVENTS) return; CoreMenuScreen m=menu(client); require(m.state().entries().stream().noneMatch(e->e.id().startsWith("world:")||e.id().startsWith("flag:")),"Game Events contains direct changes or flags"); require(!m.state().entries().isEmpty(),"fixture has no Game Events"); selectedGame=m.state().entries().getFirst().id(); m.selectForTest(selectedGame); waitViews=views; step=123; }
            case 123 -> { if (views<=waitViews) return; CoreMenuScreen m=menu(client); m.triggerForTest("Back to list"); require(m.triggerForTest("Region Changes"),"Region Changes filter unavailable"); waitViews=views; step=110; }
            case 110 -> { if (views<=waitViews) return; CoreMenuScreen m=menu(client); require(!m.state().entries().isEmpty() && m.state().entries().stream().allMatch(e->e.id().startsWith("world:")),"Region Changes contains non-world entry or no fixture"); require(m.state().detail().controls().stream().noneMatch(c->c.action()==io.github.chronicle.core.menu.CoreMenuModel.Action.EVENT_NEW),"Region Changes exposes event creation"); capture(client,"event-region-changes-filter.png"); selectedWorld=m.state().entries().getFirst().id(); m.selectForTest(selectedWorld); waitViews=views; step=124; }
            case 124 -> { if (views<=waitViews) return; CoreMenuScreen m=menu(client); m.triggerForTest("Back to list"); require(m.triggerForTest("Game Events"),"Game Events filter unavailable"); waitViews=views; step=125; }
            case 125 -> { if (views<=waitViews) return; CoreMenuScreen m=menu(client); require(m.state().selected().equals(selectedGame),"Game Event selection was not remembered"); require(m.triggerForTest("Region Changes"),"Region Changes filter unavailable on return"); waitViews=views; step=126; }
            case 126 -> { if (views<=waitViews) return; CoreMenuScreen m=menu(client); require(m.state().selected().equals(selectedWorld),"Region Change selection was not remembered"); require(m.triggerForTest("Game Events"),"Game Events filter unavailable on return"); waitViews=views; step=111; }
            case 111 -> { if (views<=waitViews) return; require(menu(client).state().selected().equals(selectedGame),"Game Event selection changed on second return"); menu(client).close(); client.player.networkHandler.sendChatCommand("chronicle");waitViews=views;step=127; }
            case 127 -> { if (!(client.currentScreen instanceof CoreMenuScreen m)||views<=waitViews||m.pending())return;require(m.state().selected().equals(selectedGame),"reopening lost selected Game Event");m.triggerForTest("Back to list");require(m.triggerForTest("Region Changes"),"reopened Region Changes filter unavailable");waitViews=views;step++; }
            case 128 -> { if(views<=waitViews)return;var m=menu(client);require(m.state().selected().equals(selectedWorld),"reopening lost the other Event filter selection");require(m.triggerForTest("Game Events"),"Game Events filter unavailable after reopen");waitViews=views;step++; }
            case 129 -> { if(views<=waitViews)return;require(menu(client).state().selected().equals(selectedGame),"Game Event selection changed after reopen filter switch");checks.add("Events filters separate Game Events and direct Region Changes and remember selections, including close and reopen");step=7; }
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
                frame(client, editor); capture(client,"event-basics.png"); editor.setDraftForTest("name", "Smoke Event");
                require(editor.pressForTest("Message & sound"), "Message & sound page button unavailable");
                checks.add("Create opens the native four-page Event editor and Cancel remains local without a write"); step++;
            }
            case 9 -> {
                if (!(client.currentScreen instanceof EventEditorScreen editor) || !"MEDIA".equals(editor.pageForTest())) return;
                editor.setDraftForTest("message", "A private smoke preview.");
                require(pressPrefix(editor, "Choose sound"), "shared registered-sound picker is unavailable"); step++;
            }
            case 10 -> selectPopup(client, "minecraft:block.note_block.bell", "selected a registered sound through the shared picker and explicit Add");
            case 11 -> { EventEditorScreen editor=editor(client); frame(client,editor); capture(client,"event-media.png"); require(editor.pressForTest("Trigger"), "Trigger page unavailable"); step++; }
            case 12 -> {
                EventEditorScreen editor=editor(client); if (!"TRIGGER".equals(editor.pageForTest())) return;
                frame(client,editor); capture(client,"event-trigger.png");
                require(pressSuffix(editor, "Selected Region"), "Region audience choice unavailable");
                require(pressPrefix(editor, "Audience Region"), "Audience Region picker unavailable after choosing Region audience"); step++;
            }
            case 13 -> selectPopup(client, "event_region_a", "selected a Region audience through the shared picker");
            case 14 -> { EventEditorScreen editor=editor(client); require(pressSuffix(editor, "Entry"), "Entry trigger choice unavailable"); step++; }
            case 15 -> {
                EventEditorScreen editor=editor(client); if (!"TRIGGER".equals(editor.pageForTest())) return;
                frame(client, editor); capture(client,"event-trigger-region-entry-incomplete.png");
                require(editor.valuesForTest().audience().equals("REGION") && editor.valuesForTest().triggerRegion().isBlank(), "fixture did not retain an incomplete Region/Entry trigger");
                require(editor.pressForTest("Message & sound"), "Message & sound page unavailable from incomplete trigger"); step++;
            }
            case 16 -> { EventEditorScreen editor=editor(client); if (!"MEDIA".equals(editor.pageForTest())) return; require(editor.pressForTest("Preview privately"), "private preview rejected an incomplete trigger/audience draft"); waitViews = views; step++; }
            case 17 -> awaitEditor(client, "MEDIA", "media preview works with an incomplete Region/Entry trigger and audience");
            case 18 -> { EventEditorScreen editor=editor(client); require(editor.pressForTest("Trigger"), "Trigger page unavailable after private preview"); step++; }
            case 19 -> { EventEditorScreen editor=editor(client); if (!"TRIGGER".equals(editor.pageForTest())) return; require(pressSuffix(editor, "Active time"), "Timer trigger choice unavailable"); step++; }
            case 20 -> { EventEditorScreen editor=editor(client); if (!"TRIGGER".equals(editor.pageForTest())) return; editor.setDraftForTest("seconds", "30"); frame(client,editor);capture(client,"event-trigger-region-timer.png"); require(pressSuffix(editor, "Participants"), "Participants audience restore unavailable"); step++; }
            case 21 -> { EventEditorScreen editor=editor(client); require(pressSuffix(editor, "Manual"), "Manual trigger restore unavailable"); step++; }
            case 22 -> { EventEditorScreen editor=editor(client); require(pressPrefix(editor, "Optional flag"), "optional flag picker is unavailable for Manual trigger"); step++; }
            case 23 -> selectPopup(client, "chronicle_core:ui_gate", "selected the newly created optional prerequisite flag");
            case 24 -> { EventEditorScreen editor=editor(client); require(editor.pressForTest("Region changes"), "Region changes page unavailable"); step++; }
            case 25 -> { EventEditorScreen editor=editor(client);frame(client,editor);capture(client,"event-changes-empty.png"); if (!"CHANGES".equals(editor.pageForTest())) return; require(pressPrefix(editor, "Add Saved state"), "Saved-state picker unavailable"); step++; }
            case 26 -> selectPopup(client, "event_region_a|START", "added first Region Saved state through shared picker");
            case 27 -> { EventEditorScreen editor=editor(client); require(pressPrefix(editor, "Add Saved state"), "second Saved-state picker unavailable"); step++; }
            case 28 -> selectPopup(client, "event_region_b|START", "added second Region Saved state");
            case 29 -> { EventEditorScreen editor=editor(client);frame(client,editor);capture(client,"event-changes-paired.png"); require(pressPrefix(editor, "Add Saved state"), "duplicate Saved-state picker unavailable"); step++; }
            case 30 -> duplicatePopupRefused(client);
            case 31 -> { EventEditorScreen editor=editor(client); require(editor.pressForTest("Remove"), "Saved-state removal unavailable"); checks.add("duplicate Region target is refused and a target can be removed"); step++; }
            case 32 -> { EventEditorScreen editor=editor(client); require(editor.pressForTest("Basics"), "Basics page unavailable"); step++; }
            case 33 -> { EventEditorScreen editor=editor(client); editor.setDraftForTest("id", "chronicle_core:smoke_event"); require(editor.pressForTest("Create event"), "Create event action unavailable"); waitViews=views; step++; }
            case 34 -> {
                if (!(client.currentScreen instanceof CoreMenuScreen menu) || views <= waitViews) return;
                require(menu.state().page() == Page.EVENTS && menu.state().selected().contains("smoke_event"), "successful save did not return to selected Event detail");
                checks.add("native create/save returns to the updated selected Event without reopening a stale editor");
                capture(client, "event-editor-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png"); require(press(menu,"Edit event"),"saved Event editor unavailable");step++;
            }
            case 35 -> { if(!(client.currentScreen instanceof EventEditorScreen editor))return; require(editor.valuesForTest().flag().equals("chronicle_core:ui_gate"),"flag prerequisite did not survive save and reopen"); client.player.networkHandler.sendChatCommand("chronicle editing off");waitViews=views;step++; }
            case 36 -> { if(!(client.currentScreen instanceof EventEditorScreen editor)||views<=waitViews)return; require(!editor.pressForTest("Save edits"),"stale editor allowed Save after Editing was revoked"); require(!editor.parentMenu().state().editing(),"server retained Editing");checks.add("revoking Editing preserves the draft but disables stale Event writes");finish(client,true,"completed "+checks.size()+" native Event editor checks"); }
            default -> throw new IllegalStateException("unexpected Event editor step " + step);
        }
    }

    private void selectPopup(MinecraftClient client, String id, String check) {
        if (!(client.currentScreen instanceof CatalogAddPopup popup)) return;
        var search = popup.children().stream().filter(net.minecraft.client.gui.widget.TextFieldWidget.class::isInstance).map(net.minecraft.client.gui.widget.TextFieldWidget.class::cast).findFirst().orElseThrow();
        if (!search.getText().equals(id)) { search.setText(id); pressPopup(popup, "Find"); return; }
        String label = pickerLabel(popup, id, true);
        if (label == null) return; // Wait for the matching server picker response, never an earlier page.
        var button = popup.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast)
                .filter(value -> value.getMessage().getString().equals(label)).findFirst();
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
        String label = pickerLabel(popup, id, true);
        if (label == null) return; // Wait for the exact query rather than inspecting stale picker entries.
        var button = popup.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast)
                .filter(value -> value.getMessage().getString().equals(label)).findFirst();
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
    private static String pickerLabel(CatalogAddPopup popup, String id, boolean waitForExactQuery) {
        if (!(popup.parentScreen() instanceof EventEditorScreen editor) || editor.parentMenu().state().picker() == null) return null;
        var picker = editor.parentMenu().state().picker();
        if (waitForExactQuery && !picker.query().equals(id)) return null;
        return picker.entries().stream().filter(entry -> entry.id().equals(id)).findFirst()
                .map(entry -> entry.label()).orElse(null);
    }
    private static boolean pressPrefix(EventEditorScreen editor, String prefix) {
        return editor.visibleWidgetBoundsForTest().stream().map(CoreMenuScreen.WidgetBounds::label).filter(label -> label.startsWith(prefix)).findFirst().map(editor::pressForTest).orElse(false);
    }
    private static boolean pressSuffix(EventEditorScreen editor, String suffix) {
        return editor.visibleWidgetBoundsForTest().stream().map(CoreMenuScreen.WidgetBounds::label).filter(label -> label.endsWith(suffix)).findFirst().map(editor::pressForTest).orElse(false);
    }
    private void await(java.util.function.BooleanSupplier condition, String check) { if (views > waitViews && condition.getAsBoolean()) { checks.add(check); step++; } }
    private void awaitMenu(MinecraftClient client, Page page, String check) { if (client.currentScreen instanceof CoreMenuScreen menu && !menu.pending() && menu.state().page()==page) { checks.add(check); step++; } }
    private void awaitEditor(MinecraftClient client, String page, String check) { if (client.currentScreen instanceof EventEditorScreen editor && page.equals(editor.pageForTest()) && views > waitViews) { checks.add(check); step++; } }
    private static CoreMenuScreen menu(MinecraftClient client) { if (!(client.currentScreen instanceof CoreMenuScreen screen)) throw new IllegalStateException("Core menu is not open"); return screen; }
    private static EventEditorScreen editor(MinecraftClient client) { if (!(client.currentScreen instanceof EventEditorScreen screen)) throw new IllegalStateException("Event editor is not open"); return screen; }
    private static boolean press(CoreMenuScreen screen,String label) { for(int i=0;i<42;i++){if(screen.triggerForTest(label))return true;screen.scrollDetailForTest(i%2==0?1:-1);}return false; }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private String campaignText() { try { return Files.readString(serverRoot.resolve("world/chronicle/campaign-state.json"), StandardCharsets.UTF_8); } catch (IOException error) { throw new IllegalStateException("Cannot read campaign fixture", error); } }
    private boolean flagValue() { return com.google.gson.JsonParser.parseString(campaignText()).getAsJsonObject().getAsJsonObject("flags").get("chronicle_core:ui_gate").getAsBoolean(); }
    private void frame(MinecraftClient client,EventEditorScreen editor){
        var widgets=editor.visibleWidgetBoundsForTest();
        for(var w:widgets)require(w.x()>=0&&w.y()>=0&&w.x()+w.width()<=client.getWindow().getScaledWidth()&&w.y()+w.height()<=client.getWindow().getScaledHeight(),"editor widget escapes viewport: "+w);
        for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var a=widgets.get(i);var b=widgets.get(j);require(a.x()+a.width()<=b.x()||b.x()+b.width()<=a.x()||a.y()+a.height()<=b.y()||b.y()+b.height()<=a.y(),"editor widgets overlap: "+a+" / "+b);}
    }
    private void requireFixture(MinecraftClient client) { ServerInfo server=client.getCurrentServerEntry(); require(server!=null&&server.address.equals("127.0.0.1:25575"),"refusing outside disposable loopback fixture"); require(client.player.getUuid().equals(UUID.nameUUIDFromBytes("OfflinePlayer:ChronicleEventUI".getBytes(StandardCharsets.UTF_8))),"unexpected smoke account"); }
    private void capture(MinecraftClient client,String name){ScreenshotRecorder.saveScreenshot(output.toFile(),name,client.getFramebuffer(),1,ignored->{});}
    private void finish(MinecraftClient client,boolean pass,String detail){if(complete)return;complete=true;JsonObject result=new JsonObject();result.addProperty("result",pass?"PASS":"FAIL");result.addProperty("detail",detail);result.addProperty("step",step);result.addProperty("views",views);JsonArray rows=new JsonArray();checks.forEach(rows::add);result.add("checks",rows);try{Files.writeString(output.resolve("event-editor.json"),result+"\n",StandardCharsets.UTF_8);}catch(IOException ignored){}client.scheduleStop();}
    private static void setupFailure(Throwable error){try{if(OUTPUT_ENV==null)return;Path out=Path.of(OUTPUT_ENV);Files.createDirectories(out);Files.writeString(out.resolve("event-editor.json"),"{\"result\":\"FAIL\",\"detail\":"+new com.google.gson.Gson().toJson(error.getMessage())+"}\n",StandardCharsets.UTF_8);}catch(IOException ignored){}}
}
