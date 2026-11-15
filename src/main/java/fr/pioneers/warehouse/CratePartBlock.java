package fr.pioneers.warehouse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 1) : piece passive de la caisse d'entrepot : CADRE d'acier (aretes de la boite), PANNEAU de bois traite (faces) ou PORT
 * (une case de face : les entonnoirs et le cable logistique y rangent et y prennent les objets de la caisse formee ; jamais le joueur).
 */
public class CratePartBlock extends CrateBlock {

    public enum Kind {
        FRAME, PANEL, PORT
    }

    public final Kind kind;

    public CratePartBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return kind == Kind.PORT ? new PortTile(pos, state) : null;
    }

    /** Tile du port : sans etat ; prete la capacite d'objets de la trappe de la caisse formee (rien sinon). */
    public static class PortTile extends BlockEntity {

        public PortTile(BlockPos pos, BlockState state) {
            super(PiWarehouse.PORT_TILE.get(), pos, state);
        }

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap == ForgeCapabilities.ITEM_HANDLER && level != null) {
                CrateHatchTile host = CrateBlock.host(level, worldPosition);
                if (host != null && host.formed()) {
                    return host.itemCapability().cast();
                }
            }
            return super.getCapability(cap, side);
        }
    }
}
