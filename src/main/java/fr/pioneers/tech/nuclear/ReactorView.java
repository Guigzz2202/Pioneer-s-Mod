package fr.pioneers.tech.nuclear;

import net.minecraft.core.BlockPos;

/**
 * Etat d'un reacteur vu par le client (0.17.1) : ce que dessinent les rendus animes du coeur (lueur Tcherenkov du MKI, bain de sodium du
 * MKII, plasma poussiereux du MKIII). Synchronise par le controleur a chaque changement notable (au plus 4 fois par seconde).
 * Obtenu par {@code ControllerTile#reactorView()} des deux cotes.
 */
public interface ReactorView {

    /** 1 = fission MKI, 2 = neutrons rapides MKII, 3 = fragments de fission MKIII ; 0.17.2 : 4 = tokamak, 5 = stellarator (fusion). */
    int mark();

    /** Puissance relative (0..1), lissee. */
    float power();

    /** Temperature relative a la nominale des carcasses (0 = ambiante, 1 = nominale ; au-dela : surchauffe) ; fusion : plasma / ignition. */
    float heat();

    /** Insertion des barres (0..1) ; fusion : chauffage (0 coupe, 0,05..1 en marche). */
    float rods();

    /** Arret d'urgence ; fusion : flash de disruption (3 s). */
    boolean scrammed();

    /** Assemblages (ou cartouches) charges / places ; fusion : densite du plasma en % / 100. */
    int fuel();

    int capacity();

    /** MKII : remplissage du sodium primaire (0..1) ; MKIII et fusion : champ de confinement (0..1) ; MKI : eau (0..1). */
    float coolant();

    /** Bornes de la structure (coins inclus) : le rendu du coeur se place dedans. */
    BlockPos min();

    BlockPos max();
}
