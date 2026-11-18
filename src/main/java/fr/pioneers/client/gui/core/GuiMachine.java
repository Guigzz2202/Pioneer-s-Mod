package fr.pioneers.client.gui.core;

import com.mojang.blaze3d.platform.InputConstants;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.ToolBay;
import fr.pioneers.client.PiKeys;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.Texts;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.core.machine.MachineMenu;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.machine.RedstoneMode;
import fr.pioneers.core.menu.MenuSlot;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.recipe.Remanents;
import fr.pioneers.core.network.PacketRecipeLock;
import fr.pioneers.core.network.PacketTileAction;
import fr.pioneers.core.network.PiNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface standard des blocs du socle, au graphisme de reference (textures {@code textures/gui/machine/*_<tier>}, generateur
 * {@code tools/gc/core/gui_machine.py}) :
 * <ul>
 *     <li>panneau d'acier aux cerclages du tier (zone machine de hauteur variable, inventaire du joueur dessous) ;</li>
 *     <li>buffers : jauge, ecran d'etat (en-tete nom + pourcentage, barre, lignes libelle / valeur, debit), cases d'entree / sortie
 *     (objet fantome dans la case d'entree vide) ; buffer d'objets : ses cases ;</li>
 *     <li>radiateur et compresseur : reserve d'energie, thermometre / manometre (cible marquee), ecran, reglages sous l'ecran ;</li>
 *     <li>onglets accroches a gauche du panneau (comme Mekanism) : configuration des faces et ejection automatique.</li>
 * </ul>
 * Sans textures, tout retombe sur le dessin vectoriel de {@link PiMenuScreen}.
 * <p>
 * API Machine (lot 2) : la vue de chaque machine vient du registre {@link MachineScreens} (nom de vue de sa fiche) au lieu des
 * {@code instanceof} ; les valeurs se lisent par leur nom ({@link UiValues}). Interface standard v2 ({@link StandardView}, maquettes
 * validees) : inventaire DANS le cadre, onglets a DROITE (Recettes, faces, redstone, Infos), ecran d'etat de 48 px.
 * <p>
 * Lot 3 (Guillaume, apres le test du lot 2) : le carre du selecteur de recette ({@link RecipeSquare}) s'ouvre et se ferme (onglet
 * « Recettes » en tete des onglets, qui montre l'icone de la recette verrouillee ; touche {@link PiKeys#RECIPE_SQUARE}, Ctrl + R par
 * defaut ; ✕ ; Echap le ferme avant l'ecran). Ouvert, il se pose PAR-DESSUS la zone machine ({@link StandardLayout#squareOver}, calque du
 * dessus : souris et clavier a lui) ; ferme, il ne prend aucune place : le panneau et ses onglets sont centres seuls. 1.0.2-beta : il est
 * toujours ferme a l'ouverture de l'interface (avant : le dernier choix du joueur pour cette machine, sinon ouvert si aucune recette n'etait
 * verrouillee). Choisir une recette le referme.
 */
public class GuiMachine extends PiMenuScreen<MachineMenu> {

    /** Onglets : 24x24, a gauche du panneau. */
    private static final int TAB_LEGACY = 24, TAB_X = -TAB_LEGACY, TAB_Y = 14;
    private static final int SEPARATOR = 0xFF24333E, BAR_BG = 0xFF05080B, FLOW_UP = 0xFF55FF55, FLOW_DOWN = 0xFFFF6A5A;
    private static final int STEEL = 0xFF262C35, STEEL_HOVER = 0xFF323A45, STEEL_LIGHT = 0xFF55606F, STEEL_DARK = 0xFF1F242C;
    static final int FLOW_UP_COLOR = FLOW_UP;
    private final MachineViews views = new MachineViews(this);
    /** API Machine (lot 2) : vue de la machine (registre) ; interface standard v2 ? */
    private final MachineScreens.View view;
    private final boolean standard;
    /** Onglet de la commande redstone (machines du socle, {@link MachineTile}). */
    private final boolean redstone;
    /** Carre du selecteur (interface standard v2) et onglet Infos. */
    private final RecipeSquare square = new RecipeSquare();
    /** Lot 3 : carre du selecteur ouvert (par-dessus la zone machine) ? 1.0.2-beta : ferme a l'ouverture de l'interface. */
    private boolean squareOpen, squareInit;
    private boolean infoOpen;
    /** Lot 5 : onglet Structure ouvert (multiblocs). */
    private boolean structureOpen;
    private boolean sidesOpen;
    /** Ressource dont on regle les faces (canal de la configuration). */
    private int sideChannel;
    /** Faces de la vue « configuration » : position du bouton (relative au panneau) et face relative. */
    private final List<int[]> faceButtons = new ArrayList<>();
    /** MAJ Recettes (lot 7) : selecteur de recette (case « Recette » des machines a verrou). */
    private final RecipePicker picker = new RecipePicker();
    private final RecipePicker.Host pickerHost = new RecipePicker.Host() {
        @Override
        public Font font() {
            return font;
        }

        @Override
        public boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
            return GuiMachine.this.over(mouseX, mouseY, x, y, w, h);
        }

        @Override
        public void click(int x, int y, int w, int h, Runnable action) {
            GuiMachine.this.click(x, y, w, h, action);
        }

        @Override
        public void tooltip(List<Component> lines) {
            GuiMachine.this.tooltip(lines);
        }

        @Override
        public void choose(@Nullable ResourceLocation id) {
            lockRecipe(id);
        }
    };
    /** Lot 3 : le carre ; choisir une recette (clic, Entree) le referme, deverrouiller non. */
    private final RecipePicker.Host squareHost = new RecipePicker.Host() {
        @Override
        public Font font() {
            return font;
        }

        @Override
        public boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
            return GuiMachine.this.over(mouseX, mouseY, x, y, w, h);
        }

        @Override
        public void click(int x, int y, int w, int h, Runnable action) {
            GuiMachine.this.click(x, y, w, h, action);
        }

        @Override
        public void tooltip(List<Component> lines) {
            GuiMachine.this.tooltip(lines);
        }

        @Override
        public void choose(@Nullable ResourceLocation id) {
            lockRecipe(id);
            if (id != null) {
                squareOpen = false;
            }
        }
    };

    public GuiMachine(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.width(), menu.height());
        titleLabelY = 7;
        inventoryLabelY = -1;
        view = MachineScreens.of(menu.getTile());
        standard = view.standard();
        redstone = StorageViews.as(MachineTile.class, menu.getTile()) != null || menu.machine() != null && menu.machine().redstoneTab();   // 1.0.6
    }

    @Override
    protected void init() {
        super.init();
        if (standard) {
            //Lot 4 (Guillaume : « centrer l'interface quand on l'ouvre ») : le PANNEAU est centre (les onglets pendent a droite) ; seul un
            //ecran trop etroit pour les onglets le decale. Le carre se pose par-dessus : ouvrir ou fermer le carre ne bouge rien.
            leftPos = Math.max(0, Math.min((width - imageWidth) / 2, width - imageWidth - StandardLayout.TAB));
            topPos = Math.max(0, (height - imageHeight) / 2);
        }
        //1.0.2-beta (Guillaume : « le filtre de recette ne doit pas s'ouvrir a l'ouverture de l'interface ; l'ouverture reste en memoire quand
        //on ferme une interface ») : le carre du selecteur est TOUJOURS ferme a l'ouverture ; il ne s'ouvre qu'a la demande (onglet Recettes,
        //case Recette, Ctrl + R). Plus de memoire ouvert / ferme par machine (RecipeMemory.squareOpen n'est plus lu)
        squareInit = true;
        views.init();
    }

    /** Vues d'avant le lot 2 (jauges communes, machines pas encore au registre). */
    MachineViews views() {
        return views;
    }

    /** Interface standard v2 avec le carre du selecteur (machine a recettes) ? */
    private boolean hasSquare() {
        return standard && lockable() != null;
    }

    /** Lot 3 : le carre est-il ouvert (et visible : ni panneau des faces ni Infos par-dessus) ? */
    boolean squareShown() {
        return squareOpen && hasSquare() && !sidesOpen && !infoOpen && !structureOpen;
    }

    /** Lot 3 : ouvre ou ferme le carre (choix du joueur, retenu pour cette machine : {@link RecipeMemory#setSquareOpen}). */
    private void setSquare(boolean open) {
        RecipeLockable rl = lockable();
        if (rl == null) {
            return;
        }
        if (open) {
            sidesOpen = false;
            infoOpen = false;
            structureOpen = false;
            if (!squareOpen) {
                square.opened();
            }
        }
        squareOpen = open;
    }

    /** Carre ouvert, relatif au panneau. */
    private StandardLayout.Rect squareRect() {
        return StandardLayout.squareOver(imageWidth);
    }

    /** Champ de saisie ou autre widget vanilla d'une vue (coordonnees ecran). */
    <T extends AbstractWidget> T widget(T widget) {
        return addRenderableWidget(widget);
    }

    int left() {
        return leftPos;
    }

    int top() {
        return topPos;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        //API Machine (lot 3) : touche du selecteur (Ctrl + R par defaut) : ouvre / ferme le carre
        if (hasSquare() && PiKeys.RECIPE_SQUARE.isActiveAndMatches(InputConstants.getKey(key, scan))) {
            setSquare(!squareShown());
            square.swallowNextChar();
            return true;
        }
        //Carre ouvert : recherche active d'office (lettres, fleches, Entree, Suppr, Tab, F) ; Echap ferme le carre, puis l'ecran
        if (squareShown()) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                setSquare(false);
                return true;
            }
            if (square.keyPressed(key, modifiers, squareHost, hoveredSlot != null)) {
                return true;
            }
        }
        //MAJ Recettes (lot 7) : le selecteur ouvert prend toutes les touches (filtre au clavier ; Echap le ferme)
        if (picker.keyPressed(key, pickerHost)) {
            return true;
        }
        //1.0.5-beta (lot 17) : comptoir d'entrepot : Entree = « Commander », Echap ferme d'abord un volet
        if (!sidesOpen && !infoOpen && tile() != null && views.keyPressed(tile(), key)) {
            return true;
        }
        //Echap vide d'abord la recherche d'un atelier, puis ferme
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && views.clearSearch()) {
            return true;
        }
        //Un champ de saisie actif garde ses touches (sinon « E » fermerait l'interface)
        if (getFocused() instanceof EditBox box && box.isFocused() && box.canConsumeInput()) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                return super.keyPressed(key, scan, modifiers);
            }
            return box.keyPressed(key, scan, modifiers) || true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (squareShown() && square.charTyped(ch, hoveredSlot != null)) {
            return true;
        }
        return picker.charTyped(ch) || super.charTyped(ch, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        picker.tick();
        square.tick();
    }

    @Override
    public void removed() {
        views.removed();
        super.removed();
    }

    /** Valeur synchronisee de la machine. */
    long value(int index) {
        return menu.value(index);
    }

    /** API Machine (lot 2) : valeur synchronisee NOMMEE. */
    long value(UiValues.Key key) {
        return menu.value(key);
    }

    Font font() {
        return font;
    }

    Minecraft minecraft() {
        return minecraft;
    }

    boolean sidesOpen() {
        return sidesOpen;
    }

    private BlockEntity tile() {
        return menu.getTile();
    }

    private String tier() {
        MachineGui machine = menu.machine();
        return machine != null ? machine.guiTier() : "basic";
    }

    String sprite(String name) {
        return GuiSprites.tiered(name, tier());
    }

    /**
     * 1.0.4-beta (lot 26) : habillage CLAIR (machines de salle blanche, {@code NbtRoles.CLEANROOM_GUI}). Lot 30 (Guillaume : « l'interface de
     * la salle blanche me semble pas en accord avec nos autres interfaces ») : memes dessins que les tiers ({@code T:core/gui_machine.py},
     * palette CLEANROOM) : plaque blanche, biseaux et onglets clairs, filets bleus ; le dedans des alcoves (cases, jauges, ecrans, cadre de
     * l'inventaire) est celui des tiers. Les textes poses sur la plaque passent au sombre ({@link #panelText}, {@link #panelDim}).
     */
    boolean light() {
        return GuiSprites.LIGHT.equals(tier());
    }

    /** Couleur d'un texte pose directement sur le panneau (titre). */
    int panelText() {
        return light() ? 0xFF1E2A33 : TEXT;
    }

    /** Couleur d'un texte secondaire pose sur le panneau (libelle de l'inventaire, note du titre). */
    int panelDim() {
        return light() ? 0xFF56626C : DIM;
    }

    // ------------------------------------------------------------------
    // 1.0.4-beta (lot 31, Guillaume : « l'interface de la salle blanche n'embarque pas de tier [...] l'interface doit egalement etre
    // blanche ; ma remarque portait notamment sur les jauges ») : habillage CLAIR jusqu'au fond (cases, jauges, ecran, fleche, inventaire,
    // onglets ; textures T:core/gui_machine.py cleanroom). Ce que le Java dessine en dur passe par ces couleurs : encre sombre lisible
    // (contraste >= 4,5:1 sur le verre blanc : tools/check_p104_31.py), fonds clairs, graduations grises, accent gris argent.
    // ------------------------------------------------------------------

    /** Encres de l'habillage clair : texte, texte secondaire, texte d'ecran (vert sombre), fond de reference (verre blanc de l'ecran). */
    static final int INK = 0xFF1E2A33, INK_DIM = 0xFF4A5662, INK_SCREEN = 0xFF0B6146, PAPER = 0xFFEEF1F4;
    /** Habillage clair : filet de l'ecran, fond de barre, cadre de l'inventaire, rail de la fleche, graduations, bord, survol des cases. */
    static final int LIGHT_SEPARATOR = 0xFFC3CAD1, LIGHT_BAR_BG = 0xFFD5DBE1, LIGHT_FRAME = 0xFFDCE1E6, LIGHT_FRAME_DARK = 0xFF8C96A0,
          LIGHT_FRAME_HI = 0xFFF7F9FA, LIGHT_TRACK = 0xFFC3CAD1, LIGHT_TICK = 0xB0707C88, LIGHT_SLOT_HOVER = 0x60405060;
    /** Habillage de l'interface dessinee a cette image (vues statiques : graduations, fleches ; une seule interface a la fois). */
    private static boolean lightStatic;

    /** L'interface dessinee en ce moment est-elle claire (salle blanche) ? */
    static boolean lightSkin() {
        return lightStatic;
    }

    /** Couleur lisible sur l'habillage courant : inchangee sur les tiers ; claire : texte d'un ecran sombre -> encre sombre lisible. */
    int ink(int color) {
        return light() ? inkOf(color) : color;
    }

    /**
     * Encre d'une couleur de texte pensee pour un ecran sombre, lisible sur le verre blanc : TEXT, DIM, SCREEN_TEXT ont leur encre ; toute
     * autre couleur (jaune de la consommation, vert « formee », rouge d'erreur...) garde sa teinte, assombrie jusqu'a un contraste de 4,5:1.
     */
    static int inkOf(int color) {
        int c = color | 0xFF000000;
        if (c == TEXT || c == 0xFFFFFFFF) {
            return INK;
        }
        if (c == DIM || c == 0xFF59666A) {
            return INK_DIM;
        }
        if (c == SCREEN_TEXT) {
            return INK_SCREEN;
        }
        for (int i = 0; i < 24 && contrast(c, PAPER) < 4.6; i++) {
            c = 0xFF000000 | GuiDraw.darken(c, 0.88F);
        }
        return c;
    }

    /** Contraste WCAG de deux couleurs opaques (1..21). */
    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int c) {
        double r = channel(c >> 16 & 255), g = channel(c >> 8 & 255), b = channel(c & 255);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** Accent d'un remplissage (fleche, barre, pastille) : celui de la machine ; salle blanche : gris argent fonce (aucune couleur de tier). */
    int skinAccent(int machineAccent) {
        return light() ? GuiSprites.CLEANROOM_INK : machineAccent;
    }

    /** Lot 31 : survol des cases visible sur les cases claires (vanilla : voile blanc). */
    @Override
    public int getSlotColor(int index) {
        return light() ? LIGHT_SLOT_HOVER : super.getSlotColor(index);
    }

    void send(int action) {
        if (tile() != null) {
            PiNetwork.CHANNEL.sendToServer(new PacketTileAction(tile().getBlockPos(), action));
        }
    }

    // ------------------------------------------------------------------
    // 1.0.2-beta : CURSEURS de l'interface standard (boite de vitesses, regulateur a boules)
    // ------------------------------------------------------------------

    /** Curseur dessine a la derniere image : reglage, rail (relatif au panneau), bornes. */
    private record Slider(int setting, int x, int y, int w, int h, int min, int max) {

        int at(double panelX) {
            double f = Math.max(0, Math.min(1, (panelX - x) / Math.max(1, w)));
            return min + (int) Math.round(f * (max - min));
        }
    }

    private final List<Slider> sliders = new ArrayList<>();
    /** Valeur synchronisee de chaque curseur au dernier dessin (molette). */
    private final java.util.Map<Integer, Integer> sliderValues = new java.util.HashMap<>();

    private int sliderValue(int setting) {
        return sliderValues.getOrDefault(setting, 0);
    }
    /** Curseur saisi (reglage, valeur suivie par la souris), ou -1. */
    private int dragSetting = -1, dragValue;
    /** Derniere valeur envoyee : montree jusqu'a ce que le serveur la renvoie (1,5 s au plus). */
    private int sentSetting = -1, sentValue;
    private long sentAt;

    /** Envoie un reglage a valeur au serveur (PacketMachineSetting, garde PacketGuard). */
    void sendSetting(int setting, int value) {
        if (tile() != null) {
            PiNetwork.CHANNEL.sendToServer(new fr.pioneers.core.network.PacketMachineSetting(tile().getBlockPos(), setting, value));
            sentSetting = setting;
            sentValue = value;
            sentAt = net.minecraft.Util.getMillis();
        }
    }

    /**
     * Curseur dans la rangee {@code row} (relative au panneau, {@link StandardLayout#sliderRow}) : rail rempli a la couleur {@code accent}
     * jusqu'au bouton, graduations {@code marks} (valeurs) et leurs libelles {@code labels} dessous ; glisser (valeur envoyee au lacher),
     * clic sur le rail, molette (un cran). Renvoie la valeur MONTREE (en cours de glisse, ou envoyee pas encore revenue).
     */
    int slider(GuiGraphics g, int mouseX, int mouseY, StandardLayout.Rect row, int setting, int min, int max, int value, int[] marks,
          List<Component> labels, int accent, List<Component> tip) {
        StandardLayout.Rect t = StandardLayout.sliderTrack(row);
        Slider sl = new Slider(setting, t.x(), row.y(), t.w(), 16, min, max);
        sliders.add(sl);
        sliderValues.put(setting, value);
        boolean pending = sentSetting == setting && value != sentValue && net.minecraft.Util.getMillis() - sentAt < 1500;
        int shown = dragSetting == setting ? dragValue : pending ? sentValue : value;
        shown = Math.max(min, Math.min(max, shown));
        int kx = t.x() + (int) Math.round((shown - min) / (double) Math.max(1, max - min) * t.w());
        //Rail : creux sombre, part reglee a l'accent
        g.fill(t.x() - 1, t.y() - 1, t.x() + t.w() + 1, t.y() + t.h() + 1, 0xFF0A0E12);
        g.fill(t.x(), t.y(), t.x() + t.w(), t.y() + t.h(), 0xFF1C232B);
        g.fill(t.x(), t.y(), kx, t.y() + t.h(), 0xFF000000 | GuiDraw.darken(accent, 0.25F));
        g.fill(t.x(), t.y(), kx, t.y() + 1, 0xFF000000 | accent);
        //Graduations et libelles
        for (int i = 0; i < marks.length; i++) {
            int mx = t.x() + (int) Math.round((marks[i] - min) / (double) Math.max(1, max - min) * t.w());
            boolean on = marks[i] == shown;
            g.fill(mx, t.y() + t.h() + 1, mx + 1, t.y() + t.h() + 4, on ? 0xFF000000 | accent : 0xFF55606F);
            if (i < labels.size()) {
                GuiDraw.center(g, font, labels.get(i), mx + 0.5F, row.y() + 15, 0.6F, on ? 0xFFFFFFFF : PiMenuScreen.DIM, 30);
            }
        }
        //Bouton d'acier
        boolean hover = dragSetting == setting || over(mouseX, mouseY, kx - 4, t.y() - 5, 8, 14);
        int bx = kx - 3, by = t.y() - 4;
        g.fill(bx - 1, by - 1, bx + 7, by + 13, 0xFF14181E);
        g.fill(bx, by, bx + 6, by + 12, hover ? STEEL_HOVER : STEEL);
        g.fill(bx, by, bx + 6, by + 1, STEEL_LIGHT);
        g.fill(bx, by, bx + 1, by + 12, STEEL_LIGHT);
        g.fill(bx + 2, by + 4, bx + 4, by + 8, 0xFF000000 | accent);
        if (tip != null && over(mouseX, mouseY, row.x(), row.y(), row.w(), row.h())) {
            tooltip(tip);
        }
        return shown;
    }

    /** Curseur sous la souris (coordonnees de l'ecran), si aucun panneau ne le couvre. */
    @Nullable
    private Slider sliderAt(double mouseX, double mouseY) {
        if (squareShown() || sidesOpen || infoOpen || structureOpen) {
            return null;
        }
        double x = mouseX - leftPos, y = mouseY - topPos;
        for (Slider sl : sliders) {
            if (x >= sl.x() - 4 && x < sl.x() + sl.w() + 4 && y >= sl.y() && y < sl.y() + sl.h()) {
                return sl;
            }
        }
        return null;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragSetting >= 0) {
            for (Slider sl : sliders) {
                if (sl.setting() == dragSetting) {
                    dragValue = sl.at(mouseX - leftPos);
                }
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragSetting >= 0 && button == 0) {
            sendSetting(dragSetting, dragValue);
            dragSetting = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------
    // Panneau et cases
    // ------------------------------------------------------------------

    @Override
    protected void drawPanel(GuiGraphics graphics, int x, int y) {
        int zone = imageHeight - StandardLayout.INVENTORY;
        //Panneau dessine pour une zone machine de 86 : bande du titre (24) et inventaire (102) gardes, le reste etire. Interface v2 (lot 6
        //de la 1.0.3, Guillaume : « la partie inventaire est superposee avec le design de l'interface ») : habillage en bandes, le bas de
        //l'alcove et la glissiere du tier AU-DESSUS du cadre de l'inventaire (StandardLayout.panelBands), plus rien dessous
        boolean drawn = standard
                        ? GuiSprites.bands(graphics, sprite("panel"), x, y, imageWidth, StandardLayout.PANEL_TEX, StandardLayout.PANEL_TEX, 8, 8,
                              StandardLayout.panelBands(zone, imageHeight))
                        : GuiSprites.grid(graphics, sprite("panel"), x, y, imageWidth, imageHeight, 176, 176, 8, 8, 24, 102);
        if (!drawn) {
            super.drawPanel(graphics, x, y);
        }
        if (standard) {
            //Interface standard v2 : inventaire DANS un cadre interieur (alcove sombre), rien ne depasse du panneau
            StandardLayout.Rect f = StandardLayout.inventoryFrame(imageWidth, zone);
            int fx0 = x + f.x(), fy0 = y + f.y(), fx1 = fx0 + f.w(), fy1 = fy0 + f.h();
            //1.0.4-beta (lot 31) : salle blanche : cadre clair (creux gris tres clair, arete gris moyen en haut, biseau blanc)
            boolean light = light();
            graphics.fill(fx0, fy0, fx1, fy1, light ? LIGHT_FRAME : 0xFF0E141A);
            graphics.fill(fx0, fy0, fx1, fy0 + 1, light ? LIGHT_FRAME_DARK : 0xFF06090C);
            graphics.fill(fx0, fy1 - 1, fx1, fy1, light ? LIGHT_FRAME_HI : 0xFF34404C);
            graphics.fill(fx1 - 1, fy0, fx1, fy1, light ? LIGHT_FRAME_HI : 0xFF34404C);
        }
    }

    @Override
    protected void drawSlot(GuiGraphics graphics, Slot slot, int x, int y) {
        //1.0.4-beta (lot 31) : salle blanche : cases du joueur claires aussi (slot_inv_cleanroom)
        String name = slot instanceof MenuSlot ? sprite("slot") : light() ? "slot_inv_" + GuiSprites.LIGHT : "slot";
        if (!GuiSprites.draw(graphics, name, x - 1, y - 1, 18, 18, 18, 18)) {
            super.drawSlot(graphics, slot, x, y);
        }
        if (slot instanceof MenuSlot menuSlot && !slot.hasItem()) {
            //Aide declaree par la case elle-meme (lot 3 : plus de regles par classe de machine ici)
            ItemStack ghost = menuSlot.slot.getGhost();
            String icon = ghost.isEmpty() ? menuSlot.slot.getGhostIcon() : null;
            if (!ghost.isEmpty() || icon != null && GuiSprites.has(icon)) {
                if (icon != null) {
                    GuiSprites.draw(graphics, icon, x, y, 16, 16, 16, 16);
                } else {
                    graphics.renderFakeItem(ghost, x, y);
                }
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 250);
                graphics.fill(x, y, x + 16, y + 16, light() ? 0xC8E9EDF0 : 0xC80C1015);
                graphics.pose().popPose();
            }
        }
    }

    // ------------------------------------------------------------------
    // Fond
    // ------------------------------------------------------------------

    /**
     * Fleche de progression dessinee a cette image (relative au panneau), ou null : zone cliquable « Voir les recettes » de JEI
     * (integration/jei/MachineArrowHandler). Remise a zero a chaque fond, posee par progressArrow et StandardView.arrow.
     */
    @Nullable
    public static Rect2i jeiArrow;

    /** Bloc de la machine ouverte (catalyseur des recettes de JEI), ou null. */
    @Nullable
    public Block machineBlock() {
        return tile() == null ? null : tile().getBlockState().getBlock();
    }

    @Override
    protected void drawBackground(GuiGraphics graphics, int mouseX, int mouseY) {
        jeiArrow = null;
        sliders.clear();
        BlockEntity tile = tile();
        if (tile != null) {
            //API Machine (lot 2) : vue de la fiche (registre MachineScreens), plus d'aiguillage par classe ici
            view.draw(this, graphics, mouseX, mouseY, tile);
        }
        recipeSlot(graphics, mouseX, mouseY);
        if (squareShown() || sidesOpen || infoOpen || structureOpen) {
            //Lot 3 : la fleche (clic = recettes dans JEI) est sous le carre ouvert ; 1.0.6 (retour de Guillaume du 16/11/2026) : de meme sous
            //le panneau des faces, la fiche et la structure (generateurs : « Voir toutes les recettes » passait par-dessus le panneau des faces)
            jeiArrow = null;
        }
    }

    /** Ce que le carre du selecteur montre : les recettes, les entrees, le verrou, les favoris de la machine, sa puissance au tier. */
    private RecipeSquare.Context squareContext(RecipeLockable rl) {
        return new RecipeSquare.Context(context(rl), rl.recipeSource(), Tiers.power(Math.max(0, Math.min(3, rl.recipeTier()))));
    }

    /** Titre : interface standard v2, nom sans tier et pastilles (tier, multibloc) ; sinon le titre d'avant. */
    @Override
    protected void drawTitle(GuiGraphics graphics, int mouseX, int mouseY) {
        if (standard) {
            fr.pioneers.api.machine.MachineRegistry.Entry e = tile() == null ? null : fr.pioneers.api.machine.MachineRegistry.of(tile().getBlockState().getBlock());
            int tier = e == null ? -1 : e.tier();
            if (tile() != null) {
                tier = view.titleTier(this, tile(), tier);
            }
            StandardView.title(this, graphics, mouseX, mouseY, title, imageWidth, tier, e == null ? null : e.spec(),
                  tile() == null ? null : view.titleNote(this, tile()));
        } else {
            super.drawTitle(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void drawForeground(GuiGraphics graphics, int mouseX, int mouseY) {
        BlockEntity tile = tile();
        if (standard) {
            //Libelle de l'inventaire, dans son cadre (1.0.4-beta lot 31 : cadre clair de la salle blanche : encre sombre)
            GuiDraw.text(graphics, font, playerInventoryTitle, 8 + (imageWidth - 176) / 2F, imageHeight - StandardLayout.INVENTORY + StandardLayout.INV_LABEL,
                  0.8F, ink(DIM), 80);
            if (!sidesOpen && !infoOpen && !structureOpen && tile != null) {
                view.foreground(this, graphics, mouseX, mouseY, tile);
            }
        } else if (tile != null) {
            //Lot 8c : plus de vue d'avant (etablis, stabilisateur, simulateur, absorbeur sont au registre, dans le cadre v2) ; une vue hors
            //cadre v2 garde son premier plan
            view.foreground(this, graphics, mouseX, mouseY, tile);
        }
        tabs(graphics, mouseX, mouseY);
    }

    /** 0.19.13 : le panneau des faces passe par-dessus tout (cases, nombres des piles, textes de la machine). */
    @Override
    protected void drawOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        if (sidesOpen) {
            sidesView(graphics, mouseX, mouseY);
        } else if (structureOpen && standard && tile() != null) {
            view.structure(this, graphics, mouseX, mouseY, tile());
        } else if (infoOpen && standard && tile() != null) {
            StandardView.info(this, graphics, mouseX, mouseY, tile(), imageWidth, lockable() == null ? 0 : lockable().recipeChoices().size(), view);
        } else if (squareShown()) {
            //Lot 3 : le carre ouvert, par-dessus la zone machine ; il avale les clics et les infobulles de ce qui est dessous
            RecipeLockable rl = lockable();
            StandardLayout.Rect q = squareRect();
            if (over(mouseX, mouseY, q.x() - 2, q.y() - 1, q.w() + 3, q.h() + 2)) {
                tooltip(null);
            }
            swallow(q.x() - 2, q.y() - 1, q.w() + 3, q.h() + 2);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, RecipeSquare.BASE_Z - 350);
            square.draw(graphics, squareHost, mouseX, mouseY, q.x(), q.y(), squareContext(rl), accent(), sprite("panel"), () -> setSquare(false));
            graphics.pose().popPose();
        }
        RecipeLockable lockable = lockable();
        if (picker.isOpen() && lockable != null && !sidesOpen && !standard) {
            int[] r = pickerRect();
            picker.draw(graphics, pickerHost, mouseX, mouseY, r[0], r[1], r[2], r[3], context(lockable), accent());
        } else if (picker.isOpen()) {
            picker.close();
        }
    }

    /** 0.19.13 : sous le panneau des faces ouvert, aucune case ne reagit (ni survol, ni infobulle, ni clic). */
    @Override
    protected boolean covers(double mouseX, double mouseY) {
        double x = mouseX - leftPos, y = mouseY - topPos;
        if (picker.isOpen()) {
            int[] r = pickerRect();
            if (x >= r[0] - 1 && x < r[0] + r[2] + 1 && y >= r[1] - 1 && y < r[1] + r[3] + 1) {
                return true;
            }
        }
        if (squareShown()) {
            StandardLayout.Rect q = squareRect();
            if (x >= q.x() - 2 && x < q.x() + q.w() + 1 && y >= q.y() - 1 && y < q.y() + q.h() + 1) {
                return true;
            }
        }
        if ((infoOpen || structureOpen) && standard && !sidesOpen) {
            StandardLayout.Rect r = StandardView.infoRect(imageWidth);
            if (x >= r.x() - 1 && x < r.x() + r.w() + 1 && y >= r.y() - 1 && y < r.y() + r.h() + 1) {
                return true;
            }
        }
        return sidesOpen && x >= sidesX() - 1 && x < sidesX() + SIDES_W + 1 && y >= SIDES_Y - 1 && y < SIDES_Y + SIDES_H + 1;
    }

    /** Zone qui avale les clics d'un panneau ouvert (rien dessous ne reagit). */
    void swallow(int x, int y, int w, int h) {
        click(x, y, w, h, () -> {
        });
    }

    /** JEI : zones de l'interface hors du panneau (onglets ; lot 3 : le carre ouvert est DANS le panneau), coordonnees ecran. */
    public List<Rect2i> extraAreas() {
        List<Rect2i> out = new ArrayList<>();
        int tabs = tabCount();
        if (standard) {
            if (tabs > 0) {
                out.add(new Rect2i(leftPos + imageWidth, topPos + TAB_Y, tab(), tabs * (tab() + 2)));
            }
        } else if (tabs > 0) {
            out.add(new Rect2i(leftPos + TAB_X, topPos + TAB_Y, tab(), tabs * (tab() + 2)));
        }
        return out;
    }

    /** JEI : grille du carre du selecteur (coordonnees ecran), cible d'un objet glisse (recherche par cet objet), ou null. */
    @Nullable
    public Rect2i squareGridArea() {
        if (!squareShown()) {
            return null;
        }
        StandardLayout.Rect q = squareRect();
        return new Rect2i(leftPos + q.x() + RecipeSquare.GRID_X, topPos + q.y() + RecipeSquare.GRID_Y, RecipeSquare.COLS * RecipeSquare.CELL,
              RecipeSquare.ROWS * RecipeSquare.CELL);
    }

    /** 1.0.5-beta (lot 17) : JEI : carte des regles de l'interface de sortie d'entrepot (coordonnees ecran), cible d'un objet glisse ; ou null. */
    @Nullable
    public Rect2i warehouseKeepArea() {
        StandardLayout.Rect r = sidesOpen || infoOpen ? null : WarehouseViews.keepArea(tile());
        return r == null ? null : new Rect2i(leftPos + r.x(), topPos + r.y(), r.w(), r.h());
    }

    /** 1.0.5-beta (lot 17) : JEI : objet glisse sur les regles de l'interface de sortie : regle « garder une pile ». */
    public void warehouseKeepDropped(ItemStack stack) {
        WarehouseViews.keepDropped(tile(), stack);
    }

    /** JEI : objet glisse sur la grille du carre : recherche par cet objet. */
    public void searchRecipesFor(ItemStack stack) {
        square.searchFor(stack);
    }

    // ------------------------------------------------------------------
    // MAJ Recettes (lot 7) : case « Recette » et selecteur
    // ------------------------------------------------------------------

    /** La machine ouverte a-t-elle un selecteur (une case et des recettes) ? */
    @Nullable
    private RecipeLockable lockable() {
        RecipeLockable rl = StorageViews.as(RecipeLockable.class, tile());
        return rl != null && rl.recipeSlot() != null && !rl.recipeChoices().isEmpty() ? rl : null;
    }

    private static RecipePicker.Context context(RecipeLockable rl) {
        return new RecipePicker.Context(rl.recipeChoices(), rl.presentItems(), rl.presentFluids(), rl.presentGases(), rl.lockedRecipe(), rl.recipeTier());
    }

    /** Panneau du selecteur : toute l'interface sous le titre (il passe par-dessus l'inventaire le temps du choix). */
    private int[] pickerRect() {
        return new int[]{4, 16, imageWidth - 8, imageHeight - 20};
    }

    /** Pose (id) ou retire (null) le verrou : paquet borne, verifie par le serveur. */
    public void lockRecipe(@Nullable ResourceLocation id) {
        RecipePicker.remember(id);
        if (tile() != null) {
            PiNetwork.CHANNEL.sendToServer(new PacketRecipeLock(tile().getBlockPos(), null, id == null ? "" : id.toString()));
        }
    }

    /** JEI : zone de la case « Recette » (coordonnees ecran), ou null. */
    @Nullable
    public Rect2i recipeSlotArea() {
        RecipeLockable rl = lockable();
        if (rl == null || squareShown()) {
            return null;
        }
        int[] p = rl.recipeSlot();
        return new Rect2i(leftPos + p[0] - 1, topPos + p[1] - 1, 18, 18);
    }

    /** JEI : objet glisse sur la case « Recette » : verrouille la premiere recette de la machine qui le fait. */
    public boolean lockRecipeMaking(ItemStack stack) {
        RecipeLockable rl = lockable();
        ResourceLocation id = rl == null ? null : RecipePicker.recipeFor(context(rl), stack);
        if (id != null) {
            lockRecipe(id);
            return true;
        }
        return false;
    }

    /**
     * 1.0.5-beta (lot 44, retour 62) : bouton « + » de JEI sur une recette a OUTIL : met l'outil de la recette (pris dans l'inventaire du
     * joueur) dans la case Outil de la machine ouverte, par des clics ordinaires de l'inventaire (verifies par le serveur) : un outil (Maj :
     * toute la pile) ; un autre outil deja dans la case revient d'abord dans l'inventaire. Rien si l'outil est deja en place ou absent.
     */
    public void placeTool(Object recipe, boolean all) {
        net.minecraft.world.item.crafting.Ingredient want = Remanents.ingredientOf(recipe);
        fr.pioneers.core.inventory.ItemSlot tool = ToolBay.of(tile());
        Minecraft mc = Minecraft.getInstance();
        if (want == null || tool == null || mc.gameMode == null || mc.player == null || !menu.getCarried().isEmpty()) {
            return;
        }
        int toolIndex = -1, from = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot sl = menu.slots.get(i);
            if (sl instanceof fr.pioneers.core.menu.MenuSlot ms && ms.slot == tool) {
                toolIndex = i;
            } else if (!(sl instanceof fr.pioneers.core.menu.MenuSlot) && from < 0 && sl.hasItem() && want.test(sl.getItem())) {
                from = i;
            }
        }
        if (toolIndex < 0 || !tool.isEmpty() && want.test(tool.getStack()) || from < 0) {
            return;
        }
        int id = menu.containerId;
        if (!tool.isEmpty()) {
            mc.gameMode.handleInventoryMouseClick(id, toolIndex, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
            if (!tool.isEmpty()) {
                return;                                                   // inventaire plein : l'ancien outil reste en place
            }
        }
        if (all) {
            mc.gameMode.handleInventoryMouseClick(id, from, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
            return;
        }
        mc.gameMode.handleInventoryMouseClick(id, from, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(id, toolIndex, 1, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
        if (!menu.getCarried().isEmpty()) {
            mc.gameMode.handleInventoryMouseClick(id, from, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
        }
    }

    /** JEI : verrouille cette recette si la machine ouverte la fait (bouton « + » de JEI). */
    public boolean canLockRecipe(ResourceLocation id) {
        RecipeLockable rl = lockable();
        return rl != null && RecipeLockable.find(rl.recipeChoices(), id) != null;
    }

    /**
     * Case « Recette » (fantome) : l'icone de la recette verrouillee et un cadenas, sinon un pictogramme de liste ; pastille ambre
     * quand plusieurs recettes prennent les entrees presentes sans verrou, coin rouge quand la recette depasse le tier de la machine.
     */
    private void recipeSlot(GuiGraphics g, int mouseX, int mouseY) {
        RecipeLockable rl = lockable();
        if (rl == null) {
            return;
        }
        int[] p = rl.recipeSlot();
        int x = p[0], y = p[1];
        if (!GuiSprites.draw(g, sprite("slot"), x - 1, y - 1, 18, 18, 18, 18)) {
            slot(g, x, y);
        }
        RecipePicker.Context c = context(rl);
        RecipeChoice locked = rl.lockedChoice();
        boolean missing = locked == null && rl.lockedRecipe() != null;
        boolean low = locked != null && locked.tier() > rl.recipeTier();
        int ambiguous = RecipePicker.ambiguous(c);
        if (locked != null && !locked.icon().isEmpty()) {
            g.renderItem(locked.icon(), x, y);
        } else {
            //Pictogramme « liste de recettes » (fantome)
            int dim = 0xFF4A5664;
            for (int i = 0; i < 3; i++) {
                g.fill(x + 3, y + 4 + i * 4, x + 5, y + 6 + i * 4, dim);
                g.fill(x + 7, y + 4 + i * 4, x + 13, y + 6 + i * 4, dim);
            }
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 260);
        if (locked != null) {
            //Cadenas (bas a droite)
            int lx = x + 11, ly = y + 10;
            g.fill(lx, ly + 2, lx + 5, ly + 6, 0xFFE8C050);
            g.fill(lx + 1, ly, lx + 4, ly + 1, 0xFFE8C050);
            g.fill(lx, ly + 1, lx + 1, ly + 2, 0xFFE8C050);
            g.fill(lx + 4, ly + 1, lx + 5, ly + 2, 0xFFE8C050);
            g.fill(lx + 2, ly + 3, lx + 3, ly + 5, 0xFF5A4010);
        }
        if (low || missing) {
            for (int k = 0; k < 5; k++) {
                g.fill(x + 11 + k, y, x + 16, y + 1 + k, RecipePicker.LOW_TIER);
            }
        }
        if (ambiguous > 0) {
            //Pastille ambre « ! » (haut a gauche), clignote doucement
            boolean bright = (System.currentTimeMillis() / 500) % 2 == 0;
            g.fill(x - 1, y - 1, x + 5, y + 6, bright ? RecipePicker.WARN : 0xFFB08020);
            g.fill(x + 1, y, x + 3, y + 3, 0xFF201000);
            g.fill(x + 1, y + 4, x + 3, y + 5, 0xFF201000);
        }
        g.pose().popPose();
        if (picker.isOpen()) {
            g.renderOutline(x - 1, y - 1, 18, 18, 0xFF000000 | accent());
        }
        if (over(mouseX, mouseY, x, y, 16, 16) && !picker.isOpen() && !sidesOpen && !infoOpen && !structureOpen && !squareShown()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 270);
            g.fill(x, y, x + 16, y + 16, 0x40FFFFFF);
            g.pose().popPose();
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.pioneers.recipe_lock.slot"));
            if (locked != null) {
                tip.add(Component.translatable("gui.pioneers.recipe_lock.locked", locked.name()).withStyle(ChatFormatting.GREEN));
                tip.add(Component.translatable("gui.pioneers.recipe_lock.only").withStyle(ChatFormatting.GRAY));
            } else if (missing) {
                tip.add(Component.translatable("gui.pioneers.recipe_lock.missing").withStyle(ChatFormatting.RED));
            } else {
                tip.add(Component.translatable("gui.pioneers.recipe_lock.none").withStyle(ChatFormatting.GRAY));
            }
            if (low) {
                //1.0.4-beta (lot 32) : salle blanche : « hors de portee », jamais le tier
                tip.add((light() ? Component.translatable("gui.pioneers.recipe_lock.cleanroom_low")
                         : Component.translatable("gui.pioneers.recipe_lock.tier_low", RecipePicker.tierName(locked.tier()))).withStyle(ChatFormatting.RED));
            }
            if (ambiguous > 0) {
                tip.add(Component.translatable("gui.pioneers.recipe_lock.warning", ambiguous).withStyle(ChatFormatting.GOLD));
            }
            tip.add(Component.translatable("gui.pioneers.recipe_lock.hint").withStyle(ChatFormatting.DARK_GRAY));
            tooltip(tip);
        }
        click(x, y, 16, 16, () -> {
            sidesOpen = false;
            if (!standard) {
                picker.toggle();
            } else {
                //Lot 3 : la case « Recette » ouvre le carre (recherche vide et active)
                setSquare(true);
            }
        });
    }

    /** 1.0.5-beta (lot 44) : liseré de la case « Outil » (couleur des outils, comme les cases catalyseur / outil de JEI). */
    public static final int TOOL_EDGE = 0xFFB08A3A;

    /**
     * 1.0.5-beta (lot 44, retour 62) : la case « Outil » des remanents (coin de l'objet en x, y ; panneau) : liseré doré autour de la case
     * (dessine sous la case), infobulle de la case vide : son role, la regle (jamais une case d'entree, ni ejectee) et les outils acceptes.
     */
    void toolSlot(GuiGraphics g, int mouseX, int mouseY, int x, int y, String machineKey) {
        g.fill(x - 2, y - 2, x + 18, y - 1, TOOL_EDGE);
        g.fill(x - 2, y + 17, x + 18, y + 18, TOOL_EDGE);
        g.fill(x - 2, y - 1, x - 1, y + 17, TOOL_EDGE);
        g.fill(x + 17, y - 1, x + 18, y + 17, TOOL_EDGE);
        if (over(mouseX, mouseY, x, y, 16, 16) && menu.getCarried().isEmpty() && hoveredSlot != null && !hoveredSlot.hasItem()) {
            List<Component> tip = new java.util.ArrayList<>();
            tip.add(Component.translatable("gui.pioneers.tool.slot").withStyle(s -> s.withColor(TOOL_EDGE & 0xFFFFFF)));
            tip.add(Component.translatable("gui.pioneers.tool.slot.help").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("gui.pioneers.tool.slot.keep").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("gui.pioneers.tool.slot.auto").withStyle(ChatFormatting.DARK_GRAY));
            net.minecraft.network.chat.MutableComponent names = Component.empty();
            int n = 0;
            for (net.minecraft.world.item.Item item : Remanents.items(machineKey)) {
                if (n > 0) {
                    names.append(", ");
                }
                if (n == 6) {
                    names.append("…");
                    break;
                }
                names.append(new net.minecraft.world.item.ItemStack(item).getHoverName());
                n++;
            }
            tip.add(Component.translatable("gui.pioneers.tool.slot.accepts", names).withStyle(ChatFormatting.DARK_AQUA));
            tooltip(tip);
        }
    }

    /**
     * 1.0.5-beta (lot 46, retour 72) : case reservee d'ECHANTILLON du generateur de roches (meme lisere que la case Outil) : vide, l'infobulle
     * dit ce qu'elle attend (roches de ce tier, eau et lave jamais prelevees, jamais de minerai) ; un objet tenu a la souris qui n'est pas un
     * echantillon de ce tier : « Echantillon refuse : <raison> » (minerai, roche a valeur de ressource, pas une roche de la liste, tier trop bas).
     */
    void sampleSlot(GuiGraphics g, int mouseX, int mouseY, int x, int y, int tier) {
        g.fill(x - 2, y - 2, x + 18, y - 1, TOOL_EDGE);
        g.fill(x - 2, y + 17, x + 18, y + 18, TOOL_EDGE);
        g.fill(x - 2, y - 1, x - 1, y + 17, TOOL_EDGE);
        g.fill(x + 17, y - 1, x + 18, y + 17, TOOL_EDGE);
        if (!over(mouseX, mouseY, x, y, 16, 16)) {
            return;
        }
        net.minecraft.world.item.ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            String why = fr.pioneers.tech.industry.RockSamples.refusal(carried, tier);
            if (why != null) {
                Component reason = fr.pioneers.tech.industry.RockSamples.TIER.equals(why)
                                   ? Component.translatable("gui.pioneers.sample.why.tier", tierName(fr.pioneers.tech.industry.RockSamples.minTier(carried)))
                                   : Component.translatable("gui.pioneers.sample.why." + why);
                tooltip(List.of(Component.translatable("gui.pioneers.sample.refused", reason).withStyle(ChatFormatting.RED)));
            }
            return;
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem()) {
            List<Component> tip = new java.util.ArrayList<>();
            tip.add(Component.translatable("gui.pioneers.sample.slot").withStyle(s -> s.withColor(TOOL_EDGE & 0xFFFFFF)));
            tip.add(Component.translatable("gui.pioneers.sample.slot.help").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("gui.pioneers.sample.slot.fluids").withStyle(ChatFormatting.GRAY));
            net.minecraft.network.chat.MutableComponent names = Component.empty();
            int n = 0;
            for (net.minecraft.world.item.Item item : fr.pioneers.tech.industry.RockSamples.items(tier)) {
                if (n > 0) {
                    names.append(", ");
                }
                if (n == 8) {
                    names.append("…");
                    break;
                }
                names.append(new net.minecraft.world.item.ItemStack(item).getHoverName());
                n++;
            }
            tip.add(Component.translatable("gui.pioneers.sample.slot.accepts", names).withStyle(ChatFormatting.DARK_AQUA));
            tip.add(Component.translatable("gui.pioneers.sample.slot.never").withStyle(ChatFormatting.DARK_GRAY));
            tooltip(tip);
        }
    }

    /** « MKI » .. « MKIV » (0..3). */
    private static Component tierName(int t) {
        return Component.literal(switch (Math.max(0, Math.min(3, t))) {
            case 0 -> "MKI";
            case 1 -> "MKII";
            case 2 -> "MKIII";
            default -> "MKIV";
        });
    }

    /** Infobulle d'une case vide (ce qu'elle attend). */
    void slotTip(int mouseX, int mouseY, int x, int y, String key) {
        if (over(mouseX, mouseY, x, y, 16, 16) && menu.getCarried().isEmpty() && hoveredSlot != null && !hoveredSlot.hasItem()) {
            tooltip(List.of(Component.translatable(key)));
        }
    }

    static void arrow(GuiGraphics graphics, int x, int y0, int y1) {
        graphics.fill(x, y0, x + 1, y1, 0xFF3C4A57);
        graphics.fill(x - 2, y1 - 2, x + 3, y1 - 1, 0xFF3C4A57);
        graphics.fill(x - 1, y1 - 1, x + 2, y1, 0xFF3C4A57);
    }

    // ------------------------------------------------------------------
    // Ecran d'etat
    // ------------------------------------------------------------------

    /** Ligne de l'ecran : libelle a gauche (gris), valeur alignee a droite. */
    record Row(Component label, Component value, int color) {

        static Row of(String key, Component value) {
            return new Row(Component.translatable(key), value, SCREEN_TEXT);
        }
    }

    /**
     * Ecran d'etat : en-tete (pastille de couleur, nom, valeur a droite), barre de remplissage ({@code ratio} < 0 : aucune), lignes ;
     * la derniere ligne est detachee par un filet si {@code lastApart}.
     */
    void info(GuiGraphics graphics, int x, int y, int w, int h, int chip, Component name, Component headline, double ratio, int barColor,
          List<Row> rows, boolean lastApart) {
        if (!GuiSprites.nine(graphics, sprite("screen"), x, y, w, h, 104, 60, 7)) {
            screen(graphics, x, y, w, h);
        }
        //1.0.4-beta (lot 31) : ecran en verre blanc de la salle blanche : encres sombres, filet et fond de barre clairs
        boolean light = light();
        int separator = light ? LIGHT_SEPARATOR : SEPARATOR;
        int textX = x + 6;
        if (chip != 0) {
            graphics.fill(x + 6, y + 6, x + 11, y + 11, 0xFF000000 | chip);
            textX = x + 14;
        }
        //Nom et etat se partagent la ligne : l'etat prend au plus la moitie, le nom le reste (jamais de largeur nulle = sans limite)
        float hw = Math.min(font.width(headline) * 0.85F, (w - 12) / 2F);
        GuiDraw.text(graphics, font, name, textX, y + 5, 0.85F, ink(TEXT), Math.max(1, x + w - 6 - textX - hw - 4));
        if (hw > 0) {
            GuiDraw.text(graphics, font, headline, x + w - 6 - hw, y + 5, 0.85F, ink(SCREEN_TEXT), hw);
        }
        graphics.fill(x + 5, y + 15, x + w - 5, y + 16, separator);
        int ry = y + 19;
        if (ratio >= 0) {
            graphics.fill(x + 5, y + 18, x + w - 5, y + 25, light ? LIGHT_BAR_BG : BAR_BG);
            int bw = (int) Math.round((w - 12) * Math.max(0, Math.min(1, ratio)));
            if (bw > 0) {
                graphics.fill(x + 6, y + 19, x + 6 + bw, y + 24, 0xFF000000 | barColor);
                graphics.fill(x + 6, y + 19, x + 6 + bw, y + 20, 0xFF000000 | GuiDraw.darken(barColor, 1F) | 0x303030);
            }
            ry = y + 29;
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (lastApart && i == rows.size() - 1 && rows.size() > 1) {
                graphics.fill(x + 5, ry + 1, x + w - 5, ry + 2, separator);
                ry += 4;
            }
            //Une ligne qui sortirait de l'ecran n'est pas dessinee ; valeur bornee, le libelle prend le reste
            if (ry + 7 > y + h - 1) {
                break;
            }
            float vw = Math.min(font.width(row.value) * 0.7F, w - 32);
            GuiDraw.text(graphics, font, row.label, x + 6, ry + 1, 0.6F, ink(DIM), Math.max(1, w - 12 - vw - 3));
            GuiDraw.text(graphics, font, row.value, x + w - 6 - vw, ry, 0.7F, ink(row.color), vw);
            ry += 9;
        }
    }

    static Component literal(String text) {
        return Component.literal(text);
    }

    static Component flow(double perTick, String unit) {
        return literal((perTick > 0 ? "+" : "") + compact(perTick) + " " + unit + "/t");
    }

    static int flowColor(double perTick) {
        return perTick > 0 ? FLOW_UP : perTick < 0 ? FLOW_DOWN : DIM;
    }

    static String compact(double value) {
        double abs = Math.abs(value);
        if (abs >= 1000) {
            return Texts.format(Math.round(value));
        }
        return String.format(Locale.ROOT, abs >= 10 || value == 0 ? "%.0f" : "%.1f", value);
    }

    static Component percent(double ratio) {
        return literal(Math.round(Math.max(0, Math.min(1, ratio)) * 100) + " %");
    }

    // ------------------------------------------------------------------
    // Reglages (lot 3 : les vues des buffers, du radiateur et du compresseur sont dans StorageViews)
    // ------------------------------------------------------------------

    private static final int STEP_H = 14;

    /** Fleche verticale de progression (entree -> sortie), remplie de haut en bas. */
    static void progressArrow(GuiGraphics graphics, int cx, int y0, int y1, double ratio) {
        jeiArrow = new Rect2i(cx - 4, y0, 9, Math.max(4, y1 - y0));
        int h = y1 - y0 - 3;
        graphics.fill(cx - 1, y0, cx + 2, y0 + h, 0xFF0B1016);
        int filled = (int) Math.round(h * Math.max(0, Math.min(1, ratio)));
        if (filled > 0) {
            graphics.fill(cx - 1, y0, cx + 2, y0 + filled, 0xFF3FD0A0);
            graphics.fill(cx - 1, y0, cx, y0 + filled, 0xFF8CF5C6);
        }
        int head = ratio >= 0.999 ? 0xFF3FD0A0 : 0xFF3C4A57;
        graphics.fill(cx - 3, y0 + h, cx + 4, y0 + h + 1, head);
        graphics.fill(cx - 2, y0 + h + 1, cx + 3, y0 + h + 2, head);
        graphics.fill(cx - 1, y0 + h + 2, cx + 2, y0 + h + 3, head);
    }

    /** Flamme du generateur (14 x 14) : sa hauteur suit le combustible restant. */
    static void flame(GuiGraphics graphics, int x, int y, double ratio) {
        int[] widths = {1, 2, 3, 4, 6, 8, 9, 10, 11, 12, 12, 12, 10, 8};
        int lit = (int) Math.ceil(widths.length * Math.max(0, Math.min(1, ratio)));
        for (int i = 0; i < widths.length; i++) {
            int row = widths.length - 1 - i;
            int w = widths[i], fx = x + (14 - w) / 2, fy = y + i;
            boolean on = row < lit;
            graphics.fill(fx, fy, fx + w, fy + 1, on ? lerp(0xFFE0451E, 0xFFFFE07A, i / (double) widths.length) : 0xFF1A2027);
        }
    }

    /** Reglage : [-] valeur [+] (relatif au panneau), infobulle = ce qu'on regle. Maj + clic : cinq crans. */
    void stepper(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, Component value, Component what, int down, int up) {
        int times = hasShiftDown() ? 5 : 1;
        steelButton(graphics, mouseX, mouseY, x, y, 12, STEP_H, "-", () -> repeat(down, times));
        int sx = x + 13, sw = w - 26;
        graphics.fill(sx, y, sx + sw, y + STEP_H, 0xFF0B1016);
        graphics.fill(sx, y, sx + sw, y + 1, STEEL_DARK);
        graphics.fill(sx, y, sx + 1, y + STEP_H, STEEL_DARK);
        graphics.fill(sx, y + STEP_H - 1, sx + sw, y + STEP_H, STEEL_LIGHT);
        graphics.fill(sx + sw - 1, y, sx + sw, y + STEP_H, STEEL_LIGHT);
        GuiDraw.center(graphics, font, value, sx + sw / 2F, y + 4, 0.7F, SCREEN_TEXT, sw - 4);
        steelButton(graphics, mouseX, mouseY, x + w - 12, y, 12, STEP_H, "+", () -> repeat(up, times));
        if (over(mouseX, mouseY, x, y, w, STEP_H)) {
            tooltip(List.of(what, Component.translatable("gui.pioneers.machine.step_tip").withStyle(ChatFormatting.DARK_GRAY)));
        }
    }

    /** Bouton d'acier en relief (enfonce au survol). */
    void steelButton(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, String label, Runnable action) {
        boolean hover = over(mouseX, mouseY, x, y, w, h);
        buttonBody(graphics, x, y, w, h, true, hover);
        GuiDraw.center(graphics, font, literal(label), x + w / 2F + 0.5F, y + (h - 7) / 2F, 1F, light() ? INK : hover ? 0xFFFFFFFF : TEXT, w);
        click(x, y, w, h, action);
    }

    /** Corps d'un bouton : acier sombre ; lot 31 : bouton laque claire sur l'interface de salle blanche (meme decoupe, memes biseaux). */
    private void buttonBody(GuiGraphics graphics, int x, int y, int w, int h, boolean active, boolean hover) {
        boolean light = light();
        graphics.fill(x, y, x + w, y + h, light ? LIGHT_FRAME_DARK : 0xFF14181E);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, light ? !active ? 0xFFE6E9EC : hover ? 0xFFF7F9FA : 0xFFDCE1E6
              : !active ? 0xFF1A1F26 : hover ? STEEL_HOVER : STEEL);
        if (active) {
            int edge = light ? hover ? 0xFFB9C1C9 : 0xFFFFFFFF : hover ? STEEL_DARK : STEEL_LIGHT;
            graphics.fill(x + 1, y + 1, x + w - 1, y + 2, edge);
            graphics.fill(x + 1, y + 1, x + 2, y + h - 1, edge);
        }
    }

    /** Bouton d'acier a libelle traduit (reduit puis coupe s'il est trop long) ; inactif : grise, sans clic. */
    void steelButton(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, Component label, boolean active, Runnable action) {
        boolean hover = active && over(mouseX, mouseY, x, y, w, h);
        buttonBody(graphics, x, y, w, h, active, hover);
        float scale = h >= 14 ? 0.8F : 0.65F;
        GuiDraw.center(graphics, font, label, x + w / 2F + 0.5F, y + (h - 8 * scale) / 2F, scale, light() ? !active ? INK_DIM : INK
              : !active ? 0xFF59666A : hover ? 0xFFFFFFFF : TEXT, w - 4);
        if (active) {
            click(x, y, w, h, action);
        }
    }

    /** Bouton d'onglet de page : allume (accent) quand la page est affichee. */
    void pageButton(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, Component label, boolean selected, int accent, Runnable action) {
        if (selected) {
            graphics.fill(x, y, x + w, y + h, 0xFF14181E);
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000 | GuiDraw.darken(accent, 0.35F));
            graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF000000 | accent);
            GuiDraw.center(graphics, font, label, x + w / 2F, y + (h - 6.4F) / 2F, 0.8F, 0xFFFFFFFF, w - 4);
        } else {
            steelButton(graphics, mouseX, mouseY, x, y, w, h, label, true, action);
        }
    }

    /** Ecran sombre (neuf morceaux de l'ecran du tier). */
    /**
     * 1.0.4-beta (lot 34) : FOND D'UN CHAMP DE SAISIE (recherche du comptoir et des etablis) : creux plat, filet sombre en haut et a gauche,
     * filet clair en bas. L'ecran en neuf morceaux ({@link #screenAt}) a des coins de 7 px : sur 11 px de haut ses equerres passaient sous
     * les lettres.
     */
    void fieldAt(GuiGraphics graphics, int x, int y, int w, int h) {
        boolean light = light();
        graphics.fill(x, y, x + w, y + h, light ? LIGHT_FRAME : 0xFF0B1016);
        graphics.fill(x, y, x + w, y + 1, light ? LIGHT_FRAME_DARK : 0xFF05080B);
        graphics.fill(x, y, x + 1, y + h, light ? LIGHT_FRAME_DARK : 0xFF05080B);
        graphics.fill(x + 1, y + h - 1, x + w, y + h, light ? LIGHT_FRAME_HI : 0xFF2C3844);
    }

    void screenAt(GuiGraphics graphics, int x, int y, int w, int h) {
        if (!GuiSprites.nine(graphics, sprite("screen"), x, y, w, h, 104, 60, 7)) {
            screen(graphics, x, y, w, h);
        }
    }

    int accent() {
        return GuiSprites.accent(tier());
    }

    private void repeat(int action, int times) {
        for (int i = 0; i < times; i++) {
            send(action);
        }
    }

    // ------------------------------------------------------------------
    // Jauges
    // ------------------------------------------------------------------

    /** Cadre de jauge (neuf morceaux du cadre du tier). */
    void frame(GuiGraphics graphics, int x, int y, int w, int h) {
        if (!GuiSprites.nine(graphics, sprite("gauge"), x - 1, y - 1, w + 2, h + 2, 26, 62, 3)) {
            graphics.fill(x, y, x + w, y + h, SCREEN);
            graphics.renderOutline(x, y, w, h, EDGE);
        }
    }

    /** Reserve d'energie verticale (rouge, comme Mekanism). */
    void energy(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h) {
        long stored = menu.value(UiValues.ENERGY), capacity = menu.value(UiValues.CAPACITY);
        frame(graphics, x, y, w, h);
        int filled = (int) Math.round((h - 2) * (capacity <= 0 ? 0 : Math.min(1, stored / (double) capacity)));
        if (filled > 0) {
            graphics.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - 1, 0xFFC8383A);
            graphics.fill(x + 1, y + h - 1 - filled, x + 3, y + h - 1, 0xFFE05A58);
            graphics.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - filled, 0x80FFFFFF);
        }
        ticks(graphics, x, y, w, h);
        if (over(mouseX, mouseY, x, y, w, h)) {
            tooltip(List.of(Component.translatable("gui.pioneers.energy.stored", Texts.energy(stored), Texts.energy(capacity)),
                  Component.translatable("gui.pioneers.energy.using", Texts.energy(Math.round(menu.value(UiValues.USAGE) / 100.0))).withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Thermometre : du rouge sombre au blanc incandescent ; trait de la temperature visee si {@code target} > 0. */
    void thermometer(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, double kelvin, int target, List<Component> tip) {
        thermometer(graphics, mouseX, mouseY, x, y, w, h, kelvin, target, tip, 300, 1_800);
    }

    /** 1.0.3-beta (correctif) : thermometre a echelle choisie ({@code minK} en bas, {@code maxK} en haut) : chaudiere de 0 a 120 C. */
    void thermometer(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, double kelvin, int target, List<Component> tip,
          double minK, double maxK) {
        frame(graphics, x, y, w, h);
        double span = Math.max(1, maxK - minK);
        double ratio = Math.max(0, Math.min(1, (kelvin - minK) / span));
        int filled = (int) Math.round((h - 2) * ratio);
        for (int i = 0; i < filled; i++) {
            double t = i / (double) (h - 2);
            int color = t < 0.33 ? lerp(0xFF5A2A20, 0xFFB5281A, t / 0.33) : t < 0.66 ? lerp(0xFFB5281A, 0xFFFF7A1F, (t - 0.33) / 0.33)
                  : lerp(0xFFFF7A1F, 0xFFFFF2C0, (t - 0.66) / 0.34);
            graphics.fill(x + 1, y + h - 2 - i, x + w - 1, y + h - 1 - i, color);
        }
        if (target > 0) {
            int ty = y + h - 1 - (int) Math.round((h - 2) * Math.max(0, Math.min(1, (target - minK) / span)));
            graphics.fill(x - 1, ty, x + w + 1, ty + 1, lightStatic ? INK : 0xFFFFFFFF);
        }
        ticks(graphics, x, y, w, h);
        if (over(mouseX, mouseY, x, y, w, h)) {
            tooltip(tip);
        }
    }

    /** Manometre vertical : air bleu-blanc ; trait de la pression demandee si {@code target} >= 0. */
    void manometer(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, double ratio, double target, List<Component> tip) {
        frame(graphics, x, y, w, h);
        int filled = (int) Math.round((h - 2) * Math.max(0, Math.min(1, ratio)));
        for (int i = 0; i < filled; i++) {
            double t = i / (double) (h - 2);
            graphics.fill(x + 1, y + h - 2 - i, x + w - 1, y + h - 1 - i, lerp(0xFF6F8EA6, 0xFFE6EEF4, t));
        }
        if (target >= 0) {
            int ty = y + h - 1 - (int) Math.round((h - 2) * Math.min(1, target));
            graphics.fill(x - 1, ty, x + w + 1, ty + 1, 0xFF8CF5C6);
        }
        ticks(graphics, x, y, w, h);
        if (over(mouseX, mouseY, x, y, w, h)) {
            tooltip(tip);
        }
    }

    static void ticks(GuiGraphics graphics, int x, int y, int w, int h) {
        for (int i = 1; i < 8; i++) {
            int ty = y + h * i / 8;
            //1.0.4-beta (lot 31) : graduations grises sur le verre blanc de la salle blanche
            graphics.fill(x + w - (i % 2 == 0 ? 5 : 3), ty, x + w - 1, ty + 1, lightStatic ? LIGHT_TICK : 0x80FFFFFF);
        }
    }

    static void tiled(GuiGraphics graphics, TextureAtlasSprite sprite, int tint, int x, int y, int w, int h) {
        graphics.setColor((tint >> 16 & 255) / 255F, (tint >> 8 & 255) / 255F, (tint & 255) / 255F, 1F);
        for (int ty = 0; ty < h; ty += 16) {
            for (int tx = 0; tx < w; tx += 16) {
                int pw = Math.min(16, w - tx), ph = Math.min(16, h - ty);
                graphics.enableScissor(leftPosStatic + x + tx, topPosStatic + y + h - ty - ph, leftPosStatic + x + tx + pw, topPosStatic + y + h - ty);
                graphics.blit(x + tx, y + h - ty - 16, 0, 16, 16, sprite);
                graphics.disableScissor();
            }
        }
        graphics.setColor(1, 1, 1, 1);
    }

    /** 1.0.6 : fantome d'une ressource attendue dans une jauge vide (texture a 30 %, sur toute la hauteur). */
    static void ghost(GuiGraphics graphics, TextureAtlasSprite sprite, int tint, int x, int y, int w, int h) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        graphics.setColor((tint >> 16 & 255) / 255F, (tint >> 8 & 255) / 255F, (tint & 255) / 255F, 0.3F);
        for (int ty = 0; ty < h; ty += 16) {
            for (int tx = 0; tx < w; tx += 16) {
                int pw = Math.min(16, w - tx), ph = Math.min(16, h - ty);
                graphics.enableScissor(leftPosStatic + x + tx, topPosStatic + y + h - ty - ph, leftPosStatic + x + tx + pw, topPosStatic + y + h - ty);
                graphics.blit(x + tx, y + h - ty - 16, 0, 16, 16, sprite);
                graphics.disableScissor();
            }
        }
        graphics.setColor(1, 1, 1, 1);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private static int leftPosStatic, topPosStatic;

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        leftPosStatic = leftPos;
        topPosStatic = topPos;
        lightStatic = light();
        super.renderBg(graphics, partialTick, mouseX, mouseY);
    }

    static TextureAtlasSprite atlas(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    static int lerp(int c0, int c1, double t) {
        int a = (int) ((c0 >>> 24) + ((c1 >>> 24) - (c0 >>> 24)) * t);
        int r = (int) ((c0 >> 16 & 255) + ((c1 >> 16 & 255) - (c0 >> 16 & 255)) * t);
        int g = (int) ((c0 >> 8 & 255) + ((c1 >> 8 & 255) - (c0 >> 8 & 255)) * t);
        int b = (int) ((c0 & 255) + ((c1 & 255) - (c0 & 255)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    // ------------------------------------------------------------------
    // Onglets (faces, ejection) et configuration des faces
    // ------------------------------------------------------------------

    @Nullable
    private SideConfig config() {
        MachineGui machine = menu.machine();
        return machine != null ? machine.getSideConfig() : null;
    }

    private void tabs(GuiGraphics graphics, int mouseX, int mouseY) {
        SideConfig config = config();
        int y = TAB_Y;
        if (hasSquare()) {
            //Lot 3 : onglet « Recettes » en tete (ouvre / ferme le carre ; icone de la recette verrouillee)
            recipeTab(graphics, mouseX, mouseY, y);
            y += tab() + 2;
        }
        if (config != null) {
            tab(graphics, mouseX, mouseY, y, "tab_sides", sidesOpen, () -> {
                sidesOpen = !sidesOpen;
                infoOpen = false;
                structureOpen = false;
                squareOpen = false;
                picker.close();
            },
                  List.of(Component.translatable("gui.pioneers.machine.sides")));
            y += tab() + 2;
        } else {
            sidesOpen = false;
        }
        if (redstone) {
            RedstoneMode mode = RedstoneMode.byIndex((int) menu.value(UiValues.REDSTONE));
            tab(graphics, mouseX, mouseY, y, "tab_redstone_" + mode.key(), mode != RedstoneMode.IGNORED,
                  () -> send(MachineTile.REDSTONE),
                  List.of(Component.translatable("gui.pioneers.machine.redstone." + mode.key()),
                        Component.translatable("gui.pioneers.machine.redstone_tip").withStyle(ChatFormatting.DARK_GRAY)));
            y += tab() + 2;
        }
        if (standard) {
            //API Machine (lot 2) : onglet Infos (regles du tier, couts, multibloc, recettes, aide courte)
            infoTab(graphics, mouseX, mouseY, y);
            y += tab() + 2;
        }
        if (hasStructureTab()) {
            //API Multibloc (lot 5) : onglet Structure (decompte par role, premiere faute, plan, JEI)
            structureTab(graphics, mouseX, mouseY, y);
        } else {
            structureOpen = false;
        }
    }

    /** Lot 5 : onglet Structure des multiblocs du moteur commun. */
    private boolean hasStructureTab() {
        return standard && tile() != null && view.structureTab(tile());
    }

    private void structureTab(GuiGraphics graphics, int mouseX, int mouseY, int y) {
        int tx = tabX();
        boolean hover = smallTab(graphics, mouseX, mouseY, y, structureOpen);
        tabIcon(graphics, "structure" + (structureOpen ? "_on" : ""), tx, y);
        if (hover) {
            tooltip(List.of(Component.translatable("gui.pioneers.multiblock.tab"),
                  Component.translatable("gui.pioneers.multiblock.tab_tip").withStyle(ChatFormatting.DARK_GRAY)));
        }
        click(tx, y, tab(), tab(), () -> {
            structureOpen = !structureOpen;
            infoOpen = false;
            sidesOpen = false;
            squareOpen = false;
        });
    }

    /** Nombre d'onglets (Recettes, faces, redstone, Infos de l'interface v2 ; l'ejection est dans le panneau des faces). */
    private int tabCount() {
        return (hasSquare() ? 1 : 0) + (config() != null ? 1 : 0) + (redstone ? 1 : 0) + (standard ? 1 : 0) + (hasStructureTab() ? 1 : 0);
    }

    /**
     * Lot 3 : onglet « Recettes » : icone de la recette verrouillee (et un cadenas), sinon le pictogramme de liste ; allume quand le carre
     * est ouvert. Clic : ouvre / ferme le carre. Lot 4 : onglet de 18, icone de 12.
     */
    private void recipeTab(GuiGraphics graphics, int mouseX, int mouseY, int y) {
        RecipeLockable rl = lockable();
        int tx = tabX();
        boolean on = squareShown();
        boolean hover = smallTab(graphics, mouseX, mouseY, y, on);
        RecipeChoice locked = rl == null ? null : rl.lockedChoice();
        if (locked != null && !locked.icon().isEmpty()) {
            //Objet a l'echelle de l'icone (12 px), au-dessus du fond de l'onglet, sous les infobulles (premier plan a z 300, l'objet ajoute 150)
            graphics.pose().pushPose();
            graphics.pose().translate(tx + ICON_X, y + ICON_Y, -140);
            graphics.pose().scale(ICON / 16F, ICON / 16F, 1F);
            graphics.renderItem(locked.icon(), 0, 0);
            graphics.pose().popPose();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 30);
            RecipeSquare.lock(graphics, tx + ICON_X + 8, y + ICON_Y + 7, RecipeSquare.LOCK);
            graphics.pose().popPose();
        } else {
            tabIcon(graphics, "recipes" + (on ? "_on" : ""), tx, y);
        }
        if (hover) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.pioneers.selector.tab"));
            if (locked != null) {
                tip.add(Component.translatable("gui.pioneers.selector.locked", locked.name()).withStyle(ChatFormatting.GREEN));
            } else {
                tip.add(Component.translatable("gui.pioneers.recipe_lock.none").withStyle(ChatFormatting.GRAY));
            }
            tip.add(Component.translatable(on ? "gui.pioneers.selector.tab_close" : "gui.pioneers.selector.tab_open",
                  PiKeys.RECIPE_SQUARE.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
            tooltip(tip);
        }
        click(tx, y, tab(), tab(), () -> setSquare(!squareShown()));
    }

    /** Taille des onglets : 18 sur l'interface standard v2 (lot 4, Guillaume : « reduis la taille des icones sur le cote »), 24 sinon. */
    private int tab() {
        return standard ? StandardLayout.TAB : TAB_LEGACY;
    }

    /** Lot 4 : icone des onglets de l'interface v2 (12 x 12, textures {@code tab_icon_<nom>} de tools/gc/core/gui_machine.py) et sa place. */
    private static final int ICON = 12, ICON_X = 2, ICON_Y = 3;

    /**
     * Lot 4 : cadre d'un onglet de l'interface v2 (18 x 18, accroche au bord droit du panneau), dessine net au pixel : bord sombre, acier,
     * filet clair en haut, liseré du tier au bord exterieur ; allume : fond et liseré a l'accent ; survol : eclairci. Rend le survol.
     */
    private boolean smallTab(GuiGraphics graphics, int mouseX, int mouseY, int y, boolean on) {
        int tx = tabX(), t = StandardLayout.TAB, a = accent();
        boolean hover = over(mouseX, mouseY, tx, y, t, t), light = light();
        graphics.fill(tx, y, tx + t, y + t, light ? 0xFF8C96A0 : 0xFF0E1216);
        graphics.fill(tx, y + 1, tx + t - 1, y + t - 1, on ? 0xFF000000 | GuiDraw.darken(a, light ? 0.8F : 0.38F)
              : light ? hover ? 0xFFF4F6F8 : 0xFFDDE2E7 : hover ? 0xFF323B46 : 0xFF262E37);
        graphics.fill(tx, y + 1, tx + t - 3, y + 2, on ? 0xFF000000 | GuiDraw.darken(a, light ? 1.1F : 0.6F) : light ? 0xFFFBFCFD : 0xFF3A4551);
        graphics.fill(tx + t - 3, y + 1, tx + t - 1, y + t - 1, 0xFF000000 | (on ? a : GuiDraw.darken(a, 0.45F)));
        return hover;
    }

    /** Lot 4 : icone de 12 d'un onglet de l'interface v2 (repli : un carre gris si la texture manque). */
    private static void tabIcon(GuiGraphics graphics, String name, int tx, int y) {
        //1.0.4-beta (lot 31) : onglets blancs de la salle blanche : pictogrammes clairs redessines a l'encre sombre (tab_icon_*_cleanroom)
        String own = "tab_icon_" + name + "_" + GuiSprites.LIGHT;
        if (lightStatic && GuiSprites.draw(graphics, own, tx + ICON_X, y + ICON_Y, ICON, ICON, ICON, ICON)) {
            return;
        }
        if (!GuiSprites.draw(graphics, "tab_icon_" + name, tx + ICON_X, y + ICON_Y, ICON, ICON, ICON, ICON)) {
            graphics.fill(tx + ICON_X + 2, y + ICON_Y + 2, tx + ICON_X + ICON - 2, y + ICON_Y + ICON - 2, 0xFF8A94A0);
        }
    }

    /** Bord des onglets : a droite du panneau (interface standard v2), sinon a gauche. */
    private int tabX() {
        return standard ? imageWidth : TAB_X;
    }

    private void tab(GuiGraphics graphics, int mouseX, int mouseY, int y, String name, boolean on, Runnable action, List<Component> tip) {
        int tx = tabX();
        boolean hover;
        if (standard) {
            //Lot 4 : onglet de 18 dessine net, icone de 12 (faces : cube allume ouvert ; redstone : son mode)
            hover = smallTab(graphics, mouseX, mouseY, y, on);
            tabIcon(graphics, name.substring("tab_".length()) + (on && name.equals("tab_sides") ? "_on" : ""), tx, y);
        } else {
            hover = over(mouseX, mouseY, tx, y, tab(), tab());
            if (!GuiSprites.draw(graphics, sprite(name + (on ? "_on" : "")), tx, y, tab(), tab(), TAB_LEGACY, TAB_LEGACY)) {
                graphics.fill(tx, y, tx + tab(), y + tab(), on ? 0xFF2F6F5A : 0xFF2A3540);
                graphics.renderOutline(tx, y, tab(), tab(), 0xFF3C4A57);
            }
            if (hover) {
                graphics.fill(tx + 3, y + 2, tx + tab() - 2, y + tab() - 2, 0x18FFFFFF);
            }
        }
        if (hover) {
            tooltip(tip);
        }
        click(tx, y, tab(), tab(), action);
    }

    /** Onglet Infos (lot 4 : onglet de 18, icone « i » de 12). */
    private void infoTab(GuiGraphics graphics, int mouseX, int mouseY, int y) {
        int tx = tabX();
        boolean hover = smallTab(graphics, mouseX, mouseY, y, infoOpen);
        tabIcon(graphics, "info", tx, y);
        if (hover) {
            tooltip(List.of(Component.translatable("gui.pioneers.api.info.tab"),
                  Component.translatable("gui.pioneers.api.info.tab_tip").withStyle(ChatFormatting.DARK_GRAY)));
        }
        click(tx, y, tab(), tab(), () -> {
            infoOpen = !infoOpen;
            structureOpen = false;
            sidesOpen = false;
            squareOpen = false;
        });
    }

    /** Les onglets et le carre sont hors du panneau : un clic dessus n'est pas un clic « dehors » (qui lacherait l'objet tenu). */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        double x = mouseX - left, y = mouseY - top;
        int tx = tabX();
        if (tabCount() > 0 && x >= tx && x < tx + tab() && y >= TAB_Y && y < TAB_Y + tabCount() * (tab() + 2)) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    /**
     * 0.19.13 : panneau des faces moderne (apercu valide : tools/gc/preview, maquette sides_mock) : carte sombre plate a liseré du tier,
     * en-tete (titre, ressource choisie, interrupteur d'ejection, tout fermer), patron du bloc en tuiles pleines colorees par mode (fleche et
     * nom de la face), legende, pastilles plates des ressources. Tout est dessine ici ; seuls les logos 12x12 sont des textures
     * ({@code side_icon_<ressource>}, tools/gc/core/gui_sides.py). Dessine par-dessus tout ({@link #drawOverlay}) : rien ne le traverse.
     */
    private static final int SIDES_X = 6, SIDES_Y = 16, SIDES_W = 164, SIDES_H = 69, PLATE = 16, CHIP = 17;
    private static final int CHIP_X = 82, CHIP_Y = 24, SWITCH_X = 127, SWITCH_Y = 3, CLEAR_X = 150, CLEAR_Y = 3;
    private static final int[][] CELLS = {{23, 34}, {6, 34}, {40, 34}, {57, 34}, {23, 17}, {23, 51}};   // ordre de RelativeSide
    private static final String[][] GLYPHS = {
          {".......", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "......."},                  // fermee
          {"...#...", "...#...", ".#.#.#.", "..###..", "...#...", ".......", "#######"},                  // entree
          {"...#...", "..###..", ".#.#.#.", "...#...", "...#...", ".......", "#######"},                  // sortie
          {".#...#.", "###..#.", ".#...#.", ".#..###", ".#...#.", ".......", "#######"}};                 // les deux

    /** Lot 4 : le panneau des faces est centre dans le panneau de l'interface v2 (plus large) ; a gauche sinon. */
    private int sidesX() {
        return standard ? (imageWidth - SIDES_W) / 2 : SIDES_X;
    }

    private static void rounded(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x + 1, y, x + w - 1, y + 1, color);
        graphics.fill(x, y + 1, x + w, y + h - 1, color);
        graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
    }

    private static int shade(int argb, float f) {
        int r = Math.min(255, Math.round((argb >> 16 & 255) * f)), g = Math.min(255, Math.round((argb >> 8 & 255) * f)), b = Math.min(255,
              Math.round((argb & 255) * f));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private void sidesView(GuiGraphics graphics, int mouseX, int mouseY) {
        SideConfig config = config();
        if (config == null) {
            sidesOpen = false;
            return;
        }
        int px = sidesX(), py = SIDES_Y;
        graphics.fill(px - 1, py - 1, px + SIDES_W + 1, py + SIDES_H + 1, 0x78000000);
        rounded(graphics, px, py, SIDES_W, SIDES_H, 0xFF2A3440);
        rounded(graphics, px + 1, py + 1, SIDES_W - 2, SIDES_H - 2, 0xFF121820);
        graphics.fill(px + 2, py + 1, px + SIDES_W - 2, py + 2, 0xFF000000 | accent());
        //Zone qui avale les clics (rien en dessous ne reagit)
        click(px, py, SIDES_W, SIDES_H, () -> {
        });
        int channels = config.channelCount();
        boolean typed = !config.types().isEmpty();
        sideChannel = typed ? Math.min(sideChannel, channels - 1) : 0;
        int channel = sideChannel;
        int sides = menu.sides(channel);
        SideType type = config.types().isEmpty() ? null : config.types().get(Math.min(channel, config.types().size() - 1));
        int famColor = type != null ? type.color : 0xFF37C48E;
        //En-tete : titre, ressource choisie
        Component head = Component.translatable("gui.pioneers.side.title");
        GuiDraw.text(graphics, font, head, px + 6, py + 4, 0.75F, TEXT, 40);
        if (type != null && typed) {
            GuiDraw.text(graphics, font, Component.literal("\u00b7  ").append(Component.translatable("gui.pioneers.side_type." + type.key())),
                  px + 8 + font.width(head) * 0.75F, py + 4, 0.75F, famColor, 58);
        }
        //Ejection automatique (interrupteur) et « tout fermer »
        boolean can = config.canEject(typed ? type : null);
        boolean eject = (sides >> 12 & 1) != 0;
        Component ej = Component.translatable("gui.pioneers.machine.eject_auto");
        GuiDraw.right(graphics, font, ej, px + SWITCH_X - 2, py + 5, 0.6F, can ? TEXT : DIM);
        int sx = px + SWITCH_X, sy = py + SWITCH_Y;
        if (!can) {
            rounded(graphics, sx, sy, 20, 10, 0xFF1E242C);
            rounded(graphics, sx + 1, sy + 1, 8, 8, 0xFF3A424C);
        } else {
            rounded(graphics, sx, sy, 20, 10, eject ? famColor | 0xFF000000 : 0xFF2A313A);
            rounded(graphics, eject ? sx + 11 : sx + 1, sy + 1, 8, 8, eject ? 0xFFF2F6FA : 0xFF8A94A0);
        }
        int ejectLeft = (int) (px + SWITCH_X - 2 - font.width(ej) * 0.6F);
        if (over(mouseX, mouseY, ejectLeft, py + 2, sx + 20 - ejectLeft, 12)) {
            tooltip(List.of(Component.translatable("gui.pioneers.machine.eject." + (can && eject ? "on" : "off")),
                  Component.translatable(can ? "gui.pioneers.machine.eject_auto_tip" : "gui.pioneers.machine.eject_unavailable")
                        .withStyle(ChatFormatting.DARK_GRAY)));
        }
        if (can) {
            int action = typed ? SideConfig.EJECT_TYPED + channel : MachineGui.EJECT;
            click(ejectLeft, py + 2, sx + 20 - ejectLeft, 12, () -> send(action));
        }
        int cx = px + CLEAR_X, cy = py + CLEAR_Y;
        boolean clearHover = over(mouseX, mouseY, cx, cy, 10, 10);
        rounded(graphics, cx, cy, 10, 10, clearHover ? 0xFF4A2A2E : 0xFF2A313A);
        GuiDraw.center(graphics, font, Component.literal("\u2716"), cx + 5, cy + 2.2F, 0.7F, 0xFFE07070, 9);
        if (clearHover) {
            tooltip(List.of(Component.translatable("gui.pioneers.machine.sides_clear"),
                  Component.translatable("gui.pioneers.machine.sides_clear_tip").withStyle(ChatFormatting.DARK_GRAY)));
        }
        int clearAction = SideConfig.CLEAR_TYPED + channel;
        click(cx, cy, 10, 10, () -> send(clearAction));
        graphics.fill(px + 4, py + 14, px + SIDES_W - 4, py + 15, 0xFF222A33);
        //Patron du bloc : tuiles pleines colorees par mode, fleche et nom de la face
        faceButtons.clear();
        for (RelativeSide side : RelativeSide.values()) {
            int x = px + CELLS[side.ordinal()][0], y = py + CELLS[side.ordinal()][1];
            faceButtons.add(new int[]{x, y, side.ordinal()});
            SideMode mode = SideMode.byIndex(sides >> side.ordinal() * 2 & 3);
            boolean locked = config.isLocked(side);
            boolean hover = over(mouseX, mouseY, x, y, PLATE, PLATE);
            if (mode == SideMode.NONE) {
                rounded(graphics, x, y, PLATE, PLATE, 0xFF1E242C);
                rounded(graphics, x + 1, y + 1, PLATE - 2, PLATE - 2, hover && !locked ? 0xFF303945 : 0xFF262D36);
            } else {
                rounded(graphics, x, y, PLATE, PLATE, shade(mode.color, 0.62F));
                rounded(graphics, x + 1, y + 1, PLATE - 2, PLATE - 2, hover && !locked ? shade(mode.color, 1.15F) : mode.color);
                graphics.fill(x + 2, y + 1, x + PLATE - 2, y + 2, shade(mode.color, 1.3F));
            }
            int glyph = locked ? 0xFF59636E : mode == SideMode.NONE ? 0xFF6A7480 : 0xFFFFFFFF;
            String[] rows = GLYPHS[mode.ordinal()];
            for (int j = 0; j < rows.length; j++) {
                for (int i = 0; i < rows[j].length(); i++) {
                    if (rows[j].charAt(i) == '#') {
                        graphics.fill(x + 8 + i, y + 8 + j, x + 9 + i, y + 9 + j, glyph);
                    }
                }
            }
            GuiDraw.text(graphics, font, Component.translatable("gui.pioneers.side.short." + side.name().toLowerCase(Locale.ROOT)), x + 2, y + 1.6F,
                  0.5F, mode == SideMode.NONE ? 0xFF8F99A4 : 0xFFFFFFFF, 12);
            if (locked) {
                for (int k = 0; k < PLATE; k += 3) {
                    graphics.fill(x + k, y + PLATE - 2, x + Math.min(PLATE, k + 1), y + PLATE - 1, 0xFF000000);
                }
            }
            if (hover) {
                graphics.renderOutline(x - 1, y - 1, PLATE + 2, PLATE + 2, locked ? 0xFF8A94A0 : 0xFFFFFFFF);
                List<Component> tip = new ArrayList<>();
                if (type != null && typed) {
                    tip.add(Component.translatable("gui.pioneers.side_type." + type.key()).withStyle(style -> style.withColor(type.color & 0xFFFFFF)));
                }
                tip.add(Component.translatable("gui.pioneers.side." + side.name().toLowerCase(Locale.ROOT)));
                tip.add(Component.translatable("gui.pioneers.side_mode." + mode.key()).withStyle(style -> style.withColor(mode.color & 0xFFFFFF)));
                tip.add(Component.translatable(locked ? "gui.pioneers.side.locked" : "gui.pioneers.machine.side_tip").withStyle(ChatFormatting.DARK_GRAY));
                tooltip(tip);
            }
            if (!locked) {
                int forward = typed ? SideConfig.TYPED + channel * 16 + side.ordinal() : side.ordinal();
                click(x, y, PLATE, PLATE, () -> send(forward));
            }
        }
        //Legende (a droite de la face du dessus)
        SideMode[] legend = {SideMode.INPUT, SideMode.OUTPUT, SideMode.IO, SideMode.NONE};
        for (int i = 0; i < legend.length; i++) {
            int lx = px + 42, ly = py + 18 + i * 4;
            graphics.fill(lx, ly, lx + 3, ly + 3, legend[i] == SideMode.NONE ? 0xFF4A525C : legend[i].color);
            GuiDraw.text(graphics, font, Component.translatable("gui.pioneers.side.legend." + legend[i].key()), lx + 5, ly - 0.4F, 0.45F, DIM, 32);
        }
        //Pastilles des ressources (4 par ligne)
        if (typed) {
            GuiDraw.text(graphics, font, Component.translatable("gui.pioneers.side.resource"), px + CHIP_X, py + 17.5F, 0.5F, DIM, 76);
            for (int i = 0; i < channels && i < 8; i++) {
                SideType t = config.types().get(i);
                int x = px + CHIP_X + i % 4 * (CHIP + 2), y = py + CHIP_Y + i / 4 * (CHIP + 3);
                boolean selected = i == channel;
                boolean hover = over(mouseX, mouseY, x, y, CHIP, CHIP);
                if (selected) {
                    rounded(graphics, x, y, CHIP, CHIP, t.color | 0xFF000000);
                    rounded(graphics, x + 1, y + 1, CHIP - 2, CHIP - 2, shade(t.color, 0.38F));
                } else {
                    rounded(graphics, x, y, CHIP, CHIP, hover ? 0xFF4A5664 : 0xFF2A3440);
                    rounded(graphics, x + 1, y + 1, CHIP - 2, CHIP - 2, 0xFF1A2027);
                }
                GuiSprites.draw(graphics, "side_icon_" + t.key(), x + 3, y + 3, 12, 12, 12, 12);
                if (!selected) {
                    graphics.fill(x + 3, y + 3, x + 15, y + 15, hover ? 0x66121820 : 0xA0121820);
                }
                if (hover) {
                    tooltip(List.of(Component.translatable("gui.pioneers.side_type." + t.key()).withStyle(style -> style.withColor(t.color & 0xFFFFFF)),
                          Component.translatable("gui.pioneers.machine.side_type_tip").withStyle(ChatFormatting.DARK_GRAY)));
                }
                int index = i;
                click(x, y, CHIP, CHIP, () -> sideChannel = index);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        //1.0.2-beta : molette sur un curseur : un cran
        Slider sl = sliderAt(mouseX, mouseY);
        if (sl != null && delta != 0) {
            int base = sentSetting == sl.setting() && net.minecraft.Util.getMillis() - sentAt < 1500 ? sentValue : sliderValue(sl.setting());
            sendSetting(sl.setting(), Math.max(sl.min(), Math.min(sl.max(), base + (delta > 0 ? 1 : -1))));
            return true;
        }
        if (squareShown()) {
            StandardLayout.Rect q = squareRect();
            double x = mouseX - leftPos, y = mouseY - topPos;
            if (x >= q.x() && x < q.x() + q.w() && y >= q.y() && y < q.y() + q.h()) {
                return square.scrolled(delta);
            }
        }
        if (picker.scrolled(delta)) {
            return true;
        }
        if (tile() != null && views.scrolled(tile(), delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Clic droit sur une face : reglage precedent ; clic droit sur la case « Recette » : le verrou est retire. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        //1.0.2-beta : clic sur un curseur : il est saisi (la valeur part au lacher)
        if (button == 0) {
            Slider sl = sliderAt(mouseX, mouseY);
            if (sl != null) {
                dragSetting = sl.setting();
                dragValue = sl.at(mouseX - leftPos);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        //1.0.4-beta (lot 3) : clic droit dans la vue du comptoir d'entrepot (grille, panier, recherche)
        if (button == 1 && !sidesOpen && !infoOpen && tile() != null && views.clicked(tile(), mouseX - leftPos, mouseY - topPos, button)) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        //API Machine (lot 2) : clic droit sur une recette du carre = favori
        if (button == 1 && squareShown() && square.rightClick()) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        RecipeLockable lockable = lockable();
        if (button == 1 && lockable != null && !picker.isOpen() && !sidesOpen && !squareShown()) {
            int[] p = lockable.recipeSlot();
            int x = leftPos + p[0], y = topPos + p[1];
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                if (lockable.lockedRecipe() != null) {
                    lockRecipe(null);
                }
                return true;
            }
        }
        if (sidesOpen && button == 1) {
            for (int[] face : faceButtons) {
                int x = leftPos + face[0], y = topPos + face[1];
                if (mouseX >= x && mouseX < x + PLATE && mouseY >= y && mouseY < y + PLATE) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    SideConfig config = config();
                    boolean typed = config != null && !config.types().isEmpty();
                    send(typed ? SideConfig.TYPED + sideChannel * 16 + 8 + face[2] : MachineGui.BACK + face[2]);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
