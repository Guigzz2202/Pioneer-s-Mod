package fr.pioneers.integration.jei;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.client.gui.GuiDraw;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.10.2 (JEI) : categorie commune des PRODUCTIONS SANS RECETTE JSON (butins des blocs, generation du monde, cultures, saignees, gestes,
 * peche, ruines, villages, generateurs renouvelables, perceuse, sondes, recherche...). Avant, ces productions n'etaient que des pages
 * d'information : R (recettes d'un objet) et U (usages) ne les trouvaient pas. Ici chaque entree a de vraies cases d'entree et de sortie,
 * ses catalyseurs (machines qui ouvrent la categorie) et quelques lignes de conditions.
 * <p>
 * Une famille = une categorie (une {@link RecipeType} par famille, voir {@link Productions}). Interface sombre au style du mod
 * ({@link GuiDraw} : panneau, cases), sans {@code setOutputSlotBackground} (CLAUDE.md §3 nonies), textes par {@link #block}.
 */
class ProductionCategory extends PiCategory<ProductionCategory.Entry> {

    static final int W = 176, H = 84, SLOT = 18, TOP = 0, IN_COLS = 3, OUT_COLS = 5, ROWS = 2;
    /** Couleurs du panneau sombre (memes teintes que les interfaces du mod). */
    static final int LIGHT = 0xFFD8E0E8, DIM = 0xFF8A98A6, ACCENT = 0xFF3FD0A0;

    /**
     * Une case : objets (alternatives qui defilent), ou fluide, gaz, donnees ; {@code note} = petite mention dessinee sur la case
     * (quantite variable, chance) et reprise dans l'infobulle avec {@code tip}.
     */
    record Slot(List<ItemStack> items, @Nullable FluidStack fluid, @Nullable GasStack gas, @Nullable DataStack data, @Nullable String note,
                List<Component> tip) {

        static Slot items(List<ItemStack> items) {
            return new Slot(items, null, null, null, null, List.of());
        }

        static Slot item(ItemStack stack) {
            return items(List.of(stack));
        }

        static Slot fluid(FluidStack fluid) {
            return new Slot(List.of(), fluid, null, null, null, List.of());
        }

        static Slot gas(GasStack gas) {
            return new Slot(List.of(), null, gas, null, null, List.of());
        }

        static Slot data(DataStack data) {
            return new Slot(List.of(), null, null, data, null, List.of());
        }

        Slot with(@Nullable String note, List<Component> tip) {
            return new Slot(items, fluid, gas, data, note, tip);
        }

        boolean empty() {
            return items.isEmpty() && (fluid == null || fluid.isEmpty()) && (gas == null || gas.isEmpty()) && data == null;
        }
    }

    /** Une production : titre, entrees (6 au plus), sorties (10 au plus), conditions. */
    record Entry(Component title, List<Slot> inputs, List<Slot> outputs, List<Component> lines) {
    }

    ProductionCategory(IGuiHelper helper, RecipeType<Entry> type, String titleKey, ItemLike icon) {
        super(helper, type, titleKey, icon, W, H);
        //1.11.3 (charte) : badge de la famille d'apres l'identifiant (prod_<famille>)
        String id = JeiOrder.name(type);
        family(id.endsWith("renewable") ? JeiDraw.Family.ENERGY : id.endsWith("kinetic") ? JeiDraw.Family.MECHANICAL
              : id.endsWith("outpost") || id.endsWith("probes") || id.endsWith("greenhouse") ? JeiDraw.Family.SPACE
                : id.endsWith("research") ? JeiDraw.Family.RESEARCH : id.endsWith("gestures") ? JeiDraw.Family.PRIMITIVE : JeiDraw.Family.WORLD);
    }

    /** 1.11.3 : le titre de l'entree passe dans l'en-tete de la charte. */
    @Override
    protected Component headerTitle(Entry entry) {
        return entry.title();
    }

    private static int rows(Entry entry) {
        int r = Math.max((entry.inputs().size() + IN_COLS - 1) / IN_COLS, (entry.outputs().size() + OUT_COLS - 1) / OUT_COLS);
        return Math.max(1, Math.min(ROWS, r));
    }

    private static void slot(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, int y, Slot slot) {
        IRecipeSlotBuilder b;
        if (slot.fluid() != null && !slot.fluid().isEmpty()) {
            b = fluid(builder, role, x, y, SLOT, SLOT, slot.fluid().getFluid(), slot.fluid().getAmount(), slot.fluid().getAmount());
        } else if (slot.gas() != null && !slot.gas().isEmpty()) {
            b = gas(builder, role, x, y, SLOT, SLOT, slot.gas(), slot.gas().getAmount());
        } else if (slot.data() != null) {
            b = builder.addSlot(role, x + 1, y + 1).addIngredient(DataIngredient.TYPE, slot.data()).setStandardSlotBackground();
        } else {
            b = builder.addSlot(role, x + 1, y + 1).addItemStacks(slot.items()).setStandardSlotBackground();
        }
        if (slot.note() != null && !slot.note().isEmpty()) {
            b.setOverlay(new JeiDraw.Mark(slot.note(), false), 0, 0);
        }
        if (!slot.tip().isEmpty()) {
            b.addTooltipCallback((view, tooltip) -> tooltip.addAll(slot.tip()));
        }
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, Entry entry, @NotNull IFocusGroup focuses) {
        for (int i = 0; i < Math.min(IN_COLS * ROWS, entry.inputs().size()); i++) {
            slot(builder, RecipeIngredientRole.INPUT, (i % IN_COLS) * SLOT, TOP + (i / IN_COLS) * SLOT, entry.inputs().get(i));
        }
        int ox = W - OUT_COLS * SLOT;
        for (int i = 0; i < Math.min(OUT_COLS * ROWS, entry.outputs().size()); i++) {
            slot(builder, RecipeIngredientRole.OUTPUT, ox + (i % OUT_COLS) * SLOT, TOP + (i / OUT_COLS) * SLOT, entry.outputs().get(i));
        }
    }

    @Override
    protected void drawContent(@NotNull Entry entry, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        int rows = rows(entry);
        if (!entry.outputs().isEmpty()) {
            float left = IN_COLS * SLOT, right = W - OUT_COLS * SLOT;
            JeiDraw.still(graphics, (left + right) / 2F, TOP + rows * SLOT / 2F - 4 + 4);
        }
        List<Note> notes = new ArrayList<>();
        for (Component line : entry.lines()) {
            notes.add(new Note(line, LIGHT));
        }
        int y = TOP + rows * SLOT + 3;
        if (!notes.isEmpty()) {
            GuiDraw.screen(graphics, 0, y - 1, W, H - y + 1);
            block(graphics, notes, 3, y + 1.5F, SMALL, W - 6, H - 1);
        }
    }
}
