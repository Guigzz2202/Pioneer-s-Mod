package fr.pioneers.logistics.robotics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.logistics.robotics.RobotUnitEntity;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.logistics.robotics.UnitTier;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Rendu d'une unite : maquette au liseré de son tier, feux et visiere lumineux, outil de la tache, cargaison visible, et rayon laser
 * vers le bloc travaille (abattage, minage, terrassement, pavage).
 */
public class RobotUnitRenderer extends MobRenderer<RobotUnitEntity, RobotUnitModel> {

    private final UnitKind kind;

    public RobotUnitRenderer(EntityRendererProvider.Context context, UnitKind kind) {
        super(context, new RobotUnitModel(context.bakeLayer(RobotUnitModels.layer(kind)), kind), kind == UnitKind.ROVER ? 0.8F : 0.4F);
        this.kind = kind;
        addLayer(new GlowLayer(this, kind));
        addLayer(new ToolLayer(this));
    }

    /** 0.20.46 (audit) : textures calculees une fois (une ResourceLocation neuve par unite et par image avant). */
    private static final ResourceLocation[][] TEXTURES = new ResourceLocation[UnitKind.values().length][UnitTier.values().length];
    private static final ResourceLocation[] GLOWS = new ResourceLocation[UnitKind.values().length];

    public static ResourceLocation texture(UnitKind kind, UnitTier tier) {
        ResourceLocation id = TEXTURES[kind.ordinal()][tier.ordinal()];
        if (id == null) {
            id = Pioneers.rl("textures/entity/robotics/" + kind.id() + "_mk" + tier.level() + ".png");
            TEXTURES[kind.ordinal()][tier.ordinal()] = id;
        }
        return id;
    }

    public static ResourceLocation glow(UnitKind kind) {
        ResourceLocation id = GLOWS[kind.ordinal()];
        if (id == null) {
            id = Pioneers.rl("textures/entity/robotics/" + kind.id() + "_glow.png");
            GLOWS[kind.ordinal()] = id;
        }
        return id;
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull RobotUnitEntity unit) {
        return texture(kind, unit.getTier());
    }

    @Override
    public void render(@NotNull RobotUnitEntity unit, float yaw, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light) {
        super.render(unit, yaw, partialTick, pose, buffers, light);
        Optional<BlockPos> beam = unit.getBeam();
        if (unit.isWorking() && beam.isPresent()) {
            drawBeam(unit, beam.get(), partialTick, pose, buffers);
        }
        if (unit.isSleeping()) {
            drawSleep(unit, partialTick, pose, buffers);
        }
    }

    /** « z Z z » qui montent au-dessus d'une tortue endormie (mode repos). */
    private void drawSleep(RobotUnitEntity unit, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Font font = getFont();
        float time = unit.tickCount + partialTick;
        for (int i = 0; i < 3; i++) {
            float phase = (time * 0.012F + i / 3F) % 1F;
            int alpha = (int) (255 * Math.sin(phase * Math.PI));
            if (alpha < 12) {
                continue;
            }
            pose.pushPose();
            pose.translate(0.15 + Math.sin(phase * 6 + i) * 0.12, unit.getBbHeight() + 0.1 + phase * 0.7, 0);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            float size = 0.014F + phase * 0.012F;
            pose.scale(-size, -size, size);
            String text = i == 1 ? "Z" : "z";
            font.drawInBatch(text, -font.width(text) / 2F, 0, (alpha << 24) | 0xBFE8FF, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0,
                  LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }

    /** Rayon : deux rubans croises, de l'outil jusqu'au centre du bloc, couleur de la tache, qui pulse. */
    private void drawBeam(RobotUnitEntity unit, BlockPos target, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Vec3 origin = unit.getPosition(partialTick);
        double startY = switch (kind) {
            case DRONE -> 0.05;
            case ROBOT -> 1.1;
            case ROVER -> 0.9;
            case TURTLE -> 0.5;
        };
        Vec3 from = new Vec3(0, startY, 0);
        Vec3 to = Vec3.atCenterOf(target).subtract(origin);
        int color = unit.getTask().color;
        float r = (color >> 16 & 255) / 255F, g = (color >> 8 & 255) / 255F, b = (color & 255) / 255F;
        float time = unit.tickCount + partialTick;
        float pulse = 0.55F + 0.45F * (float) Math.sin(time * 0.9);
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        Vec3 dir = to.subtract(from);
        Vec3 side1 = dir.cross(new Vec3(0, 1, 0));
        if (side1.lengthSqr() < 1e-4) {
            side1 = new Vec3(1, 0, 0);
        }
        side1 = side1.normalize().scale(0.05);
        Vec3 side2 = dir.cross(side1).normalize().scale(0.05);
        for (Vec3 side : new Vec3[]{side1, side2}) {
            quad(buffer, matrix, from, to, side.scale(2.2), r, g, b, 0.18F * pulse);
            quad(buffer, matrix, from, to, side, 1, 1, 1, 0.55F * pulse);
        }
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 side, float r, float g, float bl, float alpha) {
        vertex(buffer, matrix, a.add(side), r, g, bl, alpha);
        vertex(buffer, matrix, b.add(side), r, g, bl, alpha);
        vertex(buffer, matrix, b.subtract(side), r, g, bl, alpha);
        vertex(buffer, matrix, a.subtract(side), r, g, bl, alpha);
        vertex(buffer, matrix, a.subtract(side), r, g, bl, alpha);
        vertex(buffer, matrix, b.subtract(side), r, g, bl, alpha);
        vertex(buffer, matrix, b.add(side), r, g, bl, alpha);
        vertex(buffer, matrix, a.add(side), r, g, bl, alpha);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 p, float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }

    /** Feux, visiere et cellules : texture lumineuse en rendu additif (visible la nuit). */
    private static class GlowLayer extends RenderLayer<RobotUnitEntity, RobotUnitModel> {

        private final RenderType type;

        GlowLayer(MobRenderer<RobotUnitEntity, RobotUnitModel> parent, UnitKind kind) {
            super(parent);
            this.type = RenderType.eyes(glow(kind));
        }

        @Override
        public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull RobotUnitEntity unit, float limbSwing,
              float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
            getParentModel().renderToBuffer(pose, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        }
    }

    /** Outil de la tache et cargaison. */
    private static class ToolLayer extends RenderLayer<RobotUnitEntity, RobotUnitModel> {

        ToolLayer(MobRenderer<RobotUnitEntity, RobotUnitModel> parent) {
            super(parent);
        }

        @Override
        public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull RobotUnitEntity unit, float limbSwing,
              float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
            RobotUnitModel model = getParentModel();
            ItemStack tool = unit.getMainHandItem();
            if (!tool.isEmpty() && model.kind() != UnitKind.DRONE) {
                pose.pushPose();
                model.translateToTool(pose);
                if (model.kind() == UnitKind.ROBOT) {
                    pose.mulPose(Axis.XP.rotationDegrees(-90));
                    pose.mulPose(Axis.YP.rotationDegrees(180));
                    pose.scale(0.6F, 0.6F, 0.6F);
                } else {
                    pose.scale(0.5F, 0.5F, 0.5F);
                }
                Minecraft.getInstance().getItemRenderer().renderStatic(tool, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, light,
                      OverlayTexture.NO_OVERLAY, pose, buffers, unit.level(), unit.getId());
                pose.popPose();
            }
            renderCargo(model, unit.getShownCargo(), unit.getCargoFill(), pose, buffers, light, unit);
        }
    }

    /** Cargaison : caisse sous le drone, piles sur le plateau du rover, sac sur le dos du robot. */
    static void renderCargo(RobotUnitModel model, ItemStack shown, float fill, PoseStack pose, MultiBufferSource buffers, int light,
          @Nullable RobotUnitEntity unit) {
        if (shown.isEmpty()) {
            return;
        }
        pose.pushPose();
        model.translateToCargo(pose);
        switch (model.kind()) {
            case DRONE -> {
                pose.translate(0, 0.3, 0);
                pose.scale(0.38F, 0.38F, 0.38F);
                one(shown, pose, buffers, light, unit, 0);
            }
            case ROVER -> {
                //Jusqu'a six piles, plus il y a de cargaison plus le plateau se remplit
                int count = Math.max(1, Math.min(6, Math.round(fill * 6)));
                for (int i = 0; i < count; i++) {
                    pose.pushPose();
                    pose.translate((i % 2 == 0 ? -0.14 : 0.14), -0.12 - (i / 2) * 0.0, -0.12 + (i / 2) * 0.16);
                    pose.mulPose(Axis.XP.rotationDegrees(90));
                    pose.scale(0.32F, 0.32F, 0.32F);
                    one(shown, pose, buffers, light, unit, i);
                    pose.popPose();
                }
            }
            case ROBOT -> {
                pose.translate(0, -0.5, 0.3);
                pose.scale(0.3F, 0.3F, 0.3F);
                one(shown, pose, buffers, light, unit, 0);
            }
            case TURTLE -> {
                //Sur la carapace
                pose.translate(0, -0.5, 0.05);
                pose.mulPose(Axis.XP.rotationDegrees(90));
                pose.scale(0.3F, 0.3F, 0.3F);
                one(shown, pose, buffers, light, unit, 0);
            }
        }
        pose.popPose();
    }

    private static void one(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, @Nullable RobotUnitEntity unit,
          int seed) {
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
              unit == null ? null : unit.level(), unit == null ? seed : unit.getId() + seed);
    }
}
