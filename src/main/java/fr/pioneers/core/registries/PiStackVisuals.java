package fr.pioneers.core.registries;

import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Visuel de pile du standard items : le modele de l'item change avec la quantite (predicat {@code pioneers:stack_level}).
 * Seuils = BREAKS de tools/gc/core/item_bake.py : 1 | 2-16 | 17-40 | 41-63 | 64 (pile cerclee) -> niveaux 0..4.
 * L'enregistrement du predicat est fait cote client (PiClientRegistration#clientSetup).
 */
public final class PiStackVisuals {

    private PiStackVisuals() {
    }

    public static final ResourceLocation STACK_LEVEL = Pioneers.rl("stack_level");

    private static final List<Supplier<? extends Item>> ITEMS = new ArrayList<>();

    public static void add(Supplier<? extends Item> item) {
        ITEMS.add(item);
    }

    public static List<Supplier<? extends Item>> items() {
        return Collections.unmodifiableList(ITEMS);
    }

    /** Niveau de pile 0..4 d'une pile. */
    public static float level(ItemStack stack) {
        int n = stack.getCount();
        return n >= 64 ? 4 : n >= 41 ? 3 : n >= 17 ? 2 : n >= 2 ? 1 : 0;
    }
}
