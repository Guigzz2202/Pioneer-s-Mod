package fr.pioneers.farm.food;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.ClanApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9.2 (retours de Guillaume : « Tous les plats et ingredients doivent etre plaçables dans le monde ») : le geste de pose des tas
 * ({@link PlacedFoodBlock}) pour les objets du mod comme pour ceux de vanilla (dont on ne peut pas changer {@code useOn}) : Maj + clic droit,
 * main principale. Les plats servis ({@code FarmFoodItem.useOn} -> {@link PlacedDishBlock}) ne passent pas par ici (absents de la table).
 * Respecte les claims de clan (pose = action PLACE). Les poissons crus : Maj + clic droit sur le DESSUS d'un bloc = pose a l'etal ; le
 * relacher vivant reste au clic droit vers l'eau et au Maj + clic droit sur un flanc ({@code LiveRelease}).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class PlacedFoodEvents {

    private PlacedFoodEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.isSpectator() || !PlacedFoodBlock.placeable(held)
            || event.getUseItem() == Event.Result.DENY) {
            return;
        }
        Level level = event.getLevel();
        BlockPos target = level.getBlockState(event.getPos()).getBlock() instanceof PlacedFoodBlock ? event.getPos() : event.getPos().above();
        if (event.getFace() != Direction.UP && target != event.getPos()) {
            return;
        }
        if (!ClanApi.canModify(player, level, target, ClanApi.Action.PLACE)) {
            return;
        }
        InteractionResult r = PlacedFoodBlock.place(player, level, event.getHand(), event.getHitVec(), held);
        if (r != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(r);
        }
    }
}
