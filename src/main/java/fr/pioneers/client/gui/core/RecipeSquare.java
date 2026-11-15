package fr.pioneers.client.gui.core;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.tech.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.guide.client.GuideLinks;
import fr.pioneers.core.Texts;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe.FluidIn;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe.GasAmount;
import fr.pioneers.core.registries.PiFluids;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * API Machine (lot 2) : le CARRE DU SELECTEUR DE RECETTE de l'interface standard v2 (maquette validee :
 * scratchpad/apilot1/planches/planche_api1_machine.png). Lot 3 (Guillaume : « ouvrable / fermable, au-dessus de l'interface machine pour
 * optimiser la place ») : le carre s'ouvre et se ferme (onglet « Recettes » du bord droit, touche du selecteur, Ctrl + R par defaut ;
 * Echap ou ✕ le ferme) et, ouvert, se pose PAR-DESSUS la zone machine ({@link StandardLayout#squareOver}), au calque du dessus de l'ecran :
 * il prend la souris et le clavier ; ferme, il ne prend aucune place. De haut en bas (coordonnees du carre, {@link StandardLayout#SQ} = 130) :
 * <ul>
 *     <li>0-14 : titre « Recettes », nombre de recettes montrees, cadenas (clic : deverrouiller), ✕ (fermer) ;</li>
 *     <li>17-28 : recherche ACTIVE D'OFFICE (on tape, la grille filtre : noms des produits ET des entrees, sans accents) ;</li>
 *     <li>31-41 : filtres Toutes · Faisables (entrees la, bon tier) · ★ favoris · Recentes (Tab / Maj + Tab) ;</li>
 *     <li>44-98 : grille 6 x 3 a la molette (trait vert : tout est la ; ambre : une partie ; gris + coin rouge : tier insuffisant ;
 *     ★ favori, point bleu recente, cadenas : verrouillee) ;</li>
 *     <li>101-127 : apercu de la recette survolee (sinon choisie) : entrees -> sorties, duree MKI -> au tier, energie, tier demande.</li>
 * </ul>
 * Clavier : lettres = recherche, fleches = choisir, Entree = verrouiller (le carre se referme), Suppr = deverrouiller, Echap = fermer le
 * carre (l'ecran au coup suivant), Tab = filtre, F (sur une recette survolee) = favori. Souris : clic = verrouiller (le carre se referme),
 * clic droit = favori, Maj + clic = recettes dans JEI, molette = defiler ; objet glisse de JEI sur la grille = recherche par cet objet.
 * Favoris, recentes et etat ouvert / ferme : {@link RecipeMemory} (client).
 * <p>
 * Profondeur : le carre est dessine au calque du dessus (z de base {@link #BASE_Z}, au-dessus des objets des cases et de leurs nombres) ;
 * ses objets sont tires vers le bas ({@link #ITEM_DZ}) et ses marques au-dessus d'eux ({@link #MARK_DZ}) pour rester SOUS les infobulles
 * (z 400).
 */
public final class RecipeSquare {

    /** Ce que la machine montre au carre : le contexte du selecteur, la cle de ses favoris, sa puissance au tier. */
    public record Context(RecipePicker.Context picker, String source, long power) {}

    public static final int SQ = StandardLayout.SQ, COLS = 6, ROWS = 3, CELL = 18, GRID_X = 6, GRID_Y = 44, PREVIEW_Y = 104;
    /** Lot 3 : en-tete, depuis le bord droit : bouton ✕ (fermer), puis cadenas. */
    public static final int CLOSE_X = 15, LOCK_X = 25;
    static final int STAR = 0xFFFFD24A, LOCK = 0xFFE8C050, RECENT = RecipePicker.RECENT_COLOR, READY = RecipePicker.READY,
          PARTIAL = RecipePicker.PARTIAL, LOW = RecipePicker.LOW_TIER, SEPARATOR = 0xFF24333E;
    private static final int TEXT = PiMenuScreen.TEXT, DIM = PiMenuScreen.DIM;
    /** Filtres : toutes, faisables, favoris, recentes. */
    public static final int ALL = 0, READY_ONLY = 1, FAVORITES = 2, RECENT_ONLY = 3;
    private static final String[] FILTER_KEYS = {"all", "ready", "favorites", "recent"};
    /** Profondeur du carre au calque du dessus, de ses objets (renderItem ajoute 150) et de ses marques, relatives a la base. */
    public static final int BASE_Z = 310, ITEM_DZ = -130, MARK_DZ = 40;

    private String query = "";
    private int filter = ALL, scroll, selected, hovered = -1, blink;
    private boolean swallowChar;
    /** Liste montree (gardee tant que la liste, les entrees, le filtre, la recherche et la memoire ne changent pas). */
    private List<RecipeChoice> shown = List.of();
    private int[] shownState = new int[0];
    @Nullable
    private List<RecipeChoice> shownFor;
    private int shownSignature, shownFilter = -1, shownMemory;
    private String shownQuery = "";
    @Nullable
    private ResourceLocation shownLocked;
    private List<String> search = List.of();
    @Nullable
    private List<RecipeChoice> searchFor;
    /** Derniere recette survolee (clic droit, F, Maj + clic) et dernier contexte dessine. */
    @Nullable
    private Context last;

    public void tick() {
        blink++;
    }

    public String query() {
        return query;
    }

    /** Recherche posee de l'exterieur (objet glisse de JEI sur la grille). */
    public void search(String text) {
        query = text.length() > 32 ? text.substring(0, 32) : text;
        scroll = 0;
        selected = 0;
    }

    // ------------------------------------------------------------------
    // Liste montree
    // ------------------------------------------------------------------

    private static int memorySignature(String source) {
        return RecipeMemory.recent().hashCode() * 31 + RecipeMemory.favorites(source).hashCode();
    }

    private void refresh(Context ctx) {
        RecipePicker.Context c = ctx.picker();
        int sig = RecipePicker.signature(c), mem = memorySignature(ctx.source());
        if (c.choices() == shownFor && sig == shownSignature && query.equals(shownQuery) && filter == shownFilter && mem == shownMemory
            && java.util.Objects.equals(c.locked(), shownLocked)) {
            return;
        }
        RecipePicker.Ranked r = RecipePicker.rank(c);
        if (searchFor != c.choices()) {
            List<String> texts = new ArrayList<>(c.choices().size());
            for (RecipeChoice choice : c.choices()) {
                texts.add(choice.searchText());
            }
            search = texts;
            searchFor = c.choices();
        }
        String q = RecipeChoice.fold(query.trim());
        Set<ResourceLocation> favs = RecipeMemory.favorites(ctx.source());
        List<ResourceLocation> recent = RecipeMemory.recent();
        //Ordre : favoris, recentes (ordre d'usage), puis au bon tier (pretes, en partie, autres), puis tier insuffisant
        List<RecipeChoice> fav = new ArrayList<>(), rec = new ArrayList<>(), fit = new ArrayList<>(), low = new ArrayList<>();
        List<Integer> favS = new ArrayList<>(), recS = new ArrayList<>(), fitS = new ArrayList<>(), lowS = new ArrayList<>();
        for (int i = 0; i < r.order().size(); i++) {
            RecipeChoice choice = r.order().get(i);
            int state = r.state()[i];
            if (!passes(c, choice, q)) {
                continue;
            }
            boolean ok = choice.tier() <= c.tier();
            if (filter == READY_ONLY && !(state == 2 && ok) || filter == FAVORITES && !favs.contains(choice.id())
                || filter == RECENT_ONLY && !recent.contains(choice.id())) {
                continue;
            }
            if (favs.contains(choice.id())) {
                fav.add(choice);
                favS.add(state);
            } else if (recent.contains(choice.id())) {
                rec.add(choice);
                recS.add(state);
            } else {
                (ok ? fit : low).add(choice);
                (ok ? fitS : lowS).add(state);
            }
        }
        //Recentes dans l'ordre d'usage (la plus recente d'abord)
        List<RecipeChoice> recSorted = new ArrayList<>(rec);
        recSorted.sort((a, b) -> Integer.compare(recent.indexOf(a.id()), recent.indexOf(b.id())));
        List<Integer> recSortedS = new ArrayList<>();
        for (RecipeChoice x : recSorted) {
            recSortedS.add(recS.get(rec.indexOf(x)));
        }
        List<RecipeChoice> list = new ArrayList<>(fav);
        list.addAll(recSorted);
        list.addAll(fit);
        list.addAll(low);
        List<Integer> states = new ArrayList<>(favS);
        states.addAll(recSortedS);
        states.addAll(fitS);
        states.addAll(lowS);
        boolean reset = !query.equals(shownQuery) || filter != shownFilter;
        shown = list;
        shownState = states.stream().mapToInt(Integer::intValue).toArray();
        if (reset || shownFor == null) {
            scroll = 0;
            //La recette verrouillee choisie d'office (si elle est montree)
            selected = 0;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id().equals(c.locked())) {
                    selected = i;
                    break;
                }
            }
            scrollTo(selected);
        }
        selected = Math.max(0, Math.min(selected, list.size() - 1));
        shownFor = c.choices();
        shownSignature = sig;
        shownQuery = query;
        shownFilter = filter;
        shownMemory = mem;
        shownLocked = c.locked();
    }

    private boolean passes(RecipePicker.Context c, RecipeChoice choice, String q) {
        if (q.isEmpty()) {
            return true;
        }
        int index = c.choices().indexOf(choice);
        if (index < 0) {
            return false;
        }
        String text = search.get(index);
        for (String word : q.split("\\s+")) {
            if (!word.isEmpty() && !text.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private void scrollTo(int index) {
        int row = index / COLS;
        if (row < scroll) {
            scroll = row;
        } else if (row >= scroll + ROWS) {
            scroll = row - ROWS + 1;
        }
    }

    // ------------------------------------------------------------------
    // Dessin (coordonnees de l'hote : (x, y) = coin du carre)
    // ------------------------------------------------------------------

    /** Dessine le carre (coin (x, y) ; z de base : celui de l'appelant) ; {@code close} : bouton ✕. */
    public void draw(GuiGraphics g, RecipePicker.Host host, int mouseX, int mouseY, int x, int y, Context ctx, int accent, String panelSprite,
          Runnable close) {
        last = ctx;
        refresh(ctx);
        RecipePicker.Context c = ctx.picker();
        Font font = host.font();
        //Lot 3b (Guillaume : « cadre vert plus propre et au-dessus ») : fond plein, plus de panneau en neuf morceaux (ses barres vertes
        //coupees se melangeaient a celles de l'interface dessous) ; le cadre est dessine a la fin, au-dessus de tout le contenu (frame)
        g.fill(x, y, x + SQ, y + SQ, PiMenuScreen.BG);
        //Ombre portee (le carre est pose sur l'interface ; lot 4 : il tient dans la zone machine, l'ombre aussi)
        g.fill(x - 2, y + 2, x - 1, y + SQ, 0x60000000);
        // -- titre, nombre, cadenas, fermer
        Component title = Component.translatable("gui.pioneers.selector.title");
        GuiDraw.text(g, font, title, x + 7, y + 5, 0.85F, TEXT, 60);
        GuiDraw.text(g, font, Component.literal(String.valueOf(shown.size())), x + 7 + font.width(title) * 0.85F + 4, y + 5.6F, 0.7F, DIM, 20);
        boolean locked = c.locked() != null;
        int cx0 = x + SQ - CLOSE_X;
        boolean closeHover = host.over(mouseX, mouseY, cx0, y + 3, 9, 9);
        g.fill(cx0, y + 3, cx0 + 9, y + 12, closeHover ? 0xFF4A2A2E : 0xFF1E262E);
        GuiDraw.center(g, font, Component.literal("\u2716"), cx0 + 4.5F, y + 4.6F, 0.6F, closeHover ? 0xFFFF9090 : 0xFFB06060, 9);
        if (closeHover) {
            host.tooltip(List.of(Component.translatable("gui.pioneers.selector.close"),
                  Component.translatable("gui.pioneers.selector.close_tip").withStyle(ChatFormatting.DARK_GRAY)));
        }
        host.click(cx0, y + 3, 9, 9, close);
        lock(g, x + SQ - LOCK_X, y + 4, locked ? LOCK : 0xFF4A5664);
        if (host.over(mouseX, mouseY, x + SQ - LOCK_X - 2, y + 2, 9, 10)) {
            RecipeChoice lc = locked ? fr.pioneers.core.recipe.RecipeLockable.find(c.choices(), c.locked()) : null;
            host.tooltip(locked ? List.of(Component.translatable("gui.pioneers.selector.locked", lc == null ? Component.translatable(
                        "gui.pioneers.recipe_lock.missing") : lc.name()).withStyle(ChatFormatting.GREEN),
                  Component.translatable("gui.pioneers.selector.unlock").withStyle(ChatFormatting.GRAY))
                  : List.of(Component.translatable("gui.pioneers.selector.unlocked").withStyle(ChatFormatting.GRAY)));
        }
        if (locked) {
            host.click(x + SQ - LOCK_X - 2, y + 2, 9, 10, () -> host.choose(null));
        }
        g.fill(x + 6, y + 14, x + SQ - 6, y + 15, SEPARATOR);
        // -- recherche (active d'office)
        int fx = x + 6, fy = y + 17, fw = SQ - 12, fh = 11;
        g.fill(fx, fy, fx + fw, fy + fh, 0xFF000000 | accent);
        g.fill(fx + 1, fy + 1, fx + fw - 1, fy + fh - 1, 0xFF0B1116);
        g.fill(fx + 3, fy + 3, fx + 7, fy + 4, DIM);
        g.fill(fx + 3, fy + 3, fx + 4, fy + 7, DIM);
        g.fill(fx + 6, fy + 3, fx + 7, fy + 7, DIM);
        g.fill(fx + 3, fy + 6, fx + 7, fy + 7, DIM);
        g.fill(fx + 7, fy + 7, fx + 9, fy + 9, DIM);
        if (query.isEmpty()) {
            GuiDraw.text(g, font, Component.translatable("gui.pioneers.selector.search"), fx + 12, fy + 2.5F, 0.62F, 0xFF5A6A70, fw - 16);
        } else {
            GuiDraw.text(g, font, Component.literal(query), fx + 12, fy + 2.5F, 0.7F, TEXT, fw - 18);
            if (blink / 10 % 2 == 0) {
                float cx = fx + 12 + Math.min(fw - 18, font.width(query) * 0.7F) + 1;
                g.fill((int) cx, fy + 2, (int) cx + 1, fy + 9, TEXT);
            }
        }
        // -- filtres
        int chipX = x + 6;
        for (int i = 0; i < FILTER_KEYS.length; i++) {
            chipX = chip(g, host, mouseX, mouseY, chipX, y + 31, i, accent);
        }
        // -- grille
        int gx = x + GRID_X, gy = y + GRID_Y;
        int total = (shown.size() + COLS - 1) / COLS;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - ROWS)));
        hovered = -1;
        Set<ResourceLocation> favs = RecipeMemory.favorites(ctx.source());
        for (int i = 0; i < COLS * ROWS; i++) {
            int index = scroll * COLS + i;
            int cx = gx + (i % COLS) * CELL, cy = gy + (i / COLS) * CELL;
            g.fill(cx, cy, cx + 18, cy + 18, 0xFF0F151B);
            g.fill(cx + 1, cy + 1, cx + 17, cy + 17, 0xFF18212A);
            if (index >= shown.size()) {
                continue;
            }
            RecipeChoice r = shown.get(index);
            int state = shownState[index];
            boolean low = r.tier() > c.tier(), fav = favs.contains(r.id()), rec = RecipeMemory.isRecent(r.id()), lk = r.id().equals(c.locked());
            if (!r.icon().isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(0, 0, ITEM_DZ);
                g.renderItem(r.icon(), cx + 1, cy + 1);
                g.pose().popPose();
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, MARK_DZ);
            if (low) {
                g.fill(cx + 1, cy + 1, cx + 17, cy + 17, 0x9A101418);
                g.fill(cx + 12, cy + 1, cx + 17, cy + 4, LOW);
            }
            int border = state == 2 ? READY : state == 1 ? PARTIAL : 0;
            if (border != 0) {
                g.fill(cx + 1, cy + 16, cx + 17, cy + 17, border);
            }
            if (fav) {
                star(g, cx + 12, cy + 1, STAR);
            } else if (rec) {
                g.fill(cx + 13, cy + 2, cx + 16, cy + 5, RECENT);
            }
            if (lk) {
                lock(g, cx + 1, cy + 10, LOCK);
            }
            boolean over = host.over(mouseX, mouseY, cx + 1, cy + 1, 16, 16);
            if (over) {
                hovered = index;
            }
            if (index == selected) {
                outline(g, cx, cy, 18, 18, 0xFF000000 | accent);
                outline(g, cx + 1, cy + 1, 16, 16, 0x80FFFFFF);
            }
            if (over) {
                g.fill(cx + 1, cy + 1, cx + 17, cy + 17, 0x40FFFFFF);
            }
            g.pose().popPose();
            if (over) {
                List<Component> tip = new ArrayList<>(RecipePicker.tooltip(r, state, c, lk, rec));
                if (fav) {
                    tip.add(1, Component.translatable("gui.pioneers.selector.favorite").withStyle(ChatFormatting.GOLD));
                }
                tip.add(Component.translatable("gui.pioneers.selector.hint_cell").withStyle(ChatFormatting.DARK_GRAY));
                host.tooltip(tip);
            }
            ResourceLocation id = r.id();
            ItemStack icon = r.icon();
            host.click(cx + 1, cy + 1, 16, 16, () -> {
                if (Screen.hasShiftDown()) {
                    GuideLinks.showInJei(icon, false);
                } else {
                    selected = shown.indexOf(r);
                    RecipeMemory.remember(id);
                    host.choose(id);
                }
            });
        }
        // barre de defilement
        int sx = gx + COLS * CELL + 3;
        g.fill(sx, gy, sx + 4, gy + ROWS * CELL, 0xFF0F151B);
        int track = ROWS * CELL - 2, thumb = total <= ROWS ? track : Math.max(8, track * ROWS / total);
        int ty = gy + 1 + (total <= ROWS ? 0 : (track - thumb) * scroll / Math.max(1, total - ROWS));
        g.fill(sx + 1, ty, sx + 3, ty + thumb, total <= ROWS ? 0xFF2A3440 : 0xFF5A6A78);
        if (shown.isEmpty()) {
            GuiDraw.center(g, font, Component.translatable("gui.pioneers.recipe_lock.empty"), gx + COLS * CELL / 2F, gy + ROWS * CELL / 2F - 3, 0.7F, DIM,
                  COLS * CELL);
        }
        // -- apercu
        g.fill(x + 6, y + 100, x + SQ - 6, y + 101, SEPARATOR);
        int pick = hovered >= 0 ? hovered : selected;
        if (pick >= 0 && pick < shown.size()) {
            preview(g, host, mouseX, mouseY, x, y + PREVIEW_Y, shown.get(pick), ctx);
        }
        frame(g, x, y, accent);
    }

    /**
     * Cadre du carre, au-dessus de tout son contenu (objets de la grille compris) : bord sombre de 1 px, filet de l'accent de 1 px sur
     * les quatre cotes, coins pleins ; un seul trait continu, sans morceaux de panneau.
     */
    private static void frame(GuiGraphics g, int x, int y, int accent) {
        //Au-dessus des objets de la grille et de leurs marques, SOUS les infobulles (z 400)
        g.pose().pushPose();
        g.pose().translate(0, 0, MARK_DZ + 10);
        int a = 0xFF000000 | accent, dark = 0xFF0B1016;
        g.renderOutline(x - 1, y - 1, SQ + 2, SQ + 2, dark);
        g.renderOutline(x, y, SQ, SQ, a);
        g.renderOutline(x + 1, y + 1, SQ - 2, SQ - 2, 0x40000000 | (accent & 0xFFFFFF));
        g.pose().popPose();
    }

    private int chip(GuiGraphics g, RecipePicker.Host host, int mouseX, int mouseY, int x, int y, int index, int accent) {
        Font font = host.font();
        boolean on = filter == index;
        String key = FILTER_KEYS[index];
        Component label = index == FAVORITES ? Component.empty() : Component.translatable("gui.pioneers.selector." + key);
        boolean icon = index == FAVORITES || index == RECENT_ONLY;
        int w = index == FAVORITES ? 11 : (int) (font.width(label) * 0.6F + (icon ? 14 : 8));
        g.fill(x, y, x + w, y + 10, 0xFF000000 | (on ? accent : 0x26313B));
        if (on) {
            g.fill(x + 1, y + 1, x + w - 1, y + 9, 0xFF000000 | GuiDraw.darken(accent, 0.42F));
        }
        float tx = x + 4;
        if (index == FAVORITES) {
            star(g, x + 3, y + 3, STAR);
            tx = x + 10;
        } else if (index == RECENT_ONLY) {
            g.fill(x + 3, y + 3, x + 7, y + 7, RECENT);
            tx = x + 10;
        }
        if (index != FAVORITES) {
            GuiDraw.text(g, font, label, tx, y + 2.2F, 0.6F, on ? TEXT : DIM, w - (tx - x) - 1);
        }
        if (host.over(mouseX, mouseY, x, y, w, 10)) {
            //1.0.4-beta (lot 31) : salle blanche : « Faisables » sans « bon tier »
            boolean cleanroom = GuiMachine.lightSkin() && "ready".equals(key);
            host.tooltip(List.of(Component.translatable("gui.pioneers.selector." + key + "_tip" + (cleanroom ? "_cleanroom" : "")),
                  Component.translatable("gui.pioneers.selector.filter_keys").withStyle(ChatFormatting.DARK_GRAY)));
        }
        host.click(x, y, w, 10, () -> filter = index);
        return x + w + 2;
    }

    /** Apercu : entrees -> sorties (petites icones, quantites), tier demande a droite, duree MKI -> au tier et energie dessous. */
    private void preview(GuiGraphics g, RecipePicker.Host host, int mouseX, int mouseY, int x, int y, RecipeChoice r, Context ctx) {
        Font font = host.font();
        int machineTier = ctx.picker().tier();
        Component badge = RecipePicker.tierName(r.tier());
        //1.0.4-beta (lot 31) : interface de salle blanche sans tier : pas de pastille MK dans l'apercu
        boolean noTier = GuiMachine.lightSkin();
        int bw = noTier ? -2 : (int) (font.width(badge) * 0.55F) + 5;
        int limit = x + SQ - 9 - bw;
        int px = x + 7;
        int[] cursor = {px};
        boolean[] full = {false};
        for (Counted in : r.items()) {
            ItemStack[] all = in.ingredient().getItems();
            if (all.length > 0) {
                miniItem(g, font, cursor, limit, y, all[0], in.count(), full);
            }
        }
        for (FluidIn in : r.fluids()) {
            List<FluidStack> f = in.displayed();
            miniGauge(g, font, cursor, limit, y, f.isEmpty() ? 0x3F76E4 : fluidColor(f.get(0)), amount(in.amount()), full);
        }
        for (GasAmount in : r.gases()) {
            GasStack s = in.stack();
            miniGauge(g, font, cursor, limit, y, s.isEmpty() ? 0xC8D2DC : s.getGas().getTint() & 0xFFFFFF, amount(in.amount()), full);
        }
        // fleche
        int ax = cursor[0];
        if (ax + 13 <= limit) {
            g.fill(ax, y + 5, ax + 9, y + 7, 0xFF5A6A78);
            for (int i = 0; i < 3; i++) {
                g.fill(ax + 9 + i, y + 3 + i, ax + 10 + i, y + 9 - i, 0xFF5A6A78);
            }
            cursor[0] = ax + 15;
        }
        for (ItemStack o : r.outputs()) {
            miniItem(g, font, cursor, limit, y, o, o.getCount(), full);
        }
        for (FluidStack f : r.fluidOutputs()) {
            miniGauge(g, font, cursor, limit, y, fluidColor(f), amount(f.getAmount()), full);
        }
        for (GasAmount ga : r.gasOutputs()) {
            GasStack s = ga.stack();
            miniGauge(g, font, cursor, limit, y, s.isEmpty() ? 0xC8D2DC : s.getGas().getTint() & 0xFFFFFF, amount(ga.amount()), full);
        }
        if (full[0]) {
            GuiDraw.text(g, font, Component.literal("…"), cursor[0] - 2, y + 4, 0.7F, DIM, 8);
        }
        // tier demande
        boolean ok = r.tier() <= machineTier;
        int bc = ok ? tierColor(r.tier()) : 0xDC4038;
        int bx = x + SQ - 7 - bw;
        if (!noTier) {
            g.fill(bx, y + 1, bx + bw, y + 9, 0xFF000000 | GuiDraw.darken(bc, 0.5F));
            g.fill(bx, y + 8, bx + bw, y + 9, 0xFF000000 | bc);
            GuiDraw.text(g, font, badge, bx + 2.5F, y + 2.6F, 0.55F, 0xFFFFFFFF, bw - 3);
        }
        if (!noTier && host.over(mouseX, mouseY, bx, y + 1, bw, 8)) {
            host.tooltip(List.of(Component.translatable(ok ? "gui.pioneers.recipe_lock.tier" : "gui.pioneers.recipe_lock.tier_low", badge)
                  .withStyle(ok ? ChatFormatting.GRAY : ChatFormatting.RED)));
        }
        // duree MKI -> au tier, energie
        Component energy = Component.literal(Texts.grouped(ctx.power()) + " FE/t");
        Component line;
        if (r.time() > 0 && !r.turns()) {
            int at = Tiers.ticks(r.time(), machineTier, r.tier(), false);
            line = Component.translatable("gui.pioneers.selector.preview", seconds(r.time()), seconds(at), energy);
        } else {
            line = energy;
        }
        GuiDraw.text(g, font, line, x + 7, y + 15, 0.58F, DIM, SQ - 14);
    }

    private static Component seconds(int ticks) {
        return Component.translatable("gui.pioneers.selector.seconds", String.format(Locale.ROOT, "%.1f", ticks / 20.0));
    }

    private static void miniItem(GuiGraphics g, Font font, int[] cursor, int limit, int y, ItemStack stack, int count, boolean[] full) {
        int x = cursor[0];
        if (x + 13 > limit) {
            full[0] = true;
            return;
        }
        g.fill(x - 1, y - 1, x + 13, y + 13, 0xFF1A222B);
        g.pose().pushPose();
        g.pose().translate(x, y, ITEM_DZ);
        g.pose().scale(0.75F, 0.75F, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
        if (count > 1) {
            g.pose().pushPose();
            g.pose().translate(0, 0, MARK_DZ);
            String s = String.valueOf(count);
            GuiDraw.text(g, font, Component.literal(s), x + 13 - font.width(s) * 0.5F, y + 9, 0.5F, 0xFFFFFFFF, 14);
            g.pose().popPose();
        }
        cursor[0] = x + 15;
    }

    private static void miniGauge(GuiGraphics g, Font font, int[] cursor, int limit, int y, int color, String label, boolean[] full) {
        int x = cursor[0];
        if (x + 13 > limit) {
            full[0] = true;
            return;
        }
        g.fill(x - 1, y - 1, x + 13, y + 13, 0xFF1A222B);
        g.fill(x, y + 3, x + 12, y + 12, 0xFF000000 | color);
        g.fill(x, y + 3, x + 2, y + 12, 0xFF000000 | lighten(color));
        GuiDraw.text(g, font, Component.literal(label), x + 13 - font.width(label) * 0.5F, y + 9, 0.5F, 0xFFFFFFFF, 14);
        cursor[0] = x + 15;
    }

    /** Quantite courte d'un fluide ou d'un gaz : « 1B », « ½B », « 1.5B », ou des mB. */
    static String amount(long mb) {
        if (mb == 500) {
            return "½B";
        }
        if (mb >= 1000) {
            return mb % 1000 == 0 ? mb / 1000 + "B" : String.format(Locale.ROOT, "%.1fB", mb / 1000.0);
        }
        return String.valueOf(mb);
    }

    private static int fluidColor(FluidStack stack) {
        if (stack.isEmpty()) {
            return 0x3F76E4;
        }
        int tint = IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(stack) & 0xFFFFFF;
        int own = PiFluids.averageColor(stack.getFluid());
        if (tint == 0xFFFFFF && own != 0) {
            return own;
        }
        return tint == 0xFFFFFF ? 0x3F76E4 : tint;
    }

    private static int lighten(int c) {
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        return (int) (r + (255 - r) * 0.25) << 16 | (int) (g + (255 - g) * 0.25) << 8 | (int) (b + (255 - b) * 0.25);
    }

    /** Couleur d'un tier (pastille de JEI) : MKI vert, MKII bleu, MKIII rouge, MKIV pourpre. */
    static int tierColor(int tier) {
        return switch (Math.max(0, Math.min(3, tier))) {
            case 0 -> 0x58C860;
            case 1 -> 0x488CE6;
            case 2 -> 0xDC4038;
            default -> 0x962848;
        };
    }

    static void star(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y, x + 3, y + 5, color);
        g.fill(x, y + 1, x + 5, y + 3, color);
        g.fill(x + 1, y + 3, x + 2, y + 5, color);
        g.fill(x + 3, y + 3, x + 4, y + 5, color);
    }

    static void lock(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y + 2, x + 5, y + 6, color);
        g.fill(x + 1, y, x + 4, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 2, color);
        g.fill(x + 4, y + 1, x + 5, y + 2, color);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    // ------------------------------------------------------------------
    // Clavier, souris
    // ------------------------------------------------------------------

    /**
     * Touche (carre ouvert) : vrai si le carre la prend. Echap n'est pas pris ici : l'ecran ferme le carre (lot 3). Les lettres sont prises
     * (le caractere arrive par {@link #charTyped}) : « E » ne ferme pas l'interface ; les chiffres restent a la barre d'action quand une
     * case est survolee et que la recherche est vide.
     */
    public boolean keyPressed(int key, int modifiers, RecipePicker.Host host, boolean slotHovered) {
        int n = shown.size();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                scroll = 0;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            filter = (filter + ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? FILTER_KEYS.length - 1 : 1)) % FILTER_KEYS.length;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (n > 0) {
                ResourceLocation id = shown.get(Math.max(0, Math.min(selected, n - 1))).id();
                RecipeMemory.remember(id);
                host.choose(id);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            host.choose(null);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            if (n > 0) {
                int step = key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : key == GLFW.GLFW_KEY_UP ? -COLS : COLS;
                selected = Math.max(0, Math.min(n - 1, selected + step));
                scrollTo(selected);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && hovered >= 0 && hovered < n && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0) {
            toggleFavorite(hovered);
            swallowChar = true;
            return true;
        }
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0 || key == GLFW.GLFW_KEY_SPACE) {
            return true;
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            //Chiffres : a la barre d'action sur une case survolee (recherche vide), sinon a la recherche
            return !query.isEmpty() || !slotHovered;
        }
        return false;
    }

    /** Caractere tape : a la recherche. */
    public boolean charTyped(char ch, boolean slotHovered) {
        if (swallowChar) {
            swallowChar = false;
            return true;
        }
        if (Character.isDigit(ch) && query.isEmpty() && slotHovered) {
            return false;
        }
        if (ch >= ' ' && ch != '\u007f' && query.length() < 32) {
            query += ch;
            scroll = 0;
            return true;
        }
        return false;
    }

    /** Molette sur le carre (coordonnees de l'hote). */
    public boolean scrolled(double delta) {
        scroll -= (int) Math.signum(delta);
        return true;
    }

    /** Clic droit : la recette survolee devient (ou n'est plus) favorite. */
    public boolean rightClick() {
        if (hovered >= 0 && hovered < shown.size()) {
            toggleFavorite(hovered);
            return true;
        }
        return false;
    }

    private void toggleFavorite(int index) {
        if (last != null) {
            RecipeMemory.toggleFavorite(last.source(), shown.get(index).id());
        }
    }

    /** Lot 3 : le caractere de la touche qui vient de fermer / ouvrir le carre ne va pas a la recherche. */
    public void swallowNextChar() {
        swallowChar = true;
    }

    /** Lot 3 : ouverture du carre : recherche vide et active, premiere page. */
    public void opened() {
        search("");
        hovered = -1;
        shownFor = null;
    }

    /** Recherche par un objet (glisse de JEI) : son nom. */
    public void searchFor(ItemStack stack) {
        search(stack.getHoverName().getString().toLowerCase(Locale.ROOT));
    }
}
