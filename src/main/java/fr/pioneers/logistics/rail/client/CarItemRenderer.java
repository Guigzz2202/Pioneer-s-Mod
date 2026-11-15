package fr.pioneers.logistics.rail.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.logistics.rail.train.RailCarItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.0 : objet 3D d'un vehicule (son maillage, incline comme un bloc par le modele d'objet). 1.6 : seulement en main, au sol et dans les
 * cadres : dans l'inventaire, le modele d'objet (forge:separate_transforms, T:rail/data.py) montre une icone plate dessinee d'apres ce meme
 * maillage et cette meme transformation (T:rail/item_icons.py, a relancer apres toute retouche de CarModels).
 */
public class CarItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static CarItemRenderer instance;

    private CarItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static CarItemRenderer instance() {
        if (instance == null) {
            instance = new CarItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext context, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers,
          int light, int overlay) {
        if (!(stack.getItem() instanceof RailCarItem item)) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5, 0.06, 0.5);
        float s = 0.92F;
        pose.scale(s, s, s);
        CarRenderer.draw(pose, buffers, light, item.type, item.tier, 0.6F, 0, 0, 0, 0);
        pose.popPose();
    }
}
