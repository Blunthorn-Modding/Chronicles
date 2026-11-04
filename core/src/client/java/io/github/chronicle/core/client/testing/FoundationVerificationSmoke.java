package io.github.chronicle.core.client.testing;

import com.google.gson.*;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.server.FoundationVerificationTrial;
import io.github.chronicle.core.sound.BundledSoundCatalog;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.*;
import net.minecraft.client.sound.OggAudioStream;

/** Native codec/private-preview proof; the automated clients remain muted. */
public final class FoundationVerificationSmoke {
    private final Path output;private final List<String> messages=new ArrayList<>(),checks=new ArrayList<>();private final boolean observer;
    private boolean connecting,done;private int step,wait;private long start=System.nanoTime();
    private FoundationVerificationSmoke(String destination){output=Path.of(destination);observer="true".equals(System.getenv("CHRONICLE_FOUNDATION_OBSERVER"));Path root=Path.of(System.getenv("CHRONICLE_FOUNDATION_ROOT")).toAbsolutePath().normalize();if(!Files.isRegularFile(root.resolve(".foundation-fixture"))||!root.getParent().getFileName().toString().equals("test-runs")||output.toAbsolutePath().startsWith(root))throw new IllegalArgumentException("unsafe fixture");}
    public static void register(){String file=System.getenv("CHRONICLE_FOUNDATION_SMOKE");if(file==null)return;var probe=new FoundationVerificationSmoke(file);ClientReceiveMessageEvents.GAME.register((text,overlay)->{if(!overlay)probe.messages.add(text.getString());});ClientTickEvents.END_CLIENT_TICK.register(probe::tick);}
    private void tick(MinecraftClient c){if(done)return;try{
        if(System.nanoTime()-start>180_000_000_000L)throw new IllegalStateException("timeout step "+step+" "+messages);
        if(c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof AccessibilityOnboardingScreen)c.setScreen(new TitleScreen());
        if(!connecting&&c.player==null&&c.getOverlay()==null&&c.currentScreen instanceof TitleScreen){connecting=true;ConnectScreen.connect(c.currentScreen,c,ServerAddress.parse("127.0.0.1:25575"),new ServerInfo("Foundation proof","127.0.0.1:25575",ServerInfo.ServerType.OTHER),false,null);return;}
        if(c.player==null)return;c.getToastManager().clear();for(String message:messages)if(message.contains("Foundation trial FAIL"))throw new IllegalStateException(message);
        if(step==0){decodeAll(c);step=1;if(!observer)command(c,"prepare");return;}
        if(observer){if(messages.stream().anyMatch(m->m.equals("Foundation trial RELEASE"))){require(mediaCount()==1,"private preview reached observer: "+mediaCount());require(count(FoundationVerificationTrial.FIRST)==1&&count(FoundationVerificationTrial.REPEAT)==3,"observer recurrence audience/count mismatch");checks.add("observer received first-player cue once, all three activity cues, and no GM-private preview");finish(c,null);}return;}
        CoreMenuScreen menu=c.currentScreen instanceof CoreMenuScreen m?m:null;
        if(step==1){if(!passed("prepare"))return;wait=30;step=2;return;}
        if(step==2){if(wait-->0)return;command(c,"ready");wait=10;step=3;return;}
        if(step==3){if(!passed("ready")){if(wait-->0)return;step=2;return;}require(mediaCount()==1,"live Event media was not delivered once");command(c,"repeat");wait=20;step=11;return;}
        if(step==11){if(!passed("repeat")||wait-->0)return;command(c,"repeated");wait=10;step=12;return;}
        if(step==12){if(!passed("repeated")){if(wait-->0)return;step=11;return;}if(count(FoundationVerificationTrial.REPEAT)<3||count(FoundationVerificationTrial.FIRST)<1)return;require(count(FoundationVerificationTrial.FIRST)==1&&count(FoundationVerificationTrial.REPEAT)==3,"operator recurrence audience/count mismatch");checks.add("durable source queue delivered three activity cues; actor saw their own first-use cue once");command(c,"uncertain");wait=10;step=13;return;}
        if(step==13){if(wait-->0)return;command(c,"uncertain_ready");wait=10;step=14;return;}
        if(step==14){if(!passed("uncertain_ready")){if(wait-->0)return;step=13;return;}command(c,"acknowledge");wait=10;step=15;return;}
        if(step==15){if(wait-->0)return;command(c,"uncertain_clear");wait=10;step=16;return;}
        if(step==16){if(!passed("uncertain_clear")){if(wait-->0)return;step=15;return;}require(count("Uncertain cue must not replay")==0,"uncertain cue was replayed");checks.add("post-claim media failure held intake; exact GM acknowledgement retired evidence without replay");command(c,"source_recovery_holds");step=20;return;}
        if(step==20){if(!passed("source_recovery_holds"))return;checks.add("exact entity metadata recovery preserved parent Event hold, rejected unrelated holds and fabricated no activity");command(c,"pressure");wait=20;step=17;return;}
        if(step==17){if(!passed("pressure")||wait-->0)return;command(c,"pressure_release");wait=10;step=18;return;}
        if(step==18){if(wait-->0)return;command(c,"pressure_clear");wait=10;step=19;return;}
        if(step==19){if(!passed("pressure_clear")){if(wait-->0)return;step=18;return;}if(count("Capacity drain cue")<256)return;require(count("Capacity drain cue")==256,"capacity drain duplicated/lost media");checks.add("256 blocked queued cues stopped intake; real GM flag override unblocked and drained all exact archives");command(c,"pause");step=4;return;}
        if(step==4){if(!passed("pause"))return;c.player.networkHandler.sendChatCommand("chronicle");step=5;return;}
        if(step==5){if(menu==null||menu.pending())return;require(menu.triggerForTest("Events"),"Events navigation missing");step=6;return;}
        if(step==6){if(menu==null||menu.pending()||menu.state().page()!=io.github.chronicle.core.menu.CoreMenuModel.Page.EVENTS)return;var entry=menu.state().entries().stream().filter(e->e.label().equals("Foundation media")).findFirst().orElseThrow();menu.selectForTest(entry.id());step=7;return;}
        if(step==7){if(menu==null||menu.pending()||menu.state().detail()==null||!menu.state().detail().title().equals("Foundation media"))return;require(menu.triggerForTest("Preview privately"),"private preview missing");wait=60;step=8;return;}
        if(step==8){if(wait-->0)return;require(mediaCount()==2,"actual C2S private preview did not reach GM exactly once");command(c,"online_guard");step=9;return;}
        if(step==9){if(!passed("online_guard"))return;checks.add("live automatic Event and C2S private preview kept intended recipients; online presence blocked cleanup beyond quiet delay");command(c,"release");step=10;return;}
        if(step==10){if(!passed("release"))return;finish(c,null);}
    }catch(Throwable error){finish(c,error.toString());}}
    private void decodeAll(MinecraftClient c)throws Exception{
        io.github.chronicle.core.scenario.ScenarioContent content;
        try(var source=getClass().getResourceAsStream("/data/quiet_below/chronicle/scenario-content.json")){require(source!=null,"missing final scenario defaults");content=io.github.chronicle.core.scenario.ScenarioContentJson.decode(new String(source.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        for(var book:content.books().values())for(String page:book.pages())require(c.textRenderer.wrapLines(net.minecraft.text.Text.literal(page),114).size()<=14,"book page exceeds native reading height: "+book.title());
        checks.add("all nine final books fit the native Minecraft font/page bounds");
        try(var input=getClass().getResourceAsStream("/data/quiet_below/chronicle/event-sounds.json")){var catalog=BundledSoundCatalog.read(input);catalog.verify(getClass()::getResourceAsStream);for(var sound:catalog.sounds()){
            require(c.getSoundManager().get(net.minecraft.util.Identifier.of(sound.id()))!=null,"sound registry not loaded: "+sound.id());
            try(var stream=getClass().getResourceAsStream("/assets/"+catalog.namespace()+"/sounds/"+sound.file()+".ogg");var decoder=new OggAudioStream(stream)){long[] count={0};while(decoder.read((it.unimi.dsi.fastutil.floats.FloatConsumer)value->{if(!Float.isFinite(value))throw new IllegalStateException("non-finite sample");count[0]++;})){if(count[0]>250_000_000L)throw new IllegalStateException("unbounded audio");}require(count[0]>0,"decoded no audio: "+sound.id());}
        }checks.add("matching audio handshake, registry/resources and native Vorbis decoder passed all "+catalog.sounds().size()+" clips");}
    }
    private int mediaCount(){return (int)messages.stream().filter(m->m.equals(FoundationVerificationTrial.MARK)).count();}
    private int count(String text){return (int)messages.stream().filter(m->m.equals(text)).count();}
    private boolean passed(String verb){return messages.stream().anyMatch(m->m.contains("Foundation trial PASS "+verb));}
    private void command(MinecraftClient c,String verb){c.player.networkHandler.sendChatCommand("chronicle_foundation "+verb);}
    private void finish(MinecraftClient c,String error){if(done)return;done=true;try{var value=new JsonObject();value.addProperty("result",error==null?"PASS":"FAIL");value.addProperty("detail",error==null?"Completed":error);value.add("checks",new Gson().toJsonTree(checks));Files.writeString(output,new Gson().toJson(value));}catch(Exception ignored){}c.scheduleStop();}
    private static void require(boolean okay,String message){if(!okay)throw new IllegalStateException(message);}
}
