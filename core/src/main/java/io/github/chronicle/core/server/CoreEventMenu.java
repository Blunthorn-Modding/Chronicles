package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.areas.AreaChange;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.menu.CoreMenuModel.Action;
import io.github.chronicle.core.menu.CoreMenuModel.Control;
import io.github.chronicle.core.menu.CoreMenuModel.Detail;
import io.github.chronicle.core.menu.CoreMenuModel.Entry;
import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import io.github.chronicle.core.menu.EventEditorForm;
import io.github.chronicle.core.play.EventConfiguration;
import io.github.chronicle.core.play.EventExecution;
import io.github.chronicle.core.play.EventRegionChange;
import io.github.chronicle.core.play.ObservationCatalog;
import io.github.chronicle.core.play.PlayState;
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
        return EventRuntime.configurations(server).values().stream()
                .map(configuration -> {
                    var definition = configuration.definition();
                    String summary = definition.description().isBlank() ? definition.text() : definition.description();
                    return entry("cue:" + definition.id(), definition.label(), summary,
                            "CUE", definition.id(), definition.revision());
                })
                .sorted(Comparator.comparing(Entry::label).thenComparing(Entry::id))
                .toList();
    }

    static EventEditorForm emptyForm() {
        return new EventEditorForm("chronicle_core:event", 0, "", "", "", "",
                ObservationCatalog.Audience.PARTICIPANTS.name(), "", 0,
                PlayState.Mode.MANUAL.name(), "", 0, 0, "", List.of(), true);
    }

    static EventEditorForm form(MinecraftServer server, String id) {
        EventConfiguration configuration = EventRuntime.configuration(server, id);
        var definition = configuration.definition();
        Optional<io.github.chronicle.core.play.EventRegionReference> audience = definition.audienceRegion();
        Optional<io.github.chronicle.core.play.EventRegionReference> trigger = configuration.triggerRegion();
        List<EventEditorForm.Change> changes = definition.changes().stream()
                .map(change -> change(server, change)).toList();
        return new EventEditorForm(definition.id(), definition.revision(), definition.label(),
                definition.description(), definition.text(), definition.sound(), definition.audience().name(),
                audience.map(io.github.chronicle.core.play.EventRegionReference::id).orElse(""),
                audience.map(io.github.chronicle.core.play.EventRegionReference::revision).orElse(0L),
                configuration.mode().name(),
                trigger.map(io.github.chronicle.core.play.EventRegionReference::id).orElse(""),
                trigger.map(io.github.chronicle.core.play.EventRegionReference::revision).orElse(0L),
                configuration.delayTicks() / 20,
                configuration.requiredFlags(), changes, false,definition.flagChanges(),configuration.source());
    }

    static Detail detail(ServerPlayerEntity player, String selected, boolean reviewed) {
        return detail(player,selected,reviewed,false);
    }
    static Detail detail(ServerPlayerEntity player, String selected, boolean reviewed, boolean sourceReviewed) {
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
            return new Detail("", "Events", "Choose when Events run, their required flags, and their text, sound, Region or flag outcomes.",
                    facts, controls, null, null, null);
        }
        String id = raw(selected);
        EventConfiguration configuration = EventRuntime.configuration(server, id);
        var definition = configuration.definition();
        PlayState play = PlayRuntime.service(server).state();
        EventExecution execution = play.executions().get(id);
        PlayState.Delivery delivery = play.deliveries().get(id);
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
        facts.add(new Fact("Progression", EventProgressRuntime.status(server,configuration)));
        if(configuration.mode()==PlayState.Mode.SYSTEM)facts.add(new Fact("Activity source", configuration.source().toString()));
        facts.add(new Fact("Region changes", definition.changes().isEmpty() ? "None" : definition.changes().stream()
                .map(change -> change.region().id() + " -> " + change.state()).reduce((left, right) -> left + "; " + right).orElse("None")));
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
        if(RuntimeHolds.reason(server)!=null&&!RuntimeHolds.heldByOther(server,EventSources.HOLD))
            controls.add(control(Action.EVENT_SOURCE_RELOAD,"Reload source evidence",!active,"Pause play before reloading repaired evidence.",true,""));

        controls.add(control(Action.EVENT_EDIT, "Edit event", canEdit && !unresolved,
                unresolved ? "Resolve the current Event execution before editing it." : editReason, false, ""));
        if (!definition.text().isBlank() || !definition.sound().isBlank()) {
            controls.add(control(Action.EVENT_PREVIEW, "Preview privately", true, "", false, ""));
        }
        controls.add(control(Action.EVENT_CHECK, "Check audience", true, "", false, ""));
        controls.add(control(Action.EVENT_REVIEW, "Review run", active && !held(server) && configuration.enabled() && available,
                !active ? "Start play before running an Event." : held(server) ? "Resolve the current operation first."
                        : !configuration.enabled() ? "Configure this Event before running it."
                        : !available ? "Allow the Event again after its current run is resolved." : "",
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
            controls.add(control(Action.EVENT_RECOVER, "Recover event", active, "Restore active play before recovery.", true, ""));
        }
        var progress=EventProgressRuntime.entry(server,configuration);
        if (delivery != null || execution != null && execution.phase() == EventExecution.Phase.COMPLETE || progress!=null&&(progress.skipped()||progress.triggered()&&execution==null)) {
            controls.add(control(Action.EVENT_RESET, execution==null&&delivery==null?"Reset pending trigger":"Allow event again", canEdit, editReason, true, ""));
        }
        if (custom) controls.add(control(Action.EVENT_DELETE, "Delete event", canEdit && !unresolved,
                unresolved ? "Resolve the current Event execution before deleting it." : editReason, true, ""));

        String description = definition.description().isBlank()
                ? "A player-facing Event with optional media and saved Region changes."
                : definition.description();
        if(EventProgressRuntime.legacyReviewRequired(configuration))description+=" Review and save this timer to enable its flag-based countdown.";
        return new Detail(selected, definition.label(), description, facts, controls, null, null, null);
    }

    static List<Entry> catalog(MinecraftServer server, String kind) {
        ArrayList<Entry> entries = new ArrayList<>();
        switch (kind) {
            case "EVENT_SOUND" -> Registries.SOUND_EVENT.getIds().stream().sorted()
                    .forEach(id -> entries.add(entry(id.toString(), id.toString(), "Registered sound", "SOUND", id.toString(), 0)));
            case "EVENT_REGION" -> AuthoringRuntime.get(server).service().state().sites().values().stream()
                    .filter(AuthoringSite.Region.class::isInstance).map(AuthoringSite.Region.class::cast)
                    .sorted(Comparator.comparing(AuthoringSite.Region::id))
                    .forEach(region -> entries.add(entry(region.id(), region.id(), describe(region), "REGION", region.id(), region.revision())));
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
