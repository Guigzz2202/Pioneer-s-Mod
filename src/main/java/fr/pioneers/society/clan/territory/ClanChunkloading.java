package fr.pioneers.society.clan.territory;

import fr.pioneers.Pioneers;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.ClanTreasury;
import fr.pioneers.society.clan.Permission;
import fr.pioneers.society.clan.core.ClanClock;
import fr.pioneers.society.clan.core.ClanDefs;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.society.economy.EconomyApi;
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
 * <p>
 * 1.0.6 (Guillaume, 17/11/2026 : « on doit pouvoir acheter comme on le souhaite (en dehors de la map) des claims et des chunkloads pour
 * preparer le stock dispo ; les claims chunkloades sont charges indefiniment et gratuitement (aucun prelevement journalier) ; on doit pouvoir
 * drag and drop pour chunkloader comme on le fait pour claim ») : les emplacements s'ACHETENT au tresor ({@link #buySlots}, n-ieme au prix
 * {@link #price}) et forment le STOCK du clan ({@code Clan.boughtChunkloads}) ; charger un chunk en prend un, sans rien payer ; plus de cout du
 * jour ni d'arret faute de paiement (un clan suspendu d'avant repart) ; zone tracee sur la carte ({@link #area}). Le plafond du serveur et sa
 * file d'attente restent.
 */
public final class ClanChunkloading {

    /** Cle de {@code Clan.extra} : chunkloading suspendu faute de paiement. */
    static final String SUSPENDED = "t_cl_suspended";

    private ClanChunkloading() {
    }

    // ------------------------------------------------------------------ lecture
    /** 1.0.6 : emplacements du clan = son stock achete ({@link #buySlots}). */
    public static int slots(Clan c) {
        return Math.max(0, c.boughtChunkloads);
    }

    /** 1.0.6 : emplacements du stock encore libres (ni charges ni en file). */
    public static int free(MinecraftServer server, Clan c) {
        return Math.max(0, slots(c) - c.chunkload.size() - queued(server, c));
    }

    /** 1.0.6 : emplacements achetes au plus : sans plafond par defaut (reglages chunkloadBuyLimit, chunkloadBuySoloLimit). */
    public static int buyCap(Clan c) {
        int cap = c.solo ? PiCommonConfig.get(RpConfig.CHUNKLOAD_BUY_SOLO_MAX, 0) : PiCommonConfig.get(RpConfig.CHUNKLOAD_BUY_MAX, 0);
        return cap <= 0 ? Integer.MAX_VALUE : cap;
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

    /** 1.0.6 : prix du prochain emplacement achete (rang = emplacements deja achetes + 1). */
    public static long nextPrice(MinecraftServer server, Clan c) {
        return price(c.boughtChunkloads + 1);
    }

    /** 1.0.6 : prix de {@code n} emplacements de plus (rangs achetes + 1 a achetes + n). */
    public static long priceOf(Clan c, int n) {
        long sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += price(c.boughtChunkloads + i);
        }
        return sum;
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

    /** 1.0.6 (Guillaume, 17/11/2026 : « retire tous les plafonds, au pire on change dans les configs ») : sans plafond par defaut. */
    public static int globalMax() {
        int cap = PiCommonConfig.get(RpConfig.CHUNKLOAD_SERVER_MAX, 0);
        return cap <= 0 ? Integer.MAX_VALUE : cap;
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

    /** Cout du jour : rien depuis la 1.0.6 (« charges indefiniment et gratuitement »). */
    public static long dailyCost(Clan c) {
        return 0;
    }

    /**
     * 1.0.6 : achete {@code n} emplacements au tresor du clan (Solitaire connecte : sur son solde d'abord), au prix du rang de chacun. Meme droit
     * que l'achat de claims ({@code BUY_CLAIMS}) ; {@code seen} = emplacements achetes vus par le client (-1 : ne pas verifier).
     *
     * @return cle du refus, ou null (achat fait, message au joueur)
     */
    @Nullable
    public static String buySlots(ServerPlayer p, int n, int seen) {
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
        if (!ClanTerritory.mayBuy(c, p.getUUID())) {
            return "clan.pioneers.territory.buy.no_permission";
        }
        if (c.frozen) {
            return "clan.pioneers.error.frozen";
        }
        if (seen >= 0 && seen != c.boughtChunkloads) {
            return "clan.pioneers.territory.buy.stale";
        }
        if (n <= 0 || n > TerritoryRules.BUY_STEP_MAX) {
            return "clan.pioneers.error.amount";
        }
        if ((long) c.boughtChunkloads + n > buyCap(c)) {
            return "clan.pioneers.chunkload.buy.cap";
        }
        long cost = priceOf(c, n);
        if (!canPay(server, c, cost)) {
            return "clan.pioneers.territory.buy.treasury";
        }
        if (!buy(server, c, cost, c.boughtChunkloads + 1)) {
            return "clan.pioneers.territory.buy.treasury";
        }
        c.boughtChunkloads += n;
        c.log(PioneerApi.day(server), "clan.pioneers.log.buy_chunkloads", ClanMessages.character(p), String.valueOf(n), String.valueOf(cost));
        ClanData.get(server).changed();
        TerritoryNet.bump();
        ClanMessages.tell(p, "clan.pioneers.chunkload.buy.done", n, cost, free(server, c), slots(c));
        return null;
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
        //1.0.6 : un emplacement du stock achete (rien a payer a la pose ni chaque jour)
        if (free(server, clan) <= 0) {
            ClanMessages.refuse(p, "clan.pioneers.chunkload.no_stock", slots(clan));
            return;
        }
        if (!place(server, clan, dim, chunk)) {
            ClanMessages.tell(p, "clan.pioneers.chunkload.queued", saved.queue.size());
        } else {
            ClanMessages.tell(p, "clan.pioneers.chunkload.loaded_stock", clan.chunkload.size(), slots(clan));
        }
        clan.log(PioneerApi.day(server), "clan.pioneers.log.chunkload", ClanMessages.character(p), ClanTerritory.coords(chunk));
        ClanData.get(server).changed();
        TerritoryNet.bump();
    }

    /** 1.0.6 : pose d'un emplacement (stock deja verifie) : charge tout de suite, ou en file au plafond du serveur (faux). */
    private static boolean place(MinecraftServer server, Clan clan, String dim, long chunk) {
        String key = ClanTerritory.key(dim, chunk);
        TerritorySaved saved = TerritorySaved.get(server);
        if (globalActive(server) >= globalMax()) {
            saved.queue.add(new TerritorySaved.Queued(clan.id, dim, chunk, ClanClock.now()));
            saved.changed();
            return false;
        }
        clan.chunkload.add(key);
        force(server, clan.id, key, true);
        return true;
    }

    /**
     * 1.0.6 (Guillaume : « drag and drop pour chunkloader comme on le fait pour claim ») : zone tracee sur la carte, chargee ({@code load}) ou
     * liberee. Meme recapitulatif que les claims ({@link ClanTerritory#area}) : {@code ok} chunks acceptes, {@code cost} emplacements pris ou
     * rendus, {@code budget} stock, {@code left} libres apres, {@code skipped}, {@code refused} [{r, n, arg}], {@code cells} (0 accepte, 1
     * ignore, 2 refuse), {@code error}, {@code done} si applique. Le serveur refait tout le plan a l'application.
     */
    public static net.minecraft.nbt.CompoundTag area(ServerPlayer p, ServerLevel level, int x0, int z0, int x1, int z1, boolean load, boolean apply) {
        net.minecraft.nbt.CompoundTag out = new net.minecraft.nbt.CompoundTag();
        out.putInt("mode", load ? 2 : 3);
        out.putBoolean("claim", false);
        out.putInt("x0", Math.min(x0, x1));
        out.putInt("z0", Math.min(z0, z1));
        out.putInt("x1", Math.max(x0, x1));
        out.putInt("z1", Math.max(z0, z1));
        List<Long> rect = TerritoryRules.rectangle(x0, z0, x1, z1);
        MinecraftServer server = p.getServer();
        Clan clan = ClanData.get(server).clanOf(p.getUUID());
        if (!ClanApi.enabled()) {
            out.putString("error", "clan.pioneers.territory.disabled");
            return out;
        }
        if (rect.size() > TerritoryRules.AREA_MAX) {
            out.putString("error", "clan.pioneers.ui.area.too_big");
            return out;
        }
        if (clan == null) {
            out.putString("error", "clan.pioneers.chunkload.not_ours");
            return out;
        }
        if (!ClanTerritory.may(clan, p.getUUID(), Permission.CHUNKLOAD)) {
            out.putString("error", "clan.pioneers.territory.no_permission");
            return out;
        }
        String dim = ClanApi.dim(level);
        if (load && !dimensionAllowed(dim)) {
            out.putString("error", "clan.pioneers.chunkload.dimension");
            return out;
        }
        TerritorySaved saved = TerritorySaved.get(server);
        int stock = slots(clan), freeNow = free(server, clan);
        List<Long> ok = new ArrayList<>();
        int skipped = 0, notOurs = 0, noSlot = 0;
        byte[] cells = new byte[rect.size()];
        for (int i = 0; i < rect.size(); i++) {
            long chunk = rect.get(i);
            String key = ClanTerritory.key(dim, chunk);
            boolean held = clan.chunkload.contains(key) || saved.queue.stream().anyMatch(q -> q.clan().equals(clan.id) && q.key().equals(key));
            if (clan.claim(dim, chunk) == null) {
                if (load) {
                    notOurs++;
                    cells[i] = 2;
                } else {
                    skipped++;
                    cells[i] = 1;
                }
            } else if (load == held) {
                skipped++;
                cells[i] = 1;
            } else if (load && ok.size() >= freeNow) {
                noSlot++;
                cells[i] = 2;
            } else {
                ok.add(chunk);
                cells[i] = 0;
            }
        }
        out.putInt("ok", ok.size());
        out.putInt("cost", ok.size());
        out.putInt("used", stock - freeNow);
        out.putInt("budget", stock);
        out.putInt("left", load ? freeNow - ok.size() : freeNow + ok.size());
        out.putInt("skipped", skipped);
        out.putByteArray("cells", cells);
        net.minecraft.nbt.ListTag why = new net.minecraft.nbt.ListTag();
        if (notOurs > 0) {
            net.minecraft.nbt.CompoundTag r = new net.minecraft.nbt.CompoundTag();
            r.putString("r", "cl_not_ours");
            r.putInt("n", notOurs);
            r.putString("arg", "");
            why.add(r);
        }
        if (noSlot > 0) {
            net.minecraft.nbt.CompoundTag r = new net.minecraft.nbt.CompoundTag();
            r.putString("r", "cl_no_stock");
            r.putInt("n", noSlot);
            r.putString("arg", String.valueOf(stock));
            why.add(r);
        }
        out.put("refused", why);
        if (!apply || ok.isEmpty()) {
            return out;
        }
        int queued = 0;
        for (long chunk : ok) {
            if (load) {
                if (!place(server, clan, dim, chunk)) {
                    queued++;
                }
            } else {
                unload(server, clan, ClanTerritory.key(dim, chunk), false);
            }
        }
        String where = ClanTerritory.coords(TerritoryRules.chunk((Math.min(x0, x1) + Math.max(x0, x1)) / 2, (Math.min(z0, z1) + Math.max(z0, z1)) / 2));
        clan.log(PioneerApi.day(server), load ? "clan.pioneers.log.chunkload_area" : "clan.pioneers.log.chunkunload_area", ClanMessages.character(p),
              String.valueOf(ok.size()), where);
        ClanMessages.tell(p, load ? "clan.pioneers.chunkload.area_loaded" : "clan.pioneers.chunkload.area_unloaded", ok.size(), free(server, clan),
              slots(clan));
        if (queued > 0) {
            ClanMessages.tell(p, "clan.pioneers.chunkload.queued", saved.queue.size());
        }
        ClanData.get(server).changed();
        TerritoryNet.bump();
        out.putBoolean("done", true);
        return out;
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

    /**
     * Passage du jour de jeu. 1.0.6 : plus aucun prelevement (« charges indefiniment et gratuitement ») : un clan en preavis ou arrete par
     * l'ancienne regle repart.
     */
    public static void dayChange(MinecraftServer server, long now) {
        for (Clan c : new ArrayList<>(ClanData.get(server).clans())) {
            if (c.chunkloadStopAt > 0 || suspended(c)) {
                resume(server, c);
            }
        }
    }

    /** Controle chaque minute : la file d'attente du plafond du serveur ; 1.0.6 : un clan arrete par l'ancienne regle repart. */
    static void tickMinute(MinecraftServer server, long now) {
        for (Clan c : new ArrayList<>(ClanData.get(server).clans())) {
            if (c.chunkloadStopAt > 0 || suspended(c)) {
                resume(server, c);
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
        if (c == null || !c.solo || c.chunkloadStopAt <= 0 && !suspended(c)) {
            return;
        }
        resume(server, c);   //1.0.6 : plus de cout du jour
    }
}
