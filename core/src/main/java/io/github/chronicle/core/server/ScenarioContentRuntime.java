package io.github.chronicle.core.server;

import io.github.chronicle.core.scenario.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Core-owned capture/export coordinator. Providers only identify a scenario; Core owns all live definition reads. */
public final class ScenarioContentRuntime {
    private static final int MAX_PROVIDERS=16;
    private static final Map<String,String> PROVIDERS=new TreeMap<>(); private static boolean frozen;
    private ScenarioContentRuntime() { }
    public record Pinned(ScenarioExportPreview preview,ScenarioContent content) { }
    public static synchronized void registerScenario(String id,String title) {
        new ScenarioContent(id,Map.of(),Map.of(),Map.of(),Map.of());
        if(title==null||title.isBlank()||title.length()>160)throw new IllegalArgumentException("Scenario title is required and bounded");
        if(frozen)throw new IllegalStateException("Scenario provider registration is closed after server start");
        if(PROVIDERS.containsKey(id))throw new IllegalArgumentException("Scenario provider is already registered: "+id);
        if(PROVIDERS.size()>=MAX_PROVIDERS)throw new IllegalStateException("At most 16 scenario content providers may register");
        PROVIDERS.put(id,title);
    }
    /** Called by Core lifecycle setup; registration stays available through campaign initialization only. */
    public static synchronized void register(){ServerLifecycleEvents.SERVER_STARTED.register(server->freezeRegistrations());}
    private static synchronized void freezeRegistrations(){frozen=true;}
    static synchronized List<ScenarioExportPreview> previews(MinecraftServer server) {
        try {int roles=PlayerRoles.definitions(server).size(),books=BookRuntime.books(server).size(),loot=TypedChestLoot.types(server).size(),events=eventCount(server),groups=Encounters.groups(server).size();return PROVIDERS.entrySet().stream().map(entry->new ScenarioExportPreview(entry.getKey(),entry.getValue(),roles,books,loot,events,0,"",true,"",groups)).toList();}
        catch(RuntimeException exception){return PROVIDERS.entrySet().stream().map(entry->new ScenarioExportPreview(entry.getKey(),entry.getValue(),0,0,0,0,0,"",false,shortText(exception.getMessage()))).toList();}
    }
    private static int eventCount(MinecraftServer server) {
        var ids=new HashSet<>(ScenarioEventTemplates.templates().keySet());
        ids.addAll(EventRuntime.configurations(server).keySet());
        return ids.size();
    }
    static synchronized Pinned open(MinecraftServer server,String providerId) {
        String title=PROVIDERS.get(providerId);if(title==null)throw new IllegalArgumentException("Choose a registered scenario content provider.");
        return capture(server,providerId,title);
    }
    static String export(MinecraftServer server,Pinned pinned) throws IOException {
        if(pinned==null)throw new IllegalStateException("Open Scenario content and review the current definitions first.");
        Pinned current=open(server,pinned.preview().providerId());
        if(!MessageDigest.isEqual(pinned.preview().digest().getBytes(StandardCharsets.US_ASCII),current.preview().digest().getBytes(StandardCharsets.US_ASCII)))throw new IllegalStateException("Scenario definitions changed. Review the current content before exporting.");
        ScenarioDraftStore store=new ScenarioDraftStore(FabricLoader.getInstance().getConfigDir().resolve("chronicle"),pinned.content().scenarioId());
        store.load(); // Establishes the observed revision and rejects corrupt/external draft data before overwrite.
        store.save(pinned.content());
        return store.export(pinned.content()).toString();
    }
    private static Pinned capture(MinecraftServer server,String id,String title) {
        var result=ScenarioContentCapture.capture(id,PlayerRoles.definitions(server),BookRuntime.books(server),TypedChestLoot.types(server),EventRuntime.configurations(server),Encounters.groups(server));
        var captured=result.content();
        var events=new TreeMap<>(ScenarioEventTemplates.templates());events.putAll(captured.events());
        var content=new ScenarioContent(captured.scenarioId(),captured.roles(),captured.books(),captured.lootTables(),events,captured.encounterGroups());
        String digest=digest(ScenarioContentJson.encode(content));
        return new Pinned(new ScenarioExportPreview(id,title,content.roles().size(),content.books().size(),content.lootTables().size(),content.events().size(),result.strippedRoleBindings().size(),digest,true,"",content.encounterGroups().size()),content);
    }
    private static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 is unavailable",exception);}}
    private static String shortText(String value){return value==null?"Scenario content is unavailable.":value.substring(0,Math.min(512,value.length()));}
}
