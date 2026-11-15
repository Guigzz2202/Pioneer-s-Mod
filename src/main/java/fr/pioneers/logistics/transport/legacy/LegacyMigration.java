package fr.pioneers.logistics.transport.legacy;

import fr.pioneers.Pioneers;
import fr.pioneers.core.block.Waterlogging;
import fr.pioneers.logistics.transport.logistic.ChannelDef;
import fr.pioneers.logistics.transport.logistic.LogisticApi;
import fr.pioneers.logistics.transport.logistic.LogisticCableBlock;
import fr.pioneers.logistics.transport.logistic.LogisticCableTile;
import fr.pioneers.logistics.transport.logistic.LogisticNetworks;
import fr.pioneers.logistics.transport.logistic.PointConfig;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (D2c, spec §8.1) : conversion des cables herites au chargement. Une tile heritee se signale a {@code onLoad} ; en fin de tick
 * (avant les reseaux logistiques, budget {@link #BUDGET} cables par tick et par monde), l'ancien reseau qui la contient est lu
 * ({@link LegacyPlan#collect}) puis remplace :
 * <ol>
 *     <li>contenu interne : les objets en transit tombent au sol sur leur cable ; fluides, gaz, energie et donnees en transit sont perdus ;</li>
 *     <li>chaque bloc devient le cable logistique du meme niveau (opaque ou transparent, eau gardee) ;</li>
 *     <li>coupures : faces coupees et faces vers un ancien cable d'une autre famille (tile creee pour s'en souvenir, comme a la cle) ;</li>
 *     <li>formes recalculees (fiches) ;</li>
 *     <li>un canal pour l'ancien reseau ({@link LogisticApi#newUid}), ses fiches reprises en points ({@link LogisticApi#seed}) dans chaque
 *     tile a fiche ; ancien reseau a cheval sur un chunk non charge : canal {@code partial} fusionne a la rencontre.</li>
 * </ol>
 * Les machines et les buffers ne sont pas touches (ils gardent leur contenu).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyMigration {

    /** Cables convertis par tick et par monde (au-dela, la suite au tick suivant). */
    public static final int BUDGET = 512;
    private static final Direction[] DIRS = Direction.values();
    private static final Map<Level, LongLinkedOpenHashSet> QUEUES = new WeakHashMap<>();
    /** Statistiques (journal, GameTest) : cables convertis et canaux crees depuis le demarrage. */
    private static long converted, channels;

    private LegacyMigration() {
    }

    public static void queue(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        synchronized (QUEUES) {
            QUEUES.computeIfAbsent(level, l -> new LongLinkedOpenHashSet()).add(pos.asLong());
        }
    }

    public static long convertedCount() {
        return converted;
    }

    public static long channelCount() {
        return channels;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        LongLinkedOpenHashSet queue;
        synchronized (QUEUES) {
            queue = QUEUES.get(level);
        }
        if (queue == null || queue.isEmpty()) {
            return;
        }
        run(level, queue);
    }

    /** Traite la file (aussi appele par le GameTest pour ne pas attendre un tick). */
    public static void run(ServerLevel level) {
        LongLinkedOpenHashSet queue;
        synchronized (QUEUES) {
            queue = QUEUES.get(level);
        }
        if (queue != null) {
            run(level, queue);
        }
    }

    private static void run(ServerLevel level, LongLinkedOpenHashSet queue) {
        int done = 0;
        LegacyPlan.World world = world(level);
        while (done < BUDGET && !queue.isEmpty()) {
            long start;
            synchronized (QUEUES) {
                start = queue.removeFirstLong();
            }
            BlockPos pos = BlockPos.of(start);
            if (!level.isLoaded(pos) || !(level.getBlockState(pos).getBlock() instanceof LegacyCableBlock)) {
                continue;
            }
            LegacyPlan.Component component = LegacyPlan.collect(world, start);
            if (component == null) {
                continue;
            }
            apply(level, component);
            done += component.cables.size();
            synchronized (QUEUES) {
                for (LegacyPlan.Converted c : component.cables) {
                    queue.remove(c.old.pos);
                }
            }
        }
    }

    /** Lecture des anciens cables (etat + tile). */
    private static LegacyPlan.World world(ServerLevel level) {
        return new LegacyPlan.World() {
            @Nullable
            @Override
            public LegacyPlan.Old at(long pos) {
                BlockPos p = BlockPos.of(pos);
                BlockState state = level.getBlockState(p);
                if (!(state.getBlock() instanceof LegacyCableBlock block)) {
                    return null;
                }
                LegacyPlan.Old old = new LegacyPlan.Old(pos, block.family, block.level, block.clear);
                old.plugs = block.plugMask(state);
                old.disabled = block.disabledMask(state);
                if (level.getBlockEntity(p) instanceof LegacyCableTile tile) {
                    old.disabled |= tile.disabled();
                    old.pulling = tile.pulling();
                    old.pushing = tile.pushing();
                    old.temperature = tile.temperature();
                    old.air = tile.air();
                }
                return old;
            }

            @Override
            public boolean loaded(long pos) {
                return level.isLoaded(BlockPos.of(pos));
            }

            @Override
            public boolean converted(long pos) {
                return level.getBlockState(BlockPos.of(pos)).getBlock() instanceof LogisticCableBlock;
            }
        };
    }

    private static void apply(ServerLevel level, LegacyPlan.Component component) {
        List<BlockPos> positions = new ArrayList<>(component.cables.size());
        //1. Contenu interne (objets au sol) et nouveaux blocs, sans prevenir les voisins (les formes viennent ensuite)
        for (LegacyPlan.Converted c : component.cables) {
            BlockPos pos = BlockPos.of(c.old.pos);
            positions.add(pos);
            BlockState old = level.getBlockState(pos);
            if (level.getBlockEntity(pos) instanceof LegacyCableTile tile && c.old.family == LegacyFamily.ITEM) {
                ListTag share = tile.share();
                for (int i = 0; i < share.size(); i++) {
                    CompoundTag tag = share.getCompound(i);
                    ItemStack stack = ItemStack.of(tag);
                    if (!stack.isEmpty()) {
                        Block.popResource(level, pos, stack.copyWithCount(Math.max(1, tag.getInt("n"))));
                    }
                }
            }
            boolean water = old.hasProperty(Waterlogging.WATERLOGGED) && old.getValue(Waterlogging.WATERLOGGED);
            Block replacement = old.getBlock() instanceof LegacyCableBlock legacy ? legacy.replacement() : null;
            if (replacement == null) {
                continue;
            }
            level.setBlock(pos, replacement.defaultBlockState().setValue(Waterlogging.WATERLOGGED, water), Block.UPDATE_CLIENTS);
        }
        //2. Coupures (la tile qui s'en souvient est creee, comme a la cle)
        for (int i = 0; i < positions.size(); i++) {
            LegacyPlan.Converted c = component.cables.get(i);
            if (c.cut == 0) {
                continue;
            }
            BlockPos pos = positions.get(i);
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof LogisticCableBlock)) {
                continue;
            }
            LogisticCableTile tile = level.getBlockEntity(pos) instanceof LogisticCableTile t ? t : null;
            if (tile == null) {
                tile = new LogisticCableTile(pos, state);
                level.setBlockEntity(tile);
            }
            for (Direction d : DIRS) {
                if ((c.cut >> d.ordinal() & 1) != 0) {
                    tile.setCut(d, true);
                }
            }
        }
        //3. Formes (fiches vers les machines, liaisons entre cables convertis), voisins prevenus
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LogisticCableBlock cable) {
                level.setBlock(pos, cable.refresh(level, pos, state), Block.UPDATE_ALL);
            }
        }
        //4. Canal de l'ancien reseau et ses points
        converted += component.cables.size();
        if (component.hasPoints()) {
            ChannelDef def = component.channel(LogisticApi.newUid(), level.getGameTime() << 4);
            boolean any = false;
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof LogisticCableBlock) || LogisticCableBlock.plugMask(state) == 0
                    || !(level.getBlockEntity(pos) instanceof LogisticCableTile tile)) {
                    continue;
                }
                LegacyPlan.Converted c = component.cables.get(i);
                boolean seeded = false;
                for (Direction d : DIRS) {
                    PointConfig point = c.points[d.ordinal()];
                    if (point != null && LogisticCableBlock.link(state, d) == LogisticCableBlock.Link.PLUG) {
                        LogisticApi.seed(tile, def, pos.relative(d).asLong(), point, def.rev);
                        seeded = true;
                    }
                }
                if (!seeded) {
                    //Le canal est connu de toutes les tiles a fiche du composant (reconciliation)
                    LogisticApi.seed(tile, def, 0L, null, def.rev);
                }
                any = true;
            }
            if (any) {
                channels++;
            }
        }
        for (BlockPos pos : positions) {
            LogisticNetworks.invalidate(level, pos);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            synchronized (QUEUES) {
                QUEUES.remove(level);
            }
        }
    }
}
