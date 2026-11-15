package fr.pioneers.client.logistic;

import fr.pioneers.api.gas.Gas;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.robotics.RoboticsScreen;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.item.GasCapsuleItem;
import fr.pioneers.transport.logistic.CableLevel;
import fr.pioneers.transport.logistic.ChannelDef;
import fr.pioneers.transport.logistic.ChannelKind;
import fr.pioneers.transport.logistic.DiagramLayout;
import fr.pioneers.transport.logistic.DiagramSnapshot;
import fr.pioneers.transport.logistic.Distribution;
import fr.pioneers.transport.logistic.Filter;
import fr.pioneers.transport.logistic.LogisticNetwork;
import fr.pioneers.transport.logistic.PointConfig;
import fr.pioneers.transport.logistic.Role;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (D2b, spec §5.2, maquettes {@code diagramme_1..3}) : diagramme du reseau logistique, ouvert par un clic droit sur n'importe quel cable.
 * Plein ecran, style sombre de {@link RoboticsScreen} :
 * <ul>
 *     <li>barre du haut (rosace, titre, niveau et cables ; Copier / Coller le reseau, Aide, Fermer) ;</li>
 *     <li>colonne des canaux (x 0..118 : 16 lignes de 13 px qui defilent, liseré de couleur, glyphe du type, nom, debit mesure, ⚠ ; « + Nouveau
 *     canal », compteur ; bloc Reseau : cables, maillon faible (clic : Montrer), machines et groupes, redstone, repasser en auto) ;</li>
 *     <li>schema ({@link DiagramLayout} : machines et jonctions, liens sans longueur ; canal choisi en couleur avec paquets qui avancent ;
 *     molette = zoom, glisser le fond = defiler, glisser un noeud = le deplacer, Maj + clic = deplier / regrouper) ;</li>
 *     <li>panneau d'un canal (x W-159) ou d'un point (x W-175), qui defile ; barre d'etat (resume et avertissement le plus grave).</li>
 * </ul>
 * Les panneaux editent une copie (brouillon) envoyee par « Valider » ; le serveur renvoie l'instantane. Aucun texte en dur : cles
 * {@code logistic.pioneers.diagram.*}. Coordonnees reprises par la maquette {@code tools/gc/logistics/diagram_preview.py}.
 * <p>
 * 1.0.5-beta (lot 45) : retour 66 : SITES (cadres « Site A », machines, en pause ; bouton « Reorganiser » de chaque site ; traits entre sites
 * avec la vraie distance, infobulle : longueur de cable) ; machines EN PAUSE (chunk non charge) et DECONNECTEES (retour 63 : « Rien » dans
 * tous les canaux) grisees, avec leur pastille et leur infobulle ; places a la main relatives au site.
 */
@OnlyIn(Dist.CLIENT)
public final class LogisticDiagramScreen extends RoboticsScreen {

    static final String D = "logistic.pioneers.diagram.";
    /** Barre du haut, barre d'etat, colonne des canaux, panneaux. */
    static final int BAR = 16, STATUS = 18, COL = 118, CH_PANEL = 159, PT_PANEL = 175, ROW = 15;
    /** Couleurs des roles : entree (machine -> cable), sortie (cable -> machine), les deux. */
    static final int IN = 0xFFC9071F, OUT = 0xFF366BD0, BOTH = 0xFF8A4FD0, GOLD = 0xFFFFD36B, WARN = 0xFFE0B040;
    /** 16 teintes proposees aux canaux. */
    static final int[] PALETTE = {0xE0A040, 0xC0703A, 0x3FB6C4, 0x3F76E4, 0x4F8A55, 0xB5413A, 0xA460D9, 0xC8D0DA,
                                  0xB07A3E, 0xE0E6EE, 0xFF5A1F, 0x9FB8C8, 0x7FE6C0, 0xE86AA8, 0x8FD14F, 0x6B2340};
    /** Couleurs des types (familles des cables) et glyphes 6 x 6 (memes motifs que les cables : GLYPH6). */
    static final int[] KIND_COLOR = {0xB07A3E, 0x3F76E4, 0xE0E6EE, 0x3FB6C4, 0xFF5A1F, 0x9FB8C8, 0xA460D9};
    static final String[][] GLYPH = {
          {"######", "#....#", "#.##.#", "#.##.#", "#....#", "######"},
          {"..##..", "..##..", ".####.", "######", "######", ".####."},
          {".####.", "#....#", "#.##.#", "#.##.#", "#....#", ".####."},
          {"...###", "..##..", ".####.", "..##..", ".##...", "##...."},
          {"..#...", "..##..", ".###.#", "######", "######", ".####."},
          {".####.", "#...##", "#..#.#", "#.#..#", "#....#", ".####."},
          {"#.#.#.", ".#.#.#", "#.#.#.", ".#.#.#", "#.#.#.", ".#.#.#"}};
    /** Patron du cube : (colonne, rangee) de chaque face relative (avant, gauche, droite, arriere, dessus, dessous). */
    static final int[][] NET = {{1, 1}, {0, 1}, {2, 1}, {3, 1}, {1, 0}, {1, 2}};

    private enum Panel {NONE, CHANNEL, POINT}

    private enum Popup {NONE, PICKER, CONFIRM}

    private DiagramSnapshot snap;
    private DiagramLayout layout;
    /** 1.12.1 (F1) : point de vue du joueur a l'ouverture (disposition spatiale) ; schema reorganise (bouton, depliage d'un groupe). */
    @Nullable
    private final DiagramLayout.View view;
    private boolean arranged;
    /** 1.0.5-beta (lot 45) : sites reorganises seuls (bouton de leur en-tete). */
    private final LongOpenHashSet arrangedSites = new LongOpenHashSet();
    /** 1.12.1 (F1) : animation vers les nouvelles places (300 ms) : depart et arrivee de chaque noeud. */
    private final Long2ObjectOpenHashMap<double[]> animFrom = new Long2ObjectOpenHashMap<>(), animTo = new Long2ObjectOpenHashMap<>();
    private long animStart;
    private final LongOpenHashSet unfolded = new LongOpenHashSet();
    private int selUid;
    private Panel panel = Panel.NONE;
    @Nullable
    private ChannelDef chDraft;
    private boolean chNew;
    @Nullable
    private DiagramLayout.Box ptBox;
    @Nullable
    private PointConfig ptDraft;
    private boolean applyGroup = true;
    private double zoom = 1, offX, offY;
    private boolean fitted;
    private int chScroll, panelScroll, panelContent;
    private boolean help;
    private Popup popup = Popup.NONE;
    private boolean pickerForPoint;
    /** 1.12.1 (F1) : mini-fenetre du filtre : recherche, page, ingredient survole (tags proposes), objet glisse depuis l'inventaire. */
    private EditBox searchBox;
    private int pickerPage;
    @Nullable
    private IngredientIndex.Entry pickerFocus;
    @Nullable
    private String dragKey;
    @Nullable
    private ItemStack dragIcon;
    private int dropX0, dropY0, dropX1, dropY1;
    @Nullable
    private Runnable confirmAction;
    private Component confirmText = Component.empty();
    @Nullable
    private Component flash;
    private long flashUntil;
    // glisser
    @Nullable
    private DiagramLayout.Box dragBox;
    private boolean panning, dragged;
    private double pressX, pressY, dragOffX, dragOffY;
    // saisie de texte
    private EditBox textBox;
    @Nullable
    private Consumer<String> textDone;
    private boolean closed;
    private final long opened = System.currentTimeMillis();

    public LogisticDiagramScreen(DiagramSnapshot snap) {
        super(Component.translatable(D + "title"), null, 427, 240);
        drawPanel = false;
        this.snap = snap;
        var player = Minecraft.getInstance().player;
        view = player == null ? null : new DiagramLayout.View(player.getX(), player.getEyeY(), player.getZ(), player.getYRot());
        rebuild(false);
        selUid = snap.channels.isEmpty() ? 0 : snap.channels.get(0).uid();
        if (snap.focus != DiagramSnapshot.NO_FOCUS) {
            DiagramLayout.Box box = layout.boxOf(snap.focus);
            for (DiagramSnapshot.Channel c : snap.channels) {
                if (snap.role(snap.focus, c.uid()) != Role.NONE) {
                    selUid = c.uid();
                    break;
                }
            }
            if (box != null && selUid != 0) {
                openPoint(box);
            }
        }
    }

    long cable() {
        return snap.cable;
    }

    // ------------------------------------------------------------------
    // Donnees recues
    // ------------------------------------------------------------------

    /**
     * Schema recalcule (calcul au clic ou a la reception, jamais a chaque image). {@code animate} : les noeuds glissent de leur ancienne place
     * a la nouvelle (un noeud nouveau part de la place du groupe qui le contenait).
     */
    private void rebuild(boolean animate) {
        Long2ObjectOpenHashMap<double[]> before = new Long2ObjectOpenHashMap<>();
        if (animate && layout != null) {
            finishAnimation();
            for (DiagramLayout.Box b : layout.boxes) {
                double[] at = {b.x, b.y};
                before.put(b.id, at);
                for (long m : b.members) {
                    before.put(m, at);
                }
            }
        }
        layout = DiagramLayout.build(snap, unfolded, view, arranged, arrangedSites);
        animFrom.clear();
        animTo.clear();
        if (animate && !before.isEmpty()) {
            for (DiagramLayout.Box b : layout.boxes) {
                double[] from = before.get(b.id);
                if (from == null && b.members.length > 0) {
                    from = before.get(b.members[0]);
                }
                if (from != null && (from[0] != b.x || from[1] != b.y)) {
                    animFrom.put(b.id, from);
                    animTo.put(b.id, new double[]{b.x, b.y});
                }
            }
            animStart = System.currentTimeMillis();
        }
    }

    /** Places du moment pendant l'animation (ecrites dans les noeuds) ; fin : places d'arrivee. */
    private void animate() {
        if (animTo.isEmpty()) {
            return;
        }
        double t = Math.min(1, (System.currentTimeMillis() - animStart) / 300.0);
        double e = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
        for (DiagramLayout.Box b : layout.boxes) {
            double[] to = animTo.get(b.id), from = animFrom.get(b.id);
            if (to != null) {
                b.x = from[0] + (to[0] - from[0]) * e;
                b.y = from[1] + (to[1] - from[1]) * e;
            }
        }
        if (t >= 1) {
            finishAnimation();
        }
    }

    private void finishAnimation() {
        if (layout != null) {
            for (DiagramLayout.Box b : layout.boxes) {
                double[] to = animTo.get(b.id);
                if (to != null) {
                    b.x = to[0];
                    b.y = to[1];
                }
            }
        }
        animFrom.clear();
        animTo.clear();
    }

    /**
     * 1.12.1 (F1) : « Reorganiser » : disposition claire (moins de croisements, grille) depuis la disposition spatiale. Les places deplacees
     * a la main sont oubliees (pour tous les joueurs : la disposition est partagee) : elles restent gardees jusqu'au prochain « Reorganiser ».
     */
    private void reorganize() {
        arranged = true;
        arrangedSites.clear();
        if (!snap.layout.isEmpty() && snap.canEdit) {
            CompoundTag op = new CompoundTag();
            op.putString("op", "node");
            op.putBoolean("clear", true);
            send(op);
            snap.layout.clear();
        }
        rebuild(true);
        fitOnArrival();
    }

    /**
     * 1.0.5-beta (lot 45, retour 66 : « Reorganiser » par site) : ce site seul (moins de croisements, grille) ; ses places deplacees a la main
     * sont oubliees (pour tous les joueurs), celles des autres sites restent.
     */
    private void reorganizeSite(DiagramLayout.Site site) {
        arrangedSites.add(site.id);
        long[] ids = new long[site.boxes.size()];
        boolean any = false;
        for (int i = 0; i < ids.length; i++) {
            ids[i] = site.boxes.get(i).id;
            any |= snap.layout.containsKey(ids[i]);
        }
        if (any && snap.canEdit) {
            CompoundTag op = new CompoundTag();
            op.putString("op", "node");
            op.putBoolean("clear", true);
            op.putLongArray("ia", ids);
            send(op);
            for (long id : ids) {
                snap.layout.remove(id);
            }
        }
        rebuild(true);
        flash(tr("site_arranged", site.name()));
    }

    /** Cadrage sur les places d'arrivee de l'animation (les noeuds glissent dans ce cadre). */
    private void fitOnArrival() {
        double[][] now = new double[layout.boxes.size()][];
        for (int i = 0; i < now.length; i++) {
            DiagramLayout.Box b = layout.boxes.get(i);
            now[i] = new double[]{b.x, b.y};
            double[] to = animTo.get(b.id);
            if (to != null) {
                b.x = to[0];
                b.y = to[1];
            }
        }
        fit();
        for (int i = 0; i < now.length; i++) {
            layout.boxes.get(i).x = now[i][0];
            layout.boxes.get(i).y = now[i][1];
        }
    }

    /** Nouvel instantane (apres une edition, ou le reseau a change). */
    void update(DiagramSnapshot next, int selected, String lastOp) {
        long ptKey = ptBox == null ? Long.MIN_VALUE : ptBox.members.length > 0 ? ptBox.members[0] : ptBox.id;
        snap = next;
        rebuild(false);
        if (selected != 0 && snap.channel(selected) != null) {
            selUid = selected;
        } else if (snap.channel(selUid) == null) {
            //Canal auto materialise : meme type ; sinon le premier
            selUid = snap.channels.isEmpty() ? 0 : snap.channels.get(0).uid();
        }
        if (panel == Panel.CHANNEL) {
            DiagramSnapshot.Channel ch = snap.channel(selUid);
            if (lastOp.equals("del") || ch == null && !chNew) {
                closePanel();
            } else if (!lastOp.isEmpty() && ch != null) {
                chDraft = ch.def.copy();
                chNew = false;
            }
        } else if (panel == Panel.POINT) {
            DiagramLayout.Box box = ptKey == Long.MIN_VALUE ? null : layout.boxOf(ptKey);
            if (box == null || snap.channel(selUid) == null) {
                closePanel();
            } else {
                ptBox = box;
                if (!lastOp.isEmpty()) {
                    ptDraft = snap.config(ptKey, selUid);
                }
            }
        }
    }

    void stats(CompoundTag tag) {
        if (tag.getInt("nid") == snap.nid) {
            snap.applyStats(tag);
        }
    }

    private void send(CompoundTag op) {
        if (!LogisticDiagramClient.send(op)) {
            flash(tr("too_big"));
        }
    }

    private void flash(Component text) {
        flash = text;
        flashUntil = System.currentTimeMillis() + 3000;
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        super.init();
        textBox = new EditBox(font, 0, 0, 80, 12, Component.empty());
        textBox.setMaxLength(128);
        textBox.setVisible(false);
        addRenderableWidget(textBox);
        //1.12.1 (F1) : champ de recherche de la mini-fenetre du filtre (dessine par picker, au-dessus de la fenetre)
        String query = searchBox == null ? "" : searchBox.getValue();
        searchBox = new EditBox(font, 0, 0, 120, 12, Component.empty());
        searchBox.setMaxLength(64);
        searchBox.setValue(query);
        searchBox.setVisible(false);
        searchBox.setResponder(q -> pickerPage = 0);
        addWidget(searchBox);
    }

    @Override
    public void tick() {
        super.tick();
        textBox.tick();
        searchBox.tick();
    }

    @Override
    public void onClose() {
        cancelText();
        sendClose();
        super.onClose();
    }

    @Override
    public void removed() {
        sendClose();
        super.removed();
    }

    private void sendClose() {
        if (!closed) {
            closed = true;
            LogisticDiagramClient.close();
        }
    }

    // ------------------------------------------------------------------
    // Geometrie
    // ------------------------------------------------------------------

    private int canvasRight() {
        return panel == Panel.CHANNEL ? width - CH_PANEL : panel == Panel.POINT ? width - PT_PANEL : width;
    }

    private boolean inCanvas(double mx, double my) {
        return mx > COL && mx < canvasRight() && my > BAR && my < height - STATUS;
    }

    private double sx(double x) {
        return COL + 1 + offX + x * zoom;
    }

    private double sy(double y) {
        return BAR + 1 + offY + y * zoom;
    }

    private void fit() {
        double[] b = layout.bounds();
        int cw = canvasRight() - COL - 1, ch = height - STATUS - BAR - 1;
        double w = b[2] - b[0] + DiagramLayout.SEP_X + 10, h = b[3] - b[1] + DiagramLayout.SEP_Y + 30;
        //1.0.5-beta (lot 45) : plusieurs sites : cadrage jusqu'au plus petit zoom de la molette (0,4) pour les voir tous ; un site : comme avant
        zoom = Mth.clamp(Math.min(cw / w, ch / h), layout.sites.size() > 1 ? 0.4 : 0.5, 1.0);
        offX = cw / 2.0 - (b[0] + b[2]) / 2 * zoom;
        offY = (ch - 12) / 2.0 - (b[1] + b[3]) / 2 * zoom;
        fitted = true;
    }

    @Nullable
    private DiagramLayout.Box boxAt(double mx, double my) {
        if (!inCanvas(mx, my)) {
            return null;
        }
        for (int i = layout.boxes.size() - 1; i >= 0; i--) {
            DiagramLayout.Box b = layout.boxes.get(i);
            double r = (b.machine ? 15 : 5) * zoom;
            if (Math.abs(mx - sx(b.x)) <= r && Math.abs(my - sy(b.y)) <= r) {
                return b;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Textes
    // ------------------------------------------------------------------

    static MutableComponent tr(String key, Object... args) {
        return Component.translatable(D + key, args);
    }

    static Component kindName(ChannelKind kind) {
        return Component.translatable("logistic.pioneers.kind." + kind.id());
    }

    static Component levelName(CableLevel level) {
        return Component.translatable("logistic.pioneers.level." + level.id());
    }

    private static Component channelName(DiagramSnapshot.Channel c) {
        Component base = c.def.name.isEmpty() ? kindName(c.def.kind) : Component.literal(c.def.name);
        return c.auto ? tr("channel_auto", base) : base;
    }

    private static Component defName(ChannelDef def) {
        return def.name.isEmpty() ? kindName(def.kind) : Component.literal(def.name);
    }

    private static String decimal(double v) {
        boolean fr = Minecraft.getInstance().getLanguageManager().getSelected().startsWith("fr");
        String s = String.format(Locale.ROOT, "%.1f", v);
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return fr ? s.replace('.', ',') : s;
    }

    /** 21 400 -> « 21k » ; 1 250 000 -> « 1,3M ». */
    static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1e9) {
            return decimal(v / 1e9) + "G";
        }
        if (a >= 1e6) {
            return decimal(v / 1e6) + "M";
        }
        if (a >= 1e4) {
            return Math.round(v / 1e3) + "k";
        }
        if (a >= 1e3) {
            return decimal(v / 1e3) + "k";
        }
        return a >= 10 ? Long.toString(Math.round(v)) : decimal(v);
    }

    /** Debit mesure (ou corps) dans l'unite du type : objets/s, mB/t, FE/t, Mo/t, °C, bar. */
    static Component rate(ChannelKind kind, double perSecond, double body) {
        return switch (kind) {
            case ITEM -> tr("unit.item", compact(perSecond));
            case FLUID, GAS -> tr("unit.fluid", compact(perSecond / 20));
            case ENERGY -> tr("unit.energy", compact(perSecond / 20));
            case DATA -> tr("unit.data", compact(perSecond / 20));
            case HEAT -> Double.isNaN(body) ? Component.literal("—") : tr("unit.heat", compact(body - 273.15));
            case PRESSURE -> Double.isNaN(body) ? Component.literal("—") : tr("unit.pressure", decimal(body));
        };
    }

    private Component machineName(DiagramLayout.Box box) {
        Item item = item(box.item);
        Component name = item == Items.AIR ? tr("machine") : new ItemStack(item).getHoverName();
        return box.group() ? tr("group_name", name, box.members.length) : name;
    }

    static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
    }

    static Component roleName(Role role) {
        return tr("role." + role.name().toLowerCase(Locale.ROOT));
    }

    static int roleColor(Role role) {
        return switch (role) {
            case INPUT -> IN;
            case OUTPUT -> OUT;
            case BOTH -> BOTH;
            case NONE -> 0xFF3A4652;
        };
    }

    static Component faceName(int relative) {
        return tr("face." + RelativeSide.byIndex(relative).name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    @Override
    protected void drawContents(GuiGraphics g, int mx, int my, float pt) {
        if (!fitted) {
            fit();
        }
        animate();
        if (popup != Popup.PICKER && searchBox.isVisible()) {
            searchBox.setVisible(false);
            searchBox.setFocused(false);
        }
        int W = width, H = height;
        g.fill(0, 0, W, H, D_BG);
        topBar(g, mx, my);
        canvas(g, mx, my);
        channelColumn(g, mx, my);
        if (panel == Panel.CHANNEL && chDraft != null) {
            channelPanel(g, mx, my);
        } else if (panel == Panel.POINT && ptBox != null && ptDraft != null) {
            pointPanel(g, mx, my);
        }
        statusBar(g);
        if (help) {
            helpOverlay(g, mx, my);
        }
        if (popup == Popup.PICKER) {
            picker(g, mx, my);
        } else if (popup == Popup.CONFIRM) {
            confirm(g, mx, my);
        }
    }

    private void topBar(GuiGraphics g, int mx, int my) {
        int W = width;
        g.fill(0, 0, W, BAR, D_BAR);
        g.fill(0, BAR, W, BAR + 1, D_LINE);
        rosace(g, 4, 3);
        GuiDraw.text(g, font, title, 18, 4, 1.0F, D_TEXT, 200);
        float tx = 18 + Math.min(200, font.width(title)) + 6;
        Component sub = tr("subtitle", levelName(snap.level), snap.cables);
        GuiDraw.text(g, font, sub, tx, 5, 0.75F, D_DIM, W - 178 - tx);
        boolean auto = snap.channels.isEmpty();
        //1.12.1 (F1) : Reorganiser
        button(g, mx, my, W - 175, 2, 62, 12, tr("arrange"), !layout.boxes.isEmpty(), this::reorganize);
        tooltip(mx, my, W - 175, 2, 62, 12, tr("arrange_tip"));
        button(g, mx, my, W - 109, 2, 34, 12, tr("copy"), !auto, this::copyNetwork);
        tooltip(mx, my, W - 109, 2, 34, 12, tr("copy_tip"));
        button(g, mx, my, W - 73, 2, 34, 12, tr("paste"), snap.canEdit, this::pasteNetwork);
        tooltip(mx, my, W - 73, 2, 34, 12, tr("paste_tip"));
        button(g, mx, my, W - 37, 2, 14, 12, Component.literal("?"), true, () -> help = !help);
        button(g, mx, my, W - 21, 2, 18, 12, Component.literal("x"), true, this::onClose);
    }

    /** Rosace des 7 types (logo du cable). */
    private static void rosace(GuiGraphics g, int x, int y) {
        int[][] spots = {{4, 4, 0}, {4, 0, 3}, {7, 2, 1}, {7, 6, 2}, {4, 8, 4}, {1, 6, 5}, {1, 2, 6}};
        for (int[] s : spots) {
            g.fill(x + s[0], y + s[1], x + s[0] + 2, y + s[1] + 2, 0xFF000000 | KIND_COLOR[s[2]]);
        }
    }

    /** Glyphe 6 x 6 d'un type (pixels de {@code k}). */
    static void glyph(GuiGraphics g, ChannelKind kind, int x, int y, float k, boolean dim) {
        int c = 0xFF000000 | (dim ? GuiDraw.darken(KIND_COLOR[kind.ordinal()], 0.45F) : KIND_COLOR[kind.ordinal()]);
        String[] pat = GLYPH[kind.ordinal()];
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1);
        for (int j = 0; j < 6; j++) {
            for (int i = 0; i < 6; i++) {
                if (pat[j].charAt(i) == '#') {
                    g.fill(i, j, i + 1, j + 1, c);
                }
            }
        }
        g.pose().popPose();
    }

    // ---------------------------------------------------------------- colonne des canaux

    private int listBottom() {
        //1.0.5-beta (lot 45) : deux lignes de plus dans le bloc Reseau (sites ; en pause et deconnectees)
        return height - STATUS - 62 - 22;
    }

    private void channelColumn(GuiGraphics g, int mx, int my) {
        int H = height;
        g.fill(0, BAR + 1, COL, H - STATUS, D_PANEL);
        g.fill(COL, BAR + 1, COL + 1, H - STATUS, D_LINE);
        GuiDraw.text(g, font, tr("channels"), 5, 21, 0.85F, D_TEXT, 70);
        int live = 0;
        for (DiagramSnapshot.Channel c : snap.channels) {
            live += c.auto ? 0 : 1;
        }
        GuiDraw.right(g, font, tr("count", live, 16), 113, 22, 0.75F, D_DIM);
        int top = 32, bottom = listBottom();
        int rows = snap.channels.size();
        chScroll = Mth.clamp(chScroll, 0, Math.max(0, rows * ROW - (bottom - top)));
        beginClip(g, 0, top, COL, bottom);
        int y = top - chScroll;
        for (DiagramSnapshot.Channel c : snap.channels) {
            channelRow(g, mx, my, c, y);
            y += ROW;
        }
        endClip(g);
        if (rows * ROW > bottom - top) {
            //Ascenseur
            int track = bottom - top, bar = Math.max(8, track * track / (rows * ROW));
            int by = top + (track - bar) * chScroll / Math.max(1, rows * ROW - track);
            g.fill(COL - 2, by, COL - 1, by + bar, 0xFF4A5A68);
        }
        //Nouveau canal
        int ny = bottom + 2;
        boolean canNew = snap.canEdit && live < 16;
        boolean hover = canNew && over(mx, my, 3, ny, 112, 13);
        g.fill(3, ny, 115, ny + 13, hover ? 0xFF1C3229 : 0xFF14231E);
        g.renderOutline(3, ny, 112, 13, canNew ? 0xFF2E9E78 : 0xFF2A3634);
        GuiDraw.text(g, font, tr("new_channel"), 8, ny + 3, 0.75F, canNew ? 0xFF7FE6C0 : 0xFF5E706C, 104);
        if (canNew) {
            click(3, ny, 112, 13, this::newChannel);
        }
        //Bloc Reseau
        int y2 = ny + 22;
        GuiDraw.text(g, font, tr("network"), 5, y2, 0.85F, D_TEXT, 60);
        if (!snap.auto && snap.canEdit) {
            chip(g, mx, my, 70, y2 - 1, 43, 10, tr("to_auto"), false, 0x2E9E78, () -> ask(tr("confirm_auto"), () -> {
                CompoundTag op = new CompoundTag();
                op.putString("op", "auto");
                send(op);
            }));
            tooltip(mx, my, 70, y2 - 1, 43, 10, tr("to_auto_tip"));
        }
        y2 += 10;
        netLine(g, y2, tr("cables"), tr("cables_value", snap.cables, levelName(snap.level)), D_TEXT);
        y2 += 8;
        Component weak = snap.weakCount > 0 ? tr("weak_value", snap.weakCount, levelName(snap.level)) : tr("weak_none");
        netLine(g, y2, tr("weak"), weak, snap.weakCount > 0 ? WARN : D_DIM);
        if (snap.weakCount > 0 && snap.weak.length > 0) {
            click(3, y2 - 1, 112, 8, () -> {
                DiagramHighlights.show(snap.weak);
                flash(tr("weak_shown", snap.weak.length));
            });
            tooltip(mx, my, 3, y2 - 1, 112, 8, tr("weak_tip", levelName(snap.level)));
        }
        y2 += 8;
        netLine(g, y2, tr("machines"), tr("machines_value", snap.machines.size(), layout.boxes.stream().filter(b -> b.machine).count()), D_TEXT);
        y2 += 8;
        netLine(g, y2, tr("powered"), tr(snap.powered ? "yes" : "no"), D_TEXT);
        //1.0.5-beta (lot 45) : sites, machines en pause et deconnectees (sur la meme ligne pour garder la place de la colonne)
        int paused = 0, detached = 0;
        for (DiagramSnapshot.Machine m : snap.machines) {
            paused += m.paused ? 1 : 0;
            detached += m.detached ? 1 : 0;
        }
        y2 += 8;
        netLine(g, y2, tr("sites"), Component.literal(Integer.toString(layout.sites.size())), D_TEXT);
        tooltip(mx, my, 3, y2 - 1, 112, 8, tr("sites_tip", DiagramLayout.SITE_GAP));
        y2 += 8;
        netLine(g, y2, tr("paused_line"), tr("paused_value", paused, detached), paused > 0 || detached > 0 ? WARN : D_DIM);
        tooltip(mx, my, 3, y2 - 1, 112, 8, tr("paused_tip"));
    }

    private void netLine(GuiGraphics g, int y, Component label, Component value, int color) {
        float vw = Math.min(font.width(value) * 0.7F, 56);
        GuiDraw.text(g, font, label, 5, y, 0.7F, D_DIM, 108 - vw - 4);
        GuiDraw.text(g, font, value, 113 - vw, y, 0.7F, color, 56);
    }

    private void channelRow(GuiGraphics g, int mx, int my, DiagramSnapshot.Channel c, int y) {
        boolean sel = c.uid() == selUid;
        boolean hover = over(mx, my, 3, y, 112, 13);
        g.fill(3, y, 115, y + 13, sel ? 0xFF22303A : hover ? 0xFF1C252E : 0xFF171E25);
        if (sel) {
            g.renderOutline(3, y, 112, 13, ACCENT);
        }
        g.fill(3, y, 5, y + 13, 0xFF000000 | PALETTE[Math.floorMod(c.def.color, PALETTE.length)]);
        if (c.auto) {
            for (int yy = y + 1; yy < y + 13; yy += 3) {
                g.fill(6, yy, 7, yy + 1, D_DIM);
            }
        }
        glyph(g, c.def.kind, 8, y + 3, 1, c.state != LogisticNetwork.State.ACTIVE);
        boolean warn = c.state == LogisticNetwork.State.SUSPENDED || c.noOutput && !c.auto;
        Component rate = c.state == LogisticNetwork.State.WAITING ? tr("waiting_short") : rate(c.def.kind, c.rate, c.body);
        Component shown = warn ? Component.literal("⚠ ").append(rate) : rate;
        float rw = Math.min(font.width(shown) * 0.75F, 44);
        GuiDraw.text(g, font, channelName(c), 17, y + 3, 0.75F, c.auto || c.state == LogisticNetwork.State.WAITING ? D_DIM : D_TEXT, 112 - rw - 21);
        GuiDraw.text(g, font, shown, 112 - rw, y + 3, 0.75F, warn ? WARN : D_DIM, 44);
        click(3, y, 112, 13, () -> {
            if (selUid == c.uid() && panel != Panel.CHANNEL) {
                openChannel(c);
            } else {
                selUid = c.uid();
                if (panel == Panel.CHANNEL) {
                    openChannel(c);
                } else if (panel == Panel.POINT && ptBox != null) {
                    openPoint(ptBox);
                }
            }
        });
        if (hover) {
            List<Component> lines = new ArrayList<>();
            lines.add(channelName(c).copy().withStyle(ChatFormatting.WHITE));
            lines.add(tr("row_kind", kindName(c.def.kind), tr("mode." + c.def.mode.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GRAY));
            lines.add(tr("row_points", c.sources, c.outputs).withStyle(ChatFormatting.GRAY));
            Component why = warning(c);
            if (why != null) {
                lines.add(why.copy().withStyle(ChatFormatting.GOLD));
            }
            lines.add(tr("row_tip").withStyle(ChatFormatting.DARK_GRAY));
            tooltip(lines);
        }
    }

    /** Pourquoi un canal ne marche pas (null : rien a signaler). */
    @Nullable
    private Component warning(DiagramSnapshot.Channel c) {
        return switch (c.state) {
            case SUSPENDED -> tr("warn_suspended", channelName(c), kindName(c.def.kind), levelName(snap.level));
            case WAITING -> tr("warn_waiting", channelName(c));
            case OFF -> tr("warn_off", channelName(c));
            case ACTIVE -> c.noOutput && !c.auto ? tr("warn_no_output", channelName(c))
                  : c.sources == 0 && !c.auto && !c.def.kind.body() ? tr("warn_no_input", channelName(c)) : null;
        };
    }

    // ---------------------------------------------------------------- schema

    private void canvas(GuiGraphics g, int mx, int my) {
        int x1 = canvasRight(), H = height;
        g.fill(COL + 1, BAR + 1, x1, H - STATUS, 0xFF0E141A);
        beginClip(g, COL + 1, BAR + 1, x1, H - STATUS);
        //Trame discrete (suit le defilement)
        int step = Math.max(6, (int) Math.round(12 * zoom));
        int gx0 = (int) (COL + 1 + Math.floorMod((int) Math.round(offX), step)), gy0 = (int) (BAR + 1 + Math.floorMod((int) Math.round(offY), step));
        for (int gx = gx0; gx < x1; gx += step) {
            for (int gy = gy0; gy < H - STATUS; gy += step) {
                g.fill(gx, gy, gx + 1, gy + 1, 0xFF1A232B);
            }
        }
        DiagramSnapshot.Channel ch = snap.channel(selUid);
        boolean[] used = ch == null ? new boolean[layout.edges.size()] : layout.used(b -> participates(b, ch.uid()));
        int color = ch == null ? 0xFF3A4652 : 0xFF000000 | PALETTE[Math.floorMod(ch.def.color, PALETTE.length)];
        g.pose().pushPose();
        g.pose().translate(COL + 1 + offX, BAR + 1 + offY, 0);
        g.pose().scale((float) zoom, (float) zoom, 1);
        //1.0.5-beta (lot 45, retour 66) : cadres des sites (plusieurs sites seulement)
        boolean sites = layout.sites.size() > 1;
        if (sites) {
            for (DiagramLayout.Site site : layout.sites) {
                siteFrame(g, site);
            }
        }
        //Liens : gris, puis la couleur du canal sur les troncons utilises ; entre deux sites : le trait du site (plus bas)
        for (int i = 0; i < layout.edges.size(); i++) {
            DiagramLayout.Edge e = layout.edges.get(i);
            if (e.between()) {
                stub(g, e, 0xFF3A4652);
            } else {
                route(g, e.a(), e.b(), 0xFF3A4652);
            }
        }
        for (DiagramLayout.SiteLink link : layout.siteLinks) {
            polyline(g, link.route, 0xFF3A4652);
        }
        double speed = ch == null ? 0 : Mth.clamp(Math.log10(1 + Math.abs(ch.rate)) * 9, 0, 40);
        double phase = (System.currentTimeMillis() - opened) / 1000.0 * speed;
        for (int i = 0; i < layout.edges.size(); i++) {
            if (used[i]) {
                DiagramLayout.Edge e = layout.edges.get(i);
                if (e.between()) {
                    stub(g, e, color);
                    continue;
                }
                route(g, e.a(), e.b(), color);
                if (speed > 0) {
                    packets(g, e.a(), e.b(), phase);
                }
            }
        }
        for (DiagramLayout.SiteLink link : layout.siteLinks) {
            boolean on = false;
            for (DiagramLayout.Edge e : link.edges) {
                on |= used[layout.edges.indexOf(e)];
            }
            if (on) {
                polyline(g, link.route, color);
            }
            distanceLabel(g, link);
        }
        for (DiagramLayout.Box b : layout.boxes) {
            if (!b.machine) {
                int x = (int) b.x, y = (int) b.y;
                g.fill(x - 3, y - 3, x + 3, y + 3, 0xFF56636F);
                g.fill(x - 2, y - 2, x + 2, y + 2, 0xFF8796A4);
            }
        }
        DiagramLayout.Box hover = popup == Popup.NONE && !help ? boxAt(mx, my) : null;
        for (DiagramLayout.Box b : layout.boxes) {
            if (b.machine) {
                node(g, b, ch, b == hover);
            }
        }
        g.pose().popPose();
        DiagramLayout.SiteLink overLink = null;
        if (sites && popup == Popup.NONE && !help) {
            for (DiagramLayout.Site site : layout.sites) {
                siteHeader(g, mx, my, site);
            }
            if (hover == null) {
                overLink = linkAt(mx, my);
            }
        }
        //Bandeau du mode auto (spec §11, risque 8)
        if (snap.auto) {
            int w = Math.min(x1 - COL - 12, 300);
            int bx = COL + 1 + (x1 - COL - 1 - w) / 2;
            int lines = Math.max(1, Math.min(3, font.split(tr("auto_banner"), (int) ((w - 8) / 0.65F)).size()));
            g.fill(bx, BAR + 4, bx + w, BAR + 8 + lines * 7, 0xE0142420);
            g.renderOutline(bx, BAR + 4, w, 4 + lines * 7, 0xFF2E9E78);
            wrapped(g, tr("auto_banner"), bx + 4, BAR + 6, 0.65F, 0xFF9FE8CC, w - 8, 3);
        }
        legend(g, x1);
        endClip(g);
        if (hover != null) {
            nodeTooltip(hover, ch);
        } else if (overLink != null) {
            linkTooltip(overLink);
        }
    }

    // ---------------------------------------------------------------- 1.0.5-beta (lot 45, retour 66) : sites

    /** Cadre d'un site (fond un peu plus clair, liseré, en-tete) ; le texte et le bouton de l'en-tete sont dessines a l'echelle de l'ecran. */
    private static void siteFrame(GuiGraphics g, DiagramLayout.Site site) {
        int x0 = (int) site.x0, y0 = (int) site.y0, x1 = (int) site.x1, y1 = (int) site.y1;
        g.fill(x0, y0, x1, y1, 0x60162029);
        g.fill(x0, y0, x1, y0 + DiagramLayout.SITE_HEAD, site.here ? 0xC0223A33 : 0xC01C2731);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, site.here ? 0xFF2E9E78 : 0xFF34414D);
        g.fill(x0 + 1, y0 + DiagramLayout.SITE_HEAD, x1 - 1, y0 + DiagramLayout.SITE_HEAD + 1, 0xFF2A3540);
    }

    /** En-tete d'un site : nom, machines, en pause ; bouton « Reorganiser » de ce site (clic en coordonnees de l'ecran). */
    private void siteHeader(GuiGraphics g, int mx, int my, DiagramLayout.Site site) {
        double hx0 = sx(site.x0), hy0 = sy(site.y0), hx1 = sx(site.x1), hh = DiagramLayout.SITE_HEAD * zoom;
        float k = (float) Math.max(0.5, Math.min(0.75, 0.75 * zoom / 0.85));
        int bw = (int) Math.round(Math.min(52, Math.max(26, 52 * zoom))), bh = (int) Math.max(7, Math.round(hh - 4));
        int bx = (int) Math.round(hx1 - bw - 3), by = (int) Math.round(hy0 + (hh - bh) / 2);
        Component name = tr(site.here ? "site_here" : "site_name", site.name(), site.machines);
        float tw = (float) (bx - hx0 - 8);
        GuiDraw.text(g, font, name, (float) hx0 + 4, (float) (hy0 + (hh - 6 * k / 0.75) / 2), k, site.here ? 0xFF9FE8CC : D_TEXT, tw);
        if (site.paused > 0 && tw > 60) {
            Component p = tr("site_paused", site.paused);
            float pw = font.width(p) * k;
            float px = (float) hx0 + 6 + Math.min(font.width(name) * k, tw - pw - 4);
            if (px + pw < bx - 4) {
                GuiDraw.text(g, font, p, px + 4, (float) (hy0 + (hh - 6 * k / 0.75) / 2), k, WARN, pw + 1);
            }
        }
        //Deja reorganise (seul ou avec tous) : le bouton ne sert plus que s'il reste des places deplacees a la main dans ce site
        boolean moved = false;
        for (DiagramLayout.Box b : site.boxes) {
            moved |= snap.layout.containsKey(b.id);
        }
        boolean enabled = moved || !arranged && !arrangedSites.contains(site.id);
        boolean hover = enabled && over(mx, my, bx, by, bw, bh);
        g.fill(bx, by, bx + bw, by + bh, !enabled ? 0xFF1A2027 : hover ? 0xFF3A4C5C : 0xFF2A3540);
        g.renderOutline(bx, by, bw, bh, !enabled ? 0xFF242C34 : hover ? 0xFF6F8EA6 : 0xFF3C4A57);
        GuiDraw.center(g, font, tr("site_arrange"), bx + bw / 2F, by + (bh - 6 * k / 0.75F) / 2F, k * 0.95F, enabled ? D_TEXT : 0xFF59666A, bw - 3);
        if (enabled) {
            click(bx, by, bw, bh, () -> reorganizeSite(site));
        }
        if (over(mx, my, bx, by, bw, bh)) {
            tooltip(List.of(tr("site_arrange_tip", site.name())));
        } else if (over(mx, my, (int) hx0, (int) hy0, (int) (bx - hx0), (int) hh)) {
            BlockPos at = BlockPos.containing(site.wx, site.wy, site.wz);
            List<Component> lines = new ArrayList<>();
            lines.add(tr("site_name", site.name(), site.machines).withStyle(ChatFormatting.WHITE));
            lines.add(tr("tip_pos", at.getX(), at.getY(), at.getZ()).withStyle(ChatFormatting.GRAY));
            if (site.paused > 0) {
                lines.add(tr("site_paused_tip", site.paused).withStyle(ChatFormatting.GOLD));
            }
            if (site.detached > 0) {
                lines.add(tr("site_detached_tip", site.detached).withStyle(ChatFormatting.GRAY));
            }
            lines.add(tr("site_tip", DiagramLayout.SITE_GAP).withStyle(ChatFormatting.DARK_GRAY));
            tooltip(lines);
        }
    }

    /** Bout d'un lien entre deux sites : du noeud jusqu'au point d'attache du trait sur le cadre de son site. */
    private void stub(GuiGraphics g, DiagramLayout.Edge e, int color) {
        DiagramLayout.SiteLink link = linkOf(e);
        if (link == null) {
            return;
        }
        stubFrom(g, e.a(), link, color);
        stubFrom(g, e.b(), link, color);
    }

    private static void stubFrom(GuiGraphics g, DiagramLayout.Box box, DiagramLayout.SiteLink link, int color) {
        boolean first = box.site == link.a;
        double px = first ? link.ax : link.bx, py = first ? link.ay : link.by;
        double bx = Math.round(box.x), by = Math.round(box.y);
        //Horizontal puis vertical vers un bord vertical du cadre ; vertical puis horizontal vers le bas ou le haut
        boolean side = py != box.site.y1 && py != box.site.y0;
        double[] p = side ? new double[]{bx, by, bx, py, px, py} : new double[]{bx, by, px, by, px, py};
        polyline(g, p, color);
    }

    @Nullable
    private DiagramLayout.SiteLink linkOf(DiagramLayout.Edge e) {
        for (DiagramLayout.SiteLink l : layout.siteLinks) {
            if (l.edges.contains(e)) {
                return l;
            }
        }
        return null;
    }

    /** Trace orthogonal (points x0, y0, x1, y1, ...), 2 px. */
    private static void polyline(GuiGraphics g, double[] p, int color) {
        for (int i = 0; i + 3 < p.length; i += 2) {
            int x0 = (int) p[i], y0 = (int) p[i + 1], x1 = (int) p[i + 2], y1 = (int) p[i + 3];
            g.fill(Math.min(x0, x1) - 1, Math.min(y0, y1) - 1, Math.max(x0, x1) + 1, Math.max(y0, y1) + 1, color);
        }
    }

    /** Pastille de la vraie distance sur le trait entre deux sites. */
    private void distanceLabel(GuiGraphics g, DiagramLayout.SiteLink link) {
        Component t = tr("site_distance", Math.round(link.distance));
        float w = font.width(t) * 0.7F;
        int x = (int) Math.round(link.labelX - w / 2 - 3), y = (int) Math.round(link.labelY - 5);
        g.fill(x, y, (int) (x + w + 6), y + 10, 0xF0101820);
        g.renderOutline(x, y, (int) (w + 6), 10, 0xFF4A5A68);
        GuiDraw.text(g, font, t, x + 3, y + 2, 0.7F, GOLD, w + 1);
    }

    /** Trait (pastille de distance) sous la souris. */
    @Nullable
    private DiagramLayout.SiteLink linkAt(int mx, int my) {
        if (!inCanvas(mx, my)) {
            return null;
        }
        for (DiagramLayout.SiteLink l : layout.siteLinks) {
            Component t = tr("site_distance", Math.round(l.distance));
            double w = (font.width(t) * 0.7 + 6) * zoom;
            if (Math.abs(mx - sx(l.labelX)) <= w / 2 + 1 && Math.abs(my - sy(l.labelY)) <= 6 * zoom + 1) {
                return l;
            }
        }
        return null;
    }

    private void linkTooltip(DiagramLayout.SiteLink l) {
        List<Component> lines = new ArrayList<>();
        lines.add(tr("site_link", l.a.name(), l.b.name()).withStyle(ChatFormatting.WHITE));
        lines.add(tr("site_link_distance", Math.round(l.distance)).withStyle(ChatFormatting.GOLD));
        if (l.cable > 0) {
            lines.add(tr("site_link_cable", l.cable).withStyle(ChatFormatting.GRAY));
        }
        if (l.edges.size() > 1) {
            lines.add(tr("site_link_count", l.edges.size()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(tr("site_link_tip").withStyle(ChatFormatting.DARK_GRAY));
        tooltip(lines);
    }

    private boolean participates(DiagramLayout.Box b, int uid) {
        for (long k : b.members) {
            if (snap.role(k, uid) != Role.NONE) {
                return true;
            }
        }
        return false;
    }

    /** Role commun d'un noeud dans un canal (groupe : celui du premier ; reglages identiques par construction). */
    private Role role(DiagramLayout.Box b, int uid) {
        return b.members.length == 0 ? Role.NONE : snap.role(b.members[0], uid);
    }

    /** Lien orthogonal en « Z » ({@link DiagramLayout#route} : vertical, horizontal a mi-hauteur, vertical), 2 px. */
    private static void route(GuiGraphics g, DiagramLayout.Box a, DiagramLayout.Box b, int color) {
        double[] p = DiagramLayout.route(a, b);
        for (int i = 0; i + 3 < p.length; i += 2) {
            int x0 = (int) p[i], y0 = (int) p[i + 1], x1 = (int) p[i + 2], y1 = (int) p[i + 3];
            g.fill(Math.min(x0, x1) - 1, Math.min(y0, y1) - 1, Math.max(x0, x1) + 1, Math.max(y0, y1) + 1, color);
        }
    }

    /** Paquets blancs qui avancent le long d'un lien (vitesse selon le debit mesure). */
    private static void packets(GuiGraphics g, DiagramLayout.Box a, DiagramLayout.Box b, double phase) {
        double[] p = DiagramLayout.route(a, b);
        double len = 0;
        for (int i = 0; i + 3 < p.length; i += 2) {
            len += Math.abs(p[i + 2] - p[i]) + Math.abs(p[i + 3] - p[i + 1]);
        }
        if (len < 20) {
            return;
        }
        for (double d = phase % 14; d < len; d += 14) {
            if (d < 14 || d > len - 14) {
                continue;
            }
            double rest = d;
            for (int i = 0; i + 3 < p.length; i += 2) {
                double seg = Math.abs(p[i + 2] - p[i]) + Math.abs(p[i + 3] - p[i + 1]);
                if (rest <= seg) {
                    int x = (int) (p[i] + Math.signum(p[i + 2] - p[i]) * rest), y = (int) (p[i + 1] + Math.signum(p[i + 3] - p[i + 1]) * rest);
                    g.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
                    break;
                }
                rest -= seg;
            }
        }
    }

    private void node(GuiGraphics g, DiagramLayout.Box b, @Nullable DiagramSnapshot.Channel ch, boolean hover) {
        int x = (int) b.x - 14, y = (int) b.y - 14;
        Role role = ch == null ? Role.NONE : role(b, ch.uid());
        //1.0.5-beta (lot 45) : deconnectee (retour 63) ou toute en pause (retour 66) : grisee quel que soit le canal
        boolean paused = b.paused > 0 && b.paused == b.members.length;
        boolean dim = ch != null && role == Role.NONE || b.detached || paused;
        boolean focus = panel == Panel.POINT && ptBox == b;
        if (b.group()) {
            g.renderOutline(x + 2, y - 2, 28, 28, 0xFF2A3540);
        }
        g.fill(x, y, x + 28, y + 28, dim ? 0xFF131A20 : hover ? 0xFF223040 : 0xFF1B2530);
        int frame = focus ? GOLD : role != Role.NONE ? roleColor(role) : hover ? 0xFF4A5A68 : 0xFF34414D;
        g.renderOutline(x, y, 28, 28, frame);
        Item item = item(b.item);
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 4, 0);
        g.pose().scale(1.25F, 1.25F, 1);
        g.renderItem(new ItemStack(item == Items.AIR ? Items.BARRIER : item), 0, 0);
        g.pose().popPose();
        if (dim) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(x + 1, y + 1, x + 27, y + 27, 0xA00E141A);
            g.pose().popPose();
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 210);
        if (b.group()) {
            Component n = Component.literal("×" + b.members.length);
            float w = font.width(n) * 0.7F;
            g.fill((int) (x + 26 - w), y + 19, x + 28, y + 27, 0xE0101418);
            GuiDraw.text(g, font, n, x + 27 - w, y + 20, 0.7F, 0xFFFFFFFF);
        }
        if (role != Role.NONE) {
            int c = roleColor(role);
            g.fill(x - 3, y + 10, x + 4, y + 18, c);
            arrow(g, x - 2, y + 11, role);
        }
        if (b.detached) {
            //Fiche debranchee : pastille rouge sombre, prise barree
            g.fill(x + 19, y - 3, x + 31, y + 9, 0xFF3A1F22);
            g.renderOutline(x + 19, y - 3, 12, 12, 0xFFB5413A);
            unplugged(g, x + 21, y - 1);
        } else if (b.paused > 0) {
            //Pause : deux barres (chunk non charge) ; un groupe en partie en pause : pastille plus claire
            g.fill(x + 19, y - 3, x + 31, y + 9, paused ? 0xFF3A3220 : 0xFF2A2A22);
            g.renderOutline(x + 19, y - 3, 12, 12, paused ? WARN : 0xFF8A7A50);
            g.fill(x + 22, y, x + 24, y + 6, paused ? WARN : 0xFF8A7A50);
            g.fill(x + 26, y, x + 28, y + 6, paused ? WARN : 0xFF8A7A50);
        }
        g.pose().popPose();
        nodeLabel(g, b, y + 31, dim ? D_FAINT : D_TEXT);
        if (b.detached || paused) {
            Component state = tr(b.detached ? "detached_short" : "paused_short");
            GuiDraw.center(g, font, state, (float) b.x, y + 45, 0.6F, b.detached ? 0xFFB5413A : WARN, 84);
        }
    }

    /** Prise debranchee 8 x 8 (fiche et cable separes par un trait). */
    private static void unplugged(GuiGraphics g, int x, int y) {
        int c = 0xFFE0A0A0;
        g.fill(x, y + 3, x + 3, y + 5, c);
        g.fill(x + 2, y + 2, x + 3, y + 6, c);
        g.fill(x + 5, y + 2, x + 6, y + 6, c);
        g.fill(x + 5, y + 3, x + 8, y + 5, c);
        g.fill(x + 3, y + 7, x + 5, y + 8, 0xFFB5413A);
        g.fill(x + 3, y, x + 5, y + 1, 0xFFB5413A);
    }

    /** Nom sous le noeud (sans « xN » : le badge le dit), sur deux lignes au plus, centrees. */
    private void nodeLabel(GuiGraphics g, DiagramLayout.Box b, int y, int color) {
        Item item = item(b.item);
        Component name = item == Items.AIR ? tr("machine") : new ItemStack(item).getHoverName();
        List<String> lines = new ArrayList<>();
        font.getSplitter().splitLines(name, (int) (84 / 0.7F), Style.EMPTY).forEach(l -> lines.add(l.getString()));
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            String line = i == 1 && lines.size() > 2 ? String.join(" ", lines.subList(1, lines.size())) : lines.get(i);
            GuiDraw.center(g, font, Component.literal(line), (float) b.x, y + i * 7, 0.7F, color, 84);
        }
    }

    /** Petite fleche blanche du role : vers la machine (sortie), vers le cable (entree), double (les deux). */
    private static void arrow(GuiGraphics g, int x, int y, Role role) {
        int c = 0xFFFFFFFF;
        if (role == Role.OUTPUT) {
            g.fill(x, y, x + 1, y + 5, c);
            g.fill(x + 1, y + 1, x + 2, y + 4, c);
            g.fill(x + 2, y + 2, x + 3, y + 3, c);
        } else if (role == Role.INPUT) {
            g.fill(x + 2, y, x + 3, y + 5, c);
            g.fill(x + 1, y + 1, x + 2, y + 4, c);
            g.fill(x, y + 2, x + 1, y + 3, c);
        } else {
            g.fill(x, y + 2, x + 5, y + 3, c);
            g.fill(x + 1, y + 1, x + 2, y + 4, c);
            g.fill(x + 3, y + 1, x + 4, y + 4, c);
        }
    }

    private void nodeTooltip(DiagramLayout.Box b, @Nullable DiagramSnapshot.Channel ch) {
        List<Component> lines = new ArrayList<>();
        if (!b.machine) {
            lines.add(tr("junction").withStyle(ChatFormatting.GRAY));
            tooltip(lines);
            return;
        }
        lines.add(machineName(b).copy().withStyle(ChatFormatting.WHITE));
        //1.0.5-beta (lot 45) : deconnectee (retour 63), en pause (retour 66)
        if (b.detached) {
            lines.add(tr("tip_detached").withStyle(ChatFormatting.RED));
            lines.add(tr("tip_detached_fix").withStyle(ChatFormatting.GRAY));
        }
        if (b.paused > 0) {
            lines.add((b.paused == b.members.length ? tr("tip_paused") : tr("tip_paused_some", b.paused, b.members.length)).withStyle(ChatFormatting.GOLD));
        }
        if (ch != null) {
            long key = b.members[0];
            PointConfig cfg = snap.config(key, ch.uid());
            if (cfg.role == Role.NONE) {
                lines.add(tr("tip_none", channelName(ch)).withStyle(ChatFormatting.GRAY));
            } else {
                Component face = cfg.face == PointConfig.TOUCHED ? tr("face_touched_short") : faceName(cfg.face);
                lines.add(tr("tip_role", roleName(cfg.role), face).withStyle(cfg.role == Role.OUTPUT ? ChatFormatting.BLUE
                      : cfg.role == Role.INPUT ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE));
                DiagramSnapshot.Point p = snap.point(key, ch.uid());
                double r = 0;
                for (long k : b.members) {
                    DiagramSnapshot.Point q = snap.point(k, ch.uid());
                    r += q == null ? 0 : q.rate;
                }
                if (p != null && !ch.def.kind.body()) {
                    lines.add(tr("tip_rate", rate(ch.def.kind, r, Double.NaN)).withStyle(ChatFormatting.GRAY));
                }
            }
        }
        BlockPos pos = BlockPos.of(b.members[0]);
        lines.add(tr("tip_pos", pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(tr(b.group() ? "tip_hint_group" : "tip_hint").withStyle(ChatFormatting.DARK_GRAY));
        tooltip(lines);
    }

    private void legend(GuiGraphics g, int x1) {
        int lx = COL + 6, y = height - STATUS - 9;
        //1.0.5-beta (lot 45) : deconnectee et en pause dans la legende (seulement si le reseau en a)
        boolean det = false, pause = false;
        for (DiagramSnapshot.Machine m : snap.machines) {
            det |= m.detached;
            pause |= m.paused;
        }
        int[] colors = {IN, OUT, BOTH, 0xFFB5413A, WARN};
        String[] keys = {"legend_in", "legend_out", "legend_both", "legend_detached", "legend_paused"};
        for (int i = 0; i < 5; i++) {
            if (i == 3 && !det || i == 4 && !pause) {
                continue;
            }
            Component t = tr(keys[i]);
            float w = font.width(t) * 0.7F;
            if (lx + 8 + w > x1 - 2) {
                break;
            }
            g.fill(lx, y, lx + 6, y + 6, colors[i]);
            GuiDraw.text(g, font, t, lx + 8, y, 0.7F, D_DIM);
            lx += (int) (12 + w);
        }
    }

    // ---------------------------------------------------------------- barre d'etat

    private void statusBar(GuiGraphics g) {
        int W = width, H = height;
        g.fill(0, H - STATUS, W, H, D_BAR);
        g.fill(0, H - STATUS, W, H - STATUS + 1, D_LINE);
        Component warning = worstWarning();
        Component warn = warning == null ? null : Component.literal("⚠ ").append(warning);
        float ww = warn == null ? 0 : Math.min(font.width(warn) * 0.75F, W * 0.4F);
        Component text;
        if (flash != null && System.currentTimeMillis() < flashUntil) {
            text = flash;
        } else if (panel == Panel.POINT && ptBox != null && snap.channel(selUid) != null) {
            text = pointSummary();
        } else {
            DiagramSnapshot.Channel ch = snap.channel(selUid);
            text = ch == null ? tr("status_none") : channelSummary(ch);
        }
        GuiDraw.text(g, font, text, 5, H - 12, 0.75F, D_TEXT, W - 15 - ww);
        if (warn != null) {
            GuiDraw.text(g, font, warn, W - 5 - ww, H - 12, 0.75F, WARN, W * 0.4F);
        }
    }

    private Component channelSummary(DiagramSnapshot.Channel ch) {
        Component mode = ch.def.kind.body() ? tr("mode.body") : tr("mode." + ch.def.mode.name().toLowerCase(Locale.ROOT));
        return tr("status_channel", channelName(ch), rate(ch.def.kind, ch.rate, ch.body), ch.sources, ch.outputs, mode);
    }

    private Component pointSummary() {
        DiagramSnapshot.Channel ch = snap.channel(selUid);
        long key = ptBox.members[0];
        PointConfig cfg = snap.config(key, selUid);
        Component face = cfg.face == PointConfig.TOUCHED ? tr("face_touched_short") : faceName(cfg.face);
        DiagramSnapshot.Point p = snap.point(key, selUid);
        return tr("status_point", machineName(ptBox), roleName(cfg.role), channelName(ch), face,
              rate(ch.def.kind, p == null ? 0 : p.rate, Double.NaN));
    }

    /** Avertissement le plus grave : type suspendu, canal sans sortie, en attente, maillon faible. */
    @Nullable
    private Component worstWarning() {
        Component worst = null;
        int rank = 0;
        for (DiagramSnapshot.Channel c : snap.channels) {
            int r = c.state == LogisticNetwork.State.SUSPENDED ? 4 : c.noOutput && !c.auto && c.state == LogisticNetwork.State.ACTIVE ? 3
                  : c.state == LogisticNetwork.State.WAITING ? 2 : 0;
            if (r > rank) {
                rank = r;
                worst = warning(c);
            }
        }
        if (worst == null && snap.weakCount > 0) {
            worst = tr("warn_weak", snap.weakCount, levelName(snap.level));
        }
        return worst;
    }

    // ---------------------------------------------------------------- panneaux communs

    private int panelHeader(GuiGraphics g, int x0, Component title, int accent) {
        int W = width;
        g.fill(x0, BAR + 1, W, height - STATUS, D_PANEL);
        g.fill(x0, BAR + 1, x0 + 1, height - STATUS, D_LINE);
        g.fill(x0 + 1, BAR + 1, W, BAR + 14, D_BAR);
        g.fill(x0 + 1, BAR + 1, x0 + 3, BAR + 14, accent);
        return x0;
    }

    private void label(GuiGraphics g, Component t, int x, int y, int maxW) {
        GuiDraw.text(g, font, t, x, y, 0.7F, D_DIM, maxW);
    }

    /** Champ numerique : - valeur + (Maj : grand pas) ; clic sur la valeur = saisie au clavier ; {@code shown} = texte affiche. */
    private void numField(GuiGraphics g, int mx, int my, int x, int y, int w, long value, long min, long max, long step, long bigStep,
          Component shown, boolean infiniteAtMax, LongConsumer set) {
        long s = hasShiftDown() ? bigStep : step;
        boolean inf = infiniteAtMax ? value >= max : false;
        button(g, mx, my, x, y, 11, 12, Component.literal("-"), value > min, () -> {
            if (infiniteAtMax && value >= max) {
                set.accept(Math.max(min, bigStep * 4));
            } else {
                set.accept(Math.max(min, value - s));
            }
        });
        int fx = x + 12, fw = w - 24;
        boolean hover = over(mx, my, fx, y, fw, 12);
        g.fill(fx, y, fx + fw, y + 12, hover ? 0xFF16222B : 0xFF0E1419);
        if (hover) {
            g.renderOutline(fx, y, fw, 12, 0xFF4A6A78);
        }
        GuiDraw.center(g, font, shown, fx + fw / 2F, y + 3, 0.75F, D_TEXT, fw - 4);
        click(fx, y, fw, 12, () -> startText(fx, y, fw, inf ? "" : Long.toString(value), 12, txt -> {
            if (txt.isEmpty() || txt.equals("∞")) {
                set.accept(infiniteAtMax ? max : min);
                return;
            }
            try {
                set.accept(Math.max(min, Math.min(max, Long.parseLong(txt.replace(" ", "")))));
            } catch (NumberFormatException ignored) {
            }
        }));
        if (hover) {
            tooltip(List.of(Component.translatable("gui.pioneers.type_value").withStyle(ChatFormatting.GRAY)));
        }
        button(g, mx, my, x + w - 11, y, 11, 12, Component.literal("+"), !inf && value < max, () -> {
            long next = value + s;
            set.accept(next >= max || next < value ? max : next);
        });
    }

    private void filterSlots(GuiGraphics g, int mx, int my, int x, int y, int size, Filter filter, int max, ChannelKind kind, boolean forPoint) {
        for (int i = 0; i < max; i++) {
            int sx = x + i * (size + 1);
            boolean hover = over(mx, my, sx, y, size, size);
            g.fill(sx, y, sx + size, y + size, 0xFF0E1419);
            g.renderOutline(sx, y, size, size, hover ? 0xFF4A6A78 : 0xFF2F3B46);
            if (i < filter.entries.size()) {
                Filter.Entry e = filter.entries.get(i);
                entryIcon(g, e, kind, sx, y, size);
                final int index = i;
                click(sx, y, size, size, () -> removeEntry(forPoint, index));
                if (hover) {
                    tooltip(List.of(Component.literal(e.key()).withStyle(ChatFormatting.WHITE), tr("filter_remove").withStyle(ChatFormatting.DARK_GRAY)));
                }
            } else if (i == filter.entries.size()) {
                GuiDraw.center(g, font, Component.literal("+"), sx + size / 2F, y + (size - 7) / 2F, 0.8F, hover ? D_TEXT : D_FAINT, size);
                click(sx, y, size, size, () -> openPicker(forPoint));
                if (hover) {
                    tooltip(List.of(tr("filter_add")));
                }
            }
        }
    }

    private void entryIcon(GuiGraphics g, Filter.Entry e, ChannelKind kind, int x, int y, int size) {
        ItemStack icon = ItemStack.EMPTY;
        if (!e.isTag() && !e.isMod()) {
            ResourceLocation rl = ResourceLocation.tryParse(e.key());
            if (rl != null && kind == ChannelKind.ITEM) {
                icon = new ItemStack(BuiltInRegistries.ITEM.get(rl));
            } else if (rl != null && kind == ChannelKind.FLUID && ForgeRegistries.FLUIDS.containsKey(rl)) {
                Item bucket = ForgeRegistries.FLUIDS.getValue(rl).getBucket();
                icon = bucket == Items.AIR ? ItemStack.EMPTY : new ItemStack(bucket);
            }
        }
        IngredientIndex.Entry known = icon.isEmpty() ? IngredientIndex.find(kind, e.key()) : null;
        if (known != null && (known.fluid() != null || known.gas() != null)) {
            //1.12.1 (F1) : fluide sans seau, gaz : texture teintee
            float k = (size - 2) / 16F;
            g.pose().pushPose();
            g.pose().translate(x + 1, y + 1, 0);
            g.pose().scale(k, k, 1);
            drawIngredient(g, known, 0, 0);
            g.pose().popPose();
        } else if (!icon.isEmpty() && icon.getItem() != Items.AIR) {
            float k = (size - 2) / 16F;
            g.pose().pushPose();
            g.pose().translate(x + 1, y + 1, 0);
            g.pose().scale(k, k, 1);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        } else {
            String s = e.isTag() ? "#" : e.isMod() ? "@" : e.bare().substring(e.bare().indexOf(':') + 1, Math.min(e.bare().length(), e.bare().indexOf(':') + 3));
            glyph(g, kind, x + 1, y + 1, 0.5F, false);
            GuiDraw.center(g, font, Component.literal(s), x + size / 2F, y + (size - 6) / 2F, 0.6F, D_TEXT, size - 1);
        }
    }

    private void removeEntry(boolean forPoint, int index) {
        Filter f = forPoint ? ptDraft.filter : chDraft.filter;
        List<Filter.Entry> list = new ArrayList<>(f.entries);
        if (index < list.size()) {
            list.remove(index);
        }
        Filter next = list.isEmpty() && !f.blacklist ? Filter.EMPTY : new Filter(f.blacklist, list);
        if (forPoint) {
            ptDraft.filter = next;
        } else {
            chDraft.filter = next;
        }
    }

    private void addEntry(String key) {
        String k = key.trim();
        if (k.isEmpty() || k.length() > 128) {
            return;
        }
        if (!k.startsWith("#") && !k.startsWith("@") && !k.contains(":")) {
            k = "minecraft:" + k;
        }
        boolean forPoint = pickerForPoint;
        Filter f = forPoint ? ptDraft.filter : chDraft.filter;
        int max = forPoint ? Filter.POINT_SIZE : Filter.CHANNEL_SIZE;
        List<Filter.Entry> list = new ArrayList<>(f.entries);
        for (Filter.Entry e : list) {
            if (e.key().equals(k)) {
                return;
            }
        }
        if (list.size() >= max) {
            return;
        }
        list.add(new Filter.Entry(k, null));
        Filter next = new Filter(f.blacklist, list);
        if (forPoint) {
            ptDraft.filter = next;
        } else {
            chDraft.filter = next;
        }
    }

    private static boolean filtered(ChannelKind kind) {
        return kind == ChannelKind.ITEM || kind == ChannelKind.FLUID || kind == ChannelKind.GAS || kind == ChannelKind.DATA;
    }

    /** Debut d'un panneau qui defile ; rend le y de depart du contenu. */
    private int beginPanelBody(GuiGraphics g, int x0, int bottom) {
        int top = BAR + 15;
        panelScroll = Mth.clamp(panelScroll, 0, Math.max(0, panelContent - (bottom - top)));
        beginClip(g, x0 + 1, top, width, bottom);
        return top + 3 - panelScroll;
    }

    private void endPanelBody(GuiGraphics g, int y, int x0, int bottom) {
        int top = BAR + 15;
        panelContent = y + panelScroll - top + 2;
        endClip(g);
        if (panelContent > bottom - top) {
            int track = bottom - top, bar = Math.max(8, track * track / panelContent);
            int by = top + (track - bar) * panelScroll / Math.max(1, panelContent - track);
            g.fill(width - 2, by, width - 1, by + bar, 0xFF4A5A68);
        }
    }

    // ---------------------------------------------------------------- panneau d'un canal (maquette 2)

    private void openChannel(DiagramSnapshot.Channel c) {
        cancelText();
        panel = Panel.CHANNEL;
        chDraft = c.def.copy();
        chNew = false;
        panelScroll = 0;
        selUid = c.uid();
    }

    private void newChannel() {
        cancelText();
        ChannelDef def = new ChannelDef(0, ChannelKind.ITEM);
        for (ChannelKind k : ChannelKind.all()) {
            if (snap.level.permits(k)) {
                def.kind = k;
                break;
            }
        }
        def.color = snap.channels.size() % PALETTE.length;
        panel = Panel.CHANNEL;
        chDraft = def;
        chNew = true;
        panelScroll = 0;
    }

    private void channelPanel(GuiGraphics g, int mx, int my) {
        int W = width, x0 = W - CH_PANEL, bottom = height - STATUS - 16;
        ChannelDef d = chDraft;
        int accent = 0xFF000000 | PALETTE[Math.floorMod(d.color, PALETTE.length)];
        panelHeader(g, x0, Component.empty(), accent);
        Component head = chNew ? tr("panel_new_channel") : tr("panel_channel", defName(d));
        boolean reorder = !chNew && snap.canEdit && !snap.auto;
        GuiDraw.text(g, font, head, x0 + 6, BAR + 4, 0.85F, D_TEXT, W - x0 - (reorder ? 40 : 18));
        if (reorder) {
            button(g, mx, my, W - 36, BAR + 2, 10, 10, Component.literal("↑"), true, () -> move(-1));
            button(g, mx, my, W - 25, BAR + 2, 10, 10, Component.literal("↓"), true, () -> move(1));
            tooltip(mx, my, W - 36, BAR + 2, 21, 10, tr("reorder_tip"));
        }
        button(g, mx, my, W - 13, BAR + 2, 10, 10, Component.literal("x"), true, this::closePanel);
        int y = beginPanelBody(g, x0, bottom);
        boolean edit = snap.canEdit;
        //Nom
        label(g, tr("name"), x0 + 6, y + 2, 32);
        int fw = W - 6 - (x0 + 40);
        g.fill(x0 + 40, y, W - 6, y + 11, 0xFF0E1419);
        g.renderOutline(x0 + 40, y, fw, 11, over(mx, my, x0 + 40, y, fw, 11) ? 0xFF4A6A78 : 0xFF2F3B46);
        GuiDraw.text(g, font, defName(d), x0 + 43, y + 2, 0.75F, d.name.isEmpty() ? D_FAINT : D_TEXT, fw - 6);
        final int ny = y;
        if (edit) {
            click(x0 + 40, y, fw, 11, () -> startText(x0 + 40, ny, fw, d.name, ChannelDef.NAME_MAX, v -> d.name = v));
        }
        y += 14;
        //Type (7 boutons ; types non permis grises avec la raison)
        label(g, tr("kind"), x0 + 6, y + 2, 32);
        int kx = x0 + 40;
        for (ChannelKind k : ChannelKind.all()) {
            boolean sel = d.kind == k, ok = snap.level.permits(k);
            boolean hover = over(mx, my, kx, y, 15, 12);
            g.fill(kx, y, kx + 15, y + 12, sel ? 0xFF22303A : hover && ok ? 0xFF1C252E : 0xFF171E25);
            g.renderOutline(kx, y, 15, 12, sel ? ACCENT : 0xFF2F3B46);
            glyph(g, k, kx + 4, y + 3, 1, !ok);
            if (ok && edit) {
                click(kx, y, 15, 12, () -> d.kind = k);
            }
            if (hover) {
                tooltip(ok ? List.of(kindName(k)) : List.of(kindName(k), tr("kind_refused", levelName(snap.level)).withStyle(ChatFormatting.GOLD)));
            }
            kx += 16;
        }
        y += 15;
        //Couleur (16 teintes, 2 rangees)
        label(g, tr("color"), x0 + 6, y + 1, 32);
        for (int i = 0; i < PALETTE.length; i++) {
            int cx = x0 + 40 + (i % 8) * 12, cy = y + (i / 8) * 11;
            g.fill(cx, cy, cx + 9, cy + 9, 0xFF000000 | PALETTE[i]);
            if (Math.floorMod(d.color, PALETTE.length) == i) {
                g.renderOutline(cx - 1, cy - 1, 11, 11, 0xFFFFFFFF);
            }
            final int ci = i;
            if (edit) {
                click(cx, cy, 9, 9, () -> d.color = ci);
            }
        }
        y += 25;
        //Repartition (4 puces ; grisee pour la chaleur et la pression)
        label(g, tr("distribution"), x0 + 6, y, W - x0 - 12);
        y += 9;
        Distribution[] modes = Distribution.values();
        for (int i = 0; i < modes.length; i++) {
            Distribution m = modes[i];
            int cx = x0 + 6 + (i % 2) * 76, cy = y + (i / 2) * 14;
            if (d.kind.body()) {
                g.fill(cx, cy, cx + 74, cy + 12, 0xFF171E25);
                GuiDraw.center(g, font, tr("mode." + m.name().toLowerCase(Locale.ROOT)), cx + 37, cy + 3, 0.75F, 0xFF4A5654, 70);
            } else {
                chip(g, mx, my, cx, cy, 74, 12, tr("mode." + m.name().toLowerCase(Locale.ROOT)), d.mode == m, 0x2E9E78, () -> {
                    if (edit) {
                        d.mode = m;
                    }
                });
                tooltip(mx, my, cx, cy, 74, 12, tr("mode_tip." + m.name().toLowerCase(Locale.ROOT)));
            }
        }
        y += 29;
        if (d.kind.body()) {
            y += wrapped(g, tr("body_note"), x0 + 6, y, 0.6F, D_FAINT, W - x0 - 12, 3) + 3;
        }
        //Filtre
        if (filtered(d.kind)) {
            label(g, tr("filter"), x0 + 6, y + 1, 32);
            chip(g, mx, my, x0 + 40, y - 1, 52, 11, tr("whitelist"), !d.filter.blacklist, 0x2E9E78, () -> {
                if (edit) {
                    d.filter = d.filter.entries.isEmpty() ? Filter.EMPTY : new Filter(false, d.filter.entries);
                }
            });
            chip(g, mx, my, x0 + 94, y - 1, 52, 11, tr("blacklist"), d.filter.blacklist, 0xB5413A, () -> {
                if (edit) {
                    d.filter = new Filter(true, d.filter.entries);
                }
            });
            y += 12;
            filterSlots(g, mx, my, x0 + 6, y, 15, d.filter, Filter.CHANNEL_SIZE, d.kind, false);
            y += 18;
            GuiDraw.text(g, font, d.filter.entries.isEmpty() ? tr(d.filter.blacklist ? "filter_empty_black" : "filter_empty") : tr("filter_click"),
                  x0 + 6, y, 0.65F, D_FAINT, W - x0 - 12);
            y += 10;
        }
        //Condition (clic : valeur suivante)
        label(g, tr("condition"), x0 + 6, y + 1, 42);
        Component cond = tr("cond." + d.condition.name().toLowerCase(Locale.ROOT));
        int cw = W - 6 - (x0 + 50);
        button(g, mx, my, x0 + 50, y - 1, cw, 11, cond, edit, () -> d.condition = ChannelDef.Condition.byIndex(d.condition.ordinal() + 1));
        tooltip(mx, my, x0 + 50, y - 1, cw, 11, tr("condition_tip"));
        y += 13;
        //Debit max du canal (0 = debit du niveau)
        if (!d.kind.body()) {
            label(g, tr("cap"), x0 + 6, y + 1, 42);
            long levelRate = Math.max(1, snap.level.rate(d.kind));
            long cap = d.cap <= 0 ? levelRate : Math.min(d.cap, levelRate);
            Component shown = d.cap <= 0 || d.cap >= levelRate ? tr("cap_level", rate(d.kind, levelRate * (d.kind.perSecond() ? 1 : 20), Double.NaN))
                  : rate(d.kind, d.cap * (d.kind.perSecond() ? 1 : 20), Double.NaN);
            if (edit) {
                numField(g, mx, my, x0 + 50, y - 1, W - 6 - (x0 + 50), cap, 1, levelRate, Math.max(1, levelRate / 64), Math.max(1, levelRate / 8),
                      shown, true, v -> d.cap = v >= levelRate ? 0 : v);
            } else {
                GuiDraw.text(g, font, shown, x0 + 50, y + 1, 0.75F, D_TEXT, W - x0 - 56);
            }
            y += 15;
        }
        //Resume des points
        if (!chNew) {
            int in = 0, out = 0, both = 0;
            for (DiagramSnapshot.Machine m : snap.machines) {
                Role r = snap.role(m.key, d.uid);
                in += r == Role.INPUT ? 1 : 0;
                out += r == Role.OUTPUT ? 1 : 0;
                both += r == Role.BOTH ? 1 : 0;
            }
            y += wrapped(g, tr("points_summary", in, out, both), x0 + 6, y, 0.65F, D_DIM, W - x0 - 12, 2) + 2;
            y += wrapped(g, tr("points_hint"), x0 + 6, y, 0.6F, D_FAINT, W - x0 - 12, 3) + 2;
        }
        endPanelBody(g, y, x0, bottom);
        //Boutons
        int by = height - STATUS - 14;
        if (!chNew) {
            button(g, mx, my, x0 + 6, by, 50, 12, tr("delete"), edit, () -> ask(tr("confirm_delete", defName(d)), () -> {
                CompoundTag op = new CompoundTag();
                op.putString("op", "del");
                op.putInt("u", d.uid);
                send(op);
            }));
            int live = 0;
            for (DiagramSnapshot.Channel c : snap.channels) {
                live += c.auto ? 0 : 1;
            }
            button(g, mx, my, x0 + 58, by, 44, 12, tr("duplicate"), edit && live < 16 && !snap.auto, () -> {
                CompoundTag op = new CompoundTag();
                op.putString("op", "dup");
                op.putInt("u", d.uid);
                send(op);
            });
        }
        primary(g, mx, my, W - 52, by, 46, 12, tr("validate"), edit, () -> {
            cancelText();
            CompoundTag op = new CompoundTag();
            op.putString("op", "ch");
            op.put("d", d.save());
            send(op);
        });
    }

    private void move(int delta) {
        CompoundTag op = new CompoundTag();
        op.putString("op", "mv");
        op.putInt("u", chDraft.uid);
        op.putInt("d", delta);
        send(op);
    }

    // ---------------------------------------------------------------- panneau d'un point (maquette 3)

    private void openPoint(DiagramLayout.Box box) {
        cancelText();
        if (snap.channel(selUid) == null || box.members.length == 0) {
            return;
        }
        panel = Panel.POINT;
        ptBox = box;
        ptDraft = snap.config(box.members[0], selUid);
        applyGroup = true;
        panelScroll = 0;
    }

    private void closePanel() {
        cancelText();
        panel = Panel.NONE;
        chDraft = null;
        ptBox = null;
        ptDraft = null;
        if (popup == Popup.PICKER) {
            closePicker();
        }
    }

    private void pointPanel(GuiGraphics g, int mx, int my) {
        int W = width, x0 = W - PT_PANEL, bottom = height - STATUS - 16;
        DiagramSnapshot.Channel ch = snap.channel(selUid);
        if (ch == null) {
            closePanel();
            return;
        }
        PointConfig p = ptDraft;
        long key = ptBox.members[0];
        DiagramSnapshot.Machine m = snap.machine(key);
        panelHeader(g, x0, Component.empty(), roleColor(p.role));
        GuiDraw.text(g, font, tr("panel_point", machineName(ptBox)), x0 + 6, BAR + 4, 0.85F, D_TEXT, W - x0 - 20);
        button(g, mx, my, W - 13, BAR + 2, 10, 10, Component.literal("x"), true, this::closePanel);
        boolean edit = snap.canEdit;
        int y = beginPanelBody(g, x0, bottom);
        //Machine et canal
        Item item = item(ptBox.item);
        g.renderItem(new ItemStack(item == Items.AIR ? Items.BARRIER : item), x0 + 5, y);
        GuiDraw.text(g, font, tr("point_channel", channelName(ch)), x0 + 24, y + 1, 0.7F, D_TEXT, W - x0 - 28);
        Component plug = m == null ? Component.empty() : tr("point_plug", side(Direction.from3DDataValue(m.touchedFace())));
        GuiDraw.text(g, font, plug, x0 + 24, y + 9, 0.65F, D_DIM, W - x0 - 28);
        y += 21;
        //Role
        label(g, tr(ch.def.kind.body() ? "role_valve" : "role"), x0 + 6, y, W - x0 - 12);
        y += 8;
        Role[] roles = Role.values();
        for (int i = 0; i < roles.length; i++) {
            Role r = roles[i];
            chip(g, mx, my, x0 + 6 + i * 42, y, 40, 12, roleName(r), p.role == r, roleColor(r) & 0xFFFFFF, () -> {
                if (edit) {
                    p.role = r;
                }
            });
            tooltip(mx, my, x0 + 6 + i * 42, y, 40, 12, tr("role_tip." + r.name().toLowerCase(Locale.ROOT)));
        }
        y += 15;
        //Tirer (entree)
        if (!ch.def.kind.body()) {
            boolean in = p.role.in();
            boolean hover = over(mx, my, x0 + 6, y - 1, W - x0 - 12, 9);
            g.fill(x0 + 6, y, x0 + 13, y + 7, 0xFF0E1419);
            g.renderOutline(x0 + 6, y, 7, 7, hover && in ? 0xFF7FE6C0 : 0xFF2F3B46);
            if (p.pull && in) {
                g.fill(x0 + 8, y + 2, x0 + 11, y + 5, ACCENT);
            }
            GuiDraw.text(g, font, tr("pull"), x0 + 16, y, 0.6F, in ? D_DIM : D_FAINT, W - x0 - 20);
            if (in && edit) {
                click(x0 + 6, y - 1, W - x0 - 12, 9, () -> p.pull = !p.pull);
            }
            tooltip(mx, my, x0 + 6, y - 1, W - x0 - 12, 9, tr("pull_tip"));
            y += 11;
        }
        //Face de la machine (patron du cube)
        label(g, tr("face_title"), x0 + 6, y, W - x0 - 12);
        y += 8;
        y = cube(g, mx, my, x0, y, ch, p, m, edit);
        //Priorite, poids, limites, filtre propre (pas pour la chaleur et la pression)
        if (!ch.def.kind.body()) {
            label(g, tr("priority"), x0 + 6, y + 2, 34);
            if (edit) {
                numField(g, mx, my, x0 + 40, y, 70, p.priority, -99, 99, 1, 10, Component.literal(Integer.toString(p.priority)), false,
                      v -> p.priority = (int) v);
            } else {
                GuiDraw.text(g, font, Component.literal(Integer.toString(p.priority)), x0 + 52, y + 2, 0.75F, D_TEXT);
            }
            label(g, tr("weight"), x0 + 114, y + 2, 20);
            if (edit) {
                numField(g, mx, my, x0 + 134, y, W - 6 - (x0 + 134), p.weight, 1, 16, 1, 4, Component.literal(Integer.toString(p.weight)), false,
                      v -> p.weight = (int) v);
            } else {
                GuiDraw.text(g, font, Component.literal(Integer.toString(p.weight)), x0 + 146, y + 2, 0.75F, D_TEXT);
            }
            tooltip(mx, my, x0 + 114, y, W - 6 - x0 - 114, 12, tr("weight_tip"));
            y += 14;
            String unit = ch.def.kind == ChannelKind.ITEM ? "" : " mB";
            if (p.role.in()) {
                label(g, tr("keep"), x0 + 6, y + 2, 62);
                Component shown = Component.literal(p.keep + unit);
                if (edit) {
                    numField(g, mx, my, x0 + 70, y, 70, p.keep, 0, 1_000_000_000L, ch.def.kind == ChannelKind.ITEM ? 1 : 100,
                          ch.def.kind == ChannelKind.ITEM ? 16 : 1000, shown, false, v -> p.keep = v);
                } else {
                    GuiDraw.text(g, font, shown, x0 + 82, y + 2, 0.75F, D_TEXT);
                }
                tooltip(mx, my, x0 + 6, y, 62, 12, tr("keep_tip"));
                y += 13;
            }
            if (p.role.out()) {
                label(g, tr("fill_to"), x0 + 6, y + 2, 62);
                boolean inf = p.fillTo == PointConfig.UNLIMITED;
                Component shown = inf ? Component.literal("∞") : Component.literal(p.fillTo + unit);
                long max = 1_000_000_000L;
                if (edit) {
                    numField(g, mx, my, x0 + 70, y, 70, inf ? max : p.fillTo, 1, max, ch.def.kind == ChannelKind.ITEM ? 1 : 100,
                          ch.def.kind == ChannelKind.ITEM ? 16 : 1000, shown, true, v -> p.fillTo = v >= max ? PointConfig.UNLIMITED : v);
                } else {
                    GuiDraw.text(g, font, shown, x0 + 82, y + 2, 0.75F, D_TEXT);
                }
                tooltip(mx, my, x0 + 6, y, 62, 12, tr("fill_tip"));
                y += 13;
            }
            label(g, tr("max_rate"), x0 + 6, y + 2, 62);
            long lim = Math.max(1, snap.level.rate(ch.def.kind));
            boolean infRate = p.maxRate <= 0 || p.maxRate >= lim;
            Component shownRate = infRate ? Component.literal("∞" + (ch.def.kind.perSecond() ? "/s" : "/t"))
                  : Component.literal(compact(p.maxRate) + (ch.def.kind.perSecond() ? "/s" : "/t"));
            if (edit) {
                numField(g, mx, my, x0 + 70, y, 70, infRate ? lim : p.maxRate, 1, lim, Math.max(1, lim / 64), Math.max(1, lim / 8), shownRate, true,
                      v -> p.maxRate = v >= lim ? 0 : v);
            } else {
                GuiDraw.text(g, font, shownRate, x0 + 82, y + 2, 0.75F, D_TEXT);
            }
            y += 14;
            if (filtered(ch.def.kind)) {
                label(g, tr("own_filter"), x0 + 6, y + 3, 62);
                filterSlots(g, mx, my, x0 + 74, y, 13, p.filter, Filter.POINT_SIZE, ch.def.kind, true);
                if (p.filter.blacklist || !p.filter.entries.isEmpty()) {
                    chip(g, mx, my, x0 + 145, y + 1, W - 6 - x0 - 145, 11, tr(p.filter.blacklist ? "black_short" : "white_short"), true,
                          p.filter.blacklist ? 0xB5413A : 0x2E9E78, () -> {
                              if (edit) {
                                  p.filter = new Filter(!p.filter.blacklist, p.filter.entries);
                              }
                          });
                }
                y += 16;
            }
        }
        endPanelBody(g, y, x0, bottom);
        //Boutons : Copier, Coller, Groupe, Valider
        int by = height - STATUS - 14;
        button(g, mx, my, x0 + 6, by, 40, 12, tr("copy_point"), true, () -> {
            LogisticDiagramClient.copyPoint(snap.pointCopy(key));
            flash(tr("point_copied"));
        });
        tooltip(mx, my, x0 + 6, by, 40, 12, tr("copy_point_tip"));
        CompoundTag clip = LogisticDiagramClient.pointClipboard();
        button(g, mx, my, x0 + 48, by, 40, 12, tr("paste_point"), edit && clip != null, () -> pastePoint(clip));
        tooltip(mx, my, x0 + 48, by, 40, 12, tr("paste_point_tip"));
        if (ptBox.group()) {
            chip(g, mx, my, x0 + 90, by, 40, 12, applyGroup ? tr("group_all", ptBox.members.length) : tr("group_one"), applyGroup, 0x2E9E78,
                  () -> applyGroup = !applyGroup);
            tooltip(mx, my, x0 + 90, by, 40, 12, tr("group_tip"));
        }
        primary(g, mx, my, W - 42, by, 36, 12, tr("validate"), edit, () -> {
            cancelText();
            sendPoint(ch.uid(), p);
        });
    }

    private long[] targets() {
        return applyGroup ? ptBox.members.clone() : new long[]{ptBox.members[0]};
    }

    private void sendPoint(int uid, PointConfig cfg) {
        CompoundTag op = new CompoundTag();
        op.putString("op", "pt");
        op.putInt("u", uid);
        op.put("c", cfg.save());
        op.putLongArray("k", targets());
        send(op);
    }

    private void pastePoint(@Nullable CompoundTag clip) {
        if (clip == null) {
            return;
        }
        int done = 0;
        for (DiagramSnapshot.Channel c : snap.channels) {
            PointConfig cfg = DiagramSnapshot.pastedFor(clip, c);
            if (cfg != null) {
                sendPoint(c.uid(), cfg);
                done++;
                if (snap.auto) {
                    //Le premier envoi materialise le mode auto : les uid suivants changent, on s'arrete la
                    break;
                }
            }
        }
        flash(done > 0 ? tr("point_pasted", done) : tr("point_paste_none"));
    }

    /**
     * Patron du cube des faces de la machine (faces relatives a sa facade) : couleur = ce que la face offre pour le type du canal (vert
     * insertion, rouge extraction seule, violet les deux, gris fermee), fiche du cable sur la face touchee, face choisie cerclee d'or.
     */
    private int cube(GuiGraphics g, int mx, int my, int x0, int y, DiagramSnapshot.Channel ch, PointConfig p, @Nullable DiagramSnapshot.Machine m,
          boolean edit) {
        int k = 17, W = width;
        int faces = m == null ? 0 : m.faces[ch.def.kind.ordinal()];
        int touched = m == null ? RelativeSide.BOTTOM.ordinal()
              : RelativeSide.fromDirections(Direction.from3DDataValue(m.facing), Direction.from3DDataValue(m.touchedFace())).ordinal();
        int chosen = p.face == PointConfig.TOUCHED ? touched : p.face;
        for (int r = 0; r < 6; r++) {
            int fx = x0 + 6 + NET[r][0] * k, fy = y + NET[r][1] * k;
            int bits = faces >> (r * 2) & 3;
            int base = bits == 1 ? 0xFF1F3A2A : bits == 2 ? 0xFF3A1F22 : bits == 3 ? 0xFF2E2140 : 0xFF1A2027;
            boolean hover = over(mx, my, fx, fy, k - 1, k - 1);
            g.fill(fx, fy, fx + k - 1, fy + k - 1, hover ? GuiDraw.darken(base, 1.4F) | 0xFF000000 : base);
            g.renderOutline(fx, fy, k - 1, k - 1, bits == 1 ? 0xFF3E9E5A : bits == 2 ? 0xFFB5413A : bits == 3 ? 0xFF8A4FD0 : 0xFF3C4A57);
            if (r == chosen) {
                g.renderOutline(fx - 1, fy - 1, k + 1, k + 1, GOLD);
                g.renderOutline(fx, fy, k - 1, k - 1, GOLD);
            }
            if (r == touched) {
                g.fill(fx + 5, fy + 4, fx + 11, fy + 10, 0xFF55606F);
                g.fill(fx + 7, fy + 6, fx + 9, fy + 8, 0xFF1F242C);
            }
            GuiDraw.center(g, font, tr("face_short." + RelativeSide.byIndex(r).name().toLowerCase(Locale.ROOT)), fx + k / 2F, fy + k - 7, 0.55F,
                  bits != 0 ? D_TEXT : D_FAINT, k - 1);
            final int face = r;
            if (edit) {
                click(fx, fy, k - 1, k - 1, () -> p.face = face == touched && p.face == PointConfig.TOUCHED ? PointConfig.TOUCHED : face);
            }
            if (hover) {
                tooltip(List.of(faceName(r), tr("offers." + bits).withStyle(bits == 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY),
                      tr(r == touched ? "face_is_touched" : "face_click").withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
        int tx = x0 + 6 + 4 * k + 4, tw = W - tx - 5;
        Component verb = p.role.out() && !p.role.in() ? tr("inject_by", faceName(chosen)) : p.role.in() && !p.role.out() ? tr("extract_by", faceName(chosen))
              : tr("access_by", faceName(chosen));
        GuiDraw.text(g, font, verb, tx, y + 1, 0.7F, GOLD, tw);
        Component why;
        int bits = faces >> (chosen * 2) & 3;
        if (bits == 0) {
            why = tr("face_closed");
        } else if (p.face == PointConfig.TOUCHED) {
            why = tr("face_touched_note");
        } else {
            why = tr("face_other_note", faceName(touched), faceName(chosen));
        }
        wrapped(g, why, tx, y + 10, 0.6F, bits == 0 ? WARN : D_DIM, tw, 4);
        chip(g, mx, my, tx, y + 40, 52, 11, tr("face_touched"), p.face == PointConfig.TOUCHED, 0x2E9E78, () -> {
            if (edit) {
                p.face = PointConfig.TOUCHED;
            }
        });
        tooltip(mx, my, tx, y + 40, 52, 11, tr("face_touched_tip"));
        return y + 3 * k + 4;
    }

    // ---------------------------------------------------------------- copier / coller le reseau

    private void copyNetwork() {
        boolean saved = LogisticDiagramClient.copyNetwork(snap.template());
        flash(saved ? tr("network_copied") : tr("network_copied_memory"));
    }

    private void pasteNetwork() {
        CompoundTag t = LogisticDiagramClient.networkClipboard();
        if (t == null || t.getList("c", 10).isEmpty()) {
            flash(tr("network_paste_none"));
            return;
        }
        Set<String> here = new LinkedHashSet<>();
        for (DiagramSnapshot.Machine m : snap.machines) {
            here.add(m.item);
        }
        int matching = 0;
        for (String type : DiagramSnapshot.templateTypes(t)) {
            matching += here.contains(type) ? 1 : 0;
        }
        ask(tr("confirm_paste", t.getList("c", 10).size(), matching), () -> {
            CompoundTag op = new CompoundTag();
            op.putString("op", "paste");
            op.put("t", t);
            send(op);
        });
    }

    // ---------------------------------------------------------------- fenetres

    private void ask(Component text, Runnable action) {
        confirmText = text;
        confirmAction = action;
        popup = Popup.CONFIRM;
    }

    private void confirm(GuiGraphics g, int mx, int my) {
        int w = 200, h = 64, x = (width - w) / 2, y = (height - h) / 2;
        click(0, 0, width, height, () -> popup = Popup.NONE);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(0, 0, width, height, 0x80000000);
        g.fill(x, y, x + w, y + h, D_PANEL);
        g.renderOutline(x, y, w, h, WARN);
        wrapped(g, confirmText, x + 8, y + 8, 0.75F, D_TEXT, w - 16, 4);
        button(g, mx, my, x + 8, y + h - 18, 60, 12, tr("cancel"), true, () -> popup = Popup.NONE);
        primary(g, mx, my, x + w - 68, y + h - 18, 60, 12, tr("confirm"), true, () -> {
            popup = Popup.NONE;
            if (confirmAction != null) {
                confirmAction.run();
            }
        });
        g.pose().popPose();
    }

    private void openPicker(boolean forPoint) {
        pickerForPoint = forPoint;
        popup = Popup.PICKER;
        pickerPage = 0;
        pickerFocus = null;
        searchBox.setValue("");
        searchBox.setVisible(true);
        searchBox.setFocused(true);
        setFocused(searchBox);
    }

    private void closePicker() {
        popup = Popup.NONE;
        dragKey = null;
        searchBox.setVisible(false);
        searchBox.setFocused(false);
        setFocused(null);
    }

    private ChannelKind pickerKind() {
        return pickerForPoint ? snap.channel(selUid) == null ? ChannelKind.ITEM : snap.channel(selUid).def.kind : chDraft == null ? ChannelKind.ITEM
              : chDraft.kind;
    }

    @Nullable
    private Filter pickerFilter() {
        return pickerForPoint ? ptDraft == null ? null : ptDraft.filter : chDraft == null ? null : chDraft.filter;
    }

    /**
     * 1.12.1 (F1, demande de Guillaume : « une mini frame JEI dans la config pour voir ce qu'on recherche ») : fenetre d'ajout au filtre facon
     * JEI. Entrees actuelles en haut (clic = retirer, depot d'un objet glisse = ajouter), recherche (nom, {@code @mod}, {@code #tag}), grille
     * des ingredients du type du canal ({@link IngredientIndex}, sans JEI) qui defile par pages (fleches ou molette), tags proposes (de la
     * recherche ou de l'ingredient survole), inventaire du joueur (clic ou glisser vers les entrees), saisie libre d'une cle.
     */
    private void picker(GuiGraphics g, int mx, int my) {
        ChannelKind kind = pickerKind();
        Filter filter = pickerFilter();
        if (filter == null) {
            closePicker();
            return;
        }
        int max = pickerForPoint ? Filter.POINT_SIZE : Filter.CHANNEL_SIZE;
        int w = Math.min(250, width - 12), h = Math.min(214, height - 8), x = (width - w) / 2, y = (height - h) / 2;
        click(0, 0, width, height, this::closePicker);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(0, 0, width, height, 0x80000000);
        g.fill(x, y, x + w, y + h, D_PANEL);
        g.renderOutline(x, y, w, h, ACCENT);
        click(x, y, w, h, () -> {
        });
        g.fill(x + 1, y + 1, x + w - 1, y + 13, D_BAR);
        GuiDraw.text(g, font, tr("picker_title", kindName(kind)), x + 6, y + 4, 0.8F, D_TEXT, w - 30);
        button(g, mx, my, x + w - 14, y + 2, 11, 10, Component.literal("x"), true, this::closePicker);
        //Entrees actuelles (zone de depot)
        int ey = y + 16;
        GuiDraw.text(g, font, tr("picker_current", filter.entries.size(), max), x + 6, ey, 0.65F, D_DIM, 70);
        int ex = x + 6;
        dropX0 = x + 4;
        dropY0 = ey - 2;
        dropX1 = x + w - 4;
        dropY1 = ey + 28;
        if (dragKey != null && mx >= dropX0 && mx < dropX1 && my >= dropY0 && my < dropY1) {
            g.renderOutline(dropX0, dropY0, dropX1 - dropX0, dropY1 - dropY0, GOLD);
        }
        for (int i = 0; i < max; i++) {
            int sx = ex + i * 19, sy = ey + 8;
            boolean hover = over(mx, my, sx, sy, 18, 18);
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF0E1419);
            g.renderOutline(sx, sy, 18, 18, hover && i < filter.entries.size() ? IN : 0xFF2F3B46);
            if (i < filter.entries.size()) {
                Filter.Entry e = filter.entries.get(i);
                entryIcon(g, e, kind, sx, sy, 18);
                final int index = i;
                click(sx, sy, 18, 18, () -> removeEntry(pickerForPoint, index));
                if (hover) {
                    IngredientIndex.Entry known = IngredientIndex.find(kind, e.key());
                    tooltip(List.of(known != null ? known.name() : Component.literal(e.key()), Component.literal(e.key()).withStyle(ChatFormatting.DARK_GRAY),
                          tr("filter_remove").withStyle(ChatFormatting.RED)));
                }
            }
        }
        if (filter.entries.isEmpty()) {
            GuiDraw.text(g, font, tr(filter.blacklist ? "filter_empty_black" : "filter_empty"), ex + max * 19 + 4, ey + 13, 0.6F, D_FAINT,
                  x + w - (ex + max * 19 + 8));
        }
        //Recherche
        int qy = ey + 30;
        boolean full = filter.entries.size() >= max;
        searchBox.setX(x + 6);
        searchBox.setY(qy);
        searchBox.setWidth(w - 76);
        searchBox.setVisible(true);
        String query = searchBox.getValue().trim();
        boolean raw = rawKey(query);
        button(g, mx, my, x + w - 68, qy, 62, 12, raw ? tr("picker_add_raw", query) : tr("picker_type"), raw && !full, () -> addEntry(query));
        tooltip(mx, my, x + w - 68, qy, 62, 12, tr("picker_search_tip"));
        //Grille
        int gy = qy + 15, cols = Math.max(4, (w - 12) / 18), rows = Math.max(2, Math.min(5, (h - 120) / 18));
        List<IngredientIndex.Entry> found = IngredientIndex.supports(kind) ? IngredientIndex.search(kind, query) : List.of();
        int perPage = cols * rows, pages = Math.max(1, (found.size() + perPage - 1) / perPage);
        pickerPage = Mth.clamp(pickerPage, 0, pages - 1);
        int gx = x + (w - cols * 18) / 2;
        g.fill(gx - 1, gy - 1, gx + cols * 18 + 1, gy + rows * 18 + 1, 0xFF0A0F13);
        IngredientIndex.Entry hovered = null;
        for (int i = 0; i < perPage; i++) {
            int k = pickerPage * perPage + i;
            if (k >= found.size()) {
                break;
            }
            IngredientIndex.Entry e = found.get(k);
            int sx = gx + (i % cols) * 18, sy = gy + (i / cols) * 18;
            boolean hover = over(mx, my, sx, sy, 18, 18);
            boolean chosen = has(filter, e.key());
            if (hover || chosen) {
                g.fill(sx, sy, sx + 18, sy + 18, chosen ? 0xFF1F3B30 : 0xFF223040);
            }
            drawIngredient(g, e, sx + 1, sy + 1);
            if (!chosen && !full) {
                click(sx, sy, 18, 18, () -> addEntry(e.key()));
            }
            if (hover) {
                hovered = e;
                tooltip(List.of(e.name(), Component.literal(e.key()).withStyle(ChatFormatting.DARK_GRAY),
                      (chosen ? tr("picker_chosen") : full ? tr("picker_full") : tr("picker_click_add")).withStyle(ChatFormatting.GRAY)));
            }
        }
        if (hovered != null) {
            pickerFocus = hovered;
        }
        if (found.isEmpty()) {
            GuiDraw.center(g, font, tr(IngredientIndex.supports(kind) ? "picker_none" : "picker_no_list"), gx + cols * 9, gy + rows * 9 - 3, 0.7F,
                  D_FAINT, cols * 18 - 8);
        }
        //Pages
        int py = gy + rows * 18 + 3;
        button(g, mx, my, gx, py, 12, 11, Component.literal("<"), pickerPage > 0, () -> pickerPage--);
        GuiDraw.center(g, font, tr("picker_page", pickerPage + 1, pages, found.size()), gx + cols * 9, py + 2, 0.65F, D_DIM, cols * 18 - 30);
        button(g, mx, my, gx + cols * 18 - 12, py, 12, 11, Component.literal(">"), pickerPage < pages - 1, () -> pickerPage++);
        //Tags proposes
        int ty = py + 14;
        GuiDraw.text(g, font, tr("picker_tags"), x + 6, ty + 2, 0.65F, D_DIM, 30);
        int tx = x + 34;
        List<String> tags = IngredientIndex.tags(kind, query, pickerFocus, 6);
        for (String tag : tags) {
            String label = "#" + tag;
            int tw = (int) Math.min(110, font.width(label) * 0.65F + 6);
            if (tx + tw > x + w - 6) {
                break;
            }
            boolean chosen = has(filter, label);
            chip(g, mx, my, tx, ty, tw, 10, Component.literal(label), chosen, 0x2E9E78, () -> {
                if (!chosen && !full) {
                    addEntry(label);
                }
            });
            tooltip(mx, my, tx, ty, tw, 10, Component.literal(label), tr("picker_tag_tip").withStyle(ChatFormatting.GRAY));
            tx += tw + 2;
        }
        if (tags.isEmpty()) {
            GuiDraw.text(g, font, tr("picker_tags_hint"), tx, ty + 2, 0.6F, D_FAINT, x + w - tx - 6);
        }
        //Inventaire (clic ou glisser vers les entrees)
        int iy = ty + 14;
        GuiDraw.text(g, font, tr("picker_inventory"), x + 6, iy + 5, 0.65F, D_DIM, 40);
        List<String> keys = new ArrayList<>();
        List<ItemStack> icons = new ArrayList<>();
        if (minecraft != null && minecraft.player != null) {
            var inv = minecraft.player.getInventory();
            for (int i = 0; i < 36; i++) {
                ItemStack st = inv.getItem(i);
                String key = keyOf(st, kind);
                if (key != null && !keys.contains(key)) {
                    keys.add(key);
                    icons.add(st);
                }
            }
        }
        int ix = x + 48, slots = Math.max(1, (x + w - 6 - ix) / 18);
        for (int i = 0; i < Math.min(keys.size(), slots); i++) {
            int sx = ix + i * 18;
            boolean hover = over(mx, my, sx, iy, 18, 18);
            g.fill(sx, iy, sx + 18, iy + 18, hover ? 0xFF223040 : 0xFF0E1419);
            g.renderItem(icons.get(i), sx + 1, iy + 1);
            if (hover) {
                tooltip(List.of(icons.get(i).getHoverName(), Component.literal(keys.get(i)).withStyle(ChatFormatting.DARK_GRAY),
                      tr("picker_drag_tip").withStyle(ChatFormatting.GRAY)));
            }
        }
        if (keys.isEmpty()) {
            GuiDraw.text(g, font, tr("picker_empty"), ix, iy + 5, 0.6F, D_FAINT, x + w - ix - 6);
        }
        searchBox.render(g, mx, my, 0);
        //Objet glisse
        if (dragKey != null && dragIcon != null) {
            g.pose().translate(0, 0, 100);
            g.renderItem(dragIcon, mx - 8, my - 8);
        }
        g.pose().popPose();
    }

    /** Case de l'inventaire de la mini-fenetre sous la souris : sa cle et l'objet (pour le glisser), sinon null. */
    @Nullable
    private Object[] inventoryCellAt(double mx, double my) {
        if (popup != Popup.PICKER || minecraft == null || minecraft.player == null) {
            return null;
        }
        ChannelKind kind = pickerKind();
        int w = Math.min(250, width - 12), h = Math.min(214, height - 8), x = (width - w) / 2, y = (height - h) / 2;
        int rows = Math.max(2, Math.min(5, (h - 120) / 18));
        int iy = y + 16 + 30 + 15 + rows * 18 + 3 + 14 + 14, ix = x + 48, slots = Math.max(1, (x + w - 6 - ix) / 18);
        if (my < iy || my >= iy + 18 || mx < ix) {
            return null;
        }
        int cell = (int) ((mx - ix) / 18);
        List<String> keys = new ArrayList<>();
        List<ItemStack> icons = new ArrayList<>();
        var inv = minecraft.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            String key = keyOf(st, kind);
            if (key != null && !keys.contains(key)) {
                keys.add(key);
                icons.add(st);
            }
        }
        return cell < Math.min(slots, keys.size()) ? new Object[]{keys.get(cell), icons.get(cell)} : null;
    }

    /** Texte de la recherche ajoutable tel quel : {@code #tag}, {@code @mod} ou {@code mod:id}, sans espace. */
    private static boolean rawKey(String query) {
        return query.length() > 1 && query.indexOf(' ') < 0 && (query.startsWith("#") || query.startsWith("@") || query.indexOf(':') > 0);
    }

    private static boolean has(Filter filter, String key) {
        for (Filter.Entry e : filter.entries) {
            if (e.key().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** Icone 16 x 16 d'un ingredient : objet, seau du fluide ou texture teintee du fluide / du gaz. */
    private static void drawIngredient(GuiGraphics g, IngredientIndex.Entry e, int x, int y) {
        if (e.fluid() != null && e.icon().isEmpty()) {
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(e.fluid().getFluid());
            sprite(g, ext.getStillTexture(e.fluid()), ext.getTintColor(e.fluid()), x, y);
        } else if (e.gas() != null) {
            sprite(g, e.gas().getIcon(), e.gas().getTint(), x, y);
        } else {
            g.renderFakeItem(e.icon(), x, y);
        }
    }

    private static void sprite(GuiGraphics g, ResourceLocation texture, int tint, int x, int y) {
        TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        g.blit(x, y, 0, 16, 16, sp, (tint >> 16 & 255) / 255F, (tint >> 8 & 255) / 255F, (tint & 255) / 255F, 1);
    }

    /** Cle de filtre d'un objet de l'inventaire pour un type de canal (null : rien a ajouter). */
    @Nullable
    private static String keyOf(ItemStack s, ChannelKind kind) {
        if (s.isEmpty()) {
            return null;
        }
        return switch (kind) {
            case ITEM -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            case FLUID -> FluidUtil.getFluidContained(s).filter(f -> !f.isEmpty())
                  .map(f -> ForgeRegistries.FLUIDS.getKey(f.getFluid()).toString()).orElse(null);
            case GAS -> {
                Gas gas = s.getItem() instanceof GasCapsuleItem ? GasCapsuleItem.getGas(s) : null;
                yield gas == null || gas.getRegistryName() == null ? null : gas.getRegistryName().toString();
            }
            default -> null;
        };
    }

    private void helpOverlay(GuiGraphics g, int mx, int my) {
        int w = Math.min(260, width - 40), x = (width - w) / 2, y = BAR + 10;
        int h = Math.min(height - STATUS - y - 6, 150);
        click(0, 0, width, height, () -> help = false);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(x, y, x + w, y + h, 0xF0101820);
        g.renderOutline(x, y, w, h, ACCENT);
        GuiDraw.text(g, font, tr("help_title"), x + 6, y + 5, 0.85F, D_TEXT, w - 12);
        int yy = y + 17;
        for (int i = 1; i <= 9 && yy < y + h - 18; i++) {
            yy += wrapped(g, tr("help." + i), x + 6, yy, 0.62F, D_DIM, w - 12, 3) + 2;
        }
        GuiDraw.text(g, font, tr("cost", decimal(snap.costMicros)), x + 6, y + h - 10, 0.62F, D_FAINT, w - 12);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------
    // Saisie de texte (meme mecanique que TrainScreen)
    // ------------------------------------------------------------------

    private void startText(int x, int y, int w, String value, int max, Consumer<String> done) {
        finishText();
        textBox.setX(x);
        textBox.setY(y);
        textBox.setWidth(Math.max(40, w));
        textBox.setMaxLength(max);
        textBox.setValue(value);
        textBox.setVisible(true);
        textBox.setFocused(true);
        setFocused(textBox);
        textDone = done;
    }

    private void finishText() {
        if (textDone != null) {
            Consumer<String> d = textDone;
            textDone = null;
            d.accept(textBox.getValue().trim());
        }
        textBox.setVisible(false);
        textBox.setFocused(false);
    }

    private void cancelText() {
        textDone = null;
        if (textBox != null) {
            textBox.setVisible(false);
            textBox.setFocused(false);
        }
    }

    // ------------------------------------------------------------------
    // Souris et clavier
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (textDone != null && !(mx >= textBox.getX() && mx < textBox.getX() + textBox.getWidth() && my >= textBox.getY() && my < textBox.getY() + 12)) {
            finishText();
        }
        if (button == 0 && popup == Popup.PICKER) {
            //1.12.1 (F1) : un objet de l'inventaire de la mini-fenetre se prend (clic = ajout, glisser vers les entrees = ajout)
            Object[] cell = inventoryCellAt(mx, my);
            if (cell != null) {
                dragKey = (String) cell[0];
                dragIcon = (ItemStack) cell[1];
                pressX = mx;
                pressY = my;
                return true;
            }
            searchBox.setFocused(true);
            setFocused(searchBox);
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (popup != Popup.NONE || help || button != 0 || !inCanvas(mx, my)) {
            return false;
        }
        finishAnimation();
        pressX = mx;
        pressY = my;
        dragged = false;
        dragBox = boxAt(mx, my);
        panning = dragBox == null;
        if (dragBox != null) {
            dragOffX = sx(dragBox.x) - mx;
            dragOffY = sy(dragBox.y) - my;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0 || !panning && dragBox == null) {
            return super.mouseDragged(mx, my, button, dx, dy);
        }
        if (Math.abs(mx - pressX) + Math.abs(my - pressY) > 3) {
            dragged = true;
        }
        if (!dragged) {
            return true;
        }
        if (panning) {
            offX += dx;
            offY += dy;
        } else if (snap.canEdit) {
            dragBox.x = (mx + dragOffX - COL - 1 - offX) / zoom;
            dragBox.y = (my + dragOffY - BAR - 1 - offY) / zoom;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && dragKey != null) {
            String key = dragKey;
            boolean click = Math.abs(mx - pressX) + Math.abs(my - pressY) <= 3;
            boolean dropped = mx >= dropX0 && mx < dropX1 && my >= dropY0 && my < dropY1;
            dragKey = null;
            dragIcon = null;
            if (click || dropped) {
                addEntry(key);
            }
            return true;
        }
        if (button == 0 && (panning || dragBox != null)) {
            DiagramLayout.Box box = dragBox;
            boolean wasDrag = dragged;
            dragBox = null;
            panning = false;
            if (box != null && wasDrag && snap.canEdit) {
                saveLayout(box);
            } else if (box != null && !wasDrag) {
                clickBox(box);
            } else if (box == null && !wasDrag && panel == Panel.POINT) {
                closePanel();
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    /**
     * Noeud deplace : sa place est envoyee ; la premiere fois (aucune place gardee), toutes les places sont envoyees pour que le reste du schema
     * ne bouge plus (disposition partagee, spec §5.2).
     */
    private void saveLayout(DiagramLayout.Box moved) {
        moved.x = Math.round(moved.x);
        moved.y = Math.round(moved.y);
        List<DiagramLayout.Box> list = new ArrayList<>();
        if (snap.layout.isEmpty()) {
            list.addAll(layout.boxes);
        } else {
            list.add(moved);
        }
        for (int from = 0; from < list.size(); from += 256) {
            int to = Math.min(list.size(), from + 256);
            long[] ids = new long[to - from];
            int[] xs = new int[ids.length], ys = new int[ids.length];
            for (int i = from; i < to; i++) {
                //1.0.5-beta (lot 45) : place relative au site du noeud (un seul site : la meme qu'avant)
                ids[i - from] = list.get(i).id;
                xs[i - from] = (int) DiagramLayout.localX(list.get(i));
                ys[i - from] = (int) DiagramLayout.localY(list.get(i));
            }
            CompoundTag op = new CompoundTag();
            op.putString("op", "node");
            op.putLongArray("ia", ids);
            op.putIntArray("xa", xs);
            op.putIntArray("ya", ys);
            send(op);
        }
    }

    private void clickBox(DiagramLayout.Box box) {
        if (!box.machine) {
            return;
        }
        if (hasShiftDown()) {
            //Deplier / regrouper
            boolean any = false;
            for (long k : box.members) {
                any |= unfolded.remove(k);
            }
            if (!any) {
                DiagramLayout.Box mine = box;
                //Un noeud seul qui faisait partie d'un groupe deplie : regroupe ; un groupe : deplie
                if (mine.group()) {
                    for (long k : mine.members) {
                        unfolded.add(k);
                    }
                }
            }
            //1.12.1 (F1) : deplier / regrouper reorganise tout de suite le schema (spatial, puis clair)
            arranged = true;
            rebuild(true);
            return;
        }
        if (snap.channel(selUid) == null) {
            flash(tr("select_channel_first"));
            return;
        }
        openPoint(box);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (textDone != null) {
            finishText();
        }
        if (popup == Popup.PICKER) {
            pickerPage -= (int) Math.signum(delta);
            return true;
        }
        if (popup != Popup.NONE || help) {
            return true;
        }
        if (mx < COL && my > BAR && my < listBottom()) {
            chScroll -= (int) (delta * ROW);
            return true;
        }
        if (mx >= canvasRight() && panel != Panel.NONE) {
            panelScroll -= (int) (delta * 14);
            return true;
        }
        if (inCanvas(mx, my)) {
            double old = zoom;
            zoom = Mth.clamp(zoom * (delta > 0 ? 1.15 : 1 / 1.15), 0.4, 2.0);
            //Zoom autour du curseur
            double cx = mx - COL - 1, cy = my - BAR - 1;
            offX = cx - (cx - offX) * zoom / old;
            offY = cy - (cy - offY) * zoom / old;
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (textDone != null) {
            if (key == 257 || key == 335) {
                finishText();
            } else if (key == 256) {
                cancelText();
            } else {
                textBox.keyPressed(key, scan, mods);
            }
            return true;
        }
        if (popup == Popup.PICKER) {
            //1.12.1 (F1) : recherche de la mini-fenetre ; Entree = le seul resultat, ou la saisie (#tag, @mod, mod:id)
            if (key == 256) {
                closePicker();
            } else if (key == 257 || key == 335) {
                String q = searchBox.getValue().trim();
                List<IngredientIndex.Entry> found = IngredientIndex.supports(pickerKind()) ? IngredientIndex.search(pickerKind(), q) : List.of();
                if (found.size() == 1) {
                    addEntry(found.get(0).key());
                } else if (rawKey(q)) {
                    addEntry(q);
                }
            } else {
                searchBox.keyPressed(key, scan, mods);
            }
            return true;
        }
        if (key == 256) {
            if (popup != Popup.NONE) {
                popup = Popup.NONE;
                return true;
            }
            if (help) {
                help = false;
                return true;
            }
            if (panel != Panel.NONE) {
                closePanel();
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (textDone != null) {
            return textBox.charTyped(c, mods);
        }
        if (popup == Popup.PICKER) {
            return searchBox.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
    }
}
