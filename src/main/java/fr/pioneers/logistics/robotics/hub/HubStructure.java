package fr.pioneers.logistics.robotics.hub;

import fr.pioneers.core.inventory.ItemSlot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

/**
 * Formation des hubs robotiques (socle) : les changements de blocs sont regroupes et traites en fin de tick. Une piece hors hub cherche un
 * cube valide qui la contient ; une piece d'un hub forme fait reverifier son hub. Le maitre (boitier du coin bas) reverifie aussi chaque
 * seconde (verre retire...). Les donnees restent dans le maitre quand le hub se defait ; casser le maitre fait tomber son contenu.
 */
@Mod.EventBusSubscriber
public final class HubStructure {

    private static final Map<ResourceKey<Level>, Set<BlockPos>> PENDING = new HashMap<>();

    private HubStructure() {
    }

    public static void queue(ServerLevel level, BlockPos pos) {
        PENDING.computeIfAbsent(level.dimension(), key -> new LinkedHashSet<>()).add(pos.immutable());
    }

    /** 0.20.46 (audit) : changements en attente d'un monde arrete. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        Set<BlockPos> pending = PENDING.remove(level.dimension());
        if (pending == null) {
            return;
        }
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos pos : pending) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof TileEntityRoboticCasing tile && tile.getMaster() != null) {
                TileEntityRoboticCasing master = tile.masterTile();
                if (master != null && done.add(master.getBlockPos())) {
                    revalidate(level, master);
                    continue;
                } else if (master != null) {
                    continue;
                }
                //Maitre disparu : la piece est libre
                tile.setMaster(null);
            }
            //Piece libre (ou voisin d'une piece) : un cube valide la contient-il ?
            for (BlockPos min : candidates(level, pos)) {
                if (done.contains(min)) {
                    continue;
                }
                RoboticHubValidator.Result result = RoboticHubValidator.validate(level, min);
                if (result != null && result.valid()) {
                    done.add(min);
                    form(level, min, result);
                    break;
                }
            }
        }
    }

    /** Cubes candidats qui contiennent la position dans leur coque (coins en boitiers). */
    private static List<BlockPos> candidates(ServerLevel level, BlockPos pos) {
        List<BlockPos> list = new ArrayList<>();
        for (int dx = 0; dx < RoboticHubValidator.WIDTH; dx++) {
            for (int dy = 0; dy < RoboticHubValidator.HEIGHT; dy++) {
                for (int dz = 0; dz < RoboticHubValidator.WIDTH; dz++) {
                    if (dx == 1 && dy == 1 && dz == 1) {
                        continue;
                    }
                    BlockPos min = pos.offset(-dx, -dy, -dz);
                    if (RoboticHubValidator.corners(level, min)) {
                        list.add(min);
                    }
                }
            }
        }
        return list;
    }

    /** Reverifie le hub d'un maitre ; renvoie faux s'il s'est defait. */
    static boolean revalidate(ServerLevel level, TileEntityRoboticCasing master) {
        //Lot 8a : par le cycle de vie commun du maitre (MultiblockCore.House)
        master.house().checkNow();
        return master.isMaster();
    }

    /** Forme (ou met a jour) le hub de coin bas {@code min}. */
    static void form(ServerLevel level, BlockPos min, RoboticHubValidator.Result result) {
        if (!(level.getBlockEntity(min) instanceof TileEntityRoboticCasing master)) {
            return;
        }
        RoboticHubData hub = master.ownHub();
        List<TileEntityRoboticCasing> members = members(level, min);
        if (!hub.isFormed() && !hub.hasContents()) {
            //Hub refait ailleurs : on reprend le contenu d'un ancien maitre de la structure
            for (TileEntityRoboticCasing member : members) {
                RoboticHubData other = member.storedHub();
                if (member != master && other != null && !other.isFormed() && other.hasContents()) {
                    hub.takeFrom(other);
                    member.setChanged();
                    break;
                }
            }
        }
        boolean wasFormed = hub.isFormed() && !hub.getBays().isEmpty();
        hub.setStructure(min, result.bays());
        for (TileEntityRoboticCasing member : members) {
            member.setMaster(min);
        }
        if (!wasFormed) {
            master.setChanged();
            master.sync();
        }
    }

    /** Defait le hub d'un maitre (les donnees restent dans le maitre). */
    static void unform(ServerLevel level, TileEntityRoboticCasing master) {
        RoboticHubData hub = master.storedHub();
        if (hub != null) {
            hub.unform(level);
        }
        for (TileEntityRoboticCasing member : members(level, master.getBlockPos())) {
            if (master.getBlockPos().equals(member.getMaster())) {
                member.setMaster(null);
            }
        }
        master.setMaster(null);
        master.setChanged();
    }

    /** Une piece est retiree : son hub se defait ; le maitre fait tomber son contenu. */
    static void removed(ServerLevel level, TileEntityRoboticCasing tile) {
        TileEntityRoboticCasing master = tile.masterTile();
        if (master == tile) {
            unform(level, tile);
        } else if (master != null) {
            //Reverifie a la fin du tick (le bloc sera parti)
            queue(level, master.getBlockPos());
        }
        RoboticHubData data = tile.storedHub();
        if (data != null) {
            BlockPos pos = tile.getBlockPos();
            for (ItemSlot slot : data.getSlots()) {
                if (!slot.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, slot.getStack().copy());
                }
            }
            data.clearContents();
        }
    }

    /** Pieces du hub (boitiers, ports, portes) presentes dans le cube. */
    private static List<TileEntityRoboticCasing> members(ServerLevel level, BlockPos min) {
        List<TileEntityRoboticCasing> list = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, min.offset(RoboticHubValidator.WIDTH - 1, RoboticHubValidator.HEIGHT - 1, RoboticHubValidator.WIDTH - 1))) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof TileEntityRoboticCasing tile) {
                list.add(tile);
            }
        }
        return list;
    }

    /** Clic droit sur une piece : ouvre le hub forme, sinon dit ce qui ne va pas. */
    static void interact(ServerLevel level, TileEntityRoboticCasing tile, ServerPlayer player) {
        TileEntityRoboticCasing master = tile.masterTile();
        if (master != null && master.getHub() != null) {
            NetworkHooks.openScreen(player, master, buffer -> buffer.writeBlockPos(master.getBlockPos()));
            return;
        }
        Component error = null;
        for (BlockPos min : candidates(level, tile.getBlockPos())) {
            RoboticHubValidator.Result result = RoboticHubValidator.validate(level, min);
            if (result != null && result.valid()) {
                form(level, min, result);
                if (level.getBlockEntity(min) instanceof TileEntityRoboticCasing formed && formed.getHub() != null) {
                    NetworkHooks.openScreen(player, formed, buffer -> buffer.writeBlockPos(min));
                }
                return;
            } else if (result != null && error == null) {
                error = result.error();
            }
        }
        if (error == null) {
            error = Component.translatable("robotic_hub.pioneers.formation.size");
        }
        player.displayClientMessage(error.copy().withStyle(ChatFormatting.RED), true);
    }

}
