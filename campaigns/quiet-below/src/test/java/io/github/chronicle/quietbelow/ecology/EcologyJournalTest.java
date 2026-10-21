package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcologyJournalTest {
    @TempDir Path temporaryDirectory;

    @Test
    void createsAppendsForcesPublishesAndReplaysInOrder() throws IOException {
        Path file = temporaryDirectory.resolve("ecology.jsonl");
        JsonObject firstPayload = payload("mode", "HELD");
        JsonObject secondPayload = payload("position", "1,64,2");

        try (EcologyJournal journal = new EcologyJournal(file)) {
            assertTrue(Files.isRegularFile(file));
            assertEquals(0, Files.size(file));
            assertEquals(0, journal.revision());
            assertEquals(0, journal.bytes());

            EcologyJournal.Entry first = journal.append("MODE", firstPayload);
            long firstLength = Files.size(file);
            assertEquals(1, first.sequence());
            assertEquals("MODE", first.type());
            assertEquals(firstLength, journal.bytes());
            assertEquals(firstLength, Files.size(file));

            EcologyJournal.Entry second = journal.append("GROW", secondPayload);
            assertEquals(2, second.sequence());
            assertEquals(2, journal.revision());
            assertEquals(Files.size(file), journal.bytes());
        }

        try (EcologyJournal reopened = new EcologyJournal(file)) {
            assertEquals(2, reopened.revision());
            assertEquals(List.of("MODE", "GROW"), reopened.entries().stream().map(EcologyJournal.Entry::type).toList());
            assertEquals("HELD", reopened.entries().get(0).payload().get("mode").getAsString());
            assertEquals(Files.size(file), reopened.bytes());
        }
    }

    @Test
    void acceptsEveryFiniteEventType() throws IOException {
        Path file = temporaryDirectory.resolve("types.jsonl");
        List<String> types = List.of("CONFIGURE", "INDEX", "INDEX_COMPLETE", "MODE", "GROW", "REMOVE", "OBSERVE", "CONNECT",
                "CLAIM_START", "CLAIM_SCAN", "CLAIM_DONE", "CLEANUP_MODE", "PURGE", "SPEED", "FRONTIER", "VITALITY", "VITALITY_RULES", "CROP_RULES",
                "CHECKPOINT_BEGIN", "CHECKPOINT_CELLS", "CHECKPOINT_AIR", "CHECKPOINT_END", "RECOVERY_RULES");
        try (EcologyJournal journal = new EcologyJournal(file)) {
            for (String type : types) journal.append(type, payload("type", type));
            assertEquals(types, journal.entries().stream().map(EcologyJournal.Entry::type).toList());
            assertThrows(IllegalArgumentException.class, () -> journal.append("OTHER", new JsonObject()));
            assertEquals(types.size(), journal.revision());
        }
    }

    @Test
    void entryAndCollectionSnapshotsAreDefensivelyImmutable() throws IOException {
        Path file = temporaryDirectory.resolve("immutable.jsonl");
        JsonObject nested = payload("value", "original");
        JsonObject supplied = new JsonObject();
        supplied.add("nested", nested);

        try (EcologyJournal journal = new EcologyJournal(file)) {
            EcologyJournal.Entry appended = journal.append("OBSERVE", supplied);
            supplied.getAsJsonObject("nested").addProperty("value", "caller-change");
            JsonObject returned = appended.payload();
            returned.getAsJsonObject("nested").addProperty("value", "accessor-change");

            assertEquals("original", appended.payload().getAsJsonObject("nested").get("value").getAsString());
            assertEquals("original", journal.entries().get(0).payload()
                    .getAsJsonObject("nested").get("value").getAsString());
            assertThrows(UnsupportedOperationException.class,
                    () -> journal.entries().add(new EcologyJournal.Entry(2, "MODE", new JsonObject())));
        }

        try (EcologyJournal reopened = new EcologyJournal(file)) {
            assertEquals("original", reopened.entries().get(0).payload()
                    .getAsJsonObject("nested").get("value").getAsString());
        }
    }

    @Test
    void rejectsOrderTamperTruncationDuplicateFutureUnknownAndNoncanonicalEvidenceWithoutChangingBytes()
            throws IOException {
        Path file = temporaryDirectory.resolve("invalid.jsonl");
        byte[] valid;
        try (EcologyJournal journal = new EcologyJournal(file)) {
            journal.append("MODE", payload("value", "first"));
            journal.append("OBSERVE", payload("value", "second"));
        }
        valid = Files.readAllBytes(file);
        String source = new String(valid, StandardCharsets.UTF_8);
        List<String> lines = source.lines().toList();
        String first = lines.get(0);
        String second = lines.get(1);

        List<byte[]> invalid = List.of(
                (second + "\n" + first + "\n").getBytes(StandardCharsets.UTF_8),
                source.replace("\"first\"", "\"altered\"").getBytes(StandardCharsets.UTF_8),
                source.substring(0, source.length() - 1).getBytes(StandardCharsets.UTF_8),
                (first.substring(0, first.length() - 1) + ",\"schema\":1}\n" + second + "\n")
                        .getBytes(StandardCharsets.UTF_8),
                (first.replace("\"schema\":1", "\"schema\":2") + "\n" + second + "\n")
                        .getBytes(StandardCharsets.UTF_8),
                (first.replace("\"type\":\"MODE\"", "\"type\":\"OTHER\"") + "\n" + second + "\n")
                        .getBytes(StandardCharsets.UTF_8),
                (first.substring(0, first.length() - 1) + ",\"unknown\":true}\n" + second + "\n")
                        .getBytes(StandardCharsets.UTF_8),
                (first + " trailing\n" + second + "\n").getBytes(StandardCharsets.UTF_8),
                (" " + source).getBytes(StandardCharsets.UTF_8));

        for (byte[] candidate : invalid) {
            Files.write(file, candidate, StandardOpenOption.TRUNCATE_EXISTING);
            byte[] before = Files.readAllBytes(file);
            assertThrows(IOException.class, () -> new EcologyJournal(file));
            assertArrayEquals(before, Files.readAllBytes(file));
        }
    }

    @Test
    void rejectsMalformedUtf8AndBlankLinesWithoutChangingEvidence() throws IOException {
        Path file = temporaryDirectory.resolve("malformed.jsonl");
        List<byte[]> invalid = List.of(
                new byte[]{(byte) 0xc3, (byte) 0x28, (byte) '\n'},
                "\n".getBytes(StandardCharsets.UTF_8));
        for (byte[] candidate : invalid) {
            Files.write(file, candidate);
            assertThrows(IOException.class, () -> new EcologyJournal(file));
            assertArrayEquals(candidate, Files.readAllBytes(file));
        }
    }

    @Test
    void outsideMetadataChangePoisonsWriterBeforePublishingAnotherEntry() throws IOException {
        Path file = temporaryDirectory.resolve("outside.jsonl");
        try (EcologyJournal journal = new EcologyJournal(file)) {
            journal.append("MODE", payload("value", "one"));
            long publishedBytes = journal.bytes();
            FileTime original = Files.getLastModifiedTime(file);
            FileTime changed = FileTime.fromMillis(original.toMillis() + 10_000);
            Files.setLastModifiedTime(file, changed);

            assertThrows(IOException.class, () -> journal.append("MODE", payload("value", "two")));
            assertEquals(1, journal.revision());
            assertEquals(publishedBytes, journal.bytes());
            assertEquals(publishedBytes, Files.size(file));

            Files.setLastModifiedTime(file, original);
            IOException poisoned = assertThrows(IOException.class,
                    () -> journal.append("MODE", payload("value", "three")));
            assertTrue(poisoned.getMessage().contains("poisoned"));
            assertEquals(1, journal.revision());
        }
    }

    @Test
    void exclusiveProcessLockPreventsConcurrentWriter() throws IOException {
        Path file = temporaryDirectory.resolve("locked.jsonl");
        try (EcologyJournal ignored = new EcologyJournal(file)) {
            assertThrows(IOException.class, () -> new EcologyJournal(file));
        }
        try (EcologyJournal reopened = new EcologyJournal(file)) {
            assertEquals(0, reopened.revision());
        }
    }

    @Test
    void windowsNullFileKeyUsesADeleteShareProtectedHandle() throws IOException {
        Path file = temporaryDirectory.resolve("windows-identity.jsonl");
        Path replacement = temporaryDirectory.resolve("replacement.jsonl");
        try (EcologyJournal journal = new EcologyJournal(file)) {
            BasicFileAttributes attributes = Files.readAttributes(
                    file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.fileKey() == null) {
                assertTrue(EcologyJournal.windowsDeleteShareProtectionAvailable());
                Files.writeString(replacement, "replacement", StandardCharsets.UTF_8);
                assertThrows(IOException.class,
                        () -> Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING));
                journal.append("MODE", payload("value", "still-open"));
                assertEquals(1, journal.revision());
            }
        }
    }

    @Test
    void enforcesFileLineTextDepthAndDeclaredEntryCapsBeforePublication() throws IOException {
        assertEquals(64L * 1024 * 1024, EcologyJournal.MAX_FILE_BYTES);
        assertEquals(64 * 1024, EcologyJournal.MAX_LINE_BYTES);
        assertEquals(150_000, EcologyJournal.MAX_ENTRIES);

        Path file = temporaryDirectory.resolve("caps.jsonl");
        try (EcologyJournal journal = new EcologyJournal(file)) {
            JsonArray wide = new JsonArray();
            for (int index = 0; index < 4; index++) wide.add("x".repeat(EcologyJournal.MAX_STRING_CODEPOINTS));
            JsonObject oversizedLine = new JsonObject();
            oversizedLine.add("values", wide);
            assertThrows(IllegalArgumentException.class, () -> journal.append("CONFIGURE", oversizedLine));

            JsonObject oversizedText = payload("value", "x".repeat(EcologyJournal.MAX_STRING_CODEPOINTS + 1));
            assertThrows(IllegalArgumentException.class, () -> journal.append("CONFIGURE", oversizedText));

            JsonObject oversizedKey = new JsonObject();
            oversizedKey.addProperty("k".repeat(EcologyJournal.MAX_KEY_CODEPOINTS + 1), true);
            assertThrows(IllegalArgumentException.class, () -> journal.append("CONFIGURE", oversizedKey));

            JsonObject tooDeep = new JsonObject();
            JsonObject cursor = tooDeep;
            for (int index = 0; index <= EcologyJournal.MAX_JSON_DEPTH; index++) {
                JsonObject child = new JsonObject();
                cursor.add("child", child);
                cursor = child;
            }
            assertThrows(IllegalArgumentException.class, () -> journal.append("CONFIGURE", tooDeep));
            assertEquals(0, journal.revision());
            assertEquals(0, journal.bytes());
            assertEquals(0, Files.size(file));
        }

        Path huge = temporaryDirectory.resolve("huge.jsonl");
        try (FileChannel channel = FileChannel.open(huge, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.position(EcologyJournal.MAX_FILE_BYTES);
            channel.write(ByteBuffer.wrap(new byte[]{1}));
        }
        long exactLength = Files.size(huge);
        assertThrows(IOException.class, () -> new EcologyJournal(huge));
        assertEquals(exactLength, Files.size(huge));
    }

    @Test
    void failedValidationAndCloseNeverPublishAnEntry() throws IOException {
        Path file = temporaryDirectory.resolve("publication.jsonl");
        EcologyJournal journal = new EcologyJournal(file);
        assertThrows(IllegalArgumentException.class, () -> journal.append(null, new JsonObject()));
        assertThrows(IllegalArgumentException.class, () -> journal.append("MODE", null));
        assertEquals(0, journal.revision());
        assertEquals(0, journal.bytes());
        journal.close();
        journal.close();
        assertThrows(IOException.class, () -> journal.append("MODE", new JsonObject()));
        assertEquals(0, journal.revision());
        assertEquals(0, Files.size(file));
    }

    @Test
    void persistedLinesAreCanonicalLfTerminatedAndHashChained() throws IOException {
        Path file = temporaryDirectory.resolve("canonical.jsonl");
        JsonObject unordered = new JsonObject();
        unordered.addProperty("z", 2.00);
        unordered.addProperty("a", 1);
        try (EcologyJournal journal = new EcologyJournal(file)) {
            journal.append("CONFIGURE", unordered);
            journal.append("INDEX_COMPLETE", new JsonObject());
        }

        String source = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(source.endsWith("\n"));
        assertFalse(source.contains("\r"));
        assertFalse(source.contains(" "));
        assertTrue(source.contains("\"payload\":{\"a\":1,\"z\":2}"));
        assertEquals(2, source.lines().count());
        try (EcologyJournal ignored = new EcologyJournal(file)) {
            // Reopening verifies each checksum and previousHash link.
        }
    }

    private static JsonObject payload(String name, String value) {
        JsonObject result = new JsonObject();
        result.addProperty(name, value);
        return result;
    }
}
