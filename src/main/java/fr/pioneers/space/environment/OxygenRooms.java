package fr.pioneers.space.environment;

import fr.pioneers.Pioneers;
import fr.pioneers.space.tile.TileEntityOxygenDiffuser;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Pieces pressurisees du monde : les diffuseurs d'oxygene charges s'inscrivent ici, et l'on demande si un point est respirable.
 * <p>
 * 0.20.46 (audit) : les changements de blocs sont signales aux diffuseurs dont la piece est concernee (ils ne rebalayent plus toutes les
 * deux secondes) : mises a jour de blocs (pose, casse, piston, explosion, sable, feu... : tout changement qui previent ses voisins),
 * portes ouvertes ou fermees (sas : sans mise a jour des voisins), chunks charges ou decharges autour de la piece.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OxygenRooms {

    private OxygenRooms() {
    }

    private static final Map<Level, Set<TileEntityOxygenDiffuser>> DIFFUSERS = new WeakHashMap<>();

    public static void register(TileEntityOxygenDiffuser diffuser) {
        Level level = diffuser.getLevel();
        if (level != null && !level.isClientSide) {
            DIFFUSERS.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(diffuser);
        }
    }

    public static void unregister(TileEntityOxygenDiffuser diffuser) {
        Level level = diffuser.getLevel();
        if (level != null) {
            Set<TileEntityOxygenDiffuser> set = DIFFUSERS.get(level);
            if (set != null) {
                set.remove(diffuser);
            }
        }
    }

    /** Diffuseur dont la piece pressurisee contient ce point (null s'il n'y en a pas). */
    @Nullable
    public static TileEntityOxygenDiffuser pressurizedAt(Level level, BlockPos pos) {
        Set<TileEntityOxygenDiffuser> set = DIFFUSERS.get(level);
        if (set == null) {
            return null;
        }
        for (TileEntityOxygenDiffuser diffuser : set) {
            if (!diffuser.isRemoved() && diffuser.isPressurized() && diffuser.getRoom().contains(pos)) {
                return diffuser;
            }
        }
        return null;
    }

    public static boolean isPressurized(Level level, BlockPos pos) {
        return pressurizedAt(level, pos) != null;
    }

    // ------------------------------------------------------------------ 0.20.46 (audit) : changements signales

    @Nullable
    private static Set<TileEntityOxygenDiffuser> diffusers(Object level) {
        if (!(level instanceof Level l) || l.isClientSide || DIFFUSERS.isEmpty()) { //cote client d'abord : la table est au serveur
            return null;
        }
        Set<TileEntityOxygenDiffuser> set = DIFFUSERS.get(l);
        return set == null || set.isEmpty() ? null : set;
    }

    /** Tout changement de bloc qui previent ses voisins (etat deja en place). */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        Set<TileEntityOxygenDiffuser> set = diffusers(event.getLevel());
        if (set != null) {
            for (TileEntityOxygenDiffuser d : set) {
                d.blockChanged(event.getPos(), event.getState());
            }
        }
    }

    /** Portes et trappes (les sas changent d'etat sans prevenir leurs voisins). */
    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        GameEvent e = event.getVanillaEvent();
        if (e != GameEvent.BLOCK_OPEN && e != GameEvent.BLOCK_CLOSE) {
            return;
        }
        Set<TileEntityOxygenDiffuser> set = diffusers(event.getLevel());
        if (set != null) {
            BlockPos pos = BlockPos.containing(event.getEventPosition());
            for (TileEntityOxygenDiffuser d : set) {
                if (d.near(pos.getX(), pos.getY(), pos.getZ(), 2)) {
                    d.markDirty();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        chunkChanged(event.getLevel(), event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        chunkChanged(event.getLevel(), event.getChunk().getPos());
    }

    /** Chunk charge ou decharge qui touche la piece (bord ouvert sur l'inconnu, ou paroi qui disparait). */
    private static void chunkChanged(Object level, ChunkPos chunk) {
        Set<TileEntityOxygenDiffuser> set = diffusers(level);
        if (set != null) {
            for (TileEntityOxygenDiffuser d : set) {
                if (d.nearColumns(chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), chunk.getMaxBlockZ())) {
                    d.markDirty();
                }
            }
        }
    }
}
