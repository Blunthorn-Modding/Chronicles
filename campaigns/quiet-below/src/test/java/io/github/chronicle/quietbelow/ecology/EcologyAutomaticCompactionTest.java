package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.AIR;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.SCULK;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcologyAutomaticCompactionTest {
    @TempDir Path directory;

    private static final EcologyEngine.Setup SETUP = new EcologyEngine.Setup(
            "city", 1, "heart", 2, "minecraft:overworld");
    private static final EcologyEngine.Area AREA = new EcologyEngine.Area(
            new EcologyTerrain.Pos(-1, 0, 0), new EcologyTerrain.Pos(31, 0, 0));
    private static final EcologyTerrain.Pos HEART = new EcologyTerrain.Pos(-1, 0, 0);
    private static final EcologyTerrain.Pos SOURCE = new EcologyTerrain.Pos(0, 0, 0);

    @Test void candidateEntryAndByteBudgetsAreExpectedAndDoNotPoison() throws Exception {
        Path bounded=directory.resolve("bounded.jsonl");
        try(var output=new EcologyJournal(bounded,4096,1)){
            output.append("SPEED",speed(25));
            assertThrows(EcologyJournal.OutputBudgetExceeded.class,()->output.append("SPEED",speed(50)));
            assertFalse(output.fingerprint().isBlank());assertEquals(1,output.revision());
        }
        try(var ordinary=new EcologyJournal(bounded)){ordinary.append("SPEED",speed(50));assertEquals(2,ordinary.revision());}
        try(var output=new EcologyJournal(directory.resolve("tiny-budget.jsonl"),1,EcologyJournal.MAX_ENTRIES)){
            assertThrows(EcologyJournal.OutputBudgetExceeded.class,()->output.append("SPEED",speed(25)));
            assertEquals(0,output.bytes());assertFalse(output.closed());
        }
    }
    @Test void tinySourceCannotSaveFourKiBAndKeepsOriginalReadyForLaterRetry() throws Exception {
        Path file=directory.resolve("tiny-source/ecology.jsonl");
        var smallArea=new EcologyEngine.Area(HEART,SOURCE);
        try(var initial=new EcologyJournal(file)){
            var minimal=new EcologyEngine(initial);minimal.configure(SETUP,smallArea,HEART);
            while(!minimal.view().indexed())minimal.index(new World());
        }
        byte[] original=Files.readAllBytes(file);assertTrue(original.length<4096);
        var source=new EcologyJournal(file);source.beginMaintenance();
        var unchanged=EcologyAutomaticCompaction.compact(source,file,SETUP,smallArea,HEART);
        assertFalse(unchanged.applied());assertSame(source,unchanged.journal());assertEquals("",unchanged.checkpointHash());
        assertNull(unchanged.archive());assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(original)),source.fingerprint());
        assertEquals(original.length,unchanged.afterBytes());assertFalse(source.maintenanceActive());
        var engine=new EcologyEngine(source);engine.attach(SETUP,smallArea,HEART);
        for(int n=0;n<400;n++)engine.speed(n%2==0?25:400);
        source.beginMaintenance();var retried=EcologyAutomaticCompaction.compact(source,file,SETUP,smallArea,HEART);
        try(var retained=retried.journal()){assertTrue(retried.applied());assertFalse(retained.closed());}
        assertTrue(artifacts(file.getParent(),".prepared").isEmpty());assertTrue(artifacts(file.getParent(),".snapshot").isEmpty());
    }

    @Test
    void maintenanceLeaseRejectsAppendWithoutPoisoningAndKeepsEvidenceReadable() throws Exception {
        Path file = directory.resolve("lease/ecology.jsonl");
        try (EcologyJournal journal = new EcologyJournal(file)) {
            journal.beginMaintenance();
            assertTrue(journal.maintenanceActive());
            assertThrows(IOException.class, () -> journal.append("SPEED", speed(25)));
            assertDoesNotThrow(journal::fingerprint);
            journal.endMaintenance();
            assertFalse(journal.maintenanceActive());
            assertDoesNotThrow(() -> journal.append("SPEED", speed(25)));
            assertEquals(1, journal.revision());
        }
    }

    @Test
    void appliesExactArchivePreservesStateAndSupportsAppendThenRecompaction() throws Exception {
        Path file = fixture("roundtrip", 420);
        byte[] original = Files.readAllBytes(file);
        EcologyEngine originalState = load(file);

        EcologyJournal source = new EcologyJournal(file);
        source.beginMaintenance();
        EcologyAutomaticCompaction.Result first = EcologyAutomaticCompaction.compact(
                source, file, SETUP, AREA, HEART);
        assertTrue(first.applied());
        assertTrue(source.closed());
        assertNotSame(source, first.journal());
        assertArrayEquals(original, Files.readAllBytes(first.archive()));
        assertTrue(first.afterBytes() < first.beforeBytes() - 4096);
        EcologyEngine installed = attached(first.journal());
        assertTrue(originalState.sameCheckpointState(installed));

        for (int turn = 0; turn < 160; turn++) {
            installed.speed(turn % 2 == 0 ? 50 : 800);
        }
        first.journal().close();
        EcologyEngine appendedState = load(file);

        EcologyJournal appended = new EcologyJournal(file);
        appended.beginMaintenance();
        EcologyAutomaticCompaction.Result second = EcologyAutomaticCompaction.compact(
                appended, file, SETUP, AREA, HEART);
        assertTrue(second.applied());
        assertTrue(appendedState.sameCheckpointState(attached(second.journal())));
        second.journal().close();
        assertTrue(appendedState.sameCheckpointState(load(file)));
    }

    @Test
    void noSavingsDoesNotPromoteAndReturnsOriginalUnsealedJournal() throws Exception {
        Path file = fixture("no-saving", 420);
        EcologyJournal source = new EcologyJournal(file);
        source.beginMaintenance();
        EcologyAutomaticCompaction.Result first = EcologyAutomaticCompaction.compact(
                source, file, SETUP, AREA, HEART);
        assertTrue(first.applied());
        first.journal().close();
        byte[] compacted = Files.readAllBytes(file);

        EcologyJournal compact = new EcologyJournal(file);
        compact.beginMaintenance();
        EcologyAutomaticCompaction.Result result = EcologyAutomaticCompaction.compact(
                compact,
                file,
                SETUP,
                AREA,
                HEART,
                (candidate, journal) -> {
                    throw new AssertionError("No-saving compaction must not promote");
                },
                (stage, artifact) -> { });
        assertFalse(result.applied());
        assertSame(compact, result.journal());
        assertFalse(compact.maintenanceActive());
        assertEquals(compacted.length, compact.bytes());
        assertEquals(first.checkpointHash(), compact.fingerprint());
        assertTrue(artifacts(file.getParent(), ".snapshot").isEmpty());
        assertTrue(artifacts(file.getParent(), ".prepared").isEmpty());

        EcologyEngine engine = attached(compact);
        engine.speed(engine.speedPercent() == 25 ? 50 : 25);
        compact.close();
        assertDoesNotThrow(() -> load(file));
    }

    @Test
    void requiresLeaseAndRefusesExternalSourceMutationWithoutPromotion() throws Exception {
        Path file = fixture("mutation", 420);
        byte[] original = Files.readAllBytes(file);
        try (EcologyJournal unsealed = new EcologyJournal(file)) {
            assertThrows(IOException.class, () -> EcologyAutomaticCompaction.compact(
                    unsealed, file, SETUP, AREA, HEART));
        }

        EcologyJournal source = new EcologyJournal(file);
        source.beginMaintenance();
        assertThrows(IOException.class, () -> EcologyAutomaticCompaction.compact(
                source,
                file,
                SETUP,
                AREA,
                HEART,
                (candidate, journal) -> {
                    throw new AssertionError("Mutated source must not promote");
                },
                (stage, artifact) -> {
                    if (stage == EcologyAutomaticCompaction.Stage.SOURCE_CLOSED) {
                        Files.writeString(file, "external", StandardOpenOption.APPEND);
                    }
                }));
        assertTrue(source.closed());
        assertEquals(1, artifacts(file.getParent(), ".prepared").size());
        assertArrayEquals(original, Files.readAllBytes(onlyArchive(file)));
    }

    @Test
    void corruptSnapshotIsRejectedAndRetainedWithOriginalSealed() throws Exception {
        Path file = fixture("corrupt-snapshot", 420);
        EcologyJournal source = new EcologyJournal(file);
        source.beginMaintenance();
        String sourceHash = source.fingerprint();
        assertThrows(IOException.class, () -> EcologyAutomaticCompaction.compact(
                source,
                file,
                SETUP,
                AREA,
                HEART,
                (candidate, journal) -> {
                    throw new AssertionError("Corrupt snapshot must not promote");
                },
                (stage, artifact) -> {
                    if (stage == EcologyAutomaticCompaction.Stage.SNAPSHOT_READY) {
                        Files.writeString(artifact, "broken", StandardOpenOption.APPEND);
                    }
                }));
        assertEquals(sourceHash, source.fingerprint());
        assertTrue(source.maintenanceActive());
        assertFalse(artifacts(file.getParent(), ".snapshot").isEmpty());
        source.close();
    }

    @Test
    void incompleteSourceIndexIsRefusedWithoutChangingOrUnsealingSource() throws Exception {
        Path file = directory.resolve("incomplete/ecology.jsonl");
        try (EcologyJournal journal = new EcologyJournal(file)) {
            EcologyEngine engine = new EcologyEngine(journal);
            engine.configure(SETUP, AREA, HEART);
        }
        EcologyJournal source = new EcologyJournal(file);
        source.beginMaintenance();
        String sourceHash = source.fingerprint();
        assertThrows(IOException.class, () -> EcologyAutomaticCompaction.compact(
                source, file, SETUP, AREA, HEART));
        assertTrue(source.maintenanceActive());
        assertEquals(sourceHash, source.fingerprint());
        assertFalse(artifacts(file.getParent(), ".snapshot").isEmpty());
        source.close();
    }

    @Test
    void atomicMoveFailureKeepsOriginalArchiveAndPreparedCheckpoint() throws Exception {
        Path file = fixture("move-failure", 420);
        byte[] original = Files.readAllBytes(file);
        EcologyJournal source = new EcologyJournal(file);
        source.beginMaintenance();
        assertThrows(IOException.class, () -> EcologyAutomaticCompaction.compact(
                source,
                file,
                SETUP,
                AREA,
                HEART,
                (candidate, journal) -> {
                    throw new AtomicMoveNotSupportedException(
                            candidate.toString(), journal.toString(), "injected");
                },
                (stage, artifact) -> { }));
        assertTrue(source.closed());
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(1, artifacts(file.getParent(), ".prepared").size());
        Path archive = onlyArchive(file);
        assertArrayEquals(original, Files.readAllBytes(archive));
        assertDoesNotThrow(() -> load(file));
    }

    @Test
    void afterMoveUncertaintyStillLeavesValidCheckpointAndExactArchive() throws Exception {
        Path file = fixture("after-move", 420);
        byte[] original = Files.readAllBytes(file);
        EcologyEngine expected = load(file);
        EcologyJournal source = new EcologyJournal(file);
        source.beginMaintenance();
        assertThrows(IOException.class, () -> EcologyAutomaticCompaction.compact(
                source,
                file,
                SETUP,
                AREA,
                HEART,
                (candidate, journal) -> Files.move(
                        candidate,
                        journal,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING),
                (stage, artifact) -> {
                    if (stage == EcologyAutomaticCompaction.Stage.PROMOTED) {
                        throw new IOException("injected after atomic move");
                    }
                }));
        assertTrue(source.closed());
        assertArrayEquals(original, Files.readAllBytes(onlyArchive(file)));
        assertTrue(expected.sameCheckpointState(load(file)));
    }

    @Test
    void linkedArchiveDirectoryIsRefusedBeforeSourceClose() throws Exception {
        Path file = fixture("linked-archive", 420);
        Path target = directory.resolve("archive-target");
        Files.createDirectories(target);
        try {
            Files.createSymbolicLink(file.getParent().resolve("journal-archives"), target);
        } catch (IOException | UnsupportedOperationException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable in this test environment: " + exception);
            return;
        }
        EcologyJournal source = new EcologyJournal(file);
        source.beginMaintenance();
        String sourceHash = source.fingerprint();
        assertThrows(IOException.class, () -> EcologyAutomaticCompaction.compact(
                source, file, SETUP, AREA, HEART));
        assertFalse(source.closed());
        assertTrue(source.maintenanceActive());
        assertEquals(sourceHash, source.fingerprint());
        source.close();
    }

    private Path fixture(String name, int history) throws Exception {
        Path file = directory.resolve(name).resolve("ecology.jsonl");
        try (EcologyJournal journal = new EcologyJournal(file)) {
            EcologyEngine engine = new EcologyEngine(journal);
            engine.configure(SETUP, AREA, HEART);
            World world = new World();
            while (!engine.view().indexed()) {
                engine.index(world);
            }
            engine.mode(true);
            for (int turn = 0; turn < 160; turn++) {
                engine.beginTick();
                engine.tick(world, true);
            }
            engine.flushVitality();
            for (int turn = 0; turn < history; turn++) {
                engine.speed(turn % 2 == 0 ? 25 : 1600);
            }
        }
        return file;
    }

    private EcologyEngine load(Path file) throws Exception {
        try (EcologyJournal journal = new EcologyJournal(file)) {
            return attached(journal);
        }
    }

    private EcologyEngine attached(EcologyJournal journal) {
        EcologyEngine engine = new EcologyEngine(journal);
        engine.attach(SETUP, AREA, HEART);
        return engine;
    }

    private List<Path> artifacts(Path parent, String suffix) throws IOException {
        try (var paths = Files.list(parent)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(suffix)).toList();
        }
    }

    private Path onlyArchive(Path file) throws IOException {
        try (var paths = Files.list(file.getParent().resolve("journal-archives"))) {
            List<Path> archives = paths.toList();
            assertEquals(1, archives.size());
            return archives.getFirst();
        }
    }

    private static JsonObject speed(int percent) {
        JsonObject payload = new JsonObject();
        payload.addProperty("percent", percent);
        return payload;
    }

    private static final class World implements EcologyTerrain {
        private final Map<EcologyTerrain.Pos, EcologyTerrain.State> blocks =
                new HashMap<>(Map.of(SOURCE, SCULK));

        @Override public boolean loaded(EcologyTerrain.Pos position) { return true; }
        @Override public EcologyTerrain.State get(EcologyTerrain.Pos position) {
            return blocks.getOrDefault(
                    position,
                    position.y() == 0
                            ? new EcologyTerrain.State("minecraft:stone", Map.of())
                            : AIR);
        }
        @Override public boolean solidFace(EcologyTerrain.Pos position, int face) {
            return !get(position).air() && !get(position).vein();
        }
        @Override public boolean substrate(EcologyTerrain.Pos position) {
            return get(position).id().equals("minecraft:stone");
        }
        @Override public int light(EcologyTerrain.Pos position) { return 15; }
        @Override public boolean set(EcologyTerrain.Pos position, EcologyTerrain.State state) {
            blocks.put(position, state);
            return true;
        }
    }
}
