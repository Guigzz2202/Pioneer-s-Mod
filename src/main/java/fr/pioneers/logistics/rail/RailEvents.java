package fr.pioneers.logistics.rail;

import fr.pioneers.Pioneers;
import fr.pioneers.logistics.rail.block.ConductorBlock;
import fr.pioneers.logistics.rail.block.SubstationBlock;
import fr.pioneers.logistics.rail.net.Electrification;
import fr.pioneers.logistics.rail.net.Piece;
import fr.pioneers.logistics.rail.track.TrackBlock;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.20.0 : evenements du chemin de fer : tick des reseaux (chaque dimension), verification des voies des chunks charges, et retrait du
 * ferroviaire vanilla du monde (decision de Guillaume) : les rails disparaissent au chargement de leur chunk, on ne peut plus en poser, les
 * wagonnets disparaissent (contenu au sol ; ceux des mines deviennent un coffre a butin a leur place).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RailEvents {

    /**
     * Chunks a examiner au prochain tick, par dimension (on ne touche pas aux blocs pendant le chargement). 0.20.46 (audit) : ensemble ordonne
     * sans doublon (un chunk recharge n'est plus mis deux fois en file), et un chunk decharge en sort : la file ne grossit plus sans fin pendant
     * une pregeneration.
     */
    private static final Map<Level, LongLinkedOpenHashSet> PENDING = new HashMap<>();
    private static final Map<Level, List<Runnable>> LATER = new HashMap<>();

    private RailEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        List<Runnable> later = LATER.remove(level);
        if (later != null) {
            later.forEach(Runnable::run);
        }
        LongLinkedOpenHashSet q = PENDING.get(level);
        if (q != null) {
            for (int i = 0; i < 16 && !q.isEmpty(); i++) {
                long c = q.removeFirstLong();
                int cx = ChunkPos.getX(c), cz = ChunkPos.getZ(c);
                if (level.hasChunk(cx, cz)) {
                    scan(level, level.getChunk(cx, cz));
                }
            }
        }
        RailWorld.get(level).tick(level);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            PENDING.computeIfAbsent(level, k -> new LongLinkedOpenHashSet()).add(chunk.getPos().toLong());
        }
    }

    /** 0.20.46 (audit) : un chunk decharge avant d'etre examine sort de la file (il y reviendra a son prochain chargement). */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LongLinkedOpenHashSet q = PENDING.get(level);
            if (q != null) {
                q.remove(event.getChunk().getPos().toLong());
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            PENDING.remove(level);
            LATER.remove(level);
        }
    }

    /** Rails vanilla retires ; voies du mod comparees au reseau (ajoutees si le reseau les avait perdues). */
    private static void scan(ServerLevel level, LevelChunk chunk) {
        RailWorld world = RailWorld.get(level);
        world.validate(level, chunk.getPos());
        LevelChunkSection[] sections = chunk.getSections();
        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            boolean vanilla = section.maybeHas(s -> s.getBlock() instanceof BaseRailBlock);
            boolean ours = section.maybeHas(s -> s.getBlock() instanceof TrackBlock);
            boolean power = section.maybeHas(s -> s.getBlock() instanceof ConductorBlock || s.getBlock() instanceof SubstationBlock);
            if (!vanilla && !ours && !power) {
                continue;
            }
            int baseY = chunk.getSectionYFromSectionIndex(si) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState st = section.getBlockState(x, y, z);
                        BlockPos pos = chunk.getPos().getBlockAt(x, baseY + y, z);
                        if (vanilla && st.getBlock() instanceof BaseRailBlock) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        } else if (power && st.getBlock() instanceof ConductorBlock cb) {
                            if (world.power.conductor(pos) == null) {
                                world.power.add(new Electrification.Conductor(pos.immutable(), cb.kind, 0));
                            }
                        } else if (power && st.getBlock() instanceof SubstationBlock sb) {
                            if (world.power.conductor(pos) == null) {
                                world.power.add(new Electrification.Conductor(pos.immutable(), Electrification.Kind.SUBSTATION, sb.tier()));
                            }
                        } else if (ours && st.getBlock() instanceof TrackBlock) {
                            Piece p = TrackBlocks.pieceOf(st, pos);
                            if (p != null) {
                                Piece known = world.net.piece(pos.asLong());
                                if (known == null || !known.same(p)) {
                                    world.trackPlaced(p);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Les wagonnets vanilla n'entrent plus dans le monde ; ceux des mines laissent un coffre a butin, les autres leur contenu. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof AbstractMinecart cart) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        cart.ejectPassengers();
        event.setCanceled(true);
        BlockPos pos = cart.blockPosition();
        if (cart instanceof AbstractMinecartContainer box) {
            ResourceLocation loot = box.getLootTable();
            long seed = box.getLootTableSeed();
            List<ItemStack> contents = new ArrayList<>();
            if (loot == null) {
                for (int i = 0; i < box.getContainerSize(); i++) {
                    if (!box.getItem(i).isEmpty()) {
                        contents.add(box.getItem(i).copy());
                    }
                }
            }
            LATER.computeIfAbsent(level, k -> new ArrayList<>()).add(() -> {
                if (loot != null) {
                    if (level.getBlockState(pos).canBeReplaced() || level.getBlockState(pos).getBlock() instanceof BaseRailBlock) {
                        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, cart.getDirection()), Block.UPDATE_ALL);
                        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                            chest.setLootTable(loot, seed);
                        }
                    }
                } else {
                    for (ItemStack s : contents) {
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
                    }
                }
            });
        }
    }

    /** On ne pose plus de rails vanilla. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().getBlock() instanceof BaseRailBlock) {
            event.setCanceled(true);
        }
    }
}
