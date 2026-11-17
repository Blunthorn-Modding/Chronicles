package io.github.chronicle.core.server;

import io.github.chronicle.core.content.PendingChestOperation;
import io.github.chronicle.core.content.InventorySnapshot;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/** Scenario-owned item identities join Core's durable chest-fill transaction. */
public final class LootItemFactories {
    public interface Factory {
        /** Pure construction. The operation and physical slot identify a reproducible reward. */
        ItemStack prepare(MinecraftServer server, UUID operation, int slot, ItemStack template);
        /** Idempotently save scenario authority before the prepared reward enters the world. */
        void authorize(MinecraftServer server, UUID operation, int slot, ItemStack reward) throws IOException;
    }

    private static final Map<Identifier, Factory> FACTORIES = new HashMap<>();
    private LootItemFactories() { }

    public static void register(Identifier item, Factory factory) {
        Objects.requireNonNull(item);
        Objects.requireNonNull(factory);
        if (FACTORIES.putIfAbsent(item, factory) != null) throw new IllegalArgumentException("Duplicate loot item factory: " + item);
    }

    static ItemStack prepare(MinecraftServer server, UUID operation, int slot, ItemStack template) {
        Factory factory = FACTORIES.get(Registries.ITEM.getId(template.getItem()));
        if (factory == null) return template;
        ItemStack reward = factory.prepare(server, operation, slot, template.copy());
        if (reward == null || reward.isEmpty() || !reward.isOf(template.getItem()) || reward.getCount() != template.getCount())
            throw new IllegalArgumentException("Loot item factory changed the item or quantity");
        return reward;
    }

    static InventorySnapshot prepareContents(MinecraftServer server, UUID operation, InventorySnapshot contents) {
        var stacks = ChestAuthoring.decode(server, contents);
        var prepared = new net.minecraft.inventory.SimpleInventory(contents.size());
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (!stack.isEmpty()) prepared.setStack(slot, prepare(server, operation, slot, stack));
        }
        return ChestAuthoring.snapshot(server, prepared);
    }

    static void authorize(MinecraftServer server, PendingChestOperation operation) throws IOException {
        if (!operation.equals(ChestAuthoring.service(server).state().pending().get(operation.operationId())))
            throw new IllegalStateException("Loot item authorization requires the exact durable chest intent");
        var binding = ChestAuthoring.service(server).state().bindings().get(operation.bindingId());
        if (operation.purpose() != PendingChestOperation.Purpose.FILL || binding == null || !binding.typed()) return;
        var stacks = ChestAuthoring.decode(server, operation.after());
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack reward = stacks.get(slot);
            if (reward.isEmpty()) continue;
            Factory factory = FACTORIES.get(Registries.ITEM.getId(reward.getItem()));
            if (factory != null) factory.authorize(server, UUID.fromString(operation.operationId()), slot, reward.copy());
        }
    }
}
