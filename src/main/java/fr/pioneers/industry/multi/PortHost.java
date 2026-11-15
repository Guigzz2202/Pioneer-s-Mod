package fr.pioneers.industry.multi;

import fr.pioneers.api.multiblock.MultiblockPort;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * 1.9.2 (retours de Guillaume) : multibloc construit hors du catalogue industriel (baie d'assemblage robotique, hall d'integration des
 * fusees) qui accepte les ports industriels dans son enveloppe. Le controleur s'inscrit dans {@link Structures#putHost} avec ses cases ;
 * les ports y retrouvent leur hote comme ils retrouvent un {@link ControllerTile}.
 * <p>
 * Lot 5 : cas particulier du contrat commun {@link MultiblockPort} (l'hote ne distingue pas ses ports par leur case).
 */
public interface PortHost extends MultiblockPort {

    /** Gestionnaire prete par un port de ce type et de ce sens (objet de capacite Forge), ou null si l'hote n'en a pas. */
    @Nullable
    Object portHandler(PortBlock.Type type, boolean output);

    @Nullable
    @Override
    default Object portHandler(PortBlock.Type type, boolean output, BlockPos port) {
        return portHandler(type, output);
    }
}
