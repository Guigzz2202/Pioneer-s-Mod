package fr.pioneers.logistics.rail.track;

import fr.pioneers.logistics.rail.TrackBlocks;
import fr.pioneers.logistics.rail.net.Piece;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;

/** 0.20.0 : voie d'une qualite (bois, MKI a MKIV) : droites, pentes, courbes, aiguillages, croisement. */
public class RailTrackBlock extends TrackBlock {

    public RailTrackBlock(TrackQuality quality, Properties properties) {
        super(quality, Piece.Kind.TRACK, properties);
        registerDefaultState(stateDefinition.any().setValue(TrackBlocks.SHAPE, TrackShape.STRAIGHT_NS).setValue(TrackBlocks.ROUTE, 0));
    }

    @Override
    public EnumProperty<TrackShape> shapeProperty() {
        return TrackBlocks.SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TrackBlocks.SHAPE, TrackBlocks.ROUTE);
    }
}
