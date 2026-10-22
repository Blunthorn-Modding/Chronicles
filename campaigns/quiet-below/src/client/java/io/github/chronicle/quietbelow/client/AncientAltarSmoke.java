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
    private java.util.concurrent.CompletableFuture<Void> packReload;
    private List<String> originalPacks;
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
        command("supply inventory preservation proof","chronicle_bargain_trial proof_prepare","proof_prepare");
        command("open the real Altar offering", "chronicle_bargain_trial open", "open");
        workstationFlows(client);
        prayerChoices(client);
        command("inventory stays unchanged through six prayers","chronicle_bargain_trial proof_check","proof_check");
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
        requireFood(client, 20, "death persistence must keep normal20 food capacity");
        command("kill with an unaccounted persistent sacrifice","chronicle_bargain_trial orphan_death","orphan_death");
        step("respawn without refunding unknown price",()->{},()->{
            if(client.currentScreen instanceof DeathScreen){client.player.requestRespawn();client.setScreen(null);return false;}
            return client.currentScreen==null&&client.player.isAlive();
        },30_000);
        command("verify unknown-price death hold and explicit repair","chronicle_bargain_trial orphan_respawn_repair","orphan_respawn_repair");
        command("pause and safely save the bargain fixture", "chronicle_bargain_trial save", "save");
        screenshot("capture normal ten-icon native food HUD", client, "altar-food-normal.png");
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
        step("other player never receives private prayer replies",()->require(messages.stream().noneMatch(m->m.startsWith("You prayed, and the sculk answered.")),"prayer reply broadcast to passive player"),()->true,1_000);
    }

    private void buildReconnect(MinecraftClient client) {
        command("verify reconnect receipts and attributes", "chronicle_bargain_trial restart", "restart");
        requireFood(client, 20, "reconnect must synchronize normal20 food capacity");
        optionalHudEditor(client);
        command("open prayer popup with all approved optional HUD mods","chronicle_bargain_trial open","open");
        step("optional HUD mods preserve native slot-free prayer screen",()->{},()->client.currentScreen instanceof OfferingScreen screen&&screen.getScreenHandler().slots.isEmpty(),15_000);
        screenshot("capture popup with optional HUD dependencies",client,"altar-prayers-optional-hud.png");
        step("close optional HUD prayer popup",()->client.currentScreen.close(),()->client.currentScreen==null,5_000);
    }

    private void buildRestart(MinecraftClient client) {
        command("verify persisted receipts and attributes after server restart", "chronicle_bargain_trial restart", "restart");
        requireFood(client, 20, "server restart must synchronize normal20 food capacity");
        command("verify held corruption repair", "chronicle_bargain_trial corrupt", "corrupt", 15_000);
        command("end the campaign and remove both sides of bargains", "chronicle_bargain_trial ending", "ending");
        command("verify campaign-ending cleanup", "chronicle_bargain_trial ended", "ended");
        step("verify native stats returned after ending", () -> { }, () -> FoodCapacity.capacity(client.player) == 20
                && Math.abs(client.player.getMovementSpeed() - .1f) < .015f && client.player.getMaxHealth() == 20f, 20_000);
        screenshot("capture returned vanilla food HUD", client, "altar-food-returned.png");
    }

    private void workstationFlows(MinecraftClient client) {
        step("slot-free native prayer opens without mechanic fields",()->{},()->{
            if(!(client.currentScreen instanceof OfferingScreen screen))return false;
            require(screen.getScreenHandler()==client.player.currentScreenHandler,"native prayer handler mismatch");
            require(screen.getScreenHandler().slots.isEmpty(),"prayer contains inventory slots");
            require(screen.state().choices().get(0).label().equals("Pray for more life")&&screen.state().choices().get(1).label().equals("Pray for quicker feet"),"prayer labels wrong");
            String data=new Gson().toJson(screen.state());
            for(String forbidden:List.of("cost","bonus","count","health","food","speed","problem","revision"))require(!data.contains("\""+forbidden+"\""),"mechanics leaked in player view: "+forbidden);
            return true;
        },15_000);
        step("native popup640x480 GUI2",()->{client.options.getGuiScale().setValue(2);client.getWindow().setWindowedSize(640,480);},()->client.getWindow().getFramebufferWidth()==640&&client.getWindow().getFramebufferHeight()==480&&client.currentScreen.width==320,10_000);
        screenshot("capture native popup640",client,"altar-prayers-640x480.png");
        step("native popup1280x720 GUI2",()->client.getWindow().setWindowedSize(1280,720),()->client.getWindow().getFramebufferWidth()==1280&&client.getWindow().getFramebufferHeight()==720&&client.currentScreen.width==640,10_000);
        screenshot("capture native popup1280",client,"altar-prayers-1280x720.png");
        prayerResourcePack(client);
        command("age only opt-in offer beyond60s","chronicle_bargain_trial expire","expire");
        step("expired prayer has no technical disclosure",()->{},()->client.currentScreen instanceof OfferingScreen screen&&screen.state().state()==io.github.chronicle.core.network.OfferingPayloads.State.EXPIRED&&!screen.triggerForTest("choice0"),10_000);
        step("R refresh requests fresh offer without a prayer",()->require(client.currentScreen.keyPressed(new net.minecraft.client.input.KeyInput(82,0,0)),"R refresh unhandled"),()->ready(client),10_000);
        command("refresh made no receipt","chronicle_bargain_trial no_purchase","no_purchase");
        command("cut power while popup open","chronicle_bargain_trial cut_power","cut_power");
        step("unpowered popup disables both prayers",()->{},()->quiet(client),15_000);
        screenshot("capture cryptic unavailable popup",client,"altar-prayers-unavailable.png");
        command("restore power","chronicle_bargain_trial restore_power","restore_power");
        step("live power resumes without reopening",()->{},()->ready(client),20_000);
        command("move out of reach","chronicle_bargain_trial out_of_reach","out_of_reach");
        step("range failure stays cryptic and disabled",()->{},()->quiet(client),10_000);
        command("return to Altar","chronicle_bargain_trial return_to_altar","return_to_altar");
        step("return restores prayer",()->{},()->ready(client),20_000);
        step("Escape revokes popup and late acceptance",()->closeWithReplay(client,0,true),()->client.currentScreen==null,10_000);
        delay("allow queued Escape closure",5);
        command("Escape late token cannot buy","chronicle_bargain_trial closed_check","closed_check");
        command("open generic16-choice group","chronicle_bargain_trial pages","pages");
        step("generic prayer group has16 choices",()->{},()->client.currentScreen instanceof OfferingScreen screen&&screen.state().choices().size()==16,10_000);
        for(int i=1;i<8;i++)step("generic next page "+i,()->require(((OfferingScreen)client.currentScreen).triggerForTest("next"),"next page unavailable"),()->true,1_000);
        step("last page exposes actual choice16 label",()->{},()->client.currentScreen.children().stream().anyMatch(v->v instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("Choice 16")),10_000);
        screenshot("capture generic page8",client,"altar-prayers-page8.png");
        step("Close button revokes generic popup and late index15",()->closeWithReplay(client,15,false),()->client.currentScreen==null,10_000);
        delay("allow queued native server closure",5);
        command("generic late token cannot buy","chronicle_bargain_trial closed_check","closed_check");
        command("reopen actual Altar","chronicle_bargain_trial open","open");
    }
    private void prayerChoices(MinecraftClient client){
        for(int n=1;n<=3;n++){
            int expected=n;
            if(n>1)command("reopen for life prayer "+n,"chronicle_bargain_trial open","open");
            step("await life prayer "+n,()->{},()->ready(client),15_000);
            step("pray for more life "+n,()->pray(client,0,expected),()->answered(client,true),15_000);
            command("life prayer "+n+" immediate price and lasting stats","chronicle_bargain_trial life_after","life_after");
            command("native exhaustion once at life tier "+n,"chronicle_bargain_trial exhaustion","exhaustion");
            if(n==1){
                command("set distinct health/food before late replay","chronicle_bargain_trial replay_prepare","replay_prepare");
                step("send old successful life token",()->net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new io.github.chronicle.core.network.OfferingPayloads.Action(closedToken,1,0)),()->true,1_000);
                delay("allow stale receipt replay",5);
                command("receipt replay does not reapply immediate payment","chronicle_bargain_trial replay_check","replay_check");
            }
        }
        command("reopen independently available feet prayer","chronicle_bargain_trial open","open");
        step("life cap hides its count but disables only life",()->{},()->client.currentScreen instanceof OfferingScreen screen&&screen.state().choices().get(0).complete()&&!screen.state().choices().get(0).available()&&screen.state().choices().get(1).available(),10_000);
        screenshot("capture one exhausted prayer",client,"altar-prayers-one-complete.png");
        for(int n=1;n<=3;n++){
            int expected=n;
            if(n==2)command("preserve stronger vanilla Slowness","chronicle_bargain_trial strong_slow","strong_slow");
            if(n==3)command("clear prior test effects before final feet prayer","chronicle_bargain_trial clear_slow","clear_slow");
            if(n>1)command("reopen for feet prayer "+n,"chronicle_bargain_trial open","open");
            step("await feet prayer "+n,()->{},()->ready(client),15_000);
            step("pray for quicker feet "+n,()->pray(client,1,expected),()->answered(client,false),15_000);
            command("feet prayer "+n+" uses native Slowness/merge","chronicle_bargain_trial feet_after","feet_after");
        }
        delay("vanilla Slowness timer advances",20);
        command("Slowness was not constantly refreshed","chronicle_bargain_trial slow_timer","slow_timer");
        command("reopen exhausted Altar","chronicle_bargain_trial open","open");
        step("both exhausted prayers stay disabled without counts",()->{},()->client.currentScreen instanceof OfferingScreen screen&&screen.state().state()==io.github.chronicle.core.network.OfferingPayloads.State.EXHAUSTED&&!screen.triggerForTest("choice0")&&!screen.triggerForTest("choice1"),10_000);
        screenshot("capture exhausted prayer popup",client,"altar-prayers-complete.png");
        command("cut power after both prayer caps","chronicle_bargain_trial cut_power","cut_power");
        step("maxed/unavailable state explicitly quiet",()->{},()->quiet(client),15_000);
        screenshot("capture exhausted unavailable popup",client,"altar-prayers-complete-unavailable.png");
        command("restore exhausted Altar power","chronicle_bargain_trial restore_power","restore_power");
        step("Close final popup",()->client.currentScreen.close(),()->client.currentScreen==null,10_000);
        step("client hunger input never receives server-only price",()->{
            var h=client.player.getHungerManager();float original=exhaustionValue(h);h.addExhaustion(40);h.addExhaustion(-40);h.addExhaustion(2);
            require(exhaustionValue(h)==2,"client exhaustion double scaled");h.addExhaustion(original-2);
        },()->true,1_000);
    }
    private void pray(MinecraftClient client,int choice,int count){
        require(client.currentScreen instanceof OfferingScreen,"prayer popup absent");OfferingScreen screen=(OfferingScreen)client.currentScreen;
        closedToken=screen.state().token();
        if(count%2==1){
            var click=new net.minecraft.client.gui.Click((screen.width-224)/2+112,(screen.height-132)/2+(choice==0?58:84),new net.minecraft.client.input.MouseInput(0,0));
            require(screen.mouseClicked(click,false),"prayer mouse click unhandled");screen.mouseReleased(click);
        }else{
            String label=choice==0?"Pray for more life":"Pray for quicker feet";
            var button=screen.children().stream().filter(v->v instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals(label)).findFirst().orElseThrow();
            screen.setFocused(button);require(screen.keyPressed(new net.minecraft.client.input.KeyInput(257,0,0)),"prayer Enter unhandled");
        }
        require(screen.pending(),"prayer did not await authority");
        require(!screen.triggerForTest("choice0")&&!screen.triggerForTest("choice1"),"pending prayer allows repeat input");
    }
    private boolean answered(MinecraftClient client,boolean life){
        return client.currentScreen==null&&messages.subList(baseline,messages.size()).stream().anyMatch(m->m.startsWith("You prayed, and the sculk answered.")&&(life?m.contains("pulses hungrily"):m.contains("light, then leaden")));
    }
    private boolean ready(MinecraftClient client){return client.currentScreen instanceof OfferingScreen screen&&screen.state().state()==io.github.chronicle.core.network.OfferingPayloads.State.READY&&!screen.pending();}
    private boolean quiet(MinecraftClient client){return client.currentScreen instanceof OfferingScreen screen&&screen.state().state()==io.github.chronicle.core.network.OfferingPayloads.State.UNAVAILABLE&&!screen.triggerForTest("choice0")&&!screen.triggerForTest("choice1");}
    private void closeWithReplay(MinecraftClient client,int index,boolean escape){
        OfferingScreen screen=(OfferingScreen)client.currentScreen;closedToken=screen.state().token();
        if(escape)require(screen.keyPressed(new net.minecraft.client.input.KeyInput(256,0,0)),"Escape unhandled");
        else require(screen.triggerForTest("close"),"Close unhandled");
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new io.github.chronicle.core.network.OfferingPayloads.Action(closedToken,1,index));
    }
    private void requireFood(MinecraftClient client,int expected,String failure){step("client food capacity is "+expected,()->{},()->FoodCapacity.capacity(client.player)==expected,20_000);}

    private void prayerResourcePack(MinecraftClient client){
        step("load original native sprite/font test resource pack",()->loadPrayerPack(client),()->packReload!=null&&packReload.isDone()&&!packReload.isCompletedExceptionally()&&client.getOverlay()==null,40_000);
        step("texture-pack font fits native prayer controls",()->{
            require(client.textRenderer.getWidth("Pray for quicker feet")<=188,"resource-pack font label clipped");
            require(client.currentScreen instanceof OfferingScreen,"resource reload lost native prayer screen");
        },()->true,5_000);
        screenshot("capture standard popup/buttons/font overridden by pack",client,"altar-prayers-resource-pack.png");
        step("restore vanilla resource selection",()->{
            client.getResourcePackManager().setEnabledProfiles(originalPacks);packReload=client.reloadResources();
        },()->packReload.isDone()&&!packReload.isCompletedExceptionally()&&client.getOverlay()==null,40_000);
    }
    private void loadPrayerPack(MinecraftClient client){
        try{
            Path root=FabricLoader.getInstance().getGameDir().resolve("resourcepacks/chronicle-prayer-contrast");
            Files.createDirectories(root);
            Files.writeString(root.resolve("pack.mcmeta"),"{\"pack\":{\"min_format\":[69,0],\"max_format\":[69,0],\"description\":\"Original Chronicle prayer GUI contrast fixture\"}}");
            Path assets=root.resolve("assets/minecraft");
            fixtureSprite(assets.resolve("textures/gui/sprites/popup/background.png"),236,34,0xFF142B43,0xFF87CFFF);
            Files.writeString(assets.resolve("textures/gui/sprites/popup/background.png.mcmeta"),"{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":236,\"height\":34,\"border\":6}}}");
            for(String name:List.of("button","button_highlighted","button_disabled"))fixtureSprite(assets.resolve("textures/gui/sprites/widget/"+name+".png"),200,20,name.equals("button_disabled")?0xFF26394A:0xFF164F78,0xFFB4E3FF);
            Path font=assets.resolve("font/default.json");Files.createDirectories(font.getParent());Files.writeString(font,"{\"providers\":[{\"type\":\"reference\",\"id\":\"minecraft:uniform\"}]}");
            var manager=client.getResourcePackManager();originalPacks=List.copyOf(manager.getEnabledIds());manager.scanPacks();
            String id=manager.getProfiles().stream().filter(v->v.getId().endsWith("chronicle-prayer-contrast")).findFirst().orElseThrow().getId();
            var enabled=new ArrayList<>(originalPacks);enabled.add(id);manager.setEnabledProfiles(enabled);packReload=client.reloadResources();
        }catch(IOException error){throw new IllegalStateException(error);}
    }
    private static void fixtureSprite(Path path,int width,int height,int interior,int border)throws IOException{
        Files.createDirectories(path.getParent());var png=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)png.setRGB(x,y,x<3||y<3||x>=width-3||y>=height-3?border:interior);
        javax.imageio.ImageIO.write(png,"PNG",path.toFile());
    }

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
        step("close optional editor to render actual normal ten-icon food HUD", () -> client.setScreen(null), () -> client.currentScreen == null, 5_000);
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
    private static float exhaustionValue(net.minecraft.entity.player.HungerManager hunger){
        var data=net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY);
        hunger.writeData(data);return data.getNbt().getFloat("foodExhaustionLevel",Float.NaN);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private record Step(String name, Runnable start, BooleanSupplier done, long timeoutMillis) { }
    private static void writeSetupFailure(Throwable error) {
        try { if (OUTPUT_ENV == null) return; Path output = Path.of(OUTPUT_ENV).toAbsolutePath().normalize(); Files.createDirectories(output); JsonObject result = new JsonObject(); result.addProperty("result", "FAIL"); result.addProperty("phase", String.valueOf(PHASE_ENV)); result.addProperty("role", String.valueOf(ROLE_ENV)); result.addProperty("detail", "setup: " + error.getMessage()); Files.writeString(output.resolve("bargain-" + PHASE_ENV + "-" + ROLE_ENV + ".json"), new Gson().toJson(result) + "\n", StandardCharsets.UTF_8); } catch (IOException | RuntimeException ignored) { }
    }
}
