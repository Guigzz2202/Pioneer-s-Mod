package fr.pioneers.farm.device;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.crop.FarmCropBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Evenements de la ferme animee : compost et engrais sur une culture (stades + culture fertilisee), terre labouree protegee du pietinement par
 * un epouvantail.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class DeviceEvents {

    private DeviceEvents() {
    }

    @SubscribeEvent
    public static void bonemeal(BonemealEvent event) {
        ItemStack stack = event.getStack();
        BlockState state = event.getBlock();
        if (!(state.getBlock() instanceof FarmCropBlock crop) || !stack.is(PiFarmDevices.FERTILIZERS)) {
            return;
        }
        //Compost : 1 stade ; engrais : 2 ; les deux fertilisent la culture (recolte et graines ameliorees)
        int stages = stack.is(PiFarmDevices.COMPOST.get()) ? 1 : 2;
        if (event.getLevel() instanceof ServerLevel level) {
            crop.fertilize(level, event.getPos(), state, stages);
        }
        event.setResult(Event.Result.ALLOW);
    }

    @SubscribeEvent
    public static void trample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof ServerLevel level && DeviceSites.scared(level, event.getPos().getX() + 0.5, event.getPos().getY(),
              event.getPos().getZ() + 0.5, ScarecrowBlock.TRAMPLE_RADIUS)) {
            event.setCanceled(true);
        }
    }
}
