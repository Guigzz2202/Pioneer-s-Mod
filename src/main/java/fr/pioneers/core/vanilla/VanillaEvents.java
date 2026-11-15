package fr.pioneers.core.vanilla;

import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Conversion en jeu des objets vanilla remplaces ({@link VanillaReplacements}) : inventaire des joueurs (chaque seconde), objets poses au
 * sol, echanges des villageois et du marchand ambulant (emeraudes, lingots, diamants du mod).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class VanillaEvents {

    private VanillaEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || event.player.tickCount % 20 != 0) {
            return;
        }
        Inventory inventory = event.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (VanillaReplacements.isReplaced(stack)) {
                inventory.setItem(i, VanillaReplacements.convert(stack));
            }
        }
        ItemStack carried = event.player.containerMenu.getCarried();
        if (VanillaReplacements.isReplaced(carried)) {
            event.player.containerMenu.setCarried(VanillaReplacements.convert(carried));
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ItemEntity item && VanillaReplacements.isReplaced(item.getItem())) {
            item.setItem(VanillaReplacements.convert(item.getItem()));
        }
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        event.getTrades().replaceAll((level, listings) -> wrap(listings));
    }

    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        List<VillagerTrades.ItemListing> generic = wrap(event.getGenericTrades()), rare = wrap(event.getRareTrades());
        event.getGenericTrades().clear();
        event.getGenericTrades().addAll(generic);
        event.getRareTrades().clear();
        event.getRareTrades().addAll(rare);
    }

    private static List<VillagerTrades.ItemListing> wrap(List<VillagerTrades.ItemListing> listings) {
        List<VillagerTrades.ItemListing> out = new ArrayList<>(listings.size());
        for (VillagerTrades.ItemListing listing : listings) {
            out.add((trader, random) -> {
                MerchantOffer offer = listing.getOffer(trader, random);
                if (offer == null) {
                    return null;
                }
                return new MerchantOffer(VanillaReplacements.convert(offer.getBaseCostA()), VanillaReplacements.convert(offer.getCostB()),
                      VanillaReplacements.convert(offer.getResult()), offer.getUses(), offer.getMaxUses(), offer.getXp(), offer.getPriceMultiplier(),
                      offer.getDemand());
            });
        }
        return out;
    }
}
