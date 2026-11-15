package fr.pioneers.naval;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.27 : balise maritime : bouee a feu clignotant, posee sur l'eau (comme un nenuphar) ou sur un bloc plein (cote, rocher). Inscrite
 * dans {@link NavalWorld} a la pose ; reliee a d'autres balises avec la {@link NavigationChartItem carte de navigation}. Clic droit main
 * vide : ses liaisons et les ports que l'on atteint par son reseau. La carte marine ignore la bouee (les navires passent).
 */
public class SeaBeaconBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 15, 12);

    public SeaBeaconBlock(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        BlockPos below = pos.below();
        FluidState f = level.getFluidState(below);
        if (WaterChart.navigableFluid(f) && level.getFluidState(pos).isEmpty()) {
            return true;
        }
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel sl && !old.is(this)) {
            NavalWorld.get(sl).addBeacon(pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (level instanceof ServerLevel sl && !next.is(this)) {
            NavalWorld.get(sl).removeBeacon(pos);
        }
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof NavigationChartItem) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel sl)) {
            return InteractionResult.SUCCESS;
        }
        NavalWorld w = NavalWorld.get(sl);
        SeaBeacon b = w.beacon(pos.asLong());
        if (b == null) {
            b = w.addBeacon(pos);
        }
        int ok = 0, pending = 0, none = 0;
        for (long o : b.links) {
            SeaBeacon.Lane l = w.lane(b.pos, o);
            if (l == null || l.state == SeaBeacon.Lane.State.PENDING) {
                pending++;
            } else if (l.state == SeaBeacon.Lane.State.OK) {
                ok++;
            } else {
                none++;
            }
        }
        player.displayClientMessage(Component.translatable("naval.pioneers.beacon.info", b.links.size(), ok, pending, none), false);
        List<String> ports = w.destinations(b);
        player.displayClientMessage(ports.isEmpty() ? Component.translatable("naval.pioneers.beacon.no_ports", (int) SeaBeacon.VIEW)
                                                    : Component.translatable("naval.pioneers.beacon.ports", String.join(", ", ports)), false);
        return InteractionResult.CONSUME;
    }
}
