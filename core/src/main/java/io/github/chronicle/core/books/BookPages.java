package io.github.chronicle.core.books;

import java.util.ArrayList;
import java.util.List;

/** Lossless conservative pagination for vanilla's written-book text area. */
public final class BookPages {
    private static final int WIDTH = 108, LINES = 9, MAX_CODEPOINTS = 16384, MAX_PAGES = 1024;
    private BookPages() { }
    public static List<String> paginate(String text) {
        if (text == null || text.length() > 65536 || text.codePointCount(0, text.length()) > MAX_CODEPOINTS) throw new IllegalArgumentException("Book text is too large.");
        List<String> pages = new ArrayList<>(); StringBuilder page = new StringBuilder(); int line = 1, width = 0;
        for (int i = 0; i < text.length();) {
            char ch = text.charAt(i);
            if (Character.isSurrogate(ch) && (!Character.isHighSurrogate(ch) || i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1)))) throw new IllegalArgumentException("Invalid surrogate in book text.");
            int cp = text.codePointAt(i); int chars = Character.charCount(cp);
            if (cp == 0 || cp == 0x00a7) throw new IllegalArgumentException("Book text contains a forbidden character.");
            int glyph = cp == '\t' ? 24 : cp == ' ' ? 4 : cp < 128 ? 6 : 10;
            if (cp != '\n' && width + glyph > WIDTH) { line++; width = 0; }
            if (line > LINES) { pages.add(page.toString()); if (pages.size() >= MAX_PAGES) throw new IllegalArgumentException("Book text needs too many pages."); page.setLength(0); line = 1; width = 0; }
            page.appendCodePoint(cp); i += chars;
            if (cp == '\n') { line++; width = 0; } else width += glyph;
        }
        if (!page.isEmpty() || pages.isEmpty()) pages.add(page.toString());
        return List.copyOf(pages);
    }
}
