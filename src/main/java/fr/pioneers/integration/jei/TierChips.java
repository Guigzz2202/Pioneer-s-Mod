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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 38, retour 49, specification de l'age du bronze §2) : pastilles de tier des pages JEI.
 * <p>
 * 1.0.6 (demande de Guillaume du 16/11/2026 : « indiquer clairement quels tiers la recette permet » et « afficher le cout en energie et la
 * duree du plus bas tier, autre que mecanique et vapeur si possible ») :
 * <ul>
 *     <li>{@link #tiers} : pastille « T2 a T5 » (couleur du plus bas tier) : les tiers de machine qui font la recette ; au survol, un tier par
 *     ligne avec les machines de la famille, la regle de vitesse et, pour une recette a rendement, les rendements ;</li>
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
     * Pastille « Tn a Tm » (« Tn seulement » si min = max) : tiers de machine qui font la recette. {@code family} : famille de tiers (cle de
     * {@link GeneratedTierFamilies#FAMILIES}) dont les machines sont listees au survol, ou null ; {@code id}, {@code inputs}, {@code main} :
     * rendements d'une recette a rendement (null : aucun).
     */
    public static JeiDraw.Chip tiers(int min, int max, @Nullable String family, @Nullable ResourceLocation id, List<Counted> inputs, ItemStack main) {
        int lo = clamp(min), hi = Math.max(lo, clamp(max));
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("jei.pioneers.tiers.tip"));
        GeneratedTierFamilies.Family fam = family == null ? null : TierRecipes.family(family);
        for (int t = lo; t <= hi; t++) {
            MutableComponent line = Component.translatable("tier.pioneers.global." + t).copy();
            List<Component> names = fam == null ? List.of() : machines(fam, t);
            if (!names.isEmpty()) {
                MutableComponent list = Component.empty();
                for (int i = 0; i < names.size(); i++) {
                    list.append(i == 0 ? Component.empty() : Component.literal(", ")).append(names.get(i));
                }
                line = Component.translatable("jei.pioneers.tiers.line", line, list);
            }
            tip.add(line.withStyle(ChatFormatting.GRAY));
        }
        if (hi > lo) {
            tip.add(Component.translatable("jei.pioneers.tiers.speed").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (id != null && GeneratedTierFamilies.YIELD_RECIPES.contains(id.toString())) {
            boolean capped = TierRecipes.capped(inputs, main.isEmpty() ? List.of() : List.of(new TierRecipes.Out(main, 1F)));
            tip.add(Component.translatable("jei.pioneers.min_tier.yield_tip", factor(1, lo, capped), factor(2, lo, capped), factor(5, lo, capped)));
        }
        Component text = lo == hi ? Component.translatable("jei.pioneers.chip.tier_only", lo) : Component.translatable("jei.pioneers.chip.tiers", lo, hi);
        return new JeiDraw.Chip(JeiDraw.Pic.INFO, text, 0xFF000000 | GeneratedTierFamilies.COLORS[lo], tip);
    }

    /** Ancienne pastille « Tier min. m » : remplacee par {@link #tiers} (m a T5, machines de la famille au survol). */
    public static JeiDraw.Chip minTier(int m, ResourceLocation id, List<Counted> inputs, ItemStack main, @Nullable String family) {
        return tiers(m, family != null && TierRecipes.family(family) != null && TierRecipes.family(family).industry() != null ? MAX : Math.max(m, 1),
              family, id, inputs, main);
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

    /** Noms des machines de la famille au tier global {@code t} (multiblocs : tier de leurs carcasses, montres a partir du MKI). */
    static List<Component> machines(GeneratedTierFamilies.Family fam, int t) {
        List<Component> out = new ArrayList<>();
        for (String block : fam.blocks()) {
            int bt = GeneratedTierFamilies.TIER_OF.getOrDefault(block, -2);
            if (bt == t || (bt == -1 && t == MKI)) {
                out.add(Component.translatable("block.pioneers." + block));
            }
        }
        return out;
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
