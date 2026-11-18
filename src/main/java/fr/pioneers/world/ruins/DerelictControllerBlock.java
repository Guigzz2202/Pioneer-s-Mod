package fr.pioneers.world.ruins;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : Controleur delabre, coeur d'une ruine (non fonctionnel). Il porte le site ({@link RuinSites}) et ouvre le projet
 * de restauration ({@link Restoration}).
 */
public class DerelictControllerBlock extends DerelictBlock implements EntityBlock {

    public DerelictControllerBlock(int tier, Properties properties) {
        super("controller", tier, properties);
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new DerelictControllerTile(pos, state);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof DerelictControllerTile tile) {
            RuinSites.Site site = tile.site(sl);
            if (site == null) {
                sp.displayClientMessage(Component.translatable("ruins.pioneers.controller.dead").withStyle(ChatFormatting.GRAY), true);
            } else {
                Restoration.open(sp, sl, site);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
