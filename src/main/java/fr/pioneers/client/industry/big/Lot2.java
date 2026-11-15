package fr.pioneers.client.industry.big;

import org.joml.Matrix4f;

/**
 * 0.19.15 : machines interieures du lot 2 (apercus valides par Guillaume le 25/10/2026) : four de fusion, four tubulaire, four a cuve,
 * four rotatif et broyeur a boulets, cuve agitee, colonne a plateaux, lit catalytique, cellule d'electrolyse.
 * <p>
 * Repere canonique de {@link BigMesh} (px) : x de 0 a w en travers, y de 0 a h, z de 0 a l le long de l'axe. Les machines verticales sont
 * centrees en (w / 2, l / 2) et s'inscrivent dans le PROFIL de l'interieur (domes, ventres : rangees plus etroites en haut ou en bas ;
 * sections en croix sans coins). Partie fixe dans les methodes sans suffixe, partie animee (regime, phase) dans les {@code ...Life}.
 */
final class Lot2 {

    private Lot2() {
    }

    // ------------------------------------------------------------------ profil

    /** Rayon libre (px) dans la rangee de hauteur y ; sans profil : demi-largeur. Section en croix (sans coins) : cercle inscrit dans la croix. */
    static float room(int[] prof, float y, float w) {
        if (prof.length == 0) {
            return w / 2 - 1.5F;
        }
        int i = Math.max(0, Math.min(prof.length - 1, (int) Math.floor(y / 16)));
        int p = prof[i];
        if (p == 0) {
            return 0;
        }
        int cells = p / 10;
        return p % 10 == 1 ? cells * 8 - 1.5F : cells * 8 * 0.84F - 1;
    }

    /** Rayon libre minimal entre y0 et y1. */
    static float roomBetween(int[] prof, float y0, float y1, float w) {
        float r = Float.MAX_VALUE;
        for (float y = y0; y < y1; y += 4) {
            r = Math.min(r, room(prof, y, w));
        }
        return Math.min(r, room(prof, y1 - 0.01F, w));
    }

    /** Premiere rangee (px, son bas) dont le rayon libre atteint {@code min}. */
    static float firstWide(int[] prof, float min, float w, float h) {
        for (float y = 0; y < h; y += 16) {
            if (room(prof, y + 1, w) >= min) {
                return y;
            }
        }
        return 0;
    }

    /** Plus haut y (px) atteignable depuis y0 en gardant au moins le rayon r. */
    static float topWith(int[] prof, float y0, float r, float w, float h) {
        float y = y0;
        while (y < h && room(prof, y + 1, w) >= r) {
            y += 16;
            y = (float) Math.floor(y / 16) * 16;
        }
        return Math.min(h, y);
    }

    private static void disc(BigMesh m, float cx, float cz, float r, float y0, float y1, BigMat mat, int sides) {
        m.cyl('y', cx, cz, r, y0, y1, mat, sides, null, 4);
    }

    private static void ring(BigMesh m, float cx, float cz, float r, float y0, float y1, BigMat mat, float thick, int sides) {
        m.cyl('y', cx, cz, r, y0, y1, mat, sides, null, 4, false, thick);
    }

    private static int rgb(int tint, float k, int alpha) {
        int r = Math.min(255, (int) ((tint >> 16 & 255) * k)), g = Math.min(255, (int) ((tint >> 8 & 255) * k)), b = Math.min(255, (int) ((tint & 255) * k));
        return alpha << 24 | r << 16 | g << 8 | b;
    }

    private static int grey(float k) {
        int v = Math.max(0, Math.min(255, (int) (255 * k)));
        return 0xFF000000 | v << 16 | v << 8 | v;
    }

    // ------------------------------------------------------------------ four de fusion : creuset, bain, bobine d'induction, electrodes

    private record Hearth(float y, float r, float bath, float top, float er) {
    }

    private static Hearth hearth(int[] prof, float w, float h) {
        float y = firstWide(prof, 15, w, h);
        float r = Math.min(roomBetween(prof, y + 1, Math.min(h, y + 26), w), Math.min(w, h) / 2 - 1.5F) - 2.5F;
        float er = r * 0.42F;
        float top = topWith(prof, y, er + 4, w, h) - 6;
        return new Hearth(y, r, y + 22, Math.max(y + 30, top), er);
    }

    static void furnace(BigMesh m, float w, float h, float l, int[] prof) {
        float cx = w / 2, cz = l / 2;
        Hearth hz = hearth(prof, w, h);
        float r = hz.r, yb = hz.y;
        ring(m, cx, cz, r, yb, yb + 26, BigMat.REFR_L, 4, 24);                                    //creuset de briques
        disc(m, cx, cz, r - 3, yb, yb + 3, BigMat.REFR_D, 20);
        for (int k = 0; k < 5; k++) {                                                             //bobine d'induction (spires de cuivre)
            float yk = yb + 3 + k * 4.6F;
            ring(m, cx, cz, r + 1.8F, yk, yk + 2.6F, BigMat.ANODE, 1.8F, 24);
        }
        for (int k = 0; k < 3; k++) {                                                             //electrodes de graphite
            double a = Math.toRadians(k * 120 + 30);
            float px = cx + (float) (hz.er * Math.cos(a)), pz = cz + (float) (hz.er * Math.sin(a));
            disc(m, px, pz, 2.6F, hz.bath - 1, hz.top, BigMat.COKE, 10);
            m.box(px - 1.6F, hz.top, pz - 1.6F, px + 1.6F, hz.top + 3, pz + 1.6F, BigMat.ANODE, null, 3);   //pinces
        }
        float rr = Math.min(r - 1, room(prof, hz.top + 4, w) - 1);
        ring(m, cx, cz, rr, hz.top + 1, hz.top + 4, BigMat.STEEL_D, 3, 20);                        //anneau de voute
        m.box(cx - rr * 0.6F, hz.top + 3, cz - 2, cx + rr * 0.6F, hz.top + 6, cz + 2, BigMat.ANODE, null, 4);  //barre de courant
    }

    static void furnaceLife(BigMesh m, float w, float h, float l, int[] prof, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2, s = a.speed;
        Hearth hz = hearth(prof, w, h);
        float r = hz.r - 3.5F, hb = hz.bath;
        m.color(grey(0.62F + 0.38F * s)).glow(Math.round(7 + 8 * s));
        disc(m, cx, cz, r, hb - 2, hb, BigMat.MOLTEN, 20);                                        //bain en fusion
        m.color(0xFFFFFFFF).glow(0);
        for (int k = 0; k < 9; k++) {                                                             //charge qui fond en derivant lentement
            double ang = BigMachines.hash(k, 1) * Math.PI * 2 + a.phase * 0.004;
            float rr = (0.25F + 0.55F * BigMachines.hash(k, 2)) * (r - 3), sz = (2.2F + 2.2F * BigMachines.hash(k, 3)) * (0.75F + 0.25F * (1 - s));
            float px = cx + (float) (rr * Math.cos(ang)), pz = cz + (float) (rr * Math.sin(ang));
            m.box(px - sz, hb - 1, pz - sz, px + sz, hb + sz * 0.8F, pz + sz, k % 2 == 0 ? BigMat.SLAG : BigMat.ORE1, null, 4);
        }
        if (s > 0.05F) {                                                                         //arcs au contact du bain
            m.glow(15);
            for (int k = 0; k < 3; k++) {
                double an = Math.toRadians(k * 120 + 30);
                float px = cx + (float) (hz.er * Math.cos(an)), pz = cz + (float) (hz.er * Math.sin(an));
                float f = s * (0.75F + 0.25F * (float) Math.sin(a.phase * 0.9F + k * 2.1F));
                disc(m, px, pz, 2.6F + 1.4F * f, hb - 0.5F, hb + 2.5F * f, BigMat.FLAME_C, 10);
            }
            m.glow(0);
        }
    }

    // ------------------------------------------------------------------ four tubulaire : serpentins, bruleurs

    static void heater(BigMesh m, float w, float h, float l, int[] prof) {
        float cx = w / 2, cz = l / 2;
        m.glow(6);
        if (w == l) {
            //four rond (cowper, cokefaction, grillage) : tubes radiants verticaux en couronne
            float r = roomBetween(prof, 2, Math.min(h, 40), w) - 3;
            float top = topWith(prof, 0, r + 2, w, h) - 8;
            int n = Math.max(8, Math.round(r * 0.6F));
            for (int k = 0; k < n; k++) {
                double a = Math.PI * 2 * k / n;
                float px = cx + (float) (r * Math.cos(a)), pz = cz + (float) (r * Math.sin(a));
                m.cyl('y', px, pz, 1.6F, 8, Math.max(16, top), BigMat.GLOWROD, 8, null, 4);
            }
            m.glow(0);
            disc(m, cx, cz, r + 1.5F, 0, 3, BigMat.REFR_D, 20);
            ring(m, cx, cz, r + 1.5F, Math.max(16, top), Math.max(16, top) + 3, BigMat.STEEL_D, 3, 20);
            disc(m, cx, cz, 4, 3, 6, BigMat.REFR_L, 12);                                         //bruleur central
            return;
        }
        //four en pave : serpentins horizontaux le long des deux grandes parois, coudes alternes aux bouts
        float k = heaterK(w), tr = 1.8F * k, st = 6 * k;
        for (float xs : new float[]{5 * k, w - 5 * k}) {
            int i = 0;
            for (float y = 14 * k; y < h - 10 * k; y += st, i++) {
                m.glow(6).cyl('z', xs, y, tr, 7, l - 7, i % 2 == 0 ? BigMat.GLOWROD : BigMat.GLOWBED, 10, null, 4);
                m.glow(0);
                float za = i % 2 == 0 ? l - 7 : 4, zb = i % 2 == 0 ? l - 4 : 7;
                if (y + st < h - 10 * k) {
                    m.box(xs - tr, y, za, xs + tr, y + st, zb, BigMat.STEEL_D, null, 4);
                }
            }
        }
        m.glow(0);
        m.box(1, 0, 1, w - 1, 3, l - 1, BigMat.REFR_D, null, 4);                                    //sole
        float bh = 3 * k;
        for (float z = 10; z < l - 8; z += 14) {
            m.box(cx - bh, 3, z - bh, cx + bh, 3 + bh, z + bh, BigMat.REFR_L, null, 4);              //bruleurs
        }
    }

    /** Echelle du four en pave : 1 pour une largeur de 3 blocs et plus. */
    private static float heaterK(float w) {
        return Math.max(0.35F, Math.min(1, w / 48));
    }

    static void heaterLife(BigMesh m, float w, float h, float l, int[] prof, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2, s = a.speed;
        if (s < 0.03F) {
            return;
        }
        m.glow(15);
        if (w == l) {
            float f = s * (0.82F + 0.18F * (float) Math.sin(a.phase * 0.6F));
            float top = Math.min(h - 6, 6 + 34 * f);
            disc(m, cx, cz, 3.6F, 6, 6 + (top - 6) * 0.45F, BigMat.FLAME, 12);
            disc(m, cx, cz, 2.0F, 6, top, BigMat.FLAME_C, 10);
            m.glow(0);
            return;
        }
        int i = 0;
        float k = heaterK(w), y0 = 3 + 3 * k, fh = Math.min(1, (h - y0 - 2) / 22F);
        for (float z = 10; z < l - 8; z += 14, i++) {
            float f = s * (0.8F + 0.2F * (float) Math.sin(a.phase * 0.55F + i * 1.2F)) * fh;
            m.box(cx - 3.4F * k, y0, z - 3.4F * k, cx + 3.4F * k, y0 + 7 * f, z + 3.4F * k, BigMat.FLAME, null, 4);
            m.box(cx - 2.4F * k, y0, z - 2.4F * k, cx + 2.4F * k, y0 + 14 * f, z + 2.4F * k, BigMat.FLAME, null, 4);
            m.box(cx - 1.4F * k, y0, z - 1.4F * k, cx + 1.4F * k, y0 + 22 * f, z + 1.4F * k, BigMat.FLAME_C, null, 4);
        }
        m.glow(0);
    }

    // ------------------------------------------------------------------ four a cuve : creuset, tuyeres, couches qui descendent

    private static float shaftHearth(int[] prof, float w, float h) {
        return firstWide(prof, 15, w, h);
    }

    static void shaft(BigMesh m, float w, float h, float l, int[] prof) {
        float cx = w / 2, cz = l / 2;
        float yb = shaftHearth(prof, w, h);
        float r = roomBetween(prof, yb + 1, Math.min(h, yb + 28), w) - 1;
        ring(m, cx, cz, r, yb, yb + 28, BigMat.REFR_L, 4, 24);                                    //creuset
        disc(m, cx, cz, r - 3, yb, yb + 2, BigMat.REFR_D, 20);
        for (int k = 0; k < 10; k++) {                                                            //tuyeres
            double a = Math.PI * 2 * k / 10;
            float px = cx + (float) ((r - 3) * Math.cos(a)), pz = cz + (float) ((r - 3) * Math.sin(a));
            m.box(px - 1.2F, yb + 20, pz - 1.2F, px + 1.2F, yb + 22.4F, pz + 1.2F, BigMat.CHROME, null, 2);
        }
        float top = h - 8;
        float rt = Math.max(3, room(prof, top + 2, w) - 1);
        ring(m, cx, cz, rt, top, top + 3, BigMat.STEEL_D, Math.min(3, rt * 0.4F), 16);           //gueulard
    }

    static void shaftLife(BigMesh m, float w, float h, float l, int[] prof, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2, s = a.speed;
        float yb = shaftHearth(prof, w, h);
        float r = roomBetween(prof, yb + 1, Math.min(h, yb + 28), w) - 4.5F;
        m.color(grey(0.6F + 0.4F * s)).glow(Math.round(6 + 9 * s));
        disc(m, cx, cz, r, yb + 2, yb + 6, BigMat.MOLTEN, 20);                                   //fonte
        m.color(0xFFFFFFFF).glow(0);
        disc(m, cx, cz, r, yb + 6, yb + 10, BigMat.SLAG, 20);                                    //laitier
        if (s > 0.03F) {                                                                         //jets des tuyeres
            m.glow(15);
            for (int k = 0; k < 10; k++) {
                double an = Math.PI * 2 * k / 10;
                float f = s * (0.8F + 0.2F * (float) Math.sin(a.phase * 0.7F + k));
                float qx = cx + (float) ((r - 4) * Math.cos(an)), qz = cz + (float) ((r - 4) * Math.sin(an));
                float d = 1.4F + 1.2F * f;
                m.box(qx - d, yb + 19.6F, qz - d, qx + d, yb + 22.8F, qz + d, BigMat.FLAME, null, 2);
            }
            m.glow(0);
        }
        //charge : couches minerai / coke qui descendent (rougeoient pres des tuyeres)
        float lay = 7, base = yb + 24, top = h - 8;
        float off = (a.phase * 0.035F) % (2 * lay);
        int k = 0;
        for (float y = base - off; y < top; y += lay, k++) {
            float ya = Math.max(y, base), yc = Math.min(y + lay, top);
            if (yc <= ya + 0.2F) {
                continue;
            }
            float rr = Math.min(room(prof, ya + 0.5F, w), room(prof, yc - 0.5F, w)) - 1.5F;
            if (rr < 2) {
                continue;
            }
            boolean coke = Math.floorMod(k + (int) Math.floor((a.phase * 0.035F) / lay), 2) == 1;
            float t = (ya - base) / Math.max(1, top - base);
            if (!coke && t < 0.2F) {
                m.glow(Math.round(10 * s));
            }
            disc(m, cx, cz, rr, ya, yc, coke ? BigMat.COKE : (t < 0.2F ? BigMat.GLOWBED : BigMat.ORE1), 18);
            m.glow(0);
        }
    }

    // ------------------------------------------------------------------ four rotatif et broyeur a boulets : tambour sur galets

    /** Rayon de la virole : section en croix (cercle inscrit) ; fut d'un seul bloc : presque tout le bloc. */
    private static float drumR(float w, float h) {
        float d = Math.min(w, h);
        return d <= 16 ? d / 2 - 1.5F : d * 0.42F - 3 * Math.min(1, d / 80);
    }

    /** Echelle des details du tambour : 1 pour une virole de 30 px de rayon (fut de 5 blocs). */
    private static float drumK(float r) {
        return Math.max(0.25F, Math.min(1, r / 30));
    }

    static void drum(BigMesh m, float w, float h, float l, boolean mill) {
        float cx = w / 2, cy = h / 2, r = drumR(w, h), k = drumK(r);
        float za = 8, zb = l - 8;
        int spin = m.spin('z', cx, cy, 0, 0.5F);
        m.cyl('z', cx, cy, r, za, zb, mill ? BigMat.DRUM : BigMat.STEEL_D, 24, null, 4, false, 1.4F * k);      //virole
        if (!mill) {
            m.cyl('z', cx, cy, r - 1.4F * k, za, zb, BigMat.REFR_L, 24, null, 4, false, 1.2F * k);            //garnissage refractaire
        } else {
            for (int i = 0; i < 8; i++) {                                                                      //releveurs
                m.box(cx - k, cy + r - 4 * k, za + 2, cx + k, cy + r - 1.4F * k, zb - 2, BigMat.CAST_L, BigMesh.rot('z', cx, cy, 0, i * 45), 3);
            }
        }
        float[] tires = {za + (zb - za) * 0.22F, za + (zb - za) * 0.78F};
        for (float zt : tires) {
            m.cyl('z', cx, cy, r + 1.8F * k, zt - 3 * k, zt + 3 * k, BigMat.CHROME, 28, null, 4, false, 1.8F * k);   //bandages
        }
        float zg = (za + zb) / 2;
        m.cyl('z', cx, cy, r + 2.2F * k, zg - 2 * k, zg + 2 * k, BigMat.CAST_L, 32, null, 4, false, 2.2F * k);    //couronne dentee
        for (int i = 0; i < 36; i++) {
            m.box(cx - 0.8F * k, cy + r + 2.2F * k, zg - 2 * k, cx + 0.8F * k, cy + r + 3.2F * k, zg + 2 * k, BigMat.CAST_L,
                  BigMesh.rot('z', cx, cy, 0, i * 10), 2);
        }
        if (mill) {
            m.cyl('z', cx, cy, r * 0.42F, 1, za, BigMat.CAST_L, 14, null, 4);                                  //tourillon d'alimentation
            m.cyl('z', cx, cy, r * 0.5F, zb, l - 1, BigMat.GRATE, 16, null, 4, false, 0.8F);                   //trommel de sortie
        }
        m.group(spin);
        m.fixed();
        //galets porteurs (tournent en sens inverse) et pignon d'attaque
        float rg = 3 * k, yr = cy - r - 1.8F * k - rg + 0.4F * k;
        for (float zt : tires) {
            for (int side = -1; side <= 1; side += 2) {
                float xr = cx + side * r * 0.45F;
                m.spin('z', xr, yr, 0, -0.5F * (r + 1.8F * k) / rg);
                m.cyl('z', xr, yr, rg, zt - 4 * k, zt + 4 * k, BigMat.ROLL, 12, null, 3);
                m.fixed();
                if (yr - rg > 0.6F) {
                    m.box(xr - 2.5F * k, 0, zt - 5 * k, xr + 2.5F * k, yr - rg + 0.5F, zt + 5 * k, BigMat.CAST, null, 4);  //paliers
                }
            }
        }
        float rp = 2.5F * k, yp = cy - r - 2.2F * k - rp - 0.5F * k;
        m.spin('z', cx, yp, 0, -0.5F * (r + 2.2F * k) / rp);
        m.cyl('z', cx, yp, rp, zg - 2 * k, zg + 2 * k, BigMat.CAST_L, 10, null, 3);
        m.fixed();
        float mh = Math.min(9, h * 0.4F), mw = Math.min(5, w * 0.3F);
        m.box(cx - mw, 0.5F, 0.5F, cx + mw, 0.5F + mh, Math.min(6.5F, za - 1), BigMat.MOTOR, null, 4);                //moteur
        if (!mill) {
            m.cyl('z', cx, cy, Math.max(0.8F, 2.2F * k), zb - 2, l - 1, BigMat.CHROME, 10, null, 4);           //bruleur
        }
    }

    static void drumLife(BigMesh m, float w, float h, float l, boolean mill, BigMachines.Anim a) {
        float k0 = drumK(drumR(w, h));
        float cx = w / 2, cy = h / 2, r = drumR(w, h) - (mill ? 1.6F : 2.8F) * k0, s = a.speed;
        float za = 8, zb = l - 8;
        int nb = Math.max(4, (int) ((zb - za) / 6));
        for (int k = 0; k < nb; k++) {
            float t = k / (float) Math.max(1, nb - 1);
            float z = za + 4 + t * (zb - za - 8);
            //la charge reste en bas, remontee du cote ou tourne le tambour, et roule
            float ph = (k * 0.37F + a.phase * 0.011F) % 1F;
            float ang = (float) Math.toRadians(-35 + 70 * ph);
            float px = cx + (r - 5 * k0) * 0.55F * (float) Math.sin(ang), py = cy - (r - 5 * k0) * 0.9F;
            float sz = (mill ? 4 : 5) * k0;
            BigMat mat;
            if (mill) {
                mat = k % 3 == 0 ? BigMat.ORE2 : BigMat.BALL;
            } else {
                mat = t < 0.45F ? BigMat.ORE1 : BigMat.GLOWBED;
                if (t >= 0.45F) {
                    m.glow(Math.round((4 + 11 * (t - 0.45F) / 0.55F) * s));
                }
            }
            m.box(px - sz, py - k0, z - 2.6F * k0, px + sz, py + sz * 0.7F, z + 2.6F * k0, mat, null, 3);
            m.glow(0);
            if (mill && k % 2 == 0 && BigMachines.hash(k, 9) < s) {                                            //boulets en cascade
                float c = (a.phase * 0.009F + k * 0.13F) % 1F;
                float rb = r - 6 * k0, d = 1.8F * k0;
                float bx = cx + rb * 0.6F * (0.6F - c), by = cy - rb * 0.2F + rb * 0.7F * (float) Math.sin(Math.PI * c);
                m.box(bx - d, by - d, z - d, bx + d, by + d, z + d, BigMat.BALL, null, 2);
            }
        }
        if (!mill && s > 0.03F) {                                                                              //flamme du bruleur
            float f = s * (0.85F + 0.15F * (float) Math.sin(a.phase * 0.35F));
            m.glow(15);
            m.cyl('z', cx, cy, (3.6F * f + 0.5F) * k0, zb - 26 * f * k0, zb - 2, BigMat.FLAME, 12, null, 4);
            m.cyl('z', cx, cy, 2.0F * k0, zb - 34 * f * k0, zb - 20 * f * k0, BigMat.FLAME_C, 10, null, 4);
            m.glow(0);
        }
    }

    // ------------------------------------------------------------------ cuve agitee

    private record Vessel(float r, float top, float lvl, float motor) {
    }

    private static Vessel vessel(int[] prof, float w, float h) {
        float r0 = room(prof, 1, w);
        float top = topWith(prof, 0, r0 - 0.5F, w, h);
        float r = Math.min(r0, w / 2 - 1.5F) - 0.5F;
        float motor = top >= h - 4 ? h - 10 : top + 2;
        return new Vessel(r, top, top * 0.66F, motor);
    }

    static void tank(BigMesh m, float w, float h, float l, int[] prof) {
        float cx = w / 2, cz = l / 2;
        Vessel v = vessel(prof, w, h);
        float r = v.r, lvl = v.lvl;
        for (int k = 0; k < 4; k++) {                                                                          //chicanes
            m.box(cx + r - 2.6F, 4, cz - 0.8F, cx + r - 0.6F, lvl + 4, cz + 0.8F, BigMat.STEEL_D, BigMesh.rot('y', cx, 0, cz, 90 * k + 45), 4);
        }
        for (int k = 0; k < 4; k++) {                                                                          //serpentin de chauffe
            float yk = 8 + k * (lvl - 14) / 3;
            ring(m, cx, cz, r - 1.8F, yk, yk + 2.2F, BigMat.ANODE, 1.6F, 24);
        }
        ring(m, cx, cz, r - 4, 2, 3.6F, BigMat.PIPE, 1.4F, 20);                                                //diffuseur
        float mt = Math.min(v.motor, h - 9);
        m.box(cx - 5, mt, cz - 5, cx + 5, mt + 8, cz + 5, BigMat.MOTOR, null, 4);                              //moteur et reducteur
        disc(m, cx, cz, 3.2F, mt - 4, mt, BigMat.STEEL_D, 12);
        int spin = m.spin('y', cx, 0, cz, 6);
        m.box(cx - 1.6F, 6, cz - 1.6F, cx + 1.6F, mt - 4, cz + 1.6F, BigMat.CHROME, null, 4);                  //arbre
        int nimp = lvl > 60 ? 3 : 2;
        for (int i = 0; i < nimp; i++) {
            float yi = 10 + i * (lvl - 18) / Math.max(1, nimp - 1);
            m.group(spin);
            disc(m, cx, cz, 3.4F, yi - 2, yi + 2, BigMat.STEEL_D, 10);
            Matrix4f pitch = BigMesh.rot('x', 0, yi, cz, 32);
            for (int k = 0; k < 4; k++) {                                                                      //pales inclinees
                m.box(cx + 3, yi - 0.6F, cz - 3, cx + r * 0.62F, yi + 0.6F, cz + 3, BigMat.CHROME, BigMesh.chain(pitch, BigMesh.rot('y', cx, 0, cz, k * 90)), 3);
            }
        }
        m.fixed();
    }

    static void tankLife(BigMesh m, float w, float h, float l, int[] prof, int tint, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2, s = a.speed;
        Vessel v = vessel(prof, w, h);
        float r = v.r, lvl = v.lvl;
        for (int k = 0; k < 18; k++) {                                                                         //bulles du diffuseur
            if (BigMachines.hash(k, 4) > s) {
                continue;
            }
            float ph = (a.phase * 0.011F + k * 0.137F) % 1F;
            double an = k * 2.4;
            float rr = (0.3F + 0.6F * BigMachines.hash(k, 5)) * (r - 5);
            float bx = cx + (float) (rr * Math.cos(an)), bz = cz + (float) (rr * Math.sin(an)), by = 4 + ph * (lvl - 6);
            float d = 0.9F + 0.6F * ph;
            m.box(bx - d, by - d, bz - d, bx + d, by + d, bz + d, BigMat.BUBBLE, null, 2);
        }
        for (int k = 0; k < 8; k++) {                                                                          //tourbillon en surface
            double an = Math.toRadians(k * 45) + a.phase * 0.1 * s;
            float r1 = r * (0.35F + 0.12F * (k % 2));
            float px = cx + (float) (r1 * Math.cos(an)), pz = cz + (float) (r1 * Math.sin(an));
            m.box(px - 2.2F, lvl - 0.3F, pz - 2.2F, px + 2.2F, lvl + 0.4F + 0.3F * s, pz + 2.2F, BigMat.FROTH, null, 2);
        }
        m.color(rgb(tint, 1, 0xA8));
        disc(m, cx, cz, r - 0.5F, 0.5F, lvl, BigMat.LIQUID, 24);                                              //liquide
        m.color(0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ colonne a plateaux et lit catalytique

    private static final float TRAY = 22;

    private static int trays(float h) {
        return Math.max(1, (int) ((h - 10) / TRAY));
    }

    static void column(BigMesh m, float w, float h, float l, int[] prof) {
        float cx = w / 2, cz = l / 2;
        int n = trays(h);
        for (int i = 0; i < n; i++) {
            float yt = 10 + i * TRAY;
            float r = room(prof, yt + 1, w) - 0.5F;
            int side = i % 2 == 1 ? 1 : -1;
            disc(m, cx, cz, r, yt, yt + 1.2F, BigMat.GRATE, 20);                                               //plateau perfore
            float xw = cx + side * r * 0.5F;
            m.box(Math.min(xw, xw + side * 1.2F), yt + 1, cz - r * 0.8F, Math.max(xw, xw + side * 1.2F), yt + 4.4F, cz + r * 0.8F, BigMat.STEEL_D, null, 4);  //deversoir
            if (i > 0) {
                m.box(Math.min(xw, xw + side), yt - TRAY + 5, cz - r * 0.78F, Math.max(xw, xw + side), yt, cz + r * 0.78F, BigMat.CAST, null, 4);  //descente
            }
        }
    }

    static void columnLife(BigMesh m, float w, float h, float l, int[] prof, int tint, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2, s = a.speed;
        int n = trays(h);
        for (int i = 0; i < n; i++) {
            float yt = 10 + i * TRAY, t = i / (float) Math.max(1, n - 1);
            float r = room(prof, yt + 1, w) - 1.1F;
            int side = i % 2 == 1 ? 1 : -1;
            for (int k = 0; k < 14; k++) {                                                                     //mousse qui bouillonne
                if (BigMachines.hash(k, i + 20) > s) {
                    continue;
                }
                float ph = (a.phase * 0.022F + k * 0.29F + i * 0.13F) % 1F;
                double an = k * 0.63 + i;
                float kk = Math.max(0.3F, Math.min(1, r / 20));
                float rr = (0.15F + 0.75F * BigMachines.hash(k, i + 40)) * Math.max(0, r - 3 * kk);
                float bx = cx + (float) (rr * Math.cos(an)) - side * 3 * kk, bz = cz + (float) (rr * Math.sin(an));
                float hh = (1.5F + 3 * (float) Math.sin(Math.PI * ph)) * kk, d = 1.3F * kk;
                m.box(bx - d, yt + 3.6F, bz - d, bx + d, yt + 3.6F + hh, bz + d, BigMat.FROTH, null, 2);
            }
            //liquide : coupe lourde (plus sombre) en bas, legere en haut
            m.color(rgb(tint, 0.72F + 0.5F * t, 0xB0));
            disc(m, cx, cz, r, yt + 1.2F, yt + 4, BigMat.LIQUID, 20);
            m.color(0xFFFFFFFF);
        }
    }

    private static int beds(float h) {
        return Math.max(1, Math.min(3, (int) ((h - 12) / 32)));
    }

    static void packed(BigMesh m, float w, float h, float l, int[] prof) {
        float cx = w / 2, cz = l / 2;
        float top = topWith(prof, 0, room(prof, 1, w) - 0.5F, w, h);
        int beds = beds(top);
        float hb = (top - 12) / beds;
        for (int i = 0; i < beds; i++) {
            float ya = 6 + i * hb;
            float r = roomBetween(prof, ya + 1, ya + hb - 2, w) - 0.5F;
            disc(m, cx, cz, r, ya, ya + 1.4F, BigMat.GRATE, 20);                                              //grille support
            disc(m, cx, cz, r - 0.4F, ya + 1.4F, ya + hb - 9, BigMat.CAT, 20);                                 //lit de granules
            for (int k = 0; k < 14; k++) {                                                                     //granules en surface
                double an = k * 2.3;
                float rr = (0.15F + 0.8F * BigMachines.hash(k, i)) * (r - 3);
                float px = cx + (float) (rr * Math.cos(an)), pz = cz + (float) (rr * Math.sin(an));
                m.box(px - 1.3F, ya + hb - 9, pz - 1.3F, px + 1.3F, ya + hb - 7.6F, pz + 1.3F, BigMat.CAT, null, 2);
            }
            disc(m, cx, cz, r - 1, ya + hb - 4, ya + hb - 3, BigMat.GRATE, 20);                               //plateau distributeur
        }
    }

    static void packedLife(BigMesh m, float w, float h, float l, int[] prof, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2, s = a.speed;
        float top = topWith(prof, 0, room(prof, 1, w) - 0.5F, w, h);
        int beds = beds(top);
        float hb = (top - 12) / beds;
        for (int i = 0; i < beds; i++) {
            float ya = 6 + i * hb;
            float r = roomBetween(prof, ya + 1, ya + hb - 2, w) - 0.5F;
            for (int k = 0; k < 10; k++) {                                                                     //gouttes du distributeur
                if (BigMachines.hash(k, i + 60) > s) {
                    continue;
                }
                float ph = (a.phase * 0.011F + k * 0.17F) % 1F;
                double an = k * 0.8;
                float bx = cx + (float) (r * 0.5F * Math.cos(an)), bz = cz + (float) (r * 0.5F * Math.sin(an));
                float by = ya + hb - 4 - ph * 4.6F;
                m.box(bx - 0.6F, by - 1.2F, bz - 0.6F, bx + 0.6F, by, bz + 0.6F, BigMat.FROTH, null, 2);
            }
            //vapeur chaude au-dessus du lit (legere lueur au regime)
            m.color(0x70E8B070).glow(Math.round(5 * s));
            disc(m, cx, cz, r - 0.6F, ya + hb - 7.6F, ya + hb - 4, BigMat.LIQUID, 20);
            m.color(0xFFFFFFFF).glow(0);
        }
    }

    // ------------------------------------------------------------------ cellule d'electrolyse

    static void cell(BigMesh m, float w, float h, float l) {
        for (float xs : new float[]{10, w - 10}) {
            m.box(xs - 2, h - 6, 3, xs + 2, h - 2, l - 3, BigMat.ANODE, null, 4);                              //barres omnibus
        }
        int k = 0;
        for (float z = 10; z < l - 8; z += 8, k++) {
            m.box(8, 4, z - 0.9F, w - 8, h - 8, z + 0.9F, k % 2 == 0 ? BigMat.ANODE : BigMat.CATHODE, null, 4);  //plaques
            m.box(6, h - 8, z - 1.2F, w - 6, h - 6, z + 1.2F, BigMat.STEEL_D, null, 4);                       //oreilles
        }
    }

    static void cellLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        float s = a.speed, lvl = Math.min(h - 10, 22);
        int k = 0;
        for (float z = 10; z < l - 8; z += 8, k++) {
            for (int j = 0; j < 8; j++) {
                if (BigMachines.hash(j, k) > s) {
                    continue;
                }
                float ph = (a.phase * 0.017F + j * 0.21F + k * 0.11F) % 1F;
                float bx = 11 + j * (w - 22) / 7, by = 4 + ph * (lvl - 4), dz = k % 2 == 0 ? 2 : -2, d = 0.9F + 0.5F * ph;
                m.box(bx - d, by - d, z + dz - d, bx + d, by + d, z + dz + d, BigMat.BUBBLE, null, 2);
            }
        }
        m.color(rgb(tint, 1, 0x9C));
        m.box(3, 1, 3, w - 3, lvl, l - 3, BigMat.LIQUID, null, 8);                                             //bain
        m.color(0xFFFFFFFF);
    }
}
