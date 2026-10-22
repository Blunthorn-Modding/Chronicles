package io.github.chronicle.core.client.hud;

import io.github.chronicle.core.stats.FoodCapacity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/** Original renderer using Minecraft's public HUD sprite identifiers; no vendor art is copied. */
final class ChronicleFoodRenderer {
    private static final Identifier EMPTY = Identifier.ofVanilla("hud/food_empty");
    private static final Identifier HALF = Identifier.ofVanilla("hud/food_half");
    private static final Identifier FULL = Identifier.ofVanilla("hud/food_full");
    private static final Identifier EMPTY_HUNGER = Identifier.ofVanilla("hud/food_empty_hunger");
    private static final Identifier HALF_HUNGER = Identifier.ofVanilla("hud/food_half_hunger");
    private static final Identifier FULL_HUNGER = Identifier.ofVanilla("hud/food_full_hunger");
    private static final int ICON = 9;
    private static final int STEP = 8;
    private static final int ROW_HEIGHT = 10;

    private ChronicleFoodRenderer() { }

    static int height(PlayerEntity player) {
        return rows(FoodCapacity.capacity(player)) * ROW_HEIGHT;
    }

    static void render(DrawContext context, PlayerEntity player) {
        if (player == null) return;
        int rows = rows(FoodCapacity.capacity(player));
        renderAt(context, player, context.getScaledWindowWidth() / 2 + 10,
                context.getScaledWindowHeight() - 39 - (rows - 1) * ROW_HEIGHT);
    }

    /** Renders into an editor-controlled element with the supplied upper-left corner. */
    static void renderAt(DrawContext context, PlayerEntity player, int x, int y) {
        if (player == null) return;
        int capacity = FoodCapacity.capacity(player);
        if (capacity == 20) return;
        int food = Math.clamp(player.getHungerManager().getFoodLevel(), 0, capacity);
        int slots = (capacity + 1) / 2;
        boolean hungry = player.hasStatusEffect(StatusEffects.HUNGER);
        Identifier empty = hungry ? EMPTY_HUNGER : EMPTY;
        Identifier half = hungry ? HALF_HUNGER : HALF;
        Identifier full = hungry ? FULL_HUNGER : FULL;
        int rows = rows(capacity);
        for (int index = 0; index < slots; index++) {
            int row = index / 10;
            int column = index % 10;
            int iconX = x + 72 - column * STEP;
            // Preserve the familiar vanilla row at the bottom; capacity beyond
            // twenty grows upward into additional rows.
            int iconY = y + (rows - 1 - row) * ROW_HEIGHT;
            draw(context, empty, iconX, iconY);
            int points = index * 2;
            if (food >= points + 2) draw(context, full, iconX, iconY);
            else if (food == points + 1) draw(context, half, iconX, iconY);
        }
    }

    private static int rows(int capacity) { return Math.max(1, ((capacity + 1) / 2 + 9) / 10); }

    private static void draw(DrawContext context, Identifier sprite, int x, int y) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, y, ICON, ICON, 0xFFFFFFFF);
    }
}
