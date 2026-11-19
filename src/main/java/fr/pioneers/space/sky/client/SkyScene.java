package fr.pioneers.space.sky.client;

import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.sky.client.Celestial.Body;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Ce que voit un observateur pose sur un astre, a un instant donne (calcul pur, sans Minecraft).
 * <p>
 * Repere du ciel (celui du Soleil de Minecraft, avant la rotation du jour) : le Soleil est toujours en +Y,
 * l'ecliptique est le plan YZ (les planetes suivent donc le chemin du Soleil), le pole nord de l'ecliptique est en -X.
 * Un astre a l'est du Soleil (longitude plus grande) a un Z negatif : il se couche apres lui, comme l'etoile du soir.
 * Le rendu applique ensuite YP(-90) puis XP(heure * 360), exactement comme le Soleil vanilla.
 * <p>
 * Lunes en rotation synchrone : leur planete reste immobile dans le ciel. Le jour local d'une telle lune dure une
 * revolution ; la position de la lune sur son orbite est donc deduite de l'heure, ce qui donne les bonnes phases
 * (pleine planete a minuit, nouvelle a midi) et les eclipses quand le Soleil passe derriere elle.
 * <p>
 * 1.0.7 (« Plus loin et au-dela », lot 4) : les MEGASTRUCTURES debloquees sont un parametre du calcul (masque {@code megas}, lu par le
 * client dans l'etat synchronise) : dans le ciel de leur astre, un objet {@link #MEGA} porte leur geometrie (cable de l'Ascenseur : de
 * l'horizon presque au zenith, fixe par rapport au sol) et ce que la lumiere du Soleil en eclaire ; vu d'un autre astre, le disque de leur
 * astre porte leur marque ({@link Item#megaMarks}).
 */
public final class SkyScene {

    public static final int SUN = 0, POINT = 1, DISC = 2, MEGA = 3;
    /** 1.0.7 (lot 4) : geometries des megastructures (index de T:au_dela/megastructures.py SKY_KINDS). */
    public static final int MEGA_TETHER = 0, MEGA_RING = 1, MEGA_PLUME = 2, MEGA_LIGHTS = 3, MEGA_ROTOR = 4, MEGA_AURORA = 5, MEGA_COLUMN = 6, MEGA_SWARM = 7;
    /** Astre du ciel de chaque megastructure (meme nom que l'astre du systeme solaire), ou null. */
    private static final Body[] MEGA_BODY = new Body[Megastructures.COUNT];

    static {
        for (int i = 0; i < Megastructures.COUNT; i++) {
            try {
                MEGA_BODY[i] = Body.valueOf(Megastructures.body(i).name());
            } catch (IllegalArgumentException e) {
                MEGA_BODY[i] = null;
            }
        }
    }

    /** Pole de Saturne (et de ses anneaux) en coordonnees ecliptiques. */
    private static final double[] SATURN_POLE = unitFromLonLat(79.5, 61.9);

    public static final class Item {
        public int type;
        public Body body;
        /** Direction (unitaire) dans le repere du ciel. */
        public double x, y, z;
        /** Distance (km), pour trier du plus lointain au plus proche. */
        public double dist;
        /** Rayon apparent a l'ecran (degres) pour le Soleil et les disques. */
        public double visual;
        /** Rayon angulaire reel (rad). */
        public double alpha;
        public double mag;
        /** Direction de la lumiere du Soleil arrivant sur l'astre (de l'astre vers le Soleil). */
        public double lx, ly, lz;
        public float discAlpha = 1, pointAlpha = 1;
        public boolean ring;
        /** Pole des anneaux dans le repere du ciel. */
        public double px, py, pz;
        /** 1.0.7 (lot 4) : megastructures debloquees de cet astre, vues d'ailleurs (bit i : marque sur son disque). */
        public long megaMarks;
        /** Objet MEGA : index de la megastructure, sorte de geometrie ; cable : base (x, y, z = sommet ; bx, by, bz = base). */
        public int mega = -1, megaKind;
        public double bx, by, bz;
        /** Cable : part a l'ombre depuis la base (0 = tout eclaire, 1 = tout dans l'ombre de l'astre), eclat de l'aube (0..1), largeur (deg). */
        public double shadow, glint, width;
        /**
         * Anneau (lot 5) : x, y, z = sommet de l'arc, bx, by, bz = point est de l'horizon ; ux, uy, uz = verticale du lieu (repere du ciel) ;
         * rayon de l'anneau en rayons de l'astre ; largeur de la bande des reflets autour du terminateur (deg) ; surcroit du cote jour.
         */
        public double ux, uy, uz, ratio, terminator, dayBoost;
    }

    /** Reglages d'un observateur : l'astre, et pour une lune en rotation synchrone l'astre fixe de son ciel. */
    public static final class Observer {
        public final Body body;
        public final Body anchor;
        /** Position de l'astre fixe : heure solaire equivalente (degres, 0 = zenith, negatif = est) et decalage au nord. */
        public final double psi0, delta;
        public final boolean belt;

        public Observer(Body body, Body anchor, double psi0, double delta, boolean belt) {
            this.body = body;
            this.anchor = anchor;
            this.psi0 = psi0;
            this.delta = delta;
            this.belt = belt;
        }
    }

    private static final Map<Body, Observer> OBSERVERS = new EnumMap<>(Body.class);

    static {
        put(new Observer(Body.EARTH, null, 0, 0, false));
        put(new Observer(Body.MERCURY, null, 0, 0, false));
        put(new Observer(Body.VENUS, null, 0, 0, false));
        put(new Observer(Body.MARS, null, 0, 0, false));
        put(new Observer(Body.MOON, Body.EARTH, -30, 25, false));
        put(new Observer(Body.PHOBOS, Body.MARS, 20, 15, false));
        put(new Observer(Body.DEIMOS, Body.MARS, -25, 20, false));
        put(new Observer(Body.CERES, null, 0, 0, true));
        put(new Observer(Body.VESTA, null, 0, 0, true));
        put(new Observer(Body.PALLAS, null, 0, 0, true));
        put(new Observer(Body.HYGIEA, null, 0, 0, true));
        put(new Observer(Body.IO, Body.JUPITER, 15, 10, false));
        put(new Observer(Body.EUROPA, Body.JUPITER, -20, 12, false));
        put(new Observer(Body.GANYMEDE, Body.JUPITER, 25, 14, false));
        put(new Observer(Body.CALLISTO, Body.JUPITER, -15, 30, false));
        put(new Observer(Body.ENCELADUS, Body.SATURN, -20, 38, false));
        put(new Observer(Body.TITAN, Body.SATURN, 10, 20, false));
        put(new Observer(Body.MIRANDA, Body.URANUS, 10, 35, false));
        put(new Observer(Body.TITANIA, Body.URANUS, -30, 30, false));
        put(new Observer(Body.TRITON, Body.NEPTUNE, 20, 30, false));
        put(new Observer(Body.PLUTO, Body.CHARON, -20, 30, false));
        put(new Observer(Body.CHARON, Body.PLUTO, 25, 30, false));
    }

    private static void put(Observer observer) {
        OBSERVERS.put(observer.body, observer);
    }

    public static Observer observer(Body body) {
        return OBSERVERS.get(body);
    }

    public final Observer observer;
    /** Astres, du plus lointain au plus proche (ordre de dessin). */
    public final List<Item> items = new ArrayList<>();
    /** Longitude ecliptique du Soleil vu de l'observateur (rad) : rotation des etoiles lointaines. */
    public double sunLongitude;
    /** Distance au Soleil (km). */
    public double sunDistance;
    public Item sun;
    /**
     * 1.0.7 (lot 9) : lumiere du Soleil gardee (0..1) et sa teinte (rouge, vert, bleu), baissees par l'Essaim dans tout le systeme
     * solaire ; 1 sans lui.
     */
    public double sunLight = 1, sunTintR = 1, sunTintG = 1, sunTintB = 1;

    private final double[] e1 = new double[3], e2 = new double[3], e3 = new double[3];

    private SkyScene(Observer observer) {
        this.observer = observer;
    }

    /**
     * @param t       jour du monde (dayTime / 24000)
     * @param timeDeg rotation du ciel (heure * 360, celle appliquee au Soleil)
     */
    public static SkyScene compute(Observer obs, double t, double timeDeg) {
        return compute(obs, t, timeDeg, 0L);
    }

    /** @param megas megastructures debloquees (bit i), etat synchronise du client (1.0.7, lot 4) */
    public static SkyScene compute(Observer obs, double t, double timeDeg, long megas) {
        SkyScene scene = new SkyScene(obs);
        scene.build(t, timeDeg);
        if (megas != 0) {
            scene.megastructures(megas, timeDeg);
        }
        return scene;
    }

    /** 1.0.7 (lot 4) : objets MEGA du ciel de l'observateur, marques sur les disques des autres astres. */
    private void megastructures(long megas, double timeDeg) {
        for (int i = 0; i < Megastructures.COUNT; i++) {
            if ((megas & (1L << i)) == 0 || MEGA_BODY[i] == null) {
                continue;
            }
            if (Megastructures.skyKind(i) == MEGA_SWARM) {
                //1.0.7 (lot 9) : l'Essaim voile et teinte le Soleil dans tout le systeme (les ciels de SkyScene sont ceux du systeme solaire)
                double[] p = Megastructures.skyParams(i);
                sunLight = Math.min(sunLight, p[0]);
                sunTintR *= p[1];
                sunTintG *= p[2];
                sunTintB *= p[3];
            }
            if (MEGA_BODY[i] == observer.body) {
                if (Megastructures.skyKind(i) == MEGA_TETHER) {
                    items.add(tether(i, timeDeg));
                } else if (Megastructures.skyKind(i) == MEGA_RING) {
                    items.add(ring(i, timeDeg));
                } else if (Megastructures.skyKind(i) == MEGA_PLUME) {
                    items.add(plume(i, timeDeg));
                }
                continue;
            }
            for (Item item : items) {
                if (item.body == MEGA_BODY[i] && item.type != MEGA) {
                    item.megaMarks |= 1L << i;
                }
            }
        }
    }

    /**
     * Cable d'un ascenseur spatial vu de son astre, fixe par rapport au sol : de l'horizon (azimut reglable) presque jusqu'au zenith. Un
     * point du cable a la hauteur h est eclaire tant que le Soleil est au-dessus de -acos(R / (R + h)) : au crepuscule le haut du cable
     * reste dans la lumiere quand le sol est deja dans l'ombre ; l'eclat est le plus fort quand le Soleil frole l'horizon.
     */
    private Item tether(int i, double timeDeg) {
        double az = Math.toRadians(Megastructures.sky(i, 0)), alt = Math.toRadians(Megastructures.sky(i, 1));
        double top = Megastructures.sky(i, 3), radius = Megastructures.sky(i, 4);
        Item item = new Item();
        item.type = MEGA;
        item.body = MEGA_BODY[i];
        item.mega = i;
        item.megaKind = MEGA_TETHER;
        item.dist = 1;
        item.width = Megastructures.sky(i, 2);
        double[] p = new double[3];
        ground(0, az, timeDeg, p);
        item.bx = p[0];
        item.by = p[1];
        item.bz = p[2];
        ground(alt, az, timeDeg, p);
        item.x = p[0];
        item.y = p[1];
        item.z = p[2];
        //hauteur du Soleil au-dessus de l'horizon (deg) : le Soleil du repere du sol est en (-sin t, cos t, 0)
        double sunAlt = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, Math.cos(Math.toRadians(timeDeg))))));
        if (sunAlt >= 0) {
            item.shadow = 0;
        } else {
            double dip = Math.toRadians(-sunAlt);
            double needed = dip >= Math.PI / 2 ? Double.MAX_VALUE : radius * (1 / Math.cos(dip) - 1);
            //hauteur le long du cable : h(s) = sommet * s^3 (le cable s'eloigne vite du sol vu d'en bas)
            item.shadow = Math.min(1, Math.cbrt(needed / top));
        }
        item.glint = Megastructures.sky(i, 5) * Math.exp(-(sunAlt / 12) * (sunAlt / 12));
        return item;
    }

    /**
     * Anneau de miroirs en orbite equatoriale (Diademe), vu de la surface : arc d'un horizon a l'autre (est, sommet, ouest), fixe par rapport
     * au sol. L'eclairage de chaque point de l'arc se calcule a part ({@link #ringShade}), sans allocation, au rendu comme au banc.
     */
    private Item ring(int i, double timeDeg) {
        Item item = new Item();
        item.type = MEGA;
        item.body = MEGA_BODY[i];
        item.mega = i;
        item.megaKind = MEGA_RING;
        item.dist = 1;
        double[] p = new double[3];
        ground(Math.toRadians(Megastructures.sky(i, 1)), Math.toRadians(Megastructures.sky(i, 0)), timeDeg, p);
        item.x = p[0];
        item.y = p[1];
        item.z = p[2];
        ground(0, Math.toRadians(90), timeDeg, p);
        item.bx = p[0];
        item.by = p[1];
        item.bz = p[2];
        ground(Math.PI / 2, 0, timeDeg, p);
        item.ux = p[0];
        item.uy = p[1];
        item.uz = p[2];
        item.width = Megastructures.sky(i, 2);
        item.ratio = Megastructures.sky(i, 3) / Megastructures.sky(i, 4);
        item.glint = Megastructures.sky(i, 5);
        item.terminator = Megastructures.sky(i, 6);
        item.dayBoost = Megastructures.sky(i, 7);
        return item;
    }

    /**
     * Eclat (0 = dans l'ombre de l'astre) du point de l'anneau vu dans la direction (dx, dy, dz) du repere du ciel : l'astre a pour rayon 1 et
     * pour centre l'origine, l'observateur est a sa surface (verticale du lieu), le Soleil en +Y. Plus clair du cote jour, reflets les plus
     * vifs pres du terminateur (plan perpendiculaire au Soleil).
     */
    public static double ringShade(Item ring, double dx, double dy, double dz) {
        double od = ring.ux * dx + ring.uy * dy + ring.uz * dz;
        double disc = od * od - (1 - ring.ratio * ring.ratio);
        if (disc < 0) {
            return 0;
        }
        double t = -od + Math.sqrt(disc);
        double px = ring.ux + dx * t, py = ring.uy + dy * t, pz = ring.uz + dz * t;
        //ombre : derriere l'astre par rapport au Soleil (+Y), a moins d'un rayon de l'axe
        if (py < 0 && px * px + pz * pz < 1) {
            return 0;
        }
        double cos = py / ring.ratio;
        double a = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
        double g = ring.glint * Math.exp(-((a - 90) / ring.terminator) * ((a - 90) / ring.terminator));
        return 0.45 + ring.dayBoost * Math.max(0, cos) + g;
    }

    /**
     * Lueur et panache d'un puits de forage (Grand Forage) : x, y, z = haut du panache, bx, by, bz = sa base sur l'horizon ; width = largeur
     * du panache, terminator = demi-largeur de la lueur sur l'horizon (degres) ; glint = eclat, plus fort la nuit (la lueur du manteau perce
     * mieux les nuages quand le jour baisse).
     */
    private Item plume(int i, double timeDeg) {
        Item item = new Item();
        item.type = MEGA;
        item.body = MEGA_BODY[i];
        item.mega = i;
        item.megaKind = MEGA_PLUME;
        item.dist = 1;
        double az = Math.toRadians(Megastructures.sky(i, 0));
        double[] p = new double[3];
        ground(0, az, timeDeg, p);
        item.bx = p[0];
        item.by = p[1];
        item.bz = p[2];
        ground(Math.toRadians(Megastructures.sky(i, 2)), az, timeDeg, p);
        item.x = p[0];
        item.y = p[1];
        item.z = p[2];
        item.terminator = Megastructures.sky(i, 1);
        item.width = Megastructures.sky(i, 3);
        double sunAlt = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, Math.cos(Math.toRadians(timeDeg))))));
        item.glint = Megastructures.sky(i, 4) * (0.35 + 0.65 * smoothstep(6, -12, sunAlt));
        item.ux = az;
        return item;
    }

    /** Direction (altitude, azimut depuis le nord vers l'est) du repere du sol vers le repere du ciel a l'heure donnee. */
    public static void ground(double alt, double az, double timeDeg, double[] out) {
        //sol : x = est, y = haut, z = sud
        double gx = Math.cos(alt) * Math.sin(az), gy = Math.sin(alt), gz = -Math.cos(alt) * Math.cos(az);
        //inverse du rendu YP(-90) puis XP(t) : YP(90), puis XP(-t)
        double x = gz, y = gy, z = -gx;
        double t = Math.toRadians(timeDeg);
        out[0] = x;
        out[1] = y * Math.cos(t) + z * Math.sin(t);
        out[2] = -y * Math.sin(t) + z * Math.cos(t);
    }

    /** Partie fractionnaire (tirages fixes des feux de la Cite). */
    private static double frac(double v) {
        return v - Math.floor(v);
    }

    /**
     * 1.0.7 (lot 7) : feu {@code k} d'une cite flottante vue de sa geante (ciel des geantes, repere du sol : x est, y haut, z sud) : sa
     * direction dans {@code out} et son eclat (0..1). Les plates-formes sont fixes autour de l'horizon (tirage fixe par feu, les plus
     * lointaines plus basses), plus eclatantes la nuit ({@code night} : 0 en plein jour, 1 la nuit ; rapport nuit / jour = reglage 5),
     * et scintillent dans la brume ; un feu sur quatre est un phare a eclats. p : reglages SKY de la megastructure.
     */
    public static double cityLight(double[] p, int k, double night, double ticks, double[] out) {
        double az = 2 * Math.PI * frac(k * 0.6180339887 + 0.137);
        double depth = frac(k * 0.4142135 + 0.71);
        double el = Math.toRadians(p[1] + (p[2] - p[1]) * depth * depth);
        out[0] = Math.cos(el) * Math.sin(az);
        out[1] = Math.sin(el);
        out[2] = -Math.cos(el) * Math.cos(az);
        double base = 0.55 + 0.45 * frac(k * 0.318 + 0.05);
        double dayNight = 1 / p[5] + (1 - 1 / p[5]) * Math.max(0, Math.min(1, night));
        double twinkle = 1 - p[4] * 0.5 * (1 + Math.sin(ticks * 0.09 + k * 2.3));
        if (k % 4 == 3) {
            //phare a eclats : allume 15 ticks sur 60
            twinkle = (ticks + k * 13) % 60 < 15 ? 1 : 0.15;
        }
        return Math.max(0, Math.min(1, base * dayNight * twinkle));
    }

    /**
     * 1.0.7 (lot 7) : feu {@code k} d'une cite flottante vu d'ailleurs, sur le disque de sa geante : coordonnees du disque (unite = rayon ;
     * x selon la droite r, y selon le haut u) dans {@code out}, eclat (0..1), nul du cote jour. n : visee du disque ; l : lumiere du Soleil
     * arrivant sur l'astre (de l'astre vers le Soleil) ; feux dans une bande equatoriale.
     */
    public static double cityMark(double[] p, int k, double rx, double ry, double rz, double ux, double uy, double uz, double nx, double ny,
          double nz, double lx, double ly, double lz, double[] out) {
        double x = -0.85 + 1.7 * frac(k * 0.6180339887 + 0.13), y = (frac(k * 0.4142135 + 0.71) - 0.5) * 0.5;
        double r2 = x * x + y * y;
        if (r2 > 0.85) {
            double f = Math.sqrt(0.85 / r2);
            x *= f;
            y *= f;
            r2 = 0.85;
        }
        out[0] = x;
        out[1] = y;
        double sz = Math.sqrt(1 - r2);
        //normale de la sphere en ce point (vers l'observateur = -n), comme l'eclairage du disque
        double gx = x * rx + y * ux - sz * nx, gy = x * ry + y * uy - sz * ny, gz = x * rz + y * uz - sz * nz;
        double mu0 = gx * lx + gy * ly + gz * lz;
        return Math.max(0, Math.min(1, -mu0 * 4)) * (0.6 + 0.4 * frac(k * 0.318 + 0.05));
    }

    /** Bascule douce de 0 (sous a) a 1 (au-dessus de b). */
    private static double step(double a, double b, double v) {
        double x = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return x * x * (3 - 2 * x);
    }

    /**
     * 1.0.7 (lot 8) : arc des anneaux de Saturne vu de la geante (Anneau-Rotor) : point {@code theta} (0 = est, pi / 2 = sommet au sud, pi =
     * ouest) dans {@code out} (repere du sol : x est, y haut, z sud). p : (segments, surcroit des segments, hauteur du sommet en degres,
     * largeur en degres, part allumee d'un segment 0..1, vitesse de rotation en tours de segment par tick).
     */
    public static void rotorArc(double[] p, double theta, double[] out) {
        double a = Math.toRadians(p[2]);
        out[0] = Math.cos(theta);
        out[1] = Math.sin(theta) * Math.sin(a);
        out[2] = Math.sin(theta) * Math.cos(a);
    }

    /** Eclat de l'arc des anneaux en {@code theta} : le fond, et des segments reguliers plus vifs qui tournent lentement (le rotor). */
    public static double rotorShade(double[] p, double theta, double ticks) {
        double u = frac(theta * p[0] / (2 * Math.PI) + ticks * p[5]);
        double on = step(0, 0.06, u) * (1 - step(p[4] - 0.06, p[4], u));
        return 0.45 + p[1] * on;
    }

    /**
     * 1.0.7 (lot 8) : aurore permanente de la Boucle vue de sa geante : eclat (0..1) du rideau a l'azimut {@code az} (radians), et dans
     * {@code out[0]} la hauteur de son bord haut (degres) ; le bas du rideau est a p[0]. Les rideaux ondulent avec le temps ; plus vifs la
     * nuit. p : (hauteur du bas en degres, hauteur du haut en degres, ondulations par tour, vitesse, eclat de jour, eclat de nuit).
     */
    public static double aurora(double[] p, double az, double ticks, double night, double[] out) {
        double w = 0.5 + 0.5 * Math.sin(az * p[2] + ticks * p[3]);
        double w2 = 0.5 + 0.5 * Math.sin(az * (2 * p[2] + 1) - ticks * p[3] * 1.7);
        out[0] = p[0] + (p[1] - p[0]) * (0.6 + 0.4 * w2);
        double level = p[4] + (p[5] - p[4]) * Math.max(0, Math.min(1, night));
        return level * (0.35 + 0.65 * w);
    }

    /**
     * 1.0.7 (lot 8) : colonne lumineuse de la Descente vue de sa geante : direction du point {@code s} de la colonne (0 = le haut, 1 = sous
     * l'horizon, dans les nuages) dans {@code out}, et son eclat : des impulsions qui descendent le long de la colonne. p : (azimut, hauteur
     * du haut en degres, largeur en degres, vitesse des impulsions, eclat 0..1).
     */
    public static double column(double[] p, double s, double ticks, double[] out) {
        double az = Math.toRadians(p[0]), el = Math.toRadians(p[1] + (-3 - p[1]) * s);
        out[0] = Math.cos(el) * Math.sin(az);
        out[1] = Math.sin(el);
        out[2] = -Math.cos(el) * Math.cos(az);
        double pulse = 0.5 + 0.5 * Math.sin(s * 18 - ticks * p[3]);
        return p[4] * (0.55 + 0.45 * pulse) * (1 - step(0.85, 1, s));
    }

    /**
     * 1.0.7 (lot 9) : eclat {@code k} de l'Essaim autour du disque du Soleil : coordonnees dans le plan du disque (unite = rayon du disque)
     * dans {@code out}, eclat (0..1) qui scintille ; les eclats tournent lentement sur des orbites de 1,3 a p[5] rayons. p : reglages SKY.
     */
    public static double swarmGlint(double[] p, int k, double ticks, double[] out) {
        double r = 1.3 + (p[5] - 1.3) * frac(k * 0.4142135 + 0.3);
        double a = 2 * Math.PI * frac(k * 0.6180339887) + ticks * 0.002 / r;
        //orbites inclinees : l'essaim est un nuage, pas un anneau
        double tilt = 0.35 + 0.65 * frac(k * 0.271 + 0.5);
        out[0] = r * Math.cos(a);
        out[1] = r * Math.sin(a) * tilt;
        return (0.35 + 0.65 * frac(k * 0.318 + 0.05)) * (0.6 + 0.4 * Math.sin(ticks * 0.11 + k * 1.7));
    }

    private void build(double t, double timeDeg) {
        Body self = observer.body;
        Body anchor = observer.anchor;
        double[] posO;
        double anchorDist = 0;
        if (anchor != null && anchor == self.parent()) {
            //Lune synchrone : sa position sur l'orbite suit l'heure pour que la planete reste immobile
            double[] parent = Celestial.position(anchor, t);
            double theta = Celestial.longitude(parent) + Math.toRadians(timeDeg - observer.psi0);
            posO = new double[]{parent[0] + self.a * Math.cos(theta), parent[1] + self.a * Math.sin(theta), parent[2]};
            anchorDist = self.a;
        } else {
            posO = Celestial.position(self, t);
            if (anchor != null) {
                anchorDist = anchor.a;
            }
        }
        sunDistance = len(posO);
        e1[0] = -posO[0] / sunDistance;
        e1[1] = -posO[1] / sunDistance;
        e1[2] = -posO[2] / sunDistance;
        e3[0] = -e1[2] * e1[0];
        e3[1] = -e1[2] * e1[1];
        e3[2] = 1 - e1[2] * e1[2];
        normalize(e3);
        //e2 = e3 x e1
        e2[0] = e3[1] * e1[2] - e3[2] * e1[1];
        e2[1] = e3[2] * e1[0] - e3[0] * e1[2];
        e2[2] = e3[0] * e1[1] - e3[1] * e1[0];
        sunLongitude = Math.atan2(e1[1], e1[0]);

        //Le Soleil
        sun = new Item();
        sun.type = SUN;
        sun.body = Body.SUN;
        sun.y = 1;
        sun.dist = sunDistance;
        sun.alpha = Math.asin(Math.min(1, Body.SUN.radius / sunDistance));
        sun.visual = Celestial.visualRadius(sun.alpha);
        items.add(sun);

        //L'astre fixe (planete d'une lune synchrone, ou Charon vu de Pluton)
        if (anchor != null) {
            Item item = new Item();
            item.type = DISC;
            item.body = anchor;
            double psi = Math.toRadians(observer.psi0 - timeDeg);
            double delta = Math.toRadians(observer.delta);
            item.x = -Math.sin(delta);
            item.y = Math.cos(delta) * Math.cos(psi);
            item.z = Math.cos(delta) * Math.sin(psi);
            item.dist = anchorDist;
            item.alpha = Math.asin(Math.min(0.999, anchor.radius / Math.max(anchorDist - self.radius, anchor.radius * 1.05)));
            item.visual = Celestial.visualRadius(item.alpha);
            light(item, item.x * anchorDist, item.y * anchorDist, item.z * anchorDist);
            if (anchor.ringed()) {
                //En orbite dans le plan des anneaux : on les voit par la tranche
                double[] p = {-1, 0, 0};
                double d = p[0] * item.x;
                p[0] -= d * item.x;
                p[1] -= d * item.y;
                p[2] -= d * item.z;
                normalize(p);
                double tilt = Math.toRadians(1.2);
                item.ring = true;
                item.px = p[0] * Math.cos(tilt) - item.x * Math.sin(tilt);
                item.py = p[1] * Math.cos(tilt) - item.y * Math.sin(tilt);
                item.pz = p[2] * Math.cos(tilt) - item.z * Math.sin(tilt);
            }
            items.add(item);
        }

        double[] posT = new double[3];
        double[] posP = new double[3];
        double[] c = new double[3];
        for (Body body : Body.ALL) {
            if (body == Body.SUN || body == self || body == anchor) {
                continue;
            }
            if (self == Body.EARTH && body == Body.MOON) {
                //La Lune vanilla est deja dessinee
                continue;
            }
            Celestial.position(body, t, posT);
            double dx = posT[0] - posO[0], dy = posT[1] - posO[1], dz = posT[2] - posO[2];
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            toSky(dx / dist, dy / dist, dz / dist, c);
            Body parent = body.parent();
            if (parent != null && parent != self && parent != self.parent()) {
                //Lune d'un autre systeme : invisible si elle se confond avec sa planete
                Celestial.position(parent, t, posP);
                double px = posP[0] - posO[0], py = posP[1] - posO[1], pz = posP[2] - posO[2];
                double pd = Math.sqrt(px * px + py * py + pz * pz);
                double cos = (px * dx + py * dy + pz * dz) / (pd * dist);
                if (cos > Math.cos(Math.toRadians(0.25))) {
                    continue;
                }
            }
            double effective = parent == self ? Math.max(dist - self.radius, body.radius * 2) : dist;
            double alpha = Math.asin(Math.min(0.999, body.radius / effective));
            double distSun = len(posT);
            double cosPhase = -(dx * -posT[0] + dy * -posT[1] + dz * -posT[2]) / (dist * distSun);
            double phase = Math.acos(Math.max(-1, Math.min(1, cosPhase)));
            double mag = Celestial.magnitude(body, distSun, dist, phase);
            double ratio = alpha / Celestial.ALPHA0;
            if (ratio < 0.1 && mag > 7) {
                continue;
            }
            Item item = new Item();
            item.body = body;
            item.x = c[0];
            item.y = c[1];
            item.z = c[2];
            item.dist = dist;
            item.alpha = alpha;
            item.mag = mag;
            item.visual = Celestial.visualRadius(alpha);
            double[] l = new double[3];
            toSky(-posT[0] / distSun, -posT[1] / distSun, -posT[2] / distSun, l);
            item.lx = l[0];
            item.ly = l[1];
            item.lz = l[2];
            if (ratio >= 0.1) {
                item.type = DISC;
                item.discAlpha = (float) smoothstep(0.1, 0.2, ratio);
                item.pointAlpha = 1 - item.discAlpha;
                if (body.ringed()) {
                    double[] p = new double[3];
                    toSky(SATURN_POLE[0], SATURN_POLE[1], SATURN_POLE[2], p);
                    item.ring = true;
                    item.px = p[0];
                    item.py = p[1];
                    item.pz = p[2];
                }
            } else {
                item.type = POINT;
            }
            items.add(item);
        }
        items.sort((a, b) -> Double.compare(b.dist, a.dist));
    }

    /** Lumiere arrivant sur un astre situe en (bx, by, bz) dans le repere du ciel (km). */
    private void light(Item item, double bx, double by, double bz) {
        double lx = -bx, ly = sunDistance - by, lz = -bz;
        double l = Math.sqrt(lx * lx + ly * ly + lz * lz);
        item.lx = lx / l;
        item.ly = ly / l;
        item.lz = lz / l;
    }

    /** Vecteur ecliptique (inertiel) vers le repere du ciel. */
    public void toSky(double dx, double dy, double dz, double[] out) {
        out[0] = -(dx * e3[0] + dy * e3[1] + dz * e3[2]);
        out[1] = dx * e1[0] + dy * e1[1] + dz * e1[2];
        out[2] = -(dx * e2[0] + dy * e2[1] + dz * e2[2]);
    }

    /**
     * Vecteur ecliptique vers le repere "de base" des etoiles (le repere du ciel quand le Soleil est a la longitude 0) :
     * le rendu y applique ensuite XP(sunLongitude).
     */
    public static void toBase(double dx, double dy, double dz, double[] out) {
        out[0] = -dz;
        out[1] = dx;
        out[2] = -dy;
    }

    public static double[] unitFromLonLat(double lonDeg, double latDeg) {
        double lon = Math.toRadians(lonDeg), lat = Math.toRadians(latDeg);
        return new double[]{Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat)};
    }

    private static double len(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static void normalize(double[] v) {
        double l = len(v);
        v[0] /= l;
        v[1] /= l;
        v[2] /= l;
    }

    public static double smoothstep(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
