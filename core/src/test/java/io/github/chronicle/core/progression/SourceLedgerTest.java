package io.github.chronicle.core.progression;

import io.github.chronicle.core.history.ImmutableHistoryArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceLedgerTest {
    @TempDir Path directory;
    private static SourceLedger.Receipt receipt(String id) {
        return new SourceLedger.Receipt(id,"chronicle_core:book_opened","quiet:journal","",UUID.randomUUID(),true,
                Map.of("quiet:ready",false),"transient:"+UUID.randomUUID(),SourceLedger.Stage.PREPARED);
    }
    @Test void preparedEvidenceNeverClaimsCompletionAndHasPinnedImmutableFlags() {
        Map<String,Boolean> flags=new LinkedHashMap<>(Map.of("quiet:ready",false));
        var receipt=new SourceLedger.Receipt("source:one","chronicle_core:book_opened","quiet:journal","",UUID.randomUUID(),true,flags,"transient:one",SourceLedger.Stage.PREPARED);
        SourceLedger prepared=SourceLedger.empty().prepare(receipt);flags.put("quiet:ready",true);
        assertFalse(prepared.receipts().get(receipt.id()).flags().get("quiet:ready"));
        assertEquals(SourceLedger.Stage.PREPARED,prepared.receipts().get(receipt.id()).stage());
        SourceLedger committed=prepared.finish(receipt.id(),true);
        assertEquals(SourceLedger.Stage.COMMITTED,committed.receipts().get(receipt.id()).stage());
        assertSame(committed,committed.prepare(receipt));
        assertSame(committed,committed.finish(receipt.id(),true));
        assertThrows(IllegalStateException.class,()->committed.finish(receipt.id(),false));
        assertThrows(UnsupportedOperationException.class,()->committed.receipts().clear());
        assertThrows(UnsupportedOperationException.class,()->receipt.flags().clear());
        var conflict=new SourceLedger.Receipt(receipt.id(),receipt.type(),receipt.target(),receipt.activity(),receipt.actor(),true,Map.of("quiet:ready",true),receipt.authority(),receipt.stage());
        assertThrows(IllegalStateException.class,()->committed.prepare(conflict));
        assertThrows(IllegalStateException.class,()->prepared.retire(Set.of(receipt.id())));
    }
    @Test void persistsPendingAndCompletedEvidenceAndRejectsExternalChanges()throws IOException {
        Path file=directory.resolve("sources.bin");SourceLedgerStore store=new SourceLedgerStore(file);assertEquals(SourceLedger.empty(),store.load());
        var first=receipt("source:first");var second=receipt("source:second");
        SourceLedger ledger=SourceLedger.empty().prepare(first).prepare(second).finish(first.id(),true);store.save(ledger);
        assertEquals(ledger,new SourceLedgerStore(file).load());
        byte[] before=Files.readAllBytes(file);byte[] damaged=before.clone();damaged[0]^=1;Files.write(file,damaged);
        assertThrows(IOException.class,()->store.save(ledger.finish(second.id(),false)));
        assertArrayEquals(damaged,Files.readAllBytes(file));
        assertThrows(IOException.class,()->new SourceLedgerStore(file).load());
    }
    @Test void decodeRejectsTrailingTruncatedAndUnknownSchemaBytes()throws IOException {
        byte[] valid=SourceLedgerStore.encode(SourceLedger.empty().prepare(receipt("source:first")));
        assertThrows(IllegalArgumentException.class,()->SourceLedgerStore.decode(Arrays.copyOf(valid,valid.length-1)));
        assertThrows(IllegalArgumentException.class,()->SourceLedgerStore.decode(Arrays.copyOf(valid,valid.length+1)));
        valid[7]=2;assertThrows(IllegalArgumentException.class,()->SourceLedgerStore.decode(valid));
    }
    @Test void fullLedgerDoesNotPrunePendingOrCommittedEvidence() {
        Map<String,SourceLedger.Receipt> records=new LinkedHashMap<>();
        for(int i=0;i<SourceLedger.MAX_RECEIPTS;i++){var receipt=receipt("source:"+i);records.put(receipt.id(),i%2==0?receipt:receipt.at(SourceLedger.Stage.COMMITTED));}
        SourceLedger full=new SourceLedger(SourceLedger.MAX_RECEIPTS,records);
        assertThrows(IllegalStateException.class,()->full.prepare(receipt("source:overflow")));
        assertEquals(SourceLedger.MAX_RECEIPTS,full.receipts().size());
        assertEquals(SourceLedger.Stage.CANCELLED,full.finish("source:0",false).receipts().get("source:0").stage());
    }
    @Test void failedActivitiesKeepOnlyBoundedCancelledHistoryAndNeverPruneCommittedOrPending() {
        var committed=receipt("source:committed");var pending=receipt("source:pending");
        SourceLedger ledger=SourceLedger.empty().prepare(committed).finish(committed.id(),true).prepare(pending);
        for(int i=0;i<SourceLedger.MAX_RECEIPTS*2;i++){var attempt=receipt("source:cancelled_"+i);ledger=ledger.prepare(attempt).finish(attempt.id(),false);}
        assertEquals(SourceLedger.MAX_CANCELLED+2,ledger.receipts().size());
        assertEquals(SourceLedger.Stage.COMMITTED,ledger.receipts().get(committed.id()).stage());
        assertEquals(SourceLedger.Stage.PREPARED,ledger.receipts().get(pending.id()).stage());
        assertFalse(ledger.receipts().containsKey("source:cancelled_0"));
    }
    @Test void definitiveAuthorityRefusalsReclaimMoreThanTheGlobalCapacityWithoutPruningRealEvidence() {
        var committed=receipt("source:committed");var pending=receipt("source:pending");SourceLedger ledger=SourceLedger.empty().prepare(committed).finish(committed.id(),true).prepare(pending);
        for(int i=0;i<SourceLedger.MAX_RECEIPTS*2;i++) {
            var candidate=receipt("source:refused_"+i);ledger=ledger.prepare(candidate).cancelAuthority(candidate.authority());
        }
        assertEquals(SourceLedger.MAX_CANCELLED+2,ledger.receipts().size());assertEquals(SourceLedger.Stage.COMMITTED,ledger.receipts().get(committed.id()).stage());assertEquals(SourceLedger.Stage.PREPARED,ledger.receipts().get(pending.id()).stage());
        assertSame(ledger,ledger.cancelAuthority(committed.authority()));
    }
    @Test void unavailableSaveDoesNotPublishAnyFile()throws IOException {
        Path parent=directory.resolve("blocked");SourceLedgerStore store=new SourceLedgerStore(parent.resolve("sources.bin"));store.load();
        Files.writeString(parent,"not a directory");
        assertThrows(IOException.class,()->store.save(SourceLedger.empty().prepare(receipt("source:first"))));
        assertFalse(Files.exists(parent.resolve("sources.bin")));
    }
    @Test void conflictingArchiveStopsBeforeLivePrune()throws Exception {
        Path file=directory.resolve("sources.bin");SourceLedgerStore store=new SourceLedgerStore(file);store.load();
        SourceLedger.Receipt terminal=receipt("source:terminal").at(SourceLedger.Stage.COMMITTED);
        SourceLedger live=new SourceLedger(1,Map.of(terminal.id(),terminal));store.save(live);
        new ImmutableHistoryArchive(directory.resolve("event-source-history"),"event-source",SourceLedgerStore.MAX_BYTES)
                .publish(terminal.id(),new byte[]{1});
        assertThrows(IOException.class,()->store.save(live.retire(Set.of(terminal.id()))));
        assertEquals(terminal,new SourceLedgerStore(file).load().receipts().get(terminal.id()));
    }
    @Test void retryAfterArchivePublicationBeforeLivePruneIsIdempotent()throws Exception{
        SourceLedger.Receipt terminal=receipt("source:interrupted").at(SourceLedger.Stage.COMMITTED);SourceLedger live=new SourceLedger(1,Map.of(terminal.id(),terminal));
        Path primary=directory.resolve("primary/sources.bin");SourceLedgerStore store=new SourceLedgerStore(primary);store.load();store.save(live);
        Path shadow=directory.resolve("shadow/sources.bin");SourceLedgerStore completed=new SourceLedgerStore(shadow);completed.load();completed.save(live);completed.save(live.retire(Set.of(terminal.id())));
        Path shadowArchive=shadow.resolveSibling("event-source-history"),primaryArchive=primary.resolveSibling("event-source-history");Files.createDirectories(primaryArchive);
        try(var files=Files.list(shadowArchive)){Path archived=files.findFirst().orElseThrow();Files.copy(archived,primaryArchive.resolve(archived.getFileName()));}
        store.save(live.retire(Set.of(terminal.id())));
        assertTrue(new SourceLedgerStore(primary).load().receipts().isEmpty());assertEquals(terminal,store.archived(terminal.id()).orElseThrow());
    }
    @Test void archivedTerminalIdsCannotReplayAndMoreThanOneLiveCapacityCanProceed()throws Exception {
        Path file=directory.resolve("sources.bin");SourceLedgerStore store=new SourceLedgerStore(file);store.load();
        Map<String,SourceLedger.Receipt> first=new LinkedHashMap<>();
        for(int i=0;i<SourceLedger.MAX_RECEIPTS;i++){String id="source:old_"+i;var r=new SourceLedger.Receipt(id,"chronicle_core:block_opened","*","",new UUID(0,i),true,Map.of(),"transient:old_"+i,SourceLedger.Stage.COMMITTED);first.put(id,r);}
        SourceLedger full=new SourceLedger(1,first);store.save(full);store.save(full.retire(first.keySet()));
        assertEquals(first.get("source:old_0"),store.archived("source:old_0").orElseThrow());
        assertThrows(IOException.class,()->store.save(new SourceLedger(3,Map.of("source:old_0",first.get("source:old_0")))));
        Map<String,SourceLedger.Receipt> second=new LinkedHashMap<>();
        for(int i=0;i<SourceLedger.MAX_RECEIPTS;i++){String id="source:new_"+i;var r=new SourceLedger.Receipt(id,"chronicle_core:block_opened","*","",new UUID(1,i),true,Map.of(),"transient:new_"+i,SourceLedger.Stage.COMMITTED);second.put(id,r);}
        store.save(new SourceLedger(3,second));assertEquals(SourceLedger.MAX_RECEIPTS,store.load().receipts().size());
    }
    @Test void automaticCancelledEvictionArchivesTheExactRetiredIdentity() throws Exception {
        var store=new SourceLedgerStore(directory.resolve("cancelled.bin"));
        var ledger=store.load();
        for(int i=0;i<SourceLedger.MAX_CANCELLED;i++) {
            String id="source:cancel_"+i;
            ledger=ledger.prepare(receipt(id));store.save(ledger);
            ledger=ledger.finish(id,false);store.save(ledger);
        }
        ledger=ledger.prepare(receipt("source:next"));store.save(ledger);
        ledger=ledger.finish("source:next",false);store.save(ledger);
        assertFalse(ledger.receipts().containsKey("source:cancel_0"));
        assertEquals(SourceLedger.Stage.CANCELLED,store.archived("source:cancel_0").orElseThrow().stage());
        var retired=ledger.prepare(receipt("source:cancel_0"));
        assertThrows(IOException.class,()->store.save(retired));
        assertFalse(store.load().receipts().containsKey("source:cancel_0"));
    }
    @Test void forgeProposalIsImmutableAndPinsTheActualChangedOutput()throws IOException {
        var evidence=new ForgeResultEvidence(UUID.randomUUID(),UUID.randomUUID(),"managed:"+UUID.randomUUID(),"{first}","{ingredient}","{enchanted-result}");
        String hash=evidence.save(directory);assertTrue(hash.matches("[0-9a-f]{64}"));assertEquals(hash,evidence.save(directory));
        assertEquals(evidence,ForgeResultEvidence.load(directory,evidence.operation(),hash));
        var changed=new ForgeResultEvidence(evidence.operation(),evidence.actor(),evidence.target(),evidence.first(),evidence.second(),"{different-result}");
        assertThrows(IOException.class,()->changed.save(directory));
    }
    @Test void forgeResultEvidenceRejectsSymbolicLinkParentForSaveAndLoad()throws IOException {
        Path real=directory.resolve("actual");Files.createDirectories(real);Path link=directory.resolve("linked");
        try{Files.createSymbolicLink(link,real);}catch(IOException|UnsupportedOperationException|SecurityException unavailable){org.junit.jupiter.api.Assumptions.assumeTrue(false,"Windows symbolic-link privilege unavailable");}
        var evidence=new ForgeResultEvidence(UUID.randomUUID(),UUID.randomUUID(),"*","{first}","{ingredient}","{result}");
        String hash=evidence.save(real.resolve("results"));
        assertThrows(IOException.class,()->evidence.save(link.resolve("results")));
        assertThrows(IOException.class,()->ForgeResultEvidence.load(link.resolve("results"),evidence.operation(),hash));
    }
}
