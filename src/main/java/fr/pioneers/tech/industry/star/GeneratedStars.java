package fr.pioneers.tech.industry.star;

/**
 * GENERE par T:au_dela/multiblocs.py stars_java() (ne pas modifier a la main) : les multiblocs extraordinaires (1.0.7, lot 12) : sorte du
 * rendu anime, centre de l'effet (cases du gabarit sculpte), taille, couleur, parts de la duree de chaque etape.
 */
public final class GeneratedStars {

    private GeneratedStars() {
    }

    /** Un extraordinaire : sorte du rendu (index de {@code RENDERS}), centre (x, y, z), taille, couleur, parts des etapes. */
    public record Star(String key, int render, float fx, float fy, float fz, float scale, int accent, float[] stages) {
    }

    public static final Star[] ALL = {
        new Star("heliothermal_plasma_forge", 0, 11.5F, 21.5F, 11.5F, 2.6F, 0xFFAA46, new float[]{0.2F, 0.5F, 0.3F}),
        new Star("diamond_anvil_press", 1, 10.5F, 9.5F, 7.5F, 1.6F, 0xC8E6FF, new float[]{0.25F, 0.5F, 0.25F}),
        new Star("annular_accelerator", 2, 13.5F, 3.5F, 13.5F, 11.0F, 0x78C8FF, new float[]{0.2F, 0.5F, 0.3F}),
        new Star("qubit_foundry", 3, 7.5F, 5.0F, 7.5F, 1.4F, 0x96DCFF, new float[]{0.35F, 0.4F, 0.25F}),
        new Star("aneutronic_fusion_core", 4, 10.5F, 6.5F, 10.5F, 6.0F, 0xFFDC8C, new float[]{0.2F, 0.55F, 0.25F}),
        new Star("dark_matter_confiner", 5, 10.5F, 10.5F, 10.5F, 5.4F, 0xAA82FF, new float[]{0.25F, 0.45F, 0.3F}),
        new Star("degenerate_matter_forge", 6, 10.5F, 6.0F, 7.5F, 2.2F, 0x8CD2FF, new float[]{0.2F, 0.5F, 0.3F}),
        new Star("singularity_injector", 7, 11.5F, 16.5F, 11.5F, 3.6F, 0xB478FF, new float[]{0.2F, 0.5F, 0.3F})
    };
}
