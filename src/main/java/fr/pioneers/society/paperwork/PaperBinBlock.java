package fr.pioneers.society.paperwork;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 16) : un BAC A PAPIERS pose au sol, facade vers le joueur qui le pose (le bac a lettres : caisse basse de chene a deux
 * compartiments ; le bac d'archives : fichier de noyer a coins de fer et fiches dressees). Le poseur en est le proprietaire ; son clan y a
 * acces ; les autres apprennent a qui il est. Clic droit : l'ecran du bac ({@link PaperBinMenu}). Maj + clic droit main vide sur le bac a
 * lettres : releve la boite aux lettres du joueur (lettres et factures rangees d'un coup). Casse : le contenu tombe (comme un coffre), par le
 * proprietaire, son clan ou un operateur seulement. Formes : {@link #BOXES} (= modeles, {@code T:core/p105_lot16_bins.py SHAPES}).
 */
public class PaperBinBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Formes (facade au nord) : bac a lettres, bac d'archives ; memes cotes que les modeles. */
    public static final double[][] BOXES = {{1, 0, 2, 15, 8, 14}, {1, 0, 1, 15, 10, 15}};
    public final PaperBinKind kind;
    private final VoxelShape[] shapes = new VoxelShape[4];

    public PaperBinBlock(PaperBinKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        double[] b = BOXES[kind.ordinal()];
        for (Direction d : Direction.Plane.HORIZONTAL) {
            double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
            shapes[d.get2DDataValue()] = switch (d) {
                case SOUTH -> Block.box(16 - x1, b[1], 16 - z1, 16 - x0, b[4], 16 - z0);
                case EAST -> Block.box(16 - z1, b[1], x0, 16 - z0, b[4], x1);
                case WEST -> Block.box(z0, b[1], 16 - x1, z1, b[4], 16 - x0);
                default -> Block.box(x0, b[1], z0, x1, b[4], z1);
            };
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        VoxelShape s = shapes[state.getValue(FACING).get2DDataValue()];
        return s == null ? Shapes.block() : s;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(@NotNull BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(@NotNull BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new PaperBinTile(pos, state);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer,
          @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && level.getBlockEntity(pos) instanceof PaperBinTile t) {
            t.claim(sp);
        }
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || sp instanceof FakePlayer || !(level.getBlockEntity(pos) instanceof PaperBinTile tile)) {
            return InteractionResult.CONSUME;
        }
        if (tile.owner() == null) {
            tile.claim(sp);
        }
        if (!tile.mayUse(sp)) {
            sp.displayClientMessage(Component.translatable("paperwork.pioneers.msg.owned_by", tile.ownerName()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (kind == PaperBinKind.LETTERS && sp.isShiftKeyDown() && sp.getItemInHand(hand).isEmpty()) {
            int n = PaperBinMenu.fromMailbox(sp, tile.items);
            sp.displayClientMessage(Component.translatable(n > 0 ? "paperwork.pioneers.msg.collected" : "paperwork.pioneers.msg.nothing", n)
                  .withStyle(n > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen(sp, tile, buf -> buf.writeByte(kind.ordinal()));
        level.playSound(null, pos, fr.pioneers.core.profile.RpSounds.PAGE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.0F);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof PaperBinTile tile) {
            Containers.dropContents(level, pos, tile.items);
        }
        super.onRemove(state, level, pos, next, moving);
    }

    // ------------------------------------------------------------------ protection : proprietaire, son clan, operateurs
    private static boolean mayBreak(BlockGetter level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof PaperBinTile tile) || tile.owner() == null) {
            return true;
        }
        return player instanceof ServerPlayer sp ? tile.mayUse(sp) : player.getUUID().equals(tile.owner()) || player.hasPermissions(2);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return mayBreak(level, pos, player) ? super.getDestroyProgress(state, player, level, pos) : 0;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!mayBreak(level, pos, player)) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof PaperBinTile tile) {
                player.displayClientMessage(Component.translatable("paperwork.pioneers.msg.owned_by", tile.ownerName()).withStyle(ChatFormatting.RED),
                      true);
            }
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }
}
