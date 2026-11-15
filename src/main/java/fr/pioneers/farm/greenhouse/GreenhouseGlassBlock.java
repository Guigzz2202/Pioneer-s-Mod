package fr.pioneers.farm.greenhouse;

import fr.pioneers.common.block.Connections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 3) : VITRE DE SERRE (verre arme dans un cadre) : les vitres voisines de meme matiere forment une seule verriere (textures
 * connectees : le cadre ne se dessine que vers les cotes libres ; faces communes cachees comme tout verre). Une famille par matiere
 * ({@link TreatedGreenhouseGlassBlock}, {@link IronGreenhouseGlassBlock}).
 */
public abstract class GreenhouseGlassBlock extends GlassBlock implements Connections.Connected, GreenhouseWall {

    private final GreenhouseMaterial material;

    protected GreenhouseGlassBlock(Properties properties, GreenhouseMaterial material) {
        super(properties);
        this.material = material;
        registerDefaultState(Connections.unconnected(stateDefinition.any()));
    }

    @Override
    public GreenhouseMaterial greenhouseMaterial() {
        return material;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        Connections.addProperties(builder);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return Connections.connect(defaultBlockState(), context.getLevel(), context.getClickedPos(), connectFamily());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        return Connections.update(state, side, neighbor, connectFamily());
    }

    /** Textures connectees recalculees a toute pose ({@link Connections#heal}). */
    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        Connections.heal(state, level, pos, old);
    }
}
