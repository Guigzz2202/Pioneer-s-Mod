package fr.pioneers.api.machine.resource;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.multiblock.MultiblockLayout;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import java.util.List;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.6 (Guillaume, 17/11/2026 : « fusionner les jauges de gaz et de liquide servant pour les recettes ; les traiter de la meme maniere ; pour
 * chaque ressource indiquer en infobulle s'il s'agit de liquide ou de gaz ; les machines seront smart quand on alimente automatiquement ») :
 * JAUGES DE RESSOURCES des machines de recettes.
 * <ul>
 *     <li>une machine dont les recettes prennent (ou rendent) des liquides ET des gaz a autant de reservoirs de liquide que de gaz
 *     (catalogue : T:industry/io_audit.py) : le reservoir de liquide i et celui de gaz i forment UNE jauge, qui tient soit un liquide, soit un
 *     gaz (jamais les deux) ; le nombre de jauges est celui des ressources de la recette la plus gourmande ;</li>
 *     <li>remplissage automatique (tuyaux, ports, seaux) : une ressource va d'abord a la jauge qui la tient deja ; une jauge vide ne la prend
 *     que si aucune autre jauge ne la tient et, recette verrouillee, si elle en fait partie et qu'il reste assez de jauges vides pour les
 *     autres ressources de la recette qui manquent ;</li>
 *     <li>sorties : un produit liquide ne va jamais dans une jauge qui tient un gaz (et inversement) : {@code RecipeMatch.pairedOutputs}.</li>
 * </ul>
 * Les colonnes a fractions (chaque liquide sort par l'etage de sa hauteur) gardent des sorties separees ; les reservoirs propres (caloporteur,
 * vapeur, stockage, coeurs des reacteurs) ne sont pas des jauges de recette.
 */
public final class ResourceRows {

    private ResourceRows() {
    }

    /** Machines a jauges de recette generiques (pas les puits, pompes, reservoirs, foreuses ni coeurs propres). */
    public static boolean generic(IndustryMachine m) {
        return (m.kind == IndustryMachine.Kind.PROCESS || m.kind == IndustryMachine.Kind.GENERATOR) && !MultiblockLayout.core(m);
    }

    private static final java.util.Map<IndustryMachine, boolean[]> CACHE = new java.util.EnumMap<>(IndustryMachine.class);

    private static boolean[] of(IndustryMachine m) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(m, k -> new boolean[]{generic(k) && paired(k.fluidsIn, k.gasesIn),
                  generic(k) && !k.fractions && paired(k.fluidsOut, k.gasesOut)});
        }
    }

    /** Les entrees de liquide et de gaz de la machine sont des jauges partagees. */
    public static boolean pairedIn(IndustryMachine m) {
        return of(m)[0];
    }

    /** Les sorties de liquide et de gaz de la machine sont des jauges partagees (pas les colonnes a fractions). */
    public static boolean pairedOut(IndustryMachine m) {
        return of(m)[1];
    }

    /** Validateur du reservoir de liquide i d'une machine : jauges partagees, regle de {@link #fluidFits} ; sinon tout liquide. */
    public static boolean fluidOk(IndustryMachine m, List<? extends FluidTank> fluids, List<BasicGasTank> gases, int i, FluidStack stack,
          @Nullable RecipeChoice locked) {
        return !pairedIn(m) || fluidFits(fluids, gases, i, stack, locked);
    }

    /** Validateur du reservoir de gaz i d'une machine : jauges partagees, regle de {@link #gasFits} ; sinon tout gaz. */
    public static boolean gasOk(IndustryMachine m, List<? extends FluidTank> fluids, List<BasicGasTank> gases, int i, GasStack stack,
          @Nullable RecipeChoice locked) {
        return !pairedIn(m) || gasFits(fluids, gases, i, stack, locked);
    }

    public static boolean paired(int fluids, int gases) {
        return fluids > 0 && fluids == gases;
    }

    /** Nombre de jauges d'un cote. */
    public static int gauges(int fluids, int gases, boolean paired) {
        return paired ? fluids : fluids + gases;
    }

    // ------------------------------------------------------------------ remplissage (validateurs des reservoirs d'entree)

    /** Jauge i libre : ni liquide ni gaz. */
    public static boolean empty(List<? extends FluidTank> fluids, List<BasicGasTank> gases, int i) {
        return (i >= fluids.size() || fluids.get(i).isEmpty()) && (i >= gases.size() || gases.get(i).getStack().isEmpty());
    }

    /**
     * Le liquide peut-il entrer dans la jauge i ? Elle le tient deja (la place, c'est le reservoir qui la voit), ou elle est vide, aucune
     * autre jauge ne le tient, et (recette verrouillee) il reste assez de jauges vides pour les autres ressources de la recette qui manquent.
     */
    public static boolean fluidFits(List<? extends FluidTank> fluids, List<BasicGasTank> gases, int i, FluidStack stack, @Nullable RecipeChoice locked) {
        if (i < gases.size() && !gases.get(i).getStack().isEmpty()) {
            return false;
        }
        FluidTank tank = fluids.get(i);
        if (!tank.isEmpty()) {
            return tank.getFluid().isFluidEqual(stack);
        }
        for (int j = 0; j < fluids.size(); j++) {
            if (j != i && !fluids.get(j).isEmpty() && fluids.get(j).getFluid().isFluidEqual(stack)) {
                return false;
            }
        }
        return reserveOk(fluids, gases, i, stack, null, locked);
    }

    /** Meme regle pour un gaz. */
    public static boolean gasFits(List<? extends FluidTank> fluids, List<BasicGasTank> gases, int i, GasStack stack, @Nullable RecipeChoice locked) {
        if (i < fluids.size() && !fluids.get(i).isEmpty()) {
            return false;
        }
        BasicGasTank tank = gases.get(i);
        if (!tank.getStack().isEmpty()) {
            return tank.getStack().isSameGas(stack);
        }
        for (int j = 0; j < gases.size(); j++) {
            if (j != i && !gases.get(j).getStack().isEmpty() && gases.get(j).getStack().isSameGas(stack)) {
                return false;
            }
        }
        return reserveOk(fluids, gases, i, null, stack, locked);
    }

    /** Recette verrouillee : apres avoir pris cette ressource dans la jauge vide i, reste-t-il une jauge vide pour chaque autre qui manque ? */
    private static boolean reserveOk(List<? extends FluidTank> fluids, List<BasicGasTank> gases, int i, @Nullable FluidStack fluid,
          @Nullable GasStack gas, @Nullable RecipeChoice locked) {
        if (locked == null) {
            return true;
        }
        int n = Math.max(fluids.size(), gases.size());
        int free = 0;
        for (int j = 0; j < n; j++) {
            if (j != i && empty(fluids, gases, j)) {
                free++;
            }
        }
        int missing = 0;
        for (IndustrialRecipe.FluidIn in : locked.fluids()) {
            if (fluid != null && in.test(fluid)) {
                continue;
            }
            boolean held = false;
            for (FluidTank t : fluids) {
                held |= !t.isEmpty() && in.test(t.getFluid());
            }
            missing += held ? 0 : 1;
        }
        for (IndustrialRecipe.GasAmount in : locked.gases()) {
            if (gas != null && in.test(gas)) {
                continue;
            }
            boolean held = false;
            for (BasicGasTank t : gases) {
                held |= !t.getStack().isEmpty() && in.test(t.getStack());
            }
            missing += held ? 0 : 1;
        }
        return free >= missing;
    }
}
