package fr.pioneers.logistics.robotics;

import fr.pioneers.logistics.robotics.logistics.LogisticNetwork;
import fr.pioneers.logistics.robotics.logistics.LogisticsRegistry;
import fr.pioneers.logistics.transport.legacy.LegacyCableBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * Degats a reparer : les blocs detruits par une explosion (creeper, meteore...) dans la couverture d'un reseau sont retenus, avec leur
 * etat d'origine. Les intendants (reseau passif) viennent les reposer, materiaux pris dans le reseau (voir
 * {@link RoboticsScheduler}). Un bloc remis par quelqu'un d'autre, ou remplace par autre chose, sort de la liste.
 */
@Mod.EventBusSubscriber
public class RobotRepairs extends SavedData {

    private static final String NAME = "pioneers_robot_repairs";
    /** Au plus autant de blocs retenus (les plus anciens sortent). */
    private static final int MAX = 8192;

    /** Position -> etat d'origine, dans l'ordre des degats. */
    final Long2ObjectLinkedOpenHashMap<BlockState> damaged = new Long2ObjectLinkedOpenHashMap<>();

    public static RobotRepairs get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> load(level, tag), RobotRepairs::new, NAME);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getAffectedBlocks().isEmpty()) {
            return;
        }
        List<LogisticNetwork> networks = LogisticsRegistry.get(level).networks(level.getGameTime());
        if (networks.isEmpty() || !RoboticsOrders.get(level).repairs()) {
            return;
        }
        RobotRepairs data = null;
        for (BlockPos pos : event.getAffectedBlocks()) {
            BlockState state = level.getBlockState(pos);
            //Ni air, ni liquide seul, ni feu, ni bloc a contenu (coffres, machines : leur contenu est perdu)
            if (state.isAir() || state.getBlock() instanceof BaseFireBlock || !state.getFluidState().isEmpty() && state.canBeReplaced()
                || level.getBlockEntity(pos) != null || state.getBlock().asItem() == Items.AIR) {
                continue;
            }
            boolean covered = false;
            for (LogisticNetwork network : networks) {
                if (network.covers(pos)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                continue;
            }
            if (data == null) {
                data = get(level);
            }
            data.damaged.putIfAbsent(pos.asLong(), state);
        }
        if (data != null) {
            while (data.damaged.size() > MAX) {
                data.damaged.removeFirst();
            }
            data.setDirty();
        }
    }

    public void forget(long pos) {
        if (damaged.remove(pos) != null) {
            setDirty();
        }
    }

    public int size() {
        return damaged.size();
    }

    private static RobotRepairs load(ServerLevel level, CompoundTag tag) {
        RobotRepairs data = new RobotRepairs();
        var blocks = level.holderLookup(Registries.BLOCK);
        for (Tag entry : tag.getList("damaged", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) entry;
            data.damaged.put(c.getLong("pos"), LegacyCableBlock.upgrade(NbtUtils.readBlockState(blocks, c.getCompound("state"))));
        }
        return data;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        for (var entry : damaged.long2ObjectEntrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", entry.getLongKey());
            c.put("state", NbtUtils.writeBlockState(entry.getValue()));
            list.add(c);
        }
        tag.put("damaged", list);
        return tag;
    }
}
