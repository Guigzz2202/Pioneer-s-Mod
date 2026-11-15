package fr.pioneers.api.multiblock;

import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.PortBlock;
import fr.pioneers.tech.industry.multi.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * API Multibloc (lot 5) : CONTRAT DE PORT commun ({@code PortHost} generalise). Tout hote de ports (controleur industriel, halls
 * d'assemblage ; multiblocs maison au lot 6) prete ses capacites aux ports de sa structure de la meme facon : le port cherche son hote par
 * {@link #find} (registre des structures formees par monde, {@link Structures}), puis lui demande le gestionnaire de son type et de son
 * sens ({@link #portHandler}).
 */
public interface MultiblockPort {

    /**
     * Gestionnaire prete par un port de ce type et de ce sens (objet de capacite Forge), ou null si l'hote n'en a pas.
     *
     * @param port case du port (les ports de fluides des colonnes a fractions pretent la fraction de leur etage)
     */
    @Nullable
    default Object portHandler(PortBlock.Type type, boolean output, BlockPos port) {
        return null;
    }

    /**
     * Lot 8c : gestionnaire COMMUN d'un hote maison pour un port de ce type : la capacite interne de l'hote (face null : ses cases, reservoirs,
     * energie, chaleur, air, donnees, selon ce qu'il porte), ou null. Les hotes maison (bases, simulateur, absorbeur, port, chaudiere,
     * haut fourneau, foreuse...) l'utilisent pour {@link #portHandler} : un port commun ({@code PortTile}) pose dans leur structure se branche
     * comme sur un controleur industriel ; leurs ports propres (tiles de port de chaque maison) gardent leurs gestionnaires.
     */
    @Nullable
    static Object capabilityOf(net.minecraft.world.level.block.entity.BlockEntity host, PortBlock.Type type) {
        net.minecraftforge.common.capabilities.Capability<?> cap = switch (type) {
            case ITEM -> net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER;
            case FLUID -> net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER;
            case ENERGY -> net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY;
            case GAS -> fr.pioneers.api.PioneersAPI.GAS_HANDLER;
            case HEAT -> fr.pioneers.api.PioneersAPI.HEAT_HANDLER;
            case PRESSURE -> fr.pioneers.api.PioneersAPI.PRESSURE_HANDLER;
            case DATA -> fr.pioneers.api.PioneersAPI.DATA_HANDLER;
        };
        return host.getCapability(cap, null).resolve().orElse(null);
    }

    /** Un bloc a ete pose ou casse dans les cases de la structure : la revalider au plus tot. */
    void requestCheck();

    /** Hote forme du port en {@code pos} : le controleur forme qui le contient, sinon l'hote hors catalogue (halls), sinon null. */
    @Nullable
    static MultiblockPort find(@Nullable Level level, BlockPos pos) {
        if (level == null) {
            return null;
        }
        BlockPos c = Structures.controllerAt(level, pos);
        if (c != null && level.getBlockEntity(c) instanceof ControllerTile tile && tile.formed()) {
            return tile;
        }
        BlockPos h = Structures.hostAt(level, pos);
        return h != null && level.getBlockEntity(h) instanceof MultiblockPort host ? host : null;
    }

    /**
     * Lot 8a : hote maison de la piece en {@code pos} (cases de sa structure formee inscrites par {@code MultiblockCore.House}), du type
     * voulu, ou null : les tiles de port des multiblocs maison trouvent leur hote ainsi (plus de recherche propre a chaque maison).
     */
    @Nullable
    static <T> T house(@Nullable Level level, BlockPos pos, Class<T> type) {
        if (level == null) {
            return null;
        }
        BlockPos h = Structures.hostAt(level, pos);
        if (h == null || !level.isLoaded(h)) {
            return null;
        }
        Object tile = level.getBlockEntity(h);
        return type.isInstance(tile) ? type.cast(tile) : null;
    }
}
