package fr.pioneers.client.nuclear;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.nuclear.ReactorView;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

/**
 * Plasmas de fusion (0.17.2), dessines dans le volume creux du tore par {@link RenderReactorCore} :
 * <ul>
 *     <li>tokamak (mark 4) : anneau de plasma rose-violet a section en D (elongation, triangularite), lignes de champ qui s'enroulent
 *     (facteur de securite 3) et defilent, lueur des points d'impact du divertor en bas de la chambre ; eclair blanc a l'ignition, flash puis
 *     extinction a la disruption (chute brutale de la puissance) ;</li>
 *     <li>stellarator (mark 5) : ruban de plasma cyan dont la section elliptique tourne cinq fois par tour (cinq periodes) et dont l'axe
 *     magnetique ondule, filaments des surfaces magnetiques, coeur blanc tres lumineux.</li>
 * </ul>
 * Geometrie tiree des bornes de la structure : rayon exterieur Wh = demi-largeur, trou ~ Wh/3, rayon majeur R = 2 Wh / 3, demi-epaisseur
 * radiale de la chambre Wh/3 - 1, demi-hauteur interieure. Formules rejouees par {@code tools/gc/nuclear/core_bench.py}.
 */
final class RenderFusion {

    /** Etat client par controleur : puissance precedente, debut et nature du dernier evenement (1 = ignition, 2 = disruption). */
    private static final class Fx {
        float last;
        float eventStart = -1000;
        int event;
        float seen;
    }

    private static final Map<Long, Fx> FX = new HashMap<>();

    private RenderFusion() {
    }

    /** Geometrie du tore (coordonnees du controleur). */
    record Torus(float cx, float cy, float cz, float major, float minorR, float minorY) {

        /** 1.0 (lot 3 bis) : anneau construit : grand rayon = demi-largeur - 4,5 ; chambre fine (plasma de rayon 0,65 ; heliotron 0,9). */
        static Torus of(RenderReactorCore.Frame f, boolean tokamak) {
            float wh = Math.min(f.x1() - f.x0(), f.z1() - f.z0()) * 0.5F + 1;
            float major = Math.max(2, wh - 4.5F);
            //0.19.15 : heliotron : plasma plus epais (chambre de 1,2 bloc de rayon) ; centre du tube pris sur le gabarit (y = 4 de 8, 4,5 de 9 :
            //le sommet de l'heliotron n'a aucun bloc, la boite des blocs etait plus basse d'un bloc et le plasma descendait d'un demi-bloc)
            float minorR = tokamak ? 0.65F : 0.9F;
            float minorY = tokamak ? 0.65F * 1.5F / 0.85F : 0.9F;
            return new Torus(f.cx(), f.y0() + (tokamak ? 4.0F : 3.5F), f.cz(), major, minorR, minorY);
        }
    }

    /** Suit la puissance pour reperer ignition et disruption ; renvoie l'etat. */
    private static Fx track(BlockPos controller, ReactorView v, float t) {
        Fx fx = FX.computeIfAbsent(controller.asLong(), k -> new Fx());
        float p = v.power();
        if (fx.last < 0.04F && p >= 0.04F && v.heat() >= 0.9F) {
            fx.event = 1;
            fx.eventStart = t;
        } else if (fx.last > 0.25F && p < fx.last * 0.5F) {
            fx.event = 2;
            fx.eventStart = t;
        }
        fx.last = p;
        fx.seen = t;
        if (FX.size() > 64) {
            for (Iterator<Fx> it = FX.values().iterator(); it.hasNext(); ) {
                if (Math.abs(t - it.next().seen) > 2400) {
                    it.remove();
                }
            }
        }
        return fx;
    }

    /** Point d'une section en D (theta : angle poloidal) autour de l'axe toroidal phi. */
    static float[] dPoint(Torus g, double phi, double theta, float scale, float kappa, float delta) {
        double rr = g.major() + g.minorR() * scale * Math.cos(theta + delta * Math.sin(theta));
        double y = g.minorY() * scale * kappa * Math.sin(theta);
        return new float[]{g.cx() + (float) (Math.cos(phi) * rr), g.cy() + (float) y, g.cz() + (float) (Math.sin(phi) * rr)};
    }

    // ------------------------------------------------------------------
    // Tokamak
    // ------------------------------------------------------------------

    static void tokamak(RenderReactorCore.Frame f, Matrix4f m, MultiBufferSource buffers, BlockPos controller) {
        ReactorView v = f.view();
        Torus g = Torus.of(f, true);
        float t = f.t();
        float p = RenderReactorCore.clamp(v.power()), heat = Math.max(0, v.heat()), field = RenderReactorCore.clamp(v.coolant());
        Fx fx = track(controller, v, t);
        float since = t - fx.eventStart;
        float flash = since < 30 ? (float) Math.exp(-since / 8) : 0;
        boolean disrupt = fx.event == 2 && since < 60;
        // luminosite : chauffage (avant ignition) = lueur violette sombre ; combustion = rose vif a coeur blanc
        float warm = RenderReactorCore.clamp(heat) * 0.35F;
        float burn = p;
        float base = Math.max(warm, burn) * (disrupt ? Math.max(0, 1 - since / 20F) : 1);
        if (base < 0.02F && flash < 0.02F) {
            return;
        }
        int nt = f.lod() == 2 ? 40 : f.lod() == 1 ? 28 : 18, np = f.lod() == 2 ? 10 : 8;
        float kappa = 0.85F, delta = 0.35F;
        // --- 0.19.15 : corps du plasma (translucide, lumineux) qui donne de la matiere : on ne voit plus au travers
        VertexConsumer b = buffers.getBuffer(RenderType.entityTranslucentEmissive(RenderReactorCore.PLASMA_BODY));
        float body = Math.min(0.85F, 0.25F + base * 0.75F);
        torusMesh(b, m, g, nt, np, 0.66F, kappa, delta, 0, t * 0.015F, disrupt ? 1 : 1.0F, disrupt ? 0.9F : 0.55F, disrupt ? 0.85F : 0.9F, body, false);
        // --- couches du plasma (texture qui defile le long du tore)
        b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.PLASMA));
        float[] scale = {0.35F, 0.6F, 0.82F}, share = {1.4F, 0.85F, 0.45F};
        float[][] col = {{1.0F, 0.85F, 1.0F}, {1.0F, 0.42F, 0.85F}, {0.62F, 0.3F, 1.0F}};
        for (int k = 0; k < 3; k++) {
            float i = base * share[k] * (0.85F + 0.15F * (float) Math.sin(t * 0.13F + k));
            float cr = RenderReactorCore.mix(col[k][0], 0.55F, burn < 0.05F ? 0.6F : 0), cg = col[k][1], cb = col[k][2];
            if (disrupt) {
                cr = 1;
                cg = 0.9F;
                cb = 0.85F;
            }
            torusMesh(b, m, g, nt, np, scale[k], kappa, delta, 0, t * 0.01F * (k + 1), cr * i, cg * i, cb * i, k == 2);
        }
        // --- lignes de champ (q = 3) qui defilent, et lueur du divertor
        if (f.lod() > 0) {
            b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.FILAMENT));
            int lines = f.lod() == 2 ? 6 : 4, seg = f.lod() == 2 ? 80 : 48;
            for (int l = 0; l < lines; l++) {
                float[][] pts = new float[seg + 1][];
                double th0 = 2 * Math.PI * l / lines + t * 0.02;
                for (int j = 0; j <= seg; j++) {
                    double phi = 2 * Math.PI * j / seg;
                    pts[j] = dPoint(g, phi, th0 + phi / 3, 0.7F, kappa, delta);
                }
                float i = base * 0.5F;
                CoreDraw.ribbon(b, m, pts, 0.03F, f.camera(), i, 0.55F * i, 0.9F * i, RenderReactorCore.FULL);
            }
        }
        b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.GLOW));
        float div = burn * (0.7F + 0.3F * (float) Math.sin(t * 0.5F));
        if (div > 0.02F) {
            float yb = g.cy() - g.minorY() * kappa * 0.95F;
            int n = f.lod() == 2 ? 36 : 18;
            for (int j = 0; j < n; j++) {
                double phi = 2 * Math.PI * j / n + t * 0.003;
                for (int side = -1; side <= 1; side += 2) {
                    float rr = g.major() + side * g.minorR() * 0.35F;
                    float s = 0.25F + 0.1F * (float) Math.sin(t * 0.7 + j * 1.7 + side);
                    CoreDraw.billboard(b, m, g.cx() + (float) Math.cos(phi) * rr, yb, g.cz() + (float) Math.sin(phi) * rr, s, f.left(), f.up(),
                          div, 0.55F * div, 0.75F * div, RenderReactorCore.FULL);
                }
            }
        }
        // --- eclair d'ignition (blanc) ou flash de disruption (blanc-jaune qui s'etend)
        if (flash > 0.02F) {
            b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.RING));
            float grow = fx.event == 2 ? 1 + since / 10F : 1;
            CoreDraw.ring(b, m, g.cx(), g.cy(), g.cz(), g.major(), g.minorR() * 0.9F * grow, 0, 0, t * 0.1F, nt, flash, flash, flash * 0.95F,
                  RenderReactorCore.FULL);
            b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.GLOW));
            CoreDraw.billboard(b, m, g.cx(), g.cy(), g.cz(), g.major() * 1.3F * grow, f.left(), f.up(), flash, 0.9F * flash, 0.95F * flash,
                  RenderReactorCore.FULL);
        }
        // champ faible : le plasma tremble (lueur rouge sur les bords)
        if (field < 0.4F && base > 0.1F && f.lod() > 0) {
            b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.HAZE));
            float w = (0.4F - field) * 2 * base * (0.5F + 0.5F * (float) Math.sin(t * 1.3F));
            torusMesh(b, m, g, nt / 2, np, 0.95F, kappa, delta, 0, t * 0.05F, w, 0.15F * w, 0.2F * w, true);
        }
    }

    // ------------------------------------------------------------------
    // Stellarator
    // ------------------------------------------------------------------

    static void stellarator(RenderReactorCore.Frame f, Matrix4f m, MultiBufferSource buffers, BlockPos controller) {
        ReactorView v = f.view();
        Torus g = Torus.of(f, false);
        float t = f.t();
        float p = RenderReactorCore.clamp(v.power()), heat = Math.max(0, v.heat()), field = RenderReactorCore.clamp(v.coolant());
        Fx fx = track(controller, v, t);
        float since = t - fx.eventStart;
        float flash = since < 30 ? (float) Math.exp(-since / 8) : 0;
        float base = Math.max(RenderReactorCore.clamp(heat) * 0.35F, p) * (fx.event == 2 && since < 60 ? Math.max(0, 1 - since / 20F) : 1);
        if (base < 0.02F && flash < 0.02F) {
            return;
        }
        int nt = f.lod() == 2 ? 64 : f.lod() == 1 ? 40 : 24, np = f.lod() == 2 ? 10 : 8;
        // --- 0.19.15 : corps du plasma (translucide, lumineux) qui donne de la matiere
        VertexConsumer b = buffers.getBuffer(RenderType.entityTranslucentEmissive(RenderReactorCore.PLASMA_BODY));
        float body = Math.min(0.85F, 0.25F + base * 0.75F);
        stellaratorMesh(b, m, g, nt, np, 0.66F, t * 0.015F, 0.45F, 0.9F, 1.0F, body, false);
        b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.PLASMA));
        float[] scale = {0.32F, 0.58F, 0.85F}, share = {2.0F, 1.3F, 0.75F};
        float[][] col = {{0.9F, 1.0F, 1.0F}, {0.35F, 0.9F, 1.0F}, {0.25F, 0.55F, 1.0F}};
        for (int k = 0; k < 3; k++) {
            float i = base * share[k] * (0.9F + 0.1F * (float) Math.sin(t * 0.17F + k));
            stellaratorMesh(b, m, g, nt, np, scale[k], t * 0.012F * (k + 1), col[k][0] * i, col[k][1] * i, col[k][2] * i, k == 2);
        }
        // --- filaments des surfaces magnetiques (transformee rotationnelle ~ 0,9) : ils suivent la torsion
        if (f.lod() > 0) {
            b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.FILAMENT));
            int lines = f.lod() == 2 ? 6 : 4, seg = f.lod() == 2 ? 110 : 60;
            for (int l = 0; l < lines; l++) {
                float sc = 0.45F + 0.4F * (l % 3) / 2F;
                float[][] pts = new float[seg + 1][];
                double th0 = 2 * Math.PI * l / lines - t * 0.025;
                for (int j = 0; j <= seg; j++) {
                    double phi = 2 * Math.PI * j / seg;
                    pts[j] = stellaratorPoint(g, phi, th0 + 0.9 * phi, sc);
                }
                float i = base * 0.55F;
                CoreDraw.ribbon(b, m, pts, 0.03F, f.camera(), 0.5F * i, i, i, RenderReactorCore.FULL);
            }
        }
        // --- coeur lumineux le long de l'axe magnetique
        b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.GLOW));
        int n = f.lod() == 2 ? 30 : 15;
        for (int j = 0; j < n; j++) {
            double phi = 2 * Math.PI * j / n + t * 0.004;
            float[] c = stellaratorPoint(g, phi, 0, 0);
            float s = g.minorR() * (0.5F + 0.15F * (float) Math.sin(t * 0.3 + j));
            float i = base * 0.45F;
            CoreDraw.billboard(b, m, c[0], c[1], c[2], s, f.left(), f.up(), 0.6F * i, i, i, RenderReactorCore.FULL);
        }
        if (flash > 0.02F) {
            CoreDraw.billboard(b, m, g.cx(), g.cy(), g.cz(), g.major() * 1.3F, f.left(), f.up(), 0.8F * flash, flash, flash, RenderReactorCore.FULL);
        }
        if (field < 0.4F && base > 0.1F) {
            float w = (0.4F - field) * 2 * base * (0.5F + 0.5F * (float) Math.sin(t * 1.3F));
            for (int j = 0; j < n; j++) {
                double phi = 2 * Math.PI * (j + 0.5) / n;
                float[] c = stellaratorPoint(g, phi, t * 0.2 + j, 0.9F);
                CoreDraw.billboard(b, m, c[0], c[1], c[2], 0.3F, f.left(), f.up(), w, 0.2F * w, 0.25F * w, RenderReactorCore.FULL);
            }
        }
    }

    /**
     * Point de la surface du stellarator : axe magnetique qui ondule (cinq periodes), section elliptique (rapport 2,2) qui tourne d'un
     * demi-tour par periode.
     */
    static float[] stellaratorPoint(Torus g, double phi, double theta, float scale) {
        double wob = 0.18 * g.minorR();
        double axisR = g.major() + wob * Math.cos(5 * phi), axisY = 0.18 * g.minorY() * Math.sin(5 * phi);
        double rot = 2.5 * phi;
        double a = g.minorR() * scale * 0.95, bb = g.minorR() * scale * 0.43;
        double u = a * Math.cos(theta), w = bb * Math.sin(theta);
        double dr = u * Math.cos(rot) - w * Math.sin(rot), dy = u * Math.sin(rot) + w * Math.cos(rot);
        dy *= g.minorY() / Math.max(0.6, g.minorR());
        double rr = axisR + dr;
        return new float[]{g.cx() + (float) (Math.cos(phi) * rr), g.cy() + (float) (axisY + dy), g.cz() + (float) (Math.sin(phi) * rr)};
    }

    // ------------------------------------------------------------------
    // Maillages
    // ------------------------------------------------------------------

    private static void torusMesh(VertexConsumer b, Matrix4f m, Torus g, int nt, int np, float scale, float kappa, float delta, float du, float dv,
          float r, float gg, float bl, boolean both) {
        torusMesh(b, m, g, nt, np, scale, kappa, delta, du, dv, r, gg, bl, 1, both);
    }

    private static void torusMesh(VertexConsumer b, Matrix4f m, Torus g, int nt, int np, float scale, float kappa, float delta, float du, float dv,
          float r, float gg, float bl, float alpha, boolean both) {
        for (int i = 0; i < nt; i++) {
            double p0 = 2 * Math.PI * i / nt, p1 = 2 * Math.PI * (i + 1) / nt;
            for (int j = 0; j < np; j++) {
                double t0 = 2 * Math.PI * j / np, t1 = 2 * Math.PI * (j + 1) / np;
                float u0 = 6F * i / nt + dv, u1 = 6F * (i + 1) / nt + dv, v0 = (float) j / np + du, v1 = (float) (j + 1) / np + du;
                float[] a = dPoint(g, p0, t0, scale, kappa, delta), c = dPoint(g, p1, t0, scale, kappa, delta);
                float[] d = dPoint(g, p1, t1, scale, kappa, delta), e = dPoint(g, p0, t1, scale, kappa, delta);
                CoreDraw.quad(b, m, new float[][]{{a[0], a[1], a[2], u0, v0}, {c[0], c[1], c[2], u1, v0}, {d[0], d[1], d[2], u1, v1},
                      {e[0], e[1], e[2], u0, v1}}, r, gg, bl, alpha, RenderReactorCore.FULL, both);
            }
        }
    }

    private static void stellaratorMesh(VertexConsumer b, Matrix4f m, Torus g, int nt, int np, float scale, float dv, float r, float gg, float bl,
          boolean both) {
        stellaratorMesh(b, m, g, nt, np, scale, dv, r, gg, bl, 1, both);
    }

    private static void stellaratorMesh(VertexConsumer b, Matrix4f m, Torus g, int nt, int np, float scale, float dv, float r, float gg, float bl,
          float alpha, boolean both) {
        for (int i = 0; i < nt; i++) {
            double p0 = 2 * Math.PI * i / nt, p1 = 2 * Math.PI * (i + 1) / nt;
            for (int j = 0; j < np; j++) {
                double t0 = 2 * Math.PI * j / np, t1 = 2 * Math.PI * (j + 1) / np;
                float u0 = 10F * i / nt + dv, u1 = 10F * (i + 1) / nt + dv, v0 = (float) j / np, v1 = (float) (j + 1) / np;
                float[] a = stellaratorPoint(g, p0, t0, scale), c = stellaratorPoint(g, p1, t0, scale);
                float[] d = stellaratorPoint(g, p1, t1, scale), e = stellaratorPoint(g, p0, t1, scale);
                CoreDraw.quad(b, m, new float[][]{{a[0], a[1], a[2], u0, v0}, {c[0], c[1], c[2], u1, v0}, {d[0], d[1], d[2], u1, v1},
                      {e[0], e[1], e[2], u0, v1}}, r, gg, bl, alpha, RenderReactorCore.FULL, both);
            }
        }
    }
}
