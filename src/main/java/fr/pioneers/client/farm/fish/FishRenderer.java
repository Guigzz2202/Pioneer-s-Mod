package fr.pioneers.client.farm.fish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.farm.fish.GeneratedFish;
import fr.pioneers.farm.fish.PiFish;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Rendu des poissons du mod : modele de l'espece (1.0.3-beta : un modele par espece, {@link FishModels} ; avant : 10 silhouettes partagees),
 * robe de l'espece, taille de l'individu ; nage et sursauts hors de l'eau.
 */
public class FishRenderer extends MobRenderer<PiFish, FishModel> {

    public static final ModelLayerLocation[] LAYERS = new ModelLayerLocation[GeneratedFish.SPECIES.length];

    static {
        for (int i = 0; i < LAYERS.length; i++) {
            LAYERS[i] = new ModelLayerLocation(Pioneers.rl("fish_" + GeneratedFish.SPECIES[i].id()), "main");
        }
    }

    private final FishModel[] models = new FishModel[LAYERS.length];
    private static final ResourceLocation[] TEX = new ResourceLocation[GeneratedFish.SPECIES.length];

    public FishRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new FishModel(ctx.bakeLayer(LAYERS[0]), 0), 0.3F);
        for (int i = 0; i < LAYERS.length; i++) {
            models[i] = new FishModel(ctx.bakeLayer(LAYERS[i]), i);
        }
    }

    @Override
    public void render(PiFish fish, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        model = models[Mth.clamp(fish.species(), 0, models.length - 1)];
        shadowRadius = 0.25F * Math.min(2.5F, fish.scale());
        super.render(fish, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PiFish fish) {
        int s = fish.species();
        if (s < 0 || s >= TEX.length) {
            s = 0;
        }
        if (TEX[s] == null) {
            TEX[s] = Pioneers.rl("textures/entity/fish/" + GeneratedFish.SPECIES[s].id() + ".png");
        }
        return TEX[s];
    }

    @Override
    protected void scale(PiFish fish, PoseStack pose, float partial) {
        float s = fish.scale();
        pose.scale(s, s, s);
    }

    @Override
    protected void setupRotations(PiFish fish, PoseStack pose, float bob, float bodyYaw, float partial) {
        super.setupRotations(fish, pose, bob, bodyYaw, partial);
        pose.mulPose(Axis.YP.rotationDegrees(4.3F * Mth.sin(0.6F * bob)));
        if (!fish.isInWater()) {
            pose.translate(0.1F, 0.1F, -0.1F);
            pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
        }
    }
}
