package io.github.chronicle.core.enchanting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IngredientEnchantmentRecipeTest {
    @Test void validatesEveryIdLevelAndCount() {
        assertEquals(1, recipe("chronicle:echo", "minecraft:echo_shard").level());
        assertThrows(IllegalArgumentException.class, () -> new IngredientEnchantmentRecipe("Chronicle:echo", "chronicle:forge", "minecraft:echo_shard", "minecraft:sharpness", 1, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new IngredientEnchantmentRecipe("chronicle:echo", "chronicle:forge", "minecraft:echo_shard", "minecraft:sharpness", 0, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new IngredientEnchantmentRecipe("chronicle:echo", "chronicle:forge", "minecraft:echo_shard", "minecraft:sharpness", 1, 65, true));
    }

    static IngredientEnchantmentRecipe recipe(String id, String ingredient) {
        return new IngredientEnchantmentRecipe(id, "chronicle:forge", ingredient, "minecraft:sharpness", 1, 1, true);
    }
}
