package fr.pioneers.core.compat.mekanism;

import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Menus encore fournis par Mekanism (multiblocs du lot E) : a supprimer avec eux. */
public final class MekanismMenus {

    private MekanismMenus() {
    }

    public static boolean isOpen(AbstractContainerMenu menu, BlockEntity tile) {
        return menu instanceof MekanismTileContainer<?> container && container.getTileEntity() == tile;
    }
}
