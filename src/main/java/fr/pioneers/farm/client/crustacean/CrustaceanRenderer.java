package fr.pioneers.farm.client.crustacean;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.farm.crustacean.Crustacean;
import fr.pioneers.farm.crustacean.GeneratedCrustaceans;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/** 1.4 : rendu des crustaces : silhouette et carapace de l'espece, taille de l'individu ; les crabes marchent de cote. */
public class CrustaceanRenderer extends MobRenderer<Crustacean, CrustaceanModel> {

    public static final ModelLayerLocation[] LAYERS = new ModelLayerLocation[GeneratedCrustaceans.SHAPES.length];

    static {
        for (int i = 0; i < LAYERS.length; i++) {
            LAYERS[i] = new ModelLayerLocation(Pioneers.rl("crustacean_" + GeneratedCrustaceans.SHAPES[i]), "main");
        }
    }

    private final CrustaceanModel[] models = new CrustaceanModel[LAYERS.length];
    private static final ResourceLocation[] TEX = new ResourceLocation[GeneratedCrustaceans.SPECIES.length];

    public CrustaceanRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CrustaceanModel(ctx.bakeLayer(LAYERS[0]), 0), 0.3F);
        for (int i = 0; i < LAYERS.length; i++) {
            models[i] = new CrustaceanModel(ctx.bakeLayer(LAYERS[i]), i);
        }
    }

    @Override
    public void render(@NotNull Crustacean c, float yaw, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light) {
        model = models[Mth.clamp(c.def().shape(), 0, models.length - 1)];
        shadowRadius = 0.3F * Math.min(2F, c.scale());
        super.render(c, yaw, partial, pose, buffers, light);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull Crustacean c) {
        int s = Mth.clamp(c.species(), 0, TEX.length - 1);
        if (TEX[s] == null) {
            TEX[s] = Pioneers.rl("textures/entity/crustacean/" + GeneratedCrustaceans.SPECIES[s].id() + ".png");
        }
        return TEX[s];
    }

    @Override
    protected void scale(@NotNull Crustacean c, @NotNull PoseStack pose, float partial) {
        float s = c.scale();
        pose.scale(s, s, s);
    }

    @Override
    protected void setupRotations(@NotNull Crustacean c, @NotNull PoseStack pose, float bob, float bodyYaw, float partial) {
        super.setupRotations(c, pose, bob, bodyYaw, partial);
        if (c.def().sideways()) {
            pose.mulPose(Axis.YP.rotationDegrees(90F));
        }
    }
}
