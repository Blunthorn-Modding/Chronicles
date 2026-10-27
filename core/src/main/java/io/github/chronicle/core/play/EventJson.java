package io.github.chronicle.core.play;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.github.chronicle.core.areas.AreaBounds;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.areas.SnapshotRef;
import io.github.chronicle.core.authoring.BlockPosition;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Strict JSON codec for bounded unified Event definitions, configurations and executions. */
public final class EventJson {
    public static final int MAX_ENCODED_BYTES = 64 * 1024;

    private static final Set<String> DEFINITION_FIELDS = Set.of(
            "id", "revision", "label", "description", "text", "sound", "audience", "audienceRegion", "changes");
    private static final Set<String> REFERENCE_FIELDS = Set.of("id", "revision");
    private static final Set<String> CHANGE_FIELDS = Set.of("region", "areaId", "areaRevision", "state", "snapshot");
    private static final Set<String> SNAPSHOT_FIELDS = Set.of("sha256", "bounds", "cells", "encodedBytes");
    private static final Set<String> BOUNDS_FIELDS = Set.of("regionId", "dimension", "regionRevision", "min", "max");
    private static final Set<String> POSITION_FIELDS = Set.of("x", "y", "z");
    private static final Set<String> CONFIGURATION_FIELDS = Set.of(
            "definition", "mode", "triggerRegion", "armedAtTicks", "delayTicks", "requiredFlag", "enabled");
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
        return bounded(object, "event definition");
    }

    public static EventDefinition decodeDefinition(JsonObject object) {
        bounded(object, "event definition");
        PlayJson.fields(object, DEFINITION_FIELDS, "event definition");
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
                changes);
    }

    public static JsonObject encodeConfiguration(EventConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("event configuration is required");
        JsonObject object = new JsonObject();
        object.add("definition", encodeDefinition(configuration.definition()));
        object.addProperty("mode", configuration.mode().name());
        object.add("triggerRegion", encodeOptionalReference(configuration.triggerRegion()));
        object.addProperty("armedAtTicks", configuration.armedAtTicks());
        object.addProperty("delayTicks", configuration.delayTicks());
        object.addProperty("requiredFlag", configuration.requiredFlag());
        object.addProperty("enabled", configuration.enabled());
        return bounded(object, "event configuration");
    }

    public static EventConfiguration decodeConfiguration(JsonObject object) {
        bounded(object, "event configuration");
        PlayJson.fields(object, CONFIGURATION_FIELDS, "event configuration");
        return new EventConfiguration(
                decodeDefinition(PlayJson.object(PlayJson.required(object, "definition"), "event definition")),
                PlayJson.enumeration(PlayJson.required(object, "mode"), PlayState.Mode.class, "event trigger mode"),
                decodeOptionalReference(object, "triggerRegion", "event trigger Region"),
                PlayJson.number(PlayJson.required(object, "armedAtTicks"), "event armed time"),
                PlayJson.number(PlayJson.required(object, "delayTicks"), "event delay"),
                PlayJson.string(PlayJson.required(object, "requiredFlag"), "required flag"),
                bool(PlayJson.required(object, "enabled"), "event enabled state"));
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
