package fr.pioneers.logistics.caravan.client;

/**
 * GENERE par tools/gc/caravan/caravan.py (1.0.4-beta, lot 10) : couches des chameaux du mod, memes parties et memes noms que CamelModel (les
 * animations vanilla s'appliquent) : chameau de Bactriane (DEUX bosses) et dromadaire (une bosse, corps 13 px, cou 6 px, jambes 4 px).
 * Boite : {u, v, x, y, z, w, h, d, deformation}. Robes re-ciblees face par face (entity/caravan/bactrian.png, dromedary.png, 128 x 128).
 * Ne pas modifier a la main.
 */
public final class GeneratedCamels {

    private GeneratedCamels() {
    }

    /** Partie : nom, parent ("" = racine), decalage, boites. */
    public record Part(String name, String parent, float x, float y, float z, float[][] boxes) {
    }

    public static final Part[] BACTRIAN = {
        new Part("body", "", 0F, 4F, 9.5F, new float[][]{{0, 25, -7.5F, -12F, -23.5F, 15F, 12F, 27F, 0F}}),
        new Part("hump", "body", 0F, -12F, -10F, new float[][]{{74, 0, -4F, -6F, -6F, 8F, 6F, 5F, 0F}, {74, 0, -4F, -6F, 1F, 8F, 6F, 5F, 0F}}),
        new Part("tail", "body", 0F, -9F, 3.5F, new float[][]{{122, 0, -1.5F, 0F, 0F, 3F, 14F, 0F, 0F}}),
        new Part("head", "body", 0F, -3F, -19.5F, new float[][]{{60, 24, -3.5F, -7F, -15F, 7F, 8F, 19F, 0F}, {21, 0, -3.5F, -21F, -15F, 7F, 14F, 7F, 0F}, {50, 0, -2.5F, -21F, -21F, 5F, 5F, 6F, 0F}}),
        new Part("left_ear", "head", 3F, -21F, -9.5F, new float[][]{{45, 0, -0.5F, 0.5F, -1F, 3F, 1F, 2F, 0F}}),
        new Part("right_ear", "head", -3F, -21F, -9.5F, new float[][]{{67, 0, -2.5F, 0.5F, -1F, 3F, 1F, 2F, 0F}}),
        new Part("left_hind_leg", "", 4.9F, 1F, 9.5F, new float[][]{{58, 16, -2.5F, 2F, -2.5F, 5F, 21F, 5F, 0F}}),
        new Part("right_hind_leg", "", -4.9F, 1F, 9.5F, new float[][]{{94, 16, -2.5F, 2F, -2.5F, 5F, 21F, 5F, 0F}}),
        new Part("left_front_leg", "", 4.9F, 1F, -10.5F, new float[][]{{0, 0, -2.5F, 2F, -2.5F, 5F, 21F, 5F, 0F}}),
        new Part("right_front_leg", "", -4.9F, 1F, -10.5F, new float[][]{{0, 26, -2.5F, 2F, -2.5F, 5F, 21F, 5F, 0F}}),
        new Part("saddle", "body", 0F, 0F, 0F, new float[][]{{74, 64, -4.5F, -16F, -11.5F, 9F, 4F, 3F, 0.1F}, {92, 114, -3.5F, -19F, -12.5F, 7F, 3F, 5F, 0.1F}, {0, 89, -7.5F, -12F, -23.5F, 15F, 12F, 27F, 0.1F}}),
        new Part("reins", "head", 0F, 0F, 0F, new float[][]{{98, 42, 3.51F, -18F, -17F, 0F, 7F, 15F, 0F}, {84, 57, -3.5F, -18F, -2F, 7F, 7F, 0F, 0F}, {98, 42, -3.51F, -18F, -17F, 0F, 7F, 15F, 0F}}),
        new Part("bridle", "head", 0F, 0F, 0F, new float[][]{{60, 87, -3.5F, -7F, -15F, 7F, 8F, 19F, 0.1F}, {21, 64, -3.5F, -21F, -15F, 7F, 14F, 7F, 0.1F}, {50, 64, -2.5F, -21F, -21F, 5F, 5F, 6F, 0.1F}, {74, 70, 2.5F, -19F, -18F, 1F, 2F, 2F, 0F}, {74, 70, -3.5F, -19F, -18F, 1F, 2F, 2F, 0F}})
    };

    public static final Part[] DROMEDARY = {
        new Part("body", "", 0F, 4F, 9.5F, new float[][]{{0, 25, -6.5F, -12F, -23.5F, 13F, 12F, 27F, 0F}}),
        new Part("hump", "body", 0F, -12F, -10F, new float[][]{{74, 0, -4F, -6F, -5.5F, 8F, 6F, 11F, 0F}}),
        new Part("tail", "body", 0F, -9F, 3.5F, new float[][]{{122, 0, -1.5F, 0F, 0F, 3F, 14F, 0F, 0F}}),
        new Part("head", "body", 0F, -3F, -19.5F, new float[][]{{60, 24, -3.5F, -7F, -15F, 7F, 8F, 19F, 0F}, {21, 0, -3F, -21F, -14.5F, 6F, 14F, 6F, 0F}, {50, 0, -2.5F, -21F, -21F, 5F, 5F, 6F, 0F}}),
        new Part("left_ear", "head", 3F, -21F, -9.5F, new float[][]{{45, 0, -0.5F, 0.5F, -1F, 3F, 1F, 2F, 0F}}),
        new Part("right_ear", "head", -3F, -21F, -9.5F, new float[][]{{67, 0, -2.5F, 0.5F, -1F, 3F, 1F, 2F, 0F}}),
        new Part("left_hind_leg", "", 4.4F, 1F, 9.5F, new float[][]{{58, 16, -2F, 2F, -2F, 4F, 21F, 4F, 0F}}),
        new Part("right_hind_leg", "", -4.4F, 1F, 9.5F, new float[][]{{94, 16, -2F, 2F, -2F, 4F, 21F, 4F, 0F}}),
        new Part("left_front_leg", "", 4.4F, 1F, -10.5F, new float[][]{{0, 0, -2F, 2F, -2F, 4F, 21F, 4F, 0F}}),
        new Part("right_front_leg", "", -4.4F, 1F, -10.5F, new float[][]{{0, 26, -2F, 2F, -2F, 4F, 21F, 4F, 0F}}),
        new Part("saddle", "body", 0F, 0F, 0F, new float[][]{{74, 64, -4F, -18F, -15.5F, 8F, 6F, 11F, 0.1F}, {92, 114, -3.5F, -21F, -15.5F, 7F, 3F, 11F, 0.1F}, {0, 89, -6.5F, -12F, -23.5F, 13F, 12F, 27F, 0.1F}}),
        new Part("reins", "head", 0F, 0F, 0F, new float[][]{{98, 42, 3.51F, -18F, -17F, 0F, 7F, 15F, 0F}, {84, 57, -3.5F, -18F, -2F, 7F, 7F, 0F, 0F}, {98, 42, -3.51F, -18F, -17F, 0F, 7F, 15F, 0F}}),
        new Part("bridle", "head", 0F, 0F, 0F, new float[][]{{60, 87, -3.5F, -7F, -15F, 7F, 8F, 19F, 0.1F}, {21, 64, -3F, -21F, -14.5F, 6F, 14F, 6F, 0.1F}, {50, 64, -2.5F, -21F, -21F, 5F, 5F, 6F, 0.1F}, {74, 70, 2.5F, -19F, -18F, 1F, 2F, 2F, 0F}, {74, 70, -3.5F, -19F, -18F, 1F, 2F, 2F, 0F}})
    };
}
