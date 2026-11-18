package fr.pioneers.tech.forge.smithing;

import net.minecraft.core.Direction;

/**
 * Place de la piece sur l'enclume (repere local du modele dessine facade au sud, en px) : grille 16 x 16 de cases de {@link #CELL} px, coin
 * ({@link #X0}, {@link #Z0}), posee a la hauteur {@link #TOP}. Rangee z = 0 de l'ecran = cote du fond (loin du joueur). Partage par le
 * rendu (client), les particules (serveur) et le generateur des modeles ({@code tools/gc/forge/smithing.py}, memes chiffres).
 */
public final class AnvilGeometry {

    public static final float CELL = 0.5F;
    public static final float X0 = 3.5F, Z0 = 4.0F;
    /** Dessus de la table de l'enclume (px). */
    public static final float TOP = 12F;
    /** Relief d'un pixel de matiere (px). */
    public static final float RELIEF = 0.6F;

    private AnvilGeometry() {
    }

    /** x monde (px dans le bloc) d'un point local, selon la facade (meme rotation que les etats de bloc). */
    public static double worldX(Direction facing, double x, double z) {
        return switch (facing) {
            case WEST -> 16 - z;
            case NORTH -> 16 - x;
            case EAST -> z;
            default -> x;
        };
    }

    public static double worldZ(Direction facing, double x, double z) {
        return switch (facing) {
            case WEST -> x;
            case NORTH -> 16 - z;
            case EAST -> 16 - x;
            default -> z;
        };
    }
}
