package fr.pioneers.society.economy.stall;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.3-beta (lot 11, Guillaume : « augmenter la hauteur de l'etal (2 blocs de haut), rendre configurable la couleur secondaire de son toit
 * (blanc etant la principale) ») : ETAL de 2 blocs. Le bas ({@link #HALF} lower) porte la tuile, le modele complet (plateau, fond, montants
 * et toit raye, T:village/blocks.py) et le butin ; le haut est un bloc fantome sans modele (forme du toit) qui renvoie clic, casse et
 * protection au bas. Le haut tombe quand le bas disparait ; casser le haut casse l'etal (le stock revient a son proprietaire comme avant).
 * <p>
 * Toit : rayures blanches + {@link #COLOR} (16 colorants ; rouge = la toile d'avant) ; clic droit avec un colorant par qui peut gerer l'etal
 * (proprietaire, operateur) : les deux moities changent, un colorant est consomme. La couleur est un etat du bloc : sauvee, synchronisee, et
 * copiee dans l'objet a la casse (butin {@code copy_state} -> {@code BlockStateTag}, reposee telle quelle).
 * <p>
 * Etals poses avant (un seul bloc) : {@link StallTile#onLoad} programme un tick ; le haut est pose si la place est libre, sinon l'etal reste
 * valide tel quel (son toit depasse dans le bloc du dessus).
 */
public class TallStallBlock extends StallBlock {

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    /** Couleur d'avant la 1.0.3-beta (toile blanc casse et rouge brique). */
    public static final DyeColor DEFAULT_COLOR = DyeColor.RED;
    private static final VoxelShape[] UPPER = new VoxelShape[4];

    public TallStallBlock(Properties properties) {
        super(false, properties);
        registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER).setValue(COLOR, DEFAULT_COLOR));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF, COLOR);
    }

    public static boolean upper(BlockState state) {
        return state.hasProperty(HALF) && state.getValue(HALF) == DoubleBlockHalf.UPPER;
    }

    @Override
    protected BlockPos main(BlockState state, BlockPos pos) {
        return upper(state) ? pos.below() : pos;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return upper(state) ? null : super.newBlockEntity(pos, state);
    }

    // ------------------------------------------------------------------ pose, haut, casse
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return null;
        }
        BlockState s = super.getStateForPlacement(ctx);
        return s == null ? null : s.setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!upper(state)) {
            level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        }
    }

    /** Etal d'avant (un bloc) : pose son haut si la place est libre (programme par {@link StallTile#onLoad}). */
    @Override
    @SuppressWarnings("deprecation")
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (upper(state)) {
            return;
        }
        BlockPos up = pos.above();
        BlockState above = level.getBlockState(up);
        if (above.is(this) && upper(above)) {
            if (above.getValue(COLOR) != state.getValue(COLOR) || above.getValue(FACING) != state.getValue(FACING)) {
                level.setBlock(up, state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
            }
        } else if (above.isAir() && up.getY() < level.getMaxBuildHeight()) {
            level.setBlock(up, state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos otherPos) {
        if (upper(state) && dir == Direction.DOWN && !(other.is(this) && !upper(other))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, other, level, pos, otherPos);
    }

    /** Casser le haut casse l'etal entier (meme regles que le bas : protection, stock rendu au proprietaire, butin du bas). */
    @Override
    public void playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
        if (upper(state) && !level.isClientSide) {
            BlockPos below = pos.below();
            BlockState low = level.getBlockState(below);
            if (low.is(this) && !upper(low)) {
                super.playerWillDestroy(level, below, low, player);
                if (player.isCreative()) {
                    level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, 2001, below, Block.getId(low));
                } else {
                    level.destroyBlock(below, true, player);
                }
            }
            return;
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull PushReaction getPistonPushReaction(@NotNull BlockState state) {
        return PushReaction.BLOCK;
    }

    // ------------------------------------------------------------------ toit
    @Override
    @SuppressWarnings("deprecation")
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof DyeItem dye) {
            BlockPos low = main(state, pos);
            if (level.getBlockEntity(low) instanceof StallTile tile && tile.canManage(player)) {
                if (!level.isClientSide) {
                    dye(level, low, dye.getDyeColor());
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    level.playSound(null, low.above(), SoundEvents.DYE_USE, SoundSource.BLOCKS, 1F, 1F);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    /** Teint le toit de l'etal dont le bas est en {@code low} (les deux moities). Vrai si la couleur a change. */
    public static boolean dye(Level level, BlockPos low, DyeColor color) {
        BlockState s = level.getBlockState(low);
        if (!(s.getBlock() instanceof TallStallBlock) || upper(s) || s.getValue(COLOR) == color) {
            return false;
        }
        level.setBlock(low, s.setValue(COLOR, color), Block.UPDATE_ALL);
        BlockState up = level.getBlockState(low.above());
        if (up.is(s.getBlock()) && upper(up)) {
            level.setBlock(low.above(), up.setValue(COLOR, color), Block.UPDATE_ALL);
        }
        return true;
    }

    // ------------------------------------------------------------------ formes
    @Override
    @SuppressWarnings("deprecation")
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        if (!upper(state)) {
            return super.getShape(state, level, pos, ctx);
        }
        int q = state.getValue(FACING).get2DDataValue();
        VoxelShape s = UPPER[q];
        if (s == null) {
            //repere du modele (facade au nord) : montants avant (jusqu'a y 8,9) et arriere (14,2), toile penchee de y 8,6 (avant) a 15,5
            Direction f = state.getValue(FACING);
            s = Shapes.or(box(f, 0, 0, 0.75, 1, 9, 1.75), box(f, 15, 0, 0.75, 16, 9, 1.75), box(f, 0, 0, 13.5, 1, 14.2, 14.5),
                  box(f, 15, 0, 13.5, 16, 14.2, 14.5), box(f, 0, 8.5, 0, 16, 11, 6), box(f, 0, 10.5, 5, 16, 13.5, 11), box(f, 0, 13, 10, 16, 15.5, 16));
            UPPER[q] = s;
        }
        return s;
    }

    /** Boite du repere du modele (facade au nord) tournee vers {@code f} (meme rotation que les etats : nord 0, est 90...). */
    private static VoxelShape box(Direction f, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (f) {
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    // ------------------------------------------------------------------ objet
    /** Infobulle : couleur du toit gardee dans l'objet ({@code BlockStateTag.color}). */
    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable BlockGetter level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        CompoundTag tag = stack.getTagElement("BlockStateTag");
        DyeColor c = DEFAULT_COLOR;
        if (tag != null && tag.contains("color")) {
            DyeColor read = DyeColor.byName(tag.getString("color"), null);
            c = read != null ? read : c;
        }
        Tooltips.of(tooltip).stat("stall.pioneers.roof", Component.translatable("color.minecraft." + c.getName()));
    }
}
