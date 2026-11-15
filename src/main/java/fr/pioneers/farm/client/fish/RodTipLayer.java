package fr.pioneers.farm.client.fish;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.farm.fish.PiBobber;
import fr.pioneers.farm.fish.PiRodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 1.0.2-beta (Guillaume : « le fil de la canne bouge de son point d'origine lors du lancer ») : couche du joueur, vue en 3e personne, qui ne
 * dessine RIEN : elle releve ou se trouve le bout de la canne tenue, avec les memes transformations que la couche vanilla des objets en main
 * (ItemInHandLayer.renderArmWithItem : bras pose par l'animation de cette image, rotations -90 / 180, decalage de la main, vue
 * thirdperson du modele cuit), puis la ramene dans le monde (rotation inverse de la vue, position de la camera). {@link BobberRenderer} y
 * attache le fil pendant tout le lancer ({@link RodClient#thirdPersonTip}).
 */
public class RodTipLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public RodTipLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull AbstractClientPlayer player, float limbSwing,
          float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (PiBobber.of(player) == null) {
            return;
        }
        InteractionHand hand = PiBobber.rodHand(player);
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof PiRodItem)) {
            return;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean left = arm == HumanoidArm.LEFT;
        pose.pushPose();
        getParentModel().translateToHand(arm, pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
        RodClient.applyModelView(pose, player, stack, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left);
        Vector4f v = pose.last().pose().transform(RodClient.tipPoint(player));
        pose.popPose();
        //Repere de la vue (rotation de la camera) -> monde
        Vector3f w = new Vector3f(v.x, v.y, v.z);
        RenderSystem.getInverseViewRotationMatrix().transform(w);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        RodClient.storeThirdPerson(player, cam.x + w.x, cam.y + w.y, cam.z + w.z);
    }
}
