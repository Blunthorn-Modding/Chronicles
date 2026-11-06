package io.github.chronicle.lastcouncil.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ballots.BallotStatus;
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

/** Opt-in native clients exercise only the normal authenticated network API. */
public final class CouncilBallotsSmoke {
    private static CouncilBallotsSmoke instance;
    private final Path root, output, shared;
    private final String role, phase;
    private final List<String> checks = new ArrayList<>();
    private final long started = System.nanoTime();
    private boolean connecting, done;
    private int step, wait, callMessages;
    private UUID pending, motion, ballot, secretMotion, secretBallot, replacement;
    private AdminView admin;
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
        });
        ClientTickEvents.END_CLIENT_TICK.register(instance::tick);
    }
    public static void admin(AdminView value) { if (instance != null && value.request().equals(instance.pending)) instance.admin = value; }
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
        pending = UUID.randomUUID(); admin = null; notice = null;
        ClientPlayNetworking.send(new AdminRequest(pending, action, revision, target, house, false, NONE));
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
                admin(Operation.ASSIGN, admin.revision(), offline("ChronicleFabricB"), houseB); step = 4; }
            case 4 -> { noFailure(); if (admin == null) return;
                admin(Operation.ASSIGN, admin.revision(), offline("ChronicleFabricC"), houseC); step = 5; }
            case 5 -> { noFailure(); if (admin == null) return;
                command(c, "fill -5 64 -5 5 64 5 minecraft:stone");
                command(c, "setblock 0 65 4 minecraft:lectern");
                command(c, "tp ChronicleFabricB 0 65 0"); command(c, "tp ChronicleFabricC 1 65 0");
                command(c, "tp ChronicleFabricA 10 65 0"); command(c, "chronicle editing off");
                wait = 30; mark("initial-join", "ready"); step = 6; }
            case 6 -> { if (!has("initial-joined-B") || !has("initial-joined-C") || !has("initial-denied-B")) return;
                command(c, "chronicle session start"); wait = 30; step = 7; }
            case 7 -> { request(Action.STATE, 0, NONE, "", ""); step = 8; }
            case 8 -> { noFailure(); if (view == null) return; request(Action.CALL, view.revision(), NONE, "", ""); step = 31; }
            case 31 -> { noFailure(); if (view == null) return; request(Action.CALL, view.revision(), NONE, "", ""); step = 32; }
            case 32 -> { noFailure(); if (view == null) return; request(Action.START, view.revision(), NONE, "", ""); step = 9; }
            case 9 -> { noFailure(); if (view == null) return; motion = UUID.randomUUID();
                request(Action.PREPARE, view.revision(), motion, "Draft", "Council test"); step = 10; }
            case 10 -> { noFailure(); if (view == null) return;
                require(view.motions().stream().anyMatch(row -> row.id().equals(motion)), "prepared motion missing");
                request(Action.OPEN_BALLOT, view.revision(), motion, "", ""); step = 11; }
            case 11 -> { noFailure(); if (view == null) return; ballot = view.activeBallot();
                require(ballot != null && !ballot.equals(NONE), "ballot missing");
                mark("ballot-id", ballot.toString()); mark("motion-id", motion.toString());
                mark("initial-open", "ready"); step = 12; }
            case 12 -> { if (!has("initial-cast-B") || !has("initial-cast-C")) return;
                request(Action.BALLOT, 0, ballot, "", ""); step = 13; }
            case 13 -> { noFailure(); if (vote == null) return;
                require(vote.ballot().threshold() == 2 && vote.ballot().electorateLabels().size() == 2, "fixed electorate or threshold wrong");
                require(vote.ballot().namedVotes().size() == 2, "public named votes missing");
                request(Action.STATE, 0, NONE, "", ""); step = 14; }
            case 14 -> { noFailure(); if (view == null) return; request(Action.CLOSE_BALLOT, view.revision(), ballot, "", ""); step = 15; }
            case 15 -> { noFailure(); if (view == null) return; request(Action.BALLOT, 0, ballot, "", ""); step = 16; }
            case 16 -> { noFailure(); if (vote == null) return;
                var b = vote.ballot();
                require(b.status() == BallotStatus.CLOSED && b.totals().getOrDefault("yes", 0) == 1
                        && b.totals().getOrDefault("abstain", 0) == 1 && b.passedOptions().isEmpty(), "closed result wrong");
                checks.add("two distinct house representatives cast a public ballot; fixed threshold and closed aggregate verified");
                mark("initial-closed", "ready"); request(Action.STATE, 0, NONE, "", ""); step = 17; }
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
                else step = 4; }
            case 3 -> { if (notice == null) return; require(notice.message().contains("Chancellor"), "non-operator host request accepted");
                checks.add("non-operator host action denied"); mark("initial-denied-B", "ready"); step = 4; }
            case 4 -> { if (!has("initial-open")) return; ballot = readId("ballot-id");
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
                Vote late=new Vote(delayed,true,snapshot.ballot(),snapshot.description(),snapshot.canCast(),snapshot.reason());
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
                if (role.equals("A")) request(Action.STATE, 0, motion, "", "");
                else request(Action.BALLOT, 0, secretBallot, "", ""); step = 2; }
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
                c.getWindow().setWindowedSize(1280, 720); command(c, "chronicle council proceedings"); wait = 30; step = 10; }
            case 10 -> { if (!(c.currentScreen instanceof CouncilBusinessScreen)) return;
                ScreenshotRecorder.saveScreenshot(shared.toFile(), "proceedings-normal.png", c.getFramebuffer(), 1, ignored -> {});
                c.getWindow().setWindowedSize(640, 480); wait = 30; step = 11; }
            case 11 -> { require(c.currentScreen instanceof CouncilBusinessScreen, "proceedings screen closed");
                ScreenshotRecorder.saveScreenshot(shared.toFile(), "proceedings-compact.png", c.getFramebuffer(), 1, ignored -> {});
                checks.add("native ballot and proceedings screen screenshots captured in report directory");
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
    private static void secretProjection(Vote value) {
        require(value.ballot().namedVotes().isEmpty() && value.ballot().totals().isEmpty(), "secret projection exposed aggregate or named votes");
    }
    private void finish(MinecraftClient c, String error) {
        if (done) return; done = true;
        try { var json = new JsonObject(); json.addProperty("result", error == null ? "PASS" : "FAIL");
            json.addProperty("detail", error == null ? "Completed" : error); json.add("checks", new Gson().toJsonTree(checks));
            Files.writeString(output, new Gson().toJson(json), StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
        c.scheduleStop();
    }
    private static void press(Screen screen, String label) {
        var button = screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .filter(value -> value.active && value.getMessage().getString().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalStateException("button unavailable: " + label));
        button.onPress(new MouseInput(0, 0));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
