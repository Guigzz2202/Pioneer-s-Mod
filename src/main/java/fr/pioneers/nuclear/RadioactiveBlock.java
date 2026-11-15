package fr.pioneers.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/** Bloc radioactif (0.17.0) : s'enregistre comme source dans {@link RadiationData} a la pose et s'en retire a la casse. */
public class RadioactiveBlock extends Block {

    private final byte kind;

    public RadioactiveBlock(byte kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    static void register(Level level, BlockPos pos, byte kind) {
        if (level instanceof ServerLevel server) {
            RadiationData.get(server).addBlock(pos, kind);
        }
    }

    static void unregister(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            RadiationData.get(server).removeBlock(pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!old.is(state.getBlock())) {
            register(level, pos, kind);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (!next.is(state.getBlock())) {
            unregister(level, pos);
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
