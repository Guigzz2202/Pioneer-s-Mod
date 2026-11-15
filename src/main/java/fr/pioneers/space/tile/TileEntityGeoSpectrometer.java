package fr.pioneers.space.tile;

import fr.pioneers.api.data.DataType;
import fr.pioneers.core.data.PiDataTypes;
import fr.pioneers.core.registries.PiBlocks;
import fr.pioneers.core.registries.PiItems;
import fr.pioneers.core.registries.PiScienceMachines;
import fr.pioneers.space.mining.MiningMaterial;
import fr.pioneers.space.research.BodyResearch;
import fr.pioneers.space.research.ResearchNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Spectrometre geologique : analyse les minerais bruts extraits par les bases minieres (un bloc de minerai brut vaut 9 minerais)
 * et en tire les donnees geologiques de leur astre (lunarium : donnees geologiques lunaires).
 */
public class TileEntityGeoSpectrometer extends TileEntityAnalyzer {

    public static final long ENERGY_PER_TICK_FE = 160;

    public TileEntityGeoSpectrometer(BlockPos pos, BlockState state) {
        super(PiScienceMachines.GEO_SPECTROMETER_TILE.get(), pos, state);
    }

    /** Metal d'un minerai brut (ou d'un bloc de minerai brut), null sinon. */
    @Nullable
    public static MiningMaterial materialOf(ItemStack stack) {
        Item item = stack.getItem();
        for (MiningMaterial material : MiningMaterial.values()) {
            if (item == PiItems.RAW.get(material).get() || item == PiBlocks.RAW_BLOCKS.get(material).asItem()) {
                return material;
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
        MiningMaterial material = materialOf(stack);
        return material != null && BodyResearch.node(material.body, ResearchNode.Kind.GEOLOGY) != null;
    }

    @Nullable
    @Override
    public Sample sampleOf(int index, ItemStack stack) {
        MiningMaterial material = materialOf(stack);
        ResearchNode node = material == null ? null : BodyResearch.node(material.body, ResearchNode.Kind.GEOLOGY);
        DataType data = material == null ? null : PiDataTypes.geological(material.body);
        if (node == null || data == null) {
            return null;
        }
        boolean block = stack.getItem() == PiBlocks.RAW_BLOCKS.get(material).asItem();
        return new Sample(node, 0, block ? 9 : 1, data);
    }

    @Override
    public int ticksPerItem() {
        return 10;
    }

    @Override
    public long energyPerTick() {
        return ENERGY_PER_TICK_FE;
    }

    @Override
    protected String guiName() {
        return "geo_spectrometer";
    }
}
