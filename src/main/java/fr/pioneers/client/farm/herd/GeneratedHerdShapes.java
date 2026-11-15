package fr.pioneers.client.farm.herd;

/**
 * GENERE par tools/gc/farm2/herd.py (T:farm2/herd_shapes.py, 1.0.4-beta lot 33) : SILHOUETTES des races de chevaux et de lamas.
 * Cheval : proportions appliquees par {@link HerdShapes} apres l'animation vanilla (jambes : longueur, epaisseur ; corps : largeur, hauteur,
 * longueur ; encolure : epaisseur, longueur, port (rad) ; tete ; queue : epaisseur, longueur, port (rad)) et crins en plus (texture
 * {@code <race>_hair.png} 64 x 64) : {partie, u, v, x, y, z, w, h, d, gonflement}, parties = {@link #HAIR_PARTS}.
 * Lama : modele complet d'une race (memes noms que LlamaModel), remontee des pattes, boites du corps et de l'encolure (repere de leur partie,
 * x0 y0 z0 x1 y1 z1 : ajustement du tapis), cote et longueur des pattes, demi-largeur du corps ; boite : {u, v, x, y, z, w, h, d, gonflement}.
 * Ne pas modifier a la main.
 */
public final class GeneratedHerdShapes {

    private GeneratedHerdShapes() {
    }

    public static final String[] HAIR_PARTS = {"mane", "head", "tail", "left_front_leg", "right_front_leg", "left_hind_leg", "right_hind_leg"};

    public record HorseShape(String id, float legLen, float legThick, float bodyW, float bodyH, float bodyL, float neckThick, float neckLen,
                             float neckTilt, float head, float tailThick, float tailLen, float tailLift, float[][] hair) {
    }

    public static final HorseShape[] HORSES = {
        new HorseShape("percheron", 0.92F, 1.3F, 1.22F, 1.14F, 1.06F, 1.25F, 0.9F, 0.0698F, 1.12F, 1.25F, 0.95F, 0F, new float[][]{{0, 18, 0, -1.5F, -10.5F, 4.6F, 3, 15, 3, 0.15F}, {1, 44, 20, -1.5F, -11.7F, -4.6F, 3, 1, 5, 0F}, {2, 0, 0, -2F, 1F, -0.5F, 4, 15, 5, 0F}, {3, 30, 0, -3.6F, 6.9F, -2.5F, 5, 3, 6, 0.05F}, {4, 0, 20, -1.6F, 6.9F, -2.5F, 5, 3, 6, 0.05F}, {5, 22, 20, -3.6F, 6.9F, -1.6F, 5, 3, 6, 0.05F}, {6, 0, 29, -1.6F, 6.9F, -1.6F, 5, 3, 6, 0.05F}}),
        new HorseShape("boulonnais", 0.95F, 1.2F, 1.15F, 1.1F, 1F, 1.18F, 0.96F, 0F, 1.02F, 1.15F, 1F, 0.0698F, new float[][]{{0, 18, 0, -1.5F, -10.5F, 4.6F, 3, 15, 3, 0.15F}, {1, 44, 20, -1.5F, -11.7F, -4.6F, 3, 1, 5, 0F}, {2, 0, 0, -2F, 1F, -0.5F, 4, 15, 5, 0F}, {3, 30, 0, -3.6F, 6.9F, -2.5F, 5, 3, 6, 0.05F}, {4, 0, 20, -1.6F, 6.9F, -2.5F, 5, 3, 6, 0.05F}, {5, 22, 20, -3.6F, 6.9F, -1.6F, 5, 3, 6, 0.05F}, {6, 0, 29, -1.6F, 6.9F, -1.6F, 5, 3, 6, 0.05F}}),
        new HorseShape("thoroughbred", 1.18F, 0.86F, 0.88F, 0.94F, 1.08F, 0.88F, 1.12F, -0.0698F, 0.96F, 0.85F, 1F, 0.1047F, new float[][]{}),
        new HorseShape("arabian", 1.1F, 0.84F, 0.9F, 0.94F, 0.9F, 0.9F, 1.06F, -0.2094F, 0.86F, 0.85F, 0.95F, 0.4538F, new float[][]{{1, 0, 0, -1.5F, -11.7F, -4.6F, 3, 1, 5, 0F}}),
        new HorseShape("merens", 0.9F, 1.12F, 1.1F, 1.06F, 0.95F, 1.12F, 0.9F, 0.0349F, 1F, 1.2F, 1.05F, 0F, new float[][]{{0, 32, 0, -1.5F, -10.6F, 4.6F, 3, 15, 3, 0.1F}, {0, 18, 0, 2F, -10F, 1F, 1, 13, 6, 0F}, {1, 44, 0, -1.5F, -11.7F, -5.6F, 3, 1, 6, 0F}, {2, 0, 0, -2F, 1F, -0.5F, 4, 16, 5, 0F}, {3, 0, 21, -3.6F, 6.9F, -2.5F, 5, 3, 6, 0.05F}, {4, 22, 21, -1.6F, 6.9F, -2.5F, 5, 3, 6, 0.05F}, {5, 0, 30, -3.6F, 6.9F, -1.6F, 5, 3, 6, 0.05F}, {6, 22, 30, -1.6F, 6.9F, -1.6F, 5, 3, 6, 0.05F}}),
        new HorseShape("camargue", 0.94F, 1.04F, 1.02F, 1F, 0.94F, 1.06F, 0.9F, 0.0698F, 1.06F, 1.1F, 1F, 0.0349F, new float[][]{{0, 32, 0, -1.5F, -10.6F, 4.6F, 3, 15, 3, 0.1F}, {0, 18, 0, 2F, -10F, 1F, 1, 13, 6, 0F}, {1, 44, 0, -1.5F, -11.7F, -4.6F, 3, 1, 5, 0F}, {2, 0, 0, -2F, 1F, -0.5F, 4, 15, 5, 0F}}),
        new HorseShape("friesian", 1.04F, 1.06F, 1F, 1.02F, 1F, 1.04F, 1.14F, -0.2443F, 1F, 1.15F, 1.02F, 0.1396F, new float[][]{{0, 46, 0, -1.5F, -10.6F, 4.6F, 3, 15, 3, 0.1F}, {0, 18, 0, 2F, -10F, 1F, 1, 13, 6, 0F}, {0, 32, 0, -3F, -10F, 1F, 1, 13, 6, 0F}, {1, 44, 21, -1.5F, -11.7F, -5.6F, 3, 1, 6, 0F}, {2, 0, 0, -2F, 1F, -0.5F, 4, 16, 5, 0F}, {3, 0, 21, -3.6F, 4.6F, -2.5F, 5, 5, 6, 0.25F}, {4, 22, 21, -1.6F, 4.6F, -2.5F, 5, 5, 6, 0.25F}, {5, 0, 32, -3.6F, 4.6F, -1.6F, 5, 5, 6, 0.25F}, {6, 22, 32, -1.6F, 4.6F, -1.6F, 5, 5, 6, 0.25F}}),
        new HorseShape("pony", 0.8F, 1.1F, 1.08F, 1.08F, 0.88F, 1.12F, 0.84F, 0.1047F, 1.12F, 1.2F, 0.9F, 0F, new float[][]{{0, 18, 0, -1.5F, -10.5F, 4.6F, 3, 15, 3, 0.15F}, {1, 44, 20, -1.5F, -11.7F, -5.6F, 3, 1, 6, 0F}, {2, 0, 0, -2F, 1F, -0.5F, 4, 15, 5, 0F}, {3, 30, 0, -3.6F, 6.9F, -2.5F, 5, 3, 6, 0.05F}, {4, 0, 20, -1.6F, 6.9F, -2.5F, 5, 3, 6, 0.05F}, {5, 22, 20, -3.6F, 6.9F, -1.6F, 5, 3, 6, 0.05F}, {6, 0, 29, -1.6F, 6.9F, -1.6F, 5, 3, 6, 0.05F}})
    };

    /** Partie d'un modele : nom, parent ("" = racine), pivot, rotation (rad), boites. */
    public record Part(String name, String parent, float x, float y, float z, float xRot, float yRot, float zRot, float[][] cubes) {
    }

    public record LlamaShape(String id, float lift, float[] body, float[] neck, float legW, float legLen, float halfWidth, Part[] parts) {
    }

    public static final LlamaShape[] LLAMAS = {
        new LlamaShape("ccara_llama", 2F, new float[]{-5.5F, -10F, -6F, 5.5F, 9F, 3F}, new float[]{-3F, -17F, -5F, 3F, 2F, 0F}, 3F, 16F, 5.5F, new Part[]{
            new Part("head", "", 0F, 5F, -6F, 0F, 0F, 0F, new float[][]{{74, 28, -3F, -17F, -5F, 6, 19, 5, 0F}, {96, 28, -2F, -15F, -8F, 4, 4, 8, 0F}}),
            new Part("right_ear", "head", -1.8F, -16.5F, -2.5F, 0F, 0F, -0.1745F, new float[][]{{120, 28, -1F, -4F, -0.5F, 2, 4, 1, 0F}}),
            new Part("right_ear_tip", "right_ear", 0F, -3.7F, 0F, -0.1396F, 0F, 0.4538F, new float[][]{{74, 52, -1F, -2.6F, -0.5F, 2, 3, 1, -0.05F}}),
            new Part("left_ear", "head", 1.8F, -16.5F, -2.5F, 0F, 0F, 0.1745F, new float[][]{{120, 28, -1F, -4F, -0.5F, 2, 4, 1, 0F}}),
            new Part("left_ear_tip", "left_ear", 0F, -3.7F, 0F, -0.1396F, 0F, -0.4538F, new float[][]{{74, 52, -1F, -2.6F, -0.5F, 2, 3, 1, -0.05F}}),
            new Part("body", "", 0F, 3F, 2F, 1.5708F, 0F, 0F, new float[][]{{74, 0, -5.5F, -10F, -6F, 11, 19, 9, 0F}}),
            new Part("right_chest", "", -8F, 1F, 3F, 0F, 1.5708F, 0F, new float[][]{{45, 28, -3F, 0F, 0F, 8, 8, 3, 0F}}),
            new Part("left_chest", "", 5F, 1F, 3F, 0F, 1.5708F, 0F, new float[][]{{45, 41, -3F, 0F, 0F, 8, 8, 3, 0F}}),
            new Part("right_hind_leg", "", -3.5F, 8F, 6F, 0F, 0F, 0F, new float[][]{{114, 0, -1.5F, 0F, -1.5F, 3, 16, 3, 0F}}),
            new Part("left_hind_leg", "", 3.5F, 8F, 6F, 0F, 0F, 0F, new float[][]{{114, 0, -1.5F, 0F, -1.5F, 3, 16, 3, 0F}}),
            new Part("right_front_leg", "", -3.5F, 8F, -5F, 0F, 0F, 0F, new float[][]{{114, 0, -1.5F, 0F, -1.5F, 3, 16, 3, 0F}}),
            new Part("left_front_leg", "", 3.5F, 8F, -5F, 0F, 0F, 0F, new float[][]{{114, 0, -1.5F, 0F, -1.5F, 3, 16, 3, 0F}})
        }),
        new LlamaShape("tampuli_llama", 0F, new float[]{-7F, -10F, -9F, 7F, 9F, 3F}, new float[]{-5F, -15F, -8F, 5F, 2F, 0F}, 4F, 14F, 7F, new Part[]{
            new Part("head", "", 0F, 7F, -6F, 0F, 0F, 0F, new float[][]{{74, 31, -5F, -15F, -8F, 10, 17, 8, 0F}, {24, 52, -2F, -13F, -10F, 4, 4, 7, 0F}, {46, 52, -4F, -16.6F, -7F, 8, 2, 6, 0F}}),
            new Part("right_ear", "head", -3.8F, -14.5F, -2.5F, 0F, 0F, -0.1745F, new float[][]{{116, 31, -1F, -2F, -0.5F, 2, 2, 1, 0F}}),
            new Part("right_ear_tip", "right_ear", 0F, -1.7F, 0F, -0.1396F, 0F, 0.3491F, new float[][]{{110, 31, -1F, -2.6F, -0.5F, 2, 3, 1, -0.05F}}),
            new Part("left_ear", "head", 3.8F, -14.5F, -2.5F, 0F, 0F, 0.1745F, new float[][]{{116, 31, -1F, -2F, -0.5F, 2, 2, 1, 0F}}),
            new Part("left_ear_tip", "left_ear", 0F, -1.7F, 0F, -0.1396F, 0F, -0.3491F, new float[][]{{110, 31, -1F, -2.6F, -0.5F, 2, 3, 1, -0.05F}}),
            new Part("body", "", 0F, 5F, 2F, 1.5708F, 0F, 0F, new float[][]{{74, 0, -7F, -10F, -9F, 14, 19, 12, 0F}}),
            new Part("right_chest", "", -9.5F, 3F, 3F, 0F, 1.5708F, 0F, new float[][]{{45, 28, -3F, 0F, 0F, 8, 8, 3, 0F}}),
            new Part("left_chest", "", 6.5F, 3F, 3F, 0F, 1.5708F, 0F, new float[][]{{45, 41, -3F, 0F, 0F, 8, 8, 3, 0F}}),
            new Part("right_hind_leg", "", -3.5F, 10F, 6F, 0F, 0F, 0F, new float[][]{{29, 29, -2F, 0F, -2F, 4, 14, 4, 0F}, {0, 52, -3F, -1F, -3F, 6, 6, 6, 0F}}),
            new Part("left_hind_leg", "", 3.5F, 10F, 6F, 0F, 0F, 0F, new float[][]{{29, 29, -2F, 0F, -2F, 4, 14, 4, 0F}, {0, 52, -3F, -1F, -3F, 6, 6, 6, 0F}}),
            new Part("right_front_leg", "", -3.5F, 10F, -5F, 0F, 0F, 0F, new float[][]{{29, 29, -2F, 0F, -2F, 4, 14, 4, 0F}, {0, 52, -3F, -1F, -3F, 6, 6, 6, 0F}}),
            new Part("left_front_leg", "", 3.5F, 10F, -5F, 0F, 0F, 0F, new float[][]{{29, 29, -2F, 0F, -2F, 4, 14, 4, 0F}, {0, 52, -3F, -1F, -3F, 6, 6, 6, 0F}})
        }),
        new LlamaShape("suri_llama", 0F, new float[]{-6.5F, -10F, -8F, 6.5F, 8F, 3F}, new float[]{-4F, -16F, -7F, 4F, 2F, 0F}, 4F, 14F, 6.5F, new Part[]{
            new Part("head", "", 0F, 7F, -6F, 0F, 0F, 0F, new float[][]{{74, 29, -4F, -16F, -7F, 8, 18, 7, 0F}, {0, 52, -2F, -14F, -10F, 4, 4, 8, 0F}, {48, 52, -3F, -17.6F, -6F, 6, 2, 5, 0F}}),
            new Part("right_ear", "head", -2.8F, -15.5F, -2.5F, 0F, 0F, -0.1745F, new float[][]{{122, 0, -1F, -3F, -0.5F, 2, 3, 1, 0F}}),
            new Part("right_ear_tip", "right_ear", 0F, -2.7F, 0F, -0.1396F, 0F, 0.4189F, new float[][]{{118, 29, -1F, -2.6F, -0.5F, 2, 3, 1, -0.05F}}),
            new Part("left_ear", "head", 2.8F, -15.5F, -2.5F, 0F, 0F, 0.1745F, new float[][]{{122, 0, -1F, -3F, -0.5F, 2, 3, 1, 0F}}),
            new Part("left_ear_tip", "left_ear", 0F, -2.7F, 0F, -0.1396F, 0F, -0.4189F, new float[][]{{118, 29, -1F, -2.6F, -0.5F, 2, 3, 1, -0.05F}}),
            new Part("body", "", 0F, 5F, 2F, 1.5708F, 0F, 0F, new float[][]{{74, 0, -6.5F, -10F, -8F, 13, 18, 11, 0F}}),
            new Part("fringe_right", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{104, 29, -7.1F, -9F, -11F, 1, 16, 6, 0F}}),
            new Part("fringe_left", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{104, 29, 6.1F, -9F, -11F, 1, 16, 6, 0F}}),
            new Part("right_chest", "", -9F, 3F, 3F, 0F, 1.5708F, 0F, new float[][]{{45, 28, -3F, 0F, 0F, 8, 8, 3, 0F}}),
            new Part("left_chest", "", 6F, 3F, 3F, 0F, 1.5708F, 0F, new float[][]{{45, 41, -3F, 0F, 0F, 8, 8, 3, 0F}}),
            new Part("right_hind_leg", "", -3.5F, 10F, 6F, 0F, 0F, 0F, new float[][]{{29, 29, -2F, 0F, -2F, 4, 14, 4, 0F}, {24, 52, -3F, -1F, -3F, 6, 6, 6, 0F}}),
            new Part("left_hind_leg", "", 3.5F, 10F, 6F, 0F, 0F, 0F, new float[][]{{29, 29, -2F, 0F, -2F, 4, 14, 4, 0F}, {24, 52, -3F, -1F, -3F, 6, 6, 6, 0F}}),
            new Part("right_front_leg", "", -3.5F, 10F, -5F, 0F, 0F, 0F, new float[][]{{29, 29, -2F, 0F, -2F, 4, 14, 4, 0F}, {24, 52, -3F, -1F, -3F, 6, 6, 6, 0F}}),
            new Part("left_front_leg", "", 3.5F, 10F, -5F, 0F, 0F, 0F, new float[][]{{29, 29, -2F, 0F, -2F, 4, 14, 4, 0F}, {24, 52, -3F, -1F, -3F, 6, 6, 6, 0F}})
        }),
        new LlamaShape("chaku_llama", 0F, new float[]{-6.5F, -10F, -8F, 6.5F, 8F, 3F}, new float[]{-4F, -16F, -7F, 4F, 2F, 0F}, 4F, 14F, 6.5F, new Part[]{
            new Part("head", "", 0F, 7F, -6F, 0F, 0F, 0F, new float[][]{{74, 29, -4F, -16F, -7F, 8, 18, 7, 0F}, {104, 29, -2F, -14F, -10F, 4, 4, 8, 0F}}),
            new Part("right_ear", "head", -2.8F, -15.5F, -2.5F, 0F, 0F, -0.1745F, new float[][]{{122, 0, -1F, -3F, -0.5F, 2, 3, 1, 0F}}),
            new Part("right_ear_tip", "right_ear", 0F, -2.7F, 0F, -0.1396F, 0F, 0.4189F, new float[][]{{98, 54, -1F, -2.6F, -0.5F, 2, 3, 1, -0.05F}}),
            new Part("left_ear", "head", 2.8F, -15.5F, -2.5F, 0F, 0F, 0.1745F, new float[][]{{122, 0, -1F, -3F, -0.5F, 2, 3, 1, 0F}}),
            new Part("left_ear_tip", "left_ear", 0F, -2.7F, 0F, -0.1396F, 0F, -0.4189F, new float[][]{{98, 54, -1F, -2.6F, -0.5F, 2, 3, 1, -0.05F}}),
            new Part("body", "", 0F, 5F, 2F, 1.5708F, 0F, 0F, new float[][]{{74, 0, -6.5F, -10F, -8F, 13, 18, 11, 0F}}),
            new Part("right_chest", "", -9F, 3F, 3F, 0F, 1.5708F, 0F, new float[][]{{45, 28, -3F, 0F, 0F, 8, 8, 3, 0F}}),
            new Part("left_chest", "", 6F, 3F, 3F, 0F, 1.5708F, 0F, new float[][]{{45, 41, -3F, 0F, 0F, 8, 8, 3, 0F}}),
            new Part("right_hind_leg", "", -3.5F, 10F, 6F, 0F, 0F, 0F, new float[][]{{29, 29, -2F, 0F, -2F, 4, 14, 4, 0F}, {74, 54, -3F, -1F, -3F, 6, 4, 6, 0F}}),
            new Part("left_hind_leg", "", 3.5F, 10F, 6F, 0F, 0F, 0F, new float[][]{{29, 29, -2F, 0F, -2F, 4, 14, 4, 0F}, {74, 54, -3F, -1F, -3F, 6, 4, 6, 0F}}),
            new Part("right_front_leg", "", -3.5F, 10F, -5F, 0F, 0F, 0F, new float[][]{{29, 29, -2F, 0F, -2F, 4, 14, 4, 0F}, {74, 54, -3F, -1F, -3F, 6, 4, 6, 0F}}),
            new Part("left_front_leg", "", 3.5F, 10F, -5F, 0F, 0F, 0F, new float[][]{{29, 29, -2F, 0F, -2F, 4, 14, 4, 0F}, {74, 54, -3F, -1F, -3F, 6, 4, 6, 0F}})
        })
    };
}
