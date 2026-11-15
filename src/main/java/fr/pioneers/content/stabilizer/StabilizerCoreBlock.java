package fr.pioneers.content.stabilizer;

import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.registries.PiStabilizer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Noyau du Stabilisateur spatial (noyau de singularite ou Stellar Core) : se pose au centre exact du globe et fixe son regime. Clic droit :
 * interface de la structure. Consomme a l'allumage ({@link #SPENT} : invisible, ne rend rien a la casse) ; pour rallumer, clic droit sur la
 * paroi avec un nouveau noyau en main.
 */
public class StabilizerCoreBlock extends MachineBlock {

    private static final VoxelShape SHAPE = Block.box(4, 4, 4, 12, 12, 12);
    public static final BooleanProperty SPENT = BooleanProperty.create("spent");
    public final Regime regime;

    public StabilizerCoreBlock(Regime regime, Properties properties) {
        super(properties, () -> PiStabilizer.CORE_TILE.get(), StabilizerCoreTile::new);
        this.regime = regime;
        shaped(new VoxelShape[]{SHAPE, SHAPE, SHAPE, SHAPE});
        registerDefaultState(defaultBlockState().setValue(SPENT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SPENT);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return state.getValue(SPENT) ? RenderShape.INVISIBLE : super.getRenderShape(state);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return state.getValue(SPENT) ? Shapes.empty() : super.getShape(state, level, pos, context);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && !old.is(state.getBlock())) {
            StabilizerStructure.queue(server, pos);
        }
    }
}
