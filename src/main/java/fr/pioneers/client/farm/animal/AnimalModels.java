package fr.pioneers.client.farm.animal;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERE par tools/gc/farm2/gen_java.py depuis animal_shapes.py (1.0.3-beta, lot 8) : un modele par race et par sexe (couche
 * {@code <espece>_<race>_<f|m>}, ordre de animals.py : espece, race, femelle puis male), puis la toison de chaque race de mouton
 * ({@code sheep_fur_<race>}). Robes a 2 texels par pixel (uv normalises : la taille declaree est celle du patron). NE PAS MODIFIER.
 */
public final class AnimalModels {

    private AnimalModels() {
    }

    /** Nom de chaque couche. */
    public static final String[] LAYERS = {"cow_holstein_f", "cow_holstein_m", "cow_jersey_f", "cow_jersey_m", "cow_charolais_f", "cow_charolais_m", "cow_highland_f", "cow_highland_m", "cow_angus_f", "cow_angus_m", "sheep_merino_f", "sheep_merino_m", "sheep_suffolk_f", "sheep_suffolk_m", "sheep_texel_f", "sheep_texel_m", "sheep_ouessant_f", "sheep_ouessant_m", "sheep_jacob_f", "sheep_jacob_m", "pig_large_white_f", "pig_large_white_m", "pig_gascon_f", "pig_gascon_m", "pig_duroc_f", "pig_duroc_m", "pig_pietrain_f", "pig_pietrain_m", "pig_kunekune_f", "pig_kunekune_m", "chicken_leghorn_f", "chicken_leghorn_m", "chicken_marans_f", "chicken_marans_m", "chicken_brahma_f", "chicken_brahma_m", "chicken_gauloise_f", "chicken_gauloise_m", "chicken_silkie_f", "chicken_silkie_m", "goat_alpine_f", "goat_alpine_m", "goat_saanen_f", "goat_saanen_m", "goat_boer_f", "goat_boer_m", "goat_angora_f", "goat_angora_m", "goat_poitevine_f", "goat_poitevine_m", "sheep_fur_merino", "sheep_fur_suffolk", "sheep_fur_texel", "sheep_fur_ouessant", "sheep_fur_jacob"};

    /** Pieces cachees chez les petits (cornes, mamelle, crete, defenses...) ; pieces du pelage de l'angora (cachees tondu). */
    public static final String[][] ADULT = {
        {"udder", "teat0", "teat1", "teat2", "teat3", "horn_l0_b", "horn_r0_b"},
        {"ring0", "ring1", "ring2", "ring3", "ring4", "ring5", "ring6", "ring7", "horn_l0_b", "horn_r0_b"},
        {"udder", "teat0", "teat1", "teat2", "teat3", "horn_l0_b", "horn_l1_b", "horn_r0_b", "horn_r1_b"},
        {"ring0", "ring1", "ring2", "ring3", "ring4", "ring5", "ring6", "ring7", "horn_l0_b", "horn_l1_b", "horn_r0_b", "horn_r1_b"},
        {"udder", "teat0", "teat1", "teat2", "teat3", "horn_l0_b", "horn_l1_b", "horn_r0_b", "horn_r1_b"},
        {"ring0", "ring1", "ring2", "ring3", "ring4", "ring5", "ring6", "ring7", "horn_l0_b", "horn_l1_b", "horn_r0_b", "horn_r1_b"},
        {"udder", "teat0", "teat1", "teat2", "teat3", "horn_l0_b", "horn_l1_b", "horn_l2_b", "horn_r0_b", "horn_r1_b", "horn_r2_b"},
        {"ring0", "ring1", "ring2", "ring3", "ring4", "ring5", "ring6", "ring7", "horn_l0_b", "horn_l1_b", "horn_l2_b", "horn_r0_b", "horn_r1_b", "horn_r2_b"},
        {"udder", "teat0", "teat1", "teat2", "teat3"},
        {"ring0", "ring1", "ring2", "ring3", "ring4", "ring5", "ring6", "ring7"},
        {},
        {"horn_l0_b", "horn_l1_b", "horn_l2_b", "horn_l3_b", "horn_r0_b", "horn_r1_b", "horn_r2_b", "horn_r3_b"},
        {},
        {},
        {},
        {},
        {},
        {"horn_l0_b", "horn_l1_b", "horn_l2_b", "horn_l3_b", "horn_r0_b", "horn_r1_b", "horn_r2_b", "horn_r3_b"},
        {"horn_l0_b", "horn_l1_b", "horn_l2_b", "horn_l3_b", "horn_r0_b", "horn_r1_b", "horn_r2_b", "horn_r3_b", "hornu_l0_b", "hornu_l1_b", "hornu_r0_b", "hornu_r1_b"},
        {"horn_l0_b", "horn_l1_b", "horn_l2_b", "horn_l3_b", "horn_r0_b", "horn_r1_b", "horn_r2_b", "horn_r3_b", "hornu_l0_b", "hornu_l1_b", "hornu_r0_b", "hornu_r1_b"},
        {"teat0l", "teat0r", "teat1l", "teat1r", "teat2l", "teat2r", "teat3l", "teat3r"},
        {"tusk_l", "tusk_r"},
        {"teat0l", "teat0r", "teat1l", "teat1r", "teat2l", "teat2r", "teat3l", "teat3r"},
        {"tusk_l", "tusk_r"},
        {"teat0l", "teat0r", "teat1l", "teat1r", "teat2l", "teat2r", "teat3l", "teat3r"},
        {"tusk_l", "tusk_r"},
        {"teat0l", "teat0r", "teat1l", "teat1r", "teat2l", "teat2r", "teat3l", "teat3r"},
        {"tusk_l", "tusk_r"},
        {"teat0l", "teat0r", "teat1l", "teat1r", "teat2l", "teat2r", "teat3l", "teat3r"},
        {"tusk_l", "tusk_r"},
        {"comb", "wattle"},
        {"comb", "wattle", "leg_l_spur", "leg_r_spur"},
        {"comb", "wattle"},
        {"comb", "wattle", "leg_l_spur", "leg_r_spur"},
        {"comb", "wattle"},
        {"comb", "wattle", "leg_l_spur", "leg_r_spur"},
        {"comb", "wattle"},
        {"comb", "wattle", "leg_l_spur", "leg_r_spur"},
        {"comb"},
        {"comb", "leg_l_spur", "leg_r_spur"},
        {"udder", "teat0", "teat1", "beard", "horn_l0_b", "horn_l1_b", "horn_l2_b", "horn_r0_b", "horn_r1_b", "horn_r2_b"},
        {"beard", "horn_l0_b", "horn_l1_b", "horn_l2_b", "horn_r0_b", "horn_r1_b", "horn_r2_b"},
        {"udder", "teat0", "teat1", "beard"},
        {"beard"},
        {"udder", "teat0", "teat1", "horn_l0_b", "horn_l1_b", "horn_l2_b", "horn_r0_b", "horn_r1_b", "horn_r2_b"},
        {"beard", "horn_l0_b", "horn_l1_b", "horn_l2_b", "horn_r0_b", "horn_r1_b", "horn_r2_b"},
        {"udder", "teat0", "teat1", "horn_l0_b", "horn_l1_b", "horn_l2_b", "horn_l3_b", "horn_r0_b", "horn_r1_b", "horn_r2_b", "horn_r3_b"},
        {"beard", "horn_l0_b", "horn_l1_b", "horn_l2_b", "horn_l3_b", "horn_r0_b", "horn_r1_b", "horn_r2_b", "horn_r3_b"},
        {"udder", "teat0", "teat1", "beard"},
        {"beard"},
        {},
        {},
        {},
        {},
        {}
    };
    public static final String[][] COAT = {
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {"mohair", "mohair2", "topknot"},
        {"mohair", "mohair2", "topknot"},
        {},
        {},
        {},
        {},
        {},
        {},
        {}
    };

    /** Longueur des pattes (pixels de modele, du pivot au sol) : petits aux pattes plus longues. */
    public static final float[] LEG = {12.25F, 12.25F, 11.5F, 11.5F, 11F, 11F, 9.25F, 9.25F, 10.5F, 10.5F, 8.25F, 8.25F, 9.75F, 9.75F, 8.5F, 8.5F, 8.5F, 8.5F, 9F, 9F, 6.5F, 6.5F, 6.75F, 6.75F, 6.5F, 6.5F, 6.25F, 6.25F, 5.25F, 5.25F, 5F, 5.25F, 4.5F, 5F, 4.75F, 5F, 4.75F, 5F, 3.5F, 3.75F, 9.5F, 9.5F, 9.75F, 9.75F, 8.5F, 8.5F, 8.5F, 8.5F, 9.5F, 9.5F, 8.25F, 9.75F, 8.5F, 8.5F, 9F};

    public static LayerDefinition create(int layer) {
        return switch (layer) {
            case 0 -> m0();
            case 1 -> m1();
            case 2 -> m2();
            case 3 -> m3();
            case 4 -> m4();
            case 5 -> m5();
            case 6 -> m6();
            case 7 -> m7();
            case 8 -> m8();
            case 9 -> m9();
            case 10 -> m10();
            case 11 -> m11();
            case 12 -> m12();
            case 13 -> m13();
            case 14 -> m14();
            case 15 -> m15();
            case 16 -> m16();
            case 17 -> m17();
            case 18 -> m18();
            case 19 -> m19();
            case 20 -> m20();
            case 21 -> m21();
            case 22 -> m22();
            case 23 -> m23();
            case 24 -> m24();
            case 25 -> m25();
            case 26 -> m26();
            case 27 -> m27();
            case 28 -> m28();
            case 29 -> m29();
            case 30 -> m30();
            case 31 -> m31();
            case 32 -> m32();
            case 33 -> m33();
            case 34 -> m34();
            case 35 -> m35();
            case 36 -> m36();
            case 37 -> m37();
            case 38 -> m38();
            case 39 -> m39();
            case 40 -> m40();
            case 41 -> m41();
            case 42 -> m42();
            case 43 -> m43();
            case 44 -> m44();
            case 45 -> m45();
            case 46 -> m46();
            case 47 -> m47();
            case 48 -> m48();
            case 49 -> m49();
            case 50 -> m50();
            case 51 -> m51();
            case 52 -> m52();
            case 53 -> m53();
            case 54 -> m54();
            default -> m0();
        };
    }

    /** cow_holstein_f */
    private static LayerDefinition m0() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 7.5F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 18).addBox(-5.5F, -4.75F, -9.5F, 11F, 10.5F, 6F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-6F, -4.25F, -3.5F, 12F, 10.5F, 7F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 35).addBox(-5.5F, -5.25F, 3.5F, 11F, 10F, 6F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_l", CubeListBuilder.create().texOffs(45, 95).addBox(3F, -5.75F, 4F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_r", CubeListBuilder.create().texOffs(52, 95).addBox(-5F, -5.75F, 4F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("brisket", CubeListBuilder.create().texOffs(30, 89).addBox(-4F, 5.75F, -9F, 8F, 1F, 4.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("udder", CubeListBuilder.create().texOffs(24, 74).addBox(-2.5F, 5.25F, 1.5F, 5F, 3F, 5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0", CubeListBuilder.create().texOffs(29, 95).addBox(1F, 8.25F, 1.5F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1", CubeListBuilder.create().texOffs(33, 95).addBox(-2F, 8.25F, 1.5F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2", CubeListBuilder.create().texOffs(37, 95).addBox(1F, 8.25F, 4.5F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3", CubeListBuilder.create().texOffs(41, 95).addBox(-2F, 8.25F, 4.5F, 1F, 1.5F, 1F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.75F, -8F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(34, 35).addBox(-3F, -1.5F, -5.5F, 6F, 7.5F, 6.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 51).addBox(-3.5F, -2.5F, -10.5F, 7F, 6.5F, 5.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("poll", CubeListBuilder.create().texOffs(0, 95).addBox(-2.5F, -3.5F, -9.5F, 5F, 1F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(0, 82).addBox(-2.75F, 1.5F, -13F, 5.5F, 4.5F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(44, 74).addBox(-2.25F, 4F, -10F, 4.5F, 2.5F, 4.5F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(3.5F, -0.5F, -7F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(15, 95).addBox(0F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.5F, -0.5F, -7F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(22, 95).addBox(-3F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(3F, -1.75F, -8F, 0F, 0F, -0.55F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(0, 99).addBox(0F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-3F, -1.75F, -8F, 0F, 0F, 0.55F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(6, 99).addBox(-2F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.25F, 11.75F, -6.5F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(0, 63).addBox(-2F, -0.5F, -2F, 4F, 6.25F, 4F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(0, 74).addBox(-1.5F, 5.75F, -1.5F, 3F, 5F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(0, 89).addBox(-1.75F, 10.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.25F, 11.75F, -6.5F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(16, 63).addBox(-2F, -0.5F, -2F, 4F, 6.25F, 4F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(12, 74).addBox(-1.5F, 5.75F, -1.5F, 3F, 5F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(15, 89).addBox(-1.75F, 10.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.25F, 11.75F, 6.5F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(29, 51).addBox(-2F, -0.5F, -2.5F, 4F, 6.25F, 4.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(32, 63).addBox(-1.5F, 5.75F, -1.5F, 3F, 5F, 3F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(24, 82).addBox(-1.75F, 10.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.25F, 11.75F, 6.5F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(46, 51).addBox(-2F, -0.5F, -2.5F, 4F, 6.25F, 4.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(44, 63).addBox(-1.5F, 5.75F, -1.5F, 3F, 5F, 3F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(39, 82).addBox(-1.75F, 10.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -4.5F, 10F, 0.12F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(25, 51).addBox(-0.5F, 0F, -0.5F, 1F, 11F, 1F), PartPose.ZERO);
        p_tail.addOrReplaceChild("switch", CubeListBuilder.create().texOffs(16, 82).addBox(-1F, 8F, -1F, 2F, 4F, 2F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** cow_holstein_m */
    private static LayerDefinition m1() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 7.5F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 18).addBox(-5.5F, -4.75F, -9.5F, 11F, 10.5F, 6F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-6F, -4.25F, -3.5F, 12F, 10.5F, 7F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 35).addBox(-5.5F, -5.25F, 3.5F, 11F, 10F, 6F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_l", CubeListBuilder.create().texOffs(7, 99).addBox(3F, -5.75F, 4F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_r", CubeListBuilder.create().texOffs(14, 99).addBox(-5F, -5.75F, 4F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("brisket", CubeListBuilder.create().texOffs(30, 88).addBox(-4F, 5.75F, -9F, 8F, 1F, 4.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(0, 94).addBox(-4.5F, -5.75F, -9F, 9F, 1F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.75F, -8F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(34, 35).addBox(-3F, -1.5F, -5.5F, 6F, 7.5F, 6.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 51).addBox(-3.5F, -2.5F, -10.5F, 7F, 6.5F, 5.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("poll", CubeListBuilder.create().texOffs(26, 94).addBox(-2.5F, -3.5F, -9.5F, 5F, 1F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(42, 74).addBox(-2.75F, 1.5F, -13F, 5.5F, 4.5F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(24, 74).addBox(-2.25F, 4F, -10F, 4.5F, 2.5F, 4.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("nose_pad", CubeListBuilder.create().texOffs(41, 94).addBox(-2.5F, 2.5F, -13.5F, 5F, 2.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring0", CubeListBuilder.create().texOffs(39, 99).addBox(-0.5F, 4F, -14F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring1", CubeListBuilder.create().texOffs(42, 99).addBox(-1F, 4.5F, -14F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring2", CubeListBuilder.create().texOffs(44, 99).addBox(0.5F, 4.5F, -14F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring3", CubeListBuilder.create().texOffs(35, 99).addBox(-1.5F, 5F, -14F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring4", CubeListBuilder.create().texOffs(37, 99).addBox(1F, 5F, -14F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring5", CubeListBuilder.create().texOffs(46, 99).addBox(-1F, 6F, -14F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring6", CubeListBuilder.create().texOffs(48, 99).addBox(0.5F, 6F, -14F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring7", CubeListBuilder.create().texOffs(50, 99).addBox(-0.5F, 6.5F, -14F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(8, 82).addBox(-2.5F, -2.5F, -4.5F, 5F, 1F, 4.5F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(3.5F, -0.5F, -7F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(52, 94).addBox(0F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.5F, -0.5F, -7F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(0, 99).addBox(-3F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(3F, -1.75F, -8F, 0F, 0F, -0.55F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(21, 99).addBox(0F, -0.5F, -0.5F, 2.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-3F, -1.75F, -8F, 0F, 0F, 0.55F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(28, 99).addBox(-2.5F, -0.5F, -0.5F, 2.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.25F, 11.75F, -6.5F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(0, 63).addBox(-2F, -0.5F, -2F, 4F, 6.25F, 4F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(0, 74).addBox(-1.5F, 5.75F, -1.5F, 3F, 5F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(0, 88).addBox(-1.75F, 10.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.25F, 11.75F, -6.5F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(16, 63).addBox(-2F, -0.5F, -2F, 4F, 6.25F, 4F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(12, 74).addBox(-1.5F, 5.75F, -1.5F, 3F, 5F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(15, 88).addBox(-1.75F, 10.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.25F, 11.75F, 6.5F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(29, 51).addBox(-2F, -0.5F, -2.5F, 4F, 6.25F, 4.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(32, 63).addBox(-1.5F, 5.75F, -1.5F, 3F, 5F, 3F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(27, 82).addBox(-1.75F, 10.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.25F, 11.75F, 6.5F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(46, 51).addBox(-2F, -0.5F, -2.5F, 4F, 6.25F, 4.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(44, 63).addBox(-1.5F, 5.75F, -1.5F, 3F, 5F, 3F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(42, 82).addBox(-1.75F, 10.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -4.5F, 10F, 0.12F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(25, 51).addBox(-0.5F, 0F, -0.5F, 1F, 11F, 1F), PartPose.ZERO);
        p_tail.addOrReplaceChild("switch", CubeListBuilder.create().texOffs(0, 82).addBox(-1F, 8F, -1F, 2F, 4F, 2F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** cow_jersey_f */
    private static LayerDefinition m2() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 8.875F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 16).addBox(-4.75F, -4F, -8.75F, 9.75F, 9.25F, 5.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-5.25F, -3.5F, -3.25F, 10.75F, 9.25F, 6.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(31, 16).addBox(-4.75F, -4.5F, 3.25F, 9.75F, 8.75F, 5.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_l", CubeListBuilder.create().texOffs(23, 86).addBox(2.5F, -5F, 3.75F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_r", CubeListBuilder.create().texOffs(30, 86).addBox(-4.25F, -5F, 3.75F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("brisket", CubeListBuilder.create().texOffs(15, 80).addBox(-3.25F, 5.25F, -8.25F, 6.75F, 1F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("udder", CubeListBuilder.create().texOffs(16, 56).addBox(-2.5F, 4.75F, 1.25F, 5F, 3F, 5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0", CubeListBuilder.create().texOffs(7, 86).addBox(1F, 7.75F, 1.25F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1", CubeListBuilder.create().texOffs(11, 86).addBox(-2F, 7.75F, 1.25F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2", CubeListBuilder.create().texOffs(15, 86).addBox(1F, 7.75F, 4.25F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3", CubeListBuilder.create().texOffs(19, 86).addBox(-2F, 7.75F, 4.25F, 1F, 1.5F, 1F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.125F, -7.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 31).addBox(-2.75F, -1.5F, -5.25F, 5.5F, 7.5F, 6.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(24, 31).addBox(-3.5F, -2.5F, -9.75F, 7F, 6.5F, 5F), PartPose.ZERO);
        p_head.addOrReplaceChild("poll", CubeListBuilder.create().texOffs(37, 80).addBox(-2.5F, -3.5F, -8.75F, 5F, 1F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(24, 66).addBox(-2.75F, 1.5F, -12F, 5.5F, 4.5F, 2.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(40, 66).addBox(-2.25F, 4F, -9.25F, 4.5F, 2.5F, 4F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(3.5F, -0.5F, -6.25F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(52, 80).addBox(0F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.5F, -0.5F, -6.25F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(0, 86).addBox(-3F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(3F, -1.75F, -7.25F, 0F, 0.25F, -0.2F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(37, 86).addBox(0F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.65F, 0F, 0F, 0F, 0.55F, -0.75F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(49, 86).addBox(0F, -0.25F, -0.25F, 2F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-3F, -1.75F, -7.25F, 0F, -0.25F, 0.2F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(43, 86).addBox(-2F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.65F, 0F, 0F, 0F, -0.55F, 0.75F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(54, 86).addBox(-2F, -0.25F, -0.25F, 2F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, 12.5F, -5.875F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(34, 45).addBox(-2F, -0.5F, -2F, 4F, 5.75F, 4F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(0, 66).addBox(-1.5F, 5.25F, -1.5F, 3F, 4.75F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(38, 74).addBox(-1.75F, 10F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, 12.5F, -5.875F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 56).addBox(-2F, -0.5F, -2F, 4F, 5.75F, 4F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(12, 66).addBox(-1.5F, 5.25F, -1.5F, 3F, 4.75F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(0, 80).addBox(-1.75F, 10F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, 12.5F, 5.875F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(0, 45).addBox(-2F, -0.5F, -2.5F, 4F, 5.75F, 4.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(36, 56).addBox(-1.5F, 5.25F, -1.5F, 3F, 4.75F, 3F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(8, 74).addBox(-1.75F, 10F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, 12.5F, 5.875F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(17, 45).addBox(-2F, -0.5F, -2.5F, 4F, 5.75F, 4.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(48, 56).addBox(-1.5F, 5.25F, -1.5F, 3F, 4.75F, 3F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(23, 74).addBox(-1.75F, 10F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.875F, 9.375F, 0.12F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(48, 31).addBox(-0.5F, 0F, -0.5F, 1F, 10F, 1F), PartPose.ZERO);
        p_tail.addOrReplaceChild("switch", CubeListBuilder.create().texOffs(0, 74).addBox(-1F, 7F, -1F, 2F, 4F, 2F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** cow_jersey_m */
    private static LayerDefinition m3() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 8.875F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 16).addBox(-4.75F, -4F, -8.75F, 9.75F, 9.25F, 5.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-5.25F, -3.5F, -3.25F, 10.75F, 9.25F, 6.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(31, 16).addBox(-4.75F, -4.5F, 3.25F, 9.75F, 8.75F, 5.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_l", CubeListBuilder.create().texOffs(25, 84).addBox(2.5F, -5F, 3.75F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_r", CubeListBuilder.create().texOffs(32, 84).addBox(-4.25F, -5F, 3.75F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("brisket", CubeListBuilder.create().texOffs(0, 79).addBox(-3.25F, 5.25F, -8.25F, 6.75F, 1F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(22, 79).addBox(-3.75F, -5F, -8.25F, 7.75F, 1F, 3.75F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.125F, -7.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 31).addBox(-3F, -1.5F, -5.25F, 6F, 7.5F, 6.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(25, 31).addBox(-3.5F, -2.5F, -9.75F, 7F, 6.5F, 5F), PartPose.ZERO);
        p_head.addOrReplaceChild("poll", CubeListBuilder.create().texOffs(45, 79).addBox(-2.5F, -3.5F, -8.75F, 5F, 1F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(0, 66).addBox(-2.75F, 1.5F, -12F, 5.5F, 4.5F, 2.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(16, 66).addBox(-2.25F, 4F, -9.25F, 4.5F, 2.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("nose_pad", CubeListBuilder.create().texOffs(0, 84).addBox(-2.5F, 2.5F, -12.5F, 5F, 2.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring0", CubeListBuilder.create().texOffs(6, 87).addBox(-0.5F, 4F, -13F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring1", CubeListBuilder.create().texOffs(9, 87).addBox(-1F, 4.5F, -13F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring2", CubeListBuilder.create().texOffs(11, 87).addBox(0.5F, 4.5F, -13F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring3", CubeListBuilder.create().texOffs(53, 84).addBox(-1.5F, 5F, -13F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring4", CubeListBuilder.create().texOffs(55, 84).addBox(1F, 5F, -13F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring5", CubeListBuilder.create().texOffs(13, 87).addBox(-1F, 6F, -13F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring6", CubeListBuilder.create().texOffs(15, 87).addBox(0.5F, 6F, -13F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring7", CubeListBuilder.create().texOffs(17, 87).addBox(-0.5F, 6.5F, -13F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(45, 73).addBox(-2.5F, -2.5F, -4.25F, 5F, 1F, 4.25F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(3.5F, -0.5F, -6.25F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(11, 84).addBox(0F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.5F, -0.5F, -6.25F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(18, 84).addBox(-3F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(3F, -1.75F, -7.25F, 0F, 0.25F, -0.2F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(39, 84).addBox(0F, -0.5F, -0.5F, 2.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(2.15F, 0F, 0F, 0F, 0.55F, -0.75F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(57, 84).addBox(0F, -0.25F, -0.25F, 2.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-3F, -1.75F, -7.25F, 0F, -0.25F, 0.2F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(46, 84).addBox(-2.5F, -0.5F, -0.5F, 2.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.15F, 0F, 0F, 0F, -0.55F, 0.75F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(0, 87).addBox(-2.5F, -0.25F, -0.25F, 2.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, 12.5F, -5.875F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(34, 45).addBox(-2F, -0.5F, -2F, 4F, 5.75F, 4F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(40, 56).addBox(-1.5F, 5.25F, -1.5F, 3F, 4.75F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(15, 73).addBox(-1.75F, 10F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, 12.5F, -5.875F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 56).addBox(-2F, -0.5F, -2F, 4F, 5.75F, 4F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(52, 56).addBox(-1.5F, 5.25F, -1.5F, 3F, 4.75F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(30, 73).addBox(-1.75F, 10F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, 12.5F, 5.875F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(0, 45).addBox(-2F, -0.5F, -2.5F, 4F, 5.75F, 4.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(16, 56).addBox(-1.5F, 5.25F, -1.5F, 3F, 4.75F, 3F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(41, 66).addBox(-1.75F, 10F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, 12.5F, 5.875F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(17, 45).addBox(-2F, -0.5F, -2.5F, 4F, 5.75F, 4.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(28, 56).addBox(-1.5F, 5.25F, -1.5F, 3F, 4.75F, 3F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 73).addBox(-1.75F, 10F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.875F, 9.375F, 0.12F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(49, 31).addBox(-0.5F, 0F, -0.5F, 1F, 10F, 1F), PartPose.ZERO);
        p_tail.addOrReplaceChild("switch", CubeListBuilder.create().texOffs(33, 66).addBox(-1F, 7F, -1F, 2F, 4F, 2F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** cow_charolais_f */
    private static LayerDefinition m4() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 8.125F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 20).addBox(-6.25F, -5.25F, -10F, 12.5F, 11.75F, 6.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-6.75F, -4.75F, -3.75F, 13.5F, 11.75F, 7.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 39).addBox(-6.25F, -5.75F, 3.75F, 12.5F, 11.25F, 6.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_l", CubeListBuilder.create().texOffs(45, 101).addBox(3.75F, -6.25F, 4.25F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_r", CubeListBuilder.create().texOffs(52, 101).addBox(-5.75F, -6.25F, 4.25F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("brisket", CubeListBuilder.create().texOffs(24, 88).addBox(-4.75F, 6.5F, -9.5F, 9.5F, 1F, 4.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("udder", CubeListBuilder.create().texOffs(32, 70).addBox(-2.5F, 6F, 1.75F, 5F, 3F, 5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0", CubeListBuilder.create().texOffs(29, 101).addBox(1F, 9F, 1.75F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1", CubeListBuilder.create().texOffs(33, 101).addBox(-2F, 9F, 1.75F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2", CubeListBuilder.create().texOffs(37, 101).addBox(1F, 9F, 4.75F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3", CubeListBuilder.create().texOffs(41, 101).addBox(-2F, 9F, 4.75F, 1F, 1.5F, 1F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.375F, -8.625F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(38, 39).addBox(-3F, -1.5F, -5.75F, 6F, 7.5F, 6.75F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 57).addBox(-3.5F, -2.5F, -11F, 7F, 6.5F, 5.75F), PartPose.ZERO);
        p_head.addOrReplaceChild("poll", CubeListBuilder.create().texOffs(0, 101).addBox(-2.5F, -3.5F, -10F, 5F, 1F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(0, 88).addBox(-2.75F, 1.5F, -13.5F, 5.5F, 4.5F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(36, 80).addBox(-2.25F, 4F, -10.5F, 4.5F, 2.5F, 4.75F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(3.5F, -0.5F, -7.5F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(15, 101).addBox(0F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.5F, -0.5F, -7.5F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(22, 101).addBox(-3F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(3F, -1.75F, -8.5F, 0F, 0.25F, -0.2F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(0, 105).addBox(0F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.65F, 0F, 0F, 0F, 0.55F, -0.75F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(12, 105).addBox(0F, -0.25F, -0.25F, 2F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-3F, -1.75F, -8.5F, 0F, -0.25F, 0.2F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(6, 105).addBox(-2F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.65F, 0F, 0F, 0F, -0.55F, 0.75F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(17, 105).addBox(-2F, -0.25F, -0.25F, 2F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(4F, 13F, -7.125F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(0, 70).addBox(-2F, -0.5F, -2F, 4F, 5.5F, 4F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(12, 80).addBox(-1.5F, 5F, -1.5F, 3F, 4.5F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(30, 95).addBox(-1.75F, 9.5F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-4F, 13F, -7.125F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(16, 70).addBox(-2F, -0.5F, -2F, 4F, 5.5F, 4F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(24, 80).addBox(-1.5F, 5F, -1.5F, 3F, 4.5F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(45, 95).addBox(-1.75F, 9.5F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(4F, 13F, 7.125F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(30, 57).addBox(-2F, -0.5F, -2.5F, 4F, 5.5F, 4.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(52, 70).addBox(-1.5F, 5F, -1.5F, 3F, 4.5F, 3F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(0, 95).addBox(-1.75F, 9.5F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-4F, 13F, 7.125F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(47, 57).addBox(-2F, -0.5F, -2.5F, 4F, 5.5F, 4.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(0, 80).addBox(-1.5F, 5F, -1.5F, 3F, 4.5F, 3F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(15, 95).addBox(-1.75F, 9.5F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -5.125F, 10.625F, 0.12F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(26, 57).addBox(-0.5F, 0F, -0.5F, 1F, 11.25F, 1F), PartPose.ZERO);
        p_tail.addOrReplaceChild("switch", CubeListBuilder.create().texOffs(16, 88).addBox(-1F, 8.25F, -1F, 2F, 4F, 2F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** cow_charolais_m */
    private static LayerDefinition m5() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 8.125F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 20).addBox(-6.25F, -5.25F, -10F, 12.5F, 11.75F, 6.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-6.75F, -4.75F, -3.75F, 13.5F, 11.75F, 7.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 39).addBox(-6.25F, -5.75F, 3.75F, 12.5F, 11.25F, 6.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_l", CubeListBuilder.create().texOffs(7, 106).addBox(3.75F, -6.25F, 4.25F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_r", CubeListBuilder.create().texOffs(14, 106).addBox(-5.75F, -6.25F, 4.25F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("brisket", CubeListBuilder.create().texOffs(8, 88).addBox(-4.75F, 6.5F, -9.5F, 9.5F, 1F, 4.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(0, 94).addBox(-5.25F, -6.25F, -9.5F, 10.5F, 1F, 4.5F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.375F, -8.625F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(38, 39).addBox(-3F, -1.5F, -5.75F, 6F, 7.5F, 6.75F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 57).addBox(-3.5F, -2.5F, -11F, 7F, 6.5F, 5.75F), PartPose.ZERO);
        p_head.addOrReplaceChild("poll", CubeListBuilder.create().texOffs(30, 100).addBox(-2.5F, -3.5F, -10F, 5F, 1F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(43, 80).addBox(-2.75F, 1.5F, -13.5F, 5.5F, 4.5F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(24, 80).addBox(-2.25F, 4F, -10.5F, 4.5F, 2.5F, 4.75F), PartPose.ZERO);
        p_head.addOrReplaceChild("nose_pad", CubeListBuilder.create().texOffs(45, 100).addBox(-2.5F, 2.5F, -14F, 5F, 2.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring0", CubeListBuilder.create().texOffs(51, 106).addBox(-0.5F, 4F, -14.5F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring1", CubeListBuilder.create().texOffs(54, 106).addBox(-1F, 4.5F, -14.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring2", CubeListBuilder.create().texOffs(56, 106).addBox(0.5F, 4.5F, -14.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring3", CubeListBuilder.create().texOffs(35, 106).addBox(-1.5F, 5F, -14.5F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring4", CubeListBuilder.create().texOffs(37, 106).addBox(1F, 5F, -14.5F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring5", CubeListBuilder.create().texOffs(58, 106).addBox(-1F, 6F, -14.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring6", CubeListBuilder.create().texOffs(60, 106).addBox(0.5F, 6F, -14.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring7", CubeListBuilder.create().texOffs(0, 109).addBox(-0.5F, 6.5F, -14.5F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(37, 88).addBox(-2.5F, -2.5F, -4.75F, 5F, 1F, 4.75F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(3.5F, -0.5F, -7.5F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(56, 100).addBox(0F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.5F, -0.5F, -7.5F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(0, 106).addBox(-3F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(3F, -1.75F, -8.5F, 0F, 0.25F, -0.2F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(21, 106).addBox(0F, -0.5F, -0.5F, 2.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(2.15F, 0F, 0F, 0F, 0.55F, -0.75F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(39, 106).addBox(0F, -0.25F, -0.25F, 2.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-3F, -1.75F, -8.5F, 0F, -0.25F, 0.2F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(28, 106).addBox(-2.5F, -0.5F, -0.5F, 2.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.15F, 0F, 0F, 0F, -0.55F, 0.75F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(45, 106).addBox(-2.5F, -0.25F, -0.25F, 2.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(4F, 13F, -7.125F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(0, 70).addBox(-2F, -0.5F, -2F, 4F, 5.5F, 4F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(0, 80).addBox(-1.5F, 5F, -1.5F, 3F, 4.5F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(0, 100).addBox(-1.75F, 9.5F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-4F, 13F, -7.125F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(16, 70).addBox(-2F, -0.5F, -2F, 4F, 5.5F, 4F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(12, 80).addBox(-1.5F, 5F, -1.5F, 3F, 4.5F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(15, 100).addBox(-1.75F, 9.5F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(4F, 13F, 7.125F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(30, 57).addBox(-2F, -0.5F, -2.5F, 4F, 5.5F, 4.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(32, 70).addBox(-1.5F, 5F, -1.5F, 3F, 4.5F, 3F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(30, 94).addBox(-1.75F, 9.5F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-4F, 13F, 7.125F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(47, 57).addBox(-2F, -0.5F, -2.5F, 4F, 5.5F, 4.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(44, 70).addBox(-1.5F, 5F, -1.5F, 3F, 4.5F, 3F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(45, 94).addBox(-1.75F, 9.5F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -5.125F, 10.625F, 0.12F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(26, 57).addBox(-0.5F, 0F, -0.5F, 1F, 11.25F, 1F), PartPose.ZERO);
        p_tail.addOrReplaceChild("switch", CubeListBuilder.create().texOffs(0, 88).addBox(-1F, 8.25F, -1F, 2F, 4F, 2F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** cow_highland_f */
    private static LayerDefinition m6() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 10.25F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 18).addBox(-5.75F, -5F, -9F, 11.5F, 11F, 6F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-6.25F, -4.5F, -3.25F, 12.5F, 11F, 6.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 52).addBox(-5.75F, -5.5F, 3.25F, 11.5F, 10.5F, 6F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_l", CubeListBuilder.create().texOffs(4, 140).addBox(3.25F, -6F, 3.75F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_r", CubeListBuilder.create().texOffs(11, 140).addBox(-5.25F, -6F, 3.75F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("brisket", CubeListBuilder.create().texOffs(30, 130).addBox(-4.25F, 6F, -8.5F, 8.5F, 1F, 4.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("udder", CubeListBuilder.create().texOffs(18, 102).addBox(-2.5F, 5.5F, 1.25F, 5F, 3F, 5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0", CubeListBuilder.create().texOffs(51, 136).addBox(1F, 8.5F, 1.25F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1", CubeListBuilder.create().texOffs(55, 136).addBox(-2F, 8.5F, 1.25F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2", CubeListBuilder.create().texOffs(59, 136).addBox(1F, 8.5F, 4.25F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3", CubeListBuilder.create().texOffs(0, 140).addBox(-2F, 8.5F, 4.25F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 35).addBox(-6.75F, -4F, -2.75F, 13.5F, 11.5F, 5.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(0, 81).addBox(-5.25F, -6.5F, -5.25F, 10.5F, 1.75F, 8.5F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3F, -7.625F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(35, 52).addBox(-3F, -1.5F, -5.25F, 6F, 7.5F, 6.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 69).addBox(-3.5F, -2.5F, -10F, 7F, 6.5F, 5.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("poll", CubeListBuilder.create().texOffs(0, 136).addBox(-2.5F, -3.5F, -9F, 5F, 1F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(0, 111).addBox(-2.75F, 1.5F, -12.25F, 5.5F, 4.5F, 2.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(38, 102).addBox(-2.25F, 4F, -9.5F, 4.5F, 2.5F, 4.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("forelock", CubeListBuilder.create().texOffs(0, 102).addBox(-3F, -4F, -10.5F, 6F, 5.5F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("beardc", CubeListBuilder.create().texOffs(8, 124).addBox(-2.5F, 6F, -9F, 5F, 1.5F, 4.25F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(3.5F, -0.5F, -6.5F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(35, 136).addBox(0F, -1F, -0.25F, 3.5F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.5F, -0.5F, -6.5F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(43, 136).addBox(-3.5F, -1F, -0.25F, 3.5F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(3F, -1.75F, -7.5F, 0F, 0.1F, 0.05F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(15, 136).addBox(0F, -0.75F, -0.75F, 3.5F, 1.5F, 1.5F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(2.925F, 0F, 0F, 0F, 0.35F, -0.55F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(18, 140).addBox(0F, -0.5F, -0.5F, 3.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l2 = p_horn_l1.addOrReplaceChild("horn_l2", CubeListBuilder.create(), PartPose.offsetAndRotation(3.1F, 0F, 0F, 0F, 0.25F, -0.65F));
        p_horn_l2.addOrReplaceChild("horn_l2_b", CubeListBuilder.create().texOffs(36, 140).addBox(0F, -0.25F, -0.25F, 3F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-3F, -1.75F, -7.5F, 0F, -0.1F, -0.05F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(25, 136).addBox(-3.5F, -0.75F, -0.75F, 3.5F, 1.5F, 1.5F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.925F, 0F, 0F, 0F, -0.35F, 0.55F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(27, 140).addBox(-3.5F, -0.5F, -0.5F, 3.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r2 = p_horn_r1.addOrReplaceChild("horn_r2", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.1F, 0F, 0F, 0F, -0.25F, 0.65F));
        p_horn_r2.addOrReplaceChild("horn_r2_b", CubeListBuilder.create().texOffs(43, 140).addBox(-2.75F, -0.25F, -0.25F, 3F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.5F, 14.75F, -6.125F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(17, 92).addBox(-2F, -0.5F, -2F, 4F, 4.75F, 4F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(40, 111).addBox(-1.5F, 4.25F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(0, 130).addBox(-1.75F, 7.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_fur", CubeListBuilder.create().texOffs(32, 118).addBox(-2F, 4.75F, -2F, 4F, 2F, 4F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.5F, 14.75F, -6.125F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(33, 92).addBox(-2F, -0.5F, -2F, 4F, 4.75F, 4F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(52, 111).addBox(-1.5F, 4.25F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(15, 130).addBox(-1.75F, 7.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_fur", CubeListBuilder.create().texOffs(48, 118).addBox(-2F, 4.75F, -2F, 4F, 2F, 4F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.5F, 14.75F, 6.125F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(38, 81).addBox(-2F, -0.5F, -2.5F, 4F, 4.75F, 4.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(16, 111).addBox(-1.5F, 4.25F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(27, 124).addBox(-1.75F, 7.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_fur", CubeListBuilder.create().texOffs(0, 118).addBox(-2F, 4.75F, -2F, 4F, 2F, 4F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.5F, 14.75F, 6.125F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(0, 92).addBox(-2F, -0.5F, -2.5F, 4F, 4.75F, 4.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(28, 111).addBox(-1.5F, 4.25F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(42, 124).addBox(-1.75F, 7.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_fur", CubeListBuilder.create().texOffs(16, 118).addBox(-2F, 4.75F, -2F, 4F, 2F, 4F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -4.75F, 9.625F, 0.12F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(25, 69).addBox(-0.5F, 0F, -0.5F, 1F, 10F, 1F), PartPose.ZERO);
        p_tail.addOrReplaceChild("switch", CubeListBuilder.create().texOffs(0, 124).addBox(-1F, 7F, -1F, 2F, 4F, 2F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 256);
    }

    /** cow_highland_m */
    private static LayerDefinition m7() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 10.25F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 18).addBox(-5.75F, -5F, -9F, 11.5F, 11F, 6F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-6.25F, -4.5F, -3.25F, 12.5F, 11F, 6.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 52).addBox(-5.75F, -5.5F, 3.25F, 11.5F, 10.5F, 6F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_l", CubeListBuilder.create().texOffs(39, 141).addBox(3.25F, -6F, 3.75F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_r", CubeListBuilder.create().texOffs(46, 141).addBox(-5.25F, -6F, 3.75F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("brisket", CubeListBuilder.create().texOffs(15, 130).addBox(-4.25F, 6F, -8.5F, 8.5F, 1F, 4.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(0, 136).addBox(-4.75F, -6F, -8.5F, 9.5F, 1F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 35).addBox(-6.75F, -4F, -2.75F, 13.5F, 11.5F, 5.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(0, 81).addBox(-5.25F, -6.5F, -5.25F, 10.5F, 1.75F, 8.5F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3F, -7.625F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(35, 52).addBox(-3F, -1.5F, -5.25F, 6F, 7.5F, 6.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 69).addBox(-3.5F, -2.5F, -10F, 7F, 6.5F, 5.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("poll", CubeListBuilder.create().texOffs(27, 136).addBox(-2.5F, -3.5F, -9F, 5F, 1F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(36, 102).addBox(-2.75F, 1.5F, -12.25F, 5.5F, 4.5F, 2.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(18, 102).addBox(-2.25F, 4F, -9.5F, 4.5F, 2.5F, 4.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("nose_pad", CubeListBuilder.create().texOffs(12, 141).addBox(-2.5F, 2.5F, -12.75F, 5F, 2.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring0", CubeListBuilder.create().texOffs(31, 144).addBox(-0.5F, 4F, -13.25F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring1", CubeListBuilder.create().texOffs(34, 144).addBox(-1F, 4.5F, -13.25F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring2", CubeListBuilder.create().texOffs(36, 144).addBox(0.5F, 4.5F, -13.25F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring3", CubeListBuilder.create().texOffs(11, 144).addBox(-1.5F, 5F, -13.25F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring4", CubeListBuilder.create().texOffs(13, 144).addBox(1F, 5F, -13.25F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring5", CubeListBuilder.create().texOffs(38, 144).addBox(-1F, 6F, -13.25F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring6", CubeListBuilder.create().texOffs(40, 144).addBox(0.5F, 6F, -13.25F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring7", CubeListBuilder.create().texOffs(42, 144).addBox(-0.5F, 6.5F, -13.25F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(41, 130).addBox(-2.5F, -2.5F, -4.25F, 5F, 1F, 4.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("forelock", CubeListBuilder.create().texOffs(0, 102).addBox(-3F, -4F, -10.5F, 6F, 5.5F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("beardc", CubeListBuilder.create().texOffs(0, 124).addBox(-2.5F, 6F, -9F, 5F, 1.5F, 4.25F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(3.5F, -0.5F, -6.5F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(23, 141).addBox(0F, -1F, -0.25F, 3.5F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.5F, -0.5F, -6.5F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(31, 141).addBox(-3.5F, -1F, -0.25F, 3.5F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(3F, -1.75F, -7.5F, 0F, 0.1F, 0.05F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(42, 136).addBox(0F, -0.75F, -0.75F, 4.25F, 1.5F, 1.5F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(3.788F, 0F, 0F, 0F, 0.35F, -0.55F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(53, 141).addBox(0F, -0.5F, -0.5F, 4.25F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l2 = p_horn_l1.addOrReplaceChild("horn_l2", CubeListBuilder.create(), PartPose.offsetAndRotation(3.962F, 0F, 0F, 0F, 0.25F, -0.65F));
        p_horn_l2.addOrReplaceChild("horn_l2_b", CubeListBuilder.create().texOffs(15, 144).addBox(0F, -0.25F, -0.25F, 3.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-3F, -1.75F, -7.5F, 0F, -0.1F, -0.05F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(0, 141).addBox(-4.25F, -0.75F, -0.75F, 4.25F, 1.5F, 1.5F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.788F, 0F, 0F, 0F, -0.35F, 0.55F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(0, 144).addBox(-4.25F, -0.5F, -0.5F, 4.25F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r2 = p_horn_r1.addOrReplaceChild("horn_r2", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.962F, 0F, 0F, 0F, -0.25F, 0.65F));
        p_horn_r2.addOrReplaceChild("horn_r2_b", CubeListBuilder.create().texOffs(23, 144).addBox(-3.5F, -0.25F, -0.25F, 3.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.5F, 14.75F, -6.125F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(17, 92).addBox(-2F, -0.5F, -2F, 4F, 4.75F, 4F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(12, 111).addBox(-1.5F, 4.25F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(49, 124).addBox(-1.75F, 7.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_fur", CubeListBuilder.create().texOffs(16, 118).addBox(-2F, 4.75F, -2F, 4F, 2F, 4F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.5F, 14.75F, -6.125F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(33, 92).addBox(-2F, -0.5F, -2F, 4F, 4.75F, 4F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(24, 111).addBox(-1.5F, 4.25F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(0, 130).addBox(-1.75F, 7.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_fur", CubeListBuilder.create().texOffs(32, 118).addBox(-2F, 4.75F, -2F, 4F, 2F, 4F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.5F, 14.75F, 6.125F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(38, 81).addBox(-2F, -0.5F, -2.5F, 4F, 4.75F, 4.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(52, 102).addBox(-1.5F, 4.25F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(19, 124).addBox(-1.75F, 7.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_fur", CubeListBuilder.create().texOffs(36, 111).addBox(-2F, 4.75F, -2F, 4F, 2F, 4F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.5F, 14.75F, 6.125F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(0, 92).addBox(-2F, -0.5F, -2.5F, 4F, 4.75F, 4.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(0, 111).addBox(-1.5F, 4.25F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(34, 124).addBox(-1.75F, 7.75F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_fur", CubeListBuilder.create().texOffs(0, 118).addBox(-2F, 4.75F, -2F, 4F, 2F, 4F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -4.75F, 9.625F, 0.12F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(25, 69).addBox(-0.5F, 0F, -0.5F, 1F, 10F, 1F), PartPose.ZERO);
        p_tail.addOrReplaceChild("switch", CubeListBuilder.create().texOffs(48, 118).addBox(-1F, 7F, -1F, 2F, 4F, 2F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 256);
    }

    /** cow_angus_f */
    private static LayerDefinition m8() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 8.75F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 19).addBox(-6F, -5.25F, -9.5F, 12F, 11.5F, 6F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-6.5F, -4.75F, -3.5F, 13F, 11.5F, 7F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 37).addBox(-6F, -5.75F, 3.5F, 12F, 11F, 6F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_l", CubeListBuilder.create().texOffs(45, 97).addBox(3.5F, -6.25F, 4F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_r", CubeListBuilder.create().texOffs(52, 97).addBox(-5.5F, -6.25F, 4F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("brisket", CubeListBuilder.create().texOffs(30, 91).addBox(-4.5F, 6.25F, -9F, 9F, 1F, 4.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("udder", CubeListBuilder.create().texOffs(32, 66).addBox(-2.5F, 5.75F, 1.5F, 5F, 3F, 5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0", CubeListBuilder.create().texOffs(29, 97).addBox(1F, 8.75F, 1.5F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1", CubeListBuilder.create().texOffs(33, 97).addBox(-2F, 8.75F, 1.5F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2", CubeListBuilder.create().texOffs(37, 97).addBox(1F, 8.75F, 4.5F, 1F, 1.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3", CubeListBuilder.create().texOffs(41, 97).addBox(-2F, 8.75F, 4.5F, 1F, 1.5F, 1F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.25F, -8F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(36, 37).addBox(-3F, -1.5F, -5.25F, 6F, 7.5F, 6.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 54).addBox(-3.5F, -2.5F, -10F, 7F, 6.5F, 5.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("poll", CubeListBuilder.create().texOffs(0, 97).addBox(-2.5F, -3.5F, -9F, 5F, 1F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(36, 76).addBox(-2.75F, 1.5F, -12.5F, 5.5F, 4.5F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 84).addBox(-2.25F, 4F, -9.5F, 4.5F, 2.5F, 4.25F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(3.5F, -0.5F, -6.5F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(15, 97).addBox(0F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.5F, -0.5F, -6.5F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(22, 97).addBox(-3F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.75F, 13.5F, -6.5F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(0, 66).addBox(-2F, -0.5F, -2F, 4F, 5.25F, 4F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(12, 76).addBox(-1.5F, 4.75F, -1.5F, 3F, 4.25F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(0, 91).addBox(-1.75F, 9F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.75F, 13.5F, -6.5F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(16, 66).addBox(-2F, -0.5F, -2F, 4F, 5.25F, 4F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(24, 76).addBox(-1.5F, 4.75F, -1.5F, 3F, 4.25F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(15, 91).addBox(-1.75F, 9F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.75F, 13.5F, 6.5F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(29, 54).addBox(-2F, -0.5F, -2.5F, 4F, 5.25F, 4.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(52, 66).addBox(-1.5F, 4.75F, -1.5F, 3F, 4.25F, 3F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(26, 84).addBox(-1.75F, 9F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.75F, 13.5F, 6.5F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(46, 54).addBox(-2F, -0.5F, -2.5F, 4F, 5.25F, 4.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(0, 76).addBox(-1.5F, 4.75F, -1.5F, 3F, 4.25F, 3F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(41, 84).addBox(-1.75F, 9F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -5F, 10F, 0.12F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(25, 54).addBox(-0.5F, 0F, -0.5F, 1F, 10.75F, 1F), PartPose.ZERO);
        p_tail.addOrReplaceChild("switch", CubeListBuilder.create().texOffs(18, 84).addBox(-1F, 7.75F, -1F, 2F, 4F, 2F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** cow_angus_m */
    private static LayerDefinition m9() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 8.75F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 19).addBox(-6F, -5.25F, -9.5F, 12F, 11.5F, 6F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-6.5F, -4.75F, -3.5F, 13F, 11.5F, 7F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 37).addBox(-6F, -5.75F, 3.5F, 12F, 11F, 6F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_l", CubeListBuilder.create().texOffs(7, 101).addBox(3.5F, -6.25F, 4F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hook_r", CubeListBuilder.create().texOffs(14, 101).addBox(-5.5F, -6.25F, 4F, 2F, 0.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("brisket", CubeListBuilder.create().texOffs(15, 90).addBox(-4.5F, 6.25F, -9F, 9F, 1F, 4.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(0, 96).addBox(-5F, -6.25F, -9F, 10F, 1F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.25F, -8F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(36, 37).addBox(-3F, -1.5F, -5.25F, 6F, 7.5F, 6.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 54).addBox(-3.5F, -2.5F, -10F, 7F, 6.5F, 5.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("poll", CubeListBuilder.create().texOffs(28, 96).addBox(-2.5F, -3.5F, -9F, 5F, 1F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(24, 76).addBox(-2.75F, 1.5F, -12.5F, 5.5F, 4.5F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(40, 76).addBox(-2.25F, 4F, -9.5F, 4.5F, 2.5F, 4.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("nose_pad", CubeListBuilder.create().texOffs(43, 96).addBox(-2.5F, 2.5F, -13F, 5F, 2.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring0", CubeListBuilder.create().texOffs(25, 101).addBox(-0.5F, 4F, -13.5F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring1", CubeListBuilder.create().texOffs(28, 101).addBox(-1F, 4.5F, -13.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring2", CubeListBuilder.create().texOffs(30, 101).addBox(0.5F, 4.5F, -13.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring3", CubeListBuilder.create().texOffs(21, 101).addBox(-1.5F, 5F, -13.5F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring4", CubeListBuilder.create().texOffs(23, 101).addBox(1F, 5F, -13.5F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring5", CubeListBuilder.create().texOffs(32, 101).addBox(-1F, 6F, -13.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring6", CubeListBuilder.create().texOffs(34, 101).addBox(0.5F, 6F, -13.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("ring7", CubeListBuilder.create().texOffs(36, 101).addBox(-0.5F, 6.5F, -13.5F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(42, 90).addBox(-2.5F, -2.5F, -4.25F, 5F, 1F, 4.25F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(3.5F, -0.5F, -6.5F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(54, 96).addBox(0F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.5F, -0.5F, -6.5F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(0, 101).addBox(-3F, -1F, -0.25F, 3F, 2F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.75F, 13.5F, -6.5F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(0, 66).addBox(-2F, -0.5F, -2F, 4F, 5.25F, 4F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(0, 76).addBox(-1.5F, 4.75F, -1.5F, 3F, 4.25F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(38, 84).addBox(-1.75F, 9F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.75F, 13.5F, -6.5F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(16, 66).addBox(-2F, -0.5F, -2F, 4F, 5.25F, 4F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(12, 76).addBox(-1.5F, 4.75F, -1.5F, 3F, 4.25F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(0, 90).addBox(-1.75F, 9F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.75F, 13.5F, 6.5F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(29, 54).addBox(-2F, -0.5F, -2.5F, 4F, 5.25F, 4.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(32, 66).addBox(-1.5F, 4.75F, -1.5F, 3F, 4.25F, 3F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(8, 84).addBox(-1.75F, 9F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.75F, 13.5F, 6.5F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(46, 54).addBox(-2F, -0.5F, -2.5F, 4F, 5.25F, 4.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(44, 66).addBox(-1.5F, 4.75F, -1.5F, 3F, 4.25F, 3F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(23, 84).addBox(-1.75F, 9F, -2.25F, 3.5F, 1.5F, 4F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -5F, 10F, 0.12F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(25, 54).addBox(-0.5F, 0F, -0.5F, 1F, 10.75F, 1F), PartPose.ZERO);
        p_tail.addOrReplaceChild("switch", CubeListBuilder.create().texOffs(0, 84).addBox(-1F, 7.75F, -1F, 2F, 4F, 2F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** sheep_merino_f */
    private static LayerDefinition m10() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 13F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -3.25F, -6.5F, 7F, 6.5F, 13F), PartPose.ZERO);
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(40, 0).addBox(-3F, -2.75F, -7.5F, 6F, 6.5F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.75F, -6F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 20).addBox(-2F, -2.5F, -4F, 4F, 5.5F, 4.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(17, 20).addBox(-2.5F, -5F, -7.5F, 5F, 5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(15, 30).addBox(-1.75F, -2.5F, -9.5F, 3.5F, 3F, 2F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, -4F, -5F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(27, 37).addBox(0F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, -4F, -5F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(33, 37).addBox(-2.5F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.25F, 15.75F, -4.25F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(51, 20).addBox(-1F, -0.5F, -1F, 2F, 4.25F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(38, 30).addBox(-0.75F, 3.75F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(9, 37).addBox(-1F, 6.75F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.25F, 15.75F, -4.25F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 30).addBox(-1F, -0.5F, -1F, 2F, 4.25F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(44, 30).addBox(-0.75F, 3.75F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(18, 37).addBox(-1F, 6.75F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.25F, 15.75F, 4.25F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 20).addBox(-1F, -0.5F, -1F, 2F, 4.25F, 2F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(26, 30).addBox(-0.75F, 3.75F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(50, 30).addBox(-1F, 6.75F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.25F, 15.75F, 4.25F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(43, 20).addBox(-1F, -0.5F, -1F, 2F, 4.25F, 2F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(32, 30).addBox(-0.75F, 3.75F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 37).addBox(-1F, 6.75F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.5F, 6.75F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(8, 30).addBox(-1F, 0F, -0.5F, 2F, 4F, 1.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** sheep_merino_m */
    private static LayerDefinition m11() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 13F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -3.25F, -6.5F, 7F, 6.5F, 13F), PartPose.ZERO);
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(40, 0).addBox(-3F, -2.75F, -7.5F, 6F, 6.5F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.75F, -6F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 20).addBox(-2F, -2.5F, -4F, 4F, 5.5F, 4.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(17, 20).addBox(-2.5F, -5F, -7.5F, 5F, 5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(15, 30).addBox(-1.75F, -2.5F, -9.5F, 3.5F, 3F, 2F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, -4F, -5F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(43, 37).addBox(0F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, -4F, -5F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(49, 37).addBox(-2.5F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, -4.75F, -5.5F, 0F, -0.8F, -0.8F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(27, 37).addBox(0F, -0.75F, -0.75F, 2.5F, 1.5F, 1.5F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.975F, 0F, 0F, 0F, -1.2F, 0F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(55, 37).addBox(0F, -0.5F, -0.5F, 2.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l2 = p_horn_l1.addOrReplaceChild("horn_l2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.975F, 0F, 0F, 0F, -1.2F, 0F));
        p_horn_l2.addOrReplaceChild("horn_l2_b", CubeListBuilder.create().texOffs(19, 41).addBox(0F, -0.25F, -0.25F, 2.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l3 = p_horn_l2.addOrReplaceChild("horn_l3", CubeListBuilder.create(), PartPose.offsetAndRotation(2.15F, 0F, 0F, 0F, -1F, 0F));
        p_horn_l3.addOrReplaceChild("horn_l3_b", CubeListBuilder.create().texOffs(0, 41).addBox(0F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, -4.75F, -5.5F, 0F, 0.8F, 0.8F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(35, 37).addBox(-2.5F, -0.75F, -0.75F, 2.5F, 1.5F, 1.5F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.975F, 0F, 0F, 0F, 1.2F, 0F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(6, 41).addBox(-2.5F, -0.5F, -0.5F, 2.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r2 = p_horn_r1.addOrReplaceChild("horn_r2", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.975F, 0F, 0F, 0F, 1.2F, 0F));
        p_horn_r2.addOrReplaceChild("horn_r2_b", CubeListBuilder.create().texOffs(25, 41).addBox(-2.5F, -0.25F, -0.25F, 2.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r3 = p_horn_r2.addOrReplaceChild("horn_r3", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.15F, 0F, 0F, 0F, 1F, 0F));
        p_horn_r3.addOrReplaceChild("horn_r3_b", CubeListBuilder.create().texOffs(13, 41).addBox(-1.75F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.25F, 15.75F, -4.25F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(51, 20).addBox(-1F, -0.5F, -1F, 2F, 4.25F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(38, 30).addBox(-0.75F, 3.75F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(9, 37).addBox(-1F, 6.75F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.25F, 15.75F, -4.25F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 30).addBox(-1F, -0.5F, -1F, 2F, 4.25F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(44, 30).addBox(-0.75F, 3.75F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(18, 37).addBox(-1F, 6.75F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.25F, 15.75F, 4.25F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 20).addBox(-1F, -0.5F, -1F, 2F, 4.25F, 2F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(26, 30).addBox(-0.75F, 3.75F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(50, 30).addBox(-1F, 6.75F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.25F, 15.75F, 4.25F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(43, 20).addBox(-1F, -0.5F, -1F, 2F, 4.25F, 2F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(32, 30).addBox(-0.75F, 3.75F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 37).addBox(-1F, 6.75F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.5F, 6.75F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(8, 30).addBox(-1F, 0F, -0.5F, 2F, 4F, 1.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** sheep_suffolk_f */
    private static LayerDefinition m12() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 11.375F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -3.25F, -6.75F, 7.25F, 6.75F, 13.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(42, 0).addBox(-3F, -2.75F, -7.75F, 6.25F, 6.75F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.875F, -6.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 21).addBox(-2F, -2.5F, -4F, 4F, 5.5F, 4.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(17, 21).addBox(-2.5F, -5F, -7.5F, 5F, 5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(39, 31).addBox(-1.75F, -2.5F, -9.5F, 3.5F, 3F, 2F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, -4F, -5F, 0F, 0.2F, 0.95F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(27, 38).addBox(0F, -0.75F, -0.25F, 3F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, -4F, -5F, 0F, -0.2F, -0.95F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(34, 38).addBox(-3F, -0.75F, -0.25F, 3F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.375F, 14.25F, -4.625F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(51, 21).addBox(-1F, -0.5F, -1F, 2F, 5F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(27, 31).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(9, 38).addBox(-1F, 8.25F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.375F, 14.25F, -4.625F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 31).addBox(-1F, -0.5F, -1F, 2F, 5F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(33, 31).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(18, 38).addBox(-1F, 8.25F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.375F, 14.25F, 4.625F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 21).addBox(-1F, -0.5F, -1F, 2F, 5F, 2F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(15, 31).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(50, 31).addBox(-1F, 8.25F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.375F, 14.25F, 4.625F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(43, 21).addBox(-1F, -0.5F, -1F, 2F, 5F, 2F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(21, 31).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 38).addBox(-1F, 8.25F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.625F, 7.125F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(8, 31).addBox(-1F, 0F, -0.5F, 2F, 4F, 1.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** sheep_suffolk_m */
    private static LayerDefinition m13() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 11.375F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -3.25F, -6.75F, 7.25F, 6.75F, 13.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(42, 0).addBox(-3F, -2.75F, -7.75F, 6.25F, 6.75F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.875F, -6.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 21).addBox(-2F, -2.5F, -4F, 4F, 5.5F, 4.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(17, 21).addBox(-2.5F, -5F, -7.5F, 5F, 5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(39, 31).addBox(-1.75F, -2.5F, -9.5F, 3.5F, 3F, 2F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, -4F, -5F, 0F, 0.2F, 0.95F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(27, 38).addBox(0F, -0.75F, -0.25F, 3F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, -4F, -5F, 0F, -0.2F, -0.95F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(34, 38).addBox(-3F, -0.75F, -0.25F, 3F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.375F, 14.25F, -4.625F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(51, 21).addBox(-1F, -0.5F, -1F, 2F, 5F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(27, 31).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(9, 38).addBox(-1F, 8.25F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.375F, 14.25F, -4.625F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 31).addBox(-1F, -0.5F, -1F, 2F, 5F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(33, 31).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(18, 38).addBox(-1F, 8.25F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.375F, 14.25F, 4.625F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 21).addBox(-1F, -0.5F, -1F, 2F, 5F, 2F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(15, 31).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(50, 31).addBox(-1F, 8.25F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.375F, 14.25F, 4.625F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(43, 21).addBox(-1F, -0.5F, -1F, 2F, 5F, 2F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(21, 31).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 38).addBox(-1F, 8.25F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.625F, 7.125F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(8, 31).addBox(-1F, 0F, -0.5F, 2F, 4F, 1.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** sheep_texel_f */
    private static LayerDefinition m14() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 12.375F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-3.75F, -3.5F, -6.25F, 7.75F, 7.25F, 12.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(41, 0).addBox(-3.25F, -3F, -7.25F, 6.75F, 7.25F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.125F, -5.875F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 20).addBox(-2F, -2.5F, -4F, 4F, 5.5F, 4.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(17, 20).addBox(-2.5F, -5F, -7.5F, 5F, 5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(15, 30).addBox(-1.75F, -2.5F, -9.5F, 3.5F, 3F, 2F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, -4F, -5F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(27, 37).addBox(0F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, -4F, -5F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(33, 37).addBox(-2.5F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, 15.5F, -4.125F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(51, 20).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(38, 30).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(9, 37).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, 15.5F, -4.125F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 30).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(44, 30).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(18, 37).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, 15.5F, 4.125F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 20).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(26, 30).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(50, 30).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, 15.5F, 4.125F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(43, 20).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(32, 30).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 37).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.875F, 6.625F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(8, 30).addBox(-1F, 0F, -0.5F, 2F, 4F, 1.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** sheep_texel_m */
    private static LayerDefinition m15() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 12.375F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-3.75F, -3.5F, -6.25F, 7.75F, 7.25F, 12.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(41, 0).addBox(-3.25F, -3F, -7.25F, 6.75F, 7.25F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.125F, -5.875F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 20).addBox(-2F, -2.5F, -4F, 4F, 5.5F, 4.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(17, 20).addBox(-2.5F, -5F, -7.5F, 5F, 5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(15, 30).addBox(-1.75F, -2.5F, -9.5F, 3.5F, 3F, 2F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, -4F, -5F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(27, 37).addBox(0F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, -4F, -5F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(33, 37).addBox(-2.5F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, 15.5F, -4.125F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(51, 20).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(38, 30).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(9, 37).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, 15.5F, -4.125F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 30).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(44, 30).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(18, 37).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, 15.5F, 4.125F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 20).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(26, 30).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(50, 30).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, 15.5F, 4.125F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(43, 20).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(32, 30).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 37).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.875F, 6.625F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(8, 30).addBox(-1F, 0F, -0.5F, 2F, 4F, 1.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** sheep_ouessant_f */
    private static LayerDefinition m16() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 13F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-3.25F, -3F, -5.75F, 6.5F, 6F, 11.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(37, 0).addBox(-2.75F, -2.5F, -6.75F, 5.5F, 6F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.5F, -5.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 18).addBox(-2F, -2.5F, -4F, 4F, 5.5F, 4.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(17, 18).addBox(-2.5F, -5F, -7.5F, 5F, 5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(15, 28).addBox(-1.75F, -2.5F, -9.5F, 3.5F, 3F, 2F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, -4F, -5F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(27, 35).addBox(0F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, -4F, -5F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(33, 35).addBox(-2.5F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, 15.5F, -3.625F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(51, 18).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(38, 28).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(9, 35).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, 15.5F, -3.625F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 28).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(44, 28).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(18, 35).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, 15.5F, 3.625F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 18).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(26, 28).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(50, 28).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, 15.5F, 3.625F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(43, 18).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(32, 28).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 35).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.25F, 6.125F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(8, 28).addBox(-1F, 0F, -0.5F, 2F, 4F, 1.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** sheep_ouessant_m */
    private static LayerDefinition m17() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 13F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-3.25F, -3F, -5.75F, 6.5F, 6F, 11.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(37, 0).addBox(-2.75F, -2.5F, -6.75F, 5.5F, 6F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.5F, -5.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 18).addBox(-2F, -2.5F, -4F, 4F, 5.5F, 4.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(17, 18).addBox(-2.5F, -5F, -7.5F, 5F, 5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(15, 28).addBox(-1.75F, -2.5F, -9.5F, 3.5F, 3F, 2F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, -4F, -5F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(45, 35).addBox(0F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, -4F, -5F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(51, 35).addBox(-2.5F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, -4.75F, -5.5F, 0F, -0.8F, -0.8F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(27, 35).addBox(0F, -0.75F, -0.75F, 3F, 1.5F, 1.5F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(2.35F, 0F, 0F, 0F, -1.2F, 0F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(0, 39).addBox(0F, -0.5F, -0.5F, 3F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l2 = p_horn_l1.addOrReplaceChild("horn_l2", CubeListBuilder.create(), PartPose.offsetAndRotation(2.35F, 0F, 0F, 0F, -1.2F, 0F));
        p_horn_l2.addOrReplaceChild("horn_l2_b", CubeListBuilder.create().texOffs(30, 39).addBox(0F, -0.25F, -0.25F, 3F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l3 = p_horn_l2.addOrReplaceChild("horn_l3", CubeListBuilder.create(), PartPose.offsetAndRotation(2.525F, 0F, 0F, 0F, -1F, 0F));
        p_horn_l3.addOrReplaceChild("horn_l3_b", CubeListBuilder.create().texOffs(8, 39).addBox(0F, -0.5F, -0.5F, 2.25F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, -4.75F, -5.5F, 0F, 0.8F, 0.8F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(36, 35).addBox(-2.75F, -0.75F, -0.75F, 3F, 1.5F, 1.5F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.35F, 0F, 0F, 0F, 1.2F, 0F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(15, 39).addBox(-2.75F, -0.5F, -0.5F, 3F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r2 = p_horn_r1.addOrReplaceChild("horn_r2", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.35F, 0F, 0F, 0F, 1.2F, 0F));
        p_horn_r2.addOrReplaceChild("horn_r2_b", CubeListBuilder.create().texOffs(37, 39).addBox(-2.75F, -0.25F, -0.25F, 3F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r3 = p_horn_r2.addOrReplaceChild("horn_r3", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.525F, 0F, 0F, 0F, 1F, 0F));
        p_horn_r3.addOrReplaceChild("horn_r3_b", CubeListBuilder.create().texOffs(23, 39).addBox(-2.25F, -0.5F, -0.5F, 2.25F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, 15.5F, -3.625F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(51, 18).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(38, 28).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(9, 35).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, 15.5F, -3.625F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 28).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(44, 28).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(18, 35).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, 15.5F, 3.625F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 18).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(26, 28).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(50, 28).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, 15.5F, 3.625F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(43, 18).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(32, 28).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 35).addBox(-1F, 7F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.25F, 6.125F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(8, 28).addBox(-1F, 0F, -0.5F, 2F, 4F, 1.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** sheep_jacob_f */
    private static LayerDefinition m18() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 12.375F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-3.25F, -3F, -6.5F, 6.75F, 6.25F, 13F), PartPose.ZERO);
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(40, 0).addBox(-2.75F, -2.5F, -7.5F, 5.75F, 6.25F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.625F, -6F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 20).addBox(-2F, -2.5F, -4F, 4F, 5.5F, 4.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(17, 20).addBox(-2.5F, -5F, -7.5F, 5F, 5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(15, 30).addBox(-1.75F, -2.5F, -9.5F, 3.5F, 3F, 2F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, -4F, -5F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(39, 37).addBox(0F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, -4F, -5F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(45, 37).addBox(-2.5F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, -4.75F, -5.5F, 0F, -0.8F, -0.8F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(27, 37).addBox(0F, -0.75F, -0.75F, 1.5F, 1.5F, 1.5F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.975F, 0F, 0F, 0F, -1.2F, 0F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(51, 37).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l2 = p_horn_l1.addOrReplaceChild("horn_l2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.975F, 0F, 0F, 0F, -1.2F, 0F));
        p_horn_l2.addOrReplaceChild("horn_l2_b", CubeListBuilder.create().texOffs(22, 41).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l3 = p_horn_l2.addOrReplaceChild("horn_l3", CubeListBuilder.create(), PartPose.offsetAndRotation(1.15F, 0F, 0F, 0F, -1F, 0F));
        p_horn_l3.addOrReplaceChild("horn_l3_b", CubeListBuilder.create().texOffs(56, 37).addBox(0F, -0.5F, -0.5F, 1.25F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, -4.75F, -5.5F, 0F, 0.8F, 0.8F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(33, 37).addBox(-1.5F, -0.75F, -0.75F, 1.5F, 1.5F, 1.5F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.975F, 0F, 0F, 0F, 1.2F, 0F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(0, 41).addBox(-1.5F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r2 = p_horn_r1.addOrReplaceChild("horn_r2", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.975F, 0F, 0F, 0F, 1.2F, 0F));
        p_horn_r2.addOrReplaceChild("horn_r2_b", CubeListBuilder.create().texOffs(26, 41).addBox(-1.5F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r3 = p_horn_r2.addOrReplaceChild("horn_r3", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.15F, 0F, 0F, 0F, 1F, 0F));
        p_horn_r3.addOrReplaceChild("horn_r3_b", CubeListBuilder.create().texOffs(5, 41).addBox(-1F, -0.5F, -0.5F, 1.25F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_hornu_l0 = p_head.addOrReplaceChild("hornu_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, -5F, -6.5F, 0F, 0.2F, -1.1F));
        p_hornu_l0.addOrReplaceChild("hornu_l0_b", CubeListBuilder.create().texOffs(10, 41).addBox(0F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_hornu_l1 = p_hornu_l0.addOrReplaceChild("hornu_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.65F, 0F, 0F, 0F, 0.1F, -0.4F));
        p_hornu_l1.addOrReplaceChild("hornu_l1_b", CubeListBuilder.create().texOffs(30, 41).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_hornu_r0 = p_head.addOrReplaceChild("hornu_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, -5F, -6.5F, 0F, -0.2F, 1.1F));
        p_hornu_r0.addOrReplaceChild("hornu_r0_b", CubeListBuilder.create().texOffs(16, 41).addBox(-2F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_hornu_r1 = p_hornu_r0.addOrReplaceChild("hornu_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.65F, 0F, 0F, 0F, -0.1F, 0.4F));
        p_hornu_r1.addOrReplaceChild("hornu_r1_b", CubeListBuilder.create().texOffs(34, 41).addBox(-1.5F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.125F, 15F, -4.25F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(51, 20).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(38, 30).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.25F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(9, 37).addBox(-1F, 7.5F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.125F, 15F, -4.25F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 30).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(44, 30).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.25F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(18, 37).addBox(-1F, 7.5F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.125F, 15F, 4.25F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 20).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(26, 30).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.25F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(50, 30).addBox(-1F, 7.5F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.125F, 15F, 4.25F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(43, 20).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(32, 30).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.25F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 37).addBox(-1F, 7.5F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.375F, 6.75F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(8, 30).addBox(-1F, 0F, -0.5F, 2F, 4F, 1.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** sheep_jacob_m */
    private static LayerDefinition m19() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 12.375F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-3.25F, -3F, -6.5F, 6.75F, 6.25F, 13F), PartPose.ZERO);
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(40, 0).addBox(-2.75F, -2.5F, -7.5F, 5.75F, 6.25F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.625F, -6F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 20).addBox(-2F, -2.5F, -4F, 4F, 5.5F, 4.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(17, 20).addBox(-2.5F, -5F, -7.5F, 5F, 5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(15, 30).addBox(-1.75F, -2.5F, -9.5F, 3.5F, 3F, 2F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, -4F, -5F, 0F, 0.15F, 0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(41, 37).addBox(0F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, -4F, -5F, 0F, -0.15F, -0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(47, 37).addBox(-2.5F, -0.75F, -0.25F, 2.5F, 1.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, -4.75F, -5.5F, 0F, -0.8F, -0.8F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(27, 37).addBox(0F, -0.75F, -0.75F, 2F, 1.5F, 1.5F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.35F, 0F, 0F, 0F, -1.2F, 0F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(53, 37).addBox(0F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l2 = p_horn_l1.addOrReplaceChild("horn_l2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.35F, 0F, 0F, 0F, -1.2F, 0F));
        p_horn_l2.addOrReplaceChild("horn_l2_b", CubeListBuilder.create().texOffs(23, 41).addBox(0F, -0.25F, -0.25F, 2F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l3 = p_horn_l2.addOrReplaceChild("horn_l3", CubeListBuilder.create(), PartPose.offsetAndRotation(1.525F, 0F, 0F, 0F, -1F, 0F));
        p_horn_l3.addOrReplaceChild("horn_l3_b", CubeListBuilder.create().texOffs(59, 37).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, -4.75F, -5.5F, 0F, 0.8F, 0.8F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(34, 37).addBox(-1.75F, -0.75F, -0.75F, 2F, 1.5F, 1.5F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.35F, 0F, 0F, 0F, 1.2F, 0F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(0, 41).addBox(-1.75F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r2 = p_horn_r1.addOrReplaceChild("horn_r2", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.35F, 0F, 0F, 0F, 1.2F, 0F));
        p_horn_r2.addOrReplaceChild("horn_r2_b", CubeListBuilder.create().texOffs(28, 41).addBox(-1.75F, -0.25F, -0.25F, 2F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r3 = p_horn_r2.addOrReplaceChild("horn_r3", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.525F, 0F, 0F, 0F, 1F, 0F));
        p_horn_r3.addOrReplaceChild("horn_r3_b", CubeListBuilder.create().texOffs(6, 41).addBox(-1.5F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_hornu_l0 = p_head.addOrReplaceChild("hornu_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, -5F, -6.5F, 0F, 0.2F, -1.1F));
        p_hornu_l0.addOrReplaceChild("hornu_l0_b", CubeListBuilder.create().texOffs(11, 41).addBox(0F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_hornu_l1 = p_hornu_l0.addOrReplaceChild("hornu_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.65F, 0F, 0F, 0F, 0.1F, -0.4F));
        p_hornu_l1.addOrReplaceChild("hornu_l1_b", CubeListBuilder.create().texOffs(33, 41).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_hornu_r0 = p_head.addOrReplaceChild("hornu_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, -5F, -6.5F, 0F, -0.2F, 1.1F));
        p_hornu_r0.addOrReplaceChild("hornu_r0_b", CubeListBuilder.create().texOffs(17, 41).addBox(-2F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_hornu_r1 = p_hornu_r0.addOrReplaceChild("hornu_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.65F, 0F, 0F, 0F, -0.1F, 0.4F));
        p_hornu_r1.addOrReplaceChild("hornu_r1_b", CubeListBuilder.create().texOffs(37, 41).addBox(-1.5F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.125F, 15F, -4.25F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(51, 20).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(38, 30).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.25F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(9, 37).addBox(-1F, 7.5F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.125F, 15F, -4.25F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 30).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(44, 30).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.25F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(18, 37).addBox(-1F, 7.5F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.125F, 15F, 4.25F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 20).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(26, 30).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.25F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(50, 30).addBox(-1F, 7.5F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.125F, 15F, 4.25F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(43, 20).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(32, 30).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.25F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 37).addBox(-1F, 7.5F, -1.5F, 2F, 1.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.375F, 6.75F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(8, 30).addBox(-1F, 0F, -0.5F, 2F, 4F, 1.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** pig_large_white_f */
    private static LayerDefinition m20() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 14F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -4F, -8.25F, 9F, 8F, 16.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(0, 25).addBox(-4F, 4F, -6.25F, 8F, 1F, 12.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("jowl", CubeListBuilder.create().texOffs(24, 39).addBox(-4F, -3F, -9.75F, 8F, 7.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0l", CubeListBuilder.create().texOffs(54, 64).addBox(1F, 5F, -5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0r", CubeListBuilder.create().texOffs(56, 64).addBox(-1.5F, 5F, -5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1l", CubeListBuilder.create().texOffs(58, 64).addBox(1F, 5F, -2.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1r", CubeListBuilder.create().texOffs(60, 64).addBox(-1.5F, 5F, -2.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2l", CubeListBuilder.create().texOffs(62, 64).addBox(1F, 5F, 0F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2r", CubeListBuilder.create().texOffs(0, 69).addBox(-1.5F, 5F, 0F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3l", CubeListBuilder.create().texOffs(2, 69).addBox(1F, 5F, 2.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3r", CubeListBuilder.create().texOffs(4, 69).addBox(-1.5F, 5F, 2.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -0.5F, -9.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 39).addBox(-3.5F, -3F, -5F, 7F, 7.5F, 5F), PartPose.ZERO);
        p_head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(13, 52).addBox(-2F, 0F, -8F, 4F, 4F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("disc", CubeListBuilder.create().texOffs(20, 64).addBox(-1.75F, 0.25F, -8.5F, 3.5F, 3.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, -2.5F, -2.5F, 0.15F, 0.1F, -0.75F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(28, 64).addBox(0F, -1.25F, -0.25F, 3F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, -2.5F, -2.5F, 0.15F, -0.1F, 0.75F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(35, 64).addBox(-3F, -1.25F, -0.25F, 3F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, 17.5F, -5.875F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(27, 52).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(0, 64).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(13, 59).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, 17.5F, -5.875F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(39, 52).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(10, 64).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(26, 59).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, 17.5F, 5.875F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(43, 39).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(39, 59).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(51, 52).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, 17.5F, 5.875F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(0, 52).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(49, 59).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 59).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3F, 8.625F, -0.6F, 0F, 0F));
        PartDefinition p_curl0 = p_tail.addOrReplaceChild("curl0", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.57F, 0F));
        p_curl0.addOrReplaceChild("curl0_b", CubeListBuilder.create().texOffs(47, 64).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_curl1 = p_curl0.addOrReplaceChild("curl1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.3F));
        p_curl1.addOrReplaceChild("curl1_b", CubeListBuilder.create().texOffs(42, 64).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_curl2 = p_curl1.addOrReplaceChild("curl2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.4F));
        p_curl2.addOrReplaceChild("curl2_b", CubeListBuilder.create().texOffs(51, 64).addBox(0F, -0.25F, -0.25F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** pig_large_white_m */
    private static LayerDefinition m21() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 14F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -4F, -8.25F, 9F, 8F, 16.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(0, 25).addBox(-4F, 4F, -6.25F, 8F, 1F, 12.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("jowl", CubeListBuilder.create().texOffs(0, 52).addBox(-4F, -3F, -9.75F, 8F, 7.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("ham_l", CubeListBuilder.create().texOffs(24, 39).addBox(4.5F, -3F, 3.5F, 0.5F, 6.5F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("ham_r", CubeListBuilder.create().texOffs(34, 39).addBox(-5F, -3F, 3.5F, 0.5F, 6.5F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("shield_l", CubeListBuilder.create().texOffs(44, 39).addBox(4.5F, -3F, -7.75F, 0.5F, 6F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("shield_r", CubeListBuilder.create().texOffs(53, 39).addBox(-5F, -3F, -7.75F, 0.5F, 6F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -0.5F, -9.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 39).addBox(-3.5F, -3F, -5F, 7F, 7.5F, 5F), PartPose.ZERO);
        p_head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(45, 52).addBox(-2F, 0F, -8F, 4F, 4F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("disc", CubeListBuilder.create().texOffs(53, 68).addBox(-1.75F, 0.25F, -8.5F, 3.5F, 3.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("tusk_l", CubeListBuilder.create().texOffs(14, 73).addBox(2F, 2.5F, -7.5F, 0.5F, 1.5F, 1F), PartPose.ZERO);
        p_head.addOrReplaceChild("tusk_r", CubeListBuilder.create().texOffs(17, 73).addBox(-2.5F, 2.5F, -7.5F, 0.5F, 1.5F, 1F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, -2.5F, -2.5F, 0.15F, 0.1F, -0.75F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(0, 73).addBox(0F, -1.25F, -0.25F, 3F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, -2.5F, -2.5F, 0.15F, -0.1F, 0.75F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(7, 73).addBox(-3F, -1.25F, -0.25F, 3F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, 17.5F, -5.875F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(0, 61).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(33, 68).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(50, 61).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, 17.5F, -5.875F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(12, 61).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(43, 68).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(0, 68).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, 17.5F, 5.875F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(19, 52).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(13, 68).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(24, 61).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, 17.5F, 5.875F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(32, 52).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(23, 68).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(37, 61).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3F, 8.625F, -0.6F, 0F, 0F));
        PartDefinition p_curl0 = p_tail.addOrReplaceChild("curl0", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.57F, 0F));
        p_curl0.addOrReplaceChild("curl0_b", CubeListBuilder.create().texOffs(25, 73).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_curl1 = p_curl0.addOrReplaceChild("curl1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.3F));
        p_curl1.addOrReplaceChild("curl1_b", CubeListBuilder.create().texOffs(20, 73).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_curl2 = p_curl1.addOrReplaceChild("curl2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.4F));
        p_curl2.addOrReplaceChild("curl2_b", CubeListBuilder.create().texOffs(29, 73).addBox(0F, -0.25F, -0.25F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** pig_gascon_f */
    private static LayerDefinition m22() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 13.875F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-4.25F, -3.75F, -7.5F, 8.75F, 7.75F, 15F), PartPose.ZERO);
        p_body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(0, 23).addBox(-3.75F, 4F, -5.5F, 7.75F, 1F, 11F), PartPose.ZERO);
        p_body.addOrReplaceChild("jowl", CubeListBuilder.create().texOffs(0, 35).addBox(-3.75F, -2.75F, -9F, 7.75F, 7.25F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0l", CubeListBuilder.create().texOffs(20, 56).addBox(1F, 5F, -4.25F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0r", CubeListBuilder.create().texOffs(22, 56).addBox(-1.5F, 5F, -4.25F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1l", CubeListBuilder.create().texOffs(24, 56).addBox(1F, 5F, -1.75F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1r", CubeListBuilder.create().texOffs(26, 56).addBox(-1.5F, 5F, -1.75F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2l", CubeListBuilder.create().texOffs(28, 56).addBox(1F, 5F, 0.75F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2r", CubeListBuilder.create().texOffs(30, 56).addBox(-1.5F, 5F, 0.75F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3l", CubeListBuilder.create().texOffs(32, 56).addBox(1F, 5F, 3.25F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3r", CubeListBuilder.create().texOffs(34, 56).addBox(-1.5F, 5F, 3.25F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -0.375F, -8.5F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(38, 23).addBox(-3.5F, -3F, -4.75F, 7F, 7.25F, 4.75F), PartPose.ZERO);
        p_head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(45, 35).addBox(-2F, 0F, -8F, 4F, 3.75F, 3.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("disc", CubeListBuilder.create().texOffs(44, 51).addBox(-1.75F, 0.25F, -8.5F, 3.5F, 3.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, -2.5F, -2.25F, -0.45F, 0.25F, 1.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(52, 51).addBox(0F, -1.25F, -0.25F, 3.5F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, -2.5F, -2.25F, -0.45F, -0.25F, -1.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(0, 56).addBox(-3.5F, -1.25F, -0.25F, 3.5F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, 17.25F, -5F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(0, 44).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(44, 44).addBox(-1.25F, 3F, -1.25F, 2.5F, 2.25F, 2.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(22, 51).addBox(-1.25F, 5.25F, -1.75F, 2.5F, 1.5F, 3F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, 17.25F, -5F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(12, 44).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(54, 44).addBox(-1.25F, 3F, -1.25F, 2.5F, 2.25F, 2.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(33, 51).addBox(-1.25F, 5.25F, -1.75F, 2.5F, 1.5F, 3F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, 17.25F, 5F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(19, 35).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(24, 44).addBox(-1.25F, 3F, -1.25F, 2.5F, 2.25F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(0, 51).addBox(-1.25F, 5.25F, -1.75F, 2.5F, 1.5F, 3F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, 17.25F, 5F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(32, 35).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(34, 44).addBox(-1.25F, 3F, -1.25F, 2.5F, 2.25F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(11, 51).addBox(-1.25F, 5.25F, -1.75F, 2.5F, 1.5F, 3F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.875F, 7.75F, -0.6F, 0F, 0F));
        PartDefinition p_curl0 = p_tail.addOrReplaceChild("curl0", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.57F, 0F));
        p_curl0.addOrReplaceChild("curl0_b", CubeListBuilder.create().texOffs(13, 56).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_curl1 = p_curl0.addOrReplaceChild("curl1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.3F));
        p_curl1.addOrReplaceChild("curl1_b", CubeListBuilder.create().texOffs(8, 56).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_curl2 = p_curl1.addOrReplaceChild("curl2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.4F));
        p_curl2.addOrReplaceChild("curl2_b", CubeListBuilder.create().texOffs(17, 56).addBox(0F, -0.25F, -0.25F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** pig_gascon_m */
    private static LayerDefinition m23() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 13.875F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-4.25F, -3.75F, -7.5F, 8.75F, 7.75F, 15F), PartPose.ZERO);
        p_body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(0, 23).addBox(-3.75F, 4F, -5.5F, 7.75F, 1F, 11F), PartPose.ZERO);
        p_body.addOrReplaceChild("jowl", CubeListBuilder.create().texOffs(38, 35).addBox(-3.75F, -2.75F, -9F, 7.75F, 7.25F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("ham_l", CubeListBuilder.create().texOffs(0, 35).addBox(4.5F, -2.75F, 2.5F, 0.5F, 6.25F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("ham_r", CubeListBuilder.create().texOffs(10, 35).addBox(-4.75F, -2.75F, 2.5F, 0.5F, 6.25F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("shield_l", CubeListBuilder.create().texOffs(20, 35).addBox(4.5F, -2.75F, -7F, 0.5F, 5.75F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("shield_r", CubeListBuilder.create().texOffs(29, 35).addBox(-4.75F, -2.75F, -7F, 0.5F, 5.75F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -0.375F, -8.5F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(38, 23).addBox(-3.5F, -3F, -4.75F, 7F, 7.25F, 4.75F), PartPose.ZERO);
        p_head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(26, 46).addBox(-2F, 0F, -8F, 4F, 3.75F, 3.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("disc", CubeListBuilder.create().texOffs(33, 60).addBox(-1.75F, 0.25F, -8.5F, 3.5F, 3.25F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("tusk_l", CubeListBuilder.create().texOffs(57, 60).addBox(2F, 2.25F, -7.5F, 0.5F, 1.5F, 1F), PartPose.ZERO);
        p_head.addOrReplaceChild("tusk_r", CubeListBuilder.create().texOffs(60, 60).addBox(-2.5F, 2.25F, -7.5F, 0.5F, 1.5F, 1F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, -2.5F, -2.25F, -0.45F, 0.25F, 1.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(41, 60).addBox(0F, -1.25F, -0.25F, 3.5F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, -2.5F, -2.25F, -0.45F, -0.25F, -1.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(49, 60).addBox(-3.5F, -1.25F, -0.25F, 3.5F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, 17.25F, -5F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(41, 46).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(32, 53).addBox(-1.25F, 3F, -1.25F, 2.5F, 2.25F, 2.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(11, 60).addBox(-1.25F, 5.25F, -1.75F, 2.5F, 1.5F, 3F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, 17.25F, -5F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 53).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(42, 53).addBox(-1.25F, 3F, -1.25F, 2.5F, 2.25F, 2.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(22, 60).addBox(-1.25F, 5.25F, -1.75F, 2.5F, 1.5F, 3F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, 17.25F, 5F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(0, 46).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(12, 53).addBox(-1.25F, 3F, -1.25F, 2.5F, 2.25F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(52, 53).addBox(-1.25F, 5.25F, -1.75F, 2.5F, 1.5F, 3F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, 17.25F, 5F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(13, 46).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(22, 53).addBox(-1.25F, 3F, -1.25F, 2.5F, 2.25F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 60).addBox(-1.25F, 5.25F, -1.75F, 2.5F, 1.5F, 3F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.875F, 7.75F, -0.6F, 0F, 0F));
        PartDefinition p_curl0 = p_tail.addOrReplaceChild("curl0", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.57F, 0F));
        p_curl0.addOrReplaceChild("curl0_b", CubeListBuilder.create().texOffs(5, 65).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_curl1 = p_curl0.addOrReplaceChild("curl1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.3F));
        p_curl1.addOrReplaceChild("curl1_b", CubeListBuilder.create().texOffs(0, 65).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_curl2 = p_curl1.addOrReplaceChild("curl2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.4F));
        p_curl2.addOrReplaceChild("curl2_b", CubeListBuilder.create().texOffs(9, 65).addBox(0F, -0.25F, -0.25F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** pig_duroc_f */
    private static LayerDefinition m24() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 13.625F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-4.75F, -4.25F, -7.5F, 9.75F, 8.75F, 15.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(0, 38).addBox(-4.25F, 4.5F, -5.5F, 8.75F, 1F, 11.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("jowl", CubeListBuilder.create().texOffs(36, 51).addBox(-4.25F, -3.25F, -9F, 8.75F, 8.25F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("arch", CubeListBuilder.create().texOffs(0, 51).addBox(-3.75F, -4.75F, -4.5F, 7.75F, 0.5F, 10.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0l", CubeListBuilder.create().texOffs(7, 80).addBox(1F, 5.5F, -4.25F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0r", CubeListBuilder.create().texOffs(9, 80).addBox(-1.5F, 5.5F, -4.25F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1l", CubeListBuilder.create().texOffs(11, 80).addBox(1F, 5.5F, -1.75F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1r", CubeListBuilder.create().texOffs(13, 80).addBox(-1.5F, 5.5F, -1.75F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2l", CubeListBuilder.create().texOffs(15, 80).addBox(1F, 5.5F, 0.75F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2r", CubeListBuilder.create().texOffs(17, 80).addBox(-1.5F, 5.5F, 0.75F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3l", CubeListBuilder.create().texOffs(19, 80).addBox(1F, 5.5F, 3.25F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3r", CubeListBuilder.create().texOffs(21, 80).addBox(-1.5F, 5.5F, 3.25F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -0.875F, -8.625F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 24).addBox(-3.5F, -3F, -5.5F, 7F, 8.25F, 5.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 62).addBox(-2F, 0F, -8.5F, 4F, 4.75F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("disc", CubeListBuilder.create().texOffs(52, 70).addBox(-1.75F, 0.25F, -9F, 3.5F, 4.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, -2.5F, -3F, -0.55F, 0.3F, -0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(40, 75).addBox(0F, -1.25F, -0.25F, 3.5F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, -2.5F, -3F, -0.55F, -0.3F, 0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(48, 75).addBox(-3.5F, -1.25F, -0.25F, 3.5F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.125F, 17.5F, -5.125F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(40, 62).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(20, 75).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(26, 70).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.125F, 17.5F, -5.125F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(52, 62).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(30, 75).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(39, 70).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.125F, 17.5F, 5.125F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(14, 62).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(0, 75).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(0, 70).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.125F, 17.5F, 5.125F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(27, 62).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(10, 75).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(13, 70).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.375F, 7.875F, -0.6F, 0F, 0F));
        PartDefinition p_curl0 = p_tail.addOrReplaceChild("curl0", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.57F, 0F));
        p_curl0.addOrReplaceChild("curl0_b", CubeListBuilder.create().texOffs(0, 80).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_curl1 = p_curl0.addOrReplaceChild("curl1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.3F));
        p_curl1.addOrReplaceChild("curl1_b", CubeListBuilder.create().texOffs(56, 75).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_curl2 = p_curl1.addOrReplaceChild("curl2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.4F));
        p_curl2.addOrReplaceChild("curl2_b", CubeListBuilder.create().texOffs(4, 80).addBox(0F, -0.25F, -0.25F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** pig_duroc_m */
    private static LayerDefinition m25() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 13.625F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-4.75F, -4.25F, -7.5F, 9.75F, 8.75F, 15.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(0, 38).addBox(-4.25F, 4.5F, -5.5F, 8.75F, 1F, 11.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("jowl", CubeListBuilder.create().texOffs(0, 62).addBox(-4.25F, -3.25F, -9F, 8.75F, 8.25F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("arch", CubeListBuilder.create().texOffs(0, 51).addBox(-3.75F, -4.75F, -4.5F, 7.75F, 0.5F, 10.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("ham_l", CubeListBuilder.create().texOffs(40, 38).addBox(5F, -3.25F, 2.75F, 0.5F, 7.25F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("ham_r", CubeListBuilder.create().texOffs(50, 38).addBox(-5.25F, -3.25F, 2.75F, 0.5F, 7.25F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("shield_l", CubeListBuilder.create().texOffs(36, 51).addBox(5F, -3.25F, -7F, 0.5F, 6.75F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("shield_r", CubeListBuilder.create().texOffs(45, 51).addBox(-5.25F, -3.25F, -7F, 0.5F, 6.75F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -0.875F, -8.625F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 24).addBox(-3.5F, -3F, -5.5F, 7F, 8.25F, 5.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(21, 62).addBox(-2F, 0F, -8.5F, 4F, 4.75F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("disc", CubeListBuilder.create().texOffs(13, 79).addBox(-1.75F, 0.25F, -9F, 3.5F, 4.25F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("tusk_l", CubeListBuilder.create().texOffs(16, 84).addBox(2F, 3.25F, -8F, 0.5F, 1.5F, 1F), PartPose.ZERO);
        p_head.addOrReplaceChild("tusk_r", CubeListBuilder.create().texOffs(19, 84).addBox(-2.5F, 3.25F, -8F, 0.5F, 1.5F, 1F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, -2.5F, -3F, -0.55F, 0.3F, -0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(0, 84).addBox(0F, -1.25F, -0.25F, 3.5F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, -2.5F, -3F, -0.55F, -0.3F, 0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(8, 84).addBox(-3.5F, -1.25F, -0.25F, 3.5F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.125F, 17.5F, -5.125F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(0, 72).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(41, 79).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(50, 72).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.125F, 17.5F, -5.125F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(12, 72).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(51, 79).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(0, 79).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.125F, 17.5F, 5.125F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 62).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(21, 79).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(24, 72).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.125F, 17.5F, 5.125F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(48, 62).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(31, 79).addBox(-1.25F, 3F, -1.25F, 2.5F, 2F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(37, 72).addBox(-1.5F, 5F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.375F, 7.875F, -0.6F, 0F, 0F));
        PartDefinition p_curl0 = p_tail.addOrReplaceChild("curl0", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.57F, 0F));
        p_curl0.addOrReplaceChild("curl0_b", CubeListBuilder.create().texOffs(27, 84).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_curl1 = p_curl0.addOrReplaceChild("curl1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.3F));
        p_curl1.addOrReplaceChild("curl1_b", CubeListBuilder.create().texOffs(22, 84).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_curl2 = p_curl1.addOrReplaceChild("curl2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.4F));
        p_curl2.addOrReplaceChild("curl2_b", CubeListBuilder.create().texOffs(31, 84).addBox(0F, -0.25F, -0.25F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** pig_pietrain_f */
    private static LayerDefinition m26() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 13.75F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-5F, -4.5F, -7.25F, 10F, 9F, 14.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(0, 38).addBox(-4.5F, 4.5F, -5.25F, 9F, 1F, 10.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("jowl", CubeListBuilder.create().texOffs(40, 38).addBox(-4.5F, -3.5F, -8.75F, 9F, 8.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("ham_l", CubeListBuilder.create().texOffs(25, 24).addBox(5F, -3.5F, 2.5F, 0.5F, 7.5F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("ham_r", CubeListBuilder.create().texOffs(35, 24).addBox(-5.5F, -3.5F, 2.5F, 0.5F, 7.5F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0l", CubeListBuilder.create().texOffs(3, 68).addBox(1F, 5.5F, -4F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0r", CubeListBuilder.create().texOffs(5, 68).addBox(-1.5F, 5.5F, -4F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1l", CubeListBuilder.create().texOffs(7, 68).addBox(1F, 5.5F, -1.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1r", CubeListBuilder.create().texOffs(9, 68).addBox(-1.5F, 5.5F, -1.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2l", CubeListBuilder.create().texOffs(11, 68).addBox(1F, 5.5F, 1F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2r", CubeListBuilder.create().texOffs(13, 68).addBox(-1.5F, 5.5F, 1F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3l", CubeListBuilder.create().texOffs(15, 68).addBox(1F, 5.5F, 3.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3r", CubeListBuilder.create().texOffs(17, 68).addBox(-1.5F, 5.5F, 3.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1F, -8.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 24).addBox(-3.5F, -3F, -5.5F, 7F, 8.5F, 5.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 50).addBox(-2F, 0F, -8.25F, 4F, 5F, 2.75F), PartPose.ZERO);
        p_head.addOrReplaceChild("disc", CubeListBuilder.create().texOffs(0, 58).addBox(-1.75F, 0.25F, -8.75F, 3.5F, 4.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, -2.5F, -3F, 0.15F, 0.1F, -0.75F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(40, 63).addBox(0F, -1.25F, -0.25F, 3F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, -2.5F, -3F, 0.15F, -0.1F, 0.75F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(47, 63).addBox(-3F, -1.25F, -0.25F, 3F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.25F, 17.75F, -4.875F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(40, 50).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(20, 63).addBox(-1.25F, 3F, -1.25F, 2.5F, 1.75F, 2.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(34, 58).addBox(-1.5F, 4.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.25F, 17.75F, -4.875F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(52, 50).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(30, 63).addBox(-1.25F, 3F, -1.25F, 2.5F, 1.75F, 2.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(47, 58).addBox(-1.5F, 4.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.25F, 17.75F, 4.875F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(14, 50).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(0, 63).addBox(-1.25F, 3F, -1.25F, 2.5F, 1.75F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(8, 58).addBox(-1.5F, 4.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.25F, 17.75F, 4.875F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(27, 50).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(10, 63).addBox(-1.25F, 3F, -1.25F, 2.5F, 1.75F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(21, 58).addBox(-1.5F, 4.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.5F, 7.625F, -0.6F, 0F, 0F));
        PartDefinition p_curl0 = p_tail.addOrReplaceChild("curl0", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.57F, 0F));
        p_curl0.addOrReplaceChild("curl0_b", CubeListBuilder.create().texOffs(59, 63).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_curl1 = p_curl0.addOrReplaceChild("curl1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.3F));
        p_curl1.addOrReplaceChild("curl1_b", CubeListBuilder.create().texOffs(54, 63).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_curl2 = p_curl1.addOrReplaceChild("curl2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.4F));
        p_curl2.addOrReplaceChild("curl2_b", CubeListBuilder.create().texOffs(0, 68).addBox(0F, -0.25F, -0.25F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** pig_pietrain_m */
    private static LayerDefinition m27() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 13.75F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-5F, -4.5F, -7.25F, 10F, 9F, 14.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(0, 38).addBox(-4.5F, 4.5F, -5.25F, 9F, 1F, 10.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("jowl", CubeListBuilder.create().texOffs(0, 50).addBox(-4.5F, -3.5F, -8.75F, 9F, 8.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("ham_l", CubeListBuilder.create().texOffs(25, 24).addBox(5F, -3.5F, 2.5F, 0.5F, 7.5F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("ham_r", CubeListBuilder.create().texOffs(35, 24).addBox(-5.5F, -3.5F, 2.5F, 0.5F, 7.5F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("shield_l", CubeListBuilder.create().texOffs(40, 38).addBox(5F, -3.5F, -6.75F, 0.5F, 7F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("shield_r", CubeListBuilder.create().texOffs(49, 38).addBox(-5.5F, -3.5F, -6.75F, 0.5F, 7F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1F, -8.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 24).addBox(-3.5F, -3F, -5.5F, 7F, 8.5F, 5.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(21, 50).addBox(-2F, 0F, -8.25F, 4F, 5F, 2.75F), PartPose.ZERO);
        p_head.addOrReplaceChild("disc", CubeListBuilder.create().texOffs(24, 60).addBox(-1.75F, 0.25F, -8.75F, 3.5F, 4.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("tusk_l", CubeListBuilder.create().texOffs(24, 72).addBox(2F, 3.5F, -7.75F, 0.5F, 1.5F, 1F), PartPose.ZERO);
        p_head.addOrReplaceChild("tusk_r", CubeListBuilder.create().texOffs(27, 72).addBox(-2.5F, 3.5F, -7.75F, 0.5F, 1.5F, 1F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, -2.5F, -3F, 0.15F, 0.1F, -0.75F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(10, 72).addBox(0F, -1.25F, -0.25F, 3F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, -2.5F, -3F, 0.15F, -0.1F, 0.75F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(17, 72).addBox(-3F, -1.25F, -0.25F, 3F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.25F, 17.75F, -4.875F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(0, 60).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(46, 67).addBox(-1.25F, 3F, -1.25F, 2.5F, 1.75F, 2.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(0, 67).addBox(-1.5F, 4.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.25F, 17.75F, -4.875F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(12, 60).addBox(-1.5F, -0.5F, -1.5F, 3F, 3.5F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(0, 72).addBox(-1.25F, 3F, -1.25F, 2.5F, 1.75F, 2.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(13, 67).addBox(-1.5F, 4.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.25F, 17.75F, 4.875F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 50).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(26, 67).addBox(-1.25F, 3F, -1.25F, 2.5F, 1.75F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(32, 60).addBox(-1.5F, 4.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.25F, 17.75F, 4.875F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(48, 50).addBox(-1.5F, -0.5F, -2F, 3F, 3.5F, 3.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(36, 67).addBox(-1.25F, 3F, -1.25F, 2.5F, 1.75F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(45, 60).addBox(-1.5F, 4.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.5F, 7.625F, -0.6F, 0F, 0F));
        PartDefinition p_curl0 = p_tail.addOrReplaceChild("curl0", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.57F, 0F));
        p_curl0.addOrReplaceChild("curl0_b", CubeListBuilder.create().texOffs(35, 72).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_curl1 = p_curl0.addOrReplaceChild("curl1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.3F));
        p_curl1.addOrReplaceChild("curl1_b", CubeListBuilder.create().texOffs(30, 72).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_curl2 = p_curl1.addOrReplaceChild("curl2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.4F));
        p_curl2.addOrReplaceChild("curl2_b", CubeListBuilder.create().texOffs(39, 72).addBox(0F, -0.25F, -0.25F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** pig_kunekune_f */
    private static LayerDefinition m28() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 14.75F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-5F, -4.5F, -6.25F, 10F, 9F, 12.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(0, 36).addBox(-4.5F, 4.5F, -4.25F, 9F, 1F, 8.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("jowl", CubeListBuilder.create().texOffs(25, 22).addBox(-4.5F, -3.5F, -7.75F, 9F, 8.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0l", CubeListBuilder.create().texOffs(56, 58).addBox(1F, 5.5F, -3F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0r", CubeListBuilder.create().texOffs(58, 58).addBox(-1.5F, 5.5F, -3F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1l", CubeListBuilder.create().texOffs(60, 58).addBox(1F, 5.5F, -0.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1r", CubeListBuilder.create().texOffs(62, 58).addBox(-1.5F, 5.5F, -0.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2l", CubeListBuilder.create().texOffs(0, 62).addBox(1F, 5.5F, 2F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat2r", CubeListBuilder.create().texOffs(2, 62).addBox(-1.5F, 5.5F, 2F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3l", CubeListBuilder.create().texOffs(4, 62).addBox(1F, 5.5F, 4.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat3r", CubeListBuilder.create().texOffs(6, 62).addBox(-1.5F, 5.5F, 4.5F, 0.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1F, -7.25F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 22).addBox(-3.5F, -3F, -5.5F, 7F, 8.5F, 5.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(35, 36).addBox(-2F, 0F, -7.5F, 4F, 5F, 2F), PartPose.ZERO);
        p_head.addOrReplaceChild("disc", CubeListBuilder.create().texOffs(37, 46).addBox(-1.75F, 0.25F, -8F, 3.5F, 4.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("tassel_l", CubeListBuilder.create().texOffs(36, 58).addBox(1.25F, 5.5F, -4.5F, 1F, 2F, 1F), PartPose.ZERO);
        p_head.addOrReplaceChild("tassel_r", CubeListBuilder.create().texOffs(40, 58).addBox(-2.25F, 5.5F, -4.5F, 1F, 2F, 1F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, -2.5F, -3F, -0.55F, 0.3F, -0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(20, 58).addBox(0F, -1.25F, -0.25F, 3.5F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, -2.5F, -3F, -0.55F, -0.3F, 0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(28, 58).addBox(-3.5F, -1.25F, -0.25F, 3.5F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.25F, 18.75F, -3.75F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(13, 46).addBox(-1.5F, -0.5F, -1.5F, 3F, 3F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(0, 58).addBox(-1.25F, 2.5F, -1.25F, 2.5F, 1.25F, 2.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(13, 53).addBox(-1.5F, 3.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.25F, 18.75F, -3.75F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(25, 46).addBox(-1.5F, -0.5F, -1.5F, 3F, 3F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(10, 58).addBox(-1.25F, 2.5F, -1.25F, 2.5F, 1.25F, 2.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(26, 53).addBox(-1.5F, 3.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.25F, 18.75F, 3.75F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(47, 36).addBox(-1.5F, -0.5F, -2F, 3F, 3F, 3.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(39, 53).addBox(-1.25F, 2.5F, -1.25F, 2.5F, 1.25F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(45, 46).addBox(-1.5F, 3.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.25F, 18.75F, 3.75F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(0, 46).addBox(-1.5F, -0.5F, -2F, 3F, 3F, 3.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(49, 53).addBox(-1.25F, 2.5F, -1.25F, 2.5F, 1.25F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 53).addBox(-1.5F, 3.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.5F, 6.5F, -0.6F, 0F, 0F));
        PartDefinition p_curl0 = p_tail.addOrReplaceChild("curl0", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.57F, 0F));
        p_curl0.addOrReplaceChild("curl0_b", CubeListBuilder.create().texOffs(49, 58).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_curl1 = p_curl0.addOrReplaceChild("curl1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.3F));
        p_curl1.addOrReplaceChild("curl1_b", CubeListBuilder.create().texOffs(44, 58).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_curl2 = p_curl1.addOrReplaceChild("curl2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.4F));
        p_curl2.addOrReplaceChild("curl2_b", CubeListBuilder.create().texOffs(53, 58).addBox(0F, -0.25F, -0.25F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** pig_kunekune_m */
    private static LayerDefinition m29() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 14.75F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-5F, -4.5F, -6.25F, 10F, 9F, 12.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(21, 36).addBox(-4.5F, 4.5F, -4.25F, 9F, 1F, 8.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("jowl", CubeListBuilder.create().texOffs(0, 36).addBox(-4.5F, -3.5F, -7.75F, 9F, 8.5F, 1.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("ham_l", CubeListBuilder.create().texOffs(25, 22).addBox(5F, -3.5F, 1.25F, 0.5F, 7.5F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("ham_r", CubeListBuilder.create().texOffs(35, 22).addBox(-5.5F, -3.5F, 1.25F, 0.5F, 7.5F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("shield_l", CubeListBuilder.create().texOffs(45, 22).addBox(5F, -3.5F, -5.75F, 0.5F, 7F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("shield_r", CubeListBuilder.create().texOffs(54, 22).addBox(-5.5F, -3.5F, -5.75F, 0.5F, 7F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1F, -7.25F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 22).addBox(-3.5F, -3F, -5.5F, 7F, 8.5F, 5.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 46).addBox(-2F, 0F, -7.5F, 4F, 5F, 2F), PartPose.ZERO);
        p_head.addOrReplaceChild("disc", CubeListBuilder.create().texOffs(0, 53).addBox(-1.75F, 0.25F, -8F, 3.5F, 4.5F, 0.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("tusk_l", CubeListBuilder.create().texOffs(0, 62).addBox(2F, 3.5F, -7F, 0.5F, 1.5F, 1F), PartPose.ZERO);
        p_head.addOrReplaceChild("tusk_r", CubeListBuilder.create().texOffs(3, 62).addBox(-2.5F, 3.5F, -7F, 0.5F, 1.5F, 1F), PartPose.ZERO);
        p_head.addOrReplaceChild("tassel_l", CubeListBuilder.create().texOffs(56, 58).addBox(1.25F, 5.5F, -4.5F, 1F, 2F, 1F), PartPose.ZERO);
        p_head.addOrReplaceChild("tassel_r", CubeListBuilder.create().texOffs(60, 58).addBox(-2.25F, 5.5F, -4.5F, 1F, 2F, 1F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, -2.5F, -3F, -0.55F, 0.3F, -0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(40, 58).addBox(0F, -1.25F, -0.25F, 3.5F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, -2.5F, -3F, -0.55F, -0.3F, 0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(48, 58).addBox(-3.5F, -1.25F, -0.25F, 3.5F, 2.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.25F, 18.75F, -3.75F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(38, 46).addBox(-1.5F, -0.5F, -1.5F, 3F, 3F, 3F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(20, 58).addBox(-1.25F, 2.5F, -1.25F, 2.5F, 1.25F, 2.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(34, 53).addBox(-1.5F, 3.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.25F, 18.75F, -3.75F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(50, 46).addBox(-1.5F, -0.5F, -1.5F, 3F, 3F, 3F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(30, 58).addBox(-1.25F, 2.5F, -1.25F, 2.5F, 1.25F, 2.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(47, 53).addBox(-1.5F, 3.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(3.25F, 18.75F, 3.75F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(12, 46).addBox(-1.5F, -0.5F, -2F, 3F, 3F, 3.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(0, 58).addBox(-1.25F, 2.5F, -1.25F, 2.5F, 1.25F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(8, 53).addBox(-1.5F, 3.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.25F, 18.75F, 3.75F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(25, 46).addBox(-1.5F, -0.5F, -2F, 3F, 3F, 3.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(10, 58).addBox(-1.25F, 2.5F, -1.25F, 2.5F, 1.25F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(21, 53).addBox(-1.5F, 3.75F, -2F, 3F, 1.5F, 3.5F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.5F, 6.5F, -0.6F, 0F, 0F));
        PartDefinition p_curl0 = p_tail.addOrReplaceChild("curl0", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 1.57F, 0F));
        p_curl0.addOrReplaceChild("curl0_b", CubeListBuilder.create().texOffs(11, 62).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_curl1 = p_curl0.addOrReplaceChild("curl1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.3F));
        p_curl1.addOrReplaceChild("curl1_b", CubeListBuilder.create().texOffs(6, 62).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_curl2 = p_curl1.addOrReplaceChild("curl2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 0F, 0F, 1.4F));
        p_curl2.addOrReplaceChild("curl2_b", CubeListBuilder.create().texOffs(15, 62).addBox(0F, -0.25F, -0.25F, 1F, 0.5F, 0.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** chicken_leghorn_f */
    private static LayerDefinition m30() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 17.375F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-2.25F, -2F, -3F, 4.5F, 4.25F, 6F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(20, 11).addBox(-1.75F, -1F, -4F, 3.5F, 3.75F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(29, 11).addBox(-1.75F, -2.5F, -1.5F, 3.5F, 0.5F, 4F), PartPose.ZERO);
        PartDefinition p_wing_l = p_body.addOrReplaceChild("wing_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.25F, -1.625F, -2F, 0F, 0F, 0F));
        p_wing_l.addOrReplaceChild("wing_l_b", CubeListBuilder.create().texOffs(21, 0).addBox(0F, 0F, 0F, 0.5F, 3.25F, 5.5F), PartPose.ZERO);
        PartDefinition p_wing_r = p_body.addOrReplaceChild("wing_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.25F, -1.625F, -2F, 0F, 0F, 0F));
        p_wing_r.addOrReplaceChild("wing_r_b", CubeListBuilder.create().texOffs(33, 0).addBox(-0.5F, 0F, 0F, 0.5F, 3.25F, 5.5F), PartPose.ZERO);
        PartDefinition p_tailf = p_body.addOrReplaceChild("tailf", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.625F, 2.5F, -0.55F, 0F, 0F));
        p_tailf.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(55, 0).addBox(-1.5F, -5F, 0F, 3F, 5.5F, 1F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.125F, -2.5F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(45, 0).addBox(-1.25F, -4.25F, -2F, 2.5F, 4.75F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 11).addBox(-1.5F, -6.75F, -3.5F, 3F, 3F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(22, 17).addBox(-0.5F, -5.25F, -5F, 1F, 1F, 1.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("comb", CubeListBuilder.create().texOffs(12, 11).addBox(-0.25F, -8.75F, -3.5F, 0.5F, 2F, 3.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("wattle", CubeListBuilder.create().texOffs(18, 17).addBox(-0.5F, -4.25F, -4F, 1F, 1.75F, 1F), PartPose.ZERO);
        PartDefinition p_leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, 19F, 0.5F, 0F, 0F, 0F));
        p_leg_l.addOrReplaceChild("leg_l_thigh", CubeListBuilder.create().texOffs(44, 11).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_shank", CubeListBuilder.create().texOffs(58, 11).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 3F, 0.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_foot", CubeListBuilder.create().texOffs(0, 17).addBox(-1F, 4.5F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, 19F, 0.5F, 0F, 0F, 0F));
        p_leg_r.addOrReplaceChild("leg_r_thigh", CubeListBuilder.create().texOffs(51, 11).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_shank", CubeListBuilder.create().texOffs(60, 11).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 3F, 0.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_foot", CubeListBuilder.create().texOffs(9, 17).addBox(-1F, 4.5F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** chicken_leghorn_m */
    private static LayerDefinition m31() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 17F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.25F, -3.25F, 5F, 4.5F, 6.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(45, 11).addBox(-2F, -1.25F, -4.25F, 4F, 4F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(0, 19).addBox(-2F, -2.75F, -1.75F, 4F, 0.5F, 4.5F), PartPose.ZERO);
        PartDefinition p_wing_l = p_body.addOrReplaceChild("wing_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, -1.75F, -2.25F, 0F, 0F, 0F));
        p_wing_l.addOrReplaceChild("wing_l_b", CubeListBuilder.create().texOffs(23, 0).addBox(0F, 0F, 0F, 0.5F, 3.5F, 6F), PartPose.ZERO);
        PartDefinition p_wing_r = p_body.addOrReplaceChild("wing_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, -1.75F, -2.25F, 0F, 0F, 0F));
        p_wing_r.addOrReplaceChild("wing_r_b", CubeListBuilder.create().texOffs(36, 0).addBox(-0.5F, 0F, 0F, 0.5F, 3.5F, 6F), PartPose.ZERO);
        PartDefinition p_tailf = p_body.addOrReplaceChild("tailf", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.75F, 2.75F, -0.35F, 0F, 0F));
        p_tailf.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(0, 11).addBox(-1.5F, -6.5F, 0F, 3F, 7F, 1F), PartPose.ZERO);
        PartDefinition p_sickle = p_tailf.addOrReplaceChild("sickle", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -6F, 0.5F, -0.5F, 0F, 0F));
        p_sickle.addOrReplaceChild("sickle_b", CubeListBuilder.create().texOffs(18, 11).addBox(-0.75F, -0.25F, 0F, 1.5F, 0.5F, 5.75F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.25F, -2.75F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(8, 11).addBox(-1.25F, -4.25F, -2F, 2.5F, 4.75F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(33, 11).addBox(-1.5F, -6.75F, -3.5F, 3F, 3F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(57, 19).addBox(-0.5F, -5.25F, -5F, 1F, 1F, 1.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("comb", CubeListBuilder.create().texOffs(49, 0).addBox(-0.25F, -9.75F, -3.5F, 0.5F, 3F, 5.25F), PartPose.ZERO);
        p_head.addOrReplaceChild("wattle", CubeListBuilder.create().texOffs(35, 19).addBox(-0.5F, -4.25F, -4F, 1F, 2.5F, 1F), PartPose.ZERO);
        PartDefinition p_leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, 18.75F, 0.5F, 0F, 0F, 0F));
        p_leg_l.addOrReplaceChild("leg_l_thigh", CubeListBuilder.create().texOffs(19, 19).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_shank", CubeListBuilder.create().texOffs(17, 19).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 3.25F, 0.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_foot", CubeListBuilder.create().texOffs(39, 19).addBox(-1F, 4.75F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_spur", CubeListBuilder.create().texOffs(0, 24).addBox(-0.25F, 3.25F, 0.25F, 0.5F, 0.5F, 0.75F), PartPose.ZERO);
        PartDefinition p_leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, 18.75F, 0.5F, 0F, 0F, 0F));
        p_leg_r.addOrReplaceChild("leg_r_thigh", CubeListBuilder.create().texOffs(28, 19).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_shank", CubeListBuilder.create().texOffs(26, 19).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 3.25F, 0.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_foot", CubeListBuilder.create().texOffs(48, 19).addBox(-1F, 4.75F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_spur", CubeListBuilder.create().texOffs(3, 24).addBox(-0.25F, 3.25F, 0.25F, 0.5F, 0.5F, 0.75F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** chicken_marans_f */
    private static LayerDefinition m32() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 17.75F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.25F, -3.25F, 5F, 4.5F, 6.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(20, 11).addBox(-2F, -1.25F, -4.25F, 4F, 4F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(30, 11).addBox(-2F, -2.75F, -1.75F, 4F, 0.5F, 4.5F), PartPose.ZERO);
        PartDefinition p_wing_l = p_body.addOrReplaceChild("wing_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, -1.75F, -2.25F, 0F, 0F, 0F));
        p_wing_l.addOrReplaceChild("wing_l_b", CubeListBuilder.create().texOffs(23, 0).addBox(0F, 0F, 0F, 0.5F, 3.5F, 6F), PartPose.ZERO);
        PartDefinition p_wing_r = p_body.addOrReplaceChild("wing_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, -1.75F, -2.25F, 0F, 0F, 0F));
        p_wing_r.addOrReplaceChild("wing_r_b", CubeListBuilder.create().texOffs(36, 0).addBox(-0.5F, 0F, 0F, 0.5F, 3.5F, 6F), PartPose.ZERO);
        PartDefinition p_tailf = p_body.addOrReplaceChild("tailf", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.75F, 2.75F, -0.55F, 0F, 0F));
        p_tailf.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(12, 11).addBox(-1.5F, -4.5F, 0F, 3F, 5F, 1F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.25F, -2.75F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(49, 0).addBox(-1.25F, -4F, -2F, 2.5F, 4.5F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 11).addBox(-1.5F, -6.5F, -3.5F, 3F, 3F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(37, 17).addBox(-0.5F, -5F, -5F, 1F, 1F, 1.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("comb", CubeListBuilder.create().texOffs(47, 11).addBox(-0.25F, -8F, -3.5F, 0.5F, 1.5F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("wattle", CubeListBuilder.create().texOffs(42, 17).addBox(-0.5F, -4F, -4F, 1F, 1.5F, 1F), PartPose.ZERO);
        PartDefinition p_leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, 19.5F, 0.5F, 0F, 0F, 0F));
        p_leg_l.addOrReplaceChild("leg_l_thigh", CubeListBuilder.create().texOffs(53, 11).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_shank", CubeListBuilder.create().texOffs(20, 17).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 2.5F, 0.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_foot", CubeListBuilder.create().texOffs(11, 17).addBox(-1F, 4F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_feath", CubeListBuilder.create().texOffs(7, 17).addBox(0.25F, 2F, -0.5F, 1F, 2F, 1F), PartPose.ZERO);
        PartDefinition p_leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, 19.5F, 0.5F, 0F, 0F, 0F));
        p_leg_r.addOrReplaceChild("leg_r_thigh", CubeListBuilder.create().texOffs(0, 17).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_shank", CubeListBuilder.create().texOffs(35, 17).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 2.5F, 0.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_foot", CubeListBuilder.create().texOffs(26, 17).addBox(-1F, 4F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_feath", CubeListBuilder.create().texOffs(22, 17).addBox(-1.25F, 2F, -0.5F, 1F, 2F, 1F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** chicken_marans_m */
    private static LayerDefinition m33() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 17.125F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-2.75F, -2.25F, -3.5F, 5.5F, 4.75F, 7F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(0, 19).addBox(-2.25F, -1.25F, -4.5F, 4.5F, 4.25F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(45, 12).addBox(-2.25F, -2.75F, -2F, 4.5F, 0.5F, 5F), PartPose.ZERO);
        PartDefinition p_wing_l = p_body.addOrReplaceChild("wing_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.75F, -1.875F, -2.5F, 0F, 0F, 0F));
        p_wing_l.addOrReplaceChild("wing_l_b", CubeListBuilder.create().texOffs(25, 0).addBox(0F, 0F, 0F, 0.5F, 3.75F, 6.5F), PartPose.ZERO);
        PartDefinition p_wing_r = p_body.addOrReplaceChild("wing_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.75F, -1.875F, -2.5F, 0F, 0F, 0F));
        p_wing_r.addOrReplaceChild("wing_r_b", CubeListBuilder.create().texOffs(39, 0).addBox(-0.5F, 0F, 0F, 0.5F, 3.75F, 6.5F), PartPose.ZERO);
        PartDefinition p_tailf = p_body.addOrReplaceChild("tailf", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.875F, 3F, -0.35F, 0F, 0F));
        p_tailf.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(53, 0).addBox(-1.5F, -5.75F, 0F, 3F, 6.25F, 1F), PartPose.ZERO);
        PartDefinition p_sickle = p_tailf.addOrReplaceChild("sickle", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -5.25F, 0.5F, -0.5F, 0F, 0F));
        p_sickle.addOrReplaceChild("sickle_b", CubeListBuilder.create().texOffs(31, 12).addBox(-0.75F, -0.25F, 0F, 1.5F, 0.5F, 5.25F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.375F, -3F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 12).addBox(-1.25F, -4F, -2F, 2.5F, 4.5F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(19, 12).addBox(-1.5F, -6.5F, -3.5F, 3F, 3F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(59, 19).addBox(-0.5F, -5F, -5F, 1F, 1F, 1.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("comb", CubeListBuilder.create().texOffs(10, 12).addBox(-0.25F, -9F, -3.5F, 0.5F, 2.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("wattle", CubeListBuilder.create().texOffs(55, 19).addBox(-0.5F, -4F, -4F, 1F, 2F, 1F), PartPose.ZERO);
        PartDefinition p_leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, 19F, 0.5F, 0F, 0F, 0F));
        p_leg_l.addOrReplaceChild("leg_l_thigh", CubeListBuilder.create().texOffs(11, 19).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_shank", CubeListBuilder.create().texOffs(29, 19).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 3F, 0.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_foot", CubeListBuilder.create().texOffs(37, 19).addBox(-1F, 4.5F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_feath", CubeListBuilder.create().texOffs(25, 19).addBox(0.25F, 2F, -0.5F, 1F, 2.5F, 1F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_spur", CubeListBuilder.create().texOffs(0, 25).addBox(-0.25F, 3F, 0.25F, 0.5F, 0.5F, 0.75F), PartPose.ZERO);
        PartDefinition p_leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, 19F, 0.5F, 0F, 0F, 0F));
        p_leg_r.addOrReplaceChild("leg_r_thigh", CubeListBuilder.create().texOffs(18, 19).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_shank", CubeListBuilder.create().texOffs(35, 19).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 3F, 0.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_foot", CubeListBuilder.create().texOffs(46, 19).addBox(-1F, 4.5F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_feath", CubeListBuilder.create().texOffs(31, 19).addBox(-1.25F, 2F, -0.5F, 1F, 2.5F, 1F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_spur", CubeListBuilder.create().texOffs(3, 25).addBox(-0.25F, 3F, 0.25F, 0.5F, 0.5F, 0.75F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** chicken_brahma_f */
    private static LayerDefinition m34() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 17F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -2.75F, -3.75F, 6F, 5.5F, 7.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(32, 14).addBox(-2.5F, -1.75F, -4.75F, 5F, 5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(10, 14).addBox(-2.5F, -3.25F, -2.25F, 5F, 0.5F, 5.75F), PartPose.ZERO);
        PartDefinition p_wing_l = p_body.addOrReplaceChild("wing_l", CubeListBuilder.create(), PartPose.offsetAndRotation(3F, -2.25F, -2.875F, 0F, 0F, 0F));
        p_wing_l.addOrReplaceChild("wing_l_b", CubeListBuilder.create().texOffs(28, 0).addBox(0F, 0F, 0F, 0.5F, 4.5F, 7.25F), PartPose.ZERO);
        PartDefinition p_wing_r = p_body.addOrReplaceChild("wing_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-3F, -2.25F, -2.875F, 0F, 0F, 0F));
        p_wing_r.addOrReplaceChild("wing_r_b", CubeListBuilder.create().texOffs(44, 0).addBox(-0.5F, 0F, 0F, 0.5F, 4.5F, 7.25F), PartPose.ZERO);
        PartDefinition p_tailf = p_body.addOrReplaceChild("tailf", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.25F, 3.375F, -0.55F, 0F, 0F));
        p_tailf.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(56, 14).addBox(-1.5F, -3.5F, 0F, 3F, 4F, 1F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.75F, -3.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 14).addBox(-1.25F, -4F, -2F, 2.5F, 4.5F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(44, 14).addBox(-1.5F, -6.5F, -3.5F, 3F, 3F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(44, 21).addBox(-0.5F, -5F, -5F, 1F, 1F, 1.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("comb", CubeListBuilder.create().texOffs(49, 21).addBox(-0.5F, -7F, -3.5F, 1F, 0.5F, 2F), PartPose.ZERO);
        p_head.addOrReplaceChild("wattle", CubeListBuilder.create().texOffs(55, 21).addBox(-0.5F, -4F, -4F, 1F, 0.75F, 1F), PartPose.ZERO);
        PartDefinition p_leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, 19.25F, 0.5F, 0F, 0F, 0F));
        p_leg_l.addOrReplaceChild("leg_l_thigh", CubeListBuilder.create().texOffs(0, 21).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_shank", CubeListBuilder.create().texOffs(18, 21).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 2.75F, 0.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_foot", CubeListBuilder.create().texOffs(26, 21).addBox(-1F, 4.25F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_feath", CubeListBuilder.create().texOffs(14, 21).addBox(0.25F, 2F, -0.5F, 1F, 2.25F, 1F), PartPose.ZERO);
        PartDefinition p_leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, 19.25F, 0.5F, 0F, 0F, 0F));
        p_leg_r.addOrReplaceChild("leg_r_thigh", CubeListBuilder.create().texOffs(7, 21).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_shank", CubeListBuilder.create().texOffs(24, 21).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 2.75F, 0.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_foot", CubeListBuilder.create().texOffs(35, 21).addBox(-1F, 4.25F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_feath", CubeListBuilder.create().texOffs(20, 21).addBox(-1.25F, 2F, -0.5F, 1F, 2.25F, 1F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** chicken_brahma_m */
    private static LayerDefinition m35() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 16.625F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-3.25F, -2.75F, -4.25F, 6.5F, 5.75F, 8.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(34, 15).addBox(-2.75F, -1.75F, -5.25F, 5.5F, 5.25F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(10, 15).addBox(-2.75F, -3.25F, -2.75F, 5.5F, 0.5F, 6.5F), PartPose.ZERO);
        PartDefinition p_wing_l = p_body.addOrReplaceChild("wing_l", CubeListBuilder.create(), PartPose.offsetAndRotation(3.25F, -2.375F, -3.25F, 0F, 0F, 0F));
        p_wing_l.addOrReplaceChild("wing_l_b", CubeListBuilder.create().texOffs(30, 0).addBox(0F, 0F, 0F, 0.5F, 4.75F, 8F), PartPose.ZERO);
        PartDefinition p_wing_r = p_body.addOrReplaceChild("wing_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.25F, -2.375F, -3.25F, 0F, 0F, 0F));
        p_wing_r.addOrReplaceChild("wing_r_b", CubeListBuilder.create().texOffs(47, 0).addBox(-0.5F, 0F, 0F, 0.5F, 4.75F, 8F), PartPose.ZERO);
        PartDefinition p_tailf = p_body.addOrReplaceChild("tailf", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.375F, 3.75F, -0.35F, 0F, 0F));
        p_tailf.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(0, 22).addBox(-1.5F, -4.5F, 0F, 3F, 5F, 1F), PartPose.ZERO);
        PartDefinition p_sickle = p_tailf.addOrReplaceChild("sickle", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -4F, 0.5F, -0.5F, 0F, 0F));
        p_sickle.addOrReplaceChild("sickle_b", CubeListBuilder.create().texOffs(8, 22).addBox(-0.75F, -0.25F, 0F, 1.5F, 0.5F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.875F, -3.75F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 15).addBox(-1.25F, -4F, -2F, 2.5F, 4.5F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(47, 15).addBox(-1.5F, -6.5F, -3.5F, 3F, 3F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(0, 28).addBox(-0.5F, -5F, -5F, 1F, 1F, 1.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("comb", CubeListBuilder.create().texOffs(5, 28).addBox(-0.5F, -7F, -3.5F, 1F, 0.5F, 2F), PartPose.ZERO);
        p_head.addOrReplaceChild("wattle", CubeListBuilder.create().texOffs(11, 28).addBox(-0.5F, -4F, -4F, 1F, 0.75F, 1F), PartPose.ZERO);
        PartDefinition p_leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, 19F, 0.5F, 0F, 0F, 0F));
        p_leg_l.addOrReplaceChild("leg_l_thigh", CubeListBuilder.create().texOffs(19, 22).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_shank", CubeListBuilder.create().texOffs(37, 22).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 3F, 0.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_foot", CubeListBuilder.create().texOffs(45, 22).addBox(-1F, 4.5F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_feath", CubeListBuilder.create().texOffs(33, 22).addBox(0.25F, 2F, -0.5F, 1F, 2.5F, 1F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_spur", CubeListBuilder.create().texOffs(15, 28).addBox(-0.25F, 3F, 0.25F, 0.5F, 0.5F, 0.75F), PartPose.ZERO);
        PartDefinition p_leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, 19F, 0.5F, 0F, 0F, 0F));
        p_leg_r.addOrReplaceChild("leg_r_thigh", CubeListBuilder.create().texOffs(26, 22).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_shank", CubeListBuilder.create().texOffs(43, 22).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 3F, 0.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_foot", CubeListBuilder.create().texOffs(54, 22).addBox(-1F, 4.5F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_feath", CubeListBuilder.create().texOffs(39, 22).addBox(-1.25F, 2F, -0.5F, 1F, 2.5F, 1F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_spur", CubeListBuilder.create().texOffs(18, 28).addBox(-0.25F, 3F, 0.25F, 0.5F, 0.5F, 0.75F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** chicken_gauloise_f */
    private static LayerDefinition m36() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 17.625F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-2.25F, -2F, -3F, 4.75F, 4.25F, 6.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(20, 11).addBox(-1.75F, -1F, -4F, 3.75F, 3.75F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(30, 11).addBox(-1.75F, -2.5F, -1.5F, 3.75F, 0.5F, 4.25F), PartPose.ZERO);
        PartDefinition p_wing_l = p_body.addOrReplaceChild("wing_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.375F, -1.625F, -2.125F, 0F, 0F, 0F));
        p_wing_l.addOrReplaceChild("wing_l_b", CubeListBuilder.create().texOffs(22, 0).addBox(0F, 0F, 0F, 0.5F, 3.25F, 5.75F), PartPose.ZERO);
        PartDefinition p_wing_r = p_body.addOrReplaceChild("wing_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.375F, -1.625F, -2.125F, 0F, 0F, 0F));
        p_wing_r.addOrReplaceChild("wing_r_b", CubeListBuilder.create().texOffs(35, 0).addBox(-0.5F, 0F, 0F, 0.5F, 3.25F, 5.75F), PartPose.ZERO);
        PartDefinition p_tailf = p_body.addOrReplaceChild("tailf", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.625F, 2.625F, -0.55F, 0F, 0F));
        p_tailf.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(0, 11).addBox(-1.5F, -5.25F, 0F, 3F, 5.75F, 1F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.125F, -2.625F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(48, 0).addBox(-1.25F, -4.25F, -2F, 2.5F, 4.75F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(8, 11).addBox(-1.5F, -6.75F, -3.5F, 3F, 3F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(29, 18).addBox(-0.5F, -5.25F, -5F, 1F, 1F, 1.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("comb", CubeListBuilder.create().texOffs(46, 11).addBox(-0.25F, -8.5F, -3.5F, 0.5F, 1.75F, 2.75F), PartPose.ZERO);
        p_head.addOrReplaceChild("wattle", CubeListBuilder.create().texOffs(34, 18).addBox(-0.5F, -4.25F, -4F, 1F, 1.5F, 1F), PartPose.ZERO);
        PartDefinition p_leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, 19.25F, 0.5F, 0F, 0F, 0F));
        p_leg_l.addOrReplaceChild("leg_l_thigh", CubeListBuilder.create().texOffs(53, 11).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_shank", CubeListBuilder.create().texOffs(7, 18).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 2.75F, 0.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_foot", CubeListBuilder.create().texOffs(11, 18).addBox(-1F, 4.25F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        PartDefinition p_leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, 19.25F, 0.5F, 0F, 0F, 0F));
        p_leg_r.addOrReplaceChild("leg_r_thigh", CubeListBuilder.create().texOffs(0, 18).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_shank", CubeListBuilder.create().texOffs(9, 18).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 2.75F, 0.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_foot", CubeListBuilder.create().texOffs(20, 18).addBox(-1F, 4.25F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** chicken_gauloise_m */
    private static LayerDefinition m37() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 17.25F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.25F, -3.25F, 5.25F, 4.5F, 6.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(18, 20).addBox(-2F, -1.25F, -4.25F, 4.25F, 4F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(0, 20).addBox(-2F, -2.75F, -1.75F, 4.25F, 0.5F, 4.75F), PartPose.ZERO);
        PartDefinition p_wing_l = p_body.addOrReplaceChild("wing_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, -1.75F, -2.375F, 0F, 0F, 0F));
        p_wing_l.addOrReplaceChild("wing_l_b", CubeListBuilder.create().texOffs(24, 0).addBox(0F, 0F, 0F, 0.5F, 3.5F, 6.25F), PartPose.ZERO);
        PartDefinition p_wing_r = p_body.addOrReplaceChild("wing_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, -1.75F, -2.375F, 0F, 0F, 0F));
        p_wing_r.addOrReplaceChild("wing_r_b", CubeListBuilder.create().texOffs(38, 0).addBox(-0.5F, 0F, 0F, 0.5F, 3.5F, 6.25F), PartPose.ZERO);
        PartDefinition p_tailf = p_body.addOrReplaceChild("tailf", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.75F, 2.875F, -0.35F, 0F, 0F));
        p_tailf.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(52, 0).addBox(-1.5F, -6.75F, 0F, 3F, 7.25F, 1F), PartPose.ZERO);
        PartDefinition p_sickle = p_tailf.addOrReplaceChild("sickle", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -6.25F, 0.5F, -0.5F, 0F, 0F));
        p_sickle.addOrReplaceChild("sickle_b", CubeListBuilder.create().texOffs(20, 12).addBox(-0.75F, -0.25F, 0F, 1.5F, 0.5F, 6F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.25F, -2.875F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(10, 12).addBox(-1.25F, -4.25F, -2F, 2.5F, 4.75F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(35, 12).addBox(-1.5F, -6.75F, -3.5F, 3F, 3F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(9, 26).addBox(-0.5F, -5.25F, -5F, 1F, 1F, 1.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("comb", CubeListBuilder.create().texOffs(0, 12).addBox(-0.25F, -9.5F, -3.5F, 0.5F, 2.75F, 4.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("wattle", CubeListBuilder.create().texOffs(47, 20).addBox(-0.5F, -4.25F, -4F, 1F, 2.25F, 1F), PartPose.ZERO);
        PartDefinition p_leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, 19F, 0.5F, 0F, 0F, 0F));
        p_leg_l.addOrReplaceChild("leg_l_thigh", CubeListBuilder.create().texOffs(29, 20).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_shank", CubeListBuilder.create().texOffs(43, 20).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 3F, 0.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_foot", CubeListBuilder.create().texOffs(51, 20).addBox(-1F, 4.5F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_spur", CubeListBuilder.create().texOffs(14, 26).addBox(-0.25F, 3F, 0.25F, 0.5F, 0.5F, 0.75F), PartPose.ZERO);
        PartDefinition p_leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, 19F, 0.5F, 0F, 0F, 0F));
        p_leg_r.addOrReplaceChild("leg_r_thigh", CubeListBuilder.create().texOffs(36, 20).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_shank", CubeListBuilder.create().texOffs(45, 20).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 3F, 0.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_foot", CubeListBuilder.create().texOffs(0, 26).addBox(-1F, 4.5F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_spur", CubeListBuilder.create().texOffs(17, 26).addBox(-0.25F, 3F, 0.25F, 0.5F, 0.5F, 0.75F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** chicken_silkie_f */
    private static LayerDefinition m38() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 18.625F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.25F, -3.25F, 5.25F, 4.75F, 6.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(36, 12).addBox(-2F, -1.25F, -4.25F, 4.25F, 4.25F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(0, 22).addBox(-2F, -2.75F, -1.75F, 4.25F, 0.5F, 4.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("puff", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -1.25F, -2.25F, 6.25F, 4.75F, 6.25F), PartPose.ZERO);
        PartDefinition p_wing_l = p_body.addOrReplaceChild("wing_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, -1.875F, -2.375F, 0F, 0F, 0F));
        p_wing_l.addOrReplaceChild("wing_l_b", CubeListBuilder.create().texOffs(49, 0).addBox(0F, 0F, 0F, 0.5F, 3.75F, 6.25F), PartPose.ZERO);
        PartDefinition p_wing_r = p_body.addOrReplaceChild("wing_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, -1.875F, -2.375F, 0F, 0F, 0F));
        p_wing_r.addOrReplaceChild("wing_r_b", CubeListBuilder.create().texOffs(0, 12).addBox(-0.5F, 0F, 0F, 0.5F, 3.75F, 6.25F), PartPose.ZERO);
        PartDefinition p_tailf = p_body.addOrReplaceChild("tailf", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.875F, 2.875F, -0.55F, 0F, 0F));
        p_tailf.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(31, 22).addBox(-1.5F, -2.75F, 0F, 3F, 3.25F, 1F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.375F, -2.875F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(14, 12).addBox(-1.25F, -3.5F, -2F, 2.5F, 4F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(24, 12).addBox(-1.5F, -6F, -3.5F, 3F, 3F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(9, 28).addBox(-0.5F, -4.5F, -5F, 1F, 1F, 1.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(18, 22).addBox(-1.75F, -7.5F, -3F, 3.5F, 2F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("comb", CubeListBuilder.create().texOffs(26, 28).addBox(-0.5F, -6.5F, -4F, 1F, 0.5F, 1F), PartPose.ZERO);
        PartDefinition p_leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, 20.5F, 0.5F, 0F, 0F, 0F));
        p_leg_l.addOrReplaceChild("leg_l_thigh", CubeListBuilder.create().texOffs(39, 22).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_shank", CubeListBuilder.create().texOffs(18, 28).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 1.5F, 0.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_foot", CubeListBuilder.create().texOffs(53, 22).addBox(-1F, 3F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_feath", CubeListBuilder.create().texOffs(14, 28).addBox(0.25F, 2F, -0.5F, 1F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, 20.5F, 0.5F, 0F, 0F, 0F));
        p_leg_r.addOrReplaceChild("leg_r_thigh", CubeListBuilder.create().texOffs(46, 22).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_shank", CubeListBuilder.create().texOffs(24, 28).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 1.5F, 0.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_foot", CubeListBuilder.create().texOffs(0, 28).addBox(-1F, 3F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_feath", CubeListBuilder.create().texOffs(20, 28).addBox(-1.25F, 2F, -0.5F, 1F, 1F, 1F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** chicken_silkie_m */
    private static LayerDefinition m39() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 18.25F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 0).addBox(-2.75F, -2.5F, -3.5F, 5.75F, 5F, 7.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(20, 24).addBox(-2.25F, -1.5F, -4.5F, 4.75F, 4.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(0, 24).addBox(-2.25F, -3F, -2F, 4.75F, 0.5F, 5.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("puff", CubeListBuilder.create().texOffs(26, 0).addBox(-3.25F, -1.5F, -2.5F, 6.75F, 5F, 6.75F), PartPose.ZERO);
        PartDefinition p_wing_l = p_body.addOrReplaceChild("wing_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.875F, -2F, -2.625F, 0F, 0F, 0F));
        p_wing_l.addOrReplaceChild("wing_l_b", CubeListBuilder.create().texOffs(0, 13).addBox(0F, 0F, 0F, 0.5F, 4F, 6.75F), PartPose.ZERO);
        PartDefinition p_wing_r = p_body.addOrReplaceChild("wing_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.875F, -2F, -2.625F, 0F, 0F, 0F));
        p_wing_r.addOrReplaceChild("wing_r_b", CubeListBuilder.create().texOffs(15, 13).addBox(-0.5F, 0F, 0F, 0.5F, 4F, 6.75F), PartPose.ZERO);
        PartDefinition p_tailf = p_body.addOrReplaceChild("tailf", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2F, 3.125F, -0.35F, 0F, 0F));
        p_tailf.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(45, 24).addBox(-1.5F, -3.5F, 0F, 3F, 4F, 1F), PartPose.ZERO);
        PartDefinition p_sickle = p_tailf.addOrReplaceChild("sickle", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3F, 0.5F, -0.5F, 0F, 0F));
        p_sickle.addOrReplaceChild("sickle_b", CubeListBuilder.create().texOffs(7, 30).addBox(-0.75F, -0.25F, 0F, 1.5F, 0.5F, 3.25F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.5F, -3.125F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(30, 13).addBox(-1.25F, -3.5F, -2F, 2.5F, 4F, 2.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(40, 13).addBox(-1.5F, -6F, -3.5F, 3F, 3F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(35, 30).addBox(-0.5F, -4.5F, -5F, 1F, 1F, 1.5F), PartPose.ZERO);
        p_head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(32, 24).addBox(-1.75F, -7.5F, -3F, 3.5F, 2F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("comb", CubeListBuilder.create().texOffs(52, 30).addBox(-0.5F, -6.5F, -4F, 1F, 0.5F, 1F), PartPose.ZERO);
        PartDefinition p_leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, 20.25F, 0.5F, 0F, 0F, 0F));
        p_leg_l.addOrReplaceChild("leg_l_thigh", CubeListBuilder.create().texOffs(53, 24).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_shank", CubeListBuilder.create().texOffs(44, 30).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 1.75F, 0.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_foot", CubeListBuilder.create().texOffs(17, 30).addBox(-1F, 3.25F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_feath", CubeListBuilder.create().texOffs(40, 30).addBox(0.25F, 2F, -0.5F, 1F, 1.25F, 1F), PartPose.ZERO);
        p_leg_l.addOrReplaceChild("leg_l_spur", CubeListBuilder.create().texOffs(56, 30).addBox(-0.25F, 1.75F, 0.25F, 0.5F, 0.5F, 0.75F), PartPose.ZERO);
        PartDefinition p_leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, 20.25F, 0.5F, 0F, 0F, 0F));
        p_leg_r.addOrReplaceChild("leg_r_thigh", CubeListBuilder.create().texOffs(0, 30).addBox(-0.75F, -0.5F, -1F, 1.5F, 1.75F, 2F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_shank", CubeListBuilder.create().texOffs(50, 30).addBox(-0.25F, 1.5F, -0.25F, 0.5F, 1.75F, 0.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_foot", CubeListBuilder.create().texOffs(26, 30).addBox(-1F, 3.25F, -2F, 2F, 0.5F, 2.5F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_feath", CubeListBuilder.create().texOffs(46, 30).addBox(-1.25F, 2F, -0.5F, 1F, 1.25F, 1F), PartPose.ZERO);
        p_leg_r.addOrReplaceChild("leg_r_spur", CubeListBuilder.create().texOffs(59, 30).addBox(-0.25F, 1.75F, 0.25F, 0.5F, 0.5F, 0.75F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** goat_alpine_f */
    private static LayerDefinition m40() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 12F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(36, 0).addBox(-2.75F, -3.5F, -6.25F, 5.5F, 7F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-3.25F, -3F, -2.25F, 6.5F, 7F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 12).addBox(-2.75F, -3.5F, 2.25F, 5.5F, 6.5F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("udder", CubeListBuilder.create().texOffs(8, 23).addBox(-1.75F, 3.5F, 0.25F, 3.5F, 2F, 3.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0", CubeListBuilder.create().texOffs(6, 34).addBox(0.75F, 5.5F, 1.75F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1", CubeListBuilder.create().texOffs(8, 34).addBox(-1.25F, 5.5F, 1.75F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2F, -5.75F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(22, 0).addBox(-1.5F, -5F, -3.5F, 3F, 7.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(19, 12).addBox(-2F, -8F, -6.5F, 4F, 4.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(46, 23).addBox(-1.5F, -5.5F, -8.5F, 3F, 3F, 2F), PartPose.ZERO);
        p_head.addOrReplaceChild("beard", CubeListBuilder.create().texOffs(26, 30).addBox(-0.75F, -2.5F, -8F, 1.5F, 1.5F, 1F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, -7F, -4F, 0.15F, 0.1F, -0.75F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(55, 30).addBox(0F, -0.5F, -0.25F, 2.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, -7F, -4F, 0.15F, -0.1F, 0.75F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(0, 34).addBox(-2.5F, -0.5F, -0.25F, 2.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, -7.75F, -4.5F, 0F, -0.35F, -1.3F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(31, 30).addBox(0F, -0.5F, -0.5F, 2.25F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.9F, 0F, 0F, 0F, -0.6F, 0F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(10, 34).addBox(0F, -0.25F, -0.25F, 2.25F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l2 = p_horn_l1.addOrReplaceChild("horn_l2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.9F, 0F, 0F, 0F, -0.6F, 0F));
        p_horn_l2.addOrReplaceChild("horn_l2_b", CubeListBuilder.create().texOffs(38, 30).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, -7.75F, -4.5F, 0F, 0.35F, 1.3F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(43, 30).addBox(-2.25F, -0.5F, -0.5F, 2.25F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.9F, 0F, 0F, 0F, 0.6F, 0F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(16, 34).addBox(-2.25F, -0.25F, -0.25F, 2.25F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r2 = p_horn_r1.addOrReplaceChild("horn_r2", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.9F, 0F, 0F, 0F, 0.6F, 0F));
        p_horn_r2.addOrReplaceChild("horn_r2_b", CubeListBuilder.create().texOffs(50, 30).addBox(-1.5F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.5F, 14.5F, -4.25F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(53, 12).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(34, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(7, 30).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.5F, 14.5F, -4.25F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 23).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(40, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(14, 30).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.5F, 14.5F, 4.25F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 12).addBox(-1F, -0.5F, -1.5F, 2F, 4.75F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(22, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(56, 23).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.5F, 14.5F, 4.25F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(44, 12).addBox(-1F, -0.5F, -1.5F, 2F, 4.75F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(28, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 30).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.25F, 6.25F, -0.9F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(21, 30).addBox(-0.75F, -2.5F, 0F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** goat_alpine_m */
    private static LayerDefinition m41() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 12F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(37, 0).addBox(-2.75F, -3.5F, -6.25F, 5.5F, 7F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-3.25F, -3F, -2.25F, 6.5F, 7F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 12).addBox(-2.75F, -3.5F, 2.25F, 5.5F, 6.5F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2F, -5.75F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(22, 0).addBox(-1.75F, -5F, -3.5F, 3.5F, 7.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(19, 12).addBox(-2F, -8F, -6.5F, 4F, 4.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(32, 23).addBox(-1.5F, -5.5F, -8.5F, 3F, 3F, 2F), PartPose.ZERO);
        p_head.addOrReplaceChild("beard", CubeListBuilder.create().texOffs(42, 23).addBox(-0.75F, -2.5F, -8F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, -7F, -4F, 0.15F, 0.1F, -0.75F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(47, 30).addBox(0F, -0.5F, -0.25F, 2.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, -7F, -4F, 0.15F, -0.1F, 0.75F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(53, 30).addBox(-2.5F, -0.5F, -0.25F, 2.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, -7.75F, -4.5F, 0F, -0.35F, -1.3F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(19, 30).addBox(0F, -0.5F, -0.5F, 2.75F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(2.462F, 0F, 0F, 0F, -0.6F, 0F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(0, 34).addBox(0F, -0.25F, -0.25F, 2.75F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l2 = p_horn_l1.addOrReplaceChild("horn_l2", CubeListBuilder.create(), PartPose.offsetAndRotation(2.462F, 0F, 0F, 0F, -0.6F, 0F));
        p_horn_l2.addOrReplaceChild("horn_l2_b", CubeListBuilder.create().texOffs(27, 30).addBox(0F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, -7.75F, -4.5F, 0F, 0.35F, 1.3F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(33, 30).addBox(-2.75F, -0.5F, -0.5F, 2.75F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.462F, 0F, 0F, 0F, 0.6F, 0F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(7, 34).addBox(-2.75F, -0.25F, -0.25F, 2.75F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r2 = p_horn_r1.addOrReplaceChild("horn_r2", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.462F, 0F, 0F, 0F, 0.6F, 0F));
        p_horn_r2.addOrReplaceChild("horn_r2_b", CubeListBuilder.create().texOffs(41, 30).addBox(-1.75F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.5F, 14.5F, -4.25F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(53, 12).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(20, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(0, 30).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.5F, 14.5F, -4.25F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 23).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(26, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(7, 30).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.5F, 14.5F, 4.25F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 12).addBox(-1F, -0.5F, -1.5F, 2F, 4.75F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(8, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(47, 23).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.5F, 14.5F, 4.25F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(44, 12).addBox(-1F, -0.5F, -1.5F, 2F, 4.75F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(14, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(54, 23).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.25F, 6.25F, -0.9F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(14, 30).addBox(-0.75F, -2.5F, 0F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** goat_saanen_f */
    private static LayerDefinition m42() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 11.625F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(24, 0).addBox(-2.75F, -3.5F, -6.5F, 5.75F, 7.25F, 4.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-3.25F, -3F, -2.5F, 6.75F, 7.25F, 5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 13).addBox(-2.75F, -3.5F, 2.5F, 5.75F, 6.75F, 4.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("udder", CubeListBuilder.create().texOffs(8, 24).addBox(-1.75F, 3.75F, 0.5F, 3.5F, 2F, 3.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0", CubeListBuilder.create().texOffs(43, 31).addBox(0.75F, 5.75F, 2F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1", CubeListBuilder.create().texOffs(45, 31).addBox(-1.25F, 5.75F, 2F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.125F, -6.125F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(44, 0).addBox(-1.5F, -5F, -3.5F, 3F, 7.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(20, 13).addBox(-2F, -8F, -6.5F, 4F, 4.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(46, 24).addBox(-1.5F, -5.5F, -8.5F, 3F, 3F, 2F), PartPose.ZERO);
        p_head.addOrReplaceChild("beard", CubeListBuilder.create().texOffs(26, 31).addBox(-0.75F, -2.5F, -8F, 1.5F, 1.5F, 1F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, -7F, -4F, -0.55F, 0.3F, -0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(31, 31).addBox(0F, -0.5F, -0.25F, 2.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, -7F, -4F, -0.55F, -0.3F, 0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(37, 31).addBox(-2.5F, -0.5F, -0.25F, 2.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.625F, 14.25F, -4.625F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(54, 13).addBox(-1F, -0.5F, -1F, 2F, 5F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(34, 24).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(7, 31).addBox(-0.75F, 8.25F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.625F, 14.25F, -4.625F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 24).addBox(-1F, -0.5F, -1F, 2F, 5F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(40, 24).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(14, 31).addBox(-0.75F, 8.25F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.625F, 14.25F, 4.625F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(36, 13).addBox(-1F, -0.5F, -1.5F, 2F, 5F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(22, 24).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(56, 24).addBox(-0.75F, 8.25F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.625F, 14.25F, 4.625F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(45, 13).addBox(-1F, -0.5F, -1.5F, 2F, 5F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(28, 24).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 31).addBox(-0.75F, 8.25F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.375F, 6.625F, -0.9F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(21, 31).addBox(-0.75F, -2.5F, 0F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** goat_saanen_m */
    private static LayerDefinition m43() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 11.625F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(24, 0).addBox(-2.75F, -3.5F, -6.5F, 5.75F, 7.25F, 4.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-3.25F, -3F, -2.5F, 6.75F, 7.25F, 5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 13).addBox(-2.75F, -3.5F, 2.5F, 5.75F, 6.75F, 4.25F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.125F, -6.125F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(44, 0).addBox(-1.75F, -5F, -3.5F, 3.5F, 7.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(20, 13).addBox(-2F, -8F, -6.5F, 4F, 4.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(32, 24).addBox(-1.5F, -5.5F, -8.5F, 3F, 3F, 2F), PartPose.ZERO);
        p_head.addOrReplaceChild("beard", CubeListBuilder.create().texOffs(42, 24).addBox(-0.75F, -2.5F, -8F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, -7F, -4F, -0.55F, 0.3F, -0.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(19, 31).addBox(0F, -0.5F, -0.25F, 2.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, -7F, -4F, -0.55F, -0.3F, 0.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(25, 31).addBox(-2.5F, -0.5F, -0.25F, 2.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.625F, 14.25F, -4.625F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(54, 13).addBox(-1F, -0.5F, -1F, 2F, 5F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(20, 24).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(0, 31).addBox(-0.75F, 8.25F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.625F, 14.25F, -4.625F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 24).addBox(-1F, -0.5F, -1F, 2F, 5F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(26, 24).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(7, 31).addBox(-0.75F, 8.25F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.625F, 14.25F, 4.625F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(36, 13).addBox(-1F, -0.5F, -1.5F, 2F, 5F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(8, 24).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(47, 24).addBox(-0.75F, 8.25F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.625F, 14.25F, 4.625F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(45, 13).addBox(-1F, -0.5F, -1.5F, 2F, 5F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(14, 24).addBox(-0.75F, 4.5F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(54, 24).addBox(-0.75F, 8.25F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.375F, 6.625F, -0.9F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(14, 31).addBox(-0.75F, -2.5F, 0F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** goat_boer_f */
    private static LayerDefinition m44() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 12.375F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(24, 0).addBox(-3.25F, -4F, -6.25F, 6.5F, 8.25F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-3.75F, -3.5F, -2.25F, 7.5F, 8.25F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 13).addBox(-3.25F, -4F, 2.25F, 6.5F, 7.75F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("udder", CubeListBuilder.create().texOffs(25, 25).addBox(-1.75F, 4.25F, 0.25F, 3.5F, 2F, 3.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0", CubeListBuilder.create().texOffs(21, 37).addBox(0.75F, 6.25F, 1.75F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1", CubeListBuilder.create().texOffs(23, 37).addBox(-1.25F, 6.25F, 1.75F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.625F, -5.75F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(21, 13).addBox(-1.75F, -5F, -3.5F, 3.5F, 7.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(36, 13).addBox(-2F, -8F, -6.5F, 4F, 4.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(39, 25).addBox(-1.5F, -5.5F, -8.5F, 3F, 3F, 2F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, -7F, -4F, -0.45F, 0.25F, 1.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(5, 37).addBox(0F, -0.5F, -0.25F, 3.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, -7F, -4F, -0.45F, -0.25F, -1.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(13, 37).addBox(-3.5F, -0.5F, -0.25F, 3.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, -7.75F, -4.5F, 0F, -0.5F, -1F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(45, 32).addBox(0F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.525F, 0F, 0F, 0F, -0.7F, 0F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(25, 37).addBox(0F, -0.25F, -0.25F, 2F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l2 = p_horn_l1.addOrReplaceChild("horn_l2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.525F, 0F, 0F, 0F, -0.6F, 0F));
        p_horn_l2.addOrReplaceChild("horn_l2_b", CubeListBuilder.create().texOffs(51, 32).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, -7.75F, -4.5F, 0F, 0.5F, 1F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(56, 32).addBox(-1.75F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.525F, 0F, 0F, 0F, 0.7F, 0F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(30, 37).addBox(-1.75F, -0.25F, -0.25F, 2F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r2 = p_horn_r1.addOrReplaceChild("horn_r2", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.525F, 0F, 0F, 0F, 0.6F, 0F));
        p_horn_r2.addOrReplaceChild("horn_r2_b", CubeListBuilder.create().texOffs(0, 37).addBox(-1.5F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, 15.5F, -4.25F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(9, 25).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(0, 32).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(26, 32).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, 15.5F, -4.25F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(17, 25).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(6, 32).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(33, 32).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, 15.5F, 4.25F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(52, 13).addBox(-1F, -0.5F, -1.5F, 2F, 4.5F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(49, 25).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(12, 32).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, 15.5F, 4.25F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(0, 25).addBox(-1F, -0.5F, -1.5F, 2F, 4.5F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(55, 25).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(19, 32).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.875F, 6.25F, -0.9F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(40, 32).addBox(-0.75F, -2.5F, 0F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** goat_boer_m */
    private static LayerDefinition m45() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 12.375F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(24, 0).addBox(-3.25F, -4F, -6.25F, 6.5F, 8.25F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 0).addBox(-3.75F, -3.5F, -2.25F, 7.5F, 8.25F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 13).addBox(-3.25F, -4F, 2.25F, 6.5F, 7.75F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.625F, -5.75F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(21, 13).addBox(-1.75F, -5F, -3.5F, 3.5F, 7.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(36, 13).addBox(-2F, -8F, -6.5F, 4F, 4.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(25, 25).addBox(-1.5F, -5.5F, -8.5F, 3F, 3F, 2F), PartPose.ZERO);
        p_head.addOrReplaceChild("beard", CubeListBuilder.create().texOffs(59, 25).addBox(-0.75F, -2.5F, -8F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, -7F, -4F, -0.45F, 0.25F, 1.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(0, 36).addBox(0F, -0.5F, -0.25F, 3.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, -7F, -4F, -0.45F, -0.25F, -1.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(8, 36).addBox(-3.5F, -0.5F, -0.25F, 3.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, -7.75F, -4.5F, 0F, -0.5F, -1F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(33, 32).addBox(0F, -0.5F, -0.5F, 2.25F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.994F, 0F, 0F, 0F, -0.7F, 0F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(16, 36).addBox(0F, -0.25F, -0.25F, 2.25F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l2 = p_horn_l1.addOrReplaceChild("horn_l2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.994F, 0F, 0F, 0F, -0.6F, 0F));
        p_horn_l2.addOrReplaceChild("horn_l2_b", CubeListBuilder.create().texOffs(40, 32).addBox(0F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, -7.75F, -4.5F, 0F, 0.5F, 1F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(46, 32).addBox(-2.25F, -0.5F, -0.5F, 2.25F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.994F, 0F, 0F, 0F, 0.7F, 0F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(22, 36).addBox(-2.25F, -0.25F, -0.25F, 2.25F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r2 = p_horn_r1.addOrReplaceChild("horn_r2", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.994F, 0F, 0F, 0F, 0.6F, 0F));
        p_horn_r2.addOrReplaceChild("horn_r2_b", CubeListBuilder.create().texOffs(53, 32).addBox(-1.75F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, 15.5F, -4.25F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(9, 25).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(47, 25).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(14, 32).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, 15.5F, -4.25F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(17, 25).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(53, 25).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(21, 32).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, 15.5F, 4.25F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(52, 13).addBox(-1F, -0.5F, -1.5F, 2F, 4.5F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(35, 25).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(0, 32).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, 15.5F, 4.25F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(0, 25).addBox(-1F, -0.5F, -1.5F, 2F, 4.5F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(41, 25).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(7, 32).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.875F, 6.25F, -0.9F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(28, 32).addBox(-0.75F, -2.5F, 0F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** goat_angora_f */
    private static LayerDefinition m46() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 13F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(21, 23).addBox(-2.75F, -3.5F, -5.75F, 5.5F, 7F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 23).addBox(-3.25F, -3F, -2F, 6.5F, 7F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(34, 34).addBox(-2.75F, -3.5F, 2F, 5.5F, 6.5F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("udder", CubeListBuilder.create().texOffs(50, 45).addBox(-1.75F, 3.5F, 0F, 3.5F, 2F, 3.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0", CubeListBuilder.create().texOffs(55, 59).addBox(0.75F, 5.5F, 1.5F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1", CubeListBuilder.create().texOffs(57, 59).addBox(-1.25F, 5.5F, 1.5F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("mohair", CubeListBuilder.create().texOffs(0, 0).addBox(-4.25F, -4.5F, -6.25F, 8.5F, 9.5F, 13.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("mohair2", CubeListBuilder.create().texOffs(0, 34).addBox(-3.25F, -5F, -4.75F, 6.5F, 0.5F, 10.25F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2F, -5.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(44, 0).addBox(-1.5F, -5F, -3.5F, 3F, 7.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 45).addBox(-2F, -8F, -6.5F, 4F, 4.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(0, 54).addBox(-1.5F, -5.5F, -8.5F, 3F, 3F, 2F), PartPose.ZERO);
        p_head.addOrReplaceChild("topknot", CubeListBuilder.create().texOffs(34, 54).addBox(-2.25F, -9F, -6F, 4.5F, 1.5F, 3F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, -7F, -4F, -0.45F, 0.25F, 1.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(39, 59).addBox(0F, -0.5F, -0.25F, 3.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, -7F, -4F, -0.45F, -0.25F, -1.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(47, 59).addBox(-3.5F, -0.5F, -0.25F, 3.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, -7.75F, -4.5F, 0F, -0.3F, -0.4F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(19, 59).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.15F, 0F, 0F, 1F, -0.5F, 0F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(59, 59).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l2 = p_horn_l1.addOrReplaceChild("horn_l2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.15F, 0F, 0F, 1F, -0.5F, 0F));
        p_horn_l2.addOrReplaceChild("horn_l2_b", CubeListBuilder.create().texOffs(24, 59).addBox(0F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l3 = p_horn_l2.addOrReplaceChild("horn_l3", CubeListBuilder.create(), PartPose.offsetAndRotation(1.325F, 0F, 0F, 1F, -0.5F, 0F));
        p_horn_l3.addOrReplaceChild("horn_l3_b", CubeListBuilder.create().texOffs(0, 63).addBox(0F, -0.25F, -0.25F, 1.25F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, -7.75F, -4.5F, 0F, 0.3F, 0.4F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(29, 59).addBox(-1.5F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.15F, 0F, 0F, 1F, 0.5F, 0F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(4, 63).addBox(-1.5F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r2 = p_horn_r1.addOrReplaceChild("horn_r2", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.15F, 0F, 0F, 1F, 0.5F, 0F));
        p_horn_r2.addOrReplaceChild("horn_r2_b", CubeListBuilder.create().texOffs(34, 59).addBox(-1.5F, -0.5F, -0.5F, 1.5F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r3 = p_horn_r2.addOrReplaceChild("horn_r3", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.325F, 0F, 0F, 1F, 0.5F, 0F));
        p_horn_r3.addOrReplaceChild("horn_r3_b", CubeListBuilder.create().texOffs(8, 63).addBox(-1F, -0.25F, -0.25F, 1.25F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.5F, 15.5F, -3.875F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(34, 45).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(22, 54).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(0, 59).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.5F, 15.5F, -3.875F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(42, 45).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(28, 54).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(7, 59).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.5F, 15.5F, 3.875F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(16, 45).addBox(-1F, -0.5F, -1.5F, 2F, 4.5F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(10, 54).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(49, 54).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.5F, 15.5F, 3.875F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(25, 45).addBox(-1F, -0.5F, -1.5F, 2F, 4.5F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(16, 54).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(56, 54).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.25F, 5.875F, -0.9F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(14, 59).addBox(-0.75F, -2.5F, 0F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** goat_angora_m */
    private static LayerDefinition m47() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 13F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(21, 23).addBox(-2.75F, -3.5F, -5.75F, 5.5F, 7F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(0, 23).addBox(-3.25F, -3F, -2F, 6.5F, 7F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(34, 34).addBox(-2.75F, -3.5F, 2F, 5.5F, 6.5F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("mohair", CubeListBuilder.create().texOffs(0, 0).addBox(-4.25F, -4.5F, -6.25F, 8.5F, 9.5F, 13.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("mohair2", CubeListBuilder.create().texOffs(0, 34).addBox(-3.25F, -5F, -4.75F, 6.5F, 0.5F, 10.25F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2F, -5.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(44, 0).addBox(-1.75F, -5F, -3.5F, 3.5F, 7.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 45).addBox(-2F, -8F, -6.5F, 4F, 4.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(50, 45).addBox(-1.5F, -5.5F, -8.5F, 3F, 3F, 2F), PartPose.ZERO);
        p_head.addOrReplaceChild("beard", CubeListBuilder.create().texOffs(39, 54).addBox(-0.75F, -2.5F, -8F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        p_head.addOrReplaceChild("topknot", CubeListBuilder.create().texOffs(24, 54).addBox(-2.25F, -9F, -6F, 4.5F, 1.5F, 3F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, -7F, -4F, -0.45F, 0.25F, 1.25F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(43, 59).addBox(0F, -0.5F, -0.25F, 3.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, -7F, -4F, -0.45F, -0.25F, -1.25F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(51, 59).addBox(-3.5F, -0.5F, -0.25F, 3.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l0 = p_head.addOrReplaceChild("horn_l0", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, -7.75F, -4.5F, 0F, -0.3F, -0.4F));
        p_horn_l0.addOrReplaceChild("horn_l0_b", CubeListBuilder.create().texOffs(19, 59).addBox(0F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l1 = p_horn_l0.addOrReplaceChild("horn_l1", CubeListBuilder.create(), PartPose.offsetAndRotation(1.525F, 0F, 0F, 1F, -0.5F, 0F));
        p_horn_l1.addOrReplaceChild("horn_l1_b", CubeListBuilder.create().texOffs(59, 59).addBox(0F, -0.25F, -0.25F, 2F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_l2 = p_horn_l1.addOrReplaceChild("horn_l2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.525F, 0F, 0F, 1F, -0.5F, 0F));
        p_horn_l2.addOrReplaceChild("horn_l2_b", CubeListBuilder.create().texOffs(25, 59).addBox(0F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_l3 = p_horn_l2.addOrReplaceChild("horn_l3", CubeListBuilder.create(), PartPose.offsetAndRotation(1.7F, 0F, 0F, 1F, -0.5F, 0F));
        p_horn_l3.addOrReplaceChild("horn_l3_b", CubeListBuilder.create().texOffs(0, 63).addBox(0F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r0 = p_head.addOrReplaceChild("horn_r0", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, -7.75F, -4.5F, 0F, 0.3F, 0.4F));
        p_horn_r0.addOrReplaceChild("horn_r0_b", CubeListBuilder.create().texOffs(31, 59).addBox(-1.75F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r1 = p_horn_r0.addOrReplaceChild("horn_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.525F, 0F, 0F, 1F, 0.5F, 0F));
        p_horn_r1.addOrReplaceChild("horn_r1_b", CubeListBuilder.create().texOffs(4, 63).addBox(-1.75F, -0.25F, -0.25F, 2F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_horn_r2 = p_horn_r1.addOrReplaceChild("horn_r2", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.525F, 0F, 0F, 1F, 0.5F, 0F));
        p_horn_r2.addOrReplaceChild("horn_r2_b", CubeListBuilder.create().texOffs(37, 59).addBox(-1.75F, -0.5F, -0.5F, 2F, 1F, 1F), PartPose.ZERO);
        PartDefinition p_horn_r3 = p_horn_r2.addOrReplaceChild("horn_r3", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.7F, 0F, 0F, 1F, 0.5F, 0F));
        p_horn_r3.addOrReplaceChild("horn_r3_b", CubeListBuilder.create().texOffs(9, 63).addBox(-1.5F, -0.25F, -0.25F, 1.5F, 0.5F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.5F, 15.5F, -3.875F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(34, 45).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(12, 54).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(0, 59).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.5F, 15.5F, -3.875F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(42, 45).addBox(-1F, -0.5F, -1F, 2F, 4.5F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(18, 54).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(7, 59).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.5F, 15.5F, 3.875F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(16, 45).addBox(-1F, -0.5F, -1.5F, 2F, 4.5F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(0, 54).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(44, 54).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.5F, 15.5F, 3.875F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(25, 45).addBox(-1F, -0.5F, -1.5F, 2F, 4.5F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(6, 54).addBox(-0.75F, 4F, -0.75F, 1.5F, 3F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(51, 54).addBox(-0.75F, 7F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.25F, 5.875F, -0.9F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(14, 59).addBox(-0.75F, -2.5F, 0F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** goat_poitevine_f */
    private static LayerDefinition m48() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 12.125F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(36, 0).addBox(-2.5F, -3.25F, -6.25F, 5.25F, 6.75F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(14, 0).addBox(-3F, -2.75F, -2.25F, 6.25F, 6.75F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 12).addBox(-2.5F, -3.25F, 2.25F, 5.25F, 6.25F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("udder", CubeListBuilder.create().texOffs(8, 23).addBox(-1.75F, 3.5F, 0.25F, 3.5F, 2F, 3.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat0", CubeListBuilder.create().texOffs(43, 30).addBox(0.75F, 5.5F, 1.75F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("teat1", CubeListBuilder.create().texOffs(45, 30).addBox(-1.25F, 5.5F, 1.75F, 0.5F, 1F, 0.5F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.875F, -5.75F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -5F, -3.5F, 3F, 7.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(19, 12).addBox(-2F, -8F, -6.5F, 4F, 4.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(46, 23).addBox(-1.5F, -5.5F, -8.5F, 3F, 3F, 2F), PartPose.ZERO);
        p_head.addOrReplaceChild("beard", CubeListBuilder.create().texOffs(26, 30).addBox(-0.75F, -2.5F, -8F, 1.5F, 1.5F, 1F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, -7F, -4F, 0.15F, 0.1F, -0.75F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(31, 30).addBox(0F, -0.5F, -0.25F, 2.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, -7F, -4F, 0.15F, -0.1F, 0.75F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(37, 30).addBox(-2.5F, -0.5F, -0.25F, 2.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.375F, 14.5F, -4.25F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(53, 12).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(34, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(7, 30).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.375F, 14.5F, -4.25F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 23).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(40, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(14, 30).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.375F, 14.5F, 4.25F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 12).addBox(-1F, -0.5F, -1.5F, 2F, 4.75F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(22, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(56, 23).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.375F, 14.5F, 4.25F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(44, 12).addBox(-1F, -0.5F, -1.5F, 2F, 4.75F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(28, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(0, 30).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.125F, 6.25F, -0.9F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(21, 30).addBox(-0.75F, -2.5F, 0F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** goat_poitevine_m */
    private static LayerDefinition m49() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 12.125F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(37, 0).addBox(-2.5F, -3.25F, -6.25F, 5.25F, 6.75F, 4F), PartPose.ZERO);
        p_body.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(15, 0).addBox(-3F, -2.75F, -2.25F, 6.25F, 6.75F, 4.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("hind", CubeListBuilder.create().texOffs(0, 12).addBox(-2.5F, -3.25F, 2.25F, 5.25F, 6.25F, 4F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.875F, -5.75F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 0).addBox(-1.75F, -5F, -3.5F, 3.5F, 7.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(19, 12).addBox(-2F, -8F, -6.5F, 4F, 4.5F, 4F), PartPose.ZERO);
        p_head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(32, 23).addBox(-1.5F, -5.5F, -8.5F, 3F, 3F, 2F), PartPose.ZERO);
        p_head.addOrReplaceChild("beard", CubeListBuilder.create().texOffs(42, 23).addBox(-0.75F, -2.5F, -8F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        PartDefinition p_ear_l = p_head.addOrReplaceChild("ear_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, -7F, -4F, 0.15F, 0.1F, -0.75F));
        p_ear_l.addOrReplaceChild("ear_l_b", CubeListBuilder.create().texOffs(19, 30).addBox(0F, -0.5F, -0.25F, 2.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_ear_r = p_head.addOrReplaceChild("ear_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, -7F, -4F, 0.15F, -0.1F, 0.75F));
        p_ear_r.addOrReplaceChild("ear_r_b", CubeListBuilder.create().texOffs(25, 30).addBox(-2.5F, -0.5F, -0.25F, 2.5F, 1.25F, 0.5F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.375F, 14.5F, -4.25F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_up", CubeListBuilder.create().texOffs(53, 12).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_lo", CubeListBuilder.create().texOffs(20, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fl.addOrReplaceChild("leg_fl_hoof", CubeListBuilder.create().texOffs(0, 30).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.375F, 14.5F, -4.25F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_up", CubeListBuilder.create().texOffs(0, 23).addBox(-1F, -0.5F, -1F, 2F, 4.75F, 2F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_lo", CubeListBuilder.create().texOffs(26, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_fr.addOrReplaceChild("leg_fr_hoof", CubeListBuilder.create().texOffs(7, 30).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(1.375F, 14.5F, 4.25F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_up", CubeListBuilder.create().texOffs(35, 12).addBox(-1F, -0.5F, -1.5F, 2F, 4.75F, 2.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(8, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_bl.addOrReplaceChild("leg_bl_hoof", CubeListBuilder.create().texOffs(47, 23).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.375F, 14.5F, 4.25F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_up", CubeListBuilder.create().texOffs(44, 12).addBox(-1F, -0.5F, -1.5F, 2F, 4.75F, 2.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(14, 23).addBox(-0.75F, 4.25F, -0.75F, 1.5F, 3.75F, 1.5F), PartPose.ZERO);
        p_leg_br.addOrReplaceChild("leg_br_hoof", CubeListBuilder.create().texOffs(54, 23).addBox(-0.75F, 8F, -1.25F, 1.5F, 1.5F, 2F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -3.125F, 6.25F, -0.9F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(14, 30).addBox(-0.75F, -2.5F, 0F, 1.5F, 2.5F, 1F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** sheep_fur_merino */
    private static LayerDefinition m50() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 13F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("fleece", CubeListBuilder.create().texOffs(0, 0).addBox(-5.5F, -5.25F, -8F, 11F, 10F, 16.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("back", CubeListBuilder.create().texOffs(0, 45).addBox(-3F, -6.25F, -6F, 6F, 1F, 12F), PartPose.ZERO);
        p_body.addOrReplaceChild("rump", CubeListBuilder.create().texOffs(0, 58).addBox(-4.5F, -4.75F, 8.5F, 9F, 8.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("flank_l", CubeListBuilder.create().texOffs(0, 27).addBox(5.5F, -3.75F, -5F, 0.5F, 7.5F, 10.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("flank_r", CubeListBuilder.create().texOffs(22, 27).addBox(-6F, -3.75F, -5F, 0.5F, 7.5F, 10.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(36, 45).addBox(-4.5F, -4.25F, -9F, 9F, 8.5F, 1F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.75F, -6F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(20, 58).addBox(-3.5F, -3.25F, -3.5F, 7F, 6.25F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("fold", CubeListBuilder.create().texOffs(0, 76).addBox(-3.25F, 1.5F, -3.25F, 6.5F, 2F, 1.75F), PartPose.ZERO);
        p_head.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(24, 68).addBox(-3F, -5.5F, -7F, 6F, 2F, 4F), PartPose.ZERO);
        PartDefinition p_leg_fl = root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.25F, 15.75F, -4.25F, 0F, 0F, 0F));
        p_leg_fl.addOrReplaceChild("leg_fl_w", CubeListBuilder.create().texOffs(0, 68).addBox(-1.5F, 0.5F, -1.5F, 3F, 4.25F, 3F), PartPose.ZERO);
        PartDefinition p_leg_fr = root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.25F, 15.75F, -4.25F, 0F, 0F, 0F));
        p_leg_fr.addOrReplaceChild("leg_fr_w", CubeListBuilder.create().texOffs(12, 68).addBox(-1.5F, 0.5F, -1.5F, 3F, 4.25F, 3F), PartPose.ZERO);
        PartDefinition p_leg_bl = root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.25F, 15.75F, 4.25F, 0F, 0F, 0F));
        p_leg_bl.addOrReplaceChild("leg_bl_w", CubeListBuilder.create().texOffs(40, 58).addBox(-1.5F, 0.5F, -1.5F, 3F, 4.25F, 3F), PartPose.ZERO);
        PartDefinition p_leg_br = root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.25F, 15.75F, 4.25F, 0F, 0F, 0F));
        p_leg_br.addOrReplaceChild("leg_br_w", CubeListBuilder.create().texOffs(52, 58).addBox(-1.5F, 0.5F, -1.5F, 3F, 4.25F, 3F), PartPose.ZERO);
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.5F, 6.75F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_w", CubeListBuilder.create().texOffs(44, 68).addBox(-1.5F, 0F, -1F, 3F, 3.5F, 2.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** sheep_fur_suffolk */
    private static LayerDefinition m51() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 11.375F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("fleece", CubeListBuilder.create().texOffs(0, 0).addBox(-5F, -4.75F, -7.75F, 10.25F, 9.5F, 16.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("back", CubeListBuilder.create().texOffs(0, 45).addBox(-3F, -5.75F, -6.25F, 6.25F, 1F, 12.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("rump", CubeListBuilder.create().texOffs(19, 59).addBox(-4F, -4.25F, 8.5F, 8.25F, 8F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("flank_l", CubeListBuilder.create().texOffs(0, 26).addBox(5.25F, -3.25F, -5.25F, 0.5F, 7F, 11.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("flank_r", CubeListBuilder.create().texOffs(24, 26).addBox(-5.5F, -3.25F, -5.25F, 0.5F, 7F, 11.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(0, 59).addBox(-4F, -3.75F, -8.75F, 8.25F, 8F, 1F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.875F, -6.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(38, 45).addBox(-3.25F, -3.25F, -3.5F, 6.5F, 6.25F, 3F), PartPose.ZERO);
        root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.375F, 14.25F, -4.625F, 0F, 0F, 0F));
        root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.375F, 14.25F, -4.625F, 0F, 0F, 0F));
        root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.375F, 14.25F, 4.625F, 0F, 0F, 0F));
        root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.375F, 14.25F, 4.625F, 0F, 0F, 0F));
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.625F, 7.125F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_w", CubeListBuilder.create().texOffs(38, 59).addBox(-1.5F, 0F, -1F, 3F, 3.5F, 2.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** sheep_fur_texel */
    private static LayerDefinition m52() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 12.375F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("fleece", CubeListBuilder.create().texOffs(0, 0).addBox(-5.25F, -5F, -7.25F, 10.75F, 10F, 15.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("back", CubeListBuilder.create().texOffs(0, 44).addBox(-3.25F, -6F, -5.75F, 6.75F, 1F, 11.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("rump", CubeListBuilder.create().texOffs(0, 57).addBox(-4.25F, -4.5F, 8F, 8.75F, 8.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("flank_l", CubeListBuilder.create().texOffs(0, 26).addBox(5.5F, -3.5F, -4.75F, 0.5F, 7.5F, 10.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("flank_r", CubeListBuilder.create().texOffs(22, 26).addBox(-5.75F, -3.5F, -4.75F, 0.5F, 7.5F, 10.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(37, 44).addBox(-4.25F, -4F, -8.25F, 8.75F, 8.5F, 1F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.125F, -5.875F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(20, 57).addBox(-3.25F, -3.25F, -3.5F, 6.5F, 6.25F, 3F), PartPose.ZERO);
        root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, 15.5F, -4.125F, 0F, 0F, 0F));
        root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, 15.5F, -4.125F, 0F, 0F, 0F));
        root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.625F, 15.5F, 4.125F, 0F, 0F, 0F));
        root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.625F, 15.5F, 4.125F, 0F, 0F, 0F));
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.875F, 6.625F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_w", CubeListBuilder.create().texOffs(39, 57).addBox(-1.5F, 0F, -1F, 3F, 3.5F, 2.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }

    /** sheep_fur_ouessant */
    private static LayerDefinition m53() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 13F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("fleece", CubeListBuilder.create().texOffs(0, 0).addBox(-4.75F, -4.5F, -6.75F, 9.5F, 8.75F, 14.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("back", CubeListBuilder.create().texOffs(0, 39).addBox(-2.75F, -5.5F, -5.25F, 5.5F, 1F, 10.75F), PartPose.ZERO);
        p_body.addOrReplaceChild("rump", CubeListBuilder.create().texOffs(17, 51).addBox(-3.75F, -4F, 7.5F, 7.5F, 7.25F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("flank_l", CubeListBuilder.create().texOffs(0, 23).addBox(4.75F, -3F, -4.25F, 0.5F, 6.25F, 9.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("flank_r", CubeListBuilder.create().texOffs(20, 23).addBox(-5.25F, -3F, -4.25F, 0.5F, 6.25F, 9.25F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(0, 51).addBox(-3.75F, -3.5F, -7.75F, 7.5F, 7.25F, 1F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.5F, -5.375F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(33, 39).addBox(-3.25F, -3.25F, -3.5F, 6.5F, 6.25F, 3F), PartPose.ZERO);
        root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, 15.5F, -3.625F, 0F, 0F, 0F));
        root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, 15.5F, -3.625F, 0F, 0F, 0F));
        root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, 15.5F, 3.625F, 0F, 0F, 0F));
        root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2F, 15.5F, 3.625F, 0F, 0F, 0F));
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.25F, 6.125F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_w", CubeListBuilder.create().texOffs(34, 51).addBox(-1.5F, 0F, -1F, 3F, 3.5F, 2.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** sheep_fur_jacob */
    private static LayerDefinition m54() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 12.375F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("fleece", CubeListBuilder.create().texOffs(0, 0).addBox(-4.75F, -4.5F, -7.5F, 9.75F, 9F, 15.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("back", CubeListBuilder.create().texOffs(0, 42).addBox(-2.75F, -5.5F, -6F, 5.75F, 1F, 12F), PartPose.ZERO);
        p_body.addOrReplaceChild("rump", CubeListBuilder.create().texOffs(18, 55).addBox(-3.75F, -4F, 8F, 7.75F, 7.5F, 1F), PartPose.ZERO);
        p_body.addOrReplaceChild("flank_l", CubeListBuilder.create().texOffs(0, 25).addBox(5F, -3F, -5F, 0.5F, 6.5F, 10.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("flank_r", CubeListBuilder.create().texOffs(22, 25).addBox(-5.25F, -3F, -5F, 0.5F, 6.5F, 10.5F), PartPose.ZERO);
        p_body.addOrReplaceChild("breast", CubeListBuilder.create().texOffs(0, 55).addBox(-3.75F, -3.5F, -8.5F, 7.75F, 7.5F, 1F), PartPose.ZERO);
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -1.625F, -6F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(36, 42).addBox(-3.25F, -3.25F, -3.5F, 6.5F, 6.25F, 3F), PartPose.ZERO);
        p_head.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(36, 55).addBox(-3F, -5.5F, -7F, 6F, 2F, 4F), PartPose.ZERO);
        root.addOrReplaceChild("leg_fl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.125F, 15F, -4.25F, 0F, 0F, 0F));
        root.addOrReplaceChild("leg_fr", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.125F, 15F, -4.25F, 0F, 0F, 0F));
        root.addOrReplaceChild("leg_bl", CubeListBuilder.create(), PartPose.offsetAndRotation(2.125F, 15F, 4.25F, 0F, 0F, 0F));
        root.addOrReplaceChild("leg_br", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.125F, 15F, 4.25F, 0F, 0F, 0F));
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2.375F, 6.75F, 0.35F, 0F, 0F));
        p_tail.addOrReplaceChild("tail_w", CubeListBuilder.create().texOffs(0, 64).addBox(-1.5F, 0F, -1F, 3F, 3.5F, 2.5F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 128);
    }
}
