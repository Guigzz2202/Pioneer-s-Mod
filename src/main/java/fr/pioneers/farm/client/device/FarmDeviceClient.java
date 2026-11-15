package fr.pioneers.farm.client.device;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.Pioneers;
import fr.pioneers.farm.crow.Crow;
import fr.pioneers.farm.device.FertileEggItem;
import fr.pioneers.farm.device.PiFarmDevices;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/** Client de la ferme animee : rendu des blocs ({@link DeviceRenderer}), modeles en plus (tetes d'arroseur, buste et tetes de l'epouvantail),
 * corbeau, teinte des oeufs fecondes. */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FarmDeviceClient {

    private FarmDeviceClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PiFarmDevices.DEVICE_TILE.get(), DeviceRenderer::new);
        //1.0.4-beta (lot 28) : aquarium (eau, sable, poissons et crustaces qui nagent)
        event.registerBlockEntityRenderer(fr.pioneers.farm.aquarium.PiAquarium.TILE.get(), fr.pioneers.farm.client.aquarium.AquariumRenderer::new);
        event.registerEntityRenderer(PiFarmDevices.CROW.get(), CrowRenderer::new);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CrowModel.LAYER, CrowModel::create);
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        event.register(DeviceRenderer.BRONZE_HEAD);
        event.register(DeviceRenderer.STEEL_HEAD);
        event.register(DeviceRenderer.SCARECROW_BODY);
        for (ResourceLocation h : DeviceRenderer.SCARECROW_HEADS) {
            event.register(h);
        }
    }

    @SubscribeEvent
    public static void itemColours(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 0 ? FertileEggItem.colour(stack) : -1, PiFarmDevices.FERTILE_EGG.get());
    }

    /** Rendu du corbeau. */
    public static class CrowRenderer extends MobRenderer<Crow, CrowModel> {

        private static final ResourceLocation TEXTURE = Pioneers.rl("textures/entity/crow.png");

        /**
         * 1.0.5-beta (lot 13, tailles a l'echelle reelle) : le modele (tete a 0,6 bloc) est dessine a la moitie : corneille debout de 0,3 m
         * (tools/fauna/sizes.py CROW). 1.0.5-beta (lot 21, « credible plutot que realiste ») : aux trois quarts (0,45 m, lisible a cote du joueur).
         */
        public static final float SCALE = 0.75F;

        public CrowRenderer(EntityRendererProvider.Context context) {
            super(context, new CrowModel(context.bakeLayer(CrowModel.LAYER)), 0.25F * SCALE);
        }

        @Override
        protected void scale(@NotNull Crow crow, @NotNull PoseStack pose, float partialTick) {
            pose.scale(SCALE, SCALE, SCALE);
        }

        @Override
        public ResourceLocation getTextureLocation(Crow crow) {
            return TEXTURE;
        }
    }
}
