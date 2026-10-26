package io.github.chronicle.core.content;

import io.github.chronicle.core.books.BookDelivery;
import io.github.chronicle.core.books.BookDeliveryClaim;
import io.github.chronicle.core.books.BookDeliveryState;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Complete Core chest state. Every map is immutable and insertion ordered for stable persistence. */
public record ChestContentState(long revision, Map<String, ChestTemplate> templates,
                                Map<String, ChestBinding> bindings, List<ChestReceipt> receipts,
                                Map<String, PendingChestOperation> pending, LootSchedule schedule,
                                BookDeliveryState bookDelivery,
                                Map<UUID, AreaContentRemoval> areaRemovals) {
    public static final int MAX_TEMPLATES = 256;
    public static final int MAX_BINDINGS = 512;
    public static final int MAX_RECEIPTS = 8_192;
    public static final int MAX_PENDING = 64;
    public static final int MAX_AREA_REMOVALS = 4_096;

    public ChestContentState {
        if (revision < 0) throw new IllegalArgumentException("content revision cannot be negative");
        templates = immutableMap(templates, "templates", MAX_TEMPLATES);
        bindings = immutableMap(bindings, "bindings", MAX_BINDINGS);
        receipts = List.copyOf(receipts);
        pending = immutableMap(pending, "pending operations", MAX_PENDING);
        if (schedule == null) throw new IllegalArgumentException("loot schedule is required");
        if (bookDelivery == null) throw new IllegalArgumentException("book delivery state is required");
        areaRemovals = immutableUuidMap(areaRemovals, "area-content removals", MAX_AREA_REMOVALS);
        if (receipts.size() > MAX_RECEIPTS) throw new IllegalArgumentException("receipts may contain at most " + MAX_RECEIPTS + " entries");
        for (var entry : templates.entrySet()) {
            String id = entry.getKey(); ChestTemplate value = entry.getValue();
            if (!id.equals(value.id())) throw new IllegalArgumentException("template map key must match template ID");
            if (value.revision() > revision) throw new IllegalArgumentException("template revision cannot exceed state revision");
        }
        for (var entry : bindings.entrySet()) {
            String id = entry.getKey(); ChestBinding value = entry.getValue();
            if (!id.equals(value.id())) throw new IllegalArgumentException("binding map key must match binding ID");
            if (value.revision() > revision || (!value.typed() && value.templateRevision() > revision)) {
                throw new IllegalArgumentException("binding or template revision cannot exceed state revision");
            }
            if (!value.typed() && !templates.containsKey(value.templateId())) throw new IllegalArgumentException("binding references a missing template: " + value.templateId());
        }
        var typedTargets = new HashSet<String>();
        for (ChestBinding binding : bindings.values()) {
            if (binding.typed() && !typedTargets.add(physicalKey(binding.directTarget()))) {
                throw new IllegalArgumentException("typed bindings cannot share a physical target");
            }
        }
        HashSet<String> completedOperations = new HashSet<>();
        HashSet<String> latestTypedBindings = new HashSet<>();
        for (ChestReceipt receipt : receipts) {
            if (!completedOperations.add(receipt.operationId())) throw new IllegalArgumentException("receipt operation IDs must be unique");
            ChestBinding binding = bindings.get(receipt.bindingId());
            if (binding != null) {
                if (binding.typed() != (receipt.cycle() >= 0)) throw new IllegalArgumentException("receipt cycle kind does not match its binding");
                if (binding.typed() && !latestTypedBindings.add(binding.id())) throw new IllegalArgumentException("typed binding may retain only its newest receipt");
            }
        }
        HashSet<String> lockedTargets = new HashSet<>();
        Map<String, HashSet<String>> claimedOperations = new LinkedHashMap<>();
        for (var entry : pending.entrySet()) {
            String id = entry.getKey(); PendingChestOperation value = entry.getValue();
            if (!id.equals(value.operationId())) throw new IllegalArgumentException("pending map key must match operation ID");
            if (value.bindingRevision() > revision) throw new IllegalArgumentException("pending binding revision cannot exceed state revision");
            if (completedOperations.contains(id)) throw new IllegalArgumentException("an operation cannot be both pending and completed");
            ChestBinding binding = bindings.get(value.bindingId());
            if (binding == null || binding.revision() != value.bindingRevision()) throw new IllegalArgumentException("pending operation references a missing or stale binding: " + value.bindingId());
            if (!lockedTargets.add(physicalKey(value.target()))) throw new IllegalArgumentException("pending operation endpoints must not overlap");
            if (binding.typed()) {
                if (value.purpose() == PendingChestOperation.Purpose.FILL && value.cycle() < 0) {
                    throw new IllegalArgumentException("typed fill operation requires a nonnegative cycle");
                }
                if (!value.target().equals(binding.directTarget())) throw new IllegalArgumentException("typed pending target does not match its binding target");
            } else {
                if (value.purpose() != PendingChestOperation.Purpose.FILL) throw new IllegalArgumentException("legacy bindings support only fill operations");
                if (value.cycle() != -1) throw new IllegalArgumentException("legacy pending operation cycle must be -1");
                if (!value.target().anchorId().equals(binding.anchorId()) || value.target().anchorRevision() != binding.anchorRevision()) {
                    throw new IllegalArgumentException("pending target does not match its binding anchor");
                }
            }
            if (value.purpose() == PendingChestOperation.Purpose.MOVE) {
                ChestBinding source = bindings.get(value.sourceBindingId());
                if (source == null || !source.typed() || source.revision() != value.sourceBindingRevision()) {
                    throw new IllegalArgumentException("move operation references a missing or stale source binding: " + value.sourceBindingId());
                }
                if (!source.directTarget().equals(value.sourceTarget())) throw new IllegalArgumentException("move source target does not match its binding target");
                if (!source.lootType().equals(binding.lootType())) throw new IllegalArgumentException("move endpoints must belong to the same loot table");
                if (!lockedTargets.add(physicalKey(value.sourceTarget()))) throw new IllegalArgumentException("pending operation endpoints must not overlap");
            }
            for (BookDeliveryClaim claim : value.bookClaims()) {
                BookDelivery delivery = bookDelivery.deliveries().get(claim.bookId());
                if (delivery == null || !delivery.deliveryToken().equals(claim.deliveryToken())) {
                    throw new IllegalArgumentException("pending operation has an unknown managed-book claim: " + claim.bookId());
                }
                claimedOperations.computeIfAbsent(claim.bookId(), ignored -> new HashSet<>()).add(value.operationId());
            }
        }
        int preparedRemovals=0;
        for(var entry:areaRemovals.entrySet()){
            AreaContentRemoval removal=entry.getValue();
            if(!entry.getKey().equals(removal.operationId()))throw new IllegalArgumentException("area-content removal map key must match operation ID");
            if(completedOperations.contains(removal.operationId().toString())||pending.containsKey(removal.operationId().toString()))throw new IllegalArgumentException("area-content operation UUID collides with chest operation history");
            if(removal.expectedContentRevision()>revision)throw new IllegalArgumentException("area-content removal revision cannot exceed state revision");
            if(removal.stage()==AreaContentRemoval.Stage.PREPARED){
                preparedRemovals++;
                for(var binding:removal.affectedBindingRevisions().entrySet()){
                    ChestBinding current=bindings.get(binding.getKey());
                    if(current==null||current.revision()!=binding.getValue())throw new IllegalArgumentException("prepared area removal references a missing or stale binding: "+binding.getKey());
                }
                for(ManagedBookRemoval book:removal.managedBooks()){
                    BookDelivery delivery=bookDelivery.deliveries().get(book.bookId());
                    if(delivery==null||!delivery.deliveryToken().equals(book.deliveryToken()))throw new IllegalArgumentException("area removal has unknown managed-book evidence: "+book.bookId());
                    claimedOperations.computeIfAbsent(book.bookId(),ignored->new HashSet<>()).add(removal.operationId().toString());
                }
            }
        }
        if(preparedRemovals>1)throw new IllegalArgumentException("only one area-content removal may await world evidence");
        if(preparedRemovals>0&&!pending.isEmpty())throw new IllegalArgumentException("area-content removal cannot overlap pending chest operations");
        for (BookDelivery delivery : bookDelivery.deliveries().values()) {
            if (!delivery.pendingOperationIds().equals(claimedOperations.getOrDefault(delivery.bookId(), new HashSet<>()))) {
                throw new IllegalArgumentException("book pending reservations must match pending operation claims: " + delivery.bookId());
            }
        }
    }

    /** Source-compatible constructor for callers that do not yet manage book delivery state. */
    public ChestContentState(long revision, Map<String, ChestTemplate> templates,
                             Map<String, ChestBinding> bindings, List<ChestReceipt> receipts,
                             Map<String, PendingChestOperation> pending, LootSchedule schedule,
                             BookDeliveryState bookDelivery) {
        this(revision, templates, bindings, receipts, pending, schedule, bookDelivery, Map.of());
    }

    /** Source-compatible constructor for callers that do not yet manage book delivery state. */
    public ChestContentState(long revision, Map<String, ChestTemplate> templates,
                             Map<String, ChestBinding> bindings, List<ChestReceipt> receipts,
                             Map<String, PendingChestOperation> pending, LootSchedule schedule) {
        this(revision, templates, bindings, receipts, pending, schedule, BookDeliveryState.create(), Map.of());
    }

    /** Source-compatible constructor for callers that do not manage typed refill schedules. */
    public ChestContentState(long revision, Map<String, ChestTemplate> templates,
                             Map<String, ChestBinding> bindings, List<ChestReceipt> receipts,
                             Map<String, PendingChestOperation> pending) {
        this(revision, templates, bindings, receipts, pending, LootSchedule.empty(), BookDeliveryState.create(), Map.of());
    }

    public static ChestContentState empty() {
        return new ChestContentState(0, Map.of(), Map.of(), List.of(), Map.of(), LootSchedule.empty(), BookDeliveryState.create(), Map.of());
    }

    private static String physicalKey(ChestTarget target) {
        return target.dimension() + '\u0000' + target.x() + '\u0000' + target.y() + '\u0000' + target.z();
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> values, String field, int max) {
        if (values == null) throw new IllegalArgumentException(field + " are required");
        if (values.size() > max) throw new IllegalArgumentException(field + " may contain at most " + max + " entries");
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static <T> Map<UUID,T> immutableUuidMap(Map<UUID,T> values,String field,int max){
        if(values==null)throw new IllegalArgumentException(field+" are required");
        if(values.size()>max)throw new IllegalArgumentException(field+" may contain at most "+max+" entries");
        LinkedHashMap<UUID,T> result=new LinkedHashMap<>();values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{if(entry.getKey()==null||entry.getValue()==null)throw new IllegalArgumentException(field+" must not contain null");result.put(entry.getKey(),entry.getValue());});return java.util.Collections.unmodifiableMap(result);
    }
}
