package fr.pioneers.client.farm.fish;

import fr.pioneers.farm.fish.PiFish;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Modele d'une espece de poisson ({@link FishModels}) : nage par ondulation des maillons (corps, arriere, troncons de l'anguille, queue), plus forte hors de l'eau. */
public class FishModel extends HierarchicalModel<PiFish> {

    private final ModelPart root;
    private final ModelPart[] sway;
    private final float[] amp, phase;

    public FishModel(ModelPart root, int species) {
        this.root = root;
        String[] paths = FishModels.SWAY_PATHS[species];
        float[] s = FishModels.SWAY[species];
        sway = new ModelPart[paths.length];
        amp = new float[paths.length];
        phase = new float[paths.length];
        for (int i = 0; i < paths.length; i++) {
            ModelPart p = root;
            for (String name : paths[i].split("/")) {
                p = p.getChild(name);
            }
            sway[i] = p;
            amp[i] = s[2 * i];
            phase[i] = s[2 * i + 1];
        }
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(PiFish fish, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        swim(age, fish.isInWater());
    }

    /** 1.0.4-beta (lot 28) : ondulation sans entite (aquarium : {@code J:client/farm/aquarium/AquariumRenderer}). */
    public void swim(float age, boolean inWater) {
        swim(age, inWater, 1F);
    }

    /**
     * 1.0.5-beta (lot 25) : ondulation d'amplitude reglable ({@code strength} : 1 = celle de l'entite ; l'aquarium la fait suivre l'allure du poisson,
     * {@code J:client/farm/aquarium/AquariumSwim.pose}).
     */
    public void swim(float age, boolean inWater, float strength) {
        float k = (inWater ? 1.0F : 1.6F) * strength;
        float speed = inWater ? 0.6F : 1.7F;
        for (int i = 0; i < sway.length; i++) {
            sway[i].yRot = -k * 0.45F * amp[i] * Mth.sin(speed * age - phase[i]);
        }
    }
}
