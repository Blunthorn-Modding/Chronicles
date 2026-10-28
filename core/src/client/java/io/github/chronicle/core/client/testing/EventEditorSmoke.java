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
import net.minecraft.client.gui.widget.ButtonWidget;
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
    private String campaignBeforeCancel, campaignAfterCreate, selectedGame, selectedWorld, sourceBook;
    private final java.util.Set<String> pickerVisited = new java.util.HashSet<>(), pickerAll = new java.util.HashSet<>();
    private List<io.github.chronicle.core.menu.CoreMenuModel.Entry> pickerFirstPage;
    private boolean pickerReturned;
    private int pickerMoves;

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
            if (client.currentScreen instanceof EventEditorScreen e) frame(client,e);
            if (client.currentScreen instanceof CatalogAddPopup p) popupFrame(client,p);
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
                    if (!(client.currentScreen instanceof EventEditorScreen e)) return;
                    e.setDraftForTest("id", "chronicle_core:cancelled_event");
                    require(e.pressForTest("Cancel"), "local Event editor Cancel unavailable"); cancellationIssued = true; return;
                }
                if (!cancellationVerified) {
                    if (!(client.currentScreen instanceof CoreMenuScreen m)) return;
                    require(m.state().entries().stream().noneMatch(entry -> entry.id().contains("cancelled_event")), "Cancel wrote an Event definition");
                    require(press(m, "Create new event"), "Create unavailable after Cancel"); cancellationVerified = true; return;
                }
                if (!(client.currentScreen instanceof EventEditorScreen e)) return;
                require("BASICS".equals(e.pageForTest()), "new editor did not open on Basics"); frame(client,e); capture(client,"event-basics.png");
                e.setDraftForTest("name", "Smoke Event"); e.setDraftForTest("id", "chronicle_core:smoke_event");
                require(e.pressForTest(">"), "When navigation unavailable"); checks.add("Create opens the Event flow and Cancel remains local without a write"); step++;
            }
            case 9 -> { EventEditorScreen e=editor(client); frame(client,e); require("WHEN".equals(e.pageForTest()), "When section missing"); require(pressPrefix(e,"Trigger:"), "trigger picker unavailable"); step=900; }
            case 900 -> {
                if (!(client.currentScreen instanceof CatalogAddPopup p)) return;
                if(((EventEditorScreen)p.parentScreen()).parentMenu().pending())return;
                var picker=((EventEditorScreen)p.parentScreen()).parentMenu().state().picker();
                if(picker==null||!picker.kind().equals("EVENT_TRIGGER")||!picker.query().isEmpty())return;
                require(++pickerMoves<80,"picker failed to reach its final entries");
                if(pickerFirstPage==null){require(picker.pages()>1,"trigger fixture needs multiple pages");pickerFirstPage=picker.entries();capture(client,"trigger-picker.png");}
                for(var entry:picker.entries()){pickerAll.add(entry.id());if(popupHasRow(p,entry.label()))pickerVisited.add(entry.id());}
                if(popupActive(p,"↓")){pressPopup(p,"↓");return;}
                require(picker.page()==picker.pages()-1&&picker.entries().size()<12,"Down stopped before the short final page");
                require(pickerVisited.equals(pickerAll),"scrolling skipped catalog entries");checks.add("Up Down reaches every trigger across server pages without separate page controls");step=901;
            }
            case 901 -> {
                if (!(client.currentScreen instanceof CatalogAddPopup p)) return;
                if(((EventEditorScreen)p.parentScreen()).parentMenu().pending())return;
                var picker=((EventEditorScreen)p.parentScreen()).parentMenu().state().picker();
                if(picker.page()==0&&!pickerReturned){int rows=Math.clamp((client.getWindow().getScaledHeight()-176)/20,1,6);require(popupHasRow(p,pickerFirstPage.get(Math.max(0,pickerFirstPage.size()-rows)).label()),"previous page did not return to its last rows");pickerReturned=true;}
                require(++pickerMoves<150,"picker failed to return to its start");
                if(popupActive(p,"↑")){pressPopup(p,"↑");return;}
                require(picker.page()==0&&pickerReturned&&popupHasRow(p,pickerFirstPage.getFirst().label()),"Up stopped before the first entry");
                popupSearch(p,"Forge");pressPopup(p,"Find");step=902;
            }
            case 902 -> {
                if (!(client.currentScreen instanceof CatalogAddPopup p)) return;
                if(((EventEditorScreen)p.parentScreen()).parentMenu().pending())return;
                var picker=((EventEditorScreen)p.parentScreen()).parentMenu().state().picker();
                if(!picker.query().equals("Forge"))return;
                require(picker.page()==0&&picker.entries().size()==1&&picker.entries().getFirst().id().equals("chronicle_core:forge_completed"),"search did not reset the catalog");
                require(!popupActive(p,"↑")&&!popupActive(p,"↓"),"single search result still scrolls");popupSearch(p,"");pressPopup(p,"Find");step=903;
            }
            case 903 -> {
                if (!(client.currentScreen instanceof CatalogAddPopup p)) return;
                if(((EventEditorScreen)p.parentScreen()).parentMenu().pending())return;
                var picker=((EventEditorScreen)p.parentScreen()).parentMenu().state().picker();if(!picker.query().isEmpty())return;
                require(picker.page()==0&&popupHasRow(p,pickerFirstPage.getFirst().label()),"cleared search did not return to the start");checks.add("Up returns through prior pages and search resets scrolling without losing entries");step=10;
            }
            case 10 -> { selectPopup(client, "chronicle_core:book_found", "selected Book found source through the shared picker"); if (step==11) step=1001; }
            case 1001 -> { EventEditorScreen e=editor(client); frame(client,e); require(e.visibleWidgetBoundsForTest().stream().anyMatch(w -> w.label().contains("Book found")), "system trigger did not display its catalog label"); capture(client,"event-when.png"); checks.add("captured When section"); require(pressPrefix(e,"Trigger:"), "trigger picker unavailable after source"); step=1002; }
            case 1002 -> { selectPopup(client, "CONDITION", "selected the flags-On trigger through the shared picker"); if (step==1003) step=11; }
            case 11 -> { EventEditorScreen e=editor(client); require(e.pressForTest(">"), "Requirements navigation unavailable"); step=1111; }
            case 1111 -> { EventEditorScreen e=editor(client); frame(client,e); capture(client,"event-requirements.png"); checks.add("captured Requirements section"); require(pressPrefix(e,"Add prerequisite"), "prerequisite picker unavailable"); step=12; }
            case 12 -> selectPopup(client, "chronicle_core:ui_gate", "selected the newly created prerequisite flag");
            case 13 -> { EventEditorScreen e=editor(client); require(pressPrefix(e,"Add prerequisite"), "second prerequisite picker unavailable"); step++; }
            case 14 -> selectPopup(client, "quiet_below:m8_gate", "selected a second prerequisite flag");
            case 15 -> { EventEditorScreen e=editor(client); require(e.valuesForTest().requiredFlags().size()==2, "two prerequisites not retained"); require(pressPrefix(e,"Add prerequisite"), "picker Cancel probe unavailable"); step++; }
            case 16 -> { if (!(client.currentScreen instanceof CatalogAddPopup p)) return; pressPopup(p,"Cancel"); require(editor(client).valuesForTest().requiredFlags().size()==2, "picker Cancel changed prerequisites"); checks.add("prerequisite picker Cancel preserves the draft"); step++; }
            case 17 -> { EventEditorScreen e=editor(client); require(e.pressForTest(">"), "Delay navigation unavailable"); require(pressSuffix(e,"Active-play delay"), "delay selection unavailable"); e.setDraftForTest("seconds","30"); step=171; }
            case 171 -> { EventEditorScreen e=editor(client); frame(client,e); capture(client,"event-delay.png"); checks.add("captured Delay section"); require(e.pressForTest(">"), "What happens navigation unavailable"); step=18; }
            case 18 -> { EventEditorScreen e=editor(client); frame(client,e); require(pressPrefix(e,"Add action"), "Add action unavailable"); frame(client,e); require(pressPrefix(e,"Update flag"), "Update flag unavailable"); step=181; }
            case 181 -> { EventEditorScreen e=editor(client); frame(client,e); capture(client,"event-action-flag.png"); checks.add("captured flag action editor"); require(pressPrefix(e,"Flag:"), "flag target picker unavailable"); step=19; }
            case 19 -> selectPopup(client, "chronicle_core:ui_gate", "selected the first flag outcome");
            case 20 -> { EventEditorScreen e=editor(client); frame(client,e); require(e.pressForTest("Done"), "flag Done unavailable"); require(pressPrefix(e,"Add action"), "second Add action unavailable"); require(pressPrefix(e,"Update flag"), "second flag action unavailable"); require(pressPrefix(e,"Flag:"), "second flag target unavailable"); step++; }
            case 21 -> selectPopup(client, "quiet_below:m8_gate", "selected the second flag outcome");
            case 22 -> { EventEditorScreen e=editor(client); require(pressSuffix(e,"Off"), "flag Off choice unavailable"); require(e.pressForTest("Done"), "second flag Done unavailable"); require(pressPrefix(e,"Add action"), "text Add action unavailable"); require(pressPrefix(e,"Send text"), "text action unavailable"); e.setDraftForTest("message","A private smoke preview."); step=221; }
            case 221 -> { EventEditorScreen e=editor(client); frame(client,e); capture(client,"event-action-text.png"); checks.add("captured text action editor"); require(pressSuffix(e,"Selected Region"), "Region audience unavailable"); require(pressPrefix(e,"Audience Region"), "Region audience picker unavailable"); step=23; }
            case 23 -> selectPopup(client, "event_region_a", "selected a Region audience through the shared picker");
            case 24 -> { EventEditorScreen e=editor(client); require(e.pressForTest("Preview privately"), "private preview unavailable"); waitViews=views; step++; }
            case 25 -> { if (!(client.currentScreen instanceof EventEditorScreen e) || views<=waitViews) return; require(e.valuesForTest().requiredFlags().size()==2 && e.valuesForTest().flagChanges().size()==2 && e.valuesForTest().seconds()==30, "preview changed draft"); checks.add("private preview leaves actions and prerequisites untouched"); require(e.pressForTest("Done"), "text Done unavailable"); require(pressPrefix(e,"Add action"), "sound Add unavailable"); require(pressPrefix(e,"Play sound"), "sound action unavailable"); step=251; }
            case 251 -> { EventEditorScreen e=editor(client); frame(client,e); capture(client,"event-action-sound.png"); checks.add("captured sound action editor"); require(pressPrefix(e,"Choose sound"), "sound picker unavailable"); step=26; }
            case 26 -> selectPopup(client, "minecraft:block.note_block.bell", "selected a registered sound through the shared picker and explicit Add");
            case 27 -> { EventEditorScreen e=editor(client); require(e.pressForTest("Done"), "sound Done unavailable"); require(pressPrefix(e,"Add action"), "Region Add unavailable"); require(pressPrefix(e,"Apply Region Saved state"), "Region action unavailable"); step++; }
            case 28 -> selectPopup(client, "event_region_a|START", "added first Region Saved state through shared picker");
            case 29 -> { EventEditorScreen e=editor(client); require(pressPrefix(e,"Add action"), "second Region Add unavailable"); require(pressPrefix(e,"Apply Region Saved state"), "second Region action unavailable"); step++; }
            case 30 -> selectPopup(client, "event_region_b|START", "added second Region Saved state");
            case 31 -> { EventEditorScreen e=editor(client); frame(client,e); capture(client,"event-actions-long-list.png"); require(e.pressForTest("\u2193"), "long action list cannot scroll down"); frame(client,e); require(e.pressForTest("\u2191"), "long action list cannot scroll up"); checks.add("action list scrolls beyond four visible rows"); require(pressPrefix(e,"Add action"), "duplicate Region Add unavailable"); require(pressPrefix(e,"Apply Region Saved state"), "duplicate Region action unavailable"); step++; }
            case 32 -> duplicatePopupRefused(client);
            case 33 -> { EventEditorScreen e=editor(client); require(e.pressForTest("Cancel"), "return from Add action unavailable"); require(removeRow(e,2), "text removal unavailable"); require(removeRow(e,2), "sound removal unavailable"); require(removeRow(e,2), "first Region removal unavailable"); require(removeRow(e,2), "second Region removal unavailable"); var f=e.valuesForTest(); require(f.message().isBlank() && f.sound().isBlank() && f.changes().isEmpty() && f.flagChanges().size()==2, "flag-only draft incorrect"); require(f.requiredFlags().size()==2 && f.seconds()==30 && f.mode().equals("CONDITION"), "requirements or delay lost"); checks.add("duplicate Region target is refused and a target can be removed"); require(e.pressForTest("Create event"), "flag-only Create unavailable"); waitViews=views; step++; }
            case 34 -> { if (!(client.currentScreen instanceof CoreMenuScreen m) || views<=waitViews) return; require(m.state().page()==Page.EVENTS && m.state().selected().contains("smoke_event"), "save did not select Event: " + m.state().message()); checks.add("native flag-only create returns to the selected Event"); require(press(m,"Edit event"), "saved Event editor unavailable"); step++; }
            case 35 -> { if (!(client.currentScreen instanceof EventEditorScreen e)) return; var f=e.valuesForTest(); require(f.requiredFlags().size()==2 && f.requiredFlags().contains("chronicle_core:ui_gate") && f.requiredFlags().contains("quiet_below:m8_gate"), "prerequisites did not survive save"); require(Boolean.TRUE.equals(f.flagChanges().get("chronicle_core:ui_gate")) && Boolean.FALSE.equals(f.flagChanges().get("quiet_below:m8_gate")), "flag outcomes did not survive save"); require(f.mode().equals("CONDITION") && f.seconds()==30 && f.message().isBlank() && f.sound().isBlank() && f.changes().isEmpty(), "flag-only delay readback failed"); checks.add("two prerequisites, On and Off outcomes, and condition delay survive flag-only save"); require(e.pressForTest("Cancel"), "return from first Event unavailable"); step=350; }
            case 350 -> { CoreMenuScreen m=menu(client); m.triggerForTest("Back to list"); require(press(m,"Create new event"), "second Event Create unavailable"); step=351; }
            case 351 -> { if (!(client.currentScreen instanceof EventEditorScreen e)) return; e.setDraftForTest("name","Source UI Event"); e.setDraftForTest("id","chronicle_core:source_ui_event"); require(e.pressForTest(">"), "source When unavailable"); require(pressPrefix(e,"Trigger:"), "source trigger picker unavailable"); step=352; }
            case 352 -> { selectPopup(client,"chronicle_core:region_applied","selected Region applied source"); if(step==353)step=353; }
            case 353 -> { EventEditorScreen e=editor(client); require(pressPrefix(e,"Activity:"), "activity picker unavailable"); step=354; }
            case 354 -> selectTypedPopup(client,"chronicle_core:region_applied","START",false);
            case 355 -> { EventEditorScreen e=editor(client); require(e.valuesForTest().source().activity().equals("START"), "specific activity not selected"); require(pressPrefix(e,"Activity:"), "activity clear picker unavailable"); step=356; }
            case 356 -> selectTypedPopup(client,"chronicle_core:region_applied","",false);
            case 357 -> { EventEditorScreen e=editor(client); require(e.valuesForTest().source().activity().isEmpty(), "Any activity failed to clear activity"); require(e.visibleWidgetBoundsForTest().stream().anyMatch(w->w.label().contains("Any activity")), "Any activity caption missing"); checks.add("typed activity picker selects and clears a specific activity"); require(pressPrefix(e,"Trigger:"), "Book opened trigger unavailable"); step=358; }
            case 358 -> selectPopup(client,"chronicle_core:book_opened","selected Book opened source");
            case 359 -> { EventEditorScreen e=editor(client); require(pressPrefix(e,"Target:"), "Book target picker unavailable"); step=360; }
            case 360 -> selectTypedPopup(client,"chronicle_core:book_opened","",true);
            case 361 -> { EventEditorScreen e=editor(client); sourceBook=e.valuesForTest().source().target(); require(!sourceBook.isBlank(), "Book target missing"); require(e.pressForTest(">") && e.pressForTest(">") && e.pressForTest(">"), "source Actions navigation unavailable"); require(pressPrefix(e,"Add action") && pressPrefix(e,"Update flag") && pressPrefix(e,"Flag:"), "source flag action unavailable"); step=362; }
            case 362 -> selectPopup(client,"chronicle_core:ui_gate","selected flag-only source outcome");
            case 363 -> { EventEditorScreen e=editor(client); require(e.pressForTest("Done"), "source flag Done unavailable"); require(e.pressForTest("Create event"), "source Event Create unavailable"); waitViews=views; step=364; }
            case 364 -> { if (!(client.currentScreen instanceof CoreMenuScreen m) || views<=waitViews) return; require(m.state().selected().contains("source_ui_event"), "source Event save failed: "+m.state().message()); require(press(m,"Edit event"), "source Event reopen unavailable"); step=365; }
            case 365 -> { if (!(client.currentScreen instanceof EventEditorScreen e)) return; var f=e.valuesForTest(); require(f.mode().equals("SYSTEM") && f.source().type().equals("chronicle_core:book_opened") && f.source().target().equals(sourceBook) && f.source().activity().isEmpty() && Boolean.TRUE.equals(f.flagChanges().get("chronicle_core:ui_gate")) && f.message().isBlank() && f.sound().isBlank() && f.changes().isEmpty(), "source Event did not roundtrip"); checks.add("Book opened target and flag-only outcome survive save and reopen"); require(e.pressForTest(">") && e.pressForTest(">"), "Requirements navigation unavailable after reopen"); require(pressPrefix(e,"Add prerequisite"), "stale picker probe unavailable"); client.player.networkHandler.sendChatCommand("chronicle editing off"); waitViews=views; step=36; }
            case 36 -> { if (!(client.currentScreen instanceof CatalogAddPopup p) || views<=waitViews) return; require(p.parentScreen() instanceof EventEditorScreen, "stale picker lost parent"); EventEditorScreen e=(EventEditorScreen)p.parentScreen(); if (e.parentMenu().state().editing()) return; require(!popupActive(p,"Add") && !popupActive(p,"Find"), "stale picker permits mutation or search"); pressPopup(p,"Close"); require(client.currentScreen==e && !e.pressForTest("Save edits"), "stale editor allowed Save"); require(e.valuesForTest().source().target().equals(sourceBook), "stale picker lost draft"); require(e.pressForTest("Cancel"), "stale editor cannot close"); checks.add("stale child picker locks while preserving the draft and Close remains usable"); finish(client,true,"completed "+checks.size()+" native Event editor checks"); }
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

    private void selectTypedPopup(MinecraftClient client, String type, String requested, boolean firstTarget) {
        if (!(client.currentScreen instanceof CatalogAddPopup popup)) return;
        var search=popup.children().stream().filter(net.minecraft.client.gui.widget.TextFieldWidget.class::isInstance).map(net.minecraft.client.gui.widget.TextFieldWidget.class::cast).findFirst().orElseThrow();
        if (!search.getText().equals(requested)) { search.setText(requested); pressPopup(popup,"Find"); return; }
        if (!(popup.parentScreen() instanceof EventEditorScreen e) || e.parentMenu().state().picker()==null) return;
        var picker=e.parentMenu().state().picker(); if (!picker.query().equals(type+"|"+requested)) return;
        var entry=picker.entries().stream().filter(v -> firstTarget ? v.id().startsWith(type+"|") && v.id().length()>type.length()+1 : v.id().equals(type+"|"+requested)).findFirst().orElse(null);
        if (entry==null) return;
        var button=popup.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast).filter(v->v.active && v.getMessage().getString().equals(entry.label())).findFirst().orElse(null);
        if (button==null) return;
        button.onPress(new net.minecraft.client.input.MouseInput(0,0)); pressPopup(popup,"Add"); step++;
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

    private static boolean removeRow(EventEditorScreen editor, int index) {
        var buttons=editor.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast).filter(b -> b.active && b.getMessage().getString().equals("Remove")).toList();
        if (index >= buttons.size()) return false; buttons.get(index).onPress(new net.minecraft.client.input.MouseInput(0,0)); return true;
    }
    private static boolean popupHasRow(CatalogAddPopup popup,String label) {
        return popup.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .anyMatch(b->b.visible&&(b.getMessage().getString().equals(label)||b.getMessage().getString().equals("> "+label)));
    }
    private static void popupSearch(CatalogAddPopup popup,String query) {
        popup.children().stream().filter(net.minecraft.client.gui.widget.TextFieldWidget.class::isInstance)
                .map(net.minecraft.client.gui.widget.TextFieldWidget.class::cast).findFirst().orElseThrow().setText(query);
    }
    private static boolean popupActive(CatalogAddPopup popup, String label) {
        return popup.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast).anyMatch(b -> b.getMessage().getString().equals(label) && b.active);
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
        int width=client.getWindow().getScaledWidth(), height=client.getWindow().getScaledHeight();
        int contentWidth=Math.min(440,Math.max(240,width-24)), left=(width-contentWidth)/2-6, top=Math.max(6,(height-240)/2+6)-6;
        for(var w:widgets)require(w.x()>=left&&w.y()>=top&&w.x()+w.width()<=left+contentWidth+12&&w.y()+w.height()<=top+240&&w.x()+w.width()<=width&&w.y()+w.height()<=height,"editor widget escapes backdrop: "+w);
        for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var a=widgets.get(i);var b=widgets.get(j);require(a.x()+a.width()<=b.x()||b.x()+b.width()<=a.x()||a.y()+a.height()<=b.y()||b.y()+b.height()<=a.y(),"editor widgets overlap: "+a+" / "+b);}
    }
    private void popupFrame(MinecraftClient client, CatalogAddPopup popup) {
        int width=client.getWindow().getScaledWidth(), height=client.getWindow().getScaledHeight();
        int contentWidth=Math.min(440,Math.max(240,width-24)), rows=Math.clamp((height-176)/20,1,6), panelHeight=150+rows*20;
        int left=(width-contentWidth)/2-6, top=Math.max(8,(height-panelHeight)/2)-6;
        var widgets=popup.children().stream().filter(net.minecraft.client.gui.widget.ClickableWidget.class::isInstance).map(net.minecraft.client.gui.widget.ClickableWidget.class::cast).filter(w -> w.visible).toList();
        require(widgets.stream().noneMatch(w -> w.getMessage().getString().equals("<") || w.getMessage().getString().equals(">")), "redundant picker page controls remain");
        if(popup.parentScreen() instanceof EventEditorScreen e && e.parentMenu().state().picker()!=null) {
            for(var entry:e.parentMenu().state().picker().entries())if(!entry.kind().equals("ITEM")&&!entry.kind().equals("BOOK"))
                for(var widget:widgets)if(widget.getMessage().getString().equals(entry.label())||widget.getMessage().getString().equals("> "+entry.label()))
                    require(widget.getX()==left+6&&widget.getWidth()==contentWidth,"text-only picker row is not centered");
        }
        for(var w:widgets) require(w.getX()>=left && w.getY()>=top && w.getX()+w.getWidth()<=left+contentWidth+12 && w.getY()+w.getHeight()<=top+panelHeight && w.getX()+w.getWidth()<=width && w.getY()+w.getHeight()<=height, "picker widget escapes backdrop: "+w.getMessage().getString());
        for(int a=0;a<widgets.size();a++) for(int b=a+1;b<widgets.size();b++) { var x=widgets.get(a); var y=widgets.get(b); require(x.getX()+x.getWidth()<=y.getX() || y.getX()+y.getWidth()<=x.getX() || x.getY()+x.getHeight()<=y.getY() || y.getY()+y.getHeight()<=x.getY(), "picker widgets overlap"); }
    }
    private void requireFixture(MinecraftClient client) { ServerInfo server=client.getCurrentServerEntry(); require(server!=null&&server.address.equals("127.0.0.1:25575"),"refusing outside disposable loopback fixture"); require(client.player.getUuid().equals(UUID.nameUUIDFromBytes("OfflinePlayer:ChronicleEventUI".getBytes(StandardCharsets.UTF_8))),"unexpected smoke account"); }
    private void capture(MinecraftClient client,String name){ScreenshotRecorder.saveScreenshot(output.toFile(),name,client.getFramebuffer(),1,ignored->{});}
    private void finish(MinecraftClient client,boolean pass,String detail){if(complete)return;complete=true;JsonObject result=new JsonObject();result.addProperty("result",pass?"PASS":"FAIL");result.addProperty("detail",detail);result.addProperty("step",step);result.addProperty("views",views);JsonArray rows=new JsonArray();checks.forEach(rows::add);result.add("checks",rows);try{Files.writeString(output.resolve("event-editor.json"),result+"\n",StandardCharsets.UTF_8);}catch(IOException ignored){}client.scheduleStop();}
    private static void setupFailure(Throwable error){try{if(OUTPUT_ENV==null)return;Path out=Path.of(OUTPUT_ENV);Files.createDirectories(out);Files.writeString(out.resolve("event-editor.json"),"{\"result\":\"FAIL\",\"detail\":"+new com.google.gson.Gson().toJson(error.getMessage())+"}\n",StandardCharsets.UTF_8);}catch(IOException ignored){}}
}
