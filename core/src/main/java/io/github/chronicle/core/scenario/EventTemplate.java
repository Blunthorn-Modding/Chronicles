package io.github.chronicle.core.scenario;

import io.github.chronicle.core.play.EventConfiguration;
import io.github.chronicle.core.play.EventRegionChange;
import io.github.chronicle.core.play.EventSource;
import io.github.chronicle.core.play.ObservationCatalog;
import io.github.chronicle.core.play.PlayState;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.areas.AreaState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Portable Event authoring data. It intentionally has no revision, clock, snapshot, or enabled state. */
public record EventTemplate(String id, String label, String description, String text, String sound,
                            ObservationCatalog.Audience audience, String audienceReference,
                            PlayState.Mode mode, String triggerReference, long delayTicks,
                            List<String> requiredFlags, Map<String, Boolean> flagOutcomes,
                            List<RegionOutcome> regionOutcomes, EventSource source, List<EncounterOutcome> encounterOutcomes) {
    public static final int MAX_EVENTS = 128;
    public record RegionOutcome(String regionId, String areaId, String state) {
        public RegionOutcome {
            requireMapId(regionId, "event Region ID"); requireMapId(areaId, "event Area ID");
            try { AreaState.valueOf(state); } catch (IllegalArgumentException | NullPointerException exception) { throw new IllegalArgumentException("event Area state must be START or END", exception); }
        }
    }
    /** Portable spawn choice; GROUP leaves target resolution to a later target-map binding. */
    public record EncounterOutcome(String groupId, String targetKind, String targetReference) {
        public EncounterOutcome {
            requireId(groupId, "encounter group ID");
            if (!"GROUP".equals(targetKind) && !"POINT".equals(targetKind) && !"REGION".equals(targetKind)) throw new IllegalArgumentException("encounter target kind must be GROUP, POINT, or REGION");
            if (targetReference == null || ("GROUP".equals(targetKind) ? !targetReference.isEmpty() : !AuthoringSite.ID.matcher(targetReference).matches())) throw new IllegalArgumentException("encounter target reference is invalid");
        }
    }
    public EventTemplate(String id, String label, String description, String text, String sound, ObservationCatalog.Audience audience, String audienceReference, PlayState.Mode mode, String triggerReference, long delayTicks, List<String> requiredFlags, Map<String, Boolean> flagOutcomes, List<RegionOutcome> regionOutcomes, EventSource source) {
        this(id, label, description, text, sound, audience, audienceReference, mode, triggerReference, delayTicks, requiredFlags, flagOutcomes, regionOutcomes, source, List.of());
    }
    public EventTemplate {
        requireId(id, "event ID"); plain(label, 80, false, "event label"); plain(description, 512, true, "event description");
        if (text == null || text.length() > 512 || text.codePoints().anyMatch(cp -> (Character.isISOControl(cp) && cp != '\n') || cp == '\u00a7')) throw new IllegalArgumentException("event text is invalid");
        if (sound == null || (!sound.isEmpty() && (sound.length() > ObservationCatalog.MAX_ID_LENGTH || !ObservationCatalog.NAMESPACED_ID.matcher(sound).matches()))) throw new IllegalArgumentException("event sound is invalid");
        Objects.requireNonNull(audience, "event audience is required"); Objects.requireNonNull(mode, "event mode is required"); Objects.requireNonNull(source, "event source is required");
        if (source.target().matches("managed:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw new IllegalArgumentException("event source must not contain a managed runtime UUID");
        audienceReference = optionalMapId(audienceReference, "event audience reference"); triggerReference = optionalMapId(triggerReference, "event trigger reference");
        if (delayTicks < 0 || delayTicks > EventConfiguration.MAX_DELAY_TICKS) throw new IllegalArgumentException("event delay is outside the supported range");
        requiredFlags = ids(requiredFlags, EventConfiguration.MAX_FLAGS, "required flags");
        TreeMap<String, Boolean> flags = new TreeMap<>();
        if (flagOutcomes == null || flagOutcomes.size() > EventConfiguration.MAX_FLAGS) throw new IllegalArgumentException("event flag outcomes are invalid");
        flagOutcomes.forEach((key, value) -> { requireId(key, "event outcome flag"); flags.put(key, Objects.requireNonNull(value, "event outcome value is required")); });
        flagOutcomes = Collections.unmodifiableMap(flags);
        regionOutcomes = List.copyOf(regionOutcomes == null ? List.of() : regionOutcomes);
        if (regionOutcomes.size() > 8 || regionOutcomes.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("event Region outcomes are invalid");
        var regions = new java.util.HashSet<String>(); var areas = new java.util.HashSet<String>();
        for (RegionOutcome outcome : regionOutcomes) if (!regions.add(outcome.regionId()) || !areas.add(outcome.areaId())) throw new IllegalArgumentException("event Region and Area outcomes must be distinct");
        encounterOutcomes = List.copyOf(encounterOutcomes == null ? List.of() : encounterOutcomes);
        if (encounterOutcomes.size() > 4 || encounterOutcomes.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("event encounter outcomes are invalid");
        if (text.isBlank() && sound.isEmpty() && regionOutcomes.isEmpty() && flagOutcomes.isEmpty() && encounterOutcomes.isEmpty()) throw new IllegalArgumentException("an event requires text, sound, a Region outcome, flag outcome, or encounter outcome");
    }
    /** Converts live configuration without carrying pins, snapshots, armed time, or enablement. */
    public static EventTemplate from(EventConfiguration configuration) {
        Objects.requireNonNull(configuration, "event configuration is required");
        var definition = configuration.definition();
        List<RegionOutcome> outcomes = new ArrayList<>();
        for (EventRegionChange change : definition.changes()) outcomes.add(new RegionOutcome(change.region().id(), change.areaId(), change.state().name()));
        List<EncounterOutcome> encounters = new ArrayList<>();
        for (var action : definition.encounterActions()) encounters.add(action.targetOverride().map(target -> new EncounterOutcome(action.groupId(), target.kind().name(), target.siteId())).orElseGet(() -> new EncounterOutcome(action.groupId(), "GROUP", "")));
        return new EventTemplate(definition.id(), definition.label(), definition.description(), definition.text(), definition.sound(), definition.audience(),
                definition.audienceRegion().map(reference -> reference.id()).orElse(""), configuration.mode(),
                configuration.triggerRegion().map(reference -> reference.id()).orElse(""), configuration.delayTicks(), configuration.requiredFlags(),
                definition.flagChanges(), outcomes, portableSource(configuration.source()), encounters);
    }
    /** Portable imports always require an explicit target-map review before they can be enabled. */
    public boolean enabled() { return false; }
    private static EventSource portableSource(EventSource source) { return source.target().matches("managed:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") ? new EventSource(source.type(), "", source.activity()) : source; }
    private static List<String> ids(List<String> values, int max, String field) { if (values == null || values.size() > max) throw new IllegalArgumentException(field + " are invalid"); TreeMap<String, Boolean> sorted = new TreeMap<>(); for (String value : values) { requireId(value, field); if (sorted.put(value, Boolean.TRUE) != null) throw new IllegalArgumentException(field + " must be distinct"); } return List.copyOf(sorted.keySet()); }
    private static String optionalMapId(String value, String field) { if (value == null) throw new IllegalArgumentException(field + " is required; use empty for none"); if (!value.isEmpty()) requireMapId(value, field); return value; }
    private static void requireId(String value, String field) { if (value == null || value.length() > ObservationCatalog.MAX_ID_LENGTH || !ObservationCatalog.NAMESPACED_ID.matcher(value).matches()) throw new IllegalArgumentException(field + " is invalid"); }
    private static void requireMapId(String value, String field) { if (value == null || !AuthoringSite.ID.matcher(value).matches()) throw new IllegalArgumentException(field + " is invalid"); }
    private static void plain(String value, int max, boolean empty, String field) { if (value == null || value.length() > max || (!empty && value.isBlank()) || value.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(field + " is invalid"); }
}
