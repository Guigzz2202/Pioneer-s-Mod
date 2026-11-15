package fr.pioneers.tech.industry.client.big;

import fr.pioneers.tech.industry.multi.IndustryShape;
import org.joml.Matrix4f;

/**
 * 0.19.21 : machines interieures du lot 7 : formes de revolution (cyclone, tour de refroidissement, sphere d'ergols), colonne du
 * melangeur a cuves jumelles et pavés (echangeur a plaques, cellule chaude, forge, groupe froid). Les formes rondes epousent le profil
 * (rayon libre par rangee, {@link Lot2#room}).
 */
final class Lot7 {

    private Lot7() {
    }

    private static int argb(int rgb, int alpha) {
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    /** Rayon de la cuve de revolution a la hauteur y : rayon libre lisse sur +- 6 px, moins une marge. */
    private static float shell(int[] prof, float y, float w, float h, float margin) {
        float r = Float.MAX_VALUE;
        for (float d = -6; d <= 6; d += 3) {
            r = Math.min(r, Lot2.room(prof, Math.max(0.5F, Math.min(h - 0.5F, y + d)), w));
        }
        return Math.max(1.5F, r - margin);
    }

    /** Enveloppe translucide par bandes de 4 px et cerclages tous les 16 px. */
    private static void vessel(BigMesh m, int[] prof, float w, float h, float y0, float y1, float margin, BigMat skin) {
        float cx = w / 2, cz = w / 2;
        for (float y = y0; y < y1; y += 4) {
            float r = shell(prof, y + 2, w, h, margin);
            m.cyl('y', cx, cz, r, y, Math.min(y1, y + 4), skin, 18, null, 4, false, 0.4F);
            if ((int) (y - y0) % 16 == 0) {
                m.cyl('y', cx, cz, r + 0.4F, y, y + 0.8F, BigMat.STEEL_D, 18, null, 2, false, 0.6F);
            }
        }
    }

    // ------------------------------------------------------------------ cyclone

    static void cyclone(BigMesh m, float w, float h, float l, int[] prof) {
        float cx = w / 2, cz = l / 2;
        vessel(m, prof, w, h, 2, h - 18, 2, BigMat.GLASS);
        float rt = shell(prof, h - 20, w, h, 2);
        m.cyl('y', cx, cz, rt + 0.5F, h - 18, h - 16, BigMat.CAST_L, 18, null, 4);                              //toit
        m.cyl('y', cx, cz, Math.min(4, rt * 0.4F), h * 0.55F, h - 1, BigMat.PIPE, 12, null, 4, false, 0.8F);     //tube plongeur
        float ri = Math.max(2, shell(prof, h * 0.7F, w, h, 2));
        float yi = h * 0.7F;
        m.box(cx + ri * 0.4F, yi - 3, cz + ri - 0.5F, Math.min(w - 0.5F, cx + ri + 6), yi + 3, cz + ri + 4, BigMat.PIPE, null, 4);  //entree tangentielle
        m.cyl('y', cx, cz, 1.5F, 0, 3, BigMat.CAST, 8, null, 2);                                                 //sortie des poussieres
    }

    static void cycloneLife(BigMesh m, float w, float h, float l, int[] prof, int tint, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2, s = a.speed;
        if (s < 0.03F) {
            return;
        }
        for (int k = 0; k < 70; k++) {                                                                           //poussieres en spirale vers le bas
            if (BigMachines.hash(k, 300) > s) {
                continue;
            }
            float ph = (a.phase * 0.006F + k * 0.0143F) % 1F;
            float y = h * 0.72F - ph * (h * 0.72F - 4);
            float r = shell(prof, y, w, h, 3.2F);
            float an = (float) (ph * 40 + k * 0.9);
            m.color(0xFF000000 | tint);
            float x = cx + (float) Math.cos(an) * r, z = cz + (float) Math.sin(an) * r;
            m.box(x - 0.45F, y, z - 0.45F, x + 0.45F, y + 0.9F, z + 0.45F, BigMat.STRIP, null, 1);
        }
        m.color(0xFFF0F4F8);
        for (int k = 0; k < 24; k++) {                                                                           //tourbillon de gaz propre vers le haut
            float ph = (a.phase * 0.01F + k * 0.0417F) % 1F;
            float y = h * 0.2F + ph * (h * 0.55F);
            float r = Math.min(2.5F, shell(prof, y, w, h, 3) * 0.3F);
            float an = (float) (-ph * 30 - k);
            float x = cx + (float) Math.cos(an) * r, z = cz + (float) Math.sin(an) * r;
            m.box(x - 0.35F, y, z - 0.35F, x + 0.35F, y + 1.4F, z + 0.35F, BigMat.FROTH, null, 1);
        }
        m.color(0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ tour de refroidissement

    /** 0.20.26 : dessus du radier du bassin (px, sous le garnissage : les pieds font {@link IndustryShape#HYPER_LEGS} blocs). */
    private static final float BASIN = -16F * IndustryShape.HYPER_LEGS;

    /** Rayon du garnissage (px) : la rangee des modules est un disque. */
    private static float packing(int[] prof, float w) {
        return prof.length > 0 && prof[0] > 0 ? Math.max(6, prof[0] / 10 * 8 - 2.5F) : w / 2 - 2;
    }

    /** Hauteur de la coque (px) au-dessus du bas du garnissage (BigInterior : cages = {hauteur}). */
    private static float towerTop(int[] stands, float h) {
        return stands.length > 0 ? stands[0] : h + 160;
    }

    /** Rayon libre dans la coque (px) a la hauteur y : col aux 72 % (meme profil que IndustryShape.hyperRadius). */
    private static float throat(float r, float y, float top) {
        float yw = 0.72F * top, k = 0.38F;
        if (y <= yw) {
            return r * (1 - k * (float) Math.sin(Math.PI / 2 * Math.max(0, y) / yw));
        }
        return r * (1 - k + 0.12F * (y - yw) / Math.max(1, top - yw));
    }

    /**
     * 0.20.26 : tour construite : garnissage en nids d'abeilles (couches de grilles) sur poutres, rampes de distribution en etoile et buses,
     * separateur de gouttes, colonne montante depuis le bassin, poteaux jusqu'au radier entre les pieds.
     */
    static void cooling(BigMesh m, float w, float h, float l, int[] prof, int[] stands) {
        float cx = w / 2, cz = l / 2, r = packing(prof, w);
        for (float y = 1.5F; y < 11; y += 2.4F) {                                                                 //garnissage
            m.cyl('y', cx, cz, r, y, y + 1.1F, BigMat.GRATE, 28, null, 6, false, r);
        }
        m.cyl('y', cx, cz, r + 0.3F, 0, 12, BigMat.CAST_L, 28, null, 4, false, 0.5F);                           //virole du garnissage
        for (int k = 0; k < 4; k++) {                                                                             //poutres porteuses
            m.box(cx - r, 0, cz - 0.9F, cx + r, 1.4F, cz + 0.9F, BigMat.CAST_L, BigMesh.rot('y', cx, 0, cz, k * 45 + 22.5F), 2);
        }
        for (int k = 0; k < 6; k++) {                                                                             //rampes et buses
            Matrix4f g = BigMesh.rot('y', cx, 0, cz, k * 30);
            m.box(cx - r + 2, 13, cz - 0.7F, cx + r - 2, 14.4F, cz + 0.7F, BigMat.PIPE, g, 2);
            for (float x = cx - r + 6; x < cx + r - 5; x += 8) {
                if (Math.abs(x - cx) > 4) {
                    m.box(x - 0.5F, 12, cz - 0.5F, x + 0.5F, 13, cz + 0.5F, BigMat.CAST, g, 1);
                }
            }
        }
        m.cyl('y', cx, cz, 2.6F, BASIN, 15.5F, BigMat.PIPE, 12, null, 4);                                        //colonne montante
        m.cyl('y', cx, cz, 4, 13, 15.5F, BigMat.CAST, 12, null, 4);                                              //collecteur
        float rs = throat(r, 20, towerTop(stands, h)) - 1;
        m.cyl('y', cx, cz, rs, 19, 20.5F, BigMat.GRATE, 28, null, 4, false, rs);                                  //separateur
        for (int k = 0; k < 8; k++) {                                                                             //poteaux
            double an = Math.toRadians(k * 45 + 22.5);
            float x = cx + (float) Math.cos(an) * r * 0.62F, z = cz + (float) Math.sin(an) * r * 0.62F;
            m.box(x - 0.9F, BASIN, z - 0.9F, x + 0.9F, 0, z + 0.9F, BigMat.CAST_L, null, 2);
        }
        m.cyl('y', cx, cz, 7, BASIN, BASIN + 1.2F, BigMat.CAST, 16, null, 4);                                    //puisard
    }

    /**
     * Eau du bassin qui ondule ; en marche : pluie dense sous le garnissage (le debit suit le regime), embruns sous les buses, brume
     * translucide qui monte en tournant dans la coque et s'evase au col. Le panache au-dessus est en particules (RenderBigMachine).
     */
    static void coolingLife(BigMesh m, float w, float h, float l, int[] prof, int[] stands, int tint, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2, s = a.speed, r = packing(prof, w), top = towerTop(stands, h);
        float rip = 0.4F * (float) Math.sin(a.phase * 0.07F);
        m.color(argb(tint, 0x9C));
        m.cyl('y', cx, cz, r + 8, BASIN + 0.15F, BASIN + 3 + rip * s, BigMat.LIQUID, 32, null, 8);                      //eau du bassin
        m.color(0xFFFFFFFF);
        if (s < 0.03F) {
            return;
        }
        for (int k = 0; k < 160; k++) {                                                                           //pluie
            if (BigMachines.hash(k, 310) > s) {
                continue;
            }
            float ph = (a.phase * 0.045F + k * 0.00625F) % 1F;
            float rr = r * (float) Math.sqrt(BigMachines.hash(k, 311));
            double an = BigMachines.hash(k, 312) * Math.PI * 2;
            float x = cx + (float) Math.cos(an) * rr, z = cz + (float) Math.sin(an) * rr, y = -ph * (-BASIN - 3);
            m.box(x - 0.18F, y - 2, z - 0.18F, x + 0.18F, y, z + 0.18F, BigMat.BUBBLE, null, 1);
        }
        for (int k = 0; k < 40; k++) {                                                                            //embruns sous les buses
            if (BigMachines.hash(k, 316) > s) {
                continue;
            }
            float ph = (a.phase * 0.12F + k * 0.025F) % 1F;
            float rr = r * 0.95F * (float) Math.sqrt(BigMachines.hash(k, 317));
            double an = BigMachines.hash(k, 318) * Math.PI * 2;
            float x = cx + (float) Math.cos(an) * rr, z = cz + (float) Math.sin(an) * rr, y = 12 - ph * 1.6F;
            m.box(x - 0.2F, y, z - 0.2F, x + 0.2F, y + 0.4F, z + 0.2F, BigMat.BUBBLE, null, 1);
        }
        for (int k = 0; k < 46; k++) {                                                                            //brume dans la coque
            if (BigMachines.hash(k, 313) > s) {
                continue;
            }
            float ph = (a.phase * 0.0035F + k * 0.0217F) % 1F;
            float d = 2F + 3.5F * ph + 1.5F * BigMachines.hash(k, 319);
            //1.0.4-beta (lot 25, « rien ne doit depasser ») : la bouffee la plus haute s'arrete au col (le panache au-dessus est en particules)
            float y = 21 + ph * (top - 21 - d * 1.2F - 0.25F);
            float rr = throat(r, y, top) * 0.8F * (float) Math.sqrt(BigMachines.hash(k, 314));
            double an = BigMachines.hash(k, 315) * Math.PI * 2 + ph * 2.5;
            float x = cx + (float) Math.cos(an) * rr, z = cz + (float) Math.sin(an) * rr;
            int alpha = (int) (0x58 * Math.min(1, (1 - ph) * 3) * Math.min(1, ph * 8));
            m.color(argb(0xF2F5F8, alpha));
            m.box(x - d, y, z - d, x + d, y + d * 1.2F, z + d, BigMat.LIQUID, null, 4);
        }
        m.color(0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ sphere d'ergols cryogeniques

    static void sphere(BigMesh m, float w, float h, float l, int[] prof) {
        float cx = w / 2, cz = l / 2;
        vessel(m, prof, w, h, 1, h - 1, 2.5F, BigMat.GLASS);
        for (float y = h * 0.3F; y < h * 0.8F; y += h * 0.25F) {                                                 //chicanes anti-ballottement
            float r = shell(prof, y, w, h, 3.5F);
            m.cyl('y', cx, cz, r, y, y + 0.6F, BigMat.CAST_L, 18, null, 4, false, Math.max(1, r * 0.25F));
        }
        m.cyl('y', cx, cz, 1.2F, h * 0.1F, h, BigMat.PIPE, 8, null, 2);                                          //tube de remplissage
        m.cyl('y', cx, cz, 3, 0, 2, BigMat.CAST, 12, null, 2);
    }

    static void sphereLife(BigMesh m, float w, float h, float l, int[] prof, int tint, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2, s = a.speed;
        float lvl = h * (0.25F + 0.35F * (0.5F + 0.5F * (float) Math.sin(a.phase * 0.0015F)) * Math.max(0.2F, s) + 0.1F);
        m.color(argb(tint, 0xA0));
        for (float y = 1.5F; y < lvl; y += 4) {                                                                    //liquide cryogenique
            float r = shell(prof, y + 2, w, h, 3);
            m.cyl('y', cx, cz, r, y, Math.min(lvl, y + 4), BigMat.LIQUID, 18, null, 6);
        }
        m.color(0xFFFFFFFF);
        float rs = shell(prof, lvl, w, h, 3);
        for (int k = 0; k < 20; k++) {                                                                            //evaporation en surface
            float ph = (a.phase * 0.01F + k * 0.05F) % 1F;
            double an = BigMachines.hash(k, 320) * Math.PI * 2;
            float rr = rs * 0.9F * (float) Math.sqrt(BigMachines.hash(k, 321));
            float x = cx + (float) Math.cos(an) * rr, z = cz + (float) Math.sin(an) * rr, y = lvl + ph * 6, d = 0.6F + ph * 1.4F;
            m.color(0xFFE8F4FF);
            m.box(x - d, y, z - d, x + d, y + d, z + d, BigMat.FROTH, null, 2);
        }
        m.color(0xFFFFFFFF);
        if (s > 0.05F) {                                                                                          //remplissage
            for (int k = 0; k < 8; k++) {
                float ph = (a.phase * 0.06F + k * 0.125F) % 1F;
                float y = h * 0.1F - ph * (h * 0.1F - 1);
                m.color(argb(tint, 0xFF));
                m.box(cx - 0.4F, Math.max(lvl, y), cz - 0.4F, cx + 0.4F, Math.max(lvl, y) + 1, cz + 0.4F, BigMat.STRIP, null, 1);
            }
            m.color(0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------------ melangeur (colonne entre les deux cuves)

    static void mixer(BigMesh m, float w, float h, float l) {
        float cx = w / 2, cz = l / 2, r = Math.min(w, l) / 2 - 2;
        m.cyl('y', cx, cz, r, 6, h - 10, BigMat.GLASS, 14, null, 6, false, 0.3F);                                //colonne transparente
        for (float y = 6; y < h - 9; y += 16) {
            m.cyl('y', cx, cz, r + 0.5F, y, y + 1, BigMat.STEEL_D, 14, null, 2, false, 0.8F);
        }
        m.cyl('y', cx, cz, r + 1, 0, 6, BigMat.CAST_L, 14, null, 4);                                              //pied et arrivees
        for (int side = -1; side <= 1; side += 2) {
            m.box(side < 0 ? 0 : cx, 2, cz - 1.2F, side < 0 ? cx : w, 4.4F, cz + 1.2F, BigMat.PIPE, null, 2);
        }
        m.box(cx - 4, h - 10, cz - 4, cx + 4, h - 2, cz + 4, BigMat.MOTOR, null, 4);                              //moteur
        m.spin('y', cx, 0, cz, 8);                                                                                //helice de melange
        m.cyl('y', cx, cz, 0.7F, 6, h - 10, BigMat.CHROME, 8, null, 2);
        for (float y = 8; y < h - 12; y += 2) {
            m.box(cx - r + 1, y, cz - 0.5F, cx + r - 1, y + 0.6F, cz + 0.5F, BigMat.CAST_L, BigMesh.rot('y', cx, 0, cz, y * 15), 2);
        }
        m.fixed();
    }

    static void mixerLife(BigMesh m, float w, float h, float l, int tint, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2, r = Math.min(w, l) / 2 - 2, s = a.speed;
        m.color(argb(tint, 0x70));
        m.cyl('y', cx, cz, r - 0.4F, 6, h - 11, BigMat.LIQUID, 14, null, 6);
        m.color(0xFFFFFFFF);
        for (int k = 0; k < 30; k++) {                                                                           //filets des deux fluides
            if (BigMachines.hash(k, 330) > s) {
                continue;
            }
            float ph = (a.phase * 0.01F + k * 0.033F) % 1F;
            float y = 7 + ph * (h - 19), an = (float) (ph * 25 + k * 0.7), rr = (r - 1.2F) * (0.4F + 0.6F * BigMachines.hash(k, 331));
            float x = cx + (float) Math.cos(an) * rr, z = cz + (float) Math.sin(an) * rr;
            m.color(k % 2 == 0 ? 0xFF000000 | tint : 0xFFE8E0C8);
            m.box(x - 0.35F, y, z - 0.35F, x + 0.35F, y + 1, z + 0.35F, BigMat.STRIP, null, 1);
        }
        m.color(0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ echangeur a plaques

    private static int packs(float w) {
        return w >= 48 ? 2 : 1;
    }

    static void plates(BigMesh m, float w, float h, float l) {
        int n = packs(w);
        float uw = w / n;
        for (int i = 0; i < n; i++) {
            float x0 = uw * i + 2, x1 = uw * (i + 1) - 2, cx = (x0 + x1) / 2, ph = Math.min(h - 4, 40);
            m.box(x0, 0, 2, x1, ph + 2, 6, BigMat.CAST, null, 4);                                                 //plateau fixe
            m.box(x0, 0, l - 9, x1, ph + 2, l - 6, BigMat.CAST_L, null, 4);                                      //plateau de serrage
            m.box(x0 + 1, 0, l - 6, x1 - 1, 2, l - 2, BigMat.CAST, null, 4);                                     //colonne d'appui
            m.box(cx - 1.2F, ph + 2, 2, cx + 1.2F, ph + 4, l - 2, BigMat.CHROME, null, 4);                       //barre porteuse
            m.box(cx - 1, 0, 6, cx + 1, 2, l - 9, BigMat.CHROME, null, 4);                                       //barre de guidage
            for (float x : new float[]{x0 + 1, x1 - 1}) {
                for (float y : new float[]{3, ph - 1}) {
                    m.cyl('z', x, y, 0.7F, 1, l - 4, BigMat.CHROME, 6, null, 2);                                    //tirants
                }
            }
            int k = 0;
            for (float z = 6.4F; z < l - 9.2F; z += 1.3F, k++) {                                                   //plaques alternees chaud / froid
                m.color(k % 2 == 0 ? 0xFFD8A090 : 0xFF90B0D8);
                m.box(x0 + 1.5F, 2.5F, z, x1 - 1.5F, ph, z + 0.9F, BigMat.STRIP, null, 4);
            }
            m.color(0xFFFFFFFF);
            for (int p = 0; p < 4; p++) {                                                                          //piquages
                float px = p % 2 == 0 ? x0 + 3.5F : x1 - 3.5F, py = p < 2 ? ph - 4 : 6.5F;
                m.cyl('z', px, py, 2, 0, 2, p % 2 == 0 ? BigMat.PAINT_R : BigMat.PIPE, 10, null, 2);
            }
        }
    }

    static void platesLife(BigMesh m, float w, float h, float l, BigMachines.Anim a) {
        int n = packs(w);
        float uw = w / n, s = a.speed;
        for (int i = 0; i < n; i++) {
            float x0 = uw * i + 2, x1 = uw * (i + 1) - 2, ph = Math.min(h - 4, 40);
            m.glow(Math.round(10 * s)).color(0xFF000000 | heatRgb(0.3F + 0.6F * s));                             //bord chaud des plaques
            m.box(x0 + 1.4F, ph - 2, 6.4F, x0 + 1.6F, ph, l - 9.2F, BigMat.STRIP, null, 4);
            m.glow(0).color(0xFF7CC0FF);
            m.box(x1 - 1.6F, 2.5F, 6.4F, x1 - 1.4F, 4.5F, l - 9.2F, BigMat.STRIP, null, 4);
            m.color(0xFFFFFFFF);
            if (s < 0.05F) {
                continue;
            }
            for (int k = 0; k < 12; k++) {                                                                        //ecoulements aux bords
                float t = (a.phase * 0.03F + k * 0.083F) % 1F, z = 6.4F + t * (l - 15.6F);
                boolean hot = k % 2 == 0;
                m.glow(hot ? 8 : 0).color(hot ? 0xFFF09060 : 0xFF70B0F0);
                m.box(hot ? x0 + 1.2F : x1 - 1.8F, hot ? ph - 1.6F : 3, z, hot ? x0 + 1.8F : x1 - 1.2F, hot ? ph - 1 : 3.6F, z + 1.2F, BigMat.STRIP,
                      null, 1);
            }
            m.glow(0).color(0xFFFFFFFF);
        }
    }

    private static int heatRgb(float k) {
        int r = Math.round(90 + 165 * Math.min(1, k * 1.6F)), g = Math.round(84 + 140 * k * k), b = Math.round(80 + 60 * k * k * k);
        return r << 16 | g << 8 | b;
    }

    // ------------------------------------------------------------------ cellule chaude

    private record Cells(int n, float cl) {
    }

    private static Cells hotCells(float l) {
        int n = Math.max(1, (int) (l / 32));
        return new Cells(n, l / n);
    }

    static void hotCell(BigMesh m, float w, float h, float l) {
        Cells c = hotCells(l);
        float ch = Math.min(h - 2, 30), wall = Math.min(4, w * 0.12F);
        for (int i = 0; i < c.n; i++) {
            float z0 = c.cl * i + 1, z1 = c.cl * (i + 1) - 1;
            m.box(1, 0, z0, w - 1, 1.5F, z1, BigMat.STEEL_D, null, 4);                                            //sol inox
            m.box(1, 0, z0, wall + 1, ch, z1, BigMat.CAST, null, 6);                                              //murs de beton
            m.box(1, 0, z0, w - 1, ch, z0 + wall * 0.6F, BigMat.CAST, null, 6);
            m.box(1, ch - 2, z0, wall + 3, ch, z1, BigMat.CAST, null, 6);                                         //corniche (cellule ouverte en haut)
            float wz0 = z0 + wall, wz1 = z1 - 2;
            m.box(w - 1 - wall, 0, z1 - 2, w - 1, ch, z1, BigMat.CAST, null, 4);                                 //face avant : verre au plomb
            m.box(w - 1 - wall, 0, wz0, w - 1, ch * 0.35F, wz1, BigMat.CAST, null, 4);
            m.box(w - 1 - wall, ch * 0.75F, wz0, w - 1, ch - 2, wz1, BigMat.CAST, null, 4);
            m.color(argb(0xE8C870, 0x70));
            m.box(w - 1 - wall, ch * 0.35F, wz0, w - 1, ch * 0.75F, wz1, BigMat.LIQUID, null, 4);
            m.color(0xFFFFFFFF);
            float tz = (z0 + z1) / 2;
            m.box(w * 0.3F, 1.5F, tz - 4, w * 0.62F, 7, tz + 4, BigMat.CAST_L, null, 4);                         //paillasse
            for (int side = -1; side <= 1; side += 2) {                                                            //traversees des telemanipulateurs
                float z = tz + side * Math.min(5, (z1 - z0) * 0.2F);
                m.cyl('x', ch * 0.82F, z, 1.4F, w - 1 - wall - 0.5F, w - 0.5F, BigMat.CHROME, 10, null, 2);
            }
            m.glow(12).color(0xFFFFF4D8);
            m.box(w * 0.3F, ch - 2.3F, tz - 3, w * 0.7F, ch - 2, tz + 3, BigMat.LED, null, 2);                    //eclairage
            m.glow(0).color(0xFFFFFFFF);
        }
    }

    static void hotCellLife(BigMesh m, float w, float h, float l, BigMachines.Anim a) {
        Cells c = hotCells(l);
        float ch = Math.min(h - 2, 30), wall = Math.min(4, w * 0.12F), s = a.speed;
        for (int i = 0; i < c.n; i++) {
            float z0 = c.cl * i + 1, z1 = c.cl * (i + 1) - 1, tz = (z0 + z1) / 2;
            m.glow(Math.round(6 + 8 * s)).color(0xFF70FF90);                                                      //echantillon actif
            m.cyl('y', w * 0.46F, tz, 1, 7, 9, BigMat.LED, 8, null, 1);
            m.glow(0).color(0xFFFFFFFF);
            for (int side = -1; side <= 1; side += 2) {
                float zb = tz + side * Math.min(5, (z1 - z0) * 0.2F);
                float t = a.phase * 0.03F * Math.max(0.15F, s) + i * 2 + side;
                float tx = w * 0.46F + (float) Math.sin(t) * 2.5F, ty = 9.5F + 2 * (0.5F + 0.5F * (float) Math.sin(t * 1.7F));
                float tzz = tz + side * 1.5F + (float) Math.cos(t * 0.8F) * 1.5F;
                float px = w - 1 - wall - 0.5F, py = ch * 0.82F;
                Lot4.beam(m, px, py, zb, (px + tx) / 2 + 2, py - 2, (zb + tzz) / 2, 1.2F, BigMat.CHROME);            //bras
                Lot4.beam(m, (px + tx) / 2 + 2, py - 2, (zb + tzz) / 2, tx, ty + 1.5F, tzz, 0.9F, BigMat.CHROME);   //avant-bras
                float open = 0.3F + 0.3F * (0.5F + 0.5F * (float) Math.sin(t * 3));
                m.box(tx - 0.2F - open, ty, tzz - 0.3F, tx + 0.2F - open, ty + 1.5F, tzz + 0.3F, BigMat.CAST, null, 1);  //pinces
                m.box(tx - 0.2F + open, ty, tzz - 0.3F, tx + 0.2F + open, ty + 1.5F, tzz + 0.3F, BigMat.CAST, null, 1);
            }
        }
    }

    // ------------------------------------------------------------------ forge (marteau-pilon)

    private record Smithy(int nx, int nz, float cw, float cl) {
    }

    private static Smithy smithy(float w, float l) {
        int nx = Math.max(1, Math.round(w / 40)), nz = Math.max(1, Math.round(l / 64));
        return new Smithy(nx, nz, w / nx, l / nz);
    }

    static void forge(BigMesh m, float w, float h, float l) {
        Smithy f = smithy(w, l);
        for (int i = 0; i < f.nx; i++) {
            m.shift(f.cw * i);
            for (int j = 0; j < f.nz; j++) {
                forgeAt(m, f.cw, h, f.cl, f.cl * j);
            }
        }
        m.shift(0);
    }

    static void forgeLife(BigMesh m, float w, float h, float l, BigMachines.Anim a) {
        Smithy f = smithy(w, l);
        for (int i = 0; i < f.nx; i++) {
            m.shift(f.cw * i);
            for (int j = 0; j < f.nz; j++) {
                BigMachines.Anim b = new BigMachines.Anim();
                b.speed = a.speed;
                b.phase = a.phase + (i * 3 + j) * 2.3F;
                forgeAtLife(m, f.cw, h, f.cl, f.cl * j, b);
            }
        }
        m.shift(0);
    }

    private static float forgeK(float w, float h, float l) {
        return Math.max(1, Math.min(1.7F, Math.min(Math.min(w / 28, l / 48), h / 32)));
    }

    private static void forgeAt(BigMesh m, float w, float h, float l, float oz) {
        float k = forgeK(w, h, l), cx = w / 2, zh = oz + l * 0.25F, za = oz + l * 0.68F, hh = Math.min(h - 2, 44 * k);
        float fw = Math.min(w / 2 - 1, 10 * k);
        m.box(cx - fw, 0, zh - 8 * k, cx + fw, 8 * k, zh + 8 * k, BigMat.REFR_D, null, 4);                         //foyer
        m.box(cx - fw + 1.5F, 6 * k, zh - 6.5F * k, cx + fw - 1.5F, 8 * k + 0.2F, zh + 6.5F * k, BigMat.COKE, null, 4);
        m.box(cx - 3 * k, 8 * k + 6, zh - 3 * k, cx + 3 * k, Math.min(h, hh + 4), zh + 3 * k, BigMat.CAST_L, null, 4);   //hotte
        m.box(cx - fw * 0.7F, 8 * k + 4, zh - 4 * k, cx + fw * 0.7F, 8 * k + 6, zh + 4 * k, BigMat.CAST_L, null, 4);
        m.box(cx - fw, 2, zh + 8 * k, cx + fw, 5, zh + 8 * k + 2, BigMat.PIPE, null, 2);                         //soufflerie
        m.box(cx - 6 * k, 0, za - 6 * k, cx + 6 * k, 4 * k, za + 6 * k, BigMat.CAST, null, 4);                  //chabotte
        m.box(cx - 3 * k, 4 * k, za - 4 * k, cx + 3 * k, 8 * k, za + 4 * k, BigMat.STEEL_D, null, 4);           //enclume
        for (int side = -1; side <= 1; side += 2) {                                                              //jambages
            m.box(cx + side * 6 * k - 1.5F * k, 0, za - 2.5F * k, cx + side * 6 * k + 1.5F * k, hh - 6 * k, za + 2.5F * k, BigMat.CAST, null, 4);
        }
        m.box(cx - 7.5F * k, hh - 6 * k, za - 3 * k, cx + 7.5F * k, hh - 3 * k, za + 3 * k, BigMat.CAST, null, 4);
        m.cyl('y', cx, za, 3.5F * k, hh - 3 * k, Math.min(h, hh + 6 * k), BigMat.CAST_L, 12, null, 4);          //cylindre
    }

    private static void forgeAtLife(BigMesh m, float w, float h, float l, float oz, BigMachines.Anim a) {
        float k = forgeK(w, h, l), cx = w / 2, zh = oz + l * 0.25F, za = oz + l * 0.68F, hh = Math.min(h - 2, 44 * k), s = a.speed;
        float fw = Math.min(w / 2 - 1, 10 * k);
        float fl = 0.8F + 0.2F * (float) Math.sin(a.phase * 0.7F);
        m.glow(Math.round(4 + 11 * s * fl)).color(Lot3.Glow.heat(0.4F + 0.6F * s));
        m.box(cx - fw + 2, 8 * k + 0.2F, zh - 6 * k, cx + fw - 2, 8 * k + 0.6F, zh + 6 * k, BigMat.MOLTEN, null, 4);   //braises
        m.glow(0).color(0xFFFFFFFF);
        float u = (a.phase * 0.15F) % 1F, drop = u < 0.15F ? Lot4.smooth(u / 0.15F) : 1 - Lot4.smooth((u - 0.15F) / 0.85F);
        drop *= Lot4.smooth(s * 3);
        float hit = 8 * k + 2.2F * k, top = hh - 16 * k;
        float yr = top - (top - hit) * drop;                                                                      //coulisseau qui frappe
        m.box(cx - 4 * k, yr, za - 3.5F * k, cx + 4 * k, yr + 8 * k, za + 3.5F * k, BigMat.CAST_L, null, 4);
        m.cyl('y', cx, za, 1.2F * k, yr + 8 * k, hh - 6 * k, BigMat.CHROME, 8, null, 2);
        if (s < 0.05F) {
            return;
        }
        float flat = 1 - 0.3F * ((a.phase * 0.15F) % 12) / 12;                                                   //lopin qui s'aplatit
        m.glow(13).color(Lot3.Glow.heat(0.95F));
        m.box(cx - 2.5F * k / flat, 8 * k, za - 2 * k, cx + 2.5F * k / flat, 8 * k + 2.2F * k * flat, za + 2 * k, BigMat.MOLTEN, null, 2);
        m.box(cx - 1.5F * k, 8 * k + 0.2F, zh - 1.5F * k, cx + 1.5F * k, 8 * k + 1.4F * k, zh + 1.5F * k, BigMat.MOLTEN, null, 2);  //lopin au feu
        m.glow(0).color(0xFFFFFFFF);
        if (drop > 0.9F) {                                                                                        //etincelles a l'impact
            m.glow(15).color(0xFFFFD27A);
            for (int n = 0; n < 10; n++) {
                double an = n * 0.628 + a.phase;
                float d = (2 + 4 * BigMachines.hash(n, 340 + (int) (a.phase * 0.15F))) * k;
                float x = cx + (float) Math.cos(an) * d, z = za + (float) Math.sin(an) * d, y = 8 * k + 1 + BigMachines.hash(n, 341) * 3 * k;
                m.box(x - 0.2F, y, z - 0.2F, x + 0.2F, y + 0.4F, z + 0.2F, BigMat.LED, null, 1);
            }
            m.glow(0).color(0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------------ groupe froid

    static void chiller(BigMesh m, float w, float h, float l) {
        float cx = w / 2, hh = Math.min(h - 1, 40);
        float re = Math.min(w * 0.22F, 9);
        m.cyl('z', cx - w * 0.2F, re + 1, re, 4, l * 0.62F, BigMat.CAST_L, 16, null, 6);                         //evaporateur
        m.cyl('z', cx + w * 0.2F, re + 1, re * 0.8F, 4, l * 0.62F, BigMat.STEEL_D, 16, null, 6);                 //condenseur
        int nc = Math.max(1, (int) (w / 24));
        for (int i = 0; i < nc; i++) {                                                                              //compresseurs a vis
            float x = w * (i + 0.5F) / nc;
            m.box(x - 4, 2 * re + 2, 8, x + 4, 2 * re + 9, 22, BigMat.MOTOR, null, 4);
            m.cyl('z', x, 2 * re + 5.5F, 3, 22, 30, BigMat.CAST_L, 12, null, 4);
            m.cyl('y', x, 26, 1, 2 * re + 9, 2 * re + 12, BigMat.PIPE, 8, null, 2);
        }
        float zc = l * 0.66F;                                                                                       //batterie aerorefrigerante
        m.box(2, 0, zc, w - 2, hh - 6, l - 2, BigMat.GRATE, null, 6);
        m.box(1, hh - 6, zc, w - 1, hh - 5, l - 2, BigMat.CAST, null, 4);
        int nf = Math.max(1, (int) ((w - 4) / 14));
        for (int i = 0; i < nf; i++) {
            float x = 2 + (w - 4) * (i + 0.5F) / nf, z = (zc + l - 2) / 2, rf = Math.min((w - 4) / nf, l - 2 - zc) * 0.4F;
            m.cyl('y', x, z, rf + 0.6F, hh - 5, hh - 2, BigMat.CAST, 16, null, 2, false, 0.6F);                    //virole
            m.spin('y', x, 0, z, 25);
            for (int p = 0; p < 4; p++) {
                m.box(x - 0.8F, hh - 4, z, x + 0.8F, hh - 3.6F, z + rf, BigMat.CAST_L, BigMesh.rot('y', x, 0, z, p * 90), 2);   //pales
            }
            m.fixed();
            m.cyl('y', x, z, 1, hh - 4.4F, hh - 3.2F, BigMat.STEEL_D, 8, null, 1);
        }
        m.cyl('z', cx - w * 0.2F, re * 0.6F + 1, 1, l * 0.62F, zc, BigMat.PIPE, 8, null, 2);                     //tuyauteries
        m.cyl('z', cx + w * 0.2F, 2 * re - 2, 1, l * 0.62F, zc, BigMat.PIPE, 8, null, 2);
    }

    static void chillerLife(BigMesh m, float w, float h, float l, BigMachines.Anim a) {
        float cx = w / 2, re = Math.min(w * 0.22F, 9), s = a.speed;
        float k = Math.min(1, s * 1.2F);                                                                           //givre sur l'evaporateur
        if (k > 0.05F) {
            m.color(argb(0xE8F4FF, Math.round(80 + 140 * k)));
            m.cyl('z', cx - w * 0.2F, re + 1, re + 0.3F, 4, l * 0.62F * (0.4F + 0.6F * k), BigMat.FROTH, 16, null, 6, false, 0.3F);
            m.color(0xFFFFFFFF);
        }
        int nc = Math.max(1, (int) (w / 24));
        for (int i = 0; i < nc; i++) {
            float x = w * (i + 0.5F) / nc, vib = 0.12F * s * (float) Math.sin(a.phase * 5 + i);
            m.glow(s > 0.05F ? 12 : 2).color(s > 0.05F ? 0xFF60E080 : 0xFFF0B040);
            m.box(x - 4.2F + vib, 2 * re + 7, 9, x - 3.9F + vib, 2 * re + 8, 10, BigMat.LED, null, 1);
            m.glow(0).color(0xFFFFFFFF);
        }
        float zc = l * 0.66F;
        if (s > 0.05F) {
            m.color(0x60FFFFFF);
            for (int i = 0; i < 10; i++) {                                                                          //air chaud qui s'echappe
                float ph = (a.phase * 0.03F + i * 0.1F) % 1F, x = 3 + BigMachines.hash(i, 350) * (w - 6), z = zc + 2 + BigMachines.hash(i, 351) * (l - zc - 6);
                float y = Math.min(h - 1, 40) - 2 + ph * 10, d = 0.5F + ph;
                m.box(x - d, y, z - d, x + d, y + d, z + d, BigMat.FROTH, null, 1);
            }
            m.color(0xFFFFFFFF);
        }
    }
}
