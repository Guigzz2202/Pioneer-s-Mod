package fr.pioneers.logistics.transport.trash;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 22, retour 1 de Guillaume) : la POUBELLE, un seul bloc pour toutes les ressources sauf la donnee : tout ce qui y entre, par
 * n'importe quelle face (cable logistique de tout niveau, entonnoir en bois, entonnoir, buffer, port de multibloc, goulotte d'une interface
 * de sortie, chaudiere, compresseur...) ou par son interface, disparait (puits {@link TrashSink}, tuile {@link TrashCanTile} sans etat ni
 * tick). Bac d'acier sur socle sombre, grille et broyeur en haut, une plaque de raccord a voyants par face (modele
 * {@code T:core/p105_lot22.py}). Forme : {@link #BOX} (= le modele). Clic droit : l'interface ({@link TrashCanMenu}).
 */
public class TrashCanBlock extends Block implements EntityBlock {

    /** Forme (px) : socle 0,5..15,5, bac et plaques 1..15, rebord jusqu'a 15. */
    public static final double[] BOX = {1, 0, 1, 15, 15, 15};
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0.5, 0, 0.5, 15.5, 2, 15.5), Block.box(BOX[0], BOX[1], BOX[2], BOX[3], BOX[4], BOX[5]));

    public TrashCanBlock(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new TrashCanTile(pos, state);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && level.getBlockEntity(pos) instanceof TrashCanTile tile) {
            NetworkHooks.openScreen(sp, tile, buf -> buf.writeBlockPos(pos));
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5F, 0.8F);
        }
        return InteractionResult.CONSUME;
    }
}
