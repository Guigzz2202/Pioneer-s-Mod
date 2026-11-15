package fr.pioneers.client.farm.herd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.client.caravan.HerdHorseModel;
import fr.pioneers.farm.herd.GeneratedHerd;
import fr.pioneers.farm.herd.PiHerd;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.DyeableHorseArmorItem;
import net.minecraft.world.item.HorseArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 9) : rendu des chevaux (remplace celui du vanilla) : robe de la RACE ({@code textures/entity/herd/horse/<race>.png} : robe,
 * crins, balzanes, pommelures, pie, fanons ; T:farm2/herd.py horse_texture) et ECHELLE de la race (poney 0,72 ... percheron 1,18), bardes
 * vanilla ; 1.0.4-beta (lot 10) : bat des caravanes. Sans race connue : robe vanilla de sa variante (les marques vanilla ne sont plus dessinees : la race porte les siennes).
 * 1.0.4-beta (lot 33, Guillaume : « Les modeles de lama et de chevaux sont a ameliorer selon la race ») : SILHOUETTE de la race
 * ({@link HerdShapes}) sur le modele, la BARDE ({@link Armor}, meme silhouette) et les CRINS en plus ({@link Hair} : criniere, toupet,
 * queue, fanons ; texture {@code <race>_hair.png}) ; le bat (PackLayer) suit le corps.
 */
public class HerdHorseRenderer extends AbstractHorseRenderer<Horse, HorseModel<Horse>> {

    public HerdHorseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HerdHorseModel<>(ctx.bakeLayer(ModelLayers.HORSE)), 1.1F);
        addLayer(new Armor(this, new HerdHorseModel<>(ctx.bakeLayer(ModelLayers.HORSE_ARMOR))));
        addLayer(new Hair(this));
        //1.0.4-beta (lot 10) : bat des caravanes (sacoches de cuir, remplies a vue)
        addLayer(new fr.pioneers.client.caravan.PackLayer<>(this, m -> ((HerdHorseModel<Horse>) m).body()));
    }

    @Nullable
    static GeneratedHerdShapes.HorseShape shapeOf(Horse horse) {
        GeneratedHerd.HorseBreed b = PiHerd.horseBreed(horse);
        return b != null && b.kind() == 0 ? HerdShapes.horse(b.id()) : null;
    }

    @Override
    public void render(@NotNull Horse horse, float yaw, float partialTicks, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light) {
        ((HerdHorseModel<Horse>) model).shape = shapeOf(horse);
        super.render(horse, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    protected void scale(@NotNull Horse horse, @NotNull PoseStack pose, float partialTicks) {
        GeneratedHerd.HorseBreed b = PiHerd.horseBreed(horse);
        if (b != null) {
            pose.scale(b.scale(), b.scale(), b.scale());
        }
        super.scale(horse, pose, partialTicks);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull Horse horse) {
        GeneratedHerd.HorseBreed b = PiHerd.horseBreed(horse);
        if (b != null && b.kind() == 0) {
            return Pioneers.rl("textures/entity/herd/horse/" + b.id() + ".png");
        }
        return new ResourceLocation("textures/entity/horse/horse_" + switch (horse.getVariant()) {
            case WHITE -> "white";
            case CREAMY -> "creamy";
            case CHESTNUT -> "chestnut";
            case BROWN -> "brown";
            case BLACK -> "black";
            case GRAY -> "gray";
            default -> "darkbrown";
        } + ".png");
    }

    /** Barde : la couche vanilla (HorseArmorLayer) sur un modele a la silhouette de la race. */
    static final class Armor extends RenderLayer<Horse, HorseModel<Horse>> {

        private final HerdHorseModel<Horse> armor;

        Armor(RenderLayerParent<Horse, HorseModel<Horse>> parent, HerdHorseModel<Horse> armor) {
            super(parent);
            this.armor = armor;
        }

        @Override
        public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull Horse horse, float limbSwing,
              float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack stack = horse.getArmor();
            if (!(stack.getItem() instanceof HorseArmorItem item)) {
                return;
            }
            getParentModel().copyPropertiesTo(armor);
            armor.shape = shapeOf(horse);
            armor.prepareMobModel(horse, limbSwing, limbSwingAmount, partialTick);
            armor.setupAnim(horse, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            float r = 1F, g = 1F, b = 1F;
            if (item instanceof DyeableHorseArmorItem dye) {
                int c = dye.getColor(stack);
                r = (c >> 16 & 255) / 255F;
                g = (c >> 8 & 255) / 255F;
                b = (c & 255) / 255F;
            }
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(item.getTexture()));
            armor.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, r, g, b, 1F);
        }
    }

    /** Crins en plus de la race (modele aux memes parties que le cheval, meme animation et meme silhouette). */
    static final class Hair extends RenderLayer<Horse, HorseModel<Horse>> {

        private final Map<String, HerdHorseModel<Horse>> models = new HashMap<>();

        Hair(RenderLayerParent<Horse, HorseModel<Horse>> parent) {
            super(parent);
        }

        @Override
        public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull Horse horse, float limbSwing,
              float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            GeneratedHerdShapes.HorseShape s = shapeOf(horse);
            if (s == null || s.hair().length == 0 || horse.isInvisible()) {
                return;
            }
            HerdHorseModel<Horse> m = models.computeIfAbsent(s.id(), k -> new HerdHorseModel<>(HerdShapes.hairLayer(s).bakeRoot()));
            getParentModel().copyPropertiesTo(m);
            m.shape = s;
            m.prepareMobModel(horse, limbSwing, limbSwingAmount, partialTick);
            m.setupAnim(horse, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(Pioneers.rl("textures/entity/herd/horse/" + s.id() + "_hair.png")));
            m.renderToBuffer(pose, vc, light, LivingEntityRenderer.getOverlayCoords(horse, 0F), 1F, 1F, 1F, 1F);
        }
    }
}
