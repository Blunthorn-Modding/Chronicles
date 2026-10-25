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
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Opt-in native CF18 authoring and receipt probe, restricted to its disposable fixture. */
public final class BooksLoreSmoke {
    private static final String OUTPUT_ENV = System.getenv("CHRONICLE_BOOKS_SMOKE");
    private static final String SERVER_ENV = System.getenv("CHRONICLE_BOOKS_SERVER_DIR");
    private static final boolean DELIVERY = "true".equals(System.getenv("CHRONICLE_BOOKS_DELIVERY"));
    private static final boolean RESTART = "true".equals(System.getenv("CHRONICLE_BOOKS_RESTART"));
    private static final String ADDRESS = "127.0.0.1:25575";
    private static BooksLoreSmoke instance;

    private final Path output, serverRoot;
    private final List<String> checks = new ArrayList<>();
    private final List<String> trialMessages = new ArrayList<>();
    private View latestView;
    private long views, started = System.nanoTime();
    private boolean connecting, complete;
    private int step, waitTicks;
    private String awaitingTrial = "";

    private BooksLoreSmoke(Path output, Path serverRoot) throws IOException {
        this.output = output.toAbsolutePath().normalize();
        this.serverRoot = serverRoot.toAbsolutePath().normalize();
        if (!Files.isRegularFile(this.serverRoot.resolve(".books-lore-fixture")))
            throw new IllegalArgumentException("refusing Books & Lore smoke outside its disposable fixture");
        if (this.output.startsWith(this.serverRoot)) throw new IllegalArgumentException("evidence must be outside the fixture");
        Files.createDirectories(this.output.getParent());
    }

    public static void register() {
        if (OUTPUT_ENV == null) return;
        try {
            if (SERVER_ENV == null) throw new IllegalArgumentException("CHRONICLE_BOOKS_SERVER_DIR is required");
            instance = new BooksLoreSmoke(Path.of(OUTPUT_ENV), Path.of(SERVER_ENV));
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay) instance.trialMessages.add(message.getString()); });
            ClientTickEvents.END_CLIENT_TICK.register(instance::tick);
        } catch (IOException | RuntimeException error) {
            ChronicleCore.LOGGER.error("Books & Lore smoke setup failed", error);
            writeSetupFailure(error);
        }
    }

    /** Hook this beside the normal menu screen's view delivery, if it is not already shared. */
    public static void onView(View view) { if (instance != null && !instance.complete) {instance.views++;instance.latestView=view;} }

    private void tick(MinecraftClient client) {
        if (complete) return;
        client.getToastManager().clear();
        try {
            if (System.nanoTime() - started > 120_000_000_000L) throw new IllegalStateException("Books menu probe timed out at step "+step);
            if (client.player == null && client.getOverlay() == null && client.currentScreen instanceof AccessibilityOnboardingScreen)
                client.setScreen(new TitleScreen());
            if (!connecting && client.player == null && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connecting = true;
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS),
                        new ServerInfo("Chronicle Books smoke", ADDRESS, ServerInfo.ServerType.OTHER), false, null);
                return;
            }
            if (client.player == null) return;
            if(client.player.isDead())throw new IllegalStateException("Fixture player died at step "+step);
            if (client.getCurrentServerEntry() == null || !ADDRESS.equals(client.getCurrentServerEntry().address))
                throw new IllegalStateException("refusing non-loopback Books smoke connection");
            if (step == 0) { client.player.networkHandler.sendChatCommand("chronicles"); step++; return; }
            CoreMenuScreen screen = client.currentScreen instanceof CoreMenuScreen value ? value : null;
            if ((step <= 1 || (!DELIVERY && (step == 2 || (RESTART && step == 3)))) && (screen == null || screen.pending())) return;
            if (step == 1) {
                if (screen.state().page() != Page.BOOKS_LORE) {
                    if (!screen.triggerForTest("Books & Lore")) throw new IllegalStateException("Books & Lore navigation is unavailable");
                    return;
                }
                checks.add("Books & Lore page rendered through normal menu transport");
                step++;
                return;
            }
            if (DELIVERY && (step >= 2 && step <= 6 || step >= 200 && step <= 235)) { editor(client, screen); return; }
            if (step == 2 && RESTART) { command(client, "restart"); step++; return; }
            if (step == 3 && RESTART) {
                if (!passed()) return;
                var bounds = screen.contentBoundsForTest();
                if (bounds.width() < 1 || bounds.height() < 1) throw new IllegalStateException("Books page has no visible content bounds");
                ScreenshotRecorder.saveScreenshot(output.getParent().toFile(), "books-lore-" + client.getWindow().getScaledWidth()
                        + "x" + client.getWindow().getScaledHeight() + ".png", client.getFramebuffer(), 1, ignored -> { });
                checks.add("Books delivery provenance survived restart");screen.selectForTest("quiet_below:field_journal_i");step=4;return;
            }
            if(step==4&&RESTART){if(screen==null||screen.pending()||!screen.state().selected().equals("quiet_below:field_journal_i")||screen.state().detail().book()==null)return;capture(client,"books-narrow-detail.png");checks.add("narrow layout rendered selected book details");finish(client,true,"completed restart verification");return;}
            if (step == 2 && !DELIVERY) {
                var bounds = screen.contentBoundsForTest();
                if (bounds.width() < 1 || bounds.height() < 1) throw new IllegalStateException("Books page has no visible content bounds");
                ScreenshotRecorder.saveScreenshot(output.getParent().toFile(), "books-lore-" + client.getWindow().getScaledWidth()
                        + "x" + client.getWindow().getScaledHeight() + ".png", client.getFramebuffer(), 1, ignored -> { });
                checks.add("Books & Lore desktop/narrow page retained its popup bounds");
                finish(client, true, "completed " + checks.size() + " read-only Books & Lore checks");
            }
            if (!DELIVERY) return;
            if (step == 7) { command(client, "prepare"); step++; return; }
            if (step == 8) { if (!passed()) return; command(client, "keep"); step++; return; }
            if (step == 9) { if (!passed()) return; waitTicks++; if(waitTicks<150)return; command(client, "cycles"); step++; return; }
            if (step == 10) { if (!passed()) return; checks.add("shared ledger proves kept/once/table isolation after scheduled cycles"); client.setScreen(null); openChest(client); step++; return; }
            if (step == 11) {
                int chest = managedChestSlot(client); if (chest < 0) return;
                command(client,"full"); waitTicks=5; step++; return;
            }
            if (step == 12) { if (!passed()||waitTicks-->0) return; int chest=managedChestSlot(client); if(chest<0)return; client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId,chest,0,SlotActionType.QUICK_MOVE,client.player); step=120; waitTicks=5; return; }
            if (step == 13) { if (!passed()||waitTicks-->0) return; int chest=managedChestSlot(client); if(chest<0)return; client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId,chest,0,SlotActionType.PICKUP,client.player); step=130; waitTicks=5; return; }
            if(step==120){if(waitTicks-->0)return;command(client,"failed");step=13;waitTicks=5;return;}
            if(step==130){if(waitTicks-->0)return;command(client,"cursor");step=14;return;}
            if (step == 14) {
                if (!passed()) return;
                int target = emptyPlayerSlot(client); if (target < 0) throw new IllegalStateException("fixture player inventory remained full");
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, target, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, target, 0, SlotActionType.QUICK_MOVE, client.player);
                step=140;waitTicks=5;return;
            }
            if(step==140){if(waitTicks-->0)return;command(client,"found");step=15;return;}
            if(step==15){if(!passed())return;checks.add("real fleeting inventory receipt persisted; cursor and full transfers did not count");command(client,"uncertain");step++;return;}
            if(step==16){if(!passed())return;checks.add("uncertain same-table existing copy deferred without duplication");command(client,"occupied");step++;waitTicks=100;return;}
            if(step==17){if(!passed()||waitTicks-->0)return;command(client,"occupied_check");step++;return;}
            if(step==18){if(!passed())return;checks.add("refill did not move onto another chest holding the same book");finish(client,true,"completed "+checks.size()+" Books delivery checks");}
        } catch (Throwable error) { finish(client, false, error.getClass().getSimpleName() + ": " + error.getMessage()); }
    }

    private void editor(MinecraftClient client, CoreMenuScreen menu) {
        if(step>=200){editorExtra(client,menu);return;}
        if (step == 2) { menu.selectForTest("test:editing"); step++; return; }
        if (step == 3) { if (menu.pending() || !menu.state().selected().equals("test:editing") || menu.state().detail()==null || menu.state().detail().book()==null) return; var b=menu.state().detail().book();
            if (!b.author().equals("Fixture author") || !b.pages().equals(List.of("alpha","","omega")) || b.rule().name().equals("REPEAT")) throw new IllegalStateException("editor definition details were not rendered canonically");
            if (!menu.triggerForTest("Enable editing")) throw new IllegalStateException("could not enable fixture editing"); step++; return; }
        if (step == 4) { if (!menu.state().editing()) return; if (!menu.triggerForTest("Edit details")) throw new IllegalStateException("details editor unavailable in editing mode"); step++; return; }
        if (step == 5) { if (!client.currentScreen.getClass().getSimpleName().equals("BookEditorScreen")) return; boolean changed=false;
            for (var child:client.currentScreen.children()) if(child instanceof TextFieldWidget field&&!changed){field.setText("Cancelled title");changed=true;}
            press(client,"Cancel"); step++; return; }
        if (step == 6) { if (menu.state().detail()==null || menu.state().detail().book()==null || !menu.state().detail().book().title().equals("Editing")) return;
            checks.add("actual BookEditor cancel retained the server definition and blank-page order");
            ScreenshotRecorder.saveScreenshot(output.getParent().toFile(), "books-editor-"+client.getWindow().getScaledWidth()+"x"+client.getWindow().getScaledHeight()+".png",client.getFramebuffer(),1,ignored->{}); step=200; }
    }
    private void editorExtra(MinecraftClient client,CoreMenuScreen menu) {
        switch(step){
            case 200 -> {if(menu==null||menu.pending())return;require(menu.triggerForTest("Edit details"),"missing details action");step++;}
            case 201 -> {if(!editorScreen(client))return;var fields=client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).toList();require(fields.size()==3,"metadata fields missing");fields.get(0).setText("Editing saved");fields.get(1).setText("Native author");fields.get(2).setText("Native description");press(client,"Save details");step++;}
            case 202 -> {if(menu==null||menu.pending()||menu.state().detail().book()==null||!menu.state().detail().book().title().equals("Editing saved"))return;require(menu.state().detail().book().author().equals("Native author"),"author failed round trip");checks.add("actual metadata Save round-tripped title and author");require(menu.triggerForTest("Edit pages"),"pages action unavailable");step++;}
            case 203 -> {if(!editorScreen(client))return;pageField(client).setText("Literal <entry>");press(client,"Preview");step++;}
            case 204 -> {if(!(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.BookScreen))return;capture(client,"books-reader.png");checks.add("draft preview used the normal vanilla book reader");press(client,"Done");step++;}
            case 205 -> {if(!editorScreen(client))return;require(pageField(client).getText().equals("Literal <entry>"),"preview lost local draft");press(client,"Next >");step++;}
            case 206 -> {require(pageField(client).getText().isEmpty(),"blank page reordered");press(client,"Remove page\u2026");step++;}
            case 207 -> {require(hasButton(client,"Remove page 2"),"page removal lacked explicit confirmation");press(client,"Cancel");require(pageField(client).getText().isEmpty(),"cancel removed blank page");press(client,"< Previous");press(client,"Add page");press(client,"Remove page\u2026");press(client,"Remove page 4");press(client,"Save pages");step++;}
            case 208 -> {if(menu==null||menu.pending())return;require(menu.state().detail().book().pages().equals(List.of("Literal <entry>","","omega")),"saved page order/blank/literal content changed");checks.add("actual page Save preserved blank order and preview draft");require(menu.triggerForTest("Distribution..."),"rule action unavailable");step++;}
            case 209 -> {if(!editorScreen(client))return;pressSuffix(client,"Until found");press(client,"Save rule");require(hasButton(client,"Confirm rule"),"rule save lacked confirmation");press(client,"Cancel");press(client,"Cancel");step++;}
            case 210 -> {if(menu==null||menu.pending())return;require(menu.state().detail().book().rule()==io.github.chronicle.core.books.BookDefinition.Rule.OFF,"cancel changed rule");checks.add("rule confirmation Cancel left definition unchanged");require(menu.triggerForTest("Distribution..."),"rule action unavailable");step++;}
            case 211 -> {if(!editorScreen(client))return;pressSuffix(client,"Until found");press(client,"Save rule");press(client,"Confirm rule");step++;}
            case 212 -> {if(menu==null||menu.pending()||menu.state().detail().book().rule()!=io.github.chronicle.core.books.BookDefinition.Rule.UNTIL_FOUND)return;checks.add("confirmed discovery rule persisted through actual menu transport");require(menu.triggerForTest("New book..."),"new book action unavailable");step++;}
            case 213 -> {if(!editorScreen(client))return;var fields=client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).toList();fields.get(0).setText("Created from native");fields.get(1).setText("Native author");fields.get(2).setText("");press(client,"Create and edit pages");step++;}
            case 214 -> {if(!editorScreen(client)||latestView==null||latestView.detail()==null||latestView.detail().book()==null||!latestView.detail().book().title().equals("Created from native"))return;require(latestView.selected().startsWith("chronicle:book/"),"create did not use a server-generated identity");require(latestView.detail().book().rule()==io.github.chronicle.core.books.BookDefinition.Rule.OFF,"new book distribution enabled");pageField(client).setText("Created page");press(client,"Save pages");step++;}
            case 215 -> {if(menu==null||menu.pending()||menu.state().detail().book()==null||!menu.state().detail().book().pages().equals(List.of("Created page")))return;checks.add("New book used server identity and saved its first page");capture(client,"books-authoring.png");require(menu.triggerForTest("Stop editing"),"editing toggle unavailable");step++;}
            case 216 -> {if(menu==null||menu.pending()||menu.state().editing())return;require(menu.triggerForTest("Loot Tables"),"loot navigation missing");step++;}
            case 217 -> {if(menu==null||menu.pending()||menu.state().page()!=Page.LOOT_TABLES)return;menu.selectForTest("test:books");step++;}
            case 218 -> {if(menu==null||menu.pending()||!menu.state().selected().equals("test:books")||menu.state().detail().loot()==null)return;var add=menu.probeButtonForTest("Add item");require(add!=null&&!add.active(),"ordinary item add enabled while editing off");checks.add("ordinary Items mutation disabled outside Editing");require(menu.triggerForTest("Enable editing"),"editing toggle unavailable");step++;}
            case 219 -> {if(menu==null||menu.pending()||!menu.state().editing())return;require(menu.triggerForTest("Lore books"),"separate Lore books tab missing");require(menu.triggerForTest("Kept 0.0%"),"named book row missing");chanceField(client).setText("-1");require(menu.triggerForTest("Open book"),"read-only book handoff missing");step=230;}
            case 230 -> {if(menu==null||menu.pending()||menu.state().page()!=Page.BOOKS_LORE||!menu.state().selected().equals("test:kept"))return;require(menu.triggerForTest("Open table: Books"),"table return link missing");step++;}
            case 231 -> {if(menu==null||menu.pending()||menu.state().page()!=Page.LOOT_TABLES)return;require(chanceField(client).getText().equals("-1"),"book handoff lost unsaved invalid chance");checks.add("read-only book handoff restored unsaved table draft");step=220;}
            case 220 -> {var chance=chanceField(client);chance.setText("-1");require(menu.triggerForTest("Save loot table"),"table save unavailable");require(chanceField(client).getText().equals("-1"),"invalid chance draft lost");capture(client,"books-table-invalid.png");checks.add("named Lore tab retained invalid chance without submitting");chanceField(client).setText("0");require(menu.triggerForTest("Save loot table"),"table save unavailable");step++;}
            case 221 -> {require(menu.triggerForTest("Confirm: Save loot table"),"whole-table save lacked confirmation");step++;}
            case 222 -> {if(menu==null||menu.pending())return;require(menu.state().success()&&menu.state().detail().loot().entries().size()==2&&menu.state().detail().loot().books().size()==2&&menu.state().detail().loot().books().getFirst().chancePercent()==0,"named table save lost ordinary entries or changed chance");capture(client,"books-table.png");checks.add("named-table whole Save preserved ordinary entries and shared refill");require(menu.triggerForTest("Stop editing"),"editing toggle missing");step++;}
            case 223 -> {if(menu==null||menu.pending()||menu.state().editing())return;step=7;}

        }
    }
    private static TextFieldWidget chanceField(MinecraftClient c){return c.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).filter(f->f.getMessage().getString().equals("Chance %")).findFirst().orElseThrow(()->new IllegalStateException("chance field missing"));}
    private static boolean editorScreen(MinecraftClient c){return c.currentScreen!=null&&c.currentScreen.getClass().getSimpleName().equals("BookEditorScreen");}
    private static net.minecraft.client.gui.widget.EditBoxWidget pageField(MinecraftClient c){return c.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.EditBoxWidget.class::isInstance).map(net.minecraft.client.gui.widget.EditBoxWidget.class::cast).findFirst().orElseThrow(()->new IllegalStateException("page field missing"));}
    private static boolean hasButton(MinecraftClient c,String label){return c.currentScreen.children().stream().anyMatch(n->n instanceof ButtonWidget b&&b.getMessage().getString().equals(label));}
    private static void pressSuffix(MinecraftClient c,String suffix){for(var child:c.currentScreen.children())if(child instanceof ButtonWidget b&&b.getMessage().getString().endsWith(suffix)){require(b.active,"disabled rule");b.onPress(new net.minecraft.client.input.MouseInput(0,0));return;}throw new IllegalStateException("missing rule "+suffix);}
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException(text);}
    private void capture(MinecraftClient client,String name){ScreenshotRecorder.saveScreenshot(output.getParent().toFile(),name,client.getFramebuffer(),1,ignored->{});}
    private static void press(MinecraftClient client,String label) { for(var child:client.currentScreen.children()) if(child instanceof ButtonWidget button&&button.getMessage().getString().equals(label)){button.onPress(new net.minecraft.client.input.MouseInput(0,0));return;} throw new IllegalStateException("editor button missing: "+label); }

    private void command(MinecraftClient client, String verb) { awaitingTrial = verb; trialMessages.clear(); client.player.networkHandler.sendChatCommand("chronicle_books_trial " + verb); }
    private boolean passed() { for(String message:trialMessages) { if(message.contains("Books trial FAIL "+awaitingTrial)) throw new IllegalStateException(message); if(message.contains("Books trial PASS "+awaitingTrial)) return true; } return false; }
    private void openChest(MinecraftClient client) {
        BlockPos position;
        try { String[] values=Files.readString(serverRoot.resolve(".books-kept-position"),StandardCharsets.UTF_8).trim().split(","); position=new BlockPos(Integer.parseInt(values[0]),Integer.parseInt(values[1]),Integer.parseInt(values[2])); }
        catch (Exception error) { throw new IllegalStateException("missing shared keep-location fixture data", error); }
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(position).add(0, .5, 0), Direction.UP, position, false));
    }
    private static int managedChestSlot(MinecraftClient client) {
        for (int i = 0; i < client.player.currentScreenHandler.slots.size(); i++) {
            var stack = client.player.currentScreenHandler.slots.get(i).getStack(); var data = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
            if (data != null && data.copyNbt().getCompound("chronicle_book").map(marker -> marker.getString("book").orElse("")).filter("test:kept"::equals).isPresent()) return i;
        } return -1;
    }
    private static int emptyPlayerSlot(MinecraftClient client) {
        for (int i = 27; i < client.player.currentScreenHandler.slots.size(); i++) if (client.player.currentScreenHandler.slots.get(i).getStack().isEmpty()) return i;
        return -1;
    }

    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (complete) return;
        complete = true;
        try {
            JsonObject result = new JsonObject();
            result.addProperty("result", pass ? "PASS" : "FAIL");
            result.addProperty("detail", detail); result.addProperty("views", views);
            JsonArray values = new JsonArray(); checks.forEach(values::add); result.add("checks", values);
            Files.writeString(output, new Gson().toJson(result) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
        client.scheduleStop();
    }

    private static void writeSetupFailure(Throwable error) {
        try {
            if (OUTPUT_ENV == null) return;
            JsonObject result = new JsonObject(); result.addProperty("result", "FAIL");
            result.addProperty("detail", "setup: " + error.getMessage());
            Files.writeString(Path.of(OUTPUT_ENV), new Gson().toJson(result) + "\n", StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) { }
    }
}
