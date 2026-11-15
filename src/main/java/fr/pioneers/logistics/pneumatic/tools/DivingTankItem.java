package fr.pioneers.logistics.pneumatic.tools;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Bouteille de plongee : un reservoir d'air comprime a garder sur soi (n'importe ou dans l'inventaire). Sous l'eau, le joueur respire sur la
 * bouteille ({@value #PER_TICK} litre par tick, soit 12 L par seconde) tant qu'elle n'est pas vide ; la reserve s'affiche toutes les deux
 * secondes. Plein : {@value #CAPACITY} litres, un peu plus de trois minutes.
 */
public class DivingTankItem extends AirToolItem {

    public static final double CAPACITY = 2400, PER_TICK = 0.6;

    public DivingTankItem(Properties properties) {
        super(CAPACITY, properties);
    }

    @Override
    protected double perUse() {
        return PER_TICK * 20;
    }

    /** Une seconde de plongee (appele chaque tick serveur pour chaque joueur). */
    static void breathe(Player player) {
        if (!player.isEyeInFluid(FluidTags.WATER) || player.getAbilities().invulnerable || player.getAirSupply() >= player.getMaxAirSupply()) {
            return;
        }
        ItemStack tank = find(player);
        if (tank.isEmpty() || !use(tank, PER_TICK)) {
            return;
        }
        player.setAirSupply(player.getMaxAirSupply());
        if (player.tickCount % 40 == 0) {
            int seconds = (int) (air(tank) / (PER_TICK * 20));
            player.displayClientMessage(Component.translatable("item.pioneers.diving_tank.reserve", Math.round(air(tank)), seconds / 60,
                  String.format(Locale.ROOT, "%02d", seconds % 60)), true);
        }
    }

    /** Premiere bouteille non vide de l'inventaire (mains, armure comprises). */
    private static ItemStack find(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof DivingTankItem && has(stack, PER_TICK)) {
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof DivingTankItem && has(stack, PER_TICK)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
