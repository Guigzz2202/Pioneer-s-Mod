package fr.pioneers.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;

/**
 * Rover minier de l'avant-poste : le rover robotique (memes textures par tier) avec un bras de forage a l'avant. Maquette generee par
 * units.py (couche {@code RobotUnitModels.ROVER_DRILL}) ; avant vers -z, sol a y = 24.
 */
public class RoverDrillModel extends Model {

    private final ModelPart root;
    private final ModelPart[] wheels = new ModelPart[6];
    private final ModelPart drill, bit, cargo;

    public RoverDrillModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        for (int i = 0; i < 6; i++) {
            wheels[i] = root.getChild("wheel" + i);
        }
        ModelPart chassis = root.getChild("chassis");
        drill = chassis.getChild("drill");
        bit = drill.getChild("bit");
        cargo = chassis.getChild("cargo");
    }

    /**
     * @param wheelSpin rotation des roues (radians)
     * @param armDown   0 = bras releve, 1 = foret au sol
     * @param drillSpin rotation du foret (radians)
     * @param cargoFill remplissage de la benne (0..1)
     */
    public void setPose(float wheelSpin, float armDown, float drillSpin, float cargoFill) {
        for (ModelPart wheel : wheels) {
            wheel.xRot = wheelSpin;
        }
        drill.xRot = 0.35F - armDown * 0.95F;
        bit.zRot = drillSpin;
        cargo.visible = cargoFill > 0.01F;
        cargo.y = cargo.getInitialPose().y + 2 * (1 - Math.min(1, cargoFill));
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        boolean shown = cargo.visible;
        cargo.visible = false;
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        cargo.visible = shown;
    }

    /** Le chargement de la benne, teinte a la couleur du minerai transporte. */
    public void renderCargo(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue) {
        if (!cargo.visible) {
            return;
        }
        //Seule la benne : on rend le chemin chassis -> benne
        poseStack.pushPose();
        root.getChild("chassis").translateAndRotate(poseStack);
        cargo.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, 1);
        poseStack.popPose();
    }
}
