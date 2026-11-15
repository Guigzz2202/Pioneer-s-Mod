package fr.pioneers.core.machine;

import fr.pioneers.core.block.PiBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Tile d'un bloc fantome : position de son bloc principal ; capacites renvoyees a la machine selon le decalage. */
public class BoundingTile extends PiBlockEntity {

    @Nullable
    private BlockPos main;

    public BoundingTile(BlockPos pos, BlockState state) {
        super(MachineBounds.TILE.get(), pos, state);
    }

    @Nullable
    public BlockPos getMain() {
        return main;
    }

    public void setMain(BlockPos main) {
        this.main = main.immutable();
        setChanged();
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (main != null && level != null && level.getBlockEntity(main) instanceof MachineTile machine) {
            Vec3i offset = worldPosition.subtract(main);
            return machine.boundCapability(cap, side, offset);
        }
        return super.getCapability(cap, side);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (main != null) {
            tag.put("main", NbtUtils.writeBlockPos(main));
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        main = tag.contains("main") ? NbtUtils.readBlockPos(tag.getCompound("main")) : null;
    }
}
