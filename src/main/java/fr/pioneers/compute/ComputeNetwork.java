package fr.pioneers.compute;

import fr.pioneers.Pioneers;
import fr.pioneers.core.Directions;
import fr.pioneers.transport.logistic.LogisticNetwork;
import fr.pioneers.transport.logistic.LogisticNetworks;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Reseau de calcul de chaque monde (0.17.2, serveur). Chaque seconde : les noeuds inscrits sont regroupes (memes reseaux de cables de
 * donnees, ou consommateur colle a la structure d'un fournisseur), l'offre de chaque groupe est repartie au prorata des demandes (GOPS et
 * QOPS separement), et les fournisseurs apprennent leur charge.
 * <p>
 * Les consommateurs simples (table de recherche, ordinateur) passent par {@link #demand} : une demande renouvelee tant qu'elle est formulee.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ComputeNetwork {

    private ComputeNetwork() {
    }

    /** Ticks entre deux repartitions. */
    public static final int PERIOD = 20;
    /** Un noeud qui ne s'inscrit plus depuis ce delai quitte le reseau. */
    private static final int STALE = 2 * PERIOD + 5;

    private static final class Node {

        IComputeHandler handler;
        long seen;
        int group;
    }

    /** Consommateur simple : demande renouvelee par {@link #demand}. */
    private static final class Simple implements IComputeHandler {

        final BlockPos pos;
        final List<BlockPos> cables = new ArrayList<>();
        double gops, qops, grantG, grantQ;
        long scanned = Long.MIN_VALUE / 2;

        Simple(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public double demandGops() {
            return gops;
        }

        @Override
        public double demandQops() {
            return qops;
        }

        @Override
        public void grant(double g, double q) {
            grantG = g;
            grantQ = q;
        }

        @Override
        public Collection<BlockPos> cables() {
            return cables;
        }
    }

    private static final class State {

        final Map<BlockPos, Node> nodes = new HashMap<>();
        final Map<BlockPos, Simple> simple = new HashMap<>();
        /** Derniere repartition : calcul libre (offre - demandes servies) par groupe, groupe de chaque reseau de cables et boite. */
        final Map<LogisticNetwork, Integer> gridGroup = new IdentityHashMap<>();
        final List<double[]> spare = new ArrayList<>();
        /** 0.20.46 : offre et demande (GOPS, QOPS, GOPS, QOPS) de chaque groupe, pour le terminal de réseau. */
        final List<double[]> sums = new ArrayList<>();
        final List<BlockPos[]> boxes = new ArrayList<>();
        final List<Integer> boxGroup = new ArrayList<>();
        /** Totaux de la derniere repartition (statistiques, progres). */
        double totalGops, totalQops;
    }

    private static final Map<Level, State> LEVELS = new WeakHashMap<>();

    private static State state(Level level) {
        return LEVELS.computeIfAbsent(level, l -> new State());
    }

    // ------------------------------------------------------------------
    // Inscription
    // ------------------------------------------------------------------

    /** Inscrit (ou maintient) un noeud ; a appeler a chaque tick ou le noeud fournit ou demande du calcul. */
    public static void register(Level level, BlockPos pos, IComputeHandler handler) {
        if (level.isClientSide) {
            return;
        }
        State s = state(level);
        Node node = s.nodes.get(pos);
        if (node == null) {
            node = new Node();
            s.nodes.put(pos.immutable(), node);
        }
        node.handler = handler;
        node.seen = level.getGameTime();
    }

    /** Retire un noeud (bloc casse, structure defaite). */
    public static void unregister(Level level, BlockPos pos) {
        //0.20.46 (audit) : etat serveur seulement ; le client (fil de rendu en solo) ne lit ni ne modifie la table du serveur
        if (level.isClientSide) {
            return;
        }
        State s = LEVELS.get(level);
        if (s != null) {
            s.nodes.remove(pos);
            s.simple.remove(pos);
        }
    }

    /**
     * Consommateur simple : demande {@code gops} / {@code qops} au reseau touche par le bloc (cables voisins, structure collee).
     *
     * @return GOPS accordes par la derniere repartition (0 la premiere seconde).
     */
    public static double demand(Level level, BlockPos pos, double gops, double qops) {
        if (level.isClientSide) {
            return 0;
        }
        State s = state(level);
        Simple simple = s.simple.computeIfAbsent(pos.immutable(), Simple::new);
        simple.gops = Math.max(0, gops);
        simple.qops = Math.max(0, qops);
        long now = level.getGameTime();
        if (now - simple.scanned >= PERIOD) {
            simple.scanned = now;
            simple.cables.clear();
            simple.cables.addAll(adjacentCables(level, pos));
        }
        register(level, pos, simple);
        return simple.grantG;
    }

    /** QOPS accordes au consommateur simple de ce bloc par la derniere repartition. */
    public static double grantedQops(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return 0;
        }
        State s = LEVELS.get(level);
        Simple simple = s == null ? null : s.simple.get(pos);
        return simple == null ? 0 : simple.grantQ;
    }

    /** Calcul libre (GOPS, QOPS) du reseau touche par ce bloc, d'apres la derniere repartition. */
    public static double[] available(Level level, BlockPos pos) {
        State s = level.isClientSide ? null : LEVELS.get(level);
        if (s == null) {
            return new double[2];
        }
        int group = groupAt(level, s, pos);
        return group < 0 ? new double[2] : s.spare.get(group).clone();
    }

    /** 0.20.46 : offre GOPS, offre QOPS, demande GOPS, demande QOPS du groupe de calcul touché par ce bloc (dernière répartition). */
    public static double[] stats(Level level, BlockPos pos) {
        State s = level.isClientSide ? null : LEVELS.get(level);
        if (s == null) {
            return new double[4];
        }
        int group = groupAt(level, s, pos);
        if (group < 0) {
            //Bloc pris dans la fibre elle-même (cable) : groupe de sa grille
            LogisticNetwork grid = LogisticNetworks.digital(level, pos);
            Integer g = grid == null ? null : s.gridGroup.get(grid);
            group = g == null ? -1 : g;
        }
        return group < 0 || group >= s.sums.size() ? new double[4] : s.sums.get(group).clone();
    }

    /** Calcul total servi dans ce monde a la derniere repartition (GOPS). */
    public static double totalGops(Level level) {
        State s = level.isClientSide ? null : LEVELS.get(level);
        return s == null ? 0 : s.totalGops;
    }

    private static int groupAt(Level level, State s, BlockPos pos) {
        for (BlockPos cable : adjacentCables(level, pos)) {
            LogisticNetwork grid = LogisticNetworks.digital(level, cable);
            Integer g = grid == null ? null : s.gridGroup.get(grid);
            if (g != null) {
                return g;
            }
        }
        for (int i = 0; i < s.boxes.size(); i++) {
            if (touches(s.boxes.get(i)[0], s.boxes.get(i)[1], pos)) {
                return s.boxGroup.get(i);
            }
        }
        return -1;
    }

    /** Cables de donnees voisins d'un bloc. */
    public static List<BlockPos> adjacentCables(Level level, BlockPos pos) {
        List<BlockPos> out = new ArrayList<>(2);
        for (Direction d : Directions.ALL) {
            BlockPos n = pos.relative(d);
            if (level.isLoaded(n) && LogisticNetworks.isDataCable(level.getBlockState(n))) {
                out.add(n.immutable());
            }
        }
        return out;
    }

    /** Le bloc est-il dans la boite ou colle a une de ses faces ? */
    static boolean touches(BlockPos min, BlockPos max, BlockPos p) {
        return p.getX() >= min.getX() - 1 && p.getX() <= max.getX() + 1 && p.getY() >= min.getY() - 1 && p.getY() <= max.getY() + 1
               && p.getZ() >= min.getZ() - 1 && p.getZ() <= max.getZ() + 1;
    }

    // ------------------------------------------------------------------
    // Repartition
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.getGameTime() % PERIOD != 0) {
            return;
        }
        State s = LEVELS.get(level);
        if (s != null) {
            distribute(level, s);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide) {
            LEVELS.remove(level);
        }
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) {
            parent[ra] = rb;
        }
    }

    private static void distribute(ServerLevel level, State s) {
        long now = level.getGameTime();
        for (Iterator<Map.Entry<BlockPos, Node>> it = s.nodes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Node> e = it.next();
            if (now - e.getValue().seen > STALE) {
                it.remove();
                s.simple.remove(e.getKey());
            }
        }
        s.gridGroup.clear();
        s.spare.clear();
        s.sums.clear();
        s.boxes.clear();
        s.boxGroup.clear();
        s.totalGops = s.totalQops = 0;
        List<BlockPos> positions = new ArrayList<>(s.nodes.keySet());
        int n = positions.size();
        if (n == 0) {
            return;
        }
        Node[] nodes = new Node[n];
        int[] parent = new int[n];
        Map<LogisticNetwork, Integer> firstOnGrid = new IdentityHashMap<>();
        for (int i = 0; i < n; i++) {
            nodes[i] = s.nodes.get(positions.get(i));
            parent[i] = i;
            for (BlockPos cable : nodes[i].handler.cables()) {
                LogisticNetwork grid = LogisticNetworks.digital(level, cable);
                if (grid != null) {
                    Integer first = firstOnGrid.putIfAbsent(grid, i);
                    if (first != null) {
                        union(parent, i, first);
                    }
                }
            }
        }
        //Contact direct : un noeud colle a la structure d'un fournisseur
        for (int p = 0; p < n; p++) {
            BlockPos min = nodes[p].handler.boxMin(), max = nodes[p].handler.boxMax();
            if (min == null || max == null) {
                continue;
            }
            for (int i = 0; i < n; i++) {
                if (i != p && nodes[i].handler.boxMin() == null && touches(min, max, positions.get(i))) {
                    union(parent, i, p);
                }
            }
        }
        //Sommes par groupe : offre et demande
        Map<Integer, Integer> index = new HashMap<>();
        List<double[]> sums = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            Integer g = index.get(root);
            if (g == null) {
                g = sums.size();
                index.put(root, g);
                sums.add(new double[4]);
            }
            nodes[i].group = g;
            IComputeHandler h = nodes[i].handler;
            double[] sum = sums.get(g);
            sum[0] += Math.max(0, h.supplyGops());
            sum[1] += Math.max(0, h.supplyQops());
            sum[2] += Math.max(0, h.demandGops());
            sum[3] += Math.max(0, h.demandQops());
        }
        for (int i = 0; i < n; i++) {
            IComputeHandler h = nodes[i].handler;
            double[] sum = sums.get(nodes[i].group);
            //Consommateur : part de l'offre au prorata ; fournisseur : charge au prorata de sa capacite
            double rg = sum[2] <= 0 ? 0 : Math.min(1, sum[0] / sum[2]);
            double rq = sum[3] <= 0 ? 0 : Math.min(1, sum[1] / sum[3]);
            h.grant(h.demandGops() * rg, h.demandQops() * rq);
            double lg = sum[0] <= 0 ? 0 : Math.min(1, sum[2] / sum[0]);
            double lq = sum[1] <= 0 ? 0 : Math.min(1, sum[3] / sum[1]);
            h.serve(h.supplyGops() * lg, h.supplyQops() * lq);
        }
        for (double[] sum : sums) {
            s.sums.add(sum.clone());
            s.spare.add(new double[]{Math.max(0, sum[0] - sum[2]), Math.max(0, sum[1] - sum[3])});
            s.totalGops += Math.min(sum[0], sum[2]);
            s.totalQops += Math.min(sum[1], sum[3]);
        }
        for (Map.Entry<LogisticNetwork, Integer> e : firstOnGrid.entrySet()) {
            s.gridGroup.put(e.getKey(), nodes[e.getValue()].group);
        }
        for (int i = 0; i < n; i++) {
            BlockPos min = nodes[i].handler.boxMin(), max = nodes[i].handler.boxMax();
            if (min != null && max != null) {
                s.boxes.add(new BlockPos[]{min, max});
                s.boxGroup.add(nodes[i].group);
            }
        }
    }
}
