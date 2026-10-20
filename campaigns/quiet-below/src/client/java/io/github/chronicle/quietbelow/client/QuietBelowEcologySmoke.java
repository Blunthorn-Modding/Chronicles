package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import io.github.chronicle.core.client.AuthoringPreviewRenderer;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.menu.CoreMenuModel.Page;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.NarratorMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/** Opt-in two-process ecology rehearsal on the disposable loopback fixture only. */
final class QuietBelowEcologySmoke {
    private static final String OUTPUT = System.getenv("CHRONICLE_ECOLOGY_SMOKE");
    private static final String PHASE = System.getenv("CHRONICLE_ECOLOGY_PHASE");
    private static final String ADDRESS = "127.0.0.1:25575";
    private final List<String> messages = new ArrayList<>(), checks = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private final long overallDeadline = System.nanoTime() + 180_000_000_000L;
    private int index, baseline, sculkBefore, pausedSculk;
    private long deadline, until, rootFrames;
    private String vitalityClock;
    private boolean started, connected, built, finished;

    static void register() {
        if (OUTPUT == null) return;
        if (!List.of("initial","restart","surface","organic","vitality").contains(PHASE)) throw new IllegalArgumentException("Invalid ecology smoke phase");
        var smoke = new QuietBelowEcologySmoke();
        ClientReceiveMessageEvents.GAME.register((text, overlay) -> smoke.messages.add(text.getString()));
        ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
    }

    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            require(System.nanoTime() < overallDeadline, "ecology smoke overall timeout");
            if (client.player == null && client.getOverlay() == null && client.currentScreen instanceof AccessibilityOnboardingScreen)
                throw new IllegalStateException("Pack preferences failed to suppress accessibility onboarding");
            if (!connected && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                require(client.options.getGuiScale().getValue()==2,"pack GUI scale not loaded");checks.add("new client loads GUI scale 2");
                require(client.options.getNarrator().getValue()==NarratorMode.OFF,"pack narrator setting not loaded");checks.add("new client loads narrator off");
                require(!client.options.onboardAccessibility,"pack onboarding preference not loaded");checks.add("new client skips accessibility onboarding without test bypass");
                require(client.options.skipMultiplayerWarning,"pack multiplayer acknowledgement not loaded");checks.add("new client loads multiplayer acknowledgement");
                connected = true;
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS),
                        new ServerInfo("Chronicle ecology fixture", ADDRESS, ServerInfo.ServerType.OTHER), false, null);
            }
            if (client.player == null || client.world == null || (client.currentScreen != null && !(client.currentScreen instanceof CoreMenuScreen))) return;
            require(client.getCurrentServerEntry() != null && ADDRESS.equals(client.getCurrentServerEntry().address), "ecology fixture address mismatch");
            if (!built) { build(client); built = true; }
            if (index >= steps.size()) { finish(client, true, "ecology rehearsal complete"); return; }
            Step step = steps.get(index);
            if (!started) { baseline = messages.size(); deadline = System.nanoTime() + step.timeoutMillis * 1_000_000L; step.start.run(); started = true; }
            if (step.done.getAsBoolean()) { checks.add(step.name); index++; started = false; }
            else require(System.nanoTime() < deadline, "timed out: " + step.name);
        } catch (Exception error) { finish(client, false, error.toString()); }
    }

    private void build(MinecraftClient client) {
        if ("initial".equals(PHASE)) initial(client); else if("surface".equals(PHASE)) surface(client); else if("organic".equals(PHASE)) organic(client); else if("vitality".equals(PHASE)) vitality(client); else restart(client);
    }
    private void vitality(MinecraftClient client){
        command(client,"place vitality Heart","setblock 0 64 0 reinforced_deepslate",".*Changed.*");
        command(client,"place vitality branch","fill 1 64 0 4 64 0 sculk",".*filled.*");
        command(client,"place vitality sensor","setblock 4 65 0 sculk_sensor",".*Changed.*");
        command(client,"place isolated viewing platform","fill 6 63 -1 8 63 1 glass",".*filled.*");
        command(client,"enable vitality authoring","chronicle author mode on","Author mode enabled.*");
        command(client,"create vitality source","chronicle author region create vitality_source","Region draft vitality_source opened.*");
        command(client,"vitality first corner","chronicle author region pos1 0 64 0","Set region draft vitality_source position 1.*");
        command(client,"vitality second corner","chronicle author region pos2 4 65 0","Set region draft vitality_source position 2.*");
        command(client,"save vitality source","chronicle author region save","Saved region vitality_source.*");
        command(client,"look at vitality Heart","tp @s 0.5 65 0.5 0 90",".*Teleported.*");
        command(client,"bind vitality Heart","chronicle author block bind vitality_heart target","Saved block vitality_heart.*");
        command(client,"stand on isolated platform","tp @s 7.5 64 0.5 90 0",".*Teleported.*");
        command(client,"assign vitality source","chronicles setup assign \"quiet_below:source_city\" vitality_source","Requirement assigned.*");
        command(client,"assign vitality Heart","chronicles setup assign \"quiet_below:relic_heart\" vitality_heart","Requirement assigned.*");
        command(client,"index vitality source","chronicles setup tool \"quiet_below:ecology\" index","Source indexing started.*");
        delay("finish vitality index",600);
        command(client,"enable vitality ecology","chronicles setup tool \"quiet_below:ecology\" enable","Growth enabled.*");
        openVitalityMenu(client);
        for(int i=0;i<3;i++)vitalityButton(client,"Faster vitality loss");
        for(int i=0;i<2;i++)vitalityButton(client,"Faster recovery");
        check("minimum vitality rates are disabled",()->{
            var d=((CoreMenuScreen)client.currentScreen).state().detail();
            require(d.controls().stream().filter(c->c.label().equals("Faster vitality loss")||c.label().equals("Faster recovery")).noneMatch(c->c.enabled()),"vitality rates underflow");
            require(d.facts().stream().anyMatch(f->f.label().equals("Full vitality loss")&&f.value().startsWith("1 eligible")),"minimum full-loss setting missing");
        });
        vitalityButton(client,"Lower extent thresholds");vitalityButton(client,"Lower extent thresholds");
        check("minimum extent thresholds are disabled",()->require(((CoreMenuScreen)client.currentScreen).state().detail().controls().stream().filter(c->c.label().equals("Lower extent thresholds")).noneMatch(c->c.enabled()),"extent underflow"));
        vitalityScreenshot(client,"ecology-vitality-settings.png");check("close vitality menu",()->client.currentScreen.close());
        command(client,"start vitality play","chronicle session start","Started Chronicle session.*");
        command(client,"join vitality play","chronicles join",".*(joined|already).*Chronicle participation.*");
        command(client,"cut vitality branch","setblock 1 64 0 air",".*Changed.*");
        command(client,"active play rejects vitality edits","chronicles setup tool \"quiet_below:vitality\" loss_slower",".*requires operator editing mode.*");
        delay("let detached vitality fall through a real eligible minute",70_000);
        command(client,"zero vitality retains terrain and disables native response","quiet_below_native_trial vitality_zero","PASS vitality zero:.*");
        command(client,"pause spent vitality time","chronicle session pause","Paused Chronicle session.*");
        openVitalityMenu(client);check("capture paused vitality clock",()->vitalityClock=vitalityFact(client,"Clock"));
        delay("wait with vitality paused",1_100);
        step("refresh paused vitality status",()->require(press((CoreMenuScreen)client.currentScreen,"Refresh status"),"vitality refresh unavailable"),
                ()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().message().contains("Vitality status refreshed"),10_000);
        check("paused vitality does not advance",()->require(vitalityClock.equals(vitalityFact(client,"Clock")),"paused vitality clock advanced"));
        vitalityScreenshot(client,"ecology-vitality-dormant.png");check("close dormant vitality menu",()->client.currentScreen.close());
        command(client,"replace physical vitality bridge while paused","setblock 1 64 0 sculk",".*Changed.*");
        command(client,"resume vitality recovery","chronicle session resume","Resumed Chronicle session.*");
        delay("allow physical joining without immediate vitality refill",500);
        command(client,"reconnected branch recovers gradually","quiet_below_native_trial vitality_recovering","PASS vitality recovering:.*");
        delay("allow bright recovery through the ready threshold and shared checkpoint",22_000);
        command(client,"recovered branch restores native response","quiet_below_native_trial vitality_ready","PASS vitality ready:.*");
        command(client,"leave vitality trial paused","chronicle session pause","Paused Chronicle session.*");
        openVitalityMenu(client);vitalityScreenshot(client,"ecology-vitality-recovered.png");
    }
    private void vitalityScreenshot(MinecraftClient client,String name){
        check("show vitality readings for "+name,()->{var m=(CoreMenuScreen)client.currentScreen;for(int n=0;n<100;n++)m.scrollDetailForTest(1);});
        screenshot(client,name);
    }
    private String vitalityFact(MinecraftClient client,String name){return ((CoreMenuScreen)client.currentScreen).state().detail().facts().stream().filter(f->f.label().equals(name)).findFirst().orElseThrow().value();}
    private void openVitalityMenu(MinecraftClient client){
        step("open native vitality menu",()->send(client,"chronicles"),()->client.currentScreen instanceof CoreMenuScreen,10_000);
        step("open Game setup for vitality",()->require(press((CoreMenuScreen)client.currentScreen,"Game setup"),"Game setup unavailable"),
                ()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().page()==Page.SETUP,10_000);
        step("select Sculk vitality",()->((CoreMenuScreen)client.currentScreen).selectForTest("tool:quiet_below:vitality"),
                ()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().detail()!=null&&m.state().detail().id().equals("tool:quiet_below:vitality"),10_000);
    }
    private void vitalityButton(MinecraftClient client,String label){
        step("native vitality "+label,()->require(press((CoreMenuScreen)client.currentScreen,label),label+" unavailable"),
                ()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().message().contains("Vitality tuning saved"),10_000);
    }

    private void initial(MinecraftClient client) {
        command(client, "bury corridor in solid stone", "fill -2 60 -2 16 70 4 stone", ".*filled.*");
        command(client, "open corridor interior", "fill 0 65 1 13 66 1 air", ".*filled.*");
        command(client, "place temporary Heart", "setblock 0 64 1 reinforced_deepslate", ".*Changed.*");
        command(client, "place source sculk", "setblock 1 64 1 sculk", ".*Changed.*");
        command(client, "place catalyst support", "setblock 2 64 1 sculk", ".*Changed.*");
        command(client, "place native source catalyst", "setblock 2 65 1 sculk_catalyst", ".*Changed.*");
        command(client, "place native source sensor", "setblock 1 65 1 sculk_sensor", ".*Changed.*");
        command(client, "close barrier", "fill 8 65 1 8 66 1 stone", ".*filled.*");
        command(client, "enable author mode", "chronicle author mode on", "Author mode enabled.*");
        command(client, "create source region draft", "chronicle author region create \"quiet_below:source_city\"", "Region draft quiet_below:source_city opened.*");
        command(client, "set source region first corner", "chronicle author region pos1 0 64 1", "Set region draft quiet_below:source_city position 1.*");
        command(client, "set source region second corner", "chronicle author region pos2 2 66 1", "Set region draft quiet_below:source_city position 2.*");
        command(client, "save source region", "chronicle author region save", "Saved region quiet_below:source_city.*");
        command(client, "look down at Heart", "tp @s 0.5 65 1.5 0 90", ".*Teleported.*");
        command(client, "bind Heart anchor", "chronicle author block bind \"quiet_below:relic_heart\" target", "Saved block quiet_below:relic_heart.*");
        command(client, "assign source city", "chronicles setup assign \"quiet_below:source_city\" \"quiet_below:source_city\"", "Requirement assigned.*");
        command(client, "assign Heart", "chronicles setup assign \"quiet_below:relic_heart\" \"quiet_below:relic_heart\"", "Requirement assigned.*");
        command(client, "start explicit source indexing", "chronicles setup tool \"quiet_below:ecology\" index", "Source indexing started.*");
        delay("allow small loaded source index", 1_000);
        command(client, "enable indexed ecology", "chronicles setup tool \"quiet_below:ecology\" enable", "Growth enabled.*");
        command(client,"save non-default speed before restart","chronicles setup tool \"quiet_below:ecology\" faster","Spread speed saved: 2.00x.*");
        command(client, "clear previous region outline before root test", "chronicles setup tool \"quiet_below:ecology\" hide_root", "Root outline hidden.*");
        delay("settle cleared outline",200);
        check("record private root renderer",()->rootFrames=AuthoringPreviewRenderer.renderedFrames());
        command(client, "show source root without editing terrain", "chronicles setup tool \"quiet_below:ecology\" show_root", "Root outlined privately.*");
        step("root outline renders in frozen world",()->{},()->AuthoringPreviewRenderer.renderedFrames()>rootFrames,10_000);
        check("root location delivered privately to operator",()->require(messages.stream().anyMatch(m->m.contains("Sculk root: 0, 64, 1 in minecraft:overworld")),"missing root coordinates"));
        screenshot(client,"ecology-root-paused.png");
        command(client, "hide source root", "chronicles setup tool \"quiet_below:ecology\" hide_root", "Root outline hidden.*");
        command(client, "start session", "chronicle session start", "Started Chronicle session.*");
        check("active session without participant has no growth", () -> sculkBefore = sculk(client));
        delay("wait without participation", 1_100);
        check("no participant remains inert", () -> require(sculk(client) == sculkBefore, "ecology grew without an eligible participant"));
        command(client, "owned native features reject inactive intake", "quiet_below_native_trial inactive", "PASS native inactive hooks:.*");
        command(client, "join participation", "chronicles join", ".*(joined|already).*Chronicle participation.*");
        step("living source grows before closed barrier", () -> {}, () -> {
            int count = sculk(client); require(count <= 8, "closed corridor exceeded bounded initial growth before barrier: " + count); return count > sculkBefore;
        }, 30_000);
        command(client, "owned catalyst ignores deaths and legacy charge", "quiet_below_native_trial active", "PASS native active hooks:.*");
        delay("observe closed barrier during occupied play", 10_000);
        check("closed barrier has no growth beyond x8", () -> require(sculkBeyondBarrier(client) == 0, "growth crossed the closed barrier"));
        command(client, "open physical barrier", "fill 8 65 1 8 66 1 air", ".*filled.*");
        step("growth reaches corridor beyond opened barrier", () -> {}, () -> sculkBeyondBarrier(client) > 0, 60_000);
        command(client, "pause active ecology", "chronicle session pause", "Paused Chronicle session.*");
        delay("settle pause", 400);
        check("capture paused terrain", () -> pausedSculk = sculk(client));
        delay("paused world remains stable", 1_100);
        check("pause prevents further ecology writes", () -> require(sculk(client) == pausedSculk, "ecology changed terrain while paused"));
        command(client,"place disconnected takeover sculk","setblock 12 64 8 sculk",".*Changed.*");
        command(client,"place disconnected takeover catalyst","setblock 12 65 8 sculk_catalyst",".*Changed.*");
        command(client,"reenter paused editing","chronicle author mode on","Author mode enabled.*");
        command(client,"create takeover region","chronicle author region create extra_sculk","Region draft extra_sculk opened.*");
        command(client,"takeover corner one","chronicle author region pos1 12 64 8","Set region draft extra_sculk position 1.*");
        command(client,"takeover corner two","chronicle author region pos2 12 65 8","Set region draft extra_sculk position 2.*");
        command(client,"save takeover geometry","chronicle author region save","Saved region extra_sculk.*");
        command(client,"assign optional takeover region","chronicles setup assign \"quiet_below:sculk_takeover\" extra_sculk","Requirement assigned.*");
        command(client,"start explicit region takeover","chronicles setup tool \"quiet_below:ownership\" claim","Region takeover started.*");
        delay("finish small loaded region takeover",600);
        command(client,"place non-campaign sculk for cleanup","setblock 6 -64 6 sculk",".*Changed.*");
        command(client,"enable persistent world cleanup","chronicles setup tool \"quiet_below:ownership\" cleanup_on","Non-campaign sculk cleanup enabled.*");
        delay("cleanup waits for occupied active play",600);
        check("paused cleanup preserves stray sculk",()->require(client.world.getBlockState(new BlockPos(6,-64,6)).isOf(Blocks.SCULK),"cleanup changed paused terrain"));
        command(client,"resume cleanup test","chronicle session resume","Resumed Chronicle session.*");
        step("world cleanup deletes non-campaign sculk",()->{},()->client.world.getBlockState(new BlockPos(6,-64,6)).isAir(),30_000);
        check("takeover preserves disconnected sculk and catalyst",()->require(client.world.getBlockState(new BlockPos(12,64,8)).isOf(Blocks.SCULK)&&client.world.getBlockState(new BlockPos(12,65,8)).isOf(Blocks.SCULK_CATALYST),"cleanup deleted explicitly adopted sculk"));
        command(client,"new stray catalyst queues cleanup","setblock 7 -64 6 sculk_catalyst",".*Changed.*");
        step("world cleanup also removes newly placed stray catalyst",()->{},()->client.world.getBlockState(new BlockPos(7,-64,6)).isAir(),10_000);
        command(client,"pause after ownership trial","chronicle session pause","Paused Chronicle session.*");
        screenshot(client, "ecology-corridor-paused.png");
    }

    private void restart(MinecraftClient client) {
        openSpeedMenu(client);
        check("speed persists in actual menu after saving restart",()->require(menuSpeed(client,"2.00x"),"saved speed was not restored"));
        check("close persisted speed menu",()->client.currentScreen.close());
        check("restart begins paused without catch-up", () -> sculkBefore = sculk(client));
        delay("wait paused after saving restart", 1_100);
        check("restart pause preserves terrain", () -> require(sculk(client) == sculkBefore, "ecology caught up while paused after restart"));
        command(client, "temporarily remove Heart anchor", "setblock 0 64 1 air", ".*Changed.*");
        command(client, "missing Heart has explicit reversible status", "chronicles setup tool \"quiet_below:ecology\" refresh", ".*Heart block is missing.*");
        command(client, "restore Heart anchor", "setblock 0 64 1 reinforced_deepslate", ".*Changed.*");
        command(client, "restored Heart is not a permanent fault", "chronicles setup tool \"quiet_below:ecology\" refresh", ".*Paused; resume Chronicle play.*");
        command(client, "prune source while paused", "setblock 1 64 1 stone", ".*Changed.*");
        command(client, "prune and seal the Heart upper face", "setblock 0 65 1 stone", ".*Changed.*");
        command(client, "resume after source pruning", "chronicle session resume", "Resumed Chronicle session.*");
        check("capture pruned source terrain", () -> sculkBefore = sculk(client));
        delay("allow reconciliation after prune", 2_100);
        check("pruned source does not regrow ownership", () -> require(sculk(client) == sculkBefore, "ecology restored or grew from a removed source"));
        command(client, "disconnected branch has clear dormant status", "chronicles setup tool \"quiet_below:ecology\" refresh", ".*Dormant: no living sculk connection to the Heart.*");
        command(client, "replace physical source connection", "setblock 1 64 1 sculk", ".*Changed.*");
        check("capture manually restored connection",()->sculkBefore=sculk(client));
        step("restored sculk wakes branch and resumes growth",()->{},()->sculk(client)>sculkBefore,60_000);
        command(client, "reconnected branch reports active growth", "chronicles setup tool \"quiet_below:ecology\" refresh", ".*Active; spreading along reachable exposed surfaces.*");
        command(client,"persisted cleanup sees newly loaded/restarted world","setblock 7 -64 7 sculk",".*Changed.*");
        step("cleanup remains enabled after saving restart",()->{},()->client.world.getBlockState(new BlockPos(7,-64,7)).isAir(),10_000);
        check("takeover ownership survives restart",()->require(client.world.getBlockState(new BlockPos(12,64,8)).isOf(Blocks.SCULK),"restarted cleanup lost takeover ownership"));
        command(client,"stop cleanup independently of growth","chronicles setup tool \"quiet_below:ownership\" cleanup_off","Non-campaign sculk cleanup stopped.*");
        command(client,"place stray after cleanup disabled","setblock 7 -64 7 sculk",".*Changed.*");
        delay("disabled cleanup does not delete stray",600);
        check("cleanup off preserves non-campaign sculk",()->require(client.world.getBlockState(new BlockPos(7,-64,7)).isOf(Blocks.SCULK),"disabled cleanup deleted sculk"));
        command(client, "disable ecology without terrain rollback", "chronicles setup tool \"quiet_below:ecology\" disable", "Growth disabled; owned terrain and history retained.*");
        check("capture disabled terrain", () -> sculkBefore = sculk(client));
        delay("disabled ecology remains inert", 1_100);
        check("disable preserves existing terrain", () -> require(sculk(client) == sculkBefore, "disable changed terrain"));
        command(client,"leave safe paused snapshot for human review","chronicle session pause","Paused Chronicle session.*");
        command(client,"enable editing for review speed default","chronicle author mode on","Author mode enabled.*");
        command(client,"reset review snapshot to default speed","chronicles setup tool \"quiet_below:ecology\" speed_reset","Spread speed saved: 1.00x.*");
        screenshot(client, "ecology-disabled-after-prune.png");
    }

    private void organic(MinecraftClient client) {
        command(client,"build flat organic-growth surface","fill -12 60 -12 12 64 12 stone",".*filled.*");
        command(client,"place patch Heart","setblock 0 64 -1 reinforced_deepslate",".*Changed.*");
        command(client,"place patch source","setblock 0 64 0 sculk",".*Changed.*");
        command(client,"enable patch editing","chronicle author mode on","Author mode enabled.*");
        command(client,"create patch source region","chronicle author region create patch_source","Region draft patch_source opened.*");
        command(client,"patch source first position","chronicle author region pos1 0 64 -1","Set region draft patch_source position 1.*");
        command(client,"patch source second position","chronicle author region pos2 0 64 0","Set region draft patch_source position 2.*");
        command(client,"save patch source","chronicle author region save","Saved region patch_source.*");
        command(client,"look at patch Heart","tp @s 0.5 65 -0.5 0 90",".*Teleported.*");
        command(client,"bind patch Heart","chronicle author block bind patch_heart target","Saved block patch_heart.*");
        command(client,"stand on the patch surface","tp @s 0.5 65 6.5 180 30",".*Teleported.*");
        command(client,"assign patch source","chronicles setup assign \"quiet_below:source_city\" patch_source","Requirement assigned.*");
        command(client,"assign patch Heart","chronicles setup assign \"quiet_below:relic_heart\" patch_heart","Requirement assigned.*");
        command(client,"index patch source","chronicles setup tool \"quiet_below:ecology\" index","Source indexing started.*");
        delay("finish patch source index",600);
        command(client,"enable patch growth","chronicles setup tool \"quiet_below:ecology\" enable","Growth enabled.*");
        for(int i=0;i<4;i++)command(client,"accelerate patch for visual check "+i,"chronicles setup tool \"quiet_below:ecology\" faster","Spread speed saved:.*");
        command(client,"start patch play","chronicle session start","Started Chronicle session.*");
        command(client,"join patch play","chronicles join",".*(joined|already).*Chronicle participation.*");
        step("grow an early connected patch",()->{},()->patch(client).size()>=49,30_000);
        command(client,"pause partial patch","chronicle session pause","Paused Chronicle session.*");
        check("early patch branches across both axes",()->{
            var cells=patch(client);int x=cells.stream().mapToInt(p->Math.abs(p.getX())).max().orElseThrow(),z=cells.stream().mapToInt(p->Math.abs(p.getZ())).max().orElseThrow();
            require(Math.max(x,z)<=10&&Math.min(x,z)>=3,"early patch still forms an axial line: "+x+","+z);
        });
        command(client,"hold patch viewing position","gamemode spectator @s",".*(Spectator|spectator).*");
        command(client,"view partial patch from above","tp @s 0.5 80 0.5 0 90",".*Teleported.*");
        screenshot(client,"ecology-organic-patch.png");
    }
    private List<BlockPos> patch(MinecraftClient client) {
        List<BlockPos> cells=new ArrayList<>();
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)if(client.world.getBlockState(new BlockPos(x,64,z)).isOf(Blocks.SCULK))cells.add(new BlockPos(x,64,z));
        return cells;
    }
    private void surface(MinecraftClient client) {
        command(client,"build isolated stone cube","fill 0 64 0 4 68 4 stone",".*filled.*");
        command(client,"place cube Heart","setblock 0 65 2 reinforced_deepslate",".*Changed.*");
        command(client,"open temporary anchor sightline","fill 0 66 2 0 67 2 air",".*filled.*");
        command(client,"author cube source","chronicle author mode on","Author mode enabled.*");
        command(client,"create cube source region","chronicle author region create cube_source","Region draft cube_source opened.*");
        command(client,"cube source first corner","chronicle author region pos1 0 65 2","Set region draft cube_source position 1.*");
        command(client,"cube source second corner","chronicle author region pos2 0 66 2","Set region draft cube_source position 2.*");
        command(client,"save cube source region","chronicle author region save","Saved region cube_source.*");
        command(client,"look at cube Heart","tp @s 0.5 66 2.5 0 90",".*Teleported.*");
        command(client,"bind cube Heart","chronicle author block bind cube_heart target","Saved block cube_heart.*");
        command(client,"restore cube sightline stone","setblock 0 67 2 stone",".*Changed.*");
        command(client,"place cube source sculk","setblock 0 66 2 sculk",".*Changed.*");
        command(client,"view cube from outside","tp @s -3 67 2.5 -90 20",".*Teleported.*");
        command(client,"assign cube source","chronicles setup assign \"quiet_below:source_city\" cube_source","Requirement assigned.*");
        command(client,"assign cube Heart","chronicles setup assign \"quiet_below:relic_heart\" cube_heart","Requirement assigned.*");
        command(client,"index cube source","chronicles setup tool \"quiet_below:ecology\" index","Source indexing started.*");
        delay("finish cube source index",600);
        command(client,"enable cube ecology","chronicles setup tool \"quiet_below:ecology\" enable","Growth enabled.*");
        openSpeedMenu(client);
        menuSpeedButton(client,"Slower","0.50x");
        menuSpeedButton(client,"Slower","0.25x");
        check("minimum speed disables slower",()->require(((CoreMenuScreen)client.currentScreen).state().detail().controls().stream().filter(c->c.label().equals("Slower")).noneMatch(c->c.enabled()),"minimum speed can underflow"));
        menuSpeedButton(client,"Reset speed","1.00x");
        for(String value:List.of("2.00x","4.00x","8.00x","16.00x"))menuSpeedButton(client,"Faster",value);
        check("maximum speed disables faster",()->require(((CoreMenuScreen)client.currentScreen).state().detail().controls().stream().filter(c->c.label().equals("Faster")).noneMatch(c->c.enabled()),"maximum speed can overflow"));
        screenshot(client,"ecology-speed-menu.png");
        check("close speed menu",()->client.currentScreen.close());
        command(client,"start cube play","chronicle session start","Started Chronicle session.*");
        command(client,"join cube play","chronicles join",".*(joined|already).*Chronicle participation.*");
        command(client,"active play rejects speed edits","chronicles setup tool \"quiet_below:ecology\" slower",".*requires operator editing mode.*");
        step("cube growth turns adjacent opposite top and bottom exposed faces",()->{},()->
                client.world.getBlockState(new BlockPos(2,66,0)).isOf(Blocks.SCULK)
                &&client.world.getBlockState(new BlockPos(4,66,2)).isOf(Blocks.SCULK)
                &&client.world.getBlockState(new BlockPos(2,68,2)).isOf(Blocks.SCULK)
                &&client.world.getBlockState(new BlockPos(2,64,2)).isOf(Blocks.SCULK),60_000);
        check("cube interior remains intact",()->require(client.world.getBlockState(new BlockPos(2,66,2)).isOf(Blocks.STONE),"growth excavated a closed cube"));
        command(client,"pause cube after wrapping","chronicle session pause","Paused Chronicle session.*");
        command(client,"hold an unobstructed cube viewing position","gamemode spectator @s",".*(Spectator|spectator).*");
        command(client,"look at the wrapped cube","tp @s -5 71 -5 -45 30",".*Teleported.*");
        screenshot(client,"ecology-wrapped-cube.png");
    }
    private void openSpeedMenu(MinecraftClient client) {
        step("open native ecology speed menu",()->send(client,"chronicles"),()->client.currentScreen instanceof CoreMenuScreen,10_000);
        step("open Game setup for speed",()->require(press((CoreMenuScreen)client.currentScreen,"Game setup"),"Game setup unavailable"),
                ()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().page()==Page.SETUP,10_000);
        step("select Sculk ecology speed controls",()->((CoreMenuScreen)client.currentScreen).selectForTest("tool:quiet_below:ecology"),
                ()->client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().detail()!=null&&m.state().detail().id().equals("tool:quiet_below:ecology"),10_000);
    }
    private boolean menuSpeed(MinecraftClient client,String value) {
        return client.currentScreen instanceof CoreMenuScreen m&&!m.pending()&&m.state().detail()!=null&&m.state().detail().facts().stream().anyMatch(f->f.label().equals("Spread speed")&&f.value().startsWith(value));
    }
    private void menuSpeedButton(MinecraftClient client,String button,String expected) {
        step("native "+button+" saves "+expected,()->require(press((CoreMenuScreen)client.currentScreen,button),button+" unavailable"),
                ()->menuSpeed(client,expected)&&((CoreMenuScreen)client.currentScreen).state().message().contains("Spread speed saved"),10_000);
    }
    private boolean press(CoreMenuScreen m,String label) {
        for(int n=0;n<60;n++)m.scrollDetailForTest(1);
        for(int n=0;n<120;n++){if(m.triggerForTest(label))return true;m.scrollDetailForTest(-1);}return false;
    }

    private int sculk(MinecraftClient client) {
        int count = 0;
        for (int x = 0; x <= 14; x++) for (int y = 64; y <= 66; y++) for (int z = 0; z <= 2; z++)
            if (client.world.getBlockState(new BlockPos(x, y, z)).isOf(Blocks.SCULK)) count++;
        return count;
    }

    private int sculkBeyondBarrier(MinecraftClient client) {
        int count = 0;
        for (int x = 9; x <= 14; x++) for (int y = 64; y <= 66; y++) for (int z = 0; z <= 2; z++)
            if (client.world.getBlockState(new BlockPos(x, y, z)).isOf(Blocks.SCULK)) count++;
        return count;
    }

    private void screenshot(MinecraftClient client, String name) {
        check("clear overlays for " + name, () -> { client.inGameHud.getChatHud().clear(false); client.getToastManager().clear(); });
        delay("render " + name, 400);
        step("capture " + name, () -> ScreenshotRecorder.saveScreenshot(Path.of(OUTPUT).toFile(), name, client.getFramebuffer(), 1, ignored -> {}),
                () -> Files.isRegularFile(Path.of(OUTPUT, "screenshots", name)), 20_000);
    }

    private void command(MinecraftClient client, String name, String command, String response) { step(name, () -> send(client, command), () -> received(response), 20_000); }
    private void delay(String name, long millis) { step(name, () -> until = System.nanoTime() + millis * 1_000_000L, () -> System.nanoTime() >= until, millis + 5_000); }
    private void check(String name, Runnable assertion) { step(name, assertion, () -> true, 1_000); }
    private void step(String name, Runnable start, BooleanSupplier done, long timeoutMillis) { steps.add(new Step(name, start, done, timeoutMillis)); }
    private void send(MinecraftClient client, String command) { client.player.networkHandler.sendChatCommand(command); }
    private boolean received(String expression) { Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE); return messages.subList(baseline, messages.size()).stream().anyMatch(message -> pattern.matcher(message).matches()); }

    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (finished) return;
        finished = true;
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT).resolve("ecology-" + PHASE + ".json"), new Gson().toJson(Map.of(
                    "result", pass ? "PASS" : "FAIL", "detail", detail, "checks", checks,
                    "step", index < steps.size() ? steps.get(index).name : "complete", "messages", messages)));
        } catch (Exception error) { error.printStackTrace(); }
        client.scheduleStop();
    }

    private static void require(boolean condition, String detail) { if (!condition) throw new IllegalStateException(detail); }
    private record Step(String name, Runnable start, BooleanSupplier done, long timeoutMillis) { }
}
