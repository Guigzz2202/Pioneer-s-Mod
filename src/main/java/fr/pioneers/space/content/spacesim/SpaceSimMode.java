package fr.pioneers.space.content.spacesim;

import java.util.Locale;

/**
 * Ce que simule le Simulateur spatial, selon l'objet pose dans la case du coeur :
 * <ul>
 *     <li>{@link #SOLAR} (Stellar Core, garde tant que le soleil brille) : un systeme solaire miniature ; chaque planete simulee installee
 *     produit les ressources de sa recette ;</li>
 *     <li>{@link #GALAXY} (Void Singularity Cell, consommee au Big Bang) : une galaxie entiere, choisie dans l'interface.</li>
 * </ul>
 */
public enum SpaceSimMode {
    NONE,
    SOLAR,
    GALAXY;

    public static SpaceSimMode byIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : NONE;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
