package io.github.chronicle.core.content;

import io.github.chronicle.core.books.BookDeliveryClaim;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Durable intent retained until the live inventory proves whether mutation completed. */
public record PendingChestOperation(String operationId, String actorUuid, String actorName,
                                    String bindingId, long bindingRevision, int sessionNumber,
                                    long cycle,
                                    ChestTarget target, InventorySnapshot before, InventorySnapshot after,
                                    String createdAt, Purpose purpose,
                                    String sourceBindingId, long sourceBindingRevision,
                                    ChestTarget sourceTarget, InventorySnapshot sourceBefore, InventorySnapshot sourceAfter,
                                    List<BookDeliveryClaim> bookClaims) {
    public enum Purpose { FILL, INSERT, MOVE }

    public PendingChestOperation {
        ContentIds.requireOperationId(operationId);
        ContentIds.requireUuid(actorUuid);
        ContentIds.requireText(actorName, "actor name", 64);
        ContentIds.requireId(bindingId, "binding");
        ContentIds.requireRevision(bindingRevision, "binding");
        if (sessionNumber < 0) throw new IllegalArgumentException("operation session number cannot be negative");
        if (cycle < -1) throw new IllegalArgumentException("operation cycle cannot be less than -1");
        Objects.requireNonNull(target, "operation target is required");
        Objects.requireNonNull(before, "operation before snapshot is required");
        Objects.requireNonNull(after, "operation after snapshot is required");
        if (before.size() != after.size()) throw new IllegalArgumentException("operation snapshots must have the same size");
        ContentIds.requireText(createdAt, "operation creation time", 128);
        Objects.requireNonNull(purpose, "operation purpose is required");
        if (purpose == Purpose.MOVE) {
            ContentIds.requireId(sourceBindingId, "source binding");
            ContentIds.requireRevision(sourceBindingRevision, "source binding");
            Objects.requireNonNull(sourceTarget, "move source target is required");
            Objects.requireNonNull(sourceBefore, "move source before snapshot is required");
            Objects.requireNonNull(sourceAfter, "move source after snapshot is required");
            if (sourceBefore.size() != sourceAfter.size()) throw new IllegalArgumentException("move source snapshots must have the same size");
            if (samePhysicalTarget(sourceTarget, target)) throw new IllegalArgumentException("move source and destination must be different inventories");
        } else if (sourceBindingId != null || sourceBindingRevision != 0 || sourceTarget != null || sourceBefore != null || sourceAfter != null) {
            throw new IllegalArgumentException("only MOVE operations may contain source evidence");
        }
        if (bookClaims == null || bookClaims.size() > 256) throw new IllegalArgumentException("book claims may contain at most 256 entries");
        bookClaims = List.copyOf(bookClaims);
        HashSet<String> claimedBooks = new HashSet<>();
        for (BookDeliveryClaim claim : bookClaims) {
            if (claim == null) throw new IllegalArgumentException("book claims must not contain null");
            if (!claimedBooks.add(claim.bookId())) throw new IllegalArgumentException("an operation may claim each book only once");
        }
        if ((purpose == Purpose.INSERT || purpose == Purpose.MOVE) && bookClaims.size() != 1) {
            throw new IllegalArgumentException(purpose + " must claim exactly one managed book");
        }
    }

    /** Source-compatible constructor for schema-2 fill operations. */
    public PendingChestOperation(String operationId, String actorUuid, String actorName,
                                 String bindingId, long bindingRevision, int sessionNumber,
                                 long cycle, ChestTarget target, InventorySnapshot before, InventorySnapshot after,
                                 String createdAt) {
        this(operationId, actorUuid, actorName, bindingId, bindingRevision, sessionNumber, cycle,
                target, before, after, createdAt, Purpose.FILL, null, 0, null, null, null, List.of());
    }

    /** Source-compatible constructor for schema-1/template operations. */
    public PendingChestOperation(String operationId, String actorUuid, String actorName,
                                 String bindingId, long bindingRevision, int sessionNumber,
                                 ChestTarget target, InventorySnapshot before, InventorySnapshot after,
                                 String createdAt) {
        this(operationId, actorUuid, actorName, bindingId, bindingRevision, sessionNumber, -1,
                target, before, after, createdAt, Purpose.FILL, null, 0, null, null, null, List.of());
    }

    private static boolean samePhysicalTarget(ChestTarget left, ChestTarget right) {
        return left.dimension().equals(right.dimension()) && left.x() == right.x() && left.y() == right.y() && left.z() == right.z();
    }
}
