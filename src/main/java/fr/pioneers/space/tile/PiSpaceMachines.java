package fr.pioneers.space.tile;

import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.space.tile.TileEntityChunkLoader;
import fr.pioneers.space.tile.TileEntityForceField;
import fr.pioneers.space.tile.TileEntityLightningCollector;
import fr.pioneers.space.tile.TileEntityOxygenDiffuser;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import fr.pioneers.core.registries.PiTileEntityTypes;

/**
 * Machines spatiales passees au socle (lot A) : chunkloader, champ de force, collecteur de foudre, diffuseur d'oxygene, collecteur de gaz,
 * compresseur gravitationnel ({@link MachineTile}).
 */
public final class PiSpaceMachines {

    private PiSpaceMachines() {
    }

    public static void init() {
    }

    /** 1.0 (lot 4) : chaque machine en 4 tiers ({@code basic_ advanced_ elite_ ultimate_<id>}) ; l'ancien id devient le MKI (PiRemaps). */
    public static final List<BlockEntry<MachineBlock>> CHUNK_LOADERS = tiers("chunk_loader", MapColor.COLOR_PURPLE,
          () -> PiSpaceMachines.CHUNK_LOADER_TILE.get(), TileEntityChunkLoader::new);
    public static final List<BlockEntry<MachineBlock>> FORCE_FIELD_GENERATORS = tiers("force_field_generator", MapColor.COLOR_CYAN,
          () -> PiSpaceMachines.FORCE_FIELD_TILE.get(), TileEntityForceField::new);
    public static final List<BlockEntry<MachineBlock>> LIGHTNING_COLLECTORS = tiers("lightning_collector", MapColor.COLOR_ORANGE,
          () -> PiSpaceMachines.LIGHTNING_COLLECTOR_TILE.get(), TileEntityLightningCollector::new);
    public static final List<BlockEntry<MachineBlock>> OXYGEN_DIFFUSERS = tiers("oxygen_diffuser", MapColor.COLOR_LIGHT_BLUE,
          () -> PiSpaceMachines.OXYGEN_DIFFUSER_TILE.get(), TileEntityOxygenDiffuser::new);

    /** Les MKI (references historiques : JEI, stations orbitales, tickets de chunks). */
    public static final BlockEntry<MachineBlock> CHUNK_LOADER = CHUNK_LOADERS.get(0);
    public static final BlockEntry<MachineBlock> FORCE_FIELD_GENERATOR = FORCE_FIELD_GENERATORS.get(0);
    public static final BlockEntry<MachineBlock> LIGHTNING_COLLECTOR = LIGHTNING_COLLECTORS.get(0);
    public static final BlockEntry<MachineBlock> OXYGEN_DIFFUSER = OXYGEN_DIFFUSERS.get(0);

    public static final RegistryObject<BlockEntityType<TileEntityChunkLoader>> CHUNK_LOADER_TILE = tile("chunk_loader", TileEntityChunkLoader::new,
          CHUNK_LOADERS);
    public static final RegistryObject<BlockEntityType<TileEntityForceField>> FORCE_FIELD_TILE = tile("force_field_generator", TileEntityForceField::new,
          FORCE_FIELD_GENERATORS);
    public static final RegistryObject<BlockEntityType<TileEntityLightningCollector>> LIGHTNING_COLLECTOR_TILE = tile("lightning_collector",
          TileEntityLightningCollector::new, LIGHTNING_COLLECTORS);
    public static final RegistryObject<BlockEntityType<TileEntityOxygenDiffuser>> OXYGEN_DIFFUSER_TILE = tile("oxygen_diffuser",
          TileEntityOxygenDiffuser::new, OXYGEN_DIFFUSERS);

    /** Toutes les machines, tier par tier (onglet creatif). */
    public static List<BlockEntry<MachineBlock>> all() {
        List<BlockEntry<MachineBlock>> out = new ArrayList<>();
        //0.20.34 : le compresseur gravitationnel est devenu un multibloc industriel
        for (List<BlockEntry<MachineBlock>> list : List.of(CHUNK_LOADERS, FORCE_FIELD_GENERATORS, LIGHTNING_COLLECTORS, OXYGEN_DIFFUSERS)) {
            out.addAll(list);
        }
        return out;
    }

    private static List<BlockEntry<MachineBlock>> tiers(String name, MapColor color, Supplier<? extends BlockEntityType<?>> type,
          BiFunction<BlockPos, BlockState, BlockEntity> factory) {
        List<BlockEntry<MachineBlock>> out = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            int tier = t;
            out.add(PiCoreRegistries.block(TieredMachineBlock.TIERS[t] + "_" + name, () -> new TieredMachineBlock(tier, BlockBehaviour.Properties.of()
                  .mapColor(color).strength(3.5F + tier, 9.0F + 3 * tier).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                  .lightLevel(state -> state.getValue(MachineBlock.ACTIVE) ? 8 : 0), type, factory)));
        }
        return List.copyOf(out);
    }

    @SuppressWarnings("ConstantConditions")
    private static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> tile(String name, BlockEntityType.BlockEntitySupplier<T> factory,
          List<BlockEntry<MachineBlock>> blocks) {
        return PiTileEntityTypes.VANILLA_TILES.register(name, () -> BlockEntityType.Builder.of(factory, blocks.stream().map(BlockEntry::getBlock)
              .toArray(Block[]::new)).build(null));
    }
}
