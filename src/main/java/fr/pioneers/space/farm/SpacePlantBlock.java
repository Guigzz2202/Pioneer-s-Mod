package fr.pioneers.space.farm;

import fr.pioneers.farm.crop.Genes;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Plante spatiale sauvage : pousse sur le sol de son astre. Cassee, elle donne ses graines (et parfois sa recolte).
 * 1.12.3 (lot F3) : ses graines ont des genes sauvages (1 a 3), comme celles des herbes de la Terre.
 */
public class SpacePlantBlock extends BushBlock {

    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 13, 14);
    private final SpacePlant plant;

    public SpacePlantBlock(SpacePlant plant, Properties properties) {
        super(properties);
        this.plant = plant;
    }

    public SpacePlant getPlant() {
        return plant;
    }

    @Override
    protected boolean mayPlaceOn(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return plant.planet.isSoil(state);
    }

    /** Graines du butin (table de butin) : genes sauvages tires pour chaque pile. */
    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(@NotNull BlockState state, @NotNull LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        RandomSource random = builder.getLevel().random;
        for (ItemStack stack : drops) {
            if (stack.getItem() instanceof ItemSpaceSeeds && !Genes.has(stack)) {
                Genes.wild(random).apply(stack);
            }
        }
        return drops;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, offset.y, offset.z);
    }
}
