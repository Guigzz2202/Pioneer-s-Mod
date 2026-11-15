package fr.pioneers.client.robotics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.robotics.RobotUnitEntity;
import fr.pioneers.robotics.UnitKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Animation des unites : marche et bras-outil du robot, rotors et inclinaison du drone, roues, suspension et grue du rover.
 */
public class RobotUnitModel extends EntityModel<RobotUnitEntity> {

    private final UnitKind kind;
    private final ModelPart root;
    private final List<ModelPart> all = new ArrayList<>();

    public RobotUnitModel(ModelPart root, UnitKind kind) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.kind = kind;
        root.getAllParts().forEach(all::add);
    }

    public UnitKind kind() {
        return kind;
    }

    @Nullable
    private ModelPart part(String path) {
        ModelPart part = root;
        for (String name : path.split("/")) {
            if (!part.hasChild(name)) {
                return null;
            }
            part = part.getChild(name);
        }
        return part;
    }

    private void reset() {
        for (ModelPart part : all) {
            part.resetPose();
        }
    }

    @Override
    public void setupAnim(@NotNull RobotUnitEntity unit, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        Vec3 motion = unit.getDeltaMovement();
        animate(age, limbSwing, limbSwingAmount, headYaw, headPitch, unit.isWorking(), motion.horizontalDistance(), unit.getCargoFill());
        if (unit.isSleeping()) {
            sleep(age);
        }
    }

    /** Mode repos (tortue) : rentree dans sa carapace, posee au sol, tete rentree, respiration lente. */
    private void sleep(float age) {
        for (int i = 0; i < 4; i++) {
            ModelPart leg = part("leg" + i);
            if (leg != null) {
                leg.xRot = 0;
                leg.y -= 2.5F;
            }
        }
        ModelPart body = part("body");
        if (body != null) {
            body.y += 2.2F + Mth.sin(age * 0.04F) * 0.12F;
        }
        ModelPart head = part("body/head");
        if (head != null) {
            head.z += 1.8F;
            head.y += 0.6F;
            head.xRot = 0.25F;
            head.yRot = 0;
        }
        ModelPart tool = part("body/tool");
        if (tool != null) {
            tool.xRot = -0.3F;
        }
    }

    /** Pose animee (aussi pour l'objet et les unites garees, sans entite). */
    public void animate(float age, float swing, float amount, float headYaw, float headPitch, boolean working, double speed, float fill) {
        reset();
        switch (kind) {
            case ROBOT -> robot(age, swing, amount, headYaw, headPitch, working);
            case DRONE -> drone(age, working, speed);
            case ROVER -> rover(age, swing, amount, working);
            case TURTLE -> turtle(age, swing, amount, headYaw, working);
        }
    }

    private void robot(float age, float swing, float amount, float headYaw, float headPitch, boolean working) {
        ModelPart body = part("body"), head = part("body/head"), rightArm = part("body/right_arm"), leftArm = part("body/left_arm");
        ModelPart rightLeg = part("right_leg"), leftLeg = part("left_leg");
        float walk = Mth.cos(swing * 0.6662F) * 1.1F * amount;
        if (rightLeg != null && leftLeg != null) {
            rightLeg.xRot = walk;
            leftLeg.xRot = -walk;
        }
        if (body != null) {
            //Leger balancement a la marche, respiration au repos
            body.y += Math.abs(Mth.sin(swing * 0.6662F)) * amount * 0.8F + Mth.sin(age * 0.08F) * 0.15F;
            body.zRot = Mth.cos(swing * 0.6662F) * 0.05F * amount;
        }
        if (head != null) {
            head.yRot = headYaw * Mth.DEG_TO_RAD;
            head.xRot = headPitch * Mth.DEG_TO_RAD;
            if (!working && amount < 0.1F) {
                //Balayage du regard a l'arret
                head.yRot += Mth.sin(age * 0.05F) * 0.35F;
            }
        }
        if (rightArm != null && leftArm != null) {
            rightArm.xRot = -walk * 0.8F;
            leftArm.xRot = walk * 0.8F;
            if (working) {
                //Geste de travail : coups reguliers du bras droit, le gauche stabilise
                rightArm.xRot = -1.9F + Mth.sin(age * 0.55F) * 0.65F;
                rightArm.zRot = 0.1F;
                leftArm.xRot = -0.6F;
                leftArm.zRot = -0.25F;
            }
        }
    }

    private void drone(float age, boolean working, double speed) {
        ModelPart body = part("body");
        for (int i = 0; i < 4; i++) {
            ModelPart rotor = part("body/rotor" + i);
            if (rotor != null) {
                rotor.yRot = age * (i % 2 == 0 ? 2.1F : -2.1F);
            }
        }
        if (body != null) {
            body.y -= Mth.sin(age * 0.18F) * 0.6F;
            //Le drone pique du nez quand il avance
            body.xRot = (float) Mth.clamp(speed * 2.2, 0, 0.45);
            body.zRot = Mth.sin(age * 0.11F) * 0.04F;
        }
        ModelPart claw = part("body/claw");
        if (claw != null && working) {
            claw.y += 1 + Mth.sin(age * 0.5F) * 0.5F;
        }
    }

    private void rover(float age, float swing, float amount, boolean working) {
        for (int i = 0; i < 6; i++) {
            ModelPart wheel = part("wheel" + i);
            if (wheel != null) {
                wheel.xRot = swing * 0.9F;
            }
        }
        ModelPart chassis = part("chassis");
        if (chassis != null) {
            chassis.y += Mth.sin(swing * 1.7F) * 0.35F * amount + Mth.sin(age * 0.06F) * 0.05F;
            chassis.xRot = Mth.sin(swing * 0.9F) * 0.015F * amount;
        }
        ModelPart antenna = part("chassis/cab/antenna");
        if (antenna != null) {
            antenna.zRot = Mth.sin(age * 0.4F) * 0.12F * (0.3F + amount);
            antenna.xRot = -0.15F * amount;
        }
        ModelPart boom = part("chassis/boom");
        if (boom != null) {
            if (working) {
                boom.yRot = Mth.PI + Mth.sin(age * 0.12F) * 0.5F;
                boom.xRot = -0.25F + Mth.sin(age * 0.5F) * 0.12F;
            } else {
                boom.yRot = 0;
            }
        }
    }

    /** Tortue : pattes qui alternent lentement, tete qui regarde, bras planteur qui pique au travail. */
    private void turtle(float age, float swing, float amount, float headYaw, boolean working) {
        float walk = Mth.cos(swing * 0.45F) * 0.6F * amount;
        for (int i = 0; i < 4; i++) {
            ModelPart leg = part("leg" + i);
            if (leg != null) {
                leg.xRot = (i == 0 || i == 3) ? walk : -walk;
            }
        }
        ModelPart body = part("body");
        if (body != null) {
            body.y += Mth.sin(swing * 0.9F) * 0.25F * amount + Mth.sin(age * 0.05F) * 0.08F;
        }
        ModelPart head = part("body/head");
        if (head != null) {
            head.yRot = headYaw * Mth.DEG_TO_RAD * 0.6F;
            head.xRot = Mth.sin(age * 0.07F) * 0.05F;
        }
        ModelPart tool = part("body/tool");
        if (tool != null && working) {
            tool.xRot = 0.35F + Mth.sin(age * 0.45F) * 0.35F;
        }
    }

    /** Place la pile de pose sur l'outil (main droite du robot, pince du drone, tete de grue du rover). */
    public void translateToTool(PoseStack pose) {
        String path = switch (kind) {
            case ROBOT -> "body/right_arm";
            case DRONE -> "body/claw";
            case ROVER -> "chassis/boom";
            case TURTLE -> "body/tool";
        };
        ModelPart part = root;
        part.translateAndRotate(pose);
        for (String name : path.split("/")) {
            if (!part.hasChild(name)) {
                return;
            }
            part = part.getChild(name);
            part.translateAndRotate(pose);
        }
        switch (kind) {
            case ROBOT -> pose.translate(0, 0.75, -0.06);
            case DRONE -> pose.translate(0, 0.28, 0);
            case ROVER -> pose.translate(0, -0.12, 0.5);
            case TURTLE -> pose.translate(0, 0, -0.3);
        }
    }

    /** Place la pile de pose sur la soute (plateau du rover, sous la pince du drone, dos du robot). */
    public void translateToCargo(PoseStack pose) {
        String path = switch (kind) {
            case ROBOT -> "body";
            case DRONE -> "body/claw";
            case ROVER -> "chassis/bed";
            case TURTLE -> "body";
        };
        ModelPart part = root;
        part.translateAndRotate(pose);
        for (String name : path.split("/")) {
            if (!part.hasChild(name)) {
                return;
            }
            part = part.getChild(name);
            part.translateAndRotate(pose);
        }
    }

    @Override
    public void renderToBuffer(@NotNull PoseStack pose, @NotNull VertexConsumer buffer, int light, int overlay, float red, float green, float blue,
          float alpha) {
        root.render(pose, buffer, light, overlay, red, green, blue, alpha);
    }
}
