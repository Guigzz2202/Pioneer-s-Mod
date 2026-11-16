package fr.pioneers.integration.jei;

import fr.pioneers.api.machine.PowerRules;
import fr.pioneers.api.machine.tier.GeneratedTierFamilies;
import fr.pioneers.api.machine.tier.TierRecipes;
import fr.pioneers.core.Texts;
import fr.pioneers.tech.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.tech.industry.Tiers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 38, retour 49, specification de l'age du bronze §2) : pastilles de tier des pages JEI.
 * <p>
 * 1.0.6 (demande de Guillaume du 16/11/2026 : « indiquer clairement quels tiers la recette permet » et « afficher le cout en energie et la
 * duree du plus bas tier, autre que mecanique et vapeur si possible ») :
 * <ul>
 *     <li>{@link #yieldOf} : rendement d'une recette a rendement (la pastille « tiers permis » a ete retiree : le filigrane suffit) ;</li>
 *     <li>{@link #electric} : energie et duree au plus bas tier ELECTRIQUE (MKI) d'une recette basse (mecanique ou vapeur) de sa famille ;
 *     sans machine electrique dans la famille, la page garde sa duree mecanique ou a vapeur, sans cout.</li>
 * </ul>
 */
public final class TierChips {

    /** Tier le plus haut d'une machine de traitement (T5 = MKIV ; T6 = hors echelle, jamais une machine de traitement). */
    public static final int MAX = 5;
    /** Tier global de la machine MKI (premier tier electrique). */
    public static final int MKI = 2;

    private TierChips() {
    }

    /**
     * 1.0.6 : rendement d'une recette A RENDEMENT (matiere premiere, {@link GeneratedTierFamilies#YIELD_RECIPES}) de tier minimal {@code m} :
     * « x1 -> x3,5 » (du tier minimal au MKIV), detail au survol ; null pour les autres (la pastille « tiers permis » est retiree : demande de
     * Guillaume du 16/11/2026, « le filigrane suffit »).
     */
    @Nullable
    public static JeiDraw.Chip yieldOf(int m, @Nullable ResourceLocation id, List<Counted> inputs, ItemStack main) {
        if (id == null || !GeneratedTierFamilies.YIELD_RECIPES.contains(id.toString())) {
            return null;
        }
        int lo = clamp(m);
        boolean capped = TierRecipes.capped(inputs, main.isEmpty() ? List.of() : List.of(new TierRecipes.Out(main, 1F)));
        return JeiDraw.Chip.of(JeiDraw.Pic.YIELD, Component.translatable("jei.pioneers.chip.tier_yield", factor(lo, lo, capped), factor(MAX, lo, capped)),
              JeiDraw.GOOD, Component.translatable("jei.pioneers.min_tier.yield_tip", factor(1, lo, capped), factor(2, lo, capped), factor(5, lo, capped)));
    }

    /**
     * Energie et duree d'une recette basse (tier minimal {@code m} &lt;= 1, duree de base {@code baseTime} en ticks) a la machine MKI de sa
     * famille (= {@code IndustryRecipes.convertLow} : duree / 2^(2 - m), puissance du MKI) ; null si la famille n'a pas de machine electrique.
     */
    @Nullable
    public static JeiDraw.Chip electric(@Nullable String family, int m, int baseTime) {
        GeneratedTierFamilies.Family fam = family == null ? null : TierRecipes.family(family);
        if (fam == null || fam.industry() == null) {
            return null;
        }
        int time = Math.max(1, baseTime >> Math.max(0, MKI - m));
        long power = Tiers.power(0);
        String secs = seconds(time);
        return JeiDraw.Chip.of(JeiDraw.Pic.ENERGY, Component.translatable("jei.pioneers.chip.energy", Texts.grouped(power), secs,
                    Texts.grouped(power * time)), JeiDraw.ENERGY,
              Component.translatable("jei.pioneers.tiers.electric", Component.translatable("tier.pioneers.global." + MKI), secs, Texts.energy(power)),
              Component.translatable("jei.pioneers.chip.energy_tip").withStyle(ChatFormatting.GRAY));
    }

    /** Duree sans cout (machine mecanique ou a vapeur d'une famille sans machine electrique). */
    public static JeiDraw.Chip duration(JeiDraw.Pic pic, int ticks, Component tip) {
        return JeiDraw.Chip.of(pic, Component.literal(seconds(ticks)), JeiDraw.LIGHT, tip);
    }

    static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f s", ticks / 20.0);
    }

    private static int clamp(int t) {
        return Math.max(0, Math.min(GeneratedTierFamilies.COLORS.length - 1, t));
    }

    /** Multiplicateur au tier global n d'une recette a rendement de tier m (plafond a T1 : regle 1). */
    static String factor(int n, int m, boolean capped) {
        return MeltingCategories.number(n < m ? 1 : PowerRules.yieldFactor(capped ? Math.min(n, Math.max(1, m)) : n, m));
    }
}
