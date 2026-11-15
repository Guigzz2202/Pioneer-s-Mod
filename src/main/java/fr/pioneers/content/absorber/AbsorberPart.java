package fr.pioneers.content.absorber;

/** Pieces de l'absorbeur d'energie solaire (memes ids qu'avant le socle). */
public enum AbsorberPart {
    /** Boitier : aretes obligatoires, et parois. */
    CASING("solar_absorber_casing"),
    /** Port : energie en sortie ; caloporteur froid en entree, ou caloporteur chaud en sortie (cle). */
    PORT("solar_absorber_port"),
    /** Capteur : toute la face superieure (hors aretes), et seulement la. */
    COLLECTOR("solar_absorber_collector");

    public final String id;

    AbsorberPart(String id) {
        this.id = id;
    }

    /** Peut former une paroi laterale ou le fond. */
    public boolean isWall() {
        return this != COLLECTOR;
    }
}
