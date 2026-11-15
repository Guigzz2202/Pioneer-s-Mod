package fr.pioneers.rail.block;

import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.rail.RailWorld;
import fr.pioneers.rail.net.Electrification;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/** 0.20.2 : sous-station (MKI a MKIV) : conducteur de l'electrification qui porte la reserve d'energie de sa grille. */
public class SubstationBlock extends TieredMachineBlock {

    public SubstationBlock(int tier, Properties properties, Supplier<? extends BlockEntityType<?>> type, BiFunction<BlockPos, BlockState, BlockEntity> factory) {
        super(tier, properties, type, factory);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel sl && !old.is(this)) {
            RailWorld w = RailWorld.get(sl);
            if (w.power.conductor(pos) == null) {
                w.power.add(new Electrification.Conductor(pos.immutable(), Electrification.Kind.SUBSTATION, tier()));
                w.setDirty();
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (level instanceof ServerLevel sl && !next.is(this)) {
            RailWorld w = RailWorld.get(sl);
            //La reserve passe aux autres sous-stations de sa grille (perdue s'il n'y en a pas)
            w.power.removeSubstation(pos, w.net);
            w.setDirty();
        }
        super.onRemove(state, level, pos, next, moving);
    }

    /** 0.20.8 : forme d'apres le maillage, tournee vers la voie voisine. */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
          CollisionContext ctx) {
        return RailShapes.outline("substation_" + tier(), state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
          CollisionContext ctx) {
        return RailShapes.get("substation_" + tier(), state.getValue(FACING));
    }
}
