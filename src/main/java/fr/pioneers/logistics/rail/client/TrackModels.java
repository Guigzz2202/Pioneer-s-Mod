package fr.pioneers.logistics.rail.client;

import fr.pioneers.tech.industry.client.big.BigMat;
import fr.pioneers.logistics.rail.net.Piece;
import fr.pioneers.logistics.rail.track.TrackPath;
import fr.pioneers.logistics.rail.track.TrackQuality;
import fr.pioneers.logistics.rail.track.TrackShape;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;

/**
 * 0.20.0 : maillage des voies (cuit dans les modeles de blocs), d'apres les apercus valides ({@code scratchpad/rail/track.py}) :
 * <ul>
 *     <li>dessus du rail a {@link #RT} px pour toutes les qualites (les roues se posent au meme endroit partout) ;</li>
 *     <li>bois : traverses de chene sur un lit de terre ; MKI : traverses traitees sur gravier ; MKII : beton sur ballast, eclisses ; MKIII :
 *     beton et rails inox sur ballast sombre ; MKIV : poutre de guidage magnetique a bandes lumineuses ;</li>
 *     <li>tout suit le trace des chemins (droites, pentes, arcs) par troncons de 2 px ; les chemins secondaires d'un aiguillage sont un peu
 *     plus bas (pas de faces confondues), leurs traverses seulement la ou ils s'ecartent ; lames chromees sur le chemin choisi.</li>
 * </ul>
 */
public final class TrackModels {

    /** Dessus du rail (px). */
    public static final float RT = 3.6F;
    /** Demi-ecartement : axe d'une file de rails depuis l'axe de la voie (px). */
    public static final float GAUGE = 3.4F;

    private TrackModels() {
    }

    public static RailMesh build(TrackQuality q, Piece.Kind kind, TrackShape shape, int route, boolean powered) {
        RailMesh m = new RailMesh();
        List<TrackPath> paths = shape.paths();
        for (int i = 0; i < paths.size(); i++) {
            TrackPath p = paths.get(i);
            float lower = i * 0.03F;
            if (q == TrackQuality.ULTIMATE) {
                maglev(m, p, lower, i == route && shape.isSwitch());
            } else {
                bed(m, q, p, lower, i == 0 ? null : paths.get(0));
                rails(m, q, p, lower, shape.isSwitch() && i == route);
            }
        }
        if (shape.isSwitch()) {
            switchMachine(m, shape, route);
        }
        //Pentes : remblai aux textures du bloc porteur, ajoute par RailBakedModel (0.20.3)
        switch (kind) {
            case BUFFER -> bufferStop(m, paths.get(0));
            case BRAKE -> brake(m, paths.get(0));
            case BOOSTER -> booster(m, paths.get(0));
            case DETECTOR -> detector(m, paths.get(0), powered);
            case WEIGHBRIDGE -> weighbridge(m, paths.get(0), powered);
            case UNCOUPLER -> uncoupler(m, paths.get(0), powered);
            case LEVEL_CROSSING -> levelCrossing(m, paths.get(0));
            default -> {
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ outils

    /** Repere local au point s du chemin : x en travers (a droite), y en haut, z le long du chemin. */
    private static void frame(RailMesh m, TrackPath p, float s) {
        float[] pt = new float[3], t = new float[3];
        p.point(s, pt);
        p.tangent(s, t);
        float yaw = (float) Math.toDegrees(Math.atan2(t[0], t[2]));
        float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, t[1]))));
        m.translate(pt[0], pt[1], pt[2]);
        m.rotate('y', 0, 0, 0, yaw);
        m.rotate('x', 0, 0, 0, -pitch);
    }

    private static void unframe(RailMesh m) {
        m.pop();
        m.pop();
        m.pop();
    }

    /** Troncons d'un chemin : un seul pour une droite, 2 px pour un arc. */
    private static int segments(TrackPath p) {
        return p.isArc() ? Math.max(2, (int) Math.ceil(p.length / 2F)) : 1;
    }

    /** Boite le long du chemin entre s0 et s1 : travers [x0, x1], hauteur [y0, y1] (repere local). */
    private static void along(RailMesh m, TrackPath p, float s0, float s1, float x0, float x1, float y0, float y1, BigMat mat) {
        float mid = (s0 + s1) / 2, half = (s1 - s0) / 2 + 0.06F;
        frame(m, p, mid);
        if (p.isArc()) {
            //Sur un arc, le bord exterieur est plus long que l'axe : on allonge un peu la boite selon son eloignement
            float k = 1 + Math.max(Math.abs(x0), Math.abs(x1)) / Math.max(1, p.radius());
            half *= k;
        }
        m.box(x0, y0, -half, x1, y1, half, mat);
        unframe(m);
    }

    /**
     * Bande le long du chemin. 0.20.4 : les bouts tombent pile sur les faces du bloc (repere pris au bout du chemin, aucun depassement :
     * plus de bande scintillante au raccord de deux blocs) ; seuls les troncons interieurs d'un arc se chevauchent, en quinconce de
     * {@link RailMesh#NUDGE} px de haut pour ne pas etre dans le meme plan.
     */
    private static void strip(RailMesh m, TrackPath p, float x0, float x1, float y0, float y1, BigMat mat) {
        int n = segments(p);
        if (n == 1) {
            frame(m, p, p.length / 2);
            m.box(x0, y0, -p.length / 2, x1, y1, p.length / 2, mat);
            unframe(m);
            return;
        }
        float seg = p.length / n;
        float k = 1 + Math.max(Math.abs(x0), Math.abs(x1)) / Math.max(1, p.radius());
        for (int i = 0; i < n; i++) {
            float lift = (i % 2) * RailMesh.NUDGE * 1.5F;
            if (i == 0) {
                frame(m, p, 0);
                m.box(x0, y0, 0, x1, y1 + lift, seg + 0.06F, mat);
            } else if (i == n - 1) {
                frame(m, p, p.length);
                m.box(x0, y0, -seg - 0.06F, x1, y1 + lift, 0, mat);
            } else {
                frame(m, p, seg * (i + 0.5F));
                float half = seg / 2 * k + 0.06F;
                m.box(x0, y0, -half, x1, y1 + lift, half, mat);
            }
            unframe(m);
        }
    }

    /** Ecart (px, a plat) entre deux chemins au meme abscisse. */
    private static float apart(TrackPath a, TrackPath b, float s) {
        float[] pa = new float[3], pb = new float[3];
        a.point(s, pa);
        b.point(Math.min(s, b.length), pb);
        return (float) Math.hypot(pa[0] - pb[0], pa[2] - pb[2]);
    }

    // ------------------------------------------------------------------ lit, traverses, rails

    private static void bed(RailMesh m, TrackQuality q, TrackPath p, float lower, TrackPath primary) {
        BigMat tie;
        float tieY0, tieW = 11.6F;
        switch (q) {
            case WOOD -> {
                strip(m, p, -6.4F, 6.4F, 0, 1.0F - lower, BigMat.R_EARTH);
                tie = BigMat.R_OAK;
                tieY0 = 1.0F;
                tieW = 11.2F;
            }
            case BASIC -> {
                strip(m, p, -6.8F, 6.8F, 0, 1.1F - lower, BigMat.R_GRAVEL);
                tie = BigMat.R_TIE;
                tieY0 = 1.1F;
                tieW = 12.0F;
            }
            default -> {
                BigMat ballast = q == TrackQuality.ELITE ? BigMat.R_BALLAST_D : BigMat.R_BALLAST;
                strip(m, p, -6.8F, 6.8F, 0, 0.9F - lower, ballast);
                strip(m, p, -5.8F, 5.8F, 0.9F - lower, 1.2F - lower, ballast);
                tie = BigMat.R_CONCRETE;
                tieY0 = 1.2F;
            }
        }
        float top = RT - 1.6F;
        int n = Math.max(1, Math.round(p.length / 4F));
        float step = p.length / n;
        for (int k = 0; k < n; k++) {
            float s = step * (k + 0.5F);
            if (primary != null && apart(p, primary, s) < 6F) {
                continue;
            }
            frame(m, p, s);
            m.box(-tieW / 2, tieY0 - lower, -1, tieW / 2, top - lower, 1, tie);
            if (q.ordinal() >= 2) {
                //Selles de rail
                for (float x : new float[]{-GAUGE, GAUGE}) {
                    m.box(x - 1.6F, top - lower, -0.7F, x + 1.6F, top + 0.12F - lower, 0.7F, BigMat.R_DARK);
                }
            }
            for (float x : new float[]{-GAUGE, GAUGE}) {
                //Tirefonds
                m.box(x - 1.25F, top - lower, -0.4F, x - 0.85F, top + 0.3F - lower, 0.4F, BigMat.R_STEEL);
                m.box(x + 0.85F, top - lower, -0.4F, x + 1.25F, top + 0.3F - lower, 0.4F, BigMat.R_STEEL);
            }
            unframe(m);
        }
    }

    private static void rails(RailMesh m, TrackQuality q, TrackPath p, float lower, boolean blades) {
        BigMat head = q == TrackQuality.ELITE ? BigMat.R_INOX : BigMat.R_RAIL;
        float foot = RT - 1.6F;
        float lift = 0.02F;
        for (float x : new float[]{-GAUGE, GAUGE}) {
            strip(m, p, x - 0.8F, x + 0.8F, foot + lift - lower, foot + 0.35F - lower, BigMat.R_RAIL_FOOT);
            strip(m, p, x - 0.25F, x + 0.25F, foot + 0.35F - lower, RT - 0.5F - lower, BigMat.R_RAIL_FOOT);
            strip(m, p, x - 0.5F, x + 0.5F, RT - 0.5F - lower, RT - lower, head);
        }
        if (q.ordinal() >= 2 && !p.isArc() && p.length >= 15) {
            //Eclisses au milieu
            for (float x : new float[]{-GAUGE, GAUGE}) {
                along(m, p, p.length / 2 - 0.9F, p.length / 2 + 0.9F, x - 0.75F, x + 0.75F, foot + 0.4F - lower, foot + 1.0F - lower, BigMat.R_STEEL);
            }
        }
        if (blades) {
            //Lames de l'aiguillage : les 6 premiers px du chemin choisi, chromes
            for (float x : new float[]{-GAUGE, GAUGE}) {
                int n = 3;
                for (int k = 0; k < n; k++) {
                    along(m, p, 6F * k / n, 6F * (k + 1) / n, x - 0.55F, x + 0.55F, RT - 0.45F - lower, RT + 0.06F - lower, BigMat.R_CHROME);
                }
            }
        }
    }

    private static void maglev(RailMesh m, TrackPath p, float lower, boolean chosen) {
        strip(m, p, -5.6F, 5.6F, 0, 1.6F - lower, BigMat.R_CONCRETE);
        for (float x : new float[]{-GAUGE, GAUGE}) {
            strip(m, p, x - 0.8F, x + 0.8F, 1.6F - lower, RT - lower, BigMat.R_LUNAR);
        }
        strip(m, p, -2.6F, 2.6F, 1.6F - lower, 2.0F - lower, BigMat.R_LUNAR);
        strip(m, p, -0.6F, 0.6F, 2.0F - lower, 2.2F - lower, chosen ? BigMat.R_LAMP_G : BigMat.R_GLOW_CYAN);
        int n = Math.max(1, Math.round(p.length / 4F));
        float step = p.length / n;
        for (int k = 0; k < n; k++) {
            float s = step * (k + 0.5F);
            frame(m, p, s);
            for (float x : new float[]{-GAUGE, GAUGE}) {
                m.box(x - 0.65F, RT - 0.4F - lower, -1, x + 0.65F, RT + 0.05F - lower, 1, BigMat.R_GLOW_BLUE);
            }
            unframe(m);
        }
    }

    // ------------------------------------------------------------------ remblai des pentes (0.20.3)

    /** Face du remblai : coins (px du bloc), UV (px de texture), normale, face du bloc porteur dont elle prend la texture. */
    public record EmbankFace(float[][] pts, float[][] uv, float[] normal, Direction face) {}

    /**
     * Remblai plein sous une pente : un coin du bas du bloc jusque sous le lit de la voie (moitie basse 0 -> 8 px, moitie haute
     * 8 -> 16 px), sur toute la largeur ; dessus incline, flancs dont le haut suit la pente (UV comptees depuis le haut : la bordure d'herbe
     * longe le talus). Les bouts sont contre la moitie jumelle ou le bloc du haut : pas dessines. Textures : {@code RailBakedModel}.
     */
    public static List<EmbankFace> embankment(TrackShape shape) {
        List<EmbankFace> out = new ArrayList<>();
        Direction f = shape.slopeRising();
        if (f == null) {
            return out;
        }
        float y0 = shape.slopeHigh() ? 8 : 0;
        float e = 0.03F;
        //u : de la face basse (0) a la face haute (16) ; w : en travers
        float[][] top = new float[4][], uv = new float[4][];
        float[][] corners = {{0, 0}, {0, 16}, {16, 16}, {16, 0}};
        for (int i = 0; i < 4; i++) {
            float u = corners[i][0], w = corners[i][1];
            top[i] = slopePoint(f, u, w, y0 + u / 2 - e);
            uv[i] = new float[]{w, 16 - u};
        }
        float ny = 1 / (float) Math.sqrt(1.25), nu = -0.5F / (float) Math.sqrt(1.25);
        out.add(new EmbankFace(top, uv, new float[]{f.getStepX() * nu, ny, f.getStepZ() * nu}, Direction.UP));
        for (float w : new float[]{0, 16}) {
            Direction side = w == 0 ? (f.getAxis() == Direction.Axis.Z ? Direction.WEST : Direction.NORTH)
                  : (f.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH);
            if (f == Direction.SOUTH || f == Direction.WEST) {
                side = side.getOpposite();
            }
            float h0 = Math.max(0, y0 - e), h1 = y0 + 8 - e;
            float[][] pts = {slopePoint(f, 0, w, 0), slopePoint(f, 16, w, 0), slopePoint(f, 16, w, h1), slopePoint(f, 0, w, h0)};
            float[][] st = {{0, h0}, {16, h1}, {16, 0}, {0, 0}};
            out.add(new EmbankFace(pts, st, new float[]{side.getStepX(), 0, side.getStepZ()}, side));
        }
        return out;
    }

    /** Point du repere de la pente (u le long de la montee, w en travers, y en hauteur) en px du bloc. */
    private static float[] slopePoint(Direction f, float u, float w, float y) {
        return switch (f) {
            case NORTH -> new float[]{w, y, 16 - u};
            case SOUTH -> new float[]{16 - w, y, u};
            case EAST -> new float[]{u, y, w};
            default -> new float[]{16 - u, y, 16 - w};
        };
    }

    /** Moteur d'aiguillage : boitier a cote du talon, voyant vert du cote de la branche choisie. */
    private static void switchMachine(RailMesh m, TrackShape shape, int route) {
        TrackPath p0 = shape.paths().get(0);
        Direction stem = p0.a.face();
        Direction side = stem.getClockWise();
        float cx = 8 + stem.getStepX() * 5.5F + side.getStepX() * 7.0F, cz = 8 + stem.getStepZ() * 5.5F + side.getStepZ() * 7.0F;
        m.box(cx - 1.2F, 0, cz - 1.2F, cx + 1.2F, 2.6F, cz + 1.2F, BigMat.R_DARK);
        m.box(cx - 1.25F, 2.0F, cz - 1.25F, cx + 1.25F, 2.4F, cz + 1.25F, BigMat.R_STRIPE);
        m.box(cx - 0.5F, 2.6F, cz - 0.5F, cx + 0.5F, 3.4F, cz + 0.5F, route == 0 ? BigMat.R_LAMP_G : BigMat.R_LAMP_O);
        //Tringle de manoeuvre
        float tx = 8 + stem.getStepX() * 6.5F, tz = 8 + stem.getStepZ() * 6.5F;
        if (stem.getAxis() == Direction.Axis.Z) {
            m.box(2.0F, RT - 1.6F, tz - 0.3F, 14.0F, RT - 1.2F, tz + 0.3F, BigMat.R_STEEL);
        } else {
            m.box(tx - 0.3F, RT - 1.6F, 2.0F, tx + 0.3F, RT - 1.2F, 14.0F, BigMat.R_STEEL);
        }
    }

    // ------------------------------------------------------------------ pieces fonctionnelles

    /** Butoir : traverse rouge a tampons au bout du chemin (vers la face fermee). */
    private static void bufferStop(RailMesh m, TrackPath p) {
        frame(m, p, p.length);
        float y = RT;
        m.box(-6, y - 1.0F, 0.6F, 6, y + 4.4F, 3.6F, BigMat.R_RED);
        m.box(-5, y + 4.4F, 1.1F, 5, y + 5.4F, 3.1F, BigMat.R_HAZARD);
        for (float x : new float[]{-GAUGE, GAUGE}) {
            m.cyl('z', x, y + 2.1F, 1.1F, -0.8F, 0.6F, BigMat.R_CHROME, 10);
            m.box(x - 1.6F, y + 0.5F, -1.4F, x + 1.6F, y + 3.7F, -0.8F, BigMat.R_DARK);
        }
        for (float x : new float[]{-5.6F, 5.6F}) {
            m.box(x - 0.6F, 0, 3.0F, x + 0.6F, y + 2.6F, 4.6F, BigMat.R_STEEL);
        }
        m.box(-0.8F, y + 5.4F, 1.6F, 0.8F, y + 6.8F, 2.6F, BigMat.R_LAMP_R);
        unframe(m);
    }

    private static void brake(RailMesh m, TrackPath p) {
        int n = 3;
        for (int k = 0; k < n; k++) {
            float s = p.length * (k + 0.5F) / n;
            frame(m, p, s);
            for (float x : new float[]{-GAUGE + 1.1F, GAUGE - 1.1F}) {
                m.box(x - 0.45F, RT - 1.5F, -1.6F, x + 0.45F, RT - 0.2F, 1.6F, k == 1 ? BigMat.R_HAZARD : BigMat.R_RED);
            }
            unframe(m);
        }
        strip(m, p, -1.2F, 1.2F, RT - 1.6F, RT - 1.3F, BigMat.R_DARK);
    }

    private static void booster(RailMesh m, TrackPath p) {
        strip(m, p, -1.6F, 1.6F, RT - 1.6F, RT - 1.1F, BigMat.R_DARK);
        int n = 4;
        for (int k = 0; k < n; k++) {
            float s = p.length * (k + 0.5F) / n;
            frame(m, p, s);
            m.box(-1.4F, RT - 1.4F, -1.2F, 1.4F, RT - 0.6F, 1.2F, BigMat.R_COPPER);
            m.box(-0.6F, RT - 0.6F, -0.6F, 0.6F, RT - 0.4F, 0.6F, BigMat.R_GLOW_CYAN);
            unframe(m);
        }
    }

    private static void detector(RailMesh m, TrackPath p, boolean powered) {
        frame(m, p, p.length / 2);
        m.box(-1.0F, RT - 1.6F, -2.0F, 1.0F, RT - 0.8F, 2.0F, BigMat.R_DARK);
        m.box(-0.5F, RT - 0.8F, -0.5F, 0.5F, RT - 0.6F, 0.5F, powered ? BigMat.R_LAMP_R : BigMat.R_LAMP_OFF);
        //Boitier a cote de la voie
        m.box(6.6F, 0, -1.8F, 8.0F, 3.6F, 1.8F, BigMat.R_DARK);
        m.box(6.5F, 2.2F, -0.8F, 6.6F, 3.0F, 0.8F, powered ? BigMat.R_LAMP_R : BigMat.R_LAMP_OFF);
        unframe(m);
    }

    /** 0.20.5 : pont-bascule : plateau d'acier entre les rails sur un cadre, boitier de pesee a afficheur a cote de la voie. */
    private static void weighbridge(RailMesh m, TrackPath p, boolean powered) {
        frame(m, p, p.length / 2);
        //Plateau et cadre (sous le niveau des rails)
        m.box(-2.6F, RT - 1.5F, -7.0F, 2.6F, RT - 1.1F, 7.0F, BigMat.R_STEEL_L);
        m.box(-2.9F, RT - 1.7F, -7.4F, -2.6F, RT - 1.0F, 7.4F, BigMat.R_DARK);
        m.box(2.6F, RT - 1.7F, -7.4F, 2.9F, RT - 1.0F, 7.4F, BigMat.R_DARK);
        for (int k = -1; k <= 1; k++) {
            m.box(-2.4F, RT - 1.1F, k * 4.6F - 0.2F, 2.4F, RT - 1.0F, k * 4.6F + 0.2F, BigMat.R_HAZARD);
        }
        //Boitier et afficheur
        m.box(6.4F, 0, -2.4F, 8.0F, 5.2F, 2.4F, BigMat.R_FRAME);
        m.box(6.3F, 3.0F, -1.8F, 6.4F, 4.6F, 1.8F, powered ? BigMat.R_SCREEN : BigMat.R_GLASS_D);
        m.box(6.3F, 1.4F, -0.4F, 6.4F, 2.2F, 0.4F, powered ? BigMat.R_LAMP_G : BigMat.R_LAMP_OFF);
        unframe(m);
    }

    /** 0.20.5 : rail de detelage : verin central jaune et noir entre les rails, levier et voyant a cote de la voie. */
    /** 0.20.6 : passage a niveau : planches entre les rails et de part et d'autre (ornieres laissees aux boudins des roues). */
    private static void levelCrossing(RailMesh m, TrackPath p) {
        float y0 = RT - 1.6F, y1 = RT - 0.25F;
        strip(m, p, -GAUGE + 1.1F, GAUGE - 1.1F, y0, y1, BigMat.R_PLANKS);
        strip(m, p, GAUGE + 0.8F, 8, y0, y1, BigMat.R_PLANKS);
        strip(m, p, -8, -GAUGE - 0.8F, y0, y1, BigMat.R_PLANKS);
        strip(m, p, -8, -6.8F, 0, y0, BigMat.R_DARK);
        strip(m, p, 6.8F, 8, 0, y0, BigMat.R_DARK);
    }

    private static void uncoupler(RailMesh m, TrackPath p, boolean powered) {
        frame(m, p, p.length / 2);
        m.box(-1.6F, RT - 1.6F, -3.0F, 1.6F, RT - 1.0F, 3.0F, BigMat.R_DARK);
        m.box(-0.9F, RT - 1.0F, -1.8F, 0.9F, RT - 0.4F, 1.8F, BigMat.R_HAZARD);
        m.box(-0.3F, RT - 0.4F, -0.3F, 0.3F, RT + (powered ? 0.6F : -0.2F), 0.3F, BigMat.R_CHROME);
        //Levier
        m.box(6.6F, 0, -1.4F, 8.0F, 1.6F, 1.4F, BigMat.R_DARK);
        m.box(7.0F, 1.6F, -0.25F, 7.5F, 4.8F, 0.25F, BigMat.R_HAZARD);
        m.box(6.5F, 0.6F, -0.4F, 6.6F, 1.2F, 0.4F, powered ? BigMat.R_LAMP_O : BigMat.R_LAMP_OFF);
        unframe(m);
    }
}
