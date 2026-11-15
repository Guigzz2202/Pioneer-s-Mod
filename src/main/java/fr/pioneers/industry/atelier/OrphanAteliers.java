package fr.pioneers.industry.atelier;

import com.mojang.logging.LogUtils;
import fr.pioneers.Pioneers;
import fr.pioneers.industry.IoMigration;
import fr.pioneers.machine.KitMachineBlock;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 1.0.4-beta (lot 13, fusion des machines) : objets d'une machine COMPACTE retiree dont le bloc est remappe vers le CONTROLEUR d'un multibloc
 * ({@code PiRemaps.p103Block} / {@code p104Block} : Cellule blindee devenue multibloc seul, four de vitrification, separateur laser,
 * photobioreacteur, colonne de stripping...). Le controleur n'est pas un atelier : la tile {@code industry_atelier} sauvee n'y est pas relue
 * ({@link AtelierTile#create} rend null) et son contenu se perdait. A la lecture du tronçon ({@link ChunkDataEvent.Load}, fil principal), le NBT
 * d'atelier d'une case dont le bloc n'est plus un atelier est lu : ses OBJETS (cases d'entree, de sortie, batterie) sont rendus au sol au
 * tick suivant, au-dessus du bloc ({@link IoMigration#release}). Fluides, gaz et energie ne peuvent pas etre rendus (perdus, comme avant).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OrphanAteliers {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Id du type de tile des ateliers (PiIndustry.ATELIER_TILE). */
    public static final String ATELIER_TYPE = Pioneers.MODID + ":industry_atelier";

    /** Objets a rendre : dimension, case, piles. */
    record Pending(ResourceKey<Level> dimension, BlockPos pos, List<ItemStack> stacks) {
    }

    private static final ConcurrentLinkedQueue<Pending> PENDING = new ConcurrentLinkedQueue<>();

    private OrphanAteliers() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkDataEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ListTag tiles = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < tiles.size(); i++) {
            CompoundTag tag = tiles.getCompound(i);
            if (!ATELIER_TYPE.equals(tag.getString("id"))) {
                continue;
            }
            BlockPos pos = BlockEntity.getPosFromTag(tag);
            Block block = chunk.getBlockState(pos).getBlock();
            if (block instanceof AtelierBlock || block instanceof KitMachineBlock) {
                continue;
            }
            List<ItemStack> stacks = items(tag);
            if (!stacks.isEmpty()) {
                PENDING.add(new Pending(level.dimension(), pos, stacks));
                LOGGER.info("Pioneer's : machine compacte retiree en {} (bloc {}) : {} pile(s) d'objets rendue(s) au sol", pos, block, stacks.size());
            }
        }
    }

    /** Piles d'objets d'un NBT d'atelier ({@code items}, liste des cases). */
    public static List<ItemStack> items(CompoundTag tag) {
        List<ItemStack> out = new ArrayList<>();
        ListTag items = tag.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = ItemStack.of(items.getCompound(i));
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty() || !(event.level instanceof ServerLevel level)) {
            return;
        }
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (!p.dimension().equals(level.dimension()) || !level.isLoaded(p.pos())) {
                continue;
            }
            it.remove();
            IoMigration.release(level, p.pos(), new ArrayList<>(p.stacks()));
        }
    }
}
