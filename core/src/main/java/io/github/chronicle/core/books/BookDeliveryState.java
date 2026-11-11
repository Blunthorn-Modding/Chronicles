package io.github.chronicle.core.books;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Bounded campaign identity, named-book lifecycle ledger, and keep-request ledger. */
public record BookDeliveryState(String campaignId, Map<String, BookDelivery> deliveries,
                                Map<String, BookKeepRequest> keepRequests) {
    public static final int MAX_BOOKS = 256;
    public static final int MAX_KEEP_REQUESTS = 256;
    public static final int MAX_PENDING_PER_BOOK = 64;
    private static final Pattern BOOK_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern SIMPLE_ID = Pattern.compile("[a-z][a-z0-9_]{0,63}(?::[a-z][a-z0-9_]{0,63})?");

    public BookDeliveryState {
        BookDelivery.requireUuid(campaignId, "campaign ID");
        deliveries = immutable(deliveries, "book deliveries", MAX_BOOKS);
        keepRequests = immutable(keepRequests, "keep requests", MAX_KEEP_REQUESTS);
        for (Map.Entry<String, BookDelivery> entry : deliveries.entrySet()) {
            String id = entry.getKey(); BookDelivery delivery = entry.getValue();
            if (!id.equals(delivery.bookId())) throw new IllegalArgumentException("delivery map key must match book ID");
            if (!token(campaignId, id).equals(delivery.deliveryToken())) {
                throw new IllegalArgumentException("delivery token must be derived from the campaign and book IDs");
            }
        }
        for (Map.Entry<String, BookKeepRequest> entry : keepRequests.entrySet()) {
            String id = entry.getKey(); BookKeepRequest request = entry.getValue();
            if (!id.equals(request.bookId())) throw new IllegalArgumentException("keep request map key must match book ID");
            BookDelivery delivery = deliveries.get(id);
            if (request.status() == BookKeepRequest.Status.COMPLETED_FOUND
                    && (delivery == null || delivery.firstDiscovery() == null)) {
                throw new IllegalArgumentException("a found keep request requires a first-discovery receipt");
            }
        }
    }

    public static BookDeliveryState create() {
        return new BookDeliveryState(UUID.randomUUID().toString(), Map.of(), Map.of());
    }

    /** Stable in-memory identity for a legacy file until its next real schema-3 save. */
    public static BookDeliveryState migrateLegacy(String legacySource) {
        if (legacySource == null) throw new IllegalArgumentException("legacy source is required");
        String id = UUID.nameUUIDFromBytes(("chronicle:chest-content-legacy\u0000" + legacySource)
                .getBytes(StandardCharsets.UTF_8)).toString();
        return new BookDeliveryState(id, Map.of(), Map.of());
    }

    /** Stable without allocating or persisting a delivery record. */
    public String deliveryToken(String bookId) {
        requireBookId(bookId);
        return token(campaignId, bookId);
    }

    private static String token(String campaignId, String bookId) {
        return UUID.nameUUIDFromBytes((campaignId + '\u0000' + bookId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    static void requireBookId(String value) {
        if (value == null || value.length() > BookDefinition.MAX_ID || !BOOK_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("book ID must be a namespaced identifier of at most " + BookDefinition.MAX_ID + " characters");
        }
    }

    static void requireSimpleId(String value, String field) {
        if (value == null || !SIMPLE_ID.matcher(value).matches()) throw new IllegalArgumentException(field + " ID is invalid");
    }

    static void requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + max + " characters");
        }
    }

    private static <T> Map<String, T> immutable(Map<String, T> source, String field, int max) {
        if (source == null || source.size() > max) throw new IllegalArgumentException(field + " may contain at most " + max + " entries");
        LinkedHashMap<String, T> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null || value == null) throw new IllegalArgumentException(field + " must not contain null");
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}
