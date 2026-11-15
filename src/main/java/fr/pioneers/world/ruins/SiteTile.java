package fr.pioneers.world.ruins;

import fr.pioneers.core.block.PiBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/** 1.9 (MAJ RP, bloc 1) : tuile qui connait son site (Console de perimetre). */
public class SiteTile extends PiBlockEntity {

    protected long site = Long.MIN_VALUE;

    public SiteTile(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void setSite(long site) {
        this.site = site;
        setChanged();
    }

    public long site() {
        return site;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("site", site);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        site = tag.contains("site") ? tag.getLong("site") : Long.MIN_VALUE;
    }
}
