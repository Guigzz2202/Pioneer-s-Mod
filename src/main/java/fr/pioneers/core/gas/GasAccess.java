package fr.pioneers.core.gas;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.compat.mekanism.MekanismCompat;
import fr.pioneers.compat.mekanism.MekanismGasBridge;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Acces aux reservoirs de gaz d'un bloc : la capacite Pioneer's d'abord, sinon (si Mekanism est la) ceux de Mekanism. */
public final class GasAccess {

    private GasAccess() {
    }

    @Nullable
    public static IGasHandler find(@Nullable BlockEntity tile, @Nullable Direction side) {
        if (tile == null) {
            return null;
        }
        IGasHandler handler = tile.getCapability(PioneersAPI.GAS_HANDLER, side).resolve().orElse(null);
        if (handler == null && MekanismCompat.LOADED) {
            handler = MekanismGasBridge.find(tile, side);
        }
        return handler;
    }

    /** Reservoirs de gaz d'un objet : notre capacite, sinon ceux de Mekanism. */
    @Nullable
    public static IGasHandler find(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        IGasHandler handler = stack.getCapability(PioneersAPI.GAS_HANDLER).resolve().orElse(null);
        if (handler == null && MekanismCompat.LOADED) {
            handler = MekanismGasBridge.find(stack);
        }
        return handler;
    }
}
