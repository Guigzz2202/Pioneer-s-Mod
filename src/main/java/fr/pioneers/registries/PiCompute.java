package fr.pioneers.registries;

import fr.pioneers.compute.AiLabTile;
import fr.pioneers.compute.ComputeCore;
import fr.pioneers.compute.FactoryAiCoreTile;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * Calcul et IA (0.17.2) : laboratoire d'IA (entrainement des modeles), noyau d'usine (IA qui supervise, regule et parle). La carte
 * d'amelioration robotique {@code ai_upgrade} est une {@code RoboticUpgrade} (registre des ameliorations, {@link PiRobotics}). Les multiblocs
 * (centre de donnees, ordinateur quantique) sont des controleurs industriels ({@code ComputeCore}).
 * Visuels, recettes et langue : {@code tools/gc/compute/compute_data.py}.
 */
public final class PiCompute {

    private PiCompute() {
    }

    public static void init() {
    }

    public static final BlockEntry<MachineBlock> AI_LAB = PiCoreRegistries.block("ai_lab", () -> new MachineBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.COLOR_GRAY).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
          .lightLevel(state -> state.getValue(MachineBlock.ACTIVE) ? 9 : 2), () -> PiCompute.AI_LAB_TILE.get(), AiLabTile::new));
    public static final BlockEntry<MachineBlock> FACTORY_AI_CORE = PiCoreRegistries.block("factory_ai_core", () -> new MachineBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(5.0F, 12.0F).sound(SoundType.GLASS).requiresCorrectToolForDrops()
                .noOcclusion().lightLevel(state -> state.getValue(MachineBlock.ACTIVE) ? 13 : 3), () -> PiCompute.FACTORY_AI_CORE_TILE.get(),
          FactoryAiCoreTile::new));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<AiLabTile>> AI_LAB_TILE = PiTileEntityTypes.VANILLA_TILES.register("ai_lab",
          () -> BlockEntityType.Builder.of(AiLabTile::new, AI_LAB.getBlock()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<FactoryAiCoreTile>> FACTORY_AI_CORE_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "factory_ai_core", () -> BlockEntityType.Builder.of(FactoryAiCoreTile::new, FACTORY_AI_CORE.getBlock()).build(null));

    /** Ordre dans l'onglet creatif (age industriel, apres l'informatique). */
    public static final List<Supplier<? extends ItemLike>> TAB = List.of(() -> AI_LAB, () -> FACTORY_AI_CORE);
}
