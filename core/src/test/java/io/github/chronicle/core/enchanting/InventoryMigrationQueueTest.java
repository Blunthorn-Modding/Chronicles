package io.github.chronicle.core.enchanting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryMigrationQueueTest {
    @Test void admitsBoundedInventoriesIdempotentlyAndInFifoOrder() {
        InventoryMigrationQueue<Token, Token> queue = new InventoryMigrationQueue<>();
        Token first = new Token("same");
        Token second = new Token("same");
        Token owner = new Token("owner");
        assertTrue(queue.offer(first, 2, owner));
        assertTrue(queue.offer(second, 3, owner));
        assertTrue(queue.offer(first, InventoryMigrationQueue.MAX_INVENTORY_SLOTS + 1, new Token("new owner")));
        assertEquals(2, queue.size());
        assertEquals(5, queue.remainingSlots());
        InventoryMigrationQueue.Entry<Token, Token> entry = queue.next();
        assertSame(first, entry.inventory());
        assertSame(owner, entry.owner());
        assertEquals(2, entry.size());
        assertEquals(0, entry.slot());
        assertEquals(2, entry.remaining());
        assertFalse(queue.offer(new Token("large"), InventoryMigrationQueue.MAX_INVENTORY_SLOTS + 1, owner));
        assertFalse(queue.offer(new Token("zero"), 0, owner));
        assertEquals(5, queue.remainingSlots());
    }

    @Test void advancesExactlyOneSlotAndRemovesCompleteOrStaleEntries() {
        InventoryMigrationQueue<Object, Object> queue = new InventoryMigrationQueue<>();
        Object inventory = new Object();
        assertTrue(queue.offer(inventory, 2, null));
        InventoryMigrationQueue.Entry<Object, Object> entry = queue.next();
        queue.advance(entry);
        assertEquals(1, entry.slot());
        assertEquals(1, entry.remaining());
        assertEquals(1, queue.remainingSlots());
        assertSame(entry, queue.next());
        queue.advance(entry);
        assertNull(queue.next());
        assertEquals(0, queue.remainingSlots());
        assertThrows(IllegalArgumentException.class, () -> queue.advance(entry));
        InventoryMigrationQueue<Object, Object> other = new InventoryMigrationQueue<>();
        assertTrue(other.offer(new Object(), 1, null));
        assertThrows(IllegalArgumentException.class, () -> queue.advance(other.next()));
    }

    @Test void rejectsAggregateBudgetWithoutChangingState() {
        InventoryMigrationQueue<Object, Object> queue = new InventoryMigrationQueue<>();
        assertTrue(queue.offer(new Object(), InventoryMigrationQueue.MAX_INVENTORY_SLOTS, null));
        assertTrue(queue.offer(new Object(), InventoryMigrationQueue.MAX_INVENTORY_SLOTS, null));
        assertFalse(queue.offer(new Object(), 1, null));
        assertEquals(2, queue.size());
        assertEquals(InventoryMigrationQueue.MAX_SLOTS, queue.remainingSlots());
    }

    @Test void removesPartialInventoryWorkAndAllOwnerIdentityWorkWithoutLeaks() {
        InventoryMigrationQueue<Token, Token> queue = new InventoryMigrationQueue<>();
        Token owner = new Token("same");
        Token equalButDistinctOwner = new Token("same");
        Token first = new Token("first");
        Token second = new Token("second");
        Token third = new Token("third");
        assertTrue(queue.offer(first, 3, owner));
        assertTrue(queue.offer(second, 2, owner));
        assertTrue(queue.offer(third, 4, equalButDistinctOwner));
        InventoryMigrationQueue.Entry<Token, Token> firstEntry = queue.next();
        queue.advance(firstEntry);
        assertTrue(queue.removeInventory(first));
        assertFalse(queue.removeInventory(first));
        assertEquals(6, queue.remainingSlots());
        assertEquals(1, queue.removeOwner(owner));
        assertEquals(1, queue.size());
        assertSame(third, queue.next().inventory());
        assertEquals(4, queue.remainingSlots());
        assertEquals(0, queue.removeOwner(owner));
        assertEquals(1, queue.removeOwner(equalButDistinctOwner));
        assertEquals(0, queue.size());
        assertEquals(0, queue.remainingSlots());
    }

    @Test void clearDropsEveryIndexAndMakesOldEntriesStale() {
        InventoryMigrationQueue<Object, Object> queue = new InventoryMigrationQueue<>();
        Object inventory = new Object();
        assertTrue(queue.offer(inventory, 1, new Object()));
        InventoryMigrationQueue.Entry<Object, Object> stale = queue.next();
        queue.clear();
        assertEquals(0, queue.size());
        assertEquals(0, queue.remainingSlots());
        assertNull(queue.next());
        assertThrows(IllegalArgumentException.class, () -> queue.advance(stale));
        assertEquals(0, queue.removeOwner(stale.owner()));
        assertTrue(queue.offer(inventory, 1, null));
        assertEquals(1, queue.size());
    }

    private static final class Token {
        private final String value;
        private Token(String value) { this.value = value; }
        @Override public boolean equals(Object other) { return other instanceof Token token && value.equals(token.value); }
        @Override public int hashCode() { return value.hashCode(); }
    }
}
