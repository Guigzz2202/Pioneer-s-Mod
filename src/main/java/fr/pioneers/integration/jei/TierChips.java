package fr.pioneers.integration.jei;

import fr.pioneers.api.machine.PowerRules;
import fr.pioneers.api.machine.tier.GeneratedTierFamilies;
import fr.pioneers.api.machine.tier.TierRecipes;
import fr.pioneers.tech.bronze.bench.BenchRecipe.Counted;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 1.0.5-beta (lot 38, retour 49, specification de l'age du bronze §2) : pastille « Tier min. n » des pages JEI des recettes basses des
 * familles de tiers (meule, concasseur, tamis, scie, tour a bois, machines a vapeur) : toute machine de la famille de ce tier ou plus la fait,
 * deux fois plus vite par tier au-dessus ; une recette a rendement (matiere premiere) rend R(N) / R(m) (plafond a T1 des concasses, regle 1).
 */
public final class TierChips {

    private TierChips() {
    }

    /** Pastille « Tier min. m » (couleur du tier), infobulle : regle de la famille et rendements (recette a rendement). */
    public static JeiDraw.Chip minTier(int m, ResourceLocation id, List<Counted> inputs, ItemStack main) {
        int tier = Math.max(0, Math.min(GeneratedTierFamilies.COLORS.length - 1, m));
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("tier.pioneers.global." + tier));
        tip.add(Component.translatable("jei.pioneers.min_tier.tip", tier));
        if (GeneratedTierFamilies.YIELD_RECIPES.contains(id.toString())) {
            boolean capped = TierRecipes.capped(inputs, main.isEmpty() ? List.of() : List.of(new TierRecipes.Out(main, 1F)));
            tip.add(Component.translatable("jei.pioneers.min_tier.yield_tip", factor(1, tier, capped), factor(2, tier, capped), factor(5, tier, capped)));
        }
        return new JeiDraw.Chip(JeiDraw.Pic.INFO, Component.translatable("jei.pioneers.chip.min_tier", tier), 0xFF000000 | GeneratedTierFamilies.COLORS[tier], tip);
    }

    /** Multiplicateur au tier global n d'une recette a rendement de tier m (plafond a T1 : regle 1). */
    static String factor(int n, int m, boolean capped) {
        return MeltingCategories.number(n < m ? 1 : PowerRules.yieldFactor(capped ? Math.min(n, Math.max(1, m)) : n, m));
    }
}
