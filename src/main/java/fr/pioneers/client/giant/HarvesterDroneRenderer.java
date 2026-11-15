package fr.pioneers.client.giant;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.client.robotics.RobotUnitItemRenderer;
import fr.pioneers.robotics.UnitKind;
import fr.pioneers.space.giant.HarvesterDroneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Drone de la baie de drones : le drone robotique de son tier, avec un leger roulis dans les virages et un balancement en vol
 * stationnaire.
 */
public class HarvesterDroneRenderer extends EntityRenderer<HarvesterDroneEntity> {

    private static final ResourceLocation TEXTURE = Pioneers.rl("textures/entity/robotics/drone_mk1.png");

    public HarvesterDroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.3F;
    }

    @Override
    public void render(@NotNull HarvesterDroneEntity drone, float yaw, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffer,
          int light) {
        float time = drone.tickCount + partialTick;
        pose.pushPose();
        pose.translate(0, Mth.sin(time * 0.15F) * 0.04, 0);
        //Meme orientation que les unites robotiques (renderUnit ajoute le demi-tour)
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, drone.yRotO, drone.getYRot())));
        float bank = (float) Mth.clamp(drone.getDeltaMovement().horizontalDistance() * 30, 0, 14);
        pose.mulPose(Axis.XP.rotationDegrees(bank));
        RobotUnitItemRenderer.renderUnit(UnitKind.DRONE, drone.getTier(), time, drone.getPhase() == HarvesterDroneEntity.Phase.HARVEST, pose, buffer,
              light);
        pose.popPose();
        super.render(drone, yaw, partialTick, pose, buffer, light);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull HarvesterDroneEntity drone) {
        return TEXTURE;
    }
}
