package fr.pioneers.core;

import fr.pioneers.Pioneers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Toute cle reconnue (tags {@code forge:tools/wrench} et {@code forge:wrenches}, donc la cle Pioneer's) regle les blocs configurables
 * comme le configurateur : clic droit = lire le mode, accroupi + clic droit = mode suivant. Vaut pour tous les ports du mod (serre,
 * avant-poste, pas de tir), les faces de donnees des machines, et tout bloc qui expose la capacite « configurable ».
 * Le configurateur de Mekanism est traite ici comme une cle ; les cables gerent la cle eux-memes.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class WrenchInteraction {

    private WrenchInteraction() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!Wrenches.isWrench(stack)) {
            return;
        }
        BlockEntity tile = event.getLevel().getBlockEntity(event.getPos());
        if (tile == null) {
            return;
        }
        Configurable configurable = tile instanceof Configurable.Holder holder ? holder.getConfigurable(event.getFace()) : null;
        if (configurable == null) {
            return;
        }
        Player player = event.getEntity();
        InteractionResult result = player.isShiftKeyDown() ? configurable.onSneakRightClick(player) : configurable.onRightClick(player);
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result.consumesAction() ? InteractionResult.sidedSuccess(event.getLevel().isClientSide) : result);
        }
    }
}
