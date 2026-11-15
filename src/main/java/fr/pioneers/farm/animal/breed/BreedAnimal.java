package fr.pioneers.farm.animal.breed;

import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.BreedDef;

/**
 * Animal d'elevage d'une race (1.0.3-beta, lot 8 : une entite par race, Guillaume : « les races deviennent des animaux a part entiere »).
 * La race est celle du TYPE de l'entite ({@link PiBreeds#breedOf}) ; le sexe, la faim, la soif, la domestication et les minuteries restent
 * dans la capacite {@link fr.pioneers.farm.animal.AnimalData}. Chaque espece a UNE classe (vache, mouton, cochon, poule, chevre) qui etend
 * la classe vanilla : tout le comportement vanilla est garde et deux races d'une meme espece s'accouplent ({@code Animal.canMate} compare les
 * classes).
 */
public interface BreedAnimal {

    AnimalSpecies species();

    /** Indice de la race dans {@code species().breeds()}. */
    int breed();

    default BreedDef def() {
        return species().breed(breed());
    }
}
