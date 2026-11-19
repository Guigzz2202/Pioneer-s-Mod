package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.logistics.warehouse.client.CounterClient;
import fr.pioneers.core.Texts;
import fr.pioneers.core.network.PacketWarehouse;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.logistics.warehouse.CounterCart;
import fr.pioneers.logistics.warehouse.CounterTile;
import fr.pioneers.logistics.warehouse.CounterUnit;
import fr.pioneers.logistics.warehouse.Parcel;
import fr.pioneers.logistics.warehouse.StockKey;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 1) : vue {@code warehouse} du COMPTOIR D'ENTREPOT dans le cadre v2 (inventaire dans le cadre, titre et pastille d'age).
 * <p>1.0.4-beta (lot 3, Guillaume : « on devrait utiliser ce comptoir comme AE2 mais en cliquant, l'item part en zone tampon avec un bouton
 * commander ») : facon TERMINAL AE2 avec PANIER.
 * <p>1.0.5-beta (lot 17, Guillaume : « interface plus petite (place pour JEI), Maj + clic sur une recette JEI, joueur assiste au maximum,
 * fluide et performant ») : 240 px de large au lieu de 350 (la colonne de JEI reste visible) ; rien de perdu :
 * <ul>
 *     <li>a gauche : recherche (clic = saisie, clic droit = vider ; Echap la vide aussi ; « @mod »), tri cyclique (quantite / nom / mod),
 *     GRILLE du stock (8 x 6, molette et barre, quantites abregees, infobulle : dans l'entrepot, au panier, en route, disponible) ; cliquer un
 *     objet l'AJOUTE AU PANIER : clic = + 1 pile, Maj = + 1, Ctrl = + 1 colis (4 piles), clic droit = - 1 pile (Maj : - 1) ;</li>
 *     <li>a droite : la SORTIE (3 x 3, a prendre), le bouton PANIER (volet par-dessus la grille : lignes avec icone, nom en entier, quantite
 *     et piles, - / + / x, molette ; « Vider » ; total ; « Livrer a » : la sortie du comptoir ou une interface de sortie du reseau),
 *     « COMMANDER » (Entree aussi), la carte EN ROUTE (corde, colis, file ou POURQUOI ca attend ; clic : volet avec le detail et
 *     « Annuler la file ») ; en bas la ligne d'aide, ou l'avis du dernier « Commander » / de la derniere recette de JEI.</li>
 * </ul>
 * Le stock arrive PAR DIFFERENCES et se garde en table ({@link CounterClient.Stock}) : une lecture par case et par image ; la liste filtree et
 * triee n'est refaite que si le stock, la recherche ou le tri changent (noms replies gardes par type). Le panier est tenu par le SERVEUR
 * (par joueur : {@link CounterCart}) ; la vue garde une copie locale modifiee aussitot et reprend celle du serveur des que celui-ci a traite
 * sa derniere modification (numero de paquet {@code seq}).
 * <p>Vue {@code warehouse_io} (1.0.5-beta, lot 17) : INTERFACES d'entree et de sortie (9 cases, etat de la station, regles « garder N »).
 */
final class WarehouseViews {

    static final int GRID_X = CounterTile.GRID_X, GRID_Y = CounterTile.GRID_Y, COLS = CounterTile.COLS, ROWS = CounterTile.ROWS, CELL = CounterTile.CELL;
    private static final int WOOD = 0xFFB08A50, GREEN = 0xFF6EDC5A, RED = 0xFFF05050, ORANGE = 0xFFF0B040, CELL_BG = 0xFF161D24, CELL_HOVER = 0xFF2A3440,
          AQUA = 0xFF7FD8E8;
    private static final Pattern MARKS = Pattern.compile("\\p{M}"), SPACES = Pattern.compile("\\s+");
    /** Tris : quantite, nom, mod. */
    private static final String[] SORTS = {"count", "name", "mod"};
    /** Duree d'affichage d'un avis (ms). */
    private static final long NOTICE_MS = 7000;
    /** 1.0.5-beta (lot 17) : volets (aucun, panier, en route). */
    static final int NO_PANEL = 0, CART_PANEL = 1, ROUTE_PANEL = 2;

    static final MachineScreens.View VIEW = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            gui.views().warehouse().draw(g, mouseX, mouseY, tile);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity tile) {
            return WarehouseViews.infoRows(gui);
        }

        @Override
        public void infoExtra(GuiMachine gui, GuiGraphics g, BlockEntity tile, int x, int y, int w, int bottom) {
            WarehouseViews.infoExtra(gui, g, x, y, w, bottom);
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.warehouse.info.help";
        }
    };

    /** 1.0.5-beta (lot 17) : vue des interfaces d'entree et de sortie. */
    static final MachineScreens.View IO_VIEW = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            gui.views().warehouse().drawIo(g, mouseX, mouseY, tile);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity tile) {
            return WarehouseViews.infoRows(gui);
        }

        @Override
        public void infoExtra(GuiMachine gui, GuiGraphics g, BlockEntity tile, int x, int y, int w, int bottom) {
            WarehouseViews.infoExtra(gui, g, x, y, w, bottom);
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.warehouse.io.info.help";
        }
    };

    /** Onglet Infos : age, caisses, poulies, reseau (lot 34) ; lot 17 : ce qui fait attendre. */
    static List<StandardView.InfoRow> infoRows(GuiMachine gui) {
        List<StandardView.InfoRow> rows = new ArrayList<>();
        rows.add(new StandardView.InfoRow("age", Component.translatable("tooltip.pioneers.api.age.bronze"), StandardView.ageColor("bronze")));
        rows.add(new StandardView.InfoRow("crates", Component.translatable("gui.pioneers.warehouse.info.crates", gui.value(CounterUnit.CRATES)), PiMenuScreen.TEXT));
        rows.add(new StandardView.InfoRow("pulleys", Component.translatable("gui.pioneers.warehouse.info.pulleys", gui.value(CounterUnit.LINKED)), PiMenuScreen.TEXT));
        //1.0.4-beta (lot 34) : etat du raccordement en clair (le detail et le remede sont dessous, infoExtra)
        int code = diagCode(gui);
        rows.add(new StandardView.InfoRow("network", Component.translatable("gui.pioneers.warehouse.diag." + CounterUnit.DIAG_KEYS[code]), diagColor(code)));
        int wait = waitCode(gui);
        if (wait != CounterUnit.W_NONE) {
            rows.add(new StandardView.InfoRow("waiting", Component.translatable("gui.pioneers.warehouse.wait." + CounterUnit.WAIT_KEYS[wait]), ORANGE));
        }
        return rows;
    }

    /** 1.0.4-beta (lot 34, Guillaume : « mon comptoir ne se connecte pas au reseau ») : ce qui manque et ce qu'il faut faire. */
    static void infoExtra(GuiMachine gui, GuiGraphics g, int x, int y, int w, int bottom) {
        int code = diagCode(gui);
        g.fill(x - 2, y, x + w + 2, bottom, 0xFF0D1218);
        g.fill(x - 2, y, x - 1, bottom, diagColor(code));
        int lines = GuiDraw.wrapped(g, gui.font(), diagDetail(gui), x + 2, y + 3, 0.62F, diagColor(code), w - 6, 3, 7);
        if (code != CounterUnit.D_OK) {
            GuiDraw.wrapped(g, gui.font(), Component.translatable("gui.pioneers.warehouse.diag." + CounterUnit.DIAG_KEYS[code] + ".fix"), x + 2,
                  y + 5 + lines * 7, 0.6F, PiMenuScreen.TEXT, w - 6, Math.max(1, (bottom - y - 7 - lines * 7) / 7), 7);
        }
    }

    /** 1.0.4-beta (lot 34) : code du diagnostic (borne : un serveur plus recent ne fait pas planter l'ecran). */
    static int diagCode(GuiMachine gui) {
        long c = gui.value(CounterUnit.DIAG);
        return c < 0 || c >= CounterUnit.DIAG_KEYS.length ? CounterUnit.D_NO_PULLEY : (int) c;
    }

    /** 1.0.5-beta (lot 17) : pourquoi ca attend (borne). */
    static int waitCode(GuiMachine gui) {
        long c = gui.value(CounterUnit.WAIT);
        return c < 0 || c >= CounterUnit.WAIT_KEYS.length ? CounterUnit.W_NONE : (int) c;
    }

    static boolean linked(GuiMachine gui) {
        int c = diagCode(gui);
        return c == CounterUnit.D_OK || c == CounterUnit.D_STILL;
    }

    static int diagColor(int code) {
        return code == CounterUnit.D_OK ? GREEN : code == CounterUnit.D_STILL || code == CounterUnit.D_LOADING ? ORANGE : RED;
    }

    /** Position (BlockPos.asLong) en « x y z ». */
    private static Component at(long pos) {
        return at(BlockPos.of(pos));
    }

    private static Component at(BlockPos p) {
        return Component.literal(p.getX() + " " + p.getY() + " " + p.getZ());
    }

    /** Detail du diagnostic, avec ses positions ou nombres ({@code CounterUnit.diagnose}). */
    static Component diagDetail(GuiMachine gui) {
        int code = diagCode(gui);
        long a = gui.value(CounterUnit.DIAG_A), b = gui.value(CounterUnit.DIAG_B), c = gui.value(CounterUnit.DIAG_C);
        String key = "gui.pioneers.warehouse.diag." + CounterUnit.DIAG_KEYS[code] + ".detail";
        return switch (code) {
            case CounterUnit.D_OK -> Component.translatable(key, gui.value(CounterUnit.CRATES), gui.value(CounterUnit.LINKED));
            case CounterUnit.D_COLUMN, CounterUnit.D_ONE_WAY, CounterUnit.D_OFF_HATCH -> Component.translatable(key, at(a), at(b));
            case CounterUnit.D_ROPE_BLOCKED -> Component.translatable(key, at(a), at(b), at(c));
            case CounterUnit.D_UNDER_WHEEL -> Component.translatable(key, at(a), at(c));
            case CounterUnit.D_TOO_HIGH -> Component.translatable(key, at(a), b, c);
            case CounterUnit.D_NO_CRATE -> Component.translatable(key, gui.value(CounterUnit.LINKED));
            case CounterUnit.D_NO_PULLEY, CounterUnit.D_LOADING -> Component.translatable(key);
            default -> Component.translatable(key, at(a));
        };
    }

    /** Ligne du panier local. */
    private static final class Line {
        final ItemStack proto;
        int count;

        Line(ItemStack proto, int count) {
            this.proto = proto;
            this.count = count;
        }
    }

    private final GuiMachine gui;
    private int scroll, sort, cartScroll, panel;
    private String query = "";
    private ScaledEditBox search;
    private int listStamp = -1, listSort = -1;
    private String listQuery;
    private List<PacketWarehouse.Entry> shown = List.of();
    /**
     * 1.0.7 (Guillaume, 18/11/2026 : « le tri par mods met en evidence les mods auquel les items appartiennent ») : rangees de la grille,
     * {debut dans shown, longueur, HEADER ou ITEMS} ; tri par mod : une rangee d'en-tete (nom du mod, nombre de sortes) devant chaque mod,
     * chaque mod commence sa rangee.
     */
    private List<int[]> rows = List.of();
    private static final int ITEMS = 0, HEADER = 1;
    /** Nom affiche des mods (nom du mod, sinon son identifiant), garde par identifiant. */
    private final Map<String, String> modNames = new HashMap<>();
    /** Noms replies (recherche, tri par nom) gardes par type : calcules une fois. */
    private final Map<StockKey, String> folded = new HashMap<>();
    /** Panier local ; numero du prochain paquet ; dernier paquet envoye (NONE : rien en attente). */
    private final List<Line> cart = new ArrayList<>();
    private static final int NONE = Integer.MIN_VALUE;
    private int seq = (int) (Util.getNanos() & 0x3FFFFFFF), pending = NONE, noticeId = NONE;
    private long noticeUntil;
    private Component notice = Component.empty();
    private int noticeTone;
    /**
     * 1.0.5-beta (lot 22, retour 24 : « voir les recettes demandees et ajuster le nombre de fabrications via la molette ») : RECETTES DEMANDEES
     * du panier (vue du serveur : produit, fabrications, ce qui manque, ingredients au panier), en tete de la liste du volet ; nombre choisi
     * a la molette en attente de la reponse du serveur (par numero de recette).
     */
    private List<PacketWarehouse.CraftView> crafts = List.of();
    private final Map<Integer, Integer> craftPending = new HashMap<>();
    /** Derniere position de la souris (panneau) : la molette sait ou elle tourne. */
    private int lastX, lastY;
    @Nullable
    private CounterTile current;

    WarehouseViews(GuiMachine gui) {
        this.gui = gui;
        sort = Math.min(SORTS.length - 1, fr.pioneers.logistics.warehouse.client.WarehouseClientConfig.sort().ordinal()); //1.0.7 : tri garde
    }

    void init(BlockEntity tile) {
        search = null;
        CounterTile ct = StorageViews.as(CounterTile.class, tile);
        current = ct;
        if (ct != null && ct.role() == CounterTile.Role.COUNTER) {
            StandardLayout.Rect r = CounterTile.SEARCH;
            //1.0.4-beta (lot 34) : champ a la taille des autres textes (le champ vanilla chevauchait le cadre), comme celui des etablis
            search = gui.widget(new ScaledEditBox(gui.font(), gui.left() + r.x() + 5, gui.top() + r.y() + 2, r.w() - 10, r.h() - 3,
                  Component.translatable("gui.pioneers.warehouse.search")));
            search.setBordered(false);
            search.setMaxLength(40);
            search.setTextColor(0xFFE0F5EA);
            search.setHint(Component.translatable("gui.pioneers.warehouse.search").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query);
            search.setResponder(text -> {
                query = text;
                scroll = 0;
            });
            search.visible = panel == NO_PANEL;
            //Le panier local est lu par la poignee de transfert de JEI (ce qui manque en rouge)
            CounterClient.cart(ct.getBlockPos(), this::inCart);
        }
    }

    boolean clearSearch() {
        if (search != null && !search.getValue().isEmpty()) {
            search.setValue("");
            return true;
        }
        return false;
    }

    void removed() {
        if (current != null && current.role() == CounterTile.Role.COUNTER) {
            CounterClient.cart(current.getBlockPos(), null);
        }
    }

    private void open(int which) {
        panel = which;
        if (search != null) {
            search.visible = panel == NO_PANEL;
            if (panel != NO_PANEL) {
                search.setFocused(false);
            }
        }
    }

    /**
     * 1.0.5-beta (lot 17) : clavier du comptoir : Entree = « Commander » (dans la recherche : quitte d'abord la recherche) ; Echap ferme
     * d'abord un volet ouvert.
     */
    boolean keyPressed(BlockEntity be, int key) {
        CounterTile tile = StorageViews.as(CounterTile.class, be);
        if (tile == null || tile.role() != CounterTile.Role.COUNTER) {
            return false;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && panel != NO_PANEL) {
            open(NO_PANEL);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            if (search != null && search.isFocused()) {
                search.setFocused(false);
                return true;
            }
            if (!cart.isEmpty()) {
                send(tile, PacketWarehouse.CartEdit.ORDER, ItemStack.EMPTY, 0);
            } else {
                say(Component.translatable("gui.pioneers.warehouse.order.empty"), 0);
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ souris

    /**
     * Molette : sur une ligne du panier (volet) = +- 1 (Maj : +- 1 pile) ; ailleurs dans le volet = defilement ; sur la grille = defilement ;
     * sur une regle de l'interface de sortie = +- 1 (Maj : +- 1 pile).
     */
    boolean scrolled(BlockEntity be, double delta) {
        CounterTile tile = StorageViews.as(CounterTile.class, be);
        if (tile == null) {
            return false;
        }
        int dir = (int) Math.signum(delta);
        if (tile.role() == CounterTile.Role.OUTLET) {
            int row = keepRowAt(tile, lastX, lastY);
            if (row >= 0) {
                CounterUnit.Keep k = tile.counter().keeps().get(row);
                keep(tile, k.proto, k.count + dir * (Screen.hasShiftDown() ? 1 : k.proto.getMaxStackSize()));
                return true;
            }
            return false;
        }
        if (tile.role() != CounterTile.Role.COUNTER) {
            return false;
        }
        if (panel == CART_PANEL && inside(CounterTile.VOLET)) {
            int row = cartRowAt(lastX, lastY);
            if (row >= 0 && row < crafts.size()) {
                //1.0.5-beta (lot 22) : sur une recette demandee : +- 1 fabrication (Maj : +- 10), entre 1 et 64
                PacketWarehouse.CraftView cv = crafts.get(row);
                setCraft(tile, cv, Math.max(1, craftTimes(cv) + dir * (Screen.hasShiftDown() ? 10 : 1)));
            } else if (row >= 0) {
                Line l = cart.get(row - crafts.size());
                setLine(tile, l.proto, l.count + dir * (Screen.hasShiftDown() ? l.proto.getMaxStackSize() : 1));
            } else {
                cartScroll = Math.max(0, Math.min(maxCartScroll(), cartScroll - dir));
            }
            return true;
        }
        if (panel != NO_PANEL) {
            return inside(CounterTile.VOLET);
        }
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - ROWS), scroll - dir));
        return true;
    }

    /** Clic droit (panneau) : grille = - 1 pile (Maj : - 1) ; ligne du panier = - 1 pile ; recherche = vider ; regle = - 1 pile. */
    boolean clicked(BlockEntity be, double x, double y, int button) {
        CounterTile tile = StorageViews.as(CounterTile.class, be);
        if (tile == null || button != 1) {
            return false;
        }
        int mx = (int) Math.floor(x), my = (int) Math.floor(y);
        if (tile.role() == CounterTile.Role.OUTLET) {
            int row = keepRowAt(tile, mx, my);
            if (row >= 0) {
                CounterUnit.Keep k = tile.counter().keeps().get(row);
                keep(tile, k.proto, k.count - (Screen.hasShiftDown() ? 1 : k.proto.getMaxStackSize()));
                return true;
            }
            return false;
        }
        if (tile.role() != CounterTile.Role.COUNTER) {
            return false;
        }
        if (panel == CART_PANEL) {
            int row = cartRowAt(mx, my);
            if (row >= 0 && row < crafts.size()) {
                PacketWarehouse.CraftView cv = crafts.get(row);
                setCraft(tile, cv, Math.max(1, craftTimes(cv) - (Screen.hasShiftDown() ? 10 : 1)));
                return true;
            }
            if (row >= 0) {
                Line l = cart.get(row - crafts.size());
                setLine(tile, l.proto, l.count - (Screen.hasShiftDown() ? 1 : l.proto.getMaxStackSize()));
                return true;
            }
            return over(CounterTile.VOLET, mx, my);
        }
        if (panel != NO_PANEL) {
            return over(CounterTile.VOLET, mx, my);
        }
        if (over(CounterTile.SEARCH, mx, my)) {
            clearSearch();
            if (search != null) {
                search.setFocused(true);
            }
            return true;
        }
        PacketWarehouse.Entry e = gridAt(mx, my);
        if (e != null) {
            int have = inCart(e.proto());
            if (have > 0) {
                setLine(tile, e.proto(), have - (Screen.hasShiftDown() ? 1 : e.proto().getMaxStackSize()));
            }
            return true;
        }
        return false;
    }

    private boolean inside(StandardLayout.Rect r) {
        return over(r, lastX, lastY);
    }

    private static boolean over(StandardLayout.Rect r, int x, int y) {
        return x >= r.x() && x < r.x() + r.w() && y >= r.y() && y < r.y() + r.h();
    }

    @Nullable
    private PacketWarehouse.Entry gridAt(int x, int y) {
        int c = Math.floorDiv(x - GRID_X, CELL), r = Math.floorDiv(y - GRID_Y, CELL);
        if (c < 0 || c >= COLS || r < 0 || r >= ROWS || (x - GRID_X) % CELL == CELL - 1 || (y - GRID_Y) % CELL == CELL - 1) {
            return null;
        }
        int rr = scroll + r;
        if (rr >= rows.size() || rows.get(rr)[2] == HEADER || c >= rows.get(rr)[1]) {
            return null;
        }
        int i = rows.get(rr)[0] + c;
        return i < shown.size() ? shown.get(i) : null;
    }

    /**
     * Rangee du volet du panier sous le point, ou -1 (volet du panier ouvert) : 1.0.5-beta (lot 22) : d'abord les recettes demandees
     * (indices 0 .. recettes - 1, {@link CounterTile#CRAFT_ROW} de haut), puis les lignes ({@link CounterTile#CART_ROW}).
     */
    private int cartRowAt(int x, int y) {
        StandardLayout.Rect v = CounterTile.VOLET;
        if (panel != CART_PANEL || x < v.x() + 1 || x >= v.x() + v.w() - 1 || y < CounterTile.CART_TOP || y >= CounterTile.CART_TOP + CounterTile.CART_AREA) {
            return -1;
        }
        int top = CounterTile.CART_TOP;
        for (int i = cartScroll; i < rowCount(); i++) {
            int h = rowHeight(i);
            if (top + h > CounterTile.CART_TOP + CounterTile.CART_AREA) {
                return -1;
            }
            if (y < top + h) {
                return i;
            }
            top += h;
        }
        return -1;
    }

    /** 1.0.5-beta (lot 22) : rangees du volet (recettes demandees puis lignes) et hauteur de la rangee {@code i}. */
    private int rowCount() {
        return crafts.size() + cart.size();
    }

    private int rowHeight(int i) {
        return i < crafts.size() ? CounterTile.CRAFT_ROW : CounterTile.CART_ROW;
    }

    /** Premiere rangee la plus basse qui laisse voir la fin de la liste (defilement au plus). */
    private int maxCartScroll() {
        int h = 0, n = rowCount();
        for (int i = n - 1; i >= 0; i--) {
            h += rowHeight(i);
            if (h > CounterTile.CART_AREA) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Nombre de fabrications affiche : celui demande a la molette tant que le serveur n'a pas repondu. */
    private int craftTimes(PacketWarehouse.CraftView cv) {
        return craftPending.getOrDefault(cv.id(), cv.times());
    }

    /** Fabrications d'une recette demandee (1 a 64 ; 0 la retire avec sa part du panier) : le serveur refait le plan et repond. */
    private void setCraft(CounterTile tile, PacketWarehouse.CraftView cv, int times) {
        int n = Math.max(0, Math.min(fr.pioneers.logistics.warehouse.RecipeAsk.MAX_TIMES, times));
        if (n == craftTimes(cv)) {
            return;
        }
        craftPending.put(cv.id(), n);
        pending = ++seq;
        PiNetwork.sendToServer(new PacketWarehouse.CraftEdit(tile.getBlockPos(), cv.id(), n, pending));
    }

    // ------------------------------------------------------------------ panier local

    private int inCart(ItemStack proto) {
        for (Line l : cart) {
            if (ItemStack.isSameItemSameTags(l.proto, proto)) {
                return l.count;
            }
        }
        return 0;
    }

    /** Ce qui peut encore aller au panier pour ce type : stock moins file moins panier. */
    private long free(CounterTile tile, ItemStack proto, int inCart) {
        BlockPos pos = tile.getBlockPos();
        return Math.max(0, CounterClient.stock(pos).count(proto) - CounterClient.queued(pos, proto) - inCart);
    }

    /** Fixe la quantite d'une ligne (bornee par le stock moins la file) ; la copie locale change aussitot, le serveur suit. */
    private void setLine(CounterTile tile, ItemStack proto, int count) {
        BlockPos pos = tile.getBlockPos();
        long max = Math.max(0, CounterClient.stock(pos).count(proto) - CounterClient.queued(pos, proto));
        int n = (int) Math.max(0, Math.min(Math.min(count, max), CounterCart.MAX_COUNT));
        Line found = null;
        for (Line l : cart) {
            if (ItemStack.isSameItemSameTags(l.proto, proto)) {
                found = l;
            }
        }
        if (found == null && (n == 0 || cart.size() >= CounterCart.MAX_LINES)) {
            if (n > 0) {
                say(Component.translatable("gui.pioneers.warehouse.cart.full", CounterCart.MAX_LINES), 2);
            }
            return;
        }
        if (found != null && found.count == n) {
            return;
        }
        if (found == null) {
            cart.add(new Line(proto.copyWithCount(1), n));
        } else if (n == 0) {
            cart.remove(found);
        } else {
            found.count = n;
        }
        cartScroll = Math.max(0, Math.min(maxCartScroll(), cartScroll));
        send(tile, PacketWarehouse.CartEdit.SET, proto, n);
    }

    private void send(CounterTile tile, int op, ItemStack proto, int n) {
        pending = ++seq;
        PiNetwork.sendToServer(new PacketWarehouse.CartEdit(tile.getBlockPos(), op, proto, n, pending));
    }

    private void say(Component text, int tone) {
        notice = text;
        noticeTone = tone;
        noticeUntil = Util.getMillis() + NOTICE_MS;
    }

    /** Reprend le panier du serveur s'il a traite notre derniere modification ; affiche un avis neuf. */
    private void sync(@Nullable PacketWarehouse.Contents contents) {
        if (contents == null) {
            return;
        }
        PacketWarehouse.CartView v = contents.cart();
        if (pending == NONE || v.seq() == pending) {
            cart.clear();
            for (PacketWarehouse.Entry e : v.lines()) {
                cart.add(new Line(e.proto(), (int) e.count()));
            }
            crafts = v.crafts();
            craftPending.clear();
            cartScroll = Math.max(0, Math.min(maxCartScroll(), cartScroll));
        }
        if (noticeId == NONE) {
            noticeId = v.noticeId();
        } else if (v.noticeId() != noticeId) {
            noticeId = v.noticeId();
            if (!v.notice().isEmpty()) {
                say(noticeText(v), v.tone());
            }
        }
    }

    /** « n lignes », « 1 ligne »... (singulier pour 1) : {@code what} = lines, piles, parcels. */
    static Component count(String what, long n) {
        return n == 1 ? Component.translatable("gui.pioneers.warehouse.cart." + what + "1")
                      : Component.translatable("gui.pioneers.warehouse.cart." + what, Texts.format(n));
    }

    /**
     * Texte de l'avis du serveur : « Commander » (a = lignes parties ; ok : b = piles ; partiel : b = lignes restees ; rien : a = lignes
     * restees), puis la raison ; recette de JEI (lot 17 : a = fabrications ; ok : b = lignes ; partiel : b = objets manquants ; rien : a =
     * lignes manquantes) ; autres avis : a, b s'il est >= 0, la raison s'il y en a une.
     */
    static Component noticeText(PacketWarehouse.CartView v) {
        Component reason = v.reason().isEmpty() ? Component.empty() : Component.translatable(v.reason());
        return switch (v.notice()) {
            case "gui.pioneers.warehouse.order.ok" -> Component.translatable(v.notice(), count("lines", v.a()), count("piles", v.b()));
            case "gui.pioneers.warehouse.order.partial" -> Component.translatable(v.notice(), count("lines", v.a()), count("lines", v.b()), reason);
            case "gui.pioneers.warehouse.order.none" -> Component.translatable(v.notice(), count("lines", v.a()), reason);
            case "gui.pioneers.warehouse.recipe.ok" -> Component.translatable(v.notice(), Texts.format(v.a()), count("lines", v.b()));
            case "gui.pioneers.warehouse.recipe.partial" -> Component.translatable(v.notice(), Texts.format(v.a()), Texts.format(v.b()), reason);
            case "gui.pioneers.warehouse.recipe.none" -> Component.translatable(v.notice(), count("lines", v.a()), reason);
            default -> {
                List<Object> args = new ArrayList<>();
                args.add(v.a());
                if (v.b() >= 0) {
                    args.add(v.b());
                }
                if (!v.reason().isEmpty()) {
                    args.add(reason);
                }
                yield Component.translatable(v.notice(), args.toArray());
            }
        };
    }

    // ------------------------------------------------------------------ liste

    static String fold(String text) {
        return MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private String folded(ItemStack proto) {
        return folded.computeIfAbsent(StockKey.of(proto), k -> fold(proto.getHoverName().getString()));
    }

    /** Separateur decimal de la langue choisie (virgule en francais). */
    private static boolean comma() {
        try {
            return net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected().startsWith("fr");
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Quantite abregee (12, 1,2k, 34k, 1,5M ; point decimal en anglais). */
    static String compact(long n) {
        return compact(n, comma());
    }

    static String compact(long n, boolean comma) {
        String out;
        if (n < 1000) {
            return Long.toString(n);
        } else if (n < 10_000) {
            out = String.format(Locale.ROOT, "%.1fk", n / 1000.0).replace(".0k", "k");
        } else if (n < 1_000_000) {
            return (n / 1000) + "k";
        } else {
            out = String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0).replace(".0M", "M");
        }
        return comma ? out.replace('.', ',') : out;
    }

    private static String modOf(ItemStack s) {
        var key = ForgeRegistries.ITEMS.getKey(s.getItem());
        return key == null ? "" : key.getNamespace();
    }

    /** Liste filtree et triee (refaite seulement si le stock recu, la recherche ou le tri changent). */
    private List<PacketWarehouse.Entry> list(CounterClient.Stock stock) {
        if (listStamp != CounterClient.received() || listSort != sort || !query.equals(listQuery)) {
            listStamp = CounterClient.received();
            listSort = sort;
            listQuery = query;
            String[] words = query.isBlank() ? null : SPACES.split(fold(query).trim());
            List<PacketWarehouse.Entry> out = new ArrayList<>();
            for (PacketWarehouse.Entry e : stock.entries()) {
                if (words != null) {
                    //Recherche dans le nom et, avec « @ », dans le mod (comme JEI et AE2)
                    String k = folded(e.proto()), mod = modOf(e.proto());
                    boolean ok = true;
                    for (String w : words) {
                        ok &= w.startsWith("@") ? mod.startsWith(w.substring(1)) : k.contains(w);
                    }
                    if (!ok) {
                        continue;
                    }
                }
                out.add(e);
            }
            Comparator<PacketWarehouse.Entry> byName = Comparator.comparing(e -> folded(e.proto()));
            out.sort(switch (sort) {
                case 1 -> byName;
                case 2 -> Comparator.comparing((PacketWarehouse.Entry e) -> modOf(e.proto())).thenComparing(byName);
                default -> Comparator.comparingLong(PacketWarehouse.Entry::count).reversed().thenComparing(byName);
            });
            shown = out;
            rows = layout(out, sort == 2);
        }
        return shown;
    }

    /** 1.0.7 : rangees de la grille ; {@code byMod} : un en-tete devant chaque mod, chaque mod commence sa rangee. */
    private static List<int[]> layout(List<PacketWarehouse.Entry> list, boolean byMod) {
        List<int[]> out = new ArrayList<>();
        int i = 0;
        while (i < list.size()) {
            int end = list.size();
            if (byMod) {
                String mod = modOf(list.get(i).proto());
                end = i + 1;
                while (end < list.size() && modOf(list.get(end).proto()).equals(mod)) {
                    end++;
                }
                out.add(new int[]{i, end - i, HEADER});
            }
            for (int j = i; j < end; j += COLS) {
                out.add(new int[]{j, Math.min(COLS, end - j), ITEMS});
            }
            i = end;
        }
        return out;
    }

    /** Nom affiche d'un mod (nom declare par le mod, sinon son identifiant). */
    private String modName(String id) {
        return modNames.computeIfAbsent(id, k -> net.minecraftforge.fml.ModList.get().getModContainerById(k)
              .map(c -> c.getModInfo().getDisplayName()).orElse(k.isEmpty() ? "?" : k));
    }

    /** Couleur d'accent d'un mod (stable : tiree de son identifiant ; Minecraft en gris). */
    private static int modColor(String id) {
        if ("minecraft".equals(id)) {
            return 0xFF8A949E;
        }
        float hue = (id.hashCode() & 0xFFFF) / 65536F;
        return 0xFF000000 | (net.minecraft.util.Mth.hsvToRgb(hue, 0.45F, 0.85F) & 0xFFFFFF);
    }

    /** Objets de ce type dans les colis qui viennent au comptoir. */
    private static long flying(CounterTile tile, ItemStack proto) {
        long n = 0;
        for (Parcel p : tile.counter().parcels()) {
            if (p.kind == Parcel.Kind.REQUEST && p.towardCounter() && ItemStack.isSameItemSameTags(p.proto, proto)) {
                n += p.count;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ dessin du comptoir

    void draw(GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
        CounterTile tile = StorageViews.as(CounterTile.class, be);
        if (tile == null) {
            return;
        }
        lastX = mouseX;
        lastY = mouseY;
        BlockPos pos = tile.getBlockPos();
        PacketWarehouse.Contents contents = CounterClient.list(pos);
        CounterClient.Stock stock = CounterClient.stock(pos);
        sync(contents);
        CounterClient.cart(pos, this::inCart);
        List<PacketWarehouse.Entry> list = list(stock);
        List<Component> tip = null;
        if (panel == NO_PANEL) {
            //Recherche et tri
            StandardLayout.Rect sr = CounterTile.SEARCH, so = CounterTile.SORT;
            gui.fieldAt(g, sr.x(), sr.y(), sr.w(), sr.h());
            gui.steelButton(g, mouseX, mouseY, so.x(), so.y(), so.w(), so.h(), Component.translatable("gui.pioneers.warehouse.sort." + SORTS[sort]), true,
                  () -> {
                      sort = (sort + 1) % SORTS.length;
                      scroll = 0;
                      //1.0.7 : le choix est garde (config/pioneers-client.toml)
                      fr.pioneers.logistics.warehouse.client.WarehouseClientConfig.setSort(
                            fr.pioneers.logistics.warehouse.client.WarehouseClientConfig.Sort.values()[sort]);
                  });
            if (gui.over(mouseX, mouseY, so.x(), so.y(), so.w(), so.h())) {
                tip = List.of(Component.translatable("gui.pioneers.warehouse.sort.tip"));
            } else if (gui.over(mouseX, mouseY, sr.x(), sr.y(), sr.w(), sr.h()) && query.isEmpty()) {
                tip = List.of(Component.translatable("gui.pioneers.warehouse.search.tip"));
            }
            tip = grid(g, mouseX, mouseY, tile, stock, list, tip);
        }
        tip = right(g, mouseX, mouseY, tile, contents, tip);
        if (panel == CART_PANEL) {
            tip = cartPanel(g, mouseX, mouseY, tile, stock, tip);
        } else if (panel == ROUTE_PANEL) {
            tip = routePanel(g, mouseX, mouseY, tile, contents, tip);
        }
        //Ligne d'aide, ou l'avis du dernier « Commander », ou le refus du comptoir (colonne bouchee, introuvable...)
        StandardLayout.Rect ln = CounterTile.LINE;
        String refusal = tile.counter().notice();
        Component help;
        int color;
        if (Util.getMillis() < noticeUntil) {
            help = notice;
            color = noticeTone == 1 ? GREEN : noticeTone == 2 ? ORANGE : PiMenuScreen.TEXT;
        } else if (!refusal.isEmpty()) {
            help = Component.translatable(refusal);
            color = ORANGE;
        } else if (!linked(gui)) {
            //1.0.4-beta (lot 34) : pas relie : la ligne du bas dit pourquoi (onglet Infos : le remede)
            help = diagDetail(gui);
            color = diagColor(diagCode(gui));
        } else {
            int row = panel == CART_PANEL ? cartRowAt(mouseX, mouseY) : -1;
            help = Component.translatable(row >= 0 && row < crafts.size() ? "gui.pioneers.warehouse.craft.help"
                                          : panel == CART_PANEL ? "gui.pioneers.warehouse.cart.help" : "gui.pioneers.warehouse.help");
            color = PiMenuScreen.DIM;
        }
        GuiDraw.text(g, gui.font(), help, ln.x() + 1, ln.y(), 0.6F, color, ln.w() - 2);
        if (tip != null) {
            gui.tooltip(tip);
        }
    }

    /** Grille du stock ; renvoie l'infobulle du survol. */
    private List<Component> grid(GuiGraphics g, int mouseX, int mouseY, CounterTile tile, CounterClient.Stock stock, List<PacketWarehouse.Entry> list,
          @Nullable List<Component> tip) {
        StandardLayout.Rect gr = CounterTile.GRID;
        gui.screenAt(g, gr.x(), gr.y(), gr.w(), gr.h());
        int rows = this.rows.size();
        scroll = Math.max(0, Math.min(Math.max(0, rows - ROWS), scroll));
        for (int r = 0; r < ROWS; r++) {
            int[] row = scroll + r < rows ? this.rows.get(scroll + r) : null;
            if (row != null && row[2] == HEADER) {
                //1.0.7 : bandeau du mod (accent de couleur, nom, nombre de sortes d'objets)
                int y = GRID_Y + r * CELL, w = COLS * CELL - 1;
                String id = modOf(list.get(row[0]).proto());
                int color = modColor(id);
                g.fill(GRID_X, y, GRID_X + w, y + CELL - 1, 0xFF161E26);
                g.fill(GRID_X, y, GRID_X + 2, y + CELL - 1, color);
                g.fill(GRID_X + 2, y + CELL - 2, GRID_X + w, y + CELL - 1, (color & 0xFFFFFF) | 0x60000000);
                GuiDraw.text(g, gui.font(), Component.literal(modName(id)), GRID_X + 6, y + 5.5F, 0.75F, color, w - 34);
                GuiDraw.right(g, gui.font(), Component.literal(Integer.toString(row[1])), GRID_X + w - 4, y + 6.5F, 0.6F, PiMenuScreen.DIM);
                if (gui.over(mouseX, mouseY, GRID_X, y, w, CELL - 1)) {
                    tip = List.of(Component.translatable("gui.pioneers.warehouse.mod_group", modName(id), row[1]).withStyle(ChatFormatting.GRAY));
                }
                continue;
            }
            for (int c = 0; c < COLS; c++) {
                int i = row == null || c >= row[1] ? Integer.MAX_VALUE : row[0] + c;
                int x = GRID_X + c * CELL, y = GRID_Y + r * CELL;
                if (i >= list.size()) {
                    g.fill(x, y, x + CELL - 1, y + CELL - 1, 0xFF10161C);
                    continue;
                }
                PacketWarehouse.Entry e = list.get(i);
                boolean hover = gui.over(mouseX, mouseY, x, y, CELL - 1, CELL - 1);
                int have = inCart(e.proto());
                long free = free(tile, e.proto(), have);
                g.fill(x, y, x + CELL - 1, y + CELL - 1, hover ? CELL_HOVER : CELL_BG);
                if (have > 0) {
                    g.renderOutline(x, y, CELL - 1, CELL - 1, WOOD);
                }
                g.renderItem(e.proto(), x + 1, y + 1);
                g.pose().pushPose();
                g.pose().translate(0, 0, 260);
                if (free <= 0) {
                    //Tout est deja au panier ou en file : case voilee
                    g.fill(x + 1, y + 1, x + CELL - 2, y + CELL - 2, 0x90101418);
                }
                GuiDraw.right(g, gui.font(), Component.literal(compact(e.count())), x + CELL - 1.5F, y + CELL - 6.5F, 0.5F, 0xFFFFFFFF);
                if (have > 0) {
                    GuiDraw.text(g, gui.font(), Component.literal("+" + compact(have)), x + 1.5F, y + 1.5F, 0.45F, WOOD);
                }
                g.pose().popPose();
                if (hover) {
                    tip = itemTip(tile, e.proto(), e.count(), have);
                }
                gui.click(x, y, CELL - 1, CELL - 1, () -> {
                    int pile = e.proto().getMaxStackSize();
                    int add = Screen.hasShiftDown() ? 1 : Screen.hasControlDown() ? Parcel.PILES * pile : pile;
                    int now = inCart(e.proto());
                    if (free(tile, e.proto(), now) <= 0) {
                        say(Component.translatable("gui.pioneers.warehouse.cart.none_left", e.proto().getHoverName()), 2);
                        return;
                    }
                    setLine(tile, e.proto(), now + add);
                });
            }
        }
        if (list.isEmpty()) {
            //1.0.4-beta (lot 34) : par-dessus les cases, sur un cartouche, et dit pourquoi
            emptyMessage(g, stock.ready);
        }
        //Barre de defilement (toujours la, pleine s'il n'y a rien a defiler)
        int trackX = GRID_X + COLS * CELL + 1, track = ROWS * CELL - 1;
        g.fill(trackX, GRID_Y, trackX + 3, GRID_Y + track, 0xFF0B1016);
        int knobH = rows > ROWS ? Math.max(8, track * ROWS / rows) : track;
        int knobY = GRID_Y + (rows > ROWS ? (int) Math.round((track - knobH) * scroll / (double) (rows - ROWS)) : 0);
        g.fill(trackX, knobY, trackX + 3, knobY + knobH, rows > ROWS ? WOOD : 0xFF2A333C);
        return tip;
    }

    /** Grille vide : cartouche par-dessus les cases (chargement, rien ne correspond, pas relie : pourquoi et quoi faire, vide). */
    private void emptyMessage(GuiGraphics g, boolean ready) {
        int x = GRID_X + 3, y = GRID_Y + 3, w = COLS * CELL - 7;
        boolean linked = linked(gui);
        Component head = !ready ? Component.translatable("gui.pioneers.warehouse.loading")
                                : !query.isBlank() ? Component.translatable("gui.pioneers.warehouse.no_match")
                                                   : linked ? Component.translatable("gui.pioneers.warehouse.empty") : diagDetail(gui);
        int code = diagCode(gui);
        Component fix = ready && query.isBlank() && !linked ? Component.translatable("gui.pioneers.warehouse.diag." + CounterUnit.DIAG_KEYS[code] + ".fix")
                                                            : null;
        int h = fix == null ? 26 : ROWS * CELL - 8;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(x, y, x + w, y + h, 0xF00D1218);
        g.fill(x, y, x + 1, y + h, fix == null ? 0xFF2A333C : diagColor(code));
        int lines = GuiDraw.wrapped(g, gui.font(), head, x + 5, y + 4, 0.62F, fix == null ? PiMenuScreen.DIM : diagColor(code), w - 9, 3, 7);
        if (fix != null) {
            GuiDraw.wrapped(g, gui.font(), fix, x + 5, y + 7 + lines * 7, 0.6F, PiMenuScreen.TEXT, w - 9, Math.max(1, (h - 10 - lines * 7) / 7), 7);
        }
        g.pose().popPose();
    }

    private List<Component> itemTip(CounterTile tile, ItemStack proto, long stored, int have) {
        List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(gui.minecraft(), proto));
        tip.add(Component.translatable("gui.pioneers.warehouse.stored", Texts.format(stored)).withStyle(ChatFormatting.GOLD));
        if (have > 0) {
            tip.add(inCartLine(proto, have).withStyle(ChatFormatting.YELLOW));
        }
        long route = CounterClient.queued(tile.getBlockPos(), proto) + flying(tile, proto);
        if (route > 0) {
            tip.add(Component.translatable("gui.pioneers.warehouse.en_route", Texts.format(route)).withStyle(ChatFormatting.AQUA));
        }
        tip.add(Component.translatable("gui.pioneers.warehouse.free", Texts.format(free(tile, proto, have))).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gui.pioneers.warehouse.grid.tip").withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    /** « Au panier : n (p piles) » (singulier pour une pile). */
    private static net.minecraft.network.chat.MutableComponent inCartLine(ItemStack proto, int count) {
        int piles = CounterCart.piles(proto, count);
        return piles == 1 ? Component.translatable("gui.pioneers.warehouse.in_cart1", Texts.format(count))
                          : Component.translatable("gui.pioneers.warehouse.in_cart", Texts.format(count), piles);
    }

    /** Totaux du panier local : piles, colis estimes (4 piles par colis et par type). */
    private int[] totals() {
        int piles = 0, parcels = 0;
        for (Line l : cart) {
            piles += CounterCart.piles(l.proto, l.count);
            parcels += CounterCart.parcels(l.proto, l.count);
        }
        return new int[]{piles, parcels};
    }

    /** Colonne de droite : sortie, bouton du panier, « Commander », carte « En route ». */
    private List<Component> right(GuiGraphics g, int mouseX, int mouseY, CounterTile tile, @Nullable PacketWarehouse.Contents contents,
          @Nullable List<Component> tip) {
        StandardLayout.Rect oc = CounterTile.OUT_CARD, cb = CounterTile.CART_BUTTON, od = CounterTile.ORDER, st = CounterTile.STATUS;
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.output"), oc.x() + 3, oc.y() + 3, 0.62F, PiMenuScreen.DIM, oc.w() - 4);
        if (gui.over(mouseX, mouseY, oc.x(), oc.y(), oc.w(), oc.h())) {
            tip = List.of(Component.translatable("gui.pioneers.warehouse.output.tip"));
        }
        boolean any = !cart.isEmpty();
        Component label = any ? Component.translatable("gui.pioneers.warehouse.cart.button", cart.size()) : Component.translatable("gui.pioneers.warehouse.cart");
        gui.pageButton(g, mouseX, mouseY, cb.x(), cb.y(), cb.w(), cb.h(), label, panel == CART_PANEL, WOOD & 0xFFFFFF,
              () -> open(panel == CART_PANEL ? NO_PANEL : CART_PANEL));
        if (panel == CART_PANEL && gui.over(mouseX, mouseY, cb.x(), cb.y(), cb.w(), cb.h())) {
            //Le bouton allume se referme d'un clic
            gui.click(cb.x(), cb.y(), cb.w(), cb.h(), () -> open(NO_PANEL));
        }
        if (gui.over(mouseX, mouseY, cb.x(), cb.y(), cb.w(), cb.h())) {
            int[] t = totals();
            tip = List.of(Component.translatable("gui.pioneers.warehouse.cart.button.tip"),
                  Component.translatable("gui.pioneers.warehouse.cart.total", count("lines", cart.size()), count("piles", t[0]), count("parcels", t[1]))
                        .withStyle(ChatFormatting.YELLOW),
                  Component.translatable("gui.pioneers.warehouse.jei.tip").withStyle(ChatFormatting.DARK_GRAY));
        }
        gui.steelButton(g, mouseX, mouseY, od.x(), od.y(), od.w(), od.h(), Component.translatable("gui.pioneers.warehouse.order"), any,
              () -> send(tile, PacketWarehouse.CartEdit.ORDER, ItemStack.EMPTY, 0));
        if (gui.over(mouseX, mouseY, od.x(), od.y(), od.w(), od.h())) {
            CounterClient.Stock stock = CounterClient.stock(tile.getBlockPos());
            tip = new ArrayList<>();
            tip.add(any ? Component.translatable("gui.pioneers.warehouse.order.tip", count("parcels", totals()[1]))
                        : Component.translatable("gui.pioneers.warehouse.order.empty"));
            tip.add(Component.translatable("gui.pioneers.warehouse.deliver", destName(stock.dest)).withStyle(ChatFormatting.AQUA));
            tip.add(Component.translatable("gui.pioneers.warehouse.order.keys").withStyle(ChatFormatting.DARK_GRAY));
        }
        //Carte « En route » : corde, colis et file, ou POURQUOI ca attend ; clic : le detail. Fond plat (l'ecran en neuf morceaux a des
        //equerres de 7 px qui passaient sur les lettres d'une carte de 23 px)
        gui.fieldAt(g, st.x(), st.y(), st.w(), st.h());
        double rope = gui.value(CounterUnit.ROPE) / 100.0;
        boolean linked = linked(gui);
        int x = st.x() + 5, y = st.y() + 3, w = st.w() - 8;
        String speed = String.format(Locale.ROOT, "%.2f", rope).replace('.', comma() ? ',' : '.');
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.en_route.title"), x, y, 0.55F, PiMenuScreen.DIM, w);
        Component ropeLine = !linked ? Component.translatable("gui.pioneers.warehouse.not_linked")
                                     : rope <= 0 ? Component.translatable("gui.pioneers.warehouse.still")
                                                 : Component.translatable("gui.pioneers.warehouse.speed", speed);
        GuiDraw.text(g, gui.font(), ropeLine, x, y + 6.5F, 0.55F, !linked || rope <= 0 ? RED : GREEN, w);
        int wait = waitCode(gui);
        long queue = gui.value(CounterUnit.QUEUE);
        Component third = wait != CounterUnit.W_NONE && wait != CounterUnit.W_LINK && wait != CounterUnit.W_STILL
                          ? Component.translatable("gui.pioneers.warehouse.wait." + CounterUnit.WAIT_KEYS[wait])
                          : Component.translatable("gui.pioneers.warehouse.status.counts", gui.value(CounterUnit.INBOUND), compact(queue));
        GuiDraw.text(g, gui.font(), third, x, y + 13, 0.55F, wait != CounterUnit.W_NONE && wait != CounterUnit.W_LINK && wait != CounterUnit.W_STILL
                                                         ? ORANGE : queue > 0 ? PiMenuScreen.TEXT : PiMenuScreen.DIM, w);
        gui.click(st.x(), st.y(), st.w(), st.h(), () -> open(panel == ROUTE_PANEL ? NO_PANEL : ROUTE_PANEL));
        if (gui.over(mouseX, mouseY, st.x(), st.y(), st.w(), st.h())) {
            tip = routeTip(tile, contents);
            //La carte est etroite : la phrase complete de la corde et de l'attente en tete de l'infobulle
            tip.add(1, (!linked ? diagDetail(gui).copy() : rope <= 0 ? Component.translatable("gui.pioneers.warehouse.still.tip")
                                                                     : Component.translatable("gui.pioneers.warehouse.speed.tip", speed))
                  .withStyle(!linked || rope <= 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
            if (wait != CounterUnit.W_NONE) {
                tip.add(2, Component.translatable("gui.pioneers.warehouse.wait." + CounterUnit.WAIT_KEYS[wait] + ".tip").withStyle(ChatFormatting.GOLD));
            }
            tip.add(Component.translatable("gui.pioneers.warehouse.status.tip").withStyle(ChatFormatting.DARK_GRAY));
        }
        return tip;
    }

    /** Nom d'une destination : la sortie du comptoir, ou l'interface de sortie (sa position). */
    private static Component destName(@Nullable BlockPos dest) {
        return dest == null ? Component.translatable("gui.pioneers.warehouse.dest.counter") : Component.translatable("gui.pioneers.warehouse.dest.outlet", at(dest));
    }

    /** Volet du PANIER : titre, « Vider », fermer ; lignes ; total ; « Livrer a ». */
    private List<Component> cartPanel(GuiGraphics g, int mouseX, int mouseY, CounterTile tile, CounterClient.Stock stock, @Nullable List<Component> tip) {
        StandardLayout.Rect v = CounterTile.VOLET;
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        gui.screenAt(g, v.x(), v.y(), v.w(), v.h());
        gui.swallow(v.x(), v.y(), v.w(), v.h());
        boolean any = !cart.isEmpty();
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.cart.title", count("lines", cart.size())), v.x() + 9, v.y() + 5, 0.7F,
              PiMenuScreen.TEXT, v.w() - 69);
        gui.steelButton(g, mouseX, mouseY, v.x() + v.w() - 54, v.y() + 3, 36, 11, Component.translatable("gui.pioneers.warehouse.cart.clear"),
              any || !crafts.isEmpty(), () -> {
                  cart.clear();
                  crafts = List.of();
                  cartScroll = 0;
                  send(tile, PacketWarehouse.CartEdit.CLEAR, ItemStack.EMPTY, 0);
              });
        gui.steelButton(g, mouseX, mouseY, v.x() + v.w() - 16, v.y() + 3, 11, 11, Component.literal("x"), true, () -> open(NO_PANEL));
        if (gui.over(mouseX, mouseY, v.x() + v.w() - 16, v.y() + 3, 11, 11)) {
            tip = List.of(Component.translatable("gui.pioneers.warehouse.panel.close"));
        }
        if (!any && crafts.isEmpty()) {
            GuiDraw.wrapped(g, gui.font(), Component.translatable("gui.pioneers.warehouse.cart.empty"), v.x() + 7, CounterTile.CART_TOP + 4, 0.6F,
                  PiMenuScreen.DIM, v.w() - 14, 6, 7);
        }
        int rowH = CounterTile.CART_ROW, rowY = CounterTile.CART_TOP;
        cartScroll = Math.max(0, Math.min(maxCartScroll(), cartScroll));
        for (int i = cartScroll; i < rowCount(); i++) {
            int k = i - crafts.size();
            if (rowY + rowHeight(i) > CounterTile.CART_TOP + CounterTile.CART_AREA) {
                break;
            }
            if (i < crafts.size()) {
                tip = craftRow(g, mouseX, mouseY, tile, crafts.get(i), v.x() + 3, rowY, v.w() - 6, tip);
                rowY += CounterTile.CRAFT_ROW;
                continue;
            }
            Line l = cart.get(k);
            int x = v.x() + 3, y = rowY, w = v.w() - 6;
            rowY += rowH;
            boolean hover = gui.over(mouseX, mouseY, x, y, w, rowH - 1);
            g.fill(x, y, x + w, y + rowH - 1, hover ? CELL_HOVER : (k % 2 == 0 ? 0xFF141A21 : 0xFF11161C));
            g.pose().pushPose();
            g.pose().translate(x + 1, y + 0.5F, 0);
            g.pose().scale(0.8F, 0.8F, 1);
            g.renderItem(l.proto, 0, 0);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            int bx = x + w - 31;
            float tw = bx - (x + 16) - 2;
            GuiDraw.text(g, gui.font(), l.proto.getHoverName(), x + 16, y + 1.5F, 0.6F, PiMenuScreen.TEXT, tw);
            int piles = CounterCart.piles(l.proto, l.count);
            long left = free(tile, l.proto, l.count);
            GuiDraw.text(g, gui.font(), piles == 1 ? Component.translatable("gui.pioneers.warehouse.cart.qty1", Texts.format(l.count))
                                     : Component.translatable("gui.pioneers.warehouse.cart.qty", Texts.format(l.count), piles), x + 16, y + 7.5F, 0.55F,
                  WOOD, tw);
            g.pose().popPose();
            small(g, mouseX, mouseY, bx, y + 2, "-", () -> setLine(tile, l.proto, l.count - (Screen.hasShiftDown() ? 1 : l.proto.getMaxStackSize())));
            small(g, mouseX, mouseY, bx + 10, y + 2, "+", () -> {
                if (free(tile, l.proto, inCart(l.proto)) <= 0) {
                    say(Component.translatable("gui.pioneers.warehouse.cart.none_left", l.proto.getHoverName()), 2);
                    return;
                }
                setLine(tile, l.proto, l.count + (Screen.hasShiftDown() ? 1 : l.proto.getMaxStackSize()));
            });
            small(g, mouseX, mouseY, bx + 20, y + 2, "x", () -> setLine(tile, l.proto, 0));
            if (hover && !gui.over(mouseX, mouseY, bx, y + 2, 29, 9)) {
                List<Component> t = new ArrayList<>(Screen.getTooltipFromItem(gui.minecraft(), l.proto));
                t.add(inCartLine(l.proto, l.count).withStyle(ChatFormatting.YELLOW));
                //1.0.5-beta (lot 22) : pour quelles recettes demandees
                Component forRecipes = forRecipes(l.proto);
                if (forRecipes != null) {
                    t.add(forRecipes);
                }
                t.add(Component.translatable("gui.pioneers.warehouse.free", Texts.format(left)).withStyle(ChatFormatting.GRAY));
                t.add(Component.translatable("gui.pioneers.warehouse.cart.row_help").withStyle(ChatFormatting.DARK_GRAY));
                tip = t;
            } else if (gui.over(mouseX, mouseY, bx, y + 2, 29, 9)) {
                tip = List.of(Component.translatable("gui.pioneers.warehouse.cart.row_help"));
            }
        }
        int maxScroll = maxCartScroll();
        if (maxScroll > 0) {
            //Plus de rangees que de place : petite barre a droite (molette hors d'une rangee)
            int total = rowCount(), top = CounterTile.CART_TOP, track = CounterTile.CART_AREA - 1;
            int knob = Math.max(6, track * (total - maxScroll) / total);
            int ky = top + (int) Math.round((track - knob) * cartScroll / (double) maxScroll);
            g.fill(v.x() + v.w() - 3, ky, v.x() + v.w() - 2, ky + knob, WOOD);
        }
        //Total : lignes, piles, colis estimes
        int[] t = totals();
        int sy = CounterTile.CART_TOP + CounterTile.CART_AREA + 1;
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.cart.total", count("lines", cart.size()), count("piles", t[0]),
              count("parcels", t[1])), v.x() + 6, sy + 1, 0.58F, any ? PiMenuScreen.TEXT : PiMenuScreen.DIM, v.w() - 12);
        //Livrer a : la sortie du comptoir ou une interface de sortie du reseau (clic : la suivante)
        int dy = sy + 10;
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.deliver.label"), v.x() + 6, dy + 3, 0.58F, PiMenuScreen.DIM, 40);
        List<BlockPos> dests = stock.dests;
        boolean choice = !dests.isEmpty();
        gui.steelButton(g, mouseX, mouseY, v.x() + 46, dy, v.w() - 51, 11, destName(stock.dest), choice, () -> {
            int i = stock.dest == null ? -1 : dests.indexOf(stock.dest);
            BlockPos next = i + 1 >= dests.size() ? null : dests.get(i + 1);
            stock.dest = next;
            PiNetwork.sendToServer(new PacketWarehouse.DeskEdit(tile.getBlockPos(), PacketWarehouse.DeskEdit.DEST, next == null ? 0 : next.asLong(), ItemStack.EMPTY, 0));
        });
        if (gui.over(mouseX, mouseY, v.x() + 46, dy, v.w() - 51, 11)) {
            tip = List.of(Component.translatable(choice ? "gui.pioneers.warehouse.deliver.tip" : "gui.pioneers.warehouse.deliver.none"));
        }
        g.pose().popPose();
        return tip;
    }

    /**
     * 1.0.5-beta (lot 22, retour 24) : une RECETTE DEMANDEE dans le volet du panier ({@link CounterTile#CRAFT_ROW} de haut) : produit, nom et
     * « x n » fabrications (orange s'il manque des objets dans l'entrepot), ingredients au panier en petites icones avec leur nombre ;
     * - / + / x a droite ; molette : +- 1 fabrication (Maj : +- 10) ; infobulle : produit, ingredients, ce qui manque, l'aide.
     */
    private List<Component> craftRow(GuiGraphics g, int mouseX, int mouseY, CounterTile tile, PacketWarehouse.CraftView cv, int x, int y, int w,
          @Nullable List<Component> tip) {
        int h = CounterTile.CRAFT_ROW - 1, times = craftTimes(cv);
        boolean hover = gui.over(mouseX, mouseY, x, y, w, h), short_ = cv.missing() > 0;
        g.fill(x, y, x + w, y + h, hover ? CELL_HOVER : 0xFF1A1812);
        g.fill(x, y, x + 1, y + h, short_ ? ORANGE : WOOD);
        g.pose().pushPose();
        g.pose().translate(x + 2, y + 1, 0);
        g.pose().scale(0.8F, 0.8F, 1);
        g.renderItem(cv.result(), 0, 0);
        g.pose().popPose();
        int bx = x + w - 31;
        float tw = bx - (x + 17) - 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.craft.row", cv.result().getHoverName(), times), x + 17, y + 1.5F, 0.6F,
              short_ ? ORANGE : PiMenuScreen.TEXT, tw);
        //Ingredients au panier : icone (8 px) et nombre, tant qu'il y a de la place ; « +n » au bout
        float ix = x + 17, right = bx - 2;
        int shown = 0;
        for (PacketWarehouse.Entry e : cv.uses()) {
            String n = compact(e.count());
            float need = 8 + gui.font().width(n) * 0.5F + 3;
            if (ix + need > right - (shown < cv.uses().size() - 1 ? 9 : 0)) {
                break;
            }
            g.pose().pushPose();
            g.pose().translate(ix, y + 9.5F, 0);
            g.pose().scale(0.5F, 0.5F, 1);
            g.renderItem(e.proto(), 0, 0);
            g.pose().popPose();
            GuiDraw.text(g, gui.font(), Component.literal(n), ix + 8.5F, y + 12F, 0.5F, WOOD, right - ix - 8);
            ix += need;
            shown++;
        }
        if (shown < cv.uses().size()) {
            GuiDraw.text(g, gui.font(), Component.literal("+" + (cv.uses().size() - shown)), ix, y + 12F, 0.5F, PiMenuScreen.DIM, right - ix);
        }
        g.pose().popPose();
        small(g, mouseX, mouseY, bx, y + 2, "-", () -> setCraft(tile, cv, Math.max(1, times - (Screen.hasShiftDown() ? 10 : 1))));
        small(g, mouseX, mouseY, bx + 10, y + 2, "+", () -> setCraft(tile, cv, times + (Screen.hasShiftDown() ? 10 : 1)));
        small(g, mouseX, mouseY, bx + 20, y + 2, "x", () -> setCraft(tile, cv, 0));
        if (hover) {
            List<Component> t = new ArrayList<>();
            t.add(Component.translatable("gui.pioneers.warehouse.craft.tip.title", cv.result().getHoverName()).withStyle(ChatFormatting.GOLD));
            t.add(Component.translatable("gui.pioneers.warehouse.craft.tip.times", times));
            t.add(Component.translatable("gui.pioneers.warehouse.craft.tip.makes", Texts.format((long) times * cv.yield()), cv.result().getHoverName())
                  .withStyle(ChatFormatting.YELLOW));
            t.add(Component.translatable("gui.pioneers.warehouse.craft.tip.uses").withStyle(ChatFormatting.GRAY));
            for (PacketWarehouse.Entry e : cv.uses()) {
                t.add(Component.translatable("gui.pioneers.warehouse.craft.tip.item", Texts.format(e.count()), e.proto().getHoverName())
                      .withStyle(ChatFormatting.GRAY));
            }
            if (short_) {
                t.add(Component.translatable("gui.pioneers.warehouse.craft.tip.missing", Texts.format(cv.missing())).withStyle(ChatFormatting.RED));
            }
            t.add(Component.translatable("gui.pioneers.warehouse.craft.help").withStyle(ChatFormatting.DARK_GRAY));
            tip = t;
        }
        return tip;
    }

    /** « Pour : recette, recette » : les recettes demandees qui ont mis ce type au panier (null : aucune). */
    @Nullable
    private Component forRecipes(ItemStack proto) {
        List<String> names = new ArrayList<>();
        for (PacketWarehouse.CraftView cv : crafts) {
            for (PacketWarehouse.Entry e : cv.uses()) {
                if (ItemStack.isSameItemSameTags(e.proto(), proto)) {
                    names.add(cv.result().getHoverName().getString() + " ×" + craftTimes(cv));
                    break;
                }
            }
        }
        return names.isEmpty() ? null : Component.translatable("gui.pioneers.warehouse.cart.for", String.join(", ", names)).withStyle(ChatFormatting.GOLD);
    }

    /** Volet « EN ROUTE » : corde, attente (pourquoi), colis, file, envois, detail des colis et de la file, « Annuler la file ». */
    private List<Component> routePanel(GuiGraphics g, int mouseX, int mouseY, CounterTile tile, @Nullable PacketWarehouse.Contents contents,
          @Nullable List<Component> tip) {
        StandardLayout.Rect v = CounterTile.VOLET;
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        gui.screenAt(g, v.x(), v.y(), v.w(), v.h());
        gui.swallow(v.x(), v.y(), v.w(), v.h());
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.en_route.title"), v.x() + 9, v.y() + 5, 0.7F, PiMenuScreen.TEXT, v.w() - 33);
        gui.steelButton(g, mouseX, mouseY, v.x() + v.w() - 16, v.y() + 3, 11, 11, Component.literal("x"), true, () -> open(NO_PANEL));
        int x = v.x() + 6, w = v.w() - 12;
        float y = v.y() + 17;
        double rope = gui.value(CounterUnit.ROPE) / 100.0;
        boolean linked = linked(gui);
        String speed = String.format(Locale.ROOT, "%.2f", rope).replace('.', comma() ? ',' : '.');
        Component ropeLine = !linked ? diagDetail(gui) : rope <= 0 ? Component.translatable("gui.pioneers.warehouse.still.tip")
                                                                   : Component.translatable("gui.pioneers.warehouse.speed.tip", speed);
        y += 7 * GuiDraw.wrapped(g, gui.font(), ropeLine, x, y, 0.6F, !linked || rope <= 0 ? RED : GREEN, w, 2, 7);
        int wait = waitCode(gui);
        if (wait != CounterUnit.W_NONE) {
            y += 7 * GuiDraw.wrapped(g, gui.font(), Component.translatable("gui.pioneers.warehouse.wait." + CounterUnit.WAIT_KEYS[wait] + ".tip"), x, y, 0.6F,
                  ORANGE, w, 2, 7);
        }
        long queue = gui.value(CounterUnit.QUEUE), sending = gui.value(CounterUnit.SENDING);
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.route.counts", gui.value(CounterUnit.PARCELS), CounterUnit.MAX_PARCELS,
              Texts.format(queue), Texts.format(sending)), x, y + 1, 0.58F, PiMenuScreen.TEXT, w);
        y += 9;
        g.fill(x, (int) y, x + w, (int) y + 1, 0xFF24333E);
        y += 3;
        //Detail : colis qui viennent (bleu), puis la file (jaune) : icone, nom, quantite
        List<Object[]> rows = new ArrayList<>();
        for (Parcel p : tile.counter().parcels()) {
            if (p.kind == Parcel.Kind.REQUEST && p.towardCounter() && p.count > 0) {
                rows.add(new Object[]{p.proto, (long) p.count, AQUA});
            }
        }
        if (contents != null) {
            for (PacketWarehouse.Entry e : contents.queued()) {
                rows.add(new Object[]{e.proto(), e.count(), 0xFFF0D060});
            }
        }
        int bottom = v.y() + v.h() - 17, max = Math.max(0, (bottom - (int) y) / 10);
        if (rows.isEmpty()) {
            GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.en_route.nothing"), x, y + 1, 0.6F, PiMenuScreen.DIM, w);
        }
        for (int i = 0; i < rows.size() && i < max; i++) {
            Object[] r = rows.get(i);
            ItemStack s = (ItemStack) r[0];
            int ry = (int) y + i * 10;
            boolean last = i == max - 1 && rows.size() > max;
            if (last) {
                GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.en_route.more", rows.size() - i), x, ry + 2, 0.58F, PiMenuScreen.DIM, w);
                break;
            }
            g.pose().pushPose();
            g.pose().translate(x, ry, 0);
            g.pose().scale(0.56F, 0.56F, 1);
            g.renderItem(s, 0, 0);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.route.row", s.getHoverName(), Texts.format((long) r[1])), x + 12, ry + 2,
                  0.58F, (int) r[2], w - 12);
            g.pose().popPose();
        }
        gui.steelButton(g, mouseX, mouseY, v.x() + 4, v.y() + v.h() - 15, v.w() - 8, 11, Component.translatable("gui.pioneers.warehouse.clear"), queue > 0,
              () -> gui.send(CounterTile.CLEAR_QUEUE));
        if (queue > 0 && gui.over(mouseX, mouseY, v.x() + 4, v.y() + v.h() - 15, v.w() - 8, 11)) {
            tip = List.of(Component.translatable("gui.pioneers.warehouse.clear.tip"));
        }
        g.pose().popPose();
        return tip;
    }

    /** Petit bouton 9 x 9 d'une ligne du panier. */
    private void small(GuiGraphics g, int mouseX, int mouseY, int x, int y, String label, Runnable action) {
        boolean hover = gui.over(mouseX, mouseY, x, y, 9, 9);
        g.fill(x, y, x + 9, y + 9, 0xFF14181E);
        g.fill(x + 1, y + 1, x + 8, y + 8, hover ? 0xFF3A4C5C : 0xFF262C35);
        GuiDraw.center(g, gui.font(), Component.literal(label), x + 4.75F, y + 1.6F, 0.75F, hover ? 0xFFFFFFFF : PiMenuScreen.TEXT, 9);
        gui.click(x, y, 9, 9, action);
    }

    private static List<Component> routeTip(CounterTile tile, @Nullable PacketWarehouse.Contents contents) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("gui.pioneers.warehouse.en_route.title").withStyle(ChatFormatting.WHITE));
        int shownLines = 0;
        for (Parcel p : tile.counter().parcels()) {
            if (p.kind == Parcel.Kind.REQUEST && p.towardCounter() && p.count > 0 && shownLines < 6) {
                tip.add(Component.translatable("gui.pioneers.warehouse.en_route.parcel", p.proto.getHoverName(), Texts.format(p.count)).withStyle(ChatFormatting.AQUA));
                shownLines++;
            }
        }
        if (contents != null) {
            int q = 0;
            for (PacketWarehouse.Entry e : contents.queued()) {
                if (q++ < 6) {
                    tip.add(Component.translatable("gui.pioneers.warehouse.en_route.queued", e.proto().getHoverName(), Texts.format(e.count()))
                          .withStyle(ChatFormatting.YELLOW));
                }
            }
            if (q > 6) {
                tip.add(Component.translatable("gui.pioneers.warehouse.en_route.more", q - 6).withStyle(ChatFormatting.GRAY));
            }
        }
        if (tip.size() == 1) {
            tip.add(Component.translatable("gui.pioneers.warehouse.en_route.nothing").withStyle(ChatFormatting.GRAY));
        }
        return tip;
    }

    // ------------------------------------------------------------------ 1.0.5-beta (lot 17) : interfaces d'entree et de sortie

    /** Regle de l'interface de sortie sous le point (indice), ou -1. */
    private static int keepRowAt(CounterTile tile, int x, int y) {
        StandardLayout.Rect c = CounterTile.IO_SIDE;
        if (x < c.x() + 2 || x >= c.x() + c.w() - 2 || y < CounterTile.KEEP_TOP) {
            return -1;
        }
        int i = (y - CounterTile.KEEP_TOP) / CounterTile.KEEP_ROW;
        return i < tile.counter().keeps().size() && i < CounterUnit.MAX_KEEPS ? i : -1;
    }

    /** Fixe une regle « garder N » (0 la retire) : le serveur verifie et renvoie l'etat. */
    private static void keep(CounterTile tile, ItemStack proto, int count) {
        PiNetwork.sendToServer(new PacketWarehouse.DeskEdit(tile.getBlockPos(), PacketWarehouse.DeskEdit.KEEP, 0, proto,
              Math.max(0, Math.min(CounterUnit.MAX_KEEP, count))));
    }

    /** JEI : zone des regles de l'interface de sortie (relative au panneau) ; un objet glisse y ajoute une regle. */
    @Nullable
    static StandardLayout.Rect keepArea(@Nullable BlockEntity be) {
        CounterTile tile = be == null ? null : StorageViews.as(CounterTile.class, be);
        return tile != null && tile.role() == CounterTile.Role.OUTLET ? CounterTile.IO_SIDE : null;
    }

    /** JEI : objet glisse sur la carte des regles : une regle « garder une pile » (si elle n'existe pas). */
    static void keepDropped(@Nullable BlockEntity be, ItemStack stack) {
        CounterTile tile = be == null ? null : StorageViews.as(CounterTile.class, be);
        if (tile != null && tile.role() == CounterTile.Role.OUTLET && !stack.isEmpty()) {
            for (CounterUnit.Keep k : tile.counter().keeps()) {
                if (ItemStack.isSameItemSameTags(k.proto, stack)) {
                    return;
                }
            }
            keep(tile, stack, stack.getMaxStackSize());
        }
    }

    void drawIo(GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
        CounterTile tile = StorageViews.as(CounterTile.class, be);
        if (tile == null) {
            return;
        }
        lastX = mouseX;
        lastY = mouseY;
        boolean inlet = tile.role() == CounterTile.Role.INLET;
        List<Component> tip = null;
        StandardLayout.Rect lc = CounterTile.IO_CARD, stc = CounterTile.IO_STATUS, side = inlet ? CounterTile.IO_SIDE_INLET : CounterTile.IO_SIDE,
              ln = CounterTile.IO_LINE;
        GuiDraw.text(g, gui.font(), Component.translatable(inlet ? "gui.pioneers.warehouse.io.inlet.slots" : "gui.pioneers.warehouse.io.outlet.slots"),
              lc.x() + 3, lc.y() + 3, 0.62F, PiMenuScreen.DIM, lc.w() - 4);
        if (gui.over(mouseX, mouseY, lc.x(), lc.y(), lc.w(), lc.h())) {
            tip = List.of(Component.translatable(inlet ? "gui.pioneers.warehouse.io.inlet.slots.tip" : "gui.pioneers.warehouse.io.outlet.slots.tip"));
        }
        //Etat de la station : corde, colis, pourquoi ca attend (fond plat : pas d'equerres sur les lettres)
        gui.fieldAt(g, stc.x(), stc.y(), stc.w(), stc.h());
        int x = stc.x() + 5, w = stc.w() - 8;
        double rope = gui.value(CounterUnit.ROPE) / 100.0;
        boolean linked = linked(gui);
        String speed = String.format(Locale.ROOT, "%.2f", rope).replace('.', comma() ? ',' : '.');
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.io.station"), x, stc.y() + 4, 0.55F, PiMenuScreen.DIM, w);
        GuiDraw.text(g, gui.font(), !linked ? Component.translatable("gui.pioneers.warehouse.not_linked")
                                 : rope <= 0 ? Component.translatable("gui.pioneers.warehouse.still") : Component.translatable("gui.pioneers.warehouse.speed", speed),
              x, stc.y() + 11, 0.55F, !linked || rope <= 0 ? RED : GREEN, w);
        long parcels = gui.value(CounterUnit.PARCELS);
        GuiDraw.text(g, gui.font(), Component.translatable(inlet ? "gui.pioneers.warehouse.io.sent" : "gui.pioneers.warehouse.io.coming",
              inlet ? parcels : gui.value(CounterUnit.INBOUND)), x, stc.y() + 18, 0.55F, PiMenuScreen.TEXT, w);
        if (!inlet) {
            GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.queue", compact(gui.value(CounterUnit.QUEUE))), x, stc.y() + 25, 0.55F,
                  gui.value(CounterUnit.QUEUE) > 0 ? PiMenuScreen.TEXT : PiMenuScreen.DIM, w);
        }
        int wait = waitCode(gui);
        if (wait != CounterUnit.W_NONE) {
            GuiDraw.wrapped(g, gui.font(), Component.translatable("gui.pioneers.warehouse.wait." + CounterUnit.WAIT_KEYS[wait]), x, stc.y() + (inlet ? 27 : 34),
                  0.55F, ORANGE, w, 2, 6.5F);
        }
        if (gui.over(mouseX, mouseY, stc.x(), stc.y(), stc.w(), stc.h())) {
            tip = new ArrayList<>();
            tip.add(Component.translatable("gui.pioneers.warehouse.io.station").withStyle(ChatFormatting.WHITE));
            tip.add((!linked ? diagDetail(gui).copy() : rope <= 0 ? Component.translatable("gui.pioneers.warehouse.still.tip")
                                                                  : Component.translatable("gui.pioneers.warehouse.speed.tip", speed))
                  .withStyle(!linked || rope <= 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
            if (wait != CounterUnit.W_NONE) {
                tip.add(Component.translatable("gui.pioneers.warehouse.wait." + CounterUnit.WAIT_KEYS[wait] + ".tip").withStyle(ChatFormatting.GOLD));
            }
        }
        gui.screenAt(g, side.x(), side.y(), side.w(), side.h());
        if (inlet) {
            int sx = side.x() + 6, sw = side.w() - 12;
            GuiDraw.text(g, gui.font(), Component.translatable("block.pioneers.warehouse_inlet"), sx + 3, side.y() + 5, 0.7F, PiMenuScreen.TEXT, sw - 6);
            int held = 0;
            for (var slot : tile.outputs()) {
                held += slot.getStack().getCount();
            }
            int n = GuiDraw.wrapped(g, gui.font(), Component.translatable("gui.pioneers.warehouse.io.inlet.explain"), sx, side.y() + 16, 0.6F, PiMenuScreen.TEXT,
                  sw, 8, 7);
            GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.io.inlet.waiting", Texts.format(held)), sx, side.y() + 20 + n * 7, 0.6F,
                  held > 0 ? WOOD : PiMenuScreen.DIM, sw);
            //1.0.5-beta (lot 45, retour 65) : case de sac (sous la carte) et sa carte : son contenu part en colis vers les caisses
            fr.pioneers.building.home.bag.BagView bagView = tile.bagContents();
            if (bagView != null) {
                StandardLayout.Rect bc = CounterTile.IO_BAG;
                gui.fieldAt(g, bc.x(), bc.y(), bc.w(), bc.h());
                int bx = bc.x() + 4, bw = bc.w() - 7;
                boolean hasBag = bagView.kind() != null;
                int left = bagView.count();
                GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.io.inlet.bag"), bx, bc.y() + 3, 0.6F, PiMenuScreen.TEXT, bw);
                GuiDraw.text(g, gui.font(), hasBag ? Component.translatable("gui.pioneers.warehouse.io.inlet.bag_left", Texts.format(left))
                                                 : Component.translatable("gui.pioneers.warehouse.io.inlet.bag_none"), bx, bc.y() + 11, 0.55F,
                      hasBag ? left > 0 ? WOOD : GREEN : PiMenuScreen.DIM, bw);
                if (gui.over(mouseX, mouseY, CounterTile.BAG_X - 1, bc.y(), bc.x() + bc.w() - CounterTile.BAG_X + 1, bc.h())) {
                    tip = List.of(Component.translatable("gui.pioneers.warehouse.io.inlet.bag.tip"));
                }
            }
        } else {
            tip = keeps(g, mouseX, mouseY, tile, tip);
        }
        GuiDraw.text(g, gui.font(), Component.translatable(inlet ? "gui.pioneers.warehouse.io.inlet.help" : "gui.pioneers.warehouse.io.outlet.help"),
              ln.x() + 1, ln.y(), 0.6F, PiMenuScreen.DIM, ln.w() - 2);
        if (tip != null) {
            gui.tooltip(tip);
        }
    }

    /** Carte des regles « garder N » de l'interface de sortie : lignes (icone, nom, quantite, ici), - / + / x ; deposer un objet = une regle. */
    private List<Component> keeps(GuiGraphics g, int mouseX, int mouseY, CounterTile tile, @Nullable List<Component> tip) {
        StandardLayout.Rect side = CounterTile.IO_SIDE;
        int sx = side.x() + 3, sw = side.w() - 6;
        List<CounterUnit.Keep> keeps = tile.counter().keeps();
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.keep.title", keeps.size(), CounterUnit.MAX_KEEPS), sx + 6, side.y() + 5, 0.7F,
              PiMenuScreen.TEXT, sw - 12);
        for (int k = 0; k < CounterUnit.MAX_KEEPS; k++) {
            int y = CounterTile.KEEP_TOP + k * CounterTile.KEEP_ROW;
            boolean hover = gui.over(mouseX, mouseY, sx, y, sw, CounterTile.KEEP_ROW - 1);
            if (k >= keeps.size()) {
                if (k == keeps.size()) {
                    //Premiere ligne libre : deposer l'objet porte (ou le glisser depuis JEI)
                    g.fill(sx, y, sx + sw, y + CounterTile.KEEP_ROW - 1, hover ? CELL_HOVER : 0xFF11161C);
                    GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.keep.add"), sx + 4, y + 4, 0.55F, PiMenuScreen.DIM, sw - 8);
                    gui.click(sx, y, sw, CounterTile.KEEP_ROW - 1, () -> {
                        ItemStack carried = gui.getMenu().getCarried();
                        if (!carried.isEmpty()) {
                            keepDropped(tile, carried);
                        } else {
                            say(Component.translatable("gui.pioneers.warehouse.keep.add.tip"), 0);
                        }
                    });
                    if (hover) {
                        tip = List.of(Component.translatable("gui.pioneers.warehouse.keep.add.tip"));
                    }
                }
                continue;
            }
            CounterUnit.Keep kp = keeps.get(k);
            g.fill(sx, y, sx + sw, y + CounterTile.KEEP_ROW - 1, hover ? CELL_HOVER : (k % 2 == 0 ? 0xFF141A21 : 0xFF11161C));
            g.pose().pushPose();
            g.pose().translate(sx + 1, y + 0.5F, 0);
            g.pose().scale(0.8F, 0.8F, 1);
            g.renderItem(kp.proto, 0, 0);
            g.pose().popPose();
            int bx = sx + sw - 31;
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            GuiDraw.text(g, gui.font(), kp.proto.getHoverName(), sx + 16, y + 1.5F, 0.55F, PiMenuScreen.TEXT, bx - sx - 18);
            GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.warehouse.keep.row", Texts.format(kp.count), Texts.format(tile.held(kp.proto))),
                  sx + 16, y + 7.5F, 0.55F, WOOD, bx - sx - 18);
            g.pose().popPose();
            small(g, mouseX, mouseY, bx, y + 2, "-", () -> keep(tile, kp.proto, kp.count - (Screen.hasShiftDown() ? 1 : kp.proto.getMaxStackSize())));
            small(g, mouseX, mouseY, bx + 10, y + 2, "+", () -> keep(tile, kp.proto, kp.count + (Screen.hasShiftDown() ? 1 : kp.proto.getMaxStackSize())));
            small(g, mouseX, mouseY, bx + 20, y + 2, "x", () -> keep(tile, kp.proto, 0));
            if (hover) {
                List<Component> t = new ArrayList<>(Screen.getTooltipFromItem(gui.minecraft(), kp.proto));
                t.add(Component.translatable("gui.pioneers.warehouse.keep.row.tip", Texts.format(kp.count)).withStyle(ChatFormatting.YELLOW));
                t.add(Component.translatable("gui.pioneers.warehouse.cart.row_help").withStyle(ChatFormatting.DARK_GRAY));
                tip = t;
            }
        }
        int fy = CounterTile.KEEP_TOP + CounterUnit.MAX_KEEPS * CounterTile.KEEP_ROW + 2;
        GuiDraw.wrapped(g, gui.font(), Component.translatable("gui.pioneers.warehouse.io.outlet.explain"), sx + 3, fy, 0.55F, PiMenuScreen.DIM, sw - 6,
              Math.max(1, (side.y() + side.h() - fy - 2) / 6), 6.5F);
        return tip;
    }
}
