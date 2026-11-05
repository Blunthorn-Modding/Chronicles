package io.github.chronicle.core.client;

import io.github.chronicle.core.network.ProfilePayloads;
import net.minecraft.client.MinecraftClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Optional scenario presentations for the authenticated player's own public card. */
public final class OwnCardScreens {
    private static final int MAX_PRESENTERS = 8;
    private static final Map<String, Presenter> PRESENTERS = new LinkedHashMap<>();

    private OwnCardScreens() { }

    /** Register during client initialization. Activation must reflect the connected server's capability. */
    public static void register(String id, BooleanSupplier active,
                                BiConsumer<MinecraftClient, ProfilePayloads.View> open) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(open, "open");
        if (id.isBlank() || id.length() > 128 || PRESENTERS.containsKey(id) || PRESENTERS.size() >= MAX_PRESENTERS) {
            throw new IllegalArgumentException("Invalid or duplicate own-card presenter, or presenter limit reached.");
        }
        PRESENTERS.put(id, new Presenter(active, open));
    }

    /** Falls back to Core's card if none or more than one presenter is active. */
    static boolean open(MinecraftClient client, ProfilePayloads.View view) {
        if (client.player == null || view.saved() || !client.player.getUuid().equals(view.owner())) return false;
        Presenter selected = null;
        for (Presenter presenter : PRESENTERS.values()) {
            if (presenter.active().getAsBoolean()) {
                if (selected != null) return false;
                selected = presenter;
            }
        }
        if (selected == null) return false;
        selected.open().accept(client, view);
        return true;
    }

    private record Presenter(BooleanSupplier active, BiConsumer<MinecraftClient, ProfilePayloads.View> open) { }
}
