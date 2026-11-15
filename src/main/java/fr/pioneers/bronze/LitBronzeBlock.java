package fr.pioneers.bronze;

import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Bloc de l'age du bronze qui a un feu : etat {@code lit} (modele allume, lumiere, fumee). */
public class LitBronzeBlock extends BronzeBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private final double smokeY;

    public LitBronzeBlock(Properties properties, BiFunction<BlockPos, BlockState, ? extends BronzeTile> factory, @Nullable VoxelShape shape, double smokeY) {
        super(properties.lightLevel(state -> state.getValue(LIT) ? 12 : 0), factory, shape);
        this.smokeY = smokeY;
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (state.getValue(LIT)) {
            //1.0.4-beta (lot 25) : multiblocs maison : la fumee sort de LEUR bouche (houseFx du controleur, smokeY 0) ; le crepitement reste
            if (smokeY > 0) {
                level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + smokeY, pos.getZ() + 0.3
                      + random.nextDouble() * 0.4, 0, 0.04, 0);
            }
            if (random.nextInt(6) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FURNACE_FIRE_CRACKLE,
                      SoundSource.BLOCKS, 0.6F, 1.0F, false);
            }
        }
    }

    /** Met l'etat allume a jour (sans toucher au reste). */
    public static void setLit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (state.hasProperty(LIT) && state.getValue(LIT) != lit) {
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
        }
    }
}
