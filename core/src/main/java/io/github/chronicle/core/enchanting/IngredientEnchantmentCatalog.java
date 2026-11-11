package io.github.chronicle.core.enchanting;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Startup-only registry of campaign-provided ingredient enchantment recipes. */
public final class IngredientEnchantmentCatalog {
    public static final int MAX_RECIPES = 256;
    private final TreeMap<String, IngredientEnchantmentRecipe> recipes = new TreeMap<>();
    private final Set<GroupIngredient> ingredients = new HashSet<>();
    private boolean frozen;

    public synchronized void register(IngredientEnchantmentRecipe recipe) {
        if (frozen) throw new IllegalStateException("ingredient enchantment catalog is frozen");
        if (recipe == null) throw new IllegalArgumentException("ingredient enchantment recipe is required");
        if (recipes.size() >= MAX_RECIPES) throw new IllegalArgumentException("ingredient enchantment catalog accepts at most " + MAX_RECIPES + " recipes");
        if (recipes.containsKey(recipe.id())) throw new IllegalArgumentException("duplicate ingredient enchantment recipe id: " + recipe.id());
        GroupIngredient key = new GroupIngredient(recipe.group(), recipe.ingredient());
        if (!ingredients.add(key)) throw new IllegalArgumentException("duplicate ingredient enchantment recipe group and ingredient: " + recipe.group() + "/" + recipe.ingredient());
        recipes.put(recipe.id(), recipe);
    }

    public synchronized void freeze() { frozen = true; }

    public synchronized Map<String, IngredientEnchantmentRecipe> recipes() {
        return Collections.unmodifiableMap(new TreeMap<>(recipes));
    }

    static List<IngredientEnchantmentRecipe> immutableValidated(List<IngredientEnchantmentRecipe> source, String field) {
        if (source == null) throw new IllegalArgumentException(field + " is required");
        if (source.size() > MAX_RECIPES) throw new IllegalArgumentException(field + " may contain at most " + MAX_RECIPES + " recipes");
        Set<String> ids = new HashSet<>();
        Set<GroupIngredient> pairs = new HashSet<>();
        for (IngredientEnchantmentRecipe recipe : source) {
            if (recipe == null) throw new IllegalArgumentException(field + " may not contain null recipes");
            if (!ids.add(recipe.id())) throw new IllegalArgumentException(field + " contains duplicate recipe id: " + recipe.id());
            if (!pairs.add(new GroupIngredient(recipe.group(), recipe.ingredient()))) throw new IllegalArgumentException(field + " contains duplicate group and ingredient: " + recipe.group() + "/" + recipe.ingredient());
        }
        return List.copyOf(source);
    }

    private record GroupIngredient(String group, String ingredient) { }
}
