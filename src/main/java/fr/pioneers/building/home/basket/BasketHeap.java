package fr.pioneers.building.home.basket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1.0.3-beta (lot 11b) : TAS d'objets dessine dans le panier (pur, sans Minecraft ; lu par {@code J:building/home/client/BasketRenderer} et par les
 * planches des lots). Places fixes, couche par couche (fond du panier puis vers le haut), coordonnees en px du bloc (interieur du panier :
 * x, z de 3 a 13, fond a y 1) ; l'objet est couche a plat, tourne de {@code yaw} degres, incline de {@code tilt} degres.
 * <p>
 * 1.0.3-beta (lot 12, Guillaume : « ameliorer la facon dont le contenu est affiche (largement plus dense) ; une version haute et une
 * demi-hauteur ») : un tas par format ({@link #of}) :
 * <ul>
 *   <li>objets plats (carotte, pomme, oeuf...) : couches SERREES de 16 objets (grille 4 x 4 a 2,55 px, une couche sur deux decalee en
 *   quinconce) tous les {@link #STEP} 0,62 px, objets de 5,4 px ({@link #SCALE}) qui se chevauchent : aucun trou au fond ; les 3 couches du
 *   haut se resserrent (dome). Demi-hauteur : 12 couches (171 places), haut : 20 couches (295 places) ;</li>
 *   <li>objets de bloc (citrouille, melon : modele en cube) : cubes reduits a 4,6 px ({@link #BLOCK_SCALE}), 2 x 2 par couche, dome de 2 :
 *   6 places (demi-hauteur), 10 (haut) ;</li>
 *   <li>paliers : {@link #HALF_LEVELS} 6 (demi-hauteur) et {@link #TALL_LEVELS} 10 (haut), deux couches par palier ;</li>
 *   <li>cout de rendu PLAFONNE : seules les {@link Heap#cap} dernieres places montrees sont dessinees ({@link Heap#first}) : les couches
 *   du dessous sont cachees par celles du dessus et par la vannerie. Places calculees une fois (cache statique).</li>
 * </ul>
 */
public final class BasketHeap {

    /** Une place du tas : centre (px), rotation autour de la verticale, inclinaison (degres). */
    public record Place(double x, double y, double z, double yaw, double tilt) {
    }

    /** Paliers du tas dessine (1..n ; 0 = vide) : panier demi-hauteur et panier haut. */
    public static final int HALF_LEVELS = 6, TALL_LEVELS = 10;
    /** Ecart vertical entre deux couches d'objets plats (px) ; hauteur de la premiere couche. */
    public static final double STEP = 0.62, FLOOR = 1.3;
    /** Couches d'objets plats : demi-hauteur (dessus vers 8,1 px, bord a 8), haut (dessus vers 13,1 px, bord a 13). */
    public static final int HALF_LAYERS = 12, TALL_LAYERS = 20;
    /** Echelle d'un objet plat dessine (1 = un bloc : 5,4 px) ; d'un objet de bloc (vue FIXED a 0,5 : cube de 4,6 px). */
    public static final double SCALE = 0.34, BLOCK_SCALE = 0.575;
    /** Places dessinees au plus (les dernieres montrees) : demi-hauteur, haut. */
    public static final int HALF_CAP = 56, TALL_CAP = 72;
    /** Ecart de la grille des objets plats (px). */
    static final double PITCH = 2.55;
    /** Bord exterieur des parois (px) moins une marge : aucun objet ne depasse de la vannerie. */
    static final double WALL_LO = 2.3, WALL_HI = 13.7;
    /** Part de la demi-largeur d'un objet plat comptee contre la paroi (le dessin ne remplit pas tout le carre de 16 px). */
    static final double FILL = 0.8;

    /** Un tas : places dans l'ordre de remplissage, places montrees par palier, plafond de dessin, echelle. */
    public static final class Heap {
        private final List<Place> places;
        private final int[] shown;
        private final int cap;
        private final double scale;
        private final boolean blocky;

        Heap(List<Place> places, int[] shown, int cap, double scale, boolean blocky) {
            this.places = Collections.unmodifiableList(places);
            this.shown = shown;
            this.cap = cap;
            this.scale = scale;
            this.blocky = blocky;
        }

        public List<Place> places() {
            return places;
        }

        /** Paliers de ce tas (le palier 0 est le panier vide). */
        public int levels() {
            return shown.length - 1;
        }

        /** Nombre de places montrees au palier {@code level}. */
        public int shown(int level) {
            return shown[Math.max(0, Math.min(shown.length - 1, level))];
        }

        /** Premiere place dessinee au palier {@code level} (plafond : les dernieres montrees seulement). */
        public int first(int level) {
            return Math.max(0, shown(level) - cap);
        }

        public int cap() {
            return cap;
        }

        public double scale() {
            return scale;
        }

        public boolean blocky() {
            return blocky;
        }
    }

    private static final Heap HALF = flat(HALF_LAYERS, HALF_LEVELS, HALF_CAP), TALL = flat(TALL_LAYERS, TALL_LEVELS, TALL_CAP);
    private static final Heap HALF_BLOCKS = blocks(new int[]{4, 2}, HALF_LEVELS), TALL_BLOCKS = blocks(new int[]{4, 4, 2}, TALL_LEVELS);

    private BasketHeap() {
    }

    /** Tas d'un format de panier ({@code tall} : panier haut) pour des objets plats ou de bloc ({@code blocky}). */
    public static Heap of(boolean tall, boolean blocky) {
        return blocky ? (tall ? TALL_BLOCKS : HALF_BLOCKS) : (tall ? TALL : HALF);
    }

    /** Toutes les places du panier demi-hauteur (objets plats), dans l'ordre de remplissage (lot 11b). */
    public static List<Place> places() {
        return HALF.places();
    }

    /** Nombre de places montrees au palier {@code level} du panier demi-hauteur (objets plats). */
    public static int shown(int level) {
        return HALF.shown(level);
    }

    // ------------------------------------------------------------------ 1.0.3-beta (lot 14) : SURFACE du tas (rendu en jeu)
    /** Fond interieur du panier (px) ; hauteur du bord (px) : demi-hauteur / haut (memes chiffres que T:home/basket.py HEIGHTS). */
    public static final double INNER_FLOOR = 1, HALF_RIM = 8, TALL_RIM = 13;
    /**
     * Surface : objets plats de 5,8 px ({@link #TOP_SCALE}), grille 3 x 3 au pas {@link #TOP_PITCH} ; ecart vertical entre deux objets
     * ({@link #SUB} : plus que l'epaisseur d'un objet a plat, 16 x 0,36 / 16 = 0,36 px).
     */
    public static final double TOP_SCALE = 0.36, TOP_PITCH = 2.7, SUB = 0.38;
    /** Bombe quand le panier est plein (px, au centre). */
    public static final double DOME = 0.9;
    /** Objets dessines au plus par panier (surface 9 + lit 4) : contre 56 / 72 avant. */
    public static final int SURFACE_MAX = 13;

    /** Hauteur du dessus du tas (px) pour un remplissage {@code fill} (0..1) : fond + remplissage x hauteur interieure ; plein = a ras bord. */
    public static double surfaceY(boolean tall, double fill) {
        double f = Math.max(0, Math.min(1, fill));
        return INNER_FLOOR + f * ((tall ? TALL_RIM : HALF_RIM) - INNER_FLOOR);
    }

    /**
     * 1.0.3-beta (lot 14, Guillaume : « la texture du contenu est assez sale ; il faut que le contenu remplisse a ras bord quand c'est plein :
     * hauteur proportionnelle au remplissage ») : places DESSINEES du tas, calculees d'apres le remplissage exact ({@code count} objets sur
     * {@code capacity}) : seule la SURFACE se voit au-dessus de la vannerie opaque, on ne dessine qu'elle.
     * <ul>
     *   <li>objets plats : couche du dessus = grille 3 x 3 (pas 2,7 px, objets de 5,8 px, quarts de tour seulement : pixels droits, lisibles),
     *   5 hauteurs a {@link #SUB} px d'ecart, le centre en haut (petit monticule de 1,5 px) : deux objets qui se recouvrent ne sont
     *   jamais dans le meme plan (pas de scintillement) et ne se traversent pas (epaisseur d'un objet a plat : 0,36 px) ; un LIT de 4 objets
     *   decales d'un demi-pas dessous bouche les jours ; plein : bombe de {@link #DOME} px au centre ; peu d'objets : {@code count} places ;</li>
     *   <li>objets de bloc : cubes de 4,6 px, 2 x 2 dont le dessus suit la surface (plein : a ras bord), une couche de plus dessous si la
     *   place le permet (cubes qui se touchent sans se traverser).</li>
     * </ul>
     * Centres ramenes dans les parois (aucun objet ne traverse la vannerie). Rien n'est garde en cache (13 places au plus).
     */
    public static List<Place> surface(boolean tall, boolean blocky, int count, int capacity) {
        List<Place> out = new ArrayList<>();
        if (count <= 0 || capacity <= 0) {
            return out;
        }
        double fill = Math.min(1, count / (double) capacity), top = surfaceY(tall, fill);
        boolean full = count >= capacity;
        if (blocky) {
            //(pas de bombe pour les cubes : un cube pose dessus depasserait le bord de 4,6 px)
            double size = 16 * 0.5 * BLOCK_SCALE;
            double y = Math.max(INNER_FLOOR + size / 2, top - size / 2);
            int n = 4;   //1.0.3-beta (correctif) : decoration : couche complete a la hauteur du remplissage
            //couche du dessous posee juste sous celle du dessus (cubes qui se touchent, sans se traverser), si la place le permet
            if (y - size >= INNER_FLOOR + size / 2 - 0.01) {
                for (int i = 0; i < 4; i++) {
                    out.add(cube(i, 4, y - size, i + 20));
                }
            }
            for (int i = 0; i < n; i++) {
                out.add(cube(i, 4, y, i));
            }
            return out;
        }
        double half = TOP_SCALE * 8 * FILL, lo = 3 + half, hi = 13 - half;
        //couche du dessus : du centre vers les bords, autant de places que d'objets (9 au plus). Deux objets qui se recouvrent (ecart < 5,8 px)
        //ne sont jamais a la meme hauteur : 5 hauteurs ({@link #SUB} px d'ecart), le centre en haut, puis 4 paires d'objets qui ne se touchent
        //pas (ecart 6,04 px : un bord et le coin oppose) ; le monticule ne fait que 1,5 px : le panier plein reste a ras bord
        int[][] order = {{1, 1}, {1, 0}, {0, 2}, {0, 1}, {2, 2}, {2, 1}, {0, 0}, {1, 2}, {2, 0}};
        int[] band = {0, 1, 1, 2, 2, 3, 3, 4, 4};
        int n = 9, bands = band[n - 1] + 1;   //1.0.3-beta (correctif) : couche complete a chaque hauteur
        double base = Math.max(INNER_FLOOR + 0.3, top - 0.25 - (bands - 1) * SUB);
        for (int k = 0; k < n; k++) {
            int i = order[k][0], j = order[k][1];
            double x = clamp(8 + (i - 1) * TOP_PITCH + jitter(k, 11) * 0.25, lo, hi), z = clamp(8 + (j - 1) * TOP_PITCH + jitter(k, 12) * 0.25, lo, hi);
            double dome = full && k == 0 ? DOME : 0;
            out.add(new Place(x, base + (bands - 1 - band[k]) * SUB + dome, z, Math.floorMod(k * 5 + i + 2 * j, 4) * 90, 0));
        }
        //lit : 4 objets au demi-pas SOUS la couche du dessus (chacun a sa hauteur), s'il reste de la place : bouche les jours entre les objets
        double bed = base - SUB - 0.05;
        if (bed - 3 * SUB > INNER_FLOOR + 0.2) {
            for (int m = 0; m < 4; m++) {
                int i = m % 2, j = m / 2;
                out.add(new Place(clamp(8 + (i - 0.5) * TOP_PITCH, lo, hi), bed - m * SUB, clamp(8 + (j - 0.5) * TOP_PITCH, lo, hi),
                      Math.floorMod(m * 3 + 1, 4) * 90, 0));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 1.0.3-beta (correctif) : LIT du tas
    /**
     * Un carreau du lit : rectangle (px) {@code x0..x1 x z0..z1} a la hauteur {@code y}, partie du carreau entier qu'il montre ({@code f*} en
     * fractions 0..1 du carreau), quart de tour de la texture ({@code rot}), teinte (1 = pleine lumiere, plus sombre dessous).
     */
    public record BedTile(double x0, double z0, double x1, double z1, double y, double fu0, double fv0, double fu1, double fv1, int rot,
                          float shade) {
    }

    /** Lit : cote d'un carreau (px), couches, ecart vertical entre couches (px). */
    public static final double BED_TILE = 4.6, BED_STEP = 0.16;
    public static final int BED_LAYERS = 5;
    /** Decalage (fractions de carreau, x puis z) de chaque couche : les trous d'une couche tombent sur le plein des autres. */
    static final double[][] BED_OFFSET = {{0, 0}, {0.5, 0.5}, {0.25, 0.75}, {0.75, 0.25}, {0.5, 0}};

    /**
     * 1.0.3-beta (correctif, Guillaume : « les paniers sont toujours relativement vides, surtout les poissons ») : sous les objets de la
     * surface, un LIT fait de la texture de l'objet lui-meme, carrelee a plat sur tout l'interieur (3..13 px) en {@link #BED_LAYERS} couches
     * decalees ({@link #BED_OFFSET}) et tournees d'un quart de tour carreau par carreau : les jours d'une couche (parties transparentes d'un
     * poisson, d'une carotte…) sont bouches par la suivante ; plus de fond de panier visible. Juste sous l'objet le plus bas de la surface
     * (objets plats) ou a mi-hauteur des cubes du dessus (objets de bloc) ; jamais sous le fond.
     */
    public static List<BedTile> bed(boolean tall, boolean blocky, int count, int capacity) {
        List<BedTile> out = new ArrayList<>();
        List<Place> top = surface(tall, blocky, count, capacity);
        if (top.isEmpty()) {
            return out;
        }
        double low = Double.MAX_VALUE;
        for (Place p : top) {
            low = Math.min(low, p.y());
        }
        double y0 = blocky ? low : low - 0.35;
        int layers = BED_LAYERS;
        for (int l = 0; l < layers; l++) {
            //peu rempli : le lit couvre le fond (couches gardees dans l'ordre, sans plan commun)
            double y = Math.max(y0 - l * BED_STEP, INNER_FLOOR + 0.03 + (layers - 1 - l) * 0.03);
            double ox = BED_OFFSET[l][0] * BED_TILE, oz = BED_OFFSET[l][1] * BED_TILE;
            float shade = 1F - 0.07F * l;
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    double tx0 = 3 + (i - 1) * BED_TILE + ox, tz0 = 3 + (j - 1) * BED_TILE + oz;
                    double x0 = Math.max(3, tx0), z0 = Math.max(3, tz0), x1 = Math.min(13, tx0 + BED_TILE), z1 = Math.min(13, tz0 + BED_TILE);
                    if (x1 - x0 < 0.05 || z1 - z0 < 0.05) {
                        continue;
                    }
                    int rot = Math.floorMod(i * 3 + j * 5 + l * 7, 4);
                    out.add(new BedTile(x0, z0, x1, z1, y, (x0 - tx0) / BED_TILE, (z0 - tz0) / BED_TILE, (x1 - tx0) / BED_TILE, (z1 - tz0) / BED_TILE,
                          rot, shade));
                }
            }
        }
        return out;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static Place cube(int i, int of, double y, int id) {
        double x, z;
        if (of == 4) {
            x = 8 + (i % 2 == 0 ? -1 : 1) * 2.45;
            z = 8 + (i / 2 == 0 ? -1 : 1) * 2.45;
        } else {
            x = 8 + (i == 0 ? -1.3 : 1.3);
            z = 8 + (i == 0 ? 0.9 : -0.9);
        }
        double size = 16 * 0.5 * BLOCK_SCALE, yaw = jitter(id, 6) * 12, a = Math.toRadians(yaw);
        double ext = size / 2 * (Math.abs(Math.cos(a)) + Math.abs(Math.sin(a)));
        //interieur des parois : 3..13
        return new Place(clamp(x, 3 + ext, 13 - ext), y, clamp(z, 3 + ext, 13 - ext), yaw, 0);
    }

    static double jitter(int i, int salt) {
        long h = (i * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return ((h & 0xFFFF) / 65535.0) * 2 - 1;
    }

    /** Rayon (px, depuis l'axe du panier) des places gardees dans la couche {@code t} a partir du haut : dome sur 3 couches. */
    static double domeRadius(int fromTop) {
        return fromTop == 0 ? 2.4 : fromTop == 1 ? 4.2 : fromTop == 2 ? 5.0 : 99;
    }

    private static Heap flat(int layers, int levels, int cap) {
        List<Place> out = new ArrayList<>();
        int[] perLayer = new int[layers];
        int n = 0;
        for (int k = 0; k < layers; k++) {
            List<double[]> layer = new ArrayList<>();
            double r = domeRadius(layers - 1 - k);
            for (int j = 0; j < 4; j++) {
                for (int i = 0; i < 4; i++) {
                    //grille 4 x 4 ; couches impaires en quinconce (rangees decalees d'un quart de pas, en x et en z)
                    double x = 8 + (i - 1.5) * PITCH + (k % 2 == 1 ? (j % 2 == 0 ? 0.64 : -0.64) : 0);
                    double z = 8 + (j - 1.5) * PITCH + (k % 2 == 1 ? (i % 2 == 0 ? -0.64 : 0.64) : 0);
                    x += jitter(n, 1) * 0.45;
                    z += jitter(n, 2) * 0.45;
                    n++;
                    double d = Math.hypot(x - 8, z - 8);
                    if (d <= r) {
                        layer.add(new double[]{x, z, d, n});
                    }
                }
            }
            //Du centre vers les bords
            layer.sort((a, b) -> Double.compare(a[2], b[2]));
            for (double[] p : layer) {
                int id = (int) p[3];
                double y = FLOOR + k * STEP + jitter(id, 3) * 0.12;
                //quart de tour + angle libre de +-35 deg (les objets se croisent) ; centre ramene pour ne pas sortir des parois
                double yaw = Math.floorMod(id * 7, 4) * 90 + jitter(id, 4) * 35;
                double half = SCALE * 8 * FILL, a = Math.toRadians(yaw);
                double ext = half * (Math.abs(Math.cos(a)) + Math.abs(Math.sin(a)));
                double x = Math.max(WALL_LO + ext, Math.min(WALL_HI - ext, p[0]));
                double z = Math.max(WALL_LO + ext, Math.min(WALL_HI - ext, p[1]));
                out.add(new Place(x, y, z, yaw, jitter(id, 5) * 14));
            }
            perLayer[k] = layer.size();
        }
        //paliers : autant de couches par palier (le dernier palier = tout)
        int[] shown = new int[levels + 1];
        for (int l = 1; l <= levels; l++) {
            int upto = (int) Math.ceil(layers * (double) l / levels), sum = 0;
            for (int k = 0; k < upto; k++) {
                sum += perLayer[k];
            }
            shown[l] = sum;
        }
        return new Heap(out, shown, cap, SCALE, false);
    }

    private static Heap blocks(int[] layers, int levels) {
        List<Place> out = new ArrayList<>();
        double size = 16 * 0.5 * BLOCK_SCALE;
        int n = 0;
        for (int k = 0; k < layers.length; k++) {
            double y = 1 + size / 2 + k * (size - 0.2);
            for (int i = 0; i < layers[k]; i++) {
                double x, z;
                if (layers[k] == 4) {
                    x = 8 + (i % 2 == 0 ? -1 : 1) * 2.45;
                    z = 8 + (i / 2 == 0 ? -1 : 1) * 2.45;
                } else {
                    //dome : deux cubes au milieu, en travers de la couche du dessous
                    x = 8 + (i == 0 ? -1.3 : 1.3);
                    z = 8 + (i == 0 ? 0.9 : -0.9);
                }
                double yaw = jitter(n, 6) * 18;
                double half = size / 2, a = Math.toRadians(yaw);
                double ext = half * (Math.abs(Math.cos(a)) + Math.abs(Math.sin(a)));
                x = Math.max(WALL_LO + ext, Math.min(WALL_HI - ext, x + jitter(n, 7) * 0.3));
                z = Math.max(WALL_LO + ext, Math.min(WALL_HI - ext, z + jitter(n, 8) * 0.3));
                out.add(new Place(x, y + jitter(n, 9) * 0.1, z, yaw, 0));
                n++;
            }
        }
        int[] shown = new int[levels + 1];
        for (int l = 1; l <= levels; l++) {
            shown[l] = Math.max(1, (int) Math.ceil(out.size() * (double) l / levels));
        }
        return new Heap(out, shown, out.size(), BLOCK_SCALE, true);
    }
}
