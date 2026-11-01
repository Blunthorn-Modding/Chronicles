package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EcologyCheckpointCandidateTest {
    @TempDir Path directory;
    @Test void candidateForcesOnceAtCloseAndReplaysExactly() throws Exception {
        Path file=directory.resolve("batch.prepared");var forces=new AtomicInteger();
        try(var output=EcologyJournal.checkpointCandidate(file,EcologyJournal.MAX_FILE_BYTES,EcologyJournal.MAX_ENTRIES,
                channel->{forces.incrementAndGet();channel.force(true);})){
            for(int i=0;i<300;i++){var payload=new JsonObject();payload.addProperty("value",i);output.append("SPEED",payload);}
            assertEquals(0,forces.get());assertEquals(300,output.revision());
            assertThrows(IOException.class,output::beginMaintenance);
            output.close();assertEquals(1,forces.get());
        }
        assertEquals(1,forces.get());
        try(var reopened=new EcologyJournal(file)){assertEquals(300,reopened.revision());assertEquals(299,reopened.entries().getLast().payload().get("value").getAsInt());}
    }
    @Test void candidateRefusesExistingOrNonPreparedFiles() throws Exception {
        Path file=directory.resolve("existing.prepared");Files.writeString(file,"preserve");
        assertThrows(java.nio.file.FileAlreadyExistsException.class,()->EcologyJournal.checkpointCandidate(file,4096,10));
        assertEquals("preserve",Files.readString(file));
        assertThrows(IllegalArgumentException.class,()->EcologyJournal.checkpointCandidate(directory.resolve("ecology.jsonl"),4096,10));
    }
    @Test void closeForceFailureIsPropagatedAndResourcesAreReleased() throws Exception {
        Path file=directory.resolve("failed.prepared");
        var output=EcologyJournal.checkpointCandidate(file,4096,10,channel->{throw new IOException("injected force failure");});
        var payload=new JsonObject();payload.addProperty("value",1);output.append("SPEED",payload);
        assertTrue(assertThrows(IOException.class,output::close).getMessage().contains("force failure"));
        assertTrue(output.closed());
        // Closing the failed unpublished candidate releases its handle, without blessing it for promotion.
        Files.delete(file);assertFalse(Files.exists(file));
    }
}
