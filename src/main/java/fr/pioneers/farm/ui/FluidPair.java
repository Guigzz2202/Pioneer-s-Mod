package fr.pioneers.farm.ui;

import fr.pioneers.farm.GeneratedFarm;
import fr.pioneers.farm.PiFarm;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 41, retour 60) : paires de cases des cuves des postes et appareils de la ferme, a la place du seau ou de la bouteille au clic
 * droit : « Remplir » (contenant vide -> plein depuis une cuve : seau du fluide 1 000 mB, contenant de la mise en bouteille
 * {@code GeneratedFarm.BOTTLING} 250 mB, tout autre recipient a capacite de fluide) et « Vider » (contenant plein -> cuve ; le contenant vide
 * est rendu dans la case du dessous). Une operation = UN contenant ; la tile repete tant que c'est possible, au changement du contenu
 * seulement (aucun travail par tick). Memes regles que l'extracteur de miel du lot 32 ({@code HoneyFill}) pour la fiole et le seau.
 */
public final class FluidPair {

    public static final int BOTTLE_MB = 250, BUCKET_MB = 1000;

    private FluidPair() {
    }

    /** Ligne de mise en bouteille {fluide, objet plein, contenant} dont le contenant est cet objet et le fluide celui-ci (null : aucune). */
    @Nullable
    private static String[] bottling(ItemStack container, @Nullable FluidStack fluid) {
        for (String[] b : GeneratedFarm.BOTTLING) {
            if ((fluid == null || b[0].equals(id(fluid))) && container.is(PiFarm.item(b[2]))) {
                return b;
            }
        }
        return null;
    }

    private static String id(FluidStack fluid) {
        return String.valueOf(BuiltInRegistries.FLUID.getKey(fluid.getFluid()));
    }

    /** Objet que la case « Remplir » accepte : seau vide, contenant de la mise en bouteille, recipient vide a capacite de fluide. */
    public static boolean fillable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.BUCKET) || bottling(stack, null) != null) {
            return true;
        }
        return FluidUtil.getFluidHandler(stack.copyWithCount(1)).isPresent() && FluidUtil.getFluidContained(stack.copyWithCount(1)).isEmpty();
    }

    /** Objet que la case « Vider » accepte : un contenant PLEIN (seau d'eau, de lait, bouteille de jus...). */
    public static boolean drainable(ItemStack stack) {
        return !contained(stack).isEmpty();
    }

    /** Fluide contenu (bouteille de la mise en bouteille : 250 mB de son fluide), vide sinon. */
    public static FluidStack contained(ItemStack stack) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        for (String[] b : GeneratedFarm.BOTTLING) {
            if (stack.is(PiFarm.item(b[1]))) {
                net.minecraft.world.level.material.Fluid f = BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse(b[0]));
                return f == null ? FluidStack.EMPTY : new FluidStack(f, BOTTLE_MB);
            }
        }
        return FluidUtil.getFluidContained(stack.copyWithCount(1)).orElse(FluidStack.EMPTY);
    }

    /** Peut-on poser {@code product} sur la case de sortie {@code out} ? */
    private static boolean fits(ItemStack out, ItemStack product) {
        return out.isEmpty() || ItemStack.isSameItemSameTags(out, product) && out.getCount() + product.getCount() <= out.getMaxStackSize();
    }

    private static ItemStack[] moved(ItemStack in, ItemStack out, ItemStack product) {
        ItemStack newIn = in.copy();
        newIn.shrink(1);
        ItemStack newOut = out.isEmpty() ? product.copy() : out.copyWithCount(out.getCount() + product.getCount());
        return new ItemStack[]{newIn, newOut};
    }

    /**
     * Remplit UN contenant de {@code in} depuis {@code tank} et le pose sur {@code out} : {nouvelle entree, nouvelle sortie} (la cuve est
     * videe de ce qu'il faut), ou null si rien n'est possible.
     */
    @Nullable
    public static ItemStack[] fillOne(@Nullable FluidTank tank, ItemStack in, ItemStack out) {
        if (tank == null || tank.isEmpty() || in.isEmpty()) {
            return null;
        }
        FluidStack fluid = tank.getFluid();
        if (in.is(Items.BUCKET)) {
            Item bucket = fluid.getFluid().getBucket();
            if (bucket == Items.AIR || bucket == Items.BUCKET || tank.getFluidAmount() < BUCKET_MB) {
                return null;
            }
            ItemStack product = new ItemStack(bucket);
            if (!fits(out, product)) {
                return null;
            }
            tank.drain(BUCKET_MB, IFluidHandler.FluidAction.EXECUTE);
            return moved(in, out, product);
        }
        String[] b = bottling(in, fluid);
        if (b != null) {
            ItemStack product = new ItemStack(PiFarm.item(b[1]));
            if (tank.getFluidAmount() < BOTTLE_MB || !fits(out, product)) {
                return null;
            }
            tank.drain(BOTTLE_MB, IFluidHandler.FluidAction.EXECUTE);
            return moved(in, out, product);
        }
        //Autre recipient a capacite de fluide (vide) : rempli au maximum qu'il prend
        ItemStack one = in.copyWithCount(1);
        FluidActionResult sim = FluidUtil.tryFillContainer(one, tank, Integer.MAX_VALUE, null, false);
        if (!sim.isSuccess() || !fits(out, sim.getResult())) {
            return null;
        }
        FluidActionResult done = FluidUtil.tryFillContainer(in.copyWithCount(1), tank, Integer.MAX_VALUE, null, true);
        return done.isSuccess() ? moved(in, out, done.getResult()) : null;
    }

    /**
     * Vide UN contenant plein de {@code in} dans {@code tank} (tout son contenu, sinon rien) et pose le contenant vide sur {@code out} :
     * {nouvelle entree, nouvelle sortie}, ou null si rien n'est possible (cuve pleine, fluide refuse, sortie occupee).
     */
    @Nullable
    public static ItemStack[] drainOne(@Nullable FluidTank tank, ItemStack in, ItemStack out) {
        if (tank == null || in.isEmpty()) {
            return null;
        }
        FluidStack fluid = contained(in);
        if (fluid.isEmpty() || tank.fill(fluid, IFluidHandler.FluidAction.SIMULATE) < fluid.getAmount()) {
            return null;
        }
        ItemStack empty = emptied(in);
        if (!empty.isEmpty() && !fits(out, empty)) {
            return null;
        }
        tank.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        ItemStack newIn = in.copy();
        newIn.shrink(1);
        ItemStack newOut = empty.isEmpty() ? out : out.isEmpty() ? empty : out.copyWithCount(out.getCount() + empty.getCount());
        return new ItemStack[]{newIn, newOut};
    }

    /** Contenant vide rendu apres avoir verse {@code full} (seau vide, contenant de la mise en bouteille, reste du recipient). */
    public static ItemStack emptied(ItemStack full) {
        for (String[] b : GeneratedFarm.BOTTLING) {
            if (full.is(PiFarm.item(b[1]))) {
                return new ItemStack(PiFarm.item(b[2]));
            }
        }
        ItemStack one = full.copyWithCount(1);
        if (one.hasCraftingRemainingItem()) {
            return one.getCraftingRemainingItem();
        }
        FluidActionResult r = FluidUtil.tryEmptyContainer(one, new FluidTank(Integer.MAX_VALUE), Integer.MAX_VALUE, null, true);
        return r.isSuccess() ? r.getResult() : ItemStack.EMPTY;
    }

    /** Repete {@link #fillOne} (au plus 64 fois) : vrai si au moins un contenant a ete rempli ; {@code set} recoit les nouvelles piles. */
    public static boolean fillAll(@Nullable FluidTank tank, ItemStack[] pair) {
        boolean any = false;
        for (int k = 0; k < 64; k++) {
            ItemStack[] r = fillOne(tank, pair[0], pair[1]);
            if (r == null) {
                break;
            }
            pair[0] = r[0];
            pair[1] = r[1];
            any = true;
        }
        return any;
    }

    /** Repete {@link #drainOne} (au plus 64 fois). */
    public static boolean drainAll(@Nullable FluidTank tank, ItemStack[] pair) {
        boolean any = false;
        for (int k = 0; k < 64; k++) {
            ItemStack[] r = drainOne(tank, pair[0], pair[1]);
            if (r == null) {
                break;
            }
            pair[0] = r[0];
            pair[1] = r[1];
            any = true;
        }
        return any;
    }
}
