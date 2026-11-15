package fr.pioneers.client.farm.leisure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.farm.leisure.GeneratedLeisure;
import fr.pioneers.farm.leisure.Instrument;
import fr.pioneers.farm.leisure.InstrumentItem;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

/**
 * 1.6 : instrument de musique porte par un joueur, vu en 3e personne : contre le corps (guitare, accordeon, tambour en bandouliere avec une
 * baguette dans chaque main) ou devant la bouche (flute qui joue, tenue comme une flute a bec). Le modele de l'objet en main est cache (vues
 * thirdperson a l'echelle 0, tools/gc/farm2/leisure.py) ; la flute au repos reste tenue en main par la couche vanilla.
 * Placements : {@link GeneratedLeisure#LAYER}, mouvements : {@link LeisureClient}.
 */
public class InstrumentLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public InstrumentLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull AbstractClientPlayer player, float limbSwing,
          float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        ItemStack stack = ItemStack.EMPTY;
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof InstrumentItem) {
                stack = player.getItemInHand(hand);
                break;
            }
        }
        if (!(stack.getItem() instanceof InstrumentItem item)) {
            return;
        }
        Instrument instrument = item.instrument;
        if (instrument == Instrument.FLUTE && !LeisureClient.playing(player, partialTick)) {
            return;
        }
        float age = LeisureClient.age(player, partialTick);
        float[] extra = LeisureClient.layerExtra(instrument, age, LeisureClient.now(partialTick));
        float[] p = GeneratedLeisure.LAYER[instrument.ordinal()];
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        pose.pushPose();
        (GeneratedLeisure.ON_HEAD[instrument.ordinal()] ? model.head : model.body).translateAndRotate(pose);
        pose.mulPose(Axis.XP.rotationDegrees(180));                             // +y en haut, +z devant le joueur
        pose.translate(p[0], p[1] + extra[2], p[2]);
        pose.mulPose(new Quaternionf().rotationXYZ(p[3] * Mth.DEG_TO_RAD, p[4] * Mth.DEG_TO_RAD, p[5] * Mth.DEG_TO_RAD));
        pose.mulPose(Axis.ZP.rotationDegrees(extra[0]));
        pose.scale(p[6] * extra[1], p[6], p[6]);
        LeisureClient.render(stack, instrument == Instrument.DRUM ? LeisureClient.extra(LeisureClient.DRUM_WORN) : LeisureClient.itemModel(stack, player),
              pose, buffers, light, false);
        pose.popPose();
        if (instrument == Instrument.DRUM) {
            for (int arm = 0; arm < 2; arm++) {                                  // une baguette dans chaque main (repere du bras)
                pose.pushPose();
                (arm == 0 ? model.rightArm : model.leftArm).translateAndRotate(pose);
                LeisureClient.place(pose, arm == 0 ? GeneratedLeisure.STICK_RIGHT : GeneratedLeisure.STICK_LEFT);
                pose.translate(GeneratedLeisure.GRIP, GeneratedLeisure.GRIP, 0);
                LeisureClient.render(stack, LeisureClient.extra(LeisureClient.DRUM_STICK), pose, buffers, light, arm == 1);
                pose.popPose();
            }
        }
    }
}
