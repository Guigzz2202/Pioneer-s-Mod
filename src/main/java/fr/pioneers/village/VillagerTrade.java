package fr.pioneers.village;

import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.economy.PriceService;
import fr.pioneers.economy.SpaceGoods;
import fr.pioneers.economy.Trade;
import fr.pioneers.economy.Values;
import fr.pioneers.reputation.PeopleApi;
import fr.pioneers.reputation.ReputationApi;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TridentItem;

/**
 * 1.9 (MAJ RP, bloc 3) : un villageois comme partenaire d'echange. Le catalogue se lit dans la DOCTRINE du peuple de son village (version
 * « standard » ou « rurale » de son metier), jamais dans le metier seul ; le village y ajoute ses services (Atelier : outils de l'age MkI ;
 * Archives : carte de ruines, manuels), son evenement (penurie, surplus) et, au palier Estime du peuple, son stock exclusif.
 */
public final class VillagerTrade implements Trade.TradePartner {

    private final ServerLevel level;
    private final Villager villager;

    public VillagerTrade(ServerLevel level, Villager villager) {
        this.level = level;
        this.villager = villager;
    }

    private VillagerLore lore() {
        return VillagerLore.get(villager);
    }

    @Nullable
    private VillageData.Village village() {
        return Villagers.village(level, villager);
    }

    private VillageCatalog.Doctrine doctrine() {
        return VillageCatalog.doctrine(PeopleApi.doctrine(level.getServer(), lore().people));
    }

    private VillageCatalog.Version version() {
        VillageCatalog.Profession p = VillageCatalog.profession(Villagers.profession(villager));
        return p == null ? new VillageCatalog.Version(List.of(), List.of(), List.of(), null) : p.version(doctrine().version());
    }

    private long day() {
        return PioneerApi.day(level);
    }

    /** Raison de refuser l'echange (cle de langue) ou null. */
    @Nullable
    static String refusal(ServerLevel level, ServerPlayer player, Villager v) {
        String prof = Villagers.profession(v);
        if ("minecraft:nitwit".equals(prof)) {
            return "village.pioneers.refuse.niais";
        }
        if ("minecraft:none".equals(prof)) {
            return "village.pioneers.refuse.idle";
        }
        VillagerLore lore = VillagerLore.get(v);
        if (ReputationApi.tier(player, lore.people).refuse()) {
            return "village.pioneers.refuse.reputation";
        }
        if (Villagers.affinity(level, v, player.getUUID()) <= -20) {
            return "village.pioneers.refuse.affinity";
        }
        VillageData.Village village = Villagers.village(level, v);
        if (village != null && village.average(player.getUUID()) <= -40) {
            return "village.pioneers.refuse.village";
        }
        return null;
    }

    @Override
    public boolean valid(ServerPlayer player) {
        return villager.isAlive() && villager.level() == player.level() && player.distanceToSqr(villager) <= 64;
    }

    @Override
    public Component tradeName() {
        return lore().displayName();
    }

    @Override
    public PriceService.Partner pricing(ServerPlayer player) {
        return new PriceService.Partner(PriceService.Kind.VILLAGER, lore().people, effectiveAffinity(player), 1, 1, 1,
              VillageFounding.isFounder(village(), player.getUUID()));
    }

    /**
     * 1.9 (MAJ RP, bloc 4) : affinite EFFECTIVE pour les prix (affinite personnelle + paliers du clan et de la faction, cumul borne a +-15) ;
     * jamais pour la reputation du village, qui reste la moyenne des affinites personnelles.
     */
    private int effectiveAffinity(ServerPlayer player) {
        return fr.pioneers.clan.rep.ClanReputation.effective(player, Villagers.affinity(level, villager, player.getUUID()), lore().people,
              villager.blockPosition());
    }

    @Override
    public PriceService.Partner pricing(ServerPlayer player, ItemStack stack, PriceService.Side side) {
        VillageData.Village v = village();
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        double event = 1, quota = 1, doctrine = 1;
        long day = day();
        if (v != null) {
            if (side == PriceService.Side.SELL && Villages.eventItem(v, "shortage", day).equals(id)) {
                event = 2;
                quota *= 2;
            }
            if (side == PriceService.Side.BUY && Villages.eventItem(v, "surplus", day).equals(id)) {
                event = 0.6;
            }
            if (v.services.contains("storage")) {
                quota *= 2;
            }
            if (v.services.contains("water") && (Values.has(stack.getItem(), "crop") || Values.has(stack.getItem(), "fish")
                                                  || Values.has(stack.getItem(), "wool"))) {
                quota *= 1.5;
            }
        }
        if (side == PriceService.Side.SELL) {
            for (VillageCatalog.Bonus b : doctrine().buyBonus()) {
                for (String f : b.families()) {
                    if (Values.has(stack.getItem(), f)) {
                        doctrine = Math.max(doctrine, b.factor());
                    }
                }
            }
        }
        //1.9 (MAJ RP, bloc 4) : affinite effective (paliers du clan)
        //1.0.3-beta (lot 11b) : le fondateur du village a de meilleurs prix (FoundingRules)
        return new PriceService.Partner(PriceService.Kind.VILLAGER, lore().people, effectiveAffinity(player), event, quota, doctrine,
              VillageFounding.isFounder(v, player.getUUID()));
    }

    @Nullable
    @Override
    public String refusal(ServerPlayer player) {
        return refusal(level, player, villager);
    }

    @Override
    public List<Trade.Offer> offers(ServerPlayer player) {
        List<Trade.Offer> out = new ArrayList<>();
        VillageCatalog.Version ver = version();
        VillageCatalog.Doctrine d = doctrine();
        VillageData.Village v = village();
        String prof = Villagers.profession(villager);
        Set<Item> seen = new LinkedHashSet<>();
        for (ItemStack s : ver.sells()) {
            if (seen.add(s.getItem())) {
                out.add(new Trade.Offer(lot(s), -1));
            }
        }
        if (v != null && v.services.contains("workshop") && d.workshopMaxAge() >= 2) {
            for (ItemStack s : ver.workshopSells()) {
                if (seen.add(s.getItem())) {
                    out.add(new Trade.Offer(lot(s), -1));
                }
            }
        }
        if (v != null && v.services.contains("archives")) {
            if ("minecraft:cartographer".equals(prof) && seen.add(VillageItems.RUIN_MAP.get())) {
                out.add(new Trade.Offer(new ItemStack(VillageItems.RUIN_MAP.get()), -1));
            }
            if ("minecraft:librarian".equals(prof)) {
                for (var m : VillageItems.MANUALS.values()) {
                    if (seen.add(m.get())) {
                        out.add(new Trade.Offer(new ItemStack(m.get()), -1));
                    }
                }
            }
        }
        //stock exclusif du peuple, au palier Estime ou plus, chez les metiers qui le portent
        if (ReputationApi.tier(player, lore().people).exclusive() && exclusiveProfession(prof, d)) {
            for (ItemStack s : d.exclusive()) {
                if (seen.add(s.getItem())) {
                    out.add(new Trade.Offer(lot(s), -1));
                }
            }
        }
        //1.0.4-beta (lot 4) : jamais un objet de l'espace (tag pioneers:space_only)
        out.removeIf(o -> SpaceGoods.is(o.stack()));
        return out;
    }

    /** Lot d'au moins un jeton au prix plancher (puissance de deux, 64 au plus) : un objet bon marche ne se donne jamais. */
    static ItemStack lot(ItemStack s) {
        long v = Values.value(new ItemStack(s.getItem()));
        int n = Math.max(1, s.getCount());
        while (v > 0 && v * n * 85 / 100 < 100 && n < Math.min(64, s.getMaxStackSize())) {
            n *= 2;
        }
        return s.copyWithCount(Math.min(n, s.getMaxStackSize()));
    }

    /** Metiers qui proposent le stock exclusif : Bibliothecaire (lettres), Fermier, Berger, Pecheur (ruraux), Cartographe (marchands). */
    private static boolean exclusiveProfession(String prof, VillageCatalog.Doctrine d) {
        return switch (d.id()) {
            case "lettered" -> "minecraft:librarian".equals(prof);
            case "earthbound" -> "minecraft:farmer".equals(prof) || "minecraft:shepherd".equals(prof) || "minecraft:fisherman".equals(prof);
            default -> "minecraft:cartographer".equals(prof);
        };
    }

    @Override
    public boolean buys(ServerPlayer player, ItemStack stack) {
        return buyList(player).contains(stack.getItem()) && Values.value(stack) > 0;
    }

    @Override
    public List<Item> buyList(ServerPlayer player) {
        Set<Item> out = new LinkedHashSet<>(version().buys());
        for (ItemStack s : doctrine().scraps()) {
            out.add(s.getItem());
        }
        VillageData.Village v = village();
        if (v != null) {
            String shortage = Villages.eventItem(v, "shortage", day());
            if (!shortage.isEmpty()) {
                Item it = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse(shortage));
                if (it != net.minecraft.world.item.Items.AIR) {
                    out.add(it);
                }
            }
        }
        //1.0.4-beta (lot 4) : un villageois n'achete pas non plus d'objet de l'espace
        out.removeIf(SpaceGoods::is);
        return new ArrayList<>(out);
    }

    @Override
    public int boughtToday(Item item) {
        return lore().bought(BuiltInRegistries.ITEM.getKey(item).toString(), day());
    }

    @Override
    public void recordBought(Item item, int count) {
        lore().addBought(BuiltInRegistries.ITEM.getKey(item).toString(), count, day());
    }

    @Override
    public void recordSold(int offer, int lots) {
        //stock illimite : les villageois ne s'epuisent pas, seuls leurs achats ont un quota
    }

    @Nullable
    @Override
    public String service() {
        return version().service();
    }

    @Override
    public boolean serviceAccepts(ItemStack stack) {
        String svc = service();
        if (svc == null || Values.value(new ItemStack(stack.getItem())) <= 0) {
            return false;
        }
        Item it = stack.getItem();
        boolean ok = "repair".equals(svc) ? it instanceof DiggerItem || it instanceof ShearsItem || it instanceof FishingRodItem
              : it instanceof SwordItem || it instanceof AxeItem || it instanceof TridentItem || it instanceof BowItem || it instanceof CrossbowItem;
        if (ok && doctrine().rural()) {
            //chez les Mains Nues : seulement le bois, la pierre et l'os
            ok = it instanceof TieredItem t ? t.getTier() == Tiers.WOOD || t.getTier() == Tiers.STONE
                                             : Values.has(it, "tool_wood") || Values.has(it, "weapon_wood") || Values.has(it, "tool_bone");
        }
        return ok;
    }

    @Override
    public double serviceFactor(ServerPlayer player) {
        int a = Villagers.affinity(level, villager, player.getUUID());
        VillageData.Village v = village();
        VillagerLore.Bond b = lore().bond(player.getUUID());
        if (v != null && v.services.contains("workshop") && a >= 40 && b.freeRepairDay != day()) {
            return 0;
        }
        double f = a >= 60 ? 0.5 : 1;
        if (v != null && v.services.contains("energy")) {
            f *= 0.75;
        }
        return f;
    }

    /** Echanges d'au moins 100 jetons qui font monter l'affinite d'un villageois, par jour (+1 chacun). */
    public static final int TRADE_AFFINITY_DAILY = 5;

    @Override
    public void traded(ServerPlayer player, PriceService.Side side, ItemStack stack, int count, long total) {
        VillagerLore.Bond b = lore().bond(player.getUUID());
        long day = day();
        if (count == 0 && total == 0) {
            //service gratuit du jour (Atelier)
            b.freeRepairDay = day;
        }
        if (total >= 100) {
            if (b.tradeDay != day) {
                b.tradeDay = day;
                b.tradeCount = 0;
            }
            //1.10.1 (bac de vente du village) : le commerce direct, plus fastidieux, doit rapporter plus de reputation que le bac (au
            //plus +3 par nuit pour tout le village) : jusqu'a +5 d'affinite par villageois et par jour (3 avant)
            if (b.tradeCount < TRADE_AFFINITY_DAILY) {
                b.tradeCount++;
                Villagers.change(level, villager, player, 1, "trade");
            }
            if (side == PriceService.Side.SELL) {
                if (stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, fr.pioneers.Pioneers.rl("scrap")))) {
                    ReputationApi.applyAct(player, "sell_scraps", null, null);
                } else if (Villages.farmProduct(stack.getItem())) {
                    ReputationApi.applyAct(player, "sell_farm", null, null);
                }
            }
        }
        VillageData.Village v = village();
        if (v != null && total > 0) {
            Villages.deed(level, v, player, total);
        }
        villager.playAmbientSound();
    }

    @Nullable
    @Override
    public String voice(String moment) {
        return null;
    }

    @Override
    public BlockPos tradePos() {
        return villager.blockPosition();
    }

    @Override
    public void closed(ServerPlayer player) {
        level.playSound(null, villager.blockPosition(), RpSounds.BOOK_CLOSE.get(), SoundSource.NEUTRAL, 0.3F, 1.2F);
    }
}
