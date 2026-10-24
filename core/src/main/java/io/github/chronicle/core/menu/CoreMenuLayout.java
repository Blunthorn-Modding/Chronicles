package io.github.chronicle.core.menu;

/**
 * Pure geometry for the native editor popup.  Keeping these bounds independent of
 * Minecraft widgets lets the smoke harness assert that every interactive control
 * stays within the vanilla popup at each supported logical GUI size.
 */
public final class CoreMenuLayout {
    private static final int PADDING = 8;
    private static final int HEADER_HEIGHT = 18;
    private static final int FOOTER_HEIGHT = 18;

    private CoreMenuLayout() { }

    public static Layout forViewport(int width, int height) {
        int frameX = clamp(width / 48, 6, 20);
        int frameY = clamp(height / 32, 6, 12);
        Bounds frame = new Bounds(frameX, frameY, Math.max(1, width - 2 * frameX), Math.max(1, height - 2 * frameY));
        Bounds content = new Bounds(frame.x() + PADDING, frame.y() + PADDING,
                Math.max(1, frame.width() - 2 * PADDING), Math.max(1, frame.height() - 2 * PADDING));
        int footerY = Math.max(content.y(), content.bottom() - FOOTER_HEIGHT);
        int feedbackY = Math.max(content.y() + HEADER_HEIGHT, footerY - 12);
        int bodyTop = Math.max(content.y() + HEADER_HEIGHT + 4, frame.y() + 30);
        int bodyBottom = Math.max(bodyTop + 1, feedbackY - 4);
        int navWidth = Math.min(Math.max(86, Math.min(100, width / 5)), Math.max(1, content.width() - 125));
        int mainLeft = Math.min(content.right() - 1, content.x() + navWidth + 5);
        return new Layout(frame, content, content.y(), bodyTop, bodyBottom, feedbackY, footerY,
                navWidth, mainLeft, Math.max(1, content.right() - mainLeft), width <= 480);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Layout(Bounds frame, Bounds content, int headerY, int bodyTop, int bodyBottom,
                         int feedbackY, int footerY, int navWidth, int mainLeft, int mainWidth,
                         boolean compact) { }

    public record Bounds(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(int otherX, int otherY, int otherWidth, int otherHeight) {
            return otherX >= x && otherY >= y && otherX + otherWidth <= right() && otherY + otherHeight <= bottom();
        }
    }
}
