package io.github.chronicle.lastcouncil.client;

import com.google.gson.*;
import io.github.chronicle.lastcouncil.network.*;
import io.github.chronicle.lastcouncil.treaties.TreatyService.Purpose;
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
import net.minecraft.client.network.*;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/** Opt-in native fixture driver. It can connect only to the disposable loopback smoke server. */
public final class ConflictNativeProbe {
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private final Path root,directory;private final String role;private boolean connecting;private int tick;private long last=-1;private String error="";private Object treaty,conflict;private final List<String> messages=new ArrayList<>();
    private ConflictNativeProbe(String directory)throws Exception{root=Path.of(System.getenv("CHRONICLE_CONFLICT_ROOT")).toAbsolutePath().normalize();this.directory=Path.of(directory).toAbsolutePath().normalize();role=System.getenv("CHRONICLE_CONFLICT_ROLE");if(!Set.of("A","B","C","D").contains(role)||root.getParent()==null||!root.getParent().getFileName().toString().equals("test-runs")||!Files.isRegularFile(root.resolve(".council-conflicts-fixture"))||this.directory.startsWith(root))throw new IllegalArgumentException("Unsafe conflict fixture");}
    public static void register(){String dir=System.getenv("CHRONICLE_CONFLICT_PROBE");if(dir!=null)try{var p=new ConflictNativeProbe(dir);ClientTickEvents.END_CLIENT_TICK.register(p::tick);net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message,overlay)->{p.messages.add(message.getString());if(p.messages.size()>40)p.messages.remove(0);});}catch(Exception e){throw new IllegalStateException(e);}}
    private void tick(MinecraftClient c){try{
        tick++;if(c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof AccessibilityOnboardingScreen)c.setScreen(new TitleScreen());
        if(!connecting&&c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof TitleScreen){connecting=true;ConnectScreen.connect(c.currentScreen,c,ServerAddress.parse("127.0.0.1:25575"),new ServerInfo("Conflict fixture","127.0.0.1:25575",ServerInfo.ServerType.OTHER),false,null);}
        if(c.player==null)return;Path command=directory.resolve(role+"-command.json");if(Files.isRegularFile(command)){JsonObject q=JsonParser.parseString(Files.readString(command,StandardCharsets.UTF_8)).getAsJsonObject();long id=q.get("id").getAsLong();if(id>last){last=id;error="";try{act(c,q);}catch(Exception e){error=e.toString();}}}
        if(tick%4==0){if(TreatyClient.currentView()!=null)treaty=TreatyClient.currentView();if(ConflictClient.currentView()!=null)conflict=ConflictClient.currentView();JsonObject s=new JsonObject();s.addProperty("last",last);s.addProperty("error",error);s.addProperty("screen",c.currentScreen==null?"":c.currentScreen.getClass().getSimpleName());s.addProperty("treatyOpen",TreatyClient.currentRoot()!=null);s.addProperty("conflictOpen",ConflictClient.currentRoot()!=null);s.add("treaty",JSON.toJsonTree(treaty));s.add("conflict",JSON.toJsonTree(conflict));s.addProperty("x",c.player.getX());s.addProperty("y",c.player.getY());s.addProperty("z",c.player.getZ());JsonArray buttons=new JsonArray(),outside=new JsonArray();if(c.currentScreen!=null)for(var child:c.currentScreen.children())if(child instanceof ClickableWidget w){var b=new JsonObject();b.addProperty("label",w.getMessage().getString());b.addProperty("active",w.active);buttons.add(b);if(c.currentScreen instanceof CouncilScreen f&&(w.getX()<f.left||w.getY()<f.top||w.getX()+w.getWidth()>f.left+f.panelWidth||w.getY()+w.getHeight()>f.top+f.panelHeight))outside.add(w.getMessage().getString());}s.add("buttons",buttons);s.add("outside",outside);s.add("messages",JSON.toJsonTree(messages));Path target=directory.resolve(role+"-state.json");Files.writeString(target,JSON.toJson(s),StandardCharsets.UTF_8);}
    }catch(java.io.IOException|com.google.gson.JsonParseException transientProbeIo){/* Reader retries incomplete diagnostic frames. No gameplay authority lives here. */}catch(Exception e){error=e.toString();}}
    private void act(MinecraftClient c,JsonObject q)throws Exception{switch(q.get("action").getAsString()){
        case "chat"->c.player.networkHandler.sendChatCommand(q.get("text").getAsString());
        case "close"->{if(c.currentScreen!=null)c.currentScreen.close();}
        case "lectern"->{if(c.currentScreen!=null)c.currentScreen.close();c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(.5,65.5,2),Direction.NORTH,new BlockPos(0,65,2),false));}
        case "challenge"->{if(c.currentScreen!=null)c.currentScreen.close();c.player.getInventory().setSelectedSlot(0);c.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(0));UUID who=UUID.nameUUIDFromBytes(("OfflinePlayer:ChronicleFabric"+q.get("target").getAsString()).getBytes(StandardCharsets.UTF_8));var target=c.world.getPlayerByUuid(who);if(target==null)throw new IllegalStateException("Target not loaded");c.interactionManager.interactEntity(c.player,target,Hand.MAIN_HAND);}
        case "click"->{String label=q.get("label").getAsString();c.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(b->b.active&&b.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new IllegalStateException("Missing active button "+label)).onPress(new MouseInput(0,0));}
        case "text"->{String value=q.get("value").getAsString();if(q.has("edit")&&q.get("edit").getAsBoolean())c.currentScreen.children().stream().filter(EditBoxWidget.class::isInstance).map(EditBoxWidget.class::cast).findFirst().orElseThrow().setText(value);else c.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow().setText(value);}
        case "treaty"->{var v=TreatyClient.currentView();UUID tid=q.has("treaty")?UUID.fromString(q.get("treaty").getAsString()):q.get("verb").getAsString().equals("CREATE")||v.detail()==null?TreatyPayloads.NONE:v.detail().id();var parties=new ArrayList<UUID>();if(q.has("parties"))for(var p:q.getAsJsonArray("parties"))parties.add(UUID.nameUUIDFromBytes(("OfflinePlayer:ChronicleFabric"+p.getAsString()).getBytes(StandardCharsets.UTF_8)));ClientPlayNetworking.send(new TreatyPayloads.Request(UUID.randomUUID(),TreatyPayloads.Action.valueOf(q.get("verb").getAsString()),v.token(),tid,q.has("revision")?q.get("revision").getAsLong():v.detail()==null?0:v.detail().revision(),BlockPos.ORIGIN,q.has("title")?q.get("title").getAsString():"",q.has("terms")?q.get("terms").getAsString():"",q.has("private")&&q.get("private").getAsBoolean(),parties,0,q.has("purpose")?Purpose.valueOf(q.get("purpose").getAsString()):Purpose.GENERAL,q.has("conflict")?UUID.fromString(q.get("conflict").getAsString()):TreatyPayloads.NONE,q.has("membership")?q.get("membership").getAsLong():0));}
        case "conflict"->{var v=ConflictClient.currentView();UUID id=q.has("conflict")?UUID.fromString(q.get("conflict").getAsString()):v.detail()==null?ConflictPayloads.NONE:v.detail().id();ClientPlayNetworking.send(new ConflictPayloads.Request(UUID.randomUUID(),ConflictPayloads.Action.valueOf(q.get("verb").getAsString()),v.token(),id,q.has("revision")?q.get("revision").getAsLong():v.detail()==null?0:v.detail().revision(),q.has("side")?q.get("side").getAsString():"",q.has("reason")?q.get("reason").getAsString():"",0));}
        case "resize"->c.getWindow().setWindowedSize(q.get("width").getAsInt(),q.get("height").getAsInt());
        case "capture"->{String name=q.get("name").getAsString();if(!name.matches("[a-z0-9_-]+\\.png"))throw new IllegalArgumentException("Unsafe capture name");ScreenshotRecorder.saveScreenshot(directory.toFile(),name,c.getFramebuffer(),1,ignored->{});}
        case "stop"->c.scheduleStop();
        default->throw new IllegalArgumentException("Unknown native probe action");
    }}
}
