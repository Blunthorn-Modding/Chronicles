package io.github.chronicle.lastcouncil.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ballots.BallotStatus;
import io.github.chronicle.core.client.DossierScreen;
import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.AdminRequest;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.AdminView;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.Notice;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.Operation;
import io.github.chronicle.lastcouncil.network.ProceedingsPayloads.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Opt-in native clients exercise only the normal authenticated network API. */
public final class CouncilBallotsSmoke {
    private static CouncilBallotsSmoke instance;
    private final Path root, output, shared;
    private final String role, phase;
    private final List<String> checks = new ArrayList<>();
    private final long started = System.nanoTime();
    private boolean connecting, done, competed;
    private int step, wait, callMessages, poolMessages;
    private UUID pending, motion, ballot, secretMotion, secretBallot, replacement;
    private AdminView admin;
    private io.github.chronicle.lastcouncil.network.CatalogueEditorPayloads.View catalogue;
    private CouncilPayloads.PersonalView personal;
    private io.github.chronicle.lastcouncil.network.ComplicationPayloads.View complicationSettings;
    private View view;
    private Vote vote;
    private Notice notice;
    private String houseB, houseC;
    private static final UUID NONE = CouncilPayloads.NONE;

    private CouncilBallotsSmoke(String path) {
        root = Path.of(System.getenv("CHRONICLE_BALLOTS_ROOT")).toAbsolutePath().normalize();
        output = Path.of(path).toAbsolutePath().normalize();
        shared = output.getParent();
        role = System.getenv("CHRONICLE_BALLOTS_ROLE");
        phase = System.getenv("CHRONICLE_BALLOTS_PHASE");
        if (root.getParent() == null || !root.getParent().getFileName().toString().equals("test-runs")
                || !Files.isRegularFile(root.resolve(".council-ballots-fixture")) || output.startsWith(root)
                || !Set.of("A", "B", "C").contains(role) || !Set.of("initial", "restart").contains(phase))
            throw new IllegalArgumentException("unsafe ballot fixture");
    }
    public static void register() {
        String path = System.getenv("CHRONICLE_BALLOTS_SMOKE");
        if (path == null) return;
        instance = new CouncilBallotsSmoke(path);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay && message.getString().equals("The Chancellor calls the houses to Council.")) instance.callMessages++;
            if(!overlay&&message.getString().equals("A trial bell rings across the council."))instance.poolMessages++;
        });
        ClientTickEvents.END_CLIENT_TICK.register(instance::tick);
    }
    public static void admin(AdminView value) { if (instance != null && value.request().equals(instance.pending)) instance.admin = value; }
    public static void catalogue(io.github.chronicle.lastcouncil.network.CatalogueEditorPayloads.View value) { if(instance!=null&&value.request().equals(instance.pending))instance.catalogue=value; }
    public static void complicationSettings(io.github.chronicle.lastcouncil.network.ComplicationPayloads.View value){if(instance!=null&&value.request().equals(instance.pending))instance.complicationSettings=value;}
    public static void personal(CouncilPayloads.PersonalView value) { if (instance != null && value.request().equals(instance.pending)) instance.personal = value; }
    public static void view(View value) { if (instance != null && value.request().equals(instance.pending)) instance.view = value; }
    public static void vote(Vote value) { if (instance != null && value.request().equals(instance.pending)) instance.vote = value; }
    public static void notice(Notice value) { if (instance != null && value.request().equals(instance.pending)) instance.notice = value; }
    private static UUID offline(String name) { return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)); }
    private Path marker(String name) { return shared.resolve(name); }
    private void mark(String name, String value) throws Exception { Files.writeString(marker(name), value, StandardCharsets.UTF_8); }
    private boolean has(String name) { return Files.isRegularFile(marker(name)); }
    private UUID readId(String name) throws Exception { return UUID.fromString(Files.readString(marker(name), StandardCharsets.UTF_8).trim()); }
    private void command(MinecraftClient c, String value) { c.player.networkHandler.sendChatCommand(value); }
    private void admin(Operation action, long revision, UUID target, String house) {
        long officeRevision = admin == null ? -1 : admin.officeRevision();
        pending = UUID.randomUUID(); admin = null; notice = null;
        ClientPlayNetworking.send(new AdminRequest(pending, action, revision, target, house, false, NONE, officeRevision));
    }
    private void request(Action action, long revision, UUID reference, String text, String title) {
        pending = UUID.randomUUID(); view = null; vote = null; notice = null;
        if(action==Action.BALLOT)BallotAccess.expectOpening(pending,MinecraftClient.getInstance().currentScreen,reference);
        ClientPlayNetworking.send(new Request(pending, action, revision, reference, "", text, title, false, false));
    }
    private void noFailure() { if (notice != null) throw new IllegalStateException("server refused request: " + notice.message()); }
    private void tick(MinecraftClient c) {
        if (done) return;
        try {
            if (System.nanoTime() - started > 200_000_000_000L) throw new IllegalStateException("timeout " + role + " step " + step);
            if (c.player == null && c.getOverlay() == null && c.currentScreen instanceof AccessibilityOnboardingScreen) c.setScreen(new TitleScreen());
            if (!connecting && c.player == null && c.getOverlay() == null && c.currentScreen instanceof TitleScreen) {
                connecting = true;
                ConnectScreen.connect(c.currentScreen, c, ServerAddress.parse("127.0.0.1:25575"),
                        new ServerInfo("Council ballot smoke", "127.0.0.1:25575", ServerInfo.ServerType.OTHER), false, null);
                return;
            }
            if (c.player == null) return;
            if (wait > 0) { wait--; return; }
            if (step == 0) { mark(phase + "-ready-" + role, "ready"); step = 1; return; }
            if (phase.equals("restart")) { restart(c); return; }
            if (role.equals("A")) host(c); else voter(c);
        } catch (Throwable failure) { finish(c, failure.toString()); }
    }
    private void host(MinecraftClient c) throws Exception {
        switch (step) {
            case 1 -> { if (!has("initial-ready-B") || !has("initial-ready-C")) return;
                command(c, "chronicle editing on"); wait = 30; step = 2; }
            case 2 -> { admin(Operation.OPEN, 0, offline("ChronicleFabricB"), ""); step = 3; }
            case 3 -> { noFailure(); if (admin == null) return;
                var houses = admin.houses();
                require(admin.editable() && houses.size() >= 2, "two editable Council houses required");
                houseB = houses.get(0).id(); houseC = houses.get(1).id();
                require(!houseB.equals(houseC), "houses must differ");
                require(admin.officeEditable() && admin.chancellor().equals(NONE), "vacant office unavailable");
                admin(Operation.APPOINT_CHANCELLOR, admin.revision(), offline("ChronicleFabricA"), ""); step = 33; }
            case 33 -> { noFailure(); if (admin == null) return;
                require(admin.chancellor().equals(offline("ChronicleFabricA")), "appointment missing");
                admin(Operation.ASSIGN, admin.revision(), offline("ChronicleFabricB"), houseB); step = 4; }
            case 4 -> { noFailure(); if (admin == null) return;
                require(admin.players().stream().anyMatch(p->p.id().equals(offline("ChronicleFabricC"))&&p.houseId().isBlank()),"late player already assigned"); step=5; }
            case 5 -> { noFailure(); if (admin == null) return;
                command(c, "fill -5 64 -5 5 64 5 minecraft:stone");
                command(c, "setblock 0 65 4 minecraft:lectern");
                command(c, "tp ChronicleFabricB 0 65 0"); command(c, "tp ChronicleFabricC 1 65 0");
                command(c, "tp ChronicleFabricA 10 65 0"); command(c, "op ChronicleFabricB"); command(c, "chronicle editing off");
                wait = 30; mark("initial-join", "ready"); step = 6; }
            case 6 -> { if (!has("initial-joined-B") || !has("initial-joined-C") || !has("initial-denied-B")) return;
                command(c, "chronicle session start"); wait = 30; step = 7; }
            case 7 -> { command(c,"chronicle session pause");command(c,"chronicle editing on");wait=30;step=56; }
            case 56 -> { admin(Operation.OPEN,0,offline("ChronicleFabricC"),"");step=57; }
            case 57 -> { noFailure();if(admin==null)return;require(admin.committed()&&admin.lateAssignable(),"committed cast blocked late assignment");
                for(String file:List.of("roster.json","cast.json","automatic-cast.json"))mark("base-"+file+"-hash",worldHash(file));
                admin(Operation.ASSIGN,admin.revision(),offline("ChronicleFabricC"),houseC);step=58; }
            case 58 -> { noFailure();if(admin==null)return;
                require(admin.players().stream().anyMatch(p->p.id().equals(offline("ChronicleFabricC"))&&p.houseId().equals(houseC)),"late House not published");
                for(String file:List.of("roster.json","cast.json","automatic-cast.json"))require(worldHash(file).equals(Files.readString(shared.resolve("base-"+file+"-hash")).trim()),"baseline rewritten during late assignment");
                require(Files.isRegularFile(root.resolve("world/chronicle/last-council/house-enrollments.json")),"enrollment receipt missing");
                checks.add("late House and new papers publish atomically without rewriting original roster/cast/receipt");
                pending=UUID.randomUUID();catalogue=null;
                ClientPlayNetworking.send(new io.github.chronicle.lastcouncil.network.CatalogueEditorPayloads.Request(pending,io.github.chronicle.lastcouncil.network.CatalogueEditorPayloads.Action.OPEN,io.github.chronicle.lastcouncil.network.CataloguePayloads.Category.SECRETS,"",0,0,0,""));step=62; }
            case 62 -> { noFailure();if(catalogue==null)return;require(!catalogue.rows().isEmpty()&&catalogue.cards().stream().anyMatch(card->card.title().equals("Template")&&!card.body().isBlank()),"reusable source catalogue missing");
                c.getWindow().setWindowedSize(1280,720);c.setScreen(new CouncilCatalogueScreen(catalogue,null));wait=30;step=63; }
            case 63 -> { require(c.currentScreen instanceof CouncilCatalogueScreen,"catalogue screen closed");checkFrame((CouncilScreen)c.currentScreen);
                ScreenshotRecorder.saveScreenshot(shared.toFile(),"source-catalogue-normal.png",c.getFramebuffer(),1,ignored->{});
                c.getWindow().setWindowedSize(640,480);wait=30;step=64; }
            case 64 -> { require(c.currentScreen instanceof CouncilCatalogueScreen,"compact catalogue screen closed");checkFrame((CouncilScreen)c.currentScreen);
                ScreenshotRecorder.saveScreenshot(shared.toFile(),"source-catalogue-compact.png",c.getFramebuffer(),1,ignored->{});c.currentScreen.close();
                checks.add("operator reads reusable catalogue with normal/compact native layouts");command(c,"chronicle events");step=80; }
            case 80 -> {if(!(c.currentScreen instanceof io.github.chronicle.core.client.CoreMenuScreen))return;
                if(buttons(c.currentScreen).stream().noneMatch(b->b.active&&b.getMessage().getString().equals("Event pools")))return;
                var parent=c.currentScreen;press(parent,"Event pools");c.setScreen(new Screen(net.minecraft.text.Text.literal("Unrelated test screen")){});c.setScreen(parent);wait=20;step=93;}
            case 93 -> {require(c.currentScreen instanceof io.github.chronicle.core.client.CoreMenuScreen,"delayed pool opening replaced returned parent");checks.add("pool open reply is cancelled after navigation, including return to same parent");press(c.currentScreen,"Event pools");step=81;}
            case 81 -> {if(!(c.currentScreen instanceof io.github.chronicle.core.client.EventPoolEditorScreen))return;
                press(c.currentScreen,"Create pool");input(c.currentScreen).setText("Native test pool");press(c.currentScreen,"Create");
                press(c.currentScreen,"Add existing Event");input(c.currentScreen).setText("broken");press(c.currentScreen,"Find");
                press(c.currentScreen,"A broken royal seal");press(c.currentScreen,"Add");input(c.currentScreen).setText("2");press(c.currentScreen,"Save pool");wait=30;step=82;}
            case 82 -> {require(c.currentScreen instanceof io.github.chronicle.core.client.EventPoolEditorScreen,"pool editor closed");
                var pools=io.github.chronicle.core.eventpool.EventPoolCatalogStore.decode(Files.readAllBytes(root.resolve("config/chronicle/event-pools.json")));
                require(pools.values().stream().anyMatch(p->p.label().equals("Native test pool")&&p.entries().size()==1&&p.entries().get(0).weight()==2),"native pool Save did not persist entry and weight");
                c.getWindow().setWindowedSize(1280,720);wait=30;step=83;}
            case 83 -> {checkPoolFrame(c.currentScreen);ScreenshotRecorder.saveScreenshot(shared.toFile(),"event-pools-normal.png",c.getFramebuffer(),1,ignored->{});c.getWindow().setWindowedSize(1920,574);wait=30;step=95;}
            case 95 -> {checkPoolFrame(c.currentScreen);ScreenshotRecorder.saveScreenshot(shared.toFile(),"event-pools-wide-short.png",c.getFramebuffer(),1,ignored->{});c.getWindow().setWindowedSize(640,480);wait=30;step=84;}
            case 84 -> {checkPoolFrame(c.currentScreen);ScreenshotRecorder.saveScreenshot(shared.toFile(),"event-pools-compact.png",c.getFramebuffer(),1,ignored->{});mark("pool-compete","ready");step=90;}
            case 90 -> {if(!has("pool-competitor-saved"))return;input(c.currentScreen).setText("4");press(c.currentScreen,"Save pool");wait=30;step=91;}
            case 91 -> {require(buttons(c.currentScreen).stream().anyMatch(b->b.getMessage().getString().equals("Save pool")&&!b.active),"stale save still allows overwrite on retry");
                require(poolWeight()==3,"stale draft overwrote another operator");press(c.currentScreen,"Back");press(c.currentScreen,"Event pools");step=92;}
            case 92 -> {if(!(c.currentScreen instanceof io.github.chronicle.core.client.EventPoolEditorScreen))return;press(c.currentScreen,"Native test pool");
                require(buttons(c.currentScreen).stream().anyMatch(b->b.getMessage().getString().contains("seal (3)")),"reopen did not reload competing edit");
                checks.add("two real operator editors preserve the winning save and require reopen after stale revision");press(c.currentScreen,"Delete");press(c.currentScreen,"Delete");wait=30;step=85;}
            case 85 -> {require(!Files.readString(root.resolve("config/chronicle/event-pools.json")).contains("Native test pool"),"native pool Delete did not persist");
                checks.add("native pool editor creates, picks an existing Event, edits weight, saves, deletes and fits normal/compact frames");press(c.currentScreen,"Back");command(c,"chronicle setup");step=86;}
            case 86 -> {if(!(c.currentScreen instanceof io.github.chronicle.core.client.CoreMenuScreen menu))return;complicationSettings=null;
                pending=ComplicationSettingsClient.open(menu);c.setScreen(new Screen(net.minecraft.text.Text.literal("Unrelated test screen")){});c.setScreen(menu);wait=20;step=94;}
            case 94 -> {require(c.currentScreen instanceof io.github.chronicle.core.client.CoreMenuScreen,"delayed settings opening replaced returned parent");checks.add("Council settings reply is cancelled after navigation, including return to same parent");pending=ComplicationSettingsClient.open((io.github.chronicle.core.client.CoreMenuScreen)c.currentScreen);step=87;}
            case 87 -> {if(!(c.currentScreen instanceof ComplicationSettingsScreen))return;require(complicationSettings!=null&&complicationSettings.editable()&&!complicationSettings.started(),"settings projection unavailable");
                var settings=complicationSettings.config();require(settings.total()==2&&settings.meetings().equals(List.of(1,2,3))&&settings.earliestTicks()==6000&&settings.latestTicks()==30000&&settings.spacingTicks()==6000,"approved defaults missing");
                checkFrame((CouncilScreen)c.currentScreen);input(c.currentScreen).setText("0");press(c.currentScreen,"Save settings");wait=30;step=88;}
            case 88 -> {var settings=com.google.gson.JsonParser.parseString(Files.readString(root.resolve("config/chronicle/last-council-complications.json"))).getAsJsonObject();require(settings.get("total").getAsInt()==0,"native settings Save did not persist");
                ScreenshotRecorder.saveScreenshot(shared.toFile(),"complication-settings-compact.png",c.getFramebuffer(),1,ignored->{});press(c.currentScreen,"Timing");checkFrame((CouncilScreen)c.currentScreen);press(c.currentScreen,"Meetings");checkFrame((CouncilScreen)c.currentScreen);press(c.currentScreen,"Next >");
                require(buttons(c.currentScreen).stream().anyMatch(b->b.getMessage().getString().contains("Meeting 9")),"meeting selector page did not move");press(c.currentScreen,"< Previous");c.getWindow().setWindowedSize(1280,720);wait=30;step=89;}
            case 89 -> {checkFrame((CouncilScreen)c.currentScreen);ScreenshotRecorder.saveScreenshot(shared.toFile(),"complication-settings-normal.png",c.getFramebuffer(),1,ignored->{});c.currentScreen.close();if(c.currentScreen!=null)c.currentScreen.close();c.getWindow().setWindowedSize(640,480);
                checks.add("native Council settings show approved defaults, save disabled budget and page meeting choices without hidden allocation");command(c,"chronicle_pool_trial prepare");step=66;}
            case 66 -> {if(!poolTrial("prepare"))return;command(c,"chronicle editing off");command(c,"chronicle session resume");wait=30;step=59;}
            case 59 -> { CouncilCreativeSearchProbe.begin(c);wait=30;step=60; }
            case 60 -> { CouncilCreativeSearchProbe.finish(c);checks.add("Creative search preserves I/O/R/K and rebound input without opening menus");command(c,"chronicle_pool_trial initial");step=67; }
            case 67 -> {if(!poolTrial("initial"))return;checks.add("native Event pool dispatch pins choice, applies flags/media, and conserves archived completion");request(Action.STATE,0,NONE,"","");step=8;}
            case 8 -> { noFailure(); if (view == null) return; request(Action.CALL, view.revision(), NONE, "", ""); step = 31; }
            case 31 -> { noFailure(); if (view == null) return; request(Action.CALL, view.revision(), NONE, "", ""); step = 32; }
            case 32 -> { noFailure(); if (view == null) return; request(Action.START, view.revision(), NONE, "", ""); step = 9; }
            case 9 -> { noFailure(); if (view == null) return; motion = UUID.randomUUID();
                request(Action.PREPARE, view.revision(), motion, "Draft", "Council test"); step = 10; }
            case 10 -> { noFailure(); if (view == null) return;
                require(view.motions().stream().anyMatch(row -> row.id().equals(motion)), "prepared motion missing");
                mark("motion-prepared", "ready"); step = 34; }
            case 34 -> { if (!has("lectern-personal-checked-B")) return;
                command(c, "tp ChronicleFabricA 0 65 1"); wait = 30; step = 36; }
            case 36 -> { c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND,
                        new BlockHitResult(new Vec3d(0.5, 65.5, 4.0), Direction.NORTH, new BlockPos(0, 65, 4), false)); step = 37; }
            case 37 -> { if (!(c.currentScreen instanceof CouncilBusinessScreen screen)) return;
                checkMotionLectern(screen);require(buttons(screen).stream().noneMatch(b->Set.of("Propose...","Sponsor","Withdraw...").contains(b.getMessage().getString())),"Chancellor received house actions on lectern"); ScreenshotRecorder.saveScreenshot(shared.toFile(), "lectern-chancellor.png", c.getFramebuffer(), 1, ignored -> {});
                c.currentScreen.close(); command(c, "tp ChronicleFabricA 10 65 0"); wait = 30; step = 38; }
            case 38 -> { request(Action.OPEN_BALLOT, view.revision(), motion, "", ""); step = 11; }
            case 11 -> { noFailure(); if (view == null) return; ballot = view.activeBallot();
                require(ballot != null && !ballot.equals(NONE), "ballot missing");
                mark("ballot-id", ballot.toString()); mark("motion-id", motion.toString());
                c.setScreen(new CouncilBusinessScreen(view,null));mark("initial-open", "ready"); step = 12; }
            case 12 -> { if (!has("initial-cast-B") || !has("initial-cast-C")) return;
                require(c.currentScreen instanceof CouncilBusinessScreen,"Chancellor progress workspace closed");
                var live=((CouncilBusinessScreen)c.currentScreen).snapshot().progress();
                if(live==null||live.submitted()!=2)return;
                require(live.electorate()==2&&live.outstanding().isEmpty(),"all-submitted public progress wrong");
                checks.add("Chancellor public completion arrives automatically without Refresh");
                request(Action.BALLOT, 0, ballot, "", ""); step = 13; }
            case 13 -> { noFailure(); if (vote == null) return;
                require(vote.ballot().threshold() == 2 && vote.ballot().electorateLabels().size() == 2, "fixed electorate or threshold wrong");
                require(vote.ballot().namedVotes().size() == 2, "public named votes missing");
                require(c.currentScreen instanceof BallotScreen&&buttons(c.currentScreen).stream().noneMatch(bu->bu.getMessage().getString().equals("Refresh")),"manual Refresh still required");
                require(vote.voters().size() == 2 && vote.voters().stream().anyMatch(v -> v.player().equals(offline("ChronicleFabricB")) && v.name().equals("Envoy Beatrice of Ash and the Northern Road"))
                        && vote.voters().stream().anyMatch(v -> v.player().equals(offline("ChronicleFabricC")) && v.name().equals("ChronicleFabricC")), "captured public card names missing");
                wait = 260; step = 35; }
            case 35 -> { require(c.currentScreen instanceof BallotScreen, "public ballot screen missing");
                ScreenshotRecorder.saveScreenshot(shared.toFile(), "public-ballot-voted.png", c.getFramebuffer(), 1, ignored -> {});
                c.currentScreen.close(); request(Action.STATE, 0, NONE, "", ""); step = 14; }
            case 14 -> { noFailure(); if (view == null) return; request(Action.CLOSE_BALLOT, view.revision(), ballot, "", ""); step = 15; }
            case 15 -> { noFailure(); if (view == null) return; request(Action.BALLOT, 0, ballot, "", ""); step = 16; }
            case 16 -> { noFailure(); if (vote == null) return;
                var b = vote.ballot();
                require(b.status() == BallotStatus.CLOSED && b.totals().getOrDefault("yes", 0) == 1
                        && b.totals().getOrDefault("abstain", 0) == 1 && b.passedOptions().isEmpty(), "closed result wrong");
                checks.add("two distinct house representatives cast a public ballot; fixed threshold and closed aggregate verified");
                mark("initial-closed", "ready"); request(Action.STATE,0,motion,"","");step=61; }
            case 61 -> { noFailure();if(view==null)return;require(view.detail()!=null&&view.detail().attempts().size()==1,"public motion history missing");
                var old=view.detail().attempts().getFirst();require(old.ballot().namedVotes().size()==2&&old.voters().stream().anyMatch(v->v.name().equals("Envoy Beatrice of Ash and the Northern Road")),"history lost frozen public names/choices");
                checks.add("selected public motion exposes its actual immutable named attempt history");c.getWindow().setWindowedSize(1280,720);c.setScreen(new CouncilBusinessScreen(view,null));wait=30;step=65; }
            case 65 -> { require(c.currentScreen instanceof CouncilBusinessScreen,"history detail closed");checkFrame((CouncilScreen)c.currentScreen);
                ScreenshotRecorder.saveScreenshot(shared.toFile(),"public-motion-history.png",c.getFramebuffer(),1,ignored->{});c.currentScreen.close();request(Action.STATE,0,NONE,"","");step=17; }
            case 17 -> { noFailure(); if (view == null || !has("inventory-ready-B")) return; secretMotion = UUID.randomUUID();
                requestSecret(Action.PREPARE, view.revision(), secretMotion, "Secret draft", "Secret Council test"); step = 18; }
            case 18 -> { noFailure(); if (view == null) return;
                request(Action.OPEN_BALLOT, view.revision(), secretMotion, "", ""); step = 19; }
            case 19 -> { noFailure(); if (view == null) return; secretBallot = view.activeBallot();
                require(secretBallot != null && !secretBallot.equals(ballot), "secret ballot identity missing");
                mark("secret-motion-id", secretMotion.toString()); mark("void-id", secretBallot.toString());
                mark("secret-open", "ready"); step = 20; }
            case 20 -> { if (!has("secret-cast-B") || !has("secret-view-C")) return;
                request(Action.BALLOT, 0, secretBallot, "", ""); step = 21; }
            case 21 -> { noFailure(); if (vote == null) return; secretProjection(vote);
                require(vote.ballot().threshold() == 2 && vote.ballot().electorateLabels().size() == 2, "secret electorate wrong");
                command(c, "tp ChronicleFabricC 10 65 0"); wait = 20; mark("secret-outside", "ready"); step = 22; }
            case 22 -> { if (!has("secret-outside-denied-C")) return;
                command(c, "tp ChronicleFabricC 1 65 0"); command(c, "chronicle session pause");
                wait = 30; mark("secret-paused", "ready"); step = 23; }
            case 23 -> { if (!has("secret-pause-denied-C") || !has("secret-immutable-B")) return;
                command(c, "chronicle session resume"); wait = 30; mark("secret-resumed", "ready"); step = 24; }
            case 24 -> { if (!has("secret-cast-C")) return; request(Action.STATE, 0, NONE, "", ""); step = 25; }
            case 25 -> { noFailure(); if (view == null) return;
                request(Action.VOID_BALLOT, view.revision(), secretBallot, "Wrong electorate", ""); step = 26; }
            case 26 -> { noFailure(); if (view == null) return;
                request(Action.BALLOT, 0, secretBallot, "", ""); step = 27; }
            case 27 -> { noFailure(); if (vote == null) return; secretProjection(vote);
                require(vote.ballot().status() == BallotStatus.VOID && vote.ballot().reason().contains("Wrong electorate"), "void reason missing");
                command(c, "tp ChronicleFabricC 10 65 0"); wait = 30; request(Action.STATE, 0, NONE, "", ""); step = 28; }
            case 28 -> { noFailure(); if (view == null) return;
                request(Action.OPEN_BALLOT, view.revision(), secretMotion, "", ""); step = 29; }
            case 29 -> { noFailure(); if (view == null) return; replacement = view.activeBallot();
                require(replacement != null && !replacement.equals(secretBallot), "replacement must have new identity");
                mark("replacement-id", replacement.toString());
                request(Action.BALLOT, 0, replacement, "", ""); step = 30; }
            case 30 -> { noFailure(); if (vote == null) return; secretProjection(vote);
                require(vote.ballot().threshold() == 1 && vote.ballot().electorateLabels().size() == 1, "replacement electorate wrong");
                checks.add("secret projections hide votes; void reason and replacement electorate verified");
                mark("initial-complete", "ready"); finish(c, null); }
            default -> throw new IllegalStateException("invalid host step");
        }
    }
    private void voter(MinecraftClient c) throws Exception {
        switch (step) {
            case 1 -> { if (!has("initial-join")) return;
                command(c, "chronicle players join"); wait = 30; step = 2; }
            case 2 -> { mark("initial-joined-" + role, "ready");
                if (role.equals("B")) { request(Action.PREPARE, 0, UUID.randomUUID(), "Draft", "Unauthorized"); step = 3; }
                else {pending=UUID.randomUUID();catalogue=null;ClientPlayNetworking.send(new io.github.chronicle.lastcouncil.network.CatalogueEditorPayloads.Request(pending,io.github.chronicle.lastcouncil.network.CatalogueEditorPayloads.Action.OPEN,io.github.chronicle.lastcouncil.network.CataloguePayloads.Category.SECRETS,"",0,0,0,""));wait=30;step=62;} }
            case 90 -> {if(!(c.currentScreen instanceof io.github.chronicle.core.client.CoreMenuScreen))return;if(buttons(c.currentScreen).stream().noneMatch(b->b.getMessage().getString().equals("Event pools")))return;press(c.currentScreen,"Event pools");step=91;}
            case 91 -> {if(!(c.currentScreen instanceof io.github.chronicle.core.client.EventPoolEditorScreen))return;press(c.currentScreen,"Native test pool");press(c.currentScreen,"A broken royal seal (2)");input(c.currentScreen).setText("3");press(c.currentScreen,"Save pool");wait=30;step=92;}
            case 92 -> {require(poolWeight()==3,"competing operator Save failed");c.currentScreen.close();if(c.currentScreen!=null)c.currentScreen.close();command(c,"chronicle editing off");competed=true;mark("pool-competitor-saved","ready");step=42;}
            case 62 -> { require(catalogue==null,"ordinary House player received source catalogue");checks.add("ordinary participant cannot fetch reusable secret catalogue");pending=UUID.randomUUID();complicationSettings=null;ClientPlayNetworking.send(new io.github.chronicle.lastcouncil.network.ComplicationPayloads.Request(pending,io.github.chronicle.lastcouncil.network.ComplicationPayloads.Action.OPEN,0,null,List.of(),""));wait=30;step=63; }
            case 63 -> {require(complicationSettings==null,"ordinary player received Council configuration");checks.add("ordinary participant cannot query Council complication setup");step=4;}
            case 3 -> { if (notice == null) return; require(notice.message().contains("Chancellor"), "operator house member prepared motion");
                request(Action.STATE, 0, NONE, "", ""); step = 51; }
            case 51 -> { if (notice == null) return; require(notice.message().contains("Chancellor"), "operator house member accessed proceedings");
                checks.add("operator house member cannot prepare or access proceedings"); mark("initial-denied-B", "ready"); step = 42; }
            case 42 -> { if(role.equals("B")&&!competed&&has("pool-compete")){command(c,"chronicle editing on");command(c,"chronicle events");step=90;return;} if (!has("motion-prepared")) return;
                c.getWindow().setWindowedSize(1280, 720);
                c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND,
                        new BlockHitResult(new Vec3d(0.5, 65.5, 4.0), Direction.NORTH, new BlockPos(0, 65, 4), false));
                step = 43; }
            case 43 -> { if (!(c.currentScreen instanceof CouncilBusinessScreen screen)) return;
                checkMotionLectern(screen); pressPrefix(screen, "Council test"); wait = 260; step = 44; }
            case 44 -> { require(c.currentScreen instanceof CouncilBusinessScreen, "lectern closed after selecting motion");
                checkMotionLectern((CouncilBusinessScreen)c.currentScreen);
                ScreenshotRecorder.saveScreenshot(shared.toFile(), "lectern-motion-normal.png", c.getFramebuffer(), 1, ignored -> {});
                c.getWindow().setWindowedSize(640, 480); wait = 30; step = 45; }
            case 45 -> { require(c.currentScreen instanceof CouncilBusinessScreen, "compact lectern closed");
                checkMotionLectern((CouncilBusinessScreen)c.currentScreen);
                ScreenshotRecorder.saveScreenshot(shared.toFile(), "lectern-motion-compact.png", c.getFramebuffer(), 1, ignored -> {});
                c.currentScreen.close(); c.getWindow().setWindowedSize(1280, 720);
                command(c, "chronicle council"); step = 46; }
            case 46 -> { if (!(c.currentScreen instanceof PersonalScreen screen)) return;
                press(screen, "Other houses"); wait = 30; step = 47; }
            case 47 -> { if (!(c.currentScreen instanceof PersonalScreen screen)) return;
                if (personal == null) { // The screen makes its own request; obtain the same public directory for assertions.
                    pending = UUID.randomUUID(); notice = null;
                    ClientPlayNetworking.send(new CouncilPayloads.PersonalRequest(pending, CouncilPayloads.Section.INFORMATION, 0)); step = 48;
                } else step = 48; }
            case 48 -> { noFailure(); if (personal == null) return;
                require(c.currentScreen instanceof PersonalScreen, "personal screen closed");
                checkOtherHouses((PersonalScreen)c.currentScreen, personal); wait = 260; step = 49; }
            case 49 -> { require(c.currentScreen instanceof PersonalScreen, "personal screen closed");
                checkOtherHouses((PersonalScreen)c.currentScreen, personal);
                ScreenshotRecorder.saveScreenshot(shared.toFile(), "other-houses-normal.png", c.getFramebuffer(), 1, ignored -> {});
                c.getWindow().setWindowedSize(640, 480); wait = 30; step = 50; }
            case 50 -> { require(c.currentScreen instanceof PersonalScreen, "compact personal screen closed");
                checkOtherHouses((PersonalScreen)c.currentScreen, personal);
                ScreenshotRecorder.saveScreenshot(shared.toFile(), "other-houses-compact.png", c.getFramebuffer(), 1, ignored -> {});
                c.getWindow().setWindowedSize(1280, 720); press(c.currentScreen, "Character"); step = 52; }
            case 52 -> { if (!(c.currentScreen instanceof PersonalScreen screen)) return;
                press(screen, "Edit character card\u2026"); step = 53; }
            case 53 -> { if (!(c.currentScreen instanceof DossierScreen editor)) return;
                var fields = editor.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).toList();
                require(fields.size() == 3, "character card fields missing"); fields.get(0).setText("Envoy Beatrice of Ash and the Northern Road"); press(editor, "Save"); step = 54; }
            case 54 -> { if (!(c.currentScreen instanceof DossierScreen editor) || !editor.snapshot().saved()
                        || !editor.snapshot().name().equals("Envoy Beatrice of Ash and the Northern Road")) return;
                press(editor, "Done"); step = 55; }
            case 55 -> { if (!(c.currentScreen instanceof PersonalScreen)) return;
                c.currentScreen.close(); checks.add("lectern and public house directory show visible lists; public character name saved");
                mark("lectern-personal-checked-B", "ready"); step = 4; }
            case 4 -> { if (!has("initial-open")) return;
                pending = UUID.randomUUID(); personal = null; notice = null;
                ClientPlayNetworking.send(new CouncilPayloads.PersonalRequest(pending, CouncilPayloads.Section.SECRETS, 0)); step = 24; }
            case 24 -> { noFailure(); if (personal == null) return;
                require(!personal.ownHouse().isBlank() && !personal.paragraphs().isEmpty(), "automatic private papers missing");
                checks.add("automatic house papers available after session start"); ballot = readId("ballot-id");
                request(Action.BALLOT, 0, NONE, "", ""); step = 5; }
            case 5 -> { noFailure(); if (vote == null) return;
                require(vote.ballot().id().equals(ballot) && vote.canCast(), "eligible voter cannot cast");
                request(Action.CAST, 0, ballot, role.equals("B") ? "yes" : "abstain", ""); step = 6; }
            case 6 -> { noFailure(); if (vote == null) return;
                require(vote.ballot().ownChoice().equals(role.equals("B") ? "yes" : "abstain"), "own confirmed choice missing");
                checks.add("representative cast and received their confirmed choice"); mark("initial-cast-" + role, "ready"); step = 7; }
            case 7 -> { if (role.equals("B") && !has("inventory-ready-B")) {
                    if(c.currentScreen!=null)c.currentScreen.close();
                    c.setScreen(new InventoryScreen(c.player)); mark("inventory-ready-B", "ready");
                }
                if (!has("secret-open")) return; secretBallot = readId("void-id");
                if (role.equals("B")) {
                    if (!secretBallot.equals(BallotAccess.currentHint())) return;
                    require(c.currentScreen instanceof InventoryScreen||c.currentScreen instanceof CreativeInventoryScreen, "ballot signal replaced inventory: "+(c.currentScreen==null?"null":c.currentScreen.getClass().getSimpleName()));
                    BallotAccess.binding().setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_G));
                    KeyBinding.updateKeysByCode();
                    require(!ScreenKeyboardEvents.allowKeyPress(c.currentScreen).invoker()
                            .allowKeyPress(c.currentScreen, new KeyInput(GLFW.GLFW_KEY_G, 0, 0)), "rebound ballot key was not consumed");
                    step = 21;
                } else { request(Action.BALLOT, 0, secretBallot, "", ""); step = 8; } }
            case 21 -> { if (!(c.currentScreen instanceof BallotScreen screen)) return;
                require(screen.snapshot().ballot().id().equals(secretBallot), "hotkey opened wrong ballot");
                secretProjection(screen.snapshot());
                Vote snapshot=screen.snapshot();UUID delayed=UUID.randomUUID();
                BallotAccess.expectOpening(delayed,null,secretBallot);
                Vote late=new Vote(delayed,true,snapshot.ballot(),snapshot.description(),snapshot.canCast(),snapshot.reason(),snapshot.voters());
                require(!BallotAccess.openResponse(late,c)&&c.currentScreen==screen,"delayed opening replaced a newer screen");
                checks.add("delayed ballot response preserves a newer screen");
                checks.add("ballot signal preserved inventory and rebound key opened the active ballot");
                wait = 12; step = 40; }
            case 40 -> {
                MotionDraftScreen draft=new MotionDraftScreen(null,(title,body,secret,thirds)->{throw new AssertionError("unexpected draft submit");});
                c.setScreen(draft);
                TextFieldWidget field=(TextFieldWidget)draft.children().stream().filter(TextFieldWidget.class::isInstance).findFirst().orElseThrow();
                field.setText("Unsent draft");draft.setFocused(field);
                require(ScreenKeyboardEvents.allowKeyPress(draft).invoker().allowKeyPress(draft,new KeyInput(GLFW.GLFW_KEY_G,0,0))&&c.currentScreen==draft,"ballot key interrupted text entry");
                draft.setFocused(null);
                require(!ScreenKeyboardEvents.allowKeyPress(draft).invoker().allowKeyPress(draft,new KeyInput(GLFW.GLFW_KEY_G,0,0)),"ballot key failed on nested editor");
                step=41;
            }
            case 41 -> {
                if(!(c.currentScreen instanceof BallotScreen screen))return;
                require(screen.parent instanceof MotionDraftScreen,"ballot lost suspended editor");
                Vote snapshot=screen.snapshot();screen.close();
                require(c.currentScreen instanceof MotionDraftScreen,"ballot did not return to editor");
                TextFieldWidget field=(TextFieldWidget)c.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).findFirst().orElseThrow();
                require(field.getText().equals("Unsent draft"),"ballot discarded unsent editor text");
                checks.add("hotkey respects text entry and preserves a nested unsent editor");
                c.setScreen(new BallotScreen(snapshot,null));wait=20;step=20;
            }
            case 8 -> { noFailure(); if (vote == null) return; secretProjection(vote);
                if (role.equals("B")) {
                    require(c.currentScreen instanceof BallotScreen, "secret ballot screen missing");
                    wait = 20; step = 20; }
                else step = 11; }
            case 20 -> { require(c.currentScreen instanceof BallotScreen, "secret ballot screen closed");
                ScreenshotRecorder.saveScreenshot(shared.toFile(), "secret-ballot-before-vote.png", c.getFramebuffer(), 1, ignored -> {});
                press(c.currentScreen, "Yes"); step = 22; }
            case 22 -> { if (!(c.currentScreen instanceof ConfirmScreen)) return;
                Screen confirmation=c.currentScreen;
                require(ScreenKeyboardEvents.allowKeyPress(confirmation).invoker().allowKeyPress(confirmation,new KeyInput(GLFW.GLFW_KEY_G,0,0))&&c.currentScreen==confirmation,"ballot key bypassed a confirmation");
                checks.add("ballot hotkey does not bypass an open confirmation");
                press(c.currentScreen, "Yes"); step = 23; }
            case 23 -> { if (!(c.currentScreen instanceof BallotScreen screen) || !screen.snapshot().ballot().ownChoice().equals("yes")) return;
                secretProjection(screen.snapshot()); checks.add("native ballot confirmation recorded the chosen vote");
                mark("secret-cast-B", "ready"); request(Action.CAST, 0, secretBallot, "no", ""); step = 10; }
            case 9 -> { noFailure(); if (vote == null) return; secretProjection(vote);
                require(vote.ballot().ownChoice().equals("yes"), "secret confirmed choice missing");
                mark("secret-cast-B", "ready"); request(Action.CAST, 0, secretBallot, "no", ""); step = 10; }
            case 10 -> { if (notice == null) return;
                require(notice.message().contains("final"), "changed confirmed choice accepted");
                checks.add("confirmed secret vote cannot be changed"); mark("secret-immutable-B", "ready"); step = 17; }
            case 11 -> { if (!has("secret-cast-B")) return;
                request(Action.BALLOT, 0, secretBallot, "", ""); step = 19; }
            case 19 -> { noFailure(); if (vote == null) return; secretProjection(vote);
                mark("secret-view-C", "ready"); step = 12; }
            case 12 -> { if (!has("secret-outside")) return;
                request(Action.CAST, 0, secretBallot, "no", ""); step = 13; }
            case 13 -> { if (notice == null) return;
                require(notice.message().contains("Region"), "outside Region vote accepted");
                mark("secret-outside-denied-C", "ready"); step = 14; }
            case 14 -> { if (!has("secret-paused")) return;
                request(Action.CAST, 0, secretBallot, "no", ""); step = 15; }
            case 15 -> { if (notice == null) return;
                require(notice.message().contains("active play"), "paused vote accepted");
                mark("secret-pause-denied-C", "ready"); step = 16; }
            case 16 -> { if (!has("secret-resumed")) return;
                request(Action.CAST, 0, secretBallot, "no", ""); step = 18; }
            case 18 -> { noFailure(); if (vote == null) return; secretProjection(vote);
                require(vote.ballot().ownChoice().equals("no"), "secret vote missing");
                checks.add("outside Region and paused casts refused; returned representative cast after resume");
                mark("secret-cast-C", "ready"); step = 17; }
            case 17 -> { if (!has("initial-complete")) return;
                require(callMessages == 2, "expected exactly two Council call messages, got " + callMessages);
                checks.add("two distinct Council calls delivered one message each"); finish(c, null); }
            default -> throw new IllegalStateException("invalid voter step");
        }
    }
    private void restart(MinecraftClient c) throws Exception {
        switch (step) {
            case 1 -> { if (role.equals("A") && (!has("restart-ready-B") || !has("restart-ready-C"))) return;
                ballot = readId("ballot-id"); motion = readId("motion-id");
                secretBallot = readId("void-id"); replacement = readId("replacement-id");
                if (role.equals("A")) {command(c,"chronicle_pool_trial restart");step=68;}
                else {request(Action.BALLOT, 0, secretBallot, "", "");step=2;} }
            case 68 -> {if(!poolTrial("restart"))return;checks.add("pool draw survives actual restart and original completion never replays");request(Action.STATE,0,motion,"","");step=2;}
            case 2 -> { noFailure();
                if (role.equals("A")) { if (view == null) return;
                    require(replacement.equals(view.activeBallot()) && view.motions().stream().anyMatch(row -> row.id().equals(motion)), "open replacement or public motion missing after restart");
                    require(view.history().stream().anyMatch(row -> row.id().equals(ballot)), "public ballot history missing after restart");
                    command(c, "tp ChronicleFabricB 0 65 0"); command(c, "tp ChronicleFabricC 1 65 0");
                    wait = 30; mark("restart-inside", "ready"); step = 3; }
                else { if (vote == null) return; secretProjection(vote);
                    require(vote.ballot().status() == BallotStatus.VOID && vote.ballot().ownChoice().equals(role.equals("B") ? "yes" : "no"), "void own choice missing after restart");
                    step = 4; } }
            case 3 -> { if (!has("restart-cast-B") || !has("restart-denied-C")) return;
                request(Action.STATE, 0, NONE, "", ""); step = 5; }
            case 4 -> { if (!has("restart-inside")) return;
                request(Action.CAST, 0, replacement, "yes", ""); step = 6; }
            case 6 -> { if (role.equals("B")) { noFailure(); if (vote == null) return;
                    require(vote.ballot().ownChoice().equals("yes"), "replacement vote missing");
                    mark("restart-cast-B", "ready"); }
                else { if (notice == null) return;
                    require(notice.message().contains("not present"), "late arrival joined fixed electorate");
                    mark("restart-denied-C", "ready"); }
                step = 9; }
            case 5 -> { noFailure(); if (view == null) return;
                request(Action.CLOSE_BALLOT, view.revision(), replacement, "", ""); step = 7; }
            case 7 -> { noFailure(); if (view == null) return;
                request(Action.BALLOT, 0, replacement, "", ""); step = 8; }
            case 8 -> { noFailure(); if (vote == null) return;
                require(vote.ballot().status() == BallotStatus.CLOSED && vote.ballot().threshold() == 1
                        && vote.ballot().totals().getOrDefault("yes", 0) == 1
                        && vote.ballot().passedOptions().contains("yes"), "replacement result wrong");
                checks.add("replacement remained open across restart with fixed one-person electorate and passed");
                c.getWindow().setWindowedSize(1280, 720);
                if (c.currentScreen != null) c.currentScreen.close();
                c.setScreen(new InventoryScreen(c.player));
                ProceedingsAccess.binding().setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_H));
                KeyBinding.updateKeysByCode();
                require(!ScreenKeyboardEvents.allowKeyPress(c.currentScreen).invoker().allowKeyPress(c.currentScreen,
                        new KeyInput(GLFW.GLFW_KEY_H, 0, 0)), "rebound proceedings key was not consumed");
                wait = 260; step = 10; }
            case 10 -> { if (!(c.currentScreen instanceof CouncilBusinessScreen screen)) return;
                checkProceedingsLayout(screen);
                ScreenshotRecorder.saveScreenshot(shared.toFile(), "proceedings-normal.png", c.getFramebuffer(), 1, ignored -> {});
                c.getWindow().setWindowedSize(640, 480); wait = 30; step = 11; }
            case 11 -> { require(c.currentScreen instanceof CouncilBusinessScreen, "proceedings screen closed");
                checkProceedingsLayout((CouncilBusinessScreen)c.currentScreen);
                ScreenshotRecorder.saveScreenshot(shared.toFile(), "proceedings-compact.png", c.getFramebuffer(), 1, ignored -> {});
                checks.add("rebound proceedings key opened away from lectern; normal and compact frames captured");
                for(String file:List.of("roster.json","cast.json","automatic-cast.json"))require(worldHash(file).equals(Files.readString(shared.resolve("base-"+file+"-hash")).trim()),"baseline changed after restart");
                checks.add("late enrollment remains valid through voting and actual restart; original private files unchanged");
                mark("restart-complete", "ready"); finish(c, null); }
            case 9 -> { if (!has("restart-complete")) return;
                require(callMessages == 0, "Council call replayed on restart");
                checks.add("void choice persisted and replacement electorate remained fixed across restart"); finish(c, null); }
            default -> throw new IllegalStateException("invalid restart step");
        }
    }
    private void requestSecret(Action action, long revision, UUID reference, String text, String title) {
        pending = UUID.randomUUID(); view = null; vote = null; notice = null;
        if(action==Action.BALLOT)BallotAccess.expectOpening(pending,MinecraftClient.getInstance().currentScreen,reference);
        ClientPlayNetworking.send(new Request(pending, action, revision, reference, "", text, title, true, false));
    }
    private static final Map<UUID,List<Voter>> frozenSecretRosters=new HashMap<>();
    private static void secretProjection(Vote value) {
        var prior=frozenSecretRosters.putIfAbsent(value.ballot().id(),value.voters());require(prior==null||prior.equals(value.voters()),"secret voter roster disclosed activity or changed");
        require(value.ballot().namedVotes().isEmpty() && value.ballot().totals().isEmpty(), "secret projection exposed aggregate or named votes");
    }
    private int poolWeight()throws Exception {return io.github.chronicle.core.eventpool.EventPoolCatalogStore.decode(Files.readAllBytes(root.resolve("config/chronicle/event-pools.json"))).values().stream().filter(p->p.label().equals("Native test pool")).findFirst().orElseThrow().entries().get(0).weight();}
    private boolean poolTrial(String expected)throws Exception {
        Path path=shared.resolve("pool-trial-"+phase+".json");if(!Files.isRegularFile(path))return false;
        var data=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        require(data.get("result").getAsString().equals("CHRONICLE_POOL_TRIAL_OK"),"native Event pool trial failed");
        return data.get("phase").getAsString().equals(expected);
    }
    private void finish(MinecraftClient c, String error) {
        if (done) return;
        if(error==null) {
            int expected=phase.equals("initial")&&!role.equals("A")?1:0;
            if(poolMessages!=expected)error="Pool media was missing, leaked outside audience or replayed: "+poolMessages;
            else checks.add("actual pool chat reaches only saved recipients once and is not replayed after restart");
        }
        done = true;
        try { var json = new JsonObject(); json.addProperty("result", error == null ? "PASS" : "FAIL");
            json.addProperty("detail", error == null ? "Completed" : error); json.add("checks", new Gson().toJsonTree(checks));
            Files.writeString(output, new Gson().toJson(json), StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
        c.scheduleStop();
    }
    private static List<ButtonWidget> buttons(Screen screen) {
        return screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).toList();
    }
    private static void checkFrame(CouncilScreen screen) {
        for (var b : buttons(screen)) require(b.getX() >= screen.left && b.getY() >= screen.top
                && b.getX() + b.getWidth() <= screen.left + screen.panelWidth
                && b.getY() + b.getHeight() <= screen.top + screen.panelHeight, "control outside frame");
    }
    private static void checkMotionLectern(CouncilBusinessScreen screen) {
        var labels = buttons(screen).stream().map(b -> b.getMessage().getString()).toList();
        require(labels.stream().anyMatch(s -> (s.startsWith("> ")?s.substring(2):s).startsWith("Council test")), "visible motion row missing");
        require(labels.stream().noneMatch(s -> s.startsWith("Choose motion") || s.startsWith("Prepare")
                || s.contains("Proceedings") || s.startsWith("Admit") || s.startsWith("Open vote")), "host control on lectern");
        checkFrame(screen);
    }
    private static void checkOtherHouses(PersonalScreen screen, CouncilPayloads.PersonalView view) {
        var labels = buttons(screen).stream().map(b -> b.getMessage().getString()).toList();
        require(view.houses().stream().filter(h -> !h.id().equals(view.ownHouse()))
                .anyMatch(h -> labels.stream().anyMatch(label -> h.title().startsWith(label) && !label.isBlank())), "visible other-house row missing");
        require(labels.stream().noneMatch(s -> s.startsWith("Choose other")), "house picker remains");
        require(screen.children().stream().noneMatch(TextFieldWidget.class::isInstance), "house details are editable");
        checkFrame(screen);
    }
    private static void pressPrefix(Screen screen, String prefix) {
        var button = buttons(screen).stream().filter(b -> b.active && (b.getMessage().getString().startsWith("> ")?b.getMessage().getString().substring(2):b.getMessage().getString()).startsWith(prefix)).findFirst()
                .orElseThrow(() -> new IllegalStateException("row unavailable: " + prefix));
        button.onPress(new MouseInput(0, 0));
    }
    private static void checkProceedingsLayout(CouncilBusinessScreen screen) {
        var buttons = screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).toList();
        if(screen.panelWidth<380) {
            // Compact navigation is paged in-place, rather than seven overlapping rows.
            for(int i=0;i<3;i++) {
                var next=buttons(screen).stream().filter(b->b.active&&b.getMessage().getString().equals(">"))
                    .min(Comparator.comparingInt(ButtonWidget::getY)).orElseThrow(()->new IllegalStateException("compact navigation unavailable"));
                next.onPress(new MouseInput(0,0));
            }
            require(buttons(screen).stream().anyMatch(b->b.getMessage().getString().equals("Motions")),"compact Motions navigation unreachable");
            buttons=buttons(screen);
        } else require(buttons.stream().anyMatch(b -> b.getMessage().getString().equals("Motions")), "proceedings navigation missing");
        require(buttons.stream().noneMatch(b -> b.getMessage().getString().equals("Choose motion")), "motion picker remains");
        for (var b : buttons) require(b.getX() >= screen.left && b.getY() >= screen.top
                && b.getX() + b.getWidth() <= screen.left + screen.panelWidth
                && b.getY() + b.getHeight() <= screen.top + screen.panelHeight, "control outside proceedings frame");
    }
    private static void press(Screen screen, String label) {
        var button = screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .filter(value -> value.active && value.getMessage().getString().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalStateException("button unavailable: " + label));
        button.onPress(new MouseInput(0, 0));
    }
    private static net.minecraft.client.gui.widget.TextFieldWidget input(Screen screen){return screen.children().stream().filter(net.minecraft.client.gui.widget.TextFieldWidget.class::isInstance).map(net.minecraft.client.gui.widget.TextFieldWidget.class::cast).findFirst().orElseThrow(()->new IllegalStateException("input field unavailable"));}
    private static void checkPoolFrame(Screen screen){
        require(screen instanceof io.github.chronicle.core.client.EventPoolEditorScreen,"pool editor expected");
        var actual=((io.github.chronicle.core.client.EventPoolEditorScreen)screen).menuLayoutForTest();
        var expected=io.github.chronicle.core.menu.CoreMenuLayout.forViewport(screen.width,screen.height);
        require(actual.frame().equals(expected.frame()),"pool frame differs from normal O menu");
        for(var child:screen.children())if(child instanceof net.minecraft.client.gui.widget.ClickableWidget w)
            require(actual.content().contains(w.getX(),w.getY(),w.getWidth(),w.getHeight()),"pool control clips outside grey frame content");
        require(buttons(screen).stream().filter(b->Set.of("Back","Save pool","Delete").contains(b.getMessage().getString())).allMatch(b->b.getY()==expected.footerY()),"pool bottom actions do not use shared inset footer");
    }
    private static void checkWidgets(Screen screen){for(var child:screen.children())if(child instanceof net.minecraft.client.gui.widget.ClickableWidget w)require(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=screen.width&&w.getY()+w.getHeight()<=screen.height,"widget outside screen");}
    private String worldHash(String name)throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve("world/chronicle/last-council/"+name))));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
