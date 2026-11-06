package io.github.chronicle.core.ballots;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class BallotDurabilityTest {
    @TempDir Path root;
    private static BallotDefinition definition(UUID actor) {
        return new BallotDefinition(UUID.randomUUID(),"test","Question",List.of(new BallotOption("a","A",true),new BallotOption("b","B",false)),Map.of("one",new BallotElector(actor,"One")),1,BallotVisibility.SECRET);
    }
    @Test void malformedUnicodeCannotBeAcceptedAndChangedByPersistence() {
        for(String bad:List.of("bad\ud800","bad\udc00")) {
            assertThrows(IllegalArgumentException.class,()->new BallotOption("a",bad,true));
            assertThrows(IllegalArgumentException.class,()->new BallotElector(UUID.randomUUID(),bad));
        }
        assertDoesNotThrow(()->new BallotOption("a","Good \ud83d\ude00",true));
    }
    @Test void idempotentAcknowledgementsStillVerifyDurableAuthority() throws Exception {
        Path file=root.resolve("ballots.json");UUID actor=UUID.randomUUID();var d=definition(actor);
        var service=new BallotService(new BallotStore(file),Clock.systemUTC());service.open(d,actor);service.cast(d.id(),actor,"a");
        String authority=Files.readString(file);Files.writeString(file,"external");
        assertThrows(IOException.class,()->service.open(d,actor));
        assertThrows(IOException.class,()->service.cast(d.id(),actor,"a"));
        assertEquals("external",Files.readString(file));
        Files.writeString(file,authority);service.close(d.id(),actor);String terminal=Files.readString(file);Files.delete(file);
        assertThrows(IOException.class,()->service.close(d.id(),actor));
        assertFalse(Files.exists(file));Files.writeString(file,terminal);
        assertEquals(BallotStatus.CLOSED,new BallotService(new BallotStore(file),Clock.systemUTC()).state().ballots().get(d.id()).status());
    }
    @Test void competingConfirmationsAcceptExactlyOneAndPersistIt() throws Exception {
        Path file=root.resolve("ballots.json");UUID actor=UUID.randomUUID();var d=definition(actor);
        var service=new BallotService(new BallotStore(file),Clock.systemUTC());service.open(d,actor);
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(String option:List.of("a","b"))results.add(pool.submit(()->{start.await();try{service.cast(d.id(),actor,option);return true;}catch(IllegalStateException rejected){return false;}}));
            start.countDown();int accepted=0;for(var result:results)if(result.get(10,TimeUnit.SECONDS))accepted++;
            assertEquals(1,accepted);
            var restarted=new BallotService(new BallotStore(file),Clock.systemUTC());
            assertEquals(service.view(d.id(),actor).ownChoice(),restarted.view(d.id(),actor).ownChoice());
            assertTrue(restarted.view(d.id(),UUID.randomUUID()).totals().isEmpty());
        } finally {pool.shutdownNow();}
    }
}
