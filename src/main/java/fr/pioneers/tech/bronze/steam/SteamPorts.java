package fr.pioneers.tech.bronze.steam;

import fr.pioneers.logistics.transport.logistic.ChannelKind;
import fr.pioneers.logistics.transport.logistic.LogisticCableBlock;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 0.20.44 (Guillaume : « les modeles se connectent pas tres bien aux tuyaux de cuivre, ca fait sale ») : brides de vapeur des blocs de l'age
 * du bronze. Chaque face de vapeur ({@link Faces}) ou se branche un cable a gaz (sa fiche tournee vers le bloc) affiche une bride de laiton
 * et un piquage de cuivre jusqu'au corps de la machine : la conduite s'y boulonne bride contre bride. Proprietes d'etat {@code north..down}
 * (directions du monde), tenues a jour a la pose et a chaque changement de voisin ; modeles {@code steam_port_*} en multipart.
 */
public final class SteamPorts {

    public static final Map<Direction, BooleanProperty> PORTS = PipeBlock.PROPERTY_BY_DIRECTION;

    private SteamPorts() {
    }

    /** Faces par lesquelles le bloc prend la vapeur (selon son etat : orientation...). */
    @FunctionalInterface
    public interface Faces {

        boolean steam(BlockState state, Direction side);
    }

    public static void add(StateDefinition.Builder<Block, BlockState> builder) {
        for (BooleanProperty p : PORTS.values()) {
            builder.add(p);
        }
    }

    public static BlockState defaults(BlockState state) {
        for (BooleanProperty p : PORTS.values()) {
            state = state.setValue(p, false);
        }
        return state;
    }

    /**
     * Le voisin {@code neighbour} (du cote {@code side}) est-il un cable logistique qui porte le gaz (bronze et mieux) et dont la fiche se
     * branche sur ce bloc ? (1.11 : avant, les cables a gaz)
     */
    public static boolean plugged(BlockState neighbour, Direction side) {
        return neighbour.getBlock() instanceof LogisticCableBlock cable && cable.level.permits(ChannelKind.GAS)
               && LogisticCableBlock.link(neighbour, side.getOpposite()) == LogisticCableBlock.Link.PLUG;
    }

    /** Toutes les brides d'apres les voisins (pose). */
    public static BlockState refresh(BlockState state, LevelAccessor level, BlockPos pos, Faces faces) {
        for (Map.Entry<Direction, BooleanProperty> e : PORTS.entrySet()) {
            Direction d = e.getKey();
            state = state.setValue(e.getValue(), faces.steam(state, d) && plugged(level.getBlockState(pos.relative(d)), d));
        }
        return state;
    }

    /** Une bride apres un changement du voisin {@code side}. */
    public static BlockState update(BlockState state, Direction side, BlockState neighbour, Faces faces) {
        return state.setValue(PORTS.get(side), faces.steam(state, side) && plugged(neighbour, side));
    }
}
