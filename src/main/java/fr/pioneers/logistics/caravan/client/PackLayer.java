package fr.pioneers.logistics.caravan.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.logistics.caravan.CaravanData;
import fr.pioneers.logistics.caravan.PiCaravan;
import java.util.function.Function;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 10) : BAT dessine sur la bete (cheval : sacoches ; ane et mulet : paniers ; chameau, dromadaire : caisses), au niveau de
 * remplissage recu du serveur ({@code CaravanData.clientStage}, 0 vide a 3 plein) ; rabats qui s'ouvrent au relais (ouverture adoucie). Pose
 * dans le repere du corps du modele (suit la marche, le cabre, l'animal assis).
 */
public class PackLayer<T extends AbstractHorse, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private final Function<M, ModelPart> body;

    public PackLayer(RenderLayerParent<T, M> parent, Function<M, ModelPart> body) {
        super(parent);
        this.body = body;
    }

    @Override
    public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull T entity, float limbSwing, float limbSwingAmount,
          float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        CaravanData d = PiCaravan.get(entity);
        if (d == null || !d.pack || entity.isInvisible() || entity.isBaby()) {
            return;
        }
        float target = d.open ? 1 : 0;
        d.openAnim += Math.max(-0.06F, Math.min(0.06F, target - d.openAnim));
        CaravanModels.Pack pm = CaravanModels.pack(PiCaravan.style(entity), d.clientStage);
        for (int i = 0; i < pm.flaps().size(); i++) {
            pm.flaps().get(i).zRot = d.openAnim * fr.pioneers.logistics.caravan.client.GeneratedPack.OPEN_ANGLE * pm.dirs()[i];
        }
        pose.pushPose();
        body.apply(getParentModel()).translateAndRotate(pose);
        pm.root().render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(CaravanModels.PACK_TEXTURE)), light,
              LivingEntityRenderer.getOverlayCoords(entity, 0));
        pose.popPose();
    }
}
