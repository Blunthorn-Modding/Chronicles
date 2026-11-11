package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.play.EventConfiguration;
import io.github.chronicle.core.scenario.EventTemplate;
import io.github.chronicle.core.scenario.ScenarioEventTemplates;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import java.util.*;

/** First-use scenario setup. It never dispatches gameplay effects or replaces saved configurations. */
public final class ScenarioDefaultEvents {
    private static final String HOLD="chronicle_core:scenario-defaults";
    private static final Set<MinecraftServer> INITIALIZED=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<MinecraftServer> FAILED=Collections.newSetFromMap(new IdentityHashMap<>());
    private ScenarioDefaultEvents(){}
    public static void register(){
        ServerTickEvents.START_SERVER_TICK.register(ScenarioDefaultEvents::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{INITIALIZED.remove(server);FAILED.remove(server);});
    }
    private static void tick(MinecraftServer server){
        if(FAILED.contains(server)||RuntimeHolds.reason(server)!=null)return;
        boolean first=!INITIALIZED.contains(server);
        if(!first&&(server.getTicks()%20!=0||ChronicleRuntime.service(server).state().phase()==SessionPhase.ACTIVE
                ||PlayRuntime.service(server).state().scenarioDrafts().isEmpty()))return;
        try{
            initialize(server);
            INITIALIZED.add(server);
        }catch(EventSources.Unavailable sourceFailure){/* The source service already owns its exact recovery hold. */}
        catch(Exception failure){if(RuntimeHolds.reason(server)==null){FAILED.add(server);RuntimeHolds.hold(server,HOLD,"Scenario defaults could not be initialized. Existing setup was preserved; repair the reported storage failure and restart.");}ChronicleCore.LOGGER.error("Scenario default initialization stopped; existing world data retained",failure);}
    }
    static void initialize(MinecraftServer server)throws java.io.IOException{
        var templates=ScenarioEventTemplates.templates();var flags=new TreeMap<>(ScenarioEventTemplates.flags());
        for(var template:templates.values()){
            template.requiredFlags().forEach(id->flags.putIfAbsent(id,false));
            template.flagOutcomes().keySet().forEach(id->flags.putIfAbsent(id,false));
        }
        ChronicleRuntime.service(server).ensureDefaultFlags(new Actor("Scenario defaults",null),flags);
        var play=PlayRuntime.service(server);var state=play.state();
        var ready=new TreeMap<String,EventConfiguration>();
        boolean resolveDrafts=ChronicleRuntime.service(server).state().phase()!=SessionPhase.ACTIVE;
        for(var template:templates.values())if(!state.scenarioDefaults().contains(template.id())||resolveDrafts&&state.scenarioDrafts().contains(template.id())){
            if(EventRuntime.configurations(server).containsKey(template.id()))continue;
            try{ready.put(template.id(),resolve(server,template.id()));}
            catch(IllegalArgumentException|IllegalStateException incomplete){/* Listed with a diagnostic; no partial configuration is installed. */}
        }
        Set<String> reserved=new TreeSet<>();ChronicleCore.observations().definitions().forEach(d->reserved.add(d.id()));
        reserved.addAll(EventProgressRuntime.reservedEventIds(server));
        reserved.addAll(EventOccurrenceRuntime.reservedEventIds(server));
        reserved.retainAll(templates.keySet());
        for(String id:play.bootstrapScenarioEvents(ready,templates.keySet(),reserved))EventProgressRuntime.arm(server,play.state().events().get(id));
    }
    private static EventConfiguration resolve(MinecraftServer server,String id){
        return EventRuntime.resolveForm(server,id,CoreEventMenu.templateForm(server,id));
    }
    static boolean draft(MinecraftServer server,String id){return PlayRuntime.service(server).state().scenarioDrafts().contains(id);}
    static Optional<String> issue(MinecraftServer server,String id){
        try{
            if(draft(server,id)){
                if(ScenarioEventTemplates.get(id).isEmpty())return Optional.of("Install the scenario mod that defines this Event.");
                if(EventRuntime.configurations(server).size()>=io.github.chronicle.core.play.ObservationCatalog.MAX_DEFINITIONS)return Optional.of("The Event limit has been reached. Remove an unused Event first.");
                resolve(server,id);
                return Optional.of("Waiting for non-active play to finish this Event's setup.");
            }
            EventRuntime.validateSetup(server,EventRuntime.configuration(server,id));return Optional.empty();
        }catch(IllegalArgumentException|IllegalStateException incomplete){return Optional.of(message(incomplete));}
    }
    static Optional<String> flagIssue(MinecraftServer server,String flag){
        var updaters=new TreeSet<String>();
        EventRuntime.configurations(server).forEach((id,c)->{if(c.definition().flagChanges().containsKey(flag))updaters.add(id);});
        for(String id:PlayRuntime.service(server).state().scenarioDrafts())ScenarioEventTemplates.get(id).filter(t->t.flagOutcomes().containsKey(flag)).ifPresent(t->updaters.add(id));
        for(String id:updaters){var problem=issue(server,id);if(problem.isPresent())return Optional.of(label(server,id)+": "+problem.get());}
        return Optional.empty();
    }
    static String label(MinecraftServer server,String id){var c=EventRuntime.configurations(server).get(id);return c==null?ScenarioEventTemplates.get(id).map(EventTemplate::label).orElse(id):c.definition().label();}
    private static String message(RuntimeException error){String text=error.getMessage();return text==null||text.isBlank()?"Review the Event's map bindings.":text.substring(0,Math.min(400,text.length()));}
}
