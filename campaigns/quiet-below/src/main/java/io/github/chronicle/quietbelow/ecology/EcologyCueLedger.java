package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.history.ImmutableHistoryArchive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Small create-only cue authority. At most 26 growth receipts and two station receipts per world. */
public final class EcologyCueLedger {
    public static final int STEP = 5_000;
    private final ImmutableHistoryArchive archive;
    private final Path marker;
    private Integer baseline;
    private int steps;
    private final Set<String> stations = new HashSet<>();
    public EcologyCueLedger(Path directory) { archive = new ImmutableHistoryArchive(directory, "quiet-below-cue", 128); marker = directory.resolveSibling("ecology-cues.initialized"); }
    public void load() throws IOException {
        baseline = null; steps = 0; stations.clear();
        var initial = archive.read("baseline");
        if (initial.isEmpty()) {
            if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Initialized ecology cue history is missing; restore its exact archive");
            return;
        }
        try {
            baseline = Integer.parseInt(new String(initial.get(), StandardCharsets.US_ASCII));
            if (baseline < 0 || baseline > EcologyEngine.MAX_CELLS) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw new IOException("Ecology cue baseline is invalid", invalid); }
        boolean gap = false;
        for (int step = 1; step <= EcologyEngine.MAX_CELLS / STEP; step++) {
            var value = archive.read("growth:" + step);
            if (value.isEmpty()) gap = true;
            else {
                if (gap || !Arrays.equals(value.get(), bytes(baseline + step * STEP)) || baseline + step * STEP > EcologyEngine.MAX_CELLS)
                    throw new IOException("Ecology growth cue history has a gap or invalid threshold");
                steps = step;
            }
        }
        for (String type : List.of("quiet_below:ancient_altar", "quiet_below:ancient_forge")) {
            var value = archive.read("station:" + type);
            if (value.isPresent()) {
                if (!Arrays.equals(value.get(), type.getBytes(StandardCharsets.UTF_8))) throw new IOException("Station cue identity changed");
                stations.add(type);
            }
        }
        markInitialized();
    }
    public boolean initialized() { return baseline != null; }
    public void initialize(int count) throws IOException {
        if (baseline != null || count < 0 || count > EcologyEngine.MAX_CELLS) throw new IllegalArgumentException("Invalid ecology cue baseline");
        archive.publish("baseline", bytes(count)); baseline = count; markInitialized();
    }
    public int nextThreshold() { if (baseline == null) throw new IllegalStateException("Cue baseline is not initialized"); return baseline + (steps + 1) * STEP; }
    public String nextGrowthKey() { return "growth:" + (steps + 1); }
    public void publishGrowth() throws IOException { archive.publish(nextGrowthKey(), bytes(nextThreshold())); steps++; }
    public boolean stationSeen(String type) { return stations.contains(type); }
    public void publishStation(String type) throws IOException {
        if (!List.of("quiet_below:ancient_altar", "quiet_below:ancient_forge").contains(type)) throw new IllegalArgumentException("Unknown station cue");
        archive.publish("station:" + type, type.getBytes(StandardCharsets.UTF_8)); stations.add(type);
    }
    public boolean proves(String key) throws IOException {
        if (key.startsWith("growth:")) {
            int step;
            try { step = Integer.parseInt(key.substring(7)); } catch (NumberFormatException invalid) { throw new IOException("Invalid growth cue authority", invalid); }
            return baseline != null && step >= 1 && step <= steps
                    && archive.read(key).filter(value -> Arrays.equals(value, bytes(baseline + step * STEP))).isPresent();
        }
        if (key.startsWith("station:")) return stations.contains(key.substring(8))
                && archive.read(key).filter(value -> Arrays.equals(value, key.substring(8).getBytes(StandardCharsets.UTF_8))).isPresent();
        throw new IOException("Unknown ecology cue authority");
    }
    private void markInitialized() throws IOException {
        for (Path path = marker.toAbsolutePath(); path != null; path = path.getParent()) if (Files.isSymbolicLink(path)) throw new IOException("Ecology cue marker path cannot contain symbolic links");
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.size(marker) != 1 || Files.readAllBytes(marker)[0] != '1') throw new IOException("Invalid ecology cue initialization marker");
        } else {
            Path temporary = Files.createTempFile(marker.getParent(), ".cue-init-", ".tmp");
            try {
                try (var channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.write(java.nio.ByteBuffer.wrap(new byte[]{'1'})); channel.force(true); }
                Files.createLink(marker, temporary);
            } finally { Files.deleteIfExists(temporary); }
        }
    }
    private static byte[] bytes(int value) { return Integer.toString(value).getBytes(StandardCharsets.US_ASCII); }
}
