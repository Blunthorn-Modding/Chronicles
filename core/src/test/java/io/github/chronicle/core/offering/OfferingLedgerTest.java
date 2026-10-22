package io.github.chronicle.core.offering;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferingLedgerTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final OfferingDefinition HEART = new OfferingDefinition("quiet_below:extra_heart", new OfferingTerms(2, 2, 0), 3);

    @Test void buysEachDefinitionIndependentlyThreeTimesAndRejectsFourth() {
        OfferingLedger ledger = OfferingLedger.EMPTY;
        for (int index = 0; index < 3; index++) ledger = ledger.purchase(index, transaction(index), PLAYER, "quiet_below:network", HEART);
        assertEquals(3, ledger.count(PLAYER, "quiet_below:network", HEART.id()));
        OfferingLedger full = ledger;
        assertThrows(IllegalStateException.class, () -> full.purchase(3, transaction(3), PLAYER, "quiet_below:network", HEART));

        OfferingDefinition movement = new OfferingDefinition("quiet_below:movement", new OfferingTerms(-2, 0, 10), 3);
        OfferingLedger withMovement = ledger.purchase(3, transaction(4), PLAYER, "quiet_below:network", movement);
        assertEquals(1, withMovement.count(PLAYER, "quiet_below:network", movement.id()));
        assertEquals(new OfferingTerms(4, 6, 10), withMovement.totals(PLAYER).get("quiet_below:network"));
    }

    @Test void replayIsIdempotentBeforeRevisionCheckButCannotChangeTransactionMeaning() {
        UUID transaction = transaction(1);
        OfferingLedger bought = OfferingLedger.EMPTY.purchase(0, transaction, PLAYER, "quiet_below:network", HEART);
        assertEquals(bought, bought.purchase(0, transaction, PLAYER, "quiet_below:network", HEART));
        assertThrows(IllegalStateException.class, () -> bought.purchase(0, transaction, PLAYER, "quiet_below:other", HEART));
        assertThrows(IllegalStateException.class, () -> bought.purchase(0, transaction(2), PLAYER, "quiet_below:network", HEART));
    }

    @Test void snapshotsTermsAndClosureRemovesOnlyItsSource() {
        OfferingLedger first = OfferingLedger.EMPTY.purchase(0, transaction(1), PLAYER, "quiet_below:network", HEART);
        OfferingDefinition changed = new OfferingDefinition(HEART.id(), new OfferingTerms(4, 1, 0), 3);
        OfferingLedger second = first.purchase(1, transaction(2), PLAYER, "quiet_below:network", changed)
                .purchase(2, transaction(3), PLAYER, "other:network", HEART);
        assertEquals(new OfferingTerms(6, 3, 0), second.totals(PLAYER).get("quiet_below:network"));
        OfferingLedger closed = second.closeSource("quiet_below:network");
        assertTrue(closed.closedSources().contains("quiet_below:network"));
        assertEquals(1, closed.receipts().size());
        assertEquals("other:network", closed.receipts().getFirst().source());
        assertEquals(closed, closed.closeSource("quiet_below:network"));
        assertThrows(IllegalStateException.class, () -> closed.purchase(closed.revision(), transaction(4), PLAYER, "quiet_below:network", HEART));
    }

    @Test void enforcesReceiptPlayerAndClosureCapacities() {
        List<OfferingReceipt> perPlayer = new ArrayList<>();
        for (int index = 0; index <= OfferingLedger.MAX_PER_PLAYER; index++) perPlayer.add(receipt(index, PLAYER));
        assertThrows(IllegalArgumentException.class, () -> new OfferingLedger(129, perPlayer, Set.of()));

        List<OfferingReceipt> tooMany = new ArrayList<>();
        for (int index = 0; index <= OfferingLedger.MAX_RECEIPTS; index++) {
            tooMany.add(receipt(index, new UUID(0, index / OfferingLedger.MAX_PER_PLAYER + 1L)));
        }
        assertThrows(IllegalArgumentException.class, () -> new OfferingLedger(tooMany.size(), tooMany, Set.of()));

        Set<String> closed = new TreeSet<>();
        for (int index = 0; index <= OfferingLedger.MAX_CLOSED_SOURCES; index++) closed.add("test:source_" + index);
        assertThrows(IllegalArgumentException.class, () -> new OfferingLedger(closed.size(), List.of(), closed));
        assertThrows(IllegalArgumentException.class, () -> new OfferingLedger(0, List.of(receipt(1, PLAYER)), Set.of()));
        OfferingReceipt duplicate = receipt(2, PLAYER);
        assertThrows(IllegalArgumentException.class, () -> new OfferingLedger(2, List.of(duplicate, duplicate), Set.of()));
    }

    private static OfferingReceipt receipt(int index, UUID player) {
        return new OfferingReceipt(new UUID(1, index + 1L), player, "quiet_below:network", HEART.id(), HEART.terms());
    }

    private static UUID transaction(int index) { return new UUID(2, index + 1L); }
}
