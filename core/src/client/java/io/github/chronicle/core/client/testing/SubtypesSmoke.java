package io.github.chronicle.core.client.testing;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.menu.CoreMenuModel.Page;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Drives the real native Region/Location editors in a guarded disposable world. */
public final class SubtypesSmoke {
    private static final String OUTPUT=System.getenv("CHRONICLE_SUBTYPES_SMOKE"), ROOT=System.getenv("CHRONICLE_SUBTYPES_SERVER_DIR"), ADDRESS="127.0.0.1:25575";
    private static SubtypesSmoke instance; private final Path output; private final List<String> checks=new ArrayList<>(), messages=new ArrayList<>();
    private final java.util.Map<String,Integer> consumed=new java.util.HashMap<>(); private final long began=System.nanoTime(); private boolean connecting,done; private int step,wait,stableTicks; private Object captureScreen;
    private SubtypesSmoke(){output=Path.of(OUTPUT).toAbsolutePath().normalize();Path root=Path.of(ROOT).toAbsolutePath().normalize();if(!Files.isRegularFile(root.resolve(".subtypes-fixture"))||output.startsWith(root))throw new IllegalArgumentException("unsafe smoke fixture");}
    public static void register(){if(OUTPUT==null)return;try{if(ROOT==null)throw new IllegalArgumentException("fixture directory required");instance=new SubtypesSmoke();ClientReceiveMessageEvents.GAME.register((m,o)->{if(!o)instance.messages.add(m.getString());});ClientTickEvents.END_CLIENT_TICK.register(instance::tick);}catch(Exception e){ChronicleCore.LOGGER.error("Subtypes smoke setup failed",e);}}
    private void tick(MinecraftClient c){if(done)return;try{if(System.nanoTime()-began>300_000_000_000L)throw new IllegalStateException("timeout step "+step);
        if(c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof AccessibilityOnboardingScreen)c.setScreen(new TitleScreen());
        if(!connecting&&c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof TitleScreen){connecting=true;ConnectScreen.connect(c.currentScreen,c,ServerAddress.parse(ADDRESS),new ServerInfo("Subtype smoke",ADDRESS,ServerInfo.ServerType.OTHER),false,null);return;}
        if(c.player==null)return;c.getToastManager().clear();drive(c);
    }catch(Throwable e){finish(c,false,e.getClass().getSimpleName()+": "+e.getMessage());}}
    private void drive(MinecraftClient c){CoreMenuScreen m=c.currentScreen instanceof CoreMenuScreen value?value:null;
        switch(step){
            case 0 -> {command(c,"prepare");step++;}
            case 1 -> {if(!passed("prepare"))return;c.player.networkHandler.sendChatCommand("chronicles");step++;}
            case 2 -> {if(m==null||m.pending())return;require(!m.triggerForTest("Departures")&&!m.triggerForTest("Area changes"),"legacy destinations still navigable");navigate(m,"Regions");step=100;}
            case 100 -> {if(m==null||m.pending()||m.state().page()!=Page.REGIONS)return;press(m,"Create region\u2026");step++;}
            case 101 -> {if(!simple(c,"RegionCreateScreen"))return;require(c.getWindow().getScaledWidth()==Integer.parseInt(System.getenv("CHRONICLE_MENU_WIDTH"))/2&&c.getWindow().getScaledHeight()==Integer.parseInt(System.getenv("CHRONICLE_MENU_HEIGHT"))/2,"unexpected logical viewport");require(button(c,"Saved states: off").active,"ordinary Region does not default to no states");require(noButton(c,"Start")&&noButton(c,"End"),"ordinary Region showed state selection");text(c,"Unified ordinary");if(!settled(c))return;capture(c,"region-create-ordinary");click(c,"Begin selection");step++;}
            case 102 -> {if(c.currentScreen!=null||!c.player.getMainHandStack().isOf(net.minecraft.item.Items.WOODEN_SHOVEL))return;sendToolAction(c,net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.DROP_ITEM);command(c,"verify_tool");step++;}
            case 103 -> {if(!passed("verify_tool"))return;sendToolAction(c,net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND);command(c,"verify_tool");step++;}
            case 104 -> {if(!passed("verify_tool"))return;int slot=36+c.player.getInventory().getSelectedSlot();c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId,slot,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,c.player);command(c,"verify_tool");step++;}
            case 105 -> {if(!passed("verify_tool"))return;selectFirst(c);step++;}
            case 106 -> {if(!seenNew("Region position 1 set"))return;selectSecond(c);step++;}
            case 107 -> {if(!seenNew("Region position 2 set"))return;c.player.networkHandler.sendChatCommand("chronicle region save");step++;}
            case 108 -> {if(!seenNew("Saved Region 'unified_ordinary'"))return;command(c,"verify_ordinary");step++;}
            case 109 -> {if(!passed("verify_ordinary"))return;checks.add("ordinary Region created by native popup and shovel; drop, offhand swap and quick-move packets left the leased tool in hand with no dropped tool");c.player.networkHandler.sendChatCommand("chronicles");step++;}
            case 110 -> {if(m==null||m.pending())return;navigate(m,"Regions");step++;}
            case 111 -> {if(m==null||m.pending()||m.state().page()!=Page.REGIONS)return;press(m,"Create region\u2026");step=1100;}
            case 1100 -> {if(!simple(c,"RegionCreateScreen"))return;text(c,"Unified cancelled");click(c,"Begin selection");step++;}
            case 1101 -> {if(c.currentScreen!=null||!c.player.getMainHandStack().isOf(net.minecraft.item.Items.WOODEN_SHOVEL))return;c.player.networkHandler.sendChatCommand("chronicle region cancel");step++;}
            case 1102 -> {if(!seenNew("Region selection cancelled"))return;command(c,"verify_cancel");step++;}
            case 1103 -> {if(!passed("verify_cancel"))return;checks.add("cancelled Region selection discarded the draft and returned the shovel");command(c,"cancel_capture");step=11030;}
            case 11030 -> {if(!passed("cancel_capture"))return;checks.add("unified Cancel released its own in-progress capture hold without publishing metadata");command(c,"respawn_tool");step++;}
            case 11031 -> {if(!passed("respawn_tool"))return;checks.add("actual alive respawn retained the Region draft with exactly one current Debug shovel, then cancelled cleanly");c.player.networkHandler.sendChatCommand("chronicles");step=1104;}
            case 1104 -> {if(m==null||m.pending())return;navigate(m,"Regions");step++;}
            case 1105 -> {if(m==null||m.pending()||m.state().page()!=Page.REGIONS)return;press(m,"Create region\u2026");step=112;}
            case 112 -> {if(!simple(c,"RegionCreateScreen")||!settled(c))return;text(c,"Unified stateful");click(c,"Saved states: off");click(c,"End");wait=4;step=1121;}
            case 1121 -> {if(!simple(c,"RegionCreateScreen")||wait-->0)return;capture(c,"region-create-end");click(c,"Begin selection");step=113;}
            case 113 -> {if(c.currentScreen!=null||!c.player.getMainHandStack().isOf(net.minecraft.item.Items.WOODEN_SHOVEL))return;selectFirst(c);step++;}
            case 114 -> {if(!seenNew("Area change position 1 set"))return;selectSecond(c);step++;}
            case 115 -> {if(!seenNew("Area change position 2 set"))return;c.player.networkHandler.sendChatCommand("chronicle region save");step++;}
            case 116 -> {if(!seenNew("Saved Area change 'Unified stateful' with its END state."))return;command(c,"verify_stateful");step++;}
            case 117 -> {if(!passed("verify_stateful"))return;checks.add("same native Region popup and shovel saved an End-first Region with exact selected bounds");c.player.networkHandler.sendChatCommand("chronicles");step++;}
            case 118 -> {if(m==null||m.pending())return;navigate(m,"Regions");step=3;}
            case 3 -> {if(m==null||m.pending()||m.state().page()!=Page.REGIONS)return;m.selectForTest("subtype_boarding");step++;}
            case 4 -> {if(m==null||m.pending()||m.state().detail()==null||!m.state().detail().id().equals("subtype_boarding"))return;
                require(m.state().detail().controls().stream().noneMatch(v->v.action().name().equals("REGION_ASSIGN")||v.action().name().equals("REGION_UNASSIGN")),"reverse Region use controls still present");
                if(!settled(c))return;capture(c,"region-ordinary");press(m,"Choose subtypes");step++;}
            case 5 -> {if(!simple(c,"SiteSubtypeScreen"))return;if(!settled(c))return;capture(c,"region-subtypes");click(c,"Saved states");click(c,"Apply");step++;}
            case 6 -> {if(m==null||m.pending()||m.state().detail()==null||m.state().detail().area()==null)return;press(m,"Preview bounding box");step++;}
            case 7 -> {if(m==null||m.pending())return;require(!simple(c,"AreaStateScreen"),"preview opened a state picker");checks.add("Region subtype enabled and bounding box preview requested before any capture");press(m,"Capture current Region state");step++;}
            case 8 -> {if(!simple(c,"AreaStateScreen"))return;click(c,"Start");step++;}
            case 9 -> {if(!seenNew("Saved current area as START"))return;command(c,"end_scene");step++;}
            case 10 -> {if(!passed("end_scene"))return;step=1010;}
            case 1010 -> {if(!ready(m,"AREA_APPLY_REVIEW")||m.state().detail().area()==null||!m.state().detail().area().startSaved())return;press(m,"Apply saved Region state");step=1011;}
            case 1011 -> {if(!simple(c,"AreaStateScreen"))return;require(button(c,"Start").active&&!button(c,"End").active,"missing End state is not disabled");click(c,"Cancel");checks.add("apply picker disabled uncaptured End while Start was available");step=1012;}
            case 1012 -> {if(!ready(m,"AREA_CAPTURE_OPEN"))return;press(m,"Capture current Region state");step=11;}
            case 11 -> {if(!simple(c,"AreaStateScreen"))return;click(c,"End");step++;}
            case 12 -> {if(!seenNew("Saved current area as END"))return;command(c,"target");step++;}
            case 13 -> {if(!passed("target"))return;step=1013;}
            case 1013 -> {if(!ready(m,"AREA_APPLY_REVIEW"))return;press(m,"Apply saved Region state");step=14;}
            case 14 -> {if(!simple(c,"AreaStateScreen"))return;click(c,"Start");step++;}
            case 15 -> {if(m==null||m.pending())return;press(m,"Confirm: Apply reviewed state");step++;}
            case 16 -> {if(!seenNew("Area change saved:"))return;command(c,"verify_apply");step++;}
            case 17 -> {if(m==null||m.pending()||!passed("verify_apply"))return;checks.add("UI captured Start and End and applied Start with chest contents restored");command(c,"baseline");step=170;}
            case 170 -> {if(m==null||m.pending()||!passed("baseline"))return;press(m,"Edit region");step++;}
            case 171 -> {if(m==null||m.pending())return;c.player.networkHandler.sendChatCommand("chronicle region save");step++;}
            case 172 -> {if(!seenNew("Saved Region 'subtype_boarding'"))return;command(c,"verify_noop");step++;}
            case 173 -> {if(m==null||m.pending()||!passed("verify_noop"))return;checks.add("no-op Region edit and save preserved Region revision and both snapshots");navigate(m,"Locations");step=18;}
            case 18 -> {if(m==null||m.pending()||m.state().page()!=Page.LOCATIONS)return;require(m.triggerForTest("Block sites"),"Block sites filter missing");step++;}
            case 19 -> {if(m==null||m.pending()||!m.state().filter().equals("BLOCK"))return;String id=m.state().entries().stream().filter(e->e.reference().equals("subtype_helm")||e.id().equals("subtype_helm")||e.id().equals("manual:subtype_helm")).findFirst().orElseThrow().id();m.selectForTest(id);step++;}
            case 20 -> {if(m==null||m.pending()||m.state().detail()==null||!m.state().detail().title().contains("subtype_helm"))return;press(m,"Choose subtypes");step++;}
            case 21 -> {if(!simple(c,"SiteSubtypeScreen"))return;click(c,"Departure");click(c,"Apply");step++;}
            case 22 -> {if(simple(c,"DepartureEditorScreen")){step=23;return;}if(m==null||m.pending())return;press(m,"Configure departure");step++;}
            case 23 -> {if(!simple(c,"DepartureEditorScreen"))return;require(noButton(c,"Area:")&&noButton(c,"Block site:"),"separate Area or Block selector present");text(c,"Subtype departure");warnings(c,"First native warning","Final native warning");click(c,"Region:");step++;}
            case 24 -> {if(!simple(c,"DepartureChoiceScreen"))return;require(button(c,">").active,"Region selector did not expose later choices");click(c,">");require(noButton(c,"subtype_boarding")&&!noButton(c,"zzpage_"),"Region paging did not change rows");click(c,"<");click(c,"subtype_boarding");checks.add("boarding Region selector paged forward and back without losing the departure draft");step++;}
            case 25 -> {if(!simple(c,"DepartureEditorScreen"))return;require(textValue(c).equals("Subtype departure"),"draft lost after Region selector");click(c,"End");step=251;}
            case 251 -> {if(!simple(c,"DepartureEditorScreen")||!settled(c))return;capture(c,"departure-setup");click(c,"Save");step=26;}
            case 26 -> {if(m==null||m.pending())return;command(c,"verify");step++;}
            case 27 -> {if(m==null||m.pending()||!passed("verify"))return;checks.add("Location departure popup saved exact helm, boarding Region and its End state while retaining Building Mode");press(m,"Configure departure");step++;}
            case 28 -> {if(!simple(c,"DepartureEditorScreen"))return;text(c,"Edited subtype departure");click(c,"Save");step++;}
            case 29 -> {if(m==null||m.pending())return;command(c,"verify_edit");step++;}
            case 30 -> {if(m==null||m.pending()||!passed("verify_edit"))return;press(m,"Configure departure");step++;}
            case 31 -> {if(!simple(c,"DepartureEditorScreen"))return;text(c,"Cancelled edit");click(c,"Region:");step++;}
            case 32 -> {if(!simple(c,"DepartureChoiceScreen"))return;click(c,"subtype_other");step++;}
            case 33 -> {if(!simple(c,"DepartureEditorScreen"))return;require(!button(c,"Save").active,"mismatched state allowed Save");if(!settled(c))return;capture(c,"departure-unresolved");click(c,"Leave unchanged");require(button(c,"Save").active,"explicit clear did not repair state");click(c,"Cancel");step++;}
            case 34 -> {if(m==null||m.pending())return;command(c,"verify_edit");step++;}
            case 35 -> {if(m==null||m.pending()||!passed("verify_edit"))return;checks.add("existing Save advanced exactly once; mismatched state blocked Save until explicit clear; Cancel retained prior definition");command(c,"verify_bounds_guard");step=345;}
            case 345 -> {if(m==null||m.pending()||!passed("verify_bounds_guard"))return;checks.add("unused departure pin refused Region bounds changes without mutation");press(m,"Choose subtypes");step++;}
            case 346 -> {if(!simple(c,"SiteSubtypeScreen"))return;click(c,"Departure");click(c,"Apply");step++;}
            case 347 -> {if(m==null||m.pending()||m.state().detail()==null||m.state().detail().departure()!=null)return;command(c,"verify_departure_removed");step++;}
            case 348 -> {if(m==null||m.pending()||!passed("verify_departure_removed"))return;checks.add("unused Departure removed through Location subtype picker");navigate(m,"Regions");step=350;}
            case 350 -> {if(m==null||m.pending()||m.state().page()!=Page.REGIONS)return;m.selectForTest("subtype_boarding");step++;}
            case 351 -> {if(m==null||m.pending()||m.state().detail()==null||!m.state().detail().id().equals("subtype_boarding"))return;command(c,"change_bounds");step++;}
            case 352 -> {if(!passed("change_bounds"))return;command(c,"verify_changed");step++;}
            case 353 -> {if(!passed("verify_changed"))return;step++;}
            case 354 -> {if(m==null||m.pending()||m.state().detail()==null||m.state().detail().area()==null||!m.state().detail().area().stale())return;press(m,"Capture current Region state");step++;}
            case 355 -> {if(!simple(c,"AreaStateScreen"))return;click(c,"Start");step++;}
            case 356 -> {if(m==null||m.pending())return;press(m,"Confirm: Recapture changed Region");step++;}
            case 357 -> {if(!seenNew("Saved current area as START"))return;command(c,"verify_recapture");step++;}
            case 358 -> {if(m==null||m.pending()||!passed("verify_recapture"))return;step++;}
            case 359 -> {if(!ready(m,"AREA_CAPTURE_OPEN")||m.state().detail().area()==null||m.state().detail().area().endSaved())return;press(m,"Capture current Region state");step++;}
            case 360 -> {if(!simple(c,"AreaStateScreen"))return;click(c,"End");step++;}
            case 361 -> {if(!seenNew("Saved current area as END"))return;command(c,"verify_both");step++;}
            case 362 -> {if(!passed("verify_both"))return;checks.add("command bounds review kept metadata untouched, rejected a changed proposal, and required fresh confirmation; reviewed recapture kept Area ID and invalidated the opposite state");step=3621;}
            case 3621 -> {if(!ready(m,"AREA_DELETE_REVIEW"))return;press(m,"Stop editing");step=3622;}
            case 3622 -> {if(m==null||m.pending()||m.state().editing())return;require(!ready(m,"AREA_CAPTURE_OPEN")&&!ready(m,"AREA_APPLY_REVIEW"),"state controls enabled without Editing");command(c,"verify_capture_refused");step=3623;}
            case 3623 -> {if(m==null||m.pending()||!passed("verify_capture_refused"))return;press(m,"Enable editing");step=3624;}
            case 3624 -> {if(!ready(m,"AREA_DELETE_REVIEW"))return;checks.add("server-synced Editing gates UI and shared capture boundary without mutation");command(c,"prepare_menu_bounds");step=3625;}
            case 3625 -> {if(!passed("prepare_menu_bounds"))return;step=3626;}
            case 3626 -> {if(m==null||m.pending()||m.state().detail()==null||m.state().detail().region()==null)return;press(m,"Save region changes");step=3627;}
            case 3627 -> {if(m==null||m.pending()||!m.visibleWidgetBoundsForTest().stream().anyMatch(b->b.label().equals("Confirm: Save changed Region bounds")))return;command(c,"verify_menu_bounds_unpublished");step=3628;}
            case 3628 -> {if(m==null||m.pending()||!passed("verify_menu_bounds_unpublished"))return;press(m,"Confirm: Save changed Region bounds");step=3629;}
            case 3629 -> {if(m==null||m.pending()||m.state().detail()==null||m.state().detail().region()!=null)return;command(c,"verify_menu_bounds_saved");step=3630;}
            case 3630 -> {if(m==null||m.pending()||!passed("verify_menu_bounds_saved"))return;checks.add("native menu warned before committing changed bounds and final confirmation published one revised Region");press(m,"Choose subtypes");step=363;}
            case 363 -> {if(!simple(c,"SiteSubtypeScreen"))return;click(c,"Saved states");click(c,"Apply");step++;}
            case 364 -> {if(!simple(c,"AreaStateScreen"))return;click(c,"Leave unchanged");step++;}
            case 365 -> {if(m==null||m.pending())return;press(m,"Confirm: Remove Saved states");step++;}
            case 366 -> {if(m==null||m.pending()||m.state().detail()==null||m.state().detail().area()!=null)return;command(c,"verify_states_removed");step++;}
            case 367 -> {if(!passed("verify_states_removed"))return;checks.add("Saved states subtype removal retained the Core Region and durable deletion history");finish(c,true,"native subtype flows passed");}
        }
    }
    private static void sendToolAction(MinecraftClient c,net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action action){c.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(action,net.minecraft.util.math.BlockPos.ORIGIN,net.minecraft.util.math.Direction.DOWN));}
    private static void selectFirst(MinecraftClient c){c.interactionManager.attackBlock(new net.minecraft.util.math.BlockPos(2,63,2),net.minecraft.util.math.Direction.UP);}
    private static void selectSecond(MinecraftClient c){var p=new net.minecraft.util.math.BlockPos(3,63,3);c.interactionManager.interactBlock(c.player,net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(p),net.minecraft.util.math.Direction.UP,p,false));}
    private boolean seenNew(String part){int count=(int)messages.stream().filter(m->m.contains(part)).count();String key="message:"+part;int previous=consumed.getOrDefault(key,0);if(count<=previous)return false;consumed.put(key,previous+1);return true;}
    private static void navigate(CoreMenuScreen m,String label){if(m.triggerForTest(label))return;for(int i=0;i<12;i++){if(!m.triggerForTest("v"))break;if(m.triggerForTest(label))return;}throw new IllegalStateException("navigation missing: "+label);}
    private static boolean ready(CoreMenuScreen m,String action){return m!=null&&!m.pending()&&m.state().detail()!=null&&m.state().detail().controls().stream().anyMatch(control->control.action().name().equals(action)&&control.enabled());}
    private static void press(CoreMenuScreen m,String label){if(m.triggerForTest(label))return;for(int i=0;i<25;i++){m.scrollDetailForTest(1);if(m.triggerForTest(label))return;}for(int i=0;i<30;i++){m.scrollDetailForTest(-1);if(m.triggerForTest(label))return;}throw new IllegalStateException("menu button missing or disabled: "+label+"; controls="+m.state().detail().controls()+"; widgets="+m.visibleWidgetBoundsForTest());}
    private static boolean simple(MinecraftClient c,String name){return c.currentScreen!=null&&c.currentScreen.getClass().getSimpleName().equals(name);}
    private static ButtonWidget button(MinecraftClient c,String part){return c.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(b->b.getMessage().getString().contains(part)).findFirst().orElseThrow(()->new IllegalStateException("button missing: "+part));}
    private static boolean noButton(MinecraftClient c,String part){return c.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).noneMatch(b->b.getMessage().getString().contains(part));}
    private static void click(MinecraftClient c,String part){ButtonWidget b=button(c,part);require(b.active&&b.visible,"button disabled: "+part);b.onPress(new net.minecraft.client.input.MouseInput(0,0));}
    private static TextFieldWidget field(MinecraftClient c){return c.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();}
    private static void text(MinecraftClient c,String value){field(c).setText(value);}private static String textValue(MinecraftClient c){return field(c).getText();}
    private static void warnings(MinecraftClient c,String a,String b){var fields=c.currentScreen.children().stream().filter(EditBoxWidget.class::isInstance).map(EditBoxWidget.class::cast).toList();require(fields.size()==2,"warning fields missing");fields.get(0).setText(a);fields.get(1).setText(b);}
    private static void command(MinecraftClient c,String action){c.player.networkHandler.sendChatCommand("chronicle_subtypes_trial "+action);}
    private boolean passed(String action){int count=0;for(String message:messages){if(message.contains("Subtypes trial FAIL "+action))throw new IllegalStateException(message);if(message.contains("Subtypes trial PASS "+action))count++;}int previous=consumed.getOrDefault(action,0);if(count<=previous)return false;consumed.put(action,previous+1);return true;}
    private boolean settled(MinecraftClient c){if(captureScreen!=c.currentScreen){captureScreen=c.currentScreen;stableTicks=0;return false;}return ++stableTicks>=4;}
    private void capture(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(output.getParent().toFile(),"subtypes-"+name+"-"+c.getWindow().getScaledWidth()+"x"+c.getWindow().getScaledHeight()+".png",c.getFramebuffer(),1,t->{});}
    private void finish(MinecraftClient c,boolean pass,String detail){if(done)return;done=true;try{Files.createDirectories(output.getParent());JsonObject result=new JsonObject();result.addProperty("result",pass?"PASS":"FAIL");result.addProperty("detail",detail);JsonArray a=new JsonArray();checks.forEach(a::add);result.add("checks",a);Files.writeString(output,new Gson().toJson(result));}catch(Exception ignored){}c.scheduleStop();}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
