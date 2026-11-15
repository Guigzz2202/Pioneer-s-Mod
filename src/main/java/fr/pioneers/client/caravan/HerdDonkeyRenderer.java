package fr.pioneers.client.caravan;

import fr.pioneers.Pioneers;
import net.minecraft.client.model.ChestedHorseModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 10) : rendu de l'ane (gris brun a raie de mulet, museau et ventre clairs) et du mulet (bai, crins sombres) du mod, coffre
 * vanilla, BAT ({@link PackLayer}, paniers d'osier). Remplace {@code ChestedHorseRenderer} (memes echelles : ane 0,87, mulet 0,92).
 */
public class HerdDonkeyRenderer<T extends AbstractChestedHorse> extends AbstractHorseRenderer<T, ChestedHorseModel<T>> {

    private final ResourceLocation texture;

    public HerdDonkeyRenderer(EntityRendererProvider.Context ctx, ModelLayerLocation layer, float scale, String texture) {
        super(ctx, new HerdHorseModel.Chested<>(ctx.bakeLayer(layer)), scale);
        this.texture = Pioneers.rl("textures/entity/caravan/" + texture + ".png");
        addLayer(new PackLayer<>(this, m -> ((HerdHorseModel.Chested<T>) m).body()));
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull T entity) {
        return texture;
    }
}
