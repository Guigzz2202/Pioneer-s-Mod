package fr.pioneers.integration.jei;

import com.mojang.datafixers.util.Either;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidStack;

/**
 * 1.0.6 (Guillaume, 17/11/2026 : « dans JEI les fluides et les gaz ont aussi un tooltip indiquant le type : Azote (Gaz), Azote liquide
 * (Fluide) ») : TYPE DE LA RESSOURCE dans l'infobulle JEI d'un fluide. Le nom des gaz est complete par leur propre rendu
 * ({@link GasIngredient}) ; les fluides ont le rendu de JEI : l'infobulle (sans objet) est retouchee quand JEI a un fluide sous la souris
 * (liste des ingredients, favoris, page de recettes) et que sa premiere ligne est le nom de ce fluide. Abonnement pose par
 * {@link PiJeiPlugin#onRuntimeAvailable} : rien ne charge cette classe sans JEI.
 */
final class JeiResourceKind {

    private static IJeiRuntime runtime;
    private static boolean listening;

    private JeiResourceKind() {
    }

    static void start(IJeiRuntime jei) {
        runtime = jei;
        if (!listening) {
            listening = true;
            MinecraftForge.EVENT_BUS.addListener(JeiResourceKind::gather);
        }
    }

    static void stop() {
        runtime = null;
    }

    private static void gather(RenderTooltipEvent.GatherComponents event) {
        IJeiRuntime jei = runtime;
        if (jei == null || !event.getItemStack().isEmpty()) {
            return;
        }
        FluidStack fluid = underMouse(jei);
        if (fluid == null || fluid.isEmpty()) {
            return;
        }
        List<Either<FormattedText, TooltipComponent>> lines = event.getTooltipElements();
        if (lines.isEmpty()) {
            return;
        }
        Component name = fluid.getDisplayName();
        Optional<FormattedText> first = lines.get(0).left();
        if (first.isPresent() && first.get().getString().equals(name.getString())) {
            lines.set(0, Either.left(Component.translatable("jei.pioneers.res.fluid_name", name)));
        }
    }

    /** Fluide que JEI montre sous la souris (liste, favoris, page de recettes), sinon null. */
    private static FluidStack underMouse(IJeiRuntime jei) {
        try {
            FluidStack f = jei.getIngredientListOverlay().getIngredientUnderMouse(ForgeTypes.FLUID_STACK);
            if (f == null) {
                f = jei.getBookmarkOverlay().getIngredientUnderMouse(ForgeTypes.FLUID_STACK);
            }
            if (f == null) {
                f = jei.getRecipesGui().getIngredientUnderMouse(ForgeTypes.FLUID_STACK).orElse(null);
            }
            return f;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
