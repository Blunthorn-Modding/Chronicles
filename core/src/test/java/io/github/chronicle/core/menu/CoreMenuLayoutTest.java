package io.github.chronicle.core.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreMenuLayoutTest {
    @Test
    void popupContentIsInsetAtSupportedLogicalSizes() {
        assertLayout(320, 240, 6, 7, 308, 226, 14);
        assertLayout(480, 270, 10, 8, 460, 254, 18);
        assertLayout(640, 360, 13, 11, 614, 338, 21);
        assertLayout(960, 287, 20, 8, 920, 271, 28);
    }

    @Test
    void bodyAndFooterStayInsideContent() {
        CoreMenuLayout.Layout layout = CoreMenuLayout.forViewport(320, 240);
        assertTrue(layout.bodyTop() >= layout.content().y());
        assertTrue(layout.bodyBottom() < layout.feedbackY());
        assertTrue(layout.feedbackY() < layout.footerY());
        assertTrue(layout.content().contains(layout.mainLeft(), layout.bodyTop(), layout.mainWidth(),
                layout.bodyBottom() - layout.bodyTop()));
    }

    private static void assertLayout(int width, int height, int frameX, int frameY, int frameWidth, int frameHeight, int firstTabX) {
        CoreMenuLayout.Layout layout = CoreMenuLayout.forViewport(width, height);
        assertEquals(new CoreMenuLayout.Bounds(frameX, frameY, frameWidth, frameHeight), layout.frame());
        assertEquals(firstTabX, layout.content().x());
        assertTrue(layout.content().contains(layout.content().x(), layout.bodyTop(), layout.navWidth(), 18));
    }
}
