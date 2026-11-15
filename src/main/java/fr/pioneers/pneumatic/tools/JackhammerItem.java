package fr.pioneers.pneumatic.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.TierSortingRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Marteau-piqueur : creuse la pierre et la terre tres vite, en 3 x 3 dans le plan de la face visee (niveau de minage du diamant). Chaque
 * bloc coute {@value #PER_BLOCK} litres d'air ; reservoir vide, il ne vaut pas mieux qu'une main nue. Jamais les blocs a contenu (machines,
 * coffres) ni les blocs incassables.
 */
public class JackhammerItem extends AirToolItem {

    public static final double CAPACITY = 2000, PER_BLOCK = 50;
    private static final float SPEED = 14F;
    /** Garde-fou : les 8 blocs voisins cassent sans relancer le 3 x 3. */
    private static boolean breaking;

    public JackhammerItem(Properties properties) {
        super(CAPACITY, properties);
    }

    @Override
    protected double perUse() {
        return PER_BLOCK;
    }

    /** Pierre, terre, sable, gravier : ce que la pointe attaque. */
    public static boolean target(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    @Override
    public float getDestroySpeed(@NotNull ItemStack stack, @NotNull BlockState state) {
        return target(state) && has(stack, PER_BLOCK) ? SPEED : 1F;
    }

    @Override
    public boolean isCorrectToolForDrops(@NotNull ItemStack stack, @NotNull BlockState state) {
        return target(state) && TierSortingRegistry.isCorrectTierForDrops(Tiers.DIAMOND, state);
    }

    @Override
    public boolean mineBlock(@NotNull ItemStack stack, @NotNull Level level, @NotNull BlockState state, @NotNull BlockPos pos, @NotNull LivingEntity miner) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) > 0 && target(state)) {
            use(stack, PER_BLOCK);
            if (!breaking) {
                level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.18F, 1.9F + level.random.nextFloat() * 0.2F);
            }
        }
        return true;
    }

    /** Casse les 8 voisins du bloc dans le plan de la face visee (appele quand le joueur casse un bloc avec le marteau-piqueur). */
    static void area(ServerPlayer player, ItemStack stack, BlockPos center, BlockState centerState) {
        if (breaking || !target(centerState) || player.isShiftKeyDown()) {
            return;
        }
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1F).scale(6));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Direction face = hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(center) ? hit.getDirection() : Direction.UP;
        float hardness = centerState.getDestroySpeed(level, center);
        breaking = true;
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) {
                        continue;
                    }
                    BlockPos pos = switch (face.getAxis()) {
                        case X -> center.offset(0, a, b);
                        case Y -> center.offset(a, 0, b);
                        default -> center.offset(a, b, 0);
                    };
                    if (!has(stack, PER_BLOCK) || !(player.getMainHandItem() == stack)) {
                        return;
                    }
                    BlockState state = level.getBlockState(pos);
                    float h = state.getDestroySpeed(level, pos);
                    if (state.isAir() || h < 0 || h > hardness * 2 + 1 || level.getBlockEntity(pos) != null || !target(state)
                        || !level.mayInteract(player, pos)) {
                        continue;
                    }
                    player.gameMode.destroyBlock(pos);
                }
            }
        } finally {
            breaking = false;
        }
    }
}
