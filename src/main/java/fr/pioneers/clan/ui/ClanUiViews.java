package fr.pioneers.clan.ui;

import fr.pioneers.clan.ClaimIndex;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.Faction;
import fr.pioneers.clan.Permission;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.core.ClanCore;
import fr.pioneers.clan.core.ClanMessages;
import fr.pioneers.clan.core.ClanRoles;
import fr.pioneers.clan.core.ClanViews;
import fr.pioneers.clan.faction.FactionRules;
import fr.pioneers.clan.rep.ClanReputation;
import fr.pioneers.clan.seal.ClanSeals;
import fr.pioneers.clan.stats.ClanStats;
import fr.pioneers.clan.territory.ClanChunkloading;
import fr.pioneers.clan.territory.ClanTerritory;
import fr.pioneers.clan.territory.TerritoryDefs;
import fr.pioneers.clan.territory.TerritoryRules;
import fr.pioneers.clan.war.ClanDiplomacy;
import fr.pioneers.clan.war.ClanWar;
import fr.pioneers.clan.war.Siege;
import fr.pioneers.clan.war.SiegeFlagTile;
import fr.pioneers.clan.war.WarRules;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.economy.EconomyApi;
import fr.pioneers.network.PacketRate;
import fr.pioneers.reputation.PeopleApi;
import fr.pioneers.reputation.ReputationApi;
import fr.pioneers.village.VillageData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : fiches de l'onglet Clan qui ne sont pas celles des fondations ({@code ClanViews}). Demandees par
 * {@code CLAN_REQUEST} {sheet, ...} (aiguillees ici par {@code ClanNetwork}), cadencees par {@link PacketRate}, chacune sous 32 767 octets :
 * <ul>
 * <li>{@code stats} -&gt; {@code CLAN_STATS} : fiche de {@link ClanStats#sheet} (courbes de production et de potentiel) et statistiques
 * personnelles du joueur sur 7 jours ;</li>
 * <li>{@code territory}, {@code diplomacy}, {@code recruits} -&gt; {@code CLAN_VIEW} : budget, chunkloading, fonctions ; traites, guerres,
 * creneaux, sieges, reputation du clan ; joueurs connectes que l'on peut inviter (nom de personnage et jeton, jamais un pseudo) ;</li>
 * <li>{@code directory} (pages), {@code card} {clan}, {@code peoples} -&gt; {@code CLAN_DIRECTORY} : annuaire public des clans, fiche
 * publique d'un clan (un allie voit la liste des personnages), registre des peuples.</li>
 * </ul>
 */
public final class ClanUiViews {

    public static final List<String> SHEETS = List.of("stats", "territory", "diplomacy", "recruits", "directory", "card", "peoples");
    /** Clans par page de l'annuaire. */
    public static final int DIRECTORY_PAGE = 8;
    private static final int RECRUITS = 16, FUNCTIONS = 48;

    private ClanUiViews() {
    }

    /** Demande venue du client : cadencee (la meme demande repetee dans l'intervalle est ecartee). */
    public static void request(ServerPlayer p, String sheet, CompoundTag t) {
        long key = (sheet.hashCode() * 31L + t.getInt("page")) * 31L + t.getString("clan").hashCode();
        if (PacketRate.allowKeyed(p, PacketRate.CLAN_SHEET, key)) {
            send(p, sheet, t);
        }
    }

    /** Envoie la fiche (sans cadence : reponse a une action deja comptee). */
    public static void send(ServerPlayer p, String sheet, CompoundTag t) {
        CompoundTag out = new CompoundTag();
        out.putString("sheet", sheet);
        out.putLong("now", ClanClock.now());
        if (!ClanCore.enabled()) {
            out.putString("error", "clan.pioneers.error.disabled");
            RpNet.send(p, RpActions.CLAN_VIEW, out);
            return;
        }
        MinecraftServer server = p.getServer();
        Clan mine = ClanData.get(server).clanOf(p.getUUID());
        switch (sheet) {
            case "stats" -> {
                CompoundTag s = mine == null ? new CompoundTag() : ClanStats.sheet(p, mine);
                s.putString("sheet", sheet);
                s.putLong("now", ClanClock.now());
                s.putBoolean("clan", mine != null);
                s.put("me", ClanStats.personal(server, p.getUUID(), 7));
                RpNet.send(p, RpActions.CLAN_STATS, s);
            }
            case "territory" -> {
                territory(p, mine, out);
                RpNet.send(p, RpActions.CLAN_VIEW, out);
            }
            case "diplomacy" -> {
                if (mine == null) {
                    out.putString("error", "clan.pioneers.error.no_clan");
                } else {
                    diplomacy(p, mine, out);
                }
                RpNet.send(p, RpActions.CLAN_VIEW, out);
            }
            case "recruits" -> {
                recruits(p, mine, out);
                RpNet.send(p, RpActions.CLAN_VIEW, out);
            }
            case "directory" -> {
                directory(p, mine, Math.max(0, t.getInt("page")), out);
                RpNet.send(p, RpActions.CLAN_DIRECTORY, out);
            }
            case "card" -> {
                card(p, mine, t.getString("clan"), out);
                RpNet.send(p, RpActions.CLAN_DIRECTORY, out);
            }
            case "peoples" -> {
                peoples(server, out);
                RpNet.send(p, RpActions.CLAN_DIRECTORY, out);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ Territoire
    private static void territory(ServerPlayer p, @Nullable Clan c, CompoundTag t) {
        MinecraftServer server = p.getServer();
        long now = ClanClock.now();
        TerritoryDefs d = TerritoryDefs.get();
        t.putInt("radius", ClanMapNet.R);
        t.putBoolean("clan", c != null);
        if (c == null) {
            //le Solitaire qui n'a pas encore de clan solo : le premier claim le cree
            t.putInt("budget", TerritoryRules.budget(ClanTerritory.rules(), 1, 1, false, true));
            t.putBoolean("p_claim", true);
            return;
        }
        UUID me = p.getUUID();
        t.putBoolean("solo", c.solo);
        t.putInt("claims", c.claimCount());
        t.putInt("budget", ClanTerritory.budget(c, now));
        t.putInt("outposts", ClanTerritory.outposts(c));
        t.putInt("maxOutposts", d.outposts);
        int declining = 0;
        for (Map<Long, Clan.Claim> m : c.claims.values()) {
            for (Clan.Claim x : m.values()) {
                if (x.declineUntil > 0) {
                    declining++;
                }
            }
        }
        t.putInt("declining", declining);
        //1.0.5-beta (lot 13) : plus de claims charges d'office (lot 8 de la 1.0.4) : emplacements payes a la pose (prix du prochain)
        t.putInt("slots", ClanChunkloading.slots(c));
        t.putLong("nextPrice", ClanChunkloading.nextPrice(server, c));
        t.putInt("loaded", c.chunkload.size());
        t.putInt("queued", ClanChunkloading.queued(server, c));
        t.putLong("cost", EconomyApi.enabled() ? d.chunkloadCost : 0);
        t.putLong("daily", ClanChunkloading.dailyCost(c));
        t.putBoolean("suspended", ClanChunkloading.suspended(c));
        t.putLong("stopAt", c.chunkloadStopAt);
        t.putLong("now", now);
        t.putInt("global", ClanChunkloading.globalActive(server));
        t.putInt("globalMax", ClanChunkloading.globalMax());
        t.putBoolean("p_claim", ClanRoles.has(c, me, Permission.CLAIM));
        t.putBoolean("p_zones", ClanRoles.has(c, me, Permission.ZONES));
        t.putBoolean("p_chunkload", ClanRoles.has(c, me, Permission.CHUNKLOAD));
        t.putInt("myRank", ClanRoles.rank(c, me));
        //1.0.3-beta (lot 16) : achat de claims (part gratuite, achetes, plafond, prix des 5 prochains, tresor si on le voit)
        TerritoryRules.Purchase buy = ClanTerritory.purchase();
        t.putInt("free", ClanTerritory.freeBudget(c, now));
        t.putInt("bought", c.boughtClaims);
        t.putInt("buyMax", TerritoryRules.buyCap(buy, c.solo));
        long[] next = new long[5];
        for (int i = 0; i < next.length; i++) {
            next[i] = TerritoryRules.claimPrice(buy, c.boughtClaims + 1 + i);
        }
        t.putLongArray("prices", next);
        t.putLong("price", buy.price());
        t.putBoolean("economy", EconomyApi.enabled());
        t.putBoolean("p_buy", ClanTerritory.mayBuy(c, me));
        if (ClanRoles.has(c, me, Permission.TREASURY_VIEW) || ClanTerritory.mayBuy(c, me)) {
            t.putLong("treasury", c.treasury);
        }
        t.putBoolean("frozen", c.frozen);
        //permissions des visiteurs par zone (Membres, Ouverte), lues par tout membre ; reglees avec la permission ZONES
        t.putIntArray("zperm", c.zonePerms.toArray());
        ListTag fns = new ListTag();
        for (Map.Entry<String, String> e : c.functions.entrySet()) {
            if (fns.size() >= FUNCTIONS) {
                break;
            }
            BlockPos b = ClanSeals.posOf(e.getKey());
            CompoundTag f = new CompoundTag();
            f.putString("fn", e.getValue());
            f.putString("dim", ClanSeals.dimOf(e.getKey()));
            f.putInt("x", b.getX());
            f.putInt("y", b.getY());
            f.putInt("z", b.getZ());
            fns.add(f);
        }
        t.put("functions", fns);
    }

    // ------------------------------------------------------------------ Diplomatie
    private static void diplomacy(ServerPlayer p, Clan c, CompoundTag t) {
        MinecraftServer server = p.getServer();
        long now = ClanClock.now();
        UUID me = p.getUUID();
        WarRules.Values v = WarRules.get();
        t.putBoolean("solo", c.solo);
        t.putBoolean("war", ClanWar.enabled());
        t.put("wars", ClanWar.view(server, c));
        t.put("treaties", ClanDiplomacy.view(server, c));
        t.put("federal", federalTreaties(server, c, now));
        t.putIntArray("windows", ClanWar.windows(c, now));
        t.putInt("windowHours", v.windowHours());
        t.putBoolean("inWindow", ClanWar.inWindow(c, now));
        if (c.pendingWindows != null && c.windowsFrom > now) {
            t.putIntArray("pendingWindows", c.pendingWindows);
            t.putLong("windowsFrom", c.windowsFrom);
        }
        t.putLong("now", now);
        t.putBoolean("p_diplomacy", ClanRoles.has(c, me, Permission.DIPLOMACY));
        t.putBoolean("p_war", ClanRoles.has(c, me, Permission.DECLARE_WAR));
        t.putBoolean("p_siege", ClanRoles.has(c, me, Permission.SIEGE));
        //sieges en cours : drapeaux plantes sur nos chunks, ou sur ceux d'un clan avec qui nous sommes en guerre
        ClanData data = ClanData.get(server);
        ListTag sieges = new ListTag();
        for (String key : Siege.flaggedChunks()) {
            int bar = key.lastIndexOf('|');
            if (bar <= 0 || sieges.size() >= 16) {
                continue;
            }
            String dim = key.substring(0, bar);
            long chunk;
            try {
                chunk = Long.parseLong(key.substring(bar + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            UUID owner = ClaimIndex.owner(dim, chunk);
            Clan o = data.clan(owner);
            boolean defending = c.id.equals(owner);
            if (o == null || !defending && ClanWar.live(server, c, o) == null) {
                continue;
            }
            CompoundTag s = new CompoundTag();
            s.putBoolean("defending", defending);
            s.putString("clan", defending ? "" : ClanMessages.name(server, o).getString());
            s.putInt("x", TerritoryRules.cx(chunk) << 4);
            s.putInt("z", TerritoryRules.cz(chunk) << 4);
            BlockPos flag = Siege.flagAt(key);
            ServerLevel level = ClanTerritory.level(server, dim);
            if (flag != null && level != null && level.isLoaded(flag) && level.getBlockEntity(flag) instanceof SiegeFlagTile tile) {
                s.putInt("progress", (int) Math.floor(tile.progress() * 100));
            } else {
                s.putInt("progress", -1);
            }
            sieges.add(s);
        }
        t.put("sieges", sieges);
        //reputation du clan aupres des peuples (le micro-peuple) : noms des peuples de joueurs
        CompoundTag rep = ClanReputation.sheet(p, c);
        for (Tag x : rep.getList("gauges", Tag.TAG_COMPOUND)) {
            CompoundTag g = (CompoundTag) x;
            PeopleApi.People pe = PeopleApi.get(server, g.getString("people"));
            if (pe != null && pe.player()) {
                g.putString("name", pe.displayName());
            }
        }
        t.put("rep", rep);
    }

    /**
     * Traites federaux qui valent pour ce clan (lecture seule ici : ils se decident au niveau de la faction) : ceux de sa faction (avec un
     * clan ou une autre faction) et ceux qu'une faction a signes avec lui. Sorte en cours, fin, rupture annoncee ; nom de l'autre camp.
     */
    private static ListTag federalTreaties(MinecraftServer server, Clan c, long now) {
        ClanData data = ClanData.get(server);
        ListTag out = new ListTag();
        Faction mine = data.faction(c.faction);
        for (Faction f : data.factions()) {
            for (Map.Entry<UUID, CompoundTag> e : f.treaties.entrySet()) {
                boolean ours = f == mine;
                if (!ours && !c.id.equals(e.getKey())) {
                    continue;
                }
                String other;
                if (ours) {
                    Faction of = data.faction(e.getKey());
                    Clan oc = of == null ? data.clan(e.getKey()) : null;
                    other = of != null ? of.name : oc != null ? ClanMessages.name(server, oc).getString() : "";
                } else {
                    other = f.name;
                }
                ListTag kinds = new ListTag();
                for (String k : FactionRules.TREATIES) {
                    if (FactionRules.treatyActive(e.getValue(), k, now)) {
                        CompoundTag x = new CompoundTag();
                        x.putString("kind", k);
                        x.putLong("end", e.getValue().getLong(FactionRules.treatyEnd(k)));
                        x.putBoolean("breaking", e.getValue().getBoolean(k + "Break"));
                        kinds.add(x);
                    }
                }
                if (other.isEmpty() || kinds.isEmpty() || out.size() >= 24) {
                    continue;
                }
                CompoundTag x = new CompoundTag();
                x.putString("clan", other);
                x.putString("by", ours ? f.name : "");
                x.put("treaties", kinds);
                out.add(x);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ joueurs a inviter
    private static void recruits(ServerPlayer p, @Nullable Clan c, CompoundTag t) {
        MinecraftServer server = p.getServer();
        boolean may = c != null && ClanRoles.has(c, p.getUUID(), Permission.INVITE);
        t.putBoolean("p_invite", may);
        ListTag l = new ListTag();
        if (may) {
            ClanData data = ClanData.get(server);
            List<ServerPlayer> others = new ArrayList<>(server.getPlayerList().getPlayers());
            others.removeIf(o -> o == p || c.members.containsKey(o.getUUID()));
            others.sort(Comparator.comparingDouble(o -> o.level() == p.level() ? o.distanceToSqr(p) : Double.MAX_VALUE));
            for (ServerPlayer o : others) {
                if (l.size() >= RECRUITS) {
                    break;
                }
                Clan oc = data.clanOf(o.getUUID());
                CompoundTag e = new CompoundTag();
                e.putString("token", ClanViews.token(c, o.getUUID()));
                String n = PioneerCap.get(o).name;
                e.putString("char", n == null || n.isBlank() ? "" : n);
                //membre d'un autre clan (hors clan solo) : on ne l'invite pas
                e.putBoolean("busy", oc != null && !oc.solo);
                e.putInt("dist", o.level() == p.level() ? (int) Math.sqrt(o.distanceToSqr(p)) : -1);
                l.add(e);
            }
        }
        t.put("list", l);
    }

    // ------------------------------------------------------------------ annuaires
    private static String relation(MinecraftServer server, @Nullable Clan mine, Clan o) {
        return mine == null ? "neutral" : ClanApi.relation(server, mine, o).name().toLowerCase(Locale.ROOT);
    }

    private static void directory(ServerPlayer p, @Nullable Clan mine, int page, CompoundTag t) {
        MinecraftServer server = p.getServer();
        List<Clan> all = new ArrayList<>();
        for (Clan c : ClanData.get(server).clans()) {
            if (!c.solo && c.name != null && !c.name.isBlank()) {
                all.add(c);
            }
        }
        all.sort(Comparator.comparing((Clan c) -> c.name.toLowerCase(Locale.ROOT)));
        int pages = Math.max(1, (all.size() + DIRECTORY_PAGE - 1) / DIRECTORY_PAGE);
        int pg = Math.min(page, pages - 1);
        ListTag l = new ListTag();
        for (int i = pg * DIRECTORY_PAGE; i < Math.min(all.size(), (pg + 1) * DIRECTORY_PAGE); i++) {
            CompoundTag card = ClanStats.card(server, all.get(i));
            card.putString("rel", relation(server, mine, all.get(i)));
            l.add(card);
        }
        t.putString("kind", "clans");
        t.put("clans", l);
        t.putInt("page", pg);
        t.putInt("pages", pages);
        t.putInt("total", all.size());
    }

    private static void card(ServerPlayer p, @Nullable Clan mine, String id, CompoundTag t) {
        MinecraftServer server = p.getServer();
        ClanData data = ClanData.get(server);
        t.putString("kind", "card");
        Clan o;
        try {
            o = data.clan(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            o = null;
        }
        if (o == null) {
            t.putString("error", "clan.pioneers.error.no_such_clan");
            return;
        }
        CompoundTag card = ClanStats.card(server, o);
        String rel = relation(server, mine, o);
        card.putString("rel", rel);
        Faction f = data.faction(o.faction);
        card.putString("faction", f == null ? "" : f.name);
        //un allie voit la liste des personnages (jamais un pseudo de compte)
        if ("ally".equals(rel)) {
            ListTag chars = new ListTag();
            for (UUID u : o.members.keySet()) {
                chars.add(StringTag.valueOf(ClanMessages.character(server, o, u)));
            }
            card.put("characters", chars);
        }
        t.put("card", card);
        if (mine == null || mine == o) {
            return;
        }
        long now = ClanClock.now();
        UUID me = p.getUUID();
        t.putBoolean("mineSolo", mine.solo);
        t.putBoolean("war", ClanWar.enabled());
        t.putBoolean("p_diplomacy", ClanRoles.has(mine, me, Permission.DIPLOMACY));
        t.putBoolean("p_war", ClanRoles.has(mine, me, Permission.DECLARE_WAR));
        t.putBoolean("atWar", ClanWar.live(server, mine, o) != null);
        t.putInt("reasonMax", WarRules.get().reasonMax());
        ListTag active = new ListTag(), offers = new ListTag();
        for (String k : ClanDiplomacy.KINDS) {
            if (ClanDiplomacy.effective(mine, o, k, now)) {
                active.add(StringTag.valueOf(k));
            }
        }
        CompoundTag tr = mine.treaties.get(o.id);
        if (tr != null) {
            CompoundTag of = tr.getCompound("offers");
            for (String k : of.getAllKeys()) {
                CompoundTag x = new CompoundTag();
                x.putString("kind", k);
                x.putBoolean("received", !(of.getCompound(k).hasUUID("from") && mine.id.equals(of.getCompound(k).getUUID("from"))));
                offers.add(x);
            }
        }
        t.put("active", active);
        t.put("offers", offers);
    }

    private static void peoples(MinecraftServer server, CompoundTag t) {
        ClanData data = ClanData.get(server);
        ListTag l = new ListTag();
        VillageData villages = VillageData.get(server.overworld());
        for (PeopleApi.People pe : PeopleApi.all(server)) {
            CompoundTag e = new CompoundTag();
            e.putString("id", pe.id());
            e.putBoolean("player", pe.player());
            e.putString("name", Component.Serializer.toJson(ReputationApi.peopleName(server, pe.id())));
            e.putString("doctrine", pe.doctrine());
            e.putString("model", PeopleApi.model(server, pe.id()));
            e.putInt("color", pe.color());
            int n = 0;
            for (VillageData.Village v : villages.all()) {
                if (pe.id().equals(v.people)) {
                    n++;
                }
            }
            e.putInt("villages", n);
            for (Faction f : data.factions()) {
                if (pe.id().equals(f.people)) {
                    e.putString("faction", f.name);
                    e.putString("tag", f.tag);
                    e.putInt("clans", f.clans.size());
                }
            }
            l.add(e);
        }
        t.putString("kind", "peoples");
        t.put("peoples", l);
    }
}
