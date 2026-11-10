package io.github.chronicle.core.books;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookItemsTest {
    @Test void bothMarkersShareLiteralPagesAndMetadata() {
        var book=new BookDefinition("test:book","Title","Author","",List.of("literal \"quotes\" ? text", "??\nline"),BookDefinition.Rule.OFF);
        var managed=JsonParser.parseString(BookItems.managed(book,"campaign","token")).getAsJsonObject();
        var personal=JsonParser.parseString(BookItems.personal(book,UUID.randomUUID())).getAsJsonObject();
        var a=managed.getAsJsonObject("components"); var b=personal.getAsJsonObject("components");
        assertEquals(a.get("minecraft:written_book_content"),b.get("minecraft:written_book_content"));
        assertEquals(book.pages().get(0),b.getAsJsonObject("minecraft:written_book_content").getAsJsonArray("pages").get(0).getAsJsonObject().get("text").getAsString());
        assertTrue(a.getAsJsonObject("minecraft:custom_data").has("chronicle_book"));
        assertFalse(b.getAsJsonObject("minecraft:custom_data").has("chronicle_book"));
    }
    @Test void malformedUnicodeIsRejectedBeforeRendering() {
        var malformed = new BookDefinition("test:book", "Title", "Author", "", List.of("\ud800"), BookDefinition.Rule.OFF);
        assertThrows(IllegalArgumentException.class, () -> BookItems.personal(malformed, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> BookItems.managed(malformed, "campaign", "token"));
    }

}
