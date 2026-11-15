package fr.pioneers.farm.greenhouse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 3, specification « Le monde vivant » §2.2) : RELEVE D'UNE SERRE, calcul PUR (aucun monde : une {@link Grid} dit ce
 * qu'il y a dans chaque case ; le jeu la lit sur le monde, le banc {@code tools/greenhouse/GreenhouseBench} sur des mondes ecrits a la
 * main). Le regulateur est pose dans une paroi : on remplit la piece depuis une case d'air voisine (6 voisins, jamais en diagonale).
 * <ul>
 *   <li>{@link Kind#OPEN} : l'air et tout ce qui ne bouche pas (cultures, plantes, troncs et feuilles d'un arbre de la serre, torches,
 *   eau...) : c'est l'INTERIEUR ;</li>
 *   <li>{@link Kind#WALL} (blocs de serre : vitres, cadres, trappes vitrees ouvertes ou fermees), {@link Kind#DOOR} (toute porte, ouverte
 *   ou fermee), {@link Kind#REGULATOR} : la PAROI ;</li>
 *   <li>{@link Kind#SOLID} (tout autre bloc plein) : permis seulement comme SOL ({@link #ground} : sous l'interieur, ou sous la paroi
 *   sans etre aligne sur elle) ; ailleurs (mur, toit, pilier) c'est un BLOC FAUTIF ;</li>
 *   <li>{@link Kind#UNLOADED} : chunk voisin non charge : releve impossible (rien ne change).</li>
 * </ul>
 * Une piece qui ne se referme pas avant {@link #EXPLORE} cases ou hors de la boite d'exploration FUIT si son chemin vers le dehors trouve
 * un trou net ou passe sous le ciel (sinon elle est fermee mais TROP GRANDE, ou ouverte sur une grotte) : la case du trou est cherchee sur
 * le plus court chemin de l'interieur vers le dehors ({@link #findHole}) : une case de ce chemin d'ou l'on voit loin dans une direction
 * (« ouverte ») et entouree de paroi dans le plan perpendiculaire est le trou (3 ou 4 voisins pleins : position EXACTE).
 */
public final class GreenhouseScan {

    /** Volume interieur permis (blocs d'air et de plantes) et dimensions maximales de l'interieur (largeur, longueur, hauteur). */
    public static final int MIN_VOLUME = 8, MAX_VOLUME = 4096, MAX_WIDTH = 32, MAX_HEIGHT = 16;
    /** Exploration : cases au plus, et ecart au germe (au-dela : la piece fuit, ou elle est bien trop grande). */
    public static final int EXPLORE = 8192, REACH_XZ = 40, REACH_Y = 20;
    /** Portee des rayons qui cherchent le trou d'une fuite. */
    public static final int RAY = 24;
    /** Fautes gardees au plus (plan, interface). */
    public static final int MAX_FAULTS = 64;

    private GreenhouseScan() {
    }

    /** Contenu d'une case. */
    public enum Kind {
        OPEN, WALL, DOOR, REGULATOR, SOLID, UNLOADED;

        /** Paroi de serre (vitres, cadres, trappes, portes, regulateurs). */
        public boolean wall() {
            return this == WALL || this == DOOR || this == REGULATOR;
        }
    }

    /** Ce que voit le releve : le monde (jeu) ou un monde ecrit a la main (banc). */
    public interface Grid {

        Kind kind(BlockPos pos);

        /** Facade d'un regulateur (case {@link Kind#REGULATOR}) ; null : inconnue. */
        @Nullable
        default Direction facing(BlockPos pos) {
            return null;
        }
    }

    /** Verdict du releve. */
    public enum Status {
        /** Serre formee. */
        FORMED("formed"),
        /** La piece fuit (trou dans la paroi ou le toit). */
        LEAK("leak"),
        /** Un bloc qui n'est pas un bloc de serre dans la paroi ou le toit. */
        WRONG_BLOCK("wrong_block"),
        /** Moins de {@link #MIN_VOLUME} blocs d'air. */
        TOO_SMALL("too_small"),
        /** Plus de {@link #MAX_VOLUME} blocs d'air ou plus de 32 x 32 x 16. */
        TOO_BIG("too_big"),
        /** Le regulateur ne touche aucun air (il doit etre pose dans une paroi, un cote vers l'interieur). */
        NO_ROOM("no_room"),
        /** Un autre regulateur regle deja cette serre. */
        SHARED("shared"),
        /** Chunk voisin non charge. */
        UNLOADED("unloaded");

        public final String id;

        Status(String id) {
            this.id = id;
        }

        /** Cle de langue de l'erreur (commune aux multiblocs maison : « A corriger »). */
        public String key() {
            return "gui.pioneers.greenhouse.error." + id;
        }
    }

    /**
     * Releve : verdict, germe (case d'air d'ou part le remplissage), interieur, paroi (vitres, cadres, trappes, portes, regulateurs), sol,
     * blocs fautifs (paroi ou toit qui ne sont pas des blocs de serre, du plus proche au plus lointain du regulateur), trou d'une fuite
     * ({@code exact} : trou entoure de paroi, position sure), autre regulateur (serre partagee), bornes de l'interieur.
     */
    public record Result(Status status, @Nullable BlockPos seed, Set<BlockPos> interior, Set<BlockPos> walls, Set<BlockPos> floor, List<BlockPos> faults,
                         @Nullable BlockPos hole, boolean exact, @Nullable BlockPos other, int[] min, int[] max) {

        public boolean formed() {
            return status == Status.FORMED;
        }

        public int volume() {
            return interior.size();
        }

        /** Dimensions de l'interieur {largeur x, hauteur y, longueur z} (0 si vide). */
        public int[] size() {
            return interior.isEmpty() ? new int[3] : new int[]{max[0] - min[0] + 1, max[1] - min[1] + 1, max[2] - min[2] + 1};
        }

        /** Case a montrer au joueur (trou, premier bloc fautif, autre regulateur), ou null. */
        @Nullable
        public BlockPos at() {
            return switch (status) {
                case LEAK -> hole;
                case WRONG_BLOCK -> faults.isEmpty() ? null : faults.get(0);
                case SHARED -> other;
                default -> null;
            };
        }
    }

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /**
     * Releve la serre du regulateur en {@code regulator} (facade {@code facing}, ou null). Germes essayes : derriere le regulateur (il est
     * pose depuis le dehors, facade vers le joueur), devant, puis les cotes ; ceux qui sont sous un toit de serre d'abord. Le premier qui
     * donne une serre formee l'emporte ; sinon le releve du premier germe (le plus probable) est rendu.
     */
    public static Result survey(Grid grid, BlockPos regulator, @Nullable Direction facing) {
        List<BlockPos> seeds = new ArrayList<>();
        Direction back = facing == null || !facing.getAxis().isHorizontal() ? Direction.NORTH : facing.getOpposite();
        Direction[] order = {back, back.getOpposite(), back.getClockWise(), back.getCounterClockWise()};
        boolean unloaded = false;
        for (Direction d : order) {
            BlockPos s = regulator.relative(d);
            Kind k = grid.kind(s);
            if (k == Kind.OPEN) {
                seeds.add(s);
            } else if (k == Kind.UNLOADED) {
                unloaded = true;
            }
        }
        if (seeds.isEmpty()) {
            return empty(unloaded ? Status.UNLOADED : Status.NO_ROOM, null);
        }
        //Germes sous un toit de serre d'abord (le dehors a le ciel au-dessus)
        List<BlockPos> sorted = new ArrayList<>();
        for (BlockPos s : seeds) {
            if (roofed(grid, s)) {
                sorted.add(s);
            }
        }
        for (BlockPos s : seeds) {
            if (!sorted.contains(s)) {
                sorted.add(s);
            }
        }
        Result first = null;
        for (BlockPos s : sorted) {
            Result r = fill(grid, regulator, s);
            if (r.formed() || r.status() == Status.UNLOADED) {
                return r;
            }
            if (first == null) {
                first = r;
            }
        }
        return first;
    }

    /** Une case de paroi de serre au-dessus, a moins de {@link #MAX_HEIGHT} + 1 blocs, au travers de l'air ? */
    static boolean roofed(Grid grid, BlockPos seed) {
        BlockPos.MutableBlockPos p = seed.mutable();
        for (int i = 0; i <= MAX_HEIGHT; i++) {
            p.move(Direction.UP);
            Kind k = grid.kind(p);
            if (k != Kind.OPEN) {
                return k.wall();
            }
        }
        return false;
    }

    private static Result empty(Status status, @Nullable BlockPos seed) {
        return new Result(status, seed, Set.of(), Set.of(), Set.of(), List.of(), null, false, null, new int[3], new int[3]);
    }

    /** Remplissage depuis un germe : interieur, paroi, sol, fautes ; fuite et trou. */
    static Result fill(Grid grid, BlockPos regulator, BlockPos seed) {
        Set<BlockPos> interior = new LinkedHashSet<>();
        Set<BlockPos> walls = new LinkedHashSet<>();
        Set<BlockPos> solids = new LinkedHashSet<>();
        Set<BlockPos> regulators = new LinkedHashSet<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seed = seed.immutable();
        interior.add(seed);
        parent.put(seed, seed);
        queue.add(seed);
        int[] min = {seed.getX(), seed.getY(), seed.getZ()}, max = {seed.getX(), seed.getY(), seed.getZ()};
        BlockPos escape = null;
        while (!queue.isEmpty()) {
            BlockPos c = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = c.relative(d);
                if (interior.contains(n) || walls.contains(n) || solids.contains(n)) {
                    continue;
                }
                Kind k = grid.kind(n);
                switch (k) {
                    case UNLOADED -> {
                        return empty(Status.UNLOADED, seed);
                    }
                    case WALL, DOOR -> walls.add(n);
                    case REGULATOR -> {
                        walls.add(n);
                        regulators.add(n);
                    }
                    case SOLID -> solids.add(n);
                    default -> {
                        if (Math.abs(n.getX() - seed.getX()) > REACH_XZ || Math.abs(n.getZ() - seed.getZ()) > REACH_XZ
                            || Math.abs(n.getY() - seed.getY()) > REACH_Y || interior.size() >= EXPLORE) {
                            parent.put(n, c);
                            escape = n;
                            break;
                        }
                        interior.add(n);
                        parent.put(n, c);
                        queue.add(n);
                        min[0] = Math.min(min[0], n.getX());
                        min[1] = Math.min(min[1], n.getY());
                        min[2] = Math.min(min[2], n.getZ());
                        max[0] = Math.max(max[0], n.getX());
                        max[1] = Math.max(max[1], n.getY());
                        max[2] = Math.max(max[2], n.getZ());
                    }
                }
                if (escape != null) {
                    break;
                }
            }
            if (escape != null) {
                break;
            }
        }
        if (escape != null) {
            //Fuite : trou cherche sur le plus court chemin du germe vers le dehors
            List<BlockPos> path = new ArrayList<>();
            for (BlockPos p = escape; ; p = parent.get(p)) {
                path.add(p);
                if (p.equals(seed) || parent.get(p) == null) {
                    break;
                }
            }
            Collections.reverse(path);
            Hole hole = findHole(grid, path);
            if ((hole == null || !hole.exact) && !sky(grid, path)) {
                //Aucun trou net et jamais le ciel sur le chemin : piece fermee mais bien trop grande (ou ouverte sur une grotte)
                return new Result(Status.TOO_BIG, seed, Set.of(), Set.of(), Set.of(), List.of(), null, false, null, new int[3], new int[3]);
            }
            BlockPos at = hole == null ? path.get(Math.min(path.size() - 1, 1)) : hole.at;
            return new Result(Status.LEAK, seed, Set.of(), Set.of(), Set.of(), List.of(at), at, hole != null && hole.exact, null, new int[3],
                  new int[3]);
        }
        //Sol : un bloc plein n'est permis que sous l'interieur ; ailleurs (mur, toit), c'est un bloc fautif
        Set<BlockPos> floor = new LinkedHashSet<>();
        List<BlockPos> faults = new ArrayList<>();
        for (BlockPos s : solids) {
            if (ground(grid, interior, walls, s)) {
                floor.add(s);
            } else {
                faults.add(s);
            }
        }
        faults.sort((a, b) -> Double.compare(a.distSqr(regulator), b.distSqr(regulator)));
        if (faults.size() > MAX_FAULTS) {
            faults = new ArrayList<>(faults.subList(0, MAX_FAULTS));
        }
        Status status;
        BlockPos other = null;
        int w = max[0] - min[0] + 1, hgt = max[1] - min[1] + 1, l = max[2] - min[2] + 1;
        if (!faults.isEmpty()) {
            status = Status.WRONG_BLOCK;
        } else if (interior.size() < MIN_VOLUME) {
            status = Status.TOO_SMALL;
        } else if (interior.size() > MAX_VOLUME || w > MAX_WIDTH || l > MAX_WIDTH || hgt > MAX_HEIGHT) {
            status = Status.TOO_BIG;
        } else {
            //Un seul regulateur par serre : celui qui a le plus petit indice garde la serre (les autres disent « deja reglee »)
            for (BlockPos r : regulators) {
                if (r.equals(regulator)) {
                    continue;
                }
                Direction f = grid.facing(r);
                BlockPos back = f == null ? null : r.relative(f.getOpposite());
                if (back != null && interior.contains(back) && r.asLong() < regulator.asLong()) {
                    other = r;
                    break;
                }
            }
            status = other == null ? Status.FORMED : Status.SHARED;
        }
        return new Result(status, seed, interior, walls, floor, faults, null, false, other, min, max);
    }

    /**
     * Un bloc plein qui touche l'interieur est-il un SOL ? Oui s'il porte l'interieur (case au-dessus dans la serre : sol, plate-bande,
     * coffre pose par terre), ou s'il porte la paroi (case au-dessus : paroi) sans etre aligne sur elle (aucun voisin horizontal de paroi :
     * terre sous un mur au bord d'une rigole creusee, muret d'un bloc sous les vitres). Sinon (dans un mur, au toit, pilier), il est fautif.
     */
    static boolean ground(Grid grid, Set<BlockPos> interior, Set<BlockPos> walls, BlockPos s) {
        BlockPos up = s.above();
        if (interior.contains(up)) {
            return true;
        }
        if (!walls.contains(up) && !grid.kind(up).wall()) {
            return false;
        }
        for (Direction d : HORIZONTAL) {
            BlockPos n = s.relative(d);
            if (walls.contains(n) || grid.kind(n).wall()) {
                return false;
            }
        }
        return true;
    }

    /** Trou d'une fuite : case et position sure. */
    record Hole(BlockPos at, boolean exact) {
    }

    /**
     * Trou d'une fuite sur le chemin {@code path} (germe -> dehors) : pour chaque case et chaque direction ou l'on voit loin (rayon de
     * {@link #RAY} cases d'air sans rien rencontrer), le nombre de voisins pleins dans le plan perpendiculaire (paroi, sol). Le trou est la
     * case qui en a le plus (a egalite, la plus proche du dehors), cherchee avant le dehors (jusqu'a la premiere case sous le ciel et la
     * suivante) ; exact a partir de 3 (trou dans un mur, un toit, au pied d'un mur).
     */
    @Nullable
    static Hole findHole(Grid grid, List<BlockPos> path) {
        BlockPos best = null;
        int bestScore = -1;
        Set<BlockPos> seen = new HashSet<>();
        //Le trou est avant le dehors : chemin jusqu'a la premiere case sous le ciel, et la suivante (trou d'un toit)
        int end = path.size() - 1;
        for (int i = 0; i < path.size(); i++) {
            if (sky(grid, List.of(path.get(i)))) {
                end = Math.min(path.size() - 1, i + 1);
                break;
            }
        }
        for (BlockPos c : path.subList(0, end + 1)) {
            if (!seen.add(c) || grid.kind(c) != Kind.OPEN) {
                continue;
            }
            for (Direction d : Direction.values()) {
                if (!open(grid, c, d)) {
                    continue;
                }
                int score = 0;
                for (Direction p : Direction.values()) {
                    if (p.getAxis() != d.getAxis() && grid.kind(c.relative(p)) != Kind.OPEN) {
                        score++;
                    }
                }
                if (score >= bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
        }
        return best == null ? null : new Hole(best, bestScore >= 3);
    }

    /** Hauteur du rayon du ciel (une case du chemin sous le ciel : la piece donne vraiment sur le dehors). */
    public static final int SKY = 64;

    /** Une case du chemin voit-elle le ciel (rayon vers le haut de {@link #SKY} cases d'air) ? */
    static boolean sky(Grid grid, List<BlockPos> path) {
        for (BlockPos c : path) {
            BlockPos.MutableBlockPos p = c.mutable();
            boolean open = true;
            for (int i = 0; i < SKY && open; i++) {
                p.move(Direction.UP);
                Kind k = grid.kind(p);
                if (k == Kind.UNLOADED) {
                    break;
                }
                open = k == Kind.OPEN;
            }
            if (open) {
                return true;
            }
        }
        return false;
    }

    /** Voit-on loin depuis {@code c} dans la direction {@code d} (rayon d'air de {@link #RAY} cases) ? */
    static boolean open(Grid grid, BlockPos c, Direction d) {
        BlockPos.MutableBlockPos p = c.mutable();
        for (int i = 0; i < RAY; i++) {
            p.move(d);
            Kind k = grid.kind(p);
            if (k == Kind.UNLOADED) {
                return true;
            }
            if (k != Kind.OPEN) {
                return false;
            }
        }
        return true;
    }
}
