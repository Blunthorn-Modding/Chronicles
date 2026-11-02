package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.scenario.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.WorldSavePath;

/** Startup-only content profiles. Each authoring environment keeps its own world and configuration. */
public final class ScenarioAuthoringProfiles {
    private static ScenarioProfile profile;
    private static boolean loaded;
    private ScenarioAuthoringProfiles(){}
    public static synchronized Optional<ScenarioProfile> active(){
        if(!loaded){Path file=file();try{profile=Files.exists(file,LinkOption.NOFOLLOW_LINKS)?ScenarioProfileStore.read(file):null;loaded=true;}
            catch(IOException error){throw new IllegalStateException("Cannot load custom scenario profile; existing files were preserved.",error);}}
        return Optional.ofNullable(profile);
    }
    public static Path chronicleConfig(){return FabricLoader.getInstance().getConfigDir().resolve("chronicle").toAbsolutePath().normalize();}
    private static Path file(){return chronicleConfig().resolve("scenario-profile.json");}
    public static Path contentFile(String name){
        if(!Set.of("books.json","chest-loot.json").contains(name))throw new IllegalArgumentException("Unknown portable content catalog.");
        return chronicleConfig().resolve(active().isPresent()?"profile-content/"+name:name);
    }
    public static void register(){
        active();
        // Registered before every other Core startup listener and every catalog freeze.
        ServerLifecycleEvents.SERVER_STARTING.register(server->{
            try{
                var current=active().orElse(null);
                if(Files.exists(file(),LinkOption.NOFOLLOW_LINKS)!=(current!=null)||(current!=null&&!ScenarioProfileStore.read(file()).equals(current)))throw new IOException("Scenario profile changed during startup; restart with the intended profile.");
                Path world=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
                ScenarioProfileContentStore.checkWorld(world,current);
                if(current==null){if(Files.exists(chronicleConfig().resolve("profile-content"),LinkOption.NOFOLLOW_LINKS))throw new IOException("This configuration contains custom scenario catalogs but its profile is missing.");return;}
                if(!server.isDedicated())throw new IOException("Custom authoring profiles require a separate dedicated server and configuration.");
                ScenarioContentRuntime.requireCompatibleMechanics(current.mechanics());
                if(!ChronicleCore.books().books().isEmpty()||!ChronicleCore.chestTypes().types().isEmpty()||!ChronicleCore.playerRoles().roles().isEmpty()||!ChronicleCore.observations().definitions().isEmpty()||!ScenarioEventTemplates.templates().isEmpty())throw new IOException("An installed scenario registered reusable defaults despite custom profile mode. Its mechanics need profile support before using this setup.");
                new ScenarioProfileContentStore(chronicleConfig()).initialize(current);
                ScenarioProfileContentStore.bindWorld(world,current);
                current.content().roles().values().forEach(ChronicleCore.playerRoles()::register);
                Encounters.registerDefaults(current.content().encounterGroups().values());
                current.content().events().values().forEach(ScenarioEventTemplates::register);
                ChronicleCore.LOGGER.info("Custom scenario profile ready: {} ({}) with installed mechanics {}",current.title(),current.content().scenarioId(),current.mechanics());
            }catch(IOException|RuntimeException error){throw new IllegalStateException("Custom scenario startup refused; existing world/configuration was preserved.",error);}
        });
    }
}
