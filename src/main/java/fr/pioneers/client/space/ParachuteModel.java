package fr.pioneers.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.space.RocketEntity;
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
 * Parachute d'arrivee : voilure en dome (4 etages) reliee au nez de la fusee par 4 suspentes.
 * Origine du modele = pointe de la fusee ; y vers le bas.
 */
public class ParachuteModel extends EntityModel<RocketEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Pioneers.rl("rocket_parachute"), "main");
    /** Hauteur de la voilure au-dessus de la pointe, en pixels. */
    public static final float LINE_HEIGHT = 40;
    private static final float SPREAD = 22;

    private final ModelPart root;

    public ParachuteModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float base = -LINE_HEIGHT;
        root.addOrReplaceChild("canopy1", CubeListBuilder.create().texOffs(0, 0).addBox(-26, base - 2, -26, 52, 2, 52), PartPose.ZERO);
        root.addOrReplaceChild("canopy2", CubeListBuilder.create().texOffs(0, 54).addBox(-22, base - 5, -22, 44, 3, 44), PartPose.ZERO);
        root.addOrReplaceChild("canopy3", CubeListBuilder.create().texOffs(0, 101).addBox(-16, base - 8, -16, 32, 3, 32), PartPose.ZERO);
        root.addOrReplaceChild("canopy4", CubeListBuilder.create().texOffs(128, 101).addBox(-8, base - 10, -8, 16, 2, 16), PartPose.ZERO);
        //Suspentes : de la pointe vers le bord de la voilure
        float length = (float) Math.sqrt(SPREAD * SPREAD + LINE_HEIGHT * LINE_HEIGHT);
        float angle = (float) Math.atan2(SPREAD, LINE_HEIGHT);
        for (int i = 0; i < 4; i++) {
            float yaw = i * (float) Math.PI / 2;
            PartDefinition pivot = root.addOrReplaceChild("pivot" + i, CubeListBuilder.create(), PartPose.rotation(0, yaw, 0));
            pivot.addOrReplaceChild("line" + i, CubeListBuilder.create().texOffs(192, 101).addBox(-0.5F, -length, -0.5F, 1, length, 1, new CubeDeformation(-0.3F)),
                  PartPose.rotation(angle, 0, 0));
        }
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public void setupAnim(RocketEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
