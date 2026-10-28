package io.github.chronicle.core.progression;

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
