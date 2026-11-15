package fr.pioneers.client.caravan;

import fr.pioneers.Pioneers;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.CamelRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.camel.Camel;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 10) : rendu du chameau (de Bactriane, DEUX bosses, laine brune : remplace le rendu vanilla) et du dromadaire (une bosse,
 * plus elance, robe sable) : couches {@link CaravanModels#BACTRIAN} / {@link CaravanModels#DROMEDARY} (memes parties que CamelModel), robes
 * {@code entity/caravan/<id>.png}, BAT ({@link PackLayer}, caisses).
 */
public class HerdCamelRenderer extends CamelRenderer {

    private final ResourceLocation texture;

    public HerdCamelRenderer(EntityRendererProvider.Context ctx, ModelLayerLocation layer, String texture) {
        super(ctx, layer);
        this.texture = Pioneers.rl("textures/entity/caravan/" + texture + ".png");
        addLayer(new PackLayer<>(this, m -> m.root().getChild("body")));
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull Camel camel) {
        return texture;
    }
}
