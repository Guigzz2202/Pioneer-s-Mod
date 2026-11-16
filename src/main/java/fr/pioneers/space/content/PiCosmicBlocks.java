package fr.pioneers.space.content;

import fr.pioneers.space.content.absorber.AbsorberControllerBlock;
import fr.pioneers.space.content.absorber.AbsorberControllerTile;
import fr.pioneers.space.content.absorber.AbsorberPart;
import fr.pioneers.space.content.absorber.AbsorberPartBlock;
import fr.pioneers.space.content.absorber.AbsorberPartTile;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.BlockRegistryObject;
import fr.pioneers.core.registry.PiCoreRegistries;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import fr.pioneers.core.registries.PiBlocks;
import fr.pioneers.core.registries.PiTileEntityTypes;

/**
 * Multiblocs cosmiques de la 0.10 : le Simulateur d'univers (15x15x15) et l'absorbeur d'energie solaire (en orbite d'une etoile),
 * et les blocs des produits de la simulation.
 */
public final class PiCosmicBlocks {

    private PiCosmicBlocks() {
    }

    public static void init() {
        //Charge la classe (et donc ses enregistrements differes)
    }

    // Simulateur d'univers : remplace par le Simulateur spatial (PiSpaceSim)

    // ------------------------------------------------------------------
    // Absorbeur d'energie solaire (socle : AbsorberStructure, controleur dans une paroi laterale)
    // ------------------------------------------------------------------

    private static BlockBehaviour.Properties absorber(AbsorberPart part) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(5.0F, 12.0F)
              .requiresCorrectToolForDrops().sound(part == AbsorberPart.COLLECTOR ? SoundType.GLASS : SoundType.METAL);
        if (part == AbsorberPart.COLLECTOR) {
            //Capteur : laisse voir le ciel, s'illumine quand l'absorbeur travaille
            properties = properties.noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)
                  .lightLevel(state -> state.getValue(AbsorberPartBlock.ACTIVE) ? 12 : 0);
        }
        return properties;
    }

    public static final Map<AbsorberPart, BlockEntry<AbsorberPartBlock>> ABSORBER_PARTS = new EnumMap<>(AbsorberPart.class);

    static {
        for (AbsorberPart part : AbsorberPart.values()) {
            ABSORBER_PARTS.put(part, PiCoreRegistries.block(part.id, () -> new AbsorberPartBlock(part, absorber(part))));
        }
    }

    public static final BlockEntry<AbsorberPartBlock> ABSORBER_CASING = ABSORBER_PARTS.get(AbsorberPart.CASING);
    public static final BlockEntry<AbsorberPartBlock> ABSORBER_PORT = ABSORBER_PARTS.get(AbsorberPart.PORT);
    public static final BlockEntry<AbsorberPartBlock> ABSORBER_COLLECTOR = ABSORBER_PARTS.get(AbsorberPart.COLLECTOR);
    public static final BlockEntry<AbsorberControllerBlock> ABSORBER_CONTROLLER = PiCoreRegistries.block("solar_absorber_controller",
          () -> new AbsorberControllerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(5.0F, 12.0F)
                .requiresCorrectToolForDrops().sound(SoundType.METAL).lightLevel(state -> state.getValue(MachineBlock.ACTIVE) ? 10 : 2)));

    public static final RegistryObject<BlockEntityType<AbsorberPartTile>> ABSORBER_PART_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "solar_absorber_part", () -> BlockEntityType.Builder.of(AbsorberPartTile::new, ABSORBER_PARTS.values().stream().map(BlockEntry::getBlock)
                .toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<AbsorberControllerTile>> ABSORBER_CONTROLLER_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "solar_absorber_controller", () -> BlockEntityType.Builder.of(AbsorberControllerTile::new, ABSORBER_CONTROLLER.getBlock()).build(null));

    // ------------------------------------------------------------------
    // Blocs des produits du simulateur
    // ------------------------------------------------------------------

    public static final BlockRegistryObject<Block, BlockItem> STARDUST_BLOCK = PiBlocks.BLOCKS.register("stardust_block",
          () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5F).sound(SoundType.AMETHYST)
                .lightLevel(state -> 10)));
    public static final BlockRegistryObject<Block, BlockItem> NEUTRONIUM_BLOCK = PiBlocks.BLOCKS.register("neutronium_block",
          () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops().strength(50.0F, 3600.0F)
                .sound(SoundType.NETHERITE_BLOCK)), block -> new BlockItem(block, new Item.Properties().rarity(Rarity.RARE)));
    public static final BlockRegistryObject<Block, BlockItem> DARK_MATTER_BLOCK = PiBlocks.BLOCKS.register("dark_matter_block",
          () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(20.0F, 1200.0F)
                .sound(SoundType.SCULK)), block -> new BlockItem(block, new Item.Properties().rarity(Rarity.RARE)));
    public static final BlockRegistryObject<Block, BlockItem> QUASAR_LAMP = PiBlocks.BLOCKS.register("quasar_lamp",
          () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0F).sound(SoundType.GLASS)
                .lightLevel(state -> 15)), block -> new BlockItem(block, new Item.Properties().rarity(Rarity.EPIC)));
}
