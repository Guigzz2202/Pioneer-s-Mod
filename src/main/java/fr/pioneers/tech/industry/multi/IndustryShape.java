package fr.pioneers.tech.industry.multi;

import fr.pioneers.tech.nuclear.ReactorBlock;

/**
 * Gabarits des multiblocs industriels (0.16.1). Geometrie identique a {@code tools/gc/industry/shapes.py} (verification du catalogue et
 * apercus) : toute modification doit etre faite des deux cotes.
 * <p>
 * Repere canonique : x lateral (droite +), y haut, z profondeur (+ vers l'interieur ; la facade est en z = 0). Parametres (a, b, c) :
 * <ul>
 *     <li>BOX : largeur, hauteur, profondeur ;</li>
 *     <li>TOWER, DOME : diametre, hauteur (DOME : hauteur des murs, la calotte s'y ajoute) ;</li>
 *     <li>HYPERBOLOID (0.20.26, construit) : diametre du bassin, hauteur ; tour de refroidissement : bassin, pieds en cadres, garnissage, coque
 *     mince ouverte en haut ({@link #hyperRole}) ;</li>
 *     <li>BELLY : diametre du creuset (le ventre deborde d'un bloc de chaque cote), hauteur ;</li>
 *     <li>DRUM : diametre, -, longueur (fut couche vers l'arriere du controleur) ;</li>
 *     <li>HALL : largeur, hauteur des murs, longueur (toit a deux pans au-dessus) ;</li>
 *     <li>POOL : largeur, profondeur (fond compris), longueur ; dessus ouvert ;</li>
 *     <li>SPHERE (0.17.1) : diametre (boite a x a x a, b et c ignores) ;</li>
 *     <li>TORUS (0.17.2) : grand diametre, hauteur de la section (boite a x b x a) ; trou central de diametre a/3, section rectangulaire aux
 *     coins arrondis.</li>
 *     <li>CONE (1.0) : diametre de la base, hauteur ; le rayon decroit jusqu'a une pointe d'un bloc (foreuses, tetes de puits) ;</li>
 *     <li>RIG (1.0) : diametre de la base ronde, hauteur ; tour ajouree des foreuses et des puits : socle rond en carcasses, quatre pieds en
 *     cadres qui se resserrent en courbe jusqu'a un mat, colonne de modules au centre (train de tiges, pompe) ;</li>
 *     <li>JACK (1.0) : 3 de large, hauteur, longueur ; pompe a balancier : chassis en carcasses, moteur, chevalet, balancier et bielle en
 *     cadres, colonne de pompage (modules) sous la tete ;</li>
 *     <li>LINE (1.0) : largeur, hauteur, longueur ; chaine de production : banc en carcasses, postes (modules) dessus, portiques en cadres
 *     tous les 4 blocs et aux bouts ;</li>
 *     <li>GANTRY (1.0) : largeur, hauteur, profondeur ; presse : banc, quatre tirants en cadres, sommier et verin, coulisseau (modules)
 *     suspendu au-dessus de la matrice ;</li>
 *     <li>TWIN (1.0) : diametre des cuves, hauteur ; deux cuves rondes (paroi seule) et, entre elles, la colonne de melange (modules), deux
 *     montants et un pont en cadres ;</li>
 *     <li>PWR, SFR (lot 3) : diametre, hauteur ; reacteurs Gen I (canaux dans l'eau, barres dans le couvercle) et Gen II (cuve-piscine au
 *     sodium, coeur au fond, dalle a barres, pompes et echangeurs) ;</li>
 *     <li>FFR (lot 3) : diametre de la sphere ; reacteur Gen III entre deux dalles (boite (a + 6) x (a + 4) x (a + 6)) ;</li>
 *     <li>TOK, HEL (lot 3 bis) : grand rayon de l'anneau ; tokamak (16 bobines en D, solenoide, injecteur, couverture) et heliotron (2 bobines
 *     helicoidales, convertisseur) ; chambre fine (petit rayon 1,2) ; boite (2a + 9) x (9 ou 8) x (2a + 9).</li>
 * </ul>
 * 1.0 : chaque case a un role ({@link #role}) : enveloppe, interieur (modules), cadre ({@link StructureFrameBlock}) ou rien (libre).
 * Enveloppe = case pleine dont une voisine (6 directions) est vide ; interieur = le reste du plein.
 */
public enum IndustryShape {
    BOX, TOWER, HYPERBOLOID, BELLY, DRUM, DOME, HALL, POOL, FREEFORM, SPHERE, TORUS, CONE, RIG, JACK, LINE, GANTRY, TWIN, PWR, SFR, FFR, TOK, HEL,
    /** 1.9.2 (retours de Guillaume) : atelier ferroviaire, halle de depot (en fin d'enum). */
    DEPOT;

    /** 1.0 : roles d'une case du gabarit. */
    public static final int NONE = 0, SHELL = 1, INTERIOR = 2, FRAME = 3;
    /**
     * 1.0 (lot 3) : roles des reacteurs (blocs fonctionnels {@link ReactorBlock}), verre seul, vide exige (eau, sodium, plasma
     * sont dessines par le coeur), carcasse jamais remplacee par une tole (couvercle, dalle).
     */
    public static final int FUEL = 4, ROD = 5, PUMP = 6, EXCHANGER = 7, COIL = 8, REFLECTOR = 9, COLLECTOR = 10, GLASS = 11, EMPTY = 12, CASING = 13;
    /** Lot 3 bis : fusion. */
    public static final int CHAMBER = 14, LINING = 15, SOLENOID = 16, INJECTOR = 17, TFCOIL = 18, HELIX = 19;

    /** Dimensions de la boite englobante (x, y, z). */
    public int[] bbox(int a, int b, int c) {
        return switch (this) {
            case BOX, POOL, JACK, LINE, GANTRY -> new int[]{a, b, c};
            case TWIN -> new int[]{2 * a + 3, b, a};
            case PWR, SFR -> new int[]{a, b, a};
            case FFR -> new int[]{a + 6, a + 4, a + 6};
            case TOK -> new int[]{2 * a + 9, 9, 2 * a + 9};
            case HEL -> new int[]{2 * a + 9, 8, 2 * a + 9};
            case TOWER, HYPERBOLOID, TORUS, CONE, RIG -> new int[]{a, b, a};
            case BELLY -> new int[]{a + 2, b, a + 2};
            case DRUM -> new int[]{a, a, c};
            case DOME -> new int[]{a, b + a / 2, a};
            case HALL -> new int[]{a, b + 1 + (a - 1) / 2, c};
            case SPHERE -> new int[]{a, a, a};
            case FREEFORM -> new int[]{1, 1, 1};
            case DEPOT -> new int[]{a, b + depotSlopes(a) + 3, c};
        };
    }

    private static boolean disk(int d, int u, int v, double r2) {
        double c = (d - 1) / 2.0;
        return (u - c) * (u - c) + (v - c) * (v - c) <= r2;
    }

    private static boolean disk(int d, int u, int v) {
        return disk(d, u, v, d * d / 4.0);
    }

    static double bellyRadius(int d, int h, int y) {
        double r0 = d / 2.0 - 1.0, rb = d / 2.0 + 1.0, rt = d / 2.0 - 1.5;
        double yb = Math.max(1.0, 0.4 * (h - 1));
        if (y <= yb) {
            return r0 + (rb - r0) * y / yb;
        }
        return rb + (rt - rb) * (y - yb) / Math.max(1.0, (h - 1) - yb);
    }

    /** 0.20.26 : hauteur des pieds de la tour de refroidissement (entrees d'air entre eux). */
    public static final int HYPER_LEGS = 3;

    /**
     * 0.20.26 : rayon de la coque de la tour de refroidissement a la rangee y (coque de y = HYPER_LEGS + 1 au sommet) : hyperboloide de
     * revolution, base d/2 - 1 (le bassin deborde d'un bloc), gorge 0,62 x base aux 72 % de la coque, sommet un peu evase (shapes.py hyper_r).
     */
    static double hyperRadius(int d, int h, int y) {
        int y0 = HYPER_LEGS + 1;
        double big = d / 2.0 - 1.0, rw = 0.62 * big, yw = y0 + 0.72 * (h - 1 - y0);
        double a = (yw - y0) / Math.sqrt((big / rw) * (big / rw) - 1);
        double t = (y - yw) / a;
        return rw * Math.sqrt(1 + t * t);
    }

    /** Volume plein de la coque (pour trouver sa peau) : disque de rayon hyperRadius ; sous la coque, celui de la base. */
    private static boolean hyperSolid(int a, int b, int x, int y, int z) {
        double r = hyperRadius(a, b, Math.max(y, HYPER_LEGS + 1));
        return y >= 0 && y < b && disk(a, x, z, r * r);
    }

    /**
     * 0.20.26 : tour de refroidissement construite : bassin rond (y = 0), pieds en treillis (cadres en quinconce) laissant entrer l'air,
     * garnissage (modules) sur la premiere rangee de la coque, coque hyperbolique mince au-dessus (voile de beton), vide dedans, ouverte
     * en haut (shapes.py hyper_role).
     */
    static int hyperRole(int a, int b, int x, int y, int z) {
        if (!disk(a, x, z)) {
            return NONE;
        }
        if (y == 0) {
            return SHELL;
        }
        int y0 = HYPER_LEGS + 1;
        if (y < y0) {
            boolean ring = hyperSolid(a, b, x, y0, z) && !(hyperSolid(a, b, x + 1, y0, z) && hyperSolid(a, b, x - 1, y0, z)
                                                         && hyperSolid(a, b, x, y0, z + 1) && hyperSolid(a, b, x, y0, z - 1));
            return ring && Math.floorMod(x + z + y, 2) == 0 ? FRAME : NONE;
        }
        if (!hyperSolid(a, b, x, y, z)) {
            return NONE;
        }
        for (int[] d : HYPER_DIRS) {
            int ny = y + d[1];
            if (ny >= b || ny < y0) {
                continue;
            }
            if (!hyperSolid(a, b, x + d[0], ny, z + d[2])) {
                return SHELL;
            }
        }
        return y == y0 ? INTERIOR : NONE;
    }

    /** Meme ordre que shapes.py hyper_role (le resultat n'en depend pas). */
    private static final int[][] HYPER_DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};

    /** Distance a l'axe vertical du tore (repere canonique). */
    static double torusRho(int a, int x, int z) {
        double cc = (a - 1) / 2.0;
        double dx = x - cc, dz = z - cc;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Rayon moyen du tore (entre le trou et le bord exterieur). */
    static double torusMid(int a) {
        return (a / 2.0 + a / 6.0) / 2.0;
    }

    /** 0.17.2 : tore (section rectangulaire aux coins arrondis ; meme calcul que shapes.py torus_solid). */
    static boolean torus(int a, int b, int x, int y, int z) {
        double ro = a / 2.0, ri = a / 6.0;
        double rm = (ro + ri) / 2.0, hw = (ro - ri) / 2.0, hh = b / 2.0, yc = (b - 1) / 2.0;
        double r = Math.min(hw, hh) * 0.6;
        double rho = torusRho(a, x, z);
        double du = Math.abs(rho - rm), dv = Math.abs(y - yc);
        if (du > hw || dv > hh) {
            return false;
        }
        double u = du - (hw - r), v = dv - (hh - r);
        return u <= 0 || v <= 0 || u * u + v * v <= r * r;
    }

    /** 1.0 : rayon du cone a la hauteur y (pointe d'un bloc au sommet). */
    static double coneRadius(int d, int h, int y) {
        return d / 2.0 - (d / 2.0 - 0.6) * y / Math.max(1.0, h - 1);
    }

    /** 1.0 : ecart des pieds de la tour ajouree au pied (le plus grand qui tient dans le disque de la base). */
    static int rigSpread(int a) {
        int c = (a - 1) / 2, best = 0;
        for (int d = 0; d <= c; d++) {
            if (2 * d * d <= a * a / 4.0 - 1) {
                best = d;
            }
        }
        return best;
    }

    /** 1.0 : ecart des pieds a la hauteur y (profil concave : evase en bas, mat en haut ; meme calcul que shapes.py rig_off). */
    static int rigOffset(int a, int b, int y) {
        int d0 = rigSpread(a), tip = b - 1;
        if (y <= 0) {
            return d0;
        }
        if (y >= tip) {
            return 0;
        }
        return Math.min(d0, (int) (d0 * Math.pow(1.0 - (double) y / tip, 1.6) + 0.5));
    }

    /** 1.0 : premiere hauteur ou les pieds se rejoignent (le mat commence, la colonne de modules s'arrete). */
    static int rigMast(int a, int b) {
        for (int y = 0; y < b; y++) {
            if (rigOffset(a, b, y) == 0) {
                return y;
            }
        }
        return b;
    }

    static int rigRole(int a, int b, int x, int y, int z) {
        int c = (a - 1) / 2, dx = x - c, dz = z - c;
        if (y == 0) {
            if (dx == 0 && dz == 0) {
                return INTERIOR;
            }
            return dx * dx + dz * dz <= a * a / 4.0 ? SHELL : NONE;
        }
        if (dx == 0 && dz == 0) {
            return y < rigMast(a, b) ? INTERIOR : FRAME;
        }
        int d = rigOffset(a, b, y), prev = rigOffset(a, b, y - 1), ax = Math.abs(dx), az = Math.abs(dz);
        //Pied (et sa marche quand il rentre d'un cran : on rejoint la diagonale par le cote)
        if (az == d && ax >= d && ax <= prev || ax == d && az >= d && az <= prev) {
            return FRAME;
        }
        return NONE;
    }

    /** 1.0 : pompe a balancier (facade en z = 0 : controleur, moteur derriere, chevalet, balancier, tete et pompe au fond). */
    static int jackRole(int b, int c, int x, int y, int z) {
        int back = c - 1, post = c - 2 - c / 2;
        if (x == 1 && z == back && y <= b - 3) {
            return INTERIOR;
        }
        if (y == 0) {
            return SHELL;
        }
        if ((z == 1 || z == 2) && (y == 1 || y == 2 && x == 1)) {
            return SHELL;
        }
        if (z == post && ((x == 0 || x == 2) && y <= b - 3 || x == 1 && y == b - 2)) {
            return FRAME;
        }
        if (x == 1 && (y == b - 1 && z >= 2 || y == b - 2 && z == back || z == 2 && y >= 3 && y <= b - 2)) {
            return FRAME;
        }
        return NONE;
    }

    /** 1.0 : chaine de production (meme calcul que shapes.py line_role). */
    static int lineRole(int a, int b, int c, int x, int y, int z) {
        if (y == 0) {
            return SHELL;
        }
        if (x >= 1 && x <= a - 2 && y <= b - 3 && z >= 1 && z <= c - 2) {
            return INTERIOR;
        }
        if ((z % 4 == 0 || z == c - 1) && (y == b - 1 || (x == 0 || x == a - 1) && y <= b - 2)) {
            return FRAME;
        }
        return NONE;
    }

    /** 1.9.2 : hauteur de la porte de l'atelier ferroviaire (largeur 3) et demi-largeur du lanterneau (memes valeurs que shapes.py). */
    public static final int DEPOT_DOOR = 3, DEPOT_LANTERN = 1;

    /** 1.9.2 : rangees de pente du toit de l'atelier ferroviaire (marches de 2 cases) avant le lanterneau. */
    static int depotSlopes(int a) {
        return Math.max(1, ((a - 1) / 2 - DEPOT_LANTERN + 1) / 2);
    }

    /**
     * 1.9.2 (retours de Guillaume : « structure assez laide et beaucoup trop longue ») : atelier ferroviaire = halle de depot (meme calcul
     * que shapes.py depot_role). Dalle qui deborde d'une case (trottoirs, parvis ; son milieu = les modules, sous la fosse et la voie
     * dessinees), murs en retrait (facade z = 1, fond z = c - 1), porte de 3 x {@link #DEPOT_DOOR} (la voie d'entree passe sur le
     * controleur, au bord du parvis), poteaux d'angle en cadres, toit a deux pans en marches de 2 cases qui deborde, lanterneau vitre au
     * faite, pignons fermes ; volume de la halle libre (pont roulant et vehicules dessines).
     */
    static int depotRole(int a, int b, int c, int x, int y, int z) {
        int h = (a - 1) / 2, dx = Math.abs(x - h), wall = h - 1;
        boolean end = z == 1 || z == c - 1;
        if (y == 0) {
            return dx < wall && z >= 2 && z <= c - 2 ? INTERIOR : SHELL;
        }
        if (y <= b) {
            if (dx == h) {
                return z == 0 || z == c - 1 ? FRAME : NONE;
            }
            if (z == 0) {
                return NONE;
            }
            if (dx == wall) {
                return SHELL;
            }
            if (end) {
                return z == 1 && dx <= 1 && y <= DEPOT_DOOR ? NONE : SHELL;
            }
            return NONE;
        }
        int k = y - b, n = depotSlopes(a);
        if (k <= n) {
            int lo = Math.max(DEPOT_LANTERN + 1, h - 2 * k + 1), hi = h - 2 * k + 2;
            if (dx >= lo && dx <= hi) {
                return SHELL;
            }
            return dx < lo && end ? SHELL : NONE;
        }
        if (k == n + 1) {
            if (dx == DEPOT_LANTERN) {
                return SHELL;
            }
            return dx < DEPOT_LANTERN && (end || z == 0) ? SHELL : NONE;
        }
        if (k == n + 2) {
            return dx <= DEPOT_LANTERN ? SHELL : NONE;
        }
        return NONE;
    }

    /**
     * 1.9.2 : case d'enveloppe de l'atelier ferroviaire proposee en verre (plan projete) : fenetres hautes des murs, lanterneau, verriere
     * des pignons, fenetres de la facade et du fond (meme regle que shapes.py depot_glass). Coordonnees canoniques.
     */
    public static boolean depotGlass(int a, int b, int c, int x, int y, int z) {
        if (depotRole(a, b, c, x, y, z) != SHELL) {
            return false;
        }
        int h = (a - 1) / 2, dx = Math.abs(x - h);
        boolean end = z == 1 || z == c - 1;
        if (y >= 2 && y <= b - 1) {
            if (dx == h - 1) {
                return z % 2 == 1 && z >= 2 && z <= c - 2;
            }
            if (z == 1) {
                return dx == h - 2;
            }
            return z == c - 1 && dx < h - 1 && x % 2 == 1;
        }
        int k = y - b;
        if (k == depotSlopes(a) + 1 && dx == DEPOT_LANTERN) {
            return z >= 2 && z <= c - 2;
        }
        return k == 1 && end && dx <= 1;
    }

    /** 1.0 : presse (meme calcul que shapes.py gantry_role) : banc, tirants en cadres, sommier, verin ; coulisseau suspendu, y = 1 libre. */
    static int gantryRole(int a, int b, int c, int x, int y, int z) {
        int cx = (a - 1) / 2, cz = (c - 1) / 2;
        if (y == 0 || y == b - 2) {
            return SHELL;
        }
        if (y == b - 1) {
            return Math.abs(x - cx) <= 1 && z == cz ? SHELL : NONE;
        }
        if ((x == 0 || x == a - 1) && (z == 0 || z == c - 1)) {
            return FRAME;
        }
        if (x >= 1 && x <= a - 2 && z >= 1 && z <= c - 2 && y >= 2) {
            return INTERIOR;
        }
        return NONE;
    }

    /** 1.0 : deux cuves et le melangeur (meme calcul que shapes.py twin_role). */
    static int twinRole(int a, int b, int x, int y, int z) {
        int cz = (a - 1) / 2;
        for (int x0 : new int[]{0, a + 3}) {
            int u = x - x0;
            if (u >= 0 && u < a) {
                if (!disk(a, u, z)) {
                    return NONE;
                }
                if (y == 0 || y == b - 1 || !disk(a, u + 1, z) || !disk(a, u - 1, z) || !disk(a, u, z + 1) || !disk(a, u, z - 1)) {
                    return SHELL;
                }
                return NONE;
            }
        }
        if (Math.abs(z - cz) > 1) {
            return NONE;
        }
        if (y == 0) {
            return SHELL;
        }
        if (z != cz) {
            return NONE;
        }
        if (x == a + 1) {
            return y <= b - 2 ? INTERIOR : FRAME;
        }
        return FRAME;
    }

    /** 1.0 : gabarit « construit » (roles propres, cadres, socle plat : controleur au ras du sol, pas de sommet). */
    public boolean built() {
        return this == HYPERBOLOID || this == RIG || this == JACK || this == LINE || this == GANTRY || this == TWIN || this == PWR || this == SFR || this == FFR || this == TOK
               || this == HEL || this == DEPOT;
    }

    private static boolean edgeDisk(int d, int x, int z) {
        return disk(d, x, z) && !(disk(d, x + 1, z) && disk(d, x - 1, z) && disk(d, x, z + 1) && disk(d, x, z - 1));
    }

    static boolean pwrFuel(int a, int x, int z) {
        int c = (a - 1) / 2, dx = x - c, dz = z - c;
        return Math.floorMod(dx, 2) == 0 && Math.floorMod(dz, 2) == 0 && Math.abs(dx) + Math.abs(dz) <= a / 2 && disk(a, x, z) && !edgeDisk(a, x, z);
    }

    /** Gen I (meme calcul que shapes.py pwr_role). */
    static int pwrRole(int a, int b, int x, int y, int z) {
        if (!disk(a, x, z)) {
            return NONE;
        }
        if (y == 0) {
            return SHELL;
        }
        boolean fuel = pwrFuel(a, x, z);
        if (y == b - 1) {
            //1.0.4-beta (lot 23) : plus de barre de commande (bloc retire) : couvercle tout en carcasses, barres integrees (builtInRods)
            return CASING;
        }
        if (edgeDisk(a, x, z)) {
            return SHELL;
        }
        return fuel ? FUEL : EMPTY;
    }

    /** Gen II (meme calcul que shapes.py sfr_role). */
    static int sfrRole(int a, int b, int x, int y, int z) {
        if (!disk(a, x, z)) {
            return NONE;
        }
        int c = (a - 1) / 2, dx = x - c, dz = z - c;
        if (y == 0) {
            return SHELL;
        }
        if (y == b - 1) {
            //1.0.4-beta (lot 23) : plus de barres, pompes primaires ni echangeurs intermediaires (blocs retires) : dalle toute en carcasses,
            //equipements integres (builtInRods, SFR_PUMPS, SFR_EXCHANGERS)
            return CASING;
        }
        if (edgeDisk(a, x, z)) {
            return SHELL;
        }
        double rc = Math.max(1.6, a / 5.0);
        if (dx * dx + dz * dz <= rc * rc && y <= Math.max(2, (b - 1) / 2)) {
            return FUEL;
        }
        return EMPTY;
    }

    /** Gen III (meme calcul que shapes.py ffr_role). */
    static int ffrRole(int a, int x, int y, int z) {
        int d = a + 6, h = a + 4, c = (d - 1) / 2, cy = 2 + (a - 1) / 2;
        double r = a / 2.0, r2 = r * r;
        int dx = x - c, dz = z - c, dy = y - cy;
        if (!disk(d, x, z)) {
            return NONE;
        }
        if (y == 0 || y == h - 1) {
            return dx == 0 && dz == 0 ? COLLECTOR : SHELL;
        }
        if (edgeDisk(d, x, z)) {
            return GLASS;
        }
        if (dx * dx + dy * dy + dz * dz <= r2) {
            boolean out = false;
            for (int[] e : DIRS) {
                int ex = dx + e[0], ey = dy + e[1], ez = dz + e[2];
                if (ex * ex + ey * ey + ez * ez > r2) {
                    out = true;
                    break;
                }
            }
            if (!out) {
                return EMPTY;
            }
            return dx == 0 || dz == 0 || dy == 0 ? REFLECTOR : GLASS;
        }
        double rho = Math.sqrt(dx * dx + dz * dz);
        if (dy == 0 && rho >= r + 0.5 && rho < r + 1.5) {
            return COIL;
        }
        int leg = Math.max(1, (int) (r * 0.45));
        if (Math.abs(dx) == leg && Math.abs(dz) == leg) {
            return FRAME;
        }
        return NONE;
    }

    static final double FUSION_R = 1.2;

    /** Lot 3 bis : role d'une case de tokamak ou d'heliotron sans les pieds (meme calcul que shapes.py fusion_base ; a = grand rayon). */
    static int fusionBase(boolean tok, int a, int x, int y, int z) {
        double r = FUSION_R, kap = tok ? 1.5 : 1.0;
        int span = a + 4, h = tok ? 9 : 8, c = span;
        double yc = h / 2.0;
        int dx = x - c, dz = z - c;
        double rho = Math.sqrt(dx * dx + dz * dz);
        if (y == 0) {
            return dx * dx + dz * dz <= (span + 0.3) * (span + 0.3) ? SHELL : NONE;
        }
        if (tok && rho <= 1.2 && y <= h - 2) {
            return SOLENOID;
        }
        if (injectorCell(tok, a, x, y, z)) {
            //1.0.4-beta (lot 23) : plus d'injecteur de neutres (bloc retire) : case libre ; injection integree (builtInInjectors)
            return NONE;
        }
        double phi = Math.atan2(dz, dx);
        double u = rho - a, v = (y - yc) / kap, ds = Math.sqrt(u * u + v * v);
        boolean oncoil = false;
        if (tok) {
            for (int k = 0; k < 16; k++) {
                double pk = 2 * Math.PI * k / 16;
                if (Math.abs(rho * Math.sin(phi - pk)) <= 0.45 && Math.cos(phi - pk) > 0) {
                    oncoil = true;
                    break;
                }
            }
        } else {
            double th = Math.atan2(y - yc, rho - a);
            double hel = ((th - 5 * phi) % Math.PI + Math.PI) % Math.PI;
            oncoil = Math.min(hel, Math.PI - hel) < 0.5;
        }
        if (ds > r + 0.5 && ds <= r + 1.5 && oncoil) {
            return tok ? TFCOIL : HELIX;
        }
        if (ds > r - 0.5 && ds <= r + 0.5) {
            return y < yc - 0.5 && !oncoil ? LINING : CHAMBER;
        }
        if (ds <= r - 0.5) {
            return EMPTY;
        }
        return NONE;
    }

    /** Lot 3 bis : case de l'ancien injecteur de neutres du tokamak (1.0.4-beta, lot 23 : case libre). */
    static boolean injectorCell(boolean tok, int a, int x, int y, int z) {
        double r = FUSION_R;
        int span = a + 4, h = tok ? 9 : 8, c = span;
        double yc = h / 2.0;
        double rho = Math.sqrt((x - c) * (x - c) + (z - c) * (z - c));
        return tok && y == (int) yc && z == c + 3 && rho - a > r + 0.5 && rho - a <= r + 3.5;
    }

    /** 1.0.4-beta (lot 23) : pompes primaires et echangeurs intermediaires integres a la dalle du Gen II (le gabarit en exigeait 2 + 2). */
    public static final int SFR_PUMPS = 2, SFR_EXCHANGERS = 2;

    /**
     * 1.0.4-beta (lot 23) : barres de commande INTEGREES (blocs retires) : autant que l'ancien gabarit en exigeait (Gen I : une au-dessus de
     * chaque colonne de canaux ; Gen II : les 5 cases du centre de la dalle) ; {@code a} = diametre (largeur de la structure).
     */
    public static int builtInRods(IndustryShape shape, int a) {
        int n = 0, c = (a - 1) / 2;
        for (int x = 0; x < a; x++) {
            for (int z = 0; z < a; z++) {
                int dx = x - c, dz = z - c;
                if (shape == PWR ? pwrFuel(a, x, z) : shape == SFR && disk(a, x, z) && dx * dx + dz * dz <= 1) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 1.0.4-beta (lot 23) : injecteurs de neutres INTEGRES au tokamak (anciennes cases / 3, comme FusionCore) ; {@code a} = grand rayon. */
    public static int builtInInjectors(int a) {
        int n = 0, span = a + 4, h = 9;
        for (int x = 0; x <= 2 * span; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z <= 2 * span; z++) {
                    if (injectorCell(true, a, x, y, z)) {
                        n++;
                    }
                }
            }
        }
        return (n + 2) / 3;
    }

    /** Lot 3 bis : role complet (pieds en cadres sous la chambre). */
    static int fusionRole(boolean tok, int a, int x, int y, int z) {
        int r0 = fusionBase(tok, a, x, y, z);
        if (r0 != NONE || y < 1 || y >= (tok ? 9 : 8) / 2.0) {
            return r0;
        }
        int c = a + 4, n = tok ? 16 : 12;
        for (int k = 0; k < n; k++) {
            double pk = 2 * Math.PI * k / n;
            if (x == c + (int) Math.floor(a * Math.cos(pk) + 0.5) && z == c + (int) Math.floor(a * Math.sin(pk) + 0.5)) {
                return FRAME;
            }
        }
        return NONE;
    }

    /** 1.0 : role d'une case (hors boite : rien). */
    public int role(int a, int b, int c, int x, int y, int z) {
        int[] s = bbox(a, b, c);
        if (x < 0 || y < 0 || z < 0 || x >= s[0] || y >= s[1] || z >= s[2]) {
            return NONE;
        }
        if (this == RIG) {
            return rigRole(a, b, x, y, z);
        }
        if (this == HYPERBOLOID) {
            return hyperRole(a, b, x, y, z);
        }
        if (this == JACK) {
            return jackRole(b, c, x, y, z);
        }
        if (this == LINE) {
            return lineRole(a, b, c, x, y, z);
        }
        if (this == GANTRY) {
            return gantryRole(a, b, c, x, y, z);
        }
        if (this == TWIN) {
            return twinRole(a, b, x, y, z);
        }
        if (this == PWR) {
            return pwrRole(a, b, x, y, z);
        }
        if (this == SFR) {
            return sfrRole(a, b, x, y, z);
        }
        if (this == FFR) {
            return ffrRole(a, x, y, z);
        }
        if (this == TOK || this == HEL) {
            return fusionRole(this == TOK, a, x, y, z);
        }
        if (this == DEPOT) {
            return depotRole(a, b, c, x, y, z);
        }
        return !solid(a, b, c, x, y, z) ? NONE : shell(a, b, c, x, y, z) ? SHELL : INTERIOR;
    }

    /** Case pleine du gabarit ? */
    public boolean solid(int a, int b, int c, int x, int y, int z) {
        int[] s = bbox(a, b, c);
        if (x < 0 || y < 0 || z < 0 || x >= s[0] || y >= s[1] || z >= s[2]) {
            return false;
        }
        return switch (this) {
            case BOX, POOL, FREEFORM -> true;
            case TOWER -> disk(a, x, z);
            case BELLY -> {
                double r = bellyRadius(a, b, y);
                yield disk(a + 2, x, z, r * r);
            }
            case DRUM -> disk(a, x, y);
            case DOME -> {
                if (y < b) {
                    yield disk(a, x, z);
                }
                double cc = (a - 1) / 2.0;
                int dy = y - (b - 1);
                yield (x - cc) * (x - cc) + (z - cc) * (z - cc) + dy * dy <= a * a / 4.0;
            }
            case HALL -> {
                if (y <= b) {
                    yield true;
                }
                int k = y - b;
                yield x >= k && x <= a - 1 - k;
            }
            case SPHERE -> {
                double cc = (a - 1) / 2.0;
                yield (x - cc) * (x - cc) + (y - cc) * (y - cc) + (z - cc) * (z - cc) <= a * a / 4.0;
            }
            case TORUS -> torus(a, b, x, y, z);
            case CONE -> {
                double r = coneRadius(a, b, y);
                yield disk(a, x, z, r * r);
            }
            case RIG -> rigRole(a, b, x, y, z) != NONE;
            case HYPERBOLOID, JACK, LINE, GANTRY, TWIN, PWR, SFR, FFR, TOK, HEL, DEPOT -> role(a, b, c, x, y, z) != NONE;
        };
    }

    /** Case de l'enveloppe ? (bassin : le dessus est ouvert, ce n'est pas une paroi) */
    public boolean shell(int a, int b, int c, int x, int y, int z) {
        if (built()) {
            int r = role(a, b, c, x, y, z);
            return r == SHELL || r == CASING;
        }
        if (!solid(a, b, c, x, y, z)) {
            return false;
        }
        int top = bbox(a, b, c)[1];
        for (int[] d : DIRS) {
            int ny = y + d[1];
            if (this == POOL && ny >= top) {
                continue;
            }
            if (!solid(a, b, c, x + d[0], ny, z + d[2])) {
                return true;
            }
        }
        return false;
    }

    static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** Case canonique du controleur : base de la facade, au centre (fut : centre du fond avant). */
    public int[] controller(int a, int b, int c) {
        int[] s = bbox(a, b, c);
        int cx = (s[0] - 1) / 2, cy = this == DRUM ? (a - 1) / 2 : built() ? 0 : 1;
        for (int z = 0; z < s[2]; z++) {
            if (solid(a, b, c, cx, cy, z)) {
                return new int[]{cx, cy, z};
            }
        }
        return new int[]{cx, cy, 0};
    }

    /** Sommet (case du toit au-dessus de laquelle se pose la piece de la zone TOP) ou null (bassin). */
    public int[] summit(int a, int b, int c) {
        if (this == POOL || this == TORUS || built()) {
            return null;
        }
        int[] s = bbox(a, b, c);
        return new int[]{(s[0] - 1) / 2, s[1] - 1, (s[2] - 1) / 2};
    }

    /** Zones d'une case de l'enveloppe (masque de bits {@link Signature.Zone#bit} ; TOP : case rapportee, voir {@link ShapeTemplate}). */
    public int zones(int a, int b, int c, int x, int y, int z) {
        int[] s = bbox(a, b, c);
        int m = 0;
        if (y == 1 && this != DRUM) {
            m |= Signature.Zone.LOW_RING.bit;
        }
        if (y == s[1] - 1) {
            m |= this == POOL ? Signature.Zone.RIM.bit : Signature.Zone.RIDGE.bit;
        }
        if (z == s[2] - 1) {
            m |= Signature.Zone.BACK.bit;
        }
        //0.17.1 : dalle (toit plat sans son bord) et equateur de la sphere
        if (y == s[1] - 1 && this != POOL && solid(a, b, c, x + 1, y, z) && solid(a, b, c, x - 1, y, z) && solid(a, b, c, x, y, z + 1)
            && solid(a, b, c, x, y, z - 1)) {
            m |= Signature.Zone.ROOF.bit;
        }
        if (this == SPHERE && y == (a - 1) / 2) {
            m |= Signature.Zone.EQUATOR.bit;
        }
        //0.17.2 : anneaux exterieur et interieur (face au trou) du tore, a mi-hauteur
        if (this == TORUS && y == (b - 1) / 2) {
            m |= torusRho(a, x, z) > torusMid(a) ? Signature.Zone.OUTER.bit : Signature.Zone.INNER.bit;
        }
        return m;
    }

    /**
     * Ecart aux proportions caracteristiques du gabarit (0 = ideal) : tours elancees, futs longs, halles allongees, bassins peu profonds...
     * Sert au choix des plans projetes (meme formule dans shapes.py).
     */
    public double aspectPenalty(int a, int b, int c) {
        return switch (this) {
            case TOWER -> Math.abs(Math.log(b / (a * 2.5)));
            case BELLY -> Math.abs(Math.log(b / ((a + 2) * 1.8)));
            case HYPERBOLOID -> Math.abs(Math.log(b / (a * 1.6)));
            case DRUM -> Math.abs(Math.log(c / (a * 2.2)));
            case DOME -> Math.abs(Math.log(b / (a * 0.8)));
            case HALL -> Math.abs(Math.log(c / (a * 1.5))) + Math.abs(b - 3) * 0.3;
            case POOL -> Math.abs(Math.log(c / (a * 1.4))) + Math.abs(b - 3) * 0.3;
            case TORUS -> Math.abs(Math.log(b / (a * 0.4)));
            case CONE -> Math.abs(Math.log(b / (a * 1.6)));
            case RIG -> Math.abs(Math.log(b / (a * 2.4)));
            case JACK -> Math.abs(Math.log(c / (b * 1.5)));
            case LINE -> Math.abs(Math.log(c / (a * 2.5))) + Math.abs(b - 4) * 0.3;
            case GANTRY -> Math.abs(Math.log(b / (a * 1.2))) + Math.abs(Math.log((double) c / a)) * 0.5;
            case TWIN -> Math.abs(Math.log(b / (a * 1.8)));
            case PWR, SFR -> Math.abs(Math.log(b / (a * 0.9)));
            case DEPOT -> Math.abs(Math.log(c / (a * 1.25))) + Math.abs(b - 4) * 0.3;
            default -> 0;
        };
    }

    /** Candidat utilisable (au moins une case interieure, controleur au-dessus du fond). */
    public boolean valid(int a, int b, int c) {
        return switch (this) {
            case DOME, POOL -> b >= 2;
            case HALL -> b >= 2 && a >= 3;
            case TOWER, BELLY, BOX -> b >= 3;
            case HYPERBOLOID -> a >= 9 && b >= 10;
            case SPHERE -> a >= 5;
            case TORUS -> a >= 9 && b >= 3;
            case CONE -> a >= 5 && b >= 4;
            case RIG -> a >= 5 && b >= 8;
            case JACK -> a == 3 && b >= 6 && c >= 8;
            case LINE -> a >= 3 && b >= 4 && c >= 5;
            case GANTRY -> a >= 5 && b >= 6 && c >= 3;
            case TWIN -> a >= 3 && a != 5 && b >= 4;
            case PWR, SFR -> a >= 7 && b >= 5;
            case FFR, TOK, HEL -> a >= 7;
            case DEPOT -> a >= 9 && b >= DEPOT_DOOR + 1 && c >= 6;
            default -> true;
        };
    }
}
