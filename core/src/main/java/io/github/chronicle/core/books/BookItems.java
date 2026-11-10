package io.github.chronicle.core.books;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.UUID;

/** Shared literal written-book encoding; markers do not alter page text. */
public final class BookItems {
    private BookItems() { }
    public static String managed(BookDefinition book, String campaign, String token) {
        JsonObject marker = new JsonObject(); marker.addProperty("campaign", campaign); marker.addProperty("book", book.id()); marker.addProperty("token", token);
        JsonObject custom = new JsonObject(); custom.add("chronicle_book", marker); return stack(book, custom);
    }
    public static String personal(BookDefinition book, UUID request) {
        JsonObject custom = new JsonObject(); custom.addProperty("chronicle_personal_copy", request.toString()); return stack(book, custom);
    }
    /** Reject malformed UTF-16 before JSON encoding or hashing can silently replace it. */
    public static void validateLiteral(BookDefinition book) {
        java.util.Objects.requireNonNull(book, "book");
        validUnicode(book.id()); validUnicode(book.title()); validUnicode(book.author()); validUnicode(book.description());
        book.pages().forEach(BookItems::validUnicode);
    }
    private static void validUnicode(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) { if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw new IllegalArgumentException("Book contains invalid Unicode."); }
            else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Book contains invalid Unicode.");
        }
    }
    private static String stack(BookDefinition book, JsonObject custom) {
        validateLiteral(book);
        JsonObject content = new JsonObject(); content.addProperty("title", book.title()); content.addProperty("author", book.author()); content.addProperty("generation", 0); content.addProperty("resolved", true);
        JsonArray pages = new JsonArray(); for (String page : book.pages()) { JsonObject text = new JsonObject(); text.addProperty("text", page); pages.add(text); } content.add("pages", pages);
        JsonObject components = new JsonObject(); components.add("minecraft:written_book_content", content); components.add("minecraft:custom_data", custom);
        JsonObject stack = new JsonObject(); stack.addProperty("id", "minecraft:written_book"); stack.addProperty("count", 1); stack.add("components", components); return stack.toString();
    }
}
