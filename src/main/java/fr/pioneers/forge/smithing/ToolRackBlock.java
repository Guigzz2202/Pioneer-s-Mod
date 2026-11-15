package fr.pioneers.forge.smithing;

import fr.pioneers.forge.ForgeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.2-beta (lot 2, notes de Guillaume : « ajouter un porte outil pour stocker jusque 3 outils ») : PORTE-OUTILS de la forge. Ratelier de
 * chene cercle de fer pose au sol (FACING horizontal, modele {@code forge_tool_rack}) : trois outils (objets non empilables : marteau,
 * pince, lime, outils...) debout contre la traverse, dessines par {@code ToolRackRenderer}. Clic droit avec un outil : il se range dans
 * l'emplacement vise (ou le premier libre) ; main vide : on reprend l'outil vise (ou le dernier pose). Comparateur : nombre d'outils.
 * <p>
 * 1.0.3-beta (lot 14, Guillaume : « il faut que l'on puisse poser le porte-outils au mur et que la texture s'adapte ») : MEME bloc, meme
 * tuile, meme contenu : pose sur le FLANC d'un bloc solide = porte-outils MURAL ({@link #WALL} : planche du fond plaquee au mur, rail a
 * chevilles, pas de pieds ; {@code FACING} = face cliquee ; il tombe si le mur disparait) ; pose sur le dessus = version au sol d'avant.
 * Muraux voisins de meme facade : planches et rails continus ({@link #LEFT} / {@link #RIGHT} = cote x bas / x haut du modele facade au
 * sud), une seule rive a chaque bout de la rangee. Modeles : {@code T:home/racks.py}.
 */
public class ToolRackBlock extends ForgeBlock {

    public static final BooleanProperty WALL = BooleanProperty.create("wall");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    private static final VoxelShape FLOOR = Block.box(0, 0, 3, 16, 14, 13);
    /** Mural, facade au sud (mur au nord) puis tourne : planche, rail et outils (z 0..5). */
    private static final VoxelShape[] WALL_SHAPES = {Block.box(0, 1, 11, 16, 16, 16), Block.box(0, 1, 0, 5, 16, 16), Block.box(0, 1, 0, 16, 16, 5),
          Block.box(11, 1, 0, 16, 16, 16)};

    public ToolRackBlock(Properties properties) {
        super(properties, ToolRackTile::new, FLOOR);
        registerDefaultState(defaultBlockState().setValue(WALL, false).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WALL, LEFT, RIGHT);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isHorizontal()) {
            BlockState wall = defaultBlockState().setValue(FACING, face).setValue(WALL, true);
            if (wall.canSurvive(context.getLevel(), context.getClickedPos())) {
                return connect(wall, context.getLevel(), context.getClickedPos());
            }
        }
        return super.getStateForPlacement(context);
    }

    /** Mural : il lui faut le flanc solide du bloc de derriere ; au sol : comme avant. */
    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        if (!state.getValue(WALL)) {
            return true;
        }
        Direction f = state.getValue(FACING);
        BlockPos back = pos.relative(f.getOpposite());
        return level.getBlockState(back).isFaceSturdy(level, back, f);
    }

    /** Liaisons des muraux : voisin mural de meme facade du cote x bas (gauche) / x haut (droite) du modele. */
    public static BlockState connect(BlockState s, LevelAccessor level, BlockPos pos) {
        if (!s.getValue(WALL)) {
            return s.setValue(LEFT, false).setValue(RIGHT, false);
        }
        Direction f = s.getValue(FACING);
        return s.setValue(LEFT, joins(s, level.getBlockState(pos.relative(f.getClockWise()))))
              .setValue(RIGHT, joins(s, level.getBlockState(pos.relative(f.getCounterClockWise()))));
    }

    private static boolean joins(BlockState s, BlockState other) {
        return other.getBlock() instanceof ToolRackBlock && other.getValue(WALL) && other.getValue(FACING) == s.getValue(FACING);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos otherPos) {
        if (state.getValue(WALL) && dir == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            level.scheduleTick(pos, this, 1);
        }
        return connect(state, level, pos);
    }

    /** Le mur a disparu : le porte-outils tombe (bloc et outils rendus). */
    @Override
    @SuppressWarnings("deprecation")
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        if (!state.getValue(WALL)) {
            return FLOOR;
        }
        //facade au sud = mur au nord : index par facade (nord, est, sud, ouest -> le mur est derriere)
        return switch (state.getValue(FACING)) {
            case NORTH -> WALL_SHAPES[0];
            case EAST -> WALL_SHAPES[1];
            case WEST -> WALL_SHAPES[3];
            default -> WALL_SHAPES[2];
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ToolRackTile tile ? tile.count() * 5 : 0;
    }
}
