package io.github.chronicle.core.managed;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Pure registration catalog; Minecraft registry resolution happens when the server runtime starts. */
public final class ManagedBlockCatalog {
    public static final int MAX_TYPES = 256;
    private final Map<String, ManagedBlockDefinition> definitions = new TreeMap<>();
    private final Map<String, String> blockTypes = new TreeMap<>();
    private boolean frozen;

    public synchronized void register(ManagedBlockDefinition definition) {
        if (frozen) throw new IllegalStateException("Managed block catalog is frozen.");
        if (definition == null || definitions.size() >= MAX_TYPES)
            throw new IllegalArgumentException("Managed block catalog accepts at most " + MAX_TYPES + " types.");
        if (definitions.putIfAbsent(definition.typeId(), definition) != null)
            throw new IllegalArgumentException("Duplicate managed block type: " + definition.typeId());
        String previous = blockTypes.putIfAbsent(definition.blockId(), definition.typeId());
        if (previous != null) {
            definitions.remove(definition.typeId());
            throw new IllegalArgumentException("Managed block " + definition.blockId() + " is already registered as " + previous + ".");
        }
    }

    public synchronized void freeze() { frozen = true; }
    public synchronized List<ManagedBlockDefinition> definitions() { return List.copyOf(definitions.values()); }
    public synchronized Optional<ManagedBlockDefinition> get(String typeId) { return Optional.ofNullable(definitions.get(typeId)); }
    public synchronized Optional<ManagedBlockDefinition> byBlock(String blockId) {
        String type = blockTypes.get(blockId);
        return type == null ? Optional.empty() : Optional.of(definitions.get(type));
    }
}
