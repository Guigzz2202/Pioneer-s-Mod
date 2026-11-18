package fr.pioneers.tech.industry.client.big;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * 0.20.34 : le cosmos industriel (demande de Guillaume : « supprimer le compresseur gravitationnel, le passer en multibloc complexe et
 * anime, pareil pour le synthetiseur »).
 * <ul>
 *     <li>{@link #gravity} : compresseur gravitationnel (sphere) : six pylones a gravitons pointes vers le centre, anneau de stator ;
 *     en marche, un coeur incandescent qui pulse, trois anneaux gyroscopiques inclines qui tournent et precessent, de la matiere aspiree
 *     en spirale et des arcs entre les pylones et le coeur ;</li>
 *     <li>{@link #synth} : synthetiseur (dome) : plateau, berceau a griffes, six projecteurs en couronne, projecteur de plafond ; en marche,
 *     une planete miniature qui tourne (oceans, continents, calottes), son atmosphere translucide, des paquets de donnees qui filent des
 *     projecteurs vers la planete, un anneau de balayage et trois satellites en orbite inclinee.</li>
 * </ul>
 */
final class Lot10 {

    private static final int ORANGE = 0xFF8A1F, CYAN = 0x59E3FF;

    private Lot10() {
    }

    private static int argb(int rgb, int alpha) {
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    /** Anneau de {@code n} segments tangents dans le plan XZ autour de (cx, cy, cz), puis transforme par xf. */
    private static void ring(BigMesh m, float cx, float cy, float cz, float r, float depth, float thick, int n, BigMat mat, @Nullable Matrix4f xf) {
        float sw = (float) (2 * r * Math.tan(Math.PI / n) + 0.05);
        for (int k = 0; k < n; k++) {
            Matrix4f seg = BigMesh.rot('y', cx, 0, cz, k * 360F / n);
            m.box(cx - sw / 2, cy - thick / 2, cz + r - depth, cx + sw / 2, cy + thick / 2, cz + r, mat, BigMesh.chain(seg, xf), 4);
        }
    }

    /** Boule en tranches horizontales (opaque : cylindres pleins ; translucide : peaux seules). */
    private static void ball(BigMesh m, float cx, float cy, float cz, float r, BigMat mat, int slices, @Nullable Matrix4f xf) {
        float step = 2 * r / slices;
        for (int i = 0; i < slices; i++) {
            float y0 = cy - r + i * step, y1 = y0 + step, ym = (y0 + y1) / 2 - cy;
            float rr = (float) Math.sqrt(Math.max(0.25F, r * r - ym * ym));
            if (mat.translucent()) {
                m.cyl('y', cx, cz, rr, y0, y1, mat, 16, xf, 4, false, 0.4F);
            } else {
                m.cyl('y', cx, cz, rr, y0, y1, mat, 12, xf, 4, true, Math.max(0.6F, rr * 0.3F));
            }
        }
    }

    // ------------------------------------------------------------------ compresseur gravitationnel

    static void gravity(BigMesh m, float w, float h, float l) {
        float c = w / 2, cy = h / 2, cz = l / 2;
        float inner = 38, outer = Math.min(c, cy) - 4;
        //Socle et pylones a gravitons : quatre a l'equateur, un en haut, un en bas, pointes vers le centre
        m.cyl('y', c, cz, 16, 0, 2, BigMat.CAST, 16, null, 4);
        for (int k = 0; k < 4; k++) {
            Matrix4f r = BigMesh.rot('y', c, 0, cz, 90 * k + 45);
            m.box(c - 3, cy - 3, cz + inner + 2, c + 3, cy + 3, cz + outer, BigMat.STEEL_D, r, 8);
            m.box(c - 4, cy - 4, cz + inner + 5, c + 4, cy + 4, cz + inner + 9, BigMat.CHROME, r, 4);           //bobine
            m.box(c - 4, cy - 4, cz + outer - 6, c + 4, cy + 4, cz + outer - 2, BigMat.CHROME, r, 4);
            m.glow(12);
            m.box(c - 2, cy - 2, cz + inner, c + 2, cy + 2, cz + inner + 2, BigMat.GLOWROD, r, 2);             //pointe
            m.glow(0);
        }
        for (int s = -1; s <= 1; s += 2) {
            float y0 = s < 0 ? 2 : cy + inner + 2, y1 = s < 0 ? cy - inner - 2 : h - 2;
            m.box(c - 3, y0, cz - 3, c + 3, y1, cz + 3, BigMat.STEEL_D, null, 8);
            float yc = s < 0 ? cy - inner - 9 : cy + inner + 5;
            m.box(c - 4, yc, cz - 4, c + 4, yc + 4, cz + 4, BigMat.CHROME, null, 4);
            m.glow(12);
            float yt = s < 0 ? cy - inner - 2 : cy + inner;
            m.box(c - 2, yt, cz - 2, c + 2, yt + 2, cz + 2, BigMat.GLOWROD, null, 2);
            m.glow(0);
        }
        //Stator : anneau equatorial et deux anneaux de garde
        ring(m, c, cy, cz, inner + 5, 2, 3, 32, BigMat.STEEL_D, null);
        ring(m, c, cy + 18, cz, inner, 1.5F, 1.5F, 28, BigMat.CAST_L, null);
        ring(m, c, cy - 18, cz, inner, 1.5F, 1.5F, 28, BigMat.CAST_L, null);
    }

    static void gravityLife(BigMesh m, float w, float h, float l, BigMachines.Anim a) {
        float c = w / 2, cy = h / 2, cz = l / 2, s = a.speed, ph = a.phase;
        float inner = 38;
        //Coeur : sombre a l'arret, incandescent et pulsant en marche
        float pulse = 1 + 0.18F * s * (float) Math.sin(ph * 0.09F);
        float rc = (5 + 3 * s) * pulse;
        m.glow(Math.round(4 + 11 * s));
        ball(m, c, cy, cz, rc, s > 0.05F ? BigMat.MOLTEN : BigMat.SLAG, 6, null);
        m.glow(0);
        if (s > 0.05F) {
            m.color(argb(ORANGE, Math.round(0x30 + 0x40 * s))).glow(15);
            ball(m, c, cy, cz, rc * 1.9F, BigMat.GLASS, 7, null);                                              //halo
            m.color(0xFFFFFFFF).glow(0);
        }
        //Anneaux gyroscopiques : rotation dans leur plan, inclinaison, precession
        float[] tilt = {25, 70, 115};
        float[] spin = {3.2F, -2.4F, 1.7F};
        for (int i = 0; i < 3; i++) {
            float r = 20 + 6.5F * i;
            Matrix4f xf = BigMesh.chain(BigMesh.chain(BigMesh.rot('y', c, cy, cz, ph * spin[i]), BigMesh.rot('x', c, cy, cz, tilt[i])),
                  BigMesh.rot('y', c, cy, cz, ph * 0.35F * (i + 1) + 40 * i));
            ring(m, c, cy, cz, r, 1.6F, 2.4F, 22, BigMat.CHROME, xf);
            m.glow(Math.round(15 * s)).color(argb(ORANGE, 0xFF));
            ring(m, c, cy, cz, r - 1.6F, 0.4F, 1.2F, 22, BigMat.LED, xf);
            m.glow(0).color(0xFFFFFFFF);
        }
        if (s <= 0.05F) {
            return;
        }
        //Matiere aspiree en spirale (roches qui s'echauffent pres du coeur)
        int n = Math.round(40 * s);
        for (int k = 0; k < n; k++) {
            float u = (ph * 0.006F + k / 40F) % 1F;
            float rr = 40 * (1 - u) + rc;
            double an = k * 2.4 + u * Math.PI * 4;
            float y = cy + (float) Math.sin(k * 1.7) * (1 - u) * 18;
            float x = c + (float) Math.cos(an) * rr, z = cz + (float) Math.sin(an) * rr, d = 1.4F * (1 - u) + 0.4F;
            boolean hot = u > 0.7F;
            m.glow(hot ? 15 : 0);
            m.box(x - d, y - d, z - d, x + d, y + d, z + d, hot ? BigMat.FLAME_C : k % 2 == 0 ? BigMat.ORE1 : BigMat.ORE2, null, 1);
        }
        m.glow(0);
        //Arcs des pointes vers le coeur (crepitent)
        float[][] tips = new float[6][];
        for (int k = 0; k < 4; k++) {
            double an = Math.toRadians(90 * k + 45);
            tips[k] = new float[]{c + (float) Math.sin(an) * inner, cy, cz + (float) Math.cos(an) * inner};
        }
        tips[4] = new float[]{c, cy + inner, cz};
        tips[5] = new float[]{c, cy - inner, cz};
        m.glow(15).color(argb(0xFFD8A0, 0xFF));
        for (int k = 0; k < 6; k++) {
            int tick = (int) (ph / 2);
            if (BigMachines.hash(k, tick) > s * 0.45F) {
                continue;
            }
            for (int j = 1; j < 10; j++) {
                float u = j / 10F;
                float jx = (BigMachines.hash(k * 31 + j, tick) - 0.5F) * 5, jy = (BigMachines.hash(k * 17 + j, tick + 3) - 0.5F) * 5;
                float x = tips[k][0] + (c - tips[k][0]) * u + jx, y = tips[k][1] + (cy - tips[k][1]) * u + jy;
                float z = tips[k][2] + (cz - tips[k][2]) * u - jx;
                m.box(x - 0.5F, y - 0.5F, z - 0.5F, x + 0.5F, y + 0.5F, z + 0.5F, BigMat.LED, null, 1);
            }
        }
        m.glow(0).color(0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ synthetiseur

    /** Centre (y) et rayon de la planete miniature. */
    private static float planetY(float h) {
        return Math.min(h * 0.46F, 46);
    }

    private static float planetR(float w) {
        return Math.min(12, w / 2 - 22);
    }

    static void synth(BigMesh m, float w, float h, float l) {
        float c = w / 2, cz = l / 2, py = planetY(h), rp = planetR(w);
        float rim = Math.min(c, cz) - 4;
        //Plateau, piste de donnees, socle et berceau a trois griffes
        m.cyl('y', c, cz, rim, 0, 2, BigMat.CAST_L, 24, null, 6);
        m.cyl('y', c, cz, rim * 0.5F, 2, 2.6F, BigMat.PCB, 16, null, 8);
        m.cyl('y', c, cz, 7, 2, 10, BigMat.CAST, 12, null, 4);
        m.cyl('y', c, cz, 4, 10, py - rp - 8, BigMat.STEEL_D, 10, null, 4);
        float cup = py - rp - 8;
        m.cyl('y', c, cz, 8, cup, cup + 2, BigMat.CHROME, 14, null, 2, false, 1.4F);
        for (int k = 0; k < 3; k++) {
            Matrix4f r = BigMesh.rot('y', c, 0, cz, 120 * k);
            m.box(c - 1, cup + 1, cz + 6.5F, c + 1, cup + 6, cz + 8.5F, BigMat.STEEL_D, r, 2);
            m.box(c - 1, cup + 6, cz + rp - 1, c + 1, cup + 7.5F, cz + 8.5F, BigMat.STEEL_D, r, 2);
        }
        //Couronne de six projecteurs : colonne, tete tournee vers la planete, pointe lumineuse
        float pr = rim - 4, top = py + 10;
        for (int k = 0; k < 6; k++) {
            Matrix4f r = BigMesh.rot('y', c, 0, cz, 60 * k + 30);
            m.box(c - 1.5F, 2, cz + pr - 1.5F, c + 1.5F, top, cz + pr + 1.5F, BigMat.STEEL_D, r, 8);
            m.box(c - 2.5F, top, cz + pr - 6, c + 2.5F, top + 4, cz + pr + 2.5F, BigMat.CHROME, r, 4);
            m.box(c - 1.2F, 2, cz + pr - 1.2F, c + 1.2F, 2.6F, cz + pr - 8, BigMat.PIPE, r, 4);                 //cable de donnees
            m.glow(10);
            m.box(c - 1.5F, top + 0.5F, cz + pr - 7, c + 1.5F, top + 3.5F, cz + pr - 6, BigMat.LED, r, 2);
            m.glow(0);
        }
        //Projecteur de plafond
        float roof = Math.min(h - 2, py + rp + 30);
        m.cyl('y', c, cz, 5, roof - 8, roof, BigMat.STEEL_D, 12, null, 4);
        m.cyl('y', c, cz, 7, roof - 9, roof - 8, BigMat.CHROME, 14, null, 2, false, 1);
        m.glow(10);
        m.cyl('y', c, cz, 2.5F, roof - 10, roof - 9, BigMat.LED, 10, null, 2);
        m.glow(0);
        if (roof < h - 2) {
            m.cyl('y', c, cz, 1.2F, roof, h, BigMat.PIPE, 8, null, 2);
        }
    }

    static void synthLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        float c = w / 2, cz = l / 2, py = planetY(h), rp = planetR(w), s = a.speed, ph = a.phase;
        float rim = Math.min(c, cz) - 4, pr = rim - 4, top = py + 10;
        //Planete : oceans, continents qui tournent avec elle, calottes
        float spin = ph * 1.1F;
        m.color(0xFF2E6BD8);
        ball(m, c, py, cz, rp, BigMat.FROTH, 8, null);
        m.color(0xFF4C9A3C);
        for (int k = 0; k < 7; k++) {
            float lat = -45 + 90 * BigMachines.hash(k, 401), lon = 360 * BigMachines.hash(k, 402) + spin;
            float sx = 2.5F + 3 * BigMachines.hash(k, 403), sy = 2 + 2.5F * BigMachines.hash(k, 404);
            Matrix4f xf = BigMesh.chain(BigMesh.rot('x', c, py, cz, -lat), BigMesh.rot('y', c, py, cz, lon));
            m.box(c - sx, py - sy, cz + rp - 1.2F, c + sx, py + sy, cz + rp + 0.5F, BigMat.FROTH, xf, 2);
        }
        m.color(0xFFF2F6FF);
        m.cyl('y', c, cz, rp * 0.42F, py + rp - 1.4F, py + rp + 0.4F, BigMat.FROTH, 12, null, 2);
        m.cyl('y', c, cz, rp * 0.36F, py - rp - 0.4F, py - rp + 1.4F, BigMat.FROTH, 12, null, 2);
        //Atmosphere : de plus en plus dense avec le regime
        m.color(argb(0x9CC8FF, Math.round(0x28 + 0x50 * s))).glow(Math.round(6 * s));
        ball(m, c, py, cz, rp + 2.6F, BigMat.GLASS, 8, null);
        m.color(0xFFFFFFFF).glow(0);
        if (s <= 0.05F) {
            return;
        }
        //Paquets de donnees : des pointes des projecteurs vers la planete
        m.glow(15).color(argb(CYAN, 0xFF));
        for (int k = 0; k < 6; k++) {
            double an = Math.toRadians(60 * k + 30);
            float sn = (float) Math.sin(an), cs = (float) Math.cos(an);
            float tx = c + sn * (pr - 7), ty = top + 2, tz = cz + cs * (pr - 7);
            float ex = c + sn * (rp + 3), ey = py, ez = cz + cs * (rp + 3);
            int n = Math.round(7 * s);
            for (int j = 0; j < n; j++) {
                float u = (ph * 0.03F + j / 7F + k * 0.11F) % 1F;
                float x = tx + (ex - tx) * u, y = ty + (ey - ty) * u, z = tz + (ez - tz) * u, d = 0.45F;
                m.box(x - d, y - d, z - d, x + d, y + d, z + d, BigMat.LED, null, 1);
            }
        }
        //Anneau de balayage qui parcourt la planete
        float dy = rp * (float) Math.sin(ph * 0.035F);
        float rs = (float) Math.sqrt(Math.max(1, rp * rp - dy * dy)) + 3.2F;
        m.color(argb(tint, 0xFF));
        m.cyl('y', c, cz, rs, py + dy - 0.2F, py + dy + 0.2F, BigMat.LED, 24, null, 2, false, 0.3F);
        //Faisceau du plafond
        float roof = Math.min(h - 2, py + rp + 30);
        m.color(argb(tint, Math.round(0x40 * s)));
        m.cyl('y', c, cz, 2, py + rp + 3, roof - 10, BigMat.GLASS, 10, null, 2, false, 0.3F);
        m.color(0xFFFFFFFF).glow(0);
        //Satellites en orbite inclinee : corps, panneaux, feu
        for (int i = 0; i < 3; i++) {
            double t = ph * 0.045 * (1 + 0.35 * i) + i * 2.1;
            double inc = Math.toRadians(-35 + 35 * i);
            float rr = rp + 7 + 2.5F * i;
            float ox = (float) Math.cos(t) * rr, oz0 = (float) Math.sin(t) * rr;
            float x = c + ox, y = py + oz0 * (float) Math.sin(inc), z = cz + oz0 * (float) Math.cos(inc);
            m.box(x - 0.9F, y - 0.9F, z - 0.9F, x + 0.9F, y + 0.9F, z + 0.9F, BigMat.CHROME, null, 1);
            m.color(0xFF3A5AA8);
            m.box(x - 3.4F, y - 0.15F, z - 0.7F, x - 1, y + 0.15F, z + 0.7F, BigMat.FROTH, null, 1);
            m.box(x + 1, y - 0.15F, z - 0.7F, x + 3.4F, y + 0.15F, z + 0.7F, BigMat.FROTH, null, 1);
            m.color(0xFFFFFFFF);
            if (BigMachines.hash(i, (int) (ph / 10)) < 0.6F) {
                m.glow(15);
                m.box(x - 0.3F, y + 0.9F, z - 0.3F, x + 0.3F, y + 1.5F, z + 0.3F, BigMat.LED, null, 1);
                m.glow(0);
            }
        }
        //Voyants de la piste de donnees au sol
        for (int k = 0; k < 12; k++) {
            double an = Math.toRadians(30 * k);
            float r = rim * 0.5F - 1.5F, x = c + (float) Math.sin(an) * r, z = cz + (float) Math.cos(an) * r;
            boolean on = BigMachines.hash(k, (int) (ph / 4)) < 0.5F + 0.4F * s;
            m.glow(on ? 15 : 0).color(on ? argb(CYAN, 0xFF) : 0xFF30404A);
            m.box(x - 0.6F, 2.6F, z - 0.6F, x + 0.6F, 3.1F, z + 0.6F, BigMat.LED, null, 1);
        }
        m.glow(0).color(0xFFFFFFFF);
    }
}
