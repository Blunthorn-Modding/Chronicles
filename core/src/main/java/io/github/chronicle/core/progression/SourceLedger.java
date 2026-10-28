package io.github.chronicle.core.progression;

import java.util.*;

/** Bounded durable source evidence. Pending observations never imply a successful gameplay outcome. */
public record SourceLedger(long revision, Map<String, Receipt> receipts) {
    public static final int MAX_RECEIPTS = 1024;
    public static final int MAX_CANCELLED = 64;
    public enum Stage { PREPARED, COMMITTED, CANCELLED }
    public record Receipt(String id, String type, String target, String activity, UUID actor,
                          boolean activeOrigin, Map<String,Boolean> flags, String authority, Stage stage) {
        public Receipt {
            text(id, 256, false); text(type, 128, false); text(target, 160, true);
            text(activity, 128, true); text(authority, 256, false);
            if (!type.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || stage == null) throw new IllegalArgumentException("invalid source receipt");
            if(flags==null||flags.size()>256)throw new IllegalArgumentException("invalid source flag snapshot");
            flags.forEach((key,value)->{if(key==null||key.length()>128||!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||value==null)throw new IllegalArgumentException("invalid source flag snapshot");});
            flags=Map.copyOf(flags);
        }
        public Receipt at(Stage next) { return new Receipt(id,type,target,activity,actor,activeOrigin,flags,authority,next); }
    }
    public SourceLedger {
        if (revision < 0 || receipts == null || receipts.size() > MAX_RECEIPTS) throw new IllegalArgumentException("source evidence capacity exceeded");
        Map<String,Receipt> copy = new LinkedHashMap<>();
        receipts.forEach((id,receipt) -> {
            if (receipt == null || !receipt.id().equals(id)) throw new IllegalArgumentException("source receipt identity mismatch");
            copy.put(id,receipt);
        });
        receipts = Collections.unmodifiableMap(copy);
        if(receipts.values().stream().filter(r->r.stage()==Stage.CANCELLED).count()>MAX_CANCELLED)throw new IllegalArgumentException("cancelled source history exceeds limits");
    }
    public static SourceLedger empty() { return new SourceLedger(0,Map.of()); }
    public SourceLedger prepare(Receipt receipt) {
        Receipt old = receipts.get(receipt.id());
        if (old != null) {
            if(old.stage()==Stage.CANCELLED)throw new IllegalStateException("cancelled source operation ID cannot be reused");
            if (!old.at(receipt.stage()).equals(receipt)) throw new IllegalStateException("source operation identity changed");
            return this;
        }
        if (receipt.stage() != Stage.PREPARED) throw new IllegalArgumentException("new source receipt must be prepared");
        Map<String,Receipt> next = new LinkedHashMap<>(receipts);
        if(next.size()>=MAX_RECEIPTS)next.entrySet().stream().filter(e->e.getValue().stage()==Stage.CANCELLED).map(Map.Entry::getKey).findFirst().ifPresent(next::remove);
        if (next.size() >= MAX_RECEIPTS) throw new IllegalStateException("Event source evidence is full; preserve the stopped world for maintenance");
        next.put(receipt.id(),receipt);
        return new SourceLedger(Math.incrementExact(revision),next);
    }
    public SourceLedger finish(String id, boolean committed) {
        Receipt old = receipts.get(id);
        if (old == null) throw new IllegalStateException("source preparation is missing");
        Stage nextStage = committed ? Stage.COMMITTED : Stage.CANCELLED;
        if (old.stage() == nextStage) return this;
        if (old.stage() != Stage.PREPARED) throw new IllegalStateException("source outcome conflicts with saved evidence");
        Map<String,Receipt> next = new LinkedHashMap<>(receipts); next.put(id,old.at(nextStage));
        while(next.values().stream().filter(r->r.stage()==Stage.CANCELLED).count()>MAX_CANCELLED) {
            String oldest=next.entrySet().stream().filter(e->e.getValue().stage()==Stage.CANCELLED).map(Map.Entry::getKey).findFirst().orElseThrow();next.remove(oldest);
        }
        return new SourceLedger(Math.incrementExact(revision),next);
    }
    /** A trusted producer proved that no authority for this operation was committed. */
    public SourceLedger cancelAuthority(String authority) {
        SourceLedger next=this;
        for(Receipt receipt:receipts.values())if(receipt.stage()==Stage.PREPARED&&receipt.authority().equals(authority))next=next.finish(receipt.id(),false);
        return next;
    }
    static void text(String value,int max,boolean empty) {
        if (value == null || value.length() > max || !empty && value.isEmpty() || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("invalid bounded source text");
    }
}
