package io.github.chronicle.core.enchanting;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.MergedComponentMap;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Permanent, copy-on-write nested normalization. All mutations target the owning stack only. */
public final class EnchantmentStacks {
    public static final int MAX_DEPTH = 16, MAX_NODES = 4096;
    private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);
    private EnchantmentStacks() { }

    /** Five immutable value identities detect native mutations even when the map itself is reused. */
    public record Stamp(EnchantmentCaps policy, Object active, Object stored, Object container, Object bundle, Object charged) {
        static Stamp of(EnchantmentCaps policy, MergedComponentMap map) {
            return new Stamp(policy, map.get(DataComponentTypes.ENCHANTMENTS), map.get(DataComponentTypes.STORED_ENCHANTMENTS),
                    map.get(DataComponentTypes.CONTAINER), map.get(DataComponentTypes.BUNDLE_CONTENTS), map.get(DataComponentTypes.CHARGED_PROJECTILES));
        }
        boolean matches(EnchantmentCaps candidate, MergedComponentMap map) {
            return policy == candidate && active == map.get(DataComponentTypes.ENCHANTMENTS)
                    && stored == map.get(DataComponentTypes.STORED_ENCHANTMENTS)
                    && container == map.get(DataComponentTypes.CONTAINER) && bundle == map.get(DataComponentTypes.BUNDLE_CONTENTS)
                    && charged == map.get(DataComponentTypes.CHARGED_PROJECTILES);
        }
    }

    /** Returns whether actual stored components changed. Oversized trees are rejected before mutation. */
    public static boolean normalize(ItemStack stack, EnchantmentCaps policy) {
        if (BUSY.get() || !policy.enabled() || stack.isEmpty()) return false;
        var access = (EnchantmentStackAccess)(Object)stack;
        var stamp = access.chronicle$capStamp();
        if (stamp != null && stamp.matches(policy,access.chronicle$capComponents())) return false;
        BUSY.set(true);
        try {
            // Build a complete replacement off to the side, so a depth/node failure changes nothing.
            ItemStack replacement = normalizedCopy(stack, policy, 0, new int[]{0});
            if (replacement != null) stack.applyComponentsFrom(((EnchantmentStackAccess)(Object)replacement).chronicle$capComponents());
            access.chronicle$capStamp(Stamp.of(policy, access.chronicle$capComponents()));
            return replacement != null;
        } finally { BUSY.remove(); }
    }

    private static ItemStack normalizedCopy(ItemStack source, EnchantmentCaps policy, int depth, int[] nodes) {
        if (++nodes[0] > MAX_NODES || depth > MAX_DEPTH) throw new IllegalArgumentException("Enchanted item nesting exceeds Chronicle's 16-depth/4096-item limit; contents were not changed.");
        if (source.isEmpty()) return null;
        var map = ((EnchantmentStackAccess)(Object)source).chronicle$capComponents();
        var active = cap(map.get(DataComponentTypes.ENCHANTMENTS), policy);
        var stored = cap(map.get(DataComponentTypes.STORED_ENCHANTMENTS), policy);
        var container = map.get(DataComponentTypes.CONTAINER);
        var bundle = map.get(DataComponentTypes.BUNDLE_CONTENTS);
        var charged = map.get(DataComponentTypes.CHARGED_PROJECTILES);
        List<ItemStack> contents = container == null ? null : children(container.stream().toList(), policy, depth, nodes);
        List<ItemStack> bundled = bundle == null ? null : children(bundle.stream().toList(), policy, depth, nodes);
        List<ItemStack> projectiles = charged == null ? null : children(charged.getProjectiles(), policy, depth, nodes);
        if (active == null && stored == null && contents == null && bundled == null && projectiles == null) return null;
        ItemStack result = source.copy();
        if (active != null) result.set(DataComponentTypes.ENCHANTMENTS, active);
        if (stored != null) result.set(DataComponentTypes.STORED_ENCHANTMENTS, stored);
        if (contents != null) result.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(contents));
        if (bundled != null) {
            var builder = new BundleContentsComponent.Builder(new BundleContentsComponent(List.copyOf(bundled)));
            builder.setSelectedStackIndex(bundle.getSelectedStackIndex());
            result.set(DataComponentTypes.BUNDLE_CONTENTS, builder.build());
        }
        if (projectiles != null) result.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.of(projectiles));
        return result;
    }

    private static List<ItemStack> children(List<ItemStack> source, EnchantmentCaps policy, int depth, int[] nodes) {
        List<ItemStack> result = null;
        for (int i = 0; i < source.size(); i++) {
            ItemStack replacement = normalizedCopy(source.get(i), policy, depth + 1, nodes);
            if (replacement != null) {
                if (result == null) result = new ArrayList<>(source);
                result.set(i, replacement);
            }
        }
        return result;
    }

    private static ItemEnchantmentsComponent cap(ItemEnchantmentsComponent component, EnchantmentCaps policy) {
        if (component == null || component.isEmpty()) return null;
        ItemEnchantmentsComponent.Builder builder = null;
        for (var enchantment : component.getEnchantments()) {
            String id = enchantment.getKey().orElseThrow(() -> new IllegalArgumentException("Unregistered enchantment cannot be capped")).getValue().toString();
            int old = component.getLevel(enchantment), next = policy.storedLevel(id, old);
            if (next != old) {
                if (builder == null) builder = new ItemEnchantmentsComponent.Builder(component);
                builder.set(enchantment, next);
            }
        }
        return builder == null ? null : builder.build();
    }
}
