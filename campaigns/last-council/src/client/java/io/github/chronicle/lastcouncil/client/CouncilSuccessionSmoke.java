package io.github.chronicle.lastcouncil.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ballots.BallotStatus;
import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.*;
import io.github.chronicle.lastcouncil.network.ProceedingsPayloads.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;

/** Disposable native clients use only the ordinary authenticated Council packets. */
public final class CouncilSuccessionSmoke {
    private static CouncilSuccessionSmoke instance;
    private static final UUID NONE = CouncilPayloads.NONE;
    private final Path root, output, shared;
    private final String role, phase;
    private final List<String> checks = new ArrayList<>();
    private final long started = System.nanoTime();
    private boolean connecting, done;
    private int step, wait;
    private UUID pending, first, replacement, finalBallot, policy;
    private String houseA, houseB, houseC, titleA, titleB;
    private AdminView admin;
    private View view;
    private Vote vote;
    private Notice notice;

    private CouncilSuccessionSmoke(String path) {
        root = Path.of(System.getenv("CHRONICLE_SUCCESSION_ROOT")).toAbsolutePath().normalize();
        output = Path.of(path).toAbsolutePath().normalize(); shared = output.getParent();
        role = System.getenv("CHRONICLE_SUCCESSION_ROLE"); phase = System.getenv("CHRONICLE_SUCCESSION_PHASE");
        if (root.getParent() == null || !root.getParent().getFileName().toString().equals("test-runs")
                || !Files.isRegularFile(root.resolve(".council-succession-fixture")) || output.startsWith(root)
                || !Set.of("A", "B", "C").contains(role) || !Set.of("initial", "restart").contains(phase))
            throw new IllegalArgumentException("unsafe succession fixture");
    }
    public static void register() {
        String path = System.getenv("CHRONICLE_SUCCESSION_SMOKE"); if (path == null) return;
        instance = new CouncilSuccessionSmoke(path); ClientTickEvents.END_CLIENT_TICK.register(instance::tick);
    }
    public static void admin(AdminView value) { if (instance != null && value.request().equals(instance.pending)) instance.admin = value; }
    public static void view(View value) { if (instance != null && value.request().equals(instance.pending)) instance.view = value; }
    public static void vote(Vote value) { if (instance != null && value.request().equals(instance.pending)) instance.vote = value; }
    public static void notice(Notice value) { if (instance != null && value.request().equals(instance.pending)) instance.notice = value; }
    private static UUID offline(String name) { return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)); }
    private void mark(String name, String value) throws Exception { Files.writeString(shared.resolve(name), value, StandardCharsets.UTF_8); }
    private boolean has(String name) { return Files.isRegularFile(shared.resolve(name)); }
    private String read(String name) throws Exception { return Files.readString(shared.resolve(name), StandardCharsets.UTF_8).trim(); }
    private UUID id(String name) throws Exception { return UUID.fromString(read(name)); }
    private void command(MinecraftClient c, String value) { c.player.networkHandler.sendChatCommand(value); }
    private void admin(Operation action, long revision, UUID target, String house) {
        long officeRevision = admin == null ? -1 : admin.officeRevision();
        pending = UUID.randomUUID(); admin = null; notice = null;
        ClientPlayNetworking.send(new AdminRequest(pending, action, revision, target, house, true, NONE, officeRevision));
    }
    private void request(Action action, long revision, UUID reference, String text) {
        pending = UUID.randomUUID(); view = null; vote = null; notice = null;
        ClientPlayNetworking.send(new Request(pending, action, revision, reference, "", text, "", false, false));
    }
    private void noFailure() { if (notice != null) throw new IllegalStateException("server refused request: " + notice.message()); }
    private void tick(MinecraftClient c) {
        if (done) return;
        try {
            if (System.nanoTime() - started > 200_000_000_000L) throw new IllegalStateException("timeout " + role + " step " + step);
            if (c.player == null && c.getOverlay() == null && c.currentScreen instanceof AccessibilityOnboardingScreen) c.setScreen(new TitleScreen());
            if (!connecting && c.player == null && c.getOverlay() == null && c.currentScreen instanceof TitleScreen) {
                connecting = true; ConnectScreen.connect(c.currentScreen, c, ServerAddress.parse("127.0.0.1:25575"),
                        new ServerInfo("Council succession smoke", "127.0.0.1:25575", ServerInfo.ServerType.OTHER), false, null); return;
            }
            if (c.player == null) return;
            if (wait > 0) { wait--; return; }
            if (step == 0) { mark(phase + "-ready-" + role, "ready"); step = 1; return; }
            if (phase.equals("restart")) restart(c); else if (role.equals("A")) host(c); else voter(c);
        } catch (Throwable failure) { finish(c, failure.toString()); }
    }
    private void host(MinecraftClient c) throws Exception {
        switch (step) {
            case 1 -> { if (!has("initial-ready-B") || !has("initial-ready-C")) return; command(c, "chronicle editing on"); wait=30; step=2; }
            case 2 -> { admin(Operation.OPEN, 0, offline("ChronicleFabricA"), ""); step=3; }
            case 3 -> { noFailure(); if (admin==null) return; var houses=admin.houses();
                require(admin.editable() && houses.size()>=3, "three editable houses required");
                houseA=houses.get(0).id(); houseB=houses.get(1).id(); houseC=houses.get(2).id();
                titleA=houses.get(0).title(); titleB=houses.get(1).title();
                require(Set.of(houseA,houseB,houseC).size()==3,"houses must differ");
                mark("house-A",houseA); mark("house-B",houseB); mark("house-C",houseC); mark("title-A",titleA); mark("title-B",titleB);
                require(admin.players().stream().anyMatch(p -> p.id().equals(offline("CouncilAbsentCandidate")) && p.houseId().equals(houseA)), "preseeded candidate missing");
                admin(Operation.APPOINT_CHANCELLOR,admin.revision(),offline("ChronicleFabricA"),""); step=4; }
            case 4 -> { noFailure(); if(admin==null)return; require(admin.chancellor().equals(offline("ChronicleFabricA")), "appointment missing"); admin(Operation.ASSIGN,admin.revision(),offline("ChronicleFabricB"),houseB); step=5; }
            case 5 -> { noFailure(); if(admin==null)return; admin(Operation.ASSIGN,admin.revision(),offline("ChronicleFabricC"),houseC); step=6; }
            case 6 -> { noFailure(); if(admin==null)return;
                command(c,"fill -5 64 -5 5 64 5 minecraft:stone"); command(c,"setblock 0 65 4 minecraft:lectern");
                command(c,"tp ChronicleFabricB 0 65 0"); command(c,"tp ChronicleFabricC 1 65 0"); command(c,"tp ChronicleFabricA 10 65 0");
                command(c,"chronicle editing off"); wait=30; mark("join","ready"); step=7; }
            case 7 -> { if(!has("joined-B")||!has("joined-C"))return; command(c,"chronicle session start"); wait=30; step=8; }
            case 8 -> { request(Action.STATE,0,NONE,""); step=9; }
            case 9 -> { noFailure(); if(view==null)return; request(Action.START,view.revision(),NONE,""); step=10; }
            case 10 -> { noFailure(); if(view==null)return; policy=UUID.randomUUID();
                pending=UUID.randomUUID(); view=null; vote=null; notice=null;
                ClientPlayNetworking.send(new Request(pending,Action.PREPARE,0,policy,"","Policy awaiting decision","Policy",false,false)); step=30; }
            case 30 -> { noFailure(); if(view==null)return; require(!view.regency().canOpen(),"pending policy did not block regency"); request(Action.OPEN_REGENCY,view.revision(),NONE,""); step=31; }
            case 31 -> { if(notice==null)return; request(Action.STATE,0,NONE,""); step=32; }
            case 32 -> { noFailure(); if(view==null)return; request(Action.DEFER,view.revision(),policy,"Deferred for this meeting"); step=33; }
            case 33 -> { noFailure(); if(view==null)return; require(view.regency().canOpen(),"deferred policy still blocks regency"); checks.add("pending policy blocked regency until deferred"); c.setScreen(new CouncilBusinessScreen(view,null)); wait=5; step=37; }
            case 37 -> { require(c.currentScreen instanceof CouncilBusinessScreen,"proceedings screen missing"); press(c,"Regency vote..."); require(c.currentScreen instanceof ConfirmScreen,"regency confirmation missing"); wait=5; step=38; }
            case 38 -> { press(c,"Yes"); wait=10; step=39; }
            case 39 -> { require(c.currentScreen instanceof CouncilBusinessScreen,"proceedings did not return after regency");
                var screen=(CouncilBusinessScreen)c.currentScreen; var updated=screen.snapshot();
                if(updated.activeBallot()==null||updated.activeBallot().equals(NONE))return;
                require(updated.detail()!=null,"regency detail missing");
                var labels=screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).map(b->b.getMessage().getString()).toList();
                require(labels.contains("Close ballot...")&&labels.contains("Void ballot..."),"active regency controls missing");
                require(updated.detail().title().toLowerCase(Locale.ROOT).contains("regency"),"active regency detail not selected");
                ScreenshotRecorder.saveScreenshot(shared.toFile(),"regency-active-proceedings.png",c.getFramebuffer(),1,ignored->{});
                checks.add("native Regency sidebar opens confirmation and retains active ballot details/close/void controls"); view=updated; step=11; }
            case 11 -> { noFailure(); if(view==null)return; first=view.activeBallot(); require(first!=null&&!first.equals(NONE),"first ballot missing"); request(Action.BALLOT,0,first,""); step=12; }
            case 12 -> { noFailure(); if(vote==null)return; verifySlate(vote,Set.of(houseA,houseB,houseC)); request(Action.STATE,0,NONE,""); step=13; }
            case 13 -> { noFailure(); if(view==null)return; request(Action.VOID_BALLOT,view.revision(),first,"Incorrect setup"); step=14; }
            case 14 -> { noFailure(); if(view==null)return; request(Action.BALLOT,0,first,""); step=15; }
            case 15 -> { noFailure(); if(vote==null)return; secret(vote); require(vote.ballot().status()==BallotStatus.VOID && vote.ballot().reason().contains("Incorrect setup"),"void reason missing"); request(Action.STATE,0,NONE,""); step=16; }
            case 16 -> { noFailure(); if(view==null)return; request(Action.OPEN_REGENCY,view.revision(),NONE,""); step=17; }
            case 17 -> { noFailure(); if(view==null)return; replacement=view.activeBallot(); require(replacement!=null&&!replacement.equals(first),"replacement identity wrong"); mark("replacement",replacement.toString()); request(Action.BALLOT,0,replacement,""); step=18; }
            case 18 -> { noFailure(); if(vote==null)return; verifySlate(vote,Set.of(houseA,houseB,houseC)); mark("vote-first","ready"); step=19; }
            case 19 -> { if(!has("cast-first-B")||!has("cast-first-C"))return; request(Action.BALLOT,0,replacement,""); step=20; }
            case 20 -> { noFailure(); if(vote==null)return; secret(vote); request(Action.STATE,0,NONE,""); step=21; }
            case 21 -> { noFailure(); if(view==null)return; request(Action.CLOSE_BALLOT,view.revision(),replacement,""); step=22; }
            case 22 -> { noFailure(); if(view==null)return; require(view.regency().candidates().size()==2&&!view.regency().candidates().contains(titleA)&&view.regency().summary().contains("retains its Council vote"),"elimination outcome wrong"); require(!view.regency().canOpen(),"same meeting allowed"); request(Action.OPEN_REGENCY,view.revision(),NONE,""); step=23; }
            case 23 -> { if(notice==null)return; request(Action.STATE,0,NONE,""); step=24; }
            case 24 -> { noFailure(); if(view==null)return; request(Action.FINISH,view.revision(),NONE,""); step=25; }
            case 25 -> { noFailure(); if(view==null)return; request(Action.START,view.revision(),NONE,""); step=26; }
            case 26 -> { noFailure(); if(view==null)return; require(view.meeting()==2,"second meeting missing");
                require(!view.regency().canOpen(),"deferred policy did not return next meeting");request(Action.ADMIT,view.revision(),policy,"");step=34; }
            case 34 -> { noFailure();if(view==null)return;request(Action.OPEN_BALLOT,view.revision(),policy,"");step=35; }
            case 35 -> { noFailure();if(view==null)return;request(Action.CLOSE_BALLOT,view.revision(),view.activeBallot(),"");step=36; }
            case 36 -> { noFailure();if(view==null)return;require(view.regency().canOpen(),"recorded policy still blocks regency");request(Action.OPEN_REGENCY,view.revision(),NONE,"");step=27; }
            case 27 -> { noFailure(); if(view==null)return; finalBallot=view.activeBallot(); require(finalBallot!=null&&!finalBallot.equals(replacement),"final ballot identity wrong"); mark("final",finalBallot.toString()); request(Action.BALLOT,0,finalBallot,""); step=28; }
            case 28 -> { noFailure(); if(vote==null)return; verifySlate(vote,Set.of(houseB,houseC)); checks.add("void replacement preserved the three-house slate; unique lowest candidate eliminated and retained Council vote"); mark("initial-complete","ready"); finish(c,null); }
            default -> throw new IllegalStateException("invalid host step");
        }
    }
    private void voter(MinecraftClient c) throws Exception {
        switch(step) {
            case 1 -> { if(!has("join"))return; command(c,"chronicle players join"); wait=30; step=2; }
            case 2 -> { mark("joined-"+role,"ready"); step=3; }
            case 3 -> { if(!has("vote-first"))return; replacement=id("replacement"); request(Action.CAST,0,replacement,read("house-"+role)); step=4; }
            case 4 -> { noFailure(); if(vote==null)return; secret(vote); require(vote.ballot().ownChoice().equals(read("house-"+role)),"confirmed choice missing"); mark("cast-first-"+role,"ready"); step=5; }
            case 5 -> { if(!has("initial-complete"))return; checks.add("representative cast a final secret choice"); finish(c,null); }
            default -> throw new IllegalStateException("invalid voter step");
        }
    }
    private void restart(MinecraftClient c) throws Exception {
        switch(step) {
            case 1 -> { if(role.equals("A")&&(!has("restart-ready-B")||!has("restart-ready-C")))return; finalBallot=id("final"); houseB=read("house-B"); houseC=read("house-C"); titleB=read("title-B");
                if(role.equals("A"))request(Action.STATE,0,NONE,""); else request(Action.BALLOT,0,finalBallot,""); step=2; }
            case 2 -> { noFailure(); if(role.equals("A")) { if(view==null)return; require(finalBallot.equals(view.activeBallot()),"open ballot lost on restart"); command(c,"tp ChronicleFabricB 0 65 0"); command(c,"tp ChronicleFabricC 1 65 0"); command(c,"tp ChronicleFabricA 0 65 1"); wait=30; mark("restart-inside","ready"); step=3; }
                else { if(vote==null)return; verifySlate(vote,Set.of(houseB,houseC)); step=4; } }
            case 3 -> { if(!has("restart-cast-B")||!has("restart-cast-C"))return; request(Action.CAST,0,finalBallot,houseB); step=5; }
            case 5 -> { if(notice==null)return; request(Action.BALLOT,0,finalBallot,""); step=6; }
            case 6 -> { noFailure(); if(vote==null)return; verifySlate(vote,Set.of(houseB,houseC)); request(Action.STATE,0,NONE,""); step=7; }
            case 7 -> { noFailure(); if(view==null)return; request(Action.CLOSE_BALLOT,view.revision(),finalBallot,""); step=8; }
            case 8 -> { noFailure(); if(view==null)return; require(view.regency().status().equals("CROWNED")&&view.regency().summary().contains(titleB)&&!view.regency().canOpen()&&view.regency().candidates().isEmpty(),"crowning outcome wrong"); admin(Operation.OPEN,0,offline("ChronicleFabricA"),""); step=40; }
            case 40 -> { noFailure();if(admin==null)return;require(admin.houses().stream().noneMatch(PublicHouse::candidate),"public directory retained ended crown candidates");checks.add("public directory clears crown candidates after the election ends");request(Action.OPEN_REGENCY,view.revision(),NONE,"");step=9; }
            case 9 -> { if(notice==null)return; request(Action.STATE,0,NONE,""); step=10; }
            case 10 -> { noFailure(); if(view==null)return; request(Action.FINISH,view.revision(),NONE,""); step=11; }
            case 37 -> { require(c.currentScreen instanceof CouncilBusinessScreen,"proceedings screen missing"); press(c,"Regency vote..."); require(c.currentScreen instanceof ConfirmScreen,"regency confirmation missing"); wait=5; step=38; }
            case 38 -> { press(c,"Yes"); wait=10; step=39; }
            case 39 -> { require(c.currentScreen instanceof CouncilBusinessScreen,"proceedings did not return after regency");
                var screen=(CouncilBusinessScreen)c.currentScreen; var updated=screen.snapshot();
                if(updated.activeBallot()==null||updated.activeBallot().equals(NONE))return;
                require(updated.detail()!=null,"regency detail missing");
                var labels=screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).map(b->b.getMessage().getString()).toList();
                require(labels.contains("Close ballot...")&&labels.contains("Void ballot..."),"active regency controls missing");
                require(updated.detail().title().toLowerCase(Locale.ROOT).contains("regency"),"active regency detail not selected");
                ScreenshotRecorder.saveScreenshot(shared.toFile(),"regency-active-proceedings.png",c.getFramebuffer(),1,ignored->{});
                checks.add("native Regency sidebar opens confirmation and retains active ballot details/close/void controls"); view=updated; step=11; }
            case 11 -> { noFailure(); if(view==null)return; request(Action.START,view.revision(),NONE,""); step=12; }
            case 12 -> { noFailure(); if(view==null)return; require(view.meeting()==3,"ordinary meeting did not continue"); c.getWindow().setWindowedSize(1280,720); command(c,"chronicle council proceedings"); wait=30; step=13; }
            case 13 -> { if(!(c.currentScreen instanceof CouncilBusinessScreen))return; ScreenshotRecorder.saveScreenshot(shared.toFile(),"succession-proceedings.png",c.getFramebuffer(),1,ignored->{}); checks.add("restart preserved fixed electorate and slate; majority crowned the remaining candidate and ordinary meetings continued"); mark("restart-complete","ready"); finish(c,null); }
            case 4 -> { if(!has("restart-inside"))return; request(Action.CAST,0,finalBallot,houseB); step=14; }
            case 14 -> { noFailure(); if(vote==null)return; secret(vote); require(vote.ballot().ownChoice().equals(houseB),"confirmed choice missing"); request(Action.CAST,0,finalBallot,houseC); step=15; }
            case 15 -> { if(notice==null)return; mark("restart-cast-"+role,"ready"); step=16; }
            case 16 -> { if(!has("restart-complete"))return; request(Action.BALLOT,0,finalBallot,""); step=17; }
            case 17 -> { noFailure(); if(vote==null)return; require(vote.ballot().ownChoice().equals(houseB),"final choice did not persist"); checks.add("captured representative cast after restart and choice remained final"); finish(c,null); }
            default -> throw new IllegalStateException("invalid restart step");
        }
    }
    private static void verifySlate(Vote value, Set<String> houses) {
        secret(value); var options=new HashSet<String>(); value.ballot().options().forEach(o->options.add(o.id()));
        var expected=new HashSet<>(houses); expected.add("abstain");
        require(options.equals(expected)&&value.ballot().threshold()==2&&value.ballot().electorateLabels().size()==2,"slate or fixed electorate wrong");
    }
    private static void secret(Vote value) { require(value.ballot().namedVotes().isEmpty()&&value.ballot().totals().isEmpty(),"secret projection exposed votes"); }
    private void finish(MinecraftClient c,String error) {
        if(done)return; done=true;
        try { var json=new JsonObject(); json.addProperty("result",error==null?"PASS":"FAIL"); json.addProperty("detail",error==null?"Completed":error); json.add("checks",new Gson().toJsonTree(checks)); Files.writeString(output,new Gson().toJson(json),StandardCharsets.UTF_8); } catch(Exception ignored) { }
        c.scheduleStop();
    }
    private static void press(MinecraftClient client,String label) {
        var button=client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
            .filter(b->b.active&&b.getMessage().getString().equals(label)).findFirst()
            .orElseThrow(()->new IllegalStateException("button unavailable: "+label));
        button.onPress(new MouseInput(0,0));
    }
    private static void require(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
}
