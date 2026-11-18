package fr.pioneers.tech.industry.client.big;

import fr.pioneers.logistics.naval.client.ShipModels;
import fr.pioneers.logistics.rail.client.RailMesh;
import fr.pioneers.logistics.naval.ShipKind;
import org.joml.Matrix4f;

/**
 * 0.20.11 : chantier naval (cale seche) : l'interieur est le bassin ; un navire en construction repose sur ses tins (les vrais maillages
 * des navires, mis a l'echelle du bassin), des echafaudages le longent ; dessus, un portique roule le long de la cale, son crochet monte
 * et descend, et des eclairs de soudure crepitent sur la coque pendant le travail.
 */
final class Lot9 {

    private Lot9() {
    }

    /** Navire montre : le plus grand qui tient dans le bassin (MKIV si la cale est assez longue). */
    private static int tier(float w, float l) {
        for (int t = 3; t >= 0; t--) {
            if (ShipKind.length(t) * 16 <= l + 8 && ShipKind.beam(t) * 16 <= w + 4) {
                return t;
            }
        }
        return 0;
    }

    private static float scale(float w, float l, int t) {
        return Math.min(1, Math.min((l - 6) / (ShipKind.length(t) * 16F), (w - 10) / (ShipKind.beam(t) * 16F)));
    }

    static void drydock(BigMesh m, float w, float h, float l) {
        int t = tier(w, l);
        float k = scale(w, l, t), cx = w / 2, cz = l / 2;
        //Radier : beton sombre, rigoles, tins de quille tous les 2 blocs
        m.box(0, 0, 0, w, 0.6F, l, BigMat.STEEL_D, null, 16);
        m.box(cx - 0.4F, 0.6F, 2, cx + 0.4F, 0.8F, l - 2, BigMat.RUBBER, null, 4);
        float keel = 5;
        for (float z = 8; z < l - 6; z += 32) {
            m.box(cx - 3, 0.6F, z, cx + 3, keel, z + 4, BigMat.CAST, null, 4);
            m.box(cx - 3.4F, keel - 0.6F, z - 0.4F, cx + 3.4F, keel, z + 4.4F, BigMat.WOOD1, null, 4);
        }
        //Navire : un cargo (coque, superstructure, cales), ligne de flottaison au-dessus des tins. 1.0.4-beta (lot 25, « rien ne doit
        //depasser ») : aussi a l'echelle de la HAUTEUR de la cale (le sommet de la superstructure sous le pont roulant)
        BigMesh ship = ShipModels.build(ShipKind.CARGO, t, 0.35F, 0).mesh;
        float shipTop = 0;
        for (BigMesh.Quad q : ship.quads) {
            for (int i = 0; i < 4; i++) {
                shipTop = Math.max(shipTop, q.pos[i * 3 + 1]);
            }
        }
        float room = h - CRANE_CLEAR - keel, need = ShipKind.draft(t) + shipTop;
        if (need > 0 && k * need > room) {
            k = room / need;
        }
        float draft = ShipKind.draft(t) * k;
        Matrix4f xf = new Matrix4f().translate(cx, keel + draft, cz).scale(k);
        RailMesh.append(m, ship, xf);
        //Echafaudages des deux cotes : montants, platelage tous les 12 px
        float half = ShipKind.beam(t) * 8F * k;
        for (float side : new float[]{-1, 1}) {
            float x = cx + side * (half + 3);
            for (float z = 6; z < l - 4; z += 24) {
                m.box(x - 0.4F, 0.6F, z - 0.4F, x + 0.4F, h - 2, z + 0.4F, BigMat.CHROME, null, 4);
            }
            for (float y = 10; y < h - 2; y += 12) {
                m.box(x - 1.6F, y, 6, x + 1.6F, y + 0.5F, l - 6, BigMat.WOOD2, null, 8);
                m.box(x - 0.2F + side * 1.6F, y + 0.5F, 6, x + 0.2F + side * 1.6F, y + 3, l - 6, BigMat.PAINT_O, null, 8);
            }
        }
        //Chemins de roulement du pont roulant, en haut des deux rives (1.0.4-beta, lot 25 : sous le haut de la cale)
        for (float x : new float[]{1.5F, w - 1.5F}) {
            m.box(x - 1.5F, h - 7, 0, x + 1.5F, h - 6, l, BigMat.STEEL_D, null, 8);
            m.box(x - 0.6F, h - 6, 0, x + 0.6F, h - 5.4F, l, BigMat.CHROME, null, 8);
        }
    }

    /** 1.0.4-beta (lot 25) : place laissee au pont roulant sous le haut de la cale (px) : le navire reste en dessous. */
    private static final float CRANE_CLEAR = 13;

    /** Portique qui va et vient, crochet, eclairs de soudure (seulement pendant le travail). */
    static void drydockLife(BigMesh m, float w, float h, float l, BigMachines.Anim a) {
        float travel = l - 24;
        float u = (float) (0.5 - 0.5 * Math.cos(a.phase * 0.004));
        float z = 12 + travel * u;
        //1.0.4-beta (lot 25, « rien ne doit depasser ») : pont roulant DANS la cale (il roulait sur des palees au-dessus des rives) : sommiers
        //sur les chemins de roulement, poutre sous le haut de la cale
        float top = h - 5.4F;
        for (float x : new float[]{1.5F, w - 1.5F}) {
            m.box(x - 2.4F, top - 1.4F, z - 4, x + 2.4F, top, z + 4, BigMat.STEEL_D, null, 4);
        }
        m.box(0.3F, top, z - 3, w - 0.3F, top + 4, z + 3, BigMat.PAINT_O, null, 8);
        m.box(0.3F, top + 4, z - 0.6F, w - 0.3F, top + 4.6F, z + 0.6F, BigMat.STEEL_D, null, 8);
        //Chariot et crochet (monte et descend)
        float tx = w / 2 + (float) Math.sin(a.phase * 0.011) * (w / 2 - 10);
        m.box(tx - 3, top - 2, z - 3.4F, tx + 3, top, z + 3.4F, BigMat.MOTOR, null, 4);
        float drop = 3 + 9 * (float) (0.5 + 0.5 * Math.sin(a.phase * 0.02));
        m.box(tx - 0.25F, top - 2 - drop, z - 0.25F, tx + 0.25F, top - 2, z + 0.25F, BigMat.CHROME, null, 1);
        m.box(tx - 1.2F, top - 4 - drop, z - 1.2F, tx + 1.2F, top - 2 - drop, z + 1.2F, BigMat.STEEL_D, null, 2);
        //Eclairs de soudure le long des deux flancs
        if (a.speed > 0.05F) {
            for (int i = 0; i < 6; i++) {
                if (BigMachines.hash(i, (int) (a.phase / 3)) > a.speed * 0.8F) {
                    continue;
                }
                float sz = 10 + BigMachines.hash(i, 7) * (l - 20), side = i % 2 == 0 ? 1 : -1;
                float sx = w / 2 + side * (w / 2 - 14 + BigMachines.hash(i, 9) * 4), sy = 8 + BigMachines.hash(i, 11) * 10;
                float d = 0.7F + BigMachines.hash(i, (int) a.phase) * 0.6F;
                m.box(sx - d, sy - d, sz - d, sx + d, sy + d, sz + d, BigMat.FLAME_C, null, 1);
            }
        }
    }
}
