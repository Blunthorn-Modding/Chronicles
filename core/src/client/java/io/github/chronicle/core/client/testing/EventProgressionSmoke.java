package io.github.chronicle.core.client.testing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.books.BookConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Native chat driver; assertions and checkpoints come from the actual server coordinator. */
public final class EventProgressionSmoke {
    private static final String OUTPUT=System.getenv("CHRONICLE_EVENT_PROGRESSION_SMOKE"),
            ROOT=System.getenv("CHRONICLE_EVENT_PROGRESSION_SERVER_DIR"),PHASE=System.getenv("CHRONICLE_EVENT_PROGRESSION_PHASE"),ADDRESS="127.0.0.1:25575";
    private final Path output,root;private final List<String> messages=new ArrayList<>();
    private final long began=System.nanoTime();private boolean connecting,started,bounced,done;private int warmup;
    private EventProgressionSmoke() {
        output=Path.of(OUTPUT).toAbsolutePath().normalize();root=Path.of(ROOT).toAbsolutePath().normalize();
        if(!Files.isRegularFile(root.resolve(".event-progression-fixture"))||output.startsWith(root)
                ||root.getParent()==null||!root.getParent().getFileName().toString().equals("test-runs")
                ||!List.of("basic","restart","crash","recover","final").contains(PHASE))
            throw new IllegalArgumentException("unsafe progression smoke fixture");
    }
    public static void register() {
        if(OUTPUT==null)return;
        try {
            var instance=new EventProgressionSmoke();
            ClientReceiveMessageEvents.GAME.register((message,overlay)->{if(!overlay)instance.messages.add(message.getString());});
            ClientTickEvents.END_CLIENT_TICK.register(instance::tick);
        }catch(Exception failure){ChronicleCore.LOGGER.error("Event progression smoke setup failed",failure);}
    }
    private void tick(MinecraftClient client) {
        if(done)return;
        try {
            if(System.nanoTime()-began>300_000_000_000L)throw new IllegalStateException("progression smoke timeout in "+PHASE);
            for(String message:messages)if(message.contains("Event progression trial FAIL"))throw new IllegalStateException(message);
            if(client.player==null&&client.getOverlay()==null&&client.currentScreen instanceof AccessibilityOnboardingScreen)client.setScreen(new TitleScreen());
            if(!connecting&&client.player==null&&client.getOverlay()==null&&client.currentScreen instanceof TitleScreen) {
                connecting=true;ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(ADDRESS),new ServerInfo("Progression smoke",ADDRESS,ServerInfo.ServerType.OTHER),false,null);return;
            }
            if(client.player==null)return;client.getToastManager().clear();
            if(!started) {
                if(++warmup<40)return;started=true;command(client,PHASE);return;
            }
            if(PHASE.equals("basic")&&!bounced&&seen("Event progression trial NEXT bounce")){bounced=true;command(client,"bounce");}
            if(seen("Event progression trial PASS "+PHASE))finish(client,true,"native runtime checkpoints passed");
        }catch(Throwable failure){finish(client,false,failure.getClass().getSimpleName()+": "+failure.getMessage());}
    }
    private void command(MinecraftClient client,String verb){client.player.networkHandler.sendChatCommand("chronicle_event_progression_trial "+verb);}
    private boolean seen(String text){return messages.stream().anyMatch(message->message.contains(text));}
    private void finish(MinecraftClient client,boolean pass,String detail) {
        if(done)return;done=true;
        try {
            JsonObject value=new JsonObject();value.addProperty("result",pass?"PASS":"FAIL");value.addProperty("phase",PHASE);value.addProperty("detail",detail);
            JsonArray transcript=new JsonArray();messages.forEach(transcript::add);value.add("messages",transcript);
            if(Files.isRegularFile(root.resolve(".event-progression-trial.json"))) {
                JsonObject trial=BookConfig.strictObject(Files.readString(root.resolve(".event-progression-trial.json")));
                value.add("trial",trial);if(trial.has("checks"))value.add("checks",trial.get("checks"));
            }
            Files.createDirectories(output.getParent());Files.writeString(output,value+"\n");
        }catch(Exception failure){ChronicleCore.LOGGER.error("Cannot write Event progression smoke result",failure);}
        client.scheduleStop();
    }
}
