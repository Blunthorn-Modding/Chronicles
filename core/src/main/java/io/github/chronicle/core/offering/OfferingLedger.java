package io.github.chronicle.core.offering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/** Immutable authority. No player reversal; source closure removes both sides of every trade. */
public record OfferingLedger(long revision, List<OfferingReceipt> receipts, Set<String> closedSources) {
    public static final int MAX_RECEIPTS = 16384;
    public static final int MAX_PER_PLAYER = 128;
    public static final int MAX_CLOSED_SOURCES = 256;
    public static final OfferingLedger EMPTY = new OfferingLedger(0, List.of(), Set.of());

    public OfferingLedger {
        if (revision < 0 || receipts == null || closedSources == null) throw new IllegalArgumentException("invalid offering ledger");
        if (receipts.size() > MAX_RECEIPTS || closedSources.size() > MAX_CLOSED_SOURCES) throw new IllegalArgumentException("offering ledger capacity exceeded");
        TreeSet<String> closed = new TreeSet<>();
        for (String source : closedSources) { OfferingDefinition.validId(source); closed.add(source); }
        Set<UUID> transactions = new HashSet<>();
        Map<UUID, Integer> counts = new TreeMap<>();
        for (OfferingReceipt receipt : receipts) {
            if (receipt == null || !transactions.add(receipt.transaction())) throw new IllegalArgumentException("null or duplicate offering receipt");
            if (closed.contains(receipt.source())) throw new IllegalArgumentException("closed source retains offering receipts");
            if (counts.merge(receipt.player(), 1, Integer::sum) > MAX_PER_PLAYER) throw new IllegalArgumentException("player offering capacity exceeded");
        }
        if (revision < (long) receipts.size() + closed.size()) throw new IllegalArgumentException("offering revision cannot precede its durable history");
        receipts = List.copyOf(receipts);
        closedSources = Collections.unmodifiableSet(closed);
    }

    public int count(UUID player, String source, String offering) {
        return (int) receipts.stream().filter(receipt -> receipt.player().equals(player)
                && receipt.source().equals(source) && receipt.offering().equals(offering)).count();
    }

    public Map<String, OfferingTerms> totals(UUID player) {
        TreeMap<String, OfferingTerms> result = new TreeMap<>();
        for (OfferingReceipt receipt : receipts) if (receipt.player().equals(player)) {
            result.merge(receipt.source(), receipt.terms(), OfferingTerms::plus);
        }
        return Collections.unmodifiableMap(result);
    }

    public OfferingLedger purchase(long expectedRevision, UUID transaction, UUID player, String source, OfferingDefinition definition) {
        return purchase(expectedRevision,transaction,player,source,definition,0,0);
    }
    public OfferingLedger purchase(long expectedRevision, UUID transaction, UUID player, String source, OfferingDefinition definition,long clock,long cooldownTicks) {
        if(clock<0||cooldownTicks<0)throw new IllegalArgumentException("invalid offering cooldown clock");
        OfferingReceipt candidate = new OfferingReceipt(transaction, player, source, definition.id(), definition.terms(),clock,Math.addExact(clock,cooldownTicks));
        if (closedSources.contains(source)) throw new IllegalStateException("this campaign source has ended");
        for (OfferingReceipt receipt : receipts) if (receipt.transaction().equals(transaction)) {
            if (!receipt.player().equals(player)||!receipt.source().equals(source)||!receipt.offering().equals(definition.id())||!receipt.terms().equals(definition.terms())) throw new IllegalStateException("transaction id already belongs to a different offering");
            return this;
        }
        if (expectedRevision != revision) throw new IllegalStateException("offerings changed; reopen before confirming");
        if (count(player, source, definition.id()) >= definition.maxPurchases()) throw new IllegalStateException("offering purchase limit reached");
        List<OfferingReceipt> next = new ArrayList<>(receipts); next.add(candidate);
        return new OfferingLedger(Math.incrementExact(revision), next, closedSources);
    }

    public OfferingLedger closeSource(String source) {
        OfferingDefinition.validId(source);
        if (closedSources.contains(source)) return this;
        Set<String> closed = new TreeSet<>(closedSources); closed.add(source);
        return new OfferingLedger(Math.incrementExact(revision), receipts.stream().filter(receipt -> !receipt.source().equals(source)).toList(), closed);
    }
}
