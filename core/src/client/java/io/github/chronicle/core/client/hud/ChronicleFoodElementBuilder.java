package io.github.chronicle.core.client.hud;

import de.keksuccino.fancymenu.customization.element.AbstractElement;
import de.keksuccino.fancymenu.customization.element.ElementBuilder;
import de.keksuccino.fancymenu.customization.element.SerializedElement;
import de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen;
import de.keksuccino.spiffyhud.customization.SpiffyOverlayScreen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Persisted, editable Chronicle element registered alongside upstream element types. */
public final class ChronicleFoodElementBuilder extends ElementBuilder<ChronicleFoodElement, ChronicleFoodEditorElement> {
    public ChronicleFoodElementBuilder() { super("chronicle_core_expanded_food"); }

    @Override public @NotNull ChronicleFoodElement buildDefaultInstance() {
        ChronicleFoodElement element = new ChronicleFoodElement(this);
        element.baseWidth = 81;
        element.baseHeight = 20;
        element.stickyAnchor = true;
        element.stayOnScreen = false;
        return element;
    }

    @Override public ChronicleFoodElement deserializeElement(@NotNull SerializedElement serialized) { return buildDefaultInstance(); }

    @Override protected SerializedElement serializeElement(@NotNull ChronicleFoodElement element, @NotNull SerializedElement out) { return out; }

    @Override public @NotNull ChronicleFoodEditorElement wrapIntoEditorElement(@NotNull ChronicleFoodElement element,
            @NotNull LayoutEditorScreen editor) {
        return new ChronicleFoodEditorElement(element, editor);
    }

    @Override public @NotNull Text getDisplayName(@Nullable AbstractElement element) {
        return Text.literal("Chronicle expanded food");
    }

    @Override public @Nullable Text[] getDescription(@Nullable AbstractElement element) {
        return new Text[] { Text.literal("Uses Chronicle's synchronized food capacity.") };
    }

    @Override public boolean shouldShowUpInEditorElementMenu(@NotNull LayoutEditorScreen editor) {
        return editor.layoutTargetScreen instanceof SpiffyOverlayScreen;
    }
}
