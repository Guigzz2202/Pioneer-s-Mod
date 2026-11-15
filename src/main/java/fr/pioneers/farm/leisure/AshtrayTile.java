package fr.pioneers.farm.leisure;

import fr.pioneers.core.block.PiBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/** 1.5 : tuile du cendrier : ce qui reste a bruler du cigare allume pose ({@code remaining}, ticks de cigare), qui baisse d'un tick sur deux. */
public class AshtrayTile extends PiBlockEntity {

    private int remaining;

    public AshtrayTile(BlockPos pos, BlockState state) {
        super(PiLeisure.ASHTRAY_TILE.get(), pos, state);
    }

    public int remaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = remaining;
        setChanged();
    }

    void serverTick() {
        if (level == null || level.getGameTime() % 20 != 0) {
            return;
        }
        remaining -= 10;
        if (remaining <= 0) {
            remaining = 0;
            level.setBlock(worldPosition, getBlockState().setValue(AshtrayBlock.CIGAR, 0), 3);
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.2F, 1.6F);
        }
        setChanged();
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        remaining = tag.getInt("Remaining");
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Remaining", remaining);
    }
}
