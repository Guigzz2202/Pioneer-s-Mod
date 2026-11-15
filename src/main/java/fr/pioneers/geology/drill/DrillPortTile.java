package fr.pioneers.geology.drill;

import fr.pioneers.bronze.BronzeTile;
import fr.pioneers.bronze.steam.Steam;
import fr.pioneers.bronze.steam.SteamNode;
import fr.pioneers.bronze.steam.SteamVolume;
import fr.pioneers.registries.PiBronze;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Prise de la foreuse : un socle qui recoit la vapeur (tuyau sur n'importe quelle face) et livre le minerai (entonnoir, tuyau d'objets). */
public class DrillPortTile extends BronzeTile implements SteamNode {

    public DrillPortTile(BlockPos pos, BlockState state) {
        super(PiBronze.DRILL_PORT_TILE.get(), pos, state);
    }

    @Nullable
    public SteamDrillTile drill() {
        if (level == null) {
            return null;
        }
        //Lot 8a : hote trouve par le registre commun des hotes (cases de la foreuse formee, MultiblockPort)
        SteamDrillTile host = fr.pioneers.api.multiblock.MultiblockPort.house(level, worldPosition, SteamDrillTile.class);
        if (host != null && host.formed()) {
            return host;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (Direction e : new Direction[]{null, d.getClockWise()}) {
                BlockPos p = e == null ? worldPosition.relative(d) : worldPosition.relative(d).relative(e);
                if (level.getBlockEntity(p) instanceof SteamDrillTile drill && drill.formed()
                    && Math.abs(p.getX() - worldPosition.getX()) <= 1 && Math.abs(p.getZ() - worldPosition.getZ()) <= 1) {
                    return drill;
                }
            }
        }
        return null;
    }

    @Override
    public SteamVolume steam(Direction side) {
        SteamDrillTile drill = drill();
        return drill == null ? null : drill.chest();
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public void serverTick(ServerLevel level) {
        if (drill() != null) {
            Steam.exchangeAll(level, this);
        }
    }

    @Override
    public int guiHeight() {
        return 86;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            SteamDrillTile drill = drill();
            return drill == null ? LazyOptional.empty() : drill.getCapability(cap, Direction.DOWN);
        }
        return super.getCapability(cap, side);
    }
}
