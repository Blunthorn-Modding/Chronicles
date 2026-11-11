package io.github.chronicle.core.enchanting;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IngredientEnchantmentCatalogTest {
    @Test void registersUniqueRecipesFreezesAndReturnsImmutableSortedCopy() {
        IngredientEnchantmentCatalog catalog = new IngredientEnchantmentCatalog();
        IngredientEnchantmentRecipe later = IngredientEnchantmentRecipeTest.recipe("chronicle:zeta", "minecraft:feather");
        IngredientEnchantmentRecipe earlier = IngredientEnchantmentRecipeTest.recipe("chronicle:alpha", "minecraft:echo_shard");
        catalog.register(later);
        catalog.register(earlier);
        Map<String, IngredientEnchantmentRecipe> recipes = catalog.recipes();
        assertEquals(java.util.List.of("chronicle:alpha", "chronicle:zeta"), recipes.keySet().stream().toList());
        assertThrows(UnsupportedOperationException.class, recipes::clear);
        catalog.freeze();
        assertThrows(IllegalStateException.class, () -> catalog.register(IngredientEnchantmentRecipeTest.recipe("chronicle:other", "minecraft:flint")));
    }

    @Test void rejectsDuplicateIdsAndGroupIngredientPairsEvenWhenDisabled() {
        IngredientEnchantmentCatalog catalog = new IngredientEnchantmentCatalog();
        catalog.register(IngredientEnchantmentRecipeTest.recipe("chronicle:echo", "minecraft:echo_shard"));
        assertThrows(IllegalArgumentException.class, () -> catalog.register(IngredientEnchantmentRecipeTest.recipe("chronicle:echo", "minecraft:flint")));
        IngredientEnchantmentRecipe disabledDuplicate = new IngredientEnchantmentRecipe("chronicle:other", "chronicle:forge", "minecraft:echo_shard", "minecraft:unbreaking", 1, 1, false);
        assertThrows(IllegalArgumentException.class, () -> catalog.register(disabledDuplicate));
    }
}
