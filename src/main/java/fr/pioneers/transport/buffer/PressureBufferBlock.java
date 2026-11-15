package fr.pioneers.transport.buffer;

import fr.pioneers.transport.CableTier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Reservoir d'air comprime : son etat {@code pressure} (0 ambiante .. 8 pression maximale) choisit le modele (aiguille du manometre,
 * voyants). Tenu a jour par {@link BufferTile} quand la pression change de huitieme.
 */
public class PressureBufferBlock extends BufferBlock {

    public static final IntegerProperty PRESSURE = IntegerProperty.create("pressure", 0, 8);

    public PressureBufferBlock(CableTier tier, Properties properties) {
        super(BufferType.PRESSURE, tier, properties);
        registerDefaultState(stateDefinition.any().setValue(PRESSURE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PRESSURE);
    }

    /** 0 a pression ambiante, sinon 1 (un peu d'air) a 8 (pression maximale). */
    public static int level(double ratio) {
        if (ratio <= 1e-4) {
            return 0;
        }
        return (int) Math.max(1, Math.min(8, Math.ceil(8 * ratio - 1e-9)));
    }
}
