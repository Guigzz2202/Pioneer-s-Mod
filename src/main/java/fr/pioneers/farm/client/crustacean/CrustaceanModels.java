package fr.pioneers.farm.client.crustacean;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERE par tools/gc/farm2/crustacean_java.py depuis crustaceans.py : silhouettes des crustaces (patron 64x64) et pieces animees
 * (chemin, role = indice de crustaceans.ROLES, cote +1 gauche / -1 droite, rang). Ne pas modifier a la main.
 */
public final class CrustaceanModels {

    private CrustaceanModels() {
    }

    public static final String[] ROLES = {"body", "claw", "leg", "tail", "antenna", "eye", "fan", "shell"};

    /** Par silhouette : chemins des pieces animees. */
    public static final String[][] PATHS = {
        {"claw_l", "claw_r", "leg_l0", "leg_r0", "leg_l1", "leg_r1", "leg_l2", "leg_r2"},
        {"claw_l", "claw_r", "leg_l0", "leg_r0", "leg_l1", "leg_r1", "leg_l2", "leg_r2"},
        {"claw_r", "claw_l", "leg_l0", "leg_r0", "leg_l1", "leg_r1"},
        {"tail1", "tail1/tail2", "tail1/tail2/tail3", "tail1/tail2/tail3/fan", "arm_l", "arm_r", "antenna_l", "antenna_r", "leg_l0", "leg_r0", "leg_l1", "leg_r1", "leg_l2", "leg_r2", "leg_l3", "leg_r3"},
        {"tail1", "tail1/tail2", "tail1/tail2/fan", "antenna_l", "antenna_r", "leg_l0", "leg_r0", "leg_l1", "leg_r1", "leg_l2", "leg_r2"}
    };

    /** Par silhouette : role, cote, rang de chaque piece animee (3 entiers par piece). */
    public static final int[][] ANIM = {
        {1, 1, 0, 1, -1, 0, 2, 1, 0, 2, -1, 0, 2, 1, 1, 2, -1, 1, 2, 1, 2, 2, -1, 2},
        {1, 1, 0, 1, -1, 0, 2, 1, 0, 2, -1, 0, 2, 1, 1, 2, -1, 1, 2, 1, 2, 2, -1, 2},
        {1, -1, 0, 1, 1, 0, 2, 1, 0, 2, -1, 0, 2, 1, 1, 2, -1, 1},
        {3, 0, 0, 3, 0, 1, 3, 0, 2, 6, 0, 0, 1, 1, 0, 1, -1, 0, 4, 1, 0, 4, -1, 0, 2, 1, 0, 2, -1, 0, 2, 1, 1, 2, -1, 1, 2, 1, 2, 2, -1, 2, 2, 1, 3, 2, -1, 3},
        {3, 0, 0, 3, 0, 1, 6, 0, 0, 4, 1, 0, 4, -1, 0, 2, 1, 0, 2, -1, 0, 2, 1, 1, 2, -1, 1, 2, 1, 2, 2, -1, 2}
    };

    public static LayerDefinition create(int shape) {
        return switch (shape) {
            case 0 -> crab();
            case 1 -> king();
            case 2 -> hermit();
            case 3 -> lobster();
            case 4 -> shrimp();
            default -> crab();
        };
    }

    private static LayerDefinition crab() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_carapace = root.addOrReplaceChild("carapace", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -2.0F, -3.0F, 8.0F, 2.0F, 6.0F),
              PartPose.offsetAndRotation(0.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_dome = root.addOrReplaceChild("dome", CubeListBuilder.create().texOffs(28, 0).addBox(-3.0F, -3.0F, -2.0F, 6.0F, 1.0F, 4.0F),
              PartPose.offsetAndRotation(0.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_eye_l = root.addOrReplaceChild("eye_l", CubeListBuilder.create().texOffs(12, 8).addBox(1.0F, -4.0F, -3.0F, 1.0F, 2.0F, 1.0F),
              PartPose.offsetAndRotation(0.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_eye_r = root.addOrReplaceChild("eye_r", CubeListBuilder.create().texOffs(16, 8).addBox(-2.0F, -4.0F, -3.0F, 1.0F, 2.0F, 1.0F),
              PartPose.offsetAndRotation(0.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_claw_l = root.addOrReplaceChild("claw_l", CubeListBuilder.create().texOffs(48, 0).addBox(0.0F, -1.0F, -3.0F, 3.0F, 2.0F, 3.0F),
              PartPose.offsetAndRotation(3.0F, 21.5F, -3.0F, 0.0F, 0.45F, 0.0F));
        PartDefinition p_claw_r = root.addOrReplaceChild("claw_r", CubeListBuilder.create().texOffs(0, 8).addBox(-3.0F, -1.0F, -3.0F, 3.0F, 2.0F, 3.0F),
              PartPose.offsetAndRotation(-3.0F, 21.5F, -3.0F, 0.0F, -0.45F, 0.0F));
        PartDefinition p_leg_l0 = root.addOrReplaceChild("leg_l0", CubeListBuilder.create().texOffs(20, 8).addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(3.5F, 21.0F, -2.0F, 0.0F, -0.35F, 0.55F));
        PartDefinition p_leg_r0 = root.addOrReplaceChild("leg_r0", CubeListBuilder.create().texOffs(30, 8).addBox(-4.0F, 0.0F, 0.0F, 4.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-3.5F, 21.0F, -2.0F, 0.0F, 0.35F, -0.55F));
        PartDefinition p_leg_l1 = root.addOrReplaceChild("leg_l1", CubeListBuilder.create().texOffs(40, 8).addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(3.5F, 21.0F, 0.0F, 0.0F, 0.0F, 0.55F));
        PartDefinition p_leg_r1 = root.addOrReplaceChild("leg_r1", CubeListBuilder.create().texOffs(50, 8).addBox(-4.0F, 0.0F, 0.0F, 4.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-3.5F, 21.0F, 0.0F, 0.0F, -0.0F, -0.55F));
        PartDefinition p_leg_l2 = root.addOrReplaceChild("leg_l2", CubeListBuilder.create().texOffs(0, 13).addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(3.5F, 21.0F, 2.0F, 0.0F, 0.35F, 0.55F));
        PartDefinition p_leg_r2 = root.addOrReplaceChild("leg_r2", CubeListBuilder.create().texOffs(10, 13).addBox(-4.0F, 0.0F, 0.0F, 4.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-3.5F, 21.0F, 2.0F, 0.0F, -0.35F, -0.55F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition king() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_carapace = root.addOrReplaceChild("carapace", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 2.0F, 6.0F),
              PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_dome = root.addOrReplaceChild("dome", CubeListBuilder.create().texOffs(24, 0).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 1.0F, 4.0F),
              PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_spikes = root.addOrReplaceChild("spikes", CubeListBuilder.create().texOffs(0, 8).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_eye_l = root.addOrReplaceChild("eye_l", CubeListBuilder.create().texOffs(14, 8).addBox(1.0F, -4.0F, -3.0F, 1.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_eye_r = root.addOrReplaceChild("eye_r", CubeListBuilder.create().texOffs(18, 8).addBox(-2.0F, -4.0F, -3.0F, 1.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_claw_l = root.addOrReplaceChild("claw_l", CubeListBuilder.create().texOffs(40, 0).addBox(0.0F, -1.0F, -3.0F, 2.0F, 2.0F, 3.0F),
              PartPose.offsetAndRotation(2.0F, 20.5F, -3.0F, 0.0F, 0.4F, 0.0F));
        PartDefinition p_claw_r = root.addOrReplaceChild("claw_r", CubeListBuilder.create().texOffs(50, 0).addBox(-2.0F, -1.0F, -3.0F, 2.0F, 2.0F, 3.0F),
              PartPose.offsetAndRotation(-2.0F, 20.5F, -3.0F, 0.0F, -0.4F, 0.0F));
        PartDefinition p_leg_l0 = root.addOrReplaceChild("leg_l0", CubeListBuilder.create().texOffs(22, 8).addBox(0.0F, 0.0F, 0.0F, 7.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(2.5F, 19.6F, -2.0F, 0.0F, -0.45F, 0.62F));
        PartDefinition p_leg_r0 = root.addOrReplaceChild("leg_r0", CubeListBuilder.create().texOffs(38, 8).addBox(-7.0F, 0.0F, 0.0F, 7.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-2.5F, 19.6F, -2.0F, 0.0F, 0.45F, -0.62F));
        PartDefinition p_leg_l1 = root.addOrReplaceChild("leg_l1", CubeListBuilder.create().texOffs(0, 10).addBox(0.0F, 0.0F, 0.0F, 7.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(2.5F, 19.6F, 0.0F, 0.0F, 0.0F, 0.62F));
        PartDefinition p_leg_r1 = root.addOrReplaceChild("leg_r1", CubeListBuilder.create().texOffs(16, 10).addBox(-7.0F, 0.0F, 0.0F, 7.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-2.5F, 19.6F, 0.0F, 0.0F, -0.0F, -0.62F));
        PartDefinition p_leg_l2 = root.addOrReplaceChild("leg_l2", CubeListBuilder.create().texOffs(32, 10).addBox(0.0F, 0.0F, 0.0F, 7.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(2.5F, 19.6F, 2.0F, 0.0F, 0.45F, 0.62F));
        PartDefinition p_leg_r2 = root.addOrReplaceChild("leg_r2", CubeListBuilder.create().texOffs(48, 10).addBox(-7.0F, 0.0F, 0.0F, 7.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-2.5F, 19.6F, 2.0F, 0.0F, -0.45F, -0.62F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition hermit() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_shell = root.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -5.0F, -1.0F, 4.0F, 4.0F, 5.0F),
              PartPose.offsetAndRotation(0.0F, 22.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_coil = root.addOrReplaceChild("coil", CubeListBuilder.create().texOffs(32, 0).addBox(-1.0F, -6.0F, 0.0F, 2.0F, 1.0F, 3.0F),
              PartPose.offsetAndRotation(0.0F, 22.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(18, 0).addBox(-2.0F, -1.0F, -3.0F, 4.0F, 2.0F, 3.0F),
              PartPose.offsetAndRotation(0.0F, 22.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_eye_l = root.addOrReplaceChild("eye_l", CubeListBuilder.create().texOffs(50, 0).addBox(0.0F, -3.0F, -3.0F, 1.0F, 2.0F, 1.0F),
              PartPose.offsetAndRotation(0.0F, 22.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_eye_r = root.addOrReplaceChild("eye_r", CubeListBuilder.create().texOffs(54, 0).addBox(-1.0F, -3.0F, -3.0F, 1.0F, 2.0F, 1.0F),
              PartPose.offsetAndRotation(0.0F, 22.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_claw_r = root.addOrReplaceChild("claw_r", CubeListBuilder.create().texOffs(42, 0).addBox(-2.0F, -1.0F, -2.0F, 2.0F, 2.0F, 2.0F),
              PartPose.offsetAndRotation(-2.0F, 22.5F, -3.0F, 0.0F, -0.3F, 0.0F));
        PartDefinition p_claw_l = root.addOrReplaceChild("claw_l", CubeListBuilder.create().texOffs(58, 0).addBox(0.0F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F),
              PartPose.offsetAndRotation(2.0F, 22.5F, -3.0F, 0.0F, 0.3F, 0.0F));
        PartDefinition p_leg_l0 = root.addOrReplaceChild("leg_l0", CubeListBuilder.create().texOffs(0, 9).addBox(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(1.5F, 22.0F, -2.0F, 0.0F, -0.4F, 0.4F));
        PartDefinition p_leg_r0 = root.addOrReplaceChild("leg_r0", CubeListBuilder.create().texOffs(8, 9).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-1.5F, 22.0F, -2.0F, 0.0F, 0.4F, -0.4F));
        PartDefinition p_leg_l1 = root.addOrReplaceChild("leg_l1", CubeListBuilder.create().texOffs(16, 9).addBox(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(1.5F, 22.0F, -1.0F, 0.0F, 0.2F, 0.4F));
        PartDefinition p_leg_r1 = root.addOrReplaceChild("leg_r1", CubeListBuilder.create().texOffs(24, 9).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-1.5F, 22.0F, -1.0F, 0.0F, -0.2F, -0.4F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition lobster() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_thorax = root.addOrReplaceChild("thorax", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -2.0F, -4.0F, 4.0F, 3.0F, 6.0F),
              PartPose.offsetAndRotation(0.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_rostrum = root.addOrReplaceChild("rostrum", CubeListBuilder.create().texOffs(54, 9).addBox(-1.0F, -2.0F, -6.0F, 2.0F, 1.0F, 2.0F),
              PartPose.offsetAndRotation(0.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_eye_l = root.addOrReplaceChild("eye_l", CubeListBuilder.create().texOffs(38, 15).addBox(1.0F, -3.0F, -4.0F, 1.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(0.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_eye_r = root.addOrReplaceChild("eye_r", CubeListBuilder.create().texOffs(42, 15).addBox(-2.0F, -3.0F, -4.0F, 1.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(0.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tail1 = root.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(24, 9).addBox(-2.0F, -1.0F, 0.0F, 4.0F, 2.0F, 3.0F),
              PartPose.offsetAndRotation(0.0F, 20.5F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tail2 = p_tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(0, 15).addBox(-2.0F, -1.0F, 0.0F, 4.0F, 1.0F, 2.0F),
              PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tail3 = p_tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(12, 15).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 1.0F, 2.0F),
              PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_fan = p_tail3.addOrReplaceChild("fan", CubeListBuilder.create().texOffs(20, 15).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 0.0F, 3.0F),
              PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_l = root.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(38, 9).addBox(0.0F, 0.0F, -3.0F, 1.0F, 1.0F, 3.0F),
              PartPose.offsetAndRotation(2.0F, 21.0F, -3.0F, 0.0F, 0.3F, 0.0F));
        PartDefinition p_pincer_l = p_arm_l.addOrReplaceChild("pincer_l", CubeListBuilder.create().texOffs(0, 9).addBox(0.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F),
              PartPose.offsetAndRotation(0.0F, 0.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_r = root.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(46, 9).addBox(-1.0F, 0.0F, -3.0F, 1.0F, 1.0F, 3.0F),
              PartPose.offsetAndRotation(-2.0F, 21.0F, -3.0F, 0.0F, -0.3F, 0.0F));
        PartDefinition p_pincer_r = p_arm_r.addOrReplaceChild("pincer_r", CubeListBuilder.create().texOffs(12, 9).addBox(-2.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F),
              PartPose.offsetAndRotation(0.0F, 0.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_antenna_l = root.addOrReplaceChild("antenna_l", CubeListBuilder.create().texOffs(20, 0).addBox(0.0F, 0.0F, -9.0F, 1.0F, 0.0F, 9.0F),
              PartPose.offsetAndRotation(1.0F, 19.5F, -5.0F, -0.3F, -0.35F, 0.0F));
        PartDefinition p_antenna_r = root.addOrReplaceChild("antenna_r", CubeListBuilder.create().texOffs(40, 0).addBox(-1.0F, 0.0F, -9.0F, 1.0F, 0.0F, 9.0F),
              PartPose.offsetAndRotation(-1.0F, 19.5F, -5.0F, -0.3F, 0.35F, 0.0F));
        PartDefinition p_leg_l0 = root.addOrReplaceChild("leg_l0", CubeListBuilder.create().texOffs(46, 15).addBox(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(2.0F, 21.5F, -2.0F, 0.0F, -0.45F, 0.6F));
        PartDefinition p_leg_r0 = root.addOrReplaceChild("leg_r0", CubeListBuilder.create().texOffs(54, 15).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-2.0F, 21.5F, -2.0F, 0.0F, 0.45F, -0.6F));
        PartDefinition p_leg_l1 = root.addOrReplaceChild("leg_l1", CubeListBuilder.create().texOffs(0, 18).addBox(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(2.0F, 21.5F, -1.0F, 0.0F, -0.15F, 0.6F));
        PartDefinition p_leg_r1 = root.addOrReplaceChild("leg_r1", CubeListBuilder.create().texOffs(8, 18).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-2.0F, 21.5F, -1.0F, 0.0F, 0.15F, -0.6F));
        PartDefinition p_leg_l2 = root.addOrReplaceChild("leg_l2", CubeListBuilder.create().texOffs(16, 18).addBox(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(2.0F, 21.5F, 0.0F, 0.0F, 0.15F, 0.6F));
        PartDefinition p_leg_r2 = root.addOrReplaceChild("leg_r2", CubeListBuilder.create().texOffs(24, 18).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-2.0F, 21.5F, 0.0F, 0.0F, -0.15F, -0.6F));
        PartDefinition p_leg_l3 = root.addOrReplaceChild("leg_l3", CubeListBuilder.create().texOffs(32, 18).addBox(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(2.0F, 21.5F, 1.0F, 0.0F, 0.45F, 0.6F));
        PartDefinition p_leg_r3 = root.addOrReplaceChild("leg_r3", CubeListBuilder.create().texOffs(40, 18).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-2.0F, 21.5F, 1.0F, 0.0F, -0.45F, -0.6F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition shrimp() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(44, 0).addBox(-1.0F, -2.0F, -3.0F, 2.0F, 2.0F, 4.0F),
              PartPose.offsetAndRotation(0.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_rostrum = root.addOrReplaceChild("rostrum", CubeListBuilder.create().texOffs(10, 10).addBox(0.0F, -2.0F, -6.0F, 1.0F, 1.0F, 3.0F),
              PartPose.offsetAndRotation(-0.5F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_eye_l = root.addOrReplaceChild("eye_l", CubeListBuilder.create().texOffs(26, 10).addBox(1.0F, -2.0F, -3.0F, 1.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(0.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_eye_r = root.addOrReplaceChild("eye_r", CubeListBuilder.create().texOffs(30, 10).addBox(-2.0F, -2.0F, -3.0F, 1.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(0.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tail1 = root.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(0, 10).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 3.0F),
              PartPose.offsetAndRotation(0.0F, 20.0F, 1.0F, 0.3F, 0.0F, 0.0F));
        PartDefinition p_tail2 = p_tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(18, 10).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 1.0F, 2.0F),
              PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.4F, 0.0F, 0.0F));
        PartDefinition p_fan = p_tail2.addOrReplaceChild("fan", CubeListBuilder.create().texOffs(34, 10).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 2.0F),
              PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_antenna_l = root.addOrReplaceChild("antenna_l", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, -10.0F, 1.0F, 0.0F, 10.0F),
              PartPose.offsetAndRotation(0.5F, 19.5F, -3.0F, -0.25F, -0.25F, 0.0F));
        PartDefinition p_antenna_r = root.addOrReplaceChild("antenna_r", CubeListBuilder.create().texOffs(22, 0).addBox(-1.0F, 0.0F, -10.0F, 1.0F, 0.0F, 10.0F),
              PartPose.offsetAndRotation(-0.5F, 19.5F, -3.0F, -0.25F, 0.25F, 0.0F));
        PartDefinition p_leg_l0 = root.addOrReplaceChild("leg_l0", CubeListBuilder.create().texOffs(46, 10).addBox(0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(0.5F, 21.0F, -2.0F, 0.0F, -0.3F, 1.0F));
        PartDefinition p_leg_r0 = root.addOrReplaceChild("leg_r0", CubeListBuilder.create().texOffs(52, 10).addBox(-2.0F, 0.0F, 0.0F, 2.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-0.5F, 21.0F, -2.0F, 0.0F, 0.3F, -1.0F));
        PartDefinition p_leg_l1 = root.addOrReplaceChild("leg_l1", CubeListBuilder.create().texOffs(58, 10).addBox(0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(0.5F, 21.0F, -1.0F, 0.0F, 0.0F, 1.0F));
        PartDefinition p_leg_r1 = root.addOrReplaceChild("leg_r1", CubeListBuilder.create().texOffs(0, 15).addBox(-2.0F, 0.0F, 0.0F, 2.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-0.5F, 21.0F, -1.0F, 0.0F, -0.0F, -1.0F));
        PartDefinition p_leg_l2 = root.addOrReplaceChild("leg_l2", CubeListBuilder.create().texOffs(6, 15).addBox(0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(0.5F, 21.0F, 0.0F, 0.0F, 0.3F, 1.0F));
        PartDefinition p_leg_r2 = root.addOrReplaceChild("leg_r2", CubeListBuilder.create().texOffs(12, 15).addBox(-2.0F, 0.0F, 0.0F, 2.0F, 1.0F, 1.0F),
              PartPose.offsetAndRotation(-0.5F, 21.0F, 0.0F, 0.0F, -0.3F, -1.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
