package io.github.chronicle.core.books;

import java.util.List;
import java.util.regex.Pattern;

/** A bounded, literal-text book definition owned by the campaign. */
public record BookDefinition(String id, String title, String author, String description, List<String> pages, Rule rule) {
    public static final int MAX_ID = 160;
    public static final int MAX_TITLE = 32;
    public static final int MAX_AUTHOR = 80;
    public static final int MAX_DESCRIPTION = 256;
    public static final int MAX_PAGES = 64;
    public static final int MAX_PAGE_CHARACTERS = 1_024;
    public static final int MAX_TOTAL_PAGE_CHARACTERS = 16_384;
    /** IDs beneath this root are created by the server for organizer-authored books. */
    public static final String GENERATED_ID_ROOT = "chronicle:book/";
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public enum Rule { OFF, ONCE, UNTIL_FOUND, REPEAT }

    public BookDefinition {
        if (id == null || id.length() > MAX_ID || !ID.matcher(id).matches()) throw new IllegalArgumentException("book id must be a namespaced identifier");
        title = requiredMetadata(title, "title", MAX_TITLE);
        author = optionalMetadata(author, "author", MAX_AUTHOR);
        description = optionalMetadata(description, "description", MAX_DESCRIPTION);
        if (pages == null || pages.size() > MAX_PAGES) throw new IllegalArgumentException("pages must contain at most " + MAX_PAGES + " entries");
        pages = List.copyOf(pages);
        int total = 0;
        boolean nonblank = false;
        for (String page : pages) {
            if (page == null) throw new IllegalArgumentException("pages must not contain null");
            int length = page.codePointCount(0, page.length());
            if (length > MAX_PAGE_CHARACTERS) throw new IllegalArgumentException("each page must contain at most " + MAX_PAGE_CHARACTERS + " characters");
            total += length;
            if (total > MAX_TOTAL_PAGE_CHARACTERS) throw new IllegalArgumentException("pages must contain at most " + MAX_TOTAL_PAGE_CHARACTERS + " characters in total");
            if (!page.isBlank()) nonblank = true;
        }
        if (rule == null) throw new IllegalArgumentException("book rule is required");
        if (!nonblank && rule != Rule.OFF) throw new IllegalArgumentException("a book without a nonblank page must use rule OFF");
    }

    public static boolean isGeneratedId(String id) { return id != null && id.startsWith(GENERATED_ID_ROOT); }

    private static String requiredMetadata(String value, String field, int limit) {
        String result = optionalMetadata(value, field, limit);
        if (result.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return result;
    }

    private static String optionalMetadata(String value, String field, int limit) {
        if (value == null || value.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint) || codePoint == '\u00a7')) {
            throw new IllegalArgumentException(field + " must be literal, control-free text");
        }
        if (value.codePointCount(0, value.length()) > limit) throw new IllegalArgumentException(field + " must contain at most " + limit + " characters");
        return value;
    }
}
