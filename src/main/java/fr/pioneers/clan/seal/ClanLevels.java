package fr.pioneers.clan.seal;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.core.ClanDefs;
import fr.pioneers.clan.core.ClanMessages;
import fr.pioneers.clan.territory.TerritoryDefs;
import fr.pioneers.clan.territory.TerritoryNet;
import fr.pioneers.clan.territory.TerritoryRules;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.ruins.RuinEvents;
import fr.pioneers.ruins.RuinSites;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 4) : points et niveau du clan (1 a 10, jamais en baisse). Sources : production (1 point par 100 jetons de valeur
 * produite, 50 par membre actif et par jour au plus, compteur pose par les statistiques via {@link #addProduction}), territoire tenu (2 par
 * chunk, 100 par jour), fonctions designees (10 par fonction, 80 par jour), restauration d'une ruine (500, une fois par ruine et par clan).
 * Seuils, effectifs et emplacements : {@code data/pioneers/clans/levels.json} (lu par les fondations) ; valeurs des sources :
 * {@code territory.json}. Les points suivent le jour de jeu.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ClanLevels {

    /** Cles de {@code Clan.pointsToday} et de {@code Clan.extra}. */
    static final String PROD_TOKENS = "production_tokens", PROD_POINTS = "production_points", RUINS = "t_ruins";

    private ClanLevels() {
    }

    /** Remet a zero les compteurs du jour si le jour de jeu a change. */
    static void roll(Clan c, long day) {
        if (c.pointsDay != day) {
            c.pointsDay = day;
            c.pointsToday.clear();
        }
    }

    /**
     * Valeur produite par un membre (jetons-equivalent), comptee par les statistiques : 1 point par 100 jetons, au plus 50 points par membre
     * actif et par jour pour le clan.
     */
    public static void addProduction(Clan c, long tokens) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (tokens <= 0 || server == null) {
            return;
        }
        TerritoryDefs d = TerritoryDefs.get();
        roll(c, PioneerApi.day(server));
        long total = c.pointsToday.merge(PROD_TOKENS, tokens, Long::sum);
        long cap = (long) d.productionMemberCap * Math.max(1, c.solo ? 1 : c.activeCount(ClanClock.now()));
        long given = c.pointsToday.getOrDefault(PROD_POINTS, 0L);
        long fresh = TerritoryRules.productionPoints(total, d.pointsPerTokens, cap, given);
        if (fresh > 0) {
            c.pointsToday.put(PROD_POINTS, given + fresh);
            add(server, c, fresh);
        } else {
            ClanData.get(server).changed();
        }
    }

    /** Ajoute des points et fait monter le niveau (les seuils des donnees ; le niveau ne baisse jamais). */
    public static void add(MinecraftServer server, Clan c, long points) {
        if (points <= 0) {
            return;
        }
        c.points += points;
        int target = Math.max(c.level, ClanDefs.get().levelFor(c.points));
        if (target > c.level) {
            int from = c.level;
            c.level = target;
            c.log(PioneerApi.day(server), "clan.pioneers.log.level_up", String.valueOf(from), String.valueOf(target));
            ClanMessages.members(server, c, null, ClanMessages.tagged(c, Component.translatable("clan.pioneers.level.up", target)));
            TerritoryNet.bump();
        }
        ClanData.get(server).changed();
    }

    /** Passage du jour de jeu : points du territoire tenu et des fonctions designees, plafonnes. */
    public static void dayChange(MinecraftServer server, long day) {
        TerritoryDefs d = TerritoryDefs.get();
        for (Clan c : ClanData.get(server).clans()) {
            if (c.members.isEmpty()) {
                continue;
            }
            long pts = TerritoryRules.dailyPoints(c.claimCount(), d.chunkPoints, d.chunkCap, c.functions.size(), d.functionPoints, d.functionCap);
            roll(c, day);
            add(server, c, pts);
        }
    }

    /** Ruine restauree : 500 points a chaque clan qui compte un contributeur (ou le joueur qui l'a remise en service), une fois par ruine. */
    @SubscribeEvent
    public static void onRestored(RuinEvents.Restored e) {
        MinecraftServer server = e.level.getServer();
        ClanData data = ClanData.get(server);
        Set<Clan> clans = new LinkedHashSet<>();
        if (e.player != null && data.clanOf(e.player.getUUID()) != null) {
            clans.add(data.clanOf(e.player.getUUID()));
        }
        RuinSites.Site site = RuinSites.get(e.level).site(e.site);
        if (site != null) {
            for (UUID u : site.contributors.keySet()) {
                Clan c = data.clanOf(u);
                if (c != null) {
                    clans.add(c);
                }
            }
        }
        for (Clan c : clans) {
            ListTag done = c.extra.getList(RUINS, Tag.TAG_LONG);
            boolean seen = false;
            for (Tag t : done) {
                if (((LongTag) t).getAsLong() == e.site) {
                    seen = true;
                    break;
                }
            }
            if (seen) {
                continue;
            }
            done.add(LongTag.valueOf(e.site));
            c.extra.put(RUINS, done);
            c.log(PioneerApi.day(server), "clan.pioneers.log.ruin_points", String.valueOf(TerritoryDefs.get().ruinPoints));
            add(server, c, TerritoryDefs.get().ruinPoints);
        }
    }
}
