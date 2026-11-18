package fr.pioneers.tech.industry;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.tech.energy.EnergyKind;
import fr.pioneers.tech.energy.MicrobialCellTile;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11.2 (lot E2, demande de Guillaume : « JEI ou le tooltip nous indiquent pour chaque generateur et recette du generateur combien de FE on
 * va obtenir au total et combien par tick ») : SOURCE UNIQUE des calculs d'energie des generateurs a recettes. La machine d'atelier et le
 * controleur l'appellent pour produire, JEI et les infobulles pour l'afficher : un seul calcul, aucune constante recopiee.
 * <p>
 * Regle (age industriel) : une recette de generateur donne {@code energy} FE/t pendant {@code time} ticks au tier de base. Chaque tier brule
 * 4 fois plus vite et produit 4 fois plus par tick : l'energie totale d'un combustible ne depend pas du tier. Multibloc : x1,25
 * ({@link Tiers#INDUSTRIAL_YIELD}) et jusqu'a {@link Tiers#parallelCap} operations a la fois. Combustible solide : la recette
 * {@link IndustryRecipes#fuel} (duree de combustion vanilla, {@link Tiers#BASE_POWER} FE/t).
 * <p>
 * MAJ simplification (03/11/2026, Guillaume : « generateurs a combustion : rendement x2 a tous les tiers ») : le generateur a combustion a
 * un rendement de {@link #COMBUSTION_YIELD} a tous les tiers (atelier MKI a MKIV : x2), compte comme le rendement multibloc : FE/t et FE au
 * total doubles, meme duree (meme combustible brule par seconde). Le multibloc de combustion prend le meme rendement (x1,25 -> x2, soit
 * x1,6) au lieu de cumuler les deux : par mB de gaz il reste sous la turbine a gaz et le cycle combine (audit F4, GameTest
 * {@code cycle_combine_rendement}) ; il garde son avantage de puissance (operations en parallele). Toutes les productions, JEI et infobulles
 * passent par ici.
 */
public final class GeneratorOutput {

    private GeneratorOutput() {
    }

    /** Multiplicateur de cadence du tier (1, 4, 16, 64). */
    public static int rate(int tier) {
        return 1 << (2 * Math.max(0, Math.min(3, tier)));
    }

    /** Duree d'une operation (ticks) dans un generateur du tier. */
    public static int duration(int time, int tier) {
        return (int) Math.max(1, Math.round(time / (double) rate(tier)));
    }

    /** MAJ simplification : rendement du generateur a combustion (x2, tous les tiers, atelier et multibloc). */
    public static final double COMBUSTION_YIELD = 2.0;

    /** Rendement : 1 en machine d'atelier, x1,25 en multibloc. */
    public static double yieldFactor(boolean industrial) {
        return industrial ? Tiers.INDUSTRIAL_YIELD : 1.0;
    }

    /**
     * 1.0.3-beta (lot 5, generateurs separes) : generateur a combustion (solide ou liquide) : rendement {@link #COMBUSTION_YIELD} ; la turbine a
     * gaz (compacts depuis ce lot) garde le rendement general (x1, multibloc x1,25).
     */
    public static boolean combustion(@Nullable IndustryMachine machine) {
        return machine == IndustryMachine.SOLID_COMBUSTION_GENERATOR || machine == IndustryMachine.LIQUID_COMBUSTION_GENERATOR;
    }

    /** Rendement propre a la machine d'atelier (generateurs a combustion : {@link #COMBUSTION_YIELD} ; les autres : 1). */
    public static double machineYield(@Nullable IndustryMachine machine) {
        return combustion(machine) ? COMBUSTION_YIELD : 1.0;
    }

    /** Rendement d'une recette : celui de la combustion s'il y en a un (atelier et multibloc), sinon celui du multibloc. */
    public static double recipeYield(IndustrialRecipe r, boolean industrial) {
        return combustion(r.machine()) ? COMBUSTION_YIELD : yieldFactor(industrial);
    }

    /** Energie totale d'une operation (FE), la meme a tous les tiers. */
    public static long total(long energy, int time, boolean industrial) {
        return Math.round(energy * (double) time * yieldFactor(industrial));
    }

    public static long total(IndustrialRecipe r, boolean industrial) {
        return Math.round(r.energy() * (double) r.time() * recipeYield(r, industrial));
    }

    /**
     * Puissance exacte d'une operation (FE/t) dans un generateur du tier : l'energie totale repartie sur la duree arrondie (rien ne se perd a
     * l'arrondi). Le multibloc multiplie par le nombre d'operations en parallele avant d'arrondir.
     */
    public static double exactPerTick(long energy, int time, int tier, boolean industrial) {
        return energy * (double) time * yieldFactor(industrial) / duration(time, tier);
    }

    public static long perTick(long energy, int time, int tier, boolean industrial) {
        return Math.round(exactPerTick(energy, time, tier, industrial));
    }

    /** Puissance exacte d'une operation d'une recette (rendement de sa machine compris). */
    public static double exactPerTick(IndustrialRecipe r, int tier, boolean industrial) {
        return r.energy() * (double) r.time() * recipeYield(r, industrial) / duration(r.time(), tier);
    }

    public static long perTick(IndustrialRecipe r, int tier, boolean industrial) {
        return Math.round(exactPerTick(r, tier, industrial));
    }

    /** Puissance nominale du generateur a combustion d'un tier avec un combustible solide (FE/t, rendement x2 compris). */
    public static long solidPerTick(int tier) {
        return Math.round(Tiers.BASE_POWER * rate(tier) * COMBUSTION_YIELD);
    }

    /** Recette de combustion d'un combustible solide (null : ne brule pas). */
    @Nullable
    public static IndustrialRecipe solidFuel(ItemStack stack) {
        return IndustryRecipes.fuel(stack);
    }

    /** Pile microbienne : energie totale d'un objet organique au tier (FE, 0 : refuse). */
    public static long microbialTotal(ItemStack stack, int tier) {
        return Math.round(MicrobialCellTile.units(stack) * EnergyKind.MICROBIAL_UNIT[Math.max(0, Math.min(3, tier))]);
    }

    /** Pile microbienne : puissance au tier (FE/t), celle de sa production maximale. */
    public static long microbialPerTick(int tier) {
        return EnergyKind.MICROBIAL.peak[Math.max(0, Math.min(3, tier))];
    }

    /** Energie par mB de gaz ou de fluide consomme par une recette (FE/mB ; 0 si la recette n'en prend pas). */
    public static double perUnit(IndustrialRecipe r, long amount, boolean industrial) {
        return amount <= 0 ? 0 : total(r, industrial) / (double) amount;
    }

    /** Usage d'un combustible fluide ou gazeux : le generateur, sa recette, la quantite consommee par operation (mB). */
    public record Use(IndustryMachine machine, IndustrialRecipe recipe, long amount) {

        /** Multibloc seulement (turbines...) : rendement x1,25 compte. */
        public boolean industrial() {
            return machine.atelier == IndustryMachine.Atelier.NONE;
        }

        /** Energie totale de {@code mb} mB du combustible (FE). */
        public long totalFor(long mb) {
            return Math.round(total(recipe, industrial()) * (double) mb / Math.max(1, amount));
        }

        /** Puissance de la recette au tier (FE/t). */
        public long perTick(int tier) {
            return GeneratorOutput.perTick(recipe, tier, industrial());
        }
    }

    /**
     * Generateur qui brule ce fluide (null : aucun). Un generateur qui a une machine d'atelier d'abord (1.0.3-beta lot 5 : le generateur a
     * combustion liquide), sinon le premier multibloc generateur qui le prend.
     */
    @Nullable
    public static Use fluid(Level level, FluidStack fluid) {
        if (fluid.isEmpty()) {
            return null;
        }
        Use found = null;
        for (IndustryMachine m : IndustryMachine.values()) {
            if (!m.isGenerator()) {
                continue;
            }
            for (IndustrialRecipe r : IndustryRecipes.of(level, m)) {
                if (r.energy() <= 0) {
                    continue;
                }
                for (IndustrialRecipe.FluidIn in : r.fluids()) {
                    if (in.test(fluid) && (found == null || compactFirst(m, found.machine()))) {
                        found = new Use(m, r, in.amount());
                    }
                }
            }
        }
        return found;
    }

    /** 1.0.3-beta (lot 5) : {@code m} passe avant le generateur deja trouve s'il a une machine d'atelier et l'autre non. */
    private static boolean compactFirst(IndustryMachine m, IndustryMachine found) {
        return m != found && m.atelier == IndustryMachine.Atelier.NEW && found.atelier != IndustryMachine.Atelier.NEW;
    }

    /** Generateur qui brule ce gaz (null : aucun), meme ordre que {@link #fluid} (la turbine a gaz, compacte depuis le lot 5). */
    @Nullable
    public static Use gas(Level level, GasStack gas) {
        if (gas.isEmpty()) {
            return null;
        }
        Use found = null;
        for (IndustryMachine m : IndustryMachine.values()) {
            if (!m.isGenerator()) {
                continue;
            }
            for (IndustrialRecipe r : IndustryRecipes.of(level, m)) {
                if (r.energy() <= 0) {
                    continue;
                }
                for (IndustrialRecipe.GasAmount in : r.gases()) {
                    if (in.test(gas) && (found == null || compactFirst(m, found.machine()))) {
                        found = new Use(m, r, in.amount());
                    }
                }
            }
        }
        return found;
    }

    /** Puissance la plus forte d'une operation d'un generateur multibloc au tier (FE/t ; 0 : pas de recette d'energie). */
    public static long bestPerTick(Level level, IndustryMachine machine, int tier, boolean industrial) {
        long best = 0;
        for (IndustrialRecipe r : IndustryRecipes.of(level, machine)) {
            if (r.energy() > 0) {
                best = Math.max(best, perTick(r, tier, industrial));
            }
        }
        return best;
    }
}
