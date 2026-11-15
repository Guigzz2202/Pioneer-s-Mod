package fr.pioneers.client.farm.crustacean;

import fr.pioneers.farm.crustacean.Crustacean;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * 1.4 : modele d'une silhouette de crustace ({@link CrustaceanModels}) : pattes qui battent et balaient a la marche (en alternance par cote et
 * par rang), pinces qui s'ouvrent et se levent, abdomen et eventail qui ondulent, antennes qui oscillent.
 */
public class CrustaceanModel extends HierarchicalModel<Crustacean> {

    private static final int CLAW = 1, LEG = 2, TAIL = 3, ANTENNA = 4, FAN = 6;
    private final ModelPart root;
    private final ModelPart[] parts;
    private final int[] role, side, rank;

    public CrustaceanModel(ModelPart root, int shape) {
        this.root = root;
        String[] paths = CrustaceanModels.PATHS[shape];
        int[] anim = CrustaceanModels.ANIM[shape];
        parts = new ModelPart[paths.length];
        role = new int[paths.length];
        side = new int[paths.length];
        rank = new int[paths.length];
        for (int i = 0; i < paths.length; i++) {
            ModelPart p = root;
            for (String name : paths[i].split("/")) {
                p = p.getChild(name);
            }
            parts[i] = p;
            role[i] = anim[3 * i];
            side[i] = anim[3 * i + 1];
            rank[i] = anim[3 * i + 2];
        }
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Crustacean c, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        pose(limbSwing, limbSwingAmount, age);
    }

    /** 1.0.4-beta (lot 28) : la pose sans entite (aquarium : {@code J:client/farm/aquarium/AquariumRenderer}). */
    public void pose(float limbSwing, float limbSwingAmount, float age) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float walk = Math.min(1F, limbSwingAmount * 1.6F);
        float t = limbSwing * 2.2F;
        for (int i = 0; i < parts.length; i++) {
            ModelPart p = parts[i];
            int s = side[i];
            switch (role[i]) {
                case LEG -> {
                    float ph = rank[i] * 1.9F + (s > 0 ? 0 : Mth.PI);
                    p.zRot += s * walk * 0.35F * Mth.sin(t + ph);
                    p.yRot += s * walk * 0.30F * Mth.cos(t + ph);
                }
                case CLAW -> {
                    p.yRot += s * (0.12F * Mth.sin(age * 0.12F + s) - 0.1F * walk);
                    p.xRot -= 0.25F * walk + 0.05F * Mth.sin(age * 0.09F);
                }
                case TAIL -> p.xRot += 0.06F * Mth.sin(age * 0.1F - rank[i] * 0.8F) + 0.08F * walk * Mth.sin(t);
                case FAN -> p.xRot += 0.12F * Mth.sin(age * 0.13F);
                case ANTENNA -> {
                    p.yRot += 0.18F * Mth.sin(age * 0.17F + s * 1.3F);
                    p.xRot += 0.08F * Mth.cos(age * 0.11F + s);
                }
                default -> {
                }
            }
        }
    }
}
