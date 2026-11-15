package fr.pioneers.client.model;

import fr.pioneers.content.spacesim.SpaceSimPart;
import fr.pioneers.content.spacesim.SpaceSimPartBlock;
import fr.pioneers.content.stabilizer.StabilizerPart;
import fr.pioneers.content.stabilizer.StabilizerPartBlock;
import fr.pioneers.core.Directions;
import fr.pioneers.registries.PiCosmicBlocks;
import fr.pioneers.registries.PiRobotics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Logique des textures connectees (methode "compacte" de CTM).
 * <p>
 * Chaque face est decoupee en 4 quarts. Pour chaque quart on regarde le voisin horizontal, le voisin vertical et le voisin
 * en diagonale (dans le plan de la face), puis on choisit l'une des 5 tuiles :
 * <ul>
 *     <li>{@link #NONE} : aucun voisin, bordure complete ;</li>
 *     <li>{@link #HORIZONTAL} : voisin horizontal seulement, bordures haut/bas ;</li>
 *     <li>{@link #VERTICAL} : voisin vertical seulement, bordures gauche/droite ;</li>
 *     <li>{@link #INNER} : voisins horizontal et vertical mais pas en diagonale, petit coin rentrant ;</li>
 *     <li>{@link #FULL} : entoure de partout, aucune bordure.</li>
 * </ul>
 * Chaque quart affiche le quart correspondant de la tuile choisie.
 */
public final class ConnectedTextures {

    private ConnectedTextures() {
    }

    public static final int NONE = 0;
    public static final int HORIZONTAL = 1;
    public static final int VERTICAL = 2;
    public static final int INNER = 3;
    public static final int FULL = 4;
    public static final String[] TILE_NAMES = {"none", "h", "v", "inner", "full"};

    /**
     * Famille de connexion d'un bloc : les boitiers, ports, ports d'alimentation et la matrice laser se connectent entre eux (1),
     * le verre seulement avec le verre (2) ; idem pour la Chambre stellaire (3 et 4), le Simulateur spatial (5 coque, 6 verre,
     * 12 bobines), l'absorbeur solaire (7 et 8 pour les capteurs) et le hub robotique (11).
     * 0 = ne se connecte a rien.
     */
    public static int group(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof StabilizerPartBlock part) {
            //Stabilisateur spatial : paroi (1), verre (2)
            return part.part == StabilizerPart.GLASS ? 2 : 1;
        } else if (block instanceof SpaceSimPartBlock part && part.part != SpaceSimPart.PROJECTOR) {
            //Simulateur spatial : coque (5), verre (6), anneau de bobines (12)
            return switch (part.part) {
                case GLASS -> 6;
                case COIL -> 12;
                default -> 5;
            };
        } else if (block == PiCosmicBlocks.ABSORBER_CASING.getBlock() || block == PiCosmicBlocks.ABSORBER_PORT.getBlock()
                   || block == PiCosmicBlocks.ABSORBER_CONTROLLER.getBlock()) {
            return 7;
        } else if (block == PiCosmicBlocks.ABSORBER_COLLECTOR.getBlock()) {
            return 8;
        } else if (block == PiRobotics.CASING.getBlock() || block == PiRobotics.PORT.getBlock() || block == PiRobotics.BAY.getBlock()) {
            return 11;
        }
        return 0;
    }

    /** Axe "horizontal" de la texture (u) pour une face, tel que Minecraft applique les UV par defaut. */
    public static Direction.Axis uAxis(Direction face) {
        return face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    /** Axe "vertical" de la texture (v) pour une face. */
    public static Direction.Axis vAxis(Direction face) {
        return face.getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
    }

    /** Signe du quart sur l'axe u : -1 pour la moitie basse des coordonnees, +1 pour la haute. */
    public static int uSign(int quadrant) {
        return (quadrant & 1) == 0 ? -1 : 1;
    }

    public static int vSign(int quadrant) {
        return (quadrant & 2) == 0 ? -1 : 1;
    }

    private static BlockPos offset(BlockPos pos, Direction.Axis axis, int sign) {
        return pos.relative(Direction.fromAxisAndDirection(axis, sign > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE));
    }

    /**
     * Calcule la tuile de chaque quart de chaque face. Resultat : 6 faces x 4 quarts, indexe par {@code face.get3DDataValue() * 4 + quart}.
     */
    public static byte[] compute(BlockAndTintGetter level, BlockPos pos) {
        byte[] result = new byte[24];
        int group = group(level.getBlockState(pos));
        for (Direction face : Directions.ALL) {
            Direction.Axis u = uAxis(face), v = vAxis(face);
            for (int q = 0; q < 4; q++) {
                BlockPos uPos = offset(pos, u, uSign(q));
                BlockPos vPos = offset(pos, v, vSign(q));
                BlockPos dPos = offset(uPos, v, vSign(q));
                boolean cu = group != 0 && group(level.getBlockState(uPos)) == group;
                boolean cv = group != 0 && group(level.getBlockState(vPos)) == group;
                int tile;
                if (!cu && !cv) {
                    tile = NONE;
                } else if (cu && !cv) {
                    tile = HORIZONTAL;
                } else if (!cu) {
                    tile = VERTICAL;
                } else if (group(level.getBlockState(dPos)) != group) {
                    tile = INNER;
                } else {
                    tile = FULL;
                }
                result[face.get3DDataValue() * 4 + q] = (byte) tile;
            }
        }
        return result;
    }
}
