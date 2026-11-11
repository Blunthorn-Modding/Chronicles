package io.github.chronicle.core.play;

/** Finite descriptive source identity. These values never contain executable instructions. */
public record EventSource(String type, String target, String activity) {
    public EventSource {
        ObservationCatalog.requirePlain(type, 128, true, "event source type");
        ObservationCatalog.requirePlain(target, 160, true, "event source target");
        ObservationCatalog.requirePlain(activity, 128, true, "event source activity");
        if (!type.isEmpty()) ObservationCatalog.requireIdentifier(type, "event source type");
        if (type.isEmpty() && (!target.isEmpty() || !activity.isEmpty())) {
            throw new IllegalArgumentException("event source details require a source type");
        }
    }

    public static EventSource empty() { return new EventSource("", "", ""); }

    public boolean isEmpty() { return type.isEmpty(); }
}
