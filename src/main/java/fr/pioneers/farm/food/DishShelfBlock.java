package fr.pioneers.farm.food;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.home.HomeWoods;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import fr.pioneers.registries.PiTileEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.8 (demande de Guillaume : « des etageres qui accueillent jusqu'a 4 plats pour les exposer ; se connectent au mur ou au sol de maniere
 * logique et peuvent s'empiler ») : etagere a plats, dessinee avec ses objets par {@code client/farm/dish/DishShelfRenderer}.
 * <ul>
 *   <li>1.9.2 : liaisons par {@link ShelfJoints} (seulement les voisines alignees : meme facade, meme mode, planche a la meme hauteur) ;
 *   accepte aussi tout ce qui se pose en tas ({@link PlacedFoodBlock}).</li>
 *   <li>1.0.3-beta (lot 12, Guillaume : « une murale et une au sol ») : ce bloc est l'etagere AU SOL (les ids d'avant : etageres deja
 *   posees, contenu garde) ; la murale est un bloc a part ({@link WallShelfBlock}, ids {@code <bois>_wall_shelf}). Un module = 4 montants,
 *   une planche basse sur pattes et une planche haute ; empile, chaque module garde sa planche basse et seul celui du sommet garde la haute
 *   ({@link #UP}) ; cote a cote, montants partages et planches continues ({@link #LEFT} / {@link #RIGHT}). TOUTES les planches portent 4
 *   objets ({@link ShelfJoints#boards}) : planche basse places 0..3, planche haute 4..7. Quand une etagere vient se poser dessus, la planche
 *   haute disparait : ses objets passent sur la planche basse de la nouvelle (au meme endroit), sinon ils tombent ({@link #tick}).</li>
 *   <li>{@link #WALL} ne sert plus qu'aux etageres murales posees avant le lot 12 : au premier passage ({@link DishShelfTile#onLoad}),
 *   elles deviennent l'etagere murale du meme bois, meme orientation, meme contenu ({@link #tick}).</li>
 *   <li>Clic droit avec un plat ou une boisson : il se pose a la place visee de la planche visee (ou la premiere libre) ; main vide : on
 *   reprend le plus proche.</li>
 * </ul>
 */
public class DishShelfBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Etagere murale d'avant le lot 12 (convertie au premier passage) ; jamais vrai a la pose. */
    public static final BooleanProperty WALL = BooleanProperty.create("wall");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    /** Places par planche. */
    public static final int SLOTS = ShelfJoints.PER_BOARD;

    public static final BlockEntry<DishShelfBlock> ENTRY = PiCoreRegistries.block("dish_shelf", () -> new DishShelfBlock(properties("oak"), "oak"));
    /**
     * 1.0.3-beta (lot 7, Guillaume : « ameliorer le design des etageres et faire une variante par bois ») : une etagere par bois de
     * {@link HomeWoods#ALL} ; le chene garde l'id d'origine {@code dish_shelf} (etageres deja posees : meme bloc, meme contenu), les autres
     * {@code <bois>_dish_shelf}. Une seule tuile pour toutes ; les etageres de bois differents se lient comme celles d'un meme bois.
     */
    public static final Map<String, BlockEntry<DishShelfBlock>> VARIANTS = new LinkedHashMap<>();
    /** 1.0.3-beta (lot 12) : etageres murales, une par bois, ids {@code <bois>_wall_shelf} (meme tuile). */
    public static final Map<String, BlockEntry<WallShelfBlock>> WALL_VARIANTS = new LinkedHashMap<>();

    static {
        for (String wood : HomeWoods.ALL) {
            VARIANTS.put(wood, wood.equals("oak") ? ENTRY : PiCoreRegistries.block(id(wood), () -> new DishShelfBlock(properties(wood), wood)));
        }
        for (String wood : HomeWoods.ALL) {
            WALL_VARIANTS.put(wood, PiCoreRegistries.block(wallId(wood), () -> new WallShelfBlock(properties(wood), wood)));
        }
    }

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<DishShelfTile>> TILE = PiTileEntityTypes.VANILLA_TILES.register("dish_shelf",
          () -> BlockEntityType.Builder.of(DishShelfTile::new, Stream.concat(VARIANTS.values().stream().map(BlockEntry::getBlock),
                WALL_VARIANTS.values().stream().map(BlockEntry::getBlock)).toArray(Block[]::new)).build(null));

    /** 1.0.3-beta (lot 7) : id de l'etagere au sol d'un bois (le chene garde {@code dish_shelf}). */
    public static String id(String wood) {
        return wood.equals("oak") ? "dish_shelf" : wood + "_dish_shelf";
    }

    /** 1.0.3-beta (lot 12) : id de l'etagere murale d'un bois. */
    public static String wallId(String wood) {
        return wood + "_wall_shelf";
    }

    static BlockBehaviour.Properties properties(String wood) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).noOcclusion()
              .sound(wood.equals("bamboo") ? SoundType.BAMBOO_WOOD : wood.equals("cherry") ? SoundType.CHERRY_WOOD
                    : wood.equals("crimson") || wood.equals("warped") ? SoundType.NETHER_WOOD : SoundType.WOOD);
        return wood.equals("crimson") || wood.equals("warped") ? p : p.ignitedByLava();
    }

    /** Charge les registres (appele par {@code PiFarmDevices.init}). */
    public static void init() {
    }

    private final String wood;

    public DishShelfBlock(Properties properties, String wood) {
        super(properties);
        this.wood = wood;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WALL, false).setValue(UP, false).setValue(DOWN, false)
              .setValue(LEFT, false).setValue(RIGHT, false));
    }

    public String wood() {
        return wood;
    }

    // ------------------------------------------------------------------ geometrie (repere du modele : avant au nord, fond au sud)

    /** Planches qui portent des objets ({@link ShelfJoints#boards}) : au sol, ou murale ({@link WallShelfBlock}). */
    public static List<ShelfJoints.Board> boards(BlockState state) {
        if (state.getBlock() instanceof WallShelfBlock) {
            return ShelfJoints.boards(true, false, false);
        }
        return ShelfJoints.boards(false, state.getValue(UP), state.getValue(DOWN));
    }

    /** Position le long de la planche (0..1, repere du modele : ouest -> est) d'un point du monde dans ce bloc. */
    static double along(Direction facing, BlockPos pos, Vec3 hit) {
        double lx = hit.x - pos.getX(), lz = hit.z - pos.getZ();
        return switch (facing) {
            case EAST -> lz;
            case SOUTH -> 1 - lx;
            case WEST -> 1 - lz;
            default -> lx;
        };
    }

    // ------------------------------------------------------------------ pose et liaisons
    /** Etagere de cette case, decrite pour {@link ShelfJoints} (null : pas d'etagere). */
    @Nullable
    static ShelfJoints.Cell cell(BlockState s) {
        if (s.getBlock() instanceof WallShelfBlock) {
            return new ShelfJoints.Cell(s.getValue(WallShelfBlock.FACING).get2DDataValue(), true, false);
        }
        return s.getBlock() instanceof DishShelfBlock && !s.getValue(WALL) ? new ShelfJoints.Cell(s.getValue(FACING).get2DDataValue(), false,
              s.getValue(DOWN)) : null;
    }

    /**
     * 1.0.3-beta (lot 14) : voisine de cote decrite d'apres le MONDE (son {@code down} / {@code up} recalcules sur ses propres voisines du
     * dessus et du dessous, pas lus dans son etat qui peut etre en retard d'une mise a jour) : la liaison exige le meme role a ce niveau.
     */
    @Nullable
    static ShelfJoints.Cell side(LevelReader level, BlockPos p) {
        ShelfJoints.Cell c = cell(level.getBlockState(p));
        if (c == null || c.wall()) {
            return c;
        }
        return new ShelfJoints.Cell(c.facing(), false, ShelfJoints.stacks(c, cell(level.getBlockState(p.below()))),
              ShelfJoints.stacks(c, cell(level.getBlockState(p.above()))));
    }

    /** Liaisons calculees par {@link ShelfJoints} : seulement avec les voisines alignees (meme facade, meme mode, meme role a ce niveau). */
    public static BlockState connect(BlockState s, LevelReader level, BlockPos pos) {
        if (s.getValue(WALL)) {
            return s;
        }
        Direction f = s.getValue(FACING);
        int l = ShelfJoints.links(cell(s), cell(level.getBlockState(pos.above())), cell(level.getBlockState(pos.below())),
              side(level, pos.relative(f.getCounterClockWise())), side(level, pos.relative(f.getClockWise())));
        //1.0.3-beta (correctif, capture de Guillaume « il manque une patte ») : une jonction ne partage ses montants que si les deux colonnes
        //restent liees a TOUS les etages au-dessus ; sinon (deux montants plus haut) le montant du haut s'arretait sur la planche, sans pied
        boolean left = (l & ShelfJoints.LEFT) != 0 && columnLinked(level, pos, f.getCounterClockWise());
        boolean right = (l & ShelfJoints.RIGHT) != 0 && columnLinked(level, pos, f.getClockWise());
        return s.setValue(UP, (l & ShelfJoints.UP) != 0).setValue(DOWN, (l & ShelfJoints.DOWN) != 0).setValue(LEFT, left).setValue(RIGHT, right);
    }

    /**
     * Les etageres empilees au-dessus de {@code pos} et celles au-dessus de sa voisine du cote {@code dir} sont-elles liees etage par etage
     * jusqu'au sommet de la colonne ({@link ShelfJoints#beside}, roles recalcules d'apres le monde) ? Vrai si {@code pos} est au sommet.
     */
    static boolean columnLinked(LevelReader level, BlockPos pos, Direction dir) {
        BlockPos p = pos;
        for (int i = 0; i < 32; i++) {
            ShelfJoints.Cell here = side(level, p);
            if (here == null || !here.up()) {
                return true;
            }
            p = p.above();
            ShelfJoints.Cell up = side(level, p);
            if (!ShelfJoints.beside(up, side(level, p.relative(dir)))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Passage programme (chargement de la tuile, liaison qui change) : etagere murale d'avant le lot 12 → murale du meme bois ; liaisons a
     * jour ; planche haute recouverte par une etagere empilee → ses objets passent sur la planche basse de celle du dessus (sinon tombent).
     */
    @Override
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (state.getValue(WALL)) {
            convertWall(state, level, pos);
            return;
        }
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }
        BlockState now = connect(state, level, pos);
        if (now != state) {
            level.setBlock(pos, now, Block.UPDATE_ALL);
        }
        if (now.getValue(UP) && level.getBlockEntity(pos) instanceof DishShelfTile tile) {
            DishShelfTile above = level.getBlockEntity(pos.above()) instanceof DishShelfTile t ? t : null;
            for (int i = SLOTS; i < ShelfJoints.CAPACITY; i++) {
                if (tile.get(i).isEmpty()) {
                    continue;
                }
                ItemStack moved = tile.take(i);
                if (above != null && above.get(i - SLOTS).isEmpty()) {
                    above.set(i - SLOTS, moved);
                } else {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, moved);
                }
            }
        }
    }

    /** Etagere murale d'avant le lot 12 : remplacee par l'etagere murale du meme bois, meme orientation, memes 4 objets. */
    private void convertWall(BlockState state, ServerLevel level, BlockPos pos) {
        List<ItemStack> kept = new ArrayList<>();
        if (level.getBlockEntity(pos) instanceof DishShelfTile tile) {
            for (int i = 0; i < ShelfJoints.CAPACITY; i++) {
                kept.add(tile.get(i).copy());
                tile.take(i);
            }
        }
        BlockState wall = WALL_VARIANTS.get(wood).getBlock().defaultBlockState().setValue(WallShelfBlock.FACING, state.getValue(FACING));
        level.setBlock(pos, WallShelfBlock.connect(wall, level, pos), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof DishShelfTile tile) {
            for (int i = 0; i < kept.size(); i++) {
                if (i < SLOTS) {
                    tile.set(i, kept.get(i));
                } else if (!kept.get(i).isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, kept.get(i));
                }
            }
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState below = level.getBlockState(pos.below());
        //Empilement : meme orientation que l'etagere au sol du dessous ; sinon tournee vers le joueur. Toujours au sol (la murale est a part).
        Direction f = below.getBlock() instanceof DishShelfBlock && !below.getValue(WALL) ? below.getValue(FACING)
              : ctx.getHorizontalDirection().getOpposite();
        BlockState s = defaultBlockState().setValue(FACING, f);
        return s.canSurvive(level, pos) ? connect(s, level, pos) : null;
    }

    @Override
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        if (state.getValue(WALL)) {
            return true;
        }
        return ShelfJoints.survives(cell(state), cell(level.getBlockState(pos.below())),
              level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP), false);
    }

    @NotNull
    @Override
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level, @NotNull BlockPos pos,
          @NotNull BlockPos otherPos) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState now = connect(state, level, pos);
        if (now.getValue(UP) && !state.getValue(UP)) {
            level.scheduleTick(pos, this, 1);
        }
        return now;
    }

    // ------------------------------------------------------------------ poser, reprendre (communs a la murale)
    /** Plats et boissons qui se servent (hors gateaux et tartes, qui sont des blocs). */
    public static boolean shelvable(ItemStack stack) {
        //1.9.2 : aussi tout ce qui se pose en tas (ingredients, recoltes, vaisselle : PlacedFoodBlock)
        return stack.getItem() instanceof FarmFoodItem f && PlacedDishBlock.placeable(f.def()) || PlacedFoodBlock.placeable(stack);
    }

    /** Clic droit sur une planche ({@code board}) : poser l'objet tenu a la place visee, ou reprendre le plus proche main vide. */
    static InteractionResult interact(DishShelfTile tile, ShelfJoints.Board board, double along, Level level, BlockPos pos, Player player,
          InteractionHand hand) {
        int slot = board.first() + (int) Math.max(0, Math.min(SLOTS - 1, Math.floor(along * SLOTS)));
        ItemStack held = player.getItemInHand(hand);
        if (shelvable(held)) {
            int s = tile.get(slot).isEmpty() ? slot : tile.firstEmpty(board.first(), SLOTS);
            if (s < 0) {
                return InteractionResult.CONSUME;
            }
            if (!level.isClientSide) {
                tile.set(s, held.copyWithCount(1));
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.DECORATED_POT_PLACE, SoundSource.BLOCKS, 0.6F, 1.3F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (hand != InteractionHand.MAIN_HAND || !held.isEmpty()) {
            return InteractionResult.PASS;
        }
        int s = !tile.get(slot).isEmpty() ? slot : tile.nearestFull(slot, board.first(), SLOTS);
        if (s < 0) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack back = tile.take(s);
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
            level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 0.5F, 1.4F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Objets rendus a la casse (toutes les places). */
    static void dropAll(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof DishShelfTile tile) {
            for (int i = 0; i < ShelfJoints.CAPACITY; i++) {
                if (!tile.get(i).isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, tile.get(i).copy());
                }
            }
        }
    }

    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DishShelfTile tile)) {
            return InteractionResult.PASS;
        }
        List<ShelfJoints.Board> boards = boards(state);
        double y = (hit.getLocation().y - pos.getY()) * 16;
        ShelfJoints.Board board = boards.get(Math.min(boards.size() - 1, ShelfJoints.boardAt(false, state.getValue(UP), y)));
        return interact(tile, board, along(state.getValue(FACING), pos, hit.getLocation()), level, pos, player, hand);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            dropAll(level, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    // ------------------------------------------------------------------ formes (montants, planches), tournees selon l'orientation
    private static final VoxelShape[] SHAPES = new VoxelShape[4 * 16];

    static VoxelShape box(Direction f, double x0, double y0, double z0, double x1, double y1, double z1) {
        //Repere du modele (avant au nord) -> monde, comme la rotation y de l'etat
        return switch (f) {
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    private static VoxelShape shape(BlockState state) {
        Direction f = state.getValue(FACING);
        boolean down = state.getValue(DOWN), up = state.getValue(UP), left = state.getValue(LEFT), right = state.getValue(RIGHT);
        int key = f.get2DDataValue() * 16 + (down ? 8 : 0) + (up ? 4 : 0) + (left ? 2 : 0) + (right ? 1 : 0);
        VoxelShape s = SHAPES[key];
        if (s != null) {
            return s;
        }
        //1.0.3-beta (lot 12) : planche basse (sur pattes ou au ras du bloc), planche haute au sommet, montants sur toute la hauteur
        double b = down ? ShelfJoints.BASE : ShelfJoints.LOW;
        s = box(f, 0, b, ShelfJoints.Z0, 16, b + ShelfJoints.BOARD, ShelfJoints.Z1);
        if (!up) {
            s = Shapes.or(s, box(f, 0, ShelfJoints.TOP - ShelfJoints.BOARD, ShelfJoints.Z0, 16, ShelfJoints.TOP, ShelfJoints.Z1));
        }
        if (!left) {
            s = Shapes.or(s, box(f, 0.25, 0, 3.25, 1.75, 16, 4.75), box(f, 0.25, 0, 14.25, 1.75, 16, 15.75));
        }
        if (!right) {
            s = Shapes.or(s, box(f, 14.25, 0, 3.25, 15.75, 16, 4.75), box(f, 14.25, 0, 14.25, 15.75, 16, 15.75));
        }
        SHAPES[key] = s;
        return s;
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return shape(state);
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WALL, UP, DOWN, LEFT, RIGHT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new DishShelfTile(pos, state);
    }
}
