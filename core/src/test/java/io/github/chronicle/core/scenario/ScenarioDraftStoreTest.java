package io.github.chronicle.core.scenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioDraftStoreTest {
    @TempDir Path directory;
    private static ScenarioContent content(String id) { return new ScenarioContent(id, java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()); }
    private static ScenarioContent changedContent() {
        EventTemplate event = new EventTemplate("example:event", "Event", "", "A cue.", "", io.github.chronicle.core.play.ObservationCatalog.Audience.PARTICIPANTS, "", io.github.chronicle.core.play.PlayState.Mode.MANUAL, "", 0, java.util.List.of(), java.util.Map.of(), java.util.List.of(), io.github.chronicle.core.play.EventSource.empty());
        return new ScenarioContent("example:quiet", java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(event.id(), event));
    }
    @Test void savesWithExternalEditProtectionAndDurableBackup() throws Exception {
        ScenarioDraftStore store = new ScenarioDraftStore(directory, "example:quiet"); ScenarioContent first = content("example:quiet"); store.save(first); Path draft = store.draftFile(); byte[] old = Files.readAllBytes(draft);
        ScenarioContent changed = changedContent(); store.save(changed);
        assertArrayEquals(old, Files.readAllBytes(draft.resolveSibling("draft.json.bak"))); Files.writeString(draft, "external"); assertThrows(IOException.class, () -> store.save(first)); assertEquals("external", Files.readString(draft));
    }
    @Test void exportsOnlyControlledImmutableHashPath() throws Exception {
        ScenarioDraftStore store = new ScenarioDraftStore(directory, "example:quiet"); Path exported = store.export(content("example:quiet")); assertTrue(exported.startsWith(directory.toAbsolutePath())); assertEquals(exported, store.export(content("example:quiet"))); assertThrows(IllegalArgumentException.class, () -> new ScenarioDraftStore(directory, "../escape"));
    }
    @Test void refusesUnsafeBackupWithoutReplacingTheOldDraft() throws Exception {
        ScenarioDraftStore store = new ScenarioDraftStore(directory, "example:quiet"); ScenarioContent first = content("example:quiet"); store.save(first); Path draft = store.draftFile(); String before = Files.readString(draft);
        Path backup = draft.resolveSibling("draft.json.bak"); Files.createDirectory(backup); Files.writeString(backup.resolve("keep"), "do not replace");
        assertThrows(IOException.class, () -> store.save(changedContent()));
        assertEquals(before, Files.readString(draft)); assertEquals("do not replace", Files.readString(backup.resolve("keep")));
    }
    @Test void rejectsMalformedUtf8AndFreshStoresCannotOverwriteAnExistingDraft() throws Exception {
        ScenarioDraftStore first = new ScenarioDraftStore(directory, "example:quiet"); first.save(content("example:quiet")); Path draft = first.draftFile(); String before = Files.readString(draft);
        assertThrows(IOException.class, () -> new ScenarioDraftStore(directory, "example:quiet").save(content("example:quiet"))); assertEquals(before, Files.readString(draft));
        Files.write(draft, new byte[] {(byte) 0xc3, (byte) 0x28}); assertThrows(IOException.class, () -> new ScenarioDraftStore(directory, "example:quiet").load());
    }
    @Test void loadRejectsSymlinkedAncestorWhenSupported() throws Exception {
        ScenarioDraftStore store = new ScenarioDraftStore(directory, "example:quiet"); Path desired = store.draftFile(); Path target = directory.resolve("actual"); Path targetDraft = target.resolve(desired.getParent().getFileName()).resolve("draft.json"); Files.createDirectories(targetDraft.getParent()); Files.writeString(targetDraft, ScenarioContentJson.encode(content("example:quiet")));
        try { Files.createSymbolicLink(directory.resolve("scenario-content"), target); } catch (UnsupportedOperationException | IOException exception) { Assumptions.abort("symbolic links are unavailable on this Windows test account"); }
        assertThrows(IOException.class, store::load);
    }
    @Test void rejectsSymlinkedControlledPathWhenSupported() throws Exception {
        Path target = directory.resolve("target"); Files.createDirectories(target); Path link = directory.resolve("scenario-content"); try { Files.createSymbolicLink(link, target); } catch (UnsupportedOperationException | IOException exception) { Assumptions.abort("symbolic links are unavailable on this Windows test account"); }
        assertThrows(IOException.class, () -> new ScenarioDraftStore(directory, "example:quiet").save(content("example:quiet")));
    }
}
