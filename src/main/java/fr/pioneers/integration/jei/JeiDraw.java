package fr.pioneers.integration.jei;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.logistics.transport.CableTier;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11.3 (lot E3, demande de Guillaume : « ameliorer les interfaces JEI, les rendre superbes et comprehensibles ; garder le watermark du MK en bas
 * a droite ») : CHARTE COMMUNE de toutes les categories JEI du mod. Toute categorie passe par {@link PiCategory}, qui dessine avec ces outils :
 * <ul>
 *   <li>cadre sombre des interfaces du mod (memes teintes que {@link GuiDraw#panel}) ;</li>
 *   <li>en-tete de {@link #HEADER} px : icone de la machine, nom, pastille de couleur du tier ou de la famille, filet de la meme couleur ;</li>
 *   <li>contenu a {@link #PAD} px des bords, cases d'entree / sortie / catalyseur sombres a liseré distinct ({@link #slotBackground}) ;</li>
 *   <li>fleche de progression animee avec la duree dessous ({@link Arrow}) ;</li>
 *   <li>pied de page : pastilles a pictogramme ({@link Chip} : duree, energie FE/t x duree = total, temperature, pression...) avec leur
 *   infobulle, et le badge « MK » (watermark) en bas a droite, toujours a la meme place et du meme style ({@link #badge}).</li>
 * </ul>
 * Maquette et controle hors jeu : {@code tools/jei/jei_ui_preview.py} (memes constantes).
 */
@OnlyIn(Dist.CLIENT)
public final class JeiDraw {

    private JeiDraw() {
    }

    // ------------------------------------------------------------------ palette (memes teintes que GuiDraw / PiMenuScreen)

    public static final int PANEL = 0xFF141A20, EDGE = 0xFF2E3A44, HEAD = 0xFF1A222A, SCREEN = 0xFF0B1016;
    public static final int LIGHT = 0xFFD8E0E8, DIM = 0xFF8A98A6, FAINT = 0xFF55636F, ACCENT = 0xFF3FD0A0;
    public static final int GOOD = 0xFF6FD08A, WARN = 0xFFFFB04A, BAD = 0xFFFF6B5E, ENERGY = 0xFFFFD84A, CHANCE = 0xFFFFE08A, COLD = 0xFF7FC8FF;
    /** Liseré des cases : entree (acier), sortie (vert d'eau), catalyseur ou outil (laiton). Fond commun. */
    public static final int SLOT_IN = 0xFF2C3640, SLOT_OUT = 0xFF2F6F5A, SLOT_CAT = 0xFF6A5A2C, SLOT_FILL = 0xFF0E1419;

    // ------------------------------------------------------------------ geometrie (px d'interface)

    /** Marge gauche / droite du contenu. */
    public static final int PAD = 4;
    /** Hauteur de l'en-tete (filet compris). */
    public static final int HEADER = 14;
    /** Ecart entre l'en-tete et le contenu, entre le contenu et le pied de page. */
    public static final int GAP = 3;
    /** Hauteur d'une ligne de pied de page. */
    public static final int FOOT_LINE = 10;
    /** Marge sous le pied de page. */
    public static final int BOTTOM = 2;
    /** Badge (watermark MK) : largeur minimale, hauteur. */
    public static final int BADGE_W = 26, BADGE_H = 9;
    /** Echelle des petits textes (pied de page, notes). */
    public static final float SMALL = 0.75F;

    static Font font() {
        return Minecraft.getInstance().font;
    }

    // ------------------------------------------------------------------ badge (watermark) et familles

    /** Badge : texte court en blanc sur une pastille de couleur (tier MK, sinon famille de la categorie). */
    public record Badge(Component label, int rgb) {

        public static Badge tier(int tier) {
            CableTier t = Tiers.tier(tier);
            return new Badge(Component.translatable("industry.pioneers.tier." + t.id()), t.color);
        }

        public static Badge of(Family family) {
            return new Badge(Component.translatable("jei.pioneers.badge." + family.key), family.rgb);
        }
    }

    /** Familles des categories sans tier MK (le badge garde la meme place et le meme style que le badge de tier). */
    public enum Family {
        PRIMITIVE("primitive", 0x8A6A44),
        MECHANICAL("mechanical", 0x9C7A52),
        BRONZE("bronze", 0xB06A30),
        STEAM("steam", 0x6E7C88),
        FORGE("forge", 0xC0502E),
        BENCH("bench", 0x7A6248),
        KITCHEN("kitchen", 0xA8883A),
        WORLD("world", 0x4E8A5C),
        ENERGY("energy", 0xB09020),
        RESEARCH("research", 0x3F8FA8),
        DATA("data", 0x3A9AA8),
        SPACE("space", 0x4C5FB0),
        COSMOS("cosmos", 0x7848B0),
        FUSION("fusion", 0x9050C8),
        GUIDE("guide", 0x5A6A78),
        BLUEPRINT("blueprint", 0x2F8A78),
        /** 1.0.4-beta (lot 32) : machines de salle blanche (aucun tier affiche) : gris argent fonce = GuiSprites.CLEANROOM_INK. */
        CLEANROOM("cleanroom", 0x6C7783);

        public final String key;
        public final int rgb;

        Family(String key, int rgb) {
            this.key = key;
            this.rgb = rgb;
        }
    }

    /** Largeur d'un badge (au moins {@link #BADGE_W}, plus si le texte est long). */
    public static int badgeWidth(Badge b) {
        return Math.max(BADGE_W, Math.round(font().width(b.label()) * 0.7F) + 6);
    }

    /** Badge (pastille aux coins arrondis, reflet en haut, texte blanc a 0,7). Meme dessin que l'ancien {@code IndustryCategory.tierBadge}. */
    public static void badge(GuiGraphics g, int x, int y, Badge b) {
        int w = badgeWidth(b);
        int c = 0xFF000000 | b.rgb();
        g.fill(x + 1, y, x + w - 1, y + BADGE_H, c);
        g.fill(x, y + 1, x + w, y + BADGE_H - 1, c);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x40FFFFFF);
        float tw = Math.min(w - 4, font().width(b.label()) * 0.7F);
        GuiDraw.text(g, font(), b.label(), x + (w - tw) / 2F, y + 1.6F, 0.7F, 0xFFFFFFFF, w - 4);
    }

    // ------------------------------------------------------------------ cadre

    /** Fond complet d'une categorie : panneau, en-tete, filet de couleur, separateur du pied de page en {@code footTop}. */
    public static void frame(GuiGraphics g, int w, int h, int accentRgb, int footTop) {
        g.fill(0, 0, w, h, PANEL);
        g.renderOutline(0, 0, w, h, EDGE);
        g.fill(1, 1, w - 1, HEADER - 1, HEAD);
        g.fill(1, HEADER - 1, w - 1, HEADER, 0xFF000000 | accentRgb);
        g.fill(PAD, footTop - 2, w - PAD, footTop - 1, 0xFF222C35);
    }

    /** En-tete : icone (10 px), titre (reduit s'il est trop long), pastille de la couleur du badge a droite. */
    public static void header(GuiGraphics g, int w, @Nullable IDrawable icon, Component title, int rgb) {
        int tx = 4;
        if (icon != null) {
            g.pose().pushPose();
            g.pose().translate(3, 2, 0);
            g.pose().scale(0.625F, 0.625F, 1);
            icon.draw(g, 0, 0);
            g.pose().popPose();
            tx = 16;
        }
        int dot = w - 10;
        GuiDraw.text(g, font(), title, tx, 3.5F, 1F, LIGHT, dot - tx - 3);
        int c = 0xFF000000 | rgb;
        g.fill(dot, 4, dot + 6, 9, c);
        g.fill(dot + 1, 3, dot + 5, 10, c);
        g.fill(dot + 1, 4, dot + 5, 5, 0x50FFFFFF);
    }

    // ------------------------------------------------------------------ cases et jauges

    /** Fond de case 18 x 18 (coin = 1 px avant l'objet) au liseré du role. */
    public record SlotBackground(int edge) implements IDrawable {

        @Override
        public int getWidth() {
            return 18;
        }

        @Override
        public int getHeight() {
            return 18;
        }

        @Override
        public void draw(@NotNull GuiGraphics g, int x, int y) {
            g.fill(x, y, x + 18, y + 18, edge);
            g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FILL);
        }
    }

    private static final IDrawable IN = new SlotBackground(SLOT_IN), OUT = new SlotBackground(SLOT_OUT), CAT = new SlotBackground(SLOT_CAT);

    /** Fond de case selon le role (entree, sortie, catalyseur / outil). */
    public static IDrawable slotBackground(RecipeIngredientRole role) {
        return switch (role) {
            case OUTPUT -> OUT;
            case CATALYST, RENDER_ONLY -> CAT;
            default -> IN;
        };
    }

    /** Cadre de jauge (fluide, gaz) : meme dessin partout, ecran sombre a liseré d'acier. */
    public static void gaugeFrame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_IN);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SCREEN);
    }

    /** Petite mention sur une case (chance en haut a gauche, quantite variable en bas a droite), au-dessus de l'objet. */
    public record Mark(String text, boolean top) implements IDrawable {

        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public int getHeight() {
            return 16;
        }

        @Override
        public void draw(@NotNull GuiGraphics g, int x, int y) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            Component c = Component.literal(text);
            float s = 0.5F, w = font().width(c) * s;
            float tx = top ? x : x + 16 - w, ty = top ? y : y + 16 - 4;
            g.fill((int) Math.floor(tx - 0.5F), (int) Math.floor(ty - 0.5F), (int) Math.ceil(tx + w + 0.5F), (int) Math.ceil(ty + 4.5F), 0xC0000000);
            GuiDraw.text(g, font(), c, tx, ty, s, top ? CHANCE : LIGHT);
            g.pose().popPose();
        }
    }

    /** Chance lisible : « 35 % » (arrondi, « <1 % » en dessous). */
    public static String percent(double chance) {
        double p = chance * 100;
        return p < 1 ? "<1%" : Math.round(p) + "%";
    }

    // ------------------------------------------------------------------ pictogrammes 7 x 7

    /** Pictogrammes du pied de page (dessines pixel a pixel : aucune texture). */
    public enum Pic {
        TIME(JeiDraw.ACCENT, ".#####.", "#..#..#", "#..#..#", "#..##.#", "#.....#", "#.....#", ".#####."),
        ENERGY(JeiDraw.ENERGY, "...##..", "..##...", ".##....", "######.", "...##..", "..##...", ".##...."),
        TEMP(JeiDraw.BAD, "..#....", ".#.#...", ".#.#...", ".###...", "#####..", "#####..", ".###..."),
        COLD(JeiDraw.COLD, "#..#..#", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "#..#..#"),
        PRESSURE(JeiDraw.COLD, ".#####.", "#.....#", "#...#.#", "#..#..#", "#.....#", "#.....#", ".#####."),
        HEAT(JeiDraw.WARN, "...#...", "..##...", ".###.#.", ".#####.", "#######", "###.###", ".#####."),
        CLIMATE(JeiDraw.GOOD, "....###", "..#####", ".######", ".#####.", "#.###..", ".#.....", "#......"),
        INFO(JeiDraw.DIM, "...#...", ".......", "..##...", "...#...", "...#...", "...#...", "..###.."),
        WARN(JeiDraw.WARN, "...#...", "..###..", "..#.#..", ".##.##.", ".#####.", "##.#.##", "#######"),
        CHANCE(JeiDraw.CHANCE, "#######", "#.....#", "#.#.#.#", "#.....#", "#.#.#.#", "#.....#", "#######"),
        SIZE(JeiDraw.ACCENT, "..####.", ".#..##.", "####.#.", "#..#.#.", "#..##..", "####...", "......."),
        BLOCKS(JeiDraw.LIGHT, "###.###", "###.###", "###.###", ".......", "###.###", "###.###", "###.###"),
        RESEARCH(0xFF7FB8FF, "..###..", ".#...#.", ".#...#.", "..###..", "...#...", "..###..", "..###.."),
        //1.0.2-beta (lot 3) : la vapeur de l'age du bronze n'a plus de pression : un nuage (plus le manometre)
        STEAM(0xFFC8D0D8, "..#..#.", ".#..#..", "..#..#.", ".......", ".#####.", "#######", ".#####."),
        FLUID(0xFF5A9BE0, "...#...", "..###..", ".#####.", "#######", "#######", ".#####.", "..###.."),
        TURNS(0xFFC8A060, ".#.#.#.", "#######", ".#...#.", "##.#.##", ".#...#.", "#######", ".#.#.#."),
        //1.0.3-beta (lot 3) : multiplicateur du tier (fonte du minerai au creuset electrique, lixiviation acide)
        YIELD(JeiDraw.GOOD, "#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#");

        final int color;
        final String[] rows;

        Pic(int color, String... rows) {
            this.color = color;
            this.rows = rows;
        }

        public int color() {
            return color;
        }
    }

    public static void pic(GuiGraphics g, Pic p, int x, int y, int color) {
        for (int r = 0; r < 7; r++) {
            String row = p.rows[r];
            for (int c = 0; c < 7; c++) {
                if (row.charAt(c) == '#') {
                    g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
                }
            }
        }
    }

    // ------------------------------------------------------------------ pied de page : pastilles

    /** Pastille du pied de page : pictogramme, texte court (couleur), infobulle (detail). */
    public record Chip(Pic pic, Component text, int color, List<Component> tip) {

        public static Chip of(Pic pic, Component text, int color, Component... tip) {
            return new Chip(pic, text, color, List.of(tip));
        }
    }

    /** Place d'une pastille dans le pied de page (coordonnees de la categorie) et l'echelle de son texte. */
    public record Placed(Chip chip, int x, int y, int w, float scale) {

        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + FOOT_LINE;
        }
    }

    /** Largeur naturelle d'une pastille (pictogramme, 2 px, texte a {@link #SMALL}). */
    public static int chipWidth(Chip c) {
        return 9 + Math.round(font().width(c.text()) * SMALL);
    }

    /**
     * Range les pastilles ligne par ligne a partir de {@code (x0, y0)} sur {@code width} px, 6 px entre deux ; la derniere ligne laisse la place
     * du badge ({@code reserve}). Une pastille trop large pour une ligne entiere y tient seule, texte reduit.
     */
    public static List<Placed> layout(List<Chip> chips, int x0, int y0, int width, int lines, int reserve) {
        List<Placed> out = new ArrayList<>();
        int line = 0, x = x0;
        for (Chip c : chips) {
            int room = x0 + width - (line == lines - 1 ? reserve : 0);
            int w = chipWidth(c);
            if (x > x0 && x + w > room && line < lines - 1) {
                line++;
                x = x0;
                room = x0 + width - (line == lines - 1 ? reserve : 0);
            }
            float scale = SMALL;
            if (x + w > room) {
                int textRoom = Math.max(8, room - x - 9);
                scale = Math.max(0.45F, Math.min(SMALL, textRoom / (float) Math.max(1, font().width(c.text()))));
                w = Math.min(w, room - x);
            }
            if (w <= 9) {
                break;
            }
            out.add(new Placed(c, x, y0 + line * FOOT_LINE, w, scale));
            x += w + 6;
        }
        return out;
    }

    public static void chips(GuiGraphics g, List<Placed> placed) {
        for (Placed p : placed) {
            pic(g, p.chip().pic(), p.x(), p.y() + 1, p.chip().pic().color);
            GuiDraw.text(g, font(), p.chip().text(), p.x() + 9, p.y() + 1.5F + (SMALL - p.scale()) * 4, p.scale(), p.chip().color(), p.w() - 9);
        }
    }

    // ------------------------------------------------------------------ fleche de progression

    /** Taille de la zone de la fleche (celle de la fleche de JEI : les positions des categories restent valables). */
    public static final int ARROW_W = 24, ARROW_H = 17;

    /**
     * Fleche : corps et pointe en creux, remplie de gauche a droite selon {@code progress} (0..1), duree en petit dessous (si non vide).
     * Dessinee dans un cadre de {@link #ARROW_W} x {@link #ARROW_H}.
     */
    public static void arrow(GuiGraphics g, int x, int y, float progress, @Nullable Component label) {
        int fillTo = x + 1 + Math.round(21 * Math.max(0, Math.min(1, progress)));
        for (int col = 0; col < 22; col++) {
            int half = col < 13 ? 2 : Math.max(0, 21 - col) * 6 / 8;
            int cx = x + 1 + col, cy = y + 6;
            int top = cy - half, bottom = cy + half + 1;
            g.fill(cx, top, cx + 1, bottom, cx < fillTo ? ACCENT : SLOT_IN);
        }
        if (label != null) {
            Component c = label;
            float s = 0.55F, w = font().width(c) * s;
            GuiDraw.text(g, font(), c, x + ARROW_W / 2F - w / 2F, y + 13, s, DIM);
        }
    }

    /** Fleche immobile (productions, guides : pas de duree), centree sur {@code (cx, cy)}, pleine en gris clair. */
    public static void still(GuiGraphics g, float cx, float cy) {
        int x = Math.round(cx) - ARROW_W / 2, y = Math.round(cy) - 6;
        for (int col = 0; col < 22; col++) {
            int half = col < 13 ? 2 : Math.max(0, 21 - col) * 6 / 8;
            g.fill(x + 1 + col, y + 6 - half, x + 2 + col, y + 7 + half, DIM);
        }
    }

    /** Widget JEI de la fleche (remplace les fleches de JEI dans toutes les categories du mod, voir {@link PiCategory}). */
    public static final class Arrow implements IRecipeWidget {

        private final int ticks;
        private int x, y;
        private final List<FormattedText> tooltip = new ArrayList<>();

        public Arrow(int ticks) {
            this.ticks = ticks;
        }

        public void place(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public List<FormattedText> tooltip() {
            return tooltip;
        }

        @NotNull
        @Override
        public ScreenPosition getPosition() {
            return new ScreenPosition(x, y);
        }

        @Override
        public void draw(@NotNull GuiGraphics g, double mouseX, double mouseY) {
            float progress = ticks <= 0 ? 0 : (System.currentTimeMillis() % (ticks * 50L)) / (ticks * 50F);
            arrow(g, 0, 0, progress, ticks > 0 ? PiCategory.seconds(ticks) : null);
        }

        @Override
        public void getTooltip(@NotNull ITooltipBuilder tooltip, double mouseX, double mouseY) {
            if (mouseX < 0 || mouseY < 0 || mouseX >= ARROW_W || mouseY >= ARROW_H) {
                return;
            }
            if (ticks > 0) {
                tooltip.add(Component.translatable("jei.pioneers.time", PiCategory.seconds(ticks)));
            }
            tooltip.addAll(this.tooltip);
        }
    }
}
