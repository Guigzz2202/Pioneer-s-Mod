package fr.pioneers.logistics.pneumatic.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sableuse : un jet de sable sous pression decape le bloc vise (clic droit maintenu) : cuivre oxyde d'un cran vers le neuf, cire retiree
 * (elle tombe en rayon de miel), mousse et fissures des pierres et briques. Chaque bloc decape coute un sable (pris dans l'inventaire) et
 * {@value #PER_BLOCK} litres d'air.
 */
public class SandblasterItem extends AirToolItem {

    public static final double CAPACITY = 1500, PER_BLOCK = 25;
    private static final int PERIOD = 6;
    private static final Map<Block, Block> CLEAN = new HashMap<>();

    static {
        clean(Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE);
        clean(Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.COBBLESTONE_STAIRS);
        clean(Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.COBBLESTONE_SLAB);
        clean(Blocks.MOSSY_COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL);
        clean(Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS);
        clean(Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.STONE_BRICK_STAIRS);
        clean(Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.STONE_BRICK_SLAB);
        clean(Blocks.MOSSY_STONE_BRICK_WALL, Blocks.STONE_BRICK_WALL);
        clean(Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS);
        clean(Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS);
        clean(Blocks.CRACKED_DEEPSLATE_TILES, Blocks.DEEPSLATE_TILES);
        clean(Blocks.CRACKED_NETHER_BRICKS, Blocks.NETHER_BRICKS);
        clean(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS);
        clean(Blocks.INFESTED_MOSSY_STONE_BRICKS, Blocks.INFESTED_STONE_BRICKS);
        clean(Blocks.INFESTED_CRACKED_STONE_BRICKS, Blocks.INFESTED_STONE_BRICKS);
    }

    private static void clean(Block from, Block to) {
        CLEAN.put(from, to);
    }

    public SandblasterItem(Properties properties) {
        super(CAPACITY, properties);
    }

    @Override
    protected double perUse() {
        return PER_BLOCK;
    }

    /** Etat decape (ou rien si le bloc est deja propre) ; {@code wax} recoit vrai si c'est de la cire qui part. */
    @Nullable
    public static BlockState cleaned(BlockState state, boolean[] wax) {
        Block block = state.getBlock();
        Block unwaxed = HoneycombItem.WAX_OFF_BY_BLOCK.get().get(block);
        if (unwaxed != null) {
            wax[0] = true;
            return unwaxed.withPropertiesOf(state);
        }
        Optional<BlockState> younger = WeatheringCopper.getPrevious(state);
        if (younger.isPresent()) {
            return younger.get();
        }
        Block clean = CLEAN.get(block);
        return clean == null ? null : clean.withPropertiesOf(state);
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!has(stack, PER_BLOCK)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return 72_000;
    }

    @NotNull
    @Override
    public UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity user, @NotNull ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server) || remaining % PERIOD != 0 || !(user instanceof Player player)) {
            return;
        }
        if (!has(stack, PER_BLOCK)) {
            player.stopUsingItem();
            return;
        }
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getViewVector(1F).scale(5)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
              player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        boolean[] wax = {false};
        BlockState result = cleaned(state, wax);
        Vec3 at = hit.getLocation();
        server.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.05);
        if (result == null || !level.mayInteract(player, pos)) {
            return;
        }
        if (!player.getAbilities().instabuild && !takeSand(player)) {
            player.displayClientMessage(Component.translatable("item.pioneers.sandblaster.no_sand"), true);
            player.stopUsingItem();
            return;
        }
        use(stack, PER_BLOCK);
        level.setBlock(pos, result, Block.UPDATE_ALL_IMMEDIATE);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 12, 0.25, 0.25, 0.25, 0.1);
        level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
        if (wax[0]) {
            Block.popResource(level, pos.relative(hit.getDirection()), new ItemStack(Items.HONEYCOMB));
        }
    }

    /** Preleve un sable dans l'inventaire du joueur. */
    private static boolean takeSand(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(ItemTags.SAND)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }
}
