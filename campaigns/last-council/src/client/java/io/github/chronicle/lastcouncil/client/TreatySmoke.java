package io.github.chronicle.lastcouncil.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.chronicle.lastcouncil.network.TreatyPayloads;
import io.github.chronicle.lastcouncil.network.TreatyPayloads.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/** Opt-in native clients; fixture marker and loopback only. */
public final class TreatySmoke {
    private final Path root,output,shared;
    private final String role,phase;
    private final List<String> checks=new ArrayList<>();
    private final long started=System.nanoTime();
    private boolean connecting,done;private int step,wait;private long outsiderRevision;private UUID treaty;
    private TreatySmoke(String path){
        root=Path.of(System.getenv("CHRONICLE_TREATY_ROOT")).toAbsolutePath().normalize();output=Path.of(path).toAbsolutePath().normalize();shared=output.getParent();
        role=System.getenv("CHRONICLE_TREATY_ROLE");phase=System.getenv("CHRONICLE_TREATY_PHASE");
        if(root.getParent()==null||!root.getParent().getFileName().toString().equals("test-runs")||!Files.isRegularFile(root.resolve(".council-treaties-fixture"))||output.startsWith(root)||!Set.of("A","B","C","D").contains(role)||!Set.of("initial","restart").contains(phase))throw new IllegalArgumentException("unsafe treaty fixture");
    }
    public static void register(){String path=System.getenv("CHRONICLE_TREATY_SMOKE");if(path!=null)ClientTickEvents.END_CLIENT_TICK.register(new TreatySmoke(path)::tick);}
    private void mark(String key,String value)throws Exception{Files.writeString(shared.resolve(key),value,StandardCharsets.UTF_8);}
    private boolean has(String key){return Files.isRegularFile(shared.resolve(key));}
    private String read(String key)throws Exception{return Files.readString(shared.resolve(key),StandardCharsets.UTF_8).trim();}
    private static UUID offline(String role){return UUID.nameUUIDFromBytes(("OfflinePlayer:ChronicleFabric"+role).getBytes(StandardCharsets.UTF_8));}
    private void cmd(MinecraftClient c,String text){c.player.networkHandler.sendChatCommand(text);}
    private void tick(MinecraftClient c){if(done)return;try{
        if(System.nanoTime()-started>270_000_000_000L)throw new IllegalStateException("timeout "+phase+" "+role+" step "+step);
        if(c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof AccessibilityOnboardingScreen)c.setScreen(new TitleScreen());
        if(!connecting&&c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof TitleScreen){connecting=true;ConnectScreen.connect(c.currentScreen,c,ServerAddress.parse("127.0.0.1:25575"),new ServerInfo("Treaty smoke","127.0.0.1:25575",ServerInfo.ServerType.OTHER),false,null);return;}
        if(c.player==null)return;if(wait>0){wait--;return;}
        if(step==0){mark(phase+"-ready-"+role,"ready");step=1;return;}
        if(step<10){setup(c);return;}
        if(phase.equals("initial")){if(role.equals("A"))initialA(c);else if(role.equals("B"))initialB(c);else if(role.equals("C"))initialC(c);else initialD(c);}
        else restart(c);
    }catch(Throwable failure){finish(c,failure.toString());}}
    private void setup(MinecraftClient c)throws Exception{switch(step){
        case 1->{if(role.equals("A")){if(!has(phase+"-ready-B")||!has(phase+"-ready-C")||!has(phase+"-ready-D"))return;cmd(c,"fill -5 64 -5 5 64 5 minecraft:stone");cmd(c,"setblock 0 65 2 minecraft:lectern");for(String p:List.of("A","B","C","D"))cmd(c,"tp ChronicleFabric"+p+" 0 65 0");wait=35;mark(phase+"-setup","ready");step=2;}else{if(!has(phase+"-setup"))return;if(phase.equals("initial"))cmd(c,"chronicle players join");wait=35;mark(phase+"-joined-"+role,"ready");step=3;}}
        case 2->{if(!has(phase+"-joined-B")||!has(phase+"-joined-C")||!has(phase+"-joined-D"))return;if(phase.equals("initial"))cmd(c,"chronicle session start");wait=40;mark(phase+"-go","ready");step=3;}
        case 3->{if(!has(phase+"-go"))return;open(c);wait=20;step=4;}
        case 4->{if(TreatyClient.currentRoot()==null)return;mark(phase+"-opened-"+role,"ready");step=10;}
        default->throw new IllegalStateException("bad setup step");}}
    private void open(MinecraftClient c){c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(.5,65.5,2),Direction.NORTH,new BlockPos(0,65,2),false));}
    private View view(){return TreatyClient.currentView();}
    private Detail detail(){return view()==null?null:view().detail();}
    private boolean parties(int count){return detail()!=null&&detail().parties().stream().filter(PartyView::signed).count()==count;}
    private void request(Action action,long rev){ClientPlayNetworking.send(new Request(UUID.randomUUID(),action,view().token(),treaty,rev,BlockPos.ORIGIN,"","",false,List.of(),0));}
    private void initialA(MinecraftClient c)throws Exception{switch(step){
        case 10->{if(!has("initial-opened-B")||!has("initial-opened-C")||!has("initial-opened-D")||view().people().size()!=3)return;mark("all-open","ready");step=11;}
        case 11->{if(!has("c-closed")||detail()==null||detail().parties().stream().allMatch(PartyView::present))return;treaty=detail().id();require(parties(2)&&!detail().canSanctify(),"absent party did not block sanctification");request(Action.SIGN,1);request(Action.SANCTIFY,1);wait=30;step=12;}
        case 12->{require(detail().status().equals("Draft")&&parties(2),"absent sanctification changed record");checks.add("absent party blocks Chancellor sanctification; retained signatures remain");mark("absence-tested","ready");step=13;}
        case 13->{if(!has("b-resigned")||!has("c-resigned")||detail()==null||detail().revision()!=2||!parties(2))return;mark("initial-complete","ready");finish(c,null);}
    }}
    private void initialB(MinecraftClient c)throws Exception{switch(step){
        case 10->{if(!has("outsider-baseline"))return;press(c.currentScreen,"+ Create treaty");frame(c.currentScreen);text(c.currentScreen).setText("x".repeat(81));edit(c.currentScreen).setText("First line\nSecond line");press(c.currentScreen,"Public");pressPrefix(c.currentScreen,"Representatives (");frame(c.currentScreen);pressContaining(c.currentScreen,"ChronicleFabricC");press(c.currentScreen,"Done");press(c.currentScreen,"Submit");wait=30;step=35;}
        case 35->{require(c.currentScreen!=TreatyClient.currentRoot(),"refused draft closed and lost input");require(text(c.currentScreen).getText().equals("x".repeat(81))&&edit(c.currentScreen).getText().equals("First line\nSecond line"),"refused draft lost fields");frame(c.currentScreen);ScreenshotRecorder.saveScreenshot(shared.toFile(),"treaty-draft-compact.png",c.getFramebuffer(),1,ignored->{});text(c.currentScreen).setText("Private road pact");press(c.currentScreen,"Submit");checks.add("server-refused draft preserves multiline text, privacy and selected parties for correction");wait=25;step=11;}
        case 11->{if(detail()==null||!detail().title().equals("Private road pact"))return;treaty=detail().id();mark("treaty-id",treaty.toString());require(detail().privateTerms()&&detail().terms().equals("First line\nSecond line"),"private multiline terms changed");c.getWindow().setWindowedSize(1280,720);wait=25;step=12;}
        case 12->{frame(c.currentScreen);ScreenshotRecorder.saveScreenshot(shared.toFile(),"treaty-normal.png",c.getFramebuffer(),1,ignored->{});c.getWindow().setWindowedSize(640,480);wait=25;step=13;}
        case 13->{frame(c.currentScreen);ScreenshotRecorder.saveScreenshot(shared.toFile(),"treaty-compact.png",c.getFramebuffer(),1,ignored->{});press(c.currentScreen,"Sign");frame(c.currentScreen);press(c.currentScreen,"Confirm");wait=20;step=14;}
        case 14->{if(!parties(1))return;mark("b-signed","ready");step=15;}
        case 15->{if(!has("c-returned")||detail()==null||!parties(2)||detail().parties().stream().anyMatch(p->!p.present()))return;press(c.currentScreen,"Edit");edit(c.currentScreen).setText("First line revised\nSecond line");press(c.currentScreen,"Submit");wait=25;step=16;}
        case 16->{if(detail()==null||detail().revision()!=2)return;require(parties(0),"edit did not clear signatures");mark("edited","ready");step=17;}
        case 17->{if(!has("stale-tested"))return;press(c.currentScreen,"Sign");press(c.currentScreen,"Confirm");wait=20;step=18;}
        case 18->{if(detail()==null||!detail().parties().stream().anyMatch(p->p.owner().equals(offline("B"))&&p.signed()))return;mark("b-resigned","ready");step=19;}
        case 19->{if(!has("initial-complete"))return;checks.add("native private multiline draft, revision edit and own signature; compact and normal frames captured");finish(c,null);}
    }}
    private void initialC(MinecraftClient c)throws Exception{switch(step){
        case 10->{if(!has("b-signed")||!has("private-checked")||detail()==null||!detail().canSign())return;press(c.currentScreen,"Sign");press(c.currentScreen,"Confirm");wait=25;step=11;}
        case 11->{if(!parties(2))return;c.currentScreen.close();mark("c-closed","ready");step=12;}
        case 12->{if(!has("absence-tested"))return;open(c);wait=20;step=13;}
        case 13->{if(view()==null||detail()==null||!parties(2))return;mark("c-returned","ready");step=14;}
        case 14->{if(!has("edited")||detail()==null||detail().revision()!=2)return;treaty=UUID.fromString(read("treaty-id"));request(Action.SIGN,1);wait=25;step=15;}
        case 15->{require(detail().revision()==2&&parties(0),"stale signature accepted");mark("stale-tested","ready");step=16;}
        case 16->{if(detail()==null||!detail().canSign())return;press(c.currentScreen,"Sign");press(c.currentScreen,"Confirm");wait=20;step=17;}
        case 17->{if(!detail().parties().stream().anyMatch(p->p.owner().equals(offline("C"))&&p.signed()))return;mark("c-resigned","ready");step=18;}
        case 18->{if(!has("initial-complete"))return;checks.add("individual consent survives reopening and stale revision cannot sign");finish(c,null);}
    }}
    private void initialD(MinecraftClient c)throws Exception{switch(step){
        case 10->{if(!has("all-open"))return;require(view().rows().isEmpty()&&view().detail()==null,"outsider initially sees records");outsiderRevision=view().revision();mark("outsider-baseline","ready");step=11;}
        case 11->{if(!has("b-signed"))return;require(view().revision()==outsiderRevision&&view().rows().isEmpty()&&view().detail()==null,"private creation/signature leaked to outsider");treaty=UUID.fromString(read("treaty-id"));request(Action.STATE,0);request(Action.SIGN,1);wait=25;step=13;}
        case 13->{require(view().revision()==outsiderRevision&&view().rows().isEmpty()&&view().detail()==null,"forged private selection/sign exposed record");mark("private-checked","ready");step=12;}
        case 12->{if(!has("initial-complete"))return;checks.add("private activity and forged private selection/sign do not alter outsider projection or revision");finish(c,null);}
    }}
    private void restart(MinecraftClient c)throws Exception{switch(step){
        case 10->{if(!has("restart-opened-A")||!has("restart-opened-B")||!has("restart-opened-C")||!has("restart-opened-D"))return;if(view().people().size()!=3)return;treaty=UUID.fromString(read("treaty-id"));if(role.equals("D")){require(view().rows().isEmpty()&&view().detail()==null,"private history visible after restart");outsiderRevision=view().revision();mark("restart-outsider-baseline","ready");}else{if(detail()==null||!detail().id().equals(treaty)||detail().revision()!=2||!parties(2))return;require(detail().status().equals("Draft"),"pending draft lost on restart");}mark("restart-checked-"+role,"ready");step=11;}
        case 11->{if(role.equals("A")){if(!has("restart-checked-B")||!has("restart-checked-C")||!has("restart-checked-D"))return;require(detail().canSanctify(),"Chancellor cannot sanctify restored signatures");press(c.currentScreen,"Sanctify");press(c.currentScreen,"Confirm");wait=25;step=12;}else if(role.equals("B")){if(!has("sanctified")||detail()==null||!detail().status().equals("Active"))return;step=20;}else if(role.equals("C")){if(!has("public-created"))return;step=30;}else{if(!has("broken"))return;require(view().revision()==outsiderRevision&&view().rows().isEmpty()&&view().detail()==null,"private sanctification or breach leaked to outsider");mark("restart-private-checked","ready");step=40;}}
        case 12->{if(detail()==null||!detail().status().equals("Active"))return;int count=detail().history().size();request(Action.SANCTIFY,2);wait=25;step=13;outsiderRevision=count;}
        case 13->{require(detail().status().equals("Active")&&detail().history().size()==outsiderRevision,"duplicate sanctification changed history");mark("sanctified","ready");step=30;}
        case 20->{press(c.currentScreen,"Break");press(c.currentScreen,"Confirm");wait=25;step=21;}
        case 21->{if(detail()==null||!detail().status().equals("Broken"))return;mark("broken","ready");step=30;}
        case 40->{if(!has("restart-private-checked"))return;press(c.currentScreen,"+ Create treaty");text(c.currentScreen).setText("Public market pact");edit(c.currentScreen).setText("Open market road");pressPrefix(c.currentScreen,"Representatives (");pressContaining(c.currentScreen,"ChronicleFabricB");press(c.currentScreen,"Done");press(c.currentScreen,"Submit");wait=25;step=41;}
        case 41->{if(view().rows().stream().noneMatch(r->r.title().equals("Public market pact")))return;mark("public-created","ready");step=30;}
        case 30->{if(!has("public-created")||view().rows().stream().noneMatch(r->r.title().equals("Public market pact")))return;if(role.equals("D"))require(view().rows().stream().noneMatch(Row::privateTerms),"private treaty visible to outsider");checks.add("restart preserved signatures; sanctification, breach and public/private projections verified for "+role);mark("restart-finished-"+role,"ready");step=31;}
        case 31->{if(!has("restart-finished-A")||!has("restart-finished-B")||!has("restart-finished-C")||!has("restart-finished-D"))return;finish(c,null);}
        default->throw new IllegalStateException("bad restart step "+step);
    }}
    private static void frame(Screen screen){
        require(screen instanceof CouncilScreen,"not a Council frame");CouncilScreen frame=(CouncilScreen)screen;
        for(var child:screen.children())if(child instanceof ClickableWidget w)require(w.getX()>=frame.left&&w.getY()>=frame.top&&w.getX()+w.getWidth()<=frame.left+frame.panelWidth&&w.getY()+w.getHeight()<=frame.top+frame.panelHeight,"widget outside frame: "+w.getMessage().getString());
    }
    private static TextFieldWidget text(Screen screen){return screen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();}
    private static EditBoxWidget edit(Screen screen){return screen.children().stream().filter(EditBoxWidget.class::isInstance).map(EditBoxWidget.class::cast).findFirst().orElseThrow();}
    private static void press(Screen screen,String label){pressMatching(screen,s->s.equals(label),label);}
    private static void pressPrefix(Screen screen,String label){pressMatching(screen,s->s.startsWith(label),label);}
    private static void pressContaining(Screen screen,String label){pressMatching(screen,s->s.contains(label),label);}
    private static void pressMatching(Screen screen,java.util.function.Predicate<String> match,String label){ButtonWidget button=screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(b->b.active&&match.test(b.getMessage().getString())).findFirst().orElseThrow(()->new IllegalStateException("button missing: "+label));button.onPress(new MouseInput(0,0));}
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    private void finish(MinecraftClient c,String error){if(done)return;done=true;try{JsonObject json=new JsonObject();json.addProperty("result",error==null?"PASS":"FAIL");json.addProperty("detail",error==null?"Completed":error);json.add("checks",new Gson().toJsonTree(checks));Files.writeString(output,new Gson().toJson(json),StandardCharsets.UTF_8);}catch(Exception ignored){}c.scheduleStop();}
}
