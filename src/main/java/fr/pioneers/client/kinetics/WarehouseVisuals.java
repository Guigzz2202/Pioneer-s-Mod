package fr.pioneers.client.kinetics;

import fr.pioneers.kinetics.rope.LinkType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 1.0.4-beta (lot 1) : DESSIN de l'entrepot (meme code au jeu et au banc {@code tools/kinetics}) : cordes des lignes (dessin de la corde du
 * mod, {@link MachineVisuals#rope}, a la hauteur de la gorge), les COLIS (petite caisse de bois traite cerclee d'acier, pendue a la corde par
 * une griffe et un etrier qui contourne la joue de la roue) et les battants de la trappe des caisses. Materiaux
 * {@code textures/block/mechanics/*} (dont {@code warehouse_*} : {@code tools/gc/warehouse/warehouse.py}). 1.0.4-beta (lot 27) : plus de
 * poulie d'entrepot ni de poteau (roue, potence, mat, broche retires) : la station est une petite poulie de base.
 */
public final class WarehouseVisuals {

    private WarehouseVisuals() {
    }

    /**
     * Rayon de la gorge d'une station (px : la petite poulie, 1.0.4-beta lot 27) ; {@code ROPE_Y} : hauteur de la corde dans le REPERE DU
     * COLIS (px) : le colis est dessine pendu a une corde a cette hauteur, puis place sur la vraie corde.
     */
    public static final float R = 4F, ROPE_Y = 12.5F;
    /** Colis (repere local : centre en (8, 8), +x vers l'exterieur de la roue, +z en avant) : demi-largeur, bas, haut (px). */
    public static final float PARCEL_HALF = 2.7F, PARCEL_Y0 = 3.6F, PARCEL_Y1 = 8.6F;

    /**
     * 1.0.4-beta (lot 2) : la corde enroulee dans la gorge d'une poulie de base (a dessiner dans le repere tournant de la roue : elle tourne
     * avec la jante, sans glisser) : un anneau de corde au rayon primitif {@code r}, a la hauteur {@code y} (px).
     */
    public static void groove(KDraw d, float r, float y, String tex) {
        int seg = Math.max(12, Math.round(r * 2.5F));
        float w = tex.equals("leather") ? 1.6F : 1.2F;
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
            //Un peu au-dela de chaque sommet : pas de fente entre deux morceaux
            double e = (a1 - a0) * 0.12;
            d.beam(8 + (float) (Math.cos(a0 - e) * r), y, 8 + (float) (Math.sin(a0 - e) * r), 8 + (float) (Math.cos(a1 + e) * r), y,
                  8 + (float) (Math.sin(a1 + e) * r), w, tex);
        }
    }

    /** Fleche (px) de la corde au point {@code t} (0..1) d'un troncon de {@code blocks} blocs : celle du dessin de la corde du mod. */
    public static float sag(double blocks, double t, LinkType type) {
        return fr.pioneers.warehouse.WarehouseLines.sag(blocks, t, type);
    }

    /**
     * Place d'un colis (meme calcul au jeu, J:client/warehouse/CounterRenderer, et au banc, tools/kinetics/WarehouseFrames) : point de la corde
     * {@code geo} a l'abscisse {@code s}, lacet (repere local : +x vers l'exterieur de la roue, +z = -spin x sens de marche), balancement
     * (plus ample avec la vitesse de la corde ; nul pose), penche vers l'exterieur dans les tours de roue. Renvoie {x, z, lacet, balancement,
     * penche} (blocs, degres).
     */
    public static double[] place(fr.pioneers.warehouse.ParcelRoute geo, double s, int spin, double speed, float time, int id, boolean travel,
          boolean resting, double[] pt) {
        geo.point(s, pt);
        double fx = -spin * pt[2], fz = -spin * pt[3];
        float yaw = (float) Math.toDegrees(Math.atan2(fx, fz));
        double v = travel ? speed : 0;
        float amp = (float) (1.2 + 5.0 * Math.min(1, v * 20 / 1.5));
        float swing = resting ? 0 : amp * (float) Math.sin(time * 0.23 + id * 1.7) * (travel ? 1 : 0.35F);
        float lean = travel && geo.onArc(s) ? 6F * (float) Math.min(1, v * 20 / 1.2) : 0;
        return new double[]{pt[0], pt[1], yaw, swing, lean};
    }

    /**
     * 1.0.4-beta (lot 2) : corde d'une ligne d'entrepot entre deux roues d'axe vertical : A au centre du bloc (rayon primitif {@code ra} px,
     * gorge a {@code ya} px), B decalee de (ox, oy, oz) blocs ({@code rb}, {@code yb}) ; brins exterieurs (tangentes de {@link
     * MachineVisuals#rope}, meme sens de defilement : {@code scroll} = defilement de la gorge de A), chacun en ligne droite d'une gorge a
     * l'autre, avec la fleche de la corde.
     */
    public static void line(KDraw d, float ox, float oy, float oz, float ra, float rb, float ya, float yb, LinkType t, float scroll) {
        float du = ox * 16, dv = oz * 16;
        double dist = Math.hypot(du, dv);
        if (dist < 1) {
            return;
        }
        String tex = t.texture;
        double base = Math.atan2(dv, du);
        float old = d.scroll;
        float w = tex.equals("leather") ? 1.6F : 1.2F;
        float yB = oy * 16 + yb;
        for (int side = -1; side <= 1; side += 2) {
            double phi = Math.asin(Math.max(-1, Math.min(1, (ra - rb) / dist)));
            double na = base + side * (Math.PI / 2 - phi);
            float au = (float) (Math.cos(na) * ra), av = (float) (Math.sin(na) * ra);
            float bu = du + (float) (Math.cos(na) * rb), bv = dv + (float) (Math.sin(na) * rb);
            int n = 8;
            float[] prev = null;
            float acc = 0;
            float run = MachineVisuals.strandSign(Direction.Axis.Y, side) * scroll;
            for (int i = 0; i <= n; i++) {
                float tt = i / (float) n;
                float[] p = {au + (bu - au) * tt + 8, ya + (yB - ya) * tt - sag(dist / 16, tt, t), av + (bv - av) * tt + 8};
                if (prev != null) {
                    d.scroll = acc - run;
                    d.beam(prev[0], prev[1], prev[2], p[0], p[1], p[2], w, tex);
                    acc += (float) Math.sqrt((p[0] - prev[0]) * (p[0] - prev[0]) + (p[1] - prev[1]) * (p[1] - prev[1]) + (p[2] - prev[2]) * (p[2] - prev[2]));
                }
                prev = p;
            }
        }
        d.scroll = old;
    }

    /**
     * Un colis dans son repere local (centre (8, 8), +x vers l'exterieur de la roue, +z en avant), griffe sur la corde a {@code ROPE_Y} :
     * {@code drop} = descente (px) de la caisse sous l'etrier (station), {@code lid} = ouverture du couvercle (0..1), {@code full} : charge
     * (sangles tendues, plomb rouge) ou vide.
     */
    public static void parcel(KDraw d, float drop, float lid, boolean full) {
        //1.0.5-beta (lot 22, retour 20 de Guillaume : « la texture de la caisse qui descend sous la poulie est toujours buggee : elle defile en
        //meme temps que la caisse descend ») : KDraw prend les UV dans les COORDONNEES du repere (uv = position mod 16, carreaux coupes aux
        //multiples de 16) : la caisse dessinee en PARCEL_Y0 - drop .. PARCEL_Y1 - drop echantillonnait une autre bande de sa texture a chaque
        //image de la descente. La caisse est maintenant dessinee a SA place (y0, y1 fixes) dans une pose translatee de -drop : texture collee
        //a la caisse ; seul le filin (qui se deroule) defile. Banc tools/warehouse_bench/parcel_uv.sh (ParcelUvBench).
        float y0 = PARCEL_Y0, y1 = PARCEL_Y1, h = PARCEL_HALF;
        //Griffe sur la corde (dans la gorge, entre les joues), etrier qui sort au-dela de la joue puis revient au-dessus du colis (fixe)
        float hy = PARCEL_Y1 + 0.8F, armX = 8 + 1.55F + 0.7F;
        d.box(7.45F, ROPE_Y - 0.7F, 7.35F, 8.55F, ROPE_Y + 0.7F, 8.65F, "steel", "steel");
        d.box(8.55F, ROPE_Y - 0.25F, 7.7F, armX + 0.5F, ROPE_Y + 0.25F, 8.3F, "iron_bright", "iron_bright");
        d.box(armX, hy, 7.7F, armX + 0.5F, ROPE_Y - 0.25F, 8.3F, "steel", "steel");
        d.box(8 + REEL_W, hy, 7.7F, armX, hy + 0.5F, 8.3F, "steel", "steel");
        //1.0.4-beta (lot 2) : TREUIL du chariot : le bout de l'etrier est un tambour (axe x) ou s'enroule le filin ; il tourne quand le colis
        //descend vers une station plus basse que la roue (ou remonte), le filin defile en se deroulant
        d.push().rotate(Direction.Axis.X, (float) Math.toDegrees(drop / REEL_R), 8, hy + 0.25F, 8);
        d.box(8 - REEL_W, hy + 0.25F - REEL_R, 8 - REEL_R, 8 + REEL_W, hy + 0.25F + REEL_R, 8 + REEL_R, "rope", "steel");
        d.pop();
        //Suspente : anneau court ; a la station, le colis descend au bout du filin
        if (drop > 0.05F) {
            float old = d.scroll;
            d.scroll = -drop;
            d.beam(8, y1 - drop, 8, 8, hy + 0.25F - REEL_R + 0.05F, 8, 0.4F, "rope");
            d.scroll = old;
        } else {
            d.box(7.65F, y1, 7.65F, 8.35F, hy + 0.25F - REEL_R + 0.05F, 8.35F, "iron_bright", "iron_bright");
        }
        //La caisse : planches (texture de colis), cerclages d'acier aux deux tiers, coins ; lot 22 : a sa place, pose descendue de drop
        d.push().translate(0, -drop, 0);
        d.box(8 - h, y0, 8 - h, 8 + h, y1 - 0.8F, 8 + h, "warehouse_parcel", "warehouse_parcel_top");
        for (float z : new float[]{8 - h * 0.55F, 8 + h * 0.55F}) {
            d.box(8 - h - 0.12F, y0 - 0.12F, z - 0.35F, 8 + h + 0.12F, y1 - 0.8F + 0.05F, z + 0.35F, "steel", "steel");
        }
        for (float x : new float[]{8 - h, 8 + h}) {
            for (float z : new float[]{8 - h, 8 + h}) {
                d.box(x - 0.25F, y0 - 0.05F, z - 0.25F, x + 0.25F, y1 - 0.75F, z + 0.25F, "steel_plate", "steel_plate");
            }
        }
        //Couvercle sur charniere (arriere, -z) : s'ouvre au comptoir
        d.push().rotate(Direction.Axis.X, -lid * 105F, 8, y1 - 0.8F, 8 - h);
        d.box(8 - h - 0.1F, y1 - 0.8F, 8 - h - 0.1F, 8 + h + 0.1F, y1, 8 + h + 0.1F, "warehouse_parcel", "warehouse_parcel_top");
        d.box(8 - h - 0.2F, y1 - 0.85F, 8 + h - 0.5F, 8 + h + 0.2F, y1 + 0.05F, 8 + h + 0.2F, "steel", "steel");
        d.pop();
        if (full && lid < 0.05F) {
            //Plomb de fermeture (charge)
            d.box(7.6F, y1 - 1.6F, 8 + h + 0.05F, 8.4F, y1 - 0.9F, 8 + h + 0.4F, "warehouse_seal", "warehouse_seal");
        }
        d.pop();
    }

    /** Battants de la trappe (repere de la case de trappe d'une caisse selon Z, ouverture du cote +x) ; {@code open} 0..1. */
    public static void hatchDoors(KDraw d, float open) {
        float deg = open * 92F;
        d.push().rotate(Direction.Axis.X, deg, 8, 15.4F, HATCH_Z0);
        d.box(HATCH_X0, 15.0F, HATCH_Z0, HATCH_X1, 15.8F, (HATCH_Z0 + HATCH_Z1) / 2 - 0.05F, "warehouse_hatch", "warehouse_hatch");
        d.pop();
        d.push().rotate(Direction.Axis.X, -deg, 8, 15.4F, HATCH_Z1);
        d.box(HATCH_X0, 15.0F, (HATCH_Z0 + HATCH_Z1) / 2 + 0.05F, HATCH_X1, 15.8F, HATCH_Z1, "warehouse_hatch", "warehouse_hatch");
        d.pop();
    }

    /**
     * Treuil du chariot (px) : demi-cote du tambour carre (il tient dans l'epaisseur de l'etrier, tourne : 0,35 px de plus en diagonale,
     * toujours sous la joue basse d'une poulie de base) et demi-longueur.
     */
    public static final float REEL_R = 0.25F, REEL_W = 0.8F;

    /**
     * Ouverture de la trappe (case de trappe, caisse selon Z) : la ou les colis descendent (angle de depose +x). 1.0.4-beta (lot 27) : centree
     * sur la jante de la petite poulie (8 + 4 px ; avant 8 + 5,25 avec la poulie d'entrepot).
     */
    public static final float HATCH_X0 = 9.2F, HATCH_X1 = 14.8F, HATCH_Z0 = 4.7F, HATCH_Z1 = 11.3F;
}
