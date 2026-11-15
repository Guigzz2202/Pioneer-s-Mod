package fr.pioneers.client.rail;

import fr.pioneers.client.industry.big.BigMat;
import net.minecraft.core.Direction;

/**
 * 0.20.0 : maillages des blocs poses le long de la voie, d'apres les apercus valides ({@code scratchpad/rail/infra.py}). Repere canonique :
 * les trains commandes roulent vers le nord (-z), la voie est a l'ouest (-x) ; le modele est ensuite tourne selon {@code facing}.
 */
public final class InfraModels {

    /**
     * Hauteur du fil de contact de la catenaire (px, repere du bloc du poteau, la voie au meme niveau) : le fil occupe WIRE_Y .. WIRE_Y +
     * 0,3 au-dessus de l'axe de la voie (8 px au-dela du bord du bloc du poteau) ; l'archet du pantographe leve touche WIRE_Y.
     */
    public static final float WIRE_Y = 23.0F;

    private InfraModels() {
    }

    /** Tourne le repere canonique (trains vers le nord) vers {@code facing}. */
    public static void orient(RailMesh m, Direction facing) {
        int k = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        m.rotate('y', 8, 0, 8, -90 * k);
    }

    private static void basePlate(RailMesh m) {
        pad(m, 4.5F, 4.5F, 11.5F, 11.5F, BigMat.R_TIER2);
    }

    /**
     * 0.20.8 : socle commun des objets de gare (style du mod : socle sombre a liseré d'acier, bande de tier, boulons), pose au sol de
     * 0 a {@link #PAD} px.
     */
    static void pad(RailMesh m, float x0, float z0, float x1, float z1, BigMat tier) {
        m.box(x0, 0, z0, x1, 0.9F, z1, BigMat.R_FRAME);
        m.box(x0 + 0.4F, 0.9F, z0 + 0.4F, x1 - 0.4F, PAD, z1 - 0.4F, BigMat.R_DARK);
        m.box(x0 - 0.05F, 0.35F, z0 - 0.05F, x1 + 0.05F, 0.65F, z1 + 0.05F, tier);
        for (float x : new float[]{x0 + 0.9F, x1 - 1.5F}) {
            for (float z : new float[]{z0 + 0.9F, z1 - 1.5F}) {
                m.box(x, PAD, z, x + 0.6F, PAD + 0.25F, z + 0.6F, BigMat.R_STEEL_L);
            }
        }
    }

    /** Hauteur du socle commun (px). */
    static final float PAD = 1.4F;

    private static BigMat tier(int t) {
        return switch (t) {
            case 1 -> BigMat.R_TIER2;
            case 2 -> BigMat.R_TIER3;
            case 3 -> BigMat.R_TIER4;
            default -> BigMat.R_TIER1;
        };
    }

    /** Semaphore (signal de canton) : bras a l'horizontale = arret, leve a 45 degres = voie libre. Aspect : 0 vert, 1 orange, 2 rouge. */
    public static RailMesh semaphore(Direction facing, int aspect) {
        RailMesh m = new RailMesh();
        orient(m, facing);
        basePlate(m);
        m.box(7.4F, 1.4F, 7.4F, 8.6F, 22.0F, 8.6F, BigMat.R_STEEL);
        m.box(7.0F, 22.0F, 7.0F, 9.0F, 22.6F, 9.0F, BigMat.R_DARK);
        for (int y = 4; y < 20; y += 3) {
            m.box(8.6F, y, 7.6F, 9.4F, y + 0.3F, 8.4F, BigMat.R_STEEL);
        }
        boolean stop = aspect == 2;
        float ang = stop ? 0 : aspect == 1 ? 22.5F : 45;
        m.translate(8.6F, 19.0F, 8.95F);
        m.rotate('z', 0, 0, 0, ang);
        m.box(0, -0.8F, -0.3F, 7.5F, 0.8F, 0.3F, BigMat.R_RED);
        m.box(5.0F, -0.8F, -0.32F, 6.0F, 0.8F, 0.32F, BigMat.R_WHITE);
        m.box(-1.6F, -1.0F, -0.4F, 0.4F, 1.0F, 0.4F, BigMat.R_DARK);
        m.pop();
        m.pop();
        BigMat lens = stop ? BigMat.R_LAMP_R : aspect == 1 ? BigMat.R_LAMP_O : BigMat.R_LAMP_G;
        m.box(8.4F, 15.4F, 8.7F, 9.6F, 16.6F, 9.6F, lens);
        m.box(8.2F, 15.2F, 8.6F, 9.8F, 16.8F, 8.7F, BigMat.R_DARK);
        m.pop();
        return m;
    }

    /** Feu sur mat (signal d'entree) : rouge, bleu (sortie partielle), vert. */
    public static RailMesh lightSignal(Direction facing, int aspect) {
        RailMesh m = new RailMesh();
        orient(m, facing);
        basePlate(m);
        m.cyl('y', 8, 8, 0.7F, 1.4F, 14.0F, BigMat.R_STEEL, 10);
        m.box(6.2F, 14.0F, 7.0F, 9.8F, 22.4F, 9.4F, BigMat.R_BLACK);
        m.box(6.0F, 13.6F, 6.8F, 10.0F, 14.0F, 9.6F, BigMat.R_TIER2);
        int lit = aspect == 2 ? 0 : aspect == 1 || aspect == 3 ? 1 : 2;
        BigMat[] on = {BigMat.R_LAMP_R, BigMat.R_LAMP_B, BigMat.R_LAMP_G};
        float[] ys = {20.4F, 18.0F, 15.6F};
        for (int i = 0; i < 3; i++) {
            m.cyl('z', 8, ys[i], 0.9F, 9.4F, 9.6F, i == lit ? on[i] : BigMat.R_LAMP_OFF, 10);
            m.box(6.6F, ys[i] + 1.0F, 9.4F, 9.4F, ys[i] + 1.3F, 10.6F, BigMat.R_BLACK);
        }
        m.box(7.0F, 10.0F, 8.7F, 9.0F, 12.0F, 8.9F, BigMat.R_WHITE);
        m.box(7.4F, 10.4F, 8.85F, 8.6F, 11.6F, 8.95F, BigMat.R_TIER2);
        m.pop();
        return m;
    }

    /** Panneau de gare : ecran (le nom est ecrit par le rendu du bloc), horloge, liseré de tier. */
    public static RailMesh station(Direction facing) {
        RailMesh m = new RailMesh();
        orient(m, facing);
        basePlate(m);
        for (float x : new float[]{5.4F, 10.6F}) {
            m.box(x - 0.4F, 1.4F, 7.6F, x + 0.4F, 18.0F, 8.4F, BigMat.R_STEEL);
        }
        m.box(2.0F, 13.0F, 7.4F, 14.0F, 20.0F, 8.6F, BigMat.R_DARK);
        m.box(2.4F, 13.4F, 8.55F, 13.6F, 19.6F, 8.7F, BigMat.R_SCREEN);
        m.box(2.4F, 13.4F, 7.3F, 13.6F, 19.6F, 7.45F, BigMat.R_SCREEN);
        m.box(3.0F, 14.2F, 8.65F, 11.0F, 14.8F, 8.8F, BigMat.R_LAMP);
        m.box(3.0F, 14.2F, 7.2F, 11.0F, 14.8F, 7.35F, BigMat.R_LAMP);
        m.box(1.6F, 20.0F, 7.2F, 14.4F, 20.6F, 8.8F, BigMat.R_TIER2);
        m.cyl('y', 8, 8, 1.6F, 20.6F, 22.6F, BigMat.R_WHITE, 14);
        m.cyl('z', 8, 21.6F, 1.2F, 8.8F, 9.0F, BigMat.R_DARK, 12);
        m.cyl('z', 8, 21.6F, 1.2F, 7.0F, 7.2F, BigMat.R_DARK, 12);
        m.pop();
        return m;
    }

    /**
     * Quai a objets : coque de machine, montants a chapeau de tier, fenetre de la ressource, bras pivotant vers la voie ({@code arm} : face ou
     * est la voie, ou null).
     */
    public static RailMesh itemLoader(int tier, Direction arm, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, arm == null ? Direction.NORTH : arm.getClockWise());
        m.box(1.0F, 0, 1.0F, 15.0F, 1.5F, 15.0F, BigMat.R_FRAME);
        m.box(2.0F, 1.5F, 2.0F, 14.0F, 11.0F, 14.0F, BigMat.R_DARK);
        for (float x : new float[]{1.8F, 13.0F}) {
            for (float z : new float[]{1.8F, 13.0F}) {
                m.box(x, 1.5F, z, x + 1.2F, 11.6F, z + 1.2F, BigMat.R_STEEL);
                m.box(x - 0.1F, 11.0F, z - 0.1F, x + 1.3F, 11.8F, z + 1.3F, tier(tier));
            }
        }
        m.box(1.9F, 5.0F, 4.0F, 2.1F, 9.0F, 12.0F, BigMat.R_FAM_ITEM);
        m.box(13.9F, 5.0F, 4.0F, 14.1F, 9.0F, 12.0F, BigMat.R_FAM_ITEM);
        m.box(4.0F, 11.0F, 4.0F, 12.0F, 12.6F, 12.0F, BigMat.R_STEEL);
        m.cyl('y', 8, 8, 2.0F, 12.6F, 14.0F, BigMat.R_DARK, 12);
        m.translate(8, 14.6F, 8);
        m.rotate('y', 0, 0, 0, active ? 10 : -12);
        m.box(-10.0F, -0.8F, -1.2F, 0, 0.8F, 1.2F, tier(tier));
        m.box(-10.6F, -2.6F, -1.6F, -8.4F, 0.4F, 1.6F, BigMat.R_STEEL);
        m.box(-10.2F, -3.0F, -1.0F, -8.8F, -2.6F, 1.0F, BigMat.R_DARK);
        m.pop();
        m.pop();
        m.pop();
        return m;
    }

    /** Grue a eau : colonne, bras qui pivote au-dessus de la voie, manche de cuir, volant de la vanne. */
    public static RailMesh waterCrane(Direction arm, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, arm == null ? Direction.NORTH : arm.getClockWise());
        pad(m, 4.5F, 4.5F, 11.5F, 11.5F, BigMat.R_TIER1);
        m.cyl('y', 8, 8, 2.4F, PAD, 3.0F, BigMat.R_FAM_FLUID, 14);
        m.cyl('y', 8, 8, 1.3F, 3.0F, 21.0F, BigMat.R_STEEL, 12);
        for (float y : new float[]{6.0F, 12.0F, 18.0F}) {
            m.cyl('y', 8, 8, 1.55F, y, y + 0.6F, BigMat.R_TIER1, 12, false, 0.4F);
        }
        m.cyl('y', 8, 8, 1.8F, 21.0F, 22.0F, BigMat.R_DARK, 12);
        //Volant de vanne
        m.cyl('x', 7.0F, 8, 1.4F, 9.4F, 9.8F, BigMat.R_RED, 10, false, 0.4F);
        m.translate(8, 21.5F, 8);
        m.rotate('y', 0, 0, 0, active ? 0 : 35);
        m.cyl('x', 0, 0, 1.0F, -11.0F, 0, BigMat.R_STEEL, 10);
        m.cyl('y', -10.4F, 0, 1.1F, -1.0F, 0.6F, BigMat.R_DARK, 10);
        //Manche qui pend
        m.cyl('y', -10.4F, 0, 0.75F, active ? -9.0F : -6.5F, -1.0F, BigMat.R_BLACK, 8);
        m.pop();
        m.pop();
        m.pop();
        return m;
    }

    // ------------------------------------------------------------------ 0.20.2 : postes des nouvelles tractions et des wagons

    private static Direction track(Direction arm) {
        return arm == null ? Direction.NORTH : arm.getClockWise();
    }

    /** Barre entre deux points du plan (x, y), de z0 a z1 en epaisseur ; {@code ext} : prolongement aux deux bouts (flexibles). */
    static void rodXY(RailMesh m, float xa, float ya, float xb, float yb, float z0, float z1, float w, float ext, BigMat mat) {
        float len = (float) Math.hypot(xb - xa, yb - ya);
        float ang = (float) Math.toDegrees(Math.atan2(yb - ya, xb - xa));
        m.translate(xa, ya, 0);
        m.rotate('z', 0, 0, 0, ang);
        m.box(-ext, -w / 2, z0, len + ext, w / 2, z1, mat);
        m.pop();
        m.pop();
    }

    /** Flexible ou cable qui pend entre deux points du plan (x, y) a z fixe ({@code sag} : fleche au milieu). */
    static void hose(RailMesh m, float xa, float ya, float xb, float yb, float z, float sag, float w, BigMat mat) {
        int n = 7;
        float px = xa, py = ya;
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n;
            float x = xa + (xb - xa) * t, y = ya + (yb - ya) * t - sag * 4 * t * (1 - t);
            rodXY(m, px, py, x, y, z - w / 2, z + w / 2, w, w / 2, mat);
            px = x;
            py = y;
        }
    }

    /** Coque des postes de quai (comme le quai a objets) : socle, caisse sombre, montants a chapeau de tier, fenetres des ressources. */
    private static void shell(RailMesh m, int tier, BigMat front, BigMat back) {
        m.box(1.0F, 0, 1.0F, 15.0F, 1.5F, 15.0F, BigMat.R_FRAME);
        m.box(2.0F, 1.5F, 2.0F, 14.0F, 11.0F, 14.0F, BigMat.R_DARK);
        for (float x : new float[]{1.8F, 13.0F}) {
            for (float z : new float[]{1.8F, 13.0F}) {
                m.box(x, 1.5F, z, x + 1.2F, 11.6F, z + 1.2F, BigMat.R_STEEL);
                m.box(x - 0.1F, 11.0F, z - 0.1F, x + 1.3F, 11.8F, z + 1.3F, tier(tier));
            }
        }
        m.box(1.9F, 4.0F, 4.0F, 2.1F, 8.6F, 12.0F, front);
        m.box(13.9F, 4.0F, 4.0F, 14.1F, 8.6F, 12.0F, back);
        m.box(4.0F, 1.6F, 1.9F, 12.0F, 2.2F, 2.1F, tier(tier));
        m.box(4.0F, 1.6F, 13.9F, 12.0F, 2.2F, 14.1F, tier(tier));
    }

    /** Voyants de marche sur la face cote voie. */
    private static void status(RailMesh m, boolean active) {
        m.box(1.85F, 9.4F, 4.6F, 2.05F, 10.2F, 5.6F, active ? BigMat.R_LAMP_G : BigMat.R_LAMP_OFF);
        m.box(1.85F, 9.4F, 6.2F, 2.05F, 10.2F, 7.2F, active ? BigMat.R_LAMP_O : BigMat.R_LAMP_OFF);
    }

    /** Poste de remplissage (citernes fluide et gaz) : coque, colonne, bras qui s'abaisse sur le dome de la citerne. */
    public static RailMesh fluidLoader(int tier, Direction arm, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, track(arm));
        shell(m, tier, BigMat.R_FAM_FLUID, BigMat.R_FAM_GAS);
        status(m, active);
        m.box(4.0F, 11.0F, 4.0F, 12.0F, 12.4F, 12.0F, BigMat.R_STEEL);
        m.cyl('y', 8, 8, 1.2F, 12.4F, 21.0F, BigMat.R_STEEL, 12);
        for (float y : new float[]{14.0F, 17.6F}) {
            m.cyl('y', 8, 8, 1.35F, y, y + 0.5F, tier(tier), 12, false, 0.3F);
        }
        //Conduites du fluide et du gaz le long de la colonne
        m.cyl('y', 8, 9.8F, 0.4F, 12.4F, 21.4F, BigMat.R_FAM_FLUID, 8);
        m.cyl('y', 8, 6.2F, 0.4F, 12.4F, 21.4F, BigMat.R_FAM_GAS, 8);
        m.box(6.6F, 21.0F, 6.2F, 9.4F, 23.0F, 9.8F, BigMat.R_DARK);
        m.translate(8, 22.0F, 8);
        m.rotate('z', 0, 0, 0, active ? 5 : -16);
        m.cyl('x', 0, 0, 0.7F, -16.0F, 0, BigMat.R_CHROME, 10);
        m.box(-15.0F, 0.6F, -0.2F, -1.4F, 0.95F, 0.2F, BigMat.R_FAM_FLUID);
        m.box(1.0F, -1.2F, -1.2F, 4.4F, 1.2F, 1.2F, BigMat.R_DARK);
        m.box(3.0F, -1.3F, -1.3F, 3.6F, 1.3F, 1.3F, tier(tier));
        m.box(-17.0F, -1.0F, -1.0F, -15.0F, 0.8F, 1.0F, BigMat.R_DARK);
        m.cyl('y', -16.0F, 0, 0.55F, -3.6F, -1.0F, BigMat.R_CHROME, 10);
        m.cyl('y', -16.0F, 0, 0.8F, -4.4F, -3.6F, BigMat.R_DARK, 10);
        m.pop();
        m.pop();
        m.pop();
        return m;
    }

    /** Prise de quai (wagons-batteries) : enrouleur, potence, cable qui pend jusqu'au flanc du wagon. */
    public static RailMesh energyLoader(int tier, Direction arm, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, track(arm));
        shell(m, tier, BigMat.R_FAM_ENERGY, BigMat.R_FAM_ENERGY);
        status(m, active);
        //Enrouleur
        m.box(4.0F, 11.0F, 4.0F, 12.0F, 12.0F, 12.0F, BigMat.R_STEEL);
        for (float z : new float[]{4.6F, 10.8F}) {
            m.box(6.4F, 12.0F, z, 9.6F, 15.4F, z + 0.6F, BigMat.R_STEEL);
        }
        m.cyl('z', 8, 14.6F, 2.2F, 5.6F, 10.4F, BigMat.R_BLACK, 14);
        m.cyl('z', 8, 14.6F, 2.9F, 5.2F, 5.6F, tier(tier), 14);
        m.cyl('z', 8, 14.6F, 2.9F, 10.4F, 10.8F, tier(tier), 14);
        m.cyl('z', 8, 14.6F, 2.25F, 7.6F, 8.4F, BigMat.R_FAM_ENERGY, 14, false, 0.3F);
        m.cyl('z', 8, 14.6F, 0.6F, 4.4F, 11.6F, BigMat.R_CHROME, 8);
        //Potence vers la voie
        m.box(2.2F, 11.8F, 7.4F, 3.4F, 18.0F, 8.6F, BigMat.R_STEEL);
        m.box(-1.6F, 17.2F, 7.5F, 3.2F, 18.2F, 8.5F, tier(tier));
        m.cyl('z', -0.8F, 16.8F, 0.8F, 7.35F, 8.65F, BigMat.R_DARK, 10);
        //Cable de l'enrouleur a la poulie
        rodXY(m, 6.0F, 16.4F, -0.2F, 17.4F, 7.75F, 8.25F, 0.5F, 0.2F, BigMat.R_BLACK);
        if (active) {
            hose(m, -1.5F, 16.6F, -2.0F, 10.6F, 8.0F, -0.6F, 0.55F, BigMat.R_BLACK);
            plug(m, -2.7F, -1.5F, 9.0F, 10.6F, BigMat.R_FAM_ENERGY);
        } else {
            m.box(-1.75F, 14.4F, 7.75F, -1.25F, 16.2F, 8.25F, BigMat.R_BLACK);
            plug(m, -2.1F, -0.9F, 12.8F, 14.4F, BigMat.R_FAM_ENERGY);
        }
        m.pop();
        return m;
    }

    /** Fiche au bout d'un cable : boitier sombre et contacts lumineux cote wagon (-x). */
    private static void plug(RailMesh m, float x0, float x1, float y0, float y1, BigMat tip) {
        m.box(x0 + 0.2F, y0, 7.3F, x1, y1, 8.7F, BigMat.R_DARK);
        m.box(x0, y0 + 0.3F, 7.6F, x0 + 0.25F, y1 - 0.3F, 8.4F, tip);
    }

    /** Fibre de quai (wagons-serveurs) : baie a voyants, potence, fibre lumineuse qui pend jusqu'au wagon. */
    public static RailMesh dataLoader(int tier, Direction arm, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, track(arm));
        shell(m, tier, BigMat.R_FAM_DATA, BigMat.R_FAM_DATA);
        status(m, active);
        //Baie a voyants
        m.box(4.0F, 11.0F, 4.0F, 12.0F, 16.6F, 12.0F, BigMat.R_BLACK);
        for (int k = 0; k < 5; k++) {
            for (int c = 0; c < 3; c++) {
                BigMat led = !active ? BigMat.R_LAMP_OFF : (k + c) % 3 != 0 ? BigMat.R_FAM_DATA : BigMat.R_LAMP_G;
                m.box(3.85F, 11.6F + k * 0.95F, 5.0F + c * 2.1F, 4.0F, 12.1F + k * 0.95F, 6.6F + c * 2.1F, led);
                m.box(12.0F, 11.6F + k * 0.95F, 5.0F + c * 2.1F, 12.15F, 12.1F + k * 0.95F, 6.6F + c * 2.1F, led);
            }
        }
        m.box(4.2F, 16.6F, 4.2F, 11.8F, 17.0F, 11.8F, BigMat.R_GRILL);
        //Potence et fibre
        m.box(-1.6F, 15.0F, 7.5F, 4.0F, 15.8F, 8.5F, tier(tier));
        m.cyl('z', -0.9F, 14.8F, 0.55F, 7.6F, 8.4F, BigMat.R_DARK, 10);
        if (active) {
            hose(m, -1.2F, 14.4F, -2.2F, 10.4F, 8.0F, -0.4F, 0.35F, BigMat.R_FAM_DATA);
            plug(m, -2.7F, -1.7F, 9.4F, 10.4F, BigMat.R_LAMP_G);
        } else {
            m.box(-1.08F, 13.2F, 7.82F, -0.72F, 14.3F, 8.18F, BigMat.R_FAM_DATA);
            plug(m, -1.4F, -0.4F, 12.2F, 13.2F, BigMat.R_LAMP_OFF);
        }
        m.pop();
        return m;
    }

    /** Raccord pneumatique (wagons a air) : reservoir vertical, manometre face a la voie, flexible et tete d'accouplement. */
    public static RailMesh airLoader(int tier, Direction arm, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, track(arm));
        shell(m, tier, BigMat.R_FAM_PRESSURE, BigMat.R_FAM_PRESSURE);
        status(m, active);
        //Reservoir d'air
        m.cyl('y', 9.2F, 8, 2.8F, 11.0F, 19.0F, BigMat.R_STEEL_L, 14);
        m.cyl('y', 9.2F, 8, 2.0F, 19.0F, 19.8F, BigMat.R_STEEL_L, 12);
        m.cyl('y', 9.2F, 8, 0.4F, 19.8F, 20.6F, BigMat.R_CHROME, 8);
        for (float y : new float[]{12.6F, 17.0F}) {
            m.cyl('y', 9.2F, 8, 2.95F, y, y + 0.4F, tier(tier), 14, false, 0.3F);
        }
        m.cyl('y', 9.2F, 8, 2.9F, 15.8F, 16.4F, BigMat.R_FAM_PRESSURE, 14, false, 0.25F);
        //Manometre face a la voie
        m.box(5.6F, 14.6F, 7.6F, 6.6F, 15.4F, 8.4F, BigMat.R_STEEL);
        m.cyl('x', 15.0F, 8, 1.7F, 5.2F, 5.6F, BigMat.R_BLACK, 14);
        m.cyl('x', 15.0F, 8, 1.45F, 5.1F, 5.2F, BigMat.R_WHITE, 14);
        m.translate(5.05F, 15.0F, 8);
        m.rotate('x', 0, 0, 0, active ? -55 : 50);
        m.box(-0.05F, -0.1F, -0.09F, 0.05F, 1.25F, 0.09F, BigMat.R_RED);
        m.pop();
        m.pop();
        m.box(4.95F, 14.8F, 7.8F, 5.1F, 15.2F, 8.2F, BigMat.R_BLACK);
        //Sortie, vanne, flexible
        m.box(0.8F, 8.4F, 7.4F, 2.0F, 9.6F, 8.6F, BigMat.R_CHROME);
        m.cyl('y', 1.4F, 8, 0.7F, 9.6F, 9.9F, BigMat.R_RED, 10, false, 0.3F);
        if (active) {
            hose(m, 0.8F, 9.0F, -2.0F, 9.2F, 8.0F, 1.6F, 0.7F, BigMat.R_BLACK);
            m.box(-3.0F, 8.6F, 7.4F, -1.8F, 9.8F, 8.6F, BigMat.R_CHROME);
            m.box(-2.6F, 8.5F, 7.3F, -2.2F, 9.9F, 8.7F, BigMat.R_FAM_PRESSURE);
        } else {
            hose(m, 0.8F, 9.0F, 0.9F, 6.4F, 8.0F, -1.2F, 0.7F, BigMat.R_BLACK);
            m.box(0.3F, 5.2F, 7.4F, 1.5F, 6.4F, 8.6F, BigMat.R_CHROME);
            m.box(0.25F, 5.6F, 7.3F, 1.55F, 6.0F, 8.7F, BigMat.R_FAM_PRESSURE);
            m.box(1.5F, 5.6F, 7.7F, 1.9F, 6.0F, 8.3F, BigMat.R_STEEL);
        }
        m.pop();
        return m;
    }

    /**
     * Silo de chargement pose a cote de la voie : quatre pieds contreventes, cuve, goulotte inclinee qui surplombe l'axe de la voie
     * voisine, manche telescopique abaisse dans le wagon quand le poste charge.
     */
    public static RailMesh loadingSilo(int tier, Direction arm, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, track(arm));
        for (float x : new float[]{1.0F, 13.6F}) {
            for (float z : new float[]{1.0F, 13.6F}) {
                m.box(x - 0.3F, 0, z - 0.3F, x + 1.7F, 0.6F, z + 1.7F, BigMat.R_DARK);
                m.box(x, 0.6F, z, x + 1.4F, 18.0F, z + 1.4F, BigMat.R_STEEL);
            }
        }
        //Contreventement en croix sur les quatre faces et ceinture
        for (float x : new float[]{1.5F, 14.1F}) {
            m.rodYZ(x, x + 0.4F, 1.2F, 2.4F, 16.8F, 13.6F, 0.45F, BigMat.R_STEEL);
            m.rodYZ(x, x + 0.4F, 16.8F, 2.4F, 1.2F, 13.6F, 0.45F, BigMat.R_STEEL);
        }
        for (float z : new float[]{1.5F, 14.1F}) {
            rodXY(m, 2.4F, 1.2F, 13.6F, 16.8F, z, z + 0.4F, 0.45F, 0, BigMat.R_STEEL);
            rodXY(m, 2.4F, 16.8F, 13.6F, 1.2F, z, z + 0.4F, 0.45F, 0, BigMat.R_STEEL);
        }
        m.box(2.4F, 8.6F, 1.3F, 13.6F, 9.2F, 1.7F, BigMat.R_DARK);
        m.box(2.4F, 8.6F, 14.3F, 13.6F, 9.2F, 14.7F, BigMat.R_DARK);
        //Tremie sous la cuve
        m.box(4.0F, 15.0F, 4.0F, 12.0F, 18.0F, 12.0F, BigMat.R_DARK);
        m.box(5.6F, 13.4F, 5.6F, 10.4F, 15.0F, 10.4F, BigMat.R_DARK);
        //Cuve
        m.box(0.6F, 18.0F, 0.6F, 15.4F, 19.0F, 15.4F, BigMat.R_DARK);
        m.box(1.4F, 19.0F, 1.4F, 14.6F, 30.0F, 14.6F, BigMat.R_STEEL);
        for (float y : new float[]{22.0F, 27.4F}) {
            m.box(1.3F, y, 1.3F, 14.7F, y + 0.6F, 14.7F, tier(tier));
        }
        m.box(1.25F, 23.2F, 5.0F, 1.45F, 26.8F, 11.0F, BigMat.R_FAM_ITEM);
        m.box(14.55F, 23.2F, 5.0F, 14.75F, 26.8F, 11.0F, BigMat.R_FAM_ITEM);
        m.box(1.25F, 28.4F, 11.6F, 1.45F, 29.2F, 12.6F, active ? BigMat.R_LAMP_G : BigMat.R_LAMP_OFF);
        m.box(1.0F, 30.0F, 1.0F, 15.0F, 30.8F, 15.0F, BigMat.R_DARK);
        m.box(3.0F, 30.8F, 3.0F, 13.0F, 31.4F, 13.0F, BigMat.R_DARK);
        m.cyl('y', 8, 8, 1.6F, 31.4F, 32.2F, BigMat.R_STEEL, 12);
        //Vanne de sortie et goulotte inclinee
        m.box(-0.2F, 19.4F, 6.0F, 1.4F, 22.4F, 10.0F, BigMat.R_DARK);
        m.box(-0.3F, 20.6F, 5.9F, 0.6F, 21.2F, 10.1F, tier(tier));
        float cx0 = -9.4F, cy0 = 18.9F, cx1 = -0.2F, cy1 = 20.2F;
        float ang = (float) Math.toDegrees(Math.atan2(cy1 - cy0, cx1 - cx0));
        m.translate(cx0, cy0, 0);
        m.rotate('z', 0, 0, 0, ang);
        float len = (float) Math.hypot(cx1 - cx0, cy1 - cy0);
        m.box(0, -0.4F, 6.4F, len, 0, 9.6F, BigMat.R_STEEL);
        m.box(0, 0, 6.4F, len, 1.4F, 6.8F, BigMat.R_STEEL);
        m.box(0, 0, 9.2F, len, 1.4F, 9.6F, BigMat.R_STEEL);
        if (active) {
            m.tinted(0, 0, 6.8F, len, 0.3F, 9.2F, BigMat.R_BULK, 0xFF8A7060);
        }
        m.pop();
        m.pop();
        //Hauban de la goulotte
        rodXY(m, 1.4F, 28.6F, -7.6F, 20.6F, 7.8F, 8.2F, 0.3F, 0, BigMat.R_STEEL);
        //Manche telescopique a l'aplomb de l'axe de la voie
        m.cyl('y', -8.0F, 8, 1.5F, 16.6F, 19.2F, BigMat.R_DARK, 12);
        m.cyl('y', -8.0F, 8, 1.7F, 17.4F, 17.8F, tier(tier), 12, false, 0.3F);
        m.cyl('y', -8.0F, 8, 1.05F, active ? 12.6F : 15.6F, 16.6F, BigMat.R_STEEL, 12);
        m.cyl('y', -8.0F, 8, 1.2F, active ? 12.4F : 15.4F, active ? 12.9F : 15.9F, BigMat.R_HAZARD, 12, false, 0.3F);
        m.pop();
        return m;
    }

    /**
     * Fosse de dechargement, posee SOUS la voie : bloc plein (coque sombre, montants a chapeau de tier, fenetres des objets), dessus en
     * grille a 16 px, tremie en entonnoir dessous ; trappes ouvertes quand elle decharge.
     */
    public static RailMesh unloadingPit(int tier, boolean active) {
        RailMesh m = new RailMesh();
        m.box(0, 0, 0, 16, 1.0F, 16, BigMat.R_FRAME);
        for (float x : new float[]{0, 14.4F}) {
            for (float z : new float[]{0, 14.4F}) {
                m.box(x, 1.0F, z, x + 1.6F, 15.2F, z + 1.6F, BigMat.R_STEEL);
                m.box(x, 15.2F, z, x + 1.6F, 16.0F, z + 1.6F, tier(tier));
            }
        }
        //Parois (en retrait des montants) et fenetres des objets
        m.box(0.3F, 1.0F, 1.6F, 1.5F, 15.2F, 14.4F, BigMat.R_DARK);
        m.box(14.5F, 1.0F, 1.6F, 15.7F, 15.2F, 14.4F, BigMat.R_DARK);
        m.box(1.6F, 1.0F, 0.3F, 14.4F, 15.2F, 1.5F, BigMat.R_DARK);
        m.box(1.6F, 1.0F, 14.5F, 14.4F, 15.2F, 15.7F, BigMat.R_DARK);
        m.box(0.15F, 10.6F, 5.0F, 0.35F, 13.0F, 11.0F, BigMat.R_FAM_ITEM);
        m.box(15.65F, 10.6F, 5.0F, 15.85F, 13.0F, 11.0F, BigMat.R_FAM_ITEM);
        m.box(5.0F, 10.6F, 0.15F, 11.0F, 13.0F, 0.35F, BigMat.R_FAM_ITEM);
        m.box(5.0F, 10.6F, 15.65F, 11.0F, 13.0F, 15.85F, BigMat.R_FAM_ITEM);
        for (float y : new float[]{4.0F, 8.0F}) {
            //Raidisseurs des parois
            m.box(0.15F, y, 1.6F, 0.3F, y + 0.6F, 14.4F, BigMat.R_STEEL);
            m.box(15.7F, y, 1.6F, 15.85F, y + 0.6F, 14.4F, BigMat.R_STEEL);
            m.box(1.6F, y, 0.15F, 14.4F, y + 0.6F, 0.3F, BigMat.R_STEEL);
            m.box(1.6F, y, 15.7F, 14.4F, y + 0.6F, 15.85F, BigMat.R_STEEL);
        }
        //Cadre du dessus
        m.box(0.3F, 15.2F, 1.6F, 1.6F, 16.0F, 14.4F, BigMat.R_STEEL);
        m.box(14.4F, 15.2F, 1.6F, 15.7F, 16.0F, 14.4F, BigMat.R_STEEL);
        m.box(1.6F, 15.2F, 0.3F, 14.4F, 16.0F, 1.6F, BigMat.R_STEEL);
        m.box(1.6F, 15.2F, 14.4F, 14.4F, 16.0F, 15.7F, BigMat.R_STEEL);
        //Grille : barreaux porteurs et entretoises plus bas
        for (float x = 2.3F; x < 14.0F; x += 1.4F) {
            m.box(x, 15.3F, 1.6F, x + 0.45F, 16.0F, 14.4F, BigMat.R_STEEL_L);
        }
        for (float z : new float[]{4.6F, 8.0F, 11.4F}) {
            m.box(1.6F, 14.7F, z - 0.25F, 14.4F, 15.3F, z + 0.25F, BigMat.R_STEEL);
        }
        //Tremie en entonnoir
        for (float s : new float[]{-1, 1}) {
            float xa = 8 + s * 6.5F, xb = 8 + s * 1.6F;
            rodXY(m, xa, 13.8F, xb, 5.6F, 1.5F, 14.5F, 0.5F, 0.2F, BigMat.R_STEEL);
            m.rodYZ(1.5F, 14.5F, 13.8F, xa, 5.6F, xb, 0.5F, BigMat.R_STEEL);
        }
        m.box(6.4F, 3.0F, 6.4F, 9.6F, 5.8F, 9.6F, BigMat.R_DARK);
        m.box(1.5F, 1.0F, 6.0F, 14.5F, 2.4F, 10.0F, BigMat.R_BLACK);
        for (float x : new float[]{2.4F, 13.6F}) {
            m.cyl('z', x, 1.7F, 0.6F, 5.8F, 10.2F, BigMat.R_STEEL, 8);
        }
        if (active) {
            m.tinted(6.8F, 2.4F, 6.8F, 9.2F, 3.0F, 9.2F, BigMat.R_BULK, 0xFF8A7060);
            m.tinted(4.0F, 2.4F, 6.6F, 12.0F, 2.8F, 9.4F, BigMat.R_BULK, 0xFF8A7060);
        }
        //Trappes (charnieres sur les bords, ouvertes vers le bas en marche)
        m.translate(1.6F, 14.6F, 0);
        m.rotate('z', 0, 0, 0, active ? -50 : 0);
        m.box(0, -0.4F, 1.7F, 6.35F, 0, 14.3F, BigMat.R_STEEL_L);
        m.pop();
        m.pop();
        m.translate(14.4F, 14.6F, 0);
        m.rotate('z', 0, 0, 0, active ? 50 : 0);
        m.box(-6.35F, -0.4F, 1.7F, 0, 0, 14.3F, BigMat.R_STEEL_L);
        m.pop();
        m.pop();
        m.box(0.15F, 12.4F, 1.9F, 0.35F, 13.4F, 2.9F, active ? BigMat.R_LAMP_G : BigMat.R_LAMP_OFF);
        return m;
    }

    /** Grue de quai : mat, fleche qui surplombe la voie, chariot et palan (crochet abaisse en marche). */
    public static RailMesh platformCrane(Direction arm, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, track(arm));
        m.box(2.0F, 0, 2.0F, 14.0F, 1.6F, 14.0F, BigMat.R_FRAME);
        m.box(3.0F, 1.6F, 3.0F, 13.0F, 2.6F, 13.0F, BigMat.R_DARK);
        m.box(2.9F, 1.8F, 2.9F, 13.1F, 2.3F, 13.1F, BigMat.R_HAZARD);
        //Mat en caisson a jours
        m.box(6.6F, 2.6F, 6.6F, 9.4F, 32.0F, 9.4F, BigMat.R_STRIPE);
        for (float y = 4.0F; y < 28; y += 4.0F) {
            m.box(6.5F, y, 7.3F, 9.5F, y + 2.6F, 8.7F, BigMat.R_DARK);
            m.box(7.3F, y, 6.5F, 8.7F, y + 2.6F, 9.5F, BigMat.R_DARK);
        }
        //Cabine de manoeuvre
        m.box(9.4F, 20.0F, 6.0F, 12.6F, 24.0F, 10.0F, BigMat.R_STRIPE);
        m.box(12.55F, 21.6F, 6.6F, 12.7F, 23.4F, 9.4F, BigMat.R_GLASS_D);
        m.box(9.9F, 21.6F, 5.9F, 12.1F, 23.4F, 10.1F, BigMat.R_GLASS_D);
        m.box(9.2F, 24.0F, 5.8F, 12.8F, 24.4F, 10.2F, BigMat.R_DARK);
        //Fleche, contre-fleche, tete du mat et haubans
        m.box(-13.0F, 32.0F, 6.8F, 13.0F, 34.0F, 9.2F, BigMat.R_STRIPE);
        for (float x = -11.6F; x < 11.0F; x += 3.0F) {
            m.box(x, 32.4F, 6.7F, x + 1.8F, 33.6F, 9.3F, BigMat.R_DARK);
        }
        m.box(-13.1F, 31.9F, 6.7F, -11.6F, 34.1F, 9.3F, BigMat.R_HAZARD);
        m.box(10.0F, 29.4F, 6.2F, 13.6F, 32.0F, 9.8F, BigMat.R_CONCRETE);
        m.box(7.2F, 34.0F, 7.2F, 8.8F, 37.4F, 8.8F, BigMat.R_STRIPE);
        rodXY(m, 8.0F, 37.0F, -12.4F, 34.0F, 7.8F, 8.2F, 0.3F, 0, BigMat.R_STEEL);
        rodXY(m, 8.0F, 37.0F, 12.6F, 34.0F, 7.8F, 8.2F, 0.3F, 0, BigMat.R_STEEL);
        //Chariot et palan a l'aplomb de l'axe de la voie
        m.box(-9.6F, 30.8F, 6.4F, -6.4F, 32.0F, 9.6F, BigMat.R_DARK);
        for (float x : new float[]{-9.0F, -7.0F}) {
            m.cyl('z', x, 31.9F, 0.4F, 6.3F, 9.7F, BigMat.R_STEEL, 8);
        }
        float hy = active ? 25.4F : 28.0F;
        for (float x : new float[]{-8.6F, -7.6F}) {
            m.box(x, hy + 1.6F, 7.9F, x + 0.2F, 30.8F, 8.1F, BigMat.R_STEEL);
        }
        m.box(-9.2F, hy, 7.2F, -6.8F, hy + 1.6F, 8.8F, BigMat.R_HAZARD);
        m.cyl('z', -8.0F, hy + 0.8F, 0.5F, 7.1F, 8.9F, BigMat.R_DARK, 8);
        m.box(-8.3F, hy - 1.2F, 7.7F, -7.7F, hy, 8.3F, BigMat.R_CHROME);
        m.box(-8.3F, hy - 1.6F, 7.7F, -7.1F, hy - 1.2F, 8.3F, BigMat.R_CHROME);
        m.box(-7.5F, hy - 1.2F, 7.7F, -7.1F, hy - 0.7F, 8.3F, BigMat.R_CHROME);
        m.box(12.6F, 23.5F, 7.6F, 12.75F, 23.9F, 8.4F, active ? BigMat.R_LAMP_O : BigMat.R_LAMP_OFF);
        m.pop();
        return m;
    }

    /** Hauteur du dessus du quai voyageurs (plancher des voitures). */
    static final float PLATFORM_Y = TrackModels.RT + 3.4F;

    /** Quai voyageurs : dalles a hauteur du plancher, bordure et bande de securite, abri sur deux poteaux, banc, eclairage. */
    public static RailMesh passengerPlatform(Direction arm, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, track(arm));
        float top = PLATFORM_Y;
        m.box(0, 0, 0, 16, top - 0.5F, 16, BigMat.R_CONCRETE);
        m.box(0, top - 0.5F, 0, 0.6F, top, 16, BigMat.R_CONCRETE);
        m.box(0.6F, top - 0.5F, 0, 1.8F, top + 0.08F, 16, BigMat.R_STRIPE);
        m.box(1.8F, top - 0.5F, 0, 16, top, 16, BigMat.R_SLAB);
        //Abri : deux poteaux, toit incline
        float roof = top + 29.5F;
        for (float z : new float[]{2.0F, 13.2F}) {
            m.box(12.6F, top, z, 13.4F, roof + 1.2F, z + 0.8F, BigMat.R_STEEL);
            m.box(12.3F, top, z - 0.3F, 13.7F, top + 0.4F, z + 1.1F, BigMat.R_DARK);
            rodXY(m, 12.6F, roof - 3.0F, 9.0F, roof + 0.5F, z + 0.2F, z + 0.6F, 0.4F, 0, BigMat.R_STEEL);
        }
        rodXY(m, 15.4F, roof + 1.5F, 0.6F, roof, 0, 16, 0.6F, 0, BigMat.R_DARK);
        rodXY(m, 15.4F, roof + 1.05F, 0.6F, roof - 0.45F, 0.3F, 15.7F, 0.3F, 0, BigMat.R_STEEL);
        m.box(0.15F, roof - 0.9F, 0, 0.55F, roof + 0.2F, 16, BigMat.R_TIER2);
        //Eclairage sous l'abri
        for (float z : new float[]{3.6F, 12.4F}) {
            m.box(4.6F, roof - 0.25F, z - 0.6F, 8.6F, roof + 0.6F, z + 0.6F, BigMat.R_DARK);
            m.box(4.8F, roof - 0.4F, z - 0.4F, 8.4F, roof - 0.25F, z + 0.4F, active ? BigMat.R_LAMP : BigMat.R_LAMP_OFF);
        }
        //Banc
        m.box(10.4F, top + 3.2F, 4.0F, 12.4F, top + 3.8F, 12.0F, BigMat.R_PLANKS);
        m.box(12.0F, top + 3.8F, 4.0F, 12.4F, top + 7.0F, 12.0F, BigMat.R_PLANKS);
        for (float z : new float[]{4.4F, 11.2F}) {
            m.box(10.8F, top, z, 12.2F, top + 3.2F, z + 0.4F, BigMat.R_DARK);
        }
        //Panneau lumineux du quai, suspendu sous l'abri
        for (float z : new float[]{5.6F, 10.2F}) {
            m.box(12.9F, top + 19.0F, z, 13.1F, roof + 1.0F, z + 0.2F, BigMat.R_STEEL);
        }
        m.box(12.5F, top + 16.0F, 5.0F, 13.5F, top + 19.0F, 11.0F, BigMat.R_DARK);
        m.box(12.35F, top + 16.4F, 5.4F, 12.5F, top + 18.6F, 10.6F, BigMat.R_SCREEN);
        m.box(12.3F, top + 17.2F, 6.0F, 12.35F, top + 17.8F, 9.6F, active ? BigMat.R_LAMP_O : BigMat.R_LAMP_OFF);
        m.pop();
        return m;
    }

    /** Charbonnier : trémie en bois cerclee d'acier sur quatre pieds, goulotte pivotante vers le tender. */
    public static RailMesh coalingTower(Direction arm, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, track(arm));
        for (float x : new float[]{1.6F, 12.8F}) {
            for (float z : new float[]{1.6F, 12.8F}) {
                m.box(x - 0.4F, 0, z - 0.4F, x + 2.0F, 0.8F, z + 2.0F, BigMat.R_CONCRETE);
                m.box(x, 0.8F, z, x + 1.6F, 18.0F, z + 1.6F, BigMat.R_OAK);
            }
        }
        for (float x : new float[]{2.0F, 13.2F}) {
            m.rodYZ(x, x + 0.8F, 1.6F, 3.2F, 16.4F, 12.8F, 0.6F, BigMat.R_OAK);
        }
        for (float z : new float[]{2.0F, 13.2F}) {
            rodXY(m, 3.2F, 16.4F, 12.8F, 1.6F, z, z + 0.8F, 0.6F, 0, BigMat.R_OAK);
        }
        //Tremie
        m.box(3.6F, 15.4F, 3.6F, 12.4F, 18.0F, 12.4F, BigMat.R_DARK);
        m.box(5.6F, 14.0F, 5.6F, 10.4F, 15.4F, 10.4F, BigMat.R_DARK);
        m.box(1.2F, 18.0F, 1.2F, 14.8F, 28.0F, 14.8F, BigMat.R_PLANKS);
        for (float y : new float[]{19.2F, 23.2F, 27.2F}) {
            m.box(1.1F, y, 1.1F, 14.9F, y + 0.5F, 14.9F, BigMat.R_DARK);
        }
        for (float x : new float[]{1.0F, 14.2F}) {
            for (float z : new float[]{1.0F, 14.2F}) {
                m.box(x, 17.9F, z, x + 0.8F, 28.4F, z + 0.8F, BigMat.R_STEEL);
            }
        }
        //Charbon
        m.box(1.8F, 27.6F, 1.8F, 14.2F, 28.4F, 14.2F, BigMat.R_COAL);
        m.box(4.0F, 28.4F, 4.0F, 12.0F, 29.4F, 12.0F, BigMat.R_COAL);
        m.box(6.0F, 29.4F, 6.0F, 10.0F, 30.0F, 10.0F, BigMat.R_COAL);
        //Vanne et goulotte pivotante (abaissee en marche)
        m.box(0.2F, 18.8F, 6.0F, 1.2F, 22.0F, 10.0F, BigMat.R_DARK);
        m.box(0.0F, 21.0F, 5.8F, 1.2F, 21.6F, 10.2F, BigMat.R_STEEL);
        m.translate(0.2F, 20.0F, 0);
        m.rotate('z', 0, 0, 0, active ? 24 : -18);
        m.box(-10.0F, -0.4F, 6.4F, 0, 0, 9.6F, BigMat.R_STEEL);
        m.box(-10.0F, 0, 6.4F, 0, 1.3F, 6.8F, BigMat.R_STEEL);
        m.box(-10.0F, 0, 9.2F, 0, 1.3F, 9.6F, BigMat.R_STEEL);
        m.box(-10.4F, -0.5F, 6.3F, -9.8F, 1.4F, 9.7F, BigMat.R_DARK);
        if (active) {
            m.box(-9.8F, 0, 6.8F, 0, 0.4F, 9.2F, BigMat.R_COAL);
        }
        m.cyl('z', 0, 0, 0.5F, 6.2F, 9.8F, BigMat.R_STEEL, 8);
        m.pop();
        m.pop();
        //Chaine de levage
        float a = (float) Math.toRadians(active ? 24 : -18);
        float tx = 0.2F - 9.0F * (float) Math.cos(a), ty = 20.0F - 9.0F * (float) Math.sin(a) + 1.4F;
        rodXY(m, 1.2F, 27.8F, tx, ty, 7.85F, 8.15F, 0.3F, 0, BigMat.R_STEEL);
        m.pop();
        return m;
    }

    /** Borne de vapeur (vapeur sans foyer) : colonne calorifugee, manometre, volant, bras (releve a l'arret) et flexible annele. */
    public static RailMesh steamCharger(Direction arm, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, track(arm));
        pad(m, 4.0F, 4.0F, 12.0F, 12.0F, BigMat.R_TIER1);
        m.box(5.0F, 1.4F, 5.0F, 11.0F, 4.0F, 11.0F, BigMat.R_STEEL);
        m.box(4.85F, 2.0F, 6.5F, 5.05F, 3.4F, 9.5F, BigMat.R_FAM_GAS);
        m.cyl('y', 8, 8, 1.9F, 4.0F, 20.0F, BigMat.R_WHITE, 14);
        for (float y : new float[]{6.0F, 10.0F, 14.0F, 18.0F}) {
            m.cyl('y', 8, 8, 2.05F, y, y + 0.5F, BigMat.R_BRASS, 14, false, 0.3F);
        }
        //Manometre face a la voie
        m.cyl('x', 12.0F, 8, 1.1F, 5.6F, 6.3F, BigMat.R_BLACK, 12);
        m.cyl('x', 12.0F, 8, 0.9F, 5.5F, 5.6F, BigMat.R_WHITE, 12);
        m.translate(5.45F, 12.0F, 8);
        m.rotate('x', 0, 0, 0, active ? -60 : 45);
        m.box(-0.05F, -0.1F, -0.07F, 0.05F, 0.8F, 0.07F, BigMat.R_RED);
        m.pop();
        m.pop();
        //Volant de la vanne
        m.cyl('z', 8, 8.0F, 0.25F, 9.8F, 10.6F, BigMat.R_STEEL, 6);
        m.cyl('z', 8, 8.0F, 1.2F, 10.4F, 10.8F, BigMat.R_RED, 10, false, 0.35F);
        //Tete et bras pivotant
        m.cyl('y', 8, 8, 2.2F, 20.0F, 21.4F, BigMat.R_DARK, 12);
        m.translate(8, 21.0F, 8);
        m.rotate('z', 0, 0, 0, active ? 0 : -16);
        m.cyl('x', 0, 0, 0.9F, -15.2F, 0, BigMat.R_WHITE, 10);
        for (float x : new float[]{-4.0F, -9.0F, -13.0F}) {
            m.cyl('x', 0, 0, 1.05F, x - 0.5F, x, BigMat.R_BRASS, 10, false, 0.3F);
        }
        m.box(-17.0F, -1.0F, -1.0F, -15.2F, 0.8F, 1.0F, BigMat.R_DARK);
        float low = active ? -5.0F : -2.4F;
        m.cyl('y', -16.0F, 0, 0.6F, low, -1.0F, BigMat.R_BLACK, 10);
        for (float y = -1.6F; y > low + 0.3F; y -= 0.8F) {
            m.cyl('y', -16.0F, 0, 0.7F, y - 0.2F, y, BigMat.R_CHROME, 10, false, 0.15F);
        }
        m.cyl('y', -16.0F, 0, 0.45F, low - 0.6F, low, BigMat.R_CHROME, 8);
        m.pop();
        m.pop();
        m.pop();
        return m;
    }

    /** Pompe a carburant (diesel et gaz) : distributeur, afficheur, pistolet et flexible jusqu'au bouchon de la locomotive. */
    public static RailMesh fuelPump(Direction arm, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, track(arm));
        pad(m, 2.5F, 3.0F, 13.5F, 13.0F, BigMat.R_TIER2);
        m.box(3.4F, PAD, 4.0F, 12.6F, 2.0F, 12.0F, BigMat.R_DARK);
        m.box(5.0F, 2.0F, 5.0F, 11.0F, 15.0F, 11.0F, BigMat.R_RED);
        m.box(4.9F, 3.0F, 5.6F, 11.1F, 8.0F, 10.4F, BigMat.R_STEEL_L);
        m.box(4.85F, 8.6F, 5.8F, 5.0F, 9.6F, 7.8F, BigMat.R_FAM_FLUID);
        m.box(4.85F, 8.6F, 8.2F, 5.0F, 9.6F, 10.2F, BigMat.R_FAM_GAS);
        m.box(4.85F, 10.4F, 5.8F, 5.0F, 13.6F, 10.2F, BigMat.R_SCREEN);
        if (active) {
            m.box(4.8F, 11.2F, 6.4F, 4.85F, 11.7F, 9.6F, BigMat.R_LAMP_O);
            m.box(4.8F, 12.3F, 6.4F, 4.85F, 12.8F, 8.6F, BigMat.R_LAMP_O);
        }
        m.box(4.6F, 15.0F, 4.6F, 11.4F, 15.8F, 11.4F, BigMat.R_DARK);
        m.box(6.4F, 15.8F, 6.4F, 9.6F, 16.6F, 9.6F, active ? BigMat.R_LAMP : BigMat.R_LAMP_OFF);
        m.box(6.2F, 16.6F, 6.2F, 9.8F, 16.9F, 9.8F, BigMat.R_DARK);
        //Etui du pistolet et flexible
        m.box(4.5F, 5.6F, 7.8F, 4.95F, 8.1F, 9.0F, BigMat.R_DARK);
        m.box(4.6F, 4.4F, 8.0F, 4.95F, 5.2F, 8.8F, BigMat.R_CHROME);
        if (active) {
            hose(m, 4.6F, 4.8F, -1.6F, 7.6F, 8.4F, 2.4F, 0.6F, BigMat.R_BLACK);
            m.box(-2.6F, 7.2F, 8.0F, -1.4F, 8.0F, 8.8F, BigMat.R_CHROME);
            m.box(-1.6F, 6.6F, 7.95F, -1.0F, 7.4F, 8.85F, BigMat.R_DARK);
        } else {
            hose(m, 4.6F, 4.8F, 4.2F, 6.0F, 8.4F, 2.2F, 0.6F, BigMat.R_BLACK);
            m.box(3.9F, 6.0F, 8.0F, 4.5F, 7.8F, 8.8F, BigMat.R_CHROME);
        }
        m.pop();
        return m;
    }

    /** Borne de recharge electrique : colonne sombre, bande d'energie, ecran, cable et fiche jusqu'au flanc de la locomotive. */
    public static RailMesh chargingPost(Direction arm, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, track(arm));
        basePlate(m);
        m.box(6.2F, 1.4F, 6.0F, 9.8F, 16.0F, 10.0F, BigMat.R_DARK);
        m.cyl('z', 8, 16.0F, 1.8F, 6.0F, 10.0F, BigMat.R_DARK, 12);
        m.box(6.0F, 15.4F, 5.9F, 10.0F, 15.8F, 10.1F, BigMat.R_TIER3);
        m.box(6.05F, 3.0F, 6.5F, 6.3F, 14.6F, 7.1F, BigMat.R_FAM_ENERGY);
        m.box(9.7F, 3.0F, 8.9F, 9.95F, 14.6F, 9.5F, BigMat.R_FAM_ENERGY);
        m.box(6.05F, 10.6F, 7.6F, 6.3F, 13.6F, 9.6F, BigMat.R_SCREEN);
        m.box(6.0F, 11.4F, 8.0F, 6.05F, 12.0F, 9.2F, active ? BigMat.R_LAMP_G : BigMat.R_LAMP_OFF);
        m.box(6.0F, 14.0F, 7.6F, 6.05F, 14.4F, 9.6F, active ? BigMat.R_LAMP_G : BigMat.R_LAMP_R);
        //Prise et cable
        m.box(5.6F, 5.6F, 7.8F, 6.2F, 6.8F, 9.0F, BigMat.R_BLACK);
        if (active) {
            hose(m, 5.8F, 6.2F, -1.6F, 9.6F, 8.4F, 2.4F, 0.6F, BigMat.R_BLACK);
            m.box(-2.6F, 9.0F, 7.8F, -1.4F, 10.4F, 9.0F, BigMat.R_DARK);
            m.box(-2.7F, 9.3F, 8.0F, -2.5F, 10.1F, 8.8F, BigMat.R_FAM_ENERGY);
        } else {
            hose(m, 5.8F, 6.2F, 5.6F, 7.6F, 8.4F, 3.2F, 0.6F, BigMat.R_BLACK);
            m.box(5.2F, 7.6F, 7.8F, 6.2F, 9.0F, 9.0F, BigMat.R_DARK);
            m.box(5.1F, 7.9F, 8.0F, 5.2F, 8.7F, 8.8F, BigMat.R_FAM_ENERGY);
        }
        m.pop();
        return m;
    }

    /** Poste de combustible nucleaire : socle blinde, chateau de plomb a lueur bleue, portique et hotte de transfert. */
    public static RailMesh nuclearFuelStation(Direction arm, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, track(arm));
        m.box(0.8F, 0, 0.8F, 15.2F, 1.6F, 15.2F, BigMat.R_FRAME);
        m.box(0.7F, 0.6F, 0.7F, 15.3F, 1.2F, 15.3F, BigMat.R_HAZARD);
        //Chateau de plomb
        float kx = 8.2F;
        m.cyl('y', kx, 8, 4.6F, 1.6F, 13.0F, BigMat.R_LEAD, 18);
        for (float y : new float[]{3.4F, 7.0F, 10.6F}) {
            m.cyl('y', kx, 8, 4.75F, y, y + 0.7F, BigMat.R_DARK, 18, false, 0.5F);
        }
        for (float a : new float[]{0, 90, 180}) {
            m.rotate('y', kx, 0, 8, a);
            for (float[] ys : new float[][]{{4.3F, 6.8F}, {7.9F, 10.4F}}) {
                m.box(kx - 0.6F, ys[0], 3.25F, kx + 0.6F, ys[1], 3.65F, BigMat.R_GLOW_BLUE);
            }
            m.pop();
        }
        m.cyl('y', kx, 8, 3.8F, 13.0F, 14.4F, BigMat.R_DARK, 16);
        m.box(kx - 1.4F, 14.4F, 6.6F, kx + 1.4F, 14.7F, 9.4F, BigMat.R_STRIPE);
        m.box(kx - 0.4F, 14.7F, 7.6F, kx + 0.4F, 14.9F, 8.4F, BigMat.R_BLACK);
        //Portique de transfert
        m.box(13.2F, 1.6F, 6.8F, 15.2F, 26.0F, 9.2F, BigMat.R_STEEL);
        for (float y = 3.0F; y < 24; y += 5.0F) {
            m.box(13.1F, y, 6.7F, 15.3F, y + 1.0F, 9.3F, BigMat.R_HAZARD);
        }
        m.box(-11.0F, 24.0F, 7.0F, 15.0F, 25.6F, 9.0F, BigMat.R_STRIPE);
        m.box(-11.1F, 23.9F, 6.9F, -9.8F, 25.7F, 9.1F, BigMat.R_HAZARD);
        rodXY(m, 14.2F, 26.0F, 5.0F, 24.8F, 7.8F, 8.2F, 0.3F, 0, BigMat.R_STEEL);
        m.box(-9.6F, 23.0F, 6.6F, -6.4F, 24.0F, 9.4F, BigMat.R_DARK);
        //Hotte de transfert a l'aplomb de l'axe de la voie
        float fy = active ? 16.4F : 18.4F;
        m.box(-8.6F, fy + 4.0F, 7.8F, -8.2F, 23.0F, 8.2F, BigMat.R_STEEL);
        m.box(-7.8F, fy + 4.0F, 7.8F, -7.4F, 23.0F, 8.2F, BigMat.R_STEEL);
        m.cyl('y', -8.0F, 8, 1.7F, fy + 0.4F, fy + 4.0F, BigMat.R_LEAD, 14);
        m.cyl('y', -8.0F, 8, 1.85F, fy + 2.8F, fy + 3.3F, BigMat.R_DARK, 14, false, 0.3F);
        m.cyl('y', -8.0F, 8, 1.2F, fy, fy + 0.4F, BigMat.R_GLOW_BLUE, 12);
        m.box(13.05F, 16.2F, 7.4F, 13.2F, 17.2F, 8.6F, active ? BigMat.R_LAMP_B : BigMat.R_LAMP_OFF);
        m.pop();
        return m;
    }

    /**
     * Poteau de catenaire pose dans le bloc voisin de la voie ({@code toTrack} : direction de la voie) : mat en H, console qui porte le fil
     * de contact au-dessus de l'axe de la voie (8 px au-dela du bord), isolateurs, hauban. Le fil lui-meme est dessine a part (WIRE_Y).
     */
    public static RailMesh catenaryPole(int tier, Direction toTrack) {
        RailMesh m = new RailMesh();
        orient(m, track(toTrack));
        float top = WIRE_Y + 7.0F;
        pad(m, 0.6F, 5.6F, 4.4F, 10.4F, BigMat.R_TIER3);
        m.box(1.6F, 1.4F, 7.0F, 3.4F, top, 7.4F, BigMat.R_STEEL);
        m.box(1.6F, 1.4F, 8.6F, 3.4F, top, 9.0F, BigMat.R_STEEL);
        m.box(2.3F, 1.4F, 7.4F, 2.7F, top, 8.6F, BigMat.R_STEEL);
        m.box(1.5F, 1.4F, 6.9F, 3.5F, 2.6F, 9.1F, tier(tier));
        m.box(1.4F, top, 6.8F, 3.6F, top + 0.4F, 9.2F, tier(tier));
        //Console et isolateur a l'encastrement
        float cy = WIRE_Y + 4.0F;
        m.box(-9.2F, cy - 0.35F, 7.65F, 0.4F, cy + 0.35F, 8.35F, BigMat.R_STEEL);
        m.cyl('x', cy, 8, 0.5F, 0.4F, 1.6F, BigMat.R_WHITE, 10);
        for (float x : new float[]{0.6F, 1.1F}) {
            m.cyl('x', cy, 8, 0.8F, x, x + 0.2F, BigMat.R_WHITE, 10);
        }
        m.box(-9.4F, cy - 0.45F, 7.55F, -8.9F, cy + 0.45F, 8.45F, tier(tier));
        //Hauban
        rodXY(m, 1.8F, top - 0.6F, -8.6F, cy + 0.3F, 7.85F, 8.15F, 0.3F, 0, BigMat.R_STEEL);
        m.cyl('x', top - 1.2F, 8, 0.45F, 0.6F, 1.6F, BigMat.R_WHITE, 8);
        //Pendule, isolateur et griffe au-dessus de l'axe de la voie
        m.cyl('y', -8.0F, 8, 0.3F, WIRE_Y + 1.6F, cy - 0.35F, BigMat.R_WHITE, 8);
        for (float y : new float[]{WIRE_Y + 2.0F, WIRE_Y + 2.6F, WIRE_Y + 3.2F}) {
            m.cyl('y', -8.0F, 8, 0.6F, y, y + 0.2F, BigMat.R_WHITE, 8);
        }
        m.box(-8.4F, WIRE_Y + 0.6F, 7.6F, -7.6F, WIRE_Y + 1.6F, 8.4F, BigMat.R_DARK);
        m.box(-8.2F, WIRE_Y + 0.35F, 7.7F, -7.8F, WIRE_Y + 0.6F, 8.3F, BigMat.R_COPPER);
        m.pop();
        return m;
    }

    /** Troisieme rail : rail conducteur sur isolateurs, capot de protection, pose contre le bord du bloc cote voie ({@code toTrack}). */
    public static RailMesh thirdRail(Direction toTrack) {
        RailMesh m = new RailMesh();
        orient(m, track(toTrack));
        for (float z : new float[]{4.0F, 12.0F}) {
            m.box(0.4F, 0, z - 1.2F, 3.8F, 0.8F, z + 1.2F, BigMat.R_CONCRETE);
            m.cyl('y', 2.0F, z, 0.6F, 0.8F, 2.6F, BigMat.R_WHITE, 10);
            for (float y : new float[]{1.1F, 1.8F}) {
                m.cyl('y', 2.0F, z, 0.85F, y, y + 0.25F, BigMat.R_WHITE, 10);
            }
            m.box(1.2F, 2.6F, z - 0.6F, 2.8F, 3.0F, z + 0.6F, BigMat.R_DARK);
            //Console du capot
            m.box(3.0F, 0.8F, z - 0.3F, 3.4F, 5.6F, z + 0.3F, BigMat.R_DARK);
            m.box(1.0F, 5.2F, z - 0.3F, 3.0F, 5.6F, z + 0.3F, BigMat.R_DARK);
        }
        m.box(1.3F, 3.0F, 0, 2.7F, 3.4F, 16, BigMat.R_STEEL);
        m.box(1.75F, 3.4F, 0, 2.25F, 4.2F, 16, BigMat.R_STEEL);
        m.box(1.2F, 4.2F, 0, 2.8F, 4.8F, 16, BigMat.R_STEEL_L);
        m.box(0.6F, 5.6F, 0, 3.4F, 6.0F, 16, BigMat.R_STRIPE);
        m.box(0.45F, 5.65F, 0, 0.6F, 5.95F, 16, BigMat.R_FAM_ENERGY);
        m.pop();
        return m;
    }

    /** Sous-station (bloc plein, style des machines) : transformateur a ailettes, traversees isolantes, fenetre d'energie en facade. */
    public static RailMesh substation(int tier, Direction facing, boolean active) {
        RailMesh m = new RailMesh();
        orient(m, facing == null ? Direction.NORTH : facing);
        m.box(0.5F, 0, 0.5F, 15.5F, 1.6F, 15.5F, BigMat.R_FRAME);
        m.box(2.5F, 1.6F, 3.5F, 13.5F, 11.0F, 12.5F, BigMat.R_DARK);
        for (float z = 4.0F; z < 12.0F; z += 1.2F) {
            m.box(1.0F, 2.6F, z, 2.5F, 10.0F, z + 0.4F, BigMat.R_GRILL);
            m.box(13.5F, 2.6F, z, 15.0F, 10.0F, z + 0.4F, BigMat.R_GRILL);
        }
        for (float x : new float[]{0.8F, 14.0F}) {
            for (float z : new float[]{0.8F, 14.0F}) {
                m.box(x, 1.6F, z, x + 1.2F, 12.0F, z + 1.2F, BigMat.R_STEEL);
                m.box(x - 0.1F, 11.4F, z - 0.1F, x + 1.3F, 12.2F, z + 1.3F, tier(tier));
            }
        }
        m.box(2.4F, 2.0F, 3.4F, 13.6F, 2.5F, 12.6F, tier(tier));
        m.box(4.0F, 4.0F, 3.35F, 12.0F, 9.0F, 3.5F, BigMat.R_FAM_ENERGY);
        m.box(4.0F, 9.6F, 3.35F, 5.2F, 10.4F, 3.5F, active ? BigMat.R_LAMP_G : BigMat.R_LAMP_OFF);
        m.box(5.8F, 9.6F, 3.35F, 7.0F, 10.4F, 3.5F, active ? BigMat.R_LAMP_O : BigMat.R_LAMP_OFF);
        m.box(2.2F, 11.0F, 3.2F, 13.8F, 11.8F, 12.8F, BigMat.R_STEEL);
        //Traversees : trois haute tension a l'arriere, trois basse tension a l'avant
        for (int i = 0; i < 3; i++) {
            float x = 4.5F + i * 3.5F;
            m.cyl('y', x, 10.2F, 0.55F, 11.8F, 15.6F, BigMat.R_WHITE, 10);
            for (float y : new float[]{12.6F, 13.6F, 14.6F}) {
                m.cyl('y', x, 10.2F, 0.95F, y, y + 0.35F, BigMat.R_WHITE, 10);
            }
            m.cyl('y', x, 10.2F, 0.4F, 15.6F, 16.0F, BigMat.R_COPPER, 8);
            m.cyl('y', x, 5.6F, 0.45F, 11.8F, 13.6F, BigMat.R_WHITE, 8);
            m.cyl('y', x, 5.6F, 0.7F, 12.6F, 12.9F, BigMat.R_WHITE, 8);
            m.cyl('y', x, 5.6F, 0.35F, 13.6F, 14.0F, BigMat.R_COPPER, 8);
        }
        m.pop();
        return m;
    }
}
