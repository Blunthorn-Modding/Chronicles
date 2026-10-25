package io.github.chronicle.core.books;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BookCatalogTest {
    private static BookDefinition book(String id) {
        return new BookDefinition(id, "Field notes", "", "A recovered journal.", List.of("First page"), BookDefinition.Rule.UNTIL_FOUND);
    }

    @Test void validatesBookBoundsLiteralMetadataAndBlankPageRule() {
        assertEquals("", new BookDefinition("example:untitled_description", "Notes", "", "", List.of("text"), BookDefinition.Rule.OFF).description());
        assertEquals(List.of("", "  ", "text", ""), new BookDefinition("example:blanks", "Notes", "", "A record.", List.of("", "  ", "text", ""), BookDefinition.Rule.ONCE).pages());
        assertDoesNotThrow(() -> new BookDefinition("example:empty", "Notes", "", "A record.", List.of("", "  "), BookDefinition.Rule.OFF));
        assertThrows(IllegalArgumentException.class, () -> new BookDefinition("example:empty", "Notes", "", "A record.", List.of("", "  "), BookDefinition.Rule.REPEAT));
        assertThrows(IllegalArgumentException.class, () -> new BookDefinition("no_namespace", "Notes", "", "A record.", List.of("text"), BookDefinition.Rule.OFF));
        assertThrows(IllegalArgumentException.class, () -> book("example:" + "x".repeat(161)));
        assertThrows(IllegalArgumentException.class, () -> new BookDefinition("example:control", "Bad\nTitle", "", "A record.", List.of("text"), BookDefinition.Rule.OFF));
        assertThrows(IllegalArgumentException.class, () -> new BookDefinition("example:format", "Bad\u00a7a", "", "A record.", List.of("text"), BookDefinition.Rule.OFF));
        assertThrows(IllegalArgumentException.class, () -> new BookDefinition("example:many", "Notes", "", "A record.", java.util.Collections.nCopies(65, "text"), BookDefinition.Rule.OFF));
        assertThrows(IllegalArgumentException.class, () -> new BookDefinition("example:long", "Notes", "", "A record.", List.of("x".repeat(1025)), BookDefinition.Rule.OFF));
        assertThrows(IllegalArgumentException.class, () -> new BookDefinition("example:total", "Notes", "", "A record.", java.util.Collections.nCopies(17, "x".repeat(1024)), BookDefinition.Rule.OFF));
    }

    @Test void freezesAnImmutableBoundedCatalog() {
        BookCatalog catalog = new BookCatalog();
        catalog.register(book("example:one"));
        assertThrows(UnsupportedOperationException.class, () -> catalog.books().clear());
        assertThrows(IllegalArgumentException.class, () -> catalog.register(book("example:one")));
        catalog.freeze();
        assertThrows(IllegalStateException.class, () -> catalog.register(book("example:two")));

        BookCatalog full = new BookCatalog();
        for (int i = 0; i < BookCatalog.MAX_BOOKS; i++) full.register(book("example:book" + i));
        assertThrows(IllegalArgumentException.class, () -> full.register(book("example:overflow")));
    }
}
