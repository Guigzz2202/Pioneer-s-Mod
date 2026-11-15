package fr.pioneers.tech.compute;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.industry.multi.ControllerTile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Puces d'IA dans les controleurs de multibloc (0.17.2) : clic droit avec un modele entraine = la puce s'installe (une seule) ; Maj + clic
 * droit main vide sur un controleur equipe = la puce revient (avant la projection du plan, qui reprend la main sans puce).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AiChipEvents {

    private AiChipEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Level level = event.getLevel();
        if (!(level.getBlockEntity(event.getPos()) instanceof ControllerTile tile)) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        if (AiChips.isModel(held)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            if (level.isClientSide) {
                return;
            }
            Component name = held.getHoverName().copy();
            if (tile.installChip(held)) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                player.displayClientMessage(Component.translatable("ai.pioneers.chip.installed", name,
                      Component.translatable("block.pioneers." + tile.machine.multiId())).withStyle(ChatFormatting.AQUA), true);
                level.playSound(null, event.getPos(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6F, 1.6F);
            } else {
                player.displayClientMessage(Component.translatable("ai.pioneers.chip.occupied").withStyle(ChatFormatting.YELLOW), true);
            }
            return;
        }
        if (held.isEmpty() && player.isShiftKeyDown()) {
            boolean has = level.isClientSide ? tile.aiLevel() > 0 : !tile.aiChip().isEmpty();
            if (!has) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            if (!level.isClientSide) {
                ItemStack chip = tile.takeChip();
                if (!player.getInventory().add(chip)) {
                    player.drop(chip, false);
                }
                player.displayClientMessage(Component.translatable("ai.pioneers.chip.removed").withStyle(ChatFormatting.GRAY), true);
                level.playSound(null, event.getPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5F, 1.6F);
            }
        }
    }
}
