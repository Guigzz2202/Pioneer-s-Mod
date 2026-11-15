package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.common.PiLang;
import fr.pioneers.core.Texts;
import fr.pioneers.recipe.AssemblyRecipe;
import fr.pioneers.space.research.ResearchNode;
import fr.pioneers.tile.base.TileEntityAssemblyBase;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * API Machine (lot 8b) : INTERFACE STANDARD v2 des tables d'assemblage (modules, equipement, rover ; vue {@code works}) et CORPS des halls
 * d'assemblage (assembleur de fusees, baie d'assemblage robotique : {@link #BODY}, dans la vue maison {@code house_works}). Corps propre en
 * VRAIE v2 (plus seulement encadre) : reserve d'energie et batterie a la place commune, panneau large de 236 ; valeurs NOMMEES
 * ({@link UiValues}) ; plus d'{@code instanceof} ({@link StorageViews#as}).
 * <pre>
 *  [E] [&lt;] |  Produit choisi   n/N · poste, duree, energie  | [&gt;]    PLAN [plan]
 *  [B] [ grille 5x10 : motif de la recette, ingredient fantome dans chaque case a remplir ]   |#|
 *                                                                                              [sortie]
 *      [Assembler] [Stop] [Repeter]   | etat : pieces 12/30, plan requis, pret...  |
 * </pre>
 */
final class WorksViews {

    private static final int HEADER_X = TileEntityAssemblyBase.HEADER.x() + 16, HEADER_W = TileEntityAssemblyBase.HEADER.w() - 32,
          BUTTON_X = TileEntityAssemblyBase.BUTTONS.x(), BUTTON_Y = TileEntityAssemblyBase.BUTTONS.y(), STATUS_X = BUTTON_X + 144,
          STATUS_W = TileEntityAssemblyBase.BUTTONS.x() + TileEntityAssemblyBase.BUTTONS.w() - STATUS_X;
    private static final int TEXT = PiMenuScreen.TEXT, DIM = PiMenuScreen.DIM;

    private final GuiMachine gui;

    private WorksViews(GuiMachine gui) {
        this.gui = gui;
    }

    /** Corps d'une table ou d'un hall (dessin et premier plan), sans le cadre. */
    static final MachineScreens.View BODY = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            TileEntityAssemblyBase table = StorageViews.as(TileEntityAssemblyBase.class, be);
            if (table != null) {
                new WorksViews(gui).draw(g, mouseX, mouseY, table);
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            TileEntityAssemblyBase table = StorageViews.as(TileEntityAssemblyBase.class, be);
            if (table != null) {
                new WorksViews(gui).foreground(g, mouseX, mouseY, table);
            }
            return true;
        }
    };

    /** Vue {@code works} : tables d'assemblage. */
    static final MachineScreens.View VIEW = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            BODY.draw(gui, g, mouseX, mouseY, be);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            return BODY.foreground(gui, g, mouseX, mouseY, be);
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            List<StandardView.InfoRow> rows = ScienceViews.energyRows(gui);
            rows.add(new StandardView.InfoRow("recipe_power", GuiMachine.literal(Texts.energy(gui.value(UiValues.FE_PER_TICK)) + "/t"), 0xFFFFFF55));
            return rows;
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_works";
        }
    };

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("assembler.pioneers." + key, args);
    }

    private static int[] cellPos(int index) {
        return new int[]{TileEntityAssemblyBase.GRID_X + (index % AssemblyRecipe.COLUMNS) * 18 + 1,
                         TileEntityAssemblyBase.GRID_Y + (index / AssemblyRecipe.COLUMNS) * 18 + 1};
    }

    /** Objet representatif d'un ingredient (cycle entre les objets acceptes). */
    private static ItemStack display(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return ItemStack.EMPTY;
        }
        return items[(int) (System.currentTimeMillis() / 1000 % items.length)];
    }

    private void draw(GuiGraphics graphics, int mouseX, int mouseY, TileEntityAssemblyBase table) {
        int accent = gui.accent();
        AssemblyRecipe recipe = table.getSelectedRecipe();
        List<AssemblyRecipe> recipes = table.getRecipes();
        //En-tete : fleches de choix et produit
        boolean idle = gui.value(UiValues.WORKING) == 0;
        gui.energy(graphics, mouseX, mouseY, 8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.ENERGY_H);
        gui.steelButton(graphics, mouseX, mouseY, HEADER_X - 16, 17, 14, 15, Component.literal("<"), idle, () -> gui.send(TileEntityAssemblyBase.CYCLE_BACK));
        gui.steelButton(graphics, mouseX, mouseY, HEADER_X + HEADER_W + 2, 17, 14, 15, Component.literal(">"), idle, () -> gui.send(TileEntityAssemblyBase.CYCLE_NEXT));
        gui.screenAt(graphics, HEADER_X, 17, HEADER_W, 15);
        float cx = HEADER_X + HEADER_W / 2F;
        if (recipe == null) {
            GuiDraw.center(graphics, gui.font(), tr("no_recipe"), cx, 21, 0.7F, 0xFFFF6A5A, HEADER_W - 8);
        } else {
            ItemStack result = recipe.getResult();
            Component name = result.getCount() > 1 ? result.getHoverName().copy().append(" ×" + result.getCount()) : result.getHoverName();
            GuiDraw.center(graphics, gui.font(), name, cx, 19.5F, 0.72F, 0xFF000000 | GuiDraw.darken(accent, 1F) | 0x202020, HEADER_W - 8);
            Component details = Component.literal((recipes.indexOf(recipe) + 1) + "/" + recipes.size() + " · ").append(table.getStation().getName())
                  .append(" · " + recipe.getDuration() / 20 + " s · " + Texts.energy(recipe.getEnergy()));
            GuiDraw.center(graphics, gui.font(), details, cx, 26.5F, 0.5F, DIM, HEADER_W - 8);
            //Grille : hors motif recouvert d'acier, ingredient attendu en fantome
            for (int index = 0; index < AssemblyRecipe.CELLS; index++) {
                int[] p = cellPos(index);
                if (!recipe.usesSlot(index)) {
                    if (table.isGridSlotEmpty(index)) {
                        graphics.fill(p[0] - 1, p[1] - 1, p[0] + 17, p[1] + 17, 0xFF1A2027);
                    }
                } else if (table.isGridSlotEmpty(index)) {
                    Ingredient ingredient = recipe.cell(index);
                    if (ingredient != null) {
                        GuiDraw.ghost(graphics, display(ingredient), p[0], p[1]);
                    }
                }
            }
            if (recipe.getBlueprint() != null && table.isBlueprintMissing()) {
                GuiDraw.ghost(graphics, new ItemStack(recipe.getBlueprint()), TileEntityAssemblyBase.BLUEPRINT_X, TileEntityAssemblyBase.BLUEPRINT_Y);
            }
        }
        //Colonne de droite : plan, avancement, sortie
        GuiDraw.center(graphics, gui.font(), tr("blueprint_short"), TileEntityAssemblyBase.BLUEPRINT_X + 8, 30.5F, 0.5F, DIM, 30);
        Rect bar = TileEntityAssemblyBase.BAR;
        graphics.fill(bar.x(), bar.y(), bar.x() + bar.w(), bar.y() + bar.h(), 0xFF0B1016);
        int filled = (int) Math.round(bar.h() * gui.value(UiValues.PROGRESS_X1000) / 1000.0);
        if (filled > 0) {
            graphics.fill(bar.x(), bar.y(), bar.x() + bar.w(), bar.y() + filled, 0xFF000000 | accent);
        }
        //Boutons et etat
        boolean building = gui.value(UiValues.WORKING) != 0, repeat = gui.value(TileEntityAssemblyBase.REPEAT_ON) != 0;
        gui.steelButton(graphics, mouseX, mouseY, BUTTON_X, BUTTON_Y, 54, 14, PiLang.ASSEMBLER_BUILD.translate(), !building, () -> gui.send(TileEntityAssemblyBase.START));
        gui.steelButton(graphics, mouseX, mouseY, BUTTON_X + 56, BUTTON_Y, 34, 14, PiLang.ASSEMBLER_CANCEL.translate(), building, () -> gui.send(TileEntityAssemblyBase.STOP));
        if (repeat) {
            gui.pageButton(graphics, mouseX, mouseY, BUTTON_X + 92, BUTTON_Y, 50, 14, PiLang.ASSEMBLER_REPEAT_ON.translate(), true, accent,
                  () -> gui.send(TileEntityAssemblyBase.REPEAT));
            gui.click(BUTTON_X + 92, BUTTON_Y, 50, 14, () -> gui.send(TileEntityAssemblyBase.REPEAT));
        } else {
            gui.steelButton(graphics, mouseX, mouseY, BUTTON_X + 92, BUTTON_Y, 50, 14, PiLang.ASSEMBLER_REPEAT_OFF.translate(), true, () -> gui.send(TileEntityAssemblyBase.REPEAT));
        }
        gui.screenAt(graphics, STATUS_X, BUTTON_Y, STATUS_W, 14);
        if (recipe != null) {
            Component text;
            int color;
            Component problem = table.structureProblem();
            if (problem != null) {
                //1.9.2 : multibloc d'assemblage incomplet (detail au survol)
                text = tr("status.structure");
                color = 0xFFFF6A5A;
                if (gui.over(mouseX, mouseY, STATUS_X, BUTTON_Y, STATUS_W, 14)) {
                    gui.tooltip(List.of(problem, Component.translatable("assembler.pioneers.structure.blueprint").withStyle(ChatFormatting.GRAY)));
                }
            } else if (building) {
                text = PiLang.ASSEMBLER_PROGRESS.translate(GuiMachine.percent(gui.value(UiValues.PROGRESS_X1000) / 1000.0).getString());
                color = 0xFF55CCFF;
            } else if (table.isBlueprintMissing()) {
                text = tr("status.blueprint");
                color = 0xFFFF6A5A;
            } else if (table.missingIngredients() > 0) {
                text = tr("status.parts", recipe.cellCount() - table.missingIngredients(), recipe.cellCount());
                color = 0xFFFFAA00;
            } else if (table.hasFinishedProduct()) {
                text = tr("status.output");
                color = 0xFFFFFF55;
            } else {
                text = tr("status.ready");
                color = 0xFF55FF88;
            }
            GuiDraw.text(graphics, gui.font(), text, STATUS_X + 4, BUTTON_Y + 4, 0.6F, color, STATUS_W - 8);
            ResearchNode research = recipe.getResearch();
            if (research != null && !recipe.isResearched() && gui.over(mouseX, mouseY, STATUS_X, BUTTON_Y, STATUS_W, 14)) {
                gui.tooltip(List.of(Component.translatable("research.pioneers.blocked").withStyle(ChatFormatting.GOLD),
                      Component.translatable(research.getTranslationKey()).withStyle(ChatFormatting.RED)));
            }
        }
    }

    /** Premier plan : objets errones en rouge, infobulles des cases vides. */
    private void foreground(GuiGraphics graphics, int mouseX, int mouseY, TileEntityAssemblyBase table) {
        AssemblyRecipe recipe = table.getSelectedRecipe();
        for (int index = 0; index < AssemblyRecipe.CELLS; index++) {
            int[] p = cellPos(index);
            if (recipe != null && !recipe.usesSlot(index) && !table.isGridSlotEmpty(index) || table.isWrongItem(index)) {
                graphics.fill(p[0], p[1], p[0] + 16, p[1] + 16, 0x80C02020);
            }
        }
        if (gui.over(mouseX, mouseY, TileEntityAssemblyBase.BLUEPRINT_X, TileEntityAssemblyBase.BLUEPRINT_Y, 16, 16)) {
            gui.slotTip(mouseX, mouseY, TileEntityAssemblyBase.BLUEPRINT_X, TileEntityAssemblyBase.BLUEPRINT_Y,
                  recipe != null && recipe.getBlueprint() != null ? "assembler.pioneers.blueprint" : "assembler.pioneers.blueprint_none");
        }
        gui.slotTip(mouseX, mouseY, TileEntityAssemblyBase.OUTPUT_X, TileEntityAssemblyBase.OUTPUT_Y, "gui.pioneers.machine.out");
        gui.slotTip(mouseX, mouseY, TileEntityAssemblyBase.ENERGY_X, TileEntityAssemblyBase.ENERGY_Y, "gui.pioneers.machine.battery");
        if (recipe != null) {
            for (int index = 0; index < AssemblyRecipe.CELLS; index++) {
                int[] p = cellPos(index);
                Ingredient ingredient = recipe.cell(index);
                if (ingredient != null && table.isGridSlotEmpty(index) && gui.over(mouseX, mouseY, p[0], p[1], 16, 16)) {
                    ItemStack shown = display(ingredient);
                    if (!shown.isEmpty()) {
                        gui.tooltip(List.of(shown.getHoverName()));
                    }
                }
            }
        }
    }
}
