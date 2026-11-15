package fr.pioneers.integration.jei;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.industry.Tiers;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.3-beta (lot 10, demande de Guillaume du 05/11 : « une page de type Procede par materiau complexe, qui explique comment l'obtenir sans
 * detailler les recettes, par type de procede ») : la page transverse « Procedes » ({@code pioneers:processes}). Une page par materiau
 * (aluminium, titane, acier, silicium, circuits, plastiques, carburants, combustibles nucleaires...) ; une voie = depart -> etapes -> produit.
 * Pas de quantites : la machine et les objets sont cliquables (R / U) et menent aux recettes. Roles : depart et intermediaires en ENTREE (U sur
 * une matiere de la voie montre la page), produit de chaque voie en SORTIE (R), machines en RENDER_ONLY (cliquables, sans faire apparaitre la
 * page dans leurs usages).
 * 1.0.3-beta (lot 14) : mise en page refaite (machine au-dessus de son etape, libelles sur une ligne, bande des reactifs et sous-produits).
 * 1.0.3-beta (lot 15, Guillaume : « ameliore encore la qualite de la page procedes JEI ») : schema « objet -> [machine sur la fleche] ->
 * objet » (3 etapes par rangee), PICTOGRAMME DU PROCEDE dans un cadre du tier (la case reste la machine : infobulle, clic = recettes),
 * libelles courts ecrits a la source (1 ou 2 lignes a 0,75, nom complet en infobulle), reactifs « + » au-dessus de la machine, sous-produits
 * dessous, depart nomme, produit au cadre dore, titres courts sans troncature, pastille du tier avec la complexite en points, force et
 * faiblesse en pastilles, VUE D'ENSEMBLE des voies en tete de la premiere page, voie trop longue coupee entre deux rangees (« (suite) »).
 * Miroir Python de TOUTE la mise en page (memes noms) et controle des 82 pages en francais et en anglais :
 * {@code tools/jei/process_layout.py}, {@code tools/jei/check_process_layout.py} ; planches {@code scratchpad/p103/lot15/planches_p103_15.py}.
 * <p>
 * Donnees : {@code assets/pioneers/jei/process_pages.json}, ecrit par {@code tools/gc/industry/process_pages.py} (SOURCE UNIQUE : voies de
 * {@code processes.py} et du graphe des recettes, chaque etape = une recette reelle, controle {@code --check} ; libelles courts, pictogrammes,
 * forces et faiblesses : {@code tools/gc/industry/process_data.py} ; pictogrammes dessines par {@code tools/gc/icons/draw_jei.py PICTOS}).
 */
final class ProcessCategory extends PiCategory<ProcessCategory.Page> {

    static final RecipeType<Page> TYPE = JeiOrder.create("processes", Page.class);
    private static final ResourceLocation FILE = Pioneers.rl("jei/process_pages.json");

    // geometrie (coordonnees du contenu). 1.0.3-beta (lot 15, Guillaume : « ameliore encore la qualite de la page procedes JEI ») : une
    // voie = TITRE (numero, pastille du tier avec la complexite en points, nom court) / PASTILLES (+ force, - faiblesse) / RANGEES « objet ->
    // [machine sur la fleche] -> objet », 3 etapes par rangee (pas de 52 : objet 18, fleche 8, machine 18, fleche 8). Au-dessus de chaque
    // machine : le libelle court du procede (1 ou 2 lignes a 0,75), puis les reactifs qui ENTRENT (« + », icones de 8 px) ; dessous : les
    // sous-produits qui SORTENT (fleche orange, icones de 8 px). Sous la case de depart : d'ou l'on part. Le produit : cadre dore. Rangee
    // suivante : l'objet repris en tete, fleche de retour depuis la fin de la rangee d'avant. Premiere page d'un materiau a plusieurs voies :
    // VUE D'ENSEMBLE (une ligne par voie). Une voie plus haute que la page est coupee entre deux rangees (« (suite) » sur la page d'apres).
    // Maquette et controle des 82 pages : tools/jei/check_process_layout.py (miroir de ces constantes, vraie police).
    static final int WIDTH = 176, HEIGHT = 190, X0 = 1, PITCH = 52, STUB = 8, PER_ROW = 3, NODE = 18, TITLE_H = 10, TAGS_H = 10,
          LINE = 7, RG_H = 9, MINI = 8, MINI_STEP = 9, ROW_GAP = 5, ROUTE_GAP = 5, OV_LINE = 10, OV_GAP = 4, LABEL_ROOM = 49,
          TAG_H = 9, NUM_W = 9, RETURN_X = 172, MIN_HEIGHT = 130, JEI_CHROME = 122, FRAME = 36;
    /** Couleurs : reactifs (vert), sous-produits (orange), cadre du produit (or), fond des pastilles, points de complexite eteints. */
    static final int REAGENT = 0xFF6FD08A, BYPRODUCT = 0xFFE0904A, GOLD = 0xFFE8C060, TAG_BG = 0xFF222D37, PIP_OFF = 0x60FFFFFF;
    /** Echelle des textes (jamais moins de SCALE_MIN : un texte trop long est reduit, jamais coupe). */
    static final float SCALE = 0.75F, SCALE_MIN = 0.6F;

    private static final int NUM_BG = 0xFF3A4A58, NUM_EDGE = 0xFF2A3640, SEP = 0xFF2A3640;

    /** Un noeud : objet (ou tag d'objets), fluide ou gaz. */
    record Node(String id, @Nullable String tag) {

        boolean fluid() {
            return id.startsWith("fluid:");
        }

        boolean gas() {
            return id.startsWith("gas:");
        }

        ResourceLocation rl() {
            return new ResourceLocation(fluid() ? id.substring(6) : gas() ? id.substring(4) : id);
        }
    }

    /** Une etape : poste (objet), tier, cle du procede (nom complet), cle du libelle court, pictogramme, sortie, reactifs, sous-produits. */
    record Step(String station, int tier, String proc, String label, String picto, Node out, List<Node> reagents, List<Node> byproducts) {

        /** Lignes du libelle court dans la langue du joueur (« \n » ecrit a la source). */
        List<String> lines() {
            return Arrays.asList(Component.translatable(label).getString().split("\n"));
        }
    }

    /**
     * Une voie : bande, tier, origine, premiere voie ?, cle des textes (voies de processes.py) ou poste final (voies du graphe), depart,
     * etapes de la ligne et a cote, etapes en machine, complexite (1..3), cles de la force et de la faiblesse courtes.
     */
    record Route(String band, int tier, String origin, boolean first, @Nullable String text, @Nullable String by, @Nullable Node start,
                 List<Step> steps, List<Step> side, int count, int pips, @Nullable String plus, @Nullable String minus) {

        int labLines() {
            int n = 1;
            for (Step s : steps) {
                n = Math.max(n, s.lines().size());
            }
            return n;
        }

        boolean hasReagents() {
            return steps.stream().anyMatch(s -> !s.reagents().isEmpty());
        }

        boolean hasTags() {
            return plus != null || minus != null;
        }

        int rows() {
            return Math.max(1, (steps.size() + PER_ROW - 1) / PER_ROW);
        }

        int rowH() {
            return labLines() * LINE + 1 + (hasReagents() ? RG_H : 0) + NODE + 1 + MINI;
        }

        int headH(boolean cont) {
            return TITLE_H + (hasTags() && !cont ? TAGS_H : 0) + 1;
        }

        int pieceH(int r0, int r1, boolean cont) {
            int n = r1 - r0;
            return headH(cont) + n * rowH() + (n - 1) * ROW_GAP;
        }
    }

    /** Morceau d'une voie pose sur une vue : rangees [r0, r1), suite d'une vue precedente ? */
    record Piece(int route, int r0, int r1, boolean cont) {
    }

    /** Une vue JEI : le materiau, TOUTES ses voies, les morceaux de cette vue, le rang de la vue, le nombre de vues, la vue de chaque voie. */
    record Page(String id, String family, @Nullable String title, Node product, int age, int total, List<Route> routes, List<Piece> pieces,
                int index, int pages, int[] onPage) {
    }

    /** Une rangee posee sur la vue : sa voie, son rang dans la voie, ses ordonnees (libelles, reactifs, objets, sous-produits). */
    record RowPos(Route route, int ri, int row, int rt, int rgy, int ny, int bpy, boolean firstOfPiece) {
    }

    /**
     * 1.0.3-beta (correctif, Guillaume : « l'interface des procedes est trop grande et deborde ») : hauteur du contenu ajustee a la fenetre.
     * JEI donne a la fenetre des recettes la hauteur de l'ecran moins 58 px (76 avec la recherche au centre), dont ~44 px de titres et de
     * boutons : la page entiere (contenu + {@link #FRAME} d'en-tete, de pied et de marge) doit tenir dans l'ecran moins {@link #JEI_CHROME}.
     * Calculee a chaque demarrage de JEI (connexion a un monde) ; bornee a [{@link #MIN_HEIGHT}, {@link #HEIGHT}]. La pagination suit.
     */
    static int viewH = HEIGHT;

    static int fitHeight() {
        int screen = HEIGHT + JEI_CHROME + FRAME;
        try {
            screen = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        } catch (RuntimeException e) {
            //banc hors jeu : hauteur maximale
        }
        viewH = Math.max(MIN_HEIGHT, Math.min(HEIGHT, screen - JEI_CHROME - FRAME));
        return viewH;
    }

    ProcessCategory(IGuiHelper helper) {
        super(helper, TYPE, "jei.pioneers.processes", Items.BOOK, WIDTH, fitHeight());
        family(JeiDraw.Family.GUIDE);
        footerLines(1);
    }

    // ------------------------------------------------------------------ donnees

    private static JsonObject read() {
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(FILE);
            if (res.isPresent()) {
                try (Reader reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        } catch (Exception e) {
            Pioneers.LOGGER.warn("JEI : lecture de {} impossible", FILE, e);
        }
        return new JsonObject();
    }

    @Nullable
    private static Node node(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        //1.0.3-beta (correctif) : « product » est ecrit en texte simple ("pioneers:aluminium_ingot") : JEI plantait au chargement
        if (e.isJsonPrimitive()) {
            return new Node(e.getAsString(), null);
        }
        JsonObject o = e.getAsJsonObject();
        return new Node(o.get("id").getAsString(), o.has("tag") ? o.get("tag").getAsString() : null);
    }

    private static List<Node> nodes(JsonObject o, String key) {
        List<Node> out = new ArrayList<>();
        if (o.has(key)) {
            for (JsonElement e : o.getAsJsonArray(key)) {
                Node n = node(e);
                if (n != null) {
                    out.add(n);
                }
            }
        }
        return out;
    }

    @Nullable
    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static List<Step> steps(JsonObject o, String key) {
        List<Step> out = new ArrayList<>();
        if (o.has(key)) {
            for (JsonElement e : o.getAsJsonArray(key)) {
                JsonObject s = e.getAsJsonObject();
                String proc = s.get("proc").getAsString();
                String label = str(s, "label");
                String picto = str(s, "picto");
                out.add(new Step(s.get("station").getAsString(), s.get("tier").getAsInt(), proc, label != null ? label : proc,
                      picto != null ? picto : "chem", node(s.get("out")), nodes(s, "reagents"), nodes(s, "byproducts")));
            }
        }
        return out;
    }

    /** Vues JEI de tous les materiaux (meme algorithme que {@code process_layout.paginate}). */
    static List<Page> recipes() {
        return recipes(read());
    }

    /** Vues JEI de tous les materiaux d'apres le JSON (pur : banc {@code tools/jei/ProcessBench.java}). */
    static List<Page> recipes(JsonObject root) {
        List<Page> out = new ArrayList<>();
        if (!root.has("pages")) {
            return out;
        }
        for (JsonElement pe : root.getAsJsonArray("pages")) {
            JsonObject p = pe.getAsJsonObject();
            List<Route> routes = new ArrayList<>();
            for (JsonElement re : p.getAsJsonArray("routes")) {
                JsonObject r = re.getAsJsonObject();
                routes.add(new Route(r.get("band").getAsString(), r.get("tier").getAsInt(), r.get("origin").getAsString(),
                      r.has("first") && r.get("first").getAsBoolean(), str(r, "text"), str(r, "by"), node(r.get("start")), steps(r, "steps"),
                      steps(r, "side"), r.has("count") ? r.get("count").getAsInt() : 0, r.has("pips") ? r.get("pips").getAsInt() : 1,
                      str(r, "plus"), str(r, "minus")));
            }
            if (routes.isEmpty()) {
                continue;
            }
            List<List<Piece>> views = paginate(routes);
            int[] onPage = new int[routes.size()];
            Arrays.fill(onPage, -1);
            for (int v = 0; v < views.size(); v++) {
                for (Piece pc : views.get(v)) {
                    if (onPage[pc.route()] < 0) {
                        onPage[pc.route()] = v;
                    }
                }
            }
            Node product = node(p.get("product"));
            int total = p.get("total").getAsInt();
            List<Route> all = List.copyOf(routes);
            for (int i = 0; i < views.size(); i++) {
                out.add(new Page(p.get("id").getAsString(), p.get("family").getAsString(), str(p, "title"), product, p.get("age").getAsInt(),
                      total, all, List.copyOf(views.get(i)), i, views.size(), onPage));
            }
        }
        return out;
    }

    static int ovH(int n) {
        return n >= 2 ? n * OV_LINE + OV_GAP : 0;
    }

    /**
     * Pagination par hauteur : une voie qui ne tient pas dans la place restante passe a la vue suivante si elle tient dans une vue vide ;
     * sinon elle est coupee entre deux rangees (la suite commence la vue d'apres). Vue d'ensemble en tete de la premiere vue (>= 2 voies).
     */
    static List<List<Piece>> paginate(List<Route> routes) {
        List<List<Piece>> views = new ArrayList<>();
        List<Piece> cur = new ArrayList<>();
        int used = ovH(routes.size());
        for (int ri = 0; ri < routes.size(); ri++) {
            Route r = routes.get(ri);
            int rows = r.rows(), r0 = 0;
            boolean cont = false;
            while (r0 < rows) {
                int gap = cur.isEmpty() ? 0 : ROUTE_GAP;
                int full = r.pieceH(r0, rows, cont);
                if (used + gap + full <= viewH) {
                    cur.add(new Piece(ri, r0, rows, cont));
                    used += gap + full;
                    break;
                }
                if (!cur.isEmpty() && full <= viewH) {
                    views.add(cur);
                    cur = new ArrayList<>();
                    used = 0;
                    continue;
                }
                int k = 0;
                for (int m = rows - r0; m > 0; m--) {
                    if (used + gap + r.pieceH(r0, r0 + m, cont) <= viewH) {
                        k = m;
                        break;
                    }
                }
                if (k == 0) {
                    if (!cur.isEmpty() || used > 0) {
                        views.add(cur);
                        cur = new ArrayList<>();
                        used = 0;
                        continue;
                    }
                    k = 1;
                }
                cur.add(new Piece(ri, r0, r0 + k, cont));
                r0 += k;
                cont = true;
                views.add(cur);
                cur = new ArrayList<>();
                used = 0;
            }
        }
        if (!cur.isEmpty()) {
            views.add(cur);
        }
        return views;
    }

    // ------------------------------------------------------------------ geometrie (memes noms que process_layout.py)

    static int nodeX(int c) {
        return X0 + c * PITCH;
    }

    static int machX(int c) {
        return nodeX(c) - STUB - NODE;
    }

    static int mc(int c) {
        return machX(c) + 9;
    }

    static int startRoom() {
        return mc(1) - 2 - X0;
    }

    private static boolean overview(Page page) {
        return page.index() == 0 && page.routes().size() >= 2;
    }

    /** Hauts des morceaux de la vue. */
    private static int[] pieceTops(Page page) {
        int[] tops = new int[page.pieces().size()];
        int top = overview(page) ? ovH(page.routes().size()) : 0;
        for (int i = 0; i < tops.length; i++) {
            Piece pc = page.pieces().get(i);
            if (i > 0) {
                top += ROUTE_GAP;
            }
            tops[i] = top;
            top += page.routes().get(pc.route()).pieceH(pc.r0(), pc.r1(), pc.cont());
        }
        return tops;
    }

    /** Rangees de la vue et leurs ordonnees. */
    private static List<RowPos> rowsOf(Page page) {
        List<RowPos> out = new ArrayList<>();
        int[] tops = pieceTops(page);
        for (int i = 0; i < tops.length; i++) {
            Piece pc = page.pieces().get(i);
            Route r = page.routes().get(pc.route());
            int rg = r.hasReagents() ? RG_H : 0, rh = r.rowH(), ry0 = tops[i] + r.headH(pc.cont());
            for (int j = pc.r0(); j < pc.r1(); j++) {
                int rt = ry0 + (j - pc.r0()) * (rh + ROW_GAP), rgy = rt + r.labLines() * LINE + 1, ny = rgy + rg;
                out.add(new RowPos(r, pc.route(), j, rt, rgy, ny, ny + NODE + 1, j == pc.r0()));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ ingredients

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return it == null ? Items.AIR : it;
    }

    @Nullable
    private static Fluid fluidOf(Node n) {
        Fluid f = ForgeRegistries.FLUIDS.getValue(n.rl());
        return f == null || f == Fluids.EMPTY ? null : f;
    }

    @Nullable
    private static Gas gasOf(Node n) {
        Gas g = PioneersAPI.gas(n.rl());
        return g == null || g.isEmptyType() ? null : g;
    }

    /** Pose un noeud (objet, tag, fluide ou gaz) dans une case de 18 x 18 ; null si l'ingredient n'existe pas dans ce jeu. */
    @Nullable
    private static IRecipeSlotBuilder place(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, int y, @Nullable Node n) {
        if (n == null) {
            return null;
        }
        if (n.fluid()) {
            Fluid f = fluidOf(n);
            return f == null ? null : fluid(builder, role, x, y, 18, 18, f, 1000, 1000);
        }
        if (n.gas()) {
            Gas g = gasOf(n);
            return g == null ? null : gas(builder, role, x, y, 18, 18, GasStack.of(g, 1000), 1000);
        }
        if (n.tag() != null) {
            Ingredient ing = Ingredient.of(TagKey.create(Registries.ITEM, new ResourceLocation(n.tag())));
            if (ing.getItems().length > 0) {
                return builder.addSlot(role, x + 1, y + 1).setStandardSlotBackground().addIngredients(ing);
            }
        }
        Item it = item(n.id());
        return it == Items.AIR ? null : builder.addSlot(role, x + 1, y + 1).setStandardSlotBackground().addItemStack(new ItemStack(it));
    }

    /** Nom affiche d'un noeud. */
    static Component name(@Nullable Node n) {
        if (n == null) {
            return Component.empty();
        }
        if (n.fluid()) {
            Fluid f = fluidOf(n);
            return f == null ? Component.literal(n.id()) : f.getFluidType().getDescription();
        }
        if (n.gas()) {
            Gas g = gasOf(n);
            return g == null ? Component.literal(n.id()) : g.getName();
        }
        return new ItemStack(item(n.id())).getHoverName();
    }

    /** Objet qui represente un noeud en petit (fluide : son seau ; gaz : rien, une pastille de sa couleur). */
    private static ItemStack mini(Node n) {
        if (n.fluid()) {
            Fluid f = fluidOf(n);
            return f == null ? ItemStack.EMPTY : new ItemStack(f.getBucket());
        }
        if (n.gas()) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item(n.id()));
    }

    /** Rendu de la case d'une machine : le pictogramme de son procede (32 x 32 affiche a 16 x 16) ; infobulle = la machine. */
    record Picto(ResourceLocation texture) implements IIngredientRenderer<ItemStack> {

        @Override
        public void render(@NotNull GuiGraphics graphics, @NotNull ItemStack stack) {
            graphics.blit(texture, 0, 0, 16, 16, 0, 0, 32, 32, 32, 32);
        }

        @Override
        @SuppressWarnings("removal")
        public List<Component> getTooltip(@NotNull ItemStack stack, @NotNull TooltipFlag flag) {
            return List.of(stack.getHoverName());
        }
    }

    // ------------------------------------------------------------------ page

    /**
     * Une case visible d'une vue : role JEI, noeud, position (contenu), voie, etape (0 = depart ou objet repris en tete de rangee, k = sortie
     * de l'etape k), la rangee commence-t-elle la voie (depart) ?
     */
    record Slot(RecipeIngredientRole role, Node node, int x, int y, int route, int k, boolean start) {
    }

    /**
     * Cases visibles d'une vue (PUR : banc {@code tools/jei/ProcessBench.java}) : en tete de rangee le depart (ou l'objet repris), puis la
     * sortie de chaque etape ; la sortie de la DERNIERE etape d'une voie est en SORTIE (R), tout le reste en ENTREE (U).
     */
    static List<Slot> slots(Page page) {
        List<Slot> out = new ArrayList<>();
        for (RowPos rp : rowsOf(page)) {
            Route r = rp.route();
            int n = r.steps().size(), k0 = rp.row() * PER_ROW;
            Node first = k0 == 0 ? r.start() : r.steps().get(k0 - 1).out();
            if (first != null) {
                out.add(new Slot(k0 == n ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT, first, nodeX(0), rp.ny(), rp.ri(), k0, k0 == 0));
            }
            for (int c = 1; c <= PER_ROW; c++) {
                int k = k0 + c;
                if (k > n) {
                    break;
                }
                Node o = r.steps().get(k - 1).out();
                if (o != null) {
                    out.add(new Slot(k == n ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT, o, nodeX(c), rp.ny(), rp.ri(), k, false));
                }
            }
        }
        return out;
    }

    /**
     * 1.0.4-beta (lot 33, Guillaume : « Certaines pages de JEI manquent dans les procedes : pour le cuivre nous n'avons que la suite de la
     * methode 4 ») : INGREDIENTS INVISIBLES d'une vue (PUR). Cause du bug : JEI ne montre, pour R sur un objet, que les vues ou il est une
     * SORTIE, et pour U que celles ou il est une ENTREE ; or le produit n'etait une sortie que sur la vue qui porte la DERNIERE etape d'une
     * voie. Une voie coupee entre deux vues (fenetre de JEI basse : {@link #fitHeight} donne 130 a 190 px de contenu) perdait sa premiere
     * partie dans R (« seulement la suite de la methode 4 »), et U sur son depart perdait la suite. Desormais chaque vue d'un materiau porte
     * en SORTIE le produit de la page et le produit de chacune de ses voies, et en ENTREE tous les objets (depart, sorties intermediaires)
     * des voies qu'elle montre, meme en partie : R sur le produit ouvre TOUTES les vues du materiau, dans l'ordre ; U sur un objet d'une voie
     * ouvre toutes les vues de cette voie. Les noeuds deja visibles dans le meme role ne sont pas repetes.
     */
    static List<Node> hidden(Page page, RecipeIngredientRole role) {
        List<String> seen = new ArrayList<>();
        for (Slot s : slots(page)) {
            if (s.role() == role) {
                seen.add(s.node().id());
            }
        }
        List<Node> out = new ArrayList<>();
        List<Integer> shown = new ArrayList<>();
        for (Piece pc : page.pieces()) {
            if (!shown.contains(pc.route())) {
                shown.add(pc.route());
            }
        }
        if (role == RecipeIngredientRole.OUTPUT) {
            add(out, seen, page.product());
            for (int ri : shown) {
                List<Step> st = page.routes().get(ri).steps();
                if (!st.isEmpty()) {
                    add(out, seen, st.get(st.size() - 1).out());
                }
            }
        } else if (role == RecipeIngredientRole.INPUT) {
            for (int ri : shown) {
                Route r = page.routes().get(ri);
                add(out, seen, r.start());
                for (int k = 0; k + 1 < r.steps().size(); k++) {
                    add(out, seen, r.steps().get(k).out());
                }
            }
        }
        return out;
    }

    private static void add(List<Node> out, List<String> seen, @Nullable Node n) {
        if (n != null && !seen.contains(n.id())) {
            seen.add(n.id());
            out.add(n);
        }
    }

    /** Ingredient invisible (objet, tag, fluide ou gaz ; rien si l'ingredient n'existe pas dans ce jeu). */
    private static void invisible(IRecipeLayoutBuilder builder, RecipeIngredientRole role, Node n) {
        if (n.fluid()) {
            Fluid f = fluidOf(n);
            if (f != null) {
                builder.addInvisibleIngredients(role).addFluidStack(f, 1000);
            }
            return;
        }
        if (n.gas()) {
            Gas g = gasOf(n);
            if (g != null) {
                builder.addInvisibleIngredients(role).addIngredient(GasIngredient.TYPE, GasStack.of(g, 1000));
            }
            return;
        }
        if (n.tag() != null) {
            Ingredient ing = Ingredient.of(TagKey.create(Registries.ITEM, new ResourceLocation(n.tag())));
            if (ing.getItems().length > 0) {
                builder.addInvisibleIngredients(role).addIngredients(ing);
                return;
            }
        }
        Item it = item(n.id());
        if (it != Items.AIR) {
            builder.addInvisibleIngredients(role).addItemStack(new ItemStack(it));
        }
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, @NotNull Page page, @NotNull IFocusGroup focuses) {
        for (Slot sl : slots(page)) {
            Route r = page.routes().get(sl.route());
            int n = r.steps().size(), k = sl.k();
            boolean head = sl.x() == nodeX(0);
            IRecipeSlotBuilder slot = place(builder, sl.role(), sl.x(), sl.y(), sl.node());
            if (slot == null) {
                continue;
            }
            if (head) {
                Component tip = sl.start() ? Component.translatable("jei.pioneers.processes.start_of", startLabel(r))
                                : Component.translatable("jei.pioneers.processes.repeat");
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(tip.copy().withStyle(ChatFormatting.GRAY)));
            } else {
                Step s = r.steps().get(k - 1);
                boolean last = k == n;
                slot.addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(Component.translatable("jei.pioneers.processes.process", Component.translatable(s.proc())).withStyle(ChatFormatting.GRAY));
                    if (last) {
                        tooltip.add(Component.translatable("jei.pioneers.processes.final").withStyle(ChatFormatting.GOLD));
                    }
                });
            }
        }
        for (RowPos rp : rowsOf(page)) {
            Route r = rp.route();
            int n = r.steps().size(), k0 = rp.row() * PER_ROW;
            for (int c = 1; c <= PER_ROW; c++) {
                int k = k0 + c;
                if (k > n) {
                    break;
                }
                Step s = r.steps().get(k - 1);
                Item st = item(s.station());
                if (st != Items.AIR) {
                    builder.addSlot(RecipeIngredientRole.RENDER_ONLY, machX(c) + 1, rp.ny() + 1)
                          .setCustomRenderer(VanillaTypes.ITEM_STACK, new Picto(Pioneers.rl("textures/gui/jei/process/" + s.picto() + ".png")))
                          .addItemStack(new ItemStack(st)).addRichTooltipCallback((view, tooltip) -> stepLines(s, false).forEach(tooltip::add));
                }
            }
        }
        //1.0.4-beta (lot 33) : chaque vue joignable par R sur le produit et par U sur tout objet de ses voies (voir hidden)
        for (RecipeIngredientRole role : new RecipeIngredientRole[]{RecipeIngredientRole.OUTPUT, RecipeIngredientRole.INPUT}) {
            for (Node nd : hidden(page, role)) {
                invisible(builder, role, nd);
            }
        }
    }

    /** Lignes d'infobulle d'une etape : (machine), procede (nom complet), reactifs, sous-produits, rappel du clic. */
    private static List<Component> stepLines(Step s, boolean machine) {
        List<Component> out = new ArrayList<>();
        if (machine) {
            out.add(new ItemStack(item(s.station())).getHoverName().copy().withStyle(ChatFormatting.WHITE));
        }
        out.add(Component.translatable("jei.pioneers.processes.process", Component.translatable(s.proc())).withStyle(ChatFormatting.AQUA));
        if (!s.reagents().isEmpty()) {
            out.add(Component.translatable("jei.pioneers.processes.reagents", join(s.reagents())).withStyle(ChatFormatting.GREEN));
        }
        if (!s.byproducts().isEmpty()) {
            out.add(Component.translatable("jei.pioneers.processes.byproducts", join(s.byproducts())).withStyle(ChatFormatting.GOLD));
        }
        out.add(Component.translatable("jei.pioneers.processes.click").withStyle(ChatFormatting.DARK_GRAY));
        return out;
    }

    private static Component join(List<Node> nodes) {
        MutableComponent c = Component.empty();
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) {
                c.append(", ");
            }
            c.append(name(nodes.get(i)));
        }
        return c;
    }

    /** Nom long d'une voie (infobulle) : son nom (voies de processes.py) ou le poste qui finit la voie (voies du graphe). */
    private static Component routeName(Route r) {
        return r.text() != null ? Component.translatable(r.text() + ".name")
               : Component.translatable("jei.pioneers.processes.by", new ItemStack(item(r.by())).getHoverName());
    }

    /** Nom court d'une voie (titre) : ecrit a la main, sinon « Par <dernier procede> » (process_layout.route_name). */
    private static String shortName(Route r) {
        if (r.text() != null) {
            return Component.translatable(r.text() + ".short").getString();
        }
        String last = Component.translatable(r.steps().get(r.steps().size() - 1).label()).getString().replace("\n", " ").replace("- ", "-");
        return Component.translatable("jei.pioneers.processes.by", last).getString();
    }

    private static Component tierName(int tier) {
        return Component.translatable("industry.pioneers.tier." + Tiers.tier(tier).id());
    }

    /** Titre d'une voie (infobulle) : bande · tier, puis son nom. */
    private static Component routeTitle(Route r) {
        return Component.translatable("jei.pioneers.processes.band." + r.band()).append(" · ").append(tierName(r.tier())).append(" — ")
              .append(routeName(r));
    }

    /** Mot sous la case de depart (process_layout.start_label) : nom, premier (ou dernier) mot d'un minerai / matiere, mot de l'origine. */
    static String startLabel(Route r) {
        float room = startRoom();
        String word = Component.translatable("jei.pioneers.processes.from." + r.origin()).getString();
        String full = r.start() != null ? name(r.start()).getString() : word;
        String[] words = full.split(" ");
        String head = "first".equals(Component.translatable("jei.pioneers.processes.head_word").getString()) ? words[0] : words[words.length - 1];
        List<String> cands = new ArrayList<>();
        cands.add(full);
        if (head.length() >= 3 && !head.equals(full) && ("ore".equals(r.origin()) || "world".equals(r.origin()))) {
            cands.add(head);
        }
        cands.add(word);
        for (String s : cands) {
            if (font().width(s) * SCALE <= room) {
                return s;
            }
        }
        String best = word;
        for (String s : cands) {
            if (font().width(s) < font().width(best)) {
                best = s;
            }
        }
        return best;
    }

    /** Echelle d'un texte dans {@code room} px : SCALE, reduite jusqu'a SCALE_MIN (jamais coupe). */
    static float fit(String s, float room) {
        int w = font().width(s);
        return w * SCALE <= room ? SCALE : Math.max(SCALE_MIN, room / Math.max(1, w));
    }

    private static void str(GuiGraphics g, String s, float x, float y, float scale, int color) {
        text(g, Component.literal(s), x, y, scale, color);
    }

    private static void rounded(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0 + 1, y0, x1 - 1, y1, c);
        g.fill(x0, y0 + 1, x1, y1 - 1, c);
    }

    static int pillW(Route r) {
        return Math.round(font().width(tierName(r.tier())) * SCALE) + 19;
    }

    /** Pastille du tier : nom du tier (blanc sur la couleur du tier) et complexite en 3 points (allumes : 1 simple ... 3 tres complexe). */
    private static void pill(GuiGraphics g, Route r, int x, int y) {
        int tw = Math.round(font().width(tierName(r.tier())) * SCALE), w = pillW(r);
        rounded(g, x, y, x + w, y + TAG_H, Tiers.tier(r.tier()).color | 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x40FFFFFF);
        text(g, tierName(r.tier()), x + 3, y + 1.5F, SCALE, 0xFFFFFFFF);
        int px = x + 3 + tw + 3;
        for (int j = 0; j < 3; j++) {
            g.fill(px + j * 4, y + 3, px + j * 4 + 3, y + 6, j < r.pips() ? 0xFFFFFFFF : PIP_OFF);
        }
    }

    /** Numero de la voie : plein si la voie est detaillee sur cette vue, en creux sinon (vue d'ensemble). */
    private static void num(GuiGraphics g, int n, int x, int y, boolean here) {
        if (here) {
            rounded(g, x, y, x + NUM_W, y + TAG_H, NUM_BG);
        } else {
            rounded(g, x, y, x + NUM_W, y + TAG_H, NUM_EDGE);
            rounded(g, x + 1, y + 1, x + NUM_W - 1, y + TAG_H - 1, JeiDraw.PANEL);
        }
        String s = String.valueOf(n);
        str(g, s, x + (NUM_W - font().width(s) * SCALE) / 2F, y + 1.5F, SCALE, here ? JeiDraw.LIGHT : JeiDraw.FAINT);
    }

    static String tagText(String key, boolean plus) {
        return (plus ? "+ " : "− ") + Component.translatable(key).getString();
    }

    /** Pastille de force ou de faiblesse ; renvoie son bord droit. Texte reduit seulement si {@code room} l'exige (jamais coupe). */
    private static int tag(GuiGraphics g, String s, int x, int y, int color, int room) {
        int w = Math.round(font().width(s) * SCALE) + 6;
        float scale = SCALE;
        if (w > room) {
            scale = Math.max(SCALE_MIN, (room - 6) / (float) Math.max(1, font().width(s)));
            w = Math.round(font().width(s) * scale) + 6;
        }
        rounded(g, x, y, x + w, y + TAG_H, TAG_BG);
        str(g, s, x + 3, y + 1.5F, scale, color);
        return x + w;
    }

    static String stepsText(int n) {
        return n == 1 ? Component.translatable("jei.pioneers.processes.step_one").getString()
               : Component.translatable("jei.pioneers.processes.steps", n).getString();
    }

    @Override
    protected void drawContent(@NotNull Page page, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        List<Route> routes = page.routes();
        if (overview(page)) {
            for (int i = 0; i < routes.size(); i++) {
                Route r = routes.get(i);
                int y = i * OV_LINE;
                boolean here = page.onPage()[i] == page.index();
                num(graphics, i + 1, 0, y, here);
                pill(graphics, r, NUM_W + 2, y);
                int x = NUM_W + 2 + pillW(r) + 3;
                String right = stepsText(r.steps().size());
                float rw = font().width(right) * SCALE;
                str(graphics, right, WIDTH - rw, y + 1, SCALE, here ? JeiDraw.DIM : JeiDraw.FAINT);
                if (r.plus() != null) {
                    tag(graphics, tagText(r.plus(), true), x, y, GOOD, (int) (WIDTH - rw - 4 - x));
                }
            }
            graphics.fill(0, routes.size() * OV_LINE, WIDTH, routes.size() * OV_LINE + 1, SEP);
        }
        int[] tops = pieceTops(page);
        for (int i = 0; i < tops.length; i++) {
            Piece pc = page.pieces().get(i);
            Route r = routes.get(pc.route());
            int ty = tops[i];
            num(graphics, pc.route() + 1, 0, ty, true);
            String nm = shortName(r);
            int x;
            if (pc.cont()) {
                nm = Component.translatable("jei.pioneers.processes.cont").getString() + " " + nm;
                x = NUM_W + 3;
            } else {
                pill(graphics, r, NUM_W + 2, ty);
                x = NUM_W + 2 + pillW(r) + 4;
            }
            str(graphics, nm, x, ty + 1, fit(nm, WIDTH - x), r.first() ? WARN : TEXT);
            if (r.hasTags() && !pc.cont()) {
                int tx = NUM_W + 2;
                if (r.plus() != null) {
                    tx = tag(graphics, tagText(r.plus(), true), tx, ty + TITLE_H, GOOD, WIDTH - tx) + 3;
                }
                if (r.minus() != null) {
                    tag(graphics, tagText(r.minus(), false), tx, ty + TITLE_H, JeiDraw.BAD, WIDTH - tx);
                }
            }
        }
        for (RowPos rp : rowsOf(page)) {
            drawRow(graphics, rp);
        }
    }

    /** Une rangee : depart nomme, fleche de retour, puis pour chaque etape fleche, machine, fleche, libelle, reactifs, sous-produits. */
    private static void drawRow(GuiGraphics g, RowPos rp) {
        Route r = rp.route();
        int n = r.steps().size(), k0 = rp.row() * PER_ROW, ny = rp.ny(), lab = r.labLines();
        if (k0 == 0) {
            String s = startLabel(r);
            str(g, s, X0, rp.bpy(), fit(s, startRoom()), JeiDraw.DIM);
        }
        if (!rp.firstOfPiece()) {
            //fleche de retour : du bas du dernier objet de la rangee d'avant, sous elle, puis a gauche jusqu'a cet objet repris
            int pny = ny - r.rowH() - ROW_GAP, yb = pny + NODE + 1 + MINI + 2, cx = nodeX(0) + 9;
            g.fill(RETURN_X, pny + 18, RETURN_X + 1, yb + 1, JeiDraw.ACCENT);
            g.fill(cx, yb, RETURN_X + 1, yb + 1, JeiDraw.ACCENT);
            g.fill(cx, yb, cx + 1, ny - 1, JeiDraw.ACCENT);
            g.fill(cx - 2, ny - 3, cx + 3, ny - 2, JeiDraw.ACCENT);
            g.fill(cx - 1, ny - 2, cx + 2, ny - 1, JeiDraw.ACCENT);
        }
        for (int c = 1; c <= PER_ROW; c++) {
            int k = k0 + c;
            if (k > n) {
                break;
            }
            Step s = r.steps().get(k - 1);
            int mx = machX(c), m = mc(c);
            g.fill(nodeX(c - 1) + 18, ny + 9, mx, ny + 10, JeiDraw.ACCENT);
            //la machine SUR la fleche : cadre du tier de l'etape (laiton pour un etabli), fond sombre ; le pictogramme est la case (Picto)
            //1.0.4-beta (lot 32) : etape faite en salle blanche (usine de semi-conducteurs) : cadre gris argent, jamais la couleur d'un tier
            g.fill(mx, ny, mx + 18, ny + 18, fr.pioneers.industry.multi.Cleanroom.byController(s.station()) != null ? 0xFF000000 | JeiDraw.Family.CLEANROOM.rgb
                                             : s.tier() >= 0 ? Tiers.tier(s.tier()).color | 0xFF000000 : JeiDraw.SLOT_CAT);
            g.fill(mx + 1, ny + 1, mx + 17, ny + 17, JeiDraw.SLOT_FILL);
            arrowHead(g, mx + 18, nodeX(c) - (k == n ? 1 : 0), ny);
            if (k == n) {
                int x = nodeX(c) - 1, y = ny - 1;
                g.fill(x, y, x + 20, y + 1, GOLD);
                g.fill(x, y + 19, x + 20, y + 20, GOLD);
                g.fill(x, y, x + 1, y + 20, GOLD);
                g.fill(x + 19, y, x + 20, y + 20, GOLD);
            }
            List<String> lines = s.lines();
            for (int i = 0; i < lines.size(); i++) {
                String ln = lines.get(i);
                float sc = fit(ln, LABEL_ROOM);
                str(g, ln, m - font().width(ln) * sc / 2F, rp.rt() + (lab - lines.size() + i) * LINE, sc, TEXT);
            }
            List<Node> rgs = s.reagents().subList(0, Math.min(3, s.reagents().size()));
            if (!rgs.isEmpty()) {
                int w = 6 + rgs.size() * MINI_STEP - 1, x0 = m - w / 2, y = rp.rgy();
                g.fill(x0, y + 3, x0 + 5, y + 4, REAGENT);
                g.fill(x0 + 2, y + 1, x0 + 3, y + 6, REAGENT);
                for (int i = 0; i < rgs.size(); i++) {
                    miniIcon(g, rgs.get(i), x0 + 6 + i * MINI_STEP, y);
                }
            }
            List<Node> bps = s.byproducts().subList(0, Math.min(3, s.byproducts().size()));
            if (!bps.isEmpty()) {
                int y = rp.bpy();
                g.fill(m, y, m + 1, y + 5, BYPRODUCT);
                g.fill(m - 1, y + 3, m + 2, y + 4, BYPRODUCT);
                for (int i = 0; i < bps.size(); i++) {
                    miniIcon(g, bps.get(i), m + 3 + i * MINI_STEP, y);
                }
            }
        }
    }

    /** Fleche horizontale de {@code x0} a {@code x1} (pointe comprise) au milieu d'une case d'objet posee en {@code y}. */
    private static void arrowHead(GuiGraphics graphics, int x0, int x1, int y) {
        graphics.fill(x0, y + 9, x1 - 2, y + 10, JeiDraw.ACCENT);
        graphics.fill(x1 - 3, y + 7, x1 - 2, y + 12, JeiDraw.ACCENT);
        graphics.fill(x1 - 2, y + 8, x1 - 1, y + 11, JeiDraw.ACCENT);
        graphics.fill(x1 - 1, y + 9, x1, y + 10, JeiDraw.ACCENT);
    }

    /** Pictogramme d'un reactif ou d'un sous-produit (8 x 8) : objet ou seau reduit, pastille de couleur pour un gaz. */
    private static void miniIcon(GuiGraphics graphics, Node n, int x, int y) {
        ItemStack st = mini(n);
        if (!st.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(MINI / 16F, MINI / 16F, 1);
            graphics.renderItem(st, 0, 0);
            graphics.pose().popPose();
        } else if (n.gas()) {
            Gas g = gasOf(n);
            int rgb = g == null ? 0x8A98A6 : g.getTint();
            graphics.fill(x + 1, y + 1, x + MINI - 1, y + MINI - 1, 0xFF000000 | rgb);
            graphics.fill(x + 1, y + 1, x + MINI - 1, y + 2, 0x60FFFFFF);
        }
    }

    @Override
    protected List<Component> contentTooltip(@NotNull Page page, @NotNull IRecipeSlotsView view, double mouseX, double mouseY) {
        List<Route> routes = page.routes();
        if (overview(page) && mouseY >= 0 && mouseY < routes.size() * OV_LINE) {
            int i = (int) (mouseY / OV_LINE);
            List<Component> out = routeLines(routes.get(i));
            if (page.onPage()[i] != page.index()) {
                out.add(Component.translatable("jei.pioneers.processes.on_page", page.onPage()[i] + 1).withStyle(ChatFormatting.YELLOW));
            }
            return out;
        }
        int[] tops = pieceTops(page);
        for (int i = 0; i < tops.length; i++) {
            Piece pc = page.pieces().get(i);
            Route r = routes.get(pc.route());
            if (mouseY >= tops[i] && mouseY < tops[i] + TITLE_H) {
                return routeLines(r);
            }
            if (r.hasTags() && !pc.cont() && mouseY >= tops[i] + TITLE_H && mouseY < tops[i] + TITLE_H + TAG_H) {
                return traitLines(r);
            }
        }
        for (RowPos rp : rowsOf(page)) {
            Route r = rp.route();
            int n = r.steps().size(), k0 = rp.row() * PER_ROW;
            if (k0 == 0 && in(mouseX, mouseY, X0, rp.bpy(), startRoom(), MINI)) {
                return List.of(Component.translatable("jei.pioneers.processes.origin." + r.origin()).withStyle(ChatFormatting.GRAY));
            }
            for (int c = 1; c <= PER_ROW; c++) {
                int k = k0 + c;
                if (k > n) {
                    break;
                }
                Step s = r.steps().get(k - 1);
                int m = mc(c);
                if (in(mouseX, mouseY, m - LABEL_ROOM / 2, rp.rt(), LABEL_ROOM, r.labLines() * LINE)) {
                    return stepLines(s, true);
                }
                List<Node> rgs = s.reagents();
                if (!rgs.isEmpty()) {
                    int cnt = Math.min(3, rgs.size()), w = 6 + cnt * MINI_STEP - 1, x0 = m - w / 2;
                    for (int i = 0; i < cnt; i++) {
                        if (in(mouseX, mouseY, x0 + 6 + i * MINI_STEP, rp.rgy(), MINI, MINI)) {
                            return List.of(name(rgs.get(i)), Component.translatable("jei.pioneers.processes.reagents", "").withStyle(ChatFormatting.GREEN));
                        }
                    }
                    if (in(mouseX, mouseY, x0, rp.rgy(), 6, MINI)) {
                        return List.of(Component.translatable("jei.pioneers.processes.reagents", join(rgs)).withStyle(ChatFormatting.GREEN));
                    }
                }
                List<Node> bps = s.byproducts();
                if (!bps.isEmpty()) {
                    int cnt = Math.min(3, bps.size());
                    for (int i = 0; i < cnt; i++) {
                        if (in(mouseX, mouseY, m + 3 + i * MINI_STEP, rp.bpy(), MINI, MINI)) {
                            return List.of(name(bps.get(i)), Component.translatable("jei.pioneers.processes.byproducts", "").withStyle(ChatFormatting.GOLD));
                        }
                    }
                    if (in(mouseX, mouseY, m - 1, rp.bpy(), 4, MINI)) {
                        return List.of(Component.translatable("jei.pioneers.processes.byproducts", join(bps)).withStyle(ChatFormatting.GOLD));
                    }
                }
            }
        }
        return List.of();
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Force et faiblesse en entier (voies de processes.py) ou les traits calcules (voies du graphe). */
    private static List<Component> traitLines(Route r) {
        List<Component> out = new ArrayList<>();
        if (r.text() != null) {
            out.add(Component.translatable("jei.pioneers.processes.plus", Component.translatable(r.text() + ".plus")).withStyle(ChatFormatting.GREEN));
            out.add(Component.translatable("jei.pioneers.processes.minus", Component.translatable(r.text() + ".minus")).withStyle(ChatFormatting.RED));
            return out;
        }
        if (r.plus() != null) {
            out.add(Component.literal(tagText(r.plus(), true)).withStyle(ChatFormatting.GREEN));
        }
        if (r.minus() != null) {
            out.add(Component.literal(tagText(r.minus(), false)).withStyle(ChatFormatting.RED));
        }
        return out;
    }

    /** Infobulle du titre d'une voie : nom complet, origine, force, faiblesse, nombre d'etapes, complexite, etapes a cote. */
    private static List<Component> routeLines(Route r) {
        List<Component> out = new ArrayList<>();
        out.add(routeTitle(r));
        out.add(Component.translatable("jei.pioneers.processes.origin." + r.origin()).withStyle(ChatFormatting.GRAY));
        if (r.first()) {
            out.add(Component.translatable("jei.pioneers.processes.first").withStyle(ChatFormatting.GOLD));
        }
        out.addAll(traitLines(r));
        out.add(Component.translatable("jei.pioneers.processes.steps", r.count()).withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable("jei.pioneers.processes.pips", Component.translatable("jei.pioneers.processes.band." + r.band()))
              .withStyle(ChatFormatting.GRAY));
        if (!r.side().isEmpty()) {
            MutableComponent c = Component.empty();
            for (int i = 0; i < r.side().size(); i++) {
                Step s = r.side().get(i);
                if (i > 0) {
                    c.append(" · ");
                }
                c.append(Component.translatable(s.proc())).append(" (").append(name(s.out())).append(")");
            }
            out.add(Component.translatable("jei.pioneers.processes.side", c).withStyle(ChatFormatting.DARK_AQUA));
        }
        return out;
    }

    @Override
    protected Component headerTitle(Page page) {
        //metaux : le nom du materiau (« Aluminium », cle process.pioneers.page.<id>) ; ailleurs : le nom du produit
        return Component.translatable("jei.pioneers.processes.title", page.title() != null ? Component.translatable(page.title()) : name(page.product()));
    }

    @Override
    protected JeiDraw.Badge badge(Page page) {
        return JeiDraw.Badge.tier(Math.max(0, Math.min(3, page.age())));
    }

    @Override
    protected List<JeiDraw.Chip> chips(Page page) {
        List<JeiDraw.Chip> out = new ArrayList<>();
        int shown = page.routes().size();
        Component routes = shown == 1 ? Component.translatable("jei.pioneers.processes.routes_one")
                           : Component.translatable("jei.pioneers.processes.routes", shown);
        out.add(JeiDraw.Chip.of(JeiDraw.Pic.INFO, routes, TEXT, Component.translatable("jei.pioneers.processes.routes_tip")));
        if (page.pages() > 1) {
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.INFO, Component.translatable("jei.pioneers.processes.page", page.index() + 1, page.pages()), TEXT_LIGHT));
        }
        if (page.total() > shown) {
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.WARN, Component.translatable("jei.pioneers.processes.more", page.total() - shown), WARN));
        }
        return out;
    }
}
