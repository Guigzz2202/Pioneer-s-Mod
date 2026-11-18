package fr.pioneers.farm.greenhouse;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.core.registries.PiTileEntityTypes;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.0.5-beta (lot 3) : registres de la SERRE MULTIBLOC : par matiere ({@link GreenhouseMaterial} : bois traite, fer) une vitre
 * ({@code <m>_greenhouse_glass}), un cadre ({@code <m>_greenhouse_frame}) et une trappe vitree ({@code <m>_greenhouse_vent}) ; un
 * regulateur ({@code greenhouse_regulator}) et sa tile. Ressources : {@code T:greenhouse/greenhouse_assets.py}.
 */
public final class PiGreenhouse {

    public static final Map<GreenhouseMaterial, BlockEntry<GreenhouseGlassBlock>> GLASS = new EnumMap<>(GreenhouseMaterial.class);
    public static final Map<GreenhouseMaterial, BlockEntry<GreenhouseFrameBlock>> FRAME = new EnumMap<>(GreenhouseMaterial.class);
    public static final Map<GreenhouseMaterial, BlockEntry<GreenhouseVentBlock>> VENT = new EnumMap<>(GreenhouseMaterial.class);

    static {
        for (GreenhouseMaterial m : GreenhouseMaterial.values()) {
            boolean iron = m == GreenhouseMaterial.IRON;
            GLASS.put(m, PiCoreRegistries.block(m.id() + "_greenhouse_glass", () -> {
                BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(m.hardness, m.resistance)
                      .sound(SoundType.GLASS).noOcclusion().isValidSpawn((s, l, pos, e) -> false).isRedstoneConductor(PiGreenhouse::never)
                      .isSuffocating(PiGreenhouse::never).isViewBlocking(PiGreenhouse::never);
                return iron ? new IronGreenhouseGlassBlock(p) : new TreatedGreenhouseGlassBlock(p);
            }));
            FRAME.put(m, PiCoreRegistries.block(m.id() + "_greenhouse_frame", () -> new GreenhouseFrameBlock(BlockBehaviour.Properties.of()
                  .mapColor(iron ? MapColor.METAL : MapColor.COLOR_BROWN).strength(m.hardness * 1.5F, m.resistance)
                  .sound(iron ? SoundType.METAL : SoundType.WOOD), m)));
            VENT.put(m, PiCoreRegistries.block(m.id() + "_greenhouse_vent", () -> new GreenhouseVentBlock(BlockBehaviour.Properties.of()
                  .mapColor(MapColor.NONE).strength(m.hardness, m.resistance).sound(SoundType.GLASS).noOcclusion()
                  .isValidSpawn((s, l, pos, e) -> false), m)));
        }
    }

    public static final BlockEntry<GreenhouseRegulatorBlock> REGULATOR = PiCoreRegistries.block("greenhouse_regulator",
          () -> new GreenhouseRegulatorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.5F, 6F).sound(SoundType.WOOD)));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<GreenhouseRegulatorTile>> REGULATOR_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "greenhouse_regulator", () -> BlockEntityType.Builder.of(GreenhouseRegulatorTile::new, REGULATOR.getBlock()).build(null));

    private PiGreenhouse() {
    }

    private static boolean never(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
        return false;
    }

    /** Vitre de serre d'une matiere. */
    public static Block glass(GreenhouseMaterial m) {
        return GLASS.get(m).getBlock();
    }

    /** Tous les blocs de paroi de serre du mod (vitres, cadres, trappes, regulateur). */
    public static Set<Block> wallBlocks() {
        Set<Block> out = new HashSet<>();
        for (GreenhouseMaterial m : GreenhouseMaterial.values()) {
            out.add(GLASS.get(m).getBlock());
            out.add(FRAME.get(m).getBlock());
            out.add(VENT.get(m).getBlock());
        }
        out.add(REGULATOR.getBlock());
        return out;
    }

    /** Charge les registres (appele par {@code Pioneers}). */
    public static void init() {
    }
}
