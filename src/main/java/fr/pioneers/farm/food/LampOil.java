package fr.pioneers.farm.food;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.PiFarm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.5-beta (lot 11, specification « Le monde vivant » §9 : « la graisse en huile pour lampe ») : l'HUILE DE LAMPE (objet de cuisine
 * {@code pioneers:lamp_oil}, bouteille d'huile de baleine : graisse de baleine au pressoir, puis mise en bouteille, tools/gc/farm2/dishes16.py)
 * est le combustible des luminaires a huile (ingredient de l'applique et de la lanterne de bronze par le tag {@code pioneers:lamp_fuels},
 * tools/gc/lighting/lighting.py) et brule au four comme un petit combustible ({@link #BURN_TICKS}, = T:farm2/game_kitchen.py LAMP_OIL_BURN).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class LampOil {

    public static final String ID = "pioneers:lamp_oil";
    /** Duree de combustion au four, en ticks (2 objets cuits). */
    public static final int BURN_TICKS = 400;

    private LampOil() {
    }

    /** L'objet huile de lampe (air si absent). */
    public static Item item() {
        return PiFarm.item(ID);
    }

    public static boolean is(ItemStack stack) {
        Item item = item();
        return !stack.isEmpty() && item != Items.AIR && stack.is(item);
    }

    @SubscribeEvent
    public static void burnTime(FurnaceFuelBurnTimeEvent event) {
        if (is(event.getItemStack())) {
            event.setBurnTime(BURN_TICKS);
        }
    }
}
