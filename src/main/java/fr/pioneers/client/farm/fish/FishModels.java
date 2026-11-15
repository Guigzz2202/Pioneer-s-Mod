package fr.pioneers.client.farm.fish;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERE par tools/gc/farm2/gen_java.py depuis fish_shapes.py : un modele par espece de poisson (ordre de GeneratedFish.SPECIES ; 1.0.3-beta,
 * avant : 10 silhouettes partagees) et ondulation de nage. Robes 2 texels par pixel (uv normalises : la taille declaree reste celle du patron).
 */
public final class FishModels {

    private FishModels() {
    }

    /** Par espece : chemins des maillons qui ondulent (corps, arriere ou troncons, queue), part de l'oscillation, dephasage. */
    public static final String[][] SWAY_PATHS = {
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/seg1_j", "body/seg1_j/seg2_j", "body/seg1_j/seg2_j/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/seg1_j", "body/seg1_j/seg2_j", "body/seg1_j/seg2_j/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/seg1_j", "body/seg1_j/seg2_j", "body/seg1_j/seg2_j/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/seg1_j", "body/seg1_j/seg2_j", "body/seg1_j/seg2_j/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/seg1_j", "body/seg1_j/seg2_j", "body/seg1_j/seg2_j/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/seg1_j", "body/seg1_j/seg2_j", "body/seg1_j/seg2_j/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/seg1_j", "body/seg1_j/seg2_j", "body/seg1_j/seg2_j/seg3_j", "body/seg1_j/seg2_j/seg3_j/tail"},
        {"body", "body/rear", "body/rear/tail"},
        {"body", "body/rear", "body/rear/tail"}
    };
    public static final float[][] SWAY = {
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.15F, 0F, 0.5F, 0.6F, 0.7F, 1.2F, 0.9F, 1.8F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.15F, 0F, 0.5F, 0.6F, 0.7F, 1.2F, 0.9F, 1.8F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.15F, 0F, 0.5F, 0.6F, 0.7F, 1.2F, 0.9F, 1.8F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.15F, 0F, 0.5F, 0.6F, 0.7F, 1.2F, 0.9F, 1.8F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.15F, 0F, 0.5F, 0.6F, 0.7F, 1.2F, 0.9F, 1.8F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.15F, 0F, 0.5F, 0.6F, 0.7F, 1.2F, 0.9F, 1.8F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.15F, 0F, 0.5F, 0.6F, 0.7F, 1.2F, 0.9F, 1.8F, 0.9F, 2.4F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F},
        {0.08F, 0F, 0.38F, 0.6F, 0.9F, 1.2F}
    };

    public static LayerDefinition create(int species) {
        return switch (species) {
            case 0 -> brook_trout();
            case 1 -> arctic_char();
            case 2 -> grayling();
            case 3 -> pike();
            case 4 -> salmon();
            case 5 -> sturgeon();
            case 6 -> burbot();
            case 7 -> carp();
            case 8 -> perch();
            case 9 -> roach();
            case 10 -> bream();
            case 11 -> zander();
            case 12 -> catfish();
            case 13 -> tench();
            case 14 -> eel();
            case 15 -> barbel();
            case 16 -> tilapia();
            case 17 -> piranha();
            case 18 -> arapaima();
            case 19 -> electric_eel();
            case 20 -> discus();
            case 21 -> gar();
            case 22 -> snakehead();
            case 23 -> bowfin();
            case 24 -> cavefish();
            case 25 -> glowfish();
            case 26 -> cod();
            case 27 -> herring();
            case 28 -> mackerel();
            case 29 -> pollock();
            case 30 -> halibut();
            case 31 -> wolffish();
            case 32 -> sea_bass();
            case 33 -> sea_bream();
            case 34 -> sardine();
            case 35 -> anchovy();
            case 36 -> sole();
            case 37 -> turbot();
            case 38 -> red_mullet();
            case 39 -> conger();
            case 40 -> monkfish();
            case 41 -> tuna();
            case 42 -> swordfish();
            case 43 -> mahi_mahi();
            case 44 -> barracuda();
            case 45 -> grouper();
            case 46 -> clownfish();
            case 47 -> pufferfish();
            case 48 -> lionfish();
            case 49 -> tropical_fish();
            case 50 -> anglerfish();
            case 51 -> lanternfish();
            case 52 -> oarfish();
            case 53 -> coelacanth();
            case 54 -> gray_reef_shark();
            default -> brook_trout();
        };
    }

    private static LayerDefinition brook_trout() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(0, 9).addBox(-1F, -1.5F, -4F, 2F, 2.5F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(22, 0).addBox(-1.5F, -2.5F, -3F, 3F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -2.5F, 0F, 3F, 5F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(34, 0).addBox(-1F, -2F, 0F, 2F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(50, 0).addBox(-1F, -1.5F, 3F, 2F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(44, 0).addBox(0F, -5F, 1.5F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(10, 9).addBox(0F, -2.5F, 3.2F, 0F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(6, 9).addBox(0F, 2F, 0F, 0F, 1F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_rear.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(58, 0).addBox(0F, 1.5F, 2F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(14, 0).addBox(0F, -2.5F, 0F, 0F, 5F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(12, 9).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1.5F, 1.125F, -0.2F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(20, 9).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1.5F, 1.125F, -0.2F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition arctic_char() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -5.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 7F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(0, 11).addBox(-1F, -1F, -5.5F, 2F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(36, 0).addBox(-1F, -1.5F, -3.5F, 2F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(8, 0).addBox(-1F, -2F, -0.5F, 2F, 4F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(24, 0).addBox(-1F, -1.5F, 0F, 2F, 3F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(46, 0).addBox(-0.5F, -1F, 4F, 1F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(54, 0).addBox(0F, -4F, 1.5F, 0F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(16, 11).addBox(0F, -2F, 4.2F, 0F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(8, 11).addBox(0F, 1.5F, 4.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_rear.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(12, 11).addBox(0F, 1F, 2.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3.5F, 0F, 0F, 7F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(18, 11).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 0.9F, -0.5F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(26, 11).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 0.9F, -0.5F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition grayling() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(4, 12).addBox(-1F, -1F, -4F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(46, 0).addBox(-1F, -1.5F, -3F, 2F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(22, 0).addBox(-1F, -2F, -1F, 2F, 4F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(36, 0).addBox(-1F, -1.5F, 0F, 2F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(54, 0).addBox(-0.5F, -1F, 3F, 1F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -6.5F, -0.5F, 0F, 5F, 7F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_body.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(60, 0).addBox(0F, 1.5F, 3.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(0, 12).addBox(0F, 1F, 2F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_rear.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(10, 12).addBox(0F, -2F, 3.4F, 0F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(14, 0).addBox(0F, -3F, 0F, 0F, 6F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(12, 12).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 0.9F, -0.8F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(20, 12).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 0.9F, -0.8F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition pike() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -5.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 7F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(8, 10).addBox(-1F, -0.5F, -5.5F, 2F, 1F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(0, 10).addBox(-1F, -1.5F, -2.5F, 2F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -2F, -0.5F, 2F, 4F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(24, 0).addBox(-1F, -2F, 0F, 2F, 4F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(36, 0).addBox(-1F, -1.5F, 4F, 2F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_rear.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(46, 0).addBox(0F, -5F, 1F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(52, 0).addBox(0F, 1.5F, 1.5F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(18, 10).addBox(0F, 2F, 2.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(16, 0).addBox(0F, -3F, 0F, 0F, 6F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(22, 10).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 0.9F, 0F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(30, 10).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 0.9F, 0F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition salmon() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -5.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 7F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(0, 12).addBox(-1F, -1F, -6F, 2F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(28, 0).addBox(-1F, -2F, -4F, 2F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -2.5F, -1F, 3F, 4.5F, 7F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(38, 0).addBox(-1F, -1.5F, 0F, 2F, 3F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(56, 0).addBox(-0.5F, -1F, 4F, 1F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(50, 0).addBox(0F, -5.5F, 2F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(16, 12).addBox(0F, -2F, 5.2F, 0F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(8, 12).addBox(0F, 1.5F, 5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_rear.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(12, 12).addBox(0F, 1F, 3F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(20, 0).addBox(0F, -3.5F, 0F, 0F, 7F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(18, 12).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1.5F, 0.9F, -0.8F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(26, 12).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1.5F, 0.9F, -0.8F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition sturgeon() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -6.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 8F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(26, 10).addBox(-1F, -0.5F, -6F, 2F, 1F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(46, 0).addBox(-1F, -1.5F, -3F, 2F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(10, 0).addBox(-1.5F, -1.5F, 0F, 3F, 3F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(0, 10).addBox(-1F, -1F, 0F, 2F, 2F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(12, 10).addBox(-0.5F, -1F, 4F, 1F, 1F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_rear.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(36, 10).addBox(0F, -3F, 2.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(22, 10).addBox(0F, 0F, 2.5F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(40, 10).addBox(0F, 1F, 5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -4F, 0F, 0F, 5F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(44, 10).addBox(0F, 0F, 0F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(1.5F, 0.675F, 0.5F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(54, 10).addBox(-3F, 0F, 0F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(-1.5F, 0.675F, 0.5F, 0F, 0F, -0.5F));
        PartDefinition p_scute_back = p_body.addOrReplaceChild("scute_back", CubeListBuilder.create().texOffs(28, 0).addBox(-0.5F, -2.5F, -1F, 1F, 1F, 8F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_barbels = p_body.addOrReplaceChild("barbels", CubeListBuilder.create().texOffs(0, 16).addBox(-1F, 0.5F, -4.5F, 2F, 1F, 0F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition burbot() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -6.5F, 0F, 0F, 0F));
        PartDefinition p_seg1_j = p_body.addOrReplaceChild("seg1_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg2_j = p_seg1_j.addOrReplaceChild("seg2_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_seg2_j.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(36, 11).addBox(-1.5F, -1F, -4F, 3F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(6, 11).addBox(-2F, -1.5F, -3F, 4F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(34, 0).addBox(-1.5F, -1.5F, 0F, 3F, 3F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_seg1_j.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(50, 0).addBox(-1F, -1F, 0F, 2F, 2F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_seg2_j.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(20, 11).addBox(-1F, -1F, 0F, 2F, 2F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(32, 11).addBox(0F, -3.5F, 2F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_seg1_j.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3F, 0F, 0F, 2F, 9F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_seg1_j.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(18, 0).addBox(0F, 1F, 0.5F, 0F, 2F, 8F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 11).addBox(0F, -2F, 0F, 0F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(44, 11).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1.5F, 0.675F, 0F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(52, 11).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1.5F, 0.675F, 0F, 0F, 0F, -0.5F));
        PartDefinition p_chin_barbel = p_body.addOrReplaceChild("chin_barbel", CubeListBuilder.create().texOffs(60, 11).addBox(-0.5F, 1.5F, -3.5F, 1F, 2F, 0F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition carp() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(16, 11).addBox(-1F, -1F, -4F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(48, 0).addBox(-1.5F, -2.5F, -3F, 3F, 4.5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(22, 0).addBox(-1.5F, -3.5F, 0F, 3F, 6F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(36, 0).addBox(-1.5F, -3F, 0F, 3F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(0, 11).addBox(-1F, -1.5F, 3F, 2F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -5.5F, 0.5F, 0F, 4F, 7F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(8, 11).addBox(0F, 1.5F, 2F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(12, 11).addBox(0F, 2F, 3F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(14, 0).addBox(0F, -3.5F, 0F, 0F, 7F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(22, 11).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1.5F, 0.9F, -0.4F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(30, 11).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1.5F, 0.9F, -0.4F, 0F, 0F, -0.6F));
        PartDefinition p_barbels = p_body.addOrReplaceChild("barbels", CubeListBuilder.create().texOffs(38, 11).addBox(-1F, 0.5F, -3.8F, 2F, 1F, 0F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition perch() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(4, 10).addBox(-1F, -1F, -4F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(20, 0).addBox(-1F, -2F, -3F, 2F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(8, 0).addBox(-1F, -3F, 0F, 2F, 5F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(38, 0).addBox(-1F, -2F, 0F, 2F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(58, 0).addBox(-0.5F, -1F, 3F, 1F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(30, 0).addBox(0F, -6F, 0F, 0F, 3F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(48, 0).addBox(0F, -4F, 0.5F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(54, 0).addBox(0F, 1F, 2.5F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_rear.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(0, 10).addBox(0F, 1F, 2F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3F, 0F, 0F, 6F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(10, 10).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 0.9F, -0.4F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(18, 10).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 0.9F, -0.4F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition roach() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(54, 0).addBox(-1F, -1.5F, -3.5F, 2F, 2.5F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(20, 0).addBox(-1F, -2.5F, -2.5F, 2F, 5F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(8, 0).addBox(-1F, -3.5F, -0.5F, 2F, 6F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(28, 0).addBox(-1F, -2.5F, 0F, 2F, 4.5F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(44, 0).addBox(-0.5F, -1F, 2F, 1F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(36, 0).addBox(0F, -6.5F, 1F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(40, 0).addBox(0F, 1F, 0.5F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(50, 0).addBox(0F, 2.5F, 1F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3.5F, 0F, 0F, 7F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(0, 11).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 1.125F, -1.1F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(8, 11).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 1.125F, -1.1F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition bream() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(0, 12).addBox(-0.5F, -1F, -3.5F, 1F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(42, 0).addBox(-1F, -2.5F, -2.5F, 2F, 5F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -4F, -0.5F, 2F, 8F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(32, 0).addBox(-1F, -3F, 0F, 2F, 6F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(50, 0).addBox(-0.5F, -1.5F, 3F, 1F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(56, 0).addBox(0F, -7F, 1.5F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_body.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(20, 0).addBox(0F, 1.5F, 2F, 0F, 5F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(60, 0).addBox(0F, 4F, -0.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(12, 0).addBox(0F, -4F, 0F, 0F, 8F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(4, 12).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 1.125F, -0.9F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(12, 12).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 1.125F, -0.9F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition zander() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -5.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 7F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(20, 10).addBox(-1F, -1F, -4.5F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(50, 0).addBox(-1F, -1.5F, -3.5F, 2F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(8, 0).addBox(-1F, -2F, -0.5F, 2F, 4F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(22, 0).addBox(-1F, -2F, 0F, 2F, 4F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(0, 10).addBox(-1F, -1.5F, 4F, 2F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(34, 0).addBox(0F, -5F, 0.5F, 0F, 3F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(42, 0).addBox(0F, -4.5F, 0.5F, 0F, 3F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(10, 10).addBox(0F, 1.5F, 2F, 0F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_body.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(16, 10).addBox(0F, 2F, 2F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3F, 0F, 0F, 6F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(26, 10).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 0.9F, -0.1F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(34, 10).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 0.9F, -0.1F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition catfish() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -6F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 9F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(30, 13).addBox(-3F, -1F, -5F, 6F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(0, 13).addBox(-3F, -1.5F, -3F, 6F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(18, 0).addBox(-2F, -2F, 0F, 4F, 4F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(36, 0).addBox(-1.5F, -2F, 0F, 3F, 3F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(18, 13).addBox(-1F, -1.5F, 5F, 2F, 2F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(46, 13).addBox(0F, -3F, 2F, 0F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(0, 0).addBox(0F, 0.5F, -1F, 0F, 4F, 9F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(52, 0).addBox(0F, -2.5F, 0F, 0F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(48, 13).addBox(0F, 0F, 0F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(2F, 0.9F, 0F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(0, 19).addBox(-3F, 0F, 0F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(-2F, 0.9F, 0F, 0F, 0F, -0.5F));
        PartDefinition p_whisker_left = p_body.addOrReplaceChild("whisker_left", CubeListBuilder.create().texOffs(18, 19).addBox(0F, 0.5F, 0F, 7F, 0F, 1F),
              PartPose.offsetAndRotation(3F, 0.5F, -4.5F, 0F, -0.5F, 0.15F));
        PartDefinition p_whisker_right = p_body.addOrReplaceChild("whisker_right", CubeListBuilder.create().texOffs(34, 19).addBox(-7F, 0.5F, 0F, 7F, 0F, 1F),
              PartPose.offsetAndRotation(-3F, 0.5F, -4.5F, 0F, 0.5F, -0.15F));
        PartDefinition p_chin_barbels = p_body.addOrReplaceChild("chin_barbels", CubeListBuilder.create().texOffs(10, 19).addBox(-2F, 1F, -4F, 4F, 2F, 0F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition tench() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(0, 10).addBox(-1F, -1F, -4.5F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(22, 0).addBox(-1.5F, -2F, -3.5F, 3F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -2.5F, -0.5F, 3F, 4.5F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(34, 0).addBox(-1F, -2F, 0F, 2F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(44, 0).addBox(-1F, -1.5F, 3F, 2F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(52, 0).addBox(0F, -4.5F, 1.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(56, 0).addBox(0F, 2F, 0.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(60, 0).addBox(0F, 2F, 2F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(16, 0).addBox(0F, -3F, 0F, 0F, 6F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(6, 10).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1.5F, 0.9F, -0.9F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(14, 10).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1.5F, 0.9F, -0.9F, 0F, 0F, -0.6F));
        PartDefinition p_barbels = p_body.addOrReplaceChild("barbels", CubeListBuilder.create().texOffs(22, 10).addBox(-1F, 0.5F, -4.3F, 2F, 1F, 0F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition eel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -8F, 0F, 0F, 0F));
        PartDefinition p_seg1_j = p_body.addOrReplaceChild("seg1_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_seg2_j = p_seg1_j.addOrReplaceChild("seg2_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_tail = p_seg2_j.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(44, 14).addBox(-0.5F, -0.5F, -4F, 1F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(34, 14).addBox(-1F, -1F, -3F, 2F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(48, 0).addBox(-1F, -1F, 0F, 2F, 2F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_seg1_j.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(0, 14).addBox(-1F, -1F, 0F, 2F, 2F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_seg2_j.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(16, 14).addBox(-0.5F, -1F, 0F, 1F, 1F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_seg1_j.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -2F, -2F, 0F, 1F, 13F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_seg1_j.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(26, 0).addBox(0F, 0F, 0F, 0F, 2F, 11F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(28, 14).addBox(0F, -1.5F, 0F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(48, 14).addBox(0F, 0F, 0F, 1F, 0F, 1F),
              PartPose.offsetAndRotation(1F, 0.45F, -0.4F, 0F, 0F, 0.4F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(52, 14).addBox(-1F, 0F, 0F, 1F, 0F, 1F),
              PartPose.offsetAndRotation(-1F, 0.45F, -0.4F, 0F, 0F, -0.4F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition barbel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(0, 10).addBox(-1F, -0.5F, -4.5F, 2F, 1.5F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(34, 0).addBox(-1F, -1.5F, -2.5F, 2F, 3.5F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(8, 0).addBox(-1F, -2F, -0.5F, 2F, 4F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(22, 0).addBox(-1F, -2F, 0F, 2F, 4F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(46, 0).addBox(-0.5F, -1F, 4F, 1F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(42, 0).addBox(0F, -5F, 2F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(52, 0).addBox(0F, 2F, 2F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(56, 0).addBox(0F, 2F, 3.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3F, 0F, 0F, 6F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(8, 10).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 0.9F, -0.1F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(16, 10).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 0.9F, -0.1F, 0F, 0F, -0.5F));
        PartDefinition p_barbels = p_body.addOrReplaceChild("barbels", CubeListBuilder.create().texOffs(24, 10).addBox(-1F, 1F, -4F, 2F, 2F, 0F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_barbels2 = p_body.addOrReplaceChild("barbels2", CubeListBuilder.create().texOffs(28, 10).addBox(-1F, 1F, -3F, 2F, 1F, 0F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition tilapia() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(10, 11).addBox(-1F, -1F, -4F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(34, 0).addBox(-1F, -2.5F, -3F, 2F, 4.5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(16, 0).addBox(-1F, -3F, 0F, 2F, 6F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(44, 0).addBox(-1F, -2.5F, 0F, 2F, 4.5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(54, 0).addBox(-0.5F, -1.5F, 3F, 1F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -5.5F, -1F, 0F, 3F, 8F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(0, 11).addBox(0F, 2F, 0F, 0F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(6, 11).addBox(0F, 3F, 1F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(28, 0).addBox(0F, -3F, 0F, 0F, 6F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(16, 11).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 0.9F, -0.8F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(24, 11).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 0.9F, -0.8F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition piranha() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(48, 0).addBox(-1F, -2F, -3.5F, 2F, 4F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(20, 0).addBox(-1.5F, -3F, -2.5F, 3F, 6F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -3.5F, -0.5F, 3F, 7F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(30, 0).addBox(-1F, -2.5F, 0F, 2F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(54, 0).addBox(-0.5F, -1.5F, 3F, 1F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(60, 0).addBox(0F, -6.5F, 1.5F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(4, 11).addBox(0F, -3.5F, 2.5F, 0F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(40, 0).addBox(0F, 1.5F, 3F, 0F, 4F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_body.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(0, 11).addBox(0F, 3.5F, 0.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(14, 0).addBox(0F, -3F, 0F, 0F, 6F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(16, 11).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1.5F, 1.35F, -0.9F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(24, 11).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1.5F, 1.35F, -0.9F, 0F, 0F, -0.6F));
        PartDefinition p_jaw = p_body.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(6, 11).addBox(-1.5F, 1.5F, -5F, 3F, 1F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition arapaima() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -6.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 9F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(34, 11).addBox(-2F, -1F, -5.5F, 4F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(20, 11).addBox(-2F, -2F, -3.5F, 4F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-2F, -2.5F, -0.5F, 4F, 5F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(28, 0).addBox(-1.5F, -2.5F, 0F, 3F, 5F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(44, 0).addBox(-1.5F, -2.5F, 5F, 3F, 5F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_rear.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 11).addBox(0F, -5.5F, 2F, 0F, 3F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(10, 11).addBox(0F, 2.5F, 2F, 0F, 3F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(46, 11).addBox(0F, 2.5F, 3.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(20, 0).addBox(0F, -3.5F, 0F, 0F, 7F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(50, 11).addBox(0F, 0F, 0F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(2F, 1.125F, -0.5F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(0, 19).addBox(-3F, 0F, 0F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(-2F, 1.125F, -0.5F, 0F, 0F, -0.5F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition electric_eel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -7.5F, 0F, 0F, 0F));
        PartDefinition p_seg1_j = p_body.addOrReplaceChild("seg1_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_seg2_j = p_seg1_j.addOrReplaceChild("seg2_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_tail = p_seg2_j.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(48, 20).addBox(-1.5F, -1F, -5F, 3F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(30, 20).addBox(-1.5F, -1.5F, -3F, 3F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(34, 0).addBox(-1.5F, -1.5F, 0F, 3F, 3F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_seg1_j.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(0, 20).addBox(-1F, -1.5F, 0F, 2F, 3F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_seg2_j.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(16, 20).addBox(-1F, -1F, 0F, 2F, 2F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 0).addBox(0F, 1F, 0F, 0F, 3F, 17F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(42, 20).addBox(0F, -1F, 0F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(58, 20).addBox(0F, 0F, 0F, 1F, 0F, 1F),
              PartPose.offsetAndRotation(1.5F, 0.675F, -1F, 0F, 0F, 0.4F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(0, 29).addBox(-1F, 0F, 0F, 1F, 0F, 1F),
              PartPose.offsetAndRotation(-1.5F, 0.675F, -1F, 0F, 0F, -0.4F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition discus() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -3F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(6, 13).addBox(-0.5F, -1.5F, -3.5F, 1F, 3F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(44, 0).addBox(-1F, -3F, -2.5F, 2F, 6F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -4.5F, -0.5F, 2F, 9F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(52, 0).addBox(-1F, -3F, 0F, 2F, 6F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(10, 13).addBox(-0.5F, -1.5F, 2F, 1F, 3F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(12, 0).addBox(0F, -6.5F, -2F, 0F, 5F, 8F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_body.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(28, 0).addBox(0F, 1.5F, -2F, 0F, 5F, 8F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 13).addBox(0F, -2.5F, 0F, 0F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(14, 13).addBox(0F, 0F, 0F, 2F, 0F, 1F),
              PartPose.offsetAndRotation(1F, 1.35F, -1.3F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(20, 13).addBox(-2F, 0F, 0F, 2F, 0F, 1F),
              PartPose.offsetAndRotation(-1F, 1.35F, -1.3F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition gar() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -5.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 8F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(36, 0).addBox(-0.5F, -0.5F, -6F, 1F, 1F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(0, 9).addBox(-1F, -1F, -2F, 2F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -1.5F, 0F, 2F, 3F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(16, 0).addBox(-1F, -1.5F, 0F, 2F, 3F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(46, 0).addBox(-1F, -1F, 5F, 2F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_rear.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(56, 0).addBox(0F, -4F, 4F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(60, 0).addBox(0F, 1F, 3.5F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(8, 9).addBox(0F, 1.5F, 5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(30, 0).addBox(0F, -2.5F, 0F, 0F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(12, 9).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 0.675F, 0.5F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(20, 9).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 0.675F, 0.5F, 0F, 0F, -0.5F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition snakehead() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -6F, 0F, 0F, 0F));
        PartDefinition p_seg1_j = p_body.addOrReplaceChild("seg1_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_seg2_j = p_seg1_j.addOrReplaceChild("seg2_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_seg2_j.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(42, 15).addBox(-1.5F, -1F, -5F, 3F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(20, 15).addBox(-1.5F, -1.5F, -3F, 3F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(46, 0).addBox(-1.5F, -1.5F, 0F, 3F, 3F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_seg1_j.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(0, 15).addBox(-1F, -1.5F, 0F, 2F, 3F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_seg2_j.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(32, 15).addBox(-1F, -1F, 0F, 2F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3F, 0F, 0F, 2F, 13F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_seg1_j.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(26, 0).addBox(0F, 1F, -3F, 0F, 2F, 10F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(14, 15).addBox(0F, -2F, 0F, 0F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(52, 15).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1.5F, 0.675F, 0F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(0, 23).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1.5F, 0.675F, 0F, 0F, 0F, -0.5F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition bowfin() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(30, 13).addBox(-1F, -1F, -4.5F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(0, 13).addBox(-1.5F, -2F, -3.5F, 3F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(20, 0).addBox(-1.5F, -2F, -0.5F, 3F, 4F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(36, 0).addBox(-1F, -2F, 0F, 2F, 4F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(12, 13).addBox(-1F, -1.5F, 4F, 2F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -4.5F, 0F, 0F, 3F, 10F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(20, 13).addBox(0F, 2F, 1F, 0F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(26, 13).addBox(0F, 2F, 2.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(48, 0).addBox(0F, -2.5F, 0F, 0F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(36, 13).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1.5F, 0.9F, -0.5F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(44, 13).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1.5F, 0.9F, -0.5F, 0F, 0F, -0.5F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition cavefish() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -3.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(46, 0).addBox(-1F, -1F, -3F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(16, 0).addBox(-1F, -1.5F, -2F, 2F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(6, 0).addBox(-1F, -2F, 0F, 2F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(24, 0).addBox(-1F, -1.5F, 0F, 2F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(36, 0).addBox(-0.5F, -1F, 2F, 1F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(32, 0).addBox(0F, -4.5F, 1.5F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(42, 0).addBox(0F, 1F, 0.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(52, 0).addBox(0F, 2F, 2F, 0F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -2.5F, 0F, 0F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(54, 0).addBox(0F, 0F, 0F, 2F, 0F, 1F),
              PartPose.offsetAndRotation(1F, 0.675F, -0.4F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(0, 8).addBox(-2F, 0F, 0F, 2F, 0F, 1F),
              PartPose.offsetAndRotation(-1F, 0.675F, -0.4F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition glowfish() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -2.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 2F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(54, 0).addBox(-1.5F, -1.5F, -3F, 3F, 3F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(20, 0).addBox(-2F, -2.5F, -2F, 4F, 5F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-2F, -3F, 0F, 4F, 6F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(32, 0).addBox(-1.5F, -2F, 0F, 3F, 4F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(42, 0).addBox(0F, -5.5F, -0.5F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_body.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(48, 0).addBox(0F, 3F, 0F, 0F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(14, 0).addBox(0F, -2.5F, 0F, 0F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(0, 9).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(2F, 1.125F, -1F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(8, 9).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-2F, 1.125F, -1F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition cod() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(9, 9).addBox(-1.5F, -1.5F, -4F, 3F, 3F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(32, 0).addBox(-1.5F, -2.5F, -3F, 3F, 4.5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -2.5F, 0F, 3F, 5F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(20, 0).addBox(-1F, -2F, 0F, 2F, 4F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(17, 9).addBox(-1F, -1F, 4F, 2F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(5, 9).addBox(0F, -5.5F, 0.5F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_body.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(44, 0).addBox(0F, -5F, 3.5F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(56, 0).addBox(0F, -4F, 3F, 0F, 3F, 2.5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_body.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(50, 0).addBox(0F, 2F, 3.5F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin4 = p_rear.addOrReplaceChild("fin4", CubeListBuilder.create().texOffs(0, 9).addBox(0F, 1F, 3F, 0F, 3F, 2.5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin5 = p_body.addOrReplaceChild("fin5", CubeListBuilder.create().texOffs(25, 9).addBox(0F, 2F, -0.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(14, 0).addBox(0F, -3F, 0F, 0F, 6F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(29, 9).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1.5F, 1.125F, 0F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(37, 9).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1.5F, 1.125F, 0F, 0F, 0F, -0.5F));
        PartDefinition p_barbel = p_body.addOrReplaceChild("barbel", CubeListBuilder.create().texOffs(45, 9).addBox(-0.5F, 1.5F, -3.4F, 1F, 1F, 0F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition herring() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(52, 0).addBox(-1F, -1F, -3.5F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(30, 0).addBox(-1F, -1.5F, -2.5F, 2F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(8, 0).addBox(-1F, -1.5F, -0.5F, 2F, 3.5F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(20, 0).addBox(-1F, -1.5F, 0F, 2F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(38, 0).addBox(-0.5F, -1F, 3F, 1F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(44, 0).addBox(0F, -3.5F, 1.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(48, 0).addBox(0F, 1F, 2F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(58, 0).addBox(0F, 2F, 2.5F, 0F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3F, 0F, 0F, 6F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(0, 10).addBox(0F, 0F, 0F, 2F, 0F, 1F),
              PartPose.offsetAndRotation(1F, 0.675F, -0.9F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(6, 10).addBox(-2F, 0F, 0F, 2F, 0F, 1F),
              PartPose.offsetAndRotation(-1F, 0.675F, -0.9F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition mackerel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(58, 0).addBox(-1F, -1F, -4F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(34, 0).addBox(-1F, -1.5F, -3F, 2F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(8, 0).addBox(-1F, -2F, -1F, 2F, 4F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(22, 0).addBox(-1F, -1.5F, 0F, 2F, 3F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(0, 10).addBox(-0.5F, -0.5F, 4F, 1F, 1F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(54, 0).addBox(0F, -4F, 0F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(6, 10).addBox(0F, -3.5F, 0.5F, 0F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(8, 10).addBox(0F, 1.5F, 0.5F, 0F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_rear.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(42, 0).addBox(0F, -2.5F, 2.5F, 0F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin4 = p_rear.addOrReplaceChild("fin4", CubeListBuilder.create().texOffs(48, 0).addBox(0F, 0.5F, 2.5F, 0F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin5 = p_body.addOrReplaceChild("fin5", CubeListBuilder.create().texOffs(10, 10).addBox(0F, 2F, 1.5F, 0F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3F, 0F, 0F, 6F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(12, 10).addBox(0F, 0F, 0F, 2F, 0F, 1F),
              PartPose.offsetAndRotation(1F, 0.9F, -1F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(18, 10).addBox(-2F, 0F, 0F, 2F, 0F, 1F),
              PartPose.offsetAndRotation(-1F, 0.9F, -1F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition pollock() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(12, 10).addBox(-1F, -1F, -4F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(34, 0).addBox(-1F, -2F, -3F, 2F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(8, 0).addBox(-1.5F, -2.5F, 0F, 3F, 4.5F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(22, 0).addBox(-1F, -2F, 0F, 2F, 4F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(6, 10).addBox(-0.5F, -1F, 4F, 1F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(60, 0).addBox(0F, -5.5F, 0.5F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_body.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(44, 0).addBox(0F, -5F, 3.5F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(50, 0).addBox(0F, -4F, 2.5F, 0F, 3F, 2.5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_body.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(0, 10).addBox(0F, 2F, 3.5F, 0F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin4 = p_rear.addOrReplaceChild("fin4", CubeListBuilder.create().texOffs(55, 0).addBox(0F, 1F, 2.5F, 0F, 3F, 2.5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3F, 0F, 0F, 6F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(24, 10).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 0.9F, -0.2F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(32, 10).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 0.9F, -0.2F, 0F, 0F, -0.5F));
        PartDefinition p_jaw = p_body.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(18, 10).addBox(-0.5F, 0.5F, -5.5F, 1F, 1F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition halibut() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 22.5F, -5.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 7F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(52, 37).addBox(-2F, -0.5F, -5F, 4F, 1F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(0, 37).addBox(-3.5F, -1F, -3F, 7F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 30).addBox(-4.5F, -1F, 0F, 9F, 2F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(28, 30).addBox(-3.5F, -1F, 0F, 7F, 2F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(20, 37).addBox(-1.5F, -0.5F, 4F, 3F, 1F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(32, 37).addBox(-3F, 0F, 0F, 6F, 0F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fringe_left = p_body.addOrReplaceChild("fringe_left", CubeListBuilder.create().texOffs(0, 0).addBox(1F, 0F, -3.5F, 6F, 0F, 15F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fringe_right = p_body.addOrReplaceChild("fringe_right", CubeListBuilder.create().texOffs(0, 15).addBox(-7F, 0F, -3.5F, 6F, 0F, 15F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition wolffish() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -6F, 0F, 0F, 0F));
        PartDefinition p_seg1_j = p_body.addOrReplaceChild("seg1_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg2_j = p_seg1_j.addOrReplaceChild("seg2_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_seg2_j.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(46, 16).addBox(-1.5F, -1.5F, -5F, 3F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(14, 16).addBox(-2F, -2F, -3F, 4F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(42, 0).addBox(-1.5F, -2F, 0F, 3F, 4F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_seg1_j.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(0, 16).addBox(-1F, -1.5F, 0F, 2F, 3F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_seg2_j.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(34, 16).addBox(-1F, -1F, 0F, 2F, 2F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -4F, 0F, 0F, 3F, 13F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_seg1_j.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(26, 0).addBox(0F, 1F, 0F, 0F, 2F, 8F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(28, 16).addBox(0F, -2F, 0F, 0F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(0, 24).addBox(0F, 0F, 0F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(1.5F, 0.9F, 0F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(10, 24).addBox(-3F, 0F, 0F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(-1.5F, 0.9F, 0F, 0F, 0F, -0.5F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition sea_bass() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(8, 10).addBox(-1F, -1F, -4.5F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(24, 0).addBox(-1F, -2F, -3.5F, 2F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -2.5F, -0.5F, 3F, 5F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(34, 0).addBox(-1F, -2F, 0F, 2F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(56, 0).addBox(-0.5F, -1F, 3F, 1F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(44, 0).addBox(0F, -5.5F, 0F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_body.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(50, 0).addBox(0F, -5F, 3.5F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(0, 10).addBox(0F, 2F, 0.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_body.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(4, 10).addBox(0F, 2.5F, 1.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(16, 0).addBox(0F, -3F, 0F, 0F, 6F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(14, 10).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1.5F, 1.125F, -0.5F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(22, 10).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1.5F, 1.125F, -0.5F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition sea_bream() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(4, 11).addBox(-1F, -1.5F, -3.5F, 2F, 2.5F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(32, 0).addBox(-1F, -3F, -2.5F, 2F, 6F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(8, 0).addBox(-1F, -3.5F, -0.5F, 2F, 6.5F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2.5F, 0F, 2F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(56, 0).addBox(-0.5F, -1.5F, 3F, 1F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(20, 0).addBox(0F, -5.5F, 0F, 0F, 3F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(50, 0).addBox(0F, 1.5F, 0.5F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(0, 11).addBox(0F, 3F, 1F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3.5F, 0F, 0F, 7F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(10, 11).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 1.35F, -0.5F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(18, 11).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 1.35F, -0.5F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition sardine() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -3F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(36, 0).addBox(-1F, -1F, -3F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(16, 0).addBox(-1F, -1.5F, -2F, 2F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(6, 0).addBox(-1F, -1.5F, 0F, 2F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(24, 0).addBox(-1F, -1F, 0F, 2F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(42, 0).addBox(-0.5F, -0.5F, 2F, 1F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(32, 0).addBox(0F, -3.5F, 0.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(46, 0).addBox(0F, 1F, 0F, 0F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(48, 0).addBox(0F, 1.5F, 1.5F, 0F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -2F, 0F, 0F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(50, 0).addBox(0F, 0F, 0F, 1F, 0F, 1F),
              PartPose.offsetAndRotation(1F, 0.675F, -1F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(54, 0).addBox(-1F, 0F, 0F, 1F, 0F, 1F),
              PartPose.offsetAndRotation(-1F, 0.675F, -1F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition anchovy() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -3F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(28, 0).addBox(-1F, -1.5F, -3F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(16, 0).addBox(-0.5F, -1F, -2F, 1F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(6, 0).addBox(-1F, -1F, 0F, 2F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(22, 0).addBox(-0.5F, -1F, 0F, 1F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(36, 0).addBox(-0.5F, -0.5F, 2F, 1F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(34, 0).addBox(0F, -3F, 1F, 0F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(40, 0).addBox(0F, 1F, 0F, 0F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(42, 0).addBox(0F, 1F, 1F, 0F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -2F, 0F, 0F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(44, 0).addBox(0F, 0F, 0F, 1F, 0F, 1F),
              PartPose.offsetAndRotation(0.5F, 0.45F, -1.4F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(48, 0).addBox(-1F, 0F, 0F, 1F, 0F, 1F),
              PartPose.offsetAndRotation(-0.5F, 0.45F, -1.4F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition sole() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 22.5F, -2.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(54, 10).addBox(-1.5F, -0.5F, -4F, 3F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(24, 10).addBox(-2.5F, -1F, -3F, 5F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 10).addBox(-3.5F, -1F, -1F, 7F, 2F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(38, 10).addBox(-2.5F, -0.5F, 0F, 5F, 1F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 17).addBox(-2F, 0F, 0F, 4F, 0F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fringe_left = p_body.addOrReplaceChild("fringe_left", CubeListBuilder.create().texOffs(0, 0).addBox(2F, 0F, -2.5F, 3F, 0F, 10F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fringe_right = p_body.addOrReplaceChild("fringe_right", CubeListBuilder.create().texOffs(26, 0).addBox(-5F, 0F, -2.5F, 3F, 0F, 10F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition turbot() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 22.5F, -3F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(36, 14).addBox(-2F, -0.5F, -3.5F, 4F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(28, 8).addBox(-4F, -1F, -2.5F, 8F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 8).addBox(-5F, -1F, -0.5F, 10F, 2F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(0, 14).addBox(-3.5F, -1F, 0F, 7F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(46, 14).addBox(-1.5F, -0.5F, 2F, 3F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(18, 14).addBox(-3F, 0F, 0F, 6F, 0F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fringe_left = p_body.addOrReplaceChild("fringe_left", CubeListBuilder.create().texOffs(0, 0).addBox(1F, 0F, -2F, 5F, 0F, 8F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fringe_right = p_body.addOrReplaceChild("fringe_right", CubeListBuilder.create().texOffs(26, 0).addBox(-6F, 0F, -2F, 5F, 0F, 8F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition red_mullet() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(54, 0).addBox(-1F, -1.5F, -3.5F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(18, 0).addBox(-1F, -2F, -2.5F, 2F, 4F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -2F, -0.5F, 2F, 4F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(26, 0).addBox(-1F, -1.5F, 0F, 2F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(40, 0).addBox(-0.5F, -1F, 3F, 1F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(36, 0).addBox(0F, -5F, 0F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(46, 0).addBox(0F, -3.5F, 0F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(50, 0).addBox(0F, 1.5F, 0.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_body.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(60, 0).addBox(0F, 2F, 0.5F, 0F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(12, 0).addBox(0F, -2.5F, 0F, 0F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(4, 8).addBox(0F, 0F, 0F, 2F, 0F, 1F),
              PartPose.offsetAndRotation(1F, 0.9F, -0.9F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(10, 8).addBox(-2F, 0F, 0F, 2F, 0F, 1F),
              PartPose.offsetAndRotation(-1F, 0.9F, -0.9F, 0F, 0F, -0.6F));
        PartDefinition p_barbels = p_body.addOrReplaceChild("barbels", CubeListBuilder.create().texOffs(0, 8).addBox(-1F, 1.5F, -3.3F, 2F, 2F, 0F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition conger() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -7.5F, 0F, 0F, 0F));
        PartDefinition p_seg1_j = p_body.addOrReplaceChild("seg1_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_seg2_j = p_seg1_j.addOrReplaceChild("seg2_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_tail = p_seg2_j.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(6, 28).addBox(-1.5F, -1F, -5F, 3F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(48, 19).addBox(-1.5F, -1.5F, -3F, 3F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 19).addBox(-1.5F, -1.5F, 0F, 3F, 3F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_seg1_j.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(18, 19).addBox(-1F, -1.5F, 0F, 2F, 3F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_seg2_j.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(34, 19).addBox(-1F, -1F, 0F, 2F, 2F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3F, 0F, 0F, 2F, 17F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_seg1_j.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(34, 0).addBox(0F, 1F, -2F, 0F, 2F, 13F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 28).addBox(0F, -1.5F, 0F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(16, 28).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1.5F, 0.675F, -1F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(24, 28).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1.5F, 0.675F, -1F, 0F, 0F, -0.5F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition monkfish() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(0, 8).addBox(-4F, -2F, -4F, 8F, 4F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -2.5F, -3F, 9F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, 0F, 6F, 4F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(50, 0).addBox(-2F, -1.5F, 0F, 4F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(18, 8).addBox(-1F, -1F, 3F, 2F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(32, 8).addBox(0F, -4F, 2F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(28, 8).addBox(0F, -4F, 2F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(36, 8).addBox(0F, 1F, 2F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(44, 0).addBox(0F, -2.5F, 0F, 0F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(0, 13).addBox(0F, 0F, 0F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(3F, 0.9F, 1F, 0F, 0F, 0.4F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(10, 13).addBox(-3F, 0F, 0F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(-3F, 0.9F, 1F, 0F, 0F, -0.4F));
        PartDefinition p_lure = p_body.addOrReplaceChild("lure", CubeListBuilder.create().texOffs(40, 8).addBox(-0.5F, -5F, -3.5F, 1F, 3F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fringe_left = p_body.addOrReplaceChild("fringe_left", CubeListBuilder.create().texOffs(44, 8).addBox(4.5F, 0.5F, -3.8F, 2F, 0F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fringe_right = p_body.addOrReplaceChild("fringe_right", CubeListBuilder.create().texOffs(54, 8).addBox(-6.5F, 0.5F, -3.8F, 2F, 0F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition tuna() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -5.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 7F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(20, 13).addBox(-1F, -1F, -4.5F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(40, 0).addBox(-1.5F, -2.5F, -3.5F, 3F, 4.5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(8, 0).addBox(-2F, -3F, -0.5F, 4F, 6F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(26, 0).addBox(-1.5F, -2F, 0F, 3F, 4F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(12, 13).addBox(-0.5F, -0.5F, 4F, 1F, 1F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 13).addBox(0F, -6F, 0.5F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(4, 13).addBox(0F, -5F, 0F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(8, 13).addBox(0F, 2F, 0F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_rear.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(52, 0).addBox(0F, -3.5F, 3F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin4 = p_rear.addOrReplaceChild("fin4", CubeListBuilder.create().texOffs(58, 0).addBox(0F, 0.5F, 3F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -4.5F, 0F, 0F, 9F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(26, 13).addBox(0F, 0F, 0F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(2F, 1.35F, -0.5F, 0F, 0F, 0.35F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(36, 13).addBox(-3F, 0F, 0F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(-2F, 1.35F, -0.5F, 0F, 0F, -0.35F));
        PartDefinition p_keel = p_rear.addOrReplaceChild("keel", CubeListBuilder.create().texOffs(46, 13).addBox(-1.5F, 0F, 5F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition swordfish() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -5.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 8F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 7F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(24, 0).addBox(-0.5F, -0.5F, -8F, 1F, 0.5F, 8F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(0, 14).addBox(-1F, -1.5F, 0F, 2F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(8, 0).addBox(-1.5F, -2.5F, 3F, 3F, 5F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(42, 0).addBox(-1F, -2F, 0F, 2F, 4F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(14, 14).addBox(-0.5F, -0.5F, 4F, 1F, 1F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(54, 0).addBox(0F, -7.5F, 3F, 0F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(22, 14).addBox(0F, -3.5F, 3.5F, 0F, 3F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(10, 14).addBox(0F, 2F, 1F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_rear.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(24, 14).addBox(0F, 0.5F, 3.5F, 0F, 3F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -5F, 0F, 0F, 10F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(26, 14).addBox(0F, 0F, 0F, 4F, 0F, 2F),
              PartPose.offsetAndRotation(1.5F, 1.125F, 3.5F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(38, 14).addBox(-4F, 0F, 0F, 4F, 0F, 2F),
              PartPose.offsetAndRotation(-1.5F, 1.125F, 3.5F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition mahi_mahi() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(22, 19).addBox(-1F, -3F, -4.5F, 2F, 4F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(0, 19).addBox(-1F, -3.5F, -3.5F, 2F, 6F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(36, 0).addBox(-1F, -3F, -0.5F, 2F, 6F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(10, 19).addBox(-1F, -2.5F, 0F, 2F, 4.5F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(28, 19).addBox(-0.5F, -1F, 4F, 1F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -6F, -4F, 0F, 5F, 14F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_body.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(50, 0).addBox(0F, 1F, 3.5F, 0F, 4F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(34, 19).addBox(0F, 3F, -0.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(28, 0).addBox(0F, -4F, 0F, 0F, 8F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(38, 19).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 1.125F, -1.5F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(46, 19).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 1.125F, -1.5F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition barracuda() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -6F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 8F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(16, 11).addBox(-1F, -0.5F, -5F, 2F, 1.5F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(36, 0).addBox(-1F, -1.5F, -3F, 2F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(8, 0).addBox(-1F, -2F, 0F, 2F, 4F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(22, 0).addBox(-1F, -2F, 0F, 2F, 4F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(46, 0).addBox(-0.5F, -1F, 5F, 1F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(54, 0).addBox(0F, -5F, 2F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(58, 0).addBox(0F, -4F, 3F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(0, 11).addBox(0F, 2F, 3F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_body.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(4, 11).addBox(0F, 2F, 4F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3.5F, 0F, 0F, 7F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(24, 11).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 0.9F, 0.5F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(32, 11).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 0.9F, 0.5F, 0F, 0F, -0.5F));
        PartDefinition p_jaw = p_body.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(8, 11).addBox(-0.5F, 0.5F, -6.5F, 1F, 1F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition grouper() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(6, 11).addBox(-2F, -2F, -4.5F, 4F, 4F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(24, 0).addBox(-2F, -3F, -3.5F, 4F, 6F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-2F, -3F, -0.5F, 4F, 6F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(50, 0).addBox(-1.5F, -2.5F, 0F, 3F, 4.5F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(16, 11).addBox(-1F, -2F, 4F, 2F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(38, 0).addBox(0F, -5.5F, -0.5F, 0F, 3F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(0, 11).addBox(0F, -5.5F, 1F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(24, 11).addBox(0F, 2F, 1F, 0F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_body.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(30, 11).addBox(0F, 3F, 0.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(18, 0).addBox(0F, -3.5F, 0F, 0F, 7F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(34, 11).addBox(0F, 0F, 0F, 3F, 0F, 3F),
              PartPose.offsetAndRotation(2F, 1.35F, 0F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(46, 11).addBox(-3F, 0F, 0F, 3F, 0F, 3F),
              PartPose.offsetAndRotation(-2F, 1.35F, 0F, 0F, 0F, -0.5F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition clownfish() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -3F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(46, 0).addBox(-1F, -1.5F, -3F, 2F, 2.5F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(16, 0).addBox(-1F, -2F, -2F, 2F, 4F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(6, 0).addBox(-1F, -2.5F, 0F, 2F, 4.5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(24, 0).addBox(-1F, -2F, 0F, 2F, 4F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(52, 0).addBox(-0.5F, -1F, 2F, 1F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(32, 0).addBox(0F, -5F, -0.5F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_body.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(38, 0).addBox(0F, -5F, 2.5F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(42, 0).addBox(0F, 2F, 2F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_body.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(56, 0).addBox(0F, 2F, 0F, 0F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -2.5F, 0F, 0F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(0, 8).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(1F, 0.9F, -0.6F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(8, 8).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-1F, 0.9F, -0.6F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition pufferfish() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -1.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 1F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(46, 0).addBox(-1F, -1.5F, -4F, 2F, 3F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(18, 0).addBox(-2F, -2.5F, -3F, 4F, 5F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -3F, -1F, 6F, 6F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_body.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(30, 0).addBox(-2F, -2.5F, 2F, 4F, 5F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(52, 0).addBox(-1F, -1F, 0F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(58, 0).addBox(0F, -4.5F, 2.5F, 0F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_body.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(60, 0).addBox(0F, 2.5F, 2.5F, 0F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(42, 0).addBox(0F, -2F, 0F, 0F, 4F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(40, 9).addBox(0F, 0F, 0F, 2F, 0F, 1F),
              PartPose.offsetAndRotation(2F, 1.125F, -1.5F, 0F, 0F, 0.3F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(46, 9).addBox(-2F, 0F, 0F, 2F, 0F, 1F),
              PartPose.offsetAndRotation(-2F, 1.125F, -1.5F, 0F, 0F, -0.3F));
        PartDefinition p_spike0 = p_body.addOrReplaceChild("spike0", CubeListBuilder.create().texOffs(0, 9).addBox(2.5F, -1.5F, -0.5F, 1F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_spike1 = p_body.addOrReplaceChild("spike1", CubeListBuilder.create().texOffs(4, 9).addBox(-3.5F, -1.5F, -0.5F, 1F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_spike2 = p_body.addOrReplaceChild("spike2", CubeListBuilder.create().texOffs(8, 9).addBox(2.5F, 1.5F, -0.5F, 1F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_spike3 = p_body.addOrReplaceChild("spike3", CubeListBuilder.create().texOffs(12, 9).addBox(-3.5F, 1.5F, -0.5F, 1F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_spike4 = p_body.addOrReplaceChild("spike4", CubeListBuilder.create().texOffs(16, 9).addBox(-0.5F, -3.5F, -0.5F, 1F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_spike5 = p_body.addOrReplaceChild("spike5", CubeListBuilder.create().texOffs(20, 9).addBox(2.5F, -1.5F, 0.5F, 1F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_spike6 = p_body.addOrReplaceChild("spike6", CubeListBuilder.create().texOffs(24, 9).addBox(-3.5F, -1.5F, 0.5F, 1F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_spike7 = p_body.addOrReplaceChild("spike7", CubeListBuilder.create().texOffs(28, 9).addBox(2.5F, 1.5F, 0.5F, 1F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_spike8 = p_body.addOrReplaceChild("spike8", CubeListBuilder.create().texOffs(32, 9).addBox(-3.5F, 1.5F, 0.5F, 1F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_spike9 = p_body.addOrReplaceChild("spike9", CubeListBuilder.create().texOffs(36, 9).addBox(-0.5F, -3.5F, 0.5F, 1F, 1F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition lionfish() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(50, 12).addBox(-1F, -1F, -3.5F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(46, 0).addBox(-1F, -2F, -2.5F, 2F, 4F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(12, 0).addBox(-1F, -2.5F, -0.5F, 2F, 5F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(30, 0).addBox(-1F, -2F, 0F, 2F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(44, 12).addBox(-0.5F, -1F, 3F, 1F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -8F, -2F, 0F, 6F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(40, 0).addBox(0F, -5F, 0.5F, 0F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(54, 0).addBox(0F, 2F, 0F, 0F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_body.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(60, 0).addBox(0F, 2.5F, 0F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(24, 0).addBox(0F, -3F, 0F, 0F, 6F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(0, 12).addBox(0F, 0F, 0F, 6F, 0F, 5F),
              PartPose.offsetAndRotation(1F, 0.9F, -0.7F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(22, 12).addBox(-6F, 0F, 0F, 6F, 0F, 5F),
              PartPose.offsetAndRotation(-1F, 0.9F, -0.7F, 0F, 0F, -0.5F));
        PartDefinition p_tentacle = p_body.addOrReplaceChild("tentacle", CubeListBuilder.create().texOffs(56, 12).addBox(-0.5F, -4F, -2.7F, 1F, 2F, 0F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition tropical_fish() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -3F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(60, 0).addBox(-0.5F, -1.5F, -3F, 1F, 2.5F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -3F, -2F, 2F, 6F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(24, 0).addBox(-1F, -3.5F, 0F, 2F, 7F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(48, 0).addBox(-1F, -2.5F, 0F, 2F, 5F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(56, 0).addBox(-0.5F, -1.5F, 2F, 1F, 3F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -8.5F, -1F, 0F, 6F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_body.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(12, 0).addBox(0F, 2.5F, -1F, 0F, 6F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(34, 0).addBox(0F, -3F, 0F, 0F, 6F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(0, 12).addBox(0F, 0F, 0F, 2F, 0F, 1F),
              PartPose.offsetAndRotation(1F, 1.35F, -1F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(6, 12).addBox(-2F, 0F, 0F, 2F, 0F, 1F),
              PartPose.offsetAndRotation(-1F, 1.35F, -1F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition anglerfish() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -3F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(40, 0).addBox(-2.5F, -2.5F, -3.5F, 5F, 5F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -3F, -2.5F, 6F, 6F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(18, 0).addBox(-2.5F, -2.5F, 0.5F, 5F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(52, 0).addBox(-1.5F, -1.5F, 0F, 3F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(28, 9).addBox(-1F, -1F, 2F, 2F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 9).addBox(0F, -4.5F, 3F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_body.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(4, 9).addBox(0F, 1.5F, 3F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(34, 0).addBox(0F, -2F, 0F, 0F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(34, 9).addBox(0F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(3F, 1.35F, -0.5F, 0F, 0F, 0.5F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(42, 9).addBox(-2F, 0F, 0F, 2F, 0F, 2F),
              PartPose.offsetAndRotation(-3F, 1.35F, -0.5F, 0F, 0F, -0.5F));
        PartDefinition p_lure_stalk = p_body.addOrReplaceChild("lure_stalk", CubeListBuilder.create().texOffs(8, 9).addBox(-0.5F, -6F, -2.5F, 1F, 3F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_lure_bar = p_body.addOrReplaceChild("lure_bar", CubeListBuilder.create().texOffs(12, 9).addBox(-0.5F, -6F, -5.5F, 1F, 1F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_lure_bulb = p_body.addOrReplaceChild("lure_bulb", CubeListBuilder.create().texOffs(20, 9).addBox(-1F, -6.5F, -7F, 2F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition lanternfish() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -4F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 3F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 5F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(32, 0).addBox(-1F, -1.5F, -3F, 2F, 3F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(6, 0).addBox(-1F, -2F, -2F, 2F, 4F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(14, 0).addBox(-1F, -1.5F, 0F, 2F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(24, 0).addBox(-0.5F, -1F, 0F, 1F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(42, 0).addBox(-0.5F, -0.5F, 3F, 1F, 1F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(38, 0).addBox(0F, -3.5F, 0.5F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(48, 0).addBox(0F, -2.5F, 2.4F, 0F, 2F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(50, 0).addBox(0F, 1F, 0.5F, 0F, 1F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -2.5F, 0F, 0F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(54, 0).addBox(0F, 0F, 0F, 1F, 0F, 1F),
              PartPose.offsetAndRotation(1F, 0.9F, -1F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(58, 0).addBox(-1F, 0F, 0F, 1F, 0F, 1F),
              PartPose.offsetAndRotation(-1F, 0.9F, -1F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition oarfish() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -9F, 0F, 0F, 0F));
        PartDefinition p_seg1_j = p_body.addOrReplaceChild("seg1_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_seg2_j = p_seg1_j.addOrReplaceChild("seg2_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_seg3_j = p_seg2_j.addOrReplaceChild("seg3_j", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_tail = p_seg3_j.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(58, 29).addBox(-0.5F, -1.5F, -6F, 1F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(42, 29).addBox(-0.5F, -1.5F, -4F, 1F, 3F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 29).addBox(-0.5F, -1.5F, 0F, 1F, 3F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_seg1_j.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(14, 29).addBox(-0.5F, -1.5F, 0F, 1F, 3F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_seg2_j.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(28, 29).addBox(-0.5F, -1F, 0F, 1F, 2F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg5 = p_seg3_j.addOrReplaceChild("seg5", CubeListBuilder.create().texOffs(0, 38).addBox(-0.5F, -0.5F, 0F, 1F, 1F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -3.5F, -4F, 0F, 3F, 26F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_body.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(52, 29).addBox(0F, -6.5F, -6F, 0F, 5F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_body.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(56, 29).addBox(0F, 1.5F, -4.2F, 0F, 6F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(10, 38).addBox(0F, -1F, 0F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition coelacanth() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -5.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 4.5F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 7F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(12, 11).addBox(-1.5F, -1.5F, -4.5F, 3F, 3F, 1F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(40, 0).addBox(-2F, -2.5F, -3.5F, 4F, 5F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-2F, -3F, -0.5F, 4F, 6F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(26, 0).addBox(-1.5F, -2.5F, 0F, 3F, 4.5F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(54, 0).addBox(-1F, -1.5F, 4F, 2F, 3F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(0, 11).addBox(0F, -6F, 0F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(4, 11).addBox(0F, -5.5F, 2F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(20, 11).addBox(0F, 2F, 2F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_body.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(8, 11).addBox(0F, 2F, 3.5F, 0F, 3F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(18, 0).addBox(0F, -3.5F, 0F, 0F, 7F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(36, 11).addBox(0F, 0F, 0F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(2F, 1.35F, 0F, 0F, 0F, 0.6F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(46, 11).addBox(-3F, 0F, 0F, 3F, 0F, 2F),
              PartPose.offsetAndRotation(-2F, 1.35F, 0F, 0F, 0F, -0.6F));
        PartDefinition p_lobe_left = p_body.addOrReplaceChild("lobe_left", CubeListBuilder.create().texOffs(24, 11).addBox(0F, -0.5F, 0F, 1F, 1F, 2F),
              PartPose.offsetAndRotation(2F, 1.35F, -0.5F, 0F, 0F, 0.6F));
        PartDefinition p_lobe_right = p_body.addOrReplaceChild("lobe_right", CubeListBuilder.create().texOffs(30, 11).addBox(-1F, -0.5F, 0F, 1F, 1F, 2F),
              PartPose.offsetAndRotation(-2F, 1.35F, -0.5F, 0F, 0F, -0.6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition gray_reef_shark() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 21F, -7.5F, 0F, 0F, 0F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 6F, 0F, 0F, 0F));
        PartDefinition p_tail = p_rear.addOrReplaceChild("tail", CubeListBuilder.create(),
              PartPose.offsetAndRotation(0F, 0F, 9F, 0F, 0F, 0F));
        PartDefinition p_seg0 = p_body.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(24, 14).addBox(-1F, -1F, -6F, 2F, 2F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(0, 14).addBox(-1.5F, -2.5F, -3F, 3F, 4F, 3F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg2 = p_body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(12, 0).addBox(-2F, -3F, 0F, 4F, 6F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg3 = p_rear.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(32, 0).addBox(-1.5F, -2F, 0F, 3F, 4F, 5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_seg4 = p_rear.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(12, 14).addBox(-1F, -1F, 5F, 2F, 2F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin0 = p_body.addOrReplaceChild("fin0", CubeListBuilder.create().texOffs(48, 0).addBox(0F, -8F, 1.5F, 0F, 5F, 4F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin1 = p_rear.addOrReplaceChild("fin1", CubeListBuilder.create().texOffs(34, 14).addBox(0F, -4F, 4.5F, 0F, 3F, 1.5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin2 = p_rear.addOrReplaceChild("fin2", CubeListBuilder.create().texOffs(41, 14).addBox(0F, 1F, 5F, 0F, 2F, 1.5F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_fin3 = p_rear.addOrReplaceChild("fin3", CubeListBuilder.create().texOffs(37, 14).addBox(0F, 2F, 1F, 0F, 2F, 2F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_caudal = p_tail.addOrReplaceChild("caudal", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -6F, 0F, 0F, 8F, 6F),
              PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_pect_left = p_body.addOrReplaceChild("pect_left", CubeListBuilder.create().texOffs(44, 14).addBox(0F, 0F, 0F, 6F, 0F, 3F),
              PartPose.offsetAndRotation(2F, 1.35F, 0.5F, 0F, 0F, 0.45F));
        PartDefinition p_pect_right = p_body.addOrReplaceChild("pect_right", CubeListBuilder.create().texOffs(0, 21).addBox(-6F, 0F, 0F, 6F, 0F, 3F),
              PartPose.offsetAndRotation(-2F, 1.35F, 0.5F, 0F, 0F, -0.45F));
        return LayerDefinition.create(mesh, 64, 32);
    }
}
