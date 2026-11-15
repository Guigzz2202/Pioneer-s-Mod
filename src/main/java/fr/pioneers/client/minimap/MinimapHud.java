package fr.pioneers.client.minimap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.pioneers.space.SolarSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.joml.Matrix4f;

/**
 * 1.10.3 : la minimap a l'ecran (calque d'interface sous le texte de F3) : cadre sombre du mod (socle, anneau d'acier, lisere d'acier ou
 * de tier), carte carree ou ronde, nord fixe ou tournant avec le joueur, marque du nord, puis coordonnees, biome, heure et astre sous le
 * cadre (au-dessus si la minimap est dans un coin du bas). Masquee avec l'interface (F1), avec l'ecran de debogage (F3), pendant la carte
 * plein ecran, et dans une dimension a plafond si le reglage le demande.
 */
final class MinimapHud {

    private static final MapRenderer RENDER = new MapRenderer();
    private static final MapRenderer.Opts OPTS = new MapRenderer.Opts();
    static final int STEEL = 0x8A939C;
    static final int[] TIER = {0x4F8A55, 0x4468A0, 0xB5413A, 0x6B2340};

    private MinimapHud() {
    }

    static boolean visible(Minecraft mc) {
        if (!MinimapConfig.on(MinimapConfig.ENABLED, true) || mc.options.hideGui || mc.options.renderDebug || mc.level == null || mc.player == null) {
            return false;
        }
        if (mc.screen instanceof WorldMapScreen) {
            return false;
        }
        return !mc.level.dimensionType().hasCeiling()
               || MinimapConfig.pick(MinimapConfig.CEILING, MinimapConfig.Ceiling.IGNORE) != MinimapConfig.Ceiling.HIDE;
    }

    static int accent() {
        MinimapConfig.Frame f = MinimapConfig.pick(MinimapConfig.FRAME, MinimapConfig.Frame.STEEL);
        return f == MinimapConfig.Frame.STEEL ? STEEL : TIER[f.ordinal() - 1];
    }

    static void render(GuiGraphics g, int screenW, int screenH, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc) || MinimapClient.S.store == null) {
            fr.pioneers.client.look.HudZones.clear("minimap");
            return;
        }
        int size = MinimapConfig.num(MinimapConfig.SIZE, MinimapConfig.DEFAULT_SIZE);
        size = Math.min(size, Math.min(screenW, screenH) - 16);
        MinimapConfig.Corner corner = MinimapConfig.pick(MinimapConfig.CORNER, MinimapConfig.DEFAULT_CORNER);
        boolean right = corner == MinimapConfig.Corner.TOP_RIGHT || corner == MinimapConfig.Corner.BOTTOM_RIGHT;
        boolean bottom = corner == MinimapConfig.Corner.BOTTOM_LEFT || corner == MinimapConfig.Corner.BOTTOM_RIGHT;
        fr.pioneers.network.PacketEnvironment env = MinimapConfig.on(MinimapConfig.ENVIRONMENT, true) ? EnvironmentInfo.get() : null;
        Texts T = texts(mc, env);
        List<Component> info = T.info;
        Font font = mc.font;
        float ts = MinimapConfig.num(MinimapConfig.TEXT_SCALE, MinimapConfig.DEFAULT_TEXT) / 100F;
        int lineH = Math.round(10 * ts);
        //1.12.1 (F1) : ligne climat / oxygene / gravite (pictogrammes + valeurs), a une echelle lisible meme si le texte est a 50 %
        float es = Math.max(ts, 0.6F);
        int envH = env == null ? 0 : Math.round(10 * es) + 1;
        //1.0.5-beta (lot 1) : la saison sur la ligne climat si elle y tient, sinon juste dessous (meme interrupteur)
        Component season = env == null ? null : T.season;
        //1.0.5-beta (lot 14) : ligne climat des cultures (« Tempéré 17 °C · Humide 72 % ») en bas, sur deux lignes si elle ne tient pas a
        //l'echelle 0,5 (jamais coupee) ; la ligne d'environnement ne garde alors que l'oxygene et la gravite, la saison passe dessous
        Component zone = env == null ? null : T.zone, wet = env == null ? null : T.wet;
        int climateRows = zone == null || wet == null ? 0 : climateRow(font, zone, wet) * 0.5F <= size + 2 * 4 - 4 ? 1 : 2;
        boolean seasonBelow = season != null && (climateRows > 0 || !seasonFits(font, T, size + 4, es));
        if (seasonBelow) {
            envH += Math.round(10 * es) + 1;
        }
        envH += climateRows * (Math.round(10 * es) + 1);
        int infoH = info.isEmpty() && env == null ? 0 : info.size() * lineH + envH + 4;
        int ox = MinimapConfig.num(MinimapConfig.OFFSET_X, 6), oy = MinimapConfig.num(MinimapConfig.OFFSET_Y, 6);
        int border = 4;
        //1.0.5-beta (lot 26, retour 16) : point d'ancrage commun (HudLayouts.minimap) : coin exterieur du cadre fixe ; en bas, la carte ne
        //remonte plus quand une ligne d'information apparait (avant : decalee de la hauteur des lignes), les lignes s'empilent au-dessus
        int[] mm = fr.pioneers.client.look.HudLayouts.minimap(fr.pioneers.client.look.HudAnchor.corner(right, bottom), screenW, screenH, ox, oy, size,
              border, infoH);
        int x = mm[0], y = mm[1];
        infoH = mm[3];
        //1.0.5-beta (lot 14) : place prise (cadre et lignes d'information), evitee par la fiche au regard
        fr.pioneers.client.look.HudZones.mark("minimap", x - border, bottom ? y - border - infoH : y - border, x + size + border,
              bottom ? y + size + border : y + size + border + infoH);
        boolean round = MinimapConfig.pick(MinimapConfig.SHAPE, MinimapConfig.Shape.SQUARE) == MinimapConfig.Shape.ROUND;
        boolean rotate = MinimapConfig.on(MinimapConfig.ROTATE, false);
        float cx = x + size / 2F, cy = y + size / 2F, half = size / 2F;
        float rot = rotate ? MapMath.rotationFor(mc.player.getViewYRot(partialTick)) : 0;
        double px = mc.player.xo + (mc.player.getX() - mc.player.xo) * partialTick, pz = mc.player.zo + (mc.player.getZ() - mc.player.zo) * partialTick;
        MapMath m = RENDER.m;
        m.project(px, pz, MinimapConfig.zoom(), rot, cx, cy);
        if (round) {
            m.viewCircle(cx, cy, half);
        } else {
            m.viewRect(x, y, x + size, y + size);
        }
        RENDER.rotation(rot);
        OPTS.full = false;
        OPTS.edge = true;
        OPTS.round = round;
        OPTS.half = half;
        frameBack(g, x, y, size, round);
        RENDER.draw(g, MinimapClient.S, MinimapConfig.num(MinimapConfig.OPACITY, 100) / 100F, OPTS);
        frameFront(g, x, y, size, round);
        if (MinimapConfig.on(MinimapConfig.NORTH_MARK, true)) {
            north(g, font, m, cx, cy, half, round);
        }
        // lignes sous (ou au-dessus du) cadre
        if (infoH > 0) {
            int ty = bottom ? y - border - infoH + 2 : y + size + border + 2;
            int tc = 0xFF000000 | MinimapConfig.num(MinimapConfig.C_TEXT, 0xE0F5EA);
            g.fill(x - border, ty - 2, x + size + border, ty + infoH - 2, 0xA0141A20);
            for (Component line : info) {
                g.pose().pushPose();
                g.pose().translate(cx, ty, 0);
                g.pose().scale(ts, ts, 1);
                int w = font.width(line);
                int maxW = Math.round((size + 2 * border - 4) / ts);
                if (w > maxW) {
                    String cut = font.plainSubstrByWidth(line.getString(), maxW - font.width("\u2026")) + "\u2026";
                    g.drawString(font, cut, -font.width(cut) / 2, 0, tc, false);
                } else {
                    g.drawString(font, line, -w / 2, 0, tc, false);
                }
                g.pose().popPose();
                ty += lineH;
            }
            if (env != null) {
                environment(g, font, env, T, cx, ty, size + 2 * border - 4, es, tc, season != null && !seasonBelow, climateRows > 0);
                int row = Math.round(10 * es) + 1;
                if (seasonBelow) {
                    ty += row;
                    seasonLine(g, font, season, cx, ty, size + 2 * border - 4, es, tc);
                }
                if (climateRows == 1) {
                    climateLine(g, font, env, new Component[]{zone, wet}, cx, ty + row, size + 2 * border - 4, es, tc);
                } else if (climateRows == 2) {
                    climateLine(g, font, env, new Component[]{zone, null}, cx, ty + row, size + 2 * border - 4, es, tc);
                    climateLine(g, font, env, new Component[]{null, wet}, cx, ty + 2 * row, size + 2 * border - 4, es, tc);
                }
            }
        }
    }

    /**
     * 1.12.1 (F1) : thermometre + « 17 °C · 45 % », bulle d'oxygene + « Air » / « Pressurise » / « Aucun », fleche + « 1,00 g », centres.
     * Trop large : l'humidite part, puis la ligne est resserree (jamais sous 0,5). 1.0.5-beta (lot 1) : {@code withSeason} : la saison et le
     * jour suivent le climat (« 17 °C · 45 % · Automne, j. 3/10 ») quand la ligne entiere tient sans resserrer.
     */
    private static void environment(GuiGraphics g, Font font, fr.pioneers.network.PacketEnvironment env, Texts T, float cx, int ty, int maxW, float es,
          int tc, boolean withSeason, boolean climateLine) {
        Component[] texts = {T.climate(env, true, withSeason), T.oxygen, T.gravity};
        int[] icons = {Icons.THERMO, Icons.OXYGEN, Icons.GRAVITY};
        int[] tints = {EnvironmentInfo.temperatureColor(env.temperature()), EnvironmentInfo.oxygenColor(env), 0xE0F5EA};
        if (climateLine) {
            //1.0.5-beta (lot 14) : temperature et humidite sont sur la ligne climat : ici l'oxygene et la gravite seulement
            texts = new Component[]{texts[1], texts[2]};
            icons = new int[]{icons[1], icons[2]};
            tints = new int[]{tints[1], tints[2]};
        }
        int w = rowWidth(font, texts);
        if (!climateLine && w * es > maxW) {
            texts[0] = T.climate(env, false, withSeason);
            w = rowWidth(font, texts);
        }
        float s = Math.max(0.5F, Math.min(es, maxW / (float) w));
        g.pose().pushPose();
        g.pose().translate(cx - w * s / 2F, ty, 0);
        g.pose().scale(s, s, 1);
        int x = 0;
        for (int i = 0; i < texts.length; i++) {
            Icons.draw(g, icons[i], x + 4.5F, 4F, tints[i], 1F);
            x += 11;
            g.drawString(font, texts[i], x, 0, icons[i] == Icons.OXYGEN ? 0xFF000000 | tints[i] : tc, false);
            x += font.width(texts[i]) + 7;
        }
        g.pose().popPose();
    }

    /** 1.0.5-beta (lot 14) : largeur (echelle 1) de la ligne climat : thermometre + zone + °C, goutte + humidite. */
    private static int climateRow(Font font, Component zone, Component wet) {
        return 11 + font.width(zone) + 7 + 11 + font.width(wet);
    }

    /**
     * 1.0.5-beta (lot 14) : ligne climat des cultures, centree : thermometre (couleur de la temperature) + « Tempéré 17 °C », goutte (couleur de
     * l'humidite) + « Humide 72 % » ; {@code parts} : l'une, l'autre ou les deux (null = absente). Resserree jusqu'a 0,5 au plus.
     */
    private static void climateLine(GuiGraphics g, Font font, fr.pioneers.network.PacketEnvironment env, Component[] parts, float cx, int ty, int maxW, float es,
          int tc) {
        int[] icons = {Icons.THERMO, Icons.HUMIDITY};
        int[] tints = {EnvironmentInfo.temperatureColor(env.temperature()), EnvironmentInfo.humidityColor(env.climateH())};
        int w = -7;
        for (Component c : parts) {
            w += c == null ? 0 : 11 + font.width(c) + 7;
        }
        float s = Math.max(0.5F, Math.min(es, maxW / (float) w));
        g.pose().pushPose();
        g.pose().translate(cx - w * s / 2F, ty, 0);
        g.pose().scale(s, s, 1);
        int x = 0;
        for (int i = 0; i < 2; i++) {
            if (parts[i] == null) {
                continue;
            }
            Icons.draw(g, icons[i], x + 4.5F, 4F, tints[i], 1F);
            x += 11;
            g.drawString(font, parts[i], x, 0, tc, false);
            x += font.width(parts[i]) + 7;
        }
        g.pose().popPose();
    }

    /** 1.0.5-beta (lot 1) : vrai si la ligne climat avec la saison (humidite comprise) tient a l'echelle {@code es}. */
    private static boolean seasonFits(Font font, Texts T, int maxW, float es) {
        Component[] texts = {T.climate(T.env, true, true), T.oxygen, T.gravity};
        return rowWidth(font, texts) * es <= maxW;
    }

    /** 1.0.5-beta (lot 1) : « Automne, j. 3/10 » centre sous la ligne climat (minimap trop etroite pour une seule ligne). */
    private static void seasonLine(GuiGraphics g, Font font, Component season, float cx, int ty, int maxW, float es, int tc) {
        int w = font.width(season);
        float s = Math.max(0.5F, Math.min(es, maxW / (float) w));
        g.pose().pushPose();
        g.pose().translate(cx - w * s / 2F, ty, 0);
        g.pose().scale(s, s, 1);
        g.drawString(font, season, 0, 0, tc, false);
        g.pose().popPose();
    }

    private static int rowWidth(Font font, Component[] texts) {
        int w = 0;
        for (Component t : texts) {
            w += 11 + font.width(t) + 7;
        }
        return w - 7;
    }

    /**
     * 1.0.5-beta (lot 29, reprise du §2 « client ») : textes de la minimap d'UN TICK DU CLIENT (coordonnees, biome, heure, astre ; climat,
     * saison, zones, oxygene, gravite) : avant, une douzaine de textes traduits (et un {@code String.format}) etaient refaits a chaque image,
     * 3 a 7 fois par tick a 60 - 144 images/s. Cle : heure du monde, case du joueur, dimension, paquet d'environnement, reglages, langue ;
     * memes textes, meme dessin.
     */
    private static final class Texts {
        long tick = Long.MIN_VALUE, pos;
        int flags;
        @org.jetbrains.annotations.Nullable
        Object dim;
        @org.jetbrains.annotations.Nullable
        fr.pioneers.network.PacketEnvironment env;
        String lang = "";
        List<Component> info = List.of();
        @org.jetbrains.annotations.Nullable
        Component season, zone, wet, oxygen, gravity;
        final Component[] climate = new Component[4];

        Component climate(fr.pioneers.network.PacketEnvironment env, boolean humid, boolean withSeason) {
            int i = (humid ? 2 : 0) + (withSeason ? 1 : 0);
            if (climate[i] == null) {
                //ligne climat avec humidite et saison (essai « tient-elle ? », lot 1) ou ses variantes resserrees
                climate[i] = humid && withSeason ? EnvironmentInfo.climate(env, true, true) : EnvironmentInfo.climate(env, humid, withSeason);
            }
            return climate[i];
        }
    }

    private static final Texts TEXTS = new Texts();

    private static Texts texts(Minecraft mc, @org.jetbrains.annotations.Nullable fr.pioneers.network.PacketEnvironment env) {
        long tick = mc.level.getGameTime(), pos = mc.player.blockPosition().asLong();
        int flags = (MinimapConfig.on(MinimapConfig.COORDS, true) ? 1 : 0) | (MinimapConfig.on(MinimapConfig.BIOME, true) ? 2 : 0)
                    | (MinimapConfig.on(MinimapConfig.TIME, false) ? 4 : 0) | (MinimapConfig.on(MinimapConfig.ASTRE, true) ? 8 : 0);
        String lang = mc.options.languageCode;
        Texts t = TEXTS;
        if (t.tick == tick && t.pos == pos && t.flags == flags && t.dim == mc.level.dimension() && t.env == env && t.lang.equals(lang)) {
            return t;
        }
        t.tick = tick;
        t.pos = pos;
        t.flags = flags;
        t.dim = mc.level.dimension();
        t.env = env;
        t.lang = lang;
        t.info = info(mc);
        java.util.Arrays.fill(t.climate, null);
        t.season = env == null ? null : EnvironmentInfo.season();
        t.zone = env == null ? null : EnvironmentInfo.climateZone(env);
        t.wet = env == null ? null : EnvironmentInfo.humidityZone(env);
        t.oxygen = env == null ? null : EnvironmentInfo.oxygen(env);
        t.gravity = env == null ? null : EnvironmentInfo.gravity(env);
        return t;
    }

    /** Lignes d'information demandees. */
    private static List<Component> info(Minecraft mc) {
        List<Component> out = new ArrayList<>(4);
        BlockPos p = mc.player.blockPosition();
        if (MinimapConfig.on(MinimapConfig.COORDS, true)) {
            out.add(Component.literal(p.getX() + "  " + p.getY() + "  " + p.getZ()));
        }
        if (MinimapConfig.on(MinimapConfig.BIOME, true)) {
            ResourceKey<Biome> key = mc.level.getBiome(p).unwrapKey().orElse(null);
            if (key != null) {
                out.add(Component.translatableWithFallback("biome." + key.location().getNamespace() + "." + key.location().getPath(), pretty(key.location().getPath())));
            }
        }
        if (MinimapConfig.on(MinimapConfig.TIME, false)) {
            out.add(Component.literal(clock(mc.level.getDayTime())));
        }
        if (MinimapConfig.on(MinimapConfig.ASTRE, true)) {
            ResourceKey<Level> dim = mc.level.dimension();
            SolarSystem.Body body = SolarSystem.fromDimension(dim);
            if (body != null && dim != Level.OVERWORLD) {
                out.add(Component.translatable(body.getTranslationKey()));
            }
        }
        return out;
    }

    private static String pretty(String path) {
        String s = path.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Heure du jour (0 tick = 6 h 00). */
    static String clock(long dayTime) {
        long t = Math.floorMod(dayTime + 6000, 24000L);
        int h = (int) (t / 1000), min = (int) (t % 1000 * 60 / 1000);
        return String.format("%02d:%02d", h, min);
    }

    // ------------------------------------------------------------------ cadre
    private static void frameBack(GuiGraphics g, int x, int y, int size, boolean round) {
        if (round) {
            ring(g, x + size / 2F, y + size / 2F, size / 2F, size / 2F + 4, 0xF0141A20);
        } else {
            g.fill(x - 4, y - 4, x + size + 4, y + size + 4, 0xF0141A20);
        }
    }

    private static void frameFront(GuiGraphics g, int x, int y, int size, boolean round) {
        int accent = 0xFF000000 | accent();
        if (round) {
            float c = size / 2F;
            ring(g, x + c, y + c, c + 3, c + 4, 0xFF2E3A44);
            ring(g, x + c, y + c, c, c + 1.25F, 0xFF5A6670);
            ring(g, x + c, y + c, c + 1.25F, c + 2.25F, accent);
            return;
        }
        // anneau d'acier (clair en haut a gauche, sombre en bas a droite), lisere du tier, rivets
        g.renderOutline(x - 4, y - 4, size + 8, size + 8, 0xFF2E3A44);
        g.fill(x - 1, y - 1, x + size + 1, y, 0xFF6F7B86);
        g.fill(x - 1, y - 1, x, y + size + 1, 0xFF6F7B86);
        g.fill(x - 1, y + size, x + size + 1, y + size + 1, 0xFF3C4852);
        g.fill(x + size, y - 1, x + size + 1, y + size + 1, 0xFF3C4852);
        g.fill(x - 3, y - 3, x + size + 3, y - 2, accent);
        g.fill(x - 3, y + size + 2, x + size + 3, y + size + 3, (accent & 0xFFFFFF) | 0x90000000);
        int[][] rivets = {{x - 3, y - 3}, {x + size + 1, y - 3}, {x - 3, y + size + 1}, {x + size + 1, y + size + 1}};
        for (int[] r : rivets) {
            g.fill(r[0], r[1], r[0] + 2, r[1] + 2, 0xFFA8B4BE);
        }
    }

    /** Couronne pleine entre deux rayons (triangles colores). */
    static void ring(GuiGraphics g, float cx, float cy, float r0, float r1, int argb) {
        g.flush();
        Matrix4f mat = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int a = argb >>> 24, r = (argb >> 16) & 255, gg = (argb >> 8) & 255, bl = argb & 255;
        int n = MapMath.CIRCLE_SIDES;
        float k = 1 / (float) Math.cos(Math.PI / n);
        for (int i = 0; i <= n; i++) {
            double t = 2 * Math.PI * i / n;
            float c = (float) Math.cos(t), s = (float) Math.sin(t);
            b.vertex(mat, cx + c * r1 * k, cy + s * r1 * k, 0).color(r, gg, bl, a).endVertex();
            b.vertex(mat, cx + c * r0 * k, cy + s * r0 * k, 0).color(r, gg, bl, a).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
    }

    /** « N » sur le bord, dans la direction du nord. */
    private static void north(GuiGraphics g, Font font, MapMath m, float cx, float cy, float half, boolean round) {
        float nx = m.screenX(m.cx, m.cz - 1) - cx, ny = m.screenY(m.cx, m.cz - 1) - cy;
        float d = (float) Math.sqrt(nx * nx + ny * ny);
        if (d < 1e-4) {
            return;
        }
        nx /= d;
        ny /= d;
        float k = round ? half + 2 : (half + 2) / Math.max(Math.abs(nx), Math.abs(ny));
        float x = cx + nx * k, y = cy + ny * k;
        g.fill(Math.round(x) - 4, Math.round(y) - 4, Math.round(x) + 4, Math.round(y) + 4, 0xFF141A20);
        g.renderOutline(Math.round(x) - 4, Math.round(y) - 4, 8, 8, 0xFF000000 | accent());
        g.pose().pushPose();
        g.pose().translate(x, y - 2.5F, 0);
        g.pose().scale(0.62F, 0.62F, 1);
        g.drawString(font, "N", -font.width("N") / 2, 0, 0xFFE0F5EA, false);
        g.pose().popPose();
    }
}
