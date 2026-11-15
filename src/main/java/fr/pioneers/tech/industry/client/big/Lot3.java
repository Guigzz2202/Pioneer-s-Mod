package fr.pioneers.tech.industry.client.big;

/**
 * 0.19.17 : machines interieures du lot 3 (apercus valides par Guillaume le 25/10/2026) : foreuse, pompes de puits, pompe a balancier,
 * cascade de centrifugeuses, centrifugeuse, chaudiere a tubes de fumee, machines a vis (extrudeuse, presse a huile, extracteur,
 * compresseur de gaz). Meme repere que {@link Lot2} ; partie fixe sans suffixe, partie animee dans les {@code ...Life}.
 */
final class Lot3 {

    /** Dessus du socle des tours ajourees et de la pompe a balancier (px) : la tete de puits et la table y sont posees. */
    private static final float FLOOR = 16;

    private Lot3() {
    }

    private static int argb(int rgb, int alpha) {
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    // ------------------------------------------------------------------ foreuse : colonne du mat

    static void drill(BigMesh m, float w, float h, float l) {
        float cx = w / 2, cz = l / 2;
        m.box(0, 0, 0, w, FLOOR, l, BigMat.CAST, null, 4);                                                   //avant-puits dans le socle
        for (float xs : new float[]{1, w - 2.5F}) {
            m.box(xs, FLOOR, cz - 2, xs + 1.5F, h - 6, cz + 2, BigMat.CAST, null, 4);                         //rails de guidage
        }
        m.box(2, h - 7, 2, w - 2, h - 1, l - 2, BigMat.CAST_L, null, 4);                                     //moufle fixe
        m.box(1.5F, FLOOR, 1.5F, w - 1.5F, FLOOR + 8, l - 1.5F, BigMat.STEEL_D, null, 4);                    //table rotative / obturateur
        m.spin('y', cx, 0, cz, 6);
        m.cyl('y', cx, cz, 5.5F, FLOOR + 8, FLOOR + 10, BigMat.CAST_L, 12, null, 4);
        m.fixed();
    }

    /** Course de la tete : 40 s par tige a plein regime, puis remontee. */
    private static float drillTop(float h, float phase) {
        float t = (phase / 800F) % 1F;
        return h - 26 - t * (h - FLOOR - 50);
    }

    static void drillLife(BigMesh m, float w, float h, float l, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2;
        float yt = drillTop(h, a.phase);
        for (float xs : new float[]{cx - 3, cx + 3}) {                                                        //cables
            m.box(xs - 0.3F, yt + 12, cz - 0.3F, xs + 0.3F, h - 7, cz + 0.3F, BigMat.ROLL, null, 2);
        }
        m.box(2.5F, yt, 2.5F, w - 2.5F, yt + 10, l - 2.5F, BigMat.MOTOR, null, 4);                          //tete d'entrainement
        m.box(4, yt + 10, 4, w - 4, yt + 13, l - 4, BigMat.CAST_L, null, 4);
        var spin = BigMesh.rot('y', cx, 0, cz, a.phase * 6);
        m.cyl('y', cx, cz, 1.6F, FLOOR + 6, yt, BigMat.CHROME, 8, spin, 4);                                 //tige qui tourne
        for (float y = FLOOR + 20; y < yt - 4; y += 30) {
            m.cyl('y', cx, cz, 2.3F, y, y + 3, BigMat.STEEL_D, 8, spin, 4);                                  //joints
        }
        for (int k = 0; k < 8; k++) {                                                                        //deblais qui remontent
            if (BigMachines.hash(k, 70) > a.speed) {
                continue;
            }
            float ph = (a.phase * 0.011F + k * 0.13F) % 1F;
            double an = k * 0.8;
            float bx = cx + 4 * (float) Math.cos(an), bz = cz + 4 * (float) Math.sin(an), by = FLOOR + 8 + ph * 3;
            m.box(bx - 0.7F, by, bz - 0.7F, bx + 0.7F, by + 1.4F, bz + 0.7F, BigMat.ORE2, null, 2);
        }
    }

    // ------------------------------------------------------------------ pompes de puits

    static void well(BigMesh m, float w, float h, float l) {
        float cx = w / 2, cz = l / 2;
        m.box(0, 0, 0, w, FLOOR, l, BigMat.CAST, null, 4);                                                   //avant-puits dans le socle
        m.box(2, h - 14, 2, w - 2, h - 2, l - 2, BigMat.MOTOR, null, 4);                                     //moteur
        m.box(3.5F, h - 18, 3.5F, w - 3.5F, h - 14, l - 3.5F, BigMat.CAST_L, null, 4);
        m.spin('y', cx - 3, 0, cz, 8);
        m.cyl('y', cx - 3, cz, 1, FLOOR + 12, h - 18, BigMat.CHROME, 8, null, 4);                          //arbre
        for (float y = FLOOR + 24; y < h - 22; y += 24) {
            m.cyl('y', cx - 3, cz, 1.8F, y, y + 2, BigMat.STEEL_D, 8, null, 4);                              //accouplements
        }
        m.fixed();
        m.cyl('y', cx + 3, cz, 2.6F, FLOOR + 12, h - 20, BigMat.GLASS, 10, null, 4, false, 0.4F);           //colonne de production
        //tete de puits (arbre de Noel) : vannes et volants rouges
        m.box(3, FLOOR, 3, w - 3, FLOOR + 12, l - 3, BigMat.STEEL_D, null, 4);
        for (float yv : new float[]{FLOOR + 3, FLOOR + 8}) {
            m.box(1, yv, cz - 1.5F, w - 1, yv + 3, cz + 1.5F, BigMat.CAST_L, null, 4);
            m.box(0.2F, yv - 1, cz - 2.5F, 1, yv + 4, cz + 2.5F, BigMat.PAINT_R, null, 2);
        }
    }

    static void wellLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2;
        for (int k = 0; k < 10; k++) {                                                                       //bouchons de fluide qui montent
            if (BigMachines.hash(k, 71) > a.speed) {
                continue;
            }
            float ph = (a.phase * 0.017F + k * 0.1F) % 1F;
            float yy = FLOOR + 12 + ph * (h - FLOOR - 34);
            m.box(cx + 1.6F, yy, cz - 1.4F, cx + 4.4F, yy + 1.2F, cz + 1.4F, BigMat.BUBBLE, null, 2);
        }
        m.color(argb(tint, 0xA0));
        m.cyl('y', cx + 3, cz, 2.2F, FLOOR + 12, h - 20, BigMat.LIQUID, 10, null, 4);
        m.color(0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ pompe a balancier : colonne sous la tete de cheval

    static void jack(BigMesh m, float w, float h, float l) {
        float cx = w / 2, cz = l / 2;
        m.box(0, 0, 0, w, FLOOR, l, BigMat.CAST, null, 4);                                                   //avant-puits dans le socle
        m.box(4, FLOOR + 9, 4, w - 4, FLOOR + 14, l - 4, BigMat.STEEL_D, null, 4);                         //presse-etoupe
        m.box(2.5F, FLOOR, 2.5F, w - 2.5F, FLOOR + 9, l - 2.5F, BigMat.STEEL_D, null, 4);                  //tete de puits
        m.box(0, FLOOR + 3, cz - 1.5F, w, FLOOR + 6, cz + 1.5F, BigMat.CAST_L, null, 4);                   //te de sortie
        m.box(-0.2F, FLOOR + 2, cz - 2.5F, 0.6F, FLOOR + 7, cz + 2.5F, BigMat.PAINT_R, null, 2);
    }

    static void jackLife(BigMesh m, float w, float h, float l, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2;
        float mid = FLOOR + (h - FLOOR) * 0.45F, amp = Math.min(16, (h - FLOOR) * 0.2F);
        float yb = mid + amp * (float) Math.sin(Math.toRadians(a.phase * 2.4F));                               //course : 8 coups par minute
        for (float xs : new float[]{cx - 2.4F, cx + 2.4F}) {
            m.box(xs - 0.3F, yb, cz - 0.3F, xs + 0.3F, h, cz + 0.3F, BigMat.ROLL, null, 2);                  //cables de la tete de cheval
        }
        m.box(2, yb - 1.4F, 5, w - 2, yb, l - 5, BigMat.CAST_L, null, 3);                                    //traverse
        m.cyl('y', cx, cz, 0.9F, FLOOR + 12, yb, BigMat.CHROME, 8, null, 3);                               //tige polie
    }

    // ------------------------------------------------------------------ cascade de centrifugeuses

    /**
     * 1.0.4-beta (lot 26, retours de Guillaume : « les frames + sheetmetal au-dessus etaient censes representer les centrifuges [...] au profit
     * d'une meilleure animation ») : six VRAIES centrifugeuses dans les baies de la structure (cases x 0 et 2, z 2, 4, 6 de la cascade : air
     * exige, derogation au NBT), de part et d'autre de l'allee : socle, bol vertical a douves alternees qui TOURNE (on voit la rotation),
     * paliers et frettes fixes, accouplement et moteur sous la charpente du toit, piquages vers deux collecteurs qui courent le long de
     * l'allee. Repere de la boite (0, 1, 1)..(2, 4, 7) : x en travers (48 px), y (64 px), z le long de l'allee (112 px) ; baies centrees en
     * x 8 / w - 8 et z 24 + 32 k. Tout tient dans sa case (rayon 6,5 px) et dans la boite.
     */
    private record Cascade(float[] xs, float[] zs, float bowlTop) {
    }

    private static Cascade cascadeLayout(float w, float h, float l, int[] prof) {
        float[] xs = w >= 40 ? new float[]{8, w - 8} : new float[]{w / 2};
        int nz = Math.max(1, (int) ((l - 16) / 32));
        float[] zs = new float[nz];
        float z0 = (l - (nz - 1) * 32) / 2;
        for (int j = 0; j < nz; j++) {
            zs[j] = z0 + j * 32;
        }
        return new Cascade(xs, zs, Math.max(20, h - 18));
    }

    /** Rayon du bol et de son socle (px) : dans la case de 16 px, marge comprise. */
    private static final float BOWL_R = 4.6F, BASE_R = 6.5F;

    static void cascade(BigMesh m, float w, float h, float l, int[] prof) {
        Cascade c = cascadeLayout(w, h, l, prof);
        float top = c.bowlTop;
        float aisle0 = w >= 40 ? 17 : 1, aisle1 = w >= 40 ? w - 17 : w - 1;
        m.box(aisle0, 0, 0.5F, aisle1, 1, l - 0.5F, BigMat.GRATE, null, 4);                                   //caillebotis de l'allee
        for (float x : c.xs) {
            for (float z : c.zs) {
                m.box(x - BASE_R, 0, z - BASE_R, x + BASE_R, 2.5F, z + BASE_R, BigMat.CAST, null, 4);         //socle
                m.cyl('y', x, z, 5.6F, 2.5F, 5.5F, BigMat.CAST_L, 16, null, 4);                                 //palier bas
                //bol : douves alternees (la rotation se voit), fond et couvercle ; groupe tournant autour de son axe
                m.spin('y', x, 0, z, 18);
                int n = 12;
                float sw = (float) (2 * BOWL_R * Math.tan(Math.PI / n) + 0.05);
                for (int i = 0; i < n; i++) {
                    m.box(x - sw / 2, 5.5F, z + BOWL_R - 1.0F, x + sw / 2, top, z + BOWL_R, i % 2 == 0 ? BigMat.STEEL_D : BigMat.CHROME,
                          BigMesh.rot('y', x, 0, z, i * 360F / n), 4);
                }
                m.cyl('y', x, z, BOWL_R - 0.9F, 5.5F, top, BigMat.CAST_L, 12, null, 4);                       //coeur du bol
                m.box(x - 0.6F, top, z - BOWL_R + 0.8F, x + 0.6F, top + 1, z + BOWL_R - 0.8F, BigMat.CHROME, null, 2);  //croisillon
                m.fixed();
                for (float y = 16; y < top - 6; y += 16) {
                    m.cyl('y', x, z, BOWL_R + 0.7F, y, y + 1.5F, BigMat.CAST, 16, null, 4, false, 0.7F);       //frettes fixes
                }
                m.cyl('y', x, z, 1.2F, top + 1, top + 4, BigMat.CHROME, 8, null, 2);                          //accouplement
                m.box(x - 4, top + 4, z - 4, x + 4, top + 12, z + 4, BigMat.MOTOR, null, 4);                    //moteur
                m.box(x - 4.4F, top + 6, z - 4.4F, x + 4.4F, top + 7, z + 4.4F, BigMat.CAST, null, 2);          //ailettes
                m.box(x - 4.4F, top + 9, z - 4.4F, x + 4.4F, top + 10, z + 4.4F, BigMat.CAST, null, 2);
                //piquage : du chapeau du bol vers le collecteur de l'allee
                float toward = x < w / 2 ? 1 : -1, xp = x + toward * 4.5F, xc = x < w / 2 ? aisle0 + 2 : aisle1 - 2;
                m.box(Math.min(xp, xc) - 0.8F, top - 3.4F, z - 0.8F, Math.max(xp, xc) + 0.8F, top - 1.8F, z + 0.8F, BigMat.PIPE, null, 2);
            }
            float xc = x < w / 2 ? aisle0 + 2 : aisle1 - 2;
            m.cyl('z', xc, top - 2.6F, 1.4F, c.zs[0] - 8, c.zs[c.zs.length - 1] + 8, BigMat.PIPE, 8, null, 3);    //collecteur
        }
    }

    static void cascadeLife(BigMesh m, float w, float h, float l, int[] prof, BigMachines.Anim a) {
        Cascade c = cascadeLayout(w, h, l, prof);
        int step = (int) (a.phase / 20);
        for (int i = 0; i < c.xs.length; i++) {
            for (int j = 0; j < c.zs.length; j++) {
                boolean on = a.speed > 0.05F && Math.floorMod(j + i * 3 + step, 7) != 0;
                float x = c.xs[i], z = c.zs[j];
                float face = x < w / 2 ? x + BASE_R : x - BASE_R - 0.4F;
                m.glow(on ? 12 : 0).color(on ? 0xFF7CF0A0 : 0xFF405048);
                m.box(face, 0.8F, z - 1, face + 0.4F, 1.8F, z + 1, BigMat.LED, null, 2);                          //voyant d'etat
            }
        }
        m.glow(0).color(0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ centrifugeuse (tour)

    static void centrifuge(BigMesh m, float w, float h, float l, int[] prof) {
        float cx = w / 2, cz = l / 2;
        float r = Lot2.roomBetween(prof, 1, h - 1, w) - 3;
        int n = 16;
        float sw = (float) (2 * r * Math.tan(Math.PI / n) + 0.05);
        m.spin('y', cx, 0, cz, 10);
        for (int i = 0; i < n; i++) {                                                                        //bol a douves alternees
            m.box(cx - sw / 2, 16, cz + r - 1.2F, cx + sw / 2, h - 22, cz + r, i % 2 == 0 ? BigMat.STEEL_D : BigMat.CHROME,
                  BigMesh.rot('y', cx, 0, cz, i * 360F / n), 4);
        }
        m.cyl('y', cx, cz, r * 0.8F, 4, 16, BigMat.CAST, 16, null, 4);                                       //cone de sortie
        m.cyl('y', cx, cz, 2, h - 22, h - 20, BigMat.CHROME, 8, null, 4);
        m.fixed();
        for (float y = 30; y < h - 30; y += 40) {
            m.cyl('y', cx, cz, r + 0.8F, y, y + 2, BigMat.CAST_L, 20, null, 4, false, 1.0F);                    //frettes fixes
        }
        float mw = Math.min(6, r * 0.5F);
        m.box(cx - mw, h - 20, cz - mw, cx + mw, h - 6, cz + mw, BigMat.MOTOR, null, 4);                    //moteur
        m.box(cx - 1.2F, h - 6, cz - 1.2F, cx + 1.2F, h - 1, cz + 1.2F, BigMat.PIPE, null, 2);              //alimentation
    }

    // ------------------------------------------------------------------ chaudiere a tubes de fumee (fut)

    private static float shellR(float w, float h) {
        float d = Math.min(w, h);
        return d <= 16 ? d / 2 - 1.5F : d * 0.42F - 3 * Math.min(1, d / 80);
    }

    static void boiler(BigMesh m, float w, float h, float l) {
        float cx = w / 2, cy = h / 2, r = shellR(w, h), k = Math.max(0.25F, Math.min(1, r / 30));
        float za = Math.min(10, l * 0.1F), zb = l - Math.min(6, l * 0.06F);
        for (float zs : new float[]{za + (zb - za) * 0.2F, za + (zb - za) * 0.8F}) {                          //berceaux
            m.box(cx - r * 0.7F, 0, zs - 4 * k, cx + r * 0.7F, Math.max(0.5F, cy - r * 0.6F), zs + 4 * k, BigMat.CAST, null, 4);
        }
        for (float z = za; z < zb; z += 30) {
            m.cyl('z', cx, cy, r + 0.6F * k, z, Math.min(zb, z + 2 * k), BigMat.STEEL_D, r < 12 ? 16 : 28, null, 4, false, k);  //viroles
        }
        float yf = cy - r * 0.35F;
        float step = Math.max(5, 9 * k);
        for (float z = za + 4 * k; z < zb - 4 * k; z += step) {
            m.cyl('z', cx, yf, r * 0.34F, z, z + step * 0.4F, BigMat.STEEL_D, 12, null, 4, false, 1.4F * k);  //foyer ondule
        }
        for (int i = 0; i < 6; i++) {
            for (int row = 0; row < 2; row++) {
                float xx = cx - r * 0.55F + i * r * 0.22F, yy = cy + r * 0.2F + row * r * 0.24F;
                m.cyl('z', xx, yy, Math.max(0.5F, 1.5F * k), za + 2, zb - 2, BigMat.DRUM, 8, null, 3);        //tubes de fumee
            }
        }
        float zd = za + (zb - za) * 0.6F, dr = Math.min(7, r * 0.25F);
        if (h - 2 > cy + r - 2) {
            m.cyl('y', cx, zd, dr, cy + r - 2, h - 2, BigMat.STEEL_D, 14, null, 4);                         //dome de vapeur
        }
        m.box(cx - k, h - 2, zd - k, cx + k, h + 2 * k, zd + k, BigMat.CHROME, null, 2);                      //soupape
        float bw = Math.min(8, r * 0.3F);
        m.box(cx - bw, yf - bw, 1, cx + bw, yf + bw, za + 4 * k, BigMat.MOTOR, null, 4);                     //bruleur
    }

    static void boilerLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        float cx = w / 2, cy = h / 2, r = shellR(w, h), k = Math.max(0.25F, Math.min(1, r / 30)), s = a.speed;
        float za = Math.min(10, l * 0.1F), zb = l - Math.min(6, l * 0.06F), yf = cy - r * 0.35F;
        if (s > 0.03F) {                                                                                     //flamme dans le foyer
            float f = s * (0.85F + 0.15F * (float) Math.sin(a.phase * 0.45F));
            m.glow(15);
            m.cyl('z', cx, yf, r * 0.26F * f, za + 4 * k, za + 4 * k + (zb - za) * 0.7F * f, BigMat.FLAME, 12, null, 4);
            m.cyl('z', cx, yf, r * 0.14F, za + 4 * k, za + 4 * k + (zb - za) * 0.5F * f, BigMat.FLAME_C, 10, null, 4);
            m.glow(0);
        }
        for (int i = 0; i < 24; i++) {                                                                        //ebullition
            if (BigMachines.hash(i, 72) > s) {
                continue;
            }
            float ph = (a.phase * 0.022F + i * 0.137F) % 1F;
            float bz = za + 6 + (i * 37 % 97) / 97F * (zb - za - 12);
            float bx = cx + ((i * 13) % 11 - 5) * r * 0.12F, by = yf + r * 0.3F + ph * r * 0.9F, d = Math.max(0.4F, k);
            m.box(bx - d, by - d, bz - d, bx + d, by + d, bz + d, BigMat.BUBBLE, null, 2);
        }
        m.color(argb(tint, 0x70));
        m.cyl('z', cx, cy, r, za, zb, BigMat.LIQUID, 28, null, 8);                                           //eau
        m.color(0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ machines a vis (fut)

    private record Screw(float k, float yb, float za, float zf, float zb, float rb) {
    }

    private static Screw screw(float w, float h, float l) {
        float k = Math.max(0.3F, Math.min(1, Math.min(w, h) / 80));
        float yb = h * 0.375F, za = l * 0.24F, zf = za + 24 * k, zb = l - Math.max(8, l * 0.16F);
        return new Screw(k, yb, za, zf, zb, 8 * k);
    }

    static void screwMachine(BigMesh m, float w, float h, float l) {
        float cx = w / 2;
        Screw s = screw(w, h, l);
        float k = s.k, yb = s.yb;
        m.box(cx - 22 * k, 0, 2, cx + 22 * k, yb - 18 * k, l - 2, BigMat.CAST, null, 4);                      //bati
        m.box(cx - 13 * k, yb - 18 * k, 2, cx + 13 * k, yb + 10 * k, s.za - 16 * k, BigMat.MOTOR, null, 4);    //moteur
        m.box(cx - 11 * k, yb - 18 * k, s.za - 16 * k, cx + 11 * k, yb + 14 * k, s.za, BigMat.CAST_L, null, 4); //reducteur
        //zone d'alimentation ouverte : vis visible sous la tremie
        m.spin('z', cx, yb, 0, 12);
        m.cyl('z', cx, yb, 2.4F * k, s.za, s.zf, BigMat.CHROME, 10, null, 4);
        int nf = (int) ((s.zf - s.za) / (3 * k));
        for (int i = 0; i < nf; i++) {
            float z = s.za + i * 3 * k;
            m.box(cx - 0.6F * k, yb + 2 * k, z, cx + 0.6F * k, yb + 8 * k, z + 1.4F * k, BigMat.ROLL, BigMesh.rot('z', cx, yb, 0, i * 60), 2);
        }
        m.fixed();
        m.box(cx - 10 * k, yb - 10 * k, s.za, cx + 10 * k, yb - 8 * k, s.zf, BigMat.CAST_L, null, 4);         //auge
        for (int side = -1; side <= 1; side += 2) {                                                          //tremie en entonnoir
            m.box(cx + side * 6 * k - 6 * k, yb + 14 * k, s.za + 2, cx + side * 6 * k + 6 * k, yb + 15 * k, s.zf - 2, BigMat.CAST_L,
                  BigMesh.rot('z', cx + side * 6 * k, yb + 14 * k, 0, -side * 40), 4);
        }
        m.box(cx - 5 * k, yb + 9 * k, s.za + 6 * k, cx + 5 * k, yb + 13 * k, s.zf - 6 * k, BigMat.ORE2, null, 4);
        m.cyl('z', cx, yb, s.rb, s.zf, s.zb, BigMat.CAST_L, 16, null, 4);                                     //fourreau
        m.box(cx - 9 * k, yb - 9 * k, s.zb, cx + 9 * k, yb + 9 * k, s.zb + 6 * k, BigMat.STEEL_D, null, 4);     //filiere
        m.box(cx - 20 * k, yb - 12 * k, s.zb + 6 * k, cx + 20 * k, yb - 10 * k, l - 2, BigMat.RUBBER, null, 4); //convoyeur
    }

    static void screwLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        float cx = w / 2, sp = a.speed;
        Screw s = screw(w, h, l);
        float k = s.k, yb = s.yb;
        int i = 0;
        for (float z = s.zf + 6 * k; z < s.zb - 6 * k; z += 14 * k, i++) {                                      //colliers chauffants
            m.glow(Math.round(3 + 10 * sp)).color(Glow.heat(0.35F + 0.65F * sp));
            m.cyl('z', cx, yb, s.rb + k, z, z + 6 * k, i % 2 == 0 ? BigMat.GLOWBED : BigMat.GLOWROD, 16, null, 4);
        }
        m.glow(0).color(0xFFFFFFFF);
        if (tint == 0) {
            return;                                                                                          //compresseur : pas d'extrudat
        }
        //extrudat qui sort de la filiere et refroidit sur le convoyeur (avance avec la phase, sort tant que la machine travaille)
        float z0 = s.zb + 6 * k, z1 = l - 3, seg = 8 * k;
        float off = (a.phase * 0.25F * k) % seg;
        float reach = z0 + (z1 - z0) * Math.min(1, sp * 1.25F);
        for (float z = z0 - seg + off; z < reach; z += seg) {
            float ya = Math.max(z, z0), yc = Math.min(z + seg, reach);
            if (yc <= ya) {
                continue;
            }
            float t = (ya - z0) / Math.max(1, z1 - z0);
            int hot = Math.round(Math.max(0, 12 * (1 - t * 3)));
            m.glow(hot).color(Glow.cool(tint, t));
            m.box(cx - 3 * k, yb - 10 * k, ya, cx + 3 * k, yb - 4 * k, yc, BigMat.STRIP, null, 4);
        }
        m.glow(0).color(0xFFFFFFFF);
    }

    /** Teintes : rouge cerise (chaud) -> couleur du produit (froid). */
    static final class Glow {

        private Glow() {
        }

        static int heat(float k) {
            int v = Math.max(0, Math.min(255, Math.round(255 * k)));
            return 0xFF000000 | v << 16 | v << 8 | v;
        }

        static int cool(int tint, float t) {
            float u = Math.min(1, t * 3);
            int r = Math.round(230 + ((tint >> 16 & 255) - 230) * u), g = Math.round(96 + ((tint >> 8 & 255) - 96) * u),
                  b = Math.round(48 + ((tint & 255) - 48) * u);
            return 0xFF000000 | r << 16 | g << 8 | b;
        }
    }
}
