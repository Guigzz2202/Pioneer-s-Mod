package fr.pioneers.logistics.warehouse;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.core.registries.PiTileEntityTypes;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.0.4-beta (lot 1, « L'entrepot ») : blocs de la logistique d'entrepot. Caisse d'entrepot (multibloc 3 x 3 x 3, 3 x 5 x 3 ou 3 x 7 x 3 :
 * cadre d'acier, panneaux de bois traite, ports, trappe), poulie d'entrepot (reseau de cordes qui tournent) et comptoir d'entrepot (liste,
 * demandes, colis) ; lot 3 : poteau de support des poulies ; 1.0.5-beta (lot 17) : interfaces d'entree et de sortie ; lot 45 : interface de sac. Ressources : {@code tools/gc/warehouse/warehouse.py} ; langue : {@code tools/gc/core/lang_p104.py}.
 */
public final class PiWarehouse {

    private PiWarehouse() {
    }

    public static void init() {
    }

    static BlockBehaviour.Properties steel() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 8.0F).sound(SoundType.METAL).noOcclusion()
              .requiresCorrectToolForDrops();
    }

    static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.5F, 4.0F).sound(SoundType.WOOD).noOcclusion();
    }

    private static <B extends Block> BlockEntry<B> block(String name, Supplier<B> block) {
        return PiCoreRegistries.block(name, block, b -> new WarehouseBlockItem(b, new Item.Properties()));
    }

    public static final BlockEntry<CratePartBlock> CRATE_FRAME = block("warehouse_crate_frame", () -> new CratePartBlock(CratePartBlock.Kind.FRAME, steel()));
    public static final BlockEntry<CratePartBlock> CRATE_PANEL = block("warehouse_crate_panel", () -> new CratePartBlock(CratePartBlock.Kind.PANEL, wood()));
    public static final BlockEntry<CratePartBlock> CRATE_PORT = block("warehouse_crate_port", () -> new CratePartBlock(CratePartBlock.Kind.PORT, steel()));
    public static final BlockEntry<CrateHatchBlock> CRATE_HATCH = block("warehouse_crate_hatch", () -> new CrateHatchBlock(wood()));
    public static final BlockEntry<CounterBlock> COUNTER = block("warehouse_counter", () -> new CounterBlock(wood()));
    // 1.0.5-beta (lot 17, Guillaume : « des blocs d'interface pour les entrees / sorties, le comptoir ne sert qu'aux commandes ») : interfaces
    // d'entree et de sortie (stations du reseau comme le comptoir : petite poulie au-dessus ; tile CounterTile au role INLET / OUTLET)
    public static final BlockEntry<CounterBlock> INLET = block("warehouse_inlet", () -> new CounterBlock(wood(), CounterTile.Role.INLET));
    public static final BlockEntry<CounterBlock> OUTLET = block("warehouse_outlet", () -> new CounterBlock(wood(), CounterTile.Role.OUTLET));
    // 1.0.5-beta (lot 45, retour 65 de Guillaume : « un bloc qui fasse office d'interface : On pose le sac dans le slot et l'interface lit le
    // contenu ensuite on peut lier des cables logistiques ») : INTERFACE DE SAC (contenu du sac pose expose aux cables et aux entonnoirs)
    public static final BlockEntry<fr.pioneers.tech.bronze.BronzeBlock> BAG_INTERFACE = block("bag_interface",
          () -> new fr.pioneers.tech.bronze.BronzeBlock(wood(), fr.pioneers.building.home.bag.BagInterfaceTile::new, null));
    // 1.0.4-beta (lot 27, Guillaume : « Supprime la poulie d'entrepot. Les poulies de bases sont suffisantes », « Supprime le poteau
    // d'entrepot ») : la petite poulie a axe vertical est la station (PulleyTile.stationWheel) ; remaps PiRemaps.P104_LOT27

    /** Tous les blocs, dans l'ordre de l'onglet creatif (CreativeSort les range par motif). */
    public static final List<BlockEntry<? extends Block>> ALL = List.of(CRATE_FRAME, CRATE_PANEL, CRATE_PORT, CRATE_HATCH, COUNTER, INLET,
          OUTLET, BAG_INTERFACE);

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<CratePartBlock.PortTile>> PORT_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "warehouse_crate_port", () -> BlockEntityType.Builder.of(CratePartBlock.PortTile::new, CRATE_PORT.getBlock()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<CrateHatchTile>> HATCH_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "warehouse_crate_hatch", () -> BlockEntityType.Builder.of(CrateHatchTile::new, CRATE_HATCH.getBlock()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<CounterTile>> COUNTER_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "warehouse_counter", () -> BlockEntityType.Builder.of(CounterTile::new, COUNTER.getBlock()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<CounterTile>> INLET_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "warehouse_inlet", () -> BlockEntityType.Builder.of(CounterTile::inlet, INLET.getBlock()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<CounterTile>> OUTLET_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "warehouse_outlet", () -> BlockEntityType.Builder.of(CounterTile::outlet, OUTLET.getBlock()).build(null));
    /** 1.0.5-beta (lot 45, retour 65) : tile de l'interface de sac. */
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<fr.pioneers.building.home.bag.BagInterfaceTile>> BAG_INTERFACE_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "bag_interface", () -> BlockEntityType.Builder.of(fr.pioneers.building.home.bag.BagInterfaceTile::new, BAG_INTERFACE.getBlock()).build(null));
}
