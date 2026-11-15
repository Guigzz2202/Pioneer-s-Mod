package fr.pioneers.registries;

import fr.pioneers.core.registry.BlockRegistryObject;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Blocs des geantes gazeuses : aerostats (pont, aire d'atterrissage, coque, ballasts) et baie de drones.
 */
public final class PiGiantBlocks {

    private PiGiantBlocks() {
    }

    public static void init() {
        //Charge la classe (et donc ses enregistrements differes)
    }

    private static BlockBehaviour.Properties metal(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).requiresCorrectToolForDrops().strength(4.0F, 9.0F).sound(SoundType.METAL);
    }

    /** Pont de l'aerostat (caillebotis). */
    public static final BlockRegistryObject<Block, BlockItem> AEROSTAT_DECK = PiBlocks.BLOCKS.register("aerostat_deck",
          () -> new Block(metal(MapColor.METAL).noOcclusion()));
    /** Aire d'atterrissage (marquage lumineux). */
    public static final BlockRegistryObject<Block, BlockItem> AEROSTAT_LANDING_PAD = PiBlocks.BLOCKS.register("aerostat_landing_pad",
          () -> new Block(metal(MapColor.COLOR_ORANGE).lightLevel(state -> 6)));
    /** Coque (quille sous le pont). */
    public static final BlockRegistryObject<Block, BlockItem> AEROSTAT_HULL = PiBlocks.BLOCKS.register("aerostat_hull",
          () -> new Block(metal(MapColor.COLOR_GRAY)));
    /** Ballast d'helium : cellule lumineuse qui porte l'aerostat. */
    public static final BlockRegistryObject<Block, BlockItem> AEROSTAT_BUOYANCY_CELL = PiBlocks.BLOCKS.register("aerostat_buoyancy_cell",
          () -> new Block(metal(MapColor.COLOR_LIGHT_BLUE).lightLevel(state -> 12).sound(SoundType.GLASS)));

}
