package fr.pioneers.logistics.robotics.hub;

import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.registries.PiRobotics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Port du hub robotique : recoit l'energie (FE), et donne acces au tampon (ce que les unites rapportent quand aucun stockage ne peut le
 * prendre) pour les tuyaux et convoyeurs.
 */
public class TileEntityRoboticPort extends TileEntityRoboticCasing {

    public TileEntityRoboticPort(BlockPos pos, BlockState state) {
        super(PiRobotics.PORT_TILE.get(), pos, state);
    }

    private LazyOptional<?> energyCap = LazyOptional.empty(), itemCap = LazyOptional.empty();

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY || capability == ForgeCapabilities.ITEM_HANDLER) {
            RoboticHubData hub = getHub();
            if (hub == null) {
                return LazyOptional.empty();
            }
            if (capability == ForgeCapabilities.ENERGY) {
                if (!energyCap.isPresent()) {
                    energyCap = LazyOptional.of(() -> hub.energyContainer);
                }
                return energyCap.cast();
            }
            if (!itemCap.isPresent()) {
                ItemSlots handler = new ItemSlots(hub.getBuffer(), Automation.EXTERNAL);
                itemCap = LazyOptional.of(() -> handler);
            }
            return itemCap.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    protected void onMasterChanged() {
        invalidateHubCaps();
        if (level != null) {
            //Les cables voisins se rebranchent
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    private void invalidateHubCaps() {
        energyCap.invalidate();
        itemCap.invalidate();
        energyCap = LazyOptional.empty();
        itemCap = LazyOptional.empty();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        invalidateHubCaps();
    }
}
