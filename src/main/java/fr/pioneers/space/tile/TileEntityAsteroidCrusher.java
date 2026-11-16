package fr.pioneers.space.tile;

import fr.pioneers.api.data.DataType;
import fr.pioneers.core.data.PiDataTypes;
import fr.pioneers.space.research.PiScienceMachines;
import fr.pioneers.space.environment.PiWorldBlocks;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchNode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Broyeur d'asteroides : reduit les petits asteroides (roche, metal, glace, volcanique) en poudre et analyse leur composition
 * (un type de donnees par famille d'asteroides).
 * Chaque palier de la recherche d'asteroides demande autant d'asteroides de chacun des quatre types.
 */
public class TileEntityAsteroidCrusher extends TileEntityAnalyzer {

    public static final String[] TYPES = {"rock", "iron", "ice", "volcanic"};
    public static final ResearchNode[] TIERS = {ResearchNode.ASTEROIDS_1, ResearchNode.ASTEROIDS_2, ResearchNode.ASTEROIDS_3, ResearchNode.ASTEROIDS_4};
    public static final long ENERGY_PER_TICK_FE = 200;
    /** Lot 8b (vraie v2) : 2 x 2 a droite de la reserve d'energie. */
    private static final int[][] POSITIONS = {{30, 30}, {48, 30}, {30, 48}, {48, 48}};

    public TileEntityAsteroidCrusher(BlockPos pos, BlockState state) {
        super(PiScienceMachines.ASTEROID_CRUSHER_TILE.get(), pos, state);
    }

    /** Palier courant : le premier qui n'est pas termine. */
    public static ResearchNode currentTier() {
        for (ResearchNode tier : TIERS) {
            if (!ResearchEffects.has(tier)) {
                return tier;
            }
        }
        return TIERS[TIERS.length - 1];
    }

    public static ItemStack meteorite(int type) {
        return new ItemStack(PiWorldBlocks.METEORITES.get(TYPES[type] + "_meteorite"));
    }

    @Override
    protected int inputCount() {
        return 4;
    }

    @Override
    protected int[] inputPosition(int index) {
        return POSITIONS[index];
    }

    @Override
    protected ItemStack inputGhost(int index) {
        return new ItemStack(PiWorldBlocks.METEORITES.get(TYPES[index] + "_meteorite"));
    }

    @Override
    protected boolean accepts(int index, ItemStack stack) {
        return stack.is(PiWorldBlocks.METEORITES.get(TYPES[index] + "_meteorite").asItem());
    }

    @Nullable
    @Override
    public Sample sampleOf(int index, ItemStack stack) {
        return accepts(index, stack) ? new Sample(currentTier(), index, 1, PiDataTypes.asteroid(index)) : null;
    }

    /** Un reservoir par famille d'asteroides : les quatre types avancent sans se bloquer. */
    @Override
    protected int outputTankCount() {
        return TYPES.length;
    }

    @Override
    protected boolean acceptsData(int tank, DataType type) {
        return type == PiDataTypes.asteroid(tank);
    }

    @Override
    public int ticksPerItem() {
        return 20;
    }

    @Override
    public long energyPerTick() {
        return ENERGY_PER_TICK_FE;
    }

    @Override
    protected String guiName() {
        return "asteroid_crusher";
    }

    @Override
    public Component typeName(int type) {
        return Component.translatable("analyzer.pioneers.asteroid." + TYPES[type]);
    }
}
