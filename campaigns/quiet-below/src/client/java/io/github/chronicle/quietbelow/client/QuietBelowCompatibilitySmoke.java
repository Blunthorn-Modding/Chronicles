package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;

/** Also packaged alone as a test-only client mod beside retained dev.18 jars. */
public final class QuietBelowCompatibilitySmoke implements ClientModInitializer {
    private static final String OUTPUT=System.getenv("CHRONICLE_COMPATIBILITY_SMOKE");
    private static final String CASE=System.getenv("CHRONICLE_COMPATIBILITY_CASE");
    private static final String ADDRESS="127.0.0.1:25575";
    private final long deadline=System.nanoTime()+120_000_000_000L;
    private boolean started,capturing,finished;
    private String reason;

    @Override public void onInitializeClient(){register();}
    public static void register(){
        if(OUTPUT==null)return;
        if(!Set.of("old-client","old-server").contains(CASE))throw new IllegalArgumentException("Invalid compatibility case");
        var test=new QuietBelowCompatibilitySmoke();ClientTickEvents.END_CLIENT_TICK.register(test::tick);
    }
    private void tick(MinecraftClient client){
        if(finished)return;
        try{
            if(System.nanoTime()>deadline)throw new IllegalStateException("Compatibility rejection timed out");
            if(client.world!=null||client.player!=null)throw new IllegalStateException("An incompatible peer entered PLAY");
            if(!started&&client.getOverlay()==null&&client.currentScreen instanceof AccessibilityOnboardingScreen)
                throw new IllegalStateException("Client defaults did not suppress onboarding");
            if(!started&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen){
                started=true;
                ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(ADDRESS),
                        new ServerInfo("Chronicle layout rejection fixture",ADDRESS,ServerInfo.ServerType.OTHER),false,null);
            }
            if(started&&client.currentScreen instanceof DisconnectedScreen screen){
                if(!capturing){
                    reason=screen.children().stream().filter(e->e instanceof ClickableWidget)
                            .map(e->((ClickableWidget)e).getMessage().getString()).reduce("",(a,b)->a+" "+b);
                    String expected=CASE.equals("old-client")?"client pack is missing or outdated":"server pack is missing or outdated";
                    if(!reason.contains("The Quiet Below connection was refused:")||!reason.contains(expected))
                        throw new IllegalStateException("Unexpected rejection: "+reason);
                    capturing=true;
                    ScreenshotRecorder.saveScreenshot(Path.of(OUTPUT).toFile(),CASE+"-rejected.png",client.getFramebuffer(),1,t->{});
                }
                if(Files.isRegularFile(Path.of(OUTPUT,"screenshots",CASE+"-rejected.png")))finish(client,true,"refused before PLAY");
            }
        }catch(Exception e){finish(client,false,e.toString());}
    }
    private void finish(MinecraftClient client,boolean pass,String detail){
        finished=true;
        try{
            Files.createDirectories(Path.of(OUTPUT));Files.writeString(Path.of(OUTPUT,"compatibility-"+CASE+".json"),new Gson().toJson(Map.of(
                    "result",pass?"PASS":"FAIL","detail",detail,"reason",reason==null?"":reason,
                    "checks",pass?List.of("incompatible peer never entered PLAY","matching-pack rejection text displayed","rejection screenshot captured"):List.of())));
        }catch(Exception e){e.printStackTrace();}
        client.scheduleStop();
    }
}
