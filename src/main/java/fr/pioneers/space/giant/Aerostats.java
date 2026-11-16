package fr.pioneers.space.giant;

import fr.pioneers.space.giant.PiGiantBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Aerostats des geantes gazeuses : plateformes flottantes dans la couche de brume, ou se posent les capsules.
 * Chaque dimension garde la liste de ses aerostats ; une capsule qui arrive loin de tout aerostat en deploie un a sa verticale.
 */
public class Aerostats extends SavedData {

    private static final String NAME = "pioneers_aerostats";
    /** Altitude du pont (au-dessus de la couche de nuages principale). */
    public static final int DECK_Y = 300;
    /** Un aerostat existant sert de point d'arrivee dans ce rayon. */
    public static final int ARRIVAL_RADIUS = 64;
    /** Demi-cote du pont (pont de 9 x 9). */
    public static final int HALF = 4;

    private final List<BlockPos> decks = new ArrayList<>();

    public static Aerostats get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Aerostats::load, Aerostats::new, NAME);
    }

    @Nullable
    public BlockPos nearest(int x, int z, int radius) {
        BlockPos best = null;
        double bestDist = (double) radius * radius;
        for (BlockPos deck : decks) {
            double dx = deck.getX() - x, dz = deck.getZ() - z;
            double dist = dx * dx + dz * dz;
            if (dist <= bestDist) {
                bestDist = dist;
                best = deck;
            }
        }
        return best;
    }

    /** Centre du pont de l'aerostat le plus proche (dans {@link #ARRIVAL_RADIUS}), deploye a la verticale s'il n'y en a pas. */
    public static BlockPos ensureNear(ServerLevel level, int x, int z) {
        Aerostats aerostats = get(level);
        BlockPos deck = aerostats.nearest(x, z, ARRIVAL_RADIUS);
        if (deck == null || !level.getBlockState(deck).is(PiGiantBlocks.AEROSTAT_LANDING_PAD.getBlock())) {
            if (deck != null) {
                aerostats.decks.remove(deck);
            }
            deck = new BlockPos(x, DECK_Y, z);
            build(level, deck);
            aerostats.decks.add(deck);
            aerostats.setDirty();
        }
        return deck;
    }

    /** Ajoute un aerostat deploye par un kit. */
    public static void register(ServerLevel level, BlockPos deck) {
        Aerostats aerostats = get(level);
        aerostats.decks.add(deck.immutable());
        aerostats.setDirty();
    }

    /** Peut-on deployer un aerostat ici (que des nuages ou de l'air) ? */
    public static boolean canBuild(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-HALF, -2, -HALF), center.offset(HALF, 3, HALF))) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !(state.getBlock() instanceof GasCloudBlock) && !state.canBeReplaced()) {
                return false;
            }
        }
        return center.getY() - 2 > level.getMinBuildHeight() && center.getY() + 3 < level.getMaxBuildHeight();
    }

    /**
     * Pont de 9 x 9 (aire d'atterrissage de 3 x 3 au centre), garde-corps, quatre ballasts lumineux aux coins et quille en
     * pointe sous le pont.
     */
    public static void build(ServerLevel level, BlockPos center) {
        Block deck = PiGiantBlocks.AEROSTAT_DECK.getBlock(), pad = PiGiantBlocks.AEROSTAT_LANDING_PAD.getBlock();
        Block hull = PiGiantBlocks.AEROSTAT_HULL.getBlock(), cell = PiGiantBlocks.AEROSTAT_BUOYANCY_CELL.getBlock();
        //Degagement des nuages autour
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-HALF - 1, -3, -HALF - 1), center.offset(HALF + 1, 4, HALF + 1))) {
            if (level.getBlockState(pos).getBlock() instanceof GasCloudBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                boolean center3 = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                level.setBlock(center.offset(dx, 0, dz), (center3 ? pad : deck).defaultBlockState(), Block.UPDATE_ALL);
                boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
                boolean corner = Math.abs(dx) == HALF && Math.abs(dz) == HALF;
                if (corner) {
                    level.setBlock(center.offset(dx, 1, dz), cell.defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(center.offset(dx, 2, dz), cell.defaultBlockState(), Block.UPDATE_ALL);
                } else if (edge && !(dx == 0 || dz == 0)) {
                    //Garde-corps, ouvert au milieu de chaque cote
                    level.setBlock(center.offset(dx, 1, dz), Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_ALL);
                }
                //Quille : 7 x 7 puis 3 x 3, et un ballast a la pointe
                if (Math.abs(dx) <= HALF - 1 && Math.abs(dz) <= HALF - 1) {
                    level.setBlock(center.offset(dx, -1, dz), hull.defaultBlockState(), Block.UPDATE_ALL);
                }
                if (center3) {
                    level.setBlock(center.offset(dx, -2, dz), hull.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        level.setBlock(center.offset(0, -3, 0), cell.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static Aerostats load(CompoundTag tag) {
        Aerostats aerostats = new Aerostats();
        for (Tag entry : tag.getList("decks", Tag.TAG_LONG)) {
            aerostats.decks.add(BlockPos.of(((LongTag) entry).getAsLong()));
        }
        return aerostats;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BlockPos deck : decks) {
            list.add(LongTag.valueOf(deck.asLong()));
        }
        tag.put("decks", list);
        return tag;
    }
}
