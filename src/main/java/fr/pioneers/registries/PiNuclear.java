package fr.pioneers.registries;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.nuclear.CoriumBlock;
import fr.pioneers.nuclear.DosimeterItem;
import fr.pioneers.nuclear.DryStorageCaskBlock;
import fr.pioneers.nuclear.HazmatArmorItem;
import fr.pioneers.nuclear.RadiationData;
import fr.pioneers.nuclear.RadioactiveBlock;
import fr.pioneers.nuclear.ReactorBlock;
import fr.pioneers.nuclear.RtgBlock;
import fr.pioneers.nuclear.WastePackageBlock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * Age nucleaire (0.17.0) : blocs et objets du code nucleaire (le combustible et les machines viennent du catalogue de l'age industriel).
 * Corium et corium refroidi (fusion du coeur), chateau d'entreposage a sec, dosimetre, combinaison anti-radiation ; 0.17.1 : colis de
 * stockage geologique, generateur a radio-isotope.
 */
public final class PiNuclear {

    private PiNuclear() {
    }

    public static void init() {
    }

    /** 1.0 (lot 3) : blocs fonctionnels des reacteurs a fission. */
    public static final Map<ReactorBlock.Kind, BlockEntry<ReactorBlock>> REACTOR = new EnumMap<>(ReactorBlock.Kind.class);

    static {
        for (ReactorBlock.Kind kind : ReactorBlock.Kind.values()) {
            boolean glows = kind == ReactorBlock.Kind.FUEL_CHANNEL || kind == ReactorBlock.Kind.FAST_FUEL_CHANNEL || kind == ReactorBlock.Kind.CONFINEMENT_COIL
                            || kind == ReactorBlock.Kind.FRAGMENT_COLLECTOR
                            || kind == ReactorBlock.Kind.DIRECT_CONVERTER || kind == ReactorBlock.Kind.HELICAL_COIL;
            REACTOR.put(kind, PiCoreRegistries.block(kind.id(), () -> ReactorBlock.create(kind, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                  .strength(5.0F, 30.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                  .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)
                  .lightLevel(state -> glows && state.getValue(ReactorBlock.ACTIVE) ? 10 : 0))));
        }
    }

    public static final BlockEntry<CoriumBlock> CORIUM = PiCoreRegistries.block("corium", () -> new CoriumBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.COLOR_ORANGE).strength(50.0F, 1200.0F).sound(SoundType.BASALT).lightLevel(state -> 12).randomTicks()
          .requiresCorrectToolForDrops().emissiveRendering((s, l, p) -> true).hasPostProcess((s, l, p) -> true)));
    public static final BlockEntry<RadioactiveBlock> COOLED_CORIUM = PiCoreRegistries.block("cooled_corium", () -> new RadioactiveBlock(
          RadiationData.COOLED_CORIUM, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(30.0F, 1200.0F).sound(SoundType.BASALT)
                .requiresCorrectToolForDrops()));
    public static final BlockEntry<DryStorageCaskBlock> DRY_STORAGE_CASK = PiCoreRegistries.block("dry_storage_cask", () -> new DryStorageCaskBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(25.0F, 1200.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                .noOcclusion()));
    /** 0.17.1 : colis de stockage geologique, generateur a radio-isotope. */
    public static final BlockEntry<WastePackageBlock> WASTE_PACKAGE = PiCoreRegistries.block("waste_package", () -> new WastePackageBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(20.0F, 1200.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                .noOcclusion()));
    public static final BlockEntry<RtgBlock> RTG = PiCoreRegistries.block("rtg", () -> new RtgBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.METAL).strength(8.0F, 600.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
          .lightLevel(state -> state.getValue(RtgBlock.ACTIVE) ? 6 : 0)));
    public static final RegistryObject<BlockEntityType<RtgBlock.Tile>> RTG_TILE = PiTileEntityTypes.VANILLA_TILES.register("rtg",
          () -> BlockEntityType.Builder.of(RtgBlock.Tile::new, RTG.getBlock()).build(null));

    public static final RegistryObject<Item> DOSIMETER = PiCoreRegistries.ITEMS.register("dosimeter", () -> new DosimeterItem(new Item.Properties()
          .stacksTo(1)));
    public static final RegistryObject<Item> HAZMAT_HELMET = hazmat("hazmat_helmet", ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> HAZMAT_CHESTPLATE = hazmat("hazmat_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> HAZMAT_LEGGINGS = hazmat("hazmat_leggings", ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> HAZMAT_BOOTS = hazmat("hazmat_boots", ArmorItem.Type.BOOTS);

    private static RegistryObject<Item> hazmat(String name, ArmorItem.Type type) {
        return PiCoreRegistries.ITEMS.register(name, () -> new HazmatArmorItem(type, new Item.Properties().rarity(Rarity.UNCOMMON)));
    }

    /** Onglet Equipement : dosimetre puis combinaison. */
    public static List<RegistryObject<Item>> equipment() {
        return List.of(DOSIMETER, HAZMAT_HELMET, HAZMAT_CHESTPLATE, HAZMAT_LEGGINGS, HAZMAT_BOOTS);
    }
}
