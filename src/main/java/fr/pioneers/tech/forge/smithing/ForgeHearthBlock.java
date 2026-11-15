package fr.pioneers.tech.forge.smithing;

import fr.pioneers.tech.forge.ForgeBlock;
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
import org.jetbrains.annotations.NotNull;

/**
 * Foyer de forge en pierre (FACING + LIT) : chauffe les lingots jusqu'a la temperature de travail de leur metal, sans les fondre.
 * Entonnoirs : lingots par le haut, charbon par les cotes, metal chauffe par le dessous ({@link ForgeHearthTile}). Allume : lumiere 13,
 * flammeches et fumee au-dessus de la braise, crepitement.
 */
public class ForgeHearthBlock extends ForgeBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ForgeHearthBlock(Properties properties) {
        super(properties.lightLevel(s -> s.getValue(LIT) ? 13 : 0), ForgeHearthTile::new, Block.box(0, 0, 0, 16, 12, 16));
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.25 + random.nextDouble() * 0.5, z = pos.getZ() + 0.25 + random.nextDouble() * 0.5, y = pos.getY() + 0.72;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0, 0.01, 0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.1, z, 0, 0.03, 0);
        }
        if (random.nextInt(12) == 0) {
            level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
        }
        if (random.nextInt(24) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                  0.6F + random.nextFloat() * 0.4F, 0.7F + random.nextFloat() * 0.5F, false);
        }
    }
}
