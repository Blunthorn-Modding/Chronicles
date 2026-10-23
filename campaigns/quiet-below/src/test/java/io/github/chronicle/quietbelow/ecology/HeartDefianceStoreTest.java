package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HeartDefianceStoreTest {
    @TempDir Path dir;
    private static final HeartDefianceStore.Pin PIN=new HeartDefianceStore.Pin("city",1,"heart",2,"minecraft:overworld",new EcologyTerrain.Pos(3,64,-7));

    @Test void defaultClaimIsDurableAndDeniesEveryCycleBeginningBeforeTenActiveMinutes()throws Exception{
        Path file=dir.resolve("heart-defiance.json");HeartDefianceStore store=new HeartDefianceStore(file);
        HeartDefianceStore.State empty=store.load();assertEquals(0,empty.revision());assertEquals(600,empty.cooldownSeconds());assertNull(empty.receipt());
        HeartDefianceStore.State claimed=store.claim(PIN,1_000,false);HeartDefianceStore.Receipt receipt=claimed.receipt();
        assertEquals(13_000,receipt.eligibleAfterActiveTicks());assertEquals(claimed,new HeartDefianceStore(file).load());
        assertFalse(HeartDefianceStore.cycleEligible(receipt,PIN,1_001),"an immediate reconnect/cut started inside cooldown");
        assertFalse(HeartDefianceStore.cycleEligible(receipt,PIN,12_999));
        assertTrue(HeartDefianceStore.cycleEligible(receipt,PIN,13_000));
        assertThrows(IllegalStateException.class,()->store.claim(PIN,12_999,false));
        HeartDefianceStore.State second=store.claim(PIN,13_000,false);assertEquals(2,second.revision());
        assertTrue(Files.exists(file.resolveSibling("heart-defiance.json.bak")));
        assertEquals(claimed,HeartDefianceStore.decode(Files.readString(file.resolveSibling("heart-defiance.json.bak"))));
    }

    @Test void aCycleThatStartsDuringCooldownNeverBecomesEligibleMidCycle(){
        HeartDefianceStore.Receipt receipt=new HeartDefianceStore.Receipt(1,PIN,1_000,13_000,false);
        long reconnectCutCycleStart=12_999;
        assertFalse(HeartDefianceStore.cycleEligible(receipt,PIN,reconnectCutCycleStart));
        assertFalse(HeartDefianceStore.cycleEligible(receipt,PIN,reconnectCutCycleStart),"later wall time cannot alter a saved cycle start");
        assertFalse(HeartDefianceStore.cycleEligible(receipt,new HeartDefianceStore.Pin("city",1,"heart",3,"minecraft:overworld",PIN.root()),13_001));
    }

    @Test void cooldownTuningIsStrictAndDoesNotRewriteAnExistingReceiptExpiry()throws Exception{
        HeartDefianceStore store=new HeartDefianceStore(dir.resolve("tuning.json"));
        HeartDefianceStore.State five=store.tuneCooldown(300);assertEquals(300,five.cooldownSeconds());assertNull(five.receipt());
        HeartDefianceStore.State claimed=store.claim(PIN,100,false);assertEquals(6_100,claimed.receipt().eligibleAfterActiveTicks());
        HeartDefianceStore.State later=store.tuneCooldown(1_800);assertEquals(1_800,later.cooldownSeconds());
        assertEquals(6_100,later.receipt().eligibleAfterActiveTicks(),"tuning applies to future claims, not a phase already claimed");
        assertThrows(IllegalArgumentException.class,()->store.tuneCooldown(42));
    }

    @Test void permanentReceiptAndOverflowFailClosed()throws Exception{
        HeartDefianceStore store=new HeartDefianceStore(dir.resolve("permanent.json"));
        HeartDefianceStore.State state=store.claim(PIN,7,true);assertTrue(state.receipt().permanent());
        assertEquals(Long.MAX_VALUE,state.receipt().eligibleAfterActiveTicks());assertFalse(HeartDefianceStore.cycleEligible(state.receipt(),PIN,Long.MAX_VALUE));
        assertThrows(IllegalStateException.class,()->store.claim(PIN,Long.MAX_VALUE,false));
        HeartDefianceStore overflow=new HeartDefianceStore(dir.resolve("overflow.json"));
        assertThrows(ArithmeticException.class,()->overflow.claim(PIN,Long.MAX_VALUE-1,false));
        assertFalse(Files.exists(dir.resolve("overflow.json")));
    }

    @Test void corruptFutureTrailingAndOversizedFilesArePreserved()throws Exception{
        for(String[] fixture:new String[][]{
                {"corrupt.json","{bad"},
                {"future.json","{\"schemaVersion\":2,\"revision\":0,\"cooldownSeconds\":600,\"receipt\":null}"},
                {"unknown.json","{\"schemaVersion\":1,\"revision\":0,\"cooldownSeconds\":600,\"receipt\":null,\"extra\":1}"},
                {"trailing.json","{\"schemaVersion\":1,\"revision\":0,\"cooldownSeconds\":600,\"receipt\":null} []"}}){
            Path file=dir.resolve(fixture[0]);Files.writeString(file,fixture[1],StandardCharsets.UTF_8);String original=Files.readString(file);
            HeartDefianceStore store=new HeartDefianceStore(file);assertThrows(IOException.class,store::load,fixture[0]);
            assertThrows(IOException.class,()->store.tuneCooldown(300),fixture[0]);assertEquals(original,Files.readString(file),fixture[0]);
        }
        Path oversized=dir.resolve("oversized.json");Files.writeString(oversized,"x".repeat((int)HeartDefianceStore.MAX_FILE_BYTES+1));
        assertThrows(IOException.class,()->new HeartDefianceStore(oversized).load());
    }

    @Test void uncertainCommitIsTreatedAsClaimedOnRestart()throws Exception{
        Path file=dir.resolve("uncertain.json");
        HeartDefianceStore uncertain=new HeartDefianceStore(file,(target,bytes)->{Files.write(target,bytes);throw new IOException("simulated acknowledgement loss");});
        assertThrows(IOException.class,()->uncertain.claim(PIN,2_000,false));
        HeartDefianceStore.State recovered=new HeartDefianceStore(file).load();
        assertNotNull(recovered.receipt(),"a move that committed before acknowledgement must remain authoritative");
        assertFalse(HeartDefianceStore.cycleEligible(recovered.receipt(),PIN,2_001));
        assertThrows(IllegalStateException.class,()->new HeartDefianceStore(file).claim(PIN,2_001,false));
    }

    @Test void encodingRoundTripRejectsNonCanonicalOrInconsistentReceipts(){
        HeartDefianceStore.State state=new HeartDefianceStore.State(4,600,new HeartDefianceStore.Receipt(4,PIN,100,12_100,false));
        assertEquals(state,HeartDefianceStore.decode(HeartDefianceStore.encode(state)));
        assertThrows(IllegalArgumentException.class,()->HeartDefianceStore.decode(HeartDefianceStore.encode(state).replace("\"revision\":4","\"revision\":04")));
        assertThrows(IllegalArgumentException.class,()->new HeartDefianceStore.State(3,600,state.receipt()));
    }
}
