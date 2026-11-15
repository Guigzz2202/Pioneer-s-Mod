package fr.pioneers.core.registries;

import fr.pioneers.core.item.WrenchItem;
import fr.pioneers.core.machine.MachineMenu;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.logistics.transport.buffer.BufferBlock;
import fr.pioneers.logistics.transport.buffer.BufferTile;
import fr.pioneers.logistics.transport.buffer.BufferType;
import fr.pioneers.logistics.transport.buffer.EnergyBufferBlock;
import fr.pioneers.logistics.transport.buffer.HeatBufferBlock;
import fr.pioneers.logistics.transport.buffer.PressureBufferBlock;
import fr.pioneers.logistics.transport.heat.ElectricHeaterBlock;
import fr.pioneers.logistics.transport.heat.ElectricHeaterTile;
import fr.pioneers.logistics.transport.legacy.LegacyCableBlock;
import fr.pioneers.logistics.transport.legacy.LegacyCableTile;
import fr.pioneers.logistics.transport.legacy.LegacyFamily;
import fr.pioneers.logistics.transport.legacy.LegacyFiberBlock;
import fr.pioneers.logistics.transport.logistic.CableLevel;
import fr.pioneers.logistics.transport.logistic.LogisticCableBlock;
import fr.pioneers.logistics.transport.logistic.LogisticCableTile;
import fr.pioneers.logistics.transport.pressure.AirCompressorBlock;
import fr.pioneers.logistics.transport.pressure.AirCompressorTile;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * Transport du socle Pioneer's : cable logistique (1.11 : 6 niveaux opaques et transparents, supraconducteur), buffers en quatre tiers
 ({@code <tier>_<famille>_buffer}), compresseur d'air, radiateur, cle. Les 60 anciens cables (1.10) sont des blocs herites convertis au
 * chargement ({@link #LEGACY_CABLES}).
 */
public final class PiTransport {

    private PiTransport() {
    }

    public static void init() {
    }

    public static final Map<BufferType, Map<CableTier, BlockEntry<BufferBlock>>> BUFFERS = new EnumMap<>(BufferType.class);
    /** 1.0 : buffers creatifs ({@code creative_<famille>_buffer}). */
    public static final Map<BufferType, BlockEntry<BufferBlock>> CREATIVE_BUFFERS = new EnumMap<>(BufferType.class);
    static {
        for (BufferType type : BufferType.values()) {
            Map<CableTier, BlockEntry<BufferBlock>> tiers = new EnumMap<>(CableTier.class);
            for (CableTier tier : CableTier.values()) {
                tiers.put(tier, PiCoreRegistries.plainBlock(type.bufferId(tier), () -> {
                    BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 9.0F).sound(SoundType.METAL)
                          .requiresCorrectToolForDrops().noOcclusion();
                    //Chaleur et energie : etat visible (incandescence, voyants), avec leur lumiere
                    return switch (type) {
                        case HEAT -> new HeatBufferBlock(tier, properties.lightLevel(HeatBufferBlock::light));
                        case ENERGY -> new EnergyBufferBlock(tier, properties.lightLevel(EnergyBufferBlock::light));
                        case PRESSURE -> new PressureBufferBlock(tier, properties);
                        default -> new BufferBlock(type, tier, properties);
                    };
                }));
            }
            BUFFERS.put(type, tiers);
            //1.0 : buffer creatif de chaque famille (capacites du tier ultime, contenu inepuisable)
            CREATIVE_BUFFERS.put(type, PiCoreRegistries.plainBlock("creative_" + type.id() + "_buffer", () -> {
                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(-1.0F, 3_600_000.0F)
                      .sound(SoundType.METAL).noOcclusion().noLootTable();
                return (switch (type) {
                    case HEAT -> new HeatBufferBlock(CableTier.ULTIMATE, properties.lightLevel(HeatBufferBlock::light));
                    case ENERGY -> new EnergyBufferBlock(CableTier.ULTIMATE, properties.lightLevel(EnergyBufferBlock::light));
                    case PRESSURE -> new PressureBufferBlock(CableTier.ULTIMATE, properties);
                    default -> new BufferBlock(type, CableTier.ULTIMATE, properties);
                }).asCreative();
            }));
        }
    }

    /**
     * 1.11 (spec §1.2) : cables logistiques, 6 niveaux ({@code <niveau>_logistic_cable}) et le cable supraconducteur (energie seule, reseau a
     * part). Les anciens cables sont des blocs herites ({@link #LEGACY_CABLES}). MAJ simplification (03/11/2026, demande de Guillaume) : plus
     * de cables transparents ({@code _clear}) : leurs anciens identifiants deviennent le cable opaque du meme niveau ({@code PiRemaps}).
     */
    public static final Map<CableLevel, BlockEntry<LogisticCableBlock>> LOGISTIC_CABLES = new EnumMap<>(CableLevel.class);
    public static final BlockEntry<LogisticCableBlock> SUPERCONDUCTING_CABLE = PiCoreRegistries.plainBlock(CableLevel.SUPER.blockId(false),
          () -> new LogisticCableBlock(CableLevel.SUPER, false, logisticProperties(CableLevel.SUPER)));

    static {
        for (CableLevel level : CableLevel.all()) {
            if (level != CableLevel.SUPER) {
                LOGISTIC_CABLES.put(level, PiCoreRegistries.plainBlock(level.blockId(false), () -> new LogisticCableBlock(level, false,
                      logisticProperties(level))));
            }
        }
    }

    /** Proprietes par niveau (spec §1.2) : bois = hache, brule ; bronze = cuivre ; MK = metal ; supraconducteur = bleu glace. */
    private static BlockBehaviour.Properties logisticProperties(CableLevel level) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().noOcclusion().dynamicShape();
        return switch (level) {
            case WOODEN -> p.mapColor(MapColor.WOOD).strength(0.8F, 2.0F).sound(SoundType.WOOD).ignitedByLava();
            case BRONZE -> p.mapColor(MapColor.COLOR_ORANGE).strength(1.0F, 4.0F).sound(SoundType.COPPER);
            case SUPER -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5F, 6.0F).sound(SoundType.METAL);
            default -> p.mapColor(MapColor.METAL).strength(1.0F, 6.0F).sound(SoundType.METAL);
        };
    }

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<LogisticCableTile>> LOGISTIC_CABLE_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "logistic_cable", () -> BlockEntityType.Builder.of(LogisticCableTile::new, logisticBlocks()).build(null));

    /** Les 7 blocs du cable logistique (6 niveaux, supraconducteur). */
    public static Block[] logisticBlocks() {
        List<Block> list = new ArrayList<>();
        LOGISTIC_CABLES.values().forEach(e -> list.add(e.getBlock()));
        list.add(SUPERCONDUCTING_CABLE.getBlock());
        return list.toArray(Block[]::new);
    }

    /** Cable du niveau ; {@code clear} (ancien cable transparent) n'a plus d'effet : MAJ simplification, il devient l'opaque. */
    public static BlockEntry<LogisticCableBlock> logisticCable(CableLevel level, boolean clear) {
        return level == CableLevel.SUPER ? SUPERCONDUCTING_CABLE : LOGISTIC_CABLES.get(level);
    }

    /**
     * 1.11 (D2c, spec §8.1) : les 60 anciens cables, gardes une version comme blocs herites (sans objet, invisibles, convertis au chargement
     * par {@code LegacyMigration}) ; a retirer en 1.12. Cles : anciens identifiants.
     */
    public static final Map<String, RegistryObject<LegacyCableBlock>> LEGACY_CABLES = new LinkedHashMap<>();

    static {
        for (LegacyFamily.Old old : LegacyFamily.olds()) {
            LEGACY_CABLES.put(old.id(), PiCoreRegistries.BLOCKS.register(old.id(), () -> {
                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0F, 6.0F)
                      .sound(SoundType.METAL).noOcclusion().noLootTable();
                return old.family() == LegacyFamily.DATA ? new LegacyFiberBlock(old.level(), old.clear(), properties)
                                                         : new LegacyCableBlock(old.family(), old.level(), old.clear(), properties);
            }));
        }
    }

    /** Tiles heritees (ancien NBT lu tel quel) : {@code pioneers:cable} (52 cables) et {@code pioneers:data_cable} (8 fibres). */
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<LegacyCableTile>> LEGACY_CABLE_TILE = PiTileEntityTypes.VANILLA_TILES.register("cable",
          () -> BlockEntityType.Builder.<LegacyCableTile>of((pos, state) -> new LegacyCableTile(PiTransport.LEGACY_CABLE_TILE.get(), pos, state),
                legacyBlocks(false)).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<LegacyCableTile>> LEGACY_FIBER_TILE = PiTileEntityTypes.VANILLA_TILES.register("data_cable",
          () -> BlockEntityType.Builder.<LegacyCableTile>of((pos, state) -> new LegacyCableTile(PiTransport.LEGACY_FIBER_TILE.get(), pos, state),
                legacyBlocks(true)).build(null));

    private static Block[] legacyBlocks(boolean fibers) {
        List<Block> list = new ArrayList<>();
        for (RegistryObject<LegacyCableBlock> entry : LEGACY_CABLES.values()) {
            if ((entry.get() instanceof LegacyFiberBlock) == fibers) {
                list.add(entry.get());
            }
        }
        return list.toArray(Block[]::new);
    }

    /** Bloc herite d'un ancien identifiant (null : inconnu). */
    @Nullable
    public static LegacyCableBlock legacy(String id) {
        RegistryObject<LegacyCableBlock> entry = LEGACY_CABLES.get(id);
        return entry == null ? null : entry.get();
    }

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<BufferTile>> BUFFER_TILE = PiTileEntityTypes.VANILLA_TILES.register("buffer",
          () -> BlockEntityType.Builder.of(BufferTile::new, bufferBlocks()).build(null));
    /** Compresseur d'air : premiere source de pression. */
    public static final BlockEntry<AirCompressorBlock> AIR_COMPRESSOR = PiCoreRegistries.block("air_compressor", () -> new AirCompressorBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<AirCompressorTile>> AIR_COMPRESSOR_TILE = PiTileEntityTypes.VANILLA_TILES.register("air_compressor",
          () -> BlockEntityType.Builder.of(AirCompressorTile::new, AIR_COMPRESSOR.getBlock()).build(null));
    /** Cle a molette : reglage des faces des cables sans le configurateur de Mekanism. */
    public static final RegistryObject<Item> WRENCH = PiCoreRegistries.ITEMS.register("wrench",
          () -> new WrenchItem(new Item.Properties().stacksTo(1)));
    /** Radiateur electrique : premiere source de chaleur (FE → J). */
    public static final BlockEntry<ElectricHeaterBlock> ELECTRIC_HEATER = PiCoreRegistries.block("electric_heater", () -> new ElectricHeaterBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                .lightLevel(state -> state.getValue(ElectricHeaterBlock.ACTIVE) ? 9 : 0)));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<ElectricHeaterTile>> ELECTRIC_HEATER_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "electric_heater", () -> BlockEntityType.Builder.of(ElectricHeaterTile::new, ELECTRIC_HEATER.getBlock()).build(null));
    /** Interface standard des blocs du socle (buffers, radiateur, compresseur...). */
    public static final RegistryObject<MenuType<MachineMenu>> MACHINE_MENU = PiCoreRegistries.MENUS.register("machine",
          () -> IForgeMenuType.create(MachineMenu::client));

    private static Block[] bufferBlocks() {
        List<Block> list = new ArrayList<>(List.of(blocks(BUFFERS)));
        CREATIVE_BUFFERS.values().forEach(entry -> list.add(entry.getBlock()));
        return list.toArray(Block[]::new);
    }

    private static <K, B extends Block> Block[] blocks(Map<K, Map<CableTier, BlockEntry<B>>> map) {
        List<Block> list = new ArrayList<>();
        for (Map<CableTier, BlockEntry<B>> tiers : map.values()) {
            for (BlockEntry<B> entry : tiers.values()) {
                list.add(entry.getBlock());
            }
        }
        return list.toArray(Block[]::new);
    }

    public static BlockEntry<BufferBlock> buffer(BufferType type, CableTier tier) {
        return BUFFERS.get(type).get(tier);
    }
}
