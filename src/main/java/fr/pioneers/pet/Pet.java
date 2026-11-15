package fr.pioneers.pet;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.TamableAnimal;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 5) : chien ({@link PetDog}) ou chat ({@link PetCat}) de race : mode, niche ou panier lie, repos (couche, en boule).
 * Le maitre et l'adoption sont communs aux montures : {@link PetMaster}.
 */
public interface Pet {

    /** Repos : debout, couche, en boule (chat). */
    int AWAKE = 0, LYING = 1, CURLED = 2;

    TamableAnimal self();

    boolean isCat();

    /** Race : index dans {@link GeneratedPets#DOGS} ou {@link GeneratedPets#CATS}. */
    int breed();

    PetMode mode();

    void setMode(PetMode mode);

    /** Niche ou panier lie (null : aucun). */
    @Nullable
    GlobalPos home();

    void setHome(@Nullable GlobalPos home);

    int rest();

    void setRest(int rest);

    /** Etat de la cervelle (minuteries des buts autonomes, poisson porte) : serveur seulement. */
    PetBrain brain();

    /** Mode en vigueur : AUTONOME sans niche = SUIVRE (comportement vanilla). */
    default PetMode effectiveMode() {
        PetMode m = mode();
        return m == PetMode.AUTONOMOUS && home() == null ? PetMode.FOLLOW : m;
    }

    default String breedId() {
        return isCat() ? GeneratedPets.CATS[breed()].id() : GeneratedPets.DOGS[breed()].id();
    }
}
