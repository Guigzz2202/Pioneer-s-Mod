package fr.pioneers.bronze.bench;

import fr.pioneers.bronze.BronzeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;

/** Un atelier specialise ({@link BenchType}) : plateau de travail, outils poses, choix de la piece. */
public class BenchBlock extends BronzeBlock {

    public final BenchType type;

    public BenchBlock(BenchType type, Properties properties) {
        super(properties, BenchTile::new, Shapes.or(Block.box(0, 12, 0, 16, 16, 16), Block.box(1, 0, 1, 15, 12, 15)));
        this.type = type;
    }
}
