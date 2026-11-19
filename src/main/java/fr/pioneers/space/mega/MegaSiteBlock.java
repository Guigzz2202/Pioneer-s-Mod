package fr.pioneers.space.mega;

import fr.pioneers.space.mega.client.MegaClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 4) : le CHANTIER DE MEGASTRUCTURE. Ne se pose que sur un astre qui a sa megastructure ; le premier
 * pose ouvre le chantier (son poseur est affiche comme ouvreur), les suivants sont d'autres points de livraison du meme chantier commun.
 * Clic droit : l'ecran du chantier (liste d'ingredients, avancement, bouton « Livrer depuis l'inventaire »).
 */
public class MegaSiteBlock extends Block implements EntityBlock {

    public MegaSiteBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        if (Megastructures.here(context.getLevel(), context.getClickedPos()) < 0) {
            if (!context.getLevel().isClientSide && context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable("mega.pioneers.none_here"), true);
            }
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer,
          @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        int i = Megastructures.here(level, pos);
        if (!level.isClientSide && i >= 0 && placer instanceof ServerPlayer player) {
            MegaWorld.get(player.server).open(i, player);
        }
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        int i = Megastructures.here(level, pos);
        if (i < 0) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("mega.pioneers.none_here"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MegaClientHooks.openSite(pos, i));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Livre au chantier tout ce qui lui manque encore dans l'inventaire du joueur (bouton de l'ecran). */
    static void deliverFrom(ServerPlayer player, BlockPos pos) {
        int i = Megastructures.here(player.level(), pos);
        if (i < 0) {
            return;
        }
        MegaWorld world = MegaWorld.get(player.server);
        if (world.unlocked(i)) {
            player.displayClientMessage(Component.translatable("mega.pioneers.already"), true);
            return;
        }
        world.open(i, player);
        int total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && !world.unlocked(i); slot++) {
            ItemStack stack = inventory.getItem(slot);
            int taken = world.accept(player.server, i, stack, false);
            if (taken > 0) {
                stack.shrink(taken);
                total += taken;
            }
        }
        if (total > 0) {
            inventory.setChanged();
        }
        player.displayClientMessage(total > 0 ? Component.translatable("mega.pioneers.delivered", total)
              : Component.translatable("mega.pioneers.nothing"), true);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new MegaSiteTile(pos, state);
    }
}
