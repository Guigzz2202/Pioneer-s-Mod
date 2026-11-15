package fr.pioneers.kinetics.parts;

import fr.pioneers.kinetics.KineticBlock;
import fr.pioneers.kinetics.KineticEvents;
import fr.pioneers.kinetics.KineticTile;
import fr.pioneers.kinetics.KineticsConfig;
import fr.pioneers.registries.PiKinetics;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Manivelle : la premiere source, a la force du bras. Posee sur le bout d'un arbre (ou d'une machine), elle tourne tant que le joueur garde
 * le clic droit : environ 90 W, au prix de la faim. Seule, elle ne demarre pas une meule : il faut une demultiplication (rouet et pignon).
 */
public class HandCrankBlock extends KineticBlock {

    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.DOWN, Block.box(3, 0, 3, 13, 9, 13));
        SHAPES.put(Direction.UP, Block.box(3, 7, 3, 13, 16, 13));
        SHAPES.put(Direction.NORTH, Block.box(3, 3, 0, 13, 13, 9));
        SHAPES.put(Direction.SOUTH, Block.box(3, 3, 7, 13, 13, 16));
        SHAPES.put(Direction.WEST, Block.box(0, 3, 3, 9, 13, 13));
        SHAPES.put(Direction.EAST, Block.box(7, 3, 3, 16, 13, 13));
    }

    public HandCrankBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof Tile tile) {
            if (!level.isClientSide) {
                if (tile.cranking == 0) {
                    KineticEvents.award((ServerLevel) level, pos, "first_crank");
                }
                tile.cranking = 10;
                player.causeFoodExhaustion(0.03F);
                if (level.getGameTime() % 8 == 0) {
                    level.playSound(null, pos, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 0.7F);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    public static class Tile extends KineticTile {

        int cranking;

        /** 1.4 : aide de la fiche au regard. */
        @Override
        protected Component lookHint() {
            return Component.translatable("kinetics.pioneers.look.hint.crank");
        }

        public Tile(BlockPos pos, BlockState state) {
            super(PiKinetics.HAND_CRANK_TILE.get(), pos, state);
        }

        public Direction facing() {
            return getBlockState().getValue(FACING);
        }

        @Override
        public double shaftFactor(Direction face) {
            return face == facing() ? 1 : 0;
        }

        @Override
        public double sourceTorque() {
            return cranking > 0 ? 60 * KineticsConfig.sourcePower() : 0;
        }

        @Override
        public double sourceFreeSpeed() {
            return 60;
        }

        @Override
        public double maxTorque() {
            return 1_000;
        }

        @Override
        public double maxSpeed() {
            return 200;
        }

        /** Tourne la manivelle pendant {@code ticks} (GameTests : le joueur n'est pas la). */
        public void crank(int ticks) {
            cranking = Math.max(cranking, ticks);
        }

        @Override
        protected void serverTick(ServerLevel level) {
            if (cranking > 0) {
                cranking--;
            }
        }
    }
}
