package fr.pioneers.farm.crustacean;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiTileEntityTypes;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.4 : registre des crustaces (demande de Guillaume : « ajouter le filet de peche et le casier a crabe, au moins 5 crustaces qui naissent dans
 * le monde, dont certains 100 % aquatiques ») : deux types d'entite (aquatique, rivage), chair crue et cuite de chaque espece comestible,
 * coquillage, oeufs d'apparition, filet de peche et casier a crabe ({@link TrapBlock}). Donnees et visuels : {@code tools/gc/farm2/crustaceans.py},
 * {@code crustacean_java.py}, {@code art_crustaceans.py}.
 */
public final class PiCrustaceans {

    private PiCrustaceans() {
    }

    public static void init() {
    }

    public static final TagKey<Item> BAITS = ItemTags.create(Pioneers.rl("crab_pot_baits"));

    public static final RegistryObject<EntityType<Crustacean>> AQUATIC = PiEntityTypes.ENTITY_TYPES.register("crustacean",
          () -> EntityType.Builder.<Crustacean>of(Crustacean::new, MobCategory.WATER_AMBIENT).sized(0.55F, 0.35F).clientTrackingRange(6).build("crustacean"));
    public static final RegistryObject<EntityType<Crustacean>> SHORE = PiEntityTypes.ENTITY_TYPES.register("shore_crab",
          () -> EntityType.Builder.<Crustacean>of(Crustacean::new, MobCategory.CREATURE).sized(0.55F, 0.35F).clientTrackingRange(6).build("shore_crab"));

    /** Chair crue et cuite, par espece comestible (cle : id de l'espece). */
    public static final Map<String, RegistryObject<Item>> RAW = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Item>> COOKED = new LinkedHashMap<>();

    static {
        for (CrustaceanDef d : GeneratedCrustaceans.SPECIES) {
            if (d.meat().isEmpty()) {
                continue;
            }
            RAW.put(d.id(), PiCoreRegistries.ITEMS.register("raw_" + d.id(), () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                  .nutrition(d.rawFood()).saturationMod(d.rawSaturation()).build()))));
            COOKED.put(d.id(), PiCoreRegistries.ITEMS.register("cooked_" + d.id(), () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                  .nutrition(d.cookedFood()).saturationMod(d.cookedSaturation()).build()))));
        }
    }

    public static final RegistryObject<Item> SEASHELL = PiCoreRegistries.ITEMS.register("seashell", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> AQUATIC_EGG = PiCoreRegistries.ITEMS.register("crustacean_spawn_egg",
          () -> new ForgeSpawnEggItem(AQUATIC, 0x28405C, 0xCE5C2C, new Item.Properties()));
    public static final RegistryObject<Item> SHORE_EGG = PiCoreRegistries.ITEMS.register("shore_crab_spawn_egg",
          () -> new ForgeSpawnEggItem(SHORE, 0x546C38, 0xB08038, new Item.Properties()));

    /** 1.0.3-beta (lot 11) : filets voisins dans un meme plan = un seul grand filet ({@link FishingNetBlock}). */
    public static final BlockEntry<TrapBlock> FISHING_NET = PiCoreRegistries.block("fishing_net", () -> new FishingNetBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.8F).sound(SoundType.WOOL).noOcclusion().ignitedByLava()));
    public static final BlockEntry<TrapBlock> CRAB_POT = PiCoreRegistries.block("crab_pot", () -> new TrapBlock(TrapBlock.Kind.POT,
          BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1F).sound(SoundType.WOOD).noOcclusion().ignitedByLava()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TrapTile>> TRAP_TILE = PiTileEntityTypes.VANILLA_TILES.register("crustacean_trap",
          () -> BlockEntityType.Builder.of(TrapTile::new, FISHING_NET.getBlock(), CRAB_POT.getBlock()).build(null));

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            event.put(AQUATIC.get(), Crustacean.createAttributes().build());
            event.put(SHORE.get(), Crustacean.createAttributes().build());
        }

        @SubscribeEvent
        public static void spawns(SpawnPlacementRegisterEvent event) {
            event.register(AQUATIC.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Crustacean::checkAquatic,
                  SpawnPlacementRegisterEvent.Operation.REPLACE);
            event.register(SHORE.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Crustacean::checkShore,
                  SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }
}
