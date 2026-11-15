package fr.pioneers.data;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.transport.logistic.ChannelKind;
import fr.pioneers.transport.logistic.LogisticCableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Liaisons des faces de donnees : cable logistique (MKI ou mieux) branche ou machine a donnees accolee. */
public final class DataLinks {

    private DataLinks() {
    }

    /** Quelque chose recoit les donnees par cette face : un cable branche, ou une machine a donnees accolee. */
    public static boolean isLinked(Level level, BlockPos pos, Direction side) {
        BlockPos next = pos.relative(side);
        if (!level.isLoaded(next)) {
            return false;
        }
        BlockState state = level.getBlockState(next);
        if (state.getBlock() instanceof LogisticCableBlock cable) {
            return cable.level.permits(ChannelKind.DATA) && LogisticCableBlock.link(state, side.getOpposite()) == LogisticCableBlock.Link.PLUG;
        }
        BlockEntity neighbor = level.getBlockEntity(next);
        return neighbor != null && neighbor.getCapability(PioneersAPI.DATA_HANDLER, side.getOpposite()).isPresent();
    }
}
