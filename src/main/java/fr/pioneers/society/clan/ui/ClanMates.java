package fr.pioneers.society.clan.ui;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.10.3 (minimap) : position des membres de son clan, pour les voir sur la minimap hors de la portee des entites. Seule partie serveur
 * de la minimap, volontairement etroite : membres EN LIGNE du MEME clan (jamais un allie, jamais un clan solo : un Solitaire n'a pas de
 * compagnon), dans la MEME dimension, hors spectateurs ; pseudos de compte seulement entre membres du clan (regle de la MAJ RP). Envoye
 * toutes les {@link #PERIOD_S} s aux abonnes de la carte strategique qui l'ont demande ({@code ClanMapNet}, drapeau {@code mates}).
 */
public final class ClanMates {

    public static final int PERIOD_S = 5, MAX = 64;

    private ClanMates() {
    }

    /** Membres a montrer au joueur {@code viewer} (lui excepte). */
    public static List<ServerPlayer> of(MinecraftServer server, ServerPlayer viewer) {
        List<ServerPlayer> out = new ArrayList<>();
        if (!ClanApi.enabled()) {
            return out;
        }
        Clan c = ClanData.get(server).clanOf(viewer.getUUID());
        if (c == null || c.solo) {
            return out;
        }
        for (UUID u : c.members.keySet()) {
            if (u.equals(viewer.getUUID()) || out.size() >= MAX) {
                continue;
            }
            ServerPlayer m = server.getPlayerList().getPlayer(u);
            if (m != null && !m.isSpectator() && m.level().dimension() == viewer.level().dimension()) {
                out.add(m);
            }
        }
        return out;
    }

    /** Charge du paquet {@code CLAN_MATES} : dimension, teinture du clan, membres (pseudo, x, y, z). */
    public static CompoundTag tag(MinecraftServer server, ServerPlayer viewer) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", ClanApi.dim(viewer.level()));
        Clan c = ClanData.get(server).clanOf(viewer.getUUID());
        t.putInt("dye", c == null ? -1 : c.color & 15);
        ListTag list = new ListTag();
        for (ServerPlayer m : of(server, viewer)) {
            CompoundTag e = new CompoundTag();
            e.putString("name", m.getGameProfile().getName());
            e.putInt("x", m.getBlockX());
            e.putInt("y", m.getBlockY());
            e.putInt("z", m.getBlockZ());
            list.add(e);
        }
        t.put("mates", list);
        return t;
    }
}
