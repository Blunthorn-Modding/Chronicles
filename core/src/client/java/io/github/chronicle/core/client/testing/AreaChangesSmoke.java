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
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Matching-client driver for the disposable CF21 Area changes fixture. */
public final class AreaChangesSmoke {
    private static final String OUTPUT=System.getenv("CHRONICLE_AREAS_SMOKE"),ROOT=System.getenv("CHRONICLE_AREAS_SERVER_DIR"),PHASE=System.getenv("CHRONICLE_AREAS_PHASE"),ADDRESS="127.0.0.1:25575";
    private static final BlockPos FIRST=new BlockPos(4,64,4),SECOND=new BlockPos(5,64,4);
    private static AreaChangesSmoke instance;
    private final Path output,root;private final List<String>checks=new ArrayList<>(),messages=new ArrayList<>();private boolean connecting,done;private long began=System.nanoTime();private int step,wait;private long lastPreviewFrames;
    private AreaChangesSmoke(){output=Path.of(OUTPUT).toAbsolutePath().normalize();root=Path.of(ROOT).toAbsolutePath().normalize();if(!Files.isRegularFile(root.resolve(".area-changes-fixture")))throw new IllegalArgumentException("refusing Area smoke outside disposable fixture");if(output.startsWith(root))throw new IllegalArgumentException("Area evidence must be outside fixture");if(!List.of("initial","restart").contains(PHASE))throw new IllegalArgumentException("CHRONICLE_AREAS_PHASE must be initial or restart");}
    public static void register(){if(OUTPUT==null)return;try{if(ROOT==null||PHASE==null)throw new IllegalArgumentException("Area smoke fixture/phase is required");instance=new AreaChangesSmoke();ClientReceiveMessageEvents.GAME.register((message,overlay)->{if(!overlay)instance.messages.add(message.getString());});ClientTickEvents.END_CLIENT_TICK.register(instance::tick);}catch(Exception error){ChronicleCore.LOGGER.error("Area changes smoke setup failed",error);}}

    private void tick(MinecraftClient client){if(done)return;try{
        if(System.nanoTime()-began>240_000_000_000L)throw new IllegalStateException("Area smoke timeout step "+step);
        if(client.player==null&&client.getOverlay()==null&&client.currentScreen instanceof AccessibilityOnboardingScreen)client.setScreen(new TitleScreen());
        if(!connecting&&client.player==null&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen){connecting=true;ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(ADDRESS),new ServerInfo("Area smoke",ADDRESS,ServerInfo.ServerType.OTHER),false,null);return;}
        if(client.player==null)return;client.getToastManager().clear();if(PHASE.equals("restart")){restart(client);return;}initial(client);
    }catch(Throwable error){finish(client,false,error.getClass().getSimpleName()+": "+error.getMessage());}}

    private void initial(MinecraftClient client){
        if(step==0){command(client,"chronicle_areas_trial prepare");step++;return;}
        if(step==1){if(!passed("prepare"))return;command(client,"chronicles area change begin start Smoke");step++;return;}
        if(step==2){if(!seen("Area change 'Smoke' started"))return;command(client,"chronicle_areas_trial remember_tool");step++;return;}
        if(step==3){if(!passed("remember_tool"))return;require(client.interactionManager.attackBlock(FIRST,Direction.UP),"left-click selection packet was refused client-side");wait=4;step++;return;}
        if(step==4){if(wait-->0)return;var hit=new BlockHitResult(Vec3d.ofCenter(SECOND),Direction.UP,SECOND,false);client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,hit);wait=4;step++;return;}
        if(step==5){if(wait-->0)return;requirePreview(4,6);client.player.setPitch(40);capture(client,"selection-initial");var third=new BlockPos(7,64,4);client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(third),Direction.UP,third,false));wait=8;step=501;return;}
        if(step==501){if(wait-->0)return;requirePreview(4,8);capture(client,"selection-extended");require(client.interactionManager.attackBlock(SECOND,Direction.UP),"changed left selection refused");wait=8;step=502;return;}
        if(step==502){if(wait-->0)return;requirePreview(5,8);capture(client,"selection-first-moved");require(client.interactionManager.attackBlock(FIRST,Direction.UP),"restored left selection refused");wait=8;step=503;return;}
        if(step==503){if(wait-->0)return;requirePreview(4,8);checks.add("client preview payload and continuing render frames followed repeated right and left Debug shovel changes before saving");command(client,"chronicle_areas_trial invalid_corner");step=504;return;}
        if(step==504){if(!passed("invalid_corner"))return;requirePreview(4,8);checks.add("oversized corner refusal retained the prior valid draft and private selection/outline");command(client,"chronicles author region pos2");wait=4;step=6;return;}
        if(step==6){if(wait-->0)return;command(client,"chronicles area change save");step++;return;}
        if(step==7){if(count("Saved Area change 'Smoke' with its START state.")<1)return;checks.add("debug shovel left/right packets and the compatible pos2 command created Start");command(client,"chronicle_areas_trial make_end");step++;return;}
        if(step==8){if(!passed("make_end"))return;command(client,"chronicles area change capture area_smoke end 1");step++;return;}
        if(step==9){if(count("Saved current area as END for 'Smoke'.")<1)return;command(client,"chronicle_areas_trial target_start");step++;return;}
        if(step==10){if(!passed("target_start"))return;wait=35;step++;return;}
        if(step==11){if(wait-->0)return;command(client,"chronicle_areas_trial verify_start");step++;return;}
        if(step==12){if(!passed("verify_start"))return;checks.add("Start apply restored exact ordinary-book container and sign NBT");command(client,"chronicle_areas_trial target_end");step++;return;}
        if(step==13){if(!passed("target_end"))return;wait=35;step++;return;}
        if(step==14){if(wait-->0)return;command(client,"chronicle_areas_trial verify_end");step++;return;}
        if(step==15){if(!passed("verify_end"))return;checks.add("End apply discarded replaced chest contents without drops and retired only its binding");command(client,"chronicle_areas_trial make_start2");step++;return;}
        if(step==16){if(!passed("make_start2"))return;command(client,"chronicles area change capture area_smoke start 2");step++;return;}
        if(step==17){if(count("Saved current area as START for 'Smoke'.")<1)return;command(client,"chronicle_areas_trial target_start2");step++;return;}
        if(step==18){if(!passed("target_start2"))return;wait=35;step++;return;}
        if(step==19){if(wait-->0)return;command(client,"chronicle_areas_trial verify_start2");step++;return;}
        if(step==20){if(!passed("verify_start2"))return;checks.add("recaptured Start replaced the immutable reference and applied the new state");command(client,"chronicle_areas_trial player_refusal");step++;return;}
        if(step==21){if(!passed("player_refusal"))return;checks.add("non-spectator inside the Area refused apply before durable evidence or writes");command(client,"chronicle_areas_trial unloaded");step++;return;}
        if(step==22){if(!passed("unloaded"))return;checks.add("unloaded capture refused without forcing its chunk");command(client,"chronicle_areas_trial lore_begin");step++;return;}
        if(step==23){if(!passed("lore_begin"))return;wait=20;step++;return;}
        if(step==24){if(wait-->0)return;require(seen("Managed Chronicle lore provenance"),"managed lore capture did not report refusal");command(client,"chronicle_areas_trial lore_verify");step++;return;}
        if(step==25){if(!passed("lore_verify"))return;checks.add("ordinary book prose was captured while authentic managed lore was rejected unchanged");command(client,"chronicle_areas_trial seed_cancel");step++;return;}
        if(step==26){if(!passed("seed_cancel"))return;command(client,"chronicles area change begin start Cancelled");step++;return;}
        if(step==27){if(!seen("Area change 'Cancelled' started"))return;command(client,"chronicle_areas_trial remember_tool");step++;return;}
        if(step==28){if(!passed("remember_tool"))return;client.interactionManager.attackBlock(FIRST,Direction.UP);wait=4;step++;return;}
        if(step==29){if(wait-->0)return;command(client,"chronicles area change cancel");wait=4;step++;return;}
        if(step==30){if(wait-->0)return;command(client,"chronicle_areas_trial restore_stale");step++;return;}
        if(step==31){if(!passed("restore_stale"))return;client.interactionManager.attackBlock(SECOND,Direction.UP);wait=4;step++;return;}
        if(step==32){if(wait-->0)return;command(client,"chronicle_areas_trial verify_cancel");step++;return;}
        if(step==33){if(!passed("verify_cancel"))return;checks.add("cancel restored private selection and the stale shovel packet stayed inert");command(client,"chronicle_areas_trial quota_start");step++;return;}
        if(step==34){if(!passed("quota_start")||count("Saved Area change 'Managed quota' with its START state.")<1)return;command(client,"chronicle_areas_trial quota_end");step++;return;}
        if(step==35){if(!passed("quota_end")||count("Saved current area as END for 'Managed quota'.")<1)return;command(client,"chronicle_areas_trial quota_refuse");step++;return;}
        if(step==36){if(!passed("quota_refuse"))return;wait=35;step++;return;}
        if(step==37){if(wait-->0)return;require(seen("replacement exceeds its limit"),"quota refusal was not privately explained");command(client,"chronicle_areas_trial quota_verify");step++;return;}
        if(step==38){if(!passed("quota_verify"))return;checks.add("managed-block quota refusal left terrain, ledger and operation journal unchanged");command(client,"chronicles");step++;return;}
        CoreMenuScreen menu=client.currentScreen instanceof CoreMenuScreen value?value:null;
        if(step==39){if(menu==null||menu.pending())return;require(menu.triggerForTest("Regions"),"Regions navigation missing");step++;return;}
        if(step==40){if(menu==null||menu.pending()||menu.state().page()!=Page.REGIONS)return;wait=4;step++;return;}
        if(step==41){if(wait-->0)return;require(client.getWindow().getScaledWidth()==640&&client.getWindow().getScaledHeight()==360,"initial logical viewport is not 640x360");verifyMenu(menu);capture(client,"area-menu");checks.add("640x360 native Regions menu captured after stable frames");finish(client,true,"initial Area capture/apply/refusal checks passed");}
    }

    private void restart(MinecraftClient client){
        if(step==0){command(client,"chronicle_areas_trial restart");step=100;return;}
        if(step==100){if(!passed("restart"))return;checks.add("clean restart retained paired snapshots and quota-refused terrain without unresolved operations");command(client,"chronicles");step++;return;}
        CoreMenuScreen menu=client.currentScreen instanceof CoreMenuScreen value?value:null;
        if(step==101){if(menu==null||menu.pending())return;require(menu.triggerForTest("Regions"),"restart Regions navigation missing");step++;return;}
        if(step==102){if(menu==null||menu.pending()||menu.state().page()!=Page.REGIONS)return;wait=4;step++;return;}
        if(step==103){if(wait-->0)return;require(client.getWindow().getScaledWidth()==480&&client.getWindow().getScaledHeight()==480,"restart logical viewport is not 480x480");verifyMenu(menu);capture(client,"area-menu");checks.add("480x480 restart Regions menu captured after stable frames");client.setScreen(null);command(client,"chronicle_areas_trial delete_main");step++;return;}
        if(step==104){if(!passed("delete_main"))return;wait=35;step++;return;}
        if(step==105){if(wait-->0)return;command(client,"chronicle_areas_trial verify_delete");step++;return;}
        if(step==106){if(!passed("verify_delete"))return;checks.add("delete applied the chosen state, tombstoned the Area, retained the surviving binding and emitted no drops");command(client,"chronicle_areas_trial delete_quota");step++;return;}
        if(step==107){if(!passed("delete_quota"))return;checks.add("leave-unchanged deletion retired the quota Area without terrain access");finish(client,true,"restart, deletion and narrow Regions menu passed");}
    }

    private void requirePreview(double minX,double maxX){
        try{var field=io.github.chronicle.core.client.AuthoringPreviewRenderer.class.getDeclaredField("preview");field.setAccessible(true);var box=(io.github.chronicle.core.network.AuthoringPreviewPayload)field.get(null);require(box!=null&&box.visible()&&box.minX()==minX&&box.maxX()==maxX,"displayed bounding box did not follow the latest shovel corners; expected "+minX+" to "+maxX+", got "+box);
        require(io.github.chronicle.core.client.AuthoringPreviewRenderer.renderedFrames()>lastPreviewFrames,"outline renderer stopped drawing between corner changes");lastPreviewFrames=io.github.chronicle.core.client.AuthoringPreviewRenderer.renderedFrames();}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    private static void verifyMenu(CoreMenuScreen menu){var widgets=menu.visibleWidgetBoundsForTest();var create=widgets.stream().filter(value->value.label().equals("Create region\u2026")).findFirst().orElseThrow(()->new IllegalStateException("Create region button missing"));require(menu.state().entries().size()>=2,"saved Regions are missing from menu");var first=widgets.stream().filter(value->menu.state().entries().stream().anyMatch(entry->entry.label().equals(value.label()))).findFirst().orElseThrow(()->new IllegalStateException("Region list entry is not visible"));require(create.y()<first.y(),"Create region is not above the list");}
    private boolean passed(String verb){for(String value:messages){if(value.contains("Areas trial FAIL "+verb))throw new IllegalStateException(value);if(value.contains("Areas trial PASS "+verb))return true;}return false;}
    private boolean seen(String text){return messages.stream().anyMatch(value->value.contains(text));}
    private int count(String text){return(int)messages.stream().filter(value->value.contains(text)).count();}
    private static void command(MinecraftClient client,String command){client.player.networkHandler.sendChatCommand(command);}
    private void capture(MinecraftClient client,String kind){ScreenshotRecorder.saveScreenshot(output.getParent().toFile(),"areas-"+PHASE+"-"+kind+"-"+client.getWindow().getScaledWidth()+"x"+client.getWindow().getScaledHeight()+".png",client.getFramebuffer(),1,text->{});}
    private void finish(MinecraftClient client,boolean pass,String detail){if(done)return;done=true;try{Files.createDirectories(output.getParent());JsonObject value=new JsonObject();value.addProperty("result",pass?"PASS":"FAIL");value.addProperty("phase",PHASE);value.addProperty("detail",detail);JsonArray array=new JsonArray();checks.forEach(array::add);value.add("checks",array);Files.writeString(output,new Gson().toJson(value));}catch(Exception ignored){}client.scheduleStop();}
    private static void require(boolean condition,String text){if(!condition)throw new IllegalStateException(text);}
}
