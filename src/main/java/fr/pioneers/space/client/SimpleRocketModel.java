package fr.pioneers.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.space.RocketEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.NotNull;

/**
 * Maquette statique d'une fusee (tiers 1 a 13, voir {@link GeneratedRocketModels}).
 */
public class SimpleRocketModel extends EntityModel<RocketEntity> implements StagedRocketModel {

    private final ModelPart root;

    public SimpleRocketModel(ModelPart root) {
        this.root = root;
    }

    @Override
    public void setupAnim(@NotNull RocketEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(@NotNull PoseStack poseStack, @NotNull VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green,
          float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public ModelPart root() {
        return root;
    }
}
