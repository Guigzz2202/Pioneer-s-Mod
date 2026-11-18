package fr.pioneers.space.sky.client;

import java.util.List;
import java.util.Locale;

/**
 * Les astres du ciel, avec leurs vraies donnees orbitales (elements kepleriens J2000, rayons, albedos).
 * Calcul pur (aucune classe Minecraft) : positions heliocentriques ecliptiques en km, a un instant donne en jours.
 * <p>
 * Le temps du jeu suit le temps reel : un jour Minecraft = un jour terrestre pour les orbites. Le jour 0 du monde
 * correspond au 13 octobre 2026, si bien qu'un nouveau monde montre a peu pres la vraie configuration des planetes.
 */
public final class Celestial {

    private Celestial() {
    }

    public static final double AU = 149_597_870.7;
    /** Jours entre J2000 et le jour 0 du monde. */
    public static final double EPOCH = 9782.0;
    /** Rayon angulaire du Soleil vu de la Terre (rad) : reference des tailles apparentes. */
    public static final double ALPHA0 = 0.004652;

    public enum Kind {STAR, PLANET, MOON}

    /**
     * Astres. Pour les planetes : a (UA), e, i, noeud, longitude du perihelie, longitude moyenne a J2000 (degres).
     * Pour les lunes : a (km), periode (jours, negative = retrograde), longitude a J2000, inclinaison.
     */
    public enum Body {
        SUN(Kind.STAR, null, 0, 0, 0, 0, 0, 0, 0, 696_000, 1, 0xFFFFFF, null),
        MERCURY(Kind.PLANET, null, 0.38709927, 0.20563593, 7.00497902, 48.33076593, 77.45779628, 252.25032350, 0, 2_439.7, 0.142, 0xD8CCBF, null),
        VENUS(Kind.PLANET, null, 0.72333566, 0.00677672, 3.39467605, 76.67984255, 131.60246718, 181.97909950, 0, 6_051.8, 0.689, 0xFFF6E0, null),
        EARTH(Kind.PLANET, null, 1.00000261, 0.01671123, 0, 0, 102.93768193, 100.46457166, 0, 6_371, 0.367, 0x9CC0FF, "earth"),
        MARS(Kind.PLANET, null, 1.52371034, 0.09339410, 1.84969142, 49.55953891, -23.94362959, -4.55343205, 0, 3_389.5, 0.170, 0xFF8C5A, "mars"),
        CERES(Kind.PLANET, null, 2.7675, 0.0758, 10.59, 80.31, 153.91, 162.0, 0, 469.7, 0.09, 0xD8D2C8, null),
        VESTA(Kind.PLANET, null, 2.3615, 0.0887, 7.14, 103.81, 255.0, 271.0, 0, 262.7, 0.42, 0xE0D8C8, null),
        PALLAS(Kind.PLANET, null, 2.7730, 0.2305, 34.84, 173.08, 123.0, 33.0, 0, 256.0, 0.16, 0xD0D4D8, null),
        HYGIEA(Kind.PLANET, null, 3.1415, 0.1125, 3.83, 283.20, 235.5, 112.0, 0, 217.0, 0.07, 0xC8C0B8, null),
        JUPITER(Kind.PLANET, null, 5.20288700, 0.04838624, 1.30439695, 100.47390909, 14.72847983, 34.39644051, 0, 69_911, 0.538, 0xFFEBCC, "jupiter"),
        SATURN(Kind.PLANET, null, 9.53667594, 0.05386179, 2.48599187, 113.66242448, 92.59887831, 49.95424423, 0, 58_232, 0.499, 0xFFE6A6, "saturn"),
        URANUS(Kind.PLANET, null, 19.18916464, 0.04725744, 0.77263783, 74.01692503, 170.95427630, 313.23810451, 0, 25_362, 0.488, 0xB4EBF2, "uranus"),
        NEPTUNE(Kind.PLANET, null, 30.06992276, 0.00859048, 1.77004347, 131.78422574, 44.96476227, -55.12002969, 0, 24_622, 0.442, 0x8CA6FF, "neptune"),
        PLUTO(Kind.PLANET, null, 39.48211675, 0.24882730, 17.14001206, 110.30393684, 224.06891629, 238.92903833, 0, 1_188.3, 0.52, 0xF2D9BF, "pluto"),
        //Lunes : (parent, a km, periode jours, longitude J2000, inclinaison)
        MOON(Kind.MOON, "EARTH", 384_400, 27.321582, 218.316, 5.145, 0, 0, 0, 1_737.4, 0.12, 0xD9D9D9, null),
        PHOBOS(Kind.MOON, "MARS", 9_376, 0.31891, 35, 1.08, 0, 0, 0, 11.1, 0.071, 0xA08C80, "phobos"),
        DEIMOS(Kind.MOON, "MARS", 23_463, 1.26244, 79, 1.79, 0, 0, 0, 6.2, 0.068, 0xB4A898, null),
        IO(Kind.MOON, "JUPITER", 421_700, 1.769138, 106.08, 0.05, 0, 0, 0, 1_821.6, 0.63, 0xFFF29A, "io"),
        EUROPA(Kind.MOON, "JUPITER", 671_034, 3.551181, 175.73, 0.47, 0, 0, 0, 1_560.8, 0.67, 0xF2EBE0, "europa"),
        GANYMEDE(Kind.MOON, "JUPITER", 1_070_412, 7.154553, 120.56, 0.2, 0, 0, 0, 2_634.1, 0.43, 0xD0C8BF, "ganymede"),
        CALLISTO(Kind.MOON, "JUPITER", 1_882_709, 16.689018, 84.44, 0.2, 0, 0, 0, 2_410.3, 0.22, 0xB4ADA6, "callisto"),
        ENCELADUS(Kind.MOON, "SATURN", 237_948, 1.370218, 200, 0.02, 0, 0, 0, 252.1, 1.0, 0xFFFFFF, null),
        TITAN(Kind.MOON, "SATURN", 1_221_870, 15.945421, 15, 0.35, 0, 0, 0, 2_574.7, 0.22, 0xFFC27A, "titan"),
        MIRANDA(Kind.MOON, "URANUS", 129_390, 1.413479, 40, 4.2, 0, 0, 0, 235.8, 0.32, 0xD0D0D4, "miranda"),
        TITANIA(Kind.MOON, "URANUS", 435_910, 8.705872, 260, 0.3, 0, 0, 0, 788.4, 0.27, 0xD0C8C2, "titania"),
        TRITON(Kind.MOON, "NEPTUNE", 354_759, -5.876854, 300, 0, 0, 0, 0, 1_353.4, 0.76, 0xF2DCD9, null),
        CHARON(Kind.MOON, "PLUTO", 19_591, 6.387221, 120, 0, 0, 0, 0, 606, 0.30, 0xC8C8CF, "charon");

        /** Tous les astres (liste partagee, sans copie a chaque image). */
        public static final List<Body> ALL = List.of(values());

        public final Kind kind;
        private final String parentName;
        /** Planetes : demi-grand axe (UA). Lunes : rayon de l'orbite (km). */
        public final double a;
        public final double e, incl, node, peri, l0;
        /** Lunes : periode sideral (jours). */
        public final double period;
        public final double radius, albedo;
        public final int color;
        /** Texture du ciel (textures/environment/sky/<nom>.png), ou null : texture de la carte. */
        public final String skyTexture;

        Body(Kind kind, String parent, double a, double eOrPeriod, double inclOrL0, double nodeOrIncl, double peri, double l0, double unused,
              double radius, double albedo, int color, String skyTexture) {
            this.kind = kind;
            this.parentName = parent;
            this.a = a;
            if (kind == Kind.MOON) {
                this.period = eOrPeriod;
                this.l0 = inclOrL0;
                this.incl = nodeOrIncl;
                this.e = 0;
                this.node = 0;
                this.peri = 0;
            } else {
                this.e = eOrPeriod;
                this.incl = inclOrL0;
                this.node = nodeOrIncl;
                this.peri = peri;
                this.l0 = l0;
                this.period = kind == Kind.STAR ? 0 : 365.25 * Math.pow(a, 1.5);
            }
            this.radius = radius;
            this.albedo = albedo;
            this.color = color;
            this.skyTexture = skyTexture;
        }

        public Body parent() {
            return parentName == null ? null : Body.valueOf(parentName);
        }

        public boolean isMoon() {
            return kind == Kind.MOON;
        }

        public boolean ringed() {
            return this == SATURN;
        }

        /** Nom de la texture de la carte du systeme solaire. */
        public String mapTexture() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Position heliocentrique ecliptique (km) au jour {@code t} du monde. */
    public static double[] position(Body body, double t) {
        double[] out = new double[3];
        position(body, t, out);
        return out;
    }

    public static void position(Body body, double t, double[] out) {
        if (body.kind == Kind.STAR) {
            out[0] = out[1] = out[2] = 0;
            return;
        }
        double days = t + EPOCH;
        if (body.kind == Kind.MOON) {
            position(body.parent(), t, out);
            double theta = Math.toRadians(body.l0 + 360.0 * days / body.period);
            double inc = Math.toRadians(body.incl);
            out[0] += body.a * Math.cos(theta);
            out[1] += body.a * Math.sin(theta) * Math.cos(inc);
            out[2] += body.a * Math.sin(theta) * Math.sin(inc);
            return;
        }
        double l = Math.toRadians(body.l0 + 360.0 * days / body.period);
        double varpi = Math.toRadians(body.peri);
        double omega = Math.toRadians(body.node);
        double w = varpi - omega;
        double inc = Math.toRadians(body.incl);
        double m = l - varpi;
        m = Math.IEEEremainder(m, 2 * Math.PI);
        double e = body.e;
        double ea = m + e * Math.sin(m);
        for (int k = 0; k < 6; k++) {
            ea -= (ea - e * Math.sin(ea) - m) / (1 - e * Math.cos(ea));
        }
        double aKm = body.a * AU;
        double xp = aKm * (Math.cos(ea) - e);
        double yp = aKm * Math.sqrt(1 - e * e) * Math.sin(ea);
        double cw = Math.cos(w), sw = Math.sin(w), cO = Math.cos(omega), sO = Math.sin(omega), ci = Math.cos(inc), si = Math.sin(inc);
        out[0] = (cw * cO - sw * sO * ci) * xp + (-sw * cO - cw * sO * ci) * yp;
        out[1] = (cw * sO + sw * cO * ci) * xp + (-sw * sO + cw * cO * ci) * yp;
        out[2] = (sw * si) * xp + (cw * si) * yp;
    }

    /** Longitude ecliptique heliocentrique (rad). */
    public static double longitude(double[] p) {
        return Math.atan2(p[1], p[0]);
    }

    /**
     * Taille apparente a l'ecran (rayon, degres) pour un rayon angulaire reel (rad) : le Soleil vu de la Terre fait 5 degres
     * (comme le Soleil de Minecraft), les petits astres sont grossis (loi en puissance 0,6) et les tres gros plafonnent en douceur.
     */
    public static double visualRadius(double alpha) {
        double v = 5.0 * Math.pow(alpha / ALPHA0, 0.6);
        return v / Math.sqrt(1 + (v / 45.0) * (v / 45.0));
    }

    /**
     * Magnitude apparente d'un astre eclaire par le Soleil.
     *
     * @param distSun distance au Soleil (km)
     * @param distObs distance a l'observateur (km)
     * @param phase   angle de phase (rad)
     */
    public static double magnitude(Body body, double distSun, double distObs, double phase) {
        double r = distSun / AU;
        double ratio = body.radius / distObs;
        double phi = (Math.sin(phase) + (Math.PI - phase) * Math.cos(phase)) / Math.PI;
        double flux = body.albedo * ratio * ratio * Math.max(phi, 1e-4) / (r * r);
        return -26.74 - 2.5 * Math.log10(flux);
    }
}
