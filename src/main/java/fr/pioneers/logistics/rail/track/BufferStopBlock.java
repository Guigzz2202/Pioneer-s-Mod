package fr.pioneers.logistics.rail.track;

import fr.pioneers.logistics.rail.TrackBlocks;
import fr.pioneers.logistics.rail.net.Piece;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;

/** 0.20.0 : butoir : fin de voie, le train s'arrete contre ses tampons (forme tournee vers le regard du joueur a la pose). */
public class BufferStopBlock extends TrackBlock {

    public BufferStopBlock(Properties properties) {
        super(TrackQuality.BASIC, Piece.Kind.BUFFER, properties);
        registerDefaultState(stateDefinition.any().setValue(TrackBlocks.BUFFER_SHAPE, TrackShape.BUFFER_N));
    }

    @Override
    public EnumProperty<TrackShape> shapeProperty() {
        return TrackBlocks.BUFFER_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TrackBlocks.BUFFER_SHAPE);
    }
}
