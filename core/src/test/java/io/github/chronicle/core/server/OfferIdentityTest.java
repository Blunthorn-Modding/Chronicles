package io.github.chronicle.core.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferIdentityTest {
    private static final UUID OLD = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CURRENT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test void absentConnectionsAndTokensNeverAuthorizeAnAction(){
        Object connection=new Object();assertFalse(OfferIdentity.sameConnection(null,null));
        assertFalse(OfferIdentity.accepts(connection,CURRENT,null,connection,null,true));
        assertFalse(OfferIdentity.accepts(connection,null,null,connection,CURRENT,false));
    }

    @Test void delayedDisconnectOnlyMatchesTheConnectionThatCreatedTheOffer() {
        Object oldConnection = new Object();
        Object reconnected = new Object();

        assertTrue(OfferIdentity.sameConnection(reconnected, reconnected));
        assertFalse(OfferIdentity.sameConnection(reconnected, oldConnection));
        assertFalse(OfferIdentity.accepts(reconnected, CURRENT, null, oldConnection, CURRENT, false));
    }

    @Test void closeAcceptsOnlyTheCurrentOrImmediateParentTokenOnTheSameConnection() {
        Object connection = new Object();
        Object otherConnection = new Object();

        assertTrue(OfferIdentity.accepts(connection, CURRENT, OLD, connection, CURRENT, true));
        assertTrue(OfferIdentity.accepts(connection, CURRENT, OLD, connection, OLD, true));
        assertFalse(OfferIdentity.accepts(connection, CURRENT, OLD, connection, OLD, false));
        assertFalse(OfferIdentity.accepts(connection, CURRENT, null, connection, OLD, true));
        assertFalse(OfferIdentity.accepts(connection, CURRENT, OLD, otherConnection, CURRENT, true));
        assertFalse(OfferIdentity.accepts(connection, CURRENT, OLD, otherConnection, OLD, true));
    }
}
