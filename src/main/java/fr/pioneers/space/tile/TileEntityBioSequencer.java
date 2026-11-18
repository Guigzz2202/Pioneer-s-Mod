package fr.pioneers.space.tile;

import fr.pioneers.api.data.DataType;
import fr.pioneers.core.data.PiDataTypes;
import fr.pioneers.core.registries.PiItems;
import fr.pioneers.space.research.PiScienceMachines;
import fr.pioneers.space.farm.SpacePlant;
import fr.pioneers.space.research.BodyResearch;
import fr.pioneers.space.research.ResearchNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Sequenceur biologique : sequence le genome des recoltes des plantes spatiales (il en faut enormement : on les cultive
 * en serre) et en tire les donnees biologiques de leur astre.
 */
public class TileEntityBioSequencer extends TileEntityAnalyzer {

    public static final long ENERGY_PER_TICK_FE = 120;

    public TileEntityBioSequencer(BlockPos pos, BlockState state) {
        super(PiScienceMachines.BIO_SEQUENCER_TILE.get(), pos, state);
    }

    /** Plante d'une recolte, null sinon. */
    @Nullable
    public static SpacePlant plantOf(ItemStack stack) {
        Item item = stack.getItem();
        for (SpacePlant plant : SpacePlant.values()) {
            if (item == PiItems.PRODUCE.get(plant).get()) {
                return plant;
            }
        }
        return null;
    }

    @Override
    protected int inputCount() {
        return 1;
    }

    @Override
    protected int[] inputPosition(int index) {
        //Lot 8b (vraie v2) : au milieu de la place des entrees
        return new int[]{39, 39};
    }

    @Override
    protected boolean accepts(int index, ItemStack stack) {
        return plantOf(stack) != null;
    }

    @Nullable
    @Override
    public Sample sampleOf(int index, ItemStack stack) {
        SpacePlant plant = plantOf(stack);
        ResearchNode node = plant == null ? null : BodyResearch.node(plant.planet.body(), ResearchNode.Kind.BIOLOGY);
        DataType data = plant == null ? null : PiDataTypes.biological(plant.planet.body());
        return node == null || data == null ? null : new Sample(node, 0, 1, data);
    }

    @Override
    public int ticksPerItem() {
        return 5;
    }

    @Override
    public long energyPerTick() {
        return ENERGY_PER_TICK_FE;
    }

    @Override
    protected String guiName() {
        return "bio_sequencer";
    }
}
