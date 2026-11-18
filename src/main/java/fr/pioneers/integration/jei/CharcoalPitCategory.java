package fr.pioneers.integration.jei;

import fr.pioneers.Pioneers;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.tech.mechanics.PiMechanics;
import fr.pioneers.core.registries.PiFluids;
import fr.pioneers.tech.forge.PiForge;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

/**
 * 1.4 : categorie JEI « Meule de charbonnier » : ce qu'il faut (buches, couverture, briquet), ce qui sort (charbon de bois, creosote au drain,
 * cendre des fuites), schema en coupe numerote ({@code textures/gui/jei/charcoal_pit.png}, {@code T:jei_diagrams/charcoal_pit.py}) et les
 * etapes qui reprennent les numeros du schema.
 */
class CharcoalPitCategory extends PiCategory<CharcoalPitCategory.Entry> {

    static final int W = 176, H = 166, SLOT = 18, TOP = 0, PIC = TOP + SLOT + 2, PIC_H = 88;
    private static final ResourceLocation DIAGRAM = Pioneers.rl("textures/gui/jei/charcoal_pit.png");

    record Entry() {
    }

    CharcoalPitCategory(IGuiHelper helper, RecipeType<Entry> type) {
        super(helper, type, "jei.pioneers.charcoal_pit", PiMechanics.COLLIER_POLE.get(), W, H);
        family(JeiDraw.Family.PRIMITIVE);
    }

    /** 1.11.3 : le titre passe dans l'en-tete de la charte. */
    @Override
    protected Component headerTitle(Entry entry) {
        return Component.translatable("jei.pioneers.charcoal_pit.title");
    }

    static List<ItemStack> tag(TagKey<Item> tag) {
        List<ItemStack> out = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(h -> out.add(new ItemStack(h.value())));
        return out;
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, @NotNull Entry entry, @NotNull IFocusGroup focuses) {
        List<ItemStack> logs = tag(ItemTags.LOGS_THAT_BURN);
        logs.forEach(s -> s.setCount(18));
        builder.addSlot(RecipeIngredientRole.INPUT, 1, TOP + 1).addItemStacks(logs).setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.INPUT, SLOT + 1, TOP + 1)
              .addItemStacks(List.of(new ItemStack(Items.DIRT, 24), new ItemStack(Items.GRAVEL, 24), new ItemStack(Items.CLAY, 24),
                    new ItemStack(Items.COBBLESTONE, 24))).setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.CATALYST, 2 * SLOT + 1, TOP + 1)
              .addItemStacks(List.of(new ItemStack(Items.FLINT_AND_STEEL), new ItemStack(PiForge.FIRE_STARTER.get()), new ItemStack(Items.FIRE_CHARGE)))
              .setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.CATALYST, 3 * SLOT + 1, TOP + 1).addItemStack(new ItemStack(PiMechanics.CLAY_DRAIN.getBlock()))
              .setStandardSlotBackground();
        int ox = W - 3 * SLOT;
        builder.addSlot(RecipeIngredientRole.OUTPUT, ox + 1, TOP + 1).addItemStack(new ItemStack(PiVanillaMaterials.item("charcoal"), 15))
              .setStandardSlotBackground();
        fluid(builder, RecipeIngredientRole.OUTPUT, ox + SLOT, TOP, SLOT, SLOT, PiFluids.CREOSOTE.source.get(), 360, 360);
        builder.addSlot(RecipeIngredientRole.OUTPUT, ox + 2 * SLOT + 1, TOP + 1).addItemStack(new ItemStack(PiMechanics.WOOD_ASH.get()))
              .setStandardSlotBackground();
    }

    @Override
    protected void drawContent(@NotNull Entry entry, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        JeiDraw.still(graphics, (4 * SLOT + W - 3 * SLOT) / 2F, TOP + 5 + 4);
        graphics.blit(DIAGRAM, 0, PIC, W, PIC_H, 0, 0, 352, 176, 352, 176);
        List<Note> notes = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            notes.add(new Note(Component.translatable("jei.pioneers.charcoal_pit.step" + i), i == 5 ? TEXT_LIGHT : TEXT));
        }
        fr.pioneers.client.gui.GuiDraw.screen(graphics, 0, PIC + PIC_H + 2, W, H - (PIC + PIC_H + 2));
        block(graphics, notes, 3, PIC + PIC_H + 2 + 2.5F, SMALL, W - 6, H - 1);
    }
}
