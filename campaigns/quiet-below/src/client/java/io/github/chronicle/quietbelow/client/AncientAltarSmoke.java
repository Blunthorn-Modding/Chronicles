package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.chronicle.core.client.OfferingScreen;
import io.github.chronicle.core.stats.FoodCapacity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/** Opt-in disposable native rehearsal for durable Quiet Below Altar bargains. */
public final class AncientAltarSmoke {
    private static final String OUTPUT_ENV = System.getenv("CHRONICLE_BARGAIN_SMOKE");
    private static final String PHASE_ENV = System.getenv("CHRONICLE_BARGAIN_PHASE");
    private static final String ROLE_ENV = System.getenv("CHRONICLE_BARGAIN_ROLE");
    private static final String SERVER_ENV = System.getenv("CHRONICLE_BARGAIN_SERVER_DIR");
    private static final String ADDRESS = "127.0.0.1:25575";
    private static final long DEADLINE_NANOS = 600_000_000_000L;
    private static final Pattern BARGAIN_PASS = Pattern.compile("^Bargain trial PASS ([a-z_]+) \\(\\d+ checks\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORGE_PASS = Pattern.compile("^PASS Forge ([a-z_]+) \\(\\d+ checks\\)$", Pattern.CASE_INSENSITIVE);
    private final String phase, role;
    private final Path output, serverRoot, readyMarker, doneMarker;
    private final long deadline = System.nanoTime() + DEADLINE_NANOS;
    private final List<String> messages = new ArrayList<>(), checks = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private int index, baseline, maximumObservedFood;
    private long stepDeadline, until;
    private boolean connected, built, started, finished;
    private java.util.UUID closedToken;
    private Object optionalLayout;
    private Screen optionalEditor;
    private Path optionalLayoutFile;

    private AncientAltarSmoke(String phase, String role, Path output, Path serverRoot) throws IOException {
        if (!List.of("initial", "reconnect", "restart", "ended").contains(phase)) throw new IllegalArgumentException("Invalid CHRONICLE_BARGAIN_PHASE");
        if (!List.of("A", "B").contains(role)) throw new IllegalArgumentException("CHRONICLE_BARGAIN_ROLE must be A or B");
        this.phase = phase; this.role = role;
        this.output = output.toAbsolutePath().normalize(); this.serverRoot = serverRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.serverRoot)) throw new IllegalArgumentException("CHRONICLE_BARGAIN_SERVER_DIR must be a staged disposable server");
        if (this.output.startsWith(this.serverRoot)) throw new IllegalArgumentException("Bargain smoke output must be outside the staged server");
        Files.createDirectories(this.output);
        readyMarker = output.resolve("bargain-B-ready.txt"); doneMarker = output.resolve("bargain-A-done.txt");
    }

    public static void register() {
        if (OUTPUT_ENV == null || PHASE_ENV == null || ROLE_ENV == null) return;
        try {
            if (SERVER_ENV == null) throw new IllegalArgumentException("Bargain smoke staged server directory is required");
            var smoke = new AncientAltarSmoke(PHASE_ENV, ROLE_ENV, Path.of(OUTPUT_ENV), Path.of(SERVER_ENV));
            ClientReceiveMessageEvents.GAME.register((text, overlay) -> { if (!overlay && !smoke.finished) smoke.messages.add(text.getString()); });
            ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
        } catch (IOException | RuntimeException error) { writeSetupFailure(error); MinecraftClient.getInstance().scheduleStop(); }
    }

    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            require(System.nanoTime() < deadline, "Bargain smoke overall timeout");
            if (client.player == null && client.currentScreen instanceof AccessibilityOnboardingScreen) throw new IllegalStateException("unexpected accessibility onboarding screen");
            if (client.player == null) dismissOptionalWelcome(client);
            if (!connected && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connected = true;
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS), new ServerInfo("Chronicle Altar fixture", ADDRESS, ServerInfo.ServerType.OTHER), false, null);
                return;
            }
            if (client.player == null) return;
            maximumObservedFood=Math.max(maximumObservedFood,client.player.getHungerManager().getFoodLevel());
            require(client.getCurrentServerEntry() != null && ADDRESS.equals(client.getCurrentServerEntry().address), "Altar fixture address mismatch");
            if (!built) { if (role.equals("A")) buildA(client); else buildB(); built = true; }
            if (index >= steps.size()) { finish(client, true, "Bargain native rehearsal complete"); return; }
            Step step = steps.get(index);
            if (!started) { baseline = messages.size(); stepDeadline = System.nanoTime() + step.timeoutMillis * 1_000_000L; step.start.run(); started = true; }
            if (step.done.getAsBoolean()) { checks.add(step.name); index++; started = false; }
            else require(System.nanoTime() < stepDeadline, "timed out: " + step.name);
        } catch (Throwable error) { finish(client, false, error.getClass().getSimpleName() + ": " + error.getMessage()); }
    }

    private void buildA(MinecraftClient client) {
        if (phase.equals("restart")) { buildRestart(client); return; }
        if (phase.equals("reconnect")) { buildReconnect(client); return; }
        commandForge("prepare the shared Forge prerequisite", "quiet_below_forge_trial prepare", "prepare");
        delay("wait at least 120 Forge ticks", 120);
        commandForge("verify shared Forge fixture ready", "quiet_below_forge_trial ready", "ready");
        command("verify native ledger basics, replay, and foreign-token rejection", "chronicle_bargain_trial basic", "basic");
        command("verify native food regeneration", "chronicle_bargain_trial food", "food", 35_000);
        step("native food packets carried26 rather than clamping20",()->{},()->maximumObservedFood>=26,5_000);
        command("place the real Ancient Altar", "chronicle_bargain_trial prepare", "prepare");
        step("wait for passive second player", () -> { }, () -> Files.isRegularFile(readyMarker), 40_000);
        command("verify cross-token, pause, creative, distance, replacement, and native safety", "chronicle_bargain_trial safety", "safety");
        delay("allow physical network reconnection after the cut",120);
        command("open the real Altar offering", "chronicle_bargain_trial open", "open");
        workstationFlows(client);
        offerChoiceZero(client);
        command("reopen Altar for movement offering", "chronicle_bargain_trial open", "open");
        offerChoiceOne(client);
        command("verify six real Altar receipts and disabled fourth acceptance", "chronicle_bargain_trial ui_check", "ui_check");
        command("pose four original Altar facings","chronicle_bargain_trial render","render");
        delay("wait for model gallery rendering",20);
        screenshot("capture four Altar facings",client,"altar-gallery.png");
        command("supply actual Altar held model","chronicle_bargain_trial itemrender","itemrender");
        step("select actual Altar item on the native client",()->{
            client.player.getInventory().setSelectedSlot(8);
            client.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(8));
        },()->client.player.getMainHandStack().isOf(io.github.chronicle.quietbelow.QuietBelow.ANCIENT_ALTAR),10_000);
        delay("wait for held item equip animation",12);
        screenshot("capture actual held and inventory Altar",client,"altar-held.png");
        command("kill the real purchaser", "chronicle_bargain_trial death", "death");
        step("request native client respawn after the real death screen", () -> { }, () -> {
            if (client.currentScreen instanceof DeathScreen) { client.player.requestRespawn(); client.setScreen(null); return false; }
            return client.currentScreen == null && client.player.isAlive();
        }, 30_000);
        command("verify death persistence", "chronicle_bargain_trial respawn_check", "respawn_check");
        requireFood(client, 26, "death persistence must keep 26 food capacity");
        command("kill with an unaccounted persistent sacrifice","chronicle_bargain_trial orphan_death","orphan_death");
        step("respawn without refunding unknown price",()->{},()->{
            if(client.currentScreen instanceof DeathScreen){client.player.requestRespawn();client.setScreen(null);return false;}
            return client.currentScreen==null&&client.player.isAlive();
        },30_000);
        command("verify unknown-price death hold and explicit repair","chronicle_bargain_trial orphan_respawn_repair","orphan_respawn_repair");
        command("pause and safely save the bargain fixture", "chronicle_bargain_trial save", "save");
        screenshot("capture real thirteen-slot native food HUD", client, "altar-food-13.png");
    }

    private void buildB() {
        if(phase.equals("ended")){
            command("offline character loses both trade sides on login","chronicle_bargain_trial ended","ended");
            step("offline character food capacity returns20",()->{},()->FoodCapacity.capacity(MinecraftClient.getInstance().player)==20,15_000);return;
        }
        if (!phase.equals("initial")) { step("no passive account needed outside initial phase", () -> { }, () -> true, 1_000); return; }
        command("verify independent second account identity", "chronicle_bargain_trial identity", "identity");
        step("announce second Altar account ready", () -> writeMarker(readyMarker), () -> true, 2_000);
        step("wait for active account completion", () -> { }, () -> Files.isRegularFile(doneMarker), 500_000);
    }

    private void buildReconnect(MinecraftClient client) {
        command("verify reconnect receipts and attributes", "chronicle_bargain_trial restart", "restart");
        requireFood(client, 26, "reconnect must synchronize 26 food capacity");
        optionalHudEditor(client);
    }

    private void buildRestart(MinecraftClient client) {
        command("verify persisted receipts and attributes after server restart", "chronicle_bargain_trial restart", "restart");
        requireFood(client, 26, "server restart must synchronize 26 food capacity");
        command("verify held corruption repair", "chronicle_bargain_trial corrupt", "corrupt", 15_000);
        command("end the campaign and remove both sides of bargains", "chronicle_bargain_trial ending", "ending");
        command("verify campaign-ending cleanup", "chronicle_bargain_trial ended", "ended");
        step("verify native stats returned after ending", () -> { }, () -> FoodCapacity.capacity(client.player) == 20
                && Math.abs(client.player.getMovementSpeed() - .1f) < .015f && client.player.getMaxHealth() == 20f, 20_000);
        screenshot("capture returned vanilla food HUD", client, "altar-food-returned.png");
    }

    private void workstationFlows(MinecraftClient client) {
        step("native handler and default-font terms fit",()->{},()->{
            if(!(client.currentScreen instanceof OfferingScreen screen))return false;
            require(screen.getScreenHandler()==client.player.currentScreenHandler,"native handler mismatch");
            require(screen.getScreenHandler().slots.size()==36,"workstation does not have exactly36 player slots");
            for(var choice:screen.state().choices()){
                require(client.textRenderer.getWidth("Cost: "+choice.cost())<=276,"cost clipped in native font");
                require(client.textRenderer.getWidth("Bonus: "+choice.bonus())<=276,"bonus clipped in native font");
            }
            return true;
        },15_000);
        command("supply named inventory proof stacks","chronicle_bargain_trial inventory_prepare","inventory_prepare");
        step("wait for proof inventory sync",()->{},()->client.player.getInventory().getStack(9).getCount()==7,10_000);
        step("real shift-click moves bread into player hotbar",()->slotClick(client,0,1),()->client.player.getInventory().getStack(0).getCount()==7&&client.player.getInventory().getStack(9).isEmpty(),10_000);
        step("real number-key swaps player inventory slots",()->{
            OfferingScreen screen=(OfferingScreen)client.currentScreen;
            try{var f=net.minecraft.client.gui.screen.ingame.HandledScreen.class.getDeclaredField("focusedSlot");f.setAccessible(true);f.set(screen,screen.getScreenHandler().slots.get(0));}
            catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            require(screen.keyPressed(new net.minecraft.client.input.KeyInput(50,0,0)),"number-key was not handled");
        },()->client.player.getInventory().getStack(9).isOf(net.minecraft.item.Items.IRON_INGOT)&&client.player.getInventory().getStack(1).isEmpty(),10_000);
        step("ordinary mouse click takes named stack to cursor",()->slotClick(client,27,0),()->client.player.currentScreenHandler.getCursorStack().getCount()==7,10_000);
        step("ordinary mouse click returns stack without Altar storage",()->slotClick(client,27,0),()->client.player.currentScreenHandler.getCursorStack().isEmpty()&&client.player.getInventory().getStack(0).getCount()==7,10_000);
        command("server verifies exact item counts/components and no purchase","chronicle_bargain_trial inventory_check","inventory_check");
        step("resize workstation to exact640x480 at GUI2",()->{client.options.getGuiScale().setValue(2);client.getWindow().setWindowedSize(640,480);},()->client.getWindow().getFramebufferWidth()==640&&client.getWindow().getFramebufferHeight()==480&&client.currentScreen.width==320,10_000);
        screenshot("native workstation640x480",client,"altar-workstation-640x480.png");
        step("resize workstation to exact1280x720 at GUI2",()->client.getWindow().setWindowedSize(1280,720),()->client.getWindow().getFramebufferWidth()==1280&&client.getWindow().getFramebufferHeight()==720&&client.currentScreen.width==640,10_000);
        screenshot("native workstation1280x720",client,"altar-workstation-1280x720.png");
        command("age only the opt-in offer beyond its60s limit","chronicle_bargain_trial expire","expire");
        step("expired offer is refreshable but cannot be accepted",()->{},()->client.currentScreen instanceof OfferingScreen screen&&screen.state().completed()&&!screen.triggerForTest("confirm"),10_000);
        step("real R refresh obtains fresh authoritative offer",()->require(client.currentScreen.keyPressed(new net.minecraft.client.input.KeyInput(82,0,0)),"Refresh key unhandled"),()->client.currentScreen instanceof OfferingScreen screen&&!screen.pending()&&!screen.state().completed(),10_000);
        command("expiry/refresh created no receipt","chronicle_bargain_trial no_purchase","no_purchase");
        command("cut power while workstation remains open","chronicle_bargain_trial cut_power","cut_power");
        step("live unpowered view prevents acceptance",()->{},()->client.currentScreen instanceof OfferingScreen screen&&!screen.state().stationReady()&&!screen.triggerForTest("confirm"),15_000);
        screenshot("native unpowered workstation",client,"altar-workstation-unpowered.png");
        command("restore physical Altar network","chronicle_bargain_trial restore_power","restore_power");
        step("live power resumes without reopening",()->{},()->client.currentScreen instanceof OfferingScreen screen&&screen.state().stationReady(),20_000);
        command("move outside physical Altar range","chronicle_bargain_trial out_of_reach","out_of_reach");
        step("out-of-reach keeps readable disabled workstation",()->{},()->client.currentScreen instanceof OfferingScreen screen&&!screen.state().stationReady()&&screen.state().stationProblem().contains("out of reach")&&!screen.triggerForTest("confirm"),10_000);
        command("return to physical Altar","chronicle_bargain_trial return_to_altar","return_to_altar");
        step("returning restores current station view",()->{},()->client.currentScreen instanceof OfferingScreen screen&&screen.state().stationReady(),20_000);
        step("keyboard Right selects movement via server",()->require(client.currentScreen.keyPressed(new net.minecraft.client.input.KeyInput(262,0,0)),"Right not handled"),()->selected(client,1)&&!((OfferingScreen)client.currentScreen).pending(),10_000);
        step("Escape closes refreshed successor",()->{
            closedToken=((OfferingScreen)client.currentScreen).state().token();
            require(client.currentScreen.keyPressed(new net.minecraft.client.input.KeyInput(256,0,0)),"Escape not handled");
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new io.github.chronicle.core.network.OfferingPayloads.Action(closedToken,1,1));
        },()->client.currentScreen==null,10_000);
        command("closed token replay cannot commit","chronicle_bargain_trial closed_check","closed_check");
        command("open registered16-choice Core group","chronicle_bargain_trial pages","pages");
        step("native generic group has16 choices",()->{},()->client.currentScreen instanceof OfferingScreen screen&&screen.state().choices().size()==16,10_000);
        for(int page=1;page<8;page++)step("Core page next "+page,()->require(((OfferingScreen)client.currentScreen).triggerForTest("next"),"next page unavailable"),()->true,1_000);
        step("last visible choice preserves server index15",()->require(((OfferingScreen)client.currentScreen).triggerForTest("choice1"),"last choice unavailable"),()->selected(client,15)&&!((OfferingScreen)client.currentScreen).pending(),10_000);
        screenshot("native generic last page",client,"altar-workstation-page8.png");
        step("close generic group and send late acceptance",()->{
            closedToken=((OfferingScreen)client.currentScreen).state().token();
            client.currentScreen.close();
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new io.github.chronicle.core.network.OfferingPayloads.Action(closedToken,1,15));
        },()->client.currentScreen==null,10_000);
        delay("allow queued native server closure to settle",5);
        command("generic group close leaves no live token","chronicle_bargain_trial closed_check","closed_check");
        command("reopen clean workstation for purchases","chronicle_bargain_trial open","open");
    }
    private void slotClick(MinecraftClient client,int index,int modifiers){
        require(client.currentScreen instanceof OfferingScreen,"workstation closed before slot click");
        OfferingScreen screen=(OfferingScreen)client.currentScreen;var slot=screen.getScreenHandler().slots.get(index);
        var click=new net.minecraft.client.gui.Click((screen.width-300)/2+slot.x+8,(screen.height-222)/2+slot.y+8,new net.minecraft.client.input.MouseInput(0,modifiers));
        screen.mouseClicked(click,false);screen.mouseReleased(click);
    }

    private void offerChoiceZero(MinecraftClient client) {
        step("await real OfferingScreen for food bargain", () -> { }, () -> client.currentScreen instanceof OfferingScreen, 25_000);
        command("apply foreign modifiers during open workstation","chronicle_bargain_trial preview_foreign","preview_foreign");
        step("server typed preview includes foreign modifiers",()->{},()->{
            if(!(client.currentScreen instanceof OfferingScreen screen))return false;
            var choice=screen.state().choices().get(0);
            return choice.before().health()==24&&Math.abs(choice.before().speed()-.12)<.00001&&choice.after()!=null&&choice.after().health()==26&&Math.abs(choice.after().speed()-.12)<.00001;
        },10_000);
        command("remove foreign preview modifiers","chronicle_bargain_trial preview_clean","preview_clean");
        step("live preview returns to native stats",()->{},()->client.currentScreen instanceof OfferingScreen screen&&screen.state().choices().get(0).before().health()==20,10_000);
        screenshot("capture complete first bargain consent",client,"altar-offering-preview.png");
        for (int count = 1; count <= 3; count++) {
            int expected = count;
            step("accept food bargain " + count, () -> accept(client, "choice0"), () -> offerCount(client, 0, expected), 25_000);
            step("food capacity synchronizes to " + (20 + count * 2), () -> { }, () -> FoodCapacity.capacity(client.player) == 20 + expected * 2, 20_000);
        }
        screenshot("capture OfferingScreen and thirteen food icons", client, "altar-offering-food.png");
        step("close completed food offer", () -> client.setScreen(null), () -> client.currentScreen == null, 5_000);
    }

    private void offerChoiceOne(MinecraftClient client) {
        step("await real OfferingScreen for movement bargain", () -> { }, () -> client.currentScreen instanceof OfferingScreen, 25_000);
        step("select movement offering", () -> require(((OfferingScreen) client.currentScreen).triggerForTest("choice1"), "choice1 unavailable"), () -> selected(client, 1), 15_000);
        for (int count = 1; count <= 3; count++) {
            int expected = count;
            step("accept movement bargain " + count, () -> accept(client, "choice1"), () -> offerCount(client, 1, expected), 25_000);
        }
        step("verify three movement bargains", () -> { }, () -> Math.abs(client.player.getMovementSpeed() - .13f) < .015f, 20_000);
        step("fourth movement acceptance is disabled", () -> { }, () -> client.currentScreen instanceof OfferingScreen screen && !screen.triggerForTest("confirm"), 10_000);
        screenshot("native both completed workstation",client,"altar-workstation-completed.png");
        command("cut power after both bargain caps","chronicle_bargain_trial cut_power","cut_power");
        step("maxed and unpowered remain independent states",()->{},()->client.currentScreen instanceof OfferingScreen screen&&screen.state().choices().stream().allMatch(c->c.count()==3)&&!screen.state().stationReady()&&!screen.triggerForTest("confirm"),15_000);
        screenshot("native maxed and unpowered workstation",client,"altar-workstation-completed-unpowered.png");
        command("restore power after capped-state check","chronicle_bargain_trial restore_power","restore_power");
        step("powered capped screen returns without purchase",()->{},()->client.currentScreen instanceof OfferingScreen screen&&screen.state().stationReady(),20_000);
        step("close completed movement offer", () -> client.setScreen(null), () -> client.currentScreen == null, 5_000);
    }

    private void accept(MinecraftClient client, String choice) {
        require(client.currentScreen instanceof OfferingScreen, "OfferingScreen closed before acceptance");
        OfferingScreen screen=(OfferingScreen)client.currentScreen;
        int selected = Integer.parseInt(choice.substring("choice".length()));
        require(screen.state().selected()==selected,"expected selected offering before accepting");
        if(screen.state().choices().get(selected).count()%2==0){
            var click=new net.minecraft.client.gui.Click((screen.width-300)/2+127,(screen.height-222)/2+118,new net.minecraft.client.input.MouseInput(0,0));
            require(screen.mouseClicked(click,false),"accept mouse click unhandled");screen.mouseReleased(click);
        }else{
            var button=screen.children().stream().filter(value->value instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("Make permanent bargain")).findFirst().orElseThrow();
            screen.setFocused(button);
            require(screen.keyPressed(new net.minecraft.client.input.KeyInput(257,0,0)),"focused accept Enter unhandled");
        }
        require(screen.pending(),"accept did not enter waiting state");
    }
    private boolean offerCount(MinecraftClient client, int choice, int count) { return client.currentScreen instanceof OfferingScreen screen && screen.state().choices().get(choice).count() == count; }
    private boolean selected(MinecraftClient client, int choice) { return client.currentScreen instanceof OfferingScreen screen && screen.state().selected() == choice; }
    private void requireFood(MinecraftClient client, int expected, String failure) { step("client food capacity is " + expected, () -> { }, () -> FoodCapacity.capacity(client.player) == expected, 20_000); }

    private void optionalHudEditor(MinecraftClient client) {
        step("verify optional HUD dependencies are present only for reconnect", () -> { }, () -> {
            for (String mod : List.of("spiffyhud", "fancymenu", "konkrete", "melody")) require(FabricLoader.getInstance().isModLoaded(mod), "missing optional HUD mod " + mod);
            return true;
        }, 10_000);
        step("verify original Chronicle FancyMenu builder registration", () -> { }, () -> fancyBuilder() != null, 15_000);
        step("open optional HUD editor with original Chronicle element", () -> openOptionalEditor(client), () -> optionalEditor != null && client.currentScreen == optionalEditor, 15_000);
        screenshot("capture editable Chronicle food element at 640x480", client, "altar-hud-editor-640x480.png");
        step("save a moved Chronicle food element through public editor API", this::saveOptionalEditor, () -> optionalLayoutFile != null && Files.isRegularFile(optionalLayoutFile), 15_000);
        step("reload saved optional HUD layout", this::reloadOptionalLayout, this::optionalLayoutReloaded, 15_000);
        step("close optional editor to render actual thirteen-slot food HUD", () -> client.setScreen(null), () -> client.currentScreen == null, 5_000);
        screenshot("capture optional Chronicle food element runtime", client, "altar-hud-runtime-640x480.png");
        step("resize optional HUD runtime", () -> client.getWindow().setWindowedSize(1280, 720), () -> client.getWindow().getFramebufferWidth() == 1280 && client.getWindow().getFramebufferHeight() == 720, 10_000);
        screenshot("capture optional Chronicle food element at 1280x720", client, "altar-hud-runtime-1280x720.png");
    }

    private Object fancyBuilder() {
        try { return Class.forName("de.keksuccino.fancymenu.customization.element.ElementRegistry").getMethod("getBuilder", String.class).invoke(null, "chronicle_core_expanded_food"); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("FancyMenu public builder lookup failed", error); }
    }
    /** The optional dependency's introductory documentation is not a server agreement. */
    private void dismissOptionalWelcome(MinecraftClient client) {
        if (client.currentScreen == null || !client.currentScreen.getClass().getName().equals("de.keksuccino.fancymenu.WelcomeScreen")) return;
        try {
            Object options = Class.forName("de.keksuccino.fancymenu.FancyMenu").getMethod("getOptions").invoke(null);
            Object setting = options.getClass().getField("showWelcomeScreen").get(options);
            setting.getClass().getMethod("setValue", Object.class).invoke(setting, false);
            client.currentScreen.close();
            checks.add("closed optional FancyMenu documentation welcome in disposable reconnect profile");
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("could not close optional FancyMenu welcome", error); }
    }
    @SuppressWarnings("unchecked")
    private void openOptionalEditor(MinecraftClient client) {
        try {
            Class<?> overlayType = Class.forName("de.keksuccino.spiffyhud.customization.SpiffyOverlayScreen");
            Screen overlay = (Screen) overlayType.getConstructor(boolean.class).newInstance(true);
            Class<?> layoutType = Class.forName("de.keksuccino.fancymenu.customization.layout.Layout");
            optionalLayout = layoutType.getConstructor(Screen.class).newInstance(overlay);
            optionalLayoutFile = FabricLoader.getInstance().getGameDir().resolve("config/fancymenu/customization/chronicle_bargain_food.txt");
            Files.createDirectories(optionalLayoutFile.getParent());
            field(optionalLayout, "layoutFile", optionalLayoutFile.toFile());
            Object builder = fancyBuilder();
            Object element = builder.getClass().getMethod("buildDefaultInstance").invoke(builder);
            field(element, "posOffsetX", 37); field(element, "posOffsetY", -60);
            Class<?> base = Class.forName("de.keksuccino.fancymenu.customization.element.AbstractElement");
            Object serialized = builder.getClass().getMethod("serializeElementInternal", base).invoke(builder, element);
            ((List<Object>) optionalLayout.getClass().getField("serializedElements").get(optionalLayout)).add(serialized);
            optionalEditor = (Screen) Class.forName("de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen")
                    .getConstructor(Screen.class, layoutType).newInstance(overlay, optionalLayout);
            client.setScreen(optionalEditor);
        } catch (ReflectiveOperationException | IOException error) { throw new IllegalStateException("could not open public FancyMenu editor", error); }
    }
    private void saveOptionalEditor() {
        try {
            List<?> elements = (List<?>) optionalEditor.getClass().getField("normalEditorElements").get(optionalEditor);
            require(elements.size() == 1, "editor did not build exactly one Chronicle food element");
            Object element = elements.getFirst().getClass().getField("element").get(elements.getFirst());
            field(element, "posOffsetX", 67);
            optionalEditor.getClass().getMethod("saveLayout").invoke(optionalEditor);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("could not save public FancyMenu editor layout", error); }
    }
    private void reloadOptionalLayout() {
        try { Class.forName("de.keksuccino.fancymenu.customization.layout.LayoutHandler").getMethod("reloadLayouts").invoke(null); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("could not reload public FancyMenu layouts", error); }
    }
    private boolean optionalLayoutReloaded() {
        try {
            List<?> layouts = (List<?>) Class.forName("de.keksuccino.fancymenu.customization.layout.LayoutHandler").getMethod("getAllLayouts").invoke(null);
            Object reloaded = null;
            for (Object candidate : layouts) if (optionalLayoutFile.toFile().equals(candidate.getClass().getField("layoutFile").get(candidate))) reloaded = candidate;
            require(reloaded != null, "saved Chronicle food layout missing after reload");
            Object collection = reloaded.getClass().getMethod("buildElementInstances").invoke(reloaded);
            List<Object> elements = new ArrayList<>((List<Object>) collection.getClass().getField("foregroundElements").get(collection));
            elements.addAll((List<Object>) collection.getClass().getField("backgroundElements").get(collection));
            for (Object element : elements) {
                Object builder = element.getClass().getField("builder").get(element);
                if ("chronicle_core_expanded_food".equals(builder.getClass().getMethod("getIdentifier").invoke(builder)))
                    return element.getClass().getField("posOffsetX").getInt(element) == 67;
            }
            throw new IllegalStateException("reloaded Chronicle food element missing");
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("could not inspect public FancyMenu reload", error); }
    }
    private static void field(Object object, String name, Object value) throws ReflectiveOperationException { object.getClass().getField(name).set(object, value); }

    private void command(String name, String command, String verb) { command(name, command, verb, 30_000); }
    private void command(String name, String command, String verb, long timeout) { step(name, () -> send(command), () -> received(BARGAIN_PASS, verb), timeout); }
    private void commandForge(String name, String command, String verb) { step(name, () -> send(command), () -> received(FORGE_PASS, verb), 30_000); }
    private void send(String command) { MinecraftClient.getInstance().player.networkHandler.sendChatCommand(command); }
    private void delay(String name, int ticks) { step(name, () -> until = System.nanoTime() + ticks * 50_000_000L, () -> System.nanoTime() >= until, ticks * 50L + 5_000); }
    private boolean received(Pattern pattern, String verb) {
        for (String message : messages.subList(baseline, messages.size())) {
            if (message.toLowerCase().contains("trial fail") || message.toLowerCase().startsWith("fail forge")) throw new IllegalStateException(message);
            if (pattern.matcher(message).matches() && message.toLowerCase().contains(verb.toLowerCase())) return true;
        }
        return false;
    }
    private void screenshot(String name, MinecraftClient client, String filename) {
        boolean[] requested = {false};
        step(name, () -> { client.inGameHud.getChatHud().clear(false); client.getToastManager().clear(); until = System.nanoTime() + 100_000_000L; }, () -> {
            if (System.nanoTime() < until) return false;
            if (!requested[0]) { ScreenshotRecorder.saveScreenshot(output.toFile(), filename, client.getFramebuffer(), 1, ignored -> { }); requested[0] = true; }
            return validPng(output.resolve("screenshots").resolve(filename));
        }, 20_000);
    }
    private static boolean validPng(Path path) {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            if (channel.size() < 32) return false;
            ByteBuffer header = ByteBuffer.allocate(8), trailer = ByteBuffer.allocate(12);
            while (header.hasRemaining()) if (channel.read(header) < 0) return false;
            channel.position(channel.size() - 12); while (trailer.hasRemaining()) if (channel.read(trailer) < 0) return false;
            byte[] h = header.array(), t = trailer.array();
            return (h[0] & 0xFF) == 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47 && t[4] == 0x49 && t[5] == 0x45 && t[6] == 0x4E && t[7] == 0x44 && (t[8] & 0xFF) == 0xAE && (t[9] & 0xFF) == 0x42 && (t[10] & 0xFF) == 0x60 && (t[11] & 0xFF) == 0x82;
        } catch (IOException error) { return false; }
    }
    private void step(String name, Runnable start, BooleanSupplier done, long timeoutMillis) { steps.add(new Step(name, start, done, timeoutMillis)); }
    private void writeMarker(Path path) { try { Files.writeString(path, role + " connected\n", StandardCharsets.UTF_8); } catch (IOException error) { throw new IllegalStateException("could not write Bargain coordination marker", error); } }
    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (finished) return; finished = true;
        if (role.equals("A") && phase.equals("initial")) writeMarker(doneMarker);
        try { JsonObject result = new JsonObject(); result.addProperty("result", pass ? "PASS" : "FAIL"); result.addProperty("phase", phase); result.addProperty("role", role); result.addProperty("detail", detail); result.add("checks", new Gson().toJsonTree(checks)); result.add("messages", new Gson().toJsonTree(messages)); Files.writeString(output.resolve("bargain-" + phase + "-" + role + ".json"), new Gson().toJson(result) + "\n", StandardCharsets.UTF_8); }
        catch (IOException error) { error.printStackTrace(); }
        client.scheduleStop();
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private record Step(String name, Runnable start, BooleanSupplier done, long timeoutMillis) { }
    private static void writeSetupFailure(Throwable error) {
        try { if (OUTPUT_ENV == null) return; Path output = Path.of(OUTPUT_ENV).toAbsolutePath().normalize(); Files.createDirectories(output); JsonObject result = new JsonObject(); result.addProperty("result", "FAIL"); result.addProperty("phase", String.valueOf(PHASE_ENV)); result.addProperty("role", String.valueOf(ROLE_ENV)); result.addProperty("detail", "setup: " + error.getMessage()); Files.writeString(output.resolve("bargain-" + PHASE_ENV + "-" + ROLE_ENV + ".json"), new Gson().toJson(result) + "\n", StandardCharsets.UTF_8); } catch (IOException | RuntimeException ignored) { }
    }
}
