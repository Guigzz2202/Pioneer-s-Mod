package fr.pioneers.energy;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.registries.PiTileEntityTypes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.45 « Energized » : generateurs renouvelables ({@link EnergyKind}), quatre tiers chacun. Onglet Industrie (apres les machines de
 * chaleur). Modeles, textures, langue, recettes : {@code tools/gc/energy/assets.py}.
 */
public final class PiEnergy {

    private PiEnergy() {
    }

    public static void init() {
    }

    /** Blocs dans l'ordre de l'onglet. */
    public static final List<Supplier<? extends ItemLike>> TAB = new ArrayList<>();
    public static final Map<EnergyKind, List<BlockEntry<GeneratorBlock>>> BLOCKS = new EnumMap<>(EnergyKind.class);

    public static final List<BlockEntry<GeneratorBlock>> SOLAR = tiers(EnergyKind.SOLAR, MapColor.COLOR_BLUE, () -> PiEnergy.SOLAR_TILE.get(),
          SolarPanelTile::new, box(0, 0, 0, 16, 4, 16));
    //1.9.2 (retours de Guillaume) : la nacelle est un bloc plein (le joueur construit son mat) ; le rotor est un bloc a part (ROTORS)
    public static final List<BlockEntry<GeneratorBlock>> WIND = tiers(EnergyKind.WIND, MapColor.SNOW, () -> PiEnergy.WIND_TILE.get(),
          WindTurbineTile::new);
    /** 1.9.2 : rotors d'eolienne (un par tier), poses sur la face avant de la nacelle ; juste apres les nacelles dans l'onglet. */
    public static final List<BlockEntry<WindRotorBlock>> ROTORS = rotors();

    public static final List<BlockEntry<GeneratorBlock>> HYDRO = tiers(EnergyKind.HYDRO, MapColor.STONE, () -> PiEnergy.HYDRO_TILE.get(),
          HydroTurbineTile::new);
    //MAJ simplification (03/11/2026) : centrale geothermique cubique (modele au ras des six faces) : forme pleine
    public static final List<BlockEntry<GeneratorBlock>> GEOTHERMAL = tiers(EnergyKind.GEOTHERMAL, MapColor.COLOR_ORANGE,
          () -> PiEnergy.GEOTHERMAL_TILE.get(), GeothermalPlantTile::new, box(0, 0, 0, 16, 16, 16));
    public static final List<BlockEntry<GeneratorBlock>> MICROBIAL = tiers(EnergyKind.MICROBIAL, MapColor.COLOR_GREEN,
          () -> PiEnergy.MICROBIAL_TILE.get(), MicrobialCellTile::new, box(1, 0, 1, 15, 15, 15));

    public static final RegistryObject<BlockEntityType<SolarPanelTile>> SOLAR_TILE = tile("solar_panel", SolarPanelTile::new, SOLAR);
    public static final RegistryObject<BlockEntityType<WindTurbineTile>> WIND_TILE = tile("wind_turbine", WindTurbineTile::new, WIND);
    public static final RegistryObject<BlockEntityType<HydroTurbineTile>> HYDRO_TILE = tile("hydro_turbine", HydroTurbineTile::new, HYDRO);
    public static final RegistryObject<BlockEntityType<GeothermalPlantTile>> GEOTHERMAL_TILE = tile("geothermal_plant", GeothermalPlantTile::new,
          GEOTHERMAL);
    public static final RegistryObject<BlockEntityType<MicrobialCellTile>> MICROBIAL_TILE = tile("microbial_fuel_cell", MicrobialCellTile::new,
          MICROBIAL);

    //1.0.3-beta (lot 5, decision 4 de Guillaume) : turbine a vapeur compacte et echappement compact RETIRES (PiRemaps.p103Block : generateur
    //a combustion solide MKI et buffer de gaz MKI)

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<WindRotorTile>> ROTOR_TILE = PiTileEntityTypes.VANILLA_TILES.register("wind_rotor",
          () -> BlockEntityType.Builder.of(WindRotorTile::new, ROTORS.stream().map(BlockEntry::getBlock).toArray(Block[]::new)).build(null));

    private static List<BlockEntry<WindRotorBlock>> rotors() {
        List<BlockEntry<WindRotorBlock>> out = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            int tier = t;
            String id = EnergyKind.TIERS[t] + "_wind_rotor";
            BlockEntry<WindRotorBlock> entry = PiCoreRegistries.block(id, () -> new WindRotorBlock(tier, BlockBehaviour.Properties.of()
                  .mapColor(MapColor.SNOW).strength(2.0F + tier, 6.0F + 2 * tier).sound(SoundType.METAL).requiresCorrectToolForDrops()
                  .noOcclusion()), b -> new WindRotorItem(b, new Item.Properties(), "description.pioneers." + id));
            out.add(entry);
            TAB.add(entry::getBlock);
        }
        return List.copyOf(out);
    }

    /** Boite (pixels) d'un modele dessine facade au nord. */
    private static double[] box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new double[]{x0, y0, z0, x1, y1, z1};
    }

    /** Formes par facade (nord, sud, ouest, est : ordre de {@code MachineBlock.shaped}) ; null : bloc plein. */
    private static VoxelShape[] shapes(double[]... boxes) {
        if (boxes.length == 0) {
            return null;
        }
        VoxelShape[] out = new VoxelShape[4];
        for (int f = 0; f < 4; f++) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
                double[] r = switch (f) {
                    case 1 -> new double[]{16 - x1, 16 - z1, 16 - x0, 16 - z0};     //sud (y 180)
                    case 2 -> new double[]{z0, 16 - x1, z1, 16 - x0};               //ouest (y 270)
                    case 3 -> new double[]{16 - z1, x0, 16 - z0, x1};               //est (y 90)
                    default -> new double[]{x0, z0, x1, z1};
                };
                shape = Shapes.or(shape, Block.box(r[0], b[1], r[1], r[2], b[4], r[3]));
            }
            out[f] = shape;
        }
        return out;
    }

    private static List<BlockEntry<GeneratorBlock>> tiers(EnergyKind kind, MapColor color, Supplier<? extends BlockEntityType<?>> type,
          BiFunction<BlockPos, BlockState, BlockEntity> factory, double[]... boxes) {
        VoxelShape[] shapes = shapes(boxes);
        List<BlockEntry<GeneratorBlock>> out = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            int tier = t;
            BlockEntry<GeneratorBlock> entry = PiCoreRegistries.block(kind.id(t), () -> {
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of().mapColor(color).strength(3.0F + tier, 8.0F + 3 * tier)
                      .sound(SoundType.METAL).requiresCorrectToolForDrops();
                //1.10.1 (Guillaume : « bug de transparence sur le block d'eolienne ») : la nacelle, de forme pleine, n'occulte plus ses
                //voisins : son modele ne remplit pas tout le cube (coque en retrait de 0,6 px entre les montants, socle, toit), et les
                //faces cachees des blocs voisins (mat, rotor) laissaient voir le vide a travers ces retraits
                props = props.noOcclusion();
                GeneratorBlock block = new GeneratorBlock(kind, tier, props
                      .lightLevel(state -> kind == EnergyKind.GEOTHERMAL && state.getValue(GeneratorBlock.ACTIVE) ? 6 : 0), type, factory);
                if (shapes != null) {
                    block.shaped(shapes);
                }

                return block;
            });
            out.add(entry);
            TAB.add(entry::getBlock);
        }
        BLOCKS.put(kind, out);
        return List.copyOf(out);
    }

    @SuppressWarnings("ConstantConditions")
    private static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> tile(String name, BlockEntityType.BlockEntitySupplier<T> factory,
          List<BlockEntry<GeneratorBlock>> blocks) {
        return PiTileEntityTypes.VANILLA_TILES.register(name, () -> BlockEntityType.Builder.of(factory, blocks.stream().map(BlockEntry::getBlock)
              .toArray(Block[]::new)).build(null));
    }
}
