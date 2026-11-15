package fr.pioneers.farm.crop;

import net.minecraft.world.level.block.Block;

/**
 * 1.0.3-beta (lot 13) : substrat a champignons (compost, paille et sciure tasses, ensemences par le mycelium) : sol des champignons
 * cultives ({@link MushroomCropBlock}), toujours humide (compte comme une terre irriguee pour le climat), pousse x
 * {@link MushroomCropBlock#SUBSTRATE_GROWTH}. Bloc plein, a la pelle ; modele, texture et recette : {@code tools/gc/farm2/mushrooms.py}.
 */
public class MushroomSubstrateBlock extends Block {

    public MushroomSubstrateBlock(Properties properties) {
        super(properties);
    }
}
