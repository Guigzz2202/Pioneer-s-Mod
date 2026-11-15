package fr.pioneers.clan.territory;

import fr.pioneers.clan.Clan;
import fr.pioneers.clan.core.ClanCore;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.9 (MAJ RP, bloc 4) : acces au clan solo d'un Solitaire, cree au premier claim par les fondations ({@link ClanCore#soloFor}). Un seul
 * point d'appel pour le territoire.
 */
public final class SoloClans {

    private SoloClans() {
    }

    @Nullable
    public static Clan soloFor(ServerPlayer player) {
        return ClanCore.soloFor(player);
    }
}
