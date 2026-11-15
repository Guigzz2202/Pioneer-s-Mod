package fr.pioneers.bronze.steam;

import fr.pioneers.clan.ClanApi;
import fr.pioneers.core.Directions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Outils de la vapeur : noeud voisin, echange entre deux reserves collees. 1.0.2-beta (lot 3) : plus de pression ; l'echange suit le
 * remplissage des reserves ({@link SteamRules#exchange}), l'eau bout toujours a 100 C ({@link SteamRules#BOIL_C}).
 */
public final class Steam {

    /** 1 mB d'eau vaporisee = 8 mB de vapeur (meme chiffre partout : {@link SteamRules#STEAM_PER_WATER}). */
    public static final double STEAM_PER_WATER = SteamRules.STEAM_PER_WATER;

    private Steam() {
    }

    @Nullable
    public static SteamNode node(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof SteamNode node ? node : null;
    }

    /**
     * Echange entre le noeud {@code a} (face {@code side}) et son voisin : de la reserve la plus remplie vers la moins remplie, borne pour ne
     * pas depasser l'equilibre ni le debit des faces, filtre par les clapets ; une source (chaudiere) ne se remplit jamais. Renvoie le debit
     * (mB, positif de a vers b).
     */
    public static double exchange(SteamNode a, Direction side, SteamNode b) {
        SteamVolume va = a.steam(side), vb = b.steam(side.getOpposite());
        if (va == null || vb == null || va == vb) {
            return 0;
        }
        double c = Math.min(a.conductance(side), b.conductance(side.getOpposite()));
        double flow = SteamRules.exchange(va.steam(), va.capacity(), vb.steam(), vb.capacity(), c);
        if (flow == 0) {
            return 0;
        }
        boolean towardB = flow > 0;
        if ((towardB ? b : a).steamSource() || !a.accepts(side, !towardB) || !b.accepts(side.getOpposite(), towardB)) {
            return 0;
        }
        if (towardB) {
            double wet = va.wet();
            double put = vb.add(Math.min(flow, vb.room()), wet);
            va.take(put);
            return put;
        }
        double wet = vb.wet();
        double put = va.add(Math.min(-flow, va.room()), wet);
        vb.take(put);
        return -put;
    }

    /** Echanges d'un noeud actif avec tous ses voisins (un couple actif/actif n'est traite qu'une fois, par le plus petit). */
    public static void exchangeAll(Level level, SteamNode node) {
        BlockPos pos = node.nodePos();
        for (Direction side : Directions.ALL) {
            if (node.steam(side) == null) {
                continue;
            }
            //1.9 (MAJ RP, bloc 4) : la vapeur ne passe pas une frontiere de claim refusee
            SteamNode other = ClanApi.canTransfer(level, pos, pos.relative(side)) ? node(level, pos.relative(side)) : null;
            if (other == null || other.steam(side.getOpposite()) == null) {
                continue;
            }
            if (other.active() && other.nodePos().asLong() < pos.asLong()) {
                continue;
            }
            exchange(node, side, other);
        }
    }
}
