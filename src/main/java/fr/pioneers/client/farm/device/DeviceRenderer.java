package fr.pioneers.client.farm.device;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.client.kinetics.KDraw;
import fr.pioneers.core.RenderClock;
import fr.pioneers.farm.crop.Climate;
import fr.pioneers.farm.device.ApiaryTile;
import fr.pioneers.farm.device.AutoFeederTile;
import fr.pioneers.farm.device.CompostBinTile;
import fr.pioneers.farm.device.DeviceTile;
import fr.pioneers.farm.device.IncubatorTile;
import fr.pioneers.farm.device.NestBoxTile;
import fr.pioneers.farm.device.ScarecrowBlock;
import fr.pioneers.farm.device.ScarecrowTile;
import fr.pioneers.farm.device.SprinklerTile;
import fr.pioneers.farm.device.WeatherStationTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Parties animees de la ferme animee (reperes recopies de {@code tools/gc/farm2/art_devices.py}) : tete des arroseurs qui tourne, tas du
 * composteur, coupelles et girouette de la station meteo (dans le repere du monde : le vent), colonne du thermometre, abeilles autour de la
 * ruche, oeufs du pondoir et de la couveuse (qui basculent), grains et helice du distributeur, buste de l'epouvantail qui se balance au vent.
 */
public class DeviceRenderer implements BlockEntityRenderer<DeviceTile> {

    public static final ResourceLocation BRONZE_HEAD = Pioneers.rl("block/farm/device/bronze_sprinkler_head");
    public static final ResourceLocation STEEL_HEAD = Pioneers.rl("block/farm/device/steel_sprinkler_head");
    public static final ResourceLocation SCARECROW_BODY = Pioneers.rl("block/farm/device/scarecrow_body");
    public static final ResourceLocation[] SCARECROW_HEADS = {Pioneers.rl("block/farm/device/scarecrow_head_0"),
                                                              Pioneers.rl("block/farm/device/scarecrow_head_1"),
                                                              Pioneers.rl("block/farm/device/scarecrow_head_2")};

    public DeviceRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(DeviceTile tile) {
        return tile instanceof ScarecrowTile || tile instanceof ApiaryTile || tile instanceof WeatherStationTile
              || tile instanceof fr.pioneers.farm.device.HutchTile;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public void render(DeviceTile tile, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }
        float time = (float) RenderClock.time(partial);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        //Ce qui suit le monde (vent, abeilles, rotation des tetes) avant de tourner selon la facade
        if (tile instanceof WeatherStationTile w) {
            weatherWorld(w, partial, pose, buffers, light, overlay);
        } else if (tile instanceof ApiaryTile a) {
            bees(a, time, pose, buffers, light, overlay);
        } else if (tile instanceof SprinklerTile s) {
            sprinkler(s, partial, pose, buffers, light, overlay);
            return;
        }
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(tile.facing())));
        pose.translate(-0.5, 0, -0.5);
        if (tile instanceof CompostBinTile c) {
            compost(c, time, partial, pose, buffers, light, overlay);
        } else if (tile instanceof WeatherStationTile w) {
            thermometer(w, pose, buffers, light, overlay);
        } else if (tile instanceof NestBoxTile n) {
            nest(n, pose, buffers, light, level);
        } else if (tile instanceof IncubatorTile i) {
            incubator(i, time, pose, buffers, light, level);
        } else if (tile instanceof AutoFeederTile f) {
            feeder(f, time, pose, buffers, light, overlay);
        } else if (tile instanceof ScarecrowTile s) {
            scarecrow(s, time, pose, buffers, light, overlay);
        } else if (tile instanceof fr.pioneers.farm.device.HutchTile h) {
            //1.0.4-beta (lot 28) : les lapins loges, visibles derriere le grillage
            fr.pioneers.client.farm.herd.HutchRabbits.render(h, time, pose, buffers, light);
        }
        pose.popPose();
    }

    private static float yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ outils

    static KDraw.Tex dev(String name) {
        return KDraw.of(Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(Pioneers.rl("block/farm/device/" + name)));
    }

    static KDraw.Tex kit(String name) {
        return KDraw.of(Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(Pioneers.rl("block/farm/kitchen/" + name)));
    }

    static KDraw.Tex mc(String name) {
        return KDraw.of(Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(new ResourceLocation("minecraft", "block/" + name)));
    }

    /** Modele cuit (enregistre en plus, {@code FarmDeviceClient}) dessine dans le repere courant. */
    static void model(ResourceLocation id, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BakedModel m = Minecraft.getInstance().getModelManager().getModel(id);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null, m, 1F, 1F,
              1F, light, overlay, ModelData.EMPTY, RenderType.cutout());
    }

    private static void item(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, Level level, float x, float y, float z, float scale,
          float yaw, float roll, int seed) {
        if (stack.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.translate(x / 16F, y / 16F, z / 16F);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.translate(0, scale * 0.25F, 0);
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, level,
              seed);
        pose.popPose();
    }

    // ------------------------------------------------------------------ arroseurs

    private static void sprinkler(SprinklerTile s, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float angle = Mth.lerp(partial, s.lastAngle, s.angle);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-angle));
        pose.translate(-0.5, 0, -0.5);
        model(s.steel() ? STEEL_HEAD : BRONZE_HEAD, pose, buffers, light, overlay);
        pose.popPose();
    }

    // ------------------------------------------------------------------ composteur

    /**
     * 1.6 : tas en couches (reperes de {@code art_devices.compost_bin} : interieur 2,3..13,7, fond 1,0, couche 1,65 px), frais et vert en
     * remplissage, qui se tasse, respire, bouillonne et brunit en fermentation, compost noir et bombe quand il est pret ; couvercle a
     * charniere arriere (pivot y 14,0, z 14,6) ouvert pour remplir ou vider, ferme en fermentation et souleve par les bouffees.
     */
    private static void compost(CompostBinTile c, float time, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        KDraw d = new KDraw(pose, buffers, light, overlay);
        KDraw.Tex side = dev("compost_side");
        float lo = 2.3F, hi = 13.7F, floor = 1.0F;
        boolean fermenting = c.ferment() >= 0;
        if (c.ready()) {
            KDraw.Tex ripe = dev("compost_ripe");
            d.box(lo, floor, lo, hi, floor + 7.0F, hi, side, ripe);
            d.box(3.6F, floor + 7.0F, 3.6F, 12.4F, floor + 8.2F, 12.4F, ripe, ripe);
            d.box(5.6F, floor + 8.2F, 5.4F, 10.6F, floor + 9.0F, 10.8F, ripe, ripe);
        } else if (c.layers() > 0) {
            float r = c.ripeness();
            float h = c.layers() * 1.65F;
            KDraw.Tex top = dev("compost_fresh");
            if (fermenting) {
                h = h * (1F - 0.3F * r) + 0.18F * Mth.sin(time * 0.09F);
                top = r < 0.55F ? dev("compost_mid") : dev("compost_ripe");
            }
            d.box(lo, floor, lo, hi, floor + h, hi, side, top);
            //Tas inegal : deux bosses
            d.box(4.2F, floor + h, 5.0F, 8.6F, floor + h + 0.6F, 9.4F, top, top);
            d.box(8.8F, floor + h, 8.2F, 12.2F, floor + h + 0.45F, 11.8F, top, top);
            if (fermenting) {
                //Bulles qui gonflent et crevent a la surface
                KDraw.Tex bubble = dev("compost_mid");
                for (int i = 0; i < 3; i++) {
                    float ph = (time * 0.025F + i * 0.37F) % 1F;
                    float bx = 4.5F + (i * 3.7F) % 7F, bz = 5.0F + (i * 2.9F) % 6.5F, b = 0.55F * Mth.sin(ph * Mth.PI);
                    if (b > 0.05F) {
                        d.box(bx - b, floor + h, bz - b, bx + b, floor + h + b * 1.2F, bz + b, bubble, bubble);
                    }
                }
            }
        }
        //Couvercle : ouvert (100 deg) pour remplir ou vider, ferme en fermentation, souleve par les bouffees de vapeur
        float a = c.lid(partial);
        if (fermenting) {
            a += Math.max(0F, Mth.sin(time * 0.06F) - 0.6F) * 14F;
        }
        KDraw.Tex wood = kit("wood"), woodH = kit("wood_h"), iron = kit("iron"), ironL = kit("iron_light"), dark = kit("wood_dark");
        d.push();
        d.rotate(Direction.Axis.X, a, 8F, 14.0F, 14.6F);
        for (int i = 0; i < 3; i++) {
            float x0 = 1.2F + i * 4.6F;
            d.box(x0, 14.0F, 1.2F, x0 + 4.4F, 15.0F, 14.8F, i == 1 ? wood : woodH, i == 1 ? wood : woodH);
        }
        d.box(1.0F, 15.0F, 3.0F, 15.0F, 15.4F, 4.0F, iron, ironL);
        d.box(1.0F, 15.0F, 12.0F, 15.0F, 15.4F, 13.0F, iron, ironL);
        d.box(6.6F, 15.0F, 1.4F, 9.4F, 15.8F, 2.4F, dark, dark);
        d.pop();
    }

    // ------------------------------------------------------------------ station meteo

    private static void weatherWorld(WeatherStationTile w, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        KDraw d = new KDraw(pose, buffers, light, overlay);
        KDraw.Tex steel = kit("steel"), light2 = kit("iron_light"), dark = kit("dark"), red = dev("red"), brass = kit("brass");
        float cups = Mth.lerp(partial, w.lastCups, w.cups), vane = Mth.lerp(partial, w.lastVane, w.vane);
        d.push().rotate(Direction.Axis.Y, cups, 8, 0, 8);
        d.box(7.4F, 28.6F, 7.4F, 8.6F, 29.4F, 8.6F, brass, brass);
        for (int i = 0; i < 3; i++) {
            d.push().rotate(Direction.Axis.Y, i * 120F, 8, 0, 8);
            d.box(8F, 28.85F, 7.85F, 12.2F, 29.15F, 8.15F, steel, steel);
            d.box(11.6F, 28.4F, 8.15F, 12.8F, 29.6F, 9.3F, light2, light2);
            d.box(11.8F, 28.6F, 9.3F, 12.6F, 29.4F, 9.32F, dark, dark);
            d.pop();
        }
        d.pop();
        d.push().rotate(Direction.Axis.Y, -vane, 8, 0, 8);
        d.box(7.8F, 23.8F, 3.8F, 8.2F, 24.2F, 12.4F, steel, steel);
        d.box(7.3F, 23.5F, 2.6F, 8.7F, 24.5F, 3.8F, red, red);
        d.box(7.6F, 23.65F, 1.8F, 8.4F, 24.35F, 2.6F, red, red);
        d.box(7.9F, 22.8F, 11.0F, 8.1F, 25.4F, 13.4F, red, red);
        d.pop();
    }

    private static void thermometer(WeatherStationTile w, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = w.getLevel();
        float t = Climate.temperature(level, w.getBlockPos().above());
        float k = Mth.clamp((WeatherStationTile.celsius(t) + 10) / 60F, 0.04F, 1F);
        KDraw d = new KDraw(pose, buffers, LightTexture.FULL_BRIGHT, overlay);
        KDraw.Tex red = dev("red");
        d.box(7.6F, 6F, 5.88F, 8.4F, 6F + 8F * k, 5.98F, red, red);
    }

    // ------------------------------------------------------------------ ruche

    private static void bees(ApiaryTile a, float time, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int n = a.bees();
        if (n <= 0) {
            return;
        }
        KDraw d = new KDraw(pose, buffers, light, overlay);
        KDraw.Tex body = dev("bee"), wing = dev("bee_wing");
        long seed = a.getBlockPos().asLong();
        for (int i = 0; i < n; i++) {
            float sp = 0.035F + 0.006F * ((seed >> i & 7));
            float t = time * sp + i * 1.9F;
            float r = 11F + 7F * Mth.sin(i * 1.3F + time * 0.004F) + 4F * Mth.sin(t * 0.7F);
            float x = 8 + Mth.cos(t) * r, z = 8 + Mth.sin(t * 1.4F + i) * r * 0.75F, y = 11 + 5 * Mth.sin(t * 1.9F + i * 0.7F);
            //Cap : derivee de la trajectoire
            float dx = -Mth.sin(t) * r, dz = Mth.cos(t * 1.4F + i) * r * 0.75F * 1.4F;
            float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
            float flap = Mth.sin(time * 2.6F + i) * 35F;
            d.push().translate(x, y, z).rotate(Direction.Axis.Y, yaw, 0, 0, 0);
            d.box(-0.55F, -0.45F, -0.9F, 0.55F, 0.45F, 0.9F, body, body);
            d.push().rotate(Direction.Axis.Z, flap, 0.4F, 0.45F, 0);
            d.box(0.4F, 0.45F, -0.5F, 1.9F, 0.55F, 0.6F, wing, wing);
            d.pop();
            d.push().rotate(Direction.Axis.Z, -flap, -0.4F, 0.45F, 0);
            d.box(-1.9F, 0.45F, -0.5F, -0.4F, 0.55F, 0.6F, wing, wing);
            d.pop();
            d.pop();
        }
    }

    // ------------------------------------------------------------------ pondoir, couveuse

    private static final float[][] NEST_SPOTS = {{5.2F, 7.6F}, {8.4F, 9.4F}, {11.0F, 7.2F}, {6.4F, 11.0F}, {9.8F, 11.6F}, {12.2F, 10.4F}};

    private static void nest(NestBoxTile n, PoseStack pose, MultiBufferSource buffers, int light, Level level) {
        int k = 0;
        for (ItemStack s : n.items()) {
            for (int j = 0; j < s.getCount() && k < NEST_SPOTS.length; j++, k++) {
                item(s, pose, buffers, light, level, NEST_SPOTS[k][0], 2.9F, NEST_SPOTS[k][1], 0.42F, k * 47F, 0, k);
            }
        }
    }

    private static void incubator(IncubatorTile inc, float time, PoseStack pose, MultiBufferSource buffers, int light, Level level) {
        for (int i = 0; i < IncubatorTile.SLOTS; i++) {
            float p = inc.progress(i);
            if (p < 0) {
                continue;
            }
            float x = 4.5F + (i % 3) * 3.5F, z = 6.2F + (i / 3) * 3.8F;
            float rock = Mth.sin(time * 0.22F + i * 1.7F) * (2F + 10F * p);
            if (p > 0.85F) {
                rock += Mth.sin(time * 1.8F + i) * 9F * (p - 0.85F) / 0.15F;
            }
            item(inc.items().get(i), pose, buffers, light, level, x, 5.6F, z, 0.4F, i * 60F, rock, i);
        }
    }

    // ------------------------------------------------------------------ distributeur

    private static void feeder(AutoFeederTile f, float time, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        KDraw d = new KDraw(pose, buffers, light, overlay);
        float fill = f.fillLevel();
        if (fill > 0.01F) {
            KDraw.Tex g = dev("grain");
            d.box(2.8F, 6.2F, 2.8F, 13.2F, 6.2F + 8.8F * fill, 13.2F, g, g);
        }
        boolean working = time - f.lastWork() < 60;
        float spin = working ? time * 40F : 0F;
        KDraw.Tex steel = kit("iron_light"), dark = kit("dark");
        d.box(15.4F, 8.6F, 7.6F, 15.8F, 9.4F, 8.4F, dark, dark);
        d.push().rotate(Direction.Axis.X, spin, 0, 9F, 8F);
        for (int i = 0; i < 4; i++) {
            d.push().rotate(Direction.Axis.X, i * 90F, 0, 9F, 8F);
            d.box(15.5F, 9.0F, 7.6F, 15.75F, 11.4F, 8.4F, steel, steel);
            d.pop();
        }
        d.pop();
    }

    // ------------------------------------------------------------------ epouvantail

    private static void scarecrow(ScarecrowTile s, float time, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = s.getBlockState();
        if (!state.hasProperty(ScarecrowBlock.HEAD)) {
            return;
        }
        Level level = s.getLevel();
        float gust = 1F + (level.isRaining() ? 0.8F : 0F) + (level.isThundering() ? 0.8F : 0F);
        float seed = (s.getBlockPos().asLong() & 63) * 0.37F;
        float sway = (Mth.sin(time * 0.045F + seed) * 2.2F + Mth.sin(time * 0.17F + seed * 2) * 0.6F) * gust;
        float nod = Mth.sin(time * 0.033F + seed * 1.3F) * 1.2F * gust;
        int upLight = light;
        if (level.isLoaded(s.getBlockPos().above())) {
            upLight = LevelRenderer.getLightColor(level, s.getBlockPos().above());
        }
        pose.pushPose();
        pose.translate(0, 1, 0);
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.ZP.rotationDegrees(sway));
        pose.mulPose(Axis.XP.rotationDegrees(nod));
        pose.translate(-0.5, 0, -0.5);
        model(SCARECROW_BODY, pose, buffers, upLight, overlay);
        model(SCARECROW_HEADS[state.getValue(ScarecrowBlock.HEAD)], pose, buffers, upLight, overlay);
        pose.popPose();
    }
}
