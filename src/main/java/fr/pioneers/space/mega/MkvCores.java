package fr.pioneers.space.mega;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.industry.multi.ControllerTile;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 5) : les COEURS MKV des multiblocs de famille (table {@link GeneratedMegastructures#CORE_ITEMS},
 * source T:au_dela/multiblocs.py CORES). Un multibloc de famille (Industrial ...) passe au tier MKV quand toute sa structure est MKV et que
 * son controleur porte le coeur de son domaine ; le coeur se pose d'un clic droit sur le controleur (un seul) et tombe quand on le casse.
 * Sa fabrication demande le composant gate de la megastructure du domaine : c'est le verrou du multibloc MKV de famille (brief §6.4.3).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MkvCores {

    private MkvCores() {
    }

    /** Index du coeur que la pile represente, ou -1. */
    public static int coreOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String s = id == null ? "" : id.toString();
        for (int i = 0; i < GeneratedMegastructures.CORE_ITEMS.length; i++) {
            if (GeneratedMegastructures.CORE_ITEMS[i].equals(s)) {
                return i;
            }
        }
        return -1;
    }

    /** Le coeur fait-il passer ce multibloc (cle de la machine) au MKV ? */
    public static boolean fits(ItemStack core, String machineKey) {
        int i = coreOf(core);
        return i >= 0 && List.of(GeneratedMegastructures.CORE_MACHINES[i]).contains(machineKey);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Level level = event.getLevel();
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        if (coreOf(held) < 0 || !(level.getBlockEntity(event.getPos()) instanceof ControllerTile tile)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) {
            return;
        }
        if (!fits(held, tile.machine.key)) {
            player.displayClientMessage(Component.translatable("mega.pioneers.core.wrong").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        Component name = held.getHoverName().copy();
        if (tile.mkvCore.install(held)) {
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            player.displayClientMessage(Component.translatable("mega.pioneers.core.installed", name).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            level.playSound(null, event.getPos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.4F);
        } else {
            player.displayClientMessage(Component.translatable("mega.pioneers.core.already").withStyle(ChatFormatting.YELLOW), true);
        }
    }
}
