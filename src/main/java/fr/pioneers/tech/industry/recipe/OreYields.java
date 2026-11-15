package fr.pioneers.tech.industry.recipe;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.industry.IndustryMachine;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

/**
 * 1.0.3-beta (lot 3, regle 1 de Guillaume du 05/11/2026) : RENDEMENTS DU MINERAI des metaux de tier 1, en lingots par minerai brut, calcules
 * sur les VRAIES recettes chargees (rien n'est recopie) : JEI (page « Rendements du minerai »), GameTests. Voies (lignes) :
 * <ol start="0">
 *   <li>minerai brut -> four electrique ;</li>
 *   <li>minerai brut -> creuset electrique (multiplicateur du tier) -> coulee ;</li>
 *   <li>broyeur (2 concasses) -> four electrique ;</li>
 *   <li>broyeur -> creuset electrique -> coulee ;</li>
 *   <li>broyeur -> cuve de lixiviation (acide, des le MK2) -> cellule d'electrolyse -> poudre -> four electrique.</li>
 * </ol>
 * Colonnes : tier de la machine (0 MKI .. 3 MK4) ; {@code NaN} : la voie n'existe pas a ce tier (lixiviation au MKI).
 */
public final class OreYields {

    public static final List<String> METALS = List.of("iron", "copper", "tin", "lead", "zinc", "gold");
    public static final int ROWS = 5, TIERS = 4;
    public static final int INGOT_MB = 144;

    private OreYields() {
    }

    /** Tableau [voie][tier] (lingots par minerai brut) du metal. */
    public static double[][] table(Level level, String metal) {
        double[][] t = new double[ROWS][TIERS];
        IndustrialRecipe smeltRaw = byPath(level, IndustryMachine.FURNACE, "industry/furnace/smelt_raw_" + metal);
        IndustrialRecipe smeltCrushed = byPath(level, IndustryMachine.FURNACE, "industry/furnace/smelt_crushed_" + metal);
        IndustrialRecipe smeltDust = byPath(level, IndustryMachine.FURNACE, "industry/furnace/smelt_dust_" + metal);
        IndustrialRecipe meltRaw = byPath(level, IndustryMachine.ELECTRIC_CRUCIBLE, "industry/electric_crucible/melt_raw_" + metal);
        IndustrialRecipe meltCrushed = byPath(level, IndustryMachine.ELECTRIC_CRUCIBLE, "industry/electric_crucible/melt_crushed_" + metal);
        ItemStack crushed = stack("crushed_" + metal);
        //1.0.5-beta (lot 38) : la recette que le broyeur MKI fait vraiment (recette basse convertie du concasseur a machoires : 3 concasses,
        //plafond a T1 de la regle 1 ; la recette du kit « crushing/raw_<metal> » a 2 est retiree comme doublon)
        IndustrialRecipe crush = crushOf(level, metal, crushed);
        String solution = Pioneers.MODID + ":" + metal + "_acid_solution";
        IndustrialRecipe leach = null, electro = null;
        //1.0.4-beta (lot 13) : la cuve de lixiviation est fondue dans l'extracteur (famille leaching_tank)
        for (IndustrialRecipe r : IndustryRecipes.of(level, IndustryMachine.EXTRACTOR)) {
            if ("leaching_tank".equals(r.family()) && r.items().size() == 1 && r.items().get(0).ingredient().test(crushed) && !r.fluidResults().isEmpty()
                && solution.equals(id(r.fluidResults().get(0)))) {
                leach = r;
                break;
            }
        }
        for (IndustrialRecipe r : IndustryRecipes.of(level, IndustryMachine.ELECTROLYZER)) {
            if (r.fluids().size() == 1 && !r.results().isEmpty() && r.fluids().get(0).displayed().stream().anyMatch(f -> solution.equals(id(f)))) {
                electro = r;
                break;
            }
        }
        for (int tier = 0; tier < TIERS; tier++) {
            double c = crush == null ? Double.NaN : crush.results().get(0).stack().getCount() * crush.tierYield(tier) / (double) crush.items().get(0).count();
            t[0][tier] = items(smeltRaw, tier);
            t[1][tier] = melt(meltRaw, tier);
            t[2][tier] = c * items(smeltCrushed, tier);
            t[3][tier] = c * melt(meltCrushed, tier);
            if (leach == null || electro == null || smeltDust == null || tier < leach.tier() || tier < electro.tier()) {
                t[4][tier] = Double.NaN;
            } else {
                double perCrushed = leach.yieldedFluid(tier) / (double) leach.items().get(0).count();
                double dustPerMb = electro.results().get(0).stack().getCount() / (double) electro.fluids().get(0).amount();
                t[4][tier] = c * perCrushed * dustPerMb * items(smeltDust, tier);
            }
        }
        return t;
    }

    /** 1.0.5-beta (lot 38) : premiere recette du broyeur (ordre du jeu) minerai brut -> concasse du metal, ou null. */
    @Nullable
    public static IndustrialRecipe crushOf(Level level, String metal, ItemStack crushed) {
        Item raw = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
              List.of("iron", "copper", "gold").contains(metal) ? "minecraft" : Pioneers.MODID, "raw_" + metal));
        if (raw == Items.AIR || crushed.isEmpty()) {
            return null;
        }
        ItemStack in = new ItemStack(raw);
        for (IndustrialRecipe r : IndustryRecipes.of(level, IndustryMachine.CRUSHER)) {
            if (r.items().size() == 1 && !r.results().isEmpty() && r.items().get(0).ingredient().test(in) && r.results().get(0).stack().is(crushed.getItem())) {
                return r;
            }
        }
        return null;
    }

    private static double items(@Nullable IndustrialRecipe r, int tier) {
        if (r == null || tier < r.tier() || r.results().isEmpty()) {
            return Double.NaN;
        }
        return r.results().get(0).stack().getCount() * r.tierYield(tier) / r.items().get(0).count();
    }

    private static double melt(@Nullable IndustrialRecipe r, int tier) {
        if (r == null || tier < r.tier() || r.fluidResults().isEmpty()) {
            return Double.NaN;
        }
        return r.yieldedFluid(tier) / (double) INGOT_MB / r.items().get(0).count();
    }

    @Nullable
    public static IndustrialRecipe byPath(Level level, IndustryMachine machine, String path) {
        for (IndustrialRecipe r : IndustryRecipes.of(level, machine)) {
            if (r.getId().getNamespace().equals(Pioneers.MODID) && r.getId().getPath().equals(path)) {
                return r;
            }
        }
        return null;
    }

    private static String id(FluidStack f) {
        return String.valueOf(BuiltInRegistries.FLUID.getKey(f.getFluid()));
    }

    /** Objet du mod ({@code pioneers:<id>}), vide s'il n'existe pas. */
    public static ItemStack stack(String id) {
        Item item = BuiltInRegistries.ITEM.get(Pioneers.rl(id));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
