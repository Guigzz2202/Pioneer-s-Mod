package fr.pioneers.client.robotics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.core.RenderClock;
import fr.pioneers.robotics.ItemRobotUnit;
import fr.pioneers.robotics.UnitKind;
import fr.pioneers.robotics.UnitTier;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Objets des unites : leur maquette 3D, animee doucement dans l'inventaire (rotors, regard, antenne). Sert aussi aux unites garees
 * sur leur base ({@link #renderUnit}).
 */
public class RobotUnitItemRenderer extends BlockEntityWithoutLevelRenderer {

    public static final RobotUnitItemRenderer INSTANCE = new RobotUnitItemRenderer();
    private static final Map<UnitKind, RobotUnitModel> MODELS = new EnumMap<>(UnitKind.class);

    private RobotUnitItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
        MODELS.clear();
    }

    static RobotUnitModel model(UnitKind kind) {
        return MODELS.computeIfAbsent(kind, k -> {
            EntityModelSet models = Minecraft.getInstance().getEntityModels();
            return new RobotUnitModel(models.bakeLayer(RobotUnitModels.layer(k)), k);
        });
    }

    /** Hauteur (blocs) et echelle d'affichage de chaque unite. */
    private static float height(UnitKind kind) {
        return switch (kind) {
            case DRONE -> 0.55F;
            case ROBOT -> 1.6F;
            case ROVER -> 1.1F;
            case TURTLE -> 0.65F;
        };
    }

    private static float itemScale(UnitKind kind) {
        return switch (kind) {
            case DRONE -> 0.8F;
            case ROBOT -> 0.6F;
            case ROVER -> 0.62F;
            case TURTLE -> 0.75F;
        };
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext context, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers,
          int light, int overlay) {
        if (!(stack.getItem() instanceof ItemRobotUnit unit)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float time = minecraft.level == null ? 0 : (float) RenderClock.time(minecraft.getFrameTime());   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        float scale = itemScale(unit.kind);
        pose.pushPose();
        pose.translate(0.5, 0.5 - height(unit.kind) * scale / 2, 0.5);
        pose.scale(scale, scale, scale);
        renderUnit(unit.kind, unit.tier, context == ItemDisplayContext.GUI ? time : 0, false, pose, buffers, light);
        pose.popPose();
    }

    /** Dessine une unite, pieds a l'origine de la pile de pose, face vers -z. */
    public static void renderUnit(UnitKind kind, UnitTier tier, float time, boolean working, PoseStack pose, MultiBufferSource buffers, int light) {
        renderUnit(kind, tier, time, 0, 0, working, pose, buffers, light);
    }

    /** Variante avec la marche (balancement {@code swing}, amplitude {@code amount}) : machines qui deplacent une unite. */
    public static void renderUnit(UnitKind kind, UnitTier tier, float time, float swing, float amount, boolean working, PoseStack pose,
          MultiBufferSource buffers, int light) {
        RobotUnitModel model = model(kind);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501, 0);
        model.animate(time, swing, amount, 0, 0, working, 0, 0);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(RobotUnitRenderer.texture(kind, tier))), light, OverlayTexture.NO_OVERLAY,
              1, 1, 1, 1);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(RobotUnitRenderer.glow(kind))), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
              1, 1, 1, 1);
        pose.popPose();
    }
}
