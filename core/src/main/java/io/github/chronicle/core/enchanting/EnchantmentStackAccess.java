package io.github.chronicle.core.enchanting;

import net.minecraft.component.MergedComponentMap;

/** Internal access to a stack's own map; never exposed as a capped read-only view. */
public interface EnchantmentStackAccess {
    MergedComponentMap chronicle$capComponents();
    EnchantmentStacks.Stamp chronicle$capStamp();
    void chronicle$capStamp(EnchantmentStacks.Stamp stamp);
}
