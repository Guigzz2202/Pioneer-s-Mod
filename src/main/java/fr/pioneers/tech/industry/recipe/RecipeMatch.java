package fr.pioneers.tech.industry.recipe;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.IDataTank;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.tech.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.industry.Impurity;
import fr.pioneers.tech.industry.IoTank;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe.FluidIn;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe.GasAmount;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe.Result;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Moteur commun des machines d'atelier et des multiblocs : combien d'operations d'une recette les entrees permettent (les piles d'un meme
 * objet sont additionnees, quelle que soit leur case), si les produits de {@code k} operations tiennent dans les sorties, puis
 * consommation et production. Les chances des sous-produits sont tirees operation par operation.
 */
public final class RecipeMatch {

    /** Cases et reservoirs d'une machine. */
    public interface Buffers {

        /** 1.9 (MAJ RP) : facteur du nombre de tirages ponderes (Oeil du chiffonnier du proprietaire). */
        default double drawFactor() {
            return 1;
        }

        /** 1.9 (MAJ RP) : tirages en plus par operation (un par tier de la machine au-dessus du MkI). */
        default int extraRolls() {
            return 0;
        }

        /**
         * 1.0.3-beta (lot 3) : tier de la machine (0 MKI .. 3 MK4) pour le rendement par tier des recettes ({@code tier_yield}) ; par defaut le
         * meme nombre que {@link #extraRolls} (un par tier au-dessus du MkI : procede d'atelier, atelier, controleur de multibloc).
         */
        default int yieldTier() {
            return extraRolls();
        }

        List<ItemSlot> itemInputs();

        List<ItemSlot> itemOutputs();

        List<FluidTank> fluidInputs();

        List<FluidTank> fluidOutputs();

        List<BasicGasTank> gasInputs();

        List<BasicGasTank> gasOutputs();

        /** 0.20.34 : reservoirs de donnees (synthetiseur) ; aucun par defaut. */
        default List<? extends IDataTank> dataInputs() {
            return List.of();
        }

        /** Colonne de distillation : le i-eme fluide produit va dans le i-eme reservoir de sortie, et seulement lui. */
        default boolean strictFluidOrder() {
            return false;
        }

        /**
         * 1.0.6 (Guillaume, 17/11/2026 : « fusionner les jauges de gaz et de liquide ») : les reservoirs de sortie de liquide t et de gaz t
         * sont UNE jauge (un seul contenu, liquide ou gaz) : {@link fr.pioneers.api.machine.resource.ResourceRows}.
         */
        default boolean pairedOutputs() {
            return false;
        }

        /** Noms des reservoirs pour les vues et les GameTests (ateliers, controleurs). */
        default List<FluidTank> fluidInTanks() {
            return fluidInputs();
        }

        default List<FluidTank> fluidOutTanks() {
            return fluidOutputs();
        }

        default List<BasicGasTank> gasInTanks() {
            return gasInputs();
        }

        default List<BasicGasTank> gasOutTanks() {
            return gasOutputs();
        }

        /**
         * 1.0.5-beta (lot 44, retour 62) : CASE « OUTIL » de la machine ({@link Remanents}) : l'ingredient outil d'une recette (matrice, forme
         * d'extrusion, catalyseur...) y est lu, pris, et y revient ; vide : la machine n'en a pas (l'outil est alors une entree ordinaire).
         */
        default List<ItemSlot> toolSlots() {
            return List.of();
        }

        /** 1.0.5-beta (lot 44) : outil rendu sans place (case Outil et sorties pleines : multibloc dont la case a change en cours de lot). */
        default void spill(ItemStack stack) {
        }
    }

    /** 1.0.5-beta (lot 44) : l'outil de la recette dans CETTE machine (null : pas d'outil, ou machine sans case Outil). */
    @Nullable
    public static Remanents.Tool tool(IndustrialRecipe recipe, Buffers b) {
        return b.toolSlots().isEmpty() ? null : recipe.tool();
    }

    /** 1.0.5-beta (lot 44) : cases ou se lit l'entree {@code input} : la case Outil pour l'outil, sinon les cases d'entree. */
    private static List<ItemSlot> slotsOf(IndustrialRecipe recipe, Buffers b, int input) {
        Remanents.Tool t = tool(recipe, b);
        return t != null && t.input() == input ? b.toolSlots() : b.itemInputs();
    }

    private RecipeMatch() {
    }

    private static boolean usable(IndustrialRecipe recipe, int input, ItemStack stack) {
        if (stack.isEmpty() || !recipe.items().get(input).ingredient().test(stack)) {
            return false;
        }
        if (recipe.pure() && !Impurity.isPure(stack)) {
            return false;
        }
        return input != 0 || !recipe.impureOnly() || !Impurity.isPure(stack);
    }

    /**
     * 0.20.46 (audit) : partie objets de {@link #supply} sans allocation (cases prises dans un masque de bits) et arretee au premier
     * ingredient qui manque ; meme repartition que {@link #claim} (une case ne sert qu'a une entree, piles d'un meme objet additionnees).
     */
    private static int items(IndustrialRecipe recipe, Buffers b, int limit) {
        List<ItemSlot> slots = b.itemInputs();
        Remanents.Tool tool = tool(recipe, b);
        int n = slots.size();
        if (n > 64) {
            int k = limit;
            List<List<ItemSlot>> claimed = claim(recipe, b);
            for (int i = 0; i < recipe.items().size() && k > 0; i++) {
                int have = 0;
                for (ItemSlot slot : claimed.get(i)) {
                    have += WearTool.units(slot.getStack());
                }
                k = Math.min(k, have / recipe.items().get(i).count());
            }
            return k;
        }
        int k = limit;
        long taken = 0L;
        List<Counted> items = recipe.items();
        for (int i = 0; i < items.size(); i++) {
            ItemStack first = ItemStack.EMPTY;
            int have = 0;
            if (tool != null && tool.input() == i) {
                //1.0.5-beta (lot 44) : l'outil se lit dans la case Outil (jamais dans une case d'entree)
                for (ItemSlot slot : b.toolSlots()) {
                    if (usable(recipe, i, slot.getStack())) {
                        have += WearTool.units(slot.getStack());
                    }
                }
                k = Math.min(k, have / items.get(i).count());
                if (k <= 0) {
                    return 0;
                }
                continue;
            }
            for (int s = 0; s < n; s++) {
                if ((taken & (1L << s)) != 0) {
                    continue;
                }
                ItemStack stack = slots.get(s).getStack();
                //Tests bon marche d'abord (case vide, autre objet que la premiere pile prise), l'ingredient ensuite
                if (stack.isEmpty() || !first.isEmpty() && !ItemStack.isSameItemSameTags(first, stack) || !usable(recipe, i, stack)) {
                    continue;
                }
                if (first.isEmpty()) {
                    first = stack;
                }
                taken |= 1L << s;
                //MAJ Recettes (lot 8a) : un outil d'usure (objectif de gravure) compte en points restants
                have += WearTool.units(stack);
            }
            k = Math.min(k, have / items.get(i).count());
            if (k <= 0) {
                return 0;
            }
        }
        return k;
    }

    /** Cases prises par chaque entree objet (une case ne sert qu'a une entree ; lot 44 : l'outil, la case Outil). */
    private static List<List<ItemSlot>> claim(IndustrialRecipe recipe, Buffers b) {
        List<ItemSlot> slots = b.itemInputs();
        Remanents.Tool tool = tool(recipe, b);
        List<List<ItemSlot>> claimed = new ArrayList<>();
        boolean[] taken = new boolean[slots.size()];
        for (int i = 0; i < recipe.items().size(); i++) {
            List<ItemSlot> mine = new ArrayList<>();
            if (tool != null && tool.input() == i) {
                for (ItemSlot slot : b.toolSlots()) {
                    if (usable(recipe, i, slot.getStack())) {
                        mine.add(slot);
                    }
                }
                claimed.add(mine);
                continue;
            }
            ItemStack first = ItemStack.EMPTY;
            for (int s = 0; s < slots.size(); s++) {
                ItemStack stack = slots.get(s).getStack();
                if (!taken[s] && usable(recipe, i, stack) && (first.isEmpty() || ItemStack.isSameItemSameTags(first, stack))) {
                    if (first.isEmpty()) {
                        first = stack;
                    }
                    taken[s] = true;
                    mine.add(slots.get(s));
                }
            }
            claimed.add(mine);
        }
        return claimed;
    }

    /** Nombre d'operations (au plus {@code limit}) que les entrees permettent ; 0 si la recette ne s'applique pas. */
    public static int supply(IndustrialRecipe recipe, Buffers b, int limit) {
        int k = items(recipe, b, limit);
        if (k <= 0) {
            return 0;
        }
        boolean[] usedTank = new boolean[b.fluidInputs().size()];
        for (FluidIn in : recipe.fluids()) {
            int best = -1;
            for (int t = 0; t < b.fluidInputs().size(); t++) {
                if (!usedTank[t] && in.test(b.fluidInputs().get(t).getFluid())) {
                    best = t;
                    break;
                }
            }
            if (best < 0) {
                return 0;
            }
            usedTank[best] = true;
            k = Math.min(k, b.fluidInputs().get(best).getFluidAmount() / in.amount());
            if (k <= 0) {
                return 0;
            }
        }
        boolean[] usedGas = new boolean[b.gasInputs().size()];
        for (GasAmount in : recipe.gases()) {
            int best = -1;
            for (int t = 0; t < b.gasInputs().size(); t++) {
                if (!usedGas[t] && in.test(b.gasInputs().get(t).getStack())) {
                    best = t;
                    break;
                }
            }
            if (best < 0) {
                return 0;
            }
            usedGas[best] = true;
            k = (int) Math.min(k, b.gasInputs().get(best).getStack().getAmount() / Math.max(1, in.amount()));
            if (k <= 0) {
                return 0;
            }
        }
        for (DataStack in : recipe.data()) {
            long have = 0;
            for (IDataTank t : b.dataInputs()) {
                if (t.getStack().isTypeEqual(in)) {
                    have += t.getStack().getAmount();
                }
            }
            k = (int) Math.min(k, have / Math.max(1, in.getAmount()));
            if (k <= 0) {
                return 0;
            }
        }
        return k;
    }

    /** Les produits de {@code k} operations (sous-produits comptes comme s'ils sortaient tous) tiennent-ils dans les sorties ? */
    public static boolean fits(IndustrialRecipe recipe, Buffers b, int k, int impurity, double bonus) {
        return fits(recipe, b, k, impurity, bonus, false);
    }

    /**
     * 1.0.4-beta (lot 5) : place des produits d'un PRODUCTEUR (generateurs, chaudiere industrielle, {@code api/machine/unit/Surplus}) : les
     * objets doivent rentrer comme d'habitude, mais un fluide ou un gaz n'a besoin que d'un reservoir de son espece (vide ou du meme
     * contenu) : plein, le surplus est perdu ({@link #produceVenting}).
     */
    public static boolean fitsVenting(IndustrialRecipe recipe, Buffers b, int k, int impurity, double bonus) {
        return fits(recipe, b, k, impurity, bonus, true);
    }

    /** Comme {@link #possible}, avec la regle des producteurs ({@link #fitsVenting}). */
    public static int possibleVenting(IndustrialRecipe recipe, Buffers b, int limit, int impurity, double bonus) {
        int k = supply(recipe, b, limit);
        while (k > 0 && !fits(recipe, b, k, impurity, bonus, true)) {
            k = k > 8 ? k * 3 / 4 : k - 1;
        }
        return k;
    }

    private static boolean fits(IndustrialRecipe recipe, Buffers b, int k, int impurity, double bonus, boolean vent) {
        bonus = itemBonus(recipe, b, bonus);
        if (recipe.draws() != null) {
            //Tirage pondere : il faut au moins autant de cases libres que de tirages possibles (prudent)
            int free = 0;
            for (ItemSlot slot : b.itemOutputs()) {
                if (slot.getStack().isEmpty()) {
                    free++;
                }
            }
            int need = (int) Math.ceil((recipe.draws().max() + b.extraRolls()) * k * b.drawFactor()) + recipe.results().size();
            if (free < Math.min(need, b.itemOutputs().size())) {
                return false;
            }
        }
        if (!recipe.results().isEmpty()) {
            List<ItemStack> copies = new ArrayList<>();
            for (ItemSlot slot : b.itemOutputs()) {
                copies.add(slot.getStack().copy());
            }
            //1.0.5-beta (lot 38) : recette basse convertie : rendement du tier sur toutes les sorties ; tirages (chance < 1) en place souple
            //(decision D4 : ils ne bloquent pas ; il faut seulement qu'au moins un tirage ait une place, sinon le surplus d'un tirage est perdu)
            boolean lenient = recipe.converted();
            double others = recipe.yieldAll() ? recipe.tierYield(b.yieldTier()) : 1;
            List<ItemStack> draws = null;
            Remanents.Tool tool = tool(recipe, b);
            for (int i = 0; i < recipe.results().size(); i++) {
                Result r = recipe.results().get(i);
                if (tool != null && tool.result() == i) {
                    //1.0.5-beta (lot 44) : l'outil rendu revient dans la case Outil d'ou il a ete pris : pas de place a trouver aux sorties
                    continue;
                }
                if (lenient && r.chance() < 1F) {
                    if (draws == null) {
                        draws = new ArrayList<>();
                    }
                    draws.add(r.stack());
                    continue;
                }
                //Produit principal : bonus de quantite de la machine (minerais)
                double f = i == 0 ? bonus : others;
                int left = f > 1 ? (int) Math.ceil(r.stack().getCount() * k * f) : r.stack().getCount() * k;
                ItemStack proto = r.stack();
                if (i == 0 && impurity > 0) {
                    proto = Impurity.set(proto.copy(), impurity);
                }
                for (int s = 0; s < copies.size() && left > 0; s++) {
                    ItemStack c = copies.get(s);
                    int max = Math.min(64, proto.getMaxStackSize());
                    if (c.isEmpty()) {
                        int put = Math.min(max, left);
                        copies.set(s, proto.copyWithCount(put));
                        left -= put;
                    } else if (ItemStack.isSameItemSameTags(c, proto)) {
                        int put = Math.min(max - c.getCount(), left);
                        if (put > 0) {
                            c.grow(put);
                            left -= put;
                        }
                    }
                }
                if (left > 0) {
                    return false;
                }
            }
            if (draws != null && !anyPlace(copies, draws)) {
                return false;
            }
        }
        boolean[] used = new boolean[b.fluidOutputs().size()];
        int index = -1;
        for (FluidStack out : recipe.fluidResults()) {
            index++;
            FluidStack want = new FluidStack(out, fluidAmount(recipe, b, index, k));
            boolean ok = false;
            for (int t = 0; t < b.fluidOutputs().size(); t++) {
                FluidTank tank = b.fluidOutputs().get(t);
                if (b.strictFluidOrder() && t != Math.min(index, b.fluidOutputs().size() - 1)) {
                    continue;
                }
                if (!used[t] && fluidSlot(b, t, null) && (vent ? kind(tank, want) : room(tank, want))) {
                    used[t] = true;
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        boolean[] usedGas = new boolean[b.gasOutputs().size()];
        for (GasAmount out : recipe.gasResults()) {
            GasStack want = out.stack();
            if (want.isEmpty()) {
                continue;
            }
            want = want.copyWithAmount(want.getAmount() * k);
            boolean ok = false;
            for (int t = 0; t < b.gasOutputs().size(); t++) {
                BasicGasTank tank = b.gasOutputs().get(t);
                if (!usedGas[t] && gasSlot(b, t, used) && (vent ? gasKind(tank, want) : tank.insert(want, Action.SIMULATE, Automation.INTERNAL).isEmpty())) {
                    usedGas[t] = true;
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** 1.0.5-beta (lot 38) : au moins un des tirages trouve une place (case vide, ou la meme sorte avec de la place). */
    private static boolean anyPlace(List<ItemStack> copies, List<ItemStack> draws) {
        for (ItemStack d : draws) {
            for (ItemStack c : copies) {
                if (c.isEmpty() || ItemStack.isSameItemSameTags(c, d) && c.getCount() < Math.min(64, c.getMaxStackSize())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 1.0.3-beta (lot 3) : bonus du produit principal OBJET = bonus de la machine (minerai) x rendement par tier de la recette (s'il porte sur
     * un objet : la recette produit des objets).
     */
    private static double itemBonus(IndustrialRecipe recipe, Buffers b, double bonus) {
        return recipe.hasTierYield() && !recipe.results().isEmpty() && !recipe.yieldOnFluid() ? bonus * recipe.tierYield(b.yieldTier()) : bonus;
    }

    /** 1.0.3-beta (lot 3) : mB du {@code index}-ieme fluide produit par {@code k} operations (rendement par tier sur le 1er, sans objet produit). */
    public static int fluidAmount(IndustrialRecipe recipe, Buffers b, int index, int k) {
        int base = recipe.fluidResults().get(index).getAmount() * k;
        if (index == 0 && recipe.hasTierYield() && (recipe.results().isEmpty() || recipe.yieldOnFluid())) {
            return (int) Math.floor(base * recipe.tierYield(b.yieldTier()) + 1e-6);
        }
        return base;
    }

    /** 1.0.4-beta (lot 5) : le reservoir est-il de l'espece de {@code want} (vide ou meme fluide), plein ou non ? */
    /** 1.0.6 : le liquide peut aller dans la jauge de sortie t (jauges partagees : le gaz de la meme jauge est vide et pas servi). */
    private static boolean fluidSlot(Buffers b, int t, @Nullable boolean[] usedGas) {
        if (!b.pairedOutputs() || t >= b.gasOutputs().size()) {
            return true;
        }
        return b.gasOutputs().get(t).getStack().isEmpty() && (usedGas == null || t >= usedGas.length || !usedGas[t]);
    }

    /** 1.0.6 : le gaz peut aller dans la jauge de sortie t (jauges partagees : le liquide de la meme jauge est vide et pas servi). */
    private static boolean gasSlot(Buffers b, int t, boolean[] usedFluid) {
        if (!b.pairedOutputs() || t >= b.fluidOutputs().size()) {
            return true;
        }
        return b.fluidOutputs().get(t).isEmpty() && (t >= usedFluid.length || !usedFluid[t]);
    }

    private static boolean kind(FluidTank tank, FluidStack want) {
        return tank.isEmpty() || tank.getFluid().isFluidEqual(want);
    }

    /** 1.0.4-beta (lot 5) : le reservoir de gaz est-il de l'espece de {@code want} (vide et accepte, ou meme gaz) ? */
    private static boolean gasKind(BasicGasTank tank, GasStack want) {
        return tank.getStack().isEmpty() ? tank.isValid(want) : tank.getStack().isSameGas(want);
    }

    /** Le reservoir peut-il recevoir tout {@code want} ? (meme regle pour la verification et la production) */
    private static boolean room(FluidTank tank, FluidStack want) {
        return (tank.isEmpty() || tank.getFluid().isFluidEqual(want)) && tank.getCapacity() - tank.getFluidAmount() >= want.getAmount();
    }

    /** Plus grand nombre d'operations (au plus {@code limit}) que les entrees permettent et dont les produits tiennent. */
    public static int possible(IndustrialRecipe recipe, Buffers b, int limit, int impurity, double bonus) {
        int k = supply(recipe, b, limit);
        while (k > 0 && !fits(recipe, b, k, impurity, bonus)) {
            k = k > 8 ? k * 3 / 4 : k - 1;
        }
        return k;
    }

    /** 1.0.5-beta (lot 21) : la recette donne-t-elle deja le contenant rendu parmi ses produits ? */
    static boolean returnsItself(IndustrialRecipe recipe, ItemStack rest) {
        for (IndustrialRecipe.Result r : recipe.results()) {
            if (r.chance() >= 1F && r.stack().is(rest.getItem())) {
                return true;
            }
        }
        return false;
    }

    /** Consomme les entrees de {@code k} operations (a appeler apres {@link #supply}). */
    public static void consume(IndustrialRecipe recipe, Buffers b, int k) {
        List<List<ItemSlot>> claimed = claim(recipe, b);
        Remanents.Tool own = tool(recipe, b);
        for (int i = 0; i < recipe.items().size(); i++) {
            int left = recipe.items().get(i).count() * k;
            for (ItemSlot slot : claimed.get(i)) {
                if (left <= 0) {
                    break;
                }
                //MAJ Recettes (lot 8a) : un outil d'usure n'est pas consomme, il s'use (et devient sa forme usee au dernier point)
                if (slot.getStack().getItem() instanceof WearTool tool && slot.getCount() == 1) {
                    int use = Math.min(left, tool.usesLeft(slot.getStack()));
                    slot.setStack(tool.wear(slot.getStack(), use));
                    left -= use;
                    if (own != null && own.input() == i) {
                        wornOut(recipe, b, slot);
                    }
                    continue;
                }
                int take = Math.min(left, slot.getCount());
                if (own != null && own.input() == i) {
                    //1.0.5-beta (lot 44) : outils pris dans la case Outil (elle peut garder plusieurs matrices non empilables : sans extract,
                    //qui ne sort jamais plus d'une pile normale a la fois)
                    ItemStack held = slot.getStack();
                    slot.setStack(take >= held.getCount() ? ItemStack.EMPTY : held.copyWithCount(held.getCount() - take));
                    left -= take;
                    continue;
                }
                ItemStack got = slot.extract(take, Action.EXECUTE, Automation.INTERNAL);
                left -= take;
                //Contenant rendu (seau de lave brule : le seau vide reste dans la case) ; 1.0.5-beta (lot 21) : sauf si la recette le rend
                //deja elle-meme (vidage a l'embouteilleuse : le seau vide est son produit ; avant : 2 seaux pour 1)
                if (!got.isEmpty() && got.hasCraftingRemainingItem() && slot.isEmpty() && !returnsItself(recipe, got.getCraftingRemainingItem())) {
                    slot.setStack(got.getCraftingRemainingItem());
                }
            }
        }
        boolean[] usedTank = new boolean[b.fluidInputs().size()];
        for (FluidIn in : recipe.fluids()) {
            for (int t = 0; t < b.fluidInputs().size(); t++) {
                if (!usedTank[t] && in.test(b.fluidInputs().get(t).getFluid())) {
                    usedTank[t] = true;
                    drain(b.fluidInputs().get(t), in.amount() * k);
                    break;
                }
            }
        }
        boolean[] usedGas = new boolean[b.gasInputs().size()];
        for (GasAmount in : recipe.gases()) {
            for (int t = 0; t < b.gasInputs().size(); t++) {
                if (!usedGas[t] && in.test(b.gasInputs().get(t).getStack())) {
                    usedGas[t] = true;
                    b.gasInputs().get(t).extract(in.amount() * k, Action.EXECUTE, Automation.INTERNAL);
                    break;
                }
            }
        }
        for (DataStack in : recipe.data()) {
            long left = in.getAmount() * k;
            for (IDataTank t : b.dataInputs()) {
                if (left > 0 && t.getStack().isTypeEqual(in)) {
                    left -= t.extract(left, Action.EXECUTE, Automation.INTERNAL).getAmount();
                }
            }
        }
    }

    /**
     * Range les produits de {@code k} operations ; {@code impurity} = purete du produit principal ; {@code itemBonus} = multiplicateur de
     * quantite du produit principal (minerais : x2 basique ... x4 ultime, applique par la machine selon la recette).
     */
    public static void produce(IndustrialRecipe recipe, Buffers b, int k, RandomSource random, int impurity, double itemBonus) {
        produce(recipe, b, k, random, impurity, itemBonus, false);
    }

    /** 1.0.4-beta (lot 5) : produits d'un PRODUCTEUR ({@link #fitsVenting}) : fluides et gaz remplis jusqu'au bord, le reste est perdu. */
    public static void produceVenting(IndustrialRecipe recipe, Buffers b, int k, RandomSource random, int impurity, double itemBonus) {
        produce(recipe, b, k, random, impurity, itemBonus, true);
    }

    private static void produce(IndustrialRecipe recipe, Buffers b, int k, RandomSource random, int impurity, double itemBonus, boolean vent) {
        itemBonus = itemBonus(recipe, b, itemBonus);
        IndustrialRecipe.Draws draws = recipe.draws();
        if (draws != null) {
            double f = b.drawFactor();
            for (int n = 0; n < k; n++) {
                double rolls = (draws.min() + random.nextInt(Math.max(1, draws.max() - draws.min() + 1)) + b.extraRolls()) * f;
                int whole = (int) rolls + (random.nextDouble() < rolls - (int) rolls ? 1 : 0);
                for (int d = 0; d < whole; d++) {
                    ItemStack got = draws.roll(random);
                    if (!got.isEmpty()) {
                        insert(b.itemOutputs(), got.copyWithCount(1), got.getCount());
                    }
                }
            }
        }
        Remanents.Tool tool = tool(recipe, b);
        for (int i = 0; i < recipe.results().size(); i++) {
            Result r = recipe.results().get(i);
            int count;
            if (tool != null && tool.result() == i) {
                //1.0.5-beta (lot 44) : l'outil revient dans la case Outil (chance = usure ; ni rendement ni purete : c'est le meme outil)
                giveBack(b, r, k, random, recipe.items().get(tool.input()).count() * k);
                continue;
            }
            if (r.chance() >= 1F) {
                count = r.stack().getCount() * k;
            } else {
                count = 0;
                for (int n = 0; n < k; n++) {
                    if (random.nextFloat() < r.chance()) {
                        count += r.stack().getCount();
                    }
                }
            }
            //1.0.5-beta (lot 38) : recette basse convertie : le rendement du tier porte aussi sur les autres sorties (et leurs tirages)
            double f = i == 0 ? itemBonus : recipe.yieldAll() ? recipe.tierYield(b.yieldTier()) : 1;
            if (f > 1) {
                double extra = count * (f - 1);
                count += (int) extra + (random.nextDouble() < extra - (int) extra ? 1 : 0);
            }
            ItemStack proto = r.stack().copy();
            if (i == 0) {
                Impurity.set(proto, impurity);
            }
            insert(b.itemOutputs(), proto, count);
        }
        boolean[] used = new boolean[b.fluidOutputs().size()];
        int index = -1;
        for (FluidStack out : recipe.fluidResults()) {
            index++;
            FluidStack want = new FluidStack(out, fluidAmount(recipe, b, index, k));
            for (int t = 0; t < b.fluidOutputs().size(); t++) {
                FluidTank tank = b.fluidOutputs().get(t);
                if (b.strictFluidOrder() && t != Math.min(index, b.fluidOutputs().size() - 1)) {
                    continue;
                }
                if (!fluidSlot(b, t, null)) {
                    continue;
                }
                if (!used[t] && room(tank, want)) {
                    used[t] = true;
                    fill(tank, want);
                    break;
                }
                if (vent && !used[t] && kind(tank, want)) {
                    //plein : rempli jusqu'au bord, le surplus est perdu
                    used[t] = true;
                    int room = tank.getCapacity() - tank.getFluidAmount();
                    if (room > 0) {
                        fill(tank, new FluidStack(want, Math.min(room, want.getAmount())));
                    }
                    break;
                }
            }
        }
        boolean[] usedGas = new boolean[b.gasOutputs().size()];
        for (GasAmount out : recipe.gasResults()) {
            GasStack want = out.stack();
            if (want.isEmpty()) {
                continue;
            }
            want = want.copyWithAmount(want.getAmount() * k);
            for (int t = 0; t < b.gasOutputs().size(); t++) {
                if (!gasSlot(b, t, used)) {
                    continue;
                }
                if (!usedGas[t] && b.gasOutputs().get(t).insert(want, Action.SIMULATE, Automation.INTERNAL).isEmpty()) {
                    usedGas[t] = true;
                    b.gasOutputs().get(t).insert(want, Action.EXECUTE, Automation.INTERNAL);
                    break;
                }
                BasicGasTank tank = b.gasOutputs().get(t);
                if (vent && !usedGas[t] && gasKind(tank, want)) {
                    //plein : rempli jusqu'au bord (insert rend le reste), le surplus est perdu
                    usedGas[t] = true;
                    tank.insert(want, Action.EXECUTE, Automation.INTERNAL);
                    break;
                }
            }
        }
    }

    /**
     * 1.0.5-beta (lot 44) : outils rendus par {@code k} operations (chance de la recette tiree a chaque operation, au plus ceux qui ont ete
     * pris) : case Outil d'abord, puis sorties, puis {@link Buffers#spill} (rien ne se perd).
     */
    private static void giveBack(Buffers b, Result r, int k, RandomSource random, int taken) {
        int count;
        if (r.chance() >= 1F) {
            count = r.stack().getCount() * k;
        } else {
            count = 0;
            for (int n = 0; n < k; n++) {
                if (random.nextFloat() < r.chance()) {
                    count += r.stack().getCount();
                }
            }
        }
        count = Math.min(count, taken);
        ItemStack proto = r.stack().copyWithCount(1);
        count = insertCount(b.toolSlots(), proto, count);
        count = insertCount(b.itemOutputs(), proto, count);
        while (count > 0) {
            int part = Math.min(count, proto.getMaxStackSize());
            b.spill(proto.copyWithCount(part));
            count -= part;
        }
    }

    /** 1.0.5-beta (lot 44) : un objectif de gravure use jusqu'au bout (forme usee) quitte la case Outil vers les sorties s'il y a la place. */
    private static void wornOut(IndustrialRecipe recipe, Buffers b, ItemSlot slot) {
        ItemStack now = slot.getStack();
        if (now.isEmpty() || Remanents.isTool(recipe.machine().key, now)) {
            return;
        }
        int left = insertCount(b.itemOutputs(), now.copyWithCount(1), now.getCount());
        slot.setStack(left <= 0 ? ItemStack.EMPTY : now.copyWithCount(left));
    }

    /** Range ce qui tient de {@code count} exemplaires de {@code proto} ; rend ce qui reste. */
    private static int insertCount(List<ItemSlot> slots, ItemStack proto, int count) {
        for (int pass = 0; pass < 2 && count > 0; pass++) {
            for (ItemSlot slot : slots) {
                if (count <= 0) {
                    break;
                }
                ItemStack in = slot.getStack();
                boolean same = !in.isEmpty() && ItemStack.isSameItemSameTags(in, proto);
                if (pass == 0 ? !same : !in.isEmpty()) {
                    continue;
                }
                int put = Math.min(Math.min(64, slot.getLimit(proto)) - in.getCount(), count);
                if (put > 0) {
                    ItemStack rest = slot.insert(proto.copyWithCount(put), Action.EXECUTE, Automation.INTERNAL);
                    count -= put - rest.getCount();
                    if (!rest.isEmpty()) {
                        break;
                    }
                }
            }
        }
        return count;
    }

    /** Range {@code count} exemplaires de {@code proto} dans les cases (le surplus, s'il y en a, est perdu : verifie avant par fits). */
    private static void insert(List<ItemSlot> slots, ItemStack proto, int count) {
        int max = Math.min(64, proto.getMaxStackSize());
        for (ItemSlot slot : slots) {
            if (count <= 0) {
                return;
            }
            ItemStack in = slot.getStack();
            if (!in.isEmpty() && ItemStack.isSameItemSameTags(in, proto)) {
                int put = Math.min(max - in.getCount(), count);
                if (put > 0) {
                    slot.insert(proto.copyWithCount(put), Action.EXECUTE, Automation.INTERNAL);
                    count -= put;
                }
            }
        }
        for (ItemSlot slot : slots) {
            if (count <= 0) {
                return;
            }
            if (slot.isEmpty()) {
                int put = Math.min(max, count);
                slot.insert(proto.copyWithCount(put), Action.EXECUTE, Automation.INTERNAL);
                count -= put;
            }
        }
    }

    private static void drain(FluidTank tank, int amount) {
        if (tank instanceof IoTank io) {
            io.drainInternal(amount, FluidAction.EXECUTE);
        } else {
            tank.drain(amount, FluidAction.EXECUTE);
        }
    }

    private static void fill(FluidTank tank, FluidStack stack) {
        if (tank instanceof IoTank io) {
            io.fillInternal(stack, FluidAction.EXECUTE);
        } else {
            tank.fill(stack, FluidAction.EXECUTE);
        }
    }

    /** Premiere recette applicable (tier suffisant, multibloc si exige), ou null. */
    @Nullable
    public static IndustrialRecipe find(List<IndustrialRecipe> recipes, Buffers b, int machineTier, boolean industrial) {
        for (IndustrialRecipe r : recipes) {
            if (r.tier() <= machineTier && (industrial || !r.industrialOnly()) && firstPresent(r, b) && supply(r, b, 1) > 0) {
                return r;
            }
        }
        return null;
    }

    /**
     * MAJ Recettes (lot 7, selecteur) : avec un verrou ({@code locked} non null), seule la recette verrouillee est essayee ; sans verrou, la
     * regle de {@link #find(List, Buffers, int, boolean)} (la plus complete puis la premiere par identifiant, ordre de
     * {@code IndustryRecipes.of}).
     */
    @Nullable
    public static IndustrialRecipe find(List<IndustrialRecipe> recipes, Buffers b, int machineTier, boolean industrial,
          @Nullable IndustrialRecipe locked) {
        if (locked != null) {
            return locked.tier() <= machineTier && (industrial || !locked.industrialOnly()) && firstPresent(locked, b) && supply(locked, b, 1) > 0
                   ? locked : null;
        }
        return find(recipes, b, machineTier, industrial);
    }

    /** MAJ Recettes (lot 7) : recettes a essayer : la seule recette verrouillee, sinon toutes (dans l'ordre de la machine). */
    public static List<IndustrialRecipe> candidates(List<IndustrialRecipe> recipes, @Nullable IndustrialRecipe locked) {
        return locked != null ? List.of(locked) : recipes;
    }

    /**
     * MAJ Recettes (lot 7) : tier demande (0..3) quand la recette depasse la machine : la recette verrouillee si son tier est au-dessus
     * (meme sans entrees), sinon le plus petit tier des recettes au-dessus qui conviendraient aux entrees presentes ; -1 sinon. Message
     * « Tier insuffisant : MKIII requis » ; rien n'est consomme (une telle recette n'est jamais lancee).
     */
    public static int tierNeeded(List<IndustrialRecipe> recipes, Buffers b, int machineTier, boolean industrial, @Nullable IndustrialRecipe locked) {
        if (locked != null) {
            return locked.tier() > machineTier ? locked.tier() : -1;
        }
        int need = -1;
        for (IndustrialRecipe r : recipes) {
            if (r.tier() > machineTier && (need < 0 || r.tier() < need) && (industrial || !r.industrialOnly()) && firstPresent(r, b)
                && supply(r, b, 1) > 0) {
                need = r.tier();
            }
        }
        return need;
    }

    /** 0.20.17 : tri rapide sans allocation (le premier ingredient d'objet est-il dans une case ?) avant le calcul complet. */
    public static boolean firstPresent(IndustrialRecipe r, Buffers b) {
        if (r.items().isEmpty()) {
            return true;
        }
        Counted first = r.items().get(0);
        for (ItemSlot slot : slotsOf(r, b, 0)) {
            if (!slot.isEmpty() && first.ingredient().test(slot.getStack())) {
                return true;
            }
        }
        return false;
    }

}
