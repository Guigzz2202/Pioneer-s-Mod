package fr.pioneers.machine;

import fr.pioneers.recipe.PiRecipes;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

/**
 * 0.20.46 (audit) : filtre « une recette prend-elle cet objet ? » des cases d'entree, garde par objet (piles sans NBT) et par machine
 * jusqu'au prochain rechargement des recettes, comme {@code IndustryRecipes.accepts} : il tournait a chaque essai d'insertion des cables,
 * entonnoirs et robots (recherche de cuisson, copie de la liste des recettes...). Caches separes client / serveur (en solo les deux
 * gestionnaires de recettes alternent).
 */
public final class AcceptCache {

    private static final class Side {

        RecipeManager manager;
        int generation = -1;
        final Map<Object, Map<Item, Boolean>> byKey = new HashMap<>();
    }

    private static final Side[] SIDES = {new Side(), new Side()};

    private AcceptCache() {
    }

    /**
     * Resultat de {@code scan} pour cet objet, garde sous {@code key} (une valeur par sorte de machine). Les piles avec NBT ne sont pas
     * gardees (un ingredient peut dependre du NBT).
     */
    public static synchronized boolean test(Level level, Object key, ItemStack stack, Predicate<ItemStack> scan) {
        if (stack.isEmpty() || stack.hasTag()) {
            return scan.test(stack);
        }
        Side side = SIDES[level.isClientSide ? 1 : 0];
        RecipeManager manager = level.getRecipeManager();
        int generation = PiRecipes.generation();
        if (manager != side.manager || side.generation != generation) {
            side.manager = manager;
            side.generation = generation;
            side.byKey.clear();
        }
        Map<Item, Boolean> known = side.byKey.computeIfAbsent(key, k -> new HashMap<>());
        Boolean result = known.get(stack.getItem());
        if (result == null) {
            result = scan.test(stack);
            known.put(stack.getItem(), result);
        }
        return result;
    }
}
