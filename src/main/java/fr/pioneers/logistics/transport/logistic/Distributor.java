package fr.pioneers.logistics.transport.logistic;

/**
 * 1.11 : algorithme de repartition d'une quantite entre les sorties d'un canal (spec §3.4), sans Minecraft (banc
 * {@code tools/cables/DistributionBench}). Les sorties sont des indices 0..n-1 ; {@link Sink#accept} insere (ou simule) et rend la quantite
 * acceptee. Aucune allocation par operation.
 * <p>
 * Tableaux fixes a la compilation du canal ({@link #setup}) : priorites (sorties deja triees par priorite decroissante), poids, groupes de
 * meme priorite ; remplissage lu par {@link #setFill} (mode Au plus juste).
 */
public final class Distributor {

    /** Insertion dans la sortie {@code index} ; rend la quantite acceptee (0..amount). */
    public interface Sink {

        long accept(int index, long amount);
    }

    private int n;
    private int[] weights = new int[0];
    /** Debut de chaque groupe de meme priorite (groupStart[g]..groupStart[g+1]-1), sorties triees par priorite decroissante. */
    private int[] groupStart = new int[1];
    private int groups;
    private int[] groupCursor = new int[0];
    private int cursor;
    private double[] fill = new double[0];
    private int[] order = new int[0];
    private boolean orderDirty = true;
    /** Derniere sortie servie par le dernier tour (-1 : aucune) : l'operation suivante part de la sortie d'apres (vrai tour a tour). */
    private int served = -1;

    /**
     * @param priorities priorite de chaque sortie, DEJA triee par ordre decroissant
     * @param weights    poids (1..16) de chaque sortie
     */
    public void setup(int count, int[] priorities, int[] weights) {
        n = count;
        this.weights = weights;
        groupStart = new int[count + 1];
        groups = 0;
        for (int i = 0; i < count; i++) {
            if (i == 0 || priorities[i] != priorities[i - 1]) {
                groupStart[groups++] = i;
            }
        }
        groupStart[groups] = count;
        groupCursor = new int[Math.max(1, groups)];
        for (int g = 0; g < groups; g++) {
            groupCursor[g] = groupStart[g];
        }
        cursor = 0;
        fill = new double[count];
        order = new int[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        orderDirty = false;
    }

    public int size() {
        return n;
    }

    /** Mode Au plus juste : remplissage (0..1) de la sortie {@code index}, relu par le canal au plus toutes les 20 ticks. */
    public void setFill(int index, double value) {
        fill[index] = value;
        orderDirty = true;
    }

    /**
     * Repartit {@code amount} entre les sorties (sauf {@code exclude}, la source elle-meme : jamais de retour vers l'emetteur).
     *
     * @return la quantite placee
     */
    public long distribute(Distribution mode, long amount, int exclude, Sink sink) {
        if (n == 0 || amount <= 0) {
            return 0;
        }
        long left = amount;
        switch (mode) {
            case ROUND_ROBIN -> {
                left = roundRobin(0, n, cursor, left, exclude, sink);
                cursor = next(served, cursor, 0, n);
            }
            case PRIORITY -> {
                for (int g = 0; g < groups && left > 0; g++) {
                    int from = groupStart[g], to = groupStart[g + 1];
                    left = roundRobin(from, to, groupCursor[g], left, exclude, sink);
                    groupCursor[g] = next(served, groupCursor[g], from, to);
                }
            }
            case PROPORTIONAL -> {
                long total = 0;
                for (int i = 0; i < n; i++) {
                    if (i != exclude) {
                        total += weights[i];
                    }
                }
                if (total > 0) {
                    long start = left;
                    for (int k = 0; k < n && left > 0; k++) {
                        int i = (cursor + k) % n;
                        if (i == exclude) {
                            continue;
                        }
                        long share = Math.min(left, start * weights[i] / total);
                        if (share > 0) {
                            left -= sink.accept(i, share);
                        }
                    }
                }
                //Reste (arrondis, sorties pleines) : tour a tour
                left = roundRobin(0, n, cursor, left, exclude, sink);
                cursor = next(served, cursor, 0, n);
            }
            case BALANCED -> {
                if (orderDirty) {
                    sortByFill();
                }
                for (int k = 0; k < n && left > 0; k++) {
                    int i = order[k];
                    if (i != exclude) {
                        left -= sink.accept(i, left);
                    }
                }
            }
        }
        return amount - left;
    }

    private long roundRobin(int from, int to, int start, long left, int exclude, Sink sink) {
        int len = to - from;
        served = -1;
        for (int k = 0; k < len && left > 0; k++) {
            int i = start + k;
            if (i >= to) {
                i -= len;
            }
            if (i != exclude) {
                long accepted = sink.accept(i, left);
                if (accepted > 0) {
                    left -= accepted;
                    served = i;
                }
            }
        }
        return left;
    }

    /** Depart suivant : juste apres la derniere sortie servie, sinon un cran plus loin. */
    private static int next(int served, int cursor, int from, int to) {
        int n = (served >= 0 ? served : cursor) + 1;
        return n >= to ? from : n;
    }

    /** Tri par insertion des sorties par remplissage croissant (n petit, tableau presque trie d'une lecture a l'autre). */
    private void sortByFill() {
        for (int a = 1; a < n; a++) {
            int v = order[a];
            double f = fill[v];
            int b = a - 1;
            while (b >= 0 && fill[order[b]] > f) {
                order[b + 1] = order[b];
                b--;
            }
            order[b + 1] = v;
        }
        orderDirty = false;
    }
}
