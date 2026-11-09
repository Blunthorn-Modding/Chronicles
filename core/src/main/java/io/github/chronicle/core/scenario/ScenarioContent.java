package io.github.chronicle.core.scenario;

import io.github.chronicle.core.books.BookDefinition;
import io.github.chronicle.core.loot.LootType;
import io.github.chronicle.core.encounters.EncounterGroup;
import io.github.chronicle.core.play.ObservationCatalog;
import io.github.chronicle.core.roles.PlayerRole;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Immutable, content-only build input. Map setup and live campaign state never belong here. */
public record ScenarioContent(String scenarioId, Map<String, PlayerRole> roles, Map<String, BookDefinition> books,
                              Map<String, LootType> lootTables, Map<String, EventTemplate> events, Map<String, EncounterGroup> encounterGroups, Map<String, Boolean> flags,
                              Map<String, io.github.chronicle.core.eventpool.EventPoolDefinition> eventPools, Map<String, String> extensions) {
    public static final int MAX_ENCOUNTER_GROUPS = 128;
    public static final int MAX_FLAGS = 256;
    public static final int MAX_BOOKS = io.github.chronicle.core.books.BookCatalog.MAX_BOOKS;
    public static final int MAX_LOOT_TABLES = io.github.chronicle.core.loot.LootCatalog.MAX_TYPES;
    public ScenarioContent {
        if (scenarioId == null || scenarioId.length() > ObservationCatalog.MAX_ID_LENGTH || !ObservationCatalog.NAMESPACED_ID.matcher(scenarioId).matches()) throw new IllegalArgumentException("scenario ID must be namespaced");
        roles = indexed(roles, PlayerRole.MAX_ROLES, "roles"); books = indexed(books, MAX_BOOKS, "books"); lootTables = indexed(lootTables, MAX_LOOT_TABLES, "loot tables"); events = indexed(events, EventTemplate.MAX_EVENTS, "events"); encounterGroups = indexed(encounterGroups, MAX_ENCOUNTER_GROUPS, "encounter groups");
        flags = indexed(flags, MAX_FLAGS, "flags");
        eventPools = indexed(eventPools, 64, "event pools");
        extensions = indexed(extensions, 16, "extensions");
        for (var entry : extensions.entrySet()) {
            if (entry.getKey().length() > ObservationCatalog.MAX_ID_LENGTH || !ObservationCatalog.NAMESPACED_ID.matcher(entry.getKey()).matches()) throw new IllegalArgumentException("extension IDs must be namespaced");
            if (!ScenarioContentJson.canonicalExtension(entry.getValue()).equals(entry.getValue())) throw new IllegalArgumentException("extension JSON must be canonical");
        }
        for (var entry : eventPools.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("pool key mismatch");
            for (var item : entry.getValue().entries()) {
                var event = events.get(item.eventId());
                if (event == null) throw new IllegalArgumentException("pool references absent event: " + item.eventId());
                if (event.mode() != io.github.chronicle.core.play.PlayState.Mode.MANUAL || event.occurrencePolicy() != io.github.chronicle.core.play.EventOccurrencePolicy.ONCE || event.delayTicks() != 0) throw new IllegalArgumentException("pool event must be manual, once-only and without delay: " + item.eventId());
            }
        }
        if (flags.keySet().stream().anyMatch(id -> id.length() > ObservationCatalog.MAX_ID_LENGTH || !ObservationCatalog.NAMESPACED_ID.matcher(id).matches())) throw new IllegalArgumentException("flag IDs must be namespaced");
        if (roles.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().id()))
                || books.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().id()))
                || lootTables.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().id()))
                || events.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().id()))
                || encounterGroups.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().id()))) {
            throw new IllegalArgumentException("scenario content maps must use each definition's ID");
        }
        if (roles.values().stream().anyMatch(role -> role.starting() != null || role.respawn() != null)) {
            throw new IllegalArgumentException("portable scenario roles must not contain map-bound starting or respawn bindings");
        }
        for (EventTemplate event : events.values()) for (EventTemplate.EncounterOutcome outcome : event.encounterOutcomes()) if (!encounterGroups.containsKey(outcome.groupId())) throw new IllegalArgumentException("event encounter outcome references an absent encounter group: " + outcome.groupId());
    }
    public ScenarioContent(String scenarioId, Map<String, PlayerRole> roles, Map<String, BookDefinition> books,
                           Map<String, LootType> lootTables, Map<String, EventTemplate> events, Map<String, EncounterGroup> encounterGroups, Map<String, Boolean> flags) { this(scenarioId, roles, books, lootTables, events, encounterGroups, flags, Map.of(), Map.of()); }
    public ScenarioContent(String scenarioId, Map<String, PlayerRole> roles, Map<String, BookDefinition> books,
                           Map<String, LootType> lootTables, Map<String, EventTemplate> events) { this(scenarioId, roles, books, lootTables, events, Map.of()); }
    public ScenarioContent(String scenarioId, Map<String, PlayerRole> roles, Map<String, BookDefinition> books,
                           Map<String, LootType> lootTables, Map<String, EventTemplate> events, Map<String, EncounterGroup> encounterGroups) { this(scenarioId, roles, books, lootTables, events, encounterGroups, Map.of()); }
    private static <T> Map<String, T> indexed(Map<String, T> values, int maximum, String field) {
        if (values == null || values.size() > maximum) throw new IllegalArgumentException(field + " exceed their limit");
        TreeMap<String, T> ordered = new TreeMap<>(); values.forEach((id, value) -> { if (id == null || value == null || ordered.putIfAbsent(id, value) != null) throw new IllegalArgumentException(field + " require distinct non-null IDs"); });
        return Collections.unmodifiableMap(ordered);
    }
}
