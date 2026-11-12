package io.github.chronicle.core.server;

import io.github.chronicle.core.content.InventorySnapshot;
import java.util.List;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

/** Registry-aware inventory encoding shared by scenario transaction journals. */
public final class InventorySnapshots {
    private InventorySnapshots() { }
    public static InventorySnapshot capture(MinecraftServer server, Inventory inventory) { return ChestAuthoring.snapshot(server, inventory); }
    public static List<ItemStack> decode(MinecraftServer server, InventorySnapshot snapshot) { return ChestAuthoring.decode(server, snapshot); }
}
