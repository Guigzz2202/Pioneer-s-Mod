package fr.pioneers.ruins;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.registries.PiTileEntityTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.9 (MAJ RP, bloc 1) : les blocs delabres, par tier 1..3 : casing et port (Structure), sept blocs de metier (un par categorie), Controleur,
 * Console de perimetre, Armoire d'archives, gravats technologiques. Durete : 60 % du casing industriel ; outil : celui du casing du tier.
 */
public final class RuinBlocks {

    public static final int TIERS = 3;
    /** Tous les blocs delabres, par id. */
    public static final Map<String, BlockEntry<? extends DerelictBlock>> ALL = new LinkedHashMap<>();
    public static final List<BlockEntry<DerelictControllerBlock>> CONTROLLERS = new ArrayList<>();
    public static final List<BlockEntry<PerimeterConsoleBlock>> CONSOLES = new ArrayList<>();
    public static final List<BlockEntry<ArchiveCabinetBlock>> CABINETS = new ArrayList<>();

    private RuinBlocks() {
    }

    static BlockBehaviour.Properties props(int tier, boolean noOcclusion) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(tier == 1 ? MapColor.TERRACOTTA_BROWN : tier == 2 ? MapColor.METAL
              : MapColor.COLOR_GRAY).strength(2.1F, 5.4F).sound(SoundType.METAL).requiresCorrectToolForDrops();
        return noOcclusion ? p.noOcclusion() : p;
    }

    public static String id(String role, int tier) {
        return switch (role) {
            case "controller" -> "derelict_controller_mk" + tier;
            case "console" -> "perimeter_console_mk" + tier;
            case "cabinet" -> "archive_cabinet_mk" + tier;
            case "rubble" -> "tech_rubble_mk" + tier;
            default -> "derelict_" + role + "_mk" + tier;
        };
    }

    static {
        for (int t = 1; t <= TIERS; t++) {
            int tier = t;
            for (String role : List.of("casing", "port")) {
                ALL.put(id(role, t), PiCoreRegistries.block(id(role, t), () -> new DerelictBlock(role, tier, props(tier, false))));
            }
            for (ScrapCategory c : ScrapCategory.values()) {
                if (c.hasTradeBlock()) {
                    String role = c.id();
                    ALL.put(id(role, t), PiCoreRegistries.block(id(role, t), () -> new DerelictBlock(role, tier, props(tier, false))));
                }
            }
            BlockEntry<DerelictControllerBlock> ctl = PiCoreRegistries.block(id("controller", t), () -> new DerelictControllerBlock(tier, props(tier, false)));
            BlockEntry<PerimeterConsoleBlock> con = PiCoreRegistries.block(id("console", t), () -> new PerimeterConsoleBlock(tier, props(tier, true)));
            BlockEntry<ArchiveCabinetBlock> cab = PiCoreRegistries.block(id("cabinet", t), () -> new ArchiveCabinetBlock(tier, props(tier, false)));
            CONTROLLERS.add(ctl);
            CONSOLES.add(con);
            CABINETS.add(cab);
            ALL.put(id("controller", t), ctl);
            ALL.put(id("console", t), con);
            ALL.put(id("cabinet", t), cab);
            ALL.put(id("rubble", t), PiCoreRegistries.block(id("rubble", t), () -> new TechRubbleBlock(tier, props(tier, true))));
        }
    }

    /** Plaque des contributeurs, posee a la remise en service ({@link Restoration}). */
    public static final BlockEntry<RestorationPlaqueBlock> PLAQUE = PiCoreRegistries.block("restoration_plaque", () -> new RestorationPlaqueBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.5F, 6.0F).sound(SoundType.COPPER).noOcclusion()));

    public static final RegistryObject<BlockEntityType<DerelictControllerTile>> CONTROLLER_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "derelict_controller", () -> BlockEntityType.Builder.of(DerelictControllerTile::new, blocks(CONTROLLERS)).build(null));
    public static final RegistryObject<BlockEntityType<SiteTile>> CONSOLE_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "perimeter_console", () -> BlockEntityType.Builder.<SiteTile>of((p, s) -> new SiteTile(RuinBlocks.CONSOLE_TILE.get(), p, s),
                blocks(CONSOLES)).build(null));
    public static final RegistryObject<BlockEntityType<ArchiveCabinetTile>> CABINET_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "archive_cabinet", () -> BlockEntityType.Builder.of(ArchiveCabinetTile::new, blocks(CABINETS)).build(null));

    public static final RegistryObject<BlockEntityType<RestorationPlaqueBlock.Tile>> PLAQUE_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "restoration_plaque", () -> BlockEntityType.Builder.of(RestorationPlaqueBlock.Tile::new, PLAQUE.getBlock()).build(null));

    private static Block[] blocks(List<? extends BlockEntry<? extends Block>> list) {
        return list.stream().map(BlockEntry::getBlock).toArray(Block[]::new);
    }

    /** Bloc delabre de ce role et de ce tier. */
    public static DerelictBlock get(String role, int tier) {
        return ALL.get(id(role, Math.max(1, Math.min(TIERS, tier)))).getBlock();
    }

    static void init() {
    }
}
