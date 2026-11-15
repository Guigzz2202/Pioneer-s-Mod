package fr.pioneers.clan.faction;

import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.Faction;
import fr.pioneers.clan.rep.ClanReputation;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.economy.EconomyApi;
import fr.pioneers.village.VillageData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.9 (MAJ RP, bloc 4) : ouvriers de village de la faction, payes par le tresor federal (« il paie les emplacements de chunkloading
 * federaux, les declarations de guerre et les ouvriers de village de la faction »). La faction le decide (decision federale {@code workers},
 * ligne « depenser ») : des lors, le salaire du jour d'un ouvrier embauche par un de ses membres est pris au tresor federal tant qu'il le
 * couvre, sinon au joueur comme avant. Chaque jour paye compte comme acte collectif du clan de l'employeur (financer des ouvriers).
 */
public final class FactionWorkers {

    /** Cle de {@code Faction.extra} : la faction paie les ouvriers de ses membres. */
    public static final String KEY = "fed.workers";

    private FactionWorkers() {
    }

    /** Crochet des villages ({@code Villages.workerPayer}) : vrai si le tresor federal a paye ce salaire. */
    public static boolean pay(ServerLevel level, VillageData.Village village, ServerPlayer employer, long wage) {
        MinecraftServer server = level.getServer();
        if (!EconomyApi.enabled() || wage <= 0 || !Factions.enabled()) {
            return false;
        }
        Clan c = ClanData.get(server).clanOf(employer.getUUID());
        Faction f = Factions.of(server, c);
        if (f == null || !f.extra.getBoolean(KEY)) {
            return false;
        }
        if (!Factions.debit(server, f, wage, "#clan.pioneers.faction.reason.workers", Factions.character(server, employer.getUUID()))) {
            return false;
        }
        ClanReputation.workersFunded(server, c, village.people, PioneerApi.day(server));
        return true;
    }
}
