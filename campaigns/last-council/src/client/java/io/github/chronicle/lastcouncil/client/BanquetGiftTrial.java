package io.github.chronicle.lastcouncil.client;

import com.google.gson.JsonParser;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.lastcouncil.network.BanquetPayloads.View;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/** Explicitly started, fixture-only native test of uncertain gift reconnect. */
public final class BanquetGiftTrial {
    private static BanquetGiftTrial trial;
    private final Path checkpoint, ledger;
    private int step, ticks;
    private UUID request, world;
    private int supplies;
    private boolean done;
    private BanquetGiftTrial() {
        String rootValue=System.getenv("CHRONICLE_BANQUET_ROOT"), outputValue=System.getenv("CHRONICLE_BANQUET_SMOKE");
        if(rootValue==null || outputValue==null || !"A".equals(System.getenv("CHRONICLE_BANQUET_ROLE")))throw new IllegalStateException("Banquet fixture not enabled for A");
        Path root=Path.of(rootValue).toAbsolutePath().normalize(), output=Path.of(outputValue).toAbsolutePath().normalize();
        if(root.getParent()==null || !root.getParent().getFileName().toString().equals("test-runs") || !Files.isRegularFile(root.resolve(".council-banquet-fixture")) || output.startsWith(root) || output.getParent()==null)throw new IllegalStateException("Unsafe banquet fixture");
        checkpoint=output.getParent().resolve("initial-drop-reply.json");ledger=root.resolve("world/chronicle/last-council/banquet.json");
        if(Files.exists(checkpoint))throw new IllegalStateException("Stale drop checkpoint");
    }
    public static void start(MinecraftClient client) {
        if(trial!=null)throw new IllegalStateException("Gift trial already started");
        if(client.player==null || !client.player.getName().getString().equals("ChronicleFabricA"))throw new IllegalStateException("Unexpected client");
        trial=new BanquetGiftTrial();
    }
    /** Returns true once the trial has completed; failures throw to the caller. */
    public static boolean tick(MinecraftClient c) {
        if(trial==null)return true;
        if(trial.done)return true;
        try{return trial.advance(c);}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("Gift trial failed at step "+trial.step,e);}
    }
    private boolean advance(MinecraftClient c)throws Exception {
        if(++ticks>500)throw new IllegalStateException("Gift trial timeout at step "+step);
        switch(step){
            case 0->{require(c.player!=null,"Player disconnected before trial");c.setScreen(null);c.player.networkHandler.sendChatCommand("chronicle setup");step=1;}
            case 1->{if(!(c.currentScreen instanceof CoreMenuScreen menu)||menu.pending())return false;if(!menu.state().selected().equals("tool:last_council:banquet")){menu.selectForTest("tool:last_council:banquet");return false;}require(menu.triggerForTest("Open banquet"),"Open banquet missing");step=2;}
            case 2->{if(!(c.currentScreen instanceof BanquetScreen screen))return false;checkBothSizes(c,screen);View v=view(screen);world=v.worldId();require(!world.equals(io.github.chronicle.lastcouncil.network.BanquetPayloads.NONE),"Missing world identity");press(screen,"Give supplies...");step=3;}
            case 3->{if(!(c.currentScreen instanceof CouncilScreen screen))return false;checkBothSizes(c,screen);press(screen,"Choose online participant...");step=4;}
            case 4->{if(!(c.currentScreen instanceof CouncilScreen screen))return false;checkBothSizes(c,screen);press(screen,"ChronicleFabricA");step=5;}
            case 5->{if(!(c.currentScreen instanceof CouncilScreen screen))return false;checkBothSizes(c,screen);var fields=screen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).toList();require(fields.size()==3,"Gift fields missing");fields.get(0).setText("1");press(screen,"Review...");step=6;}
            case 6->{if(!(c.currentScreen instanceof CouncilScreen screen))return false;checkBothSizes(c,screen);press(screen,"Confirm");step=7;}
            case 7->{if(!Files.isRegularFile(checkpoint))return false;var o=JsonParser.parseString(Files.readString(checkpoint)).getAsJsonObject();request=UUID.fromString(o.get("request").getAsString());require(world.equals(UUID.fromString(o.get("worldId").getAsString())),"Checkpoint world mismatch");supplies=o.get("supplies").getAsInt();require(BanquetClient.compatiblePendingGift(world)!=null && BanquetClient.compatiblePendingGift(world).request().equals(request),"Gift nonce missing before disconnect");require(c.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).anyMatch(b->b.active&&b.getMessage().getString().equals("Give supplies...")),"pending gift disabled existing controls");UUID other=UUID.randomUUID();require(BanquetClient.giftBlocked(other)&&BanquetClient.compatiblePendingGift(other)==null,"pending gift can cross worlds");c.disconnect(new TitleScreen(),false);step=8;}
            case 8->{if(c.player!=null || c.getOverlay()!=null)return false;ConnectScreen.connect(c.currentScreen,c,ServerAddress.parse("127.0.0.1:25575"),new ServerInfo("Banquet gift fixture","127.0.0.1:25575",ServerInfo.ServerType.OTHER),false,null);step=9;}
            case 9->{if(c.player==null)return false;require(c.player.getName().getString().equals("ChronicleFabricA"),"Wrong reconnected player");c.setScreen(null);c.player.networkHandler.sendChatCommand("chronicle setup");step=10;}
            case 10->{if(!(c.currentScreen instanceof CoreMenuScreen menu)||menu.pending())return false;if(!menu.state().selected().equals("tool:last_council:banquet")){menu.selectForTest("tool:last_council:banquet");return false;}require(menu.triggerForTest("Open banquet"),"Open banquet missing after reconnect");step=11;}
            case 11->{if(!(c.currentScreen instanceof BanquetScreen screen))return false;require(world.equals(view(screen).worldId()),"Reconnected world changed");require(BanquetClient.compatiblePendingGift(world)!=null && BanquetClient.compatiblePendingGift(world).request().equals(request),"Gift nonce lost after reconnect");press(screen,"Retry request");step=12;}
            case 12->{if(!(c.currentScreen instanceof BanquetScreen screen)||BanquetClient.compatiblePendingGift(world)!=null)return false;require(view(screen).message().toLowerCase(java.util.Locale.ROOT).contains("already completed"),"Retry did not report completed gift");int count=JsonParser.parseString(Files.readString(ledger)).getAsJsonObject().getAsJsonArray("supplies").size();require(count==supplies,"Retry duplicated supplies");c.setScreen(null);done=true;return true;}
            default->throw new IllegalStateException("Unknown trial step");
        }
        return false;
    }
    private static View view(BanquetScreen screen)throws Exception{var f=BanquetScreen.class.getDeclaredField("view");f.setAccessible(true);return (View)f.get(screen);}
    private static void press(Screen screen,String label){var b=screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(x->x.active&&x.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new IllegalStateException("Missing button: "+label));b.onPress(new MouseInput(0,0));}
    private static void checkBothSizes(MinecraftClient c,CouncilScreen screen){screen.resize(c,480,360);checkFrame(screen);screen.resize(c,320,180);checkFrame(screen);}
    private static void checkFrame(CouncilScreen screen){for(var child:screen.children())if(child instanceof ClickableWidget w)require(w.getX()>=screen.left && w.getY()>=screen.top && w.getX()+w.getWidth()<=screen.left+screen.panelWidth && w.getY()+w.getHeight()<=screen.top+screen.panelHeight,"Control outside frame: "+w.getMessage().getString());}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
