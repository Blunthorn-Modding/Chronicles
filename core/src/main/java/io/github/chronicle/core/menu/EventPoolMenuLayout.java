package io.github.chronicle.core.menu;

/** Pool-specific columns inside the same frame and footer as the normal editor. */
public record EventPoolMenuLayout(CoreMenuLayout.Layout menu, int listWidth, int detailX,
                                  int detailWidth, int rowsTop, int rows, int summaryY, int editY) {
    public static EventPoolMenuLayout forViewport(int width, int height) {
        var menu = CoreMenuLayout.forViewport(width, height);
        int listWidth = (menu.content().width() - 6) * 2 / 5;
        int detailX = menu.content().x() + listWidth + 6;
        int editY = menu.bodyBottom() - 20;
        int summaryY = editY - 52;
        int rowsTop = menu.bodyTop() + 22;
        int rows = Math.max(1, (summaryY - rowsTop) / 20);
        return new EventPoolMenuLayout(menu, listWidth, detailX, menu.content().right() - detailX,
                rowsTop, rows, summaryY, editY);
    }
}
