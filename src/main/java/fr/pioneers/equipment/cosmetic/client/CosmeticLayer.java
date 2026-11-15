package fr.pioneers.equipment.cosmetic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.equipment.cosmetic.CosmeticCap;
import fr.pioneers.equipment.cosmetic.CosmeticItem;
import fr.pioneers.equipment.cosmetic.CosmeticPiece;
import fr.pioneers.equipment.cosmetic.Wardrobe;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * 1.12.0 (lot E4) : calque des cosmetiques sur les joueurs (soi en 3e personne, les autres joueurs, l'apercu de l'inventaire). Pour chaque
 * piece montree ({@link Wardrobe#shows}) : le modele de la piece reprend la pose du joueur et est dessine ; l'armure de cet emplacement
 * est retiree du rendu par {@link CosmeticClient} (elle garde sa protection). Les pieces a verre (casque-bulle) passent en translucide.
 */
public class CosmeticLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Le modele cuit ne depend pas du type d'entite (memes membres que le modele du joueur). */
    @SuppressWarnings("unchecked")
    private static HumanoidModel<AbstractClientPlayer> cast(HumanoidModel<? extends LivingEntity> model) {
        return (HumanoidModel<AbstractClientPlayer>) model;
    }

    public CosmeticLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull AbstractClientPlayer player, float limb,
          float limbAmount, float partial, float age, float headYaw, float headPitch) {
        if (player.isInvisible() || CosmeticClient.hidden(player)) {
            return;
        }
        Wardrobe w = CosmeticCap.get(player);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0);
        for (CosmeticPiece piece : CosmeticPiece.ALL) {
            int i = piece.ordinal();
            if (!w.shows(i) || !(w.get(i).getItem() instanceof CosmeticItem item)) {
                continue;
            }
            HumanoidModel<AbstractClientPlayer> model = cast(CosmeticModels.model(item.outfit, piece));
            getParentModel().copyPropertiesTo(model);
            model.setAllVisible(true);
            boolean glass = CosmeticModels.translucent(item.outfit, piece);
            VertexConsumer vc = buffers.getBuffer(glass ? RenderType.entityTranslucent(CosmeticModels.texture(item.outfit))
                  : RenderType.armorCutoutNoCull(CosmeticModels.texture(item.outfit)));
            model.renderToBuffer(pose, vc, light, overlay, 1F, 1F, 1F, 1F);
        }
    }
}
