package fr.pioneers.farm.food;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 1.9.2 (retours de Guillaume : « les etageres sont buggees quand on les place en escalier, effet de connexion bizarre ») : logique des
 * liaisons des etageres, sans aucun type de Minecraft pour etre testee hors jeu (banc {@code tools/farmtest/ShelfBench.java}, planches du
 * patch 1.0.3-beta lot 12).
 * <p>
 * Regles : on ne se lie qu'a une voisine ALIGNEE : meme facade, meme mode (murale / au sol), et, de cote, meme hauteur de planche (meme
 * {@code down}).
 * <p>
 * 1.0.3-beta (lot 12, Guillaume : « une murale et une au sol ; la murale se place seulement sur le flanc d'un bloc tandis que l'autre se pose
 * au sol ») : deux blocs distincts.
 * <ul>
 *   <li><b>Etagere au sol</b> ({@link DishShelfBlock}) : « 4 pattes et deux planches (une en bas et une en haut) ; se connecte parfaitement
 *   verticalement et horizontalement ; une fois connectee il ne reste qu'une planche (sauf la derniere en haut qui a aussi une planche en
 *   haut) ; on peut remplir toutes les planches ». Chaque module garde sa planche basse (sur pattes au sol, au ras du bloc sur une autre),
 *   seul le module du sommet ({@code !up}) garde sa planche haute ; montants sur toute la hauteur du bloc (continus dans une pile) ; cote a
 *   cote, les montants de gauche disparaissent devant une voisine liee (ceux de la voisine servent aux deux : un seul jeu par jonction) et
 *   les planches se prolongent jusqu'au bord. Plus de {@code topped} (lot 11).</li>
 *   <li><b>Etagere murale</b> ({@link WallShelfBlock}) : « une planche avec 2 equerres, se connecte parfaitement avec les autres
 *   horizontalement » : planche continue, equerres seulement aux bouts de la rangee, plus une au milieu d'un bloc tous les 3 blocs
 *   ({@link #midBracket}) dans une longue rangee.</li>
 * </ul>
 * Les objets poses sont dans {@link DishShelfTile} : {@link #PER_BOARD} places par planche, planche basse = places 0..3, planche haute =
 * places 4..7 ({@link #boards}).
 */
public final class ShelfJoints {

    private ShelfJoints() {
    }

    /**
     * Une etagere voisine : facade (0..3, ordre {@code Direction.get2DDataValue}), murale, planche au ras du bloc (posee sur une autre),
     * etagere empilee dessus (1.0.3-beta lot 14 : meme role a ce niveau).
     */
    public record Cell(int facing, boolean wall, boolean down, boolean up) {

        public Cell(int facing, boolean wall, boolean down) {
            this(facing, wall, down, false);
        }
    }

    /** Une planche qui porte des objets : dessus (px), milieu en profondeur (px, repere du modele), premiere place dans la tuile. */
    public record Board(float top, float mid, int first) {
    }

    public static final int UP = 8, DOWN = 4, LEFT = 2, RIGHT = 1;
    /** Places par planche (quatre en ligne). */
    public static final int PER_BOARD = 4;
    /** Places de la tuile : planche basse puis planche haute (une murale n'utilise que la premiere). */
    public static final int CAPACITY = 2 * PER_BOARD;

    // ------------------------------------------------------------------ geometrie (px, repere du modele : avant au nord, z = 0)
    /** Epaisseur des planches au sol. */
    public static final float BOARD = 1.5F;
    /** Dessous de la planche basse : sur pattes (module pose au sol) / au ras du bloc (module empile ; 0,05 px : hors du plan des semelles des montants). */
    public static final float LOW = 3.0F, BASE = 0.05F;
    /** Planche haute du module du sommet : dessus a 15,9 px (les montants depassent de 0,1 px : pas de faces confondues). */
    public static final float TOP = 15.9F;
    /** Profondeur des planches au sol (avant, arriere) et milieu. */
    public static final float Z0 = 3.5F, Z1 = 15.5F, MID = 9.5F;
    /** Murale : dessus de la planche, milieu en profondeur (memes valeurs qu'avant le lot 12). */
    public static final float WALL_TOP = 7.5F, WALL_MID = 12.5F;
    /** Sous ce niveau (px) un clic vise la planche basse d'un module du sommet, au-dessus la planche haute. */
    public static final float SPLIT = 13.0F;

    /** Empilable avec {@code other} (au-dessus ou au-dessous) : meme facade, meme mode. */
    public static boolean stacks(Cell self, @Nullable Cell other) {
        return other != null && other.facing() == self.facing() && other.wall() == self.wall();
    }

    /**
     * Liee de cote a {@code other} : alignee (meme facade, meme mode) ET MEME ROLE a ce niveau : planche a la meme hauteur (meme {@code down})
     * et meme presence d'un module au-dessus (meme {@code up}, donc meme planche haute). 1.0.3-beta (lot 14, Guillaume : « bug sur les
     * etageres quand elles sont placees en escalier, c'est tres laid ») : avant, seul {@code down} comptait : une colonne de 2 a cote d'une
     * seule se liait par le bas, les montants de la jonction disparaissaient, la planche haute du module seul flottait et les pieds du module
     * du dessus etaient coupes. Maintenant chaque module garde ses montants quand les roles different.
     */
    public static boolean beside(Cell self, @Nullable Cell other) {
        return stacks(self, other) && other.down() == self.down() && (self.wall() || other.up() == self.up());
    }

    /**
     * Liaisons de l'etagere {@code self} d'apres ses quatre voisines (null = pas d'etagere) ; {@code left} / {@code right} dans le repere du
     * modele (gauche = sens antihoraire de la facade). Le {@code down} et le {@code up} de {@code self} sont recalcules d'abord (hauteur de sa
     * planche, planche haute ou non) ; ceux des voisines de cote doivent etre ceux du monde ({@code DishShelfBlock.connect} les recalcule
     * d'apres leurs propres voisines du dessus et du dessous). Une murale ne s'empile pas (pas de UP / DOWN).
     */
    public static int links(Cell self, @Nullable Cell up, @Nullable Cell down, @Nullable Cell left, @Nullable Cell right) {
        boolean d = !self.wall() && stacks(self, down);
        boolean u = !self.wall() && stacks(self, up);
        Cell me = new Cell(self.facing(), self.wall(), d, u);
        int out = 0;
        if (u) {
            out |= UP;
        }
        if (d) {
            out |= DOWN;
        }
        if (beside(me, left)) {
            out |= LEFT;
        }
        if (beside(me, right)) {
            out |= RIGHT;
        }
        return out;
    }

    /**
     * 1.0.3-beta (lot 12) : equerre au milieu d'une murale au coeur d'une longue rangee : liee des deux cotes, ses deux voisines aussi liees
     * plus loin (au moins deux murales de chaque cote), et une case sur trois le long de la rangee ({@code along} = x ou z du monde) : une
     * rangee de 7 et plus en a toujours, une de 3 ou 4 jamais.
     */
    public static boolean midBracket(boolean left, boolean right, boolean leftFar, boolean rightFar, int along) {
        return left && right && leftFar && rightFar && Math.floorMod(along, 3) == 0;
    }

    /** Planches qui portent des objets : murale une ; au sol la planche basse (sur pattes ou au ras du bloc) et, au sommet, la haute. */
    public static List<Board> boards(boolean wall, boolean up, boolean down) {
        if (wall) {
            return List.of(new Board(WALL_TOP, WALL_MID, 0));
        }
        List<Board> out = new ArrayList<>(2);
        out.add(new Board((down ? BASE : LOW) + BOARD, MID, 0));
        if (!up) {
            out.add(new Board(TOP, MID, PER_BOARD));
        }
        return Collections.unmodifiableList(out);
    }

    /** Planche visee par un clic a la hauteur {@code y} (px du bloc) : index dans {@link #boards}. */
    public static int boardAt(boolean wall, boolean up, double y) {
        return wall || up || y < SPLIT ? 0 : 1;
    }

    /**
     * L'etagere tient-elle ? Murale : il lui faut toujours son mur ; au sol : un sol solide ou une etagere au sol alignee dessous.
     */
    public static boolean survives(Cell self, @Nullable Cell below, boolean floorSturdy, boolean wallSturdy) {
        return self.wall() ? wallSturdy : floorSturdy || stacks(self, below);
    }
}
