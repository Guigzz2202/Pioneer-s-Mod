package fr.pioneers.society.economy.stall;

import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.society.economy.Values;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * 1.9 (MAJ RP, bloc 3) : cote serveur des etals. Ouverture (configuration pour le proprietaire, achat pour les autres, coffre du stock),
 * edition des annonces et achat. Le client n'envoie que la position, le numero d'annonce, l'emplacement d'inventaire de l'exemple, un prix
 * propose (verifie et borne ici) ou un nombre de lots : tout montant est calcule ici. Chaque action renvoie la vue complete
 * ({@link RpActions#STALL_VIEW}) avec un message eventuel.
 */
public final class StallService {

    /** Portee d'un etal (blocs). */
    public static final double REACH = 8;
    /** Lots proposes a l'achat ; le dernier choix est « le plus possible ». */
    public static final int[] CHOICES = {1, 4, 16};
    private static final String LISTED_TODAY = "pioneers_stall_listings";

    private StallService() {
    }

    public static void init() {
        RpNet.onServer(RpActions.STALL_EDIT, StallService::edit);
        RpNet.onServer(RpActions.STALL_BUY, StallService::buy);
    }

    // ------------------------------------------------------------------ ouverture
    /** Clic droit sur l'etal. */
    public static void use(ServerPlayer player, StallTile tile) {
        if (player instanceof FakePlayer) {
            return;
        }
        if (tile.owner() == null && !claim(player, tile)) {
            return;
        }
        boolean sneak = player.isSecondaryUseActive();
        if (tile.isOwner(player)) {
            snapshot(player, tile);
            if (sneak) {
                openStock(player, tile);
            } else {
                send(player, tile, true, null);
            }
        } else if (sneak && tile.canManage(player)) {
            send(player, tile, true, null);
        } else if (!EconomyApi.enabled()) {
            refuse(player, "stall.pioneers.result.closed");
        } else {
            send(player, tile, false, null);
        }
    }

    /** Etal sans proprietaire (pose sans joueur) : le premier joueur qui l'ouvre le prend, dans ses limites. */
    private static boolean claim(ServerPlayer player, StallTile tile) {
        if (!withinLimit(player, tile.counter())) {
            return false;
        }
        tile.setOwner(player.getUUID(), EconomyApi.name(player));
        StallRegistry.get(player.getServer()).add(player.getUUID(), tile.getLevel(), tile.getBlockPos(), tile.counter());
        player.displayClientMessage(Component.translatable("stall.pioneers.claimed").withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** Limite d'etals / de comptoirs par joueur et par monde ; message et faux au-dela. */
    public static boolean withinLimit(ServerPlayer player, boolean counter) {
        int limit = counter ? PiCommonConfig.get(RpConfig.COUNTERS, 2) : PiCommonConfig.get(RpConfig.STALLS, 4);
        int have = StallRegistry.get(player.getServer()).count(player.getServer(), player.getUUID(), counter);
        if (have >= limit) {
            player.displayClientMessage(Component.translatable(counter ? "stall.pioneers.limit.counter" : "stall.pioneers.limit.stall",
                  String.valueOf(limit)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    /** Pose par un joueur : il devient proprietaire, l'etal entre au registre. */
    public static void placed(ServerPlayer player, StallTile tile) {
        tile.setOwner(player.getUUID(), EconomyApi.name(player));
        snapshot(player, tile);
        StallRegistry.get(player.getServer()).add(player.getUUID(), tile.getLevel(), tile.getBlockPos(), tile.counter());
    }

    /** Instantane du proprietaire connecte (nom de pionnier, annonces en plus et taxe selon son skill Commerce). */
    static void snapshot(ServerPlayer owner, StallTile tile) {
        if (!tile.isOwner(owner)) {
            return;
        }
        int bonus = StallRules.listingBonus(PioneerApi.modifier(owner, "trade.stall_listings"), tile.counter());
        int tax = StallRules.taxPermille(PiCommonConfig.get(RpConfig.STALL_TAX, 0.05), PioneerApi.modifier(owner, "trade.stall_tax_pp"));
        tile.snapshot(EconomyApi.name(owner), bonus, tax);
    }

    static void openStock(ServerPlayer player, StallTile tile) {
        if (!tile.canManage(player)) {
            refuse(player, "stall.pioneers.not_yours");
            return;
        }
        Component title = Component.translatable("stall.pioneers.stock_title", tile.getBlockState().getBlock().getName(), tile.ownerName());
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> tile.counter() ? ChestMenu.sixRows(id, inv, tile.stock())
              : ChestMenu.threeRows(id, inv, tile.stock()), title));
        sound(player, tile, RpSounds.PAGE.get(), 0.9F);
    }

    // ------------------------------------------------------------------ vue
    /** Etal a portee du joueur (meme dimension, troncon charge, 8 blocs), sinon null. */
    @Nullable
    static StallTile reach(ServerPlayer player, long pos) {
        BlockPos p = BlockPos.of(pos);
        if (!player.level().isLoaded(p) || player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) > REACH * REACH) {
            return null;
        }
        return player.level().getBlockEntity(p) instanceof StallTile t ? t : null;
    }

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Place libre (unites) pour cet objet dans l'inventaire principal, sans rien changer. */
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

    /**
     * Vue complete. Configuration : toutes les annonces (verrouillees au-dela du nombre permis), bornes, pourcentage, taxe. Achat : seulement
     * les annonces actives, avec les totaux des choix de lots calcules ici.
     */
    static void send(ServerPlayer player, StallTile tile, boolean edit, @Nullable String message, String... args) {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", tile.getBlockPos().asLong());
        t.putBoolean("edit", edit);
        t.putBoolean("counter", tile.counter());
        t.putString("owner", tile.ownerName());
        t.putBoolean("mine", tile.isOwner(player));
        t.putLong("balance", EconomyApi.balance(player));
        t.putInt("slots", tile.slots());
        t.putInt("base", StallTile.baseListings(tile.counter()));
        t.putInt("bonus", tile.bonus());
        t.putInt("tax", tile.taxPermille());
        t.putInt("minPct", (int) Math.round(StallTile.minPct() * 100));
        t.putInt("maxPct", (int) Math.round(StallTile.maxPct() * 100));
        t.putBoolean("open", EconomyApi.enabled());
        double lo = StallTile.minPct(), hi = StallTile.maxPct();
        long balance = EconomyApi.balance(player);
        ListTag list = new ListTag();
        int count = edit ? tile.listingCount() : tile.slots();
        for (int i = 0; i < count; i++) {
            if (!edit && !tile.active(i)) {
                continue;
            }
            StallTile.Listing l = tile.listing(i);
            CompoundTag e = new CompoundTag();
            e.putInt("i", i);
            e.putBoolean("locked", i >= tile.slots());
            if (!l.empty()) {
                long value = Values.value(l.item);
                int stock = tile.stockOf(l.item);
                e.put("stack", l.item.save(new CompoundTag()));
                e.putInt("lot", l.lot);
                e.putLong("price", l.price);
                e.putLong("value", value * l.lot);
                e.putBoolean("valued", value > 0);
                e.putBoolean("priced", StallTile.priced(l));
                if (StallRules.priceable(value, l.lot, lo, hi)) {
                    e.putLong("min", StallRules.minPrice(value, l.lot, lo));
                    e.putLong("max", StallRules.maxPrice(value, l.lot, hi));
                }
                e.putInt("pct", StallRules.percent(l.price, value, l.lot));
                e.putInt("stock", stock);
                int lots = stock / l.lot;
                e.putInt("lots", lots);
                if (!edit) {
                    //« le plus possible » : stock, place dans le sac et solde
                    int room = room(player.getInventory(), l.item);
                    long afford = l.price > 0 ? balance / l.price : 0;
                    int most = (int) Math.max(1, Math.min(Math.min(StallRules.MAX_LOTS, lots), Math.min(room / l.lot, afford)));
                    int[] n = {CHOICES[0], CHOICES[1], CHOICES[2], most};
                    long[] tot = new long[n.length];
                    byte[] ok = new byte[n.length];
                    for (int k = 0; k < n.length; k++) {
                        tot[k] = l.price * n[k];
                        ok[k] = (byte) (n[k] <= lots && tot[k] <= balance && n[k] * l.lot <= room ? 1 : 0);
                    }
                    e.putIntArray("n", n);
                    e.putLongArray("tot", tot);
                    e.putByteArray("ok", ok);
                }
            }
            list.add(e);
        }
        t.put("listings", list);
        if (message != null) {
            t.putString("msg", message);
            ListTag a = new ListTag();
            for (String s : args) {
                a.add(StringTag.valueOf(s));
            }
            t.put("args", a);
        }
        RpNet.send(player, RpActions.STALL_VIEW, t);
    }

    private static void refuse(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        player.level().playSound(null, player.blockPosition(), RpSounds.BOOK_CLOSE.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    private static void sound(ServerPlayer player, StallTile tile, SoundEvent sound, float pitch) {
        player.level().playSound(null, tile.getBlockPos(), sound, SoundSource.BLOCKS, 0.7F, pitch);
    }

    // ------------------------------------------------------------------ edition (proprietaire, operateurs)
    static void edit(ServerPlayer player, CompoundTag t) {
        StallTile tile = reach(player, t.getLong("pos"));
        String op = t.getString("op");
        if (tile == null || "close".equals(op)) {
            return;
        }
        if (!tile.canManage(player)) {
            refuse(player, "stall.pioneers.not_yours");
            return;
        }
        snapshot(player, tile);
        int i = t.getInt("i");
        boolean usable = i >= 0 && i < tile.slots();
        switch (op) {
            case "stock" -> openStock(player, tile);
            case "item" -> {
                if (!usable) {
                    send(player, tile, true, "stall.pioneers.locked");
                    return;
                }
                setItem(player, tile, i, t.getInt("slot"));
            }
            case "set" -> {
                if (!usable || tile.listing(i).empty()) {
                    send(player, tile, true, "stall.pioneers.locked");
                    return;
                }
                setPrice(player, tile, i, t.getLong("price"), t.getInt("lot"));
            }
            case "clear" -> {
                if (i >= 0 && i < tile.listingCount()) {
                    tile.listing(i).clear();
                    tile.markChanged(false);
                }
                send(player, tile, true, "stall.pioneers.cleared");
            }
            default -> send(player, tile, true, null);
        }
    }

    /** Fixe l'objet d'une annonce depuis un exemple de l'inventaire (rien n'est pris), lot et prix proposes. */
    private static void setItem(ServerPlayer player, StallTile tile, int i, int slot) {
        Inventory inv = player.getInventory();
        ItemStack ex = slot >= 0 && slot < inv.getContainerSize() ? inv.getItem(slot) : ItemStack.EMPTY;
        if (ex.isEmpty()) {
            send(player, tile, true, "stall.pioneers.pick_item");
            return;
        }
        long value = Values.value(ex);
        double lo = StallTile.minPct(), hi = StallTile.maxPct();
        int lot = StallRules.smallestLot(value, 1, lo, hi);
        if (value <= 0 || lot < 0) {
            sound(player, tile, RpSounds.BOOK_CLOSE.get(), 1.0F);
            send(player, tile, true, "stall.pioneers.no_value", ex.getHoverName().getString());
            return;
        }
        StallTile.Listing l = tile.listing(i);
        l.clear();
        l.item = ex.copyWithCount(1);
        l.lot = lot;
        l.price = StallRules.defaultPrice(value, lot, lo, hi);
        tile.markChanged(false);
        if (firstToday(player, l.item)) {
            PioneerApi.gain(player, "trade.listing");
        }
        sound(player, tile, RpSounds.PAPER_PIN.get(), 1.0F);
        send(player, tile, true, "stall.pioneers.listed", ex.getHoverName().getString());
    }

    /** Prix et lot proposes par le client : lot 1..64 qui admet un prix entier, prix ramene dans les bornes (message si ramene). */
    private static void setPrice(ServerPlayer player, StallTile tile, int i, long wanted, int wantedLot) {
        StallTile.Listing l = tile.listing(i);
        long value = Values.value(l.item);
        double lo = StallTile.minPct(), hi = StallTile.maxPct();
        int lot = Math.max(1, Math.min(StallRules.MAX_LOT, wantedLot));
        if (!StallRules.priceable(value, lot, lo, hi)) {
            int least = StallRules.smallestLot(value, lot, lo, hi);
            send(player, tile, true, value <= 0 ? "stall.pioneers.no_value" : "stall.pioneers.lot_too_small",
                  value <= 0 ? l.item.getHoverName().getString() : String.valueOf(Math.max(1, least)));
            return;
        }
        long price = wanted;
        if (lot != l.lot && wanted == l.price) {
            //seul le lot a change : le prix suit, a l'unite egale
            price = Math.round(l.price * (double) lot / l.lot);
        }
        if (price <= 0) {
            price = StallRules.defaultPrice(value, lot, lo, hi);
        }
        long fixed = StallRules.clamp(price, value, lot, lo, hi);
        l.lot = lot;
        l.price = fixed;
        tile.markChanged(false);
        sound(player, tile, RpSounds.QUILL.get(), 1.2F);
        if (fixed != price) {
            send(player, tile, true, "stall.pioneers.clamped", String.valueOf(StallRules.minPrice(value, lot, lo)),
                  String.valueOf(StallRules.maxPrice(value, lot, hi)));
        } else {
            send(player, tile, true, "stall.pioneers.saved");
        }
    }

    /**
     * Premiere definition de cet objet aujourd'hui par ce joueur (XP « trade.listing ») ; memoire dans ses donnees persistantes. 1.0.4-beta
     * (lot 8, donnees RP a la mort) : sous {@code PERSISTED_NBT_TAG}, que Forge recopie a la mort (avant : perdue a chaque mort, l'XP du jour
     * se regagnait) ; l'ancienne place est relue une fois.
     */
    private static boolean firstToday(ServerPlayer player, ItemStack item) {
        CompoundTag top = player.getPersistentData();
        CompoundTag data = top.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (top.contains(LISTED_TODAY) && !data.contains(LISTED_TODAY)) {
            data.put(LISTED_TODAY, top.getCompound(LISTED_TODAY));
        }
        top.remove(LISTED_TODAY);
        top.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, data);
        CompoundTag root = data.getCompound(LISTED_TODAY);
        long day = PioneerApi.day(player.getServer());
        if (root.getLong("day") != day || !root.contains("day")) {
            root = new CompoundTag();
            root.putLong("day", day);
        }
        ListTag l = root.getList("items", Tag.TAG_STRING);
        String id = id(item);
        for (int k = 0; k < l.size(); k++) {
            if (l.getString(k).equals(id)) {
                return false;
            }
        }
        if (l.size() < 512) {
            l.add(StringTag.valueOf(id));
        }
        root.put("items", l);
        data.put(LISTED_TODAY, root);
        return true;
    }

    // ------------------------------------------------------------------ achat
    /** Empreinte d'un acheteur pour la memoire des 60 ventes. */
    static long fingerprint(UUID uuid) {
        return uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 17);
    }

    static void buy(ServerPlayer player, CompoundTag t) {
        StallTile tile = reach(player, t.getLong("pos"));
        if (tile == null) {
            return;
        }
        int i = t.getInt("i");
        int lots = Math.max(0, Math.min(StallRules.MAX_LOTS, t.getInt("n")));
        boolean exists = i >= 0 && i < tile.slots() && !tile.listing(i).empty();
        StallTile.Listing l = exists ? tile.listing(i) : new StallTile.Listing();
        UUID owner = tile.owner();
        ServerLevel level = (ServerLevel) tile.getLevel();
        if (owner != null) {
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(owner);
            if (online != null) {
                snapshot(online, tile);
            }
        }
        ItemStack like = l.item;
        String item = exists ? id(like) : "";
        StallRules.Deal deal = StallRules.quote(l.price, l.lot, lots, tile.taxPermille());
        StallRules.Buyer buyer = new StallRules.Buyer() {
            @Override
            public long balance() {
                return EconomyApi.balance(player);
            }

            @Override
            public boolean debit(long amount) {
                return EconomyApi.debit(player, amount, new EconomyApi.Entry("stall_buy", tile.ownerName(), item, deal.units())).ok();
            }

            @Override
            public void refund(long amount) {
                EconomyApi.credit(player, amount, EconomyApi.Entry.of("stall_refund", tile.ownerName()));
            }

            @Override
            public int room() {
                return StallService.room(player.getInventory(), like);
            }

            @Override
            public void give(int units) {
                int left = units;
                while (left > 0) {
                    ItemStack g = like.copyWithCount(Math.min(left, like.getMaxStackSize()));
                    left -= g.getCount();
                    ItemHandlerHelper.giveItemToPlayer(player, g);
                }
            }
        };
        StallRules.Shelf shelf = new StallRules.Shelf() {
            @Override
            public int stock() {
                return tile.stockOf(like);
            }

            @Override
            public boolean take(int units) {
                return tile.takeStock(like, units);
            }
        };
        //1.9 (MAJ RP, lot 19) : taxe de marche du clan qui tient le chunk (Marche a 32 blocs, plafond du regime, nulle avec un accord commercial
        //entre le clan de l'acheteur et ce clan) : prise sur la part du proprietaire et versee au tresor du clan ; si le tresor ne peut pas la
        //recevoir, le proprietaire garde tout (rien ne se cree, rien ne se perd)
        int marketPct = fr.pioneers.society.clan.ClanApi.enabled() ? fr.pioneers.society.clan.ClanTreasury.marketTaxFor(level, tile.getBlockPos(), player.getUUID()) : 0;
        long marketCut = marketPct <= 0 ? 0 : (long) Math.ceil(deal.total() * marketPct / 100.0);
        StallRules.Payee payee = share -> {
            long cut = Math.min(share, marketCut);
            if (cut > 0 && !fr.pioneers.society.clan.ClanTreasury.collectMarketTax(level, tile.getBlockPos(), cut, EconomyApi.name(player))) {
                cut = 0;
            }
            EconomyApi.creditLater(level.getServer(), owner, share - cut, new EconomyApi.Entry("stall_sale", EconomyApi.name(player), item, deal.units()));
        };
        StallRules.Outcome o = StallRules.execute(deal, EconomyApi.enabled(), tile.isOwner(player), owner != null, exists && lots > 0,
              exists && StallTile.priced(l), buyer, shelf, payee);
        if (o != StallRules.Outcome.OK) {
            sound(player, tile, RpSounds.BOOK_CLOSE.get(), 1.0F);
            send(player, tile, false, o.key());
            return;
        }
        //XP du proprietaire : une seule fois par acheteur parmi les 60 dernieres ventes de l'annonce (anti-boucle)
        boolean fresh = l.history.record(fingerprint(player.getUUID()));
        int tranches = StallRules.xpTranches(deal.total());
        if (fresh && tranches > 0) {
            PioneerApi.gainAuto(level, tile.getBlockPos(), owner, "trade.stall_sale", tranches);
        }
        tile.markChanged(false);
        sound(player, tile, RpSounds.QUILL.get(), 1.1F);
        send(player, tile, false, "stall.pioneers.bought", String.valueOf(deal.units()), like.getHoverName().getString(),
              String.valueOf(deal.total()));
    }
}
