package fr.pioneers.core.block;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 29, reprise du §2 « reseau et sauvegarde », banc {@code tools/perf/tickers.sh}) : GARDE DES ENVOIS PERIODIQUES d'un bloc qui
 * se sauvait et se renvoyait au client toutes les 10 ou 20 ticks MEME A L'ARRET (four a creuset, fonderie automatique, foyer de chaudiere).
 * L'etat complet (sauvegarde {@link BlockEntity#saveWithoutMetadata} + etat client {@link BlockEntity#getUpdateTag}) est compare au dernier
 * etat sauve et envoye : identique, ni sauvegarde ni paquet (le client a deja exactement cet etat ; rien de neuf a ecrire) ; different, le
 * bloc fait comme avant. EXACT par construction (comparaison entiere des NBT, jamais d'arrondi).
 */
public final class StateWatch {

    @Nullable
    private CompoundTag last;

    /** Vrai (et retenu) si l'etat du bloc a change depuis le dernier appel vrai ; le premier appel est toujours vrai. */
    public boolean changed(BlockEntity tile) {
        CompoundTag now = tile.saveWithoutMetadata();
        now.put("~client", tile.getUpdateTag());
        if (now.equals(last)) {
            return false;
        }
        last = now;
        return true;
    }
}
