package fr.pioneers.client.farm.herd;

import fr.pioneers.Pioneers;
import net.minecraft.client.model.RabbitModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.animal.Rabbit;

/**
 * 1.0.4-beta (lot 9) : modeles des lapins du mod. Meme geometrie que le lapin vanilla ({@code RabbitModel.createBodyLayer}, cotes recopiees)
 * avec deux variantes : OREILLES TOMBANTES du belier (deux boites 1 x 6 x 2 qui pendent le long des joues, UV libres de la texture
 * {@link #LOP_EAR_UV}) et POIL LONG de l'angora (coque gonflee de {@link #FUR} px, texture {@code angora_fur}). Banc :
 * {@code tools/farmtest/HerdGeom.java} (apercus {@code scratchpad/p104/lot9/tools/herd_preview.py}).
 */
public final class HerdModels {

    private HerdModels() {
    }

    public static final ModelLayerLocation RABBIT = new ModelLayerLocation(Pioneers.rl("herd_rabbit"), "main");
    public static final ModelLayerLocation RABBIT_LOP = new ModelLayerLocation(Pioneers.rl("herd_rabbit_lop"), "main");
    public static final ModelLayerLocation RABBIT_FUR = new ModelLayerLocation(Pioneers.rl("herd_rabbit_fur"), "main");
    /** UV des oreilles tombantes (droite, gauche) : coins libres de la texture 64 x 32 du lapin (meme valeur : T:farm2/herd.py LOP_EARS). */
    public static final int[][] LOP_EAR_UV = {{44, 16}, {52, 16}};
    /** Epaisseur du poil long de l'angora (px). */
    public static final float FUR = 1.0F;

    /** Lapin : {@code lop} = oreilles tombantes ; {@code fur} = gonflement (0 : corps ; > 0 : poil long, sans oreilles ni pattes avant). */
    public static LayerDefinition rabbit(boolean lop, float fur) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation d = new CubeDeformation(fur), legs = fur > 0 ? new CubeDeformation(fur * 0.4F) : CubeDeformation.NONE;
        root.addOrReplaceChild("left_hind_foot", CubeListBuilder.create().texOffs(26, 24).addBox(-1F, 5.5F, -3.7F, 2, 1, 7, legs), PartPose.offset(3F, 17.5F, 3.7F));
        root.addOrReplaceChild("right_hind_foot", CubeListBuilder.create().texOffs(8, 24).addBox(-1F, 5.5F, -3.7F, 2, 1, 7, legs), PartPose.offset(-3F, 17.5F, 3.7F));
        root.addOrReplaceChild("left_haunch", CubeListBuilder.create().texOffs(30, 15).addBox(-1F, 0F, 0F, 2, 4, 5, d),
              PartPose.offsetAndRotation(3F, 17.5F, 3.7F, -0.34906584F, 0F, 0F));
        root.addOrReplaceChild("right_haunch", CubeListBuilder.create().texOffs(16, 15).addBox(-1F, 0F, 0F, 2, 4, 5, d),
              PartPose.offsetAndRotation(-3F, 17.5F, 3.7F, -0.34906584F, 0F, 0F));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -2F, -10F, 6, 5, 10, d),
              PartPose.offsetAndRotation(0F, 19F, 8F, -0.34906584F, 0F, 0F));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(8, 15).addBox(-1F, 0F, -1F, 2, 7, 2, legs),
              PartPose.offsetAndRotation(3F, 17F, -1F, -0.17453292F, 0F, 0F));
        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(0, 15).addBox(-1F, 0F, -1F, 2, 7, 2, legs),
              PartPose.offsetAndRotation(-3F, 17F, -1F, -0.17453292F, 0F, 0F));
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(32, 0).addBox(-2.5F, -4F, -5F, 5, 4, 5, d), PartPose.offset(0F, 16F, -1F));
        if (lop) {
            //Oreilles du belier : pendent le long des joues, du dessus de la tete a 2 px sous la machoire
            root.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(LOP_EAR_UV[0][0], LOP_EAR_UV[0][1]).addBox(-3.5F, -4.2F, -3F, 1, 6, 2, d),
                  PartPose.offset(0F, 16F, -1F));
            root.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(LOP_EAR_UV[1][0], LOP_EAR_UV[1][1]).addBox(2.5F, -4.2F, -3F, 1, 6, 2, d),
                  PartPose.offset(0F, 16F, -1F));
        } else {
            root.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(52, 0).addBox(-2.5F, -9F, -1F, 2, 5, 1, d),
                  PartPose.offsetAndRotation(0F, 16F, -1F, 0F, -0.2617994F, 0F));
            root.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(58, 0).addBox(0.5F, -9F, -1F, 2, 5, 1, d),
                  PartPose.offsetAndRotation(0F, 16F, -1F, 0F, 0.2617994F, 0F));
        }
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(52, 6).addBox(-1.5F, -1.5F, 0F, 3, 3, 2, d),
              PartPose.offsetAndRotation(0F, 20F, 7F, -0.3490659F, 0F, 0F));
        root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(32, 9).addBox(-0.5F, -2.5F, -5.5F, 1, 1, 1, fur > 0 ? CubeDeformation.NONE : d),
              PartPose.offset(0F, 16F, -1F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** Modele du belier : les oreilles suivent la tete sans s'ecarter (le vanilla les ecarte de 15 degres). */
    public static final class Lop extends RabbitModel<Rabbit> {

        private final ModelPart head, leftEar, rightEar;

        public Lop(ModelPart root) {
            super(root);
            head = root.getChild("head");
            leftEar = root.getChild("left_ear");
            rightEar = root.getChild("right_ear");
        }

        @Override
        public void setupAnim(Rabbit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            leftEar.xRot = rightEar.xRot = head.xRot;
            leftEar.yRot = rightEar.yRot = head.yRot;
            //Leger balancement des oreilles avec la marche
            float sway = (float) Math.sin(limbSwing * 0.6F) * 0.08F * limbSwingAmount;
            leftEar.zRot = -0.06F + sway;
            rightEar.zRot = 0.06F + sway;
        }
    }
}
