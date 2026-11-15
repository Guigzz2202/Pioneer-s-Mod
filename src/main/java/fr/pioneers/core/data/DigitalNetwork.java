package fr.pioneers.core.data;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.tech.compute.ComputeNetwork;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Directions;
import fr.pioneers.tech.industry.multi.PortTile;
import fr.pioneers.tech.industry.multi.Structures;
import fr.pioneers.space.tile.TileEntityDataBank;
import fr.pioneers.logistics.transport.logistic.LogisticNetwork;
import fr.pioneers.logistics.transport.logistic.LogisticNetworks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.46 « Le réseau numérique » (demande de Guillaume : unifier la data spatiale et l'informatique industrielle, rendre clair ce qui
 * est faisable). Un réseau de câble logistique MKI ou mieux (1.11 ; avant : une fibre optique) = UN réseau numérique, qui porte trois services :
 * <ul>
 *     <li><b>données</b> (Mo, une matière : produites, transportées, stockées dans les banques) ;</li>
 *     <li><b>calcul</b> (GOPS / QOPS, une puissance PARTAGÉE comme l'électricité : jamais stockée ni transportée, simplement disponible
 *     à tout ce qui est branché, voir {@link ComputeNetwork}) ;</li>
 *     <li><b>supervision</b> : toute machine qui touche la fibre est visible des automates, écrans, ordinateurs et terminaux du même
 *     réseau, à n'importe quelle distance.</li>
 * </ul>
 * Cette classe dresse l'annuaire d'un réseau (machines branchées, banques, stock par type) et sert le « stock du réseau » : une machine
 * qui consomme des données (laboratoire, laboratoire d'IA, synthétiseur) puise elle-même dans les banques du réseau.
 */
public final class DigitalNetwork {

    private DigitalNetwork() {
    }

    /** Annuaire d'un réseau (une ou plusieurs grilles touchées par le bloc). */
    public static final class Snapshot {

        /** Machines branchées (contrôleur pour un multibloc), dans l'ordre de découverte. */
        public final List<BlockPos> machines = new ArrayList<>();
        /** Banques de données branchées. */
        public final List<BlockPos> banks = new ArrayList<>();
        /** Stock par type (banques ; 1.11 : le cable logistique n'a plus de contenu en transit). */
        public final Map<DataType, Long> stock = new LinkedHashMap<>();
        public long bankCapacity, cables;
        /** Calcul : offre et demande (GOPS, QOPS) du groupe de calcul. */
        public double supplyGops, supplyQops, demandGops, demandQops;
        public int grids;

        public boolean connected() {
            return grids > 0;
        }

        public long stored(DataType type) {
            return stock.getOrDefault(type, 0L);
        }

        public long totalStored() {
            long t = 0;
            for (long v : stock.values()) {
                t += v;
            }
            return t;
        }
    }

    private record Cached(Snapshot snapshot, long at) {
    }

    private static final Map<Level, Map<LogisticNetwork, Cached>> CACHE = new WeakHashMap<>();
    private static final int CACHE_TICKS = 20;

    /** 1.11 : reseaux logistiques MKI ou mieux (ils portent les donnees) qui touchent ce bloc. */
    public static List<LogisticNetwork> gridsAt(Level level, BlockPos pos) {
        List<LogisticNetwork> out = new ArrayList<>(2);
        for (Direction d : Directions.ALL) {
            BlockPos n = pos.relative(d);
            if (level.isLoaded(n) && LogisticNetworks.isDataCable(level.getBlockState(n))) {
                LogisticNetwork grid = LogisticNetworks.digital(level, n);
                if (grid != null && !out.contains(grid)) {
                    out.add(grid);
                }
            }
        }
        return out;
    }

    /** Le bloc touche-t-il une fibre ? */
    public static boolean connected(Level level, BlockPos pos) {
        return !gridsAt(level, pos).isEmpty();
    }

    /** Annuaire du réseau touché par le bloc (mis en cache une seconde par grille). */
    public static Snapshot scan(Level level, BlockPos pos) {
        List<LogisticNetwork> grids = gridsAt(level, pos);
        if (grids.isEmpty()) {
            return new Snapshot();
        }
        if (grids.size() == 1) {
            return scanGrid(level, grids.get(0), pos);
        }
        Snapshot merged = new Snapshot();
        Set<BlockPos> seen = new LinkedHashSet<>();
        for (LogisticNetwork grid : grids) {
            Snapshot s = scanGrid(level, grid, pos);
            for (BlockPos m : s.machines) {
                if (seen.add(m)) {
                    merged.machines.add(m);
                }
            }
            for (BlockPos b : s.banks) {
                if (!merged.banks.contains(b)) {
                    merged.banks.add(b);
                }
            }
            s.stock.forEach((t, v) -> merged.stock.merge(t, v, Long::sum));
            merged.bankCapacity += s.bankCapacity;
            merged.cables += s.cables;
        }
        merged.grids = grids.size();
        compute(level, pos, merged);
        return merged;
    }

    private static Snapshot scanGrid(Level level, LogisticNetwork grid, BlockPos from) {
        Map<LogisticNetwork, Cached> cache;
        synchronized (CACHE) {
            cache = CACHE.computeIfAbsent(level, l -> new IdentityHashMap<>());
        }
        long now = level.getGameTime();
        Cached c = cache.get(grid);
        if (c != null && now - c.at < CACHE_TICKS) {
            return c.snapshot;
        }
        Snapshot s = new Snapshot();
        s.grids = 1;
        s.cables = grid.graph.size;
        Set<BlockPos> machines = new LinkedHashSet<>();
        //Raccords du reseau logistique : blocs touches par une fiche (un port est ramene a son controleur)
        for (LogisticNetwork.Attach attach : grid.attaches()) {
            BlockPos n = BlockPos.of(attach.key);
            //1.0.5-beta (lot 45, retour 63) : une machine deconnectee (« Rien » partout) n'est plus sur le reseau numerique
            if (attach.detached() || !level.isLoaded(n)) {
                continue;
            }
            BlockEntity tile = level.getBlockEntity(n);
            if (tile == null) {
                continue;
            }
            BlockPos at = n.immutable();
            if (tile instanceof PortTile) {
                BlockPos ctrl = Structures.controllerAt(level, n);
                if (ctrl != null) {
                    at = ctrl.immutable();
                }
            }
            if (machines.add(at) && level.getBlockEntity(at) instanceof TileEntityDataBank bank) {
                s.banks.add(at);
                s.bankCapacity += bank.getTank().getCapacity();
                DataStack st = bank.getTank().getStack();
                if (!st.isEmpty()) {
                    s.stock.merge(st.getType(), st.getAmount(), Long::sum);
                }
            }
        }
        s.machines.addAll(machines);
        compute(level, grid.graph.size > 0 ? BlockPos.of(grid.graph.cables[0]) : from, s);
        cache.put(grid, new Cached(s, now));
        return s;
    }

    private static void compute(Level level, BlockPos pos, Snapshot s) {
        double[] stats = ComputeNetwork.stats(level, pos);
        s.supplyGops = stats[0];
        s.supplyQops = stats[1];
        s.demandGops = stats[2];
        s.demandQops = stats[3];
    }

    /** Les deux blocs sont-ils sur le même réseau numérique ? (supervision sans limite de distance) */
    public static boolean sameNetwork(Level level, BlockPos a, BlockPos b) {
        List<LogisticNetwork> grids = gridsAt(level, a);
        if (grids.isEmpty()) {
            return false;
        }
        Snapshot s = scan(level, a);
        BlockPos target = b;
        BlockPos ctrl = Structures.controllerAt(level, b);
        if (ctrl != null) {
            target = ctrl;
        }
        return s.machines.contains(target) || s.machines.contains(b);
    }

    /**
     * Stock du réseau : retire jusqu'à {@code amount} Mo de {@code type} des banques du réseau touché par {@code pos}.
     *
     * @return la quantité retirée.
     */
    public static long pull(Level level, BlockPos pos, DataType type, long amount, Action action) {
        if (amount <= 0 || type.isEmptyType()) {
            return 0;
        }
        long got = 0;
        for (BlockPos b : scan(level, pos).banks) {
            if (got >= amount) {
                break;
            }
            if (!(level.getBlockEntity(b) instanceof TileEntityDataBank bank)) {
                continue;
            }
            DataStack st = bank.getTank().getStack();
            if (st.isEmpty() || !st.isTypeEqual(type)) {
                continue;
            }
            DataStack out = bank.getTank().extract(amount - got, action, Automation.INTERNAL);
            got += out.getAmount();
        }
        if (got > 0 && action.execute()) {
            invalidate(level);
        }
        return got;
    }

    /** Un retrait a changé les stocks : l'annuaire sera refait au prochain appel. */
    public static void invalidate(Level level) {
        synchronized (CACHE) {
            Map<LogisticNetwork, Cached> cache = CACHE.get(level);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    /** Liste triée des types en stock (plus gros d'abord). */
    public static List<Map.Entry<DataType, Long>> sortedStock(Snapshot s) {
        List<Map.Entry<DataType, Long>> out = new ArrayList<>(s.stock.entrySet());
        out.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
        return out;
    }

    @Nullable
    public static BlockEntity machine(Level level, Snapshot s, int index) {
        return index < 0 || index >= s.machines.size() || !level.isLoaded(s.machines.get(index)) ? null : level.getBlockEntity(s.machines.get(index));
    }
}
