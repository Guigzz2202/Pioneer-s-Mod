package fr.pioneers.equipment.gear;

import net.minecraft.world.item.ItemStack;

/**
 * 0.20.15 : objet modulaire (armure, outil, lame, fusil) : son tier et sa sorte. Les modules installes, leur etat et l'energie sont dans le
 * NBT de la pile ({@link Modules}).
 */
public interface ModularGear {

    ModularTier tier();

    GearKind kind();

    static boolean is(ItemStack stack) {
        return stack.getItem() instanceof ModularGear;
    }
}
