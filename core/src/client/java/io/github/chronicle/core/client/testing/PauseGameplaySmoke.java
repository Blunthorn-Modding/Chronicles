package io.github.chronicle.core.client.testing;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ChronicleCore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Matching Fabric client for the isolated paused-gameplay server trial. */
public final class PauseGameplaySmoke {
    private static final String OUTPUT_ENV=System.getenv("CHRONICLE_PAUSE_SMOKE");
    private static final String CONTROL_ENV=System.getenv("CHRONICLE_PAUSE_CONTROL");
    private static final String ADDRESS="127.0.0.1:25575";
    private static PauseGameplaySmoke instance;
    private final Path output,control; private String stage="",running=""; private long started; private Vec3d movementStart; private boolean connected;
    private int activeBannerCount,stableActiveBannerCount=-1,activeBannerBaseline;
    private int buildingEffectDuration;
    private PauseGameplaySmoke(Path output,Path control) throws IOException { this.output=output.toAbsolutePath().normalize();this.control=control.toAbsolutePath().normalize();Files.createDirectories(this.output.getParent()); }
    public static void register() { if(OUTPUT_ENV==null)return;try{instance=new PauseGameplaySmoke(Path.of(OUTPUT_ENV),Path.of(CONTROL_ENV));ClientReceiveMessageEvents.GAME.register((message,overlay)->{if(overlay&&message.getString().equals("Chronicle | Play is active"))instance.activeBannerCount++;});ClientTickEvents.END_CLIENT_TICK.register(instance::tick);}catch(Exception e){ChronicleCore.LOGGER.error("Pause gameplay smoke setup failed",e);}}
    private void tick(MinecraftClient client) {
        try {
            if(client.player==null){if(client.currentScreen instanceof TitleScreen)ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(ADDRESS),new ServerInfo("Chronicle pause smoke",ADDRESS,ServerInfo.ServerType.OTHER),false,null);return;}
            if(!connected){connected=true;write("CONNECTED","","Fabric client joined loopback fixture");}
            String next=new Gson().fromJson(Files.readString(control),JsonObject.class).get("stage").getAsString();
            if(next.equals("stop")){write("PASS",next,"done");client.scheduleStop();return;}
            if(!next.equals(stage)){client.options.useKey.setPressed(false);client.options.forwardKey.setPressed(false);stage=next;running="";started=System.nanoTime();start(client);}
            advance(client);
        }catch(Throwable error){write("FAIL",stage,error.getClass().getSimpleName()+": "+error.getMessage());client.scheduleStop();}
    }
    private void start(MinecraftClient c){
        if(stage.equals("chest_open_active")||stage.equals("chest_paused_open")||stage.equals("chest_building")){c.player.getInventory().setSelectedSlot(2);place(c,new BlockPos(0,65,2));running="chest";}
        if(stage.equals("paused")){movementStart=position(c);c.options.forwardKey.setPressed(true);running="move";}
        else if(stage.equals("author_only")||stage.equals("building_off")){if(stage.equals("building_off"))activeBannerBaseline=activeBannerCount;c.player.getInventory().setSelectedSlot(0);place(c,stage.equals("author_only")?new BlockPos(4,64,0):new BlockPos(-2,64,0));running="wait";}
        else if(stage.equals("building")){buildingEffectDuration=c.player.getStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS).getDuration();c.player.getInventory().setSelectedSlot(0);place(c,new BlockPos(4,64,0));running="place";}
        else if(stage.equals("active")){stableActiveBannerCount=-1;c.player.getInventory().setSelectedSlot(0);place(c,new BlockPos(1,64,0));running="place";}
        else if(stage.equals("inflight")){eat(c);running="begin";}
    }
    private void advance(MinecraftClient c){
        long elapsed=(System.nanoTime()-started)/1_000_000L;
        if(stage.equals("chest_open_active")&&running.equals("chest")&&elapsed>750){
            if(!(c.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>))throw new IllegalStateException("ACTIVE chest did not open");
            ready(c,"real ACTIVE chest opened");
        }
        if(stage.equals("chest_building")&&running.equals("chest")&&elapsed>750){
            if(!(c.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>))throw new IllegalStateException("Building Mode chest did not open");
            c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId,0,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,c.player);
            running="transferred";
        } else if(stage.equals("chest_building")&&running.equals("transferred")&&elapsed>1200)ready(c,"Building Mode chest transfer through normal slot packet");
        if((stage.equals("chest_pause_closed")||stage.equals("chest_paused_open")||stage.equals("chest_disabled_closed"))&&!running.equals("done")&&elapsed>750){
            if(c.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>)throw new IllegalStateException("paused chest remains visible: "+stage);
            ready(c,"paused chest closed/rejected");
        }
        if(stage.equals("inflight")&&running.equals("begin")&&c.player.isUsingItem()) {running="using";write("USING",stage,"active food use began");}
        if(stage.equals("paused")){
            if(running.equals("move")&&elapsed>900){c.options.forwardKey.setPressed(false);if(Math.hypot(c.player.getX()-movementStart.x,c.player.getZ()-movementStart.z)<.08)throw new IllegalStateException("paused player did not move horizontally");c.player.getInventory().setSelectedSlot(0);place(c,new BlockPos(1,64,0));attack(c,new BlockPos(2,65,0));eat(c);running="food";}
            else if(running.equals("food")&&elapsed>3400)ready(c,"paused movement/place/break/food packets sent");
        } else if(stage.equals("author_only")||stage.equals("building_off")){if(running.equals("wait")&&elapsed>500)ready(c,"paused placement packet sent");}
        else if(stage.equals("building")){
            if(running.equals("place")&&elapsed>500){attack(c,new BlockPos(5,65,0));running="mine";}
            else if(running.equals("mine")){c.interactionManager.updateBlockBreakingProgress(new BlockPos(5,65,0),Direction.UP);if(elapsed>1700){c.interactionManager.cancelBlockBreaking();eat(c);running="food";}}
            else if(running.equals("food")&&elapsed>4000)ready(c,"global Building Mode real-player placement, mining and food-use packets sent");
        }
        else if(stage.equals("active")){
            if(elapsed>=1000&&stableActiveBannerCount<0)stableActiveBannerCount=activeBannerCount;
            if(running.equals("place")&&elapsed>500){attack(c,new BlockPos(2,65,0));running="mine";}
            else if(running.equals("mine")){c.interactionManager.updateBlockBreakingProgress(new BlockPos(2,65,0),Direction.UP);if(elapsed>1700){c.interactionManager.cancelBlockBreaking();eat(c);running="food";}}
            else if(running.equals("food")&&elapsed>4000)ready(c,"active placement/break/food packets sent");
        }
    }
    private void ready(MinecraftClient client,String detail){
        client.options.useKey.setPressed(false);
        if(stage.equals("paused")){
            if(client.player.getInventory().getStack(0).getCount()!=8 || client.player.getInventory().getStack(1).getCount()!=3)
                throw new IllegalStateException("rejected action left ghost inventory contents");
            if(!client.world.getBlockState(new BlockPos(1,65,0)).isAir())throw new IllegalStateException("rejected placement left ghost block");
        }
        if(stage.equals("active")&&(activeBannerCount-activeBannerBaseline!=1||activeBannerCount!=stableActiveBannerCount))
            throw new IllegalStateException("ACTIVE actionbar missing or periodically repeated");
        if(stage.equals("building")){
            if(client.world.getTickManager().isFrozen())throw new IllegalStateException("client world still frozen in Building Mode");
            if(!io.github.chronicle.core.server.SessionGameplay.frozen(client.player)||client.player.getStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS).getDuration()!=buildingEffectDuration)
                throw new IllegalStateException("client survival snapshot did not hold effect timers during live Building Mode");
        }
        running="done";write("READY",stage,detail);
    }
    private static void eat(MinecraftClient c){c.player.getInventory().setSelectedSlot(1);c.player.setPitch(-65);c.options.useKey.setPressed(true);c.interactionManager.interactItem(c.player,Hand.MAIN_HAND);}
    private static void place(MinecraftClient c,BlockPos base){if(c.interactionManager==null)throw new IllegalStateException("client interaction manager unavailable");c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(base).add(0,.5,0),Direction.UP,base,false));}
    private static void attack(MinecraftClient c,BlockPos pos){if(c.interactionManager==null)throw new IllegalStateException("client interaction manager unavailable");c.player.getInventory().setSelectedSlot(2);c.interactionManager.attackBlock(pos,Direction.UP);}
    private static Vec3d position(MinecraftClient client){return new Vec3d(client.player.getX(),client.player.getY(),client.player.getZ());}
    private void write(String result,String stage,String detail){try{JsonObject value=new JsonObject();value.addProperty("result",result);value.addProperty("stage",stage);value.addProperty("detail",detail);Files.writeString(output,new Gson().toJson(value)+"\n",StandardCharsets.UTF_8);}catch(IOException ignored){}}
}
