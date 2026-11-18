package fr.pioneers.tech.bronze.autofoundry;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.2-beta (lot 4, Guillaume : « un systeme de conduit, qui n'a rien a voir avec les conduits logistiques, permettant d'aller couler dans
 * des blocs de moulage ») : CONDUIT DE COULEE, rigole de terre refractaire cerclee de fer posee au pied du bloc. Il se RACCORDE tout seul sur
 * les quatre cotes : a un autre conduit, ou a une cuve de fonderie ({@link FoundryPartBlock} cuve : la source) ; au-dessus d'un moule
 * automatique ({@link AutoMoldBlock}) il a un bec qui verse dedans ({@link #DOWN}). Rien ne coule tout seul : la fonderie formee choisit le
 * premier moule libre par les conduits ({@link ChannelNetwork}) et fait couler le metal le long du chemin (rendu : {@code CastingChannelRenderer}).
 */
public class CastingChannelBlock extends BaseEntityBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, SOUTH = BlockStateProperties.SOUTH, EAST = BlockStateProperties.EAST,
          WEST = BlockStateProperties.WEST, DOWN = BlockStateProperties.DOWN;
    public static final Map<Direction, BooleanProperty> SIDES = Map.of(Direction.NORTH, NORTH, Direction.SOUTH, SOUTH, Direction.EAST, EAST,
          Direction.WEST, WEST);
    /** Haut des bords de la rigole (px) et fond (px) : memes nombres que le modele ({@code T:bronze/auto_foundry.py}). */
    public static final float RIM = 5, FLOOR = 1.5F;
    private static final VoxelShape CORE = Block.box(4.5, 0, 4.5, 11.5, RIM, 11.5);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(Direction.NORTH, Block.box(4.5, 0, 0, 11.5, RIM, 4.5),
          Direction.SOUTH, Block.box(4.5, 0, 11.5, 11.5, RIM, 16), Direction.WEST, Block.box(0, 0, 4.5, 4.5, RIM, 11.5),
          Direction.EAST, Block.box(11.5, 0, 4.5, 16, RIM, 11.5));

    public CastingChannelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(NORTH, false).setValue(SOUTH, false).setValue(EAST, false).setValue(WEST, false)
              .setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, DOWN);
    }

    /** Le conduit se raccorde-t-il a ce voisin (autre conduit, cuve de fonderie) ? */
    public static boolean connects(BlockState neighbour) {
        return neighbour.getBlock() instanceof CastingChannelBlock || neighbour.getBlock() instanceof FoundryPartBlock part && part.vat;
    }

    /** Etat raccorde d'un conduit en {@code pos} (voisins lus dans le monde). */
    public BlockState connected(BlockState state, BlockGetter level, BlockPos pos) {
        for (Map.Entry<Direction, BooleanProperty> e : SIDES.entrySet()) {
            state = state.setValue(e.getValue(), connects(level.getBlockState(pos.relative(e.getKey()))));
        }
        return state.setValue(DOWN, level.getBlockState(pos.below()).getBlock() instanceof AutoMoldBlock);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return connected(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighbour, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
        if (direction == Direction.DOWN) {
            return state.setValue(DOWN, neighbour.getBlock() instanceof AutoMoldBlock);
        }
        BooleanProperty side = SIDES.get(direction);
        return side == null ? state : state.setValue(side, connects(neighbour));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        VoxelShape shape = CORE;
        for (Map.Entry<Direction, BooleanProperty> e : SIDES.entrySet()) {
            if (state.getValue(e.getValue())) {
                shape = Shapes.or(shape, ARMS.get(e.getKey()));
            }
        }
        return shape;
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new CastingChannelTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? (l, p, s, t) -> {
            if (t instanceof CastingChannelTile c) {
                c.clientTick();
            }
        } : null;
    }
}
