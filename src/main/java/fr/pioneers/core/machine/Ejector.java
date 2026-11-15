package fr.pioneers.core.machine;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.pressure.IPressureHandler;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.core.Directions;
import fr.pioneers.core.pressure.PressureExchange;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideType;
import fr.pioneers.transport.Transfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Ejection automatique par les faces de sortie (OUTPUT, IO) d'une machine dont l'ejection est activee. */
public final class Ejector {

    private Ejector() {
    }

    /** Pousse au plus {@code max} par face de sortie dans le bloc voisin (objets, fluides, gaz, energie). */
    public static <H, R> void eject(Level level, BlockPos pos, SideConfig config, Transfer<H, R> transfer, H own, long max) {
        SideType type = transfer == Transfer.ITEMS ? SideType.ITEMS
              : transfer == Transfer.FLUIDS ? SideType.FLUIDS : transfer == Transfer.GASES ? SideType.GASES
              : SideType.ENERGY;
        if (!config.isEject(type)) {
            return;
        }
        for (Direction direction : Directions.ALL) {
            if (!config.mode(type, direction).canExtract()) {
                continue;
            }
            BlockPos neighbor = pos.relative(direction);
            //1.9 (MAJ RP, bloc 4) : rien ne sort a travers une frontiere de claim refusee
            if (!level.isLoaded(neighbor) || !ClanApi.canTransfer(level, pos, neighbor)) {
                continue;
            }
            BlockEntity tile = level.getBlockEntity(neighbor);
            H target = tile == null ? null : transfer.find(tile, direction.getOpposite());
            if (target == null) {
                continue;
            }
            R offered = transfer.extract(own, max, true);
            if (transfer.isEmpty(offered)) {
                return;
            }
            long accepted = transfer.insert(target, offered, true);
            if (accepted <= 0) {
                continue;
            }
            R taken = transfer.extractLike(own, offered, accepted, false);
            long placed = transfer.insert(target, taken, false);
            long left = transfer.amount(taken) - placed;
            if (left > 0) {
                transfer.insert(own, transfer.withAmount(taken, left), false);
            }
        }
    }

    /** Air comprime : clapet vers les voisins par les faces de sortie. */
    public static void ejectAir(Level level, BlockPos pos, SideConfig config, IPressureHandler own) {
        if (!config.isEject(SideType.PRESSURE)) {
            return;
        }
        for (Direction direction : Directions.ALL) {
            if (!config.mode(SideType.PRESSURE, direction).canExtract()) {
                continue;
            }
            //1.9 (MAJ RP, bloc 4) : ni a travers une frontiere de claim refusee
            BlockEntity tile = level.isLoaded(pos.relative(direction)) && ClanApi.canTransfer(level, pos, pos.relative(direction))
                  ? level.getBlockEntity(pos.relative(direction)) : null;
            if (tile != null) {
                tile.getCapability(PioneersAPI.PRESSURE_HANDLER, direction.getOpposite())
                      .ifPresent(body -> PressureExchange.exchange(own, body, true, false));
            }
        }
    }
}
