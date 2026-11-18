package fr.pioneers.tech.energy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.tech.kinetics.client.KDraw;
import fr.pioneers.core.RenderClock;
import fr.pioneers.tech.energy.EnergyKind;
import fr.pioneers.tech.energy.WindRotorBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9.2 (retours de Guillaume) : rotor d'eolienne en main, au sol et en cadre : le dessin du jeu ({@link EnergyVisuals#windRotor}) reduit
 * pour tenir dans un bloc (diametre ~ 22 px), moyeu au centre ; il tourne doucement en main. L'inventaire garde l'icone plate.
 */
public class WindRotorItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static WindRotorItemRenderer instance;

    private WindRotorItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static WindRotorItemRenderer instance() {
        if (instance == null) {
            instance = new WindRotorItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext context, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers,
          int light, int overlay) {
        int tier = stack.getItem() instanceof BlockItem b && b.getBlock() instanceof WindRotorBlock r ? r.tier : 0;
        float length = EnergyKind.WIND_BLADE[tier];
        float scale = 11F / (length + 3F);
        Minecraft mc = Minecraft.getInstance();
        float angle = mc.level == null || context == ItemDisplayContext.GUI ? 15 : (float) (RenderClock.time(mc.getFrameTime()) * 2 % 720);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.scale(scale, scale, scale);
        pose.translate(-0.5, -EnergyVisuals.HUB_Y / 16F, -EnergyVisuals.HUB_Z / 16F);
        KDraw d = new KDraw(pose, buffers, light, overlay);
        EnergyVisuals.windRotor(d, angle, length, tier);
        pose.popPose();
    }
}
