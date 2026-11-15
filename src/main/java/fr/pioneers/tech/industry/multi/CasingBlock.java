package fr.pioneers.tech.industry.multi;

import fr.pioneers.core.common.block.Connections;
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
 * Carcasse industrielle : donne son tier au multibloc (le plus faible de ses carcasses compte). Tier -1 : carcasse brulee (a remplacer).
 * Prend le metier de la machine a la formation ({@link IndustryStyle}) ; textures connectees avec la famille {@code pioneers:connects/industrial}
 * (carcasses, hublots, ports, controleurs).
 */
public class CasingBlock extends Block implements Connections.Connected {

    public final int tier;

    public CasingBlock(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(Connections.unconnected(stateDefinition.any().setValue(IndustryStyle.STYLE, IndustryStyle.NEUTRAL)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(IndustryStyle.STYLE);
        Connections.addProperties(builder);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return Connections.connect(defaultBlockState(), context.getLevel(), context.getClickedPos(), Connections.INDUSTRIAL);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        return Connections.update(state, side, neighbor, Connections.INDUSTRIAL);
    }

    public boolean scorched() {
        return tier < 0;
    }

    @Override
    public TagKey<Block> connectFamily() {
        return Connections.INDUSTRIAL;
    }

    /** 1.0.4-beta (lot 23) : textures connectees recalculees a toute pose ({@link Connections#heal}). */
    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        Connections.heal(state, level, pos, old);
    }
}
