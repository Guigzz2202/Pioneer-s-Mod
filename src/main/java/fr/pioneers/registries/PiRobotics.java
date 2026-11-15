package fr.pioneers.registries;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.robotics.ItemRobotUnit;
import fr.pioneers.robotics.ItemRoboticUpgrade;
import fr.pioneers.robotics.ItemZonePlanner;
import fr.pioneers.robotics.RobotUnitEntity;
import fr.pioneers.robotics.RoboticUpgrade;
import fr.pioneers.robotics.UnitKind;
import fr.pioneers.robotics.UnitTier;
import fr.pioneers.robotics.hub.BlockRoboticHubPart;
import fr.pioneers.robotics.hub.HubPart;
import fr.pioneers.robotics.hub.RoboticHubMenu;
import fr.pioneers.robotics.hub.TileEntityRoboticBay;
import fr.pioneers.robotics.hub.TileEntityRoboticCasing;
import fr.pioneers.robotics.hub.TileEntityRoboticPort;
import fr.pioneers.robotics.logistics.BlockLogisticChest;
import fr.pioneers.robotics.logistics.BlockLogisticMarker;
import fr.pioneers.robotics.logistics.BlockLogisticTerminal;
import fr.pioneers.robotics.logistics.BlockRoboticRelay;
import fr.pioneers.robotics.logistics.BlockRoboticRoad;
import fr.pioneers.robotics.logistics.RoboticRelayMenu;
import fr.pioneers.robotics.logistics.TileEntityLogisticChest;
import fr.pioneers.robotics.logistics.TileEntityLogisticMarker;
import fr.pioneers.robotics.logistics.TileEntityLogisticTerminal;
import fr.pioneers.robotics.logistics.TileEntityRoboticRelay;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Robotique : le hub multibloc (boitier, port, porte de baie), le relais, les points logistiques (balise, coffres), la route, les unites
 * des quatre tiers, le module d'extension, les cartes d'amelioration, le planificateur de zone, et les entites des unites.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PiRobotics {

    private PiRobotics() {
    }

    public static void init() {
        //Charge la classe (et donc ses enregistrements differes)
    }

    // ------------------------------------------------------------------
    // Hub multibloc
    // ------------------------------------------------------------------

    /** Hub robotique : multibloc du socle (sans Mekanism). */
    private static BlockBehaviour.Properties hubProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    public static final BlockEntry<BlockRoboticHubPart> CASING = PiCoreRegistries.block("robotic_casing",
          () -> new BlockRoboticHubPart(HubPart.CASING, hubProperties()));
    public static final BlockEntry<BlockRoboticHubPart> PORT = PiCoreRegistries.block("robotic_port",
          () -> new BlockRoboticHubPart(HubPart.PORT, hubProperties()));
    public static final BlockEntry<BlockRoboticHubPart> BAY = PiCoreRegistries.block("robotic_bay",
          () -> new BlockRoboticHubPart(HubPart.BAY, hubProperties()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TileEntityRoboticCasing>> CASING_TILE = PiTileEntityTypes.VANILLA_TILES.register("robotic_casing",
          () -> BlockEntityType.Builder.of(TileEntityRoboticCasing::new, CASING.getBlock()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TileEntityRoboticPort>> PORT_TILE = PiTileEntityTypes.VANILLA_TILES.register("robotic_port",
          () -> BlockEntityType.Builder.of(TileEntityRoboticPort::new, PORT.getBlock()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TileEntityRoboticBay>> BAY_TILE = PiTileEntityTypes.VANILLA_TILES.register("robotic_bay",
          () -> BlockEntityType.Builder.of(TileEntityRoboticBay::new, BAY.getBlock()).build(null));
    public static final RegistryObject<MenuType<RoboticHubMenu>> HUB_MENU = PiCoreRegistries.MENUS.register("robotic_hub",
          () -> IForgeMenuType.create(RoboticHubMenu::client));

    public static Supplier<BlockEntityType<TileEntityRoboticCasing>> casingTile() {
        return CASING_TILE;
    }

    public static Supplier<BlockEntityType<TileEntityRoboticBay>> bayTile() {
        return BAY_TILE;
    }

    // ------------------------------------------------------------------
    // Balises logistiques
    // ------------------------------------------------------------------

    /** Balise logistique (une seule : son role se choisit dans ses reglages). */
    public static final BlockEntry<BlockLogisticMarker> MARKER = PiCoreRegistries.plainBlock("logistic_marker",
          () -> new BlockLogisticMarker(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.6F).sound(SoundType.METAL).noOcclusion()
                .lightLevel(state -> 7)));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TileEntityLogisticMarker>> MARKER_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "logistic_marker", () -> BlockEntityType.Builder.of(TileEntityLogisticMarker::new, MARKER.getBlock()).build(null));

    /** Coffres logistiques (27, 36 et 54 emplacements). */
    public static final Map<BlockLogisticChest.Size, BlockEntry<BlockLogisticChest>> CHESTS = new EnumMap<>(BlockLogisticChest.Size.class);

    static {
        for (BlockLogisticChest.Size size : BlockLogisticChest.Size.values()) {
            CHESTS.put(size, PiCoreRegistries.plainBlock(size.id(), () -> new BlockLogisticChest(size, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                  .strength(2.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(state -> 4))));
        }
    }

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TileEntityLogisticChest>> CHEST_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "logistic_chest", () -> BlockEntityType.Builder.of(TileEntityLogisticChest::new, CHESTS.values().stream()
                .map(BlockEntry::getBlock).toArray(BlockLogisticChest[]::new)).build(null));

    /** Terminal logistique : le joueur commande, le reseau livre. */
    public static final BlockEntry<BlockLogisticTerminal> TERMINAL = PiCoreRegistries.plainBlock(
          "logistic_terminal", () -> new BlockLogisticTerminal(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .strength(2.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(state -> 6)));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TileEntityLogisticTerminal>> TERMINAL_TILE =
          PiTileEntityTypes.VANILLA_TILES.register("logistic_terminal", () -> BlockEntityType.Builder.of(
                TileEntityLogisticTerminal::new, TERMINAL.getBlock()).build(null));

    /** Route robotique : les unites au sol y vont plus vite. */
    public static final BlockEntry<BlockRoboticRoad> ROAD_BLOCK = PiCoreRegistries.plainBlock("robotic_road",
          () -> new BlockRoboticRoad(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5F, 6.0F).sound(SoundType.STONE)
                .requiresCorrectToolForDrops()));
    public static final Supplier<Block> ROAD = ROAD_BLOCK::getBlock;

    // ------------------------------------------------------------------
    // Relais robotique
    // ------------------------------------------------------------------

    public static final BlockEntry<BlockRoboticRelay> RELAY = PiCoreRegistries.block("robotic_relay", () -> new BlockRoboticRelay(
          BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TileEntityRoboticRelay>> RELAY_TILE = PiTileEntityTypes.VANILLA_TILES.register("robotic_relay",
          () -> BlockEntityType.Builder.of(TileEntityRoboticRelay::new, RELAY.getBlock()).build(null));
    public static final RegistryObject<MenuType<RoboticRelayMenu>> RELAY_MENU = PiCoreRegistries.MENUS.register("robotic_relay",
          () -> IForgeMenuType.create(RoboticRelayMenu::client));

    // ------------------------------------------------------------------
    // Unites, module et outil
    // ------------------------------------------------------------------

    public static final Map<UnitKind, Map<UnitTier, RegistryObject<Item>>> UNITS = new EnumMap<>(UnitKind.class);

    public static String unitId(UnitKind kind, UnitTier tier) {
        return kind.id() + "_mk" + tier.level();
    }

    public static ResourceLocation unitItemId(UnitKind kind, UnitTier tier) {
        return Pioneers.rl(unitId(kind, tier));
    }

    static {
        for (UnitKind kind : UnitKind.values()) {
            Map<UnitTier, RegistryObject<Item>> tiers = new EnumMap<>(UnitTier.class);
            for (UnitTier tier : UnitTier.values()) {
                tiers.put(tier, PiItems.ITEMS.register(unitId(kind, tier), () -> new ItemRobotUnit(kind, tier)));
            }
            UNITS.put(kind, tiers);
        }
    }

    /** Module d'extension de baie : +5 places dans un hub (3 au plus). */
    public static final RegistryObject<Item> BAY_MODULE = PiItems.ITEMS.register("robotic_bay_module", () -> new Item(new Item.Properties().stacksTo(16)) {
        @Override
        public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
            Tooltips tips = Tooltips.of(tooltip);
            tips.desc("item.pioneers.robotic_bay_module.desc");
        }
    });
    public static final RegistryObject<ItemZonePlanner> ZONE_PLANNER = PiItems.ITEMS.register("zone_planner", ItemZonePlanner::new);
    /** Cartes d'amelioration (hubs et relais). */
    public static final Map<RoboticUpgrade, RegistryObject<Item>> UPGRADES = new EnumMap<>(RoboticUpgrade.class);

    static {
        for (RoboticUpgrade upgrade : RoboticUpgrade.values()) {
            UPGRADES.put(upgrade, PiItems.ITEMS.register(upgrade.itemId(), () -> new ItemRoboticUpgrade(upgrade)));
        }
    }

    // ------------------------------------------------------------------
    // Entites
    // ------------------------------------------------------------------

    public static final Map<UnitKind, RegistryObject<EntityType<RobotUnitEntity>>> UNIT_ENTITIES = new EnumMap<>(UnitKind.class);

    static {
        for (UnitKind kind : UnitKind.values()) {
            float width = switch (kind) {
                case DRONE -> 0.7F;
                case ROBOT -> 0.7F;
                //Moins d'un bloc de large : le rover passe les portillons et les couloirs d'un bloc (le modele deborde un peu)
                case ROVER -> 0.95F;
                case TURTLE -> 0.9F;
            };
            float height = switch (kind) {
                case DRONE -> 0.55F;
                case ROBOT -> 1.6F;
                case ROVER -> 1.1F;
                case TURTLE -> 0.8F;
            };
            String id = "unit_" + kind.id();
            UNIT_ENTITIES.put(kind, PiEntityTypes.ENTITY_TYPES.register(id,
                  () -> EntityType.Builder.<RobotUnitEntity>of((type, level) -> new RobotUnitEntity(type, level, kind), MobCategory.MISC)
                        .sized(width, height)
                        .clientTrackingRange(10)
                        .updateInterval(2)
                        .fireImmune()
                        .noSave()
                        .build(Pioneers.rl(id).toString())));
        }
    }

    public static EntityType<RobotUnitEntity> unitEntity(UnitKind kind) {
        return UNIT_ENTITIES.get(kind).get();
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (RegistryObject<EntityType<RobotUnitEntity>> type : UNIT_ENTITIES.values()) {
            event.put(type.get(), RobotUnitEntity.createAttributes().build());
        }
    }
}
