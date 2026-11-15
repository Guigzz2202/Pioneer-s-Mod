package fr.pioneers.client.farm.herd;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.Pioneers;
import fr.pioneers.farm.herd.GeneratedHerd;
import fr.pioneers.farm.herd.PiHerd;
import net.minecraft.client.model.RabbitModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Rabbit;

/**
 * 1.0.4-beta (lot 9) : rendu des lapins (remplace celui du vanilla) : robe de la RACE ({@code textures/entity/herd/rabbit/<race>.png}),
 * echelle de la race, oreilles tombantes du belier ({@link HerdModels.Lop}), poil long de l'angora (couche {@link Fur}, cachee une fois
 * tondu). Sans race connue (lapin tueur, donnees pas encore recues) : robe vanilla de sa variante.
 */
public class HerdRabbitRenderer extends MobRenderer<Rabbit, RabbitModel<Rabbit>> {

    private final RabbitModel<Rabbit> plain, lop;

    public HerdRabbitRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new RabbitModel<>(ctx.bakeLayer(HerdModels.RABBIT)), 0.3F);
        plain = model;
        lop = new HerdModels.Lop(ctx.bakeLayer(HerdModels.RABBIT_LOP));
        addLayer(new Fur(this, new RabbitModel<>(ctx.bakeLayer(HerdModels.RABBIT_FUR))));
    }

    @Override
    public void render(Rabbit rabbit, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        GeneratedHerd.RabbitBreed b = PiHerd.rabbitBreed(rabbit);
        model = b != null && b.lop() ? lop : plain;
        super.render(rabbit, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    protected void scale(Rabbit rabbit, PoseStack pose, float partialTicks) {
        GeneratedHerd.RabbitBreed b = PiHerd.rabbitBreed(rabbit);
        if (b != null) {
            pose.scale(b.scale(), b.scale(), b.scale());
        }
    }

    @Override
    public ResourceLocation getTextureLocation(Rabbit rabbit) {
        GeneratedHerd.RabbitBreed b = PiHerd.rabbitBreed(rabbit);
        if (b != null) {
            return Pioneers.rl("textures/entity/herd/rabbit/" + b.id() + ".png");
        }
        String name = net.minecraft.ChatFormatting.stripFormatting(rabbit.getName().getString());
        if ("Toast".equals(name)) {
            return new ResourceLocation("textures/entity/rabbit/toast.png");
        }
        return new ResourceLocation("textures/entity/rabbit/" + switch (rabbit.getVariant()) {
            case WHITE -> "white";
            case BLACK -> "black";
            case GOLD -> "gold";
            case SALT -> "salt";
            case WHITE_SPLOTCHED -> "white_splotched";
            case EVIL -> "caerbannog";
            default -> "brown";
        } + ".png");
    }

    /** Poil long de l'angora : la coque gonflee suit le modele ; cachee quand l'angora vient d'etre tondu (repousse : laine prete). */
    static final class Fur extends RenderLayer<Rabbit, RabbitModel<Rabbit>> {

        static final ResourceLocation TEXTURE = Pioneers.rl("textures/entity/herd/rabbit/angora_fur.png");
        private final RabbitModel<Rabbit> fur;

        Fur(RenderLayerParent<Rabbit, RabbitModel<Rabbit>> parent, RabbitModel<Rabbit> fur) {
            super(parent);
            this.fur = fur;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Rabbit rabbit, float limbSwing, float limbSwingAmount, float partialTicks,
              float ageInTicks, float netHeadYaw, float headPitch) {
            GeneratedHerd.RabbitBreed b = PiHerd.rabbitBreed(rabbit);
            var d = PiHerd.get(rabbit);
            if (b == null || !b.longHair() || rabbit.isInvisible() || d == null || d.woolTimer > 0) {
                return;
            }
            coloredCutoutModelCopyLayerRender(getParentModel(), fur, TEXTURE, pose, buffers, light, rabbit, limbSwing, limbSwingAmount, ageInTicks,
                  netHeadYaw, headPitch, partialTicks, 1F, 1F, 1F);
        }
    }
}
