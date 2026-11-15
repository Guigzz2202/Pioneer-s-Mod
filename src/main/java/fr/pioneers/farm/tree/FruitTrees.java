package fr.pioneers.farm.tree;

import fr.pioneers.farm.PiFarm;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Pousse d'un arbre fruitier a partir de sa pousse. Formes : {@code round} (tronc 4-5, couronne ronde), {@code tall} (couronne en ogive),
 * {@code wide} (tronc court, branches, couronne large et plate), {@code palm} (bananier : stipe, grandes palmes retombantes). Tout se pose
 * seulement si chaque case est libre (air, herbe, feuilles) ; sinon la pousse attend. 40 % des feuilles portent des fruits.
 */
public final class FruitTrees {

    private FruitTrees() {
    }

    public static boolean grow(ServerLevel level, BlockPos origin, TreeDef def, RandomSource random) {
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        Block logBlock = ForgeRegistries.BLOCKS.getValue(ResourceLocation.withDefaultNamespace(def.log() + "_log"));
        if (logBlock == null || logBlock == Blocks.AIR) {
            logBlock = Blocks.OAK_LOG;
        }
        BlockState log = logBlock.defaultBlockState();
        BlockState leaves = PiFarm.LEAVES.get(def.id()).get().defaultBlockState();
        switch (def.shape()) {
            case "tall" -> {
                int h = 5 + random.nextInt(2);
                trunk(plan, origin, h, log);
                blob(plan, origin.above(h - 1), 2.2F, 3.2F, leaves, random);
            }
            case "wide" -> {
                int h = 3 + random.nextInt(2);
                trunk(plan, origin, h, log);
                BlockPos top = origin.above(h - 1);
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (random.nextFloat() < 0.75F) {
                        BlockPos b = top.relative(d).above();
                        plan.put(b, log.setValue(RotatedPillarBlock.AXIS, d.getAxis()));
                        blob(plan, b.relative(d).above(), 1.7F, 1.3F, leaves, random);
                    }
                }
                blob(plan, top.above(2), 2.6F, 1.6F, leaves, random);
            }
            case "palm" -> {
                int h = 4 + random.nextInt(2);
                trunk(plan, origin, h, log);
                BlockPos top = origin.above(h);
                put(plan, top, leaves);
                put(plan, top.above(), leaves);
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    put(plan, top.relative(d), leaves);
                    put(plan, top.relative(d, 2), leaves);
                    put(plan, top.relative(d, 3).below(), leaves);
                    put(plan, top.relative(d).relative(d.getClockWise()), leaves);
                }
            }
            default -> {
                int h = 4 + random.nextInt(2);
                trunk(plan, origin, h, log);
                blob(plan, origin.above(h - 1), 2.4F, 2.2F, leaves, random);
            }
        }
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
            if (!e.getKey().equals(origin) && !free(level, e.getKey())) {
                return false;
            }
        }
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
            BlockState state = e.getValue();
            if (state.getBlock() instanceof FruitLeavesBlock) {
                int fruit = random.nextFloat() < 0.4F ? 1 + random.nextInt(2) : 0;
                state = state.setValue(FruitLeavesBlock.FRUIT, fruit).setValue(LeavesBlock.DISTANCE, 1);
            }
            level.setBlock(e.getKey(), state, 3);
        }
        //Distance des feuilles au tronc recalculee par le tick des feuilles vanilla
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
            if (e.getValue().getBlock() instanceof FruitLeavesBlock) {
                level.scheduleTick(e.getKey(), e.getValue().getBlock(), 1);
            }
        }
        level.playSound(null, origin, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.BLOCKS, 1F, 0.8F);
        return true;
    }

    private static void trunk(Map<BlockPos, BlockState> plan, BlockPos origin, int h, BlockState log) {
        for (int y = 0; y < h; y++) {
            plan.put(origin.above(y), log);
        }
    }

    private static void put(Map<BlockPos, BlockState> plan, BlockPos pos, BlockState state) {
        plan.putIfAbsent(pos, state);
    }

    /** Couronne : ellipsoide de rayons (r, ry), bords rognes au hasard. */
    private static void blob(Map<BlockPos, BlockState> plan, BlockPos centre, float r, float ry, BlockState leaves, RandomSource random) {
        int ir = (int) Math.ceil(r), iy = (int) Math.ceil(ry);
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dy = -iy + 1; dy <= iy; dy++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    float d = (dx * dx + dz * dz) / (r * r) + dy * dy / (ry * ry);
                    if (d <= 1F && (d < 0.7F || random.nextFloat() < 0.75F)) {
                        put(plan, centre.offset(dx, dy, dz), leaves);
                    }
                }
            }
        }
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS);
    }
}
