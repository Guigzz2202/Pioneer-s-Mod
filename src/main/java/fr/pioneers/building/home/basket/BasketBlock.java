package fr.pioneers.building.home.basket;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.3-beta (lot 11b) : panier en osier pose ; lot 12 : deux formats, demi-hauteur ({@code basket}) et haut ({@code tall_basket}). Clic droit avec un produit agricole : en depose un (Maj : toute la pile) ; main vide : reprend
 * une pile (Maj : un seul). Un panier ne garde qu'une sorte d'objet ({@link BasketTile}) ; il la garde cassé (butin copy_nbt), l'infobulle
 * de l'objet dit le contenu. Comparateur : remplissage.
 */
public class BasketBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 8, 14), TALL_SHAPE = box(2, 0, 2, 14, 13, 14);
    private final boolean tall;

    /** 1.0.3-beta (lot 12) : {@code tall} = panier haut (13 px, 8 piles) ; sinon demi-hauteur (8 px, 4 piles : le panier d'origine). */
    public BasketBlock(Properties properties, boolean tall) {
        super(properties);
        this.tall = tall;
    }

    public boolean tall() {
        return tall;
    }

    /** Piles que tient ce format. */
    public int stacks() {
        return tall ? BasketTile.TALL_STACKS : BasketTile.STACKS;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return tall ? TALL_SHAPE : SHAPE;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BasketTile(pos, state);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BasketTile tile)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            if (hand != InteractionHand.MAIN_HAND || tile.count() <= 0) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                ItemStack out = tile.extract(player.isShiftKeyDown() ? 1 : tile.kind().getMaxStackSize(), false);
                if (!player.getInventory().add(out)) {
                    player.drop(out, false);
                }
                level.playSound(null, pos, SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.7F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!BasketTile.accepts(held)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (!tile.sameKind(held)) {
                player.displayClientMessage(Component.translatable("message.pioneers.basket.other", tile.kind().getHoverName())
                      .withStyle(ChatFormatting.GOLD), true);
            } else {
                int took = tile.insert(held, player.isShiftKeyDown() ? held.getCount() : 1, false);
                if (took <= 0) {
                    player.displayClientMessage(Component.translatable("message.pioneers.basket.full").withStyle(ChatFormatting.GOLD), true);
                } else {
                    if (!player.getAbilities().instabuild) {
                        held.shrink(took);
                    }
                    level.playSound(null, pos, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.7F, 1.0F);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BasketTile tile ? tile.signal() : 0;
    }

    /** Infobulle de l'objet : contenu (nombre et nom) ou « vide », capacite. */
    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable BlockGetter level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        CompoundTag be = stack.getTagElement("BlockEntityTag");
        CompoundTag t = be == null ? new CompoundTag() : be.getCompound(BasketTile.KEY);
        ItemStack kind = BasketTile.readKind(t);
        int count = t.getInt("Count");
        if (kind.isEmpty() || count <= 0) {
            tips.stat("tooltip.pioneers.basket.content", Component.translatable("tooltip.pioneers.basket.empty"));
            tips.spec("tooltip.pioneers.basket.capacity", tall ? BasketTile.TALL_ITEMS : BasketTile.ITEMS);
        } else {
            tips.stat("tooltip.pioneers.basket.content", Component.translatable("tooltip.pioneers.basket.amount", count, kind.getHoverName()));
            tips.stat("tooltip.pioneers.basket.fill", count + " / " + BasketTile.capacityFor(kind, stacks()));
        }
    }
}
