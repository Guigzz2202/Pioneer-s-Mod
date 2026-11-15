package fr.pioneers.registries;

import fr.pioneers.computing.ComputerBlock;
import fr.pioneers.computing.ComputerMenu;
import fr.pioneers.computing.ComputerTile;
import fr.pioneers.computing.ComputingMenu;
import fr.pioneers.computing.DataDiskItem;
import fr.pioneers.computing.DataLinkItem;
import fr.pioneers.computing.FlightComputerItem;
import fr.pioneers.computing.LogicBlock;
import fr.pioneers.computing.LogicKind;
import fr.pioneers.computing.LogicTile;
import fr.pioneers.computing.NetworkTerminalTile;
import fr.pioneers.computing.ScreenBlock;
import fr.pioneers.computing.ScreenTile;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;

/**
 * Informatique industrielle (0.17.0) : configurateur de liaison, armoire a relais, automate programmable, ecran de supervision ;
 * ordinateur programmable en PioScript et disquette (0.17.1).
 * Visuels, recettes et langue : {@code tools/gc/computing/assets.py}.
 */
public final class PiComputing {

    private PiComputing() {
    }

    public static void init() {
    }

    public static final BlockEntry<LogicBlock> RELAY_CABINET = PiCoreRegistries.block("relay_cabinet", () -> new LogicBlock(LogicKind.RELAY, logic()));
    public static final BlockEntry<LogicBlock> PLC = PiCoreRegistries.block("plc", () -> new LogicBlock(LogicKind.PLC, logic()));
    public static final BlockEntry<ScreenBlock> SUPERVISION_SCREEN = PiCoreRegistries.block("supervision_screen",
          () -> new ScreenBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).sound(SoundType.METAL)
                .requiresCorrectToolForDrops().lightLevel(state -> 6)));
    public static final RegistryObject<Item> DATA_LINK = PiCoreRegistries.ITEMS.register("data_link", () -> new DataLinkItem(new Item.Properties().stacksTo(1)));
    // 0.17.1 : ordinateur programmable (PioScript) et disquette
    public static final BlockEntry<ComputerBlock> COMPUTER = PiCoreRegistries.block("computer", () -> new ComputerBlock(logic()));
    public static final RegistryObject<Item> DATA_DISK = PiCoreRegistries.ITEMS.register("data_disk", () -> new DataDiskItem(new Item.Properties().stacksTo(1)));
    // 0.18.0 : ordinateur de vol (avionique des sondes et des fusees), programme par flight.flash
    public static final RegistryObject<Item> FLIGHT_COMPUTER = PiCoreRegistries.ITEMS.register("flight_computer",
          () -> new FlightComputerItem(new Item.Properties().stacksTo(16), false));
    public static final RegistryObject<Item> PROGRAMMED_FLIGHT_COMPUTER = PiCoreRegistries.ITEMS.register("programmed_flight_computer",
          () -> new FlightComputerItem(new Item.Properties().stacksTo(16), true));

    /** 0.20.46 : terminal de reseau (vue d'ensemble du reseau numerique : donnees, calcul, machines, recherche, conseils). */
    public static final BlockEntry<MachineBlock> NETWORK_TERMINAL = PiCoreRegistries.block("network_terminal", () -> new MachineBlock(logic(),
          () -> PiComputing.NETWORK_TERMINAL_TILE.get(), NetworkTerminalTile::new));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<NetworkTerminalTile>> NETWORK_TERMINAL_TILE =
          PiTileEntityTypes.VANILLA_TILES.register("network_terminal", () -> BlockEntityType.Builder.of(NetworkTerminalTile::new,
                NETWORK_TERMINAL.getBlock()).build(null));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<LogicTile>> LOGIC_TILE = PiTileEntityTypes.VANILLA_TILES.register("logic_controller",
          () -> BlockEntityType.Builder.of(LogicTile::new, RELAY_CABINET.getBlock(), PLC.getBlock()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<ScreenTile>> SCREEN_TILE = PiTileEntityTypes.VANILLA_TILES.register("supervision_screen",
          () -> BlockEntityType.Builder.of(ScreenTile::new, SUPERVISION_SCREEN.getBlock()).build(null));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<ComputerTile>> COMPUTER_TILE = PiTileEntityTypes.VANILLA_TILES.register("computer",
          () -> BlockEntityType.Builder.of(ComputerTile::new, COMPUTER.getBlock()).build(null));
    public static final RegistryObject<MenuType<ComputerMenu>> COMPUTER_MENU = PiCoreRegistries.MENUS.register("computer",
          () -> IForgeMenuType.create(ComputerMenu::client));

    public static final RegistryObject<MenuType<ComputingMenu>> MENU = PiCoreRegistries.MENUS.register("computing",
          () -> IForgeMenuType.create(ComputingMenu::client));

    /** Ordre dans l'onglet creatif (age industriel). */
    public static final List<Supplier<? extends ItemLike>> TAB = List.of(() -> NETWORK_TERMINAL, () -> DATA_LINK.get(), () -> RELAY_CABINET, () -> PLC, () -> SUPERVISION_SCREEN,
          () -> COMPUTER, () -> DATA_DISK.get(), () -> FLIGHT_COMPUTER.get(), () -> PROGRAMMED_FLIGHT_COMPUTER.get());

    private static BlockBehaviour.Properties logic() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
              .noOcclusion().lightLevel(state -> state.getValue(MachineBlock.ACTIVE) ? 6 : 0);
    }
}
