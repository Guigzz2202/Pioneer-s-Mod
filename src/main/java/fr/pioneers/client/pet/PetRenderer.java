package fr.pioneers.client.pet;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.Pioneers;
import fr.pioneers.pet.GeneratedPets;
import fr.pioneers.pet.Pet;
import fr.pioneers.pet.PiPets;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 5) : RENDU des chiens et des chats : un rendu pour tous les chiens (races du mod + chien sauvage = loup vanilla, robe
 * hirsute, yeux rougis quand il est en colere) et un pour tous les chats (races du mod + chat vanilla pas encore converti : race de sa
 * variante). Le modele de la race est choisi a chaque image (modeles prets a la construction, partages par toutes les betes de la race :
 * pose initiale remise a chaque image, {@link PetModel#restore}). Echelle de la race (petits : moitie), ombre a la taille, collier teint.
 */
public class PetRenderer<T extends Mob> extends MobRenderer<T, PetModel<T>> {

    public static final ModelLayerLocation[] DOG_LAYERS = new ModelLayerLocation[GeneratedPetShapes.DOGS.length];
    public static final ModelLayerLocation[] CAT_LAYERS = new ModelLayerLocation[GeneratedPetShapes.CATS.length];
    /** Chien sauvage : derniere silhouette des chiens. */
    public static final int WILD = GeneratedPetShapes.DOGS.length - 1;

    static {
        for (int i = 0; i < DOG_LAYERS.length; i++) {
            DOG_LAYERS[i] = new ModelLayerLocation(Pioneers.rl("pet_" + GeneratedPetShapes.DOGS[i].id()), "main");
        }
        for (int i = 0; i < CAT_LAYERS.length; i++) {
            CAT_LAYERS[i] = new ModelLayerLocation(Pioneers.rl("pet_" + GeneratedPetShapes.CATS[i].id() + "_cat"), "main");
        }
    }

    final boolean cat;
    final PetModel<T>[] models;
    final ResourceLocation[] textures;
    static final ResourceLocation WILD_ANGRY = Pioneers.rl("textures/entity/pet/wild_angry.png");

    @SuppressWarnings("unchecked")
    public PetRenderer(EntityRendererProvider.Context ctx, boolean cat) {
        super(ctx, model(ctx, cat, 0), 0.5F);
        this.cat = cat;
        GeneratedPetShapes.Shape[] shapes = cat ? GeneratedPetShapes.CATS : GeneratedPetShapes.DOGS;
        models = new PetModel[shapes.length];
        textures = new ResourceLocation[shapes.length];
        for (int i = 0; i < shapes.length; i++) {
            models[i] = i == 0 ? this.model : model(ctx, cat, i);
            textures[i] = Pioneers.rl("textures/entity/pet/" + shapes[i].id() + ".png");
        }
        addLayer(new Collar<>(this));
    }

    static <T extends Mob> PetModel<T> model(EntityRendererProvider.Context ctx, boolean cat, int i) {
        return new PetModel<>(ctx.bakeLayer(cat ? CAT_LAYERS[i] : DOG_LAYERS[i]), cat ? GeneratedPetShapes.CATS[i] : GeneratedPetShapes.DOGS[i]);
    }

    /** Silhouette d'une bete : race du mod, chien sauvage, chat vanilla d'apres sa variante. */
    public int index(T e) {
        if (e instanceof Pet p) {
            return p.breed();
        }
        if (e instanceof Cat c) {
            ResourceLocation v = BuiltInRegistries.CAT_VARIANT.getKey(c.getVariant());
            return PiPets.catBreedOfVariant(v == null ? null : v.toString());
        }
        return cat ? 0 : WILD;
    }

    public float breedScale(int i) {
        return cat ? GeneratedPets.CATS[i].scale() : GeneratedPets.DOGS[i].scale();
    }

    @Override
    public void render(@NotNull T e, float yaw, float pt, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light) {
        int i = index(e);
        this.model = models[i];
        this.shadowRadius = 0.35F * breedScale(i) * (e.isBaby() ? 0.5F : 1F) * (cat ? 0.8F : 1F);
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    protected void scale(@NotNull T e, @NotNull PoseStack pose, float pt) {
        float s = breedScale(index(e)) * (e.isBaby() ? 0.5F : 1F);
        pose.scale(s, s, s);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull T e) {
        int i = index(e);
        if (!cat && i == WILD && e instanceof Wolf w && w.isAngry()) {
            return WILD_ANGRY;
        }
        return textures[i];
    }

    /** Collier des betes apprivoisees : la boite du collier seule, teinte de la couleur du collier (boucle de laiton comprise dans la teinte). */
    static final class Collar<T extends Mob> extends RenderLayer<T, PetModel<T>> {

        Collar(RenderLayerParent<T, PetModel<T>> parent) {
            super(parent);
        }

        @Override
        public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull T e, float limb, float amount, float pt,
              float age, float headYaw, float headPitch) {
            if (!(e instanceof TamableAnimal t) || !t.isTame() || e.isInvisible()) {
                return;
            }
            DyeColor c = e instanceof Wolf w ? w.getCollarColor() : e instanceof Cat ct ? ct.getCollarColor() : DyeColor.RED;
            float[] rgb = c.getTextureDiffuseColors();
            PetModel<T> m = getParentModel();
            m.collarOnly = true;
            try {
                m.setupAnim(e, limb, amount, age, headYaw, headPitch);
                m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(e))), light,
                      LivingEntityRenderer.getOverlayCoords(e, 0F), rgb[0], rgb[1], rgb[2], 1F);
            } finally {
                m.collarOnly = false;
            }
        }
    }
}
