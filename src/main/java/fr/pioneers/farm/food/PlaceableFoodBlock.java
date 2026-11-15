package fr.pioneers.farm.food;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Plat a poser (gateau, tartes, pizza, quiche) : se mange part par part (clic droit), comme le gateau vanilla ; chaque part donne la nourriture
 * et les effets de la {@link FoodDef}. {@link #BITES} : parts deja mangees (0..5 ; le plat a {@link FoodDef#bites()} parts).
 */
public class PlaceableFoodBlock extends HorizontalDirectionalBlock {

    public static final IntegerProperty BITES = IntegerProperty.create("bites", 0, 5);
    private final FoodDef def;
    private final VoxelShape[] shapes;

    public PlaceableFoodBlock(FoodDef def, Properties properties) {
        super(properties);
        this.def = def;
        registerDefaultState(stateDefinition.any().setValue(BITES, 0).setValue(FACING, Direction.NORTH));
        int parts = Math.max(1, def.bites());
        boolean cake = "cake".equals(def.id());
        shapes = new VoxelShape[parts];
        for (int i = 0; i < parts; i++) {
            double cut = 14.0 * i / parts;
            shapes[i] = Block.box(1 + cut, 0, 1, 15, cake ? 8 : 4, 15);
        }
    }

    public FoodDef def() {
        return def;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BITES, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[Math.min(state.getValue(BITES), shapes.length - 1)];
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.canEat(false) && def.nutrition() > 0 && !player.isCreative()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            player.getFoodData().eat(def.nutrition(), def.saturation());
            for (int i = 0; i < def.effects().length; i++) {
                player.addEffect(new MobEffectInstance(PiEffects.byName(def.effects()[i]), def.seconds()[i] * 20, def.amplifiers()[i]));
            }
            level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1F, 0.9F + level.random.nextFloat() * 0.2F);
            level.gameEvent(player, GameEvent.EAT, pos);
            int bites = state.getValue(BITES) + 1;
            if (bites >= Math.max(1, def.bites())) {
                level.removeBlock(pos, false);
                level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
            } else {
                level.setBlock(pos, state.setValue(BITES, bites), 3);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        return dir == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid();
    }
}
