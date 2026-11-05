package io.github.chronicle.lastcouncil.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.client.DossierScreen;
import io.github.chronicle.core.menu.CoreMenuModel;
import io.github.chronicle.lastcouncil.content.HouseCastStore;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.*;
import static io.github.chronicle.lastcouncil.network.CouncilPayloads.NONE;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.*;
import net.minecraft.client.util.ScreenshotRecorder;

/** Explicitly opted-in test client; talks only to the ordinary authenticated server API. */
public final class CouncilSmoke {
    private static CouncilSmoke instance;
    private final Path root, output, shared;
    private final String role, phase;
    private final List<String> checks = new ArrayList<>();
    private final long started=System.nanoTime();
    private boolean connecting,done; private int step,wait; private UUID pending;
    private AdminView admin; private PersonalView personal; private Notice notice;
    private String houseA,houseB; private UUID a,b;
    private CouncilSmoke(String path) {
        root=Path.of(System.getenv("CHRONICLE_COUNCIL_ROOT")).toAbsolutePath().normalize();
        output=Path.of(path).toAbsolutePath().normalize(); shared=output.getParent();
        role=System.getenv("CHRONICLE_COUNCIL_ROLE"); phase=System.getenv("CHRONICLE_COUNCIL_PHASE");
        if(root.getParent()==null || !root.getParent().getFileName().toString().equals("test-runs") || !Files.isRegularFile(root.resolve(".last-council-fixture")) || output.startsWith(root) || !(role.equals("A")||role.equals("B")) || !(phase.equals("initial")||phase.equals("restart"))) throw new IllegalArgumentException("unsafe council fixture");
        a=offline("ChronicleFabricA"); b=offline("ChronicleFabricB");
    }
    public static void register(){String p=System.getenv("CHRONICLE_COUNCIL_SMOKE");if(p==null)return;instance=new CouncilSmoke(p);ClientTickEvents.END_CLIENT_TICK.register(instance::tick);}
    public static void admin(AdminView v){if(instance!=null && v.request().equals(instance.pending))instance.admin=v;}
    public static void personal(PersonalView v){if(instance!=null && v.request().equals(instance.pending))instance.personal=v;}
    public static void notice(Notice v){if(instance!=null && v.request().equals(instance.pending))instance.notice=v;}
    private static UUID offline(String name){return UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));}
    private void tick(MinecraftClient c){if(done)return;try{
        if(System.nanoTime()-started>150_000_000_000L)throw new IllegalStateException("timeout step "+step);
        if(c.player==null && c.getOverlay()==null && c.currentScreen instanceof AccessibilityOnboardingScreen)c.setScreen(new TitleScreen());
        if(!connecting && c.player==null && c.getOverlay()==null && c.currentScreen instanceof TitleScreen){connecting=true;ConnectScreen.connect(c.currentScreen,c,ServerAddress.parse("127.0.0.1:25575"),new ServerInfo("Council smoke","127.0.0.1:25575",ServerInfo.ServerType.OTHER),false,null);return;}
        if(c.player==null)return;
        if(step==0){Files.writeString(shared.resolve(phase+"-ready-"+role),"ready");step++;return;}
        if(role.equals("B") && step==1){sendAdmin(Operation.OPEN,0,b,"",NONE);step=2;return;}
        if(role.equals("B") && step==2){if(notice==null)return;require(notice.message().contains("Chancellor"),"non-operator admin request was not denied");checks.add("non-operator admin request denied");step=10;return;}
        if(role.equals("A") && step==1){if(!Files.exists(shared.resolve(phase+"-ready-B")))return;c.player.networkHandler.sendChatCommand("chronicle editing on");wait=30;step=2;return;}
        if(role.equals("A") && step==2){if(wait-->0)return;sendAdmin(Operation.OPEN,0,a,"",NONE);step=3;return;}
        if(role.equals("A") && step==3){if(admin==null)return;require(admin.houses().size()>=2,"two houses required");houseA=admin.houses().get(0).id();houseB=admin.houses().get(1).id();require(!houseA.equals(houseB),"distinct houses required");if(phase.equals("restart")){require(admin.committed(),"cast missing after restart");Files.writeString(shared.resolve(phase+"-committed"),"ready");step=10;return;}require(admin.editable(),"editing unavailable: "+admin.reason());sendAdmin(Operation.ASSIGN,admin.revision(),a,houseA,NONE);step=4;return;}
        if(role.equals("A") && step==4){if(admin==null)return;sendAdmin(Operation.ASSIGN,admin.revision(),b,houseB,NONE);step=5;return;}
        if(role.equals("A") && step==5){if(admin==null)return;sendAdmin(Operation.PREVIEW,admin.revision(),a,"",NONE);step=6;return;}
        if(role.equals("A") && step==6){if(admin==null)return;require(!admin.preview().equals(NONE)&&!admin.committed(),"preview failed");sendAdmin(Operation.COMMIT,admin.revision(),a,"",admin.preview());step=7;return;}
        if(role.equals("A") && step==7){if(admin==null)return;require(admin.committed(),"commit failed");Files.writeString(shared.resolve(phase+"-committed"),"ready");checks.add("two distinct assignments previewed and committed through server guards");step=10;return;}
        if(step==10){if(!Files.exists(shared.resolve(phase+"-committed")))return;request(Section.INFORMATION);step=11;return;}
        if(step>=11 && step<=14){if(personal==null)return;require(!personal.ownHouse().isBlank(),"missing own house");require(personal.houses().size()>=2,"public directory missing");require(personal.section()==Section.values()[step-11],"wrong section");if(step==11){String own=personal.ownHouse();Files.writeString(shared.resolve(phase+"-house-"+role),own);}
            if(step==12 || step==13) {
                var cast=new HouseCastStore(root.resolve("world/chronicle/last-council/cast.json")).load();
                var own=cast.papers().get(role.equals("A")?a:b);
                require(own!=null && own.houseId().equals(personal.ownHouse()),"fixture owner mismatch");
                var expected=step==12?own.secrets():own.goals();
                require(expected.equals(personal.paragraphs()),"recipient papers differ from trusted fixture");
                String publicText=personal.houses().toString();
                for(String paragraph:expected)if(!paragraph.isBlank())require(!publicText.contains(paragraph),"private paper appears in public directory");
            }
            if(step<14){request(Section.values()[step-10]);step++;return;}checks.add("own information, secrets, goals and known secrets received with public directory");c.player.networkHandler.sendChatCommand("chronicle council");step=15;return;}
        if(step==15){if(!(c.currentScreen instanceof PersonalScreen screen))return;press(screen,"Edit character card\u2026");step=16;return;}
        if(step==16){if(!(c.currentScreen instanceof DossierScreen editor))return;
            if(phase.equals("restart")){require(editor.snapshot().pronouns().equals("they/them"),"saved pronouns missing after restart");press(editor,"Done");step=19;return;}
            var fields=editor.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).toList();
            require(fields.size()==3,"character card fields missing");fields.get(1).setText("they/them");press(editor,"Save");step=17;return;}
        if(step==17){if(!(c.currentScreen instanceof DossierScreen editor))return;if(!editor.snapshot().saved() || !editor.snapshot().pronouns().equals("they/them"))return;
            checks.add("pronouns saved through existing character card editor");press(editor,"Done");step=19;return;}
        if(step==19){if(!(c.currentScreen instanceof PersonalScreen screen))return;press(screen,"My house");wait=260;step=20;return;}
        if(step==20){if(wait-->0)return;ScreenshotRecorder.saveScreenshot(shared.toFile(),phase+"-"+role+"-house.png",c.getFramebuffer(),1,t->{});step=21;return;}
        if(step==21){Path other=shared.resolve(phase+"-house-"+(role.equals("A")?"B":"A"));if(!Files.exists(other))return;require(!Files.readString(other).equals(Files.readString(shared.resolve(phase+"-house-"+role))),"private recipients share house");
            checks.add("distinct recipients received their own fixture secrets and goals, absent from public directory");
            if(role.equals("B")){finish(c,null);return;}c.player.networkHandler.sendChatCommand("chronicle");step=22;return;}
        if(step==22){if(!(c.currentScreen instanceof CoreMenuScreen menu)||menu.pending())return;require(menu.triggerForTest("Players"),"Players navigation unavailable");step=23;return;}
        if(step==23){if(!(c.currentScreen instanceof CoreMenuScreen menu)||menu.pending()||menu.state().page()!=CoreMenuModel.Page.PLAYERS)return;menu.selectForTest(a.toString());step=24;return;}
        if(step==24){if(!(c.currentScreen instanceof CoreMenuScreen menu)||menu.pending()||menu.state().detail()==null)return;require(menu.triggerForTest("House & papers\u2026"),"house extension unavailable");step=25;return;}
        if(step==25){if(!(c.currentScreen instanceof HouseSetupScreen))return;wait=20;step=26;return;}
        if(step==26){if(wait-->0)return;ScreenshotRecorder.saveScreenshot(shared.toFile(),phase+"-setup-normal.png",c.getFramebuffer(),1,t->{});c.getWindow().setWindowedSize(640,480);wait=30;step=27;return;}
        if(step==27){if(wait-->0)return;ScreenshotRecorder.saveScreenshot(shared.toFile(),phase+"-setup-compact.png",c.getFramebuffer(),1,t->{});checks.add("Players extension opened native House setup; normal and compact screenshots captured");wait=20;step=28;return;}
        if(step==28){if(wait-->0)return;finish(c,null);}
    }catch(Throwable e){finish(c,e.toString());}}
    private static void press(Screen screen,String label){var button=screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(x->x.active && x.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new IllegalStateException("button unavailable: "+label));button.onPress(new MouseInput(0,0));}
    private void sendAdmin(Operation op,long revision,UUID target,String house,UUID preview){pending=UUID.randomUUID();admin=null;notice=null;ClientPlayNetworking.send(new AdminRequest(pending,op,revision,target,house,false,preview));}
    private void request(Section section){pending=UUID.randomUUID();personal=null;notice=null;ClientPlayNetworking.send(new PersonalRequest(pending,section,0));}
    private void finish(MinecraftClient c,String error){if(done)return;done=true;try{var json=new JsonObject();json.addProperty("result",error==null?"PASS":"FAIL");json.addProperty("detail",error==null?"Completed":error);json.add("checks",new Gson().toJsonTree(checks));Files.writeString(output,new Gson().toJson(json));}catch(Exception ignored){}c.scheduleStop();}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
