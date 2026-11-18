package fr.pioneers.farm.client;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.GeneratedFarm;
import fr.pioneers.farm.client.fish.BobberRenderer;
import fr.pioneers.farm.client.crustacean.CrustaceanHud;
import fr.pioneers.farm.client.crustacean.CrustaceanModels;
import fr.pioneers.farm.client.crustacean.CrustaceanRenderer;
import fr.pioneers.farm.client.fish.FishHud;
import fr.pioneers.farm.client.fish.FishModels;
import fr.pioneers.farm.client.fish.FishRenderer;
import fr.pioneers.farm.PiFishing;
import fr.pioneers.farm.crustacean.PiCrustaceans;
import fr.pioneers.farm.PiKitchen;
import fr.pioneers.farm.fish.FishRoeBlock;
import fr.pioneers.farm.fish.FishRoeItem;
import fr.pioneers.farm.fish.GeneratedFish;
import fr.pioneers.farm.fish.PiBobber;
import fr.pioneers.farm.fish.PiRodItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.entity.player.Player;
import fr.pioneers.farm.food.CannedFoodItem;
import fr.pioneers.farm.food.FarmFoodItem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client de la cuisine : rendu des postes ({@link StationRenderer}), teinte du contenu des bocaux (confiture, legumes : couleur de la recolte
 * d'origine) et de l'etiquette des conserves (couleur du plat) ; calque 1 des icones.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FarmClient {

    private FarmClient() {
    }

    private static Map<String, Integer> colours;

    /** Couleur d'un objet (table generee) ; -1 si inconnue. */
    public static int colour(Item item) {
        if (colours == null) {
            Map<String, Integer> m = new HashMap<>();
            for (int i = 0; i < GeneratedFarm.COLOR_KEYS.length; i++) {
                m.put(GeneratedFarm.COLOR_KEYS[i], GeneratedFarm.COLOR_VALUES[i]);
            }
            colours = m;
        }
        Integer c = colours.get(BuiltInRegistries.ITEM.getKey(item).toString());
        return c == null ? -1 : c;
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PiKitchen.STATION_TILE.get(), StationRenderer::new);
        //Elevage (1.0.3-beta lot 8) : un rendu par entite de race ; les types vanilla gardent le leur (convertis en leur race a l'entree)
        AnimalRenderers.renderers(event);
        //Poissons et peche : type generique et une entite par espece
        event.registerEntityRenderer(PiFishing.FISH.get(), FishRenderer::new);
        for (var t : PiFishing.SPECIES_TYPES) {
            event.registerEntityRenderer(t.get(), FishRenderer::new);
        }
        event.registerEntityRenderer(PiFishing.BOBBER.get(), BobberRenderer::new);
        //1.4 : crustaces
        event.registerEntityRenderer(PiCrustaceans.AQUATIC.get(), CrustaceanRenderer::new);
        event.registerEntityRenderer(PiCrustaceans.SHORE.get(), CrustaceanRenderer::new);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        AnimalRenderers.layers(event);
        for (int i = 0; i < FishRenderer.LAYERS.length; i++) {
            int species = i;
            event.registerLayerDefinition(FishRenderer.LAYERS[i], () -> FishModels.create(species));
        }
        for (int i = 0; i < CrustaceanRenderer.LAYERS.length; i++) {
            int shape = i;
            event.registerLayerDefinition(CrustaceanRenderer.LAYERS[i], () -> CrustaceanModels.create(shape));
        }
    }

    @SubscribeEvent
    public static void blockColours(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, layer) -> GeneratedFish.SPECIES[Math.min(state.getValue(FishRoeBlock.SPECIES),
              GeneratedFish.SPECIES.length - 1)].accent(), PiFishing.ROE_BLOCK.get());
    }

    /** 1.0.2-beta : releve du bout de la canne en 3e personne (attache du fil, {@link fr.pioneers.farm.client.fish.RodTipLayer}). */
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer) {
                renderer.addLayer(new fr.pioneers.farm.client.fish.RodTipLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        FarmHud.ENTITY_PROVIDERS.add(AnimalHud::card);
        FarmHud.ENTITY_PROVIDERS.add(FishHud::card);
        FarmHud.ENTITY_PROVIDERS.add(CrustaceanHud::card);
        event.enqueueWork(() -> {
            for (Item rod : new Item[]{PiFishing.BAMBOO_ROD.get(), PiFishing.BRONZE_ROD.get(), PiFishing.STEEL_ROD.get()}) {
                //Canne lancee (fil), et ployee selon la tension du combat
                ItemProperties.register(rod, Pioneers.rl("cast"), (stack, level, entity, seed) -> {
                    if (!(entity instanceof Player p)) {
                        return 0;
                    }
                    boolean held = p.getMainHandItem() == stack || p.getOffhandItem() == stack && !(p.getMainHandItem().getItem() instanceof PiRodItem);
                    return held && PiBobber.of(p) != null ? 1 : 0;
                });
                ItemProperties.register(rod, Pioneers.rl("bend"), (stack, level, entity, seed) -> {
                    if (!(entity instanceof Player p)) {
                        return 0;
                    }
                    PiBobber b = PiBobber.of(p);
                    return b != null && b.state() == PiBobber.FIGHT ? b.tension() : 0;
                });
            }
        });
    }

    @SubscribeEvent
    public static void itemColours(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 1 ? jarColour(stack) : -1, PiKitchen.FOODS.get("jam").get(), PiKitchen.FOODS.get("pickles").get());
        event.register((stack, layer) -> {
            if (layer != 1) {
                return -1;
            }
            Item dish = CannedFoodItem.dish(stack);
            int c = dish == null ? -1 : colour(dish);
            return c == -1 ? 0xC8CCD2 : c;
        }, PiKitchen.FOODS.get("canned_food").get());
        event.register((stack, layer) -> layer == 1 || stack.is(PiFishing.FISH_ROE.get()) && layer == 0
              ? GeneratedFish.SPECIES[FishRoeItem.species(stack)].accent() : -1, PiFishing.FISH_ROE.get());
    }

    private static int jarColour(ItemStack stack) {
        Item src = FarmFoodItem.source(stack);
        if (src != null) {
            int c = colour(src);
            if (c != -1) {
                return c;
            }
        }
        return stack.getItem() instanceof FarmFoodItem food ? food.def().colour() : -1;
    }
}
