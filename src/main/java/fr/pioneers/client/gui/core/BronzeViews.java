package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.bronze.bench.BenchTile;
import fr.pioneers.tech.bronze.bench.BenchType;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.recipes.PiRecipes;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Vue des ETABLIS ({@code BenchType} : hors API Machine, decision 1 ; exceptions declarees de {@code tools/gc/api/machines.py}). Lot 8c : meme
 * CADRE v2 que les machines de l'API ({@link #BENCH} : vue enregistree par id de bloc, inventaire dans le cadre, titre et pastille d'age,
 * onglet Infos ; geometrie lue sur {@link BenchTile}) : cases d'outils et de materiaux a gauche, recherche et grille des pieces que l'atelier
 * sait faire (claires si faisables tout de suite, la choisie cerclee de la couleur de l'atelier), sortie et boutons a droite, besoins en bas.
 * L'etat (recherche, defilement) vit dans l'instance tenue par {@code MachineViews} (un ecran ouvert).
 */
final class BronzeViews {

    static final int GRID_X = BenchTile.GRID_X, GRID_Y = BenchTile.GRID_Y, COLS = BenchTile.COLS, ROWS = BenchTile.ROWS, CELL = BenchTile.CELL;
    /** Lot 8c : ids des 16 etablis (vue {@link #BENCH} enregistree par bloc dans {@link MachineScreens}). */
    static final String[] BENCHES = java.util.Arrays.stream(BenchType.values()).map(BenchType::id).toArray(String[]::new);

    /** Lot 8c : l'etabli dans le cadre v2 (vue d'un bloc sans fiche). */
    static final MachineScreens.View BENCH = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            gui.views().bronze().draw(g, mouseX, mouseY, tile);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            return gui.views().bronze().foreground(g, mouseX, mouseY, tile);
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity tile) {
            List<StandardView.InfoRow> rows = new ArrayList<>();
            String age = fr.pioneers.api.machine.MachineRegistry.stationAge(tile.getBlockState().getBlock());
            if (age != null) {
                rows.add(new StandardView.InfoRow("age", Component.translatable("tooltip.pioneers.api.age." + age), StandardView.ageColor(age)));
            }
            BenchTile bench = StorageViews.as(BenchTile.class, tile);
            if (bench != null && bench.getLevel() != null) {
                rows.add(new StandardView.InfoRow("recipes", Component.translatable("gui.pioneers.api.info.recipes_value",
                      BenchTile.recipes(bench.getLevel(), bench.type()).size()), PiMenuScreen.TEXT));
            }
            return rows;
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_workbench";
        }
    };

    private final GuiMachine gui;
    private int scroll;
    /** Barre de recherche des ateliers (texte garde tant que l'ecran reste ouvert, meme apres un redimensionnement). */
    static final int SEARCH_X = BenchTile.SEARCH.x(), SEARCH_Y = BenchTile.SEARCH.y(), SEARCH_W = BenchTile.SEARCH.w(), SEARCH_H = BenchTile.SEARCH.h();
    private SearchBox search;
    private String query = "";
    /** Texte cherche de chaque recette (nom du resultat et des ingredients, sans accents ni casse). */
    private final Map<BenchRecipe, String> keys = new WeakHashMap<>();
    private static final Pattern MARKS = Pattern.compile("\\p{M}"), SPACES = Pattern.compile("\\s+");
    /**
     * 0.20.46 (audit) : grille gardee d'une image a l'autre. Avant, chaque image refiltrait et retriait toutes les recettes (947 au
     * montage) avec un comparateur qui recalculait les pieces faisables (~19 000 appels par image) et repliait la recherche par recette.
     * Maintenant : liste des recettes gardee jusqu'au prochain rechargement, mots cherches replies une fois, pieces faisables calculees
     * une fois par contenu des cases, ordre (partition stable : faisables d'abord) refait seulement si la recherche ou les cases changent.
     */
    private Object listKey;
    private RecipeManager listManager;
    private int listGeneration = -1;
    private List<BenchRecipe> list = List.of();
    @Nullable
    private String orderQuery;
    @Nullable
    private String[] words;
    private final List<ItemStack> orderStacks = new ArrayList<>();
    @Nullable
    private List<Integer> order;
    /** Pieces faisables par index de recette (-1 : pas encore calcule pour ce contenu des cases). */
    private int[] counts = new int[0];

    /** Champ de recherche : Echap le vide avant de fermer l'ecran. */
    static final class SearchBox extends ScaledEditBox {

        //1.0.4-beta (lot 34) : texte a la taille des autres textes de l'ecran (champ commun ScaledEditBox, comme le comptoir d'entrepot)
        SearchBox(Font font, int x, int y, int w, int h) {
            super(font, x, y, w, h, Component.translatable("gui.pioneers.bench.search"));
        }
    }

    BronzeViews(GuiMachine gui) {
        this.gui = gui;
    }

    /** Cree la barre de recherche des ateliers et des machines a vapeur qui choisissent une piece. */
    void init(BlockEntity tile) {
        search = null;
        if (StorageViews.as(BenchTile.class, tile) != null) {
            search = gui.widget(new SearchBox(gui.font(), gui.left() + SEARCH_X + 5, gui.top() + SEARCH_Y + 2, SEARCH_W - 10, SEARCH_H - 3));
            search.setBordered(false);
            search.setMaxLength(40);
            search.setTextColor(0xFFE0F5EA);
            search.setHint(Component.translatable("gui.pioneers.bench.search").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query);
            search.setResponder(text -> {
                query = text;
                scroll = 0;
            });
        }
    }

    /** Echap : vide la recherche s'il y a du texte (vrai), sinon laisse fermer l'ecran. */
    boolean clearSearch() {
        if (search != null && !search.getValue().isEmpty()) {
            search.setValue("");
            return true;
        }
        return false;
    }

    /** Sans accents ni casse. */
    static String fold(String text) {
        return MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** Recettes de cet atelier ou de cette machine (cle : sorte), gardees jusqu'au prochain rechargement des recettes. */
    private List<BenchRecipe> recipes(Level level, Object key, Supplier<List<BenchRecipe>> build) {
        RecipeManager manager = level.getRecipeManager();
        if (key != listKey || manager != listManager || listGeneration != PiRecipes.generation()) {
            listKey = key;
            listManager = manager;
            listGeneration = PiRecipes.generation();
            list = build.get();
            order = null;
            counts = new int[0];
        }
        return list;
    }

    private String key(BenchRecipe recipe) {
        return keys.computeIfAbsent(recipe, r -> {
            StringBuilder b = new StringBuilder(r.result().getHoverName().getString());
            for (BenchRecipe.Counted c : r.ingredients()) {
                for (ItemStack stack : c.ingredient().getItems()) {
                    b.append(' ').append(stack.getHoverName().getString());
                }
            }
            return fold(b.toString());
        });
    }

    /** Mots cherches (replies une fois par recherche ; null : pas de recherche). */
    private void foldQuery() {
        if (!query.equals(orderQuery)) {
            orderQuery = query;
            words = query.isBlank() ? null : SPACES.split(fold(query).trim());
            order = null;
        }
    }

    /** La recette correspond-elle a tous les mots cherches ? */
    private boolean matches(BenchRecipe recipe) {
        if (words == null) {
            return true;
        }
        String k = key(recipe);
        for (String word : words) {
            if (!k.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** Pieces faisables de la recette {@code index} (gardees tant que le contenu des cases ne change pas). */
    private int count(List<BenchRecipe> recipes, int index, ToIntFunction<BenchRecipe> possible) {
        if (counts.length != recipes.size()) {
            counts = new int[recipes.size()];
            Arrays.fill(counts, -1);
        }
        if (counts[index] < 0) {
            counts[index] = possible.applyAsInt(recipes.get(index));
        }
        return counts[index];
    }

    /** Le contenu des cases a-t-il change depuis le dernier ordre ? (il est alors retenu) */
    private boolean stacksChanged(List<ItemStack> stacks) {
        boolean same = stacks.size() == orderStacks.size();
        for (int i = 0; same && i < stacks.size(); i++) {
            same = ItemStack.matches(stacks.get(i), orderStacks.get(i));
        }
        if (same) {
            return false;
        }
        orderStacks.clear();
        for (ItemStack stack : stacks) {
            orderStacks.add(stack.copy());
        }
        return true;
    }

    /**
     * Index (dans la liste commune) des recettes affichees : filtrees par la recherche, faisables d'abord (partition stable, comme le
     * tri d'avant). {@code stacks} : contenu des cases dont depend {@code possible}.
     */
    private List<Integer> shown(List<BenchRecipe> recipes, List<ItemStack> stacks, ToIntFunction<BenchRecipe> possible) {
        foldQuery();
        if (stacksChanged(stacks)) {
            counts = new int[0];
            order = null;
        }
        if (order == null) {
            List<Integer> yes = new ArrayList<>(), no = new ArrayList<>();
            for (int i = 0; i < recipes.size(); i++) {
                if (matches(recipes.get(i))) {
                    (count(recipes, i, possible) > 0 ? yes : no).add(i);
                }
            }
            yes.addAll(no);
            order = yes;
        }
        return order;
    }

    /** Nombre de recettes que la recherche laisse voir (molette). */
    private int matching(List<BenchRecipe> recipes) {
        foldQuery();
        if (order != null) {
            return order.size();
        }
        int n = 0;
        for (BenchRecipe recipe : recipes) {
            if (matches(recipe)) {
                n++;
            }
        }
        return n;
    }

    boolean draw(GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
        BenchTile bench = StorageViews.as(BenchTile.class, tile);
        if (bench != null) {
            bench(g, mouseX, mouseY, bench);
        } else {
            return false;
        }
        return true;
    }

    private long v(int i) {
        return gui.value(i);
    }

    boolean foreground(GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
        if (StorageViews.as(BenchTile.class, tile) != null) {
            for (int i = 0; i < BenchType.TOOLS; i++) {
                gui.slotTip(mouseX, mouseY, BenchTile.TOOL_X, BenchTile.TOOL_Y + i * 20, "gui.pioneers.bench.tool");
            }
            return true;
        }
        return false;
    }

    /** Molette : fait defiler la grille des pieces. */
    boolean scrolled(BlockEntity tile, double delta) {
        BenchTile bench = StorageViews.as(BenchTile.class, tile);
        int count = bench != null && bench.getLevel() != null ? matching(recipes(bench.getLevel(), bench.type(),
              () -> BenchTile.recipes(bench.getLevel(), bench.type()))) : -1;
        if (count >= 0) {
            int rows = (count + COLS - 1) / COLS;
            scroll = Math.max(0, Math.min(Math.max(0, rows - ROWS), scroll - (int) Math.signum(delta)));
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Atelier
    // ------------------------------------------------------------------

    private void bench(GuiGraphics g, int mouseX, int mouseY, BenchTile tile) {
        if (tile.getLevel() == null) {
            return;
        }
        BenchType type = tile.type();
        int accent = 0xFF000000 | type.accent;
        List<BenchRecipe> recipes = recipes(tile.getLevel(), type, () -> BenchTile.recipes(tile.getLevel(), type));
        int selected = (int) gui.value(BenchTile.SELECTED);
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.bench.tools"), BenchTile.TOOL_X, BenchTile.TOOL_Y - 7, 0.42F, PiMenuScreen.DIM);
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.bench.materials"), BenchTile.MAT_X, BenchTile.MAT_Y - 7, 0.42F, PiMenuScreen.DIM);
        List<ItemStack> tools = tile.toolStacks(), materials = tile.materialStacks();
        List<ItemStack> stacks = new ArrayList<>(tools);
        stacks.addAll(materials);
        BenchRecipe hovered = grid(g, mouseX, mouseY, recipes, stacks, selected, tile::possible, accent, BenchTile.SELECT);
        //Sortie et boutons
        BenchRecipe current = selected >= 0 && selected < recipes.size() ? recipes.get(selected) : null;
        if (current != null && tile.outputSlot().isEmpty()) {
            GuiDraw.ghost(g, current.result(), BenchTile.OUT_X, BenchTile.OUT_Y);
        }
        int can = current == null ? 0 : count(recipes, selected, tile::possible);
        StandardLayout.Rect cb = BenchTile.CRAFT_BUTTONS;
        gui.steelButton(g, mouseX, mouseY, cb.x(), cb.y(), cb.w(), 14, Component.translatable("gui.pioneers.bench.craft"), can > 0, () -> gui.send(BenchTile.CRAFT));
        gui.steelButton(g, mouseX, mouseY, cb.x(), cb.y() + 17, cb.w(), 14, Component.translatable("gui.pioneers.bench.craft_all", can), can > 1,
              () -> gui.send(BenchTile.CRAFT_ALL));
        if (type == BenchType.TANNER) {
            //1.12.0 (lot E4) : l'etabli du tanneur remplace le metier a tisser : motifs de banniere
            StandardLayout.Rect pb = BenchTile.PATTERNS_BUTTON;
            gui.steelButton(g, mouseX, mouseY, pb.x(), pb.y(), pb.w(), pb.h(), Component.translatable("gui.pioneers.bench.patterns"), true,
                  () -> gui.send(BenchTile.PATTERNS));
        }
        //Besoins de la piece choisie
        StandardLayout.Rect nr = BenchTile.NEEDS;
        int y = nr.y() + 2;
        g.fill(nr.x(), nr.y() - 1, nr.x() + nr.w(), nr.y(), 0xFF24333E);
        if (current == null) {
            GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.bench.pick"), 8, y + 4, 0.65F, PiMenuScreen.DIM, 220);
        } else {
            needs(g, mouseX, mouseY, tools, materials, current, y);
        }
        hoverTip(hovered);
    }

    private void hoverTip(BenchRecipe hovered) {
        if (hovered != null) {
            List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(gui.minecraft(), hovered.result()));
            tip.add(Component.translatable("gui.pioneers.bench.makes", hovered.result().getCount()).withStyle(ChatFormatting.DARK_GRAY));
            gui.tooltip(tip);
        }
    }

    /** Grille des pieces (claires si faisables, la choisie cerclee) avec defilement ; renvoie la piece survolee. */
    private BenchRecipe grid(GuiGraphics g, int mouseX, int mouseY, List<BenchRecipe> recipes, List<ItemStack> stacks, int selected,
          ToIntFunction<BenchRecipe> possible, int accent, int selectAction) {
        gui.screenAt(g, GRID_X - 2, GRID_Y - 2, COLS * CELL + 4, ROWS * CELL + 4);
        //Barre de recherche (le champ lui-meme est un widget dessine par l'ecran)
        if (search != null) {
            gui.fieldAt(g, SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H);
        }
        //Ordre d'affichage : recherche, puis faisables d'abord (l'index envoye au serveur reste celui de la liste commune)
        List<Integer> order = shown(recipes, stacks, possible);
        int rows = (order.size() + COLS - 1) / COLS;
        scroll = Math.max(0, Math.min(Math.max(0, rows - ROWS), scroll));
        if (order.isEmpty() && !recipes.isEmpty()) {
            GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.bench.no_match"), GRID_X + 4, GRID_Y + 6, 0.6F, PiMenuScreen.DIM, COLS * CELL - 8);
        }
        BenchRecipe hovered = null;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int slot = (scroll + r) * COLS + c;
                if (slot >= order.size()) {
                    continue;
                }
                int index = order.get(slot);
                BenchRecipe recipe = recipes.get(index);
                int x = GRID_X + c * CELL, y = GRID_Y + r * CELL;
                boolean ok = count(recipes, index, possible) > 0, hover = gui.over(mouseX, mouseY, x, y, CELL - 1, CELL - 1);
                g.fill(x, y, x + CELL - 1, y + CELL - 1, index == selected ? 0xFF2A3A30 : hover ? 0xFF222C34 : 0xFF161D24);
                if (index == selected) {
                    g.renderOutline(x, y, CELL - 1, CELL - 1, accent);
                }
                g.renderItem(recipe.result(), x + 1, y + 1);
                g.renderItemDecorations(gui.font(), recipe.result(), x + 1, y + 1);
                if (!ok) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 250);
                    g.fill(x, y, x + CELL - 1, y + CELL - 1, 0x99101418);
                    g.pose().popPose();
                }
                if (hover) {
                    hovered = recipe;
                }
                int pick = index;
                gui.click(x, y, CELL - 1, CELL - 1, () -> gui.send(selectAction + pick));
            }
        }
        if (rows > ROWS) {
            //Lot 8c : ▲ ▼ a droite de la recherche (au-dessus de la grille), position dans la liste en filet sous la recherche
            int bx = SEARCH_X + SEARCH_W + 2;
            gui.steelButton(g, mouseX, mouseY, bx, SEARCH_Y, 9, SEARCH_H, "▲", () -> scroll = Math.max(0, scroll - 1));
            gui.steelButton(g, mouseX, mouseY, bx + 11, SEARCH_Y, 9, SEARCH_H, "▼", () -> scroll = Math.min(rows - ROWS, scroll + 1));
            int track = COLS * CELL - 6;
            int knob = GRID_X + (int) Math.round(track * scroll / (double) Math.max(1, rows - ROWS));
            g.fill(knob, GRID_Y - 2, knob + 6, GRID_Y - 1, accent);
        }
        return hovered;
    }

    /** Ligne des besoins : outils puis ingredients (rouge s'il en manque), quantites en surimpression. */
    private void needs(GuiGraphics g, int mouseX, int mouseY, List<ItemStack> tools, List<ItemStack> materials, BenchRecipe recipe, int y) {
        int x = 8;
        if (tools != null) {
            boolean[] haveTool = new boolean[recipe.tools().size()];
            int[] match = recipe.matchTools(tools);
            for (int i = 0; i < haveTool.length; i++) {
                haveTool[i] = match != null || tools.stream().anyMatch(recipe.tools().get(i));
            }
            for (int i = 0; i < recipe.tools().size(); i++) {
                x = icon(g, mouseX, mouseY, recipe.tools().get(i), 1, x, y, haveTool[i], true);
            }
        }
        if (tools != null && !recipe.tools().isEmpty()) {
            GuiDraw.text(g, gui.font(), Component.literal("+"), x + 1, y + 5, 0.8F, PiMenuScreen.DIM);
            x += 9;
        }
        for (BenchRecipe.Counted c : recipe.ingredients()) {
            int have = 0;
            for (ItemStack stack : materials) {
                if (c.ingredient().test(stack)) {
                    have += stack.getCount();
                }
            }
            x = icon(g, mouseX, mouseY, c.ingredient(), c.count(), x, y, have >= c.count(), false);
        }
        GuiDraw.text(g, gui.font(), Component.literal("→"), x + 1, y + 5, 0.8F, PiMenuScreen.DIM);
        x += 10;
        g.renderItem(recipe.result(), x, y);
        g.renderItemDecorations(gui.font(), recipe.result(), x, y);
    }

    private int icon(GuiGraphics g, int mouseX, int mouseY, Ingredient ingredient, int count, int x, int y, boolean ok, boolean tool) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return x;
        }
        ItemStack shown = items[(int) ((System.currentTimeMillis() / 1000) % items.length)].copy();
        shown.setCount(Math.max(1, count));
        g.fill(x - 1, y - 1, x + 17, y + 17, ok ? 0xFF1E2A24 : 0xFF3A1A1A);
        g.renderItem(shown, x, y);
        if (count > 1) {
            g.renderItemDecorations(gui.font(), shown, x, y);
        }
        if (gui.over(mouseX, mouseY, x, y, 16, 16)) {
            List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(gui.minecraft(), shown));
            tip.add(0, Component.translatable(tool ? "gui.pioneers.bench.need_tool" : "gui.pioneers.bench.need_item").withStyle(
                  ok ? ChatFormatting.GREEN : ChatFormatting.RED));
            gui.tooltip(tip);
        }
        return x + 20;
    }
}
