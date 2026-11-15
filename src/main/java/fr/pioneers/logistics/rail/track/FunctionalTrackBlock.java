package fr.pioneers.logistics.rail.track;

import fr.pioneers.logistics.rail.RailWorld;
import fr.pioneers.logistics.rail.TrackBlocks;
import fr.pioneers.logistics.rail.net.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.0 : rails fonctionnels (droits, qualite MKI) : freinage et acceleration (vehicules sans locomotive : triages, mines), detecteur
 * (signal de redstone et de comparateur quand un train l'occupe ; jamais necessaire aux trains).
 * <p>
 * 0.20.5 : pont-bascule (comparateur : remplissage du train pese ; clic : sa pesee) et rail de detelage (un train arrete a quai est coupe a
 * l'attelage qui se trouve au-dessus ; la partie arriere reste en manuel).
 */
public class FunctionalTrackBlock extends TrackBlock {

    public FunctionalTrackBlock(Piece.Kind kind, Properties properties) {
        super(TrackQuality.BASIC, kind, properties);
        registerDefaultState(stateDefinition.any().setValue(TrackBlocks.STRAIGHT_SHAPE, TrackShape.STRAIGHT_NS).setValue(TrackBlocks.POWERED, false));
    }

    @Override
    public EnumProperty<TrackShape> shapeProperty() {
        return TrackBlocks.STRAIGHT_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TrackBlocks.STRAIGHT_SHAPE, TrackBlocks.POWERED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(@NotNull BlockState state) {
        return kind == Piece.Kind.DETECTOR;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction side) {
        return kind == Piece.Kind.DETECTOR && state.getValue(TrackBlocks.POWERED) ? 15 : 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return kind == Piece.Kind.DETECTOR || kind == Piece.Kind.WEIGHBRIDGE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        if (kind == Piece.Kind.WEIGHBRIDGE) {
            if (!(level instanceof ServerLevel sl) || !state.getValue(TrackBlocks.POWERED)) {
                return 0;
            }
            RailWorld.Weigh w = RailWorld.get(sl).weigh(pos);
            return w == null ? 0 : w.comparator();
        }
        return kind == Piece.Kind.DETECTOR && state.getValue(TrackBlocks.POWERED) ? 15 : 0;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        InteractionResult r = super.use(state, level, pos, player, hand, hit);
        if (r != InteractionResult.PASS || kind != Piece.Kind.WEIGHBRIDGE) {
            return r;
        }
        if (level instanceof ServerLevel sl) {
            RailWorld.Weigh w = RailWorld.get(sl).weigh(pos);
            player.displayClientMessage(w == null ? Component.translatable("rail.pioneers.weigh.none") : w.text(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
