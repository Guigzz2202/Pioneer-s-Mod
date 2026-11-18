package fr.pioneers.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.space.RocketEntity;
import fr.pioneers.space.RocketTier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Les maquettes de toutes les fusees, pour l'entite, l'objet et la table d'assemblage.
 */
public class RocketModels {

    private final EntityModel<RocketEntity>[] models;

    @SuppressWarnings("unchecked")
    public RocketModels(Function<ModelLayerLocation, ModelPart> baker) {
        models = new EntityModel[RocketTier.values().length];
        for (int i = 0; i < models.length; i++) {
            models[i] = new SimpleRocketModel(baker.apply(GeneratedRocketModels.layer(i + 1)));
        }
    }

    public static ResourceLocation texture(RocketTier tier) {
        return Pioneers.rl("textures/entity/rocket_tier_" + tier.level + ".png");
    }

    public void render(RocketTier tier, PoseStack pose, MultiBufferSource buffers, Function<ResourceLocation, RenderType> renderType, int light, int overlay) {
        render(tier, pose, buffers, renderType, light, overlay, Stage.ALL);
    }

    /** Partie de la fusee a dessiner : toute la fusee, l'etage superieur seul (apres separation) ou le premier etage seul (qui tombe). */
    public enum Stage {
        ALL, UPPER, LOWER
    }

    public void render(RocketTier tier, PoseStack pose, MultiBufferSource buffers, Function<ResourceLocation, RenderType> renderType, int light, int overlay,
          Stage stage) {
        EntityModel<RocketEntity> model = models[tier.ordinal()];
        VertexConsumer buffer = buffers.getBuffer(renderType.apply(texture(tier)));
        if (stage == Stage.ALL || !(model instanceof StagedRocketModel staged)) {
            model.renderToBuffer(pose, buffer, light, overlay, 1, 1, 1, 1);
            return;
        }
        ModelPart root = staged.root();
        List<String> upper = upperParts(tier, root);
        if (stage == Stage.UPPER) {
            //La racine n'a pas de transformation : chaque piece se dessine seule a sa place
            for (String name : upper) {
                root.getChild(name).render(pose, buffer, light, overlay);
            }
        } else {
            for (String name : upper) {
                root.getChild(name).visible = false;
            }
            model.renderToBuffer(pose, buffer, light, overlay, 1, 1, 1, 1);
            for (String name : upper) {
                root.getChild(name).visible = true;
            }
        }
    }

    private final Map<RocketTier, List<String>> upperParts = new EnumMap<>(RocketTier.class);

    /** Pieces de l'etage superieur (prefixe u_, liste generee par tools/gc/rk/rockets2.py). */
    private List<String> upperParts(RocketTier tier, ModelPart root) {
        return upperParts.computeIfAbsent(tier, t -> {
            List<String> list = new ArrayList<>();
            for (String name : GeneratedRocketModels.upperParts(t.level)) {
                if (root.hasChild(name)) {
                    list.add(name);
                }
            }
            return List.copyOf(list);
        });
    }
}
