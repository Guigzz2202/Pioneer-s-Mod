package fr.pioneers.tech.kinetics.rope;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.kinetics.AxisKineticBlock;
import fr.pioneers.tech.kinetics.KineticSpecs;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Poulie (diametre 1, 2 ou 3 : petite, moyenne, grande) sur son arbre. Une corde ou une courroie relie deux poulies d'axes paralleles, dans un
 * meme plan, jusqu'a 24 blocs : rapport = rapport des diametres (Maj au second clic : corde croisee, sens inverse). Maj + clic main vide : decrocher.
 * 1.0.4-beta (lot 2) : a axe vertical, deux cordes (relais d'une ligne d'entrepot, {@link PulleyTile}).
 */
public class PulleyBlock extends AxisKineticBlock implements KineticSpecs.Specified {

    private static final Map<Direction.Axis, VoxelShape> SHAPES = new EnumMap<>(Direction.Axis.class);

    static {
        SHAPES.put(Direction.Axis.X, Shapes.or(Block.box(0, 6, 6, 16, 10, 10), Block.box(4, 2, 2, 12, 14, 14)));
        SHAPES.put(Direction.Axis.Y, Shapes.or(Block.box(6, 0, 6, 10, 16, 10), Block.box(2, 4, 2, 14, 12, 14)));
        SHAPES.put(Direction.Axis.Z, Shapes.or(Block.box(6, 6, 0, 10, 10, 16), Block.box(2, 2, 4, 14, 14, 12)));
    }

    /** Diametre en blocs (1, 2, 3). */
    public final int size;

    public PulleyBlock(int size, Properties properties) {
        super(properties);
        this.size = size;
    }

    /** Rayon d'enroulement en pixels (pour le rendu de la corde). */
    public float radiusPx() {
        return size * 4F;
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPES.get(axis(state));
    }

    @Override
    protected InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty() && level.getBlockEntity(pos) instanceof PulleyTile tile && tile.partner() != null) {
            if (!level.isClientSide) {
                tile.unlink(true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PulleyTile tile && tile.ropeCount() > 0) {
            tile.unlink(true);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void specs(List<Component> tooltip) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("kinetics.pioneers.spec.pulley", size);
        //1.0.4-beta (lot 27) : plusieurs cordes ; entrepot : la petite poulie est une station, les autres des relais
        tips.spec("kinetics.pioneers.spec.ropes", PulleyTile.MAX_ROPES);
        tips.spec(size == 1 ? "warehouse.pioneers.spec.station_pulley" : "warehouse.pioneers.spec.relay_pulley");
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new PulleyTile(pos, state);
    }
}
