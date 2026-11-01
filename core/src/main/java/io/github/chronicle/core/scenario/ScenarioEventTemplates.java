package io.github.chronicle.core.scenario;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Startup-only reusable Event forms. Registering a template never arms an Event or changes a map. */
public final class ScenarioEventTemplates {
    private static final Map<String, EventTemplate> TEMPLATES = new TreeMap<>();
    private static boolean frozen;
    private ScenarioEventTemplates() { }

    public static synchronized void register(EventTemplate template) {
        if (frozen) throw new IllegalStateException("Scenario Event templates are frozen after server startup.");
        if (template == null) throw new IllegalArgumentException("An Event template is required.");
        if (TEMPLATES.size() >= EventTemplate.MAX_EVENTS || TEMPLATES.containsKey(template.id()))
            throw new IllegalArgumentException("Scenario Event template ID is duplicated or the catalogue is full.");
        TEMPLATES.put(template.id(), template);
    }

    public static synchronized void freeze() { frozen = true; }
    public static synchronized Map<String, EventTemplate> templates() {
        return Collections.unmodifiableMap(new TreeMap<>(TEMPLATES));
    }
    public static synchronized Optional<EventTemplate> get(String id) { return Optional.ofNullable(TEMPLATES.get(id)); }
}
