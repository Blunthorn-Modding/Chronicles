package io.github.chronicle.core.stats;

import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;

public final class FoodCapacity {
    private FoodCapacity() { }

    /** A manager belongs to its actual player, never a process-global/server-thread value. */
    public interface Owner {
        void chronicle$bind(PlayerEntity player);
    }

    public static int capacity(PlayerEntity player) {
        if (player == null) return 20;
        double value = player.getAttributeValue(OfferingAttributes.FOOD_CAPACITY);
        return Double.isFinite(value) ? Math.clamp((int) Math.floor(value), 2, 40) : 20;
    }

    /** Called after authoritative projection, not during player loading or client packet ordering. */
    public static void clamp(HungerManager hunger, int capacity) {
        int food = Math.clamp(hunger.getFoodLevel(), 0, capacity);
        if (hunger.getFoodLevel() != food) hunger.setFoodLevel(food);
        float saturation = hunger.getSaturationLevel();
        float bounded = Float.isFinite(saturation) ? Math.clamp(saturation, 0, food) : 0;
        if (saturation != bounded) hunger.setSaturationLevel(bounded);
    }
}
