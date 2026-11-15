package fr.pioneers.farm.client.herd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import net.minecraft.client.model.LlamaModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LlamaRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 28) : rendu d'un lama de RACE : robe de la race ({@code textures/entity/herd/llama/<race>.png}, T:farm2/herd.py
 * llama_texture) et echelle de la race ; tapis et coffre vanilla.
 * 1.0.4-beta (lot 33, Guillaume : « Les modeles de lama et de chevaux sont a ameliorer selon la race ») : MODELE DE LA RACE
 * ({@link HerdShapes#llamaLayer}, memes noms de parties que LlamaModel : marche, tete, coffres vanilla) : corps, encolure, museau, pattes a
 * leurs cotes, oreilles en banane, toison ou meches ; le TAPIS est le modele vanilla ajuste boite par boite ({@link Decor}).
 */
public class HerdLlamaRenderer extends LlamaRenderer {

    private final ResourceLocation texture;
    private final float scale;
    @Nullable
    private final GeneratedHerdShapes.LlamaShape shape;

    public HerdLlamaRenderer(EntityRendererProvider.Context ctx, String breed, float scale) {
        super(ctx, ModelLayers.LLAMA);
        this.texture = Pioneers.rl("textures/entity/herd/llama/" + breed + ".png");
        this.scale = scale;
        this.shape = HerdShapes.llama(breed);
        if (shape != null) {
            model = new LlamaModel<>(HerdShapes.llamaLayer(shape).bakeRoot());
            layers.clear();
            ModelPart decor = ctx.bakeLayer(ModelLayers.LLAMA_DECOR);
            addLayer(new Decor(this, decor, shape));
        }
    }

    @Override
    protected void scale(@NotNull Llama llama, @NotNull PoseStack pose, float partialTicks) {
        pose.scale(scale, scale, scale);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull Llama llama) {
        return texture;
    }

    /** Tapis (et tapis de marchand ambulant) : modele vanilla du tapis ajuste aux boites de la race apres son animation. */
    static final class Decor extends RenderLayer<Llama, LlamaModel<Llama>> {

        private static final ResourceLocation TRADER = new ResourceLocation("textures/entity/llama/decor/trader_llama.png");
        private final LlamaModel<Llama> decor;
        private final ModelPart root;
        private final GeneratedHerdShapes.LlamaShape shape;

        Decor(RenderLayerParent<Llama, LlamaModel<Llama>> parent, ModelPart root, GeneratedHerdShapes.LlamaShape shape) {
            super(parent);
            this.decor = new LlamaModel<>(root);
            this.root = root;
            this.shape = shape;
        }

        @Override
        public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull Llama llama, float limbSwing,
              float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            DyeColor swag = llama.getSwag();
            ResourceLocation tex;
            if (swag != null) {
                tex = new ResourceLocation("textures/entity/llama/decor/" + swag.getName() + ".png");
            } else if (llama.isTraderLlama()) {
                tex = TRADER;
            } else {
                return;
            }
            getParentModel().copyPropertiesTo(decor);
            decor.setupAnim(llama, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            HerdShapes.fitDecor(root, shape);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
            decor.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        }
    }
}
