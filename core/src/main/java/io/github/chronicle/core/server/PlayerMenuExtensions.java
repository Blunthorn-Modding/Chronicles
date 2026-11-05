package io.github.chronicle.core.server;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/** Bounded operator-only extensions to an existing Players detail. */
public final class PlayerMenuExtensions {
    private static final Map<String, Extension> EXTENSIONS = new LinkedHashMap<>();
    private PlayerMenuExtensions() { }

    /** Register during mod initialization; ids fit the existing control input bound. */
    public static void register(String id, String label, BiPredicate<ServerPlayerEntity, UUID> visible,
                                BiConsumer<ServerPlayerEntity, UUID> open) {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(label, "label");
        Objects.requireNonNull(visible, "visible"); Objects.requireNonNull(open, "open");
        if (!id.matches("[a-z0-9_.:-]{1,15}") || label.isBlank() || label.length() > 96
                || label.codePoints().anyMatch(Character::isISOControl) || label.indexOf('\u00a7') >= 0
                || EXTENSIONS.size() >= 8 || EXTENSIONS.containsKey(id)) {
            throw new IllegalArgumentException("Invalid or duplicate player menu extension, or extension limit reached.");
        }
        EXTENSIONS.put(id, new Extension(id, label, visible, open));
    }

    static List<Extension> visibleEntries(ServerPlayerEntity viewer, UUID target) {
        requireOperator(viewer);
        return EXTENSIONS.values().stream().filter(extension -> extension.visible().test(viewer, target)).toList();
    }

    static void open(ServerPlayerEntity viewer, UUID target, String id) {
        requireOperator(viewer);
        Extension extension = EXTENSIONS.get(id);
        if (extension == null || !extension.visible().test(viewer, target)) {
            throw new IllegalArgumentException("Player menu extension is unavailable.");
        }
        extension.open().accept(viewer, target);
    }

    private static void requireOperator(ServerPlayerEntity viewer) {
        if (!viewer.getEntityWorld().getServer().isOnThread() || !viewer.getCommandSource().hasPermissionLevel(2)) {
            throw new IllegalStateException("Player menu extensions require operator permission on the server thread.");
        }
    }

    record Extension(String id, String label, BiPredicate<ServerPlayerEntity, UUID> visible,
                     BiConsumer<ServerPlayerEntity, UUID> open) { }
}
