package fr.pioneers.farm.client.animal;

import fr.pioneers.farm.animal.AnimalData;
import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.PiAnimals;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import org.jetbrains.annotations.Nullable;

/**
 * Modele anime d'un animal d'elevage (1.0.3-beta, lot 8) sur la geometrie generee {@link AnimalModels} (une couche par race et par sexe ;
 * groupes nommes par {@code tools/gc/farm2/animal_shapes.py}). Animations : marche (pattes en opposition comme la vanilla, leger balancement
 * du corps), regard, broutage (vache et chevre a l'arret, par cycles propres a chaque bete ; mouton : animation vanilla de l'herbe mangee),
 * picorage et battement d'ailes (poule), queue qui fouette, oreilles qui frémissent, coup de tete de la chevre (vanilla). Petits : tete x1,4,
 * pattes x1,3, sans cornes, mamelle, crete ni defenses. Chevre : cornes perdues cachees ; angora : mohair cache tondu.
 */
public class BreedModel<T extends Animal> extends HierarchicalModel<T> {

    private final ModelPart root;
    private final AnimalSpecies species;
    @Nullable
    private final ModelPart body, head, tail, tailf, earL, earR, wingL, wingR, hornL, hornR;
    private final ModelPart[] legs;
    private final List<ModelPart> adult = new ArrayList<>(), coat = new ArrayList<>();
    private final float legLen;
    /** Poule : battement d'ailes (comme ChickenRenderer.getBob) ; temps partiel de l'image. */
    public float flap, partial;

    public BreedModel(ModelPart root, AnimalSpecies species, int layer) {
        this.root = root;
        this.species = species;
        body = part("body");
        head = part("head");
        tail = part("tail");
        tailf = part("tailf");
        earL = part("ear_l");
        earR = part("ear_r");
        wingL = part("wing_l");
        wingR = part("wing_r");
        hornL = part("horn_l0");
        hornR = part("horn_r0");
        legs = species == AnimalSpecies.CHICKEN ? new ModelPart[]{part("leg_l"), part("leg_r")}
              : new ModelPart[]{part("leg_fl"), part("leg_fr"), part("leg_bl"), part("leg_br")};
        for (String n : AnimalModels.ADULT[layer]) {
            ModelPart p = part(n);
            if (p != null) {
                adult.add(p);
            }
        }
        for (String n : AnimalModels.COAT[layer]) {
            ModelPart p = part(n);
            if (p != null) {
                coat.add(p);
            }
        }
        legLen = AnimalModels.LEG[layer];
    }

    @Nullable
    private ModelPart part(String name) {
        return getAnyDescendantWithName(name).orElse(null);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void prepareMobModel(T entity, float limbSwing, float limbAmount, float partialTick) {
        partial = partialTick;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        boolean young = entity.isBaby();
        for (ModelPart p : adult) {
            p.visible = !young;
        }
        AnimalData d = PiAnimals.get(entity);
        if (!coat.isEmpty()) {
            boolean grown = d == null || d.produce >= 0.35F;
            for (ModelPart p : coat) {
                p.visible = grown;
            }
        }
        if (entity instanceof Goat goat) {
            if (hornL != null) {
                hornL.visible = !young && goat.hasLeftHorn();
            }
            if (hornR != null) {
                hornR.visible = !young && goat.hasRightHorn();
            }
        }
        float seed = (entity.getId() * 37 % 997) * 0.731F;
        float walk = Math.min(1F, limbAmount);
        float still = 1F - Math.min(1F, walk * 4F);
        //Marche : pattes en opposition, corps qui se balance
        float k = 0.6662F;
        if (legs.length == 4) {
            swing(legs[0], Mth.cos(limbSwing * k) * 1.2F * walk);
            swing(legs[1], Mth.cos(limbSwing * k + Mth.PI) * 1.2F * walk);
            swing(legs[2], Mth.cos(limbSwing * k + Mth.PI) * 1.2F * walk);
            swing(legs[3], Mth.cos(limbSwing * k) * 1.2F * walk);
        } else {
            swing(legs[0], Mth.cos(limbSwing * k) * 1.4F * walk);
            swing(legs[1], Mth.cos(limbSwing * k + Mth.PI) * 1.4F * walk);
        }
        if (body != null) {
            body.y -= Math.abs(Mth.sin(limbSwing * k)) * 0.5F * walk;
            body.zRot = Mth.sin(limbSwing * k) * 0.025F * walk;
        }
        //Regard
        if (head != null) {
            head.yRot = Mth.clamp(headYaw, -60F, 60F) * Mth.DEG_TO_RAD * (species == AnimalSpecies.CHICKEN ? 1F : 0.7F);
            head.xRot = headPitch * Mth.DEG_TO_RAD * 0.8F;
            float graze = 0;
            if (entity instanceof Sheep sheep) {
                float eat = sheep.getHeadEatPositionScale(partial);
                if (eat > 0) {
                    graze = Math.min(1F, eat * 1.6F);
                    head.xRot += Mth.sin(age * 1.4F) * 0.08F * graze;
                }
            } else if (species == AnimalSpecies.COW || species == AnimalSpecies.GOAT) {
                graze = cycle(age + seed * 40F, 520F, 150F) * still;
            }
            if (graze > 0) {
                float down = species == AnimalSpecies.COW ? 1.05F : species == AnimalSpecies.GOAT ? 0.95F : 1.15F;
                head.xRot = Mth.lerp(graze, head.xRot, down) + Mth.sin(age * 0.9F) * 0.04F * graze;
                head.yRot *= 1F - graze;
            }
            if (species == AnimalSpecies.CHICKEN) {
                //Picorage : deux coups de bec rapides, de temps en temps, a l'arret
                float c = (age + seed * 30F) % 90F;
                if (c < 14F) {
                    head.xRot += Mth.sin(c / 7F * Mth.PI) * Mth.sin(c / 7F * Mth.PI) * 0.95F * still;
                }
            }
            if (entity instanceof Goat goat) {
                head.xRot += goat.getRammingXHeadRot();
            }
        }
        //Oreilles qui fremissent, queue qui fouette
        float twitch = cycle(age + seed * 20F, 160F, 10F);
        if (earL != null) {
            earL.zRot += Mth.sin(age * 1.9F) * 0.25F * twitch;
        }
        if (earR != null) {
            earR.zRot -= Mth.sin(age * 1.7F + 1F) * 0.25F * cycle(age + seed * 23F, 190F, 10F);
        }
        if (tail != null) {
            if (species == AnimalSpecies.COW) {
                tail.zRot += Mth.sin(age * 0.11F + seed) * 0.18F + Mth.sin(age * 0.6F) * 0.12F * cycle(age + seed * 11F, 140F, 30F);
                tail.xRot += walk * 0.15F;
            } else if (species == AnimalSpecies.GOAT) {
                tail.xRot += Mth.sin(age * 1.2F) * 0.25F * cycle(age + seed * 13F, 120F, 18F);
            } else if (species == AnimalSpecies.SHEEP) {
                tail.zRot += Mth.sin(age * 0.5F + seed) * 0.08F;
            }
        }
        //Poule : ailes (battement vanilla), queue qui suit la marche
        if (wingL != null && wingR != null) {
            wingL.zRot = -flap;
            wingR.zRot = flap;
        }
        if (tailf != null) {
            tailf.xRot += Mth.sin(limbSwing * k * 2F) * 0.08F * walk;
        }
        //Petits : grosse tete, pattes plus longues (le corps remonte d'autant)
        if (young) {
            float kl = 1.3F, kh = 1.4F;
            for (ModelPart l : legs) {
                if (l != null) {
                    l.yScale = kl;
                }
            }
            root.y -= legLen * (kl - 1F);
            if (head != null) {
                head.xScale = head.yScale = head.zScale = kh;
            }
        }
    }

    private static void swing(@Nullable ModelPart leg, float a) {
        if (leg != null) {
            leg.xRot = a;
        }
    }

    /** Fenetre douce d'un cycle : 0 hors de la fenetre, monte a 1 puis redescend sur `len` ticks toutes les `period`. */
    static float cycle(float t, float period, float len) {
        float c = t % period;
        if (c < 0 || c > len) {
            return 0;
        }
        float u = c / len;
        float edge = Math.min(1F, Math.min(u, 1F - u) * 6F);
        return edge * edge * (3 - 2 * edge);
    }
}
