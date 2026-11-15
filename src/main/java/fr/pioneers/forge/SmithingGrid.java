package fr.pioneers.forge;

import java.util.Arrays;
import java.util.List;

/**
 * Grille 16 x 16 du minijeu de l'enclume (vue de dessus) : une case = un pixel de matiere (plein ou vide). Rangee {@code z} 0..15 (haut de
 * l'ecran = 0), colonne {@code x} 0..15 ; bit {@code z * 16 + x} d'un {@code long[4]} (NBT {@code mask}). Un lingot = 32 pixels.
 * Objet modifiable, sans dependance a Minecraft (testable au banc).
 */
public final class SmithingGrid {

    public static final int SIZE = 16;
    /** 1.0.2-beta : lingots au plus sur une piece (avant : 4 ; le marteau de minage en demande 5) ; 8 x 32 = 256 = la grille. */
    public static final int MAX_INGOTS = 8;
    private final long[] bits;

    public SmithingGrid() {
        this(new long[4]);
    }

    private SmithingGrid(long[] bits) {
        this.bits = bits;
    }

    /** Copie de 4 longs (NBT {@code mask}) ; un tableau de mauvaise taille donne une grille vide. */
    public static SmithingGrid of(long[] mask) {
        return new SmithingGrid(mask != null && mask.length == 4 ? mask.clone() : new long[4]);
    }

    /**
     * Grille d'un motif de recette : 16 chaines de 16 caracteres, {@code '#'} = matiere, tout autre caractere = vide. Lignes ou caracteres
     * manquants = vides.
     */
    public static SmithingGrid parse(List<String> pattern) {
        SmithingGrid g = new SmithingGrid();
        for (int z = 0; z < Math.min(SIZE, pattern.size()); z++) {
            String row = pattern.get(z);
            for (int x = 0; x < Math.min(SIZE, row.length()); x++) {
                if (row.charAt(x) == '#') {
                    g.set(x, z, true);
                }
            }
        }
        return g;
    }

    /**
     * Matiere de depart pour {@code ingots} lingots : bloc 8 x 4 au centre (x 4..11, z 6..9), puis les suivants au-dessous (z 10..13), au-dessus
     * (z 2..5), puis z 14..15 et 0..1 (4 lingots = 128 pixels) ; 1.0.2-beta : jusqu'a {@link #MAX_INGOTS} lingots, colonnes x 12..13, 2..3,
     * 14..15, 0..1.
     */
    public static SmithingGrid initial(int ingots) {
        SmithingGrid g = new SmithingGrid();
        int[][] bands = {{6, 9}, {10, 13}, {2, 5}, {14, 15}, {0, 1}};
        int left = Math.max(0, Math.min(MAX_INGOTS, ingots)) * 32;
        for (int[] band : bands) {
            for (int z = band[0]; z <= band[1] && left > 0; z++) {
                for (int x = 4; x <= 11 && left > 0; x++) {
                    g.set(x, z, true);
                    left--;
                }
            }
        }
        //1.0.2-beta (Guillaume : « l'enclume doit accepter plus de 5 lingots », le marteau de minage en demande 5) : du 5e au 8e lingot,
        //des colonnes de 2 de part et d'autre de la barre centrale (8 colonnes de 16) : a droite, a gauche, puis au bord ; 4 lingots et moins :
        //meme depart qu'avant (budgets et pieces en cours inchanges)
        int[][] columns = {{12, 13}, {2, 3}, {14, 15}, {0, 1}};
        for (int[] band : columns) {
            for (int z = 0; z < SIZE && left > 0; z++) {
                for (int x = band[0]; x <= band[1] && left > 0; x++) {
                    g.set(x, z, true);
                    left--;
                }
            }
        }
        return g;
    }

    public boolean get(int x, int z) {
        if (x < 0 || z < 0 || x >= SIZE || z >= SIZE) {
            return false;
        }
        int i = z * SIZE + x;
        return (bits[i >> 6] >>> (i & 63) & 1L) != 0;
    }

    public void set(int x, int z, boolean filled) {
        if (x < 0 || z < 0 || x >= SIZE || z >= SIZE) {
            return;
        }
        int i = z * SIZE + x;
        if (filled) {
            bits[i >> 6] |= 1L << (i & 63);
        } else {
            bits[i >> 6] &= ~(1L << (i & 63));
        }
    }

    /** Nombre de pixels de matiere. */
    public int count() {
        return Long.bitCount(bits[0]) + Long.bitCount(bits[1]) + Long.bitCount(bits[2]) + Long.bitCount(bits[3]);
    }

    /** Pixels presents mais hors de la forme visee (a couper ou deplacer). */
    public int excess(SmithingGrid target) {
        int n = 0;
        for (int i = 0; i < 4; i++) {
            n += Long.bitCount(bits[i] & ~target.bits[i]);
        }
        return n;
    }

    /** Cases de la forme visee encore vides. */
    public int missing(SmithingGrid target) {
        return target.excess(this);
    }

    /** Vrai si la matiere recouvre exactement la forme. */
    public boolean matches(SmithingGrid target) {
        return Arrays.equals(bits, target.bits);
    }

    /** Copie des 4 longs (pour le NBT). */
    public long[] toArray() {
        return bits.clone();
    }

    public SmithingGrid copy() {
        return new SmithingGrid(bits.clone());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SmithingGrid g && Arrays.equals(bits, g.bits);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bits);
    }
}
