package fr.pioneers.logistics.robotics;

import fr.pioneers.core.Directions;
import fr.pioneers.core.PiTags;
import fr.pioneers.logistics.robotics.logistics.BlockRoboticRoad;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Pathfinding des unites robotiques, au bloc pres, rapide et sans dependre de celui des monstres :
 * <ul>
 *     <li><b>au sol</b> : A* sur les cases ou l'unite tient debout (sa hauteur comptee), huit directions sans couper les angles, une
 *     marche a monter, jusqu'a trois a descendre, portillons et portes en bois ouvrables, eau a la nage (cher), lave, feu, cactus et
 *     baies evites, barrieres et murets jamais enjambes ; les <b>routes robotiques coutent moins</b> (les unites y vont plus vite), donc
 *     elles sont naturellement preferees ;</li>
 *     <li><b>en vol</b> : ligne droite si elle est libre, sinon par-dessus le relief, sinon A* en trois dimensions (grottes, batiments,
 *     enclos couverts), puis lissage des virages ;</li>
 *     <li>le but est atteint des qu'on est a <b>portee d'action</b> (on ne cherche plus a marcher sur le bloc a casser ou sur l'animal).</li>
 * </ul>
 * Budget de noeuds borne : au-dela, un chemin partiel vers le point le plus prometteur (recalcule en route). Fonctionne sur n'importe quel
 * monde (serveur, ou client pour le trace des routes du mode Travaux).
 */
public final class RobotPathfinder {

    private RobotPathfinder() {
    }

    /** Un chemin : points a suivre (centres des cases, au niveau des pieds) et s'il mene jusqu'au but. */
    public record Route(List<Vec3> points, boolean complete) {

        public boolean isEmpty() {
            return points.isEmpty();
        }
    }

    /** Cout d'une case de route (la route est 1,5 fois plus rapide). */
    public static final double ROAD_COST = 0.62;
    /** Calculs de chemin par tick serveur (au-dela, on attend le tick suivant : jamais de pic de lag). */
    private static final int PER_TICK = 8;
    /**
     * 0.20.46 (audit) : noeuds explores par tick serveur, tous calculs confondus. Un calcul ne commence que s'il en reste ; il va
     * jusqu'au bout de son propre budget (meme resultat qu'avant) et ce qu'il a coute est decompte ensuite. Les suivants attendent le
     * tick d'apres. Pire cas : ~12 000 + 9 000 noeuds par tick (8 x 9 000 avant).
     */
    private static final int NODES_PER_TICK = 12_000;
    private static long permitTick = Long.MIN_VALUE;
    private static int permits, nodes;
    /** Dernier tick ou chaque unite a eu le droit de calculer (equite : voir {@link #permit(Level, Object)}). */
    private static final Map<Object, Long> SERVED = new WeakHashMap<>();
    /** Unites refusees faute de budget, avec le tick du refus (oubliees une fois servies, ou au bout de {@link #WAIT_MEMORY} ticks). */
    private static final Map<Object, Long> WAITING = new WeakHashMap<>();
    /** Une unite servie il y a moins de {@value} ticks laisse passer celles qui attendent. */
    private static final int FAIR_TICKS = 20;
    private static final int WAIT_MEMORY = 200;

    /**
     * Autorise un calcul de chemin ce tick-ci pour {@code who} ? Equite (relecture de l'audit) : les unites sont toujours visitees dans le
     * meme ordre ; sans cela, les premieres prendraient tout le budget et les dernieres attendraient sans fin. Tant qu'une unite a ete
     * refusee et n'a pas encore ete servie, celles servies dans les {@link #FAIR_TICKS} derniers ticks lui laissent la place.
     */
    public static boolean permit(Level level, Object who) {
        long now = level.getGameTime();
        if (now != permitTick) {
            permitTick = now;
            permits = 0;
            nodes = 0;
        }
        Long served = SERVED.get(who);
        boolean recent = served != null && now - served < FAIR_TICKS;
        if (recent && othersWaiting(who, now)) {
            return false;
        }
        if (nodes < NODES_PER_TICK && permits < PER_TICK) {
            permits++;
            SERVED.put(who, now);
            WAITING.remove(who);
            return true;
        }
        if (!recent) {
            WAITING.putIfAbsent(who, now);
        }
        return false;
    }

    private static boolean othersWaiting(Object who, long now) {
        WAITING.values().removeIf(at -> now - at > WAIT_MEMORY);
        return !WAITING.isEmpty() && !(WAITING.size() == 1 && WAITING.containsKey(who));
    }

    /** Decompte les noeuds d'un calcul fait par le serveur (le client trace aussi des routes : rien a compter, autre fil). */
    private static void charge(Level level, int visited) {
        if (!level.isClientSide && level.getGameTime() == permitTick) {
            nodes += visited;
        }
    }

    private static final byte UNKNOWN = 0, FREE = 1, BLOCKED = 2, DANGER = 3, OPENABLE = 4, WATER = 5, THIN = 6;

    // ------------------------------------------------------------------
    // Au sol
    // ------------------------------------------------------------------

    /**
     * Chemin au sol de {@code from} jusqu'a portee {@code reach} de {@code goal}.
     *
     * @param height hauteur de l'unite en blocs (1 ou 2)
     * @param budget noeuds au plus
     * @return le chemin (partiel si le budget est epuise ou le but inaccessible mais qu'on s'en approche), ou null s'il n'y a rien
     */
    @Nullable
    public static Route ground(Level level, Vec3 from, Vec3 goal, double reach, int height, int budget, boolean roads) {
        int[] visited = new int[1];
        Route route = ground(level, from, goal, reach, height, budget, roads, visited);
        charge(level, visited[0]);
        return route;
    }

    @Nullable
    private static Route ground(Level level, Vec3 from, Vec3 goal, double reach, int height, int budget, boolean roads, int[] visited) {
        Grid grid = new Grid(level, height);
        BlockPos start = grid.findStart(BlockPos.containing(from));
        if (start == null) {
            return null;
        }
        double reach2 = reach * reach;
        double minCost = roads ? ROAD_COST : 1;
        //On ne s'arrete jamais sur le bloc vise (il va disparaitre sous ses pieds)
        long work = BlockPos.containing(goal).asLong();
        //Distances mesurees depuis les pieds de l'unite (bas de la case), comme l'arrivee de l'unite : decaler le but d'un demi-bloc revient
        //a comparer le centre des cases
        goal = goal.add(0, 0.5, 0);
        Long2DoubleOpenHashMap cost = new Long2DoubleOpenHashMap();
        cost.defaultReturnValue(Double.MAX_VALUE);
        Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        long startKey = start.asLong();
        cost.put(startKey, 0);
        open.add(new double[]{heuristic(start, goal, reach) * minCost, startKey});
        long best = startKey;
        double bestH = heuristic(start, goal, reach);
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        while (!open.isEmpty() && visited[0]++ < budget) {
            double[] top = open.poll();
            long key = (long) top[1];
            BlockPos pos = BlockPos.of(key);
            double here = cost.get(key);
            if (top[0] > here + heuristic(pos, goal, reach) * minCost + 1e-6) {
                continue;
            }
            if (distSqr(pos, goal) <= reach2 && BlockPos.asLong(pos.getX(), pos.getY() - 1, pos.getZ()) != work && key != work
                && BlockPos.asLong(pos.getX(), pos.getY() + 1, pos.getZ()) != work) {
                return new Route(build(parent, key, startKey), true);
            }
            double h = heuristic(pos, goal, reach);
            if (h < bestH) {
                bestH = h;
                best = key;
            }
            if (Math.abs(pos.getX() - start.getX()) > 160 || Math.abs(pos.getZ() - start.getZ()) > 160) {
                continue;
            }
            for (int d = 0; d < 8; d++) {
                int dx = DX[d], dz = DZ[d];
                boolean diagonal = dx != 0 && dz != 0;
                if (diagonal && (!grid.body(pos.getX() + dx, pos.getY(), pos.getZ()) || !grid.body(pos.getX(), pos.getY(), pos.getZ() + dz))) {
                    continue;
                }
                //Meme niveau, marche a monter, ou descente (diagonales : seulement a plat)
                int found = Integer.MIN_VALUE;
                double extra = 0;
                int x = pos.getX() + dx, z = pos.getZ() + dz, y = pos.getY();
                if (grid.stand(x, y, z)) {
                    found = y;
                } else if (!diagonal && grid.stand(x, y + 1, z) && grid.free(pos.getX(), y + height, pos.getZ())) {
                    found = y + 1;
                    extra = 0.6;
                } else if (!diagonal && grid.body(x, y, z)) {
                    for (int drop = 1; drop <= 3; drop++) {
                        if (grid.stand(x, y - drop, z)) {
                            found = y - drop;
                            extra = 0.4 * drop;
                            break;
                        }
                        if (!grid.free(x, y - drop, z)) {
                            break;
                        }
                    }
                }
                if (found == Integer.MIN_VALUE) {
                    continue;
                }
                next.set(x, found, z);
                double step = (diagonal ? 1.4142 : 1) * grid.moveCost(x, found, z, roads) + extra;
                long nextKey = next.asLong();
                double total = here + step;
                if (total < cost.get(nextKey)) {
                    cost.put(nextKey, total);
                    parent.put(nextKey, key);
                    open.add(new double[]{total + heuristic(next, goal, reach) * minCost, nextKey});
                }
            }
        }
        //Pas de chemin complet : vers le point le plus prometteur, s'il fait vraiment avancer
        if (best != startKey && heuristic(start, goal, reach) - bestH > 1.5) {
            return new Route(build(parent, best, startKey), false);
        }
        return null;
    }

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1}, DZ = {0, 0, 1, -1, 1, -1, 1, -1};

    /** 0.20.46 (audit) : distance au carre du centre de la case au but, sans allouer de Vec3 (appele plusieurs fois par noeud). */
    private static double distSqr(BlockPos pos, Vec3 goal) {
        double dx = goal.x - (pos.getX() + 0.5), dy = goal.y - (pos.getY() + 0.5), dz = goal.z - (pos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double heuristic(BlockPos pos, Vec3 goal, double reach) {
        return Math.max(0, Math.sqrt(distSqr(pos, goal)) - reach);
    }

    private static List<Vec3> build(Long2LongOpenHashMap parent, long end, long start) {
        List<Vec3> points = new ArrayList<>();
        long key = end;
        while (true) {
            BlockPos pos = BlockPos.of(key);
            points.add(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
            if (key == start || !parent.containsKey(key)) {
                break;
            }
            key = parent.get(key);
        }
        Collections.reverse(points);
        return points;
    }

    /** Lecture des cases (mise en cache le temps d'un calcul). */
    private static final class Grid {

        private final Level level;
        private final int height;
        private final Long2ByteOpenHashMap cache = new Long2ByteOpenHashMap();
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        Grid(Level level, int height) {
            this.level = level;
            this.height = Math.max(1, height);
        }

        byte type(int x, int y, int z) {
            long key = BlockPos.asLong(x, y, z);
            byte type = cache.get(key);
            if (type == UNKNOWN) {
                type = classify(x, y, z);
                cache.put(key, type);
            }
            return type;
        }

        private byte classify(int x, int y, int z) {
            cursor.set(x, y, z);
            if (!level.isLoaded(cursor) || y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                return BLOCKED;
            }
            BlockState state = level.getBlockState(cursor);
            FluidState fluid = state.getFluidState();
            if (fluid.is(FluidTags.LAVA) || state.is(PiTags.Blocks.ROBOT_HAZARDS)) {
                return DANGER;
            }
            if (state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand()) {
                return state.hasProperty(BlockStateProperties.OPEN)
                       && state.getValue(BlockStateProperties.OPEN) ? FREE : OPENABLE;
            }
            VoxelShape shape = state.getCollisionShape(level, cursor);
            if (shape.isEmpty()) {
                return !fluid.isEmpty() ? WATER : FREE;
            }
            //Tapis, neige fine, plaques : on marche dessus comme sur le sol
            return shape.max(Direction.Axis.Y) <= 0.2 ? THIN : BLOCKED;
        }

        /** Le corps passe-t-il dans cette case (sans s'y tenir) ? */
        boolean free(int x, int y, int z) {
            byte type = type(x, y, z);
            return type == FREE || type == OPENABLE || type == WATER || type == THIN;
        }

        /** Toute la hauteur de l'unite passe-t-elle a partir de cette case ? */
        boolean body(int x, int y, int z) {
            for (int i = 0; i < height; i++) {
                if (!free(x, y + i, z)) {
                    return false;
                }
            }
            return true;
        }

        /** Peut-on se tenir debout ici (pieds dans la case) ? */
        boolean stand(int x, int y, int z) {
            if (!body(x, y, z)) {
                return false;
            }
            byte feet = type(x, y, z);
            if (feet == THIN) {
                return true;
            }
            if (feet == WATER) {
                //Dans l'eau : seulement en surface (la tete hors de l'eau), jamais au fond d'un lac
                return type(x, y + 1, z) != WATER;
            }
            cursor.set(x, y - 1, z);
            byte below = type(x, y - 1, z);
            if (below == WATER) {
                //Nage en surface
                return true;
            }
            if (below != BLOCKED) {
                return false;
            }
            BlockState state = level.getBlockState(cursor);
            //Jamais sur une barriere, un muret ou un portillon (plus hauts qu'un bloc)
            if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCE_GATES)) {
                return false;
            }
            return state.getCollisionShape(level, cursor).max(Direction.Axis.Y) <= 1.0;
        }

        /** Cout d'entree dans une case : route moins chere, eau et portes plus cheres. */
        double moveCost(int x, int y, int z, boolean roads) {
            byte feet = type(x, y, z);
            if (feet == WATER) {
                //L'eau est evitee autant que possible : un long detour vaut mieux qu'une traversee
                return 14;
            }
            cursor.set(x, y - 1, z);
            if (type(x, y - 1, z) == WATER) {
                return 10;
            }
            double cost = feet == OPENABLE ? 2.5 : 1;
            if (roads) {
                cursor.set(x, y - 1, z);
                Block below = level.getBlockState(cursor).getBlock();
                if (below instanceof BlockRoboticRoad) {
                    cost = Math.min(cost, ROAD_COST);
                } else if (below == Blocks.DIRT_PATH) {
                    cost = Math.min(cost, 0.8);
                }
            }
            return cost;
        }

        /** Case de depart : la case de l'unite, ou une voisine ou elle tient (posee sur un bord, a cheval sur une dalle...). */
        @Nullable
        BlockPos findStart(BlockPos pos) {
            if (stand(pos.getX(), pos.getY(), pos.getZ())) {
                return pos;
            }
            for (int dy : new int[]{1, -1, -2, -3}) {
                if (stand(pos.getX(), pos.getY() + dy, pos.getZ())) {
                    return pos.offset(0, dy, 0);
                }
            }
            for (int r = 1; r <= 2; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        for (int dy = 1; dy >= -2; dy--) {
                            if (stand(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz)) {
                                return pos.offset(dx, dy, dz);
                            }
                        }
                    }
                }
            }
            return null;
        }

        /** Vol : la case est-elle libre pour un corps de cette hauteur ? */
        boolean air(int x, int y, int z) {
            for (int i = 0; i < height; i++) {
                byte type = type(x, y + i, z);
                if (type != FREE && type != WATER && type != OPENABLE) {
                    return false;
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------
    // En vol
    // ------------------------------------------------------------------

    /**
     * Chemin en vol jusqu'a portee de {@code goal} : ligne droite libre, sinon par-dessus le relief (monter, filer, descendre), sinon A*
     * en 3D (au plus {@code budget} noeuds). Null si rien ne marche.
     */
    @Nullable
    public static Route air(Level level, Vec3 from, Vec3 goal, double reach, double width, double heightBlocks, int budget) {
        int[] visited = new int[1];
        Route route = air(level, from, goal, reach, width, heightBlocks, budget, visited);
        charge(level, visited[0]);
        return route;
    }

    @Nullable
    private static Route air(Level level, Vec3 from, Vec3 goal, double reach, double width, double heightBlocks, int budget, int[] visited) {
        //But dans un bloc (bloc a miner...) : on vise le premier point libre au-dessus, a portee
        Vec3 target = goal;
        for (double up = 0; up <= reach; up += 0.5) {
            Vec3 p = goal.add(0, up, 0);
            if (clear(level, p, p, width, heightBlocks)) {
                target = p;
                break;
            }
        }
        if (clear(level, from, target, width, heightBlocks)) {
            return new Route(List.of(target), true);
        }
        //Par-dessus : monter a l'altitude de croisiere, filer, descendre a la verticale du but
        double cruise = cruise(level, from, target);
        Vec3 up = new Vec3(from.x, cruise, from.z), over = new Vec3(target.x, cruise, target.z);
        if (clear(level, from, up, width, heightBlocks) && clear(level, up, over, width, heightBlocks) && clear(level, over, target, width, heightBlocks)) {
            return new Route(List.of(up, over, target), true);
        }
        //A* en 3D (sous un toit, dans une grotte, un batiment...)
        int height = (int) Math.ceil(heightBlocks);
        Grid grid = new Grid(level, height);
        BlockPos start = BlockPos.containing(from);
        if (!grid.air(start.getX(), start.getY(), start.getZ())) {
            BlockPos found = null;
            for (Direction side : Directions.ALL) {
                if (grid.air(start.getX() + side.getStepX(), start.getY() + side.getStepY(), start.getZ() + side.getStepZ())) {
                    found = start.relative(side);
                    break;
                }
            }
            if (found == null) {
                return null;
            }
            start = found;
        }
        double reach2 = reach * reach;
        Long2DoubleOpenHashMap cost = new Long2DoubleOpenHashMap();
        cost.defaultReturnValue(Double.MAX_VALUE);
        Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        long startKey = start.asLong();
        cost.put(startKey, 0);
        open.add(new double[]{heuristic(start, goal, reach), startKey});
        long best = startKey;
        double bestH = heuristic(start, goal, reach);
        int minY = (int) Math.min(from.y, goal.y) - 12, maxY = (int) Math.max(from.y, goal.y) + 24;
        while (!open.isEmpty() && visited[0]++ < budget) {
            double[] top = open.poll();
            long key = (long) top[1];
            BlockPos pos = BlockPos.of(key);
            double here = cost.get(key);
            if (distSqr(pos, goal) <= reach2) {
                return new Route(smooth(level, from, build(parent, key, startKey), width, heightBlocks), true);
            }
            double h = heuristic(pos, goal, reach);
            if (h < bestH) {
                bestH = h;
                best = key;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        int x = pos.getX() + dx, y = pos.getY() + dy, z = pos.getZ() + dz;
                        if (y < minY || y > maxY || !grid.air(x, y, z)) {
                            continue;
                        }
                        //Pas de coupe d'angle : les cases traversees doivent etre libres
                        if ((dx != 0 && !grid.air(pos.getX() + dx, pos.getY(), pos.getZ())) || (dy != 0 && !grid.air(pos.getX(), pos.getY() + dy, pos.getZ()))
                            || (dz != 0 && !grid.air(pos.getX(), pos.getY(), pos.getZ() + dz))) {
                            continue;
                        }
                        long nextKey = BlockPos.asLong(x, y, z);
                        double total = here + Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (total < cost.get(nextKey)) {
                            cost.put(nextKey, total);
                            parent.put(nextKey, key);
                            open.add(new double[]{total + heuristic(BlockPos.of(nextKey), goal, reach), nextKey});
                        }
                    }
                }
            }
        }
        if (best != startKey && heuristic(start, goal, reach) - bestH > 1.5) {
            return new Route(smooth(level, from, build(parent, best, startKey), width, heightBlocks), false);
        }
        return null;
    }

    /** Le corps passe-t-il en ligne droite d'un point a l'autre ? */
    public static boolean clear(Level level, Vec3 from, Vec3 to, double width, double height) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        int steps = Math.max(1, (int) Math.ceil(length / 0.45));
        double half = width / 2;
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(delta.scale(i / (double) steps));
            if (!level.isLoaded(BlockPos.containing(p))) {
                return false;
            }
            if (!level.noCollision(new AABB(p.x - half, p.y, p.z - half, p.x + half, p.y + height, p.z + half).deflate(0.02))) {
                return false;
            }
        }
        return true;
    }

    /** Altitude de croisiere : au-dessus du plus haut relief sous la trajectoire. */
    private static double cruise(Level level, Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        int samples = (int) Math.min(64, Math.max(2, length / 1.5));
        int top = (int) Math.max(from.y, to.y);
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            int x = (int) Math.floor(from.x + dx * t), z = (int) Math.floor(from.z + dz * t);
            if (level.hasChunk(x >> 4, z >> 4)) {
                top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
            }
        }
        return top + 2.5;
    }

    /** Lissage : on saute les points intermediaires tant que la ligne droite reste libre. */
    private static List<Vec3> smooth(Level level, Vec3 from, List<Vec3> raw, double width, double height) {
        List<Vec3> points = new ArrayList<>();
        Vec3 anchor = from;
        int i = 0;
        //Les cases de l'A* sont des coins de blocs ; on vise leur centre en hauteur
        List<Vec3> centers = new ArrayList<>();
        for (Vec3 p : raw) {
            centers.add(p.add(0, 0.05, 0));
        }
        while (i < centers.size()) {
            int far = i;
            for (int j = centers.size() - 1; j > i; j--) {
                if (clear(level, anchor, centers.get(j), width, height)) {
                    far = j;
                    break;
                }
            }
            anchor = centers.get(far);
            points.add(anchor);
            i = far + 1;
        }
        return points;
    }
}
