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
    private static final Map<String,String> MECHANICS=new TreeMap<>();
    private ScenarioContentRuntime() { }
    public record Pinned(ScenarioExportPreview preview,ScenarioContent content) { }
    /** Explicit opt-in: this provider suppresses its reusable defaults when a profile is installed. */
    public static synchronized void registerScenario(String id,String title,String mechanics){
        new ScenarioContent(mechanics,Map.of(),Map.of(),Map.of(),Map.of());registerScenario(id,title);MECHANICS.put(id,mechanics);
    }
    static synchronized Set<String> compatibleMechanics(){
        if(!MECHANICS.keySet().containsAll(PROVIDERS.keySet()))throw new IllegalStateException("An installed scenario mod does not support empty custom authoring yet.");
        return Collections.unmodifiableSet(new TreeSet<>(MECHANICS.values()));
    }
    static void requireCompatibleMechanics(Set<String> required){if(!compatibleMechanics().equals(required))throw new IllegalStateException("This profile requires a different set of installed mechanics providers: "+required);}
    static java.nio.file.Path create(MinecraftServer server,String id,String title)throws IOException{
        if(PROVIDERS.containsKey(id)||ScenarioAuthoringProfiles.active().map(p->p.content().scenarioId().equals(id)).orElse(false))throw new IllegalArgumentException("Choose a new custom scenario ID, distinct from the active or installed scenarios.");
        var empty=ScenarioProfile.empty(id,title,compatibleMechanics());return new ScenarioProfileStore(ScenarioAuthoringProfiles.chronicleConfig()).create(empty);
    }
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
        Map<String,String> visible=ScenarioAuthoringProfiles.active().map(p->Map.of(p.content().scenarioId(),p.title())).orElseGet(()->Map.copyOf(PROVIDERS));
        try {int roles=PlayerRoles.definitions(server).size(),books=BookRuntime.books(server).size(),loot=TypedChestLoot.types(server).size(),events=eventCount(server),groups=Encounters.groups(server).size();return visible.entrySet().stream().map(entry->new ScenarioExportPreview(entry.getKey(),entry.getValue(),roles,books,loot,events,0,"",true,"",groups)).toList();}
        catch(RuntimeException exception){return visible.entrySet().stream().map(entry->new ScenarioExportPreview(entry.getKey(),entry.getValue(),0,0,0,0,0,"",false,shortText(exception.getMessage()))).toList();}
    }
    private static int eventCount(MinecraftServer server) {
        var ids=new HashSet<>(PlayRuntime.service(server).state().scenarioDrafts());
        ids.addAll(EventRuntime.configurations(server).keySet());
        return ids.size();
    }
    static synchronized Pinned open(MinecraftServer server,String providerId) {
        var custom=ScenarioAuthoringProfiles.active();if(custom.isPresent()){if(!custom.get().content().scenarioId().equals(providerId))throw new IllegalArgumentException("Choose the active custom scenario.");return capture(server,providerId,custom.get().title());}
        String title=PROVIDERS.get(providerId);if(title==null)throw new IllegalArgumentException("Choose a registered scenario content provider.");
        return capture(server,providerId,title);
    }
    static String export(MinecraftServer server,Pinned pinned) throws IOException {
        if(pinned==null)throw new IllegalStateException("Open Scenario content and review the current definitions first.");
        Pinned current=open(server,pinned.preview().providerId());
        if(!MessageDigest.isEqual(pinned.preview().digest().getBytes(StandardCharsets.US_ASCII),current.preview().digest().getBytes(StandardCharsets.US_ASCII)))throw new IllegalStateException("Scenario definitions changed. Review the current content before exporting.");
        var custom=ScenarioAuthoringProfiles.active();if(custom.isPresent())return new ScenarioProfileStore(ScenarioAuthoringProfiles.chronicleConfig()).export(custom.get().withContent(pinned.content())).toString();
        ScenarioDraftStore store=new ScenarioDraftStore(FabricLoader.getInstance().getConfigDir().resolve("chronicle"),pinned.content().scenarioId());
        store.load(); // Establishes the observed revision and rejects corrupt/external draft data before overwrite.
        store.save(pinned.content());
        return store.export(pinned.content()).toString();
    }
    private static Pinned capture(MinecraftServer server,String id,String title) {
        var result=ScenarioContentCapture.capture(id,PlayerRoles.definitions(server),BookRuntime.books(server),TypedChestLoot.types(server),EventRuntime.configurations(server),Encounters.groups(server),ChronicleRuntime.service(server).state().flags());
        var captured=result.content();
        var events=new TreeMap<String,EventTemplate>();
        for(String draft:PlayRuntime.service(server).state().scenarioDrafts())events.put(draft,ScenarioEventTemplates.get(draft).orElseThrow(()->new IllegalStateException("Restore the scenario mod defining Event "+draft+" before exporting.")));
        events.putAll(captured.events());
        var content=new ScenarioContent(captured.scenarioId(),captured.roles(),captured.books(),captured.lootTables(),events,captured.encounterGroups(),captured.flags());
        String digest=digest(ScenarioContentJson.encode(content));
        return new Pinned(new ScenarioExportPreview(id,title,content.roles().size(),content.books().size(),content.lootTables().size(),content.events().size(),result.strippedRoleBindings().size(),digest,true,"",content.encounterGroups().size()),content);
    }
    private static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 is unavailable",exception);}}
    private static String shortText(String value){return value==null?"Scenario content is unavailable.":value.substring(0,Math.min(512,value.length()));}
}
