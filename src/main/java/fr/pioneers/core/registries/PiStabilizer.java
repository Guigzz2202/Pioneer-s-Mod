package fr.pioneers.core.registries;

import fr.pioneers.space.content.stabilizer.Regime;
import fr.pioneers.space.content.stabilizer.StabilizerControllerBlock;
import fr.pioneers.space.content.stabilizer.StabilizerControllerTile;
import fr.pioneers.space.content.stabilizer.StabilizerCoreBlock;
import fr.pioneers.space.content.stabilizer.StabilizerCoreTile;
import fr.pioneers.space.content.stabilizer.StabilizerEmitterBlock;
import fr.pioneers.space.content.stabilizer.StabilizerPart;
import fr.pioneers.space.content.stabilizer.StabilizerPartBlock;
import fr.pioneers.space.content.stabilizer.StabilizerPartTile;
import fr.pioneers.core.block.DescribedBlockItem;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

/**
 * Stabilisateur spatial (socle, lot E) : un seul multibloc pour le trou noir et l'etoile, le regime suit le noyau pose au centre. Remplace la
 * Chambre de stabilisation et la Chambre stellaire (anciens blocs remappes par {@code PiRemaps}).
 */
public final class PiStabilizer {

    private PiStabilizer() {
    }

    public static void init() {
    }

    public static final Map<StabilizerPart, BlockEntry<? extends Block>> PARTS = new EnumMap<>(StabilizerPart.class);

    private static BlockBehaviour.Properties wall(boolean glass) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0F, 12.0F)
              .requiresCorrectToolForDrops().sound(glass ? SoundType.GLASS : SoundType.METAL);
        return glass ? properties.noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false) : properties;
    }

    static {
        for (StabilizerPart part : StabilizerPart.values()) {
            String name = "space_stabilizer_" + part.name().toLowerCase(Locale.ROOT);
            PARTS.put(part, part == StabilizerPart.EMITTER
                            ? PiCoreRegistries.block(name, () -> new StabilizerEmitterBlock(wall(false).noOcclusion().lightLevel(state -> 10)))
                            : part == StabilizerPart.CONTROLLER ? PiCoreRegistries.block(name, () -> new StabilizerControllerBlock(wall(false)))
                            : PiCoreRegistries.block(name, () -> new StabilizerPartBlock(part, wall(part == StabilizerPart.GLASS))));
        }
    }

    public static final BlockEntry<? extends Block> CASING = PARTS.get(StabilizerPart.CASING);
    public static final BlockEntry<? extends Block> GLASS = PARTS.get(StabilizerPart.GLASS);
    public static final BlockEntry<? extends Block> PORT = PARTS.get(StabilizerPart.PORT);
    public static final BlockEntry<? extends Block> POWER_PORT = PARTS.get(StabilizerPart.POWER_PORT);
    public static final BlockEntry<? extends Block> CONTROLLER = PARTS.get(StabilizerPart.CONTROLLER);
    public static final BlockEntry<? extends Block> EMITTER = PARTS.get(StabilizerPart.EMITTER);

    /** Noyaux : objets rares et brillants (comme l'ancien Stellar Core). */
    private static BlockEntry<StabilizerCoreBlock> core(String name, Regime regime, MapColor color, int light) {
        return PiCoreRegistries.block(name, () -> new StabilizerCoreBlock(regime, BlockBehaviour.Properties.of().mapColor(color).strength(5.0F, 1200.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.AMETHYST).noOcclusion().lightLevel(state -> light)),
              block -> new DescribedBlockItem(block, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant(), "description.pioneers." + name) {
                  @Override
                  public boolean isFoil(@NotNull ItemStack stack) {
                      return true;
                  }
              });
    }

    public static final BlockEntry<StabilizerCoreBlock> SINGULARITY_CORE = core("singularity_core", Regime.SINGULARITY, MapColor.COLOR_PURPLE, 4);
    public static final BlockEntry<StabilizerCoreBlock> STELLAR_CORE = core("stellar_core", Regime.STAR, MapColor.GOLD, 12);

    public static final RegistryObject<BlockEntityType<StabilizerPartTile>> PART_TILE = PiTileEntityTypes.VANILLA_TILES.register("space_stabilizer_part",
          () -> BlockEntityType.Builder.of(StabilizerPartTile::new, PARTS.entrySet().stream().filter(e -> e.getKey() != StabilizerPart.CONTROLLER)
                .map(e -> e.getValue().getBlock()).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<StabilizerControllerTile>> CONTROLLER_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "space_stabilizer_controller", () -> BlockEntityType.Builder.of(StabilizerControllerTile::new, CONTROLLER.getBlock()).build(null));
    public static final RegistryObject<BlockEntityType<StabilizerCoreTile>> CORE_TILE = PiTileEntityTypes.VANILLA_TILES.register("space_stabilizer_core",
          () -> BlockEntityType.Builder.of(StabilizerCoreTile::new, SINGULARITY_CORE.getBlock(), STELLAR_CORE.getBlock()).build(null));
}
