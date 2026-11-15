package fr.pioneers.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.client.robotics.RobotUnitModels;
import fr.pioneers.core.RenderClock;
import fr.pioneers.robotics.UnitTier;
import fr.pioneers.space.mining.Deposit;
import fr.pioneers.tile.TileEntityMiningOutpost.Mode;
import fr.pioneers.tile.TileEntityMiningOutpost;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * Base d'extraction : la parabole du radar qui tourne, les balises des gisements reperes (faisceau a la couleur du filon
 * et minerai holographique) et le rover qui sort du hangar, roule sur le relief, fore et revient.
 */
public class RenderMiningOutpost implements BlockEntityRenderer<TileEntityMiningOutpost> {

    public static final ResourceLocation DISH = Pioneers.rl("block/prospecting_radar_dish");
    private static final float ROVER_SCALE = 0.8F;
    private static final ResourceLocation[] DRILL_TEXTURES = new ResourceLocation[UnitTier.values().length];
    private static final ResourceLocation DRILL_GLOW = Pioneers.rl("textures/entity/robotics/rover_drill_glow.png");

    static {
        for (UnitTier tier : UnitTier.values()) {
            DRILL_TEXTURES[tier.ordinal()] = Pioneers.rl("textures/entity/robotics/rover_drill_mk" + tier.level() + ".png");
        }
    }

    private final RoverDrillModel rover;
    /** 0.20.46 (audit) : types de rendu du rover par tier et de sa lueur, calcules une fois. */
    private final RenderType[] drillTypes = new RenderType[DRILL_TEXTURES.length];
    private final RenderType glowType = RenderType.eyes(DRILL_GLOW);

    public RenderMiningOutpost(BlockEntityRendererProvider.Context context) {
        rover = new RoverDrillModel(context.bakeLayer(RobotUnitModels.ROVER_DRILL));
        for (int i = 0; i < DRILL_TEXTURES.length; i++) {
            drillTypes[i] = rover.renderType(DRILL_TEXTURES[i]);
        }
    }

    @Override
    public void render(@NotNull TileEntityMiningOutpost tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        BlockPos center = tile.getClientCenter();
        Level level = tile.getLevel();
        if (center == null || level == null) {
            return;
        }
        float time = (float) RenderClock.time(partialTick);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        BlockPos origin = tile.getBlockPos();
        Mode mode = tile.getClientMode();
        pose.pushPose();
        pose.translate(center.getX() - origin.getX(), center.getY() - origin.getY(), center.getZ() - origin.getZ());

        //Parabole du radar (rapide pendant une prospection)
        pose.pushPose();
        double[] radar = tile.radarOffset();
        pose.translate(radar[0] + 0.5, radar[1] + 0.75, radar[2] + 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(mode == Mode.SURVEYING ? time * 14 : time * 1.2F));
        pose.translate(-0.5, 0, -0.5);
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        renderer.renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null, Minecraft.getInstance().getModelManager().getModel(DISH),
              1, 1, 1, LevelRenderer.getLightColor(level, BlockPos.containing(center.getX() + radar[0], center.getY() + radar[1] + 1, center.getZ() + radar[2])), OverlayTexture.NO_OVERLAY, ModelData.EMPTY,
              RenderType.cutout());
        pose.popPose();

        //Balises des gisements
        List<Deposit> deposits = tile.getClientDeposits();
        for (int i = 0; i < deposits.size(); i++) {
            Deposit deposit = deposits.get(i);
            if (deposit.isDepleted()) {
                continue;
            }
            int dx = deposit.pos.getX() - center.getX(), dz = deposit.pos.getZ() - center.getZ();
            float ground = (float) groundAt(tile, level, center, dx, dz);
            boolean target = i == tile.getClientTarget();
            int color = deposit.vein.color;
            float[] rgb = {(color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F};
            float fill = deposit.remaining / (float) Math.max(1, deposit.total);
            pose.pushPose();
            pose.translate(dx, ground, dz);
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1, level.getGameTime(), 0,
                  target ? 12 : 3 + Math.round(4 * fill), rgb, 0.05F, target ? 0.14F : 0.09F);
            pose.translate(0.5, 1.3 + Mth.sin(time * 0.08F + i) * 0.08, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(time * 3 + i * 40));
            pose.scale(0.55F, 0.55F, 0.55F);
            Minecraft.getInstance().getItemRenderer().renderStatic(deposit.vein.getIcon(), ItemDisplayContext.GROUND, 0xF000F0, OverlayTexture.NO_OVERLAY,
                  pose, buffers, level, i);
            pose.popPose();
        }

        //Rover
        if (tile.hasClientRover()) {
            //0.20.46 (audit) : particules une fois par tick de jeu (et non par image)
            boolean fx = FxTicks.firstFrame(tile, level);
            //1.0 : un rover par dock du garage (convoi)
            for (int i = 0; i < tile.getClientCrew(); i++) {
                renderRover(tile, level, center, mode, time, pose, buffers, i, fx);
            }
        }
        pose.popPose();
    }

    private void renderRover(TileEntityMiningOutpost tile, Level level, BlockPos center, Mode mode, float time, PoseStack pose, MultiBufferSource buffers,
          int index, boolean fx) {
        double[] state = tile.getRoverPose(time, index);
        double x = state[0], z = state[1], dirX = state[2], dirZ = state[3];
        boolean moving = state[4] > 0;
        //Altitude : la route calculee par le serveur (le rover suit le sol et passe dans ce qu'il a creuse)
        double distance = state[5];
        boolean outside = mode == Mode.OUTBOUND || mode == Mode.DRILLING || mode == Mode.RETURNING;
        double y = !outside ? 1 : tile.hasClientRoad() ? tile.getRoadHeight(distance) : groundAt(tile, level, center, x, z);
        //Sur le dock : leger rehaussement
        double fromDock = outside ? distance : 0;
        if (fromDock < 0.7) {
            y += 3 / 16F * (1 - fromDock / 0.7);
        }
        float pitch = 0;
        if (outside && !tile.hasClientRoad()) {
            double ahead = groundAt(tile, level, center, x + dirX * 0.7, z + dirZ * 0.7), behind = groundAt(tile, level, center, x - dirX * 0.7, z - dirZ * 0.7);
            pitch = (float) Math.atan2(ahead - behind, 1.4);
        } else if (outside) {
            //Au retour le rover roule en sens inverse sur la meme route
            double sign = mode == Mode.RETURNING ? -1 : 1;
            double ahead = tile.getRoadHeight(distance + 0.7 * sign), behind = tile.getRoadHeight(distance - 0.7 * sign);
            pitch = (float) Math.atan2(ahead - behind, 1.4);
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dirX, dirZ));
        float since = time - tile.getClientPhaseStart();
        float armDown = switch (mode) {
            case DRILLING -> Mth.clamp(since / 15F, 0, 1);
            case RETURNING -> 1 - Mth.clamp(since / 10F, 0, 1);
            default -> 0;
        };
        float drillSpin = mode == Mode.DRILLING ? time * 0.9F : 0;
        float wheelSpin = (float) (state[5] / (2.5 / 16 * ROVER_SCALE));
        if (mode == Mode.RETURNING) {
            wheelSpin = -wheelSpin;
        }
        float look = Mth.sin(time * 0.03F) * 0.7F;
        float bob = moving ? Mth.sin(time * 1.3F) * 0.012F : 0;

        pose.pushPose();
        pose.translate(x + 0.5, y + bob, z + 0.5);
        //Maquette tournee vers -z : demi-tour pour rouler vers l'avant
        pose.mulPose(Axis.YP.rotationDegrees(yaw + 180));
        pose.mulPose(Axis.XP.rotation(-pitch));
        pose.scale(ROVER_SCALE, ROVER_SCALE, ROVER_SCALE);
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501, 0);
        rover.setPose(-wheelSpin, armDown, drillSpin, tile.getClientCargoFill());
        UnitTier tier = tile.getRoverTier();
        BlockPos at = BlockPos.containing(center.getX() + x + 0.5, center.getY() + y + 0.5, center.getZ() + z + 0.5);
        int roverLight = LevelRenderer.getLightColor(level, at);
        //0.20.46 (audit) : types de rendu en cache ; benne dessinee avec la caisse (meme tampon), la lueur ensuite : un seul changement de
        //tampon par rover au lieu de deux (meme image : la lueur additive n'ecrit pas la profondeur)
        int cargo = tile.getClientCargoColor();
        VertexConsumer body = buffers.getBuffer(drillTypes[tier.ordinal()]);
        rover.renderToBuffer(pose, body, roverLight, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        rover.renderCargo(pose, body, roverLight, OverlayTexture.NO_OVERLAY,
              0.55F + 0.45F * (cargo >> 16 & 255) / 255F, 0.55F + 0.45F * (cargo >> 8 & 255) / 255F, 0.55F + 0.45F * (cargo & 255) / 255F);
        rover.renderToBuffer(pose, buffers.getBuffer(glowType), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        pose.popPose();
        //Nom du rover, au-dessus de lui (comme une etiquette de nom)
        String name = tile.getClientRoverName();
        if (!name.isEmpty() && index == 0) {
            var minecraft = Minecraft.getInstance();
            var font = minecraft.font;
            pose.pushPose();
            pose.translate(x + 0.5, y + 1.45 + bob, z + 0.5);
            pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(-0.025F, -0.025F, 0.025F);
            float textX = -font.width(name) / 2F;
            int background = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255F) << 24;
            font.drawInBatch(name, textX, 0, 0x20FFFFFF, false, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH,
                  background, roverLight);
            font.drawInBatch(name, textX, 0, 0xFFFFFFFF, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, roverLight);
            pose.popPose();
        }

        //Particules : forage et poussiere de roulage
        if (!fx) {
            return;
        }
        var random = level.random;
        double wx = center.getX() + x + 0.5, wy = center.getY() + y, wz = center.getZ() + z + 0.5;
        if (mode == Mode.DRILLING && armDown >= 1 && random.nextInt(3) == 0) {
            double tipX = wx + dirX * 1.0, tipZ = wz + dirZ * 1.0;
            BlockState ground = level.getBlockState(BlockPos.containing(tipX, wy - 0.5, tipZ));
            if (!ground.isAir()) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), tipX, wy + 0.05, tipZ,
                      (random.nextDouble() - 0.5) * 0.2, 0.15, (random.nextDouble() - 0.5) * 0.2);
            }
            level.addParticle(new DustParticleOptions(new Vector3f((cargo >> 16 & 255) / 255F, (cargo >> 8 & 255) / 255F, (cargo & 255) / 255F), 0.8F),
                  tipX + (random.nextDouble() - 0.5) * 0.3, wy + 0.15, tipZ + (random.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
        } else if (moving && fromDock > 3 && random.nextInt(4) == 0) {
            BlockState ground = level.getBlockState(BlockPos.containing(wx, wy - 0.5, wz));
            if (!ground.isAir()) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), wx - dirX * 0.8, wy + 0.05, wz - dirZ * 0.8, 0, 0.05, 0);
            }
        } else if (mode == Mode.CHARGING && random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, wx + (random.nextDouble() - 0.5) * 0.6, wy + 0.3, wz + (random.nextDouble() - 0.5) * 0.6, 0, 0.05, 0);
        }
    }

    /**
     * Hauteur du sol (relative au centre du sol de la base) sous un point exprime en blocs depuis le centre,
     * interpolee entre les colonnes voisines. Dans l'emprise du hangar, c'est le plancher.
     */
    private static double groundAt(TileEntityMiningOutpost tile, Level level, BlockPos center, double x, double z) {
        double fx = x, fz = z;
        int x0 = Mth.floor(fx), z0 = Mth.floor(fz);
        double tx = fx - x0, tz = fz - z0;
        double h00 = column(tile, level, center, x0, z0), h10 = column(tile, level, center, x0 + 1, z0);
        double h01 = column(tile, level, center, x0, z0 + 1), h11 = column(tile, level, center, x0 + 1, z0 + 1);
        return Mth.lerp(tz, Mth.lerp(tx, h00, h10), Mth.lerp(tx, h01, h11));
    }

    private static double column(TileEntityMiningOutpost tile, Level level, BlockPos center, int dx, int dz) {
        if (tile.insideHangar(dx, dz)) {
            return 1;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX() + dx, center.getZ() + dz) - center.getY();
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull TileEntityMiningOutpost tile) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
