package fr.pioneers.client.farm.aquarium;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.farm.aquarium.AquariumDecor;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 1.0.5-beta (lot 25) : DESSIN de la composition du fond d'une case d'aquarium ({@link AquariumDecor}), en px de la case, dans UN tampon
 * (atlas des blocs, sans faces cachees) : sols en dalles de {@link AquariumDecor#GROUND_H} px (texture du bloc, flancs seulement vers le verre
 * ou un emplacement sans sol), elements (herbier et varech qui ondulent, coraux vivants en touffes croisees, cornichons de mer lumineux,
 * nenuphar a la surface, coquillages, roches en blocs empiles, bois flotte, amphore couchee, coffre englouti entrouvert), tous enfonces de
 * {@link #SINK} px dans le sol (aucune face confondue : pas de scintillement) et contenus a {@link #REACH} px de leur centre (rien ne touche le
 * verre). Atlas fourni par l'appelant ({@link Atlas}) : le rendu ({@code AquariumRenderer}) et le banc de la planche
 * ({@code tools/aquarium/AquariumScene.java}) dessinent par le meme code.
 */
public final class AquariumDecorDraw {

    private AquariumDecorDraw() {
    }

    /** Coordonnees d'une texture dans l'atlas : {u0, v0, u1, v1}. */
    public interface Atlas {

        float[] uv(String texture);
    }

    /** Enfoncement des elements dans le sol, distance maximale d'un sommet d'element au centre de l'emplacement (px), lumiere pleine. */
    public static final float SINK = 0.3F, REACH = 3.3F;
    public static final int FULL_BRIGHT = 0xF000F0;
    /** Ecart supplementaire des flancs du sol au verre (px : le flanc d'eau du verre est a {@code gap}). */
    public static final float GROUND_INSET = 0.05F;

    /** Ce que le dessin d'une case doit savoir de son voisinage. */
    public record Cell(AquariumDecor.Kind[] ground, AquariumDecor.Kind[] piece, boolean[] glass, AquariumDecor.Kind[] neighbourGround, float gap,
                       float water, float surface, int seed) {
    }

    /**
     * Toute la case : sols puis elements. {@code glass} : nord, sud, ouest, est sans aquarium voisin ; {@code neighbourGround} : sol des 8
     * emplacements voisins dans les cases voisines (nord 0-1, sud 2-3, ouest 4-5, est 6-7 ; null = pas de sol) ; {@code water} : colonne d'eau
     * (px, du dessus du sol a la surface) ; {@code surface} : y de la surface (px) pour le nenuphar.
     */
    public static void draw(PoseStack pose, VertexConsumer vc, Atlas atlas, Cell c, double time, int light) {
        for (int s = 0; s < AquariumDecor.SLOTS; s++) {
            if (c.ground()[s] != null) {
                ground(pose, vc, atlas, c, s, light);
            }
        }
        for (int s = 0; s < AquariumDecor.SLOTS; s++) {
            AquariumDecor.Kind k = c.piece()[s];
            if (k == null) {
                continue;
            }
            float base = groundTop(c.ground()[s], c.gap()) - SINK;
            int seed = c.seed() * 31 + s * 977 + AquariumDecor.index(k) * 7919;
            pose.pushPose();
            pose.translate(AquariumDecor.slotX(s) / 16F, base / 16F, AquariumDecor.slotZ(s) / 16F);
            piece(pose, vc, atlas, k, AquariumDecor.height(k, c.water()) + SINK, seed, time, light, c.surface() - base);
            pose.popPose();
        }
    }

    /** Dessus du sol d'un emplacement (px) : sur le fond de verre ({@code gap}), plus la dalle s'il y en a une. */
    public static float groundTop(AquariumDecor.Kind ground, float gap) {
        return gap + (ground != null ? AquariumDecor.GROUND_H : 0F);
    }

    // ------------------------------------------------------------------ sols

    private static void ground(PoseStack pose, VertexConsumer vc, Atlas atlas, Cell c, int s, int light) {
        float[] uv = atlas.uv(c.ground()[s].texture());
        float gx = (s & 1) == 0 ? 0 : 8, gz = (s & 2) == 0 ? 0 : 8;
        boolean west = (s & 1) == 0, north = (s & 2) == 0;
        float in = c.gap() + GROUND_INSET;
        float x0 = west && c.glass()[2] ? in : gx, x1 = !west && c.glass()[3] ? 16 - in : gx + 8;
        float z0 = north && c.glass()[0] ? in : gz, z1 = !north && c.glass()[1] ? 16 - in : gz + 8;
        float y0 = c.gap(), y1 = c.gap() + AquariumDecor.GROUND_H;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        //dessus : texture a la position (motif continu d'un emplacement a l'autre)
        quad(vc, m, n, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, u(uv, x0), v(uv, z0), u(uv, x1), v(uv, z1), 236, 236, 236, 255, light);
        float vs = v(uv, 16 - AquariumDecor.GROUND_H), ve = uv[3];
        //flancs : vers le verre, ou vers un emplacement (de la case ou de la voisine) sans sol
        AquariumDecor.Kind[] g = c.ground(), ng = c.neighbourGround();
        boolean nOpen = north ? !c.glass()[0] && ng[(s & 1)] == null || c.glass()[0] : g[s - 2] == null;
        boolean sOpen = !north ? !c.glass()[1] && ng[2 + (s & 1)] == null || c.glass()[1] : g[s + 2] == null;
        boolean wOpen = west ? !c.glass()[2] && ng[4 + (s >> 1)] == null || c.glass()[2] : g[s - 1] == null;
        boolean eOpen = !west ? !c.glass()[3] && ng[6 + (s >> 1)] == null || c.glass()[3] : g[s + 1] == null;
        if (nOpen) {
            quad(vc, m, n, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1, u(uv, x0), vs, u(uv, x1), ve, 200, 200, 200, 255, light);
        }
        if (sOpen) {
            quad(vc, m, n, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1, u(uv, x0), vs, u(uv, x1), ve, 200, 200, 200, 255, light);
        }
        if (wOpen) {
            quad(vc, m, n, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0, u(uv, z0), vs, u(uv, z1), ve, 176, 176, 176, 255, light);
        }
        if (eOpen) {
            quad(vc, m, n, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0, u(uv, z0), vs, u(uv, z1), ve, 176, 176, 176, 255, light);
        }
    }

    // ------------------------------------------------------------------ elements

    /** Un element au centre de son emplacement (repere : centre, pied a y 0), hauteur {@code h} px ; {@code surfaceY} : surface (px au-dessus). */
    public static void piece(PoseStack pose, VertexConsumer vc, Atlas atlas, AquariumDecor.Kind k, float h, int seed, double time, int light, float surfaceY) {
        float turn = 360F * AquariumDecor.rnd(seed, 1);
        float sway = (float) Math.sin(time * 0.05 + 6.28 * AquariumDecor.rnd(seed, 2));
        float[] uv = atlas.uv(k.texture());
        switch (k.key()) {
            case "seagrass" -> {
                pose.mulPose(Axis.YP.rotationDegrees(turn));
                cross(pose, vc, uv, 2.5F, h, 3, sway * 0.55F, 0F, light, 255, 255, 255, 0, 16);
            }
            case "kelp" -> {
                pose.mulPose(Axis.YP.rotationDegrees(turn));
                kelp(pose, vc, uv, atlas.uv("minecraft:block/kelp"), h, sway, light);
            }
            case "tube_coral", "brain_coral", "bubble_coral", "fire_coral", "horn_coral" -> {
                pose.mulPose(Axis.YP.rotationDegrees(turn));
                cross(pose, vc, uv, 3F, h, 3, sway * 0.15F, 0F, light, 255, 255, 255, 0, 16);
                //deuxieme touffe plus basse, decalee : le massif est plus fourni
                pose.translate(0.9F / 16F, 0, -0.7F / 16F);
                cross(pose, vc, uv, 2.1F, h * 0.7F, 2, sway * 0.1F, 22F, light, 236, 236, 236, 0, 16);
            }
            case "sea_pickle" -> pickles(pose, vc, uv, seed);
            case "lily_pad" -> lilyPad(pose, vc, uv, turn, surfaceY + 0.05F, light);
            case "seashell" -> shells(pose, vc, uv, turn, seed, light);
            case "rock", "mossy_rock" -> rocks(pose, vc, uv, turn, seed, h, light);
            case "driftwood" -> driftwood(pose, vc, uv, atlas.uv("minecraft:block/stripped_birch_log_top"), turn, light);
            case "amphora" -> amphora(pose, vc, uv, turn, light);
            case "chest" -> chest(pose, vc, uv, atlas.uv("minecraft:block/iron_block"), atlas.uv("minecraft:block/gold_block"), turn, light);
            default -> {
            }
        }
    }

    /** Touffe de {@code planes} plans croises de demi-largeur {@code w}, haute de {@code h} ; le haut ondule de {@code sway} px. */
    static void cross(PoseStack pose, VertexConsumer vc, float[] uv, float w, float h, int planes, float sway, float rot, int light, int r, int g, int b,
          float vTop, float vBottom) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float u0 = uv[0], u1 = uv[2], v0 = lerp(uv[1], uv[3], vTop / 16F), v1 = lerp(uv[1], uv[3], vBottom / 16F);
        for (int i = 0; i < planes; i++) {
            double a = Math.toRadians(rot + 180.0 / planes * i + 22.5);
            float dx = (float) Math.cos(a) * w, dz = (float) Math.sin(a) * w;
            float sx = sway * (float) Math.cos(a + 1.2), sz = sway * (float) Math.sin(a + 1.2);
            quad(vc, m, n, -dx, 0, -dz, -dx + sx, h, -dz + sz, dx + sx, h, dz + sz, dx, 0, dz, -dz / w, 0, dx / w, u0, v0, u1, v1, r, g, b, 255, light);
        }
    }

    /** Varech : tige en plans croises par troncons de 8 px (texture de la tige), bout en texture de la pointe ; ondule de plus en plus haut. */
    static void kelp(PoseStack pose, VertexConsumer vc, float[] stem, float[] tip, float h, float sway, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float w = 2.2F;
        int segs = Math.max(1, (int) Math.ceil(h / 8F));
        for (int i = 0; i < segs; i++) {
            float y0 = h * i / segs, y1 = h * (i + 1) / segs;
            float s0 = sway * 0.9F * (y0 / Math.max(1, h)) * (y0 / Math.max(1, h)), s1 = sway * 0.9F * (y1 / Math.max(1, h)) * (y1 / Math.max(1, h));
            float[] uv = i == segs - 1 ? tip : stem;
            float v0 = lerp(uv[1], uv[3], 1 - (y1 - y0) / 16F), v1 = uv[3];
            for (int p = 0; p < 2; p++) {
                double a = Math.toRadians(45 + 90 * p);
                float dx = (float) Math.cos(a) * w, dz = (float) Math.sin(a) * w;
                quad(vc, m, n, -dx + s0, y0, -dz, -dx + s1, y1, -dz, dx + s1, y1, dz, dx + s0, y0, dz, -dz / w, 0, dx / w, uv[0], v0, uv[2], v1, 255, 255,
                      255, 255, light);
            }
        }
    }

    /** Trois cornichons de mer (2 x 3 x 2 px, texture du modele vanilla), lumineux. */
    static void pickles(PoseStack pose, VertexConsumer vc, float[] uv, int seed) {
        float[][] at = {{-1.2F, -0.8F, 3F}, {1.1F, -0.4F, 2.4F}, {0F, 1.3F, 2.7F}};
        for (float[] p : at) {
            pose.pushPose();
            pose.translate(p[0] / 16F, 0, p[1] / 16F);
            pose.mulPose(Axis.YP.rotationDegrees(30F * AquariumDecor.rnd(seed, 5 + (int) p[2])));
            box(pose, vc, sub(uv, 4, 5, 8, 11), sub(uv, 4, 1, 8, 5), -1, 0, -1, 1, p[2], 1, FULL_BRIGHT, 255, 255, 255);
            pose.popPose();
        }
    }

    /** Nenuphar : feuille a plat a la surface (teinte des nenuphars vanilla), legerement tournee. */
    static void lilyPad(PoseStack pose, VertexConsumer vc, float[] uv, float turn, float y, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float r = 2.3F;
        quad(vc, m, n, -r, y, -r, -r, y, r, r, y, r, r, y, -r, 0, 1, 0, uv[0], uv[1], uv[2], uv[3], 0x20, 0x80, 0x30, 255, light);
        pose.popPose();
    }

    /** Deux coquillages (texture de l'objet du mod) couches, inclines. */
    static void shells(PoseStack pose, VertexConsumer vc, float[] uv, float turn, int seed, int light) {
        float[][] at = {{-0.8F, 0.4F, 1.6F, 18F}, {1.4F, -1.0F, 1.1F, -24F}};
        for (float[] p : at) {
            pose.pushPose();
            pose.translate(p[0] / 16F, (0.3F + 0.25F) / 16F, p[1] / 16F);
            pose.mulPose(Axis.YP.rotationDegrees(turn + 70F * AquariumDecor.rnd(seed, 9 + (int) p[3])));
            pose.mulPose(Axis.XP.rotationDegrees(p[3]));
            Matrix4f m = pose.last().pose();
            Matrix3f n = pose.last().normal();
            float r = p[2];
            quad(vc, m, n, -r, 0, -r, -r, 0, r, r, 0, r, r, 0, -r, 0, 1, 0, uv[0], uv[1], uv[2], uv[3], 255, 255, 255, 255, light);
            pose.popPose();
        }
    }

    /** Roche : trois blocs empiles et decales (texture du bloc, faces ombrees), hauteur {@code h}. */
    static void rocks(PoseStack pose, VertexConsumer vc, float[] uv, float turn, int seed, float h, int light) {
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        float k = h / 4.3F;
        box(pose, vc, uv, uv, -2.3F, 0, -1.9F, 1.7F, 2.6F * k, 1.8F, light, 255, 255, 255);
        box(pose, vc, uv, uv, -0.9F, 0.5F, -1.2F, 2.1F, 4.3F * k, 1.1F, light, 242, 242, 242);
        box(pose, vc, uv, uv, 0.6F, 0, 0.5F, 2.3F, 1.7F * k, 2.2F, light, 230, 230, 230);
    }

    /** Bois flotte : branche delavee couchee en biais, une fourche ; bouts en texture de coupe. */
    static void driftwood(PoseStack pose, VertexConsumer vc, float[] side, float[] end, float turn, int light) {
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        pose.pushPose();
        pose.translate(0, 0.9F / 16F, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(7F));
        box(pose, vc, side, end, -3F, -0.7F, -0.7F, 3F, 0.7F, 0.6F, light, 230, 222, 206);
        pose.popPose();
        pose.pushPose();
        pose.translate(0.6F / 16F, 1.1F / 16F, 0.2F / 16F);
        pose.mulPose(Axis.YP.rotationDegrees(-38F));
        pose.mulPose(Axis.ZP.rotationDegrees(-12F));
        box(pose, vc, side, end, 0, -0.45F, -0.45F, 2.6F, 0.45F, 0.45F, light, 222, 214, 198);
        pose.popPose();
    }

    /** Amphore de terre cuite couchee et a demi enfouie : panse, epaule, col, levre, deux anses. */
    static void amphora(PoseStack pose, VertexConsumer vc, float[] uv, float turn, int light) {
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        pose.translate(0, 1.4F / 16F, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-12F));
        int r = 214, g = 128, b = 86;
        box(pose, vc, uv, uv, -1.7F, -1.6F, -2.2F, 1.7F, 1.8F, 1.0F, light, r, g, b);
        box(pose, vc, uv, uv, -1.3F, -1.2F, 1.0F, 1.3F, 1.4F, 1.9F, light, r - 10, g - 6, b - 4);
        box(pose, vc, uv, uv, -0.75F, -0.55F, 1.9F, 0.75F, 0.95F, 2.6F, light, r - 18, g - 10, b - 6);
        box(pose, vc, uv, uv, -1.05F, -0.85F, 2.6F, 1.05F, 1.25F, 2.95F, light, r - 26, g - 16, b - 10);
        box(pose, vc, uv, uv, 0.75F, 0.1F, 1.3F, 1.25F, 0.5F, 2.4F, light, r - 30, g - 20, b - 14);
        box(pose, vc, uv, uv, -1.25F, 0.1F, 1.3F, -0.75F, 0.5F, 2.4F, light, r - 30, g - 20, b - 14);
    }

    /** Coffre englouti : caisse de chene a demi enfouie, couvercle entrouvert, ferrures, serrure et reflet d'or. */
    static void chest(PoseStack pose, VertexConsumer vc, float[] wood, float[] iron, float[] gold, float turn, int light) {
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        pose.mulPose(Axis.ZP.rotationDegrees(5F));
        box(pose, vc, wood, wood, -2.5F, 0, -1.8F, 2.5F, 2.9F, 1.8F, light, 196, 170, 130);
        box(pose, vc, gold, gold, -2.1F, 2.9F, -1.4F, 2.1F, 3.3F, 1.4F, FULL_BRIGHT, 255, 226, 120);
        box(pose, vc, iron, iron, -2.65F, 0.6F, -1.95F, -2.15F, 3.0F, 1.95F, light, 96, 100, 108);
        box(pose, vc, iron, iron, 2.15F, 0.6F, -1.95F, 2.65F, 3.0F, 1.95F, light, 96, 100, 108);
        box(pose, vc, gold, gold, -0.45F, 1.7F, 1.8F, 0.45F, 2.8F, 2.05F, light, 230, 196, 90);
        //couvercle charniere a l'arriere (z -1,8), entrouvert de 24 degres
        pose.pushPose();
        pose.translate(0, 3.0F / 16F, -1.8F / 16F);
        pose.mulPose(Axis.XP.rotationDegrees(-24F));
        box(pose, vc, wood, wood, -2.5F, 0, 0, 2.5F, 1.3F, 3.6F, light, 186, 160, 122);
        box(pose, vc, iron, iron, -2.65F, -0.05F, -0.1F, -2.15F, 1.4F, 3.7F, light, 96, 100, 108);
        box(pose, vc, iron, iron, 2.15F, -0.05F, -0.1F, 2.65F, 1.4F, 3.7F, light, 96, 100, 108);
        pose.popPose();
    }

    /** Bulles du coffre englouti {x, y, z, rayon} (px, repere de l'emplacement) qui montent du coffre jusqu'a {@code top}. */
    public static float[][] bubbles(int seed, float top, double time) {
        float y0 = 3.2F, rise = Math.max(1F, top - y0);
        float[][] out = new float[3][];
        for (int b = 0; b < 3; b++) {
            float t = (float) ((time * 0.11 + b * rise / 3F + 7F * AquariumDecor.rnd(seed, 150)) % rise);
            out[b] = new float[]{(float) (0.3 * Math.sin(time * 0.3 + b * 2.1)), y0 + t, 0.4F, 0.35F + 0.15F * b};
        }
        return out;
    }

    // ------------------------------------------------------------------ primitives

    /** Boite (px) sans face du dessous ; flancs ombres ; {@code side} : texture des flancs, {@code top} : dessus (et bouts en z). */
    static void box(PoseStack pose, VertexConsumer vc, float[] side, float[] top, float x0, float y0, float z0, float x1, float y1, float z1, int light,
          int r, int g, int b) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float su0 = side[0], su1 = lerp(side[0], side[2], Math.min(1, (x1 - x0) / 16F)), sv0 = lerp(side[1], side[3], 1 - Math.min(1, (y1 - y0) / 16F));
        float sw1 = lerp(side[0], side[2], Math.min(1, (z1 - z0) / 16F));
        float tu1 = lerp(top[0], top[2], Math.min(1, (x1 - x0) / 16F)), tv1 = lerp(top[1], top[3], Math.min(1, (z1 - z0) / 16F));
        quad(vc, m, n, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, top[0], top[1], tu1, tv1, r, g, b, 255, light);
        quad(vc, m, n, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1, su0, sv0, su1, side[3], r * 210 / 255, g * 210 / 255, b * 210 / 255, 255, light);
        quad(vc, m, n, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1, su0, sv0, su1, side[3], r * 210 / 255, g * 210 / 255, b * 210 / 255, 255, light);
        quad(vc, m, n, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0, su0, sv0, sw1, side[3], r * 180 / 255, g * 180 / 255, b * 180 / 255, 255, light);
        quad(vc, m, n, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0, su0, sv0, sw1, side[3], r * 180 / 255, g * 180 / 255, b * 180 / 255, 255, light);
    }

    static float[] sub(float[] uv, float a, float b, float c, float d) {
        return new float[]{lerp(uv[0], uv[2], a / 16F), lerp(uv[1], uv[3], b / 16F), lerp(uv[0], uv[2], c / 16F), lerp(uv[1], uv[3], d / 16F)};
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float u(float[] uv, float px) {
        return lerp(uv[0], uv[2], Math.max(0, Math.min(16, px)) / 16F);
    }

    static float v(float[] uv, float px) {
        return lerp(uv[1], uv[3], Math.max(0, Math.min(16, px)) / 16F);
    }

    /** Quadrilatere en px (a, b, c, d) : UV a = (u0, v1), b = (u0, v0), c = (u1, v0), d = (u1, v1). */
    public static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
          float cz, float dx, float dy, float dz, float nx, float ny, float nz, float u0, float v0, float u1, float v1, int r, int g, int b, int a,
          int light) {
        vertex(vc, m, n, ax, ay, az, u0, v1, nx, ny, nz, r, g, b, a, light);
        vertex(vc, m, n, bx, by, bz, u0, v0, nx, ny, nz, r, g, b, a, light);
        vertex(vc, m, n, cx, cy, cz, u1, v0, nx, ny, nz, r, g, b, a, light);
        vertex(vc, m, n, dx, dy, dz, u1, v1, nx, ny, nz, r, g, b, a, light);
    }

    static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float nx, float ny, float nz, int r, int g,
          int b, int a, int light) {
        vc.vertex(m, x / 16F, y / 16F, z / 16F).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz)
              .endVertex();
    }
}
