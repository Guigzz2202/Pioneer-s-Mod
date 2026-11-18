package fr.pioneers.building.home.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.building.home.bag.BagItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Patch 1.0.3-beta, lot 7 (Guillaume : « le sac porte se voit sur le dos du joueur quand il est dans la barre d'action ou la main
 * secondaire ») : le premier sac A DOS de la barre d'action (hors case tenue en main) ou de la main secondaire, dessine sur le dos (modele de
 * l'objet, vue « fixe », un peu reduit), place sur le corps du modele (suit l'accroupissement) ; rien avec un plastron d'elytres ou invisible.
 */
public class BackpackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Taille sur le dos et ecart au dos (blocs, repere du corps). */
    public static final float SCALE = 1.6F, OUT = 0.27F, DOWN = 0.38F;

    public BackpackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** Sac a dos a montrer : barre d'action (sauf la case choisie, deja en main) puis main secondaire. */
    public static ItemStack worn(AbstractClientPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (i != inv.selected && s.getItem() instanceof BagItem b && b.kind.backpack()) {
                return s;
            }
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof BagItem b && b.kind.backpack() ? off : ItemStack.EMPTY;
    }

    @Override
    public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull AbstractClientPlayer player, float limbSwing,
          float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || player.getItemBySlot(EquipmentSlot.CHEST).canElytraFly(player)) {
            return;
        }
        ItemStack stack = worn(player);
        if (stack.isEmpty()) {
            return;
        }
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        //Repere du corps : y vers le bas, z vers le dos ; le modele de l'objet a sa poche au nord (-z) : demi-tour autour de x (haut en haut,
        //poche vers l'exterieur, bretelles contre le dos)
        pose.translate(0, DOWN, OUT);
        pose.mulPose(Axis.XP.rotationDegrees(180));
        pose.scale(SCALE, SCALE, SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
              player.level(), player.getId());
        pose.popPose();
    }
}
