package fr.pioneers.industry.multi;

import fr.pioneers.common.block.Connections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * Verre industriel : paroi transparente d'un multibloc (on y voit les modules). Prend le metier de la machine a la formation ; les vitres
 * voisines forment une seule baie (famille {@code pioneers:connects/industrial_glass}).
 * <p>
 * 0.20.28 : une vitre par tier ({@link #tier} 0..3, fabriquee avec une carcasse du meme tier) : son cadre en porte la couleur (propriete
 * {@link IndustryStyle#TIER} = tier + 1, jamais repeinte) et, comme une carcasse, la plus faible donne son tier au multibloc.
 */
public class IndustrialGlassBlock extends GlassBlock implements Connections.Connected {

    public final int tier;

    public IndustrialGlassBlock(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(Connections.unconnected(stateDefinition.any().setValue(IndustryStyle.STYLE, IndustryStyle.NEUTRAL)
              .setValue(IndustryStyle.TIER, tier + 1)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(IndustryStyle.STYLE, IndustryStyle.TIER);
        Connections.addProperties(builder);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return Connections.connect(defaultBlockState(), context.getLevel(), context.getClickedPos(), Connections.INDUSTRIAL_GLASS);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        return Connections.update(state, side, neighbor, Connections.INDUSTRIAL_GLASS);
    }

    @Override
    public TagKey<Block> connectFamily() {
        return Connections.INDUSTRIAL_GLASS;
    }

    /** 1.0.4-beta (lot 23) : textures connectees recalculees a toute pose ({@link Connections#heal}). */
    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        Connections.heal(state, level, pos, old);
    }
}
