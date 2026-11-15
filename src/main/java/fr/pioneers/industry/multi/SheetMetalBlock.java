package fr.pioneers.industry.multi;

import net.minecraft.world.level.block.Block;

/**
 * 1.0 : tole de structure (acier, aluminium, titane, tungstene : une par tier). Paroi des contenants (cuves, reservoirs, bacs) au-dessus
 * de leur socle en carcasses ; donne son tier au multibloc comme une carcasse (le plus faible compte). Modele : tools/gc/industry/sheets.py.
 */
public class SheetMetalBlock extends Block {

    public final int tier;

    public SheetMetalBlock(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }
}
