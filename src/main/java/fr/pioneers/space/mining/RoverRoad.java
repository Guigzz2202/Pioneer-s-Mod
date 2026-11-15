package fr.pioneers.space.mining;

import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Route du rover : le profil d'altitude reel le long de son trajet, et les blocs qu'il doit creuser pour passer.
 * <ul>
 *     <li>Il suit le sol : il descend dans les creux et franchit une marche d'un bloc.</li>
 *     <li>Face a une paroi plus haute, il creuse un passage de 3 blocs de large et 2 de haut et garde son niveau.</li>
 *     <li>Il ne creuse que les blocs naturels des astres (tag {@code pioneers:rover_diggable}) ; ailleurs le gisement est inaccessible.</li>
 * </ul>
 * Le profil est echantillonne tous les {@link #STEP} blocs ; le client s'en sert pour poser le rover exactement sur sa route.
 */
public final class RoverRoad {

    private RoverRoad() {
    }

    public static final double STEP = 0.5;
    public static final TagKey<Block> DIGGABLE = TagKey.create(Registries.BLOCK, Pioneers.rl("rover_diggable"));

    /** Profil (altitude relative au centre du sol, une par echantillon), blocs a creuser et distance ou les creuser. */
    public record Plan(int[] heights, List<BlockPos> digs, List<Double> digAt, boolean blocked) {
    }

    private static boolean passable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.getCollisionShape(level, pos).isEmpty();
    }

    public static boolean diggable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(DIGGABLE) && state.getDestroySpeed(level, pos) >= 0;
    }

    /** 1.0 : garage construit (portes vers {@code door}, demi-longueur {@code half}). */
    public static boolean insideHangar(Direction door, double half, int dx, int dz) {
        double depth = dx * door.getStepX() + dz * door.getStepZ(), along = dx * door.getStepZ() - dz * door.getStepX();
        return Math.abs(depth) <= 3 && Math.abs(along) <= half - 0.5;
    }

    public static Plan plan(Level level, BlockPos center, double[][] path, Direction door, double half) {
        double length = MiningOutpostStructure.length(path);
        int samples = Mth.floor(length / STEP) + 2;
        int[] heights = new int[samples];
        List<BlockPos> digs = new ArrayList<>();
        List<Double> digAt = new ArrayList<>();
        int road = center.getY() + 1;
        int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
        boolean blocked = false;
        for (int i = 0; i < samples; i++) {
            double distance = Math.min(i * STEP, length);
            double[] at = MiningOutpostStructure.along(path, length <= 0 ? 1 : distance / length);
            int dx = Mth.floor(at[0] + 0.5), dz = Mth.floor(at[1] + 0.5);
            if (insideHangar(door, half, dx, dz)) {
                road = center.getY() + 1;
            } else if (dx != lastX || dz != lastZ) {
                int x = center.getX() + dx, z = center.getZ() + dz;
                //Descente dans les creux
                for (int drop = 0; drop < 24 && passable(level, new BlockPos(x, road - 1, z)); drop++) {
                    road--;
                }
                //Marche d'un bloc : on monte ; paroi plus haute : on creuse a niveau
                if (!passable(level, new BlockPos(x, road, z)) && passable(level, new BlockPos(x, road + 1, z))
                    && passable(level, new BlockPos(x, road + 2, z))) {
                    road++;
                }
                for (int y = road; y <= road + 1; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!passable(level, pos)) {
                        if (diggable(level, pos)) {
                            digs.add(pos);
                            digAt.add(distance);
                        } else {
                            blocked = true;
                        }
                    }
                }
                //Le rover fait un bon bloc et demi de large : on degage aussi les colonnes laterales (si elles sont naturelles)
                boolean alongX = Math.abs(at[2]) >= Math.abs(at[3]);
                for (int side = -1; side <= 1; side += 2) {
                    int lx = alongX ? x : x + side, lz = alongX ? z + side : z;
                    for (int y = road; y <= road + 1; y++) {
                        BlockPos pos = new BlockPos(lx, y, lz);
                        if (!passable(level, pos) && diggable(level, pos)) {
                            digs.add(pos);
                            digAt.add(distance);
                        }
                    }
                }
                if (blocked) {
                    break;
                }
            }
            lastX = dx;
            lastZ = dz;
            heights[i] = road - center.getY();
        }
        return new Plan(heights, digs, digAt, blocked);
    }

    /** Altitude (relative) a une distance donnee le long du profil, interpolee. */
    public static double heightAt(int[] heights, double distance) {
        if (heights.length == 0) {
            return 1;
        }
        double index = Mth.clamp(distance / STEP, 0, heights.length - 1);
        int i = Mth.floor(index);
        int j = Math.min(heights.length - 1, i + 1);
        return Mth.lerp(index - i, heights[i], heights[j]);
    }
}
