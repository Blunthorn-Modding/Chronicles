package io.github.chronicle.core.books;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BookConfigTest {
    @TempDir Path directory;

    private static BookDefinition book(String id, String title) {
        return new BookDefinition(id, title, "Archivist", "A recovered record.", List.of("", "Its literal first entry."), BookDefinition.Rule.UNTIL_FOUND);
    }

    @Test void createsStrictConfigAndOverlaysCompleteConfiguredDefinitions() throws Exception {
        Path file = directory.resolve("config/chronicle/books.json");
        BookDefinition defaultOne = book("example:one", "Default one");
        BookDefinition defaultTwo = book("example:two", "Default two");
        BookConfig config = new BookConfig(file);
        assertEquals(Map.of(defaultOne.id(), defaultOne, defaultTwo.id(), defaultTwo), config.loadOrCreate(Map.of(defaultOne.id(), defaultOne, defaultTwo.id(), defaultTwo)));
        assertTrue(Files.readString(file).contains("\n  \"books\": ["));

        Files.writeString(file, """
                {"schemaVersion":1,"books":[
                  {"id":"example:one","title":"Configured one","author":"","description":"A changed record.","pages":["","Kept in order."],"rule":"REPEAT"},
                  {"id":"custom:book/archive","title":"Custom","author":"","description":"A custom record.","pages":["Custom text"],"rule":"ONCE"}
                ]}
                """);
        Map<String, BookDefinition> loaded = config.loadOrCreate(Map.of(defaultOne.id(), defaultOne, defaultTwo.id(), defaultTwo));
        assertEquals("Configured one", loaded.get(defaultOne.id()).title());
        assertEquals(defaultTwo, loaded.get(defaultTwo.id()));
        assertEquals(List.of("Custom text"), loaded.get("custom:book/archive").pages());
        assertThrows(UnsupportedOperationException.class, () -> loaded.clear());
    }

    @Test void rejectsUnknownFutureMalformedAndDuplicateJsonWithoutWriting() throws Exception {
        Path file = directory.resolve("books.json");
        String duplicate = "{\"schemaVersion\":1,\"books\":[],\"books\":[]}";
        Files.writeString(file, duplicate);
        IOException failure = assertThrows(IOException.class, () -> new BookConfig(file).loadOrCreate(Map.of()));
        assertTrue(failure.getMessage().contains("duplicate"));
        assertEquals(duplicate, Files.readString(file));

        String unknown = "{\"schemaVersion\":1,\"books\":[],\"future\":true}";
        Files.writeString(file, unknown);
        assertThrows(IOException.class, () -> new BookConfig(file).loadOrCreate(Map.of()));
        assertEquals(unknown, Files.readString(file));

        String future = "{\"schemaVersion\":2,\"books\":[]}";
        Files.writeString(file, future);
        assertThrows(IOException.class, () -> new BookConfig(file).loadOrCreate(Map.of()));
        assertEquals(future, Files.readString(file));

        String malformed = "{\"schemaVersion\":1.0,\"books\":[]}";
        Files.writeString(file, malformed);
        assertThrows(IOException.class, () -> new BookConfig(file).loadOrCreate(Map.of()));
        assertEquals(malformed, Files.readString(file));
    }

    @Test void savesOptimisticallyCreatesOnlyGeneratedIdsAndPreservesBackup() throws Exception {
        Path file = directory.resolve("books.json");
        BookDefinition original = book("example:journal", "Original");
        BookDefinition replacement = book("example:journal", "Revised");
        BookConfig config = new BookConfig(file);
        config.loadOrCreate(Map.of(original.id(), original));
        String before = Files.readString(file);
        assertEquals(replacement, config.replace(replacement, original).get(replacement.id()));
        assertEquals(before, Files.readString(file.resolveSibling("books.json.bak")));

        BookDefinition generated = book(BookDefinition.GENERATED_ID_ROOT + "9d6b", "New");
        assertEquals(generated, config.replace(generated, null).get(generated.id()));
        assertThrows(IllegalArgumentException.class, () -> config.replace(book("custom:not_generated", "No"), null));
        assertThrows(IllegalArgumentException.class, () -> config.replace(generated, null));
    }

    @Test void rejectsStaleAndFailedSavesWithoutPublishingReplacementOrOverwritingExistingBackup() throws Exception {
        Path file = directory.resolve("books.json");
        BookDefinition original = book("example:journal", "Original");
        BookDefinition replacement = book("example:journal", "Revised");
        BookConfig config = new BookConfig(file);
        config.loadOrCreate(Map.of(original.id(), original));
        String originalBytes = Files.readString(file);
        Files.writeString(file, originalBytes + "\n");
        IOException external = assertThrows(IOException.class, () -> config.replace(replacement, original));
        assertTrue(external.getMessage().contains("changed outside Chronicle"));
        assertEquals(originalBytes + "\n", Files.readString(file));

        Files.writeString(file, originalBytes);
        Path backup = file.resolveSibling("books.json.bak");
        Files.createDirectory(backup);
        Files.writeString(backup.resolve("keep"), "do not replace this directory");
        assertThrows(IOException.class, () -> config.replace(replacement, original));
        assertEquals(originalBytes, Files.readString(file));
        assertEquals("do not replace this directory", Files.readString(backup.resolve("keep")));
    }
    @Test void detectsExternalWriteAtCommitBoundaryAndOversizedWireIds() throws Exception {
        Path file = directory.resolve("books.json");
        var original = book("example:one", "Original");
        var config = new BookConfig(file, () -> Files.writeString(file, "{\"schemaVersion\":1,\"books\":[]}\n"));
        config.loadOrCreate(Map.of(original.id(), original));
        assertThrows(IOException.class, () -> config.replace(book(original.id(), "Changed"), original));
        assertEquals("{\"schemaVersion\":1,\"books\":[]}\n", Files.readString(file));
        String json = BookDraftJson.encode(original).replace("example:one", "example:" + "x".repeat(161));
        assertThrows(IllegalArgumentException.class, () -> BookDraftJson.decode(json));
        assertThrows(IllegalArgumentException.class, () -> BookConfig.decode("{\"schemaVersion\":1,\"books\":[" + json + "]}"));
    }

}
