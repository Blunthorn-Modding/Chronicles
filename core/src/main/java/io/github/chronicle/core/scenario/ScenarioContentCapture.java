package io.github.chronicle.core.scenario;

import io.github.chronicle.core.books.BookDefinition;
import io.github.chronicle.core.loot.LootType;
import io.github.chronicle.core.play.EventConfiguration;
import io.github.chronicle.core.encounters.EncounterGroup;
import io.github.chronicle.core.roles.PlayerRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Captures effective definitions without mutating live catalogs or retaining map bindings. */
public final class ScenarioContentCapture {
    public record Result(ScenarioContent content, List<String> strippedRoleBindings) { public Result { strippedRoleBindings = List.copyOf(strippedRoleBindings); } }
    private ScenarioContentCapture() { }
    public static void validatePoolConfigurations(Map<String, io.github.chronicle.core.eventpool.EventPoolDefinition> pools, Map<String, EventConfiguration> events) {
        for (var pool : pools.values()) for (var entry : pool.entries()) {
            var config=events.get(entry.eventId());
            // Absent live configuration can be an unready packaged scenario draft.
            if (config != null && !config.enabled()) throw new IllegalArgumentException("Pool references disabled Event: " + entry.eventId());
        }
    }
    public static Result capture(String scenarioId, Map<String, PlayerRole> roles, Map<String, BookDefinition> books,
                                 Map<String, LootType> loot, Map<String, EventConfiguration> events) {
        return capture(scenarioId, roles, books, loot, events, Map.of());
    }
    public static Result capture(String scenarioId, Map<String, PlayerRole> roles, Map<String, BookDefinition> books,
                                 Map<String, LootType> loot, Map<String, EventConfiguration> events, Map<String, EncounterGroup> encounterGroups) {
        return capture(scenarioId, roles, books, loot, events, encounterGroups, Map.of());
    }
    public static Result capture(String scenarioId, Map<String, PlayerRole> roles, Map<String, BookDefinition> books,
                                 Map<String, LootType> loot, Map<String, EventConfiguration> events, Map<String, EncounterGroup> encounterGroups, Map<String, Boolean> flags) {
        TreeMap<String, Boolean> initialFlags = new TreeMap<>(); flags.forEach((id, value) -> { if (id == null || value == null) throw new IllegalArgumentException("flags require non-null IDs and values"); initialFlags.put(id, false); });
        TreeMap<String, PlayerRole> portableRoles = new TreeMap<>(); List<String> stripped = new ArrayList<>();
        roles.forEach((id, role) -> { if (role.starting() != null) stripped.add(id + ":starting"); if (role.respawn() != null) stripped.add(id + ":respawn"); portableRoles.put(id, new PlayerRole(role.id(), role.name(), role.description(), role.selectable(), role.capacity(), null, null, role.items(), role.books())); });
        TreeMap<String, EventTemplate> templates = new TreeMap<>(); events.forEach((id, event) -> templates.put(id, EventTemplate.from(event)));
        return new Result(new ScenarioContent(scenarioId, portableRoles, books, loot, templates, encounterGroups, initialFlags), stripped);
    }
}
