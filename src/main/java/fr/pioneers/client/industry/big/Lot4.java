package fr.pioneers.client.industry.big;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 0.19.18 : machines interieures du lot 4 : presses a portique (presse, presse hydraulique, presse plieuse, presse a granules) et chaines
 * d'usinage (tour, assembleur, soudage, pose de composants, scierie, presse a injecter, trieur optique, coulee, embouteilleuse).
 * <p>
 * Presses : l'interieur est le coulisseau suspendu sous le sommier ; la rangee libre dessous (y de -16 a 0) recoit la matrice et la piece.
 * Chaines : l'interieur est la rangee posee sur le banc (16 px) ; la rangee libre au-dessus monte jusqu'aux traverses des portiques
 * (y = 32), ou sont suspendus les postes ({@code stands} = positions des portiques le long de l'axe). Les pieces avancent pas a pas
 * (transfert puis arret sous les postes, qui travaillent pendant l'arret) ; scierie et trieur en continu.
 */
final class Lot4 {

    /** Dessus de la bande (px). */
    private static final float YB = 6;
    /** Dessous des traverses des portiques (px). */
    private static final float TOP = 32;
    /** Pas des pieces sur la bande et duree d'un cycle de transfert + arret (unites de phase). */
    private static final float PITCH = 16, PERIOD = 64, MOVE = 0.35F;

    private Lot4() {
    }

    // ------------------------------------------------------------------ outils

    static float clamp01(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3 - 2 * t);
    }

    /** Interpolation douce de a vers b sur la fenetre [t0, t1] de t. */
    private static float ease(float a, float b, float t, float t0, float t1) {
        return a + (b - a) * smooth((t - t0) / (t1 - t0));
    }

    /** Poutre de section t x t de p0 a p1. */
    static void beam(BigMesh m, float x0, float y0, float z0, float x1, float y1, float z1, float t, BigMat mat) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01F) {
            return;
        }
        Quaternionf q = new Quaternionf().rotationTo(0, 1, 0, dx / len, dy / len, dz / len);
        Matrix4f xf = new Matrix4f().translate(x0, y0, z0).rotate(q);
        m.box(-t / 2, 0, -t / 2, t / 2, len, t / 2, mat, xf, 4);
    }

    private static int heat(float k) {
        k = clamp01(k);
        int r = Math.round(90 + 165 * Math.min(1, k * 1.6F)), g = Math.round(84 + 140 * k * k), b = Math.round(80 + 60 * k * k * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int shade(int rgb, float k) {
        int r = Math.min(255, Math.round((rgb >> 16 & 255) * k)), g = Math.min(255, Math.round((rgb >> 8 & 255) * k)),
              b = Math.min(255, Math.round((rgb & 255) * k));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    // ------------------------------------------------------------------ presses a portique

    /** Bas du coulisseau au repos (px) ; dessus de la matrice. */
    private static final float RAM_REST = 3, DIE_TOP = -11;

    /** Cycle d'une presse : 0..1 = entree de la piece, descente, maintien, remontee, sortie. */
    private static float pressCycle(float phase) {
        return (phase / 60F) % 1F;
    }

    /** Course du coulisseau : 0 en haut, 1 en bas. */
    private static float ramDown(float u) {
        if (u < 0.25F) {
            return 0;
        }
        if (u < 0.42F) {
            return smooth((u - 0.25F) / 0.17F);
        }
        if (u < 0.55F) {
            return 1;
        }
        return 1 - smooth((u - 0.55F) / 0.2F);
    }

    /** Avance de la piece le long de l'axe : de l'entree (0) a la matrice (0,5) puis a la sortie (1). */
    private static float feed(float u) {
        if (u < 0.22F) {
            return 0.5F * smooth(u / 0.22F);
        }
        if (u < 0.78F) {
            return 0.5F;
        }
        return 0.5F + 0.5F * smooth((u - 0.78F) / 0.22F);
    }

    /** Bati commun : semelle, matrice, colonnes de guidage, couronne et verins. {@code k} : 0 presse, 1 hydraulique, 2 plieuse. */
    static void press(BigMesh m, float w, float h, float l, int k) {
        float cx = w / 2, cz = l / 2;
        m.box(1, -16, 1, w - 1, -13, l - 1, BigMat.CAST, null, 4);                                            //semelle
        if (k == 2) {                                                                                          //matrice en V
            m.box(cx - 7, -13, 3, cx - 1.2F, DIE_TOP, l - 3, BigMat.STEEL_D, null, 4);
            m.box(cx + 1.2F, -13, 3, cx + 7, DIE_TOP, l - 3, BigMat.STEEL_D, null, 4);
            m.box(cx - 1.2F, -13, 3, cx + 1.2F, -12.5F, l - 3, BigMat.CAST, null, 4);
        } else {
            m.box(cx - w * 0.32F, -13, cz - l * 0.26F, cx + w * 0.32F, DIE_TOP - 0.6F, cz + l * 0.26F, BigMat.STEEL_D, null, 4);
            m.box(cx - w * 0.3F, DIE_TOP - 0.6F, cz - l * 0.24F, cx + w * 0.3F, DIE_TOP, cz + l * 0.24F, BigMat.CHROME, null, 4);
        }
        float gx = 2.5F, gz = 2.5F;
        for (float x : new float[]{gx, w - gx}) {
            for (float z : new float[]{gz, l - gz}) {
                m.cyl('y', x, z, 1.4F, -13, h - 6, BigMat.CHROME, 8, null, 4);                                 //colonnes de guidage
            }
        }
        m.box(1, h - 6, 1, w - 1, h, l - 1, BigMat.CAST, null, 4);                                             //couronne
        int nc = k == 1 && l >= 64 ? 2 : 1;
        float rc = Math.min(w, l / nc) * (k == 1 ? 0.2F : 0.15F);
        for (int i = 0; i < nc; i++) {
            float z = l * (i + 0.5F) / nc;
            m.cyl('y', cx, z, rc, h - 20, h - 6, BigMat.CAST_L, 14, null, 4);                                  //verins
            m.cyl('y', cx, z, rc + 0.6F, h - 9, h - 7, BigMat.STEEL_D, 14, null, 4, false, 1);
        }
        if (k == 1) {                                                                                          //flexibles hydrauliques
            for (float z : new float[]{3.5F, l - 3.5F}) {
                m.box(w - 3, h - 18, z - 0.6F, w - 1.8F, h - 6, z + 0.6F, BigMat.RUBBER, null, 2);
            }
        }
    }

    static void pressLife(BigMesh m, float w, float h, float l, int k, int tint, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2, s = a.speed;
        float u = pressCycle(a.phase), d = ramDown(u) * smooth(s * 4);
        float hr = Math.min(12, h * 0.3F);
        float squash = u < 0.25F ? 0 : u < 0.42F ? smooth((u - 0.25F) / 0.17F) : 1;
        float t0 = k == 1 ? 5 : 4, thick = t0 - (t0 - 1.5F) * squash;
        float yb = RAM_REST + ((k == 2 ? DIE_TOP + 2.5F : DIE_TOP + thick) - RAM_REST) * d;                  //bas du coulisseau
        if (k == 2) {                                                                                          //tablier de la plieuse
            m.box(cx - 3, yb, 3.4F, cx + 3, yb + hr + 4, l - 3.4F, BigMat.CAST_L, null, 4);
            m.box(3.4F, yb + hr, 3.4F, w - 3.4F, yb + hr + 3, l - 3.4F, BigMat.CAST, null, 4);
        } else {
            m.box(3.4F, yb + 1.2F, 3.4F, w - 3.4F, yb + 4, l - 3.4F, BigMat.CAST_L, null, 4);              //plateau
            m.box(cx - w * 0.3F, yb, l / 2 - l * 0.24F, cx + w * 0.3F, yb + 1.2F, l / 2 + l * 0.24F, BigMat.STEEL_D, null, 4);  //outil
            m.box(6, yb + 4, 6, w - 6, yb + hr, l - 6, BigMat.CAST, null, 4);                                //corps nervure
            for (float z = 8; z < l - 7; z += Math.max(8, (l - 16) / 4)) {
                m.box(4.5F, yb + 4, z - 0.8F, w - 4.5F, yb + hr - 1, z + 0.8F, BigMat.CAST_L, null, 2);
            }
            m.box(3.4F, yb + hr, 3.4F, w - 3.4F, yb + hr + 1.5F, l - 3.4F, BigMat.CAST_L, null, 4);
        }
        for (float x : new float[]{2.5F, w - 2.5F}) {
            for (float z : new float[]{2.5F, l - 2.5F}) {
                m.cyl('y', x, z, 2.2F, yb + 1, yb + 5, BigMat.STEEL_D, 8, null, 4);                              //bagues sur les colonnes
            }
        }
        int nc = k == 1 && l >= 64 ? 2 : 1;
        float rc = Math.min(w, l / nc) * (k == 1 ? 0.2F : 0.15F);
        for (int i = 0; i < nc; i++) {
            m.cyl('y', cx, l * (i + 0.5F) / nc, rc * 0.55F, yb + hr + (k == 2 ? 3 : 1.5F), h - 20, BigMat.CHROME, 12, null, 4);   //tiges des verins
        }
        if (k == 2) {                                                                                          //poincon en V
            m.box(cx - 2.5F, yb - 2, 3, cx + 2.5F, yb, l - 3, BigMat.STEEL_D, null, 4);
            m.box(cx - 1, yb - 3, 3, cx + 1, yb - 2, l - 3, BigMat.CHROME, null, 2);
        }
        if (s < 0.05F) {
            return;
        }
        //piece : arrive par un bout, s'arrete sur la matrice, repart par l'autre
        float f = feed(u), zc = -l * 0.5F + f * l * 2;
        float hl = k == 2 ? l * 0.38F : l * (0.14F + (u >= 0.42F ? 0.06F : 0));
        float hw = k == 2 ? 6 : w * (0.18F + (u >= 0.42F ? 0.04F : 0));
        float z0 = Math.max(1, zc - hl), z1 = Math.min(l - 1, zc + hl);
        if (z1 - z0 < 1) {
            return;
        }
        if (k == 2) {                                                                                          //tole pliee
            float ang = 38 * (u < 0.25F ? 0 : u < 0.42F ? smooth((u - 0.25F) / 0.17F) : 1);
            float y0 = DIE_TOP;
            for (int side = -1; side <= 1; side += 2) {
                Matrix4f r = BigMesh.rot('z', cx, y0, 0, side * ang);
                float xa = side < 0 ? cx - hw : cx, xb = side < 0 ? cx : cx + hw;
                m.box(xa, y0, z0, xb, y0 + 0.6F, z1, BigMat.CHROME, r, 4);
            }
            return;
        }
        boolean hot = k == 1;
        if (hot) {
            m.glow(11).color(heat(0.85F - 0.25F * f));
        } else {
            m.color(0xFF000000 | tint);
        }
        m.box(cx - hw, DIE_TOP, z0, cx + hw, DIE_TOP + thick, z1, hot ? BigMat.MOLTEN : BigMat.STRIP, null, 4);
        m.glow(0).color(0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ presse a granules

    static void pellet(BigMesh m, float w, float h, float l) {
        float cx = w / 2, cy = h * 0.45F, r = Math.min(w, h) * 0.36F, cz = l * 0.5F, dl = Math.min(l * 0.32F, 24);
        float z0 = cz - dl / 2, z1 = cz + dl / 2;
        m.box(2, -16, 2, w - 2, -14, l - 2, BigMat.CAST, null, 4);                                            //semelle
        m.box(cx - r - 2, -14, z0 - 2, cx - r, cy, z1 + 2, BigMat.CAST, null, 4);                              //chassis
        m.box(cx + r, -14, z0 - 2, cx + r + 2, cy, z1 + 2, BigMat.CAST, null, 4);
        m.spin('z', cx, cy, 0, 3);                                                                             //filiere annulaire
        m.cyl('z', cx, cy, r, z0, z1, BigMat.GRATE, 24, null, 4, false, 2.2F);
        m.cyl('z', cx, cy, r + 0.6F, z0 - 1, z0, BigMat.STEEL_D, 24, null, 4, false, 3);
        m.cyl('z', cx, cy, r + 0.6F, z1, z1 + 1, BigMat.STEEL_D, 24, null, 4, false, 3);
        m.fixed();
        for (int i = 0; i < 2; i++) {                                                                          //galets presseurs
            double an = Math.toRadians(i * 180 + 30);
            float rx = cx + (float) Math.cos(an) * r * 0.45F, ry = cy + (float) Math.sin(an) * r * 0.45F;
            m.spin('z', rx, ry, 0, -6);
            m.cyl('z', rx, ry, r * 0.32F, z0 + 1, z1 - 1, BigMat.ROLL, 14, null, 4);
            m.fixed();
            m.box(rx - 0.8F, ry - 0.8F, z0 - 3, rx + 0.8F, ry + 0.8F, z0 + 1, BigMat.STEEL_D, null, 2);
        }
        m.cyl('z', cx, cy, 2, z0 - 6, z1 + 2, BigMat.CHROME, 8, null, 4);                                     //arbre
        m.box(cx - 5, cy - 6, 2, cx + 5, cy + 6, z0 - 6, BigMat.MOTOR, null, 4);                              //moteur et reducteur
        m.box(cx - 4, cy - 5, z0 - 6, cx + 4, cy + 5, z0 - 2, BigMat.CAST_L, null, 4);
        float yc = Math.min(h - 5, cy + r + 6);                                                               //conditionneur et tremie
        m.cyl('z', cx, yc, 3, z0, l - 3, BigMat.CAST_L, 12, null, 4);
        m.box(cx - 1.2F, cy + r - 1, cz - 1.2F, cx + 1.2F, yc - 2, cz + 1.2F, BigMat.PIPE, null, 2);
        m.box(cx - 5, yc + 2, l - 12, cx + 5, Math.min(h, yc + 9), l - 4, BigMat.CAST_L, null, 4);
        m.box(cx - 3, cy - r - 4, z0, cx + 3, cy - r - 2, z1, BigMat.CAST_L, null, 4);                       //goulotte
        m.box(cx - w * 0.3F, -14, z0 - 4, cx + w * 0.3F, -9, z1 + 4, BigMat.CAST, null, 4);                   //bac
    }

    static void pelletLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        float cx = w / 2, cy = h * 0.45F, r = Math.min(w, h) * 0.36F, cz = l * 0.5F, dl = Math.min(l * 0.32F, 24), s = a.speed;
        float z0 = cz - dl / 2;
        m.color(0xFF000000 | tint);
        float fill = 0.3F + 0.7F * s;
        m.box(cx - w * 0.3F + 1, -9, z0 - 3, cx + w * 0.3F - 1, -9 + 2.5F * fill, z0 + dl + 3, BigMat.STRIP, null, 4);   //tas
        for (int i = 0; i < 30; i++) {                                                                          //granules qui tombent
            if (BigMachines.hash(i, 80) > s) {
                continue;
            }
            float ph = (a.phase * 0.05F + i * 0.071F) % 1F;
            float x = cx + ((i * 7) % 9 - 4) * r * 0.12F, z = z0 + 1 + (i * 13 % 17) / 17F * (dl - 2);
            float y = cy - r - 4 - ph * (cy - r - 4 + 9);
            m.box(x - 0.5F, y, z - 0.5F, x + 0.5F, y + 1.6F, z + 0.5F, BigMat.STRIP, null, 2);
        }
        m.color(0xFFFFFFFF);
        for (int i = 0; i < 12; i++) {                                                                          //farine du conditionneur
            if (BigMachines.hash(i, 81) > s) {
                continue;
            }
            float ph = (a.phase * 0.04F + i * 0.083F) % 1F;
            float y = cy + r - 1 + (1 - ph) * 3;
            m.box(cx - 0.6F, y, cz - 0.6F + (i % 3 - 1) * 0.6F, cx + 0.6F, y + 0.8F, cz + 0.6F + (i % 3 - 1) * 0.6F, BigMat.WOOD1, null, 2);
        }
    }

    // ------------------------------------------------------------------ chaines : socle commun

    private static float belt(float w) {
        return Math.min(w - 4, 20);
    }

    /** Indice de poste du portique {@code s} (px le long de l'axe). */
    private static int stationSlot(int s) {
        return Math.round((s - PITCH / 2) / PITCH);
    }

    static void lineBase(BigMesh m, float w, float l, int[] stands, boolean continuous) {
        float cx = w / 2, bw = belt(w);
        m.box(cx - bw / 2 - 1.5F, 0, 0.5F, cx - bw / 2, YB + 1, l - 0.5F, BigMat.CAST, null, 4);              //longerons
        m.box(cx + bw / 2, 0, 0.5F, cx + bw / 2 + 1.5F, YB + 1, l - 0.5F, BigMat.CAST, null, 4);
        m.box(cx - bw / 2, YB - 1, 1.5F, cx + bw / 2, YB, l - 1.5F, BigMat.RUBBER, null, 4);                   //bande
        float v = continuous ? 0.3F : PITCH / PERIOD;
        for (float z : new float[]{2.5F, l - 2.5F}) {                                                          //tambours
            m.spin('x', 0, YB - 2.5F, z, -v / 2.4F * 57.3F);
            m.cyl('x', YB - 2.5F, z, 2.4F, cx - bw / 2, cx + bw / 2, BigMat.ROLL, 10, null, 4);
            m.fixed();
        }
        for (int s : stands) {                                                                                 //traverses des postes
            m.box(0.5F, TOP - 3, s - 3, w - 0.5F, TOP, s + 3, BigMat.CAST_L, null, 4);
            if (w >= 40) {                                                                                     //armoire de commande
                m.box(1.5F, 0, s - 6, cx - bw / 2 - 3, 13, s - 1, BigMat.CAST_L, null, 4);
                m.glow(12).color(0xFF7CF0A0);
                m.box(cx - bw / 2 - 3, 10, s - 5, cx - bw / 2 - 2.7F, 11, s - 4, BigMat.LED, null, 2);
                m.glow(0).color(0xFFFFFFFF);
            }
        }
    }

    /** Taquets de la bande (mouvement visible) ; pos = avance de la bande (px). */
    private static void cleats(BigMesh m, float w, float l, float pos) {
        float cx = w / 2, bw = belt(w);
        for (float z = pos % 8; z < l - 2; z += 8) {
            if (z < 2) {
                continue;
            }
            m.box(cx - bw / 2 + 0.3F, YB, z, cx + bw / 2 - 0.3F, YB + 0.25F, z + 0.6F, BigMat.CAST, null, 1);
        }
    }

    /** Etat du transfert pas a pas. */
    private record Step(int n, float e, float g) {
    }

    private static Step step(float phase) {
        float t = phase / PERIOD;
        int n = (int) Math.floor(t);
        float f = t - n;
        return new Step(n, smooth(f / MOVE), f < MOVE ? -1 : (f - MOVE) / (1 - MOVE));
    }

    /** Piece d'identifiant {@code id} presente sur la bande (la chaine se remplit et se vide avec le regime). */
    private static boolean present(int id, float speed) {
        return speed > 0.05F && BigMachines.hash(id, 91) < speed * 1.15F - 0.1F;
    }

    /** Postes : indices de cases (pas de PITCH) des portiques. */
    private static int[] slots(int[] stands) {
        int[] r = new int[stands.length];
        for (int i = 0; i < stands.length; i++) {
            r[i] = stationSlot(stands[i]);
        }
        return r;
    }

    /** Machine de la chaine. */
    enum Line {
        LATHE, ASSEMBLY, WELD, PLACE, SAW, MOLD, SORT, CAST, BOTTLE
    }

    // ------------------------------------------------------------------ chaines : partie fixe

    /** Nombre de voies paralleles selon la largeur. */
    private static int lanes(float w) {
        return Math.max(1, (int) (w / 22));
    }

    static void line(BigMesh m, Line kind, float w, float h, float l, int[] stands) {
        int n = lanes(w);
        for (int i = 0; i < n; i++) {
            m.shift(w * i / n);
            lane(m, kind, w / n, h, l, stands);
        }
        m.shift(0);
        if (n > 1) {
            for (int s : stands) {
                m.box(0.5F, TOP - 3, s - 3, w - 0.5F, TOP, s + 3, BigMat.CAST_L, null, 4);                     //traverse commune
            }
        }
    }

    private static void lane(BigMesh m, Line kind, float w, float h, float l, int[] stands) {
        float cx = w / 2, bw = belt(w);
        lineBase(m, w, l, stands, kind == Line.SAW || kind == Line.SORT);
        int i = 0;
        for (int s : stands) {
            switch (kind) {
                case LATHE -> {
                    m.box(cx - 4, 21, s - 4, cx + 4, TOP - 3, s + 4, BigMat.MOTOR, null, 4);                    //broche
                    m.box(cx + bw / 2 + 0.2F, YB + 1, s - 2.5F, Math.min(w - 0.3F, cx + bw / 2 + 2.6F), YB + 9, s + 2.5F, BigMat.CAST, null, 4);  //tourelle
                }
                case ASSEMBLY, WELD -> {
                    m.cyl('y', cx, s, 3, TOP - 5, TOP - 3, BigMat.STEEL_D, 12, null, 4);                         //embase du robot
                    if (kind == Line.ASSEMBLY) {
                        m.box(cx - 3, 13, s + 7, cx + 3, TOP - 3, s + 12, BigMat.CAST_L, null, 4);              //distributeur de pieces
                        m.box(cx - 1.5F, 11.5F, s + 8, cx + 1.5F, 13, s + 11, BigMat.STEEL_D, null, 2);
                    } else {
                        m.cyl('x', TOP - 7, s - 8, 3, cx - 2, cx + 2, BigMat.DRUM, 12, null, 4);               //bobine de fil
                        m.box(cx - 0.3F, TOP - 7, s - 8, cx + 0.3F, TOP - 3, s - 7, BigMat.CAST_L, null, 2);
                    }
                }
                case PLACE -> {
                    for (float z : new float[]{s - 5, s + 5}) {
                        m.box(1, TOP - 6, z - 0.8F, w - 1, TOP - 3, z + 0.8F, BigMat.CAST_L, null, 4);         //rails du portique
                    }
                    m.box(cx - 2, 15, s + 7, cx + 2, TOP - 3, s + 11, BigMat.CAST_L, null, 4);                  //chargeur de puces
                    m.cyl('x', TOP - 7, s + 15, 3.5F, cx - 1.5F, cx + 1.5F, BigMat.DRUM, 12, null, 4);       //bobine de puces
                }
                case SAW -> {
                    float r = 7;
                    m.spin('x', 0, YB + 3, s, -14);                                                             //lame circulaire
                    m.cyl('x', YB + 3, s, r, cx - 0.35F, cx + 0.35F, BigMat.CHROME, 18, null, 4);
                    for (int t = 0; t < 18; t++) {
                        Matrix4f tr = BigMesh.rot('x', 0, YB + 3, s, t * 20);
                        m.box(cx - 0.45F, YB + 3 + r - 0.4F, s - 0.8F, cx + 0.45F, YB + 3 + r + 0.9F, s + 0.5F, BigMat.TOOTH, tr, 2);
                    }
                    m.fixed();
                    m.box(cx - 2.5F, YB + 3 + 2, s - 9, cx + 2.5F, YB + 3 + r + 2.5F, s + 9, BigMat.CAST_L, null, 4);   //carter
                    m.box(cx - 2.5F, YB + 3 + r + 2.5F, s - 2, cx + 2.5F, TOP - 3, s + 2, BigMat.CAST, null, 4);
                    m.box(cx + 2.5F, YB + 1, s - 3, Math.min(cx + bw / 2 + 3, w - 0.5F), YB + 6, s + 3, BigMat.MOTOR, null, 4);
                    m.cyl('x', YB + 3, s, 1, cx - 0.5F, cx + 3, BigMat.CHROME, 8, null, 2);
                }
                case MOLD -> {
                    m.box(cx - 6, TOP - 7, s - 6, cx + 6, TOP - 3, s + 6, BigMat.CAST, null, 4);                //plateau fixe
                    for (float x : new float[]{cx - 5, cx + 5}) {
                        for (float z : new float[]{s - 5, s + 5}) {
                            m.cyl('y', x, z, 0.8F, YB, TOP - 7, BigMat.CHROME, 8, null, 2);                  //colonnes
                        }
                    }
                    m.cyl('z', cx, TOP - 5, 2.6F, s + 6, s + 18, BigMat.CAST_L, 12, null, 4);                 //fourreau d'injection
                    m.box(cx - 3, TOP - 3, s + 10, cx + 3, TOP + 6, s + 16, BigMat.CAST_L, null, 4);         //tremie
                }
                case SORT -> {
                    m.box(cx - bw / 2 - 1.5F, 11, s - 6, cx + bw / 2 + 1.5F, 18, s + 6, BigMat.CAST_L, null, 4);  //capot de la camera
                    m.box(cx - bw / 2 - 1.5F, 18, s - 2, cx + bw / 2 + 1.5F, TOP - 3, s + 2, BigMat.CAST, null, 4);
                    m.box(cx - bw / 2, 8, s + 7, cx + bw / 2, 9, s + 8, BigMat.PIPE, null, 2);               //rampe de buses
                    float xs = Math.min(w - 0.5F, cx + bw / 2 + 5);                                          //goulotte des rebuts
                    if (xs > cx + bw / 2 + 2.5F) {
                        m.box(cx + bw / 2 + 1.5F, 0, s + 9, xs, 4, s + 16, BigMat.CAST, null, 4);
                    }
                }
                case CAST -> {
                    if (i == 0) {
                        m.box(cx - 0.8F, TOP - 10, s - 0.8F, cx + 0.8F, TOP - 3, s + 0.8F, BigMat.CAST, null, 2);  //suspente de la poche
                        m.box(cx - 5, TOP - 11, s - 0.6F, cx + 5, TOP - 10, s + 0.6F, BigMat.CAST, null, 2);
                    } else {
                        m.box(cx - bw / 2, 16, s - 4, cx + bw / 2, 18, s + 4, BigMat.PIPE, null, 4);           //rampe d'arrosage
                        m.box(cx - 0.8F, 18, s - 0.8F, cx + 0.8F, TOP - 3, s + 0.8F, BigMat.PIPE, null, 2);
                    }
                }
                case BOTTLE -> {
                    m.box(cx - 4, TOP - 8, s - 4, cx + 4, TOP - 3, s + 4, i % 2 == 0 ? BigMat.CAST_L : BigMat.MOTOR, null, 4);
                    if (i % 2 == 0) {
                        m.cyl('y', cx, s, 2.5F, TOP - 3, TOP + 4, BigMat.CAST_L, 12, null, 4);               //cuve tampon
                    }
                }
            }
            i++;
        }
    }

    // ------------------------------------------------------------------ chaines : partie animee

    static void lineLife(BigMesh m, Line kind, float w, float h, float l, int[] stands, int tint, BigMachines.Anim a) {
        int n = lanes(w);
        for (int i = 0; i < n; i++) {
            BigMachines.Anim b = new BigMachines.Anim();
            b.speed = a.speed;
            b.phase = a.phase + i * 23;
            m.shift(w * i / n);
            laneLife(m, kind, w / n, h, l, stands, tint, b);
        }
        m.shift(0);
    }

    private static void laneLife(BigMesh m, Line kind, float w, float h, float l, int[] stands, int tint, BigMachines.Anim a) {
        if (kind == Line.SAW) {
            sawLife(m, w, l, stands, a);
            return;
        }
        if (kind == Line.SORT) {
            sortLife(m, w, l, stands, tint, a);
            return;
        }
        Step st = step(a.phase);
        cleats(m, w, l, PITCH * (st.n + st.e));
        int[] sl = slots(stands);
        int n = (int) (l / PITCH);
        for (int j = -1; j <= n; j++) {
            float zc = PITCH * (j + st.e) + PITCH / 2;
            if (zc < 4 || zc > l - 4) {
                continue;
            }
            int id = j - st.n;
            if (!present(id, a.speed)) {
                continue;
            }
            int done = 0, cur = -1;
            for (int k = 0; k < sl.length; k++) {
                if (sl[k] <= j) {
                    done++;
                } else if (sl[k] == j + 1 && st.g >= 0) {
                    cur = k;
                }
            }
            part(m, kind, w, zc, done, cur, st.g, tint, id);
        }
        for (int k = 0; k < sl.length; k++) {
            int j = sl[k] - 1;
            boolean busy = st.g >= 0 && present(j - st.n, a.speed);
            station(m, kind, w, stands[k], k, busy ? st.g : -1, a);
        }
    }

    /** Piece sur la bande en z (centre) ; done = postes franchis, cur = poste en cours (progression g). */
    private static void part(BigMesh m, Line kind, float w, float z, int done, int cur, float g, int tint, int id) {
        float cx = w / 2;
        switch (kind) {
            case LATHE -> {
                boolean turned = done > 0 || (cur >= 0 && g > 0.7F);
                float spin = cur >= 0 && g > 0.2F && g < 0.85F ? g * 3000 : 0;
                Matrix4f r = BigMesh.rot('y', cx, 0, z, spin);
                if (turned) {
                    m.cyl('y', cx, z, 2.2F, YB, YB + 7, BigMat.CHROME, 8, r, 4, false, 0.9F);
                    m.cyl('y', cx, z, 2.7F, YB + 4, YB + 5, BigMat.CHROME, 8, r, 4, false, 0.6F);
                } else {
                    m.cyl('y', cx, z, 3, YB, YB + 7, BigMat.STEEL_D, 8, r, 4, false, 1.2F);
                }
                m.box(cx - 1.6F, YB + 6.8F, z - 1.6F, cx + 1.6F, YB + 7, z + 1.6F, turned ? BigMat.CHROME : BigMat.STEEL_D, r, 2);
            }
            case ASSEMBLY -> {
                m.box(cx - 4, YB, z - 4, cx + 4, YB + 1.5F, z + 4, BigMat.CAST_L, null, 4);
                int nb = done + (cur >= 0 && g > 0.78F ? 1 : 0);
                BigMat[] mats = {BigMat.MOTOR, BigMat.PIPE, BigMat.CHROME, BigMat.PAINT_O, BigMat.STEEL_D};
                for (int c = 0; c < nb; c++) {
                    float hy = YB + 1.5F + c * 1.6F, hs = 3 - c * 0.45F;
                    m.box(cx - hs, hy, z - hs, cx + hs, hy + 1.6F, z + hs, mats[c % mats.length], null, 4);
                }
            }
            case WELD -> {
                m.box(cx - 4, YB, z - 3.5F, cx - 0.1F, YB + 2, z + 3.5F, BigMat.CAST_L, null, 4);
                m.box(cx + 0.1F, YB, z - 3.5F, cx + 4, YB + 2, z + 3.5F, BigMat.CAST_L, null, 4);
                float len = done > 0 ? 1 : cur >= 0 ? clamp01((g - 0.15F) / 0.7F) : 0;
                if (len > 0) {
                    float z0 = z - 3.5F, z1 = z0 + 7 * len;
                    boolean hot = done == 0;
                    m.glow(hot ? 13 : 0).color(hot ? heat(0.9F) : 0xFF6A6E74);
                    m.box(cx - 0.6F, YB + 2, z0, cx + 0.6F, YB + 2.6F, z1, hot ? BigMat.MOLTEN : BigMat.STRIP, null, 2);
                    if (hot && z1 - z0 > 1.5F) {
                        m.glow(0).color(0xFF6A6E74);
                        m.box(cx - 0.6F, YB + 2, z0, cx + 0.6F, YB + 2.6F, z1 - 1.5F, BigMat.STRIP, null, 2);
                    }
                    m.glow(0).color(0xFFFFFFFF);
                }
            }
            case PLACE -> {
                m.box(cx - 4.5F, YB, z - 4.5F, cx + 4.5F, YB + 0.6F, z + 4.5F, BigMat.PCB, null, 6);
                int nb = done * 4 + (cur >= 0 ? (int) (clamp01(g) * 4.0F) : 0);
                for (int c = 0; c < Math.min(nb, 12); c++) {
                    float px = cx - 3 + (c % 3) * 3, pz = z - 3 + (c / 3 % 4) * 2;
                    m.box(px - 0.8F, YB + 0.6F, pz - 0.6F, px + 0.8F, YB + 1.2F, pz + 0.6F, BigMat.RUBBER, null, 1);
                }
            }
            case MOLD -> {
                boolean made = done > 0 || (cur >= 0 && g > 0.72F);
                if (made) {
                    m.color(0xFF000000 | tint);
                    m.box(cx - 3, YB, z - 3, cx + 3, YB + 2.2F, z + 3, BigMat.STRIP, null, 4);
                    m.box(cx - 1.4F, YB + 2.2F, z - 1.4F, cx + 1.4F, YB + 3.6F, z + 1.4F, BigMat.STRIP, null, 4);
                    if (done > 1 || (done == 1 && cur >= 0 && g > 0.72F)) {
                        m.color(shade(tint, 0.55F));
                        m.box(cx - 3.2F, YB + 2.2F, z - 3.2F, cx + 3.2F, YB + 2.8F, z + 3.2F, BigMat.STRIP, null, 4);
                    }
                    m.color(0xFFFFFFFF);
                }
            }
            case CAST -> {
                m.box(cx - 4, YB, z - 4, cx + 4, YB + 1, z + 4, BigMat.CAST, null, 4);                         //moule
                for (int s = -1; s <= 1; s += 2) {
                    m.box(cx - 4, YB + 1, z + s * 3.2F - 0.8F, cx + 4, YB + 4, z + s * 3.2F + 0.8F, BigMat.CAST, null, 4);
                    m.box(cx + s * 3.2F - 0.8F, YB + 1, z - 2.4F, cx + s * 3.2F + 0.8F, YB + 4, z + 2.4F, BigMat.CAST, null, 4);
                }
                float fill = done > 0 ? 1 : cur >= 0 ? clamp01((g - 0.25F) / 0.5F) : 0;
                if (fill > 0) {
                    float cool = done == 0 ? 0 : Math.min(1, (done - 1 + (cur >= 0 ? g : 0.5F)) * 0.5F + 0.15F);
                    m.glow(Math.round(14 * (1 - cool))).color(cool >= 1 ? 0xFF000000 | tint : heat(1 - cool * 0.9F));
                    m.box(cx - 2.4F, YB + 1, z - 2.4F, cx + 2.4F, YB + 1 + 2.8F * fill, z + 2.4F, cool > 0.8F ? BigMat.STRIP : BigMat.MOLTEN,
                          null, 4);
                    m.glow(0).color(0xFFFFFFFF);
                }
            }
            case BOTTLE -> {
                int fillers = 0, cappers = 0;
                for (int k = 0; k < done; k++) {
                    if (k % 2 == 0) {
                        fillers++;
                    } else if (fillers > 0) {
                        cappers++;
                    }
                }
                float level = fillers > 0 ? 1 : cur >= 0 && cur % 2 == 0 ? clamp01((g - 0.25F) / 0.5F) : 0;
                boolean cap = cappers > 0 || (cur >= 0 && cur % 2 == 1 && fillers > 0 && g > 0.6F);
                for (int b = -1; b <= 1; b += 2) {
                    float bz = z + b * 2.6F;
                    m.cyl('y', cx, bz, 1.7F, YB, YB + 5.5F, BigMat.GLASS, 6, null, 4, false, 0.3F);
                    m.box(cx - 0.7F, YB + 5.5F, bz - 0.7F, cx + 0.7F, YB + 7.4F, bz + 0.7F, BigMat.GLASS, null, 2);
                    if (level > 0) {
                        m.color(0xC0000000 | tint);
                        m.box(cx - 1.15F, YB + 0.2F, bz - 1.15F, cx + 1.15F, YB + 0.2F + 5 * level, bz + 1.15F, BigMat.LIQUID, null, 4);
                        m.color(0xFFFFFFFF);
                    }
                    if (cap) {
                        m.box(cx - 0.9F, YB + 7.4F, bz - 0.9F, cx + 0.9F, YB + 8.2F, bz + 0.9F, BigMat.PAINT_R, null, 2);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Poste qui travaille pendant l'arret (g de 0 a 1 ; -1 = en attente, position haute). */
    private static void station(BigMesh m, Line kind, float w, int s, int k, float g, BigMachines.Anim a) {
        float cx = w / 2, bw = belt(w);
        boolean on = g >= 0;
        switch (kind) {
            case LATHE -> {
                float down = on ? (g < 0.2F ? smooth(g / 0.2F) : g > 0.85F ? 1 - smooth((g - 0.85F) / 0.15F) : 1) : 0;
                float yc = 21 - (21 - (YB + 7.5F)) * down;
                float spin = on && g > 0.2F && g < 0.85F ? g * 3000 : 0;
                Matrix4f r = BigMesh.rot('y', cx, 0, s, spin);
                m.cyl('y', cx, s, 1.2F, yc + 2, 21, BigMat.CHROME, 8, null, 2);                                //fourreau
                m.cyl('y', cx, s, 3.2F, yc, yc + 2.5F, BigMat.STEEL_D, 10, r, 4);                               //mandrin
                float tx = on && g > 0.25F && g < 0.8F ? cx + 2.2F : cx + bw / 2 - 0.2F;
                m.box(tx, YB + 3, s - 0.6F, cx + bw / 2 + 0.2F, YB + 4.2F, s + 0.6F, BigMat.CHROME, null, 2);  //outil
                if (on && g > 0.25F && g < 0.8F) {
                    for (int c = 0; c < 5; c++) {                                                              //copeaux
                        float ph = (a.phase * 0.2F + c * 0.21F) % 1F;
                        float x = cx + 2.6F + ph * 3, y = YB + 4 + ph * 2 - ph * ph * 5, z = s + (c - 2) * 0.7F;
                        m.box(x, y, z, x + 0.6F, y + 0.3F, z + 0.6F, BigMat.CHROME, null, 1);
                    }
                }
            }
            case ASSEMBLY -> {
                float px = cx, py = YB + 4, pz = s;
                float hx = cx, hy = 10.5F, hz = s + 9.5F;
                float rx = cx, ry = 22, rz = s + 3;
                float tx, ty, tz;
                boolean carry = false;
                if (!on || g < 0.05F) {
                    tx = rx;
                    ty = ry;
                    tz = rz;
                } else if (g < 0.3F) {
                    float t = (g - 0.05F) / 0.25F;
                    tx = hx;
                    ty = ease(ry, hy, t, 0, 1);
                    tz = ease(rz, hz, t, 0, 1);
                } else if (g < 0.75F) {
                    float t = (g - 0.3F) / 0.45F;
                    carry = true;
                    tx = px;
                    ty = t < 0.5F ? ease(hy, 16, t, 0, 0.5F) : ease(16, py, t, 0.5F, 1);
                    tz = ease(hz, pz, t, 0, 1);
                } else {
                    float t = (g - 0.75F) / 0.25F;
                    carry = t < 0.15F;
                    tx = px;
                    ty = ease(py, ry, t, 0.15F, 1);
                    tz = ease(pz, rz, t, 0.15F, 1);
                }
                robot(m, cx, s, tx, ty, tz, BigMat.PAINT_O);
                m.box(tx - 1.5F, ty - 0.5F, tz - 1.5F, tx + 1.5F, ty, tz + 1.5F, BigMat.STEEL_D, null, 2);      //pince
                if (carry) {
                    m.box(tx - 1.2F, ty - 1.8F, tz - 1.2F, tx + 1.2F, ty - 0.5F, tz + 1.2F, BigMat.PIPE, null, 2);
                }
            }
            case WELD -> {
                float tx = cx, ty, tz;
                if (!on || g < 0.1F || g > 0.9F) {
                    ty = 22;
                    tz = s + 3;
                    if (on) {
                        float t = g < 0.1F ? g / 0.1F : 1 - (g - 0.9F) / 0.1F;
                        ty = ease(22, YB + 3.6F, t, 0, 1);
                        tz = ease(s + 3, s - 3.5F, t, 0, 1);
                    }
                } else {
                    ty = YB + 3.6F;
                    tz = s - 3.5F + 7 * clamp01((g - 0.15F) / 0.7F);
                }
                robot(m, cx, s, tx, ty + 1, tz, BigMat.PAINT_O);
                m.cyl('y', tx, tz, 0.6F, ty, ty + 1.2F, BigMat.CAST, 6, null, 2);                               //torche
                if (on && g > 0.15F && g < 0.85F) {
                    float fl = 0.6F + 0.5F * BigMachines.hash((int) (a.phase * 3), 7);
                    m.glow(15).color(0xFFE8F4FF);
                    m.box(tx - fl * 0.6F, YB + 2.2F, tz - fl * 0.6F, tx + fl * 0.6F, YB + 2.2F + fl, tz + fl * 0.6F, BigMat.LED, null, 1);
                    for (int c = 0; c < 8; c++) {                                                              //etincelles
                        float ph = (a.phase * 0.3F + c * 0.13F) % 1F;
                        double an = c * 0.785 + (int) (a.phase * 0.3F + c * 0.13F) * 1.3;
                        float x = tx + (float) Math.cos(an) * ph * 4, z = tz + (float) Math.sin(an) * ph * 4,
                              y = YB + 2.6F + ph * 2.5F - ph * ph * 4;
                        m.color(0xFFFFD27A);
                        m.box(x - 0.2F, y, z - 0.2F, x + 0.2F, y + 0.4F, z + 0.2F, BigMat.LED, null, 1);
                    }
                    m.glow(0).color(0xFFFFFFFF);
                }
            }
            case PLACE -> {
                float hx = cx, hz = s + 9, hy;
                int sub = on ? Math.min(3, (int) (g * 4)) : 0;
                float sg = on ? g * 4 - sub : 0;
                float px = cx - 3 + (sub % 3) * 3, pz = s - 3 + (sub / 3 % 4) * 2;
                float x, z;
                if (!on) {
                    x = cx;
                    z = s;
                    hy = 18;
                } else if (sg < 0.45F) {
                    x = ease(px, hx, sg, 0, 0.3F);
                    z = ease(pz, hz, sg, 0, 0.3F);
                    hy = sg < 0.3F ? 18 : ease(18, 14, sg, 0.3F, 0.45F);
                } else {
                    x = ease(hx, px, sg, 0.5F, 0.8F);
                    z = ease(hz, pz, sg, 0.5F, 0.8F);
                    hy = sg < 0.8F ? 18 : ease(18, YB + 1.3F, sg, 0.8F, 1);
                }
                m.box(1, TOP - 7.5F, z - 1.2F, w - 1, TOP - 6, z + 1.2F, BigMat.CAST, null, 4);                //pont mobile
                m.box(x - 2, TOP - 11, z - 2, x + 2, TOP - 7.5F, z + 2, BigMat.MOTOR, null, 4);                 //tete
                m.cyl('y', x, z, 0.45F, hy, TOP - 11, BigMat.CHROME, 6, null, 1);
                if (on && sg > 0.45F && sg < 0.98F) {
                    m.box(x - 0.8F, hy - 0.6F, z - 0.6F, x + 0.8F, hy, z + 0.6F, BigMat.RUBBER, null, 1);
                }
                m.glow(on ? 12 : 3).color(0xFF60D0FF);
                m.box(x - 1.2F, TOP - 11.3F, z - 1.2F, x + 1.2F, TOP - 11, z + 1.2F, BigMat.LED, null, 1);
                m.glow(0).color(0xFFFFFFFF);
            }
            case MOLD -> {
                float close = on ? (g < 0.2F ? smooth(g / 0.2F) : g > 0.7F ? 1 - smooth((g - 0.7F) / 0.2F) : 1) : 0;
                float yb = 21 - (21 - (YB + 0.2F)) * close;
                m.box(cx - 5.5F, yb + 2, s - 5.5F, cx + 5.5F, yb + 4, s + 5.5F, BigMat.CAST_L, null, 4);         //plateau mobile
                m.box(cx - 4, yb, s - 4, cx + 4, yb + 2, s + 4, BigMat.STEEL_D, null, 4);                        //moule
                m.cyl('y', cx, s, 1.4F, yb + 4, TOP - 7, BigMat.CHROME, 8, null, 2);                              //genouillere
                boolean inj = on && g > 0.25F && g < 0.65F;
                m.glow(inj ? 10 : 2).color(heat(inj ? 0.8F : 0.3F));
                for (float z = s + 7; z < s + 17; z += 3) {
                    m.cyl('z', cx, TOP - 5, 2.9F, z, z + 1.4F, BigMat.GLOWBED, 12, null, 4);
                }
                m.glow(0).color(0xFFFFFFFF);
            }
            case CAST -> {
                if (k == 0) {
                    float tilt = on ? (g < 0.25F ? smooth(g / 0.25F) : g > 0.75F ? 1 - smooth((g - 0.75F) / 0.2F) : 1) : 0;
                    float py = TOP - 15;
                    Matrix4f r = BigMesh.rot('x', 0, py + 2, s, -tilt * 55);
                    m.box(cx - 3.5F, py - 2, s - 3.5F, cx + 3.5F, py + 4, s + 3.5F, BigMat.REFR_D, r, 4);          //poche
                    m.box(cx - 3.6F, py + 3, s - 3.6F, cx + 3.6F, py + 3.6F, s + 3.6F, BigMat.CAST, r, 4);
                    m.box(cx - 0.6F, py + 3.6F, s - 0.6F, cx + 0.6F, TOP - 11, s + 0.6F, BigMat.CAST, null, 2);
                    m.glow(14).color(heat(1));
                    m.box(cx - 2.8F, py + 2.6F, s - 2.8F, cx + 2.8F, py + 3.3F, s + 2.8F, BigMat.MOLTEN, r, 4);
                    if (on && g > 0.25F && g < 0.75F) {                                                        //jet
                        m.box(cx - 0.6F, YB + 1.5F, s - 0.6F, cx + 0.6F, py + 1, s + 0.6F, BigMat.MOLTEN, null, 2);
                    }
                    m.glow(0).color(0xFFFFFFFF);
                } else if (on) {
                    for (int c = 0; c < 10; c++) {                                                              //gouttes d'arrosage
                        float ph = (a.phase * 0.08F + c * 0.1F) % 1F;
                        float x = cx - 3 + (c % 4) * 2, z = s - 3 + (c / 4) * 3, y = 16 - ph * (16 - YB - 2);
                        m.box(x - 0.25F, y, z - 0.25F, x + 0.25F, y + 0.9F, z + 0.25F, BigMat.BUBBLE, null, 1);
                    }
                    for (int c = 0; c < 4; c++) {                                                               //vapeur
                        float ph = (a.phase * 0.03F + c * 0.25F) % 1F, d = 0.8F + ph * 1.6F;
                        float y = YB + 4 + ph * 10;
                        m.box(cx - d + (c - 1.5F), y, s - d, cx + d + (c - 1.5F), y + d, s + d, BigMat.FROTH, null, 2);
                    }
                }
            }
            case BOTTLE -> {
                float down = on ? (g < 0.2F ? smooth(g / 0.2F) : g > 0.8F ? 1 - smooth((g - 0.8F) / 0.2F) : 1) : 0;
                if (k % 2 == 0) {
                    float y = TOP - 8 - (TOP - 8 - (YB + 8)) * down;
                    for (int b = -1; b <= 1; b += 2) {
                        m.cyl('y', cx, s + b * 2.6F, 0.55F, y, TOP - 8, BigMat.CHROME, 6, null, 1);            //becs
                    }
                    m.box(cx - 2, y + 2, s - 4, cx + 2, y + 3.5F, s + 4, BigMat.CAST_L, null, 2);
                } else {
                    float y = TOP - 8 - (TOP - 8 - (YB + 8.3F)) * down;
                    float spin = on ? g * 900 : 0;
                    for (int b = -1; b <= 1; b += 2) {
                        float bz = s + b * 2.6F;
                        m.cyl('y', cx, bz, 1.3F, y, y + 3, BigMat.STEEL_D, 8, BigMesh.rot('y', cx, 0, bz, spin), 2);  //tetes de vissage
                        m.cyl('y', cx, bz, 0.5F, y + 3, TOP - 8, BigMat.CHROME, 6, null, 1);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Robot suspendu (6 axes simplifie) : embase sous la traverse en (bx, bz), poignet en (tx, ty, tz). */
    private static void robot(BigMesh m, float bx, float bz, float tx, float ty, float tz, BigMat paint) {
        float yaw = (float) Math.toDegrees(Math.atan2(tx - bx, tz - bz));
        m.box(bx - 2.2F, TOP - 8, bz - 2.2F, bx + 2.2F, TOP - 5, bz + 2.2F, paint, BigMesh.rot('y', bx, 0, bz, yaw), 4);   //tourelle
        Vector3f sh = new Vector3f(bx, TOP - 8, bz), wr = new Vector3f(tx, ty + 2.4F, tz);
        float a1 = 8.5F, a2 = 8;
        Vector3f d = new Vector3f(wr).sub(sh);
        float len = Math.min(d.length(), a1 + a2 - 0.2F);
        Vector3f u = new Vector3f(d).normalize();
        wr.set(sh).add(new Vector3f(u).mul(len));
        float x = (len * len + a1 * a1 - a2 * a2) / (2 * len), hh = (float) Math.sqrt(Math.max(0, a1 * a1 - x * x));
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f nn = new Vector3f(up).sub(new Vector3f(u).mul(up.dot(u)));
        if (nn.lengthSquared() < 1e-4) {
            nn.set((float) Math.sin(Math.toRadians(yaw)), 0, (float) Math.cos(Math.toRadians(yaw)));
        }
        nn.normalize();
        Vector3f el = new Vector3f(sh).add(new Vector3f(u).mul(x)).add(nn.mul(hh));
        beam(m, sh.x, sh.y, sh.z, el.x, el.y, el.z, 2.4F, paint);                                              //bras
        beam(m, el.x, el.y, el.z, wr.x, wr.y, wr.z, 1.9F, paint);                                              //avant-bras
        m.box(el.x - 1.4F, el.y - 1.4F, el.z - 1.4F, el.x + 1.4F, el.y + 1.4F, el.z + 1.4F, BigMat.STEEL_D, null, 2);
        m.box(sh.x - 1.5F, sh.y - 1.5F, sh.z - 1.5F, sh.x + 1.5F, sh.y + 1, sh.z + 1.5F, BigMat.STEEL_D, null, 2);
        beam(m, wr.x, wr.y, wr.z, tx, ty, tz, 1, BigMat.CHROME);                                               //poignet
    }

    // ------------------------------------------------------------------ scierie (continue)

    private static void sawLife(BigMesh m, float w, float l, int[] stands, BigMachines.Anim a) {
        float cx = w / 2, v = 0.3F, len = 26, gap = 34;
        float pos = a.phase * v;
        cleats(m, w, l, pos);
        int first = stands.length > 0 ? stands[0] : (int) (l / 2);
        int n = (int) (l / gap) + 2;
        for (int j = -1; j <= n; j++) {
            float z0 = (j * gap + pos % gap) - gap, z1 = z0 + len;
            int id = j - (int) Math.floor(pos / gap);
            if (z1 < 2 || z0 > l - 2 || !present(id, a.speed)) {
                continue;
            }
            z0 = Math.max(2, z0);
            z1 = Math.min(l - 2, z1);
            //bille ronde avant la lame, deux demi-billes apres ; quatre planches apres le deuxieme poste
            float cut = Math.max(z0, Math.min(z1, first));
            if (cut > z0) {
                m.cyl('z', cx, YB + 3.2F, 3.2F, z0, cut, BigMat.WOOD2, 10, null, 4);
                m.box(cx - 2.6F, YB + 0.6F, z0 - 0.01F, cx + 2.6F, YB + 5.8F, z0, BigMat.WOOD1, null, 4);
            }
            if (z1 > cut) {
                float second = stands.length > 1 ? stands[1] : Float.MAX_VALUE;
                float c2 = Math.max(cut, Math.min(z1, second));
                for (int s = -1; s <= 1; s += 2) {
                    m.box(cx + (s < 0 ? -3.2F : 0.35F), YB, cut, cx + (s < 0 ? -0.35F : 3.2F), YB + 6.2F, c2, BigMat.WOOD1, null, 4);
                }
                if (z1 > c2) {
                    for (int b = 0; b < 4; b++) {
                        float x0 = cx - 3.2F + b * 1.65F;
                        m.box(x0, YB, c2, x0 + 1.45F, YB + 5.8F, z1, BigMat.WOOD1, null, 4);
                    }
                }
            }
        }
        if (a.speed > 0.05F) {                                                                                 //sciure
            for (int s : stands) {
                for (int c = 0; c < 10; c++) {
                    if (BigMachines.hash(c, 92 + s) > a.speed) {
                        continue;
                    }
                    float ph = (a.phase * 0.09F + c * 0.1F) % 1F;
                    float x = cx + (c % 3 - 1) * 0.8F, z = s - 2 - ph * 7, y = YB + 1 + ph * 3 - ph * ph * 4;
                    m.box(x - 0.3F, Math.max(YB, y), z - 0.3F, x + 0.3F, Math.max(YB, y) + 0.5F, z + 0.3F, BigMat.WOOD1, null, 1);
                }
            }
        }
    }

    // ------------------------------------------------------------------ trieur optique (continu)

    private static void sortLife(BigMesh m, float w, float l, int[] stands, int tint, BigMachines.Anim a) {
        float cx = w / 2, bw = belt(w), v = 0.3F, pos = a.phase * v;
        cleats(m, w, l, pos);
        float sp = a.speed;
        int s0 = stands.length > 0 ? stands[0] : (int) (l / 2);
        for (int i = 0; i < (int) (l * 1.2F); i++) {                                                          //grains sur la bande
            int lane = (int) (BigMachines.hash(i, 93) * 5);
            float z = (i * 0.83F + pos) % l;
            if (z < 3 || z > l - 3 || BigMachines.hash(i, 94) > sp) {
                continue;
            }
            boolean bad = BigMachines.hash(i, 95) < 0.18F;
            if (bad && z > s0 + 7.5F) {
                continue;                                                                                      //rebut souffle
            }
            float x = cx - bw / 2 + 1.5F + lane * (bw - 3) / 4F;
            m.color(bad ? shade(tint, 0.45F) : 0xFF000000 | tint);
            m.box(x - 0.6F, YB, z - 0.6F, x + 0.6F, YB + 0.9F, z + 0.6F, BigMat.STRIP, null, 1);
        }
        m.color(0xFFFFFFFF);
        for (int s : stands) {
            m.glow(sp > 0.05F ? 13 : 2).color(0xFFB8F0FF);
            m.box(cx - bw / 2, 10.6F, s - 4, cx + bw / 2, 11, s + 4, BigMat.LED, null, 2);                   //rampe lumineuse
            m.glow(0).color(0xFFFFFFFF);
            if (sp < 0.05F) {
                continue;
            }
            for (int c = 0; c < 6; c++) {                                                                       //rebuts souffles vers la goulotte
                float ph = (a.phase * 0.07F + c * 0.17F) % 1F;
                if (BigMachines.hash(c + (int) (a.phase * 0.07F + c * 0.17F) * 7, 96) > sp * 0.8F) {
                    continue;
                }
                float x = cx + (c % 3 - 1) * 2 + ph * (bw / 2 + 4), y = YB + 1 + ph * 7 - ph * ph * 9, z = s + 8 + ph * 4;
                m.color(shade(tint, 0.45F));
                m.box(x - 0.5F, Math.max(1, y), z - 0.5F, x + 0.5F, Math.max(1, y) + 0.8F, z + 0.5F, BigMat.STRIP, null, 1);
            }
            m.color(0xFFFFFFFF);
        }
    }
}
