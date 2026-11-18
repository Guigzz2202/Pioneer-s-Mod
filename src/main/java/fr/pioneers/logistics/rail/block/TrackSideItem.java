package fr.pioneers.logistics.rail.block;

import fr.pioneers.core.block.DescribedBlockItem;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.logistics.rail.track.TrackBlock;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.1 : objet d'un bloc de bord de voie (signaux, panneau de gare, quais a objets, grue a eau) : un clic sur une voie droite le pose a
 * cote d'elle, dans le sens du regard ({@link TrackSide#spot}) ; Maj + clic inverse le sens (ou le cote). L'apercu (fantome et fleche) est
 * dessine par {@code client/rail/TrackSidePreview}. Un clic ailleurs pose le bloc comme d'habitude.
 */
public class TrackSideItem extends DescribedBlockItem {

    /** Bloc a sens (signal, gare : commande les trains d'un sens) ; sinon simple voisin de la voie. */
    public final boolean directional;
    /** 0.20.2 : bloc pose SOUS la voie (fosse de dechargement). */
    public final boolean below;

    public TrackSideItem(Block block, Properties properties, String description, boolean directional) {
        this(block, properties, description, directional, false);
    }

    public TrackSideItem(Block block, Properties properties, String description, boolean directional, boolean below) {
        super(block, properties, description);
        this.directional = directional;
        this.below = below;
    }

    /** Emplacement vise (sous la voie pour une fosse). */
    @Nullable
    public TrackSide.Spot spot(BlockGetter level, BlockPos track, Player player, Vec3 hit) {
        if (below) {
            TrackSide.Spot s = TrackSide.spot(level, track, player, hit, false);
            if (s == null) {
                return null;
            }
            BlockPos pos = track.below();
            return new TrackSide.Spot(pos, track, s.heading(), Direction.UP, swappable(level, pos));
        }
        return TrackSide.spot(level, track, player, hit, directional);
    }

    /** 0.20.3 : la fosse prend la place du bloc qui porte la voie (bloc simple : ni machine, ni voie, ni incassable). */
    private static boolean swappable(BlockGetter level, BlockPos pos) {
        BlockState there = level.getBlockState(pos);
        return there.canBeReplaced() || !there.hasBlockEntity() && there.getDestroySpeed(level, pos) >= 0 && !(there.getBlock() instanceof TrackBlock)
              && !(there.getBlock() instanceof RailPostBlock);
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null || !(level.getBlockState(ctx.getClickedPos()).getBlock() instanceof TrackBlock)) {
            return super.useOn(ctx);
        }
        TrackSide.Spot spot = spot(level, ctx.getClickedPos(), player, ctx.getClickLocation());
        if (spot == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("rail.pioneers.side.straight_only").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (!spot.free()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("rail.pioneers.side.blocked").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        BlockState old = level.getBlockState(spot.pos());
        boolean swap = below && !old.canBeReplaced();
        if (swap) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            //Le bloc support part (rendu au joueur hors creatif) sans prevenir la voie, qui tomberait sinon le temps de poser la fosse
            if (!player.isCreative()) {
                Block.dropResources(old, level, spot.pos(), null, player, player.getMainHandItem());
            }
            level.setBlock(spot.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        BlockPlaceContext place = new BlockPlaceContext(level, player, ctx.getHand(), ctx.getItemInHand(),
              new BlockHitResult(Vec3.atCenterOf(spot.pos()), Direction.UP, spot.pos(), false));
        TrackSide.placing(spot);
        try {
            InteractionResult result = place(place);
            if (swap && !result.consumesAction()) {
                level.setBlock(spot.pos(), old, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            return result;
        } finally {
            TrackSide.placing(null);
        }
    }

    @Nullable
    @Override
    protected BlockState getPlacementState(@NotNull BlockPlaceContext ctx) {
        BlockState state = super.getPlacementState(ctx);
        TrackSide.Spot spot = TrackSide.placing();
        if (state != null && spot != null && spot.pos().equals(ctx.getClickedPos()) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
              && spot.toTrack().getAxis().isHorizontal()) {
            //Bloc a sens : tourne vers les trains commandes ; quai, grue : la facade regarde a l'oppose de la voie (le bras vers elle)
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, directional ? spot.heading() : spot.toTrack().getOpposite());
        }
        return state;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.hint(below ? "rail.pioneers.side.how_below" : directional ? "rail.pioneers.side.how_directional"
              : "rail.pioneers.side.how");
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
