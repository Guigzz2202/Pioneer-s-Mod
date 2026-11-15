package fr.pioneers.clan.war;

import fr.pioneers.core.block.DescribedBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 4) : l'objet Drapeau de siege. Il ne se pose jamais comme un bloc ordinaire : le serveur verifie la guerre, le creneau,
 * le voisinage et la permission de siege ({@link Siege#plant}). Clic droit sur le sol : planter a cet endroit (si la protection du
 * territoire a refuse le clic sur le bloc, le clic qui suit plante au meme endroit) ; clic dans le vide : devant soi, sinon a ses pieds.
 */
public class SiegeFlagItem extends DescribedBlockItem {

    public SiegeFlagItem(Block block, Properties properties) {
        super(block, properties, "description.pioneers.siege_flag");
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide || !(ctx.getPlayer() instanceof ServerPlayer p) || !(level instanceof ServerLevel sl)) {
            //le client laisse passer : s'il n'y a pas de reponse sur le bloc, le clic dans le vide suit
            return InteractionResult.PASS;
        }
        BlockPos pos = new BlockPlaceContext(ctx).getClickedPos();
        return Siege.plant(p, sl, pos, ctx.getItemInHand()) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer p) || !(level instanceof ServerLevel sl)) {
            return InteractionResultHolder.pass(stack);
        }
        if (Siege.triedThisTick(p)) {
            return InteractionResultHolder.pass(stack);
        }
        //le bloc vise (la protection du territoire a pu refuser le clic sur ce bloc), sinon devant soi, sinon a ses pieds
        BlockHitResult hit = getPlayerPOVHitResult(sl, p, ClipContext.Fluid.NONE);
        BlockPos pos;
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos at = hit.getBlockPos();
            pos = sl.getBlockState(at).canBeReplaced() ? at : at.relative(hit.getDirection());
        } else {
            BlockPos front = p.blockPosition().relative(p.getDirection());
            pos = sl.getBlockState(front).canBeReplaced() ? front : p.blockPosition();
        }
        return Siege.plant(p, sl, pos, stack) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }
}
