package fr.pioneers.logistics.caravan.client;

import fr.pioneers.farm.client.herd.GeneratedHerdShapes;
import fr.pioneers.farm.client.herd.HerdShapes;
import java.util.Map;
import net.minecraft.client.model.ChestedHorseModel;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 10) : modeles vanilla du cheval et de l'ane / mulet, leur CORPS rendu accessible (le bat s'y pose).
 * 1.0.4-beta (lot 33) : SILHOUETTE de la race du cheval rendu ({@link #shape}, posee par le rendu avant chaque cheval) appliquee apres
 * l'animation vanilla ({@link HerdShapes#applyHorse}) : le modele, la barde et les crins (memes classes) bougent ensemble.
 */
public class HerdHorseModel<T extends AbstractHorse> extends HorseModel<T> {

    private final Map<String, ModelPart> parts;
    private final HerdShapes.Base base = new HerdShapes.Base();
    /** Race du cheval rendu (null : cheval vanilla, proportions d'origine). */
    @Nullable
    public GeneratedHerdShapes.HorseShape shape;

    public HerdHorseModel(ModelPart root) {
        super(root);
        parts = HerdShapes.parts(root);
    }

    /**
     * 1.0.4-beta (lot 35, Guillaume : « Les pattes arrieres des chevaux sont invisibles ») : le modele REPART DE SA POSE INITIALE a chaque
     * image. L'animation vanilla ({@code HorseModel.prepareMobModel}) ne reecrit jamais y / z des jambes ARRIERE (ni leurs copies de poulain) :
     * {@link HerdShapes#applyHorse} les deplacait a partir de la valeur laissee par l'image d'avant, d'ou une derive a chaque image (pattes
     * arriere envolees en quelques images). Le meme modele sert a tous les chevaux du rendu (races differentes) : rien ne doit passer d'une
     * image ou d'un cheval a l'autre.
     */
    @Override
    public void prepareMobModel(@NotNull T horse, float limbSwing, float limbSwingAmount, float partialTick) {
        HerdShapes.restoreHorse(parts);
        super.prepareMobModel(horse, limbSwing, limbSwingAmount, partialTick);
        base.store(parts);
    }

    @Override
    public void setupAnim(@NotNull T horse, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(horse, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        HerdShapes.applyHorse(parts, shape, young, base);
    }

    /** Parties par nom (banc des apercus). */
    public Map<String, ModelPart> parts() {
        return parts;
    }

    /** Pose de repos du banc (sans entite) : valeurs de l'animation = pose initiale, puis la silhouette. */
    public void restPose(boolean young) {
        this.young = young;
        HerdShapes.restoreHorse(parts);
        base.store(parts);
        HerdShapes.applyHorse(parts, shape, young, base);
    }

    public ModelPart body() {
        return body;
    }

    public static class Chested<T extends AbstractChestedHorse> extends ChestedHorseModel<T> {

        public Chested(ModelPart root) {
            super(root);
        }

        public ModelPart body() {
            return body;
        }
    }
}
