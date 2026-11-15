package fr.pioneers.client.minimap;

/**
 * 1.10.3 : geometrie de la minimap, SANS classe de Minecraft (le banc {@code tools/minimap/MinimapBench.java} l'execute tel quel).
 * <ul>
 *     <li>projection monde -&gt; ecran : centre (cx, cz) en blocs, echelle (pixels par bloc), rotation (radians, sens horaire : la
 *     direction regardee par le joueur monte quand la carte tourne), origine a l'ecran (ox, oy) ;</li>
 *     <li>contour de la vue : polygone CONVEXE a l'ecran (carre, rectangle, ou cercle a {@link #CIRCLE_SIDES} cotes) ;</li>
 *     <li>decoupe de Sutherland-Hodgman d'un polygone convexe (une region, une case de claim) par ce contour, et de Cyrus-Beck d'un
 *     segment (voies, routes maritimes) : tout se dessine en triangles en eventail, sans pochoir ni tampon intermediaire.</li>
 * </ul>
 * Aucune allocation par image : les tableaux de travail sont gardes (la minimap est dessinee sur le fil de rendu seulement).
 */
public final class MapMath {

    public static final int CIRCLE_SIDES = 48;
    private static final int MAX = 4 * CIRCLE_SIDES + 16;

    // projection
    public double cx, cz;
    public float scale = 1, ox, oy;
    private float cos = 1, sin = 0;
    // contour de la vue (sens trigonometrique a l'ecran, y vers le bas : sens horaire vu par l'oeil)
    public final float[] viewX = new float[MAX], viewY = new float[MAX];
    public int viewN;
    // travail
    private final float[] ax = new float[MAX], ay = new float[MAX], bx = new float[MAX], by = new float[MAX];
    /** Resultat de la derniere decoupe (ecran). */
    public final float[] outX = new float[MAX], outY = new float[MAX];
    public int outN;
    /** Segment decoupe. */
    public float sx0, sy0, sx1, sy1;

    public void project(double centerX, double centerZ, float pixelsPerBlock, float rotation, float originX, float originY) {
        cx = centerX;
        cz = centerZ;
        scale = pixelsPerBlock;
        cos = (float) Math.cos(rotation);
        sin = (float) Math.sin(rotation);
        ox = originX;
        oy = originY;
    }

    public float screenX(double wx, double wz) {
        float dx = (float) (wx - cx) * scale, dz = (float) (wz - cz) * scale;
        return ox + dx * cos - dz * sin;
    }

    public float screenY(double wx, double wz) {
        float dx = (float) (wx - cx) * scale, dz = (float) (wz - cz) * scale;
        return oy + dx * sin + dz * cos;
    }

    /** Point d'ecran -&gt; monde (x). */
    public double worldX(float sx, float sy) {
        float dx = sx - ox, dy = sy - oy;
        return cx + (dx * cos + dy * sin) / scale;
    }

    public double worldZ(float sx, float sy) {
        float dx = sx - ox, dy = sy - oy;
        return cz + (-dx * sin + dy * cos) / scale;
    }

    // ------------------------------------------------------------------ contour
    public void viewRect(float x0, float y0, float x1, float y1) {
        viewN = 4;
        viewX[0] = x0;
        viewY[0] = y0;
        viewX[1] = x1;
        viewY[1] = y0;
        viewX[2] = x1;
        viewY[2] = y1;
        viewX[3] = x0;
        viewY[3] = y1;
    }

    public void viewCircle(float centerX, float centerY, float radius) {
        viewN = CIRCLE_SIDES;
        for (int i = 0; i < CIRCLE_SIDES; i++) {
            double a = 2 * Math.PI * i / CIRCLE_SIDES;
            //rayon du polygone inscrit corrige pour que le milieu des cotes touche le cercle
            float r = radius / (float) Math.cos(Math.PI / CIRCLE_SIDES);
            viewX[i] = centerX + (float) Math.cos(a) * r;
            viewY[i] = centerY + (float) Math.sin(a) * r;
        }
    }

    /** Point d'ecran dans le contour. */
    public boolean inside(float x, float y) {
        for (int i = 0; i < viewN; i++) {
            int j = (i + 1) % viewN;
            if (cross(viewX[i], viewY[i], viewX[j], viewY[j], x, y) < 0) {
                return false;
            }
        }
        return viewN > 0;
    }

    private static float cross(float x0, float y0, float x1, float y1, float px, float py) {
        return (x1 - x0) * (py - y0) - (y1 - y0) * (px - x0);
    }

    // ------------------------------------------------------------------ decoupe
    /**
     * Rectangle du monde (x0, z0)-(x1, z1) projete puis decoupe par le contour ; resultat dans {@link #outX}/{@link #outY} ({@link #outN}
     * sommets, 0 : invisible).
     */
    public int clipWorldRect(double x0, double z0, double x1, double z1) {
        ax[0] = screenX(x0, z0);
        ay[0] = screenY(x0, z0);
        ax[1] = screenX(x1, z0);
        ay[1] = screenY(x1, z0);
        ax[2] = screenX(x1, z1);
        ay[2] = screenY(x1, z1);
        ax[3] = screenX(x0, z1);
        ay[3] = screenY(x0, z1);
        return clip(4);
    }

    /** Decoupe le polygone (ax, ay) de {@code n} sommets par le contour. */
    private int clip(int n) {
        float[] px = ax, py = ay, qx = bx, qy = by;
        for (int e = 0; e < viewN && n > 0; e++) {
            float ex0 = viewX[e], ey0 = viewY[e], ex1 = viewX[(e + 1) % viewN], ey1 = viewY[(e + 1) % viewN];
            int m = 0;
            for (int i = 0; i < n; i++) {
                float cxp = px[i], cyp = py[i], nxp = px[(i + 1) % n], nyp = py[(i + 1) % n];
                float dc = cross(ex0, ey0, ex1, ey1, cxp, cyp), dn = cross(ex0, ey0, ex1, ey1, nxp, nyp);
                if (dc >= 0) {
                    qx[m] = cxp;
                    qy[m++] = cyp;
                }
                if (dc >= 0 != dn >= 0 && m < MAX) {
                    float t = dc / (dc - dn);
                    qx[m] = cxp + (nxp - cxp) * t;
                    qy[m++] = cyp + (nyp - cyp) * t;
                }
                if (m >= MAX - 1) {
                    break;
                }
            }
            float[] tx = px, ty = py;
            px = qx;
            py = qy;
            qx = tx;
            qy = ty;
            n = m;
        }
        System.arraycopy(px, 0, outX, 0, n);
        System.arraycopy(py, 0, outY, 0, n);
        outN = n < 3 ? 0 : n;
        return outN;
    }

    /** Segment du monde decoupe par le contour (Cyrus-Beck, contour convexe) ; resultat dans sx0..sy1. */
    public boolean clipWorldSegment(double wx0, double wz0, double wx1, double wz1) {
        float x0 = screenX(wx0, wz0), y0 = screenY(wx0, wz0), x1 = screenX(wx1, wz1), y1 = screenY(wx1, wz1);
        float t0 = 0, t1 = 1, dx = x1 - x0, dy = y1 - y0;
        for (int e = 0; e < viewN; e++) {
            float ex0 = viewX[e], ey0 = viewY[e], ex1 = viewX[(e + 1) % viewN], ey1 = viewY[(e + 1) % viewN];
            //normale interieure (-(ey1-ey0), ex1-ex0) : cross >= 0 dedans
            float nx = -(ey1 - ey0), ny = ex1 - ex0;
            float num = nx * (x0 - ex0) + ny * (y0 - ey0), den = nx * dx + ny * dy;
            if (den == 0) {
                if (num < 0) {
                    return false;
                }
                continue;
            }
            float t = -num / den;
            if (den > 0) {
                t0 = Math.max(t0, t);
            } else {
                t1 = Math.min(t1, t);
            }
            if (t0 > t1) {
                return false;
            }
        }
        sx0 = x0 + dx * t0;
        sy0 = y0 + dy * t0;
        sx1 = x0 + dx * t1;
        sy1 = y0 + dy * t1;
        return viewN > 0;
    }

    /** Rotation de la carte (radians) pour un joueur de cap Minecraft {@code yaw} (degres) : sa direction regardee pointe vers le haut. */
    public static float rotationFor(float yaw) {
        return (float) Math.toRadians(180 - yaw);
    }

    /** Cap a l'ecran (radians, 0 = vers le haut, sens horaire) d'une entite de cap {@code yaw}, sur une carte tournee de {@code rotation}. */
    public static float headingOnScreen(float yaw, float rotation) {
        return (float) Math.toRadians(yaw + 180) + rotation;
    }

    /**
     * 1.0.1 : balise d'un waypoint dans le monde, a {@code dist} blocs de la camera : facteur de rapprochement vers la camera (au-dela de
     * {@code near} blocs la balise est dessinee a {@code near} blocs, reduite d'autant : meme taille a l'ecran, jamais coupee par la
     * distance d'affichage).
     */
    public static double beaconPull(double dist, double near) {
        return dist <= near ? 1 : near / dist;
    }

    /** 1.0.1 : echelle du texte de l'etiquette (comme un nom d'entite a 10 blocs, puis constante a l'ecran au-dela). */
    public static double labelScale(double dist, double near) {
        return 0.025 * Math.max(1, dist / 10.0) * beaconPull(dist, near);
    }

    // ------------------------------------------------------------------ 1.0.2 : rendu des balises (banc tools/minimap/BeaconBench.java)

    /**
     * 1.0.2 : distance de rapprochement des balises pour une distance d'affichage de {@code chunks} chunks. Au-dela du terrain dessine
     * (coin du carre de chunks : rayon x racine de 2, plus un chunk) pour que la profondeur reste juste (une balise lointaine n'est jamais
     * dessinee DEVANT une colline plus proche qu'elle), et en deca du plan lointain de la camera (4 x la distance d'affichage).
     */
    public static double pullDistance(int chunks) {
        double r = Math.max(2, chunks) * 16.0;
        return Math.max(64, r * 1.5 + 24);
    }

    /** 1.0.2 : demi-largeur du faisceau a {@code dist} blocs : 0,2 bloc comme la balise vanilla, puis au moins ~4 pixels a l'ecran. */
    public static double beamRadius(double dist) {
        return Math.max(0.2, dist * 0.0025);
    }

    /** 1.0.2 : couleur lisible sur le fond sombre de l'etiquette : la couleur du waypoint, eclaircie vers le blanc si elle est trop sombre. */
    public static int readable(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        if (lum >= 0.55) {
            return rgb & 0xFFFFFF;
        }
        double t = (0.55 - lum) / (1 - lum);
        r = (int) Math.round(r + (255 - r) * t);
        g = (int) Math.round(g + (255 - g) * t);
        b = (int) Math.round(b + (255 - b) * t);
        return r << 16 | g << 8 | b;
    }

    /** 1.0.2 : pas d'affichage de la distance : 1 m sous 1 km, puis 100 m (« 1,2 km »). */
    public static double distanceStep(double dist) {
        return dist < 1000 ? 1 : 100;
    }

    /**
     * 1.0.2 : distance AFFICHEE (avec hysteresis : la valeur ne change que si la vraie distance s'en ecarte d'au moins 0,8 pas ; avant, un
     * arrondi a chaque image faisait sauter le chiffre). {@code shown} : valeur affichee a l'image precedente, negative au depart.
     */
    public static double shownDistance(double shown, double dist) {
        double step = distanceStep(dist);
        if (shown >= 0 && Math.abs(dist - shown) < 0.8 * step && distanceStep(shown) == step) {
            return shown;
        }
        return Math.round(dist / step) * step;
    }

    /** 1.0.2 : le joueur regarde-t-il le point ? (cosinus de l'angle entre le regard et la direction du point, ou tout pres). */
    public static boolean gazed(double cosAngle, double dist) {
        return dist < 8 || cosAngle > Math.cos(Math.toRadians(4));
    }

    // ------------------------------------------------------------------ 1.0.4-beta (lot 5) : marqueurs des waypoints a l'ecran (WaypointHud)

    /** Marges du bord de l'ecran pour les marqueurs hors champ (px de l'interface) : cotes, haut, bas (au-dessus de la barre d'objets). */
    public static final double EDGE_SIDE = 14, EDGE_TOP = 14, EDGE_BOTTOM = 46;
    /** Rayon autour du viseur ou l'etiquette passe en grand (px de l'interface). */
    public static final double FOCUS = 40;

    /**
     * Place a l'ecran d'un waypoint : {@code onScreen} vrai si le point est devant la camera et dans le cadre (moins les marges) ;
     * sinon, il est ramene au bord le long de la droite qui part du centre de l'ecran, {@code angle} = direction de la fleche (radians, 0 =
     * vers la droite, y vers le bas).
     */
    public record ScreenPos(double x, double y, boolean onScreen, double angle) {
    }

    /**
     * 1.0.4-beta (lot 5) : point d'ecran d'un point en coordonnees de coupe (clip = projection x vue x position relative a la camera) pour
     * un ecran de {@code width} x {@code height} px. Derriere la camera ({@code cw <= 0}) on divise par |w| : la gauche et la droite restent
     * justes (cote vers lequel tourner), et le point va toujours au bord (en bas s'il est pile derriere).
     */
    public static ScreenPos toScreen(double cx, double cy, double cw, double width, double height) {
        boolean behind = cw <= 1e-4;
        double w = Math.max(1e-4, Math.abs(cw));
        double nx = cx / w, ny = cy / w;
        double sx = (nx + 1) / 2 * width, sy = (1 - ny) / 2 * height;
        double x0 = EDGE_SIDE, x1 = width - EDGE_SIDE, y0 = EDGE_TOP, y1 = height - EDGE_BOTTOM;
        if (!behind && sx >= x0 && sx <= x1 && sy >= y0 && sy <= y1) {
            return new ScreenPos(sx, sy, true, 0);
        }
        double mx = width / 2, my = height / 2;
        double dx = sx - mx, dy = sy - my;
        if (behind && Math.abs(dx) < 1e-6 && Math.abs(dy) < 1e-6) {
            dy = 1;                                                     //pile derriere : en bas (il faut se retourner)
        }
        double tx = dx > 0 ? (x1 - mx) / dx : dx < 0 ? (x0 - mx) / dx : Double.MAX_VALUE;
        double ty = dy > 0 ? (y1 - my) / dy : dy < 0 ? (y0 - my) / dy : Double.MAX_VALUE;
        double t = Math.min(tx, ty);
        return new ScreenPos(mx + dx * t, my + dy * t, false, Math.atan2(dy, dx));
    }

    /** 1.0.4-beta (lot 5) : l'etiquette est-elle « regardee » (pres du viseur) ? */
    public static boolean focused(ScreenPos p, double width, double height) {
        double dx = p.x() - width / 2, dy = p.y() - height / 2;
        return p.onScreen() && dx * dx + dy * dy <= FOCUS * FOCUS;
    }

    /** 1.0.4-beta (lot 5) : opacite d'un marqueur (0..255) : plein a decouvert, attenue derriere le terrain (lisible sans tromper). */
    public static int markerAlpha(boolean hidden) {
        return hidden ? 0x8C : 0xFF;
    }
}
