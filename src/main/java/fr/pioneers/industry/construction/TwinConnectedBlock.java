package fr.pioneers.industry.construction;

import fr.pioneers.common.block.Connections;
import fr.pioneers.transport.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 21c) : bloc de construction plein a textures connectees, famille {@code pioneers:connects/<id>} : le cadre du tier
 * disparait entre deux blocs identiques voisins. Aucune orientation (les anciens etats {@code facing} / {@code axis} sont ignores au
 * chargement : propriete inconnue -> etat par defaut).
 */
public abstract class TwinConnectedBlock extends Block implements Connections.Connected {

    public final CableTier tier;
    private final TagKey<Block> family;

    protected TwinConnectedBlock(String prefix, CableTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.family = Connections.family(prefix + tier.id());
        registerDefaultState(Connections.unconnected(stateDefinition.any()));
    }

    public TagKey<Block> family() {
        return family;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        Connections.addProperties(builder);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return Connections.connect(defaultBlockState(), context.getLevel(), context.getClickedPos(), family);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        return Connections.update(state, side, neighbor, family);
    }

    @Override
    public TagKey<Block> connectFamily() {
        return family;
    }

    /** 1.0.4-beta (lot 23) : textures connectees recalculees a toute pose ({@link Connections#heal}). */
    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        Connections.heal(state, level, pos, old);
    }
}
