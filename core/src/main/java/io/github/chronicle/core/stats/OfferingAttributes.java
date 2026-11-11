package io.github.chronicle.core.stats;

import net.minecraft.entity.attribute.ClampedEntityAttribute;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

/** Native tracked attribute: gameplay stays authoritative, HUD addons read the same capacity. */
public final class OfferingAttributes {
    public static final RegistryEntry<EntityAttribute> FOOD_CAPACITY = Registry.registerReference(Registries.ATTRIBUTE,
            Identifier.of("chronicle_core", "food_capacity"),
            new ClampedEntityAttribute("attribute.chronicle_core.food_capacity", 20, 2, 40).setTracked(true));
    public static final RegistryEntry<EntityAttribute> EXHAUSTION_MULTIPLIER = Registry.registerReference(Registries.ATTRIBUTE,
            Identifier.of("chronicle_core", "exhaustion_multiplier"),
            new ClampedEntityAttribute("attribute.chronicle_core.exhaustion_multiplier",1,1,Double.MAX_VALUE).setTracked(true));
    private OfferingAttributes() { }
    public static void initialize() { }
}
