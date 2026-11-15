package fr.pioneers.world.wild.client;

/**
 * GENERE par tools/gc/wild/wild7.py (1.0.5-beta, lot 7) : SILHOUETTES des predateurs et de la petite faune (memes fiches que
 * {@link GeneratedWildShapes}) : loup, ours, rat (quadrupedes) ; pic-vert, pigeon (oiseaux : ailes a l'epaule, queue, deux pattes) ;
 * serpent (tete, corps et anneaux en chaine, aucune patte). Sorte : 5 loup, 6 ours, 7 pic-vert, 8 rat, 9 pigeon, 10 serpent.
 * Ne pas modifier a la main.
 */
public final class GeneratedWild7Shapes {

    private GeneratedWild7Shapes() {
    }

    public static final GeneratedWildShapes.Shape[] SHAPES = {
        new GeneratedWildShapes.Shape("gray_wolf", 0, 5, 64, 64, 8F, 2F, 4, 6F, 7F, 13F, 5F, 24F, new GeneratedWildShapes.Part[]{
            new GeneratedWildShapes.Part("body", "", 0F, 16F, 5F, 0F, 0F, 0F, new float[][]{{0, 0, -3F, -7F, -11.5F, 6F, 7F, 13F, 0F, 0}}),
            new GeneratedWildShapes.Part("hump", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{24, 20, -4F, -9F, -12F, 8F, 3F, 6F, 0.2F, 0}}),
            new GeneratedWildShapes.Part("right_front_leg", "body", -1.95F, -1F, -9.5F, 0F, 0F, 0F, new float[][]{{0, 20, -1F, 0F, -1F, 2F, 9F, 2F, 0F, 0}}),
            new GeneratedWildShapes.Part("left_front_leg", "body", 1.95F, -1F, -9.5F, 0F, 0F, 0F, new float[][]{{0, 20, -1F, 0F, -1F, 2F, 9F, 2F, 0F, 1}}),
            new GeneratedWildShapes.Part("right_hind_leg", "body", -1.95F, -1F, 0F, 0F, 0F, 0F, new float[][]{{8, 20, -1F, 0F, -1F, 2F, 9F, 2F, 0F, 0}}),
            new GeneratedWildShapes.Part("left_hind_leg", "body", 1.95F, -1F, 0F, 0F, 0F, 0F, new float[][]{{8, 20, -1F, 0F, -1F, 2F, 9F, 2F, 0F, 1}}),
            new GeneratedWildShapes.Part("head", "body", 0F, -6F, -11.5F, 0F, 0F, 0F, new float[][]{{38, 0, -3F, -3F, -5F, 6F, 6F, 5F, 0F, 0}}),
            new GeneratedWildShapes.Part("snout", "head", 0F, 3F, -5F, 0F, 0F, 0F, new float[][]{{0, 31, -1.5F, -3F, -4F, 3F, 3F, 4F, 0F, 0}}),
            new GeneratedWildShapes.Part("right_ear", "head", -1.75F, -2.5F, -1.5F, -0.0698F, 0F, -0.1396F, new float[][]{{14, 31, -1F, -3F, -0.5F, 2F, 3F, 1F, 0F, 0}}),
            new GeneratedWildShapes.Part("left_ear", "head", 1.75F, -2.5F, -1.5F, -0.0698F, 0F, 0.1396F, new float[][]{{14, 31, -1F, -3F, -0.5F, 2F, 3F, 1F, 0F, 1}}),
            new GeneratedWildShapes.Part("tail", "body", 0F, -6F, 1F, 0.5585F, 0F, 0F, new float[][]{{16, 20, -1F, 0F, -1F, 2F, 9F, 2F, 0F, 0}})}),
        new GeneratedWildShapes.Shape("bear", 0, 6, 64, 64, 7F, 4F, 4, 12F, 11F, 18F, 7.5F, 24F, new GeneratedWildShapes.Part[]{
            new GeneratedWildShapes.Part("body", "", 0F, 17F, 7.5F, 0F, 0F, 0F, new float[][]{{0, 0, -6F, -11F, -16.5F, 12F, 11F, 18F, 0F, 0}}),
            new GeneratedWildShapes.Part("hump", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{0, 43, -5.5F, -13F, -17F, 11F, 3F, 7F, 0.2F, 0}}),
            new GeneratedWildShapes.Part("right_front_leg", "body", -3.95F, -1F, -13.5F, 0F, 0F, 0F, new float[][]{{30, 29, -2F, 0F, -2F, 4F, 8F, 4F, 0F, 0}}),
            new GeneratedWildShapes.Part("left_front_leg", "body", 3.95F, -1F, -13.5F, 0F, 0F, 0F, new float[][]{{30, 29, -2F, 0F, -2F, 4F, 8F, 4F, 0F, 1}}),
            new GeneratedWildShapes.Part("right_hind_leg", "body", -3.95F, -1F, -1F, 0F, 0F, 0F, new float[][]{{46, 29, -2F, 0F, -2F, 4F, 8F, 4F, 0F, 0}}),
            new GeneratedWildShapes.Part("left_hind_leg", "body", 3.95F, -1F, -1F, 0F, 0F, 0F, new float[][]{{46, 29, -2F, 0F, -2F, 4F, 8F, 4F, 0F, 1}}),
            new GeneratedWildShapes.Part("head", "body", 0F, -7F, -16.5F, 0F, 0F, 0F, new float[][]{{0, 29, -4F, -3.5F, -7F, 8F, 7F, 7F, 0F, 0}}),
            new GeneratedWildShapes.Part("snout", "head", 0F, 3.5F, -7F, 0F, 0F, 0F, new float[][]{{36, 43, -2F, -3F, -4F, 4F, 3F, 4F, 0F, 0}}),
            new GeneratedWildShapes.Part("right_ear", "head", -2.75F, -3F, -1.5F, 0F, 0F, -0.4363F, new float[][]{{0, 53, -1F, -2F, -0.5F, 2F, 2F, 1F, 0F, 0}}),
            new GeneratedWildShapes.Part("left_ear", "head", 2.75F, -3F, -1.5F, 0F, 0F, 0.4363F, new float[][]{{0, 53, -1F, -2F, -0.5F, 2F, 2F, 1F, 0F, 1}}),
            new GeneratedWildShapes.Part("tail", "body", 0F, -10F, 1F, 0.6981F, 0F, 0F, new float[][]{{52, 43, -1F, 0F, -1F, 2F, 2F, 2F, 0F, 0}})}),
        new GeneratedWildShapes.Shape("woodpecker", 0, 7, 64, 64, 3F, 1F, 2, 4F, 5F, 6F, 0.5F, 24F, new GeneratedWildShapes.Part[]{
            new GeneratedWildShapes.Part("body", "", 0F, 21F, 0.5F, 0F, 0F, 0F, new float[][]{{0, 0, -2F, -5F, -3.5F, 4F, 5F, 6F, 0F, 0}}),
            new GeneratedWildShapes.Part("chest", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{0, 11, -2F, -4F, -4.5F, 4F, 4F, 2F, 0.03F, 0}}),
            new GeneratedWildShapes.Part("right_leg", "body", -0.8F, -1F, 0F, 0F, 0F, 0F, new float[][]{{12, 11, -0.5F, 0F, -0.5F, 1F, 4F, 1F, 0F, 0}, {24, 11, -1F, 3F, -1.5F, 2F, 1F, 2F, 0.02F, 0}}),
            new GeneratedWildShapes.Part("left_leg", "body", 0.8F, -1F, 0F, 0F, 0F, 0F, new float[][]{{12, 11, -0.5F, 0F, -0.5F, 1F, 4F, 1F, 0F, 1}, {24, 11, -1F, 3F, -1.5F, 2F, 1F, 2F, 0.05F, 1}}),
            new GeneratedWildShapes.Part("head", "body", 0F, -4F, -2.5F, 0F, 0F, 0F, new float[][]{{50, 0, -1.5F, -3F, -3F, 3F, 3F, 4F, 0F, 0}}),
            new GeneratedWildShapes.Part("snout", "head", 0F, -1F, -3F, 0F, 0F, 0F, new float[][]{{16, 11, -0.5F, -0.5F, -3F, 1F, 1F, 3F, 0F, 0}}),
            new GeneratedWildShapes.Part("right_wing", "body", -2.05F, -4.5F, -3F, 0F, 0F, 0F, new float[][]{{20, 0, -0.5F, 0F, 0F, 1F, 5F, 6F, 0F, 0}}),
            new GeneratedWildShapes.Part("left_wing", "body", 2.05F, -4.5F, -3F, 0F, 0F, 0F, new float[][]{{20, 0, -0.5F, 0F, 0F, 1F, 5F, 6F, 0F, 1}}),
            new GeneratedWildShapes.Part("tail", "body", 0F, -4F, 1.5F, -0.6981F, 0F, 0F, new float[][]{{34, 0, -1F, -0.5F, 0F, 2F, 1F, 6F, 0F, 0}})}),
        new GeneratedWildShapes.Shape("rat", 0, 8, 64, 64, 3F, 1F, 4, 4F, 4F, 8F, 2.5F, 24F, new GeneratedWildShapes.Part[]{
            new GeneratedWildShapes.Part("body", "", 0F, 21F, 2.5F, 0F, 0F, 0F, new float[][]{{4, 0, -2F, -4F, -6.5F, 4F, 4F, 8F, 0F, 0}}),
            new GeneratedWildShapes.Part("right_front_leg", "body", -1.45F, -1F, -5F, 0F, 0F, 0F, new float[][]{{54, 0, -0.5F, 0F, -0.5F, 1F, 4F, 1F, 0F, 0}}),
            new GeneratedWildShapes.Part("left_front_leg", "body", 1.45F, -1F, -5F, 0F, 0F, 0F, new float[][]{{54, 0, -0.5F, 0F, -0.5F, 1F, 4F, 1F, 0F, 1}}),
            new GeneratedWildShapes.Part("right_hind_leg", "body", -1.45F, -1F, 0.5F, 0F, 0F, 0F, new float[][]{{58, 0, -0.5F, 0F, -0.5F, 1F, 4F, 1F, 0F, 0}}),
            new GeneratedWildShapes.Part("left_hind_leg", "body", 1.45F, -1F, 0.5F, 0F, 0F, 0F, new float[][]{{58, 0, -0.5F, 0F, -0.5F, 1F, 4F, 1F, 0F, 1}}),
            new GeneratedWildShapes.Part("head", "body", 0F, -2F, -5.5F, 0F, 0F, 0F, new float[][]{{28, 0, -2F, -1.5F, -4F, 4F, 3F, 4F, 0.03F, 0}}),
            new GeneratedWildShapes.Part("snout", "head", 0F, 1.5F, -4F, 0F, 0F, 0F, new float[][]{{44, 0, -1F, -2F, -3F, 2F, 2F, 3F, 0F, 0}}),
            new GeneratedWildShapes.Part("right_ear", "head", -1.75F, -1F, -1.5F, -0.0873F, 0F, -0.5236F, new float[][]{{0, 13, -1F, -2F, -0.5F, 2F, 2F, 1F, 0F, 0}}),
            new GeneratedWildShapes.Part("left_ear", "head", 1.75F, -1F, -1.5F, -0.0873F, 0F, 0.5236F, new float[][]{{0, 13, -1F, -2F, -0.5F, 2F, 2F, 1F, 0F, 1}}),
            new GeneratedWildShapes.Part("tail", "body", 0F, -3F, 1F, 1.0821F, 0F, 0F, new float[][]{{0, 0, -0.5F, 0F, -0.5F, 1F, 12F, 1F, 0F, 0}})}),
        new GeneratedWildShapes.Shape("pigeon", 0, 9, 64, 64, 3F, 1F, 2, 5F, 5F, 7F, 0.5F, 24F, new GeneratedWildShapes.Part[]{
            new GeneratedWildShapes.Part("body", "", 0F, 21F, 0.5F, 0F, 0F, 0F, new float[][]{{0, 0, -2.5F, -5F, -4F, 5F, 5F, 7F, 0F, 0}}),
            new GeneratedWildShapes.Part("chest", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{38, 0, -2.5F, -4F, -5F, 5F, 4F, 2F, 0.03F, 0}}),
            new GeneratedWildShapes.Part("right_leg", "body", -1.3F, -1F, 0F, 0F, 0F, 0F, new float[][]{{26, 12, -0.5F, 0F, -0.5F, 1F, 4F, 1F, 0F, 0}, {30, 12, -1F, 3F, -1.5F, 2F, 1F, 2F, 0.02F, 0}}),
            new GeneratedWildShapes.Part("left_leg", "body", 1.3F, -1F, 0F, 0F, 0F, 0F, new float[][]{{26, 12, -0.5F, 0F, -0.5F, 1F, 4F, 1F, 0F, 1}, {30, 12, -1F, 3F, -1.5F, 2F, 1F, 2F, 0.02F, 1}}),
            new GeneratedWildShapes.Part("neck", "body", 0F, -3.5F, -3F, 0.3142F, 0F, 0F, new float[][]{{14, 12, -1.5F, -2F, -1.5F, 3F, 2F, 3F, 0F, 0}}),
            new GeneratedWildShapes.Part("head", "neck", 0F, -1.5F, 0F, -0.3142F, 0F, 0F, new float[][]{{52, 0, -1.5F, -3F, -1.5F, 3F, 3F, 3F, 0F, 0}}),
            new GeneratedWildShapes.Part("snout", "head", 0F, -1F, -1.5F, 0F, 0F, 0F, new float[][]{{38, 12, -0.5F, -0.5F, -2F, 1F, 1F, 2F, 0F, 0}}),
            new GeneratedWildShapes.Part("right_wing", "body", -2.55F, -4.5F, -3.5F, 0F, 0F, 0F, new float[][]{{24, 0, -0.5F, 0F, 0F, 1F, 5F, 6F, 0F, 0}}),
            new GeneratedWildShapes.Part("left_wing", "body", 2.55F, -4.5F, -3.5F, 0F, 0F, 0F, new float[][]{{24, 0, -0.5F, 0F, 0F, 1F, 5F, 6F, 0F, 1}}),
            new GeneratedWildShapes.Part("tail", "body", 0F, -4F, 2F, -0.2094F, 0F, 0F, new float[][]{{0, 12, -1.5F, -0.5F, 0F, 3F, 1F, 4F, 0F, 0}})}),
        new GeneratedWildShapes.Shape("snake", 0, 10, 64, 64, 0F, 0F, 0, 3F, 2F, 4F, 0F, 24F, new GeneratedWildShapes.Part[]{
            new GeneratedWildShapes.Part("body", "", 0F, 24F, -9F, 0F, 0F, 0F, new float[][]{{0, 0, -1.5F, -2F, 0F, 3F, 2F, 4F, 0F, 0}}),
            new GeneratedWildShapes.Part("head", "body", 0F, 0F, 0.5F, 0F, 0F, 0F, new float[][]{{14, 0, -1.5F, -2F, -4F, 3F, 2F, 4F, 0.03F, 0}}),
            new GeneratedWildShapes.Part("snout", "head", 0F, -0.6F, -4F, 0F, 0F, 0F, new float[][]{{48, 6, -0.5F, -0.4F, -2F, 1F, 1F, 2F, 0F, 0}}),
            new GeneratedWildShapes.Part("seg_1", "body", 0F, 0F, 3F, 0F, 0F, 0F, new float[][]{{28, 0, -1.5F, -2F, -1F, 3F, 2F, 4F, 0.03F, 0}}),
            new GeneratedWildShapes.Part("seg_2", "seg_1", 0F, 0F, 3F, 0F, 0F, 0F, new float[][]{{42, 0, -1.5F, -2F, -1F, 3F, 2F, 4F, 0.06F, 0}}),
            new GeneratedWildShapes.Part("seg_3", "seg_2", 0F, 0F, 3F, 0F, 0F, 0F, new float[][]{{0, 6, -1.5F, -2F, -1F, 3F, 2F, 4F, 0.09F, 0}}),
            new GeneratedWildShapes.Part("seg_4", "seg_3", 0F, 0F, 3F, 0F, 0F, 0F, new float[][]{{14, 6, -1F, -2F, -1F, 2F, 2F, 4F, 0.12F, 0}}),
            new GeneratedWildShapes.Part("seg_5", "seg_4", 0F, 0F, 3F, 0F, 0F, 0F, new float[][]{{26, 6, -1F, -1F, -1F, 2F, 1F, 4F, 0.15F, 0}}),
            new GeneratedWildShapes.Part("seg_6", "seg_5", 0F, 0F, 3F, 0F, 0F, 0F, new float[][]{{38, 6, -0.5F, -1F, -1F, 1F, 1F, 4F, 0.18F, 0}})})
    };
}
