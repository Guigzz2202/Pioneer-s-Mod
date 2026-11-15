package fr.pioneers.farm.kitchen;

import fr.pioneers.farm.GeneratedFarm;
import fr.pioneers.farm.PiFarm;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * 1.0.5-beta (lot 32, retour 41 de Guillaume : « Donne une interface a l'extracteur de miel. On y met les cadres de ruches. Et il produit les
 * 3 ingredients (cire, rayon et miel liquide que l'on recupere via des bouteilles) ») : PROPORTIONS d'un cadre plein et REMPLISSAGE des
 * contenants depuis la cuve, communs a l'extracteur manuel ({@code HoneyExtractorTile}) et a l'extracteur mecanique
 * ({@code KitchenProcess}).
 * <p>
 * Un cadre plein (recette {@code pioneers:extracting}, SOURCE {@code T:core/p105_lot32.py}) : {@link #HONEY_MB} mB de miel, {@link #WAX} cire,
 * un rayon de miel ({@code minecraft:honeycomb}) avec la chance {@link #COMB_CHANCE}, le cadre vide rendu ; {@link #STROKES} tours.
 * Contenants : fiole vide -> bouteille de miel ({@code GeneratedFarm.BOTTLING}, {@link #BOTTLE_MB} mB), seau -> seau du fluide
 * ({@link #BUCKET_MB} mB, le seau du fluide de la cuve : seau de miel).
 */
public final class HoneyFill {

    public static final int HONEY_MB = 250, WAX = 1, STROKES = 6, BOTTLE_MB = 250, BUCKET_MB = 1000;
    public static final float COMB_CHANCE = 0.5F;

    private HoneyFill() {
    }

    /** Contenant vide que l'extracteur sait remplir : une fiole (contenant de la mise en bouteille du miel) ou un seau vide. */
    public static boolean isContainer(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.BUCKET)) {
            return true;
        }
        for (String[] b : GeneratedFarm.BOTTLING) {
            if (b[0].equals("pioneers:honey") && stack.is(PiFarm.item(b[2]))) {
                return true;
            }
        }
        return false;
    }

    /** mB pris par un contenant (0 : ce n'en est pas un). */
    public static int need(ItemStack container) {
        return container.is(Items.BUCKET) ? BUCKET_MB : isContainer(container) ? BOTTLE_MB : 0;
    }

    /** Contenant rempli du fluide (vide si ce contenant ne le prend pas) : bouteille de la mise en bouteille, ou seau du fluide. */
    public static ItemStack filled(ItemStack container, FluidStack fluid) {
        if (container.isEmpty() || fluid.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (container.is(Items.BUCKET)) {
            Item bucket = fluid.getFluid().getBucket();
            return bucket == Items.AIR || bucket == Items.BUCKET ? ItemStack.EMPTY : new ItemStack(bucket);
        }
        String id = String.valueOf(BuiltInRegistries.FLUID.getKey(fluid.getFluid()));
        for (String[] b : GeneratedFarm.BOTTLING) {
            if (b[0].equals(id) && container.is(PiFarm.item(b[2]))) {
                return new ItemStack(PiFarm.item(b[1]));
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Remplit UN contenant de {@code in} depuis la cuve et le pose sur {@code out} : renvoie {nouvelle pile d'entree, nouvelle pile de sortie}
     * (la cuve est videe de ce qu'il faut), ou null si rien n'est possible (pas de contenant, cuve trop basse, sortie pleine ou autre objet).
     */
    public static ItemStack[] fillOne(FluidTank tank, ItemStack in, ItemStack out) {
        int need = need(in);
        if (need <= 0 || tank.getFluidAmount() < need) {
            return null;
        }
        ItemStack product = filled(in, tank.getFluid());
        if (product.isEmpty()) {
            return null;
        }
        if (!out.isEmpty() && (!ItemStack.isSameItemSameTags(out, product) || out.getCount() + product.getCount() > out.getMaxStackSize())) {
            return null;
        }
        tank.drain(need, IFluidHandler.FluidAction.EXECUTE);
        ItemStack newIn = in.copy();
        newIn.shrink(1);
        ItemStack newOut = out.isEmpty() ? product : out.copyWithCount(out.getCount() + product.getCount());
        return new ItemStack[]{newIn, newOut};
    }
}
