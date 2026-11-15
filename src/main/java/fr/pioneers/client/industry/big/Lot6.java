package fr.pioneers.client.industry.big;

import org.joml.Matrix4f;

/**
 * 0.19.20 : machines interieures du lot 6 : halles (scie a wafers, photolithographie, fonderie de semi-conducteurs, separation
 * isotopique par laser, ferme hydroponique, plantation d'arbres), tours (tirage de cristaux, retraitement) et futs (four de diffusion,
 * separateur magnetique, dessaleur, separateur a membranes, autoclave).
 * <p>
 * Halles : la voute retrecit vers le haut ({@code profile}) ; on reste sous la derniere rangee pleine ({@link #hallTop}).
 */
final class Lot6 {

    private Lot6() {
    }

    private static int argb(int rgb, int alpha) {
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    /** Haut (px) des rangees de pleine largeur d'une halle. */
    static float hallTop(int[] prof, float w, float h) {
        if (prof.length == 0) {
            return h;
        }
        int full = prof[0] / 10;
        float y = 0;
        for (int p : prof) {
            if (p / 10 < full) {
                break;
            }
            y += 16;
        }
        return Math.max(16, Math.min(h, y));
    }

    private static void led(BigMesh m, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int glow) {
        m.glow(glow).color(0xFF000000 | rgb);
        m.box(x0, y0, z0, x1, y1, z1, BigMat.LED, null, 1);
        m.glow(0).color(0xFFFFFFFF);
    }

    /** Colonne de voyants (vert / orange / rouge). */
    private static void beacon(BigMesh m, float x, float y, float z, boolean on, int k) {
        m.cyl('y', x, z, 0.4F, y, y + 2, BigMat.CHROME, 6, null, 1);
        int[] c = {0x50E080, 0xF0B040, 0xF04040};
        for (int i = 0; i < 3; i++) {
            boolean lit = on ? i == 0 : i == 1;
            led(m, x - 0.7F, y + 2 + i * 1.2F, z - 0.7F, x + 0.7F, y + 3.1F + i * 1.2F, z + 0.7F, lit ? c[i] : c[i] & 0x404040, lit ? 12 : 0);
        }
    }

    // ------------------------------------------------------------------ salles blanches

    /** Machine de salle blanche. */
    enum Fab {
        SAW, LITHO, FAB
    }

    private record Bays(int n, float pitch, float tw, float tl, float th, float top) {
    }

    private static Bays bays(float w, float l, int[] prof, float h) {
        float top = hallTop(prof, w, h);
        int n = Math.max(1, (int) ((l - 8) / 28));
        float tw = Math.max(6, Math.min(16, w / 2 - 7)), tl = Math.min(20, (l - 8) / n - 6);
        return new Bays(n, (l - 8) / n, tw, tl, Math.max(10, Math.min(30, top - 14)), top);
    }

    static void fab(BigMesh m, Fab f, float w, float h, float l, int[] prof) {
        Bays b = bays(w, l, prof, h);
        m.box(0.5F, 0, 0.5F, w - 0.5F, 1.2F, l - 0.5F, BigMat.GRATE, null, 4);                                 //faux plancher
        for (float z = 4; z < l - 4; z += 12) {                                                                //plafond filtrant
            m.box(1, b.top - 2.5F, z, w - 1, b.top - 1.5F, z + 10, BigMat.CAST_L, null, 4);
            led(m, 2, b.top - 2.7F, z + 1, w - 2, b.top - 2.5F, z + 9, 0xF0F4FF, 10);
        }
        if (f == Fab.FAB) {                                                                                     //rail du transport aerien
            m.box(w / 2 - 1, b.top - 6, 2, w / 2 + 1, b.top - 5, l - 2, BigMat.CAST, null, 4);
            for (float z = 6; z < l - 4; z += 16) {
                m.box(w / 2 - 0.3F, b.top - 5, z, w / 2 + 0.3F, b.top - 2.5F, z + 0.6F, BigMat.CAST, null, 1);
            }
        }
        for (int i = 0; i < b.n; i++) {
            float zc = 4 + b.pitch * (i + 0.5F);
            for (int side = -1; side <= 1; side += 2) {
                float x0 = side < 0 ? 2 : w - 2 - b.tw, x1 = x0 + b.tw, z0 = zc - b.tl / 2, z1 = zc + b.tl / 2;
                float th = f == Fab.LITHO ? b.th : f == Fab.FAB ? b.th * (0.55F + 0.4F * BigMachines.hash(i, side + 5)) : Math.min(b.th, 14);
                m.box(x0, 1.2F, z0, x1, th, z1, BigMat.CAST_L, null, 6);                                         //carter
                float fx = side < 0 ? x1 : x0;                                                                  //face cote allee
                m.box(fx - 0.1F, th * 0.45F, z0 + 1.5F, fx + 0.1F, th - 2, z1 - 1.5F, BigMat.GLASS, null, 4);    //hublot
                m.box(side < 0 ? x1 : x0 - 2.5F, 6, zc - 2, side < 0 ? x1 + 2.5F : x0, 9, zc + 2, BigMat.STEEL_D, null, 2);  //port de chargement
                if (f == Fab.SAW) {
                    m.box(x0 + 1, th * 0.45F - 1, z0 + 1, x1 - 1, th * 0.45F, z1 - 1, BigMat.CAST, null, 4);    //table
                } else if (f == Fab.LITHO) {
                    m.box(x0 + 2, th - 6, z0 + 2, x1 - 2, th - 4, z1 - 2, BigMat.STEEL_D, null, 4);              //reticule
                    m.cyl('y', (x0 + x1) / 2, zc, Math.min(3.5F, b.tw * 0.25F), th * 0.6F, th - 6, BigMat.CAST, 12, null, 4);  //objectif
                    m.box(x0 + 1, th * 0.45F - 1, z0 + 1, x1 - 1, th * 0.45F, z1 - 1, BigMat.CAST, null, 4);
                }
            }
        }
    }

    static void fabLife(BigMesh m, Fab f, float w, float h, float l, int[] prof, BigMachines.Anim a) {
        Bays b = bays(w, l, prof, h);
        float s = a.speed;
        boolean on = s > 0.05F;
        for (int i = 0; i < b.n; i++) {
            float zc = 4 + b.pitch * (i + 0.5F);
            for (int side = -1; side <= 1; side += 2) {
                float x0 = side < 0 ? 2 : w - 2 - b.tw, x1 = x0 + b.tw, cx = (x0 + x1) / 2;
                float th = f == Fab.LITHO ? b.th : f == Fab.FAB ? b.th * (0.55F + 0.4F * BigMachines.hash(i, side + 5)) : Math.min(b.th, 14);
                float yt = th * 0.45F, ph = a.phase + i * 37 + side * 11;
                beacon(m, side < 0 ? x0 + 1 : x1 - 1, th, zc + b.tl / 2 - 1.5F, on, i);
                if (f == Fab.SAW) {
                    float dx = on ? (float) Math.sin(ph * 0.05F) * (b.tw * 0.25F) : 0;                       //table qui va et vient
                    m.cyl('y', cx + dx, zc, Math.min(4, b.tw * 0.3F), yt, yt + 0.5F, BigMat.CHROME, 14, null, 2);   //wafer
                    m.box(cx - 0.2F + dx * 0, yt + 0.5F, zc - 3, cx + 0.2F, yt + 0.55F, zc + 3, BigMat.CAST, null, 1);
                    float by = yt + 3.2F;
                    m.cyl('x', by, zc, 2.6F, cx - 0.15F, cx + 0.15F, BigMat.CHROME, 14, BigMesh.rot('x', 0, by, zc, ph * 40), 2);  //lame
                    m.box(cx - 1.2F, by, zc - 1.2F, cx + 1.2F, th - 1, zc + 1.2F, BigMat.MOTOR, null, 2);   //broche
                    if (on) {
                        for (int k = 0; k < 6; k++) {                                                          //eau d'arrosage
                            float p = (a.phase * 0.12F + k * 0.17F) % 1F;
                            m.box(cx - 0.15F + (k - 3) * 0.5F, yt + 0.6F + p * 2, zc + 2.6F + p * 2, cx + 0.15F + (k - 3) * 0.5F,
                                  yt + 0.9F + p * 2, zc + 2.9F + p * 2, BigMat.BUBBLE, null, 1);
                        }
                    }
                } else if (f == Fab.LITHO) {
                    int step = (int) (ph / 12) % 9;                                                             //platine pas a pas
                    float sx = on ? (step % 3 - 1) * 1.6F : 0, sz = on ? (step / 3 - 1) * 1.6F : 0;
                    m.box(cx - 3.5F + sx, yt, zc - 3.5F + sz, cx + 3.5F + sx, yt + 0.8F, zc + 3.5F + sz, BigMat.CAST, null, 2);
                    m.cyl('y', cx + sx, zc + sz, 3, yt + 0.8F, yt + 1.1F, BigMat.CHROME, 14, null, 1);
                    if (on && (ph % 12) < 8) {                                                                   //eclair UV
                        m.glow(15).color(0x90B060FF);
                        m.box(cx - 0.8F, yt + 1.1F, zc - 0.8F, cx + 0.8F, th * 0.6F, zc + 0.8F, BigMat.LIQUID, null, 1);
                        m.glow(0).color(0xFFFFFFFF);
                    }
                    led(m, x0 + 1, th - 3.5F, zc - 1, x1 - 1, th - 3, zc + 1, 0xB070FF, on ? 13 : 2);
                } else {
                    led(m, side < 0 ? x1 + 0.05F : x0 - 0.15F, th - 3, zc - b.tl / 2 + 2, side < 0 ? x1 + 0.15F : x0 - 0.05F, th - 2,
                          zc + b.tl / 2 - 2, (i + side) % 2 == 0 ? 0x60D0FF : 0x70F0A0, on ? 11 : 2);
                }
            }
        }
        if (f == Fab.FAB) {                                                                                      //chariots du transport aerien
            int np = Math.max(2, b.n);
            for (int k = 0; k < np; k++) {
                float z = 3 + ((a.phase * 0.18F + k * (l - 6) / np) % (l - 6));
                float stop = (float) Math.sin(a.phase * 0.02F + k) > 0.7F ? 1 : 0;
                float y = b.top - 9;
                m.box(w / 2 - 1.5F, b.top - 7, z - 2, w / 2 + 1.5F, b.top - 6, z + 2, BigMat.MOTOR, null, 2);
                m.box(w / 2 - 2, y - stop * 3, z - 2, w / 2 + 2, y + 2 - stop * 3, z + 2, BigMat.CAST_L, null, 2);  //boite de plaquettes
            }
        } else {                                                                                                  //robot mobile dans l'allee
            float t = (a.phase * 0.003F) % 2, u = t < 1 ? t : 2 - t, z = 6 + (l - 12) * Lot4.smooth(u);
            m.box(w / 2 - 2.5F, 1.2F, z - 3, w / 2 + 2.5F, 4, z + 3, BigMat.CAST_L, null, 2);
            m.box(w / 2 - 1.5F, 4, z - 1.5F, w / 2 + 1.5F, 7, z + 1.5F, BigMat.STEEL_D, null, 2);
            led(m, w / 2 - 2.6F, 2.5F, z - 3.1F, w / 2 + 2.6F, 3, z - 2.9F, 0x60D0FF, on ? 12 : 3);
        }
    }

    // ------------------------------------------------------------------ separation isotopique par laser

    static void laserSep(BigMesh m, float w, float h, float l, int[] prof) {
        float top = hallTop(prof, w, h), cy = Math.min(top * 0.5F, 20), r = Math.min(cy - 2, Math.min(w * 0.22F, 10)), cx = w / 2;
        m.box(1, 0, 1, w - 1, 1.5F, l - 1, BigMat.CAST, null, 4);
        m.cyl('z', cx, cy, r, 6, l - 6, BigMat.GLASS, 18, null, 6, false, 0.4F);                                //chambre a vide
        for (float z = 6; z < l - 5; z += 14) {
            m.cyl('z', cx, cy, r + 0.6F, z, z + 1.2F, BigMat.CAST_L, 18, null, 2, false, 0.8F);                  //brides
        }
        for (float z : new float[]{3, l - 6}) {
            m.box(cx - r - 1, cy - r - 1, z, cx + r + 1, cy + r + 1, z + 3, BigMat.CAST_L, null, 4);              //fonds et miroirs
        }
        m.box(cx - r * 0.6F, cy - r + 0.5F, 8, cx + r * 0.6F, cy - r + 2, l - 8, BigMat.STEEL_D, null, 4);    //creuset de l'evaporateur
        for (float z = 10; z < l - 10; z += 10) {                                                               //collecteurs
            m.box(cx + r * 0.3F, cy - 1, z, cx + r * 0.75F, cy + r * 0.6F, z + 0.5F, BigMat.CATHODE, null, 2);
        }
        for (int side = -1; side <= 1; side += 2) {                                                              //tables laser
            float x0 = side < 0 ? 1.5F : w - 1.5F - Math.max(4, w * 0.15F), x1 = x0 + Math.max(4, w * 0.15F);
            if (x1 - x0 < 3 || (side < 0 && x1 > cx - r - 1) || (side > 0 && x0 < cx + r + 1)) {
                continue;
            }
            m.box(x0, 1.5F, 6, x1, 9, l - 6, BigMat.CAST_L, null, 6);
            for (float z = 10; z < l - 8; z += 12) {
                m.box(x0 + 0.5F, 9, z, x1 - 0.5F, 12, z + 6, BigMat.MOTOR, null, 2);
            }
        }
    }

    static void laserSepLife(BigMesh m, float w, float h, float l, int[] prof, BigMachines.Anim a) {
        float top = hallTop(prof, w, h), cy = Math.min(top * 0.5F, 20), r = Math.min(cy - 2, Math.min(w * 0.22F, 10)), cx = w / 2;
        float s = a.speed;
        if (s < 0.03F) {
            return;
        }
        m.glow(Math.round(4 + 8 * s)).color(argb(0x9CE070, 0x50));                                            //vapeur d'uranium
        m.box(cx - r * 0.55F, cy - r + 2, 8, cx + r * 0.55F, cy - r + 2 + r * 1.1F * s, l - 8, BigMat.LIQUID, null, 6);
        boolean pulse = (a.phase % 6) < 3;
        int[] col = {0xFF5050, 0x50FF70, 0xFFD040};
        for (int k = 0; k < 3; k++) {                                                                           //faisceaux en zigzag
            float y = cy - r * 0.3F + k * r * 0.3F;
            int n = 5;
            for (int i = 0; i < n; i++) {
                float z0 = 6 + (l - 12) * i / n, z1 = 6 + (l - 12) * (i + 1) / n;
                float xa = cx + (i % 2 == 0 ? -1 : 1) * r * 0.5F, xb = cx + (i % 2 == 0 ? 1 : -1) * r * 0.5F;
                m.glow(pulse ? 15 : 11).color(0xFF000000 | col[k]);
                Lot4.beam(m, xa, y, z0, xb, y, z1, pulse ? 0.35F : 0.25F, BigMat.LED);
            }
        }
        m.glow(0).color(0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ ferme hydroponique

    /**
     * 1.0.4-beta (lot 26, Guillaume : « l'animation de la ferme hydroponique doit representer les crops qui sont plantes et qui poussent »)
     * : la ferme ne dessine plus de plants generiques : les VRAIES plantes de la recette en cours poussent sur le substrat de la structure
     * ({@code GrowRender}) ; ici, l'equipement seulement : deux rampes LED horticoles suspendues sous la verriere a deux traverses (en haut
     * de la boite, au-dessus des cultures) et les goutte-a-goutte entre les rangees, au ras du substrat.
     */
    static void hydro(BigMesh m, float w, float h, float l, int[] prof) {
        for (float z : new float[]{3, l - 4}) {
            m.box(1, h - 2.2F, z, w - 1, h - 1, z + 1, BigMat.CAST_L, null, 4);                                    //traverses
        }
        for (float x : rampsX(w)) {
            m.box(x - 1.4F, h - 4.4F, 2, x + 1.4F, h - 2.2F, l - 2, BigMat.CAST, null, 4);                        //rampes LED
        }
        for (float x = 16; x < w - 1; x += 16) {
            m.box(x - 0.4F, 0, 1, x + 0.4F, 0.7F, l - 1, BigMat.PIPE, null, 2);                                  //goutte-a-goutte
        }
    }

    /** Rampes LED de la ferme : au-dessus des rangees de cases (x des milieux entre deux rangees, ou au milieu). */
    private static float[] rampsX(float w) {
        int n = Math.max(1, (int) (w / 16) - 1);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = w * (i + 1F) / (n + 1F);
        }
        return out;
    }

    static void hydroLife(BigMesh m, float w, float h, float l, int[] prof, BigMachines.Anim a) {
        float s = a.speed;
        m.glow(Math.round(3 + 11 * s)).color(0xFFFF60C8);
        for (float x : rampsX(w)) {
            m.box(x - 1.1F, h - 4.7F, 2.4F, x + 1.1F, h - 4.4F, l - 2.4F, BigMat.LED, null, 2);                 //lumiere horticole
        }
        m.glow(0).color(0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ plantation d'arbres

    /**
     * 1.0.4-beta (lot 26, « pareil pour les arbres ») : les VRAIS arbres de l'essence de la recette en cours (pousse, puis tronc et houppier qui
     * grandissent) sont dessines sur le substrat par {@code GrowRender} ; ici, l'arrosage seulement : rampes de goutte-a-goutte entre les
     * rangees et asperseurs au ras du substrat (rien au-dessus : les houppiers montent jusqu'a la verriere).
     */
    static void trees(BigMesh m, float w, float h, float l, int[] prof) {
        for (float z = 16; z < l - 1; z += 16) {
            m.box(1, 0, z - 0.4F, w - 1, 0.7F, z + 0.4F, BigMat.PIPE, null, 2);                                 //goutte-a-goutte
            for (float x = 8; x < w; x += 16) {
                m.cyl('y', x, z, 0.7F, 0.7F, 2.2F, BigMat.CHROME, 8, null, 2);                                  //asperseur
            }
        }
    }

    static void treesLife(BigMesh m, float w, float h, float l, int[] prof, BigMachines.Anim a) {
        boolean on = a.speed > 0.05F;
        m.glow(on ? 10 : 0).color(on ? 0xFF7FD8FF : 0xFF3A4A52);
        for (float z = 16; z < l - 1; z += 16) {
            for (float x = 8; x < w; x += 16) {
                m.box(x - 0.5F, 2.2F, z - 0.5F, x + 0.5F, 2.5F, z + 0.5F, BigMat.LED, null, 2);                  //voyant d'arrosage
            }
        }
        m.glow(0).color(0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ tirage de cristaux (Czochralski)

    private static float chamberH(float h) {
        return Math.min(48, Math.max(12, h * 0.3F));
    }

    static void puller(BigMesh m, float w, float h, float l) {
        float cx = w / 2, cz = l / 2, r = w / 2 - 1.5F, ch = chamberH(h);
        m.cyl('y', cx, cz, r, 0, ch, BigMat.CAST_L, 16, null, 6, false, 1);                                     //chambre du four
        m.cyl('y', cx, cz, r * 0.75F, ch, ch + 2, BigMat.CAST_L, 16, null, 4);
        m.cyl('y', cx, cz, r * 0.55F, 1, 3, BigMat.STEEL_D, 14, null, 2);                                     //support du creuset
        float tr = Math.max(1.5F, r * 0.32F);
        m.cyl('y', cx, cz, tr + 0.4F, ch + 2, h - 8, BigMat.GLASS, 12, null, 6, false, 0.3F);                //chambre de tirage
        m.box(cx - r * 0.7F, h - 8, cz - r * 0.7F, cx + r * 0.7F, h - 1, cz + r * 0.7F, BigMat.MOTOR, null, 4);  //tete de tirage
        for (int k = 0; k < 4; k++) {                                                                          //hublots
            double an = Math.toRadians(k * 90 + 45);
            float x = cx + (float) Math.cos(an) * (r + 0.1F), z = cz + (float) Math.sin(an) * (r + 0.1F);
            m.box(x - 1, ch * 0.3F, z - 1, x + 1, ch * 0.6F, z + 1, BigMat.GLASS, null, 2);
        }
    }

    static void pullerLife(BigMesh m, float w, float h, float l, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2, r = w / 2 - 1.5F, ch = chamberH(h), s = a.speed;
        float cr = r * 0.6F, melt = ch * 0.18F;
        float t = (a.phase / 2400F) % 1F;                                                                      //un lingot toutes les 2 min
        m.cyl('y', cx, cz, cr, 3, 3 + melt + 1, BigMat.CAST, 16, null, 4, false, 0.6F);                       //creuset
        m.glow(Math.round(6 + 9 * s)).color(0xFFFFB060);
        m.cyl('y', cx, cz, cr - 0.6F, 3.5F, 3 + melt * (1 - 0.7F * t), BigMat.MOLTEN, 16, null, 4);              //bain de silicium
        m.cyl('y', cx, cz, cr + 1.5F, 2, 3 + melt + 2, BigMat.GLOWROD, 16, null, 4, false, 0.6F);             //resistance
        m.glow(0).color(0xFFFFFFFF);
        float spin = a.phase * 3;
        float y0 = 3 + melt * (1 - 0.7F * t) + 0.2F, len = (h - ch - 12) * t + ch * 0.5F * t;
        float ir = Math.max(1, Math.min(r * 0.3F, (h - 20) * 0.04F + 1));
        Matrix4f rot = BigMesh.rot('y', cx, 0, cz, spin);
        if (s > 0.02F || t > 0.02F) {
            m.cyl('y', cx, cz, ir, y0, y0 + len, BigMat.CHROME, 12, rot, 4);                                     //lingot
            m.cyl('y', cx, cz, ir * 0.6F, y0 + len, y0 + len + 2, BigMat.CHROME, 10, rot, 2);                    //epaulement
            m.cyl('y', cx, cz, 0.3F, y0 + len + 2, y0 + len + 5, BigMat.CHROME, 6, rot, 1);                      //col
            m.glow(12).color(0xFFFFC080);
            m.cyl('y', cx, cz, ir, y0 - 0.2F, y0 + 1, BigMat.MOLTEN, 12, rot, 1);                                //interface chaude
            m.glow(0).color(0xFFFFFFFF);
        }
        m.box(cx - 0.6F, y0 + len + 5, cz - 0.6F, cx + 0.6F, y0 + len + 7, cz + 0.6F, BigMat.STEEL_D, null, 1); //porte-germe
        m.box(cx - 0.1F, y0 + len + 7, cz - 0.1F, cx + 0.1F, h - 8, cz + 0.1F, BigMat.ROLL, null, 1);           //cable
    }

    // ------------------------------------------------------------------ retraitement (colonnes pulsees)

    static void pulsed(BigMesh m, float w, float h, float l) {
        int n = w >= 40 ? 3 : 1;
        float rr = Math.min(5, w / (n * 2.6F));
        for (int i = 0; i < n; i++) {
            float x = w * (i + 0.5F) / n, z = l / 2;
            float y0 = Math.min(10, h * 0.25F), y1 = h - Math.min(8, h * 0.2F);
            m.cyl('y', x, z, rr, y0, y1, BigMat.GLASS, 12, null, 6, false, 0.3F);                                //colonne
            m.cyl('y', x, z, rr * 1.4F, y1, Math.min(h, y1 + 6), BigMat.CAST_L, 12, null, 4);                   //decanteur de tete
            m.cyl('y', x, z, rr * 1.4F, Math.max(0, y0 - 6), y0, BigMat.CAST_L, 12, null, 4);
            for (float y = y0 + 6; y < y1 - 2; y += 12) {
                m.cyl('y', x, z, rr - 0.4F, y, y + 0.3F, BigMat.GRATE, 10, null, 2);                             //plateaux perfores
            }
            float lx = Math.min(w - 2, x + rr + 3);
            m.cyl('y', lx, z + rr, 1, 0, y0, BigMat.PIPE, 8, null, 2);                                          //jambe de pulsation
            m.cyl('z', lx, y0 - 2, 0.8F, z, z + rr, BigMat.PIPE, 6, null, 2);
        }
    }

    static void pulsedLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        int n = w >= 40 ? 3 : 1;
        float rr = Math.min(5, w / (n * 2.6F)), s = a.speed;
        float pulse = (float) Math.sin(a.phase * 0.3F) * 1.5F * s;
        for (int i = 0; i < n; i++) {
            float x = w * (i + 0.5F) / n, z = l / 2;
            float y0 = Math.min(10, h * 0.25F), y1 = h - Math.min(8, h * 0.2F);
            m.color(argb(0xD8C040, 0x70));
            m.cyl('y', x, z, rr - 0.4F, y0, y1 - 2 + pulse, BigMat.LIQUID, 12, null, 6);                       //phase aqueuse (nitrique)
            m.color(0xFFFFFFFF);
            for (int k = 0; k < 20; k++) {                                                                       //gouttes organiques qui montent
                if (BigMachines.hash(k, 220 + i) > s) {
                    continue;
                }
                float ph = (a.phase * 0.008F + k * 0.05F) % 1F;
                double an = k * 2.1;
                float bx = x + (float) Math.cos(an) * rr * 0.5F, bz = z + (float) Math.sin(an) * rr * 0.5F;
                float y = y0 + ph * (y1 - y0 - 3) + pulse;
                m.color(0xFF000000 | tint);
                m.box(bx - 0.4F, y, bz - 0.4F, bx + 0.4F, y + 0.8F, bz + 0.4F, BigMat.STRIP, null, 1);
            }
            m.color(0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------------ futs : outils

    /** Demi-largeur libre (px) de la rangee de hauteur y d'une section (profil), centree. */
    static float span(int[] prof, float y, float w) {
        if (prof.length == 0) {
            return w / 2;
        }
        int i = Math.max(0, Math.min(prof.length - 1, (int) Math.floor(y / 16)));
        return prof[i] / 10 * 8F;
    }

    /** Demi-largeur libre minimale entre y0 et y1. */
    static float spanBetween(int[] prof, float y0, float y1, float w) {
        float r = span(prof, y0, w);
        for (float y = y0; y < y1; y += 4) {
            r = Math.min(r, span(prof, y, w));
        }
        return Math.min(r, span(prof, y1 - 0.01F, w));
    }

    static float drumR(float w, float h) {
        float d = Math.min(w, h);
        return d <= 16 ? d / 2 - 1.5F : d * 0.42F - 3 * Math.min(1, d / 80);
    }

    // ------------------------------------------------------------------ four de diffusion (tubes horizontaux)

    private record Tubes(int nx, int ny, float r) {
    }

    private static Tubes tubes(float w, float h) {
        int nx = w >= 48 ? 2 : 1, ny = h >= 48 ? 2 : 1;
        return new Tubes(nx, ny, Math.min(w / nx, h / ny) * 0.3F);
    }

    private static float tubeX(float w, Tubes t, int i) {
        return w * (i + 0.5F) / t.nx;
    }

    private static float tubeY(float h, Tubes t, int j) {
        return h * (j + 0.5F) / t.ny;
    }

    static void diffusion(BigMesh m, float w, float h, float l, int[] prof) {
        Tubes t = tubes(w, h);
        float zf = l * 0.2F;
        for (int i = 0; i < t.nx; i++) {
            for (int j = 0; j < t.ny; j++) {
                float x = tubeX(w, t, i), y = tubeY(h, t, j);
                m.cyl('z', x, y, t.r, 2, l - 2, BigMat.GLASS, 14, null, 6, false, 0.3F);                          //tube de quartz
                for (float z = zf; z < l - 6; z += 7) {
                    m.cyl('z', x, y, t.r + 1.6F, z, z + 1.4F, BigMat.CAST, 10, null, 2, false, 0.6F);             //spires
                }
                float hs = span(prof, y - t.r - 1.6F, w) - 1;
                m.box(Math.max(w / 2 - hs, x - t.r - 2), y - t.r - 2, zf - 2, Math.min(w / 2 + hs, x + t.r + 2), y - t.r - 1.2F, l - 4, BigMat.CAST_L, null, 4);
                m.box(x - t.r * 0.8F, y - t.r * 0.6F, 2, x + t.r * 0.8F, y - t.r * 0.45F, zf - 3, BigMat.STEEL_D, null, 2);   //poutre de chargement
            }
        }
        float h0 = span(prof, 1, w) - 1;
        m.box(w / 2 - h0, 0, 1, w / 2 + h0, 2, zf - 3, BigMat.CAST, null, 4);
    }

    static void diffusionLife(BigMesh m, float w, float h, float l, BigMachines.Anim a) {
        Tubes t = tubes(w, h);
        float zf = l * 0.2F, s = a.speed;
        float u = (a.phase / 900F) % 1F;                                                                       //entree, cuisson, sortie
        float in = u < 0.15F ? Lot4.smooth(u / 0.15F) : u < 0.85F ? 1 : 1 - Lot4.smooth((u - 0.85F) / 0.15F);
        in *= Lot4.smooth(s * 3);
        float bl = Math.min(l * 0.35F, 60), z0 = 3 + (zf + (l - zf) * 0.5F - bl / 2 - 3) * in;
        for (int i = 0; i < t.nx; i++) {
            for (int j = 0; j < t.ny; j++) {
                float x = tubeX(w, t, i), y = tubeY(h, t, j);
                int heat = Math.round(14 * s * (0.6F + 0.4F * in));
                m.glow(heat).color(Lot3.Glow.heat(0.3F + 0.7F * s));
                for (int k = 0; k < 7; k++) {                                                                      //resistances du demi-fourneau bas
                    float ang = 112.5F + k * 22.5F, bw = (float) (2 * (t.r + 1) * Math.tan(Math.PI / 16) + 0.05);
                    m.box(x - bw / 2, y + t.r + 0.6F, zf, x + bw / 2, y + t.r + 1, l - 6, BigMat.GLOWROD, BigMesh.rot('z', x, y, 0, ang), 4);
                }
                m.glow(0).color(0xFFFFFFFF);
                m.box(x - t.r * 0.6F, y - t.r * 0.5F, z0, x + t.r * 0.6F, y - t.r * 0.4F, z0 + bl, BigMat.GLASS, null, 4);   //nacelle
                for (float z = z0 + 1; z < z0 + bl - 0.5F; z += 1.1F) {
                    m.box(x - t.r * 0.5F, y - t.r * 0.4F, z, x + t.r * 0.5F, y + t.r * 0.45F, z + 0.15F, BigMat.CHROME, null, 1);   //plaquettes
                }
            }
        }
    }

    // ------------------------------------------------------------------ separateur magnetique (tambours)

    static void magSep(BigMesh m, float w, float h, float l, int[] prof) {
        int n = Math.max(1, (int) (l / 40));
        float r = Math.min(h * 0.28F, 14), cy = h * 0.42F;
        for (int i = 0; i < n; i++) {
            float zc = l * (i + 0.5F) / n;
            m.spin('x', 0, cy, zc, -3);
            m.cyl('x', cy, zc, r, 2, w - 2, BigMat.CHROME, 20, null, 6, false, 1);                                //tambour
            m.fixed();
            m.cyl('x', cy, zc, r * 0.9F, 2.5F, w - 2.5F, BigMat.MOTOR, 12, BigMesh.rot('x', 0, cy, zc, 0), 4, false, 1);   //aimants fixes
            float hf = spanBetween(prof, cy + r + 1, cy + r + 3, w) - 1.5F, hb = spanBetween(prof, 0, cy - r - 2, w) - 1.5F;
            m.box(w / 2 - hf, cy + r + 1, zc - r * 1.2F, w / 2 + hf, cy + r + 3, zc - r * 0.3F, BigMat.CAST_L, null, 4);   //alimentation
            m.box(w / 2 - hb, 0, zc - r * 1.1F, w / 2 + hb, cy - r - 2, zc - 1, BigMat.CAST, null, 4);                   //bac non magnetique
            m.box(w / 2 - hb, 0, zc + 1, w / 2 + hb, cy - r - 4, zc + r * 1.1F, BigMat.STEEL_D, null, 4);                //bac magnetique
            m.cyl('x', cy, zc, 1.5F, 0.5F, w - 0.5F, BigMat.CHROME, 8, null, 2);
        }
    }

    static void magSepLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        int n = Math.max(1, (int) (l / 40));
        float r = Math.min(h * 0.28F, 14), cy = h * 0.42F, s = a.speed;
        for (int i = 0; i < n; i++) {
            float zc = l * (i + 0.5F) / n;
            for (int k = 0; k < 30; k++) {
                if (BigMachines.hash(k, 230 + i) > s) {
                    continue;
                }
                float ph = (a.phase * 0.02F + k * 0.033F) % 1F;
                float x = 3 + BigMachines.hash(k, 231) * (w - 6);
                boolean mag = k % 3 == 0;
                float y, z;
                if (ph < 0.25F) {                                                                               //chute sur le tambour
                    float p = ph / 0.25F;
                    y = cy + r + 1 - p * 1;
                    z = zc - r * 0.8F + p * r * 0.8F;
                    y = cy + (float) Math.sqrt(Math.max(0, r * r - (z - zc) * (z - zc))) + 0.4F;
                } else if (mag) {                                                                                 //colle au tambour puis tombe derriere
                    float an = (float) Math.toRadians(90 - (ph - 0.25F) / 0.75F * 160);
                    y = cy + (float) Math.sin(an) * (r + 0.4F);
                    z = zc + (float) Math.cos(an) * (r + 0.4F);
                } else {                                                                                          //projete devant
                    float p = (ph - 0.25F) / 0.75F;
                    z = zc - p * r * 0.9F;
                    y = cy + r * 0.9F - p * p * (r * 1.8F + 4);
                }
                m.color(mag ? 0xFF3C3C44 : 0xFF000000 | tint);
                m.box(x - 0.5F, y, z - 0.5F, x + 0.5F, y + 1, z + 0.5F, BigMat.STRIP, null, 1);
            }
            m.color(0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------------ dessaleur (electrostatique)

    static void desalter(BigMesh m, float w, float h, float l) {
        float cx = w / 2, cy = h / 2, r = drumR(w, h);
        for (float z = 4; z < l - 3; z += 30) {
            m.cyl('z', cx, cy, r + 0.6F, z, z + 1.5F, BigMat.STEEL_D, 24, null, 2, false, 0.8F);               //cerclages
        }
        for (int k = -1; k <= 1; k += 2) {                                                                        //grilles d'electrodes
            float y = cy + k * r * 0.18F + r * 0.1F;
            m.box(cx - r * 0.75F, y, 6, cx + r * 0.75F, y + 0.4F, l - 6, BigMat.GRATE, null, 3);
            for (float z = 10; z < l - 8; z += 20) {
                m.box(cx - 0.3F, y, z, cx + 0.3F, cy + r - 1, z + 0.6F, BigMat.CAST, null, 1);                     //suspentes isolees
            }
        }
        m.cyl('z', cx, cy - r * 0.45F, Math.max(0.6F, r * 0.08F), 4, l - 4, BigMat.PIPE, 8, null, 2);          //distributeur
        for (float z = 8; z < l - 6; z += 8) {
            m.box(cx - 0.3F, cy - r * 0.45F, z, cx + 0.3F, cy - r * 0.3F, z + 0.6F, BigMat.PIPE, null, 1);
        }
        m.box(cx - 2, cy + r - 1, l / 2 - 3, cx + 2, Math.min(h, cy + r + 3), l / 2 + 3, BigMat.MOTOR, null, 4);    //transformateur
    }

    static void desalterLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        float cx = w / 2, cy = h / 2, r = drumR(w, h), s = a.speed;
        float iface = cy - r * 0.15F;
        m.color(argb(0x4A7896, 0x90));
        m.cyl('z', cx, cy, r, 3, l - 3, BigMat.LIQUID, 20, null, 8);                                              //enveloppe d'eau
        m.color(0xFFFFFFFF);
        m.color(argb(tint, 0xC0));
        m.box(cx - r * 0.86F, iface, 3, cx + r * 0.86F, cy + r * 0.8F, l - 3, BigMat.LIQUID, null, 8);           //brut
        m.color(0xFFFFFFFF);
        for (int k = 0; k < 20; k++) {                                                                             //gouttes d'eau qui tombent
            if (BigMachines.hash(k, 240) > s) {
                continue;
            }
            float ph = (a.phase * 0.02F + k * 0.05F) % 1F;
            float x = cx + (BigMachines.hash(k, 241) - 0.5F) * r * 1.4F, z = 6 + BigMachines.hash(k, 242) * (l - 12);
            float y = cy + r * 0.3F - ph * (cy + r * 0.3F - iface + 1);
            m.box(x - 0.35F, y, z - 0.35F, x + 0.35F, y + 0.7F, z + 0.35F, BigMat.BUBBLE, null, 1);
        }
        if (s > 0.05F) {                                                                                           //decharges entre les grilles
            for (int k = 0; k < 5; k++) {
                int seed = (int) (a.phase * 0.5F) * 7 + k;
                if (BigMachines.hash(seed, 243) > 0.5F * s) {
                    continue;
                }
                float z = 8 + BigMachines.hash(seed, 244) * (l - 16), x = cx + (BigMachines.hash(seed, 245) - 0.5F) * r;
                float y0 = cy - r * 0.08F, y1 = cy + r * 0.28F;
                m.glow(15).color(0xFFB0D8FF);
                Lot4.beam(m, x, y0, z, x + 0.8F, (y0 + y1) / 2, z + 0.5F, 0.2F, BigMat.LED);
                Lot4.beam(m, x + 0.8F, (y0 + y1) / 2, z + 0.5F, x, y1, z, 0.2F, BigMat.LED);
            }
            m.glow(0).color(0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------------ separateur a membranes (batteries de tubes)

    private record Bank(int nx, int ny, float r) {
    }

    private static Bank bank(float w, float h) {
        int nx = Math.max(1, (int) (w / 14)), ny = Math.max(1, (int) (h / 14));
        return new Bank(nx, ny, Math.min(w / nx, h / ny) * 0.36F);
    }

    /** Tube (i, j) de la batterie, s'il tient dans la section. */
    private static boolean fits(Bank b, int i, int j, float w, float h, int[] prof) {
        float x = w * (i + 0.5F) / b.nx, y = h * (j + 0.5F) / b.ny;
        return Math.abs(x - w / 2) + b.r + 0.6F <= spanBetween(prof, y - b.r, y + b.r, w) - 0.5F;
    }

    static void membrane(BigMesh m, float w, float h, float l, int[] prof) {
        Bank b = bank(w, h);
        int[] last = lastTube(b, w, h, prof);
        for (int i = 0; i < b.nx; i++) {
            for (int j = 0; j < b.ny; j++) {
                if (!fits(b, i, j, w, h, prof)) {
                    continue;
                }
                float x = w * (i + 0.5F) / b.nx, y = h * (j + 0.5F) / b.ny;
                boolean cut = i == last[0] && j == last[1];
                if (!cut) {
                    m.cyl('z', x, y, b.r, 6, l - 6, BigMat.CAST_L, 12, null, 6);                                     //tube de pression
                } else {
                    m.cyl('z', x, y, b.r, 6, l - 6, BigMat.GLASS, 12, null, 6, false, 0.3F);                         //tube ouvert
                    m.cyl('z', x, y, b.r * 0.75F, 7, l - 7, BigMat.GRATE, 10, null, 4, false, 0.4F);                //membrane spiralee
                    m.cyl('z', x, y, b.r * 0.2F, 6, l - 6, BigMat.PIPE, 6, null, 2);                                 //tube de permeat
                }
                for (float z : new float[]{6, l - 7}) {
                    m.cyl('z', x, y, b.r + 0.4F, z, z + 1, BigMat.STEEL_D, 12, null, 2);
                }
            }
        }
        float rc = drumR(w, h) + 1;
        for (float z : new float[]{2, l - 4}) {                                                                  //collecteurs
            m.cyl('z', w / 2, h / 2, rc, z, z + 2, BigMat.PIPE, 20, null, 6);
        }
    }

    /** Tube ouvert : le dernier qui tient (en haut a droite de la coupe). */
    private static int[] lastTube(Bank b, float w, float h, int[] prof) {
        for (int j = b.ny - 1; j >= 0; j--) {
            for (int i = b.nx - 1; i >= 0; i--) {
                if (fits(b, i, j, w, h, prof)) {
                    return new int[]{i, j};
                }
            }
        }
        return new int[]{0, 0};
    }

    static void membraneLife(BigMesh m, float w, float h, float l, int[] prof, int tint, BigMachines.Anim a) {
        Bank b = bank(w, h);
        int[] t = lastTube(b, w, h, prof);
        float x = w * (t[0] + 0.5F) / b.nx, y = h * (t[1] + 0.5F) / b.ny, s = a.speed;
        for (int k = 0; k < 24; k++) {                                                                           //flux dans le tube ouvert
            if (BigMachines.hash(k, 250) > s) {
                continue;
            }
            float ph = (a.phase * 0.02F + k * 0.0417F) % 1F;
            double an = k * 1.7 + ph * 6;
            float rr = b.r * (k % 2 == 0 ? 0.85F : 0.12F);
            float px = x + (float) Math.cos(an) * rr, py = y + (float) Math.sin(an) * rr, z = 7 + ph * (l - 14);
            m.color(k % 2 == 0 ? 0xFF000000 | tint : 0xFFC8E8FF);
            m.box(px - 0.3F, py - 0.3F, z, px + 0.3F, py + 0.3F, z + 1.2F, BigMat.STRIP, null, 1);
        }
        m.color(0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ autoclave (compartiments agites)

    static void autoclave(BigMesh m, float w, float h, float l) {
        float cx = w / 2, cy = h / 2, r = drumR(w, h);
        int n = Math.max(1, (int) ((l - 8) / 30));
        float cl = (l - 8) / n;
        for (int i = 0; i <= n; i++) {
            float z = 4 + cl * i;
            m.box(cx - r * 0.8F, cy - r * 0.85F, z - 0.4F, cx + r * 0.8F, cy + r * 0.25F, z + 0.4F, BigMat.CAST_L, null, 4);   //cloisons
        }
        for (int i = 0; i < n; i++) {
            float zc = 4 + cl * (i + 0.5F);
            m.box(cx - 2.2F, cy + r - 1, zc - 2.2F, cx + 2.2F, Math.min(h, cy + r + 4), zc + 2.2F, BigMat.MOTOR, null, 4);
            m.spin('y', cx, 0, zc, 12);
            m.cyl('y', cx, zc, 0.7F, cy - r * 0.7F, cy + r, BigMat.CHROME, 8, null, 2);
            for (int p = 0; p < 4; p++) {
                m.box(cx - 0.4F, cy - r * 0.7F, zc, cx + 0.4F, cy - r * 0.45F, zc + r * 0.45F, BigMat.CAST_L, BigMesh.rot('y', cx, 0, zc, p * 90), 2);
            }
            m.fixed();
        }
        m.cyl('z', cx, cy, r, 2, 4, BigMat.STEEL_D, 24, null, 4);                                               //fond
        m.cyl('z', cx, cy, r + 1, l - 4, l - 1, BigMat.CAST, 24, null, 4);                                       //porte a fermeture rapide
        for (int k = 0; k < 8; k++) {
            m.box(cx - 0.6F, cy + r - 0.6F, l - 4.5F, cx + 0.6F, cy + r + 1.2F, l - 0.5F, BigMat.STEEL_D, BigMesh.rot('z', cx, cy, 0, k * 45), 2);
        }
    }

    static void autoclaveLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        float cx = w / 2, cy = h / 2, r = drumR(w, h), s = a.speed;
        m.color(argb(tint, 0xB0));
        m.box(cx - r * 0.85F, cy - r * 0.85F, 4.5F, cx + r * 0.85F, cy + r * 0.1F + 0.5F * (float) Math.sin(a.phase * 0.1F), l - 4.5F,
              BigMat.LIQUID, null, 8);
        m.color(0xFFFFFFFF);
        bubbles(m, cx, cy, r, l, a, s);
    }

    private static void bubbles(BigMesh m, float cx, float cy, float r, float l, BigMachines.Anim a, float s) {
        for (int k = 0; k < 24; k++) {
            if (BigMachines.hash(k, 260) > s) {
                continue;
            }
            float ph = (a.phase * 0.03F + k * 0.0417F) % 1F;
            float x = cx + (BigMachines.hash(k, 261) - 0.5F) * r * 1.4F, z = 6 + BigMachines.hash(k, 262) * (l - 12);
            float y = cy - r * 0.8F + ph * r * 0.9F;
            m.box(x - 0.35F, y, z - 0.35F, x + 0.35F, y + 0.7F, z + 0.35F, BigMat.BUBBLE, null, 1);
        }
        for (int k = 0; k < 6; k++) {                                                                              //vapeur au-dessus
            float ph = (a.phase * 0.01F + k * 0.166F) % 1F, d = 1 + ph * 2;
            float z = 6 + BigMachines.hash(k, 263) * (l - 12), y = cy + r * 0.15F + ph * r * 0.6F;
            if (BigMachines.hash(k, 264) > s) {
                continue;
            }
            m.box(cx - d, y, z - d, cx + d, y + d, z + d, BigMat.FROTH, null, 2);
        }
    }
}
