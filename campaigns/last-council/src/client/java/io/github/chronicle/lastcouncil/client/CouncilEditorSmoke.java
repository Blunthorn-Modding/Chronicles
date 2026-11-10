package io.github.chronicle.lastcouncil.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.lastcouncil.network.CatalogueEditorPayloads.*;
import io.github.chronicle.lastcouncil.network.CataloguePayloads.Category;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.AdminView;
import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;

/** Opt-in native source editor fixture. No test projections are accepted as server results. */
public final class CouncilEditorSmoke {
    private static CouncilEditorSmoke instance;
    private final Path root,output,shared; private final String role,phase;
    private final List<String> checks=new ArrayList<>(); private final long started=System.nanoTime();
    private boolean connecting,done; private int step,wait,views,chunks; private UUID forged,adminRequest; private View observed; private AdminView adminView; private String houseId="";
    private CouncilEditorSmoke(String path){
        root=Path.of(System.getenv("CHRONICLE_EDITOR_ROOT")).toAbsolutePath().normalize();
        output=Path.of(path).toAbsolutePath().normalize();shared=output.getParent();
        role=System.getenv("CHRONICLE_EDITOR_ROLE");phase=System.getenv("CHRONICLE_EDITOR_PHASE");
        if(root.getParent()==null||!root.getParent().getFileName().toString().equals("test-runs")||
           !Files.isRegularFile(root.resolve(".council-editor-fixture"))||!Files.isRegularFile(root.resolve(".player-roles-fixture"))||
           output.startsWith(root)||!Set.of("A","B","C").contains(role)||!Set.of("initial","restart").contains(phase))throw new IllegalArgumentException("unsafe editor fixture");
    }
    public static void register(){String path=System.getenv("CHRONICLE_EDITOR_SMOKE");if(path==null)return;instance=new CouncilEditorSmoke(path);ClientTickEvents.END_CLIENT_TICK.register(instance::tick);}
    public static void view(View v){if(instance!=null&&v.request().equals(instance.forged)){instance.views++;instance.observed=v;}}
    public static void chunk(Chunk v){if(instance!=null&&v.request().equals(instance.forged))instance.chunks++;}
    public static void admin(AdminView v){if(instance!=null&&v.request().equals(instance.adminRequest))instance.adminView=v;}
    private void admin(CouncilPayloads.Operation action,long revision){adminRequest=UUID.randomUUID();adminView=null;ClientPlayNetworking.send(new CouncilPayloads.AdminRequest(adminRequest,action,revision,UUID.nameUUIDFromBytes("OfflinePlayer:ChronicleFabricB".getBytes(StandardCharsets.UTF_8)),houseId,false,CouncilPayloads.NONE));}
    private void mark(String name)throws Exception{Files.writeString(shared.resolve(phase+"-"+name),"ready",StandardCharsets.UTF_8);}
    private void mark(String name,String value)throws Exception{Files.writeString(shared.resolve(phase+"-"+name),value,StandardCharsets.UTF_8);}
    private boolean has(String name){return Files.isRegularFile(shared.resolve(phase+"-"+name));}
    private void tick(MinecraftClient c){
        if(done)return;
        try{
            if(System.nanoTime()-started>500_000_000_000L)throw new IllegalStateException("timeout "+role+" "+phase+" step "+step);
            if(c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof AccessibilityOnboardingScreen)c.setScreen(new TitleScreen());
            if(!connecting&&c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof TitleScreen){connecting=true;ConnectScreen.connect(c.currentScreen,c,ServerAddress.parse("127.0.0.1:25575"),new ServerInfo("Source editor smoke","127.0.0.1:25575",ServerInfo.ServerType.OTHER),false,null);return;}
            if(c.player==null)return;if(wait>0){wait--;return;}
            if(step==0){mark("ready-"+role);step=1;return;}
            if(role.equals("C")){unprivileged(c);return;}
            if(role.equals("B")){secondOperator(c);return;}
            host(c);
        }catch(Throwable failure){finish(c,failure.toString());}
    }
    private void secondOperator(MinecraftClient c)throws Exception{
        switch(step){
            case 1->{if(phase.equals("restart")){step=4;return;}forged=UUID.randomUUID();ClientPlayNetworking.send(new Request(forged,Action.OPEN,Category.HOUSES,"",0,0,0,""));step=2;}
            case 2->{if(observed==null)return;require(observed.opening()&&!observed.digest().isBlank(),"second operator could not open source");mark("opened-B");step=3;}
            case 3->{if(!has("changed-A"))return;String digest=observed.digest();forged=UUID.randomUUID();observed=null;ClientPlayNetworking.send(new Request(forged,Action.DELETE,Category.HOUSES,"",0,0,0,digest));step=5;}
            case 5->{if(observed==null)return;require(observed.message().contains("changed"),"stale second-operator write was not refused: "+observed.message());checks.add("second operator's stale delete refused after concurrent save");mark("stale-B");step=4;}
            case 4->{if(has("timeout-deop")&&!has("deopped")){c.player.networkHandler.sendChatCommand("deop ChronicleFabricA");wait=40;step=6;return;}if(has("done-A")){mark("done-B");finish(c,null);}}
            case 6->{mark("deopped");step=7;}
            case 7->{if(!has("timeout-reop"))return;c.player.networkHandler.sendChatCommand("op ChronicleFabricA");wait=40;step=8;}
            case 8->{mark("reopped");step=4;}
        }
    }
    private void unprivileged(MinecraftClient c)throws Exception{
        switch(step){
            case 1->{forged=UUID.randomUUID();views=0;chunks=0;ClientPlayNetworking.send(new Request(forged,Action.OPEN,Category.SECRETS,"",0,0,0,""));ClientPlayNetworking.send(new Upload(forged,"",0,1,new byte[]{123,125}));wait=80;step=2;}
            case 2->{require(views==0&&chunks==0&&!(c.currentScreen instanceof CouncilCatalogueScreen),"non-operator received source data");checks.add("non-operator forged OPEN and Upload received no source response");mark("denied-C");step=3;}
            case 3->{if(has("done-A"))finish(c,null);}
        }
    }
    private void host(MinecraftClient c)throws Exception{
        switch(step){
            case 1->{if(!has("ready-B")||!has("ready-C")||!has("denied-C")||(!phase.equals("restart")&&!has("opened-B")))return;c.player.networkHandler.sendChatCommand("chronicle editing on");wait=30;step=2;}
            case 2->{c.player.networkHandler.sendChatCommand("chronicle setup");step=3;}
            case 3->{if(!(c.currentScreen instanceof CoreMenuScreen menu))return;if(menu.pending())return;
                if(!menu.state().selected().equals("tool:last_council:catalogue")){menu.selectForTest("tool:last_council:catalogue");wait=10;return;}
                require(menu.triggerForTest("Open source catalogue"),"catalogue open control missing");step=4;}
            case 4->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen))return;require(screen.current().editable()||phase.equals("restart"),"editable source unavailable");
                c.getWindow().setWindowedSize(1280,720);wait=20;step=5;}
            case 5->{var screen=(CouncilCatalogueScreen)c.currentScreen;checkGeometry(screen);ScreenshotRecorder.saveScreenshot(shared.toFile(),phase+"-editor-normal.png",c.getFramebuffer(),1,ignored->{});c.getWindow().setWindowedSize(640,480);wait=20;step=6;}
            case 6->{var screen=(CouncilCatalogueScreen)c.currentScreen;checkGeometry(screen);ScreenshotRecorder.saveScreenshot(shared.toFile(),phase+"-editor-compact.png",c.getFramebuffer(),1,ignored->{});checks.add("source opens through Game setup and widgets fit normal and compact frames");
                if(phase.equals("restart")){fields(screen).get(0).setText("A Editor Smoke House");step=19;return;}
                c.getWindow().setWindowedSize(1280,720);wait=20;step=7;}
            case 7->{press(c.currentScreen,"+ Create");step=8;}
            case 8->{if(!(c.currentScreen instanceof CouncilContentEditor))return;List<TextFieldWidget> f=fields(c.currentScreen);require(f.size()==1,"House creation must ask only for a name, not an ID");f.get(0).setText("A Editor Smoke House");press(c.currentScreen,"Save");step=9;}
            case 9->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen))return;require(screen.current().message().equals("Saved."),"house save was refused");if(!has("changed-A")){houseId=screen.current().houses().stream().filter(h->h.label().equals("A Editor Smoke House")).findFirst().orElseThrow().id();require(houseId.startsWith("last_council:house/"),"House ID was not generated");mark("house-id",houseId);checks.add("created a House using its name only; server generated its stable ID");mark("changed-A");}if(!has("stale-B"))return;press(screen,"+ Create");step=32;}
            case 32->{if(!(c.currentScreen instanceof CouncilContentEditor))return;var f=fields(c.currentScreen);require(f.size()==1,"unexpected internal House ID field");f.get(0).setText("Timeout Draft House");mark("timeout-deop");step=33;}
            case 33->{if(!has("deopped"))return;press(c.currentScreen,"Save");wait=230;step=34;}
            case 34->{require(c.currentScreen instanceof CouncilContentEditor,"timed-out editor replaced");var editor=(CouncilContentEditor)c.currentScreen;require(editor.outcomeUncertain()&&CouncilCatalogueClient.hasRetained(),"missing reply did not retain an uncertain draft");press(editor,"Close");require(c.currentScreen instanceof CouncilCatalogueScreen,"timed-out editor could not close");mark("timeout-reop");step=35;}
            case 35->{if(!has("reopped"))return;c.currentScreen.close();step=36;}
            case 36->{if(!(c.currentScreen instanceof CoreMenuScreen menu))return;if(menu.pending())return;if(!menu.state().selected().equals("tool:last_council:catalogue")){menu.selectForTest("tool:last_council:catalogue");wait=10;return;}require(menu.triggerForTest("Open source catalogue"),"cannot reopen source catalogue");step=37;}
            case 37->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen))return;press(screen,"Resume draft");step=38;}
            case 38->{if(!(c.currentScreen instanceof CouncilContentEditor))return;var f=fields(c.currentScreen);require(f.size()==1&&f.get(0).getText().equals("Timeout Draft House"),"resumed draft fields changed");press(c.currentScreen,"Cancel");step=39;}
            case 39->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen))return;fields(screen).get(0).setText("Timeout Draft House");step=40;}
            case 40->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen)||!screen.current().query().equals("Timeout Draft House"))return;require(screen.current().rows().isEmpty(),"unauthorized timed-out save unexpectedly mutated source");checks.add("revoked operator save timed out, preserved its draft without auto-resend, and resumed after reauthorization");press(screen,"Secrets");step=10;}
            case 10->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen)||screen.current().category()!=Category.SECRETS)return;press(screen,"+ Create");step=41;}
            case 41->{if(!(c.currentScreen instanceof CouncilContentEditor))return;fields(c.currentScreen).get(0).setText("editor-smoke-plain");press(c.currentScreen,">");edit(c.currentScreen).setText("The same harvest was promised twice.");ScreenshotRecorder.saveScreenshot(shared.toFile(),"plain-secret-text.png",c.getFramebuffer(),1,ignored->{});press(c.currentScreen,">");press(c.currentScreen,"Choose Houses...");pressPrefix(c.currentScreen,"[ ] A Editor Smoke House");press(c.currentScreen,"Done");press(c.currentScreen,"Save");step=42;}
            case 42->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen))return;require(screen.current().message().equals("Saved."),"plain secret without facts was refused");JsonObject content=JsonParser.parseString(Files.readString(root.resolve("config/chronicle/last-council/houses.json"))).getAsJsonObject();JsonObject plain=null;for(var item:content.getAsJsonArray("secrets"))if(item.getAsJsonObject().get("id").getAsString().equals("editor-smoke-plain"))plain=item.getAsJsonObject();require(plain!=null&&plain.getAsJsonObject("facts").isEmpty()&&plain.get("text").getAsString().equals("The same harvest was promised twice."),"plain secret changed or required facts");checks.add("created a normal secret with text and House selection only, without fact keys");press(screen,"+ Create");step=11;}
            case 11->{if(!(c.currentScreen instanceof CouncilContentEditor))return;fields(c.currentScreen).get(0).setText("editor-smoke-secret");press(c.currentScreen,">");edit(c.currentScreen).setText("Report for ${house.title}: ${fact.value1}");press(c.currentScreen,">");press(c.currentScreen,"Choose Houses...");pressPrefix(c.currentScreen,"[ ] A Editor Smoke House");press(c.currentScreen,"Done");press(c.currentScreen,">");press(c.currentScreen,"Template values (optional)...");press(c.currentScreen,"+ Add value");require(fields(c.currentScreen).isEmpty(),"optional value must not ask for a fact key");edit(c.currentScreen).setText("literal sample");press(c.currentScreen,"Done");press(c.currentScreen,"Done");
                var field=CouncilContentEditor.class.getDeclaredField("draft");field.setAccessible(true);JsonObject draft=(JsonObject)field.get(c.currentScreen);for(int i=0;i<13;i++)draft.getAsJsonObject("facts").addProperty("fact.large"+i,"x".repeat(4096));
                press(c.currentScreen,">");press(c.currentScreen,"Preview sample...");edit(c.currentScreen).setText("{\"house.title\":\"Example\",\"fact.value1\":\"bound\"}");press(c.currentScreen,"Render");require(c.currentScreen instanceof CouncilContentEditor.Preview preview&&preview.result.equals("Report for Example: bound"),"explicit sample render failed");press(c.currentScreen,"Close");press(c.currentScreen,"Save");step=12;}
            case 12->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen))return;require(screen.current().message().equals("Saved."),"large secret save refused");checks.add("created a scoped secret and fact through typed forms, rendered explicit bindings, and saved a multipart definition");press(screen,"Goals");step=13;}
            case 13->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen)||screen.current().category()!=Category.GOALS)return;press(screen,"+ Create");step=14;}
            case 14->{if(!(c.currentScreen instanceof CouncilContentEditor))return;fields(c.currentScreen).get(0).setText("editor-smoke-goal");press(c.currentScreen,">");edit(c.currentScreen).setText("Protect ${house.title}");press(c.currentScreen,">");press(c.currentScreen,"Choose Houses...");pressPrefix(c.currentScreen,"[ ] A Editor Smoke House");press(c.currentScreen,"Done");press(c.currentScreen,"Save");step=15;}
            case 15->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen))return;require(screen.current().message().equals("Saved."),"goal save refused");checks.add("created a scoped House goal through native forms");fields(screen).get(0).setText("editor-smoke-goal");step=20;}
            case 20->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen)||!screen.current().query().equals("editor-smoke-goal"))return;require(screen.current().rows().size()==1,"created goal missing from search");press(screen,"Edit");step=21;}
            case 21->{if(!(c.currentScreen instanceof CouncilContentEditor))return;press(c.currentScreen,">");edit(c.currentScreen).setText("Preserve ${house.title}");press(c.currentScreen,"Save");step=22;}
            case 22->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen))return;require(screen.current().message().equals("Saved."),"goal edit refused");press(screen,"Delete");press(c.currentScreen,"Delete");step=23;}
            case 23->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen)||!screen.current().message().equals("Deleted."))return;require(screen.current().rows().isEmpty(),"deleted goal still present");checks.add("edited and deleted a goal through native forms");press(screen,"Knowledge");step=16;}
            case 16->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen)||screen.current().category()!=Category.KNOWLEDGE)return;press(screen,"Edit rules");step=17;}
            case 17->{if(!(c.currentScreen instanceof CouncilContentEditor))return;fields(c.currentScreen).get(0).setText("1");press(c.currentScreen,">");fields(c.currentScreen).get(0).setText("0");press(c.currentScreen,">");fields(c.currentScreen).get(0).setText("25");press(c.currentScreen,"Save");step=18;}
            case 18->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen))return;require(screen.current().message().equals("Saved."),"rules save refused");checks.add("edited knowledge rules through paged native form");admin(CouncilPayloads.Operation.OPEN,0);step=26;}
            case 26->{if(adminView==null)return;require(adminView.houses().stream().anyMatch(h->h.id().equals(houseId)),"new House unavailable for assignment");admin(CouncilPayloads.Operation.ASSIGN,adminView.revision());step=27;}
            case 27->{if(adminView==null)return;require(adminView.players().stream().anyMatch(p->p.id().equals(UUID.nameUUIDFromBytes("OfflinePlayer:ChronicleFabricB".getBytes(StandardCharsets.UTF_8)))&&p.houseId().equals(houseId)),"new House assignment failed");c.player.networkHandler.sendChatCommand("chronicle editing off");c.player.networkHandler.sendChatCommand("chronicle session start");wait=80;step=28;}
            case 28->{c.player.networkHandler.sendChatCommand("chronicle session pause");c.player.networkHandler.sendChatCommand("chronicle editing on");wait=60;step=29;}
            case 29->{Path cast=root.resolve("world/chronicle/last-council/cast.json");require(Files.isRegularFile(cast),"automatic cast was not issued");mark("cast-hash",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(cast))));forged=UUID.randomUUID();observed=null;ClientPlayNetworking.send(new Request(forged,Action.OPEN,Category.HOUSES,"A Editor Smoke House",0,0,0,""));step=30;}
            case 30->{if(observed==null)return;require(!observed.editable(),"issued cast did not lock source editor");String digest=observed.digest();forged=UUID.randomUUID();observed=null;ClientPlayNetworking.send(new Request(forged,Action.DELETE,Category.HOUSES,"A Editor Smoke House",0,0,0,digest));step=31;}
            case 31->{if(observed==null)return;require(!observed.message().isBlank()&&!observed.message().equals("Deleted."),"forged delete was not refused after cast");checks.add("assigned new House, issued cast and refused forged deletion in read-only source");mark("done-A");finish(c,null);}
            case 19->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen)||!screen.current().query().equals("A Editor Smoke House"))return;require(!screen.current().editable(),"source unlocked after restart");Path cast=root.resolve("world/chronicle/last-council/cast.json");String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(cast)));require(hash.equals(Files.readString(shared.resolve("initial-cast-hash")).trim()),"cast changed across restart");require(screen.current().rows().stream().anyMatch(r->r.label().equals("A Editor Smoke House")),"saved House missing after restart");checks.add("saved House and read-only cast survive server restart");press(screen,"Secrets");step=24;}
            case 24->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen)||screen.current().category()!=Category.SECRETS)return;fields(screen).get(0).setText("editor-smoke-secret");step=25;}
            case 25->{if(!(c.currentScreen instanceof CouncilCatalogueScreen screen)||!screen.current().query().equals("editor-smoke-secret"))return;require(screen.current().rows().size()==1,"saved secret missing after restart");checks.add("multipart secret survives server restart");mark("done-A");finish(c,null);}
        }
    }
    private static List<TextFieldWidget> fields(Screen s){return s.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).toList();}
    private static EditBoxWidget edit(Screen s){return s.children().stream().filter(EditBoxWidget.class::isInstance).map(EditBoxWidget.class::cast).findFirst().orElseThrow();}
    private static void pressPrefix(Screen s,String prefix){var b=s.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(x->x.active&&x.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow(()->new IllegalStateException("row unavailable: "+prefix));b.onPress(new MouseInput(0,0));}
    private static void press(Screen s,String label){var b=s.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(x->x.active&&x.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new IllegalStateException("button unavailable: "+label));b.onPress(new MouseInput(0,0));}
    private static void checkGeometry(CouncilScreen s){
        var widgets=s.children().stream().filter(ClickableWidget.class::isInstance).map(ClickableWidget.class::cast).toList();
        for(var w:widgets)require(w.getX()>=s.left&&w.getY()>=s.top&&w.getX()+w.getWidth()<=s.left+s.panelWidth&&w.getY()+w.getHeight()<=s.top+s.panelHeight,"widget outside frame");
        for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var a=widgets.get(i);var b=widgets.get(j);require(a.getX()+a.getWidth()<=b.getX()||b.getX()+b.getWidth()<=a.getX()||a.getY()+a.getHeight()<=b.getY()||b.getY()+b.getHeight()<=a.getY(),"overlapping controls: "+a.getMessage().getString()+" / "+b.getMessage().getString());}
    }
    private void finish(MinecraftClient c,String error){if(done)return;done=true;try{var o=new JsonObject();o.addProperty("result",error==null?"PASS":"FAIL");o.addProperty("detail",error==null?"Completed":error);o.add("checks",new Gson().toJsonTree(checks));Files.writeString(output,new Gson().toJson(o),StandardCharsets.UTF_8);}catch(Exception ignored){}c.scheduleStop();}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
