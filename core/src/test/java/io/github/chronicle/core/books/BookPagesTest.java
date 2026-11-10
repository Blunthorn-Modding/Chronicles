package io.github.chronicle.core.books;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookPagesTest {
    @Test void preservesUnicodeNewlinesTabsAndLongWords() {
        String text = ("M\u00fcnchen \ud83c\udf0d longword".repeat(20) + "\n\tline\n").repeat(12);
        var pages = BookPages.paginate(text); assertTrue(pages.size()>1); assertEquals(text,String.join("",pages));
        assertTrue(pages.stream().allMatch(page -> page.codePointCount(0,page.length()) <= 1024));
    }
    @Test void preservesPathologicalNewlinesAndRejectsUnsafeText() {
        String text = "\n".repeat(4096); assertEquals(text,String.join("",BookPages.paginate(text)));
        assertThrows(IllegalArgumentException.class,()->BookPages.paginate("bad\u00a7text"));
        assertThrows(IllegalArgumentException.class,()->BookPages.paginate("bad\0text"));
        assertThrows(IllegalArgumentException.class,()->BookPages.paginate("bad\ud800"));
    }
}
