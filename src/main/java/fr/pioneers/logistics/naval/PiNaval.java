package fr.pioneers.logistics.naval;

import fr.pioneers.Pioneers;
import fr.pioneers.core.common.projector.Projections;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.logistics.naval.HarborBlock;
import fr.pioneers.logistics.naval.HarborBlueprint;
import fr.pioneers.logistics.naval.HarborEquipment;
import fr.pioneers.logistics.naval.HarborEquipmentBlock;
import fr.pioneers.logistics.naval.HarborEquipmentItem;
import fr.pioneers.logistics.naval.HarborEquipmentTile;
import fr.pioneers.logistics.naval.HarborInterfaceBlock;
import fr.pioneers.logistics.naval.HarborInterfaceTile;
import fr.pioneers.logistics.naval.HarborTile;
import fr.pioneers.logistics.naval.NavigationChartItem;
import fr.pioneers.logistics.naval.QuayBlock;
import fr.pioneers.logistics.naval.SeaBeaconBlock;
import fr.pioneers.logistics.naval.SeaBeaconItem;
import fr.pioneers.logistics.naval.ShipEntity;
import fr.pioneers.logistics.naval.ShipItem;
import fr.pioneers.logistics.naval.ShipKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiTileEntityTypes;

/**
 * 0.20.11 : la marine marchande : 28 navires (7 sortes x 4 tiers, objets {@code <tier>_<sorte>}), le port MKI a MKIV, l'entite des navires
 * (vue jamais sauvegardee) et la tile du port.
 */
public final class PiNaval {

    private PiNaval() {
    }

    public static void init() {
        Projections.addProvider(HarborBlueprint::forBlock);
    }

    /** Navires par id d'objet, dans l'ordre de l'onglet (sorte puis tier). */
    public static final Map<String, RegistryObject<Item>> SHIPS = new LinkedHashMap<>();

    static {
        for (ShipKind kind : ShipKind.values()) {
            for (int t = 0; t < 4; t++) {
                int tier = t;
                SHIPS.put(kind.id(t), PiCoreRegistries.ITEMS.register(kind.id(t), () -> new ShipItem(kind, tier, new Item.Properties())));
            }
        }
    }

    public static final List<BlockEntry<HarborBlock>> HARBORS = new ArrayList<>();

    static {
        for (int t = 0; t < 4; t++) {
            int tier = t;
            HARBORS.add(PiCoreRegistries.block(TieredMachineBlock.TIERS[t] + "_harbor", () -> new HarborBlock(tier, BlockBehaviour.Properties.of()
                  .mapColor(MapColor.METAL).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops())));
        }
    }

    /** 0.20.14 : dalle de quai et equipements de quai du port multibloc. */
    public static final BlockEntry<QuayBlock> QUAY = PiCoreRegistries.block("harbor_quay", () -> new QuayBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.STONE).strength(2.5F, 9.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    /** 0.20.25 : interface du port (dalle qui donne acces aux reserves de la capitainerie par toutes ses faces). */
    public static final BlockEntry<HarborInterfaceBlock> INTERFACE = PiCoreRegistries.block("harbor_interface", () -> new HarborInterfaceBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5F, 9.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<HarborInterfaceTile>> INTERFACE_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "harbor_interface", () -> BlockEntityType.Builder.of((p, s) -> new HarborInterfaceTile(PiNaval.INTERFACE_TILE.get(), p, s),
                INTERFACE.getBlock()).build(null));

    /** 0.20.27 : balise maritime (bouee a feu) et carte de navigation qui relie les balises. */
    public static final BlockEntry<SeaBeaconBlock> BEACON = PiCoreRegistries.block("sea_beacon", () -> new SeaBeaconBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.COLOR_RED).strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 12)),
          b -> new SeaBeaconItem(b, new Item.Properties()));

    public static final RegistryObject<Item> NAVIGATION_CHART = PiCoreRegistries.ITEMS.register("navigation_chart",
          () -> new NavigationChartItem(new Item.Properties().stacksTo(1)));

    public static final Map<HarborEquipment, BlockEntry<HarborEquipmentBlock>> EQUIPMENT = new EnumMap<>(HarborEquipment.class);

    static {
        for (HarborEquipment e : HarborEquipment.values()) {
            EQUIPMENT.put(e, PiCoreRegistries.block(e.id, () -> new HarborEquipmentBlock(e, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                  .strength(3.0F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()),
                  b -> new HarborEquipmentItem(b, new Item.Properties(), "description.pioneers." + e.id)));
        }
    }

    public static final RegistryObject<BlockEntityType<HarborEquipmentTile>> EQUIPMENT_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "harbor_equipment", () -> BlockEntityType.Builder.of(HarborEquipmentTile::new, EQUIPMENT.values().stream().map(BlockEntry::getBlock)
                .toArray(Block[]::new)).build(null));

    public static final RegistryObject<BlockEntityType<HarborTile>> HARBOR_TILE = PiTileEntityTypes.VANILLA_TILES.register("harbor",
          () -> BlockEntityType.Builder.of(HarborTile::new, HARBORS.stream().map(BlockEntry::getBlock).toArray(Block[]::new)).build(null));

    public static final RegistryObject<EntityType<ShipEntity>> SHIP_ENTITY = PiEntityTypes.ENTITY_TYPES.register("ship",
          () -> EntityType.Builder.<ShipEntity>of(ShipEntity::new, MobCategory.MISC)
                .sized(1.0F, 1.0F)
                .clientTrackingRange(16)
                .updateInterval(1)
                .fireImmune()
                .noSave()
                .build(Pioneers.rl("ship").toString()));

    /** Ordre de l'onglet creatif : ports, puis navires par sorte. */
    public static List<Item> creative() {
        List<Item> out = new ArrayList<>();
        for (BlockEntry<HarborBlock> h : HARBORS) {
            out.add(h.asItem());
        }
        out.add(QUAY.asItem());
        out.add(INTERFACE.asItem());
        out.add(BEACON.asItem());
        out.add(NAVIGATION_CHART.get());
        for (BlockEntry<HarborEquipmentBlock> e : EQUIPMENT.values()) {
            out.add(e.asItem());
        }
        for (RegistryObject<Item> s : SHIPS.values()) {
            out.add(s.get());
        }
        return out;
    }
}
