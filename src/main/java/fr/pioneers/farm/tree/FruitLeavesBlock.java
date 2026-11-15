package fr.pioneers.farm.tree;

import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.crop.Climate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Feuilles d'arbre fruitier. {@link #FRUIT} : 0 = feuilles steriles, 1 = au repos, 2 = en fleur, 3 = fruit vert, 4 = mur (visible). Le cycle
 * avance au tick aleatoire si le climat du biome convient (serre comprise), en {@link TreeDef#minutes} en moyenne ; clic droit sur un fruit mur :
 * cueillette (1 a 2 fruits), la branche revient au repos. Pourrissement des feuilles vanilla conserve (distance au tronc).
 */
public class FruitLeavesBlock extends LeavesBlock {

    public static final IntegerProperty FRUIT = IntegerProperty.create("fruit", 0, 4);
    private static final float RANDOM_TICK_INTERVAL = 4096F / 3F;
    /** Tolerance climatique des arbres (equivaut a une Resistance de 3). */
    public static final float TOLERANCE = 0.17F;

    private final TreeDef def;

    public FruitLeavesBlock(TreeDef def, Properties properties) {
        super(properties);
        this.def = def;
        registerDefaultState(defaultBlockState().setValue(FRUIT, 1));
    }

    public TreeDef def() {
        return def;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FRUIT);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return super.isRandomlyTicking(state) || state.getValue(FRUIT) > 0 && state.getValue(FRUIT) < 4;
    }

    public Climate.Status climate(Level level, BlockPos pos) {
        return Climate.evaluate(level, pos, def.tMin(), def.tMax(), def.hMin(), def.hMax(), TOLERANCE, false);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (decaying(state)) {
            super.randomTick(state, level, pos, random);
            return;
        }
        int fruit = state.getValue(FRUIT);
        if (fruit == 0 || fruit == 4) {
            return;
        }
        Climate.Status climate = climate(level, pos);
        //3 etapes (repos -> fleur -> vert -> mur)
        float chance = 3F * RANDOM_TICK_INTERVAL / (Math.max(1F, def.minutes()) * 1200F) * climate.factor;
        if (random.nextFloat() < chance) {
            level.setBlock(pos, state.setValue(FRUIT, fruit + 1), 2);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(FRUIT) != 4) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            int n = 1 + (level.random.nextFloat() < 0.4F ? 1 : 0);
            popResourceFromFace(level, pos, hit.getDirection(), new ItemStack(PiFarm.item(def.fruit()), n));
            level.setBlock(pos, state.setValue(FRUIT, 1), 2);
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1F, 0.8F + level.random.nextFloat() * 0.4F);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
