package io.github.chronicle.core.client.testing;

import com.google.gson.*;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.client.DepartureConfirmScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Native-client identity/connection driver; provider actions run through the guarded server's real vanilla APIs. */
public final class EventSourcesSmoke {
    private static final String OUTPUT=System.getenv("CHRONICLE_EVENT_SOURCES_SMOKE"),ROOT=System.getenv("CHRONICLE_EVENT_SOURCES_SERVER_DIR"),PHASE=System.getenv("CHRONICLE_EVENT_SOURCES_PHASE");
    private final Path output,root;private final long began=System.nanoTime();private final List<String> messages=new ArrayList<>();private boolean connecting,sent,done;private int departureStep,wait;
    private EventSourcesSmoke() {
        output=Path.of(OUTPUT).toAbsolutePath().normalize();root=Path.of(ROOT).toAbsolutePath().normalize();
        if(output.startsWith(root)||!Files.isRegularFile(root.resolve(".event-sources-fixture"))||!List.of("basic","recovery","stations","departure","regions").contains(PHASE))throw new IllegalArgumentException("unsafe Event sources client fixture");
    }
    public static void register() {
        if(OUTPUT==null)return;
        try{var smoke=new EventSourcesSmoke();ClientReceiveMessageEvents.GAME.register((message,overlay)->{if(!overlay)smoke.messages.add(message.getString());});ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);}
        catch(Exception failure){ChronicleCore.LOGGER.error("Event sources smoke setup failed",failure);}
    }
    private void tick(MinecraftClient client) {
        if(done)return;
        try {
            if(System.nanoTime()-began>180_000_000_000L)throw new IllegalStateException("Event source native fixture timeout");
            if(client.player==null&&client.getOverlay()==null&&client.currentScreen instanceof AccessibilityOnboardingScreen)client.setScreen(new TitleScreen());
            if(!connecting&&client.player==null&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen){connecting=true;String address="127.0.0.1:25575";ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(address),new ServerInfo("Event sources smoke",address,ServerInfo.ServerType.OTHER),false,null);return;}
            if(client.player==null)return;
            if(!sent){client.player.networkHandler.sendChatCommand(PHASE.equals("stations")?"chronicle_source_stations_trial run":"chronicle_event_sources_trial "+PHASE);sent=true;}
            // Real use/open packets can show vanilla handled/book screens; explicit closing does not produce source evidence.
            if(PHASE.equals("departure"))departure(client);
            if(!List.of("stations","departure").contains(PHASE)&&client.currentScreen!=null){if(client.player.currentScreenHandler!=client.player.playerScreenHandler)client.player.closeHandledScreen();else client.setScreen(null);}
            for(String message:messages){if(message.contains("Event sources trial FAIL "))throw new IllegalStateException(message);if(message.endsWith("Event sources trial PASS "+PHASE)){finish(client,true,"real guarded source provider phase completed");return;}}
        }catch(Exception failure){finish(client,false,failure.toString());}
    }
    private void departure(MinecraftClient client) {
        DepartureConfirmScreen screen=client.currentScreen instanceof DepartureConfirmScreen value?value:null;
        switch(departureStep) {
            case 0->{if(messages.stream().noneMatch(m->m.contains("NEXT departure_review")))return;interact(client);departureStep++;}
            case 1->{if(screen==null||screen.state().stage()!=1)return;if(!screen.triggerForTest("Continue"))throw new IllegalStateException("first departure Continue unavailable");departureStep++;}
            case 2->{if(screen==null||screen.state().stage()!=2)return;if(!screen.triggerForTest("Back"))throw new IllegalStateException("departure Back unavailable");departureStep++;}
            case 3->{if(screen==null||screen.state().stage()!=1)return;if(!screen.triggerForTest("Cancel"))throw new IllegalStateException("departure Cancel unavailable");wait=12;departureStep++;}
            case 4->{if(wait-->0)return;client.player.networkHandler.sendChatCommand("chronicle_event_sources_trial departure_cancelled");departureStep++;}
            case 5->{if(messages.stream().noneMatch(m->m.endsWith("PASS departure_cancelled")))return;interact(client);departureStep++;}
            case 6->{if(screen==null||screen.state().stage()!=1)return;if(!screen.triggerForTest("Continue"))throw new IllegalStateException("final departure Continue unavailable");departureStep++;}
            case 7->{if(screen==null||screen.state().stage()!=2)return;if(!screen.triggerForTest("Depart"))throw new IllegalStateException("final departure confirmation unavailable");departureStep++;}
            default->{ }
        }
    }
    private static void interact(MinecraftClient client){BlockPos pos=new BlockPos(4,64,4);client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(4.5,64.5,4.05),Direction.NORTH,pos,false));}
    private void finish(MinecraftClient client,boolean pass,String detail) {
        if(done)return;done=true;
        try{JsonObject report=new JsonObject();report.addProperty("result",pass?"PASS":"FAIL");report.addProperty("phase",PHASE);report.addProperty("detail",detail);
            Path marker=root.resolve(".event-sources-trial.json");if(Files.isRegularFile(marker)){JsonObject trial=JsonParser.parseString(Files.readString(marker)).getAsJsonObject();report.add("checks",trial.getAsJsonArray("checks"));}
            Files.createDirectories(output.getParent());Files.writeString(output,new Gson().toJson(report)+"\n");}
        catch(Exception failure){ChronicleCore.LOGGER.error("Event sources native evidence write failed",failure);}
        client.scheduleStop();
    }
}
