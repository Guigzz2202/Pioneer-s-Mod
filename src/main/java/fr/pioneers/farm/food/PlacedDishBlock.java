package fr.pioneers.farm.food;

import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.registries.PiTileEntityTypes;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
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
 * 1.4 (demande de Guillaume : « pouvoir poser les plats dans le monde ») : plat pose sur une assiette. Tout plat cuisine ({@link FoodDef} de
 * genre {@code dish}, hors gateaux et tartes qui ont leur propre bloc) se pose par Maj + clic droit sur le dessus d'un bloc, jusqu'a
 * {@link #MAX} du meme plat. Maj + clic droit main vide : on reprend une portion entiere.
 * <p>
 * 1.6 (lot A1, demandes de Guillaume : « les modeles quand on pose les plats ne sont pas jolis », « poser les boissons ») : les boissons se
 * servent aussi ; le support ({@link #BASE} : assiette, planche, ou rien quand le maillage porte son contenant) et le nombre de bouchees par
 * portion viennent de {@link GeneratedServing} (outil {@code tools/gc/farm2/placed_dish.py}) ; clic droit = une bouchee ou une gorgee : la
 * nourriture est partagee entre les bouchees, la derniere donne les effets, l'alcool et rend le contenant (bol, bouteille, tasse, chope) ;
 * le plat est dessine en volume par {@code client/farm/dish/PlacedDishRenderer} et diminue a chaque bouchee.
 */
public class PlacedDishBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Base> BASE = EnumProperty.create("base", Base.class);
    public static final int MAX = 4;

    /** Support du plat (modele de bloc). */
    public enum Base implements StringRepresentable {
        PLATE("plate"), BOARD("board"), NONE("none");

        private final String name;

        Base(String name) {
            this.name = name;
        }

        @NotNull
        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final RegistryObject<PlacedDishBlock> BLOCK = PiCoreRegistries.BLOCKS.register("placed_dish", () -> new PlacedDishBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(0.3F).sound(SoundType.DECORATED_POT).noOcclusion()
                .pushReaction(PushReaction.DESTROY)));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<PlacedDishTile>> TILE = PiTileEntityTypes.VANILLA_TILES.register("placed_dish",
          () -> BlockEntityType.Builder.of(PlacedDishTile::new, BLOCK.get()).build(null));

    /** Charge les registres (appele par {@code PiFarmDevices.init}). */
    public static void init() {
    }

    public PlacedDishBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BASE, Base.PLATE));
    }

    // ------------------------------------------------------------------ table des plats servis

    /** Support, bouchees par portion et hauteur (px) d'un plat ou d'une boisson ; repli quand le plat n'est pas dans la table. */
    public record Serving(Base base, int bites, int height) {
    }

    private static Map<String, Serving> servings;

    public static Serving serving(ItemStack stack) {
        Map<String, Serving> m = servings;
        if (m == null) {
            m = new HashMap<>();
            for (int i = 0; i < GeneratedServing.IDS.length; i++) {
                m.put(GeneratedServing.IDS[i], new Serving(Base.values()[GeneratedServing.BASE[i]], GeneratedServing.BITES[i], GeneratedServing.HEIGHT[i]));
            }
            servings = m;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Serving s = m.get(id.getPath());
        if (s == null || !id.getNamespace().equals("pioneers")) {
            boolean drink = stack.getItem() instanceof FarmFoodItem f && f.def().drink();
            s = new Serving(drink ? Base.NONE : Base.PLATE, drink ? 3 : 3, drink ? 12 : 5);
        }
        return s;
    }

    /** Ce plat ou cette boisson peut-il etre servi ? */
    public static boolean placeable(FoodDef def) {
        return ("dish".equals(def.kind()) || def.drink()) && def.bites() <= 0;
    }

    /** Pose depuis l'objet (Maj + clic droit) : nouvelle assiette sur le dessus du bloc, ou une portion de plus sur une assiette du meme plat. */
    public static InteractionResult place(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        ItemStack held = ctx.getItemInHand();
        BlockPos clicked = ctx.getClickedPos();
        boolean drink = held.getItem() instanceof FarmFoodItem f && f.def().drink();
        if (level.getBlockEntity(clicked) instanceof PlacedDishTile tile) {
            if (!tile.accepts(held)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                tile.add(held.copyWithCount(1));
                if (player == null || !player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level.playSound(null, clicked, drink ? SoundEvents.BOTTLE_EMPTY : SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 0.6F, 1.3F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (ctx.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        BlockPos pos = clicked.above();
        BlockState state = BLOCK.get().defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite())
              .setValue(BASE, serving(held).base());
        if (!level.getBlockState(pos).canBeReplaced() || !state.canSurvive(level, pos)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof PlacedDishTile tile) {
                tile.add(held.copyWithCount(1));
            }
            if (player == null || !player.getAbilities().instabuild) {
                held.shrink(1);
            }
            level.playSound(null, pos, drink ? SoundEvents.BOTTLE_FILL : SoundEvents.DECORATED_POT_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Part de la nourriture donnee par la bouchee {@code k} (0..bites-1) : la somme fait exactement la nourriture du plat. */
    static int share(int nutrition, int bites, int k) {
        return Math.round(nutrition * (k + 1) / (float) bites) - Math.round(nutrition * k / (float) bites);
    }

    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PlacedDishTile tile) || tile.dish().isEmpty() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!held.isEmpty()) {
                return InteractionResult.PASS;
            }
            if (tile.whole() <= 0) {
                //Seule reste la portion entamee : elle ne redevient pas un objet entier
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("farm.pioneers.food.dish_started"), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {
                ItemStack back = tile.takeWhole();
                if (!player.getInventory().add(back)) {
                    player.drop(back, false);
                }
                tile.emptyCheck();
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        ItemStack dish = tile.dish();
        FoodProperties food = dish.getItem().getFoodProperties(dish, player);
        boolean always = food != null && food.canAlwaysEat();
        if (!player.canEat(always) && !player.getAbilities().instabuild) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            boolean drink = dish.getItem() instanceof FarmFoodItem f && f.def().drink();
            int bites = Math.max(1, serving(dish).bites());
            int k = Math.min(tile.bites(), bites - 1);
            int n = food == null ? 0 : share(food.getNutrition(), bites, k);
            float sat = food == null ? 0 : food.getSaturationModifier();
            if (k < bites - 1) {
                //Bouchee : sa part de nourriture seulement
                if (n > 0) {
                    player.getFoodData().eat(n, sat);
                }
                tile.bite();
            } else {
                //Derniere bouchee : effets, alcool, contenant rendu, statistiques (finishUsingItem), mais seulement la part de nourriture qui reste
                FoodData data = player.getFoodData();
                int food0 = data.getFoodLevel();
                float sat0 = data.getSaturationLevel();
                ItemStack one = tile.takeEaten();
                ItemStack rest = one.finishUsingItem(level, player);
                if (food != null && bites > 1 && !player.getAbilities().instabuild) {
                    int f = Math.min(20, food0 + n);
                    data.setFoodLevel(f);
                    data.setSaturation(Math.min(sat0 + n * sat * 2F, f));
                }
                if (!rest.isEmpty() && !player.getAbilities().instabuild) {
                    if (!player.getInventory().add(rest)) {
                        player.drop(rest, false);
                    }
                }
            }
            level.playSound(null, pos, drink ? SoundEvents.GENERIC_DRINK : SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8F,
                  0.9F + level.random.nextFloat() * 0.2F);
            if (!drink && level instanceof ServerLevel server) {
                server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, dish.copyWithCount(1)), pos.getX() + 0.5, pos.getY() + 0.25,
                      pos.getZ() + 0.5, 6, 0.12, 0.04, 0.12, 0.04);
            }
            level.gameEvent(player, drink ? GameEvent.DRINK : GameEvent.EAT, pos);
            tile.emptyCheck();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PlacedDishTile tile && tile.whole() > 0) {
            //La portion entamee est perdue (sinon on la reposerait entiere)
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, tile.dish().copyWithCount(tile.whole()));
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @NotNull
    @Override
    public ItemStack getCloneItemStack(@NotNull BlockState state, @NotNull HitResult target, @NotNull BlockGetter level, @NotNull BlockPos pos,
          @NotNull Player player) {
        return level.getBlockEntity(pos) instanceof PlacedDishTile tile && !tile.dish().isEmpty() ? tile.dish().copyWithCount(1) : ItemStack.EMPTY;
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

    /** Formes par support et hauteur (px) : la boite suit le plat ou la boisson servie. */
    private static final VoxelShape[][] SHAPES = new VoxelShape[3][17];

    private static VoxelShape shape(Base base, int height) {
        int h = Math.max(2, Math.min(16, height));
        VoxelShape s = SHAPES[base.ordinal()][h];
        if (s == null) {
            s = switch (base) {
                case PLATE -> Block.box(1.3, 0, 1.3, 14.7, h, 14.7);
                case BOARD -> Block.box(0.5, 0, 0.5, 15.5, h, 15.5);                    //la planche tourne avec le plat
                case NONE -> Block.box(2, 0, 2, 14, h, 14);
            };
            SHAPES[base.ordinal()][h] = s;
        }
        return s;
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        int h = level.getBlockEntity(pos) instanceof PlacedDishTile tile && !tile.dish().isEmpty() ? serving(tile.dish()).height() : 3;
        return shape(state.getValue(BASE), h);
    }

    /** La forme suit le plat servi (tuile) : pas de forme mise en cache par etat. */
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
        builder.add(FACING, BASE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new PlacedDishTile(pos, state);
    }
}
