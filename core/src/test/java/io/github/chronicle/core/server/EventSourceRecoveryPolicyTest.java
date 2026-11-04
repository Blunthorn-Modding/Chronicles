package io.github.chronicle.core.server;

import io.github.chronicle.core.progression.SourceLedger;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EventSourceRecoveryPolicyTest {
    private SourceLedger.Receipt receipt(String id,String type,String authority,SourceLedger.Stage stage){return new SourceLedger.Receipt(id,type,"minecraft:warden","",null,true,Map.of(),authority,stage);}
    @Test void onlyExactEntitySpawnEvidenceCanUseTheMetadataRecoveryException(){
        assertTrue(EventSources.entityMetadataOnly(receipt("entity",EntitySpawnEventSources.TYPE,"entityspawn:attempt",SourceLedger.Stage.PREPARED)));
        assertFalse(EventSources.entityMetadataOnly(receipt("book","chronicle_core:book_found","entityspawn:attempt",SourceLedger.Stage.PREPARED)));
        assertFalse(EventSources.entityMetadataOnly(receipt("entity",EntitySpawnEventSources.TYPE,"book:attempt",SourceLedger.Stage.PREPARED)));
        assertFalse(EventSources.entityMetadataOnly(null));
    }
    @Test void reloadCannotSmuggleUnresolvedBookOrForgeWorkAlongsideAnEventHold(){
        var entity=receipt("entity",EntitySpawnEventSources.TYPE,"entityspawn:attempt",SourceLedger.Stage.PREPARED);
        var book=receipt("book","chronicle_core:book_found","book:attempt",SourceLedger.Stage.PREPARED);
        assertTrue(EventSources.metadataOnlyPending(new SourceLedger(0,Map.of(entity.id(),entity))));
        assertFalse(EventSources.metadataOnlyPending(new SourceLedger(0,Map.of(entity.id(),entity,book.id(),book))));
        assertTrue(EventSources.metadataOnlyPending(new SourceLedger(0,Map.of(book.id(),book.at(SourceLedger.Stage.COMMITTED)))));
        var entities=new SourceLedger(0,Map.of(entity.id(),entity));
        var books=new SourceLedger(0,Map.of(book.id(),book));
        var empty=new SourceLedger(0,Map.of());
        assertTrue(EventSources.metadataOnlyRecovery(entities,entities));
        assertFalse(EventSources.metadataOnlyRecovery(entities,empty));
        var other=receipt("other",EntitySpawnEventSources.TYPE,"entityspawn:other",SourceLedger.Stage.PREPARED);
        assertFalse(EventSources.metadataOnlyRecovery(entities,new SourceLedger(0,Map.of(other.id(),other))));
        assertFalse(EventSources.metadataOnlyRecovery(entities,new SourceLedger(1,Map.of(entity.id(),entity.at(SourceLedger.Stage.COMMITTED)))));
        var withTerminal=new SourceLedger(0,Map.of(entity.id(),entity,book.id(),book.at(SourceLedger.Stage.COMMITTED)));
        assertTrue(EventSources.metadataOnlyRecovery(withTerminal,withTerminal));
        assertFalse(EventSources.metadataOnlyRecovery(withTerminal,entities));
        assertFalse(EventSources.metadataOnlyRecovery(entities,withTerminal));
        assertFalse(EventSources.metadataOnlyRecovery(books,empty));
        assertFalse(EventSources.metadataOnlyRecovery(books,entities));
        assertFalse(EventSources.metadataOnlyRecovery(entities,books));
        assertFalse(EventSources.metadataOnlyRecovery(null,entities));
    }
}
