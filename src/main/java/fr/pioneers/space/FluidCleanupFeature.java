package fr.pioneers.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Derniere passe de generation d'un chunk (et du bord de ses voisins, {@link #MARGIN}) : aucune etendue de liquide ne doit rester en
 * marche ou suspendue.
 * <ul>
 *     <li>Une nappe posee sur la mer mais plus haute qu'elle (la marche d'un bloc vue sur Venus) est retiree en entier.</li>
 *     <li>Un bloc de liquide qui touche le vide (sur un cote ou dessous) est retire s'il depasse le niveau normal,
 *     ou remplace par le bloc de bordure (il fermait mal un lac).</li>
 *     <li>Les coulees (liquide non source) au-dessus du niveau normal sont retirees.</li>
 * </ul>
 */
public class FluidCleanupFeature extends Feature<FluidCleanupFeature.Config> {

    public record Config(BlockState fluid, BlockState barrier, int minY, int maxLevel) implements FeatureConfiguration {

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
              BlockState.CODEC.fieldOf("fluid").forGetter(Config::fluid),
              BlockState.CODEC.fieldOf("barrier").forGetter(Config::barrier),
              Codec.INT.fieldOf("min_y").forGetter(Config::minY),
              Codec.INT.fieldOf("max_level").forGetter(Config::maxLevel)
        ).apply(instance, Config::new));
    }

    /**
     * Bord des tronçons voisins repasse aussi (1.0.7, retour de Guillaume du 20/11 : cubes de liquide suspendus) : un element pose plus
     * tard par un voisin (riviere de lave longue de 30 blocs, cuvette, cratere) peut ouvrir un liquide deja verifie, loin dans le tronçon :
     * la passe du dernier tronçon decore reprend ses voisins presque en entier. 15 blocs : le voisin regarde par la passe (un bloc plus
     * loin) reste dans les tronçons voisins, seuls ou un element peut lire et ecrire.
     */
    static final int MARGIN = 15;

    public FluidCleanupFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        Config config = context.config();
        Fluid fluid = config.fluid().getFluidState().getType();
        int x0 = (context.origin().getX() & ~15) - MARGIN, z0 = (context.origin().getZ() & ~15) - MARGIN;
        int cap = Math.min(level.getMaxBuildHeight() - 1, 255);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), side = new BlockPos.MutableBlockPos();
        int x1 = x0 + 16 + 2 * MARGIN, z1 = z0 + 16 + 2 * MARGIN;
        //Retirer un bloc expose en expose d'autres (bord d'une nappe surelevee, coulee de lave longue) : les voisins d'un bloc retire sont
        //revus aussitot (file), jusqu'a ce que plus rien ne bouge ; six passes completes n'y suffisaient pas (cubes suspendus, 20/11)
        it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue todo = new it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue();
        boolean changed = false;
        for (int dx = 0; dx < 16 + 2 * MARGIN; dx++) {
            for (int dz = 0; dz < 16 + 2 * MARGIN; dz++) {
                //le liquide est sous la surface (elle compte les liquides) : rien a chercher plus haut. Un voisin deja complet n'a plus
                //la carte de hauteur du bruit (un tronçon fini ne garde que les cartes finales : celle du bruit, recreee vide, rendrait le
                //bas du monde et sa bordure ne serait pas repassee) ; un voisin decore a sa carte du bruit figee avant ses elements : la
                //plus haute des deux
                int top = Math.min(cap, Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x0 + dx, z0 + dz),
                      level.getHeight(Heightmap.Types.WORLD_SURFACE, x0 + dx, z0 + dz)) + 2);
                for (int y = top; y >= config.minY(); y--) {
                    pos.set(x0 + dx, y, z0 + dz);
                    int r = fix(level, config, fluid, pos, side);
                    changed |= r != 0;
                    if (r == AIR) {
                        around(todo, pos);
                    }
                }
            }
        }
        //borne de surete (une file qui ne finirait pas) : bien au-dela de ce qu'une region de decoration peut contenir de liquide
        for (int guard = 0; !todo.isEmpty() && guard < 400_000; guard++) {
            pos.set(todo.dequeueLong());
            if (pos.getX() < x0 || pos.getX() >= x1 || pos.getZ() < z0 || pos.getZ() >= z1 || pos.getY() < config.minY() || pos.getY() > cap) {
                continue;
            }
            if (fix(level, config, fluid, pos, side) == AIR) {
                around(todo, pos);
            }
        }
        return changed;
    }

    /** Resultats de {@link #fix} : rien, bloc de bordure pose, liquide retire (de l'air : ses voisins sont a revoir). */
    private static final int NONE = 0, BARRIER = 1, AIR = 2;

    /** Voisins qu'un bloc devenu air peut exposer : les quatre cotes et le dessus. */
    private static void around(it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue todo, BlockPos pos) {
        todo.enqueue(BlockPos.asLong(pos.getX() + 1, pos.getY(), pos.getZ()));
        todo.enqueue(BlockPos.asLong(pos.getX() - 1, pos.getY(), pos.getZ()));
        todo.enqueue(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ() + 1));
        todo.enqueue(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ() - 1));
        todo.enqueue(BlockPos.asLong(pos.getX(), pos.getY() + 1, pos.getZ()));
    }

    /** Regles d'un bloc (en tete de classe) ; rend ce qui a ete fait. */
    private static int fix(WorldGenLevel level, Config config, Fluid fluid, BlockPos pos, BlockPos.MutableBlockPos side) {
        FluidState state = level.getFluidState(pos);
        if (state.isEmpty() || !state.getType().isSame(fluid)) {
            return NONE;
        }
        if (!state.isSource()) {
            if (pos.getY() > config.maxLevel()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                return AIR;
            }
            return NONE;
        }
        boolean above = pos.getY() > config.maxLevel();
        //Nappe posee sur la mer (au-dessus de son niveau) : toujours retiree
        if (above && restsOnSea(level, pos, side, fluid, config.maxLevel())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            return AIR;
        }
        if (!exposed(level, pos, side)) {
            return NONE;
        }
        level.setBlock(pos, above ? Blocks.AIR.defaultBlockState() : config.barrier(), 2);
        return above ? AIR : BARRIER;
    }

    /** Sous ce bloc, le liquide continue sans interruption jusqu'au niveau de la mer. */
    private static boolean restsOnSea(WorldGenLevel level, BlockPos pos, BlockPos.MutableBlockPos cursor, Fluid fluid, int seaLevel) {
        cursor.set(pos);
        while (cursor.getY() > seaLevel) {
            cursor.move(0, -1, 0);
            FluidState below = level.getFluidState(cursor);
            if (below.isEmpty() || !below.getType().isSame(fluid)) {
                return false;
            }
        }
        return true;
    }

    /** Touche le vide sur un cote ou dessous (le liquide coulerait). */
    private static boolean exposed(WorldGenLevel level, BlockPos pos, BlockPos.MutableBlockPos side) {
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
            side.setWithOffset(pos, direction);
            BlockState neighbor = level.getBlockState(side);
            if (neighbor.getFluidState().isEmpty() && !neighbor.isSolidRender(level, side)) {
                return true;
            }
        }
        return false;
    }
}
