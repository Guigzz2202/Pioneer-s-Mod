package fr.pioneers.core.profile;

import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** 1.9 (MAJ RP) : envois du profil et de l'identite aux clients (profil complet au joueur, identite a ceux qui le voient). */
public final class ProfileSync {

    /** Lignes du journal des comptes envoyees avec le profil (la Bourse en montre 20). */
    public static final int LEDGER_SENT = 20;

    private ProfileSync() {
    }

    public static void full(ServerPlayer player) {
        PioneerProfile p = PioneerCap.get(player);
        CompoundTag t = p.serializeNBT();
        ListTag ledger = t.getList("ledger", 10);
        ListTag last = new ListTag();
        for (int i = Math.max(0, ledger.size() - LEDGER_SENT); i < ledger.size(); i++) {
            last.add(ledger.get(i));
        }
        t.put("ledger", last);
        t.putInt("brigade", PioneerApi.brigadePercent(player));
        RpNet.send(player, RpActions.PROFILE, t);
    }

    public static void defs(ServerPlayer player) {
        RpNet.send(player, RpActions.DEFS, PioneerDefs.get().save());
        CompoundTag f = new CompoundTag();
        for (String k : PioneerApi.FEATURES) {
            f.putBoolean(k, PioneerApi.hasFeature(k));
        }
        RpNet.send(player, RpActions.FEATURES, f);
    }

    /**
     * Identite visible d'un joueur (nom du personnage, vocation, initiation, couleur du clan) : a lui et a ceux qui le suivent. 1.12.0 : sans
     * l'ancienne allure (habillage : garde-robe cosmetique, {@code fr.pioneers.cosmetic} ; barbe et coiffure : skin du joueur).
     */
    public static void identity(Player player) {
        if (player instanceof ServerPlayer sp) {
            RpNet.sendTracking(sp, RpActions.IDENTITY_OF, identityTag(sp));
        }
    }

    public static void identityTo(ServerPlayer viewer, Player target) {
        RpNet.send(viewer, RpActions.IDENTITY_OF, identityTag(target));
    }

    static CompoundTag identityTag(Player player) {
        PioneerProfile p = PioneerCap.get(player);
        CompoundTag t = new CompoundTag();
        t.putInt("id", player.getId());
        t.putString("name", p.name);
        t.putString("vocation", p.vocation);
        t.putBoolean("initiated", p.initiated);
        t.putInt("clanColor", clanColor(player)); //1.9 (MAJ RP, bloc 4) : couleur du tabard de clan
        return t;
    }

    /**
     * 1.9 (MAJ RP, bloc 4) : teinture (0..15) du clan du joueur, pour son tabard ; -1 sans clan ou en clan solo (le tabard prend alors le
     * bleu encre). Renvoyee avec l'identite ({@code ClanCore.tabards}) quand le joueur change de clan ou que son clan change de couleur.
     */
    static int clanColor(Player player) {
        if (!(player instanceof ServerPlayer sp) || sp.getServer() == null || !ClanApi.enabled()) {
            return -1;
        }
        Clan c = ClanApi.clanOf(sp.getServer(), sp.getUUID());
        return c == null || c.solo ? -1 : c.color & 15;
    }
}
