package fr.pioneers.transport.buffer;

import fr.pioneers.transport.CableTier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Buffer d'energie : son etat {@code charge} (0 vide .. 8 plein) choisit le modele (cellules allumees de bas en haut). Tenu a jour par
 * {@link BufferTile} quand la charge change de huitieme.
 */
public class EnergyBufferBlock extends BufferBlock {

    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 8);

    public EnergyBufferBlock(CableTier tier, Properties properties) {
        super(BufferType.ENERGY, tier, properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    public static int light(BlockState state) {
        return state.getValue(CHARGE) > 0 ? 4 : 0;
    }

    /** 0 si vide, sinon 1 (il reste de l'energie) a 8 (plein). */
    public static int level(long stored, long capacity) {
        if (stored <= 0 || capacity <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.min(8, Math.ceil(8.0 * stored / capacity)));
    }
}
