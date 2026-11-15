package fr.pioneers.client.forge;

import com.mojang.blaze3d.platform.InputConstants;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.TooltipLayer;
import fr.pioneers.forge.ForgeMetals;
import fr.pioneers.forge.HeatedMetal;
import fr.pioneers.forge.SmithingGrid;
import fr.pioneers.forge.SmithingRecipe;
import fr.pioneers.forge.smithing.AnvilTile;
import fr.pioneers.forge.smithing.SmithingMoves;
import fr.pioneers.network.PacketTileAction;
import fr.pioneers.network.PiNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Ecran de forge (minijeu de l'enclume, spec forge primaire par. 2) : a gauche les formes possibles (metal de la piece, tier de l'enclume), au
 * centre la grille 16 x 16 vue de dessus (forme visee en fantome : cases a remplir en contour clair, cases en trop en rouge), a droite la
 * direction des coups (molette, fleches du clavier ou clic sur la rose) et la temperature. Clic gauche = frapper, Maj + clic = etirer, clic
 * droit = couper. Le serveur seul applique les coups ({@link AnvilTile#handleAction}) ; l'ecran relit la piece a chaque mise a jour.
 * <p>
 * Coordonnees (panneau) : MEMES CHIFFRES que la maquette {@code tools/gc/forge/smithing.py minigame_preview}.
 */
public class SmithingScreen extends Screen {

    public static final int W = 300, H = 212;
    public static final int LIST_X = 8, LIST_Y = 22, LIST_COLS = 4, LIST_CELL = 18, LIST_ROWS = 8;
    public static final int GRID_X = 90, GRID_Y = 22, PX = 10;
    public static final int ROSE_X = 258, ROSE_Y = 22, ROSE = 34;
    public static final int GAUGE_X = 269, GAUGE_Y = 64, GAUGE_W = 12, GAUGE_H = 104;
    public static final int INFO_Y = 188, STATUS_Y = 199;
    public static final int GAUGE_MAX_C = 1600;

    private static final int BG_A = 0xFF23282E, BG_B = 0xFF262C33, GHOST = 0xFF3A424C, GHOST_EDGE = 0xFFB4C0CC, EXCESS = 0xFFD8443C;
    private static final String[] ARROWS = {"\u25B2", "\u25B6", "\u25BC", "\u25C0"};

    private final BlockPos pos;
    private int left, top;
    private int dir = SmithingMoves.RIGHT;
    private int scroll;
    // Caches relus quand la piece change (version de la tuile)
    private int seen = -1;
    private String metal = "";
    private final List<SmithingRecipe> recipes = new ArrayList<>();
    private final List<ItemStack> icons = new ArrayList<>();
    @Nullable
    private SmithingRecipe current;
    @Nullable
    private SmithingGrid target;
    private int budget = -1;

    public SmithingScreen(BlockPos pos) {
        super(Component.translatable("gui.pioneers.smithing.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Nullable
    private AnvilTile tile() {
        Level level = minecraft == null ? null : minecraft.level;
        return level != null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof AnvilTile t ? t : null;
    }

    @Override
    public void tick() {
        AnvilTile tile = tile();
        if (tile == null || tile.piece().isEmpty() || minecraft.player == null
              || minecraft.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) {
            onClose();
        }
    }

    /** Relit la liste des formes et la forme visee quand la piece a change. */
    private void refresh(AnvilTile tile) {
        if (tile.version() == seen) {
            return;
        }
        seen = tile.version();
        ItemStack piece = tile.piece();
        String m = HeatedMetal.metal(piece);
        if (!m.equals(metal) || recipes.isEmpty()) {
            metal = m;
            recipes.clear();
            icons.clear();
            recipes.addAll(SmithingRecipe.forMetal(tile.getLevel(), metal, tile.tier()));
            for (SmithingRecipe r : recipes) {
                icons.add(r.result(metal));
            }
        }
        ResourceLocation id = tile.shownRecipe();
        current = null;
        for (SmithingRecipe r : recipes) {
            if (r.getId().equals(id)) {
                current = r;
            }
        }
        target = current == null ? null : current.target();
        budget = current == null ? -1 : AnvilTile.budget(current, AnvilTile.ingots(piece));
    }

    private void send(int action) {
        PiNetwork.CHANNEL.sendToServer(new PacketTileAction(pos, action));
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        AnvilTile tile = tile();
        if (tile == null) {
            return;
        }
        refresh(tile);
        ItemStack piece = tile.piece();
        float temp = HeatedMetal.temperatureNow(piece, tile.getLevel());
        int working = ForgeMetals.workingC(metal);
        boolean workable = temp >= working;
        GuiDraw.panel(g, left, top, W, H);
        Component name = tile.getBlockState().getBlock().getName().copy().append(" \u00B7 ").append(piece.getHoverName());
        GuiDraw.text(g, font, name, left + 8, top + 7, 1F, 0xFFE8E2D6, W - 16);
        drawList(g, mouseX, mouseY);
        drawGrid(g, tile, piece, temp, mouseX, mouseY);
        drawRose(g, mouseX, mouseY);
        drawGauge(g, temp, working);
        drawInfo(g, tile, piece, temp, workable);
        drawTooltips(g, mouseX, mouseY);
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        int x0 = left + LIST_X, y0 = top + LIST_Y;
        GuiDraw.screen(g, x0 - 2, y0 - 2, LIST_COLS * LIST_CELL + 4, LIST_ROWS * LIST_CELL + 4);
        for (int i = 0; i < LIST_COLS * LIST_ROWS; i++) {
            int k = i + scroll * LIST_COLS;
            if (k >= recipes.size()) {
                break;
            }
            int x = x0 + i % LIST_COLS * LIST_CELL, y = y0 + i / LIST_COLS * LIST_CELL;
            boolean sel = recipes.get(k) == current;
            boolean hover = mx >= x && mx < x + LIST_CELL && my >= y && my < y + LIST_CELL;
            g.fill(x, y, x + LIST_CELL, y + LIST_CELL, sel ? 0xFF5A4A26 : hover ? 0xFF2E3A44 : 0xFF161C22);
            if (sel) {
                g.renderOutline(x, y, LIST_CELL, LIST_CELL, 0xFFE8B84A);
            }
            g.renderItem(icons.get(k), x + 1, y + 1);
        }
        int rows = (recipes.size() + LIST_COLS - 1) / LIST_COLS;
        if (rows > LIST_ROWS) {
            int h = LIST_ROWS * LIST_CELL;
            int bar = Math.max(8, h * LIST_ROWS / rows), at = (h - bar) * scroll / Math.max(1, rows - LIST_ROWS);
            g.fill(x0 + LIST_COLS * LIST_CELL + 3, y0 + at, x0 + LIST_COLS * LIST_CELL + 5, y0 + at + bar, 0xFF5C6A76);
        }
        if (recipes.isEmpty()) {
            GuiDraw.wrapped(g, font, Component.translatable("gui.pioneers.smithing.no_recipe"), x0, y0 + 4, 0.75F, 0xFF9AA4AE,
                  LIST_COLS * LIST_CELL, 6, 8F);
        }
        if (current != null) {
            ItemStack out = current.result(metal);
            GuiDraw.wrapped(g, font, out.getHoverName(), x0, top + LIST_Y + LIST_ROWS * LIST_CELL + 6, 0.75F, 0xFFE8E2D6, LIST_COLS * LIST_CELL, 2, 8F);
            GuiDraw.text(g, font, Component.translatable("gui.pioneers.smithing.ingots", current.ingots(), current.pixels()), x0,
                  top + LIST_Y + LIST_ROWS * LIST_CELL + 22, 0.7F, 0xFF9AA4AE, LIST_COLS * LIST_CELL);
        }
    }

    private void drawGrid(GuiGraphics g, AnvilTile tile, ItemStack piece, float temp, int mx, int my) {
        int x0 = left + GRID_X, y0 = top + GRID_Y, size = SmithingGrid.SIZE * PX;
        g.fill(x0 - 2, y0 - 2, x0 + size + 2, y0 + size + 2, 0xFF0B1016);
        g.renderOutline(x0 - 2, y0 - 2, size + 4, size + 4, 0xFF2E3A44);
        SmithingGrid grid = tile.shownGrid();
        int rgb = ForgeMetals.tint(metal, temp);
        int hi = 0xFF000000 | brighten(rgb, 1.25F), lo = 0xFF000000 | GuiDraw.darken(rgb, 0.6F), mid = 0xFF000000 | rgb;
        for (int z = 0; z < SmithingGrid.SIZE; z++) {
            for (int x = 0; x < SmithingGrid.SIZE; x++) {
                int cx = x0 + x * PX, cy = y0 + z * PX;
                boolean full = grid != null && grid.get(x, z), want = target != null && target.get(x, z);
                if (full) {
                    g.fill(cx, cy, cx + PX, cy + PX, mid);
                    g.fill(cx, cy, cx + PX, cy + 1, hi);
                    g.fill(cx, cy, cx + 1, cy + PX, hi);
                    g.fill(cx, cy + PX - 1, cx + PX, cy + PX, lo);
                    g.fill(cx + PX - 1, cy, cx + PX, cy + PX, lo);
                    if (target != null && !want) {
                        g.fill(cx + 1, cy + 1, cx + PX - 1, cy + PX - 1, 0x70D8443C);
                        g.renderOutline(cx, cy, PX, PX, EXCESS);
                    }
                } else {
                    g.fill(cx, cy, cx + PX, cy + PX, (x + z & 1) == 0 ? BG_A : BG_B);
                    if (want) {
                        g.fill(cx + 1, cy + 1, cx + PX - 1, cy + PX - 1, GHOST);
                        g.renderOutline(cx + 1, cy + 1, PX - 2, PX - 2, GHOST_EDGE);
                    }
                }
            }
        }
        //Case survolee : cadre blanc, fleche de la direction et case d'arrivee du coup
        int hx = (mx - x0) / PX, hz = (my - y0) / PX;
        if (grid != null && mx >= x0 && my >= y0 && hx < SmithingGrid.SIZE && hz < SmithingGrid.SIZE) {
            int cx = x0 + hx * PX, cy = y0 + hz * PX;
            g.renderOutline(cx - 1, cy - 1, PX + 2, PX + 2, 0xFFFFFFFF);
            if (grid.get(hx, hz)) {
                int[] dest = destination(grid, hx, hz, hasShiftDown());
                if (dest != null) {
                    g.renderOutline(x0 + dest[0] * PX, y0 + dest[1] * PX, PX, PX, 0xFFFFD060);
                }
                g.drawString(font, ARROWS[dir], cx + 2, cy + 1, 0xFF101010, false);
            }
        }
    }

    /** Case ou partira le pixel (apercu du coup) ; null si le coup ne fait rien. */
    @Nullable
    private int[] destination(SmithingGrid grid, int x, int z, boolean draw) {
        SmithingGrid copy = grid.copy();
        boolean ok = draw ? SmithingMoves.draw(copy, x, z, dir) : SmithingMoves.strike(copy, x, z, dir);
        if (!ok) {
            return null;
        }
        for (int zz = 0; zz < SmithingGrid.SIZE; zz++) {
            for (int xx = 0; xx < SmithingGrid.SIZE; xx++) {
                if (copy.get(xx, zz) && !grid.get(xx, zz)) {
                    return new int[]{xx, zz};
                }
            }
        }
        return null;
    }

    private void drawRose(GuiGraphics g, int mx, int my) {
        int x0 = left + ROSE_X, y0 = top + ROSE_Y, c = ROSE / 2;
        GuiDraw.screen(g, x0, y0, ROSE, ROSE);
        int[][] at = {{c, 6}, {ROSE - 6, c}, {c, ROSE - 6}, {6, c}};
        for (int d = 0; d < 4; d++) {
            int col = d == dir ? 0xFFFFD060 : 0xFF5C6A76;
            g.drawCenteredString(font, ARROWS[d], x0 + at[d][0], y0 + at[d][1] - 4, col);
        }
        g.fill(x0 + c - 2, y0 + c - 2, x0 + c + 2, y0 + c + 2, 0xFF9AA4AE);
        GuiDraw.center(g, font, Component.translatable("gui.pioneers.smithing.direction"), x0 + c, y0 + ROSE + 3, 0.6F, 0xFF9AA4AE, ROSE + 6);
    }

    private void drawGauge(GuiGraphics g, float temp, int working) {
        int x0 = left + GAUGE_X, y0 = top + GAUGE_Y;
        GuiDraw.screen(g, x0, y0, GAUGE_W, GAUGE_H);
        int inner = GAUGE_H - 2;
        //Echelle coloree a sa temperature, remplie jusqu'a la temperature de la piece
        for (int i = 0; i < inner; i++) {
            float cellC = (i + 0.5F) * GAUGE_MAX_C / inner;
            int y = y0 + GAUGE_H - 2 - i;
            int col;
            if (cellC <= temp) {
                int glow = ForgeMetals.glow(cellC);
                col = glow == 0 ? 0xFF6A6A6A : 0xFF000000 | glow & 0xFFFFFF;
            } else {
                col = 0xFF161C22;
            }
            g.fill(x0 + 1, y, x0 + GAUGE_W - 1, y + 1, col);
        }
        int wy = y0 + GAUGE_H - 2 - Math.min(inner - 1, working * inner / GAUGE_MAX_C);
        g.fill(x0 - 3, wy, x0 + GAUGE_W + 3, wy + 1, 0xFFFFFFFF);
        GuiDraw.center(g, font, Component.literal(Math.round(temp) + "\u00B0C"), x0 + GAUGE_W / 2F, y0 + GAUGE_H + 4, 0.75F, 0xFFE8E2D6, 40);
    }

    private void drawInfo(GuiGraphics g, AnvilTile tile, ItemStack piece, float temp, boolean workable) {
        var work = HeatedMetal.workTag(piece);
        int hits = work.getInt(HeatedMetal.HITS), scrap = work.getInt(HeatedMetal.SCRAP);
        int gx = left + GRID_X, gw = SmithingGrid.SIZE * PX;
        if (current != null) {
            int colour = budget >= 0 && hits <= budget ? 0xFFE8B84A : 0xFF9AA4AE;
            GuiDraw.text(g, font, Component.translatable("gui.pioneers.smithing.hits", hits, budget), gx, top + INFO_Y, 0.75F, colour, gw / 2F);
            GuiDraw.right(g, font, Component.translatable("gui.pioneers.smithing.scrap", scrap, scrap / ForgeMetals.PIXELS_PER_NUGGET), gx + gw,
                  top + INFO_Y, 0.75F, 0xFF9AA4AE);
        }
        Component status;
        int colour;
        SmithingGrid grid = tile.shownGrid();
        if (!HeatedMetal.shape(piece).isEmpty()) {
            status = Component.translatable("gui.pioneers.smithing.status.done", HeatedMetal.shape(piece).getHoverName());
            colour = 0xFF7CD67C;
        } else if (current == null) {
            status = Component.translatable("gui.pioneers.smithing.status.choose");
            colour = 0xFFE8E2D6;
        } else if (!workable) {
            status = Component.translatable("gui.pioneers.smithing.status.cold", ForgeMetals.workingC(metal));
            colour = 0xFFE06050;
        } else if (grid != null && target != null && grid.missing(target) > grid.excess(target)) {
            int need = current.ingots() - AnvilTile.ingots(piece);
            status = need > 0 ? Component.translatable("gui.pioneers.smithing.status.weld", need)
                  : Component.translatable("gui.pioneers.smithing.status.short");
            colour = 0xFFE8B84A;
        } else {
            status = Component.translatable("gui.pioneers.smithing.status.help");
            colour = 0xFF9AA4AE;
        }
        GuiDraw.center(g, font, status, left + W / 2F, top + STATUS_Y, 0.75F, colour, W - 16);
    }

    private void drawTooltips(GuiGraphics g, int mx, int my) {
        int k = listIndex(mx, my);
        if (k >= 0) {
            SmithingRecipe r = recipes.get(k);
            List<Component> lines = new ArrayList<>();
            lines.add(icons.get(k).getHoverName());
            lines.add(Component.translatable("gui.pioneers.smithing.ingots", r.ingots(), r.pixels()).withStyle(ChatFormatting.GRAY));
            TooltipLayer.components(font, lines, mx, my);
        }
    }

    private static int brighten(int rgb, float f) {
        int r = Math.min(255, Math.round((rgb >> 16 & 255) * f)), gg = Math.min(255, Math.round((rgb >> 8 & 255) * f)),
              b = Math.min(255, Math.round((rgb & 255) * f));
        return r << 16 | gg << 8 | b;
    }

    // ------------------------------------------------------------------
    // Saisie
    // ------------------------------------------------------------------

    private int listIndex(double mx, double my) {
        int x = (int) Math.floor(mx) - left - LIST_X, y = (int) Math.floor(my) - top - LIST_Y;
        if (x < 0 || y < 0 || x >= LIST_COLS * LIST_CELL || y >= LIST_ROWS * LIST_CELL) {
            return -1;
        }
        int k = y / LIST_CELL * LIST_COLS + x / LIST_CELL + scroll * LIST_COLS;
        return k < recipes.size() ? k : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        AnvilTile tile = tile();
        if (tile == null) {
            return super.mouseClicked(mx, my, button);
        }
        int k = listIndex(mx, my);
        if (k >= 0 && button == 0) {
            send(AnvilTile.encodeSelect(k));
            return true;
        }
        int rx = (int) mx - left - ROSE_X, ry = (int) my - top - ROSE_Y;
        if (rx >= 0 && ry >= 0 && rx < ROSE && ry < ROSE) {
            int dx = rx - ROSE / 2, dy = ry - ROSE / 2;
            dir = Math.abs(dx) > Math.abs(dy) ? dx > 0 ? SmithingMoves.RIGHT : SmithingMoves.LEFT : dy > 0 ? SmithingMoves.DOWN : SmithingMoves.UP;
            return true;
        }
        int gx = (int) Math.floor((mx - left - GRID_X) / PX), gz = (int) Math.floor((my - top - GRID_Y) / PX);
        if (gx >= 0 && gz >= 0 && gx < SmithingGrid.SIZE && gz < SmithingGrid.SIZE && current != null) {
            if (!HeatedMetal.isWorkable(tile.piece(), tile.getLevel())) {
                return true;
            }
            int op = button == 1 ? SmithingMoves.OP_CUT : hasShiftDown() ? SmithingMoves.OP_DRAW : SmithingMoves.OP_STRIKE;
            if (button <= 1) {
                send(AnvilTile.encodeMove(op, gx, gz, dir));
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (listIndex(mx, my) >= 0 || mx >= left + LIST_X && mx < left + LIST_X + LIST_COLS * LIST_CELL + 6 && my >= top + LIST_Y
              && my < top + LIST_Y + LIST_ROWS * LIST_CELL) {
            int rows = (recipes.size() + LIST_COLS - 1) / LIST_COLS;
            scroll = Math.max(0, Math.min(Math.max(0, rows - LIST_ROWS), scroll - (int) Math.signum(delta)));
            return true;
        }
        dir = delta > 0 ? (dir + 3) & 3 : (dir + 1) & 3;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case InputConstants.KEY_UP -> dir = SmithingMoves.UP;
            case InputConstants.KEY_RIGHT -> dir = SmithingMoves.RIGHT;
            case InputConstants.KEY_DOWN -> dir = SmithingMoves.DOWN;
            case InputConstants.KEY_LEFT -> dir = SmithingMoves.LEFT;
            default -> {
                if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
                    onClose();
                    return true;
                }
                return super.keyPressed(key, scan, modifiers);
            }
        }
        return true;
    }
}
