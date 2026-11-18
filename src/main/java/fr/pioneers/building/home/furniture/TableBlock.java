package fr.pioneers.building.home.furniture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Patch 1.0.3-beta, lot 7 (Guillaume : « des tables [...] pieds qui se connectent ») : table d'un bois. Deux tables voisines (meme bois) se
 * relient ({@link #NORTH} ... {@link #WEST}) : le plateau devient continu, la ceinture disparait entre elles et un pied n'est dessine qu'a un
 * angle LIBRE (ni voisin d'un cote ni de l'autre) : une grande table de plusieurs blocs n'a de pieds qu'a ses angles. Etats recalcules quand
 * un voisin change. Modeles : {@code tools/gc/home/furniture.py} (multipart : plateau, ceinture par cote libre, pied par angle libre).
 */
public class TableBlock extends Block {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST, SOUTH = BlockStateProperties.SOUTH,
          WEST = BlockStateProperties.WEST;
    /** Plateau : dessus a 16 px, epaisseur 2 px. */
    public static final double TOP = 14;
    private static final VoxelShape[] SHAPES = new VoxelShape[16];

    public TableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    /** Une table voisine du meme bois relie ce cote. */
    public boolean joins(BlockState other) {
        return other.is(this);
    }

    public BlockState connect(BlockState s, BlockGetter level, BlockPos pos) {
        return s.setValue(NORTH, joins(level.getBlockState(pos.north()))).setValue(EAST, joins(level.getBlockState(pos.east())))
              .setValue(SOUTH, joins(level.getBlockState(pos.south()))).setValue(WEST, joins(level.getBlockState(pos.west())));
    }

    /** Pied a l'angle (dx, dz) = (-1|1, -1|1) : seulement si aucun des deux cotes de l'angle n'est relie (logique pure, lue par les GameTests). */
    public static boolean leg(BlockState s, int dx, int dz) {
        boolean x = dx < 0 ? s.getValue(WEST) : s.getValue(EAST), z = dz < 0 ? s.getValue(NORTH) : s.getValue(SOUTH);
        return !x && !z;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return connect(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @NotNull
    @Override
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos otherPos) {
        return dir.getAxis().isHorizontal() ? connect(state, level, pos) : state;
    }

    private static int key(BlockState s) {
        return (s.getValue(NORTH) ? 1 : 0) | (s.getValue(EAST) ? 2 : 0) | (s.getValue(SOUTH) ? 4 : 0) | (s.getValue(WEST) ? 8 : 0);
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        int k = key(state);
        VoxelShape s = SHAPES[k];
        if (s == null) {
            s = Block.box(0, TOP, 0, 16, 16, 16);
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    if (leg(state, dx, dz)) {
                        double x0 = dx < 0 ? 1 : 13, z0 = dz < 0 ? 1 : 13;
                        s = Shapes.or(s, Block.box(x0, 0, z0, x0 + 2, TOP, z0 + 2));
                    }
                }
            }
            SHAPES[k] = s;
        }
        return s;
    }

    @Override
    public boolean isPathfindable(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull PathComputationType type) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }
}
