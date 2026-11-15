package fr.pioneers.farm.greenhouse;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 3) : REGISTRE DES SERRES par monde (serveur et client, chacun le sien), consulte par les cultures SANS RECALCUL : une
 * culture demande {@link #covers} (index spatial par chunk : les serres dont la boite touche son chunk, puis la boite, puis un bit). Les
 * regulateurs y inscrivent leur serre apres chaque releve (serveur) ou a chaque donnee recue (client) et la retirent quand ils s'en vont.
 * Une serre NON formee est aussi inscrite, sans interieur : sa zone de veille ({@link #watch}) fait relire le regulateur quand un bloc est
 * pose ou casse autour (le joueur bouche le trou : la serre se forme tout de suite).
 */
public final class GreenhouseRegistry {

    /** Marge de veille d'une serre non formee autour du regulateur (au-dela : rien de ce qu'on y fait ne la concerne). */
    public static final int WATCH_XZ = GreenhouseScan.REACH_XZ, WATCH_Y = GreenhouseScan.REACH_Y;

    /**
     * Une serre : regulateur, coin bas de l'interieur et taille, cases de l'interieur (bits x + sx * (z + sz * y)), formee ou non, boite de
     * veille (interieur + 1 bloc : paroi, sol, toit ; non formee : la marge autour du regulateur).
     */
    public static final class Entry {

        public final BlockPos regulator;
        final int x0, y0, z0, sx, sy, sz;
        final BitSet inside;
        public final boolean formed;
        final int wx0, wy0, wz0, wx1, wy1, wz1;

        Entry(BlockPos regulator, int[] min, int[] max, BitSet inside, boolean formed) {
            this.regulator = regulator.immutable();
            this.formed = formed;
            this.inside = inside;
            if (formed) {
                x0 = min[0];
                y0 = min[1];
                z0 = min[2];
                sx = max[0] - min[0] + 1;
                sy = max[1] - min[1] + 1;
                sz = max[2] - min[2] + 1;
                wx0 = x0 - 1;
                wy0 = y0 - 1;
                wz0 = z0 - 1;
                wx1 = max[0] + 1;
                wy1 = max[1] + 1;
                wz1 = max[2] + 1;
            } else {
                x0 = y0 = z0 = sx = sy = sz = 0;
                wx0 = regulator.getX() - WATCH_XZ;
                wy0 = regulator.getY() - WATCH_Y;
                wz0 = regulator.getZ() - WATCH_XZ;
                wx1 = regulator.getX() + WATCH_XZ;
                wy1 = regulator.getY() + WATCH_Y;
                wz1 = regulator.getZ() + WATCH_XZ;
            }
        }

        /** La case est dans l'interieur de cette serre formee ? */
        public boolean contains(BlockPos pos) {
            if (!formed) {
                return false;
            }
            int x = pos.getX() - x0, y = pos.getY() - y0, z = pos.getZ() - z0;
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
                return false;
            }
            return inside.get(x + sx * (z + sz * y));
        }

        /** La case est dans la zone de veille (paroi, sol, toit, interieur ; non formee : autour du regulateur) ? */
        public boolean watches(BlockPos pos) {
            return pos.getX() >= wx0 && pos.getX() <= wx1 && pos.getY() >= wy0 && pos.getY() <= wy1 && pos.getZ() >= wz0 && pos.getZ() <= wz1;
        }

        /** Nombre de cases de l'interieur. */
        public int volume() {
            return inside.cardinality();
        }

        /** Coin bas et taille de l'interieur {x0, y0, z0, sx, sy, sz} (donnees du client). */
        public int[] box() {
            return new int[]{x0, y0, z0, sx, sy, sz};
        }

        public long[] bits() {
            return inside.toLongArray();
        }
    }

    private static final class Index {

        final Map<BlockPos, Entry> byRegulator = new HashMap<>();
        final Map<Long, List<Entry>> byChunk = new HashMap<>();

        void add(Entry e) {
            remove(e.regulator);
            byRegulator.put(e.regulator, e);
            for (int cx = e.wx0 >> 4; cx <= e.wx1 >> 4; cx++) {
                for (int cz = e.wz0 >> 4; cz <= e.wz1 >> 4; cz++) {
                    byChunk.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> new ArrayList<>(2)).add(e);
                }
            }
        }

        void remove(BlockPos regulator) {
            Entry old = byRegulator.remove(regulator);
            if (old == null) {
                return;
            }
            for (int cx = old.wx0 >> 4; cx <= old.wx1 >> 4; cx++) {
                for (int cz = old.wz0 >> 4; cz <= old.wz1 >> 4; cz++) {
                    long key = ChunkPos.asLong(cx, cz);
                    List<Entry> l = byChunk.get(key);
                    if (l != null) {
                        l.remove(old);
                        if (l.isEmpty()) {
                            byChunk.remove(key);
                        }
                    }
                }
            }
        }
    }

    private static final Map<LevelAccessor, Index> LEVELS = new WeakHashMap<>();

    private GreenhouseRegistry() {
    }

    private static Index index(LevelAccessor level) {
        return LEVELS.computeIfAbsent(level, l -> new Index());
    }

    /** Inscrit (ou remplace) la serre formee du regulateur : interieur releve. */
    public static void putFormed(LevelAccessor level, BlockPos regulator, Set<BlockPos> interior, int[] min, int[] max) {
        int sx = max[0] - min[0] + 1, sy = max[1] - min[1] + 1, sz = max[2] - min[2] + 1;
        BitSet bits = new BitSet(sx * sy * sz);
        for (BlockPos p : interior) {
            bits.set((p.getX() - min[0]) + sx * ((p.getZ() - min[2]) + sz * (p.getY() - min[1])));
        }
        put(level, new Entry(regulator, min, max, bits, true));
    }

    /** Inscrit la serre d'un regulateur dont la serre n'est pas formee (zone de veille seulement). */
    public static void putUnformed(LevelAccessor level, BlockPos regulator) {
        put(level, new Entry(regulator, new int[3], new int[3], new BitSet(), false));
    }

    /** Client : serre recue du serveur (coin, taille, bits ; taille nulle : non formee). */
    public static void putClient(LevelAccessor level, BlockPos regulator, int[] box, long[] bits) {
        if (box.length < 6 || box[3] <= 0 || box[4] <= 0 || box[5] <= 0) {
            putUnformed(level, regulator);
            return;
        }
        int[] min = {box[0], box[1], box[2]}, max = {box[0] + box[3] - 1, box[1] + box[4] - 1, box[2] + box[5] - 1};
        put(level, new Entry(regulator, min, max, BitSet.valueOf(bits), true));
    }

    private static void put(LevelAccessor level, Entry e) {
        synchronized (LEVELS) {
            index(level).add(e);
        }
    }

    public static void remove(LevelAccessor level, BlockPos regulator) {
        synchronized (LEVELS) {
            Index i = LEVELS.get(level);
            if (i != null) {
                i.remove(regulator);
            }
        }
    }

    /** La case est-elle dans une serre formee de ce monde ? (sans recalcul : chunk, boite, bit) */
    public static boolean covers(LevelAccessor level, BlockPos pos) {
        return coveringRegulator(level, pos) != null;
    }

    /** Regulateur de la serre formee qui contient la case, ou null. */
    @Nullable
    public static BlockPos coveringRegulator(LevelAccessor level, BlockPos pos) {
        synchronized (LEVELS) {
            Index i = LEVELS.get(level);
            if (i == null) {
                return null;
            }
            List<Entry> l = i.byChunk.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            if (l != null) {
                for (Entry e : l) {
                    if (e.contains(pos)) {
                        return e.regulator;
                    }
                }
            }
        }
        return null;
    }

    /** Regulateurs dont la zone de veille contient la case (pose ou casse d'un bloc : a relire). */
    public static List<BlockPos> watching(LevelAccessor level, BlockPos pos) {
        List<BlockPos> out = new ArrayList<>();
        synchronized (LEVELS) {
            Index i = LEVELS.get(level);
            if (i == null) {
                return out;
            }
            List<Entry> l = i.byChunk.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            if (l != null) {
                for (Entry e : l) {
                    if (e.watches(pos)) {
                        out.add(e.regulator);
                    }
                }
            }
        }
        return out;
    }

    /** Serre inscrite pour ce regulateur, ou null (GameTests, interface). */
    @Nullable
    public static Entry get(LevelAccessor level, BlockPos regulator) {
        synchronized (LEVELS) {
            Index i = LEVELS.get(level);
            return i == null ? null : i.byRegulator.get(regulator);
        }
    }

    /** Monde decharge : tout est retire. */
    public static void clear(LevelAccessor level) {
        synchronized (LEVELS) {
            LEVELS.remove(level);
        }
    }
}
