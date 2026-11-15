package fr.pioneers.client.forge;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.forge.ForgeMetals;
import fr.pioneers.forge.HeatGlow;
import fr.pioneers.forge.HeatedMetal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 15, Guillaume : « pieces de forge chaudes : vraie texture de l'objet forge avec un aspect rougeatre / incandescent, plus
 * l'aspect de billette ») : le metal chauffe se dessine comme l'objet qu'il deviendra ({@link HeatedMetal#lookOf} : piece a tremper, resultat
 * de la recette en cours, lingot du metal), avec son vrai modele et ses vues (inventaire, main, sol, cadre, JEI), teinte vers la couleur de
 * son incandescence ({@link #tint}), puis rougeoyant (passes additives {@link #glowPasses} ; 1.0.5-beta lot 19 : clarte cible toujours croissante
 * avec la temperature, {@link HeatGlow}) et lumineux tant qu'il chauffe. Sans objet vise (forge sans recette choisie) : la billette d'avant
 * (modele {@link #BILLET}, teinte de {@code ForgeClientSetup}). Pas de texture par piece : le modele de l'objet est reutilise.
 */
public class HeatedMetalItemRenderer extends BlockEntityWithoutLevelRenderer {

    /** Modele de repli (la billette d'avant), enregistre par {@code ForgeClientSetup.models}. */
    public static final ResourceLocation BILLET = Pioneers.rl("item/heated_metal_billet");

    private static HeatedMetalItemRenderer instance;

    private HeatedMetalItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static HeatedMetalItemRenderer instance() {
        if (instance == null) {
            instance = new HeatedMetalItemRenderer();
        }
        return instance;
    }

    /** Clarte moyenne de la texture par sprite (pixels opaques ; atlas recharge = nouveaux sprites : cle faible). */
    private static final java.util.Map<TextureAtlasSprite, Float> MEAN = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * Teinte multiplicative (RGB 0..1, premiere passe) d'un metal a {@code tempC} dont la texture a la clarte moyenne {@code meanLum}.
     * 1.0.5-beta (lot 19) : calculee par {@link HeatGlow} (clarte cible toujours croissante avec la temperature, meme sur une texture sombre).
     */
    public static float[] tint(float tempC, float meanLum) {
        return HeatGlow.passes(HeatGlow.factor(tempC, meanLum))[0];
    }

    /**
     * Passes LUMINEUSES (additives, {@code RenderType.eyes} : texture x couleur ajoutee a l'image), autant que la clarte cible en demande
     * ({@link HeatGlow#passes}, au plus {@link HeatGlow#MAX_ADD}) ; tableau vide sous 450 degres ou si la teinte suffit.
     */
    public static float[][] glowPasses(float tempC, float meanLum) {
        float[][] all = HeatGlow.passes(HeatGlow.factor(tempC, meanLum));
        return java.util.Arrays.copyOfRange(all, 1, all.length);
    }

    /** Couleur d'UNE passe lumineuse (toutes les passes ont la meme ; null s'il n'y en a pas). */
    @org.jetbrains.annotations.Nullable
    public static float[] glowAdd(float tempC, float meanLum) {
        float[][] add = glowPasses(tempC, meanLum);
        return add.length == 0 ? null : add[0];
    }

    /** Clarte moyenne (0..1) des pixels opaques du sprite de l'objet vise (0,45 si illisible). */
    static float meanLum(TextureAtlasSprite sprite) {
        Float known = MEAN.get(sprite);
        if (known != null) {
            return known;
        }
        float sum = 0;
        int n = 0;
        try {
            int w = sprite.contents().width(), h = sprite.contents().height();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int p = sprite.getPixelRGBA(0, x, y); //NativeImage : ABGR
                    if ((p >>> 24) < 128) {
                        continue;
                    }
                    sum += HeatGlow.lum((p & 0xFF) / 255F, ((p >> 8) & 0xFF) / 255F, ((p >> 16) & 0xFF) / 255F);
                    n++;
                }
            }
        } catch (RuntimeException e) {
            n = 0;
        }
        float mean = n == 0 ? 0.45F : sum / n;
        MEAN.put(sprite, mean);
        return mean;
    }

    /** Lumiere d'une piece a {@code tempC} : au moins son incandescence (0..15). */
    public static int light(float tempC, int light) {
        int glow = (ForgeMetals.glow(tempC) >>> 24) * 15 / 255;
        return LightTexture.pack(Math.max(glow, LightTexture.block(light)), Math.max(glow, LightTexture.sky(light)));
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext context, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers,
          int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer items = mc.getItemRenderer();
        boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        ItemStack look = HeatedMetal.lookOf(stack, mc.level);
        pose.pushPose();
        //ItemRenderer a deja decale de -0,5 (modele builtin/entity sans vue propre) : on revient au centre, le modele vise refait ses vues
        pose.translate(0.5F, 0.5F, 0.5F);
        if (look.isEmpty()) {
            items.render(stack, context, left, pose, buffers, light, overlay, mc.getModelManager().getModel(BILLET));
        } else {
            float temp = HeatedMetal.temperatureNow(stack, mc.level);
            BakedModel model = items.getModel(look, mc.level, null, 0);
            float[][] passes = HeatGlow.passes(HeatGlow.factor(temp, meanLum(model.getParticleIcon(net.minecraftforge.client.model.data.ModelData.EMPTY))));
            float[] k = passes[0];
            MultiBufferSource tinted = type -> new Tinted(buffers.getBuffer(type), k[0], k[1], k[2]);
            //Inventaire : l'objet vise garde SON eclairage (tetes d'outils en 3D : lumiere de cote ; objets plats : de face)
            boolean relight = context == ItemDisplayContext.GUI && model.usesBlockLight() && buffers instanceof MultiBufferSource.BufferSource;
            if (relight) {
                ((MultiBufferSource.BufferSource) buffers).endBatch();
                Lighting.setupFor3DItems();
            }
            items.render(look, context, left, pose, tinted, light(temp, light), overlay, model);
            //Passes lumineuses : memes quadrilateres, melange additif (la piece s'eclaircit en chauffant, meme sur une texture sombre)
            RenderType eyes = RenderType.eyes(TextureAtlas.LOCATION_BLOCKS);
            for (int i = 1; i < passes.length; i++) {
                float[] add = passes[i];
                MultiBufferSource glowing = type -> new Tinted(buffers.getBuffer(eyes), add[0], add[1], add[2]);
                items.render(look, context, left, pose, glowing, LightTexture.FULL_BRIGHT, overlay, model);
            }
            if (relight) {
                ((MultiBufferSource.BufferSource) buffers).endBatch();
                Lighting.setupForFlatItems();
            }
        }
        pose.popPose();
    }

    /** Sommets dont la couleur est multipliee par une teinte. */
    private record Tinted(VertexConsumer delegate, float r, float g, float b) implements VertexConsumer {

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int a) {
            delegate.color(Math.round(red * r), Math.round(green * g), Math.round(blue * b), a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float a, float u, float v, int overlay, int light,
              float nx, float ny, float nz) {
            delegate.vertex(x, y, z, red * r, green * g, blue * b, a, u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public void defaultColor(int red, int green, int blue, int a) {
            delegate.defaultColor(Math.round(red * r), Math.round(green * g), Math.round(blue * b), a);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
