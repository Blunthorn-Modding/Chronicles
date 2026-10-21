package io.github.chronicle.core.enchanting;

/** One ingredient-selected enchantment grant; runtime compatibility remains a caller concern. */
public record IngredientEnchantmentRecipe(String id, String group, String ingredient, String enchantment,
                                          int level, int count, boolean enabled) {
    public IngredientEnchantmentRecipe {
        EnchantmentCaps.validId(id);
        EnchantmentCaps.validId(group);
        EnchantmentCaps.validId(ingredient);
        EnchantmentCaps.validId(enchantment);
        EnchantmentCaps.validLevel(level, "recipe level");
        if (count < 1 || count > 64) throw new IllegalArgumentException("recipe count must be between 1 and 64");
    }
}
