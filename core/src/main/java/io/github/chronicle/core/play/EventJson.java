package io.github.chronicle.core.play;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.github.chronicle.core.areas.AreaBounds;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.areas.SnapshotRef;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.encounters.EncounterTarget;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.TreeMap;

/** Strict JSON codec for bounded unified Event definitions, configurations and executions. */
public final class EventJson {
    public static final int MAX_ENCODED_BYTES = 64 * 1024;

    private static final Set<String> DEFINITION_FIELDS = Set.of(
            "id", "revision", "label", "description", "text", "sound", "audience", "audienceRegion", "changes");
    private static final Set<String> DEFINITION_FIELDS_V2 = Set.of(
            "id", "revision", "label", "description", "text", "sound", "audience", "audienceRegion", "changes", "flagChanges", "formatVersion");
    private static final Set<String> DEFINITION_FIELDS_V3 = Set.of(
            "id", "revision", "label", "description", "text", "sound", "audience", "audienceRegion", "changes", "flagChanges", "encounterActions", "formatVersion");
    private static final Set<String> ENCOUNTER_ACTION_FIELDS = Set.of("groupId", "targetOverride");
    private static final Set<String> ENCOUNTER_TARGET_FIELDS = Set.of("kind", "siteId", "siteRevision");
    private static final Set<String> REFERENCE_FIELDS = Set.of("id", "revision");
    private static final Set<String> CHANGE_FIELDS = Set.of("region", "areaId", "areaRevision", "state", "snapshot");
    private static final Set<String> SNAPSHOT_FIELDS = Set.of("sha256", "bounds", "cells", "encodedBytes");
    private static final Set<String> BOUNDS_FIELDS = Set.of("regionId", "dimension", "regionRevision", "min", "max");
    private static final Set<String> POSITION_FIELDS = Set.of("x", "y", "z");
    private static final Set<String> CONFIGURATION_FIELDS = Set.of(
            "definition", "mode", "triggerRegion", "armedAtTicks", "delayTicks", "requiredFlag", "enabled");
    private static final Set<String> CONFIGURATION_FIELDS_V2 = Set.of(
            "definition", "mode", "triggerRegion", "armedAtTicks", "delayTicks", "requiredFlags", "enabled", "source", "formatVersion");
    private static final Set<String> CONFIGURATION_FIELDS_V4 = Set.of(
            "definition", "mode", "triggerRegion", "armedAtTicks", "delayTicks", "requiredFlags", "enabled", "source", "formatVersion", "occurrencePolicy");
    private static final Set<String> SOURCE_FIELDS = Set.of("type", "target", "activity");
    private static final Set<String> EXECUTION_FIELDS = Set.of(
            "eventId", "operationId", "configuration", "phase", "source", "actor", "session", "elapsedTicks",
            "recipients", "worldPlanHash", "detail");

    private EventJson() { }

    public static JsonObject encodeDefinition(EventDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("event definition is required");
        JsonObject object = new JsonObject();
        object.addProperty("id", definition.id());
        object.addProperty("revision", definition.revision());
        object.addProperty("label", definition.label());
        object.addProperty("description", definition.description());
        object.addProperty("text", definition.text());
        object.addProperty("sound", definition.sound());
        object.addProperty("audience", definition.audience().name());
        object.add("audienceRegion", encodeOptionalReference(definition.audienceRegion()));
        JsonArray changes = new JsonArray();
        definition.changes().forEach(change -> changes.add(encodeChange(change)));
        object.add("changes", changes);
        if (definition.formatVersion() >= 2) {
            object.addProperty("formatVersion", definition.formatVersion());
            JsonObject flags = new JsonObject();
            definition.flagChanges().forEach(flags::addProperty);
            object.add("flagChanges", flags);
        }
        if (definition.formatVersion() >= 3) {
            JsonArray actions = new JsonArray();
            for (EventEncounterAction action : definition.encounterActions()) {
                JsonObject value = new JsonObject(); value.addProperty("groupId", action.groupId());
                if (action.targetOverride().isEmpty()) value.add("targetOverride", JsonNull.INSTANCE);
                else { EncounterTarget target=action.targetOverride().orElseThrow();JsonObject pin=new JsonObject();pin.addProperty("kind",target.kind().name());pin.addProperty("siteId",target.siteId());pin.addProperty("siteRevision",target.siteRevision());value.add("targetOverride",pin); }
                actions.add(value);
            }
            object.add("encounterActions", actions);
        }
        return bounded(object, "event definition");
    }

    public static EventDefinition decodeDefinition(JsonObject object) {
        bounded(object, "event definition");
        int version = formatVersion(object);
        PlayJson.fields(object, version == 1 ? DEFINITION_FIELDS : version == 2 ? DEFINITION_FIELDS_V2 : DEFINITION_FIELDS_V3, "event definition");
        JsonArray values = PlayJson.array(PlayJson.required(object, "changes"), "event Region changes");
        if (values.size() > EventDefinition.MAX_CHANGES) throw new IllegalArgumentException("too many event Region changes");
        List<EventRegionChange> changes = new ArrayList<>(values.size());
        for (JsonElement value : values) changes.add(decodeChange(PlayJson.object(value, "event Region change")));
        return new EventDefinition(
                PlayJson.string(PlayJson.required(object, "id"), "event ID"),
                PlayJson.number(PlayJson.required(object, "revision"), "event revision"),
                PlayJson.string(PlayJson.required(object, "label"), "event label"),
                PlayJson.string(PlayJson.required(object, "description"), "event description"),
                PlayJson.string(PlayJson.required(object, "text"), "event text"),
                PlayJson.string(PlayJson.required(object, "sound"), "event sound"),
                PlayJson.enumeration(PlayJson.required(object, "audience"), ObservationCatalog.Audience.class, "event audience"),
                decodeOptionalReference(object, "audienceRegion", "event audience Region"),
                changes, version == 1 ? Map.of() : decodeFlags(PlayJson.object(PlayJson.required(object, "flagChanges"), "event flag changes")),
                version < 3 ? List.of() : decodeEncounterActions(PlayJson.array(PlayJson.required(object,"encounterActions"),"event encounter actions")), version);
    }

    public static JsonObject encodeConfiguration(EventConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("event configuration is required");
        JsonObject object = new JsonObject();
        object.add("definition", encodeDefinition(configuration.definition()));
        object.addProperty("mode", configuration.mode().name());
        object.add("triggerRegion", encodeOptionalReference(configuration.triggerRegion()));
        object.addProperty("armedAtTicks", configuration.armedAtTicks());
        object.addProperty("delayTicks", configuration.delayTicks());
        if (configuration.formatVersion() == 1) object.addProperty("requiredFlag", configuration.requiredFlag());
        else {
            object.addProperty("formatVersion", configuration.formatVersion());
            JsonArray flags = new JsonArray();
            configuration.requiredFlags().forEach(flags::add);
            object.add("requiredFlags", flags);
            object.add("source", encodeSource(configuration.source()));
            if (configuration.formatVersion() >= 4) {
                object.addProperty("occurrencePolicy", configuration.occurrencePolicy().name());
            }
        }
        object.addProperty("enabled", configuration.enabled());
        return bounded(object, "event configuration");
    }

    public static EventConfiguration decodeConfiguration(JsonObject object) {
        bounded(object, "event configuration");
        int version = formatVersion(object);
        PlayJson.fields(object, version == 1 ? CONFIGURATION_FIELDS : version >= 4 ? CONFIGURATION_FIELDS_V4 : CONFIGURATION_FIELDS_V2, "event configuration");
        List<String> flags;
        if (version == 1) {
            String flag = PlayJson.string(PlayJson.required(object, "requiredFlag"), "required flag");
            flags = flag.isEmpty() ? List.of() : List.of(flag);
        } else {
            JsonArray values = PlayJson.array(PlayJson.required(object, "requiredFlags"), "required flags");
            if (values.size() > EventConfiguration.MAX_FLAGS) throw new IllegalArgumentException("too many required flags");
            flags = new ArrayList<>();
            for (JsonElement value : values) flags.add(PlayJson.string(value, "required flag"));
        }
        return new EventConfiguration(
                decodeDefinition(PlayJson.object(PlayJson.required(object, "definition"), "event definition")),
                PlayJson.enumeration(PlayJson.required(object, "mode"), PlayState.Mode.class, "event trigger mode"),
                decodeOptionalReference(object, "triggerRegion", "event trigger Region"),
                PlayJson.number(PlayJson.required(object, "armedAtTicks"), "event armed time"),
                PlayJson.number(PlayJson.required(object, "delayTicks"), "event delay"),
                flags,
                bool(PlayJson.required(object, "enabled"), "event enabled state"),
                version == 1 ? EventSource.empty() : decodeSource(PlayJson.object(PlayJson.required(object, "source"), "event source")), version,
                version >= 4 ? PlayJson.enumeration(PlayJson.required(object, "occurrencePolicy"), EventOccurrencePolicy.class, "event occurrence policy") : EventOccurrencePolicy.ONCE);
    }

    public static JsonObject encodeSource(EventSource source) {
        JsonObject object = new JsonObject();
        object.addProperty("type", source.type());
        object.addProperty("target", source.target());
        object.addProperty("activity", source.activity());
        return object;
    }

    public static EventSource decodeSource(JsonObject object) {
        PlayJson.fields(object, SOURCE_FIELDS, "event source");
        return new EventSource(PlayJson.string(PlayJson.required(object, "type"), "event source type"),
                PlayJson.string(PlayJson.required(object, "target"), "event source target"),
                PlayJson.string(PlayJson.required(object, "activity"), "event source activity"));
    }

    private static Map<String, Boolean> decodeFlags(JsonObject object) {
        if (object.size() > EventDefinition.MAX_FLAGS) throw new IllegalArgumentException("too many event flag changes");
        Map<String, Boolean> flags = new TreeMap<>();
        object.entrySet().forEach(entry -> flags.put(entry.getKey(), bool(entry.getValue(), "event flag value")));
        return flags;
    }

    private static int formatVersion(JsonObject object) {
        if (!object.has("formatVersion")) return 1;
        int version = PlayJson.integer(PlayJson.required(object, "formatVersion"), "event format version");
        if (version != 2 && version != 3 && version != 4) throw new IllegalArgumentException("unsupported event format version");
        return version;
    }

    private static List<EventEncounterAction> decodeEncounterActions(JsonArray values) {
        if (values.size() > EventDefinition.MAX_ENCOUNTER_ACTIONS) throw new IllegalArgumentException("too many event encounter actions");
        List<EventEncounterAction> actions = new ArrayList<>();
        for (JsonElement element : values) {
            JsonObject value=PlayJson.object(element,"event encounter action");PlayJson.fields(value,ENCOUNTER_ACTION_FIELDS,"event encounter action");
            JsonElement targetValue=member(value,"targetOverride");Optional<EncounterTarget> target;
            if(targetValue.isJsonNull())target=Optional.empty();else{JsonObject pin=PlayJson.object(targetValue,"event encounter target");PlayJson.fields(pin,ENCOUNTER_TARGET_FIELDS,"event encounter target");target=Optional.of(new EncounterTarget(PlayJson.enumeration(PlayJson.required(pin,"kind"),EncounterTarget.Kind.class,"encounter target kind"),PlayJson.string(PlayJson.required(pin,"siteId"),"encounter target site ID"),PlayJson.number(PlayJson.required(pin,"siteRevision"),"encounter target site revision")));}
            actions.add(new EventEncounterAction(PlayJson.string(PlayJson.required(value,"groupId"),"encounter group ID"),target));
        }
        return List.copyOf(actions);
    }

    public static JsonObject encodeExecution(EventExecution execution) {
        if (execution == null) throw new IllegalArgumentException("event execution is required");
        JsonObject object = new JsonObject();
        object.addProperty("eventId", execution.eventId());
        object.addProperty("operationId", execution.operationId().toString());
        object.add("configuration", encodeConfiguration(execution.configuration()));
        object.addProperty("phase", execution.phase().name());
        object.addProperty("source", execution.source().name());
        if (execution.actor() == null) object.add("actor", JsonNull.INSTANCE);
        else object.addProperty("actor", execution.actor().toString());
        object.addProperty("session", execution.session());
        object.addProperty("elapsedTicks", execution.elapsedTicks());
        JsonArray recipients = new JsonArray();
        execution.recipients().forEach(recipient -> recipients.add(recipient.toString()));
        object.add("recipients", recipients);
        object.addProperty("worldPlanHash", execution.worldPlanHash());
        object.addProperty("detail", execution.detail());
        return bounded(object, "event execution");
    }

    public static EventExecution decodeExecution(JsonObject object) {
        bounded(object, "event execution");
        PlayJson.fields(object, EXECUTION_FIELDS, "event execution");
        JsonElement actorValue = member(object, "actor");
        UUID actor = actorValue.isJsonNull() ? null : PlayJson.uuid(actorValue, "event actor");
        JsonArray values = PlayJson.array(PlayJson.required(object, "recipients"), "event recipients");
        if (values.size() > PlayState.MAX_PARTICIPANTS) throw new IllegalArgumentException("too many event recipients");
        List<UUID> recipients = new ArrayList<>(values.size());
        for (JsonElement value : values) recipients.add(PlayJson.uuid(value, "event recipient"));
        return new EventExecution(
                PlayJson.string(PlayJson.required(object, "eventId"), "event execution ID"),
                PlayJson.uuid(PlayJson.required(object, "operationId"), "event operation ID"),
                decodeConfiguration(PlayJson.object(PlayJson.required(object, "configuration"), "event configuration")),
                PlayJson.enumeration(PlayJson.required(object, "phase"), EventExecution.Phase.class, "event execution phase"),
                PlayJson.enumeration(PlayJson.required(object, "source"), PlayState.DeliverySource.class, "event execution source"),
                actor,
                PlayJson.integer(PlayJson.required(object, "session"), "event execution session"),
                PlayJson.number(PlayJson.required(object, "elapsedTicks"), "event execution time"),
                recipients,
                PlayJson.string(PlayJson.required(object, "worldPlanHash"), "event world-plan hash"),
                PlayJson.string(PlayJson.required(object, "detail"), "event execution detail"));
    }

    private static JsonObject encodeChange(EventRegionChange change) {
        JsonObject object = new JsonObject();
        object.add("region", encodeReference(change.region()));
        object.addProperty("areaId", change.areaId());
        object.addProperty("areaRevision", change.areaRevision());
        object.addProperty("state", change.state().name());
        object.add("snapshot", encodeSnapshot(change.snapshot()));
        return object;
    }

    private static EventRegionChange decodeChange(JsonObject object) {
        PlayJson.fields(object, CHANGE_FIELDS, "event Region change");
        return new EventRegionChange(
                decodeReference(PlayJson.object(PlayJson.required(object, "region"), "event Region reference")),
                PlayJson.string(PlayJson.required(object, "areaId"), "event Area ID"),
                PlayJson.number(PlayJson.required(object, "areaRevision"), "event Area revision"),
                PlayJson.enumeration(PlayJson.required(object, "state"), AreaState.class, "event Area state"),
                decodeSnapshot(PlayJson.object(PlayJson.required(object, "snapshot"), "event Area snapshot")));
    }

    private static JsonObject encodeReference(EventRegionReference reference) {
        JsonObject object = new JsonObject();
        object.addProperty("id", reference.id());
        object.addProperty("revision", reference.revision());
        return object;
    }

    private static EventRegionReference decodeReference(JsonObject object) {
        PlayJson.fields(object, REFERENCE_FIELDS, "event Region reference");
        return new EventRegionReference(
                PlayJson.string(PlayJson.required(object, "id"), "event Region ID"),
                PlayJson.number(PlayJson.required(object, "revision"), "event Region revision"));
    }

    private static JsonElement encodeOptionalReference(Optional<EventRegionReference> reference) {
        return reference.<JsonElement>map(EventJson::encodeReference).orElse(JsonNull.INSTANCE);
    }

    private static Optional<EventRegionReference> decodeOptionalReference(JsonObject object, String field, String name) {
        JsonElement value = member(object, field);
        return value.isJsonNull() ? Optional.empty() : Optional.of(decodeReference(PlayJson.object(value, name)));
    }

    private static JsonObject encodeSnapshot(SnapshotRef snapshot) {
        JsonObject object = new JsonObject();
        object.addProperty("sha256", snapshot.sha256());
        object.add("bounds", encodeBounds(snapshot.bounds()));
        object.addProperty("cells", snapshot.cells());
        object.addProperty("encodedBytes", snapshot.encodedBytes());
        return object;
    }

    private static SnapshotRef decodeSnapshot(JsonObject object) {
        PlayJson.fields(object, SNAPSHOT_FIELDS, "event Area snapshot");
        return new SnapshotRef(
                PlayJson.string(PlayJson.required(object, "sha256"), "event snapshot SHA-256"),
                decodeBounds(PlayJson.object(PlayJson.required(object, "bounds"), "event snapshot bounds")),
                PlayJson.integer(PlayJson.required(object, "cells"), "event snapshot cell count"),
                PlayJson.number(PlayJson.required(object, "encodedBytes"), "event snapshot encoded bytes"));
    }

    private static JsonObject encodeBounds(AreaBounds bounds) {
        JsonObject object = new JsonObject();
        object.addProperty("regionId", bounds.regionId());
        object.addProperty("dimension", bounds.dimension());
        object.addProperty("regionRevision", bounds.regionRevision());
        object.add("min", encodePosition(bounds.min()));
        object.add("max", encodePosition(bounds.max()));
        return object;
    }

    private static AreaBounds decodeBounds(JsonObject object) {
        PlayJson.fields(object, BOUNDS_FIELDS, "event snapshot bounds");
        return new AreaBounds(
                PlayJson.string(PlayJson.required(object, "regionId"), "event bounds Region ID"),
                PlayJson.string(PlayJson.required(object, "dimension"), "event bounds dimension"),
                PlayJson.number(PlayJson.required(object, "regionRevision"), "event bounds Region revision"),
                decodePosition(PlayJson.object(PlayJson.required(object, "min"), "event bounds minimum")),
                decodePosition(PlayJson.object(PlayJson.required(object, "max"), "event bounds maximum")));
    }

    private static JsonObject encodePosition(BlockPosition position) {
        JsonObject object = new JsonObject();
        object.addProperty("x", position.x());
        object.addProperty("y", position.y());
        object.addProperty("z", position.z());
        return object;
    }

    private static BlockPosition decodePosition(JsonObject object) {
        PlayJson.fields(object, POSITION_FIELDS, "event block position");
        return new BlockPosition(
                PlayJson.integer(PlayJson.required(object, "x"), "event block x"),
                PlayJson.integer(PlayJson.required(object, "y"), "event block y"),
                PlayJson.integer(PlayJson.required(object, "z"), "event block z"));
    }

    private static boolean bool(JsonElement value, String name) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static JsonElement member(JsonObject object, String field) {
        if (!object.has(field)) throw new IllegalArgumentException("missing " + field);
        return object.get(field);
    }

    private static JsonObject bounded(JsonObject object, String name) {
        if (object == null) throw new IllegalArgumentException(name + " is required");
        if (object.toString().getBytes(StandardCharsets.UTF_8).length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(name + " exceeds " + MAX_ENCODED_BYTES + " encoded bytes");
        }
        return object;
    }
}
