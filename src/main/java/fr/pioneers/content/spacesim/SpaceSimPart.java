package fr.pioneers.content.spacesim;

/** Pieces du Simulateur spatial. */
public enum SpaceSimPart {
    /** Boitier de la coque. */
    CASING("space_simulator_casing"),
    /** Verre de la coque. */
    GLASS("space_simulator_glass"),
    /** Port : energie et plasma stellaire en entree, ou produits en sortie (reglage a la cle). */
    PORT("space_simulator_port"),
    /** Projecteur de faille spatio-temporelle : un au centre de chaque face du globe, il ouvre et tient la bulle d'espace. */
    PROJECTOR("space_rift_projector"),
    /** Bobine supreme : l'anneau d'energie qui ceint le globe (invisible en marche, remplacee par un torrent d'energie). */
    COIL("supreme_space_coil");

    public final String id;

    SpaceSimPart(String id) {
        this.id = id;
    }

    /** Piece acceptee sur la coque (le projecteur a sa place reservee). */
    public boolean isShell() {
        return this == CASING || this == GLASS || this == PORT;
    }
}
