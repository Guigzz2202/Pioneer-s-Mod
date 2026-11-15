package fr.pioneers.core.vanilla;

import fr.pioneers.Pioneers;
import fr.pioneers.registries.PiCreativeTabs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Onglets creatifs vanilla : les objets remplaces par le mod en sont retires (1.0). 1.6 (unification vanilla) : les objets vanilla que nos
 * onglets rangent (pommes, carottes, pasteque... dans Ferme et cuisine ; seaux d'eau, de lave, de lait dans Fluides et gaz) quittent aussi les
 * onglets vanilla (la recherche les trouve toujours, par nos onglets).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VanillaTabs {

    private VanillaTabs() {
    }

    @SubscribeEvent
    public static void onBuild(BuildCreativeModeTabContentsEvent event) {
        if (Pioneers.MODID.equals(event.getTabKey().location().getNamespace())) {
            return;
        }
        //La recherche garde les objets deplaces (ils sont dans nos onglets)
        Set<Item> moved = event.getTabKey() == CreativeModeTabs.SEARCH ? Set.of() : PiCreativeTabs.movedVanilla();
        List<ItemStack> gone = new ArrayList<>();
        for (var entry : event.getEntries()) {
            if (VanillaReplacements.isReplaced(entry.getKey()) || moved.contains(entry.getKey().getItem())) {
                gone.add(entry.getKey());
            }
        }
        gone.forEach(stack -> event.getEntries().remove(stack));
    }
}
