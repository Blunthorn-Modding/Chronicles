package io.github.chronicle.core.books;

/** The first authoritative player-inventory receipt for a managed named book. */
public record BookDiscovery(String playerUuid, String discoveredAt) {
    public BookDiscovery {
        BookDelivery.requireUuid(playerUuid, "discovery player UUID");
        BookDeliveryState.requireText(discoveredAt, "discovery time", 128);
    }
}
