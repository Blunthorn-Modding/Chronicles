package io.github.chronicle.core.content;

import io.github.chronicle.core.books.BookCopyLocation;
import io.github.chronicle.core.books.BookDefinition;
import io.github.chronicle.core.books.BookKeepRequest;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AreaContentRemovalServiceTest {
    private static final String BOOK="test:journal";
    private static final String PREPARED="2026-10-26T12:00:00Z";
    private static final String COMMITTED="2026-10-26T12:01:00Z";

    @Test void prepareClaimsExactBindingsAndBooksBeforeWorldMutationAndBlocksCompetingMutators()throws Exception{
        Memory repository=new Memory();Fixture fixture=fixture(repository);ChestContentService service=fixture.service;UUID operation=UUID.randomUUID();long expected=service.state().revision();ManagedBookRemoval book=book(service);
        AreaContentRemoval prepared=service.prepareAreaRemoval(operation,expected,Set.of(fixture.survivor.id(),fixture.retired.id()),Set.of(fixture.retired.id()),Set.of(book),PREPARED);
        assertEquals(AreaContentRemoval.Stage.PREPARED,prepared.stage());assertEquals(Map.of(fixture.retired.id(),fixture.retired.revision(),fixture.survivor.id(),fixture.survivor.revision()),prepared.affectedBindingRevisions());
        assertTrue(service.state().bindings().containsKey(fixture.retired.id()));assertEquals(Set.of(operation.toString()),service.state().bookDelivery().deliveries().get(BOOK).pendingOperationIds());assertEquals(BookCopyLocation.State.PRESENT,service.state().bookDelivery().keepRequests().get(BOOK).location().state());
        assertThrows(IllegalStateException.class,()->service.putTypedBinding("test:other",target(9),false));assertThrows(IllegalStateException.class,()->service.removeTypedBinding(fixture.retired.id()));assertThrows(IllegalStateException.class,()->service.claimOperatorIssuance("test:other"));assertThrows(IllegalStateException.class,()->service.startKeep("test:other","test:loot","rule",BookDefinition.Rule.REPEAT,false,PREPARED));
        long revision=service.state().revision();assertEquals(prepared,service.prepareAreaRemoval(operation,expected,Set.of(fixture.retired.id(),fixture.survivor.id()),Set.of(fixture.retired.id()),Set.of(book),PREPARED));assertEquals(revision,service.state().revision());
        assertThrows(IllegalStateException.class,()->service.prepareAreaRemoval(operation,expected,Set.of(fixture.retired.id()),Set.of(fixture.retired.id()),Set.of(book),PREPARED));
    }

    @Test void durableWorldReceiptAtomicallyRetiresOnlyAuthorizedBindingAndMarksObservedKeepCopyAbsent()throws Exception{
        Memory repository=new Memory();Fixture fixture=fixture(repository);ChestContentService service=fixture.service;UUID operation=UUID.randomUUID();long expected=service.state().revision();ManagedBookRemoval book=book(service);service.prepareAreaRemoval(operation,expected,Set.of(fixture.survivor.id(),fixture.retired.id()),Set.of(fixture.retired.id()),Set.of(book),PREPARED);
        var discovery=service.recordDiscovery(BOOK,book.deliveryToken(),UUID.randomUUID().toString(),"2026-10-26T12:00:30Z");repository.fail=true;
        assertThrows(IOException.class,()->service.commitAreaRemoval(operation,"world-save:abc",COMMITTED));assertTrue(service.state().bindings().containsKey(fixture.retired.id()));assertEquals(AreaContentRemoval.Stage.PREPARED,service.requireAreaRemoval(operation).stage());assertEquals(BookCopyLocation.State.PRESENT,service.state().bookDelivery().keepRequests().get(BOOK).location().state());
        repository.fail=false;AreaContentRemoval completed=service.commitAreaRemoval(operation,"world-save:abc",COMMITTED);assertEquals(AreaContentRemoval.Stage.COMMITTED,completed.stage());assertFalse(service.state().bindings().containsKey(fixture.retired.id()));assertEquals(fixture.survivor,service.state().bindings().get(fixture.survivor.id()));assertEquals(BookCopyLocation.State.ABSENT,service.state().bookDelivery().keepRequests().get(BOOK).location().state());assertEquals(BookKeepRequest.Status.COMPLETED_FOUND,service.state().bookDelivery().keepRequests().get(BOOK).status());assertEquals(discovery,service.state().bookDelivery().deliveries().get(BOOK).firstDiscovery());assertTrue(service.state().bookDelivery().deliveries().get(BOOK).pendingOperationIds().isEmpty());
        long revision=service.state().revision();assertEquals(completed,service.commitAreaRemoval(operation,"world-save:abc",COMMITTED));assertEquals(revision,service.state().revision());assertThrows(IllegalStateException.class,()->service.commitAreaRemoval(operation,"world-save:other",COMMITTED));
        ChestContentService recovered=new ChestContentService(repository);assertEquals(completed,recovered.requireAreaRemoval(operation));assertFalse(recovered.state().bindings().containsKey(fixture.retired.id()));
    }

    @Test void presentKeepCopyRequiresObservedTokenAndRetirementMustBeAffected()throws Exception{
        Fixture fixture=fixture(new Memory());ChestContentService service=fixture.service;long revision=service.state().revision();
        assertThrows(IllegalStateException.class,()->service.prepareAreaRemoval(UUID.randomUUID(),revision,Set.of(fixture.retired.id()),Set.of(fixture.retired.id()),Set.of(),PREPARED));
        assertThrows(IllegalArgumentException.class,()->service.prepareAreaRemoval(UUID.randomUUID(),revision,Set.of(fixture.survivor.id()),Set.of(fixture.retired.id()),Set.of(book(service)),PREPARED));
        assertThrows(IllegalStateException.class,()->service.prepareAreaRemoval(UUID.randomUUID(),revision-1,Set.of(fixture.retired.id()),Set.of(fixture.retired.id()),Set.of(book(service)),PREPARED));
        assertTrue(service.state().areaRemovals().isEmpty());
    }

    @Test void unboundObservedBookCanBeJournaledWithoutRetiringBindings()throws Exception{
        Memory repository=new Memory();ChestContentService service=new ChestContentService(repository);service.claimOperatorIssuance(BOOK);ManagedBookRemoval book=book(service);UUID operation=UUID.randomUUID();
        service.prepareAreaRemoval(operation,service.state().revision(),Set.of(),Set.of(),Set.of(book),PREPARED);service.commitAreaRemoval(operation,"world-save:unbound",COMMITTED);
        assertTrue(service.state().bindings().isEmpty());assertTrue(service.state().bookDelivery().deliveries().get(BOOK).issued());assertTrue(service.state().bookDelivery().deliveries().get(BOOK).pendingOperationIds().isEmpty());
    }

    @Test void failedPreparePublishesNoReservation()throws Exception{
        Memory repository=new Memory();Fixture fixture=fixture(repository);ChestContentService service=fixture.service;ChestContentState before=service.state();repository.fail=true;
        assertThrows(IOException.class,()->service.prepareAreaRemoval(UUID.randomUUID(),before.revision(),Set.of(fixture.retired.id()),Set.of(fixture.retired.id()),Set.of(book(service)),PREPARED));assertEquals(before,service.state());
    }

    private static Fixture fixture(Memory repository)throws Exception{
        ChestContentService service=new ChestContentService(repository);ChestBinding retired=service.putTypedBinding("test:loot",target(1),false);ChestBinding survivor=service.putTypedBinding("test:loot",target(2),false);service.claimOperatorIssuance(BOOK);service.startKeep(BOOK,"test:loot","rule",BookDefinition.Rule.REPEAT,false,"2026-10-26T11:00:00Z");service.reconcileKeepLocation(BOOK,new BookCopyLocation(retired.id(),retired.revision(),retired.directTarget(),BookCopyLocation.State.PRESENT),"2026-10-26T11:01:00Z");return new Fixture(service,retired,survivor);
    }
    private static ManagedBookRemoval book(ChestContentService service){return new ManagedBookRemoval(BOOK,service.deliveryToken(BOOK));}
    private static ChestTarget target(int x){return new ChestTarget("temporary",1,"minecraft:overworld",x,64,2,"minecraft:barrel");}
    private record Fixture(ChestContentService service,ChestBinding retired,ChestBinding survivor){}
    private static final class Memory implements ChestContentRepository{ChestContentState state=ChestContentState.empty();boolean fail;public ChestContentState load(){return state;}public void save(ChestContentState next)throws IOException{if(fail)throw new IOException("injected");state=next;}}
}
