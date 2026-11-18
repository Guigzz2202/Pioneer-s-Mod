package fr.pioneers.society.economy.client;

import fr.pioneers.society.economy.peddler.PeddlerEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : maquette animee du Colporteur. Grand chapeau, bras libres, haut sac a dos a armature (couverture roulee,
 * marmite, poele, tapis roule), echarpe vert-de-gris, besace a rabat et baton tenu droit. Animations : marche (jambes, bras gauche,
 * corps penche sous le sac, sac qui ballotte, echarpe), regard, respiration, pose d'echange (bras tendu, besace ouverte).
 * FICHIER GENERE par tools/gc/village/peddler_art.py, avec les textures entity/peddler/peddler_<n>.png (ne pas modifier a la main).
 */
public class PeddlerModel extends HierarchicalModel<PeddlerEntity> {

    private final ModelPart root, body, head, rightArm, leftArm, staff, rightLeg, leftLeg, pack, scarfTail, satchelFlap;

    public PeddlerModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.staff = rightArm.getChild("staff");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.pack = body.getChild("pack");
        this.scarfTail = body.getChild("scarf_tail");
        this.satchelFlap = body.getChild("satchel").getChild("satchel_flap");
    }

    /** Couche de la maquette (texture de 64 x 128). */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("right_leg", root.addOrReplaceChild("right_leg", CubeListBuilder.create()
              .texOffs(32, 59).addBox(-2F, 0F, -2F, 4F, 11F, 4F)
              .texOffs(42, 0).addBox(-2.5F, 8F, -2.5F, 5F, 4F, 5F)
              .texOffs(24, 50).addBox(-2F, 10F, -3.5F, 4F, 2F, 1F),
              PartPose.offset(-2F, 12F, 0F)));
        parts.put("left_leg", root.addOrReplaceChild("left_leg", CubeListBuilder.create()
              .texOffs(48, 59).addBox(-2F, 0F, -2F, 4F, 11F, 4F)
              .texOffs(0, 75).addBox(-2.5F, 8F, -2.5F, 5F, 4F, 5F)
              .texOffs(0, 84).addBox(-2F, 10F, -3.5F, 4F, 2F, 1F),
              PartPose.offset(2F, 12F, 0F)));
        parts.put("body", root.addOrReplaceChild("body", CubeListBuilder.create()
              .texOffs(0, 31).addBox(-4F, -12F, -2.5F, 8F, 12F, 5F)
              .texOffs(0, 48).addBox(-4.5F, -1F, -3F, 9F, 5F, 6F)
              .texOffs(24, 15).addBox(-4.5F, -2F, -3F, 9F, 1F, 6F)
              .texOffs(26, 74).addBox(-4.5F, -12F, -3F, 9F, 2F, 6F),
              PartPose.offset(0F, 12F, 0F)));
        parts.put("scarf_tail", parts.get("body").addOrReplaceChild("scarf_tail", CubeListBuilder.create()
              .texOffs(57, 74).addBox(-1F, 0F, -0.25F, 2F, 8F, 1F),
              PartPose.offset(2F, -10F, -3.25F)));
        parts.put("strap", parts.get("body").addOrReplaceChild("strap", CubeListBuilder.create()
              .texOffs(61, 42).addBox(-0.5F, 0F, -0.8F, 1F, 13F, 0.5F),
              PartPose.offsetAndRotation(-3F, -11F, -2.5F, 0F, 0F, -0.62F)));
        parts.put("satchel", parts.get("body").addOrReplaceChild("satchel", CubeListBuilder.create()
              .texOffs(0, 0).addBox(-1F, 0F, -2F, 2F, 4F, 4F),
              PartPose.offset(5.6F, 0.5F, 0F)));
        parts.put("satchel_flap", parts.get("satchel").addOrReplaceChild("satchel_flap", CubeListBuilder.create()
              .texOffs(52, 35).addBox(0F, 0F, -2.25F, 0.5F, 2.5F, 4.5F)
              .texOffs(42, 9).addBox(-2.25F, -0.5F, -2.25F, 2.75F, 0.5F, 4.5F),
              PartPose.offset(1F, 0F, 0F)));
        parts.put("pack", parts.get("body").addOrReplaceChild("pack", CubeListBuilder.create()
              .texOffs(26, 35).addBox(-4F, 0F, 0.1F, 8F, 10F, 5F)
              .texOffs(30, 50).addBox(-4.5F, -0.5F, -0.1F, 9F, 2.5F, 5.6F)
              .texOffs(43, 89).addBox(-4.5F, -5F, 5.3F, 1F, 17F, 1F)
              .texOffs(47, 89).addBox(3.5F, -5F, 5.3F, 1F, 17F, 1F)
              .texOffs(0, 93).addBox(-4.5F, -4F, 5.3F, 9F, 1F, 1F)
              .texOffs(0, 87).addBox(-5.5F, -3.5F, 1.5F, 11F, 3F, 3F)
              .texOffs(50, 22).addBox(-3.5F, -3.7F, 1.3F, 1F, 3.4F, 3.4F)
              .texOffs(52, 42).addBox(2.5F, -3.7F, 1.3F, 1F, 3.4F, 3.4F)
              .texOffs(0, 8).addBox(-7F, 4F, 1.5F, 3F, 3F, 3F)
              .texOffs(15, 75).addBox(-7.5F, 3.5F, 1F, 4F, 0.5F, 4F)
              .texOffs(48, 15).addBox(-2F, 4.5F, 5.2F, 4F, 4F, 0.5F)
              .texOffs(0, 0).addBox(-0.5F, 1.5F, 5.2F, 1F, 3F, 0.5F)
              .texOffs(18, 82).addBox(-6F, 9.5F, 1.5F, 12F, 2.5F, 2.5F),
              PartPose.offset(0F, -11F, 2.5F)));
        parts.put("head", parts.get("body").addOrReplaceChild("head", CubeListBuilder.create()
              .texOffs(0, 15).addBox(-4F, -8F, -4F, 8F, 8F, 8F)
              .texOffs(8, 0).addBox(-1F, -4.5F, -5F, 2F, 2F, 1F),
              PartPose.offset(0F, -12F, 0F)));
        parts.put("hat", parts.get("head").addOrReplaceChild("hat", CubeListBuilder.create()
              .texOffs(0, 0).addBox(-7F, -1.5F, -7F, 14F, 1F, 14F)
              .texOffs(23, 22).addBox(-4.5F, -5.5F, -4.5F, 9F, 4F, 9F)
              .texOffs(0, 15).addBox(4.75F, -5.6F, -2F, 0.5F, 4F, 2F),
              PartPose.offset(0F, -6F, 0F)));
        parts.put("right_arm", parts.get("body").addOrReplaceChild("right_arm", CubeListBuilder.create()
              .texOffs(0, 59).addBox(-2F, -2F, -2F, 4F, 12F, 4F)
              .texOffs(42, 82).addBox(-2.5F, 6.5F, -2.5F, 5F, 1.5F, 5F),
              PartPose.offset(-6F, -10F, 0F)));
        parts.put("left_arm", parts.get("body").addOrReplaceChild("left_arm", CubeListBuilder.create()
              .texOffs(16, 59).addBox(-2F, -2F, -2F, 4F, 12F, 4F)
              .texOffs(23, 88).addBox(-2.5F, 6.5F, -2.5F, 5F, 1.5F, 5F),
              PartPose.offset(6F, -10F, 0F)));
        parts.put("staff", parts.get("right_arm").addOrReplaceChild("staff", CubeListBuilder.create()
              .texOffs(59, 9).addBox(-0.5F, -16F, -0.5F, 1F, 29F, 1F)
              .texOffs(47, 35).addBox(-1F, -16F, -1F, 2F, 2F, 2F)
              .texOffs(42, 0).addBox(0.5F, -14.5F, -0.1F, 0.5F, 3F, 2F),
              PartPose.offset(0F, 9F, 0F)));
        return LayerDefinition.create(mesh, 64, 128);
    }

    @Override
    public @NotNull ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(@NotNull PeddlerEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
          float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float walk = limbSwing * 0.6662F;
        float amount = Math.min(limbSwingAmount, 1.0F);
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        // marche : pas, bras gauche en balancier, corps penche sous le sac, sac et echarpe qui ballottent
        rightLeg.xRot = Mth.cos(walk) * 1.2F * amount;
        leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.2F * amount;
        body.xRot = 0.04F + 0.08F * amount;
        body.zRot = Mth.cos(walk) * 0.035F * amount;
        pack.xRot = Mth.sin(walk * 2.0F) * 0.05F * amount;
        pack.y += Math.abs(Mth.cos(walk)) * 0.4F * amount;
        leftArm.xRot = Mth.cos(walk) * 0.9F * amount;
        rightArm.xRot = -0.45F + Mth.cos(walk + Mth.PI) * 0.22F * amount;
        scarfTail.xRot = -0.08F - Math.abs(Mth.cos(walk)) * 0.3F * amount;
        // respiration au repos
        float breath = Mth.sin(ageInTicks * 0.067F) * 0.035F;
        leftArm.zRot = -0.06F - breath;
        rightArm.zRot = 0.06F + breath;
        if (entity.isTrading()) {
            // pose d'echange : bras gauche tendu paume ouverte, baton contre l'epaule, besace ouverte
            body.xRot = 0.06F;
            body.zRot = 0.0F;
            leftArm.xRot = -1.25F;
            leftArm.yRot = -0.32F;
            leftArm.zRot = 0.0F;
            rightArm.xRot = -0.62F;
            satchelFlap.zRot = -1.45F;
        }
        // le baton reste droit dans la main
        staff.xRot = -(rightArm.xRot + body.xRot);
        staff.zRot = -(rightArm.zRot + body.zRot);
    }
}
