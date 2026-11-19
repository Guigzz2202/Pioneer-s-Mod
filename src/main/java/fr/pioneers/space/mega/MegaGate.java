package fr.pioneers.space.mega;

import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.Structure;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 4) : la garde des PORTS de megastructure (multibloc dont le controleur est le terminal). Ses recettes
 * uniques ne partent que si le port est bati sur l'astre de sa megastructure, que celle-ci est debloquee (etat commun {@link MegaWorld}) et
 * que toute sa structure est MKV (carcasses, verres, cadres, toles : le tier le plus faible compte). Lu au lancement d'un lot ({@code MultiProcess.start}), jamais a chaque tick.
 */
public final class MegaGate {

    private MegaGate() {
    }

    /** {@link ControllerTile#IDLE} si le port peut travailler, sinon l'etat a afficher. */
    public static int status(Level level, net.minecraft.core.BlockPos pos, int mega, @Nullable Structure.Result structure) {
        if (!(level instanceof ServerLevel server) || Megastructures.here(level, pos) != mega || !MegaWorld.get(server.getServer()).unlocked(mega)) {
            return ControllerTile.MEGA_LOCKED;
        }
        if (structure == null || structure.tier() < CableTier.STELLAR.ordinal()) {
            return ControllerTile.MKV_CASINGS;
        }
        return ControllerTile.IDLE;
    }
}
