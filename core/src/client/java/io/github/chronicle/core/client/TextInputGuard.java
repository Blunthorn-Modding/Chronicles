package io.github.chronicle.core.client;

import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;

/** Detects text inputs even when a screen focuses its widget directly. */
public final class TextInputGuard {
    private TextInputGuard() { }

    public static boolean editingText(Screen screen) {
        if (screen == null) return false;
        Element focused = screen.getFocused();
        for (int depth = 0; focused != null && depth < 16; depth++) {
            if (usableTextInput(focused)) return true;
            focused = focused instanceof ParentElement parent ? parent.getFocused() : null;
        }
        return containsFocusedInput(screen, 0);
    }

    private static boolean usableTextInput(Element element) {
        return (element instanceof TextFieldWidget || element instanceof EditBoxWidget)
                && element instanceof ClickableWidget widget && widget.visible && widget.active;
    }

    private static boolean containsFocusedInput(Element element, int depth) {
        if (depth >= 16) return false;
        if (usableTextInput(element) && element.isFocused()) return true;
        if (element instanceof ParentElement parent) {
            Element focused = parent.getFocused();
            if (focused != null && containsFocusedInput(focused, depth + 1)) return true;
            // CreativeInventoryScreen focuses its selectable search child directly,
            // without setting the screen's focused element.
            for (Element child : parent.children()) {
                if (child != focused && containsFocusedInput(child, depth + 1)) return true;
            }
        }
        return false;
    }
}
