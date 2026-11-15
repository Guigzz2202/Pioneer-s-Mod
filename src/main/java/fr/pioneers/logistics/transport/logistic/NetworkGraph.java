package fr.pioneers.logistics.transport.logistic;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (spec §7.1) : graphe d'un reseau, construit par un parcours en largeur des ETATS des blocs (pas des tiles) depuis un cable, arrete
 * aux chunks non charges et aux frontieres refusees ({@link Reader#canCross}). Tableaux compacts (positions, cotes relies, fiches, niveau),
 * index position -> rang. Pur : le banc hors jeu fournit son propre lecteur.
 * <p>
 * {@link #compress} donne le graphe simplifie du diagramme : noeuds = machines et jonctions (cables a 3 liaisons ou plus, ou portant les
 * fiches de machines differentes), liens = chaines de cables (sans longueur dessinee : un cable de 500 blocs = un trait).
 */
public final class NetworkGraph {

    /** Lecteur du monde (Minecraft : etats des blocs ; banc : table). */
    public interface Reader {

        /** -1 : pas un cable logistique (ou chunk non charge) ; sinon ordinal du {@link CableLevel} | {@link #CLEAR} si transparent. */
        int info(long pos);

        /** Cotes relies a un cable (bits = ordinal de {@link Direction}). */
        int cableSides(long pos);

        /** Cotes a fiche (vers une machine). */
        int plugSides(long pos);

        /** Le reseau peut-il passer de {@code from} a {@code to} (frontiere de claim) ? */
        default boolean canCross(long from, long to) {
            return true;
        }
    }

    public static final int CLEAR = 0x10, LEVEL_MASK = 0x0F;
    private static final Direction[] DIRS = Direction.values();

    public long[] cables = new long[64];
    public byte[] sides = new byte[64], plugs = new byte[64], info = new byte[64];
    public int size;
    public final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
    public CableLevel level = CableLevel.ULTIMATE;
    public final int[] perLevel = new int[CableLevel.all().length];
    public int clear, plugCount;

    private NetworkGraph() {
        index.defaultReturnValue(-1);
    }

    /** Parcours depuis {@code start} ; null si ce n'est pas un cable. {@code claimed} : positions deja prises par un autre reseau ce tick. */
    @Nullable
    public static NetworkGraph build(Reader reader, long start) {
        int first = reader.info(start);
        if (first < 0) {
            return null;
        }
        NetworkGraph g = new NetworkGraph();
        boolean superconducting = CableLevel.byIndex(first & LEVEL_MASK) == CableLevel.SUPER;
        g.level = CableLevel.byIndex(first & LEVEL_MASK);
        g.add(start, first, reader);
        for (int head = 0; head < g.size; head++) {
            long pos = g.cables[head];
            int linked = g.sides[head];
            for (int d = 0; d < 6; d++) {
                if ((linked >> d & 1) == 0) {
                    continue;
                }
                long next = BlockPos.offset(pos, DIRS[d]);
                if (g.index.get(next) >= 0) {
                    continue;
                }
                int nextInfo = reader.info(next);
                //Liaison symetrique seulement (etats transitoires pendant un tick : on ne suit qu'un lien vu des deux cotes)
                if (nextInfo < 0 || (CableLevel.byIndex(nextInfo & LEVEL_MASK) == CableLevel.SUPER) != superconducting
                    || (reader.cableSides(next) >> DIRS[d].getOpposite().ordinal() & 1) == 0 || !reader.canCross(pos, next)) {
                    continue;
                }
                g.add(next, nextInfo, reader);
            }
        }
        //Cotes relies vers des cables hors du graphe (non charges, frontiere) : ignores dans les tableaux
        for (int i = 0; i < g.size; i++) {
            int linked = g.sides[i], kept = 0;
            for (int d = 0; d < 6; d++) {
                if ((linked >> d & 1) != 0 && g.index.get(BlockPos.offset(g.cables[i], DIRS[d])) >= 0) {
                    kept |= 1 << d;
                }
            }
            g.sides[i] = (byte) kept;
        }
        return g;
    }

    /**
     * Ajout direct d'un cable nu (pose qui ne touche qu'un reseau, spec §7.1 : sans parcours) ; les voisins relies gagnent la liaison.
     *
     * @return le rang du cable
     */
    public int append(long pos, int inf, int linked) {
        add(pos, inf, linked, 0);
        int i = size - 1;
        for (int d = 0; d < 6; d++) {
            if ((linked >> d & 1) == 0) {
                continue;
            }
            int j = index.get(BlockPos.offset(pos, DIRS[d]));
            if (j >= 0) {
                sides[j] |= (byte) (1 << DIRS[d].getOpposite().ordinal());
            } else {
                sides[i] &= (byte) ~(1 << d);
            }
        }
        return i;
    }

    private void add(long pos, int inf, Reader reader) {
        add(pos, inf, reader.cableSides(pos), reader.plugSides(pos));
    }

    private void add(long pos, int inf, int linked, int plugSides) {
        if (size == cables.length) {
            int n = size * 2;
            cables = Arrays.copyOf(cables, n);
            sides = Arrays.copyOf(sides, n);
            plugs = Arrays.copyOf(plugs, n);
            info = Arrays.copyOf(info, n);
        }
        CableLevel l = CableLevel.byIndex(inf & LEVEL_MASK);
        cables[size] = pos;
        sides[size] = (byte) linked;
        plugs[size] = (byte) plugSides;
        info[size] = (byte) inf;
        index.put(pos, size);
        perLevel[l.ordinal()]++;
        if (l.ordinal() < level.ordinal()) {
            level = l;
        }
        if ((inf & CLEAR) != 0) {
            clear++;
        }
        if (plugs[size] != 0) {
            plugCount++;
        }
        size++;
    }

    /** Nombre de cables au niveau le plus faible quand le reseau melange des niveaux (maillon faible ; 0 si un seul niveau). */
    public int weakCount() {
        int levels = 0;
        for (int n : perLevel) {
            levels += n > 0 ? 1 : 0;
        }
        return levels > 1 ? perLevel[level.ordinal()] : 0;
    }

    /** Somme d'une constante par cable (capacites thermiques, volumes d'air, pertes...). */
    public double sum(java.util.function.ToDoubleFunction<CableLevel> per) {
        double total = 0;
        for (CableLevel l : CableLevel.all()) {
            if (perLevel[l.ordinal()] > 0) {
                total += perLevel[l.ordinal()] * per.applyAsDouble(l);
            }
        }
        return total;
    }

    /** Ensemble des chunks touches (ChunkPos.asLong). */
    public LongOpenHashSet chunks() {
        LongOpenHashSet set = new LongOpenHashSet();
        for (int i = 0; i < size; i++) {
            set.add(chunk(cables[i]));
        }
        return set;
    }

    public static long chunk(long pos) {
        int x = BlockPos.getX(pos) >> 4, z = BlockPos.getZ(pos) >> 4;
        return (long) x & 4294967295L | ((long) z & 4294967295L) << 32;
    }

    // ------------------------------------------------------------------
    // Graphe compresse du diagramme
    // ------------------------------------------------------------------

    /** Noeud du diagramme : machine (id = cle du raccord) ou jonction (id = position du cable). */
    public record Node(long id, boolean machine, long pos) {
    }

    /** Lien du diagramme : chaine de {@code length} cables entre deux noeuds. */
    public record Link(long a, long b, int length) {
    }

    public record Compressed(List<Node> nodes, List<Link> links) {
    }

    /** Machine touchee par une fiche : cle du raccord, ou {@link Long#MIN_VALUE} si aucune. */
    public interface PlugOwner {

        long owner(long cable, Direction face);
    }

    /**
     * Graphe compresse : cables de degre 2 (liaisons + fiches) fondus dans les liens ; machines et autres cables gardes comme noeuds. Une
     * boucle sans noeud garde un cable comme jonction.
     */
    public Compressed compress(PlugOwner owners) {
        //Voisins de chaque cable : cables (rang >= 0) et machines (codees -2 - rang de machine)
        Long2IntOpenHashMap machineIndex = new Long2IntOpenHashMap();
        machineIndex.defaultReturnValue(-1);
        List<Long> machines = new ArrayList<>();
        long[][] plugOwners = new long[size][];
        int[] degree = new int[size];
        boolean[] key = new boolean[size];
        for (int i = 0; i < size; i++) {
            int p = plugs[i];
            degree[i] = Integer.bitCount(sides[i] & 63);
            if (p == 0) {
                continue;
            }
            long[] own = new long[6];
            Arrays.fill(own, Long.MIN_VALUE);
            long seen = Long.MIN_VALUE;
            for (int d = 0; d < 6; d++) {
                if ((p >> d & 1) == 0) {
                    continue;
                }
                long m = owners.owner(cables[i], DIRS[d]);
                if (m == Long.MIN_VALUE) {
                    continue;
                }
                if (machineIndex.get(m) < 0) {
                    machineIndex.put(m, machines.size());
                    machines.add(m);
                }
                own[d] = m;
                degree[i]++;
                if (seen != Long.MIN_VALUE && seen != m) {
                    key[i] = true;
                }
                seen = m;
            }
            plugOwners[i] = own;
        }
        List<Node> nodes = new ArrayList<>();
        Long2ObjectOpenHashMap<Node> byId = new Long2ObjectOpenHashMap<>();
        for (long m : machines) {
            Node node = new Node(m, true, m);
            nodes.add(node);
            byId.put(m, node);
        }
        for (int i = 0; i < size; i++) {
            if (degree[i] != 2) {
                key[i] = true;
            }
        }
        List<Link> links = new ArrayList<>();
        boolean[] walked = new boolean[size];
        //Chaque noeud cable : un lien par direction (cable voisin ou fiche)
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < size; i++) {
                if (!key[i]) {
                    if (pass == 0 || walked[i]) {
                        continue;
                    }
                    //Boucle sans noeud : ce cable devient une jonction
                    key[i] = true;
                }
                if (!byId.containsKey(cables[i])) {
                    Node node = new Node(cables[i], false, cables[i]);
                    nodes.add(node);
                    byId.put(cables[i], node);
                }
                walked[i] = true;
                for (int d = 0; d < 6; d++) {
                    if (plugOwners[i] != null && plugOwners[i][d] != Long.MIN_VALUE) {
                        links.add(new Link(cables[i], plugOwners[i][d], 0));
                    }
                    if ((sides[i] >> d & 1) == 0) {
                        continue;
                    }
                    //Suit la chaine jusqu'au prochain noeud
                    int prev = i, cur = index.get(BlockPos.offset(cables[i], DIRS[d]));
                    int length = 1;
                    long end = Long.MIN_VALUE;
                    while (cur >= 0 && !key[cur]) {
                        walked[cur] = true;
                        //Fiche d'un cable de degre 2 : la chaine se termine sur la machine
                        int next = -1;
                        if (plugOwners[cur] != null) {
                            for (int e = 0; e < 6; e++) {
                                if (plugOwners[cur][e] != Long.MIN_VALUE) {
                                    end = plugOwners[cur][e];
                                }
                            }
                        }
                        if (end != Long.MIN_VALUE) {
                            break;
                        }
                        for (int e = 0; e < 6; e++) {
                            if ((sides[cur] >> e & 1) != 0) {
                                int n = index.get(BlockPos.offset(cables[cur], DIRS[e]));
                                if (n != prev && n >= 0) {
                                    next = n;
                                    break;
                                }
                            }
                        }
                        prev = cur;
                        cur = next;
                        length++;
                    }
                    if (end == Long.MIN_VALUE && cur >= 0) {
                        end = cables[cur];
                        //Lien entre deux cables-noeuds : un seul sens (le plus petit rang)
                        if (cur < i || cur == i && d > DIRS[d].getOpposite().ordinal()) {
                            continue;
                        }
                    }
                    if (end != Long.MIN_VALUE) {
                        links.add(new Link(cables[i], end, length));
                    }
                }
            }
        }
        return new Compressed(nodes, links);
    }
}
