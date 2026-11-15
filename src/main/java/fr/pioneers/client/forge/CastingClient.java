package fr.pioneers.client.forge;

import fr.pioneers.Pioneers;
import fr.pioneers.forge.casting.ClayBucketItem;
import fr.pioneers.forge.casting.CrucibleTongsItem;
import fr.pioneers.registries.PiFluids;
import fr.pioneers.registries.PiForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Forge primaire, ingenieur « casting », cote client : rendus du creuset d'argile (metal en fusion, solides, braises), du moule pose (metal
 * qui fige) et du soufflet a main (planche animee) ; predicat {@code pioneers:crucible} de la pince (0 vide, 0,5 creuset froid, 1 en
 * fusion).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CastingClient {

    private CastingClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PiForge.CLAY_CRUCIBLE_TILE.get(), ClayCrucibleRenderer::new);
        event.registerBlockEntityRenderer(PiForge.PLACED_MOLD_TILE.get(), PlacedMoldRenderer::new);
        event.registerBlockEntityRenderer(PiForge.PLACED_ANVIL_MOLD_TILE.get(), AnvilMoldRenderer::new);
        event.registerBlockEntityRenderer(PiForge.TOOL_RACK_TILE.get(), ToolRackRenderer::new);
        event.registerBlockEntityRenderer(PiForge.HAND_BELLOWS_TILE.get(), HandBellowsRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(PiForge.CRUCIBLE_TONGS.get(), Pioneers.rl("crucible"),
              (stack, level, entity, seed) -> CrucibleTongsItem.visual(stack, level != null ? level : Minecraft.getInstance().level)));
        event.enqueueWork(() -> ItemProperties.register(PiForge.CLAY_BUCKET.get(), Pioneers.rl("filled"),
              (stack, level, entity, seed) -> ClayBucketItem.contents(stack).isEmpty() ? 0 : 1));
        //1.10.1 : image du geste de l'allume-feu
        event.enqueueWork(FireStarterClient::registerProperties);
    }

    /** 1.2.0 : le liquide du seau en argile prend la teinte du fluide (calque 1). */
    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> {
            if (layer != 1) {
                return 0xFFFFFFFF;
            }
            FluidStack fluid = ClayBucketItem.contents(stack);
            if (fluid.isEmpty()) {
                return 0xFFFFFFFF;
            }
            //1.6 (unification des seaux) : couleur affichee des fluides du mod (leur teinte de rendu est blanche), sinon la teinte (eau)
            int own = PiFluids.averageColor(fluid.getFluid());
            return own != 0 ? 0xFF000000 | own : IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);
        }, PiForge.CLAY_BUCKET.get());
    }
}
