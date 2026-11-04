package io.github.chronicle.core.server;

import io.github.chronicle.core.history.ImmutableHistoryArchive;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EventHistorySchedulerAuditTest {
    @TempDir Path root;
    @Test void auditFailureReportsCompletedArchivalAndItsExactEvidenceRemainsReadable()throws Exception{
        var archive=new ImmutableHistoryArchive(root.resolve("history"),"test",1024);byte[] evidence={1,2,3};int[] calls={0};var outcome=new EventHistoryMaintenance.Result(1,1,1,0,0,0);
        var error=assertThrows(EventHistoryScheduler.AuditFailedAfterArchive.class,()->EventHistoryScheduler.archiveAndAudit(()->{calls[0]++;archive.publish("source",evidence);return outcome;},result->{throw new IOException("disk unavailable");}));
        assertEquals(1,calls[0]);assertEquals(outcome,error.completed);assertTrue(error.getMessage().contains("Archival completed"));assertArrayEquals(evidence,archive.read("source").orElseThrow());
    }
    @Test void anUnchangedOrFailedArchiveNeverClaimsACompletedAudit()throws Exception{
        int[] audit={0};var empty=new EventHistoryMaintenance.Result(0,0,0,0,0,0);assertEquals(empty,EventHistoryScheduler.archiveAndAudit(()->empty,r->audit[0]++));assertEquals(0,audit[0]);assertThrows(IOException.class,()->EventHistoryScheduler.archiveAndAudit(()->{throw new IOException("not archived");},r->audit[0]++));assertEquals(0,audit[0]);
    }
}
