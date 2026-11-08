package io.github.chronicle.lastcouncil.complications;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ComplicationServiceTest {
    static class Memory implements ComplicationStorage {
        byte[] data; boolean failBefore, failAfter;
        public byte[] load() { return data == null ? null : data.clone(); }
        public void save(byte[] bytes) throws IOException {
            if (failBefore) throw new IOException("before");
            data = bytes.clone();
            if (failAfter) throw new IOException("after");
        }
    }
    private ComplicationService service(Memory storage, int choice) throws IOException {
        return ComplicationService.load(storage, bound -> choice == 0 ? 0 : bound - 1);
    }
    @Test void allocationCanReachFirstAndLastAndKeepsTotal() throws Exception {
        for (int choice : List.of(0, 1)) {
            var s = service(new Memory(), choice); s.initialize(ComplicationConfig.defaults(), UUID.randomUUID());
            assertEquals(2, s.snapshot().slots().size());
            assertTrue(s.snapshot().slots().stream().allMatch(slot -> slot.meeting() == (choice == 0 ? 1 : 3)));
            var slots = s.snapshot().slots();
            assertTrue(Math.abs(slots.get(0).target() - slots.get(1).target()) >= 6000);
            assertFalse(s.snapshot().toString().contains("target"));
        }
    }
    @Test void disabledEveningIsStillADurableConfigurationDecision() throws Exception {
        Memory memory=new Memory();var s=service(memory,0);var c=ComplicationConfig.defaults();
        var disabled=new ComplicationConfig(c.poolId(),0,c.meetings(),c.earliestTicks(),c.latestTicks(),c.spacingTicks());
        s.initialize(disabled,UUID.randomUUID());
        var restored=service(memory,1);
        assertTrue(restored.isInitialized());assertEquals(disabled,restored.config());assertTrue(restored.snapshot().slots().isEmpty());
        assertThrows(IllegalStateException.class,()->restored.initialize(c,UUID.randomUUID()));
    }
    @Test void spacingForceAndRestartPreserveClaims() throws Exception {
        Memory memory = new Memory(); var s = service(memory, 0); s.initialize(ComplicationConfig.defaults(), UUID.randomUUID());
        s.observeClock(1, 0, true); s.observeClock(1, 6000, true);
        var first = s.due(1, false).orElseThrow(); s.claim(first.ordinal());
        var recovered = service(memory, 1); recovered.observeClock(1, 6000, false);
        assertEquals(first, recovered.due(1, false).orElseThrow()); recovered.deliver(first.ordinal());
        assertTrue(recovered.due(1, true).isEmpty());
        recovered.observeClock(1, 6000, true); recovered.observeClock(1, 12000, true);
        assertTrue(recovered.due(1, true).isPresent());
        assertThrows(IllegalArgumentException.class, () -> service(memory, 0).observeClock(1, 1, false));
    }
    @Test void transitionsDoNotAccrueAndFutureDoesNotBlock() throws Exception {
        var s = service(new Memory(), 1); s.initialize(ComplicationConfig.defaults(), UUID.randomUUID());
        s.observeClock(1, 10, true); s.observeClock(1, 110, true); s.observeClock(0, 110, false);
        s.observeClock(1, 10000, false); s.observeClock(1, 10000, true); s.observeClock(1, 10100, true);
        assertEquals(200, s.elapsed(1)); assertFalse(s.pendingCurrentMeeting(1)); assertTrue(s.due(1, true).isEmpty());
    }
    @Test void redistributesWithoutInflationAndRecordsEarlyEnd() throws Exception {
        var s = service(new Memory(), 1); var config = ComplicationConfig.defaults(); s.initialize(config, UUID.randomUUID());
        s.observeClock(1, 0, false); s.cancelFuture(config, 1, List.of(2));
        assertEquals(2, s.snapshot().slots().size()); assertTrue(s.snapshot().slots().stream().allMatch(slot -> slot.meeting() == 2));
        assertThrows(IllegalStateException.class, () -> s.cancelFuture(s.config(), 1, List.of()));
        s.exceptionalEnd("evening stopped");
        assertTrue(s.snapshot().slots().stream().allMatch(slot -> slot.phase() == ComplicationService.Phase.CANCELLED));
    }
    @Test void claimedDeliveryMustBeReconciledAndCannotSkipMeeting() throws Exception {
        var s = service(new Memory(), 0); s.initialize(ComplicationConfig.defaults(), UUID.randomUUID());
        s.observeClock(1, 0, true);
        var claim = s.due(1, true).orElseThrow(); s.claim(claim.ordinal());
        assertThrows(IllegalStateException.class, () -> s.exceptionalEnd("stop"));
        assertThrows(IllegalArgumentException.class, () -> s.observeClock(2, 1, true));
        assertEquals(claim, s.due(1, true).orElseThrow());
        s.deliver(claim.ordinal()); s.exceptionalEnd("stop");
        assertEquals(ComplicationService.Phase.DELIVERED, s.snapshot().slots().get(claim.ordinal()).phase());
    }
    @Test void cancellationPersistsEligibleSetAndRejectsStaleMeeting() throws Exception {
        Memory memory = new Memory(); var s = service(memory, 1); var original = ComplicationConfig.defaults();
        s.initialize(original, UUID.randomUUID()); s.observeClock(1, 0, false);
        assertThrows(IllegalStateException.class, () -> s.cancelFuture(original, 2, List.of(3)));
        s.cancelFuture(original, 1, List.of(2));
        assertEquals(List.of(1, 2), service(memory, 0).config().meetings());
        assertThrows(IllegalStateException.class, () -> s.cancelFuture(original, 1, List.of(3)));
        assertThrows(IllegalArgumentException.class, () -> s.cancelFuture(s.config(), 1, List.of(3)));
    }
    @Test void rejectsNoncanonicalNumericEncoding() throws Exception {
        for (String replacement : List.of("1.0", "1e0")) {
            Memory memory = new Memory(); var s = service(memory, 0); s.initialize(ComplicationConfig.defaults(), UUID.randomUUID());
            String json = new String(memory.data, java.nio.charset.StandardCharsets.UTF_8);
            memory.data = json.replace("\"ordinal\":1", "\"ordinal\":" + replacement).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertThrows(IOException.class, () -> service(memory, 0));
        }
    }
    @Test void uncertainWritesPoisonUntilReloadBeforeAndAfterPublication() throws Exception {
        for (boolean after : List.of(false, true)) {
            Memory memory = new Memory(); var s = service(memory, 0); s.initialize(ComplicationConfig.defaults(), UUID.randomUUID());
            memory.failBefore = !after; memory.failAfter = after;
            assertThrows(IOException.class, () -> s.observeClock(1, 10, true));
            assertThrows(IllegalStateException.class, s::snapshot);
            memory.failBefore = false; memory.failAfter = false;
            var reloaded = service(memory, 0);
            assertEquals(after ? 10 : -1, reloaded.snapshot().lastSharedTick());
        }
    }
}
