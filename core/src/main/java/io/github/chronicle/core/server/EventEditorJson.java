package io.github.chronicle.core.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.menu.EventEditorForm;
import io.github.chronicle.core.play.EventJson;
import io.github.chronicle.core.play.EventSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;

/** Strict bounded network codec for the incomplete native Event editor draft. */
public final class EventEditorJson {
    public static final int MAX_ENCODED_BYTES = 64 * 1024;
    private static final Set<String> FIELDS = Set.of(
            "id", "revision", "name", "description", "message", "sound", "audience", "audienceRegion",
            "audienceRegionRevision", "mode", "triggerRegion", "triggerRegionRevision", "seconds", "flag",
            "changes", "creating");
    private static final Set<String> FIELDS_V3 = Set.of(
            "id", "revision", "name", "description", "message", "sound", "audience", "audienceRegion",
            "audienceRegionRevision", "mode", "triggerRegion", "triggerRegionRevision", "seconds", "flag",
            "changes", "creating", "encounterActions");
    private static final Set<String> CURRENT_FIELDS = Set.of(
            "id", "revision", "name", "description", "message", "sound", "audience", "audienceRegion",
            "audienceRegionRevision", "mode", "triggerRegion", "triggerRegionRevision", "seconds", "requiredFlags",
            "changes", "creating", "flagChanges", "source");
    private static final Set<String> CURRENT_FIELDS_V3 = Set.of(
            "id", "revision", "name", "description", "message", "sound", "audience", "audienceRegion",
            "audienceRegionRevision", "mode", "triggerRegion", "triggerRegionRevision", "seconds", "requiredFlags",
            "changes", "creating", "flagChanges", "source", "encounterActions");
    private static final Set<String> CHANGE_FIELDS = Set.of(
            "regionId", "label", "state", "areaRevision", "snapshotHash", "dimension", "current", "problem");
    private static final Set<String> ENCOUNTER_FIELDS = Set.of(
            "groupId","groupLabel","targetKind","siteId","siteLabel","siteRevision","current","problem");

    private EventEditorJson() { }

    public static String encode(EventEditorForm form) {
        if (form == null) throw new IllegalArgumentException("event editor form is required");
        JsonObject object = new JsonObject();
        object.addProperty("id", form.id());
        object.addProperty("revision", form.revision());
        object.addProperty("name", form.name());
        object.addProperty("description", form.description());
        object.addProperty("message", form.message());
        object.addProperty("sound", form.sound());
        object.addProperty("audience", form.audience());
        object.addProperty("audienceRegion", form.audienceRegion());
        object.addProperty("audienceRegionRevision", form.audienceRegionRevision());
        object.addProperty("mode", form.mode());
        object.addProperty("triggerRegion", form.triggerRegion());
        object.addProperty("triggerRegionRevision", form.triggerRegionRevision());
        object.addProperty("seconds", form.seconds());
        JsonArray requirements = new JsonArray();
        form.requiredFlags().forEach(requirements::add);
        object.add("requiredFlags", requirements);
        JsonObject flags = new JsonObject();
        form.flagChanges().forEach(flags::addProperty);
        object.add("flagChanges", flags);
        object.add("source", EventJson.encodeSource(form.source()));
        JsonArray changes = new JsonArray();
        for (EventEditorForm.Change change : form.changes()) {
            JsonObject value = new JsonObject();
            value.addProperty("regionId", change.regionId());
            value.addProperty("label", change.label());
            value.addProperty("state", change.state());
            value.addProperty("areaRevision", change.areaRevision());
            value.addProperty("snapshotHash", change.snapshotHash());
            value.addProperty("dimension", change.dimension());
            value.addProperty("current", change.current());
            value.addProperty("problem", change.problem());
            changes.add(value);
        }
        object.add("changes", changes);
        JsonArray encounterActions=new JsonArray();
        for(EventEditorForm.EncounterAction action:form.encounterActions()){JsonObject value=new JsonObject();value.addProperty("groupId",action.groupId());value.addProperty("groupLabel",action.groupLabel());value.addProperty("targetKind",action.targetKind());value.addProperty("siteId",action.siteId());value.addProperty("siteLabel",action.siteLabel());value.addProperty("siteRevision",action.siteRevision());value.addProperty("current",action.current());value.addProperty("problem",action.problem());encounterActions.add(value);}
        object.add("encounterActions",encounterActions);
        object.addProperty("creating", form.creating());
        String encoded = object.toString();
        bounded(encoded);
        return encoded;
    }

    public static EventEditorForm decode(String encoded) {
        bounded(encoded);
        JsonObject object = BookConfig.strictObject(encoded);
        boolean legacy = object.has("flag");boolean v3=object.has("encounterActions");
        exact(object, legacy ? (v3?FIELDS_V3:FIELDS) : v3?CURRENT_FIELDS_V3:CURRENT_FIELDS, "event editor");
        List<String> requirements = new ArrayList<>();
        Map<String, Boolean> flags = new TreeMap<>();
        EventSource source = EventSource.empty();
        if (legacy) {
            String flag = string(object, "flag");
            if (!flag.isEmpty()) requirements.add(flag);
        } else {
            JsonArray requiredFlags = array(required(object, "requiredFlags"), "event requirements");
            if (requiredFlags.size() > EventEditorForm.MAX_FLAGS) throw new IllegalArgumentException("too many event requirements");
            for (JsonElement value : requiredFlags) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("event requirement must be a string");
                requirements.add(value.getAsString());
            }
            JsonObject patch = object(required(object, "flagChanges"), "event flag changes");
            if (patch.size() > EventEditorForm.MAX_FLAGS) throw new IllegalArgumentException("too many event flag changes");
            for (String flag : patch.keySet()) flags.put(flag, bool(patch, flag));
            source = EventJson.decodeSource(object(required(object, "source"), "event source"));
        }
        JsonArray values = array(required(object, "changes"), "event changes");
        if (values.size() > EventEditorForm.MAX_CHANGES) throw new IllegalArgumentException("too many event changes");
        ArrayList<EventEditorForm.Change> changes = new ArrayList<>(values.size());
        for (JsonElement element : values) {
            JsonObject value = object(element, "event change");
            exact(value, CHANGE_FIELDS, "event change");
            changes.add(new EventEditorForm.Change(
                    string(value, "regionId"), string(value, "label"), string(value, "state"),
                    number(value, "areaRevision"), string(value, "snapshotHash"), string(value, "dimension"),
                    bool(value, "current"), string(value, "problem")));
        }
        List<EventEditorForm.EncounterAction> encounterActions=new ArrayList<>();
        if(v3){JsonArray actionValues=array(required(object,"encounterActions"),"event encounter actions");if(actionValues.size()>EventEditorForm.MAX_ENCOUNTER_ACTIONS)throw new IllegalArgumentException("too many event encounter actions");for(JsonElement element:actionValues){JsonObject value=object(element,"event encounter action");exact(value,ENCOUNTER_FIELDS,"event encounter action");encounterActions.add(new EventEditorForm.EncounterAction(string(value,"groupId"),string(value,"groupLabel"),string(value,"targetKind"),string(value,"siteId"),string(value,"siteLabel"),number(value,"siteRevision"),bool(value,"current"),string(value,"problem")));}}
        return new EventEditorForm(
                string(object, "id"), number(object, "revision"), string(object, "name"),
                string(object, "description"), string(object, "message"), string(object, "sound"),
                string(object, "audience"), string(object, "audienceRegion"), number(object, "audienceRegionRevision"),
                string(object, "mode"), string(object, "triggerRegion"), number(object, "triggerRegionRevision"),
                number(object, "seconds"), requirements, changes, bool(object, "creating"), flags, source,encounterActions);
    }

    private static void bounded(String encoded) {
        if (encoded == null || encoded.getBytes(StandardCharsets.UTF_8).length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("event editor JSON must be at most " + MAX_ENCODED_BYTES + " encoded bytes");
        }
    }

    private static void exact(JsonObject object, Set<String> fields, String name) {
        if (!object.keySet().equals(fields)) throw new IllegalArgumentException(name + " fields do not match schema");
    }

    private static JsonElement required(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).isJsonNull()) throw new IllegalArgumentException("missing " + field);
        return object.get(field);
    }

    private static JsonObject object(JsonElement value, String name) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(name + " must be an object");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonElement value, String name) {
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(name + " must be an array");
        return value.getAsJsonArray();
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static long number(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try { return Long.parseLong(value.getAsString()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(field + " is outside the long range", exception); }
    }

    private static boolean bool(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.getAsBoolean();
    }
}
