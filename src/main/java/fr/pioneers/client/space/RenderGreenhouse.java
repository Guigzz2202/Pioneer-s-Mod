package fr.pioneers.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.client.robotics.RobotUnitItemRenderer;
import fr.pioneers.registries.PiBlocks;
import fr.pioneers.robotics.UnitKind;
import fr.pioneers.space.farm.GreenhouseStructure;
import fr.pioneers.space.farm.HydroponicSubstrateBlock;
import fr.pioneers.space.farm.SpacePlant;
import fr.pioneers.tile.TileEntityGreenhouse.Mode;
import fr.pioneers.tile.TileEntityGreenhouse;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

/**
 * Serre spatiale : les plantes dans leurs bacs (taille selon la croissance) et le robot agricole qui circule.
 */
public class RenderGreenhouse implements BlockEntityRenderer<TileEntityGreenhouse> {

    /** 0.20.46 (audit) : plantes dessinees seulement a moins de 32 blocs de la grille des bacs. */
    private static final double PLANT_RANGE = 32;
    /** 0.20.46 (audit) : etat des bacs (labour, lumiere) relu dans le monde toutes les 20 ticks. */
    private static final int REFRESH = 20;

    /** Etat des bacs d'une serre, relu toutes les {@link #REFRESH} ticks (deux lectures du monde par bac et par image avant). */
    private static final class Cells {

        long tick = Long.MIN_VALUE;
        BlockPos center;
        boolean[] tilled = new boolean[0];
        int[] light = new int[0];
    }

    private final Map<TileEntityGreenhouse, Cells> cells = new WeakHashMap<>();
    private float lastYaw;

    public RenderGreenhouse(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull TileEntityGreenhouse tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        BlockPos center = tile.getClientCenter();
        if (center == null || tile.getLevel() == null) {
            return;
        }
        var level = tile.getLevel();
        float time = level.getGameTime() + partialTick;
        BlockPos origin = tile.getBlockPos();
        pose.pushPose();
        pose.translate(center.getX() - origin.getX(), center.getY() - origin.getY() + 1, center.getZ() - origin.getZ());

        //Plantes (0.20.46 (audit) : seulement de pres ; labour et lumiere des bacs en cache)
        int[] plants = tile.getClientPlants(), stages = tile.getClientStages();
        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        Cells cache = plantsInRange(center) ? cells(tile, level, center, plants.length) : null;
        for (int cell = 0; cache != null && cell < plants.length && cell < stages.length; cell++) {
            if (plants[cell] < 0) {
                continue;
            }
            SpacePlant plant = SpacePlant.byIndex(plants[cell]);
            BlockState state = PiBlocks.SPACE_PLANTS.get(plant).getBlock().defaultBlockState();
            int dx = GreenhouseStructure.cellX(cell), dz = GreenhouseStructure.cellZ(cell);
            float scale = 0.3F + 0.175F * stages[cell];
            float sway = stages[cell] >= 4 ? Mth.sin(time * 0.05F + cell) * 2.5F : 0;
            boolean tilled = cache.tilled[cell];
            pose.pushPose();
            pose.translate(dx + 0.5, tilled ? -1 / 16F : 0, dz + 0.5);
            pose.mulPose(Axis.ZP.rotationDegrees(sway));
            pose.scale(scale, scale, scale);
            pose.translate(-0.5, 0, -0.5);
            int cellLight = cache.light[cell];
            blockRenderer.renderSingleBlock(state, pose, buffers, Math.max(cellLight, plant.light > 0 ? 0xF000F0 : 0), OverlayTexture.NO_OVERLAY,
                  ModelData.EMPTY, RenderType.cutout());
            pose.popPose();
        }

        //Robot
        if (tile.hasClientRobot()) {
            float[] pos = tile.getRobotPosition(time);
            Mode mode = tile.getClientMode();
            if (mode == Mode.MOVING && (pos[2] != 0 || pos[3] != 0)) {
                lastYaw = (float) Math.toDegrees(Math.atan2(pos[2], pos[3]));
            }
            boolean onPad = mode == Mode.CHARGING || mode == Mode.IDLE;
            float bob = mode == Mode.MOVING ? Mth.sin(time * 0.9F) * 0.02F : 0;
            pose.pushPose();
            pose.translate(pos[0] + 0.5, (onPad ? 3 / 16F : 0) + bob, pos[1] + 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(lastYaw));
            pose.scale(0.6F, 0.6F, 0.6F);
            int robotLight = LevelRenderer.getLightColor(level, center.offset(Math.round(pos[0]), 1, Math.round(pos[1])));
            boolean moving = mode == Mode.MOVING;
            //Tortue robotique (renderUnit la tourne deja vers +z, comme l'ancien robot)
            RobotUnitItemRenderer.renderUnit(UnitKind.TURTLE, tile.getRobotTier(), time, moving ? time * 0.5F : 0, moving ? 0.8F : 0,
                  mode == Mode.WORKING, pose, buffers, robotLight);
            pose.popPose();
            //Etincelles de recharge
            if (mode == Mode.CHARGING && FxTicks.firstFrame(tile, level) && level.random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, center.getX() + pos[0] + 0.5 + (level.random.nextDouble() - 0.5) * 0.6,
                      center.getY() + 1.15, center.getZ() + pos[1] + 0.5 + (level.random.nextDouble() - 0.5) * 0.6, 0, 0.05, 0);
            }
        }
        pose.popPose();
    }

    /** Camera a moins de {@link #PLANT_RANGE} blocs de la grille des bacs (point le plus proche). */
    private static boolean plantsInRange(BlockPos center) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int half = GreenhouseStructure.HALF;
        double dx = Math.max(0, Math.max(center.getX() - half - cam.x, cam.x - (center.getX() + half + 1)));
        double dy = Math.max(0, Math.max(center.getY() + 1 - cam.y, cam.y - (center.getY() + 3)));
        double dz = Math.max(0, Math.max(center.getZ() - half - cam.z, cam.z - (center.getZ() + half + 1)));
        return dx * dx + dy * dy + dz * dz <= PLANT_RANGE * PLANT_RANGE;
    }

    /** Labour et lumiere de chaque bac, relus dans le monde au plus toutes les {@link #REFRESH} ticks. */
    private Cells cells(TileEntityGreenhouse tile, Level level, BlockPos center, int count) {
        Cells c = cells.computeIfAbsent(tile, k -> new Cells());
        long now = level.getGameTime();
        if (c.tilled.length != count || !center.equals(c.center) || now - c.tick >= REFRESH || now < c.tick) {
            if (c.tilled.length != count) {
                c.tilled = new boolean[count];
                c.light = new int[count];
            }
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int cell = 0; cell < count; cell++) {
                int dx = GreenhouseStructure.cellX(cell), dz = GreenhouseStructure.cellZ(cell);
                c.tilled[cell] = HydroponicSubstrateBlock.isTilled(level, at.setWithOffset(center, dx, 0, dz));
                c.light[cell] = LevelRenderer.getLightColor(level, at.setWithOffset(center, dx, 1, dz));
            }
            c.center = center;
            c.tick = now;
        }
        return c;
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull TileEntityGreenhouse tile) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
