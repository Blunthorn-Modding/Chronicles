package io.github.chronicle.core.scenario;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Startup-only Event defaults, resolved by Core against each world's saved setup before arming. */
public final class ScenarioEventTemplates {
    private static final Map<String, EventTemplate> TEMPLATES = new TreeMap<>();
    private static final Map<String, Boolean> FLAGS = new TreeMap<>();
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
    public static synchronized void registerFlags(Map<String,Boolean> flags) {
        if(frozen)throw new IllegalStateException("Scenario flags are frozen after server startup.");
        // Reuse the portable content contract before changing either catalogue.
        new ScenarioContent("chronicle_core:defaults",Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),flags);
        var next=new TreeMap<>(FLAGS);
        flags.forEach((id,value)->{if(next.putIfAbsent(id,value)!=null)throw new IllegalArgumentException("Scenario flag ID is duplicated: "+id);});
        if(next.size()>256)throw new IllegalArgumentException("Scenario flag catalogue is full.");
        FLAGS.clear();FLAGS.putAll(next);
    }
    public static synchronized Map<String,Boolean> flags(){return Collections.unmodifiableMap(new TreeMap<>(FLAGS));}
    public static synchronized Map<String, EventTemplate> templates() {
        return Collections.unmodifiableMap(new TreeMap<>(TEMPLATES));
    }
    public static synchronized Optional<EventTemplate> get(String id) { return Optional.ofNullable(TEMPLATES.get(id)); }
}
