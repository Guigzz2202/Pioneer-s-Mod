package fr.pioneers.society.clan.territory;

import fr.pioneers.society.clan.ClaimIndex;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.ClanTreasury;
import fr.pioneers.society.clan.Permission;
import fr.pioneers.society.clan.core.ClanClock;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.society.clan.core.ClanRoles;
import fr.pioneers.society.clan.seal.ClanSeals;
import fr.pioneers.society.clan.war.Siege;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.world.ruins.RuinSites;
import fr.pioneers.society.village.VillageData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;

/**
 * 1.9 (MAJ RP, bloc 4) : le territoire d'un clan. Reclamer et abandonner un chunk (permission CLAIM), zones (permission ZONES), budget qui
 * grandit avec le clan, avant-postes, declin des bords quand le budget baisse, liberation des chunks d'un clan dissous. Toute modification
 * passe par {@link #put} et {@link #drop} : {@code ClanData}, {@code ClaimIndex}, reseaux de cables coupes a la nouvelle frontiere,
 * bordures envoyees aux joueurs. Tout est verifie cote serveur.
 */
public final class ClanTerritory {

    private ClanTerritory() {
    }

    public static final long HOUR = 3_600_000L;

    // ------------------------------------------------------------------ lecture
    public static TerritoryRules.Budget rules() {
        TerritoryDefs d = TerritoryDefs.get();
        return new TerritoryRules.Budget(PiCommonConfig.get(RpConfig.CLAIM_BASE, 9), PiCommonConfig.get(RpConfig.CLAIM_PER_MEMBER, 3),
              PiCommonConfig.get(RpConfig.CLAIM_PER_LEVEL, 4), PiCommonConfig.get(RpConfig.CLAIM_MAX, 0),
              PiCommonConfig.get(RpConfig.CLAIM_SOLO_MAX, 0), d.cooperativeBonus);
    }

    /** Budget de claims du clan maintenant (membres actifs, niveau, regime) ; 1.0.3-beta (lot 16) : plus les claims achetes au tresor. */
    public static int budget(Clan c, long now) {
        return TerritoryRules.budget(rules(), c.activeCount(now), c.level, "cooperative".equals(c.regime), c.solo, c.boughtClaims);
    }

    /** 1.0.3-beta (lot 16) : part gratuite du budget (sans les claims achetes). */
    public static int freeBudget(Clan c, long now) {
        return TerritoryRules.budget(rules(), c.activeCount(now), c.level, "cooperative".equals(c.regime), c.solo);
    }

    /** 1.0.3-beta (lot 16) : reglages de l'achat de claims (configuration du serveur, section clans). */
    public static TerritoryRules.Purchase purchase() {
        return new TerritoryRules.Purchase(PiCommonConfig.get(RpConfig.CLAIM_PRICE, 100L),
              PiCommonConfig.get(RpConfig.CLAIM_BUY_MAX, 0), PiCommonConfig.get(RpConfig.CLAIM_BUY_SOLO_MAX, 0));
    }

    /**
     * 1.0.3-beta (lot 16) : le joueur peut-il acheter des claims ? 1.0.4-beta (lot 8) : permission propre « Acheter des claims »
     * ({@code BUY_CLAIMS}, donnee par defaut au Tresorier et au Bras droit) ; les roles d'avant qui avaient Reclamer + Retirer la recoivent
     * a la migration.
     */
    public static boolean mayBuy(Clan c, UUID player) {
        return may(c, player, Permission.BUY_CLAIMS);
    }

    /**
     * 1.0.3-beta (lot 16, Guillaume : « acheter des chunk claim avec l'argent du clan, prix qui monte au fur et a mesure ») : achete
     * {@code n} claims au tresor du clan. Le serveur seul juge : prix recalcule ici ({@link TerritoryRules#claimPrice}) ; {@code seen} =
     * nombre de claims deja achetes vu par le client (-1 : ne pas verifier) : s'il a change entre-temps (autre achat), on refuse plutot que
     * de faire payer un autre prix que celui affiche. Droit : CLAIM et WITHDRAW (le retrait pour soi est la seule permission de depense du
     * tresor ; un achat de territoire ne verse rien a un membre, il n'a donc pas de plafond journalier ni de vote « spend »). Debit avec
     * le libelle « Achat de territoire (n° k) » ; aucun remboursement a l'abandon d'un chunk (le claim achete reste au budget).
     *
     * @return cle du refus, ou null (achat fait, message au joueur)
     */
    @Nullable
    public static String buy(ServerPlayer p, int n, int seen) {
        if (!ClanApi.enabled()) {
            return "clan.pioneers.territory.disabled";
        }
        if (!EconomyApi.enabled()) {
            return "clan.pioneers.error.economy";
        }
        MinecraftServer server = p.getServer();
        Clan c = ClanData.get(server).clanOf(p.getUUID());
        if (c == null) {
            return "clan.pioneers.error.no_clan";
        }
        if (!mayBuy(c, p.getUUID())) {
            return "clan.pioneers.territory.buy.no_permission";
        }
        if (c.frozen) {
            return "clan.pioneers.error.frozen";
        }
        if (seen >= 0 && seen != c.boughtClaims) {
            return "clan.pioneers.territory.buy.stale";
        }
        TerritoryRules.Purchase rules = purchase();
        TerritoryRules.BuyVerdict v = TerritoryRules.canBuy(rules, c.boughtClaims, n, c.solo, c.treasury);
        switch (v) {
            case AMOUNT -> {
                return "clan.pioneers.error.amount";
            }
            case CAP -> {
                return "clan.pioneers.territory.buy.cap";
            }
            case TREASURY -> {
                return "clan.pioneers.territory.buy.treasury";
            }
            default -> {
            }
        }
        long cost = TerritoryRules.claimsPrice(rules, c.boughtClaims, n);
        int first = c.boughtClaims + 1, last = c.boughtClaims + n;
        String reason = n == 1 ? "#clan.pioneers.reason.territory|" + first : "#clan.pioneers.reason.territory_many|" + first + "|" + last;
        if (!ClanTreasury.debit(server, c, cost, reason, ClanMessages.character(p))) {
            return "clan.pioneers.territory.buy.treasury";
        }
        c.boughtClaims = last;
        long now = ClanClock.now();
        c.log(PioneerApi.day(server), "clan.pioneers.log.buy_claims", ClanMessages.character(p), String.valueOf(n), String.valueOf(cost));
        ClanData.get(server).changed();
        //le budget grandit : un declin en cours s'arrete a la prochaine minute (decline) ; la carte et les vues se refont
        TerritoryNet.bump();
        ClanMessages.tell(p, "clan.pioneers.territory.buy.done", n, cost, budget(c, now));
        return null;
    }

    /**
     * 1.0.3-beta (lot 16) : regle ce que les visiteurs peuvent faire dans une zone : {@code masks} = un masque de {@link ZonePerms.Group}
     * par categorie de visiteur (ordre de {@link ZonePerms.Visitor}) ; {@code reset} : defauts de la zone. Permission ZONES (celle qui regle
     * deja les zones des chunks). Zone Privee : refusee (fermee a tous les visiteurs).
     *
     * @return cle du refus, ou null
     */
    @Nullable
    public static String setVisitorPerms(ServerPlayer p, Access.Zone zone, @Nullable int[] masks, boolean reset) {
        MinecraftServer server = p.getServer();
        Clan c = ClanData.get(server).clanOf(p.getUUID());
        if (c == null) {
            return "clan.pioneers.error.no_clan";
        }
        if (!may(c, p.getUUID(), Permission.ZONES)) {
            return "clan.pioneers.territory.no_permission";
        }
        ZonePerms.Visitor[] vs = ZonePerms.Visitor.values();
        if (zone == Access.Zone.PRIVATE || !reset && (masks == null || masks.length != vs.length)) {
            return "clan.pioneers.error.invalid";
        }
        ZonePerms next = c.zonePerms;
        if (reset) {
            next = next.reset(zone);
        } else {
            for (ZonePerms.Visitor v : vs) {
                next = next.with(zone, v, masks[v.ordinal()]);
            }
        }
        if (next.equals(c.zonePerms)) {
            return null;
        }
        c.zonePerms = next;
        ClanData.get(server).changed();
        String z = "#clan.pioneers.zone." + zone.name().toLowerCase(Locale.ROOT);
        c.log(PioneerApi.day(server), reset ? "clan.pioneers.log.zone_perms_reset" : "clan.pioneers.log.zone_perms", ClanMessages.character(p), z);
        return null;
    }

    public static int outposts(Clan c) {
        int n = 0;
        for (Map<Long, Clan.Claim> m : c.claims.values()) {
            for (Clan.Claim x : m.values()) {
                if (x.outpost && !x.federal) {
                    n++;
                }
            }
        }
        return n;
    }

    public static String key(String dim, long chunk) {
        return dim + "|" + chunk;
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server, String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        return rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
    }

    /** Le joueur peut-il gerer ce point du territoire (permission effective, regime compris) ? */
    public static boolean may(Clan c, UUID player, Permission p) {
        return c.members.containsKey(player) && ClanRoles.has(c, player, p);
    }

    // ------------------------------------------------------------------ verdict
    /** Controle complet d'un claim de {@code clan} (null : le Solitaire qui n'a pas encore de clan solo). */
    public static TerritoryRules.Verdict check(ServerLevel level, @Nullable Clan clan, long chunk, long now) {
        String dim = ClanApi.dim(level);
        TerritoryDefs d = TerritoryDefs.get();
        Object id = clan == null ? new Object() : clan.id;
        Map<Long, Clan.Claim> mine = clan == null ? Map.of() : clan.claims.getOrDefault(dim, Map.of());
        int used = clan == null ? 0 : clan.claimCount();
        int budget = clan == null ? TerritoryRules.budget(rules(), 1, 1, false, true) : budget(clan, now);
        //1.0.3-beta (Guillaume) : plus besoin d'etre voisin du territoire : tout chunk libre se reclame au prix d'un claim (comme le
        //premier d'une dimension) ; plus de nouvel avant-poste (les anciens restent tels quels). Toucher un AUTRE clan reste refuse.
        return TerritoryRules.canClaim(chunk, id, c -> ClaimIndex.owner(dim, c), c -> {
                  Clan.Claim x = mine.get(c);
                  return x == null ? null : x.outpost;
              }, false, clan == null ? 0 : outposts(clan), d.outposts, d.outpostWeight, used, budget, nearSpawn(level, chunk, d.spawnChunks),
              nearBell(level, chunk, d.bellBlocks), ruin(level, chunk));
    }

    static boolean nearSpawn(ServerLevel level, long chunk, int chunks) {
        if (level.dimension() != Level.OVERWORLD || chunks <= 0) {
            return false;
        }
        BlockPos s = level.getSharedSpawnPos();
        int dx = Math.abs(TerritoryRules.cx(chunk) - (s.getX() >> 4)), dz = Math.abs(TerritoryRules.cz(chunk) - (s.getZ() >> 4));
        return Math.max(dx, dz) < chunks;
    }

    /** Distance horizontale d'un point au carre du chunk (0 dedans). */
    static double distance(long chunk, BlockPos p) {
        int x0 = TerritoryRules.cx(chunk) << 4, z0 = TerritoryRules.cz(chunk) << 4;
        double dx = Math.max(0, Math.max(x0 - p.getX(), p.getX() - (x0 + 15)));
        double dz = Math.max(0, Math.max(z0 - p.getZ(), p.getZ() - (z0 + 15)));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Une cloche de village (registre des villages, ou point de rencontre connu du monde) a moins de {@code blocks} blocs du chunk. */
    static boolean nearBell(ServerLevel level, long chunk, int blocks) {
        if (blocks <= 0) {
            return false;
        }
        BlockPos center = new BlockPos((TerritoryRules.cx(chunk) << 4) + 8, 64, (TerritoryRules.cz(chunk) << 4) + 8);
        for (VillageData.Village v : VillageData.get(level).within(center, blocks + 12)) {
            if (distance(chunk, v.center) < blocks) {
                return true;
            }
        }
        return level.getPoiManager().getInSquare(t -> t.is(PoiTypes.MEETING), center, blocks + 12, PoiManager.Occupancy.ANY)
              .anyMatch(r -> distance(chunk, r.getPos()) < blocks);
    }

    /** Une ruine intacte (ou en chantier de restauration) touche-t-elle le chunk ? Une ruine restauree ou entamee se reclame. */
    static boolean ruin(ServerLevel level, long chunk) {
        int x0 = TerritoryRules.cx(chunk) << 4, z0 = TerritoryRules.cz(chunk) << 4;
        for (RuinSites.Site s : RuinSites.get(level).all()) {
            if (s.state != RuinSites.State.INTACT && s.state != RuinSites.State.RESTORING) {
                continue;
            }
            if (s.min.getX() <= x0 + 15 && s.max.getX() >= x0 && s.min.getZ() <= z0 + 15 && s.max.getZ() >= z0) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ actions des joueurs
    /** {@code /clan claim} : le chunk ou se tient le joueur. */
    public static void claim(ServerPlayer p) {
        claim(p, p.serverLevel(), p.chunkPosition().toLong());
    }

    /** 1.9 (MAJ RP, bloc 4) : claim d'un chunk choisi (carte strategique du Carnet ; la distance au joueur est revue par l'appelant). */
    public static void claim(ServerPlayer p, ServerLevel level, long chunk) {
        if (!ClanApi.enabled()) {
            ClanMessages.refuse(p, "clan.pioneers.territory.disabled");
            return;
        }
        MinecraftServer server = p.getServer();
        long now = ClanClock.now();
        Clan clan = ClanData.get(server).clanOf(p.getUUID());
        if (clan != null && !may(clan, p.getUUID(), Permission.CLAIM)) {
            ClanMessages.refuse(p, "clan.pioneers.territory.no_permission");
            return;
        }
        TerritoryRules.Verdict v = check(level, clan, chunk, now);
        if (v != TerritoryRules.Verdict.OK && v != TerritoryRules.Verdict.OUTPOST) {
            refuse(p, v, clan, now);
            return;
        }
        if (clan == null) {
            clan = SoloClans.soloFor(p);
            if (clan == null) {
                ClanMessages.refuse(p, "clan.pioneers.territory.no_solo");
                return;
            }
            ClanMessages.tell(p, "clan.pioneers.territory.solo_created");
            //le clan solo vient de naitre : on revoit le verdict avec lui (meme resultat attendu)
            v = check(level, clan, chunk, now);
            if (v != TerritoryRules.Verdict.OK && v != TerritoryRules.Verdict.OUTPOST) {
                refuse(p, v, clan, now);
                return;
            }
        }
        Clan.Claim c = new Clan.Claim();
        c.outpost = v == TerritoryRules.Verdict.OUTPOST;
        put(server, clan, ClanApi.dim(level), chunk, c);
        clan.log(PioneerApi.day(server), c.outpost ? "clan.pioneers.log.outpost" : "clan.pioneers.log.claim", ClanMessages.character(p),
              coords(chunk));
        ClanMessages.tell(p, c.outpost ? "clan.pioneers.territory.outpost_claimed" : "clan.pioneers.territory.claimed", clan.claimCount(),
              budget(clan, now));
        //1.9.2 (retours de Guillaume) : liseré de quelques secondes a la couleur du clan (plus de bordure permanente)
        TerritoryNet.flash(p);
    }

    private static void refuse(ServerPlayer p, TerritoryRules.Verdict v, @Nullable Clan clan, long now) {
        TerritoryDefs d = TerritoryDefs.get();
        Object arg = switch (v) {
            case NEAR_SPAWN -> d.spawnChunks;
            case NEAR_BELL -> d.bellBlocks;
            case BUDGET -> clan == null ? TerritoryRules.budget(rules(), 1, 1, false, true) : budget(clan, now);
            case NO_OUTPOST -> d.outposts;
            default -> "";
        };
        ClanMessages.refuse(p, "clan.pioneers.territory.refused." + v.name().toLowerCase(Locale.ROOT), arg);
    }

    /** {@code /clan unclaim} : abandonne le chunk ou se tient le joueur. */
    public static void unclaim(ServerPlayer p) {
        unclaim(p, p.serverLevel(), p.chunkPosition().toLong());
    }

    /** 1.9 (MAJ RP, bloc 4) : abandon d'un chunk choisi (carte strategique du Carnet). */
    public static void unclaim(ServerPlayer p, ServerLevel level, long chunk) {
        MinecraftServer server = p.getServer();
        Clan clan = ClanData.get(server).clanOf(p.getUUID());
        String dim = ClanApi.dim(level);
        Clan.Claim c = clan == null ? null : clan.claim(dim, chunk);
        if (c == null) {
            ClanMessages.refuse(p, "clan.pioneers.territory.not_ours");
            return;
        }
        if (!may(clan, p.getUUID(), Permission.CLAIM)) {
            ClanMessages.refuse(p, "clan.pioneers.territory.no_permission");
            return;
        }
        drop(server, clan, dim, chunk, true);
        clan.log(PioneerApi.day(server), "clan.pioneers.log.unclaim", ClanMessages.character(p), coords(chunk));
        ClanMessages.tell(p, "clan.pioneers.territory.unclaimed", clan.claimCount(), budget(clan, ClanClock.now()));
        TerritoryNet.flash(p);
    }

    // ------------------------------------------------------------------ zones tracees sur la carte (1.9.2)
    /**
     * 1.9.2 (retours de Guillaume : « drag and drop la zone que je souhaite claim ») : apercu ({@code apply} faux) ou application d'un
     * claim ou d'un abandon en zone (rectangle de chunks, {@link TerritoryRules#AREA_MAX} au plus ; la distance au joueur est revue par
     * l'appelant). Tout est revu ici, chunk par chunk : permission, budget, contiguite ({@link TerritoryRules#planArea}), point
     * d'apparition, cloche, ruine, territoires etrangers, guerre (chunk assiege ou sous scelles). L'application refait le plan a neuf et
     * inscrit les chunks acceptes en une fois (un message, une ligne de journal, un liseré).
     *
     * @return le resume pour le client : {@code claim}, coins, {@code ok} (chunks acceptes), {@code cost} (budget pris ou rendu),
     *       {@code used}/{@code budget} (avant), {@code left} (budget restant apres), {@code skipped} (deja a soi / pas a soi),
     *       {@code refused} [{r : raison, n : nombre}], {@code cells} (verdict de chaque chunk du rectangle, ligne par ligne : 0 accepte,
     *       1 ignore, 2 + ordinal de la raison), {@code error} (cle de langue, refus global) et {@code done} si applique
     */
    public static CompoundTag area(ServerPlayer p, ServerLevel level, int x0, int z0, int x1, int z1, boolean claim, boolean apply) {
        CompoundTag out = new CompoundTag();
        out.putBoolean("claim", claim);
        out.putInt("x0", Math.min(x0, x1));
        out.putInt("z0", Math.min(z0, z1));
        out.putInt("x1", Math.max(x0, x1));
        out.putInt("z1", Math.max(z0, z1));
        List<Long> rect = TerritoryRules.rectangle(x0, z0, x1, z1);
        if (!ClanApi.enabled()) {
            out.putString("error", "clan.pioneers.territory.disabled");
            return out;
        }
        if (rect.size() > TerritoryRules.AREA_MAX) {
            out.putString("error", "clan.pioneers.ui.area.too_big");
            return out;
        }
        MinecraftServer server = p.getServer();
        long now = ClanClock.now();
        Clan clan = ClanData.get(server).clanOf(p.getUUID());
        if (clan != null && !may(clan, p.getUUID(), Permission.CLAIM)) {
            out.putString("error", "clan.pioneers.territory.no_permission");
            return out;
        }
        if (!claim && clan == null) {
            out.putString("error", "clan.pioneers.territory.not_ours");
            return out;
        }
        String dim = ClanApi.dim(level);
        Map<Long, TerritoryRules.Verdict> plan = claim ? planClaim(level, clan, rect, now) : planUnclaim(clan, dim, rect, now);
        if (apply && claim && clan == null && plan.containsValue(TerritoryRules.Verdict.OK)) {
            clan = SoloClans.soloFor(p);
            if (clan == null) {
                out.putString("error", "clan.pioneers.territory.no_solo");
                return out;
            }
            ClanMessages.tell(p, "clan.pioneers.territory.solo_created");
            plan = planClaim(level, clan, rect, now);
        }
        int used = clan == null ? 0 : clan.claimCount();
        int budget = clan == null ? TerritoryRules.budget(rules(), 1, 1, false, true) : budget(clan, now);
        List<Long> ok = new ArrayList<>();
        int skipped = 0;
        byte[] cells = new byte[rect.size()];
        Map<TerritoryRules.Verdict, Integer> refused = new java.util.EnumMap<>(TerritoryRules.Verdict.class);
        for (int i = 0; i < rect.size(); i++) {
            TerritoryRules.Verdict v = plan.get(rect.get(i));
            if (v == TerritoryRules.Verdict.OK) {
                ok.add(rect.get(i));
                cells[i] = 0;
            } else if (v == null || v == (claim ? TerritoryRules.Verdict.OWN : TerritoryRules.Verdict.TAKEN)) {
                skipped++;
                cells[i] = 1;
            } else {
                refused.merge(v, 1, Integer::sum);
                cells[i] = (byte) (2 + v.ordinal());
            }
        }
        out.putInt("ok", ok.size());
        out.putInt("cost", ok.size());
        out.putInt("used", used);
        out.putInt("budget", budget);
        out.putInt("left", claim ? budget - used - ok.size() : budget - used + ok.size());
        out.putInt("skipped", skipped);
        out.putByteArray("cells", cells);
        ListTag why = new ListTag();
        for (Map.Entry<TerritoryRules.Verdict, Integer> e : refused.entrySet()) {
            CompoundTag r = new CompoundTag();
            r.putString("r", e.getKey().name().toLowerCase(Locale.ROOT));
            r.putInt("n", e.getValue());
            r.putString("arg", refusalArg(e.getKey(), clan, now));
            why.add(r);
        }
        out.put("refused", why);
        if (!apply || ok.isEmpty() || clan == null) {
            return out;
        }
        //application en une fois : chaque chunk passe par put/drop (index, reseaux coupes a la frontiere), un seul message et une ligne de journal
        for (long chunk : ok) {
            if (claim) {
                put(server, clan, dim, chunk, new Clan.Claim());
            } else {
                drop(server, clan, dim, chunk, true);
            }
        }
        String where = coords(TerritoryRules.chunk((Math.min(x0, x1) + Math.max(x0, x1)) / 2, (Math.min(z0, z1) + Math.max(z0, z1)) / 2));
        clan.log(PioneerApi.day(server), claim ? "clan.pioneers.log.claim_area" : "clan.pioneers.log.unclaim_area", ClanMessages.character(p),
              String.valueOf(ok.size()), where);
        ClanMessages.tell(p, claim ? "clan.pioneers.territory.area_claimed" : "clan.pioneers.territory.area_unclaimed", ok.size(), clan.claimCount(),
              budget(clan, now));
        out.putBoolean("done", true);
        TerritoryNet.flash(p);
        return out;
    }

    /** Argument du message d'un refus (distance, budget, nombre d'avant-postes), en texte. */
    private static String refusalArg(TerritoryRules.Verdict v, @Nullable Clan clan, long now) {
        TerritoryDefs d = TerritoryDefs.get();
        return switch (v) {
            case NEAR_SPAWN -> String.valueOf(d.spawnChunks);
            case NEAR_BELL -> String.valueOf(d.bellBlocks);
            case BUDGET -> String.valueOf(clan == null ? TerritoryRules.budget(rules(), 1, 1, false, true) : budget(clan, now));
            default -> "";
        };
    }

    /** Plan d'un claim en zone ({@code clan} null : le Solitaire sans clan solo, juge comme au premier claim). */
    static Map<Long, TerritoryRules.Verdict> planClaim(ServerLevel level, @Nullable Clan clan, List<Long> rect, long now) {
        String dim = ClanApi.dim(level);
        TerritoryDefs d = TerritoryDefs.get();
        Object id = clan == null ? new Object() : clan.id;
        Map<Long, Clan.Claim> mine = clan == null ? Map.of() : clan.claims.getOrDefault(dim, Map.of());
        int used = clan == null ? 0 : clan.claimCount();
        int budget = clan == null ? TerritoryRules.budget(rules(), 1, 1, false, true) : budget(clan, now);
        return TerritoryRules.planArea(rect, id, c -> ClaimIndex.owner(dim, c), c -> {
            Clan.Claim x = mine.get(c);
            return x == null ? null : x.outpost;
        }, !mine.isEmpty(), used, budget, c -> {
            if (ClaimIndex.owner(dim, c) != null) {
                //chunk deja pris : la regle le dira (TAKEN ou OWN) sans recherche
                return null;
            }
            if (nearSpawn(level, c, d.spawnChunks)) {
                return TerritoryRules.Verdict.NEAR_SPAWN;
            }
            if (nearBell(level, c, d.bellBlocks)) {
                return TerritoryRules.Verdict.NEAR_BELL;
            }
            if (ruin(level, c)) {
                return TerritoryRules.Verdict.RUIN;
            }
            return Siege.besieged(dim, c) ? TerritoryRules.Verdict.WAR : null;
        }, true);
    }

    /** Plan d'un abandon en zone : chunks du clan (hors zones federales), sauf ceux qu'une guerre tient (siege en cours, scelles). */
    static Map<Long, TerritoryRules.Verdict> planUnclaim(Clan clan, String dim, List<Long> rect, long now) {
        Map<Long, TerritoryRules.Verdict> out = new java.util.LinkedHashMap<>();
        for (long c : rect) {
            Clan.Claim x = clan.claim(dim, c);
            if (x == null || x.federal) {
                out.put(c, TerritoryRules.Verdict.TAKEN);
            } else if (Siege.besieged(dim, c) || x.sealedUntil > now) {
                out.put(c, TerritoryRules.Verdict.WAR);
            } else {
                out.put(c, TerritoryRules.Verdict.OK);
            }
        }
        return out;
    }

    /** {@code /clan zone} : niveau d'acces du chunk (Membres, Prive avec rang minimal, Ouvert). */
    public static void zone(ServerPlayer p, Clan.Zone zone, int minRank) {
        zone(p, p.serverLevel(), p.chunkPosition().toLong(), zone, minRank);
    }

    /** 1.9 (MAJ RP, bloc 4) : niveau d'acces d'un chunk choisi (carte strategique du Carnet). */
    public static void zone(ServerPlayer p, ServerLevel level, long chunk, Clan.Zone zone, int minRank) {
        MinecraftServer server = p.getServer();
        Clan clan = ClanData.get(server).clanOf(p.getUUID());
        String dim = ClanApi.dim(level);
        Clan.Claim c = clan == null ? null : clan.claim(dim, chunk);
        if (c == null) {
            ClanMessages.refuse(p, "clan.pioneers.territory.not_ours");
            return;
        }
        if (!may(clan, p.getUUID(), Permission.ZONES)) {
            ClanMessages.refuse(p, "clan.pioneers.territory.no_permission");
            return;
        }
        if (zone == Clan.Zone.PRIVATE && minRank > ClanRoles.rank(clan, p.getUUID())) {
            //on ne ferme pas une zone a son propre rang
            ClanMessages.refuse(p, "clan.pioneers.territory.zone_rank", ClanRoles.rank(clan, p.getUUID()));
            return;
        }
        c.zone = zone;
        c.minRank = zone == Clan.Zone.PRIVATE ? Math.max(1, Math.min(100, minRank)) : 0;
        ClanData.get(server).changed();
        TerritoryNet.bump();
        String z = zone.name().toLowerCase(Locale.ROOT);
        clan.log(PioneerApi.day(server), "clan.pioneers.log.zone", ClanMessages.character(p), coords(chunk), "#clan.pioneers.zone." + z);
        ClanMessages.tell(p, zone == Clan.Zone.PRIVATE ? "clan.pioneers.territory.zone_private" : "clan.pioneers.territory.zone_set",
              Component.translatable("clan.pioneers.zone." + z), c.minRank);
    }

    public static String coords(long chunk) {
        return (TerritoryRules.cx(chunk) << 4) + " " + (TerritoryRules.cz(chunk) << 4);
    }

    // ------------------------------------------------------------------ ecritures (toutes passent par ici)
    /** Inscrit un claim (clan, index, voisins d'un avant-poste rattache, reseaux coupes a la frontiere, bordures). */
    public static void put(MinecraftServer server, Clan clan, String dim, long chunk, Clan.Claim claim) {
        Map<Long, Clan.Claim> m = clan.claims.computeIfAbsent(dim, k -> new HashMap<>());
        m.put(chunk, claim);
        if (!claim.outpost) {
            //un avant-poste rejoint par le territoire redevient un chunk ordinaire
            for (long n : TerritoryRules.around(chunk)) {
                Clan.Claim x = m.get(n);
                if (x != null && x.outpost) {
                    x.outpost = false;
                }
            }
        }
        ClaimIndex.set(dim, chunk, clan.id);
        ClanData.get(server).changed();
        changed(server, dim, chunk);
    }

    /** Retire un claim : emplacement de chunkloading, designations de Sceau (Sceaux rendus), index, reseaux, bordures. */
    public static void drop(MinecraftServer server, Clan clan, String dim, long chunk, boolean returnSeals) {
        Map<Long, Clan.Claim> m = clan.claims.get(dim);
        if (m == null || m.remove(chunk) == null) {
            return;
        }
        if (m.isEmpty()) {
            clan.claims.remove(dim);
        }
        ClanChunkloading.unload(server, clan, key(dim, chunk), false);
        ClanSeals.dropChunk(server, clan, dim, chunk, returnSeals);
        if (clan.id.equals(ClaimIndex.owner(dim, chunk))) {
            ClaimIndex.set(dim, chunk, null);
        }
        ClanData.get(server).changed();
        changed(server, dim, chunk);
    }

    /** Une frontiere a bouge : reseaux de cables du chunk et de ses voisins refaits, bordures renvoyees. */
    static void changed(MinecraftServer server, String dim, long chunk) {
        ServerLevel level = level(server, dim);
        if (level != null) {
            BorderSplits.changed(level, chunk);
        }
        TerritoryNet.bump();
    }

    /**
     * Clan dissous (appele par les fondations) : chunkloading arrete, designations levees, et les chunks restent occupes {@code
     * dissolved_release_hours} heures (7 jours) avant d'etre libres. Le clan peut disparaitre de {@code ClanData} entre-temps.
     */
    public static void release(MinecraftServer server, Clan clan) {
        long until = ClanClock.now() + TerritoryDefs.get().releaseHours * HOUR;
        TerritorySaved saved = TerritorySaved.get(server);
        ClanChunkloading.stopAll(server, clan);
        for (Map.Entry<String, Map<Long, Clan.Claim>> e : new ArrayList<>(clan.claims.entrySet())) {
            for (long chunk : new ArrayList<>(e.getValue().keySet())) {
                ClanSeals.dropChunk(server, clan, e.getKey(), chunk, false);
                saved.releases.add(new TerritorySaved.Release(e.getKey(), chunk, clan.id, until));
            }
        }
        clan.claims.clear();
        saved.changed();
        ClanData.get(server).changed();
        TerritoryNet.bump();
    }

    /** Apres la reconstruction de l'index (demarrage) : les chunks des clans dissous restent occupes jusqu'a leur liberation. */
    static void reindex(MinecraftServer server) {
        for (TerritorySaved.Release r : TerritorySaved.get(server).releases) {
            if (ClaimIndex.owner(r.dim(), r.chunk()) == null) {
                ClaimIndex.set(r.dim(), r.chunk(), r.clan());
            }
        }
    }

    // ------------------------------------------------------------------ controles espaces (chaque minute)
    static void tickMinute(MinecraftServer server, long now) {
        TerritorySaved saved = TerritorySaved.get(server);
        for (Iterator<TerritorySaved.Release> it = saved.releases.iterator(); it.hasNext(); ) {
            TerritorySaved.Release r = it.next();
            if (r.until() > now) {
                continue;
            }
            it.remove();
            saved.changed();
            if (r.clan().equals(ClaimIndex.owner(r.dim(), r.chunk()))) {
                Clan c = ClanData.get(server).clan(r.clan());
                if (c == null || c.claim(r.dim(), r.chunk()) == null) {
                    ClaimIndex.set(r.dim(), r.chunk(), null);
                    changed(server, r.dim(), r.chunk());
                }
            }
        }
        for (Clan c : new ArrayList<>(ClanData.get(server).clans())) {
            decline(server, c, now);
        }
    }

    /** Budget depasse : les chunks du bord entrent en declin (3 jours reels), du plus exterieur vers l'interieur, puis sont liberes. */
    static void decline(MinecraftServer server, Clan c, long now) {
        //un clan vide attend sa dissolution (7 jours, fondations) : ses chunks ne declinent pas avant
        if (c.claims.isEmpty() || c.members.isEmpty()) {
            return;
        }
        int budget = budget(c, now);
        String hallDim = null;
        long hallChunk = 0;
        String hall = ClanSeals.first(c, "town_hall");
        if (hall != null) {
            int bar = hall.indexOf('|');
            hallDim = hall.substring(0, bar);
            BlockPos hp = BlockPos.of(Long.parseLong(hall.substring(bar + 1)));
            hallChunk = TerritoryRules.chunk(hp.getX() >> 4, hp.getZ() >> 4);
        }
        Map<String, Long> centers = new HashMap<>();
        for (Map.Entry<String, Map<Long, Clan.Claim>> e : c.claims.entrySet()) {
            centers.put(e.getKey(), e.getKey().equals(hallDim) ? hallChunk : TerritoryRules.center(e.getValue().keySet()));
        }
        String mainDim = hallDim != null ? hallDim : c.claims.containsKey("minecraft:overworld") ? "minecraft:overworld"
              : c.claims.keySet().iterator().next();
        List<TerritoryRules.Held> held = new ArrayList<>();
        for (Map.Entry<String, Map<Long, Clan.Claim>> e : c.claims.entrySet()) {
            boolean same = e.getKey().equals(mainDim);
            long center = centers.get(e.getKey());
            for (Map.Entry<Long, Clan.Claim> x : e.getValue().entrySet()) {
                if (!x.getValue().federal) {
                    //une autre dimension que celle de la Mairie passe avant (1e9), puis du plus exterieur vers l'interieur
                    double d = TerritoryRules.distance(x.getKey(), true, center);
                    held.add(new TerritoryRules.Held(key(e.getKey(), x.getKey()), x.getValue().outpost ? TerritoryDefs.get().outpostWeight : 1,
                          same ? d : 1e9 + d));
                }
            }
        }
        Set<String> down = TerritoryRules.declining(held, budget);
        int started = 0;
        List<String[]> free = new ArrayList<>();
        for (Map.Entry<String, Map<Long, Clan.Claim>> e : c.claims.entrySet()) {
            for (Map.Entry<Long, Clan.Claim> x : e.getValue().entrySet()) {
                Clan.Claim claim = x.getValue();
                if (down.contains(key(e.getKey(), x.getKey()))) {
                    if (claim.declineUntil <= 0) {
                        claim.declineUntil = now + TerritoryDefs.get().declineHours * HOUR;
                        started++;
                    } else if (claim.declineUntil <= now) {
                        free.add(new String[]{e.getKey(), String.valueOf(x.getKey())});
                    }
                } else if (claim.declineUntil > 0) {
                    claim.declineUntil = 0;
                    ClanData.get(server).changed();
                }
            }
        }
        if (started > 0) {
            ClanData.get(server).changed();
            TerritoryNet.bump();
            c.log(PioneerApi.day(server), "clan.pioneers.log.decline", String.valueOf(started));
            ClanMessages.members(server, c, Permission.CLAIM, ClanMessages.tagged(c, Component.translatable("clan.pioneers.territory.decline",
                  started, TerritoryDefs.get().declineHours / 24)));
        }
        for (String[] f : free) {
            long chunk = Long.parseLong(f[1]);
            drop(server, c, f[0], chunk, true);
            c.log(PioneerApi.day(server), "clan.pioneers.log.declined", coords(chunk));
        }
        if (!free.isEmpty()) {
            ClanMessages.members(server, c, Permission.CLAIM, ClanMessages.tagged(c, Component.translatable("clan.pioneers.territory.declined",
                  free.size())));
        }
    }
}
