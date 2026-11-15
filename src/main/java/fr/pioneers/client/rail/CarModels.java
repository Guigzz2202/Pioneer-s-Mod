package fr.pioneers.client.rail;

import fr.pioneers.client.industry.big.BigMat;
import fr.pioneers.rail.train.CarType;

/**
 * 0.20.0 : maillages des vehicules, d'apres les apercus valides ({@code scratchpad/rail/stock.py}) : 1 bloc de long le long de z (avant vers
 * z = 16), roues sur le dessus du rail ({@link TrackModels#RT}). Partie fixe (avec les essieux en groupes tournants : angle = distance
 * parcourue x 360 / perimetre) et partie dynamique (bielles, balancier de la draisine) refaite a chaque image.
 */
public final class CarModels {

    static final float RT = TrackModels.RT;
    /** Dessus du plateau du wagon plat (px) : le bloc transporte se pose ici (plateau de x 2 a 14, z 0,6 a 15,4). */
    public static final float FLAT_DECK = RT + 4.2F;
    /** Hauteur d'assise de la voiture voyageurs (px). */
    public static final float SEAT_Y = RT + 5.2F;
    /** Les deux places de la voiture voyageurs (z du milieu de l'assise, en px ; les voyageurs regardent vers +z, x = 8). */
    public static final float[] SEAT_Z = {4.6F, 11.0F};

    private CarModels() {
    }

    static BigMat tier(int t) {
        return switch (t) {
            case 1 -> BigMat.R_TIER2;
            case 2 -> BigMat.R_TIER3;
            case 3 -> BigMat.R_TIER4;
            default -> BigMat.R_TIER1;
        };
    }

    static BigMat livery(int t) {
        return switch (t) {
            case 1 -> BigMat.R_LIVERY2;
            case 2 -> BigMat.R_LIVERY3;
            case 3 -> BigMat.R_LIVERY4;
            default -> BigMat.R_LIVERY1;
        };
    }

    /** Facteur de rotation d'une roue (degres par px parcouru). */
    static float spinFactor(float r) {
        return (float) (360 / (2 * Math.PI * r));
    }

    // ------------------------------------------------------------------ pieces communes

    static void wheelset(RailMesh m, float z, float r, float x0, float x1, float x2, float x3) {
        float cy = RT + r;
        m.spin('x', 0, cy, z, spinFactor(r));
        for (float[] ab : new float[][]{{x0, x1}, {x2, x3}}) {
            float a = ab[0], b = ab[1];
            m.cyl('x', cy, z, r, a, b, BigMat.R_WHEEL, 14);
            m.cyl('x', cy, z, r + 0.35F, a + (a < 8 ? 0 : 0.9F), b - (a < 8 ? 0.9F : 0), BigMat.R_WHEEL, 14, false, 0.5F);
            m.cyl('x', cy, z, r * 0.35F, a - 0.2F, b + 0.2F, BigMat.R_CHROME, 8);
            //Rayons (visibles quand la roue tourne)
            m.box(a - 0.05F, cy - r * 0.85F, z - 0.25F, a + 0.02F, cy + r * 0.85F, z + 0.25F, BigMat.R_STEEL);
            m.box(b - 0.02F, cy - 0.25F, z - r * 0.85F, b + 0.05F, cy + 0.25F, z + r * 0.85F, BigMat.R_STEEL);
        }
        m.cyl('x', cy, z, 0.45F, x1, x2, BigMat.R_STEEL, 6);
        m.fixed();
    }

    static void wheelset(RailMesh m, float z, float r) {
        wheelset(m, z, r, 4.0F, 5.2F, 10.8F, 12.0F);
    }

    static void buffers(RailMesh m, float z, float y, float sign) {
        for (float x : new float[]{5.0F, 11.0F}) {
            m.cyl('z', x, y, 0.75F, z, z + sign * 1.2F, BigMat.R_CHROME, 8);
            m.box(x - 1.0F, y - 1.0F, z + sign * 1.2F - 0.15F, x + 1.0F, y + 1.0F, z + sign * 1.2F + 0.15F, BigMat.R_STEEL);
        }
    }

    static void coupler(RailMesh m, float z, float y, float sign) {
        m.box(7.4F, y - 0.5F, z, 8.6F, y + 0.5F, z + sign * 1.4F, BigMat.R_DARK);
        m.box(7.0F, y - 0.7F, z + sign * 1.2F, 9.0F, y + 0.7F, z + sign * 1.6F, BigMat.R_STEEL);
    }

    /** Chassis de wagon (deux essieux, longerons, tampons) ; renvoie le dessus du plancher. */
    static float chassis(RailMesh m) {
        float r = 1.8F, cy = RT + r;
        wheelset(m, 3.6F, r);
        wheelset(m, 12.4F, r);
        m.box(3.4F, cy + 0.4F, 0.6F, 12.6F, cy + 1.6F, 15.4F, BigMat.R_FRAME);
        m.box(5.8F, cy - 0.4F, 2.6F, 6.4F, cy + 0.6F, 4.6F, BigMat.R_DARK);
        m.box(9.6F, cy - 0.4F, 11.4F, 10.2F, cy + 0.6F, 13.4F, BigMat.R_DARK);
        buffers(m, 15.4F, cy + 1.0F, 1);
        buffers(m, 0.6F, cy + 1.0F, -1);
        return cy + 1.6F;
    }

    static void gauge(RailMesh m, BigMat fam, float y0, float y1, float z0, float fill) {
        for (float x : new float[]{13.4F, 2.3F}) {
            float o = x > 8 ? 0.25F : -0.15F;
            m.box(x, y0, z0, x + 0.3F, y1, z0 + 1.4F, BigMat.R_DARK);
            m.box(x + o, y0 + 0.2F, z0 + 0.2F, x + o + 0.15F, y0 + 0.2F + Math.max(0.05F, (y1 - y0 - 0.4F) * fill), z0 + 1.2F, fam);
        }
    }

    static void logo(RailMesh m, BigMat fam, float y, float z) {
        m.box(13.4F, y, z, 13.6F, y + 2.0F, z + 2.0F, BigMat.R_DARK);
        icon(m, fam, 13.55F, 13.7F, y, z, iconOf(fam));
    }

    /** Logo du type sur les deux flancs : plaque sombre de x0 a x1 (cote +x, en miroir de l'autre cote) et pictogramme lumineux. */
    static void logoAt(RailMesh m, BigMat fam, String[] icon, float x0, float x1, float y, float z) {
        m.box(x0, y, z, x1, y + 2.0F, z + 2.0F, BigMat.R_DARK);
        m.box(16 - x1, y, z, 16 - x0, y + 2.0F, z + 2.0F, BigMat.R_DARK);
        icon(m, fam, x1 - 0.05F, x1 + 0.1F, y, z, icon);
        icon(m, fam, 16 - x1 - 0.1F, 16 - x1 + 0.05F, y, z, icon);
    }

    /** Pictogrammes 6 x 6 (comme ceux des cables) : coffre, goutte, nuage, eclair, histogramme, manometre, robot. */
    static final String[] ICON_ITEM = {".####.", "#....#", "######", "#.##.#", "#....#", "######"};
    static final String[] ICON_FLUID = {"..#...", "..##..", ".####.", "######", "######", ".####."};
    static final String[] ICON_GAS = {"......", "..##..", ".####.", "######", "######", "......"};
    static final String[] ICON_ENERGY = {"...##.", "..##..", ".####.", "..##..", ".##...", ".#...."};
    static final String[] ICON_DATA = {".....#", "...#.#", "...#.#", ".#.#.#", ".#.#.#", "######"};
    static final String[] ICON_PRESSURE = {".####.", "#....#", "#..#.#", "#.#..#", "#....#", ".####."};
    static final String[] ICON_ROBOT = {".####.", "#.##.#", "######", ".#..#.", "######", "#....#"};

    static String[] iconOf(BigMat fam) {
        return switch (fam) {
            case R_FAM_FLUID -> ICON_FLUID;
            case R_FAM_GAS -> ICON_GAS;
            case R_FAM_ENERGY -> ICON_ENERGY;
            case R_FAM_DATA -> ICON_DATA;
            case R_FAM_PRESSURE -> ICON_PRESSURE;
            default -> ICON_ITEM;
        };
    }

    /** Pictogramme sur une plaque de 2 x 2 px (pixels de 0,25 px, rangees fusionnees), de xa a xb en epaisseur. */
    static void icon(RailMesh m, BigMat mat, float xa, float xb, float y, float z, String[] rows) {
        float p = 0.25F;
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) != '#') {
                    c++;
                    continue;
                }
                int e = c;
                while (e < row.length() && row.charAt(e) == '#') {
                    e++;
                }
                float yt = y + 2.0F - p - r * p;
                m.box(xa, yt - p, z + p + c * p, xb, yt, z + p + e * p, mat);
                c = e;
            }
        }
    }

    /**
     * Jauge de remplissage sur les deux flancs : panneau de x0 a x1 (cote +x, en miroir de l'autre cote), partie pleine lumineuse, le
     * reste en verre sombre.
     */
    static void gaugeAt(RailMesh m, BigMat fam, float x0, float x1, float y0, float y1, float z0, float fill) {
        float h = (y1 - y0 - 0.4F) * Math.max(0, Math.min(1, fill));
        h = Math.max(0.1F, h);
        for (int s = 0; s < 2; s++) {
            float a = s == 0 ? x0 : 16 - x1, b = s == 0 ? x1 : 16 - x0;
            m.box(a, y0, z0, b, y1, z0 + 1.4F, BigMat.R_DARK);
            float fa = s == 0 ? x1 - 0.05F : 16 - x1 - 0.1F, fb = fa + 0.15F;
            m.box(fa, y0 + 0.2F, z0 + 0.2F, fb, y0 + 0.2F + h, z0 + 1.2F, fam);
            if (y0 + 0.2F + h < y1 - 0.3F) {
                m.box(fa, y0 + 0.2F + h, z0 + 0.2F, fb, y1 - 0.2F, z0 + 1.2F, BigMat.R_GLASS_D);
            }
        }
    }

    // ------------------------------------------------------------------ vehicules

    /** Partie fixe (les roues dans leurs groupes). {@code fill} 0..1 et {@code bulk} : teinte du vrac (tremie, wagonnet). */
    public static RailMesh build(CarType type, int tier, float fill, int bulk) {
        RailMesh m = new RailMesh();
        switch (type) {
            case STEAM -> steam(m, tier);
            case TENDER -> tender(m, tier, fill);
            case BOXCAR -> boxcar(m, tier, fill);
            case HOPPER -> hopper(m, tier, fill, bulk);
            case DRAISINE -> draisine(m);
            case FIRELESS -> fireless(m, tier);
            case DIESEL -> diesel(m, tier);
            case GAS_LOCO -> gasLoco(m, tier);
            case ELECTRIC -> electric(m, tier);
            case NUCLEAR -> nuclear(m, tier);
            case MAGLEV -> maglev(m, tier);
            case TANK -> tank(m, tier, fill, false);
            case GAS_TANK -> tank(m, tier, fill, true);
            case BATTERY -> battery(m, tier, fill);
            case SERVER -> server(m, tier, fill);
            case AIR -> airCar(m, tier, fill);
            case FLAT -> flat(m, tier);
            case ROBOT_VAN -> robotVan(m, tier, fill);
            case PASSENGER -> passenger(m, tier);
            case LAYER -> layer(m, tier, fill);
            default -> mineWagon(m, fill, bulk);
        }
        return m;
    }

    /**
     * Partie dynamique : bielles (vapeur, vapeur sans foyer), balancier (draisine), pantographe (electrique). {@code odo} : distance
     * parcourue (px) ; {@code flags} : bit 0 = en traction, bit 3 = sous tension (pantographe leve).
     */
    public static RailMesh dynamic(CarType type, int tier, float odo, float pump, int flags) {
        RailMesh m = new RailMesh();
        if (type == CarType.STEAM || type == CarType.FIRELESS) {
            steamRods(m, odo);
        } else if (type == CarType.ELECTRIC) {
            pantograph(m, (flags & 8) != 0);
        } else if (type == CarType.DRAISINE) {
            float cy = RT + 1.7F;
            m.translate(0, cy + 7.0F, 8.0F);
            m.rotate('x', 0, 0, 0, 18 * (float) Math.sin(pump));
            m.box(7.6F, -0.4F, -4.6F, 8.4F, 0.4F, 4.6F, BigMat.R_DARK);
            m.box(4.0F, -0.3F, -4.6F, 12.0F, 0.3F, -4.0F, BigMat.R_CHROME);
            m.box(4.0F, -0.3F, 4.0F, 12.0F, 0.3F, 4.6F, BigMat.R_CHROME);
            m.pop();
            m.pop();
        }
        return m;
    }

    static void steam(RailMesh m, int tier) {
        BigMat lv = livery(tier);
        float r = 2.4F, cy = RT + r;
        wheelset(m, 5.2F, r, 3.9F, 5.3F, 10.7F, 12.1F);
        wheelset(m, 10.2F, r, 3.9F, 5.3F, 10.7F, 12.1F);
        m.box(3.6F, cy + 0.6F, 0.6F, 12.4F, cy + 1.8F, 15.6F, BigMat.R_FRAME);
        m.box(2.2F, cy + 1.8F, 0.6F, 13.8F, cy + 2.3F, 13.2F, BigMat.R_DARK);
        m.box(2.15F, cy + 1.75F, 0.65F, 2.6F, cy + 2.35F, 13.15F, tier(tier));
        m.box(13.4F, cy + 1.75F, 0.65F, 13.85F, cy + 2.35F, 13.15F, tier(tier));
        m.box(2.6F, cy - 0.6F, 15.2F, 13.4F, cy + 1.85F, 16.0F, BigMat.R_RED);
        buffers(m, 16.0F, cy + 0.6F, 1);
        coupler(m, 0.6F, cy + 0.6F, -1);
        for (float x : new float[]{2.4F, 13.6F}) {
            m.cyl('z', x, cy + 1.4F, 1.1F, 11.2F, 14.6F, BigMat.R_DARK, 10);
            m.cyl('z', x, cy + 1.4F, 1.25F, 14.4F, 14.9F, BigMat.R_CHROME, 10);
        }
        float bc = cy + 5.4F;
        m.cyl('z', 8, bc, 3.4F, 3.6F, 13.0F, lv, 16);
        for (float z : new float[]{5.4F, 8.4F, 11.4F}) {
            m.cyl('z', 8, bc, 3.55F, z, z + 0.5F, BigMat.R_BRASS, 16, false, 0.4F);
        }
        m.cyl('z', 8, bc, 3.55F, 13.0F, 15.2F, BigMat.R_BLACK, 16);
        m.cyl('z', 8, bc, 2.6F, 15.2F, 15.5F, BigMat.R_DARK, 14);
        m.box(7.7F, bc - 0.3F, 15.5F, 8.3F, bc + 0.3F, 15.9F, BigMat.R_CHROME);
        m.cyl('y', 8, 14.2F, 0.95F, bc + 3.0F, bc + 6.8F, BigMat.R_BLACK, 10);
        m.cyl('y', 8, 14.2F, 1.3F, bc + 6.4F, bc + 7.1F, BigMat.R_BLACK, 10);
        m.cyl('y', 8, 9.2F, 1.25F, bc + 3.1F, bc + 4.8F, BigMat.R_BRASS, 12);
        m.cyl('y', 8, 6.6F, 1.0F, bc + 3.1F, bc + 4.2F, lv, 12);
        m.cyl('y', 8.9F, 4.9F, 0.25F, bc + 3.2F, bc + 4.6F, BigMat.R_BRASS, 6);
        m.box(7.3F, bc + 1.9F, 15.4F, 8.7F, bc + 3.0F, 16.0F, BigMat.R_LAMP);
        float cb = cy + 2.3F;
        m.box(2.6F, cb, 0.6F, 13.4F, cb + 6.6F, 1.0F, lv);
        for (float[] xs : new float[][]{{2.6F, 3.0F}, {13.0F, 13.4F}}) {
            m.box(xs[0], cb, 0.6F, xs[1], cb + 2.6F, 3.8F, lv);
            m.box(xs[0], cb + 5.2F, 0.6F, xs[1], cb + 6.6F, 3.8F, lv);
            m.box(xs[0], cb + 2.6F, 2.8F, xs[1], cb + 5.2F, 3.8F, lv);
        }
        m.box(2.6F, cb, 3.6F, 13.4F, cb + 6.6F, 4.0F, lv);
        m.box(4.0F, cb + 3.4F, 3.55F, 6.2F, cb + 5.4F, 4.05F, BigMat.R_GLASS_D);
        m.box(9.8F, cb + 3.4F, 3.55F, 12.0F, cb + 5.4F, 4.05F, BigMat.R_GLASS_D);
        m.box(2.1F, cb + 6.6F, 0.2F, 13.9F, cb + 7.2F, 4.4F, BigMat.R_DARK);
        m.box(3.4F, cb + 7.2F, 0.6F, 12.6F, cb + 7.5F, 4.0F, BigMat.R_DARK);
        m.box(5.0F, cy + 0.6F, 1.2F, 11.0F, cy + 1.7F, 3.0F, BigMat.R_LAMP_O);
    }

    /** Bielles d'accouplement et motrices (manivelles decalees d'un quart de tour d'un cote a l'autre). */
    static void steamRods(RailMesh m, float odo) {
        float r = 2.4F, cy = RT + r;
        double th = Math.toRadians(odo * spinFactor(r));
        for (float[] xs : new float[][]{{3.3F, -1}, {12.7F, 1}}) {
            float x = xs[0];
            double ph = th + (xs[1] < 0 ? 0 : Math.PI / 2);
            float dz = (float) (1.3 * Math.cos(ph)), dy = (float) (1.3 * Math.sin(ph));
            float za = 5.2F + dz, zb = 10.2F + dz;
            m.box(x - 0.25F, cy + dy - 0.3F, za - 0.3F, x + 0.25F, cy + dy + 0.3F, zb + 0.3F, BigMat.R_CHROME);
            m.rodYZ(x - 0.2F, x + 0.2F, cy + dy, zb, cy + 1.4F, 13.2F, 0.5F, BigMat.R_STEEL);
        }
    }

    static void tender(RailMesh m, int tier, float fill) {
        BigMat lv = livery(tier);
        float r = 1.8F, cy = RT + r;
        wheelset(m, 3.6F, r);
        wheelset(m, 12.4F, r);
        m.box(3.4F, cy + 0.4F, 0.6F, 12.6F, cy + 1.6F, 15.4F, BigMat.R_FRAME);
        buffers(m, 0.6F, cy + 1.0F, -1);
        coupler(m, 15.4F, cy + 1.0F, 1);
        m.box(2.6F, cy + 1.6F, 0.8F, 13.4F, cy + 7.0F, 15.2F, lv);
        m.box(2.4F, cy + 7.0F, 0.6F, 13.6F, cy + 7.5F, 15.4F, BigMat.R_DARK);
        m.box(2.4F, cy + 1.6F, 0.6F, 13.6F, cy + 2.0F, 15.4F, tier(tier));
        float[][] heap = {{9, 1.4F}, {10.5F, 2.4F}, {12, 3.0F}, {13.5F, 2.6F}};
        for (float[] h : heap) {
            m.box(3.2F, cy + 7.5F, h[0], 12.8F, cy + 7.5F + h[1], h[0] + 1.6F, BigMat.R_COAL);
        }
        m.cyl('y', 8, 4.2F, 1.4F, cy + 7.5F, cy + 8.2F, BigMat.R_BRASS, 12);
        gauge(m, BigMat.R_FAM_FLUID, cy + 2.6F, cy + 6.4F, 2.0F, fill);
    }

    static void boxcar(RailMesh m, int tier, float fill) {
        float y = chassis(m);
        BigMat body = tier == 0 ? BigMat.R_PLANKS : BigMat.R_STEEL;
        m.box(2.6F, y, 0.8F, 13.4F, y + 6.2F, 15.2F, body);
        for (float z : new float[]{0.7F, 14.7F}) {
            m.box(2.5F, y, z, 13.5F, y + 6.2F, z + 0.6F, BigMat.R_DARK);
        }
        m.box(2.5F, y + 6.0F, 0.6F, 13.5F, y + 6.4F, 15.4F, BigMat.R_DARK);
        m.box(3.5F, y + 6.4F, 0.8F, 12.5F, y + 6.9F, 15.2F, BigMat.R_DARK);
        m.box(2.45F, y + 0.2F, 0.8F, 13.55F, y + 0.6F, 15.2F, tier(tier));
        BigMat door = tier == 0 ? BigMat.R_PLANKS : BigMat.R_STEEL_L;
        m.box(13.4F, y + 0.6F, 5.2F, 13.8F, y + 5.6F, 10.8F, door);
        m.box(2.2F, y + 0.6F, 5.2F, 2.6F, y + 5.6F, 10.8F, door);
        m.box(13.35F, y + 5.6F, 4.6F, 13.9F, y + 6.0F, 13.6F, BigMat.R_STEEL);
        gauge(m, BigMat.R_FAM_ITEM, y + 1.2F, y + 5.0F, 2.4F, fill);
        logo(m, BigMat.R_FAM_ITEM, y + 2.0F, 11.8F);
    }

    static void hopper(RailMesh m, int tier, float fill, int bulk) {
        float y = chassis(m);
        m.box(3.0F, y, 2.4F, 13.0F, y + 1.2F, 13.6F, BigMat.R_DARK);
        for (float z0 : new float[]{0.8F, 13.6F}) {
            m.box(2.6F, y + 1.2F, z0, 13.4F, y + 6.4F, z0 + 1.6F, BigMat.R_STEEL);
        }
        for (float x0 : new float[]{2.6F, 12.8F}) {
            m.box(x0, y + 1.2F, 0.8F, x0 + 0.6F, y + 6.4F, 15.2F, BigMat.R_STEEL);
        }
        for (float z : new float[]{4.4F, 8.0F, 11.6F}) {
            //Raidisseurs sur les flancs
            m.box(2.3F, y + 1.2F, z, 2.6F, y + 6.4F, z + 0.5F, tier(tier));
            m.box(13.4F, y + 1.2F, z, 13.7F, y + 6.4F, z + 0.5F, tier(tier));
        }
        m.box(2.4F, y + 6.2F, 0.7F, 13.6F, y + 6.6F, 1.0F, tier(tier));
        m.box(2.4F, y + 6.2F, 15.0F, 13.6F, y + 6.6F, 15.3F, tier(tier));
        bulk(m, y + 1.2F, fill, bulk, 3.2F, 12.8F, 2.4F, 13.6F, 4.0F);
        for (float z : new float[]{5.0F, 9.4F}) {
            m.box(6.6F, y - 1.4F, z, 9.4F, y, z + 1.6F, BigMat.R_DARK);
        }
        gauge(m, BigMat.R_FAM_ITEM, y + 1.6F, y + 5.6F, 1.6F, fill);
    }

    /** Tas de vrac : trois paliers, teinte de l'objet charge (0 : charbon). */
    static void bulk(RailMesh m, float y, float fill, int color, float x0, float x1, float z0, float z1, float depth) {
        if (fill <= 0.01F) {
            return;
        }
        float h = depth * Math.min(1, fill);
        BigMat mat = color == 0 ? BigMat.R_COAL : BigMat.R_BULK;
        int argb = color == 0 ? 0xFFFFFFFF : 0xFF000000 | color;
        m.tinted(x0, y, z0, x1, y + h, z1, mat, argb);
        float cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, hx = (x1 - x0) / 2, hz = (z1 - z0) / 2;
        m.tinted(cx - hx * 0.75F, y + h, cz - hz * 0.7F, cx + hx * 0.75F, y + h + 0.8F, cz + hz * 0.7F, mat, argb);
        m.tinted(cx - hx * 0.42F, y + h + 0.8F, cz - hz * 0.36F, cx + hx * 0.42F, y + h + 1.4F, cz + hz * 0.36F, mat, argb);
    }

    static void draisine(RailMesh m) {
        float r = 1.7F, cy = RT + r;
        wheelset(m, 4.0F, r);
        wheelset(m, 12.0F, r);
        m.box(3.4F, cy + 0.4F, 1.4F, 12.6F, cy + 1.4F, 14.6F, BigMat.R_FRAME);
        m.box(2.6F, cy + 1.4F, 1.0F, 13.4F, cy + 2.0F, 15.0F, BigMat.R_PLANKS);
        m.box(7.4F, cy + 2.0F, 7.2F, 8.6F, cy + 6.6F, 8.8F, BigMat.R_RED);
        m.box(6.8F, cy + 6.3F, 7.6F, 9.2F, cy + 7.0F, 8.4F, BigMat.R_DARK);
        buffers(m, 15.0F, cy + 1.0F, 1);
        buffers(m, 1.0F, cy + 1.0F, -1);
        //Rambardes
        for (float x : new float[]{2.8F, 13.0F}) {
            m.box(x, cy + 2.0F, 1.4F, x + 0.3F, cy + 4.6F, 1.7F, BigMat.R_STEEL);
            m.box(x, cy + 2.0F, 14.3F, x + 0.3F, cy + 4.6F, 14.6F, BigMat.R_STEEL);
            m.box(x, cy + 4.3F, 1.4F, x + 0.3F, cy + 4.6F, 14.6F, BigMat.R_CHROME);
        }
    }

    /** Wagonnet de mine : bac de bois cercle de fer sur deux petits essieux. */
    static void mineWagon(RailMesh m, float fill, int bulk) {
        float r = 1.5F, cy = RT + r;
        wheelset(m, 4.2F, r, 4.0F, 5.0F, 11.0F, 12.0F);
        wheelset(m, 11.8F, r, 4.0F, 5.0F, 11.0F, 12.0F);
        m.box(4.2F, cy + 0.3F, 1.8F, 11.8F, cy + 1.2F, 14.2F, BigMat.R_FRAME);
        float y = cy + 1.2F;
        m.box(3.4F, y, 2.4F, 12.6F, y + 0.8F, 13.6F, BigMat.R_PLANKS);
        for (float[] z : new float[][]{{2.0F, 2.8F}, {13.2F, 14.0F}}) {
            m.box(3.0F, y + 0.8F, z[0], 13.0F, y + 5.6F, z[1], BigMat.R_PLANKS);
        }
        for (float[] x : new float[][]{{2.6F, 3.4F}, {12.6F, 13.4F}}) {
            m.box(x[0], y + 0.8F, 2.0F, x[1], y + 5.6F, 14.0F, BigMat.R_PLANKS);
        }
        for (float yy : new float[]{y + 1.4F, y + 4.4F}) {
            //Cerclages de fer : quatre bandes autour du bac
            m.box(2.45F, yy, 1.85F, 13.55F, yy + 0.6F, 2.05F, BigMat.R_STEEL);
            m.box(2.45F, yy, 13.95F, 13.55F, yy + 0.6F, 14.15F, BigMat.R_STEEL);
            m.box(2.45F, yy, 2.05F, 2.65F, yy + 0.6F, 13.95F, BigMat.R_STEEL);
            m.box(13.35F, yy, 2.05F, 13.55F, yy + 0.6F, 13.95F, BigMat.R_STEEL);
        }
        for (float x : new float[]{2.4F, 13.0F}) {
            m.box(x, y + 2.2F, 7.0F, x + 0.6F, y + 3.6F, 9.0F, BigMat.R_DARK);
        }
        bulk(m, y + 0.8F, fill, bulk, 3.4F, 12.6F, 2.8F, 13.2F, 3.8F);
        buffers(m, 14.6F, cy + 0.8F, 1);
        buffers(m, 1.4F, cy + 0.8F, -1);
    }

    // ------------------------------------------------------------------ 0.20.2 : tractions

    /** Bogie a deux essieux (locomotives modernes), comme la maquette. */
    static void bogie(RailMesh m, float zc) {
        float r = 1.6F, cy = RT + r;
        wheelset(m, zc - 1.9F, r, 4.0F, 5.1F, 10.9F, 12.0F);
        wheelset(m, zc + 1.9F, r, 4.0F, 5.1F, 10.9F, 12.0F);
        for (float x : new float[]{3.1F, 12.0F}) {
            m.box(x, cy - 0.6F, zc - 3.2F, x + 0.9F, cy + 0.9F, zc + 3.2F, BigMat.R_FRAME);
        }
        for (float dz : new float[]{-1.9F, 1.9F}) {
            //Boites d'essieu
            m.box(2.8F, cy - 0.5F, zc + dz - 0.6F, 3.1F, cy + 0.5F, zc + dz + 0.6F, BigMat.R_DARK);
            m.box(12.9F, cy - 0.5F, zc + dz - 0.6F, 13.2F, cy + 0.5F, zc + dz + 0.6F, BigMat.R_DARK);
        }
        m.box(5.0F, cy + 0.6F, zc - 1.0F, 11.0F, cy + 1.6F, zc + 1.0F, BigMat.R_DARK);
    }

    /** Plancher des locomotives modernes : deux bogies, longerons d'epaisseur {@code h}, filet jaune, tampons. Renvoie le dessus. */
    static float locoDeck(RailMesh m, float h) {
        bogie(m, 3.6F);
        bogie(m, 12.4F);
        float y0 = RT + 3.2F;
        m.box(2.8F, y0, 0.4F, 13.2F, y0 + h, 15.6F, BigMat.R_FRAME);
        m.box(2.7F, y0 + 0.2F, 0.5F, 13.3F, y0 + 0.6F, 15.5F, BigMat.R_STRIPE);
        buffers(m, 15.4F, y0 + 0.6F, 1);
        buffers(m, 0.6F, y0 + 0.6F, -1);
        return y0 + h;
    }

    /** Cabine a l'avant (z 10,6 a 14,6) et capot court : diesel, gaz, nucleaire. */
    static void frontCab(RailMesh m, int tier, float yb) {
        BigMat lv = livery(tier);
        m.box(2.6F, yb, 10.6F, 13.4F, yb + 7.0F, 14.6F, lv);
        m.box(2.55F, yb + 3.6F, 11.4F, 13.45F, yb + 5.6F, 13.8F, BigMat.R_GLASS_D);
        m.box(3.6F, yb + 3.6F, 14.55F, 12.4F, yb + 5.8F, 14.7F, BigMat.R_GLASS_D);
        m.box(2.4F, yb + 7.0F, 10.4F, 13.6F, yb + 7.5F, 14.8F, BigMat.R_DARK);
        m.box(4.6F, yb, 14.6F, 11.4F, yb + 3.4F, 15.6F, lv);
        m.box(5.0F, yb + 2.2F, 15.55F, 6.6F, yb + 3.0F, 15.7F, BigMat.R_LAMP);
        m.box(9.4F, yb + 2.2F, 15.55F, 11.0F, yb + 3.0F, 15.7F, BigMat.R_LAMP);
        m.box(2.55F, yb + 0.1F, 10.65F, 13.45F, yb + 0.5F, 14.55F, tier(tier));
        //Feux arriere de la cabine (cote capot)
        m.box(2.5F, yb + 6.0F, 10.8F, 2.6F, yb + 6.6F, 11.4F, BigMat.R_LAMP_R);
        m.box(13.4F, yb + 6.0F, 10.8F, 13.5F, yb + 6.6F, 11.4F, BigMat.R_LAMP_R);
    }

    /** Vapeur sans foyer : chassis et bielles de la vapeur, gros reservoir de vapeur calorifuge cercle de laiton, ni cheminee ni foyer. */
    static void fireless(RailMesh m, int tier) {
        BigMat lv = livery(tier);
        float r = 2.4F, cy = RT + r;
        wheelset(m, 5.2F, r, 3.9F, 5.3F, 10.7F, 12.1F);
        wheelset(m, 10.2F, r, 3.9F, 5.3F, 10.7F, 12.1F);
        m.box(3.6F, cy + 0.6F, 0.6F, 12.4F, cy + 1.8F, 15.6F, BigMat.R_FRAME);
        m.box(2.2F, cy + 1.8F, 0.6F, 13.8F, cy + 2.3F, 13.2F, BigMat.R_DARK);
        m.box(2.15F, cy + 1.75F, 0.65F, 2.6F, cy + 2.35F, 13.15F, tier(tier));
        m.box(13.4F, cy + 1.75F, 0.65F, 13.85F, cy + 2.35F, 13.15F, tier(tier));
        m.box(2.6F, cy - 0.6F, 15.2F, 13.4F, cy + 1.85F, 16.0F, BigMat.R_RED);
        buffers(m, 16.0F, cy + 0.6F, 1);
        coupler(m, 0.6F, cy + 0.6F, -1);
        for (float x : new float[]{2.4F, 13.6F}) {
            m.cyl('z', x, cy + 1.4F, 1.1F, 11.2F, 14.6F, BigMat.R_DARK, 10);
            m.cyl('z', x, cy + 1.4F, 1.25F, 14.4F, 14.9F, BigMat.R_CHROME, 10);
        }
        float bc = cy + 5.4F, rr = 3.7F;
        //Reservoir calorifuge et fond bombe
        m.cyl('z', 8, bc, rr, 4.0F, 14.4F, lv, 16);
        m.cyl('z', 8, bc, 3.0F, 14.4F, 14.9F, lv, 16);
        m.cyl('z', 8, bc, 2.0F, 14.9F, 15.2F, BigMat.R_DARK, 14);
        for (float z : new float[]{5.4F, 7.8F, 10.2F, 12.6F}) {
            m.cyl('z', 8, bc, rr + 0.15F, z, z + 0.45F, BigMat.R_BRASS, 16, false, 0.4F);
        }
        m.cyl('z', 8, bc, rr + 0.15F, 13.9F, 14.35F, BigMat.R_BRASS, 16, false, 0.4F);
        //Berceaux sur le tablier
        for (float z : new float[]{5.8F, 11.6F}) {
            m.box(4.4F, cy + 2.3F, z, 11.6F, bc - 2.5F, z + 0.9F, BigMat.R_DARK);
        }
        //Petit dome, soupape, raccord de remplissage (au milieu, sous le bras de la borne)
        m.cyl('y', 8, 10.8F, 1.1F, bc + rr - 0.3F, bc + rr + 1.1F, BigMat.R_BRASS, 12);
        m.cyl('y', 8, 10.8F, 1.3F, bc + rr + 0.9F, bc + rr + 1.3F, BigMat.R_BRASS, 12);
        m.cyl('y', 8, 12.9F, 0.35F, bc + rr - 0.3F, bc + rr + 1.4F, BigMat.R_CHROME, 8);
        m.box(7.3F, bc + rr - 0.3F, 7.4F, 8.7F, bc + rr + 0.5F, 8.6F, BigMat.R_DARK);
        m.cyl('y', 8, 8, 0.45F, bc + rr + 0.5F, bc + rr + 0.9F, BigMat.R_CHROME, 8);
        m.cyl('y', 8, 6.6F, 0.7F, bc + rr + 0.2F, bc + rr + 0.45F, BigMat.R_RED, 10, false, 0.3F);
        m.box(7.85F, bc + rr - 0.3F, 6.5F, 8.15F, bc + rr + 0.3F, 7.4F, BigMat.R_STEEL);
        //Fanal
        m.box(7.2F, bc + 2.1F, 14.6F, 8.8F, bc + 2.9F, 15.3F, BigMat.R_LAMP);
        //Cabine (comme la vapeur)
        float cb = cy + 2.3F;
        m.box(2.6F, cb, 0.6F, 13.4F, cb + 6.6F, 1.0F, lv);
        for (float[] xs : new float[][]{{2.6F, 3.0F}, {13.0F, 13.4F}}) {
            m.box(xs[0], cb, 0.6F, xs[1], cb + 2.6F, 3.8F, lv);
            m.box(xs[0], cb + 5.2F, 0.6F, xs[1], cb + 6.6F, 3.8F, lv);
            m.box(xs[0], cb + 2.6F, 2.8F, xs[1], cb + 5.2F, 3.8F, lv);
        }
        m.box(2.6F, cb, 3.6F, 13.4F, cb + 6.6F, 4.0F, lv);
        m.box(4.0F, cb + 3.4F, 3.55F, 6.2F, cb + 5.4F, 4.05F, BigMat.R_GLASS_D);
        m.box(9.8F, cb + 3.4F, 3.55F, 12.0F, cb + 5.4F, 4.05F, BigMat.R_GLASS_D);
        m.box(2.1F, cb + 6.6F, 0.2F, 13.9F, cb + 7.2F, 4.4F, BigMat.R_DARK);
        m.box(3.4F, cb + 7.2F, 0.6F, 12.6F, cb + 7.5F, 4.0F, BigMat.R_DARK);
        //Manometre sur la facade de la cabine
        m.cyl('z', 12.5F, cb + 2.2F, 0.75F, 4.0F, 4.2F, BigMat.R_BLACK, 12);
        m.cyl('z', 12.5F, cb + 2.2F, 0.6F, 4.2F, 4.3F, BigMat.R_WHITE, 12);
        m.box(12.45F, cb + 2.2F, 4.3F, 12.55F, cb + 2.7F, 4.35F, BigMat.R_RED);
    }

    static void diesel(RailMesh m, int tier) {
        BigMat lv = livery(tier);
        float yb = locoDeck(m, 1.3F), y0 = yb - 1.3F;
        m.box(4.2F, yb, 1.0F, 11.8F, yb + 5.4F, 10.6F, lv);
        for (float z : new float[]{2.0F, 5.2F, 8.2F}) {
            //Persiennes des radiateurs
            m.box(4.15F, yb + 1.2F, z, 11.85F, yb + 4.2F, z + 2.2F, BigMat.R_GRILL);
        }
        m.box(4.6F, yb + 5.4F, 1.6F, 11.4F, yb + 5.8F, 10.0F, BigMat.R_DARK);
        m.cyl('y', 8, 3.0F, 1.5F, yb + 5.8F, yb + 6.1F, BigMat.R_GRILL, 14);
        //Echappement
        m.box(7.2F, yb + 5.8F, 6.6F, 8.8F, yb + 7.0F, 7.6F, BigMat.R_BLACK);
        m.box(7.0F, yb + 7.0F, 6.4F, 9.0F, yb + 7.3F, 7.8F, BigMat.R_DARK);
        m.box(4.8F, yb + 1.0F, 0.9F, 6.0F, yb + 1.6F, 1.05F, BigMat.R_LAMP_R);
        m.box(10.0F, yb + 1.0F, 0.9F, 11.2F, yb + 1.6F, 1.05F, BigMat.R_LAMP_R);
        frontCab(m, tier, yb);
        //Mains courantes sur le plancher
        for (float x : new float[]{3.0F, 12.8F}) {
            m.box(x, yb + 2.4F, 1.0F, x + 0.2F, yb + 2.6F, 10.6F, BigMat.R_CHROME);
            for (float z : new float[]{1.0F, 5.6F, 10.2F}) {
                m.box(x, yb, z, x + 0.2F, yb + 2.4F, z + 0.2F, BigMat.R_CHROME);
            }
        }
        fuelTank(m, y0, BigMat.R_FAM_FLUID);
    }

    /** Reservoir sous le chassis, entre les bogies, avec sa fenetre et le bouchon de remplissage sur le longeron. */
    static void fuelTank(RailMesh m, float y0, BigMat fam) {
        m.box(4.4F, y0 - 2.0F, 7.2F, 11.6F, y0, 8.8F, BigMat.R_DARK);
        m.box(11.6F, y0 - 1.6F, 7.5F, 11.75F, y0 - 0.4F, 8.5F, fam);
        m.box(4.25F, y0 - 1.6F, 7.5F, 4.4F, y0 - 0.4F, 8.5F, fam);
        m.box(13.3F, y0 + 0.7F, 7.6F, 13.5F, y0 + 1.2F, 8.4F, BigMat.R_CHROME);
        m.box(2.5F, y0 + 0.7F, 7.6F, 2.7F, y0 + 1.2F, 8.4F, BigMat.R_CHROME);
    }

    static void gasLoco(RailMesh m, int tier) {
        BigMat lv = livery(tier);
        float yb = locoDeck(m, 1.3F), y0 = yb - 1.3F;
        //Caisse basse (turbine)
        m.box(3.6F, yb, 1.0F, 12.4F, yb + 2.2F, 10.6F, lv);
        m.box(3.55F, yb + 0.5F, 2.0F, 12.45F, yb + 1.7F, 9.4F, BigMat.R_GRILL);
        m.box(4.4F, yb + 0.6F, 0.9F, 5.6F, yb + 1.2F, 1.05F, BigMat.R_LAMP_R);
        m.box(10.4F, yb + 0.6F, 0.9F, 11.6F, yb + 1.2F, 1.05F, BigMat.R_LAMP_R);
        //Bouteilles de gaz sur le toit
        for (float x : new float[]{5.7F, 10.3F}) {
            m.cyl('z', x, yb + 4.3F, 2.0F, 1.4F, 10.0F, BigMat.R_WHITE, 14);
            m.cyl('z', x, yb + 4.3F, 1.4F, 1.0F, 1.4F, BigMat.R_WHITE, 12);
            m.cyl('z', x, yb + 4.3F, 0.5F, 0.6F, 1.0F, BigMat.R_CHROME, 8);
            m.cyl('z', x, yb + 4.3F, 2.12F, 3.4F, 3.8F, tier(tier), 14, false, 0.3F);
            m.cyl('z', x, yb + 4.3F, 2.12F, 7.6F, 8.0F, tier(tier), 14, false, 0.3F);
            m.cyl('z', x, yb + 4.3F, 2.1F, 5.3F, 6.1F, BigMat.R_FAM_GAS, 14, false, 0.25F);
        }
        for (float z : new float[]{2.4F, 6.0F, 9.2F}) {
            m.box(4.4F, yb + 2.2F, z, 11.6F, yb + 2.6F, z + 0.6F, BigMat.R_DARK);
        }
        //Collecteur et soupape
        m.box(5.7F, yb + 6.0F, 4.4F, 10.3F, yb + 6.5F, 5.2F, BigMat.R_CHROME);
        m.cyl('y', 8, 4.8F, 0.5F, yb + 6.5F, yb + 7.4F, BigMat.R_CHROME, 8);
        m.cyl('y', 8, 4.8F, 0.7F, yb + 7.4F, yb + 7.7F, BigMat.R_RED, 10);
        frontCab(m, tier, yb);
        fuelTank(m, y0, BigMat.R_FAM_GAS);
    }

    static void electric(RailMesh m, int tier) {
        BigMat lv = livery(tier);
        float yb = locoDeck(m, 1.0F);
        m.box(2.6F, yb, 1.2F, 13.4F, yb + 7.4F, 14.8F, lv);
        //Nez et cabines aux deux bouts
        m.box(3.2F, yb, 14.8F, 12.8F, yb + 4.0F, 15.6F, lv);
        m.box(3.4F, yb + 4.0F, 14.8F, 12.6F, yb + 6.8F, 15.4F, BigMat.R_GLASS_D);
        m.box(4.0F, yb + 1.6F, 14.8F, 5.6F, yb + 2.4F, 15.65F, BigMat.R_LAMP);
        m.box(10.4F, yb + 1.6F, 14.8F, 12.0F, yb + 2.4F, 15.65F, BigMat.R_LAMP);
        m.box(3.2F, yb, 0.4F, 12.8F, yb + 4.0F, 1.2F, lv);
        m.box(3.4F, yb + 4.0F, 0.6F, 12.6F, yb + 6.8F, 1.2F, BigMat.R_GLASS_D);
        m.box(4.0F, yb + 1.6F, 0.35F, 5.6F, yb + 2.4F, 1.2F, BigMat.R_LAMP);
        m.box(10.4F, yb + 1.6F, 0.35F, 12.0F, yb + 2.4F, 1.2F, BigMat.R_LAMP);
        //Filet blanc et bande de tier
        m.box(2.55F, yb + 0.6F, 1.25F, 13.45F, yb + 1.1F, 14.75F, BigMat.R_WHITE);
        m.box(2.55F, yb + 1.1F, 1.25F, 13.45F, yb + 1.5F, 14.75F, tier(tier));
        for (float z : new float[]{3.0F, 6.0F, 9.0F}) {
            m.box(2.5F, yb + 3.0F, z, 13.5F, yb + 5.4F, z + 2.0F, BigMat.R_GRILL);
        }
        //Fenetre de l'energie et logo
        logoAt(m, BigMat.R_FAM_ENERGY, ICON_ENERGY, 13.3F, 13.5F, yb + 3.2F, 11.6F);
        //Toit, isolateurs, chassis du pantographe
        m.box(3.0F, yb + 7.4F, 1.6F, 13.0F, yb + 7.9F, 14.4F, BigMat.R_DARK);
        for (float z : new float[]{4.0F, 12.0F}) {
            for (float x : new float[]{5.6F, 10.4F}) {
                m.cyl('y', x, z, 0.45F, yb + 7.9F, yb + 8.6F, BigMat.R_WHITE, 8);
                m.cyl('y', x, z, 0.7F, yb + 8.1F, yb + 8.3F, BigMat.R_WHITE, 8);
            }
        }
        for (float x : new float[]{5.2F, 10.0F}) {
            m.box(x, yb + 8.6F, 3.4F, x + 0.8F, yb + 8.9F, 12.6F, BigMat.R_DARK);
        }
        //Disjoncteur
        m.box(6.6F, yb + 7.9F, 13.0F, 9.4F, yb + 8.8F, 14.2F, BigMat.R_DARK);
        m.cyl('y', 8, 13.6F, 0.3F, yb + 8.8F, yb + 9.6F, BigMat.R_WHITE, 8);
    }

    /** Pantographe en losange : leve, l'archet touche le fil de contact ({@link InfraModels#WIRE_Y}) ; abaisse, replie sur le toit. */
    static void pantograph(RailMesh m, boolean up) {
        float base = RT + 3.2F + 1.0F + 8.9F;
        float top = up ? InfraModels.WIRE_Y - 0.4F : base + 1.3F;
        float zc = 8;
        for (float s : new float[]{-1, 1}) {
            float za = zc + s * 2.6F;
            float zm = up ? zc + s * 1.5F : zc + s * 3.7F, ym = up ? (base + top) / 2 + 0.8F : base + 0.8F;
            for (float x : new float[]{5.6F, 10.4F}) {
                m.rodYZ(x - 0.15F, x + 0.15F, base, za, ym, zm, 0.3F, BigMat.R_CHROME);
                m.rodYZ(x - 0.15F, x + 0.15F, ym, zm, top, zc, 0.3F, BigMat.R_CHROME);
            }
            //Genouilleres (traverses)
            m.box(5.5F, ym - 0.12F, zm - 0.12F, 10.5F, ym + 0.12F, zm + 0.12F, BigMat.R_STEEL);
            m.box(5.3F, base - 0.1F, za - 0.3F, 6.0F, base + 0.3F, za + 0.3F, BigMat.R_DARK);
            m.box(10.0F, base - 0.1F, za - 0.3F, 10.7F, base + 0.3F, za + 0.3F, BigMat.R_DARK);
        }
        //Archet et cornes
        m.box(5.5F, top - 0.3F, zc - 0.2F, 10.5F, top, zc + 0.2F, BigMat.R_STEEL);
        m.box(4.6F, top, zc - 0.5F, 11.4F, top + 0.4F, zc + 0.5F, BigMat.R_COPPER);
        m.box(3.8F, top - 0.5F, zc - 0.3F, 4.6F, top + 0.3F, zc + 0.3F, BigMat.R_CHROME);
        m.box(11.4F, top - 0.5F, zc - 0.3F, 12.2F, top + 0.3F, zc + 0.3F, BigMat.R_CHROME);
    }

    static void nuclear(RailMesh m, int tier) {
        float yb = locoDeck(m, 1.4F);
        float c = yb + 3.8F;
        //Cuve blindee
        m.cyl('z', 8, c, 4.0F, 1.0F, 10.6F, BigMat.R_LEAD, 18);
        m.cyl('z', 8, c, 3.2F, 0.6F, 1.0F, BigMat.R_DARK, 16);
        for (float z : new float[]{2.6F, 5.8F, 9.0F}) {
            m.cyl('z', 8, c, 4.15F, z, z + 0.7F, BigMat.R_DARK, 18, false, 0.5F);
        }
        //Hublots a lueur bleue (entre les cerclages, des deux cotes)
        for (float z : new float[]{1.4F, 4.1F, 7.2F}) {
            m.box(11.8F, c - 0.6F, z, 12.25F, c + 0.6F, z + 1.1F, BigMat.R_GLOW_BLUE);
            m.box(3.75F, c - 0.6F, z, 4.2F, c + 0.6F, z + 1.1F, BigMat.R_GLOW_BLUE);
        }
        //Radiateurs
        for (float[] xs : new float[][]{{2.8F, 3.6F}, {12.4F, 13.2F}}) {
            m.box(xs[0], yb, 1.6F, xs[1], yb + 2.6F, 10.0F, BigMat.R_GRILL);
        }
        //Trefle
        m.box(6.6F, yb + 7.6F, 4.0F, 9.4F, yb + 8.3F, 7.6F, BigMat.R_STRIPE);
        m.box(7.6F, yb + 8.3F, 5.4F, 8.4F, yb + 8.5F, 6.2F, BigMat.R_BLACK);
        m.box(6.9F, yb + 8.3F, 4.4F, 7.5F, yb + 8.5F, 5.2F, BigMat.R_BLACK);
        m.box(8.5F, yb + 8.3F, 4.4F, 9.1F, yb + 8.5F, 5.2F, BigMat.R_BLACK);
        m.box(7.7F, yb + 8.3F, 6.4F, 8.3F, yb + 8.5F, 7.2F, BigMat.R_BLACK);
        frontCab(m, tier, yb);
    }

    /** Maglev : carrosserie profilee sans roues, au-dessus de la poutre MKIV ; jupes qui l'enveloppent et lueur sous la caisse. */
    static void maglev(RailMesh m, int tier) {
        BigMat lv = livery(tier);
        float y0 = RT + 1.0F;
        m.box(3.0F, y0, 0.6F, 13.0F, y0 + 1.4F, 15.4F, BigMat.R_DARK);
        m.box(3.4F, y0 - 0.2F, 1.0F, 12.6F, y0, 15.0F, BigMat.R_GLOW_CYAN);
        //Jupes de sustentation de part et d'autre de la poutre
        for (float[] xs : new float[][]{{2.5F, 3.1F}, {12.9F, 13.5F}}) {
            m.box(xs[0], 2.3F, 1.4F, xs[1], y0 + 1.4F, 14.6F, BigMat.R_DARK);
        }
        m.box(3.1F, 2.5F, 2.0F, 3.25F, 3.3F, 14.0F, BigMat.R_GLOW_CYAN);
        m.box(12.75F, 2.5F, 2.0F, 12.9F, 3.3F, 14.0F, BigMat.R_GLOW_CYAN);
        m.box(13.5F, 2.4F, 1.6F, 13.6F, 2.8F, 14.4F, BigMat.R_GLOW_CYAN);
        m.box(2.4F, 2.4F, 1.6F, 2.5F, 2.8F, 14.4F, BigMat.R_GLOW_CYAN);
        //Caisse
        m.box(2.6F, y0 + 1.4F, 1.2F, 13.4F, y0 + 7.4F, 13.4F, lv);
        //Nez profile
        m.box(3.0F, y0 + 1.4F, 13.4F, 13.0F, y0 + 5.2F, 14.6F, lv);
        m.box(3.2F, y0 + 5.2F, 13.4F, 12.8F, y0 + 7.0F, 14.1F, BigMat.R_GLASS_D);
        m.box(3.8F, y0 + 1.4F, 14.6F, 12.2F, y0 + 4.2F, 15.4F, lv);
        m.box(5.0F, y0 + 1.4F, 15.4F, 11.0F, y0 + 3.2F, 15.9F, lv);
        m.box(5.8F, y0 + 2.2F, 15.85F, 7.2F, y0 + 2.8F, 16.0F, BigMat.R_LAMP);
        m.box(8.8F, y0 + 2.2F, 15.85F, 10.2F, y0 + 2.8F, 16.0F, BigMat.R_LAMP);
        m.box(3.9F, y0 + 3.4F, 15.35F, 12.1F, y0 + 3.8F, 15.5F, BigMat.R_GLOW_CYAN);
        //Arriere
        m.box(2.9F, y0 + 1.4F, 0.4F, 13.1F, y0 + 6.6F, 1.2F, lv);
        m.box(4.2F, y0 + 1.4F, 0.05F, 11.8F, y0 + 4.4F, 0.4F, lv);
        m.box(5.0F, y0 + 3.0F, -0.05F, 6.4F, y0 + 3.6F, 0.1F, BigMat.R_LAMP_R);
        m.box(9.6F, y0 + 3.0F, -0.05F, 11.0F, y0 + 3.6F, 0.1F, BigMat.R_LAMP_R);
        //Bandeau vitre, ligne lumineuse, bande de tier
        m.box(2.55F, y0 + 4.2F, 1.8F, 13.45F, y0 + 5.8F, 12.8F, BigMat.R_GLASS_D);
        m.box(2.5F, y0 + 3.2F, 1.3F, 13.5F, y0 + 3.6F, 13.3F, BigMat.R_GLOW_CYAN);
        m.box(2.55F, y0 + 1.7F, 1.3F, 13.45F, y0 + 2.1F, 13.3F, tier(tier));
        //Toit
        m.box(3.0F, y0 + 7.4F, 1.6F, 13.0F, y0 + 7.9F, 12.8F, lv);
        m.box(4.0F, y0 + 7.9F, 2.4F, 12.0F, y0 + 8.2F, 12.0F, BigMat.R_DARK);
    }

    // ------------------------------------------------------------------ 0.20.2 : wagons

    /** Citerne (fluide : caisse sombre ; gaz : caisse blanche, deux soupapes). */
    static void tank(RailMesh m, int tier, float fill, boolean gas) {
        float y = chassis(m);
        float c = y + 3.85F;
        BigMat body = gas ? BigMat.R_WHITE : BigMat.R_DARK;
        BigMat fam = gas ? BigMat.R_FAM_GAS : BigMat.R_FAM_FLUID;
        m.box(4.3F, y, 2.6F, 11.7F, y + 0.8F, 4.6F, BigMat.R_FRAME);
        m.box(4.3F, y, 11.4F, 11.7F, y + 0.8F, 13.4F, BigMat.R_FRAME);
        m.cyl('z', 8, c, 3.9F, 1.4F, 14.6F, body, 18);
        m.cyl('z', 8, c, 3.2F, 0.8F, 1.4F, body, 16);
        m.cyl('z', 8, c, 3.2F, 14.6F, 15.2F, body, 16);
        for (float z : new float[]{4.0F, 11.6F}) {
            m.cyl('z', 8, c, 4.05F, z, z + 0.5F, tier(tier), 18, false, 0.4F);
        }
        m.cyl('z', 8, c, 4.06F, 7.0F, 9.0F, fam, 18, false, 0.3F);
        //Dome, soupape(s), passerelle
        m.cyl('y', 8, 8.0F, 1.5F, c + 3.6F, c + 4.8F, body, 12);
        m.cyl('y', 8, 8.0F, 0.4F, c + 4.8F, c + 6.0F, BigMat.R_CHROME, 6);
        if (gas) {
            for (float z : new float[]{3.0F, 13.0F}) {
                m.cyl('y', 8, z, 0.35F, c + 3.6F, c + 4.9F, BigMat.R_CHROME, 6);
                m.cyl('y', 8, z, 0.6F, c + 4.9F, c + 5.2F, BigMat.R_RED, 8);
            }
        }
        m.box(7.0F, c + 3.8F, 2.0F, 9.0F, c + 4.1F, 14.0F, BigMat.R_GRILL);
        //Jauge et logo sur les flancs de la cuve
        gaugeAt(m, fam, 11.3F, 12.15F, c - 1.9F, c + 1.9F, 5.1F, fill);
        logoAt(m, fam, iconOf(fam), 11.5F, 12.2F, c - 1.0F, 9.4F);
    }

    static void battery(RailMesh m, int tier, float fill) {
        float y = chassis(m);
        m.box(2.8F, y, 1.0F, 13.2F, y + 6.0F, 15.0F, BigMat.R_DARK);
        //Cellules : la partie allumee montre la charge
        float h = 4.0F * Math.max(0, Math.min(1, fill));
        for (float z : new float[]{1.6F, 4.4F, 7.2F, 10.0F}) {
            for (float[] xs : new float[][]{{13.15F, 13.35F}, {2.65F, 2.85F}}) {
                if (h > 0.05F) {
                    m.box(xs[0], y + 1.0F, z, xs[1], y + 1.0F + h, z + 1.6F, BigMat.R_FAM_ENERGY);
                }
                if (h < 3.95F) {
                    m.box(xs[0], y + 1.0F + h, z, xs[1], y + 5.0F, z + 1.6F, BigMat.R_GLASS_D);
                }
            }
        }
        logoAt(m, BigMat.R_FAM_ENERGY, ICON_ENERGY, 13.0F, 13.3F, y + 2.0F, 12.6F);
        for (float z = 2; z < 14; z += 2) {
            m.box(3.4F, y + 6.0F, z, 12.6F, y + 6.8F, z + 0.8F, BigMat.R_GRILL);
        }
        //Bornes de cuivre
        for (float x : new float[]{4.0F, 12.0F}) {
            m.cyl('y', x, 1.5F, 0.4F, y + 6.0F, y + 6.7F, BigMat.R_COPPER, 8);
            m.cyl('y', x, 14.5F, 0.4F, y + 6.0F, y + 6.7F, BigMat.R_COPPER, 8);
        }
        m.box(2.7F, y, 1.05F, 13.3F, y + 0.5F, 14.95F, tier(tier));
    }

    static void server(RailMesh m, int tier, float fill) {
        float y = chassis(m);
        m.box(2.8F, y, 1.0F, 13.2F, y + 6.4F, 15.0F, BigMat.R_BLACK);
        int lit = Math.round(Math.max(0, Math.min(1, fill)) * 5);
        float[] zs = {1.6F, 4.4F, 7.2F, 10.0F};
        for (int col = 0; col < zs.length; col++) {
            for (int k = 0; k < 5; k++) {
                BigMat led = k >= lit ? BigMat.R_LAMP_OFF : (k + col) % 3 != 0 ? BigMat.R_FAM_DATA : BigMat.R_LAMP_G;
                m.box(13.15F, y + 1.0F + k, zs[col], 13.3F, y + 1.4F + k, zs[col] + 1.8F, led);
                m.box(2.7F, y + 1.0F + k, zs[col], 2.85F, y + 1.4F + k, zs[col] + 1.8F, led);
            }
        }
        logoAt(m, BigMat.R_FAM_DATA, ICON_DATA, 13.0F, 13.3F, y + 2.2F, 12.6F);
        m.box(2.7F, y, 1.05F, 13.3F, y + 0.5F, 14.95F, tier(tier));
        m.box(3.0F, y + 6.4F, 2.0F, 13.0F, y + 7.0F, 14.0F, BigMat.R_GRILL);
        //Antenne
        m.cyl('y', 11.6F, 13.6F, 0.2F, y + 7.0F, y + 9.4F, BigMat.R_CHROME, 6);
        m.box(11.35F, y + 9.4F, 13.35F, 11.85F, y + 9.8F, 13.85F, BigMat.R_LAMP_R);
    }

    /** Wagon a air comprime : armoire des vannes a l'arriere (jauge, logo, manometre), trois bouteilles empilees, cadre avant. */
    static void airCar(RailMesh m, int tier, float fill) {
        float y = chassis(m);
        m.box(2.8F, y, 0.8F, 13.2F, y + 8.2F, 4.8F, BigMat.R_DARK);
        m.box(2.6F, y + 8.2F, 0.6F, 13.4F, y + 8.6F, 5.0F, BigMat.R_STEEL);
        m.box(2.7F, y + 0.2F, 0.85F, 13.3F, y + 0.6F, 4.75F, tier(tier));
        gaugeAt(m, BigMat.R_FAM_PRESSURE, 13.2F, 13.5F, y + 1.2F, y + 5.6F, 1.1F, fill);
        logoAt(m, BigMat.R_FAM_PRESSURE, ICON_PRESSURE, 13.2F, 13.4F, y + 1.6F, 2.7F);
        for (float[] xs : new float[][]{{13.2F, 13.45F, 13.5F}, {2.55F, 2.8F, 2.5F}}) {
            //Manometre
            m.cyl('x', y + 6.6F, 2.9F, 0.95F, xs[0], xs[1], BigMat.R_BLACK, 12);
            float w0 = xs[2] > 8 ? xs[1] - 0.05F : xs[0] - 0.05F;
            m.cyl('x', y + 6.6F, 2.9F, 0.75F, w0, w0 + 0.1F, BigMat.R_WHITE, 12);
            float n0 = xs[2] > 8 ? w0 + 0.1F : w0 - 0.05F;
            m.box(n0, y + 6.6F, 2.85F, n0 + 0.05F, y + 7.2F, 2.95F, BigMat.R_RED);
        }
        //Bouteilles
        float r = 2.2F;
        float[][] bottles = {{5.5F, y + 2.4F}, {10.5F, y + 2.4F}, {8.0F, y + 6.2F}};
        for (float[] b : bottles) {
            m.cyl('z', b[0], b[1], r, 4.8F, 14.2F, BigMat.R_STEEL_L, 14);
            m.cyl('z', b[0], b[1], 1.5F, 14.2F, 14.7F, BigMat.R_STEEL_L, 12);
            m.cyl('z', b[0], b[1], 0.5F, 14.7F, 15.0F, BigMat.R_CHROME, 8);
            for (float z : new float[]{6.6F, 12.0F}) {
                m.cyl('z', b[0], b[1], r + 0.12F, z, z + 0.4F, tier(tier), 14, false, 0.3F);
            }
            m.cyl('z', b[0], b[1], r + 0.1F, 9.0F, 9.6F, BigMat.R_FAM_PRESSURE, 14, false, 0.25F);
        }
        //Berceaux et cadre avant
        for (float z : new float[]{7.6F, 10.4F}) {
            m.box(3.4F, y, z, 12.6F, y + 0.9F, z + 0.8F, BigMat.R_FRAME);
        }
        m.box(2.8F, y, 14.6F, 3.4F, y + 8.6F, 15.2F, BigMat.R_STEEL);
        m.box(12.6F, y, 14.6F, 13.2F, y + 8.6F, 15.2F, BigMat.R_STEEL);
        m.box(2.8F, y + 8.6F, 14.6F, 13.2F, y + 9.1F, 15.2F, BigMat.R_STEEL);
        //Collecteur
        m.box(5.5F, y + 2.2F, 14.85F, 10.5F, y + 2.6F, 15.05F, BigMat.R_CHROME);
        m.box(7.8F, y + 2.6F, 14.85F, 8.2F, y + 6.2F, 15.05F, BigMat.R_CHROME);
    }

    /** Wagon plat : plateau plat (dessus a {@link #FLAT_DECK}), rien au-dessus ; le bloc transporte est dessine a part. */
    static void flat(RailMesh m, int tier) {
        float y = chassis(m);
        BigMat deck = tier == 0 ? BigMat.R_PLANKS : BigMat.R_STEEL_L;
        //Plateau affleurant : planches (ou toles) dans un cadre d'acier, tout a la meme hauteur
        m.box(2.4F, y, 1.0F, 13.6F, FLAT_DECK, 15.0F, deck);
        m.box(2.0F, y, 0.6F, 2.4F, FLAT_DECK, 15.4F, BigMat.R_STEEL);
        m.box(13.6F, y, 0.6F, 14.0F, FLAT_DECK, 15.4F, BigMat.R_STEEL);
        m.box(2.4F, y, 0.6F, 13.6F, FLAT_DECK, 1.0F, BigMat.R_STEEL);
        m.box(2.4F, y, 15.0F, 13.6F, FLAT_DECK, 15.4F, BigMat.R_STEEL);
        m.box(1.9F, y + 0.1F, 0.65F, 14.1F, y + 0.5F, 15.35F, tier(tier));
        for (float z : new float[]{2.4F, 6.0F, 10.0F, 13.6F}) {
            //Poches d'arrimage sous le rebord
            m.box(1.7F, y - 0.5F, z - 0.4F, 1.9F, y + 0.6F, z + 0.4F, BigMat.R_DARK);
            m.box(14.1F, y - 0.5F, z - 0.4F, 14.3F, y + 0.6F, z + 0.4F, BigMat.R_DARK);
        }
        //Poutre centrale en ventre de poisson
        m.box(6.6F, y - 1.6F, 5.6F, 9.4F, y - 1.2F, 10.4F, BigMat.R_FRAME);
        m.box(7.6F, y - 1.2F, 5.6F, 8.4F, y, 10.4F, BigMat.R_FRAME);
    }

    /** 0.20.6 : wagon de pose : plateau, pile de travees de voie (selon le remplissage) et portique qui tient une travee a l'avant. */
    static void layer(RailMesh m, int tier, float fill) {
        flat(m, tier);
        float y = FLAT_DECK;
        int panels = Math.max(1, Math.round(fill * 4));
        for (int i = 0; i < panels; i++) {
            float py = y + i * 1.3F;
            for (float z = 1.6F; z < 8.6F; z += 1.75F) {
                m.box(3.2F, py, z, 12.8F, py + 0.8F, z + 1.0F, BigMat.R_TIE);
            }
            m.box(4.3F, py + 0.8F, 1.4F, 5.1F, py + 1.3F, 9.4F, BigMat.R_RAIL);
            m.box(10.9F, py + 0.8F, 1.4F, 11.7F, py + 1.3F, 9.4F, BigMat.R_RAIL);
        }
        //Portique : deux montants, poutre, palan et travee suspendue
        for (float x : new float[]{2.4F, 12.6F}) {
            m.box(x, y, 10.6F, x + 1.0F, y + 9.0F, 11.6F, BigMat.R_HAZARD);
            m.box(x, y, 13.4F, x + 1.0F, y + 9.0F, 14.4F, BigMat.R_HAZARD);
            m.box(x, y + 8.2F, 10.6F, x + 1.0F, y + 9.0F, 14.4F, BigMat.R_HAZARD);
        }
        m.box(2.4F, y + 9.0F, 11.6F, 13.6F, y + 10.0F, 13.4F, BigMat.R_STEEL);
        m.box(7.3F, y + 6.0F, 12.1F, 8.7F, y + 9.0F, 12.9F, BigMat.R_DARK);
        m.box(7.8F, y + 4.4F, 12.3F, 8.2F, y + 6.0F, 12.7F, BigMat.R_STEEL_L);
        m.box(3.4F, y + 3.6F, 11.0F, 12.6F, y + 4.4F, 14.0F, BigMat.R_TIE);
        m.box(4.3F, y + 4.4F, 10.8F, 5.1F, y + 4.9F, 14.2F, BigMat.R_RAIL);
        m.box(10.9F, y + 4.4F, 10.8F, 11.7F, y + 4.9F, 14.2F, BigMat.R_RAIL);
        //Pupitre et gyrophare
        m.box(12.8F, y, 8.8F, 14.0F, y + 2.6F, 10.2F, BigMat.R_DARK);
        m.box(13.0F, y + 2.6F, 9.1F, 13.8F, y + 3.4F, 9.9F, BigMat.R_LAMP_O);
        logoAt(m, BigMat.R_LAMP_G, ICON_ROBOT, 13.95F, 14.15F, y + 0.6F, 9.5F);
    }

    static void robotVan(RailMesh m, int tier, float fill) {
        float y = chassis(m);
        m.box(2.6F, y, 0.8F, 13.4F, y + 6.4F, 15.2F, BigMat.R_STEEL);
        m.box(2.5F, y + 0.3F, 0.85F, 13.5F, y + 0.7F, 15.15F, tier(tier));
        //Portes a bandes sur les deux flancs
        m.box(13.35F, y + 1.0F, 4.0F, 13.6F, y + 5.6F, 12.0F, BigMat.R_STRIPE);
        m.box(13.55F, y + 1.4F, 4.6F, 13.7F, y + 5.2F, 11.4F, BigMat.R_DARK);
        m.box(2.4F, y + 1.0F, 4.0F, 2.65F, y + 5.6F, 12.0F, BigMat.R_STRIPE);
        m.box(2.3F, y + 1.4F, 4.6F, 2.45F, y + 5.2F, 11.4F, BigMat.R_DARK);
        gaugeAt(m, BigMat.R_FAM_ITEM, 13.4F, 13.7F, y + 1.2F, y + 5.0F, 1.8F, fill);
        logoAt(m, BigMat.R_LAMP_G, ICON_ROBOT, 13.4F, 13.6F, y + 2.0F, 12.6F);
        //Aire du drone
        m.box(3.4F, y + 6.4F, 3.4F, 12.6F, y + 6.8F, 12.6F, BigMat.R_DARK);
        m.box(5.0F, y + 6.8F, 5.0F, 11.0F, y + 6.9F, 11.0F, BigMat.R_LAMP_G);
        //Drone pose
        m.box(6.6F, y + 6.9F, 6.6F, 9.4F, y + 8.2F, 9.4F, BigMat.R_WHITE);
        m.box(7.4F, y + 7.2F, 9.4F, 8.6F, y + 7.8F, 9.5F, BigMat.R_GLOW_CYAN);
        for (float a : new float[]{45, -45}) {
            m.translate(8, y + 7.9F, 8);
            m.rotate('y', 0, 0, 0, a);
            m.box(-3.4F, -0.15F, -0.3F, 3.4F, 0.15F, 0.3F, BigMat.R_DARK);
            m.pop();
            m.pop();
        }
        for (float dx : new float[]{-2.4F, 2.4F}) {
            for (float dz : new float[]{-2.4F, 2.4F}) {
                m.cyl('y', 8 + dx, 8 + dz, 0.35F, y + 7.7F, y + 8.3F, BigMat.R_CHROME, 8);
                m.cyl('y', 8 + dx, 8 + dz, 1.0F, y + 8.3F, y + 8.45F, BigMat.R_DARK, 10);
            }
        }
    }

    /**
     * Voiture voyageurs decouverte (deux places, les voyageurs restent visibles) : caisse basse a la livree, banquettes a dossier, pare-brise
     * a l'avant, lanternes.
     */
    static void passenger(RailMesh m, int tier) {
        BigMat lv = livery(tier);
        float y = chassis(m);
        m.box(2.6F, y, 0.6F, 13.4F, y + 0.4F, 15.4F, BigMat.R_PLANKS);
        //Caisse basse
        m.box(2.6F, y + 0.4F, 0.6F, 3.2F, y + 4.0F, 15.4F, lv);
        m.box(12.8F, y + 0.4F, 0.6F, 13.4F, y + 4.0F, 15.4F, lv);
        m.box(3.2F, y + 0.4F, 0.6F, 12.8F, y + 4.0F, 1.2F, lv);
        m.box(3.2F, y + 0.4F, 14.8F, 12.8F, y + 4.0F, 15.4F, lv);
        for (float[] xs : new float[][]{{2.5F, 3.3F}, {12.7F, 13.5F}}) {
            m.box(xs[0], y + 4.0F, 0.5F, xs[1], y + 4.3F, 15.5F, BigMat.R_DARK);
        }
        m.box(3.3F, y + 4.0F, 0.5F, 12.7F, y + 4.3F, 1.3F, BigMat.R_DARK);
        m.box(3.3F, y + 4.0F, 14.7F, 12.7F, y + 4.3F, 15.5F, BigMat.R_DARK);
        //Filet blanc et bande de tier sur les flancs
        for (float[] xs : new float[][]{{2.55F, 2.7F}, {13.3F, 13.45F}}) {
            m.box(xs[0], y + 2.6F, 0.65F, xs[1], y + 3.0F, 15.35F, BigMat.R_WHITE);
            m.box(xs[0], y + 0.6F, 0.65F, xs[1], y + 1.0F, 15.35F, tier(tier));
        }
        //Pare-brise a l'avant
        m.box(3.3F, y + 4.3F, 14.9F, 3.8F, y + 7.4F, 15.3F, BigMat.R_DARK);
        m.box(12.2F, y + 4.3F, 14.9F, 12.7F, y + 7.4F, 15.3F, BigMat.R_DARK);
        m.box(3.3F, y + 7.4F, 14.9F, 12.7F, y + 7.8F, 15.3F, BigMat.R_DARK);
        m.box(3.8F, y + 4.3F, 15.0F, 12.2F, y + 7.4F, 15.2F, BigMat.R_GLASS_D);
        //Banquettes
        for (float zc : SEAT_Z) {
            m.box(4.3F, y + 0.4F, zc - 1.6F, 11.7F, y + 1.2F, zc + 1.0F, BigMat.R_DARK);
            m.box(4.0F, y + 1.2F, zc - 1.6F, 12.0F, SEAT_Y, zc + 1.0F, BigMat.R_RED);
            m.box(4.0F, SEAT_Y, zc - 1.6F, 12.0F, y + 5.0F, zc - 1.0F, BigMat.R_RED);
            m.box(3.8F, y + 5.0F, zc - 1.7F, 12.2F, y + 5.3F, zc - 0.9F, BigMat.R_DARK);
        }
        //Lanternes aux quatre coins
        for (float x : new float[]{2.75F, 12.85F}) {
            for (float z : new float[]{0.9F, 14.7F}) {
                m.box(x, y + 4.3F, z, x + 0.4F, y + 6.0F, z + 0.4F, BigMat.R_CHROME);
                m.box(x - 0.1F, y + 6.0F, z - 0.1F, x + 0.5F, y + 6.7F, z + 0.5F, z > 8 ? BigMat.R_LAMP : BigMat.R_LAMP_R);
                m.box(x - 0.15F, y + 6.7F, z - 0.15F, x + 0.55F, y + 6.9F, z + 0.55F, BigMat.R_DARK);
            }
        }
    }
}
