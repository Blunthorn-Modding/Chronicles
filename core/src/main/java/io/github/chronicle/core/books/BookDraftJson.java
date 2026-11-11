package io.github.chronicle.core.books;

import java.util.Map;

/** Single-definition wire form; shares strict validation with the on-disk catalog. */
public final class BookDraftJson {
    private BookDraftJson() { }
    public static BookDefinition decode(String json) {
        if (json == null || json.length() > 131072) throw new IllegalArgumentException("Book draft exceeds the menu limit");
        Map<String, BookDefinition> books = BookConfig.decode("{\"schemaVersion\":1,\"books\":[" + json + "]}");
        if (books.size() != 1) throw new IllegalArgumentException("Choose one book definition");
        return books.values().iterator().next();
    }
    public static String encode(BookDefinition book) { return new com.google.gson.Gson().toJson(book); }
}
