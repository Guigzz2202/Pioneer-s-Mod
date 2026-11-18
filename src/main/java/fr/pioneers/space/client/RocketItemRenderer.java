package fr.pioneers.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.space.ItemRocket;
import fr.pioneers.space.RocketTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Rendu 3D de l'objet fusee : la vraie maquette de la fusee, reduite a la taille d'un bloc.
 */
public class RocketItemRenderer extends BlockEntityWithoutLevelRenderer {

    public static final RocketItemRenderer INSTANCE = new RocketItemRenderer();
    private RocketModels models;

    private RocketItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
        models = null;
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext context, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers,
          int light, int overlay) {
        if (models == null) {
            models = new RocketModels(Minecraft.getInstance().getEntityModels()::bakeLayer);
        }
        RocketTier tier = stack.getItem() instanceof ItemRocket rocket ? rocket.getTier() : RocketTier.TIER_1;
        //Hauteur de la fusee -> 1 bloc, centree dans le cube de l'objet
        float scale = 1F / tier.height;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.scale(-scale, -scale, scale);
        pose.translate(0, -1.501, 0);
        models.render(tier, pose, buffers, RenderType::entityCutoutNoCull, light, overlay);
        pose.popPose();
    }
}
