package io.github.chronicle.core.client.hud;

import de.keksuccino.fancymenu.customization.element.ElementRegistry;

/** Direct FancyMenu API use stays behind FoodHud's optional-mod guard. */
public final class ChronicleFancyFood {
    private static boolean registered;

    private ChronicleFancyFood() { }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ElementRegistry.register(new ChronicleFoodElementBuilder());
    }

    public static boolean renderedRecently() { return ChronicleFoodElement.renderedRecently(); }
}
