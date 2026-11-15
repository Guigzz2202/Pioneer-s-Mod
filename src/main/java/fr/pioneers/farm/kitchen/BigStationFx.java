package fr.pioneers.farm.kitchen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.6 (lot A2) : particules et sons des grands postes (appele par {@link BigStationBlock#animateTick}, cote client) : bulles de la cuve,
 * vapeur du chaudron (chapeau de la cheminee) et de l'alambic (joint du chapiteau), gouttes du bec de l'alambic et du robinet du fut,
 * « part des anges » a la bonde, fumee du fumoir (cheminee, interstices de l'armoire, braises). Reperes en pixels du modele (facade au nord,
 * origine au coin de la case principale), recopies de {@code tools/gc/farm2/art_big_stations.py} (GEOM).
 */
public final class BigStationFx {

    private BigStationFx() {
    }

    static void particles(StationTile tile, BlockState state, RandomSource r) {
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }
        Direction f = state.getValue(StationBlock.FACING);
        BlockPos pos = tile.getBlockPos();
        boolean work = tile.working();
        switch (tile.kind()) {
            case FERMENTING_VAT -> {
                if (work && r.nextInt(2) == 0) {
                    float lvl = tile.tank() == null ? 0 : fill(tile);
                    double y = 2.0 + 12.0 * lvl + 0.3;
                    double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * 11.5;
                    add(level, ParticleTypes.BUBBLE_POP, pos, f, d * Math.cos(a), y, 16 + d * Math.sin(a), 0, 0.01, 0);
                    if (r.nextInt(5) == 0) {
                        level.playLocalSound(pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.35F, 0.8F + r.nextFloat() * 0.4F, false);
                    }
                }
            }
            case BREW_KETTLE -> {
                if (work) {
                    add(level, ParticleTypes.CLOUD, pos, f, 8 + r.nextGaussian() * 0.6, 32.5, 8 + r.nextGaussian() * 0.6, 0, 0.06, 0);
                    if (r.nextInt(3) == 0) {
                        //Vapeur qui s'echappe entre le bord et la hotte
                        double a = r.nextDouble() * Math.PI * 2;
                        add(level, ParticleTypes.CLOUD, pos, f, 8 + 7.6 * Math.cos(a), 15.5, 8 + 7.6 * Math.sin(a), 0.02 * Math.cos(a), 0.015, 0.02 * Math.sin(a));
                    }
                    if (r.nextInt(6) == 0) {
                        level.playLocalSound(pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.3F, 0.7F, false);
                    }
                }
            }
            case COPPER_STILL -> {
                if (work) {
                    if (r.nextInt(3) == 0) {
                        add(level, ParticleTypes.CLOUD, pos, f, 8 + r.nextGaussian() * 1.5, 19.8, 8 + r.nextGaussian() * 1.5, 0, 0.02, 0);
                    }
                    if (r.nextInt(2) == 0) {
                        add(level, ParticleTypes.FALLING_WATER, pos, f, -8, 4.3, 2.05, 0, 0, 0);
                    }
                }
            }
            case AGING_BARREL -> {                                              //1.6.2 : barrique 1 x 2 (bonde en (8, 16, 16), robinet en x 8)
                if (work && r.nextInt(8) == 0) {
                    add(level, ParticleTypes.WHITE_ASH, pos, f, 8 + r.nextGaussian() * 0.4, 16.4, 16 + r.nextGaussian() * 0.4, 0, 0.02, 0);
                }
                if (tile.tank() != null && !tile.tank().isEmpty() && r.nextInt(40) == 0) {
                    add(level, ParticleTypes.DRIPPING_HONEY, pos, f, 8, 2.2, 0.7, 0, 0, 0);
                }
            }
            case SMOKEHOUSE -> {
                if (state.getValue(StationBlock.LIT)) {
                    add(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, pos, f, 11.8 + r.nextGaussian() * 0.4, 32.2, 11.8 + r.nextGaussian() * 0.4, 0, 0.05, 0);
                    if (r.nextInt(2) == 0) {
                        //Fumee dans l'armoire (vue par la porte a claire-voie) et aux interstices
                        add(level, ParticleTypes.SMOKE, pos, f, 4 + r.nextDouble() * 8, 13 + r.nextDouble() * 12, 4 + r.nextDouble() * 8, 0, 0.01, 0);
                    }
                    if (r.nextInt(4) == 0) {
                        add(level, ParticleTypes.SMOKE, pos, f, 1.5 + r.nextDouble() * 13, 26.8, 1.5 + r.nextDouble() * 13, 0, 0.02, 0);
                    }
                    if (r.nextInt(5) == 0) {
                        add(level, ParticleTypes.SMALL_FLAME, pos, f, 5.6 + r.nextDouble() * 4.8, 4.6 + r.nextDouble() * 2.6, 0.7, 0, 0.002, 0);
                    }
                    if (r.nextInt(10) == 0) {
                        level.playLocalSound(pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 0.8F + r.nextFloat() * 0.2F, false);
                    }
                }
            }
            default -> {
            }
        }
    }

    private static float fill(StationTile tile) {
        var out = tile.outTank();
        var in = tile.tank();
        var t = out != null && !out.isEmpty() ? out : in;
        return t == null || t.isEmpty() ? 0 : t.getFluidAmount() / (float) t.getCapacity();
    }

    /** Particule a un point du modele (pixels, facade au nord) tourne selon {@code facing} autour du centre de la case principale. */
    private static void add(Level level, ParticleOptions type, BlockPos pos, Direction facing, double x, double y, double z, double vx, double vy,
          double vz) {
        double px = x - 8, pz = z - 8, qx = vx, qz = vz;
        int q = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        for (int i = 0; i < q; i++) {
            double t = px;
            px = -pz;
            pz = t;
            t = qx;
            qx = -qz;
            qz = t;
        }
        level.addParticle(type, pos.getX() + 0.5 + px / 16, pos.getY() + y / 16, pos.getZ() + 0.5 + pz / 16, qx, vy, qz);
    }
}
