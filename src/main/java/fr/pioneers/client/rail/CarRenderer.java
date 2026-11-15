package fr.pioneers.client.rail;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.client.industry.big.BigMat;
import fr.pioneers.client.industry.big.BigMesh;
import fr.pioneers.rail.train.CarType;
import fr.pioneers.rail.train.RailCarEntity;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 0.20.0 : rendu des vehicules : maillage {@link CarModels} (partie fixe en cache par sorte, tier, remplissage et vrac ; roues tournees
 * selon le compteur ; bielles refaites a chaque image), pose interpolee (cap, tangage). Une passe par materiau.
 */
public class CarRenderer extends EntityRenderer<RailCarEntity> {

    /** 0.20.46 (audit) : cle numerique (sorte, tier, remplissage, vrac) au lieu d'un texte refait a chaque image. */
    private static final Long2ObjectOpenHashMap<Cached> CACHE = new Long2ObjectOpenHashMap<>();
    /** 0.20.46 (audit) : teinte du vrac par objet (le calcul lit le registre et des noms a chaque image). */
    private static final Reference2IntOpenHashMap<Item> BULK = new Reference2IntOpenHashMap<>();
    /** Teintes du vrac d'apres un mot de l'identifiant de l'objet. */
    private static final String[][] BULK_WORDS = {{"iron", "A07A62"}, {"copper", "B8693F"}, {"gold", "E0B040"}, {"tin", "C8CCD0"}, {"zinc", "A8B4B8"},
          {"lead", "6E7484"}, {"nickel", "C8C0A0"}, {"silver", "D8DCE0"}, {"uranium", "7EA850"}, {"sand", "D8C890"}, {"gravel", "808080"}, {"clay", "A0A8B8"},
          {"redstone", "B02020"}, {"lapis", "3050B0"}, {"quartz", "E8E0D8"}, {"salt", "EEEEEE"}, {"sulfur", "D8C838"}, {"bauxite", "B86A48"},
          {"wheat", "D8B860"}, {"seed", "8AA048"}, {"wood", "8E6A42"}, {"log", "8E6A42"}, {"stone", "8A8A8A"}, {"dirt", "7A5A3A"}};
    private static final Map<BigMat, ResourceLocation> TEXTURES = new EnumMap<>(BigMat.class);

    private record Cached(Map<BigMat, List<List<BigMesh.Quad>>> buckets, List<BigMesh.Group> groups) {}

    public CarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.4F;
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull RailCarEntity entity) {
        return texture(BigMat.R_STEEL);
    }

    static ResourceLocation texture(BigMat m) {
        return TEXTURES.computeIfAbsent(m, k -> Pioneers.rl("textures/" + k.sprite() + ".png"));
    }

    @Override
    public void render(@NotNull RailCarEntity e, float yaw, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light) {
        float y = Mth.lerp(partial, e.yawO, e.yawR), p = Mth.lerp(partial, e.pitchO, e.pitchR), odo = Mth.lerp(partial, e.odoO, e.odoR);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(y));
        pose.mulPose(Axis.XP.rotationDegrees(-p));
        draw(pose, buffers, light, e.carType(), e.tier(), e.fill(), e.carType() == CarType.FLAT ? 0 : bulkColor(e.load()), odo, odo * 0.25F, e.flags());
        if (e.carType() == CarType.FLAT && e.load() > 0) {
            //0.20.2 : le bloc porte par le wagon plat, aux trois quarts, pose sur le plateau
            Item item = BuiltInRegistries.ITEM.byId(e.load());
            if (item instanceof BlockItem bi) {
                pose.pushPose();
                pose.translate(-0.375, CarModels.FLAT_DECK / 16.0, -0.375);
                pose.scale(0.75F, 0.75F, 0.75F);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(bi.getBlock().defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        pose.popPose();
        super.render(e, yaw, partial, pose, buffers, light);
    }

    /** Dessine un vehicule, centre au pied, avant vers +z (aussi pour l'objet dans l'inventaire). */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int light, CarType type, int tier, float fill, int bulk, float odo, float pump,
          int flags) {
        int bucket = Math.round(Math.max(0, Math.min(1, fill)) * 8);
        long key = (long) (type.ordinal() & 0xFF) << 56 | (long) (tier & 0xFF) << 48 | (long) (bucket & 0xFF) << 40 | bulk & 0xFFFFFFFFL;
        Cached c = CACHE.get(key);
        if (c == null) {
            if (CACHE.size() > 256) {
                CACHE.clear();
            }
            c = cache(RailMesh.deconflict(CarModels.build(type, tier, bucket / 8F, bulk).mesh));
            CACHE.put(key, c);
        }
        BigMesh dyn = RailMesh.deconflict(CarModels.dynamic(type, tier, odo, pump, flags).mesh);
        pose.pushPose();
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
        pose.translate(-8, 0, -8);
        Matrix4f[] mats = new Matrix4f[c.groups.size()];
        Matrix3f[] norms = new Matrix3f[c.groups.size()];
        for (int g = 0; g < c.groups.size(); g++) {
            BigMesh.Group gr = c.groups.get(g);
            pose.pushPose();
            if (g > 0 && gr.factor() != 0) {
                float ang = odo * gr.factor();
                pose.translate(gr.px(), gr.py(), gr.pz());
                pose.mulPose(switch (gr.axis()) {
                    case 'x' -> Axis.XP.rotationDegrees(ang);
                    case 'y' -> Axis.YP.rotationDegrees(ang);
                    default -> Axis.ZP.rotationDegrees(ang);
                });
                pose.translate(-gr.px(), -gr.py(), -gr.pz());
            }
            mats[g] = new Matrix4f(pose.last().pose());
            norms[g] = new Matrix3f(pose.last().normal());
            pose.popPose();
        }
        Map<BigMat, List<BigMesh.Quad>> extra = new EnumMap<>(BigMat.class);
        for (BigMesh.Quad q : dyn.quads) {
            extra.computeIfAbsent(q.mat, k -> new ArrayList<>()).add(q);
        }
        for (BigMat mat : BigMat.values()) {
            List<List<BigMesh.Quad>> byGroup = c.buckets.get(mat);
            List<BigMesh.Quad> more = extra.get(mat);
            if (byGroup == null && more == null) {
                continue;
            }
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture(mat)));
            if (byGroup != null) {
                for (int g = 0; g < byGroup.size(); g++) {
                    for (BigMesh.Quad q : byGroup.get(g)) {
                        emit(vc, mats[g], norms[g], q, light);
                    }
                }
            }
            if (more != null) {
                for (BigMesh.Quad q : more) {
                    emit(vc, mats[0], norms[0], q, light);
                }
            }
        }
        pose.popPose();
    }

    private static Cached cache(BigMesh mesh) {
        Map<BigMat, List<List<BigMesh.Quad>>> buckets = new EnumMap<>(BigMat.class);
        for (BigMesh.Quad q : mesh.quads) {
            List<List<BigMesh.Quad>> byGroup = buckets.computeIfAbsent(q.mat, k -> new ArrayList<>());
            while (byGroup.size() <= q.group) {
                byGroup.add(new ArrayList<>());
            }
            byGroup.get(q.group).add(q);
        }
        return new Cached(buckets, List.copyOf(mesh.groups));
    }

    private static void emit(VertexConsumer vc, Matrix4f m, Matrix3f n, BigMesh.Quad q, int light) {
        int lt = light;
        if (q.glow > 0) {
            lt = LightTexture.pack(Math.max(LightTexture.block(light), q.glow), LightTexture.sky(light));
        }
        int a = q.color >>> 24 & 255, r = q.color >> 16 & 255, g = q.color >> 8 & 255, b = q.color & 255;
        for (int i = 0; i < 4; i++) {
            vc.vertex(m, q.pos[i * 3], q.pos[i * 3 + 1], q.pos[i * 3 + 2]).color(r, g, b, a).uv(q.uv[i * 2], q.uv[i * 2 + 1])
                  .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lt).normal(n, q.nx, q.ny, q.nz).endVertex();
        }
    }

    /** Teinte du vrac selon l'objet (0 = charbon). 0.20.46 (audit) : retenue par objet. */
    static int bulkColor(int rawId) {
        if (rawId <= 0) {
            return 0x8A7060;
        }
        Item item = BuiltInRegistries.ITEM.byId(rawId);
        if (BULK.containsKey(item)) {
            return BULK.getInt(item);
        }
        int col = bulkColor(item);
        BULK.put(item, col);
        return col;
    }

    private static int bulkColor(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (id.contains("coal") || id.contains("coke") || id.contains("charcoal") || id.contains("graphite")) {
            return 0;
        }
        if (item instanceof BlockItem bi) {
            int col = bi.getBlock().defaultMapColor().col;
            if (col != 0) {
                return col;
            }
        }
        for (String[] e : BULK_WORDS) {
            if (id.contains(e[0])) {
                return Integer.parseInt(e[1], 16);
            }
        }
        return 0x8A7060;
    }

    public static void reset() {
        CACHE.clear();
        BULK.clear();
    }
}
