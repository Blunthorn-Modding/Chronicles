package io.github.chronicle.core.encounters;

/** One ordered entity type/count declaration; equipment, NBT and commands are deliberately absent. */
public record EncounterMember(String entityType, int count) {
    public EncounterMember {
        entityType = EncounterIds.registry(entityType, "entity type");
        if (count < 1 || count > 32) throw new IllegalArgumentException("encounter member count must be between 1 and 32");
    }
}
