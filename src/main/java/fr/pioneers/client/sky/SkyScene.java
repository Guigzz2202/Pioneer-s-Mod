package fr.pioneers.client.sky;

import fr.pioneers.client.sky.Celestial.Body;
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
 */
public final class SkyScene {

    public static final int SUN = 0, POINT = 1, DISC = 2;

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

    private final double[] e1 = new double[3], e2 = new double[3], e3 = new double[3];

    private SkyScene(Observer observer) {
        this.observer = observer;
    }

    /**
     * @param t       jour du monde (dayTime / 24000)
     * @param timeDeg rotation du ciel (heure * 360, celle appliquee au Soleil)
     */
    public static SkyScene compute(Observer obs, double t, double timeDeg) {
        SkyScene scene = new SkyScene(obs);
        scene.build(t, timeDeg);
        return scene;
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
