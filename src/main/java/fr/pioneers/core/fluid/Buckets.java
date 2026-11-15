package fr.pioneers.core.fluid;

import java.util.Optional;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/**
 * 1.0.1 : tout ce qui se remplit ou se vide « d'un seau » passe par ici, jamais par {@code Items.WATER_BUCKET} / {@code Items.BUCKET} en dur.
 * Un « seau » est tout objet qui porte la capacite fluide Forge ({@code FLUID_HANDLER_ITEM}) et prend ou rend 1 000 mB d'un coup : seau
 * vanilla (enveloppe {@code FluidBucketWrapper} de Forge), seau en argile du mod ({@code ClayBucketItem} : liquides froids seulement), seaux
 * des autres mods. Les operations travaillent sur une copie d'une unite (les seaux vides s'empilent) puis rangent le contenant obtenu.
 */
public final class Buckets {

    /** Volume d'un seau (1 000 mB). */
    public static final int VOLUME = FluidType.BUCKET_VOLUME;

    private Buckets() {
    }

    private static Optional<IFluidHandlerItem> handler(ItemStack one) {
        return one.isEmpty() ? Optional.empty() : FluidUtil.getFluidHandler(one).resolve();
    }

    /** Fluide contenu (vide si la pile ne porte pas de fluide). */
    public static FluidStack contents(ItemStack stack) {
        return stack.isEmpty() ? FluidStack.EMPTY : FluidUtil.getFluidContained(stack.copyWithCount(1)).orElse(FluidStack.EMPTY);
    }

    /** Vrai si la pile peut rendre un seau entier (1 000 mB) de ce fluide. */
    public static boolean holds(ItemStack stack, Fluid fluid) {
        return !drained(stack, fluid, true).isEmpty();
    }

    /** Seau d'eau (vanilla, en argile ou d'un autre mod). */
    public static boolean isWaterBucket(ItemStack stack) {
        return holds(stack, Fluids.WATER);
    }

    /** Vrai si la pile (une unite) peut recevoir un seau entier de ce fluide. */
    public static boolean canFill(ItemStack stack, Fluid fluid) {
        return !filled(stack, new FluidStack(fluid, VOLUME), true).isEmpty();
    }

    /** Seau vide capable de prendre de l'eau. */
    public static boolean isEmptyBucket(ItemStack stack) {
        return canFill(stack, Fluids.WATER);
    }

    /**
     * Contenant obtenu apres avoir vide un seau entier de ce fluide d'UNE unite de la pile (seau vanilla vide, seau en argile vide...),
     * ou {@link ItemStack#EMPTY} si c'est impossible. La pile d'origine n'est pas touchee.
     */
    public static ItemStack drained(ItemStack stack, Fluid fluid, boolean simulateOnly) {
        ItemStack one = stack.copyWithCount(1);
        return handler(one).map(h -> {
            FluidStack test = h.drain(new FluidStack(fluid, VOLUME), IFluidHandler.FluidAction.SIMULATE);
            if (test.getAmount() < VOLUME || !test.getFluid().isSame(fluid)) {
                return ItemStack.EMPTY;
            }
            if (simulateOnly) {
                return one;
            }
            h.drain(new FluidStack(fluid, VOLUME), IFluidHandler.FluidAction.EXECUTE);
            return h.getContainer().copy();
        }).orElse(ItemStack.EMPTY);
    }

    /** Contenant obtenu apres avoir rempli UNE unite de la pile d'un seau entier ({@code fluid} : 1 000 mB), ou {@link ItemStack#EMPTY}. */
    public static ItemStack filled(ItemStack stack, FluidStack fluid, boolean simulateOnly) {
        if (fluid.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack one = stack.copyWithCount(1);
        FluidStack bucket = new FluidStack(fluid, VOLUME);
        return handler(one).map(h -> {
            if (h.fill(bucket, IFluidHandler.FluidAction.SIMULATE) < VOLUME) {
                return ItemStack.EMPTY;
            }
            if (simulateOnly) {
                return one;
            }
            h.fill(bucket, IFluidHandler.FluidAction.EXECUTE);
            return h.getContainer().copy();
        }).orElse(ItemStack.EMPTY);
    }

    /** Vide un seau entier du fluide tenu en main (cote serveur) : le joueur garde le contenant vide (creatif : rien ne change). Vrai si fait. */
    public static boolean drainHeld(Player player, InteractionHand hand, Fluid fluid) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack empty = drained(held, fluid, false);
        if (empty.isEmpty()) {
            return false;
        }
        if (!player.getAbilities().instabuild) {
            give(player, hand, held, empty);
        }
        return true;
    }

    /** Remplit d'un seau entier le seau vide tenu en main (cote serveur) ; le joueur recoit le seau plein. Vrai si fait. */
    public static boolean fillHeld(Player player, InteractionHand hand, FluidStack fluid) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack full = filled(held, fluid, false);
        if (full.isEmpty()) {
            return false;
        }
        give(player, hand, held, full);
        return true;
    }

    /** Remplace une unite de la pile tenue par {@code result} (le reste de la pile reste en main, le resultat va dans l'inventaire). */
    private static void give(Player player, InteractionHand hand, ItemStack held, ItemStack result) {
        if (player.getAbilities().instabuild) {
            if (!player.getInventory().contains(result)) {
                player.getInventory().add(result);
            }
            return;
        }
        if (held.getCount() <= 1) {
            player.setItemInHand(hand, result);
            return;
        }
        held.shrink(1);
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
    }
}
