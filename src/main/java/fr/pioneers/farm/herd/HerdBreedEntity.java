package fr.pioneers.farm.herd;

/**
 * 1.0.4-beta (lot 28) : entite d'une RACE de lapin, de cheval, d'ane ou de lama ({@link HerdBreeds}) : sa race est celle de son TYPE
 * ({@code pioneers:<race>_<espece>}), comme les vaches, moutons, cochons, poules et chevres depuis la 1.0.3-beta (lot 8).
 */
public interface HerdBreedEntity {

    /** Type de race ({@link GeneratedHerd#TYPES}). */
    GeneratedHerd.HerdType herdType();

    /** Race : index dans {@link GeneratedHerd#RABBITS} (lapins) ou {@link GeneratedHerd#HORSES} (chevaux, anes, lamas). */
    default int herdBreed() {
        return herdType().breed();
    }
}
