package fr.pioneers.world.ruins.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

/**
 * 1.9 (MAJ RP, bloc 1) : acces au monde pendant la generation d'une ruine ou d'une annexe : ecritures bornees aux 3 x 3 chunks autour du
 * chunk decore (au-dela, Minecraft refuse et se plaint), hauteur du sol (sans arbres ni herbes), profondeur d'eau, materiaux de remblai.
 */
final class GenWorld {

    final WorldGenLevel level;
    final int minX, maxX, minZ, maxZ, minY, maxY;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    GenWorld(WorldGenLevel level, ChunkPos center) {
        this.level = level;
        minX = (center.x - 1) << 4;
        maxX = ((center.x + 1) << 4) + 15;
        minZ = (center.z - 1) << 4;
        maxZ = ((center.z + 1) << 4) + 15;
        minY = level.getMinBuildHeight();
        maxY = level.getMaxBuildHeight() - 1;
    }

    boolean can(int x, int y, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY;
    }

    boolean can(BlockPos p) {
        return can(p.getX(), p.getY(), p.getZ());
    }

    BlockState get(int x, int y, int z) {
        return level.getBlockState(cursor.set(x, y, z));
    }

    void set(int x, int y, int z, BlockState state) {
        if (can(x, y, z)) {
            level.setBlock(cursor.set(x, y, z), state, Block.UPDATE_CLIENTS);
        }
    }

    void set(BlockPos p, BlockState state) {
        set(p.getX(), p.getY(), p.getZ(), state);
    }

    /** Vide une case (fluide compris) si elle ne l'est pas deja. */
    void clear(int x, int y, int z) {
        if (can(x, y, z) && !get(x, y, z).isAir()) {
            set(x, y, z, Blocks.AIR.defaultBlockState());
        }
    }

    /** Dernier bloc du sol de la colonne : sous l'eau, sans feuillage, troncs, herbes ni neige. */
    int ground(int x, int z) {
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
        for (int k = 0; k < 40 && y > minY; k++) {
            BlockState s = get(x, y, z);
            if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.getFluidState().isEmpty() && s.canBeReplaced()) {
                y--;
            } else {
                break;
            }
        }
        return y;
    }

    /** Profondeur d'eau au-dessus du sol de la colonne. */
    int water(int x, int ground, int z) {
        int n = 0;
        while (n < 16 && get(x, ground + 1 + n, z).getFluidState().is(Fluids.WATER)) {
            n++;
        }
        return n;
    }

    /** Roche du remblai profond (ardoise des profondeurs sous y = 0). */
    static BlockState rock(int y) {
        return y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    /** Bloc de remblai a {@code depth} blocs sous la surface, d'apres le bloc de surface d'origine de la colonne. */
    static BlockState fill(BlockState top, int depth, int y) {
        boolean soil = top.is(BlockTags.DIRT), sand = top.is(BlockTags.SAND), gravel = top.is(Blocks.GRAVEL);
        if (depth == 0) {
            return soil || sand || gravel ? top : rock(y);
        }
        if (depth < 3) {
            return soil ? Blocks.DIRT.defaultBlockState() : sand ? Blocks.SANDSTONE.defaultBlockState() : gravel ? Blocks.GRAVEL.defaultBlockState()
                                                                                                              : rock(y);
        }
        return rock(y);
    }
}
