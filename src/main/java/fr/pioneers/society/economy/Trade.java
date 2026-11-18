package fr.pioneers.society.economy;

import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 3) : l'echange avec un PNJ (villageois ou Colporteur), un seul ecran pour les deux. Le serveur garde la session
 * (partenaire), construit la vue (offres et devis de {@link PriceService}), et execute chaque achat, vente ou service en une seule
 * transaction : tout est verifie (partenaire, devis, solde, place, objets, quota), puis tout est modifie. Le client ne demande qu'une ligne et
 * une quantite.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Trade {

    /** Une offre de vente du partenaire : objet (quantite = lot), stock restant en lots (-1 : sans limite). */
    public record Offer(ItemStack stack, int stock) {
    }

    /** Partenaire d'echange (villageois, Colporteur). */
    public interface TradePartner {

        /** Encore la, vivant, a portee du joueur. */
        boolean valid(ServerPlayer player);

        Component tradeName();

        PriceService.Partner pricing(ServerPlayer player);

        /**
         * Facteurs propres a un objet (evenement du village : penurie, surplus ; quota : services, penurie ; bonus de doctrine a la vente) :
         * ils remplacent ceux de {@link #pricing} pour cet objet. Par defaut, ceux de {@link #pricing}.
         */
        default PriceService.Partner pricing(ServerPlayer player, ItemStack stack, PriceService.Side side) {
            return pricing(player);
        }

        /** Refus d'echanger (cle de langue) ou null. */
        @Nullable
        String refusal(ServerPlayer player);

        List<Offer> offers(ServerPlayer player);

        /** Le partenaire achete-t-il cet objet au joueur ? */
        boolean buys(ServerPlayer player, ItemStack stack);

        /** Objets que le partenaire achete (pour la liste de l'onglet Vendre, meme absents de l'inventaire). */
        List<Item> buyList(ServerPlayer player);

        int boughtToday(Item item);

        void recordBought(Item item, int count);

        void recordSold(int offer, int lots);

        /** Service propose (« repair », « sharpen ») ou null ; {@link #serviceAccepts} dit sur quels objets. */
        @Nullable
        default String service() {
            return null;
        }

        default boolean serviceAccepts(ItemStack stack) {
            return false;
        }

        /** Facteur du prix d'un service (affinite, services du village) ; 0 = gratuit. */
        default double serviceFactor(ServerPlayer player) {
            return 1;
        }

        /** Apres un echange reussi (affinite, XP de Commerce, reputation, voix). */
        void traded(ServerPlayer player, PriceService.Side side, ItemStack stack, int count, long total);

        /** Ligne dite a l'ouverture, quand le joueur ne peut pas payer, quand le quota est atteint (cles de langue) ou null. */
        @Nullable
        default String voice(String moment) {
            return null;
        }

        BlockPos tradePos();

        default void closed(ServerPlayer player) {
        }
    }

    private static final Map<UUID, TradePartner> SESSIONS = new HashMap<>();

    private Trade() {
    }

    static void init() {
        RpNet.onServer(RpActions.TRADE_BUY, (p, t) -> buy(p, t.getInt("i"), t.getInt("n")));
        RpNet.onServer(RpActions.TRADE_SELL, (p, t) -> sell(p, t.getString("item"), t.getInt("n")));
        RpNet.onServer(RpActions.TRADE_SERVICE, (p, t) -> service(p, t.getInt("slot")));
        RpNet.onServer(RpActions.TRADE_QUOTE, (p, t) -> {
            if (t.getBoolean("close")) {
                close(p);
            } else {
                send(p, null);
            }
        });
    }

    /** Ouvre l'echange (appele par le villageois ou le Colporteur). */
    public static void open(ServerPlayer player, TradePartner partner) {
        String refusal = partner.refusal(player);
        if (refusal != null) {
            player.displayClientMessage(Component.translatable(refusal), true);
            player.level().playSound(null, player.blockPosition(), RpSounds.BOOK_CLOSE.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
            return;
        }
        TradePartner old = SESSIONS.put(player.getUUID(), partner);
        if (old != null && old != partner) {
            old.closed(player);
        }
        send(player, partner.voice("open"));
    }

    public static void close(ServerPlayer player) {
        TradePartner p = SESSIONS.remove(player.getUUID());
        if (p != null) {
            p.closed(player);
        }
    }

    @Nullable
    public static TradePartner session(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            close(p);
        }
    }

    // ------------------------------------------------------------------ vue
    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** Envoie la vue complete (offres, devis, inventaire utile, solde, quota) et un message eventuel. */
    static void send(ServerPlayer player, @Nullable String message) {
        TradePartner partner = SESSIONS.get(player.getUUID());
        if (partner == null || !partner.valid(player)) {
            close(player);
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putString("name", Component.Serializer.toJson(partner.tradeName()));
        t.putLong("balance", EconomyApi.balance(player));
        t.putInt("tax", fr.pioneers.society.clan.ClanTreasury.taxRate(player));
        ListTag buy = new ListTag();
        List<Offer> offers = partner.offers(player);
        for (int i = 0; i < offers.size(); i++) {
            Offer o = offers.get(i);
            PriceService.Quote q = PriceService.quote(player, partner.pricing(player, o.stack(), PriceService.Side.BUY), o.stack(),
                  o.stack().getCount(), PriceService.Side.BUY, 0);
            CompoundTag e = new CompoundTag();
            e.put("stack", o.stack().save(new CompoundTag()));
            e.putInt("stock", o.stock());
            e.putLong("unit", q.unit());
            e.putInt("pct", q.percent());
            e.putLong("lot", q.unit() * o.stack().getCount());
            if (q.refusal() != null) {
                e.putString("refusal", q.refusal());
            }
            buy.add(e);
        }
        t.put("buy", buy);
        ListTag sell = new ListTag();
        for (Item item : partner.buyList(player)) {
            ItemStack one = new ItemStack(item);
            int have = count(player.getInventory(), item);
            PriceService.Quote q = PriceService.quote(player, partner.pricing(player, one, PriceService.Side.SELL), one, 1, PriceService.Side.SELL,
                  partner.boughtToday(item));
            CompoundTag e = new CompoundTag();
            e.putString("item", id(item));
            e.putInt("have", have);
            e.putLong("unit", q.unit());
            e.putInt("pct", q.percent());
            e.putInt("left", q.quotaLeft());
            if (q.refusal() != null) {
                e.putString("refusal", q.refusal());
            }
            sell.add(e);
        }
        t.put("sell", sell);
        if (partner.service() != null) {
            t.putString("service", partner.service());
            ListTag svc = new ListTag();
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && s.isDamaged() && partner.serviceAccepts(s)) {
                    CompoundTag e = new CompoundTag();
                    e.putInt("slot", i);
                    e.put("stack", s.save(new CompoundTag()));
                    e.putLong("price", servicePrice(player, partner, s));
                    svc.add(e);
                }
            }
            t.put("services", svc);
        }
        if (message != null) {
            t.putString("message", message);
        }
        RpNet.send(player, RpActions.TRADE_VIEW, t);
    }

    static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && sellable(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Un objet abime, renomme ou enchante ne se vend pas a un PNJ (sa valeur n'est pas celle de la table). */
    public static boolean sellable(ItemStack s) {
        return !s.isDamaged() && !s.hasCustomHoverName() && !s.isEnchanted();
    }

    /** Place libre dans l'inventaire principal pour cet objet (simulation, sans rien changer). */
    static int room(Inventory inv, ItemStack like) {
        int room = 0;
        int max = Math.min(like.getMaxStackSize(), inv.getMaxStackSize());
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (s.isEmpty()) {
                room += max;
            } else if (ItemStack.isSameItemSameTags(s, like)) {
                room += Math.max(0, max - s.getCount());
            }
        }
        return room;
    }

    // ------------------------------------------------------------------ achat (le joueur achete)
    static void buy(ServerPlayer player, int index, int lots) {
        TradePartner partner = SESSIONS.get(player.getUUID());
        if (partner == null || !partner.valid(player) || lots <= 0) {
            close(player);
            return;
        }
        List<Offer> offers = partner.offers(player);
        if (index < 0 || index >= offers.size()) {
            send(player, null);
            return;
        }
        Offer o = offers.get(index);
        int n = o.stock() < 0 ? Math.min(lots, 64) : Math.min(lots, o.stock());
        if (n <= 0) {
            send(player, "economy.pioneers.trade.sold_out");
            return;
        }
        int units = o.stack().getCount() * n;
        PriceService.Quote q = PriceService.quote(player, partner.pricing(player, o.stack(), PriceService.Side.BUY), o.stack(), units,
              PriceService.Side.BUY, 0);
        if (!q.ok() || q.total() <= 0) {
            send(player, q.ok() ? "economy.pioneers.no_value" : q.refusal());
            return;
        }
        //verifications : solde, place
        if (EconomyApi.canDebit(player, q.total()) != EconomyApi.Result.OK) {
            String v = partner.voice("broke");
            send(player, v != null ? v : "economy.pioneers.result.insufficient");
            return;
        }
        if (room(player.getInventory(), o.stack()) < units) {
            send(player, "economy.pioneers.trade.no_room");
            return;
        }
        //modifications
        EconomyApi.debit(player, q.total(), new EconomyApi.Entry("buy", ledgerName(partner), id(o.stack().getItem()), units));
        int left = units;
        while (left > 0) {
            ItemStack give = o.stack().copyWithCount(Math.min(left, o.stack().getMaxStackSize()));
            left -= give.getCount();
            player.getInventory().add(give);
        }
        partner.recordSold(index, n);
        partner.traded(player, PriceService.Side.BUY, o.stack(), units, q.total());
        xp(player, partner, q.total());
        advance(player, partner);
        player.level().playSound(null, partner.tradePos(), RpSounds.QUILL.get(), SoundSource.NEUTRAL, 0.8F, 1.1F);
        send(player, null);
    }

    // ------------------------------------------------------------------ vente (le joueur vend)
    static void sell(ServerPlayer player, String itemId, int wanted) {
        TradePartner partner = SESSIONS.get(player.getUUID());
        if (partner == null || !partner.valid(player) || wanted <= 0) {
            close(player);
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        ItemStack one = new ItemStack(item);
        if (one.isEmpty() || !partner.buys(player, one)) {
            send(player, "economy.pioneers.trade.not_bought");
            return;
        }
        Inventory inv = player.getInventory();
        int have = count(inv, item);
        int n = Math.min(wanted, have);
        PriceService.Quote q = PriceService.quote(player, partner.pricing(player, one, PriceService.Side.SELL), one, n, PriceService.Side.SELL,
              partner.boughtToday(item));
        if (!q.ok()) {
            String v = "economy.pioneers.quota_reached".equals(q.refusal()) ? partner.voice("quota") : null;
            send(player, v != null ? v : q.refusal());
            return;
        }
        n = q.count();
        if (EconomyApi.canCredit(player, q.total()) != EconomyApi.Result.OK) {
            send(player, "economy.pioneers.cap");
            return;
        }
        //bloc 4 : l'impot d'activite du clan est preleve a la source, dans la meme section que la vente (affiche dans l'ecran avant la vente)
        //modifications : retirer exactement n objets vendables, puis crediter
        int left = n;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && sellable(s)) {
                int k = Math.min(left, s.getCount());
                s.shrink(k);
                left -= k;
            }
        }
        inv.setChanged();
        fr.pioneers.society.clan.ClanTreasury.creditWithTax(player, q.total(), new EconomyApi.Entry("sell", ledgerName(partner), itemId, n));
        partner.recordBought(item, n);
        partner.traded(player, PriceService.Side.SELL, one, n, q.total());
        xp(player, partner, q.total());
        advance(player, partner);
        player.level().playSound(null, partner.tradePos(), RpSounds.QUILL.get(), SoundSource.NEUTRAL, 0.8F, 0.95F);
        send(player, null);
    }

    // ------------------------------------------------------------------ services (reparation, affutage)
    static long servicePrice(ServerPlayer player, TradePartner partner, ItemStack s) {
        //un tiers de la valeur de base de l'objet (paliers d'affinite et services du village : serviceFactor)
        long base = Values.value(new ItemStack(s.getItem()));
        double price = base * Values.setting("repair", 1.0 / 3.0) * partner.serviceFactor(player) / 100.0;
        return price <= 0 ? 0 : Math.max(1, Math.round(price));
    }

    static void service(ServerPlayer player, int slot) {
        TradePartner partner = SESSIONS.get(player.getUUID());
        if (partner == null || !partner.valid(player) || partner.service() == null) {
            close(player);
            return;
        }
        Inventory inv = player.getInventory();
        if (slot < 0 || slot >= inv.getContainerSize()) {
            return;
        }
        ItemStack s = inv.getItem(slot);
        if (s.isEmpty() || !s.isDamaged() || !partner.serviceAccepts(s)) {
            send(player, null);
            return;
        }
        long price = servicePrice(player, partner, s);
        if (EconomyApi.canDebit(player, price) != EconomyApi.Result.OK) {
            String v = partner.voice("broke");
            send(player, v != null ? v : "economy.pioneers.result.insufficient");
            return;
        }
        EconomyApi.debit(player, price, new EconomyApi.Entry(partner.service(), ledgerName(partner), id(s.getItem()), 1));
        s.setDamageValue(0);
        inv.setChanged();
        partner.traded(player, PriceService.Side.BUY, s, 0, price);
        player.level().playSound(null, partner.tradePos(), net.minecraft.sounds.SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.4F, 1.4F);
        send(player, null);
    }

    /**
     * 1.0.3-beta (lot 13) : nom du partenaire dans le compte. Avant : {@code getString()} ecrit par le serveur, qui donnait la CLE de langue
     * d'un PNJ sans nom propre sur un serveur dedie ; maintenant « #cle » ou JSON, traduits dans le Carnet ({@link LedgerLabels#store}).
     */
    private static String ledgerName(TradePartner partner) {
        return LedgerLabels.store(partner.tradeName());
    }

    /** XP de Commerce : 1 par tranche de 50 jetons du total, au moins 1. */
    /** 1.9 (MAJ RP, lot 19) : progres du commerce (un echange avec un PNJ ; avec un Colporteur). */
    private static void advance(ServerPlayer player, TradePartner partner) {
        fr.pioneers.core.registries.PiTriggers.MECHANICS.trigger(player, "economy_trade");
        if (partner.pricing(player).kind() == PriceService.Kind.PEDDLER) {
            fr.pioneers.core.registries.PiTriggers.MECHANICS.trigger(player, "economy_peddler");
        }
    }

    private static void xp(ServerPlayer player, TradePartner partner, long total) {
        if (total > 0) {
            PioneerApi.gain(player, "trade.npc", Math.max(1, total / 50), partner.tradePos());
        }
    }

    /** Liste des objets d'une table (ids) a partir de chaines. */
    public static List<Item> items(List<String> ids) {
        List<Item> out = new ArrayList<>();
        for (String s : ids) {
            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(s));
            if (it != net.minecraft.world.item.Items.AIR) {
                out.add(it);
            }
        }
        return out;
    }
}
