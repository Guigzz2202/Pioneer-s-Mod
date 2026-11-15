package fr.pioneers.village;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.9.1 (retours de Guillaume) : plan du decor de la place, LOGIQUE PURE (aucune classe Minecraft : le banc {@code tools/rptest/PlazaBench}
 * la fait tourner sur de vrais villages vanilla). Le decor forme une vraie place posee d'un seul tenant pres de la cloche et tournee vers
 * elle : Tableau des besoins au fond face a la cloche, fontaine au centre de l'espace libre, lampadaires de part et d'autre, bannieres sur
 * des mats aux angles. Chaque objet repose sur un sol naturel plein et plat (jamais un chemin, une culture, de l'eau, un vide, un bloc de
 * construction), avec un bloc d'air entre lui et toute construction (porte jamais bloquee), et rien ne cache la cloche. Si la grande place
 * ne tient nulle part, on essaie des places plus petites : moins d'objets plutot que mal poses ; le Tableau seul en dernier recours.
 * <p>
 * Repere d'un gabarit : {@code w} = profondeur depuis le bord tourne vers la cloche (0) vers le fond ; {@code u} = travers (symetrique).
 * Directions horizontales numerotees comme {@code Direction.get2DDataValue} : 0 sud (+z), 1 ouest (-x), 2 nord (-z), 3 est (+x).
 */
public final class PlazaPlanner {

    /** Ce qu'il y a dans une case du monde, vu par le planificateur. */
    public enum Kind {
        /** Air (ou vide de structure). */
        AIR,
        /** Plante remplaçable (herbe, fougere, couche de neige) : un objet peut la remplacer. */
        PLANT,
        /** Petite plante non remplaçable (fleur, pousse) : on marche dessus, on ne pose rien dessus. */
        SOFT,
        /** Sol naturel plein (herbe, terre, sable, neige, pierre). Seul support permis pour un objet. */
        GROUND,
        /** Chemin (chemin de terre, dalle de rue) : on y marche, on n'y pose jamais rien. */
        PATH,
        /** Champ : terre labouree ou culture. */
        FARM,
        /** Eau, lave, plante aquatique. */
        FLUID,
        /** Tout le reste : maisons, murets, lampes, arbres, blocs de joueur, territoire d'un clan. */
        STRUCTURE
    }

    /** Lecture du monde (ou d'un monde factice). */
    @FunctionalInterface
    public interface World {

        Kind at(int x, int y, int z);
    }

    /** Les pieces du decor et leur hauteur libre demandee (objet + tete). */
    public enum Piece {
        BOARD(2), FOUNTAIN(2), LAMP(3), MAST(3);

        public final int clearance;

        Piece(int clearance) {
            this.clearance = clearance;
        }
    }

    /** Une piece posee : position de sa case basse, direction de sa facade (vers la cloche). */
    public record Placed(Piece piece, int x, int y, int z, int facing) {
    }

    /** Le plan retenu : gabarit, pieces, direction de la place (de la cloche vers le fond), note (plus petite = meilleure). */
    public record Plan(String template, List<Placed> pieces, int dir, int score) {

        public boolean isEmpty() {
            return pieces.isEmpty();
        }
    }

    public static final Plan NONE = new Plan("none", List.of(), 0, Integer.MAX_VALUE);

    record Slot(Piece piece, int u, int w) {
    }

    /** Gabarit : largeur impaire (u de -width/2 a +width/2), profondeur (w de 0 a depth - 1). */
    record Template(String id, int width, int depth, List<Slot> slots) {
    }

    /** Rayon de recherche autour de la cloche (bord de la place le plus loin). */
    public static final int RADIUS = 18;
    /** Distance mini (Chebyshev) entre la cloche et la place : la piece du centre du village reste libre. */
    public static final int BELL_GAP = 3;
    /** Hauteurs cherchees autour de la cloche. */
    public static final int BELOW = 6, ABOVE = 3;

    /**
     * Gabarits, du plus complet au plus petit. M = mat + banniere, L = lampadaire, F = fontaine, T = Tableau (face a la cloche, w = 0).
     * Grande place 7 x 5 :       M . . . . . M / . . . . . . . / . L . F . L . / . . . . . . . / M . . T . . M
     */
    static final List<Template> TEMPLATES = List.of(
          template("grand", 7, "M.....M", ".......", ".L.F.L.", ".......", "M..T..M"),
          template("medium", 5, ".....", "L.F.L", ".....", "M.T.M"),
          template("small", 5, "L.F.L", ".....", "..T.."),
          template("fountain", 3, ".F.", "...", ".T."),
          template("board", 1, "T"));

    private PlazaPlanner() {
    }

    static Template template(String id, int width, String... rows) {
        List<Slot> slots = new ArrayList<>();
        for (int w = 0; w < rows.length; w++) {
            for (int i = 0; i < width; i++) {
                char c = rows[w].charAt(i);
                Piece p = switch (c) {
                    case 'M' -> Piece.MAST;
                    case 'L' -> Piece.LAMP;
                    case 'F' -> Piece.FOUNTAIN;
                    case 'T' -> Piece.BOARD;
                    default -> null;
                };
                if (p != null) {
                    slots.add(new Slot(p, i - width / 2, w));
                }
            }
        }
        return new Template(id, width, rows.length, List.copyOf(slots));
    }

    /** Pas d'une direction horizontale (0 sud, 1 ouest, 2 nord, 3 est). */
    public static int dx(int dir) {
        return dir == 1 ? -1 : dir == 3 ? 1 : 0;
    }

    public static int dz(int dir) {
        return dir == 0 ? 1 : dir == 2 ? -1 : 0;
    }

    public static int opposite(int dir) {
        return (dir + 2) & 3;
    }

    static boolean open(Kind k) {
        return k == Kind.AIR || k == Kind.PLANT;
    }

    static boolean walkableAir(Kind k) {
        return k == Kind.AIR || k == Kind.PLANT || k == Kind.SOFT;
    }

    static boolean floor(Kind k) {
        return k == Kind.GROUND || k == Kind.PATH || k == Kind.STRUCTURE;
    }

    /**
     * Cherche la meilleure place autour de la cloche {@code (bx, by, bz)}. Renvoie {@link #NONE} si meme le Tableau seul ne tient pas.
     */
    public static Plan plan(World world, int bx, int by, int bz) {
        int ref = reference(world, bx, by, bz);
        for (Template t : TEMPLATES) {
            Plan best = null;
            for (int dir = 0; dir < 4; dir++) {
                for (int k = BELL_GAP; k + t.depth() - 1 <= RADIUS; k++) {
                    for (int s = -k; s <= k; s++) {
                        Plan p = tryAt(world, t, bx, by, bz, ref, dir, k, s);
                        if (p != null && (best == null || p.score() < best.score())) {
                            best = p;
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return NONE;
    }

    /**
     * Hauteur du sol du village autour de la cloche (mediane des colonnes de sol naturel ou de chemin a 3..8 blocs) : la place se pose a
     * ce niveau de preference, pas sur une butte voisine.
     */
    static int reference(World w, int bx, int by, int bz) {
        List<Integer> ys = new ArrayList<>();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) < BELL_GAP) {
                    continue;
                }
                for (int y = by + ABOVE; y > by - BELOW; y--) {
                    Kind below = w.at(bx + dx, y - 1, bz + dz);
                    if (open(below) || below == Kind.SOFT) {
                        continue;
                    }
                    if (below == Kind.GROUND || below == Kind.PATH) {
                        ys.add(y);
                    }
                    break;
                }
            }
        }
        if (ys.isEmpty()) {
            return by - 1;
        }
        ys.sort(Integer::compare);
        return ys.get(ys.size() / 2);
    }

    /** Hauteur de pose (case juste au-dessus du sol) d'une colonne, ou Integer.MIN_VALUE. */
    static int groundAt(World w, int x, int z, int by, int clearance) {
        if (!open(w.at(x, by + ABOVE, z))) {
            return Integer.MIN_VALUE;
        }
        for (int y = by + ABOVE; y > by - BELOW; y--) {
            Kind below = w.at(x, y - 1, z);
            if (open(below)) {
                continue;
            }
            //premier bloc plein en descendant : il doit etre un sol naturel, avec la hauteur libre au-dessus
            if (below != Kind.GROUND) {
                return Integer.MIN_VALUE;
            }
            for (int h = 0; h < clearance; h++) {
                if (!open(w.at(x, y + h, z))) {
                    return Integer.MIN_VALUE;
                }
            }
            return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Essaie un gabarit : bord avant a {@code k} blocs de la cloche dans la direction {@code dir}, decale de {@code s} en travers. */
    static Plan tryAt(World world, Template t, int bx, int by, int bz, int ref, int dir, int k, int s) {
        int wx = dx(dir), wz = dz(dir);
        int ux = -wz, uz = wx;
        int fx = bx + wx * k + ux * s, fz = bz + wz * k + uz * s;
        int half = t.width() / 2;
        //hauteur : celle du Tableau (toujours present)
        Slot board = null;
        for (Slot sl : t.slots()) {
            if (sl.piece() == Piece.BOARD) {
                board = sl;
            }
        }
        if (board == null) {
            return null;
        }
        int y0 = groundAt(world, fx + ux * board.u() + wx * board.w(), fz + uz * board.u() + wz * board.w(), by, Piece.BOARD.clearance);
        if (y0 == Integer.MIN_VALUE) {
            return null;
        }
        int score = 10 * k + 3 * Math.abs(s) + 15 * Math.abs(y0 - ref);
        //cases du gabarit et anneau de marge
        for (int u = -half - 1; u <= half + 1; u++) {
            for (int w = -1; w <= t.depth(); w++) {
                int x = fx + ux * u + wx * w, z = fz + uz * u + wz * w;
                if (Math.max(Math.abs(x - bx), Math.abs(z - bz)) < BELL_GAP) {
                    return null;
                }
                boolean inside = u >= -half && u <= half && w >= 0 && w < t.depth();
                if (!inside) {
                    //marge : aucune construction a hauteur d'homme (et de porte) autour de la place
                    for (int h = 0; h < 3; h++) {
                        Kind kk = world.at(x, y0 + h, z);
                        if (kk == Kind.STRUCTURE) {
                            return null;
                        }
                    }
                    continue;
                }
                Slot slot = slotAt(t, u, w);
                if (slot != null) {
                    if (world.at(x, y0 - 1, z) != Kind.GROUND) {
                        return null;
                    }
                    for (int h = 0; h < Math.max(3, slot.piece().clearance); h++) {
                        if (!open(world.at(x, y0 + h, z))) {
                            return null;
                        }
                    }
                } else {
                    int step = walkStep(world, x, y0, z);
                    if (step == Integer.MIN_VALUE) {
                        return null;
                    }
                    score += 2 * Math.abs(step);
                    if (world.at(x, y0 - 1 + step, z) == Kind.PATH) {
                        score += 1;
                    }
                }
            }
        }
        //rien ne cache la cloche : ligne de vue de la cloche au centre de la place, a hauteur d'homme
        double cx = fx + wx * (t.depth() - 1) / 2.0, cz = fz + wz * (t.depth() - 1) / 2.0;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(cx - bx, cz - bz) * 2));
        int blocked = 0, seen = 0;
        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.round(bx + (cx - bx) * i / steps), z = (int) Math.round(bz + (cz - bz) * i / steps);
            if (Math.max(Math.abs(x - bx), Math.abs(z - bz)) < BELL_GAP) {
                continue;
            }
            seen++;
            if (world.at(x, y0 + 1, z) == Kind.STRUCTURE || world.at(x, y0 + 2, z) == Kind.STRUCTURE) {
                blocked++;
            }
        }
        if (blocked * 5 > Math.max(1, seen)) {
            return null;
        }
        score += 6 * blocked;
        List<Placed> out = new ArrayList<>();
        int face = opposite(dir);
        for (Slot sl : t.slots()) {
            out.add(new Placed(sl.piece(), fx + ux * sl.u() + wx * sl.w(), y0, fz + uz * sl.u() + wz * sl.w(), face));
        }
        return new Plan(t.id(), List.copyOf(out), dir, score);
    }

    static Slot slotAt(Template t, int u, int w) {
        for (Slot s : t.slots()) {
            if (s.u() == u && s.w() == w) {
                return s;
            }
        }
        return null;
    }

    /**
     * Case vide de la place : on doit pouvoir y marcher a la hauteur y0 a une marche pres (sol dessous, deux cases libres), sans eau ni champ.
     * Renvoie la marche (-1, 0, +1) ou Integer.MIN_VALUE.
     */
    static int walkStep(World world, int x, int y0, int z) {
        for (int dy : new int[]{0, 1, -1}) {
            Kind below = world.at(x, y0 - 1 + dy, z), at = world.at(x, y0 + dy, z), head = world.at(x, y0 + dy + 1, z);
            if (below == Kind.FLUID || below == Kind.FARM || at == Kind.FLUID || at == Kind.FARM) {
                return Integer.MIN_VALUE;
            }
            if (floor(below) && walkableAir(at) && walkableAir(head)) {
                //une marche vers le haut demande que la case y0 soit elle-meme un sol (pas un mur)
                if (dy == 1 && below == Kind.STRUCTURE) {
                    continue;
                }
                return dy;
            }
        }
        return Integer.MIN_VALUE;
    }
}
