package fr.pioneers.space.client;

import net.minecraft.client.model.geom.ModelPart;

/**
 * Maquette de fusee a deux etages : l'etage superieur (capsule, coiffe, etage haut) et le premier etage (moteurs, corps, boosters),
 * qui se separe pendant l'ascension.
 */
public interface StagedRocketModel {

    ModelPart root();

}
