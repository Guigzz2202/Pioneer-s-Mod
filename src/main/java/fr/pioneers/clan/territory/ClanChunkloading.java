package fr.pioneers.clan.territory;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.ClanTreasury;
import fr.pioneers.clan.Permission;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.core.ClanDefs;
import fr.pioneers.clan.core.ClanMessages;
import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.economy.EconomyApi;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.world.ForgeChunkManager;

/**
 * 1.9 (MAJ RP, bloc 4) : chunkloading des clans. Tickets de {@link ForgeChunkManager} au nom du clan (identifiant du clan comme proprietaire
 * du ticket, sous le controleur du mod), valides au demarrage contre {@code ClanData} : un ticket sans claim, sans clan ou d'un clan
 * suspendu est supprime. Emplacements : ceux du niveau (6 au plus, Solitaire 3) ; seulement dans les chunks du clan et les dimensions de la
 * configuration ; plafond du serveur avec file d'attente (ordre d'arrivee). Cout : 200 jetons par jour de jeu et par emplacement, preleves
 * au passage du jour sur le tresor (Solitaire : sur son solde, sinon son tresor) ; tresor vide : preavis de 48 heures affiche aux membres
 * autorises, puis arret (les emplacements restent notes et repartent des que le jour suivant est paye).
 * <p>
 * 1.0.5-beta (lot 13, retours de Guillaume) : les claims ne sont plus charges d'office (classe ClaimLoading du lot 8 de la 1.0.4 retiree :
 * ses tickets sont rendus a la validation de Forge comme tout ticket sans emplacement) ; chaque emplacement se PAIE a la pose :
 * {@link #nextPrice} = min(500, arrondi(50 + 108 ln n)) pour le n-ieme du clan (reglages chunkloadPrice*), en plus du cout du jour.
 */
public final class ClanChunkloading {

    /** Cle de {@code Clan.extra} : chunkloading suspendu faute de paiement. */
    static final String SUSPENDED = "t_cl_suspended";

    private ClanChunkloading() {
    }

    // ------------------------------------------------------------------ lecture
    /** Emplacements du clan (donnees des niveaux ; 1 + niveau / 2 a defaut), 6 au plus, 3 pour un Solitaire. */
    public static int slots(Clan c) {
        TerritoryDefs d = TerritoryDefs.get();
        ClanDefs.LevelDef l = ClanDefs.get().level(c.level);
        int raw = l != null ? l.chunkload() : TerritoryRules.slotsFor(c.level);
        return TerritoryRules.chunkloadSlots(raw, d.chunkloadCap, c.solo, d.soloChunkloadCap);
    }

    public static boolean suspended(Clan c) {
        return c.extra.getBoolean(SUSPENDED);
    }

    /** 1.0.4-beta (lot 8) : le chunk du clan est-il charge en permanence ? 1.0.5-beta (lot 13) : seulement par un emplacement. */
    public static boolean isLoaded(Clan c, String dim, long chunk) {
        return !suspended(c) && c.chunkload.contains(ClanTerritory.key(dim, chunk));
    }

    /** 1.0.5-beta (lot 13) : prix (Shining) du n-ieme emplacement (reglages du serveur) ; rien quand l'economie est coupee. */
    public static long price(int n) {
        if (!EconomyApi.enabled()) {
            return 0;
        }
        return TerritoryRules.chunkloadPrice(PiCommonConfig.get(RpConfig.CHUNKLOAD_PRICE_BASE, 50L), PiCommonConfig.get(RpConfig.CHUNKLOAD_PRICE_COEF, 108.0),
              PiCommonConfig.get(RpConfig.CHUNKLOAD_PRICE_MAX, 500L), n);
    }

    /** 1.0.5-beta (lot 13) : prix du prochain emplacement du clan (rang = emplacements tenus + en file + 1). */
    public static long nextPrice(MinecraftServer server, Clan c) {
        return price(c.chunkload.size() + queued(server, c) + 1);
    }

    /** Chunks charges par tous les clans (emplacements actifs, hors clans suspendus). */
    public static int globalActive(MinecraftServer server) {
        int n = 0;
        for (Clan c : ClanData.get(server).clans()) {
            if (!suspended(c)) {
                n += c.chunkload.size();
            }
        }
        return n;
    }

    public static int globalMax() {
        return PiCommonConfig.get(RpConfig.CHUNKLOAD_GLOBAL_MAX, 200);
    }

    public static boolean dimensionAllowed(String dim) {
        try {
            return RpConfig.CHUNKLOAD_DIMENSIONS.get().contains(dim);
        } catch (IllegalStateException | NullPointerException e) {
            return "minecraft:overworld".equals(dim);
        }
    }

    /** Emplacements demandes en file d'attente pour ce clan. */
    public static int queued(MinecraftServer server, Clan c) {
        int n = 0;
        for (TerritorySaved.Queued q : TerritorySaved.get(server).queue) {
            if (q.clan().equals(c.id)) {
                n++;
            }
        }
        return n;
    }

    /** Cout du jour (jetons) ; rien quand l'economie est coupee. */
    public static long dailyCost(Clan c) {
        return EconomyApi.enabled() ? TerritoryDefs.get().chunkloadCost * c.chunkload.size() : 0;
    }

    // ------------------------------------------------------------------ actions des joueurs
    /** {@code /clan load} : garde le chunk ou se tient le joueur charge en permanence. */
    public static void load(ServerPlayer p) {
        load(p, p.serverLevel(), p.chunkPosition().toLong());
    }

    /** 1.9 (MAJ RP, bloc 4) : emplacement de chunkloading pour un chunk choisi (carte strategique du Carnet). */
    public static void load(ServerPlayer p, ServerLevel level, long chunk) {
        MinecraftServer server = p.getServer();
        Clan clan = ClanData.get(server).clanOf(p.getUUID());
        String dim = ClanApi.dim(level);
        String key = ClanTerritory.key(dim, chunk);
        if (clan == null || clan.claim(dim, chunk) == null) {
            ClanMessages.refuse(p, "clan.pioneers.chunkload.not_ours");
            return;
        }
        if (!ClanTerritory.may(clan, p.getUUID(), Permission.CHUNKLOAD)) {
            ClanMessages.refuse(p, "clan.pioneers.territory.no_permission");
            return;
        }
        if (!dimensionAllowed(dim)) {
            ClanMessages.refuse(p, "clan.pioneers.chunkload.dimension");
            return;
        }
        TerritorySaved saved = TerritorySaved.get(server);
        if (clan.chunkload.contains(key) || saved.queue.stream().anyMatch(q -> q.clan().equals(clan.id) && q.key().equals(key))) {
            ClanMessages.refuse(p, "clan.pioneers.chunkload.already");
            return;
        }
        if (clan.chunkload.size() + queued(server, clan) >= slots(clan)) {
            ClanMessages.refuse(p, "clan.pioneers.chunkload.no_slot", slots(clan));
            return;
        }
        long cost = EconomyApi.enabled() ? TerritoryDefs.get().chunkloadCost : 0;
        //1.0.5-beta (lot 13) : l'emplacement se paie a la pose (n-ieme du clan), le tresor doit aussi couvrir le premier jour
        int rank = clan.chunkload.size() + queued(server, clan) + 1;
        long price = price(rank);
        if (price + cost > 0 && !canPay(server, clan, price + cost)) {
            ClanMessages.refuse(p, "clan.pioneers.chunkload.no_funds_price", price, cost);
            return;
        }
        if (price > 0 && !buy(server, clan, price, rank)) {
            ClanMessages.refuse(p, "clan.pioneers.chunkload.no_funds_price", price, cost);
            return;
        }
        if (suspended(clan) || globalActive(server) >= globalMax()) {
            saved.queue.add(new TerritorySaved.Queued(clan.id, dim, chunk, ClanClock.now()));
            saved.changed();
            ClanMessages.tell(p, "clan.pioneers.chunkload.queued", saved.queue.size());
        } else {
            clan.chunkload.add(key);
            force(server, clan.id, key, true);
            ClanMessages.tell(p, "clan.pioneers.chunkload.loaded", clan.chunkload.size(), slots(clan), cost);
        }
        if (price > 0) {
            ClanMessages.tell(p, "clan.pioneers.chunkload.paid", price, rank);
        }
        clan.log(PioneerApi.day(server), "clan.pioneers.log.chunkload", ClanMessages.character(p), ClanTerritory.coords(chunk));
        ClanData.get(server).changed();
        TerritoryNet.bump();
    }

    /** {@code /clan unload} : libere l'emplacement du chunk ou se tient le joueur. */
    public static void unload(ServerPlayer p) {
        unload(p, p.serverLevel(), p.chunkPosition().toLong());
    }

    /** 1.9 (MAJ RP, bloc 4) : libere l'emplacement d'un chunk choisi (carte strategique du Carnet). */
    public static void unload(ServerPlayer p, ServerLevel level, long chunk) {
        MinecraftServer server = p.getServer();
        Clan clan = ClanData.get(server).clanOf(p.getUUID());
        String key = ClanTerritory.key(ClanApi.dim(level), chunk);
        if (clan == null) {
            ClanMessages.refuse(p, "clan.pioneers.chunkload.not_ours");
            return;
        }
        if (!ClanTerritory.may(clan, p.getUUID(), Permission.CHUNKLOAD)) {
            ClanMessages.refuse(p, "clan.pioneers.territory.no_permission");
            return;
        }
        if (!unload(server, clan, key, true)) {
            ClanMessages.refuse(p, "clan.pioneers.chunkload.not_loaded");
            return;
        }
        clan.log(PioneerApi.day(server), "clan.pioneers.log.chunkunload", ClanMessages.character(p), ClanTerritory.coords(chunk));
        ClanMessages.tell(p, "clan.pioneers.chunkload.unloaded");
    }

    /** Libere un emplacement (ou une demande en attente) ; renvoie vrai s'il y en avait un. Le premier de la file prend la place. */
    public static boolean unload(MinecraftServer server, Clan clan, String key, boolean notify) {
        boolean had = clan.chunkload.remove(key);
        if (had && !suspended(clan)) {
            force(server, clan.id, key, false);
        }
        TerritorySaved saved = TerritorySaved.get(server);
        boolean queuedOne = saved.queue.removeIf(q -> q.clan().equals(clan.id) && q.key().equals(key));
        if (queuedOne) {
            saved.changed();
        }
        if (had || queuedOne) {
            ClanData.get(server).changed();
            TerritoryNet.bump();
            serveQueue(server);
        }
        return had || queuedOne;
    }

    /** Clan dissous ou chunkloading coupe : tous ses tickets et ses demandes sont retires. */
    public static void stopAll(MinecraftServer server, Clan clan) {
        if (!suspended(clan)) {
            for (String key : clan.chunkload) {
                force(server, clan.id, key, false);
            }
        }
        clan.chunkload.clear();
        clan.extra.remove(SUSPENDED);
        clan.chunkloadStopAt = 0;
        TerritorySaved saved = TerritorySaved.get(server);
        if (saved.queue.removeIf(q -> q.clan().equals(clan.id))) {
            saved.changed();
        }
        ClanData.get(server).changed();
        serveQueue(server);
    }

    // ------------------------------------------------------------------ tickets
    static void force(MinecraftServer server, UUID clan, String key, boolean add) {
        int bar = key.indexOf('|');
        ServerLevel level = ClanTerritory.level(server, key.substring(0, bar));
        if (level == null) {
            return;
        }
        long chunk = Long.parseLong(key.substring(bar + 1));
        ForgeChunkManager.forceChunk(level, Pioneers.MODID, clan, TerritoryRules.cx(chunk), TerritoryRules.cz(chunk), add, true);
    }

    /**
     * Validation de Forge au chargement d'un monde (appelee par le controleur de tickets du mod) : un ticket au nom d'un clan n'est garde que
     * si le clan existe, n'est pas suspendu, tient encore le chunk et l'a dans ses emplacements, dans une dimension permise.
     */
    public static void validate(ServerLevel level, ForgeChunkManager.TicketHelper helper) {
        ClanData data = ClanData.get(level.getServer());
        String dim = ClanApi.dim(level);
        //1.0.5-beta (lot 13) : plus de claims charges d'office : les tickets de l'ancien mode (meme proprietaire, sans emplacement) tombent ici
        for (var e : new ArrayList<>(helper.getEntityTickets().entrySet())) {
            UUID owner = e.getKey();
            Clan c = data.clan(owner);
            List<Long> chunks = new ArrayList<>();
            e.getValue().getFirst().forEach(chunks::add);
            e.getValue().getSecond().forEach(chunks::add);
            for (long chunk : chunks) {
                boolean keep = c != null && !suspended(c) && dimensionAllowed(dim) && c.claim(dim, chunk) != null
                               && c.chunkload.contains(ClanTerritory.key(dim, chunk));
                if (!keep) {
                    helper.removeTicket(owner, chunk, true);
                    helper.removeTicket(owner, chunk, false);
                }
            }
        }
    }

    /** Au demarrage du serveur : emplacements notes mais sans ticket (dimension ajoutee, ancien monde) remis, au-dela des plafonds mis en file. */
    static void reconcile(MinecraftServer server) {
        int active = 0;
        TerritorySaved saved = TerritorySaved.get(server);
        for (Clan c : ClanData.get(server).clans()) {
            if (suspended(c)) {
                continue;
            }
            for (Iterator<String> it = c.chunkload.iterator(); it.hasNext(); ) {
                String key = it.next();
                int bar = key.indexOf('|');
                String dim = key.substring(0, bar);
                long chunk = Long.parseLong(key.substring(bar + 1));
                if (c.claim(dim, chunk) == null || !dimensionAllowed(dim)) {
                    it.remove();
                    force(server, c.id, key, false);
                    continue;
                }
                if (active >= globalMax()) {
                    it.remove();
                    force(server, c.id, key, false);
                    saved.queue.add(new TerritorySaved.Queued(c.id, dim, chunk, ClanClock.now()));
                    saved.changed();
                    continue;
                }
                active++;
                force(server, c.id, key, true);
            }
            while (c.chunkload.size() > slots(c)) {
                String key = c.chunkload.remove(c.chunkload.size() - 1);
                force(server, c.id, key, false);
            }
        }
        ClanData.get(server).changed();
    }

    /** Sert la file d'attente tant qu'il reste de la place sous le plafond du serveur. */
    static void serveQueue(MinecraftServer server) {
        TerritorySaved saved = TerritorySaved.get(server);
        if (saved.queue.isEmpty()) {
            return;
        }
        ClanData data = ClanData.get(server);
        int active = globalActive(server);
        for (Iterator<TerritorySaved.Queued> it = saved.queue.iterator(); it.hasNext() && active < globalMax(); ) {
            TerritorySaved.Queued q = it.next();
            Clan c = data.clan(q.clan());
            if (c == null || c.claim(q.dim(), q.chunk()) == null) {
                it.remove();
                saved.changed();
                continue;
            }
            if (suspended(c) || c.chunkload.size() >= slots(c)) {
                continue;
            }
            it.remove();
            saved.changed();
            c.chunkload.add(q.key());
            force(server, c.id, q.key(), true);
            active++;
            data.changed();
            ClanMessages.members(server, c, Permission.CHUNKLOAD, ClanMessages.tagged(c, Component.translatable("clan.pioneers.chunkload.served",
                  ClanTerritory.coords(q.chunk()))));
        }
        TerritoryNet.bump();
    }

    // ------------------------------------------------------------------ paiement
    @Nullable
    private static ServerPlayer soloPlayer(MinecraftServer server, Clan c) {
        return c.solo && c.representative != null ? server.getPlayerList().getPlayer(c.representative) : null;
    }

    static boolean canPay(MinecraftServer server, Clan c, long amount) {
        if (amount <= 0) {
            return true;
        }
        ServerPlayer p = soloPlayer(server, c);
        if (p != null && EconomyApi.canDebit(p, amount).ok()) {
            return true;
        }
        return !c.frozen && c.treasury >= amount;
    }

    /** Preleve le cout : Solitaire connecte sur son solde, sinon le tresor du clan. Rien n'est preleve si tout ne peut l'etre. */
    static boolean pay(MinecraftServer server, Clan c, long amount) {
        if (amount <= 0) {
            return true;
        }
        ServerPlayer p = soloPlayer(server, c);
        if (p != null && EconomyApi.debit(p, amount, EconomyApi.Entry.of("chunkload")).ok()) {
            return true;
        }
        return ClanTreasury.debit(server, c, amount, "#clan.pioneers.reason.chunkload", "");
    }

    /** 1.0.5-beta (lot 13) : achat d'un emplacement (rang {@code rank}) : Solitaire connecte sur son solde, sinon le tresor du clan. */
    static boolean buy(MinecraftServer server, Clan c, long amount, int rank) {
        if (amount <= 0) {
            return true;
        }
        ServerPlayer p = soloPlayer(server, c);
        if (p != null && EconomyApi.debit(p, amount, EconomyApi.Entry.of("chunkload")).ok()) {
            return true;
        }
        return ClanTreasury.debit(server, c, amount, "#clan.pioneers.reason.chunkload_buy|" + rank, "");
    }

    /** Passage du jour de jeu : chaque clan paie ses emplacements ; sinon preavis (48 h), puis arret. Un clan suspendu repart s'il paie. */
    static void dayChange(MinecraftServer server, long now) {
        TerritoryDefs d = TerritoryDefs.get();
        for (Clan c : new ArrayList<>(ClanData.get(server).clans())) {
            if (c.chunkload.isEmpty()) {
                c.chunkloadStopAt = 0;
                continue;
            }
            long cost = dailyCost(c);
            if (pay(server, c, cost)) {
                if (c.chunkloadStopAt > 0 || suspended(c)) {
                    resume(server, c);
                }
                continue;
            }
            if (c.chunkloadStopAt <= 0) {
                c.chunkloadStopAt = now + d.chunkloadNoticeHours * ClanTerritory.HOUR;
                ClanData.get(server).changed();
                c.log(PioneerApi.day(server), "clan.pioneers.log.chunkload_notice", String.valueOf(cost));
            }
            if (!suspended(c)) {
                long hours = Math.max(0, (c.chunkloadStopAt - now) / ClanTerritory.HOUR);
                ClanMessages.members(server, c, Permission.CHUNKLOAD, ClanMessages.tagged(c, Component.translatable("clan.pioneers.chunkload.notice",
                      cost, hours)));
            }
        }
    }

    /** Controle espace (chaque minute) : preavis ecoule, le chunkloading s'arrete. */
    static void tickMinute(MinecraftServer server, long now) {
        for (Clan c : ClanData.get(server).clans()) {
            if (c.chunkloadStopAt > 0 && now >= c.chunkloadStopAt && !suspended(c) && !c.chunkload.isEmpty()) {
                for (String key : c.chunkload) {
                    force(server, c.id, key, false);
                }
                c.extra.putBoolean(SUSPENDED, true);
                ClanData.get(server).changed();
                c.log(PioneerApi.day(server), "clan.pioneers.log.chunkload_stop");
                ClanMessages.members(server, c, Permission.CHUNKLOAD, ClanMessages.tagged(c,
                      Component.translatable("clan.pioneers.chunkload.stopped")));
                TerritoryNet.bump();
                serveQueue(server);
            }
        }
        serveQueue(server);
    }

    /** Paiement revenu : tickets remis (au plafond du serveur pres, le reste en file). */
    static void resume(MinecraftServer server, Clan c) {
        boolean was = suspended(c);
        c.chunkloadStopAt = 0;
        c.extra.remove(SUSPENDED);
        if (was) {
            int active = globalActive(server) - c.chunkload.size();
            TerritorySaved saved = TerritorySaved.get(server);
            for (Iterator<String> it = c.chunkload.iterator(); it.hasNext(); ) {
                String key = it.next();
                if (active >= globalMax()) {
                    it.remove();
                    int bar = key.indexOf('|');
                    saved.queue.add(new TerritorySaved.Queued(c.id, key.substring(0, bar), Long.parseLong(key.substring(bar + 1)), ClanClock.now()));
                    saved.changed();
                    continue;
                }
                force(server, c.id, key, true);
                active++;
            }
            ClanMessages.members(server, c, Permission.CHUNKLOAD, ClanMessages.tagged(c, Component.translatable("clan.pioneers.chunkload.resumed")));
        }
        ClanData.get(server).changed();
        TerritoryNet.bump();
    }

    /** Un Solitaire se connecte avec un chunkloading en preavis ou arrete : il paie le jour sur son solde s'il le peut. */
    static void onSoloLogin(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        Clan c = ClanData.get(server).clanOf(p.getUUID());
        if (c == null || !c.solo || c.chunkload.isEmpty() || c.chunkloadStopAt <= 0 && !suspended(c)) {
            return;
        }
        if (pay(server, c, dailyCost(c))) {
            resume(server, c);
        }
    }
}
