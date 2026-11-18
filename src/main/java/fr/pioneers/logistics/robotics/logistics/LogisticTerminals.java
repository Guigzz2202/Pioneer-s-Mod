package fr.pioneers.logistics.robotics.logistics;

import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.tech.industry.multi.PortTile;
import fr.pioneers.tech.industry.multi.Structures;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * MAJ Recettes (lot 7) : machines a selecteur de recette servies par le reseau robotique d'un terminal logistique (le bloc servi par chaque
 * point logistique du reseau ; un port de multibloc compte pour son controleur). Le terminal les liste avec leur verrou et le regle a
 * distance ({@code PacketRecipeLock} avec le terminal pour relais).
 */
public final class LogisticTerminals {

    /** Machines listees au plus (paquet borne). */
    public static final int MAX_MACHINES = 48;

    private LogisticTerminals() {
    }

    /** Positions des machines a selecteur servies par le reseau du terminal (chargees, sans doublon, dans l'ordre des points). */
    public static List<BlockPos> machines(TileEntityLogisticTerminal terminal) {
        Level level = terminal.getLevel();
        if (level == null || level.isClientSide) {
            return List.of();
        }
        LogisticNetwork network = LogisticsRegistry.get(level).networkAt(terminal.getBlockPos(), level.getGameTime());
        if (network == null) {
            return List.of();
        }
        Set<BlockPos> out = new LinkedHashSet<>();
        for (LogisticPoint point : network.points) {
            if (out.size() >= MAX_MACHINES) {
                break;
            }
            if (point.isPointRemoved()) {
                continue;
            }
            BlockPos at = served(level, point.endpoint().pos());
            if (at != null) {
                out.add(at.immutable());
            }
        }
        return new ArrayList<>(out);
    }

    /** La machine a selecteur servie a cette position (un port : son controleur), ou null. */
    @Nullable
    private static BlockPos served(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        BlockPos at = pos;
        if (be instanceof PortTile) {
            at = Structures.controllerAt(level, pos);
            be = at != null && level.isLoaded(at) ? level.getBlockEntity(at) : null;
        }
        return be instanceof RecipeLockable lockable && !lockable.recipeChoices().isEmpty() ? at : null;
    }

    /** La machine a selecteur a cette position si le reseau du terminal la sert, sinon null (paquet venu d'un client : rien n'est cru). */
    @Nullable
    public static RecipeLockable machine(TileEntityLogisticTerminal terminal, BlockPos pos) {
        if (!machines(terminal).contains(pos)) {
            return null;
        }
        Level level = terminal.getLevel();
        return level != null && level.getBlockEntity(pos) instanceof RecipeLockable lockable ? lockable : null;
    }
}
