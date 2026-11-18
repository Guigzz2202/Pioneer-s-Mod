package fr.pioneers.world.ruins.client.sentinel;

import fr.pioneers.world.ruins.sentinel.ForemanEntity;
import fr.pioneers.world.ruins.sentinel.SentinelEntity;
import fr.pioneers.world.ruins.sentinel.SentinelRole;
import javax.annotation.Nullable;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : maquette animee d'une Sentinelle (couches generees {@link SentinelModels}). Animations : repos plie (endormie),
 * reveil de 1,5 s (deplie avec a-coups), marche, attaque (frappe, recul du canon, outil du Reparateur), etourdissement (tremblement), mort
 * (effondrement ; les etincelles viennent de l'entite). Pieces de tier : bouclier du Gardien, lentille de la Tourelle, troisieme bras du
 * Reparateur (MKIII).
 */
public class SentinelModel extends HierarchicalModel<SentinelEntity> {

    private static final float DEG = Mth.DEG_TO_RAD;
    private final ModelPart root;
    private final SentinelRole role;
    @Nullable
    private final ModelPart body, head, leftArm, rightArm, leftLeg, rightLeg, shield, gun, lens, aux, leftTool, rightTool, leftGun, rightGun;
    private final ModelPart[] rotors;

    public SentinelModel(ModelPart root, SentinelRole role) {
        this.root = root;
        this.role = role;
        switch (role) {
            case GUARDIAN, FOREMAN, REPAIRER -> {
                body = root.getChild("body");
                head = body.getChild("head");
                leftArm = body.getChild("left_arm");
                rightArm = body.getChild("right_arm");
                leftLeg = root.getChild("left_leg");
                rightLeg = root.getChild("right_leg");
                shield = role == SentinelRole.GUARDIAN ? leftArm.getChild("shield") : null;
                aux = role == SentinelRole.REPAIRER ? body.getChild("aux") : null;
                leftTool = role == SentinelRole.REPAIRER ? leftArm.getChild("left_tool") : null;
                rightTool = role == SentinelRole.REPAIRER ? rightArm.getChild("right_tool") : null;
                leftGun = role == SentinelRole.FOREMAN ? body.getChild("left_gun") : null;
                rightGun = role == SentinelRole.FOREMAN ? body.getChild("right_gun") : null;
                gun = lens = null;
                rotors = new ModelPart[0];
            }
            case PATROLLER -> {
                body = root.getChild("body");
                gun = body.getChild("gun");
                rotors = new ModelPart[4];
                for (int i = 0; i < 4; i++) {
                    rotors[i] = body.getChild("rotor" + i);
                }
                head = leftArm = rightArm = leftLeg = rightLeg = shield = lens = aux = leftTool = rightTool = leftGun = rightGun = null;
            }
            default -> {
                body = root.getChild("base");
                head = root.getChild("head");
                gun = head.getChild("gun");
                lens = gun.getChild("lens");
                rotors = new ModelPart[0];
                leftArm = rightArm = leftLeg = rightLeg = shield = aux = leftTool = rightTool = leftGun = rightGun = null;
            }
        }
    }

    @Override
    public @NotNull ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(@NotNull SentinelEntity e, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float pt = Mth.clamp(age - e.tickCount, 0, 1);
        float fold = e.fold(pt);
        float awake = 1 - fold;
        //A-coups du reveil (maximum a mi-chemin du depliage)
        float jolt = fold * awake * 4 * Mth.sin(age * 2.3F) * 0.07F;
        float death = e.deathTime > 0 ? Math.min(1, (e.deathTime + pt) / 20F) : 0;
        float shake = e.flag(SentinelEntity.FLAG_STUN) ? Mth.sin(age * 9.1F) * 0.06F : 0;
        int tier = e.tier();
        switch (role) {
            case GUARDIAN -> walker(e, limbSwing, limbAmount, age, yaw, pitch, fold, awake, jolt, death, shake, 0.5F, 0.7F);
            case FOREMAN -> {
                walker(e, limbSwing, limbAmount, age, yaw, pitch, fold, awake, jolt, death, shake, 0.42F, 0.6F);
                foreman((ForemanEntity) e, age, fold);
            }
            case REPAIRER -> {
                walker(e, limbSwing, limbAmount, age, yaw, pitch, fold, awake, jolt, death, shake, 0.8F, 0.8F);
                repairer(e, age, fold, awake);
            }
            case PATROLLER -> patroller(e, limbAmount, age, yaw, pitch, fold, death, shake);
            case TURRET -> turret(e, age, yaw, pitch, fold, jolt, death, shake);
        }
        if (shield != null) {
            shield.visible = tier >= 3;
        }
        if (lens != null) {
            lens.visible = tier >= 3;
        }
        if (aux != null) {
            aux.visible = tier >= 3;
        }
    }

    /** Gardien, Contremaitre, Reparateur : marche lourde, frappe a deux bras, repos plie (buste penche, tete basse, bras poses). */
    private void walker(SentinelEntity e, float limbSwing, float limbAmount, float age, float yaw, float pitch, float fold, float awake, float jolt,
          float death, float shake, float pace, float stride) {
        float swing = Mth.cos(limbSwing * pace) * stride * limbAmount;
        leftLeg.xRot = swing;
        rightLeg.xRot = -swing;
        leftArm.xRot = -swing * 0.6F;
        rightArm.xRot = swing * 0.6F;
        //Repos plie
        body.xRot = 0.5F * fold + jolt + shake;
        body.y += 3.0F * fold;
        head.yRot = yaw * DEG * awake;
        head.xRot = pitch * DEG * awake + 0.6F * fold - jolt;
        leftArm.xRot += -0.35F * fold;
        rightArm.xRot += -0.35F * fold;
        leftArm.zRot = -0.12F * fold - jolt;
        rightArm.zRot = 0.12F * fold + jolt;
        //Frappe : les deux bras montent puis s'abattent
        float a = attackTime;
        if (a > 0) {
            float k = Mth.sin(a * Mth.PI);
            leftArm.xRot = -2.1F * k + (1 - k) * leftArm.xRot;
            rightArm.xRot = -2.1F * k + (1 - k) * rightArm.xRot;
            body.xRot += 0.25F * Mth.sin(a * Mth.PI * 0.5F);
        }
        //Effondrement
        if (death > 0) {
            body.xRot += 1.0F * death;
            body.y += 7.0F * death;
            head.xRot += 0.7F * death;
            leftArm.zRot -= 0.6F * death;
            rightArm.zRot += 0.6F * death;
            leftLeg.xRot = -0.3F * death;
            rightLeg.xRot = 0.4F * death;
            leftLeg.zRot = -0.15F * death;
            rightLeg.zRot = 0.15F * death;
        }
        head.zRot = shake;
    }

    private void foreman(ForemanEntity e, float age, float fold) {
        //Tourelles d'epaule : repliees canon en l'air, deployees en phase 3
        float deploy = e.phase() >= 3 ? 1 : 0;
        float recoil = e.flag(SentinelEntity.FLAG_HEAT) ? Math.abs(Mth.sin(age * 0.9F)) * 0.08F : 0;
        leftGun.xRot = -1.35F * (1 - deploy) + recoil;
        rightGun.xRot = -1.35F * (1 - deploy) + recoil;
        if (e.flag(SentinelEntity.FLAG_WORK)) {
            //Charge : buste en avant, bras en arriere
            body.xRot += 0.35F;
            leftArm.xRot = 0.6F;
            rightArm.xRot = 0.6F;
        }
    }

    private void repairer(SentinelEntity e, float age, float fold, float awake) {
        //Repos : bras croises devant, outils ranges
        leftArm.xRot += -0.6F * fold;
        rightArm.xRot += -0.6F * fold;
        leftArm.zRot += 0.5F * fold;
        rightArm.zRot += -0.5F * fold;
        if (e.flag(SentinelEntity.FLAG_WORK)) {
            rightArm.xRot = -1.25F + Mth.sin(age * 0.8F) * 0.12F;
            rightTool.xRot = -0.4F;
            leftArm.xRot = -0.9F;
            leftTool.yRot = Mth.sin(age * 0.6F) * 0.35F;
        } else {
            rightArm.xRot += Mth.sin(age * 0.08F) * 0.05F * awake;
            leftTool.yRot = 0;
        }
        aux.xRot = -0.25F + Mth.sin(age * 0.05F) * 0.3F * awake - 0.5F * fold;
    }

    private void patroller(SentinelEntity e, float limbAmount, float age, float yaw, float pitch, float fold, float death, float shake) {
        boolean down = death > 0 || e.flag(SentinelEntity.FLAG_STUN);
        float spin = down ? age * 0.25F : age * 1.7F;
        for (int i = 0; i < rotors.length; i++) {
            rotors[i].yRot = (i % 2 == 0 ? spin : -spin);
        }
        body.xRot = Math.min(0.45F, limbAmount * 0.5F) + shake;
        body.zRot = Mth.sin(age * 0.09F) * 0.05F + death * 0.9F;
        body.y += Mth.sin(age * 0.15F) * 0.6F * (1 - death);
        gun.xRot = pitch * DEG * (1 - fold) + 0.5F * fold;
        float a = attackTime;
        if (a > 0) {
            gun.z += Mth.sin(a * Mth.PI) * 1.2F;
        }
    }

    private void turret(SentinelEntity e, float age, float yaw, float pitch, float fold, float jolt, float death, float shake) {
        float awake = 1 - fold;
        head.yRot = yaw * DEG * awake + jolt;
        gun.xRot = pitch * DEG * awake + 0.65F * fold + shake;
        if (e.flag(SentinelEntity.FLAG_WORK)) {
            //Laser en charge : le canon vibre de plus en plus
            float k = Math.min(1, e.workTicks / 30F);
            gun.xRot += Mth.sin(age * 4.0F) * 0.02F * k;
        }
        float a = attackTime;
        if (a > 0) {
            gun.z += Mth.sin(a * Mth.PI) * 1.5F;
        }
        if (death > 0) {
            head.xRot = 0.45F * death;
            head.zRot = 0.3F * death;
            gun.xRot += 0.4F * death;
        }
    }
}
