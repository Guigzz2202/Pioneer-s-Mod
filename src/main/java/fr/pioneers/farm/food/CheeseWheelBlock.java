package fr.pioneers.farm.food;

import fr.pioneers.farm.FarmCard;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Meule de fromage posee : la meule fraiche s'affine a l'abri de la lumiere (cave : lumiere totale au plus {@link #DARK}), d'un cran par
 * tick aleatoire reussi ({@link #CHANCE}), et devient une meule affinee au dernier cran. Une meule cassee en cours d'affinage redevient
 * fraiche (le butin est l'objet d'origine).
 */
public class CheeseWheelBlock extends Block {

    public static final IntegerProperty RIPENING = IntegerProperty.create("ripening", 0, 3);
    public static final int DARK = 7;
    public static final int CHANCE = 4;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 7, 14);

    @Nullable
    private final Supplier<? extends Block> aged;

    /** {@code aged} : meule affinee qui remplace la fraiche (null = meule deja affinee). */
    public CheeseWheelBlock(@Nullable Supplier<? extends Block> aged, Properties properties) {
        super(properties);
        this.aged = aged;
        registerDefaultState(stateDefinition.any().setValue(RIPENING, 0));
    }

    public boolean fresh() {
        return aged != null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RIPENING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level,
          BlockPos pos, BlockPos otherPos) {
        return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
              : super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return aged != null;
    }

    public static boolean dark(Level level, BlockPos pos) {
        return level.getMaxLocalRawBrightness(pos) <= DARK;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (aged == null || !dark(level, pos) || random.nextInt(CHANCE) != 0) {
            return;
        }
        int r = state.getValue(RIPENING);
        if (r < 3) {
            level.setBlock(pos, state.setValue(RIPENING, r + 1), 2);
        } else {
            level.setBlock(pos, aged.get().defaultBlockState(), 3);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0);
        }
    }

    /** Fiche au regard (client). */
    public FarmCard card(Level level, BlockPos pos, BlockState state) {
        FarmCard c = FarmCard.of(new ItemStack(this), getName(), 0xF0C850);
        //1.6 : fiche synthetique (affinage en jauge, cave en un mot)
        if (aged == null) {
            c.add(FarmCard.Line.pair(FarmCard.label("state"), FarmCard.word("cheese.aged"), FarmCard.OK));
            return c;
        }
        int stage = state.getValue(RIPENING);
        c.add(FarmCard.Line.gauge(Component.translatable("farm.pioneers.cheese.ripening"), stage / 4F, FarmCard.ratio(stage, 4), 0xF0C850));
        boolean dark = dark(level, pos);
        c.add(FarmCard.Line.pair(FarmCard.label("cellar"), FarmCard.word(dark ? "cheese.dark" : "cheese.bright"), dark ? FarmCard.OK : FarmCard.WARN));
        return c;
    }
}
