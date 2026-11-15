package fr.pioneers.client.naval;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.naval.ShipItem;
import fr.pioneers.naval.ShipKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/** 0.20.11 : icone 3D d'un navire : son maillage reduit pour tenir dans un bloc (en diagonale dans l'inventaire). */
public class ShipItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static ShipItemRenderer instance;

    private ShipItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static ShipItemRenderer instance() {
        if (instance == null) {
            instance = new ShipItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext context, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers,
          int light, int overlay) {
        if (!(stack.getItem() instanceof ShipItem item)) {
            return;
        }
        pose.pushPose();
        float s = 1.28F / ShipKind.length(item.tier);
        pose.translate(0.5, 0.32, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(45));
        pose.scale(s, s, s);
        ShipRenderer.draw(pose, buffers, light, item.kind, item.tier, 0.7F, 0, 0);
        pose.popPose();
    }
}
