package fr.pioneers.logistics.rail.track;

import fr.pioneers.logistics.rail.TrackBlocks;
import fr.pioneers.logistics.rail.net.Piece;
import fr.pioneers.logistics.rail.net.PortKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.0 : pose automatique des voies (comme les rails du jeu de base, en plus complet) et cle. 0.20.3 : la forme est recalculee a chaque
 * pose et a chaque casse, pour la voie et ses voisines (de proche en proche, borne).
 * <ul>
 *     <li>A la pose, la voie se tourne vers les voies voisines qui la visent : deux en face = droite, deux en angle = courbe, trois =
 *     aiguillage (talon du cote ou se tient le joueur), quatre = croisement ; sinon dans l'axe du regard.</li>
 *     <li>Puis chaque voisine se recalcule ({@link #settle}) : une voie isolee se tourne vers la nouvelle, un bout de ligne devient une courbe,
 *     une droite raccordee des deux cotes devient un aiguillage, un aiguillage un triple. Une forme qui garde tous ses raccords ne bouge
 *     pas (choix de la cle respecte).</li>
 *     <li>A la casse, les voisines se simplifient : un aiguillage qui perd une branche redevient droite ou courbe, un croisement un
 *     aiguillage, une pente dont le haut ou le bas manque redevient droite.</li>
 *     <li>Pentes douces : quand une voie est posee un bloc plus haut au bout d'une ligne droite, les deux derniers blocs deviennent une
 *     pente (moitie basse, moitie haute).</li>
 *     <li>Cle : clic droit = forme suivante parmi celles qui vont avec les voies voisines ({@link #wrenchOptions}) ; Maj = forme
 *     precedente.</li>
 * </ul>
 */
public final class TrackPlacement {

    private static final Direction[] H = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** Ordre de la cle. */
    private static final List<TrackShape> CYCLE = new ArrayList<>();

    static {
        CYCLE.add(TrackShape.STRAIGHT_NS);
        CYCLE.add(TrackShape.STRAIGHT_EW);
        CYCLE.add(TrackShape.CURVE_NE);
        CYCLE.add(TrackShape.CURVE_SE);
        CYCLE.add(TrackShape.CURVE_SW);
        CYCLE.add(TrackShape.CURVE_NW);
        for (TrackShape s : TrackShape.values()) {
            //Les variantes diagonales (0.20.6) se choisissent seules d'apres les voisines
            if (s.isSwitch() && s.ordinal() < TrackShape.DIAG_NE.ordinal()) {
                CYCLE.add(s);
            }
        }
        CYCLE.add(TrackShape.CROSSING);
    }

    private TrackPlacement() {
    }

    /** Piece lue dans le monde. */
    @Nullable
    public static Piece view(BlockGetter level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof TrackBlock tb)) {
            return null;
        }
        TrackShape s = tb.shape(st);
        if (s == null) {
            return null;
        }
        return TrackBlocks.pieceOf(st, pos);
    }

    /** Une voie voisine a-t-elle un bout au point de raccord de cette face (niveau du sol) ? */
    private static boolean pointsAt(BlockGetter level, BlockPos pos, Direction face) {
        PortKey key = PortKey.of(pos.getX(), pos.getY(), pos.getZ(), face, 0);
        BlockPos n = pos.relative(face);
        for (BlockPos q : new BlockPos[]{n, n.below(), n.above()}) {
            Piece p = view(level, q);
            if (p == null || p.anchor.equals(pos)) {
                continue;
            }
            for (TrackPath path : p.paths()) {
                for (TrackPath.End e : new TrackPath.End[]{path.a, path.b}) {
                    if (e.open() && PortKey.of(p.anchor, e).face().equals(key) && e.face() == face.getOpposite()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Faces de ce bloc visees par des voies voisines. */
    public static Set<Direction> aimed(BlockGetter level, BlockPos pos) {
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        for (Direction f : H) {
            if (pointsAt(level, pos, f)) {
                out.add(f);
            }
        }
        return out;
    }

    /** Un bout de piece est-il raccorde a une autre piece ? */
    private static boolean connected(BlockGetter level, Piece piece, TrackPath.End e) {
        if (!e.open()) {
            return true;
        }
        PortKey key = PortKey.of(piece.anchor, e);
        BlockPos b = piece.anchor.offset(e.dx(), e.dy(), e.dz()).relative(e.face());
        for (BlockPos q : new BlockPos[]{b, b.below(), b.above()}) {
            Piece p = view(level, q);
            if (p == null || p.anchor.equals(piece.anchor)) {
                continue;
            }
            for (TrackPath path : p.paths()) {
                for (TrackPath.End o : new TrackPath.End[]{path.a, path.b}) {
                    if (o.open() && o.face() == e.face().getOpposite() && PortKey.of(p.anchor, o).equals(key)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Pose
    // ------------------------------------------------------------------

    public static BlockState placementState(TrackBlock block, BlockGetter level, BlockPos pos, Direction look, @Nullable Player player) {
        BlockState base = block.defaultBlockState();
        if (block instanceof BufferStopBlock) {
            return block.withShape(base, TrackShape.buffer(look));
        }
        Set<Direction> aimed = aimed(level, pos);
        if (block instanceof FunctionalTrackBlock) {
            Direction.Axis axis = look.getAxis();
            for (Direction d : aimed) {
                axis = d.getAxis();
                break;
            }
            return block.withShape(base, TrackShape.straight(axis));
        }
        return block.withShape(base, shapeFor(aimed, look, player, pos, level));
    }

    private static TrackShape shapeFor(Set<Direction> aimed, Direction look, @Nullable Player player, BlockPos pos, BlockGetter level) {
        if (aimed.size() >= 4) {
            return TrackShape.CROSSING;
        }
        if (aimed.size() == 3) {
            //Deux faces opposees (la ligne) et une branche
            for (Direction f : H) {
                if (aimed.contains(f) && aimed.contains(f.getOpposite())) {
                    Direction branch = null;
                    for (Direction g : aimed) {
                        if (g.getAxis() != f.getAxis()) {
                            branch = g;
                        }
                    }
                    if (branch == null) {
                        break;
                    }
                    Direction stem = stemToward(player, pos, f.getAxis(), look);
                    return TrackShape.switchShape(stem, branch);
                }
            }
        }
        if (aimed.size() == 2) {
            Direction[] two = aimed.toArray(new Direction[0]);
            return TrackShape.plainFor(two[0], two[1]);
        }
        if (aimed.size() == 1) {
            Direction f = aimed.iterator().next();
            //Une voisine libre sur le cote : on fait la courbe
            for (Direction g : H) {
                if (g.getAxis() != f.getAxis() && g == look && bendable(level, pos.relative(g))) {
                    return TrackShape.plainFor(f, g);
                }
            }
            return TrackShape.straight(f.getAxis());
        }
        return TrackShape.straight(look.getAxis());
    }

    /** Talon d'un aiguillage : le bout de la ligne du cote du joueur. */
    private static Direction stemToward(@Nullable Player player, BlockPos pos, Direction.Axis axis, Direction look) {
        Direction pos1 = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        if (player == null) {
            return look.getAxis() == axis ? look.getOpposite() : pos1;
        }
        double rel = axis == Direction.Axis.X ? player.getX() - (pos.getX() + 0.5) : player.getZ() - (pos.getZ() + 0.5);
        if (Math.abs(rel) < 0.3 && look.getAxis() == axis) {
            return look.getOpposite();
        }
        return rel >= 0 ? pos1 : pos1.getOpposite();
    }

    /** Voie simple dont au plus un bout est raccorde (on peut la tourner). */
    private static boolean bendable(BlockGetter level, BlockPos pos) {
        Piece p = view(level, pos);
        if (p == null || p.kind != Piece.Kind.TRACK || !p.anchor.equals(pos)) {
            return false;
        }
        TrackShape.Family fam = p.shape.family();
        if (fam != TrackShape.Family.STRAIGHT && fam != TrackShape.Family.CURVE) {
            return false;
        }
        TrackPath path = p.path(0);
        int n = (connected(level, p, path.a) ? 1 : 0) + (connected(level, p, path.b) ? 1 : 0);
        return n <= 1;
    }

    // ------------------------------------------------------------------
    // 0.20.7 : chantiers des robots et wagon de pose
    // ------------------------------------------------------------------

    /** Positions dont la forme est imposee (voies d'un chantier) : la pose automatique n'y touche pas. Fil du serveur seulement. */
    private static Predicate<BlockPos> FIXED = p -> false;

    /**
     * Voie posee ou retiree par un chantier : les voies du monde HORS chantier ({@code planned} faux) se recalculent pour s'y raccorder
     * (aiguillage, courbe, pente, diagonale) ; celles du chantier gardent la forme prevue.
     */
    public static void worksChanged(LevelAccessor level, BlockPos pos, boolean removed, Predicate<BlockPos> planned) {
        Predicate<BlockPos> old = FIXED;
        FIXED = planned;
        try {
            if (removed) {
                afterRemove(level, pos);
            } else {
                afterPlace(level, pos, null);
            }
        } finally {
            FIXED = old;
        }
    }

    /** Apres la pose (serveur) : voisines recalculees de proche en proche, pentes, puis la voie elle-meme. */
    public static void afterPlace(LevelAccessor level, BlockPos pos, @Nullable LivingEntity placer) {
        if (view(level, pos) == null) {
            return;
        }
        Player player = placer instanceof Player p ? p : null;
        Set<BlockPos> changed = new HashSet<>();
        changed.add(pos);
        propagate(level, pos, player, false, null, changed);
        ramps(level, pos);
        for (Direction f : H) {
            ramps(level, pos.relative(f));
            ramps(level, pos.relative(f).below());
        }
        if (settle(level, pos, player, false)) {
            propagate(level, pos, player, false, null, changed);
        }
        refineAround(level, changed);
    }

    /** Apres la casse (serveur) : les voisines se simplifient. */
    public static void afterRemove(LevelAccessor level, BlockPos pos) {
        Set<BlockPos> changed = new HashSet<>();
        changed.add(pos);
        propagate(level, pos, null, true, null, changed);
        refineAround(level, changed);
    }

    /** Voisines de {@code start} recalculees, puis les voisines de celles qui ont change (au plus {@link #BUDGET} recalculs). */
    private static void propagate(LevelAccessor level, BlockPos start, @Nullable Player player, boolean reduce, @Nullable BlockPos skip) {
        propagate(level, start, player, reduce, skip, new HashSet<>());
    }

    private static void propagate(LevelAccessor level, BlockPos start, @Nullable Player player, boolean reduce, @Nullable BlockPos skip,
          Set<BlockPos> changed) {
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        int budget = BUDGET;
        while (!queue.isEmpty() && budget > 0) {
            BlockPos c = queue.poll();
            for (Direction f : H) {
                BlockPos n = c.relative(f);
                for (BlockPos q : new BlockPos[]{n, n.below(), n.above()}) {
                    if (q.equals(skip) || q.equals(c) || budget <= 0) {
                        continue;
                    }
                    budget--;
                    if (settle(level, q, player, reduce)) {
                        queue.add(q);
                        changed.add(q.immutable());
                    }
                }
            }
        }
    }

    private static final int BUDGET = 64;

    private static int mask(Set<Direction> faces) {
        int m = 0;
        for (Direction d : faces) {
            m |= 1 << d.get2DDataValue();
        }
        return m;
    }

    /** Talon d'un aiguillage (chemin 0 : tous ses chemins partent de lui). */
    private static Direction stemOf(TrackShape s) {
        return s.paths().get(0).a.face();
    }

    private static boolean plain(TrackShape s) {
        return s.family() == TrackShape.Family.STRAIGHT || s.family() == TrackShape.Family.CURVE || s.family() == TrackShape.Family.DIAGONAL;
    }

    /**
     * Recalcule la forme d'une voie d'apres les voisines qui la visent. {@code reduce} faux (pose) : la forme ne change que si elle ne
     * garde pas tous les raccords ; vrai (casse) : elle se simplifie aussi quand elle a des bouts dans le vide.
     */
    static boolean settle(LevelAccessor level, BlockPos q, @Nullable Player player, boolean reduce) {
        if (FIXED.test(q)) {
            return false;
        }
        BlockState st = level.getBlockState(q);
        if (!(st.getBlock() instanceof RailTrackBlock tb)) {
            return false;
        }
        TrackShape cur = tb.shape(st);
        if (cur == null) {
            return false;
        }
        if (cur.family() == TrackShape.Family.SLOPE) {
            return reduce && relaxSlope(level, q, cur);
        }
        Set<Direction> linked = aimed(level, q);
        int lm = mask(linked), cm = cur.faceMask();
        if ((cm & lm) == lm && (!reduce || cm == lm || Integer.bitCount(lm) <= 1 && plain(cur))) {
            return false;
        }
        TrackShape next = best(linked, cur, player, q);
        if (next == cur || !tb.allows(next)) {
            return false;
        }
        level.setBlock(q, tb.withShape(st, next).setValue(TrackBlocks.ROUTE, 0), Block.UPDATE_ALL);
        return true;
    }

    /** Meilleure forme pour ces raccords, en gardant si possible le talon de la forme actuelle. */
    private static TrackShape best(Set<Direction> linked, TrackShape cur, @Nullable Player player, BlockPos pos) {
        int n = linked.size();
        if (n >= 4) {
            return cur.isSwitch() ? TrackShape.triple(stemOf(cur)) : TrackShape.CROSSING;
        }
        if (n == 3) {
            for (Direction f : H) {
                if (linked.contains(f) && linked.contains(f.getOpposite())) {
                    Direction branch = null;
                    for (Direction g : linked) {
                        if (g.getAxis() != f.getAxis()) {
                            branch = g;
                        }
                    }
                    Direction stem = cur.isSwitch() && stemOf(cur).getAxis() == f.getAxis() ? stemOf(cur)
                          : stemToward(player, pos, f.getAxis(), player != null ? player.getDirection() : branch);
                    return TrackShape.switchShape(stem, branch);
                }
            }
        }
        if (n == 2) {
            Direction[] two = linked.toArray(new Direction[0]);
            return TrackShape.plainFor(two[0], two[1]);
        }
        if (n == 1) {
            Direction f = linked.iterator().next();
            return plain(cur) && (cur.faceMask() & 1 << f.get2DDataValue()) != 0 ? cur : TrackShape.straight(f.getAxis());
        }
        return plain(cur) ? cur : TrackShape.straight(stemOf(cur).getAxis());
    }

    /** Pente dont le haut (ou, pour la moitie haute, le bas) n'est plus raccorde : elle redevient droite, avec sa moitie jumelle. */
    private static boolean relaxSlope(LevelAccessor level, BlockPos q, TrackShape cur) {
        Piece p = view(level, q);
        Direction f = cur.slopeRising();
        if (p == null || f == null) {
            return false;
        }
        TrackPath path = p.path(0);
        if (connected(level, p, path.b) && (!cur.slopeHigh() || connected(level, p, path.a))) {
            return false;
        }
        set(level, q, TrackShape.straight(f.getAxis()));
        BlockPos twin = cur.slopeHigh() ? q.relative(f.getOpposite()) : q.relative(f);
        BlockState ts = level.getBlockState(twin);
        if (ts.getBlock() instanceof RailTrackBlock tb && tb.shape(ts) == TrackShape.slope(f, !cur.slopeHigh())) {
            set(level, twin, TrackShape.straight(f.getAxis()));
        }
        return true;
    }

    /** Pente douce : L et H droites dans l'axe F, une voie un bloc plus haut au bout de H. */
    private static void ramps(LevelAccessor level, BlockPos x) {
        for (Direction f : H) {
            for (BlockPos h : new BlockPos[]{x, x.relative(f), x.relative(f.getOpposite()).below(), x.relative(f, 2)}) {
                tryRamp(level, h.relative(f.getOpposite()), h, f);
            }
        }
    }

    private static void tryRamp(LevelAccessor level, BlockPos l, BlockPos h, Direction f) {
        if (!rampable(level, l, f, false) || !rampable(level, h, f, true)) {
            return;
        }
        //Une voie au-dessus, au bout de H, qui vise le haut de la pente
        BlockPos top = h.above();
        if (!pointsAt(level, top, f)) {
            return;
        }
        set(level, l, TrackShape.slope(f, false));
        set(level, h, TrackShape.slope(f, true));
    }

    private static boolean rampable(LevelAccessor level, BlockPos pos, Direction f, boolean high) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof RailTrackBlock tb)) {
            return false;
        }
        TrackShape s = tb.shape(st);
        if (s == null) {
            return false;
        }
        return s == TrackShape.straight(f.getAxis());
    }

    private static void set(LevelAccessor level, BlockPos pos, TrackShape shape) {
        if (FIXED.test(pos)) {
            return;
        }
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof TrackBlock tb && tb.allows(shape) && tb.shape(st) != shape) {
            level.setBlock(pos, tb.withShape(st, shape), Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------
    // 0.20.6 : diagonales (variantes de meme raccords choisies d'apres les voisines)
    // ------------------------------------------------------------------

    /** Faces d'une voie a plat (masque N E S W), ou -1 (pas de voie, pente). */
    static int faces(BlockGetter level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof TrackBlock tb)) {
            return -1;
        }
        TrackShape s = tb.shape(st);
        if (s == null || s.family() == TrackShape.Family.SLOPE) {
            return -1;
        }
        return s.faceMask();
    }

    private static int bit(Direction d) {
        return 1 << d.get2DDataValue();
    }

    private static boolean perpendicularPair(int m) {
        return Integer.bitCount(m) == 2 && (m & 5) != 0 && (m & 10) != 0;
    }

    /** Les deux faces d'un masque de deux faces perpendiculaires. */
    private static Direction[] pair(int m) {
        Direction[] out = new Direction[2];
        int i = 0;
        for (Direction d : H) {
            if ((m & bit(d)) != 0 && i < 2) {
                out[i++] = d;
            }
        }
        return out;
    }

    /** Marche d'un escalier diagonal : deux faces en angle, et une voisine par l'une d'elles qui a les deux faces opposees. */
    static boolean diagRun(BlockGetter level, BlockPos pos) {
        int m = faces(level, pos);
        if (!perpendicularPair(m)) {
            return false;
        }
        Direction[] ab = pair(m);
        int comp = bit(ab[0].getOpposite()) | bit(ab[1].getOpposite());
        return faces(level, pos.relative(ab[0])) == comp || faces(level, pos.relative(ab[1])) == comp;
    }

    /** Forme voulue d'apres les voisines, parmi les variantes qui gardent les memes raccords ; null : garder la forme actuelle. */
    @Nullable
    static TrackShape refined(BlockGetter level, BlockPos pos, TrackShape cur) {
        int m = cur.faceMask();
        int n = Integer.bitCount(m);
        if (n == 2 && !perpendicularPair(m)) {
            Direction f = (m & bit(Direction.NORTH)) != 0 ? Direction.NORTH : Direction.EAST;
            TrackShape found = null;
            int count = 0;
            for (Direction d : new Direction[]{f, f.getOpposite()}) {
                BlockPos nb = pos.relative(d);
                int nm = faces(level, nb);
                if (nm < 0) {
                    continue;
                }
                for (Direction c : new Direction[]{d.getClockWise(), d.getCounterClockWise()}) {
                    if (nm == (bit(d.getOpposite()) | bit(c)) && diagRun(level, nb)) {
                        found = shape("EASE_", d.getOpposite(), c);
                        count++;
                    } else if (nm == (bit(d.getOpposite()) | bit(d) | bit(c)) && faces(level, nb.relative(c)) == (bit(c.getOpposite()) | bit(d))
                          && diagRun(level, nb.relative(c))) {
                        found = shape("DSWITCH_", d.getOpposite(), c);
                        count++;
                    }
                }
            }
            if (count == 1) {
                return found;
            }
            return TrackShape.straight(f.getAxis());
        }
        if (n == 2) {
            Direction[] ab = pair(m);
            if (!diagRun(level, pos)) {
                return TrackShape.plainFor(ab[0], ab[1]);
            }
            TrackShape found = null;
            int count = 0;
            for (int i = 0; i < 2; i++) {
                Direction x = ab[i], y = ab[1 - i];
                if (faces(level, pos.relative(x)) == (bit(x) | bit(x.getOpposite()))) {
                    found = shape("EASEB_", x, y);
                    count++;
                }
            }
            return count == 1 ? found : chord(ab[0], ab[1]);
        }
        if (n == 3) {
            //La ligne (deux faces opposees) et le cote c
            Direction f = null, c = null;
            for (Direction d : H) {
                if ((m & bit(d)) != 0 && (m & bit(d.getOpposite())) != 0) {
                    f = d;
                } else if ((m & bit(d)) != 0) {
                    c = d;
                }
            }
            if (f == null || c == null) {
                return null;
            }
            //Deux escaliers de part et d'autre de la ligne qui se croisent au point c : DV
            int l1 = faces(level, pos.relative(f)), l2 = faces(level, pos.relative(f.getOpposite()));
            if (l1 == (bit(f.getOpposite()) | bit(c.getOpposite())) && l2 == (bit(f) | bit(c.getOpposite())) && diagRun(level, pos.relative(f))
                  && diagRun(level, pos.relative(f.getOpposite()))) {
                return TrackShape.valueOf("DV_" + letter(c));
            }
            BlockPos dPos = pos.relative(c);
            int dm = faces(level, dPos);
            for (Direction h : new Direction[]{f, f.getOpposite()}) {
                if (dm != (bit(c.getOpposite()) | bit(h)) || !diagRun(level, dPos)) {
                    continue;
                }
                BlockPos q = pos.relative(h.getOpposite());
                int qm = faces(level, q);
                int line = bit(f) | bit(f.getOpposite());
                if (qm == (line | bit(c.getOpposite())) && diagRun(level, q.relative(c.getOpposite()))) {
                    return sdiag(f.getAxis(), h.getOpposite(), c);
                }
                if (qm >= 0 && (qm & line) == line) {
                    return shape("DMERGE_", h.getOpposite(), c);
                }
            }
            //Plus de contexte diagonal : aiguillage ordinaire (talon gardé si la forme en avait un)
            if (cur.family() == TrackShape.Family.JUNCTION || cur.family() == TrackShape.Family.CROSSING) {
                Direction stem = cur.family() == TrackShape.Family.JUNCTION ? cur.paths().get(0).a.face() : f;
                return TrackShape.switchShape(stem, c);
            }
            return null;
        }
        return null;
    }

    private static String letter(Direction d) {
        return d.getName().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static TrackShape shape(String prefix, Direction entry, Direction turn) {
        return TrackShape.valueOf(prefix + letter(entry) + "_" + letter(turn));
    }

    private static TrackShape chord(Direction a, Direction b) {
        boolean n = a == Direction.NORTH || b == Direction.NORTH, e = a == Direction.EAST || b == Direction.EAST;
        return n ? (e ? TrackShape.DIAG_NE : TrackShape.DIAG_NW) : (e ? TrackShape.DIAG_SE : TrackShape.DIAG_SW);
    }

    private static TrackShape sdiag(Direction.Axis axis, Direction a, Direction b) {
        String c = chord(a, b).name().substring(5);
        return TrackShape.valueOf("SDIAG_" + (axis == Direction.Axis.Z ? "Z_" : "X_") + c);
    }

    /** Variante voulue appliquee a une voie (seulement les voies ordinaires, a plat). */
    static boolean refine(LevelAccessor level, BlockPos pos) {
        if (FIXED.test(pos)) {
            return false;
        }
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof RailTrackBlock tb)) {
            return false;
        }
        TrackShape cur = tb.shape(st);
        if (cur == null || cur.family() == TrackShape.Family.SLOPE || cur.family() == TrackShape.Family.BUFFER) {
            return false;
        }
        //Seules les formes qui ont des variantes diagonales sont concernees
        TrackShape.Family fam = cur.family();
        if (fam == TrackShape.Family.CROSSING && cur == TrackShape.CROSSING) {
            return false;
        }
        if (fam == TrackShape.Family.SWITCH && Integer.bitCount(cur.faceMask()) != 2 && !cur.name().startsWith("SWITCH")) {
            return false;
        }
        TrackShape next = refined(level, pos, cur);
        if (next == null || next == cur || !tb.allows(next) || next.faceMask() != cur.faceMask()) {
            return false;
        }
        BlockState ns = tb.withShape(st, next);
        if (ns.hasProperty(TrackBlocks.ROUTE)) {
            ns = ns.setValue(TrackBlocks.ROUTE, 0);
        }
        level.setBlock(pos, ns, Block.UPDATE_ALL);
        return true;
    }

    /** Variantes diagonales recalculees a 2 blocs autour des voies qui ont change (a plat, meme niveau, et un niveau au-dessus et dessous). */
    public static void refineAround(LevelAccessor level, Set<BlockPos> changed) {
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos c : changed) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) > 3) {
                            continue;
                        }
                        BlockPos q = c.offset(dx, dy, dz);
                        if (seen.add(q)) {
                            refine(level, q);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Cle
    // ------------------------------------------------------------------

    /** Faces ou se tient une voie voisine au meme niveau (meme si elle ne vise pas ce bloc). */
    private static Set<Direction> nearby(BlockGetter level, BlockPos pos) {
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        for (Direction f : H) {
            if (view(level, pos.relative(f)) != null) {
                out.add(f);
            }
        }
        return out;
    }

    /**
     * Formes proposees par la cle : celles qui gardent les raccords et ne vont que vers des voies voisines ; s'il n'y en a qu'une, toutes
     * celles qui ne vont que vers des voisines ; une seule voisine : droite et courbes vers elle ; aucune : droites et courbes.
     */
    public static List<TrackShape> wrenchOptions(BlockGetter level, BlockPos pos) {
        Set<Direction> linked = aimed(level, pos);
        Set<Direction> near = nearby(level, pos);
        near.addAll(linked);
        int lm = mask(linked), nm = mask(near);
        List<TrackShape> out = new ArrayList<>();
        if (near.size() >= 2) {
            for (TrackShape s : CYCLE) {
                int m = s.faceMask();
                if ((m & ~nm) == 0 && (m & lm) == lm) {
                    out.add(s);
                }
            }
            if (out.size() <= 1) {
                out.clear();
                for (TrackShape s : CYCLE) {
                    int m = s.faceMask();
                    if ((m & ~nm) == 0 && Integer.bitCount(m) >= 2) {
                        out.add(s);
                    }
                }
            }
        } else {
            for (TrackShape s : CYCLE) {
                int m = s.faceMask();
                if (Integer.bitCount(m) == 2 && (m & nm) == nm) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    public static void cycle(TrackBlock block, LevelAccessor level, BlockPos pos, BlockState state, boolean back, Player player) {
        TrackShape cur = block.shape(state);
        if (cur == null) {
            return;
        }
        TrackShape next = null;
        if (block instanceof BufferStopBlock) {
            Direction face = cur.paths().get(0).b.face();
            next = TrackShape.buffer(back ? face.getCounterClockWise() : face.getClockWise());
        } else if (block instanceof FunctionalTrackBlock) {
            next = cur == TrackShape.STRAIGHT_NS ? TrackShape.STRAIGHT_EW : TrackShape.STRAIGHT_NS;
        } else if (cur.family() == TrackShape.Family.SLOPE) {
            player.displayClientMessage(Component.translatable("rail.pioneers.shape.slope_auto"), true);
            return;
        } else {
            List<TrackShape> options = wrenchOptions(level, pos);
            if (options.isEmpty() || options.size() == 1 && options.get(0) == cur) {
                player.displayClientMessage(Component.translatable("rail.pioneers.shape.only", shapeName(cur)), true);
                return;
            }
            int i = options.indexOf(cur);
            next = i < 0 ? options.get(0) : options.get((i + (back ? options.size() - 1 : 1)) % options.size());
        }
        if (next != null && next != cur && block.allows(next)) {
            BlockState ns = block.withShape(state, next);
            if (ns.hasProperty(TrackBlocks.ROUTE)) {
                ns = ns.setValue(TrackBlocks.ROUTE, 0);
            }
            level.setBlock(pos, ns, Block.UPDATE_ALL);
            player.displayClientMessage(Component.translatable("rail.pioneers.shape", shapeName(next)), true);
            //Les voisines suivent (une voie isolee se tourne vers la nouvelle forme), sans revenir sur le choix de la cle
            Set<BlockPos> changed = new HashSet<>();
            changed.add(pos);
            propagate(level, pos, player, false, pos, changed);
            refineAround(level, changed);
        }
    }

    private static Component shapeName(TrackShape s) {
        return Component.translatable("rail.pioneers.shape." + s.family().name().toLowerCase(Locale.ROOT));
    }
}
