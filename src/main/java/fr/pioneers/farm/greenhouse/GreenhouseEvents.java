package fr.pioneers.farm.greenhouse;

import fr.pioneers.Pioneers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.5-beta (lot 3) : evenements de la serre multibloc : pose ou casse d'un bloc dans la zone de veille d'une serre (paroi, sol, interieur ;
 * autour d'une serre non formee) -> relecture de son regulateur ({@link GreenhouseRegulatorTile#noticeChange}) ; plusieurs serres
 * voisines (paroi partagee) sont toutes prevenues. Monde decharge : registre vide. Connexion : message unique de mise a jour des serres
 * ({@link GreenhouseNotice}).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class GreenhouseEvents {

    private GreenhouseEvents() {
    }

    private static void changed(LevelAccessor level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        for (BlockPos r : GreenhouseRegistry.watching(level, pos)) {
            if (level.getBlockEntity(r) instanceof GreenhouseRegulatorTile tile) {
                tile.noticeChange();
            }
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        changed(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        changed(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        GreenhouseRegistry.clear(event.getLevel());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GreenhouseNotice.get(player.server).tell(player);
        }
    }
}
