package fr.pioneers.society.economy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.Pioneers;
import fr.pioneers.society.economy.peddler.PeddlerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : rendu du Colporteur. Trois tenues ({@link PeddlerEntity#variant()} : textures
 * {@code entity/peddler/peddler_<n>.png}, 64 x 128), meme echelle que le villageois.
 */
public class PeddlerRenderer extends MobRenderer<PeddlerEntity, PeddlerModel> {

    private static final ResourceLocation[] TEXTURES = {
          Pioneers.rl("textures/entity/peddler/peddler_0.png"),
          Pioneers.rl("textures/entity/peddler/peddler_1.png"),
          Pioneers.rl("textures/entity/peddler/peddler_2.png")
    };

    public PeddlerRenderer(EntityRendererProvider.Context context) {
        super(context, new PeddlerModel(context.bakeLayer(PeddlerClient.LAYER)), 0.5F);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull PeddlerEntity entity) {
        return TEXTURES[Math.floorMod(entity.variant(), TEXTURES.length)];
    }

    @Override
    protected void scale(@NotNull PeddlerEntity entity, @NotNull PoseStack pose, float partialTick) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }
}
