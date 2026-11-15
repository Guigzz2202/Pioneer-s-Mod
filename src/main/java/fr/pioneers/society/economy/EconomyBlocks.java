package fr.pioneers.society.economy;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.society.economy.stall.StallBlock;
import fr.pioneers.society.economy.stall.StallTile;
import fr.pioneers.society.economy.stall.TallStallBlock;
import fr.pioneers.core.registries.PiTileEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;

/** 1.9 (MAJ RP, bloc 3) : blocs de l'economie (etal, comptoir, caisse de Colporteur ; 1.10.1 : bac de vente du village). */
public final class EconomyBlocks {

    /** 1.0.3-beta (lot 11) : etal de 2 blocs, toit raye blanc + couleur au choix ({@link TallStallBlock}). */
    public static final BlockEntry<TallStallBlock> MARKET_STALL = PiCoreRegistries.block("market_stall", () -> new TallStallBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 1200.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final BlockEntry<StallBlock> MARKET_COUNTER = PiCoreRegistries.block("market_counter", () -> new StallBlock(true,
          BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 1200.0F).sound(SoundType.LANTERN).noOcclusion()));
    /** 1.0.3-beta : {@code noOcclusion} (modele plus petit qu'un bloc : il masquait les faces de ses voisins, d'ou des trous dans le monde). */
    public static final BlockEntry<CrateBlock> PEDDLER_CRATE = PiCoreRegistries.block("peddler_crate", () -> new CrateBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));

    /** 1.10.1 : bac de vente du village (54 cases, vendu a minuit). */
    public static final BlockEntry<fr.pioneers.society.economy.bin.SaleBinBlock> SALE_BIN = PiCoreRegistries.block("sale_bin",
          () -> new fr.pioneers.society.economy.bin.SaleBinBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F, 6.0F)
                .sound(SoundType.WOOD).noOcclusion()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<fr.pioneers.society.economy.bin.SaleBinTile>> SALE_BIN_TILE = PiTileEntityTypes.VANILLA_TILES
          .register("sale_bin", () -> BlockEntityType.Builder.of(fr.pioneers.society.economy.bin.SaleBinTile::new, SALE_BIN.getBlock()).build(null));

    public static final RegistryObject<BlockEntityType<StallTile>> STALL_TILE = PiTileEntityTypes.VANILLA_TILES.register("market_stall",
          () -> BlockEntityType.Builder.of(StallTile::new, MARKET_STALL.getBlock(), MARKET_COUNTER.getBlock()).build(null));

    private EconomyBlocks() {
    }

    static void init() {
    }

    /** Caisse de Colporteur : decor des Haltes et des caravanes, orientee ; forme = le couvercle et les montants (1.0.3-beta). */
    public static class CrateBlock extends Block {

        public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        public static final VoxelShape SHAPE = Block.box(0.25, 0, 0.25, 15.75, 16, 15.75);

        public CrateBlock(Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }
    }
}
