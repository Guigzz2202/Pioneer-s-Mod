package fr.pioneers.naval;

import fr.pioneers.Pioneers;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.20.11 : evenements de la marine : tick de chaque dimension, releve de la carte marine des chunks charges (au tick suivant, 16 par tick
 * au plus), correction de la carte quand un bloc est pose ou casse pres de l'eau.
 * <p>
 * 0.20.46 (audit) : file sans doublons (un generateur de pierre ne l'allonge plus a chaque bloc) ; rien n'est releve dans une dimension ou
 * la marine ne peut servir ({@link NavalWorld#charting()} : pas de mer dans le generateur, ni port, ni navire, ni balise) ; un bloc forme
 * par un fluide (pierre, obsidienne, feu) ne fait refaire le chunk que s'il tombe sur une case navigable (il ne peut rien rendre
 * navigable).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NavalEvents {

    private static final Map<Level, LongLinkedOpenHashSet> PENDING = new HashMap<>();

    private NavalEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        NavalWorld w = NavalWorld.get(level);
        LongLinkedOpenHashSet q = PENDING.get(level);
        if (q != null) {
            for (int i = 0; i < 16 && !q.isEmpty(); i++) {
                long c = q.removeFirstLong();
                int x = ChunkPos.getX(c), z = ChunkPos.getZ(c);
                if (level.hasChunk(x, z)) {
                    w.chart.scan(level.getChunk(x, z));
                }
            }
        }
        w.tick(level);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk && NavalWorld.get(level).charting()) {
            queue(level, chunk.getPos().toLong());
        }
    }

    private static void queue(ServerLevel level, long chunk) {
        PENDING.computeIfAbsent(level, k -> new LongLinkedOpenHashSet()).add(chunk);
    }

    /**
     * 0.20.46 (audit) : la marine commence a servir dans une dimension sans mer (premier port, navire ou balise) : les chunks deja charges
     * autour sont releves (ceux charges plus tard le sont a leur chargement).
     */
    static void queueAround(ServerLevel level, BlockPos pos) {
        int r = level.getServer().getPlayerList().getViewDistance() + 1;
        int x0 = pos.getX() >> 4, z0 = pos.getZ() >> 4;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (level.hasChunk(x0 + dx, z0 + dz)) {
                    queue(level, ChunkPos.asLong(x0 + dx, z0 + dz));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            PENDING.remove(level);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        changed(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        changed(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        //0.20.46 (audit) : pierre, obsidienne ou feu formes par un fluide : seulement sur une case navigable (generateurs de pierre)
        if (event.getLevel() instanceof ServerLevel sl && NavalWorld.get(sl).chart.navigable(WaterChart.cell(event.getPos().getX()),
              WaterChart.cell(event.getPos().getZ()), true)) {
            changed(sl, event.getPos());
        }
    }

    private static void changed(Object level, BlockPos pos) {
        if (level instanceof ServerLevel sl) {
            NavalWorld w = NavalWorld.get(sl);
            if (w.charting() && w.chart.known(WaterChart.cell(pos.getX()), WaterChart.cell(pos.getZ()))) {
                //Releve au tick suivant (le bloc n'est pas encore en place pendant l'evenement)
                queue(sl, ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            }
        }
    }
}
