package fr.pioneers.content.stabilizer;

/** Pieces du Stabilisateur spatial (paroi et interieur). */
public enum StabilizerPart {
    /** Boitier de la paroi. */
    CASING,
    /** Verre de la paroi. */
    GLASS,
    /** Port de matiere : entree (antimatiere, lave) ou sortie (produit), regle a la cle. */
    PORT,
    /** Port d'alimentation d'un emetteur (centre des faces laterales). */
    POWER_PORT,
    /** Controleur : verifie la structure, recoit le noyau et ouvre l'interface (en bas au centre d'une des 4 faces laterales). */
    CONTROLLER,
    /** Emetteur de champ, a l'interieur, juste derriere chaque port d'alimentation. */
    EMITTER;

    /** Piece de la paroi (le verre compris) ; l'emetteur est a l'interieur. */
    public boolean isWall() {
        return this != EMITTER;
    }

    /** Piece acceptee la ou le motif exige un boitier (contour arrondi du globe). */
    public boolean isFrame() {
        return this == CASING;
    }
}
