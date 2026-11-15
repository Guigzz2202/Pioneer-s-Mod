package fr.pioneers.client.farm.aquarium;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.core.GuiSprites;
import fr.pioneers.client.gui.core.PiMenuScreen;
import fr.pioneers.farm.aquarium.AquariumBlock;
import fr.pioneers.farm.aquarium.AquariumCompose;
import fr.pioneers.farm.aquarium.AquariumDecor;
import fr.pioneers.farm.aquarium.AquariumMenu;
import fr.pioneers.farm.aquarium.AquariumRules;
import fr.pioneers.farm.aquarium.AquariumTile;
import fr.pioneers.farm.aquarium.Aquariums;
import fr.pioneers.farm.aquarium.PacketAquarium;
import fr.pioneers.network.PiNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 25) : ecran de COMPOSITION d'un bassin d'aquarium (menu {@link AquariumMenu}), au dessin des interfaces standard v2
 * (panneau d'acier {@code panel_steel} en bandes, inventaire dans son cadre).
 * <ul>
 *     <li>en haut : l'etat du bassin (habitants, places, cases d'eau) ;</li>
 *     <li>a gauche : le FOND vu de dessus, nord en haut : une case du fond = 2 x 2 emplacements (leur nombre suit la taille du bassin), sol
 *     en texture du bloc (ou fond de verre), element en icone ; molette (Maj : en largeur) quand le fond ne tient pas ;</li>
 *     <li>a droite : la palette (Sol, Vegetation, Decor), le nombre d'objets que le joueur porte, la sorte choisie, son cout ;</li>
 *     <li>clic sur un emplacement : poser la sorte choisie (un objet pris dans l'inventaire, l'ancien rendu) ; clic droit : retirer
 *     l'element, sinon le sol (objet rendu). Le serveur revalide tout ({@link AquariumCompose#edit}).</li>
 * </ul>
 * Maquette fidele : {@code tools/aquarium/aquarium_gui.py} (memes cotes, lues dans {@link AquariumMenu}).
 */
public class AquariumScreen extends PiMenuScreen<AquariumMenu> {

    /** Couleurs : eau du cadre, fond de verre, case hors du fond, lignes des cases et des emplacements, survol. */
    public static final int FRAME_BG = 0xFF0B1A24, GLASS = 0xFF2A5468, VOID = 0xFF070B0F, CELL_LINE = 0xFF5C6E7A, SLOT_LINE = 0x40000000,
          HOVER = 0xFFFFFFFF, CHOSEN = 0xFF3FD0A0;
    /** Taille d'un emplacement dans la grille (px d'interface), bornes ; onglets. */
    public static final int SP_MIN = 6, SP_MAX = 24, TAB_H = 12, INFO_Y = 19, DESC_Y = 88, STATUS_Y = 123, HELP_Y = 136;
    /** Interligne des petits textes (px). */
    public static final float LINE = 5.5F;
    public static final AquariumDecor.Group[] TABS = AquariumDecor.Group.values();

    private AquariumDecor.Group tab = AquariumDecor.Group.SOL;
    private AquariumDecor.Kind chosen;
    private Component status;
    private int statusColor = TEXT, statusTicks;
    private int sx, sz, refresh;
    /** Fond : cases du fond par colonne (x, z) ; bornes. */
    private final Map<Long, BlockPos> floor = new HashMap<>();
    private int minX, minZ, nx = 1, nz = 1;
    private int dwellers, used, capacity, wet, cells;

    public AquariumScreen(AquariumMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AquariumMenu.W, AquariumMenu.H);
        titleLabelX = 8;
        titleLabelY = 7;
        inventoryLabelY = -1;
    }

    @Override
    protected void init() {
        super.init();
        scan();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (++refresh % 10 == 0) {
            scan();
        }
        if (statusTicks > 0 && --statusTicks == 0) {
            status = null;
        }
    }

    /** Releve du bassin (client : tuiles synchronisees). */
    private void scan() {
        Level level = minecraft == null ? null : minecraft.level;
        if (level == null) {
            return;
        }
        List<BlockPos> tank = Aquariums.tank(level, menu.pos());
        floor.clear();
        dwellers = 0;
        wet = Aquariums.wetCells(level, tank);
        used = Aquariums.usedPlaces(level, tank);
        capacity = AquariumRules.capacity(wet);
        cells = tank.size();
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : tank) {
            if (level.getBlockEntity(p) instanceof AquariumTile t) {
                dwellers += t.dwellers().size();
            }
        }
        for (BlockPos p : AquariumCompose.floorCells(level, tank)) {
            long k = key(p.getX(), p.getZ());
            BlockPos old = floor.get(k);
            if (old == null || p.getY() < old.getY()) {
                floor.put(k, p);
            }
            x0 = Math.min(x0, p.getX());
            x1 = Math.max(x1, p.getX());
            z0 = Math.min(z0, p.getZ());
            z1 = Math.max(z1, p.getZ());
        }
        if (!floor.isEmpty()) {
            minX = x0;
            minZ = z0;
            nx = x1 - x0 + 1;
            nz = z1 - z0 + 1;
        }
        sx = Math.max(0, Math.min(sx, 2 * nx - cols()));
        sz = Math.max(0, Math.min(sz, 2 * nz - rows()));
    }

    static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** Taille d'un emplacement dans la grille (px) : le fond entier si possible, bornee. */
    public static int slotPx(int nx, int nz) {
        return Math.max(SP_MIN, Math.min(SP_MAX, Math.min(AquariumMenu.GRID_W / (2 * nx), AquariumMenu.GRID_H / (2 * nz))));
    }

    private int sp() {
        return slotPx(nx, nz);
    }

    private int cols() {
        return Math.min(2 * nx, AquariumMenu.GRID_W / sp());
    }

    private int rows() {
        return Math.min(2 * nz, AquariumMenu.GRID_H / sp());
    }

    private int ox() {
        return AquariumMenu.GRID_X + (AquariumMenu.GRID_W - cols() * sp()) / 2;
    }

    private int oz() {
        return AquariumMenu.GRID_Y + (AquariumMenu.GRID_H - rows() * sp()) / 2;
    }

    /** Emplacement sous la souris (relative au panneau) : {colonne absolue, rangee absolue} ou null. */
    private int[] slotAt(double mx, double my) {
        int sp = sp();
        int i = (int) Math.floor((mx - ox()) / sp), j = (int) Math.floor((my - oz()) / sp);
        if (mx < ox() || my < oz() || i < 0 || j < 0 || i >= cols() || j >= rows()) {
            return null;
        }
        return new int[]{sx + i, sz + j};
    }

    /** Case du fond d'un emplacement de la grille (null : hors du fond). */
    private BlockPos cellOf(int[] s) {
        return floor.get(key(minX + s[0] / 2, minZ + s[1] / 2));
    }

    private static int slotIndex(int[] s) {
        return (s[0] & 1) + 2 * (s[1] & 1);
    }

    private AquariumTile tileAt(BlockPos p) {
        return p != null && minecraft.level != null && minecraft.level.getBlockEntity(p) instanceof AquariumTile t ? t : null;
    }

    // ------------------------------------------------------------------ dessin

    @Override
    protected void drawPanel(GuiGraphics g, int x, int y) {
        if (!GuiSprites.bands(g, "panel_steel", x, y, imageWidth, StandardLayout.PANEL_TEX, StandardLayout.PANEL_TEX, 8, 8,
              StandardLayout.panelBands(AquariumMenu.ZONE, imageHeight))) {
            super.drawPanel(g, x, y);
        }
        StandardLayout.Rect f = StandardLayout.inventoryFrame(imageWidth, AquariumMenu.ZONE);
        int fx0 = x + f.x(), fy0 = y + f.y(), fx1 = fx0 + f.w(), fy1 = fy0 + f.h();
        g.fill(fx0, fy0, fx1, fy1, 0xFF0E141A);
        g.fill(fx0, fy0, fx1, fy0 + 1, 0xFF06090C);
        g.fill(fx0, fy1 - 1, fx1, fy1, 0xFF34404C);
        g.fill(fx1 - 1, fy0, fx1, fy1, 0xFF34404C);
    }

    @Override
    protected void drawSlot(GuiGraphics g, Slot slot, int x, int y) {
        if (!GuiSprites.draw(g, "slot", x - 1, y - 1, 18, 18, 18, 18)) {
            super.drawSlot(g, slot, x, y);
        }
    }

    @Override
    protected void drawBackground(GuiGraphics g, int mouseX, int mouseY) {
        int gx = AquariumMenu.GRID_X, gy = AquariumMenu.GRID_Y, gw = AquariumMenu.GRID_W, gh = AquariumMenu.GRID_H;
        //cadre de la grille : acier, eau sombre
        g.fill(gx - 2, gy - 2, gx + gw + 2, gy + gh + 2, 0xFF2C3036);
        g.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, 0xFF4C535C);
        g.fill(gx, gy, gx + gw, gy + gh, FRAME_BG);
        int sp = sp(), ox = ox(), oz = oz();
        int[] hover = slotAt(mouseX, mouseY);
        for (int j = 0; j < rows(); j++) {
            for (int i = 0; i < cols(); i++) {
                int[] s = {sx + i, sz + j};
                int x = ox + i * sp, y = oz + j * sp;
                AquariumTile t = tileAt(cellOf(s));
                if (t == null) {
                    g.fill(x, y, x + sp, y + sp, VOID);
                    continue;
                }
                AquariumDecor.Kind gr = t.ground(slotIndex(s));
                if (gr != null) {
                    TextureAtlasSprite tex = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(new ResourceLocation(gr.texture()));
                    g.blit(x, y, 0, sp, sp, tex);
                } else {
                    g.fill(x, y, x + sp, y + sp, GLASS);
                    g.fill(x + 1, y + 1, x + 2, y + sp - 1, 0x30FFFFFF);
                }
                g.fill(x + sp - 1, y, x + sp, y + sp, SLOT_LINE);
                g.fill(x, y + sp - 1, x + sp, y + sp, SLOT_LINE);
            }
        }
        //lignes des cases (toutes les 2 colonnes / rangees), puis elements par-dessus
        for (int i = 0; i <= cols(); i++) {
            if ((sx + i) % 2 == 0) {
                g.fill(ox + i * sp, oz, ox + i * sp + 1, oz + rows() * sp, CELL_LINE);
            }
        }
        for (int j = 0; j <= rows(); j++) {
            if ((sz + j) % 2 == 0) {
                g.fill(ox, oz + j * sp, ox + cols() * sp, oz + j * sp + 1, CELL_LINE);
            }
        }
        for (int j = 0; j < rows(); j++) {
            for (int i = 0; i < cols(); i++) {
                int[] s = {sx + i, sz + j};
                AquariumTile t = tileAt(cellOf(s));
                AquariumDecor.Kind pc = t == null ? null : t.piece(slotIndex(s));
                if (pc != null) {
                    icon(g, AquariumTile.itemOf(pc), ox + i * sp + 1, oz + j * sp + 1, sp - 2);
                }
            }
        }
        if (hover != null && cellOf(hover) != null) {
            int x = ox + (hover[0] - sx) * sp, y = oz + (hover[1] - sz) * sp;
            g.renderOutline(x, y, sp, sp, HOVER);
        }
        //onglets de la palette
        int px = AquariumMenu.PAL_X, py = AquariumMenu.PAL_Y, tw = AquariumMenu.PAL_W / TABS.length;
        for (int k = 0; k < TABS.length; k++) {
            boolean on = TABS[k] == tab;
            int x = px + k * tw;
            g.fill(x, py, x + tw - 1, py + TAB_H, on ? 0xFF3A4C5C : 0xFF1C242C);
            g.renderOutline(x, py, tw - 1, TAB_H, on ? CHOSEN : 0xFF3C4A57);
        }
        //cases de la palette
        List<AquariumDecor.Kind> kinds = kinds(tab);
        for (int k = 0; k < kinds.size(); k++) {
            int x = px + (k % AquariumMenu.COLS) * AquariumMenu.CELL + 2, y = py + TAB_H + 4 + (k / AquariumMenu.COLS) * AquariumMenu.CELL;
            PiMenuScreen.slot(g, x, y);
            if (kinds.get(k) == chosen) {
                g.renderOutline(x - 1, y - 1, 18, 18, CHOSEN);
            }
        }
    }

    /** Icone d'un objet a la taille {@code size} (px). */
    private void icon(GuiGraphics g, ItemStack st, int x, int y, int size) {
        if (st.isEmpty()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size / 16F, size / 16F, 1F);
        g.renderItem(st, 0, 0);
        g.pose().popPose();
    }

    /** Sortes d'un groupe, dans l'ordre de {@link AquariumDecor#KINDS}. */
    public static List<AquariumDecor.Kind> kinds(AquariumDecor.Group group) {
        List<AquariumDecor.Kind> out = new ArrayList<>();
        for (AquariumDecor.Kind k : AquariumDecor.KINDS) {
            if (k.group() == group) {
                out.add(k);
            }
        }
        return out;
    }

    private boolean creative() {
        return minecraft.player != null && minecraft.player.getAbilities().instabuild;
    }

    @Override
    protected void drawForeground(GuiGraphics g, int mouseX, int mouseY) {
        GuiDraw.text(g, font, Component.translatable("aquarium.pioneers.info", dwellers, used, capacity, wet, cells), 8, INFO_Y, 0.6F, DIM, imageWidth - 16);
        //sous la grille : emplacements et cases du fond, le nord (en haut de la grille) a droite
        GuiDraw.text(g, font, Component.translatable("gui.pioneers.aquarium.floor", floor.size() * AquariumDecor.SLOTS, floor.size()), AquariumMenu.GRID_X,
              AquariumMenu.GRID_Y + AquariumMenu.GRID_H + 5, 0.55F, DIM, AquariumMenu.GRID_W - 22);
        GuiDraw.right(g, font, Component.translatable("gui.pioneers.aquarium.north"), AquariumMenu.GRID_X + AquariumMenu.GRID_W,
              AquariumMenu.GRID_Y + AquariumMenu.GRID_H + 5, 0.55F, 0xFFB8D8E8);
        int px = AquariumMenu.PAL_X, py = AquariumMenu.PAL_Y, tw = AquariumMenu.PAL_W / TABS.length;
        for (int k = 0; k < TABS.length; k++) {
            AquariumDecor.Group gr = TABS[k];
            GuiDraw.center(g, font, Component.translatable("gui.pioneers.aquarium.tab." + gr.name().toLowerCase(java.util.Locale.ROOT)), px + k * tw + (tw - 1) / 2F,
                  py + 3, 0.55F, gr == tab ? TEXT : DIM, tw - 3);
            click(px + k * tw, py, tw - 1, TAB_H, () -> tab = gr);
        }
        List<AquariumDecor.Kind> kinds = kinds(tab);
        Inventory inv = minecraft.player.getInventory();
        for (int k = 0; k < kinds.size(); k++) {
            AquariumDecor.Kind kind = kinds.get(k);
            int x = px + (k % AquariumMenu.COLS) * AquariumMenu.CELL + 2, y = py + TAB_H + 4 + (k / AquariumMenu.COLS) * AquariumMenu.CELL;
            ItemStack st = AquariumTile.itemOf(kind);
            int n = AquariumCompose.count(minecraft.player, kind);
            g.renderItem(st, x, y);
            //au-dessus de l'icone (les objets sont dessines 150 plus haut)
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            if (!creative() && n == 0) {
                g.fill(x, y, x + 16, y + 16, 0xA0101418);
            }
            String count = creative() ? "∞" : n > 999 ? "999+" : String.valueOf(n);
            GuiDraw.right(g, font, Component.literal(count), x + 17, y + 11, 0.5F, n > 0 || creative() ? TEXT : 0xFFE05A4A);
            g.pose().popPose();
            click(x - 1, y - 1, 18, 18, () -> chosen = kind);
        }
        if (inv == null) {
            return;
        }
        //sorte choisie : nom, cout, conseil
        if (chosen == null) {
            GuiDraw.wrapped(g, font, Component.translatable("gui.pioneers.aquarium.choose"), px, DESC_Y, 0.55F, DIM, AquariumMenu.PAL_W, 3, LINE);
        } else {
            GuiDraw.text(g, font, name(chosen), px, DESC_Y, 0.65F, TEXT, AquariumMenu.PAL_W);
            ItemStack st = AquariumTile.itemOf(chosen);
            GuiDraw.text(g, font, Component.translatable("gui.pioneers.aquarium.cost", st.getHoverName(), creative() ? "∞"
                  : String.valueOf(AquariumCompose.count(minecraft.player, chosen))), px, DESC_Y + 9, 0.5F, DIM, AquariumMenu.PAL_W);
            GuiDraw.wrapped(g, font, hint(chosen), px, DESC_Y + 17, 0.5F, DIM, AquariumMenu.PAL_W, 3, LINE);
        }
        if (status != null) {
            GuiDraw.wrapped(g, font, status, px, STATUS_Y, 0.55F, statusColor, AquariumMenu.PAL_W, 2, 6F);
        }
        GuiDraw.wrapped(g, font, Component.translatable("gui.pioneers.aquarium.help"), px, HELP_Y, 0.5F, DIM, AquariumMenu.PAL_W, 2, LINE);
        //infobulles
        int[] hover = slotAt(mouseX, mouseY);
        AquariumTile t = hover == null ? null : tileAt(cellOf(hover));
        if (t != null && menu.getCarried().isEmpty()) {
            int s = slotIndex(hover);
            List<Component> tip = new ArrayList<>();
            tip.add(t.piece(s) != null ? name(t.piece(s)) : Component.translatable("gui.pioneers.aquarium.no_piece"));
            tip.add(Component.translatable("gui.pioneers.aquarium.ground_is", t.ground(s) != null ? name(t.ground(s))
                  : Component.translatable("gui.pioneers.aquarium.glass_floor")).withStyle(ChatFormatting.GRAY));
            tooltip(tip);
        } else {
            for (int k = 0; k < kinds.size(); k++) {
                int x = px + (k % AquariumMenu.COLS) * AquariumMenu.CELL + 1, y = py + TAB_H + 3 + (k / AquariumMenu.COLS) * AquariumMenu.CELL;
                if (over(mouseX, mouseY, x, y, 18, 18)) {
                    tooltip(List.of(name(kinds.get(k)), hint(kinds.get(k)).copy().withStyle(ChatFormatting.GRAY)));
                }
            }
        }
    }

    static Component name(AquariumDecor.Kind k) {
        return Component.translatable("aquarium.pioneers.decor." + k.key());
    }

    /** Conseil d'une sorte : sol, plante enracinee, varech, nenuphar, decor. */
    static Component hint(AquariumDecor.Kind k) {
        String h = k.ground() ? "ground" : k.floating() ? "floating" : "kelp".equals(k.key()) ? "kelp" : "sea_pickle".equals(k.key()) ? "glow"
                   : k.rooted() ? "rooted" : "chest".equals(k.key()) ? "chest" : "decor";
        return Component.translatable("aquarium.pioneers.decor.hint." + h);
    }

    // ------------------------------------------------------------------ gestes

    private void say(Component text, int color) {
        status = text;
        statusColor = color;
        statusTicks = 80;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int[] s = slotAt(mouseX - leftPos, mouseY - topPos);
        if (s != null && (button == 0 || button == 1)) {
            BlockPos cell = cellOf(s);
            AquariumTile t = tileAt(cell);
            if (t != null) {
                edit(cell, t, slotIndex(s), button == 1);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Pose (clic) ou retrait (clic droit) : verification locale (message tout de suite), puis demande au serveur. */
    private void edit(BlockPos cell, AquariumTile t, int slot, boolean remove) {
        if (remove) {
            int layer = t.piece(slot) != null ? 1 : t.ground(slot) != null ? 0 : -1;
            if (layer >= 0) {
                AquariumDecor.Kind old = layer == 1 ? t.piece(slot) : t.ground(slot);
                PiNetwork.sendToServer(new PacketAquarium(menu.pos(), cell, slot, layer, -1));
                say(Component.translatable("gui.pioneers.aquarium.removed", name(old)), 0xFF9FD8F0);
            }
            return;
        }
        if (chosen == null) {
            say(Component.translatable("gui.pioneers.aquarium.choose"), DIM);
            return;
        }
        String why = AquariumDecor.refusal(chosen, t.ground(slot));
        if (why != null) {
            say(Component.translatable("aquarium.pioneers.decor.refuse." + why, AquariumTile.itemOf(chosen).getHoverName()), 0xFFE05A4A);
            return;
        }
        if ((chosen.ground() ? t.ground(slot) : t.piece(slot)) == chosen) {
            return;
        }
        if (!creative() && AquariumCompose.count(minecraft.player, chosen) == 0) {
            say(Component.translatable("aquarium.pioneers.decor.refuse.need", AquariumTile.itemOf(chosen).getHoverName()), 0xFFE05A4A);
            return;
        }
        PiNetwork.sendToServer(new PacketAquarium(menu.pos(), cell, slot, chosen.ground() ? 0 : 1, AquariumDecor.index(chosen)));
        say(Component.translatable("gui.pioneers.aquarium.placed", name(chosen)), CHOSEN);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double mx = mouseX - leftPos, my = mouseY - topPos;
        if (mx >= AquariumMenu.GRID_X && mx < AquariumMenu.GRID_X + AquariumMenu.GRID_W && my >= AquariumMenu.GRID_Y
            && my < AquariumMenu.GRID_Y + AquariumMenu.GRID_H) {
            int step = delta > 0 ? -2 : 2;
            if (Screen.hasShiftDown()) {
                sx = Math.max(0, Math.min(2 * nx - cols(), sx + step));
            } else {
                sz = Math.max(0, Math.min(2 * nz - rows(), sz + step));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (minecraft != null && minecraft.level != null && !(minecraft.level.getBlockState(menu.pos()).getBlock() instanceof AquariumBlock)) {
            onClose();
            return;
        }
        super.render(g, mouseX, mouseY, partial);
    }
}
