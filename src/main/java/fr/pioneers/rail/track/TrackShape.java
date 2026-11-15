package fr.pioneers.rail.track;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.0 : formes de voie. Chacune porte ses chemins ({@link TrackPath}, dans le repere de son bloc). Chaque forme tient dans un bloc
 * (0.20.3 : grands virages retires, les voies prennent seules leur forme).
 * <ul>
 *     <li>droites, pentes douces (un bloc de montee sur deux blocs : moitie basse 0 -> 8 px, moitie haute 8 -> 16 px), courbes serrees
 *     (rayon d'un demi-bloc, vitesse limitee) ;</li>
 *     <li>aiguillages : {@code SWITCH_<talon>_<branche>} (droite + courbe vers la branche), {@code WYE_<talon>} (deux courbes),
 *     {@code TRIPLE_<talon>} (droite + deux courbes) ; le chemin 0 est la direction par defaut ;</li>
 *     <li>croisement, butoirs (impasse vers la face).</li>
 * </ul>
 */
public enum TrackShape implements StringRepresentable {
    STRAIGHT_NS, STRAIGHT_EW,
    SLOPE_LOW_N, SLOPE_LOW_E, SLOPE_LOW_S, SLOPE_LOW_W,
    SLOPE_HIGH_N, SLOPE_HIGH_E, SLOPE_HIGH_S, SLOPE_HIGH_W,
    CURVE_NE, CURVE_SE, CURVE_SW, CURVE_NW,
    SWITCH_N_E, SWITCH_N_W, SWITCH_S_E, SWITCH_S_W, SWITCH_E_N, SWITCH_E_S, SWITCH_W_N, SWITCH_W_S,
    WYE_N, WYE_E, WYE_S, WYE_W,
    TRIPLE_N, TRIPLE_E, TRIPLE_S, TRIPLE_W,
    CROSSING,
    BUFFER_N, BUFFER_E, BUFFER_S, BUFFER_W,
    //0.20.6 : diagonales (ajoutees EN FIN : les ordinaux sauves ne bougent pas)
    DIAG_NE, DIAG_SE, DIAG_SW, DIAG_NW,
    EASE_N_E, EASE_N_W, EASE_E_N, EASE_E_S, EASE_S_E, EASE_S_W, EASE_W_N, EASE_W_S,
    EASEB_N_E, EASEB_N_W, EASEB_E_N, EASEB_E_S, EASEB_S_E, EASEB_S_W, EASEB_W_N, EASEB_W_S,
    DSWITCH_N_E, DSWITCH_N_W, DSWITCH_E_N, DSWITCH_E_S, DSWITCH_S_E, DSWITCH_S_W, DSWITCH_W_N, DSWITCH_W_S,
    DMERGE_N_E, DMERGE_N_W, DMERGE_E_N, DMERGE_E_S, DMERGE_S_E, DMERGE_S_W, DMERGE_W_N, DMERGE_W_S,
    SDIAG_Z_NE, SDIAG_Z_SE, SDIAG_Z_SW, SDIAG_Z_NW, SDIAG_X_NE, SDIAG_X_SE, SDIAG_X_SW, SDIAG_X_NW,
    DV_N, DV_E, DV_S, DV_W;

    /** Vitesse limite des courbes serrees et des branches d'aiguillage : 6 blocs/s (px/tick). */
    public static final float TIGHT_LIMIT = 6 * 0.8F;
    /** 0.20.6 : vitesse limite des raccords droite / diagonale : 10 blocs/s (px/tick). */
    public static final float EASE_LIMIT = 10 * 0.8F;
    /** Arret d'un butoir : distance depuis la face d'entree (px). */
    public static final float BUFFER_STOP = 11;

    public enum Family {
        STRAIGHT, SLOPE, CURVE, SWITCH, CROSSING, BUFFER,
        /** 0.20.6 : cordes a 45 degres et demi-raccords. */
        DIAGONAL,
        /** 0.20.6 : droite + demi-raccord (bloc jumeau d'un aiguillage diagonal). */
        JUNCTION
    }

    /** Cordes : faces (N ou S d'abord, puis E ou W), dans l'ordre des noms DIAG_*. */
    private static final Direction[][] CHORDS = {{Direction.NORTH, Direction.EAST}, {Direction.SOUTH, Direction.EAST}, {Direction.SOUTH, Direction.WEST},
          {Direction.NORTH, Direction.WEST}};

    private static final List<List<TrackPath>> PATHS = new ArrayList<>();
    private static final List<BlockPos> BLOCKS = List.of(BlockPos.ZERO);
    private static final Family[] FAMILY = new Family[values().length];

    static {
        TrackShape[] all = values();
        for (int i = 0; i < all.length; i++) {
            PATHS.add(new ArrayList<>());
        }
        put(STRAIGHT_NS, Family.STRAIGHT, TrackPath.line(end(Direction.NORTH, 0), end(Direction.SOUTH, 0)));
        put(STRAIGHT_EW, Family.STRAIGHT, TrackPath.line(end(Direction.WEST, 0), end(Direction.EAST, 0)));
        Direction[] cardinal = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (Direction f : cardinal) {
            String n = letter(f);
            put(valueOf("SLOPE_LOW_" + n), Family.SLOPE, TrackPath.line(end(f.getOpposite(), 0), end(f, 1)));
            put(valueOf("SLOPE_HIGH_" + n), Family.SLOPE, TrackPath.line(end(f.getOpposite(), 1), new TrackPath.End(0, 1, 0, f, 0, true)));
            Direction ex = f.getOpposite();
            put(valueOf("BUFFER_" + n), Family.BUFFER, TrackPath.deadEnd(end(ex, 0), new TrackPath.End(0, 0, 0, f, 0, false),
                  8 + ex.getStepX() * 8 + f.getStepX() * BUFFER_STOP, 8 + ex.getStepZ() * 8 + f.getStepZ() * BUFFER_STOP));
        }
        put(CURVE_NE, Family.CURVE, curve(Direction.NORTH, Direction.EAST));
        put(CURVE_SE, Family.CURVE, curve(Direction.SOUTH, Direction.EAST));
        put(CURVE_SW, Family.CURVE, curve(Direction.SOUTH, Direction.WEST));
        put(CURVE_NW, Family.CURVE, curve(Direction.NORTH, Direction.WEST));
        for (Direction s : cardinal) {
            for (Direction b : new Direction[]{s.getClockWise(), s.getCounterClockWise()}) {
                put(valueOf("SWITCH_" + letter(s) + "_" + letter(b)), Family.SWITCH, straight(s), curve(s, b));
            }
            put(valueOf("WYE_" + letter(s)), Family.SWITCH, curve(s, s.getClockWise()), curve(s, s.getCounterClockWise()));
            put(valueOf("TRIPLE_" + letter(s)), Family.SWITCH, straight(s), curve(s, s.getClockWise()), curve(s, s.getCounterClockWise()));
        }
        put(CROSSING, Family.CROSSING, TrackPath.line(end(Direction.NORTH, 0), end(Direction.SOUTH, 0)),
              TrackPath.line(end(Direction.WEST, 0), end(Direction.EAST, 0)));
        //0.20.6 : cordes (milieu de face -> milieu de la face voisine)
        put(DIAG_NE, Family.DIAGONAL, chord(Direction.NORTH, Direction.EAST));
        put(DIAG_SE, Family.DIAGONAL, chord(Direction.SOUTH, Direction.EAST));
        put(DIAG_SW, Family.DIAGONAL, chord(Direction.SOUTH, Direction.WEST));
        put(DIAG_NW, Family.DIAGONAL, chord(Direction.NORTH, Direction.WEST));
        //Raccords : la reference (entree sud, vers l'est) coupee en deux moities, puis tournee et retournee
        float[][] halves = TrackPath.split(TrackPath.EASE, TrackPath.EASE_SPLIT);
        float[] second = halves[1].clone();
        for (int i = 1; i < 8; i += 2) {
            second[i] += 16;
        }
        TrackPath easeA = TrackPath.bezier(end(Direction.SOUTH, 0), new TrackPath.End(0, 0, 0, Direction.NORTH, 0, true, 1), halves[0], EASE_LIMIT);
        TrackPath easeB = TrackPath.bezier(new TrackPath.End(0, 0, 0, Direction.SOUTH, 0, true, 1), end(Direction.EAST, 0), second, EASE_LIMIT);
        for (boolean mirrored : new boolean[]{false, true}) {
            TrackPath pa = mirrored ? easeA.mirror() : easeA, pb = mirrored ? easeB.mirror() : easeB;
            for (int k = 0; k < 4; k++) {
                Direction entry = pa.a.face(), turn = pb.b.face();
                String key = letter(entry) + "_" + letter(turn);
                put(valueOf("EASE_" + key), Family.DIAGONAL, pa);
                put(valueOf("EASEB_" + key), Family.DIAGONAL, pb);
                put(valueOf("DSWITCH_" + key), Family.SWITCH, straight(entry), pa);
                put(valueOf("DMERGE_" + key), Family.JUNCTION, straight(entry), pb);
                pa = pa.rotate();
                pb = pb.rotate();
            }
        }
        //Croisements : droite x corde (la croix est au milieu d'une face), deux cordes qui partagent un point
        for (Direction[] c : CHORDS) {
            String n = letter(c[0]) + letter(c[1]);
            put(valueOf("SDIAG_Z_" + n), Family.CROSSING, TrackPath.line(end(Direction.NORTH, 0), end(Direction.SOUTH, 0)), chord(c[0], c[1]));
            put(valueOf("SDIAG_X_" + n), Family.CROSSING, TrackPath.line(end(Direction.WEST, 0), end(Direction.EAST, 0)), chord(c[0], c[1]));
        }
        for (Direction f : cardinal) {
            put(valueOf("DV_" + letter(f)), Family.CROSSING, chord(f, f.getClockWise()), chord(f, f.getCounterClockWise()));
        }
    }

    private static TrackPath chord(Direction f1, Direction f2) {
        return TrackPath.line(end(f1, 0), end(f2, 0));
    }

    private static String letter(Direction d) {
        return d.getName().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static TrackPath.End end(Direction face, int half) {
        return new TrackPath.End(0, 0, 0, face, half, true);
    }

    private static TrackPath straight(Direction from) {
        return TrackPath.line(end(from, 0), end(from.getOpposite(), 0));
    }

    private static TrackPath curve(Direction f1, Direction f2) {
        float cx = f1 == Direction.EAST || f2 == Direction.EAST ? 16 : 0;
        float cz = f1 == Direction.SOUTH || f2 == Direction.SOUTH ? 16 : 0;
        return TrackPath.arc(end(f1, 0), end(f2, 0), cx, cz, TIGHT_LIMIT);
    }

    private static void put(TrackShape shape, Family family, TrackPath... paths) {
        FAMILY[shape.ordinal()] = family;
        PATHS.get(shape.ordinal()).addAll(List.of(paths));
    }

    // ------------------------------------------------------------------

    public List<TrackPath> paths() {
        return PATHS.get(ordinal());
    }

    /** Blocs couverts (decalages depuis l'ancre) : le bloc lui-meme. */
    public List<BlockPos> blocks() {
        return BLOCKS;
    }

    public Family family() {
        return FAMILY[ordinal()];
    }

    public boolean isSwitch() {
        return family() == Family.SWITCH;
    }

    /** Faces (et demi-hauteurs) par lesquelles la forme se raccorde au niveau de son bloc : masque 4 bits N E S W. */
    public int faceMask() {
        int mask = 0;
        for (TrackPath p : paths()) {
            for (TrackPath.End e : new TrackPath.End[]{p.a, p.b}) {
                if (e.open() && e.dx() == 0 && e.dz() == 0 && e.dy() == 0 && e.half() == 0) {
                    mask |= 1 << e.face().get2DDataValue();
                }
            }
        }
        return mask;
    }

    /** Forme a chemin unique, la plus simple qui relie exactement ces faces (masque N E S W par get2DDataValue), sinon null. */
    public static TrackShape plainFor(Direction a, Direction b) {
        if (a.getAxis() == b.getAxis()) {
            return a.getAxis() == Direction.Axis.Z ? STRAIGHT_NS : STRAIGHT_EW;
        }
        boolean n = a == Direction.NORTH || b == Direction.NORTH, e = a == Direction.EAST || b == Direction.EAST;
        return n ? (e ? CURVE_NE : CURVE_NW) : (e ? CURVE_SE : CURVE_SW);
    }

    public static TrackShape straight(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? STRAIGHT_NS : STRAIGHT_EW;
    }

    public static TrackShape slope(Direction rising, boolean high) {
        return valueOf("SLOPE_" + (high ? "HIGH_" : "LOW_") + letter(rising));
    }

    public static TrackShape buffer(Direction toward) {
        return valueOf("BUFFER_" + letter(toward));
    }

    public static TrackShape switchShape(Direction stem, Direction branch) {
        return valueOf("SWITCH_" + letter(stem) + "_" + letter(branch));
    }

    public static TrackShape triple(Direction stem) {
        return valueOf("TRIPLE_" + letter(stem));
    }

    /** Pente : direction montante, sinon null. */
    public Direction slopeRising() {
        if (family() != Family.SLOPE) {
            return null;
        }
        return paths().get(0).b.face();
    }

    public boolean slopeHigh() {
        return name().startsWith("SLOPE_HIGH");
    }

    @NotNull
    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // 0.20.7 : rotation et miroir (collage des plans des robots)
    // ------------------------------------------------------------------

    private static TrackShape[][] ROTATED, MIRRORED;

    /** Forme tournee de {@code quarters} quarts de tour dans le sens horaire (meme geometrie ; elle-meme si rien ne correspond). */
    public TrackShape rotated(int quarters) {
        int q = Math.floorMod(quarters, 4);
        if (q == 0) {
            return this;
        }
        if (ROTATED == null) {
            buildTransforms();
        }
        return ROTATED[q][ordinal()];
    }

    /** Miroir : {@code xAxis} vrai = est <-> ouest, faux = nord <-> sud. */
    public TrackShape mirrored(boolean xAxis) {
        if (MIRRORED == null) {
            buildTransforms();
        }
        return MIRRORED[xAxis ? 0 : 1][ordinal()];
    }

    private static final int SAMPLES = 6;

    /** Points de chaque chemin (px, repere du bloc), transformes. kind : 1..3 quarts de tour, 4 miroir x, 5 miroir z. */
    private static float[][] samples(TrackShape s, int kind) {
        List<TrackPath> paths = s.paths();
        float[][] out = new float[paths.size()][];
        float[] p = new float[3];
        for (int i = 0; i < paths.size(); i++) {
            TrackPath path = paths.get(i);
            float[] pts = new float[(SAMPLES + 1) * 3];
            for (int k = 0; k <= SAMPLES; k++) {
                path.point(path.length * k / SAMPLES, p);
                float x = p[0], z = p[2];
                if (kind >= 1 && kind <= 3) {
                    for (int r = 0; r < kind; r++) {
                        float nx = 16 - z;
                        z = x;
                        x = nx;
                    }
                } else if (kind == 4) {
                    x = 16 - x;
                } else if (kind == 5) {
                    z = 16 - z;
                }
                pts[k * 3] = x;
                pts[k * 3 + 1] = p[1];
                pts[k * 3 + 2] = z;
            }
            out[i] = pts;
        }
        return out;
    }

    private static boolean samePath(float[] a, float[] b) {
        boolean fwd = true, rev = true;
        int n = a.length / 3;
        for (int k = 0; k < n && (fwd || rev); k++) {
            int j = n - 1 - k;
            for (int c = 0; c < 3; c++) {
                fwd &= Math.abs(a[k * 3 + c] - b[k * 3 + c]) < 0.08F;
                rev &= Math.abs(a[k * 3 + c] - b[j * 3 + c]) < 0.08F;
            }
        }
        return fwd || rev;
    }

    private static boolean sameShape(float[][] a, float[][] b) {
        if (a.length != b.length) {
            return false;
        }
        boolean[] used = new boolean[b.length];
        for (float[] pa : a) {
            boolean found = false;
            for (int j = 0; j < b.length && !found; j++) {
                if (!used[j] && samePath(pa, b[j])) {
                    used[j] = true;
                    found = true;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static synchronized void buildTransforms() {
        TrackShape[] all = values();
        float[][][] base = new float[all.length][][];
        for (TrackShape t : all) {
            base[t.ordinal()] = samples(t, 0);
        }
        TrackShape[][] rot = new TrackShape[4][all.length];
        TrackShape[][] mir = new TrackShape[2][all.length];
        for (TrackShape s : all) {
            rot[0][s.ordinal()] = s;
            for (int kind = 1; kind <= 5; kind++) {
                float[][] moved = samples(s, kind);
                TrackShape found = s;
                for (TrackShape t : all) {
                    //Meme famille (un butoir reste un butoir, une diagonale une diagonale)
                    if (t.family() == s.family() && sameShape(moved, base[t.ordinal()])) {
                        found = t;
                        break;
                    }
                }
                if (kind <= 3) {
                    rot[kind][s.ordinal()] = found;
                } else {
                    mir[kind - 4][s.ordinal()] = found;
                }
            }
        }
        MIRRORED = mir;
        ROTATED = rot;
    }
}
