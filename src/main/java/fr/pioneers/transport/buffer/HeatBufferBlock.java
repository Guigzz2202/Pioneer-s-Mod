package fr.pioneers.transport.buffer;

import fr.pioneers.transport.CableTier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Buffer thermique : son etat {@code heat} (0 froid .. 4 incandescent) choisit le modele (ailettes, coeur et grille qui rougissent), et
 * sa lumiere. Tenu a jour par {@link BufferTile} quand la temperature franchit un seuil.
 */
public class HeatBufferBlock extends BufferBlock {

    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 4);
    /** Seuils (K) des niveaux 1 a 4 : tiede, chaud, tres chaud, incandescent. */
    public static final double[] THRESHOLDS = {330, 500, 800, 1_300};
    /** Marge autour des seuils (evite le clignotement d'un niveau a l'autre). */
    public static final double HYSTERESIS = 10;

    public HeatBufferBlock(CableTier tier, Properties properties) {
        super(BufferType.HEAT, tier, properties);
        registerDefaultState(stateDefinition.any().setValue(HEAT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEAT);
    }

    public static int light(BlockState state) {
        return switch (state.getValue(HEAT)) {
            case 4 -> 12;
            case 3 -> 7;
            default -> 0;
        };
    }

    /** Niveau pour une temperature, en partant du niveau actuel (on ne change qu'en depassant le seuil de la marge). */
    public static int level(int current, double temperature) {
        int level = current;
        while (level < 4 && temperature >= THRESHOLDS[level] + HYSTERESIS) {
            level++;
        }
        while (level > 0 && temperature < THRESHOLDS[level - 1] - HYSTERESIS) {
            level--;
        }
        return level;
    }
}
