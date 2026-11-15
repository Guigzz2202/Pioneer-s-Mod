package fr.pioneers.logistics.pneumatic.tools;

import fr.pioneers.Pioneers;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Evenements des outils a air comprime : recharge (Maj + clic droit sur un bloc a air comprime, avant tout autre usage), 3 x 3 du
 * marteau-piqueur, respiration sur la bouteille de plongee.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class PneumaticEvents {

    private PneumaticEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        if (!(stack.getItem() instanceof AirToolItem tool) || !player.isShiftKeyDown() || !AirToolItem.isAirSource(event.getLevel(), event.getPos())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (event.getLevel().isClientSide) {
            return;
        }
        double taken = tool.refill(stack, event.getLevel().getBlockEntity(event.getPos()));
        if (taken > 0) {
            event.getLevel().playSound(null, event.getPos(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3F, 1.8F);
        }
        player.displayClientMessage(Component.translatable(taken > 0 ? "tooltip.pioneers.air_tool.refilled" : "tooltip.pioneers.air_tool.nothing",
              String.format(Locale.ROOT, "%.0f", AirToolItem.air(stack)), String.format(Locale.ROOT, "%.0f", tool.capacity())), true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof JackhammerItem) {
                JackhammerItem.area(player, stack, event.getPos(), event.getState());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            DivingTankItem.breathe(event.player);
        }
    }
}
