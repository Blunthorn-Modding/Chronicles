package io.github.chronicle.core.server;

import java.util.Objects;
import java.util.UUID;

/** Package-private identity checks shared by one-use server offers. */
final class OfferIdentity {
    private OfferIdentity() { }

    static boolean sameConnection(Object offered, Object current) {
        return offered != null && offered == current;
    }

    static boolean accepts(Object offeredConnection, UUID token, UUID parent, Object currentConnection,
                           UUID request, boolean close) {
        return token != null && request != null && sameConnection(offeredConnection, currentConnection)
                && (Objects.equals(token, request) || close && Objects.equals(parent, request));
    }
}
