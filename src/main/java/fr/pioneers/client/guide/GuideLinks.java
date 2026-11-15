package fr.pioneers.client.guide;

import java.util.function.BiConsumer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Renvoi d'un objet du guide vers JEI (refonte du guide, 1.0.1-beta). JEI est facultatif : l'integration
 * ({@code integration/jei/PiJeiPlugin.onRuntimeAvailable}) pose ici l'ouverture de ses recettes, et la retire quand JEI s'arrete. Sans
 * JEI, un clic sur une icone du guide ne fait rien et l'infobulle ne propose pas le clic.
 */
public final class GuideLinks {

    /** Ouvre JEI sur un objet : vrai = ses usages, faux = ses recettes. Null sans JEI. */
    @Nullable
    private static BiConsumer<ItemStack, Boolean> jei;

    private GuideLinks() {
    }

    /** Appele par l'integration JEI (null quand son execution s'arrete). */
    public static void setJei(@Nullable BiConsumer<ItemStack, Boolean> handler) {
        jei = handler;
    }

    public static boolean jeiAvailable() {
        return jei != null;
    }

    /** Ouvre les recettes ({@code uses} faux) ou les usages de l'objet dans JEI ; faux si JEI est absent. */
    public static boolean showInJei(ItemStack stack, boolean uses) {
        BiConsumer<ItemStack, Boolean> handler = jei;
        if (handler == null || stack.isEmpty()) {
            return false;
        }
        handler.accept(stack.copyWithCount(1), uses);
        return true;
    }
}
