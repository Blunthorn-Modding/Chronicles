package io.github.chronicle.quietbelow.ecology;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure disk compaction used only behind a caller-owned runtime barrier and journal maintenance
 * lease. It never reads a live engine or Minecraft world and never performs terrain recovery.
 */
public final class EcologyAutomaticCompaction {
    private static final long MINIMUM_SAVING = 4L * 1024;

    public record Result(
            EcologyJournal journal,
            boolean applied,
            String reason,
            long beforeBytes,
            long afterBytes,
            long beforeRecords,
            long afterRecords,
            String sourceHash,
            String checkpointHash,
            Path archive) { }

    @FunctionalInterface
    interface Promotion {
        void replace(Path candidate, Path journal) throws IOException;
    }

    enum Stage {
        SNAPSHOT_READY,
        CHECKPOINT_WRITING,
        CHECKPOINT_WRITTEN,
        CANDIDATE_VERIFIED,
        ARCHIVED,
        SOURCE_CLOSED,
        PROMOTED
    }

    @FunctionalInterface
    interface StageHook {
        void reached(Stage stage, Path artifact) throws IOException;
    }

    private static final StageHook NOOP = (stage, artifact) -> { };

    private EcologyAutomaticCompaction() { }

    public static Result compact(
            EcologyJournal source,
            Path file,
            EcologyEngine.Setup setup,
            EcologyEngine.Area area,
            EcologyTerrain.Pos heart) throws IOException {
        return compact(source, file, setup, area, heart, EcologyAutomaticCompaction::promote, NOOP);
    }

    static Result compact(EcologyJournal source,Path file,EcologyEngine.Setup setup,
                          EcologyEngine.Area area,EcologyTerrain.Pos heart,StageHook hook) throws IOException {
        return compact(source,file,setup,area,heart,EcologyAutomaticCompaction::promote,hook);
    }

    static Result compact(
            EcologyJournal source,
            Path file,
            EcologyEngine.Setup setup,
            EcologyEngine.Area area,
            EcologyTerrain.Pos heart,
            Promotion promotion,
            StageHook hook) throws IOException {
        Objects.requireNonNull(source, "source journal");
        Objects.requireNonNull(file, "journal file");
        Objects.requireNonNull(setup, "ecology setup");
        Objects.requireNonNull(area, "ecology area");
        Objects.requireNonNull(heart, "ecology heart");
        Objects.requireNonNull(promotion, "promotion");
        Objects.requireNonNull(hook, "stage hook");

        Path journal = file.toAbsolutePath().normalize();
        requireSource(source, journal);
        Path parent = journal.getParent();
        Path snapshot = parent.resolve("ecology-" + UUID.randomUUID() + ".snapshot");
        Path candidate = parent.resolve("ecology-" + UUID.randomUUID() + ".prepared");

        long beforeBytes = source.bytes();
        long beforeRecords = source.revision();
        String sourceHash = source.fingerprint();

        source.archiveTo(snapshot);
        requireSourceIdentity(source, journal, beforeBytes, beforeRecords, sourceHash);
        verifyExactCopy(snapshot, sourceHash, beforeBytes, beforeRecords);
        hook.reached(Stage.SNAPSHOT_READY, snapshot);

        EcologyEngine snapshotState;
        long afterBytes;
        long afterRecords;
        String checkpointHash;
        try (EcologyJournal evidence = new EcologyJournal(snapshot)) {
            snapshotState = replay(evidence, setup, area, heart, "snapshot");
            try (EcologyJournal prepared = EcologyJournal.checkpointCandidate(candidate,Math.max(1,beforeBytes-MINIMUM_SAVING),EcologyJournal.MAX_ENTRIES)) {
                hook.reached(Stage.CHECKPOINT_WRITING,candidate);
                try {
                    snapshotState.writeCheckpoint(prepared, sourceHash);
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    throw invalid("Snapshot cannot produce a complete checkpoint", exception);
                }
                afterBytes = prepared.bytes();
                afterRecords = prepared.revision();
                checkpointHash = prepared.fingerprint();
            }
        } catch(EcologyJournal.OutputBudgetExceeded expected){
            requireSourceIdentity(source,journal,beforeBytes,beforeRecords,sourceHash);
            source.endMaintenance();
            Files.deleteIfExists(candidate);Files.deleteIfExists(snapshot);
            // The partial candidate was discarded; these are retained-source sizes and no checkpoint hash exists.
            return new Result(source,false,"Checkpoint cannot save at least 4 KiB within journal limits; original retained",
                    beforeBytes,beforeBytes,beforeRecords,beforeRecords,sourceHash,"",null);
        }

        hook.reached(Stage.CHECKPOINT_WRITTEN,candidate);

        try (EcologyJournal prepared = new EcologyJournal(candidate)) {
            EcologyEngine candidateState = replay(prepared, setup, area, heart, "prepared checkpoint");
            if (!snapshotState.sameCheckpointState(candidateState)) {
                throw new IOException("Prepared checkpoint differs from the sealed source; evidence retained: " + candidate);
            }
            if (!checkpointHash.equals(prepared.fingerprint())
                    || prepared.bytes() != afterBytes
                    || prepared.revision() != afterRecords) {
                throw new IOException("Prepared checkpoint changed after creation; evidence retained: " + candidate);
            }
        }
        hook.reached(Stage.CANDIDATE_VERIFIED, candidate);

        requireSourceIdentity(source, journal, beforeBytes, beforeRecords, sourceHash);
        if (beforeBytes - afterBytes < MINIMUM_SAVING) {
            Files.delete(candidate);
            Files.delete(snapshot);
            source.endMaintenance();
            return new Result(
                    source,
                    false,
                    "Less than 4 KiB saving; original retained",
                    beforeBytes,
                    afterBytes,
                    beforeRecords,
                    afterRecords,
                    sourceHash,
                    checkpointHash,
                    null);
        }

        Path archives = archiveDirectory(parent);
        Path archive = archives.resolve("ecology-" + sourceHash + "-" + UUID.randomUUID() + ".jsonl");
        Files.move(snapshot, archive, StandardCopyOption.ATOMIC_MOVE);
        verifyExactCopy(archive, sourceHash, beforeBytes, beforeRecords);
        hook.reached(Stage.ARCHIVED, archive);

        requireSourceIdentity(source, journal, beforeBytes, beforeRecords, sourceHash);
        source.close();
        hook.reached(Stage.SOURCE_CLOSED, journal);
        requireClosedSourceIdentity(journal, sourceHash, beforeBytes);

        promotion.replace(candidate, journal);
        hook.reached(Stage.PROMOTED, journal);

        EcologyJournal installed = null;
        try {
            installed = new EcologyJournal(journal);
            EcologyEngine installedState = replay(installed, setup, area, heart, "installed checkpoint");
            if (!checkpointHash.equals(installed.fingerprint())
                    || installed.bytes() != afterBytes
                    || installed.revision() != afterRecords
                    || !snapshotState.sameCheckpointState(installedState)) {
                throw new IOException("Installed checkpoint verification failed; inspect original archive: " + archive);
            }
            return new Result(
                    installed,
                    true,
                    "Verified checkpoint installed; original archived",
                    beforeBytes,
                    afterBytes,
                    beforeRecords,
                    afterRecords,
                    sourceHash,
                    checkpointHash,
                    archive);
        } catch (IOException | RuntimeException exception) {
            if (installed != null) {
                try {
                    installed.close();
                } catch (IOException closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
            }
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw invalid("Installed checkpoint is invalid; inspect original archive: " + archive, exception);
        }
    }

    private static EcologyEngine replay(
            EcologyJournal journal,
            EcologyEngine.Setup setup,
            EcologyEngine.Area area,
            EcologyTerrain.Pos heart,
            String label) throws IOException {
        try {
            EcologyEngine engine = new EcologyEngine(journal);
            engine.attach(setup, area, heart);
            if (!engine.view().indexed()) {
                throw new IOException("Automatic compaction requires a completed source index: " + label);
            }
            if (engine.ending() != null) {
                throw new IOException("Automatic compaction refuses a terminal ecology journal: " + label);
            }
            if (engine.failure() != null) {
                throw new IOException("Automatic compaction refuses failed ecology state: " + label);
            }
            return engine;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw invalid("Automatic compaction refused invalid " + label, exception);
        }
    }

    private static void requireSource(EcologyJournal source, Path journal) throws IOException {
        if (source.closed()) {
            throw new IOException("Automatic compaction requires an open source journal");
        }
        if (!source.maintenanceActive()) {
            throw new IOException("Automatic compaction requires an active journal maintenance lease");
        }
        if (!source.path().equals(journal)) {
            throw new IOException("Automatic compaction source handle does not match journal path");
        }
        requireOpenSourceFile(journal);
    }

    private static void requireSourceIdentity(
            EcologyJournal source,
            Path journal,
            long bytes,
            long records,
            String hash) throws IOException {
        requireSource(source, journal);
        if (source.bytes() != bytes || source.revision() != records || !source.fingerprint().equals(hash)) {
            throw new IOException("Sealed source journal changed during automatic compaction; evidence retained");
        }
    }

    private static void requireClosedSourceIdentity(Path journal, String hash, long bytes) throws IOException {
        requireRealFile(journal, "Source journal changed after its maintenance handle closed");
        if (Files.size(journal) != bytes || !hash(journal).equals(hash)) {
            throw new IOException("Source journal changed before atomic promotion; evidence retained");
        }
    }

    private static void verifyExactCopy(Path copy, String hash, long bytes, long records) throws IOException {
        requireRealFile(copy, "Automatic compaction evidence must be a real file, not a link");
        try (EcologyJournal evidence = new EcologyJournal(copy)) {
            if (evidence.bytes() != bytes
                    || evidence.revision() != records
                    || !evidence.fingerprint().equals(hash)) {
                throw new IOException("Automatic compaction evidence differs from the sealed source: " + copy);
            }
        } catch (IllegalArgumentException exception) {
            throw invalid("Automatic compaction evidence is invalid: " + copy, exception);
        }
    }

    private static Path archiveDirectory(Path parent) throws IOException {
        Path archives = parent.resolve("journal-archives");
        try {
            Files.createDirectory(archives);
        } catch (FileAlreadyExistsException ignored) {
            // Validate the existing object below without following it.
        }
        if (!Files.isDirectory(archives, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(archives)) {
            throw new IOException("Journal archive directory may not be a link: " + archives);
        }
        requireUnlinkedDirectories(archives, "Journal archive directory may not be a link");
        return archives;
    }

    private static void requireRealFile(Path file, String message) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(file)) {
            throw new IOException(message + ": " + file);
        }
        requireUnlinkedDirectories(file.getParent(), message);
    }

    /** Windows cannot resolve the real path of the NOSHARE_DELETE-pinned source file itself. */
    private static void requireOpenSourceFile(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent == null
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(file)) {
            throw new IOException("Automatic compaction journal must be a real file in a real directory, not a link: " + file);
        }
        requireUnlinkedDirectories(parent, "Automatic compaction journal directory may not be a link");
    }

    private static void requireUnlinkedDirectories(Path parent, String message) throws IOException {
        for (Path directory = parent; directory != null; directory = directory.getParent()) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(directory)) {
                throw new IOException(message + ": " + directory);
            }
        }
    }

    private static void promote(Path candidate, Path journal) throws IOException {
        try {
            Files.move(candidate, journal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Filesystem does not support atomic ecology journal promotion", exception);
        }
    }

    private static String hash(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException(exception);
        }
    }

    private static IOException invalid(String message, Throwable cause) {
        return new IOException(message, cause);
    }
}
