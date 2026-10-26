package io.github.chronicle.core.entry;

import io.github.chronicle.core.content.InventorySnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Immutable planned effects plus the durable stage of one account's once-only first entry. */
public record RoleEntryOperation(UUID operationId, UUID playerId, String roleId, String roleFingerprint, String roleContent,
                                 InventorySnapshot beforeInventory, InventorySnapshot afterInventory,
                                 PlayerPosition beforePosition, PlayerPosition afterPosition,
                                 String movementPurpose, List<RoleBookGrant> books,
                                 Stage stage, String detail, String createdAt) {
    public static final int INVENTORY_SLOTS = 36;
    public static final int MAX_BOOKS = 32;
    public static final int MAX_DETAIL = 512;

    public enum Stage { PREPARED, APPLYING, COMPLETE, ABORTED, CONFLICT }

    public RoleEntryOperation {
        Objects.requireNonNull(operationId, "role-entry operation ID is required");
        Objects.requireNonNull(playerId, "role-entry player ID is required");
        if (roleId == null || roleId.length() > 160 || !roleId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("role-entry role ID must be a namespaced identifier");
        }
        if (roleFingerprint == null || !roleFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("role fingerprint must be a lowercase SHA-256 digest");
        }
        if (roleContent == null || roleContent.isBlank() || roleContent.length() > 131_072) {
            throw new IllegalArgumentException("pinned role content must contain at most 131072 characters");
        }
        if (!roleFingerprint.equals(fingerprint(roleContent))) {
            throw new IllegalArgumentException("role fingerprint does not match pinned role content");
        }
        if (beforeInventory == null || beforeInventory.size() != INVENTORY_SLOTS
                || afterInventory == null || afterInventory.size() != INVENTORY_SLOTS) {
            throw new IllegalArgumentException("role-entry inventory evidence must contain exactly 36 slots");
        }
        Objects.requireNonNull(beforePosition, "before position is required");
        Objects.requireNonNull(afterPosition, "after position is required");
        movementPurpose = Objects.requireNonNull(movementPurpose, "movement purpose is required");
        if (!movementPurpose.isEmpty() && (movementPurpose.length() > 160
                || !movementPurpose.matches("[a-z0-9_.-]+(?::[a-z0-9_./-]+)?"))) {
            throw new IllegalArgumentException("movement purpose is invalid");
        }
        books = List.copyOf(Objects.requireNonNull(books, "role books are required"));
        if (books.size() > MAX_BOOKS || books.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("role entry may contain at most " + MAX_BOOKS + " books");
        }
        HashSet<String> ids = new HashSet<>();
        if (books.stream().anyMatch(book -> !ids.add(book.bookId()))) {
            throw new IllegalArgumentException("role-entry book IDs must be distinct");
        }
        Objects.requireNonNull(stage, "role-entry stage is required");
        detail = bounded(detail, "role-entry detail", MAX_DETAIL, false);
        createdAt = bounded(createdAt, "role-entry creation time", 64, true);
    }

    public RoleEntryOperation withStage(Stage value, String reason) {
        return new RoleEntryOperation(operationId, playerId, roleId, roleFingerprint, roleContent, beforeInventory,
                afterInventory, beforePosition, afterPosition, movementPurpose, books, value, reason, createdAt);
    }

    public boolean pending() { return stage == Stage.PREPARED || stage == Stage.APPLYING || stage == Stage.CONFLICT; }

    private static String bounded(String value, String name, int maximum, boolean required) {
        if (value == null || value.length() > maximum || value.codePoints().anyMatch(Character::isISOControl)
                || (required && value.isBlank())) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }

    private static String fingerprint(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
