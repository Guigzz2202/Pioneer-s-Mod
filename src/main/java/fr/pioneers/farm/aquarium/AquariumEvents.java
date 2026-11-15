package fr.pioneers.farm.aquarium;

import fr.pioneers.Pioneers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

/**
 * 1.0.5-beta (lot 25, Guillaume : « clic droit en etant accroupi ouvre l'interface ») : Maj + clic droit sur un aquarium, QUEL QUE SOIT
 * l'objet en main (main vide comprise), ouvre l'interface de composition du bassin ({@link AquariumMenu}). ⚠ Le jeu n'appelle pas
 * {@code Block.use} quand le joueur accroupi tient un objet (lecon du lot 23) : c'est l'evenement {@code RightClickBlock} qui le fait, en
 * priorite haute (avant la pose d'un poisson cru a l'etal ou dans le monde : {@code LiveRelease}), des deux cotes ; il annule l'usage de
 * l'objet (le seau d'eau ne se vide pas a cote). Le clic droit simple garde ses gestes ({@link Aquariums#use}).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class AquariumEvents {

    private AquariumEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void compose(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!event.getEntity().isSecondaryUseActive() || !(level.getBlockState(event.getPos()).getBlock() instanceof AquariumBlock)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer player) {
            open(player, event.getPos().immutable());
        }
    }

    /** Ouvre l'interface de composition du bassin de {@code pos}. */
    public static void open(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, p) -> new AquariumMenu(id, inv, pos), Component.translatable(
              "gui.pioneers.aquarium.title")), buf -> buf.writeBlockPos(pos));
    }
}
