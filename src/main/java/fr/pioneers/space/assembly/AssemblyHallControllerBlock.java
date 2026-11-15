package fr.pioneers.space.assembly;

import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.industry.multi.IndustryStyle;
import fr.pioneers.tile.TileEntityAssemblyHall;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9.2 (retours de Guillaume) : controleur d'un multibloc d'assemblage. Cube plein a ecran, habille comme les controleurs industriels
 * (corps connecte {@code IndustrialBodyModel}, metier fixe {@link AssemblyHall.Kind#style}, tier {@link IndustryStyle#TIER} peint a la
 * formation). Retire : la structure redevient neutre.
 */
public class AssemblyHallControllerBlock extends MachineBlock {

    public final AssemblyHall.Kind kind;

    public AssemblyHallControllerBlock(AssemblyHall.Kind kind, Properties properties, Supplier<? extends BlockEntityType<?>> type,
          BiFunction<BlockPos, BlockState, BlockEntity> factory) {
        super(properties, type, factory);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(IndustryStyle.TIER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(IndustryStyle.TIER);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TileEntityAssemblyHall hall) {
            hall.dismantle();
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
