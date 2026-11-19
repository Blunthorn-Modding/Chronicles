package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.play.EventSource;
import io.github.chronicle.core.server.EventSources;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;
import java.util.*;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.*;

/** Diagnostic commands exist only in explicitly armed, marked loopback fixtures. */
public final class PoliticalSourcesTrial {
    private static final List<String> TYPES=List.of("vote_completed","succession_result","treaty_changed","rebellion_changed");
    public static void register(){
        if(!"true".equals(System.getenv("CHRONICLE_POLITICAL_SOURCES_TRIAL")))return;
        CommandRegistrationCallback.EVENT.register((d,a,e)->{
            var command=literal("chronicle_council_sources_trial").requires(s->s.hasPermissionLevel(2));
            for(String verb:List.of("snapshot","unchanged","verify_eliminated","verify_crowned","held_guard"))command.then(literal(verb).executes(c->run(c.getSource(),verb,"")));
            command.then(literal("private_absent").then(argument("id",string()).executes(c->run(c.getSource(),"private_absent",getString(c,"id")))));
            d.register(command);
        });
    }
    @SuppressWarnings("unchecked") private static Map<net.minecraft.server.MinecraftServer,Object> worlds(Class<?> type)throws Exception{var f=type.getDeclaredField("WORLDS");f.setAccessible(true);return (Map<net.minecraft.server.MinecraftServer,Object>)f.get(null);}
    private static int run(ServerCommandSource source,String verb,String id){try{
        var server=source.getServer();var root=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
        if(root==null||root.getParent()==null||!root.getParent().getFileName().toString().equals("test-runs")||!root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")||!Files.isRegularFile(root.resolve(".political-sources-fixture"))||!server.isDedicated()||server.isOnlineMode()||server.getServerPort()!=25575||!"127.0.0.1".equals(server.getServerIp()))throw new IllegalStateException("Unsafe fixture");
        if(verb.equals("held_guard")){
            if(io.github.chronicle.core.server.ChronicleRuntime.service(server).state().phase()!=io.github.chronicle.core.campaign.SessionPhase.ACTIVE)throw new IllegalStateException("Active phase required");
            String owner="last_council:source_guard_trial";
            io.github.chronicle.core.server.RuntimeHolds.hold(server,owner,"Disposable held-source guard test");
            var play=worlds(io.github.chronicle.core.server.PlayRuntime.class);var sources=worlds(EventSources.class);Object oldPlay=play.remove(server),oldSources=sources.remove(server);
            try{PoliticalSourcesRuntime.prepare(server,List.of(new io.github.chronicle.lastcouncil.progression.PoliticalOccurrence("council-outcome:"+UUID.randomUUID(),"last_council:vote_completed","*","passed")));}
            finally{if(oldPlay!=null)play.put(server,oldPlay);if(oldSources!=null)sources.put(server,oldSources);io.github.chronicle.core.server.RuntimeHolds.release(server,owner);}
            source.sendFeedback(()->Text.literal("Council sources trial PASS held_guard"),false);return 1;
        }
        var facts=new ArrayList<EventSources.Occurrence>();
        for(String type:TYPES)facts.addAll(EventSources.facts(server,new EventSource("last_council:"+type,"*","")));
        var keys=new TreeSet<String>();var ids=new TreeSet<String>();
        for(var fact:facts){if(fact.actor()!=null||!keys.add(fact.authority()+"|"+fact.type()+"|"+fact.target()+"|"+fact.activity()))throw new IllegalStateException("Duplicate or private political metadata");ids.add(fact.id());}
        var file=root.resolve("political-sources-receipts.txt");
        switch(verb){
            case "snapshot" -> Files.write(file,ids);
            case "verify_eliminated", "verify_crowned" -> io.github.chronicle.core.server.testing.EventSourcesTrial.verify(server,new EventSource("last_council:succession_result","election",verb.substring("verify_".length())));
            case "unchanged" -> {if(!ids.equals(new TreeSet<>(Files.readAllLines(file))))throw new IllegalStateException("Political sources changed on restart without gameplay");}
            case "private_absent" -> {String prefix="council-treaty:"+UUID.fromString(id)+":";if(facts.stream().anyMatch(f->f.authority().startsWith(prefix))||EventSources.pending(server).stream().anyMatch(f->f.authority().startsWith(prefix)))throw new IllegalStateException("Private treaty leaked source metadata");}
            default -> throw new IllegalArgumentException();
        }
        source.sendFeedback(()->Text.literal("Council sources trial PASS "+verb),false);return 1;
    }catch(Exception failure){source.sendError(Text.literal("Council sources trial FAIL "+verb+": "+failure.getMessage()));return 0;}}
}
