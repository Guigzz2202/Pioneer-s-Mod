package fr.pioneers.client.gui.core;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.Texts;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.industry.recipe.IndustrialRecipe.FluidIn;
import fr.pioneers.industry.recipe.IndustrialRecipe.GasAmount;
import fr.pioneers.transport.CableTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * MAJ Recettes (lot 7) : le selecteur de recette, panneau ouvert par la case « Recette » d'une machine (interface standard) ou par une
 * machine du terminal logistique. Liste calculee chez le client a partir des recettes synchronisees : celles qui collent aux entrees
 * presentes d'abord (toutes : bord vert ; une partie : bord ambre), puis les autres dans l'ordre de la machine ; filtre au clavier (noms
 * des produits et des entrees, sans accents) ; infobulle des entrees et du tier ; clic = verrouiller ; Entree = la premiere ; Echap = vider
 * le filtre puis fermer. Dessine par l'hote (coordonnees de l'hote) : {@link Host}.
 * <p>
 * 1.11.2 (lot E2, Guillaume : « la fenetre du selecteur doit etre plus intuitive et rapide de config ») : ligne du verrou en tete (recette
 * verrouillee, bouton « Deverrouiller », ou « Sans verrou ») ; champ de recherche actif d'office (cadre de l'accent, loupe) ; recettes
 * utilisees recemment en tete (pastille bleue, gardees d'une partie a l'autre : {@code <jeu>/pioneers/recipe_recents.txt}) ; puis celles
 * dont les entrees sont la au bon tier, puis les autres ; tier insuffisant en dernier et grise ; selection au clavier (fleches, Entree),
 * Suppr = deverrouiller ; resume de la recette choisie (nom, tier, duree) sous la grille. Maquettes : T:industry/selector_ui_preview.py.
 */
public final class RecipePicker {

    /** Ecran qui accueille le selecteur (ses coordonnees pour le dessin, les clics et les survols). */
    public interface Host {

        Font font();

        boolean over(int mouseX, int mouseY, int x, int y, int w, int h);

        void click(int x, int y, int w, int h, Runnable action);

        void tooltip(List<Component> lines);

        /** Verrouille cette recette (null : retire le verrou). */
        void choose(@Nullable ResourceLocation id);
    }

    /** Ce que la machine montre : ses recettes, ses entrees presentes, son verrou et son tier. */
    public record Context(List<RecipeChoice> choices, List<ItemStack> items, List<FluidStack> fluids, List<GasStack> gases,
                          @Nullable ResourceLocation locked, int tier) {}

    /** Classement : recettes dans l'ordre du selecteur, etat de chacune (2 toutes les entrees presentes, 1 une partie, 0 aucune). */
    public record Ranked(List<RecipeChoice> order, int[] state, int ready) {}

    public static final int CELL = 18, READY = 0xFF3FD0A0, PARTIAL = 0xFFE0A030, LOW_TIER = 0xFFE04848, WARN = 0xFFFFC040;
    private static final int TEXT = PiMenuScreen.TEXT, DIM = PiMenuScreen.DIM;

    private boolean open;
    private String query = "";
    private int scroll, blink;
    /** 1.11.2 : recette choisie au clavier (indice dans la liste montree), survolee, colonnes de la derniere image. */
    private int selected, hovered = -1, lastCols = 8, lastRows = 4;
    private boolean[] shownRecent = new boolean[0];
    /** Recettes verrouillees recemment : API Machine (lot 2), tenues par {@link RecipeMemory} (avec les favoris). */
    private static final int RECENT_SHOWN = 8;
    public static final int RECENT_COLOR = 0xFF4FC3F7;
    /** Filtre et classement gardes tant que la liste, les entrees presentes et le filtre ne changent pas. */
    @Nullable
    private List<RecipeChoice> shownFor;
    private String shownQuery = "";
    private int shownSignature;
    private List<RecipeChoice> shown = List.of();
    private int[] shownState = new int[0];
    private List<String> search = List.of();
    @Nullable
    private List<RecipeChoice> searchFor;

    public boolean isOpen() {
        return open;
    }

    public void open() {
        open = true;
        query = "";
        scroll = 0;
        selected = 0;
        shownFor = null;
    }

    public void close() {
        open = false;
        query = "";
    }

    public void toggle() {
        if (open) {
            close();
        } else {
            open();
        }
    }

    public void tick() {
        blink++;
    }

    // ------------------------------------------------------------------
    // Classement (aussi pour l'avertissement de la case « Recette »)
    // ------------------------------------------------------------------

    /** Signature des entrees presentes (sortes, sans les quantites) : le classement n'est refait que quand elle change. */
    static int signature(Context c) {
        int h = 17;
        for (ItemStack s : c.items()) {
            h = h * 31 + (s.isEmpty() ? 0 : System.identityHashCode(s.getItem()));
        }
        for (FluidStack f : c.fluids()) {
            h = h * 31 + (f.isEmpty() ? 0 : System.identityHashCode(f.getFluid()));
        }
        for (GasStack g : c.gases()) {
            h = h * 31 + (g.isEmpty() ? 0 : System.identityHashCode(g.getGas()));
        }
        return h;
    }

    private static List<RecipeChoice> lastFor;
    private static int lastSignature;
    private static Ranked last;

    /** Recettes classees : toutes les entrees presentes, puis une partie, puis les autres (ordre de la machine dans chaque groupe). */
    public static Ranked rank(Context c) {
        int sig = signature(c);
        if (c.choices() == lastFor && sig == lastSignature && last != null) {
            return last;
        }
        List<RecipeChoice> full = new ArrayList<>(), part = new ArrayList<>(), rest = new ArrayList<>();
        for (RecipeChoice r : c.choices()) {
            int present = r.present(c.items(), c.fluids(), c.gases());
            int kinds = r.items().size() + r.fluids().size() + r.gases().size();
            (present > 0 && present == kinds ? full : present > 0 ? part : rest).add(r);
        }
        List<RecipeChoice> order = new ArrayList<>(c.choices().size());
        order.addAll(full);
        order.addAll(part);
        order.addAll(rest);
        int[] state = new int[order.size()];
        for (int i = 0; i < order.size(); i++) {
            state[i] = i < full.size() ? 2 : i < full.size() + part.size() ? 1 : 0;
        }
        int ready = 0;
        for (RecipeChoice r : full) {
            if (r.tier() <= c.tier()) {
                ready++;
            }
        }
        lastFor = c.choices();
        lastSignature = sig;
        last = new Ranked(order, state, ready);
        return last;
    }

    /**
     * Avertissement de l'interface : sans verrou, plusieurs recettes de la machine prennent toutes les entrees presentes (la plus
     * complete passe ; les autres ne sortiront pas tant qu'on ne verrouille pas). Rend leur nombre, 0 sinon.
     */
    public static int ambiguous(Context c) {
        if (c.locked() != null || c.choices().size() < 2) {
            return 0;
        }
        int ready = rank(c).ready();
        return ready >= 2 ? ready : 0;
    }

    private void refresh(Context c) {
        int sig = signature(c);
        if (c.choices() == shownFor && sig == shownSignature && query.equals(shownQuery)) {
            return;
        }
        Ranked r = rank(c);
        if (searchFor != c.choices()) {
            //Textes de recherche faits une fois par liste (noms des produits et des entrees)
            List<String> texts = new ArrayList<>(c.choices().size());
            for (RecipeChoice choice : c.choices()) {
                texts.add(choice.searchText());
            }
            search = texts;
            searchFor = c.choices();
        }
        String q = RecipeChoice.fold(query.trim());
        //1.11.2 : recentes d'abord (dans l'ordre d'usage), puis au bon tier (pretes, en partie, autres), puis tier insuffisant
        List<RecipeChoice> recent = new ArrayList<>(), fit = new ArrayList<>(), low = new ArrayList<>();
        List<Integer> recentState = new ArrayList<>(), fitState = new ArrayList<>(), lowState = new ArrayList<>();
        for (ResourceLocation id : RecipeMemory.recent()) {
            if (recent.size() >= RECENT_SHOWN) {
                break;
            }
            for (int i = 0; i < r.order().size(); i++) {
                RecipeChoice choice = r.order().get(i);
                if (choice.id().equals(id) && passes(c, choice, q)) {
                    recent.add(choice);
                    recentState.add(r.state()[i]);
                    break;
                }
            }
        }
        for (int i = 0; i < r.order().size(); i++) {
            RecipeChoice choice = r.order().get(i);
            if (recent.contains(choice) || !passes(c, choice, q)) {
                continue;
            }
            boolean tooLow = choice.tier() > c.tier();
            (tooLow ? low : fit).add(choice);
            (tooLow ? lowState : fitState).add(r.state()[i]);
        }
        List<RecipeChoice> list = new ArrayList<>(recent);
        list.addAll(fit);
        list.addAll(low);
        List<Integer> states = new ArrayList<>(recentState);
        states.addAll(fitState);
        states.addAll(lowState);
        shown = list;
        shownState = states.stream().mapToInt(Integer::intValue).toArray();
        shownRecent = new boolean[list.size()];
        for (int i = 0; i < recent.size(); i++) {
            shownRecent[i] = true;
        }
        if (!query.equals(shownQuery)) {
            selected = 0;
            scroll = 0;
        }
        selected = Math.max(0, Math.min(selected, list.size() - 1));
        shownFor = c.choices();
        shownSignature = sig;
        shownQuery = query;
    }

    private boolean passes(Context c, RecipeChoice choice, String q) {
        if (q.isEmpty()) {
            return true;
        }
        int index = c.choices().indexOf(choice);
        return index >= 0 && matches(search.get(index), q);
    }

    // ------------------------------------------------------------------
    // Recettes recentes (gardees dans <jeu>/pioneers/recipe_recents.txt)
    // ------------------------------------------------------------------

    /** Une recette vient d'etre verrouillee : en tete des recentes ({@link RecipeMemory}). */
    public static void remember(@Nullable ResourceLocation id) {
        RecipeMemory.remember(id);
    }

    /** Verrouille (et retient) : passe par l'hote, puis ferme. */
    private void choose(Host host, @Nullable ResourceLocation id) {
        remember(id);
        host.choose(id);
        close();
    }

    /** Tous les mots du filtre se trouvent dans le texte. */
    private static boolean matches(String text, String query) {
        for (String word : query.split("\\s+")) {
            if (!word.isEmpty() && !text.contains(word)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    /** Nombre de colonnes et de rangees de la grille d'un panneau de cette taille. */
    public static int columns(int w) {
        return Math.max(1, (w - 14) / CELL);
    }

    public static int rows(int h) {
        //En-tete (16), ligne du verrou (22), recherche (17) ; en bas : legende, resume, aide (32)
        return Math.max(1, (h - GRID_Y - 32) / CELL);
    }

    /** Positions dans le panneau (relatives a son coin) : ligne du verrou, recherche, grille. */
    public static final int LOCK_Y = 16, SEARCH_Y = 38, GRID_Y = 55;

    /** Le panneau (x, y, w, h dans les coordonnees de l'hote), sous lequel rien ne reagit. */
    public void draw(GuiGraphics g, Host host, int mouseX, int mouseY, int x, int y, int w, int h, Context c, int accent) {
        refresh(c);
        Font font = host.font();
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x78000000);
        g.fill(x, y, x + w, y + h, 0xFF2A3440);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF121820);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFF000000 | accent);
        //Le panneau avale les clics (rien dessous ne reagit)
        host.click(x, y, w, h, () -> {
        });
        //En-tete : titre, nombre de recettes, fermer
        Component count = Component.translatable("gui.pioneers.recipe_lock.count", shown.size(), c.choices().size());
        float cw = font.width(count) * 0.6F;
        GuiDraw.text(g, font, Component.translatable("gui.pioneers.recipe_lock.title"), x + 6, y + 5, 0.75F, TEXT, Math.max(1, w - 30 - cw));
        GuiDraw.text(g, font, count, x + w - 18 - cw, y + 6, 0.6F, DIM, cw + 1);
        int cx = x + w - 13, cy = y + 4;
        boolean closeHover = host.over(mouseX, mouseY, cx, cy, 10, 10);
        g.fill(cx, cy, cx + 10, cy + 10, closeHover ? 0xFF4A2A2E : 0xFF2A313A);
        GuiDraw.center(g, font, Component.literal("✖"), cx + 5, cy + 2.2F, 0.7F, 0xFFE07070, 9);
        host.click(cx, cy, 10, 10, this::close);
        lockRow(g, host, mouseX, mouseY, x, y + LOCK_Y, w, c);
        //Recherche, active d'office : cadre de l'accent, loupe, curseur qui clignote
        int fx = x + 6, fy = y + SEARCH_Y, fw = w - 12;
        g.fill(fx - 1, fy - 1, fx + fw + 1, fy + 14, 0xFF000000 | accent);
        g.fill(fx, fy, fx + fw, fy + 13, 0xFF0B1016);
        int lx0 = fx + 3, ly0 = fy + 3;
        g.fill(lx0 + 1, ly0, lx0 + 4, ly0 + 1, DIM);
        g.fill(lx0 + 1, ly0 + 4, lx0 + 4, ly0 + 5, DIM);
        g.fill(lx0, ly0 + 1, lx0 + 1, ly0 + 4, DIM);
        g.fill(lx0 + 4, ly0 + 1, lx0 + 5, ly0 + 4, DIM);
        g.fill(lx0 + 5, ly0 + 5, lx0 + 7, ly0 + 7, DIM);
        if (query.isEmpty()) {
            GuiDraw.text(g, font, Component.translatable("gui.pioneers.recipe_lock.search"), fx + 12, fy + 3.5F, 0.7F, 0xFF59666A, fw - 15);
        } else {
            GuiDraw.text(g, font, Component.literal(query + (blink / 10 % 2 == 0 ? "_" : "")), fx + 12, fy + 3.5F, 0.7F, TEXT, fw - 15);
        }
        //Grille des recettes
        int cols = columns(w), rows = rows(h);
        lastCols = cols;
        lastRows = rows;
        int gx = x + 6, gy = y + GRID_Y;
        int total = (shown.size() + cols - 1) / cols;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - rows)));
        hovered = -1;
        for (int i = 0; i < cols * rows; i++) {
            int index = scroll * cols + i;
            int sx = gx + (i % cols) * CELL + 1, sy = gy + (i / cols) * CELL + 1;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF2C3640);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF0E1419);
            if (index >= shown.size()) {
                continue;
            }
            RecipeChoice r = shown.get(index);
            int state = shownState[index];
            boolean locked = r.id().equals(c.locked());
            boolean low = r.tier() > c.tier();
            int edge = locked ? 0xFF000000 | accent : state == 2 ? READY : state == 1 ? PARTIAL : 0;
            if (edge != 0) {
                g.renderOutline(sx - 1, sy - 1, 18, 18, edge);
            }
            ItemStack icon = r.icon();
            if (!icon.isEmpty()) {
                g.renderItem(icon, sx, sy);
                if (icon.getCount() > 1) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    GuiDraw.right(g, font, Component.literal(String.valueOf(icon.getCount())), sx + 17, sy + 10, 0.6F, 0xFFFFFFFF);
                    g.pose().popPose();
                }
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 210);
            if (low) {
                //Tier insuffisant : grisee, coin rouge
                g.fill(sx, sy, sx + 16, sy + 16, 0xA0121820);
                for (int k = 0; k < 5; k++) {
                    g.fill(sx + 11 + k, sy, sx + 16, sy + 1 + k, LOW_TIER);
                }
            }
            if (shownRecent[index]) {
                g.fill(sx, sy, sx + 3, sy + 3, RECENT_COLOR);
            }
            g.pose().popPose();
            boolean over = host.over(mouseX, mouseY, sx, sy, 16, 16);
            if (over) {
                hovered = index;
                selected = index;
            }
            if (index == selected) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 220);
                g.fill(sx, sy, sx + 16, sy + 16, 0x40FFFFFF);
                g.renderOutline(sx - 1, sy - 1, 18, 18, 0xFFFFFFFF);
                g.pose().popPose();
            }
            if (over) {
                host.tooltip(tooltip(r, state, c, locked, shownRecent[index]));
            }
            ResourceLocation id = r.id();
            host.click(sx, sy, 16, 16, () -> choose(host, id));
        }
        if (total > rows) {
            int track = rows * CELL, thumb = Math.max(8, track * rows / total);
            int ty = gy + (track - thumb) * scroll / Math.max(1, total - rows);
            int tx = gx + cols * CELL + 2;
            g.fill(tx, gy, tx + 2, gy + track, 0xFF1A2027);
            g.fill(tx, ty, tx + 2, ty + thumb, 0xFF55606F);
        }
        if (shown.isEmpty()) {
            GuiDraw.center(g, font, Component.translatable("gui.pioneers.recipe_lock.empty"), gx + cols * CELL / 2F, gy + rows * CELL / 2F - 3, 0.7F, DIM,
                  cols * CELL);
        }
        //Legende : entrees presentes, recente, tier insuffisant
        int ly = y + h - 30;
        float lx = gx;
        int[] colors = {READY, RECENT_COLOR, LOW_TIER};
        String[] keys = {"legend_ready", "legend_recent", "legend_tier"};
        for (int k = 0; k < 3; k++) {
            Component t = Component.translatable("gui.pioneers.recipe_lock." + keys[k]);
            float tw = Math.min(70, font.width(t) * 0.55F);
            if (lx + 6 + tw > x + w - 6) {
                break;
            }
            g.fill((int) lx, ly + 1, (int) lx + 4, ly + 5, colors[k]);
            GuiDraw.text(g, font, t, lx + 6, ly, 0.55F, DIM, 70);
            lx += 6 + tw + 8;
        }
        //Resume de la recette choisie : nom, tier, duree
        int sy = y + h - 21;
        g.fill(gx, sy, x + w - 6, sy + 10, 0xFF0B1016);
        if (selected >= 0 && selected < shown.size()) {
            RecipeChoice r = shown.get(selected);
            //1.0.4-beta (lot 32) : salle blanche : resume sans tier (nom et duree)
            MutableComponent summary = GuiMachine.lightSkin() ? r.name().copy() : r.name().copy().append(" · ").append(tierName(r.tier()));
            Component time = timeText(r);
            if (time != null) {
                summary.append(" · ").append(time);
            }
            GuiDraw.text(g, font, summary, gx + 3, sy + 2, 0.6F, r.tier() > c.tier() ? 0xFFE08080 : TEXT, w - 18);
        }
        GuiDraw.text(g, font, Component.translatable("gui.pioneers.recipe_lock.hint_panel"), gx, y + h - 10, 0.55F, 0xFF59666A, w - 12);
    }

    /** Ligne du verrou : recette verrouillee et bouton « Deverrouiller », ou « Sans verrou ». */
    private void lockRow(GuiGraphics g, Host host, int mouseX, int mouseY, int x, int ry, int w, Context c) {
        Font font = host.font();
        g.fill(x + 6, ry, x + 24, ry + 18, 0xFF2C3640);
        g.fill(x + 7, ry + 1, x + 23, ry + 17, 0xFF0E1419);
        RecipeChoice locked = c.locked() == null ? null : RecipeLockable.find(c.choices(), c.locked());
        if (c.locked() != null) {
            if (locked != null && !locked.icon().isEmpty()) {
                g.renderItem(locked.icon(), x + 7, ry + 1);
            }
            Component label = Component.translatable("gui.pioneers.recipe_lock.unlock");
            int bw = (int) (font.width(label) * 0.65F) + 10;
            int bx = x + w - 6 - bw;
            boolean hover = host.over(mouseX, mouseY, bx, ry + 3, bw, 12);
            g.fill(bx, ry + 3, bx + bw, ry + 15, hover ? 0xFF6A3036 : 0xFF4A2A2E);
            g.fill(bx, ry + 14, bx + bw, ry + 15, 0xFFE07070);
            GuiDraw.center(g, font, label, bx + bw / 2F, ry + 6, 0.65F, 0xFFF0A0A0, bw - 4);
            host.click(bx, ry + 3, bw, 12, () -> choose(host, null));
            Component name = locked != null ? locked.name() : Component.translatable("gui.pioneers.recipe_lock.missing");
            GuiDraw.text(g, font, name, x + 28, ry + 2, 0.75F, locked != null ? TEXT : 0xFFE08080, bx - x - 32);
            GuiDraw.text(g, font, Component.translatable("gui.pioneers.recipe_lock.current"), x + 28, ry + 11, 0.55F, DIM, bx - x - 32);
        } else {
            int dim = 0xFF4A5664;
            for (int i = 0; i < 3; i++) {
                g.fill(x + 10, ry + 5 + i * 4, x + 12, ry + 7 + i * 4, dim);
                g.fill(x + 14, ry + 5 + i * 4, x + 20, ry + 7 + i * 4, dim);
            }
            GuiDraw.text(g, font, Component.translatable("gui.pioneers.recipe_lock.auto"), x + 28, ry + 6, 0.6F, DIM, w - 34);
        }
    }

    /** Duree lisible d'une recette (null : inconnue). */
    @Nullable
    static Component timeText(RecipeChoice r) {
        if (r.time() <= 0) {
            return null;
        }
        return r.turns() ? Component.translatable("gui.pioneers.recipe_lock.turns", r.time())
                         : Component.translatable("gui.pioneers.recipe_lock.time", String.format(Locale.ROOT, "%.1f", r.time() / 20.0));
    }

    /** Infobulle d'une recette : produits, entrees, tier, ce que fait le clic. */
    static List<Component> tooltip(RecipeChoice r, int state, Context c, boolean locked, boolean recent) {
        List<Component> tip = new ArrayList<>();
        tip.add(r.name().copy().withStyle(ChatFormatting.WHITE));
        for (int i = 1; i < r.outputs().size(); i++) {
            ItemStack o = r.outputs().get(i);
            tip.add(Component.literal("+ " + o.getCount() + " × ").append(o.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
        for (FluidStack f : r.fluidOutputs()) {
            tip.add(Component.literal("+ " + Texts.format(f.getAmount()) + " mB ").append(f.getDisplayName()).withStyle(ChatFormatting.GRAY));
        }
        for (GasAmount ga : r.gasOutputs()) {
            GasStack s = ga.stack();
            if (!s.isEmpty()) {
                tip.add(Component.literal("+ " + Texts.format(ga.amount()) + " mB ").append(s.getGas().getName()).withStyle(ChatFormatting.GRAY));
            }
        }
        tip.add(Component.translatable("gui.pioneers.recipe_lock.inputs").withStyle(ChatFormatting.DARK_AQUA));
        for (Counted in : r.items()) {
            ItemStack[] all = in.ingredient().getItems();
            Component name = all.length == 0 ? Component.literal("?") : all[0].getHoverName();
            tip.add(present(Component.literal("  " + in.count() + " × ").append(name), has(c.items(), in)));
        }
        for (FluidIn in : r.fluids()) {
            List<FluidStack> shownFluids = in.displayed();
            Component name = shownFluids.isEmpty() ? Component.literal("?") : shownFluids.get(0).getDisplayName();
            boolean have = false;
            for (FluidStack f : c.fluids()) {
                have |= in.test(f);
            }
            tip.add(present(Component.literal("  " + Texts.format(in.amount()) + " mB ").append(name), have));
        }
        for (GasAmount in : r.gases()) {
            GasStack s = in.stack();
            boolean have = false;
            for (GasStack gs : c.gases()) {
                have |= in.test(gs);
            }
            tip.add(present(Component.literal("  " + Texts.format(in.amount()) + " mB ").append(s.isEmpty() ? Component.literal(in.gas().toString())
                  : s.getGas().getName()), have));
        }
        for (var d : r.data()) {
            tip.add(Component.literal("  " + Texts.format(d.getAmount()) + " Mo ").append(d.getType().getName()).withStyle(ChatFormatting.GRAY));
        }
        Component tier = tierName(r.tier());
        if (GuiMachine.lightSkin()) {
            //1.0.4-beta (lot 31) : interface de salle blanche, sans tier : seulement « hors de portee » quand la machine ne peut pas la faire
            if (r.tier() > c.tier()) {
                tip.add(Component.translatable("gui.pioneers.recipe_lock.cleanroom_low").withStyle(ChatFormatting.RED));
            }
        } else if (r.tier() > c.tier()) {
            tip.add(Component.translatable("gui.pioneers.recipe_lock.tier_low", tier).withStyle(ChatFormatting.RED));
        } else {
            tip.add(Component.translatable("gui.pioneers.recipe_lock.tier", tier).withStyle(ChatFormatting.GRAY));
        }
        Component time = timeText(r);
        if (time != null) {
            tip.add(time.copy().withStyle(ChatFormatting.GRAY));
        }
        if (state == 2) {
            tip.add(Component.translatable("gui.pioneers.recipe_lock.ready").withStyle(ChatFormatting.GREEN));
        }
        if (recent) {
            tip.add(Component.translatable("gui.pioneers.recipe_lock.recent").withStyle(ChatFormatting.AQUA));
        }
        tip.add(Component.translatable(locked ? "gui.pioneers.recipe_lock.current" : "gui.pioneers.recipe_lock.choose").withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    private static boolean has(List<ItemStack> items, Counted in) {
        for (ItemStack s : items) {
            if (!s.isEmpty() && in.ingredient().test(s)) {
                return true;
            }
        }
        return false;
    }

    private static Component present(MutableComponent line, boolean have) {
        return line.withStyle(have ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    public static Component tierName(int tier) {
        return Component.translatable("industry.pioneers.tier." + CableTier.values()[Math.max(0, Math.min(3, tier))].id());
    }

    // ------------------------------------------------------------------
    // Clavier, molette
    // ------------------------------------------------------------------

    /** Caractere tape (panneau ouvert : tout va au filtre). */
    public boolean charTyped(char ch) {
        if (!open) {
            return false;
        }
        if (ch >= ' ' && ch != '\u007f' && query.length() < 32) {
            query += ch;
            scroll = 0;
        }
        return true;
    }

    /**
     * Touche (panneau ouvert : toutes les touches sont prises, « E » ne ferme pas l'interface). Echap : vide le filtre, puis ferme ;
     * Entree : verrouille la premiere recette listee.
     */
    public boolean keyPressed(int key, Host host) {
        if (!open) {
            return false;
        }
        int n = shown.size();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!query.isEmpty()) {
                query = "";
            } else {
                close();
            }
        } else if (key == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            scroll = 0;
        } else if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && n > 0) {
            choose(host, shown.get(Math.max(0, Math.min(selected, n - 1))).id());
        } else if (key == GLFW.GLFW_KEY_DELETE) {
            //1.11.2 : Suppr = deverrouiller
            choose(host, null);
        } else if (n > 0 && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            int step = key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : key == GLFW.GLFW_KEY_UP ? -lastCols : lastCols;
            selected = Math.max(0, Math.min(n - 1, selected + step));
            int row = selected / Math.max(1, lastCols);
            if (row < scroll) {
                scroll = row;
            } else if (row >= scroll + lastRows) {
                scroll = row - lastRows + 1;
            }
        }
        return true;
    }

    public boolean scrolled(double delta) {
        if (!open) {
            return false;
        }
        scroll -= (int) Math.signum(delta);
        return true;
    }

    /** Premiere recette de la liste qui fait cet objet (glisser un objet de JEI sur la case « Recette »). */
    @Nullable
    public static ResourceLocation recipeFor(Context c, ItemStack stack) {
        for (RecipeChoice r : rank(c).order()) {
            if (r.makes(stack)) {
                return r.id();
            }
        }
        return null;
    }
}
