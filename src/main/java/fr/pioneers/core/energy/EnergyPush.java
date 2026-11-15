package fr.pioneers.core.energy;

import fr.pioneers.clan.ClanApi;
import fr.pioneers.core.Directions;
import java.util.function.BiPredicate;
import java.util.function.LongUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * 0.20.46 (audit) : envoi d'energie aux six voisins, partage par les producteurs (machines, thermique, RTG...). Une seule version : voisin
 * charge verifie, faces filtrees, et le producteur ne perd que ce que les voisins ont vraiment pris.
 */
public final class EnergyPush {

    private EnergyPush() {
    }

    /**
     * Offre jusqu'a {@code budget} FE aux voisins acceptes par {@code accept} (face vue du producteur, tile voisine), dans l'ordre des faces.
     * {@code take} retire du producteur la quantite donnee et renvoie ce qu'il a vraiment rendu (en general la meme valeur).
     *
     * @return l'energie donnee au total
     */
    public static long toNeighbours(Level level, BlockPos pos, long budget, BiPredicate<Direction, BlockEntity> accept, LongUnaryOperator take) {
        long given = 0;
        for (Direction side : Directions.ALL) {
            if (budget - given <= 0) {
                break;
            }
            BlockPos at = pos.relative(side);
            //1.9 (MAJ RP, bloc 4) : l'energie ne passe pas une frontiere de claim refusee
            if (!level.isLoaded(at) || !ClanApi.canTransfer(level, pos, at)) {
                continue;
            }
            BlockEntity other = level.getBlockEntity(at);
            if (other == null || !accept.test(side, other)) {
                continue;
            }
            IEnergyStorage target = other.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).orElse(null);
            if (target != null && target.canReceive()) {
                int offered = target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, budget - given), false);
                if (offered > 0) {
                    given += take.applyAsLong(offered);
                }
            }
        }
        return given;
    }
}
