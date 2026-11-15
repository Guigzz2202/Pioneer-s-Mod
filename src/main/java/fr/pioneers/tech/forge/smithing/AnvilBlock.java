package fr.pioneers.tech.forge.smithing;

import fr.pioneers.tech.forge.ForgeBlock;
import fr.pioneers.tech.forge.ForgeMetals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Enclume de la forge primaire : depuis la 1.0.2-beta (lot 5) une seule, l'enclume en fer ({@link #tier} = {@code ANVIL_IRON} : elle forge
 * tout metal dont le tier ({@link ForgeMetals#anvilTier}) est au plus le sien). Metal chauffe pose dessus + marteau en main = ecran de forge (minijeu 16 x 16, {@link AnvilTile}). Table a
 * {@link AnvilGeometry#TOP} px ; forme selon l'axe de la facade (la bigorne part vers la droite du joueur).
 */
public class AnvilBlock extends ForgeBlock {

    public final int tier;
    private final VoxelShape alongX, alongZ;

    public AnvilBlock(int tier, Properties properties) {
        super(properties, AnvilTile::new, null);
        this.tier = tier;
        if (tier == ForgeMetals.ANVIL_STONE) {
            alongX = Shapes.or(Block.box(3, 0, 3, 13, 7, 13), Block.box(1, 7, 2, 15, 12, 14));
            alongZ = Shapes.or(Block.box(3, 0, 3, 13, 7, 13), Block.box(2, 7, 1, 14, 12, 15));
        } else {
            alongX = Shapes.or(Block.box(3, 0, 4, 13, 8, 12), Block.box(0, 8, 4, 16, 12, 12));
            alongZ = Shapes.or(Block.box(4, 0, 3, 12, 8, 13), Block.box(4, 8, 0, 12, 12, 16));
        }
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        Direction f = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.SOUTH;
        return f.getAxis() == Direction.Axis.Z ? alongX : alongZ;
    }
}
