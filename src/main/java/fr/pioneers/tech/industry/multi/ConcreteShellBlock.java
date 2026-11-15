package fr.pioneers.tech.industry.multi;

import net.minecraft.world.level.block.Block;

/**
 * 0.20.26 : voile de beton : coque des tours de refroidissement ({@link IndustryShape#HYPERBOLOID}), a la place des toles. Sans tier : le
 * bassin en carcasses et les pieds en cadres donnent le tier de la tour. Refuse sur les autres contenants. Modele : tools/gc/industry/sheets.py.
 */
public class ConcreteShellBlock extends Block {

    public ConcreteShellBlock(Properties properties) {
        super(properties);
    }
}
