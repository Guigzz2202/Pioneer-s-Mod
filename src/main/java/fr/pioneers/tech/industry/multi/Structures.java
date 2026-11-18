package fr.pioneers.tech.industry.multi;

import fr.pioneers.Pioneers;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Multiblocs industriels formes, par monde : bornes de chacun (les ports y retrouvent leur controleur sans balayer, et une pose ou une casse
 * dans les bornes fait revalider la structure tout de suite).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Structures {

    private static final Map<LevelAccessor, Map<BlockPos, Structure.Result>> FORMED = new WeakHashMap<>();
    /** 1.9.2 (retours de Guillaume) : multiblocs hors catalogue qui pretent leurs ports ({@link PortHost}) : controleur -> cases. */
    private static final Map<LevelAccessor, Map<BlockPos, Set<BlockPos>>> HOSTS = new WeakHashMap<>();

    private Structures() {
    }

    private static Map<BlockPos, Structure.Result> of(LevelAccessor level) {
        synchronized (FORMED) {
            return FORMED.computeIfAbsent(level, l -> new HashMap<>());
        }
    }

    public static void put(Level level, BlockPos controller, Structure.Result result) {
        synchronized (FORMED) {
            of(level).put(controller.immutable(), result);
        }
    }

    public static void remove(Level level, BlockPos controller) {
        synchronized (FORMED) {
            of(level).remove(controller);
        }
    }

    /** 1.9.2 : inscrit (ou remplace) les cases d'un hote de ports forme. */
    public static void putHost(Level level, BlockPos controller, Set<BlockPos> cells) {
        synchronized (FORMED) {
            HOSTS.computeIfAbsent(level, l -> new HashMap<>()).put(controller.immutable(), Set.copyOf(cells));
        }
    }

    public static void removeHost(Level level, BlockPos controller) {
        synchronized (FORMED) {
            Map<BlockPos, Set<BlockPos>> map = HOSTS.get(level);
            if (map != null) {
                map.remove(controller);
            }
        }
    }

    /** 1.9.2 : controleur de l'hote de ports forme qui contient ce bloc, ou null. */
    @Nullable
    public static BlockPos hostAt(LevelAccessor level, BlockPos pos) {
        synchronized (FORMED) {
            Map<BlockPos, Set<BlockPos>> map = HOSTS.get(level);
            if (map != null) {
                for (Map.Entry<BlockPos, Set<BlockPos>> e : map.entrySet()) {
                    if (e.getValue().contains(pos)) {
                        return e.getKey();
                    }
                }
            }
        }
        return null;
    }

    /** Controleur du multibloc forme qui contient ce bloc, ou null. */
    @Nullable
    public static BlockPos controllerAt(Level level, BlockPos pos) {
        synchronized (FORMED) {
            for (Map.Entry<BlockPos, Structure.Result> e : of(level).entrySet()) {
                if (e.getValue().contains(pos)) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    private static void changed(LevelAccessor level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        BlockPos controller = null;
        synchronized (FORMED) {
            for (Map.Entry<BlockPos, Structure.Result> e : of(level).entrySet()) {
                if (e.getValue().contains(pos)) {
                    controller = e.getKey();
                    break;
                }
            }
        }
        if (controller != null && level.getBlockEntity(controller) instanceof ControllerTile tile) {
            tile.requestCheck();
        }
        //1.9.2 : hote de ports (multiblocs d'assemblage) ; lot 8a : tout hote MultiblockPort (multiblocs maison du cycle commun)
        BlockPos host = controller == null ? hostAt(level, pos) : null;
        if (host != null && level.getBlockEntity(host) instanceof fr.pioneers.api.multiblock.MultiblockPort tile) {
            tile.requestCheck();
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
}
