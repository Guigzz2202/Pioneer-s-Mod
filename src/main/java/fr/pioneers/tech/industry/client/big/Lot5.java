package fr.pioneers.tech.industry.client.big;

import org.joml.Matrix4f;

/**
 * 0.19.19 : machines interieures du lot 5 : bassins ouverts (crible vibrant, table a secousses, flottation, trempe, decanteur, gravure
 * acide, galvanisation, extraction par solvant, ferme aquacole, piscine de combustible use). L'interieur est le bassin lui-meme
 * (fond y = 0, dessus ouvert) ; les liquides sont translucides et teintes ; ponts et palans depassent au-dessus du bord.
 */
final class Lot5 {

    private Lot5() {
    }

    private static int argb(int rgb, int alpha) {
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    private static int shade(int rgb, float k) {
        int r = Math.min(255, Math.round((rgb >> 16 & 255) * k)), g = Math.min(255, Math.round((rgb >> 8 & 255) * k)),
              b = Math.min(255, Math.round((rgb & 255) * k));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Nombre d'unites cote a cote dans la largeur. */
    private static int units(float w, float per) {
        return Math.max(1, Math.round(w / per));
    }

    private static void liquid(BigMesh m, float x0, float z0, float x1, float z1, float y0, float y1, int rgb, int alpha) {
        m.color(argb(rgb, alpha));
        m.box(x0, y0, z0, x1, y1, z1, BigMat.LIQUID, null, 8);
        m.color(0xFFFFFFFF);
    }

    private static void bubbles(BigMesh m, int n, int salt, float x0, float x1, float z0, float z1, float y0, float y1, BigMachines.Anim a,
          float rate, BigMat mat, float size) {
        for (int i = 0; i < n; i++) {
            if (BigMachines.hash(i, salt) > a.speed) {
                continue;
            }
            float ph = (a.phase * rate + i * 0.137F) % 1F;
            float x = x0 + BigMachines.hash(i, salt + 1) * (x1 - x0), z = z0 + BigMachines.hash(i, salt + 2) * (z1 - z0);
            float y = y0 + ph * (y1 - y0), d = size * (0.7F + 0.6F * BigMachines.hash(i, salt + 3));
            m.box(x - d, y - d, z - d, x + d, y + d, z + d, mat, null, 1);
        }
    }

    // ------------------------------------------------------------------ crible vibrant

    private static float sieveAngle(float l) {
        return (float) Math.toDegrees(Math.atan2(10, l - 20));
    }

    static void sifter(BigMesh m, float w, float h, float l) {
        int n = units(w, 40);
        float uw = w / n;
        for (int i = 0; i < n; i++) {
            float x0 = uw * i + 2, x1 = uw * (i + 1) - 2;
            m.box(x0, 0, l - 12, x1, 7, l - 2, BigMat.CAST, null, 4);                                            //tremie de sortie
            m.box(x0 + 1, 0, 3, x1 - 1, 5, l - 12, BigMat.CAST_L, null, 4);                                       //caisson des fines
            for (float z : new float[]{8, l - 14}) {
                for (float x : new float[]{x0 + 1, x1 - 2}) {
                    for (float y = 5; y < 10; y += 1.2F) {
                        m.box(x, y, z - 1, x + 1, y + 0.6F, z + 1, BigMat.CHROME, null, 1);                         //ressorts
                    }
                }
            }
            m.box(x0, 26, 1, x1, h - 1, 9, BigMat.CAST_L, null, 4);                                                //caisson d'alimentation
        }
    }

    static void sifterLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        int n = units(w, 40);
        float uw = w / n, s = a.speed, ang = sieveAngle(l);
        for (int i = 0; i < n; i++) {
            float x0 = uw * i + 2, x1 = uw * (i + 1) - 2;
            float t = (a.phase + i * 11) * 1.6F;
            float dy = 0.5F * s * (float) Math.sin(t), dz = 0.5F * s * (float) Math.cos(t);
            Matrix4f tilt = BigMesh.chain(BigMesh.rot('x', 0, 20, 10, ang), new Matrix4f().translate(0, dy, dz));
            m.box(x0, 12, 8, x0 + 1.2F, 24, l - 10, BigMat.CAST, tilt, 4);                                         //joues
            m.box(x1 - 1.2F, 12, 8, x1, 24, l - 10, BigMat.CAST, tilt, 4);
            m.box(x0 + 1.2F, 20, 8, x1 - 1.2F, 20.8F, l - 10, BigMat.GRATE, tilt, 6);                              //toile superieure
            m.box(x0 + 1.2F, 15, 8, x1 - 1.2F, 15.6F, l - 10, BigMat.GRATE, tilt, 3);                              //toile inferieure
            for (float z = 14; z < l - 12; z += 18) {
                m.cyl('x', 24.5F, z, 1, x0, x1, BigMat.CAST_L, 8, tilt, 2);                                       //traverses
            }
            float my = 17, mz = l / 2;                                                                            //balourds
            m.box(x0 - 1.8F, my - 2, mz - 2.5F, x0, my + 2, mz + 2.5F, BigMat.MOTOR, tilt, 3);
            m.box(x1, my - 2, mz - 2.5F, x1 + 1.8F, my + 2, mz + 2.5F, BigMat.MOTOR, tilt, 3);
            if (s < 0.05F) {
                continue;
            }
            for (int k = 0; k < 24; k++) {                                                                        //minerai qui descend le crible
                if (BigMachines.hash(k, 100 + i) > s) {
                    continue;
                }
                float ph = (a.phase * 0.012F + k * 0.041F) % 1F;
                float x = x0 + 2 + BigMachines.hash(k, 101) * (x1 - x0 - 4), z = 9 + ph * (l - 20);
                boolean fine = BigMachines.hash(k, 102) < 0.4F;
                float y = fine ? 15.6F : 20.8F, d = fine ? 0.5F : 0.9F, hop = 0.6F * Math.abs((float) Math.sin(t + k));
                m.color(fine ? shade(tint, 0.75F) : 0xFF000000 | tint);
                m.box(x - d, y + hop, z - d, x + d, y + hop + 2 * d, z + d, BigMat.STRIP, tilt, 1);
            }
            m.color(0xFFFFFFFF);
            for (int k = 0; k < 10; k++) {                                                                        //alimentation et sortie
                float ph = (a.phase * 0.05F + k * 0.1F) % 1F;
                float x = x0 + 2 + BigMachines.hash(k, 103) * (x1 - x0 - 4);
                m.color(0xFF000000 | tint);
                m.box(x - 0.7F, 25.5F - ph * 4, 9 + ph * 2, x + 0.7F, 26.9F - ph * 4, 10.4F + ph * 2, BigMat.STRIP, null, 1);
                m.box(x - 0.7F, 14 - ph * 7, l - 10 + ph, x + 0.7F, 15.4F - ph * 7, l - 8.6F + ph, BigMat.STRIP, null, 1);
            }
            m.color(0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------------ table a secousses

    static void shakingTable(BigMesh m, float w, float h, float l) {
        int n = units(w, 40);
        float uw = w / n;
        for (int i = 0; i < n; i++) {
            float x0 = uw * i + 2, x1 = uw * (i + 1) - 2;
            for (float z : new float[]{10, l - 6}) {
                m.box(x0 + 1, 0, z - 1.5F, x0 + 3, 14, z + 1.5F, BigMat.CAST, null, 4);                              //pieds
                m.box(x1 - 3, 0, z - 1.5F, x1 - 1, 16, z + 1.5F, BigMat.CAST, null, 4);
            }
            m.box(x0 + 1, 10, 1, x1 - 1, 22, 7, BigMat.MOTOR, null, 4);                                            //mecanisme de tete
            m.box(x0 + 1, 0, l - 4, x1 - 1, 6, l - 1, BigMat.CAST_L, null, 4);                                     //bac des concentres
            m.box(x0 - 1.6F, 0, 9, x0, 6, l - 4, BigMat.CAST_L, null, 4);                                          //rigole des steriles
            m.box(x1 - 2, 19, 9, x1 + 0.5F, 21, l - 5, BigMat.PIPE, null, 4);                                       //auge d'eau de lavage
        }
    }

    static void shakingTableLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        int n = units(w, 40);
        float uw = w / n, s = a.speed;
        for (int i = 0; i < n; i++) {
            float x0 = uw * i + 2, x1 = uw * (i + 1) - 2;
            float t = ((a.phase + i * 7) * 0.08F) % 1F;
            float dz = s * (t < 0.7F ? 1.2F * t / 0.7F : 1.2F * (1 - (t - 0.7F) / 0.3F));                        //course lente puis retour vif
            Matrix4f tilt = BigMesh.chain(BigMesh.rot('z', (x0 + x1) / 2, 16, 0, 3), new Matrix4f().translate(0, 0, dz));
            m.box(x0, 15, 8, x1, 16, l - 5, BigMat.CAST_L, tilt, 6);                                              //plateau
            m.box(x0 + 1, 14, 7, x1 - 1, 15, 9, BigMat.CAST, tilt, 4);
            int nr = Math.max(4, (int) ((x1 - x0) / 2.5F));
            for (int r = 0; r < nr; r++) {                                                                        //riffles effiles
                float x = x0 + 1 + r * (x1 - x0 - 2) / (nr - 1);
                float zr = l - 5 - (l - 15) * (0.3F + 0.6F * r / (nr - 1));
                m.box(x - 0.25F, 16, 9, x + 0.25F, 16.6F, zr, BigMat.STEEL_D, tilt, 2);
            }
            if (s < 0.05F) {
                continue;
            }
            liquid(m, x0 + 0.5F, 9, x1 - 0.5F, l - 5.5F, 16, 16.3F, 0x8CC0D8, 0x60);                           //film d'eau
            for (int k = 0; k < 40; k++) {                                                                        //bandes de mineraux
                if (BigMachines.hash(k, 110 + i) > s) {
                    continue;
                }
                float ph = (a.phase * 0.006F + k * 0.025F) % 1F;
                int band = k % 3;
                float z = 10 + ph * (l - 16), x = x1 - 2 - ph * (x1 - x0 - 4) * (band == 0 ? 0.25F : band == 1 ? 0.6F : 1.0F);
                m.color(band == 0 ? shade(tint, 0.7F) : band == 1 ? 0xFF000000 | tint : 0xFFD8C8A0);
                m.box(x - 0.5F, 16.1F, z - 0.5F, x + 0.5F, 16.7F, z + 0.5F, BigMat.STRIP, tilt, 1);
            }
            m.color(0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------------ flottation

    private record Cells(int nx, int nz, float cw, float cl) {
    }

    private static Cells cells(float w, float l) {
        int nx = units(w, 48), nz = Math.max(1, (int) (l / 32));
        return new Cells(nx, nz, w / nx, l / nz);
    }

    static void flotation(BigMesh m, float w, float h, float l) {
        Cells c = cells(w, l);
        for (int i = 0; i < c.nx; i++) {
            float x0 = c.cw * i + 1, x1 = c.cw * (i + 1) - 1, cx = (x0 + x1) / 2;
            m.box(x1 - 3, h - 9, 1, x1 - 0.5F, h - 7, l - 1, BigMat.CAST_L, null, 4);                              //goulotte des mousses
            for (int j = 0; j < c.nz; j++) {
                float z0 = c.cl * j + 1, z1 = c.cl * (j + 1) - 1, cz = (z0 + z1) / 2;
                m.box(x0, 0, z0, x0 + 1, h - 6, z1, BigMat.STEEL_D, null, 4);                                       //cuve
                m.box(x1 - 4, 0, z0, x1 - 3, h - 6, z1, BigMat.STEEL_D, null, 4);
                m.box(x0, 0, z0, x1 - 3, h - 6, z0 + 1, BigMat.STEEL_D, null, 4);
                m.box(x0, 0, z1 - 1, x1 - 3, h - 6, z1, BigMat.STEEL_D, null, 4);
                float ax = (x0 + x1 - 3) / 2;
                m.box(x0, h - 3, cz - 1.5F, x1 - 3, h - 1, cz + 1.5F, BigMat.CAST, null, 4);                        //pont
                m.box(ax - 3, h - 1, cz - 3, ax + 3, h + 5, cz + 3, BigMat.MOTOR, null, 4);                         //moteur
                m.spin('y', ax, 0, cz, 14);
                m.cyl('y', ax, cz, 0.9F, 4, h - 3, BigMat.CHROME, 8, null, 2);                                      //arbre et rotor
                for (int p = 0; p < 6; p++) {
                    m.box(ax - 0.4F, 3, cz, ax + 0.4F, 7, cz + Math.min(5, (z1 - z0) * 0.25F), BigMat.CAST_L,
                          BigMesh.rot('y', ax, 0, cz, p * 60), 2);
                }
                m.fixed();
                m.cyl('y', ax, cz, Math.min(6.5F, (z1 - z0) * 0.32F), 2, 9, BigMat.GRATE, 12, null, 2, false, 0.6F);  //stator
                if (cx > 0) {
                    m.box(x1 - 3, h - 7, z0, x1 - 0.5F, h - 6, z1, BigMat.CAST, null, 2);
                }
            }
        }
    }

    static void flotationLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        Cells c = cells(w, l);
        float s = a.speed;
        for (int i = 0; i < c.nx; i++) {
            float x0 = c.cw * i + 1, x1 = c.cw * (i + 1) - 1;
            for (int j = 0; j < c.nz; j++) {
                float z0 = c.cl * j + 1, z1 = c.cl * (j + 1) - 1;
                float fh = 1 + 2.5F * s;
                liquid(m, x0 + 1, z0 + 1, x1 - 4, z1 - 1, 0.2F, h - 6 - fh * 0.3F, tint, 0xB0);                     //pulpe
                float yf = h - 6 - fh * 0.3F;
                m.color(0xFFE6E8DC);
                m.box(x0 + 1, yf, z0 + 1, x1 - 4, yf + fh * 0.5F, z1 - 1, BigMat.FROTH, null, 6);                   //tapis de mousse
                for (int k = 0; k < 8; k++) {
                    float bx = x0 + 2.5F + BigMachines.hash(k + j * 17, 120 + i) * (x1 - x0 - 8.5F);
                    float bz = z0 + 2.5F + BigMachines.hash(k + j * 17, 121 + i) * (z1 - z0 - 5);
                    float d = 0.8F + 0.8F * BigMachines.hash(k, 122);
                    float y = yf + fh * 0.5F - d * 0.6F + 0.3F * (float) Math.sin(a.phase * 0.08F + k);
                    m.box(bx - d, y, bz - d, bx + d, y + d * 1.2F, bz + d, BigMat.FROTH, null, 2);
                }
                m.color(0xFFFFFFFF);
                bubbles(m, 14, 124 + j, x0 + 2, x1 - 5, z0 + 2, z1 - 2, 3, h - 7, a, 0.03F, BigMat.BUBBLE, 0.35F);
                if (s > 0.1F) {                                                                                   //mousse qui deborde
                    float ph = (a.phase * 0.02F) % 1F;
                    m.box(x1 - 3, h - 6, z0 + 1, x1 - 2.4F, h - 6 + 0.2F + ph * 0.1F, z1 - 1, BigMat.FROTH, null, 2);
                    m.box(x1 - 2.8F, h - 7 + 0.1F, z0 + 0.5F, x1 - 0.8F, h - 6.6F, z1 - 0.5F, BigMat.FROTH, null, 2);
                }
            }
        }
    }

    // ------------------------------------------------------------------ palans a tremper (trempe, galvanisation, gravure)

    /** Bains en serie le long de l'axe ; le palan prend la charge au-dessus du premier et la plonge dans chacun. */
    enum Dip {
        QUENCH, GALVANIZE, ETCH
    }

    private static float dipLevel(float h) {
        return h - 5;
    }

    private static int baths(Dip d) {
        return d == Dip.ETCH ? 3 : d == Dip.GALVANIZE ? 2 : 1;
    }

    static void dip(BigMesh m, Dip d, float w, float h, float l) {
        int nb = baths(d);
        float bl = l / nb;
        for (int i = 1; i < nb; i++) {
            m.box(1, 0, bl * i - 0.8F, w - 1, dipLevel(h) + 1, bl * i + 0.8F, BigMat.STEEL_D, null, 4);          //cloisons
        }
        for (float x : new float[]{0.5F, w - 2}) {                                                              //chemin de roulement
            m.box(x, h - 1, 0.5F, x + 1.5F, h + 1, l - 0.5F, BigMat.CAST, null, 4);
            for (float z : new float[]{1.5F, l - 3}) {
                m.box(x, h - 6, z, x + 1.5F, h - 1, z + 1.5F, BigMat.CAST, null, 4);
            }
        }
        if (d == Dip.QUENCH) {                                                                                  //agitateur immerge
            m.spin('z', w / 2, 4, 0, 18);
            for (int p = 0; p < 3; p++) {
                m.box(w / 2 - 0.5F, 4, 2, w / 2 + 0.5F, 4 + Math.min(4, w * 0.2F), 3.5F, BigMat.CAST_L, BigMesh.rot('z', w / 2, 4, 0, p * 120), 2);
            }
            m.fixed();
            m.cyl('z', w / 2, 4, 0.6F, 0.5F, 4, BigMat.CHROME, 6, null, 2);
        }
        if (d == Dip.GALVANIZE) {
            for (float x = 3; x < w - 2; x += 6) {                                                              //bruleurs du bain de zinc
                m.box(x - 1, 0, bl + 2, x + 1, 1.5F, l - 2, BigMat.STEEL_D, null, 2);
            }
        }
    }

    static void dipLife(BigMesh m, Dip d, float w, float h, float l, int tint, BigMachines.Anim a) {
        int nb = baths(d);
        float bl = l / nb, s = a.speed, lvl = dipLevel(h);
        for (int i = 0; i < nb; i++) {
            int rgb = d == Dip.QUENCH ? tint : d == Dip.GALVANIZE ? (i == 0 ? 0x9CBC7C : tint) : (i == 1 ? 0x7AA8C8 : tint);
            boolean zinc = d == Dip.GALVANIZE && i == 1;
            if (zinc) {
                m.glow(3).color(0xFF000000 | rgb);
                m.box(1, 0.2F, bl * i + 1, w - 1, lvl, bl * (i + 1) - 1, BigMat.STRIP, null, 8);
                m.glow(0).color(0xFFFFFFFF);
            } else {
                liquid(m, 1, bl * i + 1, w - 1, bl * (i + 1) - 1, 0.2F, lvl, rgb, 0x90);
            }
        }
        //cycle du palan : pour chaque bain, trajet (20 %), descente, trempe, remontee ; puis retour au premier bain
        float cycle = 100 * nb + 40, t = a.phase % cycle;
        boolean back = t >= 100 * nb;
        int stage = back ? nb - 1 : (int) (t / 100);
        float u = back ? 1 : (t - stage * 100) / 100F;
        float zc, dep;
        if (back) {
            zc = bl * (nb - 0.5F) + (bl * 0.5F - bl * (nb - 0.5F)) * Lot4.smooth((t - 100 * nb) / 40F);
            dep = 0;
        } else {
            zc = stage > 0 && u < 0.2F ? bl * (stage - 0.5F) + bl * Lot4.smooth(u / 0.2F) : bl * (stage + 0.5F);
            dep = u < 0.2F ? 0 : u < 0.35F ? Lot4.smooth((u - 0.2F) / 0.15F) : u < 0.75F ? 1 : u < 0.9F ? 1 - Lot4.smooth((u - 0.75F) / 0.15F) : 0;
        }
        dep *= Lot4.smooth(s * 3);
        float top = h + 4, low = 3, yb = top - 8 - (top - 8 - low) * dep;                                       //bas de la charge
        m.box(0.5F, h + 1, zc - 2, w - 0.5F, h + 3.5F, zc + 2, BigMat.CAST_L, null, 4);                       //pont roulant
        int nu0 = units(w, 36);
        for (int i = 0; i < nu0; i++) {
            float ux = w * (i + 0.5F) / nu0;
            m.box(ux - 2.5F, h + 3.5F, zc - 2.5F, ux + 2.5F, h + 6.5F, zc + 2.5F, BigMat.MOTOR, null, 4);
            for (float x : new float[]{ux - 2, ux + 2}) {
                m.box(x - 0.2F, yb + 6, zc - 0.2F, x + 0.2F, h + 1, zc + 0.2F, BigMat.ROLL, null, 1);          //cables
            }
        }
        int nu = units(w, 36);
        float uw = w / nu;
        for (int i = 0; i < nu; i++) {
            m.shift(uw * i);
            dipLoad(m, d, uw, h, bl, lvl, zc, yb, dep, back, stage, u, nb, a);
        }
        m.shift(0);
    }

    private static void dipLoad(BigMesh m, Dip d, float w, float h, float bl, float lvl, float zc, float yb, float dep, boolean back, int stage,
          float u, int nb, BigMachines.Anim a) {
        float s = a.speed;
        float hw = Math.min(w / 2 - 2, 10), hl = Math.min(bl / 2 - 2.5F, 8);
        m.box(w / 2 - hw, yb + 6, zc - 1, w / 2 + hw, yb + 7, zc + 1, BigMat.CAST, null, 2);                  //palonnier
        if (s < 0.05F) {
            return;
        }
        boolean treated = back || stage > 0 || u > 0.75F, last = back || (stage == nb - 1 && u > 0.75F);
        if (d == Dip.ETCH) {                                                                                     //panier de plaques
            m.box(w / 2 - hw, yb, zc - hl, w / 2 + hw, yb + 0.6F, zc + hl, BigMat.GRATE, null, 4);
            for (float z = zc - hl + 1.5F; z < zc + hl - 1; z += 2.2F) {
                m.box(w / 2 - hw + 1, yb + 0.6F, z, w / 2 + hw - 1, yb + 5.5F, z + 0.35F, treated ? BigMat.CHROME : BigMat.CAST_L, null, 4);
            }
            for (float x : new float[]{w / 2 - hw, w / 2 + hw - 0.6F}) {
                m.box(x, yb, zc - 0.4F, x + 0.6F, yb + 6, zc + 0.4F, BigMat.CAST, null, 2);
            }
        } else {                                                                                                  //panier de pieces / poutrelles
            boolean hot = d == Dip.QUENCH;
            float cool = back ? 1 : Lot4.clamp01((u - 0.35F) / 0.3F);
            for (int p = 0; p < 4; p++) {
                float z = zc - hl + 1 + p * (2 * hl - 2) / 3F;
                if (hot) {
                    m.glow(Math.round(13 * (1 - cool))).color(0xFF000000 | (cool >= 1 ? 0x5A5E64 : heatRgb(1 - cool)));
                } else {
                    m.color(last ? 0xFFB4BCC4 : 0xFF5A5E64);
                }
                m.box(w / 2 - hw + 1, yb + 1, z - 0.6F, w / 2 + hw - 1, yb + 2.4F, z + 0.6F, hot ? BigMat.MOLTEN : BigMat.STRIP, null, 4);
                m.box(w / 2 - 0.6F, yb + 2.4F, z - 0.4F, w / 2 + 0.6F, yb + 6, z + 0.4F, BigMat.CAST, null, 2);
            }
            m.glow(0).color(0xFFFFFFFF);
            m.box(w / 2 - hw, yb, zc - hl, w / 2 + hw, yb + 0.6F, zc + hl, BigMat.GRATE, null, 4);
        }
        //bouillonnement, vapeur, fumees de zinc
        boolean inBath = dep > 0.5F;
        if (inBath) {
            BigMat puff = BigMat.FROTH;
            int n = d == Dip.QUENCH ? 16 : 10;
            for (int k = 0; k < n; k++) {
                float ph = (a.phase * 0.05F + k * 0.0625F) % 1F;
                float x = w / 2 - hw + BigMachines.hash(k, 130) * hw * 2, z = zc - hl + BigMachines.hash(k, 131) * hl * 2;
                float y = lvl + ph * 10, dd = 0.6F + ph * 1.6F;
                m.color(d == Dip.ETCH ? 0xFFD8F0B0 : 0xFFF0F0F0);
                m.box(x - dd, y, z - dd, x + dd, y + dd, z + dd, puff, null, 2);
            }
            m.color(0xFFFFFFFF);
            bubbles(m, 12, 132, w / 2 - hw, w / 2 + hw, zc - hl, zc + hl, Math.max(1, yb), lvl - 0.5F, a, 0.06F, BigMat.BUBBLE, 0.35F);
        }
    }

    private static int heatRgb(float k) {
        int r = Math.round(90 + 165 * Math.min(1, k * 1.6F)), g = Math.round(84 + 140 * k * k), b = Math.round(80 + 60 * k * k * k);
        return r << 16 | g << 8 | b;
    }

    // ------------------------------------------------------------------ decanteur a pont racleur

    static void decanter(BigMesh m, float w, float h, float l) {
        m.box(1, 0, 1, w - 1, 2, 8, BigMat.SLAG, null, 4);                                                      //fosse a boues
        m.box(1, 0, 8, w - 1, 1.2F, l - 1, BigMat.CAST_L, null, 6);                                             //radier
        m.box(1, h - 7, l - 5, w - 1, h - 5.5F, l - 1, BigMat.CAST, null, 4);                                   //goulotte de surverse
        for (float x = 2; x < w - 2; x += 3) {
            m.box(x, h - 5.5F, l - 5, x + 1.5F, h - 4.8F, l - 4.4F, BigMat.CAST, null, 1);                       //deversoir crenele
        }
        for (float x : new float[]{0.5F, w - 2}) {
            m.box(x, h - 1, 0.5F, x + 1.5F, h, l - 0.5F, BigMat.CAST, null, 4);                                  //rails
        }
    }

    static void decanterLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        float s = a.speed, lvl = h - 5;
        liquid(m, 1, 1, w - 1, l - 5, 1.2F, lvl, tint, 0x80);
        liquid(m, 1, l - 4.6F, w - 1, l - 1, h - 7 + 1.5F, h - 6, 0xA8C8D0, 0x80);
        for (int k = 0; k < Math.min(60, (int) (w * l / 120)); k++) {                                          //boue au fond
            float x = 1.5F + BigMachines.hash(k, 140) * (w - 3), z = 8 + BigMachines.hash(k, 141) * (l - 14);
            float hh = 0.4F + 0.8F * BigMachines.hash(k, 142) * (1 - (z - 8) / l);
            m.box(x - 1.2F, 1.2F, z - 1.2F, x + 1.2F, 1.2F + hh, z + 1.2F, BigMat.SLAG, null, 2);
        }
        bubbles(m, 30, 143, 2, w - 2, 9, l - 6, lvl - 1, 2, a, 0.008F, BigMat.SLAG, 0.25F);                     //flocs qui decantent
        //pont racleur : va-et-vient lent le long du bassin
        float t = (a.phase * 0.0025F) % 2F, u = t < 1 ? t : 2 - t, z = 8 + (l - 18) * Lot4.smooth(u);
        m.box(0.5F, h, z - 2, w - 0.5F, h + 2.5F, z + 2, BigMat.CAST_L, null, 4);
        for (float x : new float[]{1.2F, w - 1.2F}) {
            m.cyl('x', h + 0.8F, z, 1.2F, x - 0.8F, x + 0.8F, BigMat.STEEL_D, 8, null, 2);
        }
        m.box(w / 2 - 2, h + 2.5F, z - 1.5F, w / 2 + 2, h + 5, z + 1.5F, BigMat.MOTOR, null, 4);
        float drop = t < 1 ? 1 : 0;                                                                              //racle baissee a l'aller
        m.box(1.5F, 1.4F + (1 - drop) * 6, z - 0.3F, w - 1.5F, 3 + (1 - drop) * 6, z + 0.3F, BigMat.STEEL_D, null, 4);
        for (float x : new float[]{2, w - 2.5F}) {
            m.box(x, 3 + (1 - drop) * 6, z - 0.3F, x + 0.5F, h, z + 0.3F, BigMat.CAST, null, 2);
        }
        m.box(1.5F, lvl - 0.5F, z + 1.2F, w - 1.5F, lvl + 0.8F, z + 1.6F, BigMat.CAST, null, 2);                //ecumoire
        if (s > 0.05F) {
            for (int k = 0; k < 8; k++) {
                float x = 2 + k * (w - 4) / 7F;
                m.box(x - 0.6F, lvl, z + 1.6F, x + 0.6F, lvl + 0.3F, z + 3, BigMat.FROTH, null, 1);
            }
        }
    }

    // ------------------------------------------------------------------ extraction par solvant (melangeurs-decanteurs)

    static void solvent(BigMesh m, float w, float h, float l) {
        int n = Math.max(1, (int) (l / 40));
        float sl = l / n;
        for (int i = 0; i < n; i++) {
            float z0 = sl * i + 1, zm = z0 + Math.min(12, sl * 0.3F), z1 = sl * (i + 1) - 1;
            m.box(1, 0, z0, w - 1, h - 6, z0 + 0.8F, BigMat.STEEL_D, null, 4);                                    //cuve du melangeur
            m.box(1, 0, zm, w - 1, h - 6, zm + 0.8F, BigMat.STEEL_D, null, 4);
            m.box(1, 0, z1 - 0.8F, w - 1, h - 9, z1, BigMat.STEEL_D, null, 4);                                    //deversoir
            float cx = w / 2, cz = (z0 + zm) / 2;
            m.box(1, h - 3, cz - 1.5F, w - 1, h - 1.5F, cz + 1.5F, BigMat.CAST, null, 4);
            m.box(cx - 2.5F, h - 1.5F, cz - 2.5F, cx + 2.5F, h + 3.5F, cz + 2.5F, BigMat.MOTOR, null, 4);
            m.spin('y', cx, 0, cz, 16);
            m.cyl('y', cx, cz, 0.8F, 5, h - 3, BigMat.CHROME, 8, null, 2);
            for (int p = 0; p < 4; p++) {
                m.box(cx - 0.4F, 4, cz, cx + 0.4F, 7, cz + Math.min(4, (zm - z0) * 0.35F), BigMat.CAST_L, BigMesh.rot('y', cx, 0, cz, p * 90 + 45), 2);
            }
            m.fixed();
            for (float z = zm + 6; z < z1 - 4; z += 8) {
                m.box(1.5F, h - 10, z, w - 1.5F, h - 9.6F, z + 0.4F, BigMat.GRATE, null, 2);                       //picket fences
            }
            if (i < n - 1) {                                                                                        //tuyaux a contre-courant
                m.cyl('z', w - 2.5F, h - 3.5F, 0.9F, z1 - 4, z1 + 6, BigMat.PIPE, 8, null, 2);
            }
        }
    }

    static void solventLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        int n = Math.max(1, (int) (l / 40));
        float sl = l / n, s = a.speed, lvl = h - 6.5F;
        for (int i = 0; i < n; i++) {
            float z0 = sl * i + 1, zm = z0 + Math.min(12, sl * 0.3F), z1 = sl * (i + 1) - 1;
            liquid(m, 1.2F, z0 + 0.8F, w - 1.2F, zm, 0.2F, lvl, blend(tint, 0x5A8CA8, 0.5F), 0xC0);             //emulsion
            float split = lvl * 0.45F;
            liquid(m, 1.2F, zm + 0.8F, w - 1.2F, z1 - 0.8F, 0.2F, split, 0x5A8CA8, 0xA0);                       //phase aqueuse
            liquid(m, 1.2F, zm + 0.8F, w - 1.2F, z1 - 0.8F, split, lvl, tint, 0x78);                            //phase organique
            bubbles(m, 18, 150 + i, 1.5F, w - 1.5F, z0 + 1, zm - 0.5F, 1, lvl - 0.5F, a, 0.05F, BigMat.FROTH, 0.4F);
            for (int k = 0; k < 12; k++) {                                                                        //gouttes qui se separent
                if (BigMachines.hash(k, 152) > s) {
                    continue;
                }
                float ph = (a.phase * 0.01F + k * 0.083F) % 1F;
                float x = 1.5F + BigMachines.hash(k, 153) * (w - 3), z = zm + 1 + ph * (z1 - zm - 2);
                boolean org = k % 2 == 0;
                float y = org ? split - 1 + ph * (lvl - split) * 0.8F : split + 1 - ph * split * 0.8F;
                m.color(org ? 0xFF000000 | tint : 0xFF5A8CA8);
                m.box(x - 0.4F, y, z - 0.4F, x + 0.4F, y + 0.8F, z + 0.4F, BigMat.STRIP, null, 1);
            }
            m.color(0xFFFFFFFF);
        }
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t), g = Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t),
              bb = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bb;
    }

    // ------------------------------------------------------------------ ferme aquacole

    private record Pens(int nx, int nz, float pw, float pl, float r) {
    }

    private static Pens pens(float w, float l) {
        int nx = units(w, 40), nz = Math.max(1, (int) (l / 36));
        float pw = w / nx, pl = l / nz;
        return new Pens(nx, nz, pw, pl, Math.min(pw, pl) * 0.4F);
    }

    static void aquafarm(BigMesh m, float w, float h, float l) {
        Pens p = pens(w, l);
        for (int i = 0; i < p.nx; i++) {
            for (int j = 0; j < p.nz; j++) {
                float cx = p.pw * (i + 0.5F), cz = p.pl * (j + 0.5F);
                m.cyl('y', cx, cz, p.r, h - 6, h - 4.8F, BigMat.RUBBER, 16, null, 2, false, 1);                    //flotteur
                m.cyl('y', cx, cz, p.r - 0.3F, 3, h - 5, BigMat.GRATE, 16, null, 6, false, 0.2F);                  //filet
                for (int k = 0; k < 4; k++) {                                                                       //garde-corps
                    double an = Math.toRadians(k * 90 + 45);
                    float x = cx + (float) Math.cos(an) * p.r, z = cz + (float) Math.sin(an) * p.r;
                    m.box(x - 0.3F, h - 4.8F, z - 0.3F, x + 0.3F, h - 1.5F, z + 0.3F, BigMat.CAST_L, null, 1);
                }
                m.cyl('y', cx, cz, p.r + 0.2F, h - 2, h - 1.5F, BigMat.CAST_L, 16, null, 1, false, 0.5F);
            }
        }
        for (int i = 1; i < p.nx; i++) {                                                                          //passerelles
            m.box(p.pw * i - 1.5F, h - 5, 1, p.pw * i + 1.5F, h - 4, l - 1, BigMat.GRATE, null, 4);
        }
        m.box(1, h - 5, 1, w - 1, h - 4, 3, BigMat.GRATE, null, 4);
        m.box(w / 2 - 2, h - 4, 1, w / 2 + 2, h + 2, 4.5F, BigMat.CAST_L, null, 4);                              //nourrisseur
    }

    static void aquafarmLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        Pens p = pens(w, l);
        float s = a.speed, lvl = h - 5.5F;
        liquid(m, 0.5F, 0.5F, w - 0.5F, l - 0.5F, 0.2F, lvl, tint, 0x90);
        for (int i = 0; i < p.nx; i++) {
            for (int j = 0; j < p.nz; j++) {
                float cx = p.pw * (i + 0.5F), cz = p.pl * (j + 0.5F);
                int nf = 5 + Math.round(5 * s);
                for (int f = 0; f < nf; f++) {                                                                      //poissons qui tournent
                    float rr = p.r * (0.35F + 0.5F * BigMachines.hash(f, 160 + j));
                    float sp = (0.6F + 0.5F * BigMachines.hash(f, 161)) * (f % 2 == 0 ? 1 : -1);
                    float an = (float) Math.toRadians(a.phase * 0.6F * sp + f * 47 + i * 13);
                    float x = cx + (float) Math.cos(an) * rr, z = cz + (float) Math.sin(an) * rr;
                    float y = 4 + BigMachines.hash(f, 162) * (lvl - 8) + 0.6F * (float) Math.sin(a.phase * 0.05F + f);
                    float yaw = (float) Math.toDegrees(-an) + (sp > 0 ? 0 : 180);
                    Matrix4f r = BigMesh.rot('y', x, 0, z, yaw);
                    m.color(f % 3 == 0 ? 0xFF9AA8B4 : 0xFF7E8C98);
                    m.box(x - 0.6F, y, z - 1.6F, x + 0.6F, y + 1.1F, z + 1.4F, BigMat.STRIP, r, 1);
                    m.box(x - 0.15F, y - 0.1F, z - 2.5F, x + 0.15F, y + 1.2F, z - 1.6F, BigMat.STRIP, r, 1);
                }
                bubbles(m, 8, 163 + i * 7 + j, cx - p.r * 0.3F, cx + p.r * 0.3F, cz - p.r * 0.3F, cz + p.r * 0.3F, 1, lvl, a, 0.04F,
                      BigMat.BUBBLE, 0.3F);
            }
        }
        m.color(0xFFFFFFFF);
        if (s > 0.05F) {                                                                                          //granules de nourriture
            for (int k = 0; k < 12; k++) {
                float ph = (a.phase * 0.03F + k * 0.083F) % 1F;
                float x = w / 2 + (k % 4 - 1.5F) * 1.5F + ph * 3 * (k % 2 == 0 ? 1 : -1), z = 5 + ph * Math.min(12, l * 0.2F);
                float y = h + 1 - ph * (h + 1 - lvl);
                m.box(x - 0.25F, y, z - 0.25F, x + 0.25F, y + 0.5F, z + 0.25F, BigMat.WOOD2, null, 1);
            }
        }
    }

    // ------------------------------------------------------------------ piscine de combustible use

    private record Racks(int nx, int nz, float x0, float z0) {
    }

    private static Racks racks(float w, float l) {
        int nx = Math.max(1, (int) ((w - 4) / 5)), nz = Math.max(1, (int) ((l - 14) / 5));
        return new Racks(nx, nz, (w - nx * 5) / 2, 10 + (l - 14 - nz * 5) / 2);
    }

    static void fuelPool(BigMesh m, float w, float h, float l) {
        Racks r = racks(w, l);
        float rh = Math.max(6, Math.min(22, h * 0.5F));
        for (int i = 0; i <= r.nx; i++) {                                                                       //alveoles
            m.box(r.x0 + i * 5 - 0.3F, 0, r.z0, r.x0 + i * 5 + 0.3F, rh, r.z0 + r.nz * 5, BigMat.CAST_L, null, 4);
        }
        for (int j = 0; j <= r.nz; j++) {
            m.box(r.x0, 0, r.z0 + j * 5 - 0.3F, r.x0 + r.nx * 5, rh, r.z0 + j * 5 + 0.3F, BigMat.CAST_L, null, 4);
        }
        for (int i = 0; i < r.nx; i++) {
            for (int j = 0; j < r.nz; j++) {
                if (BigMachines.hash(i * 31 + j, 170) < 0.15F) {
                    continue;                                                                                       //alveoles vides
                }
                float x = r.x0 + i * 5 + 2.5F, z = r.z0 + j * 5 + 2.5F;
                m.box(x - 1.6F, 0.5F, z - 1.6F, x + 1.6F, rh - 1.5F, z + 1.6F, BigMat.STEEL_D, null, 4);         //assemblages
                m.box(x - 0.8F, rh - 1.5F, z - 0.8F, x + 0.8F, rh - 0.5F, z + 0.8F, BigMat.CAST, null, 1);
            }
        }
        for (float x : new float[]{0.5F, w - 2}) {
            m.box(x, h - 1, 0.5F, x + 1.5F, h + 0.5F, l - 0.5F, BigMat.CAST, null, 4);
        }
    }

    static void fuelPoolLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        Racks r = racks(w, l);
        float rh = Math.max(6, Math.min(22, h * 0.5F)), lvl = h - 3.5F, s = a.speed;
        m.glow(15).color(0x60000000 | 0x40B0FF);                                                                //lueur Tcherenkov
        m.box(r.x0 + 0.4F, 0.6F, r.z0 + 0.4F, r.x0 + r.nx * 5 - 0.4F, rh - 1, r.z0 + r.nz * 5 - 0.4F, BigMat.LIQUID, null, 8);
        m.glow(0).color(0xFFFFFFFF);
        liquid(m, 0.5F, 0.5F, w - 0.5F, l - 0.5F, 0.2F, lvl, tint, 0x88);
        //pont de manutention : va et vient, mat et pince
        float t = (a.phase * 0.004F) % 2F, u = t < 1 ? t : 2 - t, z = 4 + (l - 8) * Lot4.smooth(u);
        m.box(0.5F, h + 0.5F, z - 2, w - 0.5F, h + 3, z + 2, BigMat.CAST_L, null, 4);
        float x = w / 2 + (w / 2 - 4) * (float) Math.sin(a.phase * 0.006F);
        m.box(x - 2.5F, h + 3, z - 2.5F, x + 2.5F, h + 6, z + 2.5F, BigMat.MOTOR, null, 4);
        float mast = rh + 2 + (lvl - rh) * 0.5F * (1 + (float) Math.sin(a.phase * 0.01F)) * Lot4.smooth(s * 3);
        m.box(x - 0.9F, mast, z - 0.9F, x + 0.9F, h + 3, z + 0.9F, BigMat.CHROME, null, 2);
        m.box(x - 1.8F, mast - 1.5F, z - 1.8F, x + 1.8F, mast, z + 1.8F, BigMat.CAST, null, 2);
        bubbles(m, 10, 171, r.x0, r.x0 + r.nx * 5, r.z0, r.z0 + r.nz * 5, rh, lvl, a, 0.01F, BigMat.BUBBLE, 0.25F);
    }
}
