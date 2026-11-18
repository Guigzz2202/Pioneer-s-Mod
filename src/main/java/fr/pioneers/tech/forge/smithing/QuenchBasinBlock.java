package fr.pioneers.tech.forge.smithing;

import fr.pioneers.core.fluid.Buckets;
import fr.pioneers.tech.forge.ForgeBlock;
import fr.pioneers.tech.forge.HeatedMetal;
import fr.pioneers.tech.forge.automation.QuenchBasinTile;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * Bac de trempe (FACING + LEVEL 0..4 = niveau d'eau visible, vide = pas d'eau dessinee). Clic droit avec une piece chaude : elle est posee
 * dans l'eau et trempe {@link QuenchBasinTile#QUENCH_TICKS} ticks (vapeur, message d'avancement) ; main vide : reprendre la piece finie, sinon
 * l'etat du bac ; seau d'eau = remplir ({@link QuenchBasinTile#fill}, 1 000 mB). Entonnoirs, conduites et sortie par le dessous : la tuile.
 */
public class QuenchBasinBlock extends ForgeBlock {

    /** 1.1.1 : niveau d'eau visible (0 = vide, 4 = plein), mis par la tuile. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 4);

    public QuenchBasinBlock(Properties properties) {
        super(properties, QuenchBasinTile::new, Block.box(1, 0, 1, 15, 9, 15));
        registerDefaultState(defaultBlockState().setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEVEL);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (level.getBlockEntity(pos) instanceof QuenchBasinTile basin) {
            if (HeatedMetal.is(held)) {
                if (!level.isClientSide) {
                    boolean hot = HeatedMetal.isHot(held, level);
                    if (hot && basin.water() < QuenchBasinTile.WATER_PER_QUENCH) {
                        player.displayClientMessage(Component.translatable("message.pioneers.quench_basin.empty"), true);
                    } else if (!basin.insertManual(held)) {
                        player.displayClientMessage(Component.translatable("message.pioneers.quench_basin.busy"), true);
                    } else {
                        held.shrink(1);
                        player.displayClientMessage(Component.translatable(hot ? "message.pioneers.quench_basin.start"
                              : "message.pioneers.quench_basin.cold", QuenchBasinTile.QUENCH_TICKS / 20), true);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            //1.0.1 : tout seau d'eau (vanilla, en argile, d'un autre mod)
            if (Buckets.isWaterBucket(held)) {
                if (!level.isClientSide && basin.fill(Buckets.VOLUME, true) >= Buckets.VOLUME && Buckets.drainHeld(player, hand, Fluids.WATER)) {
                    basin.fill(Buckets.VOLUME, false);
                    level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1F, 1F);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (held.isEmpty()) {
                if (!level.isClientSide) {
                    List<ItemStack> done = basin.takeOutputs();
                    for (ItemStack stack : done) {
                        if (!player.getInventory().add(stack)) {
                            player.drop(stack, false);
                        }
                    }
                    if (done.isEmpty()) {
                        player.displayClientMessage(status(basin), true);
                    } else {
                        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.2F);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    /** Ligne d'etat : eau, piece en cours et secondes restantes. */
    private static Component status(QuenchBasinTile basin) {
        Component water = Component.translatable("message.pioneers.quench_basin.water", basin.water(), QuenchBasinTile.CAPACITY);
        ItemStack piece = basin.inputPiece();
        if (piece.isEmpty()) {
            return Component.translatable("message.pioneers.quench_basin.idle", water);
        }
        int left = Math.max(0, (QuenchBasinTile.QUENCH_TICKS - basin.progress() + 19) / 20);
        return Component.translatable("message.pioneers.quench_basin.working", water, piece.getHoverName(), left);
    }
}
