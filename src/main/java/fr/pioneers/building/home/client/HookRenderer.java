package fr.pioneers.building.home.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.building.home.client.LineVisuals.Attach;
import fr.pioneers.building.home.client.LineVisuals.Load;
import fr.pioneers.tech.kinetics.client.KDraw;
import fr.pioneers.building.home.line.Clothesline;
import fr.pioneers.building.home.line.HookTile;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.ForgeHooksClient;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Patch 1.0.3-beta, lot 7 : la corde a linge, dessinee par le crochet maitre ({@link HookTile}) : chainette a la texture de la corde du
 * systeme mecanique ({@code block/mechanics/<rope|tarred_rope|leather>}, comme entre deux poulies), et sous chaque place occupee la piece
 * accrochee (vue « fixe », tournee dans le sens de la corde), qui se balance un peu au vent. Lumiere lue au point de chaque piece.
 * <p>
 * 1.0.3-beta (lot 11, Guillaume : « impression de levitation ») : chaque piece PEND a la corde : pince a linge (vetements, bannieres) ou
 * ficelle et crochet (le reste) dessines par {@link LineVisuals} ; la corde flechit sous chaque piece ; le HAUT REEL du modele de l'objet
 * (plus haute face tournee vers le haut, transformation « fixe » comprise : {@link #top}) est pose a l'attache, plus de vide entre la corde et
 * la piece ; la piece penche un peu selon la pente de la corde ({@link #TILT}) et se balance autour de son attache.
 */
public class HookRenderer implements BlockEntityRenderer<HookTile> {

    /** Part de la pente de la corde que suit la piece accrochee. */
    public static final float TILT = 0.35F;
    /** Echelle des pieces (vue fixe : une icone fait 8 px a l'echelle 1) : banniere, vetement, autre. */
    public static final float BANNER = 1.1F, GARMENT = 1.0F, OTHER = 0.8F;
    /** Haut des modeles d'objet (blocs, vue fixe, avant mise a l'echelle), par modele cuit (vide au rechargement des ressources). */
    private static final Map<BakedModel, Float> TOPS = Collections.synchronizedMap(new WeakHashMap<>());

    public HookRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(HookTile tile) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public void render(HookTile tile, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!tile.isMaster() || tile.getLevel() == null) {
            return;
        }
        BlockPos origin = tile.getBlockPos();
        Vec3 o = Vec3.atLowerCornerOf(origin), a = tile.eye(), b = tile.otherEye();
        float[] pa = px(a.subtract(o)), pb = px(b.subtract(o));
        double sagPx = tile.sag() * 16;
        KDraw d = new KDraw(pose, buffers, light, overlay);
        String tex = tile.link().texture;
        float w = tex.equals("leather") ? 1.4F : 0.9F;
        int places = tile.places();
        List<HookTile.Hung> hung = tile.hung();
        Load[] loads = new Load[hung.size()];
        for (int i = 0; i < loads.length; i++) {
            HookTile.Hung h = hung.get(i);
            loads[i] = new Load(Clothesline.t(h.place, places), Clothesline.garment(h.stack));
        }
        LineVisuals.rope(d, pa, pb, sagPx, loads, tex, w);
        float yaw = (float) Math.toDegrees(Math.atan2(b.x - a.x, b.z - a.z));
        long time = tile.getLevel().getGameTime();
        for (int i = 0; i < loads.length; i++) {
            HookTile.Hung h = hung.get(i);
            Attach at = LineVisuals.attach(d, pa, pb, sagPx, loads, i, tex);
            ItemStack s = h.stack;
            boolean banner = s.getItem() instanceof BannerItem;
            pose.pushPose();
            pose.translate(at.x() / 16F, at.y() / 16F, at.z() / 16F);
            //repere de la piece : x local le long de la corde (de b vers a), plan de la piece = plan vertical de la corde
            pose.mulPose(Axis.YP.rotationDegrees(yaw + 90));
            pose.mulPose(Axis.ZP.rotationDegrees(-at.slope() * TILT));
            float swing = (float) Math.sin((time + partial) * 0.05 + h.place * 1.7) * 6F;
            pose.mulPose(Axis.XP.rotationDegrees(swing));
            //lot 11 : pieces plus grandes (avant 0,9 / 0,6 / 0,5 : 5 px de haut, perdues sous la corde)
            float k = banner ? BANNER : Clothesline.garment(s) ? GARMENT : OTHER;
            pose.scale(k, k, k);
            BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(s, tile.getLevel(), null, (int) origin.asLong() + h.place);
            pose.translate(0, -top(model), 0);
            Vec3 world = o.add(at.x() / 16.0, at.y() / 16.0 - 0.2, at.z() / 16.0);
            int lit = LevelRenderer.getLightColor(tile.getLevel(), BlockPos.containing(world));
            Minecraft.getInstance().getItemRenderer().render(s, ItemDisplayContext.FIXED, false, pose, buffers, lit, OverlayTexture.NO_OVERLAY, model);
            pose.popPose();
        }
    }

    private static float[] px(Vec3 v) {
        return new float[]{(float) v.x * 16F, (float) v.y * 16F, (float) v.z * 16F};
    }

    /**
     * Haut du modele d'objet en vue fixe (blocs, origine = centre du rendu) : plus haute face tournee vers le haut (les objets plats ont une
     * face par bord de pixel opaque : c'est le vrai haut du dessin, sans le vide transparent de l'icone), apres la transformation « fixe » du
     * modele, comme {@code ItemRenderer.render}. Sans face vers le haut (rendu en Java) : 0,25 (moitie d'une icone a l'echelle fixe).
     */
    static float top(BakedModel model) {
        Float cached = TOPS.get(model);
        if (cached != null) {
            return cached;
        }
        PoseStack ps = new PoseStack();
        BakedModel m = ForgeHooksClient.handleCameraTransforms(ps, model, ItemDisplayContext.FIXED, false);
        ps.translate(-0.5F, -0.5F, -0.5F);
        Matrix4f mat = ps.last().pose();
        RandomSource r = RandomSource.create(42L);
        float best = Float.NEGATIVE_INFINITY;
        for (BakedModel part : m.getRenderPasses(ItemStack.EMPTY, true)) {
            for (Direction side : new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                for (BakedQuad q : part.getQuads(null, side, r)) {
                    if (q.getDirection() != Direction.UP) {
                        continue;
                    }
                    int[] v = q.getVertices();
                    int stride = v.length / 4;
                    for (int i = 0; i < 4; i++) {
                        Vector4f p = mat.transform(new Vector4f(Float.intBitsToFloat(v[i * stride]), Float.intBitsToFloat(v[i * stride + 1]),
                              Float.intBitsToFloat(v[i * stride + 2]), 1F));
                        best = Math.max(best, p.y());
                    }
                }
            }
        }
        float top = best == Float.NEGATIVE_INFINITY ? 0.25F : best;
        TOPS.put(model, top);
        return top;
    }
}
