package fr.pioneers.client.farm;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.Pioneers;
import fr.pioneers.client.farm.animal.AnimalModels;
import fr.pioneers.client.farm.animal.BreedModel;
import fr.pioneers.farm.animal.AnimalData;
import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.BreedDef;
import fr.pioneers.farm.animal.PiAnimals;
import fr.pioneers.farm.animal.breed.PiBreeds;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * Rendus des animaux d'elevage (1.0.3-beta, lot 8 : une entite par race) : un rendu par type de race, deux modeles (femelle, male) de la race
 * ({@link AnimalModels}, geometrie de {@code tools/gc/farm2/animal_shapes.py}), robe de la race et du sexe
 * ({@code textures/entity/animal/<espece>/<race>_<f|m>.png}, {@code art_animals.py}), echelle de la race ({@link BreedDef#scale}, male 8 % plus
 * grand, petit : moitie), toison des moutons par race (couche a part, teinte par la couleur de la laine). Les types vanilla gardent leur
 * rendu : ils sont convertis en leur race des leur entree dans le monde.
 */
public final class AnimalRenderers {

    private AnimalRenderers() {
    }

    public static final ModelLayerLocation[] LAYERS = new ModelLayerLocation[AnimalModels.LAYERS.length];

    static {
        for (int i = 0; i < LAYERS.length; i++) {
            LAYERS[i] = new ModelLayerLocation(Pioneers.rl("animal_" + AnimalModels.LAYERS[i]), "main");
        }
    }

    /** Couche du modele d'une race : espece, race, sexe (ordre de AnimalModels : espece, race, femelle puis male). */
    public static int layer(AnimalSpecies sp, int breed, boolean male) {
        int i = 0;
        for (AnimalSpecies s : AnimalSpecies.values()) {
            if (s == sp) {
                break;
            }
            i += s.breeds().length * 2;
        }
        return i + breed * 2 + (male ? 1 : 0);
    }

    /** Couche de la toison d'une race de mouton (apres toutes les couches des corps). */
    public static int furLayer(int breed) {
        int i = 0;
        for (AnimalSpecies s : AnimalSpecies.values()) {
            i += s.breeds().length * 2;
        }
        return i + breed;
    }

    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (int i = 0; i < LAYERS.length; i++) {
            int layer = i;
            event.registerLayerDefinition(LAYERS[i], () -> AnimalModels.create(layer));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        for (AnimalSpecies sp : AnimalSpecies.values()) {
            for (int b = 0; b < sp.breeds().length; b++) {
                int breed = b;
                EntityType t = PiBreeds.type(sp, b);
                event.registerEntityRenderer(t, ctx -> new BreedRenderer(ctx, sp, breed));
            }
        }
    }

    public static class BreedRenderer<T extends Animal> extends MobRenderer<T, BreedModel<T>> {

        private final AnimalSpecies species;
        private final BreedDef def;
        private final BreedModel<T> female, male;
        private final ResourceLocation texF, texM;

        public BreedRenderer(EntityRendererProvider.Context ctx, AnimalSpecies species, int breed) {
            super(ctx, new BreedModel<>(ctx.bakeLayer(LAYERS[layer(species, breed, false)]), species, layer(species, breed, false)),
                  0.3F * species.breed(breed).width() / 0.45F);
            this.species = species;
            this.def = species.breed(breed);
            female = model;
            male = new BreedModel<>(ctx.bakeLayer(LAYERS[layer(species, breed, true)]), species, layer(species, breed, true));
            String base = "textures/entity/animal/" + species.id() + "/" + def.id();
            texF = Pioneers.rl(base + "_f.png");
            texM = Pioneers.rl(base + "_m.png");
            if (species == AnimalSpecies.SHEEP) {
                addLayer((RenderLayer) new Fur<>((RenderLayerParent) this, new BreedModel<>(ctx.bakeLayer(LAYERS[furLayer(breed)]), species,
                      furLayer(breed)), Pioneers.rl("textures/entity/animal/sheep/fur_" + def.id() + ".png")));
            }
        }

        private boolean isMale(T e) {
            AnimalData d = PiAnimals.get(e);
            return d != null && d.init && d.male && !e.isBaby();
        }

        @Override
        public void render(T e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
            model = isMale(e) ? male : female;
            if (e instanceof Chicken c) {
                float f = Mth.lerp(partial, c.oFlap, c.flap), s = Mth.lerp(partial, c.oFlapSpeed, c.flapSpeed);
                model.flap = (Mth.sin(f) + 1.0F) * s;
            } else {
                model.flap = 0;
            }
            shadowRadius = 0.6F * def.width() * (e.isBaby() ? 0.5F : 1F);
            super.render(e, yaw, partial, pose, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(T e) {
            return isMale(e) ? texM : texF;
        }

        @Override
        protected void scale(T e, PoseStack pose, float partial) {
            float s = def.scale() * (isMale(e) ? 1.08F : 1F) * (e.isBaby() ? 0.5F : 1F);
            pose.scale(s, s, s);
        }
    }

    /** Toison d'une race de mouton : meme squelette et memes poses que le corps, teinte par la couleur de la laine ; cachee tondue. */
    static class Fur<T extends Sheep> extends RenderLayer<T, BreedModel<T>> {

        private final BreedModel<T> model;
        private final ResourceLocation tex;

        Fur(RenderLayerParent<T, BreedModel<T>> parent, BreedModel<T> model, ResourceLocation tex) {
            super(parent);
            this.model = model;
            this.tex = tex;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, T sheep, float limbSwing, float limbAmount, float partial,
              float age, float headYaw, float headPitch) {
            if (sheep.isSheared() || sheep.isInvisible()) {
                return;
            }
            float[] c = Sheep.getColorArray(sheep.getColor());
            model.partial = partial;
            coloredCutoutModelCopyLayerRender(getParentModel(), model, tex, pose, buffers, light, sheep, limbSwing, limbAmount, age, headYaw, headPitch,
                  partial, c[0], c[1], c[2]);
        }
    }
}
