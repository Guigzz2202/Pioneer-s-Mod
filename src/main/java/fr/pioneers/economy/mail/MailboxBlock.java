package fr.pioneers.economy.mail;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 29) : la BOITE AUX LETTRES : une boite de tole peinte sur un poteau de bois, facade (porte, fente, plaque de laiton) vers le
 * joueur qui la pose ; drapeau rouge sur le flanc. Le joueur qui la pose en est le proprietaire (posee par un robot : le premier joueur qui
 * l'ouvre la prend). Clic droit du proprietaire : SA boite ({@link MailService#open} : 27 cases, le meme contenu dans toutes ses boites,
 * courrier compris) ; les autres apprennent a qui elle est. Seuls le proprietaire et les operateurs la cassent ; cassee, elle ne perd rien
 * (le contenu est dans la donnee du monde {@link MailData}). Le « ! » des lettres non lues est dessine pour le seul proprietaire
 * ({@code client/economy/MailboxRenderer}).
 */
public class MailboxBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Forme (facade au nord) : poteau, boite ; memes cotes que le modele ({@code tools/gc/economy/mailbox.py SHAPE}). */
    public static final double[][] BOXES = {{6, 0, 6, 10, 9, 10}, {3, 9, 1, 13, 16, 15}};
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape s = Shapes.empty();
            for (double[] b : BOXES) {
                s = Shapes.or(s, rotated(b, d));
            }
            SHAPES[d.get2DDataValue()] = s.optimize();
        }
    }

    /** Boite (facade au nord) tournee vers {@code d}. */
    static VoxelShape rotated(double[] b, Direction d) {
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        return switch (d) {
            case SOUTH -> Block.box(16 - x1, b[1], 16 - z1, 16 - x0, b[4], 16 - z0);
            case EAST -> Block.box(16 - z1, b[1], x0, 16 - z0, b[4], x1);
            case WEST -> Block.box(z0, b[1], 16 - x1, z1, b[4], 16 - x0);
            default -> Block.box(x0, b[1], z0, x1, b[4], z1);
        };
    }

    public MailboxBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
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
        return SHAPES[state.getValue(FACING).get2DDataValue()];
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
        return new MailboxTile(pos, state);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer,
          @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer sp && !(sp instanceof FakePlayer)
            && level.getBlockEntity(pos) instanceof MailboxTile tile) {
            tile.claim(sp);
            MailData.Box b = MailData.get(sp.getServer()).box(sp.getUUID());
            MailService.state(sp, b.unread);
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
        if (!(player instanceof ServerPlayer sp) || sp instanceof FakePlayer || !(level.getBlockEntity(pos) instanceof MailboxTile tile)) {
            return InteractionResult.CONSUME;
        }
        if (tile.owner() == null) {
            tile.claim(sp);
        }
        if (!sp.getUUID().equals(tile.owner())) {
            sp.displayClientMessage(Component.translatable("mail.pioneers.msg.owned_by", tile.ownerName()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        MailService.open(sp, pos);
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ protection
    private static boolean mayBreak(BlockGetter level, BlockPos pos, Player player) {
        return !(level.getBlockEntity(pos) instanceof MailboxTile tile) || tile.owner() == null || player.getUUID().equals(tile.owner())
               || player.hasPermissions(2);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return mayBreak(level, pos, player) ? super.getDestroyProgress(state, player, level, pos) : 0;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!mayBreak(level, pos, player)) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof MailboxTile tile) {
                player.displayClientMessage(Component.translatable("mail.pioneers.msg.owned_by", tile.ownerName()).withStyle(ChatFormatting.RED),
                      true);
            }
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }
}
