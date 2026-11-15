package fr.pioneers.farm.food;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.registries.PiTileEntityTypes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.9.2 (retours de Guillaume : « Tous les plats et ingredients doivent etre plaçables dans le monde ») : NOURRITURE POSEE EN TAS, un seul
 * bloc generique a tuile ({@link PlacedFoodTile} : une pile d'un meme objet) pour tout ce qui se mange ou se cuisine et n'etait pas deja
 * posable (les plats et boissons cuisines se servent sur assiette : {@link PlacedDishBlock} ; gateaux, tartes, meules sont des blocs) :
 * ingredients et preparations crues, vaisselle vide, recoltes et fruits, poissons et crustaces, oeufs, miel, farine, sel, sucre, et les
 * nourritures vanilla qu'on cuisine. Table generee {@link GeneratedPlacedFood} (outil {@code tools/gc/farm2/placed_items.py}) ; rendu en
 * volume par famille (tas, rangee, pile, grille, panier, sac, pot) : {@code client/farm/dish/PlacedFoodRenderer}.
 * <ul>
 *   <li>Maj + clic droit sur le dessus d'un bloc solide : l'objet se pose ({@link PlacedFoodEvents}). Restent prioritaires : manger (clic
 *   droit sans Maj), planter (si l'objet peut pousser la ou on vise : carotte sur terre labouree, baies sur l'herbe, canne pres de l'eau),
 *   relacher un poisson (clic droit vers l'eau, Maj + clic droit sur un flanc), les gestes des postes et appareils du mod (jamais de tas
 *   sur un bloc du mod a contenu).</li>
 *   <li>Maj + clic droit sur un tas du meme objet : une unite de plus (jusqu'a la capacite : 4 a 16 selon la famille).</li>
 *   <li>Clic droit main vide : on reprend une unite ; Maj + clic droit main vide : tout le tas. Casser le bloc rend tout.</li>
 * </ul>
 */
public class PlacedFoodBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static final RegistryObject<PlacedFoodBlock> BLOCK = PiCoreRegistries.BLOCKS.register("placed_food", () -> new PlacedFoodBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.2F).sound(SoundType.WOOL).noOcclusion().noLootTable()
                .pushReaction(PushReaction.DESTROY)));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<PlacedFoodTile>> TILE = PiTileEntityTypes.VANILLA_TILES.register("placed_food",
          () -> BlockEntityType.Builder.of(PlacedFoodTile::new, BLOCK.get()).build(null));

    /** Charge les registres (appele par {@code PiFarmDevices.init}). */
    public static void init() {
    }

    public PlacedFoodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // ------------------------------------------------------------------ table generee

    private static Map<Item, Integer> index;

    /** Rang de l'objet dans {@link GeneratedPlacedFood} (-1 : ne se pose pas en tas). Objets absents du jeu (retires) ignores. */
    public static int indexOf(ItemStack stack) {
        Map<Item, Integer> m = index;
        if (m == null) {
            m = new HashMap<>();
            for (int i = 0; i < GeneratedPlacedFood.IDS.length; i++) {
                ResourceLocation id = ResourceLocation.parse(GeneratedPlacedFood.IDS[i]);
                if (BuiltInRegistries.ITEM.containsKey(id)) {
                    m.putIfAbsent(BuiltInRegistries.ITEM.get(id), i);
                }
            }
            index = m;
        }
        return stack.isEmpty() ? -1 : m.getOrDefault(stack.getItem(), -1);
    }

    public static boolean placeable(ItemStack stack) {
        return indexOf(stack) >= 0;
    }

    /** Unites par bloc (jamais plus que la pile de l'objet). */
    public static int capacity(ItemStack stack) {
        int i = indexOf(stack);
        return i < 0 ? 1 : Math.max(1, Math.min(GeneratedPlacedFood.CAP[i], stack.getMaxStackSize()));
    }

    private static SoundEvent sound(ItemStack stack, boolean place) {
        int i = indexOf(stack);
        return switch (i < 0 ? 0 : GeneratedPlacedFood.SOUND[i]) {
            case 1 -> place ? SoundEvents.DECORATED_POT_PLACE : SoundEvents.DECORATED_POT_HIT;
            case 2 -> place ? SoundEvents.SLIME_BLOCK_PLACE : SoundEvents.SLIME_BLOCK_HIT;
            case 3 -> place ? SoundEvents.WOOD_PLACE : SoundEvents.WOOD_HIT;
            case 4 -> place ? SoundEvents.WOOL_PLACE : SoundEvents.WOOL_HIT;
            default -> place ? SoundEvents.MOSS_PLACE : SoundEvents.ITEM_PICKUP;
        };
    }

    // ------------------------------------------------------------------ poser

    /**
     * Maj + clic droit avec un objet qui se pose en tas ({@link PlacedFoodEvents}) : une unite de plus sur un tas du meme objet, ou un
     * nouveau tas sur le dessus d'un bloc solide. PASS : le geste habituel continue (planter, gestes des blocs du mod...).
     */
    public static InteractionResult place(Player player, Level level, InteractionHand hand, BlockHitResult hit, ItemStack held) {
        BlockPos clicked = hit.getBlockPos();
        BlockState on = level.getBlockState(clicked);
        if (on.getBlock() instanceof PlacedFoodBlock) {
            if (!(level.getBlockEntity(clicked) instanceof PlacedFoodTile tile) || !tile.accepts(held)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                tile.add(held.copyWithCount(1));
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level.playSound(null, clicked, sound(held, true), SoundSource.BLOCKS, 0.7F, 1.2F + level.random.nextFloat() * 0.2F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (hit.getDirection() != Direction.UP || !Block.canSupportCenter(level, clicked, Direction.UP)) {
            return InteractionResult.PASS;
        }
        //Les blocs du mod a contenu (postes, appareils, assiettes, etageres) gardent leurs gestes
        if (level.getBlockEntity(clicked) != null && Pioneers.MODID.equals(BuiltInRegistries.BLOCK.getKey(on.getBlock()).getNamespace())) {
            return InteractionResult.PASS;
        }
        BlockPos pos = clicked.above();
        if (!level.getBlockState(pos).canBeReplaced() || !level.getFluidState(pos).isEmpty()) {
            return InteractionResult.PASS;
        }
        //Planter reste prioritaire : si l'objet est un bloc qui peut tenir la (graine, tubercule, baies, canne), on le laisse faire
        if (held.getItem() instanceof BlockItem item) {
            BlockPlaceContext ctx = new BlockPlaceContext(player, hand, held, hit);
            BlockState plant = item.getBlock().getStateForPlacement(ctx);
            if (plant != null && plant.canSurvive(level, ctx.getClickedPos())) {
                return InteractionResult.PASS;
            }
        }
        if (!level.isClientSide) {
            level.setBlock(pos, BLOCK.get().defaultBlockState().setValue(FACING, player.getDirection().getOpposite()), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof PlacedFoodTile tile) {
                tile.add(held.copyWithCount(1));
            }
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            level.playSound(null, pos, sound(held, true), SoundSource.BLOCKS, 0.8F, 1.1F);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------------ reprendre

    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty() || !(level.getBlockEntity(pos) instanceof PlacedFoodTile tile)
            || tile.pile().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack kind = tile.pile().copyWithCount(1);
            ItemStack back = tile.take(player.isShiftKeyDown() ? tile.count() : 1);
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
            level.playSound(null, pos, sound(kind, false), SoundSource.BLOCKS, 0.6F, 1.3F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PlacedFoodTile tile && !tile.pile().isEmpty()) {
            //Rien ne se perd : toute la pile retombe (en piles de taille normale)
            ItemStack all = tile.pile().copy();
            while (!all.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, all.split(all.getMaxStackSize()));
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @NotNull
    @Override
    public ItemStack getCloneItemStack(@NotNull BlockState state, @NotNull HitResult target, @NotNull BlockGetter level, @NotNull BlockPos pos,
          @NotNull Player player) {
        return level.getBlockEntity(pos) instanceof PlacedFoodTile tile && !tile.pile().isEmpty() ? tile.pile().copyWithCount(1) : ItemStack.EMPTY;
    }

    @Override
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @NotNull
    @Override
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level, @NotNull BlockPos pos,
          @NotNull BlockPos otherPos) {
        return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                                                                    : super.updateShape(state, dir, other, level, pos, otherPos);
    }

    // ------------------------------------------------------------------ forme : suit le tas (table generee, par nombre)

    private static final Map<Integer, VoxelShape> SHAPES = new ConcurrentHashMap<>();
    private static final VoxelShape SMALL = Block.box(5, 0, 5, 11, 3, 11);

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        if (!(level.getBlockEntity(pos) instanceof PlacedFoodTile tile) || tile.pile().isEmpty()) {
            return SMALL;
        }
        int i = indexOf(tile.pile());
        if (i < 0) {
            return SMALL;
        }
        String s = GeneratedPlacedFood.SHAPE[i];
        int n = Math.max(1, Math.min(s.length() / 3, tile.count()));
        boolean turn = state.getValue(FACING).getAxis() == Direction.Axis.X;
        int key = (i * 64 + n) * 2 + (turn ? 1 : 0);
        return SHAPES.computeIfAbsent(key, k -> {
            double ex = Math.max(1, (s.charAt((n - 1) * 3) - '0') / 2.0), ez = Math.max(1, (s.charAt((n - 1) * 3 + 1) - '0') / 2.0);
            double h = Math.max(1, Math.min(16, (s.charAt((n - 1) * 3 + 2) - '0') / 2.0));
            if (turn) {
                double t = ex;
                ex = ez;
                ez = t;
            }
            return Block.box(8 - ex, 0, 8 - ez, 8 + ex, h, 8 + ez);
        });
    }

    /** La forme suit le tas (tuile) : pas de forme mise en cache par etat. */
    @Override
    public boolean hasDynamicShape() {
        return true;
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new PlacedFoodTile(pos, state);
    }
}
