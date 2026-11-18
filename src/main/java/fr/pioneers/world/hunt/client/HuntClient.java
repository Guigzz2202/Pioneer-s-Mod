package fr.pioneers.world.hunt.client;

import fr.pioneers.Pioneers;
import fr.pioneers.world.wild.client.TrophyRenderer;
import fr.pioneers.world.hunt.HarpoonItem;
import fr.pioneers.world.hunt.MusketItem;
import fr.pioneers.world.hunt.PiHunt;
import fr.pioneers.world.hunt.RevolverItem;
import fr.pioneers.world.hunt.HuntRules;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 1.0.5-beta (lot 10) : client de la chasse : rendus (porte-fusil garni {@link GunRackRenderer}, trophees {@link TrophyRenderer}, harpon et
 * sa corde {@link HarpoonRenderer}) et proprietes d'objet des armes (images du modele) :
 * <ul>
 *   <li>mousquet : {@code pioneers:loaded} (0 / 1), {@code pioneers:reload} (avancement du chargement 0..1 : baguette, canon releve) ;</li>
 *   <li>revolver : {@code pioneers:reload} (1 pendant le rechargement : barillet ouvert), {@code pioneers:rounds} (cartouches / 6) ;</li>
 *   <li>harpon : {@code pioneers:thrown} (1 corde deroulee), {@code pioneers:aim} (1 bras arme).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HuntClient {

    private HuntClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PiHunt.GUN_RACK_TILE.get(), GunRackRenderer::new);
        event.registerBlockEntityRenderer(PiHunt.TROPHY_TILE.get(), TrophyRenderer::new);
        event.registerEntityRenderer(PiHunt.HARPOON_ENTITY.get(), HarpoonRenderer::new);
    }

    /** Avancement de l'usage (0..1) rapporte a {@code need} ticks, 0 si l'objet n'est pas en cours d'usage. */
    static float using(ItemStack stack, LivingEntity e, int need) {
        if (e == null || !e.isUsingItem() || e.getUseItem() != stack) {
            return 0F;
        }
        int used = stack.getUseDuration() - e.getUseItemRemainingTicks();
        return Math.max(0.01F, Math.min(1F, used / (float) Math.max(1, need)));
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(PiHunt.MUSKET.get(), Pioneers.rl("loaded"), (stack, level, e, seed) -> MusketItem.loaded(stack) ? 1F : 0F);
            ItemProperties.register(PiHunt.MUSKET.get(), Pioneers.rl("reload"),
                  (stack, level, e, seed) -> using(stack, e, MusketItem.reloadTicks(e instanceof Player p ? p : null)));
            ItemProperties.register(PiHunt.REVOLVER.get(), Pioneers.rl("reload"), (stack, level, e, seed) -> using(stack, e, 1) > 0 ? 1F : 0F);
            ItemProperties.register(PiHunt.REVOLVER.get(), Pioneers.rl("rounds"),
                  (stack, level, e, seed) -> RevolverItem.rounds(stack) / (float) HuntRules.REVOLVER_ROUNDS);
            ItemProperties.register(PiHunt.HARPOON.get(), Pioneers.rl("thrown"), (stack, level, e, seed) -> HarpoonItem.thrown(stack) != null ? 1F : 0F);
            ItemProperties.register(PiHunt.HARPOON.get(), Pioneers.rl("aim"), (stack, level, e, seed) -> using(stack, e, 10) > 0 ? 1F : 0F);
        });
    }
}
