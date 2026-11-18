package fr.pioneers.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.space.CapsuleEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Capsule de descente : bouclier thermique, corps tronconique octogonal, hublot et attache du parachute.
 * 26 px de haut, y = 24 au sol.
 */
public class CapsuleModel extends EntityModel<CapsuleEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Pioneers.rl("capsule"), "main");
    public static final int HEIGHT = 26;
    private static final float QUARTER = (float) (Math.PI / 4);

    private final ModelPart root;

    public CapsuleModel(ModelPart root) {
        this.root = root;
    }

    private static void layer(PartDefinition root, String name, int u, int v, int size, int bottom, int top) {
        float half = size / 2F;
        root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(-half, 24 - top, -half, size, top - bottom, size), PartPose.ZERO);
        root.addOrReplaceChild(name + "_45", CubeListBuilder.create().texOffs(u, v).addBox(-half, -top, -half, size, top - bottom, size, new CubeDeformation(-0.01F)),
              PartPose.offsetAndRotation(0, 24, 0, 0, QUARTER, 0));
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        layer(root, "shield", 0, 0, 22, 0, 3);
        layer(root, "hull1", 0, 25, 20, 3, 10);
        layer(root, "hull2", 0, 52, 17, 10, 17);
        layer(root, "hull3", 0, 76, 13, 17, 22);
        layer(root, "top", 64, 76, 8, 22, 26);
        root.addOrReplaceChild("window", CubeListBuilder.create().texOffs(88, 0).addBox(-2.5F, 24 - 15, -9.2F, 5, 5, 1), PartPose.ZERO);
        root.addOrReplaceChild("hatch", CubeListBuilder.create().texOffs(88, 8).addBox(-3, 24 - 9, 9.2F, 6, 5, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(CapsuleEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
