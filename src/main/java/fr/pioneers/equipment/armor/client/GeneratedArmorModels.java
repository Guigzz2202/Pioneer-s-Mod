package fr.pioneers.equipment.armor.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERE par tools/gc/core/armor3d.py (ne pas modifier a la main) : modeles 3D des armures portees, un par armure et par
 * emplacement (0 casque, 1 plastron, 2 jambieres, 3 bottes). Atlas 256x256 declare (image 512x512).
 */
public final class GeneratedArmorModels {

    private GeneratedArmorModels() {
    }

    public static final String[] IDS = {"bronze", "iron", "gold", "steel", "hardened_steel", "titanium", "tungsten_carbide", "nitinol", "space_suit", "thermal_suit", "acid_suit", "atmospheric_suit", "hazmat", "graviton", "stellaris", "neutronic", "singularity", "voltaic", "hydrogen_jetpack"};

    public static LayerDefinition create(String id, int slot) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition[] p = new PartDefinition[6];
        p[0] = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        p[1] = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        p[2] = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        p[3] = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        p[4] = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        p[5] = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        switch (id) {
            case "bronze" -> a_bronze(p, slot);
            case "iron" -> a_iron(p, slot);
            case "gold" -> a_gold(p, slot);
            case "steel" -> a_steel(p, slot);
            case "hardened_steel" -> a_hardened_steel(p, slot);
            case "titanium" -> a_titanium(p, slot);
            case "tungsten_carbide" -> a_tungsten_carbide(p, slot);
            case "nitinol" -> a_nitinol(p, slot);
            case "space_suit" -> a_space_suit(p, slot);
            case "thermal_suit" -> a_thermal_suit(p, slot);
            case "acid_suit" -> a_acid_suit(p, slot);
            case "atmospheric_suit" -> a_atmospheric_suit(p, slot);
            case "hazmat" -> a_hazmat(p, slot);
            case "graviton" -> a_graviton(p, slot);
            case "stellaris" -> a_stellaris(p, slot);
            case "neutronic" -> a_neutronic(p, slot);
            case "singularity" -> a_singularity(p, slot);
            case "voltaic" -> a_voltaic(p, slot);
            case "hydrogen_jetpack" -> a_hydrogen_jetpack(p, slot);
            default -> {
            }
        }
        return LayerDefinition.create(mesh, 256, 256);
    }

    private static void a_bronze(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(1F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(120, 0).addBox(-0.5F, -9.6F, -4.6F, 1F, 1.2F, 9.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(74, 33).addBox(-4.6F, -4.8F, -4.8F, 9.2F, 1F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(228, 0).addBox(-4.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(238, 0).addBox(3.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(182, 25).addBox(-4.2F, -2.2F, 3.8F, 8.4F, 3F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(154, 16).addBox(-3.4F, -9.8F, -3.4F, 6.8F, 1F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(32, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(182, 16).addBox(-3.6F, 0.8F, -3.6F, 7.2F, 6.4F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(108, 25).addBox(-0.6F, 0.8F, -4.2F, 1.2F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(56, 33).addBox(-3.8F, 7.4F, -3F, 7.6F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(200, 16).addBox(-4.4F, -0.8F, -3F, 8.8F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(0, 16).addBox(-4.6F, 9.4F, -3.2F, 9.2F, 1.4F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(56, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(142, 0).addBox(-4.8F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(34, 16).addBox(-4.6F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(230, 16).addBox(-4.4F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(114, 25).addBox(-3.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(62, 16).addBox(-3.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(72, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(170, 0).addBox(-2F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(82, 16).addBox(-1.8F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(0, 25).addBox(-1.6F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(134, 25).addBox(-1.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(110, 16).addBox(-1.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(198, 0).addBox(-4.2F, 6.2F, -2.8F, 8.4F, 3.4F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(130, 16).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(204, 25).addBox(-4.6F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(216, 25).addBox(0.4F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(88, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(154, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(96, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(228, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(104, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(168, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(106, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(240, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(24, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 33).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(16, 33).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(116, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(40, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(66, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(28, 33).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(44, 33).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(128, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(82, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_iron(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(1F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(120, 0).addBox(-0.5F, -9.6F, -4.6F, 1F, 1.2F, 9.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(46, 33).addBox(-4.6F, -4.8F, -4.8F, 9.2F, 1F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(198, 0).addBox(-4.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(208, 0).addBox(3.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(158, 25).addBox(-4.2F, -2.2F, 3.8F, 8.4F, 3F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(120, 16).addBox(-3.4F, -9.8F, -3.4F, 6.8F, 1F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(32, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(148, 16).addBox(-3.6F, 0.8F, -3.6F, 7.2F, 6.4F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(84, 25).addBox(-0.6F, 0.8F, -4.2F, 1.2F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(28, 33).addBox(-3.8F, 7.4F, -3F, 7.6F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(166, 16).addBox(-4.4F, -0.8F, -3F, 8.8F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(218, 0).addBox(-4.6F, 9.4F, -3.2F, 9.2F, 1.4F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(56, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(142, 0).addBox(-4.8F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(0, 16).addBox(-4.6F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(196, 16).addBox(-4.4F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(90, 25).addBox(-3.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(28, 16).addBox(-3.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(72, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(170, 0).addBox(-2F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(48, 16).addBox(-1.8F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(220, 16).addBox(-1.6F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(110, 25).addBox(-1.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(76, 16).addBox(-1.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(96, 16).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(180, 25).addBox(-4.6F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(192, 25).addBox(0.4F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(88, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(130, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(68, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(204, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(104, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(144, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(78, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(216, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(228, 25).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(244, 25).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(88, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(16, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(42, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(0, 33).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(16, 33).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(100, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(58, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_gold(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(1F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(120, 0).addBox(-0.5F, -9.6F, -4.6F, 1F, 1.2F, 9.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(86, 33).addBox(-4.6F, -4.8F, -4.8F, 9.2F, 1F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(198, 0).addBox(-4.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(208, 0).addBox(3.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(182, 25).addBox(-4.2F, -2.2F, 3.8F, 8.4F, 3F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(154, 16).addBox(-3.4F, -9.8F, -3.4F, 6.8F, 1F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(218, 0).addBox(-0.8F, -11.6F, -3.6F, 1.6F, 2.4F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(32, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(182, 16).addBox(-3.6F, 0.8F, -3.6F, 7.2F, 6.4F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(108, 25).addBox(-0.6F, 0.8F, -4.2F, 1.2F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(68, 33).addBox(-3.8F, 7.4F, -3F, 7.6F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(200, 16).addBox(-4.4F, -0.8F, -3F, 8.8F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(0, 16).addBox(-4.6F, 9.4F, -3.2F, 9.2F, 1.4F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(56, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(142, 0).addBox(-4.8F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(34, 16).addBox(-4.6F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(230, 16).addBox(-4.4F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(114, 25).addBox(-3.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(62, 16).addBox(-3.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(72, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(170, 0).addBox(-2F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(82, 16).addBox(-1.8F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(0, 25).addBox(-1.6F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(134, 25).addBox(-1.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(110, 16).addBox(-1.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(204, 25).addBox(-1F, 1.6F, -3.9F, 2F, 4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(130, 16).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(210, 25).addBox(-4.6F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(222, 25).addBox(0.4F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(88, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(154, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(108, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(234, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(104, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(168, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(118, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(0, 33).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(24, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(12, 33).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(28, 33).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(128, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(40, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(66, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(40, 33).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(56, 33).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(140, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(82, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_steel(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(1F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(120, 0).addBox(-0.5F, -9.6F, -4.6F, 1F, 1.2F, 9.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(46, 33).addBox(-4.6F, -4.8F, -4.8F, 9.2F, 1F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(198, 0).addBox(-4.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(208, 0).addBox(3.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(158, 25).addBox(-4.2F, -2.2F, 3.8F, 8.4F, 3F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(120, 16).addBox(-3.4F, -9.8F, -3.4F, 6.8F, 1F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(32, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(148, 16).addBox(-3.6F, 0.8F, -3.6F, 7.2F, 6.4F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(84, 25).addBox(-0.6F, 0.8F, -4.2F, 1.2F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(28, 33).addBox(-3.8F, 7.4F, -3F, 7.6F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(166, 16).addBox(-4.4F, -0.8F, -3F, 8.8F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(218, 0).addBox(-4.6F, 9.4F, -3.2F, 9.2F, 1.4F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(56, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(142, 0).addBox(-4.8F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(0, 16).addBox(-4.6F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(196, 16).addBox(-4.4F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(90, 25).addBox(-3.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(28, 16).addBox(-3.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(72, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(170, 0).addBox(-2F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(48, 16).addBox(-1.8F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(220, 16).addBox(-1.6F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(110, 25).addBox(-1.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(76, 16).addBox(-1.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(96, 16).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(180, 25).addBox(-4.6F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(192, 25).addBox(0.4F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(88, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(130, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(68, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(204, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(104, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(144, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(78, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(216, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(228, 25).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(244, 25).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(88, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(16, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(42, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(0, 33).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(16, 33).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(100, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(58, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_hardened_steel(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(1F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(120, 0).addBox(-0.5F, -9.6F, -4.6F, 1F, 1.2F, 9.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(86, 33).addBox(-4.6F, -4.8F, -4.8F, 9.2F, 1F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(198, 0).addBox(-4.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(208, 0).addBox(3.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(158, 25).addBox(-4.2F, -2.2F, 3.8F, 8.4F, 3F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(120, 16).addBox(-3.4F, -9.8F, -3.4F, 6.8F, 1F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(180, 25).addBox(-4.4F, -5.6F, -5F, 8.8F, 4F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(32, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(148, 16).addBox(-3.6F, 0.8F, -3.6F, 7.2F, 6.4F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(84, 25).addBox(-0.6F, 0.8F, -4.2F, 1.2F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(68, 33).addBox(-3.8F, 7.4F, -3F, 7.6F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(166, 16).addBox(-4.4F, -0.8F, -3F, 8.8F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(218, 0).addBox(-4.6F, 9.4F, -3.2F, 9.2F, 1.4F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(56, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(142, 0).addBox(-4.8F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(0, 16).addBox(-4.6F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(196, 16).addBox(-4.4F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(90, 25).addBox(-3.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(28, 16).addBox(-3.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(72, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(170, 0).addBox(-2F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(48, 16).addBox(-1.8F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(220, 16).addBox(-1.6F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(110, 25).addBox(-1.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(76, 16).addBox(-1.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(0, 33).addBox(-2.2F, 7.4F, -3.4F, 4.4F, 2.4F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(96, 16).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(200, 25).addBox(-4.6F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(212, 25).addBox(0.4F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(88, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(130, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(108, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(224, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(104, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(144, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(118, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(236, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(12, 33).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(28, 33).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(128, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(16, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(42, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(40, 33).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(56, 33).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(140, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(58, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_titanium(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(1F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(120, 0).addBox(-0.5F, -9.6F, -4.6F, 1F, 1.2F, 9.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(72, 33).addBox(-4.6F, -4.8F, -4.8F, 9.2F, 1F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(198, 0).addBox(-4.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(208, 0).addBox(3.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(158, 25).addBox(-4.2F, -2.2F, 3.8F, 8.4F, 3F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(120, 16).addBox(-3.4F, -9.8F, -3.4F, 6.8F, 1F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(28, 33).addBox(-4.2F, -5.2F, -5F, 8.4F, 1.4F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(32, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(148, 16).addBox(-3.6F, 0.8F, -3.6F, 7.2F, 6.4F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(84, 25).addBox(-0.6F, 0.8F, -4.2F, 1.2F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(48, 33).addBox(-3.8F, 7.4F, -3F, 7.6F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(166, 16).addBox(-4.4F, -0.8F, -3F, 8.8F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(218, 0).addBox(-4.6F, 9.4F, -3.2F, 9.2F, 1.4F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(56, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(142, 0).addBox(-4.8F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(0, 16).addBox(-4.6F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(196, 16).addBox(-4.4F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(90, 25).addBox(-3.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(28, 16).addBox(-3.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(72, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(170, 0).addBox(-2F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(48, 16).addBox(-1.8F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(220, 16).addBox(-1.6F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(110, 25).addBox(-1.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(76, 16).addBox(-1.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(66, 33).addBox(-0.8F, 2F, -3.9F, 1.6F, 1.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(96, 16).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(180, 25).addBox(-4.6F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(192, 25).addBox(0.4F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(88, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(130, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(94, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(204, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(104, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(144, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(104, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(216, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(228, 25).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(244, 25).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(114, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(16, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(42, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(0, 33).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(16, 33).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(126, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(58, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_tungsten_carbide(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(1F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(120, 0).addBox(-0.5F, -9.6F, -4.6F, 1F, 1.2F, 9.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(86, 33).addBox(-4.6F, -4.8F, -4.8F, 9.2F, 1F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(198, 0).addBox(-4.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(208, 0).addBox(3.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(158, 25).addBox(-4.2F, -2.2F, 3.8F, 8.4F, 3F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(120, 16).addBox(-3.4F, -9.8F, -3.4F, 6.8F, 1F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(180, 25).addBox(-4.4F, -5.6F, -5F, 8.8F, 4F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(32, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(148, 16).addBox(-3.6F, 0.8F, -3.6F, 7.2F, 6.4F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(84, 25).addBox(-0.6F, 0.8F, -4.2F, 1.2F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(68, 33).addBox(-3.8F, 7.4F, -3F, 7.6F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(166, 16).addBox(-4.4F, -0.8F, -3F, 8.8F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(218, 0).addBox(-4.6F, 9.4F, -3.2F, 9.2F, 1.4F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(56, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(142, 0).addBox(-4.8F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(0, 16).addBox(-4.6F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(196, 16).addBox(-4.4F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(90, 25).addBox(-3.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(28, 16).addBox(-3.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(72, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(170, 0).addBox(-2F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(48, 16).addBox(-1.8F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(220, 16).addBox(-1.6F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(110, 25).addBox(-1.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(76, 16).addBox(-1.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(0, 33).addBox(-2.2F, 7.4F, -3.4F, 4.4F, 2.4F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(96, 16).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(200, 25).addBox(-4.6F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(212, 25).addBox(0.4F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(88, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(130, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(108, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(224, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(104, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(144, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(118, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(236, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(12, 33).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(28, 33).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(128, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(16, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(42, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(40, 33).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(56, 33).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(140, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(58, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_nitinol(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(1F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(120, 0).addBox(-0.5F, -9.6F, -4.6F, 1F, 1.2F, 9.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(72, 33).addBox(-4.6F, -4.8F, -4.8F, 9.2F, 1F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(198, 0).addBox(-4.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(208, 0).addBox(3.8F, -3.6F, -3.6F, 1F, 4.4F, 4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(158, 25).addBox(-4.2F, -2.2F, 3.8F, 8.4F, 3F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(120, 16).addBox(-3.4F, -9.8F, -3.4F, 6.8F, 1F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(28, 33).addBox(-4.2F, -5.2F, -5F, 8.4F, 1.4F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(32, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(148, 16).addBox(-3.6F, 0.8F, -3.6F, 7.2F, 6.4F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(84, 25).addBox(-0.6F, 0.8F, -4.2F, 1.2F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(48, 33).addBox(-3.8F, 7.4F, -3F, 7.6F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(166, 16).addBox(-4.4F, -0.8F, -3F, 8.8F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(218, 0).addBox(-4.6F, 9.4F, -3.2F, 9.2F, 1.4F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(56, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(142, 0).addBox(-4.8F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(0, 16).addBox(-4.6F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(196, 16).addBox(-4.4F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2793F));
                p[2].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(90, 25).addBox(-3.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(28, 16).addBox(-3.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(72, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(170, 0).addBox(-2F, -3.6F, -3.4F, 6.8F, 3.4F, 6.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(48, 16).addBox(-1.8F, -0.4F, -3.2F, 6.4F, 1.6F, 6.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(220, 16).addBox(-1.6F, 1F, -3F, 6F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2793F));
                p[3].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(110, 25).addBox(-1.3F, 3.4F, -2.3F, 4.6F, 1.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(76, 16).addBox(-1.4F, 6.6F, -2.4F, 4.8F, 3.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(66, 33).addBox(-0.8F, 2F, -3.9F, 1.6F, 1.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(96, 16).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(180, 25).addBox(-4.6F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(192, 25).addBox(0.4F, 10F, -3F, 4.2F, 4F, 1F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0F));
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(88, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(130, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(94, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(204, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(104, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(144, 25).addBox(-2.4F, 4.2F, -3.2F, 4.8F, 3.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(104, 33).addBox(-1.6F, 3.4F, -3.6F, 3.2F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(216, 25).addBox(-2.2F, 0.6F, -2.9F, 4.4F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(228, 25).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(244, 25).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(114, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(16, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(42, 25).addBox(-2F, 8F, -2F, 4F, 4F, 4F, new CubeDeformation(1F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(0, 33).addBox(-2.6F, 10.4F, -3.8F, 5.2F, 2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(16, 33).addBox(-2.4F, 7F, -2.9F, 4.8F, 3F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(126, 33).addBox(-2.3F, 9.6F, -3.4F, 4.6F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(58, 25).addBox(-2.6F, 12.6F, -3.6F, 5.2F, 0.8F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_space_suit(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(38, 0).addBox(-5F, -10F, -4.2F, 10F, 9.6F, 8.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.2F, -10.05F, -5F, 8.4F, 9.7F, 10F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(138, 20).addBox(-4.2F, -10.56F, -4.2F, 8.4F, 0.66F, 8.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(92, 33).addBox(-3.6F, -11.2F, -3.6F, 7.2F, 0.6F, 7.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(136, 52).addBox(-4.4F, -8.6F, -5.5F, 8.8F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(130, 67).addBox(-4.8F, -9.1F, -5.7F, 9.6F, 0.6F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(152, 67).addBox(-4.8F, -2.7F, -5.7F, 9.6F, 0.7F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(76, 43).addBox(-5.2F, -8F, -3F, 0.5F, 4.6F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(84, 43).addBox(4.7F, -8F, -3F, 0.5F, 4.6F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(188, 60).addBox(4.9F, -7.6F, -1.4F, 1F, 2F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(196, 60).addBox(-5.9F, -7.6F, -1.4F, 1F, 2F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(58, 67).addBox(5F, -7.3F, -1.9F, 0.8F, 1.4F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(62, 67).addBox(-5.8F, -7.3F, -1.9F, 0.8F, 1.4F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(204, 60).addBox(3F, -13.6F, 2.2F, 0.4F, 2.8F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(174, 67).addBox(2.9F, -14F, 2.1F, 0.6F, 0.6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(164, 0).addBox(-5.6F, -1F, -5.6F, 11.2F, 1.6F, 11.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(0, 20).addBox(-5.65F, -0.5F, -5.65F, 11.3F, 0.5F, 11.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(76, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.9F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(124, 33).addBox(-4.9F, -0.6F, -3.4F, 9.8F, 7.2F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(174, 20).addBox(-4.4F, -0.65F, -3.9F, 8.8F, 7.3F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(66, 67).addBox(-4.95F, 3.6F, -4F, 9.9F, 0.9F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(208, 60).addBox(-2.6F, 7.2F, -4.4F, 5.2F, 2.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(90, 67).addBox(-2F, 7.6F, -4.7F, 2.2F, 1.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(178, 67).addBox(0.8F, 7.7F, -4.7F, 0.6F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(182, 67).addBox(1.6F, 7.7F, -4.7F, 0.6F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(186, 67).addBox(0.8F, 8.6F, -4.7F, 1.4F, 0.5F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(48, 20).addBox(-4.6F, 0.6F, 3.4F, 9.2F, 9.8F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(212, 0).addBox(-4F, 0.55F, 2.8F, 8F, 9.9F, 3.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(222, 60).addBox(-4F, 0.18F, 3.4F, 8F, 0.52F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(0, 67).addBox(-3.2F, 6.6F, 6.3F, 6.4F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(192, 67).addBox(-3.2F, 2F, 6.3F, 6.4F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(16, 67).addBox(-3.2F, 3.2F, 6.3F, 2.4F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(74, 20).addBox(4.6F, 1.2F, 3.4F, 1.2F, 8.4F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(84, 20).addBox(-5.8F, 1.2F, 3.4F, 1.2F, 8.4F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(24, 67).addBox(-1F, 10.4F, 3.4F, 2F, 0.8F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(100, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(146, 33).addBox(-4.1F, -3.4F, -2.6F, 6.2F, 2.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(196, 20).addBox(-3.6F, -3.45F, -3.1F, 5.2F, 2.7F, 6.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(156, 52).addBox(-3.6F, -3.75F, -2.6F, 5.2F, 0.45F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(92, 43).addBox(-4F, -0.9F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(116, 43).addBox(-4.05F, -0.5F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(180, 52).addBox(-3.9F, 3.6F, -2.9F, 5.8F, 1F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c25", CubeListBuilder.create().texOffs(144, 43).addBox(-4F, 7F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c26", CubeListBuilder.create().texOffs(168, 43).addBox(-4.05F, 7.4F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c27", CubeListBuilder.create().texOffs(172, 33).addBox(-3.9F, 8.2F, -2.4F, 5.8F, 3.9F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c28", CubeListBuilder.create().texOffs(222, 20).addBox(-3.4F, 8.15F, -2.9F, 4.8F, 4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c29", CubeListBuilder.create().texOffs(116, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c30", CubeListBuilder.create().texOffs(194, 33).addBox(-2.1F, -3.4F, -2.6F, 6.2F, 2.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c31", CubeListBuilder.create().texOffs(0, 33).addBox(-1.6F, -3.45F, -3.1F, 5.2F, 2.7F, 6.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c32", CubeListBuilder.create().texOffs(204, 52).addBox(-1.6F, -3.75F, -2.6F, 5.2F, 0.45F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c33", CubeListBuilder.create().texOffs(196, 43).addBox(-2F, -0.9F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c34", CubeListBuilder.create().texOffs(220, 43).addBox(-2.05F, -0.5F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c35", CubeListBuilder.create().texOffs(228, 52).addBox(-1.9F, 3.6F, -2.9F, 5.8F, 1F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c36", CubeListBuilder.create().texOffs(0, 52).addBox(-2F, 7F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c37", CubeListBuilder.create().texOffs(24, 52).addBox(-2.05F, 7.4F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c38", CubeListBuilder.create().texOffs(220, 33).addBox(-1.9F, 8.2F, -2.4F, 5.8F, 3.9F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c39", CubeListBuilder.create().texOffs(26, 33).addBox(-1.4F, 8.15F, -2.9F, 4.8F, 4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c40", CubeListBuilder.create().texOffs(96, 60).addBox(3.7F, 0.8F, -1.3F, 0.3F, 2.4F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 43).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.8F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(52, 52).addBox(-4.9F, 9.4F, -2.9F, 9.8F, 1.2F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(132, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.8F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(34, 67).addBox(-2.5F, 4F, -3F, 5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(104, 60).addBox(-2.1F, 3.95F, -3.4F, 4.2F, 3.1F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(98, 67).addBox(-2.55F, 5.2F, -3.5F, 5.1F, 0.5F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(0, 60).addBox(-2.9F, 6.2F, -2.9F, 5.8F, 0.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(148, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.8F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(46, 67).addBox(-2.5F, 4F, -3F, 5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(118, 60).addBox(-2.1F, 3.95F, -3.4F, 4.2F, 3.1F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(114, 67).addBox(-2.55F, 5.2F, -3.5F, 5.1F, 0.5F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(24, 60).addBox(-2.9F, 6.2F, -2.9F, 5.8F, 0.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(132, 60).addBox(-3.4F, 0.6F, -1.4F, 0.7F, 2.8F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(140, 60).addBox(2.7F, 0.6F, -1.4F, 0.7F, 2.8F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(48, 33).addBox(-2.8F, 7.6F, -2.8F, 5.6F, 4.4F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(94, 20).addBox(-2.2F, 7.55F, -3.4F, 4.4F, 4.5F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(148, 60).addBox(-2.2F, 7.18F, -2.8F, 4.4F, 0.52F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(48, 60).addBox(-2.95F, 7F, -2.95F, 5.9F, 1F, 5.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(84, 52).addBox(-2.85F, 9.4F, -3.45F, 5.7F, 0.6F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(24, 43).addBox(-3F, 11.8F, -4F, 6F, 1.4F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(70, 33).addBox(-2.8F, 7.6F, -2.8F, 5.6F, 4.4F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(116, 20).addBox(-2.2F, 7.55F, -3.4F, 4.4F, 4.5F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(168, 60).addBox(-2.2F, 7.18F, -2.8F, 4.4F, 0.52F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(72, 60).addBox(-2.95F, 7F, -2.95F, 5.9F, 1F, 5.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(110, 52).addBox(-2.85F, 9.4F, -3.45F, 5.7F, 0.6F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(50, 43).addBox(-3F, 11.8F, -4F, 6F, 1.4F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_thermal_suit(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(38, 0).addBox(-5F, -10F, -4.2F, 10F, 9.6F, 8.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.2F, -10.05F, -5F, 8.4F, 9.7F, 10F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(138, 20).addBox(-4.2F, -10.56F, -4.2F, 8.4F, 0.66F, 8.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(92, 33).addBox(-3.6F, -11.2F, -3.6F, 7.2F, 0.6F, 7.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(164, 52).addBox(-4.4F, -8.6F, -5.5F, 8.8F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(152, 67).addBox(-4.8F, -9.1F, -5.7F, 9.6F, 0.6F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(174, 67).addBox(-4.8F, -2.7F, -5.7F, 9.6F, 0.7F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(100, 43).addBox(-5.2F, -8F, -3F, 0.5F, 4.6F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(108, 43).addBox(4.7F, -8F, -3F, 0.5F, 4.6F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(212, 60).addBox(4.9F, -7.6F, -1.4F, 1F, 2F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(220, 60).addBox(-5.9F, -7.6F, -1.4F, 1F, 2F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(80, 67).addBox(5F, -7.3F, -1.9F, 0.8F, 1.4F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(84, 67).addBox(-5.8F, -7.3F, -1.9F, 0.8F, 1.4F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(228, 60).addBox(3F, -13.6F, 2.2F, 0.4F, 2.8F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(196, 67).addBox(2.9F, -14F, 2.1F, 0.6F, 0.6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(164, 0).addBox(-5.6F, -1F, -5.6F, 11.2F, 1.6F, 11.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(0, 20).addBox(-5.65F, -0.5F, -5.65F, 11.3F, 0.5F, 11.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(76, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.9F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(124, 33).addBox(-4.9F, -0.6F, -3.4F, 9.8F, 7.2F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(174, 20).addBox(-4.4F, -0.65F, -3.9F, 8.8F, 7.3F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(88, 67).addBox(-4.95F, 3.6F, -4F, 9.9F, 0.9F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(232, 60).addBox(-2.6F, 7.2F, -4.4F, 5.2F, 2.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(112, 67).addBox(-2F, 7.6F, -4.7F, 2.2F, 1.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(200, 67).addBox(0.8F, 7.7F, -4.7F, 0.6F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(204, 67).addBox(1.6F, 7.7F, -4.7F, 0.6F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(208, 67).addBox(0.8F, 8.6F, -4.7F, 1.4F, 0.5F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(48, 20).addBox(-4.6F, 0.6F, 3.4F, 9.2F, 9.8F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(212, 0).addBox(-4F, 0.55F, 2.8F, 8F, 9.9F, 3.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(0, 67).addBox(-4F, 0.18F, 3.4F, 8F, 0.52F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(22, 67).addBox(-3.2F, 6.6F, 6.3F, 6.4F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(214, 67).addBox(-3.2F, 2F, 6.3F, 6.4F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(38, 67).addBox(-3.2F, 3.2F, 6.3F, 2.4F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(74, 20).addBox(4.6F, 1.2F, 3.4F, 1.2F, 8.4F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(84, 20).addBox(-5.8F, 1.2F, 3.4F, 1.2F, 8.4F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(46, 67).addBox(-1F, 10.4F, 3.4F, 2F, 0.8F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(100, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(146, 33).addBox(-4.1F, -3.4F, -2.6F, 6.2F, 2.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(196, 20).addBox(-3.6F, -3.45F, -3.1F, 5.2F, 2.7F, 6.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(184, 52).addBox(-3.6F, -3.75F, -2.6F, 5.2F, 0.45F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(116, 43).addBox(-4F, -0.9F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(140, 43).addBox(-4.05F, -0.5F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(208, 52).addBox(-3.9F, 3.6F, -2.9F, 5.8F, 1F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c25", CubeListBuilder.create().texOffs(168, 43).addBox(-4F, 7F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c26", CubeListBuilder.create().texOffs(192, 43).addBox(-4.05F, 7.4F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c27", CubeListBuilder.create().texOffs(172, 33).addBox(-3.9F, 8.2F, -2.4F, 5.8F, 3.9F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c28", CubeListBuilder.create().texOffs(222, 20).addBox(-3.4F, 8.15F, -2.9F, 4.8F, 4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c29", CubeListBuilder.create().texOffs(116, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c30", CubeListBuilder.create().texOffs(194, 33).addBox(-2.1F, -3.4F, -2.6F, 6.2F, 2.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c31", CubeListBuilder.create().texOffs(0, 33).addBox(-1.6F, -3.45F, -3.1F, 5.2F, 2.7F, 6.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c32", CubeListBuilder.create().texOffs(232, 52).addBox(-1.6F, -3.75F, -2.6F, 5.2F, 0.45F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c33", CubeListBuilder.create().texOffs(220, 43).addBox(-2F, -0.9F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c34", CubeListBuilder.create().texOffs(0, 52).addBox(-2.05F, -0.5F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c35", CubeListBuilder.create().texOffs(0, 60).addBox(-1.9F, 3.6F, -2.9F, 5.8F, 1F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c36", CubeListBuilder.create().texOffs(28, 52).addBox(-2F, 7F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c37", CubeListBuilder.create().texOffs(52, 52).addBox(-2.05F, 7.4F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c38", CubeListBuilder.create().texOffs(220, 33).addBox(-1.9F, 8.2F, -2.4F, 5.8F, 3.9F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c39", CubeListBuilder.create().texOffs(26, 33).addBox(-1.4F, 8.15F, -2.9F, 4.8F, 4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c40", CubeListBuilder.create().texOffs(120, 60).addBox(3.7F, 0.8F, -1.3F, 0.3F, 2.4F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c41", CubeListBuilder.create().texOffs(242, 33).addBox(6F, 1.6F, 3.6F, 1.4F, 7.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c42", CubeListBuilder.create().texOffs(248, 33).addBox(-7.4F, 1.6F, 3.6F, 1.4F, 7.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c43", CubeListBuilder.create().texOffs(0, 43).addBox(6F, 1.6F, 4.6F, 1.4F, 7.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c44", CubeListBuilder.create().texOffs(6, 43).addBox(-7.4F, 1.6F, 4.6F, 1.4F, 7.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c45", CubeListBuilder.create().texOffs(12, 43).addBox(6F, 1.6F, 5.6F, 1.4F, 7.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c46", CubeListBuilder.create().texOffs(18, 43).addBox(-7.4F, 1.6F, 5.6F, 1.4F, 7.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(24, 43).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.8F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(80, 52).addBox(-4.9F, 9.4F, -2.9F, 9.8F, 1.2F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(132, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.8F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(56, 67).addBox(-2.5F, 4F, -3F, 5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(128, 60).addBox(-2.1F, 3.95F, -3.4F, 4.2F, 3.1F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(120, 67).addBox(-2.55F, 5.2F, -3.5F, 5.1F, 0.5F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(24, 60).addBox(-2.9F, 6.2F, -2.9F, 5.8F, 0.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(148, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.8F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(68, 67).addBox(-2.5F, 4F, -3F, 5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(142, 60).addBox(-2.1F, 3.95F, -3.4F, 4.2F, 3.1F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(136, 67).addBox(-2.55F, 5.2F, -3.5F, 5.1F, 0.5F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(48, 60).addBox(-2.9F, 6.2F, -2.9F, 5.8F, 0.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(156, 60).addBox(-3.4F, 0.6F, -1.4F, 0.7F, 2.8F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(164, 60).addBox(2.7F, 0.6F, -1.4F, 0.7F, 2.8F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(48, 33).addBox(-2.8F, 7.6F, -2.8F, 5.6F, 4.4F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(94, 20).addBox(-2.2F, 7.55F, -3.4F, 4.4F, 4.5F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(172, 60).addBox(-2.2F, 7.18F, -2.8F, 4.4F, 0.52F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(72, 60).addBox(-2.95F, 7F, -2.95F, 5.9F, 1F, 5.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(112, 52).addBox(-2.85F, 9.4F, -3.45F, 5.7F, 0.6F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(48, 43).addBox(-3F, 11.8F, -4F, 6F, 1.4F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(70, 33).addBox(-2.8F, 7.6F, -2.8F, 5.6F, 4.4F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(116, 20).addBox(-2.2F, 7.55F, -3.4F, 4.4F, 4.5F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(192, 60).addBox(-2.2F, 7.18F, -2.8F, 4.4F, 0.52F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(96, 60).addBox(-2.95F, 7F, -2.95F, 5.9F, 1F, 5.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(138, 52).addBox(-2.85F, 9.4F, -3.45F, 5.7F, 0.6F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(74, 43).addBox(-3F, 11.8F, -4F, 6F, 1.4F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_acid_suit(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(38, 0).addBox(-5F, -10F, -4.2F, 10F, 9.6F, 8.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.2F, -10.05F, -5F, 8.4F, 9.7F, 10F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(138, 20).addBox(-4.2F, -10.56F, -4.2F, 8.4F, 0.66F, 8.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(92, 33).addBox(-3.6F, -11.2F, -3.6F, 7.2F, 0.6F, 7.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(136, 52).addBox(-4.4F, -8.6F, -5.5F, 8.8F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(138, 67).addBox(-4.8F, -9.1F, -5.7F, 9.6F, 0.6F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(160, 67).addBox(-4.8F, -2.7F, -5.7F, 9.6F, 0.7F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(76, 43).addBox(-5.2F, -8F, -3F, 0.5F, 4.6F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(84, 43).addBox(4.7F, -8F, -3F, 0.5F, 4.6F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(188, 60).addBox(4.9F, -7.6F, -1.4F, 1F, 2F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(196, 60).addBox(-5.9F, -7.6F, -1.4F, 1F, 2F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(58, 67).addBox(5F, -7.3F, -1.9F, 0.8F, 1.4F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(62, 67).addBox(-5.8F, -7.3F, -1.9F, 0.8F, 1.4F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(204, 60).addBox(3F, -13.6F, 2.2F, 0.4F, 2.8F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(182, 67).addBox(2.9F, -14F, 2.1F, 0.6F, 0.6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(164, 0).addBox(-5.6F, -1F, -5.6F, 11.2F, 1.6F, 11.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(0, 20).addBox(-5.65F, -0.5F, -5.65F, 11.3F, 0.5F, 11.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(208, 60).addBox(-1.6F, -2.2F, -6.6F, 3.2F, 2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(66, 67).addBox(-1.2F, -1.9F, -7F, 2.4F, 1.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(76, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.9F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(124, 33).addBox(-4.9F, -0.6F, -3.4F, 9.8F, 7.2F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(174, 20).addBox(-4.4F, -0.65F, -3.9F, 8.8F, 7.3F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(74, 67).addBox(-4.95F, 3.6F, -4F, 9.9F, 0.9F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(220, 60).addBox(-2.6F, 7.2F, -4.4F, 5.2F, 2.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(98, 67).addBox(-2F, 7.6F, -4.7F, 2.2F, 1.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(186, 67).addBox(0.8F, 7.7F, -4.7F, 0.6F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(190, 67).addBox(1.6F, 7.7F, -4.7F, 0.6F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(194, 67).addBox(0.8F, 8.6F, -4.7F, 1.4F, 0.5F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(48, 20).addBox(-4.6F, 0.6F, 3.4F, 9.2F, 9.8F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(212, 0).addBox(-4F, 0.55F, 2.8F, 8F, 9.9F, 3.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(234, 60).addBox(-4F, 0.18F, 3.4F, 8F, 0.52F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(0, 67).addBox(-3.2F, 6.6F, 6.3F, 6.4F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(200, 67).addBox(-3.2F, 2F, 6.3F, 6.4F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(16, 67).addBox(-3.2F, 3.2F, 6.3F, 2.4F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(74, 20).addBox(4.6F, 1.2F, 3.4F, 1.2F, 8.4F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(84, 20).addBox(-5.8F, 1.2F, 3.4F, 1.2F, 8.4F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(24, 67).addBox(-1F, 10.4F, 3.4F, 2F, 0.8F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(100, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(146, 33).addBox(-4.1F, -3.4F, -2.6F, 6.2F, 2.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(196, 20).addBox(-3.6F, -3.45F, -3.1F, 5.2F, 2.7F, 6.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(156, 52).addBox(-3.6F, -3.75F, -2.6F, 5.2F, 0.45F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(92, 43).addBox(-4F, -0.9F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(116, 43).addBox(-4.05F, -0.5F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(180, 52).addBox(-3.9F, 3.6F, -2.9F, 5.8F, 1F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c25", CubeListBuilder.create().texOffs(144, 43).addBox(-4F, 7F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c26", CubeListBuilder.create().texOffs(168, 43).addBox(-4.05F, 7.4F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c27", CubeListBuilder.create().texOffs(172, 33).addBox(-3.9F, 8.2F, -2.4F, 5.8F, 3.9F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c28", CubeListBuilder.create().texOffs(222, 20).addBox(-3.4F, 8.15F, -2.9F, 4.8F, 4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c29", CubeListBuilder.create().texOffs(116, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c30", CubeListBuilder.create().texOffs(194, 33).addBox(-2.1F, -3.4F, -2.6F, 6.2F, 2.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c31", CubeListBuilder.create().texOffs(0, 33).addBox(-1.6F, -3.45F, -3.1F, 5.2F, 2.7F, 6.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c32", CubeListBuilder.create().texOffs(204, 52).addBox(-1.6F, -3.75F, -2.6F, 5.2F, 0.45F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c33", CubeListBuilder.create().texOffs(196, 43).addBox(-2F, -0.9F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c34", CubeListBuilder.create().texOffs(220, 43).addBox(-2.05F, -0.5F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c35", CubeListBuilder.create().texOffs(228, 52).addBox(-1.9F, 3.6F, -2.9F, 5.8F, 1F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c36", CubeListBuilder.create().texOffs(0, 52).addBox(-2F, 7F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c37", CubeListBuilder.create().texOffs(24, 52).addBox(-2.05F, 7.4F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c38", CubeListBuilder.create().texOffs(220, 33).addBox(-1.9F, 8.2F, -2.4F, 5.8F, 3.9F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c39", CubeListBuilder.create().texOffs(26, 33).addBox(-1.4F, 8.15F, -2.9F, 4.8F, 4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c40", CubeListBuilder.create().texOffs(96, 60).addBox(3.7F, 0.8F, -1.3F, 0.3F, 2.4F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 43).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.8F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(52, 52).addBox(-4.9F, 9.4F, -2.9F, 9.8F, 1.2F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(132, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.8F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(34, 67).addBox(-2.5F, 4F, -3F, 5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(104, 60).addBox(-2.1F, 3.95F, -3.4F, 4.2F, 3.1F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(106, 67).addBox(-2.55F, 5.2F, -3.5F, 5.1F, 0.5F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(0, 60).addBox(-2.9F, 6.2F, -2.9F, 5.8F, 0.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(148, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.8F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(46, 67).addBox(-2.5F, 4F, -3F, 5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(118, 60).addBox(-2.1F, 3.95F, -3.4F, 4.2F, 3.1F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(122, 67).addBox(-2.55F, 5.2F, -3.5F, 5.1F, 0.5F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(24, 60).addBox(-2.9F, 6.2F, -2.9F, 5.8F, 0.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(132, 60).addBox(-3.4F, 0.6F, -1.4F, 0.7F, 2.8F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(140, 60).addBox(2.7F, 0.6F, -1.4F, 0.7F, 2.8F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(48, 33).addBox(-2.8F, 7.6F, -2.8F, 5.6F, 4.4F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(94, 20).addBox(-2.2F, 7.55F, -3.4F, 4.4F, 4.5F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(148, 60).addBox(-2.2F, 7.18F, -2.8F, 4.4F, 0.52F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(48, 60).addBox(-2.95F, 7F, -2.95F, 5.9F, 1F, 5.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(84, 52).addBox(-2.85F, 9.4F, -3.45F, 5.7F, 0.6F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(24, 43).addBox(-3F, 11.8F, -4F, 6F, 1.4F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(70, 33).addBox(-2.8F, 7.6F, -2.8F, 5.6F, 4.4F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(116, 20).addBox(-2.2F, 7.55F, -3.4F, 4.4F, 4.5F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(168, 60).addBox(-2.2F, 7.18F, -2.8F, 4.4F, 0.52F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(72, 60).addBox(-2.95F, 7F, -2.95F, 5.9F, 1F, 5.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(110, 52).addBox(-2.85F, 9.4F, -3.45F, 5.7F, 0.6F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(50, 43).addBox(-3F, 11.8F, -4F, 6F, 1.4F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_atmospheric_suit(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(38, 0).addBox(-5F, -10F, -4.2F, 10F, 9.6F, 8.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.2F, -10.05F, -5F, 8.4F, 9.7F, 10F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(164, 20).addBox(-4.2F, -10.56F, -4.2F, 8.4F, 0.66F, 8.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(114, 33).addBox(-3.6F, -11.2F, -3.6F, 7.2F, 0.6F, 7.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(164, 52).addBox(-4.4F, -8.6F, -5.5F, 8.8F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(182, 67).addBox(-4.8F, -9.1F, -5.7F, 9.6F, 0.6F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(204, 67).addBox(-4.8F, -2.7F, -5.7F, 9.6F, 0.7F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(98, 43).addBox(-5.2F, -8F, -3F, 0.5F, 4.6F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(106, 43).addBox(4.7F, -8F, -3F, 0.5F, 4.6F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(212, 60).addBox(4.9F, -7.6F, -1.4F, 1F, 2F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(220, 60).addBox(-5.9F, -7.6F, -1.4F, 1F, 2F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(94, 67).addBox(5F, -7.3F, -1.9F, 0.8F, 1.4F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(98, 67).addBox(-5.8F, -7.3F, -1.9F, 0.8F, 1.4F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(252, 60).addBox(3F, -13.6F, 2.2F, 0.4F, 2.8F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(226, 67).addBox(2.9F, -14F, 2.1F, 0.6F, 0.6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(164, 0).addBox(-5.6F, -1F, -5.6F, 11.2F, 1.6F, 11.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(0, 20).addBox(-5.65F, -0.5F, -5.65F, 11.3F, 0.5F, 11.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(48, 20).addBox(-0.6F, -12F, -5.2F, 1.2F, 1.2F, 10.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(76, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.9F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(146, 33).addBox(-4.9F, -0.6F, -3.4F, 9.8F, 7.2F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(200, 20).addBox(-4.4F, -0.65F, -3.9F, 8.8F, 7.3F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(102, 67).addBox(-4.95F, 3.6F, -4F, 9.9F, 0.9F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 67).addBox(-2.6F, 7.2F, -4.4F, 5.2F, 2.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(126, 67).addBox(-2F, 7.6F, -4.7F, 2.2F, 1.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(230, 67).addBox(0.8F, 7.7F, -4.7F, 0.6F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(234, 67).addBox(1.6F, 7.7F, -4.7F, 0.6F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(238, 67).addBox(0.8F, 8.6F, -4.7F, 1.4F, 0.5F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(74, 20).addBox(-4.6F, 0.6F, 3.4F, 9.2F, 9.8F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(212, 0).addBox(-4F, 0.55F, 2.8F, 8F, 9.9F, 3.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(14, 67).addBox(-4F, 0.18F, 3.4F, 8F, 0.52F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(36, 67).addBox(-3.2F, 6.6F, 6.3F, 6.4F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(0, 71).addBox(-3.2F, 2F, 6.3F, 6.4F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(52, 67).addBox(-3.2F, 3.2F, 6.3F, 2.4F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(100, 20).addBox(4.6F, 1.2F, 3.4F, 1.2F, 8.4F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(110, 20).addBox(-5.8F, 1.2F, 3.4F, 1.2F, 8.4F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(60, 67).addBox(-1F, 10.4F, 3.4F, 2F, 0.8F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(100, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(168, 33).addBox(-4.1F, -3.4F, -2.6F, 6.2F, 2.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(222, 20).addBox(-3.6F, -3.45F, -3.1F, 5.2F, 2.7F, 6.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(184, 52).addBox(-3.6F, -3.75F, -2.6F, 5.2F, 0.45F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(114, 43).addBox(-4F, -0.9F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(138, 43).addBox(-4.05F, -0.5F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(208, 52).addBox(-3.9F, 3.6F, -2.9F, 5.8F, 1F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c25", CubeListBuilder.create().texOffs(166, 43).addBox(-4F, 7F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c26", CubeListBuilder.create().texOffs(190, 43).addBox(-4.05F, 7.4F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c27", CubeListBuilder.create().texOffs(194, 33).addBox(-3.9F, 8.2F, -2.4F, 5.8F, 3.9F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c28", CubeListBuilder.create().texOffs(0, 33).addBox(-3.4F, 8.15F, -2.9F, 4.8F, 4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c29", CubeListBuilder.create().texOffs(116, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c30", CubeListBuilder.create().texOffs(216, 33).addBox(-2.1F, -3.4F, -2.6F, 6.2F, 2.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c31", CubeListBuilder.create().texOffs(22, 33).addBox(-1.6F, -3.45F, -3.1F, 5.2F, 2.7F, 6.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c32", CubeListBuilder.create().texOffs(232, 52).addBox(-1.6F, -3.75F, -2.6F, 5.2F, 0.45F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c33", CubeListBuilder.create().texOffs(218, 43).addBox(-2F, -0.9F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c34", CubeListBuilder.create().texOffs(0, 52).addBox(-2.05F, -0.5F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c35", CubeListBuilder.create().texOffs(0, 60).addBox(-1.9F, 3.6F, -2.9F, 5.8F, 1F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c36", CubeListBuilder.create().texOffs(28, 52).addBox(-2F, 7F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c37", CubeListBuilder.create().texOffs(52, 52).addBox(-2.05F, 7.4F, -3.05F, 6.1F, 0.4F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c38", CubeListBuilder.create().texOffs(0, 43).addBox(-1.9F, 8.2F, -2.4F, 5.8F, 3.9F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c39", CubeListBuilder.create().texOffs(48, 33).addBox(-1.4F, 8.15F, -2.9F, 4.8F, 4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c40", CubeListBuilder.create().texOffs(120, 60).addBox(3.7F, 0.8F, -1.3F, 0.3F, 2.4F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c41", CubeListBuilder.create().texOffs(228, 60).addBox(-3.8F, 10.4F, 3.4F, 2.2F, 1.6F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c42", CubeListBuilder.create().texOffs(240, 60).addBox(1.6F, 10.4F, 3.4F, 2.2F, 1.6F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c43", CubeListBuilder.create().texOffs(134, 67).addBox(-3.5F, 12F, 3.7F, 1.6F, 0.4F, 1.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c44", CubeListBuilder.create().texOffs(142, 67).addBox(1.9F, 12F, 3.7F, 1.6F, 0.4F, 1.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(22, 43).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.8F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(80, 52).addBox(-4.9F, 9.4F, -2.9F, 9.8F, 1.2F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(132, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.8F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(70, 67).addBox(-2.5F, 4F, -3F, 5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(128, 60).addBox(-2.1F, 3.95F, -3.4F, 4.2F, 3.1F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(150, 67).addBox(-2.55F, 5.2F, -3.5F, 5.1F, 0.5F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(24, 60).addBox(-2.9F, 6.2F, -2.9F, 5.8F, 0.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(148, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.8F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(82, 67).addBox(-2.5F, 4F, -3F, 5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(142, 60).addBox(-2.1F, 3.95F, -3.4F, 4.2F, 3.1F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(166, 67).addBox(-2.55F, 5.2F, -3.5F, 5.1F, 0.5F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(48, 60).addBox(-2.9F, 6.2F, -2.9F, 5.8F, 0.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(156, 60).addBox(-3.4F, 0.6F, -1.4F, 0.7F, 2.8F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(164, 60).addBox(2.7F, 0.6F, -1.4F, 0.7F, 2.8F, 2.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(70, 33).addBox(-2.8F, 7.6F, -2.8F, 5.6F, 4.4F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(120, 20).addBox(-2.2F, 7.55F, -3.4F, 4.4F, 4.5F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(172, 60).addBox(-2.2F, 7.18F, -2.8F, 4.4F, 0.52F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(72, 60).addBox(-2.95F, 7F, -2.95F, 5.9F, 1F, 5.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(112, 52).addBox(-2.85F, 9.4F, -3.45F, 5.7F, 0.6F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(46, 43).addBox(-3F, 11.8F, -4F, 6F, 1.4F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(92, 33).addBox(-2.8F, 7.6F, -2.8F, 5.6F, 4.4F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(142, 20).addBox(-2.2F, 7.55F, -3.4F, 4.4F, 4.5F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(192, 60).addBox(-2.2F, 7.18F, -2.8F, 4.4F, 0.52F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(96, 60).addBox(-2.95F, 7F, -2.95F, 5.9F, 1F, 5.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(138, 52).addBox(-2.85F, 9.4F, -3.45F, 5.7F, 0.6F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(72, 43).addBox(-3F, 11.8F, -4F, 6F, 1.4F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_hazmat(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(38, 0).addBox(-4.8F, -9F, -4.1F, 9.6F, 9.4F, 8.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.1F, -9.05F, -4.8F, 8.2F, 9.5F, 9.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(100, 20).addBox(-4.1F, -9.49F, -4.1F, 8.2F, 0.59F, 8.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(108, 32).addBox(-4.2F, -7.8F, -5.2F, 8.4F, 6.4F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(84, 41).addBox(-3.6F, -7.3F, -5.6F, 7.2F, 3.6F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(102, 41).addBox(-1.5F, -3.6F, -6.3F, 3F, 2.6F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(24, 41).addBox(1.4F, -3.8F, -7.4F, 2.8F, 2.8F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(36, 41).addBox(-4.2F, -3.8F, -7.4F, 2.8F, 2.8F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(112, 41).addBox(1.7F, -3.5F, -7.8F, 2.2F, 2.2F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(120, 41).addBox(-3.9F, -3.5F, -7.8F, 2.2F, 2.2F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(180, 0).addBox(-5.2F, -1F, -5.2F, 10.4F, 1.2F, 10.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(76, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.9F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(224, 0).addBox(-1F, -0.4F, -3.3F, 2F, 11.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(128, 41).addBox(1.2F, 1.4F, -3.4F, 2.4F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(136, 41).addBox(-3.6F, 1.4F, -3.5F, 1.6F, 2.4F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(204, 41).addBox(-3.3F, 1.8F, -3.8F, 1F, 0.8F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(136, 20).addBox(-3.3F, -0.6F, -3.6F, 1.1F, 8.8F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(142, 20).addBox(2.2F, -0.6F, -3.6F, 1.1F, 8.8F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(0, 32).addBox(-4.5F, 8.2F, -3.5F, 9F, 1.2F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(40, 20).addBox(-3.4F, 0.4F, 2.3F, 6.8F, 9.6F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(230, 0).addBox(-2.4F, 0.6F, 3.6F, 4.8F, 10F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(164, 0).addBox(-1.8F, 0.55F, 3F, 3.6F, 10.1F, 3.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(142, 41).addBox(-1.8F, 0.18F, 3.6F, 3.6F, 0.52F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(156, 41).addBox(-2.5F, 2F, 3.6F, 5F, 1F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(172, 41).addBox(-0.6F, -0.8F, 4.2F, 1.2F, 1.2F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(208, 41).addBox(0.6F, -0.6F, 4.4F, 1.6F, 0.6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(100, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(128, 32).addBox(-4F, 6.8F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(152, 32).addBox(-3.9F, -2.9F, -2.9F, 5.8F, 1.6F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(148, 20).addBox(-3.9F, 7.8F, -2.4F, 5.8F, 4.3F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(56, 20).addBox(-3.4F, 7.75F, -2.9F, 4.8F, 4.4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(116, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(176, 32).addBox(-2F, 6.8F, -3F, 6F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(200, 32).addBox(-1.9F, -2.9F, -2.9F, 5.8F, 1.6F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(170, 20).addBox(-1.9F, 7.8F, -2.4F, 5.8F, 4.3F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(78, 20).addBox(-1.4F, 7.75F, -2.9F, 4.8F, 4.4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(32, 32).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.75F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(132, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.75F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(180, 41).addBox(-2.4F, 4.4F, -3F, 4.8F, 2.6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(148, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.75F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(192, 41).addBox(-2.4F, 4.4F, -3F, 4.8F, 2.6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(192, 20).addBox(-2.6F, 6.8F, -2.6F, 5.2F, 5F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 20).addBox(-2F, 6.75F, -3.2F, 4F, 5.1F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(48, 41).addBox(-2F, 6.38F, -2.6F, 4F, 0.52F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(224, 32).addBox(-2.75F, 6.4F, -2.75F, 5.5F, 1F, 5.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(56, 32).addBox(-2.8F, 11.6F, -3.8F, 5.6F, 1.4F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(214, 20).addBox(-2.6F, 6.8F, -2.6F, 5.2F, 5F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(20, 20).addBox(-2F, 6.75F, -3.2F, 4F, 5.1F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(66, 41).addBox(-2F, 6.38F, -2.6F, 4F, 0.52F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(0, 41).addBox(-2.75F, 6.4F, -2.75F, 5.5F, 1F, 5.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(82, 32).addBox(-2.8F, 11.6F, -3.8F, 5.6F, 1.4F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_graviton(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(36, 0).addBox(-4.8F, -9F, -4F, 9.6F, 9.2F, 8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -9.05F, -4.8F, 8F, 9.3F, 9.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(22, 31).addBox(-4F, -9.56F, -4F, 8F, 0.66F, 8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(124, 49).addBox(-4.1F, -7.6F, -5.3F, 8.2F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(68, 64).addBox(-2.6F, -1.9F, -5.7F, 5.2F, 1.5F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(0, 41).addBox(4.5F, -6.6F, -2F, 1.1F, 3.8F, 3.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(12, 41).addBox(-5.6F, -6.6F, -2F, 1.1F, 3.8F, 3.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(176, 57).addBox(5.4F, -5.4F, -0.8F, 0.4F, 1.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(182, 57).addBox(-5.8F, -5.4F, -0.8F, 0.4F, 1.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(160, 0).addBox(-5.2F, -0.9F, -5.2F, 10.4F, 1.2F, 10.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(140, 20).addBox(-0.5F, -9.9F, -3.6F, 1F, 1F, 8.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(226, 64).addBox(-4.6F, -8.3F, -5.6F, 9.2F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(144, 49).addBox(-5F, -5.4F, -5.2F, 1.2F, 3.8F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(154, 49).addBox(3.8F, -5.4F, -5.2F, 1.2F, 3.8F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(82, 64).addBox(-3.5F, -6.6F, -5.75F, 7F, 1.1F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(188, 57).addBox(-0.55F, -5.5F, -5.75F, 1.1F, 2.6F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(108, 57).addBox(2F, -10.6F, 1.2F, 0.8F, 1.6F, 3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(116, 57).addBox(-2.8F, -10.6F, 1.2F, 0.8F, 1.6F, 3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(72, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.9F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 41).addBox(-4.6F, -0.3F, -3.5F, 3.9F, 5.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(36, 41).addBox(0.7F, -0.3F, -3.5F, 3.9F, 5.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(194, 57).addBox(-1.3F, 1F, -3.9F, 2.6F, 2.6F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(98, 64).addBox(-0.8F, 1.5F, -4.3F, 1.6F, 1.6F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(104, 64).addBox(-3.3F, 5.5F, -3.2F, 6.6F, 1.15F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(120, 64).addBox(-3.1F, 6.95F, -3.2F, 6.2F, 1.15F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(136, 64).addBox(-2.9F, 8.4F, -3.2F, 5.8F, 1.15F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(48, 41).addBox(-4.7F, -1.1F, -3F, 9.4F, 1.3F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(80, 41).addBox(-4.6F, 9.9F, -2.9F, 9.2F, 1.3F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(248, 64).addBox(-1.3F, 10.25F, -3.15F, 1F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(252, 64).addBox(0.3F, 10.25F, -3.15F, 1F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(224, 0).addBox(-3.8F, 0.4F, 2.8F, 7.6F, 8.6F, 1.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(204, 0).addBox(-3.3F, 0.35F, 2.3F, 6.6F, 8.7F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(150, 64).addBox(-3.3F, 0.05F, 2.8F, 6.6F, 0.45F, 1.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(202, 57).addBox(-3.4F, 8.8F, 2.8F, 2.4F, 1.6F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(212, 57).addBox(1F, 8.8F, 2.8F, 2.4F, 1.6F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(168, 64).addBox(-3F, 10.4F, 3.1F, 1.6F, 0.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(176, 64).addBox(1.4F, 10.4F, 3.1F, 1.6F, 0.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(164, 49).addBox(-2.6F, 2F, 4.85F, 5.2F, 5.2F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(0, 68).addBox(3.8F, -2.4F, 5.4F, 1.6F, 0.6F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.5708F));
                p[1].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(6, 68).addBox(2.4527F, -5.6527F, 5.4F, 1.6F, 0.6F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 2.3562F));
                p[1].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(12, 68).addBox(-0.8F, -7F, 5.4F, 1.6F, 0.6F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 3.1416F));
                p[1].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(18, 68).addBox(-4.0527F, -5.6527F, 5.4F, 1.6F, 0.6F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 3.927F));
                p[1].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(24, 68).addBox(-5.4F, -2.4F, 5.4F, 1.6F, 0.6F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 4.7124F));
                p[1].addOrReplaceChild("c25", CubeListBuilder.create().texOffs(30, 68).addBox(-4.0527F, 0.8527F, 5.4F, 1.6F, 0.6F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 5.4978F));
                p[1].addOrReplaceChild("c26", CubeListBuilder.create().texOffs(36, 68).addBox(-0.8F, 2.2F, 5.4F, 1.6F, 0.6F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 6.2832F));
                p[1].addOrReplaceChild("c27", CubeListBuilder.create().texOffs(42, 68).addBox(2.4527F, 0.8527F, 5.4F, 1.6F, 0.6F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 7.0686F));
                p[1].addOrReplaceChild("c28", CubeListBuilder.create().texOffs(184, 64).addBox(-0.8F, 3.8F, 5.5F, 1.6F, 1.6F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c29", CubeListBuilder.create().texOffs(96, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c30", CubeListBuilder.create().texOffs(54, 31).addBox(-4.3F, -3.6F, -2.6F, 6.6F, 3F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c31", CubeListBuilder.create().texOffs(0, 20).addBox(-3.7F, -3.65F, -3.2F, 5.4F, 3.1F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c32", CubeListBuilder.create().texOffs(178, 49).addBox(-3.7F, -4.02F, -2.6F, 5.4F, 0.52F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c33", CubeListBuilder.create().texOffs(112, 41).addBox(-4.35F, -1F, -3.25F, 6.7F, 0.5F, 6.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c34", CubeListBuilder.create().texOffs(140, 41).addBox(-4F, -0.6F, -2.9F, 6F, 1.4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c35", CubeListBuilder.create().texOffs(80, 31).addBox(-3.7F, 0.6F, -2.2F, 0.5F, 3.2F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c36", CubeListBuilder.create().texOffs(202, 49).addBox(-3.35F, 3.4F, -2.35F, 4.7F, 1.2F, 4.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c37", CubeListBuilder.create().texOffs(160, 20).addBox(-3.8F, 5F, -2.3F, 5.6F, 4.6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c38", CubeListBuilder.create().texOffs(26, 20).addBox(-3.3F, 4.95F, -2.8F, 4.6F, 4.7F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c39", CubeListBuilder.create().texOffs(222, 57).addBox(-3.9F, 5.8F, -1.6F, 0.5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c40", CubeListBuilder.create().texOffs(164, 41).addBox(-3.45F, 9.6F, -2.45F, 4.9F, 2.6F, 4.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c41", CubeListBuilder.create().texOffs(184, 41).addBox(-4.1F, 7.4F, -3.1F, 6.2F, 0.7F, 6.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c42", CubeListBuilder.create().texOffs(112, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c43", CubeListBuilder.create().texOffs(92, 31).addBox(-2.3F, -3.6F, -2.6F, 6.6F, 3F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c44", CubeListBuilder.create().texOffs(48, 20).addBox(-1.7F, -3.65F, -3.2F, 5.4F, 3.1F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c45", CubeListBuilder.create().texOffs(222, 49).addBox(-1.7F, -4.02F, -2.6F, 5.4F, 0.52F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c46", CubeListBuilder.create().texOffs(212, 41).addBox(-2.35F, -1F, -3.25F, 6.7F, 0.5F, 6.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c47", CubeListBuilder.create().texOffs(0, 49).addBox(-2F, -0.6F, -2.9F, 6F, 1.4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c48", CubeListBuilder.create().texOffs(118, 31).addBox(3.2F, 0.6F, -2.2F, 0.5F, 3.2F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c49", CubeListBuilder.create().texOffs(0, 57).addBox(-1.35F, 3.4F, -2.35F, 4.7F, 1.2F, 4.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c50", CubeListBuilder.create().texOffs(182, 20).addBox(-1.8F, 5F, -2.3F, 5.6F, 4.6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c51", CubeListBuilder.create().texOffs(74, 20).addBox(-1.3F, 4.95F, -2.8F, 4.6F, 4.7F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c52", CubeListBuilder.create().texOffs(226, 57).addBox(3.4F, 5.8F, -1.6F, 0.5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c53", CubeListBuilder.create().texOffs(24, 49).addBox(-1.45F, 9.6F, -2.45F, 4.9F, 2.6F, 4.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c54", CubeListBuilder.create().texOffs(44, 49).addBox(-2.1F, 7.4F, -3.1F, 6.2F, 0.7F, 6.2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(130, 31).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.6F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(204, 20).addBox(-4.9F, 9.4F, -2.6F, 1F, 3.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(218, 20).addBox(3.9F, 9.4F, -2.6F, 1F, 3.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(230, 57).addBox(-1.6F, 9.8F, -2.95F, 3.2F, 2.6F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(128, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(124, 57).addBox(-2.4F, 0.4F, -3F, 4.8F, 3.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(240, 57).addBox(-2.5F, 4.1F, -3.1F, 5F, 2.6F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(136, 57).addBox(-2.1F, 4.05F, -3.5F, 4.2F, 2.7F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(48, 68).addBox(-0.6F, 4.9F, -3.75F, 1.2F, 1F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(190, 64).addBox(-2.3F, 6.8F, -3F, 4.6F, 1.2F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(154, 31).addBox(-2.9F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(164, 31).addBox(2.4F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(144, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(150, 57).addBox(-2.4F, 0.4F, -3F, 4.8F, 3.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(0, 64).addBox(-2.5F, 4.1F, -3.1F, 5F, 2.6F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(162, 57).addBox(-2.1F, 4.05F, -3.5F, 4.2F, 2.7F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(54, 68).addBox(-0.6F, 4.9F, -3.75F, 1.2F, 1F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(202, 64).addBox(-2.3F, 6.8F, -3F, 4.6F, 1.2F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(174, 31).addBox(-2.9F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(184, 31).addBox(2.4F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(232, 20).addBox(-2.7F, 7.6F, -2.8F, 5.4F, 4.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(96, 20).addBox(-2.1F, 7.55F, -3.4F, 4.2F, 4.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(68, 57).addBox(-2.1F, 7.18F, -2.8F, 4.2F, 0.52F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(12, 64).addBox(-2.6F, 10.1F, -4.1F, 5.2F, 1.8F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(194, 31).addBox(-2.9F, 11.9F, -3.9F, 5.8F, 1.2F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(72, 49).addBox(-2.95F, 12.25F, -3.95F, 5.9F, 0.4F, 6.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(28, 64).addBox(-3.2F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(34, 64).addBox(2.6F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(214, 64).addBox(-1F, 9.8F, 2.3F, 2F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(60, 68).addBox(-0.6F, 11.4F, 2.5F, 1.2F, 0.3F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(20, 57).addBox(-3F, 8F, -3F, 6F, 0.6F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(0, 31).addBox(-2.7F, 7.6F, -2.8F, 5.4F, 4.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(118, 20).addBox(-2.1F, 7.55F, -3.4F, 4.2F, 4.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(88, 57).addBox(-2.1F, 7.18F, -2.8F, 4.2F, 0.52F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(40, 64).addBox(-2.6F, 10.1F, -4.1F, 5.2F, 1.8F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(220, 31).addBox(-2.9F, 11.9F, -3.9F, 5.8F, 1.2F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(98, 49).addBox(-2.95F, 12.25F, -3.95F, 5.9F, 0.4F, 6.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(56, 64).addBox(-3.2F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(62, 64).addBox(2.6F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(220, 64).addBox(-1F, 9.8F, 2.3F, 2F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(66, 68).addBox(-0.6F, 11.4F, 2.5F, 1.2F, 0.3F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(44, 57).addBox(-3F, 8F, -3F, 6F, 0.6F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_stellaris(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(36, 0).addBox(-4.8F, -9F, -4F, 9.6F, 9.2F, 8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -9.05F, -4.8F, 8F, 9.3F, 9.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(44, 31).addBox(-4F, -9.56F, -4F, 8F, 0.66F, 8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(176, 50).addBox(-4.1F, -7.6F, -5.3F, 8.2F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(28, 71).addBox(-2.6F, -1.9F, -5.7F, 5.2F, 1.5F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(52, 41).addBox(4.5F, -6.6F, -2F, 1.1F, 3.8F, 3.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(64, 41).addBox(-5.6F, -6.6F, -2F, 1.1F, 3.8F, 3.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(102, 65).addBox(5.4F, -5.4F, -0.8F, 0.4F, 1.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(108, 65).addBox(-5.8F, -5.4F, -0.8F, 0.4F, 1.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(160, 0).addBox(-5.2F, -0.9F, -5.2F, 10.4F, 1.2F, 10.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(140, 20).addBox(-0.5F, -9.9F, -3.6F, 1F, 1F, 8.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(164, 71).addBox(-4.6F, -8.3F, -5.6F, 9.2F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(196, 50).addBox(-5F, -5.4F, -5.2F, 1.2F, 3.8F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(206, 50).addBox(3.8F, -5.4F, -5.2F, 1.2F, 3.8F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(114, 65).addBox(-3.7F, -7.2F, -5.75F, 7.4F, 2.8F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(186, 71).addBox(-3.7F, -4.3F, -5.8F, 7.4F, 0.5F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(76, 31).addBox(4.1F, -7.4F, 5F, 1F, 7.4F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -1.5708F));
                p[0].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(204, 71).addBox(3.85F, -7.4F, 4.9F, 1.5F, 1F, 0.7F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -1.5708F));
                p[0].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(80, 31).addBox(3.3337F, -10.3F, 5F, 1.3F, 8F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -1.0472F));
                p[0].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(210, 71).addBox(3.2337F, -10.3F, 4.9F, 1.5F, 1F, 0.7F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -1.0472F));
                p[0].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(160, 20).addBox(1.8F, -12.7837F, 5F, 1F, 8.8F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.5236F));
                p[0].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(216, 71).addBox(1.55F, -12.7837F, 4.9F, 1.5F, 1F, 0.7F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.5236F));
                p[0].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(224, 0).addBox(-0.65F, -14.2F, 5F, 1.3F, 9.6F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0F));
                p[0].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(222, 71).addBox(-0.75F, -14.2F, 4.9F, 1.5F, 1F, 0.7F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0F));
                p[0].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(164, 20).addBox(-2.8F, -12.7837F, 5F, 1F, 8.8F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.5236F));
                p[0].addOrReplaceChild("c25", CubeListBuilder.create().texOffs(228, 71).addBox(-3.05F, -12.7837F, 4.9F, 1.5F, 1F, 0.7F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.5236F));
                p[0].addOrReplaceChild("c26", CubeListBuilder.create().texOffs(86, 31).addBox(-4.6337F, -10.3F, 5F, 1.3F, 8F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.0472F));
                p[0].addOrReplaceChild("c27", CubeListBuilder.create().texOffs(234, 71).addBox(-4.7337F, -10.3F, 4.9F, 1.5F, 1F, 0.7F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.0472F));
                p[0].addOrReplaceChild("c28", CubeListBuilder.create().texOffs(92, 31).addBox(-5.1F, -7.4F, 5F, 1F, 7.4F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.5708F));
                p[0].addOrReplaceChild("c29", CubeListBuilder.create().texOffs(240, 71).addBox(-5.35F, -7.4F, 4.9F, 1.5F, 1F, 0.7F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.5708F));
                p[0].addOrReplaceChild("c30", CubeListBuilder.create().texOffs(40, 65).addBox(-1.6F, -6.2F, 5F, 3.2F, 3.2F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c31", CubeListBuilder.create().texOffs(132, 65).addBox(-1F, -10.4F, -3.1F, 2F, 1.2F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(72, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.9F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(76, 41).addBox(-4.6F, -0.3F, -3.5F, 3.9F, 5.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(88, 41).addBox(0.7F, -0.3F, -3.5F, 3.9F, 5.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(140, 65).addBox(-1.3F, 1F, -3.9F, 2.6F, 2.6F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(42, 71).addBox(-0.8F, 1.5F, -4.3F, 1.6F, 1.6F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(48, 71).addBox(-3.3F, 5.5F, -3.2F, 6.6F, 1.15F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(64, 71).addBox(-3.1F, 6.95F, -3.2F, 6.2F, 1.15F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(80, 71).addBox(-2.9F, 8.4F, -3.2F, 5.8F, 1.15F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(100, 41).addBox(-4.7F, -1.1F, -3F, 9.4F, 1.3F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(132, 41).addBox(-4.6F, 9.9F, -2.9F, 9.2F, 1.3F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(246, 71).addBox(-2.1F, 10.25F, -3.15F, 1F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(250, 71).addBox(-0.5F, 10.25F, -3.15F, 1F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(0, 75).addBox(1.1F, 10.25F, -3.15F, 1F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(230, 0).addBox(-3.8F, 0.4F, 2.8F, 7.6F, 8.6F, 1.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(204, 0).addBox(-3.3F, 0.35F, 2.3F, 6.6F, 8.7F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(94, 71).addBox(-3.3F, 0.05F, 2.8F, 6.6F, 0.45F, 1.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(148, 65).addBox(-3.4F, 8.8F, 2.8F, 2.4F, 1.6F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(158, 65).addBox(1F, 8.8F, 2.8F, 2.4F, 1.6F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(112, 71).addBox(-3F, 10.4F, 3.1F, 1.6F, 0.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(120, 71).addBox(1.4F, 10.4F, 3.1F, 1.6F, 0.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(164, 41).addBox(-4.2571F, -7.7635F, 4.4F, 1.9F, 7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -1.0123F));
                p[1].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(96, 31).addBox(-3.5571F, -8.1635F, 4.1F, 0.5F, 7.4F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -1.0123F));
                p[1].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(216, 50).addBox(-5.047F, -7.8478F, 4.45F, 1.9F, 6F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -1.4312F));
                p[1].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(170, 41).addBox(-4.347F, -8.2478F, 4.15F, 0.5F, 6.4F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -1.4312F));
                p[1].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(140, 58).addBox(-5.287F, -8.5403F, 4.5F, 1.9F, 4.8F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -1.85F));
                p[1].addOrReplaceChild("c25", CubeListBuilder.create().texOffs(222, 50).addBox(-4.587F, -8.9403F, 4.2F, 0.5F, 5.2F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -1.85F));
                p[1].addOrReplaceChild("c26", CubeListBuilder.create().texOffs(174, 41).addBox(2.3571F, -7.7635F, 4.4F, 1.9F, 7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.0123F));
                p[1].addOrReplaceChild("c27", CubeListBuilder.create().texOffs(100, 31).addBox(3.0571F, -8.1635F, 4.1F, 0.5F, 7.4F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.0123F));
                p[1].addOrReplaceChild("c28", CubeListBuilder.create().texOffs(226, 50).addBox(3.147F, -7.8478F, 4.45F, 1.9F, 6F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.4312F));
                p[1].addOrReplaceChild("c29", CubeListBuilder.create().texOffs(180, 41).addBox(3.847F, -8.2478F, 4.15F, 0.5F, 6.4F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.4312F));
                p[1].addOrReplaceChild("c30", CubeListBuilder.create().texOffs(146, 58).addBox(3.387F, -8.5403F, 4.5F, 1.9F, 4.8F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.85F));
                p[1].addOrReplaceChild("c31", CubeListBuilder.create().texOffs(232, 50).addBox(4.087F, -8.9403F, 4.2F, 0.5F, 5.2F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.85F));
                p[1].addOrReplaceChild("c32", CubeListBuilder.create().texOffs(168, 65).addBox(-1.2F, 1.4F, 4.9F, 2.4F, 2.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c33", CubeListBuilder.create().texOffs(96, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c34", CubeListBuilder.create().texOffs(104, 31).addBox(-4.3F, -3.6F, -2.6F, 6.6F, 3F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c35", CubeListBuilder.create().texOffs(0, 20).addBox(-3.7F, -3.65F, -3.2F, 5.4F, 3.1F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c36", CubeListBuilder.create().texOffs(0, 58).addBox(-3.7F, -4.02F, -2.6F, 5.4F, 0.52F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c37", CubeListBuilder.create().texOffs(184, 41).addBox(-4.35F, -1F, -3.25F, 6.7F, 0.5F, 6.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c38", CubeListBuilder.create().texOffs(212, 41).addBox(-4F, -0.6F, -2.9F, 6F, 1.4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c39", CubeListBuilder.create().texOffs(130, 31).addBox(-3.7F, 0.6F, -2.2F, 0.5F, 3.2F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c40", CubeListBuilder.create().texOffs(24, 58).addBox(-3.35F, 3.4F, -2.35F, 4.7F, 1.2F, 4.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c41", CubeListBuilder.create().texOffs(168, 20).addBox(-3.8F, 5F, -2.3F, 5.6F, 4.6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c42", CubeListBuilder.create().texOffs(26, 20).addBox(-3.3F, 4.95F, -2.8F, 4.6F, 4.7F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c43", CubeListBuilder.create().texOffs(176, 65).addBox(-3.9F, 5.8F, -1.6F, 0.5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c44", CubeListBuilder.create().texOffs(236, 41).addBox(-3.45F, 9.6F, -2.45F, 4.9F, 2.6F, 4.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c45", CubeListBuilder.create().texOffs(44, 58).addBox(-4.6F, -4.6F, -2.1F, 7.2F, 1.6F, 4.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c46", CubeListBuilder.create().texOffs(0, 50).addBox(-4.1F, -4.65F, -2.6F, 6.2F, 1.7F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c47", CubeListBuilder.create().texOffs(152, 58).addBox(-4.1F, -4.95F, -2.1F, 6.2F, 0.45F, 4.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c48", CubeListBuilder.create().texOffs(112, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c49", CubeListBuilder.create().texOffs(142, 31).addBox(-2.3F, -3.6F, -2.6F, 6.6F, 3F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c50", CubeListBuilder.create().texOffs(48, 20).addBox(-1.7F, -3.65F, -3.2F, 5.4F, 3.1F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c51", CubeListBuilder.create().texOffs(70, 58).addBox(-1.7F, -4.02F, -2.6F, 5.4F, 0.52F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c52", CubeListBuilder.create().texOffs(26, 50).addBox(-2.35F, -1F, -3.25F, 6.7F, 0.5F, 6.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c53", CubeListBuilder.create().texOffs(54, 50).addBox(-2F, -0.6F, -2.9F, 6F, 1.4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c54", CubeListBuilder.create().texOffs(168, 31).addBox(3.2F, 0.6F, -2.2F, 0.5F, 3.2F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c55", CubeListBuilder.create().texOffs(94, 58).addBox(-1.35F, 3.4F, -2.35F, 4.7F, 1.2F, 4.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c56", CubeListBuilder.create().texOffs(190, 20).addBox(-1.8F, 5F, -2.3F, 5.6F, 4.6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c57", CubeListBuilder.create().texOffs(74, 20).addBox(-1.3F, 4.95F, -2.8F, 4.6F, 4.7F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c58", CubeListBuilder.create().texOffs(180, 65).addBox(3.4F, 5.8F, -1.6F, 0.5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c59", CubeListBuilder.create().texOffs(78, 50).addBox(-1.45F, 9.6F, -2.45F, 4.9F, 2.6F, 4.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c60", CubeListBuilder.create().texOffs(114, 58).addBox(-2.6F, -4.6F, -2.1F, 7.2F, 1.6F, 4.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c61", CubeListBuilder.create().texOffs(98, 50).addBox(-2.1F, -4.65F, -2.6F, 6.2F, 1.7F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c62", CubeListBuilder.create().texOffs(176, 58).addBox(-2.1F, -4.95F, -2.1F, 6.2F, 0.45F, 4.2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(180, 31).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.6F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(212, 20).addBox(-4.9F, 9.4F, -2.6F, 1F, 3.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(226, 20).addBox(3.9F, 9.4F, -2.6F, 1F, 3.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(184, 65).addBox(-1.6F, 9.8F, -2.95F, 3.2F, 2.6F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(128, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(50, 65).addBox(-2.4F, 0.4F, -3F, 4.8F, 3.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(194, 65).addBox(-2.5F, 4.1F, -3.1F, 5F, 2.6F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(62, 65).addBox(-2.1F, 4.05F, -3.5F, 4.2F, 2.7F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(4, 75).addBox(-0.6F, 4.9F, -3.75F, 1.2F, 1F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(128, 71).addBox(-2.3F, 6.8F, -3F, 4.6F, 1.2F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(204, 31).addBox(-2.9F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(214, 31).addBox(2.4F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(144, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(76, 65).addBox(-2.4F, 0.4F, -3F, 4.8F, 3.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(206, 65).addBox(-2.5F, 4.1F, -3.1F, 5F, 2.6F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(88, 65).addBox(-2.1F, 4.05F, -3.5F, 4.2F, 2.7F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(10, 75).addBox(-0.6F, 4.9F, -3.75F, 1.2F, 1F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(140, 71).addBox(-2.3F, 6.8F, -3F, 4.6F, 1.2F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(224, 31).addBox(-2.9F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(234, 31).addBox(2.4F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 31).addBox(-2.7F, 7.6F, -2.8F, 5.4F, 4.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(96, 20).addBox(-2.1F, 7.55F, -3.4F, 4.2F, 4.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(200, 58).addBox(-2.1F, 7.18F, -2.8F, 4.2F, 0.52F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(218, 65).addBox(-2.6F, 10.1F, -4.1F, 5.2F, 1.8F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 41).addBox(-2.9F, 11.9F, -3.9F, 5.8F, 1.2F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(124, 50).addBox(-2.95F, 12.25F, -3.95F, 5.9F, 0.4F, 6.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(234, 65).addBox(-3.2F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(240, 65).addBox(2.6F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(152, 71).addBox(-1F, 9.8F, 2.3F, 2F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(16, 75).addBox(-0.6F, 11.4F, 2.5F, 1.2F, 0.3F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(220, 58).addBox(-3.35F, 6.9025F, 2.4569F, 0.5F, 1.4F, 3.6F, new CubeDeformation(0F)), PartPose.rotation(-0.3491F, 0F, 0F));
                p[4].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(22, 75).addBox(-3.375F, 6.4636F, 5.0297F, 0.55F, 0.5F, 1F, new CubeDeformation(0F)), PartPose.rotation(-0.3491F, 0F, 0F));
                p[4].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(230, 58).addBox(2.85F, 6.9025F, 2.4569F, 0.5F, 1.4F, 3.6F, new CubeDeformation(0F)), PartPose.rotation(-0.3491F, 0F, 0F));
                p[4].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(26, 75).addBox(2.825F, 6.4636F, 5.0297F, 0.55F, 0.5F, 1F, new CubeDeformation(0F)), PartPose.rotation(-0.3491F, 0F, 0F));
                p[5].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(22, 31).addBox(-2.7F, 7.6F, -2.8F, 5.4F, 4.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(118, 20).addBox(-2.1F, 7.55F, -3.4F, 4.2F, 4.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(0, 65).addBox(-2.1F, 7.18F, -2.8F, 4.2F, 0.52F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(0, 71).addBox(-2.6F, 10.1F, -4.1F, 5.2F, 1.8F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(26, 41).addBox(-2.9F, 11.9F, -3.9F, 5.8F, 1.2F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(150, 50).addBox(-2.95F, 12.25F, -3.95F, 5.9F, 0.4F, 6.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(16, 71).addBox(-3.2F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(22, 71).addBox(2.6F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(158, 71).addBox(-1F, 9.8F, 2.3F, 2F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(30, 75).addBox(-0.6F, 11.4F, 2.5F, 1.2F, 0.3F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(20, 65).addBox(-3.35F, 6.9025F, 2.4569F, 0.5F, 1.4F, 3.6F, new CubeDeformation(0F)), PartPose.rotation(-0.3491F, 0F, 0F));
                p[5].addOrReplaceChild("c25", CubeListBuilder.create().texOffs(36, 75).addBox(-3.375F, 6.4636F, 5.0297F, 0.55F, 0.5F, 1F, new CubeDeformation(0F)), PartPose.rotation(-0.3491F, 0F, 0F));
                p[5].addOrReplaceChild("c26", CubeListBuilder.create().texOffs(30, 65).addBox(2.85F, 6.9025F, 2.4569F, 0.5F, 1.4F, 3.6F, new CubeDeformation(0F)), PartPose.rotation(-0.3491F, 0F, 0F));
                p[5].addOrReplaceChild("c27", CubeListBuilder.create().texOffs(40, 75).addBox(2.825F, 6.4636F, 5.0297F, 0.55F, 0.5F, 1F, new CubeDeformation(0F)), PartPose.rotation(-0.3491F, 0F, 0F));
            }
            default -> {
            }
        }
    }

    private static void a_neutronic(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(36, 0).addBox(-4.8F, -9F, -4F, 9.6F, 9.2F, 8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -9.05F, -4.8F, 8F, 9.3F, 9.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(94, 32).addBox(-4F, -9.56F, -4F, 8F, 0.66F, 8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(168, 51).addBox(-4.1F, -7.6F, -5.3F, 8.2F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(80, 66).addBox(-2.6F, -1.9F, -5.7F, 5.2F, 1.5F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(116, 42).addBox(4.5F, -6.6F, -2F, 1.1F, 3.8F, 3.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(128, 42).addBox(-5.6F, -6.6F, -2F, 1.1F, 3.8F, 3.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(166, 59).addBox(5.4F, -5.4F, -0.8F, 0.4F, 1.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(172, 59).addBox(-5.8F, -5.4F, -0.8F, 0.4F, 1.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(176, 0).addBox(-5.2F, -0.9F, -5.2F, 10.4F, 1.2F, 10.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(202, 20).addBox(-0.5F, -9.9F, -3.6F, 1F, 1F, 8.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(0, 70).addBox(-4.6F, -8.3F, -5.6F, 9.2F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(188, 51).addBox(-5F, -5.4F, -5.2F, 1.2F, 3.8F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(198, 51).addBox(3.8F, -5.4F, -5.2F, 1.2F, 3.8F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(44, 59).addBox(-3.8F, -7.3F, -5.7F, 7.6F, 4.2F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(22, 70).addBox(1.7768F, -5.7146F, -6F, 2.4F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.1745F));
                p[0].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(30, 70).addBox(-4.1768F, -5.7146F, -6F, 2.4F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.1745F));
                p[0].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(220, 0).addBox(4.8F, -8.0935F, -5.7027F, 0.8F, 1.8F, 10F, new CubeDeformation(0F)), PartPose.rotation(-0.3491F, 0F, 0F));
                p[0].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(0, 20).addBox(-5.6F, -8.0935F, -5.7027F, 0.8F, 1.8F, 10F, new CubeDeformation(0F)), PartPose.rotation(-0.3491F, 0F, 0F));
                p[0].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(126, 32).addBox(4.825F, -7.3495F, -4.6685F, 0.85F, 0.5F, 8F, new CubeDeformation(0F)), PartPose.rotation(-0.3491F, 0F, 0F));
                p[0].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(144, 32).addBox(-5.675F, -7.3495F, -4.6685F, 0.85F, 0.5F, 8F, new CubeDeformation(0F)), PartPose.rotation(-0.3491F, 0F, 0F));
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(72, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.9F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(140, 42).addBox(-4.6F, -0.3F, -3.5F, 3.9F, 5.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(152, 42).addBox(0.7F, -0.3F, -3.5F, 3.9F, 5.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(178, 59).addBox(-1.3F, 1F, -3.9F, 2.6F, 2.6F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(94, 66).addBox(-0.8F, 1.5F, -4.3F, 1.6F, 1.6F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(100, 66).addBox(-3.3F, 5.5F, -3.2F, 6.6F, 1.15F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(116, 66).addBox(-3.1F, 6.95F, -3.2F, 6.2F, 1.15F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(132, 66).addBox(-2.9F, 8.4F, -3.2F, 5.8F, 1.15F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(164, 42).addBox(-4.7F, -1.1F, -3F, 9.4F, 1.3F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(196, 42).addBox(-4.6F, 9.9F, -2.9F, 9.2F, 1.3F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(38, 70).addBox(-2.9F, 10.25F, -3.15F, 1F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(42, 70).addBox(-1.3F, 10.25F, -3.15F, 1F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(46, 70).addBox(0.3F, 10.25F, -3.15F, 1F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(50, 70).addBox(1.9F, 10.25F, -3.15F, 1F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(42, 20).addBox(-3.8F, 0.4F, 2.8F, 7.6F, 8.6F, 1.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(22, 20).addBox(-3.3F, 0.35F, 2.3F, 6.6F, 8.7F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(146, 66).addBox(-3.3F, 0.05F, 2.8F, 6.6F, 0.45F, 1.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(186, 59).addBox(-3.4F, 8.8F, 2.8F, 2.4F, 1.6F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(196, 59).addBox(1F, 8.8F, 2.8F, 2.4F, 1.6F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(164, 66).addBox(-3F, 10.4F, 3.1F, 1.6F, 0.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(172, 66).addBox(1.4F, 10.4F, 3.1F, 1.6F, 0.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(96, 0).addBox(-4.0554F, -8.5079F, 4.95F, 1.3F, 14F, 1.3F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.1745F));
                p[1].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(206, 59).addBox(-4.4054F, -9.4079F, 4.6F, 2F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.1745F));
                p[1].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(162, 32).addBox(-3.7054F, -5.5079F, 6.2F, 0.6F, 8F, 0.2F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.1745F));
                p[1].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(180, 66).addBox(-4.4054F, 0.0921F, 4.6F, 2F, 0.8F, 2F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.1745F));
                p[1].addOrReplaceChild("c25", CubeListBuilder.create().texOffs(104, 0).addBox(2.7554F, -8.5079F, 4.95F, 1.3F, 14F, 1.3F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.1745F));
                p[1].addOrReplaceChild("c26", CubeListBuilder.create().texOffs(214, 59).addBox(2.4054F, -9.4079F, 4.6F, 2F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.1745F));
                p[1].addOrReplaceChild("c27", CubeListBuilder.create().texOffs(166, 32).addBox(3.1054F, -5.5079F, 6.2F, 0.6F, 8F, 0.2F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.1745F));
                p[1].addOrReplaceChild("c28", CubeListBuilder.create().texOffs(188, 66).addBox(2.4054F, 0.0921F, 4.6F, 2F, 0.8F, 2F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.1745F));
                p[1].addOrReplaceChild("c29", CubeListBuilder.create().texOffs(62, 59).addBox(-0.35F, 5F, -3.6F, 0.7F, 4.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c30", CubeListBuilder.create().texOffs(112, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c31", CubeListBuilder.create().texOffs(170, 32).addBox(-4.3F, -3.6F, -2.6F, 6.6F, 3F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c32", CubeListBuilder.create().texOffs(62, 20).addBox(-3.7F, -3.65F, -3.2F, 5.4F, 3.1F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c33", CubeListBuilder.create().texOffs(208, 51).addBox(-3.7F, -4.02F, -2.6F, 5.4F, 0.52F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c34", CubeListBuilder.create().texOffs(228, 42).addBox(-4.35F, -1F, -3.25F, 6.7F, 0.5F, 6.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c35", CubeListBuilder.create().texOffs(0, 51).addBox(-4F, -0.6F, -2.9F, 6F, 1.4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c36", CubeListBuilder.create().texOffs(196, 32).addBox(-3.7F, 0.6F, -2.2F, 0.5F, 3.2F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c37", CubeListBuilder.create().texOffs(232, 51).addBox(-3.35F, 3.4F, -2.35F, 4.7F, 1.2F, 4.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c38", CubeListBuilder.create().texOffs(222, 20).addBox(-3.8F, 5F, -2.3F, 5.6F, 4.6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c39", CubeListBuilder.create().texOffs(88, 20).addBox(-3.3F, 4.95F, -2.8F, 4.6F, 4.7F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c40", CubeListBuilder.create().texOffs(222, 59).addBox(-3.9F, 5.8F, -1.6F, 0.5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c41", CubeListBuilder.create().texOffs(24, 51).addBox(-3.45F, 9.6F, -2.45F, 4.9F, 2.6F, 4.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c42", CubeListBuilder.create().texOffs(106, 59).addBox(-4F, 5.4F, -0.4F, 0.5F, 4F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c43", CubeListBuilder.create().texOffs(128, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c44", CubeListBuilder.create().texOffs(208, 32).addBox(-2.3F, -3.6F, -2.6F, 6.6F, 3F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c45", CubeListBuilder.create().texOffs(110, 20).addBox(-1.7F, -3.65F, -3.2F, 5.4F, 3.1F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c46", CubeListBuilder.create().texOffs(0, 59).addBox(-1.7F, -4.02F, -2.6F, 5.4F, 0.52F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c47", CubeListBuilder.create().texOffs(44, 51).addBox(-2.35F, -1F, -3.25F, 6.7F, 0.5F, 6.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c48", CubeListBuilder.create().texOffs(72, 51).addBox(-2F, -0.6F, -2.9F, 6F, 1.4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c49", CubeListBuilder.create().texOffs(234, 32).addBox(3.2F, 0.6F, -2.2F, 0.5F, 3.2F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c50", CubeListBuilder.create().texOffs(24, 59).addBox(-1.35F, 3.4F, -2.35F, 4.7F, 1.2F, 4.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c51", CubeListBuilder.create().texOffs(0, 32).addBox(-1.8F, 5F, -2.3F, 5.6F, 4.6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c52", CubeListBuilder.create().texOffs(136, 20).addBox(-1.3F, 4.95F, -2.8F, 4.6F, 4.7F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c53", CubeListBuilder.create().texOffs(226, 59).addBox(3.4F, 5.8F, -1.6F, 0.5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c54", CubeListBuilder.create().texOffs(96, 51).addBox(-1.45F, 9.6F, -2.45F, 4.9F, 2.6F, 4.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c55", CubeListBuilder.create().texOffs(110, 59).addBox(3.5F, 5.4F, -0.4F, 0.5F, 4F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 42).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.6F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(22, 32).addBox(-4.9F, 9.4F, -2.6F, 1F, 3.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(36, 32).addBox(3.9F, 9.4F, -2.6F, 1F, 3.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(230, 59).addBox(-1.6F, 9.8F, -2.95F, 3.2F, 2.6F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(144, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(114, 59).addBox(-2.4F, 0.4F, -3F, 4.8F, 3.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(240, 59).addBox(-2.5F, 4.1F, -3.1F, 5F, 2.6F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(126, 59).addBox(-2.1F, 4.05F, -3.5F, 4.2F, 2.7F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(54, 70).addBox(-0.6F, 4.9F, -3.75F, 1.2F, 1F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(196, 66).addBox(-2.3F, 6.8F, -3F, 4.6F, 1.2F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(24, 42).addBox(-2.9F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(34, 42).addBox(2.4F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(252, 59).addBox(-2.45F, 0.9F, -3.2F, 0.5F, 2.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(0, 66).addBox(1.95F, 0.9F, -3.2F, 0.5F, 2.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(160, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(140, 59).addBox(-2.4F, 0.4F, -3F, 4.8F, 3.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(4, 66).addBox(-2.5F, 4.1F, -3.1F, 5F, 2.6F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(152, 59).addBox(-2.1F, 4.05F, -3.5F, 4.2F, 2.7F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(60, 70).addBox(-0.6F, 4.9F, -3.75F, 1.2F, 1F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(208, 66).addBox(-2.3F, 6.8F, -3F, 4.6F, 1.2F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(44, 42).addBox(-2.9F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(54, 42).addBox(2.4F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(16, 66).addBox(-2.45F, 0.9F, -3.2F, 0.5F, 2.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(20, 66).addBox(1.95F, 0.9F, -3.2F, 0.5F, 2.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(50, 32).addBox(-2.7F, 7.6F, -2.8F, 5.4F, 4.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(158, 20).addBox(-2.1F, 7.55F, -3.4F, 4.2F, 4.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(66, 59).addBox(-2.1F, 7.18F, -2.8F, 4.2F, 0.52F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(24, 66).addBox(-2.6F, 10.1F, -4.1F, 5.2F, 1.8F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(64, 42).addBox(-2.9F, 11.9F, -3.9F, 5.8F, 1.2F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(116, 51).addBox(-2.95F, 12.25F, -3.95F, 5.9F, 0.4F, 6.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(40, 66).addBox(-3.2F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(46, 66).addBox(2.6F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(220, 66).addBox(-1F, 9.8F, 2.3F, 2F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(66, 70).addBox(-0.6F, 11.4F, 2.5F, 1.2F, 0.3F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(226, 66).addBox(-0.4F, 8.2F, -3.5F, 0.8F, 2F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(72, 32).addBox(-2.7F, 7.6F, -2.8F, 5.4F, 4.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(180, 20).addBox(-2.1F, 7.55F, -3.4F, 4.2F, 4.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(86, 59).addBox(-2.1F, 7.18F, -2.8F, 4.2F, 0.52F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(52, 66).addBox(-2.6F, 10.1F, -4.1F, 5.2F, 1.8F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(90, 42).addBox(-2.9F, 11.9F, -3.9F, 5.8F, 1.2F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(142, 51).addBox(-2.95F, 12.25F, -3.95F, 5.9F, 0.4F, 6.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(68, 66).addBox(-3.2F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(74, 66).addBox(2.6F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(230, 66).addBox(-1F, 9.8F, 2.3F, 2F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(72, 70).addBox(-0.6F, 11.4F, 2.5F, 1.2F, 0.3F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(236, 66).addBox(-0.4F, 8.2F, -3.5F, 0.8F, 2F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_singularity(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(36, 0).addBox(-4.8F, -9F, -4F, 9.6F, 9.2F, 8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -9.05F, -4.8F, 8F, 9.3F, 9.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(22, 31).addBox(-4F, -9.56F, -4F, 8F, 0.66F, 8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(52, 49).addBox(-4.1F, -7.6F, -5.3F, 8.2F, 6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 63).addBox(-2.6F, -1.9F, -5.7F, 5.2F, 1.5F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(0, 41).addBox(4.5F, -6.6F, -2F, 1.1F, 3.8F, 3.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(12, 41).addBox(-5.6F, -6.6F, -2F, 1.1F, 3.8F, 3.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(92, 57).addBox(5.4F, -5.4F, -0.8F, 0.4F, 1.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(98, 57).addBox(-5.8F, -5.4F, -0.8F, 0.4F, 1.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(160, 0).addBox(-5.2F, -0.9F, -5.2F, 10.4F, 1.2F, 10.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(140, 20).addBox(-0.5F, -9.9F, -3.6F, 1F, 1F, 8.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(116, 66).addBox(-4.6F, -8.3F, -5.6F, 9.2F, 0.8F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(72, 49).addBox(-5F, -5.4F, -5.2F, 1.2F, 3.8F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(82, 49).addBox(3.8F, -5.4F, -5.2F, 1.2F, 3.8F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(180, 49).addBox(-3.8F, -7.3F, -5.65F, 7.6F, 4.8F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(138, 66).addBox(-4F, -7.6F, -5.9F, 8F, 0.5F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(156, 66).addBox(-4F, -2.8F, -5.9F, 8F, 0.5F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(198, 49).addBox(-4F, -7.1F, -5.9F, 0.5F, 4.3F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(202, 49).addBox(3.5F, -7.1F, -5.9F, 0.5F, 4.3F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(206, 49).addBox(1.592F, -11.1306F, -1.7F, 1.8F, 3.6F, 1.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.9076F));
                p[0].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(214, 49).addBox(-3.4874F, -13.8445F, -1.45F, 1.3F, 3.2F, 1.3F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.4189F));
                p[0].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(14, 63).addBox(-7.2531F, -13.7096F, -1.2F, 0.8F, 1.8F, 0.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.1047F));
                p[0].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(222, 49).addBox(-3.392F, -11.1306F, -1.7F, 1.8F, 3.6F, 1.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.9076F));
                p[0].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(230, 49).addBox(2.1874F, -13.8445F, -1.45F, 1.3F, 3.2F, 1.3F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.4189F));
                p[0].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(18, 63).addBox(6.4531F, -13.7096F, -1.2F, 0.8F, 1.8F, 0.8F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.1047F));
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(72, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.9F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 41).addBox(-4.6F, -0.3F, -3.5F, 3.9F, 5.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(36, 41).addBox(0.7F, -0.3F, -3.5F, 3.9F, 5.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(104, 57).addBox(-1.3F, 1F, -3.9F, 2.6F, 2.6F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(22, 63).addBox(-0.8F, 1.5F, -4.3F, 1.6F, 1.6F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(28, 63).addBox(-3.3F, 5.5F, -3.2F, 6.6F, 1.15F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(44, 63).addBox(-3.1F, 6.95F, -3.2F, 6.2F, 1.15F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(60, 63).addBox(-2.9F, 8.4F, -3.2F, 5.8F, 1.15F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(48, 41).addBox(-4.7F, -1.1F, -3F, 9.4F, 1.3F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(80, 41).addBox(-4.6F, 9.9F, -2.9F, 9.2F, 1.3F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(174, 66).addBox(-3.7F, 10.25F, -3.15F, 1F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(178, 66).addBox(-2.1F, 10.25F, -3.15F, 1F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(182, 66).addBox(-0.5F, 10.25F, -3.15F, 1F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(186, 66).addBox(1.1F, 10.25F, -3.15F, 1F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(190, 66).addBox(2.7F, 10.25F, -3.15F, 1F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(224, 0).addBox(-3.8F, 0.4F, 2.8F, 7.6F, 8.6F, 1.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(204, 0).addBox(-3.3F, 0.35F, 2.3F, 6.6F, 8.7F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(74, 63).addBox(-3.3F, 0.05F, 2.8F, 6.6F, 0.45F, 1.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(112, 57).addBox(-3.4F, 8.8F, 2.8F, 2.4F, 1.6F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(122, 57).addBox(1F, 8.8F, 2.8F, 2.4F, 1.6F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(92, 63).addBox(-3F, 10.4F, 3.1F, 1.6F, 0.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(100, 63).addBox(1.4F, 10.4F, 3.1F, 1.6F, 0.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(108, 63).addBox(1.45F, -8F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.5708F));
                p[1].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(116, 63).addBox(1.3614F, -8.6729F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.8326F));
                p[1].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(124, 63).addBox(1.1017F, -9.3F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 2.0944F));
                p[1].addOrReplaceChild("c25", CubeListBuilder.create().texOffs(132, 63).addBox(0.6885F, -9.8385F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 2.3562F));
                p[1].addOrReplaceChild("c26", CubeListBuilder.create().texOffs(140, 63).addBox(0.15F, -10.2517F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 2.618F));
                p[1].addOrReplaceChild("c27", CubeListBuilder.create().texOffs(148, 63).addBox(-0.4771F, -10.5114F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 2.8798F));
                p[1].addOrReplaceChild("c28", CubeListBuilder.create().texOffs(156, 63).addBox(-1.15F, -10.6F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 3.1416F));
                p[1].addOrReplaceChild("c29", CubeListBuilder.create().texOffs(164, 63).addBox(-1.8229F, -10.5114F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 3.4034F));
                p[1].addOrReplaceChild("c30", CubeListBuilder.create().texOffs(172, 63).addBox(-2.45F, -10.2517F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 3.6652F));
                p[1].addOrReplaceChild("c31", CubeListBuilder.create().texOffs(180, 63).addBox(-2.9885F, -9.8385F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 3.927F));
                p[1].addOrReplaceChild("c32", CubeListBuilder.create().texOffs(188, 63).addBox(-3.4017F, -9.3F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 4.1888F));
                p[1].addOrReplaceChild("c33", CubeListBuilder.create().texOffs(196, 63).addBox(-3.6614F, -8.6729F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 4.4506F));
                p[1].addOrReplaceChild("c34", CubeListBuilder.create().texOffs(204, 63).addBox(-3.75F, -8F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 4.7124F));
                p[1].addOrReplaceChild("c35", CubeListBuilder.create().texOffs(212, 63).addBox(-3.6614F, -7.3271F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 4.9742F));
                p[1].addOrReplaceChild("c36", CubeListBuilder.create().texOffs(220, 63).addBox(-3.4017F, -6.7F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 5.236F));
                p[1].addOrReplaceChild("c37", CubeListBuilder.create().texOffs(228, 63).addBox(-2.9885F, -6.1615F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 5.4978F));
                p[1].addOrReplaceChild("c38", CubeListBuilder.create().texOffs(236, 63).addBox(-2.45F, -5.7483F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 5.7596F));
                p[1].addOrReplaceChild("c39", CubeListBuilder.create().texOffs(244, 63).addBox(-1.8229F, -5.4886F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 6.0214F));
                p[1].addOrReplaceChild("c40", CubeListBuilder.create().texOffs(0, 66).addBox(-1.15F, -5.4F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 6.2832F));
                p[1].addOrReplaceChild("c41", CubeListBuilder.create().texOffs(8, 66).addBox(-0.4771F, -5.4886F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 6.545F));
                p[1].addOrReplaceChild("c42", CubeListBuilder.create().texOffs(16, 66).addBox(0.15F, -5.7483F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 6.8068F));
                p[1].addOrReplaceChild("c43", CubeListBuilder.create().texOffs(24, 66).addBox(0.6885F, -6.1615F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 7.0686F));
                p[1].addOrReplaceChild("c44", CubeListBuilder.create().texOffs(32, 66).addBox(1.1017F, -6.7F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 7.3304F));
                p[1].addOrReplaceChild("c45", CubeListBuilder.create().texOffs(40, 66).addBox(1.3614F, -7.3271F, 6.2F, 2.3F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 7.5922F));
                p[1].addOrReplaceChild("c46", CubeListBuilder.create().texOffs(194, 66).addBox(1.4105F, -6.6015F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.7453F));
                p[1].addOrReplaceChild("c47", CubeListBuilder.create().texOffs(202, 66).addBox(1.1017F, -7.45F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 2.0944F));
                p[1].addOrReplaceChild("c48", CubeListBuilder.create().texOffs(210, 66).addBox(0.5212F, -8.1417F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 2.4435F));
                p[1].addOrReplaceChild("c49", CubeListBuilder.create().texOffs(218, 66).addBox(-0.2607F, -8.5932F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 2.7925F));
                p[1].addOrReplaceChild("c50", CubeListBuilder.create().texOffs(226, 66).addBox(-1.15F, -8.75F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 3.1416F));
                p[1].addOrReplaceChild("c51", CubeListBuilder.create().texOffs(234, 66).addBox(-2.0393F, -8.5932F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 3.4907F));
                p[1].addOrReplaceChild("c52", CubeListBuilder.create().texOffs(242, 66).addBox(-2.8212F, -8.1417F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 3.8397F));
                p[1].addOrReplaceChild("c53", CubeListBuilder.create().texOffs(0, 69).addBox(-3.4017F, -7.45F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 4.1888F));
                p[1].addOrReplaceChild("c54", CubeListBuilder.create().texOffs(8, 69).addBox(-3.7105F, -6.6015F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 4.5379F));
                p[1].addOrReplaceChild("c55", CubeListBuilder.create().texOffs(16, 69).addBox(-3.7105F, -5.6985F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 4.8869F));
                p[1].addOrReplaceChild("c56", CubeListBuilder.create().texOffs(24, 69).addBox(-3.4017F, -4.85F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 5.236F));
                p[1].addOrReplaceChild("c57", CubeListBuilder.create().texOffs(32, 69).addBox(-2.8212F, -4.1583F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 5.5851F));
                p[1].addOrReplaceChild("c58", CubeListBuilder.create().texOffs(40, 69).addBox(-2.0393F, -3.7068F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 5.9341F));
                p[1].addOrReplaceChild("c59", CubeListBuilder.create().texOffs(48, 69).addBox(-1.15F, -3.55F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 6.2832F));
                p[1].addOrReplaceChild("c60", CubeListBuilder.create().texOffs(56, 69).addBox(-0.2607F, -3.7068F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 6.6323F));
                p[1].addOrReplaceChild("c61", CubeListBuilder.create().texOffs(64, 69).addBox(0.5212F, -4.1583F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 6.9813F));
                p[1].addOrReplaceChild("c62", CubeListBuilder.create().texOffs(72, 69).addBox(1.1017F, -4.85F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 7.3304F));
                p[1].addOrReplaceChild("c63", CubeListBuilder.create().texOffs(80, 69).addBox(1.4105F, -5.6985F, 6F, 2.3F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 7.6794F));
                p[1].addOrReplaceChild("c64", CubeListBuilder.create().texOffs(48, 66).addBox(-1F, 2F, 5.1F, 2F, 2F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c65", CubeListBuilder.create().texOffs(54, 66).addBox(-0.6F, 2.4F, 5.8F, 1.2F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c66", CubeListBuilder.create().texOffs(96, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c67", CubeListBuilder.create().texOffs(54, 31).addBox(-4.3F, -3.6F, -2.6F, 6.6F, 3F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c68", CubeListBuilder.create().texOffs(0, 20).addBox(-3.7F, -3.65F, -3.2F, 5.4F, 3.1F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c69", CubeListBuilder.create().texOffs(92, 49).addBox(-3.7F, -4.02F, -2.6F, 5.4F, 0.52F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c70", CubeListBuilder.create().texOffs(112, 41).addBox(-4.35F, -1F, -3.25F, 6.7F, 0.5F, 6.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c71", CubeListBuilder.create().texOffs(140, 41).addBox(-4F, -0.6F, -2.9F, 6F, 1.4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c72", CubeListBuilder.create().texOffs(80, 31).addBox(-3.7F, 0.6F, -2.2F, 0.5F, 3.2F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c73", CubeListBuilder.create().texOffs(116, 49).addBox(-3.35F, 3.4F, -2.35F, 4.7F, 1.2F, 4.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c74", CubeListBuilder.create().texOffs(160, 20).addBox(-3.8F, 5F, -2.3F, 5.6F, 4.6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c75", CubeListBuilder.create().texOffs(26, 20).addBox(-3.3F, 4.95F, -2.8F, 4.6F, 4.7F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c76", CubeListBuilder.create().texOffs(132, 57).addBox(-3.9F, 5.8F, -1.6F, 0.5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c77", CubeListBuilder.create().texOffs(164, 41).addBox(-3.45F, 9.6F, -2.45F, 4.9F, 2.6F, 4.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c78", CubeListBuilder.create().texOffs(136, 57).addBox(-1.6083F, -6.4757F, -2.1F, 1F, 2.12F, 1F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.3142F));
                p[2].addOrReplaceChild("c79", CubeListBuilder.create().texOffs(140, 57).addBox(-0.9913F, -7.0675F, -0.5F, 1F, 2.6F, 1F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.4538F));
                p[2].addOrReplaceChild("c80", CubeListBuilder.create().texOffs(144, 57).addBox(-1.6083F, -6.4757F, 1.1F, 1F, 2.12F, 1F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.3142F));
                p[2].addOrReplaceChild("c81", CubeListBuilder.create().texOffs(60, 66).addBox(-0.7913F, -8.0675F, -0.3F, 0.6F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.4538F));
                p[3].addOrReplaceChild("c82", CubeListBuilder.create().texOffs(112, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.7F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c83", CubeListBuilder.create().texOffs(92, 31).addBox(-2.3F, -3.6F, -2.6F, 6.6F, 3F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c84", CubeListBuilder.create().texOffs(48, 20).addBox(-1.7F, -3.65F, -3.2F, 5.4F, 3.1F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c85", CubeListBuilder.create().texOffs(136, 49).addBox(-1.7F, -4.02F, -2.6F, 5.4F, 0.52F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c86", CubeListBuilder.create().texOffs(184, 41).addBox(-2.35F, -1F, -3.25F, 6.7F, 0.5F, 6.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c87", CubeListBuilder.create().texOffs(212, 41).addBox(-2F, -0.6F, -2.9F, 6F, 1.4F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c88", CubeListBuilder.create().texOffs(118, 31).addBox(3.2F, 0.6F, -2.2F, 0.5F, 3.2F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c89", CubeListBuilder.create().texOffs(160, 49).addBox(-1.35F, 3.4F, -2.35F, 4.7F, 1.2F, 4.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c90", CubeListBuilder.create().texOffs(182, 20).addBox(-1.8F, 5F, -2.3F, 5.6F, 4.6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c91", CubeListBuilder.create().texOffs(74, 20).addBox(-1.3F, 4.95F, -2.8F, 4.6F, 4.7F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c92", CubeListBuilder.create().texOffs(148, 57).addBox(3.4F, 5.8F, -1.6F, 0.5F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c93", CubeListBuilder.create().texOffs(236, 41).addBox(-1.45F, 9.6F, -2.45F, 4.9F, 2.6F, 4.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c94", CubeListBuilder.create().texOffs(152, 57).addBox(0.6083F, -6.4757F, -2.1F, 1F, 2.12F, 1F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.3142F));
                p[3].addOrReplaceChild("c95", CubeListBuilder.create().texOffs(156, 57).addBox(-0.0087F, -7.0675F, -0.5F, 1F, 2.6F, 1F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.4538F));
                p[3].addOrReplaceChild("c96", CubeListBuilder.create().texOffs(160, 57).addBox(0.6083F, -6.4757F, 1.1F, 1F, 2.12F, 1F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.3142F));
                p[3].addOrReplaceChild("c97", CubeListBuilder.create().texOffs(64, 66).addBox(0.1913F, -8.0675F, -0.3F, 0.6F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.4538F));
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(130, 31).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.6F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(204, 20).addBox(-4.9F, 9.4F, -2.6F, 1F, 3.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(218, 20).addBox(3.9F, 9.4F, -2.6F, 1F, 3.6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(164, 57).addBox(-1.6F, 9.8F, -2.95F, 3.2F, 2.6F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(128, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(40, 57).addBox(-2.4F, 0.4F, -3F, 4.8F, 3.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(174, 57).addBox(-2.5F, 4.1F, -3.1F, 5F, 2.6F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(52, 57).addBox(-2.1F, 4.05F, -3.5F, 4.2F, 2.7F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(88, 69).addBox(-0.6F, 4.9F, -3.75F, 1.2F, 1F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(68, 66).addBox(-2.3F, 6.8F, -3F, 4.6F, 1.2F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(154, 31).addBox(-2.9F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(164, 31).addBox(2.4F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(144, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(66, 57).addBox(-2.4F, 0.4F, -3F, 4.8F, 3.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(186, 57).addBox(-2.5F, 4.1F, -3.1F, 5F, 2.6F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(78, 57).addBox(-2.1F, 4.05F, -3.5F, 4.2F, 2.7F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(94, 69).addBox(-0.6F, 4.9F, -3.75F, 1.2F, 1F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(80, 66).addBox(-2.3F, 6.8F, -3F, 4.6F, 1.2F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(174, 31).addBox(-2.9F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(184, 31).addBox(2.4F, 0.8F, -1.6F, 0.5F, 4.6F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(232, 20).addBox(-2.7F, 7.6F, -2.8F, 5.4F, 4.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(96, 20).addBox(-2.1F, 7.55F, -3.4F, 4.2F, 4.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 57).addBox(-2.1F, 7.18F, -2.8F, 4.2F, 0.52F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(198, 57).addBox(-2.6F, 10.1F, -4.1F, 5.2F, 1.8F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(194, 31).addBox(-2.9F, 11.9F, -3.9F, 5.8F, 1.2F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(0, 49).addBox(-2.95F, 12.25F, -3.95F, 5.9F, 0.4F, 6.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(214, 57).addBox(-3.2F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(220, 57).addBox(2.6F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(92, 66).addBox(-1F, 9.8F, 2.3F, 2F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(100, 69).addBox(-0.6F, 11.4F, 2.5F, 1.2F, 0.3F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(98, 66).addBox(-0.6F, 9.6F, -4.6F, 1.2F, 1.2F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(106, 69).addBox(-0.4F, 9.8F, -5F, 0.8F, 0.8F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(0, 31).addBox(-2.7F, 7.6F, -2.8F, 5.4F, 4.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(118, 20).addBox(-2.1F, 7.55F, -3.4F, 4.2F, 4.5F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(20, 57).addBox(-2.1F, 7.18F, -2.8F, 4.2F, 0.52F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(226, 57).addBox(-2.6F, 10.1F, -4.1F, 5.2F, 1.8F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(220, 31).addBox(-2.9F, 11.9F, -3.9F, 5.8F, 1.2F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(26, 49).addBox(-2.95F, 12.25F, -3.95F, 5.9F, 0.4F, 6.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(242, 57).addBox(-3.2F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(248, 57).addBox(2.6F, 8.6F, -1F, 0.6F, 1.8F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(104, 66).addBox(-1F, 9.8F, 2.3F, 2F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(110, 69).addBox(-0.6F, 11.4F, 2.5F, 1.2F, 0.3F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(110, 66).addBox(-0.6F, 9.6F, -4.6F, 1.2F, 1.2F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(116, 69).addBox(-0.4F, 9.8F, -5F, 0.8F, 0.8F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_voltaic(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(36, 0).addBox(-4.7F, -8.8F, -4F, 9.4F, 8.9F, 8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8.85F, -4.7F, 8F, 9F, 9.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(198, 19).addBox(-4F, -9.29F, -4F, 8F, 0.59F, 8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(180, 47).addBox(-3.9F, -6.9F, -5.15F, 7.8F, 2.6F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(196, 53).addBox(-4.2F, -7.3F, -5.35F, 8.4F, 0.5F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(216, 53).addBox(-4.2F, -4.4F, -5.35F, 8.4F, 0.5F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(94, 53).addBox(-1.4F, -9.2F, -5.3F, 2.8F, 1.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(236, 53).addBox(-0.9F, -8.9F, -5.55F, 1.8F, 0.9F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(198, 47).addBox(-2.4F, -3.6F, -5.2F, 4.8F, 2.2F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(242, 53).addBox(-1.8F, -3F, -5.45F, 3.6F, 0.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(152, 39).addBox(-5.6F, -6.6F, -1.2F, 1F, 2.6F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(210, 47).addBox(-5.95F, -6.4F, -1F, 0.4F, 0.5F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(218, 47).addBox(-5.95F, -5.55F, -1F, 0.4F, 0.5F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(226, 47).addBox(-5.95F, -4.7F, -1F, 0.4F, 0.5F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(252, 53).addBox(-5.8F, -7.6F, -0.4F, 0.2F, 1F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(160, 39).addBox(4.6F, -6.6F, -1.2F, 1F, 2.6F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(234, 47).addBox(5.55F, -6.4F, -1F, 0.4F, 0.5F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(242, 47).addBox(5.55F, -5.55F, -1F, 0.4F, 0.5F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(0, 53).addBox(5.55F, -4.7F, -1F, 0.4F, 0.5F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(0, 57).addBox(5.6F, -7.6F, -0.4F, 0.2F, 1F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(160, 0).addBox(-5.2F, -0.8F, -5.2F, 10.4F, 1.1F, 10.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(230, 19).addBox(-0.5F, -9.9F, -3.4F, 1F, 0.9F, 7.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(72, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.85F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(190, 30).addBox(-4.7F, -0.4F, -3.1F, 9.4F, 6.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 30).addBox(-4.2F, -0.45F, -3.6F, 8.4F, 6.7F, 1.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(8, 53).addBox(-2.2F, 1.4F, -4.15F, 4.4F, 3F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(102, 53).addBox(-1.7F, 2F, -4.4F, 0.6F, 1.8F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(106, 53).addBox(-0.8F, 2F, -4.4F, 0.6F, 1.8F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(110, 53).addBox(0.1F, 2F, -4.4F, 0.6F, 1.8F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(114, 53).addBox(1F, 2F, -4.4F, 0.6F, 1.8F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(4, 57).addBox(-4.75F, 6.4F, -3.3F, 9.5F, 1F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(212, 30).addBox(-4.6F, 9.6F, -3F, 9.2F, 1.4F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(26, 57).addBox(-1F, 9.8F, -3.25F, 2F, 1F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(0, 39).addBox(-4.7F, -1F, -3F, 9.4F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(62, 19).addBox(-3.6F, 0.8F, 2.8F, 7.2F, 7.6F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(204, 0).addBox(-3.1F, 0.75F, 2.3F, 6.2F, 7.7F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(118, 53).addBox(-3.1F, 0.45F, 2.8F, 6.2F, 0.45F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(32, 57).addBox(-2.9F, 1.6F, 4.65F, 5.8F, 1F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(46, 57).addBox(-2.9F, 6.4F, 4.65F, 5.8F, 0.6F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(20, 53).addBox(-2F, 3F, 4.7F, 4F, 2.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(30, 53).addBox(-2F, -0.4F, 3F, 1.2F, 1.2F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(38, 53).addBox(-3.05F, -0.6F, 1.4F, 0.9F, 0.9F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(46, 53).addBox(0.8F, -0.4F, 3F, 1.2F, 1.2F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(54, 53).addBox(2.15F, -0.6F, 1.4F, 0.9F, 0.9F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(96, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.65F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(22, 30).addBox(-4.1F, -3.4F, -2.4F, 6.2F, 3.2F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(82, 19).addBox(-3.5F, -3.45F, -3F, 5F, 3.3F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c25", CubeListBuilder.create().texOffs(168, 39).addBox(-3.5F, -3.82F, -2.4F, 5F, 0.52F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c26", CubeListBuilder.create().texOffs(32, 39).addBox(-4.15F, -0.6F, -3.05F, 6.3F, 0.6F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c27", CubeListBuilder.create().texOffs(188, 39).addBox(-3.45F, 3F, -2.45F, 4.9F, 0.4F, 4.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c28", CubeListBuilder.create().texOffs(208, 39).addBox(-3.45F, 3.6F, -2.45F, 4.9F, 0.4F, 4.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c29", CubeListBuilder.create().texOffs(228, 39).addBox(-3.45F, 4.2F, -2.45F, 4.9F, 0.4F, 4.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c30", CubeListBuilder.create().texOffs(46, 30).addBox(-3.7F, 5.4F, -2.2F, 5.4F, 4F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c31", CubeListBuilder.create().texOffs(224, 0).addBox(-3.2F, 5.35F, -2.7F, 4.4F, 4.1F, 5.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c32", CubeListBuilder.create().texOffs(116, 47).addBox(-3.8F, 6.2F, -0.9F, 0.4F, 2.2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c33", CubeListBuilder.create().texOffs(60, 39).addBox(-3.4F, 9.4F, -2.4F, 4.8F, 2.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c34", CubeListBuilder.create().texOffs(112, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.65F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c35", CubeListBuilder.create().texOffs(68, 30).addBox(-2.1F, -3.4F, -2.4F, 6.2F, 3.2F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c36", CubeListBuilder.create().texOffs(104, 19).addBox(-1.5F, -3.45F, -3F, 5F, 3.3F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c37", CubeListBuilder.create().texOffs(0, 47).addBox(-1.5F, -3.82F, -2.4F, 5F, 0.52F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c38", CubeListBuilder.create().texOffs(80, 39).addBox(-2.15F, -0.6F, -3.05F, 6.3F, 0.6F, 6.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c39", CubeListBuilder.create().texOffs(20, 47).addBox(-1.45F, 3F, -2.45F, 4.9F, 0.4F, 4.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c40", CubeListBuilder.create().texOffs(40, 47).addBox(-1.45F, 3.6F, -2.45F, 4.9F, 0.4F, 4.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c41", CubeListBuilder.create().texOffs(60, 47).addBox(-1.45F, 4.2F, -2.45F, 4.9F, 0.4F, 4.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c42", CubeListBuilder.create().texOffs(92, 30).addBox(-1.7F, 5.4F, -2.2F, 5.4F, 4F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c43", CubeListBuilder.create().texOffs(0, 19).addBox(-1.2F, 5.35F, -2.7F, 4.4F, 4.1F, 5.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c44", CubeListBuilder.create().texOffs(122, 47).addBox(3.4F, 6.2F, -0.9F, 0.4F, 2.2F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c45", CubeListBuilder.create().texOffs(108, 39).addBox(-1.4F, 9.4F, -2.4F, 4.8F, 2.6F, 4.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(114, 30).addBox(-4F, 7F, -2F, 8F, 5F, 4F, new CubeDeformation(0.6F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(126, 19).addBox(-4.8F, 9.2F, -2.7F, 1F, 3.6F, 5.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(140, 19).addBox(3.8F, 9.2F, -2.7F, 1F, 3.6F, 5.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(128, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(128, 47).addBox(-2.4F, 0.4F, -2.95F, 4.8F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(140, 47).addBox(-2.5F, 4.2F, -3.4F, 5F, 2.4F, 1.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(60, 57).addBox(5F, -1.175F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.5708F));
                p[4].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(64, 57).addBox(3.4184F, -4.9934F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 2.3562F));
                p[4].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(68, 57).addBox(-0.4F, -6.575F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 3.1416F));
                p[4].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(72, 57).addBox(-4.2184F, -4.9934F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 3.927F));
                p[4].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(76, 57).addBox(-5.8F, -1.175F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 4.7124F));
                p[4].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(80, 57).addBox(-4.2184F, 2.6434F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 5.4978F));
                p[4].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(84, 57).addBox(-0.4F, 4.225F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 6.2832F));
                p[4].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(88, 57).addBox(3.4184F, 2.6434F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 7.0686F));
                p[4].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(92, 57).addBox(-0.4F, 5F, -3.95F, 0.8F, 0.8F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(96, 57).addBox(-2.35F, 7.2F, -2.9F, 4.7F, 0.8F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(128, 39).addBox(-2.95F, 1F, -0.9F, 0.6F, 5F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(134, 39).addBox(2.35F, 1F, -0.9F, 0.6F, 5F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(144, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(154, 47).addBox(-2.4F, 0.4F, -2.95F, 4.8F, 3.4F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(166, 47).addBox(-2.5F, 4.2F, -3.4F, 5F, 2.4F, 1.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(108, 57).addBox(5F, -1.175F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 1.5708F));
                p[5].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(112, 57).addBox(3.4184F, -4.9934F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 2.3562F));
                p[5].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(116, 57).addBox(-0.4F, -6.575F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 3.1416F));
                p[5].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(120, 57).addBox(-4.2184F, -4.9934F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 3.927F));
                p[5].addOrReplaceChild("c25", CubeListBuilder.create().texOffs(124, 57).addBox(-5.8F, -1.175F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 4.7124F));
                p[5].addOrReplaceChild("c26", CubeListBuilder.create().texOffs(128, 57).addBox(-4.2184F, 2.6434F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 5.4978F));
                p[5].addOrReplaceChild("c27", CubeListBuilder.create().texOffs(132, 57).addBox(-0.4F, 4.225F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 6.2832F));
                p[5].addOrReplaceChild("c28", CubeListBuilder.create().texOffs(136, 57).addBox(3.4184F, 2.6434F, -3.85F, 0.8F, 0.45F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 7.0686F));
                p[5].addOrReplaceChild("c29", CubeListBuilder.create().texOffs(140, 57).addBox(-0.4F, 5F, -3.95F, 0.8F, 0.8F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c30", CubeListBuilder.create().texOffs(144, 57).addBox(-2.35F, 7.2F, -2.9F, 4.7F, 0.8F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c31", CubeListBuilder.create().texOffs(140, 39).addBox(-2.95F, 1F, -0.9F, 0.6F, 5F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c32", CubeListBuilder.create().texOffs(146, 39).addBox(2.35F, 1F, -0.9F, 0.6F, 5F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(154, 19).addBox(-2.6F, 7.8F, -2.7F, 5.2F, 4.2F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(22, 19).addBox(-2F, 7.75F, -3.3F, 4F, 4.3F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(80, 47).addBox(-2F, 7.38F, -2.7F, 4F, 0.52F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(62, 53).addBox(-2.6F, 10.2F, -4F, 5.2F, 1.6F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(138, 30).addBox(-2.9F, 11.8F, -4.1F, 5.8F, 1.4F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(156, 57).addBox(-2.65F, 8.4F, -3.45F, 5.3F, 0.6F, 0.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(136, 53).addBox(-1.1F, 9.4F, 2.5F, 2.2F, 0.45F, 1.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(146, 53).addBox(-1.1F, 10.15F, 2.5F, 2.2F, 0.45F, 1.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(156, 53).addBox(-1.1F, 10.9F, 2.5F, 2.2F, 0.45F, 1.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(170, 57).addBox(-0.4F, 9F, 2.9F, 0.8F, 0.5F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(176, 19).addBox(-2.6F, 7.8F, -2.7F, 5.2F, 4.2F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(42, 19).addBox(-2F, 7.75F, -3.3F, 4F, 4.3F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(98, 47).addBox(-2F, 7.38F, -2.7F, 4F, 0.52F, 4.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(78, 53).addBox(-2.6F, 10.2F, -4F, 5.2F, 1.6F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(164, 30).addBox(-2.9F, 11.8F, -4.1F, 5.8F, 1.4F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(174, 57).addBox(-2.65F, 8.4F, -3.45F, 5.3F, 0.6F, 0.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(166, 53).addBox(-1.1F, 9.4F, 2.5F, 2.2F, 0.45F, 1.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(176, 53).addBox(-1.1F, 10.15F, 2.5F, 2.2F, 0.45F, 1.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(186, 53).addBox(-1.1F, 10.9F, 2.5F, 2.2F, 0.45F, 1.1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(188, 57).addBox(-0.4F, 9F, 2.9F, 0.8F, 0.5F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
    }

    private static void a_hydrogen_jetpack(PartDefinition[] p, int slot) {
        switch (slot) {
            case 0 -> {
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(62, 0).addBox(-2.2F, -0.6F, -2.7F, 1.6F, 9.6F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(88, 0).addBox(-2.2F, -0.7F, -2.4F, 1.6F, 0.8F, 5.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(68, 0).addBox(0.6F, -0.6F, -2.7F, 1.6F, 9.6F, 0.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(104, 0).addBox(0.6F, -0.7F, -2.4F, 1.6F, 0.8F, 5.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(232, 0).addBox(-3.6F, 5.6F, -2.85F, 7.2F, 1.2F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(250, 0).addBox(-0.9F, 5.4F, -3.15F, 1.8F, 1.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(74, 0).addBox(-2.2F, 0.6F, 3.05F, 4.4F, 8.6F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(48, 0).addBox(-1.75F, 0.55F, 2.6F, 3.5F, 8.7F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(0, 15).addBox(-1.75F, 0.285F, 3.05F, 3.5F, 0.415F, 1.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(58, 15).addBox(-1.4F, 1.6F, 5F, 2.8F, 1F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(152, 0).addBox(-1.2F, 3.2F, 5F, 2.4F, 2.4F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(12, 15).addBox(-0.2F, 3.5F, 5.3F, 0.4F, 1.4F, 0.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(16, 15).addBox(-1.6F, 6.4F, 5F, 3.2F, 1.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(24, 0).addBox(-5F, -0.2F, 3F, 3.2F, 10.2F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(0, 0).addBox(-4.4F, -0.25F, 2.4F, 2F, 10.3F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(26, 15).addBox(-4.4F, -0.62F, 3F, 2F, 0.52F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(120, 0).addBox(-5.05F, 5.6F, 2.35F, 3.3F, 1.2F, 3.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(160, 0).addBox(-4.1F, -1.2F, 3.3F, 1.4F, 1.1F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(168, 0).addBox(-3.65F, -1.6F, 2.7F, 0.5F, 0.4F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(176, 0).addBox(-4.5F, 9.9F, 2.9F, 2.2F, 0.8F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(188, 0).addBox(-4.3F, 10.7F, 3.1F, 1.8F, 1.4F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(34, 15).addBox(-4F, 12.1F, 3.4F, 1.2F, 0.3F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(36, 0).addBox(1.8F, -0.2F, 3F, 3.2F, 10.2F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c23", CubeListBuilder.create().texOffs(12, 0).addBox(2.4F, -0.25F, 2.4F, 2F, 10.3F, 3.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c24", CubeListBuilder.create().texOffs(42, 15).addBox(2.4F, -0.62F, 3F, 2F, 0.52F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c25", CubeListBuilder.create().texOffs(136, 0).addBox(1.75F, 5.6F, 2.35F, 3.3F, 1.2F, 3.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c26", CubeListBuilder.create().texOffs(196, 0).addBox(2.7F, -1.2F, 3.3F, 1.4F, 1.1F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c27", CubeListBuilder.create().texOffs(204, 0).addBox(3.15F, -1.6F, 2.7F, 0.5F, 0.4F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c28", CubeListBuilder.create().texOffs(212, 0).addBox(2.3F, 9.9F, 2.9F, 2.2F, 0.8F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c29", CubeListBuilder.create().texOffs(224, 0).addBox(2.5F, 10.7F, 3.1F, 1.8F, 1.4F, 1.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c30", CubeListBuilder.create().texOffs(50, 15).addBox(2.8F, 12.1F, 3.4F, 1.2F, 0.3F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
            }
            case 3 -> {
            }
            default -> {
            }
        }
    }
}
