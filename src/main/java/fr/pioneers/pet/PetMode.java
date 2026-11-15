package fr.pioneers.pet;

/**
 * 1.0.5-beta (lot 5, specification « Le monde vivant » §5.4) : modes d'un chien ou d'un chat. AUTONOME (defaut une fois lie a sa niche ou
 * son panier : territoire de 30 blocs, sommeil, visites au maitre), SUIVRE (comportement vanilla : suit le maitre, se teleporte),
 * ASSIS, CHASSE (chien seulement : piste le gibier dans la portee de son flair et guide le maitre). Ordre = valeur sauvee : ajouter EN FIN.
 */
public enum PetMode {
    AUTONOMOUS, FOLLOW, SIT, HUNT;

    public static final PetMode[] ALL = values();

    public static PetMode of(int i) {
        return i >= 0 && i < ALL.length ? ALL[i] : FOLLOW;
    }

    public String key() {
        return "pet.pioneers.mode." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Mode permis pour l'espece (le chat ne chasse pas le gibier). */
    public boolean allowed(boolean cat) {
        return !(cat && this == HUNT);
    }
}
