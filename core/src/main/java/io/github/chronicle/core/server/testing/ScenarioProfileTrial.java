package io.github.chronicle.core.server.testing;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.books.*;
import io.github.chronicle.core.loot.*;
import io.github.chronicle.core.roles.*;
import io.github.chronicle.core.play.*;
import io.github.chronicle.core.scenario.*;
import io.github.chronicle.core.server.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.command.ServerCommandSource;

/** Called only through the existing opt-in, path/auth/port-guarded disposable role trial. */
final class ScenarioProfileTrial {
    private static final String ROLE="test:profile_role",TABLE="test:profile_loot",EVENT="test:profile_event";
    static void run(ServerCommandSource source,String verb)throws Exception{
        var server=source.getServer();
        require(ScenarioAuthoringProfiles.active().isPresent(),"custom profile not active");
        require(RuntimeHolds.reason(server)==null,"startup authority held: "+RuntimeHolds.reason(server));
        require(net.minecraft.registry.Registries.BLOCK.containsId(net.minecraft.util.Identifier.of("quiet_below:ancient_altar")),"installed Altar mechanics missing");
        require(ChronicleCore.observations().definitions().isEmpty()&&Departures.state(server).definitions().isEmpty(),"scenario observations/ship leaked into custom content");
        switch(verb){
            case "empty"->{require(PlayerRoles.definitions(server).isEmpty()&&BookRuntime.books(server).isEmpty()&&TypedChestLoot.types(server).isEmpty()&&EventRuntime.configurations(server).isEmpty()&&ScenarioEventTemplates.templates().isEmpty()&&Encounters.groups(server).isEmpty(),"fresh scenario is not empty");}
            case "seed"->{
                require(AuthoringRuntime.isAuthor(source.getPlayerOrThrow()),"normal server Editing required");
                var book=BookRuntime.create(source,new BookDefinition(BookDefinition.GENERATED_ID_ROOT+"draft","An authoring journal","A researcher","Profile proof",List.of("Test authoring content."),BookDefinition.Rule.OFF));
                PlayerRoles.create(source,new PlayerRole(ROLE,"Profile resident","Portable character",true,2,null,null,List.of(),List.of(book.id())));
                TypedChestLoot.createType(source,new LootType(TABLE,"Profile supplies","Portable loot",1,0,List.of(),List.of(new BookChance(book.id(),50))));
                var play=PlayRuntime.service(server);var def=new EventDefinition(EVENT,play.nextEventRevision(),"Profile event","Reusable message","An authored signal.","",ObservationCatalog.Audience.PARTICIPANTS,Optional.empty(),List.of(),Map.of());
                play.putEvent(new EventConfiguration(def,PlayState.Mode.MANUAL,Optional.empty(),play.elapsedTicks(),0,List.of(),false,EventSource.empty()),null,true);
            }
            case "export"->{
                try(var files=Files.list(ScenarioAuthoringProfiles.chronicleConfig().resolve("scenario-exports"))){var paths=files.toList();require(paths.size()==1,"profile export not present");var exported=ScenarioProfileStore.read(paths.getFirst());require(exported.identity().equals(ScenarioAuthoringProfiles.active().orElseThrow().identity()),"export changed identity");require(exported.content().roles().containsKey(ROLE)&&exported.content().books().size()==1&&exported.content().lootTables().containsKey(TABLE)&&exported.content().events().containsKey(EVENT),"effective authored content missing from export");require(exported.content().roles().get(ROLE).starting()==null&&!exported.content().events().get(EVENT).enabled(),"export leaked active map state");}
            }
            case "imported"->{require(PlayerRoles.definitions(server).containsKey(ROLE)&&BookRuntime.books(server).size()==1&&TypedChestLoot.types(server).containsKey(TABLE)&&ScenarioEventTemplates.get(EVENT).isPresent(),"profile import lost catalogs");require(EventRuntime.configurations(server).values().stream().noneMatch(EventConfiguration::enabled),"imported events enabled automatically");}
            case "edit"->{var book=BookRuntime.books(server).values().iterator().next();BookRuntime.save(source,new BookDefinition(book.id(),"Edited journal",book.author(),book.description(),book.pages(),book.rule()),book);PlayerRoles.delete(source,ROLE,PlayerRoles.definitions(server).get(ROLE));}
            case "edited"->{require(!PlayerRoles.definitions(server).containsKey(ROLE)&&BookRuntime.books(server).size()==1&&BookRuntime.books(server).values().iterator().next().title().equals("Edited journal"),"restart re-seeded deleted role or lost config edit");}
            default->throw new IllegalArgumentException("unknown trial");
        }
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
