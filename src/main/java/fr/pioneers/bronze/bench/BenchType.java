package fr.pioneers.bronze.bench;

import java.util.Locale;

/**
 * Les ateliers specialises de l'age du bronze : chacun a ses recettes ({@code pioneers:bench}, champ {@code bench}), ses cases d'outils et sa
 * couleur d'interface. Plus aucune « sous-recette » dans la grille : on pose outils et materiaux, on choisit la piece.
 */
public enum BenchType {
    TOOLMAKER(0x9C7A52),
    CARPENTER(0x7FA24A),
    METALWORKER(0xB8C2CC),
    POTTER(0xC9764A),
    MASON(0x9A9488),
    COPPERSMITH(0xD88A4E),
    /** Etabli de montage (age industriel) : machines, multiblocs et composants a partir de chassis, circuits et pieces, sans grille. */
    ASSEMBLY(0x55CCFF),
    /** 0.19.15 : etabli du redstoneur : composants de redstone (piston, distributeur, observateur, repeteur, comparateur...). */
    REDSTONER(0xD8453A),
    /** 0.19.15 : etabli du transporteur : wagonnets et rails, bateaux, selle et bardes de cheval, laisse. */
    TRANSPORTER(0x5E9BD0),
    /** 1.5.1 : etabli du luthier : instruments de musique, bloc musical, juke-box. */
    LUTHIER(0xC8945A),
    /** 1.6 : etabli agricole : postes de la ferme en bois, dispositifs (composteur, epouvantail, ruche...), peche, botte de foin. */
    AGRICULTURAL(0x8DB04A),
    /**
     * MAJ Recettes (lot 4b) : etabli du mouleur (fonderie de l'age industriel) : sable de fonderie, moules en sable et modeles en cire.
     */
    MOLDER(0xC8A46A),
    /**
     * MAJ Recettes (lot 10) : atelier de composants basiques (age industriel, MKI) : la voie « a la main » des formes de base (brame,
     * billette, plaque, tole, feuille, tige, fil, anneau, ressort, engrenage, tube, grillage, cable tresse), moins rentable que les machines.
     * 1.11.1 : « etabli de composants de base » (composants lies aux lingots) ; machine jumelle : le poste de faconnage.
     */
    COMPONENTS(0x8FA6B8),
    /**
     * 1.11.1 : etabli de composants avances : composants de la nomenclature de la refonte (chassis, moteurs, circuits, calculateurs...)
     * auparavant montes a l'etabli de montage ; machine jumelle : l'assembleur de composants.
     */
    ADVANCED_COMPONENTS(0x6FA8DC),
    /**
     * 1.11.1 : etabli de pieces logistiques : navires, rail, spatial (pieces de fusees, spatioport) et robotique ; machine jumelle :
     * l'assembleur logistique. Les nouveaux etablis se mettent toujours EN FIN : l'ordinal des etablis est envoye au client.
     */
    LOGISTICS(0xE0A040),
    /**
     * 1.12.0 (lot E4) : etabli du tanneur : les 80 pieces des tenues cosmetiques et les motifs de banniere ; il remplace le metier a tisser
     * vanilla (plus fabricable) : son bouton « Motifs » ouvre l'interface de motifs de banniere ({@link TannerLoomMenu}).
     */
    TANNER(0xB07A4A);

    public static final int TOOLS = 3, MATERIALS = 6;
    public final int accent;

    BenchType(int accent) {
        this.accent = accent;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Identifiant du bloc. */
    public String id() {
        return key() + "_bench";
    }

    public static BenchType byKey(String key) {
        for (BenchType type : values()) {
            if (type.key().equals(key)) {
                return type;
            }
        }
        throw new IllegalArgumentException("atelier inconnu : " + key);
    }
}
