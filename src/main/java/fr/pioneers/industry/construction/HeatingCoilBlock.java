package fr.pioneers.industry.construction;

import fr.pioneers.transport.CableTier;

/**
 * 1.0.4-beta (lot 21, 21c) : bobine de chauffe d'un tier, TOUJOURS verticale (spires horizontales sur les quatre cotes, bornes dessus /
 * dessous) ; textures connectees entre bobines du meme tier ; aucune orientation, aucune fonction de multibloc.
 */
public class HeatingCoilBlock extends TwinConnectedBlock {

    public HeatingCoilBlock(CableTier tier, Properties properties) {
        super("heating_coil_", tier, properties);
    }
}
