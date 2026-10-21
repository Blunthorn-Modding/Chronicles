package io.github.chronicle.core.enchanting;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bounded identity-based FIFO work queue for incrementally migrating inventory slots.
 * Callers own loaded-state checks and any inventory mutation or dirty marking.
 */
public final class InventoryMigrationQueue<I, O> {
    public static final int MAX_SLOTS = 8192;
    public static final int MAX_INVENTORY_SLOTS = 4096;

    private final LinkedHashSet<Entry<I, O>> entries = new LinkedHashSet<>();
    private final IdentityHashMap<I, Entry<I, O>> inventories = new IdentityHashMap<>();
    private final IdentityHashMap<O, Set<Entry<I, O>>> owners = new IdentityHashMap<>();
    private int remainingSlots;

    /** Adds an inventory once, retaining the first owner and size seen for that identity. */
    public boolean offer(I inventory, int size, O owner) {
        if (inventory == null) throw new IllegalArgumentException("inventory is required");
        if (inventories.containsKey(inventory)) return true;
        if (size <= 0 || size > MAX_INVENTORY_SLOTS || size > MAX_SLOTS - remainingSlots) return false;
        Entry<I, O> entry = new Entry<>(inventory, owner, size);
        entries.add(entry);
        inventories.put(inventory, entry);
        owners.computeIfAbsent(owner, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(entry);
        remainingSlots += size;
        return true;
    }

    /** Returns the oldest active entry in constant time, or {@code null} when empty. */
    public Entry<I, O> next() {
        return entries.isEmpty() ? null : entries.iterator().next();
    }

    /** Consumes exactly one slot of an active entry and removes it when all slots are consumed. */
    public void advance(Entry<I, O> entry) {
        requireActive(entry);
        entry.advance();
        remainingSlots--;
        if (entry.remaining() == 0) remove(entry);
    }

    /** Drops the complete remaining work for one inventory identity. */
    public boolean removeInventory(I inventory) {
        Entry<I, O> entry = inventories.get(inventory);
        if (entry == null) return false;
        remove(entry);
        return true;
    }

    /** Drops all work owned by one owner identity without inspecting other owner entries. */
    public int removeOwner(O owner) {
        Set<Entry<I, O>> owned = owners.remove(owner);
        if (owned == null) return 0;
        int removed = owned.size();
        for (Entry<I, O> entry : owned) removeWithoutOwnerIndex(entry);
        return removed;
    }

    public void clear() {
        entries.clear();
        inventories.clear();
        owners.clear();
        remainingSlots = 0;
    }

    public int size() { return entries.size(); }
    public int remainingSlots() { return remainingSlots; }

    private void requireActive(Entry<I, O> entry) {
        if (entry == null || inventories.get(entry.inventory()) != entry || entry.remaining() <= 0) {
            throw new IllegalArgumentException("entry is not active in this inventory migration queue");
        }
    }

    private void remove(Entry<I, O> entry) {
        if (inventories.remove(entry.inventory()) != entry) throw new IllegalStateException("inventory migration queue indexes are inconsistent");
        entries.remove(entry);
        remainingSlots -= entry.remaining();
        Set<Entry<I, O>> owned = owners.get(entry.owner());
        if (owned != null) {
            owned.remove(entry);
            if (owned.isEmpty()) owners.remove(entry.owner());
        }
    }

    private void removeWithoutOwnerIndex(Entry<I, O> entry) {
        if (inventories.remove(entry.inventory()) != entry) throw new IllegalStateException("inventory migration queue indexes are inconsistent");
        entries.remove(entry);
        remainingSlots -= entry.remaining();
    }

    /** A queue-owned mutable cursor over one inventory's original slot count. */
    public static final class Entry<I, O> {
        private final I inventory;
        private final O owner;
        private final int size;
        private int slot;

        private Entry(I inventory, O owner, int size) {
            this.inventory = inventory;
            this.owner = owner;
            this.size = size;
        }

        public I inventory() { return inventory; }
        public O owner() { return owner; }
        public int size() { return size; }
        public int slot() { return slot; }
        public int remaining() { return size - slot; }
        private void advance() { slot++; }
    }
}
