package io.github.chronicle.core.client.hud;

import de.keksuccino.fancymenu.customization.element.editor.AbstractEditorElement;
import de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen;
import org.jetbrains.annotations.NotNull;

/** Lets FancyMenu persist movement without exposing gameplay controls. */
public final class ChronicleFoodEditorElement extends AbstractEditorElement {
    ChronicleFoodEditorElement(@NotNull ChronicleFoodElement element, @NotNull LayoutEditorScreen editor) {
        super(element, editor);
        settings.setParallaxAllowed(false);
        settings.setResizeable(false);
        settings.setAdvancedSizingSupported(false);
        settings.setStretchable(false);
        settings.setAutoSizingAllowed(false);
    }
}
