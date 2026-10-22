package io.github.chronicle.core.client.hud;

import de.keksuccino.fancymenu.customization.element.AbstractElement;
import de.keksuccino.fancymenu.customization.element.ElementBuilder;
import io.github.chronicle.core.stats.FoodCapacity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** A separately selectable FancyMenu element; it is never inserted into a layout automatically. */
public final class ChronicleFoodElement extends AbstractElement {
    private static volatile long lastRenderNanos = Long.MIN_VALUE;

    ChronicleFoodElement(ElementBuilder<?, ?> builder) { super(builder); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        var player = MinecraftClient.getInstance().player;
        if (player == null || MinecraftClient.getInstance().options.hudHidden || player.isSpectator() || player.hasVehicle()
                || FoodCapacity.capacity(player) == 20) return;
        lastRenderNanos = System.nanoTime();
        ChronicleFoodRenderer.renderAt(context, player, getAbsoluteX(), getAbsoluteY());
    }

    @Override public int getAbsoluteWidth() { return 81; }
    @Override public int getAbsoluteHeight() { return ChronicleFoodRenderer.height(MinecraftClient.getInstance().player); }

    static boolean renderedRecently() {
        long rendered = lastRenderNanos;
        if (rendered == Long.MIN_VALUE) return false;
        long elapsed = System.nanoTime() - rendered;
        return elapsed >= 0 && elapsed < 250_000_000L;
    }
}
