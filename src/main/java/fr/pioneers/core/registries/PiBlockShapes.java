package fr.pioneers.core.registries;

import fr.pioneers.core.block.BlockShapes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Formes de collision des blocs a modele 3D (orientees nord, tournees selon la facade).
 */
public final class PiBlockShapes {

    private PiBlockShapes() {
    }

    /** Tables d'assemblage d'un bloc : plateau, pieds, bras et projecteur. */
    public static final VoxelShape[] ASSEMBLY_TABLE_SHAPES = new VoxelShape[BlockShapes.HORIZONTALS.length];

    public static final VoxelShape[] RESEARCH_TABLE_SHAPES = new VoxelShape[BlockShapes.HORIZONTALS.length];

    public static final VoxelShape[] DATA_ANTENNA_SHAPES = new VoxelShape[BlockShapes.HORIZONTALS.length];
    private static final VoxelShape DATA_ANTENNA_SHAPE = BlockShapes.combine(
          Block.box(0, 0, 0, 16, 14, 16), //caisson et plateforme
          Block.box(5, 14, 5, 11, 46, 11) //mat (la parabole tourne au-dessus)
    );

    /** Machines d'analyse : paillasse de laboratoire et instrument pose dessus. */
    public static final VoxelShape[] ANALYZER_SHAPES = new VoxelShape[BlockShapes.HORIZONTALS.length];
    /** Analyseur de biomes : baie de cellules. */
    public static final VoxelShape[] BIOME_ANALYZER_SHAPES = new VoxelShape[BlockShapes.HORIZONTALS.length];

    static {
        BlockShapes.setShape(DATA_ANTENNA_SHAPE, DATA_ANTENNA_SHAPES);
        BlockShapes.setShape(BlockShapes.combine(
              Block.box(0, 0, 0, 16, 12, 16), //caisson et plateau
              Block.box(0, 12, 13, 16, 26, 16), //portique arriere et ecran
              Block.box(11.5, 12, 1.5, 14.5, 21.5, 4.5) //bras ou fourreaux a outils
        ), ASSEMBLY_TABLE_SHAPES);
        BlockShapes.setShape(BlockShapes.combine(
              Block.box(0, 0, 0, 16, 9, 16), //coque
              Block.box(1.5, 9, 2, 14.5, 17, 14.5) //instruments poses dessus (spectrometre, cloche, tremie)
        ), ANALYZER_SHAPES);
        BlockShapes.setShape(Block.box(0, 0, 0, 16, 16, 16), BIOME_ANALYZER_SHAPES);
        BlockShapes.setShape(BlockShapes.combine(
              Block.box(0, 0, 2, 6, 12, 16), //caisson de rangement
              Block.box(13, 0, 2, 15, 12, 15), //pieds
              Block.box(5, 4, 13, 11, 12, 16), //boitier de donnees
              Block.box(0, 12, 0, 16, 14, 16), //plateau
              Block.box(1.5, 14, 10.4, 14.5, 25.5, 12.5), //moniteur
              Block.box(11.5, 14, 3, 14.5, 15.4, 6) //projecteur d'hologramme
        ), RESEARCH_TABLE_SHAPES);
        //1.9.2 : assembleur de fusees et table robotique devenus multiblocs (formes 3x3 retirees)
    }
}
