package fr.pioneers.farm.device;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 28, Guillaume : « Faire en sorte que le clapier soit un 2x1 en terme de bloc. On doit voir les lapins a l'interieur ») :
 * le clapier sur DEUX cases, comme un lit : case PRINCIPALE ({@link Part#MAIN}, la tuile {@link HutchTile}, la ou on clique) et case LATERALE
 * ({@link Part#SIDE}, a droite vue de face : {@link #sideOf}) ; un clic sur la laterale vaut un clic sur la principale, casser l'une casse
 * l'autre (la principale rend le clapier, la laterale rien). Les lapins loges se voient dans les deux cases
 * ({@code J:client/farm/herd/HutchRabbits}).
 * <p>
 * Mondes d'avant : un clapier 1 x 1 se charge en {@link Part#SINGLE} (valeur par defaut : etat d'avant sans « part ») ; il reste valide tel
 * quel (meme modele, memes lapins) et devient double tout seul des que la case a sa droite est libre ({@link #grow}, appele par la tuile).
 * Pose : la case laterale doit etre libre (sinon la pose est refusee, comme un lit).
 */
public class HutchBlock extends DeviceBlock {

    public enum Part implements StringRepresentable {
        SINGLE, MAIN, SIDE;

        @Override
        public @NotNull String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    public HutchBlock(Properties properties) {
        super(Kind.RABBIT_HUTCH, properties);
        registerDefaultState(defaultBlockState().setValue(PART, Part.SINGLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    /** Cote de la case laterale vu de la case principale (a droite pour qui regarde la facade). */
    public static Direction sideOf(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    /** Case principale d'une case du clapier (elle-meme si principale ou simple). */
    public static BlockPos mainOf(BlockState state, BlockPos pos) {
        return state.getValue(PART) == Part.SIDE ? pos.relative(sideOf(state).getOpposite()) : pos;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos side = context.getClickedPos().relative(facing.getCounterClockWise());
        Level level = context.getLevel();
        if (!level.getBlockState(side).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(side)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, Part.MAIN);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer,
          @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && state.getValue(PART) == Part.MAIN) {
            level.setBlock(pos.relative(sideOf(state)), state.setValue(PART, Part.SIDE), Block.UPDATE_ALL);
        }
    }

    /**
     * Ancien clapier 1 x 1 : devient double si la case a sa droite (vue de face) est libre (air ou plante remplacable, sans fluide).
     * Appele par la tuile toutes les 5 secondes ; vrai si le clapier a grandi.
     */
    public static boolean grow(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof HutchBlock) || state.getValue(PART) != Part.SINGLE) {
            return false;
        }
        BlockPos side = pos.relative(sideOf(state));
        BlockState there = level.getBlockState(side);
        if (!level.isLoaded(side) || !there.canBeReplaced() || !there.getFluidState().isEmpty()) {
            return false;
        }
        level.setBlock(pos, state.setValue(PART, Part.MAIN), Block.UPDATE_CLIENTS);
        level.setBlock(side, state.setValue(PART, Part.SIDE), Block.UPDATE_ALL);
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        Part part = state.getValue(PART);
        if (part == Part.MAIN && dir == sideOf(state) && !(neighbor.is(this) && neighbor.getValue(PART) == Part.SIDE)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (part == Part.SIDE && dir == sideOf(state).getOpposite() && !(neighbor.is(this) && neighbor.getValue(PART) == Part.MAIN)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** Creatif : casser la case laterale ne rend rien et retire la principale sans butin (comme le haut d'un lit). */
    @Override
    public void playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) == Part.SIDE) {
            BlockPos main = mainOf(state, pos);
            BlockState ms = level.getBlockState(main);
            if (ms.is(this) && ms.getValue(PART) == Part.MAIN) {
                level.setBlock(main, Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_ALL);
                level.levelEvent(player, 2001, main, Block.getId(ms));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return state.getValue(PART) == Part.SIDE ? null : super.newBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return state.getValue(PART) == Part.SIDE ? null : super.getTicker(level, state, type);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (state.getValue(PART) == Part.SIDE) {
            BlockPos main = mainOf(state, pos);
            BlockState ms = level.getBlockState(main);
            if (ms.is(this)) {
                return super.use(ms, level, main, player, hand, hit.withPosition(main));
            }
            return InteractionResult.PASS;
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
