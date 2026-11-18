package fr.pioneers.society.clan.ui;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.core.ClanCore;
import fr.pioneers.society.clan.core.ClanViews;
import fr.pioneers.society.clan.territory.Access;
import fr.pioneers.society.clan.territory.ClanTerritory;
import fr.pioneers.society.clan.war.ClanDiplomacy;
import fr.pioneers.society.clan.war.ClanWar;
import fr.pioneers.society.clan.war.WarRules;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : intentions de l'onglet Clan qui ne sont pas celles des fondations, recues par {@code CLAN_ACTION} (aiguillees
 * ici par {@code ClanNetwork}) : traites ({@code treaty} {verb, clan, kind}), guerre ({@code war_declare} {clan, reason},
 * {@code war_ceasefire}, {@code war_surrender} {clan}), creneaux de siege ({@code windows} {first, second}), invitation d'un joueur connecte
 * designe par son jeton ({@code invite_char} {token} : le client ne voit jamais le pseudo du compte). 1.0.3-beta (lot 16) : achat de claims
 * au tresor ({@code claim_buy} {n, seen}) et permissions des visiteurs par zone ({@code zone_perms} {zone, masks[4]} ou {zone, reset}),
 * revus par {@code ClanTerritory}. Tout est revu par les fonctions de la
 * diplomatie, de la guerre et des fondations (permissions, regime, preavis) ; le resultat detaille part aussi en message au joueur.
 */
public final class ClanUiActions {

    private static final Set<String> OPS = Set.of("treaty", "war_declare", "war_ceasefire", "war_surrender", "windows", "invite_char", "claim_buy",
          "zone_perms", "cl_buy");

    private ClanUiActions() {
    }

    public static boolean handles(String op) {
        return OPS.contains(op);
    }

    /** Execute l'intention ; cle du refus ou {@code null}. */
    @Nullable
    public static String run(ServerPlayer p, CompoundTag t) {
        ClanData data = ClanData.get(p.getServer());
        Clan mine = data.clanOf(p.getUUID());
        if (mine == null) {
            return "clan.pioneers.error.no_clan";
        }
        String op = t.getString("op");
        if ("claim_buy".equals(op)) {
            return ClanTerritory.buy(p, t.getInt("n"), t.contains("seen") ? t.getInt("seen") : -1);
        }
        if ("cl_buy".equals(op)) {
            //1.0.6 : emplacements de chunkloading achetes au tresor (stock)
            return fr.pioneers.society.clan.territory.ClanChunkloading.buySlots(p, t.getInt("n"), t.contains("seen") ? t.getInt("seen") : -1);
        }
        if ("zone_perms".equals(op)) {
            Access.Zone zone;
            try {
                zone = Access.Zone.valueOf(t.getString("zone").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return "clan.pioneers.error.invalid";
            }
            return ClanTerritory.setVisitorPerms(p, zone, t.getBoolean("reset") ? null : t.getIntArray("masks"), t.getBoolean("reset"));
        }
        if ("windows".equals(op)) {
            return ClanWar.setWindows(p, mine, Math.floorMod(t.getInt("first"), 24), Math.floorMod(t.getInt("second"), 24));
        }
        if ("invite_char".equals(op)) {
            for (ServerPlayer o : p.getServer().getPlayerList().getPlayers()) {
                if (o != p && ClanViews.token(mine, o.getUUID()).equals(t.getString("token"))) {
                    return ClanCore.invite(p, o);
                }
            }
            return "clan.pioneers.error.no_player";
        }
        Clan other = data.byName(t.getString("clan"));
        if (other == null) {
            return "clan.pioneers.error.no_such_clan";
        }
        if (other == mine) {
            return "clan.pioneers.error.invalid";
        }
        switch (op) {
            case "treaty" -> {
                return ClanDiplomacy.treatyIntent(p, mine, other, t.getString("verb"), t.getString("kind"));
            }
            case "war_declare" -> {
                String reason = t.getString("reason").trim();
                int max = WarRules.get().reasonMax();
                ClanWar.declare(p, mine, other, reason.length() > max ? reason.substring(0, max) : reason);
                return null;
            }
            case "war_ceasefire" -> {
                ClanWar.ceasefire(p, mine, other);
                return null;
            }
            case "war_surrender" -> {
                ClanWar.surrender(p, mine, other);
                return null;
            }
            default -> {
                return "clan.pioneers.error.invalid";
            }
        }
    }
}
