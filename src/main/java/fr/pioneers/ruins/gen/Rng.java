package fr.pioneers.ruins.gen;

/**
 * 1.9 (MAJ RP, bloc 1) : generateur pseudo-aleatoire de la generation des ruines (SplitMix64). Java pur, sans Minecraft : le banc hors jeu
 * ({@code tools/rptest/RuinGenBench}) rejoue exactement les memes tirages. Un objet par usage, jamais partage entre deux fils.
 */
public final class Rng {

    private long state;

    public Rng(long seed) {
        this.state = seed;
    }

    /** Melange de SplitMix64 (bijectif, rapide). */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Graine derivee de plusieurs valeurs (ordre significatif). */
    public static long hash(long... parts) {
        long h = 0x9E3779B97F4A7C15L;
        for (long p : parts) {
            h = mix(h ^ mix(p + 0x632BE59BD9B4E019L));
        }
        return h;
    }

    public long nextLong() {
        state += 0x9E3779B97F4A7C15L;
        return mix(state);
    }

    /** Entier dans [0, bound). */
    public int nextInt(int bound) {
        if (bound <= 1) {
            return 0;
        }
        return (int) Math.floorMod(nextLong() >>> 1, (long) bound);
    }

    /** Entier dans [min, max] (bornes comprises). */
    public int range(int min, int max) {
        return max <= min ? min : min + nextInt(max - min + 1);
    }

    /** Reel dans [0, 1). */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public double range(double min, double max) {
        return min + (max - min) * nextDouble();
    }

    public boolean chance(double p) {
        return nextDouble() < p;
    }

    /** Melange de Fisher-Yates d'une liste. */
    public <T> void shuffle(java.util.List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            T t = list.get(i);
            list.set(i, list.get(j));
            list.set(j, t);
        }
    }
}
