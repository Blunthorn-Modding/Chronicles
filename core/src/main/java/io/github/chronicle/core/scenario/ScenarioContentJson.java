package io.github.chronicle.core.scenario;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.loot.LootTableDraftJson;
import io.github.chronicle.core.loot.LootType;
import io.github.chronicle.core.encounters.EncounterGroup;
import io.github.chronicle.core.encounters.EncounterMember;
import io.github.chronicle.core.play.EventSource;
import io.github.chronicle.core.play.ObservationCatalog;
import io.github.chronicle.core.play.PlayState;
import io.github.chronicle.core.roles.PlayerRole;
import io.github.chronicle.core.roles.PlayerRoleConfig;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Strict, deterministic schema-5 portable content codec; schemas 1 through 4 remain readable. */
public final class ScenarioContentJson {
    public static final int SCHEMA_VERSION = 5;
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final int MAX_EXTENSION_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DEPTH = 32;
    private ScenarioContentJson() { }
    public static String encode(ScenarioContent content) {
        if (content == null) throw new IllegalArgumentException("scenario content is required");
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", SCHEMA_VERSION); root.addProperty("scenarioId", content.scenarioId());
        root.addProperty("rolesYaml", PlayerRoleConfig.encode(content.roles()));
        root.add("books", JsonParser.parseString(BookConfig.encode(content.books())).getAsJsonObject().get("books"));
        JsonArray loot = new JsonArray(); for (LootType value : content.lootTables().values()) loot.add(JsonParser.parseString(LootTableDraftJson.encode(value)));
        root.add("lootTables", loot); JsonArray events = new JsonArray(); for (EventTemplate value : content.events().values()) events.add(event(value)); root.add("events", events);
        JsonArray groups = new JsonArray(); for (EncounterGroup value : content.encounterGroups().values()) groups.add(group(value)); root.add("encounterGroups", groups);
        JsonObject flags = new JsonObject(); content.flags().forEach(flags::addProperty); root.add("flags", flags);
        try { root.add("eventPools", JsonParser.parseString(new String(io.github.chronicle.core.eventpool.EventPoolCatalogStore.encode(content.eventPools()), StandardCharsets.UTF_8)).getAsJsonObject().get("pools")); } catch (IOException e) { throw new IllegalArgumentException("invalid event pools", e); }
        JsonObject extensions = new JsonObject(); content.extensions().forEach((id, value) -> extensions.add(id, JsonParser.parseString(value))); root.add("extensions", extensions);
        String result = root.toString(); bound(result); if (!decode(result).equals(content)) throw new IllegalStateException("scenario content failed strict round trip"); return result;
    }
    public static ScenarioContent decode(String source) {
        bound(source); strict(source); JsonObject root = object(parse(source), "root"); int version = integer(required(root, "schemaVersion"), "schemaVersion");
        if (version != 1 && version != 2 && version != 3 && version != 4 && version != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported scenario content schema version");
        exact(root, version == 1 ? Set.of("schemaVersion", "scenarioId", "rolesYaml", "books", "lootTables", "events") : version < 4 ? Set.of("schemaVersion", "scenarioId", "rolesYaml", "books", "lootTables", "events", "encounterGroups") : version == 4 ? Set.of("schemaVersion", "scenarioId", "rolesYaml", "books", "lootTables", "events", "encounterGroups", "flags") : Set.of("schemaVersion", "scenarioId", "rolesYaml", "books", "lootTables", "events", "encounterGroups", "flags", "eventPools", "extensions"), "root");
        Map<String, PlayerRole> roles = PlayerRoleConfig.decode(string(required(root, "rolesYaml"), "rolesYaml"));
        JsonObject bookRoot = new JsonObject(); bookRoot.addProperty("schemaVersion", BookConfig.SCHEMA_VERSION); bookRoot.add("books", required(root, "books"));
        var books = BookConfig.decode(bookRoot.toString()); TreeMap<String, LootType> loot = new TreeMap<>();
        for (JsonElement element : array(required(root, "lootTables"), "lootTables")) { LootType value = LootTableDraftJson.decode(element.toString()); if (loot.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("duplicate loot table ID"); }
        TreeMap<String, EventTemplate> events = new TreeMap<>(); for (JsonElement element : array(required(root, "events"), "events")) { EventTemplate value = event(object(element, "event"), version); if (events.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("duplicate event ID"); }
        TreeMap<String, EncounterGroup> groups = new TreeMap<>(); if (version >= 2) for (JsonElement element : array(required(root, "encounterGroups"), "encounterGroups")) { EncounterGroup value = group(object(element, "encounter group")); if (groups.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("duplicate encounter group ID"); }
        TreeMap<String, Boolean> flags = new TreeMap<>(); if (version >= 4) object(required(root, "flags"), "flags").entrySet().forEach(entry -> flags.put(entry.getKey(), bool(entry.getValue(), "flag default")));
        Map<String, io.github.chronicle.core.eventpool.EventPoolDefinition> pools = Map.of(); TreeMap<String, String> extensions = new TreeMap<>();
        if (version >= 5) {
            JsonObject poolRoot = new JsonObject(); poolRoot.addProperty("schema", 1); poolRoot.add("pools", required(root, "eventPools"));
            try { pools = io.github.chronicle.core.eventpool.EventPoolCatalogStore.decode(poolRoot.toString().getBytes(StandardCharsets.UTF_8)); } catch (IOException e) { throw new IllegalArgumentException("invalid event pools", e); }
            object(required(root, "extensions"), "extensions").entrySet().forEach(e -> extensions.put(e.getKey(), canonicalExtension(e.getValue().toString())));
        }
        return new ScenarioContent(string(required(root, "scenarioId"), "scenarioId"), roles, books, loot, events, groups, flags, pools, extensions);
    }
    public static String canonicalExtension(String source) {
        if (source == null || source.getBytes(StandardCharsets.UTF_8).length > MAX_EXTENSION_BYTES) throw new IllegalArgumentException("extension exceeds 2 MiB");
        strict(source); String result = canonical(object(parse(source), "extension")).toString();
        if (result.getBytes(StandardCharsets.UTF_8).length > MAX_EXTENSION_BYTES) throw new IllegalArgumentException("extension exceeds 2 MiB");
        return result;
    }
    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) { JsonObject result = new JsonObject(); var sorted = new TreeMap<String, JsonElement>(); value.getAsJsonObject().entrySet().forEach(e -> sorted.put(e.getKey(), e.getValue())); sorted.forEach((key, child) -> result.add(key, canonical(child))); return result; }
        if (value.isJsonArray()) { JsonArray result = new JsonArray(); value.getAsJsonArray().forEach(child -> result.add(canonical(child))); return result; }
        return value.deepCopy();
    }
    private static JsonObject event(EventTemplate value) {
        JsonObject result = new JsonObject(); result.addProperty("id", value.id()); result.addProperty("label", value.label()); result.addProperty("description", value.description()); result.addProperty("text", value.text()); result.addProperty("sound", value.sound()); result.addProperty("audience", value.audience().name()); result.addProperty("audienceReference", value.audienceReference()); result.addProperty("mode", value.mode().name()); result.addProperty("triggerReference", value.triggerReference()); result.addProperty("delayTicks", value.delayTicks());
        JsonArray flags = new JsonArray(); value.requiredFlags().forEach(flags::add); result.add("requiredFlags", flags); JsonObject outcomes = new JsonObject(); value.flagOutcomes().forEach((flag, enabled) -> outcomes.addProperty(flag, enabled)); result.add("flagOutcomes", outcomes);
        JsonArray regions = new JsonArray(); for (EventTemplate.RegionOutcome outcome : value.regionOutcomes()) { JsonObject region = new JsonObject(); region.addProperty("regionId", outcome.regionId()); region.addProperty("areaId", outcome.areaId()); region.addProperty("state", outcome.state()); regions.add(region); } result.add("regionOutcomes", regions);
        JsonObject source = new JsonObject(); source.addProperty("type", value.source().type()); source.addProperty("target", value.source().target()); source.addProperty("activity", value.source().activity()); result.add("source", source);
        JsonArray encounters = new JsonArray(); for (EventTemplate.EncounterOutcome outcome : value.encounterOutcomes()) { JsonObject encounter = new JsonObject(); encounter.addProperty("groupId", outcome.groupId()); encounter.addProperty("targetKind", outcome.targetKind()); encounter.addProperty("targetReference", outcome.targetReference()); encounters.add(encounter); } result.add("encounterOutcomes", encounters); result.addProperty("occurrencePolicy",value.occurrencePolicy().name()); return result;
    }
    private static EventTemplate event(JsonObject value, int version) {
        value=value.deepCopy();var occurrencePolicy=version>=3?enumValue(io.github.chronicle.core.play.EventOccurrencePolicy.class,string(required(value,"occurrencePolicy"),"occurrencePolicy"),"occurrencePolicy"):io.github.chronicle.core.play.EventOccurrencePolicy.ONCE;if(version>=3)value.remove("occurrencePolicy");
        exact(value, version == 1 ? Set.of("id", "label", "description", "text", "sound", "audience", "audienceReference", "mode", "triggerReference", "delayTicks", "requiredFlags", "flagOutcomes", "regionOutcomes", "source") : Set.of("id", "label", "description", "text", "sound", "audience", "audienceReference", "mode", "triggerReference", "delayTicks", "requiredFlags", "flagOutcomes", "regionOutcomes", "source", "encounterOutcomes"), "event");
        List<String> flags = new ArrayList<>(); for (JsonElement flag : array(required(value, "requiredFlags"), "requiredFlags")) flags.add(string(flag, "required flag")); TreeMap<String, Boolean> outcomes = new TreeMap<>();
        JsonObject encodedOutcomes = object(required(value, "flagOutcomes"), "flagOutcomes"); encodedOutcomes.entrySet().forEach(entry -> outcomes.put(entry.getKey(), bool(entry.getValue(), "flag outcome")));
        List<EventTemplate.RegionOutcome> regions = new ArrayList<>(); for (JsonElement element : array(required(value, "regionOutcomes"), "regionOutcomes")) { JsonObject region = object(element, "region outcome"); exact(region, Set.of("regionId", "areaId", "state"), "region outcome"); regions.add(new EventTemplate.RegionOutcome(string(required(region, "regionId"), "regionId"), string(required(region, "areaId"), "areaId"), string(required(region, "state"), "state"))); }
        JsonObject source = object(required(value, "source"), "source"); exact(source, Set.of("type", "target", "activity"), "source");
        List<EventTemplate.EncounterOutcome> encounters = new ArrayList<>(); if (version >= 2) for (JsonElement element : array(required(value, "encounterOutcomes"), "encounterOutcomes")) { JsonObject encounter = object(element, "encounter outcome"); exact(encounter, Set.of("groupId", "targetKind", "targetReference"), "encounter outcome"); encounters.add(new EventTemplate.EncounterOutcome(string(required(encounter, "groupId"), "groupId"), string(required(encounter, "targetKind"), "targetKind"), string(required(encounter, "targetReference"), "targetReference"))); }
        return new EventTemplate(string(required(value, "id"), "id"), string(required(value, "label"), "label"), string(required(value, "description"), "description"), string(required(value, "text"), "text"), string(required(value, "sound"), "sound"), enumValue(ObservationCatalog.Audience.class, string(required(value, "audience"), "audience"), "audience"), string(required(value, "audienceReference"), "audienceReference"), enumValue(PlayState.Mode.class, string(required(value, "mode"), "mode"), "mode"), string(required(value, "triggerReference"), "triggerReference"), longNumber(required(value, "delayTicks"), "delayTicks"), flags, outcomes, regions, new EventSource(string(required(source, "type"), "source type"), string(required(source, "target"), "source target"), string(required(source, "activity"), "source activity")), encounters,occurrencePolicy);
    }
    private static JsonObject group(EncounterGroup value) { JsonObject group = new JsonObject(); group.addProperty("id", value.id()); group.addProperty("label", value.label()); group.addProperty("description", value.description()); JsonArray members = new JsonArray(); for (EncounterMember member : value.members()) { JsonObject encoded = new JsonObject(); encoded.addProperty("entityType", member.entityType()); encoded.addProperty("count", member.count()); members.add(encoded); } group.add("members", members); return group; }
    private static EncounterGroup group(JsonObject value) { exact(value, Set.of("id", "label", "description", "members"), "encounter group"); List<EncounterMember> members = new ArrayList<>(); for (JsonElement element : array(required(value, "members"), "encounter members")) { JsonObject member = object(element, "encounter member"); exact(member, Set.of("entityType", "count"), "encounter member"); members.add(new EncounterMember(string(required(member, "entityType"), "entityType"), integer(required(member, "count"), "count"))); } return new EncounterGroup(string(required(value, "id"), "id"), string(required(value, "label"), "label"), string(required(value, "description"), "description"), members); }
    private static JsonElement parse(String source) { try (JsonReader reader = new JsonReader(new StringReader(source))) { reader.setStrictness(Strictness.STRICT); JsonElement value = JsonParser.parseReader(reader); if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("unexpected trailing JSON"); return value; } catch (IOException exception) { throw new IllegalArgumentException("unable to read scenario content JSON", exception); } }
    private static void strict(String source) { try (JsonReader reader = new JsonReader(new StringReader(source))) { reader.setStrictness(Strictness.STRICT); inspect(reader, 0); if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("unexpected trailing JSON"); } catch (IOException exception) { throw new IllegalArgumentException("unable to read scenario content JSON", exception); } }
    private static void inspect(JsonReader reader, int depth) throws IOException { if (depth > MAX_DEPTH) throw new IllegalArgumentException("scenario content JSON nesting exceeds " + MAX_DEPTH); switch (reader.peek()) { case BEGIN_OBJECT -> { reader.beginObject(); Set<String> names = new HashSet<>(); while (reader.hasNext()) { String name = reader.nextName(); if (!names.add(name)) throw new IllegalArgumentException("duplicate JSON member: " + name); inspect(reader, depth + 1); } reader.endObject(); } case BEGIN_ARRAY -> { reader.beginArray(); while (reader.hasNext()) inspect(reader, depth + 1); reader.endArray(); } case STRING, NUMBER -> reader.nextString(); case BOOLEAN -> reader.nextBoolean(); case NULL -> reader.nextNull(); default -> throw new IllegalArgumentException("unexpected JSON token"); } }
    private static void bound(String value) { if (value == null || value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("scenario content exceeds 16 MiB limit"); }
    private static void exact(JsonObject object, Set<String> fields, String name) { if (!object.keySet().equals(fields)) throw new IllegalArgumentException(name + " fields do not match schema"); }
    private static JsonElement required(JsonObject object, String field) { if (!object.has(field) || object.get(field).isJsonNull()) throw new IllegalArgumentException("missing " + field); return object.get(field); }
    private static JsonObject object(JsonElement value, String name) { if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(name + " must be an object"); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value, String name) { if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(name + " must be an array"); return value.getAsJsonArray(); }
    private static String string(JsonElement value, String name) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(name + " must be a string"); return value.getAsString(); }
    private static boolean bool(JsonElement value, String name) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(name + " must be boolean"); return value.getAsBoolean(); }
    private static long longNumber(JsonElement value, String name) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(name + " must be integer"); try { return Long.parseLong(value.getAsString()); } catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " is outside range", exception); } }
    private static int integer(JsonElement value, String name) { try { return Math.toIntExact(longNumber(value, name)); } catch (ArithmeticException exception) { throw new IllegalArgumentException(name + " is outside range", exception); } }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String name, String field) { try { return Enum.valueOf(type, name); } catch (IllegalArgumentException exception) { throw new IllegalArgumentException(field + " is unsupported", exception); } }
}
