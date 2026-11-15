package fr.pioneers.farm.kitchen;

import fr.pioneers.Pioneers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.6 (lot A2, demande de Guillaume : « le sechoir peut se mettre sur le feu ») : clic droit sur un feu de camp (n'importe quelle face, tag
 * {@code minecraft:campfires}) avec le sechoir en main : il se pose AU-DESSUS du feu, ses piquets plantes de part et d'autre (etat lit,
 * modele drying_rack_lit). Feu allume : sechage deux fois plus rapide, et les recettes qui exigent le feu (viande sechee) avancent.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class DryingRackEvents {

    private DryingRackEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!(held.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof StationBlock station)
              || station.kind() != StationBlock.Kind.DRYING_RACK) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (!state.is(BlockTags.CAMPFIRES) && !(state.getBlock() instanceof CampfireBlock)) {
            return;
        }
        //Comme un clic sur le dessus du feu : la case visee devient celle du dessus
        BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        InteractionResult result = item.place(new BlockPlaceContext(event.getEntity(), event.getHand(), held, top));
        if (result.consumesAction()) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }
}
