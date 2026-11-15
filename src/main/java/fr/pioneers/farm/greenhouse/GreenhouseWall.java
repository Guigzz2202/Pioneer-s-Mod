package fr.pioneers.farm.greenhouse;

/**
 * 1.0.5-beta (lot 3) : bloc de PAROI de serre (vitres, cadres, trappes vitrees, regulateur) : le releve ({@link GreenhouseScan}) le compte
 * comme paroi par sa classe (le banc et les GameTests n'ont pas de tags), et aussi tout bloc du tag {@code pioneers:greenhouse_wall}
 * (packs de donnees). Les portes ({@code DoorBlock}) comptent aussi, ouvertes ou fermees.
 */
public interface GreenhouseWall {

    /** Matiere du bloc (plan des fautes : la vitre conseillee suit la matiere de la serre). */
    GreenhouseMaterial greenhouseMaterial();
}
