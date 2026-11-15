package fr.pioneers.client.industry.big;

/**
 * 0.19.23 : batterie de fours a coke (demande de Guillaume : une construction logique, de l'esprit du haut fourneau). Fours etroits
 * cote a cote le long de l'axe, separes par des piedroits de briques chauffes ; enfourneuse sur rails au sommet, defourneuse au pied
 * d'un cote (son bouclier pousse le gateau de coke incandescent), wagon d'extinction de l'autre ; colonnes montantes et barillet.
 */
final class Lot8 {

    private static final float PITCH = 14, OVEN = 5;

    private Lot8() {
    }

    private record Battery(int n, float z0, float top, float x0, float x1) {
    }

    private static Battery battery(float w, float h, float l) {
        int n = Math.max(2, (int) ((l - 8) / PITCH));
        float z0 = (l - n * PITCH) / 2, top = h - Math.min(18, h * 0.3F);
        return new Battery(n, z0, top, Math.min(14, w * 0.18F), w - Math.min(14, w * 0.18F));
    }

    private static float ovenZ(Battery b, int i) {
        return b.z0 + PITCH * (i + 0.5F);
    }

    static void cokeOven(BigMesh m, float w, float h, float l) {
        Battery b = battery(w, h, l);
        m.box(b.x0 - 1, 0, 1, b.x1 + 1, 3, l - 1, BigMat.CAST, null, 6);                                         //dalle
        for (int i = 0; i <= b.n; i++) {                                                                          //piedroits
            float z = b.z0 + PITCH * i;
            float za = i == 0 ? z : z - (PITCH - OVEN) / 2, zb = i == b.n ? z : z + (PITCH - OVEN) / 2;
            m.box(b.x0, 3, za, b.x1, b.top, zb, i % 2 == 0 ? BigMat.REFR_D : BigMat.REFR_L, null, 6);
        }
        m.box(b.x0, b.top, b.z0, b.x1, b.top + 2, b.z0 + PITCH * b.n, BigMat.REFR_D, null, 6);                  //voute
        for (int i = 0; i < b.n; i++) {
            float zc = ovenZ(b, i);
            for (int k = 0; k < 3; k++) {                                                                        //bouches d'enfournement
                float x = b.x0 + (b.x1 - b.x0) * (k + 1) / 4F;
                m.cyl('y', x, zc, 1.3F, b.top + 2, b.top + 2.6F, BigMat.CAST, 8, null, 1);
            }
            m.cyl('y', b.x1 - 3, zc, 1, b.top + 2, b.top + 9, BigMat.PIPE, 8, null, 2);                         //colonne montante
            m.box(b.x1 - 4, b.top + 8, zc - 1.2F, b.x1 - 2, b.top + 10, zc + 1.2F, BigMat.CAST, null, 2);
            for (float x : new float[]{b.x0 - 0.8F, b.x1}) {                                                      //portes (cote defournement : cf. vie)
                m.box(x, 4, zc - OVEN / 2 - 0.5F, x + 0.8F, b.top - 1, zc + OVEN / 2 + 0.5F, BigMat.CAST, null, 4);
            }
        }
        m.cyl('z', b.x1 - 3, b.top + 11, 2, b.z0 - 1, b.z0 + PITCH * b.n + 1, BigMat.PIPE, 12, null, 4);        //barillet
        for (float x : new float[]{b.x0 + 1, b.x1 - 6}) {                                                          //rails de l'enfourneuse
            m.box(x, b.top + 2, 1, x + 1, b.top + 2.8F, l - 1, BigMat.ROLL, null, 2);
        }
        for (float x : new float[]{1, b.x0 - 4}) {                                                                 //rails de la defourneuse
            m.box(x, 3, 1, x + 1, 3.8F, l - 1, BigMat.ROLL, null, 2);
        }
        m.box(b.x1 + 2, 3, 1, b.x1 + 3, 3.8F, l - 1, BigMat.ROLL, null, 2);                                       //voie du wagon
        m.box(w - 3, 3, 1, w - 2, 3.8F, l - 1, BigMat.ROLL, null, 2);
    }

    static void cokeOvenLife(BigMesh m, float w, float h, float l, BigMachines.Anim a) {
        Battery b = battery(w, h, l);
        float s = a.speed;
        //lueur des carneaux entre les fours et flammes au sommet des colonnes
        for (int i = 0; i < b.n; i++) {
            float zc = ovenZ(b, i);
            m.glow(Math.round(3 + 10 * s)).color(Lot3.Glow.heat(0.35F + 0.6F * s));
            m.box(b.x0 - 0.9F, 4.5F, zc - OVEN / 2 - 0.7F, b.x0 - 0.8F, b.top - 1.5F, zc - OVEN / 2 - 0.5F, BigMat.GLOWROD, null, 2);
            m.box(b.x1 + 0.8F, 4.5F, zc + OVEN / 2 + 0.5F, b.x1 + 0.9F, b.top - 1.5F, zc + OVEN / 2 + 0.7F, BigMat.GLOWROD, null, 2);
            m.glow(0).color(0xFFFFFFFF);
        }
        //cycle : la defourneuse et l'enfourneuse vont au four suivant, porte levee, poussee, enfournement
        float cyc = 260, t = a.phase % (cyc * b.n);
        int cur = (int) (t / cyc);
        float u = (t - cur * cyc) / cyc;
        int prev = (cur + b.n - 1) % b.n;
        float zPrev = ovenZ(b, prev), zCur = ovenZ(b, cur);
        float zm = u < 0.15F ? zPrev + (zCur - zPrev) * Lot4.smooth(u / 0.15F) : zCur;                            //deplacement des machines
        float door = u < 0.15F ? 0 : u < 0.25F ? Lot4.smooth((u - 0.15F) / 0.1F) : u < 0.75F ? 1 : 1 - Lot4.smooth((u - 0.75F) / 0.1F);
        door *= Lot4.smooth(s * 3);
        float push = u < 0.3F ? 0 : u < 0.6F ? Lot4.smooth((u - 0.3F) / 0.3F) : u < 0.7F ? 1 - Lot4.smooth((u - 0.6F) / 0.1F) : 0;
        push *= Lot4.smooth(s * 3);
        //defourneuse (cote x0)
        float px0 = 1, px1 = b.x0 - 3;
        m.box(px0, 3.8F, zm - 5, px1, 18, zm + 5, BigMat.PAINT_O, null, 4);
        m.box(px0 + 1, 18, zm - 4, px1 - 1, 22, zm + 4, BigMat.CAST_L, null, 4);
        float ramX = px1 + (b.x1 - px1 - 2) * push;
        m.box(px1, 9, zm - 0.6F, ramX, 10.4F, zm + 0.6F, BigMat.CHROME, null, 2);                                //tige du bouclier
        m.box(ramX - 0.8F, 5, zm - OVEN / 2 + 0.4F, ramX, b.top - 3, zm + OVEN / 2 - 0.4F, BigMat.STEEL_D, null, 2);   //bouclier
        //portes du four en cours, levees
        for (float x : new float[]{b.x0 - 1.7F, b.x1 + 0.9F}) {
            m.box(x, 4 + door * (b.top - 4), zCur - OVEN / 2 - 0.5F, x + 0.8F, b.top - 1 + door * 3, zCur + OVEN / 2 + 0.5F, BigMat.CAST, null, 4);
        }
        //gateau de coke qui sort et tombe dans le wagon d'extinction
        if (s > 0.05F && u > 0.25F && u < 0.75F) {
            float cx0 = b.x0 + (b.x1 - b.x0) * push * 0.9F, cx1 = Math.min(w - 2, b.x1 + (w - 2 - b.x1) * push);
            m.glow(14).color(Lot3.Glow.heat(0.95F));
            m.box(Math.max(b.x0, cx0), 4, zCur - OVEN / 2 + 0.3F, Math.min(b.x1, cx0 + (b.x1 - b.x0)), b.top - 2.5F, zCur + OVEN / 2 - 0.3F,
                  BigMat.MOLTEN, null, 4);
            if (cx1 > b.x1 + 1) {
                m.box(b.x1 + 1, 7, zCur - OVEN / 2 + 0.3F, Math.min(cx1, b.x1 + 5), b.top - 3, zCur + OVEN / 2 - 0.3F, BigMat.MOLTEN, null, 2);
            }
            m.glow(0).color(0xFFFFFFFF);
        }
        //guide de coke (cage) devant le four et wagon d'extinction
        float qz = zm, wx0 = b.x1 + 1.5F, wx1 = w - 1.5F;
        if (wx1 - wx0 > 3) {
            for (float z : new float[]{qz - OVEN / 2 - 1.2F, qz + OVEN / 2 + 0.2F}) {
                m.box(b.x1 + 0.9F, 9, z, wx0 + 3, b.top - 1, z + 1, BigMat.CAST_L, null, 2);
            }
            m.box(b.x1 + 0.9F, b.top - 1, qz - OVEN / 2 - 1.2F, wx0 + 3, b.top, qz + OVEN / 2 + 1.2F, BigMat.CAST_L, null, 2);
            m.box(wx0, 3.8F, qz - 7, wx1, 6, qz + 7, BigMat.CAST, null, 4);                                       //caisse du wagon
            for (float z : new float[]{qz - 7, qz + 6.4F}) {
                m.box(wx0, 6, z, wx1, 11, z + 0.6F, BigMat.CAST_L, null, 2);
            }
            m.box(wx1 - 0.6F, 6, qz - 7, wx1, 11, qz + 7, BigMat.CAST_L, null, 2);
            if (s > 0.05F && u > 0.4F) {                                                                         //coke qui s'amoncelle
                float fill = Lot4.clamp01((u - 0.4F) / 0.35F), cool = Lot4.clamp01((u - 0.75F) / 0.25F);
                m.glow(Math.round(13 * (1 - cool * 0.7F))).color(Lot3.Glow.heat(0.95F - 0.5F * cool));
                for (int k = 0; k < 10; k++) {
                    float x = wx0 + 1 + (k % 5) * (wx1 - wx0 - 2) / 5F, z = qz - 5.5F + (k / 5) * 5.5F, hh = (1.5F + 2.5F * BigMachines.hash(k, 410)) * fill;
                    m.box(x, 6, z, x + (wx1 - wx0 - 2) / 5F, 6 + hh, z + 5.5F, BigMat.MOLTEN, null, 2);
                }
                m.glow(0).color(0xFFFFFFFF);
            }
        }
        //regards de la voute : la chaleur des carneaux
        for (int i = 0; i <= b.n; i += 1) {
            float z = b.z0 + PITCH * i;
            for (int k = 0; k < 4; k++) {
                float x = b.x0 + (b.x1 - b.x0) * (k + 0.5F) / 4;
                m.glow(Math.round(2 + 10 * s)).color(Lot3.Glow.heat(0.3F + 0.6F * s));
                m.box(x - 0.6F, b.top + 2, z - 0.6F, x + 0.6F, b.top + 2.1F, z + 0.6F, BigMat.GLOWBED, null, 1);
            }
        }
        m.glow(0).color(0xFFFFFFFF);
        //enfourneuse sur la voute : descend ses trompes quand la porte s'est refermee
        float load = u > 0.8F && u < 0.95F ? 1 : 0;
        m.box(b.x0 + 0.5F, b.top + 2.8F, zm - 4, b.x1 - 5.5F, b.top + 9, zm + 4, BigMat.PAINT_O, null, 4);
        for (int k = 0; k < 3; k++) {
            float x = b.x0 + (b.x1 - b.x0) * (k + 1) / 4F;
            m.cyl('y', x, zm, 1.1F, b.top + 2.6F - load * 0.4F, b.top + 2.8F, BigMat.CAST_L, 8, null, 1);
            if (load > 0 && s > 0.05F) {
                m.color(0xFF2A2A2E);
                for (int c = 0; c < 4; c++) {
                    float ph = (a.phase * 0.2F + c * 0.25F) % 1F;
                    m.box(x - 0.4F, b.top + 2 - ph * 3, zm - 0.4F, x + 0.4F, b.top + 2.6F - ph * 3, zm + 0.4F, BigMat.COKE, null, 1);
                }
                m.color(0xFFFFFFFF);
            }
        }
        //fumee et flammes sur les colonnes montantes
        if (s > 0.05F) {
            for (int i = 0; i < b.n; i++) {
                float zc = ovenZ(b, i);
                if (BigMachines.hash(i, 400 + (int) (a.phase / 40)) > 0.35F * s) {
                    continue;
                }
                m.glow(15).color(0xFFFFB060);
                float f = 0.8F + 0.6F * BigMachines.hash(i, (int) a.phase);
                m.box(b.x1 - 3.6F, b.top + 10, zc - 0.6F, b.x1 - 2.4F, b.top + 10 + 2 * f, zc + 0.6F, BigMat.FLAME, null, 1);
                m.glow(0).color(0xFFFFFFFF);
            }
        }
    }
}
