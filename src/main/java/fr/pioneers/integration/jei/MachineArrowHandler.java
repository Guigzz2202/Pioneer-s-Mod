package fr.pioneers.integration.jei;

import fr.pioneers.client.gui.core.GuiMachine;
import java.util.Collection;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * 0.16.5 : clic sur la fleche de progression d'une interface de machine (atelier, kit, controleur de multibloc, chaleur, air, vapeur, bronze)
 * : ouvre JEI sur les recettes dont la machine est catalyseur. La fleche change de place selon la vue : sa position est relevee a chaque
 * image ({@link GuiMachine#jeiArrow}).
 */
public class MachineArrowHandler implements IGuiContainerHandler<GuiMachine> {

    /** API Machine (lot 2) : carre du selecteur et onglets, hors du panneau : JEI n'y pose pas sa liste d'objets. */
    @NotNull
    @Override
    public List<Rect2i> getGuiExtraAreas(@NotNull GuiMachine screen) {
        return screen.extraAreas();
    }

    @NotNull
    @Override
    public Collection<IGuiClickableArea> getGuiClickableAreas(@NotNull GuiMachine screen, double mouseX, double mouseY) {
        Rect2i r = GuiMachine.jeiArrow;
        Block block = screen.machineBlock();
        if (r == null || block == null || block.asItem() == Items.AIR) {
            return List.of();
        }
        ItemStack stack = new ItemStack(block);
        Rect2i area = new Rect2i(r.getX(), r.getY(), r.getWidth(), r.getHeight());
        return List.of(new IGuiClickableArea() {
            @NotNull
            @Override
            public Rect2i getArea() {
                return area;
            }

            @NotNull
            @Override
            public List<Component> getTooltipStrings() {
                return List.of(Component.translatable("jei.pioneers.show_recipes"));
            }

            @Override
            public void onClick(@NotNull IFocusFactory focusFactory, @NotNull IRecipesGui recipesGui) {
                recipesGui.show(focusFactory.createFocus(RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK, stack));
            }
        });
    }
}
