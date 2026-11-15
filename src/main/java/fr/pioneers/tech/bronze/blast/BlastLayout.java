package fr.pioneers.tech.bronze.blast;

import fr.pioneers.api.multiblock.HouseNbt;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 0.20.40 (demande de Guillaume : « une forme plus sympa ») : plan du haut fourneau, SOURCE UNIQUE pour la verification, le plan projete et
 * l'apercu. 1.0.4-beta (lot 24) : la FORME DESSINEE PAR GUILLAUME ({@link HouseNbt}, {@code blast_furnace} : NBT
 * {@code tools/gc/industry/nbt/dev_v1/primitive_blast_furnace.nbt}), lue ici avec les roles du haut fourneau :
 * <ul>
 *     <li>y 0, le creuset : socle rond de 5 x 5 en FOYERS EN BRIQUES REFRACTAIRES, le trou de coulee (controleur, bloc plein, plus de rigole
 *     devant lui) au milieu du devant, UNE tuyere au milieu de l'arriere (le soufflet se pose derriere elle, tourne vers elle) ;</li>
 *     <li>y 1 et 2, la cuve : anneau de briques refractaires autour d'un vide en croix ; y 3 a 6, la tour qui s'affine en cheminee, le
 *     gueulard ouvert au sommet.</li>
 * </ul>
 * Repere du controleur ({@link HouseNbt}) : x a droite en regardant la facade, y en haut, z vers l'interieur ; l'axe de la cuve est a
 * {@link #AXIS_DEPTH} cases derriere le controleur. Les briques prennent un role peint ({@link FireBricksBlock.Part}) : cerclees de fer aux
 * niveaux pairs, merlons au sommet.
 */
public final class BlastLayout {

    /** Cle de la structure de Guillaume. */
    public static final String SHAPE = "blast_furnace";
    /** Hauteur de la structure (creuset compris). */
    public static final int HEIGHT = 7;
    /** Profondeur de l'axe de la cuve derriere le controleur. */
    public static final int AXIS_DEPTH = 2;

    public enum Kind {
        /** Brique refractaire (role peint {@link #part}). */
        BRICK,
        /** Tuyere (elle seule). */
        TUYERE,
        /** Doit rester vide (cuve). */
        AIR,
        CONTROLLER,
        /** 1.0.4-beta (lot 24) : foyer en briques refractaires (socle). */
        FIREBOX
    }

    public record Cell(int x, int y, int z, Kind kind, FireBricksBlock.Part part) {
    }

    private static List<Cell> cells;

    private BlastLayout() {
    }

    private static List<Cell> build() {
        List<Cell> out = new ArrayList<>();
        int top = 0;
        for (HouseNbt.Cell c : HouseNbt.cells(SHAPE)) {
            if (c.letter() == 'B') {
                top = Math.max(top, c.y());
            }
        }
        for (HouseNbt.Cell c : HouseNbt.cells(SHAPE)) {
            switch (c.letter()) {
                case 'K' -> out.add(new Cell(c.x(), c.y(), c.z(), Kind.CONTROLLER, FireBricksBlock.Part.NONE));
                case 'I' -> out.add(new Cell(c.x(), c.y(), c.z(), Kind.AIR, FireBricksBlock.Part.NONE));
                case 'T' -> out.add(new Cell(c.x(), c.y(), c.z(), Kind.TUYERE, FireBricksBlock.Part.NONE));
                case 'X' -> out.add(new Cell(c.x(), c.y(), c.z(), Kind.FIREBOX, FireBricksBlock.Part.NONE));
                default -> out.add(new Cell(c.x(), c.y(), c.z(), Kind.BRICK, c.y() == top ? FireBricksBlock.Part.CROWN
                      : c.y() % 2 == 0 ? FireBricksBlock.Part.BAND : FireBricksBlock.Part.STACK));
            }
        }
        return List.copyOf(out);
    }

    public static synchronized List<Cell> cells() {
        if (cells == null) {
            cells = build();
        }
        return cells;
    }

    /** Nombre de tuyeres de la structure (au moins 1) : l'air qui porte le four a plein feu (un soufflet par tuyere). */
    public static int tuyeres() {
        return Math.max(1, (int) cells().stream().filter(c -> c.kind() == Kind.TUYERE).count());
    }

    /** Position dans le monde d'une case, pour un trou de coulee en {@code controller} dont la facade regarde {@code facing}. */
    public static BlockPos at(BlockPos controller, Direction facing, int x, int y, int z) {
        return HouseNbt.at(controller, facing, x, y, z);
    }

    /** Axe de la cuve au niveau du creuset. */
    public static BlockPos centre(BlockPos controller, Direction facing) {
        return at(controller, facing, 0, 0, AXIS_DEPTH);
    }
}
