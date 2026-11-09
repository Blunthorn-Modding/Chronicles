package io.github.chronicle.core.menu;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EventPoolMenuLayoutTest {
    @Test void sharesNormalEditorFrameAndContainsActionsAtAllSupportedSizes() {
        for (int[] size : new int[][]{{320,240},{480,270},{640,360},{960,287},{1920,1080}}) {
            var layout=EventPoolMenuLayout.forViewport(size[0],size[1]);
            var menu=layout.menu();var content=menu.content();
            assertEquals(CoreMenuLayout.forViewport(size[0],size[1]).frame(),menu.frame());
            assertTrue(menu.frame().x()>2);assertTrue(menu.frame().y()>2);
            assertTrue(content.contains(content.x(),menu.footerY(),layout.listWidth(),18));
            assertTrue(content.contains(layout.detailX(),menu.footerY(),layout.detailWidth(),18));
            assertTrue(menu.footerY()+18<=content.bottom());
            assertTrue(layout.rowsTop()+layout.rows()*20<=layout.summaryY());
            assertTrue(layout.summaryY()+36<layout.editY()-10);
            assertTrue(layout.editY()+18<=menu.bodyBottom());
            assertTrue(layout.detailWidth()>=145,"Compact weight and remove controls must fit");
        }
    }
}
