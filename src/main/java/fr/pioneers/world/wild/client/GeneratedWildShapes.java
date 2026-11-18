package fr.pioneers.world.wild.client;

/**
 * GENERE par tools/gc/wild/wild.py (1.0.5-beta, lot 6) : SILHOUETTES du gibier, un vrai modele par espece et par sexe (corps au pivot de
 * la hanche ; pattes, cou, bosse, queue en enfants du corps ; tete, museau, oreilles, bois, cornes, defenses en enfants du cou ou de la
 * tete ; dindon : deux pattes, ailes et une partie par plume de la roue). Boite : {u, v, x, y, z, l, h, p, gonflement, miroir}.
 * Sorte : 0 sanglier, 1 cerf, 2 dindon, 3 lievre, 4 bison. Ne pas modifier a la main.
 */
public final class GeneratedWildShapes {

    private GeneratedWildShapes() {
    }

    /** Partie : nom, parent ("" = racine), pivot, rotation de repos (rad), boites. */
    public record Part(String name, String parent, float x, float y, float z, float xRot, float yRot, float zRot, float[][] cubes) {
    }

    /** Silhouette : espece, sexe (0 male, 1 femelle), sorte, texture, pattes (longueur, largeur, nombre), corps (l, h, longueur), hanche. */
    public record Shape(String species, int sex, int kind, int texW, int texH, float legLen, float legW, int legs, float bodyW, float bodyH,
                        float bodyLen, float hipZ, float lieY, Part[] parts) {
    }

    public static final Shape[] SHAPES = {
        new Shape("boar", 0, 0, 64, 64, 5F, 3F, 4, 8F, 8F, 14F, 5.5F, 24F, new Part[]{
            new Part("body", "", 0F, 19F, 5.5F, 0F, 0F, 0F, new float[][]{{0, 0, -4F, -8F, -12.5F, 8F, 8F, 14F, 0F, 0}}),
            new Part("hump", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{0, 36, -4.5F, -10F, -13F, 9F, 3F, 7F, 0F, 0}}),
            new Part("mane", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{0, 22, -1F, -9.5F, -12F, 2F, 2F, 12F, 0.1F, 0}}),
            new Part("right_front_leg", "body", -2.45F, -1F, -10F, 0F, 0F, 0F, new float[][]{{32, 36, -1.5F, 0F, -1.5F, 3F, 6F, 3F, 0F, 0}}),
            new Part("left_front_leg", "body", 2.45F, -1F, -10F, 0F, 0F, 0F, new float[][]{{32, 36, -1.5F, 0F, -1.5F, 3F, 6F, 3F, 0F, 1}}),
            new Part("right_hind_leg", "body", -2.45F, -1F, -0.5F, 0F, 0F, 0F, new float[][]{{44, 36, -1.5F, 0F, -1.5F, 3F, 6F, 3F, 0F, 0}}),
            new Part("left_hind_leg", "body", 2.45F, -1F, -0.5F, 0F, 0F, 0F, new float[][]{{44, 36, -1.5F, 0F, -1.5F, 3F, 6F, 3F, 0F, 1}}),
            new Part("head", "body", 0F, -3.5F, -11.5F, 0F, 0F, 0F, new float[][]{{28, 22, -3F, -3F, -6F, 6F, 6F, 6F, 0F, 0}}),
            new Part("snout", "head", 0F, 3F, -6F, 0F, 0F, 0F, new float[][]{{0, 46, -2F, -3F, -5F, 4F, 3F, 5F, 0F, 0}}),
            new Part("right_ear", "head", -1.75F, -2.5F, -1.5F, -0.3142F, 0F, -0.4887F, new float[][]{{22, 46, -1F, -3F, -0.5F, 2F, 3F, 1F, 0F, 0}}),
            new Part("left_ear", "head", 1.75F, -2.5F, -1.5F, -0.3142F, 0F, 0.4887F, new float[][]{{22, 46, -1F, -3F, -0.5F, 2F, 3F, 1F, 0F, 1}}),
            new Part("tusks", "snout", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{28, 46, -3.02F, -4.5F, -4.5F, 1F, 3F, 1F, 0F, 0}, {28, 46, 2.02F, -4.5F, -4.5F, 1F, 3F, 1F, 0F, 1}}),
            new Part("tail", "body", 0F, -7F, 1F, 0.3142F, 0F, 0F, new float[][]{{18, 46, -0.5F, 0F, -0.5F, 1F, 6F, 1F, 0F, 0}})}),
        new Shape("boar", 1, 0, 64, 64, 5F, 3F, 4, 7F, 7F, 13F, 5F, 24F, new Part[]{
            new Part("body", "", 0F, 19F, 5F, 0F, 0F, 0F, new float[][]{{0, 0, -3.5F, -7F, -11.5F, 7F, 7F, 13F, 0F, 0}}),
            new Part("hump", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{0, 31, -4F, -8F, -12F, 8F, 2F, 6F, 0F, 0}}),
            new Part("mane", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{40, 0, -1F, -7.5F, -11F, 2F, 1F, 10F, 0.1F, 0}}),
            new Part("right_front_leg", "body", -1.95F, -1F, -9F, 0F, 0F, 0F, new float[][]{{22, 20, -1.5F, 0F, -1.5F, 3F, 6F, 3F, 0F, 0}}),
            new Part("left_front_leg", "body", 1.95F, -1F, -9F, 0F, 0F, 0F, new float[][]{{22, 20, -1.5F, 0F, -1.5F, 3F, 6F, 3F, 0F, 1}}),
            new Part("right_hind_leg", "body", -1.95F, -1F, -0.5F, 0F, 0F, 0F, new float[][]{{34, 20, -1.5F, 0F, -1.5F, 3F, 6F, 3F, 0F, 0}}),
            new Part("left_hind_leg", "body", 1.95F, -1F, -0.5F, 0F, 0F, 0F, new float[][]{{34, 20, -1.5F, 0F, -1.5F, 3F, 6F, 3F, 0F, 1}}),
            new Part("head", "body", 0F, -3F, -10.5F, 0F, 0F, 0F, new float[][]{{0, 20, -2.5F, -2.5F, -6F, 5F, 5F, 6F, 0F, 0}}),
            new Part("snout", "head", 0F, 2.5F, -6F, 0F, 0F, 0F, new float[][]{{28, 31, -1.5F, -3F, -4F, 3F, 3F, 4F, 0F, 0}}),
            new Part("right_ear", "head", -1.25F, -2F, -1.5F, -0.3142F, 0F, -0.4887F, new float[][]{{46, 31, -1F, -3F, -0.5F, 2F, 3F, 1F, 0F, 0}}),
            new Part("left_ear", "head", 1.25F, -2F, -1.5F, -0.3142F, 0F, 0.4887F, new float[][]{{46, 31, -1F, -3F, -0.5F, 2F, 3F, 1F, 0F, 1}}),
            new Part("tail", "body", 0F, -6F, 1F, 0.3142F, 0F, 0F, new float[][]{{42, 31, -0.5F, 0F, -0.5F, 1F, 5F, 1F, 0F, 0}})}),
        new Shape("deer", 0, 1, 64, 64, 11F, 2F, 4, 6F, 7F, 13F, 5F, 24F, new Part[]{
            new Part("body", "", 0F, 13F, 5F, 0F, 0F, 0F, new float[][]{{0, 0, -3F, -7F, -11.5F, 6F, 7F, 13F, 0F, 0}}),
            new Part("right_front_leg", "body", -1.95F, -1F, -9.5F, 0F, 0F, 0F, new float[][]{{38, 0, -1F, 0F, -1F, 2F, 12F, 2F, 0F, 0}}),
            new Part("left_front_leg", "body", 1.95F, -1F, -9.5F, 0F, 0F, 0F, new float[][]{{38, 0, -1F, 0F, -1F, 2F, 12F, 2F, 0F, 1}}),
            new Part("right_hind_leg", "body", -1.95F, -1F, 0F, 0F, 0F, 0F, new float[][]{{46, 0, -1F, 0F, -1F, 2F, 12F, 2F, 0F, 0}}),
            new Part("left_hind_leg", "body", 1.95F, -1F, 0F, 0F, 0F, 0F, new float[][]{{46, 0, -1F, 0F, -1F, 2F, 12F, 2F, 0F, 1}}),
            new Part("neck", "body", 0F, -5F, -9.5F, 0.5585F, 0F, 0F, new float[][]{{0, 20, -2F, -9F, -2F, 4F, 9F, 4F, 0F, 0}}),
            new Part("head", "neck", 0F, -7.5F, -0.5F, -0.3142F, 0F, 0F, new float[][]{{16, 20, -2F, -2.5F, -4.5F, 4F, 4F, 6F, 0F, 0}}),
            new Part("snout", "head", 0F, 1.5F, -4.5F, 0F, 0F, 0F, new float[][]{{40, 20, -1.5F, -3F, -3F, 3F, 3F, 3F, 0F, 0}}),
            new Part("right_ear", "head", -1.25F, -2F, -1.5F, -0.1745F, 0F, -1.0821F, new float[][]{{52, 20, -0.5F, -4F, -1F, 1F, 4F, 2F, 0F, 0}}),
            new Part("left_ear", "head", 1.25F, -2F, -1.5F, -0.1745F, 0F, 1.0821F, new float[][]{{52, 20, -0.5F, -4F, -1F, 1F, 4F, 2F, 0F, 1}}),
            new Part("right_antler", "head", -1F, -2F, -1F, -0.3491F, 0F, -0.5585F, new float[][]{{36, 20, -0.5F, -9F, -0.5F, 1F, 9F, 1F, 0F, 0}, {8, 33, -0.5F, -2.5F, -3.5F, 1F, 1F, 3F, 0F, 0}, {16, 33, -0.5F, -5.5F, -3F, 1F, 1F, 3F, 0.03F, 0}, {28, 33, -0.5F, -8.5F, -2.5F, 1F, 1F, 2F, 0F, 0}, {24, 33, -0.5F, -11.5F, -1.5F, 1F, 3F, 1F, 0F, 0}, {34, 33, -0.5F, -11F, 0.5F, 1F, 2F, 1F, 0F, 0}}),
            new Part("left_antler", "head", 1F, -2F, -1F, -0.3491F, 0F, 0.5585F, new float[][]{{36, 20, -0.5F, -9F, -0.5F, 1F, 9F, 1F, 0F, 1}, {8, 33, -0.5F, -2.5F, -3.5F, 1F, 1F, 3F, 0F, 1}, {16, 33, -0.5F, -5.5F, -3F, 1F, 1F, 3F, 0.03F, 1}, {28, 33, -0.5F, -8.5F, -2.5F, 1F, 1F, 2F, 0F, 1}, {24, 33, -0.5F, -11.5F, -1.5F, 1F, 3F, 1F, 0F, 1}, {34, 33, -0.5F, -11F, 0.5F, 1F, 2F, 1F, 0F, 1}}),
            new Part("tail", "body", 0F, -6F, 1F, 0.2094F, 0F, 0F, new float[][]{{0, 33, -1F, 0F, -1F, 2F, 3F, 2F, 0F, 0}})}),
        new Shape("deer", 1, 1, 64, 64, 10F, 2F, 4, 5F, 6F, 12F, 4.5F, 24F, new Part[]{
            new Part("body", "", 0F, 14F, 4.5F, 0F, 0F, 0F, new float[][]{{0, 0, -2.5F, -6F, -10.5F, 5F, 6F, 12F, 0F, 0}}),
            new Part("right_front_leg", "body", -1.45F, -1F, -8.5F, 0F, 0F, 0F, new float[][]{{34, 0, -1F, 0F, -1F, 2F, 11F, 2F, 0F, 0}}),
            new Part("left_front_leg", "body", 1.45F, -1F, -8.5F, 0F, 0F, 0F, new float[][]{{34, 0, -1F, 0F, -1F, 2F, 11F, 2F, 0F, 1}}),
            new Part("right_hind_leg", "body", -1.45F, -1F, 0F, 0F, 0F, 0F, new float[][]{{42, 0, -1F, 0F, -1F, 2F, 11F, 2F, 0F, 0}}),
            new Part("left_hind_leg", "body", 1.45F, -1F, 0F, 0F, 0F, 0F, new float[][]{{42, 0, -1F, 0F, -1F, 2F, 11F, 2F, 0F, 1}}),
            new Part("neck", "body", 0F, -4F, -8.5F, 0.5236F, 0F, 0F, new float[][]{{50, 0, -1.5F, -8F, -1.5F, 3F, 8F, 3F, 0F, 0}}),
            new Part("head", "neck", 0F, -6.5F, -0.5F, -0.2793F, 0F, 0F, new float[][]{{0, 18, -2F, -2.5F, -3.5F, 4F, 4F, 5F, 0F, 0}}),
            new Part("snout", "head", 0F, 1.5F, -3.5F, 0F, 0F, 0F, new float[][]{{18, 18, -1.5F, -3F, -3F, 3F, 3F, 3F, 0F, 0}}),
            new Part("right_ear", "head", -1.25F, -2F, -1.5F, -0.1745F, 0F, -1.0472F, new float[][]{{30, 18, -0.5F, -4F, -1F, 1F, 4F, 2F, 0F, 0}}),
            new Part("left_ear", "head", 1.25F, -2F, -1.5F, -0.1745F, 0F, 1.0472F, new float[][]{{30, 18, -0.5F, -4F, -1F, 1F, 4F, 2F, 0F, 1}}),
            new Part("tail", "body", 0F, -5F, 1F, 0.2094F, 0F, 0F, new float[][]{{36, 18, -1F, 0F, -1F, 2F, 3F, 2F, 0F, 0}})}),
        new Shape("turkey", 0, 2, 64, 64, 5F, 1F, 2, 7F, 7F, 9F, 0.5F, 24F, new Part[]{
            new Part("body", "", 0F, 19F, 0.5F, 0F, 0F, 0F, new float[][]{{0, 0, -3.5F, -7F, -5F, 7F, 7F, 9F, 0F, 0}}),
            new Part("chest", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{0, 16, -3F, -5.5F, -6.5F, 6F, 5F, 3F, 0F, 0}}),
            new Part("right_leg", "body", -2F, -1F, 0F, 0F, 0F, 0F, new float[][]{{26, 16, -0.5F, 0F, -0.5F, 1F, 6F, 1F, 0F, 0}, {46, 16, -1F, 5F, -2F, 2F, 1F, 3F, 0.02F, 0}}),
            new Part("left_leg", "body", 2F, -1F, 0F, 0F, 0F, 0F, new float[][]{{26, 16, -0.5F, 0F, -0.5F, 1F, 6F, 1F, 0F, 1}, {46, 16, -1F, 5F, -2F, 2F, 1F, 3F, 0.02F, 1}}),
            new Part("neck", "body", 0F, -5.5F, -6F, 0.2443F, 0F, 0F, new float[][]{{18, 16, -1F, -5F, -1F, 2F, 5F, 2F, 0F, 0}}),
            new Part("head", "neck", 0F, -4.5F, 0F, -0.2443F, 0F, 0F, new float[][]{{30, 16, -1.5F, -3F, -2F, 3F, 3F, 3F, 0F, 0}}),
            new Part("snout", "head", 0F, -1.5F, -2F, 0F, 0F, 0F, new float[][]{{4, 24, -0.5F, -0.5F, -2F, 1F, 1F, 2F, 0F, 0}}),
            new Part("snood", "head", 0F, -3F, -1.5F, 0F, 0F, 0F, new float[][]{{0, 24, -0.5F, 0F, -1F, 1F, 3F, 1F, 0.03F, 0}}),
            new Part("wattle", "head", 0F, 0F, -1F, 0F, 0F, 0F, new float[][]{{56, 16, -0.5F, -0.5F, -0.5F, 1F, 2F, 2F, 0F, 0}}),
            new Part("beard", "body", 0F, -4F, -7.5F, 0F, 0F, 0F, new float[][]{{42, 16, -0.5F, 0F, -0.5F, 1F, 4F, 1F, 0F, 0}}),
            new Part("right_wing", "body", -3.55F, -6F, -4F, 0F, 0F, 0F, new float[][]{{32, 0, -0.5F, 0F, 0F, 1F, 5F, 8F, 0.03F, 0}}),
            new Part("left_wing", "body", 3.55F, -6F, -4F, 0F, 0F, 0F, new float[][]{{32, 0, -0.5F, 0F, 0F, 1F, 5F, 8F, 0.03F, 1}}),
            new Part("fan_0", "body", 0F, -5.5F, 3F, -1.8151F, 0F, -0.4887F, new float[][]{{50, 0, -1.5F, -9F, -0.78F, 3F, 9F, 1F, 0F, 0}}),
            new Part("fan_1", "body", 0F, -5.5F, 3F, -1.8151F, 0F, -0.3665F, new float[][]{{50, 0, -1.5F, -9F, -0.71F, 3F, 9F, 1F, 0F, 0}}),
            new Part("fan_2", "body", 0F, -5.5F, 3F, -1.8151F, 0F, -0.2443F, new float[][]{{50, 0, -1.5F, -9F, -0.64F, 3F, 9F, 1F, 0F, 0}}),
            new Part("fan_3", "body", 0F, -5.5F, 3F, -1.8151F, 0F, -0.1222F, new float[][]{{50, 0, -1.5F, -9F, -0.57F, 3F, 9F, 1F, 0F, 0}}),
            new Part("fan_4", "body", 0F, -5.5F, 3F, -1.8151F, 0F, 0F, new float[][]{{50, 0, -1.5F, -9F, -0.5F, 3F, 9F, 1F, 0F, 0}}),
            new Part("fan_5", "body", 0F, -5.5F, 3F, -1.8151F, 0F, 0.1222F, new float[][]{{50, 0, -1.5F, -9F, -0.57F, 3F, 9F, 1F, 0F, 0}}),
            new Part("fan_6", "body", 0F, -5.5F, 3F, -1.8151F, 0F, 0.2443F, new float[][]{{50, 0, -1.5F, -9F, -0.64F, 3F, 9F, 1F, 0F, 0}}),
            new Part("fan_7", "body", 0F, -5.5F, 3F, -1.8151F, 0F, 0.3665F, new float[][]{{50, 0, -1.5F, -9F, -0.71F, 3F, 9F, 1F, 0F, 0}}),
            new Part("fan_8", "body", 0F, -5.5F, 3F, -1.8151F, 0F, 0.4887F, new float[][]{{50, 0, -1.5F, -9F, -0.78F, 3F, 9F, 1F, 0F, 0}})}),
        new Shape("turkey", 1, 2, 64, 64, 4F, 1F, 2, 6F, 6F, 8F, 0.5F, 24F, new Part[]{
            new Part("body", "", 0F, 20F, 0.5F, 0F, 0F, 0F, new float[][]{{0, 0, -3F, -6F, -4.5F, 6F, 6F, 8F, 0F, 0}}),
            new Part("chest", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{50, 0, -2.5F, -4.5F, -6F, 5F, 4F, 2F, 0F, 0}}),
            new Part("right_leg", "body", -1.5F, -1F, 0F, 0F, 0F, 0F, new float[][]{{8, 14, -0.5F, 0F, -0.5F, 1F, 5F, 1F, 0F, 0}, {22, 14, -1F, 4F, -2F, 2F, 1F, 3F, 0.02F, 0}}),
            new Part("left_leg", "body", 1.5F, -1F, 0F, 0F, 0F, 0F, new float[][]{{8, 14, -0.5F, 0F, -0.5F, 1F, 5F, 1F, 0F, 1}, {22, 14, -1F, 4F, -2F, 2F, 1F, 3F, 0.02F, 1}}),
            new Part("neck", "body", 0F, -4.5F, -5.5F, 0.2443F, 0F, 0F, new float[][]{{0, 14, -1F, -4F, -1F, 2F, 4F, 2F, 0F, 0}}),
            new Part("head", "neck", 0F, -3.5F, 0F, -0.2443F, 0F, 0F, new float[][]{{12, 14, -1F, -2F, -2F, 2F, 2F, 3F, 0F, 0}}),
            new Part("snout", "head", 0F, -1F, -2F, 0F, 0F, 0F, new float[][]{{32, 14, -0.5F, -0.5F, -2F, 1F, 1F, 2F, 0F, 0}}),
            new Part("wattle", "head", 0F, 0F, -1F, 0F, 0F, 0F, new float[][]{{38, 14, -0.5F, -0.5F, -0.5F, 1F, 1F, 2F, 0F, 0}}),
            new Part("right_wing", "body", -3.05F, -5F, -3.5F, 0F, 0F, 0F, new float[][]{{28, 0, -0.5F, 0F, 0F, 1F, 4F, 7F, 0.03F, 0}}),
            new Part("left_wing", "body", 3.05F, -5F, -3.5F, 0F, 0F, 0F, new float[][]{{28, 0, -0.5F, 0F, 0F, 1F, 4F, 7F, 0.03F, 1}}),
            new Part("fan_0", "body", 0F, -4.5F, 2.5F, -1.8151F, 0F, -0.3665F, new float[][]{{44, 0, -1F, -6F, -0.71F, 2F, 6F, 1F, 0F, 0}}),
            new Part("fan_1", "body", 0F, -4.5F, 2.5F, -1.8151F, 0F, -0.2443F, new float[][]{{44, 0, -1F, -6F, -0.64F, 2F, 6F, 1F, 0F, 0}}),
            new Part("fan_2", "body", 0F, -4.5F, 2.5F, -1.8151F, 0F, -0.1222F, new float[][]{{44, 0, -1F, -6F, -0.57F, 2F, 6F, 1F, 0F, 0}}),
            new Part("fan_3", "body", 0F, -4.5F, 2.5F, -1.8151F, 0F, 0F, new float[][]{{44, 0, -1F, -6F, -0.5F, 2F, 6F, 1F, 0F, 0}}),
            new Part("fan_4", "body", 0F, -4.5F, 2.5F, -1.8151F, 0F, 0.1222F, new float[][]{{44, 0, -1F, -6F, -0.57F, 2F, 6F, 1F, 0F, 0}}),
            new Part("fan_5", "body", 0F, -4.5F, 2.5F, -1.8151F, 0F, 0.2443F, new float[][]{{44, 0, -1F, -6F, -0.64F, 2F, 6F, 1F, 0F, 0}}),
            new Part("fan_6", "body", 0F, -4.5F, 2.5F, -1.8151F, 0F, 0.3665F, new float[][]{{44, 0, -1F, -6F, -0.71F, 2F, 6F, 1F, 0F, 0}})}),
        new Shape("hare", 0, 3, 64, 64, 4F, 2F, 4, 5F, 5F, 10F, 3.5F, 24F, new Part[]{
            new Part("body", "", 0F, 20F, 3.5F, 0F, 0F, 0F, new float[][]{{0, 0, -2.5F, -5F, -8.5F, 5F, 5F, 10F, 0F, 0}}),
            new Part("right_front_leg", "body", -1.45F, -1F, -6.5F, 0F, 0F, 0F, new float[][]{{6, 15, -1F, 0F, -1F, 2F, 5F, 2F, 0F, 0}}),
            new Part("left_front_leg", "body", 1.45F, -1F, -6.5F, 0F, 0F, 0F, new float[][]{{6, 15, -1F, 0F, -1F, 2F, 5F, 2F, 0F, 1}}),
            new Part("right_hind_leg", "body", -0.95F, -1F, 0F, 0F, 0F, 0F, new float[][]{{48, 0, -1.5F, 0F, -1.5F, 3F, 4F, 4F, 0F, 0}, {14, 15, -1F, 4F, -2.5F, 2F, 1F, 5F, 0.03F, 0}}),
            new Part("left_hind_leg", "body", 0.95F, -1F, 0F, 0F, 0F, 0F, new float[][]{{48, 0, -1.5F, 0F, -1.5F, 3F, 4F, 4F, 0.06F, 1}, {14, 15, -1F, 4F, -2.5F, 2F, 1F, 5F, 0.09F, 1}}),
            new Part("head", "body", 0F, -4F, -7.5F, 0F, 0F, 0F, new float[][]{{30, 0, -2F, -2F, -5F, 4F, 4F, 5F, 0F, 0}}),
            new Part("snout", "head", 0F, 2F, -5F, 0F, 0F, 0F, new float[][]{{28, 15, -1.5F, -2F, -2F, 3F, 2F, 2F, 0F, 0}}),
            new Part("right_ear", "head", -0.75F, -1.5F, -1.5F, -0.2443F, 0F, -0.1396F, new float[][]{{0, 15, -1F, -7F, -0.5F, 2F, 7F, 1F, 0F, 0}}),
            new Part("left_ear", "head", 0.75F, -1.5F, -1.5F, -0.2443F, 0F, 0.1396F, new float[][]{{0, 15, -1F, -7F, -0.5F, 2F, 7F, 1F, 0F, 1}}),
            new Part("tail", "body", 0F, -4F, 1F, 0.6981F, 0F, 0F, new float[][]{{38, 15, -1F, 0F, -1F, 2F, 2F, 2F, 0F, 0}})}),
        new Shape("bison", 0, 4, 128, 64, 10F, 4F, 4, 12F, 11F, 18F, 7.5F, 24F, new Part[]{
            new Part("body", "", 0F, 14F, 7.5F, 0F, 0F, 0F, new float[][]{{0, 0, -6F, -11F, -16.5F, 12F, 11F, 18F, 0F, 0}}),
            new Part("hump", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{60, 0, -6.5F, -16F, -17F, 13F, 7F, 10F, 0.4F, 0}}),
            new Part("chest", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{64, 29, -6.5F, -8.5F, -17.5F, 13F, 10F, 4F, 0.3F, 0}}),
            new Part("right_front_leg", "body", -3.95F, -1F, -13.5F, 0F, 0F, 0F, new float[][]{{32, 29, -2F, 0F, -2F, 4F, 11F, 4F, 0F, 0}}),
            new Part("left_front_leg", "body", 3.95F, -1F, -13.5F, 0F, 0F, 0F, new float[][]{{32, 29, -2F, 0F, -2F, 4F, 11F, 4F, 0F, 1}}),
            new Part("right_hind_leg", "body", -3.95F, -1F, -1F, 0F, 0F, 0F, new float[][]{{48, 29, -2F, 0F, -2F, 4F, 11F, 4F, 0F, 0}}),
            new Part("left_hind_leg", "body", 3.95F, -1F, -1F, 0F, 0F, 0F, new float[][]{{48, 29, -2F, 0F, -2F, 4F, 11F, 4F, 0F, 1}}),
            new Part("head", "body", 0F, -1F, -17.5F, 0F, 0F, 0F, new float[][]{{0, 29, -4.5F, -4.5F, -7F, 9F, 9F, 7F, 0F, 0}}),
            new Part("snout", "head", 0F, 4.5F, -7F, 0F, 0F, 0F, new float[][]{{102, 29, -3.5F, -4F, -3F, 7F, 4F, 3F, 0F, 0}}),
            new Part("right_ear", "head", -3.25F, -4F, -1.5F, 0F, 0F, -1.2217F, new float[][]{{18, 45, -1F, -2F, -0.5F, 2F, 2F, 1F, 0F, 0}}),
            new Part("left_ear", "head", 3.25F, -4F, -1.5F, 0F, 0F, 1.2217F, new float[][]{{18, 45, -1F, -2F, -0.5F, 2F, 2F, 1F, 0F, 1}}),
            new Part("right_horn", "head", -4F, -3.5F, -2.5F, 0F, 0F, 0.1745F, new float[][]{{24, 45, -4.5F, -1F, -0.5F, 4F, 1F, 1F, 0F, 0}, {14, 45, -4.5F, -4F, -0.5F, 1F, 3F, 1F, 0F, 0}}),
            new Part("left_horn", "head", 4F, -3.5F, -2.5F, 0F, 0F, -0.1745F, new float[][]{{24, 45, 0.5F, -1F, -0.5F, 4F, 1F, 1F, 0F, 1}, {14, 45, 3.5F, -4F, -0.5F, 1F, 3F, 1F, 0F, 1}}),
            new Part("beard", "head", 0F, 4.5F, -6F, 0F, 0F, 0F, new float[][]{{0, 45, -2.5F, -1F, -0.5F, 5F, 5F, 2F, 0F, 0}}),
            new Part("tail", "body", 0F, -10F, 1F, 0.1745F, 0F, 0F, new float[][]{{98, 29, -0.5F, 0F, -0.5F, 1F, 9F, 1F, 0F, 0}})}),
        new Shape("bison", 1, 4, 128, 64, 8F, 3F, 4, 10F, 10F, 16F, 6.5F, 24F, new Part[]{
            new Part("body", "", 0F, 16F, 6.5F, 0F, 0F, 0F, new float[][]{{0, 0, -5F, -10F, -14.5F, 10F, 10F, 16F, 0F, 0}}),
            new Part("hump", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{80, 0, -5.5F, -13F, -15F, 11F, 5F, 8F, 0.3F, 0}}),
            new Part("chest", "body", 0F, 0F, 0F, 0F, 0F, 0F, new float[][]{{24, 26, -5.5F, -6.5F, -15.5F, 11F, 8F, 3F, 0.2F, 0}}),
            new Part("right_front_leg", "body", -3.45F, -1F, -12F, 0F, 0F, 0F, new float[][]{{0, 26, -1.5F, 0F, -1.5F, 3F, 9F, 3F, 0F, 0}}),
            new Part("left_front_leg", "body", 3.45F, -1F, -12F, 0F, 0F, 0F, new float[][]{{0, 26, -1.5F, 0F, -1.5F, 3F, 9F, 3F, 0F, 1}}),
            new Part("right_hind_leg", "body", -3.45F, -1F, -0.5F, 0F, 0F, 0F, new float[][]{{12, 26, -1.5F, 0F, -1.5F, 3F, 9F, 3F, 0F, 0}}),
            new Part("left_hind_leg", "body", 3.45F, -1F, -0.5F, 0F, 0F, 0F, new float[][]{{12, 26, -1.5F, 0F, -1.5F, 3F, 9F, 3F, 0F, 1}}),
            new Part("head", "body", 0F, -1F, -15.5F, 0F, 0F, 0F, new float[][]{{52, 0, -4F, -4F, -6F, 8F, 8F, 6F, 0F, 0}}),
            new Part("snout", "head", 0F, 4F, -6F, 0F, 0F, 0F, new float[][]{{56, 26, -3F, -3F, -3F, 6F, 3F, 3F, 0F, 0}}),
            new Part("right_ear", "head", -2.75F, -3.5F, -1.5F, 0F, 0F, -1.2217F, new float[][]{{86, 26, -1F, -2F, -0.5F, 2F, 2F, 1F, 0F, 0}}),
            new Part("left_ear", "head", 2.75F, -3.5F, -1.5F, 0F, 0F, 1.2217F, new float[][]{{86, 26, -1F, -2F, -0.5F, 2F, 2F, 1F, 0F, 1}}),
            new Part("right_horn", "head", -3.5F, -3F, -2.5F, 0F, 0F, 0.1745F, new float[][]{{96, 26, -3.5F, -1F, -0.5F, 3F, 1F, 1F, 0F, 0}, {92, 26, -3.5F, -3F, -0.5F, 1F, 2F, 1F, 0F, 0}}),
            new Part("left_horn", "head", 3.5F, -3F, -2.5F, 0F, 0F, -0.1745F, new float[][]{{96, 26, 0.5F, -1F, -0.5F, 3F, 1F, 1F, 0F, 1}, {92, 26, 2.5F, -3F, -0.5F, 1F, 2F, 1F, 0F, 1}}),
            new Part("beard", "head", 0F, 4F, -5F, 0F, 0F, 0F, new float[][]{{74, 26, -2F, -1F, -0.5F, 4F, 4F, 2F, 0F, 0}}),
            new Part("tail", "body", 0F, -9F, 1F, 0.1745F, 0F, 0F, new float[][]{{52, 26, -0.5F, 0F, -0.5F, 1F, 8F, 1F, 0F, 0}})})
    };
}
