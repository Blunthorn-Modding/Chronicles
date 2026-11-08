package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.areas.AreaChange;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.encounters.EncounterTarget;
import io.github.chronicle.core.menu.CoreMenuModel.Action;
import io.github.chronicle.core.menu.CoreMenuModel.Control;
import io.github.chronicle.core.menu.CoreMenuModel.Detail;
import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import io.github.chronicle.core.menu.EventEditorForm;
import io.github.chronicle.core.play.EventConfiguration;
import io.github.chronicle.core.play.EventExecution;
import io.github.chronicle.core.play.EventRegionChange;
import io.github.chronicle.core.play.EventEncounterAction;
import io.github.chronicle.core.play.ObservationCatalog;
import io.github.chronicle.core.play.PlayState;
import io.github.chronicle.core.scenario.EventTemplate;
import io.github.chronicle.core.scenario.ScenarioEventTemplates;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Unified Event list, detail, editor projection and finite selector catalogs. */
final class CoreEventMenu {
    private CoreEventMenu() { }

    /** Creation belongs to the Events list, independent of its selected event kind. */
    static Detail withCreator(ServerPlayerEntity player,Detail selected){
        if(selected.controls().stream().anyMatch(c->c.action()==Action.EVENT_NEW))return selected;
        List<Control> controls=new ArrayList<>(detail(player,"",false).controls());controls.addAll(selected.controls());
        return new Detail(selected.id(),selected.title(),selected.description(),selected.facts(),controls,
                selected.loot(),selected.event(),selected.region(),selected.book(),selected.lore(),selected.role(),
                selected.roleAssignment(),selected.area(),selected.departure());
    }

    static List<Entry> entries(MinecraftServer server) {
        var entries=new ArrayList<>(EventRuntime.configurations(server).values().stream()
                .map(configuration -> {
                    var definition = configuration.definition();
                    String summary = definition.description().isBlank() ? definition.text() : definition.description();
                    var problem=ScenarioDefaultEvents.issue(server,definition.id());
                    return entry("cue:" + definition.id(), (problem.isPresent()?"! ":"")+definition.label(), problem.map(p->"Needs setup: "+p).orElse(summary),
                            "CUE", definition.id(), definition.revision());
                })
                .sorted(Comparator.comparing(Entry::label).thenComparing(Entry::id))
                .toList());
        for(String id:PlayRuntime.service(server).state().scenarioDrafts())entries.add(entry("cue:"+id,"! "+ScenarioDefaultEvents.label(server,id),"Needs setup: "+ScenarioDefaultEvents.issue(server,id).orElse("Review this Event's map setup."),"CUE",id,0));
        entries.sort(Comparator.comparing(Entry::label).thenComparing(Entry::id));return List.copyOf(entries);
    }

    static EventEditorForm emptyForm() {
        return new EventEditorForm("chronicle_core:event", 0, "", "", "", "",
                ObservationCatalog.Audience.PARTICIPANTS.name(), "", 0,
                PlayState.Mode.MANUAL.name(), "", 0, 0, "", List.of(), true);
    }

    static EventEditorForm form(MinecraftServer server, String id) {
        if(ScenarioDefaultEvents.draft(server,id))return templateForm(server,id);
        EventConfiguration configuration = EventRuntime.configuration(server, id);
        var definition = configuration.definition();
        Optional<io.github.chronicle.core.play.EventRegionReference> audience = definition.audienceRegion();
        Optional<io.github.chronicle.core.play.EventRegionReference> trigger = configuration.triggerRegion();
        List<EventEditorForm.Change> changes = definition.changes().stream()
                .map(change -> change(server, change)).toList();
        List<EventEditorForm.EncounterAction> encounterActions=definition.encounterActions().stream()
                .map(action->encounterAction(server,action)).toList();
        return new EventEditorForm(definition.id(), definition.revision(), definition.label(),
                definition.description(), definition.text(), definition.sound(), definition.audience().name(),
                audience.map(io.github.chronicle.core.play.EventRegionReference::id).orElse(""),
                audience.map(io.github.chronicle.core.play.EventRegionReference::revision).orElse(0L),
                configuration.mode().name(),
                trigger.map(io.github.chronicle.core.play.EventRegionReference::id).orElse(""),
                trigger.map(io.github.chronicle.core.play.EventRegionReference::revision).orElse(0L),
                configuration.delayTicks() / 20,
                configuration.requiredFlags(), changes, false,definition.flagChanges(),configuration.source(),encounterActions,configuration.occurrencePolicy().name());
    }

    static EventEditorForm templateForm(MinecraftServer server,String id){
        EventTemplate template=ScenarioEventTemplates.get(id).orElseThrow(()->new IllegalArgumentException("Choose an available scenario Event template."));
        if(EventRuntime.configurations(server).containsKey(id))throw new IllegalStateException("An Event already uses this template ID.");
        var sites=AuthoringRuntime.get(server).service().state().sites();
        String audience=template.audienceReference();long audienceRevision=0;
        if(!audience.isEmpty()){AuthoringSite site=sites.get(audience);if(site instanceof AuthoringSite.Region region)audienceRevision=region.revision();else audience="";}
        String trigger=template.triggerReference();long triggerRevision=0;
        if(!trigger.isEmpty()){AuthoringSite site=sites.get(trigger);if(site instanceof AuthoringSite.Region region)triggerRevision=region.revision();else trigger="";}
        List<EventEditorForm.Change> changes=new ArrayList<>();
        for(EventTemplate.RegionOutcome outcome:template.regionOutcomes())changes.add(templateChange(server,outcome));
        List<EventEditorForm.EncounterAction> encounters=template.encounterOutcomes().stream().map(outcome->templateEncounter(server,outcome)).toList();
        return new EventEditorForm(template.id(),0,template.label(),template.description(),template.text(),template.sound(),template.audience().name(),
                audience,audienceRevision,template.mode().name(),trigger,triggerRevision,template.delayTicks()/20,template.requiredFlags(),changes,true,
                template.flagOutcomes(),template.source(),encounters,template.occurrencePolicy().name());
    }

    static Detail detail(ServerPlayerEntity player, String selected, boolean reviewed) {
        return detail(player,selected,reviewed,false,false);
    }
    static Detail detail(ServerPlayerEntity player, String selected, boolean reviewed, boolean sourceReviewed) {
        return detail(player,selected,reviewed,sourceReviewed,false);
    }
    static Detail detail(ServerPlayerEntity player, String selected, boolean reviewed, boolean sourceReviewed,boolean encounterReviewed) {
        MinecraftServer server = player.getEntityWorld().getServer();
        boolean editing = AuthoringRuntime.isAuthor(player);
        boolean canEdit = player.getCommandSource().hasPermissionLevel(2) && editing && AuthoringRuntime.editable(server) && !held(server);
        boolean active = ChronicleRuntime.service(server).state().phase().name().equals("ACTIVE");
        String editReason = held(server)?"Finish pending operations before editing Events.":active ? "Pause play to edit Events." : "Enable editing to change Events.";
        ArrayList<Fact> facts = new ArrayList<>();
        ArrayList<Control> controls = new ArrayList<>();
        boolean room = EventRuntime.configurations(server).size() < ObservationCatalog.MAX_DEFINITIONS;
        controls.add(control(Action.EVENT_NEW, "Create new event", canEdit && !held(server) && room,
                !room ? "The Event limit has been reached." : held(server) ? "Finish pending operations first." : editReason,
                false, ""));
        if (selected == null || selected.isBlank()) {
            return new Detail("", "Events", "Choose when Events run, their required flags, and their text, sound, saved Region, encounter-spawn, or flag outcomes.",
                    facts, controls, null, null, null);
        }
        String id = raw(selected);
        if(ScenarioDefaultEvents.draft(server,id)){
            controls.add(control(Action.EVENT_EDIT,"Edit event",canEdit&&room&&ScenarioEventTemplates.get(id).isPresent(),editReason,false,""));
            var template=ScenarioEventTemplates.get(id);
            if(template.isPresent()&&(!template.get().text().isBlank()||!template.get().sound().isEmpty()))controls.add(control(Action.EVENT_PREVIEW,"Preview privately",true,"",false,""));
            controls.add(control(Action.EVENT_DELETE,"Delete event",canEdit,editReason,true,""));
            return new Detail(selected,ScenarioDefaultEvents.label(server,id),"Needs setup: "+ScenarioDefaultEvents.issue(server,id).orElse("Review the map bindings.")+template.map(t->" "+t.description()).orElse(""),facts,controls,null,null,null);
        }
        EventConfiguration configuration = EventRuntime.configuration(server, id);
        var setupIssue=ScenarioDefaultEvents.issue(server,id);
        var definition = configuration.definition();
        PlayState play = PlayRuntime.service(server).state();
        EventExecution execution = play.executions().get(id);
        boolean hidden = execution != null && execution.source() == PlayState.DeliverySource.POOL && execution.phase() != EventExecution.Phase.COMPLETE;
        if (hidden) execution = null;
        Optional<Encounters.Review> encounterReview=hidden ? Optional.empty() : Encounters.review(server,id);
        PlayState.Delivery delivery = hidden ? null : play.deliveries().get(id);
        boolean custom = EventRuntime.stored(server, id) != null && ChronicleCore.observations().get(id).isEmpty();
        boolean unresolved = execution != null && execution.phase() != EventExecution.Phase.COMPLETE;
        boolean available = execution == null && delivery == null;
        var pendingSource=EventSources.pending(server).stream().filter(EventSources::recoverable).filter(receipt->receipt.type().equals(configuration.source().type())
                &&(configuration.source().target().equals("*")||configuration.source().target().equals(receipt.target()))
                &&(configuration.source().activity().isEmpty()||configuration.source().activity().equals(receipt.activity()))).findFirst();

        facts.add(new Fact("Event ID", id));
        facts.add(new Fact("Message", definition.text().isBlank() ? "None" : definition.text()));
        facts.add(new Fact("Sound", definition.sound().isBlank() ? "None" : definition.sound()));
        facts.add(new Fact("Audience", definition.audience() == ObservationCatalog.Audience.REGION
                ? "Participants inside " + definition.audienceRegion().map(r -> r.id() + " @ " + r.revision()).orElse("missing Region")
                : "All eligible participants"));
        facts.add(new Fact("Trigger", configuration.mode() + triggerSummary(configuration)));
        facts.add(new Fact("Required flags", configuration.requiredFlags().isEmpty()?"None":String.join(", ",configuration.requiredFlags())));
        facts.add(new Fact("Flag updates", definition.flagChanges().toString()));
        facts.add(new Fact("Progression", configuration.mode() == PlayState.Mode.MANUAL && configuration.occurrencePolicy() == io.github.chronicle.core.play.EventOccurrencePolicy.ONCE && configuration.delayTicks() == 0 ? "Manual Event" : EventProgressRuntime.status(server,configuration)));
        if(configuration.mode()==PlayState.Mode.SYSTEM)facts.add(new Fact("Activity source", configuration.source().toString()));
        facts.add(new Fact("Region changes", definition.changes().isEmpty() ? "None" : definition.changes().stream()
                .map(change -> change.region().id() + " -> " + change.state()).reduce((left, right) -> left + "; " + right).orElse("None")));
        facts.add(new Fact("Encounter spawns",definition.encounterActions().isEmpty()?"None":definition.encounterActions().stream()
                .map(action->action.groupId()+action.targetOverride().map(target->" @ "+target.kind()+" "+target.siteId()+" / "+target.siteRevision()).orElse(" @ group target"))
                .reduce((left,right)->left+"; "+right).orElse("None")));
        if(encounterReview.isPresent()){
            var child=encounterReview.orElseThrow().child();long planned=child.entries().stream().filter(entry->entry.stage()==io.github.chronicle.core.encounters.EncounterSpawnEntry.Stage.PLANNED).count(),claimed=child.entries().stream().filter(entry->entry.stage()==io.github.chronicle.core.encounters.EncounterSpawnEntry.Stage.CLAIMED).count(),spawned=child.entries().stream().filter(entry->entry.stage()==io.github.chronicle.core.encounters.EncounterSpawnEntry.Stage.SPAWNED).count();
            facts.add(new Fact("Parent Event operation",encounterReview.orElseThrow().eventOperation().toString()));facts.add(new Fact("Encounter child",child.operationId()+" / "+child.groupId()));facts.add(new Fact("Composition digest",child.groupFingerprint()));facts.add(new Fact("Exact plan hash",child.planHash()));facts.add(new Fact("Retained stages","Planned "+planned+", claimed "+claimed+", spawned "+spawned));
            for(var entry:child.entries())facts.add(new Fact("Entity UUID",entry.entityId()+" / "+entry.entityType()+" / "+entry.stage()+" / "+entry.dimension()+" "+entry.x()+", "+entry.y()+", "+entry.z()));
            boolean reviewable=player.getCommandSource().hasPermissionLevel(2)&&RuntimeHolds.reason(server)!=null&&!RuntimeHolds.heldByOther(server,EventRuntime.HOLD);
            controls.add(control(Action.EVENT_ENCOUNTER_REVIEW,child.terminal()==io.github.chronicle.core.encounters.EncounterOperation.Terminal.UNCERTAIN?"Review uncertain encounter result":"Review retained encounter plan",reviewable,"Requires this Event's hold and no unrelated Chronicle hold.",false,""));
            if(encounterReviewed)controls.add(control(Action.EVENT_ENCOUNTER_ACCEPT,child.terminal()==io.github.chronicle.core.encounters.EncounterOperation.Terminal.UNCERTAIN?"Accept current encounter result":"Skip remaining encounter spawns",reviewable&&(child.terminal()!=io.github.chronicle.core.encounters.EncounterOperation.Terminal.OPEN||claimed==0),claimed>0&&child.terminal()==io.github.chronicle.core.encounters.EncounterOperation.Terminal.OPEN?"Use exact Retry first to validate retained claims; missing claims require uncertain-result review.":"Requires this Event's hold and no unrelated Chronicle hold.",true,""));
        }
        facts.add(new Fact("Completion", execution != null ? execution.phase() + " - " + execution.detail()
                : delivery != null ? delivery.status() + " - " + delivery.recipients().size() + " recipients" : "Not run"));
        facts.add(new Fact("Configuration", configuration.enabled() ? "Enabled" : "Not configured for live execution"));
        if(pendingSource.isPresent()){
            var receipt=pendingSource.get();facts.add(new Fact("Uncertain source",receipt.id()+" / "+receipt.authority()));
            controls.add(control(Action.EVENT_SOURCE_REVIEW,"Review source recovery",!active,"Pause play before reviewing source recovery.",false,""));
            if(sourceReviewed){
                controls.add(control(Action.EVENT_SOURCE_COMPLETE,"Record activity as happened",!active,"Pause play before recovery.",true,""));
                controls.add(control(Action.EVENT_SOURCE_CANCEL,"Record activity as not happened",!active,"Pause play before recovery.",true,""));
            }
        }
        if(EventSources.reloadAvailable(server))
            controls.add(control(Action.EVENT_SOURCE_RELOAD,"Reload source evidence",!active,"Pause play before reloading repaired evidence.",true,""));

        controls.add(control(Action.EVENT_EDIT, "Edit event", canEdit && !unresolved,
                unresolved ? "Resolve the current Event execution before editing it." : editReason, false, ""));
        if (!definition.text().isBlank() || !definition.sound().isBlank()) {
            controls.add(control(Action.EVENT_PREVIEW, "Preview privately", true, "", false, ""));
        }
        controls.add(control(Action.EVENT_CHECK, "Check audience", true, "", false, ""));
        controls.add(control(Action.EVENT_REVIEW, "Review run", active && !held(server) && configuration.enabled() && available && setupIssue.isEmpty(),
                !active ? "Start play before running an Event." : held(server) ? "Resolve the current operation first."
                        : !configuration.enabled() ? "Configure this Event before running it."
                        : !available ? "Allow the Event again after its current run is resolved." : setupIssue.map(p->"Needs setup: "+p).orElse(""),
                false, ""));
        if (reviewed) controls.add(control(Action.EVENT_RUN, "Run event", true, "", true, ""));
        if (configuration.enabled() && configuration.mode() != PlayState.Mode.MANUAL) {
            controls.add(control(Action.EVENT_DISABLE_AUTO, "Disable automatic trigger", canEdit && !unresolved, unresolved?"Resolve the current Event execution first.":editReason, true, ""));
        }
        if (configuration.enabled() && !custom) {
            controls.add(control(Action.EVENT_REMOVE, "Remove event configuration", canEdit && !unresolved, unresolved?"Resolve the current Event execution first.":editReason, true, ""));
        }
        if (execution != null && execution.phase() == EventExecution.Phase.MEDIA_UNCERTAIN) {
            controls.add(control(Action.EVENT_ACK_MEDIA, "Acknowledge uncertain delivery", true, "", true, ""));
        } else if (execution != null && !execution.terminal()) {
            controls.add(control(Action.EVENT_RECOVER, execution.phase()==EventExecution.Phase.ENCOUNTER_EFFECTS?"Retry exact encounter plan":"Recover event", execution.phase()==EventExecution.Phase.ENCOUNTER_EFFECTS||active, "Restore active play before recovery.", true, ""));
        }
        var progress=hidden ? null : EventProgressRuntime.entry(server,configuration);
        if (delivery != null || execution != null && execution.phase() == EventExecution.Phase.COMPLETE || progress!=null&&(progress.skipped()||progress.triggered()&&execution==null)) {
            controls.add(control(Action.EVENT_RESET, execution==null&&delivery==null?"Reset pending trigger":"Allow event again", canEdit, editReason, true, ""));
        }
        if (custom) controls.add(control(Action.EVENT_DELETE, "Delete event", canEdit && !unresolved,
                unresolved ? "Resolve the current Event execution before deleting it." : editReason, true, ""));

        String description = definition.description().isBlank()
                ? "A player-facing Event with optional media, saved Region changes, encounter spawns, and flag outcomes."
                : definition.description();
        if(execution!=null&&execution.phase()==EventExecution.Phase.ENCOUNTER_EFFECTS&&encounterReview.isPresent()){
            var child=encounterReview.orElseThrow().child();
            description="Encounter "+child.groupId()+" is retained: "+child.terminal()+". "+EventRuntime.failureReason(server).orElse("Inspect the retained plan before resolving it.");
            if(child.terminal()==io.github.chronicle.core.encounters.EncounterOperation.Terminal.OPEN&&child.entries().stream().anyMatch(entry->entry.stage()==io.github.chronicle.core.encounters.EncounterSpawnEntry.Stage.CLAIMED))description="This encounter retains a claimed entity. Use exact Retry first to validate that claim. A missing claimed entity becomes uncertain and can then be explicitly reviewed; it is never respawned automatically.";
            else if(child.terminal()==io.github.chronicle.core.encounters.EncounterOperation.Terminal.OPEN)description+=" Clear the exact planned space, then retry; or review and confirm skipping its remaining spawns. Retry while paused waits for active play.";
        }
        if(execution!=null&&execution.phase()==EventExecution.Phase.ENCOUNTER_EFFECTS&&encounterReview.isEmpty()&&EventRuntime.failureReason(server).isPresent())description="Encounter planning needs recovery: "+EventRuntime.failureReason(server).orElseThrow()+". No new encounter claim was made. Clear the target Region, then retry its new plan.";
        if(EventProgressRuntime.legacyReviewRequired(configuration))description+=" Review and save this timer to enable its flag-based countdown.";
        if(setupIssue.isPresent())description="Needs setup: "+setupIssue.get()+" "+description;
        return new Detail(selected, definition.label(), description, facts, controls, null, null, null);
    }

    static List<Entry> catalog(MinecraftServer server, String kind) {
        ArrayList<Entry> entries = new ArrayList<>();
        switch (kind) {
            case "EVENT_SOUND" -> Registries.SOUND_EVENT.getIds().stream().sorted()
                    .forEach(id -> {var bundled=EventSoundRegistry.metadata(id.toString());entries.add(entry(id.toString(),bundled.map(io.github.chronicle.core.sound.BundledSoundCatalog.Sound::label).orElse(id.toString()),bundled.map(sound->sound.title()+" by "+sound.creator()+" — "+sound.id()).orElse("Registered sound"),"SOUND",id.toString(),0));});
            case "EVENT_REGION" -> AuthoringRuntime.get(server).service().state().sites().values().stream()
                    .filter(AuthoringSite.Region.class::isInstance).map(AuthoringSite.Region.class::cast)
                    .sorted(Comparator.comparing(AuthoringSite.Region::id))
                    .forEach(region -> entries.add(entry(region.id(), region.id(), describe(region), "REGION", region.id(), region.revision())));
            case "EVENT_POINT" -> AuthoringRuntime.get(server).service().state().sites().values().stream()
                    .filter(AuthoringSite.Location.class::isInstance).map(AuthoringSite.Location.class::cast)
                    .sorted(Comparator.comparing(AuthoringSite.Location::id))
                    .forEach(point->entries.add(entry(point.id(),point.id(),point.dimension()+" - "+point.x()+" "+point.y()+" "+point.z(),"POINT",point.id(),point.revision())));
            case "EVENT_ENCOUNTER_GROUP" -> Encounters.snapshot(server).groups().values().stream()
                    .sorted(Comparator.comparing(io.github.chronicle.core.encounters.EncounterGroup::label).thenComparing(io.github.chronicle.core.encounters.EncounterGroup::id))
                    .forEach(group->{var binding=Encounters.snapshot(server).bindings().get(group.id());boolean current=binding!=null&&current(server,binding.target());entries.add(entry(group.id(),group.label(),group.description()+" ("+group.entityCount()+" entities; "+(current?"group target ready":"choose an Event target override")+")","ENCOUNTER_GROUP",group.id(),current?binding.siteRevision():0));});
            case "EVENT_TEMPLATE" -> ScenarioEventTemplates.templates().values().stream()
                    .filter(template->!EventRuntime.configurations(server).containsKey(template.id()))
                    .sorted(Comparator.comparing(EventTemplate::label).thenComparing(EventTemplate::id))
                    .forEach(template->entries.add(entry(template.id(),template.label(),template.description(),"EVENT_TEMPLATE",template.id(),0)));
            case "EVENT_FLAG" -> {
                entries.add(entry("none", "No prerequisite", "Create flags under Advanced → Flags. A required flag must be On.", "FLAG", "", 0));
                ChronicleRuntime.service(server).state().flags().entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .forEach(flag -> entries.add(entry(flag.getKey(), flag.getKey(), "Current value: " + (flag.getValue()?"On":"Off") + ". Manage under Advanced → Flags.", "FLAG", flag.getKey(), 0)));
            }
            case "EVENT_STATE" -> AreaChanges.catalog(server).areas().values().stream()
                    .sorted(Comparator.comparing(AreaChange::name).thenComparing(AreaChange::id))
                    .forEach(area -> addStates(server, entries, area));
            default -> throw new IllegalArgumentException("Unsupported Event selector: " + kind);
        }
        return List.copyOf(entries);
    }

    private static void addStates(MinecraftServer server, List<Entry> entries, AreaChange area) {
        try {
            if (!area.bounds().matches(AreaChanges.currentRegion(server, area))) return;
        } catch (RuntimeException missing) { return; }
        for (AreaState state : AreaState.values()) area.snapshot(state).ifPresent(snapshot -> entries.add(entry(
                area.bounds().regionId() + "|" + state.name(), area.name() + " - " + state.name(),
                area.bounds().dimension() + " - " + area.bounds().cells() + " blocks", "STATE",
                snapshot.sha256(), area.revision())));
    }

    private static EventEditorForm.Change change(MinecraftServer server, EventRegionChange change) {
        AreaChange area = AreaChanges.catalog(server).areas().get(change.areaId());
        boolean current = false;
        String problem = "";
        String label = change.region().id() + " - " + change.state();
        if (area == null) problem = "Saved states are missing for this Region.";
        else {
            label = area.name() + " - " + change.state();
            try {
                if (area.revision() != change.areaRevision()) problem = "The saved-state revision changed.";
                else if (!area.bounds().matches(AreaChanges.currentRegion(server, area))) problem = "The Region bounds changed.";
                else if (!area.requireSnapshot(change.state()).equals(change.snapshot())) problem = "The selected state was recaptured.";
                else current = true;
            } catch (RuntimeException unavailable) {
                problem = unavailable.getMessage() == null ? "The selected state is unavailable." : unavailable.getMessage();
            }
        }
        return new EventEditorForm.Change(change.region().id(), shortText(label,160), change.state().name(), change.areaRevision(),
                change.snapshot().sha256(), change.snapshot().bounds().dimension(), current, shortText(problem, 512));
    }

    private static EventEditorForm.Change templateChange(MinecraftServer server,EventTemplate.RegionOutcome outcome){
        AreaChange area=AreaChanges.catalog(server).areas().get(outcome.areaId());
        if(area==null)area=AreaChanges.forRegion(server,outcome.regionId()).orElse(null);
        if(area==null)return new EventEditorForm.Change(outcome.regionId(),outcome.regionId()+" - "+outcome.state(),outcome.state(),0,"","",false,"Saved states are missing for this scenario Region.");
        try{
            AreaState state=AreaState.valueOf(outcome.state());var snapshot=area.requireSnapshot(state);
            boolean current=area.bounds().regionId().equals(outcome.regionId())&&area.bounds().matches(AreaChanges.currentRegion(server,area));
            return new EventEditorForm.Change(outcome.regionId(),area.name()+" - "+outcome.state(),outcome.state(),area.revision(),snapshot.sha256(),area.bounds().dimension(),current,current?"":"The scenario Region or saved state is not current on this map.");
        }catch(RuntimeException unavailable){return new EventEditorForm.Change(outcome.regionId(),area.name()+" - "+outcome.state(),outcome.state(),area.revision(),"",area.bounds().dimension(),false,shortText(unavailable.getMessage(),512));}
    }

    private static EventEditorForm.EncounterAction templateEncounter(MinecraftServer server,EventTemplate.EncounterOutcome outcome){
        if(outcome.targetKind().equals("GROUP"))return encounterAction(server,new EventEncounterAction(outcome.groupId(),Optional.empty()));
        AuthoringSite site=AuthoringRuntime.get(server).service().state().sites().get(outcome.targetReference());
        boolean right=outcome.targetKind().equals("POINT")?site instanceof AuthoringSite.Location:site instanceof AuthoringSite.Region;
        long revision=right?site.revision():0;
        return new EventEditorForm.EncounterAction(outcome.groupId(),groupLabel(server,outcome.groupId()),outcome.targetKind(),outcome.targetReference(),outcome.targetReference(),revision,
                right&&Encounters.snapshot(server).groups().containsKey(outcome.groupId()),right?"":"The scenario map target is missing or has the wrong kind.");
    }

    private static EventEditorForm.EncounterAction encounterAction(MinecraftServer server,EventEncounterAction action){
        var snapshot=Encounters.snapshot(server);boolean group=snapshot.groups().containsKey(action.groupId());
        if(action.targetOverride().isPresent()){
            EncounterTarget target=action.targetOverride().orElseThrow();AuthoringSite site=AuthoringRuntime.get(server).service().state().sites().get(target.siteId());
            boolean current=group&&site!=null&&site.revision()==target.siteRevision()&&(target.kind()==EncounterTarget.Kind.POINT?site instanceof AuthoringSite.Location:site instanceof AuthoringSite.Region);
            return new EventEditorForm.EncounterAction(action.groupId(),groupLabel(server,action.groupId()),target.kind().name(),target.siteId(),target.siteId(),target.siteRevision(),current,current?"":"The encounter group or exact map target changed.");
        }
        var binding=snapshot.bindings().get(action.groupId());boolean current=group&&binding!=null&&current(server,binding.target());
        return new EventEditorForm.EncounterAction(action.groupId(),groupLabel(server,action.groupId()),"GROUP","","",0,current,current?"":"The encounter group binding is missing or changed.");
    }
    private static boolean current(MinecraftServer server,EncounterTarget target){AuthoringSite site=AuthoringRuntime.get(server).service().state().sites().get(target.siteId());return site!=null&&site.revision()==target.siteRevision()&&(target.kind()==EncounterTarget.Kind.POINT?site instanceof AuthoringSite.Location:site instanceof AuthoringSite.Region);}
    private static String groupLabel(MinecraftServer server,String id){var group=Encounters.snapshot(server).groups().get(id);return group==null?id:group.label();}

    private static String triggerSummary(EventConfiguration configuration) {
        StringBuilder result = new StringBuilder();
        configuration.triggerRegion().ifPresent(region -> result.append(" - Region ").append(region.id()).append(" @ ").append(region.revision()));
        if (configuration.delayTicks()>0) result.append(" - ").append(configuration.delayTicks() / 20).append(" active seconds");
        if (!configuration.requiredFlags().isEmpty()) result.append(" - all flags ").append(String.join(", ",configuration.requiredFlags()));
        return result.toString();
    }

    private static String raw(String selected) {
        if (selected == null || !selected.startsWith("cue:")) throw new IllegalArgumentException("Choose an Event.");
        return selected.substring("cue:".length());
    }

    private static boolean held(MinecraftServer server) { return RuntimeHolds.reason(server) != null; }
    private static String describe(AuthoringSite.Region region) {
        return region.dimension() + " - " + region.min().x() + " " + region.min().y() + " " + region.min().z()
                + " to " + region.max().x() + " " + region.max().y() + " " + region.max().z();
    }
    private static Entry entry(String id, String label, String description, String kind, String reference, long revision) {
        return new Entry(id, shortText(label, 160), shortText(description, 2_048), kind, reference, revision);
    }
    private static Control control(Action action, String label, boolean enabled, String reason, boolean confirm, String input) {
        return new Control(action, label, enabled, enabled ? "" : shortText(reason, 512), confirm, input);
    }
    private static String shortText(String value, int maximum) {
        if (value == null) return "";
        return value.substring(0, Math.min(value.length(), maximum));
    }
}
