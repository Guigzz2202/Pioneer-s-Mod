package fr.pioneers.farm.device;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.farm.crow.Crow;
import fr.pioneers.farm.food.PlacedDishBlock;
import fr.pioneers.tech.kinetics.KineticEvents;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiSounds;
import fr.pioneers.core.registries.PiTileEntityTypes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registre de la ferme animee (1.3.1, spec {@code docs/specification-ferme-animee.md}) : blocs fonctionnels ({@link DeviceBlock.Kind}),
 * epouvantail, objets (compost, cadres de ruche, cire, oeuf feconde), corbeau, sons. Progres : evenements {@code farm_<x>} du declencheur
 * {@code pioneers:mechanics} ({@link #award}).
 */
public final class PiFarmDevices {

    private PiFarmDevices() {
    }

    public static void init() {
        PlacedDishBlock.init();            //1.4 : plats poses
        fr.pioneers.farm.food.DishShelfBlock.init();       //1.8 : etageres a plats
        fr.pioneers.farm.food.PlacedFoodBlock.init();      //1.9.2 : nourriture posee en tas
    }

    public static final TagKey<Item> FERTILIZERS = ItemTags.create(Pioneers.rl("fertilizers"));

    public static final Map<DeviceBlock.Kind, RegistryObject<DeviceBlock>> DEVICES = new EnumMap<>(DeviceBlock.Kind.class);

    static {
        for (DeviceBlock.Kind kind : DeviceBlock.Kind.values()) {
            String id = kind.id();
            if (kind.sprinkler()) {
                DEVICES.put(kind, PiCoreRegistries.block(id, () -> new DeviceBlock(kind, properties(kind)),
                      b -> new SprinklerItem(b, new Item.Properties(), "description.pioneers." + id)).block());
            } else {
                //1.0.4-beta (lot 28) : clapier sur deux cases (HutchBlock : case principale + case laterale)
                DEVICES.put(kind, PiCoreRegistries.block(id, () -> kind == DeviceBlock.Kind.RABBIT_HUTCH ? new HutchBlock(properties(kind))
                      : new DeviceBlock(kind, properties(kind))).block());
            }
        }
    }

    private static BlockBehaviour.Properties properties(DeviceBlock.Kind kind) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().noOcclusion().pushReaction(PushReaction.BLOCK);
        return switch (kind) {
            case BRONZE_SPRINKLER -> p.mapColor(MapColor.COLOR_ORANGE).strength(2F).sound(SoundType.COPPER);
            case STEEL_SPRINKLER, WEATHER_STATION, AUTO_FEEDER -> p.mapColor(MapColor.METAL).strength(2.5F).sound(SoundType.METAL);
            case INCUBATOR -> p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).lightLevel(s -> 4);
            default -> p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).ignitedByLava();
        };
    }

    public static final RegistryObject<ScarecrowBlock> SCARECROW = PiCoreRegistries.block("scarecrow", () -> new ScarecrowBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(1F).sound(SoundType.GRASS).noOcclusion().ignitedByLava()
                .pushReaction(PushReaction.DESTROY).lightLevel(s -> s.getValue(ScarecrowBlock.HEAD) == 2 && s.getValue(ScarecrowBlock.HALF)
                                                                                                    == DoubleBlockHalf.UPPER ? 12 : 0))).block();

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<DeviceTile>> DEVICE_TILE = PiTileEntityTypes.VANILLA_TILES.register("farm_device",
          () -> BlockEntityType.Builder.<DeviceTile>of((pos, state) -> (DeviceTile) ((EntityBlock) state.getBlock())
                .newBlockEntity(pos, state), validBlocks()).build(null));

    private static Block[] validBlocks() {
        List<Block> out = new ArrayList<>();
        DEVICES.values().forEach(r -> out.add(r.get()));
        out.add(SCARECROW.get());
        return out.toArray(Block[]::new);
    }

    // ------------------------------------------------------------------ objets

    public static final RegistryObject<Item> COMPOST = PiCoreRegistries.ITEMS.register("compost", () -> new BoneMealItem(new Item.Properties()));
    public static final RegistryObject<Item> HIVE_FRAME = PiCoreRegistries.ITEMS.register("hive_frame", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> HONEY_FRAME = PiCoreRegistries.ITEMS.register("honey_frame", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> BEESWAX = PiCoreRegistries.ITEMS.register("beeswax", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> FERTILE_EGG = PiCoreRegistries.ITEMS.register("fertile_egg",
          () -> new FertileEggItem(new Item.Properties().stacksTo(16)));

    // ------------------------------------------------------------------ corbeau, sons

    public static final RegistryObject<EntityType<Crow>> CROW = PiEntityTypes.ENTITY_TYPES.register("crow",
          () -> EntityType.Builder.of(Crow::new, MobCategory.AMBIENT).sized(0.45F, 0.45F).clientTrackingRange(10).build("crow"));
    public static final RegistryObject<Item> CROW_SPAWN_EGG = PiCoreRegistries.ITEMS.register("crow_spawn_egg",
          () -> new ForgeSpawnEggItem(CROW, 0x1C1C24, 0x4A4A6A, new Item.Properties()));
    public static final RegistryObject<SoundEvent> CROW_CAW = PiSounds.SOUNDS.register("farm.crow_caw",
          () -> SoundEvent.createVariableRangeEvent(Pioneers.rl("farm.crow_caw")));

    /** Progres : evenement {@code farm_<event>} pour les joueurs a 24 blocs. */
    public static void award(Level level, BlockPos pos, String event) {
        if (level instanceof ServerLevel server) {
            KineticEvents.award(server, pos, "farm_" + event);
        }
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            event.put(CROW.get(), Crow.createAttributes().build());
        }
    }
}
