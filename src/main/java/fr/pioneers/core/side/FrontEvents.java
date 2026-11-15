package fr.pioneers.core.side;

import fr.pioneers.Pioneers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 1.0 : face avant des blocs {@link Fronted} posee vers le joueur (comme la facade d'une machine orientee). */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class FrontEvents {

    private FrontEvents() {
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        Entity placer = event.getEntity();
        if (placer == null || event.getLevel().isClientSide()) {
            return;
        }
        BlockState state = event.getPlacedBlock();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) || state.hasProperty(BlockStateProperties.FACING)) {
            return;
        }
        BlockEntity tile = event.getLevel().getBlockEntity(event.getPos());
        if (tile instanceof Fronted fronted) {
            fronted.setFront(placer.getDirection().getOpposite());
        }
    }
}
