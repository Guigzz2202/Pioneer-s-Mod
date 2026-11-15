package fr.pioneers.logistics.naval;

import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.block.PiBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.25 : interface du port : renvoie chaque capacite demandee sur une face a la capitainerie qui l'a reliee, comme si la face etait la
 * sienne (memes reglages de faces). Rien tant qu'elle n'est reliee a aucune capitainerie.
 */
public class HarborInterfaceTile extends PiBlockEntity {

    @Nullable
    private BlockPos master;

    public HarborInterfaceTile(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Nullable
    public BlockPos master() {
        return master;
    }

    /** Relie (ou delie, {@code null}) a une capitainerie ; les cables voisins revoient leurs raccords. */
    public void link(@Nullable BlockPos harbor) {
        if (harbor == null ? master == null : harbor.equals(master)) {
            return;
        }
        master = harbor == null ? null : harbor.immutable();
        setChanged();
        invalidateCaps();
        reviveCaps();
        if (level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    @Nullable
    private HarborTile harbor() {
        if (master == null || level == null || !level.isLoaded(master)) {
            return null;
        }
        return level.getBlockEntity(master) instanceof HarborTile h ? h : null;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        HarborTile h = harbor();
        //1.9 (MAJ RP, bloc 4) : pas de passage vers une capitainerie de l'autre cote d'une frontiere de claim refusee
        if (h != null && level != null && ClanApi.canTransfer(level, worldPosition, h.getBlockPos())) {
            LazyOptional<T> got = h.getCapability(cap, side);
            if (got.isPresent()) {
                return got;
            }
        }
        return super.getCapability(cap, side);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (master != null) {
            tag.putLong("master", master.asLong());
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        master = tag.contains("master") ? BlockPos.of(tag.getLong("master")) : null;
    }
}
