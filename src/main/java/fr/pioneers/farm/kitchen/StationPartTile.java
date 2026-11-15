package fr.pioneers.farm.kitchen;

import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.farm.PiKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.6 (lot A2) : case secondaire d'un grand poste ({@link BigStationBlock}) : rien a elle, elle renvoie a la tuile de la case principale les
 * capacites (entonnoirs, tuyaux : objets et fluides). Les capacites rendues sont celles de la principale (invalidees avec elle). 1.0.5-beta
 * (lot 41) : plus de fiche au regard (l'interface du poste, ouverte depuis n'importe quelle case, la remplace).
 */
public class StationPartTile extends PiBlockEntity {

    public StationPartTile(BlockPos pos, BlockState state) {
        super(PiKitchen.STATION_PART_TILE.get(), pos, state);
    }

    /** Tuile du poste (case principale), ou null (poste en cours de casse, case pas chargee). */
    @Nullable
    public StationTile main() {
        if (level == null) {
            return null;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof BigStationBlock block)) {
            return null;
        }
        BlockPos main = block.mainPos(state, worldPosition);
        if (!level.isLoaded(main)) {
            return null;
        }
        return level.getBlockEntity(main) instanceof StationTile tile && !tile.isRemoved() ? tile : null;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        StationTile tile = main();
        if (tile != null) {
            LazyOptional<T> opt = tile.getCapability(cap, side);
            if (opt.isPresent()) {
                return opt;
            }
        }
        return super.getCapability(cap, side);
    }
}
