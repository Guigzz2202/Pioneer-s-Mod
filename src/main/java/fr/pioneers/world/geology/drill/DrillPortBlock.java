package fr.pioneers.world.geology.drill;

import fr.pioneers.tech.bronze.SteamBronzeBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

/** Prise de la foreuse : un clic ouvre l'interface de la foreuse. */
public class DrillPortBlock extends SteamBronzeBlock {

    public DrillPortBlock(Properties properties) {
        super(properties, DrillPortTile::new, null, (s, side) -> true);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof DrillPortTile port) {
            SteamDrillTile drill = port.drill();
            if (drill != null) {
                NetworkHooks.openScreen(sp, drill, drill.getBlockPos());
            } else {
                sp.displayClientMessage(Component.translatable("geology.pioneers.drill_unformed")
                      .withStyle(ChatFormatting.RED), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
