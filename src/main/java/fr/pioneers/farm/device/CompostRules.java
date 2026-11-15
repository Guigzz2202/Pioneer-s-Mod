package fr.pioneers.farm.device;

import fr.pioneers.Pioneers;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.6 (unification vanilla) : ce qui se composte. La table est celle de Minecraft ({@link ComposterBlock#COMPOSTABLES} : graines, recoltes,
 * feuilles, pains... avec leurs chances), completee au setup commun par nos matieres vegetales et nos plats sans viande ; graines, recoltes,
 * fruits, pousses et feuilles du mod y sont deja (PiFarm.setup). Rien n'est ecrase : une chance deja posee est gardee. Enregistre aussi le
 * composteur comme lieu de travail des fermiers ({@link ComposterTakeover#registerJobSite}).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CompostRules {

    private CompostRules() {
    }

    /** Objets du mod et chance d'ajouter une couche (memes paliers que vanilla : 0,3 / 0,5 / 0,65 / 0,85 / 1). */
    static final Map<String, Float> EXTRA = Map.ofEntries(
          Map.entry("plant_fiber", 0.3F), Map.entry("hemp_fiber", 0.3F), Map.entry("sawdust", 0.3F), Map.entry("wood_chips", 0.3F),
          Map.entry("bagasse", 0.5F), Map.entry("miscanthus", 0.5F), Map.entry("press_cake", 0.65F), Map.entry("microalgae", 0.5F),
          Map.entry("oat_flakes", 0.5F), Map.entry("white_rice", 0.5F), Map.entry("corn_meal", 0.5F), Map.entry("flour", 0.5F),
          Map.entry("green_malt", 0.5F), Map.entry("malt", 0.5F), Map.entry("crushed_malt", 0.5F), Map.entry("yeast", 0.3F),
          Map.entry("dried_chili", 0.5F), Map.entry("green_coffee", 0.5F), Map.entry("roasted_coffee", 0.5F), Map.entry("ground_coffee", 0.65F),
          Map.entry("dried_tea", 0.5F), Map.entry("cured_tobacco", 0.5F), Map.entry("roasted_cocoa", 0.5F), Map.entry("tofu", 0.65F),
          Map.entry("raisins", 0.65F), Map.entry("dried_fruit", 0.65F), Map.entry("potato_sticks", 0.65F), Map.entry("dough", 0.65F),
          Map.entry("bread", 0.85F), Map.entry("croissant", 0.85F), Map.entry("cookies", 0.85F), Map.entry("pancakes", 0.85F),
          Map.entry("baked_potato", 0.85F), Map.entry("fries", 0.85F), Map.entry("salad", 0.85F), Map.entry("fruit_salad", 0.85F),
          Map.entry("roasted_vegetables", 0.85F), Map.entry("ratatouille", 0.85F), Map.entry("porridge", 0.85F),
          Map.entry("cake", 1F), Map.entry("apple_pie", 1F), Map.entry("cherry_pie", 1F), Map.entry("berry_pie", 1F), Map.entry("pumpkin_pie", 1F));

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        //Notre composteur = lieu de travail des fermiers (comme le composteur vanilla qu'il remplace)
        event.enqueueWork(ComposterTakeover::registerJobSite);
        event.enqueueWork(() -> EXTRA.forEach((id, chance) -> {
            Item item = ForgeRegistries.ITEMS.getValue(Pioneers.rl(id));
            if (item != null && item != Items.AIR && !ComposterBlock.COMPOSTABLES.containsKey(item)) {
                ComposterBlock.COMPOSTABLES.put(item, (float) chance);
            }
        }));
    }
}
