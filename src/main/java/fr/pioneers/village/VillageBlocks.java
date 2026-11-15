package fr.pioneers.village;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.registries.PiTileEntityTypes;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/** 1.9 (MAJ RP, bloc 3) : blocs de la place du village (Tableau des besoins, fontaine, lampadaire). */
public final class VillageBlocks {

    public static final BlockEntry<NeedsBoardBlock> NEEDS_BOARD = PiCoreRegistries.block("needs_board", () -> new NeedsBoardBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 6.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final BlockEntry<FountainBlock> VILLAGE_FOUNTAIN = PiCoreRegistries.block("village_fountain", () -> new FountainBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F, 6.0F).sound(SoundType.STONE).noOcclusion()
                .requiresCorrectToolForDrops()));
    public static final BlockEntry<LamppostBlock> VILLAGE_LAMPPOST = PiCoreRegistries.block("village_lamppost", () -> new LamppostBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.LANTERN).noOcclusion()
                .lightLevel(s -> s.getValue(LamppostBlock.LIT) && s.getValue(LamppostBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ? 15 : 0)));

    public static final RegistryObject<BlockEntityType<NeedsBoardBlock.Tile>> BOARD_TILE = PiTileEntityTypes.VANILLA_TILES.register("needs_board",
          () -> BlockEntityType.Builder.of(NeedsBoardBlock.Tile::new, NEEDS_BOARD.getBlock()).build(null));

    private VillageBlocks() {
    }

    static void init() {
    }
}
