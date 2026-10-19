package io.github.chronicle.core.campaign;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Ordered registry that campaign modules populate during initialization. */
public final class CampaignEvents {
    private final Map<String, CampaignEvent> byId = new LinkedHashMap<>();
    private boolean frozen;

    public synchronized void register(CampaignEvent event) {
        if (frozen) {
            throw new IllegalStateException("campaign event registry is frozen");
        }
        if (event == null) {
            throw new IllegalArgumentException("event is required");
        }
        if (byId.putIfAbsent(event.id(), event) != null) {
            throw new IllegalArgumentException("duplicate campaign event ID: " + event.id());
        }
    }

    public synchronized Collection<CampaignEvent> events() {
        return List.copyOf(new ArrayList<>(byId.values()));
    }

    public synchronized Optional<CampaignEvent> get(String id) {
        CampaignValidation.identifier(id, "event ID");
        return Optional.ofNullable(byId.get(id));
    }

    public synchronized void freeze() {
        frozen = true;
    }

    public synchronized boolean frozen() {
        return frozen;
    }
}
