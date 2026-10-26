package io.github.chronicle.core.content;

import io.github.chronicle.core.books.BookChance;
import io.github.chronicle.core.books.BookCopyLocation;
import io.github.chronicle.core.books.BookDefinition;
import io.github.chronicle.core.books.BookDelivery;
import io.github.chronicle.core.books.BookDeliveryClaim;
import io.github.chronicle.core.books.BookDeliveryState;
import io.github.chronicle.core.books.BookDiscovery;
import io.github.chronicle.core.books.BookKeepRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Transactional domain service: candidates become visible only after durable repository writes. */
public final class ChestContentService {
    private final ChestContentRepository repository;
    private ChestContentState current;

    public ChestContentService(ChestContentRepository repository) throws IOException {
        if (repository == null) throw new IllegalArgumentException("chest content repository is required");
        this.repository = repository;
        this.current = repository.load();
        if (current == null) throw new IOException("chest content repository returned no state");
    }

    public synchronized ChestContentState state() { return current; }

    /**
     * Reserves exact target-before content before terrain mutation. Managed-book entries must be
     * the tokens actually observed by native snapshot inspection, never inferred from a rule.
     */
    public synchronized AreaContentRemoval prepareAreaRemoval(UUID operationId,long expectedContentRevision,
                                                               Set<String> affectedBindingIds,Set<String> retiredBindingIds,
                                                               Set<ManagedBookRemoval> managedBooks,String preparedAt)throws IOException{
        if(operationId==null)throw new IllegalArgumentException("area-content operation ID is required");
        if(affectedBindingIds==null||retiredBindingIds==null||managedBooks==null)throw new IllegalArgumentException("area-content removal sets are required");
        Set<String> affected=Set.copyOf(affectedBindingIds),retired=Set.copyOf(retiredBindingIds);Set<ManagedBookRemoval> books=Set.copyOf(managedBooks);
        AreaContentRemoval existing=current.areaRemovals().get(operationId);
        if(existing!=null){if(!existing.matchesPrepare(expectedContentRevision,affected,retired,books,preparedAt))throw new IllegalStateException("area-content operation UUID was reused for different intent");return existing;}
        if(current.revision()!=expectedContentRevision)throw new IllegalStateException("chest-content revision changed before area removal preparation");
        if(!current.pending().isEmpty())throw new IllegalStateException("resolve pending chest operations before preparing area-content removal");
        requireNoPreparedAreaRemoval();
        if(current.areaRemovals().size()>=ChestContentState.MAX_AREA_REMOVALS)throw new IllegalStateException("area-content removal journal is full");
        String id=operationId.toString();
        if(current.receipts().stream().anyMatch(receipt->receipt.operationId().equals(id)))throw new IllegalStateException("area-content operation UUID is already used by a chest receipt");
        LinkedHashMap<String,Long> affectedRevisions=new LinkedHashMap<>();
        affected.stream().sorted().forEach(bindingId->{ChestBinding binding=current.bindings().get(bindingId);if(binding==null)throw new IllegalArgumentException("unknown affected chest binding: "+bindingId);affectedRevisions.put(bindingId,binding.revision());});
        AreaContentRemoval removal=AreaContentRemoval.prepared(operationId,expectedContentRevision,affectedRevisions,retired,books,preparedAt);
        Map<String,ManagedBookRemoval> evidence=new LinkedHashMap<>();for(ManagedBookRemoval book:removal.managedBooks())evidence.put(book.bookId(),book);
        for(BookKeepRequest request:current.bookDelivery().keepRequests().values()){
            BookCopyLocation location=request.location();
            if(location!=null&&location.state()==BookCopyLocation.State.PRESENT&&affected.contains(location.bindingId())&&!evidence.containsKey(request.bookId()))throw new IllegalStateException("affected binding contains a recorded managed book that was not observed: "+request.bookId());
        }
        var deliveries=new LinkedHashMap<>(current.bookDelivery().deliveries());
        for(ManagedBookRemoval book:removal.managedBooks()){
            BookDelivery delivery=deliveries.get(book.bookId());
            if(delivery==null||!delivery.deliveryToken().equals(book.deliveryToken())||!delivery.issued())throw new IllegalStateException("managed-book removal evidence is unknown or unissued for this campaign: "+book.bookId());
            if(!delivery.pendingOperationIds().isEmpty())throw new IllegalStateException("managed book already has a pending operation: "+book.bookId());
            HashSet<String> pending=new HashSet<>(delivery.pendingOperationIds());pending.add(id);
            deliveries.put(book.bookId(),new BookDelivery(book.bookId(),delivery.deliveryToken(),delivery.issued(),pending,delivery.firstDiscovery()));
        }
        BookDeliveryState deliveryState=new BookDeliveryState(current.bookDelivery().campaignId(),deliveries,current.bookDelivery().keepRequests());
        var removals=new LinkedHashMap<>(current.areaRemovals());removals.put(operationId,removal);long revision=nextRevision();
        commit(new ChestContentState(revision,current.templates(),current.bindings(),current.receipts(),current.pending(),current.schedule(),deliveryState,removals));return removal;
    }

    /** Finalizes only after the caller has durably saved the exact world result. There is no abort path. */
    public synchronized AreaContentRemoval commitAreaRemoval(UUID operationId,String durableWorldReceipt,String committedAt)throws IOException{
        AreaContentRemoval removal=requireAreaRemoval(operationId);
        if(removal.stage()==AreaContentRemoval.Stage.COMMITTED){if(!removal.durableWorldReceipt().orElseThrow().equals(durableWorldReceipt)||!removal.committedAt().orElseThrow().equals(committedAt))throw new IllegalStateException("area-content completion retry used different evidence");return removal;}
        AreaContentRemoval completed=removal.committed(durableWorldReceipt,committedAt);String id=operationId.toString();
        var bindings=new LinkedHashMap<>(current.bindings());for(String bindingId:removal.retiredBindingIds()){ChestBinding binding=bindings.get(bindingId);Long expected=removal.affectedBindingRevisions().get(bindingId);if(binding==null||binding.revision()!=expected)throw new IllegalStateException("retired binding changed before area-content completion: "+bindingId);bindings.remove(bindingId);}
        for(var entry:removal.affectedBindingRevisions().entrySet())if(!removal.retiredBindingIds().contains(entry.getKey())){ChestBinding binding=bindings.get(entry.getKey());if(binding==null||binding.revision()!=entry.getValue())throw new IllegalStateException("surviving affected binding changed before area-content completion: "+entry.getKey());}
        var deliveries=new LinkedHashMap<>(current.bookDelivery().deliveries());for(ManagedBookRemoval book:removal.managedBooks()){BookDelivery delivery=deliveries.get(book.bookId());if(delivery==null||!delivery.deliveryToken().equals(book.deliveryToken())||!delivery.pendingOperationIds().contains(id))throw new IllegalStateException("managed-book area-removal claim is missing: "+book.bookId());HashSet<String> pending=new HashSet<>(delivery.pendingOperationIds());pending.remove(id);deliveries.put(book.bookId(),new BookDelivery(book.bookId(),delivery.deliveryToken(),delivery.issued(),pending,delivery.firstDiscovery()));}
        Set<String> removedBooks=removal.managedBookIds();var requests=new LinkedHashMap<>(current.bookDelivery().keepRequests());for(BookKeepRequest request:current.bookDelivery().keepRequests().values()){BookCopyLocation location=request.location();if(location!=null&&removal.affectedBindingRevisions().containsKey(location.bindingId())&&removedBooks.contains(request.bookId())){BookCopyLocation absent=new BookCopyLocation(location.bindingId(),location.bindingRevision(),location.target(),BookCopyLocation.State.ABSENT);requests.put(request.bookId(),copyRequest(request,request.ruleFingerprint(),request.approvedRule(),request.overrideRule(),request.ownIssuance(),request.status(),absent,request.lastSessionNumber(),request.lastCycle(),committedAt));}}
        BookDeliveryState deliveryState=new BookDeliveryState(current.bookDelivery().campaignId(),deliveries,requests);var removals=new LinkedHashMap<>(current.areaRemovals());removals.put(operationId,completed);long revision=nextRevision();
        commit(new ChestContentState(revision,current.templates(),bindings,current.receipts(),current.pending(),current.schedule(),deliveryState,removals));return completed;
    }

    public synchronized AreaContentRemoval requireAreaRemoval(UUID operationId){if(operationId==null)throw new IllegalArgumentException("area-content operation ID is required");AreaContentRemoval removal=current.areaRemovals().get(operationId);if(removal==null)throw new IllegalArgumentException("unknown area-content operation: "+operationId);return removal;}

    public synchronized ChestTemplate putTemplate(String id, InventorySnapshot contents, boolean replace) throws IOException {
        requireNoPendingEdits();
        boolean exists = current.templates().containsKey(id);
        requireReplacement(exists, replace, "template", id);
        long revision = nextRevision();
        ChestTemplate template = new ChestTemplate(id, revision, contents);
        var templates = new LinkedHashMap<>(current.templates());
        templates.put(id, template);
        commit(new ChestContentState(revision, templates, current.bindings(), current.receipts(), current.pending(), current.schedule(), current.bookDelivery(), current.areaRemovals()));
        return template;
    }

    public synchronized ChestBinding putBinding(String id, String templateId, long templateRevision,
                                                 String anchorId, long anchorRevision, ChestPolicy policy,
                                                 boolean replace) throws IOException {
        requireNoPendingEdits();
        ChestTemplate template = current.templates().get(templateId);
        if (template == null) throw new IllegalArgumentException("Unknown chest template: " + templateId);
        if (template.revision() != templateRevision) throw new IllegalStateException("Chest template revision is stale: " + templateId);
        boolean exists = current.bindings().containsKey(id);
        requireReplacement(exists, replace, "binding", id);
        long revision = nextRevision();
        ChestBinding binding = new ChestBinding(id, revision, templateId, templateRevision, anchorId, anchorRevision, policy);
        var bindings = new LinkedHashMap<>(current.bindings());
        bindings.put(id, binding);
        commit(new ChestContentState(revision, current.templates(), bindings, current.receipts(), current.pending(), current.schedule(), current.bookDelivery(), current.areaRemovals()));
        return binding;
    }

    /** Binds a loot type directly to a physical container using a stable location-derived identity. */
    public synchronized ChestBinding putTypedBinding(String lootType, ChestTarget physicalTarget,
                                                      boolean replace) throws IOException {
        requireNoPendingEdits();
        if (physicalTarget == null) throw new IllegalArgumentException("typed chest physical target is required");
        ContentIds.requireNamespacedId(lootType, "loot type");
        String id = typedBindingId(physicalTarget);
        ChestBinding existing = current.bindings().get(id);
        requireReplacement(existing != null, replace, "binding", id);
        if (existing != null && (!existing.typed() || !samePhysicalTarget(existing.directTarget(), physicalTarget))) {
            throw new IllegalStateException("Stable typed chest ID collides with another binding: " + id);
        }
        for (ChestBinding binding : current.bindings().values()) {
            if (!binding.id().equals(id) && binding.typed() && samePhysicalTarget(binding.directTarget(), physicalTarget)) {
                throw new IllegalStateException("A typed chest binding already owns this physical target: " + binding.id());
            }
        }
        long revision = nextRevision();
        ChestBinding binding = ChestBinding.typed(id, revision, lootType, physicalTarget, existing == null ? null : existing.directBook());
        var bindings = new LinkedHashMap<>(current.bindings());
        bindings.put(id, binding);
        var receipts = translatedReceiptForRetype(existing, binding);
        commit(new ChestContentState(revision, current.templates(), bindings, receipts, current.pending(), current.schedule(), current.bookDelivery(), current.areaRemovals()));
        return binding;
    }

    /** Updates only the optional direct named-book route; it neither fills nor resets a cycle receipt. */
    public synchronized ChestBinding setDirectBook(String bindingId, long expectedBindingRevision,
                                                   BookChance directBook) throws IOException {
        requireNoPendingEdits();
        ChestBinding existing = requireCurrentBinding(bindingId, expectedBindingRevision);
        if (!existing.typed()) throw new IllegalArgumentException("Only typed chest bindings support a direct book");
        if (java.util.Objects.equals(existing.directBook(), directBook)) return existing;
        long revision = nextRevision();
        ChestBinding replacement = ChestBinding.typed(existing.id(), revision, existing.lootType(), existing.directTarget(), directBook);
        var bindings = new LinkedHashMap<>(current.bindings());
        bindings.put(bindingId, replacement);
        commit(new ChestContentState(revision, current.templates(), bindings, current.receipts(), current.pending(), current.schedule(), current.bookDelivery(), current.areaRemovals()));
        return replacement;
    }

    public synchronized void removeTypedBinding(String id) throws IOException {
        requireNoPreparedAreaRemoval();
        ChestBinding binding = current.bindings().get(id);
        if (binding == null) throw new IllegalArgumentException("Unknown chest binding: " + id);
        if (!binding.typed()) throw new IllegalArgumentException("Chest binding is not typed: " + id);
        if (current.pending().values().stream().anyMatch(operation -> operation.bindingId().equals(id))) {
            throw new IllegalStateException("Resolve the pending chest operation before removing binding: " + id);
        }
        var bindings = new LinkedHashMap<>(current.bindings());
        bindings.remove(id);
        long revision = nextRevision();
        commit(new ChestContentState(revision, current.templates(), bindings, current.receipts(), current.pending(), current.schedule(), current.bookDelivery(), current.areaRemovals()));
    }

    public synchronized PendingChestOperation begin(PendingChestOperation proposed, boolean targetWasEmpty) throws IOException {
        if (proposed != null && (proposed.purpose() != PendingChestOperation.Purpose.FILL || !proposed.bookClaims().isEmpty())) {
            throw new IllegalArgumentException("Managed-book INSERT/MOVE/claims must use beginLore");
        }
        return beginOperation(proposed, targetWasEmpty, Map.of(), false);
    }

    /** Publishes the inventory intent and every managed-book reservation in one durable save. */
    public synchronized PendingChestOperation beginLore(PendingChestOperation proposed,
                                                        Map<String, BookDefinition.Rule> currentRules,
                                                        boolean targetMutationAuthorized) throws IOException {
        if (proposed == null || proposed.bookClaims().isEmpty()) throw new IllegalArgumentException("lore operation must claim at least one book");
        if (currentRules == null) throw new IllegalArgumentException("current book rules are required");
        return beginOperation(proposed, targetMutationAuthorized, Map.copyOf(currentRules), true);
    }

    private PendingChestOperation beginOperation(PendingChestOperation proposed, boolean targetMutationAuthorized,
                                                 Map<String, BookDefinition.Rule> currentRules, boolean lore) throws IOException {
        requireNoPreparedAreaRemoval();
        if (proposed == null) throw new IllegalArgumentException("pending chest operation is required");
        ChestBinding binding = requireCurrentBinding(proposed.bindingId(), proposed.bindingRevision());
        if (!targetMutationAuthorized) throw new IllegalStateException(binding.typed()
                ? "Replacing typed chest contents was not explicitly authorized."
                : "Target inventory is not completely empty.");
        if (current.pending().containsKey(proposed.operationId())) throw new IllegalStateException("Chest operation already exists: " + proposed.operationId());
        if (hasLockedEndpoint(proposed)) {
            throw new IllegalStateException("Another unresolved chest operation already owns this target.");
        }
        if (binding.typed()) {
            if (!proposed.target().equals(binding.directTarget())) throw new IllegalStateException("Operation target does not match the typed binding's physical target.");
            if (proposed.purpose() == PendingChestOperation.Purpose.FILL) {
                if (proposed.cycle() < 0) throw new IllegalStateException("Typed fill operation requires a nonnegative cycle.");
                requireTypedEligible(binding.id(), proposed.sessionNumber(), proposed.cycle());
                boolean replacesReceipt = current.receipts().stream().anyMatch(receipt -> receipt.bindingId().equals(binding.id()) && receipt.cycle() >= 0);
                if (!replacesReceipt && current.receipts().size() >= ChestContentState.MAX_RECEIPTS) {
                    throw new IllegalStateException("Chest receipt limit reached; archive legacy campaign receipts before adding typed chests.");
                }
            }
            if (proposed.purpose() == PendingChestOperation.Purpose.MOVE) {
                ChestBinding source = requireCurrentBinding(proposed.sourceBindingId(), proposed.sourceBindingRevision());
                if (!source.typed() || !proposed.sourceTarget().equals(source.directTarget())) {
                    throw new IllegalStateException("Move source does not match its pinned typed binding.");
                }
                if (!source.lootType().equals(binding.lootType())) throw new IllegalStateException("Move endpoints must use the same loot table.");
            }
        } else {
            if (proposed.purpose() != PendingChestOperation.Purpose.FILL) throw new IllegalStateException("Legacy chest bindings support only fill operations.");
            if (proposed.cycle() != -1) throw new IllegalStateException("Legacy chest operation cycle must be -1.");
            if (proposed.before().equals(proposed.after())) throw new IllegalStateException("Chest template would not change the empty target.");
            if (current.receipts().size() >= ChestContentState.MAX_RECEIPTS) {
                throw new IllegalStateException("Chest receipt limit reached; archive this campaign before further fills.");
            }
            if (!proposed.target().anchorId().equals(binding.anchorId()) || proposed.target().anchorRevision() != binding.anchorRevision()) {
                throw new IllegalStateException("Operation target does not match the binding's pinned anchor.");
            }
            ChestTemplate template = requireCurrentTemplate(binding);
            if (!template.contents().equals(proposed.after())) throw new IllegalStateException("Operation after snapshot does not match its pinned template.");
            if (proposed.before().size() != template.contents().size()) throw new IllegalStateException("Target inventory size does not match the template.");
            requireEligible(binding, proposed.sessionNumber());
        }
        BookDeliveryState deliveryState = current.bookDelivery();
        if (lore) deliveryState = reserveClaims(proposed, currentRules);
        long revision = nextRevision();
        var pending = new LinkedHashMap<>(current.pending());
        pending.put(proposed.operationId(), proposed);
        commit(new ChestContentState(revision, current.templates(), current.bindings(), current.receipts(), pending, current.schedule(), deliveryState, current.areaRemovals()));
        return proposed;
    }

    public synchronized ChestReceipt complete(String operationId, String completedAt) throws IOException {
        PendingChestOperation operation = requirePending(operationId);
        ChestBinding binding = requireCurrentBinding(operation.bindingId(), operation.bindingRevision());
        if (operation.purpose() == PendingChestOperation.Purpose.FILL && !binding.typed() && current.receipts().size() >= ChestContentState.MAX_RECEIPTS) {
            throw new IllegalStateException("Chest receipt limit reached; archive this campaign before further fills.");
        }
        ChestReceipt receipt = operation.purpose() == PendingChestOperation.Purpose.FILL
                ? new ChestReceipt(operationId, binding.id(), operation.sessionNumber(), operation.cycle(), completedAt) : null;
        var receipts = new ArrayList<>(current.receipts());
        if (receipt != null) {
            if (binding.typed()) receipts.removeIf(existing -> existing.bindingId().equals(binding.id()) && existing.cycle() >= 0);
            receipts.add(receipt);
        }
        var pending = new LinkedHashMap<>(current.pending());
        pending.remove(operationId);
        BookDeliveryState deliveryState = finalizeClaims(operation, completedAt);
        long revision = nextRevision();
        commit(new ChestContentState(revision, current.templates(), current.bindings(), receipts, pending, current.schedule(), deliveryState, current.areaRemovals()));
        return receipt;
    }

    /** Resolve proof that no mutation occurred. This deliberately creates no execution receipt. */
    public synchronized void abortUnchanged(String operationId) throws IOException {
        requirePending(operationId);
        var pending = new LinkedHashMap<>(current.pending());
        pending.remove(operationId);
        BookDeliveryState deliveryState = releaseClaims(requirePending(operationId));
        long revision = nextRevision();
        commit(new ChestContentState(revision, current.templates(), current.bindings(), current.receipts(), pending, current.schedule(), deliveryState, current.areaRemovals()));
    }

    public synchronized boolean hasCompletedCycle(String bindingId, int sessionNumber, long cycle) {
        ContentIds.requireId(bindingId, "binding");
        if (sessionNumber < 0) throw new IllegalArgumentException("session number cannot be negative");
        if (cycle < 0) throw new IllegalArgumentException("typed chest cycle cannot be negative");
        return current.receipts().stream().anyMatch(receipt -> receipt.bindingId().equals(bindingId)
                && receipt.sessionNumber() == sessionNumber && receipt.cycle() >= cycle);
    }

    /** Persists schedule changes before making them visible. Identical clocks do not consume revisions. */
    public synchronized void saveSchedule(LootSchedule schedule) throws IOException {
        if (schedule == null) throw new IllegalArgumentException("loot schedule is required");
        if (schedule.equals(current.schedule())) return;
        long revision = nextRevision();
        commit(new ChestContentState(revision, current.templates(), current.bindings(), current.receipts(), current.pending(), schedule, current.bookDelivery(), current.areaRemovals()));
    }

    /** Computes the campaign/book token without allocating ledger state. */
    public synchronized String deliveryToken(String bookId) { return current.bookDelivery().deliveryToken(bookId); }

    /** A deliberate GM issuance is claimed before inserting an inventory item; it is never auto-replayed. */
    public synchronized void claimOperatorIssuance(String bookId) throws IOException {
        requireNoPreparedAreaRemoval();
        String token=deliveryToken(bookId);
        BookDelivery old=current.bookDelivery().deliveries().get(bookId);
        if(old!=null&&!old.pendingOperationIds().isEmpty())throw new IllegalStateException("Resolve pending book deliveries first.");
        if(old!=null&&old.issued())return;
        var deliveries=new LinkedHashMap<>(current.bookDelivery().deliveries());
        deliveries.put(bookId,new BookDelivery(bookId,token,true,Set.of(),null));
        saveBookDelivery(deliveries,current.bookDelivery().keepRequests());
    }

    /** Records only the first real player-inventory receipt; later observations are idempotent. */
    public synchronized BookDiscovery recordDiscovery(String bookId, String deliveryToken,
                                                      String playerUuid, String discoveredAt) throws IOException {
        BookDelivery delivery = current.bookDelivery().deliveries().get(bookId);
        if (delivery == null || !delivery.deliveryToken().equals(deliveryToken) || !delivery.issued()) {
            throw new IllegalStateException("Managed book provenance has no completed issuance: " + bookId);
        }
        if (delivery.firstDiscovery() != null) return delivery.firstDiscovery();
        BookDiscovery discovery = new BookDiscovery(playerUuid, discoveredAt);
        var deliveries = new LinkedHashMap<>(current.bookDelivery().deliveries());
        deliveries.put(bookId, new BookDelivery(bookId, delivery.deliveryToken(), true, delivery.pendingOperationIds(), discovery));
        var requests = new LinkedHashMap<>(current.bookDelivery().keepRequests());
        BookKeepRequest request = requests.get(bookId);
        if (request != null && request.status() != BookKeepRequest.Status.CANCELLED) {
            requests.put(bookId, copyRequest(request, request.ruleFingerprint(), request.approvedRule(), request.overrideRule(),
                    request.ownIssuance(), BookKeepRequest.Status.COMPLETED_FOUND, request.location(),
                    request.lastSessionNumber(), request.lastCycle(), discoveredAt));
        }
        saveBookDelivery(deliveries, requests);
        return discovery;
    }

    public synchronized BookKeepRequest startKeep(String bookId, String lootTableId, String ruleFingerprint,
                                                  BookDefinition.Rule rule, boolean overrideRule,
                                                  String requestedAt) throws IOException {
        requireNoPreparedAreaRemoval();
        if (rule == null) throw new IllegalArgumentException("book rule is required");
        BookDelivery delivery = current.bookDelivery().deliveries().get(bookId);
        if (delivery != null && delivery.firstDiscovery() != null) throw new IllegalStateException("A found book cannot be kept again: " + bookId);
        BookKeepRequest previous = current.bookDelivery().keepRequests().get(bookId);
        if (previous != null && previous.status() != BookKeepRequest.Status.CANCELLED) {
            throw new IllegalStateException("A keep request already exists for book: " + bookId);
        }
        requireKeepOverride(rule, overrideRule, delivery, false);
        BookKeepRequest request = new BookKeepRequest(bookId, lootTableId, ruleFingerprint, rule, overrideRule,
                false, BookKeepRequest.Status.ACTIVE, previous == null || !previous.lootTableId().equals(lootTableId) ? null : previous.location(), -1, -1,
                requestedAt, requestedAt);
        var requests = new LinkedHashMap<>(current.bookDelivery().keepRequests());
        requests.put(bookId, request);
        saveBookDelivery(current.bookDelivery().deliveries(), requests);
        return request;
    }

    /** Confirms a changed rule fingerprint and reactivates a suspended request. */
    public synchronized BookKeepRequest reviewKeep(String bookId, String ruleFingerprint,
                                                   BookDefinition.Rule rule, boolean overrideRule,
                                                   String reviewedAt) throws IOException {
        requireNoPreparedAreaRemoval();
        BookKeepRequest request = requireKeepRequest(bookId);
        if (request.status() != BookKeepRequest.Status.SUSPENDED_RULE
                && request.status() != BookKeepRequest.Status.SUSPENDED_ONCE) {
            throw new IllegalStateException("Only a suspended keep request can be reviewed: " + bookId);
        }
        BookDelivery delivery = current.bookDelivery().deliveries().get(bookId);
        if (delivery != null && delivery.firstDiscovery() != null) throw new IllegalStateException("A found book cannot resume keeping: " + bookId);
        requireKeepOverride(rule, overrideRule, delivery, request.ownIssuance());
        BookKeepRequest reviewed = copyRequest(request, ruleFingerprint, rule, overrideRule,
                request.ownIssuance(), BookKeepRequest.Status.ACTIVE, request.location(),
                request.lastSessionNumber(), request.lastCycle(), reviewedAt);
        saveKeepRequest(reviewed);
        return reviewed;
    }

    public synchronized BookKeepRequest suspendKeep(String bookId, BookKeepRequest.Status reason,
                                                    String suspendedAt) throws IOException {
        requireNoPreparedAreaRemoval();
        if (reason != BookKeepRequest.Status.SUSPENDED_RULE && reason != BookKeepRequest.Status.SUSPENDED_ONCE) {
            throw new IllegalArgumentException("keep suspension reason must be SUSPENDED_RULE or SUSPENDED_ONCE");
        }
        BookKeepRequest request = requireKeepRequest(bookId);
        if (!request.active()) throw new IllegalStateException("Only an active keep request can be suspended: " + bookId);
        BookKeepRequest suspended = copyRequest(request, request.ruleFingerprint(), request.approvedRule(), request.overrideRule(),
                request.ownIssuance(), reason, request.location(), request.lastSessionNumber(), request.lastCycle(), suspendedAt);
        saveKeepRequest(suspended);
        return suspended;
    }

    public synchronized BookKeepRequest stopKeep(String bookId, String stoppedAt) throws IOException {
        requireNoPreparedAreaRemoval();
        BookKeepRequest request = requireKeepRequest(bookId);
        if (request.status() == BookKeepRequest.Status.COMPLETED_FOUND) throw new IllegalStateException("A found keep request is already complete: " + bookId);
        if (request.status() == BookKeepRequest.Status.CANCELLED) return request;
        BookKeepRequest stopped = copyRequest(request, request.ruleFingerprint(), request.approvedRule(), request.overrideRule(),
                request.ownIssuance(), BookKeepRequest.Status.CANCELLED, request.location(),
                request.lastSessionNumber(), request.lastCycle(), stoppedAt);
        saveKeepRequest(stopped);
        return stopped;
    }

    /** Publishes authoritative copy-location evidence without creating or completing an inventory intent. */
    public synchronized BookKeepRequest reconcileKeepLocation(String bookId, BookCopyLocation location,
                                                              String reconciledAt) throws IOException {
        requireNoPreparedAreaRemoval();
        if (location == null) throw new IllegalArgumentException("authoritative copy location is required; use ABSENT to retain its pinned target");
        BookKeepRequest request = requireKeepRequest(bookId);
        BookKeepRequest reconciled = copyRequest(request, request.ruleFingerprint(), request.approvedRule(), request.overrideRule(),
                request.ownIssuance(), request.status(), location, request.lastSessionNumber(), request.lastCycle(), reconciledAt);
        saveKeepRequest(reconciled);
        return reconciled;
    }

    /** Marks a safely reconciled no-move/adoption generation so restart cannot relocate it again. */
    public synchronized BookKeepRequest saveKeepCycle(String bookId, int sessionNumber, long cycle,
                                                      String reconciledAt) throws IOException {
        requireNoPreparedAreaRemoval();
        if (sessionNumber < 0 || cycle < 0) throw new IllegalArgumentException("keep reconciliation session and cycle must be nonnegative");
        BookKeepRequest request = requireKeepRequest(bookId);
        if (sessionNumber < request.lastSessionNumber()
                || (sessionNumber == request.lastSessionNumber() && cycle < request.lastCycle())) {
            throw new IllegalStateException("keep reconciliation cannot move backwards");
        }
        if (sessionNumber == request.lastSessionNumber() && cycle == request.lastCycle()) return request;
        BookKeepRequest reconciled = copyRequest(request, request.ruleFingerprint(), request.approvedRule(), request.overrideRule(),
                request.ownIssuance(), request.status(), request.location(), sessionNumber, cycle, reconciledAt);
        saveKeepRequest(reconciled);
        return reconciled;
    }

    public synchronized void requireEligible(ChestBinding binding, int sessionNumber) {
        if (sessionNumber < 0) throw new IllegalArgumentException("session number cannot be negative");
        if (binding.typed()) throw new IllegalArgumentException("typed chest bindings use generation eligibility");
        boolean used = switch (binding.policy()) {
            case MANUAL -> false;
            case ONCE_CAMPAIGN -> current.receipts().stream().anyMatch(receipt -> receipt.bindingId().equals(binding.id()));
            case ONCE_SESSION -> current.receipts().stream().anyMatch(receipt -> receipt.bindingId().equals(binding.id())
                    && receipt.sessionNumber() == sessionNumber);
        };
        if (used) throw new IllegalStateException(binding.policy() == ChestPolicy.ONCE_CAMPAIGN
                ? "Chest binding has already run in this campaign: " + binding.id()
                : "Chest binding has already run in session " + sessionNumber + ": " + binding.id());
    }

    public synchronized ChestBinding requireCurrentBinding(String id, long revision) {
        ChestBinding binding = current.bindings().get(id);
        if (binding == null) throw new IllegalArgumentException("Unknown chest binding: " + id);
        if (binding.revision() != revision) throw new IllegalStateException("Chest binding revision is stale: " + id);
        return binding;
    }

    public synchronized ChestTemplate requireCurrentTemplate(ChestBinding binding) {
        if (binding.typed()) throw new IllegalArgumentException("typed chest binding does not reference a template: " + binding.id());
        ChestTemplate template = current.templates().get(binding.templateId());
        if (template == null || template.revision() != binding.templateRevision()) {
            throw new IllegalStateException("Chest binding " + binding.id() + " references a missing or replaced template.");
        }
        return template;
    }

    public synchronized PendingChestOperation requirePending(String id) {
        PendingChestOperation pending = current.pending().get(id);
        if (pending == null) throw new IllegalArgumentException("Unknown pending chest operation: " + id);
        return pending;
    }

    private BookDeliveryState reserveClaims(PendingChestOperation operation,
                                            Map<String, BookDefinition.Rule> currentRules) {
        var deliveries = new LinkedHashMap<>(current.bookDelivery().deliveries());
        for (BookDeliveryClaim claim : operation.bookClaims()) {
            BookDefinition.Rule rule = currentRules.get(claim.bookId());
            if (rule == null) throw new IllegalArgumentException("Missing current rule for claimed book: " + claim.bookId());
            if (!claim.deliveryToken().equals(current.bookDelivery().deliveryToken(claim.bookId()))) {
                throw new IllegalStateException("Book claim token does not belong to this campaign: " + claim.bookId());
            }
            BookDelivery delivery = deliveries.get(claim.bookId());
            requireClaimEligible(claim, rule, delivery);
            Set<String> pendingIds = new HashSet<>(delivery == null ? Set.of() : delivery.pendingOperationIds());
            pendingIds.add(operation.operationId());
            deliveries.put(claim.bookId(), new BookDelivery(claim.bookId(), claim.deliveryToken(),
                    delivery != null && delivery.issued(), pendingIds, delivery == null ? null : delivery.firstDiscovery()));
        }
        return new BookDeliveryState(current.bookDelivery().campaignId(), deliveries, current.bookDelivery().keepRequests());
    }

    private void requireClaimEligible(BookDeliveryClaim claim, BookDefinition.Rule rule, BookDelivery delivery) {
        boolean issued = delivery != null && delivery.issued();
        boolean pending = delivery != null && !delivery.pendingOperationIds().isEmpty();
        boolean found = delivery != null && delivery.firstDiscovery() != null;
        BookKeepRequest keep = current.bookDelivery().keepRequests().get(claim.bookId());
        if (claim.keepRequest()) {
            if (keep == null || !keep.active()) throw new IllegalStateException("Book has no active keep request: " + claim.bookId());
            if (keep.approvedRule() != rule) throw new IllegalStateException("Keep request rule changed and must be reviewed: " + claim.bookId());
            if (found) throw new IllegalStateException("Found books cannot be kept: " + claim.bookId());
            if (pending) throw new IllegalStateException("Another managed-book claim is already pending: " + claim.bookId());
            if (rule == BookDefinition.Rule.OFF && !keep.overrideRule()) {
                throw new IllegalStateException("Distribution off requires this keep request's override: " + claim.bookId());
            }
            if (rule == BookDefinition.Rule.ONCE && issued && !keep.ownIssuance() && !keep.overrideRule()) {
                throw new IllegalStateException("An external Spawn once issuance requires this keep request's override: " + claim.bookId());
            }
            return;
        }
        switch (rule) {
            case OFF -> throw new IllegalStateException("Book distribution is off: " + claim.bookId());
            case ONCE -> {
                if (issued || pending) throw new IllegalStateException("Spawn once book already has an issuance or reservation: " + claim.bookId());
            }
            case UNTIL_FOUND -> {
                if (found) throw new IllegalStateException("Until found book was already discovered: " + claim.bookId());
            }
            case REPEAT -> { }
        }
    }

    private BookDeliveryState finalizeClaims(PendingChestOperation operation, String completedAt) {
        if (operation.bookClaims().isEmpty()) return current.bookDelivery();
        var deliveries = new LinkedHashMap<>(current.bookDelivery().deliveries());
        var requests = new LinkedHashMap<>(current.bookDelivery().keepRequests());
        for (BookDeliveryClaim claim : operation.bookClaims()) {
            BookDelivery delivery = deliveries.get(claim.bookId());
            if (delivery == null || !delivery.pendingOperationIds().contains(operation.operationId())) {
                throw new IllegalStateException("Managed-book reservation is missing for completion: " + claim.bookId());
            }
            Set<String> pendingIds = new HashSet<>(delivery.pendingOperationIds());
            pendingIds.remove(operation.operationId());
            deliveries.put(claim.bookId(), new BookDelivery(claim.bookId(), delivery.deliveryToken(), true,
                    pendingIds, delivery.firstDiscovery()));
            if (claim.keepRequest()) {
                BookKeepRequest request = requests.get(claim.bookId());
                if (request == null) throw new IllegalStateException("Keep request is missing for completion: " + claim.bookId());
                BookCopyLocation location = new BookCopyLocation(operation.bindingId(), operation.bindingRevision(),
                        operation.target(), BookCopyLocation.State.PRESENT);
                int session = operation.cycle() >= 0 ? operation.sessionNumber() : request.lastSessionNumber();
                long cycle = operation.cycle() >= 0 ? operation.cycle() : request.lastCycle();
                requests.put(claim.bookId(), copyRequest(request, request.ruleFingerprint(), request.approvedRule(),
                        request.overrideRule(), true, request.status(), location, session, cycle, completedAt));
            }
        }
        return new BookDeliveryState(current.bookDelivery().campaignId(), deliveries, requests);
    }

    private BookDeliveryState releaseClaims(PendingChestOperation operation) {
        if (operation.bookClaims().isEmpty()) return current.bookDelivery();
        var deliveries = new LinkedHashMap<>(current.bookDelivery().deliveries());
        for (BookDeliveryClaim claim : operation.bookClaims()) {
            BookDelivery delivery = deliveries.get(claim.bookId());
            if (delivery == null || !delivery.pendingOperationIds().contains(operation.operationId())) {
                throw new IllegalStateException("Managed-book reservation is missing for abort: " + claim.bookId());
            }
            Set<String> pendingIds = new HashSet<>(delivery.pendingOperationIds());
            pendingIds.remove(operation.operationId());
            deliveries.put(claim.bookId(), new BookDelivery(claim.bookId(), delivery.deliveryToken(), delivery.issued(),
                    pendingIds, delivery.firstDiscovery()));
        }
        return new BookDeliveryState(current.bookDelivery().campaignId(), deliveries, current.bookDelivery().keepRequests());
    }

    private boolean hasLockedEndpoint(PendingChestOperation proposed) {
        Set<String> proposedTargets = operationTargets(proposed);
        Set<String> proposedBindings = operationBindings(proposed);
        for (PendingChestOperation existing : current.pending().values()) {
            if (!java.util.Collections.disjoint(proposedTargets, operationTargets(existing))
                    || !java.util.Collections.disjoint(proposedBindings, operationBindings(existing))) return true;
        }
        return false;
    }

    private static Set<String> operationTargets(PendingChestOperation operation) {
        HashSet<String> values = new HashSet<>();
        values.add(physicalKey(operation.target()));
        if (operation.purpose() == PendingChestOperation.Purpose.MOVE) values.add(physicalKey(operation.sourceTarget()));
        return values;
    }

    private static Set<String> operationBindings(PendingChestOperation operation) {
        HashSet<String> values = new HashSet<>();
        values.add(operation.bindingId());
        if (operation.purpose() == PendingChestOperation.Purpose.MOVE) values.add(operation.sourceBindingId());
        return values;
    }

    private static String physicalKey(ChestTarget target) {
        return target.dimension() + '\u0000' + target.x() + '\u0000' + target.y() + '\u0000' + target.z();
    }

    private static void requireKeepOverride(BookDefinition.Rule rule, boolean overrideRule,
                                            BookDelivery delivery, boolean ownIssuance) {
        if (rule == null) throw new IllegalArgumentException("book rule is required");
        if (rule == BookDefinition.Rule.OFF && !overrideRule) {
            throw new IllegalStateException("Distribution off requires a request-specific override");
        }
        if (rule == BookDefinition.Rule.ONCE && delivery != null
                && (delivery.issued() || !delivery.pendingOperationIds().isEmpty())
                && !ownIssuance && !overrideRule) {
            throw new IllegalStateException("An existing Spawn once claim requires a request-specific override");
        }
    }

    private BookKeepRequest requireKeepRequest(String bookId) {
        BookKeepRequest request = current.bookDelivery().keepRequests().get(bookId);
        if (request == null) throw new IllegalArgumentException("Unknown keep request: " + bookId);
        return request;
    }

    private void saveKeepRequest(BookKeepRequest request) throws IOException {
        var requests = new LinkedHashMap<>(current.bookDelivery().keepRequests());
        requests.put(request.bookId(), request);
        saveBookDelivery(current.bookDelivery().deliveries(), requests);
    }

    private void saveBookDelivery(Map<String, BookDelivery> deliveries,
                                  Map<String, BookKeepRequest> requests) throws IOException {
        long revision = nextRevision();
        BookDeliveryState state = new BookDeliveryState(current.bookDelivery().campaignId(), deliveries, requests);
        commit(new ChestContentState(revision, current.templates(), current.bindings(), current.receipts(),
                current.pending(), current.schedule(), state, current.areaRemovals()));
    }

    private static BookKeepRequest copyRequest(BookKeepRequest request, String fingerprint,
                                               BookDefinition.Rule rule, boolean overrideRule,
                                               boolean ownIssuance, BookKeepRequest.Status status,
                                               BookCopyLocation location, int lastSession, long lastCycle,
                                               String statusAt) {
        return new BookKeepRequest(request.bookId(), request.lootTableId(), fingerprint, rule, overrideRule,
                ownIssuance, status, location, lastSession, lastCycle, request.requestedAt(), statusAt);
    }

    private void commit(ChestContentState candidate) throws IOException {
        repository.save(candidate);
        current = candidate;
    }

    private long nextRevision() {
        try { return Math.addExact(current.revision(), 1); }
        catch (ArithmeticException exception) { throw new IllegalStateException("chest content revision is exhausted", exception); }
    }

    private static void requireReplacement(boolean exists, boolean replace, String type, String id) {
        if (exists && !replace) throw new IllegalStateException("Chest " + type + " already exists: " + id);
        if (!exists && replace) throw new IllegalStateException("Chest " + type + " does not exist for replacement: " + id);
    }

    private void requireNoPendingEdits() {
        if (!current.pending().isEmpty()) throw new IllegalStateException("Resolve pending chest operations before editing templates or bindings.");
        requireNoPreparedAreaRemoval();
    }

    private void requireNoPreparedAreaRemoval(){if(current.areaRemovals().values().stream().anyMatch(removal->removal.stage()==AreaContentRemoval.Stage.PREPARED))throw new IllegalStateException("Resolve the pending area-content removal before changing chest or book state.");}

    private void requireTypedEligible(String bindingId, int sessionNumber, long cycle) {
        ChestReceipt last = current.receipts().stream()
                .filter(receipt -> receipt.bindingId().equals(bindingId) && receipt.cycle() >= 0)
                .findFirst().orElse(null);
        if (last == null) return;
        if (sessionNumber < last.sessionNumber()) {
            throw new IllegalStateException("Typed chest operation belongs to an older session than its last receipt: " + bindingId);
        }
        if (sessionNumber == last.sessionNumber() && cycle <= last.cycle()) {
            throw new IllegalStateException("Typed chest generation has already completed: " + bindingId + " cycle " + cycle);
        }
    }

    private static String typedBindingId(ChestTarget target) {
        String identity = target.dimension() + "|" + target.x() + "|" + target.y() + "|" + target.z();
        return "chest_" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private static boolean samePhysicalTarget(ChestTarget left, ChestTarget right) {
        return left.dimension().equals(right.dimension()) && left.x() == right.x() && left.y() == right.y() && left.z() == right.z();
    }

    private java.util.List<ChestReceipt> translatedReceiptForRetype(ChestBinding previous, ChestBinding replacement) {
        if (previous != null && previous.typed() && previous.lootType().equals(replacement.lootType())) return current.receipts();
        if (current.schedule().sessionNumber() < 0) return current.receipts();
        LootCycle scheduled = current.schedule().types().get(replacement.lootType());
        long generation = scheduled == null ? 0 : scheduled.generation();
        ArrayList<ChestReceipt> translated = new ArrayList<>(current.receipts().size());
        boolean changed = false;
        for (ChestReceipt receipt : current.receipts()) {
            if (receipt.bindingId().equals(replacement.id()) && receipt.cycle() >= 0
                    && receipt.sessionNumber() == current.schedule().sessionNumber()) {
                translated.add(new ChestReceipt(receipt.operationId(), receipt.bindingId(), receipt.sessionNumber(), generation, receipt.completedAt()));
                changed = true;
            } else {
                translated.add(receipt);
            }
        }
        return changed ? translated : current.receipts();
    }
}
